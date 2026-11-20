package com.moneyx.payment.service;

import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.repository.MerchantRepository;
import com.moneyx.payment.domain.Payment;
import com.moneyx.payment.domain.PaymentCurrency;
import com.moneyx.payment.exception.IdempotencyKeyReusedException;
import com.moneyx.payment.exception.InvalidIdempotencyKeyException;
import com.moneyx.payment.repository.PaymentIdempotencyKeyRepository;
import com.moneyx.payment.repository.PaymentRepository;
import com.moneyx.support.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Idempotência de POST /v1/payments contra o PostgreSQL REAL.
 *
 * NÃO é @Transactional: cada chamada ao serviço precisa da sua própria transação, exatamente como
 * requisições HTTP distintas. Os dados são únicos por teste e apagados no @AfterEach.
 *
 * O relógio é controlado (MutableClock) para simular a passagem de 24 h sem esperar.
 */
@SpringBootTest
class PaymentIdempotencyIntegrationTest {

    private static final Instant START = Instant.parse("2026-11-19T12:00:00Z");

    @TestConfiguration
    static class ClockTestConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }
    }

    private static final CreatePaymentCommand ORDER_123 =
            new CreatePaymentCommand(4990, PaymentCurrency.BRL, "Pedido 123", "order-123", null);
    private static final CreatePaymentCommand ORDER_456 =
            new CreatePaymentCommand(15000, PaymentCurrency.BRL, "Pedido 456", "order-456", null);

    @Autowired
    private PaymentService service;

    @Autowired
    private MutableClock clock;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private PaymentIdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private PaymentRepository paymentRepository;

    private final List<UUID> createdMerchants = new ArrayList<>();
    private UUID merchantId;

    @BeforeEach
    void setUp() {
        clock.set(START);
        merchantId = newMerchant();
    }

    @AfterEach
    void cleanUp() {
        // Ordem respeita as FKs: chaves de idempotência -> pagamentos -> comerciantes.
        for (UUID id : createdMerchants) {
            jdbc.update("DELETE FROM events WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM payment_idempotency_keys WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM payments WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM merchants WHERE id = ?", id);
        }
    }

    // ---------- comportamento básico ----------

    @Test
    void firstRequestCreatesPaymentAndReservesTheKey() {
        PaymentCreation creation = service.create(merchantId, "key-1", ORDER_123);

        assertThat(creation.replayed()).isFalse();
        assertThat(creation.payment().getAmount()).isEqualTo(4990);
        assertThat(paymentCount()).isEqualTo(1);
        assertThat(idempotencyKeyRepository.findByMerchantIdAndIdempotencyKey(merchantId, "key-1"))
                .hasValueSatisfying(key -> {
                    assertThat(key.getPaymentId()).isEqualTo(creation.payment().getId());
                    assertThat(key.getExpiresAt()).isEqualTo(START.plus(Duration.ofHours(24)));
                });
    }

    @Test
    void repeatedRequestReturnsTheOriginalPaymentWithoutCreatingAnother() {
        PaymentCreation first = service.create(merchantId, "key-1", ORDER_123);
        clock.advance(Duration.ofMinutes(5));

        PaymentCreation second = service.create(merchantId, "key-1", ORDER_123);

        assertThat(second.replayed()).isTrue();
        assertThat(second.payment().getId()).isEqualTo(first.payment().getId());
        // A resposta do replay (lida do banco) é idêntica à original, inclusive os instantes.
        assertThat(second.payment().getCreatedAt()).isEqualTo(first.payment().getCreatedAt());
        assertThat(second.payment().getExpiresAt()).isEqualTo(first.payment().getExpiresAt());
        assertThat(paymentCount()).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentBodyIsRejectedAndNothingChanges() {
        PaymentCreation first = service.create(merchantId, "key-1", ORDER_123);

        assertThatThrownBy(() -> service.create(merchantId, "key-1", ORDER_456))
                .isInstanceOf(IdempotencyKeyReusedException.class);

        assertThat(paymentCount()).isEqualTo(1);
        assertThat(idempotencyKeyRepository.findByMerchantIdAndIdempotencyKey(merchantId, "key-1").orElseThrow()
                .getPaymentId()).isEqualTo(first.payment().getId());
    }

    @Test
    void differentKeysCreateDifferentPaymentsEvenWithTheSameBody() {
        PaymentCreation first = service.create(merchantId, "key-1", ORDER_123);
        PaymentCreation second = service.create(merchantId, "key-2", ORDER_123);

        assertThat(second.replayed()).isFalse();
        assertThat(second.payment().getId()).isNotEqualTo(first.payment().getId());
        assertThat(paymentCount()).isEqualTo(2);
    }

    // ---------- validade de 24 h ----------

    @Test
    void keyIsStillValidJustBeforeTwentyFourHours() {
        PaymentCreation first = service.create(merchantId, "key-1", ORDER_123);
        clock.advance(Duration.ofHours(24).minus(Duration.ofSeconds(1)));

        PaymentCreation second = service.create(merchantId, "key-1", ORDER_123);

        assertThat(second.replayed()).isTrue();
        assertThat(second.payment().getId()).isEqualTo(first.payment().getId());
    }

    @Test
    void keyCanBeReusedForANewPaymentAfterTwentyFourHours() {
        PaymentCreation first = service.create(merchantId, "key-1", ORDER_123);
        clock.advance(Duration.ofHours(24));

        // Depois de vencida, a chave vale para um pedido diferente, inclusive com outro corpo.
        PaymentCreation second = service.create(merchantId, "key-1", ORDER_456);

        assertThat(second.replayed()).isFalse();
        assertThat(second.payment().getId()).isNotEqualTo(first.payment().getId());
        assertThat(paymentCount()).isEqualTo(2);
        assertThat(idempotencyKeyRepository.findByMerchantIdAndIdempotencyKey(merchantId, "key-1").orElseThrow())
                .satisfies(key -> {
                    assertThat(key.getPaymentId()).isEqualTo(second.payment().getId());
                    assertThat(key.getExpiresAt()).isEqualTo(START.plus(Duration.ofHours(48)));
                });
    }

    // ---------- isolamento entre comerciantes ----------

    @Test
    void sameKeyFromDifferentMerchantsIsIndependent() {
        UUID otherMerchant = newMerchant();

        PaymentCreation mine = service.create(merchantId, "shared-key", ORDER_123);
        PaymentCreation theirs = service.create(otherMerchant, "shared-key", ORDER_456);

        // Nem replay, nem 422: a chave de B não "enxerga" a de A.
        assertThat(theirs.replayed()).isFalse();
        assertThat(theirs.payment().getMerchantId()).isEqualTo(otherMerchant);
        assertThat(theirs.payment().getId()).isNotEqualTo(mine.payment().getId());
    }

    // ---------- atomicidade ----------

    @Test
    void failureAfterReservingTheKeyRollsBackEverything() {
        doThrow(new IllegalStateException("simulated failure")).when(paymentRepository).save(any(Payment.class));

        assertThatThrownBy(() -> service.create(merchantId, "key-1", ORDER_123))
                .isInstanceOf(IllegalStateException.class);

        // A reserva da chave foi desfeita junto: ela não fica "presa" sem pagamento.
        assertThat(idempotencyKeyRepository.findByMerchantIdAndIdempotencyKey(merchantId, "key-1")).isEmpty();
        assertThat(paymentCount()).isZero();
    }

    @Test
    void clientCanRetryWithTheSameKeyAfterAFailure() {
        doThrow(new IllegalStateException("simulated failure")).when(paymentRepository).save(any(Payment.class));

        assertThatThrownBy(() -> service.create(merchantId, "key-1", ORDER_123))
                .isInstanceOf(IllegalStateException.class);

        // A falha passou (ex.: banco voltou). O spy volta a delegar para o repositório real.
        Mockito.reset(paymentRepository);
        PaymentCreation retry = service.create(merchantId, "key-1", ORDER_123);

        assertThat(retry.replayed()).isFalse();
        assertThat(paymentCount()).isEqualTo(1);
    }

    @Test
    void invalidPaymentDoesNotConsumeTheKey() {
        CreatePaymentCommand tooSmall = new CreatePaymentCommand(50, PaymentCurrency.BRL, null, null, null);

        assertThatThrownBy(() -> service.create(merchantId, "key-1", tooSmall))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(idempotencyKeyRepository.findByMerchantIdAndIdempotencyKey(merchantId, "key-1")).isEmpty();
        assertThat(service.create(merchantId, "key-1", ORDER_123).replayed()).isFalse();
    }

    // ---------- formato da chave ----------

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "has space", "tab\there", "ção"})
    void rejectsMalformedKeys(String key) {
        assertThatThrownBy(() -> service.create(merchantId, key, ORDER_123))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
        assertThat(paymentCount()).isZero();
    }

    @Test
    void acceptsKeysUpTo255Characters() {
        assertThat(service.create(merchantId, "k".repeat(255), ORDER_123).replayed()).isFalse();
        assertThatThrownBy(() -> service.create(merchantId, "k".repeat(256), ORDER_123))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
    }

    // ---------- concorrência ----------

    @Test
    void concurrentRequestsWithTheSameKeyCreateExactlyOnePayment() throws Exception {
        int threads = 10;

        List<Object> results = runConcurrently(threads, i -> service.create(merchantId, "same-key", ORDER_123));

        List<PaymentCreation> creations = results.stream()
                .map(r -> assertThat(r).isInstanceOf(PaymentCreation.class).actual())
                .map(PaymentCreation.class::cast)
                .toList();

        assertThat(paymentCount()).isEqualTo(1);
        assertThat(creations).extracting(c -> c.payment().getId()).containsOnly(creations.getFirst().payment().getId());
        assertThat(creations).filteredOn(c -> !c.replayed()).hasSize(1);
        assertThat(creations).filteredOn(PaymentCreation::replayed).hasSize(threads - 1);
    }

    @Test
    void concurrentRequestsWithTheSameKeyButDifferentBodiesNeverCreateTwoPayments() throws Exception {
        int threads = 10;

        List<Object> results = runConcurrently(threads,
                i -> service.create(merchantId, "same-key", i % 2 == 0 ? ORDER_123 : ORDER_456));

        // Exatamente um vence; os de corpo igual ao vencedor recebem replay, os outros 422.
        assertThat(paymentCount()).isEqualTo(1);
        assertThat(results).filteredOn(r -> r instanceof PaymentCreation c && !c.replayed()).hasSize(1);
        assertThat(results).allSatisfy(r -> assertThat(r)
                .isInstanceOfAny(PaymentCreation.class, IdempotencyKeyReusedException.class));
        assertThat(results).filteredOn(r -> r instanceof IdempotencyKeyReusedException).hasSize(threads / 2);
    }

    @Test
    void concurrentRequestsWithDifferentKeysAllSucceed() throws Exception {
        int threads = 10;

        List<Object> results = runConcurrently(threads, i -> service.create(merchantId, "key-" + i, ORDER_123));

        assertThat(results).allSatisfy(r -> assertThat(r).isInstanceOf(PaymentCreation.class));
        assertThat(paymentCount()).isEqualTo(threads);
    }

    // ---------- auxiliares ----------

    private long paymentCount() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM payments WHERE merchant_id = ?", Long.class, merchantId);
        return count == null ? 0 : count;
    }

    private UUID newMerchant() {
        String document = String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
        Merchant merchant = merchantRepository.saveAndFlush(
                new Merchant("Idempotency Test LTDA", document, "idempotency-" + UUID.randomUUID() + "@moneyx.dev"));
        createdMerchants.add(merchant.getId());
        return merchant.getId();
    }

    @FunctionalInterface
    private interface Task {
        Object run(int index);
    }

    /** Dispara as tarefas ao mesmo tempo. Cada resultado é o retorno da tarefa ou a exceção lançada. */
    private static List<Object> runConcurrently(int threads, Task task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                Callable<Object> callable = () -> {
                    ready.countDown();
                    start.await();
                    try {
                        return task.run(index);
                    } catch (RuntimeException e) {
                        return e;
                    }
                };
                futures.add(executor.submit(callable));
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
