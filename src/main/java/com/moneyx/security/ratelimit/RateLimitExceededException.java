package com.moneyx.security.ratelimit;

import java.time.Duration;

/** Limite atingido. Vira 429 + Retry-After no SecurityExceptionHandler. */
public class RateLimitExceededException extends RuntimeException {

    private final Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter) {
        super("Too many requests. Try again later");
        this.retryAfter = retryAfter;
    }

    /** Segundos inteiros, arredondados para CIMA (Retry-After não aceita fração; para baixo, o cliente voltaria cedo). */
    public long retryAfterSeconds() {
        long seconds = retryAfter.toSeconds();
        return retryAfter.minusSeconds(seconds).isZero() ? Math.max(1, seconds) : seconds + 1;
    }
}
