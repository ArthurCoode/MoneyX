package com.moneyx.apikey.api.dto;

import com.moneyx.apikey.domain.ApiKey;

import java.time.Instant;
import java.util.UUID;

/**
 * Representação de uma chave existente. NUNCA contém o segredo nem o hash:
 * o comerciante a reconhece pelo prefixo (ex.: mx_test_9fK2).
 */
public record ApiKeyResponse(
        UUID id,
        String prefix,
        boolean active,
        Instant createdAt,
        Instant revokedAt
) {

    public static ApiKeyResponse from(ApiKey apiKey) {
        return new ApiKeyResponse(
                apiKey.getId(),
                apiKey.getKeyPrefix(),
                apiKey.isActive(),
                apiKey.getCreatedAt(),
                apiKey.getRevokedAt()
        );
    }
}
