package com.moneyx.payment.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validação do BR Code contra referências EXTERNAS, não contra o próprio código:
 * - valor de checagem oficial do CRC16/CCITT-FALSE;
 * - exemplo de BR Code estático do Manual de Padrões para Iniciação do Pix (Banco Central);
 * - vetores gerados por uma implementação independente (Python), conferida contra o mesmo exemplo.
 */
class PixBrCodeTest {

    private static final String KEY = "123e4567-e12b-12d1-a456-426655440000";
    private static final String TXID = "ABCDEFGHIJKLMNOPQRSTUVWXY";

    // ---------- CRC16 ----------

    @Test
    void crc16MatchesTheStandardCheckValue() {
        // Valor de referência do algoritmo CRC-16/CCITT-FALSE para a entrada "123456789".
        assertThat(PixBrCode.crc16Hex("123456789")).isEqualTo("29B1");
    }

    @Test
    void reproducesTheStaticBrCodeExampleFromTheCentralBankManual() {
        String expected = "00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-426655440000"
                + "5204000053039865802BR5913Fulano de Tal6008BRASILIA62070503***63041D3D";

        String payload = PixBrCode.field("00", "01")
                + PixBrCode.field("26", PixBrCode.field("00", "br.gov.bcb.pix") + PixBrCode.field("01", KEY))
                + PixBrCode.field("52", "0000")
                + PixBrCode.field("53", "986")
                + PixBrCode.field("58", "BR")
                + PixBrCode.field("59", "Fulano de Tal")
                + PixBrCode.field("60", "BRASILIA")
                + PixBrCode.field("62", PixBrCode.field("05", "***"))
                + "6304";

        assertThat(payload + PixBrCode.crc16Hex(payload)).isEqualTo(expected);
    }

    // ---------- geração completa ----------

    @ParameterizedTest
    @CsvSource({
            "4990,     00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-426655440000520400005303986540549.905802BR5914MONEYX SANDBOX6009SAO PAULO62290525ABCDEFGHIJKLMNOPQRSTUVWXY630409B1",
            "100,      00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-42665544000052040000530398654041.005802BR5914MONEYX SANDBOX6009SAO PAULO62290525ABCDEFGHIJKLMNOPQRSTUVWXY6304F526",
            "10000000, 00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-4266554400005204000053039865409100000.005802BR5914MONEYX SANDBOX6009SAO PAULO62290525ABCDEFGHIJKLMNOPQRSTUVWXY6304E463"
    })
    void matchesVectorsFromAnIndependentImplementation(long amountCents, String expected) {
        assertThat(PixBrCode.build(KEY, "MONEYX SANDBOX", "SAO PAULO", amountCents, TXID)).isEqualTo(expected);
    }

    @Test
    void generatedCodeHasTheExpectedTlvFields() {
        String code = PixBrCode.build(KEY, "MONEYX SANDBOX", "SAO PAULO", 4990, TXID);

        Map<String, String> fields = parseTlv(code);

        assertThat(fields).containsEntry("00", "01")
                .containsEntry("52", "0000")
                .containsEntry("53", "986")
                .containsEntry("54", "49.90")
                .containsEntry("58", "BR")
                .containsEntry("59", "MONEYX SANDBOX")
                .containsEntry("60", "SAO PAULO");
        assertThat(parseTlv(fields.get("26")))
                .containsEntry("00", "br.gov.bcb.pix")
                .containsEntry("01", KEY);
        assertThat(parseTlv(fields.get("62"))).containsEntry("05", TXID);

        // O CRC (últimos 4 caracteres) confere com o recalculado sobre o resto.
        String withoutCrc = code.substring(0, code.length() - 4);
        assertThat(withoutCrc).endsWith("6304");
        assertThat(code.substring(code.length() - 4)).isEqualTo(PixBrCode.crc16Hex(withoutCrc));
    }

    @Test
    void anyChangeInTheCodeBreaksTheCrc() {
        String code = PixBrCode.build(KEY, "MONEYX SANDBOX", "SAO PAULO", 4990, TXID);
        // Simula alguém alterando o valor de 49.90 para 99.90 no texto copiado.
        String tampered = code.replace("540549.90", "540599.90");

        String crcInCode = tampered.substring(tampered.length() - 4);
        String recomputed = PixBrCode.crc16Hex(tampered.substring(0, tampered.length() - 4));

        assertThat(recomputed).isNotEqualTo(crcInCode);
    }

    // ---------- valor ----------

    @ParameterizedTest
    @CsvSource({"1, 0.01", "5, 0.05", "10, 0.10", "100, 1.00", "4990, 49.90", "123456, 1234.56", "10000000, 100000.00"})
    void formatsCentsWithTwoDecimalPlaces(long cents, String expected) {
        assertThat(PixBrCode.formatAmount(cents)).isEqualTo(expected);
    }

    @Test
    void outputIsAsciiRegardlessOfTheServerLocale() {
        Locale original = Locale.getDefault();
        try {
            // Em árabe, String.format sem Locale explícito produziria dígitos "٠١٢٣".
            Locale.setDefault(Locale.forLanguageTag("ar-SA"));
            String code = PixBrCode.build(KEY, "MONEYX SANDBOX", "SAO PAULO", 4990, TXID);
            assertThat(code).matches("^[\\x20-\\x7E]+$").contains("540549.90");
        } finally {
            Locale.setDefault(original);
        }
    }

    // ---------- validações ----------

    @Test
    void rejectsInvalidInputs() {
        assertThatThrownBy(() -> PixBrCode.build(KEY, "N".repeat(26), "SAO PAULO", 4990, TXID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PixBrCode.build(KEY, "MONEYX", "SÃO PAULO", 4990, TXID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PixBrCode.build(KEY, "MONEYX", "C".repeat(16), 4990, TXID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PixBrCode.build(KEY, "MONEYX", "SAO PAULO", 4990, "A".repeat(26)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PixBrCode.build(KEY, "MONEYX", "SAO PAULO", 4990, "TX-ID"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PixBrCode.build(KEY, "MONEYX", "SAO PAULO", 0, TXID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PixBrCode.build(" ", "MONEYX", "SAO PAULO", 4990, TXID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Leitor TLV mínimo e independente do gerador. */
    private static Map<String, String> parseTlv(String data) {
        Map<String, String> fields = new LinkedHashMap<>();
        int i = 0;
        while (i < data.length()) {
            String id = data.substring(i, i + 2);
            int length = Integer.parseInt(data.substring(i + 2, i + 4));
            fields.put(id, data.substring(i + 4, i + 4 + length));
            i += 4 + length;
        }
        return fields;
    }
}
