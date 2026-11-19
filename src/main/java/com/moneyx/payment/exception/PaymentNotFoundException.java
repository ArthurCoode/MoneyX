package com.moneyx.payment.exception;

/**
 * O pagamento não existe OU pertence a outro comerciante. Os dois casos são indistinguíveis
 * de propósito (mesma regra das API Keys).
 */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException() {
        super("Payment not found");
    }
}
