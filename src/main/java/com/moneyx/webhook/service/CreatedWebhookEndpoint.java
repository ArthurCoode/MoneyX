package com.moneyx.webhook.service;

import com.moneyx.webhook.domain.WebhookEndpoint;

/**
 * Endpoint recém-criado + segredo em texto puro (entregue uma única vez). toString mascarado.
 */
public record CreatedWebhookEndpoint(WebhookEndpoint endpoint, String signingSecret) {

    @Override
    public String toString() {
        return "CreatedWebhookEndpoint[endpoint=" + endpoint + ", signingSecret=***]";
    }
}
