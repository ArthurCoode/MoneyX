package com.moneyx.webhook.exception;

/** O endpoint foi removido; remoção é definitiva e ele não pode ser reativado. */
public class WebhookEndpointDeletedException extends RuntimeException {

    public WebhookEndpointDeletedException() {
        super("The webhook endpoint was deleted and cannot be enabled");
    }
}
