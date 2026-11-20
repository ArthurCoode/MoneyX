package com.moneyx.event.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import com.moneyx.event.api.dto.EventListResponse;
import com.moneyx.event.service.EventView;
import com.moneyx.event.domain.EventType;
import com.moneyx.event.service.EventListQuery;
import com.moneyx.event.service.EventService;
import com.moneyx.security.AuthenticatedMerchant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Consulta de eventos. Somente leitura: eventos são criados pelo próprio MoneyX quando algo acontece.
 *
 * Serve para o comerciante reconciliar: se o sistema dele ficou fora do ar e perdeu avisos, ele lista os
 * eventos desde o último que processou.
 */
@Tag(name = "Events")
@RestController
@RequestMapping("/v1/events")
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @Operation(operationId = "getEvent", summary = "Consultar evento",
            description = "Mesmo conteúdo enviado por webhook.")
    @ApiResponse(responseCode = "200", description = "Evento.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @GetMapping("/{id}")
    public EventView get(@AuthenticationPrincipal AuthenticatedMerchant principal, @PathVariable UUID id) {
        return EventView.from(service.get(principal.merchantId(), id));
    }

    /** ?limit=10&startingAfter=<id>&type=payment.paid */
    @Operation(operationId = "listEvents", summary = "Listar eventos",
            description = "Mais recentes primeiro, paginação por cursor, filtro `type`. Use para recuperar eventos perdidos.")
    @ApiResponse(responseCode = "200", description = "Página de eventos.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @GetMapping
    public EventListResponse list(
            @AuthenticationPrincipal AuthenticatedMerchant principal,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) UUID startingAfter,
            @RequestParam(required = false) String type) {

        EventType eventType = type == null || type.isBlank() ? null : EventType.fromValue(type.trim());
        EventListQuery query = new EventListQuery(limit, startingAfter, eventType);
        return EventListResponse.from(service.list(principal.merchantId(), query));
    }
}
