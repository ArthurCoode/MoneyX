package com.moneyx.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Cobrança Pix de um comerciante.
 *
 * Dinheiro: o valor é um long em CENTAVOS (R$ 49,90 = 4990), nunca double. Ponto flutuante
 * não representa 0,10 com exatidão e acumula erros de arredondamento; em centavos toda conta é exata.
 *
 * Estado: não há setStatus(). As transições acontecem apenas por markPaid(), cancel() e expire(),
 * que validam se a mudança é permitida (ver PaymentStatus).
 *
 * Tempo: os métodos recebem o instante atual (now) como parâmetro em vez de chamar Instant.now().
 * Quem chama (o serviço) usa um Clock; os testes conseguem simular a passagem do tempo.
 *
 * Referencia o comerciante apenas pelo merchantId (mesma regra de desacoplamento do módulo apikey).
 */
@Entity
@Table(name = "payments")
public class Payment {

    /** R$ 1,00. Limite por operação. */
    public static final long MIN_AMOUNT = 100;
    /** R$ 100.000,00. Limite por operação. */
    public static final long MAX_AMOUNT = 10_000_000;

    /** 5 minutos. Em segundos para poder ser usado também em anotações de validação da API. */
    public static final long MIN_EXPIRATION_SECONDS = 300;
    /** 24 horas. */
    public static final long MAX_EXPIRATION_SECONDS = 86_400;

    public static final Duration DEFAULT_EXPIRATION = Duration.ofMinutes(30);
    public static final Duration MIN_EXPIRATION = Duration.ofSeconds(MIN_EXPIRATION_SECONDS);
    public static final Duration MAX_EXPIRATION = Duration.ofSeconds(MAX_EXPIRATION_SECONDS);

    public static final int DESCRIPTION_MAX_LENGTH = 255;
    public static final int EXTERNAL_REFERENCE_MAX_LENGTH = 64;

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    /** Valor em centavos. */
    @Column(nullable = false, updatable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 3)
    private PaymentCurrency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(length = DESCRIPTION_MAX_LENGTH, updatable = false)
    private String description;

    /** Identificador do pedido no sistema do comerciante, usado para conciliação. */
    @Column(name = "external_reference", length = EXTERNAL_REFERENCE_MAX_LENGTH, updatable = false)
    private String externalReference;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Concorrência otimista: impede, por exemplo, que um "pagar" e um "cancelar" simultâneos ambos vençam. */
    @Version
    private Long version;

    protected Payment() {
    }

    /**
     * Cria um pagamento PENDING.
     *
     * @param expiresIn prazo para pagamento; null usa DEFAULT_EXPIRATION
     */
    public static Payment create(UUID merchantId,
                                 long amount,
                                 PaymentCurrency currency,
                                 String description,
                                 String externalReference,
                                 Duration expiresIn,
                                 Instant now) {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(now, "now must not be null");

        if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            throw new IllegalArgumentException(
                    "amount must be between " + MIN_AMOUNT + " and " + MAX_AMOUNT + " cents");
        }

        Duration expiration = expiresIn != null ? expiresIn : DEFAULT_EXPIRATION;
        if (expiration.compareTo(MIN_EXPIRATION) < 0 || expiration.compareTo(MAX_EXPIRATION) > 0) {
            throw new IllegalArgumentException("expiration must be between 5 minutes and 24 hours");
        }

        Payment payment = new Payment();
        payment.id = UUID.randomUUID();
        payment.merchantId = merchantId;
        payment.amount = amount;
        payment.currency = currency;
        payment.status = PaymentStatus.PENDING;
        payment.description = normalizeOptional(description, DESCRIPTION_MAX_LENGTH, "description");
        payment.externalReference = normalizeOptional(
                externalReference, EXTERNAL_REFERENCE_MAX_LENGTH, "externalReference");
        payment.createdAt = now;
        payment.updatedAt = now;
        payment.expiresAt = now.plus(expiration);
        return payment;
    }

    // ---------- transições de estado ----------

    /** PENDING -> PAID. Não é possível pagar depois do prazo, mesmo que o job de expiração ainda não tenha rodado. */
    public void markPaid(Instant now) {
        requirePending("paid");
        if (isPastExpiration(now)) {
            throw new IllegalStateException("Payment has expired and can no longer be paid");
        }
        this.paidAt = now;
        changeStatus(PaymentStatus.PAID, now);
    }

    /** PENDING -> CANCELED. */
    public void cancel(Instant now) {
        requirePending("canceled");
        this.canceledAt = now;
        changeStatus(PaymentStatus.CANCELED, now);
    }

    /** PENDING -> EXPIRED. Só é permitido depois que o prazo terminou. */
    public void expire(Instant now) {
        requirePending("expired");
        if (!isPastExpiration(now)) {
            throw new IllegalStateException("Payment cannot be expired before " + expiresAt);
        }
        changeStatus(PaymentStatus.EXPIRED, now);
    }

    /** O prazo terminou? (vale mesmo enquanto o status ainda for PENDING) */
    public boolean isPastExpiration(Instant now) {
        return !now.isBefore(expiresAt);
    }

    private void requirePending(String target) {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING payments can be " + target + ". Current status: " + status);
        }
    }

    private void changeStatus(PaymentStatus newStatus, Instant now) {
        this.status = newStatus;
        this.updatedAt = now;
    }

    private static String normalizeOptional(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(field + " must have at most " + maxLength + " characters");
        }
        return trimmed;
    }

    // ---------- getters ----------

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public long getAmount() {
        return amount;
    }

    public PaymentCurrency getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getCanceledAt() {
        return canceledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Payment other)) return false;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
