package com.moneyx.webhook.service;

import com.moneyx.event.domain.Event;
import com.moneyx.event.domain.EventType;
import com.moneyx.event.service.EventService;
import com.moneyx.event.service.EventView;
import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.repository.MerchantRepository;
import com.moneyx.payment.domain.Payment;
import com.moneyx.payment.domain.PaymentCurrency;
import com.moneyx.payment.service.CreatePaymentCommand;
import com.moneyx.payment.service.PaymentService;
import com.moneyx.support.MutableClock;
import com.moneyx.support.TestClockConfig;
import com.moneyx.webhook.domain.WebhookDelivery;
import com.moneyx.webhook.domain.WebhookDeliveryStatus;
import com.moneyx.webhook.domain.WebhookSignature;
import com.moneyx.webhook.repository.WebhookDeliveryRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Entrega de webhooks de ponta a ponta: PostgreSQL real + um servidor HTTP local fazendo o papel da LOJA, que
 * confere a assinatura como uma loja real faria.
 *
 * allow-local-urls=true SOMENTE neste contexto (para poder cadastrar http://127.0.0.1). Timeouts curtos para que o
 * teste de timeout seja rápido. Relógio em 2020: o job processa QUALQUER entrega vencida, e assim nunca toca em
 * dados reais do banco de desenvolvimento.
 */
@SpringBootTest(properties = {
        "moneyx.webhooks.allow-local-urls=true",
        "moneyx.webhooks.delivery.connect-timeout=1s",
        "moneyx.webhooks.delivery.request-timeout=1s",
        "moneyx.webhooks.delivery.lease=5s",
        // Sem jitter: os testes conferem os horários exatos da agenda de retentativas.
        "moneyx.webhooks.delivery.retry-jitter=0"
})
@Import(TestClockConfig.class)
class WebhookDeliveryIntegrationTest {

    private static final Instant START = Instant.parse("2020-01-01T12:00:00Z");
    private static final CreatePaymentCommand ORDER =
            new CreatePaymentCommand(4990, PaymentCurrency.BRL, "Pedido 9", "order-9", null);


    // ---------- a "loja" ----------

    record Received(String path, Map<String, String> headers, byte[] body) {
        String header(String name) {
            return headers.get(name.toLowerCase());
        }
    }

    private static HttpServer store;
    private static final ConcurrentLinkedQueue<Received> received = new ConcurrentLinkedQueue<>();
    /** Comportamento da loja por caminho: status a responder (302 = redireciona; 0 = demora além do timeout). */
    private static final Map<String, Integer> behavior = new java.util.concurrent.ConcurrentHashMap<>();

    @BeforeAll
    static void startStore() throws IOException {
        store = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        store.setExecutor(Executors.newCachedThreadPool());
        store.createContext("/", WebhookDeliveryIntegrationTest::handle);
        store.start();
    }

    @AfterAll
    static void stopStore() {
        store.stop(0);
    }

    private static void handle(HttpExchange exchange) throws IOException {
        Map<String, String> headers = exchange.getRequestHeaders().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().toLowerCase(), e -> e.getValue().getFirst(), (a, b) -> a));
        String path = exchange.getRequestURI().getPath();
        received.add(new Received(path, headers, exchange.getRequestBody().readAllBytes()));
        int status = behavior.getOrDefault(path, 200);
        if (status == 0) {
            try {
                Thread.sleep(3_000); // além do request-timeout (1 s)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            status = 200;
        }
        if (status == 302) {
            exchange.getResponseHeaders().add("Location", storeUrl("/stolen"));
        }
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private static String storeUrl(String path) {
        return "http://127.0.0.1:" + store.getAddress().getPort() + path;
    }

    // ---------- contexto ----------

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private WebhookEndpointService endpointService;

    @Autowired
    private WebhookDeliveryService deliveryService;

    @Autowired
    private WebhookDeliveryRepository deliveryRepository;

    @Autowired
    private EventService eventService;

    @Autowired
    private MutableClock clock;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> createdMerchants = new ArrayList<>();
    private final UUID apiKeyId = UUID.randomUUID();
    private UUID merchantId;

    @BeforeEach
    void setUp() {
        clock.set(START);
        received.clear();
        behavior.clear();
        merchantId = newMerchant();
    }

    @AfterEach
    void cleanUp() {
        for (UUID id : createdMerchants) {
            jdbc.update("DELETE FROM webhook_deliveries WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM webhook_endpoint_history WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM webhook_endpoints WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM events WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM payment_idempotency_keys WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM payments WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM merchants WHERE id = ?", id);
        }
    }

    // ---------- criação das entregas (fan-out) ----------

    @Test
    void createsOneDeliveryPerActiveEndpointInterestedInTheEvent() {
        UUID all = endpoint(merchantId, "/all", null);
        UUID paidOnly = endpoint(merchantId, "/paid", List.of("payment.paid"));
        UUID removed = endpoint(merchantId, "/removed", null);
        endpointService.delete(merchantId, apiKeyId, removed);
        endpoint(newMerchant(), "/other-merchant", null);

        Payment payment = createPayment("key-1");
        Event created = lastEvent(EventType.PAYMENT_CREATED);
        clock.advance(Duration.ofSeconds(1));
        paymentService.simulatePixPayment(merchantId, payment.getId());
        Event paid = lastEvent(EventType.PAYMENT_PAID);

        assertThat(deliveryRepository.findByEventId(created.getId()))
                .extracting(WebhookDelivery::getEndpointId).containsExactly(all);
        assertThat(deliveryRepository.findByEventId(paid.getId()))
                .extracting(WebhookDelivery::getEndpointId).containsExactlyInAnyOrder(all, paidOnly);
        assertThat(deliveryRepository.findByEventId(paid.getId()))
                .allSatisfy(d -> assertThat(d.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING));
    }

    @Test
    void idempotentReplayCreatesNoNewDelivery() {
        endpoint(merchantId, "/all", null);
        createPayment("key-1");
        paymentService.create(merchantId, "key-1", ORDER); // replay: nenhum evento novo

        assertThat(countDeliveries()).isEqualTo(1);
    }

    /** Outbox: se a transição for desfeita, a entrega também é. */
    @Test
    void rolledBackTransitionLeavesNoDelivery() {
        endpoint(merchantId, "/all", null);
        Payment payment = createPayment("key-1");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            paymentService.cancel(merchantId, payment.getId());
            status.setRollbackOnly();
        });

        assertThat(countDeliveries()).isEqualTo(1); // só a do payment.created
    }

    // ---------- envio ----------

    @Test
    void deliversASignedEventThatTheStoreCanVerify() {
        UUID endpointId = endpoint(merchantId, "/hook", null);
        String secret = endpointService.signingSecretOf(endpointService.get(merchantId, endpointId));
        createPayment("key-1");
        Event event = lastEvent(EventType.PAYMENT_CREATED);

        assertThat(deliveryService.deliverDue()).isEqualTo(1);

        assertThat(received).hasSize(1);
        Received request = received.peek();
        // A loja confere a assinatura com o segredo dela, exatamente como na documentação.
        assertThat(WebhookSignature.verify(secret, request.header(WebhookSignature.HEADER), request.body(),
                clock.instant(), WebhookSignature.DEFAULT_TOLERANCE)).isTrue();
        assertThat(request.header("MoneyX-Event-Id")).isEqualTo(event.getId().toString());
        assertThat(request.header("MoneyX-Event-Type")).isEqualTo("payment.created");
        assertThat(request.header("Content-Type")).isEqualTo("application/json");
        assertThat(request.header("User-Agent")).isEqualTo("MoneyX-Webhooks/1.0");
        // O corpo é o mesmo envelope de GET /v1/events/{id}.
        assertThat(jsonMapper.readTree(request.body()))
                .isEqualTo(jsonMapper.readTree(jsonMapper.writeValueAsBytes(EventView.from(event))));

        WebhookDelivery delivery = deliveryRepository.findByEventId(event.getId()).getFirst();
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.SUCCEEDED);
        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getLastStatusCode()).isEqualTo(200);
        assertThat(delivery.getDeliveredAt()).isNotNull();
        assertThat(delivery.getLeaseUntil()).isNull();

        assertThat(deliveryService.deliverDue()).isZero(); // nada é reenviado
        assertThat(received).hasSize(1);
    }

    @Test
    void non2xxResponseIsAFailureThatIsRetriedOneMinuteLater() {
        behavior.put("/broken", 500);
        endpoint(merchantId, "/broken", null);
        createPayment("key-1");

        deliveryService.deliverDue();

        WebhookDelivery delivery = onlyDelivery();
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getLastStatusCode()).isEqualTo(500);
        assertThat(delivery.getLastError()).isEqualTo("non_2xx_response");
        assertThat(Duration.between(delivery.getLastAttemptAt(), delivery.getNextAttemptAt())).isEqualTo(Duration.ofMinutes(1));
    }

    /** Um redirecionamento poderia apontar para a rede interna: não é seguido e conta como falha. */
    @Test
    void redirectsAreNotFollowed() {
        behavior.put("/redirect", 302);
        endpoint(merchantId, "/redirect", null);
        createPayment("key-1");

        deliveryService.deliverDue();

        assertThat(received).extracting(Received::path).containsExactly("/redirect");
        assertThat(onlyDelivery().getLastStatusCode()).isEqualTo(302);
        assertThat(onlyDelivery().getLastError()).isEqualTo("non_2xx_response");
    }

    @Test
    void slowStoreTimesOut() {
        behavior.put("/slow", 0);
        endpoint(merchantId, "/slow", null);
        createPayment("key-1");

        long start = System.nanoTime();
        deliveryService.deliverDue();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(onlyDelivery().getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING); // reagendada
        assertThat(onlyDelivery().getLastError()).isEqualTo("timeout");
        assertThat(elapsedMs).isLessThan(2_900); // não esperou os 3 s da loja
    }

    @Test
    void unreachableStoreIsAConnectionFailure() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        endpointService.create(merchantId, apiKeyId, "http://127.0.0.1:" + closedPort + "/hook", null);
        createPayment("key-1");

        deliveryService.deliverDue();

        assertThat(onlyDelivery().getLastError()).isEqualTo("connection_failed");
        assertThat(onlyDelivery().getLastStatusCode()).isNull();
    }

    /** O destino virou interno depois do cadastro (ex.: o DNS mudou): o envio é recusado sem conectar. */
    @Test
    void internalDestinationIsRefusedAtDeliveryTime() {
        UUID endpointId = endpoint(merchantId, "/hook", null);
        jdbc.update("UPDATE webhook_endpoints SET url = 'https://10.0.0.1/hook' WHERE id = ?", endpointId);
        createPayment("key-1");

        deliveryService.deliverDue();

        assertThat(received).isEmpty();
        // Decisão de segurança, não falha temporária: desiste na hora, sem reagendar.
        assertThat(onlyDelivery().getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(onlyDelivery().getLastError()).isEqualTo("destination_not_allowed");
    }

    @Test
    void deliveriesOfARemovedEndpointAreCanceledAndNotSent() {
        UUID endpointId = endpoint(merchantId, "/hook", null);
        createPayment("key-1");
        endpointService.delete(merchantId, apiKeyId, endpointId);

        deliveryService.deliverDue();

        assertThat(received).isEmpty();
        assertThat(onlyDelivery().getStatus()).isEqualTo(WebhookDeliveryStatus.CANCELED);
        assertThat(onlyDelivery().getAttempts()).isZero();
    }

    @Test
    void oneFailingStoreDoesNotAffectAnother() {
        behavior.put("/broken", 500);
        UUID broken = endpoint(merchantId, "/broken", null);
        UUID healthy = endpoint(merchantId, "/healthy", null);
        createPayment("key-1");

        deliveryService.deliverDue();

        Map<UUID, WebhookDeliveryStatus> byEndpoint = deliveryRepository.findAll().stream()
                .filter(d -> d.getMerchantId().equals(merchantId))
                .collect(Collectors.toMap(WebhookDelivery::getEndpointId, WebhookDelivery::getStatus));
        assertThat(byEndpoint).containsEntry(broken, WebhookDeliveryStatus.PENDING)
                .containsEntry(healthy, WebhookDeliveryStatus.SUCCEEDED);
    }

    /** Cada tentativa conta na métrica, com o resultado e o código do erro (nunca ids como etiqueta). */
    @Test
    void deliveryAttemptsAreCountedByResultAndError() {
        behavior.put("/broken", 500);
        endpoint(merchantId, "/broken", null);
        endpoint(merchantId, "/ok", null);
        double retriesBefore = deliveries("retry_scheduled", "non_2xx_response");
        double successesBefore = deliveries("succeeded", "none");
        createPayment("key-1");

        deliveryService.deliverDue();

        assertThat(deliveries("retry_scheduled", "non_2xx_response")).isEqualTo(retriesBefore + 1);
        assertThat(deliveries("succeeded", "none")).isEqualTo(successesBefore + 1);
        assertThat(meterRegistry.find("moneyx.webhook.delivery.duration").tag("outcome", "failure").timer())
                .isNotNull();
    }

    @Autowired
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    private double deliveries(String result, String error) {
        var counter = meterRegistry.find("moneyx.webhook.deliveries").tag("result", result).tag("error", error).counter();
        return counter == null ? 0 : counter.count();
    }

    // ---------- retentativas ----------

    @Test
    void retryIsNotSentBeforeItsTimeAndSucceedsOnceTheStoreRecovers() {
        behavior.put("/flaky", 503);
        UUID endpointId = endpoint(merchantId, "/flaky", null);
        createPayment("key-1");
        deliveryService.deliverDue();

        clock.advance(Duration.ofSeconds(59));
        assertThat(deliveryService.deliverDue()).isZero(); // ainda não é hora

        behavior.remove("/flaky"); // a loja voltou
        clock.advance(Duration.ofSeconds(1));
        assertThat(deliveryService.deliverDue()).isEqualTo(1);

        assertThat(received).hasSize(2);
        assertThat(onlyDelivery().getStatus()).isEqualTo(WebhookDeliveryStatus.SUCCEEDED);
        assertThat(onlyDelivery().getAttempts()).isEqualTo(2);
        assertThat(endpointService.get(merchantId, endpointId).getFailingSince()).isNull(); // sequência encerrada
    }

    /** A agenda inteira pelo job: 9 envios reais, depois FAILED, e nada mais é enviado. */
    @Test
    void givesUpAfterNineAttempts() {
        behavior.put("/down", 500);
        endpoint(merchantId, "/down", null);
        createPayment("key-1");

        deliveryService.deliverDue();
        for (Duration wait : com.moneyx.webhook.domain.WebhookRetryPolicy.DELAYS) {
            clock.advance(wait);
            assertThat(deliveryService.deliverDue()).isEqualTo(1);
        }
        clock.advance(Duration.ofDays(1));
        assertThat(deliveryService.deliverDue()).isZero();

        assertThat(received).hasSize(9);
        assertThat(onlyDelivery().getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(onlyDelivery().getAttempts()).isEqualTo(9);
    }

    // ---------- desativação automática e reativação ----------

    @Test
    void endpointFailingForThreeDaysIsDisabledByTheSystemAndRecordedInTheTimeline() {
        behavior.put("/dead", 500);
        UUID endpointId = endpoint(merchantId, "/dead", null);
        createPayment("key-1");
        deliveryService.deliverDue(); // 1ª falha: começa a sequência

        clock.advance(Duration.ofDays(3));
        createPayment("key-2");
        deliveryService.deliverDue(); // falhas 3 dias depois: desativa

        var endpoint = endpointService.get(merchantId, endpointId);
        assertThat(endpoint.isEnabled()).isFalse();
        assertThat(endpoint.getDisabledAt()).isNotNull();

        var history = endpointService.history(merchantId, endpointId);
        assertThat(history).extracting(h -> h.getAction().name()).containsExactly("CREATED", "DISABLED");
        assertThat(history.getLast().getApiKeyId()).isNull(); // ação do sistema

        // Desativado: novos eventos não geram entregas, e as pendentes são canceladas sem envio.
        int before = received.size();
        createPayment("key-3");
        assertThat(deliveryRepository.findByEventId(lastEvent(EventType.PAYMENT_CREATED).getId())).isEmpty();
        clock.advance(Duration.ofDays(1));
        deliveryService.deliverDue();
        assertThat(received).hasSize(before);
        assertThat(deliveryRepository.findAll().stream().filter(d -> d.getMerchantId().equals(merchantId)))
                .noneMatch(d -> d.getStatus() == WebhookDeliveryStatus.PENDING);
    }

    @Test
    void enablingTheEndpointResumesDeliveriesAndIsRecordedWithTheApiKey() {
        behavior.put("/dead", 500);
        UUID endpointId = endpoint(merchantId, "/dead", null);
        createPayment("key-1");
        deliveryService.deliverDue();
        clock.advance(Duration.ofDays(3));
        createPayment("key-2");
        deliveryService.deliverDue();
        assertThat(endpointService.get(merchantId, endpointId).isEnabled()).isFalse();

        behavior.remove("/dead");
        // Sem avançar o relógio: DISABLED e ENABLED no MESMO instante. A coluna seq (V11) garante a ordem de gravação.
        UUID otherKey = UUID.randomUUID();
        endpointService.enable(merchantId, otherKey, endpointId);
        endpointService.enable(merchantId, otherKey, endpointId); // idempotente: sem segundo registro

        var history = endpointService.history(merchantId, endpointId);
        assertThat(history).extracting(h -> h.getAction().name()).containsExactly("CREATED", "DISABLED", "ENABLED");
        assertThat(history.getLast().getApiKeyId()).isEqualTo(otherKey);

        // O que importa: um evento NOVO, gerado depois da reativação, volta a ser entregue.
        received.clear();
        createPayment("key-3");
        String newEventId = lastEvent(EventType.PAYMENT_CREATED).getId().toString();
        deliveryService.deliverDue();
        assertThat(received).extracting(r -> r.header("MoneyX-Event-Id")).contains(newEventId);
    }

    @Test
    void deletedEndpointCannotBeEnabled() {
        UUID endpointId = endpoint(merchantId, "/hook", null);
        endpointService.delete(merchantId, apiKeyId, endpointId);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> endpointService.enable(merchantId, apiKeyId, endpointId))
                .isInstanceOf(com.moneyx.webhook.exception.WebhookEndpointDeletedException.class);
    }

    /**
     * Várias entregas do MESMO endpoint falhando ao mesmo tempo atualizam a sequência de falhas do endpoint.
     * O lock no endpoint serializa essas atualizações; sem ele, o @Version faria parte delas falhar.
     */
    @Test
    void concurrentFailuresOfTheSameEndpointAreAllRecorded() throws Exception {
        behavior.put("/broken", 500);
        endpoint(merchantId, "/broken", null);
        for (int i = 0; i < 20; i++) {
            createPayment("key-" + i);
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> { start.await(); return deliveryService.deliverDue(); });
            Future<Integer> second = executor.submit(() -> { start.await(); return deliveryService.deliverDue(); });
            start.countDown();
            assertThat(first.get(60, TimeUnit.SECONDS) + second.get(60, TimeUnit.SECONDS)).isEqualTo(20);
        } finally {
            executor.shutdownNow();
        }

        assertThat(deliveryRepository.findAll().stream().filter(d -> d.getMerchantId().equals(merchantId)))
                .hasSize(20)
                .allSatisfy(d -> {
                    assertThat(d.getAttempts()).isEqualTo(1);
                    assertThat(d.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
                });
    }

    // ---------- consulta e reenvio manual ----------

    @Test
    void listsDeliveriesOfAnEndpointNewestFirstWithCursorAndStatusFilter() {
        behavior.put("/mixed", 500);
        UUID endpointId = endpoint(merchantId, "/mixed", null);
        for (int i = 0; i < 3; i++) {
            createPayment("key-" + i);
        }
        deliveryService.deliverDue(); // 3 pendentes (reagendadas)

        var first = deliveryService.list(merchantId, endpointId, new WebhookDeliveryListQuery(2, null, null));
        var second = deliveryService.list(merchantId, endpointId,
                new WebhookDeliveryListQuery(2, first.deliveries().getLast().getId(), null));
        var succeeded = deliveryService.list(merchantId, endpointId,
                new WebhookDeliveryListQuery(null, null, WebhookDeliveryStatus.SUCCEEDED));

        assertThat(first.deliveries()).hasSize(2);
        assertThat(first.hasMore()).isTrue();
        assertThat(second.deliveries()).hasSize(1);
        assertThat(second.hasMore()).isFalse();
        assertThat(first.deliveries().getFirst().getCreatedAt()).isAfter(second.deliveries().getFirst().getCreatedAt());
        assertThat(succeeded.deliveries()).isEmpty();

        UUID other = newMerchant();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> deliveryService.list(other, endpointId, WebhookDeliveryListQuery.firstPage()))
                .isInstanceOf(com.moneyx.webhook.exception.WebhookEndpointNotFoundException.class);
    }

    @Test
    void manualRetrySendsAFailedDeliveryAgain() {
        UUID endpointId = endpoint(merchantId, "/hook", null);
        jdbc.update("UPDATE webhook_endpoints SET url = 'https://10.0.0.1/hook' WHERE id = ?", endpointId);
        createPayment("key-1");
        deliveryService.deliverDue();
        assertThat(onlyDelivery().getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);

        // A loja corrigiu o problema (aqui: a URL volta a apontar para a loja de teste) e pede o reenvio.
        jdbc.update("UPDATE webhook_endpoints SET url = ? WHERE id = ?", storeUrl("/hook"), endpointId);
        WebhookDelivery requeued = deliveryService.retry(merchantId, onlyDelivery().getId());
        assertThat(requeued.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);

        deliveryService.deliverDue();
        assertThat(received).hasSize(1);
        assertThat(onlyDelivery().getStatus()).isEqualTo(WebhookDeliveryStatus.SUCCEEDED);
        assertThat(onlyDelivery().getAttempts()).isEqualTo(2);
    }

    @Test
    void manualRetryIsRefusedWhenNotFailedForeignOrEndpointUnavailable() {
        behavior.put("/broken", 500);
        UUID endpointId = endpoint(merchantId, "/broken", null);
        createPayment("key-1");
        deliveryService.deliverDue();
        UUID deliveryId = onlyDelivery().getId();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> deliveryService.retry(merchantId, deliveryId))
                .isInstanceOf(com.moneyx.webhook.exception.WebhookDeliveryNotFailedException.class); // está PENDING
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> deliveryService.retry(newMerchant(), deliveryId))
                .isInstanceOf(com.moneyx.webhook.exception.WebhookDeliveryNotFoundException.class);

        jdbc.update("UPDATE webhook_deliveries SET status = 'FAILED' WHERE id = ?", deliveryId);
        endpointService.delete(merchantId, apiKeyId, endpointId);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> deliveryService.retry(merchantId, deliveryId))
                .isInstanceOf(com.moneyx.webhook.exception.WebhookEndpointUnavailableException.class);
    }

    // ---------- reserva com prazo ----------

    @Test
    void deliveryReservedByAnotherRunIsNotSentUntilTheLeaseExpires() {
        endpoint(merchantId, "/hook", null);
        createPayment("key-1");
        jdbc.update("UPDATE webhook_deliveries SET lease_until = ?, lease_token = ? WHERE merchant_id = ?",
                java.sql.Timestamp.from(START.plusSeconds(60)), UUID.randomUUID(), merchantId);

        assertThat(deliveryService.deliverDue()).isZero();
        assertThat(received).isEmpty();

        clock.advance(Duration.ofSeconds(61)); // a outra execução "morreu": a reserva venceu
        assertThat(deliveryService.deliverDue()).isEqualTo(1);
        assertThat(received).hasSize(1);
        assertThat(onlyDelivery().getStatus()).isEqualTo(WebhookDeliveryStatus.SUCCEEDED);
    }

    /** Duas execuções simultâneas (ou duas instâncias): cada entrega é enviada exatamente uma vez. */
    @Test
    void concurrentRunsSendEachDeliveryExactlyOnce() throws Exception {
        endpoint(merchantId, "/hook", null);
        for (int i = 0; i < 25; i++) {
            createPayment("key-" + i);
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> { start.await(); return deliveryService.deliverDue(); });
            Future<Integer> second = executor.submit(() -> { start.await(); return deliveryService.deliverDue(); });
            start.countDown();
            assertThat(first.get(60, TimeUnit.SECONDS) + second.get(60, TimeUnit.SECONDS)).isEqualTo(25);
        } finally {
            executor.shutdownNow();
        }

        Set<String> eventIds = received.stream().map(r -> r.header("MoneyX-Event-Id")).collect(Collectors.toSet());
        assertThat(received).hasSize(25);
        assertThat(eventIds).hasSize(25);
    }

    // ---------- auxiliares ----------

    private UUID endpoint(UUID owner, String path, List<String> events) {
        return endpointService.create(owner, apiKeyId, storeUrl(path), events).endpoint().getId();
    }

    private Payment createPayment(String idempotencyKey) {
        Payment payment = paymentService.create(merchantId, idempotencyKey, ORDER).payment();
        clock.advance(Duration.ofMillis(1));
        return payment;
    }

    private Event lastEvent(EventType type) {
        return eventService.list(merchantId, new com.moneyx.event.service.EventListQuery(1, null, type)).events().getFirst();
    }

    private WebhookDelivery onlyDelivery() {
        List<WebhookDelivery> mine = deliveryRepository.findAll().stream()
                .filter(d -> d.getMerchantId().equals(merchantId)).toList();
        assertThat(mine).hasSize(1);
        return mine.getFirst();
    }

    private int countDeliveries() {
        return jdbc.queryForObject("SELECT count(*) FROM webhook_deliveries WHERE merchant_id = ?", Integer.class, merchantId);
    }

    private UUID newMerchant() {
        String document = String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
        Merchant merchant = merchantRepository.saveAndFlush(
                new Merchant("Delivery Test LTDA", document, "delivery-" + UUID.randomUUID() + "@moneyx.dev"));
        createdMerchants.add(merchant.getId());
        return merchant.getId();
    }
}
