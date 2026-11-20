package com.moneyx.event.api;

import com.moneyx.apikey.service.ApiKeyService;
import com.moneyx.event.domain.Event;
import com.moneyx.event.domain.EventType;
import com.moneyx.event.exception.EventNotFoundException;
import com.moneyx.event.exception.InvalidEventCursorException;
import com.moneyx.event.service.EventListQuery;
import com.moneyx.event.service.EventPage;
import com.moneyx.event.service.EventService;
import com.moneyx.merchant.service.MerchantService;
import com.moneyx.security.SecurityConfig;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@Import(SecurityConfig.class)
class EventControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService service;

    @MockitoBean
    private ApiKeyService apiKeyService;

    @MockitoBean
    private MerchantService merchantService;

    private final UUID merchantId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();

    private Event paidEvent() {
        return Event.of(merchantId, EventType.PAYMENT_PAID, paymentId,
                "{\"id\": \"" + paymentId + "\", \"status\": \"PAID\", \"amount\": 4990}", NOW);
    }

    @Test
    void getReturnsTheEnvelopeWithDataAsAJsonObject() throws Exception {
        Event event = paidEvent();
        when(service.get(merchantId, event.getId())).thenReturn(event);

        mockMvc.perform(get("/v1/events/{id}", event.getId()).with(activeMerchant(merchantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(event.getId().toString()))
                .andExpect(jsonPath("$.type").value("payment.paid"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-20T12:00:00Z"))
                // data é um OBJETO (não uma string com JSON dentro)
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.amount").value(4990))
                .andExpect(jsonPath("$.data.id").value(paymentId.toString()));
    }

    @Test
    void getReturns404WhenEventDoesNotBelongToMerchant() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(merchantId, id)).thenThrow(new EventNotFoundException());

        mockMvc.perform(get("/v1/events/{id}", id).with(activeMerchant(merchantId)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Event not found"));
    }

    @Test
    void listReturnsEnvelopeWithDataAndHasMore() throws Exception {
        Event event = paidEvent();
        when(service.list(eq(merchantId), any())).thenReturn(new EventPage(List.of(event), true));

        mockMvc.perform(get("/v1/events").with(activeMerchant(merchantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].type").value("payment.paid"))
                .andExpect(jsonPath("$.data[0].data.status").value("PAID"))
                .andExpect(jsonPath("$.hasMore").value(true));

        verify(service).list(merchantId, EventListQuery.firstPage());
    }

    @Test
    void listPassesPaginationAndTypeFilterToTheService() throws Exception {
        UUID cursor = UUID.randomUUID();
        when(service.list(eq(merchantId), any())).thenReturn(new EventPage(List.of(), false));

        mockMvc.perform(get("/v1/events")
                        .param("limit", "5")
                        .param("startingAfter", cursor.toString())
                        .param("type", "payment.expired")
                        .with(activeMerchant(merchantId)))
                .andExpect(status().isOk());

        verify(service).list(merchantId, new EventListQuery(5, cursor, EventType.PAYMENT_EXPIRED));
    }

    @Test
    void listReturns400ForUnknownType() throws Exception {
        mockMvc.perform(get("/v1/events").param("type", "payment.refunded").with(activeMerchant(merchantId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Unknown event type: payment.refunded"));

        verifyNoInteractions(service);
    }

    @Test
    void listReturns400WhenLimitIsOutOfRange() throws Exception {
        mockMvc.perform(get("/v1/events").param("limit", "0").with(activeMerchant(merchantId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("limit must be between 1 and 100"));

        verifyNoInteractions(service);
    }

    @Test
    void listReturns400WithCodeForForeignCursor() throws Exception {
        when(service.list(eq(merchantId), any())).thenThrow(new InvalidEventCursorException());

        mockMvc.perform(get("/v1/events")
                        .param("startingAfter", UUID.randomUUID().toString())
                        .with(activeMerchant(merchantId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_starting_after"));
    }

    @Test
    void eventsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/v1/events")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/events/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }
}
