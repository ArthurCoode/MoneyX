package com.moneyx.webhook.api.dto;

import com.moneyx.event.domain.EventType;
import com.moneyx.webhook.domain.WebhookEndpoint;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Representação pública de um endpoint. NUNCA contém o segredo: apenas o prefixo, para identificação.
 * deletedAt != null indica endpoint removido (ele continua consultável, com a sua linha do tempo).
 * disabledAt != null indica endpoint desativado automaticamente por falhas (reative com POST /{id}/enable).
 */
public record WebhookEndpointResponse(
        UUID id,
        String url,
        List<String> enabledEvents,
        String secretPrefix,
        Instant createdAt,
        Instant deletedAt,
        Instant disabledAt
) {

    public static WebhookEndpointResponse from(WebhookEndpoint endpoint) {
        return new WebhookEndpointResponse(
                endpoint.getId(),
                endpoint.getUrl(),
                endpoint.getEnabledEvents().stream().map(EventType::value).toList(),
                endpoint.getSecretPrefix(),
                endpoint.getCreatedAt(),
                endpoint.getDeletedAt(),
                endpoint.getDisabledAt());
    }
}
