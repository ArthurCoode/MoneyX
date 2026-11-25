package com.moneyx.payment.repository;

import com.moneyx.payment.domain.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JpaSpecificationExecutor: permite montar a listagem com filtros opcionais (ver PaymentSpecifications).
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    /**
     * Busca por id E dono na mesma consulta (regra de isolamento da Fase 3):
     * pagamento de outro comerciante simplesmente "não existe".
     */
    Optional<Payment> findByIdAndMerchantId(UUID id, UUID merchantId);

    /**
     * Mesma busca, trancando a linha (SELECT ... FOR UPDATE) até o fim da transação.
     *
     * Usado em TODA mudança de estado (pagar, cancelar, expirar): operações simultâneas sobre o mesmo
     * pagamento são serializadas, e a segunda enxerga o resultado da primeira (ex.: "já está PAID").
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id and p.merchantId = :merchantId")
    Optional<Payment> lockByIdAndMerchantId(UUID id, UUID merchantId);

    /**
     * Job de expiração: tranca até {@code limit} pagamentos PENDING cujo prazo terminou (os mais antigos primeiro).
     *
     * FOR UPDATE SKIP LOCKED: linhas que outra transação está alterando agora (ex.: um "pagar" em andamento)
     * são PULADAS em vez de esperadas; a próxima execução do job tenta de novo. Assim o job nunca fica
     * bloqueado e duas instâncias da aplicação nunca pegam o mesmo pagamento.
     *
     * Usa o índice parcial idx_payments_pending_expires_at (V6). SQL nativo: SKIP LOCKED não existe em JPQL.
     *
     * Só cobranças LOCAIS (sem provider_order_id). Uma cobrança num PSP externo só expira depois de cancelada lá
     * (o pagador pode ter pago no último segundo): ver findProviderChargesToReconcile.
     */
    @Query(value = """
            SELECT * FROM payments
            WHERE status = 'PENDING' AND expires_at <= :now AND provider_order_id IS NULL
            ORDER BY expires_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Payment> lockOverduePending(Instant now, int limit);

    /**
     * Tranca a linha do COMERCIANTE para serializar as criações de pagamento dele (limite de volume).
     *
     * Por que a linha do comerciante: "soma + novo <= limite" é uma regra sobre um conjunto que pode começar vazio
     * (padrão do projeto: trancar a linha "pai").
     *
     * Por que FOR NO KEY UPDATE e não FOR UPDATE: os INSERTs com FK para merchants (pagamentos, eventos, chaves de
     * idempotência) pegam FOR KEY SHARE na linha do comerciante. FOR UPDATE conflita com KEY SHARE; FOR NO KEY UPDATE
     * não. Assim pagar/cancelar/expirar outros pagamentos (que gravam eventos) não esperam por esta trava, e duas
     * criações simultâneas continuam sendo serializadas entre si.
     *
     * SQL nativo sobre merchants: o módulo payment não importa o módulo merchant (mesmo padrão do webhook).
     */
    @Query(value = "SELECT CAST(id AS VARCHAR) FROM merchants WHERE id = :merchantId FOR NO KEY UPDATE",
            nativeQuery = true)
    Optional<String> lockMerchantForPaymentCreation(UUID merchantId);

    /**
     * Soma (centavos) dos pagamentos do comerciante criados a partir de {@code since} e não cancelados.
     * Usa o índice (merchant_id, created_at) da V3.
     */
    @Query(value = """
            SELECT COALESCE(SUM(amount), 0) FROM payments
            WHERE merchant_id = :merchantId AND created_at >= :since AND status <> 'CANCELED'
            """, nativeQuery = true)
    long sumVolumeCreatedSince(UUID merchantId, Instant since);

    /**
     * Recuperação: pagamentos que ficaram CREATING desde antes de {@code createdBefore}, os mais antigos primeiro.
     * Sem trava: a conclusão de cada um trava a linha depois (e é segura para rodar em paralelo). Índice parcial da V13.
     */
    @Query(value = """
            SELECT * FROM payments
            WHERE status = 'CREATING' AND created_at <= :createdBefore
            ORDER BY created_at
            LIMIT :limit
            """, nativeQuery = true)
    List<Payment> findCreatingCreatedBefore(Instant createdBefore, int limit);

    /**
     * Conciliação (Etapa 7d): pagamentos PENDING com cobrança no PSP {@code provider}. Primeiro os que já venceram
     * (precisam ser cancelados no PSP), depois os consultados há mais tempo (NULLS FIRST = nunca consultados).
     * Sem trava: a consulta ao PSP acontece FORA de transação; a decisão trava a linha depois.
     * Índice parcial idx_payments_pending_provider_check (V14).
     */
    @Query(value = """
            SELECT * FROM payments
            WHERE status = 'PENDING' AND provider_order_id IS NOT NULL AND pix_provider = :provider
            ORDER BY (expires_at <= :now) DESC, provider_checked_at NULLS FIRST, created_at
            LIMIT :limit
            """, nativeQuery = true)
    List<Payment> findProviderChargesToReconcile(String provider, Instant now, int limit);

    /** Pagamento dono de uma order do PSP (UNIQUE da V13). Usado pelo webhook de entrada do PSP. */
    Optional<Payment> findByPixProviderAndProviderOrderId(String pixProvider, String providerOrderId);
}
