package com.moneyx.payment.exception;

/** O prazo de pagamento terminou (mesmo que o status ainda não tenha sido atualizado para EXPIRED). */
public class PaymentExpiredException extends RuntimeException {

    public PaymentExpiredException() {
        super("Payment has expired and can no longer be paid");
    }
}
