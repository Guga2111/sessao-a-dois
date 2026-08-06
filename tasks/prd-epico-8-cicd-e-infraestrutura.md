# PRD: Épico 8 — CI/CD e Infraestrutura

## Introdução

Os Épicos 1 a 7 endureceram a aplicação. Este épico endurece **o caminho até
produção** — o que roda antes do merge, o que roda no merge, e o que a VPS
executa depois.

Boa parte da T8.1 do `POST-MVP-TASK.md` foi resolvida de raspão pelos épicos
anteriores. O escopo abaixo é o **estado real do repositório**, levantado em
2026-08-06, não o texto literal do backlog de 2026-08-02:

| Task do backlog | Estado real hoje | Sobra para este épico |
|---|---|---|
| **T8.1** — CI completo e testes faltantes | `JwtServiceTest`, `SecurityConfigTest`, `JwtHandshakeInterceptorTest` existem (Épicos 1/4/5). O `ci.yml` já tem o job `frontend` (typecheck + lint + build) e já dispara em PR para `dev` **e** `main`. JaCoCo `check` já falha abaixo de 90% de linha (US-012 do Épico 5). | Só o teste do `HealthController` — a única classe da tabela original que continua sem cobertura. |
| **T8.2** — Container não-root e healthcheck | `api/Dockerfile` não define `USER` (processo Java roda como **root**) nem `HEALTHCHECK`. | A task inteira. |
| **T8.3** — Segredos no `deploy.sh` | Resolvida em 2026-08-06: o heredoc para `/tmp` foi removido (o `.env` vai por `scp` direto), e `VPS_IP`/`VPS_USER`/`ENV_FILE` são parametrizáveis. Junto veio um pipeline de CD novo, que **não estava no backlog**: `.github/workflows/deploy.yml` executa o `scripts/deploy.sh` a cada push na `main`, reusando o `ci.yml` via `workflow_call`, com `concurrency` e smoke test público. | Só a validação fim a fim: cadastrar os secrets no GitHub e ver o primeiro deploy automático passar. |
| **T8.4** — Varredura de dependências | Não existe `.github/dependabot.yml`. Não há `bun audit` no CI. jjwt continua pinado à mão em `0.12.6` (3 declarações no `api/pom.xml`). | A task inteira, menos o `dependency-check-maven` (ver decisão E8.1). |
| **T8.5** — Remover `SPRING_FLYWAY_BASELINE_ON_MIGRATE` | `docker-compose-prod.yml:12` ainda fixa `"true"`. | A task inteira. |

**Estado do código na abertura deste épico:** Épicos 1–7 concluídos. Migrations
até `V6__add_invite_code_expiry.sql` (a próxima seria a V7 — este épico **não**
cria nenhuma). 47 arquivos de teste no backend, cobertura de linha acima de 90%
com o gate do JaCoCo ativo. O frontend não tem runner de teste (nem vitest nem
testing-library) — e isso continua fora de escopo.

## Goals

- Fechar a última lacuna de cobertura da T8.1 (`HealthController`).
- Tirar o processo Java de `root` **dentro do container** e dar ao Docker um
  sinal de saúde real, sem quebrar a trilha de auditoria que hoje escreve num
  bind mount da VPS.
- Ganhar vigilância automática sobre dependências vulneráveis e desatualizadas,
  nos dois ecossistemas (Maven e npm), com ruído de PR sob controle.
- Eliminar o `SPRING_FLYWAY_BASELINE_ON_MIGRATE` permanentemente ligado, para
  que uma migration que não aplique **falhe alto** em vez de ser silenciosamente
  marcada como baseline.
- Colocar o pipeline de CD criado em 2026-08-06 em operação comprovada: secrets
  cadastrados, primeiro deploy automático verde, rollback documentado.

## Decisões tomadas para este épico

Complementam as decisões D1–D12 do `POST-MVP-TASK.md`, que continuam vinculantes
— em especial **D9** (o primeiro deploy com Flyway já ocorreu em produção, o que
desbloqueia a T8.5) e **D10** (o usuário de deploy **na VPS** continua sendo
`root`).

| # | Questão | Decisão | Consequência |
|---|---------|---------|--------------|
| E8.1 | `dependency-check-maven` no CI do backend | **Não incluir.** | O OWASP dependency-check hoje exige NVD API key, o primeiro download da base leva 10–20 minutos e produz muito falso positivo num app Spring Boot. Dependabot cobre o essencial para Maven. Se um dia houver necessidade regulatória, vira task própria. |
| E8.2 | Severidade que quebra o CI no `bun audit` | **Alta e crítica falham o build**, com lista de `--ignore` explícita e comentada para os advisories que não alcançam o usuário. | É o que a T8.4 pede literalmente. Um advisory sem correção disponível — ou sem risco real neste app — é destravado com `--ignore=<id>` **comentado no workflow**, nunca baixando o limiar global. |
| E8.3 | Pin manual do jjwt `0.12.6` | **Verificar o BOM primeiro** (`mvn dependency:tree`/`dependency:list`). Se o BOM do Spring Boot já gerencia `io.jsonwebtoken`, remover as 3 versões do `pom.xml`; se não gerencia, manter o pin **com comentário** explicando por quê. | A decisão só é tomável com o resultado do comando em mãos — a US-006 executa a verificação e aplica o caminho correspondente. |
| E8.4 | Ações que dependem de acesso externo (Supabase, painel do GitHub, VPS) | **Entram como user stories de operação**, com checklist verificável — mesmo modelo da US-010 do Épico 1. | US-007 e US-009 não são implementáveis por um agente sozinho: exigem credencial de produção. Ficam explicitamente marcadas como **operacionais**. |
| E8.5 | UID do usuário do container | **Fixo e explícito (`10001`)**, não o próximo livre que o `adduser -S` escolher. | O bind mount `/var/lib/sessao-a-dois/security-audit-logs` da VPS pertence a `root`. Sem UID fixo e sem `chown` correspondente, o Logback perde a permissão de escrita e a trilha de auditoria do Épico 5 morre silenciosamente no primeiro deploy. |
| E8.6 | Agrupamento do Dependabot | **Um PR agrupado por ecossistema, semanal**, separando atualizações de segurança das de rotina. | Com 2 ecossistemas e um único mantenedor, PR por dependência seria ruído puro. |
| E8.7 | Proteção da branch `main` | **Exigir PR + checks de CI verdes** para merge. | Fecha o push direto na `main`. O job `tests` do `deploy.yml` deixa de ser a única rede e passa a ser a segunda. Hotfix também passa por PR. |
| E8.8 | Retenção de imagens na VPS | **Manter 3 (nenhuma mudança no `deploy.sh`).** O que muda é a documentação. | O rollback por retag só funciona num caso estreito: deploy **backend-only, sem migration nova**. Com migration, o jar antigo nem sobe (o Flyway falha com *"detected applied migration not resolved locally"*); com mudança de frontend, não existe artefato antigo — o `deploy.sh` sobrescreve `/var/www/sessaoadois/` por `scp`, sem versionar. Guardar mais imagens não amplia a cobertura, só ocupa disco. O rollback **oficial** é `git revert` + merge. |
| E8.9 | Como separar dependência de build de dependência de produção no audit | **Via lista de `--ignore` explícita, não por flag.** | `bun audit` (1.3.14) **não tem** `--prod`/`--production`; só existem `--json`, `--audit-level` e `--ignore=<val>`. Separar por tipo exigiria um filtro próprio sobre o `--json`, mapeando cada caminho de dependência à raiz — script para manter. Com o volume atual (6 advisories altos, 5 deles só de `shadcn`/`eslint`), a lista comentada é mais barata e mais auditável. Revisitar se a lista passar de ~10 entradas. |

## User Stories

---

### US-001: Teste do `HealthController`

**Description:** As a mantenedor, I want o endpoint de health coberto por teste
so that o probe usado pelo `HEALTHCHECK` do Docker e pelo smoke test do CD não
possa quebrar sem o CI perceber.

**Acceptance Criteria:**
- [ ] Existe `api/src/test/java/com/app/HealthControllerTest.java` com `@WebMvcTest(HealthController.class)`.
- [ ] Teste afirma `GET /api/health` → status 200 e corpo JSON `{"status":"UP"}`.
- [ ] Teste afirma que o endpoint responde **sem autenticação** (é `permitAll` em `SecurityConfig`) — a rota é consumida pelo Docker e pelo runner do CD, que não têm cookie de sessão.
- [ ] `./mvnw test` passa e o gate do JaCoCo (90% de linha) continua verde.

---

### US-002: Container roda como não-root e expõe healthcheck

**Description:** As a operador da VPS, I want o processo Java rodando sem
privilégio e com sinal de saúde no Docker so that um escape de container não
entregue `root` no host e um `docker ps` mostre imediatamente se a API está viva.

**Acceptance Criteria:**
- [ ] No estágio de runtime do `api/Dockerfile`: grupo e usuário criados com **UID/GID fixos** (`addgroup -g 10001 -S app && adduser -u 10001 -S app -G app`), seguido de `USER app`.
- [ ] `HEALTHCHECK --interval=30s --timeout=3s --start-period=40s CMD wget -qO- http://localhost:8080/api/health || exit 1` (porta **interna** 8080, não a 8085 mapeada pelo compose).
- [ ] O `app.jar` é copiado com dono `app` (`COPY --from=build --chown=app:app`), senão o processo não-root não consegue lê-lo dependendo do umask da imagem base.
- [ ] **A trilha de auditoria continua escrevendo.** `docs/DEPLOY.md` ganha o passo `ssh root@<vps> "chown -R 10001:10001 /var/lib/sessao-a-dois/security-audit-logs"`, a ser executado **antes** do primeiro deploy com esta mudança — o bind mount pertence a `root` e o usuário `app` não conseguiria criar `security-audit.log` nele.
- [ ] A ordem de cache das camadas do multi-stage é preservada: `COPY .mvn/`, `mvnw`, `pom.xml` e `dependency:go-offline` continuam **antes** de `COPY src/`.
- [ ] Verificado localmente: `docker build` seguido de `docker run --rm --entrypoint sh sessao-api:latest -c whoami` **não** retorna `root`.
- [ ] Verificado localmente: com o container de pé, `docker ps` mostra a coluna de status com `(healthy)` após o `start-period`.
- [ ] O tamanho da imagem não cresce mais que 5MB em relação à anterior (`docker images sessao-api`).

---

### US-003: Dependabot para Maven e npm

**Description:** As a mantenedor, I want PRs automáticos de atualização de
dependência so that uma CVE numa dependência do backend ou do frontend não fique
meses sem ser notada.

**Acceptance Criteria:**
- [ ] Existe `.github/dependabot.yml` com `version: 2` e três `updates`: `maven` em `/api`, `npm` em `/client` e `github-actions` em `/` (os workflows também têm dependências pinadas).
- [ ] Cadência **semanal** em todos, com `open-pull-requests-limit` baixo (≤ 5) por ecossistema.
- [ ] `groups` configurado para agrupar patch/minor num único PR por ecossistema, deixando major em PR separado (uma atualização major do Spring Boot merece revisão isolada).
- [ ] `target-branch: dev` — os PRs abrem contra `dev`, nunca direto contra `main`, respeitando o fluxo `dev` → `main` do CD.
- [ ] O arquivo é validado pelo GitHub sem erro (aba **Insights > Dependency graph > Dependabot** não mostra erro de parsing).

---

### US-004: Limpar o que der de vulnerabilidade e triar o resto

**Description:** As a mantenedor, I want a árvore de dependências do frontend
atualizada e cada advisory restante classificado so that o gate da US-005 possa
ser ligado sem deixar o CI vermelho no primeiro commit.

> **Contexto medido em 2026-08-06:** `bun audit` no `client/` reporta **15
> advisories, 6 de severidade alta**. Origem: `ip-address`, `brace-expansion`
> (×2), `undici` e `fast-uri` vêm de `shadcn` e `eslint` (**devDependencies** —
> não entram no bundle do usuário); só `react-router` (via `react-router-dom@7.18.1`)
> é dependência de produção. Ligar o gate antes desta story trava o CI e o deploy.

**Acceptance Criteria:**
- [ ] `bun update` executado em `client/` (sem `--latest`, para não puxar breaking change), `bun.lock` commitado, e `bun run typecheck && bun run lint && bun run build` passam depois.
- [ ] Nova execução de `bun audit` registrada no PR: quantos advisories restam, por severidade.
- [ ] Cada advisory **alto ou crítico** que sobrou está classificado numa tabela no PR ou em `client/CLAUDE.md`, com: pacote, caminho de dependência, se é `dependencies` ou `devDependencies`, e se há versão corrigida disponível.
- [ ] O caso do `react-router` está decidido e registrado: o advisory `GHSA-qwww-vcr4-c8h2` é *RSC Mode CSRF Bypass*, e **este app é uma SPA em Vite que não usa React Server Components** — logo o vetor não existe aqui. A correção seria o major `7.x → 8.x`, que **não** entra neste épico (fica para um PR do Dependabot da US-003, revisado à parte).
- [ ] Nenhuma dependência de **produção** com advisory alto/crítico fica sem decisão explícita (corrigir agora, ou ignorar com justificativa escrita).
- [ ] `bun run typecheck`, `bun run lint` e `bun run build` passam.

---

### US-005: Ligar o gate do `bun audit` no CI

**Description:** As a mantenedor, I want o CI barrando dependência do frontend
com vulnerabilidade alta ou crítica so that ela não chegue a produção pelo merge
na `main`.

**Depende de:** US-004 (sem a triagem, este step deixa o CI permanentemente vermelho).

**Acceptance Criteria:**
- [ ] O job `frontend` de `.github/workflows/ci.yml` ganha um step `bun audit --audit-level=high` posicionado **depois** do `bun install` e **antes** do `build`.
- [ ] Severidade moderada ou baixa **não** quebra o build; alta e crítica quebram.
- [ ] Os advisories triados como sem risco real na US-004 entram como `--ignore=<id>` no próprio step, **cada um com comentário** dizendo o pacote, por que não alcança o usuário (ex.: "só devDependency da CLI do shadcn") e o que faria revisitar a decisão.
- [ ] O step roda também quando o `ci.yml` é chamado via `workflow_call` pelo `deploy.yml` — ou seja, o deploy para se aparecer vulnerabilidade alta nova.
- [ ] Verificado na prática: com a lista de ignores em vigor, o job `frontend` passa no estado atual do `client/`.
- [ ] Verificado na prática que o gate **funciona**: removendo temporariamente um dos `--ignore`, o step falha (não presumir — testar e desfazer).

---

### US-006: Resolver o pin manual do jjwt

**Description:** As a mantenedor, I want saber se o `0.12.6` do jjwt é um pin
necessário ou apenas herança so that a biblioteca que assina os tokens de sessão
não defase em silêncio.

**Acceptance Criteria:**
- [ ] Executado `./mvnw dependency:tree -Dincludes=io.jsonwebtoken` (ou `help:effective-pom`) e registrado no PR qual versão o BOM do Spring Boot fornece, se fornecer alguma.
- [ ] **Se o BOM gerencia:** as 3 tags `<version>0.12.6</version>` de `jjwt-api`, `jjwt-impl` e `jjwt-jackson` são removidas do `api/pom.xml`.
- [ ] **Se o BOM não gerencia:** as 3 declarações passam a usar uma property única (`<jjwt.version>`) em `<properties>`, com comentário explicando que o jjwt está fora do BOM do Spring Boot e por isso precisa de bump manual — e que o Dependabot da US-003 é quem passa a avisar.
- [ ] Em qualquer dos dois caminhos: `./mvnw test` passa e a versão efetiva do jjwt não **regride** em relação à `0.12.6`.
- [ ] `api/CLAUDE.md` menciona a situação resolvida, para que a próxima pessoa não repita a investigação.

---

### US-007 *(operacional)*: Conferir o `flyway_schema_history` em produção

**Description:** As a operador, I want confirmar que todas as migrations foram
de fato aplicadas em produção so that remover o `baseline-on-migrate` seja
seguro e não transforme o próximo deploy num incidente.

> **Story operacional (E8.4).** Exige credencial do Supabase. Não é executável
> por um agente — é um checklist para o mantenedor, e **bloqueia a US-008**.

**Acceptance Criteria:**
- [ ] Executado no banco de produção: `SELECT installed_rank, version, description, type, success FROM flyway_schema_history ORDER BY installed_rank;`
- [ ] Confirmado que existem linhas para **V1 até V6** e que **todas** têm `success = true`.
- [ ] Confirmado que nenhuma linha tem `type = 'BASELINE'` para uma migration que deveria ter sido de fato executada (a linha de baseline legítima do V1 é esperada, conforme D9).
- [ ] O resultado (a saída da query, sem dado sensível) fica registrado no PR da US-008 ou no `docs/FLYWAY.md`, com a data da verificação.
- [ ] Se qualquer migration estiver faltando ou com `success = false`: **parar**, não seguir para a US-008, e abrir uma task de correção.

---

### US-008: Remover `SPRING_FLYWAY_BASELINE_ON_MIGRATE` do compose

**Description:** As a mantenedor, I want que uma migration que não aplique
quebre o deploy visivelmente so that um schema divergente nunca seja mascarado
como baseline.

**Depende de:** US-007.

**Acceptance Criteria:**
- [ ] A linha `SPRING_FLYWAY_BASELINE_ON_MIGRATE: "true"` não existe mais em `docker-compose-prod.yml`.
- [ ] `docs/DEPLOY.md` documenta como passá-la pontualmente, caso um ambiente novo precise: `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true docker compose -f docker-compose-prod.yml up -d`.
- [ ] `docs/FLYWAY.md` registra que a variável foi removida, em que data, e com base em qual verificação (a da US-006).
- [ ] O comentário de `api/src/main/resources/application.properties` que hoje explica o "apenas no primeiro deploy" continua coerente com o estado novo (ajustar se ficou contraditório).
- [ ] O deploy seguinte sobe normalmente e o log do Flyway mostra `Successfully validated 6 migrations` (ou `No migration necessary`), sem nenhuma linha de baseline nova.

---

### US-009 *(operacional)*: Colocar o pipeline de CD em operação

**Description:** As a mantenedor, I want o deploy acontecendo sozinho no merge
de `dev` para `main` so that eu pare de rodar `deploy.sh` à mão e o processo
deixe de depender da minha máquina.

> **Story operacional (E8.4).** Exige acesso ao painel do GitHub e à VPS. Fecha o
> resíduo da T8.3 e valida o `deploy.yml` criado em 2026-08-06.

**Acceptance Criteria:**
- [ ] Par de chaves de deploy gerado (`ssh-keygen -t ed25519 -f ~/.ssh/sessao_deploy -N ""`), pública autorizada na VPS, e `ssh -i ~/.ssh/sessao_deploy root@<vps> "echo ok"` funcionando.
- [ ] Secrets cadastrados em **Settings > Secrets and variables > Actions**: `VPS_SSH_KEY`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `TMDB_API_KEY` e, preferencialmente, `VPS_SSH_KNOWN_HOSTS`.
- [ ] `chown -R 10001:10001 /var/lib/sessao-a-dois/security-audit-logs` executado na VPS (pré-requisito da US-002 — sem isso o primeiro deploy com container não-root perde a trilha de auditoria).
- [ ] Execução manual do workflow (**Actions > CD - Deploy para producao > Run workflow**) termina verde, incluindo o job `tests` e o smoke test do `/api/health`.
- [ ] O site responde e o login funciona após esse deploy.
- [ ] Um merge real de `dev` → `main` dispara o pipeline sozinho, sem intervenção.
- [ ] `docs/DEPLOY.md` ganha uma seção de **rollback** com os dois caminhos e seus limites (decisão E8.8):
  - **Oficial:** `git revert` do commit na `main` + merge — o pipeline redeploya sozinho (~10 min). Único caminho que funciona sempre.
  - **Emergencial (retag na VPS):** `docker tag sessao-api:<versão-antiga> sessao-api:latest` + `docker compose -f docker-compose-prod.yml up -d --force-recreate api` (~30s). Documentar explicitamente que **só serve para deploy backend-only sem migration nova** — com migration o container não sobe (Flyway: *"detected applied migration not resolved locally"*), e com mudança de frontend não há artefato antigo para voltar, porque o `scp` sobrescreve `/var/www/sessaoadois/`.
- [ ] Proteção da branch `main` configurada conforme E8.7: **Settings > Branches > main** com *Require a pull request before merging* e *Require status checks to pass* marcando os checks `Run Unit Tests (API)` e `Run Frontend Checks (client)`.
- [ ] Verificado na prática que um push direto na `main` é recusado pelo GitHub.

---

## Functional Requirements

- **FR-1:** O `HealthController` deve ter teste automatizado cobrindo status, corpo e acesso anônimo.
- **FR-2:** O container da API deve executar o processo Java como usuário não-privilegiado de UID fixo `10001`, dentro do container. O usuário de deploy **na VPS** permanece `root` (D10).
- **FR-3:** A imagem deve declarar `HEALTHCHECK` apontando para `http://localhost:8080/api/health`, com `start-period` suficiente para o boot do Spring (≥ 40s).
- **FR-4:** O diretório do bind mount da trilha de auditoria na VPS deve pertencer ao UID `10001` antes do primeiro deploy com FR-2.
- **FR-5:** O repositório deve declarar Dependabot para `maven` (`/api`), `npm` (`/client`) e `github-actions` (`/`), semanal, agrupado, com PRs contra `dev`.
- **FR-6:** O job `frontend` do CI deve executar `bun audit --audit-level=high` e falhar em severidade alta ou crítica, com os advisories sem risco real destravados por `--ignore=<id>` comentado no workflow (E8.2/E8.9).
- **FR-7:** A versão do jjwt deve estar explicitamente gerenciada — pelo BOM (sem pin) ou por property comentada — nunca por três literais espalhados.
- **FR-8:** `SPRING_FLYWAY_BASELINE_ON_MIGRATE` não deve constar do `docker-compose-prod.yml`, e a forma de passá-la pontualmente deve estar documentada.
- **FR-9:** O deploy em produção deve ser disparado por push na `main` e não deve exigir nenhuma execução manual do `scripts/deploy.sh` no fluxo normal.

## Non-Goals (Out of Scope)

- **Testes no frontend.** O `client/` continua sem vitest/testing-library. Criar a infraestrutura de teste do frontend é épico próprio, não um item de CI/CD.
- **`dependency-check-maven`** (decisão E8.1).
- **Usuário de deploy não-root na VPS** (decisão D10). A US-002 é sobre o usuário **dentro** do container.
- **Migrar a imagem para um registry (GHCR).** O transporte por `docker save` + `scp` foi confirmado em 2026-08-06 e não muda neste épico.
- **Aprovação manual no deploy.** Decidido em 2026-08-06: merge na `main` deploya direto, sem gate humano.
- **Ambiente de staging.** Não existe e não é criado aqui.
- **Nova migration.** Este épico não cria nenhum `V7__*.sql`.
- **Blue/green, canary ou rollback automático.** Rollback é `git revert` + merge.

## Technical Considerations

- **A armadilha central deste épico é o par US-002 + trilha de auditoria.** O
  Épico 5 (US-013) fez o `logback-spring.xml` escrever em
  `/var/log/sessao-a-dois/security-audit.log` dentro do container, montado a
  partir de `/var/lib/sessao-a-dois/security-audit-logs` na VPS — diretório
  criado pelo Docker como `root`. Trocar o processo para não-root **sem** o
  `chown` correspondente faz o `RollingFileAppender` falhar por permissão. O
  Logback não derruba a aplicação por causa disso: o app sobe normalmente e a
  trilha simplesmente para de existir. É exatamente o tipo de falha silenciosa
  que o anti-pattern #4 do `docs/ARCHITECTURE.md` condena — por isso o `chown`
  é critério de aceite explícito, e não uma nota de rodapé.
- **`wget` no `HEALTHCHECK`:** a imagem `eclipse-temurin:21-jre-alpine` traz o
  `wget` do BusyBox. Não é preciso instalar `curl` (o que aumentaria a imagem e
  contrariaria o critério de tamanho).
- **Porta no healthcheck:** dentro do container a API escuta na **8080**; o
  `8085` do `docker-compose-prod.yml` é só o mapeamento do host. Usar 8085 no
  `HEALTHCHECK` faria o container aparecer permanentemente `unhealthy`.
- **O `client/` já está com 6 advisories altos hoje** (medido em 2026-08-06):
  `ip-address`, `brace-expansion` (×2), `undici` e `fast-uri` chegam por
  `shadcn` e `eslint` — todos **devDependencies**, que não entram no bundle
  servido ao usuário. O único de produção é `react-router` (via
  `react-router-dom@7.18.1`), e o advisory é *RSC Mode CSRF Bypass* — RSC é
  React Server Components, que uma SPA em Vite não usa. Por isso a ordem
  importa: **US-004 (triagem) antes de US-005 (gate)**. Ligar o gate primeiro
  deixaria o CI vermelho e, por tabela, bloquearia todo deploy.
- **`bun audit` no caminho do deploy:** como o `deploy.yml` reusa o `ci.yml` via
  `workflow_call`, o step da US-005 também roda no push da `main`. É desejado —
  mas significa que um advisory novo publicado entre o PR e o merge pode barrar
  um deploy. O escape é o `--ignore` explícito, não desligar o step.
- **Dependabot e o CD:** `target-branch: dev` é obrigatório. PRs do Dependabot
  direto contra `main` disparariam deploy de produção a partir de um bump
  automático de dependência.
- **Ordem sugerida:** US-001 → US-002 → US-003 → US-004 → US-005 → US-006 →
  US-008, com a **US-009 executada logo após a US-002** (ela contém o `chown`
  que a US-002 exige antes do primeiro deploy com container não-root).
  Dependências rígidas: US-004 antes de US-005, e US-007 antes de US-008. O
  resto é negociável.

## Success Metrics

- Nenhuma classe da tabela original da T8.1 permanece sem teste.
- `docker run ... whoami` no container de produção não retorna `root`, e
  `docker ps` reporta `(healthy)`.
- A trilha `security-audit.log` continua recebendo eventos **depois** do deploy
  com container não-root (verificação: um login gera linha nova no arquivo).
- Dependabot abre pelo menos um PR agrupado por ecossistema na primeira semana.
- Um PR com dependência de frontend vulnerável (alta/crítica) é barrado pelo CI.
- Merge de `dev` → `main` chega em produção sem nenhum comando manual.
- `flyway_schema_history` não ganha nenhuma linha de baseline nova após a US-008.

## Open Questions

As questões originais 1, 2 e 4 foram fechadas em 2026-08-06 e viraram as decisões
**E8.7** (proteção da `main`), **E8.8** (retenção de imagens e limites reais do
rollback) e **E8.9** (separação prod/dev no audit, decidida por medição). As duas
abaixo continuam abertas e **não bloqueiam nenhuma story**.

1. **Notificação de falha de deploy.** Hoje a falha só aparece na aba Actions. O
   GitHub já envia e-mail ao autor do commit quando um workflow falha na branch
   default — isso basta, ou vale um step `if: failure()` abrindo issue
   automática, ou um webhook (Telegram/Discord) que alcance o celular?
2. **Upgrade do `react-router` 7 → 8.** A US-004 decide ignorar o advisory
   `GHSA-qwww-vcr4-c8h2` por ser específico de RSC, que este app não usa. O
   major continua pendente e virá como PR do Dependabot. Vale fazer o upgrade
   proativamente, ou esperar o PR automático e revisar então?
