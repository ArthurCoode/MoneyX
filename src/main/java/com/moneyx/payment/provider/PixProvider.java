package com.moneyx.payment.provider;

import com.moneyx.payment.domain.PixCharge;

/**
 * Quem emite a cobrança Pix de um pagamento: o simulador local (sandbox) ou um PSP real (Fase 7).
 *
 * É uma "porta" (interface) que o PaymentService usa sem saber qual implementação está por trás. A escolha é feita
 * por configuração: moneyx.pix.provider (padrão "sandbox"). Assim o resto do módulo não muda quando o PSP muda.
 *
 * Regra de uso: o serviço só chama createCharge depois de saber que o pagamento é NOVO (chave de idempotência
 * reservada e limite de volume conferido). Com um PSP real cada chamada cria uma cobrança de verdade.
 */
public interface PixProvider {

    /** Nome estável do provedor (ex.: "sandbox"), para log e para gravar junto do pagamento. */
    String name();

    /**
     * Emite a cobrança Pix.
     *
     * @return txid e BR Code ("copia e cola") que o pagador vai usar
     */
    PixCharge createCharge(PixChargeRequest request);
}
