import "server-only";
import { cookies } from "next/headers";
import { SESSION_COOKIE } from "@/lib/session-cookie";

/**
 * Cookie de sessão do dashboard. Guarda o próprio token `mx_sess_` (decisão de 2026-11-27): quem controla expiração e
 * revogação é a API; o Next só carrega o token.
 *
 * - `__Host-`: o navegador só aceita o cookie com Secure, Path=/ e sem Domain (não vaza para subdomínios).
 * - `HttpOnly`: JavaScript da página não lê o cookie (um XSS não consegue roubar a sessão).
 * - `SameSite=Lax`: o cookie não vai em POST vindo de outro site (primeira barreira contra CSRF; a segunda é a
 *   checagem de Origin no proxy.ts).
 * - `Secure`: os navegadores tratam http://localhost como seguro, então funciona também no desenvolvimento.
 */
export async function saveSession(token: string, expiresAt: string | undefined): Promise<void> {
  const store = await cookies();
  store.set(SESSION_COOKIE, token, {
    httpOnly: true,
    secure: true,
    sameSite: "lax",
    path: "/",
    // Expira junto com o limite absoluto da sessão na API (24 h); a inatividade (6 h) a API controla.
    expires: expiresAt ? new Date(expiresAt) : undefined,
  });
}

export async function readSessionToken(): Promise<string | undefined> {
  const value = (await cookies()).get(SESSION_COOKIE)?.value;
  return value?.startsWith("mx_sess_") ? value : undefined;
}

export async function clearSession(): Promise<void> {
  (await cookies()).delete({ name: SESSION_COOKIE, path: "/", secure: true, httpOnly: true, sameSite: "lax" });
}
