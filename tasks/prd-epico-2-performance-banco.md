# PRD: Épico 2 — Fundação de Performance de Banco

## 1. Introdução/Overview

Este épico elimina os problemas de performance de banco de dados identificados na
auditoria técnica de 2026-08-02 sobre `api/` (`POST-MVP-TASK.md`): ausência de
índices em colunas de FK/filtro, N+1 queries em duas camadas diferentes (resolução
de nomes de usuário e coleções LAZY do JPA), um delete em massa ineficiente, e —
pré-requisito de tudo isso — uma lacuna de CI onde nenhum teste hoje executa as
migrations Flyway de verdade contra um schema Postgres.

O objetivo é corrigir essas questões **antes que o volume de dados torne o problema
visível em produção**, sem alterar nenhum contrato de API observável pelo frontend
(`client/`). Todas as 5 tasks (T2.0–T2.4) são no backend (`api/`); nenhuma story
deste épico tem componente de UI.

## 2. Goals

- Garantir que toda migration Flyway (`V1`...`Vn`) seja executada e validada contra
  Postgres real em CI antes de chegar em produção.
- Adicionar os índices de FK/filtro que faltam nas tabelas mais consultadas
  (`couples`, `media_track`, `user_review`, `match_like`, `match_reject`).
- Eliminar o N+1 de resolução de nomes de usuário em `MediaTrackService`
  (hoje até 100 SELECTs em `users` para listar 50 tracks).
- Eliminar o N+1 nas coleções LAZY (`reviews`, `couple`, `genreIds`) de
  `MediaTrack` e desligar `spring.jpa.open-in-view` com segurança.
- Trocar o delete de notificações expiradas de "carrega tudo e apaga um a um"
  para um único `DELETE` em massa.
- Zero mudança de contrato: JSON de resposta dos endpoints afetados permanece
  byte a byte idêntico ao atual.

## 3. User Stories

### US-001: CI valida as migrations Flyway de verdade
**Description:** Como desenvolvedor do backend, eu quero que o CI execute as
migrations `V1`, `V2` (e futuras) em sequência contra um schema compatível com
Postgres e valide as entidades JPA contra esse schema, para que uma migration
quebrada ou uma divergência entidade↔schema seja pega antes de produção — não
depois, como já aconteceu (commits `c78de9f` e `18f9b57`).

**Acceptance Criteria:**
- [ ] Existe uma classe de teste dedicada, `@SpringBootTest` puro (sem
      `@DataJpaTest`, para não sofrer o `PropertyMappingContextCustomizer` que
      força H2 sem `MODE=PostgreSQL`), com `@TestPropertySource` declarando
      `spring.datasource.url=jdbc:h2:mem:migrations;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`,
      `spring.flyway.enabled=true` e `spring.jpa.hibernate.ddl-auto=validate`.
- [ ] Esse teste executa `V1`, `V2` (e qualquer migration futura) em sequência e
      falha se qualquer uma não aplicar.
- [ ] Esse teste falha se uma entidade JPA divergir do schema criado pelas
      migrations (`ddl-auto=validate` exercitado de fato).
- [ ] Introduzir um erro deliberado numa migration (ex.: coluna com nome errado)
      faz `./mvnw test` falhar — verificado na prática, não presumido.
- [ ] O workflow de CI (`.github/workflows/`) executa esse teste contra o
      `postgres:17-alpine` que o workflow já sobe como service, usando
      `@AutoConfigureTestDatabase(replace = NONE)` + `DB_URL` do ambiente.
- [ ] Os slices `@DataJpaTest` e `@SpringBootTest` existentes continuam em
      `create-drop` e passando (264 testes verdes, nenhuma regressão).
- [ ] `api/CLAUDE.md` é corrigido: remove as afirmações falsas de que
      "produção e dev/test passam pelos mesmos arquivos `V*__*.sql`" e que
      "`ddl-auto=validate` está ativo em todo lugar", e descreve o arranjo real
      (slices em `create-drop` + um teste dedicado validando migrations).
- [ ] `./mvnw test` passa.

### US-002: Migration V3 com índices de FK/filtro
**Description:** Como usuário do app, eu quero que as consultas mais frequentes
(vínculo do casal, listagem de tracks, likes pendentes) não façam full scan a
cada request, para que a aplicação continue rápida à medida que os dados crescem.

**Acceptance Criteria:**
- [ ] Nova migration `api/src/main/resources/db/migration/V3__add_indexes.sql`
      cria (todas com `IF NOT EXISTS`, sem `DROP`/`TRUNCATE`, compatíveis com o
      modo PostgreSQL do H2 usado nos testes):
      `idx_couples_user1`, `idx_couples_user2`,
      `idx_media_track_couple_status`, `idx_media_track_couple_tmdb`,
      `idx_user_review_track`, `idx_user_review_user`,
      `idx_match_like_couple_tmdb`, `idx_match_reject_couple_user`,
      `idx_media_track_genre_track`.
- [ ] A aplicação sobe contra Postgres limpo com V1+V2+V3 aplicadas em sequência.
- [ ] `EXPLAIN SELECT * FROM couples WHERE user1_id = ? OR user2_id = ?` usa
      índice em vez de seq scan.
- [ ] Nenhuma tabela existente é recriada ou alterada — só criação de índice.
- [ ] `docs/FLYWAY.md` menciona explicitamente que, sendo esta a primeira
      migration nova pós-baseline, o deploy que a aplicar deve usar a connection
      string direta (5432) ou o pooler em modo Session do Supabase, não o modo
      Transaction (6543) — pelo risco de advisory lock já documentado.
- [ ] `./mvnw test` passa, **rodando o teste da US-001** (T2.0 precisa estar
      concluída antes desta story — sem ele, `./mvnw test` não valida `V3` de
      forma nenhuma, e a migration iria para produção sem nunca ter executado).

### US-003: Eliminar N+1 na resolução de nomes de usuário e extrair mapper
**Description:** Como usuário do app, eu quero que listar meus títulos não
dispare uma query em `users` por membro do casal por título, para que a listagem
não degrade linearmente com o tamanho da lista.

**Acceptance Criteria:**
- [ ] Novo `MediaTrackMapper` (componente Spring em `com.app.tracking`)
      responsável apenas por `MediaTrack` → `MediaTrackResponse`, recebendo um
      `Map<UUID, String>` de nomes já resolvido como parâmetro — não injeta
      `UserRepository`.
- [ ] Todos os pontos de entrada que hoje resolvem nome por membro
      (`listByStatus`, `listByStatusPaged`, `addTrack`, `markAsWatched`,
      `startWatching`, `deleteTrack`, `UserReviewService.upsertReview`) resolvem
      os nomes **uma única vez por request** via `userRepository.findAllById(memberIds)`
      (mesmo padrão já usado em `NotificationService.resolveActorNames:122-130`).
- [ ] `GET /api/tracking?status=WATCHED` com 50 tracks executa **no máximo 1**
      query em `users`, independentemente da quantidade de tracks (verificável
      com `spring.jpa.show-sql=true` ou contador de queries em teste).
- [ ] `MediaTrackService` não injeta mais `UserRepository` para fins de
      mapeamento.
- [ ] `UserReviewService` passa a depender de `MediaTrackMapper`, não mais de
      `MediaTrackService` (remove o acoplamento bidirecional atual).
- [ ] O JSON de resposta de todos os endpoints de `/api/tracking` é byte a byte
      idêntico ao anterior.
- [ ] Existe `MediaTrackMapperTest`.
- [ ] `MediaTrackServiceTest`, `MediaTrackControllerTest` e
      `UserReviewServiceTest` continuam passando (ajustados para o novo mapper
      onde necessário).
- [ ] `./mvnw test` passa.

### US-004: Eliminar N+1 nas coleções LAZY e desligar open-in-view
**Description:** Como usuário do app, eu quero que o endpoint não-paginado de
tracking não dispare queries extras proporcionais ao número de títulos, e quero
que o backend falhe de forma explícita (não degrade em silêncio) se algum
código acessar dado LAZY fora de transação, para que problemas de acesso a
dados sejam pegos em desenvolvimento, não em produção sob carga.

**Acceptance Criteria:**
- [ ] `@EntityGraph(attributePaths = {"reviews", "reviews.user", "couple"})`
      adicionado aos finders de `MediaTrackRepository` (`findByCoupleId`,
      `findByCoupleIdAndStatus`, `findByCoupleIdAndStatusOrderByCreatedAtDesc`).
- [ ] A variante paginada (`MediaTrackRepository.java:18`) **não** usa
      `JOIN FETCH` de coleção combinado com `Pageable` (isso paginaria em
      memória — aviso `HHH000104`). Usa `@EntityGraph` com `countQuery`
      separado, ou o padrão de duas queries (ids paginados, depois fetch por
      `IN`).
- [ ] `MediaTrackService.startWatching` é anotado com `@Transactional`
      (hoje é o único mutador sem essa anotação).
- [ ] **Somente após os 3 itens acima**, `spring.jpa.open-in-view=false` é
      definido em `application.properties`.
- [ ] `GET /api/tracking` (sem `status`) com 30 tracks executa número
      **constante** de queries, não proporcional à quantidade de tracks.
- [ ] `GET /api/tracking?status=WATCHED&page=0&size=20` pagina no banco: o log
      do Hibernate não emite `HHH000104`.
- [ ] Com `open-in-view=false`, todos os endpoints de `/api/tracking`,
      `/api/match` e `/api/notifications` respondem sem
      `LazyInitializationException`.
- [ ] `./mvnw test` passa.
- [ ] Esta story é implantada **sozinha**, sem nenhuma outra mudança junta no
      mesmo deploy — `spring.jpa.open-in-view=false` é uma mudança de
      comportamento global (não só de `tracking`); se algum endpoint não
      rastreado acessar dado LAZY fora de transação, ele quebra, e o rollback
      precisa ser um único revert limpo.

### US-005: Delete em massa de notificações expiradas
**Description:** Como operador do sistema, eu quero que a limpeza diária de
notificações antigas seja um único `DELETE`, não um carregamento de todas as
linhas seguido de exclusão uma a uma, para que o job de limpeza não cresça
linearmente com o volume de notificações.

**Acceptance Criteria:**
- [ ] `NotificationRepository.deleteByCreatedAtBefore` deixa de ser um delete
      derivado do Spring Data e vira uma query explícita:
      `@Modifying @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoff")`.
- [ ] O tipo de retorno em `NotificationCleanupService` muda de `long` para
      `int` (compatível com o retorno de `@Modifying`).
- [ ] O log existente reportando a quantidade removida é mantido.
- [ ] O job emite **uma** instrução `DELETE`, independentemente do volume de
      notificações expiradas (verificável via `spring.jpa.show-sql=true` ou
      contador de queries em teste).
- [ ] `NotificationCleanupServiceTest` e `NotificationRepositoryTest` passam.
- [ ] `./mvnw test` passa.

## 4. Functional Requirements

1. O sistema deve ter um teste de `@SpringBootTest` dedicado que aplica todas
   as migrations Flyway em sequência contra um H2 em modo PostgreSQL e valida
   as entidades JPA contra o schema resultante (`ddl-auto=validate`).
2. O CI deve executar esse teste também contra o Postgres 17 real já disponível
   como service no workflow.
3. O sistema deve criar índices B-tree em todas as colunas de FK e nas colunas
   de filtro mais usadas das tabelas `couples`, `media_track`, `user_review`,
   `match_like`, `match_reject` e `media_track_genre`, via migration
   `V3__add_indexes.sql`.
4. O sistema deve resolver nomes de usuário para exibição em no máximo uma
   query por request, independentemente do número de tracks/reviews retornados.
5. `MediaTrackMapper` deve ser o único responsável por converter `MediaTrack`
   em `MediaTrackResponse`; `MediaTrackService` não deve mais conter essa lógica
   nem depender de `UserRepository` para isso.
6. O sistema deve buscar `reviews`, `reviews.user` e `couple` via `@EntityGraph`
   nos finders de listagem de `MediaTrackRepository`, evitando lazy-loading
   implícito por item da lista.
7. A variante paginada de listagem de tracks não deve produzir paginação em
   memória (sem `HHH000104` no log do Hibernate).
8. `MediaTrackService.startWatching` deve ser transacional.
9. `spring.jpa.open-in-view` deve ser `false`, e essa mudança deve ser
   implantada isoladamente de qualquer outra alteração deste épico.
10. A limpeza de notificações expiradas deve executar um único `DELETE` em
    massa via `@Modifying @Query`, não um delete derivado que carrega entidades
    em memória.
11. Nenhuma mudança deste épico deve alterar o formato JSON de nenhuma resposta
    de API hoje consumida pelo `client/`.

## 5. Non-Goals (Out of Scope)

- Migrar toda a suíte de testes para Flyway/Postgres — só o teste dedicado de
  validação de migrations (US-001) precisa disso.
- Reescrever `MatchLikeRepository.findPendingForUser` de `NOT IN` para
  `NOT EXISTS` — fica para o Épico 3 (T3.3), que já depende dos índices desta
  US-002.
- Paginar o endpoint `GET /api/tracking` sem `status` — fica para o Épico 3
  (T3.3).
- Cache de qualquer tipo (TMDB ou outro) — decisão #5 de `docs/BACKLOG.md`
  segue válida; fora do escopo deste épico.
- Qualquer mudança de contrato de API observável pelo `client/` — todas as
  respostas devem permanecer idênticas.
- Qualquer mudança de UI/UX — este épico não tem componente de frontend.
- Mudar a política de retenção de 30 dias das notificações ou o horário do
  cron de limpeza (US-005 só troca o mecanismo de delete).
- Usar testcontainers para validar sintaxe Postgres-only — indisponível no
  sandbox de desenvolvimento atual (sem `docker`); a validação real acontece no
  CI contra o `postgres:17-alpine` do workflow.

## 6. Design Considerations

Não aplicável — este épico é inteiramente backend (`api/`), sem nenhuma tela ou
componente novo em `client/`. A skill `frontend-design` não se aplica (regra do
`CLAUDE.md` raiz: só é obrigatória para implementação dentro de `client/`).

## 7. Technical Considerations

- **Dependência de ordem rígida:** US-001 (T2.0) deve ser concluída antes de
  US-002 (T2.1) — sem o teste dedicado de migrations, `./mvnw test` não executa
  `V3__add_indexes.sql` nenhuma vez, e a migration iria para produção "no
  escuro". US-004 (T2.3) depende de US-003 (T2.2) — o mapper extraído em US-003
  é onde o `@EntityGraph` de US-004 passa a ser consumido.
- **Risco do connection pooler do Supabase (modo Transaction, porta 6543):**
  Flyway usa advisory locks (`pg_advisory_lock`), que dependem de conexão física
  estável. O pooler em modo Transaction não garante isso. Para o deploy que
  aplicar `V3` (primeira migration nova pós-baseline), usar temporariamente a
  connection string direta (5432) ou o pooler em modo Session — já documentado
  em `docs/FLYWAY.md`, ver seção "Risco conhecido: connection pooler em modo
  Transaction".
- **Limitação de `@DataJpaTest`:** `spring.test.database.replace=ANY` (via
  `PropertyMappingContextCustomizer`) força H2 puro sem `MODE=PostgreSQL`,
  incompatível com a sintaxe Postgres das migrations. Por isso o teste de
  migrations da US-001 precisa ser `@SpringBootTest` puro, não
  `@DataJpaTest` — não tentar "consertar" isso reabilitando Flyway nos slices
  existentes.
- **Ordem de implementação de US-004 é obrigatória:** desligar
  `open-in-view=false` antes de resolver os N+1 explícitos transforma cada um
  deles em `LazyInitializationException` em vez de degradação silenciosa — é o
  comportamento desejável a longo prazo, mas quebra o app se feito fora de
  ordem (EntityGraph + `@Transactional` primeiro, OSIV por último).
- **Deploy isolado de US-004:** por mudar comportamento global (não só de
  `com.app.tracking`), deve ir a produção sozinha, sem nenhuma outra mudança
  deste épico no mesmo deploy, para que o rollback seja um único revert.
- **Ambiente de desenvolvimento sandboxed:** sem `docker`/`java`/`mvn`/`psql`
  instalados localmente neste ambiente (confirmado em `docs/SCHEMA_BASELINE.md`
  e `api/CLAUDE.md`) — a validação prática de `./mvnw test` para as 5 stories
  deste épico depende do CI ou de um ambiente com Maven/JDK 21 disponível.
- Todo SQL de migration deve seguir as convenções de `api/CLAUDE.md`:
  `IF NOT EXISTS`, nunca `DROP`/`TRUNCATE`, compatível com o modo PostgreSQL do
  H2 usado nos testes.

## 8. Success Metrics

- `GET /api/tracking?status=WATCHED` com 50 tracks: de até 100 queries em
  `users` para no máximo 1.
- `GET /api/tracking` sem `status`, com 30 tracks: de N+1 (proporcional ao
  número de tracks) para número constante de queries.
- `EXPLAIN SELECT * FROM couples WHERE user1_id = ? OR user2_id = ?`: de seq
  scan para index scan.
- Job de limpeza de notificações: de N deletes individuais para 1 `DELETE`.
- 100% das migrations (`V1`–`V3`) executadas e validadas em CI contra Postgres
  real antes de qualquer deploy — 0 migrations chegando a produção sem terem
  rodado antes em algum ambiente automatizado.
- `./mvnw test` verde nas 5 stories, incluindo os testes novos desta epic, sem
  regressão nos 264 testes existentes.
- Zero mudança no JSON de resposta de qualquer endpoint de `/api/tracking`,
  `/api/match` ou `/api/notifications` (comparação byte a byte pré/pós).

## 9. Open Questions

- Nenhuma pendência de decisão de produto — todas as decisões vinculantes já
  estão registradas na tabela "Decisões confirmadas" de `POST-MVP-TASK.md`
  (D1–D12), datada de 2026-08-02, e nenhuma delas conflita com o escopo deste
  épico.
- A validação prática de "sobe contra Postgres limpo com V1+V2+V3" (US-002) e
  dos contadores de query (US-003, US-004, US-005) depende de rodar
  `./mvnw test` num ambiente com Maven/JDK 21 — não disponível neste sandbox.
  Precisa ser executada em CI ou numa máquina/agente com esse ambiente antes de
  marcar as stories como concluídas.
