package com.moneyx.shared.observability;

import com.jayway.jsonpath.JsonPath;
import com.moneyx.payment.domain.PaymentCurrency;
import com.moneyx.payment.service.CreatePaymentCommand;
import com.moneyx.payment.service.PaymentService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Observabilidade de ponta a ponta: id de correlação (header, corpo dos erros, logs), métricas de negócio contadas
 * só depois do commit, e a REVISÃO DE LOGS automatizada: nenhum segredo ou dado pessoal pode aparecer na saída.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<String> createdMerchants = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String id : createdMerchants) {
            UUID merchantId = UUID.fromString(id);
            jdbc.update("DELETE FROM webhook_deliveries WHERE merchant_id = ?", merchantId);
            jdbc.update("DELETE FROM webhook_endpoint_history WHERE merchant_id = ?", merchantId);
            jdbc.update("DELETE FROM webhook_endpoints WHERE merchant_id = ?", merchantId);
            jdbc.update("DELETE FROM events WHERE merchant_id = ?", merchantId);
            jdbc.update("DELETE FROM payment_idempotency_keys WHERE merchant_id = ?", merchantId);
            jdbc.update("DELETE FROM payments WHERE merchant_id = ?", merchantId);
            jdbc.update("DELETE FROM api_keys WHERE merchant_id = ?", merchantId);
            jdbc.update("DELETE FROM merchants WHERE id = ?", merchantId);
        }
    }

    // ---------- id de correlação ----------

    @Test
    void everyResponseCarriesARequestId() throws Exception {
        String generated = mockMvc.perform(get("/v1/merchants/me"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getHeader(RequestIds.HEADER);

        assertThat(generated).matches("^[0-9a-f-]{36}$");
        // O MockMvc roda na thread do teste: depois da requisição, o id NÃO pode ter ficado no MDC (num servidor
        // real a thread volta ao pool, e a próxima requisição ou um log fora de requisição herdaria um id errado).
        assertThat(RequestIds.current()).isNull();
    }

    @Test
    void aSafeClientIdIsKeptAndAnUnsafeOneIsReplaced() throws Exception {
        mockMvc.perform(get("/v1/merchants/me").header(RequestIds.HEADER, "client-req-42"))
                .andExpect(header().string(RequestIds.HEADER, "client-req-42"));

        String replaced = mockMvc.perform(get("/v1/merchants/me").header(RequestIds.HEADER, "bad id with spaces"))
                .andReturn().getResponse().getHeader(RequestIds.HEADER);
        assertThat(replaced).matches("^[0-9a-f-]{36}$");
    }

    /** O mesmo id do header aparece no corpo de TODO erro (401 da segurança, 400 de validação, 404 de negócio). */
    @Test
    void problemDetailsCarryTheSameRequestIdAsTheHeader() throws Exception {
        mockMvc.perform(get("/v1/merchants/me").header(RequestIds.HEADER, "req-401"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.requestId").value("req-401"));

        mockMvc.perform(post("/v1/merchants").header(RequestIds.HEADER, "req-400")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.requestId").value("req-400"));

        Registered merchant = register("req-setup");
        mockMvc.perform(get("/v1/payments/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + merchant.key())
                        .header(RequestIds.HEADER, "req-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.requestId").value("req-404"));
    }

    @Test
    void logLinesOfARequestCarryItsId(CapturedOutput output) throws Exception {
        Registered merchant = register("req-log-check");

        assertThat(output.getOut()).contains("[req-log-check]")
                .containsPattern("\\[req-log-check\\].*merchant\\.registered merchant=" + merchant.id());
    }

    // ---------- revisão de logs ----------

    /**
     * Executa os fluxos que lidam com segredos e dados pessoais e garante que NENHUM deles aparece no log:
     * API Keys (a do cadastro e uma criada depois), segredo de webhook, documento e e-mail.
     */
    @Test
    void secretsAndPersonalDataNeverReachTheLogs(CapturedOutput output) throws Exception {
        String document = String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
        String email = "privacy-" + UUID.randomUUID() + "@moneyx.dev";
        Registered merchant = register("req-privacy", document, email);
        String bearer = "Bearer " + merchant.key();

        String secondKey = JsonPath.read(mockMvc.perform(post("/v1/api-keys").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.key");
        String webhookSecret = JsonPath.read(mockMvc.perform(post("/v1/webhook-endpoints")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://loja-privacy.com/hooks\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.secret");
        String paymentId = JsonPath.read(mockMvc.perform(post("/v1/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .header("Idempotency-Key", "privacy-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 4990, \"currency\": \"BRL\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        mockMvc.perform(post("/v1/sandbox/payments/{id}/pay", paymentId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());

        String logs = output.getAll();
        assertThat(logs).contains("payment.paid payment=" + paymentId); // os fluxos de fato foram logados
        assertThat(logs)
                .doesNotContain(merchant.key())
                .doesNotContain(secondKey)
                .doesNotContain(webhookSecret)
                .doesNotContain(document)
                .doesNotContain(email);
    }

    // ---------- métricas ----------

    @Test
    void paymentMetricsCountOnlyCommittedTransitions() throws Exception {
        Registered merchant = register("req-metrics");
        UUID merchantId = UUID.fromString(merchant.id());
        double paidBefore = count("payment.paid");
        double canceledBefore = count("payment.canceled");

        var created = paymentService.create(merchantId, "metrics-1", new CreatePaymentCommand(
                4990, PaymentCurrency.BRL, null, null, null)).payment();
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            paymentService.cancel(merchantId, created.getId());
            tx.setRollbackOnly(); // desfeito: não pode contar
        });
        assertThat(count("payment.canceled")).isEqualTo(canceledBefore);

        paymentService.simulatePixPayment(merchantId, created.getId());
        assertThat(count("payment.paid")).isEqualTo(paidBefore + 1);
    }

    /**
     * O caso que distingue AFTER_COMMIT de BEFORE_COMMIT: a transição foi feita, mas o PRÓPRIO COMMIT falha.
     * A FK de payment_idempotency_keys é DEFERRABLE (conferida só no commit): inserir uma chave apontando para um
     * pagamento inexistente faz o commit falhar depois que tudo "deu certo". A métrica não pode ter contado.
     */
    @Test
    void paymentMetricsDoNotCountWhenTheCommitItselfFails() throws Exception {
        Registered merchant = register("req-commit-fail");
        UUID merchantId = UUID.fromString(merchant.id());
        var created = paymentService.create(merchantId, "commit-fail-1", new CreatePaymentCommand(
                4990, PaymentCurrency.BRL, null, null, null)).payment();
        double canceledBefore = count("payment.canceled");

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                    paymentService.cancel(merchantId, created.getId());
                    jdbc.update("""
                            INSERT INTO payment_idempotency_keys
                                (id, merchant_id, idempotency_key, request_fingerprint, payment_id, created_at, expires_at)
                            VALUES (?, ?, 'orphan', ?, ?, now(), now() + interval '1 day')
                            """, UUID.randomUUID(), merchantId, "0".repeat(64), UUID.randomUUID());
                })).hasMessageContaining("fk_payment_idempotency_keys_payment");

        assertThat(count("payment.canceled")).isEqualTo(canceledBefore);
        assertThat(paymentService.get(merchantId, created.getId()).getStatus().name()).isEqualTo("PENDING");
    }

    private double count(String type) {
        var counter = meterRegistry.find("moneyx.payments.events").tag("type", type).counter();
        return counter == null ? 0 : counter.count();
    }

    // ---------- auxiliares ----------

    private record Registered(String id, String key) {
    }

    private Registered register(String requestId) throws Exception {
        String document = String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
        return register(requestId, document, "obs-" + UUID.randomUUID() + "@moneyx.dev");
    }

    private Registered register(String requestId, String document, String email) throws Exception {
        String body = mockMvc.perform(post("/v1/merchants")
                        .header(RequestIds.HEADER, requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalName\": \"Observability LTDA\", \"document\": \"" + document
                                + "\", \"email\": \"" + email + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.merchant.id");
        createdMerchants.add(id);
        return new Registered(id, JsonPath.read(body, "$.apiKey.key"));
    }
}
