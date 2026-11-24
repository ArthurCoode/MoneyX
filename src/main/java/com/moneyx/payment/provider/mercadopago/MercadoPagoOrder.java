package com.moneyx.payment.provider.mercadopago;

import java.time.Instant;

/**
 * O que o MoneyX usa de uma order do Mercado Pago (API de Orders), já convertido: valores em CENTAVOS.
 *
 * Status observados no sandbox (2026-11-24):
 * - action_required / waiting_transfer: aguardando o Pix;
 * - processed / accredited: pago;
 * - canceled / canceled (pagamento: canceled_transaction): cancelada.
 *
 * @param id                 id da order (ex.: "ORD..."; no sandbox "ORDTST...")
 * @param status             status da order
 * @param statusDetail       detalhe do status da order
 * @param externalReference  o que o MoneyX mandou (o id do pagamento)
 * @param totalAmount        valor total, em centavos
 * @param paymentId          id do pagamento dentro da order (ex.: "PAY...")
 * @param paymentStatus      status do pagamento
 * @param paymentStatusDetail detalhe do status do pagamento
 * @param qrCode             BR Code ("copia e cola"); null se a order não trouxer
 * @param ticketUrl          página do MP com o QR; pode ser null
 * @param expiresAt          vencimento definido PELO MP (date_of_expiration); pode ser null
 */
public record MercadoPagoOrder(
        String id,
        String status,
        String statusDetail,
        String externalReference,
        long totalAmount,
        String paymentId,
        String paymentStatus,
        String paymentStatusDetail,
        String qrCode,
        String ticketUrl,
        Instant expiresAt
) {

    @Override
    public String toString() {
        // Sem o BR Code (texto longo, não ajuda em log).
        return "MercadoPagoOrder[id=" + id + ", status=" + status + "/" + statusDetail + ", paymentId=" + paymentId
                + ", paymentStatus=" + paymentStatus + "/" + paymentStatusDetail + ", totalAmount=" + totalAmount
                + ", expiresAt=" + expiresAt + "]";
    }
}
