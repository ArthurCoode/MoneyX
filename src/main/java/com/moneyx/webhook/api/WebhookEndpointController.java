package com.moneyx.webhook.api;

import com.moneyx.security.AuthenticatedMerchant;
import com.moneyx.webhook.api.dto.CreateWebhookEndpointRequest;
import com.moneyx.webhook.api.dto.CreatedWebhookEndpointResponse;
import com.moneyx.webhook.api.dto.WebhookEndpointHistoryResponse;
import com.moneyx.webhook.api.dto.WebhookEndpointListResponse;
import com.moneyx.webhook.api.dto.WebhookEndpointResponse;
import com.moneyx.webhook.service.CreatedWebhookEndpoint;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/v1/webhook-endpoints")
public class WebhookEndpointController {

    private final WebhookEndpointService service;

    public WebhookEndpointController(WebhookEndpointService service) {
        this.service = service;
    }

    /** 201 com o segredo de assinatura (única vez), Cache-Control: no-store. */
    @PostMapping
    public ResponseEntity<CreatedWebhookEndpointResponse> create(
            @AuthenticationPrincipal AuthenticatedMerchant principal,
            @Valid @RequestBody CreateWebhookEndpointRequest request) {

        CreatedWebhookEndpoint created = service.create(
                principal.merchantId(), principal.apiKeyId(), request.url(), request.enabledEvents());

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.endpoint().getId()).toUri();

        return ResponseEntity.created(location)
                .cacheControl(CacheControl.noStore())
                .body(CreatedWebhookEndpointResponse.from(created));
    }

    /** Endpoints ATIVOS. */
    @GetMapping
    public WebhookEndpointListResponse list(@AuthenticationPrincipal AuthenticatedMerchant principal) {
        return new WebhookEndpointListResponse(
                service.listActive(principal.merchantId()).stream().map(WebhookEndpointResponse::from).toList());
    }

    @GetMapping("/{id}")
    public WebhookEndpointResponse get(@AuthenticationPrincipal AuthenticatedMerchant principal, @PathVariable UUID id) {
        return WebhookEndpointResponse.from(service.get(principal.merchantId(), id));
    }

    /** Remoção lógica e idempotente: 204 também na repetição. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedMerchant principal, @PathVariable UUID id) {
        service.delete(principal.merchantId(), principal.apiKeyId(), id);
        return ResponseEntity.noContent().build();
    }

    /** Linha do tempo do endpoint (também de endpoints já removidos). */
    @GetMapping("/{id}/history")
    public WebhookEndpointHistoryResponse history(@AuthenticationPrincipal AuthenticatedMerchant principal,
                                                  @PathVariable UUID id) {
        return WebhookEndpointHistoryResponse.from(service.history(principal.merchantId(), id));
    }
}
