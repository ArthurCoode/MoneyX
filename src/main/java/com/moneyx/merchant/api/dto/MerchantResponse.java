package com.moneyx.merchant.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.domain.MerchantStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Representação pública de um Merchant na API.
 *
 * Separar o DTO da entidade permite:
 * - escolher exatamente o que é exposto (ex.: o campo interno `version` fica de fora);
 * - evoluir o banco sem quebrar o contrato da API, e vice-versa.
 */
public record MerchantResponse(
        UUID id,
        String legalName,
        String document,
        String email,
        MerchantStatus status,
        /** Quando o e-mail foi confirmado; null = não verificado (só chaves mx_test_). */
        Instant emailVerifiedAt,
        Instant createdAt,
        Instant updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        VolumeLimits volumeLimits
) {

    /**
     * Limite acumulado de volume que vale para o comerciante, em centavos (o próprio, se definido, senão o da
     * plataforma). Só aparece no GET /v1/merchants/me.
     */
    public record VolumeLimits(long daily, long monthly) {
    }

    public static MerchantResponse from(Merchant merchant) {
        return from(merchant, null);
    }

    public static MerchantResponse from(Merchant merchant, VolumeLimits volumeLimits) {
        return new MerchantResponse(
                merchant.getId(),
                merchant.getLegalName(),
                merchant.getDocument(),
                merchant.getEmail(),
                merchant.getStatus(),
                merchant.getEmailVerifiedAt(),
                merchant.getCreatedAt(),
                merchant.getUpdatedAt(),
                volumeLimits
        );
    }
}
