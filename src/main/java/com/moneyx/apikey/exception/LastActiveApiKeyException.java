package com.moneyx.apikey.exception;

/**
 * Revogar esta chave deixaria o comerciante sem nenhuma chave ativa e, portanto,
 * sem nenhuma forma de acessar a API (não existe login por senha).
 */
public class LastActiveApiKeyException extends RuntimeException {

    public LastActiveApiKeyException() {
        super("Cannot revoke the last active API key. Create a new key before revoking this one");
    }
}
