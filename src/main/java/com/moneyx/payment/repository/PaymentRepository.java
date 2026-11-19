package com.moneyx.payment.repository;

import com.moneyx.payment.domain.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

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
}
