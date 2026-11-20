package com.moneyx.webhook.exception;

/** O startingAfter não aponta para uma entrega deste endpoint (inexistente ou de outro comerciante). */
public class InvalidDeliveryCursorException extends RuntimeException {

    public InvalidDeliveryCursorException() {
        super("startingAfter must be the id of a delivery of this endpoint");
    }
}
