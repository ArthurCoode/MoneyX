package com.moneyx.webhook.exception;

import com.moneyx.webhook.domain.WebhookDeliveryStatus;

/** Só entregas FAILED podem ser reenviadas manualmente. */
public class WebhookDeliveryNotFailedException extends RuntimeException {

    private final WebhookDeliveryStatus status;

    public WebhookDeliveryNotFailedException(WebhookDeliveryStatus status) {
        super("Only FAILED deliveries can be retried. This delivery is " + status);
        this.status = status;
    }

    public WebhookDeliveryStatus getStatus() {
        return status;
    }
}
