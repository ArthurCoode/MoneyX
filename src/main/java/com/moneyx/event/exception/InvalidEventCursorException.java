package com.moneyx.event.exception;

/** O startingAfter não aponta para um evento do comerciante (inexistente ou de outro comerciante). */
public class InvalidEventCursorException extends RuntimeException {

    public InvalidEventCursorException() {
        super("startingAfter must be the id of one of your events");
    }
}
