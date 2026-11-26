package com.moneyx.apikey.exception;

/** Chave mx_live_ pedida por comerciante sem e-mail verificado (403 email_not_verified). */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Verify your email before creating live API keys (mx_live_). "
                + "Use POST /v1/merchants/me/email-verification to get a new link");
    }
}
