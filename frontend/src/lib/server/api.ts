import "server-only";
import createClient from "openapi-fetch";
import { headers } from "next/headers";
import type { paths } from "@/lib/api/schema";
import { clientIpFrom } from "@/lib/server/client-ip";

/**
 * Cliente da API Java usado SÓ no servidor do Next (BFF). O navegador nunca fala com a API diretamente: ele fala com o
 * Next, que chama a API daqui. O `import "server-only"` faz o build FALHAR se algum componente de cliente importar este
 * arquivo por engano.
 */
const apiBaseUrl = process.env.MONEYX_API_URL ?? "http://localhost:8080";

/** Erro de rede (API fora do ar, DNS, timeout): diferente de uma resposta de erro da API. */
export class ApiUnavailableError extends Error {
  constructor(cause: unknown) {
    super("MoneyX API unavailable", { cause });
    this.name = "ApiUnavailableError";
  }
}

/**
 * Cria um cliente para UMA requisição do visitante.
 *
 * - `token`: o `mx_sess_` do cookie (rotas protegidas). Vai no mesmo `Authorization: Bearer` que a API já entende.
 * - `X-Forwarded-For`: o IP do visitante, para os limites por IP da API valerem por pessoa e não para o Next inteiro.
 *   A API só acredita nele se o IP do Next estiver em `MONEYX_TRUSTED_PROXIES` (do contrário, ignora).
 */
export async function moneyxApi(token?: string) {
  const requestHeaders = await headers();
  const clientIp = clientIpFrom(requestHeaders.get("x-forwarded-for"));

  const extraHeaders: Record<string, string> = {};
  if (token) extraHeaders.Authorization = `Bearer ${token}`;
  if (clientIp) extraHeaders["X-Forwarded-For"] = clientIp;

  return createClient<paths>({
    baseUrl: apiBaseUrl,
    headers: extraHeaders,
    // Nada da API é cacheado pelo Next: são dados por comerciante e, às vezes, segredos.
    fetch: async (request: Request) => {
      try {
        return await fetch(request, { cache: "no-store", signal: AbortSignal.timeout(10_000) });
      } catch (error) {
        throw new ApiUnavailableError(error);
      }
    },
  });
}
