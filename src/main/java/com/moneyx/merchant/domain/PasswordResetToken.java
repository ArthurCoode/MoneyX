package com.moneyx.merchant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Link de definir/redefinir senha (V17): uso único, 1 hora, só o hash guardado. Pedir outro invalida os anteriores.
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    public static final Duration VALIDITY = Duration.ofHours(1);

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected PasswordResetToken() {
    }

    public record Issued(PasswordResetToken token, String plainText) {
        @Override
        public String toString() {
            return "Issued[token=" + token.getId() + ", plainText=***]";
        }
    }

    public static Issued issue(UUID merchantId, Instant now) {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        SecretToken secret = SecretToken.generate("");
        PasswordResetToken token = new PasswordResetToken();
        token.id = UUID.randomUUID();
        token.merchantId = merchantId;
        token.tokenHash = secret.hash();
        token.createdAt = now;
        token.expiresAt = now.plus(VALIDITY);
        return new Issued(token, secret.plainText());
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void markUsed(Instant now) {
        if (usedAt != null) {
            throw new IllegalStateException("Token already used");
        }
        this.usedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
