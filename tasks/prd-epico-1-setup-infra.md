# PRD: Épico 1 — Setup e Infraestrutura (Base)

## Introdução

Este épico inicializa a base técnica do projeto **Sessão a Dois** (rastreador de mídia para casais). O objetivo é deixar o backend (`api/`) e o ambiente de desenvolvimento prontos para receber as regras de negócio dos épicos seguintes, sem ainda implementar nenhuma feature de domínio.

Hoje o repositório já contém um projeto Spring Boot **4.1.0 / Java 21** gerado (`api/`), mas com apenas as dependências mínimas (`spring-boot-starter` + test), pacote base `com.lf.sessao_a_dois`, e **sem** ambiente Docker de desenvolvimento nem Dockerfile de produção. Este épico corrige essa base.

Três decisões foram tomadas para este épico:
1. **Pacote base:** renomear de `com.lf.sessao_a_dois` para `com.app`, alinhando ao `docs/ARCHITECTURE.md` (features futuras: `com.app.user`, `com.app.security`, etc.).
2. **PostgreSQL:** imagem `postgres:17-alpine` (multi-arch, roda nativo em ARM64/Apple Silicon).
3. **Security no boot:** como `spring-boot-starter-security` bloqueia todos os endpoints por padrão, será criado um `SecurityConfig` temporário `permit-all` + endpoint de health, para o app subir "verde". O JWT real fica para o Épico 2.

## Goals

- Adicionar todas as dependências base necessárias ao `pom.xml` (Web, Data JPA, PostgreSQL, Validation, Security, WebSocket).
- Padronizar o pacote base como `com.app`.
- Fornecer um ambiente de desenvolvimento local reproduzível via Docker (PostgreSQL + PgAdmin) otimizado para ARM64.
- Garantir que a API conecta ao PostgreSQL e sobe sem erros (health check respondendo `200`).
- Produzir um `Dockerfile` multi-stage (build Maven + runtime JRE 21) que gere uma imagem executável da API.
- Não introduzir nenhuma regra de negócio (usuários, casais, mídia) neste épico.

## User Stories

### US-001: Renomear pacote base para `com.app` e adicionar dependências base
**Description:** Como desenvolvedor, quero o pacote base padronizado como `com.app` e todas as dependências base no `pom.xml`, para que os próximos épicos possam criar features (`com.app.user`, `com.app.security`, etc.) sobre uma fundação consistente.

**Acceptance Criteria:**
- [ ] Pacote base renomeado de `com.lf.sessao_a_dois` para `com.app` (classe main movida para `src/main/java/com/app/SessaoADoisApplication.java` e classe de teste equivalente movida para o mesmo pacote).
- [ ] `pom.xml` inclui as dependências: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-security`, `spring-boot-starter-websocket`.
- [ ] `pom.xml` inclui o driver `org.postgresql:postgresql` com `scope` runtime.
- [ ] `groupId` permanece `com.lf`; apenas o pacote Java base muda para `com.app`.
- [ ] `./mvnw -q -DskipTests package` compila com sucesso.
- [ ] `./mvnw test` passa (o teste de contexto `SessaoADoisApplicationTests` sobe sem erro — depende da US-002 para o datasource).

### US-002: Configurar datasource, SecurityConfig temporário e endpoint de health
**Description:** Como desenvolvedor, quero a API configurada para conectar ao PostgreSQL e com um endpoint de health público, para validar que a base sobe e conecta antes de implementar qualquer feature.

**Acceptance Criteria:**
- [ ] `application.properties` (ou `application.yml`) configura `spring.datasource.url/username/password` lendo variáveis de ambiente com defaults para dev (ex.: `${DB_URL:jdbc:postgresql://localhost:5432/sessao}`).
- [ ] `spring.jpa.hibernate.ddl-auto=update` (ou `validate`) definido para dev; sem entidades de domínio ainda.
- [ ] Existe uma classe `com.app.security.SecurityConfig` que libera (`permitAll`) todas as rotas temporariamente e desabilita o form login/basic para não bloquear o boot (comentário indicando que será substituída no Épico 2).
- [ ] Existe um endpoint público `GET /api/health` que retorna `200` com um corpo simples (ex.: `{"status":"UP"}`) — pode ser um controller próprio ou o actuator health, desde que acessível sem autenticação.
- [ ] Com o PostgreSQL do docker-compose rodando, `./mvnw spring-boot:run` sobe a aplicação sem erros e `GET /api/health` responde `200`.

### US-003: Criar `docker-compose-dev.yml` com PostgreSQL e PgAdmin (ARM64)
**Description:** Como desenvolvedor, quero subir PostgreSQL e PgAdmin com um único comando, para ter um banco local reproduzível sem instalar nada na máquina.

**Acceptance Criteria:**
- [ ] Arquivo `docker-compose-dev.yml` criado na raiz do repositório.
- [ ] Serviço `postgres` usa a imagem `postgres:17-alpine`, expõe a porta `5432`, define `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` e um volume nomeado para persistência.
- [ ] Serviço `pgadmin` (imagem `dpage/pgadmin4`) exposto em uma porta de host (ex.: `5050`) com credenciais default para dev.
- [ ] `healthcheck` no serviço `postgres` usando `pg_isready`.
- [ ] Variáveis de conexão do compose coincidem com os defaults do `application.properties` da US-002 (mesmo db/user/senha), de modo que a API conecta sem ajustes extras.
- [ ] `docker compose -f docker-compose-dev.yml up -d` sobe ambos os serviços e o `postgres` fica `healthy`.

### US-004: Criar `Dockerfile` multi-stage da API (build Maven + runtime JRE 21)
**Description:** Como desenvolvedor, quero uma imagem Docker enxuta e executável da API, para preparar o deploy e validar o build empacotado.

**Acceptance Criteria:**
- [ ] `api/Dockerfile` multi-stage: estágio de **build** usando imagem Maven+JDK 21 que roda `./mvnw -DskipTests package`; estágio de **runtime** usando imagem base **JRE 21** enxuta (ex.: `eclipse-temurin:21-jre`).
- [ ] O estágio de runtime copia apenas o `.jar` gerado e define o `ENTRYPOINT` para executá-lo.
- [ ] Camadas ordenadas para aproveitar cache de dependências (copiar `pom.xml`/`mvnw` e baixar dependências antes de copiar o código-fonte).
- [ ] `EXPOSE` da porta da aplicação (ex.: `8080`).
- [ ] `docker build -t sessao-api ./api` conclui com sucesso.
- [ ] `docker run` da imagem sobe a aplicação (aponta para um PostgreSQL alcançável via variáveis de ambiente).

## Functional Requirements

- FR-1: O `pom.xml` deve declarar as dependências base: Web, Data JPA, PostgreSQL (driver), Validation, Security e WebSocket.
- FR-2: O pacote base Java do projeto deve ser `com.app`, com a classe principal em `com.app.SessaoADoisApplication`.
- FR-3: A configuração de datasource deve ler credenciais de variáveis de ambiente, com defaults válidos para desenvolvimento local.
- FR-4: Deve existir um `SecurityConfig` temporário liberando todas as rotas, para que a inclusão do Spring Security não bloqueie o boot durante o Épico 1.
- FR-5: Deve existir um endpoint público de health (`GET /api/health`) retornando `200`.
- FR-6: O `docker-compose-dev.yml` na raiz deve prover PostgreSQL (`postgres:17-alpine`) com volume persistente e healthcheck, e PgAdmin.
- FR-7: Os defaults de conexão da API e as variáveis do `docker-compose-dev.yml` devem ser consistentes entre si.
- FR-8: O `api/Dockerfile` deve ser multi-stage: build com Maven/JDK 21 e runtime com JRE 21, produzindo uma imagem executável.

## Non-Goals (Out of Scope)

- Nenhuma entidade de domínio (`User`, `Couple`, `MediaTrack`, `UserReview`, `MatchLike`) — isso é dos Épicos 2, 4 e 5.
- Nenhuma lógica de autenticação/JWT real — o `SecurityConfig` deste épico é apenas um placeholder `permit-all` (Épico 2 substitui).
- Nenhuma integração com a API do TMDB (Épico 3).
- Nenhum `docker-compose-prod.yml`, configuração de Nginx, SSL ou script de deploy (Épico 7).
- Nenhuma tela ou alteração no frontend (`client/`).
- Nenhum endpoint de negócio além do health check.

## Technical Considerations

- **Spring Boot 4.1.0 / Java 21** já estão definidos no `pom.xml`; manter essas versões.
- `groupId` Maven permanece `com.lf`; apenas o pacote Java base muda para `com.app` (não confundir os dois).
- Ao renomear o pacote, mover **tanto** a classe main quanto a classe de teste, e ajustar quaisquer imports/`package` declarados.
- O Spring Security, ao ser adicionado, bloqueia tudo por padrão e gera uma senha aleatória no log — por isso o `SecurityConfig` `permitAll` é necessário já neste épico.
- Preferir `postgres:17-alpine` (multi-arch) para rodar nativo em ARM64 (Apple Silicon), evitando emulação.
- O `Dockerfile` deve usar o wrapper `./mvnw` já presente em `api/` para não depender de Maven instalado.
- Considerar `.dockerignore` em `api/` para evitar copiar `target/` e artefatos locais para o contexto de build.
- Os defaults de dev (db `sessao`, user/senha) devem ser idênticos entre `application.properties` e `docker-compose-dev.yml`.

## Success Metrics

- `docker compose -f docker-compose-dev.yml up -d` deixa o PostgreSQL `healthy` e o PgAdmin acessível.
- `./mvnw spring-boot:run` sobe a API conectada ao banco, e `GET /api/health` responde `200`.
- `./mvnw -DskipTests package` e `docker build ./api` concluem sem erro.
- Estrutura de pacotes pronta para receber `com.app.user`, `com.app.security`, etc. no Épico 2.

## Open Questions

- O health check deve ser um controller próprio (`GET /api/health`) ou usar o Spring Boot Actuator (`/actuator/health`)? (Assumido: controller próprio em `/api/health`, para não adicionar a dependência de actuator neste épico — ajustar se preferirem actuator.)
- Nome/porta exatos do PgAdmin no host (assumido `5050`) e credenciais default — confirmar se há preferência.
- Confirmar o nome do banco de dev (assumido `sessao`).
