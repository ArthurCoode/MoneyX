package com.moneyx.apikey.api.dto;

import java.util.List;

/**
 * Envelope de lista: {"data": [...]}.
 *
 * Padrão de gateways de pagamento (Stripe usa "data", Mercado Pago "results"). Diferente de
 * um array solto, o objeto permite acrescentar campos no futuro (ex.: paginação "has_more")
 * sem quebrar quem já integrou.
 */
public record ApiKeyListResponse(List<ApiKeyResponse> data) {
}
