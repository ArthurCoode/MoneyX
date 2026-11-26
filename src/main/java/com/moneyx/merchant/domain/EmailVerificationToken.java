package com.moneyx.merchant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Token de verificação de e-mail (V16): vai no link do e-mail, uso único, validade de 24 h.
 *
 * Como as API Keys, o token em texto NUNCA é guardado: só o hash SHA-256. Quem lê o banco não consegue verificar o
 * e-mail de ninguém. O texto aparece uma vez (no e-mail) e não vai para log.
 */
@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken {

    public static final Duration VALIDITY = Duration.ofHours(24);
    private static final int RANDOM_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

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

    protected EmailVerificationToken() {
    }

    /** Token novo + o texto que vai no e-mail (devolvido UMA vez, nunca guardado). */
    public record Issued(EmailVerificationToken token, String plainText) {
        @Override
        public String toString() {
            return "Issued[token=" + token.getId() + ", plainText=***]";
        }
    }

    public static Issued issue(UUID merchantId, Instant now) {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        String plainText = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        EmailVerificationToken token = new EmailVerificationToken();
        token.id = UUID.randomUUID();
        token.merchantId = merchantId;
        token.tokenHash = hash(plainText);
        token.createdAt = now;
        token.expiresAt = now.plus(VALIDITY);
        return new Issued(token, plainText);
    }

    /** SHA-256 em hexadecimal minúsculo. */
    public static String hash(String plainText) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(plainText.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** Consome o token (confirmação do e-mail ou substituição por um token novo). */
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
