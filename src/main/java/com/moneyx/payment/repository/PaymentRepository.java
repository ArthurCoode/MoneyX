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
     */
    @Query(value = """
            SELECT * FROM payments
            WHERE status = 'PENDING' AND expires_at <= :now
            ORDER BY expires_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Payment> lockOverduePending(Instant now, int limit);
}
