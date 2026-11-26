"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { ApiUnavailableError } from "@/lib/server/api";
import { guardResponse, sessionApi } from "@/lib/server/session-api";
import { parseReaisToCents } from "@/lib/format";
import { type FormState, type Problem, problemToFormState, retryAfterFrom, textField, UNAVAILABLE_MESSAGE } from "@/lib/forms/problem";
import { WEBHOOK_EVENT_TYPES } from "@/lib/webhook-events";

/**
 * Server Actions do painel (8e). Todas usam a sessão do cookie; a API decide se o comerciante pode (e sempre filtra
 * pelo dono: o id de outro comerciante dá 404). Depois de mudar algo, `revalidatePath` faz a página buscar de novo.
 */

type ApiResult = { response: Response; error?: unknown };

/** Converte o resultado da API em estado do formulário. 401/403 → /entrar. Sucesso → undefined. */
function failureOf(result: ApiResult | ApiUnavailableError, values?: Record<string, string>): FormState | undefined {
  if (result instanceof ApiUnavailableError) return { values, error: UNAVAILABLE_MESSAGE };
  const problem = result.error as Problem | undefined;
  guardResponse(result.response, { code: problem?.code });
  if (result.response.ok) return undefined;
  return problemToFormState(
    result.response.status,
    problem,
    retryAfterFrom(result.response),
    values,
  );
}

async function call<T>(request: () => Promise<T>): Promise<T | ApiUnavailableError> {
  try {
    return await request();
  } catch (error) {
    if (error instanceof ApiUnavailableError) return error;
    throw error;
  }
}

// ---------------------------------------------------------------- pagamentos

const EXPIRATION_CHOICES = new Set(["900", "3600", "86400"]);

export async function createPayment(_previous: FormState, formData: FormData): Promise<FormState> {
  const amountText = textField(formData, "amount");
  const description = textField(formData, "description").trim();
  const externalReference = textField(formData, "externalReference").trim();
  const expiresIn = textField(formData, "expiresInSeconds");
  const idempotencyKey = textField(formData, "idempotencyKey");
  const values = { amount: amountText, description, externalReference, expiresInSeconds: expiresIn };

  const amount = parseReaisToCents(amountText);
  if (amount === null || amount <= 0) {
    return { values, fieldErrors: { amount: "Informe um valor em reais maior que zero, ex.: 25,90." } };
  }

  const api = await sessionApi();
  const result = await call(() =>
    api.POST("/v1/payments", {
      // A chave é gerada quando a página é montada e é a MESMA em todo reenvio deste formulário:
      // - clicar duas vezes / repetir após falha de rede → a API devolve o MESMO pagamento (não cria dois);
      // - erro de validação ou limite → a API desfaz a reserva da chave, então reenviar corrigido com ela funciona;
      // - provedor Pix fora (503) → o pagamento já existe em CREATING e repetir a MESMA chave retoma a criação.
      //   Uma chave nova aqui criaria uma SEGUNDA cobrança. Se o comerciante mudar os dados depois disso, a API
      //   responde `idempotency_key_reused` e a tela pede para recarregar (nova chave, nova cobrança, consciente).
      params: { header: { "Idempotency-Key": idempotencyKey } },
      body: {
        amount,
        currency: "BRL",
        description: description || undefined,
        externalReference: externalReference || undefined,
        expiresInSeconds: EXPIRATION_CHOICES.has(expiresIn) ? Number(expiresIn) : undefined,
      },
    }),
  );
  const failure = failureOf(result, values);
  if (failure) return failure;

  const id = (result as { data?: { id?: string } }).data?.id;
  revalidatePath("/painel", "layout");
  redirect(id ? `/painel/pagamentos/${id}` : "/painel/pagamentos");
}

export async function cancelPayment(paymentId: string, _previous: FormState): Promise<FormState> {
  const api = await sessionApi();
  const failure = failureOf(
    await call(() => api.POST("/v1/payments/{id}/cancel", { params: { path: { id: paymentId } } })),
  );
  revalidatePath(`/painel/pagamentos/${paymentId}`);
  return failure ?? { notice: "Pagamento cancelado." };
}

export async function simulatePayment(paymentId: string, _previous: FormState): Promise<FormState> {
  const api = await sessionApi();
  const failure = failureOf(
    await call(() => api.POST("/v1/sandbox/payments/{id}/pay", { params: { path: { id: paymentId } } })),
  );
  revalidatePath(`/painel/pagamentos/${paymentId}`);
  return failure ?? { notice: "Pagamento simulado: o Pix foi pago." };
}

// ---------------------------------------------------------------- API Keys

export type CreatedSecretState = FormState & { secret?: string };

export async function createApiKey(_previous: CreatedSecretState, formData: FormData): Promise<CreatedSecretState> {
  const mode = textField(formData, "mode") === "live" ? "live" : "test";
  const api = await sessionApi();
  const result = await call(() => api.POST("/v1/api-keys", { body: { mode } }));
  const failure = failureOf(result);
  if (failure) return failure;
  revalidatePath("/painel/api-keys");
  return { secret: (result as { data?: { key?: string } }).data?.key };
}

export async function revokeApiKey(apiKeyId: string, _previous: FormState): Promise<FormState> {
  const api = await sessionApi();
  const failure = failureOf(await call(() => api.DELETE("/v1/api-keys/{id}", { params: { path: { id: apiKeyId } } })));
  revalidatePath("/painel/api-keys");
  return failure ?? { notice: "Chave revogada." };
}

// ---------------------------------------------------------------- webhooks

export async function createWebhookEndpoint(
  _previous: CreatedSecretState,
  formData: FormData,
): Promise<CreatedSecretState> {
  const url = textField(formData, "url").trim();
  const chosen = formData.getAll("enabledEvents").filter((value): value is string => typeof value === "string");
  const enabledEvents = chosen.filter((type) => (WEBHOOK_EVENT_TYPES as readonly string[]).includes(type));
  const values = { url };
  if (enabledEvents.length === 0) {
    return { values, fieldErrors: { enabledEvents: "Escolha pelo menos um tipo de evento." } };
  }

  const api = await sessionApi();
  const result = await call(() => api.POST("/v1/webhook-endpoints", { body: { url, enabledEvents } }));
  const failure = failureOf(result, values);
  if (failure) return failure;
  revalidatePath("/painel/webhooks");
  return { secret: (result as { data?: { secret?: string } }).data?.secret };
}

export async function deleteWebhookEndpoint(endpointId: string, _previous: FormState): Promise<FormState> {
  const api = await sessionApi();
  const failure = failureOf(
    await call(() => api.DELETE("/v1/webhook-endpoints/{id}", { params: { path: { id: endpointId } } })),
  );
  if (failure) return failure;
  revalidatePath("/painel/webhooks");
  redirect("/painel/webhooks");
}

export async function enableWebhookEndpoint(endpointId: string, _previous: FormState): Promise<FormState> {
  const api = await sessionApi();
  const failure = failureOf(
    await call(() => api.POST("/v1/webhook-endpoints/{id}/enable", { params: { path: { id: endpointId } } })),
  );
  revalidatePath(`/painel/webhooks/${endpointId}`);
  return failure ?? { notice: "Endpoint reativado." };
}

export async function retryWebhookDelivery(
  endpointId: string,
  deliveryId: string,
  _previous: FormState,
): Promise<FormState> {
  const api = await sessionApi();
  const failure = failureOf(
    await call(() => api.POST("/v1/webhook-deliveries/{id}/retry", { params: { path: { id: deliveryId } } })),
  );
  revalidatePath(`/painel/webhooks/${endpointId}`);
  return failure ?? { notice: "Reenvio agendado." };
}

// ---------------------------------------------------------------- conta

export async function resendEmailVerification(_previous: FormState): Promise<FormState> {
  const api = await sessionApi();
  const failure = failureOf(await call(() => api.POST("/v1/merchants/me/email-verification")));
  return failure ?? { notice: "Enviamos um novo link de confirmação para o seu e-mail." };
}
