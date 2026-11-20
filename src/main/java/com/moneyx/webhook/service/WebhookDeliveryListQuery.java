package com.moneyx.webhook.service;

import com.moneyx.webhook.domain.WebhookDeliveryStatus;

import java.util.UUID;

/** Paginação por cursor das entregas de um endpoint (mesmo contrato das outras listagens). */
public record WebhookDeliveryListQuery(Integer limit, UUID startingAfter, WebhookDeliveryStatus status) {

    public static final int DEFAULT_LIMIT = 10;
    public static final int MAX_LIMIT = 100;

    public WebhookDeliveryListQuery {
        if (limit == null) {
            limit = DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
    }

    public static WebhookDeliveryListQuery firstPage() {
        return new WebhookDeliveryListQuery(null, null, null);
    }
}
