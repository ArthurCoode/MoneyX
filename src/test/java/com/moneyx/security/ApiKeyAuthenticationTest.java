package com.moneyx.security;

import com.moneyx.apikey.domain.ApiKey;
import com.moneyx.apikey.domain.ApiKeyGenerator;
import com.moneyx.apikey.domain.ApiKeyGenerator.GeneratedApiKey;
import com.moneyx.apikey.service.ApiKeyService;
import com.moneyx.merchant.api.MerchantController;
import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.service.MerchantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes do filtro de API Key + regras de autorização do SecurityConfig.
 *
 * Usa GET /v1/merchants/me como exemplo de rota protegida e POST /v1/merchants como rota pública.
 * Os serviços são mocks: aqui testamos a decisão de segurança, não o banco.
 * (A busca por hash e o filtro de chaves revogadas são testados em ApiKeyServiceTest.)
 */
@WebMvcTest(MerchantController.class)
@Import(SecurityConfig.class)
class ApiKeyAuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyService apiKeyService;

    @MockitoBean
    private MerchantService merchantService;

    /** Limites mostrados no /me (módulo payment). */
    @MockitoBean
    private com.moneyx.payment.service.PaymentVolumeLimitQuery volumeLimitQuery;

    @MockitoBean
    private com.moneyx.merchant.service.EmailVerificationService emailVerificationService;

    @org.junit.jupiter.api.BeforeEach
    void stubVolumeLimits() {
        org.mockito.Mockito.when(volumeLimitQuery.effectiveFor(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.moneyx.payment.service.VolumeLimits(20_000_000, 200_000_000));
    }

    private Merchant merchant;
    private GeneratedApiKey key;

    @BeforeEach
    void setUp() {
        merchant = new Merchant("Loja LTDA", "12345678000190", "contato@loja.com");
        key = ApiKeyGenerator.generate();
        when(merchantService.getById(merchant.getId())).thenReturn(merchant);
    }

    private void keyIsValid() {
        when(apiKeyService.findActiveByPlainText(key.plainText()))
                .thenReturn(Optional.of(ApiKey.issue(merchant.getId(), key)));
    }

    private ResultActions getOwnMerchant(String authorizationHeader) throws Exception {
        var request = get("/v1/merchants/me");
        if (authorizationHeader != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
        return mockMvc.perform(request);
    }

    private static void expectUnauthorizedProblem(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("A valid API key is required"));
    }

    // ---------- 401: não sabemos quem é ----------

    @Test
    void returns401WhenAuthorizationHeaderIsMissing() throws Exception {
        expectUnauthorizedProblem(getOwnMerchant(null));
        verifyNoInteractions(apiKeyService);
    }

    @Test
    void returns401WhenSchemeIsNotBearer() throws Exception {
        expectUnauthorizedProblem(getOwnMerchant("Basic " + key.plainText()));
        verifyNoInteractions(apiKeyService);
    }

    @Test
    void returns401WhenBearerTokenIsEmpty() throws Exception {
        expectUnauthorizedProblem(getOwnMerchant("Bearer "));
    }

    @Test
    void returns401WhenKeyIsUnknownOrRevoked() throws Exception {
        // O serviço devolve vazio tanto para chave inexistente quanto para revogada.
        when(apiKeyService.findActiveByPlainText(anyString())).thenReturn(Optional.empty());

        expectUnauthorizedProblem(getOwnMerchant("Bearer mx_test_doesNotExist"));
        verify(merchantService, never()).getById(any());
    }

    @Test
    void invalidKeyIsRejectedEvenOnPublicEndpoint() throws Exception {
        when(apiKeyService.findActiveByPlainText(anyString())).thenReturn(Optional.empty());

        expectUnauthorizedProblem(mockMvc.perform(post("/v1/merchants")
                .header(HttpHeaders.AUTHORIZATION, "Bearer mx_test_wrong")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"legalName": "Loja LTDA", "document": "12345678000190", "email": "contato@loja.com"}
                        """)));

        verify(merchantService, never()).register(any(), any(), any());
    }

    @Test
    void responseNeverEchoesTheKey() throws Exception {
        when(apiKeyService.findActiveByPlainText(anyString())).thenReturn(Optional.empty());

        String body = getOwnMerchant("Bearer " + key.plainText())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(key.plainText());
    }

    // ---------- 403: sabemos quem é, mas não pode operar ----------

    @Test
    void returns403WhenMerchantIsSuspended() throws Exception {
        keyIsValid();
        merchant.suspend();

        getOwnMerchant("Bearer " + key.plainText())
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void returns403WhenMerchantIsClosed() throws Exception {
        keyIsValid();
        merchant.close();

        getOwnMerchant("Bearer " + key.plainText())
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ---------- chave válida ----------

    @Test
    void validKeyOfActiveMerchantReachesTheController() throws Exception {
        keyIsValid();

        getOwnMerchant("Bearer " + key.plainText())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(merchant.getId().toString()));
    }

    @Test
    void bearerSchemeIsCaseInsensitive() throws Exception {
        keyIsValid();

        getOwnMerchant("bearer " + key.plainText())
                .andExpect(status().isOk());
    }

    @Test
    void authenticationDoesNotCreateHttpSession() throws Exception {
        keyIsValid();

        MvcResult result = getOwnMerchant("Bearer " + key.plainText())
                .andExpect(status().isOk())
                .andReturn();

        // Stateless: nenhuma sessão e nenhum cookie de sessão.
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }
}
