package com.moneyx.webhook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Um registro da LINHA DO TEMPO de um endpoint (trilha de auditoria).
 *
 * IMUTÁVEL e só de inserção: responde "o que aconteceu com este endpoint, quando e com qual API Key".
 * O estado atual continua em WebhookEndpoint; este registro é complementar (não é Event Sourcing completo,
 * em que o estado seria reconstruído a partir destes registros).
 */
@Entity
@Table(name = "webhook_endpoint_history")
public class WebhookEndpointHistory {

    @Id
    private UUID id;

    @Column(name = "endpoint_id", nullable = false, updatable = false)
    private UUID endpointId;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private WebhookEndpointAction action;

    @Column(name = "api_key_id", nullable = false, updatable = false)
    private UUID apiKeyId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /** JSON (url e enabledEvents naquele momento). Nunca contém o segredo. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private String snapshot;

    protected WebhookEndpointHistory() {
    }

    public static WebhookEndpointHistory of(WebhookEndpoint endpoint,
                                            WebhookEndpointAction action,
                                            UUID apiKeyId,
                                            String snapshot,
                                            Instant occurredAt) {
        WebhookEndpointHistory entry = new WebhookEndpointHistory();
        entry.id = UUID.randomUUID();
        entry.endpointId = endpoint.getId();
        entry.merchantId = endpoint.getMerchantId();
        entry.action = Objects.requireNonNull(action, "action must not be null");
        entry.apiKeyId = Objects.requireNonNull(apiKeyId, "apiKeyId must not be null");
        entry.snapshot = Objects.requireNonNull(snapshot, "snapshot must not be null");
        entry.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        return entry;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public WebhookEndpointAction getAction() {
        return action;
    }

    public UUID getApiKeyId() {
        return apiKeyId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getSnapshot() {
        return snapshot;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WebhookEndpointHistory other)) return false;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
