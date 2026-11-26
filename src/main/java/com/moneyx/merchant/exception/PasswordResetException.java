package com.moneyx.merchant.exception;

/** Link de redefinição inválido/usado/substituído (400 invalid_reset_token) ou vencido (400 reset_token_expired). */
public class PasswordResetException extends RuntimeException {

    private final String code;

    private PasswordResetException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static PasswordResetException invalid() {
        return new PasswordResetException("invalid_reset_token", "The password reset link is invalid or was already used");
    }

    public static PasswordResetException expired() {
        return new PasswordResetException("reset_token_expired", "The password reset link has expired. Request a new one");
    }

    public String getCode() {
        return code;
    }
}
