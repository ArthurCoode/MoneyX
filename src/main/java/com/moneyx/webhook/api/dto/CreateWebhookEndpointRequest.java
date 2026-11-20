package com.moneyx.webhook.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Corpo de POST /v1/webhook-endpoints.
 *
 * As regras de URL (https, sem destino interno etc.) ficam no WebhookUrlValidator, que devolve o motivo exato.
 *
 * @param enabledEvents opcional; ausente = todos os tipos. Lista vazia ou tipo desconhecido = 400.
 */
public record CreateWebhookEndpointRequest(@NotBlank String url, List<String> enabledEvents) {
}
