package com.moneyx.webhook.domain;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Gera o segredo de assinatura de um endpoint: "whsec_" + 32 bytes aleatórios (SecureRandom), Base64 URL-safe.
 *
 * O prefixo "whsec_" (mesma convenção do Stripe) deixa claro o que é o valor e facilita detectar vazamentos
 * em ferramentas de varredura de segredos.
 */
public final class WebhookSecretGenerator {

    public static final String PREFIX = "whsec_";
    private static final int RANDOM_BYTES = 32;
    private static final int VISIBLE_RANDOM_CHARS = 4;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private WebhookSecretGenerator() {
    }

    public static GeneratedSecret generate() {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        String plainText = PREFIX + ENCODER.encodeToString(bytes);
        return new GeneratedSecret(plainText, plainText.substring(0, PREFIX.length() + VISIBLE_RANDOM_CHARS));
    }

    /** plainText é entregue ao comerciante uma única vez; toString mascarado para não vazar em logs. */
    public record GeneratedSecret(String plainText, String displayPrefix) {

        @Override
        public String toString() {
            return "GeneratedSecret[displayPrefix=" + displayPrefix + ", plainText=***]";
        }
    }
}
