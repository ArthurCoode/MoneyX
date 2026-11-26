package com.moneyx.merchant.service;

import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.domain.MerchantSession;
import com.moneyx.merchant.domain.SecretToken;
import com.moneyx.merchant.exception.InvalidCredentialsException;
import com.moneyx.merchant.exception.TooManyAttemptsException;
import com.moneyx.merchant.repository.MerchantRepository;
import com.moneyx.merchant.repository.MerchantSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Sessões do dashboard (Etapa 8b): login com e-mail + senha, validação do token a cada requisição e logout.
 *
 * Contra descobrir quem tem conta (enumeração): e-mail inexistente, conta sem senha e senha errada dão a MESMA
 * resposta, e os três casos gastam o mesmo tempo (o hash é verificado contra um hash falso quando não há conta).
 *
 * O Argon2 leva dezenas de milissegundos de propósito: ele roda FORA de transação (nenhuma conexão do banco fica presa
 * esperando a CPU).
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static final int MAX_TOKEN_LENGTH = 100;

    private final MerchantRepository merchants;
    private final MerchantSessionRepository sessions;
    private final PasswordEncoder encoder;
    private final LoginRateLimits rateLimits;
    private final Clock clock;
    private final TransactionTemplate transaction;
    /** Hash de uma senha aleatória: comparado quando não há conta, para o tempo de resposta ser igual. */
    private final String dummyHash;

    SessionService(MerchantRepository merchants, MerchantSessionRepository sessions, PasswordEncoder encoder,
                   LoginRateLimits rateLimits, Clock clock, PlatformTransactionManager transactionManager) {
        this.merchants = merchants;
        this.sessions = sessions;
        this.encoder = encoder;
        this.rateLimits = rateLimits;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    /**
     * @return a sessão nova e o token (entregue UMA vez ao BFF)
     * @throws TooManyAttemptsException    limite por IP ou por e-mail (429)
     * @throws InvalidCredentialsException e-mail/senha não conferem (401, sempre a mesma resposta)
     */
    public MerchantSession.Started login(String email, String password, String clientIp) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        rateLimits.acquireLogin(clientIp, normalized).ifPresent(wait -> {
            throw new TooManyAttemptsException(wait);
        });

        Optional<Merchant> merchant = merchants.findByEmail(normalized).filter(Merchant::hasPassword);
        String hash = merchant.map(Merchant::getPasswordHash).orElse(dummyHash);
        boolean matches = password != null && encoder.matches(password, hash);
        if (merchant.isEmpty() || !matches) {
            log.info("session.login_failed");
            throw new InvalidCredentialsException();
        }

        MerchantSession.Started started = MerchantSession.start(merchant.get().getId(), now());
        sessions.save(started.session());
        log.info("session.created merchant={} session={}", merchant.get().getId(), started.session().getId());
        return started;
    }

    /**
     * Valida o token de uma requisição e renova a janela de inatividade.
     *
     * @return a sessão ativa, ou vazio se o token não existe, expirou ou foi revogado
     */
    @Transactional
    public Optional<MerchantSession> authenticate(String token) {
        if (token == null || !token.startsWith(MerchantSession.TOKEN_PREFIX) || token.length() > MAX_TOKEN_LENGTH) {
            return Optional.empty();
        }
        Instant now = now();
        return sessions.findByTokenHash(SecretToken.hash(token))
                .filter(session -> session.isActive(now))
                .map(session -> {
                    session.touch(now); // gravado no commit, no máximo 1 vez por minuto
                    return session;
                });
    }

    /** Logout. Idempotente (revogar de novo não muda nada). */
    public void logout(UUID merchantId, UUID sessionId) {
        transaction.executeWithoutResult(status -> sessions.findById(sessionId)
                .filter(session -> session.getMerchantId().equals(merchantId))
                .ifPresent(session -> {
                    session.revoke(now());
                    log.info("session.revoked merchant={} session={}", merchantId, sessionId);
                }));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
