package com.moneyx.payment.api.dto;

import com.moneyx.payment.domain.Payment;
import com.moneyx.payment.domain.PaymentCurrency;
import com.moneyx.payment.service.CreatePaymentCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo de POST /v1/payments.
 *
 * {
 *   "amount": 4990,                    // centavos (R$ 49,90). Inteiro obrigatório.
 *   "currency": "BRL",                 // obrigatório
 *   "description": "Pedido 123",       // opcional
 *   "externalReference": "order-123",  // opcional: id do pedido no sistema do comerciante
 *   "expiresInSeconds": 1800           // opcional: 300 a 86400 (padrão 1800)
 * }
 *
 * Os limites vêm das constantes do domínio (Payment), para que API e domínio nunca divirjam.
 */
public record CreatePaymentRequest(
        @NotNull
        @Min(Payment.MIN_AMOUNT)
        @Max(Payment.MAX_AMOUNT)
        Long amount,

        @NotNull
        @Pattern(regexp = "BRL", message = "must be BRL")
        String currency,

        @Size(max = Payment.DESCRIPTION_MAX_LENGTH)
        String description,

        @Size(max = Payment.EXTERNAL_REFERENCE_MAX_LENGTH)
        String externalReference,

        @Min(Payment.MIN_EXPIRATION_SECONDS)
        @Max(Payment.MAX_EXPIRATION_SECONDS)
        Long expiresInSeconds
) {

    public CreatePaymentCommand toCommand() {
        return new CreatePaymentCommand(
                amount,
                PaymentCurrency.valueOf(currency),
                description,
                externalReference,
                expiresInSeconds);
    }
}
