package com.moneyx.event.repository;

import com.moneyx.event.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    /** Busca por id E dono: evento de outro comerciante simplesmente "não existe". */
    Optional<Event> findByIdAndMerchantId(UUID id, UUID merchantId);
}
