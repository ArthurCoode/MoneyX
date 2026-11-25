package com.moneyx.payment.repository;

import com.moneyx.payment.domain.PaymentIdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PaymentIdempotencyKeyRepository extends JpaRepository<PaymentIdempotencyKey, UUID> {

    /**
     * Tenta "reservar" a chave para um novo pagamento, em UM comando atômico.
     *
     * - Chave inexistente: insere a linha e retorna 1.
     * - Chave existente e ainda válida: não altera nada e retorna 0 (a requisição é uma repetição).
     * - Chave existente mas vencida (expires_at <= now): sobrescreve com o novo pagamento e retorna 1.
     *
     * Concorrência: se outra transação acabou de inserir a mesma chave e ainda não terminou,
     * o PostgreSQL faz este comando ESPERAR por ela. Se ela confirmar, retornamos 0 (repetição);
     * se ela for desfeita, a nossa inserção prossegue e retornamos 1. Nunca há dois pagamentos.
     *
     * Precisa ser SQL nativo: ON CONFLICT é específico do PostgreSQL e não existe em JPQL.
     */
    @Modifying
    @Query(value = """
            INSERT INTO payment_idempotency_keys
                (id, merchant_id, idempotency_key, request_fingerprint, payment_id, created_at, expires_at)
            VALUES
                (:id, :merchantId, :idempotencyKey, :fingerprint, :paymentId, :now, :expiresAt)
            ON CONFLICT (merchant_id, idempotency_key) DO UPDATE
                SET request_fingerprint = EXCLUDED.request_fingerprint,
                    payment_id          = EXCLUDED.payment_id,
                    created_at          = EXCLUDED.created_at,
                    expires_at          = EXCLUDED.expires_at
                WHERE payment_idempotency_keys.expires_at <= EXCLUDED.created_at
            """, nativeQuery = true)
    int reserve(UUID id,
                UUID merchantId,
                String idempotencyKey,
                String fingerprint,
                UUID paymentId,
                Instant now,
                Instant expiresAt);

    Optional<PaymentIdempotencyKey> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey);

    /**
     * Limpeza: apaga até {@code limit} chaves vencidas e devolve quantas apagou.
     *
     * Apagar uma chave vencida não muda o comportamento da API: o reserve() já trata chave vencida como livre.
     * SKIP LOCKED: uma chave que o reserve() está renovando agora é pulada (não é apagada nem esperada).
     * Se o DELETE trancar primeiro, o INSERT ... ON CONFLICT do reserve() espera e, quando a linha some,
     * simplesmente insere uma nova.
     *
     * CTE MATERIALIZED: o lote é escolhido e trancado PRIMEIRO, uma única vez, e só depois apagado. Com
     * "DELETE ... WHERE id IN (SELECT ... LIMIT ... FOR UPDATE SKIP LOCKED)", quando e quantas vezes a subconsulta
     * é avaliada fica a critério do planejador; a suíte completa chegou a apagar menos linhas que o esperado.
     */
    @Modifying
    @Query(value = """
            WITH batch AS MATERIALIZED (
                SELECT id FROM payment_idempotency_keys
                WHERE expires_at <= :now
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            DELETE FROM payment_idempotency_keys k
            USING batch
            WHERE k.id = batch.id
            """, nativeQuery = true)
    int deleteExpired(Instant now, int limit);

    /**
     * Libera a chave de um pagamento descartado (o PSP recusou a cobrança): o cliente pode repetir com a mesma chave.
     * Comando direto (sem carregar entidades); roda na transação de quem chama.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM payment_idempotency_keys WHERE payment_id = :paymentId", nativeQuery = true)
    int deleteByPaymentId(UUID paymentId);
}
