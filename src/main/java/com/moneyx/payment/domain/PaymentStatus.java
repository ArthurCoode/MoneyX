package com.moneyx.payment.domain;

/**
 * Ciclo de vida de um pagamento Pix.
 *
 *                               ┌──> PAID      (pago antes de expirar)
 * CREATING ──> PENDING ─────────┼──> CANCELED  (cancelado pelo comerciante)
 *     │                         └──> EXPIRED   (prazo encerrado sem pagamento)
 *     └──────────────────────────> EXPIRED     (o PSP não emitiu a cobrança antes do prazo)
 *
 * CREATING: gravado, aguardando a cobrança Pix do provedor (chamado fora da transação). Sem BR Code.
 * PAID, CANCELED e EXPIRED são finais: não existe transição de saída.
 * (Reembolso, quando existir, será uma operação própria, não uma mudança de volta para outro estado.)
 */
public enum PaymentStatus {
    CREATING,
    PENDING,
    PAID,
    CANCELED,
    EXPIRED;

    public boolean isFinal() {
        return this == PAID || this == CANCELED || this == EXPIRED;
    }
}
