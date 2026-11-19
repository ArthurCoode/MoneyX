package com.moneyx.merchant.exception;

import java.util.UUID;

/**
 * Lançada quando um Merchant não é encontrado.
 * Na camada HTTP será traduzida para 404 Not Found.
 */
public class MerchantNotFoundException extends RuntimeException {

    public MerchantNotFoundException(UUID id) {
        super("Merchant not found: " + id);
    }
}
