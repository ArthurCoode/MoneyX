package com.moneyx.payment.provider.mercadopago;

import java.time.Duration;
import java.util.Objects;

/**
 * Pedido de criação de uma order Pix.
 *
 * @param idempotencyKey    X-Idempotency-Key (1–128). Repetir a MESMA chave com o MESMO corpo devolve a MESMA order
 *                          (comprovado no sandbox); com outro corpo, 409 idempotency_key_already_used
 * @param externalReference referência do MoneyX (o id do pagamento; UUID com hífens é aceito)
 * @param amount            centavos
 * @param description       opcional
 * @param expiresIn         prazo; o MP só aceita duração (expiration_time), não um instante absoluto
 */
public record CreatePixOrder(String idempotencyKey,
                             String externalReference,
                             long amount,
                             String description,
                             Duration expiresIn) {

    public CreatePixOrder {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(externalReference, "externalReference must not be null");
        Objects.requireNonNull(expiresIn, "expiresIn must not be null");
        if (idempotencyKey.isEmpty() || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey must have 1 to 128 characters");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (expiresIn.isNegative() || expiresIn.isZero()) {
            throw new IllegalArgumentException("expiresIn must be positive");
        }
    }
}
