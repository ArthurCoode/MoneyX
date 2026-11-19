package com.moneyx.apikey.api;

import com.moneyx.apikey.exception.ApiKeyLimitExceededException;
import com.moneyx.apikey.exception.ApiKeyNotFoundException;
import com.moneyx.apikey.exception.LastActiveApiKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz as exceções de negócio do módulo de API Keys para Problem Details.
 *
 * Os erros de regra de negócio (409) trazem a propriedade "code": um identificador estável,
 * pensado para ser lido por programas (ex.: if (code == "last_active_api_key")). O texto de
 * "detail" é para humanos e pode mudar; o "code" é contrato. Padrão de gateways como o Stripe.
 */
@RestControllerAdvice
public class ApiKeyExceptionHandler {

    @ExceptionHandler(ApiKeyNotFoundException.class)
    public ProblemDetail handleNotFound(ApiKeyNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ApiKeyLimitExceededException.class)
    public ProblemDetail handleLimitExceeded(ApiKeyLimitExceededException ex) {
        return conflict(ex, "api_key_limit_reached");
    }

    @ExceptionHandler(LastActiveApiKeyException.class)
    public ProblemDetail handleLastActiveKey(LastActiveApiKeyException ex) {
        return conflict(ex, "last_active_api_key");
    }

    private static ProblemDetail conflict(RuntimeException ex, String code) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        body.setProperty("code", code);
        return body;
    }
}
