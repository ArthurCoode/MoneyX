package com.moneyx.apikey.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Credencial de acesso de um comerciante à API.
 *
 * Referencia o comerciante apenas pelo ID (merchantId), e não pela entidade Merchant:
 * o banco garante a integridade via chave estrangeira, enquanto os módulos Java
 * permanecem desacoplados.
 *
 * Chaves revogadas não são apagadas: revokedAt registra quando deixaram de valer (rastreabilidade).
 */
@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "key_prefix", nullable = false, updatable = false, length = 20)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, updatable = false, unique = true, length = 64)
    private String keyHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Version
    private Long version;

    protected ApiKey() {
    }

    /** Cria a entidade a partir de uma chave recém-gerada. Apenas prefixo e hash são guardados. */
    public static ApiKey issue(UUID merchantId, ApiKeyGenerator.GeneratedApiKey generated) {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(generated, "generated must not be null");

        ApiKey apiKey = new ApiKey();
        apiKey.id = UUID.randomUUID();
        apiKey.merchantId = merchantId;
        apiKey.keyPrefix = generated.displayPrefix();
        apiKey.keyHash = generated.hash();
        apiKey.createdAt = Instant.now();
        return apiKey;
    }

    public void revoke() {
        if (!isActive()) {
            throw new IllegalStateException("API key is already revoked");
        }
        this.revokedAt = Instant.now();
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    // Intencionalmente sem getter para keyHash: nada fora da persistência precisa lê-lo.

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApiKey other)) return false;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
