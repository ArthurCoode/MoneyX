import { expect, test, type Page } from "@playwright/test";

/** Coleta erros do console (inclui violações de CSP e falhas de WebGL/three.js). */
function collectErrors(page: Page): string[] {
  const errors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(message.text());
  });
  page.on("pageerror", (error) => errors.push(error.message));
  return errors;
}

test.describe("landing page (8c)", () => {
  test("herói com 3D quando há WebGL, sem erros no console nem violação de CSP", async ({ page }) => {
    const errors = collectErrors(page);
    await page.goto("/");
    await expect(page.getByRole("heading", { level: 1 })).toContainText("Pix para o seu negócio");

    const webgl = await page.evaluate(() => {
      const canvas = document.createElement("canvas");
      return Boolean(canvas.getContext("webgl2") ?? canvas.getContext("webgl"));
    });
    const visual = page.locator("[data-hero-visual]");
    if (webgl) {
      await expect(visual).toHaveAttribute("data-hero-visual", "3d");
      await expect(visual.locator("canvas")).toBeVisible();
    } else {
      await expect(visual).toHaveAttribute("data-hero-visual", "static");
    }
    // A cena é decorativa: escondida dos leitores de tela.
    await expect(visual).toHaveAttribute("aria-hidden", "true");
    // O indicador de devtools do Next (o "N" no canto) não aparece para o visitante (devIndicators: false).
    await expect(page.locator("[data-next-badge-root]")).toBeHidden();

    await page.waitForTimeout(500);
    expect(errors).toEqual([]);
  });

  test("com 'reduzir movimento', nada de 3D nem animação, e o conteúdo continua visível", async ({ page }) => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    const requests: string[] = [];
    page.on("request", (request) => requests.push(request.url()));
    await page.goto("/");

    await expect(page.locator("[data-hero-visual]")).toHaveAttribute("data-hero-visual", "static");
    await expect(page.locator("canvas")).toHaveCount(0);

    // Sem rolar: as seções já estão visíveis (o GSAP não escondeu nada).
    const lastFeature = page.locator("#recursos li").last();
    await expect(lastFeature).toHaveCSS("opacity", "1");
    expect(requests.some((url) => /three|fiber/i.test(url))).toBe(false);

    // Rolando até lá: nada de animação de entrada (com GSAP ativo, logo após rolar ela estaria no meio do fade).
    await lastFeature.scrollIntoViewIfNeeded();
    await page.waitForTimeout(150);
    const opacity = await lastFeature.evaluate((element) => getComputedStyle(element).opacity);
    expect(opacity).toBe("1");
  });

  test("seções aparecem ao rolar e os botões levam ao cadastro e ao login", async ({ page }) => {
    await page.goto("/");
    const lastFeature = page.locator("#recursos li").last();
    await lastFeature.scrollIntoViewIfNeeded();
    await expect(lastFeature).toHaveCSS("opacity", "1", { timeout: 5000 });

    await page.goto("/");
    await page.getByRole("link", { name: "Começar no modo de teste" }).click();
    await expect(page).toHaveURL(/\/cadastro$/);
    await page.goto("/");
    await page.getByRole("banner").getByRole("link", { name: "Entrar" }).click();
    await expect(page).toHaveURL(/\/entrar$/);
  });

  test("depois da revelação, o GSAP não deixa estilo inline e o hover dos cartões funciona", async ({ page }) => {
    await page.goto("/");
    const card = page.locator("#recursos li").first();
    await card.scrollIntoViewIfNeeded();
    // Espera a revelação ACONTECER e terminar (0,8 s + escalonamento). Checar antes dava falso positivo: o atributo
    // ainda estava vazio porque o GSAP nem tinha começado (achado por mutação).
    await page.waitForTimeout(1800);
    expect((await card.getAttribute("style")) ?? "").not.toMatch(/transform|opacity/);

    const inner = card.locator("div").first();
    await inner.hover();
    // Regressão de 2026-11-27: o transform inline do GSAP vencia o CSS e o cartão não subia.
    await expect.poll(() => inner.evaluate((el) => getComputedStyle(el).transform)).toBe("matrix(1, 0, 0, 1, 0, -4)");
  });

  test("celular: sem rolagem lateral e com acesso ao login", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/");
    await expect(page.getByRole("banner").getByRole("link", { name: "Entrar" })).toBeVisible();
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBe(0);
  });
});
