package com.moneyx.merchant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de POST /v1/sessions. Sem @Email: um e-mail malformado só não encontra conta (mesma resposta 401). */
public record CreateSessionRequest(
        @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(max = 128) String password
) {

    @Override
    public String toString() {
        return "CreateSessionRequest[email=***, password=***]";
    }
}
