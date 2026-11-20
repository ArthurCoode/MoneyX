package com.moneyx.payment.service;

import com.moneyx.event.domain.EventType;
import com.moneyx.payment.domain.Payment;
import com.moneyx.payment.repository.PaymentIdempotencyKeyRepository;
import com.moneyx.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Manutenção periódica dos pagamentos (chamada pelos jobs agendados, ou diretamente nos testes).
 *
 * Trabalha em LOTES, cada lote na sua própria transação curta:
 * - poucas linhas trancadas por vez (não atrapalha "pagar"/"cancelar");
 * - um erro desfaz só o lote atual, não o trabalho já feito.
 *
 * As transações são abertas com TransactionTemplate, e não com @Transactional, porque o laço e o lote
 * estão na mesma classe: uma chamada interna não passa pelo proxy do Spring e não abriria transação.
 */
@Service
public class PaymentMaintenanceService {

    public static final int EXPIRATION_BATCH_SIZE = 100;
    public static final int IDEMPOTENCY_PURGE_BATCH_SIZE = 1_000;

    private final PaymentRepository paymentRepository;
    private final PaymentIdempotencyKeyRepository idempotencyKeyRepository;
    private final PaymentEventRecorder events;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public PaymentMaintenanceService(PaymentRepository paymentRepository,
                                     PaymentIdempotencyKeyRepository idempotencyKeyRepository,
                                     PaymentEventRecorder events,
                                     PlatformTransactionManager transactionManager,
                                     Clock clock) {
        this.paymentRepository = paymentRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.events = events;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Marca como EXPIRED todos os pagamentos PENDING cujo prazo terminou.
     *
     * Linhas trancadas por outra operação no momento (SKIP LOCKED) ficam para a próxima execução.
     *
     * @return quantos pagamentos foram expirados
     */
    public int expireOverduePayments() {
        // Um único "agora" para a execução inteira: o resultado não depende de quanto tempo os lotes levam.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        int total = 0;
        int batch;
        do {
            batch = transaction.execute(status -> expireBatch(now));
            total += batch;
        } while (batch == EXPIRATION_BATCH_SIZE); // lote incompleto = não sobrou nada disponível
        return total;
    }

    private int expireBatch(Instant now) {
        List<Payment> overdue = paymentRepository.lockOverduePending(now, EXPIRATION_BATCH_SIZE);
        // O domínio confere de novo (só PENDING, só após o prazo). A gravação acontece no commit do lote,
        // pelo dirty checking do Hibernate, e o @Version ainda protege contra qualquer alteração concorrente.
        for (Payment payment : overdue) {
            payment.expire(now);
            events.record(EventType.PAYMENT_EXPIRED, payment, now); // mesma transação do lote
        }
        return overdue.size();
    }

    /**
     * Apaga as chaves de idempotência vencidas (mais de 24 h). Elas não têm mais efeito: o reserve()
     * já as trata como livres. A limpeza só evita que a tabela cresça para sempre.
     *
     * @return quantas chaves foram apagadas
     */
    public int purgeExpiredIdempotencyKeys() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        int total = 0;
        int batch;
        do {
            batch = transaction.execute(status ->
                    idempotencyKeyRepository.deleteExpired(now, IDEMPOTENCY_PURGE_BATCH_SIZE));
            total += batch;
            // Repete enquanto apagar algo: com SKIP LOCKED, um lote menor que o limite NÃO significa que acabou
            // (algumas linhas podiam só estar trancadas naquele instante). Termina quando um lote vem vazio.
        } while (batch > 0);
        return total;
    }
}
