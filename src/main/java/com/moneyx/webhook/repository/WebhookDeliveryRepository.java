package com.moneyx.webhook.repository;

import com.moneyx.webhook.domain.WebhookDelivery;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID>,
        JpaSpecificationExecutor<WebhookDelivery> {

    /**
     * Entregas prontas para envio: PENDING, já no horário (next_attempt_at) e sem reserva válida.
     *
     * FOR UPDATE SKIP LOCKED: duas execuções simultâneas (ou duas instâncias) nunca pegam a mesma linha. O lock vale
     * só durante a transação curta da RESERVA; depois disso quem protege a linha é o lease_until.
     */
    @Query(value = """
            SELECT * FROM webhook_deliveries
            WHERE status = 'PENDING'
              AND next_attempt_at <= :now
              AND (lease_until IS NULL OR lease_until <= :now)
            ORDER BY next_attempt_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<WebhookDelivery> lockDue(Instant now, int limit);

    /** Trava a linha para gravar o resultado (conferindo o token da reserva). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from WebhookDelivery d where d.id = :id")
    Optional<WebhookDelivery> lockById(UUID id);

    /** Busca por id E dono: entrega de outro comerciante simplesmente "não existe". */
    Optional<WebhookDelivery> findByIdAndMerchantId(UUID id, UUID merchantId);

    /** Mesma busca, trancando a linha (reenvio manual concorrente com o job). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from WebhookDelivery d where d.id = :id and d.merchantId = :merchantId")
    Optional<WebhookDelivery> lockByIdAndMerchantId(UUID id, UUID merchantId);

    List<WebhookDelivery> findByEventId(UUID eventId);

    /** Dentre os eventos informados, os que ainda têm alguma entrega (de qualquer status). */
    @Query("select distinct d.eventId from WebhookDelivery d where d.eventId in :eventIds")
    Set<UUID> findEventIdsWithDeliveries(Collection<UUID> eventIds);

    /**
     * Retenção: apaga até {@code limit} entregas FINALIZADAS (entregue, falha definitiva ou cancelada) criadas antes de
     * {@code cutoff}. Pendentes nunca são apagadas. Mesmo formato da limpeza de chaves de idempotência: CTE
     * MATERIALIZED (o lote é escolhido e trancado uma única vez) + SKIP LOCKED (uma entrega que o job de envio ou um
     * reenvio manual está usando agora é pulada).
     */
    @Modifying
    @Query(value = """
            WITH batch AS MATERIALIZED (
                SELECT id FROM webhook_deliveries
                WHERE status IN ('SUCCEEDED', 'FAILED', 'CANCELED')
                  AND created_at < :cutoff
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            DELETE FROM webhook_deliveries d
            USING batch
            WHERE d.id = batch.id
            """, nativeQuery = true)
    int deleteFinishedCreatedBefore(Instant cutoff, int limit);
}
