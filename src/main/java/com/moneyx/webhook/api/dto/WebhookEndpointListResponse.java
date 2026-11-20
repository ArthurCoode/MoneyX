package com.moneyx.webhook.api.dto;

import java.util.List;

/** Envelope {"data": [...]}. Sem paginação: são no máximo 5 endpoints ativos. */
public record WebhookEndpointListResponse(List<WebhookEndpointResponse> data) {
}
