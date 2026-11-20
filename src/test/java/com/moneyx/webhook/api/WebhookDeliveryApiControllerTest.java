package com.moneyx.webhook.api;

import com.moneyx.apikey.service.ApiKeyService;
import com.moneyx.event.domain.EventType;
import com.moneyx.merchant.service.MerchantService;
import com.moneyx.security.SecurityConfig;
import com.moneyx.webhook.domain.WebhookDelivery;
import com.moneyx.webhook.domain.WebhookDeliveryError;
import com.moneyx.webhook.domain.WebhookDeliveryStatus;
import com.moneyx.webhook.domain.WebhookEndpoint;
import com.moneyx.webhook.exception.InvalidDeliveryCursorException;
import com.moneyx.webhook.exception.WebhookDeliveryNotFailedException;
import com.moneyx.webhook.exception.WebhookDeliveryNotFoundException;
import com.moneyx.webhook.exception.WebhookEndpointDeletedException;
import com.moneyx.webhook.exception.WebhookEndpointUnavailableException;
import com.moneyx.webhook.service.WebhookDeliveryListQuery;
import com.moneyx.webhook.service.WebhookDeliveryPage;
import com.moneyx.webhook.service.WebhookDeliveryService;
import com.moneyx.webhook.service.WebhookEndpointService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.moneyx.security.TestAuthentication.activeMerchant;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rotas novas da Etapa 4: reativação, listagem de entregas e reenvio manual. */
@WebMvcTest(controllers = {WebhookEndpointController.class, WebhookDeliveryController.class})
@Import(SecurityConfig.class)
class WebhookDeliveryApiControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookEndpointService endpointService;

    @MockitoBean
    private WebhookDeliveryService deliveryService;

    @MockitoBean
    private ApiKeyService apiKeyService;

    @MockitoBean
    private MerchantService merchantService;

    private final UUID merchantId = UUID.randomUUID();
    private final UUID endpointId = UUID.randomUUID();

    private WebhookDelivery pendingAfterOneFailure() {
        WebhookDelivery delivery = WebhookDelivery.pending(UUID.randomUUID(), endpointId, merchantId, NOW);
        UUID token = delivery.claim(NOW, Duration.ofSeconds(30));
        delivery.recordFailure(token, WebhookDeliveryError.NON_2XX_RESPONSE, 500, 12, NOW, 0);
        return delivery;
    }

    // ---------- reativação ----------

    @Test
    void enableReturnsTheEndpointWithoutDisabledAt() throws Exception {
        WebhookEndpoint endpoint = WebhookEndpoint.create(endpointId, merchantId, "https://loja.com/hook",
                List.of(EventType.PAYMENT_PAID), "ciphertext", "whsec_ab12", NOW);
        when(endpointService.enable(eq(merchantId), any(), eq(endpointId))).thenReturn(endpoint);

        mockMvc.perform(post("/v1/webhook-endpoints/{id}/enable", endpointId).with(activeMerchant(merchantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(endpointId.toString()))
                .andExpect(jsonPath("$.disabledAt").isEmpty())
                .andExpect(jsonPath("$.secret").doesNotExist());
    }

    @Test
    void enablingADeletedEndpointIs409() throws Exception {
        when(endpointService.enable(eq(merchantId), any(), eq(endpointId))).thenThrow(new WebhookEndpointDeletedException());

        mockMvc.perform(post("/v1/webhook-endpoints/{id}/enable", endpointId).with(activeMerchant(merchantId)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("webhook_endpoint_deleted"));
    }

    // ---------- listagem de entregas ----------

    @Test
    void listsDeliveriesWithAttemptsErrorAndNextAttempt() throws Exception {
        WebhookDelivery delivery = pendingAfterOneFailure();
        when(deliveryService.list(eq(merchantId), eq(endpointId), any()))
                .thenReturn(new WebhookDeliveryPage(List.of(delivery), true));

        mockMvc.perform(get("/v1/webhook-endpoints/{id}/deliveries", endpointId).with(activeMerchant(merchantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(delivery.getId().toString()))
                .andExpect(jsonPath("$.data[0].eventId").value(delivery.getEventId().toString()))
                .andExpect(jsonPath("$.data[0].status").value("pending"))
                .andExpect(jsonPath("$.data[0].attempts").value(1))
                .andExpect(jsonPath("$.data[0].lastStatusCode").value(500))
                .andExpect(jsonPath("$.data[0].lastError").value("non_2xx_response"))
                .andExpect(jsonPath("$.data[0].nextAttemptAt").value("2026-11-20T12:01:00Z"))
                .andExpect(jsonPath("$.hasMore").value(true));

        verify(deliveryService).list(merchantId, endpointId, WebhookDeliveryListQuery.firstPage());
    }

    @Test
    void passesCursorLimitAndStatusFilter() throws Exception {
        UUID cursor = UUID.randomUUID();
        when(deliveryService.list(eq(merchantId), eq(endpointId), any())).thenReturn(new WebhookDeliveryPage(List.of(), false));

        mockMvc.perform(get("/v1/webhook-endpoints/{id}/deliveries", endpointId)
                        .param("limit", "5").param("startingAfter", cursor.toString()).param("status", "failed")
                        .with(activeMerchant(merchantId)))
                .andExpect(status().isOk());

        verify(deliveryService).list(merchantId, endpointId,
                new WebhookDeliveryListQuery(5, cursor, WebhookDeliveryStatus.FAILED));
    }

    @Test
    void unknownStatusOrInvalidLimitIs400() throws Exception {
        mockMvc.perform(get("/v1/webhook-endpoints/{id}/deliveries", endpointId).param("status", "lost")
                        .with(activeMerchant(merchantId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown delivery status: lost"));
        mockMvc.perform(get("/v1/webhook-endpoints/{id}/deliveries", endpointId).param("limit", "101")
                        .with(activeMerchant(merchantId)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(deliveryService);
    }

    @Test
    void foreignCursorIs400WithCode() throws Exception {
        when(deliveryService.list(eq(merchantId), eq(endpointId), any())).thenThrow(new InvalidDeliveryCursorException());

        mockMvc.perform(get("/v1/webhook-endpoints/{id}/deliveries", endpointId)
                        .param("startingAfter", UUID.randomUUID().toString()).with(activeMerchant(merchantId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_starting_after"));
    }

    // ---------- reenvio manual ----------

    @Test
    void retryReturnsTheRequeuedDelivery() throws Exception {
        WebhookDelivery delivery = pendingAfterOneFailure();
        when(deliveryService.retry(merchantId, delivery.getId())).thenReturn(delivery);

        mockMvc.perform(post("/v1/webhook-deliveries/{id}/retry", delivery.getId()).with(activeMerchant(merchantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    void retryErrorsAreTranslated() throws Exception {
        UUID id = UUID.randomUUID();
        when(deliveryService.retry(merchantId, id))
                .thenThrow(new WebhookDeliveryNotFailedException(WebhookDeliveryStatus.SUCCEEDED))
                .thenThrow(new WebhookEndpointUnavailableException())
                .thenThrow(new WebhookDeliveryNotFoundException());

        mockMvc.perform(post("/v1/webhook-deliveries/{id}/retry", id).with(activeMerchant(merchantId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("delivery_not_failed"))
                .andExpect(jsonPath("$.deliveryStatus").value("succeeded"));
        mockMvc.perform(post("/v1/webhook-deliveries/{id}/retry", id).with(activeMerchant(merchantId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("webhook_endpoint_unavailable"));
        mockMvc.perform(post("/v1/webhook-deliveries/{id}/retry", id).with(activeMerchant(merchantId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Webhook delivery not found"));
    }

    @Test
    void newRoutesRequireAuthentication() throws Exception {
        mockMvc.perform(post("/v1/webhook-endpoints/{id}/enable", endpointId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/webhook-endpoints/{id}/deliveries", endpointId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/webhook-deliveries/{id}/retry", UUID.randomUUID())).andExpect(status().isUnauthorized());

        verifyNoInteractions(endpointService, deliveryService);
    }
}
