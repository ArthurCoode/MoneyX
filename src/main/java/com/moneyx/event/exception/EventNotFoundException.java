package com.moneyx.event.exception;

/** O evento não existe OU pertence a outro comerciante (indistinguíveis de propósito). */
public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException() {
        super("Event not found");
    }
}
