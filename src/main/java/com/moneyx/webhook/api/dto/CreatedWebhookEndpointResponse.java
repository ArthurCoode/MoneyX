package com.moneyx.webhook.api.dto;

import com.moneyx.event.domain.EventType;
import com.moneyx.webhook.service.CreatedWebhookEndpoint;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Resposta do cadastro: a ÚNICA resposta da API que contém o segredo de assinatura completo.
 * Enviada com Cache-Control: no-store; toString mascarado para não vazar em logs.
 */
public record CreatedWebhookEndpointResponse(
        UUID id,
        String url,
        List<String> enabledEvents,
        String secret,
        String secretPrefix,
        Instant createdAt
) {

    public static CreatedWebhookEndpointResponse from(CreatedWebhookEndpoint created) {
        return new CreatedWebhookEndpointResponse(
                created.endpoint().getId(),
                created.endpoint().getUrl(),
                created.endpoint().getEnabledEvents().stream().map(EventType::value).toList(),
                created.signingSecret(),
                created.endpoint().getSecretPrefix(),
                created.endpoint().getCreatedAt());
    }

    @Override
    public String toString() {
        return "CreatedWebhookEndpointResponse[id=" + id + ", secretPrefix=" + secretPrefix + ", secret=***]";
    }
}
