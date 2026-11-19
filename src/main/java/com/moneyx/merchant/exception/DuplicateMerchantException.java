package com.moneyx.merchant.exception;

/**
 * Lançada quando já existe um Merchant com o mesmo documento ou e-mail.
 * Na camada HTTP será traduzida para 409 Conflict.
 */
public class DuplicateMerchantException extends RuntimeException {

    public DuplicateMerchantException(String message) {
        super(message);
    }
}
