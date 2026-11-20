package com.moneyx.webhook.service;

import com.moneyx.event.domain.Event;
import com.moneyx.event.service.EventService;
import com.moneyx.event.service.EventView;
import com.moneyx.webhook.domain.WebhookDelivery;
import com.moneyx.webhook.domain.WebhookEndpoint;
import com.moneyx.webhook.domain.WebhookSignature;
import com.moneyx.webhook.repository.WebhookDeliveryRepository;
import com.moneyx.webhook.repository.WebhookEndpointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Envia as entregas pendentes (chamado pelo job a cada 5 s, ou diretamente nos testes).
 *
 * Para cada lote, três passos, e só o primeiro e o último tocam o banco:
 * 1. RESERVA (transação curta): tranca as linhas com SKIP LOCKED e marca lease_until + lease_token;
 * 2. ENVIO (sem transação): assina e faz o POST, que pode levar segundos;
 * 3. RESULTADO (transação curta): grava o resultado SE o token ainda for o da reserva.
 *
 * Se a aplicação cair entre 1 e 3, a reserva vence e a entrega volta a ser elegível: a loja pode receber o mesmo
 * evento duas vezes (entrega "pelo menos uma vez") e deve deduplicar pelo id do evento.
 */
@Service
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookEndpointRepository endpointRepository;
    private final WebhookEndpointService endpointService;
    private final EventService eventService;
    private final WebhookSender sender;
    private final WebhookDeliveryProperties properties;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transaction;
    private final Clock clock;

    WebhookDeliveryService(WebhookDeliveryRepository deliveryRepository,
                           WebhookEndpointRepository endpointRepository,
                           WebhookEndpointService endpointService,
                           EventService eventService,
                           WebhookSender sender,
                           WebhookDeliveryProperties properties,
                           JsonMapper jsonMapper,
                           PlatformTransactionManager transactionManager,
                           Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.endpointRepository = endpointRepository;
        this.endpointService = endpointService;
        this.eventService = eventService;
        this.sender = sender;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** @return quantas entregas foram processadas (enviadas ou canceladas) nesta execução */
    public int deliverDue() {
        int total = 0;
        List<Claim> claims;
        do {
            Instant now = now();
            claims = transaction.execute(status -> claimBatch(now));
            for (Claim claim : claims) {
                deliver(claim);
            }
            total += claims.size();
        } while (claims.size() == properties.batchSize());
        return total;
    }

    /** Uma reserva: qual entrega e o token que prova que ela é nossa. */
    record Claim(UUID deliveryId, UUID token) {
    }

    private List<Claim> claimBatch(Instant now) {
        List<Claim> claims = new ArrayList<>();
        for (WebhookDelivery delivery : deliveryRepository.lockDue(now, properties.batchSize())) {
            claims.add(new Claim(delivery.getId(), delivery.claim(now, properties.lease())));
        }
        return claims;
    }

    private void deliver(Claim claim) {
        WebhookDelivery delivery = deliveryRepository.findById(claim.deliveryId()).orElseThrow();
        WebhookEndpoint endpoint = endpointRepository.findById(delivery.getEndpointId()).orElseThrow();

        if (!endpoint.isActive()) {
            finish(claim, d -> d.cancel(claim.token()));
            return;
        }

        Event event = eventService.get(delivery.getMerchantId(), delivery.getEventId());
        byte[] body = jsonMapper.writeValueAsBytes(EventView.from(event));
        long timestamp = clock.instant().getEpochSecond();
        String signature = WebhookSignature.header(endpointService.signingSecretOf(endpoint), timestamp, body);

        WebhookSender.Outcome outcome = sender.send(endpoint.getUrl(), body, Map.of(
                WebhookSignature.HEADER, signature,
                "MoneyX-Event-Id", event.getId().toString(),
                "MoneyX-Event-Type", event.getType().value()));

        finish(claim, d -> {
            Instant now = now();
            if (outcome.success()) {
                d.recordSuccess(claim.token(), outcome.statusCode(), outcome.durationMs(), now);
            } else {
                d.recordFailure(claim.token(), outcome.error(), outcome.statusCode(), outcome.durationMs(), now);
            }
        });
        log.info("Webhook delivery {} (event {} -> endpoint {}): {}", delivery.getId(), event.getId(),
                endpoint.getId(), outcome.success() ? "succeeded" : "failed: " + outcome.error().code());
    }

    /**
     * Grava o resultado SE a reserva ainda for nossa. Se ela venceu e outra execução reservou de novo,
     * o resultado atrasado é descartado (a outra execução é a dona agora).
     */
    private void finish(Claim claim, java.util.function.Consumer<WebhookDelivery> change) {
        transaction.executeWithoutResult(status -> {
            WebhookDelivery delivery = deliveryRepository.lockById(claim.deliveryId()).orElseThrow();
            if (!delivery.isLeasedBy(claim.token())) {
                log.warn("Discarding stale result for webhook delivery {}: lease no longer held", claim.deliveryId());
                return;
            }
            change.accept(delivery);
        });
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
