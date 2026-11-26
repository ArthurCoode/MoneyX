package com.moneyx.merchant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de POST /v1/merchants/password-reset. */
public record PasswordResetRequest(@NotBlank @Size(max = 254) String email) {

    @Override
    public String toString() {
        return "PasswordResetRequest[email=***]";
    }
}
