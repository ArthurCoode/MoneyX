import { expect, test, type BrowserContext, type Page } from "@playwright/test";
import { signUp, uniqueMerchant } from "./support";

const formError = (page: Page) => page.getByRole("alert").filter({ hasText: "Erro:" });

async function newSignedInMerchant(page: Page) {
  const merchant = uniqueMerchant();
  await signUp(page, merchant);
  await page.getByRole("link", { name: "Ir para o painel" }).click();
  await expect(page.getByRole("heading", { name: `Olá, ${merchant.legalName}` })).toBeVisible();
  return merchant;
}

async function createPayment(page: Page, amount: string, description: string) {
  await page.goto("/painel/pagamentos/nova");
  await page.getByLabel("Valor (R$)").fill(amount);
  await page.getByLabel("Descrição (opcional)").fill(description);
  await page.getByRole("button", { name: "Criar cobrança" }).click();
  await expect(page).toHaveURL(/\/painel\/pagamentos\/[0-9a-f-]{36}$/);
  return page.url().split("/").at(-1)!;
}

test.describe("painel do comerciante (8e)", () => {
  // Os 3 primeiros testes usam UM comerciante (em ordem): o cadastro público tem limite de 5 por IP.
  test.describe.configure({ mode: "serial" });

  let context: BrowserContext;
  let shared: Page;

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext();
    shared = await context.newPage();
    await newSignedInMerchant(shared);
  });

  test.afterAll(async () => {
    await context.close();
  });

  test("cobrança Pix: criar, simular o pagamento, cancelar outra, filtrar e ver os eventos", async () => {
    const page = shared;

    // Valor inválido: a tela explica sem chamar a API; abaixo do mínimo: a API recusa e a tela explica a regra.
    await page.goto("/painel/pagamentos/nova");
    await page.getByLabel("Valor (R$)").fill("vinte reais");
    await page.getByRole("button", { name: "Criar cobrança" }).click();
    await expect(page.getByText("Informe um valor em reais maior que zero")).toBeVisible();
    await page.getByLabel("Valor (R$)").fill("0,50");
    await page.getByRole("button", { name: "Criar cobrança" }).click();
    await expect(page.getByText("entre R$ 1,00 e R$ 100.000,00")).toBeVisible();

    // Corrigido, cria com a MESMA chave de idempotência (a API desfez a reserva quando recusou o valor).
    await page.getByLabel("Valor (R$)").fill("1.234,56");
    await page.getByLabel("Descrição (opcional)").fill("Pedido E2E pago");
    await page.getByRole("button", { name: "Criar cobrança" }).click();
    await expect(page).toHaveURL(/\/painel\/pagamentos\/[0-9a-f-]{36}$/);
    await expect(page.getByRole("heading", { level: 1 })).toHaveText(/R\$\s1\.234,56/);
    await expect(page.getByText("Aguardando")).toBeVisible();
    await expect(page.getByLabel("Código Pix copia e cola")).toHaveText(/^000201/);

    await page.getByRole("button", { name: "Simular pagamento" }).click();
    await expect(page.locator('[data-status="PAID"]')).toBeVisible();
    await expect(page.getByRole("button", { name: "Simular pagamento" })).toHaveCount(0);

    // Outra cobrança, cancelada (com confirmação).
    await createPayment(page, "10", "Pedido E2E cancelado");
    page.once("dialog", (dialog) => dialog.accept());
    await page.getByRole("button", { name: "Cancelar cobrança" }).click();
    await expect(page.locator('[data-status="CANCELED"]')).toBeVisible();

    // Lista + filtro.
    await page.goto("/painel/pagamentos");
    await expect(page.getByRole("cell", { name: "Pedido E2E pago" })).toBeVisible();
    await page.getByRole("link", { name: "Cancelados" }).click();
    await expect(page).toHaveURL(/status=CANCELED/);
    await expect(page.getByRole("cell", { name: "Pedido E2E cancelado" })).toBeVisible();
    await expect(page.getByRole("cell", { name: "Pedido E2E pago" })).toHaveCount(0);

    // Eventos: criado, pago e cancelado registrados.
    await page.goto("/painel/eventos");
    await expect(page.getByRole("cell", { name: /Pagamento pago/ })).toHaveCount(1);
    await expect(page.getByRole("cell", { name: /Pagamento criado/ })).toHaveCount(2);
    await expect(page.getByRole("cell", { name: /Pagamento cancelado/ })).toHaveCount(1);
    await page.getByRole("link", { name: "Pagamento pago" }).click();
    await page.getByRole("link").filter({ hasText: /^[0-9a-f]{8}$/ }).first().click();
    await expect(page.getByRole("heading", { name: "Conteúdo (igual ao enviado no webhook)" })).toBeVisible();
    await expect(page.locator("pre")).toContainText('"type": "payment.paid"');
  });

  test("idempotência: a resposta se perde na rede, o comerciante tenta de novo e só existe UMA cobrança", async () => {
    const page = shared;
    const description = `Pedido idempotente ${Date.now()}`;
    await page.goto("/painel/pagamentos/nova");

    // 1º envio: chega ao servidor (a cobrança é criada), mas a resposta "cai" antes de voltar ao navegador.
    let dropped = false;
    await page.route("**/painel/pagamentos/nova", async (route) => {
      if (route.request().method() === "POST" && !dropped) {
        dropped = true;
        await route.fetch(); // o servidor processa de verdade
        await route.abort("connectionreset");
        return;
      }
      await route.continue();
    });
    await page.getByLabel("Valor (R$)").fill("42,00");
    await page.getByLabel("Descrição (opcional)").fill(description);
    await page.getByRole("button", { name: "Criar cobrança" }).click();
    await expect(page.getByRole("heading", { name: "Não foi possível carregar esta página" })).toBeVisible();

    // 2º envio do MESMO formulário (mesma Idempotency-Key, mesmos dados): a API devolve a cobrança já criada.
    await page.getByRole("button", { name: "Tentar de novo" }).click();
    await page.getByLabel("Valor (R$)").fill("42,00");
    await page.getByLabel("Descrição (opcional)").fill(description);
    await page.getByRole("button", { name: "Criar cobrança" }).click();
    await expect(page).toHaveURL(/\/painel\/pagamentos\/[0-9a-f-]{36}$/);
    await page.unroute("**/painel/pagamentos/nova");

    await page.goto("/painel/pagamentos");
    await expect(page.getByRole("cell", { name: description })).toHaveCount(1);
  });

  test("API Keys: última chave protegida, segredo mostrado uma vez, produção exige e-mail confirmado", async () => {
    const page = shared;
    await page.goto("/painel/api-keys");
    await expect(page.getByText("1 de 5 chaves ativas")).toBeVisible();

    // A única chave ativa não pode ser revogada (a integração ficaria sem acesso).
    page.once("dialog", (dialog) => dialog.accept());
    await page.getByRole("button", { name: "Revogar" }).click();
    await expect(formError(page)).toContainText("última chave ativa");

    // Produção sem e-mail confirmado: recusado.
    await page.getByLabel(/Produção/).check();
    await page.getByRole("button", { name: "Criar chave" }).click();
    const createCard = page.getByRole("region", { name: "Criar chave" });
    await expect(createCard.getByRole("alert").filter({ hasText: "Erro:" })).toContainText("Confirme o seu e-mail");
    // A sessão continua viva: um 403 de regra de negócio não é "sessão expirada".
    await expect(page).toHaveURL(/\/painel\/api-keys$/);

    // Teste: criada, segredo aparece uma vez.
    await page.getByLabel(/Teste/).check();
    await page.getByRole("button", { name: "Criar chave" }).click();
    await expect(page.getByLabel("Nova API Key", { exact: true })).toHaveText(/^mx_test_/);
    await expect(page.getByText("2 de 5 chaves ativas")).toBeVisible();

    // Recarregou: o segredo não volta (só o prefixo na tabela).
    await page.reload();
    await expect(page.getByLabel("Nova API Key", { exact: true })).toHaveCount(0);

    // Agora a antiga pode ser revogada.
    page.once("dialog", (dialog) => dialog.accept());
    await page.getByRole("button", { name: "Revogar" }).first().click();
    await expect(page.getByText(/Revogada em/)).toBeVisible();
    await expect(page.getByText("1 de 5 chaves ativas")).toBeVisible();
  });

  test("webhooks: criar com segredo, ver o histórico com o autor e excluir", async () => {
    const page = shared;
    await page.goto("/painel/webhooks");

    // URL interna é recusada (proteção contra SSRF da API).
    await page.getByLabel("URL do endpoint").fill("http://127.0.0.1:8080/x");
    await page.getByRole("button", { name: "Criar endpoint" }).click();
    await expect(formError(page)).toContainText("https pública");

    await page.getByLabel("URL do endpoint").fill("https://example.com/webhooks/moneyx");
    await page.getByLabel(/Pagamento criado/).uncheck();
    await page.getByRole("button", { name: "Criar endpoint" }).click();
    await expect(page.getByLabel("Segredo de assinatura", { exact: true })).toHaveText(/^whsec_/);

    await page.getByRole("link", { name: "https://example.com/webhooks/moneyx" }).click();
    await expect(page.getByText("Pagamento pago, Pagamento cancelado, Pagamento expirado")).toBeVisible();
    await expect(page.getByText(/pelo painel/)).toBeVisible();

    page.once("dialog", (dialog) => dialog.accept());
    await page.getByRole("button", { name: "Excluir endpoint" }).click();
    await expect(page).toHaveURL(/\/painel\/webhooks$/);
    await expect(page.getByText("Nenhum endpoint cadastrado.")).toBeVisible();
  });

  test("isolamento: um comerciante não vê o pagamento de outro", async ({ browser }) => {
    const alice = shared;
    const paymentId = await createPayment(alice, "15,00", "Pedido da Alice");

    const bob = await browser.newPage(); // contexto separado = outro navegador, outro cookie
    await newSignedInMerchant(bob);
    await bob.goto(`/painel/pagamentos/${paymentId}`);
    await expect(bob.getByRole("heading", { name: "Não encontramos este item" })).toBeVisible();
    await expect(bob.getByText("Pedido da Alice")).toHaveCount(0);

    // Id malformado também cai em "não encontrado", sem erro técnico.
    await bob.goto("/painel/pagamentos/nao-e-um-uuid");
    await expect(bob.getByRole("heading", { name: "Não encontramos este item" })).toBeVisible();
  });
});
