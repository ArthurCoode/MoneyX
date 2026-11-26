// @vitest-environment node
import { createServer, request, type Server } from "node:http";
import type { AddressInfo } from "node:net";
import { afterEach, describe, expect, it } from "vitest";
// O guarda se instala ao ser carregado (como no `node -r` da imagem Docker).
import "../../server/forwarded-for-guard.cjs";

let server: Server | undefined;

afterEach(() => {
  server?.close();
  server = undefined;
});

/** Sobe um servidor HTTP de verdade que devolve o X-Forwarded-For que o "Next" enxergaria. */
async function forwardedForSeenByServer(headers: Record<string, string>): Promise<string | undefined> {
  server = createServer((req, res) => res.end(String(req.headers["x-forwarded-for"] ?? "")));
  await new Promise<void>((resolve) => server!.listen(0, "127.0.0.1", resolve));
  const { port } = server.address() as AddressInfo;
  return new Promise((resolve, reject) => {
    const req = request({ host: "127.0.0.1", port, headers }, (res) => {
      let body = "";
      res.on("data", (chunk: Buffer) => (body += chunk.toString()));
      res.on("end", () => resolve(body || undefined));
    });
    req.on("error", reject);
    req.end();
  });
}

describe("forwarded-for-guard", () => {
  it("troca um X-Forwarded-For forjado pelo IP real da conexão", async () => {
    expect(await forwardedForSeenByServer({ "X-Forwarded-For": "6.6.6.6" })).toBe("127.0.0.1");
  });

  it("troca também uma lista inteira (o BFF usaria o último item)", async () => {
    expect(await forwardedForSeenByServer({ "X-Forwarded-For": "1.1.1.1, 6.6.6.6" })).toBe("127.0.0.1");
  });

  it("preenche quando o header não veio", async () => {
    expect(await forwardedForSeenByServer({})).toBe("127.0.0.1");
  });
});
