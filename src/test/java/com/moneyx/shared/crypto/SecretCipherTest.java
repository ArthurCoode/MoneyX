package com.moneyx.shared.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    private static final String KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final String OTHER_KEY = "HxwdHhsaGRgXFhUUExIREA8ODQwLCgkIBwYFBAMCAQA=";

    private final SecretCipher cipher = new SecretCipher(KEY);

    @Test
    void decryptsWhatItEncrypted() {
        String stored = cipher.encrypt("whsec_abc", "endpoint-1");

        assertThat(cipher.decrypt(stored, "endpoint-1")).isEqualTo("whsec_abc");
    }

    @Test
    void storedValueDoesNotContainThePlainText() {
        String stored = cipher.encrypt("whsec_abc", "endpoint-1");

        assertThat(stored).doesNotContain("whsec");
        assertThat(new String(Base64.getDecoder().decode(stored))).doesNotContain("whsec");
    }

    /** Nonce aleatório: o mesmo segredo cifrado duas vezes dá resultados diferentes (e ambos decifram). */
    @Test
    void sameSecretEncryptsDifferentlyEachTime() {
        String first = cipher.encrypt("whsec_abc", "endpoint-1");
        String second = cipher.encrypt("whsec_abc", "endpoint-1");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(second, "endpoint-1")).isEqualTo("whsec_abc");
    }

    /** AAD: o texto cifrado de um endpoint não pode ser "colado" em outro. */
    @Test
    void failsWhenAssociatedDataDoesNotMatch() {
        String stored = cipher.encrypt("whsec_abc", "endpoint-1");

        assertThatThrownBy(() -> cipher.decrypt(stored, "endpoint-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Decryption failed");
    }

    /** GCM é autenticado: alterar um único bit faz a decifragem falhar (em vez de devolver lixo). */
    @Test
    void detectsTampering() {
        byte[] bytes = Base64.getDecoder().decode(cipher.encrypt("whsec_abc", "endpoint-1"));
        bytes[bytes.length / 2] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> cipher.decrypt(tampered, "endpoint-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Decryption failed");
    }

    @Test
    void failsWithAnotherKey() {
        String stored = cipher.encrypt("whsec_abc", "endpoint-1");

        assertThatThrownBy(() -> new SecretCipher(OTHER_KEY).decrypt(stored, "endpoint-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Decryption failed");
    }

    @Test
    void failsOnGarbageInput() {
        assertThatThrownBy(() -> cipher.decrypt("not base64 !!", "endpoint-1")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt("AAAA", "endpoint-1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMissingOrInvalidKeys() {
        assertThatThrownBy(() -> new SecretCipher(null)).hasMessage("Encryption key is missing");
        assertThatThrownBy(() -> new SecretCipher("  ")).hasMessage("Encryption key is missing");
        assertThatThrownBy(() -> new SecretCipher("not-base64!")).hasMessage("Encryption key must be Base64");
        assertThatThrownBy(() -> new SecretCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .hasMessage("Encryption key must have 32 bytes");
    }

    @Test
    void toStringNeverShowsTheKey() {
        assertThat(cipher.toString()).isEqualTo("SecretCipher[key=***]");
    }
}
