package com.moneyx.webhook.exception;

/** A entrega não pode ser reenviada porque o endpoint dela foi removido ou está desativado. */
public class WebhookEndpointUnavailableException extends RuntimeException {

    public WebhookEndpointUnavailableException() {
        super("The webhook endpoint of this delivery is deleted or disabled. Enable it before retrying");
    }
}
