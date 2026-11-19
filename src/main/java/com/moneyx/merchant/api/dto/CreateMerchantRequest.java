package com.moneyx.merchant.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo da requisição POST /v1/merchants.
 *
 * As anotações de Bean Validation rejeitam entradas inválidas na fronteira da API,
 * antes de chegarem ao serviço. A entidade continua validando por conta própria
 * (defesa em profundidade).
 *
 * Documento aceito: CPF ou CNPJ, apenas dígitos ou no formato com pontuação.
 */
public record CreateMerchantRequest(

        @NotBlank
        @Size(max = 150)
        String legalName,

        @NotBlank
        @Pattern(
                regexp = "\\d{11}|\\d{14}|\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}|\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}",
                message = "must be a valid CPF (11 digits) or CNPJ (14 digits)"
        )
        String document,

        @NotBlank
        @Email
        @Size(max = 254)
        String email
) {
}
