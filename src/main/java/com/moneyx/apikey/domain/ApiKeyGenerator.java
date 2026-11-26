package com.moneyx.apikey.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Geração e hash de API Keys.
 *
 * Formato: mx_test_ + 32 bytes aleatórios em Base64 URL-safe (43 caracteres).
 * Ex.: mx_test_9fK2c8Qp...xQ7
 *
 * - SecureRandom: gerador criptograficamente seguro. java.util.Random é previsível e nunca
 *   deve ser usado para segredos.
 * - 32 bytes = 256 bits de entropia: impossível de adivinhar por força bruta.
 * - SHA-256: como a chave tem alta entropia, um hash rápido e determinístico é seguro e
 *   permite buscar a chave no banco por índice (BCrypt seria necessário apenas para senhas).
 */
public final class ApiKeyGenerator {

    public static final String TEST_PREFIX = "mx_test_";
    /** Chave de produção: só para comerciante com e-mail verificado (ApiKeyService). */
    public static final String LIVE_PREFIX = "mx_live_";

    private static final int RANDOM_BYTES = 32;
    private static final int VISIBLE_RANDOM_CHARS = 4;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private ApiKeyGenerator() {
    }

    public static GeneratedApiKey generate() {
        return generate(false);
    }

    public static GeneratedApiKey generate(boolean live) {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);

        String prefix = live ? LIVE_PREFIX : TEST_PREFIX;
        String plainText = prefix + ENCODER.encodeToString(bytes);
        String displayPrefix = plainText.substring(0, prefix.length() + VISIBLE_RANDOM_CHARS);

        return new GeneratedApiKey(plainText, displayPrefix, hash(plainText));
    }

    /** SHA-256 em hexadecimal minúsculo (64 caracteres). */
    public static String hash(String plainText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(plainText.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // Toda JVM é obrigada a oferecer SHA-256; chegar aqui indica ambiente quebrado.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Resultado da geração.
     *
     * plainText deve ser entregue ao comerciante uma única vez e nunca persistido nem logado.
     * toString foi sobrescrito para impedir que o segredo vaze em logs por acidente.
     */
    public record GeneratedApiKey(String plainText, String displayPrefix, String hash) {

        @Override
        public String toString() {
            return "GeneratedApiKey[displayPrefix=" + displayPrefix + ", plainText=***]";
        }
    }
}
