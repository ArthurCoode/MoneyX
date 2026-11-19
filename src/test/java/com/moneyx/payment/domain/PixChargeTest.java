package com.moneyx.payment.domain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PixChargeTest {

    @Test
    void txidHas25UppercaseAlphanumericCharacters() {
        assertThat(PixCharge.generateTxid()).matches("^[A-Z0-9]{25}$");
    }

    @Test
    void txidsAreUnique() {
        Set<String> txids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            txids.add(PixCharge.generateTxid());
        }
        assertThat(txids).hasSize(10_000);
    }

    @Test
    void sandboxChargeEmbedsTheAmountAndTheTxidInTheBrCode() {
        PixCharge charge = PixCharge.sandbox(4990);

        assertThat(charge.copyPaste())
                .startsWith("000201")
                .contains("540549.90")
                .contains("0525" + charge.txid())
                .contains(PixCharge.SANDBOX_PIX_KEY)
                .matches(".*6304[0-9A-F]{4}$");
    }
}
