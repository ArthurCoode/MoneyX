package com.moneyx.merchant.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Segredo aleatório de uso interno (sessão do dashboard, link de senha): 32 bytes do SecureRandom em Base64 URL-safe,
 * com prefixo opcional. Guardamos só o SHA-256 (como as API Keys): quem lê o banco não consegue usar o segredo.
 *
 * SHA-256 simples (sem sal, sem Argon2) basta aqui, ao contrário da senha: o segredo tem 256 bits de entropia, então
 * não existe dicionário para atacar; o que protege uma SENHA (escolhida por pessoa) é o hash lento.
 *
 * @param plainText devolvido UMA vez (no cookie ou no e-mail); nunca guardado nem logado
 */
public record SecretToken(String plainText, String hash) {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BYTES = 32;

    public static SecretToken generate(String prefix) {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        String plain = prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new SecretToken(plain, hash(plain));
    }

    public static String hash(String plainText) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(plainText.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Override
    public String toString() {
        return "SecretToken[plainText=***]";
    }
}
