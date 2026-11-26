package com.moneyx.merchant.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** moneyx.mail.enabled=false: nada é enviado (testes). Só registra que um e-mail deixou de sair, SEM o link. */
@Component
@ConditionalOnProperty(name = "moneyx.mail.enabled", havingValue = "false")
class DisabledEmailVerificationMailer implements EmailVerificationMailer {

    private static final Logger log = LoggerFactory.getLogger(DisabledEmailVerificationMailer.class);

    @Override
    public void send(String to, String verificationLink) {
        log.info("email_verification.mail_disabled (no email sent)");
    }
}
