package com.moneyx.webhook.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookDeliveryTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    private static final Duration LEASE = Duration.ofSeconds(30);

    private final WebhookDelivery delivery =
            WebhookDelivery.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW);

    @Test
    void startsPendingWithoutAttempts() {
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(delivery.getAttempts()).isZero();
        assertThat(delivery.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(delivery.getLeaseUntil()).isNull();
    }

    @Test
    void successIsRecordedOnlyByTheCurrentLeaseHolder() {
        UUID token = delivery.claim(NOW, LEASE);

        delivery.recordSuccess(token, 200, 42, NOW.plusSeconds(1));

        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.SUCCEEDED);
        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getLastStatusCode()).isEqualTo(200);
        assertThat(delivery.getLastDurationMs()).isEqualTo(42);
        assertThat(delivery.getDeliveredAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(delivery.getLeaseUntil()).isNull();
    }

    @Test
    void failureIsRecordedWithAStableErrorCode() {
        UUID token = delivery.claim(NOW, LEASE);

        delivery.recordFailure(token, WebhookDeliveryError.NON_2XX_RESPONSE, 500, 10, NOW);

        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(delivery.getLastError()).isEqualTo("non_2xx_response");
        assertThat(delivery.getLastStatusCode()).isEqualTo(500);
        assertThat(delivery.getDeliveredAt()).isNull();
    }

    @Test
    void cannotBeClaimedTwiceWhileTheLeaseIsValid() {
        delivery.claim(NOW, LEASE);

        assertThatThrownBy(() -> delivery.claim(NOW.plusSeconds(29), LEASE)).isInstanceOf(IllegalStateException.class);
    }

    /** Reserva vencida: outra execução reserva de novo e o resultado atrasado da primeira é recusado. */
    @Test
    void expiredLeaseCanBeReclaimedAndTheOldTokenStopsWorking() {
        UUID first = delivery.claim(NOW, LEASE);
        UUID second = delivery.claim(NOW.plusSeconds(30), LEASE);

        assertThat(second).isNotEqualTo(first);
        assertThat(delivery.isLeasedBy(first)).isFalse();
        assertThatThrownBy(() -> delivery.recordSuccess(first, 200, 1, NOW.plusSeconds(31)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);

        delivery.recordSuccess(second, 200, 1, NOW.plusSeconds(31));
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.SUCCEEDED);
    }

    @Test
    void finalDeliveriesCannotBeClaimedAgain() {
        UUID token = delivery.claim(NOW, LEASE);
        delivery.cancel(token);

        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.CANCELED);
        assertThat(delivery.getAttempts()).isZero();
        assertThatThrownBy(() -> delivery.claim(NOW.plusSeconds(60), LEASE)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resultWithoutAnyLeaseIsRejected() {
        assertThatThrownBy(() -> delivery.recordSuccess(UUID.randomUUID(), 200, 1, NOW))
                .isInstanceOf(IllegalStateException.class);
    }
}
