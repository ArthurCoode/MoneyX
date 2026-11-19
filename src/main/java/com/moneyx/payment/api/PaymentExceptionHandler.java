package com.moneyx.payment.api;

import com.moneyx.payment.exception.IdempotencyKeyReusedException;
import com.moneyx.payment.exception.InvalidIdempotencyKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz as exceções do módulo de pagamentos para Problem Details.
 *
 * assignableTypes: restrito aos controllers deste módulo. Sem isso, o tratamento de
 * IllegalArgumentException valeria para a API inteira e poderia mascarar bugs de outros módulos.
 */
@RestControllerAdvice(assignableTypes = PaymentController.class)
public class PaymentExceptionHandler {

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    public ProblemDetail handleInvalidKey(InvalidIdempotencyKeyException ex) {
        return withCode(HttpStatus.BAD_REQUEST, ex.getMessage(), "invalid_idempotency_key");
    }

    /** 422: a requisição é sintaticamente válida, mas contradiz o uso anterior da mesma chave. */
    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ProblemDetail handleKeyReused(IdempotencyKeyReusedException ex) {
        return withCode(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), "idempotency_key_reused");
    }

    /**
     * Regra do domínio violada (valor, prazo, textos). A validação do DTO já cobre esses limites;
     * este tratamento é uma segunda linha de defesa para nunca responder 500 por dado inválido.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidPayment(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private static ProblemDetail withCode(HttpStatus status, String detail, String code) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setProperty("code", code);
        return body;
    }
}
