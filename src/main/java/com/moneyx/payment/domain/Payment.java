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
 * Estado: não há setStatus(). As transições acontecem apenas por métodos de domínio que validam a mudança:
 * CREATING -> PENDING (attachPixCharge) | EXPIRED (expireBeforeCharge);
 * PENDING -> PAID (markPaid) | CANCELED (cancel) | EXPIRED (expire).
 *
 * CREATING existe porque a cobrança Pix pode vir de um PSP externo, chamado FORA da transação (Fase 7): o pagamento
 * é gravado primeiro, sem cobrança, e só vira PENDING quando o BR Code chega.
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

    /** Prazo de pagamento. Ao anexar a cobrança, passa a ser o vencimento definido PELO PSP (quem aceita o Pix). */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Identificador da cobrança Pix (campo 62.05 do BR Code), usado na conciliação. Null enquanto CREATING. */
    @Column(name = "pix_txid", length = PixCharge.TXID_MAX_LENGTH)
    private String pixTxid;

    /** BR Code ("Pix copia e cola"). Null enquanto CREATING. */
    @Column(name = "pix_copy_paste", length = 512)
    private String pixCopyPaste;

    /** Quem emite a cobrança ("sandbox", "mercadopago"). Definido na criação; não muda. */
    @Column(name = "pix_provider", nullable = false, length = 20)
    private String pixProvider;

    /** Id da cobrança (order) no PSP, para consultar e cancelar. Null no sandbox local. */
    @Column(name = "provider_order_id", length = 64)
    private String providerOrderId;

    /** Id do pagamento dentro da order no PSP. Null no sandbox local. */
    @Column(name = "provider_payment_id", length = 64)
    private String providerPaymentId;

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

    /** Diferença máxima aceita entre o vencimento pedido e o devolvido pelo PSP (latência da chamada). */
    public static final Duration MAX_PROVIDER_EXPIRATION_DRIFT = Duration.ofMinutes(10);

    /**
     * Cria um pagamento CREATING (ainda sem cobrança Pix).
     *
     * @param pixProvider quem vai emitir a cobrança (PixProvider.name())
     * @param expiresIn   prazo para pagamento; null usa DEFAULT_EXPIRATION
     */
    public static Payment create(UUID merchantId,
                                 long amount,
                                 PaymentCurrency currency,
                                 String description,
                                 String externalReference,
                                 Duration expiresIn,
                                 String pixProvider,
                                 Instant now) {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(pixProvider, "pixProvider must not be null");
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
        payment.status = PaymentStatus.CREATING;
        payment.pixProvider = pixProvider;
        payment.description = normalizeOptional(description, DESCRIPTION_MAX_LENGTH, "description");
        payment.externalReference = normalizeOptional(
                externalReference, EXTERNAL_REFERENCE_MAX_LENGTH, "externalReference");
        payment.createdAt = now;
        payment.updatedAt = now;
        payment.expiresAt = now.plus(expiration);
        // A cobrança Pix é emitida depois, por um PixProvider (attachPixCharge), só para pagamentos NOVOS.
        return payment;
    }

    // ---------- transições de estado ----------

    /**
     * Prazo pedido (vencimento - criação). Enquanto CREATING é exatamente o que o comerciante pediu, e é o que vai
     * para o PSP: repetir a chamada manda o MESMO corpo, condição para o PSP devolver a mesma cobrança.
     */
    public Duration requestedExpiration() {
        requireStatus(PaymentStatus.CREATING, "charged");
        return Duration.between(createdAt, expiresAt);
    }

    /**
     * CREATING -> PENDING: anexa a cobrança emitida pelo provedor. Uma vez só (a cobrança nunca é trocada).
     *
     * Se o provedor informar o vencimento, ele passa a valer: é o PSP quem aceita ou recusa o Pix, e ele conta o prazo
     * a partir de quando RECEBEU o pedido (um pouco depois do nosso). Um vencimento muito diferente do pedido indica
     * erro e é recusado.
     */
    public void attachPixCharge(PixCharge pix, Instant now) {
        Objects.requireNonNull(pix, "pix must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (pixTxid != null) {
            throw new IllegalStateException("Payment already has a Pix charge");
        }
        requireStatus(PaymentStatus.CREATING, "charged");
        if (!pixProvider.equals(pix.provider())) {
            throw new IllegalStateException("Charge issued by " + pix.provider() + " but payment uses " + pixProvider);
        }
        if (pix.expiresAt() != null) {
            if (pix.expiresAt().isBefore(expiresAt.minus(MAX_PROVIDER_EXPIRATION_DRIFT))
                    || pix.expiresAt().isAfter(expiresAt.plus(MAX_PROVIDER_EXPIRATION_DRIFT))) {
                throw new IllegalStateException("Provider expiration " + pix.expiresAt()
                        + " differs too much from the requested " + expiresAt);
            }
            this.expiresAt = pix.expiresAt();
        }
        this.pixTxid = pix.txid();
        this.pixCopyPaste = pix.copyPaste();
        this.providerOrderId = pix.providerOrderId();
        this.providerPaymentId = pix.providerPaymentId();
        changeStatus(PaymentStatus.PENDING, now);
    }

    /**
     * CREATING -> EXPIRED: o prazo acabou antes de a cobrança ser emitida (o PSP ficou fora do ar). Ninguém recebeu um
     * BR Code deste pagamento, portanto ninguém pode pagá-lo.
     */
    public void expireBeforeCharge(Instant now) {
        requireStatus(PaymentStatus.CREATING, "expired before charge");
        if (!isPastExpiration(now)) {
            throw new IllegalStateException("Payment cannot be expired before " + expiresAt);
        }
        changeStatus(PaymentStatus.EXPIRED, now);
    }

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
        requireStatus(PaymentStatus.PENDING, target);
    }

    private void requireStatus(PaymentStatus required, String target) {
        if (status != required) {
            throw new IllegalStateException(
                    "Only " + required + " payments can be " + target + ". Current status: " + status);
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

    public String getPixTxid() {
        return pixTxid;
    }

    public String getPixCopyPaste() {
        return pixCopyPaste;
    }

    public String getPixProvider() {
        return pixProvider;
    }

    public String getProviderOrderId() {
        return providerOrderId;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
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
