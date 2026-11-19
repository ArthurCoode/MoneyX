package com.moneyx.merchant.api;

import com.moneyx.merchant.exception.DuplicateMerchantException;
import com.moneyx.merchant.exception.MerchantNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz as exceções de negócio do módulo Merchant para respostas HTTP.
 *
 * O serviço lança exceções de domínio sem saber nada de HTTP;
 * a decisão "isso é um 404" ou "isso é um 409" pertence à camada api.
 */
@RestControllerAdvice
public class MerchantExceptionHandler {

    @ExceptionHandler(MerchantNotFoundException.class)
    public ProblemDetail handleNotFound(MerchantNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DuplicateMerchantException.class)
    public ProblemDetail handleDuplicate(DuplicateMerchantException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }
}
