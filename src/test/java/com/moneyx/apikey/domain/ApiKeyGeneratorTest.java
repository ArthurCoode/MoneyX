package com.moneyx.apikey.domain;

import com.moneyx.apikey.domain.ApiKeyGenerator.GeneratedApiKey;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyGeneratorTest {

    @Test
    void generatedKeyHasExpectedFormat() {
        GeneratedApiKey key = ApiKeyGenerator.generate();

        // mx_test_ (8) + 32 bytes em Base64 URL-safe sem padding (43) = 51 caracteres
        assertThat(key.plainText()).startsWith("mx_test_").hasSize(51);
        assertThat(key.plainText()).matches("mx_test_[A-Za-z0-9_-]{43}");
    }

    @Test
    void displayPrefixIsTheBeginningOfTheKey() {
        GeneratedApiKey key = ApiKeyGenerator.generate();

        assertThat(key.displayPrefix()).hasSize(12);
        assertThat(key.plainText()).startsWith(key.displayPrefix());
    }

    @Test
    void hashIsSha256HexAndMatchesThePlainText() {
        GeneratedApiKey key = ApiKeyGenerator.generate();

        assertThat(key.hash()).matches("[0-9a-f]{64}");
        assertThat(key.hash()).isEqualTo(ApiKeyGenerator.hash(key.plainText()));
        assertThat(key.hash()).doesNotContain(key.plainText());
    }

    @Test
    void hashIsDeterministicAndMatchesKnownValue() {
        // Vetor de teste conhecido: SHA-256("abc")
        assertThat(ApiKeyGenerator.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void generatedKeysAreUnique() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            keys.add(ApiKeyGenerator.generate().plainText());
        }

        assertThat(keys).hasSize(1_000);
    }

    @Test
    void toStringDoesNotLeakTheSecret() {
        GeneratedApiKey key = ApiKeyGenerator.generate();

        assertThat(key.toString()).doesNotContain(key.plainText()).contains("***");
    }
}
