import { expect, type Page } from "@playwright/test";

const mailpitUrl = process.env.MAILPIT_URL ?? "http://localhost:8025";

/** Dados únicos por execução (e-mail e documento são únicos no banco). */
export function uniqueMerchant() {
  const id = `${Date.now()}${Math.floor(Math.random() * 1000)}`.slice(-14).padStart(14, "7");
  return {
    legalName: `Loja E2E ${id}`,
    document: id,
    email: `e2e-${id}@example.com`,
    password: "uma frase longa de teste",
  };
}

export async function signUp(page: Page, merchant: ReturnType<typeof uniqueMerchant>) {
  await page.goto("/cadastro");
  await page.getByLabel("Razão social ou nome").fill(merchant.legalName);
  await page.getByLabel("CPF ou CNPJ").fill(merchant.document);
  await page.getByLabel("E-mail").fill(merchant.email);
  await page.getByLabel("Senha", { exact: true }).fill(merchant.password);
  await page.getByLabel("Confirme a senha").fill(merchant.password);
  await page.getByRole("button", { name: "Criar conta" }).click();
  await expect(page.getByText("Conta criada.")).toBeVisible();
}

export async function logIn(page: Page, email: string, password: string) {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Senha").fill(password);
  await page.getByRole("button", { name: "Entrar" }).click();
}

/** Link do e-mail mais recente enviado para `to` que contenha `pathPart` (espera o e-mail chegar). */
export async function linkFromLatestEmail(to: string, pathPart: string): Promise<string> {
  const pattern = new RegExp(`https?://[^\\s"<>]*${pathPart.replace(/[?]/g, "\\?")}[^\\s"<>]*`);
  for (let attempt = 0; attempt < 30; attempt++) {
    const search = await fetch(`${mailpitUrl}/api/v1/search?query=${encodeURIComponent(`to:"${to}"`)}`);
    const { messages } = (await search.json()) as { messages: { ID: string; Subject: string }[] };
    for (const message of messages) {
      const body = (await (await fetch(`${mailpitUrl}/api/v1/message/${message.ID}`)).json()) as { Text: string };
      const match = body.Text.match(pattern);
      if (match) return match[0];
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`No email to ${to} with a link containing ${pathPart}`);
}
