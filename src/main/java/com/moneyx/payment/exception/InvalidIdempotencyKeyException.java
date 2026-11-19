package com.moneyx.payment.exception;

/** O header Idempotency-Key está vazio, longo demais ou contém caracteres não permitidos. */
public class InvalidIdempotencyKeyException extends RuntimeException {

    public InvalidIdempotencyKeyException() {
        super("Idempotency-Key must have 1 to 255 visible ASCII characters (a UUID is recommended)");
    }
}
