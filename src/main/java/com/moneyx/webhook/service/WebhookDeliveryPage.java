package com.moneyx.webhook.service;

import com.moneyx.webhook.domain.WebhookDelivery;

import java.util.List;

/** Uma página de entregas, mais recentes primeiro. */
public record WebhookDeliveryPage(List<WebhookDelivery> deliveries, boolean hasMore) {
}
