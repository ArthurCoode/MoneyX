package com.moneyx.payment.exception;

import java.time.Duration;

/**
 * O provedor de Pix não respondeu a tempo (fora do ar, timeout, limite de requisições). O pagamento ficou CREATING:
 * repetir a requisição com a MESMA Idempotency-Key retoma a criação (ou a rotina de recuperação a conclui sozinha).
 */
public class PaymentProviderUnavailableException extends RuntimeException {

    /** Espera sugerida ao cliente quando o provedor não informou nenhuma. */
    public static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(5);

    private final Duration retryAfter;

    public PaymentProviderUnavailableException(Duration retryAfter) {
        super("The Pix provider is temporarily unavailable. Retry with the same Idempotency-Key");
        this.retryAfter = retryAfter == null || retryAfter.isNegative() || retryAfter.isZero()
                ? DEFAULT_RETRY_AFTER : retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
