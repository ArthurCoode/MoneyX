package com.moneyx.webhook.domain;

import java.util.Locale;

/**
 * Por que uma tentativa de entrega falhou. Gravado como código estável (ex.: "timeout"), nunca como a mensagem
 * da exceção, que pode conter detalhes internos.
 */
public enum WebhookDeliveryError {
    /** A loja não respondeu dentro do prazo. */
    TIMEOUT,
    /** Não foi possível conectar (DNS inexistente, conexão recusada, TLS inválido...). */
    CONNECTION_FAILED,
    /** O host resolve para um endereço interno (proteção contra SSRF). Nenhuma conexão foi feita. */
    DESTINATION_NOT_ALLOWED,
    /** A loja respondeu, mas sem 2xx (inclusive redirecionamentos, que não são seguidos). */
    NON_2XX_RESPONSE;

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
