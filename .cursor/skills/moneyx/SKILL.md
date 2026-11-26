
---
name: moneyx
description: Contexto, arquitetura, decisões técnicas, regras de desenvolvimento e roadmap do projeto MoneyX, uma plataforma de infraestrutura de pagamentos desenvolvida em Java e Spring Boot.
---

# MoneyX — Project Skill

## 1. Identidade do projeto

**Nome:** MoneyX

**Repositório:** https://github.com/ArthurCoode/MoneyX

**Objetivo:** desenvolver uma plataforma de infraestrutura de pagamentos (Payment Infrastructure Platform), inicialmente como projeto de aprendizado e portfólio, com possibilidade de evolução para um produto comercial.

O MoneyX deve ser desenvolvido com padrões de engenharia próximos aos utilizados em sistemas reais de pagamentos.

O projeto não deve ser apenas um CRUD demonstrativo. A intenção é compreender e implementar os conceitos fundamentais de uma infraestrutura de pagamentos: comerciantes, autenticação, transações, processamento, idempotência, estados, notificações, segurança e rastreabilidade.

### Objetivos do projeto

- Consolidar conhecimentos de Java e Spring Boot.
- Aprender arquitetura de sistemas financeiros.
- Construir uma API de pagamentos consistente e segura.
- Desenvolver um projeto relevante para portfólio profissional.
- Criar uma base que possa evoluir para um produto comercial.
- Priorizar qualidade técnica, testes, organização e documentação.

## 2. Perfil do desenvolvedor

O proprietário do projeto é desenvolvedor júnior e está aprofundando seus conhecimentos em Java.

A assistência durante o desenvolvimento deve ter caráter educativo.

Não basta fornecer código pronto: é necessário explicar as decisões e os conceitos envolvidos para que o desenvolvedor compreenda o sistema que está construindo.

O projeto também representa uma oportunidade de evolução profissional em engenharia de software e backend.

## 3. Regras obrigatórias de desenvolvimento

1. Explicar o que será desenvolvido e por que antes de implementar.
2. Não avançar várias etapas sem que a etapa atual esteja compreendida e validada.
3. Evitar criar muitos arquivos de uma só vez.
4. Apresentar os conceitos técnicos antes de introduzir abstrações complexas.
5. Priorizar soluções simples, seguras e sustentáveis.
6. Não adicionar tecnologias apenas por tendência.
7. Não realizar alterações fora do escopo acordado sem autorização.
8. Não executar comandos destrutivos sem explicar seus efeitos e obter autorização.
9. Nunca expor credenciais, tokens, senhas ou informações sensíveis.
10. Nunca enviar arquivos `.env` ou segredos ao Git.
11. Não executar migrations ou alterações de banco sem explicar previamente seus efeitos e obter autorização.
12. Criar testes para comportamentos importantes.
13. Manter documentação atualizada conforme o projeto evolui.
14. Não afirmar que algo foi testado ou executado sem evidências.

### Metodologia de trabalho

Para cada funcionalidade:

1. Entender o problema.
2. Explicar os conceitos envolvidos.
3. Definir o comportamento esperado.
4. Apresentar a arquitetura e os arquivos envolvidos.
5. Implementar em etapas pequenas.
6. Executar testes.
7. Validar o resultado.
8. Registrar as alterações no Git.

## 4. Arquitetura escolhida

A arquitetura inicial será um **Monólito Modular**.

Não iniciar com microsserviços.

Motivos:

- Reduz a complexidade operacional.
- Facilita o aprendizado.
- Permite separação clara de responsabilidades.
- Evita distribuir prematuramente partes do sistema.
- Possibilita extrair serviços posteriormente, caso exista necessidade real.

Os módulos devem ter responsabilidades bem definidas e baixo acoplamento.

## 5. Stack tecnológica

| Tecnologia | Responsabilidade |
|---|---|
| Java 25 LTS | Linguagem principal (desde 2026-11-25; antes 21) |
| Spring Boot 4.1.2 | Framework backend |
| Maven | Gerenciamento de dependências e build |
| PostgreSQL 17 | Banco de dados principal |
| Spring Data JPA | Persistência |
| Hibernate | ORM |
| Flyway | Versionamento do banco (em uso desde a Fase 2) |
| Redis | Cache e recursos auxiliares, futuro |
| RabbitMQ | Mensageria assíncrona, futuro |
| Docker | Infraestrutura local |
| JUnit 5 | Testes |
| Testcontainers 2.x | PostgreSQL descartável em Docker para os testes |
| Mockito | Testes com dependências simuladas |
| Spring Security | Segurança e autenticação |
| springdoc-openapi 3.2.0 (OpenAPI 3.1) | Contrato da API + Swagger UI (`/v3/api-docs`, `/swagger-ui.html`) |
| GitHub Actions | CI: build + testes + imagem Docker com Trivy a cada push/PR (`.github/workflows/ci.yml`) |
| Dependabot | PRs semanais de atualização (Maven, Actions, Docker) (`.github/dependabot.yml`) |

### Decisões importantes

- Java 25 LTS é a versão do projeto desde 2026-11-25 (pom `java.version`, CI `setup-java` 25, Dockerfile
  `eclipse-temurin:25-jdk/jre-alpine` por digest). JDK 25 instalado na máquina (Temurin, winget) ao lado do 21:
  `JAVA_HOME` precisa apontar para o 25 (IntelliJ: Project SDK 25). Aviso conhecido nos testes: Mockito carrega o
  agente do ByteBuddy dinamicamente ("A Java agent has been loaded dynamically"); hoje é só aviso.
- Spring Boot 4.1.2 é a versão atualmente gerada pelo Spring Initializr.
- Não trocar versões sem avaliar compatibilidade e necessidade.
- PostgreSQL será a fonte principal da verdade.
- Redis não substituirá o banco relacional.
- RabbitMQ será introduzido quando houver necessidade de processamento assíncrono.
- A arquitetura começará como monólito modular.
- O esquema do banco é gerenciado exclusivamente pelo Flyway; o Hibernate apenas valida (`ddl-auto=validate`).
- Identificadores de entidades expostas na API são `UUID` gerados na aplicação, não sequenciais.
- Entidades não expõem setters: alterações de estado ocorrem por métodos de domínio que validam a transição.
- Regras de unicidade são verificadas no serviço (mensagem clara) e garantidas por constraints no banco (proteção contra condição de corrida).
- Entidades JPA nunca são expostas diretamente na API; a camada `api` utiliza DTOs.

## 6. Ambiente atual

### Sistema operacional

Windows 11.

### IDE

IntelliJ IDEA.

### Java

Eclipse Temurin JDK 21.0.13.

### Maven

Maven Wrapper incluído no projeto.

Executar comandos Maven pelo wrapper:

```powershell
.\mvnw.cmd
```

### Repositório

```text
C:\Users\Arthur Marques\Desktop\MoneyX
```

### Estrutura atual

```text
MoneyX/
├── .cursor/skills/moneyx/SKILL.md
├── .mvn/
├── src/
│   ├── main/
│   │   ├── java/com/moneyx/
│   │   │   ├── MoneyxApplication.java
│   │   │   ├── merchant/                  ← módulo
│   │   │   │   ├── domain/                ← entidades e regras de negócio
│   │   │   │   ├── repository/            ← acesso a dados (Spring Data JPA)
│   │   │   │   ├── service/               ← casos de uso e transações
│   │   │   │   ├── exception/             ← erros de negócio do módulo
│   │   │   │   └── api/                   ← controllers, DTOs e tratamento de erros do módulo
│   │   │   ├── apikey/                    ← módulo de API Keys (mesmas camadas do merchant)
│   │   │   ├── payment/                   ← módulo de pagamentos (mesmas camadas + job/ para agendamentos
│   │   │   │                                 + provider/ para o emissor da cobrança Pix: sandbox ou PSP)
│   │   │   ├── event/                     ← módulo de eventos (histórico + Outbox), genérico
│   │   │   ├── webhook/                   ← endpoints, linha do tempo, entregas assinadas (+ job/)
│   │   │   ├── docs/                      ← OpenApiConfig + textos do guia (contrato OpenAPI)
│   │   │   ├── security/                  ← Spring Security: filtro de API Key, SecurityConfig, erros 401/403
│   │   │   └── shared/                    ← código transversal, sem dependência de módulos
│   │   │       ├── observability/         ← id de correlação (RequestIdFilter, RequestIds, RequestIdErrorAttributes)
│   │   │       ├── api/                   ← GlobalExceptionHandler (Problem Details)
│   │   │       ├── config/                ← ClockConfig, RequiredSettingsCheck (falha cedo sem configuração obrigatória)
│   │   │       └── crypto/                ← SecretCipher (AES-256-GCM) e CryptoConfig
│   │   └── resources/
│   │       ├── application.properties
│   │       └── db/migration/              ← migrations Flyway (V1__..., V2__...)
│   └── test/
│       ├── java/com/moneyx/               ← espelha a estrutura de main
│       └── resources/config/application.properties ← propriedades EXTRAS dos testes (ex.: jobs desligados)
├── .gitignore
├── .dockerignore
├── Dockerfile                             ← imagem da app (multi-stage, não-root)
├── compose.yaml                           ← postgres + app
├── mvnw / mvnw.cmd
└── pom.xml
```

### Organização dos módulos

O código é organizado primeiro por módulo (*package by feature*) e depois por camada.
Novos módulos (`payment`, `webhook`, etc.) devem seguir a mesma estrutura do `merchant`.

Direção obrigatória das dependências:

```text
api  →  service  →  repository  →  domain
           ↓
       exception
```

- `domain` não importa nenhuma outra camada.
- `repository` conhece apenas `domain`.
- `service` orquestra `domain` e `repository` e lança as exceções do módulo.
- `api` conhece apenas `service` e seus DTOs; nunca acessa o repositório diretamente.
- `job` (agendamentos) segue a mesma regra de `api`: é só um ponto de entrada que chama `service`.
- Não adotar arquitetura hexagonal (ports/adapters) sem necessidade concreta. **Exceção com necessidade concreta (Fase 7):**
  `payment/provider/PixProvider` é uma porta para o PSP (sandbox local ou Mercado Pago). Direção: `service → provider → domain`;
  `provider` não conhece `service`, `repository` nem `api`.
- `shared` contém apenas código transversal e nunca importa classes de módulos.
- Dependências entre módulos são unidirecionais e explícitas. Atualmente: `merchant → apikey` e
  `security → merchant, apikey`, `payment → event` e `webhook → event`. `payment` referencia o comerciante por `merchantId`.
  `event` não importa nenhum módulo de negócio: recebe tipo, recurso e retrato prontos. Quando `event` precisa saber algo de
  quem o usa (ex.: "este evento ainda é referenciado?"), ele declara uma interface (`EventReferences`) e o outro módulo a
  implementa (inversão de dependência; a direção continua `webhook → event`). Nenhum módulo de negócio importa `security`, exceto a camada `api`
  para ler o `AuthenticatedMerchant` (via `@AuthenticationPrincipal`).
  `apikey` nunca importa `merchant`; referencia o comerciante apenas por `merchantId` (UUID).
  Entidades de módulos diferentes não usam `@ManyToOne` entre si; a integridade é garantida por FK no banco.
- Respostas que contêm segredos usam `Cache-Control: no-store`, e objetos que carregam segredos
  sobrescrevem `toString()` para não vazá-los em logs.

### Segurança (Spring Security)

- `security/SecurityConfig` define a única `SecurityFilterChain`: stateless, CSRF/httpBasic/formLogin/logout
  desabilitados. Rotas públicas: `POST /v1/merchants`, a documentação (`/v3/api-docs`, `/swagger-ui`), `GET /actuator/**` (que só
  existe na porta de gerenciamento 8081, em 127.0.0.1), o despacho interno `ERROR` e, desde a Etapa 7e,
  `POST /v1/provider-webhooks/mercadopago` (aviso do MP: sem API Key, autenticado pela assinatura `x-signature` no controller).
  Todo o resto exige a autoridade `ACTIVE_MERCHANT`.
- `security/ApiKeyAuthenticationFilter` (não é `@Component`, é instanciado no `SecurityConfig`):
  sem header → segue anônimo (rota protegida → 401); header inválido → 401 imediato, mesmo em rota pública;
  chave válida → `AuthenticatedMerchant(merchantId, apiKeyId)` no `SecurityContext`, com `ACTIVE_MERCHANT`
  apenas se o comerciante estiver `ACTIVE` (senão, 403 pela regra de autorização).
- O entry point (401) e o access denied handler (403) repassam a exceção ao `HandlerExceptionResolver`
  do MVC; `security/SecurityExceptionHandler` gera o Problem Details (401 inclui `WWW-Authenticate: Bearer`).
- O usuário padrão do Boot é desligado em `application.properties`
  (`spring.autoconfigure.exclude=...UserDetailsServiceAutoConfiguration`).
- Controllers obtêm o comerciante com `@AuthenticationPrincipal AuthenticatedMerchant`, nunca pela URL
  (proteção contra IDOR). Ex.: `GET /v1/merchants/me`. Não criar rotas que recebam o `merchantId` do cliente.
- Endpoints atuais de comerciante: `POST /v1/merchants` (público, `Location: /v1/merchants/me`) e `GET /v1/merchants/me`.

### Headers HTTP de segurança (definido em 2026-11-21, Fase 6 Etapa 5d)

- Padrões do Spring Security mantidos: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
  `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`, `X-XSS-Protection: 0`. Sem header `Server`.
  O `Cache-Control: no-store` explícito das respostas com segredo continua (não depender só do padrão).
- `Referrer-Policy: no-referrer` em tudo.
- `Content-Security-Policy` em DUAS versões (`SecurityConfig`, `DelegatingRequestMatcherHeaderWriter`):
  API/JSON → `default-src 'none'; frame-ancestors 'none'` (OWASP REST); Swagger UI (`/swagger-ui.html`,
  `/swagger-ui/**`) → só a própria origem, `'unsafe-inline'` apenas em estilos. **Mudou a Swagger UI (versão, CDN,
  plugin)? Conferir no navegador** (print headless do Edge + `chrome_debug.log` com `--enable-logging`): em 2026-11-21
  a página renderizou completa e sem violações. A Swagger UI chama `http://localhost:8080` (servidor fixo):
  aberta por `127.0.0.1:8080`, o "Try it out" seria barrado por `connect-src 'self'` (origem diferente).
- **HSTS** só é enviado em HTTPS (correto). Atrás de proxy com TLS: `MONEYX_TRUSTED_PROXIES` com o IP do proxy faz o
  `X-Forwarded-Proto: https` valer (ver "Rate limiting do cadastro público").
- **Log de requisição malformada:** o Tomcat (`Http11Processor`) registrava a LINHA do header inválido (com a API Key)
  em INFO na 1ª ocorrência e DEBUG depois → `logging.level.org.apache.coyote.http11.Http11Processor=WARN`.
  Acontece antes do nosso código: fora do alcance do `ObservabilityIntegrationTest`.
- Testes em servidor Tomcat REAL: `security/HttpHardeningIntegrationTest` (socket cru com um "canário" em formato de
  API Key; headers da API e da Swagger UI). Mesmas anotações do `ManagementPortIntegrationTest` (contexto compartilhado).

### Documentação da API / contrato OpenAPI (definido em 2026-11-20)

- springdoc-openapi **3.2.0** (construído sobre o Spring Boot 4.1.2; versão fixada em `springdoc.version`, o Boot não a
  gerencia). Code-first. `springdoc.api-docs.version=openapi_3_1` (necessário para `webhooks`).
- **Legibilidade da Swagger UI** (ajustada em 2026-11-20 a pedido do desenvolvedor, conferida por print headless do Edge):
  topo curto (frase + "Primeiros passos" numerado + tabela "Regras gerais"); grupos recolhidos
  (`doc-expansion=none`); seção Schemas escondida (`default-models-expand-depth=-1`); `persist-authorization`,
  `display-request-duration`, `filter`. **Não usar `writer-with-order-by-keys`**: ele punha DELETE antes de GET.
  A ordem vem do `OpenApiCustomizer` (`sortPaths`: caminhos em ordem alfabética, métodos na ordem padrão do OpenAPI;
  webhooks na ordem do ciclo de vida, com `operationId` `paymentPaidWebhook` etc.).
- `/v3/api-docs` e `/swagger-ui.html` são **públicos** (liberados no `SecurityConfig`), como a referência do Stripe.
  Desligáveis com `springdoc.api-docs.enabled=false` / `springdoc.swagger-ui.enabled=false`.
- `docs/OpenApiConfig`: info + guia (`DocsText`: autenticação, centavos, idempotência, paginação, erros, limites,
  webhooks), servidor fixo `http://localhost:8080`, esquema `bearerAuth`, schema `Problem` (RFC 9457 + `code` + `errors`),
  respostas reutilizáveis (`BadRequest`, `Unauthorized`, `Forbidden`, `NotFound`, `Conflict`, `UnprocessableContent`,
  `TooManyRequests`), e um `OpenApiCustomizer` que adiciona 401/403 a toda rota protegida e deixa o cadastro sem
  segurança + 429. Seção `webhooks` (OpenAPI 3.1) com os 4 eventos, o schema `Event` e o guia de verificação da assinatura.
- Cada operação tem `@Operation(operationId, summary, description)` e `@ApiResponse` com o status REAL (201/204) e os
  erros. **O springdoc sozinho errava**: 200 para criações e para o DELETE de webhook, sem erros, `operationId` como
  `create_1`, tags com nome de classe. Code-first gera a estrutura; status, erros e nomes precisam ser declarados.
- Campos `@JsonRawValue String` são documentados como objeto com `@Schema(types = {"object"})`: no OpenAPI 3.1 o atributo
  é `types` (plural); `type` é ignorado.
- **Teste de contrato** (`docs/OpenApiContractTest`): o `/v3/api-docs` gerado tem de ser idêntico a
  `src/test/resources/openapi/moneyx-api.json` (o teste ORDENA as chaves e usa fim de linha `\n` só na comparação,
  para não depender da ordem de exibição). Mudança intencional:
  `.\mvnw.cmd test -Dtest=OpenApiContractTest -Dopenapi.update=true` e revisar o diff no commit. Toda mudança de DTO,
  validação ou rota exige regenerar o arquivo.

### Observabilidade (definido em 2026-11-20)

- **Id de correlação:** `shared/observability/RequestIdFilter` (`@Order(HIGHEST_PRECEDENCE)`, antes da segurança) aceita o
  `X-Request-Id` do cliente se casar com `^[A-Za-z0-9._-]{1,64}$` (contra log injection); senão gera UUID. Vai para o MDC
  (`requestId`), para um atributo da requisição e para o header da resposta; o MDC é SEMPRE limpo no `finally`.
  Exceção não tratada é logada no filtro (ainda com o id). `ProblemDetailRequestIdAdvice` (ResponseBodyAdvice) põe
  `requestId` em TODO Problem Details (inclusive 401/403/429 dos filtros); `RequestIdErrorAttributes` no `/error` (500).
  Jobs: `RequestIds.runWithNewId("job", ...)` por execução.
- **Logs:** texto com `[requestId]` em cada linha (`logging.pattern.correlation`); JSON ECS em produção com
  `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (o `requestId` vira campo). Logs de negócio só com ids:
  `merchant.registered`, `api_key.created/revoked`, `webhook_endpoint.created/deleted/enabled`, `payment.*` (via
  `PaymentObservability`). **Nunca** API Key, `whsec_`, documento, e-mail (guardado por `ObservabilityIntegrationTest`).
- **Métricas (Micrometer):** `moneyx.payments.events{type}` contado em `@TransactionalEventListener(AFTER_COMMIT)`
  (o oposto do Outbox: só o confirmado conta; testado com commit que falha na FK DEFERRABLE);
  `moneyx.webhook.deliveries{result,error}` e `moneyx.webhook.delivery.duration{outcome}` (a tentativa conta mesmo se o
  commit falhar: ela aconteceu); `moneyx.rate_limit.rejections{route}`. Etiquetas só com conjuntos pequenos e fechados:
  **nunca ids** (explosão de cardinalidade).
- **Exposição:** `management.server.port=8081`, `management.server.address=127.0.0.1`, endpoints `health,metrics,prometheus`
  (`micrometer-registry-prometheus`, runtime). Na 8080 o Actuator não existe. Nos testes a exportação de métricas é
  desligada pelo Boot; `ManagementPortIntegrationTest` usa `@AutoConfigureMetrics` + `RANDOM_PORT` + `management.server.port=0`.

### Retenção de eventos e entregas (definido em 2026-11-21, Fase 6 Etapa 6b)

- **30 dias**, como o Stripe: `moneyx.events.retention=30d` (mínimo 1 dia) e `moneyx.webhooks.delivery.retention=30d`
  (mínimo 7 dias, mais que a agenda de retentativas). Depois disso, `GET /v1/events/{id}` e as entregas respondem 404
  (linha "Retenção" no guia da API). **Pagamentos nunca são apagados**; a linha do tempo de webhooks é guardada para sempre.
- Cada módulo apaga só a PRÓPRIA tabela, num job próprio a cada 1 hora: `event/job/EventRetentionJob` e
  `WebhookDeliveryJob.purgeExpiredDeliveries`. Sem ordem fixa entre eles: um evento ainda referenciado fica para a
  rodada seguinte.
- Entregas: só FINALIZADAS (`SUCCEEDED`, `FAILED`, `CANCELED`) e `created_at < corte`; CTE MATERIALIZED + SKIP LOCKED,
  laço até lote vazio (mesmo formato da limpeza de chaves de idempotência). Pendentes nunca.
- Eventos: `EventRetentionService` tranca candidatos (`created_at < corte`) com **cursor `(created_at, id)`** e SKIP LOCKED,
  pergunta a cada `EventReferences` quais ainda são referenciados e apaga o resto. O cursor é obrigatório: sem ele,
  candidatos referenciados no começo da fila voltariam em todo lote e o job nunca passaria deles. A FK
  `webhook_deliveries → events` continua sendo a última barreira.
- Índices (V12): `events (created_at, id)` e parcial `webhook_deliveries (created_at) WHERE status` finalizado (EXPLAIN
  conferido: ambos usados; o cursor entra no Index Cond).
- Testes: `webhook/service/DataRetentionIntegrationTest` (relógio em 2020; limite exato do corte; pendente nunca; ordem
  independente dos jobs; 501 referenciados à frente; vários lotes). Classe com
  `@Timeout(value = 60, threadMode = SEPARATE_THREAD)`: laço infinito vira falha em vez de travar a suíte.

### Limite acumulado de volume por comerciante (definido em 2026-11-24, Fase 6 Etapa 6c)

- **R$ 200.000,00 por dia e R$ 2.000.000,00 por mês** por comerciante (`moneyx.payments.volume-limit.daily` / `.monthly`,
  centavos `long`; `PaymentVolumeLimitProperties`). Controle de risco contra cobranças em massa com chave roubada.
  **Limite por comerciante (V15, 2026-11-25):** colunas opcionais `merchants.daily_volume_limit` e
  `monthly_volume_limit` (centavos; NULL = global; CHECK > 0 e diário <= mensal). Ajuste só por SQL (não há área de
  operador), ex.: `UPDATE merchants SET daily_volume_limit = 50000000 WHERE id = '...';`. Lidas na MESMA consulta
  que tranca o comerciante (`lockMerchantForPaymentCreation` devolve uma projeção). A entidade `Merchant` não mapeia
  essas colunas. O limite efetivo aparece em `GET /v1/merchants/me` (`volumeLimits`), via `PaymentVolumeLimitQuery`
  (dependência nova `merchant.api → payment.service`, sem ciclo).
- Conta pagamentos **criados e não cancelados** (PENDING, PAID, EXPIRED), por `created_at`. "Dia" e "mês" no fuso
  `moneyx.payments.volume-limit.zone` (padrão `America/Sao_Paulo`, sempre `ZoneId`, nunca deslocamento fixo).
- Recusa: **422** `volume_limit_exceeded` com `period` (`daily`|`monthly`), `limit`, `remaining` (centavos) e `resetsAt`
  (início do próximo dia/mês local). Se os dois estouram, responde `monthly` (tentar amanhã também falharia).
- Onde: `PaymentService.create` → `PaymentVolumeLimit.check` (MANDATORY) só quando a chave foi reservada agora
  (repetição não conta nem é recusada), antes do `save`. A recusa desfaz também a reserva da Idempotency-Key.
- **Trava:** `PaymentRepository.lockMerchantForPaymentCreation` = `SELECT ... FROM merchants ... FOR NO KEY UPDATE`,
  ANTES da reserva da chave. Sem trava, 10 criações simultâneas furavam o limite (mutação M1). **Deadlock:** os INSERTs
  com FK para `merchants` pegam `FOR KEY SHARE`; `FOR UPDATE` conflita com ele. As duas proteções (ordem: trava antes
  da reserva; modo: `NO KEY UPDATE` não conflita com KEY SHARE) se cobrem: provado por mutação que só removendo AS
  DUAS (`FOR UPDATE` depois da reserva) aparece `deadlock detected`. `NO KEY UPDATE` também não bloqueia pagar/cancelar/
  expirar outros pagamentos do comerciante (eles gravam eventos, que têm FK para `merchants`).
- Soma: `COALESCE(SUM(amount), 0) ... WHERE merchant_id = ? AND created_at >= ? AND status <> 'CANCELED'`
  (índice `(merchant_id, created_at)` da V3). `Math.addExact` na comparação.
- Testes: `PaymentVolumeLimitIntegrationTest` (limite exato, `remaining`, recusa sem gravar nada e chave reutilizável,
  replay, cancelados/pagos/expirados, isolamento, virada do dia e do mês em São Paulo, concorrência `@RepeatedTest(5)`).

### Rate limiting do cadastro público (definido em 2026-11-20)

- `security/ratelimit/SignupRateLimitFilter` (instanciado no `SecurityConfig`, antes do filtro de API Key) limita
  `POST /v1/merchants` por IP com `TokenBucketRateLimiter`: **5 tentativas em rajada, +1 a cada 2 min**
  (`moneyx.rate-limit.signup.*`; nos testes `capacity=1000000`, e os testes do próprio limite usam valores pequenos).
- **Toda tentativa conta**, inclusive 400/409 (o 409 é o que permite enumerar clientes); a recusa acontece antes do controller.
- Recusa: **429** Problem Details `code: rate_limit_exceeded` + `Retry-After` em segundos **arredondado para cima**.
- Chave = `ClientKey.of(request.getRemoteAddr())`: IPv4 como está; **IPv6 agrupado por /64**; `::ffff:a.b.c.d` = IPv4.
  **`X-Forwarded-For` só vale vindo de um proxy confiável** (`MONEYX_TRUSTED_PROXIES`, regex do Tomcat
  RemoteIpValve; `server.forward-headers-strategy=native`). **Padrão VAZIO = ninguém é confiável.** Cuidado: o padrão
  do PRÓPRIO Tomcat confia em toda rede privada (no Docker aceitaria XFF forjado vindo do host). Com proxy confiável,
  `X-Forwarded-Proto: https` também liga o HSTS. Teste: `TrustedProxyIntegrationTest` (Tomcat real, os dois modos).
- **Onde ficam os baldes (2026-11-25):** `moneyx.rate-limit.store` = `memory` (padrão, `TokenBucketRateLimiter`) ou
  `redis` (`RedisTokenBucketRateLimiter`, o Compose usa). Interface `RateLimiter`. No Redis a conta é um script Lua
  ATÔMICO (duas instâncias nunca gastam a mesma ficha), crédito em **microssegundos** inteiros (os números do Lua são
  double, exatos até 2^53: µs desde 1970 cabem, ns não), `PEXPIRE` quando o balde estaria cheio (memória limitada).
  **Redis fora do ar = fail-open** (permite e avisa no log; o limite não derruba o cadastro). Instante do Clock da app
  (relógios sincronizados por NTP com várias instâncias). Health do Redis desligado (não essencial).
  Teste: `RedisTokenBucketRateLimiterTest` (Redis real via Testcontainers; 2 "instâncias" x 25 threads = 5).
- Crédito do balde em **nanossegundos inteiros (`long`)**, nunca `double`: com ponto flutuante, frações acumuladas
  atrasavam a ficha e o `Retry-After` mentia (bug encontrado pelo teste de taxa sustentada). Mesmo princípio do dinheiro em centavos.
- `ConcurrentHashMap.compute` (atômico por chave): 50 tentativas simultâneas → exatamente 5 aceitas.
- Memória: teto de 100 mil chaves; ao atingir, remove baldes cheios (equivalentes a inexistentes); se continuar cheio,
  **nega novas chaves (fail-closed)** e loga alerta.
- Em memória = **uma instância**. Com várias, o limite se multiplicaria: mover o estado para Redis.

### Convenções da API (padrão de gateways: Stripe, Mercado Pago)

- Listas são devolvidas em envelope `{"data": [...]}`, nunca como array solto (permite paginação futura).
- Listas paginadas usam cursor: `?limit=&startingAfter=<id>` e resposta `{"data": [...], "hasMore": bool}`.
- Erros de regra de negócio incluem a propriedade `code` (snake_case, estável, para máquinas).
  Atuais: `api_key_limit_reached`, `last_active_api_key`, `invalid_idempotency_key`, `idempotency_key_reused`,
  `payment_not_pending`, `payment_expired`, `concurrent_update`, `invalid_starting_after`,
  `webhook_endpoint_limit_reached`, `invalid_webhook_url`, `webhook_endpoint_deleted`, `webhook_endpoint_unavailable`,
  `delivery_not_failed`, `rate_limit_exceeded`, `volume_limit_exceeded`, `payment_provider_unavailable` (503),
  `payment_provider_error` (502), `sandbox_payment_not_supported`. O `detail` é para humanos e pode mudar.
- Ações sobre um recurso que mudam seu estado usam `POST /recurso/{id}/<acao>` (ex.: `/cancel`) e são idempotentes
  quando o estado-alvo já foi atingido.
- `DELETE` é idempotente: repetir a operação já concluída responde `204`.
- Recurso de outro comerciante → `404` com a mesma mensagem de recurso inexistente.
  Buscar sempre por `id` E `merchantId` na mesma consulta (ex.: `findByIdAndMerchantId`).
- Rota inexistente → 404 com mensagem genérica ("The requested resource was not found").
- Regras que dependem de contagem ("no máximo N", "não remover o último") usam lock pessimista
  (`SELECT ... FOR UPDATE`) sobre as linhas do comerciante, ANTES de contar ou ler o estado.
  Provado por `ApiKeyConcurrencyIntegrationTest` (falha sem o lock).

### API Keys: endpoints e regras

- `POST /v1/api-keys` → 201, segredo apenas nesta resposta, `Cache-Control: no-store`.
- `GET /v1/api-keys` → `{"data": [{id, prefix, mode, active, createdAt, revokedAt}]}`, sem segredo nem hash.
- **Chaves `mx_live_` (2026-11-25):** `POST /v1/api-keys` com corpo opcional `{"mode": "live"}` (sem corpo = test).
  Exige o **e-mail verificado** (senão 403 `email_not_verified`). O modo vem do próprio prefixo (`ApiKey.isLive`), sem
  coluna nova; o principal ganhou `live` (`AuthenticatedMerchant`). Chave live em `/v1/sandbox/**` → 403
  `sandbox_requires_test_key`. Limitação: fora isso, chave live e test ainda fazem o mesmo (não há separação de
  dados test/live como no Stripe nem KYC de documentos).
- **Verificação de e-mail (V16):** no cadastro, um token (32 bytes, guardado só o SHA-256, uso único, 24 h) é criado
  na mesma transação e o e-mail com o link sai DEPOIS do commit (`@TransactionalEventListener AFTER_COMMIT`; falha de
  envio só loga, sem o link). `GET /v1/merchants/verify-email?token=` é público, `no-store`, seguro de repetir
  (antivírus que abre o link antes da pessoa); inválido/substituído → 400 `invalid_verification_token`, vencido → 400
  `verification_token_expired`. `POST /v1/merchants/me/email-verification` reenvia (invalida os anteriores; 1 por
  minuto → 429 `verification_email_rate_limited` + Retry-After; já verificado → 409 `email_already_verified`).
  SMTP em dev = **Mailpit** no Compose (http://localhost:8025). `moneyx.mail.enabled=false` nos testes; health do
  e-mail desligado. Link montado com `MONEYX_PUBLIC_BASE_URL`. FK dos tokens com `ON DELETE CASCADE`.
- `DELETE /v1/api-keys/{id}` → 204 (idempotente); 404 se não existir ou for de outro comerciante.
- Máximo de **5** chaves ativas por comerciante (`ApiKeyService.MAX_ACTIVE_KEYS_PER_MERCHANT`) → 409 `api_key_limit_reached`.
- A última chave ativa não pode ser revogada → 409 `last_active_api_key`.
- Revogar a própria chave usada na requisição é permitido (se não for a última); ela deixa de valer imediatamente.

### JSON

- `spring.jackson.deserialization.accept-float-as-int=false`: sem isso o Jackson TRUNCA decimais em campos
  inteiros (`"amount": 4990.5` virava 4990). Nunca remover; coberto por `PaymentControllerTest`.

### Erros da API

- Todas as respostas de erro seguem o padrão Problem Details (RFC 9457), `Content-Type: application/problem+json`.
- `shared/api/GlobalExceptionHandler` trata erros genéricos do MVC (validação, JSON malformado, tipo de parâmetro inválido).
  Erros de validação incluem a propriedade `errors` com a lista `{field, message}`.
- Cada módulo traduz suas próprias exceções de negócio em `<modulo>/api/<Modulo>ExceptionHandler`.
- Serviços lançam exceções de domínio e não conhecem HTTP.
- Todas as mensagens da API são em inglês. O idioma é fixado por `spring.web.locale=en` e
  `spring.web.locale-resolver=fixed`, independente do idioma do servidor ou do `Accept-Language`.

### Migrations

- Ficam em `src/main/resources/db/migration`, no formato `V<n>__<descricao>.sql`.
- Uma migration já aplicada nunca deve ser editada; mudanças geram uma nova versão.
- Regras críticas (unicidade, valores permitidos) também são garantidas por constraints no banco.
- Aplicadas: V1 a V17. A próxima é `V18__...`.

### Testes

| Tipo | Ferramenta | Exemplo |
|---|---|---|
| Unitário de domínio | JUnit 5 + AssertJ | `MerchantTest` |
| Unitário de serviço | Mockito | `MerchantServiceTest` |
| Integração de persistência | `@DataJpaTest` + PostgreSQL real | `MerchantRepositoryTest` |
| Camada web | `@WebMvcTest` + MockMvc + `@Import(SecurityConfig.class)` | `MerchantControllerTest` |
| Segurança | `@WebMvcTest` + mocks dos serviços do filtro | `ApiKeyAuthenticationTest` |
| Ponta a ponta | `@SpringBootTest` + `@AutoConfigureMockMvc`, tudo real (sem `@Transactional`, limpeza explícita) | `SecurityIsolationIntegrationTest` |
| Concorrência | `@SpringBootTest` + threads com `CountDownLatch` | `ApiKeyConcurrencyIntegrationTest` |

Limpeza em testes não transacionais, na ordem das FKs: `webhook_deliveries` → `webhook_endpoint_history` → `webhook_endpoints` → `events` →
`payment_idempotency_keys` → `payments` → `api_keys` → `merchants`.

Todo recurso novo pertencente a um comerciante deve ganhar cenários no `SecurityIsolationIntegrationTest`
(A não lê/altera recurso de B → 404 indistinguível de inexistente).

Testes de controller autenticados usam `TestAuthentication.activeMerchant(merchantId)` (em `src/test/.../security`).

Testes de integração que precisam de relógio controlado usam `@Import(TestClockConfig.class)` (em `src/test/.../support`)
e posicionam o relógio no `@BeforeEach`. **Não criar `@TestConfiguration` aninhada por classe**: cada configuração
diferente vira um contexto em cache com pool próprio (10 conexões) e a suíte já esbarrou no limite de 100 conexões do
PostgreSQL. `src/test/resources/spring.properties` limita o cache a 5 contextos (`spring.test.context.cache.maxSize`).
Pico medido depois da mudança: 41 conexões.

Executar todos os testes (requer apenas o **Docker rodando**; não usa o banco de desenvolvimento nem `POSTGRES_PASSWORD`):

```powershell
.\mvnw.cmd test
```

Mesmo comando do CI (testes + empacotamento): `.\mvnw.cmd -B -ntp verify`.

Nunca criar `src/test/resources/application.properties` (esconderia o principal); propriedades de teste ficam em
`src/test/resources/config/application.properties`, que é carregado além do principal.

**Banco dos testes: Testcontainers** (desde 2026-11-20). `src/test/resources/config/application.properties` define
`spring.datasource.url=jdbc:tc:postgresql:17-alpine:///moneyx?TC_DAEMON=true`: o driver do Testcontainers sobe um
PostgreSQL 17 descartável na primeira conexão, o Flyway aplica todas as migrations nele, e o mesmo container é usado
pela execução inteira (TC_DAEMON). O container `ryuk` o remove segundos depois que a JVM termina. Comprovado: suíte
completa com **0 conexões** no banco de desenvolvimento. Nenhuma classe de teste precisa saber disso (não usar
`@ServiceConnection` por classe: criaria contextos novos). Os testes continuam limpando o que criam (isolamento entre
classes dentro da mesma execução), e o relógio em 2020 dos testes de jobs continua valendo pelo mesmo motivo.

**CI:** `.github/workflows/ci.yml` roda `./mvnw -B -ntp verify` em push na `main` e em pull requests (ubuntu-latest,
Temurin 21, cache do Maven, `permissions: contents: read`, cancela execuções antigas do mesmo branch, publica
`target/surefire-reports` só em caso de falha). Sem segredos: os runners do GitHub já têm Docker. O `mvnw` precisa
estar com permissão de execução no git (`git update-index --chmod=+x mvnw`).

**Cadeia de suprimentos do CI (definido em 2026-11-21, Fase 6 Etapa 5c):**
- Toda action fixada pelo **hash do commit** + comentário com a versão (`actions/checkout@<sha> # v7.0.1`,
  `setup-java` v6.2.0, `upload-artifact` v7.0.2; todas `node24`). Hash conferido como **idêntico** à tag no repositório
  oficial (`/compare/<tag>...<sha>`), nunca copiado de fork. `checkout` com `persist-credentials: false`.
- Não adotar versão recém-publicada: preferir a mais nova com **~2 semanas** (`setup-java` v6.3.0 tinha 7 dias e foi
  preterida). O Dependabot aplica a mesma regra com `cooldown`.
- Job paralelo `image`: `docker build` (prova que o Dockerfile constrói; nada é publicado) + **Trivy 0.76.2 pela imagem
  oficial fixada por digest** (não pela action de terceiros), `--severity HIGH,CRITICAL --ignore-unfixed --exit-code 3
  --table-mode detailed`, resultado no resumo da execução ("Nenhuma encontrada." quando vazio). **BLOQUEANTE desde
  2026-11-25:** HIGH/CRITICAL com correção disponível reprova o build; falha do PRÓPRIO Trivy (código diferente de 3,
  ex.: base de vulnerabilidades indisponível) só avisa. Validado extraindo o script do `ci.yml` nos 3 caminhos (limpa →
  passa; MEDIUM forçado → reprova; imagem inexistente → avisa e passa). O MEDIUM do `zlib` não reprova (só HIGH/CRITICAL).
- `.github/dependabot.yml`: maven, github-actions e docker; semanal (segunda 06:00, America/Sao_Paulo); minor/patch
  agrupados por ecossistema, major em PR separado; `cooldown` 7 dias (major do Maven 14). **Versões maiores do
  `eclipse-temurin` são ignoradas** (`ignore`): o Dependabot propôs Java 21 → 24 (não-LTS, PR #2 fechado); a versão do
  Java é decisão do projeto. Lição: CI verde num PR do Dependabot não prova tudo (o CI testa com o Java do
  `setup-java`, não com o da imagem). Os PRs do Dependabot são a
  exceção ao "commit direto na main": revisar, esperar o CI verde e fazer merge. A imagem do Trivy fica num `docker run`
  e **não** é vista pelo Dependabot: atualizar à mão. O `postgres:17-alpine` do `compose.yaml` (só desenvolvimento)
  segue por tag.

### Rotação da chave de cifragem dos segredos de webhook (definido em 2026-11-24, Fase 6 Etapa 6d)

- **Chaveiro de duas posições:** `MONEYX_WEBHOOK_ENCRYPTION_KEY` (ATIVA, obrigatória: cifra e decifra) e
  `MONEYX_WEBHOOK_ENCRYPTION_KEY_PREVIOUS` (ANTERIOR, opcional: só decifra). Propriedades
  `moneyx.webhooks.encryption-key` / `.previous-encryption-key`. Anterior igual à ativa ou mal formada → a app não sobe.
- **Formato gravado:** `k1.<id da chave>:Base64(nonce || cifra || tag)`. `k1.` = versão do formato; o id são 8 hex do
  SHA-256 da chave com rótulo fixo (não revela a chave, não precisa de numeração manual, pode ir para log). Valor sem
  prefixo = formato antigo: tenta a ativa e depois a anterior (o GCM só aceita a chave certa). Id desconhecido →
  `Decryption failed: unknown encryption key <id>` (a anterior saiu do .env cedo demais). Trocar o id do prefixo não
  engana (o GCM recusa). AAD continua sendo o id do endpoint. Coluna `VARCHAR(255)`: o valor tem ~115 caracteres.
- **Recifragem:** `WebhookSecretRotationService.reencryptAll` (job no `WebhookDeliveryJob`, a cada 60 min, 1º após
  1 min): lotes de 100 com **cursor por id** + `FOR UPDATE SKIP LOCKED` sobre os que NÃO começam com o prefixo ativo
  (inclusive endpoints removidos). Segredo que não decifra é contado (`failed`), logado só com o id do endpoint e pulado
  (o cursor passa por ele). Log `webhook_secret.reencryption activeKeyId= reencrypted= failed= remaining=`.
  `WebhookEndpoint.replaceSecretCiphertext` é o único caminho que altera a coluna; o segredo em si nunca muda (a loja
  não percebe).
- **Como rotacionar:** (1) mover o valor atual para `..._PREVIOUS`; (2) gerar a nova ativa e reiniciar; (3) esperar o
  log com `remaining=0`; (4) apagar a `..._PREVIOUS` e reiniciar. A 1ª subida desta versão já move os segredos antigos
  (sem prefixo) para o formato novo, sem trocar de chave.
- Testes: `SecretCipherTest` (18) e `WebhookSecretRotationIntegrationTest` (6; o contexto dos testes tem uma chave
  anterior fixa, ou seja, está "no meio de uma rotação").

## 7. Infraestrutura PostgreSQL

O banco de dados é executado em um container Docker exclusivo do MoneyX.

### Configuração atual

- Container: `moneyx-postgres`
- Imagem: `postgres:17-alpine`
- Banco: `moneyx`
- Usuário: `moneyx_user`
- Porta: `5432`, publicada **só em `127.0.0.1`** (antes era `0.0.0.0`: aceitava conexões de outras máquinas da rede)
- Volume: `moneyx_moneyx_postgres_data`
- Arquivo de configuração: `compose.yaml`
- Credenciais: `.env` (o Compose o lê sozinho; `${VAR:?mensagem}` faz o Compose recusar subir se faltar a variável)

### Aplicação em container (definido em 2026-11-21, Fase 6 Etapa 5a)

- `Dockerfile` **multi-stage**: estágio `build` (`eclipse-temurin:21-jdk-alpine` + `mvnw package -DskipTests`; os
  testes rodam no CI, pois precisam de Docker) e estágio `runtime` (`eclipse-temurin:21-jre-alpine`). Imagens base
  **fixadas por digest** `@sha256:...` (o Dependabot propõe a atualização).
- Dependências baixadas numa camada separada (`dependency:go-offline` só com `pom.xml`): mudar código não rebaixa tudo.
- `.jar` extraído em camadas (`-Djarmode=tools extract --layers`), da que menos muda para a que mais muda; arquivos
  pertencem ao root (a app não altera o próprio código). `ENTRYPOINT java -jar app.jar`.
- **Usuário não-root** `moneyx`, UID/GID numéricos `10001` (`USER 10001:10001`, compatível com `runAsNonRoot`).
- `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75` (o padrão da JVM é 25% da memória do container).
- `HEALTHCHECK` com `wget` em `http://127.0.0.1:8081/actuator/health` **de dentro** do container (`start-period` 60 s).
- Nenhum segredo na imagem: chegam por variável de ambiente. `.dockerignore` exclui `.env`, `.git`, `target`, `.cursor`.
- Serviço `app` no Compose: `depends_on: postgres: condition: service_healthy`; banco pelo nome do serviço
  (`SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/...`, variável de ambiente sobrepõe o properties); só a 8080
  publicada (em `127.0.0.1`); endurecimento: `read_only: true` + `tmpfs: /tmp` (Tomcat), `cap_drop: ALL`,
  `no-new-privileges`. Verificado: processo UID 10001, `CapEff` zerado, escrita fora de `/tmp` recusada, sem `javac`
  nem código-fonte na imagem, nenhum segredo em `docker history`.

### Segurança das credenciais

O arquivo `.env` contém a senha gerada aleatoriamente.

O `.gitignore` deve ignorar:

```gitignore
.env
.env.*
!.env.example
```

Nunca imprimir ou compartilhar o conteúdo do `.env`.

### Inicialização

```powershell
docker compose up -d --build   # banco + aplicação (recompila a imagem se o código mudou)
docker compose up -d postgres  # só o banco (para rodar a app pelo IntelliJ / mvnw)
docker compose logs -f app     # logs da aplicação
docker compose stop app        # libera a 8080 antes de rodar a app fora do Docker
```

### Verificar container

```powershell
docker compose ps
```

### Verificar saúde

```powershell
docker inspect --format='{{.State.Health.Status}}' moneyx-postgres
```

O resultado esperado é `healthy`.

### Consultar banco

```powershell
docker compose exec postgres psql -U moneyx_user -d moneyx
```

Não remover o volume persistente sem autorização explícita. Ele contém os dados do banco.

## 8. Configuração da aplicação

Arquivo:

```text
src/main/resources/application.properties
```

Configuração atual:

```properties
spring.application.name=moneyx

spring.datasource.url=jdbc:postgresql://localhost:5432/moneyx
spring.datasource.username=moneyx_user
spring.datasource.password=${POSTGRES_PASSWORD}

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
```

As variáveis `POSTGRES_PASSWORD` e `MONEYX_WEBHOOK_ENCRYPTION_KEY` são disponibilizadas na sessão do PowerShell a
partir do `.env` (sem imprimir os valores):

```powershell
foreach ($n in "POSTGRES_PASSWORD", "MONEYX_WEBHOOK_ENCRYPTION_KEY") {
    Set-Item -Path "Env:$n" -Value ((Get-Content .env | Where-Object { $_ -match "^$n=" }) -split '=', 2)[1]
}
```

As duas precisam estar definidas na sessão em que a aplicação for iniciada (durante uma rotação, também
`MONEYX_WEBHOOK_ENCRYPTION_KEY_PREVIOUS`, opcional). **Sem `MONEYX_WEBHOOK_ENCRYPTION_KEY` a
aplicação não sobe** (de propósito).

**Sem `POSTGRES_PASSWORD` a aplicação também não sobe** (desde 2026-11-21): o Spring Boot, sozinho, usaria o texto
literal `${POSTGRES_PASSWORD}` como senha e o erro seria um enganoso `password authentication failed`.
`shared/config/RequiredSettingsCheck` (um `EnvironmentPostProcessor`, registrado em `META-INF/spring.factories`) roda
antes de qualquer bean e recusa valor ausente, em branco ou placeholder não resolvido (nos dois modos do resolvedor).
`MissingRequiredSettingsFailureAnalyzer` mostra o quadro "APPLICATION FAILED TO START" com a variável que falta e a
ação. **Nunca** inclui o valor na mensagem. Nova configuração obrigatória: adicionar em `REQUIRED_SETTINGS`
(propriedade → variável de ambiente). Testes: `RequiredSettingsCheckTest` (7) e `RequiredSettingsStartupTest` (sobe a
app real sem a senha: falha sem Hikari/Flyway). Os testes usam uma chave fixa de teste (`src/test/resources/config/application.properties`)
e só precisam de `POSTGRES_PASSWORD`.

**Provedor Pix (Fase 7), todas opcionais:**

| Variável | Uso |
|---|---|
| `MONEYX_PIX_PROVIDER` | `sandbox` (padrão, BR Code local) ou `mercadopago` |
| `MERCADOPAGO_ACCESS_TOKEN` | credencial do MP (no `.env`); obrigatória só com `mercadopago` (sem ela a app não sobe) |
| `MERCADOPAGO_WEBHOOK_SECRET` | assinatura secreta dos avisos do MP; vazia = `POST /v1/provider-webhooks/mercadopago` responde 404 |
| `MONEYX_PIX_MERCADOPAGO_TEST_PAYER_FIRST_NAME` | SÓ no sandbox: `APRO` faz o MP aprovar sozinho |
| `MONEYX_PIX_MERCADOPAGO_BASE_URL` | raiz da API (testes: endereço inválido simula o MP fora do ar) |

**Infraestrutura (2026-11-25), todas opcionais (o Compose já define as do Redis e do e-mail):**

| Variável | Uso |
|---|---|
| `MONEYX_TRUSTED_PROXIES` | regex dos IPs de proxy confiáveis (X-Forwarded-For/Proto); vazio = nenhum |
| `MONEYX_RATE_LIMIT_STORE` | `memory` (padrão) ou `redis`; com `MONEYX_REDIS_HOST`/`MONEYX_REDIS_PORT` |
| `MONEYX_MAIL_ENABLED` / `MONEYX_MAIL_HOST` / `MONEYX_MAIL_PORT` / `MONEYX_MAIL_FROM` | SMTP do e-mail de verificação (padrão `localhost:1025` = Mailpit publicado pelo Compose) |
| `MONEYX_PUBLIC_BASE_URL` | endereço público da API no link do e-mail (padrão `http://localhost:8080`) |

Para usar o MP sem tocar no `.env` (Git Bash): `MONEYX_PIX_PROVIDER=mercadopago docker compose up -d --build app`.
Para voltar ao normal: `docker compose up -d app`.

### Executar aplicação

Forma recomendada (sem carregar variáveis na sessão): `docker compose up -d --build`.
Fora do Docker (para depurar no IntelliJ), com a app do Compose parada:

```powershell
.\mvnw.cmd spring-boot:run
```

### Endereços locais

- Aplicação: http://localhost:8080
- Healthcheck: http://127.0.0.1:8081/actuator/health (porta de GERENCIAMENTO, só local; na 8080 é 404). Com a app no
  Compose, a 8081 existe só DENTRO do container: `docker exec moneyx-app wget -qO- http://127.0.0.1:8081/actuator/health`
- Métricas: http://127.0.0.1:8081/actuator/prometheus e /actuator/metrics
- PostgreSQL: localhost:5432

### Healthcheck HTTP

```powershell
Invoke-RestMethod http://127.0.0.1:8081/actuator/health
```

Resultado esperado:

```json
{
  "status": "UP"
}
```

## 9. Conceitos fundamentais do domínio financeiro

As implementações devem considerar desde o início os seguintes princípios:

### Dinheiro

- Não utilizar `float` ou `double` para representar valores monetários.
- Utilizar valores inteiros em centavos ou uma representação decimal apropriada.
- Validar valores positivos e limites.
- Definir claramente moeda e precisão.

### Idempotência

Uma mesma solicitação de pagamento não deve gerar cobranças duplicadas quando for repetida com a mesma chave de idempotência.

### Estados de pagamento

As transições de estado devem ser explícitas, controladas e validadas.

Não permitir mudanças arbitrárias de estado.

### Segurança multi-tenant

Cada estabelecimento deve acessar exclusivamente os próprios recursos.

A separação entre comerciantes deve ser considerada em todas as consultas e operações relevantes.

### API Keys

- Não armazenar chaves em texto puro.
- Armazenar hashes.
- Nunca retornar segredos completos depois da criação.
- Permitir revogação e controle de acesso.

### Webhooks

- Validar autenticidade.
- Utilizar assinatura HMAC quando aplicável.
- Implementar tentativas de entrega.
- Registrar resultados.
- Prevenir duplicidade de processamento.

### Confiabilidade

- Registrar eventos relevantes.
- Implementar transações adequadamente.
- Planejar consistência entre banco e mensageria.
- Considerar padrão Outbox quando a arquitetura exigir.
- Nunca confiar exclusivamente no status informado pelo frontend.

### Cartões

Não armazenar dados sensíveis de cartões. Qualquer integração futura deve considerar os requisitos de PCI DSS e a utilização de provedores especializados.

## 10. Escopo inicial — primeira versão

A primeira versão será uma infraestrutura de pagamentos simulada.

Não haverá movimentação de dinheiro real nessa fase.

### Funcionalidades planejadas

1. Cadastro de comerciantes (Merchants).
2. Autenticação e gerenciamento de API Keys.
3. Criação de pagamentos.
4. Simulação de pagamentos Pix.
5. Consulta de pagamentos.
6. Cancelamento de pagamentos, respeitando regras de estado.
7. Idempotência.
8. Simulação de processamento.
9. Webhooks.
10. Retentativas de entrega.
11. Histórico de eventos.
12. Documentação da API.
13. Testes automatizados.

### Fora do escopo inicial

- Custódia de dinheiro.
- Carteiras próprias.
- Saldos de vendedores.
- Emissão de cartões.
- Crédito.
- Antecipação.
- Split avançado.
- Antifraude avançado.
- Liquidação própria.
- Múltiplos provedores simultâneos.

Essas funcionalidades só devem ser consideradas em versões futuras, mediante análise.

## 11. Roadmap

### Fase 1 — Fundação

- [x] Instalar Java 21.
- [x] Criar repositório GitHub.
- [x] Criar projeto Spring Boot.
- [x] Configurar Maven Wrapper.
- [x] Configurar Docker.
- [x] Criar PostgreSQL.
- [x] Configurar credenciais locais.
- [x] Conectar Spring Boot ao PostgreSQL.
- [x] Validar inicialização da aplicação.
- [x] Registrar commit da infraestrutura.
- [x] Documentar a skill do projeto.

### Fase 2 — Domínio e comerciantes

- [x] Criar entidade Merchant.
- [x] Adotar Flyway e criar migration `V1__create_merchants_table`.
- [x] Criar repositório.
- [x] Criar serviço.
- [x] Organizar o módulo em camadas (`domain`, `repository`, `service`, `exception`).
- [x] Criar DTOs e validações.
- [x] Implementar endpoints (`POST /v1/merchants`, `GET /v1/merchants/{id}`).
- [x] Implementar tratamento padronizado de erros (Problem Details).
- [x] Criar testes (domínio, serviço, repositório e camada web).

### Fase 3 — Segurança

#### Decisões arquiteturais (definidas em 2026-11-18)

**Produto**

- **Cadastro público (auto-cadastro):** `POST /v1/merchants` não exige autenticação.
- **Primeira chave:** retornada na resposta do cadastro, uma única vez.
- **Múltiplas chaves ativas** por comerciante, revogáveis individualmente (rotação sem downtime).
- **Consulta própria via `GET /v1/merchants/me`:** o comerciante é identificado pela chave, nunca pela URL.
  `GET /v1/merchants/{id}` é removido (não há administrador no modelo atual).

**Técnicas**

- Formato da chave: `mx_test_` + 32 bytes aleatórios (`SecureRandom`, Base64 URL-safe, sem padding).
- Envio: header `Authorization: Bearer <chave>`.
- Armazenamento: hash **SHA-256** (não BCrypt: chaves aleatórias de 256 bits têm alta entropia e o hash
  determinístico permite busca indexada). A chave completa nunca é armazenada nem retornada novamente.
- Um prefixo curto (ex.: `mx_test_9fK2`) é armazenado em texto puro para identificação da chave.
- Spring Security stateless: filtro próprio de API Key, sem sessão, CSRF desabilitado (não há cookies).
- Chave inexistente ou revogada → **401**. Comerciante `SUSPENDED`/`CLOSED` → **403**.
- Recurso pertencente a outro comerciante → **404** (não revelar existência).
- O comerciante autenticado vem sempre do contexto de segurança, nunca de parâmetros da requisição.

**Riscos conhecidos e aceitos**

- Enumeração: o `409` do cadastro público revela se documento/e-mail já existem.
- Abuso: criação em massa de contas.
- Mitigação: cadastro público gera apenas chaves de teste (`mx_test_`), sem movimentação real.
  Rate limiting no cadastro implementado na Fase 6 (5 por IP + 1 a cada 2 min). Chaves `mx_live_` exigem e-mail verificado desde 2026-11-25.

#### Etapas

- [x] Entidade `ApiKey`, migration `V2__create_api_keys_table` e geração/hash de chaves.
- [x] Cadastro retorna a primeira chave.
- [x] Spring Security com filtro de API Key.
- [x] `GET /v1/merchants/me` e remoção de `GET /v1/merchants/{id}`.
- [x] Endpoints de gerenciamento de chaves (criar, listar, revogar).
- [x] Testes de autenticação, autorização e isolamento entre comerciantes.

**Fase 3 concluída em 2026-11-19.**

### Fase 4 — Pagamentos

#### Decisões (definidas em 2026-11-19)

- Valor em **centavos (`long`)**; limites **por operação**: mín. R$ 1,00 (100), máx. R$ 100.000,00 (10.000.000).
  Limite acumulado por período: adicionado na Fase 6 (Etapa 6c), ver "Limite acumulado de volume".
- Estados: `PENDING` → `PAID` | `CANCELED` | `EXPIRED` (todos finais). Reembolso fora da v1. Desde a Fase 7 (7c)
  existe `CREATING` antes de `PENDING` (ver "Criação em três fases"). Transições vindas do PSP (7d): `PENDING` → `PAID`
  (`confirmPaidByProvider`) ou `EXPIRED` (`expireByProvider`) e `CREATING` → `PAID` (`confirmPaidBeforeCharge`).
- Pagar após o prazo é proibido mesmo se o status ainda for `PENDING` (o job de expiração pode atrasar). **Exceção
  (7d):** confirmação do PSP. Se o PSP diz que recebeu o Pix, o pagamento vira PAID mesmo depois do prazo do MoneyX.
- Expiração do Pix: padrão 30 min, permitido de 5 min a 24 h.
- Campos opcionais `description` (255) e `externalReference` (64, conciliação com o pedido do comerciante).
- Moeda: apenas `BRL` (enum `PaymentCurrency`), campo modelado desde já.
- Limites de negócio ficam no domínio (podem mudar sem migration); o banco garante só invariantes
  (amount > 0, status/moeda válidos, `paid_at` ⇔ PAID, `canceled_at` ⇔ CANCELED).
- Métodos de domínio recebem `Instant now` (testabilidade); os serviços usam o bean `Clock` (`shared/config/ClockConfig`, UTC).
  Instantes são truncados para microssegundos (precisão do PostgreSQL) antes de usar.

#### Idempotência de `POST /v1/payments` (definida em 2026-11-19)

- Header `Idempotency-Key` **obrigatório** (ausente → 400); 1 a 255 caracteres ASCII visíveis (senão 400 `invalid_idempotency_key`).
- Escopo por comerciante: UNIQUE (merchant_id, idempotency_key) em `payment_idempotency_keys` (V4). Validade de **24 h**.
- Mesma chave + mesmo corpo → 201 com o pagamento original e `Idempotent-Replayed: true` (sem criar outro).
- Mesma chave + corpo diferente → **422** `idempotency_key_reused`. Corpo comparado por SHA-256 dos campos
  codificados com prefixo de tamanho (`CreatePaymentCommand.fingerprint`).
- Implementação: um único `INSERT ... ON CONFLICT DO UPDATE ... WHERE expires_at <= now` reserva a chave
  na MESMA transação do pagamento. Requisições simultâneas com a mesma chave ESPERAM a primeira (lock do índice
  UNIQUE) e recebem o replay; por isso não há estado "em processamento" nem 409.
  FK `payment_id` é `DEFERRABLE INITIALLY DEFERRED` (a chave é gravada antes do pagamento).
- Só sucessos consomem a chave: erro de validação ou falha fazem rollback e o cliente pode repetir com a mesma chave.
- Replay devolve o estado ATUAL do pagamento (não a resposta original byte a byte, como o Stripe).
- **Resolvido na Fase 7 (7c-1):** com o PSP chamado fora da transação, o pagamento nasce `CREATING` e uma repetição
  com a mesma chave retoma a criação (503 enquanto o PSP estiver fora). A rotina de recuperação conclui sozinha (ver
  "Criação em três fases"). Não foi preciso devolver 409 "em processamento".
- Comprovado por mutação: sem o `WHERE` de expiração ou sem a checagem do fingerprint, os testes falham.

#### Etapas

- [x] Definir modelo de Payment (`V3__create_payments_table`).
- [x] Definir estados.
- [x] Implementar criação de pagamentos (`POST /v1/payments`).
- [x] Implementar idempotência.
- [x] Simular Pix.

#### Pix (definido em 2026-11-19)

- Cada pagamento nasce com uma cobrança Pix: `pix.txid` (25 caracteres A-Z0-9, `SecureRandom`, UNIQUE) e
  `pix.copyPaste` (BR Code **estático** com valor e txid, padrão EMV do Manual de Padrões para Iniciação do Pix).
  Campos: 00, 26 (00 `br.gov.bcb.pix` + 01 chave), 52 `0000`, 53 `986`, 54 valor, 58 `BR`, 59 nome, 60 cidade,
  62.05 txid, 63 CRC16-CCITT-FALSE (poly 0x1021, init 0xFFFF). Gerador puro: `payment/domain/PixBrCode`.
- `String.format` sempre com `Locale.ROOT` (dígitos ASCII independentemente do idioma do servidor).
- Validado contra o exemplo do manual do BCB (CRC `1D3D`), o check value do CRC (`29B1`) e vetores de
  implementação independente (Python).
- **Sandbox:** recebedor fictício (`SandboxPixProvider.PIX_KEY` etc.; chave do exemplo do manual, não registrada no
  DICT, portanto não pagável de verdade). Na Fase 7 o BR Code (dinâmico) vem do PSP (Mercado Pago).
- **Criação em três fases (2026-11-24, Fase 7 Etapa 7c-1, V13):** a cobrança pode vir de um PSP chamado pela rede,
  e nenhuma transação pode ficar aberta esperando a rede (travaria o comerciante). `PaymentService.create` (SEM
  `@Transactional`; recusa rodar dentro de transação de quem chama; `TransactionTemplate` por fase):
  1. `begin` (tx): trava o comerciante, reserva a chave, limite de volume, grava `CREATING` (sem `pix`, com
     `pix_provider`). Repetição com a mesma chave devolve o original.
  2. `charge` (SEM tx): `PixProvider.createCharge` com o MESMO pedido sempre (`PixChargeRequest` com a DURAÇÃO pedida,
     `requestedExpiration()`), condição para o PSP devolver a mesma cobrança.
  3. `complete` (tx): trava o pagamento; se ainda `CREATING`, `attachPixCharge` (→ `PENDING`, ids do PSP, e o
     vencimento passa a ser o do PSP, se ele informar, desde que a no máximo 10 min do pedido) + `payment.created`.
     Se outro já concluiu, devolve como está (por isso pode rodar em paralelo: repetição × recuperação → 1 evento).
  - `PixProviderException(retryable)`: temporária → fica `CREATING`, 503 `payment_provider_unavailable` +
    `Retry-After` (do PSP, ou 5 s; arredondado para cima); definitiva → `discard` (apaga pagamento e chave) e 502
    `payment_provider_error`. Exceção inesperada → propaga (500) e fica `CREATING` para a recuperação.
  - Repetir uma criação `CREATING` retoma as fases 2 e 3 (não há "409 em andamento": o PSP é idempotente).
  - `CREATING` com prazo vencido → `expireBeforeCharge` (EXPIRED, sem `pix`, SEM evento: ninguém recebeu BR Code) e
    o provedor não é chamado.
  - **Recuperação:** `PaymentMaintenanceJobs.resumeStuckCreations` a cada 30 s → `resumeStuckCreations(30 s, 20)`:
    `CREATING` criados há mais de 30 s (índice parcial da V13), sem trava na busca (a fase 3 trava).
  - `CREATING` conta no limite de volume. API: status `CREATING` visível, `pix` nulo; `pix.txid` é o do PSP (até 35).
  - V13: status `CREATING`; `pix_txid` `VARCHAR(35)` `^[A-Za-z0-9]{1,35}$` e opcional, junto com `pix_copy_paste`
    (CHECKs: os dois juntos; `CREATING` sem; `PENDING/PAID/CANCELED` com; `EXPIRED` livre); `pix_provider`
    (`sandbox`|`mercadopago`, linhas antigas = sandbox), `provider_order_id` (UNIQUE com o provedor),
    `provider_payment_id`.
  - Testes: `PaymentIdempotencyIntegrationTest` (fluxo, recuperação, concorrência ×3), `PaymentTest` (domínio),
    `PaymentFixtures` (`creating`/`pending` para testes). **Após mudar assinaturas, rodar `mvnw clean`**: a compilação
    incremental chegou a dizer "nada para compilar" com os testes desatualizados.
- **Provedor Mercado Pago ligado (2026-11-24, Etapa 7c-2):** `MercadoPagoPixProvider` (`MONEYX_PIX_PROVIDER=mercadopago`;
  sem `MERCADOPAGO_ACCESS_TOKEN` a app não sobe). Chave `creationKeyFor(paymentId)`, `external_reference` = id do
  pagamento, descrição cortada em **150** (limite do MP; o MoneyX guarda a completa), txid do BR Code (ou o id da
  order), ids e vencimento do MP. Confere que a order tem o MESMO valor e a MESMA referência (senão falha definitiva).
  Order sem `qr_code` → temporária. **`idempotency_key_already_used` é tratado como TEMPORÁRIO, nunca descartado:**
  significa que JÁ existe uma order com a chave (o corpo mudou entre tentativas, p.ex. configuração do MP alterada);
  descartar deixaria uma order, talvez paga, sem dono (bug real encontrado no teste ponta a ponta).
  - **O MP compara o corpo da repetição BYTE A BYTE** (JSON com espaços → 409) e a repetição devolve o estado ATUAL
    da order (uma order já em `processing` não traz mais `qr_code`). Não mudar a configuração do MP (e-mail do
    pagador, `test-payer-first-name`, corte da descrição) com criações em andamento.
  - Variáveis do Compose: `MONEYX_PIX_PROVIDER`, `MONEYX_PIX_MERCADOPAGO_TEST_PAYER_FIRST_NAME` (só sandbox: `APRO`),
    `MONEYX_PIX_MERCADOPAGO_BASE_URL`. Placeholders explícitos no `application.properties` (o relaxed binding do Spring
    leria cada `_` como ponto).
  - `POST /v1/sandbox/payments/{id}/pay` só aceita cobranças do provedor `sandbox` (senão 409
    `sandbox_payment_not_supported`): marcar como pago localmente uma cobrança do MP diria "pago" sem dinheiro.
  - Testes: `MercadoPagoPixProviderTest` (servidor falso), `PixProviderSelectionTest` (contexto Spring pequeno: escolhe
    o provedor e o CRIA; pegou o "No default constructor found" que só a app real mostrou, por haver dois construtores).
- **Conciliação com o PSP (2026-11-25, Etapa 7d, V14).** Regra central: **quem diz se o Pix foi pago é o PSP**; o
  MoneyX nunca marca EXPIRED/CANCELED uma cobrança do PSP sem o PSP confirmar que a order foi encerrada.
  - Porta `PixProvider` ganhou `prepareCharge` (monta o corpo SEM rede), `fetchCharge(orderId)` e
    `cancelCharge(paymentId, orderId)`, que devolvem `ProviderChargeStatus` com estado neutro: `OPEN`, `IN_PROGRESS`,
    `PAID`, `CLOSED` (cancelada/recusada), `EXPIRED` (expirada PELO PSP) e `UNKNOWN` (ex.: estorno; nada é decidido).
    No MP: `processed`=PAID, `action_required`/`created`=OPEN, `processing`=IN_PROGRESS, `canceled`/`failed`=CLOSED,
    e EXPIRED quando `status_detail=expired` (o MP expira com `canceled/expired`).
  - **Achado no sandbox (2026-11-25):** order com prazo de **5 min** (`PT5M`) fica `canceled/expired` lá ~5 s depois
    de criada (a de 30 min e a de 10 min ficam abertas). EXPIRED do PSP vira EXPIRED no MoneyX mesmo ANTES do prazo
    local (`Payment.expireByProvider`), nunca CANCELED (o lojista não cancelou).
    Cancelar com 409 (já cancelada/paga/expirada) → consulta a order e devolve o estado real.
  - **Corpo gravado (V14 `provider_request`):** montado uma vez na criação e reenviado byte a byte em toda repetição
    (o MP compara o corpo). Resolve o `idempotency_key_already_used` depois de mudança de configuração.
  - **Rotina a cada 30 s, lote 20** (`reconcileProviderCharges`): PENDING com `provider_order_id` do provedor ATIVO,
    vencidos primeiro, depois por `provider_checked_at NULLS FIRST` (índice parcial V14). Consulta FORA de transação,
    decide DENTRO, com a linha trancada. No prazo: PAID → PAID + `payment.paid`; CLOSED → CANCELED (cancelada no painel
    do PSP); EXPIRED → EXPIRED. Vencido: **cancela no PSP** → CLOSED = EXPIRED; PSP recusou por estar paga = **PAID mesmo depois do prazo**
    (`Payment.confirmPaidByProvider` não confere o prazo; `markPaid` do sandbox continua conferindo). Erro temporário
    do PSP interrompe a rodada (nada muda); erro definitivo de uma order só grava a consulta (vai para o fim da fila).
  - **Valor/referência/order divergentes nunca viram PAID:** log ERROR `payment.provider_mismatch` + métrica
    `moneyx.payments.reconciliation.mismatches` (revisão manual).
  - A expiração local (`lockOverduePending`) passou a pegar só cobranças SEM `provider_order_id`.
  - **Cancelar (lojista):** cobrança do PSP → cancela PRIMEIRO no PSP (fora de transação), grava o resultado (commit) e
    só então responde: CLOSED → 200 CANCELED; PAID → vira PAID e responde 409 `payment_not_pending`; PSP fora do ar ou
    ainda processando → **503 `payment_provider_unavailable` + Retry-After**, nada muda. Vencido → 409
    `payment_expired` sem chamar o PSP (a rotina cancela lá).
  - **CREATING:** se a repetição traz a order sem BR Code, a exceção traz o estado (`PixProviderException.getExisting`):
    PAID → `confirmPaidBeforeCharge` (CREATING → PAID com os ids da order e `pix` nulo; a V14 permite PAID sem txid
    desde que haja `provider_order_id`). CREATING remoto vencido: repete a criação (devolve ou cria a order) e cancela
    no PSP antes de expirar. PSP fora do ar → continua CREATING (nunca expira às cegas).
  - Pagamento cujo provedor não é o ATIVO (configuração trocada): nada é decidido (503 no cancelar; rotina ignora).
- **Aviso (webhook) do Mercado Pago (2026-11-25, Etapa 7e):** `POST /v1/provider-webhooks/mercadopago?data.id=ORD...&type=order`
  (público na segurança, `@Hidden` no OpenAPI, só existe com `MONEYX_PIX_PROVIDER=mercadopago`). Segredo em
  `MERCADOPAGO_WEBHOOK_SECRET` (painel do MP → Webhooks); vazio = 404. Assinatura `x-signature: ts=,v1=` validada
  em tempo constante; manifesto `id:<data.id minúsculo>;request-id:<x-request-id>;ts:<ts>;`, HMAC-SHA256 hex
  (**CONFIRMADO com avisos reais em 2026-11-26**: id da order em minúsculas, `request-id` do header, `ts` em segundos;
  o MP também manda `data.external_reference` na query, que NÃO entra no manifesto). O aviso é só
  GATILHO: o corpo é ignorado e o estado vem de um GET no MP (`reconcileProviderOrder`); por isso não há janela de
  tempo nem deduplicação. Respostas: 200 (inclusive order desconhecida/outro tipo), 401 assinatura inválida, 503 se o
  MP não responder (o MP repete; espera 22 s, repete a cada 15 min). **Teste real feito em 2026-11-26** (túnel
  cloudflared + painel do MP em modo teste): aviso aceito, `known=true`, pagamento PAID. O MP manda 2 avisos por evento
  (request-ids diferentes). Lição: um `.env` com o nome da variável duplicado (`X=X=valor`) dá "assinatura inválida"
  sem outra pista; conferir tamanho/formato do segredo (64 hex) antes de duvidar do código.
  **Achado no sandbox:** com o APRO, uma order aprovada antes de devolver o BR Code pode ficar `processing/in_process`
  por muito tempo; o pagamento fica CREATING (IN_PROGRESS não decide nada, nem depois do prazo) até o MP resolver.
- **Emissor da cobrança (desde 2026-11-24, Fase 7 Etapa 7a):** `payment/provider/PixProvider` (`name()`,
  `createCharge(PixChargeRequest)` → `PixCharge(txid, copyPaste)`), escolhido por `moneyx.pix.provider` (padrão
  `sandbox` = `SandboxPixProvider`, BR Code local). `Payment.create` NÃO gera mais o Pix: `attachPixCharge` (uma vez,
  só PENDING). `PaymentService.create` chama o provedor **só para pagamento novo**: depois da reserva da
  Idempotency-Key E do limite de volume, antes do `save` (com um PSP real cada chamada cria uma cobrança de verdade).
  Falha do provedor desfaz tudo e a chave pode ser repetida. Testado com espião do provedor em
  `PaymentIdempotencyIntegrationTest` (repetição, chave com outro corpo e recusa por volume nunca chamam o provedor).
- **Cliente do Mercado Pago (2026-11-24, Etapa 7b; ligado como provedor na 7c-2):**
  `payment/provider/mercadopago/MercadoPagoClient` (API de **Orders**, `HttpClient` do JDK + Jackson, sem SDK):
  `createPixOrder` (`POST /v1/orders`; desde a 7d também com o corpo já montado por `orderBody`), `getOrder`,
  `cancelOrder` (`POST /v1/orders/{id}/cancel`). Configuração
  `moneyx.pix.mercadopago.*` (`MercadoPagoProperties`; token em `MERCADOPAGO_ACCESS_TOKEN`, opcional no Compose).
  Comportamento CONFERIDO no sandbox real:
  - `X-Idempotency-Key` obrigatória; **mesma chave + mesmo corpo = MESMA order (201)**; outro corpo = 409
    `idempotency_key_already_used`. Chave de criação determinística: `creationKeyFor(paymentId)` =
    `moneyx-create-<id>` (repetir a criação após falha devolve a mesma order).
  - Valores são TEXTO `"49.90"`: `formatAmount` (`BigDecimal.valueOf(cents, 2)`) e `parseAmount`
    (`longValueExact`: fração de centavo é RECUSADA, nunca arredondada).
  - Vencimento: só duração (`expiration_time`, ISO-8601 de `Duration.toString()`, ex. `PT29M59S`; 5 min aceito);
    `date_of_expiration` absoluto → 400 `unsupported_properties`. O MP devolve o instante calculado a partir de
    quando ELE recebeu o pedido (um pouco depois do nosso).
  - Status: `action_required`/`waiting_transfer` → `processed`/`accredited` (pago); `canceled`; expirada =
    `canceled/expired` (mapeamento completo em "Conciliação com o PSP"). Cancelar de novo →
    409 `order_already_canceled`; id inexistente → 404 `order_not_found`. Ids de teste começam com `ORDTST`.
  - `payer.first_name = "APRO"` (`test-payer-first-name`, SÓ sandbox) aprova sozinho, com atraso VARIÁVEL
    (medido: 0 a 46 s). `payer.email` é obrigatório no MP (hoje fixo em `payer-email`).
  - O BR Code do MP traz o txid no campo 62.05 (ex.: `mpqrinter190381238073`, 21 caracteres minúsculos):
    `PixBrCode.txidOf` lê (conferindo o CRC). A V13 aceita esse formato.
  - Erros: `MercadoPagoException(httpStatus, code = errors[0].code, retryable, retryAfter)`. Repetível: timeout,
    conexão, 429 (com `Retry-After`) e 5xx; 4xx não. Redirecionamento não é seguido (levaria o token). Id da order
    validado (`[A-Za-z0-9]{1,64}`) antes de entrar na URL. O token nunca vai para mensagem/log/toString, e é
    MASCARADO se a resposta de erro o ecoar.
  - Testes: `MercadoPagoClientTest` (servidor HTTP falso, 27) e `MercadoPagoSandboxSmokeTest` (sandbox REAL, desligado;
    rodar com `-Dmercadopago.smoke=true` e `MERCADOPAGO_ACCESS_TOKEN` na sessão, comando no javadoc).
- Só o texto é devolvido (sem imagem do QR); o comerciante gera a imagem a partir do `copyPaste`.
- `POST /v1/sandbox/payments/{id}/pay` simula o pagamento: 200 (PAID); 404 se não existir/for de outro comerciante;
  409 `payment_not_pending` (com `paymentStatus`) ou `payment_expired`. Chave `mx_live_` → 403 `sandbox_requires_test_key`.
- Toda mudança de estado de pagamento usa `PaymentRepository.lockByIdAndMerchantId` (lock pessimista).
  O `@Version` é a segunda camada; `ObjectOptimisticLockingFailureException` vira 409 `concurrent_update`.
  Comprovado por mutação: sem o lock, o `@Version` ainda impede pagamento duplo, mas os concorrentes recebiam 500.
- [x] Implementar consulta.
- [x] Implementar cancelamento.
- [x] Criar testes de transição de estado.
- [x] Jobs agendados: expirar PENDING vencidos e apagar chaves de idempotência vencidas.

**Fase 4 concluída em 2026-11-20.**

#### Jobs agendados (definido em 2026-11-20)

- `payment/job/PaymentMaintenanceJobs` (`@EnableScheduling` + `@ConditionalOnProperty moneyx.jobs.enabled`, padrão `true`)
  só dispara `payment/service/PaymentMaintenanceService`, onde ficam as regras.
  - Expiração: `fixedDelay` 1 min, lotes de 100 até esvaziar. PENDING com `expires_at <= now` → EXPIRED
    (o mesmo limite inclusivo da regra de pagar).
  - Limpeza: `fixedDelay` 1 h, lotes de 1.000, apaga `payment_idempotency_keys` com `expires_at <= now`.
  - **Desde a Fase 7:** a expiração local só pega cobranças SEM `provider_order_id` (sandbox). As do PSP expiram pela
    conciliação, que cancela no PSP antes. Jobs novos no `PaymentMaintenanceJobs`: recuperação
    de `CREATING` (30 s, lote 20, 7c-1) e **conciliação com o PSP** (30 s, lote 20, 7d; sem efeito com o sandbox).
- Cada lote é uma transação curta (`TransactionTemplate`; `@Transactional` não funcionaria numa chamada interna da
  mesma classe). Um único `now` por execução.
- `SELECT ... FOR UPDATE SKIP LOCKED`: linhas trancadas por pagar/cancelar em andamento são puladas e ficam para a
  próxima execução. Duas instâncias da aplicação dividem o trabalho sem repetir (testado).
- Índices da V6: parcial `payments (expires_at) WHERE status = 'PENDING'` e `payment_idempotency_keys (expires_at)`
  (uso confirmado com `EXPLAIN`).
- Limpeza das chaves: `WITH batch AS MATERIALIZED (SELECT ... LIMIT ... FOR UPDATE SKIP LOCKED) DELETE ... USING batch`
  e o laço repete **enquanto o lote apagar algo** (`batch > 0`). Corrigido em 2026-11-20: a versão com
  `DELETE ... WHERE id IN (subconsulta com LIMIT + SKIP LOCKED)` e laço `while (batch == limite)` apagou menos linhas
  que o esperado numa execução da suíte completa (falha intermitente, causa exata não reproduzida isoladamente).
  Com SKIP LOCKED, um lote curto NÃO significa que acabou.
- **Limitação conhecida:** se o domínio recusar um pagamento do lote (`expire()` lança exceção), o lote inteiro é desfeito
  e o job falha nesse ponto a cada execução. Hoje a consulta e o domínio usam exatamente a mesma regra; se divergirem no
  futuro, avaliar isolar por linha e registrar a falha.
- Nos testes os jobs ficam desligados (`src/test/resources/config/application.properties`); os testes chamam o serviço
  com `MutableClock` começando em **2020**, para que o job nunca toque em dados reais do banco de desenvolvimento.

#### Consulta e cancelamento (definido em 2026-11-20)

- `GET /v1/payments/{id}` → 200; 404 se não existir ou for de outro comerciante.
- `GET /v1/payments` → `{"data": [...], "hasMore": bool}`, mais recentes primeiro. **Paginação por cursor** (Stripe):
  `?limit=` (1–100, padrão 10) e `?startingAfter=<id do último item da página anterior>`. Filtros opcionais
  `status` e `externalReference` (comparado sem espaços nas pontas). `limit` fora da faixa → 400;
  `startingAfter` que não é pagamento do comerciante → 400 `invalid_starting_after`.
  Por que cursor e não offset: pagamentos novos não deslocam as páginas seguintes (nada pula nem repete).
- Ordenação `created_at DESC, id DESC` (`PaymentSpecifications.NEWEST_FIRST`). O desempate por `id` é obrigatório,
  e o cursor usa a MESMA ordem: `created_at < c OR (created_at = c AND id < c.id)`. A ordem de UUID é a do PostgreSQL
  (diferente do `UUID.compareTo` do Java): testes comparam com `ORDER BY` do banco, nunca com ordenação em Java.
- A listagem busca `limit + 1` linhas para calcular `hasMore`, sem `COUNT(*)`.
- Filtros opcionais via `Specification` (`PaymentRepository extends JpaSpecificationExecutor`) e `findBy(spec, q -> q.sortBy(...).limit(n).all())`.
  O cursor é buscado por `id` E `merchantId`.
- `POST /v1/payments/{id}/cancel` (ação explícita; não `DELETE`, o pagamento continua existindo) → 200 CANCELED.
  Já CANCELED → **200 idempotente** (mantém o `canceledAt` original). PAID/EXPIRED → 409 `payment_not_pending`
  (com `paymentStatus`). PENDING com prazo vencido → 409 `payment_expired`. Usa o lock pessimista.
  **Cobrança do PSP (7d):** cancela PRIMEIRO no PSP, fora de transação. Se já tiver sido paga, vira PAID e responde 409;
  se o PSP estiver fora ou processando, responde 503 `payment_provider_unavailable` + Retry-After e nada muda (o
  `@Transactional` saiu do método; as transações são abertas com `TransactionTemplate`).
- Comprovado por mutação (6 bugs plantados, todos detectados) e por HTTP real com pagar × cancelar simultâneos.

### Fase 5 — Eventos e webhooks

- [x] Modelar eventos.
- [x] Avaliar padrão Outbox (adotado: tabela `events` gravada na transação da mudança de estado).
- [x] Cadastro de endpoints de webhook (com linha do tempo de auditoria).
- [x] Implementar webhooks (entrega a partir do Outbox).
- [x] Implementar assinatura.
- [x] Implementar tentativas e retentativas (backoff exponencial com jitter, desativação automática, reenvio manual).
- [x] Avaliar RabbitMQ: **não introduzido**. Outbox + jobs no PostgreSQL (SKIP LOCKED + lease) atendem a v1; reavaliar
  só com volume que o banco não suporte (e mesmo assim o Outbox continua necessário).

**Fase 5 concluída em 2026-11-20.**

#### Eventos (definido em 2026-11-20)

- Tabela `events` (V7): `id`, `merchant_id` (FK), `type` (CHECK), `resource_id` (sem FK: o módulo é genérico),
  `data JSONB` (CHECK objeto), `created_at`. **Imutável**: colunas `updatable = false`, sem métodos de alteração.
- Tipos (`EventType`, contrato público, nunca renomear): `payment.created`, `payment.paid`, `payment.canceled`,
  `payment.expired`. Gravados pelo valor público via `EventTypeConverter`.
- `data` é o **retrato** do recurso no momento do evento: o `PaymentView` serializado com o MESMO `JsonMapper` da API.
  `PaymentView` (antes `api/dto/PaymentResponse`) fica em `payment/service` porque é usado pela API e pelos eventos.
- **Outbox:** `EventService.record` usa `Propagation.MANDATORY`: só grava dentro da transação de quem chama.
  Mudança de estado e evento são atômicos (testado nos dois sentidos, com falha no evento e com rollback).
  Pontos de gravação: `PaymentService.create` (só quando cria de fato), `simulatePixPayment`, `cancel` (não no
  cancelamento repetido) e `PaymentMaintenanceService.expireBatch`. Chamados via `PaymentEventRecorder`.
- API: `GET /v1/events` (cursor `limit`/`startingAfter`, filtro `type`; `{"data", "hasMore"}`) e `GET /v1/events/{id}`.
  Envelope `{id, type, createdAt, data}`; `data` sai como objeto (`@JsonRawValue`). Tipo desconhecido → 400.
- O `JSONB` não preserva a ordem das chaves: em `data` elas aparecem reordenadas em relação a `/v1/payments`
  (mesmo conteúdo; JSON não garante ordem).
- O comerciante deve deduplicar pelo `id` do evento: a entrega por webhook será "pelo menos uma vez".
- Retenção: eventos não são apagados por enquanto (Stripe guarda 30 dias); avaliar na Fase 6.

#### Endpoints de webhook (definido em 2026-11-20)

- Módulo `webhook` (depende só de `event`, pelo catálogo de tipos). V8: `webhook_endpoints` (ESTADO ATUAL) e
  `webhook_endpoint_history` (LINHA DO TEMPO / trilha de auditoria, só INSERT).
- **Estado atual + linha do tempo (abordagem escolhida pelo desenvolvedor):** cada mudança grava, na MESMA transação,
  `{action, apiKeyId (quem fez), occurredAt, snapshot (url + enabledEvents, NUNCA o segredo)}`. Não é Event Sourcing
  completo: o estado continua na tabela principal; a linha do tempo é complementar. Novas ações (edição, rotação,
  desativação por falhas) entram como novos valores de `WebhookEndpointAction` (e do CHECK).
- Endpoints: `POST /v1/webhook-endpoints` (201, segredo `whsec_...` só nesta resposta, `no-store`), `GET` (ativos, `{data}`),
  `GET /{id}` (inclusive removidos, com `deletedAt`), `DELETE /{id}` (lógico, idempotente: 204 sempre; repetir não grava
  histórico), `GET /{id}/history` (do mais antigo ao mais recente; disponível após a remoção).
- Até **5** endpoints ativos por comerciante → 409 `webhook_endpoint_limit_reached`. O lock é na **linha do comerciante**
  (`WebhookEndpointRepository.lockMerchant`, `SELECT ... FROM merchants ... FOR UPDATE`), não nos endpoints: com zero
  endpoints não haveria linha para trancar (provado: sem esse lock, 8 cadastros simultâneos partindo de zero furavam o
  limite). Regra geral: **para limites que começam em zero, trancar a linha "pai"**.
- `enabledEvents`: opcional (padrão todos); vazio, nulo ou tipo desconhecido → 400; sem repetição, na ordem do catálogo.
  Banco: `VARCHAR(50)[]` com CHECK `cardinality >= 1 AND enabled_events <@ ARRAY[...]`.
- **Segredo de assinatura cifrado** (`shared/crypto/SecretCipher`, AES-256-GCM do JDK, nonce aleatório de 12 bytes, tag de
  128 bits). Chave em `MONEYX_WEBHOOK_ENCRYPTION_KEY` (.env). **AAD = id do endpoint**: texto cifrado copiado para outro
  endpoint não decifra. Diferente da API Key (hash), porque o MoneyX precisa LER o segredo para assinar.
  `WebhookEndpointService.signingSecretOf` é de uso interno (entrega); nunca devolver pela API. Só o prefixo (`whsec_ab12`)
  aparece depois do cadastro. Rotação da chave de cifragem: ver "Rotação da chave de cifragem" (Fase 6, Etapa 6d).
- **SSRF:** `WebhookUrlValidator` (cadastro, sem DNS): URL absoluta, `https`, até 2048 caracteres, sem credenciais e sem
  fragmento; rejeita `localhost`/`*.localhost`, IPs internos escritos na URL (loopback, privados, link-local incl.
  169.254.169.254, CGNAT 100.64/10, 0/8, 240/4, multicast, IPv6 ULA fc00::/7, `::ffff:127.0.0.1`) e formas numéricas
  alternativas (`2130706433`, `0177.0.0.1`). `moneyx.webhooks.allow-local-urls` (padrão **false**) libera só
  `http(s)://localhost`/loopback para testes locais. **A checagem definitiva é na ENTREGA** (`isInternalAddress` sobre o
  IP resolvido em cada envio), por causa de DNS rebinding.

#### Entrega de webhooks (definido em 2026-11-20)

- **Fan-out no Outbox:** `EventService.record` publica `EventRecorded` (`ApplicationEventPublisher`, padrão Observer);
  `webhook/service/WebhookFanOut` escuta com `@EventListener` + `Propagation.MANDATORY` e cria uma `WebhookDelivery` por
  endpoint ATIVO que aceita o tipo, na MESMA transação do pagamento e do evento. UNIQUE `(event_id, endpoint_id)` (V9).
  Não usar `@TransactionalEventListener(AFTER_COMMIT)` para isso (perderia a atomicidade). `event` continua sem depender de `webhook`.
- `EventView` (antes `event/api/dto/EventResponse`) fica em `event/service`: é o corpo dos webhooks e a resposta da API.
- **Reserva com prazo (lease):** `WebhookDeliveryService.deliverDue` (job a cada 5 s, lotes de 10):
  1) transação curta: `lockDue` (`FOR UPDATE SKIP LOCKED`, só PENDING vencidas sem reserva válida) + `claim()` grava
  `lease_until` e um `lease_token`; 2) POST **fora de transação**; 3) transação curta: grava o resultado SÓ se o token
  ainda for o da reserva (resultado atrasado de reserva vencida é descartado). Queda no meio → a reserva vence →
  reenvio (pelo menos uma vez). `lease` (30 s) precisa ser maior que connect (5 s) + request (10 s) (validado na partida).
- O `SKIP LOCKED` serve à **vazão**; a garantia de "uma vez por reserva" vem do `lease_until` (provado: a mutação que remove
  o SKIP LOCKED não quebra nenhum teste de correção, porque o PostgreSQL reavalia o WHERE depois de esperar o lock).
- **Assinatura** (`webhook/domain/WebhookSignature`): `MoneyX-Signature: t=<epoch s>,v1=<hex HMAC-SHA256(segredo, t + "." + corpo)>`.
  Também `MoneyX-Event-Id`, `MoneyX-Event-Type`, `User-Agent: MoneyX-Webhooks/1.0`, `Content-Type: application/json`.
  Corpo = `EventView` (idêntico a `GET /v1/events/{id}`). Validado contra vetor calculado em Python.
- **Como a loja verifica** (`WebhookSignature.verify` é a implementação de referência): separar `t` e `v1`; recusar se
  `|agora - t| > 5 min` (replay); recalcular o HMAC sobre os BYTES recebidos (`t + "." + corpo`, sem reformatar o JSON);
  comparar em tempo constante; deduplicar pelo `id` do evento.
- **Cliente HTTP** (`WebhookSender`): `HttpClient` do JDK, `followRedirects(NEVER)` (redirecionamento = falha
  `non_2xx_response`), corpo da resposta descartado (não guardar dados da loja). Sucesso = 2xx.
- **SSRF na entrega** (`WebhookDestinationGuard`): resolve o DNS a cada envio e recusa se QUALQUER IP for interno
  (`destination_not_allowed`, sem conectar). Com `allow-local-urls=true`, libera só loopback. **Risco residual documentado:**
  janela mínima de DNS rebinding entre a checagem e a conexão; solução completa = proxy de saída (Fase 7).
- Erros gravados como código estável (`WebhookDeliveryError`): `timeout`, `connection_failed`, `destination_not_allowed`,
  `non_2xx_response`; mais status HTTP e duração. Nesta etapa uma tentativa só: falha → `FAILED`. Endpoint removido →
  entregas pendentes viram `CANCELED` sem envio.
- Agendador com 2 threads (`spring.task.scheduling.pool.size=2`): loja lenta não atrasa a expiração de pagamentos.
- Configuração em `moneyx.webhooks.delivery.*` (`WebhookDeliveryProperties`, `@ConfigurationProperties`).
- Para testar entrega real localmente: subir a app com `-Dspring-boot.run.arguments=--moneyx.webhooks.allow-local-urls=true`
  e um receptor HTTP em 127.0.0.1.

#### Retentativas, desativação automática e reenvio (definido em 2026-11-20)

- `webhook/domain/WebhookRetryPolicy` (pura): **9 tentativas** (1 imediata + 8), esperas 1 min, 5 min, 30 min, 2 h, 5 h,
  10 h, 24 h, 24 h (~2,7 dias). Jitter de ±10% (`moneyx.webhooks.delivery.retry-jitter`, 0 nos testes) contra
  "thundering herd". Todas as falhas reagendam, EXCETO `destination_not_allowed` (decisão de segurança → FAILED na hora).
- Enquanto há tentativas, a entrega continua `PENDING` com `next_attempt_at` no futuro; esgotadas → `FAILED`.
- Desativação automática: `webhook_endpoints.failing_since` marca o início da sequência atual de falhas (zerada a cada
  sucesso). Falha com `failing_since` há **3 dias** ou mais (`WebhookEndpoint.AUTO_DISABLE_AFTER`) → `disabled_at` + linha
  do tempo `DISABLED` **sem API Key** (ação do sistema; CHECK `(action = 'DISABLED') = (api_key_id IS NULL)`).
  Desativado: não recebe novas entregas (fan-out usa `isEnabled()`), pendentes viram `CANCELED` ao vencer, continua contando
  no limite de 5. `isActive()` = não removido; `isEnabled()` = não removido e não desativado.
- Ao gravar o resultado, o ENDPOINT é trancado (`lockByIdAndMerchantId`) na mesma transação curta: entregas do mesmo
  endpoint terminando juntas atualizam `failing_since` uma de cada vez (provado: sem o lock, o `@Version` falha).
- `POST /v1/webhook-endpoints/{id}/enable` → 200, idempotente, linha do tempo `ENABLED` com a API Key; removido → 409
  `webhook_endpoint_deleted`. Eventos do período desativado não são reenviados (a loja usa `GET /v1/events`). Retentativas
  que ainda estavam pendentes voltam a ser enviadas normalmente.
- `GET /v1/webhook-endpoints/{id}/deliveries` (cursor `limit`/`startingAfter`, filtro `status` em minúsculas) →
  `{id, endpointId, eventId, status, attempts, nextAttemptAt (só se pendente), lastAttemptAt, lastStatusCode, lastError,
  lastDurationMs, deliveredAt, createdAt}`. Inclui endpoints removidos. Nunca o corpo nem a resposta da loja.
- `POST /v1/webhook-deliveries/{id}/retry` → só `FAILED` (senão 409 `delivery_not_failed` com `deliveryStatus`); endpoint
  removido/desativado → 409 `webhook_endpoint_unavailable`. Volta para a fila para agora; as tentativas continuam contando
  (agenda esgotada + nova falha → FAILED de novo).
- **Ordem da linha do tempo (resolvido em 2026-11-21, V11):** `occurred_at, seq`. `seq BIGINT GENERATED ALWAYS AS
  IDENTITY` (+ UNIQUE) é numerada pelo banco no INSERT; a entidade a mapeia somente leitura (`insertable/updatable = false`).
  Reflete a ordem real porque toda escrita da linha do tempo de um endpoint ocorre sob lock (endpoint ou comerciante).
  **Nunca desempatar por UUID aleatório nem confiar na ordem física:** `WebhookEndpointServiceIntegrationTest` grava ações
  no mesmo instante com ids decrescentes e depois faz `CLUSTER` da tabela pelo id (um `UPDATE` não serve: é HOT e o
  índice mantém a posição antiga). Linhas anteriores à V11 receberam `seq` em ordem arbitrária.

### Fase 6 — Qualidade e documentação

- [x] Rate limiting no cadastro público de comerciantes (em memória; Redis só com mais de uma instância).
- [x] Limite acumulado de volume por comerciante (R$ 200 mil/dia, R$ 2 mi/mês; 2026-11-24).
- [x] Rotação da chave de cifragem dos segredos de webhook (chaveiro + recifragem em lotes; 2026-11-24).
- [ ] Expandir testes automatizados.
- [x] Linha do tempo de webhooks: coluna de sequência para ordenação estável (V11, 2026-11-21).
- [x] Documentar API (OpenAPI 3.1 + Swagger UI + teste de contrato).
- [x] Criar Docker Compose completo.
- [x] Criar pipeline CI (GitHub Actions) e isolar o banco dos testes (Testcontainers).
- [x] Observabilidade (id de correlação, logs estruturados, métricas de negócio, porta de gerenciamento).
- [x] Revisar segurança (Compose completo, actions por hash, Dependabot, headers HTTP).

### Fase 7 — Evolução

- [x] Integrar provedor real em ambiente sandbox: **Mercado Pago, API de Orders** (decidido em 2026-11-24; a Efí foi
  descartada pela burocracia de certificado mTLS). Etapas 7a–7e no PROGRESS.md. [x] 7a: porta `PixProvider`. [x] 7b: cliente do Mercado Pago.
  [x] 7c-1: criação em três fases + recuperação (V13). [x] 7c-2: provedor Mercado Pago ligado.
  [x] 7d: conciliação (V14). [x] 7e: aviso do MP com assinatura (confirmada com avisos reais).

**Provedor Pix real concluído em 2026-11-25** (commits `4bbf314`, `1677851`, `33aed4f`): 679 testes, 14 mutações
detectadas, ponta a ponta real no sandbox do MP (pago pela conciliação, cancelar com o MP fora = 503, vencido
cancelado no MP antes de EXPIRED).

Pendências e próximos itens da Fase 7 (evolução):
- [x] Teste real do aviso do MP (túnel cloudflared, 2026-11-26): manifesto da assinatura confirmado.
- [x] Trivy bloqueante (2026-11-25; imagem 0.76.2 já era a mais nova).
- [x] Chaves `mx_live_` com verificação de e-mail (Mailpit em dev, V16) e bloqueio de `/v1/sandbox/**` (2026-11-25).
  KYC de documentos fica fora.
- [x] Limite de volume por comerciante (V15, ajuste por SQL; 2026-11-25).
- [x] Proxies confiáveis (IP real no rate limit) + HSTS atrás de proxy (2026-11-25).
- [x] Rate limit em Redis (opcional por configuração; o Compose usa; 2026-11-25).
- [ ] Proxy de saída para webhooks (DNS rebinding): o desenvolvedor optou por NÃO fazer agora.
- [x] Migrar para Java 25 LTS (pom, CI, Dockerfile; 2026-11-25).
- [ ] Desenvolver dashboard.

### Fase 8 — Frontend (iniciada em 2026-11-26)

Escopo: **landing page** + **dashboard do comerciante**, com animações e 3D.

#### Decisões (2026-11-26)
- **Monorepo:** pasta `frontend/` (minúsculas) na raiz deste repositório. O backend continua na raiz (não mover para
  `backend/` agora). Imagem Docker, job de CI (filtrado por caminho) e entrada npm do Dependabot próprios.
  `frontend/` fica fora do `.dockerignore`/imagem do backend; `node_modules/` no `.gitignore`.
- **Stack:** TypeScript + **Next.js** (React). 3D com React Three Fiber (Three.js); animação de rolagem com GSAP;
  microinterações com Motion. Node 26 LTS na máquina.
- **Fronteira com o backend:** o frontend conhece o backend SÓ pelo contrato HTTP. Cliente/tipos TypeScript
  **gerados do `src/test/resources/openapi/moneyx-api.json`** (se a API mudar, o build do frontend quebra). Nenhuma
  regra de negócio duplicada no frontend.
- **Segurança:** API Keys (`mx_test_`/`mx_live_`) são segredos de servidor e **nunca** vão para o navegador. O
  dashboard usa o servidor do Next.js como **BFF** (cookie de sessão `HttpOnly`; as chamadas à API Java saem do
  servidor). Login do comerciante: a decidir (hoje só existe API Key).
- **Paleta "Midnight Emerald"** (escura primeiro; modo claro do dashboard depois). Sempre via tokens CSS
  (`--color-*`), nunca cores soltas nos componentes:

| Token | Cor | Uso |
|---|---|---|
| `--color-bg` | `#070B14` | fundo da página |
| `--color-surface` | `#0F1626` | cards, painéis |
| `--color-border` | `#1E293B` | divisórias |
| `--color-text` | `#E6EDF7` | texto principal |
| `--color-text-muted` | `#8A97AD` | legendas |
| `--color-primary` | `#12D6A0` | ações, PAID, destaques |
| `--color-accent` | `#7C5CFF` | brilho 3D, partículas |
| `--color-info` | `#22B8F0` | meio do gradiente, links |
| `--color-warning` | `#F5B83D` | PENDING, avisos |
| `--color-danger` | `#FF5C7A` | erros, CANCELED/EXPIRED |
| gradiente herói | `#12D6A0 → #22B8F0 → #7C5CFF` | landing, cena 3D |

  Regras: contraste WCAG AA (texto >= 4,5:1); estado nunca só pela cor (texto + ícone); não usar o teal oficial do
  Pix (`#32BCAD`) como cor da marca; respeitar `prefers-reduced-motion` nas animações/3D.
- **Login do comerciante no dashboard:** e-mail + senha (decidido em 2026-11-26), com hash forte, rate limit e sessão
  em cookie `HttpOnly` mantida pelo BFF. Etapa própria no backend.

#### Etapas
- [x] **8a** (2026-11-26) Estrutura: `frontend/` com Next.js 16.4.6, React 19.1.10, TypeScript **5.9.3** (o 6/7 não
  servem: `typescript-eslint` aceita até <6.1 e `openapi-typescript` 7.13 exige ^5), ESLint 10.15.0 (exceção de 13 dias
  ao cooldown por correção de segurança no `minimatch`), Vitest + Testing Library, Playwright, three/R3F/drei, GSAP,
  Motion, openapi-typescript/openapi-fetch. Versões EXATAS (`.npmrc save-exact`). Tokens em `src/styles/tokens.css`;
  fontes Inter/Space Grotesk/JetBrains Mono via `next/font` (baixadas no build). `next.config.ts`: sem `X-Powered-By`,
  `output: standalone`, headers nosniff/no-referrer/DENY/Permissions-Policy (CSP com nonce vem com o BFF).
  Tipos da API: `npm run api:types` gera `src/lib/api/schema.d.ts` do contrato (versionado; o CI regenera e falha se
  diferir). Backend: `PaymentView.status`/`currency` ganharam `@Schema(allowableValues)` (o contrato passou a listar os
  valores) + `PaymentViewContractTest` garante que batem com os enums. Primeiro componente: `StatusBadge` (texto +
  ícone; `satisfies Record<PaymentStatus,...>` acusa status novo sem tratamento). CI: workflow `frontend.yml`
  (paths: `frontend/**` e o contrato) com `npm ci`, tipos em dia, lint sem avisos, typecheck, testes, build e
  `npm audit --omit=dev --audit-level=high`; o `ci.yml` ignora mudanças só no frontend/.cursor. Dependabot npm.
  `.dockerignore` do backend exclui `frontend/`; `.gitattributes` força LF em `frontend/**`.
- [x] **8b** (2026-11-26) Backend: login do comerciante. Decisões do desenvolvedor: **Argon2id**; senha no cadastro +
  link por e-mail para definir/redefinir; sessão **6 h sem uso / 24 h no máximo** (mais longa que o comum em painéis
  financeiros, 15–30 min; compensado por logout imediato, redefinição que derruba todas as sessões e cookie
  HttpOnly/Secure/SameSite no BFF); limite **por IP + por e-mail**.
  - **V17:** `merchants.password_hash` (NULL = conta anterior à 8b, define pelo link), `merchant_sessions` (só o hash
    do token, `last_used_at`, `expires_at`, `revoked_at`), `password_reset_tokens` (1 h, uso único) e
    `webhook_endpoint_history.session_id` (autor = API Key OU sessão; ações do sistema sem autor; CHECK novo).
  - **Senha:** `PasswordPolicy` (12–128 caracteres contados como caracteres, não bytes; não pode ser o e-mail; sem regras
    de composição, como o NIST 800-63B). Hash `{argon2}` via `DelegatingPasswordEncoder` + `Argon2PasswordEncoder(16,
    32, 1, 19456, 2)` (mínimo OWASP: 19 MiB, t=2, p=1). Dependência `bcprov-jdk18on` 1.87 (versão fixa no pom).
    `POST /v1/merchants` exige `password`; `register(...)` sem senha continua existindo (testes e contas antigas).
    **Bug achado pelos testes:** atribuir o hash DEPOIS do save não gravava (a verificação de e-mail roda um UPDATE
    `@Modifying(clearAutomatically)` que limpa o contexto do JPA e desanexa o Merchant) → o hash entra antes do 1º save.
  - **Sessão:** `POST /v1/sessions` (público) → 201 `{id, merchantId, token "mx_sess_...", expiresAt, idleTimeout}`,
    `no-store`. Mesma resposta 401 `invalid_credentials` para e-mail inexistente, conta sem senha e senha errada, com
    o Argon2 rodando contra um hash falso quando não há conta (tempo igual). Argon2 FORA de transação. O token vai no
    mesmo `Authorization: Bearer` (o filtro reconhece o prefixo `mx_sess_`); `last_used_at` gravado no máximo 1 vez
    por minuto. Sessão age como chave de teste (`live=false`). `DELETE /v1/sessions/current` → 204 (idempotente; com
    API Key não faz nada).
  - **Limites:** `LoginRateLimiter` (security) implementa a porta `LoginRateLimits` (merchant): login 20/IP (+1 a cada
    30 s) e 10/e-mail (+1 a cada 90 s = 10 a cada 15 min); redefinição 5/IP (+1/2 min) e 3/e-mail (+1/20 min). Toda
    tentativa conta; o balde por e-mail existe para qualquer texto (não revela conta). `RateLimiterFactory` cria todos
    os limitadores no armazenamento configurado (memory/redis), cada um com o seu prefixo. 429 `rate_limit_exceeded`
    + Retry-After.
  - **Redefinição:** `POST /v1/merchants/password-reset` (público, 202 SEMPRE) e `/confirm` (`{token, newPassword}` →
    204; troca a senha, revoga TODAS as sessões, confirma o e-mail; 400 `invalid_reset_token`/`reset_token_expired`;
    senha fraca → 400 `invalid_request` e o link continua valendo). Link para o FRONTEND:
    `MONEYX_DASHBOARD_BASE_URL` (padrão http://localhost:3000) + `/redefinir-senha?token=`.
  - Erros de conta no `AccountExceptionHandler` (restrito aos 3 controllers de conta). DTOs com senha/token sobrescrevem
    `toString`. `ObservabilityIntegrationTest` agora também prova que a senha nunca vai para o log.
  - Testes: `DashboardLoginIntegrationTest` (20, banco + filtro reais), `MerchantSessionTest` (unidade).
- [ ] **8c** Landing page com 3D (R3F) e animações de rolagem (GSAP), respeitando `prefers-reduced-motion`.
- [x] **8d** (2026-11-27) BFF no Next.js + telas de conta. Decisões do desenvolvedor: o próprio token `mx_sess_` num
  cookie `__Host-mx_session` (HttpOnly, Secure, SameSite=Lax, Path=/, expira com o limite absoluto da sessão); CSRF =
  SameSite=Lax + checagem de Origin; rotas em português.
  - **BFF:** `src/lib/server/api.ts` (`import "server-only"`: o build falha se um componente de cliente importar) cria o
    cliente `openapi-fetch` por requisição com `Authorization: Bearer mx_sess_...` e `X-Forwarded-For`; `cache:
    no-store`, timeout 10 s, erro de rede vira `ApiUnavailableError` (mensagem "não conseguimos falar com o MoneyX").
    `MONEYX_API_URL` (padrão http://localhost:8080). Escritas por **Server Actions** (`src/app/actions/auth.ts`):
    login, cadastro (já abre a sessão e mostra a chave `mx_test_` do cadastro UMA vez), esqueci a senha (mensagem
    neutra, como a API), redefinir (apaga o cookie: a API derrubou todas as sessões) e sair (DELETE na API, melhor
    esforço, e apaga o cookie). `redirect()` sempre fora de try/catch.
  - **`src/proxy.ts`** (Next 16: "proxy" é o antigo middleware): (1) **CSRF**: escrita (não GET/HEAD/OPTIONS) sem
    `Origin`, com `Origin: null` ou de outro host → 403. A checagem nativa das Server Actions do Next só AVISA quando o
    Origin falta, por isso a nossa existe; (2) **CSP com nonce** por requisição (`script-src 'self' 'nonce-…'
    'strict-dynamic'`, `object-src 'none'`, `base-uri 'none'`, `form-action 'self'`, `frame-ancestors 'none'`; estilos
    com `'unsafe-inline'`; em dev + `'unsafe-eval'` e `ws:`). O layout raiz lê `headers()` → TODAS as rotas dinâmicas
    (página pré-gerada não teria o nonce e os scripts seriam bloqueados); (3) `/painel/**` sem cookie → `/entrar?proximo=`;
    `/entrar?sessao=expirada` apaga o cookie (apagar cookie `__Host-` exige Secure + Path=/ no Set-Cookie).
  - **Quem decide a sessão é a API:** `requireMerchant()` (`src/lib/server/merchant.ts`, `cache` do React) chama
    `GET /v1/merchants/me`; 401/403 → `/entrar?sessao=expirada`. Destino pós-login só `/painel` ou `/painel/...` sem `//`
    nem `\` (`safeRedirectPath`, contra open redirect).
  - **IP do visitante:** o Next só PREENCHE `X-Forwarded-For` se ele não veio (`??=` no base-server) → o BFF repassa o
    ÚLTIMO item, e só se tiver cara de IP. A API só acredita nele com o IP do Next em `MONEYX_TRUSTED_PROXIES` (8f).
    **Risco residual:** com o Next exposto direto, o visitante escolhe o "IP" (o limite por e-mail continua valendo);
    em produção o Next fica atrás de um proxy que sobrescreve o header. Sem confiar no Next, todos os visitantes dividem
    os baldes por IP (o E2E estoura o limite de cadastro 5/IP: limpar `moneyx:ratelimit:*` no Redis de dev).
  - **Erros:** `src/lib/forms/problem.ts` traduz pelo `code` estável e por campo (`errors[].field`); o `detail` em inglês
    da API nunca aparece. 429 → minutos do Retry-After arredondados para cima. Senha nunca volta preenchida.
  - Telas: `(conta)/entrar`, `cadastro`, `esqueci-a-senha`, `redefinir-senha`; `/painel` mínimo (8e preenche).
    Componentes `Field` (rótulo + dica + erro ligados por `aria-describedby`, erro em texto), `SubmitButton`
    (`useFormStatus`), `Alert` (`role=alert`/`status`, "Erro:"/"Pronto:" em texto).
  - **Testes:** Vitest 39 (CSRF, CSP, IP, mensagens, open redirect). **Playwright** (`npm run e2e`, contra ambiente no ar;
    `E2E_BASE_URL`, `MAILPIT_URL`): fluxo completo com o e-mail real no Mailpit, cookie HttpOnly invisível ao JS, token
    morto depois de sair, link de redefinição de uso único, `?proximo=`, erros por campo, e XSS por HTML injetado
    (`<img onerror>`) bloqueado pela CSP. Script criado por JS confiável é PERMITIDO pelo `strict-dynamic` (de propósito):
    não serve como teste de XSS. O role="alert" vazio do Next (anunciador de rota) exige filtrar pelo texto "Erro:".
    Mutações: 10 unitárias + 2 E2E (cookie sem HttpOnly; sair sem DELETE na API), todas mortas, controle verde.
- [ ] **8e** Dashboard: pagamentos (lista/detalhe/cancelar), API Keys, webhooks e eventos.
- [ ] **8f** Imagem Docker do frontend + Compose + testes ponta a ponta (Playwright).
- Pendência visual: trocar os ícones de texto do `StatusBadge` por SVG (o ⌛ aparece como emoji colorido).

## 12. Diretrizes de Git

- **Decisão atual:** enquanto o projeto estiver em fase de aprendizado/ambiente de testes, os commits são feitos diretamente na `main`, sem abertura de PR.
- Quando o projeto amadurecer (ex.: CI ativo ou colaboradores), voltar a utilizar branches de funcionalidade e Pull Requests.
- A IA não faz commits; ela sugere os comandos e mensagens, e o desenvolvedor executa.
- Criar commits pequenos e descritivos.
- Não incluir segredos.
- Não realizar push sem conferir os arquivos staged.
- Revisar diffs antes de concluir commits.

Exemplos de mensagens:

```text
chore: initialize Spring Boot project and PostgreSQL infrastructure
feat: add merchant registration
feat: implement payment creation
test: add payment lifecycle tests
docs: document MoneyX architecture
```

## 13. Como a IA deve trabalhar neste projeto

Ao receber uma solicitação relacionada ao MoneyX:

0. **No início de cada sessão, ler também `.cursor/skills/moneyx/PROGRESS.md`**: histórico, ponto exato onde o
   trabalho parou, plano detalhado da próxima etapa e armadilhas já conhecidas. Atualizá-lo ao concluir cada etapa.
1. Consultar esta skill.
2. Considerar o estado atual do repositório, sem presumir que o roadmap já foi implementado.
3. Respeitar as decisões técnicas registradas.
4. Verificar os arquivos relevantes antes de sugerir alterações.
5. Explicar as decisões em português.
6. Utilizar nomes de classes, métodos e variáveis em inglês.
7. Priorizar código legível e boas práticas.
8. Não introduzir complexidade desnecessária.
9. Não implementar funcionalidades futuras sem solicitação.
10. Atualizar esta documentação quando decisões importantes forem alteradas.

**Regra central:** o objetivo não é apenas fazer o MoneyX funcionar. É construir uma infraestrutura de pagamentos compreensível, testável, segura e tecnicamente consistente, enquanto o desenvolvedor evolui junto com o projeto.
