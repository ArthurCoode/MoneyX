package com.moneyx.payment.service;

import com.moneyx.payment.domain.Payment;

/**
 * Resultado de POST /v1/payments.
 *
 * @param replayed true quando a requisição repetiu uma Idempotency-Key já usada e o pagamento
 *                 devolvido é o criado na requisição original (nenhum pagamento novo foi criado)
 */
public record PaymentCreation(Payment payment, boolean replayed) {
}
