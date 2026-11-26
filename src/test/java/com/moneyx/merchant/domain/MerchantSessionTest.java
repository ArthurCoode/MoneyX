package com.moneyx.merchant.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regras de tempo da sessão e da senha, sem banco. */
class MerchantSessionTest {

    private static final Instant T0 = Instant.parse("2026-11-26T12:00:00Z");

    @Test
    void validUntilIsTheEarlierOfIdleAndAbsoluteLimits() {
        MerchantSession session = MerchantSession.start(UUID.randomUUID(), T0).session();
        assertThat(session.validUntil()).isEqualTo(T0.plus(Duration.ofHours(6)));
        session.touch(T0.plus(Duration.ofHours(20)));
        assertThat(session.validUntil()).isEqualTo(T0.plus(Duration.ofHours(24)));
    }

    @Test
    void theIdleLimitIsExclusive() {
        MerchantSession session = MerchantSession.start(UUID.randomUUID(), T0).session();
        assertThat(session.isActive(T0.plus(Duration.ofHours(6)).minusNanos(1000))).isTrue();
        assertThat(session.isActive(T0.plus(Duration.ofHours(6)))).isFalse();
    }

    @Test
    void revokingIsImmediateAndIdempotent() {
        MerchantSession session = MerchantSession.start(UUID.randomUUID(), T0).session();
        session.revoke(T0.plusSeconds(5));
        session.revoke(T0.plusSeconds(9));
        assertThat(session.isActive(T0.plusSeconds(6))).isFalse();
        assertThat(session.getRevokedAt()).isEqualTo(T0.plusSeconds(5));
    }

    @Test
    void tokensAreRandomPrefixedAndNeverPrinted() {
        MerchantSession.Started a = MerchantSession.start(UUID.randomUUID(), T0);
        MerchantSession.Started b = MerchantSession.start(UUID.randomUUID(), T0);
        assertThat(a.token()).startsWith("mx_sess_").hasSize("mx_sess_".length() + 43).isNotEqualTo(b.token());
        assertThat(a.toString()).doesNotContain(a.token());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "onze-chars!"})
    void shortOrBlankPasswordsAreRejected(String password) {
        assertThatThrownBy(() -> PasswordPolicy.validate(password, "a@b.com")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void passwordLimitsCountCharactersNotBytes() {
        assertThatCode(() -> PasswordPolicy.validate("çãõéíúâêôàü!", "a@b.com")).doesNotThrowAnyException(); // 12 caracteres
        assertThatCode(() -> PasswordPolicy.validate("x".repeat(128), "a@b.com")).doesNotThrowAnyException();
        assertThatThrownBy(() -> PasswordPolicy.validate("x".repeat(129), "a@b.com")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thePasswordCannotBeTheEmail() {
        assertThatThrownBy(() -> PasswordPolicy.validate(" Loja@Exemplo.com ", "loja@exemplo.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
