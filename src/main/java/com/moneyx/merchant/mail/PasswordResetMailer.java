package com.moneyx.merchant.mail;

/** Envia o link de definir/redefinir senha. O link contém o token: NUNCA registrar em log. */
public interface PasswordResetMailer {

    void send(String to, String resetLink);
}
