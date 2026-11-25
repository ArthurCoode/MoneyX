package com.moneyx.payment.provider;

import com.moneyx.payment.domain.PixCharge;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxPixProviderTest {

    private final SandboxPixProvider provider = new SandboxPixProvider();

    @Test
    void txidHas25UppercaseAlphanumericCharacters() {
        assertThat(SandboxPixProvider.generateTxid()).matches("^[A-Z0-9]{25}$");
    }

    @Test
    void txidsAreUnique() {
        Set<String> txids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            txids.add(SandboxPixProvider.generateTxid());
        }
        assertThat(txids).hasSize(10_000);
    }

    @Test
    void sandboxChargeEmbedsTheAmountAndTheTxidInTheBrCode() {
        PixCharge charge = provider.createCharge(
                new PixChargeRequest(UUID.randomUUID(), 4990, java.time.Duration.ofMinutes(30), null));

        assertThat(charge.copyPaste())
                .startsWith("000201")
                .contains("540549.90")
                .contains("0525" + charge.txid())
                .contains(SandboxPixProvider.PIX_KEY)
                .matches(".*6304[0-9A-F]{4}$");
    }

    @Test
    void txidFitsTheColumn() {
        assertThat(SandboxPixProvider.TXID_LENGTH).isLessThanOrEqualTo(PixCharge.TXID_MAX_LENGTH);
        assertThat(provider.name()).isEqualTo("sandbox");
    }
}
