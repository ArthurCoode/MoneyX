package com.moneyx.webhook.domain;

/**
 * O que aconteceu com um endpoint, na linha do tempo.
 * Novas ações (URL alterada, segredo rotacionado) entram aqui quando existirem (e no CHECK do banco).
 */
public enum WebhookEndpointAction {
    /** Pelo comerciante. */
    CREATED,
    /** Pelo comerciante. */
    DELETED,
    /** Pelo SISTEMA (falhas seguidas por WebhookEndpoint.AUTO_DISABLE_AFTER): o único registro sem API Key. */
    DISABLED,
    /** Pelo comerciante (reativação). */
    ENABLED;

    /** Ações automáticas não têm API Key; as do comerciante sempre têm. */
    public boolean isSystemAction() {
        return this == DISABLED;
    }
}
