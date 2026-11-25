package com.moneyx.payment.provider;

import java.util.Objects;

/**
 * Estado de uma cobrança no PSP, já traduzido para o que o MoneyX decide (sem os nomes de status do PSP).
 *
 * @param providerOrderId   id da cobrança (order) no PSP
 * @param providerPaymentId id do pagamento dentro da cobrança; pode ser null
 * @param state             o que o estado do PSP significa para o MoneyX
 * @param amount            valor da cobrança no PSP, em centavos (conferido antes de marcar PAID)
 * @param reference         a referência externa gravada no PSP (o id do pagamento no MoneyX)
 * @param providerStatus    status original no PSP, só para log
 */
public record ProviderChargeStatus(String providerOrderId,
                                   String providerPaymentId,
                                   State state,
                                   long amount,
                                   String reference,
                                   String providerStatus) {

    public enum State {
        /** Aguardando o Pix. */
        OPEN,
        /** O PSP está processando um pagamento: ainda não dá para dizer se foi pago (não decidir nada agora). */
        IN_PROGRESS,
        /** Pago (dinheiro recebido pelo PSP). */
        PAID,
        /** Encerrada sem pagamento (cancelada ou recusada). Nenhum Pix será aceito. */
        CLOSED,
        /** Encerrada sem pagamento porque o PSP a EXPIROU (pode acontecer antes do prazo do MoneyX). */
        EXPIRED,
        /** Status que o MoneyX não conhece (ex.: estorno): nenhuma decisão automática. */
        UNKNOWN
    }

    public ProviderChargeStatus {
        Objects.requireNonNull(providerOrderId, "providerOrderId must not be null");
        Objects.requireNonNull(state, "state must not be null");
    }
}
