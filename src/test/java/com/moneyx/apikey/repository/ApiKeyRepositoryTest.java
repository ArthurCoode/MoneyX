package com.moneyx.apikey.repository;

import com.moneyx.apikey.domain.ApiKey;
import com.moneyx.apikey.domain.ApiKeyGenerator;
import com.moneyx.apikey.domain.ApiKeyGenerator.GeneratedApiKey;
import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.repository.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ApiKeyRepositoryTest {

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = merchantRepository.saveAndFlush(
                new Merchant("Loja Chaves LTDA", "44455566677", "apikey-test@moneyx.dev"));
    }

    @Test
    void findsKeyByHash() {
        GeneratedApiKey generated = ApiKeyGenerator.generate();
        ApiKey saved = apiKeyRepository.saveAndFlush(ApiKey.issue(merchant.getId(), generated));

        ApiKey found = apiKeyRepository.findByKeyHash(ApiKeyGenerator.hash(generated.plainText())).orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getMerchantId()).isEqualTo(merchant.getId());
    }

    @Test
    void doesNotFindUnknownHash() {
        assertThat(apiKeyRepository.findByKeyHash(ApiKeyGenerator.hash("mx_test_unknown"))).isEmpty();
    }

    @Test
    void listsOnlyKeysOfTheGivenMerchant() {
        Merchant other = merchantRepository.saveAndFlush(
                new Merchant("Outra Loja", "77766655544", "apikey-other@moneyx.dev"));

        apiKeyRepository.saveAndFlush(ApiKey.issue(merchant.getId(), ApiKeyGenerator.generate()));
        apiKeyRepository.saveAndFlush(ApiKey.issue(merchant.getId(), ApiKeyGenerator.generate()));
        apiKeyRepository.saveAndFlush(ApiKey.issue(other.getId(), ApiKeyGenerator.generate()));

        assertThat(apiKeyRepository.findByMerchantIdOrderByCreatedAtDesc(merchant.getId()))
                .hasSize(2)
                .allMatch(key -> key.getMerchantId().equals(merchant.getId()));
    }

    @Test
    void persistsRevocation() {
        ApiKey apiKey = apiKeyRepository.saveAndFlush(ApiKey.issue(merchant.getId(), ApiKeyGenerator.generate()));

        apiKey.revoke();
        apiKeyRepository.saveAndFlush(apiKey);

        assertThat(apiKeyRepository.findById(apiKey.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void databaseRejectsKeyForNonexistentMerchant() {
        ApiKey orphan = ApiKey.issue(UUID.randomUUID(), ApiKeyGenerator.generate());

        assertThatThrownBy(() -> apiKeyRepository.saveAndFlush(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
