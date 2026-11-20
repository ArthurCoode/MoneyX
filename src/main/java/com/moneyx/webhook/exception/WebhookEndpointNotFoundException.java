package com.moneyx.webhook.exception;

/** O endpoint não existe OU pertence a outro comerciante (indistinguíveis de propósito). */
public class WebhookEndpointNotFoundException extends RuntimeException {

    public WebhookEndpointNotFoundException() {
        super("Webhook endpoint not found");
    }
}
