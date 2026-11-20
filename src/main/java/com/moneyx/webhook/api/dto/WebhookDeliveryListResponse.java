package com.moneyx.webhook.api.dto;

import com.moneyx.webhook.service.WebhookDeliveryPage;

import java.util.List;

/** Envelope {"data": [...], "hasMore": bool} (paginação por cursor). */
public record WebhookDeliveryListResponse(List<WebhookDeliveryResponse> data, boolean hasMore) {

    public static WebhookDeliveryListResponse from(WebhookDeliveryPage page) {
        return new WebhookDeliveryListResponse(
                page.deliveries().stream().map(WebhookDeliveryResponse::from).toList(), page.hasMore());
    }
}
