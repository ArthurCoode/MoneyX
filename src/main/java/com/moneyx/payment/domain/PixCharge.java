package com.moneyx.payment.domain;

import java.util.Objects;

/**
 * Dados da cobrança Pix de um pagamento: txid + BR Code ("copia e cola").
 *
 * Quem emite é um PixProvider (sandbox local ou PSP real); o domínio só guarda o resultado.
 */
public record PixCharge(String txid, String copyPaste) {

    /** Tamanho máximo do txid aceito pela coluna (V5). */
    public static final int TXID_MAX_LENGTH = 25;

    public PixCharge {
        Objects.requireNonNull(txid, "txid must not be null");
        Objects.requireNonNull(copyPaste, "copyPaste must not be null");
        if (txid.isBlank() || copyPaste.isBlank()) {
            throw new IllegalArgumentException("txid and copyPaste must not be blank");
        }
    }
}
