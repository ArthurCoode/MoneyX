package com.moneyx.apikey.service;

import com.moneyx.apikey.domain.ApiKey;
import com.moneyx.apikey.exception.ApiKeyLimitExceededException;
import com.moneyx.apikey.exception.LastActiveApiKeyException;
import com.moneyx.apikey.repository.ApiKeyRepository;
import com.moneyx.merchant.repository.MerchantRepository;
import com.moneyx.merchant.service.MerchantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova, contra o PostgreSQL real, que as regras de chaves resistem a requisições simultâneas.
 *
 * Várias threads disparam ao mesmo tempo (CountDownLatch funciona como a "largada").
 * Sem o lock pessimista, todas leriam a mesma contagem e furariam a regra.
 *
 * NÃO é @Transactional: cada chamada ao serviço precisa da sua própria transação real,
 * como aconteceria com requisições HTTP distintas. Por isso os dados são únicos e
 * apagados explicitamente no @AfterEach.
 */
@SpringBootTest
class ApiKeyConcurrencyIntegrationTest {

    private static final int THREADS = 8;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    private UUID merchantId;

    @BeforeEach
    void setUp() {
        String document = String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
        // O cadastro já emite a primeira chave: o comerciante começa com 1 chave ativa.
        merchantId = merchantService.register(
                "Concurrency Test LTDA", document, "concurrency-" + UUID.randomUUID() + "@moneyx.dev")
                .merchant().getId();
    }

    @AfterEach
    void cleanUp() {
        apiKeyRepository.deleteAll(apiKeyRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId));
        merchantRepository.deleteById(merchantId);
    }

    @Test
    void concurrentCreationNeverExceedsTheLimit() throws Exception {
        List<Outcome> outcomes = runConcurrently(THREADS, i -> apiKeyService.issueFor(merchantId));

        int slotsLeft = ApiKeyService.MAX_ACTIVE_KEYS_PER_MERCHANT - 1; // 4
        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(slotsLeft);
        assertThat(outcomes).filteredOn(o -> o.error() instanceof ApiKeyLimitExceededException)
                .hasSize(THREADS - slotsLeft);
        assertThat(activeKeyCount()).isEqualTo(ApiKeyService.MAX_ACTIVE_KEYS_PER_MERCHANT);
    }

    @Test
    void concurrentRevocationNeverLeavesMerchantWithoutActiveKey() throws Exception {
        // Completa até o limite: 5 chaves ativas, cada thread tenta revogar uma diferente.
        for (int i = 1; i < ApiKeyService.MAX_ACTIVE_KEYS_PER_MERCHANT; i++) {
            apiKeyService.issueFor(merchantId);
        }
        List<UUID> keyIds = apiKeyRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId).stream()
                .map(ApiKey::getId)
                .toList();

        List<Outcome> outcomes = runConcurrently(keyIds.size(), i -> {
            apiKeyService.revoke(merchantId, keyIds.get(i));
            return null;
        });

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(keyIds.size() - 1);
        assertThat(outcomes).filteredOn(o -> o.error() instanceof LastActiveApiKeyException).hasSize(1);
        assertThat(activeKeyCount()).isEqualTo(1);
    }

    private long activeKeyCount() {
        return apiKeyRepository.countByMerchantIdAndRevokedAtIsNull(merchantId);
    }

    // ---------- infraestrutura de concorrência ----------

    private record Outcome(Throwable error) {
        boolean succeeded() {
            return error == null;
        }
    }

    @FunctionalInterface
    private interface Task {
        Object run(int index) throws Exception;
    }

    private static List<Outcome> runConcurrently(int threads, Task task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                Callable<Outcome> callable = () -> {
                    ready.countDown();
                    start.await(); // todas esperam a largada
                    try {
                        task.run(index);
                        return new Outcome(null);
                    } catch (Exception e) {
                        return new Outcome(e);
                    }
                };
                futures.add(executor.submit(callable));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown(); // largada!

            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }
}
