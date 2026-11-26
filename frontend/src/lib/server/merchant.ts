import "server-only";
import { cache } from "react";
import { requireData, sessionApi } from "@/lib/server/session-api";

/**
 * Comerciante da sessão atual, para as páginas do /painel. Quem decide se a sessão vale é a API:
 * sem cookie, ou 401/403 da API (expirou, foi revogada, conta suspensa) → volta para /entrar e o proxy apaga o cookie.
 * `cache` do React: o layout e a página da mesma requisição fazem UMA chamada só.
 */
export const requireMerchant = cache(async () => {
  const api = await sessionApi();
  const { data, response } = await api.GET("/v1/merchants/me");
  return { merchant: requireData(data, response, "GET /v1/merchants/me") };
});
