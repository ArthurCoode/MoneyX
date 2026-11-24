package com.moneyx.webhook.service;

import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.repository.MerchantRepository;
import com.moneyx.shared.crypto.SecretCipher;
import com.moneyx.support.MutableClock;
import com.moneyx.support.TestClockConfig;
import com.moneyx.webhook.domain.WebhookEndpoint;
import com.moneyx.webhook.repository.WebhookEndpointRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rotação da chave que cifra os segredos de webhook, contra o PostgreSQL REAL.
 *
 * O contexto dos testes já está "no meio de uma rotação": tem a chave ativa e uma chave ANTERIOR fixa
 * (src/test/resources/config/application.properties). Aqui gravamos no banco valores cifrados com a anterior (e no
 * formato antigo, sem prefixo) e conferimos que a rotina os move para a ativa sem mudar o segredo.
 *
 * SEPARATE_THREAD: se o cursor quebrar, a rotina entra em laço infinito; o timeout transforma isso em falha.
 */
@SpringBootTest
@Import(TestClockConfig.class)
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class WebhookSecretRotationIntegrationTest {

    private static final String URL = "https://loja.com/webhooks/moneyx";

    @Value("${moneyx.webhooks.previous-encryption-key}")
    private String previousKey;

    @Value("${moneyx.webhooks.encryption-key}")
    private String activeKey;

    @Autowired
    private WebhookSecretRotationService rotation;

    @Autowired
    private WebhookEndpointService endpoints;

    @Autowired
    private WebhookEndpointRepository repository;

    @Autowired
    private SecretCipher cipher;

    @Autowired
    private MutableClock clock;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> createdMerchants = new ArrayList<>();
    private SecretCipher previousOnly;

    @BeforeEach
    void setUp() {
        clock.set(Instant.parse("2026-11-24T12:00:00Z"));
        previousOnly = new SecretCipher(previousKey);
    }

    @AfterEach
    void cleanUp() {
        for (UUID id : createdMerchants) {
            jdbc.update("DELETE FROM webhook_endpoint_history WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM webhook_endpoints WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM merchants WHERE id = ?", id);
        }
    }

    @Test
    void secretsFromThePreviousKeyAndFromTheLegacyFormatAreMovedToTheActiveKeyWithoutChangingTheSecret() {
        UUID merchantId = newMerchant();
        Endpoint withPrevious = newEndpoint(merchantId);
        Endpoint legacyPrevious = newEndpoint(merchantId);
        Endpoint legacyActive = newEndpoint(merchantId);
        Endpoint alreadyActive = newEndpoint(merchantId);

        store(withPrevious, previousOnly.encrypt(withPrevious.secret(), withPrevious.aad()));
        store(legacyPrevious, payloadOf(previousOnly.encrypt(legacyPrevious.secret(), legacyPrevious.aad())));
        // Situação real do banco de desenvolvimento: segredos gravados com a chave atual, antes do prefixo existir.
        store(legacyActive, payloadOf(new SecretCipher(activeKey).encrypt(legacyActive.secret(), legacyActive.aad())));
        String untouched = ciphertextOf(alreadyActive);

        WebhookSecretRotationService.Result result = rotation.reencryptAll();

        assertThat(result.reencrypted()).isEqualTo(3);
        assertThat(result.failed()).isZero();
        assertThat(result.remaining()).isZero();
        for (Endpoint endpoint : List.of(withPrevious, legacyPrevious, legacyActive, alreadyActive)) {
            String stored = ciphertextOf(endpoint);
            assertThat(stored).startsWith(cipher.activePrefix());
            // Só a chave ATIVA basta para ler: a anterior já pode sair do .env.
            assertThat(new SecretCipher(activeKey).decrypt(stored, endpoint.aad())).isEqualTo(endpoint.secret());
            // A entrega (assinatura) continua usando o mesmo segredo que a loja recebeu no cadastro.
            assertThat(endpoints.signingSecretOf(repository.findById(endpoint.id()).orElseThrow()))
                    .isEqualTo(endpoint.secret());
        }
        assertThat(ciphertextOf(alreadyActive)).isEqualTo(untouched);   // nada a fazer: não é reescrito
    }

    @Test
    void runningAgainIsANoOp() {
        Endpoint endpoint = newEndpoint(newMerchant());
        store(endpoint, previousOnly.encrypt(endpoint.secret(), endpoint.aad()));
        rotation.reencryptAll();
        String afterFirstRun = ciphertextOf(endpoint);

        WebhookSecretRotationService.Result second = rotation.reencryptAll();

        assertThat(second.reencrypted()).isZero();
        assertThat(ciphertextOf(endpoint)).isEqualTo(afterFirstRun);
    }

    @Test
    void deletedEndpointsAreAlsoReencrypted() {
        UUID merchantId = newMerchant();
        Endpoint endpoint = newEndpoint(merchantId);
        store(endpoint, previousOnly.encrypt(endpoint.secret(), endpoint.aad()));
        endpoints.delete(merchantId, UUID.randomUUID(), endpoint.id());

        rotation.reencryptAll();

        assertThat(ciphertextOf(endpoint)).startsWith(cipher.activePrefix());
    }

    /**
     * Um segredo que não decifra (corrompido, ou copiado de outro endpoint: o AAD não confere) é contado e pulado;
     * os outros continuam, e a rotina termina (o cursor passa por ele).
     */
    @Test
    void undecryptableSecretsAreSkippedAndReportedWithoutStoppingTheOthers() {
        UUID merchantId = newMerchant();
        Endpoint good = newEndpoint(merchantId);
        Endpoint copied = newEndpoint(merchantId);
        Endpoint garbage = newEndpoint(merchantId);
        store(good, previousOnly.encrypt(good.secret(), good.aad()));
        store(copied, previousOnly.encrypt(good.secret(), good.aad()));     // AAD de OUTRO endpoint
        store(garbage, "not a ciphertext");

        WebhookSecretRotationService.Result result = rotation.reencryptAll();

        assertThat(result.reencrypted()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(2);
        assertThat(result.remaining()).isEqualTo(2);
        assertThat(ciphertextOf(good)).startsWith(cipher.activePrefix());
        assertThat(ciphertextOf(garbage)).isEqualTo("not a ciphertext");
    }

    /** Lotes pequenos com segredos ruins NO MEIO: sem o cursor, eles voltariam em todo lote e a rotina não terminaria. */
    @Test
    void worksAcrossSeveralBatchesEvenWithFailuresInEveryBatch() {
        UUID merchantA = newMerchant();
        UUID merchantB = newMerchant();
        List<Endpoint> all = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            all.add(newEndpoint(merchantA));
            all.add(newEndpoint(merchantB));
        }
        int broken = 0;
        for (int i = 0; i < all.size(); i++) {
            Endpoint endpoint = all.get(i);
            if (i % 3 == 0) {
                store(endpoint, "broken-" + i);
                broken++;
            } else {
                store(endpoint, previousOnly.encrypt(endpoint.secret(), endpoint.aad()));
            }
        }

        WebhookSecretRotationService.Result result = rotation.reencryptAll(2);

        assertThat(result.failed()).isEqualTo(broken);
        assertThat(result.reencrypted()).isEqualTo(all.size() - broken);
        assertThat(result.remaining()).isEqualTo(broken);
    }

    @Test
    void newEndpointsAreAlwaysEncryptedWithTheActiveKey() {
        Endpoint endpoint = newEndpoint(newMerchant());

        assertThat(ciphertextOf(endpoint)).startsWith(cipher.activePrefix());
        assertThat(rotation.reencryptAll().reencrypted()).isZero();
    }

    // ---------- auxiliares ----------

    private record Endpoint(UUID id, String secret) {
        String aad() {
            return id.toString();
        }
    }

    private Endpoint newEndpoint(UUID merchantId) {
        CreatedWebhookEndpoint created = endpoints.create(merchantId, UUID.randomUUID(), URL, null);
        WebhookEndpoint endpoint = created.endpoint();
        return new Endpoint(endpoint.getId(), created.signingSecret());
    }

    private void store(Endpoint endpoint, String ciphertext) {
        jdbc.update("UPDATE webhook_endpoints SET secret_ciphertext = ? WHERE id = ?", ciphertext, endpoint.id());
    }

    private String ciphertextOf(Endpoint endpoint) {
        return jdbc.queryForObject("SELECT secret_ciphertext FROM webhook_endpoints WHERE id = ?", String.class,
                endpoint.id());
    }

    private static String payloadOf(String stored) {
        return stored.substring(stored.indexOf(':') + 1);
    }

    private UUID newMerchant() {
        String document = String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
        Merchant merchant = merchantRepository.saveAndFlush(
                new Merchant("Rotation Test LTDA", document, "rotation-" + UUID.randomUUID() + "@moneyx.dev"));
        createdMerchants.add(merchant.getId());
        return merchant.getId();
    }
}
