import type { NextConfig } from "next";

/**
 * Headers de segurança (mesma linha do backend, Fase 6 Etapa 5d). A Content-Security-Policy completa (com nonce para
 * os scripts do Next) entra junto com o dashboard/BFF, quando houver login.
 */
const securityHeaders = [
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "no-referrer" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
];

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Não anunciar a tecnologia ("X-Powered-By: Next.js").
  poweredByHeader: false,
  // Imagem Docker enxuta: o build gera um servidor Node autocontido em .next/standalone.
  output: "standalone",
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

export default nextConfig;
