package com.moneyx.payment.service;

import com.moneyx.payment.domain.Payment;
import com.moneyx.payment.domain.PaymentIdempotencyKey;
import com.moneyx.payment.exception.IdempotencyKeyReusedException;
import com.moneyx.payment.exception.InvalidIdempotencyKeyException;
import com.moneyx.payment.repository.PaymentIdempotencyKeyRepository;
import com.moneyx.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Casos de uso de pagamentos.
 */
@Service
public class PaymentService {

    /** 1 a 255 caracteres ASCII visíveis (sem espaços nem caracteres de controle). */
    private static final Pattern IDEMPOTENCY_KEY_FORMAT =
            Pattern.compile("^[\\x21-\\x7E]{1," + PaymentIdempotencyKey.MAX_LENGTH + "}$");

    private final PaymentRepository paymentRepository;
    private final PaymentIdempotencyKeyRepository idempotencyKeyRepository;
    private final Clock clock;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentIdempotencyKeyRepository idempotencyKeyRepository,
                          Clock clock) {
        this.paymentRepository = paymentRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.clock = clock;
    }

    /**
     * Cria um pagamento de forma idempotente.
     *
     * Tudo acontece em UMA transação:
     * 1. monta o pagamento em memória (o domínio valida valor, prazo e textos);
     * 2. tenta reservar a Idempotency-Key apontando para esse pagamento (comando atômico);
     * 3a. reservou: grava o pagamento e devolve replayed=false;
     * 3b. a chave já existia: confere se o corpo é o mesmo e devolve o pagamento ORIGINAL (replayed=true),
     *     descartando o pagamento montado no passo 1, que nunca chega ao banco.
     *
     * Se algo falhar, a transação inteira é desfeita: a chave não fica "consumida" e o cliente
     * pode repetir a requisição com segurança.
     *
     * @throws InvalidIdempotencyKeyException se a chave tiver formato inválido
     * @throws IdempotencyKeyReusedException  se a chave já foi usada com outro corpo
     * @throws IllegalArgumentException       se os dados violarem as regras do domínio
     */
    @Transactional
    public PaymentCreation create(UUID merchantId, String idempotencyKey, CreatePaymentCommand command) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY_FORMAT.matcher(idempotencyKey).matches()) {
            throw new InvalidIdempotencyKeyException();
        }

        // O PostgreSQL guarda microssegundos. Truncar aqui garante que a resposta da criação e a de um
        // replay (lida do banco) mostrem exatamente os mesmos instantes.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

        Payment payment = Payment.create(
                merchantId,
                command.amount(),
                command.currency(),
                command.description(),
                command.externalReference(),
                command.expiresInSeconds() == null ? null : Duration.ofSeconds(command.expiresInSeconds()),
                now);

        String fingerprint = command.fingerprint();

        int reserved = idempotencyKeyRepository.reserve(
                UUID.randomUUID(),
                merchantId,
                idempotencyKey,
                fingerprint,
                payment.getId(),
                now,
                now.plus(PaymentIdempotencyKey.TTL));

        if (reserved == 1) {
            paymentRepository.save(payment);
            return new PaymentCreation(payment, false);
        }

        // A chave já existe e está válida: esta requisição é uma repetição.
        // Esta leitura é um comando novo; no PostgreSQL (READ COMMITTED) ela enxerga a linha que a
        // transação concorrente acabou de confirmar.
        PaymentIdempotencyKey existing = idempotencyKeyRepository
                .findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Idempotency key reported as existing but not found"));

        if (!existing.matches(fingerprint)) {
            throw new IdempotencyKeyReusedException();
        }

        Payment original = paymentRepository.findByIdAndMerchantId(existing.getPaymentId(), merchantId)
                .orElseThrow(() -> new IllegalStateException("Payment referenced by idempotency key not found"));

        return new PaymentCreation(original, true);
    }
}
