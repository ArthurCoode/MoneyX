package com.moneyx.webhook.exception;

/** A URL não atende às regras (https, host válido, sem destino interno). A mensagem diz qual regra falhou. */
public class InvalidWebhookUrlException extends RuntimeException {

    public InvalidWebhookUrlException(String reason) {
        super(reason);
    }
}
