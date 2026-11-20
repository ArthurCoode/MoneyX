package com.moneyx.payment.service;

import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.repository.MerchantRepository;
import com.moneyx.payment.domain.Payment;
import com.moneyx.payment.domain.PaymentCurrency;
import com.moneyx.payment.domain.PaymentStatus;
import com.moneyx.payment.exception.PaymentNotPendingException;
import com.moneyx.payment.repository.PaymentRepository;
import com.moneyx.support.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
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
 * Jobs de manutenção (expiração e limpeza) contra o PostgreSQL REAL, com relógio controlado.
 *
 * O relógio começa em 2020 DE PROPÓSITO: os testes usam o banco de desenvolvimento e o job atua em
 * QUALQUER pagamento vencido. Com "agora" em 2020, dados reais (criados em 2026) nunca estão vencidos
 * para o teste e não são tocados.
 */
@SpringBootTest
class PaymentMaintenanceIntegrationTest {

    private static final Instant START = Instant.parse("2020-01-01T12:00:00Z");
    private static final Duration DEFAULT_EXPIRATION = Duration.ofMinutes(30);

    @TestConfiguration
    static class ClockTestConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }
    }

    @Autowired
    private PaymentMaintenanceService maintenance;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private MutableClock clock;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationContext context;

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

    @Test
    void scheduledJobsAreDisabledInTests() {
        assertThat(context.getBeanNamesForType(com.moneyx.payment.job.PaymentMaintenanceJobs.class)).isEmpty();
    }

    // ---------- expiração ----------

    @Test
    void expiresOnlyPendingPaymentsWhoseDeadlinePassed() {
        Payment overdue = createPayment();
        Payment paid = createPayment();
        Payment canceled = createPayment();
        paymentService.simulatePixPayment(merchantId, paid.getId());
        paymentService.cancel(merchantId, canceled.getId());
        clock.advance(Duration.ofMinutes(10));
        Payment stillInTime = createPayment(); // vence 10 min depois dos outros

        clock.advance(DEFAULT_EXPIRATION.minusMinutes(10)); // os 3 primeiros vencidos; o último ainda não
        Instant now = clock.instant();

        assertThat(maintenance.expireOverduePayments()).isEqualTo(1);

        Payment expired = reload(overdue);
        assertThat(expired.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(expired.getUpdatedAt()).isEqualTo(now);
        assertThat(reload(paid).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(reload(canceled).getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(reload(stillInTime).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void deadlineIsInclusiveExactlyLikeThePayRule() {
        Payment payment = createPayment();

        clock.advance(DEFAULT_EXPIRATION.minusSeconds(1));
        assertThat(maintenance.expireOverduePayments()).isZero();
        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.PENDING);

        clock.advance(Duration.ofSeconds(1)); // exatamente no prazo: já não pode pagar, então expira
        assertThat(maintenance.expireOverduePayments()).isEqualTo(1);
        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.EXPIRED);
    }

    @Test
    void runningAgainDoesNothing() {
        createPayment();
        clock.advance(DEFAULT_EXPIRATION);

        assertThat(maintenance.expireOverduePayments()).isEqualTo(1);
        assertThat(maintenance.expireOverduePayments()).isZero();
    }

    @Test
    void processesMoreThanOneBatchInASingleRun() {
        int count = PaymentMaintenanceService.EXPIRATION_BATCH_SIZE * 2 + 50;
        for (int i = 0; i < count; i++) {
            createPayment();
        }
        clock.advance(DEFAULT_EXPIRATION);

        assertThat(maintenance.expireOverduePayments()).isEqualTo(count);
        assertThat(countByStatus(PaymentStatus.PENDING)).isZero();
        assertThat(countByStatus(PaymentStatus.EXPIRED)).isEqualTo(count);
    }

    @Test
    void expiredPaymentCanNoLongerBePaidOrCanceledAndIsListedAsExpired() {
        Payment payment = createPayment();
        clock.advance(DEFAULT_EXPIRATION);
        maintenance.expireOverduePayments();

        assertThatThrownBy(() -> paymentService.simulatePixPayment(merchantId, payment.getId()))
                .isInstanceOf(PaymentNotPendingException.class)
                .extracting(e -> ((PaymentNotPendingException) e).getStatus())
                .isEqualTo(PaymentStatus.EXPIRED);
        assertThatThrownBy(() -> paymentService.cancel(merchantId, payment.getId()))
                .isInstanceOf(PaymentNotPendingException.class);
        assertThat(paymentService.list(merchantId, new PaymentListQuery(null, null, PaymentStatus.EXPIRED, null))
                .payments()).extracting(Payment::getId).containsExactly(payment.getId());
    }

    /**
     * SKIP LOCKED: um pagamento trancado por outra transação (ex.: um "pagar" em andamento) é PULADO,
     * o job não fica esperando, e o pagamento é expirado numa execução seguinte.
     */
    @Test
    void skipsPaymentsLockedByAnotherTransactionInsteadOfWaiting() throws Exception {
        Payment locked = createPayment();
        Payment free = createPayment();
        clock.advance(DEFAULT_EXPIRATION);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<?> holder = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
                paymentRepository.lockByIdAndMerchantId(locked.getId(), merchantId).orElseThrow();
                lockAcquired.countDown();
                await(release);
            }));
            assertThat(lockAcquired.await(10, TimeUnit.SECONDS)).isTrue();

            // Se o job esperasse pelo lock, este get estouraria o tempo.
            Future<Integer> job = executor.submit(maintenance::expireOverduePayments);
            assertThat(job.get(5, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(reload(free).getStatus()).isEqualTo(PaymentStatus.EXPIRED);
            assertThat(reload(locked).getStatus()).isEqualTo(PaymentStatus.PENDING);

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }

        assertThat(maintenance.expireOverduePayments()).isEqualTo(1);
        assertThat(reload(locked).getStatus()).isEqualTo(PaymentStatus.EXPIRED);
    }

    /** Duas execuções simultâneas (ex.: duas instâncias da aplicação) dividem o trabalho sem repetir nem falhar. */
    @Test
    void concurrentRunsExpireEachPaymentExactlyOnce() throws Exception {
        int count = 150;
        for (int i = 0; i < count; i++) {
            createPayment();
        }
        clock.advance(DEFAULT_EXPIRATION);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> { await(start); return maintenance.expireOverduePayments(); });
            Future<Integer> second = executor.submit(() -> { await(start); return maintenance.expireOverduePayments(); });
            start.countDown();

            assertThat(first.get(30, TimeUnit.SECONDS) + second.get(30, TimeUnit.SECONDS)).isEqualTo(count);
        } finally {
            executor.shutdownNow();
        }
        assertThat(countByStatus(PaymentStatus.EXPIRED)).isEqualTo(count);
    }

    // ---------- limpeza das chaves de idempotência ----------

    @Test
    void purgesOnlyExpiredIdempotencyKeysAndKeepsThePayments() {
        Payment old = createPayment();
        clock.advance(Duration.ofHours(1));
        Payment recent = createPayment();

        clock.advance(Duration.ofHours(23)); // chave do primeiro: exatamente 24 h (vencida); do segundo: 23 h

        assertThat(maintenance.purgeExpiredIdempotencyKeys()).isEqualTo(1);
        assertThat(keyCountForPayment(old)).isZero();
        assertThat(keyCountForPayment(recent)).isEqualTo(1);
        assertThat(paymentRepository.findById(old.getId())).isPresent();
    }

    @Test
    void keyCanBeReusedAfterBeingPurged() {
        CreatePaymentCommand command = new CreatePaymentCommand(4990, PaymentCurrency.BRL, null, null, null);
        Payment first = paymentService.create(merchantId, "reused-key", command).payment();
        clock.advance(Duration.ofHours(24));
        maintenance.purgeExpiredIdempotencyKeys();

        PaymentCreation second = paymentService.create(merchantId, "reused-key", command);

        assertThat(second.replayed()).isFalse();
        assertThat(second.payment().getId()).isNotEqualTo(first.getId());
    }

    @Test
    void purgeProcessesMoreThanOneBatchInASingleRun() {
        Payment payment = createPayment();
        int count = PaymentMaintenanceService.IDEMPOTENCY_PURGE_BATCH_SIZE * 2 + 500;
        // Inserção direta (rápida): várias chaves vencidas apontando para o mesmo pagamento.
        Timestamp created = Timestamp.from(START.minus(Duration.ofDays(2)));
        Timestamp expires = Timestamp.from(START.minus(Duration.ofDays(1)));
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(new Object[]{UUID.randomUUID(), merchantId, "bulk-" + i, "0".repeat(64), payment.getId(), created, expires});
        }
        jdbc.batchUpdate("""
                INSERT INTO payment_idempotency_keys
                    (id, merchant_id, idempotency_key, request_fingerprint, payment_id, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, rows);

        assertThat(maintenance.purgeExpiredIdempotencyKeys()).isEqualTo(count);
        assertThat(keyCountForPayment(payment)).isEqualTo(1); // a chave da criação, ainda válida
    }

    // ---------- auxiliares ----------

    private Payment createPayment() {
        CreatePaymentCommand command = new CreatePaymentCommand(4990, PaymentCurrency.BRL, null, null, null);
        return paymentService.create(merchantId, UUID.randomUUID().toString(), command).payment();
    }

    private Payment reload(Payment payment) {
        return paymentRepository.findById(payment.getId()).orElseThrow();
    }

    private int countByStatus(PaymentStatus status) {
        return jdbc.queryForObject("SELECT count(*) FROM payments WHERE merchant_id = ? AND status = ?",
                Integer.class, merchantId, status.name());
    }

    private int keyCountForPayment(Payment payment) {
        return jdbc.queryForObject("SELECT count(*) FROM payment_idempotency_keys WHERE payment_id = ?",
                Integer.class, payment.getId());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private UUID newMerchant() {
        String document = String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
        Merchant merchant = merchantRepository.saveAndFlush(
                new Merchant("Maintenance Test LTDA", document, "maintenance-" + UUID.randomUUID() + "@moneyx.dev"));
        createdMerchants.add(merchant.getId());
        return merchant.getId();
    }
}
