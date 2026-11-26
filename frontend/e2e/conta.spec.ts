import { expect, test, type Page } from "@playwright/test";
import { linkFromLatestEmail, logIn, signUp, uniqueMerchant } from "./support";

// O Next também tem um role="alert" (o anunciador de rota, vazio): filtra o nosso, que começa por "Erro:".
const formError = (page: Page) => page.getByRole("alert").filter({ hasText: "Erro:" });

test.describe("conta do comerciante (8d)", () => {
  test("cadastro → painel → sair → entrar → esqueci a senha → nova senha", async ({ page, context }) => {
    const merchant = uniqueMerchant();

    // Cadastro: mostra a chave de teste UMA vez e já abre a sessão.
    await signUp(page, merchant);
    await expect(page.getByLabel("Chave de API de teste")).toHaveText(/^mx_test_/);
    await page.getByRole("link", { name: "Ir para o painel" }).click();
    await expect(page.getByRole("heading", { name: `Olá, ${merchant.legalName}` })).toBeVisible();

    // O cookie de sessão é HttpOnly/Secure/SameSite=Lax e o JavaScript da página não o enxerga.
    const cookie = (await context.cookies()).find((c) => c.name === "__Host-mx_session");
    expect(cookie).toMatchObject({ httpOnly: true, secure: true, sameSite: "Lax", path: "/" });
    expect(cookie?.value).toMatch(/^mx_sess_/);
    expect(await page.evaluate(() => document.cookie)).not.toContain("mx_sess_");

    // Sair: a sessão morre na API e o cookie some.
    await page.getByRole("button", { name: "Sair" }).click();
    await expect(page.getByText("Você saiu da sua conta.")).toBeVisible();
    expect((await context.cookies()).some((c) => c.name === "__Host-mx_session")).toBe(false);

    // O token antigo não vale mais, mesmo se alguém o tiver guardado.
    await context.addCookies([{ ...cookie!, expires: -1 }]);
    await page.goto("/painel");
    await expect(page).toHaveURL(/\/entrar\?sessao=expirada/);

    // Senha errada: mensagem genérica.
    await logIn(page, merchant.email, "senha errada qualquer");
    await expect(formError(page)).toContainText("E-mail ou senha incorretos.");
    await expect(page.getByLabel("E-mail")).toHaveValue(merchant.email);

    // Esqueci a senha → link no e-mail → nova senha.
    await page.getByRole("link", { name: "Esqueci a senha" }).click();
    // Navegação no cliente: espera a tela nova antes de preencher (senão o fill cai no campo da tela anterior).
    await expect(page.getByRole("heading", { name: "Esqueci a senha" })).toBeVisible();
    await page.getByLabel("E-mail").fill(merchant.email);
    await page.getByRole("button", { name: "Enviar link" }).click();
    await expect(page.getByRole("status")).toContainText("Se houver uma conta com este e-mail");

    const resetLink = await linkFromLatestEmail(merchant.email, "/redefinir-senha?token=");
    await page.goto(resetLink);
    const newPassword = "outra frase longa e nova";
    await page.getByLabel("Nova senha", { exact: true }).fill(newPassword);
    await page.getByLabel("Confirme a nova senha").fill(newPassword);
    await page.getByRole("button", { name: "Salvar nova senha" }).click();
    await expect(page.getByText("Senha redefinida.")).toBeVisible();

    // O mesmo link não serve duas vezes.
    await page.goto(resetLink);
    await page.getByLabel("Nova senha", { exact: true }).fill("mais uma frase longa aqui");
    await page.getByLabel("Confirme a nova senha").fill("mais uma frase longa aqui");
    await page.getByRole("button", { name: "Salvar nova senha" }).click();
    await expect(formError(page)).toContainText("não é válido");

    // Entra com a senha nova e volta para a página pedida antes do login.
    await page.goto("/painel");
    await expect(page).toHaveURL(/\/entrar\?proximo=%2Fpainel/);
    await page.getByLabel("E-mail").fill(merchant.email);
    await page.getByLabel("Senha").fill(newPassword);
    await page.getByRole("button", { name: "Entrar" }).click();
    await expect(page).toHaveURL(/\/painel$/);
  });

  test("cadastro com dados inválidos mostra o erro em cada campo", async ({ page }) => {
    await page.goto("/cadastro");
    await page.getByLabel("Razão social ou nome").fill("Loja");
    await page.getByLabel("CPF ou CNPJ").fill("123");
    await page.getByLabel("E-mail").fill("e2e-invalido@example.com");
    await page.getByLabel("Senha", { exact: true }).fill("curta");
    await page.getByLabel("Confirme a senha").fill("curta");
    await page.getByRole("button", { name: "Criar conta" }).click();

    await expect(formError(page)).toContainText("Confira os campos destacados.");
    await expect(page.getByLabel("CPF ou CNPJ")).toHaveAttribute("aria-invalid", "true");
    await expect(page.getByText("CPF (11 dígitos) ou CNPJ (14 dígitos)")).toBeVisible();
    await expect(page.getByText("de 12 a 128 caracteres e não pode")).toBeVisible();
    // A senha nunca volta preenchida; os outros campos, sim.
    await expect(page.getByLabel("Senha", { exact: true })).toHaveValue("");
    await expect(page.getByLabel("Razão social ou nome")).toHaveValue("Loja");
  });

  test("a página não roda script injetado: a CSP exige o nonce", async ({ page }) => {
    const violations: string[] = [];
    page.on("console", (message) => {
      if (message.type() === "error" && /Content Security Policy/i.test(message.text())) violations.push(message.text());
    });
    const response = await page.goto("/entrar");
    expect(response?.headers()["content-security-policy"]).toContain("'strict-dynamic'");

    // Os scripts do próprio Next rodaram (o formulário reage) e nenhum foi bloqueado.
    await page.getByRole("button", { name: "Entrar" }).click();
    await expect(formError(page)).toBeVisible();
    expect(violations).toEqual([]);

    // O vetor real de XSS é HTML injetado na página (ex.: um comentário renderizado sem escape). O atributo onerror é
    // script inline sem nonce: o navegador bloqueia. (Script criado por JavaScript já confiável é permitido de
    // propósito pelo 'strict-dynamic': é assim que o Next carrega os próprios pedaços.)
    const ran = await page.evaluate(async () => {
      document.body.insertAdjacentHTML("beforeend", '<img src="/nao-existe.png" onerror="window.__injected = true">');
      await new Promise((resolve) => setTimeout(resolve, 500));
      return (window as unknown as { __injected?: boolean }).__injected === true;
    });
    expect(ran).toBe(false);
  });
});
