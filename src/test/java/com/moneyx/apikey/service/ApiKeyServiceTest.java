package com.moneyx.apikey.service;

import com.moneyx.apikey.domain.ApiKey;
import com.moneyx.apikey.domain.ApiKeyGenerator;
import com.moneyx.apikey.repository.ApiKeyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository repository;

    @InjectMocks
    private ApiKeyService service;

    @Test
    void issuesKeyAndPersistsOnlyItsHash() {
        UUID merchantId = UUID.randomUUID();
        when(repository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        IssuedApiKey issued = service.issueFor(merchantId);

        // O segredo é devolvido para o chamador...
        assertThat(issued.plainText()).startsWith("mx_test_");
        assertThat(issued.apiKey().getMerchantId()).isEqualTo(merchantId);
        assertThat(issued.apiKey().isActive()).isTrue();

        // ...e o que vai para o banco corresponde ao hash desse segredo.
        ArgumentCaptor<ApiKey> saved = ArgumentCaptor.forClass(ApiKey.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(issued.apiKey());
        assertThat(issued.plainText()).startsWith(saved.getValue().getKeyPrefix());
    }

    @Test
    void eachIssuedKeyIsDifferent() {
        when(repository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));
        UUID merchantId = UUID.randomUUID();

        IssuedApiKey first = service.issueFor(merchantId);
        IssuedApiKey second = service.issueFor(merchantId);

        assertThat(first.plainText()).isNotEqualTo(second.plainText());
        assertThat(ApiKeyGenerator.hash(first.plainText())).isNotEqualTo(ApiKeyGenerator.hash(second.plainText()));
    }

    @Test
    void toStringDoesNotLeakTheSecret() {
        when(repository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        IssuedApiKey issued = service.issueFor(UUID.randomUUID());

        assertThat(issued.toString()).doesNotContain(issued.plainText()).contains("***");
    }
}
