import { defineConfig, devices } from "@playwright/test";

/**
 * Testes ponta a ponta: navegador de verdade → Next (BFF) → API Java → Postgres/Redis/Mailpit.
 * Rodam contra um ambiente JÁ NO AR (Compose ou `npm start` + Compose): `npm run e2e`.
 * E2E_BASE_URL e MAILPIT_URL mudam os endereços (padrão: localhost).
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [["list"]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    trace: "retain-on-failure",
    locale: "pt-BR",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
