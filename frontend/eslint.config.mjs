import next from "eslint-config-next";

// Regras do Next.js (inclui React, hooks, acessibilidade básica e TypeScript).
const config = [
  ...next,
  { ignores: [".next/", "node_modules/", "src/lib/api/schema.d.ts"] },
];

export default config;
