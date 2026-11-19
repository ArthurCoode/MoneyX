package com.moneyx.payment.api;

import com.moneyx.payment.exception.IdempotencyKeyReusedException;
import com.moneyx.payment.exception.InvalidIdempotencyKeyException;
import com.moneyx.payment.exception.PaymentExpiredException;
import com.moneyx.payment.exception.PaymentNotFoundException;
import com.moneyx.payment.exception.PaymentNotPendingException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz as exceções do módulo de pagamentos para Problem Details.
 *
 * assignableTypes: restrito aos controllers deste módulo. Sem isso, o tratamento de
 * IllegalArgumentException valeria para a API inteira e poderia mascarar bugs de outros módulos.
 */
@RestControllerAdvice(assignableTypes = {PaymentController.class, PaymentSandboxController.class})
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

    @ExceptionHandler(PaymentNotFoundException.class)
    public ProblemDetail handleNotFound(PaymentNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(PaymentNotPendingException.class)
    public ProblemDetail handleNotPending(PaymentNotPendingException ex) {
        ProblemDetail body = withCode(HttpStatus.CONFLICT, ex.getMessage(), "payment_not_pending");
        body.setProperty("paymentStatus", ex.getStatus().name());
        return body;
    }

    @ExceptionHandler(PaymentExpiredException.class)
    public ProblemDetail handleExpired(PaymentExpiredException ex) {
        return withCode(HttpStatus.CONFLICT, ex.getMessage(), "payment_expired");
    }

    /**
     * Rede de segurança: duas transações alteraram o mesmo pagamento ao mesmo tempo e o @Version barrou
     * a segunda. As mudanças de estado já usam lock pessimista, então isso não deveria ocorrer; se ocorrer,
     * respondemos 409 (o cliente pode consultar o estado e tentar de novo) em vez de 500.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrentUpdate(ObjectOptimisticLockingFailureException ex) {
        return withCode(HttpStatus.CONFLICT,
                "The payment was modified by another request. Retrieve it and try again", "concurrent_update");
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
