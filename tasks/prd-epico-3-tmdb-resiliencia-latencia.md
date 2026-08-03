# PRD: Épico 3 — Resiliência e Latência do TMDB

## 1. Introdução/Overview

Este épico tira a API do TMDB do caminho crítico de request e impede que uma
instabilidade externa (TMDB lento ou fora do ar) degrade ou derrube o app. Ele
cobre as tasks T3.1–T3.3 do `POST-MVP-TASK.md`, derivadas da auditoria de
2026-08-02.

Três problemas com a mesma raiz — **o app vai ao TMDB em tempo de leitura para
dados que já conhecia em tempo de escrita**:

1. `TmdbConfig` constrói o `RestClient` **sem nenhum timeout** (nem de conexão,
   nem de leitura). Uma conexão pendurada prende a thread do Tomcat
   indefinidamente, num container com `memory: 512M` / `cpus: 1.0`.
2. `MatchService.getPending` itera até 10 likes pendentes e faz uma chamada HTTP
   ao TMDB **por item, em série** (~2s de thread presa por abertura da aba
   Sugestões).
3. `MediaCard.tsx` faz `GET /api/media/{type}/{id}` **por card montado** — cada
   uma virando uma chamada ao TMDB no backend. O Hub renderiza 3 seções ×
   `PAGE_SIZE` cards; com 30 cards são 33 requests, e
   `HubScreen.handleModalSuccess` recarrega as 3 seções, remontando tudo.

A solução é **desnormalização**, não cache — a decisão #5 de `docs/BACKLOG.md`
("Cache TMDB: não por enquanto") continua válida e foi reafirmada em D12 do
`POST-MVP-TASK.md`.

### Divergências entre o backlog e o código atual (verificadas em 2026-08-03)

O Épico 3 foi escrito antes do Épico 2 ser implementado. Três pontos da T3.3
mudaram de tamanho:

- **`DashboardScreen` já consome apenas `GET /api/tracking/stats`** — o item 2 da
  T3.3 ("expandir o `StatsService` para o Dashboard", decisão D8) **já está
  satisfeito**. Sobra apenas confirmar, não implementar.
- **O único consumidor restante de `GET /api/tracking` sem `status` é
  `MatchScreen.tsx:767`** — o Hub já migrou para a variante paginada
  (`GET /api/tracking?status=...&page=...&size=...`) no Épico 2.
- **`@EnableSpringDataWebSupport` não existe no projeto** — o item 5 da T3.3
  (formato instável de `Page<T>`) continua totalmente pendente, e
  `NotificationController.list` também devolve `Page<T>` direto.

## 2. Goals

- Nenhuma chamada ao TMDB pode prender uma thread do Tomcat por mais do que um
  tempo limitado e configurável.
- `GET /api/match/pending` deixa de fazer qualquer chamada ao TMDB para likes
  criados a partir desta entrega.
- Renderizar o Hub com 30 cards passa de **33 requests HTTP para 3**.
- Zero chamadas **novas** ao TMDB no caminho de escrita — a desnormalização
  reaproveita respostas que `addTrack`/`createMatch` já buscam hoje.
- Linhas históricas (anteriores à migration) continuam renderizando título,
  pôster e ano corretamente, e **se auto-curam** na primeira leitura.
- `GET /api/tracking` sem `status` (payload ilimitado) deixa de existir,
  substituído por um endpoint enxuto de chaves.
- `findPendingForUser` deixa de usar `NOT IN` (custo + semântica traiçoeira com
  `NULL`) e passa a `NOT EXISTS`.
- O formato JSON das respostas paginadas fica congelado num DTO próprio, sem
  quebrar nenhum tipo espelhado no `client/`.

## 3. User Stories

### US-001: Timeouts no `RestClient` do TMDB
**Description:** Como operador do sistema, eu quero que toda chamada ao TMDB
tenha timeout de conexão e de leitura, para que uma instabilidade externa vire um
erro rápido (502) em vez de threads penduradas que esgotam o pool do Tomcat.

**Arquivos:** `api/src/main/java/com/app/media/TmdbConfig.java`,
`api/src/main/resources/application.properties`,
`api/src/test/resources/application.properties`,
`api/src/test/java/com/app/media/TmdbConfigTest.java`

**Acceptance Criteria:**
- [ ] `tmdbRestClient` é construído com um `requestFactory`
      (`SimpleClientHttpRequestFactory`) com **connect timeout de 3s** e
      **read timeout de 5s**.
- [ ] Os dois valores são configuráveis por propriedade
      (`tmdb.connect-timeout` / `tmdb.read-timeout`, tipo `Duration`) com esses
      valores como default — seguindo o padrão de `tmdb.base-url`/`tmdb.api-key`
      já existente no mesmo arquivo.
- [ ] As duas propriedades novas são declaradas também em
      `api/src/test/resources/application.properties` — esse arquivo
      **substitui** o principal nos testes de contexto completo
      (`api/CLAUDE.md`), então uma propriedade obrigatória só no principal quebra
      `SessaoADoisApplicationTests.contextLoads` em silêncio.
- [ ] Um TMDB que não responde produz `502 Bad Gateway` em **≤ 6s**, não uma
      thread pendurada. Nenhum tratamento de erro novo é necessário: timeouts
      chegam como `ResourceAccessException`, já capturada e convertida em
      `TmdbUnavailableException` → 502 por `MediaExceptionHandler`.
- [ ] `TmdbConfigTest` cobre: os defaults aplicados quando nenhuma propriedade é
      definida, e os valores customizados quando as propriedades são definidas.
- [ ] `./mvnw test` passa.

---

### US-002: Migration V4 e persistência de título/pôster/ano na escrita
**Description:** Como desenvolvedor do backend, eu quero que título, URL do
pôster e ano de lançamento sejam gravados no banco no momento em que um like ou
um track é criado, para que a leitura desses dados nunca precise ir ao TMDB.

**Arquivos:** nova
`api/src/main/resources/db/migration/V4__denormalize_media_metadata.sql`,
`api/src/main/java/com/app/match/MatchLike.java`, `MatchService.java`,
`LikeRequest.java`, `api/src/main/java/com/app/tracking/MediaTrack.java`,
`MediaTrackService.java`, `docs/SCHEMA_BASELINE.md`, `docs/FLYWAY.md`

**Acceptance Criteria:**
- [ ] Nova migration `V4__denormalize_media_metadata.sql` adiciona a
      `media_track` e a `match_like` as colunas `title` (`VARCHAR(255)`),
      `poster_url` (`VARCHAR(500)`) e `release_year` (`INTEGER`), **todas
      nullable** (para tolerar as linhas históricas).
- [ ] A migration segue as convenções de `api/CLAUDE.md`: `ADD COLUMN IF NOT
      EXISTS`, nenhum `DROP`/`TRUNCATE`, nenhum default no lado do banco, sintaxe
      compatível com o modo PostgreSQL do H2 usado nos testes.
- [ ] `MediaTrack` e `MatchLike` ganham os campos `title`, `posterUrl` e
      `releaseYear` com getters/setters, sem quebrar nenhum construtor existente
      (novos campos entram por setter ou por construtor sobrecarregado, mesmo
      padrão do `Notification` de 7/8 args descrito em `api/CLAUDE.md`).
- [ ] `MediaTrackService.addTrack` popula os três campos a partir da **mesma**
      resposta de `mediaDetailsService.getDetails(...)` que já busca hoje para os
      `genreIds` — a contagem de chamadas ao TMDB em `addTrack` **não muda**.
      O `catch (RuntimeException)` que hoje protege `fetchGenreIds` continua
      valendo: um TMDB flaky nunca pode falhar a criação do track (os três campos
      ficam nulos e a US-003 os preenche depois).
- [ ] `MatchService.createMatch` popula os três campos do `MediaTrack` a partir
      da resposta de `getDetails(...)` que já busca hoje — **zero chamadas novas**.
- [ ] `LikeRequest` ganha `title`, `posterUrl` e `releaseYear` **opcionais** (sem
      `@NotNull`), e `MatchService.like` os persiste no `MatchLike` quando
      presentes. Quando ausentes, o campo fica nulo — **não** se introduz uma
      chamada ao TMDB no `like` (hoje ele não faz nenhuma quando não há match).
- [ ] `MatchServiceTest` e `MediaTrackServiceTest` provam, com verificação de
      interações no mock de `MediaDetailsService`, que a contagem de chamadas ao
      TMDB em `addTrack`, `createMatch` e `like` é **idêntica à de antes**.
- [ ] `docs/SCHEMA_BASELINE.md` reflete as três colunas novas em `media_track` e
      `match_like`.
- [ ] `docs/FLYWAY.md` registra `V4` na lista de migrations e repete a ressalva do
      pooler do Supabase: o deploy que aplicar `V4` deve usar a connection string
      direta (5432) ou o pooler em modo **Session**, nunca o modo Transaction
      (6543), pelo risco já documentado de advisory lock.
- [ ] O teste de migrations (`com.app.migration.FlywayMigrationTest`, US-001 do
      Épico 2) aplica `V1`+`V2`+`V3`+`V4` em sequência e valida as entidades JPA
      contra o schema resultante — sem ele, `V4` iria a produção sem nunca ter
      executado.
- [ ] `./mvnw test` passa.

---

### US-003: Leitura sem TMDB, com auto-cura das linhas históricas
**Description:** Como usuário do app, eu quero que abrir a aba Sugestões e listar
minha biblioteca não dispare nenhuma chamada externa, para que essas telas não
dependam da disponibilidade nem da latência do TMDB.

**Depende de:** US-002

**Arquivos:** `api/src/main/java/com/app/match/MatchService.java`,
`PendingMatchDto.java`, `api/src/main/java/com/app/tracking/MediaTrackResponse.java`,
`MediaTrackMapper.java`, `MediaTrackService.java`

**Acceptance Criteria:**
- [ ] `MatchService.getPending` monta o `PendingMatchDto` a partir dos campos
      persistidos no `MatchLike` (`title`, `posterUrl`), **sem nenhuma chamada ao
      TMDB** quando `title` não é nulo.
- [ ] `PendingMatchDto` ganha `releaseYear`.
- [ ] `MediaTrackResponse` ganha `title`, `posterUrl` e `releaseYear`, populados
      por `MediaTrackMapper` a partir da entidade (o mapper continua sem injetar
      nada além do que já recebe por parâmetro — ver `api/CLAUDE.md`).
- [ ] **Auto-cura (decisão do usuário, 2026-08-03):** para uma linha com `title`
      nulo (criada antes da `V4`), a leitura busca os dados no TMDB **uma vez**,
      devolve na resposta **e persiste** na linha. A leitura seguinte da mesma
      linha não faz chamada nenhuma.
- [ ] O preenchimento acontece dentro de um método `@Transactional`, e uma falha
      do TMDB nesse caminho **não** falha a request: a linha é devolvida com os
      campos nulos (o `client/` já trata `title` ausente com o fallback
      `Título #{tmdbId}`) e será tentada de novo na próxima leitura.
- [ ] `GET /api/match/pending` com 10 itens, todos já preenchidos, executa
      **zero** chamadas ao TMDB — verificado com
      `verify(mediaDetailsService, never()).getDetails(any(), anyLong())`. É a
      contagem de chamadas externas, não um cronômetro, que dita a latência aqui
      (a meta de < 200ms está na seção 8, como métrica pós-deploy).
- [ ] `GET /api/tracking?status=...` com todos os tracks já preenchidos executa
      **zero** chamadas ao TMDB.
- [ ] Existem testes cobrindo: linha nova (lê do banco, zero TMDB), linha
      histórica (uma chamada, persiste, segunda leitura sem chamada) e linha
      histórica com TMDB indisponível (responde 200 com campos nulos, não 502).
- [ ] `MatchControllerTest`, `MatchServiceTest`, `MediaTrackMapperTest` e
      `MediaTrackControllerTest` continuam passando, ajustados aos campos novos.
- [ ] `./mvnw test` passa.

---

### US-004: Frontend — remover o fetch por card do `MediaCard`
**Description:** Como usuário do app, eu quero que o Hub carregue com 3 requests
em vez de 33, para que a lista apareça rápido e não fique piscando enquanto 30
requisições paralelas resolvem uma a uma.

**Depende de:** US-003

**Arquivos:** `client/src/components/MediaCard.tsx`,
`client/src/types/tracking.ts`, `client/src/types/media.ts`,
`client/src/screens/MatchScreen.tsx` (payload de `POST /api/match/like`)

**Decisão de escopo visual (usuário, 2026-08-03):** o card mantém **título,
pôster e ano** (agora vindos do próprio `MediaTrackResponse`); os **chips de
streaming providers saem do card** e continuam existindo apenas no
`MediaDetailModal`, que já busca detalhes sob demanda ao abrir. Provider é dado
volátil e não vale desnormalizar.

**Acceptance Criteria:**
- [ ] O `useEffect` que chama `GET /api/media/{type}/{id}` em `MediaCard.tsx`
      (linhas ~87-94) é **removido**; o componente não faz nenhuma requisição na
      montagem.
- [ ] `MediaCard` renderiza título, pôster e ano a partir das props
      (`track.title`, `track.posterUrl`, `track.releaseYear`), mantendo os
      fallbacks atuais (`Título #{tmdbId}` e o gradiente `hsl(tmdbId % 360)`
      quando não há pôster).
- [ ] Os chips de watch providers saem do `MediaCard`. O restante do layout
      (badge de tipo, estrelas + média, data assistida, opinião, CTA do rodapé)
      permanece **visualmente idêntico** ao atual, sem buracos de espaçamento.
- [ ] `client/src/types/tracking.ts` espelha os campos novos de
      `MediaTrackResponse` (`title: string | null`, `posterUrl: string | null`,
      `releaseYear: number | null`) — sincronização manual, não há codegen
      (`client/CLAUDE.md`).
- [ ] `client/src/types/media.ts` espelha `releaseYear` em `PendingMatch`.
- [ ] `MatchScreen` passa `title`, `posterUrl` e `releaseYear` no corpo de
      `POST /api/match/like` na aba Buscar (o `MediaSearchResult` já tem os três)
      e na aba Sugestões (o `PendingMatch` tem título e pôster; `releaseYear` vai
      quando disponível, senão `null`).
- [ ] Renderizar o Hub com 30 cards dispara **3** requests HTTP (uma por seção),
      não 33 — verificado na aba Network.
- [ ] Nenhum outro consumidor de `MediaDetails` é removido: `MediaDetailModal`,
      `PendingDetailModal`, `ComparisonDialog` e `TitleModal` continuam buscando
      sob demanda como hoje.
- [ ] A skill `frontend-design` foi usada antes de implementar — esta story
      **altera pixels** (remoção dos chips de provider), logo a exceção de
      refatoração pura do `CLAUDE.md` raiz **não** se aplica.
- [ ] `bun run typecheck` e `bun run lint` passam.
- [ ] **Verificação visual pelo dono do projeto, antes do merge/deploy**
      (decisão de 2026-08-03; este sandbox não tem browser — ver seção 9). O
      autor da story entrega com typecheck + lint + revisão estática e sinaliza
      que está pronta para conferência; o dono roda `bun run dev`, abre o Hub e
      confirma: card sem os chips de provider e sem espaçamento morto no lugar
      deles, título/ano/pôster corretos, e um título antigo (pré-`V4`) renderizando
      via fallback ou já auto-curado. A story não fecha sem esse aceite.

---

### US-005: `GET /api/tracking/keys` e remoção do endpoint não paginado
**Description:** Como usuário do app, eu quero que a tela de Match saiba quais
títulos já estão na nossa lista sem baixar a biblioteca inteira do casal, para
que o payload não cresça sem limite a cada título adicionado.

**Arquivos:** `api/src/main/java/com/app/tracking/MediaTrackController.java`,
`MediaTrackService.java`, `MediaTrackRepository.java`,
`client/src/screens/MatchScreen.tsx`

**Acceptance Criteria:**
- [ ] Existe `GET /api/tracking/keys`, escopado ao casal do JWT (mesmo helper
      `currentCoupleId(userId)` dos demais endpoints do controller), devolvendo
      `[{mediaType, tmdbId}]` — sem reviews, sem metadados.
- [ ] O endpoint é servido por uma projeção no repositório (só as duas colunas),
      não por carregamento de entidades — mesma orientação de `api/CLAUDE.md`
      para consultas de leitura enxuta.
- [ ] `MatchScreen.tsx:767` passa a consumir `/api/tracking/keys` para montar o
      `Set` de chaves `mediaType-tmdbId`; o comportamento da tela (marcar como
      "já na lista") é idêntico ao atual.
- [ ] `DashboardScreen` continua renderizando os mesmos números de antes. **Já
      verificado em 2026-08-03:** a tela consome apenas
      `GET /api/tracking/stats` e todas as suas ~20 referências a dados são
      campos de `StatsResponse` (`stats.*`) — a decisão D8 já está satisfeita e
      **nenhuma expansão do `StatsService` faz parte deste épico**. Esta story só
      não pode regredir isso.
- [ ] O endpoint `GET /api/tracking` **sem** `status`
      (`MediaTrackController` `@GetMapping(params = "!status")`) é **removido**,
      junto com o caminho `status == null` de `MediaTrackService.listByStatus` se
      ficar órfão.
- [ ] `grep -rn "api/tracking\"" client/src` não retorna nenhuma chamada ao
      endpoint removido.
- [ ] `MediaTrackControllerTest` cobre o endpoint novo (inclusive o 404 de
      usuário sem casal) e deixa de cobrir o removido.
- [ ] Backend e frontend desta story vão ao **mesmo deploy** — a remoção do
      endpoint é breaking para um cliente antigo.
- [ ] `./mvnw test`, `bun run typecheck` e `bun run lint` passam.
- [ ] Sem mudança visual: `MatchScreen` só troca a origem dos dados, então a
      skill `frontend-design` **não** se aplica (decisão D11).

---

### US-006: `findPendingForUser` com `NOT EXISTS`
**Description:** Como usuário do app, eu quero que a busca por sugestões
pendentes use uma consulta que aproveite os índices criados no Épico 2 e não
tenha a semântica traiçoeira de `NOT IN` com `NULL`.

**Arquivos:** `api/src/main/java/com/app/match/MatchLikeRepository.java`,
`api/src/test/java/com/app/match/MatchLikeRepositoryTest.java`

**Acceptance Criteria:**
- [ ] As 3 subconsultas `NOT IN` de `findPendingForUser` viram `NOT EXISTS`
      correlacionados (like próprio, reject próprio, track já existente).
- [ ] O conjunto de resultados é **exatamente o mesmo** de antes — teste
      comparativo cobrindo os mesmos cenários com a query antiga e a nova.
- [ ] `MatchLikeRepositoryTest` cobre: nenhum like do parceiro, likes já
      rejeitados pelo usuário atual, likes já rastreados como `MediaTrack`, e
      like do parceiro ainda pendente (o único que deve aparecer).
- [ ] A paginação (`PageRequest.of(0, 10)`) continua funcionando.
- [ ] `./mvnw test` passa.

---

### US-007: DTO de página próprio para as respostas paginadas
**Description:** Como desenvolvedor, eu quero que o formato JSON das respostas
paginadas seja um contrato explícito do projeto, para que uma atualização do
Spring não mude a estrutura e quebre o frontend em silêncio.

**Arquivos:** novo `PageResponse.java` (em `com.app` ou `com.app.common`),
`api/src/main/java/com/app/tracking/MediaTrackController.java`,
`api/src/main/java/com/app/notification/NotificationController.java`

**Decisão (usuário, 2026-08-03):** DTO próprio preservando os campos atuais —
**não** `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`, que
moveria os metadados para um objeto aninhado `page` e exigiria alterar os tipos
espelhados e as telas do `client/`.

**Acceptance Criteria:**
- [ ] Existe um record `PageResponse<T>` com exatamente os campos que o cliente
      já espelha hoje: `content`, `totalElements`, `totalPages`, `number`,
      `size`.
- [ ] `MediaTrackController.listByStatus` e `NotificationController.list`
      devolvem `PageResponse<T>` em vez de `Page<T>`.
- [ ] O JSON de resposta dos dois endpoints é **idêntico** ao atual nos 5 campos
      que o `client/` consome — `client/src/types/tracking.ts` e
      `client/src/types/notification.ts` **não** precisam mudar, e `HubScreen` e
      `NotificationDropdown` continuam funcionando sem alteração.
- [ ] O warning *"Serializing PageImpl instances as-is is not supported"* não
      aparece mais no log ao executar os testes desses dois endpoints.
- [ ] `MediaTrackControllerTest` e os testes de `NotificationController` passam,
      ajustados ao tipo novo.
- [ ] `./mvnw test` e `bun run typecheck` passam.

## 4. Functional Requirements

1. O `RestClient` do TMDB deve ter connect timeout e read timeout configuráveis,
   com defaults de 3s e 5s respectivamente.
2. Uma chamada ao TMDB que estoure o timeout deve resultar em `502 Bad Gateway`
   em no máximo 6s, pelo caminho de erro já existente
   (`ResourceAccessException` → `TmdbUnavailableException` → 502).
3. As tabelas `media_track` e `match_like` devem ter colunas nullable `title`,
   `poster_url` e `release_year`, criadas pela migration `V4`.
4. `MediaTrackService.addTrack` e `MatchService.createMatch` devem persistir
   esses três campos reaproveitando a resposta de `MediaDetailsService` que já
   buscam — sem nenhuma chamada nova ao TMDB.
5. `MatchService.like` deve aceitar `title`, `posterUrl` e `releaseYear`
   opcionais no `LikeRequest` e persistí-los, sem introduzir chamada ao TMDB.
6. `GET /api/match/pending` deve ler exclusivamente dos campos persistidos para
   linhas preenchidas.
7. Uma linha com `title` nulo (anterior à `V4`) deve ser preenchida a partir do
   TMDB na primeira leitura e **persistida**, de forma que leituras seguintes não
   façam chamada externa.
8. Uma falha do TMDB durante esse preenchimento não pode falhar a request nem a
   escrita — os campos ficam nulos e são tentados de novo depois.
9. `MediaTrackResponse` deve expor `title`, `posterUrl` e `releaseYear`.
10. `MediaCard` não pode fazer nenhuma requisição HTTP na montagem; o que ele
    ainda precisar do TMDB e não estiver persistido deve ser buscado sob demanda
    (ao abrir o `MediaDetailModal`).
11. Deve existir `GET /api/tracking/keys` devolvendo apenas `mediaType` e
    `tmdbId` por track, escopado ao casal do JWT.
12. `GET /api/tracking` sem `status` deve ser removido, e nenhum código do
    `client/` pode referenciá-lo.
13. `MatchLikeRepository.findPendingForUser` deve usar `NOT EXISTS`, com conjunto
    de resultados inalterado.
14. Endpoints paginados devem devolver um DTO de página próprio, com os mesmos
    5 campos que o `client/` já espelha.

## 5. Non-Goals (Out of Scope)

- **Cache do TMDB** (Caffeine, `@Cacheable` ou qualquer outro). A desnormalização
  desta epic **substitui** a necessidade dele no caminho quente. Se depois de
  medir o cache ainda fizer sentido, vira task própria e exige revisitar
  formalmente a decisão #5 de `docs/BACKLOG.md` (reafirmada em D12).
- **Retry e circuit breaker** no cliente TMDB — a US-001 é só timeout.
- **Desnormalizar watch providers** — dado volátil, ficaria desatualizado sem um
  job de refresh. Continuam vindo do `MediaDetails` sob demanda, agora só no
  modal de detalhes.
- **Job de refresh dos campos desnormalizados** — título e pôster do TMDB são
  praticamente imutáveis; não há necessidade de reconciliação periódica.
- **Redesenhar as telas.** Fora a remoção dos chips de provider do card
  (US-004), nenhuma tela muda de layout — só a origem dos dados.
- **Paginar ou expandir o `StatsService`** — a decisão D8 já está satisfeita
  (`DashboardScreen` só consome `/api/tracking/stats`); a US-005 apenas confirma.
- **`@EnableSpringDataWebSupport(VIA_DTO)`** — descartado em favor do DTO próprio
  para não alterar o contrato com o `client/`.
- **Backfill em massa das linhas históricas** por script ou endpoint
  administrativo — a estratégia escolhida é auto-cura na leitura (US-003).
- Qualquer mudança de autenticação, segurança ou WebSocket — Épicos 1, 4 e 5.

## 6. Design Considerations

Apenas a **US-004** tem componente visual, e ela é a única story deste épico
sujeita à skill `frontend-design` (regra do `CLAUDE.md` raiz; as demais são
backend puro ou troca de origem de dados sem mudança de pixel, dispensadas por
D11).

- O protótipo (`docs/design/claude-design-project/Sessao a Dois.dc.html`) é a
  fonte de verdade de cores e espaçamentos. No card do Hub ele mostra os chips de
  streaming ao lado do badge de tipo — removê-los é um **desvio consciente** do
  protótipo, motivado por performance, e a linha de chips remanescente (só o
  badge `Filme`/`Série`) precisa continuar visualmente equilibrada, sem espaço
  morto onde antes havia dois chips.
- Ano e título continuam na mesma linha (`flex items-baseline justify-between`),
  como hoje e como no protótipo.
- Os estados de fallback já existentes (`Título #{tmdbId}`, gradiente
  `hsl(tmdbId % 360)` sem pôster, ícone 🍿 em `WANT_TO_SEE`) passam a ser o que
  aparece para linhas históricas ainda não auto-curadas — devem continuar
  funcionando exatamente como hoje.
- Cores/tokens seguem o padrão do `client/CLAUDE.md`: valores arbitrários do
  Tailwind (`bg-[#161513]`, `text-[#a6a39a]`, ...), não tokens de tema novos.

## 7. Technical Considerations

- **Ordem obrigatória:** US-002 → US-003 → US-004. US-001, US-005, US-006 e
  US-007 não dependem de nada e podem ser feitas em qualquer ordem/paralelo.
- **Deploy:**
  - US-002 + US-003 + US-004 vão ao **mesmo deploy** (decisão do usuário) — o
    frontend depende dos campos novos na resposta. O `scripts/deploy.sh` já sobe
    front e back juntos.
  - US-005 também exige front e back no mesmo deploy (remoção de endpoint).
  - Esse deploy aplica a `V4`: usar `DB_URL` com a connection string **direta
    (5432)** ou o pooler em modo **Session** do Supabase, nunca o modo
    Transaction (6543) — risco de advisory lock do Flyway documentado em
    `docs/FLYWAY.md`.
- **`./mvnw test` só valida a `V4` por causa do Épico 2.** O
  `FlywayMigrationTest` (US-001 do Épico 2) é o único teste que executa as
  migrations de verdade; sem ele, uma `V4` quebrada passaria verde. Confirmar que
  ele está no lugar antes de começar a US-002.
- **Convenções de migration** (`api/CLAUDE.md`): `IF NOT EXISTS`, sem
  `DROP`/`TRUNCATE`, sem default de banco em coluna gerada pela app, compatível
  com H2 em modo PostgreSQL. `LocalDateTime` → `TIMESTAMP`, `Instant` →
  `TIMESTAMP WITH TIME ZONE` (não se aplica às colunas desta epic, todas texto/
  inteiro, mas vale para não escorregar).
- **Auto-cura e transação:** com `spring.jpa.open-in-view=false` (Épico 2), o
  método que preenche a linha antiga e a persiste precisa ser `@Transactional` —
  caso contrário a escrita não acontece e/ou o acesso LAZY explode com
  `LazyInitializationException`. A chamada ao TMDB dentro de uma transação
  aberta é aceitável **porque a US-001 já garante o timeout** (por isso US-001 é
  fortemente recomendada antes do deploy da US-003).
- **`MediaTrackMapper` não injeta dependências novas** (`api/CLAUDE.md`): ele
  recebe o que precisa por parâmetro. A lógica de auto-cura fica no service, não
  no mapper.
- **Contagem de chamadas ao TMDB** é critério de aceite em três stories — o
  padrão de verificação é `Mockito.verify(mediaDetailsService, times(n))` sobre o
  mock, e `MediaTrackQueryCountTest` (Épico 2) é o modelo para provar contagens
  que não devem escalar com N.
- **Ambiente sandboxed:** `api/CLAUDE.md` confirma que `./mvnw test` **roda** no
  devcontainer (JDK 21 + Maven 3.9.16) — nunca declarar uma story de backend
  concluída sem executá-lo. Já `docker` e `psql` não existem: a validação de
  sintaxe Postgres-only da `V4` acontece no CI, contra o `postgres:17-alpine` do
  workflow.
- **Sem codegen entre backend e frontend** (`client/CLAUDE.md`): todo campo novo
  em `MediaTrackResponse`/`PendingMatchDto` tem que ser espelhado à mão em
  `client/src/types/*.ts`.

## 8. Success Metrics

- `GET /api/match/pending` com 10 itens: de ~10 chamadas HTTP ao TMDB em série
  (~2s) para **0 chamadas** e **< 200ms**.
- Hub com 30 cards: de **33 requests HTTP para 3**.
- `HubScreen.handleModalSuccess` (recarga das 3 seções após salvar um título): de
  33 requests para 3.
- TMDB indisponível: de thread pendurada por tempo indeterminado para **502 em
  ≤ 6s**.
- `GET /api/tracking` (payload proporcional a toda a biblioteca do casal, com
  reviews e metadados): **removido**; substituído por `/api/tracking/keys`, com
  2 campos por track.
- `findPendingForUser`: de 3 subconsultas `NOT IN` para 3 `NOT EXISTS`, com o
  mesmo resultado.
- Linhas históricas: 1 chamada ao TMDB **por linha, uma única vez na vida**, em
  vez de 1 por leitura.
- `./mvnw test` verde nas 6 stories de backend, `bun run typecheck` + `bun run
  lint` verdes nas 2 stories com frontend, sem regressão na suíte existente.

## 9. Open Questions

**Nenhuma pendência em aberto.** Todas as questões deste épico foram resolvidas
em 2026-08-03; o registro abaixo existe para que uma decisão não seja reaberta
sem motivo novo.

### Decisões de escopo (resolvidas em 2026-08-03)

| # | Questão | Decisão | Onde vive |
|---|---------|---------|-----------|
| E1 | O que desnormalizar, já que o card perde `year` e providers | Persistir `title`, `posterUrl` **e** `releaseYear`; os chips de provider saem do card e ficam só no `MediaDetailModal` (dado volátil não se desnormaliza) | US-002, US-004, seção 6 |
| E2 | Linhas anteriores à `V4`, com `title` nulo | **Auto-cura**: busca no TMDB na primeira leitura, devolve **e persiste**; sem backfill em massa | US-003 |
| E3 | Formato JSON de `Page<T>` | DTO próprio `PageResponse<T>` com os 5 campos atuais — **não** `@EnableSpringDataWebSupport(VIA_DTO)`, que mudaria o contrato com o `client/` | US-007 |
| E4 | Fatiamento da T3.2 | 3 stories (migration+escrita / leitura / frontend), todas no **mesmo deploy** | US-002–US-004, seção 7 |

### Pendências de verificação (resolvidas em 2026-08-03)

| # | Questão | Resolução |
|---|---------|-----------|
| V1 | `DashboardScreen` precisa de algum número que o `StatsService` ainda não fornece? (D8) | **Não.** Verificado no código: a tela só chama `GET /api/tracking/stats` e todas as suas referências a dados são campos de `StatsResponse`. Nenhuma expansão do `StatsService` entra neste épico. Se um dia faltar um número, a regra de `api/CLAUDE.md` vale: começa por uma agregação nova no repositório, em story própria. |
| V2 | Como verificar a US-004 sem browser no sandbox | **Aceite visual do dono do projeto antes do merge/deploy.** O autor entrega com `typecheck` + `lint` + revisão estática e sinaliza; o dono roda `bun run dev` e confere o card (sem chips de provider, sem espaçamento morto, título/ano/pôster corretos, um título pré-`V4` renderizando por fallback ou já auto-curado). A skill `dev-browser` não é executável aqui (`client/CLAUDE.md`: sem Chromium/Playwright e sem sudo para as libs de sistema). |
| V3 | Como verificar o "< 200ms" da US-003 | **Substituído por um critério verificável em teste:** zero chamadas ao TMDB (`verify(..., never())`) — é a contagem de chamadas externas que dita a latência. Os 200ms permanecem na seção 8 como métrica pós-deploy, medida em produção, e não bloqueiam o fechamento da story. |

### O que continua fora de discussão

As decisões vinculantes D1–D12 de `POST-MVP-TASK.md` (2026-08-02) seguem
válidas, em especial **D12**: cache do TMDB continua descartado, e este épico
resolve a latência por desnormalização. Reabrir isso exige revisitar formalmente
a decisão #5 de `docs/BACKLOG.md`, com medição em mãos.
