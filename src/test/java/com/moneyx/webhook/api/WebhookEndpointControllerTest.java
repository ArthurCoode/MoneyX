package com.moneyx.webhook.api;

import com.moneyx.apikey.service.ApiKeyService;
import com.moneyx.event.domain.EventType;
import com.moneyx.merchant.service.MerchantService;
import com.moneyx.security.SecurityConfig;
import com.moneyx.webhook.domain.WebhookEndpoint;
import com.moneyx.webhook.domain.WebhookEndpointAction;
import com.moneyx.webhook.domain.WebhookEndpointHistory;
import com.moneyx.webhook.exception.InvalidWebhookUrlException;
import com.moneyx.webhook.exception.WebhookEndpointLimitExceededException;
import com.moneyx.webhook.exception.WebhookEndpointNotFoundException;
import com.moneyx.webhook.service.CreatedWebhookEndpoint;
import com.moneyx.webhook.service.WebhookEndpointService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.moneyx.security.TestAuthentication.activeMerchant;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

@WebMvcTest(WebhookEndpointController.class)
@Import(SecurityConfig.class)
class WebhookEndpointControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    private static final String SECRET = "whsec_ab12" + "x".repeat(39);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookEndpointService service;

    @MockitoBean
    private com.moneyx.webhook.service.WebhookDeliveryService deliveryService;

    @MockitoBean
    private ApiKeyService apiKeyService;

    @MockitoBean
    private MerchantService merchantService;

    private final UUID merchantId = UUID.randomUUID();

    private WebhookEndpoint endpoint() {
        return WebhookEndpoint.create(UUID.randomUUID(), merchantId, "https://loja.com/hook",
                List.of(EventType.PAYMENT_PAID), "ciphertext-not-shown", "whsec_ab12", NOW);
    }

    @Test
    void createReturns201WithTheSecretOnceAndNoStore() throws Exception {
        WebhookEndpoint endpoint = endpoint();
        when(service.create(eq(merchantId), any(), eq("https://loja.com/hook"), eq(List.of("payment.paid"))))
                .thenReturn(new CreatedWebhookEndpoint(endpoint, SECRET));

        mockMvc.perform(post("/v1/webhook-endpoints")
                        .with(activeMerchant(merchantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://loja.com/hook", "enabledEvents": ["payment.paid"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/v1/webhook-endpoints/" + endpoint.getId()))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(endpoint.getId().toString()))
                .andExpect(jsonPath("$.secret").value(SECRET))
                .andExpect(jsonPath("$.secretPrefix").value("whsec_ab12"))
                .andExpect(jsonPath("$.enabledEvents[0]").value("payment.paid"))
                .andExpect(content().string(not(containsString("ciphertext"))));
    }

    @Test
    void createWithoutEnabledEventsPassesNullMeaningAll() throws Exception {
        when(service.create(eq(merchantId), any(), anyString(), eq(null)))
                .thenReturn(new CreatedWebhookEndpoint(endpoint(), SECRET));

        mockMvc.perform(post("/v1/webhook-endpoints")
                        .with(activeMerchant(merchantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://loja.com/hook"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void createReturns400WhenUrlIsMissing() throws Exception {
        mockMvc.perform(post("/v1/webhook-endpoints")
                        .with(activeMerchant(merchantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("url"));

        verifyNoInteractions(service);
    }

    @Test
    void createReturns400WithCodeForUnsafeUrl() throws Exception {
        when(service.create(eq(merchantId), any(), anyString(), any()))
                .thenThrow(new InvalidWebhookUrlException("url must not point to a private or internal address"));

        mockMvc.perform(post("/v1/webhook-endpoints")
                        .with(activeMerchant(merchantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://10.0.0.5/admin"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("invalid_webhook_url"))
                .andExpect(jsonPath("$.detail").value("url must not point to a private or internal address"));
    }

    @Test
    void createReturns400ForUnknownEventType() throws Exception {
        when(service.create(eq(merchantId), any(), anyString(), any()))
                .thenThrow(new IllegalArgumentException("Unknown event type: payment.refunded"));

        mockMvc.perform(post("/v1/webhook-endpoints")
                        .with(activeMerchant(merchantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://loja.com/hook", "enabledEvents": ["payment.refunded"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown event type: payment.refunded"));
    }

    @Test
    void createReturns409WhenLimitIsReached() throws Exception {
        when(service.create(eq(merchantId), any(), anyString(), any()))
                .thenThrow(new WebhookEndpointLimitExceededException(5));

        mockMvc.perform(post("/v1/webhook-endpoints")
                        .with(activeMerchant(merchantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://loja.com/hook"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("webhook_endpoint_limit_reached"));
    }

    @Test
    void listAndGetNeverExposeTheSecret() throws Exception {
        WebhookEndpoint endpoint = endpoint();
        when(service.listActive(merchantId)).thenReturn(List.of(endpoint));
        when(service.get(merchantId, endpoint.getId())).thenReturn(endpoint);

        mockMvc.perform(get("/v1/webhook-endpoints").with(activeMerchant(merchantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(endpoint.getId().toString()))
                .andExpect(jsonPath("$.data[0].secretPrefix").value("whsec_ab12"))
                .andExpect(jsonPath("$.data[0].secret").doesNotExist())
                .andExpect(content().string(not(containsString("ciphertext"))));

        mockMvc.perform(get("/v1/webhook-endpoints/{id}", endpoint.getId()).with(activeMerchant(merchantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").doesNotExist())
                .andExpect(jsonPath("$.deletedAt").isEmpty());
    }

    @Test
    void getReturns404ForAnotherMerchantsEndpoint() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(merchantId, id)).thenThrow(new WebhookEndpointNotFoundException());

        mockMvc.perform(get("/v1/webhook-endpoints/{id}", id).with(activeMerchant(merchantId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Webhook endpoint not found"));
    }

    @Test
    void deleteReturns204AndPassesTheApiKeyUsedInTheRequest() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/v1/webhook-endpoints/{id}", id).with(activeMerchant(merchantId)))
                .andExpect(status().isNoContent());

        verify(service).delete(eq(merchantId), any(UUID.class), eq(id));
    }

    @Test
    void historyReturnsTheTimelineWithSnapshotAsObject() throws Exception {
        WebhookEndpoint endpoint = endpoint();
        UUID keyId = UUID.randomUUID();
        WebhookEndpointHistory created = WebhookEndpointHistory.of(endpoint, WebhookEndpointAction.CREATED, keyId,
                "{\"url\": \"https://loja.com/hook\", \"enabledEvents\": [\"payment.paid\"]}", NOW);
        when(service.history(merchantId, endpoint.getId())).thenReturn(List.of(created));

        mockMvc.perform(get("/v1/webhook-endpoints/{id}/history", endpoint.getId()).with(activeMerchant(merchantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].action").value("created"))
                .andExpect(jsonPath("$.data[0].apiKeyId").value(keyId.toString()))
                .andExpect(jsonPath("$.data[0].occurredAt").value("2026-11-20T12:00:00Z"))
                .andExpect(jsonPath("$.data[0].snapshot.url").value("https://loja.com/hook"))
                .andExpect(jsonPath("$.data[0].snapshot.enabledEvents[0]").value("payment.paid"));
    }

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/v1/webhook-endpoints")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/webhook-endpoints").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void createdResponseToStringMasksTheSecret() {
        var response = com.moneyx.webhook.api.dto.CreatedWebhookEndpointResponse.from(
                new CreatedWebhookEndpoint(endpoint(), SECRET));

        assertThatMasked(response.toString());
        assertThatMasked(new CreatedWebhookEndpoint(endpoint(), SECRET).toString());
    }

    private static void assertThatMasked(String text) {
        org.assertj.core.api.Assertions.assertThat(text).doesNotContain(SECRET).contains("***");
    }
}
