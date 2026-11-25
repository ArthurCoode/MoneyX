package com.moneyx.payment.provider;

import java.time.Duration;

/**
 * Falha de um provedor de Pix, já traduzida para o que o MoneyX precisa decidir (sem detalhes do PSP).
 *
 * retryable = true: falha temporária (timeout, PSP fora do ar, limite de requisições). A cobrança PODE ter sido criada
 * no PSP; repetir o MESMO pedido é seguro (o PSP devolve a mesma). false: o PSP recusou o pedido; repetir não adianta.
 */
public class PixProviderException extends RuntimeException {

    private final boolean retryable;
    private final String code;
    private final Duration retryAfter;

    public PixProviderException(boolean retryable, String code, Duration retryAfter, String message, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.code = code;
        this.retryAfter = retryAfter;
    }

    public boolean isRetryable() {
        return retryable;
    }

    /** Código estável do erro no provedor (para log/diagnóstico; nunca vai para o comerciante). */
    public String getCode() {
        return code;
    }

    /** Quanto esperar antes de repetir, se o provedor informou; senão null. */
    public Duration getRetryAfter() {
        return retryAfter;
    }
}
