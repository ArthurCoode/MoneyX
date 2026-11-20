package com.moneyx.webhook.api;

import com.moneyx.webhook.exception.InvalidWebhookUrlException;
import com.moneyx.webhook.exception.WebhookEndpointLimitExceededException;
import com.moneyx.webhook.exception.WebhookEndpointNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Erros do módulo de webhooks em Problem Details. Restrito ao WebhookEndpointController. */
@RestControllerAdvice(assignableTypes = WebhookEndpointController.class)
public class WebhookEndpointExceptionHandler {

    @ExceptionHandler(WebhookEndpointNotFoundException.class)
    public ProblemDetail handleNotFound(WebhookEndpointNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(WebhookEndpointLimitExceededException.class)
    public ProblemDetail handleLimit(WebhookEndpointLimitExceededException ex) {
        return withCode(HttpStatus.CONFLICT, ex.getMessage(), "webhook_endpoint_limit_reached");
    }

    @ExceptionHandler(InvalidWebhookUrlException.class)
    public ProblemDetail handleInvalidUrl(InvalidWebhookUrlException ex) {
        return withCode(HttpStatus.BAD_REQUEST, ex.getMessage(), "invalid_webhook_url");
    }

    /** enabledEvents vazio ou com tipo desconhecido. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private static ProblemDetail withCode(HttpStatus status, String detail, String code) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setProperty("code", code);
        return body;
    }
}
