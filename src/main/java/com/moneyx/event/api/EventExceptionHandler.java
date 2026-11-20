package com.moneyx.event.api;

import com.moneyx.event.exception.EventNotFoundException;
import com.moneyx.event.exception.InvalidEventCursorException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Erros do módulo de eventos em Problem Details. Restrito ao EventController (ver PaymentExceptionHandler). */
@RestControllerAdvice(assignableTypes = EventController.class)
public class EventExceptionHandler {

    @ExceptionHandler(EventNotFoundException.class)
    public ProblemDetail handleNotFound(EventNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidEventCursorException.class)
    public ProblemDetail handleInvalidCursor(InvalidEventCursorException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        body.setProperty("code", "invalid_starting_after");
        return body;
    }

    /** Parâmetros inválidos da listagem (limit fora da faixa, type desconhecido). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidParameter(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
