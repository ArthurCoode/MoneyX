package com.moneyx.webhook.api.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.moneyx.webhook.domain.WebhookEndpointHistory;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Linha do tempo de um endpoint: {"data": [{id, action, apiKeyId, occurredAt, snapshot}, ...]}, do mais antigo
 * para o mais recente. action em minúsculas ("created", "deleted"), como os tipos de evento.
 */
public record WebhookEndpointHistoryResponse(List<Entry> data) {

    public static WebhookEndpointHistoryResponse from(List<WebhookEndpointHistory> entries) {
        return new WebhookEndpointHistoryResponse(entries.stream().map(Entry::from).toList());
    }

    @io.swagger.v3.oas.annotations.media.Schema(name = "WebhookEndpointHistoryEntry")
    public record Entry(UUID id, String action, UUID apiKeyId, Instant occurredAt, @io.swagger.v3.oas.annotations.media.Schema(types = {"object"}, description = "url e enabledEvents naquele momento (nunca o segredo).") @JsonRawValue String snapshot) {

        static Entry from(WebhookEndpointHistory entry) {
            return new Entry(entry.getId(), entry.getAction().name().toLowerCase(Locale.ROOT),
                    entry.getApiKeyId(), entry.getOccurredAt(), entry.getSnapshot());
        }
    }
}
