# PRD: Descobrir — Busca Avançada, Filtros e Ordenação (Tela de Match)

## Introdução / Visão Geral

A tela de **Match** do Sessão a Dois permite que o casal descubra filmes e séries do
catálogo TMDB e os "curta". Quando os dois curtem o mesmo título, é gerado um **Match** e a
obra vai automaticamente para a lista **Queremos Ver**.

Hoje a aba **Descobrir** (implementada como `SearchTab` em `client/src/screens/MatchScreen.tsx`)
só oferece **busca textual simples** (ex.: "Naruto", "Flash") contra
`GET /api/media/search?q=`. Não há filtros, ordenação, contador de resultados, paginação nem
um estado inicial — a aba fica **vazia** até o usuário digitar algo.

Este PRD cobre a evolução **exclusiva da aba Descobrir** para uma experiência de **descoberta
ativa avançada** usando os dados do TMDB: painel de filtros expansível, ordenação, busca por
texto, estado inicial "Em alta esta semana", contador de resultados e grid paginado de
**exatamente 20 itens por página/requisição**. O casal continua curtindo direto pelo card
(botão de curtir), reutilizando a lógica de match existente.

**Fora deste PRD:** a aba **Sugestões** (deck de swipe) já está finalizada e **não deve ser
alterada**.

## Objetivos

- Permitir descoberta ativa avançada na aba Descobrir combinando busca textual, filtros e
  ordenação sobre os dados do TMDB.
- Exibir um estado inicial útil ("Em alta esta semana") em vez de tela vazia.
- Garantir **paginação de exatamente 20 itens por página/requisição** em todos os resultados
  (busca, descoberta e trending).
- Manter fidelidade visual à referência (`docs/design/prints/matchpage-search-with-filters.jpeg`)
  e ao design system do projeto (Bricolage Grotesque / DM Sans, tema escuro, âmbar `#ffcb2b`).
- Permitir curtir um título diretamente pelo card do grid, reutilizando `POST /api/match/like`.
- Não regredir nem alterar a aba Sugestões.

## User Stories

As stories estão ordenadas por dependência (backend primeiro, depois frontend). Cada uma é
pequena o suficiente para uma sessão focada.

### US-001: Endpoint de "Em alta esta semana" (trending) paginado
**Description:** Como usuário, quero ver títulos em alta ao abrir a aba Descobrir, para ter um
ponto de partida sem precisar digitar nada.

**Acceptance Criteria:**
- [ ] Novo endpoint `GET /api/media/trending` no `MediaController` (módulo `com.app.media`).
- [ ] Consome `GET /trending/all/week` da TMDB v3 (RestClient existente, header Bearer,
      interceptor pt-BR já configurado).
- [ ] Aceita parâmetro `page` (default 1); repassa `page` ao TMDB. TMDB retorna 20 itens/página
      nativamente — a resposta deve conter **no máximo 20 itens**.
- [ ] Retorna objeto de página com: `results` (lista de `MediaSearchResult`), `page`,
      `totalResults`, `totalPages`.
- [ ] Itens de `person` retornados pelo `trending/all` são filtrados (apenas MOVIE/TV).
- [ ] Testes JUnit/Mockito para controller e service (sucesso, TMDB indisponível, filtragem de
      pessoas).
- [ ] `mvn -q -pl api test` passa.

### US-002: DTO de página paginada reutilizável
**Description:** Como desenvolvedor, quero um contrato de paginação padrão para busca,
descoberta e trending, para o frontend tratar todos igual.

**Acceptance Criteria:**
- [ ] Novo record `MediaPage` (ou similar) com `List<MediaSearchResult> results`, `int page`,
      `int totalResults`, `int totalPages`.
- [ ] `totalResults` limitado ao teto do TMDB quando aplicável (ver Considerações Técnicas).
- [ ] Testes cobrindo mapeamento a partir da resposta TMDB.
- [ ] `mvn -q -pl api test` passa.

### US-003: Migrar `GET /api/media/search` para resposta paginada
**Description:** Como usuário, quero paginar os resultados da busca textual, vendo 20 por vez.

**Acceptance Criteria:**
- [ ] `GET /api/media/search` aceita `page` (default 1) além de `q`.
- [ ] Retorna `MediaPage` (20 itens/página) em vez de `List<MediaSearchResult>`.
- [ ] `q` continua obrigatório e não vazio (mantém `InvalidSearchQueryException` → 400).
- [ ] Testes existentes de `MediaControllerTest`/`MediaSearchServiceTest` atualizados e passando.
- [ ] `mvn -q -pl api test` passa.

### US-004: Endpoint de descoberta com filtros e ordenação (TMDB Discover)
**Description:** Como usuário, quero filtrar e ordenar o catálogo para encontrar títulos que
combinam com o casal.

**Acceptance Criteria:**
- [ ] Novo endpoint `GET /api/media/discover` no `MediaController`.
- [ ] Parâmetros aceitos (todos opcionais, exceto `page` com default 1):
      `mediaType` (`MOVIE`|`TV`|ambos), `genres` (CSV de IDs TMDB), `releaseDecades`
      (ex.: `2020,2010`), `certifications` (`L,10,12,14,16,18`), `voteAverageMin`,
      `voteAverageMax`, `runtimeMin`, `runtimeMax`, `sortBy`, `page`.
- [ ] Mapeia para `GET /discover/movie` e/ou `GET /discover/tv` com: `with_genres`,
      `primary_release_date.gte/.lte` (movie) e `first_air_date.gte/.lte` (tv) derivados das
      décadas, `vote_average.gte/.lte`, `with_runtime.gte/.lte`, `sort_by`, e
      `certification_country=BR` + `certification.lte` (ver US-005).
- [ ] **Décadas (decisão):** múltiplas décadas selecionadas viram **um único intervalo
      min–max** (menor data … maior data), mesmo que não contíguas. Ex.: Anos 2020 + Anos 2000
      → `2000-01-01`..`2029-12-31` (inclui 2010).
- [ ] **Filtros x busca (decisão):** o modo descoberta é **mutuamente exclusivo** da busca
      textual — quando há termo, `/discover` não é usado (ver US-011/US-008).
- [ ] `sortBy` suporta: `popularity.desc`, `popularity.asc`, `vote_average.desc`,
      `vote_average.asc`, `release_date.desc`, `release_date.asc` (mapeando para o campo de data
      correto por tipo de mídia).
- [ ] **Combinação Filme + Série (decisão):** no modo "ambos", o backend chama
      `/discover/movie` e `/discover/tv`, **mescla os resultados por `sort_by` e fatia em 20**
      por página, garantindo NFR-1. `totalResults` é a soma (aproximada) dos dois.
- [ ] Retorna `MediaPage` com **exatamente 20 itens por página**.
- [ ] Valores inválidos de enum (sortBy, mediaType, certification) retornam 400 com mensagem.
- [ ] Testes JUnit/Mockito cobrindo construção da URL do TMDB para cada filtro e ordenação.
- [ ] `mvn -q -pl api test` passa.

### US-005: Filtro de classificação indicativa por certificação BR do TMDB
**Description:** Como usuário, quero filtrar por classificação indicativa brasileira
(Livre/10/12/14/16/18).

**Acceptance Criteria:**
- [ ] Mapeamento de chips → certificação TMDB BR: `Livre→L`, `10→10`, `12→12`, `14→14`,
      `16→16`, `18→18`.
- [ ] Requisições de filme enviam `certification_country=BR` + **`certification.lte`**
      (semântica "mais permissiva"): marcar `14` traz `L,10,12,14`. Com múltiplas marcadas,
      usa-se a **maior** classificação selecionada como teto.
- [ ] Comportamento para séries (TV) documentado: o TMDB **não** suporta `certification` em
      `/discover/tv`; nesse caso o filtro é ignorado para TV e isso é registrado em
      Considerações Técnicas (não quebra a requisição).
- [ ] Testes cobrindo a montagem de `certification_country`/`certification`.
- [ ] `mvn -q -pl api test` passa.

### US-006: Endpoint de metadados de gêneros
**Description:** Como desenvolvedor, quero a lista de gêneros TMDB (com IDs) para renderizar os
chips corretamente.

**Acceptance Criteria:**
- [ ] Endpoint `GET /api/media/genres` retornando gêneros (id + nome pt-BR) de filme e série,
      deduplicados por nome quando aplicável, reutilizando `TmdbGenre`.
- [ ] Fonte: `GET /genre/movie/list` e `GET /genre/tv/list` do TMDB.
- [ ] Testes JUnit/Mockito para controller e service.
- [ ] `mvn -q -pl api test` passa.

### US-007: Abas Descobrir/Sugestões e cabeçalho da Descobrir (frontend)
**Description:** Como usuário, quero abrir a aba Descobrir e já ver o título e o input de busca
conforme o layout aprovado.

**Acceptance Criteria:**
- [ ] A aba **Descobrir** (`activeTab === "search"`) mantém as abas Descobrir/Sugestões
      existentes; **Sugestões não é alterada**.
- [ ] Cabeçalho central: eyebrow "Match a Dois", título "Descubram o próximo juntos", subtítulo.
- [ ] Input de busca com ícone de lupa e placeholder "Ex.: Coração de Vidro, Fronteira Norte…".
- [ ] Usa a skill `frontend-design`; tokens do design system (fundo `#09090a`, card `#161513`,
      primário `#ffcb2b`, texto `#f6f4ec`, muted `#a6a39a`, fontes Bricolage/DM Sans).
- [ ] Typecheck/lint passa (`bun run build` / `tsc`).
- [ ] Verificar no browser com a skill `dev-browser`.

### US-008: Botão "Filtros" com badge e dropdown de ordenação
**Description:** Como usuário, quero abrir/fechar o painel de filtros e escolher a ordenação.

**Acceptance Criteria:**
- [ ] Botão "Filtros" com ícone de sliders, chevron que indica expandido/recolhido e **badge
      numérico** com a quantidade de filtros ativos (oculto quando 0).
- [ ] Dropdown de ordenação com rótulo + seta (ex.: "Popularidade ↓") e opções:
      Popularidade (↓/↑), Avaliação (↓/↑), Data de Lançamento (↓/↑).
- [ ] **Enquanto houver texto na busca, o botão Filtros e o dropdown de ordenação ficam
      desabilitados** (modos mutuamente exclusivos); ao limpar o texto, voltam a ficar ativos.
- [ ] Selecionar ordenação re-dispara a descoberta a partir da página 1.
- [ ] Ordenação usa shadcn `Select`/`DropdownMenu`; painel expansível usa `Collapsible`
      (skill `shadcn`).
- [ ] Typecheck/lint passa.
- [ ] Verificar no browser com a skill `dev-browser`.

### US-009: Painel de filtros expansível — chips (Data, Classificação, Gêneros)
**Description:** Como usuário, quero selecionar décadas, classificação indicativa e gêneros por
chips.

**Acceptance Criteria:**
- [ ] Seção **DATA DE LANÇAMENTO**: chips Anos 2020, Anos 2010, Anos 2000, Anos 1990, Anterior
      (seleção múltipla). Múltiplas décadas viram **um intervalo min–max** no backend (US-004),
      ou seja, décadas do meio de um intervalo não contíguo entram no resultado.
- [ ] Seção **CLASSIFICAÇÃO INDICATIVA**: chips Livre, 10, 12, 14, 16, 18 (seleção múltipla).
- [ ] Seção **GÊNEROS**: chips de múltipla escolha carregados de `GET /api/media/genres`
      (Drama, Crime, Ação, Aventura, Comédia, Fantasia, Ficção científica, Mistério, Romance,
      Terror, Thriller, Documentário, …).
- [ ] Chip ativo usa estado âmbar (borda/preenchimento `#ffcb2b`); inativo usa pílula neutra.
- [ ] Chips implementados com shadcn `ToggleGroup`/`Toggle` (múltipla escolha), adicionados via
      skill `shadcn`.
- [ ] Estado de filtros é local até "Aplicar filtros" (ver US-011); a contagem do badge reflete
      a seleção atual.
- [ ] Typecheck/lint passa.
- [ ] Verificar no browser com a skill `dev-browser`.

### US-010: Painel de filtros — range sliders (Nota TMDB e Duração)
**Description:** Como usuário, quero limitar por nota TMDB e por duração usando sliders de
intervalo.

**Acceptance Criteria:**
- [ ] **NOTA TMDB**: range slider de 0 a 10 (passo 0,5), exibindo o intervalo selecionado
      (ex.: "7,0 – 10").
- [ ] **DURAÇÃO**: range slider de 0 a 240 min, exibindo rótulo (ex.: "ATÉ 3H") e limites
      "0 min" / "240 min".
- [ ] Ambos com dois thumbs (min e max) e trilha preenchida em âmbar.
- [ ] Implementados com shadcn `Slider` (modo range/dois thumbs), adicionado via skill `shadcn`.
- [ ] Alterações contam para o badge de filtros ativos.
- [ ] Typecheck/lint passa.
- [ ] Verificar no browser com a skill `dev-browser`.

### US-011: Ações do painel — "Limpar tudo" e "Aplicar filtros"
**Description:** Como usuário, quero aplicar os filtros escolhidos ou limpar tudo de uma vez.

**Acceptance Criteria:**
- [ ] "Limpar tudo" (text button) reseta todos os filtros e o slider aos padrões; badge zera.
- [ ] "Aplicar filtros" (primary button âmbar) dispara `GET /api/media/discover` com os filtros
      selecionados, começando na página 1, e fecha (ou mantém, conforme UX) o painel.
- [ ] O painel de filtros só está disponível quando **não** há texto na busca (modos mutuamente
      exclusivos). Digitar um termo limpa/ignora o modo descoberta e usa `/search`.
- [ ] Typecheck/lint passa.
- [ ] Verificar no browser com a skill `dev-browser`.

### US-012: Área de resultados — header dinâmico e contador
**Description:** Como usuário, quero um cabeçalho que reflete o que estou vendo e quantos
títulos retornaram.

**Acceptance Criteria:**
- [ ] Estado inicial (sem busca/filtros): título "Em alta esta semana" + subtítulo "Resultados
      dinâmicos do TMDB — refine com o painel de filtros."
- [ ] Ao buscar/filtrar, o header reflete o contexto (ex.: 'Resultados para "Flash"' ou
      "Filtros aplicados").
- [ ] Contador de títulos retornados alinhado à direita (ex.: "128 títulos"), refletindo
      `totalResults`.
- [ ] Typecheck/lint passa.
- [ ] Verificar no browser com a skill `dev-browser`.

### US-013: Grid de cards com tipo, nota e botão de curtir
**Description:** Como usuário, quero ver cada título como card com o tipo (Filme/Série), a nota
TMDB e curtir direto dali.

**Acceptance Criteria:**
- [ ] Grid responsivo `repeat(auto-fill, minmax(250px, 1fr))`.
- [ ] Cada card mostra: pôster (ou gradiente placeholder atual), badge de **tipo**
      ("Filme"/"Série") no canto superior esquerdo e **badge de nota** verde
      (ponto `#01b47f` + valor, ex.: "8.4") no canto superior direito.
- [ ] **Botão de curtir** por card (coração) que chama `POST /api/match/like`
      `{ tmdbId, mediaType }`, reutilizando a lógica existente.
- [ ] Estados do curtir: idle → loading → liked/matched/error; título já rastreado aparece como
      já curtido (reaproveitar `GET /api/tracking` como hoje).
- [ ] Match dispara a celebração já existente (sem alterar a lógica de match/WebSocket).
- [ ] Typecheck/lint passa.
- [ ] Verificar no browser com a skill `dev-browser`.

### US-014: Paginação de 20 itens no grid (frontend)
**Description:** Como usuário, quero navegar entre páginas de 20 títulos.

**Acceptance Criteria:**
- [ ] O grid renderiza **no máximo 20 cards por página** (uma página = uma requisição).
- [ ] **Paginador numérico** (páginas 1, 2, 3… com anterior/próxima) que solicita a página via
      `page`; a página atual é destacada. O grid é **substituído** a cada troca de página.
- [ ] Implementado com shadcn `Pagination`, adicionado via skill `shadcn`.
- [ ] `page` reinicia em 1 sempre que busca, filtros ou ordenação mudam.
- [ ] Estado de loading e de "fim dos resultados" (quando `page === totalPages`).
- [ ] Não exceder o teto de páginas do TMDB (ver Considerações Técnicas).
- [ ] Typecheck/lint passa.
- [ ] Verificar no browser com a skill `dev-browser`.

### US-015: Estados vazios e de erro da Descobrir
**Description:** Como usuário, quero feedback claro quando não há resultados ou o TMDB falha.

**Acceptance Criteria:**
- [ ] Busca/filtros sem resultados: mensagem vazia amigável (mantendo o padrão atual de card
      tracejado).
- [ ] Falha do TMDB (`TmdbUnavailableException` → 5xx): mensagem de erro com opção de tentar de
      novo, sem quebrar a aba.
- [ ] Trending indisponível no load inicial degrada para estado vazio informativo.
- [ ] Typecheck/lint passa.
- [ ] Verificar no browser com a skill `dev-browser`.

## Requisitos Funcionais

- **FR-1:** A aba Descobrir deve exibir, ao carregar sem busca/filtros, os títulos "Em alta
  esta semana" (TMDB `trending/all/week`), paginados em 20 itens.
- **FR-2:** O sistema deve oferecer busca textual por título (`q`) com resultados paginados de
  20 itens.
- **FR-3:** O sistema deve oferecer um painel de filtros expansível com: Data de Lançamento
  (chips por década, múltipla), Classificação Indicativa (chips L/10/12/14/16/18, múltipla),
  Gêneros (chips múltipla), Nota TMDB (range slider 0–10) e Duração (range slider 0–240 min).
- **FR-4:** O botão "Filtros" deve exibir um badge com a contagem de filtros ativos e um chevron
  de expandido/recolhido.
- **FR-5:** O sistema deve oferecer ordenação por Popularidade, Avaliação e Data de Lançamento,
  cada uma com variação crescente e decrescente.
- **FR-6:** O painel deve ter "Limpar tudo" (reseta filtros) e "Aplicar filtros" (executa a
  descoberta a partir da página 1).
- **FR-6.1:** Busca textual e filtros/ordenação são **mutuamente exclusivos**: havendo texto na
  busca, os filtros e o dropdown de ordenação ficam desabilitados (usa `/search`); sem texto,
  o modo descoberta (`/discover`) fica disponível.
- **FR-7:** A classificação indicativa deve filtrar pela **certificação BR** do TMDB
  (`certification_country=BR`) para filmes; o comportamento para séries deve estar documentado.
- **FR-8:** A área de resultados deve exibir um header dinâmico e um contador de títulos
  retornados (`totalResults`).
- **FR-9:** Cada card deve indicar o tipo (Filme/Série) e a nota TMDB, e permitir curtir via
  `POST /api/match/like`, reutilizando a lógica de match existente.
- **FR-10:** O grid deve paginar em **exatamente 20 itens por página/requisição**, via
  **paginador numérico** (páginas 1, 2, 3… com anterior/próxima).
- **FR-11:** A aba Sugestões (deck de swipe) **não** deve ser modificada por este trabalho.
- **FR-12:** O frontend não consome o TMDB diretamente; toda integração passa pelo backend
  (`com.app.media`), conforme `ARCHITECTURE.md`.

## Requisitos Não-Funcionais

- **NFR-1 (Paginação):** Todas as listagens da Descobrir (busca, descoberta, trending) devem
  retornar e renderizar **no máximo 20 itens por página**, uma página por requisição, alinhado
  ao tamanho de página nativo do TMDB.
- **NFR-2 (Integração TMDB):** Usar a **API estável TMDB v3** (`https://api.themoviedb.org/3`)
  via `RestClient` já configurado (`TmdbConfig`, header `Authorization: Bearer <token>`),
  respeitando o interceptor de idioma pt-BR existente e `certification_country=BR`.
- **NFR-3 (Resiliência):** Falhas/timeout do TMDB devem ser mapeadas para exceções tratadas
  (`TmdbUnavailableException`) e exibidas como erro amigável, sem quebrar a aba.
- **NFR-4 (Testes):** Controllers, Services e Repositories novos/alterados no backend devem ter
  cobertura JUnit 5 + Mockito (obrigatório por `ARCHITECTURE.md`).
- **NFR-5 (Design):** Toda alteração em `client/` deve usar a skill `frontend-design` e seguir
  o design system (tema escuro, âmbar `#ffcb2b`, Bricolage Grotesque / DM Sans), fiel a
  `docs/design/prints/matchpage-search-with-filters.jpeg`.
- **NFR-8 (Componentes):** A UI deve ser construída com **componentes prontos do shadcn/ui**
  (ex.: `Collapsible`, `ToggleGroup`, `Slider`, `Select`/`DropdownMenu`, `Dialog`,
  `Pagination`, `Calendar`+`Popover` para Date Range) em vez de implementações próprias. Novos
  componentes devem ser adicionados usando a **skill `shadcn`** (instalada), reaproveitando o
  que já existe em `client/src/components/ui/`.
- **NFR-6 (Performance):** Debounce na busca textual (padrão atual ~400ms) e cancelamento de
  requisições obsoletas ao trocar termo/filtros/página.
- **NFR-7 (Segurança):** A chave do TMDB permanece apenas no backend (injeção via `.env`), nunca
  exposta ao frontend.

## Non-Goals (Fora de Escopo)

- Qualquer alteração na aba **Sugestões** / deck de swipe (já finalizada).
- Alterar a lógica de match, eventos WebSocket ou a celebração de Match (apenas reutilizar).
- Persistir/lembrar filtros entre sessões ou sincronizar filtros entre os dois parceiros.
- Filtros não presentes na referência (idioma, país de origem, elenco, provedores de streaming
  como filtro).
- Recomendações personalizadas por IA no grid Descobrir.
- Alterações no Hub, Dashboard ou modal de adicionar título.

## Considerações de Design

- Referência visual: `docs/design/prints/matchpage-search-with-filters.jpeg`.
- Protótipo base do design system: `docs/design/claude-design-project/Sessao a Dois.dc.html`.
- Tokens: fundo `#09090a`, superfícies `#161513`/`#201e18`, primário `#ffcb2b`, acento
  `#ff9e2c`, verde de nota `#01b47f`/`#3ddc97`, texto `#f6f4ec`, muted `#a6a39a`; fontes
  Bricolage Grotesque (títulos) e DM Sans (corpo); pílulas/chips arredondados; badges de nota
  com ponto verde.
- Reutilizar componentes existentes de `MatchScreen.tsx` (grid, card, estados de curtir).
- Chips ativos: borda/preenchimento âmbar translúcido; inativos: pílula neutra `rgba(255,255,255,.05)`.

### Uso obrigatório de componentes shadcn/ui

- **Sempre priorizar componentes prontos do shadcn/ui** em vez de reimplementar do zero. A skill
  **`shadcn`** está instalada e **deve ser usada** para adicionar/gerar novos componentes.
- Componentes shadcn/ui **já presentes** em `client/src/components/ui/` (reutilizar):
  `button`, `input`, `dialog`, `popover`, `collapsible`, `calendar`, `card`, `avatar`,
  `scroll-area`, `tooltip`.
- Componentes shadcn/ui a **adicionar via skill `shadcn`** conforme a necessidade desta feature:
  - **Painel de filtros expansível** → `Collapsible` (já existe) para expandir/recolher.
  - **Chips (Data, Classificação, Gêneros)** → `Toggle` / `ToggleGroup` (múltipla escolha).
  - **Dropdown de ordenação** → `DropdownMenu` ou `Select`.
  - **Range sliders (Nota TMDB e Duração)** → `Slider` (com dois thumbs).
  - **Date Range / seleção por data** → `Calendar` + `Popover` (padrão Date Range Picker do
    shadcn) caso se opte por datas exatas além dos chips de década.
  - **Modais** (detalhes do título, celebração de match — quando tocados) → `Dialog`.
  - **Paginador numérico** → `Pagination`.
- Ao adicionar um componente, seguir o fluxo da skill `shadcn` e ajustar os tokens do design
  system (âmbar `#ffcb2b`, tema escuro) para manter fidelidade à referência.

## Considerações Técnicas

- **Módulo backend:** `com.app.media` (novos endpoints `/api/media/discover`,
  `/api/media/trending`, `/api/media/genres`; `/api/media/search` migra para paginado).
- **TMDB Discover:** `GET /discover/movie` e `GET /discover/tv`. Décadas mapeiam para intervalos
  de data (ex.: Anos 2020 → `2020-01-01`..`2029-12-31`) em `primary_release_date`/`first_air_date`.
  Nota → `vote_average.gte/.lte`; Duração → `with_runtime.gte/.lte`; Gêneros → `with_genres`;
  Ordenação → `sort_by`.
- **Certificação (decisão):** usar `certification_country=BR` + `certification.lte` (semântica
  "mais permissiva" — teto pela maior classificação marcada). Só é suportado em
  `/discover/movie`; para TV o filtro de classificação é **ignorado** (documentar na UX), sem
  quebrar a requisição.
- **Décadas (decisão):** múltiplas décadas → **um único intervalo min–max** de data
  (`primary_release_date`/`first_air_date`), aceitando que décadas do meio de intervalos não
  contíguos entrem no resultado. Sem múltiplas chamadas.
- **Combinação Filme + Série (decisão):** o TMDB pagina movie e tv separadamente (20 cada). No
  modo "ambos", o backend chama os dois `/discover`, **mescla por `sort_by` e fatia em 20** por
  página (mantém NFR-1); `totalResults` é a soma aproximada. Exige buscar páginas equivalentes
  de ambos os endpoints para compor cada página de 20.
- **Teto de páginas do TMDB:** o Discover limita a ~500 páginas; `totalPages`/paginação do
  frontend devem respeitar esse teto.
- **Trending:** `GET /trending/all/week` retorna itens mistos incluindo `person`, que devem ser
  filtrados para MOVIE/TV.
- **DTOs:** estender/introduzir `MediaPage` reaproveitando `MediaSearchResult`
  (`tmdbId, mediaType, title, year, posterUrl, overview, voteAverage`).
- **Curtir:** reutilizar `POST /api/match/like` e o carregamento de rastreados via
  `GET /api/tracking`, como no `SearchTab` atual.

## Métricas de Sucesso

- Usuário consegue aplicar pelo menos um filtro e ordenar em ≤ 3 cliques a partir da aba.
- 100% das listagens da Descobrir retornam ≤ 20 itens por requisição (verificável na resposta da
  API).
- Nenhuma regressão funcional na aba Sugestões.
- Aumento de títulos curtidos pela aba Descobrir (descoberta ativa) após o lançamento.

## Decisões Resolvidas

Todas as questões em aberto foram decididas com o time:

1. **Filme + Série no discover:** mesclar-e-fatiar em 20 (uma página pode misturar movie/tv);
   `totalResults` é a soma aproximada. (US-004)
2. **Busca x filtros:** **mutuamente exclusivos** — havendo texto na busca, filtros e ordenação
   ficam desabilitados (usa `/search`); sem texto, usa `/discover`. (FR-6.1, US-008, US-011)
3. **Paginação do grid:** **paginador numérico** (páginas 1, 2, 3… com anterior/próxima).
   (FR-10, US-014)
4. **Décadas não contíguas:** tratadas como **um único intervalo min–max** de data (décadas do
   meio entram no resultado). (US-004, US-009)
5. **Múltiplas classificações indicativas:** semântica **mais permissiva** via
   `certification.lte` (teto pela maior marcada). (US-005)

## Open Questions

- Nenhuma pendente. (Todas resolvidas acima.)
