package com.moneyx.payment.exception;

/**
 * O startingAfter não aponta para um pagamento do comerciante (inexistente ou de outro comerciante,
 * indistinguíveis de propósito).
 */
public class InvalidCursorException extends RuntimeException {

    public InvalidCursorException() {
        super("startingAfter must be the id of one of your payments");
    }
}
