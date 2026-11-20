package com.moneyx.webhook.api;

import com.moneyx.security.AuthenticatedMerchant;
import com.moneyx.webhook.api.dto.WebhookDeliveryResponse;
import com.moneyx.webhook.service.WebhookDeliveryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/webhook-deliveries")
public class WebhookDeliveryController {

    private final WebhookDeliveryService service;

    public WebhookDeliveryController(WebhookDeliveryService service) {
        this.service = service;
    }

    /** Reenvio manual de uma entrega FAILED: volta para a fila e o job a envia em até 5 s. */
    @PostMapping("/{id}/retry")
    public WebhookDeliveryResponse retry(@AuthenticationPrincipal AuthenticatedMerchant principal, @PathVariable UUID id) {
        return WebhookDeliveryResponse.from(service.retry(principal.merchantId(), id));
    }
}
