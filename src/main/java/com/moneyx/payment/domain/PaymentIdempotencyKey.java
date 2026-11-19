package com.moneyx.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro de uma chave de idempotência já utilizada na criação de um pagamento.
 *
 * Somente leitura para o JPA: a gravação é feita por um único comando SQL atômico
 * (INSERT ... ON CONFLICT) em PaymentIdempotencyKeyRepository, que é o que garante
 * a correção sob requisições simultâneas.
 */
@Entity
@Table(name = "payment_idempotency_keys")
public class PaymentIdempotencyKey {

    /** Validade de uma chave. Depois disso, ela pode ser reutilizada para um novo pagamento. */
    public static final Duration TTL = Duration.ofHours(24);

    public static final int MAX_LENGTH = 255;

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = MAX_LENGTH)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, updatable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    protected PaymentIdempotencyKey() {
    }

    /** A requisição repetida é idêntica à original? */
    public boolean matches(String fingerprint) {
        return requestFingerprint.equals(fingerprint);
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
