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
    void temporaryFailureIsRescheduledAndStaysPending() {
        UUID token = delivery.claim(NOW, LEASE);

        boolean rescheduled = delivery.recordFailure(token, WebhookDeliveryError.NON_2XX_RESPONSE, 500, 10, NOW, 0);

        assertThat(rescheduled).isTrue();
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(delivery.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
        assertThat(delivery.getLastError()).isEqualTo("non_2xx_response");
        assertThat(delivery.getLastStatusCode()).isEqualTo(500);
        assertThat(delivery.getLeaseUntil()).isNull();
    }

    @Test
    void blockedDestinationGivesUpImmediately() {
        UUID token = delivery.claim(NOW, LEASE);

        boolean rescheduled = delivery.recordFailure(token, WebhookDeliveryError.DESTINATION_NOT_ALLOWED, null, 1, NOW, 0);

        assertThat(rescheduled).isFalse();
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(delivery.getAttempts()).isEqualTo(1);
    }

    /** Percorre a agenda inteira: 9 tentativas com as esperas previstas e, depois da 9ª falha, FAILED. */
    @Test
    void followsTheWholeScheduleAndThenGivesUp() {
        Instant now = NOW;
        for (int attempt = 1; attempt <= WebhookRetryPolicy.MAX_ATTEMPTS; attempt++) {
            UUID token = delivery.claim(now, LEASE);
            boolean rescheduled = delivery.recordFailure(token, WebhookDeliveryError.TIMEOUT, null, 1, now, 0);
            if (attempt < WebhookRetryPolicy.MAX_ATTEMPTS) {
                assertThat(rescheduled).isTrue();
                assertThat(delivery.getNextAttemptAt()).isEqualTo(now.plus(WebhookRetryPolicy.DELAYS.get(attempt - 1)));
                now = delivery.getNextAttemptAt();
            } else {
                assertThat(rescheduled).isFalse();
            }
        }
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(delivery.getAttempts()).isEqualTo(9);
        // ~2,7 dias entre a primeira e a última tentativa
        assertThat(Duration.between(NOW, delivery.getLastAttemptAt())).isEqualTo(Duration.ofMinutes(36).plusHours(65));
    }

    @Test
    void manualRetryRequeuesAFailedDeliveryForNowAndKeepsCountingAttempts() {
        exhaust();
        Instant later = NOW.plus(Duration.ofDays(5));

        delivery.requeue(later);

        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(delivery.getNextAttemptAt()).isEqualTo(later);
        UUID token = delivery.claim(later, LEASE);
        // Agenda já esgotada: uma nova falha devolve direto para FAILED (é "mais uma tentativa").
        assertThat(delivery.recordFailure(token, WebhookDeliveryError.TIMEOUT, null, 1, later, 0)).isFalse();
        assertThat(delivery.getAttempts()).isEqualTo(10);
    }

    @Test
    void onlyFailedDeliveriesCanBeRequeued() {
        assertThatThrownBy(() -> delivery.requeue(NOW)).isInstanceOf(IllegalStateException.class);
        UUID token = delivery.claim(NOW, LEASE);
        delivery.recordSuccess(token, 200, 1, NOW);
        assertThatThrownBy(() -> delivery.requeue(NOW)).isInstanceOf(IllegalStateException.class);
    }

    private void exhaust() {
        Instant now = NOW;
        for (int attempt = 1; attempt <= WebhookRetryPolicy.MAX_ATTEMPTS; attempt++) {
            UUID token = delivery.claim(now, LEASE);
            delivery.recordFailure(token, WebhookDeliveryError.TIMEOUT, null, 1, now, 0);
            now = now.plus(Duration.ofDays(1));
        }
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
