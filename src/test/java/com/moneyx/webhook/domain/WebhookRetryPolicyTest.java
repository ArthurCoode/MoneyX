package com.moneyx.webhook.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookRetryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    @ParameterizedTest
    @CsvSource({"1, PT1M", "2, PT5M", "3, PT30M", "4, PT2H", "5, PT5H", "6, PT10H", "7, PT24H", "8, PT24H"})
    void waitsLongerAfterEachFailure(int failedAttempt, Duration expectedWait) {
        assertThat(WebhookRetryPolicy.nextAttemptAt(failedAttempt, WebhookDeliveryError.TIMEOUT, NOW, 0))
                .contains(NOW.plus(expectedWait));
    }

    @Test
    void givesUpAfterTheNinthAttempt() {
        assertThat(WebhookRetryPolicy.MAX_ATTEMPTS).isEqualTo(9);
        assertThat(WebhookRetryPolicy.nextAttemptAt(9, WebhookDeliveryError.TIMEOUT, NOW, 0)).isEmpty();
        assertThat(WebhookRetryPolicy.nextAttemptAt(12, WebhookDeliveryError.TIMEOUT, NOW, 0)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = WebhookDeliveryError.class, names = "DESTINATION_NOT_ALLOWED", mode = EnumSource.Mode.EXCLUDE)
    void temporaryFailuresAreRetried(WebhookDeliveryError error) {
        assertThat(WebhookRetryPolicy.nextAttemptAt(1, error, NOW, 0)).isPresent();
    }

    @Test
    void blockedDestinationIsNeverRetried() {
        assertThat(WebhookRetryPolicy.nextAttemptAt(1, WebhookDeliveryError.DESTINATION_NOT_ALLOWED, NOW, 0)).isEmpty();
    }

    /** O jitter desloca a espera proporcionalmente: +10% de 30 min = 33 min; -10% = 27 min. */
    @Test
    void jitterSpreadsTheRetryProportionally() {
        assertThat(WebhookRetryPolicy.nextAttemptAt(3, WebhookDeliveryError.TIMEOUT, NOW, 0.1))
                .contains(NOW.plus(Duration.ofMinutes(33)));
        assertThat(WebhookRetryPolicy.nextAttemptAt(3, WebhookDeliveryError.TIMEOUT, NOW, -0.1))
                .contains(NOW.plus(Duration.ofMinutes(27)));
    }

    @Test
    void rejectsInvalidAttemptNumbers() {
        assertThatThrownBy(() -> WebhookRetryPolicy.nextAttemptAt(0, WebhookDeliveryError.TIMEOUT, NOW, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
