package com.moneyx.apikey.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

/**
 * Corpo OPCIONAL de POST /v1/api-keys. Sem corpo = chave de teste.
 *
 * @param mode "test" (padrão, mx_test_) ou "live" (mx_live_, exige e-mail verificado)
 */
public record CreateApiKeyRequest(
        @Schema(description = "test (padrão) ou live", allowableValues = {"test", "live"})
        @Pattern(regexp = "test|live", message = "must be test or live")
        String mode
) {

    public boolean live() {
        return "live".equals(mode);
    }
}
