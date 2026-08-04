# Flyway — Estrategia de Baseline e Procedimento de Deploy

Este documento explica como o Flyway se comporta neste projeto (`spring.flyway.baseline-on-migrate=true` + `spring.flyway.baseline-version=1`) e o checklist a seguir no primeiro deploy que usa Flyway em producao.

Contexto: ate `origin/main` (`e0f3d36`), o schema de producao (Supabase) foi criado inteiramente por `spring.jpa.hibernate.ddl-auto=update`. Nao existe `flyway_schema_history` em producao. As migrations atuais sao:

- `api/src/main/resources/db/migration/V1__baseline.sql` — reproduz o schema ja existente em producao (`users`, `couples`, `media_track`, `media_track_genre`, `user_review`, `match_like`, `match_reject`), ver `docs/SCHEMA_BASELINE.md`.
- `api/src/main/resources/db/migration/V2__create_notification.sql` — cria a tabela `notification`, que ainda nao existe em producao.
- `api/src/main/resources/db/migration/V3__add_indexes.sql` — cria indices em colunas de FK/filtro (`couples.user1_id`/`user2_id`, `media_track.couple_id`+`status`/`tmdb_id`, `user_review.media_track_id`/`user_id`, `match_like.couple_id`+`tmdb_id`, `match_reject.couple_id`+`user_id`, `media_track_genre.media_track_id`) — apenas `CREATE INDEX IF NOT EXISTS`, nenhuma tabela e recriada ou alterada.
- `api/src/main/resources/db/migration/V4__denormalize_media_metadata.sql` — adiciona as colunas `title` (VARCHAR(255)), `poster_url` (VARCHAR(500)) e `release_year` (INTEGER), todas nullable, em `media_track` e `match_like` (epico 3, US-002) — apenas `ADD COLUMN IF NOT EXISTS`, nenhuma tabela e recriada ou alterada, sem default no lado do banco.
- `api/src/main/resources/db/migration/V5__create_refresh_token.sql` — cria a tabela `refresh_token` (epico 4, US-001), que ainda nao existe em producao. Guarda hash do refresh token, expiracao, revogacao e cadeia de substituicao (`replaced_by_id`), com FK para `users.id` e para a propria `refresh_token.id` — apenas `CREATE TABLE`/`CREATE INDEX IF NOT EXISTS`, nenhuma tabela existente e tocada.

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

Isso vale tanto para o deploy que aplicar `V2__create_notification.sql` quanto para o que aplicar `V3__add_indexes.sql`, `V4__denormalize_media_metadata.sql` ou `V5__create_refresh_token.sql` (ou qualquer migration nova subsequente) — qualquer deploy com uma versao pendente > 1 deve usar a porta 5432 (direta) ou o pooler em modo Session, nao o modo Transaction (6543), so para essa execucao.

## Recomendacao: backup antes do primeiro deploy com Flyway

Antes de rodar o primeiro deploy com Flyway contra o banco de producao no Supabase, tirar um snapshot/backup do banco (Supabase oferece backups automaticos no dashboard do projeto, em **Database > Backups**; um backup manual/on-demand adicional e recomendado para este deploy especifico). Isso garante um ponto de restauracao caso o baseline nao se comporte como esperado (ex.: `baseline-version` incorreta, ou schema real divergente do que `docs/SCHEMA_BASELINE.md` documenta — ver a ressalva de validacao contra `information_schema` la registrada).
