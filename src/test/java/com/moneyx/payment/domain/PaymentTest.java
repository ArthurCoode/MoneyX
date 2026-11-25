package com.moneyx.payment.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    /** Relógio fixo: os testes controlam o tempo. */
    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    private static Payment pending() {
        return PaymentFixtures.pending(UUID.randomUUID(), 4990, "Pedido 123", "order-123", null, NOW);
    }

    private static Payment creating() {
        return PaymentFixtures.creating(UUID.randomUUID(), 4990, "Pedido 123", "order-123", null, NOW);
    }

    // ---------- criação em duas fases: CREATING -> PENDING (cobrança anexada) ----------

    @Nested
    class PixChargeAttachment {

        private static final Instant LATER = NOW.plusSeconds(2);

        private PixCharge sandboxCharge() {
            return PixCharge.local("sandbox", "TXID0000000000000000000001", "000201...6304ABCD");
        }

        private PixCharge pspCharge(Instant pspExpiresAt) {
            return new PixCharge("sandbox", "mpqrinter190381238073", "000201...6304ABCD", "ORDTST01", "PAY01", pspExpiresAt);
        }

        @Test
        void newPaymentStartsCreatingWithoutCharge() {
            Payment payment = creating();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CREATING);
            assertThat(payment.getStatus().isFinal()).isFalse();
            assertThat(payment.getPixTxid()).isNull();
            assertThat(payment.getPixCopyPaste()).isNull();
            assertThat(payment.getPixProvider()).isEqualTo("sandbox");
            assertThat(payment.requestedExpiration()).isEqualTo(Duration.ofMinutes(30));
        }

        @Test
        void attachingTheChargeMakesThePaymentPendingAndKeepsTheProviderIds() {
            Payment payment = creating();

            payment.attachPixCharge(pspCharge(null), LATER);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getPixTxid()).isEqualTo("mpqrinter190381238073");
            assertThat(payment.getProviderOrderId()).isEqualTo("ORDTST01");
            assertThat(payment.getProviderPaymentId()).isEqualTo("PAY01");
            assertThat(payment.getUpdatedAt()).isEqualTo(LATER);
            assertThat(payment.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));   // PSP não informou
        }

        /** O PSP conta o prazo a partir de quando recebeu o pedido: o vencimento dele passa a valer. */
        @Test
        void providerExpirationReplacesTheRequestedOne() {
            Payment payment = creating();
            Instant pspExpiresAt = NOW.plus(Duration.ofMinutes(30)).plusMillis(1_800);

            payment.attachPixCharge(pspCharge(pspExpiresAt), LATER);

            assertThat(payment.getExpiresAt()).isEqualTo(pspExpiresAt);
        }

        @Test
        void providerExpirationTooFarFromTheRequestedOneIsRejected() {
            Instant requested = NOW.plus(Duration.ofMinutes(30));
            Duration drift = Payment.MAX_PROVIDER_EXPIRATION_DRIFT;

            assertThatThrownBy(() -> creating().attachPixCharge(pspCharge(requested.plus(drift).plusSeconds(1)), LATER))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> creating().attachPixCharge(pspCharge(requested.minus(drift).minusSeconds(1)), LATER))
                    .isInstanceOf(IllegalStateException.class);
            Payment atTheLimit = creating();
            atTheLimit.attachPixCharge(pspCharge(requested.plus(drift)), LATER);
            assertThat(atTheLimit.getExpiresAt()).isEqualTo(requested.plus(drift));
        }

        @Test
        void chargeIsAttachedOnlyOnce() {
            Payment payment = creating();
            payment.attachPixCharge(sandboxCharge(), LATER);

            assertThatThrownBy(() -> payment.attachPixCharge(PixCharge.local("sandbox", "OTHER", "000201..."), LATER))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(payment.getPixTxid()).isEqualTo(sandboxCharge().txid());
        }

        @Test
        void chargeFromAnotherProviderIsRejected() {
            assertThatThrownBy(() -> creating().attachPixCharge(PixCharge.local("mercadopago", "X1", "000201..."), LATER))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void creatingPaymentCannotBePaidCanceledOrExpiredLikeAPendingOne() {
            Payment payment = creating();

            assertThatThrownBy(() -> payment.markPaid(LATER)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> payment.cancel(LATER)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> payment.expire(NOW.plus(Duration.ofHours(1)))).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void creatingPaymentExpiresOnlyAfterItsDeadline() {
            Payment payment = creating();

            assertThatThrownBy(() -> payment.expireBeforeCharge(NOW.plus(Duration.ofMinutes(29))))
                    .isInstanceOf(IllegalStateException.class);
            payment.expireBeforeCharge(NOW.plus(Duration.ofMinutes(30)));

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
            assertThat(payment.getPixTxid()).isNull();
            assertThatThrownBy(() -> payment.attachPixCharge(sandboxCharge(), LATER)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void pendingPaymentCannotExpireBeforeCharge() {
            assertThatThrownBy(() -> pending().expireBeforeCharge(NOW.plus(Duration.ofHours(1))))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void chargeRequiresProviderTxidAndCopyPaste() {
            assertThatThrownBy(() -> PixCharge.local("sandbox", null, "x")).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> PixCharge.local(null, "x", "x")).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> PixCharge.local("sandbox", " ", "x")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> PixCharge.local("sandbox", "x", "")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> PixCharge.local("sandbox", "a-b", "x")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> PixCharge.local("sandbox", "a".repeat(36), "x")).isInstanceOf(IllegalArgumentException.class);
            assertThat(PixCharge.local("sandbox", "a".repeat(35), "x").txid()).hasSize(35);
        }
    }

    @Nested
    class Creation {

        @Test
        void startsPendingWithDefaultExpirationOf30Minutes() {
            Payment payment = pending();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getAmount()).isEqualTo(4990);
            assertThat(payment.getCurrency()).isEqualTo(PaymentCurrency.BRL);
            assertThat(payment.getCreatedAt()).isEqualTo(NOW);
            assertThat(payment.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
            assertThat(payment.getPaidAt()).isNull();
            assertThat(payment.getCanceledAt()).isNull();
        }

        @Test
        void acceptsAmountsAtTheLimits() {
            assertThat(create(Payment.MIN_AMOUNT).getAmount()).isEqualTo(100);         // R$ 1,00
            assertThat(create(Payment.MAX_AMOUNT).getAmount()).isEqualTo(10_000_000);  // R$ 100.000,00
        }

        @ParameterizedTest
        @ValueSource(longs = {-1, 0, 99, 10_000_001})
        void rejectsAmountsOutsideTheLimits(long amount) {
            assertThatThrownBy(() -> create(amount))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("amount");
        }

        @Test
        void acceptsExpirationAtTheLimits() {
            assertThat(createExpiringIn(Duration.ofMinutes(5)).getExpiresAt()).isEqualTo(NOW.plusSeconds(300));
            assertThat(createExpiringIn(Duration.ofHours(24)).getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        }

        @Test
        void rejectsExpirationOutsideTheLimits() {
            assertThatThrownBy(() -> createExpiringIn(Duration.ofMinutes(4)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> createExpiringIn(Duration.ofHours(24).plusSeconds(1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void optionalTextsAreTrimmedAndBlankBecomesNull() {
            Payment payment = PaymentFixtures.creating(UUID.randomUUID(), 4990, "  Pedido 123  ", "   ", null, NOW);

            assertThat(payment.getDescription()).isEqualTo("Pedido 123");
            assertThat(payment.getExternalReference()).isNull();
        }

        @Test
        void rejectsTextsLongerThanTheColumn() {
            assertThatThrownBy(() -> PaymentFixtures.creating(UUID.randomUUID(), 4990, "x".repeat(256), null, null, NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> PaymentFixtures.creating(UUID.randomUUID(), 4990, null, "x".repeat(65), null, NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        private Payment create(long amount) {
            return PaymentFixtures.creating(UUID.randomUUID(), amount, null, null, null, NOW);
        }

        private Payment createExpiringIn(Duration expiresIn) {
            return PaymentFixtures.creating(UUID.randomUUID(), 4990, null, null, expiresIn, NOW);
        }
    }

    // ---------- transições permitidas ----------

    @Nested
    class AllowedTransitions {

        @Test
        void pendingToPaidBeforeExpiration() {
            Payment payment = pending();
            Instant paidAt = NOW.plus(Duration.ofMinutes(10));

            payment.markPaid(paidAt);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(payment.getPaidAt()).isEqualTo(paidAt);
            assertThat(payment.getUpdatedAt()).isEqualTo(paidAt);
        }

        @Test
        void pendingToCanceled() {
            Payment payment = pending();
            Instant canceledAt = NOW.plusSeconds(60);

            payment.cancel(canceledAt);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
            assertThat(payment.getCanceledAt()).isEqualTo(canceledAt);
        }

        @Test
        void pendingToExpiredAfterTheDeadline() {
            Payment payment = pending();

            payment.expire(payment.getExpiresAt());

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        }
    }

    // ---------- regras de tempo ----------

    @Nested
    class TimeRules {

        @Test
        void cannotBePaidAfterTheDeadlineEvenIfStillPending() {
            Payment payment = pending();

            assertThatThrownBy(() -> payment.markPaid(payment.getExpiresAt()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("expired");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        void cannotBeExpiredBeforeTheDeadline() {
            Payment payment = pending();

            assertThatThrownBy(() -> payment.expire(payment.getExpiresAt().minusSeconds(1)))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }
    }

    // ---------- estados finais: nenhuma transição de saída ----------

    @Nested
    class FinalStates {

        private static final Instant LATER = NOW.plus(Duration.ofHours(48));

        private static Payment paid() {
            Payment payment = pending();
            payment.markPaid(NOW.plusSeconds(60));
            return payment;
        }

        private static Payment canceled() {
            Payment payment = pending();
            payment.cancel(NOW.plusSeconds(60));
            return payment;
        }

        private static Payment expired() {
            Payment payment = pending();
            payment.expire(payment.getExpiresAt());
            return payment;
        }

        @Test
        void paidIsFinal() {
            assertNoTransitionLeaves(PaymentTest.FinalStates::paid, PaymentStatus.PAID);
        }

        @Test
        void canceledIsFinal() {
            assertNoTransitionLeaves(PaymentTest.FinalStates::canceled, PaymentStatus.CANCELED);
        }

        @Test
        void expiredIsFinal() {
            assertNoTransitionLeaves(PaymentTest.FinalStates::expired, PaymentStatus.EXPIRED);
        }

        @Test
        void onlyPendingIsNotFinal() {
            assertThat(PaymentStatus.PENDING.isFinal()).isFalse();
            assertThat(PaymentStatus.PAID.isFinal()).isTrue();
            assertThat(PaymentStatus.CANCELED.isFinal()).isTrue();
            assertThat(PaymentStatus.EXPIRED.isFinal()).isTrue();
        }

        private void assertNoTransitionLeaves(Supplier<Payment> factory, PaymentStatus finalStatus) {
            for (Consumer<Payment> transition : List.<Consumer<Payment>>of(
                    p -> p.markPaid(LATER), p -> p.cancel(LATER), p -> p.expire(LATER))) {
                Payment payment = factory.get();
                assertThatThrownBy(() -> transition.accept(payment))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("Only PENDING");
                assertThat(payment.getStatus()).isEqualTo(finalStatus);
            }
        }
    }
}
