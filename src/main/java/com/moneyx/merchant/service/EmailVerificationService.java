package com.moneyx.merchant.service;

import com.moneyx.merchant.domain.EmailVerificationToken;
import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.exception.EmailVerificationException;
import com.moneyx.merchant.exception.EmailVerificationException.Reason;
import com.moneyx.merchant.exception.MerchantNotFoundException;
import com.moneyx.merchant.mail.EmailVerificationMailer;
import com.moneyx.merchant.repository.EmailVerificationTokenRepository;
import com.moneyx.merchant.repository.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Verificação do e-mail do comerciante (pré-requisito das chaves mx_live_).
 *
 * - O token é criado na MESMA transação do cadastro (ou do reenvio); o e-mail só sai DEPOIS do commit
 *   (@TransactionalEventListener AFTER_COMMIT): um rollback nunca envia um link que não existe.
 * - Falha no envio não desfaz nada: o comerciante pede outro e-mail (reenvio).
 * - Confirmar o mesmo link duas vezes (ex.: o antivírus do e-mail abre o link antes da pessoa) responde sucesso
 *   se o e-mail já estiver verificado: o link é um GET e precisa ser seguro de repetir.
 */
@Service
public class EmailVerificationService {

    /** Intervalo mínimo entre dois e-mails de verificação do mesmo comerciante. */
    public static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private final EmailVerificationTokenRepository tokens;
    private final MerchantRepository merchants;
    private final ApplicationEventPublisher events;
    private final EmailVerificationMailer mailer;
    private final Clock clock;
    private final String publicBaseUrl;

    EmailVerificationService(EmailVerificationTokenRepository tokens, MerchantRepository merchants,
                             ApplicationEventPublisher events, EmailVerificationMailer mailer, Clock clock,
                             @Value("${moneyx.public-base-url}") String publicBaseUrl) {
        this.tokens = tokens;
        this.merchants = merchants;
        this.events = events;
        this.mailer = mailer;
        this.clock = clock;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** Evento interno: o e-mail sai depois do commit. Carrega o token em texto: nunca logar. */
    record VerificationRequested(UUID merchantId, String email, String plainToken) {
        @Override
        public String toString() {
            return "VerificationRequested[merchantId=" + merchantId + "]";
        }
    }

    /** Cria um token novo (invalida os pendentes). Precisa de uma transação aberta (cadastro ou reenvio). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requestFor(Merchant merchant) {
        Instant now = now();
        tokens.invalidatePending(merchant.getId(), now);
        EmailVerificationToken.Issued issued = EmailVerificationToken.issue(merchant.getId(), now);
        tokens.save(issued.token());
        events.publishEvent(new VerificationRequested(merchant.getId(), merchant.getEmail(), issued.plainText()));
    }

    /**
     * Reenvio pedido pelo comerciante autenticado.
     *
     * @throws EmailVerificationException ALREADY_VERIFIED (409) ou TOO_SOON (429, com Retry-After)
     */
    @Transactional
    public void resend(UUID merchantId) {
        Merchant merchant = merchants.findById(merchantId).orElseThrow(() -> new MerchantNotFoundException(merchantId));
        if (merchant.isEmailVerified()) {
            throw new EmailVerificationException(Reason.ALREADY_VERIFIED);
        }
        Instant now = now();
        var last = tokens.findFirstByMerchantIdOrderByCreatedAtDesc(merchantId);
        if (last.isPresent()) {
            Instant allowedAt = last.get().getCreatedAt().plus(RESEND_INTERVAL);
            if (now.isBefore(allowedAt)) {
                throw new EmailVerificationException(Reason.TOO_SOON, Duration.between(now, allowedAt));
            }
        }
        requestFor(merchant);
    }

    /**
     * Confirma o e-mail a partir do token do link.
     *
     * @throws EmailVerificationException INVALID_TOKEN ou EXPIRED_TOKEN (400)
     */
    @Transactional
    public void confirm(String plainToken) {
        if (plainToken == null || plainToken.isBlank() || plainToken.length() > 100) {
            throw new EmailVerificationException(Reason.INVALID_TOKEN);
        }
        EmailVerificationToken token = tokens.lockByTokenHash(EmailVerificationToken.hash(plainToken))
                .orElseThrow(() -> new EmailVerificationException(Reason.INVALID_TOKEN));
        Merchant merchant = merchants.findById(token.getMerchantId())
                .orElseThrow(() -> new IllegalStateException("Token without merchant"));
        Instant now = now();
        if (token.isUsed()) {
            if (merchant.isEmailVerified()) {
                return; // repetição do mesmo link: já está verificado
            }
            throw new EmailVerificationException(Reason.INVALID_TOKEN); // substituído por um mais novo
        }
        if (token.isExpired(now)) {
            throw new EmailVerificationException(Reason.EXPIRED_TOKEN);
        }
        token.markUsed(now);
        merchant.markEmailVerified(now);
        log.info("merchant.email_verified merchant={}", merchant.getId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void sendAfterCommit(VerificationRequested requested) {
        String link = UriComponentsBuilder.fromUriString(publicBaseUrl)
                .path("/v1/merchants/verify-email")
                .queryParam("token", requested.plainToken())
                .build().toUriString();
        try {
            mailer.send(requested.email(), link);
            log.info("email_verification.sent merchant={}", requested.merchantId());
        } catch (RuntimeException e) {
            // Sem o link e sem o e-mail no log. O comerciante pode pedir o reenvio.
            log.warn("email_verification.send_failed merchant={} error={}", requested.merchantId(),
                    e.getClass().getSimpleName());
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
