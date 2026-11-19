package com.moneyx.apikey.exception;

/** O comerciante já possui o número máximo de chaves ativas. */
public class ApiKeyLimitExceededException extends RuntimeException {

    public ApiKeyLimitExceededException(int limit) {
        super("Maximum of " + limit + " active API keys reached. Revoke an existing key before creating a new one");
    }
}
