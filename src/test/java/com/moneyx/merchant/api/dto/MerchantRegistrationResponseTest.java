package com.moneyx.merchant.api.dto;

import com.moneyx.apikey.domain.ApiKey;
import com.moneyx.apikey.domain.ApiKeyGenerator;
import com.moneyx.apikey.domain.ApiKeyGenerator.GeneratedApiKey;
import com.moneyx.apikey.service.IssuedApiKey;
import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.service.MerchantRegistration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Os records que "embrulham" a chave recém-criada não sobrescrevem toString: eles dependem do
 * toString mascarado dos componentes (IssuedApiKey, CreatedApiKeyResponse). Este teste trava essa
 * garantia implícita, pois um log como log.info("{}", registration) não pode vazar o segredo.
 */
class MerchantRegistrationResponseTest {

    @Test
    void wrappersOfTheNewKeyDoNotLeakTheSecretInToString() {
        Merchant merchant = new Merchant("Loja LTDA", "12345678000190", "contato@loja.com");
        GeneratedApiKey generated = ApiKeyGenerator.generate();
        MerchantRegistration registration = new MerchantRegistration(
                merchant, new IssuedApiKey(ApiKey.issue(merchant.getId(), generated), generated.plainText()));

        MerchantRegistrationResponse response = MerchantRegistrationResponse.from(registration);

        assertThat(registration.toString()).doesNotContain(generated.plainText());
        assertThat(response.toString()).doesNotContain(generated.plainText());
        // A serialização JSON, por outro lado, DEVE conter a chave (é o único momento em que ela é entregue).
        assertThat(response.apiKey().key()).isEqualTo(generated.plainText());
    }
}
