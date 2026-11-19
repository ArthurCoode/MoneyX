package com.moneyx.apikey.domain;

import com.moneyx.apikey.domain.ApiKeyGenerator.GeneratedApiKey;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyTest {

    @Test
    void issuedKeyIsActiveAndStoresOnlyPrefix() {
        UUID merchantId = UUID.randomUUID();
        GeneratedApiKey generated = ApiKeyGenerator.generate();

        ApiKey apiKey = ApiKey.issue(merchantId, generated);

        assertThat(apiKey.getId()).isNotNull();
        assertThat(apiKey.getMerchantId()).isEqualTo(merchantId);
        assertThat(apiKey.getKeyPrefix()).isEqualTo(generated.displayPrefix());
        assertThat(apiKey.isActive()).isTrue();
        assertThat(apiKey.getRevokedAt()).isNull();
    }

    @Test
    void revokeMarksKeyAsInactive() {
        ApiKey apiKey = ApiKey.issue(UUID.randomUUID(), ApiKeyGenerator.generate());

        apiKey.revoke();

        assertThat(apiKey.isActive()).isFalse();
        assertThat(apiKey.getRevokedAt()).isNotNull();
    }

    @Test
    void cannotRevokeTwice() {
        ApiKey apiKey = ApiKey.issue(UUID.randomUUID(), ApiKeyGenerator.generate());
        apiKey.revoke();

        assertThatThrownBy(apiKey::revoke).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requiresMerchantId() {
        assertThatThrownBy(() -> ApiKey.issue(null, ApiKeyGenerator.generate()))
                .isInstanceOf(NullPointerException.class);
    }
}
