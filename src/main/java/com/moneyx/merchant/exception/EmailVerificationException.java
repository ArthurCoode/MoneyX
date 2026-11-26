package com.moneyx.merchant.exception;

import java.time.Duration;

/**
 * Falha na verificação de e-mail, com o código estável da API e o status HTTP.
 */
public class EmailVerificationException extends RuntimeException {

    public enum Reason {
        /** 400: token inexistente, já usado ou substituído. */
        INVALID_TOKEN("invalid_verification_token", 400, "The verification link is invalid or was already replaced"),
        /** 400: passou das 24 h. */
        EXPIRED_TOKEN("verification_token_expired", 400, "The verification link has expired. Request a new one"),
        /** 409: nada a reenviar. */
        ALREADY_VERIFIED("email_already_verified", 409, "The email is already verified"),
        /** 429: reenvio antes do intervalo mínimo. */
        TOO_SOON("verification_email_rate_limited", 429, "A verification email was sent recently. Try again later");

        public final String code;
        public final int status;
        public final String message;

        Reason(String code, int status, String message) {
            this.code = code;
            this.status = status;
            this.message = message;
        }
    }

    private final Reason reason;
    private final Duration retryAfter;

    public EmailVerificationException(Reason reason) {
        this(reason, null);
    }

    public EmailVerificationException(Reason reason, Duration retryAfter) {
        super(reason.message);
        this.reason = reason;
        this.retryAfter = retryAfter;
    }

    public Reason getReason() {
        return reason;
    }

    /** Só para TOO_SOON. */
    public Duration getRetryAfter() {
        return retryAfter;
    }
}
