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
        assertThat(new String(Base64.getDecoder().decode(payloadOf(stored)))).doesNotContain("whsec");
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
        String stored = cipher.encrypt("whsec_abc", "endpoint-1");
        byte[] bytes = Base64.getDecoder().decode(payloadOf(stored));
        bytes[bytes.length / 2] ^= 0x01;
        String tampered = prefixOf(stored) + Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> cipher.decrypt(tampered, "endpoint-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Decryption failed");
    }

    @Test
    void failsWithAnotherKey() {
        String stored = cipher.encrypt("whsec_abc", "endpoint-1");

        assertThatThrownBy(() -> new SecretCipher(OTHER_KEY).decrypt(stored, "endpoint-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Decryption failed");
        // Formato antigo (sem prefixo): nenhuma chave configurada decifra.
        assertThatThrownBy(() -> new SecretCipher(OTHER_KEY).decrypt(payloadOf(stored), "endpoint-1"))
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
        assertThat(cipher.toString()).isEqualTo("SecretCipher[activeKeyId=" + cipher.activeKeyId() + ", keys=***]");
        assertThat(cipher.toString()).doesNotContain(KEY);
    }

    // ---------- rotação da chave (chaveiro) ----------

    @Test
    void storedValueNamesTheActiveKey() {
        String stored = cipher.encrypt("whsec_abc", "endpoint-1");

        assertThat(cipher.activeKeyId()).matches("[0-9a-f]{8}");
        assertThat(stored).startsWith("k1." + cipher.activeKeyId() + ":");
        assertThat(cipher.isEncryptedWithActiveKey(stored)).isTrue();
    }

    /** A mesma chave sempre tem o mesmo id (vem da chave, não de numeração manual); chaves diferentes, ids diferentes. */
    @Test
    void keyIdIsStableAndDiffersBetweenKeys() {
        assertThat(new SecretCipher(KEY).activeKeyId()).isEqualTo(cipher.activeKeyId());
        assertThat(new SecretCipher(OTHER_KEY).activeKeyId()).isNotEqualTo(cipher.activeKeyId());
    }

    @Test
    void afterRotationValuesFromThePreviousKeyStillDecryptAndNewValuesUseTheNewKey() {
        SecretCipher old = new SecretCipher(OTHER_KEY);
        String fromOldKey = old.encrypt("whsec_abc", "endpoint-1");
        SecretCipher rotated = new SecretCipher(KEY, OTHER_KEY);

        assertThat(rotated.decrypt(fromOldKey, "endpoint-1")).isEqualTo("whsec_abc");
        assertThat(rotated.isEncryptedWithActiveKey(fromOldKey)).isFalse();
        assertThat(rotated.encrypt("whsec_abc", "endpoint-1")).startsWith(rotated.activePrefix());
        // A chave antiga NÃO decifra valores novos: ela só existe para ler o que já estava gravado.
        assertThatThrownBy(() -> old.decrypt(rotated.encrypt("whsec_abc", "endpoint-1"), "endpoint-1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reencryptMovesTheValueToTheActiveKeyKeepingTheSecretAndTheAssociatedData() {
        String fromOldKey = new SecretCipher(OTHER_KEY).encrypt("whsec_abc", "endpoint-1");
        SecretCipher rotated = new SecretCipher(KEY, OTHER_KEY);

        String moved = rotated.reencrypt(fromOldKey, "endpoint-1");

        assertThat(rotated.isEncryptedWithActiveKey(moved)).isTrue();
        // Decifra só com a ativa (a anterior já pode sair) e só com o MESMO AAD.
        assertThat(new SecretCipher(KEY).decrypt(moved, "endpoint-1")).isEqualTo("whsec_abc");
        assertThatThrownBy(() -> rotated.decrypt(moved, "endpoint-2")).hasMessage("Decryption failed");
    }

    /** Valores gravados antes do prefixo existir (formato antigo) decifram com a ativa OU com a anterior. */
    @Test
    void legacyValuesWithoutPrefixDecryptWithEitherConfiguredKey() {
        String legacyActive = payloadOf(new SecretCipher(KEY).encrypt("whsec_active", "endpoint-1"));
        String legacyPrevious = payloadOf(new SecretCipher(OTHER_KEY).encrypt("whsec_previous", "endpoint-1"));
        SecretCipher rotated = new SecretCipher(KEY, OTHER_KEY);

        assertThat(rotated.decrypt(legacyActive, "endpoint-1")).isEqualTo("whsec_active");
        assertThat(rotated.decrypt(legacyPrevious, "endpoint-1")).isEqualTo("whsec_previous");
        assertThat(rotated.isEncryptedWithActiveKey(legacyActive)).isFalse();
    }

    /** Chave anterior removida cedo demais: erro claro, com o id (não é segredo) da chave que falta. */
    @Test
    void valueFromAnUnknownKeyFailsWithAClearMessage() {
        SecretCipher old = new SecretCipher(OTHER_KEY);
        String fromOldKey = old.encrypt("whsec_abc", "endpoint-1");

        assertThatThrownBy(() -> cipher.decrypt(fromOldKey, "endpoint-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Decryption failed: unknown encryption key " + old.activeKeyId());
    }

    /** Trocar o id do prefixo não engana: o GCM com a outra chave recusa. */
    @Test
    void relabelingTheKeyIdDoesNotHelp() {
        SecretCipher rotated = new SecretCipher(KEY, OTHER_KEY);
        String fromOldKey = new SecretCipher(OTHER_KEY).encrypt("whsec_abc", "endpoint-1");
        String relabeled = rotated.activePrefix() + payloadOf(fromOldKey);

        assertThatThrownBy(() -> rotated.decrypt(relabeled, "endpoint-1")).hasMessage("Decryption failed");
    }

    @Test
    void previousKeyIsOptionalButMustBeValidAndDifferent() {
        assertThat(new SecretCipher(KEY, null).hasPreviousKey()).isFalse();
        assertThat(new SecretCipher(KEY, "  ").hasPreviousKey()).isFalse();
        assertThat(new SecretCipher(KEY, OTHER_KEY).hasPreviousKey()).isTrue();
        assertThatThrownBy(() -> new SecretCipher(KEY, KEY))
                .hasMessage("Previous encryption key must differ from the active key");
        assertThatThrownBy(() -> new SecretCipher(KEY, "not-base64!"))
                .hasMessage("Previous encryption key must be Base64");
        assertThatThrownBy(() -> new SecretCipher(KEY, Base64.getEncoder().encodeToString(new byte[16])))
                .hasMessage("Previous encryption key must have 32 bytes");
    }

    @Test
    void malformedPrefixIsRejected() {
        assertThatThrownBy(() -> cipher.decrypt("k1.no-separator", "endpoint-1")).hasMessage("Decryption failed");
    }

    private static String prefixOf(String stored) {
        return stored.substring(0, stored.indexOf(':') + 1);
    }

    private static String payloadOf(String stored) {
        return stored.substring(stored.indexOf(':') + 1);
    }
}
