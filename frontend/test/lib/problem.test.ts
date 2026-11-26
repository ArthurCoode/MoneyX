import { describe, expect, it } from "vitest";
import { problemToFormState, retryAfterFrom, safeRedirectPath } from "@/lib/forms/problem";

describe("problemToFormState", () => {
  it("shows the same message for every invalid login (does not reveal if the account exists)", () => {
    const state = problemToFormState(401, { code: "invalid_credentials", detail: "Invalid email or password" }, undefined);
    expect(state.error).toBe("E-mail ou senha incorretos.");
  });

  it("never shows the API's English detail to the merchant", () => {
    const state = problemToFormState(500, { detail: "NullPointerException at line 42" }, undefined);
    expect(state.error).not.toContain("NullPointer");
  });

  it("turns Retry-After into minutes, rounding up", () => {
    expect(problemToFormState(429, { code: "rate_limit_exceeded" }, 30).error).toContain("1 minuto.");
    expect(problemToFormState(429, { code: "rate_limit_exceeded" }, 61).error).toContain("2 minutos.");
  });

  it("maps validation errors to Portuguese messages per field", () => {
    const state = problemToFormState(
      400,
      { errors: [{ field: "document", message: "must be a valid CPF" }, { field: "unknown", message: "x" }] },
      undefined,
      { document: "123" },
    );
    expect(state.fieldErrors?.document).toContain("CPF (11 dígitos)");
    expect(state.fieldErrors?.unknown).toBe("Valor inválido.");
    expect(state.values).toEqual({ document: "123" });
  });

  it("explains the password policy when the domain rejects the password", () => {
    const state = problemToFormState(400, { code: "invalid_request", detail: "password must not be the email" }, undefined);
    expect(state.error).toContain("não pode ser o seu e-mail");
  });

  it("maps reset-token errors by their stable code", () => {
    expect(problemToFormState(400, { code: "reset_token_expired" }, undefined).error).toContain("expirou");
    expect(problemToFormState(400, { code: "invalid_reset_token" }, undefined).error).toContain("não é válido");
  });

  it("maps dashboard codes and a generic 409", () => {
    expect(problemToFormState(409, { code: "last_active_api_key" }, undefined).error).toContain("última chave ativa");
    expect(problemToFormState(409, {}, undefined).error).toContain("Recarregue");
  });
});

describe("retryAfterFrom", () => {
  it("reads the numeric header", () => {
    expect(retryAfterFrom(new Response(null, { headers: { "Retry-After": "120" } }))).toBe(120);
    expect(retryAfterFrom(new Response(null))).toBeUndefined();
    expect(retryAfterFrom(new Response(null, { headers: { "Retry-After": "soon" } }))).toBeUndefined();
  });
});

describe("safeRedirectPath (open redirect)", () => {
  it.each([
    ["/painel", "/painel"],
    ["/painel/pagamentos?status=PAID", "/painel/pagamentos?status=PAID"],
    ["https://evil.example/painel", "/painel"],
    ["//evil.example", "/painel"],
    ["/painel//evil.example", "/painel"],
    ["/painel\\..\\evil", "/painel"],
    ["/painelfalso", "/painel"],
    ["/entrar", "/painel"],
    [undefined, "/painel"],
  ])("%s → %s", (candidate, expected) => {
    expect(safeRedirectPath(candidate)).toBe(expected);
  });
});
