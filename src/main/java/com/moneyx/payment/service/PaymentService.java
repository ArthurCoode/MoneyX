package com.moneyx.payment.service;

import com.moneyx.event.domain.EventType;
import com.moneyx.payment.domain.Payment;
import com.moneyx.payment.domain.PaymentIdempotencyKey;
import com.moneyx.payment.domain.PaymentStatus;
import com.moneyx.payment.exception.IdempotencyKeyReusedException;
import com.moneyx.payment.exception.InvalidCursorException;
import com.moneyx.payment.exception.InvalidIdempotencyKeyException;
import com.moneyx.payment.exception.PaymentExpiredException;
import com.moneyx.payment.exception.PaymentNotFoundException;
import com.moneyx.payment.exception.PaymentNotPendingException;
import com.moneyx.payment.exception.VolumeLimitExceededException;
import com.moneyx.payment.repository.PaymentIdempotencyKeyRepository;
import com.moneyx.payment.repository.PaymentRepository;
import com.moneyx.payment.repository.PaymentSpecifications;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
    private final PaymentEventRecorder events;
    private final PaymentVolumeLimit volumeLimit;
    private final Clock clock;

    PaymentService(PaymentRepository paymentRepository,
                   PaymentIdempotencyKeyRepository idempotencyKeyRepository,
                   PaymentEventRecorder events,
                   PaymentVolumeLimit volumeLimit,
                   Clock clock) {
        this.paymentRepository = paymentRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.events = events;
        this.volumeLimit = volumeLimit;
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
     * @throws VolumeLimitExceededException   se o pagamento passar do limite acumulado do dia ou do mês
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

        // Limite de volume: tranca o comerciante ANTES de reservar a chave. A reserva insere uma linha com FK para
        // merchants (pega FOR KEY SHARE). Duas proteções contra deadlock: a ordem (trava antes do KEY SHARE) e o modo
        // da trava (FOR NO KEY UPDATE não conflita com KEY SHARE). Provado por mutação: só com as DUAS removidas
        // (FOR UPDATE depois da reserva) as criações simultâneas entram em deadlock. Repetições também passam por aqui.
        paymentRepository.lockMerchantForPaymentCreation(merchantId)
                .orElseThrow(() -> new IllegalStateException("Authenticated merchant not found"));

        int reserved = idempotencyKeyRepository.reserve(
                UUID.randomUUID(),
                merchantId,
                idempotencyKey,
                fingerprint,
                payment.getId(),
                now,
                now.plus(PaymentIdempotencyKey.TTL));

        if (reserved == 1) {
            // Só pagamentos NOVOS contam (uma repetição não soma de novo nem é recusada). Se estourar, a exceção
            // desfaz também a reserva da chave: o cliente pode tentar de novo depois com a mesma chave.
            volumeLimit.check(merchantId, payment.getAmount(), now);
            paymentRepository.save(payment);
            events.record(EventType.PAYMENT_CREATED, payment, now);
            return new PaymentCreation(payment, false);
        }
        // Daqui em diante é um replay: o pagamento já existia e o payment.created já foi registrado na 1ª vez.

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

    /**
     * SANDBOX: simula o pagador pagando o Pix pelo app do banco.
     *
     * Em produção (Fase 7) quem confirma o pagamento é o PSP, nunca o comerciante. Por isso este
     * caso de uso existe apenas para o endpoint de sandbox.
     *
     * A linha do pagamento é trancada antes de qualquer verificação: dois "pagar" simultâneos são
     * serializados e o segundo recebe PaymentNotPendingException (status já PAID).
     *
     * @throws PaymentNotFoundException   pagamento inexistente ou de outro comerciante
     * @throws PaymentNotPendingException pagamento já está em estado final
     * @throws PaymentExpiredException    prazo de pagamento encerrado
     */
    @Transactional
    public Payment simulatePixPayment(UUID merchantId, UUID paymentId) {
        Payment payment = paymentRepository.lockByIdAndMerchantId(paymentId, merchantId)
                .orElseThrow(PaymentNotFoundException::new);

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

        // Mensagens de erro específicas para o cliente. O próprio markPaid repete estas verificações
        // (é a regra do domínio); aqui só as antecipamos para responder com o motivo exato.
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new PaymentNotPendingException(payment.getStatus());
        }
        if (payment.isPastExpiration(now)) {
            throw new PaymentExpiredException();
        }

        payment.markPaid(now);
        events.record(EventType.PAYMENT_PAID, payment, now);
        return payment;
    }

    /**
     * Consulta um pagamento do comerciante.
     *
     * @throws PaymentNotFoundException pagamento inexistente ou de outro comerciante
     */
    @Transactional(readOnly = true)
    public Payment get(UUID merchantId, UUID paymentId) {
        return paymentRepository.findByIdAndMerchantId(paymentId, merchantId)
                .orElseThrow(PaymentNotFoundException::new);
    }

    /**
     * Lista os pagamentos do comerciante, mais recentes primeiro, com paginação por cursor.
     *
     * Busca limit + 1 linhas: se a linha extra vier, existe próxima página (hasMore) e ela é descartada.
     * Assim não é preciso um COUNT(*), que ficaria caro com muitos pagamentos.
     *
     * @throws InvalidCursorException startingAfter não é um pagamento deste comerciante
     */
    @Transactional(readOnly = true)
    public PaymentPage list(UUID merchantId, PaymentListQuery query) {
        Specification<Payment> spec = PaymentSpecifications.belongsTo(merchantId);

        if (query.startingAfter() != null) {
            // Buscar o cursor TAMBÉM pelo merchantId: o id de um pagamento alheio não pode servir de referência,
            // nem revelar (pela página devolvida) quando outro comerciante criou um pagamento.
            Payment cursor = paymentRepository.findByIdAndMerchantId(query.startingAfter(), merchantId)
                    .orElseThrow(InvalidCursorException::new);
            spec = spec.and(PaymentSpecifications.after(cursor.getCreatedAt(), cursor.getId()));
        }
        if (query.status() != null) {
            spec = spec.and(PaymentSpecifications.hasStatus(query.status()));
        }
        if (query.externalReference() != null) {
            spec = spec.and(PaymentSpecifications.hasExternalReference(query.externalReference()));
        }

        int limit = query.limit();
        List<Payment> rows = paymentRepository.findBy(spec, q -> q
                .sortBy(PaymentSpecifications.NEWEST_FIRST)
                .limit(limit + 1)
                .all());

        boolean hasMore = rows.size() > limit;
        return new PaymentPage(hasMore ? rows.subList(0, limit) : rows, hasMore);
    }

    /**
     * Cancela um pagamento PENDING.
     *
     * Idempotente: cancelar um pagamento já CANCELED devolve o próprio pagamento, sem erro
     * (o cliente pode repetir a requisição com segurança, por exemplo após um timeout de rede).
     *
     * Usa o mesmo lock pessimista do "pagar": um "pagar" e um "cancelar" simultâneos são serializados,
     * exatamente um vence e o outro recebe o motivo correto (ex.: "já está PAID").
     *
     * @throws PaymentNotFoundException   pagamento inexistente ou de outro comerciante
     * @throws PaymentNotPendingException pagamento PAID ou EXPIRED
     * @throws PaymentExpiredException    prazo encerrado (mesmo que o status ainda seja PENDING)
     */
    @Transactional
    public Payment cancel(UUID merchantId, UUID paymentId) {
        Payment payment = paymentRepository.lockByIdAndMerchantId(paymentId, merchantId)
                .orElseThrow(PaymentNotFoundException::new);

        if (payment.getStatus() == PaymentStatus.CANCELED) {
            return payment; // repetição: nada mudou, portanto nenhum evento novo
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new PaymentNotPendingException(payment.getStatus());
        }

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        // O estado real de um PENDING vencido é "expirado": a Etapa 5 (job) vai marcá-lo como EXPIRED.
        if (payment.isPastExpiration(now)) {
            throw new PaymentExpiredException();
        }

        payment.cancel(now);
        events.record(EventType.PAYMENT_CANCELED, payment, now);
        return payment;
    }
}
