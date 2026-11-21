package com.moneyx.event.service;

import com.moneyx.event.repository.EventRepository;
import com.moneyx.event.repository.EventRepository.EventKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Retenção de eventos: apaga os criados há mais de {@code moneyx.events.retention} (30 dias, como o Stripe).
 * Depois disso, GET /v1/events/{id} responde 404. Os PAGAMENTOS nunca são apagados (registro financeiro); só o
 * histórico de notificações.
 *
 * Um evento ainda referenciado (ex.: uma entrega de webhook que ainda existe) NÃO é apagado: fica para uma próxima
 * execução, depois que quem o referencia for limpo. Ver {@link EventReferences}.
 *
 * Lotes, cada um na sua transação curta (TransactionTemplate, mesmo motivo do PaymentMaintenanceService).
 */
@Service
public class EventRetentionService {

    public static final int BATCH_SIZE = 500;

    /** Posição inicial do cursor: antes de qualquer evento possível. */
    private static final Instant CURSOR_START_TIME = Instant.EPOCH;
    private static final UUID CURSOR_START_ID = new UUID(0, 0);

    private final EventRepository repository;
    private final List<EventReferences> references;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final Duration retention;

    public EventRetentionService(EventRepository repository,
                                 List<EventReferences> references,
                                 PlatformTransactionManager transactionManager,
                                 Clock clock,
                                 @Value("${moneyx.events.retention:30d}") Duration retention) {
        if (retention.compareTo(Duration.ofDays(1)) < 0) {
            throw new IllegalArgumentException("moneyx.events.retention must be at least 1 day");
        }
        this.repository = repository;
        this.references = List.copyOf(references);
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.retention = retention;
    }

    /** @return quantos eventos foram apagados */
    public int purgeExpired() {
        // Um único corte para a execução inteira (o resultado não depende de quanto tempo os lotes levam).
        Instant cutoff = clock.instant().truncatedTo(ChronoUnit.MICROS).minus(retention);
        Instant afterCreatedAt = CURSOR_START_TIME;
        UUID afterId = CURSOR_START_ID;
        int total = 0;
        while (true) {
            Instant cursorTime = afterCreatedAt;
            UUID cursorId = afterId;
            Batch batch = transaction.execute(status -> purgeBatch(cutoff, cursorTime, cursorId));
            if (batch.examined() == 0) {
                return total; // não há mais candidatos depois do cursor
            }
            total += batch.deleted();
            afterCreatedAt = batch.lastCreatedAt();
            afterId = batch.lastId();
        }
    }

    private Batch purgeBatch(Instant cutoff, Instant afterCreatedAt, UUID afterId) {
        List<EventKey> candidates = repository.lockExpiredAfter(cutoff, afterCreatedAt, afterId, BATCH_SIZE);
        if (candidates.isEmpty()) {
            return new Batch(0, 0, null, null);
        }
        List<UUID> ids = candidates.stream().map(EventKey::getId).toList();
        Set<UUID> referenced = new HashSet<>();
        for (EventReferences reference : references) {
            referenced.addAll(reference.findReferenced(ids));
        }
        List<UUID> deletable = ids.stream().filter(id -> !referenced.contains(id)).toList();
        if (!deletable.isEmpty()) {
            repository.deleteAllByIdInBatch(deletable);
        }
        EventKey last = candidates.getLast();
        return new Batch(candidates.size(), deletable.size(), last.getCreatedAt(), last.getId());
    }

    private record Batch(int examined, int deleted, Instant lastCreatedAt, UUID lastId) {
    }
}
