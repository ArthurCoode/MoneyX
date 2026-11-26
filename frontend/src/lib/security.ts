/**
 * Regras de segurança do proxy.ts, separadas em funções puras para os testes (Vitest) exercitarem sem subir o Next.
 */

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

/**
 * CSRF, segunda barreira (a primeira é o cookie SameSite=Lax): toda escrita precisa vir de uma página do próprio site.
 * O navegador sempre manda `Origin` em POST; ausente, "null" ou de outro host = recusado. A checagem nativa das Server
 * Actions do Next só AVISA quando o Origin falta, por isso esta regra existe.
 */
export function isCrossSiteWrite(method: string, origin: string | null, host: string | null): boolean {
  if (SAFE_METHODS.has(method.toUpperCase())) return false;
  if (!origin || origin === "null" || !host) return true;
  try {
    return new URL(origin).host !== host;
  } catch {
    return true;
  }
}

/**
 * Content-Security-Policy com nonce: só roda script que o PRÓPRIO servidor marcou nesta resposta. Um script injetado
 * (XSS) não tem o nonce e o navegador bloqueia. `strict-dynamic` deixa os scripts confiáveis carregarem os pedaços
 * (chunks) do Next. Em desenvolvimento o React precisa de `eval` (mensagens de erro) e o recarregamento usa WebSocket.
 */
export function contentSecurityPolicy(nonce: string, development: boolean): string {
  return [
    "default-src 'self'",
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${development ? " 'unsafe-eval'" : ""}`,
    // Estilos inline: atributos style do React/Motion/3D. Script é o vetor perigoso; estilo inline é risco baixo.
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data: blob:",
    "font-src 'self'",
    `connect-src 'self'${development ? " ws:" : ""}`,
    "worker-src 'self' blob:",
    "object-src 'none'",
    "base-uri 'none'",
    "form-action 'self'",
    "frame-ancestors 'none'",
  ].join("; ");
}

export function newNonce(): string {
  return btoa(crypto.randomUUID());
}
