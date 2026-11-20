package com.moneyx.webhook.api;

import com.moneyx.webhook.exception.InvalidDeliveryCursorException;
import com.moneyx.webhook.exception.InvalidWebhookUrlException;
import com.moneyx.webhook.exception.WebhookDeliveryNotFailedException;
import com.moneyx.webhook.exception.WebhookDeliveryNotFoundException;
import com.moneyx.webhook.exception.WebhookEndpointDeletedException;
import com.moneyx.webhook.exception.WebhookEndpointUnavailableException;
import com.moneyx.webhook.exception.WebhookEndpointLimitExceededException;
import com.moneyx.webhook.exception.WebhookEndpointNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;

/** Erros do módulo de webhooks em Problem Details. Restrito aos controllers do módulo. */
@RestControllerAdvice(assignableTypes = {WebhookEndpointController.class, WebhookDeliveryController.class})
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

    @ExceptionHandler(WebhookEndpointDeletedException.class)
    public ProblemDetail handleDeleted(WebhookEndpointDeletedException ex) {
        return withCode(HttpStatus.CONFLICT, ex.getMessage(), "webhook_endpoint_deleted");
    }

    @ExceptionHandler(WebhookDeliveryNotFoundException.class)
    public ProblemDetail handleDeliveryNotFound(WebhookDeliveryNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(WebhookDeliveryNotFailedException.class)
    public ProblemDetail handleNotFailed(WebhookDeliveryNotFailedException ex) {
        ProblemDetail body = withCode(HttpStatus.CONFLICT, ex.getMessage(), "delivery_not_failed");
        body.setProperty("deliveryStatus", ex.getStatus().name().toLowerCase(Locale.ROOT));
        return body;
    }

    @ExceptionHandler(WebhookEndpointUnavailableException.class)
    public ProblemDetail handleUnavailable(WebhookEndpointUnavailableException ex) {
        return withCode(HttpStatus.CONFLICT, ex.getMessage(), "webhook_endpoint_unavailable");
    }

    @ExceptionHandler(InvalidDeliveryCursorException.class)
    public ProblemDetail handleInvalidCursor(InvalidDeliveryCursorException ex) {
        return withCode(HttpStatus.BAD_REQUEST, ex.getMessage(), "invalid_starting_after");
    }

    /** enabledEvents vazio ou com tipo desconhecido; limit ou status inválidos na listagem de entregas. */
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
