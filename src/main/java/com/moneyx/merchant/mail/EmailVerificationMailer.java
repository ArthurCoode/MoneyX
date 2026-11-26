package com.moneyx.merchant.mail;

/**
 * Envia o e-mail com o link de verificação. Implementações: SMTP (padrão; em dev, o Mailpit do Compose) ou desligada
 * (moneyx.mail.enabled=false, usada nos testes).
 *
 * O link contém o token: NUNCA registrar o link em log.
 */
public interface EmailVerificationMailer {

    void send(String to, String verificationLink);
}
