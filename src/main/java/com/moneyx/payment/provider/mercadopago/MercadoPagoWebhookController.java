package com.moneyx.payment.provider.mercadopago;

import com.moneyx.payment.exception.PaymentProviderUnavailableException;
import com.moneyx.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Avisos (webhooks) do Mercado Pago sobre orders (Etapa 7e): {@code POST /v1/provider-webhooks/mercadopago?data.id=ORD...&type=order}.
 *
 * O aviso é só um GATILHO: o corpo é ignorado e o estado vem de uma consulta ao próprio MP (a mesma conciliação da
 * rotina de 30 s). Assim um aviso repetido, atrasado ou forjado não muda nada sozinho; a assinatura x-signature
 * impede que terceiros usem o endpoint para provocar consultas.
 *
 * Respostas: 200 = recebido (inclusive order desconhecida ou tipo que não interessa: o MP não deve repetir);
 * 401 = assinatura inválida; 404 = aviso desligado (sem MERCADOPAGO_WEBHOOK_SECRET); 503 = o MP não respondeu à
 * consulta (o MP repete o aviso). O MP espera até 22 s pela resposta e repete a cada 15 min sem resposta 200/201.
 *
 * Fora do contrato OpenAPI (@Hidden): é uma rota para o MP, não para os comerciantes.
 */
@Hidden
@RestController
@ConditionalOnProperty(name = "moneyx.pix.provider", havingValue = "mercadopago")
class MercadoPagoWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookController.class);

    private final MercadoPagoProperties properties;
    private final PaymentService payments;

    MercadoPagoWebhookController(MercadoPagoProperties properties, PaymentService payments) {
        this.properties = properties;
        this.payments = payments;
    }

    @PostMapping("/v1/provider-webhooks/mercadopago")
    ResponseEntity<Void> receive(@RequestParam(name = "data.id", required = false) String dataId,
                                 @RequestParam(name = "type", required = false) String type,
                                 @RequestHeader(name = "x-signature", required = false) String signature,
                                 @RequestHeader(name = "x-request-id", required = false) String requestId) {
        if (properties.webhookSecret() == null || properties.webhookSecret().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        if (!MercadoPagoWebhookSignature.verify(properties.webhookSecret(), signature, requestId, dataId)) {
            log.warn("mercadopago.webhook_invalid_signature type={}", type);
            return ResponseEntity.status(401).build();
        }
        if (!"order".equals(type) || dataId == null || !dataId.matches("[A-Za-z0-9]{1,64}")) {
            return ResponseEntity.ok().build(); // não interessa ao MoneyX
        }
        try {
            boolean known = payments.reconcileProviderOrder(dataId);
            log.info("mercadopago.webhook order={} known={}", dataId, known);
            return ResponseEntity.ok().build();
        } catch (PaymentProviderUnavailableException e) {
            return ResponseEntity.status(503).build();
        }
    }
}
