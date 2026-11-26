package com.moneyx.merchant.exception;

import java.time.Duration;

/** Muitas tentativas de login ou de redefinição (429 rate_limit_exceeded + Retry-After). */
public class TooManyAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyAttemptsException(Duration retryAfter) {
        super("Too many attempts. Try again later");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
