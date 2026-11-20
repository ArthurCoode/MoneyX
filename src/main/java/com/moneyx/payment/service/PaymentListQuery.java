package com.moneyx.payment.service;

import com.moneyx.payment.domain.Payment;
import com.moneyx.payment.domain.PaymentStatus;

import java.util.UUID;

/**
 * Parâmetros da listagem de pagamentos (paginação por cursor, estilo Stripe).
 *
 * @param limit             tamanho da página, de 1 a 100 (null usa 10)
 * @param startingAfter     id do último pagamento da página anterior (null = primeira página)
 * @param status            filtro opcional
 * @param externalReference filtro opcional (conciliação com o pedido do comerciante)
 */
public record PaymentListQuery(Integer limit, UUID startingAfter, PaymentStatus status, String externalReference) {

    public static final int DEFAULT_LIMIT = 10;
    public static final int MAX_LIMIT = 100;

    public PaymentListQuery {
        if (limit == null) {
            limit = DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        // Mesma normalização do domínio: o valor é gravado sem espaços nas pontas e vazio vira null.
        if (externalReference != null) {
            externalReference = externalReference.isBlank() ? null : externalReference.trim();
        }
        if (externalReference != null && externalReference.length() > Payment.EXTERNAL_REFERENCE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "externalReference must have at most " + Payment.EXTERNAL_REFERENCE_MAX_LENGTH + " characters");
        }
    }

    public static PaymentListQuery firstPage() {
        return new PaymentListQuery(null, null, null, null);
    }
}
