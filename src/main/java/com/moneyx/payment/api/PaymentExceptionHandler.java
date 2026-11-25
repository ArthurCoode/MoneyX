package com.moneyx.payment.api;

import com.moneyx.payment.exception.IdempotencyKeyReusedException;
import com.moneyx.payment.exception.InvalidCursorException;
import com.moneyx.payment.exception.InvalidIdempotencyKeyException;
import com.moneyx.payment.exception.PaymentExpiredException;
import com.moneyx.payment.exception.PaymentNotFoundException;
import com.moneyx.payment.exception.PaymentNotPendingException;
import com.moneyx.payment.exception.PaymentProviderRejectedException;
import com.moneyx.payment.exception.PaymentProviderUnavailableException;
import com.moneyx.payment.exception.SandboxPaymentNotSupportedException;
import com.moneyx.payment.exception.VolumeLimitExceededException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    /**
     * 422: o pedido é válido, mas o comerciante não pode criar mais volume neste período.
     * Valores em centavos; resetsAt diz quando o período recomeça. Só dados do próprio comerciante.
     */
    @ExceptionHandler(VolumeLimitExceededException.class)
    public ProblemDetail handleVolumeLimit(VolumeLimitExceededException ex) {
        ProblemDetail body = withCode(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), "volume_limit_exceeded");
        body.setProperty("period", ex.getPeriod().wireName());
        body.setProperty("limit", ex.getLimit());
        body.setProperty("remaining", ex.getRemaining());
        body.setProperty("resetsAt", ex.getResetsAt());
        return body;
    }

    /**
     * 503: o provedor de Pix está fora do ar. O pagamento existe (CREATING); repetir com a MESMA Idempotency-Key retoma
     * a criação. Retry-After em segundos, arredondado para cima.
     */
    @ExceptionHandler(PaymentProviderUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleProviderUnavailable(PaymentProviderUnavailableException ex) {
        long seconds = Math.max(1, (ex.getRetryAfter().toMillis() + 999) / 1000);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
                .body(withCode(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), "payment_provider_unavailable"));
    }

    /** 502: o provedor recusou a cobrança. Nada foi criado; a chave ficou livre. */
    @ExceptionHandler(PaymentProviderRejectedException.class)
    public ProblemDetail handleProviderRejected(PaymentProviderRejectedException ex) {
        return withCode(HttpStatus.BAD_GATEWAY, ex.getMessage(), "payment_provider_error");
    }

    @ExceptionHandler(InvalidCursorException.class)
    public ProblemDetail handleInvalidCursor(InvalidCursorException ex) {
        return withCode(HttpStatus.BAD_REQUEST, ex.getMessage(), "invalid_starting_after");
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

    @ExceptionHandler(SandboxPaymentNotSupportedException.class)
    public ProblemDetail handleSandboxNotSupported(SandboxPaymentNotSupportedException ex) {
        return withCode(HttpStatus.CONFLICT, ex.getMessage(), "sandbox_payment_not_supported");
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
     * Regra violada em dado de entrada: parâmetros da listagem (limit, externalReference, validados em
     * PaymentListQuery) ou regras do domínio (valor, prazo, textos). Na criação, a validação do DTO já cobre
     * esses limites; aqui fica a segunda linha de defesa para nunca responder 500 por dado inválido.
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
