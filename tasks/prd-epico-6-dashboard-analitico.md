# PRD: Épico 6 — Dashboard Analítico

## Introdução

O Dashboard Analítico transforma o histórico de consumo do casal em um retrato visual e
motivador: quanto tempo assistiram juntos, o equilíbrio entre filmes e séries, os gêneros
favoritos e a evolução mês a mês. A tela `DashboardScreen.tsx` consome um único endpoint de
estatísticas (`GET /api/tracking/stats`) que o backend calcula agregando os títulos já
assistidos (`WATCHED`) do casal.

O objetivo é fechar o ciclo do produto: depois de rastrear (Épico 4) e dar match (Épico 5),
o casal ganha uma camada de "memória compartilhada" com números e gráficos, reforçando o
hábito de registrar o que assistem.

## Objetivos

- Expor um endpoint único (`GET /api/tracking/stats`) que retorna todas as métricas do casal.
- Calcular estatísticas apenas sobre títulos com status `WATCHED` ("Já Vimos").
- Somar horas apenas de **filmes** (usando `runtime`); séries contam apenas em quantidade
  (decisão técnica #14).
- Persistir os gêneros (`genre_ids`) de cada título no momento em que ele é adicionado, para
  permitir a agregação de "gênero favorito" e "gêneros mais assistidos".
- Renderizar a `DashboardScreen.tsx` fiel ao protótipo aprovado (KPIs + gráficos em Tailwind),
  consumindo os dados reais do backend.
- Fornecer agregação temporal por mês (títulos por mês e variação de horas no mês atual).

## User Stories

### US-001: Persistir gêneros do título no MediaTrack
**Description:** Como desenvolvedor, preciso armazenar os `genre_ids` de cada título ao
adicioná-lo, para que as estatísticas de gênero possam ser calculadas sem depender de chamadas
externas a cada carregamento do dashboard.

**Acceptance Criteria:**
- [ ] Adicionar campo `genreIds` ao `MediaTrack` (lista de `Integer`, ex.: `@ElementCollection`
      em tabela `media_track_genre` ou coluna de inteiros). O schema é criado automaticamente
      via `spring.jpa.hibernate.ddl-auto=update` (sem migration manual).
- [ ] Ao criar um `MediaTrack` (fluxo de tracking e fluxo de match), buscar os `genre_ids` do
      título no TMDB (endpoint de detalhes já existente em `com.app.media`) e persistí-los.
- [ ] Se o TMDB não retornar gêneros, o título é salvo com lista de gêneros vazia (não bloquear
      a criação).
- [ ] `runtime` continua sendo persistido apenas para filmes (comportamento atual mantido).
- [ ] Testes unitários cobrindo a persistência dos `genreIds` na criação.
- [ ] Typecheck/compilação (Maven) passa.

### US-002: Consultas de agregação no repositório
**Description:** Como desenvolvedor, preciso de consultas JPA/SQL que agreguem os dados do casal,
para que o service monte as estatísticas de forma eficiente.

**Acceptance Criteria:**
- [ ] Consulta: soma de `runtime` dos títulos `WATCHED` do tipo `MOVIE` do casal (total de
      minutos assistidos).
- [ ] Consulta: contagem de títulos `WATCHED` por `media_type` (filmes vs séries).
- [ ] Consulta: contagem de títulos `WATCHED` agrupada por mês do `watched_date` (para o ano
      corrente).
- [ ] Consulta: contagem de ocorrências por `genre_id` entre os títulos `WATCHED` do casal.
- [ ] Consulta: nota média do casal = média de `rating` de todas as `UserReview` vinculadas a
      `MediaTrack` `WATCHED` do casal.
- [ ] Todas as consultas são escopadas por `couple_id` (nunca vazam dados de outro casal).
- [ ] Testes de repositório (`@DataJpaTest`) validando cada consulta.
- [ ] Compilação (Maven) passa.

### US-003: StatsService monta o DTO de estatísticas
**Description:** Como desenvolvedor, preciso de um service que combine as consultas em um objeto
de resposta pronto para o frontend, incluindo os nomes dos gêneros em pt-BR.

**Acceptance Criteria:**
- [ ] `StatsService` calcula: tempo total (horas + minutos) de filmes, total de horas de filmes
      assistidos no mês corrente, contagem/percentual de filmes vs séries, total de títulos,
      nota média do casal (1 casa decimal).
- [ ] Resolve `genre_id` → nome em pt-BR usando um **mapa estático embutido** no backend
      (`Map<Integer, String>` com os gêneros de filmes e séries do TMDB). Sem chamadas externas
      no carregamento do dashboard. IDs sem nome correspondente são ignorados.
- [ ] Retorna "gênero favorito" (mais frequente) e a lista dos top 5 gêneros com percentual.
- [ ] Retorna série mensal de títulos por mês do ano corrente (12 posições ou meses com dados).
- [ ] Casal sem títulos `WATCHED` retorna zeros/listas vazias sem erro (estado vazio).
- [ ] Testes unitários com Mockito cobrindo casos com e sem dados.
- [ ] Compilação (Maven) passa.

### US-004: Endpoint GET /api/tracking/stats
**Description:** Como usuário autenticado, quero acessar as estatísticas do meu casal por uma
requisição, para que o dashboard possa exibi-las.

**Acceptance Criteria:**
- [ ] `GET /api/tracking/stats` retorna 200 com o DTO de estatísticas do casal do usuário
      autenticado (couple derivado do JWT, não de parâmetro).
- [ ] Rota exige autenticação (401 sem token válido).
- [ ] Usuário sem casal vinculado recebe **200 com estado vazio** (zeros/listas vazias), tratado
      pelo frontend como o estado vazio normal do dashboard.
- [ ] Teste de controller (`@WebMvcTest`/MockMvc) cobrindo sucesso e acesso não autenticado.
- [ ] Compilação (Maven) passa.

### US-005: DashboardScreen — KPIs
**Description:** Como usuário, quero ver os indicadores principais do casal (tempo juntos,
filmes vs séries, gênero favorito, total assistido) para ter um panorama rápido.

**Acceptance Criteria:**
- [ ] `DashboardScreen.tsx` busca `GET /api/tracking/stats` (via Axios com JWT) ao montar.
- [ ] Renderiza os 4 cards de KPI conforme o protótipo: Tempo juntos (`148h 30m` + delta do
      mês), Filmes vs Séries (% + barra + contagens), Gênero favorito, Total assistido (nº de
      títulos + nota média do casal).
- [ ] Estados de carregamento e vazio tratados (sem quebrar quando não há dados).
- [ ] Segue a skill `frontend-design` e o estilo visual do protótipo (cores, tipografia,
      cards `#161513`, etc.).
- [ ] Typecheck (TS) passa.
- [ ] Verificar no navegador usando a skill dev-browser.

### US-006: DashboardScreen — Gráficos
**Description:** Como usuário, quero ver os gráficos de títulos por mês, filmes vs séries
(donut) e gêneros mais assistidos, para entender minha evolução e preferências.

**Acceptance Criteria:**
- [ ] Gráfico de barras "Títulos por mês" alimentado pela série mensal do backend (altura das
      barras proporcional aos valores).
- [ ] Donut "Filmes vs Séries" refletindo os percentuais reais (via `conic-gradient` como no
      protótipo) com legenda de contagens/percentual.
- [ ] Barras de "Gêneros mais assistidos" (top 5) com percentuais reais.
- [ ] Todos os gráficos usam Tailwind CSS conforme o design original; sem biblioteca de charts
      externa a menos que já esteja no projeto.
- [ ] Estado vazio tratado (ex.: mensagem quando não há títulos assistidos).
- [ ] Typecheck (TS) passa.
- [ ] Verificar no navegador usando a skill dev-browser.

## Requisitos Funcionais

- FR-1: O `MediaTrack` deve armazenar os `genre_ids` do título, persistidos na criação a partir
  dos detalhes do TMDB.
- FR-2: O sistema deve calcular todas as estatísticas considerando **apenas** títulos com status
  `WATCHED` do casal do usuário autenticado.
- FR-3: O total de horas ("Tempo juntos") deve somar o `runtime` apenas de títulos `MOVIE`;
  séries não contribuem em horas, apenas em quantidade.
- FR-4: O sistema deve calcular filmes vs séries (contagem e percentual) sobre títulos `WATCHED`.
- FR-5: O sistema deve calcular o gênero favorito (mais frequente) e o ranking dos top 5 gêneros
  com percentual, resolvendo os nomes em pt-BR via um mapa estático embutido no backend.
- FR-6: O sistema deve calcular a quantidade de títulos assistidos por mês no ano corrente.
- FR-7: O sistema deve calcular o total de horas de filmes assistidos no mês corrente (soma do
  `runtime` dos filmes com `watched_date` no mês atual) para o KPI "↑ Xh neste mês".
- FR-8: O sistema deve calcular a nota média do casal a partir das `UserReview` dos títulos
  `WATCHED` (sem quebra por parceiro).
- FR-9: O endpoint `GET /api/tracking/stats` deve retornar todas as métricas acima em um único
  payload, escopado ao casal do JWT, exigindo autenticação.
- FR-10: A `DashboardScreen.tsx` deve consumir esse endpoint e renderizar KPIs e gráficos fiéis
  ao protótipo, com estados de carregamento e vazio.

## Não-Objetivos (Fora de Escopo)

- **Comparativo individual entre parceiros** (Ana vs Léo): hoje a nota é tratada como do casal,
  não individual; um comparativo exigiria repensar o fluxo de avaliação e fica para um épico
  futuro. O dashboard usa apenas a nota média do casal.
- Cache das respostas do TMDB (decisão técnica #5).
- Filtros interativos por período/gênero na UI (o dashboard é somente-leitura nesta versão).
- Exportação (PDF/imagem) ou compartilhamento das estatísticas.
- Estatísticas de títulos ainda não assistidos (`WATCHING`, `WANT_TO_SEE`).
- Recalcular gêneros retroativamente para títulos já existentes sem `genre_ids` (só novos
  títulos passam a ter gêneros; itens antigos aparecem sem contribuição de gênero).

## Considerações de Design

- Basear-se na SCREEN 2 (DASHBOARD) do protótipo em
  `docs/design/claude-design-project/Sessao a Dois.dc.html` (linhas ~198–282).
- Layout: cabeçalho "Estatísticas do Casal" → grid de 4 KPIs → linha de gráficos (barras
  mensais `1.6fr` + donut `1fr`) → barras de gêneros.
- Paleta: cards `#161513`, bordas `rgba(255,255,255,.07)`, acentos `#ffcb2b`/`#ff9e2c`,
  texto secundário `#a6a39a`, positivo `#3ddc97`. Tipografia de títulos: `Bricolage Grotesque`.
- Usar a skill `frontend-design` antes de implementar qualquer código em `client/` (regra do
  `CLAUDE.md`).

## Considerações Técnicas

- Backend em `com.app.tracking` (Spring Boot, Maven, Java 21, PostgreSQL). Reutilizar
  `MediaTrackRepository` e `UserReviewRepository` ou criar um `StatsRepository` dedicado.
- `spring.jpa.hibernate.ddl-auto=update` já está ativo — a coluna/tabela de `genreIds` é criada
  automaticamente; **não** criar migration manual (nada em produção).
- Resolução de nomes de gênero via mapa estático embutido no backend (`Map<Integer, String>`
  pt-BR para gêneros de filmes e séries do TMDB); zero chamadas externas no carregamento do
  dashboard, respeitando a decisão #5 (sem cache TMDB). O frontend nunca chama o TMDB direto.
- Frontend: React + TS + Vite + Tailwind + Axios (com interceptor JWT já existente). Gráficos
  construídos com Tailwind/CSS puro conforme o protótipo (sem lib de charts nova).
- Cobertura de testes obrigatória para Controllers, Services e Repositories (ARCHITECTURE.md).

## Métricas de Sucesso

- `GET /api/tracking/stats` responde com todas as métricas em uma única chamada (< 1 requisição
  extra ao TMDB por carregamento, apenas para nomes de gênero).
- Dashboard renderiza KPIs e 3 gráficos com dados reais do casal, fiel ao protótipo.
- Um casal com títulos assistidos vê horas (só filmes), distribuição filmes/séries, gênero
  favorito e evolução mensal corretos; um casal sem dados vê o estado vazio sem erros.

## Questões em Aberto

Todas as questões em aberto foram resolvidas:

- **Nomes de gênero:** mapa estático embutido no backend (`Map<Integer, String>` em pt-BR),
  sem chamadas ao TMDB no carregamento do dashboard. (Decisão: 1A)
- **Usuário autenticado sem casal:** `GET /api/tracking/stats` retorna 200 com estado vazio
  (zeros/listas vazias), tratado como o estado vazio normal do dashboard. (Decisão: 2A)
- **KPI "↑ Xh neste mês":** total de horas de filmes assistidos no mês corrente (soma de
  `runtime` dos filmes com `watched_date` no mês atual). (Decisão: 3A)
