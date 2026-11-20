package com.moneyx.webhook.repository;

import com.moneyx.webhook.domain.WebhookDelivery;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
}
