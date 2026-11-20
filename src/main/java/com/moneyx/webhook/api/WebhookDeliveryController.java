package com.moneyx.webhook.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import com.moneyx.security.AuthenticatedMerchant;
import com.moneyx.webhook.api.dto.WebhookDeliveryResponse;
import com.moneyx.webhook.service.WebhookDeliveryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Webhook Deliveries")
@RestController
@RequestMapping("/v1/webhook-deliveries")
public class WebhookDeliveryController {

    private final WebhookDeliveryService service;

    public WebhookDeliveryController(WebhookDeliveryService service) {
        this.service = service;
    }

    /** Reenvio manual de uma entrega FAILED: volta para a fila e o job a envia em até 5 s. */
    @Operation(operationId = "retryWebhookDelivery", summary = "Reenviar entrega",
            description = "Só entregas `failed`, com o endpoint ativo. Volta para a fila e é enviada em até 5 s.")
    @ApiResponse(responseCode = "200", description = "Entrega de volta na fila.")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    @PostMapping("/{id}/retry")
    public WebhookDeliveryResponse retry(@AuthenticationPrincipal AuthenticatedMerchant principal, @PathVariable UUID id) {
        return WebhookDeliveryResponse.from(service.retry(principal.merchantId(), id));
    }
}
