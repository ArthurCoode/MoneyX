package com.moneyx.webhook.domain;

/**
 * O que aconteceu com um endpoint, na linha do tempo.
 * Novas ações (URL alterada, segredo rotacionado, desativado por falhas) entram aqui quando existirem.
 */
public enum WebhookEndpointAction {
    CREATED,
    DELETED
}
