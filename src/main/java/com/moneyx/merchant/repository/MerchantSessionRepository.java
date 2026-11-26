package com.moneyx.merchant.repository;

import com.moneyx.merchant.domain.MerchantSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MerchantSessionRepository extends JpaRepository<MerchantSession, UUID> {

    @Query("select s from MerchantSession s where s.tokenHash = :tokenHash")
    Optional<MerchantSession> findByTokenHash(String tokenHash);

    /** Encerra todas as sessões ativas do comerciante (redefinição de senha). @return quantas */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MerchantSession s set s.revokedAt = :now where s.merchantId = :merchantId and s.revokedAt is null")
    int revokeAllActive(UUID merchantId, Instant now);
}
