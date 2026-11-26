import "server-only";
import { notFound, redirect } from "next/navigation";
import { moneyxApi } from "@/lib/server/api";
import { readSessionToken } from "@/lib/server/session";

export const EXPIRED_SESSION_PATH = "/entrar?sessao=expirada";

/** Cliente da API com a sessão do cookie. Sem sessão → volta para /entrar. */
export async function sessionApi() {
  const token = await readSessionToken();
  if (!token) redirect(EXPIRED_SESSION_PATH);
  return moneyxApi(token);
}

type GuardOptions = { notFoundOn404?: boolean; code?: string };

/**
 * Trata as respostas que valem para qualquer tela do painel: 401 ou 403 de segurança (sessão morta, conta suspensa)
 * → /entrar;
 * 404 → página "não encontrado" (inclusive recurso de OUTRO comerciante: a API responde 404, nunca 403, para não
 * revelar que o id existe).
 */
export function guardResponse(response: Response, { notFoundOn404 = false, code }: GuardOptions = {}): void {
  if (response.status === 401) redirect(EXPIRED_SESSION_PATH);
  // 403 SEM `code` = a segurança da API barrou a conta (suspensa/encerrada). 403 COM `code` é regra de negócio
  // (ex.: `email_not_verified`) e a tela mostra a mensagem, sem derrubar a sessão.
  if (response.status === 403 && !code) redirect(EXPIRED_SESSION_PATH);
  if (notFoundOn404 && response.status === 404) notFound();
}

/** Para leituras de página: devolve os dados ou falha alto (vai para a página de erro do painel). */
export function requireData<T>(data: T | undefined, response: Response, what: string): T {
  guardResponse(response, { notFoundOn404: true });
  // Id malformado na URL (ex.: /painel/pagamentos/abc) ou cursor inválido: a API responde 400; para quem navega é
  // "não encontrado".
  if (response.status === 400) notFound();
  if (data === undefined) throw new Error(`${what} failed with status ${response.status}`);
  return data;
}
