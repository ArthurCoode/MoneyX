package com.moneyx.payment.api.dto;

import com.moneyx.payment.service.PaymentPage;

import java.util.List;

/**
 * Envelope da listagem: {"data": [...], "hasMore": true}.
 *
 * Para buscar a próxima página, o cliente envia o id do último item de "data" como startingAfter
 * (mesmo modelo do Stripe).
 */
public record PaymentListResponse(List<PaymentResponse> data, boolean hasMore) {

    public static PaymentListResponse from(PaymentPage page) {
        return new PaymentListResponse(
                page.payments().stream().map(PaymentResponse::from).toList(),
                page.hasMore());
    }
}
