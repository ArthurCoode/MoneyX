package com.moneyx.payment.exception;

/**
 * A simulação de pagamento (POST /v1/sandbox/payments/{id}/pay) só vale para cobranças do simulador local. Uma cobrança
 * de um PSP real só é paga pelo PSP: marcá-la como paga aqui diria "pago" sem dinheiro nenhum no provedor.
 */
public class SandboxPaymentNotSupportedException extends RuntimeException {

    private final String provider;

    public SandboxPaymentNotSupportedException(String provider) {
        super("This payment was issued by " + provider + " and can only be paid through it");
        this.provider = provider;
    }

    public String getProvider() {
        return provider;
    }
}
