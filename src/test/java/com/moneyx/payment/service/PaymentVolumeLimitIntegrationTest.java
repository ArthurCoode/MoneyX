package com.moneyx.payment.service;

import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.repository.MerchantRepository;
import com.moneyx.payment.domain.PaymentCurrency;
import com.moneyx.payment.domain.PaymentStatus;
import com.moneyx.payment.exception.VolumeLimitExceededException;
import com.moneyx.payment.exception.VolumeLimitExceededException.Period;
import com.moneyx.support.MutableClock;
import com.moneyx.support.TestClockConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
 * Limite acumulado de volume (R$ 200 mil/dia, R$ 2 mi/mês, fuso de São Paulo) contra o PostgreSQL REAL.
 *
 * Usa os valores PADRÃO da configuração (os testes não os mudam). NÃO é @Transactional: cada chamada ao serviço
 * tem a própria transação, como requisições HTTP distintas.
 *
 * Lembrete de fuso: São Paulo = UTC-3. 03:00Z é meia-noite local.
 */
@SpringBootTest
@Import(TestClockConfig.class)
class PaymentVolumeLimitIntegrationTest {

    private static final long DAILY = 20_000_000;      // R$ 200.000,00
    private static final long MONTHLY = 200_000_000;   // R$ 2.000.000,00
    private static final long MAX_PER_PAYMENT = 10_000_000;

    /** 19/11/2026 12:00 em São Paulo. */
    private static final Instant NOON_SP = Instant.parse("2026-11-19T15:00:00Z");

    @Autowired
    private PaymentService service;

    @Autowired
    private PaymentMaintenanceService maintenance;

    @Autowired
    private MutableClock clock;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> createdMerchants = new ArrayList<>();
    private UUID merchantId;
    private int keySequence;

    @BeforeEach
    void setUp() {
        clock.set(NOON_SP);
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

    // ---------- limite diário ----------

    @Test
    void paymentThatReachesTheDailyLimitExactlyIsAcceptedAndTheNextCentIsNot() {
        create(MAX_PER_PAYMENT);
        create(9_999_900);
        create(100);                                    // soma == limite: passa

        assertThatThrownBy(() -> create(100))
                .isInstanceOfSatisfying(VolumeLimitExceededException.class, ex -> {
                    assertThat(ex.getPeriod()).isEqualTo(Period.DAILY);
                    assertThat(ex.getLimit()).isEqualTo(DAILY);
                    assertThat(ex.getRemaining()).isZero();
                    // Próxima meia-noite em São Paulo.
                    assertThat(ex.getResetsAt()).isEqualTo(Instant.parse("2026-11-20T03:00:00Z"));
                });
        assertThat(volume()).isEqualTo(DAILY);
    }

    @Test
    void rejectionReportsHowMuchStillFits() {
        create(MAX_PER_PAYMENT);
        create(9_999_000);                              // cabem mais 1.000 centavos

        assertThatThrownBy(() -> create(1_001))
                .isInstanceOfSatisfying(VolumeLimitExceededException.class,
                        ex -> assertThat(ex.getRemaining()).isEqualTo(1_000));
        create(1_000);
    }

    @Test
    void rejectedPaymentLeavesNoPaymentEventOrIdempotencyKeyAndTheKeyCanBeRetriedLater() {
        fillDay();
        long payments = count("payments");
        long events = count("events");
        long keys = count("payment_idempotency_keys");

        assertThatThrownBy(() -> service.create(merchantId, "retry-me", command(500)))
                .isInstanceOf(VolumeLimitExceededException.class);

        assertThat(count("payments")).isEqualTo(payments);
        assertThat(count("events")).isEqualTo(events);
        assertThat(count("payment_idempotency_keys")).isEqualTo(keys);

        // No dia seguinte (local), a MESMA chave cria o pagamento normalmente.
        clock.set(Instant.parse("2026-11-20T03:00:00Z"));
        PaymentCreation retried = service.create(merchantId, "retry-me", command(500));
        assertThat(retried.replayed()).isFalse();
    }

    @Test
    void replayOfAnExistingPaymentIsNeitherCountedAgainNorRejected() {
        create(MAX_PER_PAYMENT);
        PaymentCreation original = service.create(merchantId, "last-one", command(MAX_PER_PAYMENT));

        // O dia está cheio, mas a repetição devolve o pagamento original.
        PaymentCreation replay = service.create(merchantId, "last-one", command(MAX_PER_PAYMENT));

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.payment().getId()).isEqualTo(original.payment().getId());
        assertThat(volume()).isEqualTo(DAILY);
    }

    // ---------- o que conta ----------

    @Test
    void canceledPaymentsDoNotCount() {
        PaymentCreation first = service.create(merchantId, "to-cancel", command(MAX_PER_PAYMENT));
        create(MAX_PER_PAYMENT);
        service.cancel(merchantId, first.payment().getId());

        create(MAX_PER_PAYMENT);                        // cabe porque o cancelado saiu da soma
    }

    @Test
    void paidAndExpiredPaymentsStillCount() {
        PaymentCreation paid = service.create(merchantId, "to-pay", command(MAX_PER_PAYMENT));
        service.simulatePixPayment(merchantId, paid.payment().getId());
        PaymentCreation toExpire = service.create(merchantId, "to-expire", command(MAX_PER_PAYMENT));

        clock.advance(Duration.ofMinutes(31));          // prazo padrão: 30 min
        maintenance.expireOverduePayments();
        assertThat(statusOf(toExpire.payment().getId())).isEqualTo("EXPIRED");

        assertThatThrownBy(() -> create(100))
                .isInstanceOfSatisfying(VolumeLimitExceededException.class,
                        ex -> assertThat(ex.getPeriod()).isEqualTo(Period.DAILY));
    }

    @Test
    void anotherMerchantsVolumeIsNeverCounted() {
        fillDay();
        UUID other = newMerchant();

        PaymentCreation creation = service.create(other, "other-1", command(MAX_PER_PAYMENT));

        assertThat(creation.payment().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    // ---------- "dia" no fuso de São Paulo ----------

    @Test
    void dayBoundaryFollowsSaoPauloTimeNotUtc() {
        // 19/11 01:00Z = 18/11 22:00 em São Paulo: pertence ao dia 18 local, apesar de ser dia 19 em UTC.
        clock.set(Instant.parse("2026-11-19T01:00:00Z"));
        fillDay();

        // 02:59:59Z = 18/11 23:59:59 local: ainda o mesmo dia, cheio.
        clock.set(Instant.parse("2026-11-19T02:59:59Z"));
        assertThatThrownBy(() -> create(100)).isInstanceOf(VolumeLimitExceededException.class);

        // 03:00Z = 19/11 00:00 local: dia novo, o volume do dia 18 não conta mais.
        clock.set(Instant.parse("2026-11-19T03:00:00Z"));
        create(MAX_PER_PAYMENT);
        create(MAX_PER_PAYMENT);
    }

    // ---------- limite mensal ----------

    @Test
    void monthlyLimitIsEnforcedAcrossDaysAndResetsAtTheStartOfTheNextMonthInSaoPaulo() {
        // 20 pagamentos de R$ 100 mil, 2 por dia, de 01/11 a 10/11 (locais): R$ 2 mi = limite do mês exato.
        for (int day = 1; day <= 10; day++) {
            clock.set(Instant.parse(String.format("2026-11-%02dT15:00:00Z", day)));
            fillDay();
        }

        clock.set(Instant.parse("2026-11-11T15:00:00Z"));    // dia novo, mas o mês está cheio
        assertThatThrownBy(() -> create(100))
                .isInstanceOfSatisfying(VolumeLimitExceededException.class, ex -> {
                    assertThat(ex.getPeriod()).isEqualTo(Period.MONTHLY);
                    assertThat(ex.getLimit()).isEqualTo(MONTHLY);
                    assertThat(ex.getRemaining()).isZero();
                    assertThat(ex.getResetsAt()).isEqualTo(Instant.parse("2026-12-01T03:00:00Z"));
                });

        // 01/12 02:59Z ainda é 30/11 em São Paulo.
        clock.set(Instant.parse("2026-12-01T02:59:00Z"));
        assertThatThrownBy(() -> create(100)).isInstanceOf(VolumeLimitExceededException.class);

        clock.set(Instant.parse("2026-12-01T03:00:00Z"));
        create(MAX_PER_PAYMENT);
    }

    @Test
    void whenBothLimitsWouldBeExceededTheMonthlyOneIsReported() {
        for (int day = 1; day <= 10; day++) {
            clock.set(Instant.parse(String.format("2026-11-%02dT15:00:00Z", day)));
            fillDay();
        }
        // Ainda no dia 10: o dia E o mês estão cheios; "mensal" é a resposta útil (amanhã também falharia).
        assertThatThrownBy(() -> create(100))
                .isInstanceOfSatisfying(VolumeLimitExceededException.class,
                        ex -> assertThat(ex.getPeriod()).isEqualTo(Period.MONTHLY));
    }

    // ---------- concorrência ----------

    /**
     * Restam R$ 10.000,00 no dia e 10 pedidos simultâneos de R$ 3.000,00: exatamente 3 cabem.
     * Sem a trava na linha do comerciante, todos leriam a mesma soma e passariam (R$ 30 mil acima do limite).
     * Também prova que não há deadlock (seria uma exceção diferente).
     */
    @RepeatedTest(5)
    void concurrentCreationsNeverExceedTheLimit() throws Exception {
        create(MAX_PER_PAYMENT);
        create(9_000_000);                              // restam 1.000.000

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                String key = "concurrent-" + i;
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        return service.create(merchantId, key, command(300_000));
                    } catch (RuntimeException ex) {
                        return ex;
                    }
                }));
            }
            start.countDown();

            int accepted = 0;
            int rejected = 0;
            for (Future<Object> result : results) {
                Object outcome = result.get(30, TimeUnit.SECONDS);
                if (outcome instanceof PaymentCreation) {
                    accepted++;
                } else {
                    assertThat(outcome).isInstanceOf(VolumeLimitExceededException.class);
                    rejected++;
                }
            }
            assertThat(accepted).isEqualTo(3);
            assertThat(rejected).isEqualTo(7);
            assertThat(volume()).isEqualTo(19_900_000);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- auxiliares ----------

    /** Enche o dia local atual com 2 pagamentos de R$ 100 mil. */
    private void fillDay() {
        create(MAX_PER_PAYMENT);
        create(MAX_PER_PAYMENT);
    }

    private PaymentCreation create(long amount) {
        return service.create(merchantId, "key-" + (++keySequence), command(amount));
    }

    private static CreatePaymentCommand command(long amount) {
        return new CreatePaymentCommand(amount, PaymentCurrency.BRL, null, null, null);
    }

    private long volume() {
        Long sum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM payments WHERE merchant_id = ? AND status <> 'CANCELED'",
                Long.class, merchantId);
        return sum == null ? 0 : sum;
    }

    private long count(String table) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE merchant_id = ?", Long.class, merchantId);
        return count == null ? 0 : count;
    }

    private String statusOf(UUID paymentId) {
        return jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, paymentId);
    }

    private UUID newMerchant() {
        String document = String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
        Merchant merchant = merchantRepository.saveAndFlush(
                new Merchant("Volume Test LTDA", document, "volume-" + UUID.randomUUID() + "@moneyx.dev"));
        createdMerchants.add(merchant.getId());
        return merchant.getId();
    }
}
