package com.moneyx.merchant.service;

import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.domain.PasswordPolicy;
import com.moneyx.merchant.domain.PasswordResetToken;
import com.moneyx.merchant.domain.SecretToken;
import com.moneyx.merchant.exception.PasswordResetException;
import com.moneyx.merchant.exception.TooManyAttemptsException;
import com.moneyx.merchant.mail.PasswordResetMailer;
import com.moneyx.merchant.repository.MerchantRepository;
import com.moneyx.merchant.repository.MerchantSessionRepository;
import com.moneyx.merchant.repository.PasswordResetTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

/**
 * Definir/redefinir a senha do dashboard por um link enviado ao e-mail (Etapa 8b). Serve também para os comerciantes
 * anteriores à 8b, que não têm senha.
 *
 * - Pedir o link responde SEMPRE igual (202), exista a conta ou não: a API não revela quem tem conta.
 * - O e-mail sai DEPOIS do commit (mesmo padrão da verificação de e-mail).
 * - Trocar a senha encerra TODAS as sessões abertas (quem roubou uma sessão perde o acesso) e confirma o e-mail (quem
 *   abriu o link prova que controla a caixa de entrada).
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final MerchantRepository merchants;
    private final PasswordResetTokenRepository tokens;
    private final MerchantSessionRepository sessions;
    private final PasswordEncoder encoder;
    private final LoginRateLimits rateLimits;
    private final ApplicationEventPublisher events;
    private final PasswordResetMailer mailer;
    private final Clock clock;
    private final String dashboardBaseUrl;

    PasswordResetService(MerchantRepository merchants, PasswordResetTokenRepository tokens,
                         MerchantSessionRepository sessions, PasswordEncoder encoder, LoginRateLimits rateLimits,
                         ApplicationEventPublisher events, PasswordResetMailer mailer, Clock clock,
                         @Value("${moneyx.dashboard-base-url}") String dashboardBaseUrl) {
        this.merchants = merchants;
        this.tokens = tokens;
        this.sessions = sessions;
        this.encoder = encoder;
        this.rateLimits = rateLimits;
        this.events = events;
        this.mailer = mailer;
        this.clock = clock;
        this.dashboardBaseUrl = dashboardBaseUrl;
    }

    /** Evento interno: o e-mail sai depois do commit. Carrega o token em texto: nunca logar. */
    record ResetRequested(UUID merchantId, String email, String plainToken) {
        @Override
        public String toString() {
            return "ResetRequested[merchantId=" + merchantId + "]";
        }
    }

    /**
     * Pede o link. Não diz se a conta existe.
     *
     * @throws TooManyAttemptsException limite por IP ou por e-mail (429)
     */
    @Transactional
    public void request(String email, String clientIp) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        rateLimits.acquirePasswordReset(clientIp, normalized).ifPresent(wait -> {
            throw new TooManyAttemptsException(wait);
        });
        merchants.findByEmail(normalized).ifPresent(merchant -> {
            Instant now = now();
            tokens.invalidatePending(merchant.getId(), now);
            PasswordResetToken.Issued issued = PasswordResetToken.issue(merchant.getId(), now);
            tokens.save(issued.token());
            events.publishEvent(new ResetRequested(merchant.getId(), merchant.getEmail(), issued.plainText()));
        });
    }

    /**
     * Troca a senha a partir do link.
     *
     * @throws PasswordResetException   link inválido/usado/substituído ou vencido (400)
     * @throws IllegalArgumentException senha fora da política (400)
     */
    @Transactional
    public void confirm(String plainToken, String newPassword) {
        if (plainToken == null || plainToken.isBlank() || plainToken.length() > 100) {
            throw PasswordResetException.invalid();
        }
        PasswordResetToken token = tokens.lockByTokenHash(SecretToken.hash(plainToken))
                .orElseThrow(PasswordResetException::invalid);
        Instant now = now();
        if (token.getUsedAt() != null) {
            throw PasswordResetException.invalid();
        }
        if (!token.isUsable(now)) {
            throw PasswordResetException.expired();
        }
        Merchant merchant = merchants.findById(token.getMerchantId())
                .orElseThrow(() -> new IllegalStateException("Token without merchant"));
        PasswordPolicy.validate(newPassword, merchant.getEmail());

        merchant.changePasswordHash(encoder.encode(newPassword), now);
        merchant.markEmailVerified(now);
        token.markUsed(now);
        int revoked = sessions.revokeAllActive(merchant.getId(), now);
        log.info("merchant.password_changed merchant={} sessionsRevoked={}", merchant.getId(), revoked);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void sendAfterCommit(ResetRequested requested) {
        String link = UriComponentsBuilder.fromUriString(dashboardBaseUrl)
                .path("/redefinir-senha")
                .queryParam("token", requested.plainToken())
                .build().toUriString();
        try {
            mailer.send(requested.email(), link);
            log.info("password_reset.sent merchant={}", requested.merchantId());
        } catch (RuntimeException e) {
            log.warn("password_reset.send_failed merchant={} error={}", requested.merchantId(),
                    e.getClass().getSimpleName());
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
