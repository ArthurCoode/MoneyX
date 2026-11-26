import "server-only";
import { cache } from "react";
import { redirect } from "next/navigation";
import { moneyxApi } from "@/lib/server/api";
import { readSessionToken } from "@/lib/server/session";

/**
 * Comerciante da sessão atual, para as páginas do /painel. Quem decide se a sessão vale é a API:
 * sem cookie, ou 401/403 da API (expirou, foi revogada, conta suspensa) → volta para /entrar e o proxy apaga o cookie.
 * `cache` do React: o layout e a página da mesma requisição fazem UMA chamada só.
 */
export const requireMerchant = cache(async () => {
  const token = await readSessionToken();
  if (!token) redirect("/entrar?sessao=expirada");

  const api = await moneyxApi(token);
  const { data, response } = await api.GET("/v1/merchants/me");
  if (response.status === 401 || response.status === 403) redirect("/entrar?sessao=expirada");
  if (!data) throw new Error(`GET /v1/merchants/me failed with status ${response.status}`);

  return { merchant: data, token };
});
