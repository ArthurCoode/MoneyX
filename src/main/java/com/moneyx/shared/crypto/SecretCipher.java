package com.moneyx.shared.crypto;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * <h2>Rotação de chave (chaveiro)</h2>
 * Há uma chave ATIVA (cifra e decifra) e, durante uma rotação, uma chave ANTERIOR (só decifra). O valor gravado diz
 * qual chave o cifrou: {@code k1.<id da chave>:Base64(nonce || texto cifrado || tag)}.
 * - "k1." é a versão deste formato.
 * - O id são os 8 primeiros caracteres hex do SHA-256 da chave (com um rótulo fixo na frente). Não revela a chave
 *   (256 bits aleatórios) e não precisa ser numerado à mão: a mesma chave sempre tem o mesmo id.
 * - Valores SEM prefixo são do formato antigo (antes da rotação existir): tentamos a ativa e depois a anterior.
 *   O GCM garante que só a chave certa decifra, então tentar não tem risco de devolver lixo.
 */
public final class SecretCipher {

    public static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String FORMAT_PREFIX = "k1.";
    private static final byte[] KEY_ID_LABEL = "moneyx-secret-cipher-key-id:".getBytes(StandardCharsets.UTF_8);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey activeKey;
    private final String activeKeyId;
    /** Chaves por id, a ativa primeiro (ordem das tentativas para valores sem prefixo). */
    private final Map<String, SecretKey> keysById = new LinkedHashMap<>();

    /** Só a chave ativa (sem rotação em andamento). */
    public SecretCipher(String base64Key) {
        this(base64Key, null);
    }

    /**
     * @param base64Key         chave ATIVA de 32 bytes em Base64 (obrigatória)
     * @param previousBase64Key chave ANTERIOR, opcional (null ou em branco = nenhuma); só decifra
     * @throws IllegalArgumentException se uma chave estiver mal formada, ou se a anterior for igual à ativa
     */
    public SecretCipher(String base64Key, String previousBase64Key) {
        byte[] active = parseKey("Encryption key", base64Key);
        this.activeKey = new SecretKeySpec(active, "AES");
        this.activeKeyId = keyIdOf(active);
        keysById.put(activeKeyId, activeKey);

        if (previousBase64Key != null && !previousBase64Key.isBlank()) {
            byte[] previous = parseKey("Previous encryption key", previousBase64Key);
            String previousId = keyIdOf(previous);
            if (MessageDigest.isEqual(active, previous) || previousId.equals(activeKeyId)) {
                throw new IllegalArgumentException("Previous encryption key must differ from the active key");
            }
            keysById.put(previousId, new SecretKeySpec(previous, "AES"));
        }
    }

    /** Id público da chave ativa (ex.: "3f9a12c4"). Não é segredo: pode aparecer em log. */
    public String activeKeyId() {
        return activeKeyId;
    }

    /** true se existe uma chave anterior configurada (rotação em andamento). */
    public boolean hasPreviousKey() {
        return keysById.size() > 1;
    }

    /** Prefixo dos valores cifrados com a chave ativa; o que não começar com ele precisa ser recifrado. */
    public String activePrefix() {
        return FORMAT_PREFIX + activeKeyId + ":";
    }

    public boolean isEncryptedWithActiveKey(String stored) {
        return stored != null && stored.startsWith(activePrefix());
    }

    /** Sempre cifra com a chave ATIVA. */
    public String encrypt(String plainText, String associatedData) {
        Objects.requireNonNull(plainText, "plainText must not be null");
        Objects.requireNonNull(associatedData, "associatedData must not be null");
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, activeKey, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return activePrefix() + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /**
     * @throws IllegalStateException "Decryption failed" se o texto foi alterado, a chave é outra ou o associatedData
     *                               não confere (genérico de propósito: não revela qual dos três foi); ou
     *                               "unknown encryption key" se o valor foi cifrado com uma chave que não está
     *                               configurada (ex.: a anterior foi removida do .env cedo demais).
     */
    public String decrypt(String stored, String associatedData) {
        Objects.requireNonNull(stored, "stored must not be null");
        Objects.requireNonNull(associatedData, "associatedData must not be null");

        if (stored.startsWith(FORMAT_PREFIX)) {
            int separator = stored.indexOf(':');
            if (separator < 0) {
                throw new IllegalStateException("Decryption failed");
            }
            String keyId = stored.substring(FORMAT_PREFIX.length(), separator);
            SecretKey key = keysById.get(keyId);
            if (key == null) {
                throw new IllegalStateException("Decryption failed: unknown encryption key " + keyId);
            }
            return decryptWith(key, stored.substring(separator + 1), associatedData);
        }

        // Formato antigo (sem prefixo): tenta cada chave; o GCM só aceita a certa.
        for (SecretKey key : keysById.values()) {
            try {
                return decryptWith(key, stored, associatedData);
            } catch (IllegalStateException wrongKey) {
                // tenta a próxima
            }
        }
        throw new IllegalStateException("Decryption failed");
    }

    /** Decifra com a chave que o cifrou e cifra de novo com a ativa (mesmo associatedData). */
    public String reencrypt(String stored, String associatedData) {
        return encrypt(decrypt(stored, associatedData), associatedData);
    }

    private static String decryptWith(SecretKey key, String base64, String associatedData) {
        try {
            byte[] all = Base64.getDecoder().decode(base64);
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

    private static byte[] parseKey(String name, String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException(name + " is missing");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(name + " must be Base64");
        }
        if (raw.length != KEY_BYTES) {
            throw new IllegalArgumentException(name + " must have " + KEY_BYTES + " bytes");
        }
        return raw;
    }

    private static String keyIdOf(byte[] rawKey) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            sha256.update(KEY_ID_LABEL);
            byte[] digest = sha256.digest(rawKey);
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Override
    public String toString() {
        return "SecretCipher[activeKeyId=" + activeKeyId + ", keys=***]";
    }
}
