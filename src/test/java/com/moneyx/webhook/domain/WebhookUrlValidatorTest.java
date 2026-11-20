package com.moneyx.webhook.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookUrlValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://loja.com/webhooks/moneyx",
            "https://api.loja.com.br:8443/hooks?source=moneyx",
            "https://8.8.8.8/hook",              // IP público escrito diretamente
            "https://[2001:4860:4860::8888]/hook" // IPv6 público
    })
    void acceptsPublicHttpsUrls(String url) {
        assertThat(WebhookUrlValidator.validate(url, false)).isEqualTo(url);
    }

    @Test
    void trimsSurroundingSpaces() {
        assertThat(WebhookUrlValidator.validate("  https://loja.com/h  ", false)).isEqualTo("https://loja.com/h");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "http://loja.com/hook                 | url must use https",
            "ftp://loja.com/hook                  | url must use https",
            "/webhooks/moneyx                     | url must be an absolute URL",
            "mailto:dev@loja.com                  | url must be an absolute URL",
            "https://loja com/hook                | url is not a valid URL",
            "https://user:pass@loja.com/hook      | url must not contain credentials",
            "https://loja.com@10.0.0.1/hook       | url must not contain credentials",
            "https://loja.com/hook#frag           | url must not contain a fragment",
            "https://localhost/hook               | url must not point to localhost",
            "https://LOCALHOST./hook              | url must not point to localhost",
            "https://api.localhost/hook           | url must not point to localhost",
            "https://127.0.0.1/hook               | url must not point to a private or internal address",
            "https://127.1.2.3/hook               | url must not point to a private or internal address",
            "https://10.0.0.5/admin               | url must not point to a private or internal address",
            "https://172.16.0.1/hook              | url must not point to a private or internal address",
            "https://192.168.1.10/hook            | url must not point to a private or internal address",
            "https://169.254.169.254/latest/meta-data | url must not point to a private or internal address",
            "https://100.64.0.1/hook              | url must not point to a private or internal address",
            "https://0.0.0.0/hook                 | url must not point to a private or internal address",
            "https://[::1]/hook                   | url must not point to a private or internal address",
            "https://[fd12:3456::1]/hook          | url must not point to a private or internal address",
            "https://[fe80::1]/hook               | url must not point to a private or internal address",
            "https://[::ffff:127.0.0.1]/hook      | url must not point to a private or internal address",
            // Formas alternativas de escrever 127.0.0.1, entendidas por alguns clientes HTTP:
            "https://2130706433/hook              | url host is not a valid IP address",
            "https://0177.0.0.1/hook              | url host is not a valid IP address",
            // Estas duas o próprio java.net.URI já recusa como host (o último rótulo de um nome precisa começar
            // com letra), então são barradas pela regra anterior. O importante é que nenhuma passa.
            "https://0x7f.0.0.1/hook              | url must have a valid host",
            "https://127.1/hook                   | url must have a valid host"
    })
    void rejectsUnsafeOrInvalidUrls(String url, String reason) {
        assertThatThrownBy(() -> WebhookUrlValidator.validate(url, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(reason);
    }

    @Test
    void rejectsBlankAndTooLongUrls() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate(null, false)).hasMessage("url is required");
        assertThatThrownBy(() -> WebhookUrlValidator.validate("  ", false)).hasMessage("url is required");
        String tooLong = "https://loja.com/" + "a".repeat(WebhookUrlValidator.MAX_LENGTH);
        assertThatThrownBy(() -> WebhookUrlValidator.validate(tooLong, false))
                .hasMessage("url must have at most 2048 characters");
    }

    // ---------- exceção para testes locais ----------

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:9000/hook", "http://127.0.0.1:9000/hook", "https://localhost/hook",
            "http://[::1]:9000/hook"})
    void allowLocalUrlsPermitsOnlyLocalhostAndLoopback(String url) {
        assertThat(WebhookUrlValidator.validate(url, true)).isEqualTo(url);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "http://loja.com/hook       | url must use https",
            "http://10.0.0.5/hook       | url must use https",
            "https://192.168.0.1/hook   | url must not point to a private or internal address",
            "https://169.254.169.254/x  | url must not point to a private or internal address"
    })
    void allowLocalUrlsDoesNotOpenOtherInternalNetworks(String url, String reason) {
        assertThatThrownBy(() -> WebhookUrlValidator.validate(url, true)).hasMessage(reason);
    }

    @Test
    void isInternalAddressClassifiesResolvedAddresses() throws Exception {
        assertThat(WebhookUrlValidator.isInternalAddress(InetAddress.getByName("10.1.2.3"))).isTrue();
        assertThat(WebhookUrlValidator.isInternalAddress(InetAddress.getByName("240.0.0.1"))).isTrue();
        assertThat(WebhookUrlValidator.isInternalAddress(InetAddress.getByName("255.255.255.255"))).isTrue();
        assertThat(WebhookUrlValidator.isInternalAddress(InetAddress.getByName("224.0.0.1"))).isTrue();
        assertThat(WebhookUrlValidator.isInternalAddress(InetAddress.getByName("100.128.0.1"))).isFalse();
        assertThat(WebhookUrlValidator.isInternalAddress(InetAddress.getByName("8.8.8.8"))).isFalse();
    }
}
