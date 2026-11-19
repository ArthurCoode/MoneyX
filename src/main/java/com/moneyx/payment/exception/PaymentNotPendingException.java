package com.moneyx.payment.exception;

import com.moneyx.payment.domain.PaymentStatus;

/** A operação exige um pagamento PENDING, mas ele já está em um estado final. */
public class PaymentNotPendingException extends RuntimeException {

    private final PaymentStatus status;

    public PaymentNotPendingException(PaymentStatus status) {
        super("Payment is " + status + " and can no longer be changed");
        this.status = status;
    }

    public PaymentStatus getStatus() {
        return status;
    }
}
