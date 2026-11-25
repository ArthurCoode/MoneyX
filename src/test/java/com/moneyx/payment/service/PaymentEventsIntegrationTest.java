package com.moneyx.payment.service;

import com.moneyx.event.domain.Event;
import com.moneyx.event.domain.EventType;
import com.moneyx.event.service.EventListQuery;
import com.moneyx.event.service.EventPage;
import com.moneyx.event.service.EventService;
import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.repository.MerchantRepository;
import com.moneyx.payment.domain.Payment;
import com.moneyx.payment.domain.PaymentCurrency;
import com.moneyx.payment.domain.PaymentStatus;
import com.moneyx.payment.exception.PaymentNotPendingException;
import com.moneyx.payment.repository.PaymentRepository;
import com.moneyx.support.MutableClock;
import com.moneyx.support.TestClockConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

/**
 * Eventos de pagamento contra o PostgreSQL REAL: cada transição gera exatamente um evento, na MESMA
 * transação (Outbox). Relógio em 2020 porque o job de expiração é exercitado (ver PaymentMaintenanceIntegrationTest).
 */
@SpringBootTest
@Import(TestClockConfig.class)
class PaymentEventsIntegrationTest {

    private static final Instant START = Instant.parse("2020-01-01T12:00:00Z");
    private static final CreatePaymentCommand ORDER =
            new CreatePaymentCommand(4990, PaymentCurrency.BRL, "Pedido 7", "order-7", null);


    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentMaintenanceService maintenance;

    @Autowired
    private EventService eventService;

    /**
     * Espião do PaymentEventRecorder (sem proxy transacional) para simular falha ao gravar o evento.
     * Espionar o EventService não serve: o stub chamaria record() pelo proxy, que exige transação (MANDATORY).
     */
    @MockitoSpyBean
    private PaymentEventRecorder eventRecorder;

    @Autowired
    private MutableClock clock;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JsonMapper jsonMapper;

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
        Mockito.reset(eventRecorder);
        for (UUID id : createdMerchants) {
            jdbc.update("DELETE FROM events WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM payment_idempotency_keys WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM payments WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM merchants WHERE id = ?", id);
        }
    }

    // ---------- um evento por transição ----------

    @Test
    void creatingAPaymentRecordsPaymentCreatedWithTheSnapshot() {
        Payment payment = create("key-1");

        List<Event> events = eventsOf(payment);
        assertThat(events).extracting(Event::getType).containsExactly(EventType.PAYMENT_CREATED);
        Event created = events.getFirst();
        assertThat(created.getMerchantId()).isEqualTo(merchantId);
        assertThat(created.getCreatedAt()).isEqualTo(payment.getCreatedAt());
        assertThat(json(created).get("status").asString()).isEqualTo("PENDING");
        assertThat(json(created).get("amount").asLong()).isEqualTo(4990);
    }

    @Test
    void idempotentReplayDoesNotRecordASecondPaymentCreated() {
        Payment payment = create("key-1");
        PaymentCreation replay = paymentService.create(merchantId, "key-1", ORDER);

        assertThat(replay.replayed()).isTrue();
        assertThat(eventsOf(payment)).extracting(Event::getType).containsExactly(EventType.PAYMENT_CREATED);
    }

    @Test
    void payingRecordsPaymentPaidAtThePaymentTime() {
        Payment payment = create("key-1");
        clock.advance(Duration.ofMinutes(3));
        paymentService.simulatePixPayment(merchantId, payment.getId());

        Event paid = lastEventOf(payment);
        assertThat(paid.getType()).isEqualTo(EventType.PAYMENT_PAID);
        assertThat(paid.getCreatedAt()).isEqualTo(START.plus(Duration.ofMinutes(3)).plusSeconds(1));
        assertThat(json(paid).get("status").asString()).isEqualTo("PAID");
        assertThat(json(paid).get("paidAt").asString()).isEqualTo("2020-01-01T12:03:01Z");
    }

    @Test
    void cancelingRecordsPaymentCanceledOnlyOnceEvenWhenRepeated() {
        Payment payment = create("key-1");
        paymentService.cancel(merchantId, payment.getId());
        paymentService.cancel(merchantId, payment.getId()); // idempotente: nada muda

        assertThat(eventsOf(payment)).extracting(Event::getType)
                .containsExactly(EventType.PAYMENT_CANCELED, EventType.PAYMENT_CREATED);
        assertThat(json(lastEventOf(payment)).get("status").asString()).isEqualTo("CANCELED");
    }

    @Test
    void expirationJobRecordsPaymentExpired() {
        Payment payment = create("key-1");
        clock.advance(Duration.ofMinutes(30)); // 30 min + 1 s depois da criação
        maintenance.expireOverduePayments();

        Event expired = lastEventOf(payment);
        assertThat(expired.getType()).isEqualTo(EventType.PAYMENT_EXPIRED);
        assertThat(json(expired).get("status").asString()).isEqualTo("EXPIRED");
    }

    @Test
    void failedTransitionsRecordNoEvent() {
        Payment payment = create("key-1");
        paymentService.simulatePixPayment(merchantId, payment.getId());

        assertThatThrownBy(() -> paymentService.cancel(merchantId, payment.getId()))
                .isInstanceOf(PaymentNotPendingException.class);
        assertThatThrownBy(() -> paymentService.simulatePixPayment(merchantId, payment.getId()))
                .isInstanceOf(PaymentNotPendingException.class);

        assertThat(eventsOf(payment)).extracting(Event::getType)
                .containsExactly(EventType.PAYMENT_PAID, EventType.PAYMENT_CREATED);
    }

    // ---------- o retrato ----------

    /** O data do evento é exatamente o JSON que GET /v1/payments/{id} devolveria naquele momento. */
    @Test
    void snapshotHasTheSameShapeAsTheApiRepresentation() {
        Payment payment = create("key-1");
        paymentService.simulatePixPayment(merchantId, payment.getId());

        // Serializa e lê de volta (como um cliente da API receberia): comparar com valueToTree falharia só porque
        // o Jackson representa o mesmo 4990 como LongNode de um lado e IntNode do outro.
        JsonNode expected = jsonMapper.readTree(jsonMapper.writeValueAsString(PaymentView.from(reload(payment))));
        assertThat(json(lastEventOf(payment))).isEqualTo(expected);
    }

    /** Eventos são imutáveis: o payment.created continua mostrando PENDING depois que o pagamento foi pago. */
    @Test
    void olderEventsKeepTheStateOfTheirOwnMoment() {
        Payment payment = create("key-1");
        paymentService.simulatePixPayment(merchantId, payment.getId());

        Event created = eventsOf(payment).getLast();
        assertThat(created.getType()).isEqualTo(EventType.PAYMENT_CREATED);
        assertThat(json(created).get("status").asString()).isEqualTo("PENDING");
        assertThat(json(created).get("paidAt").isNull()).isTrue();
    }

    @Test
    void dataIsStoredAsAJsonObjectInTheDatabase() {
        Payment payment = create("key-1");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT jsonb_typeof(data) AS kind, data->>'status' AS status FROM events WHERE resource_id = ?",
                payment.getId());
        assertThat(row).containsEntry("kind", "object").containsEntry("status", "PENDING");
    }

    // ---------- atomicidade (Outbox) ----------

    @Test
    void ifTheEventCannotBeRecordedThePaymentDoesNotChange() {
        Payment payment = create("key-1");
        doThrow(new IllegalStateException("simulated failure"))
                .when(eventRecorder).record(eq(EventType.PAYMENT_PAID), any(), any());

        assertThatThrownBy(() -> paymentService.simulatePixPayment(merchantId, payment.getId()))
                .hasMessage("simulated failure");

        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(eventsOf(payment)).extracting(Event::getType).containsExactly(EventType.PAYMENT_CREATED);
    }

    /**
     * Desde a Etapa 7c o payment.created é gravado na FASE 3 (junto com CREATING -> PENDING). Se ele falhar, a fase 3
     * inteira é desfeita: o pagamento continua CREATING, sem cobrança e sem evento. A repetição com a mesma chave
     * retoma a criação e grava o evento UMA vez.
     */
    @Test
    void ifTheCreatedEventCannotBeRecordedThePaymentStaysCreatingAndTheRetryCompletesIt() {
        doThrow(new IllegalStateException("simulated failure"))
                .when(eventRecorder).record(eq(EventType.PAYMENT_CREATED), any(), any());

        assertThatThrownBy(() -> paymentService.create(merchantId, "key-1", ORDER)).hasMessage("simulated failure");
        Mockito.reset(eventRecorder);

        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE merchant_id = ?", String.class, merchantId))
                .isEqualTo("CREATING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM events WHERE merchant_id = ?", Integer.class, merchantId))
                .isZero();

        PaymentCreation retried = paymentService.create(merchantId, "key-1", ORDER);
        assertThat(retried.replayed()).isTrue();
        assertThat(retried.payment().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(eventsOf(retried.payment())).extracting(Event::getType).containsExactly(EventType.PAYMENT_CREATED);
    }

    @Test
    void ifTheTransactionIsRolledBackTheEventDisappearsWithTheStateChange() {
        Payment payment = create("key-1");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            paymentService.cancel(merchantId, payment.getId()); // participa desta transação
            status.setRollbackOnly();
        });

        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(eventsOf(payment)).extracting(Event::getType).containsExactly(EventType.PAYMENT_CREATED);
    }

    @Test
    void eventsCanOnlyBeRecordedInsideAnExistingTransaction() {
        assertThatThrownBy(() -> eventService.record(merchantId, EventType.PAYMENT_PAID, UUID.randomUUID(),
                Map.of("status", "PAID"), START))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void concurrentPaymentsRecordExactlyOnePaymentPaid() throws Exception {
        Payment payment = create("key-1");
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        paymentService.simulatePixPayment(merchantId, payment.getId());
                    } catch (PaymentNotPendingException expected) {
                        // os perdedores
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(eventsOf(payment)).extracting(Event::getType)
                .containsExactly(EventType.PAYMENT_PAID, EventType.PAYMENT_CREATED);
    }

    // ---------- listagem ----------

    @Test
    void listPaginatesNewestFirstAndFiltersByType() {
        List<UUID> paidEvents = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Payment payment = create("key-" + i);
            paymentService.simulatePixPayment(merchantId, payment.getId());
            paidEvents.add(lastEventOf(payment).getId());
            clock.advance(Duration.ofSeconds(1));
        }

        EventPage first = eventService.list(merchantId, new EventListQuery(4, null, null));
        EventPage second = eventService.list(merchantId,
                new EventListQuery(4, first.events().getLast().getId(), null));
        EventPage paidOnly = eventService.list(merchantId, new EventListQuery(null, null, EventType.PAYMENT_PAID));

        assertThat(first.events()).hasSize(4);
        assertThat(first.hasMore()).isTrue();
        assertThat(second.events()).hasSize(2);
        assertThat(second.hasMore()).isFalse();
        assertThat(first.events().getFirst().getType()).isEqualTo(EventType.PAYMENT_PAID);
        assertThat(paidOnly.events()).extracting(Event::getId).containsExactlyElementsOf(paidEvents.reversed());
    }

    @Test
    void listNeverIncludesAnotherMerchantsEvents() {
        UUID otherMerchant = newMerchant();
        paymentService.create(otherMerchant, "key-1", ORDER);
        Payment mine = create("key-1");

        assertThat(eventService.list(merchantId, EventListQuery.firstPage()).events())
                .extracting(Event::getResourceId).containsExactly(mine.getId());
    }

    // ---------- auxiliares ----------

    /**
     * Cria e avança o relógio 1 s: assim a transição seguinte nunca tem o MESMO instante do payment.created
     * (num empate, a ordem entre os dois eventos seria decidida pelo id, que é aleatório).
     */
    private Payment create(String idempotencyKey) {
        Payment payment = paymentService.create(merchantId, idempotencyKey, ORDER).payment();
        clock.advance(Duration.ofSeconds(1));
        return payment;
    }

    private Payment reload(Payment payment) {
        return paymentRepository.findById(payment.getId()).orElseThrow();
    }

    /** Eventos do pagamento, mais recentes primeiro (mesma ordem da API). */
    private List<Event> eventsOf(Payment payment) {
        return eventService.list(payment.getMerchantId(), new EventListQuery(100, null, null)).events().stream()
                .filter(event -> event.getResourceId().equals(payment.getId()))
                .toList();
    }

    private Event lastEventOf(Payment payment) {
        return eventsOf(payment).getFirst();
    }

    private JsonNode json(Event event) {
        return jsonMapper.readTree(event.getData());
    }

    private UUID newMerchant() {
        String document = String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
        Merchant merchant = merchantRepository.saveAndFlush(
                new Merchant("Events Test LTDA", document, "events-" + UUID.randomUUID() + "@moneyx.dev"));
        createdMerchants.add(merchant.getId());
        return merchant.getId();
    }
}
