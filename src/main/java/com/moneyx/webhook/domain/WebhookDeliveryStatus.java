package com.moneyx.webhook.domain;

/**
 * PENDING ──┬──> SUCCEEDED  (a loja respondeu 2xx)
 *           ├──> FAILED     (nesta etapa: uma tentativa; retentativas na Etapa 4)
 *           └──> CANCELED   (o endpoint foi removido antes do envio)
 */
public enum WebhookDeliveryStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    CANCELED
}
