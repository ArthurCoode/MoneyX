package com.moneyx.webhook.domain;

/**
 * PENDING ──┬──> SUCCEEDED  (a loja respondeu 2xx)
 *    ▲  │   ├──> FAILED     (desistiu: agenda esgotada ou destino bloqueado)
 *    │  │   └──> CANCELED   (o endpoint foi removido ou desativado antes do envio)
 *    │  └─ falha com tentativas restantes: continua PENDING, com nextAttemptAt no futuro
 *    └──── reenvio manual de uma FAILED (requeue)
 */
public enum WebhookDeliveryStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    CANCELED
}
