package com.moneyx.payment.provider.mercadopago;

import com.moneyx.payment.domain.PixBrCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cliente do Mercado Pago contra um servidor HTTP FALSO local (formato das respostas copiado do sandbox real em
 * 2026-11-24). Nenhuma chamada sai da máquina.
 */
class MercadoPagoClientTest {

    private static final String TOKEN = "APP_USR-test-token-that-must-never-leak";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private HttpServer server;
    private final List<Recorded> requests = new CopyOnWriteArrayList<>();
    private final AtomicReference<Responder> responder = new AtomicReference<>();

    record Recorded(String method, String path, Map<String, List<String>> headers, String body) {
        String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                    .map(e -> e.getValue().getFirst()).findFirst().orElse(null);
        }
    }

    @FunctionalInterface
    interface Responder {
        void respond(HttpExchange exchange) throws IOException;
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Recorded(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    Map.copyOf(exchange.getRequestHeaders()), body));
            responder.get().respond(exchange);
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private MercadoPagoClient client(String testPayerFirstName, Duration requestTimeout) {
        return new MercadoPagoClient(new MercadoPagoProperties(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), TOKEN,
                Duration.ofSeconds(2), requestTimeout, "test@testuser.com", testPayerFirstName), JSON);
    }

    private MercadoPagoClient client() {
        return client("", Duration.ofSeconds(5));
    }

    private static void reply(HttpExchange exchange, int status, String body, Map<String, String> headers) throws IOException {
        headers.forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    private void respondWith(int status, String body) {
        responder.set(exchange -> reply(exchange, status, body, Map.of("Content-Type", "application/json")));
    }

    /** Order no formato devolvido pelo sandbox, com um BR Code válido (CRC correto) contendo o txid. */
    private static String order(String status, String statusDetail, String total) {
        String qr = PixBrCode.build("b76aa9c2-2ec4-4110-954e-ebfe34f05b61", "TESTUSER79782994336500902", "Osasco",
                new java.math.BigDecimal(total).movePointRight(2).longValueExact(), "mpqrinter190381238073");
        return """
                {"id":"ORDTST01M87Y05CFSENQ5XA5KVTDPCYY","type":"online","processing_mode":"automatic",
                 "external_reference":"ab6288b6-6cef-4871-9cc7-aa48ee56e73e","total_amount":"%s","total_paid_amount":"0.00",
                 "status":"%s","status_detail":"%s","currency":"BRL","country_code":"BRA",
                 "transactions":{"payments":[{"id":"PAY01M87Y05CVQWG69352182T9G12","amount":"%s","reference_id":"x",
                   "status":"%s","status_detail":"%s","expiration_time":"PT30M",
                   "date_of_expiration":"2026-11-25T23:24:33.914+00:00",
                   "payment_method":{"id":"pix","type":"bank_transfer","qr_code":"%s","qr_code_base64":"iVBOR...",
                     "ticket_url":"https://www.mercadopago.com.br/sandbox/payments/1/ticket"}}]}}
                """.formatted(total, status, statusDetail, total, status, statusDetail, qr);
    }

    private static CreatePixOrder create(long amount, String description) {
        return new CreatePixOrder("moneyx-create-" + UUID.fromString("ab6288b6-6cef-4871-9cc7-aa48ee56e73e"),
                "ab6288b6-6cef-4871-9cc7-aa48ee56e73e", amount, description, Duration.ofMinutes(30));
    }

    // ---------- criar ----------

    @Test
    void createSendsTheOrderWithTokenIdempotencyKeyAndAmountsAsText() {
        respondWith(201, order("action_required", "waiting_transfer", "49.90"));

        MercadoPagoOrder created = client().createPixOrder(create(4990, "Pedido 123"));

        Recorded sent = requests.getFirst();
        assertThat(sent.method()).isEqualTo("POST");
        assertThat(sent.path()).isEqualTo("/v1/orders");
        assertThat(sent.header("Authorization")).isEqualTo("Bearer " + TOKEN);
        assertThat(sent.header("X-Idempotency-Key")).isEqualTo("moneyx-create-ab6288b6-6cef-4871-9cc7-aa48ee56e73e");
        assertThat(sent.header("Content-Type")).isEqualTo("application/json");

        JsonNode body = JSON.readTree(sent.body());
        assertThat(body.path("type").asString()).isEqualTo("online");
        assertThat(body.path("external_reference").asString()).isEqualTo("ab6288b6-6cef-4871-9cc7-aa48ee56e73e");
        assertThat(body.path("total_amount").isString()).isTrue();          // TEXTO, nunca número
        assertThat(body.path("total_amount").asString()).isEqualTo("49.90");
        assertThat(body.path("description").asString()).isEqualTo("Pedido 123");
        assertThat(body.path("payer").path("email").asString()).isEqualTo("test@testuser.com");
        assertThat(body.path("payer").has("first_name")).isFalse();         // sem APRO fora do sandbox
        JsonNode payment = body.path("transactions").path("payments").path(0);
        assertThat(payment.path("amount").asString()).isEqualTo("49.90");
        assertThat(payment.path("expiration_time").asString()).isEqualTo("PT30M");
        assertThat(payment.path("payment_method").path("id").asString()).isEqualTo("pix");
        assertThat(payment.path("payment_method").path("type").asString()).isEqualTo("bank_transfer");

        assertThat(created.id()).isEqualTo("ORDTST01M87Y05CFSENQ5XA5KVTDPCYY");
        assertThat(created.status()).isEqualTo("action_required");
        assertThat(created.statusDetail()).isEqualTo("waiting_transfer");
        assertThat(created.totalAmount()).isEqualTo(4990);
        assertThat(created.paymentId()).isEqualTo("PAY01M87Y05CVQWG69352182T9G12");
        assertThat(created.paymentStatus()).isEqualTo("action_required");
        assertThat(created.expiresAt()).isEqualTo(Instant.parse("2026-11-25T23:24:33.914Z"));
        assertThat(created.ticketUrl()).startsWith("https://");
        assertThat(PixBrCode.txidOf(created.qrCode())).contains("mpqrinter190381238073");
    }

    @Test
    void sandboxSendsTheTestPayerFirstNameAndOmitsAnEmptyDescription() {
        respondWith(201, order("action_required", "waiting_transfer", "5.00"));

        client("APRO", Duration.ofSeconds(5)).createPixOrder(create(500, null));

        JsonNode body = JSON.readTree(requests.getFirst().body());
        assertThat(body.path("payer").path("first_name").asString()).isEqualTo("APRO");
        assertThat(body.has("description")).isFalse();
    }

    @Test
    void expirationIsSentAsAnIsoDuration() {
        respondWith(201, order("action_required", "waiting_transfer", "5.00"));

        client().createPixOrder(new CreatePixOrder("k", "ref", 500, null, Duration.ofSeconds(1799)));

        JsonNode payment = JSON.readTree(requests.getFirst().body()).path("transactions").path("payments").path(0);
        assertThat(payment.path("expiration_time").asString()).isEqualTo("PT29M59S");
    }

    // ---------- consultar e cancelar ----------

    @Test
    void getReadsTheCurrentStatus() {
        respondWith(200, order("processed", "accredited", "5.00"));

        MercadoPagoOrder order = client().getOrder("ORDTST01M87Y05CFSENQ5XA5KVTDPCYY");

        assertThat(requests.getFirst().method()).isEqualTo("GET");
        assertThat(requests.getFirst().path()).isEqualTo("/v1/orders/ORDTST01M87Y05CFSENQ5XA5KVTDPCYY");
        assertThat(requests.getFirst().header("X-Idempotency-Key")).isNull();
        assertThat(order.status()).isEqualTo("processed");
        assertThat(order.paymentStatusDetail()).isEqualTo("accredited");
    }

    @Test
    void cancelPostsToTheCancelPathWithAnIdempotencyKey() {
        respondWith(200, order("canceled", "canceled", "5.00"));

        MercadoPagoOrder order = client().cancelOrder("ORDTST01M87Y05CFSENQ5XA5KVTDPCYY", "moneyx-cancel-1");

        assertThat(requests.getFirst().method()).isEqualTo("POST");
        assertThat(requests.getFirst().path()).isEqualTo("/v1/orders/ORDTST01M87Y05CFSENQ5XA5KVTDPCYY/cancel");
        assertThat(requests.getFirst().header("X-Idempotency-Key")).isEqualTo("moneyx-cancel-1");
        assertThat(order.status()).isEqualTo("canceled");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../payments", "ORD/../x", "ORD?x=1", "", "ORD 1"})
    void orderIdThatIsNotAPlainIdIsRejectedWithoutAnyRequest(String id) {
        respondWith(200, order("processed", "accredited", "5.00"));

        assertThatThrownBy(() -> client().getOrder(id)).isInstanceOf(IllegalArgumentException.class);
        assertThat(requests).isEmpty();
    }

    // ---------- erros ----------

    @Test
    void notFoundIsAPermanentErrorWithTheMercadoPagoCode() {
        respondWith(404, "{\"errors\":[{\"code\":\"order_not_found\",\"message\":\"Order not found.\"}]}");

        assertThatThrownBy(() -> client().getOrder("ORD1"))
                .isInstanceOfSatisfying(MercadoPagoException.class, ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(404);
                    assertThat(ex.getCode()).isEqualTo("order_not_found");
                    assertThat(ex.isRetryable()).isFalse();
                });
    }

    @Test
    void alreadyCanceledIsReportedWithItsCode() {
        respondWith(409, "{\"errors\":[{\"code\":\"order_already_canceled\",\"message\":\"the order is already canceled.\"}]}");

        assertThatThrownBy(() -> client().cancelOrder("ORD1", "k"))
                .isInstanceOfSatisfying(MercadoPagoException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("order_already_canceled");
                    assertThat(ex.isRetryable()).isFalse();
                });
    }

    @Test
    void tooManyRequestsIsRetryableAndCarriesRetryAfter() {
        responder.set(exchange -> reply(exchange, 429,
                "{\"errors\":[{\"code\":\"too_many_requests\",\"message\":\"slow down\"}]}", Map.of("Retry-After", "7")));

        assertThatThrownBy(() -> client().getOrder("ORD1"))
                .isInstanceOfSatisfying(MercadoPagoException.class, ex -> {
                    assertThat(ex.isRetryable()).isTrue();
                    assertThat(ex.getRetryAfter()).isEqualTo(Duration.ofSeconds(7));
                    assertThat(ex.getCode()).isEqualTo("too_many_requests");
                });
    }

    @Test
    void serverErrorWithoutJsonIsRetryable() {
        respondWith(502, "<html>bad gateway</html>");

        assertThatThrownBy(() -> client().getOrder("ORD1"))
                .isInstanceOfSatisfying(MercadoPagoException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("http_502");
                    assertThat(ex.isRetryable()).isTrue();
                });
    }

    @Test
    void slowResponseBecomesARetryableTimeout() {
        responder.set(exchange -> {
            try {
                Thread.sleep(1_500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            reply(exchange, 200, order("processed", "accredited", "5.00"), Map.of());
        });

        assertThatThrownBy(() -> client("", Duration.ofMillis(300)).getOrder("ORD1"))
                .isInstanceOfSatisfying(MercadoPagoException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("timeout");
                    assertThat(ex.isRetryable()).isTrue();
                    assertThat(ex.getHttpStatus()).isZero();
                });
    }

    @Test
    void refusedConnectionIsRetryable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        MercadoPagoClient client = new MercadoPagoClient(new MercadoPagoProperties(
                URI.create("http://127.0.0.1:" + closedPort), TOKEN, Duration.ofSeconds(2), Duration.ofSeconds(2),
                "test@testuser.com", ""), JSON);

        assertThatThrownBy(() -> client.getOrder("ORD1"))
                .isInstanceOfSatisfying(MercadoPagoException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("connection_failed");
                    assertThat(ex.isRetryable()).isTrue();
                });
    }

    /** Um redirecionamento levaria o token para outro endereço: não é seguido. */
    @Test
    void redirectsAreNotFollowed() {
        responder.set(exchange -> reply(exchange, 302, "", Map.of("Location", "http://127.0.0.1:1/steal")));

        assertThatThrownBy(() -> client().getOrder("ORD1"))
                .isInstanceOfSatisfying(MercadoPagoException.class, ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(302);
                    assertThat(ex.isRetryable()).isFalse();
                });
        assertThat(requests).hasSize(1);
    }

    @Test
    void successfulResponseThatIsNotJsonIsRejected() {
        respondWith(200, "not json");

        assertThatThrownBy(() -> client().getOrder("ORD1"))
                .isInstanceOfSatisfying(MercadoPagoException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("invalid_response"));
    }

    @Test
    void amountWithFractionOfCentInTheResponseIsRejectedNotRounded() {
        respondWith(200, order("processed", "accredited", "5.00").replace("\"total_amount\":\"5.00\"", "\"total_amount\":\"5.001\""));

        assertThatThrownBy(() -> client().getOrder("ORD1"))
                .isInstanceOfSatisfying(MercadoPagoException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("invalid_response"));
    }

    // ---------- segredo e conversões ----------

    @Test
    void tokenNeverAppearsInErrorsOrToString() {
        respondWith(401, "{\"errors\":[{\"code\":\"invalid_credentials\",\"message\":\"bad token " + TOKEN + "\"}]}");
        MercadoPagoClient client = client();

        assertThatThrownBy(() -> client.getOrder("ORD1")).isInstanceOfSatisfying(MercadoPagoException.class, ex -> {
            assertThat(ex.getCode()).isEqualTo("invalid_credentials");
            assertThat(ex.isRetryable()).isFalse();
            // A resposta de erro ECOOU o token: ele não pode seguir na mensagem (que pode ir para um log).
            assertThat(ex.getMessage()).doesNotContain(TOKEN).contains("***");
        });
        assertThat(client.toString()).doesNotContain(TOKEN);
        assertThat(new MercadoPagoProperties(URI.create("http://x"), TOKEN, Duration.ofSeconds(1), Duration.ofSeconds(1),
                "e", "").toString()).doesNotContain(TOKEN);
    }

    @Test
    void missingTokenIsRejectedAtStartup() {
        assertThatThrownBy(() -> new MercadoPagoClient(new MercadoPagoProperties(URI.create("http://x"), " ",
                Duration.ofSeconds(1), Duration.ofSeconds(1), "e", ""), JSON))
                .hasMessageContaining("MERCADOPAGO_ACCESS_TOKEN");
    }

    @ParameterizedTest
    @CsvSource({"1, 0.01", "100, 1.00", "4990, 49.90", "10000000, 100000.00"})
    void centsAreFormattedExactly(long cents, String text) {
        assertThat(MercadoPagoClient.formatAmount(cents)).isEqualTo(text);
        assertThat(MercadoPagoClient.parseAmount(text)).isEqualTo(cents);
    }

    @Test
    void parsingAcceptsFewerDecimalsButNeverFractionsOfCent() {
        assertThat(MercadoPagoClient.parseAmount("4.9")).isEqualTo(490);
        assertThat(MercadoPagoClient.parseAmount("5")).isEqualTo(500);
        assertThatThrownBy(() -> MercadoPagoClient.parseAmount("4.999")).isInstanceOf(MercadoPagoException.class);
        assertThatThrownBy(() -> MercadoPagoClient.parseAmount("abc")).isInstanceOf(MercadoPagoException.class);
    }

    @Test
    void creationKeyIsDeterministicPerPayment() {
        UUID id = UUID.randomUUID();

        assertThat(MercadoPagoClient.creationKeyFor(id)).isEqualTo(MercadoPagoClient.creationKeyFor(id));
        assertThat(MercadoPagoClient.creationKeyFor(id).length()).isLessThanOrEqualTo(128);
    }
}
