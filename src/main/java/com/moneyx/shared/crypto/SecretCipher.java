package com.moneyx.shared.crypto;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * Cifra segredos que o MoneyX precisa LER depois (ex.: segredo de assinatura de webhook), com AES-256-GCM.
 *
 * Por que não hash: um hash é de mão única. Serve para COMPARAR (API Keys), mas não para recuperar o valor.
 * Para assinar webhooks o MoneyX precisa do segredo original, então ele é cifrado com uma chave que fica FORA
 * do banco (variável de ambiente). Quem vazar só o banco não consegue usar os segredos.
 *
 * Detalhes:
 * - GCM é cifragem AUTENTICADA: além de esconder o conteúdo, detecta qualquer alteração no texto cifrado
 *   (a decifragem falha em vez de devolver lixo).
 * - Nonce de 12 bytes aleatório a CADA cifragem: o mesmo segredo cifrado duas vezes gera resultados diferentes.
 *   Reutilizar nonce com a mesma chave quebra a segurança do GCM.
 * - associatedData (AAD): dado que não é escondido, mas entra na verificação. Usamos o id do dono do segredo:
 *   copiar o texto cifrado de um registro para outro faz a decifragem falhar.
 *
 * Formato armazenado: Base64( nonce(12) || texto cifrado || tag(16) ).
 */
public final class SecretCipher {

    public static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    /**
     * @param base64Key chave de 32 bytes em Base64 (variável de ambiente)
     * @throws IllegalArgumentException se a chave estiver ausente, mal formada ou não tiver 32 bytes
     */
    public SecretCipher(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("Encryption key is missing");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Encryption key must be Base64");
        }
        if (raw.length != KEY_BYTES) {
            throw new IllegalArgumentException("Encryption key must have " + KEY_BYTES + " bytes");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plainText, String associatedData) {
        Objects.requireNonNull(plainText, "plainText must not be null");
        Objects.requireNonNull(associatedData, "associatedData must not be null");
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /**
     * @throws IllegalStateException se o texto foi alterado, a chave é outra ou o associatedData não confere.
     *                               A mensagem é genérica de propósito: não revela qual dos três foi.
     */
    public String decrypt(String stored, String associatedData) {
        Objects.requireNonNull(stored, "stored must not be null");
        Objects.requireNonNull(associatedData, "associatedData must not be null");
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            if (all.length < NONCE_BYTES + TAG_BITS / 8) {
                throw new IllegalStateException("Decryption failed");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, NONCE_BYTES));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] plain = cipher.doFinal(all, NONCE_BYTES, all.length - NONCE_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (AEADBadTagException | IllegalArgumentException e) {
            throw new IllegalStateException("Decryption failed");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }

    @Override
    public String toString() {
        return "SecretCipher[key=***]";
    }
}
