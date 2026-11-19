package com.moneyx.apikey.exception;

/**
 * A chave não existe OU pertence a outro comerciante. Os dois casos são indistinguíveis
 * de propósito: revelar que a chave existe já seria um vazamento entre comerciantes.
 */
public class ApiKeyNotFoundException extends RuntimeException {

    public ApiKeyNotFoundException() {
        super("API key not found");
    }
}
