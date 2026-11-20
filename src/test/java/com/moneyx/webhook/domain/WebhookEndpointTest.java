package com.moneyx.webhook.domain;

import com.moneyx.event.domain.EventType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookEndpointTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    private final WebhookEndpoint endpoint = WebhookEndpoint.create(UUID.randomUUID(), UUID.randomUUID(),
            "https://loja.com/hook", List.of(EventType.values()), "ciphertext", "whsec_ab12", NOW);

    @Test
    void firstFailureStartsTheStreakWithoutDisabling() {
        assertThat(endpoint.recordDeliveryFailure(NOW)).isFalse();

        assertThat(endpoint.getFailingSince()).isEqualTo(NOW);
        assertThat(endpoint.isEnabled()).isTrue();
    }

    @Test
    void isDisabledWhenFailuresLastThreeDays() {
        endpoint.recordDeliveryFailure(NOW);

        assertThat(endpoint.recordDeliveryFailure(NOW.plus(Duration.ofDays(3)).minusSeconds(1))).isFalse();
        assertThat(endpoint.recordDeliveryFailure(NOW.plus(Duration.ofDays(3)))).isTrue();

        assertThat(endpoint.isEnabled()).isFalse();
        assertThat(endpoint.isActive()).isTrue(); // desativado não é removido
        assertThat(endpoint.getDisabledAt()).isEqualTo(NOW.plus(Duration.ofDays(3)));
        assertThat(endpoint.accepts(EventType.PAYMENT_PAID)).isFalse();
    }

    @Test
    void aSuccessEndsTheStreak() {
        endpoint.recordDeliveryFailure(NOW);
        endpoint.recordDeliverySuccess();

        assertThat(endpoint.getFailingSince()).isNull();
        // a contagem recomeça: 3 dias depois da 1ª falha antiga, mas só 1 min depois da nova sequência
        endpoint.recordDeliveryFailure(NOW.plus(Duration.ofDays(3)));
        assertThat(endpoint.recordDeliveryFailure(NOW.plus(Duration.ofDays(3)).plus(Duration.ofMinutes(1)))).isFalse();
    }

    @Test
    void failuresAfterBeingDisabledDoNotDisableAgain() {
        endpoint.recordDeliveryFailure(NOW);
        endpoint.recordDeliveryFailure(NOW.plus(Duration.ofDays(3)));

        assertThat(endpoint.recordDeliveryFailure(NOW.plus(Duration.ofDays(4)))).isFalse();
    }

    @Test
    void enablingRestoresDeliveriesAndRestartsTheStreak() {
        endpoint.recordDeliveryFailure(NOW);
        endpoint.recordDeliveryFailure(NOW.plus(Duration.ofDays(3)));

        assertThat(endpoint.enable()).isTrue();

        assertThat(endpoint.isEnabled()).isTrue();
        assertThat(endpoint.getDisabledAt()).isNull();
        assertThat(endpoint.getFailingSince()).isNull();
        assertThat(endpoint.enable()).isFalse(); // idempotente
    }

    @Test
    void deletedEndpointCannotBeEnabled() {
        endpoint.delete(NOW);

        assertThatThrownBy(endpoint::enable).isInstanceOf(IllegalStateException.class);
        assertThat(endpoint.recordDeliveryFailure(NOW)).isFalse();
    }

    @Test
    void historyRequiresAKeyForMerchantActionsAndNoneForSystemActions() {
        UUID key = UUID.randomUUID();

        assertThat(WebhookEndpointHistory.of(endpoint, WebhookEndpointAction.DISABLED, null, "{}", NOW).getApiKeyId()).isNull();
        assertThat(WebhookEndpointHistory.of(endpoint, WebhookEndpointAction.ENABLED, key, "{}", NOW).getApiKeyId()).isEqualTo(key);
        assertThatThrownBy(() -> WebhookEndpointHistory.of(endpoint, WebhookEndpointAction.DISABLED, key, "{}", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebhookEndpointHistory.of(endpoint, WebhookEndpointAction.CREATED, null, "{}", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
