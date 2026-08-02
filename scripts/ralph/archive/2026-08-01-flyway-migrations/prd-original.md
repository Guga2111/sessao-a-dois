# PRD: Migração de `ddl-auto=update` para Flyway (com dados em produção)

## 1. Introdução / Visão Geral

A aplicação **Sessão a Dois** (Spring Boot 4.1.0, Java 21, PostgreSQL no Supabase) foi deployada em produção **uma única vez** usando `spring.jpa.hibernate.ddl-auto=update`, sem nenhuma ferramenta de migração. Já existem **usuários ativos com dados reais** no banco.

**Baseline de produção (fonte de verdade do estado deployado):** `origin/main` = commit **`e0f3d36` (Merge PR #13)**. Atenção: a branch `main` **local** está desatualizada (apenas "Initial commit" com um README); o estado real de produção é `origin/main`. Todas as migrações devem partir desse baseline.

**Schema-alvo (estado desejado):** branch **`create-migrations`** (HEAD atual, `cc5c464`).

Um novo deploy com `ddl-auto=update` é arriscado (comportamento não determinístico, sem versionamento, sem auditoria). Este PRD introduz o **Flyway** para versionar o schema, estabelecer um **baseline** que reconheça o schema já existente em produção, e aplicar apenas as alterações incrementais de forma segura e sem perda de dados.

### Diff real de schema (já apurado: `origin/main` → `create-migrations`)

A análise das entidades JPA entre os dois pontos revelou que **as premissas do PROMPT original estavam desatualizadas**. Estado confirmado:

- **Todas as entidades existentes são idênticas** entre `origin/main` e HEAD: `User`, `Couple`, `MediaTrack`, `UserReview`, `MatchLike`, `MatchReject`. As tabelas `match_like`, `match_reject`, `media_track_genre` (do `@ElementCollection genreIds`), e as colunas `runtime`/`genre_id` **já existem em produção** — não são novas.
- **Única diferença de schema:** a nova entidade **`Notification`** → tabela **`notification`**, com o índice `idx_notification_recipient_read` sobre `(recipient_user_id, is_read)`.
- **Nenhuma** coluna nova em tabela existente; **nenhum** backfill necessário.

Portanto o conjunto de migrações é enxuto: **`V1` = baseline do schema de produção; `V2` = criar `notification`. Não há V3+.**

## 2. Goals

- Substituir `ddl-auto=update` por Flyway como único mecanismo de evolução de schema, com `ddl-auto=validate` em todos os ambientes.
- Estabelecer um baseline (`V1`) que represente o schema **já existente** em produção, de forma que o Flyway **não tente recriá-lo** no banco de produção.
- Aplicar, no próximo deploy, **apenas** a migração incremental `V2` (tabela `notification`).
- Garantir **zero perda de dados** em produção (nenhum `DROP`/`TRUNCATE` sem confirmação explícita; `CREATE TABLE IF NOT EXISTS`).
- Manter dev local e testes funcionais: em banco limpo, o Flyway aplica `V1` (schema completo) + `V2` do zero, garantindo paridade dev/prod.
- Manter `application.properties` de main e de test em sincronia quanto às novas propriedades do Flyway.

## 3. User Stories

### US-001: Validar o schema real de produção antes de escrever o baseline
**Description:** Como desenvolvedor, preciso confirmar que o schema físico de produção corresponde ao que as entidades de `origin/main` descrevem, já que o schema foi criado por `ddl-auto=update` (que pode divergir de um `CREATE` limpo).

**Acceptance Criteria:**
- [ ] Confirmar (via `information_schema` do banco Supabase de produção **ou**, se o acesso não estiver disponível, subindo a app em `origin/main` contra um Postgres limpo e capturando o schema gerado pelo Hibernate) a lista de tabelas/colunas/constraints/índices efetivamente existentes.
- [ ] Comparar esse schema com as entidades de `origin/main` e registrar qualquer divergência (nomes, tipos, defaults, índices auto-gerados como PK/unique).
- [ ] Confirmar que as tabelas `users`, `couples`, `media_track`, `media_track_genre`, `user_review`, `match_like`, `match_reject` existem em produção e que `notification` **não** existe.
- [ ] Resultado documentado como base autoritativa para o `V1`.

### US-002: Adicionar a dependência do Flyway ao projeto
**Description:** Como desenvolvedor, preciso do Flyway disponível no build para versionar o schema.

**Acceptance Criteria:**
- [ ] Adicionar `flyway-core` e `flyway-database-postgresql` ao `api/pom.xml` **sem `<version>`** (versão gerenciada pelo BOM do Spring Boot 4.1.0, garantindo compatibilidade).
- [ ] `./mvnw -f api/pom.xml compile` executa sem erros de resolução de dependência.
- [ ] Nenhuma outra dependência do projeto é alterada.

### US-003: Configurar Flyway e `ddl-auto=validate` no `application.properties` (main + test)
**Description:** Como desenvolvedor, preciso que o Flyway seja o mecanismo de schema em todos os ambientes, com o Hibernate apenas validando (nunca alterando) o schema.

**Acceptance Criteria:**
- [ ] Em `api/src/main/resources/application.properties`: `spring.jpa.hibernate.ddl-auto=validate`, `spring.flyway.enabled=true`, `spring.flyway.baseline-on-migrate=true`, `spring.flyway.baseline-version=1`, `spring.flyway.locations=classpath:db/migration`.
- [ ] `api/src/test/resources/application.properties` reflete as mesmas propriedades do Flyway (mirror), ajustado ao banco de teste — em banco limpo o Flyway aplica V1 + V2 do zero e o Hibernate valida.
- [ ] Remover/substituir explicitamente `spring.jpa.hibernate.ddl-auto=update` onde existir.
- [ ] A aplicação sobe localmente (dev, banco limpo) aplicando V1 e V2 sem erro e passa na validação do Hibernate.
- [ ] Typecheck/compilação e testes existentes passam.

### US-004: Criar `V1__baseline.sql` (schema completo do estado de produção)
**Description:** Como desenvolvedor, preciso de uma migração baseline que represente o schema exato de produção, para que em banco limpo (dev/test) o schema seja criado do zero e em produção seja reconhecido como já aplicado (não executado).

**Acceptance Criteria:**
- [ ] Criar diretório `api/src/main/resources/db/migration/`.
- [ ] `V1__baseline.sql` contém o schema completo de produção: tabelas `users`, `couples`, `media_track`, `media_track_genre`, `user_review`, `match_like`, `match_reject`, com suas colunas, PKs (UUID), FKs, unique constraints e índices reais (conforme confirmado em US-001).
- [ ] Nomes físicos exatos: `users` (unique em `email`), `couples` (unique em `invite_code`, colunas `user1_id`/`user2_id`), `media_track` (FK `couple_id`, colunas `tmdb_id`/`media_type`/`status`/`watched_date`/`runtime`/`created_at`), `media_track_genre` (FK `media_track_id`, coluna `genre_id`), `user_review` (unique em `media_track_id`+`user_id`), `match_like` e `match_reject` (unique em `couple_id`+`user_id`+`tmdb_id`).
- [ ] Usa `CREATE TABLE IF NOT EXISTS` e é seguro contra dados existentes (nenhum `DROP`/`TRUNCATE`).
- [ ] **Não** contém a tabela `notification`.
- [ ] Em banco limpo, aplicar V1 gera um schema idêntico ao que o `ddl-auto` de `origin/main` teria gerado.

### US-005: Criar `V2__create_notification.sql`
**Description:** Como desenvolvedor, preciso da migração que adiciona a tabela `notification` ao schema, aplicável com segurança sobre o banco de produção existente.

**Acceptance Criteria:**
- [ ] `V2__create_notification.sql` cria a tabela `notification` via `CREATE TABLE IF NOT EXISTS` com as colunas: `id` (UUID PK), `couple_id` (UUID, FK para `couples`, NOT NULL), `recipient_user_id` (UUID, NOT NULL), `type` (varchar/enum-string, NOT NULL), `tmdb_id` (bigint, NOT NULL), `media_type` (varchar, NOT NULL), `title` (varchar, NOT NULL), `actor_user_id` (UUID, NOT NULL), `media_track_id` (UUID, **nullable**), `is_read` (boolean, NOT NULL, default false), `created_at` (timestamp, NOT NULL).
- [ ] Cria o índice `idx_notification_recipient_read` sobre `(recipient_user_id, is_read)`.
- [ ] **Nenhum** `DROP`/`TRUNCATE`. A tabela nasce vazia, então os `NOT NULL` não afetam dados existentes.
- [ ] Sintaxe válida para PostgreSQL do Supabase (compatível com o pooler na 6543, `prepareThreshold=0`).
- [ ] Após aplicar V1+V2 em banco limpo, o Hibernate `validate` passa contra as entidades de `create-migrations` sem discrepâncias.

### US-006: Documentar a estratégia de baseline e o procedimento de deploy em produção
**Description:** Como responsável pelo deploy, preciso entender como o baseline funciona para executar o próximo deploy em produção com segurança.

**Acceptance Criteria:**
- [ ] Documento explica que, com `baseline-on-migrate=true` + `baseline-version=1`, no primeiro run contra o banco de produção (que já tem schema mas não tem `flyway_schema_history`), o Flyway cria a tabela de histórico, marca V1 como baseline (**sem executar** `V1__baseline.sql`) e aplica somente V2.
- [ ] Documento cobre o comportamento em **dev/test** (banco limpo → V1 é executado normalmente) e a diferença em relação a produção.
- [ ] Documento inclui checklist pós-deploy (conferir linhas em `flyway_schema_history`, confirmar que nenhuma tabela existente foi recriada, dados intactos, tabela `notification` criada).
- [ ] Recomenda snapshot/backup do banco Supabase antes do primeiro deploy com Flyway.
- [ ] Salvo em `docs/` (ex.: `docs/FLYWAY.md`).

### US-007: Propor (para aprovação) alterações em arquivos sensíveis de deploy — se necessárias
**Description:** Como responsável pelo projeto, quero aprovar previamente qualquer mudança em arquivos de infra sensíveis antes que sejam aplicadas.

**Acceptance Criteria:**
- [ ] Avaliar se `scripts/deploy.sh`, `api/Dockerfile` e `docker-compose-prod.yml` precisam de alteração para o Flyway funcionar (esperado: **nenhuma**, pois o Flyway roda embutido na aplicação Spring Boot no startup).
- [ ] Se alguma mudança for necessária, apresentá-la como proposta escrita (diff + justificativa) e **aguardar aprovação explícita** — não alterar esses arquivos diretamente.
- [ ] Se nenhuma mudança for necessária, registrar essa conclusão explicitamente.

## 4. Functional Requirements

- **FR-1:** O sistema deve usar o Flyway como único mecanismo de evolução de schema; o Hibernate deve operar em modo `validate` em todos os ambientes (dev, test, prod).
- **FR-2:** As migrações devem residir em `api/src/main/resources/db/migration/` seguindo a convenção `V<n>__<descricao>.sql`.
- **FR-3:** `V1__baseline.sql` deve reproduzir o schema de produção (`origin/main`) usando `CREATE TABLE IF NOT EXISTS`; não deve conter a tabela `notification`.
- **FR-4:** `V2__create_notification.sql` deve criar a tabela `notification` e o índice `idx_notification_recipient_read`, de forma segura sobre dados existentes.
- **FR-5:** Nenhuma migração pode conter `DROP TABLE`, `DROP COLUMN` ou `TRUNCATE` sem aprovação explícita do responsável.
- **FR-6:** A configuração deve usar `spring.flyway.baseline-on-migrate=true` e `spring.flyway.baseline-version=1`, de modo que o schema V1 existente seja reconhecido (marcado, não executado) e apenas V2 seja aplicada em produção.
- **FR-7:** As propriedades do Flyway devem ser espelhadas em `api/src/main/resources/application.properties` e `api/src/test/resources/application.properties`.
- **FR-8:** Em banco limpo (dev/test), aplicar V1+V2 deve gerar um schema que passa na validação do Hibernate contra as entidades de `create-migrations`.
- **FR-9:** As migrações devem ser compatíveis com PostgreSQL do Supabase acessado via connection pooler (porta 6543, `prepareThreshold=0`).
- **FR-10:** Alterações em `scripts/deploy.sh`, `api/Dockerfile` e `docker-compose-prod.yml` só podem ocorrer após aprovação explícita e prévia.

## 5. Non-Goals (Out of Scope)

- Não haverá remoção/renomeação destrutiva de colunas ou tabelas existentes neste ciclo.
- Não é objetivo migrar/transformar dados de negócio; não há backfill (nenhuma coluna `NOT NULL` nova em tabela existente).
- Não haverá adoção de Flyway Teams/callbacks avançados, undo migrations ou repeatable migrations (`R__`) neste escopo.
- Não haverá mudança do provedor de banco (permanece Supabase/PostgreSQL).
- Não será alterada a lógica de negócio, endpoints ou entidades — apenas a infraestrutura de migração.
- Não será executado o deploy em produção como parte desta entrega (a entrega prepara e documenta; o deploy é acionado pelo responsável).

## 6. Design / Technical Considerations

- **Baseline:** `baseline-on-migrate=true` + `baseline-version=1`. Produção (schema existente, sem `flyway_schema_history`) → Flyway cria o histórico, marca V1 como baseline sem executá-lo, aplica V2. Dev/test (banco limpo) → Flyway executa V1 + V2 normalmente.
- **Paridade dev/prod:** por isso `V1` precisa ser um schema **completo e fiel** ao estado de produção — em dev ele é a única fonte do schema base.
- **Nomes físicos (confirmados):** tabelas `users`, `couples`, `media_track`, `media_track_genre`, `user_review`, `match_like`, `match_reject`, `notification`. IDs são UUID (`GenerationType.UUID`). Enums persistidos como STRING (`media_type`, `status`, `type`).
- **Segurança de dados:** `CREATE TABLE IF NOT EXISTS`. Backup do Supabase recomendado antes do primeiro deploy com Flyway.
- **Arquivos sensíveis:** espera-se que Flyway rode embutido no startup do Spring Boot, sem necessidade de tocar `deploy.sh`/`Dockerfile`/`docker-compose-prod.yml`. Qualquer exceção passa por aprovação.
- **Pontos de integração:** `api/pom.xml`, ambos `application.properties`, novo diretório `api/src/main/resources/db/migration/`.

## 7. Success Metrics

- Próximo deploy em produção aplica somente V2 (verificável em `flyway_schema_history`), com V1 marcado como baseline e **nenhuma** tabela/coluna existente recriada ou perdida.
- Zero incidentes de perda de dados; contagem de registros das tabelas de produção inalterada após o deploy (`notification` criada vazia).
- `mvn test` e o startup local (banco limpo) passam com `ddl-auto=validate` + Flyway.
- Nenhum uso de `ddl-auto=update` permanece no código.

## 8. Resolved Decisions (antigas Open Questions)

- **Baseline de produção:** `origin/main` (`e0f3d36`), não a `main` local (desatualizada).
- **Estratégia de baseline:** `baseline-on-migrate=true` + `baseline-version=1`.
- **Dev/test:** `ddl-auto=validate` em todos os ambientes; Flyway aplica V1+V2 em banco limpo.
- **Naming/nomes físicos:** entidades usam `@Table`/`@Column` explícitos; nomes confirmados na seção 6. US-001 valida contra o schema real de produção antes de escrever o V1.
- **Colunas `NOT NULL` novas em tabelas existentes:** nenhuma; sem backfill.
- **Diferenças de schema além do PROMPT:** apuradas — apenas a tabela `notification` é nova. `match_like`, `match_reject`, `media_track_genre`, `runtime`, `genre_id` já existem em produção.
- **Versão do Flyway:** não fixar; gerenciada pelo BOM do Spring Boot 4.1.0.
