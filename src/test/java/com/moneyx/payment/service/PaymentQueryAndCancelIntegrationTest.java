package com.moneyx.payment.service;

import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.repository.MerchantRepository;
import com.moneyx.payment.domain.Payment;
import com.moneyx.payment.domain.PaymentCurrency;
import com.moneyx.payment.domain.PaymentStatus;
import com.moneyx.payment.exception.InvalidCursorException;
import com.moneyx.payment.exception.PaymentExpiredException;
import com.moneyx.payment.exception.PaymentNotFoundException;
import com.moneyx.payment.exception.PaymentNotPendingException;
import com.moneyx.payment.repository.PaymentRepository;
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
 * Consulta, listagem (cursor) e cancelamento contra o PostgreSQL REAL, com relógio controlado.
 * Não é @Transactional: cada chamada tem sua própria transação, como requisições HTTP reais.
 */
@SpringBootTest
@Import(TestClockConfig.class)
class PaymentQueryAndCancelIntegrationTest {

    private static final Instant START = Instant.parse("2026-11-20T12:00:00Z");


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

    // ---------- consulta ----------

    @Test
    void getReturnsOwnPayment() {
        Payment payment = createPayment(merchantId, "order-1");

        assertThat(service.get(merchantId, payment.getId()).getId()).isEqualTo(payment.getId());
    }

    @Test
    void getDoesNotRevealAnotherMerchantsPayment() {
        Payment payment = createPayment(merchantId, "order-1");
        UUID otherMerchant = newMerchant();

        assertThatThrownBy(() -> service.get(otherMerchant, payment.getId()))
                .isInstanceOf(PaymentNotFoundException.class);
        assertThatThrownBy(() -> service.get(merchantId, UUID.randomUUID()))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    // ---------- listagem ----------

    @Test
    void listIsEmptyForMerchantWithoutPayments() {
        PaymentPage page = service.list(merchantId, PaymentListQuery.firstPage());

        assertThat(page.payments()).isEmpty();
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void listPaginatesNewestFirstWithoutSkippingOrRepeating() {
        List<UUID> created = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            created.add(createPayment(merchantId, "order-" + i).getId());
            clock.advance(Duration.ofSeconds(1));
        }

        List<List<UUID>> pages = walkAllPages(merchantId, 10, null, null);

        assertThat(pages).extracting(List::size).containsExactly(10, 10, 5);
        assertThat(pages.stream().flatMap(List::stream).toList()).isEqualTo(created.reversed());
    }

    @Test
    void lastFullPageReportsNoMore() {
        for (int i = 0; i < 4; i++) {
            createPayment(merchantId, null);
            clock.advance(Duration.ofSeconds(1));
        }

        PaymentPage first = service.list(merchantId, new PaymentListQuery(2, null, null, null));
        PaymentPage second = service.list(merchantId, new PaymentListQuery(2, lastId(first), null, null));

        assertThat(first.hasMore()).isTrue();
        assertThat(second.payments()).hasSize(2);
        assertThat(second.hasMore()).isFalse();
    }

    /**
     * Vários pagamentos com o MESMO created_at: o desempate pelo id precisa manter a ordem estável.
     * A ordem esperada vem do próprio PostgreSQL (a comparação de UUID do Java é diferente da do banco).
     */
    @Test
    void paymentsCreatedAtTheSameInstantAreEachListedExactlyOnce() {
        for (int i = 0; i < 7; i++) {
            createPayment(merchantId, null); // relógio parado: todos no mesmo instante
        }

        List<UUID> expected = jdbc.queryForList(
                "SELECT id FROM payments WHERE merchant_id = ? ORDER BY created_at DESC, id DESC", UUID.class, merchantId);

        List<List<UUID>> pages = walkAllPages(merchantId, 2, null, null);

        assertThat(pages).extracting(List::size).containsExactly(2, 2, 2, 1);
        assertThat(pages.stream().flatMap(List::stream).toList()).isEqualTo(expected);
    }

    /** O motivo de usar cursor: pagamentos novos não deslocam as páginas seguintes. */
    @Test
    void newPaymentsCreatedWhileNavigatingDoNotShiftTheNextPage() {
        List<UUID> created = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            created.add(createPayment(merchantId, null).getId());
            clock.advance(Duration.ofSeconds(1));
        }
        PaymentPage first = service.list(merchantId, new PaymentListQuery(3, null, null, null));

        createPayment(merchantId, null); // chega um pagamento novo durante a navegação
        PaymentPage second = service.list(merchantId, new PaymentListQuery(3, lastId(first), null, null));

        assertThat(ids(second)).isEqualTo(created.reversed().subList(3, 6));
        assertThat(second.hasMore()).isFalse();
    }

    @Test
    void listFiltersByStatus() {
        Payment paid = createPayment(merchantId, null);
        Payment canceled = createPayment(merchantId, null);
        Payment pending = createPayment(merchantId, null);
        service.simulatePixPayment(merchantId, paid.getId());
        service.cancel(merchantId, canceled.getId());

        assertThat(ids(service.list(merchantId, new PaymentListQuery(null, null, PaymentStatus.PAID, null))))
                .containsExactly(paid.getId());
        assertThat(ids(service.list(merchantId, new PaymentListQuery(null, null, PaymentStatus.CANCELED, null))))
                .containsExactly(canceled.getId());
        assertThat(ids(service.list(merchantId, new PaymentListQuery(null, null, PaymentStatus.PENDING, null))))
                .containsExactly(pending.getId());
        assertThat(service.list(merchantId, new PaymentListQuery(null, null, PaymentStatus.EXPIRED, null)).payments())
                .isEmpty();
    }

    @Test
    void listFiltersByExternalReferenceIgnoringSurroundingSpaces() {
        Payment target = createPayment(merchantId, "order-42");
        createPayment(merchantId, "order-43");
        createPayment(merchantId, null);

        assertThat(ids(service.list(merchantId, new PaymentListQuery(null, null, null, "  order-42 "))))
                .containsExactly(target.getId());
    }

    @Test
    void filtersAndCursorWorkTogether() {
        List<UUID> paid = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Payment payment = createPayment(merchantId, null);
            service.simulatePixPayment(merchantId, payment.getId());
            paid.add(payment.getId());
            createPayment(merchantId, null); // um PENDING entre cada PAID, que o filtro deve ignorar
            clock.advance(Duration.ofSeconds(1));
        }

        List<List<UUID>> pages = walkAllPages(merchantId, 2, PaymentStatus.PAID, null);

        assertThat(pages.stream().flatMap(List::stream).toList()).isEqualTo(paid.reversed());
    }

    @Test
    void listNeverIncludesAnotherMerchantsPayments() {
        UUID otherMerchant = newMerchant();
        createPayment(otherMerchant, "order-1");
        Payment mine = createPayment(merchantId, "order-1");

        assertThat(ids(service.list(merchantId, PaymentListQuery.firstPage()))).containsExactly(mine.getId());
        assertThat(ids(service.list(merchantId, new PaymentListQuery(null, null, null, "order-1"))))
                .containsExactly(mine.getId());
    }

    @Test
    void cursorMustBeOneOfTheMerchantsOwnPayments() {
        UUID otherMerchant = newMerchant();
        Payment foreign = createPayment(otherMerchant, null);
        createPayment(merchantId, null);

        assertThatThrownBy(() -> service.list(merchantId, new PaymentListQuery(null, foreign.getId(), null, null)))
                .isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> service.list(merchantId, new PaymentListQuery(null, UUID.randomUUID(), null, null)))
                .isInstanceOf(InvalidCursorException.class);
    }

    // ---------- cancelamento ----------

    @Test
    void cancelMarksPendingPaymentAsCanceled() {
        Payment payment = createPayment(merchantId, null);
        clock.advance(Duration.ofMinutes(5));

        Payment canceled = service.cancel(merchantId, payment.getId());

        assertThat(canceled.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        Payment stored = reload(payment);
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(stored.getCanceledAt()).isEqualTo(START.plus(Duration.ofMinutes(5)));
    }

    @Test
    void cancelingTwiceIsIdempotentAndKeepsTheOriginalCancellationTime() {
        Payment payment = createPayment(merchantId, null);
        service.cancel(merchantId, payment.getId());
        clock.advance(Duration.ofMinutes(1));

        Payment again = service.cancel(merchantId, payment.getId());

        assertThat(again.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(reload(payment).getCanceledAt()).isEqualTo(START);
    }

    @Test
    void cannotCancelPaidPayment() {
        Payment payment = createPayment(merchantId, null);
        service.simulatePixPayment(merchantId, payment.getId());

        assertThatThrownBy(() -> service.cancel(merchantId, payment.getId()))
                .isInstanceOf(PaymentNotPendingException.class)
                .extracting(e -> ((PaymentNotPendingException) e).getStatus())
                .isEqualTo(PaymentStatus.PAID);
        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void cannotCancelAfterTheDeadlineEvenIfStatusIsStillPending() {
        Payment payment = createPayment(merchantId, null);
        clock.advance(Duration.ofMinutes(30)); // prazo padrão: exatamente vencido

        assertThatThrownBy(() -> service.cancel(merchantId, payment.getId()))
                .isInstanceOf(PaymentExpiredException.class);
        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void canCancelOneSecondBeforeTheDeadline() {
        Payment payment = createPayment(merchantId, null);
        clock.advance(Duration.ofMinutes(30).minusSeconds(1));

        assertThat(service.cancel(merchantId, payment.getId()).getStatus()).isEqualTo(PaymentStatus.CANCELED);
    }

    @Test
    void cannotPayCanceledPayment() {
        Payment payment = createPayment(merchantId, null);
        service.cancel(merchantId, payment.getId());

        assertThatThrownBy(() -> service.simulatePixPayment(merchantId, payment.getId()))
                .isInstanceOf(PaymentNotPendingException.class)
                .extracting(e -> ((PaymentNotPendingException) e).getStatus())
                .isEqualTo(PaymentStatus.CANCELED);
    }

    @Test
    void merchantCannotCancelAnotherMerchantsPayment() {
        Payment payment = createPayment(merchantId, null);
        UUID otherMerchant = newMerchant();

        assertThatThrownBy(() -> service.cancel(otherMerchant, payment.getId()))
                .isInstanceOf(PaymentNotFoundException.class);
        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    /**
     * Pagar x cancelar ao mesmo tempo: exatamente um vence, e TODOS os outros recebem uma resposta de negócio
     * (nunca uma falha técnica). Repetido para aumentar a chance de pegar intercalações diferentes.
     */
    @RepeatedTest(5)
    void concurrentPayAndCancelHaveExactlyOneWinner() throws Exception {
        Payment payment = createPayment(merchantId, null);
        int threads = 10;
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Object> task = i % 2 == 0
                    ? () -> new PayResult(service.simulatePixPayment(merchantId, payment.getId()))
                    : () -> new CancelResult(service.cancel(merchantId, payment.getId()));
            tasks.add(task);
        }

        List<Object> results = runConcurrently(tasks);
        PaymentStatus finalStatus = reload(payment).getStatus();

        // Nenhuma exceção fora das previstas (ex.: falha de lock otimista viraria 409 concurrent_update).
        assertThat(results).allSatisfy(r -> assertThat(r)
                .isInstanceOfAny(PayResult.class, CancelResult.class, PaymentNotPendingException.class));
        long paySuccesses = results.stream().filter(r -> r instanceof PayResult).count();
        long cancelSuccesses = results.stream().filter(r -> r instanceof CancelResult).count();

        if (finalStatus == PaymentStatus.PAID) {
            assertThat(paySuccesses).isEqualTo(1);
            assertThat(cancelSuccesses).isZero();
        } else {
            assertThat(finalStatus).isEqualTo(PaymentStatus.CANCELED);
            assertThat(paySuccesses).isZero();
            // cancelamentos repetidos são idempotentes: todos os 5 "cancelar" respondem com sucesso
            assertThat(cancelSuccesses).isEqualTo(threads / 2);
        }
        assertThat(results).filteredOn(r -> r instanceof PaymentNotPendingException)
                .allSatisfy(e -> assertThat(((PaymentNotPendingException) e).getStatus()).isEqualTo(finalStatus));
    }

    private record PayResult(Payment payment) {
    }

    private record CancelResult(Payment payment) {
    }

    // ---------- auxiliares ----------

    private Payment createPayment(UUID owner, String externalReference) {
        CreatePaymentCommand command =
                new CreatePaymentCommand(4990, PaymentCurrency.BRL, null, externalReference, null);
        return service.create(owner, UUID.randomUUID().toString(), command).payment();
    }

    private Payment reload(Payment payment) {
        return paymentRepository.findById(payment.getId()).orElseThrow();
    }

    private List<List<UUID>> walkAllPages(UUID owner, int limit, PaymentStatus status, String externalReference) {
        List<List<UUID>> pages = new ArrayList<>();
        UUID cursor = null;
        PaymentPage page;
        do {
            page = service.list(owner, new PaymentListQuery(limit, cursor, status, externalReference));
            pages.add(ids(page));
            cursor = page.payments().isEmpty() ? null : lastId(page);
            assertThat(pages).as("pagination must terminate").hasSizeLessThan(100);
        } while (page.hasMore());
        return pages;
    }

    private static List<UUID> ids(PaymentPage page) {
        return page.payments().stream().map(Payment::getId).toList();
    }

    private static UUID lastId(PaymentPage page) {
        return page.payments().getLast().getId();
    }

    private UUID newMerchant() {
        String document = String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
        Merchant merchant = merchantRepository.saveAndFlush(
                new Merchant("Query Test LTDA", document, "query-" + UUID.randomUUID() + "@moneyx.dev"));
        createdMerchants.add(merchant.getId());
        return merchant.getId();
    }

    private static List<Object> runConcurrently(List<Callable<Object>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
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
