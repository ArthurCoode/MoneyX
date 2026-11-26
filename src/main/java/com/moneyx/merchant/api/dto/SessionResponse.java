package com.moneyx.merchant.api.dto;

import com.moneyx.merchant.domain.MerchantSession;

import java.time.Instant;
import java.util.UUID;

/**
 * Resposta do login. O token aparece SÓ aqui (como o segredo de uma API Key).
 *
 * @param expiresAt   limite absoluto (24 h depois do login)
 * @param idleTimeout segundos sem uso até a sessão expirar (6 h); cada uso renova
 */
public record SessionResponse(UUID id, UUID merchantId, String token, Instant expiresAt, long idleTimeout) {

    public static SessionResponse from(MerchantSession.Started started) {
        MerchantSession session = started.session();
        return new SessionResponse(session.getId(), session.getMerchantId(), started.token(), session.getExpiresAt(),
                MerchantSession.IDLE_TIMEOUT.toSeconds());
    }

    @Override
    public String toString() {
        return "SessionResponse[id=" + id + ", token=***]";
    }
}
