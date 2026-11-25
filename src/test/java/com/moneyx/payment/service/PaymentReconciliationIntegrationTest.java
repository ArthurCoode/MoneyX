package com.moneyx.payment.service;

import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.repository.MerchantRepository;
import com.moneyx.payment.domain.Payment;
import com.moneyx.payment.domain.PaymentCurrency;
import com.moneyx.payment.domain.PaymentStatus;
import com.moneyx.payment.exception.PaymentExpiredException;
import com.moneyx.payment.exception.PaymentNotPendingException;
import com.moneyx.payment.exception.PaymentProviderUnavailableException;
import com.moneyx.payment.provider.PixProvider;
import com.moneyx.payment.provider.PixProviderException;
import com.moneyx.payment.provider.ProviderChargeStatus.State;
import com.moneyx.payment.repository.PaymentRepository;
import com.moneyx.support.MutableClock;
import com.moneyx.support.TestClockConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Conciliação com um PSP externo (Etapa 7d) contra o PostgreSQL REAL, com um PSP falso controlável
 * (FakeRemotePixProvider). Regras de dinheiro provadas aqui:
 * - quem diz se o Pix foi pago é o PSP; pago no PSP → PAID (mesmo depois do prazo);
 * - nunca EXPIRED/CANCELED sem o PSP confirmar que a order foi encerrada;
 * - valor/referência divergentes nunca viram PAID;
 * - o corpo da criação é gravado e reenviado igual (mesmo que a configuração mude).
 */
@SpringBootTest
@Import({TestClockConfig.class, PaymentReconciliationIntegrationTest.RemoteProviderConfig.class})
class PaymentReconciliationIntegrationTest {

    private static final Instant START = Instant.parse("2026-11-25T12:00:00Z");
    private static final CreatePaymentCommand ORDER =
            new CreatePaymentCommand(4990, PaymentCurrency.BRL, "Pedido", "order-1", null);

    @TestConfiguration
    static class RemoteProviderConfig {
        @Bean
        @Primary
        FakeRemotePixProvider fakeRemotePixProvider() {
            return new FakeRemotePixProvider();
        }
    }

    @Autowired
    private PaymentService service;
    @Autowired
    private PaymentMaintenanceService maintenance;
    @Autowired
    private FakeRemotePixProvider psp;
    @Autowired
    private PixProvider activeProvider;
    @Autowired
    private PaymentRepository payments;
    @Autowired
    private MerchantRepository merchantRepository;
    @Autowired
    private MutableClock clock;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MeterRegistry meters;

    private final List<UUID> createdMerchants = new ArrayList<>();
    private UUID merchantId;

    @BeforeEach
    void setUp() {
        psp.reset();
        clock.set(START);
        merchantId = newMerchant();
    }

    @AfterEach
    void cleanUp() {
        for (UUID id : createdMerchants) {
            jdbc.update("DELETE FROM events WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM payment_idempotency_keys WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM payments WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM merchants WHERE id = ?", id);
        }
    }

    @Test
    void theRemoteFakeIsTheActiveProvider() {
        assertThat(activeProvider.name()).isEqualTo("mercadopago");
    }

    // ---------- criação: corpo gravado ----------

    @Test
    void theRequestPreparedAtCreationIsStoredAndResentUnchangedEvenAfterAConfigurationChange() {
        psp.failure.set(temporary());
        assertThatThrownBy(() -> service.create(merchantId, "k1", ORDER))
                .isInstanceOf(PaymentProviderUnavailableException.class);
        Payment creating = onlyPayment();
        assertThat(creating.getProviderRequest()).contains("\"config\":\"v1\"");

        psp.failure.set(null);
        psp.config.set("v2"); // a configuração mudou entre a tentativa e a repetição
        Payment payment = service.create(merchantId, "k1", ORDER).payment();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(psp.sentRequests).hasSize(2).allMatch(sent -> sent.equals(creating.getProviderRequest()));
    }

    // ---------- conciliação no prazo ----------

    @Test
    void paidAtTheProviderBecomesPaidWithOnePaidEvent() {
        Payment payment = pending();
        psp.orderOf(payment.getId()).state = State.PAID;

        assertThat(service.reconcileProviderCharges(20)).isEqualTo(1);
        assertThat(service.reconcileProviderCharges(20)).isZero();

        Payment paid = reload(payment);
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.getPaidAt()).isEqualTo(START);
        assertThat(paid.getProviderCheckedAt()).isEqualTo(START);
        assertThat(events(payment, "payment.paid")).isEqualTo(1);
    }

    @Test
    void openAtTheProviderStaysPendingAndRecordsTheCheck() {
        Payment payment = pending();
        clock.advance(Duration.ofSeconds(30));

        assertThat(service.reconcileProviderCharges(20)).isZero();

        Payment reloaded = reload(payment);
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(reloaded.getProviderCheckedAt()).isEqualTo(START.plusSeconds(30));
        assertThat(psp.calls).containsExactly("create", "fetch");
    }

    @Test
    void inProgressAndUnknownAreNeverDecided() {
        Payment a = pending();
        Payment b = pendingFor(newMerchant());
        psp.orderOf(a.getId()).state = State.IN_PROGRESS;
        psp.orderOf(b.getId()).state = State.UNKNOWN;

        assertThat(service.reconcileProviderCharges(20)).isZero();

        assertThat(reload(a).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(reload(b).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void aDifferentAmountAtTheProviderIsNeverPaid() {
        Payment payment = pending();
        psp.orderOf(payment.getId()).state = State.PAID;
        psp.orderOf(payment.getId()).amount = 4989;
        double before = mismatches();

        service.reconcileProviderCharges(20);

        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(events(payment, "payment.paid")).isZero();
        assertThat(mismatches()).isEqualTo(before + 1);
    }

    @Test
    void closedAtTheProviderBeforeTheDeadlineBecomesCanceled() {
        Payment payment = pending();
        psp.orderOf(payment.getId()).state = State.CLOSED; // cancelada no painel do PSP

        service.reconcileProviderCharges(20);

        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(events(payment, "payment.canceled")).isEqualTo(1);
    }

    @Test
    void expiredByTheProviderBeforeTheDeadlineBecomesExpiredNotCanceled() {
        // visto no sandbox do MP: order de 5 min expirada lá ~5 s depois de criada
        Payment payment = pending();
        psp.orderOf(payment.getId()).state = State.EXPIRED;

        service.reconcileProviderCharges(20);

        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(events(payment, "payment.expired")).isEqualTo(1);
        assertThat(events(payment, "payment.canceled")).isZero();
    }

    @Test
    void cancelOfAnOrderTheProviderAlreadyExpiredAnswersNotPendingExpired() {
        Payment payment = pending();
        psp.orderOf(payment.getId()).state = State.EXPIRED;

        assertThatThrownBy(() -> service.cancel(merchantId, payment.getId()))
                .isInstanceOfSatisfying(PaymentNotPendingException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(PaymentStatus.EXPIRED));
    }

    @Test
    void whenTheProviderIsDownNothingChangesAndTheRoundStops() {
        Payment a = pending();
        pendingFor(newMerchant());
        psp.orderOf(a.getId()).state = State.PAID;
        psp.calls.clear();
        psp.failure.set(temporary());

        assertThat(service.reconcileProviderCharges(20)).isZero();

        assertThat(reload(a).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(psp.calls).hasSize(1); // parou no primeiro erro temporário
    }

    // ---------- vencidos: cancelar no PSP ANTES de expirar ----------

    @Test
    void anOverduePaymentIsCanceledAtTheProviderAndThenExpired() {
        Payment payment = pending();
        clock.set(payment.getExpiresAt());

        assertThat(service.reconcileProviderCharges(20)).isEqualTo(1);

        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(psp.orderOf(payment.getId()).state).isEqualTo(State.CLOSED);
        assertThat(psp.calls).containsExactly("create", "cancel");
        assertThat(events(payment, "payment.expired")).isEqualTo(1);
    }

    @Test
    void anOverduePaymentPaidInTheLastSecondBecomesPaidNeverExpired() {
        Payment payment = pending();
        psp.orderOf(payment.getId()).state = State.PAID;
        clock.set(payment.getExpiresAt().plusSeconds(90));

        service.reconcileProviderCharges(20);

        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(events(payment, "payment.expired")).isZero();
        assertThat(events(payment, "payment.paid")).isEqualTo(1);
    }

    @Test
    void anOverduePaymentStaysPendingWhileTheProviderIsDown() {
        Payment payment = pending();
        clock.set(payment.getExpiresAt().plusSeconds(60));
        psp.failure.set(temporary());

        service.reconcileProviderCharges(20);
        maintenance.expireOverduePayments(); // a expiração local não toca em cobranças do PSP

        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    // ---------- cancelamento pelo comerciante ----------

    @Test
    void cancelClosesTheOrderAtTheProviderFirst() {
        Payment payment = pending();

        Payment canceled = service.cancel(merchantId, payment.getId());

        assertThat(canceled.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(psp.orderOf(payment.getId()).state).isEqualTo(State.CLOSED);
        assertThat(service.cancel(merchantId, payment.getId()).getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(events(payment, "payment.canceled")).isEqualTo(1);
    }

    @Test
    void cancelOfAPaymentPaidAtTheProviderMarksItPaidAndAnswersNotPending() {
        Payment payment = pending();
        psp.orderOf(payment.getId()).state = State.PAID;

        assertThatThrownBy(() -> service.cancel(merchantId, payment.getId()))
                .isInstanceOfSatisfying(PaymentNotPendingException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(PaymentStatus.PAID));

        // o PAID foi gravado mesmo com a resposta de erro (não pode se perder num rollback)
        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(events(payment, "payment.paid")).isEqualTo(1);
    }

    @Test
    void cancelWhileTheProviderIsDownAnswers503AndChangesNothing() {
        Payment payment = pending();
        psp.failure.set(temporary());

        assertThatThrownBy(() -> service.cancel(merchantId, payment.getId()))
                .isInstanceOf(PaymentProviderUnavailableException.class);

        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void cancelWhileTheProviderIsProcessingAPaymentAnswers503() {
        Payment payment = pending();
        psp.orderOf(payment.getId()).state = State.IN_PROGRESS;

        assertThatThrownBy(() -> service.cancel(merchantId, payment.getId()))
                .isInstanceOf(PaymentProviderUnavailableException.class);
        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void cancelOfAnOverduePaymentStillAnswersExpiredWithoutCallingTheProvider() {
        Payment payment = pending();
        clock.set(payment.getExpiresAt());
        psp.calls.clear();

        assertThatThrownBy(() -> service.cancel(merchantId, payment.getId()))
                .isInstanceOf(PaymentExpiredException.class);
        assertThat(psp.calls).isEmpty();
    }

    // ---------- CREATING cuja order existe ----------

    @Test
    void aCreatingPaymentWhoseOrderWasAlreadyPaidBecomesPaidNeverExpired() {
        psp.createWithoutQrCode = true;
        assertThatThrownBy(() -> service.create(merchantId, "k1", ORDER))
                .isInstanceOf(PaymentProviderUnavailableException.class);
        Payment creating = onlyPayment();
        assertThat(creating.getStatus()).isEqualTo(PaymentStatus.CREATING);

        psp.orderOf(creating.getId()).state = State.PAID; // o pagador de teste aprovou
        clock.set(creating.getExpiresAt().plusSeconds(60)); // e o prazo do MoneyX já passou
        service.resumeStuckCreations(Duration.ofSeconds(30), 20);

        Payment paid = reload(creating);
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.getProviderOrderId()).isEqualTo(psp.orderOf(creating.getId()).id);
        assertThat(paid.getPixTxid()).isNull();
        assertThat(events(creating, "payment.paid")).isEqualTo(1);
    }

    @Test
    void aCreatingPaymentPastTheDeadlineHasItsOrderCanceledBeforeExpiring() {
        psp.failure.set(temporary());
        assertThatThrownBy(() -> service.create(merchantId, "k1", ORDER))
                .isInstanceOf(PaymentProviderUnavailableException.class);
        Payment creating = onlyPayment();
        psp.failure.set(null);
        clock.set(creating.getExpiresAt().plusSeconds(60));

        service.resumeStuckCreations(Duration.ofSeconds(30), 20);

        assertThat(reload(creating).getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(psp.orderOf(creating.getId()).state).isEqualTo(State.CLOSED);
        assertThat(psp.calls).endsWith("create", "cancel");
    }

    @Test
    void aCreatingPaymentPastTheDeadlineStaysCreatingWhileTheProviderIsDown() {
        psp.failure.set(temporary());
        assertThatThrownBy(() -> service.create(merchantId, "k1", ORDER))
                .isInstanceOf(PaymentProviderUnavailableException.class);
        Payment creating = onlyPayment();
        clock.set(creating.getExpiresAt().plusSeconds(60));

        service.resumeStuckCreations(Duration.ofSeconds(30), 20);

        assertThat(reload(creating).getStatus()).isEqualTo(PaymentStatus.CREATING);
    }

    // ---------- aviso do PSP (webhook) ----------

    @Test
    void aProviderNoticeReconcilesTheOrderNow() {
        Payment payment = pending();
        psp.orderOf(payment.getId()).state = State.PAID;

        assertThat(service.reconcileProviderOrder("ORDUNKNOWN")).isFalse();
        assertThat(service.reconcileProviderOrder(payment.getProviderOrderId())).isTrue();

        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    // ---------- concorrência ----------

    @RepeatedTest(5)
    void concurrentReconciliationsAndCancelsProduceExactlyOneFinalTransition() throws Exception {
        Payment payment = pending();
        psp.orderOf(payment.getId()).state = State.PAID;

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            boolean cancel = i % 2 == 0;
            futures.add(pool.submit(() -> {
                go.await();
                if (cancel) {
                    try {
                        service.cancel(merchantId, payment.getId());
                    } catch (PaymentNotPendingException expected) {
                        // já PAID
                    }
                } else {
                    service.reconcileProviderOrder(payment.getProviderOrderId());
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(events(payment, "payment.paid")).isEqualTo(1);
        assertThat(events(payment, "payment.canceled")).isZero();
    }

    // ---------- apoio ----------

    private Payment pending() {
        return pendingFor(merchantId);
    }

    private Payment pendingFor(UUID merchant) {
        Payment payment = service.create(merchant, "k-" + UUID.randomUUID(), ORDER).payment();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getProviderOrderId()).isNotNull();
        return payment;
    }

    private Payment onlyPayment() {
        return payments.findAll().stream().filter(p -> p.getMerchantId().equals(merchantId)).findFirst().orElseThrow();
    }

    private Payment reload(Payment payment) {
        return payments.findById(payment.getId()).orElseThrow();
    }

    private int events(Payment payment, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM events WHERE resource_id = ? AND type = ?",
                Integer.class, payment.getId(), type);
    }

    private double mismatches() {
        var counter = meters.find("moneyx.payments.reconciliation.mismatches").counter();
        return counter == null ? 0 : counter.count();
    }

    private static PixProviderException temporary() {
        return new PixProviderException(true, "timeout", Duration.ofSeconds(5), "simulated timeout", null);
    }

    private UUID newMerchant() {
        String document = String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
        Merchant merchant = merchantRepository.saveAndFlush(
                new Merchant("Reconciliation Test LTDA", document, "reconciliation-" + UUID.randomUUID() + "@moneyx.dev"));
        createdMerchants.add(merchant.getId());
        return merchant.getId();
    }
}
