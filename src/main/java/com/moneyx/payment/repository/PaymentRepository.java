package com.moneyx.payment.repository;

import com.moneyx.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Busca por id E dono na mesma consulta (regra de isolamento da Fase 3):
     * pagamento de outro comerciante simplesmente "não existe".
     */
    Optional<Payment> findByIdAndMerchantId(UUID id, UUID merchantId);
}
