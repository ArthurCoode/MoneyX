package com.moneyx.payment.service;

import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.repository.MerchantRepository;
import com.moneyx.payment.domain.Payment;
import com.moneyx.payment.domain.PaymentCurrency;
import com.moneyx.payment.domain.PaymentStatus;
import com.moneyx.payment.exception.PaymentExpiredException;
import com.moneyx.payment.exception.PaymentNotFoundException;
import com.moneyx.payment.exception.PaymentNotPendingException;
import com.moneyx.payment.repository.PaymentRepository;
import com.moneyx.support.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Simulação de pagamento Pix (sandbox) contra o PostgreSQL REAL, com relógio controlado.
 * Não é @Transactional: cada chamada tem sua própria transação, como requisições HTTP reais.
 */
@SpringBootTest
class PaymentSandboxIntegrationTest {

    private static final Instant START = Instant.parse("2026-11-19T12:00:00Z");

    @TestConfiguration
    static class ClockTestConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }
    }

    private static final CreatePaymentCommand ORDER =
            new CreatePaymentCommand(4990, PaymentCurrency.BRL, "Pedido 123", "order-123", null);

    @Autowired
    private PaymentService service;

    @Autowired
    private MutableClock clock;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> createdMerchants = new ArrayList<>();
    private UUID merchantId;

    @BeforeEach
    void setUp() {
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

    private Payment createPayment() {
        return service.create(merchantId, UUID.randomUUID().toString(), ORDER).payment();
    }

    private Payment reload(Payment payment) {
        return paymentRepository.findById(payment.getId()).orElseThrow();
    }

    @Test
    void createdPaymentHasAPersistedPixCharge() {
        Payment payment = reload(createPayment());

        assertThat(payment.getPixTxid()).matches("^[A-Z0-9]{25}$");
        assertThat(payment.getPixCopyPaste()).contains("540549.90").contains("0525" + payment.getPixTxid());
    }

    @Test
    void payingMarksThePaymentAsPaidAtTheCurrentTime() {
        Payment payment = createPayment();
        clock.advance(Duration.ofMinutes(10));

        Payment paid = service.simulatePixPayment(merchantId, payment.getId());

        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(reload(payment).getPaidAt()).isEqualTo(START.plus(Duration.ofMinutes(10)));
    }

    @Test
    void cannotPayTwice() {
        Payment payment = createPayment();
        service.simulatePixPayment(merchantId, payment.getId());

        assertThatThrownBy(() -> service.simulatePixPayment(merchantId, payment.getId()))
                .isInstanceOf(PaymentNotPendingException.class)
                .extracting(e -> ((PaymentNotPendingException) e).getStatus())
                .isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void cannotPayAfterTheDeadlineEvenIfStatusIsStillPending() {
        Payment payment = createPayment();
        clock.advance(Duration.ofMinutes(30)); // prazo padrão: exatamente vencido

        assertThatThrownBy(() -> service.simulatePixPayment(merchantId, payment.getId()))
                .isInstanceOf(PaymentExpiredException.class);
        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void canPayOneSecondBeforeTheDeadline() {
        Payment payment = createPayment();
        clock.advance(Duration.ofMinutes(30).minusSeconds(1));

        assertThat(service.simulatePixPayment(merchantId, payment.getId()).getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void merchantCannotPayAnotherMerchantsPayment() {
        Payment payment = createPayment();
        UUID otherMerchant = newMerchant();

        assertThatThrownBy(() -> service.simulatePixPayment(otherMerchant, payment.getId()))
                .isInstanceOf(PaymentNotFoundException.class);
        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void unknownPaymentIsNotFound() {
        assertThatThrownBy(() -> service.simulatePixPayment(merchantId, UUID.randomUUID()))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void concurrentPaymentsOfTheSamePixSucceedExactlyOnce() throws Exception {
        Payment payment = createPayment();
        int threads = 10;

        List<Object> results = runConcurrently(threads, () -> service.simulatePixPayment(merchantId, payment.getId()));

        assertThat(results).filteredOn(r -> r instanceof Payment).hasSize(1);
        assertThat(results).filteredOn(r -> r instanceof PaymentNotPendingException).hasSize(threads - 1);
        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    // ---------- auxiliares ----------

    private UUID newMerchant() {
        String document = String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
        Merchant merchant = merchantRepository.saveAndFlush(
                new Merchant("Sandbox Test LTDA", document, "sandbox-" + UUID.randomUUID() + "@moneyx.dev"));
        createdMerchants.add(merchant.getId());
        return merchant.getId();
    }

    private static List<Object> runConcurrently(int threads, Callable<Object> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return task.call();
                    } catch (RuntimeException e) {
                        return e;
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
