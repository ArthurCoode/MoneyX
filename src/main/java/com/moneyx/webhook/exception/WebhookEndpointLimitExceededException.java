package com.moneyx.webhook.exception;

/** O comerciante já tem o máximo de endpoints ativos. */
public class WebhookEndpointLimitExceededException extends RuntimeException {

    public WebhookEndpointLimitExceededException(int limit) {
        super("A merchant can have at most " + limit + " active webhook endpoints");
    }
}
