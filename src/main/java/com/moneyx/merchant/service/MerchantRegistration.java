package com.moneyx.merchant.service;

import com.moneyx.apikey.service.IssuedApiKey;
import com.moneyx.merchant.domain.Merchant;

/**
 * Resultado do cadastro: o comerciante criado e sua primeira API Key.
 */
public record MerchantRegistration(Merchant merchant, IssuedApiKey apiKey) {
}
