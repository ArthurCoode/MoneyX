package com.moneyx.webhook.service;

import com.moneyx.event.domain.EventType;
import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.repository.MerchantRepository;
import com.moneyx.shared.crypto.SecretCipher;
import com.moneyx.support.MutableClock;
import com.moneyx.support.TestClockConfig;
import com.moneyx.webhook.domain.WebhookEndpoint;
import com.moneyx.webhook.domain.WebhookEndpointAction;
import com.moneyx.webhook.domain.WebhookEndpointHistory;
import com.moneyx.webhook.exception.InvalidWebhookUrlException;
import com.moneyx.webhook.exception.WebhookEndpointLimitExceededException;
import com.moneyx.webhook.exception.WebhookEndpointNotFoundException;
import com.moneyx.webhook.repository.WebhookEndpointRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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

/** Cadastro de endpoints e linha do tempo contra o PostgreSQL REAL. */
@SpringBootTest
@Import(TestClockConfig.class)
class WebhookEndpointServiceIntegrationTest {

    private static final Instant START = Instant.parse("2026-11-20T12:00:00Z");
    private static final String URL = "https://loja.com/webhooks/moneyx";


    @Autowired
    private WebhookEndpointService service;

    @Autowired
    private WebhookEndpointRepository repository;

    @Autowired
    private SecretCipher cipher;

    @Autowired
    private MutableClock clock;

    @Autowired
    private MerchantRepository merchantRepository;

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
        merchantId = newMerchant();
    }

    @AfterEach
    void cleanUp() {
        for (UUID id : createdMerchants) {
            jdbc.update("DELETE FROM webhook_endpoint_history WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM webhook_endpoints WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM merchants WHERE id = ?", id);
        }
    }

    // ---------- cadastro ----------

    @Test
    void createsEndpointWithAllEventTypesByDefault() {
        CreatedWebhookEndpoint created = service.create(merchantId, apiKeyId, URL, null);

        WebhookEndpoint stored = repository.findById(created.endpoint().getId()).orElseThrow();
        assertThat(stored.getUrl()).isEqualTo(URL);
        assertThat(stored.getEnabledEvents()).containsExactly(EventType.values());
        assertThat(stored.isActive()).isTrue();
        assertThat(stored.getCreatedAt()).isEqualTo(START);
    }

    @Test
    void enabledEventsAreDeduplicatedAndKeptInCatalogOrder() {
        CreatedWebhookEndpoint created = service.create(merchantId, apiKeyId, URL,
                List.of("payment.expired", "payment.paid", "payment.paid"));

        assertThat(repository.findById(created.endpoint().getId()).orElseThrow().getEnabledEvents())
                .containsExactly(EventType.PAYMENT_PAID, EventType.PAYMENT_EXPIRED);
    }

    @Test
    void rejectsEmptyOrUnknownEventTypes() {
        assertThatThrownBy(() -> service.create(merchantId, apiKeyId, URL, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("enabledEvents must contain at least one event type");
        assertThatThrownBy(() -> service.create(merchantId, apiKeyId, URL, List.of("payment.refunded")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown event type: payment.refunded");
        assertThat(service.listActive(merchantId)).isEmpty();
    }

    @Test
    void rejectsUnsafeUrlsWithTheReason() {
        assertThatThrownBy(() -> service.create(merchantId, apiKeyId, "https://169.254.169.254/latest", null))
                .isInstanceOf(InvalidWebhookUrlException.class)
                .hasMessage("url must not point to a private or internal address");
        // allow-local-urls é false nos testes: a exceção local está desligada por padrão.
        assertThatThrownBy(() -> service.create(merchantId, apiKeyId, "http://localhost:9000/hook", null))
                .isInstanceOf(InvalidWebhookUrlException.class);
    }

    // ---------- segredo ----------

    @Test
    void secretIsReturnedOnceAndStoredOnlyEncrypted() {
        CreatedWebhookEndpoint created = service.create(merchantId, apiKeyId, URL, null);
        String secret = created.signingSecret();

        assertThat(secret).matches("^whsec_[A-Za-z0-9_-]{43}$");
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT secret_ciphertext, secret_prefix FROM webhook_endpoints WHERE id = ?", created.endpoint().getId());
        assertThat((String) row.get("secret_ciphertext")).doesNotContain(secret).doesNotContain("whsec_");
        assertThat(row.get("secret_prefix")).isEqualTo(secret.substring(0, 10));
        // Nenhuma coluna de nenhuma tabela do endpoint contém o segredo em texto puro.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook_endpoints WHERE CAST(webhook_endpoints.* AS TEXT) LIKE ?",
                Integer.class, "%" + secret + "%")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook_endpoint_history h WHERE CAST(h.* AS TEXT) LIKE ?",
                Integer.class, "%whsec_%")).isZero();
    }

    @Test
    void storedCiphertextDecryptsBackToTheSecretForTheEndpoint() {
        CreatedWebhookEndpoint created = service.create(merchantId, apiKeyId, URL, null);
        WebhookEndpoint stored = repository.findById(created.endpoint().getId()).orElseThrow();

        assertThat(service.signingSecretOf(stored)).isEqualTo(created.signingSecret());
    }

    /** AAD na prática: o texto cifrado de um endpoint, copiado para outro, não decifra. */
    @Test
    void ciphertextCopiedToAnotherEndpointDoesNotDecrypt() {
        CreatedWebhookEndpoint first = service.create(merchantId, apiKeyId, URL, null);
        CreatedWebhookEndpoint second = service.create(merchantId, apiKeyId, URL + "/2", null);

        jdbc.update("UPDATE webhook_endpoints SET secret_ciphertext = (SELECT secret_ciphertext FROM webhook_endpoints WHERE id = ?) WHERE id = ?",
                first.endpoint().getId(), second.endpoint().getId());
        WebhookEndpoint tampered = repository.findById(second.endpoint().getId()).orElseThrow();

        assertThatThrownBy(() -> service.signingSecretOf(tampered)).hasMessage("Decryption failed");
        assertThat(cipher.decrypt(tampered.getSecretCiphertext(), first.endpoint().getId().toString()))
                .isEqualTo(first.signingSecret());
    }

    @Test
    void eachEndpointGetsADifferentSecret() {
        String a = service.create(merchantId, apiKeyId, URL, null).signingSecret();
        String b = service.create(merchantId, apiKeyId, URL, null).signingSecret();

        assertThat(a).isNotEqualTo(b);
    }

    // ---------- limite ----------

    @Test
    void allowsAtMostFiveActiveEndpointsAndDeletedOnesFreeASlot() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < WebhookEndpointService.MAX_ACTIVE_ENDPOINTS_PER_MERCHANT; i++) {
            ids.add(service.create(merchantId, apiKeyId, URL + "/" + i, null).endpoint().getId());
        }

        assertThatThrownBy(() -> service.create(merchantId, apiKeyId, URL + "/extra", null))
                .isInstanceOf(WebhookEndpointLimitExceededException.class)
                .hasMessage("A merchant can have at most 5 active webhook endpoints");

        service.delete(merchantId, apiKeyId, ids.getFirst());
        assertThat(service.create(merchantId, apiKeyId, URL + "/extra", null).endpoint().isActive()).isTrue();
    }

    /**
     * O caso que justifica trancar a linha do COMERCIANTE: partindo de ZERO endpoints não há o que trancar
     * entre os endpoints, e sem o cadeado os 8 cadastros simultâneos passariam todos pela contagem.
     */
    @RepeatedTest(3)
    void concurrentCreationsStartingFromZeroNeverExceedTheLimit() throws Exception {
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                String url = URL + "/" + i;
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        return service.create(merchantId, apiKeyId, url, null);
                    } catch (RuntimeException e) {
                        return e;
                    }
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(results).filteredOn(r -> r instanceof CreatedWebhookEndpoint).hasSize(5);
            assertThat(results).filteredOn(r -> r instanceof WebhookEndpointLimitExceededException).hasSize(3);
        } finally {
            executor.shutdownNow();
        }
        assertThat(repository.countByMerchantIdAndDeletedAtIsNull(merchantId)).isEqualTo(5);
    }

    // ---------- consulta e remoção ----------

    @Test
    void listShowsOnlyActiveEndpointsNewestFirst() {
        UUID older = service.create(merchantId, apiKeyId, URL + "/1", null).endpoint().getId();
        clock.advance(Duration.ofSeconds(1));
        UUID newer = service.create(merchantId, apiKeyId, URL + "/2", null).endpoint().getId();
        clock.advance(Duration.ofSeconds(1));
        UUID deleted = service.create(merchantId, apiKeyId, URL + "/3", null).endpoint().getId();
        service.delete(merchantId, apiKeyId, deleted);

        assertThat(service.listActive(merchantId)).extracting(WebhookEndpoint::getId).containsExactly(newer, older);
    }

    @Test
    void deleteIsLogicalAndIdempotent() {
        UUID id = service.create(merchantId, apiKeyId, URL, null).endpoint().getId();
        clock.advance(Duration.ofMinutes(5));
        service.delete(merchantId, apiKeyId, id);
        clock.advance(Duration.ofMinutes(5));
        service.delete(merchantId, apiKeyId, id); // repetição: nada muda

        WebhookEndpoint endpoint = service.get(merchantId, id); // o registro continua existindo
        assertThat(endpoint.isActive()).isFalse();
        assertThat(endpoint.getDeletedAt()).isEqualTo(START.plus(Duration.ofMinutes(5)));
    }

    @Test
    void anotherMerchantsEndpointDoesNotExist() {
        UUID id = service.create(merchantId, apiKeyId, URL, null).endpoint().getId();
        UUID other = newMerchant();

        assertThatThrownBy(() -> service.get(other, id)).isInstanceOf(WebhookEndpointNotFoundException.class);
        assertThatThrownBy(() -> service.delete(other, apiKeyId, id)).isInstanceOf(WebhookEndpointNotFoundException.class);
        assertThatThrownBy(() -> service.history(other, id)).isInstanceOf(WebhookEndpointNotFoundException.class);
        assertThat(service.listActive(other)).isEmpty();
        assertThat(service.get(merchantId, id).isActive()).isTrue();
    }

    // ---------- linha do tempo ----------

    @Test
    void historyRecordsWhatHappenedWhenAndWithWhichKey() {
        UUID id = service.create(merchantId, apiKeyId, URL, List.of("payment.paid")).endpoint().getId();
        clock.advance(Duration.ofHours(2));
        UUID otherKey = UUID.randomUUID();
        service.delete(merchantId, otherKey, id);

        List<WebhookEndpointHistory> history = service.history(merchantId, id);

        assertThat(history).extracting(WebhookEndpointHistory::getAction)
                .containsExactly(WebhookEndpointAction.CREATED, WebhookEndpointAction.DELETED);
        assertThat(history).extracting(WebhookEndpointHistory::getApiKeyId).containsExactly(apiKeyId, otherKey);
        assertThat(history).extracting(WebhookEndpointHistory::getOccurredAt)
                .containsExactly(START, START.plus(Duration.ofHours(2)));

        JsonNode snapshot = jsonMapper.readTree(history.getFirst().getSnapshot());
        assertThat(snapshot.get("url").asString()).isEqualTo(URL);
        assertThat(snapshot.get("enabledEvents").get(0).asString()).isEqualTo("payment.paid");
        assertThat(snapshot.has("secret")).isFalse();
    }

    @Test
    void actionsAtTheSameInstantAreListedInTheOrderTheyWereRecorded() {
        UUID id = service.create(merchantId, apiKeyId, URL, null).endpoint().getId();
        Instant sameInstant = START.plus(Duration.ofMinutes(5));
        // Gravadas nesta ordem, todas no MESMO instante, com ids DECRESCENTES: se a ordenação desempatasse pelo id,
        // a lista sairia exatamente invertida (determinístico, não depende da sorte do UUID aleatório).
        String[][] rows = {
                {"ffffffff-0000-4000-8000-000000000004", "DISABLED"},
                {"eeeeeeee-0000-4000-8000-000000000003", "ENABLED"},
                {"dddddddd-0000-4000-8000-000000000002", "DISABLED"},
                {"cccccccc-0000-4000-8000-000000000001", "ENABLED"}};
        for (String[] row : rows) {
            jdbc.update("""
                    INSERT INTO webhook_endpoint_history (id, endpoint_id, merchant_id, action, api_key_id, occurred_at, snapshot)
                    VALUES (?::uuid, ?, ?, ?, ?, ?, '{}'::jsonb)""",
                    row[0], id, merchantId, row[1], row[1].equals("DISABLED") ? null : apiKeyId,
                    java.sql.Timestamp.from(sameInstant));
        }

        assertThat(service.history(merchantId, id)).extracting(h -> h.getAction().name())
                .containsExactly("CREATED", "DISABLED", "ENABLED", "DISABLED", "ENABLED");

        // A ordem também não pode depender de onde as linhas estão GUARDADAS. Sem desempate explícito, o PostgreSQL
        // devolve a ordem física, que coincide com a de gravação só por acaso. Uma reorganização da tabela (CLUSTER,
        // VACUUM FULL) a muda: aqui a tabela é reescrita na ordem do id, o INVERSO da gravação, e os índices são
        // reconstruídos. Um simples UPDATE não basta: é "HOT" (o índice continua apontando para a posição antiga).
        jdbc.execute("CREATE INDEX tmp_history_by_id ON webhook_endpoint_history (id)");
        try {
            jdbc.execute("CLUSTER webhook_endpoint_history USING tmp_history_by_id");
        } finally {
            jdbc.execute("DROP INDEX tmp_history_by_id");
        }

        assertThat(service.history(merchantId, id)).extracting(h -> h.getAction().name())
                .containsExactly("CREATED", "DISABLED", "ENABLED", "DISABLED", "ENABLED");
    }

    @Test
    void sequenceIsAssignedByTheDatabaseAndCannotBeChosenByTheApplication() {
        UUID id = service.create(merchantId, apiKeyId, URL, null).endpoint().getId();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO webhook_endpoint_history (id, endpoint_id, merchant_id, action, api_key_id, occurred_at, snapshot, seq)
                VALUES (gen_random_uuid(), ?, ?, 'DELETED', ?, now(), '{}'::jsonb, 1)""", id, merchantId, apiKeyId))
                .hasMessageContaining("seq");
    }

    @Test
    void repeatedDeleteDoesNotAddASecondHistoryEntry() {
        // Sem avançar o relógio: CREATED e DELETED no MESMO instante (a coluna seq desempata, V11).
        UUID id = service.create(merchantId, apiKeyId, URL, null).endpoint().getId();
        service.delete(merchantId, apiKeyId, id);
        service.delete(merchantId, apiKeyId, id);

        assertThat(service.history(merchantId, id)).extracting(WebhookEndpointHistory::getAction)
                .containsExactly(WebhookEndpointAction.CREATED, WebhookEndpointAction.DELETED);
    }

    @Test
    void failedCreationLeavesNoEndpointAndNoHistory() {
        assertThatThrownBy(() -> service.create(merchantId, apiKeyId, "http://loja.com/hook", null))
                .isInstanceOf(InvalidWebhookUrlException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook_endpoint_history WHERE merchant_id = ?",
                Integer.class, merchantId)).isZero();
    }

    @Test
    void databaseRejectsUnknownEventTypesEvenIfTheApplicationHadABug() {
        UUID id = service.create(merchantId, apiKeyId, URL, null).endpoint().getId();

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE webhook_endpoints SET enabled_events = ARRAY['payment.refunded']::VARCHAR(50)[] WHERE id = ?", id))
                .hasMessageContaining("ck_webhook_endpoints_events");
    }

    // ---------- auxiliares ----------

    private UUID newMerchant() {
        String document = String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
        Merchant merchant = merchantRepository.saveAndFlush(
                new Merchant("Webhook Test LTDA", document, "webhook-" + UUID.randomUUID() + "@moneyx.dev"));
        createdMerchants.add(merchant.getId());
        return merchant.getId();
    }
}
