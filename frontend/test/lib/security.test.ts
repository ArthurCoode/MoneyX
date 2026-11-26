import { describe, expect, it } from "vitest";
import { contentSecurityPolicy, isCrossSiteWrite, newNonce } from "@/lib/security";
import { clientIpFrom } from "@/lib/server/client-ip";

describe("isCrossSiteWrite (CSRF)", () => {
  it("lets reads through regardless of Origin", () => {
    expect(isCrossSiteWrite("GET", null, "localhost:3000")).toBe(false);
    expect(isCrossSiteWrite("HEAD", "https://evil.example", "localhost:3000")).toBe(false);
  });

  it("accepts a write from the same host", () => {
    expect(isCrossSiteWrite("POST", "http://localhost:3000", "localhost:3000")).toBe(false);
  });

  it.each([
    ["another site", "https://evil.example"],
    ["same host, other port", "http://localhost:4000"],
    ["subdomain lookalike", "http://localhost:3000.evil.example"],
    ["opaque origin", "null"],
    ["garbage", "not a url"],
  ])("blocks a write from %s", (_label, origin) => {
    expect(isCrossSiteWrite("POST", origin, "localhost:3000")).toBe(true);
  });

  it("blocks a write WITHOUT Origin (Next alone only warns here)", () => {
    expect(isCrossSiteWrite("POST", null, "localhost:3000")).toBe(true);
    expect(isCrossSiteWrite("delete", null, "localhost:3000")).toBe(true);
  });

  it("blocks a write when the Host header is missing", () => {
    expect(isCrossSiteWrite("POST", "http://localhost:3000", null)).toBe(true);
  });
});

describe("contentSecurityPolicy", () => {
  it("allows only scripts carrying this response's nonce in production", () => {
    const csp = contentSecurityPolicy("abc123", false);
    expect(csp).toContain("script-src 'self' 'nonce-abc123' 'strict-dynamic'");
    expect(csp).not.toContain("unsafe-eval");
    expect(csp).not.toMatch(/script-src[^;]*unsafe-inline/);
    expect(csp).toContain("frame-ancestors 'none'");
    expect(csp).toContain("object-src 'none'");
    expect(csp).toContain("form-action 'self'");
  });

  it("adds eval and WebSocket only in development", () => {
    const csp = contentSecurityPolicy("abc123", true);
    expect(csp).toContain("'unsafe-eval'");
    expect(csp).toContain("connect-src 'self' ws:");
  });

  it("generates a different nonce per request", () => {
    expect(newNonce()).not.toBe(newNonce());
  });
});

describe("clientIpFrom", () => {
  it("returns undefined without the header", () => {
    expect(clientIpFrom(null)).toBeUndefined();
  });

  it("takes the LAST hop (added by the nearest proxy), not the first one the client may forge", () => {
    expect(clientIpFrom("6.6.6.6, 203.0.113.7")).toBe("203.0.113.7");
  });

  it("accepts IPv6", () => {
    expect(clientIpFrom("::ffff:127.0.0.1")).toBe("::ffff:127.0.0.1");
  });

  it("drops values that are not an IP instead of forwarding them", () => {
    expect(clientIpFrom("1.2.3.4, <script>")).toBeUndefined();
    expect(clientIpFrom("1.2.3.4\r\nX-Evil: 1")).toBeUndefined();
  });
});
