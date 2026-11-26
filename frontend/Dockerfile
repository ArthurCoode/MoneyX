# Imagem do FRONTEND (Next.js: landing + painel + BFF), em três estágios, no mesmo padrão do Dockerfile do backend:
#   1. "deps":    instala EXATAMENTE o package-lock (npm ci);
#   2. "build":   gera o build de produção (output: standalone = servidor Node autocontido, só com o necessário);
#   3. "runtime": Node + o standalone, rodando com usuário sem privilégios.
#
# Base fixada por digest (a tag pode mudar de imagem; o digest não). O Dependabot propõe o digest novo.
# Nenhum segredo entra na imagem: o endereço da API chega por variável de ambiente (compose.yaml).
# As fontes (next/font/google) são baixadas NO BUILD e servidas pelo próprio site.

# ---------- Estágio 1: dependências ----------
FROM node:26-alpine@sha256:6bd9ab43c0ec0190ca7b2d193906cb8b7edf6e7f4afa43ddeccd6452710bffb5 AS deps
WORKDIR /app
COPY package.json package-lock.json .npmrc ./
RUN npm ci --no-fund --no-audit

# ---------- Estágio 2: build ----------
FROM node:26-alpine@sha256:6bd9ab43c0ec0190ca7b2d193906cb8b7edf6e7f4afa43ddeccd6452710bffb5 AS build
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

# ---------- Estágio 3: runtime ----------
FROM node:26-alpine@sha256:6bd9ab43c0ec0190ca7b2d193906cb8b7edf6e7f4afa43ddeccd6452710bffb5 AS runtime

# Usuário dedicado com UID/GID numéricos fixos (10001), como no backend ("runAsNonRoot" no Kubernetes).
# Em produção o container só executa "node server.js": npm, npx e corepack que vêm na imagem base saem. Menos
# superfície de ataque, e o Trivy deixa de acusar vulnerabilidades das dependências do PRÓPRIO npm (brace-expansion,
# undici em /usr/local/lib/node_modules/npm, achadas em 2026-11-27) que nada têm a ver com o nosso código.
RUN addgroup -S -g 10001 moneyx && adduser -S -D -H -u 10001 -G moneyx -s /sbin/nologin moneyx \
    && rm -rf /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/corepack \
              /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack

WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

# Arquivos pertencem ao root (somente leitura para o usuário moneyx): a aplicação não altera o próprio código.
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/server/forwarded-for-guard.cjs ./server/forwarded-for-guard.cjs

USER 10001:10001
EXPOSE 3000

HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:3000/entrar || exit 1

# -r: carrega o guarda do X-Forwarded-For antes do servidor do Next (ver server/forwarded-for-guard.cjs).
ENTRYPOINT ["node", "-r", "./server/forwarded-for-guard.cjs", "server.js"]
