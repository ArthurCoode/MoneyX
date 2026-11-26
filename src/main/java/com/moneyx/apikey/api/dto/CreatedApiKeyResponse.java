package com.moneyx.apikey.api.dto;

import com.moneyx.apikey.service.IssuedApiKey;

import java.time.Instant;
import java.util.UUID;

/**
 * Representação de uma chave recém-criada. É a única resposta da API que contém o
 * segredo completo (`key`). Também será usada pelo endpoint de criação de chaves.
 */
public record CreatedApiKeyResponse(
        UUID id,
        String key,
        String prefix,
        /** "test" (mx_test_) ou "live" (mx_live_). */
        String mode,
        Instant createdAt
) {

    public static CreatedApiKeyResponse from(IssuedApiKey issued) {
        return new CreatedApiKeyResponse(
                issued.apiKey().getId(),
                issued.plainText(),
                issued.apiKey().getKeyPrefix(),
                issued.apiKey().isLive() ? "live" : "test",
                issued.apiKey().getCreatedAt()
        );
    }

    @Override
    public String toString() {
        return "CreatedApiKeyResponse[id=" + id + ", prefix=" + prefix + ", key=***]";
    }
}
