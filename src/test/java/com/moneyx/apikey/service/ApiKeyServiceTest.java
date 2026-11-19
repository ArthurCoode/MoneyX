package com.moneyx.apikey.service;

import com.moneyx.apikey.domain.ApiKey;
import com.moneyx.apikey.domain.ApiKeyGenerator;
import com.moneyx.apikey.exception.ApiKeyLimitExceededException;
import com.moneyx.apikey.exception.ApiKeyNotFoundException;
import com.moneyx.apikey.exception.LastActiveApiKeyException;
import com.moneyx.apikey.repository.ApiKeyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Test
    void findsActiveKeyByTheHashOfThePlainText() {
        ApiKeyGenerator.GeneratedApiKey generated = ApiKeyGenerator.generate();
        ApiKey stored = ApiKey.issue(UUID.randomUUID(), generated);
        // A busca deve ser feita pelo HASH do segredo, nunca pelo segredo em si.
        when(repository.findByKeyHash(generated.hash())).thenReturn(Optional.of(stored));

        assertThat(service.findActiveByPlainText(generated.plainText())).contains(stored);
    }

    @Test
    void revokedKeyIsNotReturned() {
        ApiKeyGenerator.GeneratedApiKey generated = ApiKeyGenerator.generate();
        ApiKey stored = ApiKey.issue(UUID.randomUUID(), generated);
        stored.revoke();
        when(repository.findByKeyHash(generated.hash())).thenReturn(Optional.of(stored));

        assertThat(service.findActiveByPlainText(generated.plainText())).isEmpty();
    }

    @Test
    void unknownKeyIsNotReturned() {
        when(repository.findByKeyHash(any())).thenReturn(Optional.empty());

        assertThat(service.findActiveByPlainText("mx_test_unknown")).isEmpty();
    }

    @Test
    void blankKeyIsRejectedWithoutQueryingTheDatabase() {
        assertThat(service.findActiveByPlainText(" ")).isEmpty();
        assertThat(service.findActiveByPlainText(null)).isEmpty();
        verifyNoInteractions(repository);
    }

    // ---------- limite de chaves ativas ----------

    @Test
    void locksActiveKeysBeforeCountingThem() {
        UUID merchantId = UUID.randomUUID();
        when(repository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        service.issueFor(merchantId);

        // A ordem importa: contar antes de obter o lock permitiria furar o limite em requisições simultâneas.
        InOrder order = inOrder(repository);
        order.verify(repository).lockActiveByMerchantId(merchantId);
        order.verify(repository).countByMerchantIdAndRevokedAtIsNull(merchantId);
        order.verify(repository).save(any(ApiKey.class));
    }

    @Test
    void issuesKeyWhileBelowTheLimit() {
        UUID merchantId = UUID.randomUUID();
        when(repository.countByMerchantIdAndRevokedAtIsNull(merchantId))
                .thenReturn((long) ApiKeyService.MAX_ACTIVE_KEYS_PER_MERCHANT - 1);
        when(repository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.issueFor(merchantId).apiKey().isActive()).isTrue();
    }

    @Test
    void rejectsNewKeyWhenLimitIsReached() {
        UUID merchantId = UUID.randomUUID();
        when(repository.countByMerchantIdAndRevokedAtIsNull(merchantId))
                .thenReturn((long) ApiKeyService.MAX_ACTIVE_KEYS_PER_MERCHANT);

        assertThatThrownBy(() -> service.issueFor(merchantId))
                .isInstanceOf(ApiKeyLimitExceededException.class);
        verify(repository, never()).save(any());
    }

    // ---------- revogação ----------

    private ApiKey activeKeyOf(UUID merchantId) {
        return ApiKey.issue(merchantId, ApiKeyGenerator.generate());
    }

    @Test
    void revokesKeyWhenAnotherActiveKeyRemains() {
        UUID merchantId = UUID.randomUUID();
        ApiKey target = activeKeyOf(merchantId);
        when(repository.lockActiveByMerchantId(merchantId)).thenReturn(List.of(target, activeKeyOf(merchantId)));
        when(repository.findByIdAndMerchantId(target.getId(), merchantId)).thenReturn(Optional.of(target));

        service.revoke(merchantId, target.getId());

        assertThat(target.isActive()).isFalse();
    }

    @Test
    void cannotRevokeTheLastActiveKey() {
        UUID merchantId = UUID.randomUUID();
        ApiKey onlyKey = activeKeyOf(merchantId);
        when(repository.lockActiveByMerchantId(merchantId)).thenReturn(List.of(onlyKey));
        when(repository.findByIdAndMerchantId(onlyKey.getId(), merchantId)).thenReturn(Optional.of(onlyKey));

        assertThatThrownBy(() -> service.revoke(merchantId, onlyKey.getId()))
                .isInstanceOf(LastActiveApiKeyException.class);
        assertThat(onlyKey.isActive()).isTrue();
    }

    @Test
    void revokingAnAlreadyRevokedKeyIsIdempotent() {
        UUID merchantId = UUID.randomUUID();
        ApiKey revoked = activeKeyOf(merchantId);
        revoked.revoke();
        when(repository.lockActiveByMerchantId(merchantId)).thenReturn(List.of(activeKeyOf(merchantId)));
        when(repository.findByIdAndMerchantId(revoked.getId(), merchantId)).thenReturn(Optional.of(revoked));

        service.revoke(merchantId, revoked.getId()); // não lança exceção

        assertThat(revoked.isActive()).isFalse();
    }

    @Test
    void keyOfAnotherMerchantIsReportedAsNotFound() {
        UUID merchantId = UUID.randomUUID();
        UUID someoneElsesKeyId = UUID.randomUUID();
        // A busca filtra por id E merchantId: a chave de outro comerciante não é encontrada.
        when(repository.findByIdAndMerchantId(someoneElsesKeyId, merchantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(merchantId, someoneElsesKeyId))
                .isInstanceOf(ApiKeyNotFoundException.class);
    }

    @Test
    void locksActiveKeysBeforeReadingTheKeyToRevoke() {
        UUID merchantId = UUID.randomUUID();
        ApiKey target = activeKeyOf(merchantId);
        when(repository.lockActiveByMerchantId(merchantId)).thenReturn(List.of(target, activeKeyOf(merchantId)));
        when(repository.findByIdAndMerchantId(target.getId(), merchantId)).thenReturn(Optional.of(target));

        service.revoke(merchantId, target.getId());

        InOrder order = inOrder(repository);
        order.verify(repository).lockActiveByMerchantId(merchantId);
        order.verify(repository).findByIdAndMerchantId(target.getId(), merchantId);
    }
}
