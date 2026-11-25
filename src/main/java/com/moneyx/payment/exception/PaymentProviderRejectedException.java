package com.moneyx.payment.exception;

/**
 * O provedor de Pix recusou o pedido de cobrança. Nada foi criado (nem no provedor, nem no MoneyX) e a
 * Idempotency-Key ficou livre. Repetir o mesmo pedido não deve dar certo sem mudança.
 */
public class PaymentProviderRejectedException extends RuntimeException {

    public PaymentProviderRejectedException() {
        super("The Pix provider rejected the charge. The payment was not created");
    }
}
