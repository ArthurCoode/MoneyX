package com.moneyx.payment.service;

import com.moneyx.payment.domain.PaymentCurrency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Dados para criar um pagamento, independentes de HTTP (a camada api converte o DTO neste comando).
 *
 * @param expiresInSeconds prazo para pagamento; null usa o padrão do domínio (30 min)
 */
public record CreatePaymentCommand(
        long amount,
        PaymentCurrency currency,
        String description,
        String externalReference,
        Long expiresInSeconds
) {

    /**
     * "Impressão digital" da requisição: SHA-256 de todos os campos.
     *
     * Usada para detectar a mesma Idempotency-Key reutilizada com um corpo DIFERENTE.
     *
     * Cada campo é codificado como <tamanho>:<valor> (null vira "-"). Sem o prefixo de tamanho,
     * combinações diferentes poderiam produzir o mesmo texto concatenado, por exemplo
     * description="a|b" + externalReference=null  versus  description="a" + externalReference="b".
     */
    public String fingerprint() {
        StringBuilder canonical = new StringBuilder();
        append(canonical, Long.toString(amount));
        append(canonical, currency == null ? null : currency.name());
        append(canonical, description);
        append(canonical, externalReference);
        append(canonical, expiresInSeconds == null ? null : expiresInSeconds.toString());
        return sha256(canonical.toString());
    }

    private static void append(StringBuilder target, String value) {
        if (value == null) {
            target.append('-');
        } else {
            target.append(value.length()).append(':').append(value);
        }
        target.append(';');
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
