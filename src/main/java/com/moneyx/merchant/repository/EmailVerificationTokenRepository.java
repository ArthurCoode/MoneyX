package com.moneyx.merchant.repository;

import com.moneyx.merchant.domain.EmailVerificationToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    /** Busca pelo hash trancando a linha: duas confirmações simultâneas do mesmo link são serializadas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from EmailVerificationToken t where t.tokenHash = :tokenHash")
    Optional<EmailVerificationToken> lockByTokenHash(String tokenHash);

    Optional<EmailVerificationToken> findFirstByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    /** Invalida os tokens pendentes do comerciante (um token novo substitui os anteriores). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EmailVerificationToken t set t.usedAt = :now where t.merchantId = :merchantId and t.usedAt is null")
    int invalidatePending(UUID merchantId, Instant now);
}
