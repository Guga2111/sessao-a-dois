# Flyway — Estrategia de Baseline e Procedimento de Deploy

Este documento explica como o Flyway se comporta neste projeto (`spring.flyway.baseline-on-migrate=true` + `spring.flyway.baseline-version=1`) e o checklist a seguir no primeiro deploy que usa Flyway em producao.

## 2026-08-06 — `SPRING_FLYWAY_BASELINE_ON_MIGRATE` removido do compose de producao

A linha `SPRING_FLYWAY_BASELINE_ON_MIGRATE: "true"` foi **removida** de
`docker-compose-prod.yml` em **2026-08-06** (Epico 8, US-008 do PRD
`tasks/prd-epico-8-cicd-e-infraestrutura.md`).

**Porque:** ela era necessaria apenas no *primeiro* deploy com Flyway, quando o
Supabase ja tinha o schema mas nao tinha `flyway_schema_history`. Esse deploy ja
aconteceu. Mantida ligada permanentemente, ela deixa de ser uma facilidade e vira
um risco: se um dia o `flyway_schema_history` sumir ou divergir, o Flyway cria uma
baseline nova e **marca migrations como aplicadas sem as executar** — o deploy sobe
"verde" com o schema errado, e a falha so aparece depois, como erro de validacao do
Hibernate ou como coluna inexistente em runtime. Sem a variavel, o mesmo cenario
quebra o startup imediatamente, que e o comportamento que queremos.

**Como passar pontualmente** (unico caso legitimo: ambiente novo, com schema
pre-existente e sem `flyway_schema_history`):

```bash
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true docker compose -f docker-compose-prod.yml up -d
```

Ver tambem `docs/DEPLOY.md`, seccao "Flyway: ambiente novo com schema
pre-existente".

### ⚠️ GATE HUMANO — verificacao obrigatoria ANTES do merge para a `main`

A mudanca de codigo e **inerte** ate o proximo deploy, mas ela depende de uma
verificacao operacional (US-007 do PRD) que **nao foi executada por um agente** —
exige credencial de producao do Supabase. Antes de mergear esta alteracao para a
`main` (push na `main` = deploy automatico, ver `.github/workflows/deploy.yml`),
o mantenedor tem de rodar no banco de producao:

```sql
SELECT installed_rank, version, description, type, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

E confirmar:

- [ ] Existem linhas para **V1 ate V6** (as migrations que existiam quando este gate
      foi escrito; a `V7__add_couple_dissolved_at.sql` veio depois, no Epico 9, e
      ainda nao foi aplicada em producao — se ela ja aparecer no historico, ela
      tambem tem de estar com `success = true`).
- [ ] **Todas** tem `success = true`.
- [ ] A unica linha com `type = 'BASELINE'` e a legitima da V1 (o baseline do
      primeiro deploy). Nenhuma migration que deveria ter sido executada de fato
      aparece como baseline.

**Se qualquer migration estiver faltando ou com `success = false`: PARAR.** Esta
alteracao tem de ser **revertida antes do deploy** — com o schema divergente e sem
a variavel, o proximo deploy falha no startup e a API nao sobe. Corrigir o
historico primeiro, depois reaplicar a remocao.

### ✅ Resultado da verificacao — 2026-08-10

Executado pelo mantenedor no Supabase de producao:

| installed_rank | version | description                | type     | success |
|----------------|---------|----------------------------|----------|---------|
| 1              | 1       | << Flyway Baseline >>      | BASELINE | true    |
| 2              | 2       | create notification        | SQL      | true    |
| 3              | 3       | add indexes                | SQL      | true    |
| 4              | 4       | denormalize media metadata | SQL      | true    |

**Veredito: LIBERADO** — mas por um motivo diferente do que o gate previa, e com um
achado que vale mais que o proprio gate.

Contra os tres criterios acima:

- **`success = true` em todas.** ✅
- **Um unico `type = 'BASELINE'`, e e a V1 legitima.** ✅ Nenhuma migration que
  deveria ter rodado foi mascarada como baseline — que era exatamente o risco que a
  remocao de `SPRING_FLYWAY_BASELINE_ON_MIGRATE` (Epico 8, US-007) veio eliminar.
- **"Existem linhas para V1 ate V6": nao.** O historico para na V4. ⚠️

O terceiro item **nao** e o cenario de "PARAR" descrito abaixo. Ele foi escrito para
o caso de uma migration ter **sumido ou falhado** num schema que ja deveria te-la —
divergencia real, que quebra o startup. Aqui V5, V6 e V7 estao simplesmente
**pendentes**: nunca foram aplicadas porque o deploy correspondente nunca chegou a
producao. Flyway aplica pendencia em ordem no proximo startup, que e o caminho normal
e saudavel. Nao ha nada a corrigir antes do merge.

**O achado real: producao esta no Epico 3.** A ultima migration aplicada e a
`V4__denormalize_media_metadata.sql`, do Epico 3 (US-002). Confirmado de forma
independente pelo bundle servido em `https://sessaoadois.luisgosampaio.com` na mesma
data, que ainda guarda JWT em `localStorage`, manda `Authorization: Bearer` e abre o
WebSocket com `?token=` na query string — todos padroes **anteriores ao Epico 4**.
Ou seja: **os Epicos 4 a 9 nunca foram para producao**, e o pipeline de CD
(`.github/workflows/deploy.yml`, existente desde 2026-08-06) nunca completou um deploy
bem-sucedido. O que esta no ar veio de um `./scripts/deploy.sh` manual antigo.

Consequencias praticas para o proximo deploy, que **nao** e um deploy do Epico 9 e sim
dos Epicos 4→9 de uma vez:

1. **Tres migrations aplicam juntas** (V5, V6, V7). Todas aditivas e idempotentes
   (`CREATE TABLE IF NOT EXISTS`, `ADD COLUMN IF NOT EXISTS`, `ALTER COLUMN DROP NOT
   NULL`) — nenhum `DROP`/`TRUNCATE`, nenhuma linha apagada. Por isso o deploy sem
   snapshot previo e um risco aceitavel (ver "backup" abaixo e a D15 do
   `POST-MVP-TASK.md`), ao contrario do que seria com migration destrutiva.
2. **O cutover de autenticacao do Epico 4 acontece agora**, nao antes: todos os
   usuarios sao deslogados uma vez (`docs/DEPLOY.md`, "Aviso de deploy"). Avisar os
   dois usuarios do casal.
3. **As tarefas operacionais de nginx da US-010 continuam pendentes** e agora sao mais
   urgentes, porque o app no ar ainda coloca o JWT na URL do `/ws`: reinstalar o conf
   com `access_log off;` no bloco `/ws/`, colar os 4 headers de seguranca no bloco 443
   gerado pelo Certbot, purgar `access.log*` e rotacionar o `JWT_SECRET`
   (`docs/DEPLOY.md`, "Seguranca operacional", itens (a) a (d)). O deploy sozinho para
   o vazamento novo; nao apaga o que ja foi gravado.
4. **`spring.jpa.hibernate.ddl-auto=validate`** significa que a API **nao sobe** se o
   schema divergir depois das migrations. E a rede de seguranca certa: se V5/V6/V7
   nao aplicarem, o container falha alto e o smoke test do CD reprova, em vez de subir
   com schema errado.

Contexto: ate `origin/main` (`e0f3d36`), o schema de producao (Supabase) foi criado inteiramente por `spring.jpa.hibernate.ddl-auto=update`. Nao existe `flyway_schema_history` em producao. As migrations atuais sao:

- `api/src/main/resources/db/migration/V1__baseline.sql` — reproduz o schema ja existente em producao (`users`, `couples`, `media_track`, `media_track_genre`, `user_review`, `match_like`, `match_reject`), ver `docs/SCHEMA_BASELINE.md`.
- `api/src/main/resources/db/migration/V2__create_notification.sql` — cria a tabela `notification`, que ainda nao existe em producao.
- `api/src/main/resources/db/migration/V3__add_indexes.sql` — cria indices em colunas de FK/filtro (`couples.user1_id`/`user2_id`, `media_track.couple_id`+`status`/`tmdb_id`, `user_review.media_track_id`/`user_id`, `match_like.couple_id`+`tmdb_id`, `match_reject.couple_id`+`user_id`, `media_track_genre.media_track_id`) — apenas `CREATE INDEX IF NOT EXISTS`, nenhuma tabela e recriada ou alterada.
- `api/src/main/resources/db/migration/V4__denormalize_media_metadata.sql` — adiciona as colunas `title` (VARCHAR(255)), `poster_url` (VARCHAR(500)) e `release_year` (INTEGER), todas nullable, em `media_track` e `match_like` (epico 3, US-002) — apenas `ADD COLUMN IF NOT EXISTS`, nenhuma tabela e recriada ou alterada, sem default no lado do banco.
- `api/src/main/resources/db/migration/V5__create_refresh_token.sql` — cria a tabela `refresh_token` (epico 4, US-001), que ainda nao existe em producao. Guarda hash do refresh token, expiracao, revogacao e cadeia de substituicao (`replaced_by_id`), com FK para `users.id` e para a propria `refresh_token.id` — apenas `CREATE TABLE`/`CREATE INDEX IF NOT EXISTS`, nenhuma tabela existente e tocada.
- `api/src/main/resources/db/migration/V6__add_invite_code_expiry.sql` — adiciona `couples.invite_code_expires_at` (TIMESTAMP WITH TIME ZONE, nullable) e torna `couples.invite_code` nullable (epico 5, US-007/008/009/010) — apenas `ADD COLUMN IF NOT EXISTS` + `ALTER COLUMN DROP NOT NULL`, nenhuma tabela e recriada. Um convite usado/expirado passa a ser LIMPO para `NULL` em vez de marcado em coluna separada; Postgres trata cada `NULL` como distinto no unique constraint, entao varios casais pareados nao colidem.
- `api/src/main/resources/db/migration/V7__add_couple_dissolved_at.sql` — adiciona `couples.dissolved_at` (TIMESTAMP WITH TIME ZONE, nullable; `NULL` = casal ativo), epico 9, US-001 — apenas `ADD COLUMN IF NOT EXISTS`, nenhuma tabela e recriada e nenhuma linha e apagada. A dissolucao do vinculo e um `UPDATE` desta coluna, nunca um `DELETE` em `couples` (D13: dissolver, nao apagar) — `notification.couple_id` e `media_track.couple_id` mantem FK para a tabela. Consequencia a partir daqui: um usuario pode ter varias linhas em `couples` (uma ativa e N dissolvidas), entao toda consulta que resolve "o casal do usuario" ou "o casal do convite" precisa filtrar `dissolved_at IS NULL`.

## Comportamento em producao (schema existente, sem `flyway_schema_history`)

Na primeira execucao da aplicacao com Flyway habilitado contra o banco de producao (Supabase), voce deve passar a variavel de ambiente `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` (e opcionalmente `SPRING_FLYWAY_BASELINE_VERSION=1`, que ja e o default). Ela NAO esta em `application.properties` — ver nota sobre Flyway 10.x abaixo.

Com essa variavel de ambiente definida:

1. Flyway detecta que o schema `public` ja tem tabelas mas nao tem a tabela `flyway_schema_history`.
2. Em vez de falhar (comportamento padrao sem essa flag), o Flyway cria `flyway_schema_history` e insere uma linha de baseline para a versao `1`.
3. Essa baseline **marca a V1 como ja aplicada, sem executar `V1__baseline.sql`**. O `CREATE TABLE IF NOT EXISTS` de V1 nunca roda em producao — ele so existe para o caso de banco limpo (dev/test/CI).
4. Com a V1 marcada como baseline, o Flyway aplica normalmente as migrations com versao maior que a baseline: `V2__create_notification.sql` e executada, criando a tabela `notification` (que realmente nao existe ainda em producao).
5. O resultado e um banco de producao com todas as tabelas antigas intactas (nenhuma foi recriada ou alterada) mais a nova tabela `notification`.

## Comportamento em dev/test (banco limpo)

Em um banco vazio (dev local recem-criado, CI, ou docker-compose), nao ha schema previo nem `flyway_schema_history`:

1. O Flyway cria `flyway_schema_history` e aplica **todas** as migrations em ordem, comecando pela V1.
2. `V1__baseline.sql` executa de fato, criando as 7 tabelas de producao do zero.
3. `V2__create_notification.sql` executa em seguida, criando `notification`.
4. Ao final, o Hibernate (`ddl-auto=validate`) valida as entidades contra o schema criado pelas migrations e nao deve encontrar nenhuma discrepancia.

> **ATENCAO — Flyway 10.x:** `baseline-on-migrate=true` nesta versao marca V1 como baseline sem executa-la mesmo em banco VAZIO, quebrando dev/CI. Por isso essa propriedade foi removida de `application.properties` e so deve ser ativada via variavel de ambiente (`SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`) no primeiro deploy contra o Supabase.

### Diferenca-chave entre os dois ambientes

| | Producao (schema existente) | Dev/test (banco limpo) |
|---|---|---|
| `flyway_schema_history` antes do deploy | Nao existe | Nao existe |
| V1 (`V1__baseline.sql`) | Marcada como baseline, **nao executada** | Executada normalmente |
| V2 (`V2__create_notification.sql`) | Executada | Executada |
| Tabelas antigas (`users`, `couples`, etc.) | Preservadas como estao (nao recriadas) | Criadas pela V1 |

## Checklist pos-deploy (producao)

Apos rodar `./scripts/deploy.sh` pela primeira vez com Flyway habilitado, confirmar antes de considerar o deploy bem-sucedido:

- [ ] A tabela `flyway_schema_history` existe em producao e tem uma linha com `version = 1`, `description` referente ao baseline, `success = true`.
- [ ] A mesma tabela tem uma segunda linha para `version = 2` (`create_notification`) com `success = true`.
- [ ] Nenhuma das tabelas existentes (`users`, `couples`, `media_track`, `media_track_genre`, `user_review`, `match_like`, `match_reject`) foi recriada: `SELECT count(*)` em cada uma bate com a contagem de antes do deploy (dados intactos, nenhuma linha perdida).
- [ ] A tabela `notification` foi criada, esta vazia (`SELECT count(*) FROM notification` = 0) e tem o indice `idx_notification_recipient_read`.
- [ ] Logs da API (`docker compose -f docker-compose-prod.yml logs api`, ver `docs/DEPLOY.md`) nao mostram erro de `FlywayException` nem de validacao do Hibernate na inicializacao.
- [ ] A aplicacao responde normalmente (`GET /api/health` ou equivalente) e uma leitura simples (ex.: login de um usuario existente) confirma que os dados antigos continuam acessiveis.

## Risco conhecido: connection pooler em modo Transaction (Supabase)

`docs/DEPLOY.md` (Passo 1) instrui a copiar a connection string do **Connection Pooler em modo Transaction** (porta 6543) para `DB_URL`. Isso e adequado para o trafego normal da API, mas e um risco especifico para o Flyway:

- O Flyway usa **advisory locks** do PostgreSQL (`pg_advisory_lock`) para impedir migrations concorrentes. Advisory locks sao amarrados a sessao fisica da conexao.
- O PgBouncer em **modo Transaction** nao garante a mesma conexao fisica entre statements/transacoes distintas — e o proprio motivo pelo qual `DB_URL` ja usa `?prepareThreshold=0` (workaround para prepared statements, um problema diferente e ja resolvido). Advisory locks tem o mesmo tipo de incompatibilidade estrutural com esse modo.
- Na pratica, isso pode fazer o Flyway falhar ao obter o lock de migration no primeiro deploy (startup trava ou lanca erro), mesmo sem nenhuma migration concorrente real — o problema e o pooler, nao concorrencia.

**Recomendacao:** para o primeiro deploy com Flyway (ou qualquer deploy que rode uma nova migration), usar temporariamente em `DB_URL` a **connection string direta** (porta 5432) ou o **Connection Pooler em modo Session** do Supabase, em vez do modo Transaction (6543), especificamente para essa execucao. O modo Transaction pode voltar a ser usado depois, ja que o Flyway so faz um trabalho real de migration na inicializacao com uma nova versao pendente. Isso nao exige alterar `scripts/deploy.sh`, `api/Dockerfile` ou `docker-compose-prod.yml` (nenhum dos tres fixa a porta ou o modo do pooler) — e apenas o valor de `DB_URL` no `.env`, que ja e editado manualmente a cada deploy conforme `docs/DEPLOY.md`.

Isso vale tanto para o deploy que aplicar `V2__create_notification.sql` quanto para o que aplicar `V3__add_indexes.sql`, `V4__denormalize_media_metadata.sql`, `V5__create_refresh_token.sql`, `V6__add_invite_code_expiry.sql` ou `V7__add_couple_dissolved_at.sql` (ou qualquer migration nova subsequente) — qualquer deploy com uma versao pendente > 1 deve usar a porta 5432 (direta) ou o pooler em modo Session, nao o modo Transaction (6543), so para essa execucao.

## Recomendacao: backup antes do primeiro deploy com Flyway

Antes de rodar o primeiro deploy com Flyway contra o banco de producao no Supabase, tirar um snapshot/backup do banco (Supabase oferece backups automaticos no dashboard do projeto, em **Database > Backups**; um backup manual/on-demand adicional e recomendado para este deploy especifico). Isso garante um ponto de restauracao caso o baseline nao se comporte como esperado (ex.: `baseline-version` incorreta, ou schema real divergente do que `docs/SCHEMA_BASELINE.md` documenta — ver a ressalva de validacao contra `information_schema` la registrada).
