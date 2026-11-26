package com.moneyx.merchant.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "moneyx.mail.enabled", havingValue = "true", matchIfMissing = true)
class SmtpPasswordResetMailer implements PasswordResetMailer {

    private final JavaMailSender sender;
    private final String from;

    SmtpPasswordResetMailer(JavaMailSender sender, @Value("${moneyx.mail.from}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(String to, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("MoneyX: defina a sua senha");
        message.setText("""
                Olá!

                Para definir (ou redefinir) a senha do painel MoneyX, abra o link abaixo (válido por 1 hora):

                %s

                Ao salvar a nova senha, todas as sessões abertas no painel são encerradas.
                Se não foi você que pediu, ignore esta mensagem: a sua senha atual continua valendo.
                """.formatted(resetLink));
        sender.send(message);
    }
}
