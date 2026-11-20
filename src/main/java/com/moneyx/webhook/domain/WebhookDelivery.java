package com.moneyx.webhook.domain;

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
 * Uma entrega de um evento para um endpoint (o registro do Outbox).
 *
 * Reserva com prazo (lease): claim() marca leaseUntil e um leaseToken novo. Só quem tem o token da reserva atual
 * pode gravar o resultado (recordSuccess/recordFailure). Se a reserva vencer e outra execução reservar de novo,
 * o resultado atrasado da primeira é descartado (o token não confere mais).
 */
@Entity
@Table(name = "webhook_deliveries")
public class WebhookDelivery {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "endpoint_id", nullable = false, updatable = false)
    private UUID endpointId;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebhookDeliveryStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "lease_token")
    private UUID leaseToken;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_status_code")
    private Integer lastStatusCode;

    @Column(name = "last_error", length = 50)
    private String lastError;

    @Column(name = "last_duration_ms")
    private Integer lastDurationMs;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected WebhookDelivery() {
    }

    /** Nasce PENDING e pronta para envio imediato. */
    public static WebhookDelivery pending(UUID eventId, UUID endpointId, UUID merchantId, Instant now) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.id = UUID.randomUUID();
        delivery.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        delivery.endpointId = Objects.requireNonNull(endpointId, "endpointId must not be null");
        delivery.merchantId = Objects.requireNonNull(merchantId, "merchantId must not be null");
        delivery.status = WebhookDeliveryStatus.PENDING;
        delivery.attempts = 0;
        delivery.nextAttemptAt = Objects.requireNonNull(now, "now must not be null");
        delivery.createdAt = now;
        return delivery;
    }

    /** Reserva para envio até now + lease. Devolve o token que identifica ESTA reserva. */
    public UUID claim(Instant now, Duration lease) {
        requirePending();
        if (leaseUntil != null && leaseUntil.isAfter(now)) {
            throw new IllegalStateException("Delivery is already leased until " + leaseUntil);
        }
        this.leaseUntil = now.plus(lease);
        this.leaseToken = UUID.randomUUID();
        return leaseToken;
    }

    public boolean isLeasedBy(UUID token) {
        return status == WebhookDeliveryStatus.PENDING && token != null && token.equals(leaseToken);
    }

    public void recordSuccess(UUID token, int statusCode, long durationMs, Instant now) {
        registerAttempt(token, statusCode, durationMs, now);
        this.lastError = null;
        this.deliveredAt = now;
        this.status = WebhookDeliveryStatus.SUCCEEDED;
    }

    /** Nesta etapa toda falha é definitiva (FAILED); a Etapa 4 decidirá entre reagendar ou desistir. */
    public void recordFailure(UUID token, WebhookDeliveryError error, Integer statusCode, long durationMs, Instant now) {
        registerAttempt(token, statusCode, durationMs, now);
        this.lastError = Objects.requireNonNull(error, "error must not be null").code();
        this.status = WebhookDeliveryStatus.FAILED;
    }

    /** O endpoint foi removido: a entrega não será enviada. */
    public void cancel(UUID token) {
        requireLease(token);
        clearLease();
        this.status = WebhookDeliveryStatus.CANCELED;
    }

    private void registerAttempt(UUID token, Integer statusCode, long durationMs, Instant now) {
        requireLease(token);
        clearLease();
        this.attempts++;
        this.lastAttemptAt = Objects.requireNonNull(now, "now must not be null");
        this.lastStatusCode = statusCode;
        this.lastDurationMs = (int) Math.min(durationMs, Integer.MAX_VALUE);
    }

    private void requireLease(UUID token) {
        if (!isLeasedBy(token)) {
            throw new IllegalStateException("Delivery is not leased by this token");
        }
    }

    private void requirePending() {
        if (status != WebhookDeliveryStatus.PENDING) {
            throw new IllegalStateException("Only PENDING deliveries can be claimed. Current status: " + status);
        }
    }

    private void clearLease() {
        this.leaseUntil = null;
        this.leaseToken = null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public WebhookDeliveryStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public Integer getLastStatusCode() {
        return lastStatusCode;
    }

    public String getLastError() {
        return lastError;
    }

    public Integer getLastDurationMs() {
        return lastDurationMs;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WebhookDelivery other)) return false;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
