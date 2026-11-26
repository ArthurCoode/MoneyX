package com.moneyx.merchant.service;

import com.moneyx.apikey.domain.ApiKey;
import com.moneyx.apikey.domain.ApiKeyGenerator;
import com.moneyx.apikey.domain.ApiKeyGenerator.GeneratedApiKey;
import com.moneyx.apikey.service.ApiKeyService;
import com.moneyx.apikey.service.IssuedApiKey;
import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.domain.MerchantStatus;
import com.moneyx.merchant.exception.DuplicateMerchantException;
import com.moneyx.merchant.exception.MerchantNotFoundException;
import com.moneyx.merchant.repository.MerchantRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitários do serviço com Mockito.
 *
 * O repositório é substituído por um mock: um objeto falso cujo comportamento
 * definimos em cada teste. Isso isola a lógica do serviço do banco de dados.
 */
@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock
    private MerchantRepository repository;

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private EmailVerificationService emailVerification;

    @InjectMocks
    private MerchantService service;

    @Test
    void registersMerchantAndIssuesFirstApiKey() {
        when(repository.existsByDocument("12345678000190")).thenReturn(false);
        when(repository.existsByEmail("contato@loja.com")).thenReturn(false);
        when(repository.saveAndFlush(any(Merchant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(apiKeyService.issueFor(any(UUID.class))).thenAnswer(inv -> {
            GeneratedApiKey generated = ApiKeyGenerator.generate();
            return new IssuedApiKey(ApiKey.issue(inv.getArgument(0), generated), generated.plainText());
        });

        MerchantRegistration registration =
                service.register("Loja LTDA", "12.345.678/0001-90", "Contato@Loja.com");

        Merchant merchant = registration.merchant();
        assertThat(merchant.getDocument()).isEqualTo("12345678000190");
        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.ACTIVE);
        verify(repository).saveAndFlush(merchant);

        // A chave é emitida para o comerciante recém-criado
        verify(apiKeyService).issueFor(merchant.getId());
        assertThat(registration.apiKey().apiKey().getMerchantId()).isEqualTo(merchant.getId());
        assertThat(registration.apiKey().plainText()).startsWith("mx_test_");
    }

    @Test
    void rejectsDuplicateDocumentWithoutSaving() {
        when(repository.existsByDocument("12345678000190")).thenReturn(true);

        assertThatThrownBy(() -> service.register("Loja LTDA", "12.345.678/0001-90", "a@loja.com"))
                .isInstanceOf(DuplicateMerchantException.class)
                .hasMessageContaining("document");

        verify(repository, never()).saveAndFlush(any());
        verify(apiKeyService, never()).issueFor(any());
    }

    @Test
    void rejectsDuplicateEmailWithoutSaving() {
        when(repository.existsByDocument("12345678000190")).thenReturn(false);
        when(repository.existsByEmail("a@loja.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("Loja LTDA", "12345678000190", "A@loja.com"))
                .isInstanceOf(DuplicateMerchantException.class)
                .hasMessageContaining("email");

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void translatesDatabaseUniqueViolationFromRaceCondition() {
        when(repository.existsByDocument(any())).thenReturn(false);
        when(repository.existsByEmail(any())).thenReturn(false);
        when(repository.saveAndFlush(any(Merchant.class)))
                .thenThrow(new DataIntegrityViolationException("uk_merchants_document"));

        assertThatThrownBy(() -> service.register("Loja LTDA", "12345678000190", "a@loja.com"))
                .isInstanceOf(DuplicateMerchantException.class);

        verify(apiKeyService, never()).issueFor(any());
    }

    @Test
    void invalidInputFailsBeforeTouchingRepository() {
        assertThatThrownBy(() -> service.register("Loja", "123", "a@loja.com"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).existsByDocument(any());
    }

    @Test
    void getByIdReturnsMerchant() {
        Merchant merchant = new Merchant("Loja LTDA", "12345678000190", "a@loja.com");
        when(repository.findById(merchant.getId())).thenReturn(Optional.of(merchant));

        assertThat(service.getById(merchant.getId())).isSameAs(merchant);
    }

    @Test
    void getByIdThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(MerchantNotFoundException.class)
                .hasMessageContaining(id.toString());
    }
}
