package com.moneyx.webhook.domain;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureTest {

    private static final String SECRET = "whsec_test_secret";
    private static final long T = 1763650000L;
    private static final byte[] BODY = "{\"id\":\"evt_1\",\"type\":\"payment.paid\"}".getBytes(StandardCharsets.UTF_8);
    private static final Instant NOW = Instant.ofEpochSecond(T);
    private static final Duration TOLERANCE = WebhookSignature.DEFAULT_TOLERANCE;

    /** Vetor calculado por implementação independente (Python: hmac.new(secret, f"{t}." + body, sha256)). */
    @Test
    void matchesIndependentReferenceVector() {
        assertThat(WebhookSignature.header(SECRET, T, BODY))
                .isEqualTo("t=1763650000,v1=4f155f6bdd8788ca63ded0adfa4e0314ebc83b92c879329bdee5c08e015e8e98");
    }

    @Test
    void verifiesItsOwnSignature() {
        assertThat(WebhookSignature.verify(SECRET, WebhookSignature.header(SECRET, T, BODY), BODY, NOW, TOLERANCE)).isTrue();
    }

    @Test
    void rejectsAlteredBody() {
        String header = WebhookSignature.header(SECRET, T, BODY);
        byte[] altered = "{\"id\":\"evt_1\",\"type\":\"payment.canceled\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(WebhookSignature.verify(SECRET, header, altered, NOW, TOLERANCE)).isFalse();
    }

    @Test
    void rejectsWrongSecret() {
        String header = WebhookSignature.header("whsec_attacker", T, BODY);

        assertThat(WebhookSignature.verify(SECRET, header, BODY, NOW, TOLERANCE)).isFalse();
    }

    /** Replay: a mesma mensagem, válida, reenviada 6 minutos depois é recusada. */
    @Test
    void rejectsSignaturesOutsideTheTolerance() {
        String header = WebhookSignature.header(SECRET, T, BODY);

        assertThat(WebhookSignature.verify(SECRET, header, BODY, NOW.plusSeconds(300), TOLERANCE)).isTrue();
        assertThat(WebhookSignature.verify(SECRET, header, BODY, NOW.plusSeconds(301), TOLERANCE)).isFalse();
        assertThat(WebhookSignature.verify(SECRET, header, BODY, NOW.minusSeconds(301), TOLERANCE)).isFalse();
    }

    /** Trocar só o t (para "rejuvenescer" uma mensagem antiga) invalida a assinatura, pois o t é assinado. */
    @Test
    void timestampCannotBeChangedWithoutBreakingTheSignature() {
        String header = WebhookSignature.header(SECRET, T, BODY);
        String rejuvenated = header.replace("t=" + T, "t=" + (T + 600));

        assertThat(WebhookSignature.verify(SECRET, rejuvenated, BODY, NOW.plusSeconds(600), TOLERANCE)).isFalse();
    }

    @Test
    void rejectsMalformedHeaders() {
        assertThat(WebhookSignature.verify(SECRET, null, BODY, NOW, TOLERANCE)).isFalse();
        assertThat(WebhookSignature.verify(SECRET, "", BODY, NOW, TOLERANCE)).isFalse();
        assertThat(WebhookSignature.verify(SECRET, "t=abc,v1=00", BODY, NOW, TOLERANCE)).isFalse();
        assertThat(WebhookSignature.verify(SECRET, "t=" + T, BODY, NOW, TOLERANCE)).isFalse();
        assertThat(WebhookSignature.verify(SECRET, "t=" + T + ",v1=zz", BODY, NOW, TOLERANCE)).isFalse();
    }
}
