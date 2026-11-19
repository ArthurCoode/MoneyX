package com.moneyx.merchant.api.dto;

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
        Instant createdAt,
        Instant updatedAt
) {

    public static MerchantResponse from(Merchant merchant) {
        return new MerchantResponse(
                merchant.getId(),
                merchant.getLegalName(),
                merchant.getDocument(),
                merchant.getEmail(),
                merchant.getStatus(),
                merchant.getCreatedAt(),
                merchant.getUpdatedAt()
        );
    }
}
