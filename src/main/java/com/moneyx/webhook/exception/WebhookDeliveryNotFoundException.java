package com.moneyx.webhook.exception;

/** A entrega não existe OU pertence a outro comerciante (indistinguíveis de propósito). */
public class WebhookDeliveryNotFoundException extends RuntimeException {

    public WebhookDeliveryNotFoundException() {
        super("Webhook delivery not found");
    }
}
