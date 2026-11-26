import { NextResponse, type NextRequest } from "next/server";
import { contentSecurityPolicy, isCrossSiteWrite, newNonce } from "@/lib/security";
import { SESSION_COOKIE } from "@/lib/session-cookie";

/**
 * Proxy do Next 16 (antigo "middleware"): roda antes de TODA página e Server Action.
 *
 * 1. Recusa escrita vinda de outro site (CSRF), antes de qualquer Server Action rodar.
 * 2. Gera um nonce novo por requisição e envia a CSP com ele (o Next aplica o nonce nos próprios scripts).
 * 3. Guarda o /painel: sem cookie de sessão, manda para /entrar. É só uma checagem rápida; quem decide de verdade se a
 *    sessão vale é a API (o painel trata o 401).
 */
export function proxy(request: NextRequest) {
  if (isCrossSiteWrite(request.method, request.headers.get("origin"), request.headers.get("host"))) {
    return new NextResponse("Cross-site request blocked", { status: 403 });
  }

  const { pathname, search } = request.nextUrl;
  const hasSession = request.cookies.has(SESSION_COOKIE);

  if ((pathname === "/painel" || pathname.startsWith("/painel/")) && !hasSession) {
    const login = new URL("/entrar", request.url);
    login.searchParams.set("proximo", pathname + search);
    return NextResponse.redirect(login);
  }

  const nonce = newNonce();
  const csp = contentSecurityPolicy(nonce, process.env.NODE_ENV === "development");
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set("x-nonce", nonce);
  requestHeaders.set("Content-Security-Policy", csp);

  const response = NextResponse.next({ request: { headers: requestHeaders } });
  response.headers.set("Content-Security-Policy", csp);

  // O painel mandou para cá porque a API recusou a sessão (expirou ou foi revogada): o cookie velho sai.
  if (pathname === "/entrar" && request.nextUrl.searchParams.get("sessao") === "expirada" && hasSession) {
    // Cookie __Host- só é aceito (inclusive para apagar) com Secure e Path=/.
    response.cookies.delete({ name: SESSION_COOKIE, path: "/", secure: true, httpOnly: true, sameSite: "lax" });
  }
  return response;
}

export const config = {
  // Arquivos estáticos do build não precisam de nonce nem de checagem.
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
