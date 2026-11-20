
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
| Java 21 LTS | Linguagem principal |
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
| Mockito | Testes com dependências simuladas |
| Spring Security | Segurança e autenticação |
| OpenAPI / Swagger | Documentação da API |
| GitHub Actions | CI/CD, futuro |

### Decisões importantes

- Java 21 é a versão de desenvolvimento escolhida.
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
│   │   │   ├── payment/                   ← módulo de pagamentos (mesmas camadas + job/ para agendamentos)
│   │   │   ├── event/                     ← módulo de eventos (histórico + Outbox), genérico
│   │   │   ├── webhook/                   ← endpoints, linha do tempo, entregas assinadas (+ job/)
│   │   │   ├── security/                  ← Spring Security: filtro de API Key, SecurityConfig, erros 401/403
│   │   │   └── shared/                    ← código transversal, sem dependência de módulos
│   │   │       ├── api/                   ← GlobalExceptionHandler (Problem Details)
│   │   │       ├── config/                ← ClockConfig
│   │   │       └── crypto/                ← SecretCipher (AES-256-GCM) e CryptoConfig
│   │   └── resources/
│   │       ├── application.properties
│   │       └── db/migration/              ← migrations Flyway (V1__..., V2__...)
│   └── test/
│       ├── java/com/moneyx/               ← espelha a estrutura de main
│       └── resources/config/application.properties ← propriedades EXTRAS dos testes (ex.: jobs desligados)
├── .gitignore
├── compose.yaml
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
- Não adotar arquitetura hexagonal (ports/adapters) sem necessidade concreta.
- `shared` contém apenas código transversal e nunca importa classes de módulos.
- Dependências entre módulos são unidirecionais e explícitas. Atualmente: `merchant → apikey` e
  `security → merchant, apikey`, `payment → event` e `webhook → event`. `payment` referencia o comerciante por `merchantId`.
  `event` não importa nenhum módulo de negócio: recebe tipo, recurso e retrato prontos. Nenhum módulo de negócio importa `security`, exceto a camada `api`
  para ler o `AuthenticatedMerchant` (via `@AuthenticationPrincipal`).
  `apikey` nunca importa `merchant`; referencia o comerciante apenas por `merchantId` (UUID).
  Entidades de módulos diferentes não usam `@ManyToOne` entre si; a integridade é garantida por FK no banco.
- Respostas que contêm segredos usam `Cache-Control: no-store`, e objetos que carregam segredos
  sobrescrevem `toString()` para não vazá-los em logs.

### Segurança (Spring Security)

- `security/SecurityConfig` define a única `SecurityFilterChain`: stateless, CSRF/httpBasic/formLogin/logout
  desabilitados. Rotas públicas: `POST /v1/merchants`, `GET /actuator/health` e o despacho interno `ERROR`.
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

### Convenções da API (padrão de gateways: Stripe, Mercado Pago)

- Listas são devolvidas em envelope `{"data": [...]}`, nunca como array solto (permite paginação futura).
- Listas paginadas usam cursor: `?limit=&startingAfter=<id>` e resposta `{"data": [...], "hasMore": bool}`.
- Erros de regra de negócio incluem a propriedade `code` (snake_case, estável, para máquinas).
  Atuais: `api_key_limit_reached`, `last_active_api_key`, `invalid_idempotency_key`, `idempotency_key_reused`,
  `payment_not_pending`, `payment_expired`, `concurrent_update`, `invalid_starting_after`,
  `webhook_endpoint_limit_reached`, `invalid_webhook_url`. O `detail` é para humanos e pode mudar.
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
- `GET /v1/api-keys` → `{"data": [{id, prefix, active, createdAt, revokedAt}]}`, sem segredo nem hash.
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
- Aplicadas: V1 a V9. A próxima é `V10__...`.

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

Executar todos os testes (requer o container PostgreSQL ativo e `POSTGRES_PASSWORD` definido):

```powershell
.\mvnw.cmd test
```

Nunca criar `src/test/resources/application.properties` (esconderia o principal); propriedades de teste ficam em
`src/test/resources/config/application.properties`, que é carregado além do principal.

Os testes de integração utilizam atualmente o banco de desenvolvimento, com rollback ao final de cada teste.
Avaliar Testcontainers na Fase 6 para isolar o banco de testes.

## 7. Infraestrutura PostgreSQL

O banco de dados é executado em um container Docker exclusivo do MoneyX.

### Configuração atual

- Container: `moneyx-postgres`
- Imagem: `postgres:17-alpine`
- Banco: `moneyx`
- Usuário: `moneyx_user`
- Porta: `5432`
- Volume: `moneyx_moneyx_postgres_data`
- Arquivo de configuração: `compose.yaml`
- Credenciais: `.env`

### Segurança das credenciais

O arquivo `.env` contém a senha gerada aleatoriamente.

O `.gitignore` deve ignorar:

```gitignore
.env
.env.*
!.env.example
```

Nunca imprimir ou compartilhar o conteúdo do `.env`.

### Inicialização do banco

```powershell
docker compose up -d postgres
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

As duas precisam estar definidas na sessão em que a aplicação for iniciada. **Sem `MONEYX_WEBHOOK_ENCRYPTION_KEY` a
aplicação não sobe** (de propósito). Os testes usam uma chave fixa de teste (`src/test/resources/config/application.properties`)
e só precisam de `POSTGRES_PASSWORD`.

### Executar aplicação

```powershell
.\mvnw.cmd spring-boot:run
```

### Endereços locais

- Aplicação: http://localhost:8080
- Healthcheck: http://localhost:8080/actuator/health
- PostgreSQL: localhost:5432

### Healthcheck HTTP

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
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
  Rate limiting no cadastro previsto para a Fase 6. Chaves `mx_live_` exigirão verificação (e-mail/KYC) na Fase 7.

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
  Limite acumulado por período é controle de risco/antifraude, fora do escopo da v1.
- Estados: `PENDING` → `PAID` | `CANCELED` | `EXPIRED` (todos finais). Reembolso fora da v1.
- Pagar após o prazo é proibido mesmo se o status ainda for `PENDING` (o job de expiração pode atrasar).
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
- **Reavaliar na Fase 7:** com provedor Pix real (chamada externa fora da transação) será preciso estado
  `IN_PROGRESS`, 409 e recuperação (padrão de "recovery points" do Stripe).
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
- **Sandbox:** recebedor fictício (`PixCharge.SANDBOX_*`; chave do exemplo do manual, não registrada no DICT,
  portanto não pagável de verdade). Na Fase 7 o BR Code (dinâmico) virá do PSP autorizado.
- Só o texto é devolvido (sem imagem do QR); o comerciante gera a imagem a partir do `copyPaste`.
- `POST /v1/sandbox/payments/{id}/pay` simula o pagamento: 200 (PAID); 404 se não existir/for de outro comerciante;
  409 `payment_not_pending` (com `paymentStatus`) ou `payment_expired`. Bloquear para chaves `mx_live_` na Fase 7.
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
- Comprovado por mutação (6 bugs plantados, todos detectados) e por HTTP real com pagar × cancelar simultâneos.

### Fase 5 — Eventos e webhooks

- [x] Modelar eventos.
- [x] Avaliar padrão Outbox (adotado: tabela `events` gravada na transação da mudança de estado).
- [x] Cadastro de endpoints de webhook (com linha do tempo de auditoria).
- [x] Implementar webhooks (entrega a partir do Outbox).
- [x] Implementar assinatura.
- [ ] Implementar tentativas e retentativas.
- [ ] Introduzir RabbitMQ quando necessário (hoje NÃO é: Outbox + job no PostgreSQL resolve).

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
  aparece depois do cadastro. Rotação da chave de cifragem: Fase 6.
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

### Fase 6 — Qualidade e documentação

- [ ] Rate limiting no cadastro público de comerciantes (avaliar Redis).
- [ ] Avaliar limite acumulado de volume por comerciante (dia/mês) como controle de risco.
- [ ] Expandir testes automatizados.
- [ ] Documentar API.
- [ ] Criar Docker Compose completo.
- [ ] Criar pipeline CI.
- [ ] Revisar segurança e observabilidade.

### Fase 7 — Evolução

- [ ] Integrar provedor real em ambiente sandbox.
- [ ] Desenvolver dashboard.
- [ ] Avaliar requisitos legais e regulatórios.
- [ ] Avaliar viabilidade comercial.
- [ ] Preparar eventual MVP.

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
