package com.moneyx.webhook.service;

import com.moneyx.webhook.service.WebhookDestinationGuard.Verdict;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/** Usa apenas IPs literais, "localhost" e o domínio reservado ".invalid" (RFC 2606): nada depende da internet. */
class WebhookDestinationGuardTest {

    private final WebhookDestinationGuard strict = new WebhookDestinationGuard(false);
    private final WebhookDestinationGuard local = new WebhookDestinationGuard(true);

    @Test
    void allowsPublicAddresses() {
        assertThat(strict.check(URI.create("https://8.8.8.8/hook"))).isEqualTo(Verdict.ALLOWED);
    }

    @Test
    void blocksInternalAddressesAtDeliveryTime() {
        assertThat(strict.check(URI.create("https://10.0.0.1/hook"))).isEqualTo(Verdict.NOT_ALLOWED);
        assertThat(strict.check(URI.create("https://169.254.169.254/latest"))).isEqualTo(Verdict.NOT_ALLOWED);
        assertThat(strict.check(URI.create("http://127.0.0.1:9000/hook"))).isEqualTo(Verdict.NOT_ALLOWED);
        // Um NOME que resolve para loopback é barrado pelo IP resolvido, não pelo texto.
        assertThat(strict.check(URI.create("http://localhost:9000/hook"))).isEqualTo(Verdict.NOT_ALLOWED);
    }

    @Test
    void localModeAllowsOnlyLoopback() {
        assertThat(local.check(URI.create("http://127.0.0.1:9000/hook"))).isEqualTo(Verdict.ALLOWED);
        assertThat(local.check(URI.create("http://localhost:9000/hook"))).isEqualTo(Verdict.ALLOWED);
        assertThat(local.check(URI.create("https://10.0.0.1/hook"))).isEqualTo(Verdict.NOT_ALLOWED);
        assertThat(local.check(URI.create("https://192.168.0.10/hook"))).isEqualTo(Verdict.NOT_ALLOWED);
    }

    @Test
    void reportsUnresolvableHosts() {
        assertThat(strict.check(URI.create("https://does-not-exist.invalid/hook"))).isEqualTo(Verdict.UNRESOLVABLE);
    }
}
