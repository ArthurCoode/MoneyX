package com.moneyx.webhook.service;

import com.moneyx.event.domain.Event;
import com.moneyx.event.service.EventRecorded;
import com.moneyx.webhook.domain.WebhookDelivery;
import com.moneyx.webhook.domain.WebhookEndpoint;
import com.moneyx.webhook.repository.WebhookDeliveryRepository;
import com.moneyx.webhook.repository.WebhookEndpointRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Fan-out": para cada evento gravado, cria uma entrega por endpoint ATIVO do comerciante que aceita aquele tipo.
 *
 * @EventListener (síncrono) roda DENTRO da transação que gravou o evento, e MANDATORY garante isso: o pagamento,
 * o evento e as entregas são confirmados (ou desfeitos) juntos. Este é o Outbox completo.
 *
 * Endpoints cadastrados depois do evento não recebem eventos antigos (mesmo comportamento do Stripe).
 */
@Component
class WebhookFanOut {

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;

    WebhookFanOut(WebhookEndpointRepository endpointRepository, WebhookDeliveryRepository deliveryRepository) {
        this.endpointRepository = endpointRepository;
        this.deliveryRepository = deliveryRepository;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onEventRecorded(EventRecorded recorded) {
        Event event = recorded.event();
        for (WebhookEndpoint endpoint :
                endpointRepository.findByMerchantIdAndDeletedAtIsNullOrderByCreatedAtDesc(event.getMerchantId())) {
            if (endpoint.accepts(event.getType())) {
                deliveryRepository.save(WebhookDelivery.pending(
                        event.getId(), endpoint.getId(), event.getMerchantId(), event.getCreatedAt()));
            }
        }
    }
}
