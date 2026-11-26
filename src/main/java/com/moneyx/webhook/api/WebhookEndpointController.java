package com.moneyx.webhook.api;

import com.moneyx.webhook.domain.Actor;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import com.moneyx.security.AuthenticatedMerchant;
import com.moneyx.webhook.api.dto.CreateWebhookEndpointRequest;
import com.moneyx.webhook.api.dto.CreatedWebhookEndpointResponse;
import com.moneyx.webhook.api.dto.WebhookDeliveryListResponse;
import com.moneyx.webhook.api.dto.WebhookEndpointHistoryResponse;
import com.moneyx.webhook.api.dto.WebhookEndpointListResponse;
import com.moneyx.webhook.api.dto.WebhookEndpointResponse;
import com.moneyx.webhook.domain.WebhookDeliveryStatus;
import com.moneyx.webhook.service.CreatedWebhookEndpoint;
import com.moneyx.webhook.service.WebhookDeliveryListQuery;
import com.moneyx.webhook.service.WebhookDeliveryService;
import com.moneyx.webhook.service.WebhookEndpointService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Locale;
import java.util.UUID;

@Tag(name = "Webhook Endpoints")
@RestController
@RequestMapping("/v1/webhook-endpoints")
public class WebhookEndpointController {

    private final WebhookEndpointService service;
    private final WebhookDeliveryService deliveryService;

    public WebhookEndpointController(WebhookEndpointService service, WebhookDeliveryService deliveryService) {
        this.service = service;
        this.deliveryService = deliveryService;
    }

    /** 201 com o segredo de assinatura (única vez), Cache-Control: no-store. */
    @Operation(operationId = "createWebhookEndpoint", summary = "Cadastrar endpoint de webhook",
            description = "Somente `https`, sem destinos internos. O segredo de assinatura (`whsec_...`) aparece **somente** nesta resposta. Máximo de 5 endpoints.")
    @ApiResponse(responseCode = "201", description = "Endpoint criado.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    @PostMapping
    public ResponseEntity<CreatedWebhookEndpointResponse> create(
            @AuthenticationPrincipal AuthenticatedMerchant principal,
            @Valid @RequestBody CreateWebhookEndpointRequest request) {

        CreatedWebhookEndpoint created = service.create(
                principal.merchantId(), actorOf(principal), request.url(), request.enabledEvents());

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.endpoint().getId()).toUri();

        return ResponseEntity.created(location)
                .cacheControl(CacheControl.noStore())
                .body(CreatedWebhookEndpointResponse.from(created));
    }

    /** Endpoints ATIVOS. */
    @Operation(operationId = "listWebhookEndpoints", summary = "Listar endpoints",
            description = "Endpoints não removidos (inclusive desativados).")
    @ApiResponse(responseCode = "200", description = "Endpoints.")
    @GetMapping
    public WebhookEndpointListResponse list(@AuthenticationPrincipal AuthenticatedMerchant principal) {
        return new WebhookEndpointListResponse(
                service.listActive(principal.merchantId()).stream().map(WebhookEndpointResponse::from).toList());
    }

    @Operation(operationId = "getWebhookEndpoint", summary = "Consultar endpoint",
            description = "Inclusive removidos (`deletedAt`) e desativados (`disabledAt`).")
    @ApiResponse(responseCode = "200", description = "Endpoint.")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @GetMapping("/{id}")
    public WebhookEndpointResponse get(@AuthenticationPrincipal AuthenticatedMerchant principal, @PathVariable UUID id) {
        return WebhookEndpointResponse.from(service.get(principal.merchantId(), id));
    }

    /** Remoção lógica e idempotente: 204 também na repetição. */
    @Operation(operationId = "deleteWebhookEndpoint", summary = "Remover endpoint",
            description = "Remoção lógica e idempotente. A linha do tempo continua disponível.")
    @ApiResponse(responseCode = "204", description = "Removido (ou já estava).")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedMerchant principal, @PathVariable UUID id) {
        service.delete(principal.merchantId(), actorOf(principal), id);
        return ResponseEntity.noContent().build();
    }

    /** Reativa um endpoint desativado por falhas. Idempotente. 409 se o endpoint foi removido. */
    @Operation(operationId = "enableWebhookEndpoint", summary = "Reativar endpoint",
            description = "Reativa um endpoint desativado por falhas. Idempotente.")
    @ApiResponse(responseCode = "200", description = "Endpoint ativo.")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    @PostMapping("/{id}/enable")
    public WebhookEndpointResponse enable(@AuthenticationPrincipal AuthenticatedMerchant principal, @PathVariable UUID id) {
        return WebhookEndpointResponse.from(service.enable(principal.merchantId(), actorOf(principal), id));
    }

    /** Entregas do endpoint, mais recentes primeiro: ?limit=10&startingAfter=<id>&status=failed */
    @Operation(operationId = "listWebhookDeliveries", summary = "Listar entregas do endpoint",
            description = "Tentativas, último erro, status HTTP e próxima tentativa. Paginação por cursor, filtro `status`.")
    @ApiResponse(responseCode = "200", description = "Página de entregas.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @GetMapping("/{id}/deliveries")
    public WebhookDeliveryListResponse deliveries(@AuthenticationPrincipal AuthenticatedMerchant principal,
                                                  @PathVariable UUID id,
                                                  @RequestParam(required = false) Integer limit,
                                                  @RequestParam(required = false) UUID startingAfter,
                                                  @RequestParam(required = false) String status) {
        WebhookDeliveryStatus deliveryStatus = status == null || status.isBlank() ? null : parseStatus(status);
        WebhookDeliveryListQuery query = new WebhookDeliveryListQuery(limit, startingAfter, deliveryStatus);
        return WebhookDeliveryListResponse.from(deliveryService.list(principal.merchantId(), id, query));
    }

    private static WebhookDeliveryStatus parseStatus(String value) {
        try {
            return WebhookDeliveryStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown delivery status: " + value);
        }
    }

    /** Linha do tempo do endpoint (também de endpoints já removidos). */
    @Operation(operationId = "getWebhookEndpointHistory", summary = "Linha do tempo do endpoint",
            description = "O que aconteceu com o endpoint, quando e com qual API Key (`null` = ação automática do sistema).")
    @ApiResponse(responseCode = "200", description = "Linha do tempo, do mais antigo ao mais recente.")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @GetMapping("/{id}/history")
    public WebhookEndpointHistoryResponse history(@AuthenticationPrincipal AuthenticatedMerchant principal,
                                                  @PathVariable UUID id) {
        return WebhookEndpointHistoryResponse.from(service.history(principal.merchantId(), id));
    }

    /** Quem está agindo: a sessão do dashboard (pessoa logada) ou a API Key (integração). */
    private static Actor actorOf(AuthenticatedMerchant principal) {
        return principal.sessionId() != null ? Actor.session(principal.sessionId()) : Actor.apiKey(principal.apiKeyId());
    }
}
