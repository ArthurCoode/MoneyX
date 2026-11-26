package com.moneyx.merchant.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** moneyx.mail.enabled=false (testes): nada é enviado; só registra, SEM o link. */
@Component
@ConditionalOnProperty(name = "moneyx.mail.enabled", havingValue = "false")
class DisabledPasswordResetMailer implements PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(DisabledPasswordResetMailer.class);

    @Override
    public void send(String to, String resetLink) {
        log.info("password_reset.mail_disabled (no email sent)");
    }
}
