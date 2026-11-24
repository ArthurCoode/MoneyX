package com.moneyx.shared.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cria o SecretCipher a partir de:
 * - moneyx.webhooks.encryption-key (variável MONEYX_WEBHOOK_ENCRYPTION_KEY): chave ATIVA, obrigatória;
 * - moneyx.webhooks.previous-encryption-key (MONEYX_WEBHOOK_ENCRYPTION_KEY_PREVIOUS): chave ANTERIOR, opcional,
 *   só durante uma rotação.
 *
 * Sem a chave ativa (ou com chave inválida), a APLICAÇÃO NÃO SOBE. É de propósito: preferimos falhar na partida a
 * rodar sem conseguir cifrar, ou com uma chave padrão embutida no código (que seria pública no GitHub).
 */
@Configuration
public class CryptoConfig {

    private static final Logger log = LoggerFactory.getLogger(CryptoConfig.class);

    @Bean
    public SecretCipher webhookSecretCipher(
            @Value("${moneyx.webhooks.encryption-key:}") String base64Key,
            @Value("${moneyx.webhooks.previous-encryption-key:}") String previousBase64Key) {
        SecretCipher cipher = new SecretCipher(base64Key, previousBase64Key);
        if (cipher.hasPreviousKey()) {
            // Só ids (não são segredo). Lembra o operador de remover a anterior quando a recifragem terminar.
            log.info("Webhook secret key rotation in progress: active key id {}. Remove the previous key once "
                    + "webhook_secret.reencryption reports remaining=0", cipher.activeKeyId());
        }
        return cipher;
    }
}
