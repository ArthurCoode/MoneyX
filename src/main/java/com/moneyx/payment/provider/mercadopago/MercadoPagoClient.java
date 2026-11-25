package com.moneyx.payment.provider.mercadopago;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.UUID;

/**
 * Cliente HTTP da API de Orders do Mercado Pago, só para Pix. HttpClient do JDK + Jackson (sem SDK, sem dependência
 * nova).
 *
 * Comportamentos conferidos no sandbox em 2026-11-24 (ver PROGRESS.md):
 * - POST /v1/orders exige X-Idempotency-Key; a mesma chave com o mesmo corpo devolve a MESMA order (201);
 * - valores são TEXTO com 2 casas ("4.99"): convertidos daqui para centavos e de volta, sem double;
 * - o vencimento só pode ser enviado como duração (expiration_time); o MP devolve o instante (date_of_expiration);
 * - POST /v1/orders/{id}/cancel: 200; de novo, 409 order_already_canceled; GET de id inexistente, 404 order_not_found.
 *
 * Segurança: o token vai só no header Authorization; nunca em mensagem de erro, log ou toString. Redirecionamentos
 * não são seguidos (um 3xx levaria o token para outro lugar).
 */
public class MercadoPagoClient {

    static final String ORDERS = "/v1/orders";

    private final MercadoPagoProperties properties;
    private final JsonMapper json;
    private final HttpClient http;

    public MercadoPagoClient(MercadoPagoProperties properties, JsonMapper json) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.json = Objects.requireNonNull(json, "json must not be null");
        if (properties.accessToken() == null || properties.accessToken().isBlank()) {
            throw new IllegalArgumentException("Mercado Pago access token is missing (MERCADOPAGO_ACCESS_TOKEN)");
        }
        this.http = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Cria uma order Pix. Repetível com a mesma idempotencyKey (devolve a mesma order). */
    public MercadoPagoOrder createPixOrder(CreatePixOrder request) {
        return createPixOrder(request.idempotencyKey(), orderBody(request));
    }

    /**
     * Envia um corpo já montado (orderBody) e gravado. O MP compara a repetição BYTE A BYTE com a primeira chamada:
     * reenviar o corpo gravado devolve a mesma order mesmo que a configuração tenha mudado desde então.
     */
    public MercadoPagoOrder createPixOrder(String idempotencyKey, String body) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(body, "body must not be null");
        return parseOrder(send("POST", ORDERS, body, idempotencyKey));
    }

    /** Monta o corpo JSON da criação (sem chamar a rede). */
    public String orderBody(CreatePixOrder request) {
        ObjectNode body = json.createObjectNode();
        body.put("type", "online");
        body.put("external_reference", request.externalReference());
        body.put("total_amount", formatAmount(request.amount()));
        if (request.description() != null && !request.description().isBlank()) {
            body.put("description", request.description());
        }
        ObjectNode payer = body.putObject("payer");
        payer.put("email", properties.payerEmail());
        if (properties.testPayerFirstName() != null && !properties.testPayerFirstName().isBlank()) {
            payer.put("first_name", properties.testPayerFirstName());
        }
        ObjectNode payment = body.putObject("transactions").putArray("payments").addObject();
        payment.put("amount", formatAmount(request.amount()));
        payment.put("expiration_time", request.expiresIn().toString()); // ISO-8601: PT30M, PT24H, PT29M59S
        ObjectNode method = payment.putObject("payment_method");
        method.put("id", "pix");
        method.put("type", "bank_transfer");
        return body.toString();
    }

    /** Estado atual da order. 404 vira MercadoPagoException com código "order_not_found". */
    public MercadoPagoOrder getOrder(String orderId) {
        return parseOrder(send("GET", ORDERS + "/" + pathSegment(orderId), null, null));
    }

    /**
     * Cancela a order (só possível em action_required/created). A chave de idempotência é obrigatória no MP; um
     * cancelamento repetido devolve 409 order_already_canceled, que o chamador trata como "já cancelada".
     */
    public MercadoPagoOrder cancelOrder(String orderId, String idempotencyKey) {
        return parseOrder(send("POST", ORDERS + "/" + pathSegment(orderId) + "/cancel", null, idempotencyKey));
    }

    // ---------- HTTP ----------

    private JsonNode send(String method, String path, String body, String idempotencyKey) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(properties.baseUrl().resolve(path))
                .timeout(properties.requestTimeout())
                .header("Authorization", "Bearer " + properties.accessToken())
                .header("Accept", "application/json");
        if (idempotencyKey != null) {
            builder.header("X-Idempotency-Key", idempotencyKey);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new MercadoPagoException(0, "timeout", true, null, method + " " + path + " timed out");
        } catch (ConnectException e) {
            throw new MercadoPagoException(0, "connection_failed", true, null, method + " " + path + " connection failed");
        } catch (IOException e) {
            throw new MercadoPagoException(0, "io_error", true, null, method + " " + path + " failed: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MercadoPagoException(0, "interrupted", true, null, method + " " + path + " interrupted");
        }

        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            try {
                return json.readTree(response.body());
            } catch (JacksonException e) {
                throw new MercadoPagoException(status, "invalid_response", false, null,
                        method + " " + path + " returned a body that is not JSON");
            }
        }
        throw errorOf(method, path, response);
    }

    private MercadoPagoException errorOf(String method, String path, HttpResponse<String> response) {
        int status = response.statusCode();
        String code = "http_" + status;
        String message = null;
        try {
            JsonNode error = json.readTree(response.body()).path("errors").path(0);
            if (text(error.path("code")) != null) {
                code = text(error.path("code"));
            }
            message = text(error.path("message"));
            if (message != null) {
                // O texto vem de fora: se ele ecoar a credencial, ela não pode seguir para exceção/log.
                message = message.replace(properties.accessToken(), "***");
            }
        } catch (JacksonException | IllegalArgumentException ignored) {
            // corpo de erro que não é JSON: fica o código http_<status>
        }
        boolean retryable = status == 429 || status >= 500;
        Duration retryAfter = status == 429 ? retryAfterOf(response) : null;
        return new MercadoPagoException(status, code, retryable, retryAfter,
                method + " " + path + " -> " + status + " " + code + (message == null ? "" : " (" + message + ")"));
    }

    private static Duration retryAfterOf(HttpResponse<String> response) {
        return response.headers().firstValue("Retry-After").map(value -> {
            try {
                return Duration.ofSeconds(Long.parseLong(value.trim()));
            } catch (NumberFormatException e) {
                return null;
            }
        }).orElse(null);
    }

    // ---------- conversões ----------

    private MercadoPagoOrder parseOrder(JsonNode order) {
        JsonNode payment = order.path("transactions").path("payments").path(0);
        JsonNode method = payment.path("payment_method");
        String id = text(order.path("id"));
        if (id == null) {
            throw new MercadoPagoException(200, "invalid_response", false, null, "Order response without id");
        }
        return new MercadoPagoOrder(
                id,
                text(order.path("status")),
                text(order.path("status_detail")),
                text(order.path("external_reference")),
                parseAmount(text(order.path("total_amount"))),
                text(payment.path("id")),
                text(payment.path("status")),
                text(payment.path("status_detail")),
                text(method.path("qr_code")),
                text(method.path("ticket_url")),
                parseInstant(text(payment.path("date_of_expiration"))));
    }

    /** Centavos → "4.99". BigDecimal com escala 2: exato, sem double. */
    static String formatAmount(long cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }

    /** "4.99" → 499. Fração de centavo (ex.: "4.999") é recusada, nunca arredondada. */
    static long parseAmount(String value) {
        if (value == null) {
            throw new MercadoPagoException(200, "invalid_response", false, null, "Order response without total_amount");
        }
        try {
            return new BigDecimal(value).movePointRight(2).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new MercadoPagoException(200, "invalid_response", false, null, "Invalid amount in order response");
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            throw new MercadoPagoException(200, "invalid_response", false, null, "Invalid date in order response");
        }
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asString();
    }

    /** O id vai na URL: só letras e dígitos (ids do MP são "ORD..."), nada de "/", "?" ou "..". */
    private static String pathSegment(String orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        if (!orderId.matches("[A-Za-z0-9]{1,64}")) {
            throw new IllegalArgumentException("Invalid Mercado Pago order id");
        }
        return orderId;
    }

    /** Chave de idempotência determinística da CRIAÇÃO de um pagamento: repetir a criação devolve a mesma order. */
    public static String creationKeyFor(UUID paymentId) {
        return "moneyx-create-" + paymentId;
    }

    @Override
    public String toString() {
        return "MercadoPagoClient[baseUrl=" + properties.baseUrl() + ", accessToken=***]";
    }

}
