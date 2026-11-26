package com.moneyx.merchant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de POST /v1/merchants/password-reset/confirm. */
public record ConfirmPasswordResetRequest(
        @NotBlank @Size(max = 100) String token,
        @NotBlank @Size(min = 12, max = 128) String newPassword
) {

    @Override
    public String toString() {
        return "ConfirmPasswordResetRequest[token=***, newPassword=***]";
    }
}
