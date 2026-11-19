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
public record AuthenticatedMerchant(UUID merchantId, UUID apiKeyId) {
}
