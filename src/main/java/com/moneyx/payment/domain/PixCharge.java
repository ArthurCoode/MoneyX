package com.moneyx.payment.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Cobrança Pix emitida por um PixProvider; o domínio só guarda o resultado (Payment.attachPixCharge).
 *
 * @param provider          quem emitiu ("sandbox", "mercadopago")
 * @param txid              campo 62.05 do BR Code (até 35 caracteres alfanuméricos)
 * @param copyPaste         BR Code ("copia e cola")
 * @param providerOrderId   id da cobrança no PSP; null no sandbox local
 * @param providerPaymentId id do pagamento dentro da cobrança no PSP; null no sandbox local
 * @param expiresAt         vencimento definido pelo PSP; null = mantém o pedido
 */
public record PixCharge(String provider,
                        String txid,
                        String copyPaste,
                        String providerOrderId,
                        String providerPaymentId,
                        Instant expiresAt) {

    /** Tamanho máximo do txid (campo 62.05 do BR Code; coluna da V13). */
    public static final int TXID_MAX_LENGTH = 35;

    public PixCharge {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(txid, "txid must not be null");
        Objects.requireNonNull(copyPaste, "copyPaste must not be null");
        if (txid.isBlank() || copyPaste.isBlank()) {
            throw new IllegalArgumentException("txid and copyPaste must not be blank");
        }
        if (!txid.matches("[A-Za-z0-9]{1," + TXID_MAX_LENGTH + "}")) {
            throw new IllegalArgumentException("txid must have 1 to " + TXID_MAX_LENGTH + " alphanumeric characters");
        }
    }

    /** Cobrança emitida localmente (sem PSP): sem ids externos e com o vencimento pedido. */
    public static PixCharge local(String provider, String txid, String copyPaste) {
        return new PixCharge(provider, txid, copyPaste, null, null, null);
    }
}
