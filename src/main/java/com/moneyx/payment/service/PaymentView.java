package com.moneyx.payment.service;

import com.moneyx.payment.domain.Payment;

import java.time.Instant;
import java.util.UUID;

/**
 * Representação pública de um pagamento (o CONTRATO com o comerciante). O valor é sempre em centavos.
 *
 * Usada em dois lugares, por isso fica no service e não na api: nas respostas HTTP e como retrato (data)
 * dos eventos de pagamento. Uma única classe garante que os dois formatos nunca divergem.
 *
 * "pix" agrupa os dados para o pagador (como o "point_of_interaction.transaction_data" do Mercado Pago):
 *   copyPaste: BR Code ("Pix copia e cola"). O comerciante o exibe como texto e/ou gera a imagem do QR code.
 *   txid:      identificador da cobrança Pix.
 */
public record PaymentView(
        UUID id,
        String status,
        long amount,
        String currency,
        String description,
        String externalReference,
        Instant expiresAt,
        Pix pix,
        Instant paidAt,
        Instant canceledAt,
        Instant createdAt
) {

    public static PaymentView from(Payment payment) {
        return new PaymentView(
                payment.getId(),
                payment.getStatus().name(),
                payment.getAmount(),
                payment.getCurrency().name(),
                payment.getDescription(),
                payment.getExternalReference(),
                payment.getExpiresAt(),
                new Pix(payment.getPixTxid(), payment.getPixCopyPaste()),
                payment.getPaidAt(),
                payment.getCanceledAt(),
                payment.getCreatedAt()
        );
    }

    public record Pix(String txid, String copyPaste) {
    }
}
