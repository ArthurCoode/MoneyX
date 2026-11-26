package com.moneyx.payment.exception;

/**
 * Rota de sandbox (/v1/sandbox/**) chamada com uma chave de produção (mx_live_): 403 sandbox_requires_test_key.
 *
 * Simular um pagamento é coisa de teste; com uma chave live isso diria "pago" num ambiente que deveria refletir só
 * dinheiro de verdade.
 */
public class SandboxRequiresTestKeyException extends RuntimeException {

    public SandboxRequiresTestKeyException() {
        super("Sandbox endpoints accept only test API keys (mx_test_)");
    }
}
