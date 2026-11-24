package com.moneyx.payment.provider;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * O que o provedor precisa para emitir a cobrança. Dados já validados pelo domínio (Payment.create).
 *
 * @param paymentId   id do pagamento no MoneyX (vai como referência externa no PSP, para conciliação)
 * @param amount      valor em centavos
 * @param expiresAt   fim do prazo de pagamento
 * @param description texto opcional (pode ser null)
 */
public record PixChargeRequest(UUID paymentId, long amount, Instant expiresAt, String description) {

    public PixChargeRequest {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
