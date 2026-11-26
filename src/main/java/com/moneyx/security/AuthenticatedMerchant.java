package com.moneyx.security;

import java.util.UUID;

/**
 * Principal da requisição: "quem está chamando a API".
 *
 * Fica dentro do Authentication guardado no SecurityContext e é lido pelos controllers
 * com @AuthenticationPrincipal. É a ÚNICA fonte confiável do comerciante da requisição:
 * nunca usar um merchantId vindo da URL, do corpo ou de headers.
 *
 * Contém apenas identificadores. O segredo da chave nunca entra aqui.
 */
public record AuthenticatedMerchant(UUID merchantId, UUID apiKeyId, UUID sessionId, boolean live) {

    /**
     * Exatamente uma credencial: a API Key (integração) OU a sessão do dashboard (pessoa logada, Etapa 8b).
     */
    public AuthenticatedMerchant {
        if ((apiKeyId == null) == (sessionId == null)) {
            throw new IllegalArgumentException("Exactly one of apiKeyId or sessionId");
        }
    }

    /** Por API Key. */
    public AuthenticatedMerchant(UUID merchantId, UUID apiKeyId, boolean live) {
        this(merchantId, apiKeyId, null, live);
    }

    /** Por chave de teste (mx_test_). */
    public AuthenticatedMerchant(UUID merchantId, UUID apiKeyId) {
        this(merchantId, apiKeyId, null, false);
    }

    /** Por sessão do dashboard. A sessão age como chave de teste: as rotas de sandbox continuam disponíveis. */
    public static AuthenticatedMerchant ofSession(UUID merchantId, UUID sessionId) {
        return new AuthenticatedMerchant(merchantId, null, sessionId, false);
    }
}
