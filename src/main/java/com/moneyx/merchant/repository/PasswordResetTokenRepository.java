package com.moneyx.merchant.repository;

import com.moneyx.merchant.domain.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /** Trancado: dois envios simultâneos do mesmo link são serializados (só um troca a senha). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t where t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> lockByTokenHash(String tokenHash);

    /** Um link novo invalida os anteriores ainda não usados. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PasswordResetToken t set t.usedAt = :now where t.merchantId = :merchantId and t.usedAt is null")
    int invalidatePending(UUID merchantId, Instant now);
}
