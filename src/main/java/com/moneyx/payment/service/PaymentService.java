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
import com.moneyx.payment.domain.PixCharge;
import com.moneyx.payment.exception.PaymentProviderRejectedException;
import com.moneyx.payment.exception.PaymentProviderUnavailableException;
import com.moneyx.payment.provider.PixChargeRequest;
import com.moneyx.payment.provider.PixProvider;
import com.moneyx.payment.provider.PixProviderException;
import com.moneyx.payment.provider.SandboxPixProvider;
import com.moneyx.payment.exception.SandboxPaymentNotSupportedException;
import com.moneyx.payment.repository.PaymentIdempotencyKeyRepository;
import com.moneyx.payment.repository.PaymentRepository;
import com.moneyx.payment.repository.PaymentSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

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

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /** 1 a 255 caracteres ASCII visíveis (sem espaços nem caracteres de controle). */
    private static final Pattern IDEMPOTENCY_KEY_FORMAT =
            Pattern.compile("^[\\x21-\\x7E]{1," + PaymentIdempotencyKey.MAX_LENGTH + "}$");

    private final PaymentRepository paymentRepository;
    private final PaymentIdempotencyKeyRepository idempotencyKeyRepository;
    private final PaymentEventRecorder events;
    private final PaymentVolumeLimit volumeLimit;
    private final PixProvider pixProvider;
    private final Clock clock;
    private final TransactionTemplate transaction;

    PaymentService(PaymentRepository paymentRepository,
                   PaymentIdempotencyKeyRepository idempotencyKeyRepository,
                   PaymentEventRecorder events,
                   PaymentVolumeLimit volumeLimit,
                   PixProvider pixProvider,
                   Clock clock,
                   PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.events = events;
        this.volumeLimit = volumeLimit;
        this.pixProvider = pixProvider;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Cria um pagamento de forma idempotente, em TRÊS fases (a cobrança pode vir de um PSP externo):
     *
     * 1. transação curta (begin): trava o comerciante, reserva a Idempotency-Key, confere o limite de volume e grava o
     *    pagamento como CREATING (sem cobrança). Uma repetição com a mesma chave devolve o pagamento original.
     * 2. FORA de transação (charge): pede a cobrança ao PixProvider. Nenhuma trava fica presa esperando a rede.
     * 3. transação curta (complete): trava o pagamento e, se ainda estiver CREATING, anexa a cobrança, muda para
     *    PENDING e grava o evento payment.created.
     *
     * Se o provedor falhar:
     * - temporariamente (timeout, fora do ar): o pagamento fica CREATING e PaymentProviderUnavailableException (503).
     *   Repetir a requisição com a mesma chave (ou a rotina resumeStuckCreations) retoma a fase 2: o provedor devolve
     *   a MESMA cobrança para o mesmo pedido, então chamar de novo é seguro;
     * - definitivamente (recusou o pedido): o pagamento e a chave são descartados e PaymentProviderRejectedException
     *   (502). Nenhuma cobrança foi criada no provedor.
     *
     * Não pode rodar dentro de uma transação de quem chama: as fases 1 e 3 precisam ser commits independentes.
     *
     * @throws InvalidIdempotencyKeyException     se a chave tiver formato inválido
     * @throws IdempotencyKeyReusedException      se a chave já foi usada com outro corpo
     * @throws VolumeLimitExceededException       se o pagamento passar do limite acumulado do dia ou do mês
     * @throws PaymentProviderUnavailableException o provedor está fora do ar (o pagamento fica CREATING)
     * @throws PaymentProviderRejectedException   o provedor recusou o pedido (nada foi criado)
     * @throws IllegalArgumentException           se os dados violarem as regras do domínio
     */
    public PaymentCreation create(UUID merchantId, String idempotencyKey, CreatePaymentCommand command) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY_FORMAT.matcher(idempotencyKey).matches()) {
            throw new InvalidIdempotencyKeyException();
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("PaymentService.create must not run inside a transaction");
        }

        PaymentCreation begun = transaction.execute(status -> begin(merchantId, idempotencyKey, command));
        Payment payment = begun.payment();
        if (payment.getStatus() == PaymentStatus.CREATING) {
            payment = charge(payment);
        }
        return new PaymentCreation(payment, begun.replayed());
    }

    /** Fase 1 (dentro da transação). */
    private PaymentCreation begin(UUID merchantId, String idempotencyKey, CreatePaymentCommand command) {
        // O PostgreSQL guarda microssegundos. Truncar aqui garante que a resposta da criação e a de um
        // replay (lida do banco) mostrem exatamente os mesmos instantes.
        Instant now = now();

        Payment payment = Payment.create(
                merchantId,
                command.amount(),
                command.currency(),
                command.description(),
                command.externalReference(),
                command.expiresInSeconds() == null ? null : Duration.ofSeconds(command.expiresInSeconds()),
                pixProvider.name(),
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

    /**
     * Fases 2 e 3: pede a cobrança ao provedor (fora de transação) e a anexa. Usada pela criação, pela repetição de
     * uma criação que ficou CREATING e pela rotina de recuperação. Segura para rodar mais de uma vez ao mesmo tempo:
     * o provedor devolve a mesma cobrança e a fase 3, sob trava, só anexa se o pagamento ainda estiver CREATING.
     *
     * @return o pagamento no estado atual (PENDING, ou outro se alguém concluiu antes)
     */
    private Payment charge(Payment payment) {
        if (payment.isPastExpiration(now())) {
            // Ninguém recebeu um BR Code deste pagamento: não adianta emitir a cobrança agora.
            return transaction.execute(status -> expireStuck(payment.getMerchantId(), payment.getId()));
        }

        PixChargeRequest request = new PixChargeRequest(
                payment.getId(), payment.getAmount(), payment.requestedExpiration(), payment.getDescription());
        PixCharge charge;
        try {
            charge = pixProvider.createCharge(request);
        } catch (PixProviderException e) {
            if (e.isRetryable()) {
                log.warn("payment.charge_failed paymentId={} provider={} code={} retryable=true",
                        payment.getId(), payment.getPixProvider(), e.getCode());
                throw new PaymentProviderUnavailableException(e.getRetryAfter());
            }
            log.warn("payment.charge_rejected paymentId={} provider={} code={}",
                    payment.getId(), payment.getPixProvider(), e.getCode());
            transaction.executeWithoutResult(status -> discard(payment.getMerchantId(), payment.getId()));
            throw new PaymentProviderRejectedException();
        }

        return transaction.execute(status -> complete(payment.getMerchantId(), payment.getId(), charge));
    }

    /** Fase 3 (dentro da transação). */
    private Payment complete(UUID merchantId, UUID paymentId, PixCharge charge) {
        Payment payment = paymentRepository.lockByIdAndMerchantId(paymentId, merchantId)
                .orElseThrow(() -> new IllegalStateException("Payment being charged no longer exists"));
        if (payment.getStatus() != PaymentStatus.CREATING) {
            return payment; // outra execução (repetição ou recuperação) concluiu antes
        }
        Instant now = now();
        payment.attachPixCharge(charge, now);
        events.record(EventType.PAYMENT_CREATED, payment, now);
        return payment;
    }

    /** O provedor recusou o pedido: nada foi criado nele. Apaga o pagamento e libera a chave de idempotência. */
    private void discard(UUID merchantId, UUID paymentId) {
        paymentRepository.lockByIdAndMerchantId(paymentId, merchantId)
                .filter(payment -> payment.getStatus() == PaymentStatus.CREATING)
                .ifPresent(payment -> {
                    idempotencyKeyRepository.deleteByPaymentId(paymentId);
                    paymentRepository.delete(payment);
                });
    }

    /** CREATING com o prazo vencido vira EXPIRED (sem evento: o comerciante nunca recebeu este pagamento pronto). */
    private Payment expireStuck(UUID merchantId, UUID paymentId) {
        Payment payment = paymentRepository.lockByIdAndMerchantId(paymentId, merchantId)
                .orElseThrow(() -> new IllegalStateException("Payment being charged no longer exists"));
        if (payment.getStatus() == PaymentStatus.CREATING) {
            payment.expireBeforeCharge(now());
            log.warn("payment.expired_before_charge paymentId={} provider={}", payment.getId(), payment.getPixProvider());
        }
        return payment;
    }

    /**
     * Rotina de recuperação: retoma pagamentos que ficaram em CREATING (o provedor estava fora do ar, ou a aplicação
     * caiu entre as fases). Só pega os criados há mais de {@code grace} (para não disputar com a requisição que ainda
     * está em andamento; se disputar, nada de errado acontece, só uma chamada a mais ao provedor).
     *
     * @return quantos foram concluídos (PENDING) ou expirados nesta execução
     */
    public int resumeStuckCreations(Duration grace, int limit) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("resumeStuckCreations must not run inside a transaction");
        }
        List<Payment> stuck = paymentRepository.findCreatingCreatedBefore(now().minus(grace), limit);
        int resolved = 0;
        for (Payment payment : stuck) {
            try {
                if (charge(payment).getStatus() != PaymentStatus.CREATING) {
                    resolved++;
                }
            } catch (PaymentProviderUnavailableException e) {
                // continua CREATING; próxima execução tenta de novo
            } catch (PaymentProviderRejectedException e) {
                resolved++; // descartado
            } catch (RuntimeException e) {
                log.error("payment.resume_failed paymentId={}", payment.getId(), e);
            }
        }
        return resolved;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
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
        if (!SandboxPixProvider.NAME.equals(payment.getPixProvider())) {
            throw new SandboxPaymentNotSupportedException(payment.getPixProvider());
        }

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
