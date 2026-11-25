package com.moneyx.payment.provider.mercadopago;

import com.moneyx.payment.provider.PixChargeRequest;
import com.moneyx.payment.provider.PixProviderException;
import com.moneyx.payment.provider.ProviderChargeStatus;
import com.moneyx.payment.provider.ProviderChargeStatus.State;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Consulta, cancelamento e corpo gravado do provedor Mercado Pago (Etapa 7d), contra um servidor HTTP falso. */
class MercadoPagoReconciliationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID PAYMENT_ID = UUID.fromString("ab6288b6-6cef-4871-9cc7-aa48ee56e73e");
    private static final String ORDER_ID = "ORDTST01M87Y05CFSENQ5XA5KVTDPCYY";

    private HttpServer server;
    /** "MÉTODO caminho" → {status, corpo} */
    private final Map<String, Object[]> routes = new ConcurrentHashMap<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> keys = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String route = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
            requests.add(route);
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            keys.add(exchange.getRequestHeaders().getFirst("X-Idempotency-Key"));
            Object[] answer = routes.getOrDefault(route, new Object[]{404, "{\"errors\":[{\"code\":\"order_not_found\"}]}"});
            byte[] bytes = ((String) answer[1]).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders((int) answer[0], bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private MercadoPagoPixProvider provider(String testPayer) {
        return new MercadoPagoPixProvider(new MercadoPagoProperties(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "APP_USR-test",
                Duration.ofSeconds(2), Duration.ofSeconds(5), "test@testuser.com", testPayer), JSON);
    }

    private static String order(String status, String detail, String total) {
        return """
                {"id":"%s","external_reference":"%s","total_amount":"%s","status":"%s","status_detail":"%s",
                 "transactions":{"payments":[{"id":"PAY01","amount":"%s","status":"%s","status_detail":"%s",
                   "payment_method":{"id":"pix","type":"bank_transfer"}}]}}
                """.formatted(ORDER_ID, PAYMENT_ID, total, status, detail, total, status, detail);
    }

    @ParameterizedTest
    @CsvSource({
            "processed, accredited, PAID",
            "action_required, waiting_transfer, OPEN",
            "created, created, OPEN",
            "processing, in_process, IN_PROGRESS",
            "canceled, canceled, CLOSED",
            "expired, expired, EXPIRED",
            "canceled, expired, EXPIRED",
            "failed, expired, EXPIRED",
            "failed, rejected, CLOSED",
            "refunded, refunded, UNKNOWN",
            "something_new, x, UNKNOWN"})
    void orderStatusesAreTranslated(String status, String detail, State expected) {
        routes.put("GET /v1/orders/" + ORDER_ID, new Object[]{200, order(status, detail, "49.90")});

        ProviderChargeStatus result = provider("").fetchCharge(ORDER_ID);

        assertThat(result.state()).isEqualTo(expected);
        assertThat(result.amount()).isEqualTo(4990);
        assertThat(result.reference()).isEqualTo(PAYMENT_ID.toString());
        assertThat(result.providerOrderId()).isEqualTo(ORDER_ID);
    }

    @Test
    void cancelReturnsTheCanceledOrderAndUsesAStableKey() {
        routes.put("POST /v1/orders/" + ORDER_ID + "/cancel", new Object[]{200, order("canceled", "canceled", "49.90")});

        ProviderChargeStatus result = provider("").cancelCharge(PAYMENT_ID, ORDER_ID);

        assertThat(result.state()).isEqualTo(State.CLOSED);
        assertThat(keys).containsExactly("moneyx-cancel-" + PAYMENT_ID);
    }

    @Test
    void aRefusedCancelLooksUpTheRealStateSoAPaidOrderIsReportedAsPaid() {
        routes.put("POST /v1/orders/" + ORDER_ID + "/cancel",
                new Object[]{409, "{\"errors\":[{\"code\":\"cannot_cancel_order\",\"message\":\"no\"}]}"});
        routes.put("GET /v1/orders/" + ORDER_ID, new Object[]{200, order("processed", "accredited", "49.90")});

        ProviderChargeStatus result = provider("").cancelCharge(PAYMENT_ID, ORDER_ID);

        assertThat(result.state()).isEqualTo(State.PAID);
        assertThat(requests).containsExactly("POST /v1/orders/" + ORDER_ID + "/cancel", "GET /v1/orders/" + ORDER_ID);
    }

    @Test
    void providerFailuresKeepTheirRetryableFlag() {
        routes.put("GET /v1/orders/" + ORDER_ID, new Object[]{500, "{}"});
        assertThatThrownBy(() -> provider("").fetchCharge(ORDER_ID))
                .isInstanceOfSatisfying(PixProviderException.class, e -> assertThat(e.isRetryable()).isTrue());

        routes.put("POST /v1/orders/" + ORDER_ID + "/cancel", new Object[]{503, "{}"});
        assertThatThrownBy(() -> provider("").cancelCharge(PAYMENT_ID, ORDER_ID))
                .isInstanceOfSatisfying(PixProviderException.class, e -> assertThat(e.isRetryable()).isTrue());

        routes.remove("GET /v1/orders/" + ORDER_ID); // 404 order_not_found
        assertThatThrownBy(() -> provider("").fetchCharge(ORDER_ID))
                .isInstanceOfSatisfying(PixProviderException.class, e -> assertThat(e.isRetryable()).isFalse());
    }

    @Test
    void thePreparedBodyIsSentByteForByteEvenIfTheConfigurationChanged() {
        PixChargeRequest request = new PixChargeRequest(PAYMENT_ID, 4990, Duration.ofMinutes(30), "Pedido");
        String prepared = provider("APRO").prepareCharge(request);
        assertThat(prepared).contains("\"first_name\":\"APRO\"").contains("\"external_reference\":\"" + PAYMENT_ID + "\"");
        routes.put("POST /v1/orders", new Object[]{201, order("processed", "accredited", "49.90")});

        // a aplicação reiniciou SEM o pagador de teste; a repetição usa o corpo gravado
        PixChargeRequest retry = new PixChargeRequest(PAYMENT_ID, 4990, Duration.ofMinutes(30), "Pedido", prepared);
        assertThatThrownBy(() -> provider("").createCharge(retry))
                .isInstanceOfSatisfying(PixProviderException.class, e -> {
                    // order já paga, sem BR Code: a exceção traz o estado para o MoneyX marcar PAID
                    assertThat(e.getCode()).isEqualTo("missing_qr_code");
                    assertThat(e.getExisting().state()).isEqualTo(State.PAID);
                });

        assertThat(bodies).containsExactly(prepared);
        assertThat(keys).containsExactly("moneyx-create-" + PAYMENT_ID);
    }
}
