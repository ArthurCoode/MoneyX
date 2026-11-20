package com.moneyx.payment.service;

import com.moneyx.event.domain.EventType;
import com.moneyx.event.service.EventService;
import com.moneyx.payment.domain.Payment;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Traduz uma mudança de estado de pagamento em um evento (o retrato é o PaymentView daquele momento).
 *
 * Deve ser chamado DEPOIS de aplicar a transição no objeto (o retrato já mostra o novo status) e DENTRO da
 * transação da mudança: o EventService exige isso (Propagation.MANDATORY). É o que torna a gravação atômica:
 * ou o pagamento muda E o evento existe, ou nenhum dos dois.
 */
@Component
class PaymentEventRecorder {

    private final EventService eventService;

    PaymentEventRecorder(EventService eventService) {
        this.eventService = eventService;
    }

    void record(EventType type, Payment payment, Instant occurredAt) {
        eventService.record(payment.getMerchantId(), type, payment.getId(), PaymentView.from(payment), occurredAt);
    }
}
