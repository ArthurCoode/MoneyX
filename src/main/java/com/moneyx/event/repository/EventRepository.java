package com.moneyx.event.repository;

import com.moneyx.event.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    /** Busca por id E dono: evento de outro comerciante simplesmente "não existe". */
    Optional<Event> findByIdAndMerchantId(UUID id, UUID merchantId);

    /** Identificação de um evento candidato à retenção, com a posição do cursor. */
    interface EventKey {
        UUID getId();

        Instant getCreatedAt();
    }

    /**
     * Retenção: tranca até {@code limit} eventos criados ANTES de {@code cutoff}, depois da posição do cursor
     * ({@code afterCreatedAt}, {@code afterId}), do mais antigo para o mais novo (índice idx_events_created_id, V12).
     *
     * O cursor existe porque nem todo candidato pode ser apagado (ainda referenciado): sem ele, os mesmos candidatos
     * voltariam em todo lote e o job nunca passaria deles. SKIP LOCKED: um evento trancado agora fica para a próxima
     * execução, sem esperar.
     */
    @Query(value = """
            SELECT id, created_at AS createdAt FROM events
            WHERE created_at < :cutoff
              AND (created_at, id) > (:afterCreatedAt, :afterId)
            ORDER BY created_at, id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<EventKey> lockExpiredAfter(Instant cutoff, Instant afterCreatedAt, UUID afterId, int limit);
}
