package com.moneyx.webhook.service;

import com.moneyx.event.service.EventReferences;
import com.moneyx.webhook.repository.WebhookDeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Retenção das entregas de webhook, e a resposta do módulo webhook à pergunta "este evento ainda é usado?".
 *
 * - Apaga entregas FINALIZADAS criadas há mais de moneyx.webhooks.delivery.retention (30 dias). Pendentes nunca.
 * - Enquanto uma entrega de um evento existir, o evento não é apagado (EventReferences): a entrega precisa do
 *   evento para ser enviada (o corpo é o evento) e consultada.
 */
@Service
public class WebhookDeliveryRetentionService implements EventReferences {

    public static final int BATCH_SIZE = 1_000;

    private final WebhookDeliveryRepository repository;
    private final WebhookDeliveryProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public WebhookDeliveryRetentionService(WebhookDeliveryRepository repository,
                                           WebhookDeliveryProperties properties,
                                           PlatformTransactionManager transactionManager,
                                           Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** @return quantas entregas foram apagadas */
    public int purgeExpired() {
        Instant cutoff = clock.instant().truncatedTo(ChronoUnit.MICROS).minus(properties.retention());
        int total = 0;
        int batch;
        do {
            batch = transaction.execute(status -> repository.deleteFinishedCreatedBefore(cutoff, BATCH_SIZE));
            total += batch;
            // Com SKIP LOCKED, um lote menor que o limite não significa que acabou: repete até vir vazio.
        } while (batch > 0);
        return total;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findReferenced(Collection<UUID> eventIds) {
        return repository.findEventIdsWithDeliveries(eventIds);
    }
}
