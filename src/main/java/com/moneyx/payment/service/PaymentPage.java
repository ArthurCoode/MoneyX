package com.moneyx.payment.service;

import com.moneyx.payment.domain.Payment;

import java.util.List;

/**
 * Uma página da listagem.
 *
 * @param payments pagamentos da página, mais recentes primeiro
 * @param hasMore  existem pagamentos depois do último desta página (use o id dele como startingAfter)
 */
public record PaymentPage(List<Payment> payments, boolean hasMore) {
}
