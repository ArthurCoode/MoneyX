package com.moneyx.apikey.api;

import com.moneyx.apikey.domain.ApiKey;
import com.moneyx.apikey.domain.ApiKeyGenerator;
import com.moneyx.apikey.domain.ApiKeyGenerator.GeneratedApiKey;
import com.moneyx.apikey.exception.ApiKeyLimitExceededException;
import com.moneyx.apikey.exception.ApiKeyNotFoundException;
import com.moneyx.apikey.exception.LastActiveApiKeyException;
import com.moneyx.apikey.service.ApiKeyService;
import com.moneyx.apikey.service.IssuedApiKey;
import com.moneyx.merchant.service.MerchantService;
import com.moneyx.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static com.moneyx.security.TestAuthentication.activeMerchant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiKeyController.class)
@Import(SecurityConfig.class)
class ApiKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyService service;

    @MockitoBean
    private MerchantService merchantService; // dependência do filtro de API Key

    private final UUID merchantId = UUID.randomUUID();

    // ---------- POST /v1/api-keys ----------

    @Test
    void createReturns201WithSecretAndNoStore() throws Exception {
        GeneratedApiKey generated = ApiKeyGenerator.generate();
        IssuedApiKey issued = new IssuedApiKey(ApiKey.issue(merchantId, generated), generated.plainText());
        when(service.issueFor(merchantId)).thenReturn(issued);

        mockMvc.perform(post("/v1/api-keys").with(activeMerchant(merchantId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(issued.apiKey().getId().toString()))
                .andExpect(jsonPath("$.key").value(generated.plainText()))
                .andExpect(jsonPath("$.prefix").value(generated.displayPrefix()))
                .andExpect(jsonPath("$.keyHash").doesNotExist());
    }

    @Test
    void createReturns409WithCodeWhenLimitIsReached() throws Exception {
        when(service.issueFor(merchantId)).thenThrow(new ApiKeyLimitExceededException(5));

        mockMvc.perform(post("/v1/api-keys").with(activeMerchant(merchantId)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("api_key_limit_reached"));
    }

    // ---------- GET /v1/api-keys ----------

    @Test
    void listReturnsKeysInDataEnvelopeWithoutSecrets() throws Exception {
        GeneratedApiKey activeSecret = ApiKeyGenerator.generate();
        ApiKey active = ApiKey.issue(merchantId, activeSecret);
        ApiKey revoked = ApiKey.issue(merchantId, ApiKeyGenerator.generate());
        revoked.revoke();
        when(service.listFor(merchantId)).thenReturn(List.of(active, revoked));

        String body = mockMvc.perform(get("/v1/api-keys").with(activeMerchant(merchantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(active.getId().toString()))
                .andExpect(jsonPath("$.data[0].prefix").value(activeSecret.displayPrefix()))
                .andExpect(jsonPath("$.data[0].active").value(true))
                .andExpect(jsonPath("$.data[0].revokedAt").doesNotExist())
                .andExpect(jsonPath("$.data[1].active").value(false))
                .andExpect(jsonPath("$.data[1].revokedAt").exists())
                .andExpect(jsonPath("$.data[0].key").doesNotExist())
                .andExpect(jsonPath("$.data[0].keyHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(activeSecret.plainText());
    }

    // ---------- DELETE /v1/api-keys/{id} ----------

    @Test
    void revokeReturns204() throws Exception {
        UUID keyId = UUID.randomUUID();

        mockMvc.perform(delete("/v1/api-keys/{id}", keyId).with(activeMerchant(merchantId)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        // O dono vem do principal, e não de algo que o cliente enviou.
        verify(service).revoke(merchantId, keyId);
    }

    @Test
    void revokeReturns404ForUnknownOrForeignKey() throws Exception {
        UUID keyId = UUID.randomUUID();
        doThrow(new ApiKeyNotFoundException()).when(service).revoke(merchantId, keyId);

        mockMvc.perform(delete("/v1/api-keys/{id}", keyId).with(activeMerchant(merchantId)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("API key not found"));
    }

    @Test
    void revokeReturns409WithCodeForLastActiveKey() throws Exception {
        UUID keyId = UUID.randomUUID();
        doThrow(new LastActiveApiKeyException()).when(service).revoke(merchantId, keyId);

        mockMvc.perform(delete("/v1/api-keys/{id}", keyId).with(activeMerchant(merchantId)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("last_active_api_key"));
    }

    @Test
    void revokeReturns400WhenIdIsNotUuid() throws Exception {
        mockMvc.perform(delete("/v1/api-keys/{id}", "abc").with(activeMerchant(merchantId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ---------- segurança ----------

    @Test
    void allEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(post("/v1/api-keys")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/api-keys")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/v1/api-keys/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }
}
