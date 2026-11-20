package com.moneyx.webhook.api.dto;

import com.moneyx.webhook.domain.WebhookDelivery;
import com.moneyx.webhook.domain.WebhookDeliveryStatus;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Uma entrega, para a loja depurar: quantas tentativas, o último erro (código estável), o status HTTP e quando será
 * a próxima tentativa. Nunca contém o corpo enviado nem a resposta da loja (o corpo é o evento: GET /v1/events/{eventId}).
 *
 * nextAttemptAt só aparece enquanto a entrega estiver pendente.
 */
public record WebhookDeliveryResponse(
        UUID id,
        UUID endpointId,
        UUID eventId,
        String status,
        int attempts,
        Instant nextAttemptAt,
        Instant lastAttemptAt,
        Integer lastStatusCode,
        String lastError,
        Integer lastDurationMs,
        Instant deliveredAt,
        Instant createdAt
) {

    public static WebhookDeliveryResponse from(WebhookDelivery delivery) {
        boolean pending = delivery.getStatus() == WebhookDeliveryStatus.PENDING;
        return new WebhookDeliveryResponse(
                delivery.getId(),
                delivery.getEndpointId(),
                delivery.getEventId(),
                delivery.getStatus().name().toLowerCase(Locale.ROOT),
                delivery.getAttempts(),
                pending ? delivery.getNextAttemptAt() : null,
                delivery.getLastAttemptAt(),
                delivery.getLastStatusCode(),
                delivery.getLastError(),
                delivery.getLastDurationMs(),
                delivery.getDeliveredAt(),
                delivery.getCreatedAt());
    }
}
