import type { components } from "@/lib/api/schema";

export type Problem = components["schemas"]["Problem"];

/** Estado devolvido pelas Server Actions aos formulários. Nunca carrega senhas de volta. */
export type FormState = {
  error?: string;
  fieldErrors?: Record<string, string>;
  values?: Record<string, string>;
  /** Mensagem de sucesso (ex.: "se houver conta, enviamos o link"). */
  notice?: string;
};

export const UNAVAILABLE_MESSAGE = "Não conseguimos falar com o MoneyX agora. Tente de novo em instantes.";

/**
 * Mensagens em português pelos códigos ESTÁVEIS da API (`code` do Problem Details). O `detail` da API é para
 * desenvolvedores (inglês, pode mudar) e não é mostrado ao comerciante.
 */
const MESSAGES_BY_CODE: Record<string, string> = {
  invalid_credentials: "E-mail ou senha incorretos.",
  invalid_reset_token: "Este link de redefinição não é válido. Peça um novo.",
  reset_token_expired: "Este link de redefinição expirou. Peça um novo.",
};

/** Mensagem por CAMPO nos erros de validação (400). Explica a regra em vez de repetir o texto em inglês da API. */
const FIELD_MESSAGES: Record<string, string> = {
  legalName: "Informe a razão social ou o nome (até 150 caracteres).",
  document: "Informe um CPF (11 dígitos) ou CNPJ (14 dígitos), com ou sem pontuação.",
  email: "Informe um e-mail válido.",
  password: "A senha precisa ter de 12 a 128 caracteres e não pode ser o seu e-mail.",
  newPassword: "A senha precisa ter de 12 a 128 caracteres e não pode ser o seu e-mail.",
};

/** Converte a resposta de erro da API no estado do formulário. */
export function problemToFormState(
  status: number,
  problem: Problem | undefined,
  retryAfterSeconds: number | undefined,
  values?: Record<string, string>,
): FormState {
  if (status === 429) {
    const minutes = Math.max(1, Math.ceil((retryAfterSeconds ?? 60) / 60));
    return { values, error: `Muitas tentativas. Tente de novo em ${minutes} ${minutes === 1 ? "minuto" : "minutos"}.` };
  }
  const byCode = problem?.code ? MESSAGES_BY_CODE[problem.code] : undefined;
  if (byCode) return { values, error: byCode };

  if (status === 400) {
    const fieldErrors: Record<string, string> = {};
    for (const error of problem?.errors ?? []) {
      if (error.field) fieldErrors[error.field] = FIELD_MESSAGES[error.field] ?? "Valor inválido.";
    }
    // A política de senha completa (ex.: "não pode ser o e-mail") volta como 400 sem lista de campos.
    if (Object.keys(fieldErrors).length === 0 && problem?.code === "invalid_request") {
      return { values, error: FIELD_MESSAGES.password };
    }
    return { values, fieldErrors, error: "Confira os campos destacados." };
  }
  if (status === 409) return { values, error: "Já existe uma conta com este e-mail ou documento." };
  if (status === 403) return { values, error: "Esta conta está suspensa ou encerrada. Fale com o suporte." };
  return { values, error: "Algo deu errado do nosso lado. Tente de novo em instantes." };
}

/** `Retry-After` em segundos (a API usa a forma numérica). */
export function retryAfterFrom(response: Response): number | undefined {
  const value = Number(response.headers.get("Retry-After"));
  return Number.isFinite(value) && value > 0 ? value : undefined;
}

/** Destino seguro depois do login: só caminhos do próprio painel (evita "open redirect" para outro site). */
export function safeRedirectPath(candidate: unknown): string {
  if (typeof candidate !== "string") return "/painel";
  const isPanelPath = candidate === "/painel" || candidate.startsWith("/painel/");
  return isPanelPath && !candidate.includes("//") && !candidate.includes("\\") ? candidate : "/painel";
}

export function textField(formData: FormData, name: string): string {
  const value = formData.get(name);
  return typeof value === "string" ? value : "";
}
