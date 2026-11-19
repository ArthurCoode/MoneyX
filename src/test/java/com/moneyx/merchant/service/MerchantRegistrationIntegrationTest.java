package com.moneyx.merchant.service;

import com.moneyx.apikey.domain.ApiKey;
import com.moneyx.apikey.domain.ApiKeyGenerator;
import com.moneyx.apikey.repository.ApiKeyRepository;
import com.moneyx.apikey.service.ApiKeyService;
import com.moneyx.merchant.repository.MerchantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Teste de integração do cadastro completo contra o PostgreSQL real:
 * serviço real, transação real, banco real.
 *
 * @MockitoSpyBean mantém o ApiKeyService verdadeiro, mas permite forçar uma falha
 * em um teste específico para provar a atomicidade.
 */
@SpringBootTest
class MerchantRegistrationIntegrationTest {

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @MockitoSpyBean
    private ApiKeyService apiKeyService;

    @Test
    @Transactional // rollback ao final: nada permanece no banco
    void persistsMerchantAndApiKeyWhoseHashMatchesTheReturnedSecret() {
        MerchantRegistration registration =
                merchantService.register("Loja Integracao LTDA", "10203040506", "integration@moneyx.dev");

        String secret = registration.apiKey().plainText();
        ApiKey stored = apiKeyRepository.findByKeyHash(ApiKeyGenerator.hash(secret)).orElseThrow();

        assertThat(stored.getMerchantId()).isEqualTo(registration.merchant().getId());
        assertThat(stored.isActive()).isTrue();
        assertThat(merchantRepository.existsByDocument("10203040506")).isTrue();
    }

    @Test
    void merchantIsNotCreatedWhenApiKeyIssuanceFails() {
        String document = "60504030201";
        doThrow(new IllegalStateException("simulated failure")).when(apiKeyService).issueFor(any(UUID.class));

        assertThatThrownBy(() -> merchantService.register("Loja Falha LTDA", document, "failure@moneyx.dev"))
                .isInstanceOf(IllegalStateException.class);

        // O INSERT do merchant chegou a ser executado (saveAndFlush), mas foi desfeito pelo rollback.
        assertThat(merchantRepository.existsByDocument(document)).isFalse();
    }
}
