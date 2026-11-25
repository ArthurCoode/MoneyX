package com.moneyx.payment.provider.mercadopago;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Validação da assinatura dos avisos (webhooks) do Mercado Pago: header {@code x-signature: ts=<ts>,v1=<hex>}.
 *
 * Manifesto assinado (HMAC-SHA256 com a "assinatura secreta" do painel do MP, resultado em hex):
 * {@code id:<data.id da URL>;request-id:<header x-request-id>;ts:<ts>;}. O data.id alfanumérico vai em minúsculas;
 * uma parte sem valor é omitida.
 *
 * [?] O formato do manifesto NÃO foi encontrado nas páginas lidas em 2026-11-25 (só o exemplo do header): segue o que a
 * documentação de "validar a origem da notificação" descrevia. Confirmar com um aviso real (pendência da 7e).
 *
 * Comparação em tempo constante (MessageDigest.isEqual): uma comparação comum revelaria, pelo tempo de resposta,
 * quantos caracteres iniciais estão certos.
 */
public final class MercadoPagoWebhookSignature {

    private MercadoPagoWebhookSignature() {
    }

    /** @return true só se o header estiver bem formado e a assinatura v1 conferir */
    public static boolean verify(String secret, String signatureHeader, String requestId, String dataId) {
        if (secret == null || secret.isBlank() || signatureHeader == null) {
            return false;
        }
        String ts = null;
        String v1 = null;
        for (String part : signatureHeader.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            if (pair[0].equals("ts")) {
                ts = pair[1].trim();
            } else if (pair[0].equals("v1")) {
                v1 = pair[1].trim();
            }
        }
        if (ts == null || ts.isEmpty() || v1 == null || !v1.matches("[0-9a-fA-F]{64}")) {
            return false;
        }
        byte[] expected = sign(secret, manifest(dataId, requestId, ts)).getBytes(StandardCharsets.US_ASCII);
        byte[] received = v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, received);
    }

    static String manifest(String dataId, String requestId, String ts) {
        StringBuilder manifest = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            String id = dataId.matches("[A-Za-z0-9]+") ? dataId.toLowerCase(Locale.ROOT) : dataId;
            manifest.append("id:").append(id).append(';');
        }
        if (requestId != null && !requestId.isBlank()) {
            manifest.append("request-id:").append(requestId).append(';');
        }
        manifest.append("ts:").append(ts).append(';');
        return manifest.toString();
    }

    static String sign(String secret, String manifest) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
