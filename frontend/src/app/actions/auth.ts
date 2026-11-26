"use server";

import { redirect } from "next/navigation";
import { ApiUnavailableError, moneyxApi } from "@/lib/server/api";
import { clearSession, readSessionToken, saveSession } from "@/lib/server/session";
import {
  DUPLICATE_ACCOUNT_MESSAGE,
  type FormState,
  problemToFormState,
  retryAfterFrom,
  safeRedirectPath,
  textField,
  UNAVAILABLE_MESSAGE,
} from "@/lib/forms/problem";

/**
 * Server Actions das telas de conta. Rodam SÓ no servidor do Next: recebem o formulário, chamam a API Java e, no login,
 * gravam o token no cookie HttpOnly. O navegador nunca vê o token da sessão.
 *
 * CSRF: o cookie é SameSite=Lax e o proxy.ts recusa qualquer POST sem Origin do próprio site (o Next, sozinho, só
 * avisa quando o Origin está ausente).
 */

export type SignupState = FormState & { createdKey?: string; signedIn?: boolean };

async function callApi<T>(call: () => Promise<T>): Promise<T | ApiUnavailableError> {
  try {
    return await call();
  } catch (error) {
    if (error instanceof ApiUnavailableError) return error;
    throw error;
  }
}

export async function login(_previous: FormState, formData: FormData): Promise<FormState> {
  const email = textField(formData, "email").trim();
  const password = textField(formData, "password");
  const values = { email };

  const api = await moneyxApi();
  const result = await callApi(() => api.POST("/v1/sessions", { body: { email, password } }));
  if (result instanceof ApiUnavailableError) return { values, error: UNAVAILABLE_MESSAGE };

  const { data, error, response } = result;
  if (!data?.token) return problemToFormState(response.status, error, retryAfterFrom(response), values);

  await saveSession(data.token, data.expiresAt);
  // redirect() lança uma exceção de controle do Next: fica FORA de qualquer try/catch.
  redirect(safeRedirectPath(formData.get("next")));
}

export async function signup(_previous: SignupState, formData: FormData): Promise<SignupState> {
  const legalName = textField(formData, "legalName").trim();
  const document = textField(formData, "document").trim();
  const email = textField(formData, "email").trim();
  const password = textField(formData, "password");
  const values = { legalName, document, email };

  if (password !== textField(formData, "passwordConfirmation")) {
    return { values, fieldErrors: { passwordConfirmation: "As senhas não são iguais." } };
  }

  const api = await moneyxApi();
  const created = await callApi(() => api.POST("/v1/merchants", { body: { legalName, document, email, password } }));
  if (created instanceof ApiUnavailableError) return { values, error: UNAVAILABLE_MESSAGE };
  if (!created.data) {
    // 409 do cadastro = e-mail ou documento já usados (a API não manda `code` neste caso).
    if (created.response.status === 409) return { values, error: DUPLICATE_ACCOUNT_MESSAGE };
    return problemToFormState(created.response.status, created.error, retryAfterFrom(created.response), values);
  }

  // A conta existe: já abre a sessão com a senha que acabou de ser definida. Se falhar (ex.: limite de tentativas),
  // a conta continua criada e a tela manda para /entrar.
  const session = await callApi(() => api.POST("/v1/sessions", { body: { email, password } }));
  const sessionData = session instanceof ApiUnavailableError ? undefined : session.data;
  if (sessionData?.token) await saveSession(sessionData.token, sessionData.expiresAt);

  // A chave de teste criada no cadastro aparece UMA vez (padrão de mercado: Stripe, Mercado Pago). Ela não é guardada
  // no navegador nem usada como credencial do painel.
  return { createdKey: created.data.apiKey?.key, signedIn: Boolean(sessionData?.token) };
}

export async function requestPasswordReset(_previous: FormState, formData: FormData): Promise<FormState> {
  const email = textField(formData, "email").trim();
  const values = { email };

  const api = await moneyxApi();
  const result = await callApi(() => api.POST("/v1/merchants/password-reset", { body: { email } }));
  if (result instanceof ApiUnavailableError) return { values, error: UNAVAILABLE_MESSAGE };
  if (!result.response.ok) {
    return problemToFormState(result.response.status, result.error, retryAfterFrom(result.response), values);
  }
  // A API responde 202 SEMPRE (não revela se a conta existe); a tela diz o mesmo.
  return { notice: "Se houver uma conta com este e-mail, enviamos um link para redefinir a senha. Ele vale por 1 hora." };
}

export async function confirmPasswordReset(_previous: FormState, formData: FormData): Promise<FormState> {
  const token = textField(formData, "token");
  const newPassword = textField(formData, "newPassword");
  if (newPassword !== textField(formData, "passwordConfirmation")) {
    return { fieldErrors: { passwordConfirmation: "As senhas não são iguais." } };
  }

  const api = await moneyxApi();
  const result = await callApi(() =>
    api.POST("/v1/merchants/password-reset/confirm", { body: { token, newPassword } }),
  );
  if (result instanceof ApiUnavailableError) return { error: UNAVAILABLE_MESSAGE };
  if (!result.response.ok) {
    return problemToFormState(result.response.status, result.error, retryAfterFrom(result.response));
  }
  // A API derrubou TODAS as sessões da conta; a deste navegador também deixa de valer.
  await clearSession();
  redirect("/entrar?senha=redefinida");
}

export async function logout(): Promise<void> {
  const token = await readSessionToken();
  if (token) {
    const api = await moneyxApi(token);
    // Melhor esforço: mesmo com a API fora do ar, o cookie sai deste navegador.
    await callApi(() => api.DELETE("/v1/sessions/current"));
  }
  await clearSession();
  redirect("/entrar?saiu=1");
}
