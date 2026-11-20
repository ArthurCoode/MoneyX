package com.moneyx.webhook.domain;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Assinatura dos webhooks (formato inspirado no Stripe):
 *
 *   MoneyX-Signature: t=1763650000,v1=5f2b9c...
 *   v1 = hex( HMAC-SHA256( segredo, t + "." + corpo ) )
 *
 * - HMAC prova que quem enviou conhece o segredo e que o corpo não foi alterado no caminho.
 * - O timestamp DENTRO do que é assinado impede replay: quem capturar uma mensagem não consegue reenviá-la mais
 *   tarde, pois a loja recusa t muito antigo (tolerância sugerida: 5 minutos). Trocar o t invalida a assinatura.
 * - "v1=" é a versão do esquema: permite um "v2=" no futuro sem quebrar quem já integrou.
 *
 * verify() é a implementação de referência do lado da LOJA (documentação e testes). A comparação é em tempo
 * constante (MessageDigest.isEqual): comparar com equals() vaza, pelo tempo de resposta, quantos caracteres batem.
 */
public final class WebhookSignature {

    public static final String HEADER = "MoneyX-Signature";
    public static final Duration DEFAULT_TOLERANCE = Duration.ofMinutes(5);
    private static final String ALGORITHM = "HmacSHA256";

    private WebhookSignature() {
    }

    public static String header(String secret, long timestampSeconds, byte[] body) {
        return "t=" + timestampSeconds + ",v1=" + HexFormat.of().formatHex(hmac(secret, timestampSeconds, body));
    }

    /**
     * @return true se a assinatura confere e o timestamp está dentro da tolerância (para trás e para frente)
     */
    public static boolean verify(String secret, String header, byte[] body, Instant now, Duration tolerance) {
        if (header == null) {
            return false;
        }
        Long timestamp = null;
        String signature = null;
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if (kv[0].equals("t")) {
                try {
                    timestamp = Long.parseLong(kv[1]);
                } catch (NumberFormatException e) {
                    return false;
                }
            } else if (kv[0].equals("v1")) {
                signature = kv[1];
            }
        }
        if (timestamp == null || signature == null) {
            return false;
        }
        long age = Math.abs(now.getEpochSecond() - timestamp);
        if (age > tolerance.toSeconds()) {
            return false;
        }
        byte[] received;
        try {
            received = HexFormat.of().parseHex(signature);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(hmac(secret, timestamp, body), received);
    }

    private static byte[] hmac(String secret, long timestampSeconds, byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            mac.update((timestampSeconds + ".").getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(body);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }
}
