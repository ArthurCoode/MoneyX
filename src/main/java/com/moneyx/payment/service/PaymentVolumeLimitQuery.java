package com.moneyx.payment.service;

import com.moneyx.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consulta pública do limite de volume que vale para um comerciante (usada pelo GET /v1/merchants/me).
 *
 * Fica no módulo payment porque é ele quem conhece o limite global; o módulo merchant só exibe o resultado.
 */
@Service
public class PaymentVolumeLimitQuery {

    private final PaymentRepository paymentRepository;
    private final PaymentVolumeLimit volumeLimit;

    PaymentVolumeLimitQuery(PaymentRepository paymentRepository, PaymentVolumeLimit volumeLimit) {
        this.paymentRepository = paymentRepository;
        this.volumeLimit = volumeLimit;
    }

    @Transactional(readOnly = true)
    public VolumeLimits effectiveFor(UUID merchantId) {
        return paymentRepository.findMerchantVolumeLimits(merchantId)
                .map(volumeLimit::effective)
                .orElseThrow(() -> new IllegalStateException("Merchant not found"));
    }
}
