package com.moneyx.payment.provider.mercadopago;

import java.time.Duration;

/**
 * Falha numa chamada ao Mercado Pago.
 *
 * retryable diz se REPETIR a mesma chamada (com a mesma X-Idempotency-Key) pode dar certo: timeout, falha de conexão,
 * 429 e 5xx sim; 4xx (dado inválido, credencial, conflito) não. Quem decide repetir é o chamador.
 *
 * A mensagem nunca contém o token nem o corpo enviado.
 */
public class MercadoPagoException extends RuntimeException {

    /** Status HTTP (0 quando não houve resposta: timeout ou falha de conexão). */
    private final int httpStatus;
    /** errors[0].code do MP (ex.: "order_not_found"), ou um código nosso ("timeout", "connection_failed", ...). */
    private final String code;
    private final boolean retryable;
    /** Valor do header Retry-After (só em 429), ou null. */
    private final Duration retryAfter;

    public MercadoPagoException(int httpStatus, String code, boolean retryable, Duration retryAfter, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
        this.retryable = retryable;
        this.retryAfter = retryAfter;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
