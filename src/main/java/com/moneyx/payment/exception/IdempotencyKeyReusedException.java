package com.moneyx.payment.exception;

/**
 * A Idempotency-Key já foi usada (nas últimas 24 h) com um corpo de requisição diferente.
 *
 * Quase sempre é um bug no cliente (ex.: reaproveitar a mesma chave para pedidos diferentes).
 * Rejeitamos em vez de devolver o pagamento antigo, que seria de outro pedido.
 */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException() {
        super("This Idempotency-Key was already used with a different request body. "
                + "Use a new key for a different payment");
    }
}
