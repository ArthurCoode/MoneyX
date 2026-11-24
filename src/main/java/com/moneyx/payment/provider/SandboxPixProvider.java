package com.moneyx.payment.provider;

import com.moneyx.payment.domain.PixBrCode;
import com.moneyx.payment.domain.PixCharge;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Provedor de SANDBOX: gera localmente um BR Code ESTÁTICO com valor e txid (nenhuma chamada externa).
 *
 * O recebedor é fictício: a chave Pix abaixo é a do exemplo do Manual de Padrões para Iniciação do Pix do Banco
 * Central e não está registrada no DICT, portanto o código NÃO pode ser pago de verdade no app de um banco.
 * O "pagamento" é simulado por POST /v1/sandbox/payments/{id}/pay.
 *
 * Ativo quando moneyx.pix.provider=sandbox (padrão).
 */
@Component
@ConditionalOnProperty(name = "moneyx.pix.provider", havingValue = "sandbox", matchIfMissing = true)
public class SandboxPixProvider implements PixProvider {

    public static final String NAME = "sandbox";

    public static final String PIX_KEY = "123e4567-e12b-12d1-a456-426655440000";
    public static final String MERCHANT_NAME = "MONEYX SANDBOX";
    public static final String MERCHANT_CITY = "SAO PAULO";

    /** No BR Code estático o txid (campo 62.05) tem no máximo 25 caracteres. Usamos sempre 25. */
    public static final int TXID_LENGTH = 25;
    private static final char[] TXID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public PixCharge createCharge(PixChargeRequest request) {
        String txid = generateTxid();
        return new PixCharge(txid, PixBrCode.build(PIX_KEY, MERCHANT_NAME, MERCHANT_CITY, request.amount(), txid));
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
