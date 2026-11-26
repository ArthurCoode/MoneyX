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
 * Sessão do dashboard (V17). Criada no login (e-mail + senha); o token (mx_sess_...) fica com o BFF do frontend, num
 * cookie HttpOnly, e só o hash fica aqui.
 *
 * Validade (decisão do desenvolvedor, 2026-11-26): 6 h sem uso OU 24 h depois de criada, o que vier primeiro. Cada uso
 * renova a janela de inatividade (last_used_at), nunca o limite absoluto. Logout e redefinição de senha revogam.
 */
@Entity
@Table(name = "merchant_sessions")
public class MerchantSession {

    public static final String TOKEN_PREFIX = "mx_sess_";
    public static final Duration IDLE_TIMEOUT = Duration.ofHours(6);
    public static final Duration ABSOLUTE_TIMEOUT = Duration.ofHours(24);
    /**
     * Só grava last_used_at de novo depois disso: um dashboard faz várias requisições por segundo, e um UPDATE em cada
     * uma seria desperdício. Custa no máximo 1 min de imprecisão na inatividade (desprezível perto de 6 h).
     */
    public static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected MerchantSession() {
    }

    /** Sessão nova + o token em texto (devolvido UMA vez ao BFF). */
    public record Started(MerchantSession session, String token) {
        @Override
        public String toString() {
            return "Started[session=" + session.getId() + ", token=***]";
        }
    }

    public static Started start(UUID merchantId, Instant now) {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        SecretToken token = SecretToken.generate(TOKEN_PREFIX);
        MerchantSession session = new MerchantSession();
        session.id = UUID.randomUUID();
        session.merchantId = merchantId;
        session.tokenHash = token.hash();
        session.createdAt = now;
        session.lastUsedAt = now;
        session.expiresAt = now.plus(ABSOLUTE_TIMEOUT);
        return new Started(session, token.plainText());
    }

    /** Vale agora? (não revogada, dentro das 24 h e usada há menos de 6 h) */
    public boolean isActive(Instant now) {
        return revokedAt == null
                && now.isBefore(expiresAt)
                && now.isBefore(lastUsedAt.plus(IDLE_TIMEOUT));
    }

    /** Registra o uso (renova a inatividade). @return true se gravou (passou do TOUCH_INTERVAL) */
    public boolean touch(Instant now) {
        if (!now.isBefore(lastUsedAt.plus(TOUCH_INTERVAL))) {
            this.lastUsedAt = now;
            return true;
        }
        return false;
    }

    /** Idempotente: revogar de novo mantém o instante da primeira vez. */
    public void revoke(Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }

    /** Até quando vale, se continuar sendo usada (o menor dos dois limites). */
    public Instant validUntil() {
        Instant idle = lastUsedAt.plus(IDLE_TIMEOUT);
        return idle.isBefore(expiresAt) ? idle : expiresAt;
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

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
