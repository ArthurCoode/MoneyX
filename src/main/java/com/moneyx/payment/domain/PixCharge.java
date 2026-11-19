package com.moneyx.payment.domain;

import java.security.SecureRandom;

/**
 * Dados da cobrança Pix de um pagamento: txid + BR Code ("copia e cola").
 *
 * SANDBOX: o recebedor é fictício. A chave Pix abaixo é a do exemplo do Manual de Padrões para
 * Iniciação do Pix do Banco Central e não está registrada no DICT, portanto o código NÃO pode ser
 * pago de verdade no app de um banco. Na Fase 7 (provedor real), o BR Code virá do PSP autorizado.
 */
public record PixCharge(String txid, String copyPaste) {

    public static final String SANDBOX_PIX_KEY = "123e4567-e12b-12d1-a456-426655440000";
    public static final String SANDBOX_MERCHANT_NAME = "MONEYX SANDBOX";
    public static final String SANDBOX_MERCHANT_CITY = "SAO PAULO";

    public static final int TXID_LENGTH = 25;
    private static final char[] TXID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Gera a cobrança de sandbox para o valor informado (centavos). */
    public static PixCharge sandbox(long amountCents) {
        String txid = generateTxid();
        String copyPaste = PixBrCode.build(
                SANDBOX_PIX_KEY, SANDBOX_MERCHANT_NAME, SANDBOX_MERCHANT_CITY, amountCents, txid);
        return new PixCharge(txid, copyPaste);
    }

    /**
     * 25 caracteres A-Z/0-9 (36^25, cerca de 2^129 combinações). Aleatório e imprevisível (SecureRandom);
     * a unicidade é garantida pela constraint UNIQUE no banco.
     */
    static String generateTxid() {
        char[] txid = new char[TXID_LENGTH];
        for (int i = 0; i < TXID_LENGTH; i++) {
            txid[i] = TXID_ALPHABET[RANDOM.nextInt(TXID_ALPHABET.length)];
        }
        return new String(txid);
    }
}
