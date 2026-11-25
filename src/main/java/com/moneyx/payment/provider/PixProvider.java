package com.moneyx.payment.provider;

import com.moneyx.payment.domain.PixCharge;

import java.util.UUID;

/**
 * Quem emite a cobrança Pix de um pagamento: o simulador local (sandbox) ou um PSP real (Fase 7).
 *
 * É uma "porta" (interface) que o PaymentService usa sem saber qual implementação está por trás. A escolha é feita
 * por configuração: moneyx.pix.provider (padrão "sandbox"). Assim o resto do módulo não muda quando o PSP muda.
 *
 * Regra de uso: o serviço só chama createCharge depois de saber que o pagamento é NOVO (chave de idempotência
 * reservada e limite de volume conferido). Com um PSP real cada chamada cria uma cobrança de verdade.
 *
 * Conciliação (Etapa 7d): para um PSP externo, quem diz se o Pix foi pago é ELE. fetchCharge e cancelCharge só são
 * chamados para pagamentos com cobrança no PSP (providerOrderId preenchido); o sandbox local não os implementa.
 */
public interface PixProvider {

    /** Nome estável do provedor (ex.: "sandbox"), para log e para gravar junto do pagamento. */
    String name();

    /**
     * Monta, SEM chamar a rede, o pedido exato que será enviado ao PSP. O MoneyX o grava junto do pagamento e o reenvia
     * igual em toda repetição (condição para o PSP devolver a mesma cobrança). Null quando não há PSP externo.
     */
    default String prepareCharge(PixChargeRequest request) {
        return null;
    }

    /**
     * Emite a cobrança Pix. Repetir com o MESMO pedido devolve a MESMA cobrança.
     *
     * @return txid e BR Code ("copia e cola") que o pagador vai usar
     * @throws PixProviderException falha; se a cobrança existir mas não puder ser entregue (ex.: já sendo paga), a
     *                              exceção traz o estado dela (PixProviderException.getExisting)
     */
    PixCharge createCharge(PixChargeRequest request);

    /** Estado atual da cobrança no PSP. */
    default ProviderChargeStatus fetchCharge(String providerOrderId) {
        throw new UnsupportedOperationException(name() + " has no remote charges");
    }

    /**
     * Pede o cancelamento da cobrança e devolve o estado FINAL dela: CLOSED se foi (ou já estava) cancelada/expirada,
     * PAID se o pagador pagou antes (o PSP recusa cancelar). Repetível.
     */
    default ProviderChargeStatus cancelCharge(UUID paymentId, String providerOrderId) {
        throw new UnsupportedOperationException(name() + " has no remote charges");
    }
}
