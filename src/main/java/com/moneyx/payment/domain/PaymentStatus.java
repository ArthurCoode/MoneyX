package com.moneyx.payment.domain;

/**
 * Ciclo de vida de um pagamento Pix.
 *
 *             ┌──> PAID      (pago antes de expirar)
 * PENDING ────┼──> CANCELED  (cancelado pelo comerciante)
 *             └──> EXPIRED   (prazo encerrado sem pagamento)
 *
 * PAID, CANCELED e EXPIRED são finais: não existe transição de saída.
 * (Reembolso, quando existir, será uma operação própria, não uma mudança de volta para outro estado.)
 */
public enum PaymentStatus {
    PENDING,
    PAID,
    CANCELED,
    EXPIRED;

    public boolean isFinal() {
        return this != PENDING;
    }
}
