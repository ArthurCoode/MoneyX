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

export const DUPLICATE_ACCOUNT_MESSAGE = "Já existe uma conta com este e-mail ou documento.";

export const UNAVAILABLE_MESSAGE = "Não conseguimos falar com o MoneyX agora. Tente de novo em instantes.";

/**
 * Mensagens em português pelos códigos ESTÁVEIS da API (`code` do Problem Details). O `detail` da API é para
 * desenvolvedores (inglês, pode mudar) e não é mostrado ao comerciante.
 */
const MESSAGES_BY_CODE: Record<string, string> = {
  invalid_credentials: "E-mail ou senha incorretos.",
  invalid_reset_token: "Este link de redefinição não é válido. Peça um novo.",
  reset_token_expired: "Este link de redefinição expirou. Peça um novo.",
  // Painel (8e)
  payment_not_pending: "Este pagamento não está mais pendente (já foi pago, cancelado ou expirou).",
  payment_expired: "O prazo deste pagamento já venceu.",
  payment_provider_unavailable: "O provedor Pix não respondeu. Nada foi alterado; tente de novo em instantes.",
  payment_provider_error: "O provedor Pix recusou a operação. Tente de novo em instantes.",
  volume_limit_exceeded: "Esta cobrança passaria do limite de volume da sua conta (diário ou mensal).",
  idempotency_key_reused: "Este formulário já foi enviado com outros dados. Recarregue a página e tente de novo.",
  sandbox_payment_not_supported: "A simulação só vale para cobranças do simulador local.",
  sandbox_requires_test_key: "A simulação só funciona no modo de teste.",
  api_key_limit_reached: "Você já tem 5 chaves ativas. Revogue uma antes de criar outra.",
  last_active_api_key: "Esta é a sua última chave ativa: crie outra antes de revogá-la.",
  email_not_verified: "Confirme o seu e-mail antes de criar uma chave de produção.",
  email_already_verified: "O seu e-mail já está confirmado.",
  verification_email_rate_limited: "Acabamos de enviar um e-mail. Aguarde um minuto para pedir outro.",
  invalid_webhook_url: "Use uma URL https pública (endereços internos e localhost não são aceitos).",
  webhook_endpoint_limit_reached: "Você já tem 5 endpoints. Exclua um antes de criar outro.",
  webhook_endpoint_deleted: "Este endpoint foi excluído.",
  webhook_endpoint_unavailable: "Este endpoint está desativado. Reative-o antes de reenviar.",
  delivery_not_failed: "Só entregas que falharam podem ser reenviadas.",
  concurrent_update: "Outra alteração aconteceu ao mesmo tempo. Recarregue a página e tente de novo.",
};

/** Mensagem por CAMPO nos erros de validação (400). Explica a regra em vez de repetir o texto em inglês da API. */
const FIELD_MESSAGES: Record<string, string> = {
  legalName: "Informe a razão social ou o nome (até 150 caracteres).",
  document: "Informe um CPF (11 dígitos) ou CNPJ (14 dígitos), com ou sem pontuação.",
  email: "Informe um e-mail válido.",
  password: "A senha precisa ter de 12 a 128 caracteres e não pode ser o seu e-mail.",
  newPassword: "A senha precisa ter de 12 a 128 caracteres e não pode ser o seu e-mail.",
  amount: "O valor precisa estar entre R$ 1,00 e R$ 100.000,00.",
  description: "A descrição pode ter até 255 caracteres.",
  externalReference: "A referência pode ter até 64 caracteres.",
  expiresInSeconds: "Prazo fora do permitido.",
  url: "Informe a URL do endpoint.",
  enabledEvents: "Escolha pelo menos um tipo de evento.",
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
  if (status === 409) return { values, error: "O estado mudou enquanto você usava a página. Recarregue e tente de novo." };
  if (status === 403) return { values, error: "Esta conta está suspensa ou encerrada. Fale com o suporte." };
  if (status === 404) return { values, error: "Não encontramos este item. Ele pode ter sido removido." };
  if (status === 503) return { values, error: "Serviço temporariamente indisponível. Tente de novo em instantes." };
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
