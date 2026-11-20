package com.moneyx.payment.api.dto;

import com.moneyx.payment.service.PaymentPage;
import com.moneyx.payment.service.PaymentView;

import java.util.List;

/**
 * Envelope da listagem: {"data": [...], "hasMore": true}.
 *
 * Para buscar a próxima página, o cliente envia o id do último item de "data" como startingAfter
 * (mesmo modelo do Stripe).
 */
public record PaymentListResponse(List<PaymentView> data, boolean hasMore) {

    public static PaymentListResponse from(PaymentPage page) {
        return new PaymentListResponse(
                page.payments().stream().map(PaymentView::from).toList(),
                page.hasMore());
    }
}
