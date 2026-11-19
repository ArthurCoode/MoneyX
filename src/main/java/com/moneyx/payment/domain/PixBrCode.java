package com.moneyx.payment.domain;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Gerador de BR Code estático ("Pix copia e cola"), conforme o Manual de Padrões para Iniciação
 * do Pix do Banco Central (padrão EMV QRCPS-MPM).
 *
 * O BR Code é uma sequência de campos TLV (Tag-Length-Value):
 *   ID (2 dígitos) + TAMANHO (2 dígitos) + VALOR
 * Ex.: "5303986" = ID 53 (moeda), tamanho 03, valor "986" (BRL).
 *
 * O último campo é o CRC16 (ID 63), calculado sobre todo o texto anterior INCLUINDO "6304".
 * O app do banco recalcula o CRC e recusa o código se não bater (detecta erro de digitação/cópia).
 *
 * Classe pura (sem Spring, sem banco): validada por testes contra exemplos do manual.
 */
public final class PixBrCode {

    private static final String GUI_PIX = "br.gov.bcb.pix";
    private static final String MERCHANT_CATEGORY_CODE = "0000";
    private static final String CURRENCY_BRL = "986";
    private static final String COUNTRY_BR = "BR";

    private static final int MAX_FIELD_LENGTH = 99;
    private static final int MAX_NAME_LENGTH = 25;
    private static final int MAX_CITY_LENGTH = 15;
    private static final int MAX_PIX_KEY_LENGTH = 77;
    private static final int MAX_AMOUNT_LENGTH = 13;

    /** Nome e cidade: ASCII imprimível (sem acentos), como recomenda o manual. */
    private static final Pattern PLAIN_TEXT = Pattern.compile("^[\\x20-\\x7E]+$");
    /** txid de BR Code estático: até 25 caracteres alfanuméricos. */
    private static final Pattern STATIC_TXID = Pattern.compile("^[A-Za-z0-9]{1,25}$");

    private PixBrCode() {
    }

    /**
     * Monta o BR Code estático com valor e txid.
     *
     * @param amountCents valor em centavos (> 0)
     */
    public static String build(String pixKey, String merchantName, String merchantCity, long amountCents, String txid) {
        require(pixKey != null && !pixKey.isBlank() && pixKey.length() <= MAX_PIX_KEY_LENGTH, "invalid Pix key");
        require(merchantName != null && merchantName.length() <= MAX_NAME_LENGTH
                && PLAIN_TEXT.matcher(merchantName).matches(), "merchant name must have 1-25 plain ASCII characters");
        require(merchantCity != null && merchantCity.length() <= MAX_CITY_LENGTH
                && PLAIN_TEXT.matcher(merchantCity).matches(), "merchant city must have 1-15 plain ASCII characters");
        require(txid != null && STATIC_TXID.matcher(txid).matches(), "txid must have 1-25 alphanumeric characters");
        require(amountCents > 0, "amount must be positive");

        String amount = formatAmount(amountCents);
        require(amount.length() <= MAX_AMOUNT_LENGTH, "amount too large for BR Code");

        String merchantAccount = field("00", GUI_PIX) + field("01", pixKey);

        String payload = field("00", "01")                      // Payload Format Indicator
                + field("26", merchantAccount)                  // Merchant Account Information (Pix)
                + field("52", MERCHANT_CATEGORY_CODE)           // Merchant Category Code
                + field("53", CURRENCY_BRL)                     // Transaction Currency (ISO 4217)
                + field("54", amount)                           // Transaction Amount
                + field("58", COUNTRY_BR)                       // Country Code
                + field("59", merchantName)                     // Merchant Name
                + field("60", merchantCity)                     // Merchant City
                + field("62", field("05", txid))                // Additional Data: Reference Label (txid)
                + "6304";                                       // CRC16: ID + tamanho entram no cálculo

        return payload + crc16Hex(payload);
    }

    /**
     * Centavos -> texto decimal com ponto e 2 casas ("4990" -> "49.90").
     * Só aritmética de inteiros: nenhuma conversão para double em nenhum momento.
     */
    static String formatAmount(long amountCents) {
        return (amountCents / 100) + "." + String.format(Locale.ROOT, "%02d", amountCents % 100);
    }

    /** Campo TLV: ID + tamanho com 2 dígitos + valor. */
    static String field(String id, String value) {
        int length = value.length();
        if (length > MAX_FIELD_LENGTH) {
            throw new IllegalArgumentException("BR Code field " + id + " exceeds 99 characters");
        }
        return id + String.format(Locale.ROOT, "%02d", length) + value;
    }

    /**
     * CRC16-CCITT-FALSE (polinômio 0x1021, valor inicial 0xFFFF), exigido pelo manual do Pix.
     * Resultado em 4 dígitos hexadecimais maiúsculos.
     */
    static String crc16Hex(String data) {
        int crc = 0xFFFF;
        for (byte b : data.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return String.format(Locale.ROOT, "%04X", crc);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
