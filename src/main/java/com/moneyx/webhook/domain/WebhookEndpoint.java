package com.moneyx.webhook.domain;

import com.moneyx.event.domain.EventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Destino dos eventos de um comerciante (ESTADO ATUAL; a linha do tempo fica em WebhookEndpointHistory).
 *
 * - A URL chega aqui já validada (WebhookUrlValidator).
 * - O segredo de assinatura é guardado CIFRADO; esta classe nunca vê o segredo em texto puro.
 * - Remoção é lógica: delete() preenche deletedAt e o registro continua existindo.
 */
@Entity
@Table(name = "webhook_endpoints")
public class WebhookEndpoint {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(nullable = false, updatable = false, length = 2048)
    private String url;

    /** Valores públicos dos tipos (ex.: "payment.paid"), em array do PostgreSQL. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "enabled_events", nullable = false, updatable = false)
    private String[] enabledEvents;

    @Column(name = "secret_ciphertext", nullable = false, updatable = false)
    private String secretCiphertext;

    @Column(name = "secret_prefix", nullable = false, updatable = false, length = 20)
    private String secretPrefix;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    protected WebhookEndpoint() {
    }

    /**
     * O id é gerado ANTES da cifragem: ele entra como dado associado (AAD) do segredo cifrado,
     * por isso quem cria o endpoint precisa conhecê-lo primeiro (ver WebhookEndpointService).
     */
    public static WebhookEndpoint create(UUID id,
                                         UUID merchantId,
                                         String url,
                                         Collection<EventType> enabledEvents,
                                         String secretCiphertext,
                                         String secretPrefix,
                                         Instant now) {
        if (enabledEvents == null || enabledEvents.isEmpty()) {
            throw new IllegalArgumentException("enabledEvents must contain at least one event type");
        }
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.id = Objects.requireNonNull(id, "id must not be null");
        endpoint.merchantId = Objects.requireNonNull(merchantId, "merchantId must not be null");
        endpoint.url = Objects.requireNonNull(url, "url must not be null");
        // Ordem estável (a do catálogo) e sem repetição, independentemente de como o cliente enviou.
        endpoint.enabledEvents = EnumSet.copyOf(enabledEvents).stream().map(EventType::value).toArray(String[]::new);
        endpoint.secretCiphertext = Objects.requireNonNull(secretCiphertext, "secretCiphertext must not be null");
        endpoint.secretPrefix = Objects.requireNonNull(secretPrefix, "secretPrefix must not be null");
        endpoint.createdAt = Objects.requireNonNull(now, "now must not be null");
        return endpoint;
    }

    /**
     * Remoção lógica.
     *
     * @return true se removeu agora; false se já estava removido (remoção é idempotente e mantém a data original)
     */
    public boolean delete(Instant now) {
        if (deletedAt != null) {
            return false;
        }
        this.deletedAt = Objects.requireNonNull(now, "now must not be null");
        return true;
    }

    public boolean isActive() {
        return deletedAt == null;
    }

    /** O endpoint quer receber este tipo de evento? (usado na entrega, Etapa 3) */
    public boolean accepts(EventType type) {
        return isActive() && Arrays.asList(enabledEvents).contains(type.value());
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getUrl() {
        return url;
    }

    public Set<EventType> getEnabledEvents() {
        return Arrays.stream(enabledEvents).map(EventType::fromValue)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public String getSecretCiphertext() {
        return secretCiphertext;
    }

    public String getSecretPrefix() {
        return secretPrefix;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WebhookEndpoint other)) return false;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /** Nunca inclui o segredo (nem cifrado) para não aparecer em logs. */
    @Override
    public String toString() {
        return "WebhookEndpoint[id=" + id + ", url=" + url + ", secretPrefix=" + secretPrefix + "]";
    }
}
