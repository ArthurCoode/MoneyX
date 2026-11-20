package com.moneyx.shared.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cria o SecretCipher a partir de moneyx.webhooks.encryption-key (variável MONEYX_WEBHOOK_ENCRYPTION_KEY).
 *
 * Sem a chave (ou com chave inválida), a APLICAÇÃO NÃO SOBE. É de propósito: preferimos falhar na partida a
 * rodar sem conseguir cifrar, ou com uma chave padrão embutida no código (que seria pública no GitHub).
 */
@Configuration
public class CryptoConfig {

    @Bean
    public SecretCipher webhookSecretCipher(@Value("${moneyx.webhooks.encryption-key:}") String base64Key) {
        return new SecretCipher(base64Key);
    }
}
