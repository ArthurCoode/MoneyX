package com.moneyx.webhook.domain;

import com.moneyx.event.domain.EventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
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

    /** Atualizável SÓ pela rotação da chave de cifragem (replaceSecretCiphertext): o segredo em si nunca muda. */
    @Column(name = "secret_ciphertext", nullable = false)
    private String secretCiphertext;

    @Column(name = "secret_prefix", nullable = false, updatable = false, length = 20)
    private String secretPrefix;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Preenchido quando o MoneyX desativa o endpoint por falhas seguidas (reativável pelo comerciante). */
    @Column(name = "disabled_at")
    private Instant disabledAt;

    /** Início da sequência ATUAL de falhas de entrega; null enquanto as entregas estiverem dando certo. */
    @Column(name = "failing_since")
    private Instant failingSince;

    @Version
    private Long version;

    /** Sem nenhuma entrega bem-sucedida por este tempo, o endpoint é desativado automaticamente. */
    public static final Duration AUTO_DISABLE_AFTER = Duration.ofDays(3);

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

    /** Não removido (pode estar desativado). Conta no limite de endpoints, pois pode ser reativado. */
    public boolean isActive() {
        return deletedAt == null;
    }

    /** Não removido E não desativado: recebe entregas. */
    public boolean isEnabled() {
        return isActive() && disabledAt == null;
    }

    /** O endpoint quer (e pode) receber este tipo de evento? */
    public boolean accepts(EventType type) {
        return isEnabled() && Arrays.asList(enabledEvents).contains(type.value());
    }

    /** Uma entrega deu certo: a sequência de falhas acabou. */
    public void recordDeliverySuccess() {
        this.failingSince = null;
    }

    /**
     * Uma entrega falhou. Inicia a sequência de falhas (se ainda não havia uma) e, se ela já dura AUTO_DISABLE_AFTER,
     * DESATIVA o endpoint.
     *
     * @return true se o endpoint foi desativado agora (quem chama registra isso na linha do tempo)
     */
    public boolean recordDeliveryFailure(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (!isEnabled()) {
            return false;
        }
        if (failingSince == null) {
            this.failingSince = now;
            return false;
        }
        if (Duration.between(failingSince, now).compareTo(AUTO_DISABLE_AFTER) >= 0) {
            this.disabledAt = now;
            return true;
        }
        return false;
    }

    /**
     * Reativação pelo comerciante. Idempotente: reativar um endpoint que já está ativo não muda nada.
     *
     * @return true se reativou agora
     * @throws IllegalStateException se o endpoint foi removido (remoção é definitiva)
     */
    /**
     * Troca o texto cifrado do segredo pelo MESMO segredo cifrado com outra chave (rotação da chave de cifragem).
     * Não muda o segredo de assinatura: a loja não percebe nada.
     */
    public void replaceSecretCiphertext(String newCiphertext) {
        this.secretCiphertext = Objects.requireNonNull(newCiphertext, "newCiphertext must not be null");
    }

    public boolean enable() {
        if (!isActive()) {
            throw new IllegalStateException("A deleted webhook endpoint cannot be enabled");
        }
        if (disabledAt == null) {
            return false;
        }
        this.disabledAt = null;
        this.failingSince = null; // recomeça do zero: a próxima falha inicia uma sequência nova
        return true;
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

    public Instant getDisabledAt() {
        return disabledAt;
    }

    public Instant getFailingSince() {
        return failingSince;
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
