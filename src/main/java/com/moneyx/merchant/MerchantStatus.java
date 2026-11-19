package com.moneyx.merchant;

/**
 * Ciclo de vida de um comerciante dentro do MoneyX.
 *
 * ACTIVE    -> pode operar normalmente (criar pagamentos, usar API Keys).
 * SUSPENDED -> bloqueado temporariamente (ex.: suspeita de fraude); pode voltar a ACTIVE.
 * CLOSED    -> encerrado definitivamente; estado final, sem retorno.
 */
public enum MerchantStatus {
    ACTIVE,
    SUSPENDED,
    CLOSED
}
