package com.moneyx.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Fato que aconteceu com um recurso do comerciante (ex.: "pagamento X foi pago").
 *
 * IMUTÁVEL: não há métodos de alteração e todas as colunas são updatable = false. Um evento descreve o
 * passado; se algo mudar de novo, nasce outro evento.
 *
 * data: retrato do recurso no momento do evento, já em JSON (mesmo formato da API). Guardar o retrato,
 * e não só o id, permite ao comerciante agir sem consultar a API, e mostra o estado DAQUELE momento
 * (um "payment.created" continua mostrando PENDING mesmo depois que o pagamento foi pago).
 */
@Entity
@Table(name = "events")
public class Event {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Convert(converter = EventTypeConverter.class)
    @Column(nullable = false, updatable = false, length = 50)
    private EventType type;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    /** JSON já serializado; a coluna é JSONB. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private String data;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Event() {
    }

    public static Event of(UUID merchantId, EventType type, UUID resourceId, String data, Instant occurredAt) {
        Event event = new Event();
        event.id = UUID.randomUUID();
        event.merchantId = Objects.requireNonNull(merchantId, "merchantId must not be null");
        event.type = Objects.requireNonNull(type, "type must not be null");
        event.resourceId = Objects.requireNonNull(resourceId, "resourceId must not be null");
        event.data = Objects.requireNonNull(data, "data must not be null");
        event.createdAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        return event;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public EventType getType() {
        return type;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public String getData() {
        return data;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Event other)) return false;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
