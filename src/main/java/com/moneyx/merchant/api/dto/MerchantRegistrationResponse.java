package com.moneyx.merchant.api.dto;

import com.moneyx.apikey.api.dto.CreatedApiKeyResponse;
import com.moneyx.merchant.service.MerchantRegistration;

/**
 * Resposta do cadastro (POST /v1/merchants).
 *
 * {
 *   "merchant": { ... },
 *   "apiKey":   { "id": ..., "key": "mx_test_...", "prefix": ..., "createdAt": ... }
 * }
 *
 * O campo apiKey.key é exibido apenas nesta resposta e não pode ser recuperado depois.
 */
public record MerchantRegistrationResponse(
        MerchantResponse merchant,
        CreatedApiKeyResponse apiKey
) {

    public static MerchantRegistrationResponse from(MerchantRegistration registration) {
        return new MerchantRegistrationResponse(
                MerchantResponse.from(registration.merchant()),
                CreatedApiKeyResponse.from(registration.apiKey())
        );
    }
}
