package com.moneyx.payment.provider.mercadopago;

import com.moneyx.payment.domain.PixBrCode;
import com.moneyx.payment.domain.PixCharge;
import com.moneyx.payment.provider.PixChargeRequest;
import com.moneyx.payment.provider.PixProviderException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Provedor Mercado Pago contra um servidor HTTP FALSO local (formato das respostas copiado do sandbox real). */
class MercadoPagoPixProviderTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID PAYMENT_ID = UUID.fromString("ab6288b6-6cef-4871-9cc7-aa48ee56e73e");
    private static final String VALID_QR = PixBrCode.build("b76aa9c2-2ec4-4110-954e-ebfe34f05b61", "TESTUSER", "Osasco",
            4990, "mpqrinter190381238073");

    private HttpServer server;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> keys = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> response = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(201);

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            keys.add(exchange.getRequestHeaders().getFirst("X-Idempotency-Key"));
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private MercadoPagoPixProvider provider() {
        return new MercadoPagoPixProvider(new MercadoPagoProperties(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "APP_USR-test",
                Duration.ofSeconds(2), Duration.ofSeconds(5), "test@testuser.com", ""), JSON);
    }

    private void order(String total, String externalReference, String qrCode) {
        response.set("""
                {"id":"ORDTST01M87Y05CFSENQ5XA5KVTDPCYY","external_reference":"%s","total_amount":"%s",
                 "status":"action_required","status_detail":"waiting_transfer",
                 "transactions":{"payments":[{"id":"PAY01M87Y05CVQWG69352182T9G12","amount":"%s",
                   "status":"action_required","status_detail":"waiting_transfer",
                   "date_of_expiration":"2026-11-25T00:30:01.250+00:00",
                   "payment_method":{"id":"pix","type":"bank_transfer"%s}}]}}
                """.formatted(externalReference, total, total, qrCode == null ? "" : ",\"qr_code\":\"" + qrCode + "\""));
    }

    private static PixChargeRequest request(String description) {
        return new PixChargeRequest(PAYMENT_ID, 4990, Duration.ofMinutes(30), description);
    }

    @Test
    void createsTheOrderAndReturnsTheChargeWithTheProviderData() {
        order("49.90", PAYMENT_ID.toString(), VALID_QR);

        PixCharge charge = provider().createCharge(request("Pedido 123"));

        assertThat(charge.provider()).isEqualTo("mercadopago");
        assertThat(charge.txid()).isEqualTo("mpqrinter190381238073");
        assertThat(charge.copyPaste()).isEqualTo(VALID_QR);
        assertThat(charge.providerOrderId()).isEqualTo("ORDTST01M87Y05CFSENQ5XA5KVTDPCYY");
        assertThat(charge.providerPaymentId()).isEqualTo("PAY01M87Y05CVQWG69352182T9G12");
        assertThat(charge.expiresAt()).isEqualTo(Instant.parse("2026-11-25T00:30:01.250Z"));

        JsonNode sent = JSON.readTree(bodies.getFirst());
        assertThat(keys.getFirst()).isEqualTo(MercadoPagoClient.creationKeyFor(PAYMENT_ID));
        assertThat(sent.path("external_reference").asString()).isEqualTo(PAYMENT_ID.toString());
        assertThat(sent.path("total_amount").asString()).isEqualTo("49.90");
        assertThat(sent.path("transactions").path("payments").path(0).path("expiration_time").asString()).isEqualTo("PT30M");
    }

    /** Repetir o mesmo pedido manda EXATAMENTE o mesmo corpo e a mesma chave (condição para o MP devolver a mesma order). */
    @Test
    void repeatingTheSameRequestSendsTheSameBodyAndKey() {
        order("49.90", PAYMENT_ID.toString(), VALID_QR);

        provider().createCharge(request("Pedido 123"));
        provider().createCharge(request("Pedido 123"));

        assertThat(bodies.get(1)).isEqualTo(bodies.get(0));
        assertThat(keys.get(1)).isEqualTo(keys.get(0));
    }

    @Test
    void descriptionIsCutToTheMercadoPagoLimit() {
        order("49.90", PAYMENT_ID.toString(), VALID_QR);

        provider().createCharge(request("x".repeat(255)));

        assertThat(JSON.readTree(bodies.getFirst()).path("description").asString()).hasSize(150);
    }

    @Test
    void orderWithAnotherAmountIsRejectedPermanently() {
        order("49.91", PAYMENT_ID.toString(), VALID_QR);

        assertThatThrownBy(() -> provider().createCharge(request(null)))
                .isInstanceOfSatisfying(PixProviderException.class, ex -> {
                    assertThat(ex.isRetryable()).isFalse();
                    assertThat(ex.getCode()).isEqualTo("amount_mismatch");
                });
    }

    @Test
    void orderOfAnotherReferenceIsRejectedPermanently() {
        order("49.90", UUID.randomUUID().toString(), VALID_QR);

        assertThatThrownBy(() -> provider().createCharge(request(null)))
                .isInstanceOfSatisfying(PixProviderException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("reference_mismatch"));
    }

    @Test
    void orderWithoutBrCodeIsATemporaryFailure() {
        order("49.90", PAYMENT_ID.toString(), null);

        assertThatThrownBy(() -> provider().createCharge(request(null)))
                .isInstanceOfSatisfying(PixProviderException.class, ex -> {
                    assertThat(ex.isRetryable()).isTrue();
                    assertThat(ex.getCode()).isEqualTo("missing_qr_code");
                });
    }

    @Test
    void brCodeWithAWrongCrcIsRejectedPermanently() {
        order("49.90", PAYMENT_ID.toString(), VALID_QR.substring(0, VALID_QR.length() - 4) + "0000");

        assertThatThrownBy(() -> provider().createCharge(request(null)))
                .isInstanceOfSatisfying(PixProviderException.class, ex -> {
                    assertThat(ex.isRetryable()).isFalse();
                    assertThat(ex.getCode()).isEqualTo("invalid_qr_code");
                });
    }

    @Test
    void mercadoPagoErrorsKeepTheirRetryability() {
        status.set(503);
        response.set("{\"errors\":[{\"code\":\"service_unavailable\",\"message\":\"down\"}]}");
        assertThatThrownBy(() -> provider().createCharge(request(null)))
                .isInstanceOfSatisfying(PixProviderException.class, ex -> {
                    assertThat(ex.isRetryable()).isTrue();
                    assertThat(ex.getCode()).isEqualTo("service_unavailable");
                });

        status.set(400);
        response.set("{\"errors\":[{\"code\":\"property_value\",\"message\":\"bad\"}]}");
        assertThatThrownBy(() -> provider().createCharge(request(null)))
                .isInstanceOfSatisfying(PixProviderException.class, ex -> {
                    assertThat(ex.isRetryable()).isFalse();
                    assertThat(ex.getCode()).isEqualTo("property_value");
                });
    }

    /**
     * 409 idempotency_key_already_used: JÁ EXISTE uma order com esta chave (o corpo mudou entre as tentativas). Não é
     * "nada foi criado": se fosse tratado como definitivo, o MoneyX apagaria o pagamento e a order (talvez paga) ficaria
     * sem dono. Bug visto no teste ponta a ponta.
     */
    @Test
    void idempotencyConflictMeansTheOrderMayExistSoItIsNeverPermanent() {
        status.set(409);
        response.set("{\"errors\":[{\"code\":\"idempotency_key_already_used\",\"message\":\"X-Idempotency-Key already used.\"}]}");

        assertThatThrownBy(() -> provider().createCharge(request(null)))
                .isInstanceOfSatisfying(PixProviderException.class, ex -> {
                    assertThat(ex.isRetryable()).isTrue();
                    assertThat(ex.getCode()).isEqualTo("idempotency_key_already_used");
                });
    }

    @Test
    void providerDoesNotStartWithoutTheToken() {
        assertThatThrownBy(() -> new MercadoPagoPixProvider(new MercadoPagoProperties(URI.create("http://x"), "",
                Duration.ofSeconds(1), Duration.ofSeconds(1), "e", ""), JSON))
                .hasMessageContaining("MERCADOPAGO_ACCESS_TOKEN");
    }
}
