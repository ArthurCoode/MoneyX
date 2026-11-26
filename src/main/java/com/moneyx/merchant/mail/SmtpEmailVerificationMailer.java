package com.moneyx.merchant.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** E-mail de verificação por SMTP (spring.mail.*). Em desenvolvimento o servidor é o Mailpit (http://localhost:8025). */
@Component
@ConditionalOnProperty(name = "moneyx.mail.enabled", havingValue = "true", matchIfMissing = true)
class SmtpEmailVerificationMailer implements EmailVerificationMailer {

    private final JavaMailSender sender;
    private final String from;

    SmtpEmailVerificationMailer(JavaMailSender sender, @Value("${moneyx.mail.from}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(String to, String verificationLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("MoneyX: confirme o seu e-mail");
        message.setText("""
                Olá!

                Confirme o e-mail da sua conta MoneyX abrindo o link abaixo (válido por 24 horas):

                %s

                Com o e-mail confirmado você pode criar chaves de produção (mx_live_).
                Se não foi você que criou esta conta, ignore esta mensagem.
                """.formatted(verificationLink));
        sender.send(message);
    }
}
