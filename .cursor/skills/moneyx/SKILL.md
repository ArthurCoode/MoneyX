
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
| Flyway | Versionamento do banco, previsto |
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

## 6. Ambiente atual

### Sistema operacional

Windows 11.

### IDE

Cursor.

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

### Estrutura inicial

```text
MoneyX/
├── .cursor/
│   └── skills/
│       └── moneyx/
│           └── SKILL.md
├── .mvn/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── moneyx/
│   │   └── resources/
│   │       └── application.properties
│   └── test/
├── .gitignore
├── compose.yaml
├── mvnw
├── mvnw.cmd
└── pom.xml
```

A estrutura poderá evoluir conforme os módulos forem definidos.

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

A variável `POSTGRES_PASSWORD` é disponibilizada na sessão do PowerShell a partir do `.env`:

```powershell
$env:POSTGRES_PASSWORD = ((Get-Content .env | Where-Object { $_ -match '^POSTGRES_PASSWORD=' }) -split '=', 2)[1]
```

A variável precisa estar definida na sessão em que a aplicação for iniciada.

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
- [ ] Registrar commit da infraestrutura.
- [ ] Documentar a skill do projeto.

### Fase 2 — Domínio e comerciantes

- [ ] Estudar o conceito de Merchant.
- [ ] Definir dados e regras do estabelecimento.
- [ ] Criar entidade Merchant.
- [ ] Criar repositório.
- [ ] Criar serviço.
- [ ] Criar DTOs e validações.
- [ ] Implementar endpoints.
- [ ] Criar testes.

### Fase 3 — Segurança

- [ ] Implementar autenticação.
- [ ] Criar API Keys.
- [ ] Armazenar hashes.
- [ ] Implementar isolamento por Merchant.
- [ ] Criar testes de autorização.

### Fase 4 — Pagamentos

- [ ] Definir modelo de Payment.
- [ ] Definir estados.
- [ ] Implementar criação de pagamentos.
- [ ] Implementar idempotência.
- [ ] Simular Pix.
- [ ] Implementar consulta.
- [ ] Implementar cancelamento.
- [ ] Criar testes de transição de estado.

### Fase 5 — Eventos e webhooks

- [ ] Modelar eventos.
- [ ] Implementar webhooks.
- [ ] Implementar assinatura.
- [ ] Implementar tentativas e retentativas.
- [ ] Avaliar padrão Outbox.
- [ ] Introduzir RabbitMQ quando necessário.

### Fase 6 — Qualidade e documentação

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

- Utilizar branches para funcionalidades.
- Não trabalhar diretamente na `main` para mudanças relevantes.
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
