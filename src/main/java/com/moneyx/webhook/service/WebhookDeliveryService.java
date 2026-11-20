package com.moneyx.webhook.service;

import com.moneyx.event.domain.Event;
import com.moneyx.event.service.EventService;
import com.moneyx.event.service.EventView;
import com.moneyx.webhook.domain.WebhookDelivery;
import com.moneyx.webhook.domain.WebhookDeliveryStatus;
import com.moneyx.webhook.domain.WebhookEndpoint;
import com.moneyx.webhook.domain.WebhookSignature;
import com.moneyx.webhook.exception.InvalidDeliveryCursorException;
import com.moneyx.webhook.exception.WebhookDeliveryNotFailedException;
import com.moneyx.webhook.exception.WebhookDeliveryNotFoundException;
import com.moneyx.webhook.exception.WebhookEndpointNotFoundException;
import com.moneyx.webhook.exception.WebhookEndpointUnavailableException;
import com.moneyx.webhook.repository.WebhookDeliveryRepository;
import com.moneyx.webhook.repository.WebhookDeliverySpecifications;
import com.moneyx.webhook.repository.WebhookEndpointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Envio das entregas de webhook (job a cada 5 s, ou diretamente nos testes), consulta e reenvio manual.
 *
 * Para cada lote, três passos, e só o primeiro e o último tocam o banco:
 * 1. RESERVA (transação curta): tranca as linhas com SKIP LOCKED e marca lease_until + lease_token;
 * 2. ENVIO (sem transação): assina e faz o POST, que pode levar segundos;
 * 3. RESULTADO (transação curta): SE o token ainda for o da reserva, grava o resultado na entrega (reagenda ou
 *    desiste, pela WebhookRetryPolicy) e atualiza a sequência de falhas do endpoint (que pode desativá-lo).
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
    private final MeterRegistry meterRegistry;

    WebhookDeliveryService(WebhookDeliveryRepository deliveryRepository,
                           WebhookEndpointRepository endpointRepository,
                           WebhookEndpointService endpointService,
                           EventService eventService,
                           WebhookSender sender,
                           WebhookDeliveryProperties properties,
                           JsonMapper jsonMapper,
                           PlatformTransactionManager transactionManager,
                           Clock clock,
                           MeterRegistry meterRegistry) {
        this.deliveryRepository = deliveryRepository;
        this.endpointRepository = endpointRepository;
        this.endpointService = endpointService;
        this.eventService = eventService;
        this.sender = sender;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    // ---------- envio (job) ----------

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

        // Removido ou desativado: não envia. Eventos desse período a loja recupera por GET /v1/events.
        if (!endpoint.isEnabled()) {
            finish(claim, d -> d.cancel(claim.token()));
            countDelivery("canceled", "none");
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

        finish(claim, d -> recordOutcome(d, claim.token(), outcome, now()));
    }

    private void recordOutcome(WebhookDelivery delivery, UUID token, WebhookSender.Outcome outcome, Instant now) {
        // O endpoint é TRANCADO: duas entregas do mesmo endpoint terminando juntas atualizam a sequência de falhas
        // uma de cada vez (sem lock, o @Version faria uma delas falhar).
        WebhookEndpoint endpoint = endpointRepository
                .lockByIdAndMerchantId(delivery.getEndpointId(), delivery.getMerchantId()).orElseThrow();

        // A TENTATIVA aconteceu (a loja recebeu ou não o POST), então é contada mesmo que o commit a seguir falhe.
        attemptDuration(outcome.success()).record(java.time.Duration.ofMillis(outcome.durationMs()));

        if (outcome.success()) {
            delivery.recordSuccess(token, outcome.statusCode(), outcome.durationMs(), now);
            endpoint.recordDeliverySuccess();
            countDelivery("succeeded", "none");
            log.info("Webhook delivery {} succeeded (attempt {})", delivery.getId(), delivery.getAttempts());
            return;
        }

        boolean rescheduled = delivery.recordFailure(token, outcome.error(), outcome.statusCode(),
                outcome.durationMs(), now, randomJitter());
        countDelivery(rescheduled ? "retry_scheduled" : "failed", outcome.error().code());
        if (endpoint.recordDeliveryFailure(now)) {
            endpointService.recordAutomaticDisable(endpoint, now);
            log.warn("Webhook endpoint {} disabled after failing since {}", endpoint.getId(), endpoint.getFailingSince());
        }
        log.info("Webhook delivery {} failed (attempt {}, {}): {}", delivery.getId(), delivery.getAttempts(),
                outcome.error().code(), rescheduled ? "retry at " + delivery.getNextAttemptAt() : "giving up");
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

    /**
     * moneyx.webhook.deliveries{result, error}: result in {succeeded, retry_scheduled, failed, canceled};
     * error é o código estável (ou "none"). Conjuntos pequenos e fechados: nunca ids como etiqueta.
     */
    private void countDelivery(String result, String error) {
        Counter.builder("moneyx.webhook.deliveries")
                .description("Tentativas de entrega de webhook, por resultado")
                .tag("result", result)
                .tag("error", error)
                .register(meterRegistry)
                .increment();
    }

    /** moneyx.webhook.delivery.duration{outcome}: quanto a loja levou para responder (ou até o erro). */
    private Timer attemptDuration(boolean success) {
        return Timer.builder("moneyx.webhook.delivery.duration")
                .description("Duração de cada tentativa de entrega de webhook")
                .tag("outcome", success ? "success" : "failure")
                .register(meterRegistry);
    }

    /** Sorteia a variação da próxima espera em [-retryJitter, +retryJitter]. */
    private double randomJitter() {
        double max = properties.retryJitter();
        return max == 0 ? 0 : ThreadLocalRandom.current().nextDouble(-max, max);
    }

    // ---------- consulta e reenvio manual (API) ----------

    /**
     * Entregas de um endpoint do comerciante (inclusive de endpoint removido), mais recentes primeiro.
     *
     * @throws WebhookEndpointNotFoundException endpoint inexistente ou de outro comerciante
     * @throws InvalidDeliveryCursorException   startingAfter não é uma entrega deste endpoint
     */
    @Transactional(readOnly = true)
    public WebhookDeliveryPage list(UUID merchantId, UUID endpointId, WebhookDeliveryListQuery query) {
        endpointRepository.findByIdAndMerchantId(endpointId, merchantId).orElseThrow(WebhookEndpointNotFoundException::new);

        Specification<WebhookDelivery> spec = WebhookDeliverySpecifications.ofEndpoint(merchantId, endpointId);
        if (query.startingAfter() != null) {
            WebhookDelivery cursor = deliveryRepository.findByIdAndMerchantId(query.startingAfter(), merchantId)
                    .filter(d -> d.getEndpointId().equals(endpointId))
                    .orElseThrow(InvalidDeliveryCursorException::new);
            spec = spec.and(WebhookDeliverySpecifications.after(cursor.getCreatedAt(), cursor.getId()));
        }
        if (query.status() != null) {
            spec = spec.and(WebhookDeliverySpecifications.hasStatus(query.status()));
        }

        int limit = query.limit();
        List<WebhookDelivery> rows = deliveryRepository.findBy(spec, q -> q
                .sortBy(WebhookDeliverySpecifications.NEWEST_FIRST)
                .limit(limit + 1)
                .all());
        boolean hasMore = rows.size() > limit;
        return new WebhookDeliveryPage(hasMore ? rows.subList(0, limit) : rows, hasMore);
    }

    /**
     * Reenvio manual: coloca uma entrega FAILED de volta na fila, para agora. O job a envia em até 5 s.
     *
     * @throws WebhookDeliveryNotFoundException    entrega inexistente ou de outro comerciante
     * @throws WebhookDeliveryNotFailedException   a entrega não está FAILED (pendente, entregue ou cancelada)
     * @throws WebhookEndpointUnavailableException o endpoint foi removido ou está desativado
     */
    @Transactional
    public WebhookDelivery retry(UUID merchantId, UUID deliveryId) {
        WebhookDelivery delivery = deliveryRepository.lockByIdAndMerchantId(deliveryId, merchantId)
                .orElseThrow(WebhookDeliveryNotFoundException::new);
        if (delivery.getStatus() != WebhookDeliveryStatus.FAILED) {
            throw new WebhookDeliveryNotFailedException(delivery.getStatus());
        }
        WebhookEndpoint endpoint = endpointRepository.findByIdAndMerchantId(delivery.getEndpointId(), merchantId)
                .orElseThrow();
        if (!endpoint.isEnabled()) {
            throw new WebhookEndpointUnavailableException();
        }
        delivery.requeue(now());
        return delivery;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
