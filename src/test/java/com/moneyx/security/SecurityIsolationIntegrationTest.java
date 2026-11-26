package com.moneyx.security;

import com.jayway.jsonpath.JsonPath;
import com.moneyx.apikey.repository.ApiKeyRepository;
import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.repository.MerchantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste ponta a ponta da segurança: filtro de API Key, serviços e PostgreSQL REAIS.
 *
 * Os testes de fatia (@WebMvcTest) usam mocks e provam cada peça isoladamente. Este prova que as
 * peças se encaixam: chaves de verdade, emitidas pelo cadastro, autenticando requisições de verdade.
 *
 * Cenário base: dois comerciantes, A e B, cada um com a chave recebida no cadastro.
 *
 * NÃO é @Transactional: cada requisição precisa da sua própria transação, como em produção.
 * Os dados são únicos e apagados explicitamente no @AfterEach.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> createdMerchants = new ArrayList<>();

    private Registered merchantA;
    private Registered merchantB;

    /** Comerciante criado pelo endpoint público, com sua primeira chave real. */
    private record Registered(UUID merchantId, UUID keyId, String key) {
        String bearer() {
            return "Bearer " + key;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        merchantA = register("Isolation A LTDA");
        merchantB = register("Isolation B LTDA");
    }

    @AfterEach
    void cleanUp() {
        for (UUID merchantId : createdMerchants) {
            // Ordem respeita as FKs: webhooks -> eventos -> idempotência -> pagamentos -> chaves -> comerciante.
            jdbc.update("DELETE FROM webhook_deliveries WHERE merchant_id = ?", merchantId);
            jdbc.update("DELETE FROM webhook_endpoint_history WHERE merchant_id = ?", merchantId);
            jdbc.update("DELETE FROM webhook_endpoints WHERE merchant_id = ?", merchantId);
            jdbc.update("DELETE FROM events WHERE merchant_id = ?", merchantId);
            jdbc.update("DELETE FROM payment_idempotency_keys WHERE merchant_id = ?", merchantId);
            jdbc.update("DELETE FROM payments WHERE merchant_id = ?", merchantId);
            apiKeyRepository.deleteAll(apiKeyRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId));
            merchantRepository.deleteById(merchantId);
        }
    }

    // ---------- identidade vem da chave ----------

    @Test
    void meReturnsTheMerchantThatOwnsTheKey() throws Exception {
        mockMvc.perform(get("/v1/merchants/me").header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(merchantA.merchantId().toString()));

        mockMvc.perform(get("/v1/merchants/me").header(HttpHeaders.AUTHORIZATION, merchantB.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(merchantB.merchantId().toString()));
    }

    @Test
    void merchantIdSentByTheClientIsIgnored() throws Exception {
        mockMvc.perform(get("/v1/api-keys")
                        .param("merchantId", merchantB.merchantId().toString())
                        .header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id").value(hasItem(merchantA.keyId().toString())))
                .andExpect(jsonPath("$.data[*].id").value(not(hasItem(merchantB.keyId().toString()))));
    }

    // ---------- isolamento entre comerciantes ----------

    @Test
    void merchantListsOnlyItsOwnKeys() throws Exception {
        mockMvc.perform(get("/v1/api-keys").header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(merchantA.keyId().toString()));
    }

    @Test
    void merchantCannotRevokeAnotherMerchantsKey() throws Exception {
        mockMvc.perform(delete("/v1/api-keys/{id}", merchantB.keyId())
                        .header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("API key not found"));

        // A chave de B continua intacta e funcionando.
        assertThat(apiKeyRepository.findById(merchantB.keyId()).orElseThrow().isActive()).isTrue();
        mockMvc.perform(get("/v1/merchants/me").header(HttpHeaders.AUTHORIZATION, merchantB.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void foreignKeyAndNonexistentKeyAreIndistinguishable() throws Exception {
        String foreign = mockMvc.perform(delete("/v1/api-keys/{id}", merchantB.keyId())
                        .header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        String nonexistent = mockMvc.perform(delete("/v1/api-keys/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // Mesmo status e mesma mensagem: um atacante não descobre se o id existe em outro comerciante.
        assertThat(JsonPath.<String>read(foreign, "$.detail"))
                .isEqualTo(JsonPath.<String>read(nonexistent, "$.detail"));
    }

    // ---------- ciclo de vida da chave ----------

    @Test
    void revokedKeyStopsWorkingImmediately() throws Exception {
        String secondKey = JsonPath.read(mockMvc.perform(post("/v1/api-keys")
                        .header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.key");

        mockMvc.perform(delete("/v1/api-keys/{id}", merchantA.keyId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondKey))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/merchants/me").header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get("/v1/merchants/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + secondKey))
                .andExpect(status().isOk());
    }

    // ---------- status do comerciante ----------

    @Test
    void suspendedMerchantIsForbiddenButOthersAreNotAffected() throws Exception {
        Merchant a = merchantRepository.findById(merchantA.merchantId()).orElseThrow();
        a.suspend();
        merchantRepository.saveAndFlush(a);

        mockMvc.perform(get("/v1/merchants/me").header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(post("/v1/api-keys").header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(status().isForbidden());

        // A suspensão de A não interfere em B.
        mockMvc.perform(get("/v1/merchants/me").header(HttpHeaders.AUTHORIZATION, merchantB.bearer()))
                .andExpect(status().isOk());
    }

    // ---------- pagamentos ----------

    @Test
    void sameIdempotencyKeyFromTwoMerchantsCreatesTwoIndependentPayments() throws Exception {
        String sharedKey = "shared-" + UUID.randomUUID();

        String paymentOfA = createPayment(merchantA, sharedKey, 4990);
        String paymentOfB = createPayment(merchantB, sharedKey, 15000);

        assertThat(paymentOfA).isNotEqualTo(paymentOfB);

        // O replay de A devolve o pagamento de A, nunca o de B (e vice-versa).
        mockMvc.perform(post("/v1/payments")
                        .header(HttpHeaders.AUTHORIZATION, merchantA.bearer())
                        .header("Idempotency-Key", sharedKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody(4990)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(paymentOfA))
                .andExpect(jsonPath("$.amount").value(4990));
    }

    @Test
    void merchantCannotPayAnotherMerchantsPix() throws Exception {
        String paymentOfA = createPayment(merchantA, "key-" + UUID.randomUUID(), 4990);

        mockMvc.perform(post("/v1/sandbox/payments/{id}/pay", paymentOfA)
                        .header(HttpHeaders.AUTHORIZATION, merchantB.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Payment not found"));

        // O dono consegue pagar normalmente (prova que o 404 acima foi por isolamento).
        mockMvc.perform(post("/v1/sandbox/payments/{id}/pay", paymentOfA)
                        .header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void merchantCannotReadAnotherMerchantsPaymentAndTheAnswerMatchesANonexistentOne() throws Exception {
        String paymentOfA = createPayment(merchantA, "key-" + UUID.randomUUID(), 4990);

        String foreign = mockMvc.perform(get("/v1/payments/{id}", paymentOfA)
                        .header(HttpHeaders.AUTHORIZATION, merchantB.bearer()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String nonexistent = mockMvc.perform(get("/v1/payments/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, merchantB.bearer()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // Mesma resposta (exceto o "instance", que é a URL pedida): B não descobre que o id existe.
        assertThat(JsonPath.<String>read(foreign, "$.detail")).isEqualTo(JsonPath.read(nonexistent, "$.detail"));

        mockMvc.perform(get("/v1/payments/{id}", paymentOfA).header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentOfA));
    }

    @Test
    void merchantListsOnlyItsOwnPaymentsAndCannotUseAForeignCursor() throws Exception {
        String paymentOfA = createPayment(merchantA, "key-" + UUID.randomUUID(), 4990);
        String paymentOfB = createPayment(merchantB, "key-" + UUID.randomUUID(), 15000);

        mockMvc.perform(get("/v1/payments").header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(paymentOfA))
                .andExpect(jsonPath("$.hasMore").value(false));

        // O id de um pagamento de B não serve como cursor para A (e não revela que existe).
        mockMvc.perform(get("/v1/payments")
                        .param("startingAfter", paymentOfB)
                        .header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_starting_after"));
    }

    @Test
    void merchantCannotCancelAnotherMerchantsPayment() throws Exception {
        String paymentOfA = createPayment(merchantA, "key-" + UUID.randomUUID(), 4990);

        mockMvc.perform(post("/v1/payments/{id}/cancel", paymentOfA)
                        .header(HttpHeaders.AUTHORIZATION, merchantB.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Payment not found"));

        // Continua PENDING e o dono consegue cancelar (prova que o 404 acima foi por isolamento).
        mockMvc.perform(get("/v1/payments/{id}", paymentOfA).header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(post("/v1/payments/{id}/cancel", paymentOfA)
                        .header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    @Test
    void merchantSeesOnlyItsOwnEvents() throws Exception {
        String paymentOfA = createPayment(merchantA, "key-" + UUID.randomUUID(), 4990);
        String paymentOfB = createPayment(merchantB, "key-" + UUID.randomUUID(), 15000);

        String eventsOfB = mockMvc.perform(get("/v1/events").header(HttpHeaders.AUTHORIZATION, merchantB.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].type").value("payment.created"))
                .andExpect(jsonPath("$.data[0].data.id").value(paymentOfB))
                .andReturn().getResponse().getContentAsString();
        String eventOfB = JsonPath.read(eventsOfB, "$.data[0].id");

        mockMvc.perform(get("/v1/events").header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].data.id").value(paymentOfA));

        // A não lê o evento de B pelo id nem o usa como cursor.
        mockMvc.perform(get("/v1/events/{id}", eventOfB).header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Event not found"));
        mockMvc.perform(get("/v1/events")
                        .param("startingAfter", eventOfB)
                        .header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_starting_after"));
    }

    @Test
    void merchantCannotSeeChangeOrAuditAnotherMerchantsWebhookEndpoint() throws Exception {
        String body = mockMvc.perform(post("/v1/webhook-endpoints")
                        .header(HttpHeaders.AUTHORIZATION, merchantA.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://loja-a.com/hooks"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String endpointOfA = JsonPath.read(body, "$.id");

        mockMvc.perform(get("/v1/webhook-endpoints").header(HttpHeaders.AUTHORIZATION, merchantB.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        for (var request : List.of(
                get("/v1/webhook-endpoints/{id}", endpointOfA),
                get("/v1/webhook-endpoints/{id}/history", endpointOfA),
                delete("/v1/webhook-endpoints/{id}", endpointOfA))) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, merchantB.bearer()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Webhook endpoint not found"));
        }

        // Continua ativo para A, e a linha do tempo mostra só o cadastro, feito com a chave de A.
        mockMvc.perform(get("/v1/webhook-endpoints/{id}/history", endpointOfA)
                        .header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].action").value("created"))
                .andExpect(jsonPath("$.data[0].apiKeyId").value(merchantA.keyId().toString()));
    }

    @Test
    void merchantCannotListOrRetryAnotherMerchantsDeliveriesNorEnableTheirEndpoint() throws Exception {
        String body = mockMvc.perform(post("/v1/webhook-endpoints")
                        .header(HttpHeaders.AUTHORIZATION, merchantA.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://loja-a.com/hooks"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String endpointOfA = JsonPath.read(body, "$.id");
        createPayment(merchantA, "key-" + UUID.randomUUID(), 4990); // gera uma entrega pendente para A

        String deliveries = mockMvc.perform(get("/v1/webhook-endpoints/{id}/deliveries", endpointOfA)
                        .header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        String deliveryOfA = JsonPath.read(deliveries, "$.data[0].id");

        mockMvc.perform(get("/v1/webhook-endpoints/{id}/deliveries", endpointOfA)
                        .header(HttpHeaders.AUTHORIZATION, merchantB.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Webhook endpoint not found"));
        mockMvc.perform(post("/v1/webhook-endpoints/{id}/enable", endpointOfA)
                        .header(HttpHeaders.AUTHORIZATION, merchantB.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/v1/webhook-deliveries/{id}/retry", deliveryOfA)
                        .header(HttpHeaders.AUTHORIZATION, merchantB.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Webhook delivery not found"));

        // Para o dono, a mesma entrega existe (está pendente, por isso o reenvio manual é recusado com 409).
        mockMvc.perform(post("/v1/webhook-deliveries/{id}/retry", deliveryOfA)
                        .header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("delivery_not_failed"));
    }

    /** Ciclo completo pela API: criar → pagar gera payment.created e payment.paid, com o retrato de cada momento. */
    @Test
    void paymentLifecycleIsVisibleAsEvents() throws Exception {
        String payment = createPayment(merchantA, "key-" + UUID.randomUUID(), 4990);
        mockMvc.perform(post("/v1/sandbox/payments/{id}/pay", payment).header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/events").param("type", "payment.paid").header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].data.id").value(payment))
                .andExpect(jsonPath("$.data[0].data.status").value("PAID"));
        mockMvc.perform(get("/v1/events").param("type", "payment.created").header(HttpHeaders.AUTHORIZATION, merchantA.bearer()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].data.status").value("PENDING"));
    }

    private String createPayment(Registered merchant, String idempotencyKey, long amount) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/v1/payments")
                        .header(HttpHeaders.AUTHORIZATION, merchant.bearer())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody(amount)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static String paymentBody(long amount) {
        return """
                {"amount": %d, "currency": "BRL"}
                """.formatted(amount);
    }

    // ---------- auxiliares ----------

    private Registered register(String legalName) throws Exception {
        String document = String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
        String email = "isolation-" + UUID.randomUUID() + "@moneyx.dev";

        String body = mockMvc.perform(post("/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalName": "%s", "document": "%s", "email": "%s", "password": "senha-forte-123"}
                                """.formatted(legalName, document, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID merchantId = UUID.fromString(JsonPath.read(body, "$.merchant.id"));
        createdMerchants.add(merchantId);

        return new Registered(
                merchantId,
                UUID.fromString(JsonPath.read(body, "$.apiKey.id")),
                JsonPath.read(body, "$.apiKey.key"));
    }
}
