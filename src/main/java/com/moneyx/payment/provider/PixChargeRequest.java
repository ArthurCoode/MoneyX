package com.moneyx.payment.provider;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * O que o provedor precisa para emitir a cobrança. Dados já validados pelo domínio (Payment.create).
 *
 * Repetir a chamada com o MESMO pedido deve devolver a MESMA cobrança (no Mercado Pago, via X-Idempotency-Key
 * derivada do paymentId): por isso o prazo vai como DURAÇÃO fixa (o que o comerciante pediu), e não "agora + prazo".
 *
 * @param paymentId       id do pagamento no MoneyX (referência externa no PSP, para conciliação)
 * @param amount          valor em centavos
 * @param expiresIn       prazo pedido
 * @param description     texto opcional (pode ser null)
 * @param preparedRequest o pedido gravado na criação (PixProvider.prepareCharge); null = o provedor monta agora
 */
public record PixChargeRequest(UUID paymentId, long amount, Duration expiresIn, String description,
                               String preparedRequest) {

    public PixChargeRequest {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(expiresIn, "expiresIn must not be null");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    public PixChargeRequest(UUID paymentId, long amount, Duration expiresIn, String description) {
        this(paymentId, amount, expiresIn, description, null);
    }
}
