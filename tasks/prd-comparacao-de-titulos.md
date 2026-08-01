# PRD: Comparação de Títulos (Lado a Lado)

## 1. Introdução/Visão Geral

Hoje o casal consegue ver os detalhes de um título por vez (via `MediaDetailModal` /
`PendingDetailModal`), mas não consegue **comparar dois títulos** para decidir o que
assistir. Esta feature adiciona um **modo de comparação lado a lado** nas telas
**MatchScreen** e **HubScreen**: o usuário ativa o modo por um botão no toolbar,
seleciona **exatamente 2 cards** e um **ComparisonDialog** abre exibindo, em duas
colunas, as informações-chave dos dois títulos (poster, nota do casal, nota TMDB,
gêneros, sinopse e onde assistir), com **destaque visual para o que os dois têm em
comum** (gêneros e streamings compartilhados).

O objetivo é ajudar o casal a decidir mais rápido "o que vamos ver hoje" comparando
dois candidatos de forma direta, sem sair da tela.

## 2. Goals

- Permitir comparar 2 títulos lado a lado a partir das telas MatchScreen e HubScreen.
- Reutilizar dados já disponíveis e o endpoint existente `GET /api/media/{movie|tv}/{tmdbId}`
  (`MediaDetails`) — **sem criar endpoint novo no backend**.
- Destacar visualmente gêneros e streamings **em comum** entre os dois títulos.
- Manter consistência visual com o protótipo (dark theme, accent `#ffcb2b`) e
  com os padrões de modal/skeleton/responsividade já existentes no `client/`.
- Estado do modo de comparação **efêmero e local** à tela (useState), sem tocar
  em stores Zustand.

## 3. User Stories

### US-001: Componente ComparisonDialog (base, dados mockados)
**Description:** Como desenvolvedor, quero um componente `ComparisonDialog.tsx`
que receba dois títulos já resolvidos e os renderize lado a lado, para isolar a UI
de comparação da lógica de seleção de cada tela.

**Acceptance Criteria:**
- [ ] Novo arquivo `client/src/components/ComparisonDialog.tsx`.
- [ ] Usa o primitivo `Dialog` do shadcn (`client/src/components/ui/dialog.tsx`).
- [ ] Props: `open: boolean`, `onOpenChange: (open) => void`, e os dois títulos
      normalizados num tipo interno `ComparisonItem` (ver FR-9).
- [ ] Layout **duas colunas** no desktop; **empilhado com scroll vertical** no
      mobile (via hook `useIsMobile` de `client/src/lib/useIsMobile.ts`).
- [ ] Renderiza para cada coluna: Poster (imagem ou gradient fallback padrão do app),
      Título, Ano, badge de tipo ("Filme"/"Série"), Nota do Casal (★ 1-5 ou "Sem
      avaliação"), Nota TMDB (0-10), Gêneros (pills), Sinopse (line-clamp expansível)
      e Onde Assistir (streamings).
- [ ] Botão fechar (X) no canto superior direito, com `aria-label`.
- [ ] Fecha com Escape e trava foco (comportamento nativo do `Dialog`).
- [ ] Typecheck (`tsc`) e lint (`eslint`) passam.
- [ ] Verificar no browser com a skill dev-browser.

### US-002: Destaques de comparação (gêneros/streamings/notas em comum)
**Description:** Como usuário, quero ver o que os dois títulos têm em comum
destacado, para comparar mais rápido.

**Acceptance Criteria:**
- [ ] Gêneros presentes **nos dois** títulos usam cor de destaque (dourado
      `#ffcb2b`); gêneros exclusivos ficam em cinza.
- [ ] Streamings (`watchProviders`) presentes **nos dois** títulos recebem destaque
      similar; exclusivos ficam neutros.
- [ ] Para cada linha de nota (Casal e TMDB), a **maior** das duas colunas recebe um
      indicador sutil (ex: seta ↑ ou cor levemente mais vibrante); empate não
      destaca nenhuma.
- [ ] Comparação de gêneros/streamings é **case-insensitive** por nome.
- [ ] Typecheck e lint passam.
- [ ] Verificar no browser com a skill dev-browser.

### US-003: Modo de comparação + Dialog na HubScreen
**Description:** Como usuário na HubScreen, quero ativar o modo de comparação,
selecionar 2 títulos de qualquer seção e ver o dialog de comparação.

**Acceptance Criteria:**
- [ ] Botão "Comparar" (ícone de balança/colunas) aparece no header/toolbar da HubScreen.
- [ ] Tooltip (shadcn `tooltip.tsx`) no hover: "Selecione 2 títulos para comparar
      informações como notas, gêneros e onde assistir."
- [ ] Ao ativar, os cards ganham visual de "selecionável" (ex: borda tracejada/overlay
      sutil) e o clique no card **passa a selecionar** em vez de abrir o detalhe.
- [ ] Seleção é permitida entre **quaisquer cards visíveis**, misturando as seções
      "Assistindo Atualmente", "Queremos Ver" e "Já Vimos".
- [ ] Chip/badge flutuante no topo mostra "0/2 selecionados" → "1/2 selecionados",
      com botão "X" que limpa a seleção sem sair do modo.
- [ ] Cards selecionados: borda dourada `#ffcb2b` + badge com número "1"/"2".
- [ ] Clicar num card já selecionado o desseleciona.
- [ ] Ao selecionar o 2º título, o ComparisonDialog abre automaticamente.
- [ ] Para cada título selecionado, faz fetch de `GET /api/media/{movie|tv}/{tmdbId}`
      para gêneros/sinopse/watchProviders; a **Nota do Casal** vem das `reviews[]`
      do `MediaTrackResponse` que a Hub já possui.
- [ ] Enquanto os detalhes carregam, o dialog mostra skeleton via `useDelayedLoading`.
- [ ] Se o fetch de qualquer um dos dois títulos falhar, o dialog **não abre**:
      mostra erro geral e mantém a seleção ativa para nova tentativa (FR-16a).
- [ ] Botão "Cancelar" e tecla Escape saem do modo de seleção, limpando a seleção.
- [ ] Fechar o dialog **desativa** o modo de comparação automaticamente.
- [ ] Typecheck e lint passam.
- [ ] Verificar no browser com a skill dev-browser.

### US-004: Modo de comparação + Dialog na MatchScreen
**Description:** Como usuário na MatchScreen, quero comparar 2 títulos tanto na aba
Search quanto na aba Sugestões.

**Acceptance Criteria:**
- [ ] Botão "Comparar" + Tooltip (mesma mensagem da US-003) no toolbar da MatchScreen.
- [ ] Mesmo fluxo de seleção/badge/chip/cancelar da US-003.
- [ ] **Aba Search:** usa `MediaSearchResult` (título/ano/voteAverage/overview/poster)
      + fetch de `MediaDetails` para gêneros/watchProviders. Nota do Casal = "Sem avaliação".
- [ ] **Aba Sugestões:** `PendingMatch` tem dados limitados (tmdbId, mediaType, title,
      posterUrl) → faz fetch completo de `MediaDetails` para ambos. Nota do Casal =
      "Sem avaliação".
- [ ] Trocar de aba (Search ↔ Sugestões) **reseta** a seleção em andamento, pois a
      lista visível muda.
- [ ] Skeleton via `useDelayedLoading` enquanto carrega detalhes.
- [ ] Fechar o dialog / Escape / Cancelar desativa o modo, como na US-003.
- [ ] Typecheck e lint passam.
- [ ] Verificar no browser com a skill dev-browser.

## 4. Functional Requirements

- **FR-1:** Adicionar um botão "Comparar" no toolbar/header da MatchScreen e da
  HubScreen, com ícone (balança ou colunas lado a lado) e `aria-label`.
- **FR-2:** No hover do botão, exibir Tooltip (usando `client/src/components/ui/tooltip.tsx`)
  com a mensagem: "Selecione 2 títulos para comparar informações como notas, gêneros
  e onde assistir."
- **FR-3:** Ao clicar no botão, entrar em **modo de seleção**: cards ganham estilo
  "selecionável" (ex: borda tracejada + overlay sutil) e o clique no card seleciona
  em vez de disparar sua ação normal (abrir detalhe/etc.).
- **FR-4:** Exibir um indicador flutuante (chip/badge no topo) com o contador
  "N/2 selecionados". O chip inclui um botão "X" que **limpa a seleção** (zera para
  0/2) **sem sair** do modo de comparação.
- **FR-5:** Permitir selecionar **exatamente 2** cards. Card selecionado recebe borda
  dourada (`#ffcb2b`) e um badge com "1"/"2" indicando a ordem. Clicar de novo
  desseleciona.
- **FR-6:** Na HubScreen a seleção pode misturar cards de qualquer uma das 3 seções.
  Na MatchScreen a seleção é dentro da aba atual e é **resetada ao trocar de aba**.
- **FR-7:** Ao atingir 2 selecionados, abrir automaticamente o `ComparisonDialog`.
- **FR-8:** Permitir sair do modo via botão "Cancelar" **ou** tecla Escape, limpando
  a seleção e voltando ao estado normal (cliques nos cards voltam ao comportamento
  padrão).
- **FR-9:** Definir um tipo normalizado `ComparisonItem` que o `ComparisonDialog`
  consome, com no mínimo: `tmdbId`, `mediaType`, `title`, `year`, `posterUrl`,
  `overview`, `voteAverage` (TMDB 0-10), `coupleRating` (número 1-5 ou `null`),
  `genres: string[]`, `watchProviders: { name; logoUrl }[]`. Cada tela é responsável
  por montar esse objeto a partir de suas fontes de dados.
- **FR-10:** Buscar detalhes faltantes via `GET /api/media/${mediaType.toLowerCase()}/${tmdbId}`
  (retorna `MediaDetails`), seguindo o mesmo padrão de `MediaDetailModal.tsx` /
  `PendingDetailModal.tsx`. Duas requisições (uma por título), disparadas ao abrir o dialog.
- **FR-11:** **Nota do Casal** = média aritmética dos `reviews[].rating` **não-nulos**
  do `MediaTrackResponse`. Se nenhum membro avaliou (ou o título não é trackeado —
  casos da MatchScreen), exibir "Sem avaliação".
- **FR-12:** No dialog, destacar em dourado (`#ffcb2b`) os gêneros e os streamings
  presentes nos **dois** títulos (comparação case-insensitive por nome); os
  exclusivos ficam em cinza/neutro.
- **FR-13:** Em cada linha de nota (Casal e TMDB), destacar sutilmente a coluna com o
  **valor maior** (seta ↑ ou cor mais vibrante); empate não destaca.
- **FR-14:** Sinopse com line-clamp e opção de expandir ("ver mais").
- **FR-15:** Poster: imagem quando `posterUrl` existir; senão o gradient fallback
  padrão do app (mesmo tratamento dos cards/detalhe).
- **FR-15a:** Cada coluna exibe um badge com o tipo de mídia ("Filme"/"Série",
  seguindo o `TYPE_LABEL` existente). Comparar tipos diferentes (Filme vs Série) é
  permitido normalmente, sem aviso adicional.
- **FR-16:** Enquanto os detalhes carregam, exibir skeleton (composições de
  `client/src/components/skeletons/` + `Skeleton`) gated por `useDelayedLoading` —
  **nunca** spinner para leitura (conforme convenção do projeto).
- **FR-16a:** As duas requisições de detalhe (uma por título) devem ambas ter
  sucesso para abrir a comparação. Se **qualquer uma falhar**, **não abrir** o
  `ComparisonDialog`: exibir uma mensagem de erro geral (ex: toast/inline "Não foi
  possível carregar os detalhes para comparar. Tente novamente.") e **manter o modo
  de seleção ativo** com os 2 títulos ainda selecionados, permitindo nova tentativa.
- **FR-17:** Layout responsivo: `useIsMobile` (< 768px) empilha as colunas
  verticalmente com scroll; desktop lado a lado. Aplicar o padrão de sizing de modal
  do projeto (`w-[calc(100vw-32px)]` mobile → largura fixa em `sm:`; `max-h-[90svh]
  overflow-y-auto`).
- **FR-18:** Acessibilidade: `Dialog` trava foco, fecha com Escape, botão X e botão
  "Comparar" com `aria-label`.
- **FR-19:** Botão X (canto superior direito) fecha o dialog; ao fechar, o modo de
  comparação é **desativado** (limpa seleção e sai do modo de seleção).
- **FR-20:** Estado do modo/seleção é `useState` local de cada tela; **não** usar nem
  modificar stores Zustand.

## 5. Non-Goals (Fora de Escopo)

- **NG-1:** Não criar nem alterar endpoints no backend.
- **NG-2:** Não criar nem modificar stores Zustand.
- **NG-3:** Não permitir trocar um dos títulos de dentro do dialog — o dialog é
  **somente leitura**; para comparar outros, o usuário fecha e seleciona de novo.
- **NG-4:** Não comparar mais de 2 títulos (sempre exatamente 2).
- **NG-5:** Não adicionar o modo de comparação à DashboardScreen.
- **NG-6:** Não alterar `MediaCard.tsx` nem os cards do Match além de **adicionar o
  handler de clique condicional** ao modo de comparação (e o estilo de
  selecionado/selecionável, se necessário via prop/className passada de fora).
- **NG-7:** Não persistir a seleção entre navegações de tela ou reload.

## 6. Design Considerations

- **Fonte da verdade visual:** `docs/design/claude-design-project/Sessao a Dois.dc.html`
  (o modal de "Detalhes do Título" ali é a referência mais próxima para o layout de
  gêneros, onde assistir, nota TMDB e sinopse). Reaproveitar tratamentos de:
  pills de gênero (`background:rgba(255,203,43,.12); border:1px solid rgba(255,203,43,.25);
  color:#ffdd7a`), badge de streaming (bolinha colorida + nome), e bloco de nota TMDB
  (verde `#3ddc97` / `#01b47f`).
- **Cores:** background `#09090a` / cartão `#161513`, texto `#f6f4ec`, accent `#ffcb2b`.
- **Tipografia:** `font-display` (Bricolage Grotesque) para títulos/números grandes,
  `font-auth-body` (DM Sans) para corpo.
- **Componentes a reutilizar:** `ui/dialog.tsx`, `ui/tooltip.tsx`, `ui/skeleton.tsx`,
  `ui/button.tsx`, hooks `useIsMobile`, `useDelayedLoading`, e o padrão de fetch de
  `MediaDetailModal.tsx` / `PendingDetailModal.tsx`.
- **Regra obrigatória do projeto:** qualquer código em `client/` deve passar pela
  skill `frontend-design` antes de implementar.
- Seguir os "Modal patterns" do `client/CLAUDE.md`: como o `ComparisonDialog` é aberto
  em nome dos cards/toolbar, renderizá-lo como **irmão** (Fragment) de qualquer
  `Popover`/card clicável, não aninhado.

## 7. Technical Considerations

- **Endpoint:** `GET /api/media/${mediaType.toLowerCase()}/${tmdbId}` → `MediaDetails`
  (`client/src/types/media.ts`) já traz `genres`, `runtime`, `watchProviders` além dos
  campos de `MediaSearchResult`.
- **Tipos:** `MediaTrackResponse`/`ReviewDto` (`types/tracking.ts`), `MediaSearchResult`/
  `MediaDetails`/`PendingMatch` (`types/media.ts`). Não há codegen — manter em sincronia manual.
- **Lint gotchas** (ver `client/CLAUDE.md`): evitar `setState` síncrono direto no corpo
  de `useEffect` (usar o wrapper `setTimeout(..., 0)` + flag `cancelled` para o fetch de
  detalhes); para resetar estado de seleção ao trocar de aba na Match, preferir o padrão
  de remontagem via `key={abaAtiva}` num subcomponente em vez de `setState` em efeito.
- **Sandbox:** neste ambiente não há navegador nem backend rodando (só `tsc`/`eslint`);
  a verificação visual "no browser" pode exigir ambiente local do usuário.
- **Performance:** 2 requisições de detalhe por comparação; considerar cache simples em
  memória por `tmdbId` durante a sessão do dialog (opcional).

## 8. Success Metrics

- Usuário ativa o modo, seleciona 2 títulos e vê o dialog em ≤ 3 cliques.
- Gêneros e streamings em comum são visualmente distinguíveis num relance.
- Nenhuma regressão no clique normal dos cards quando o modo de comparação está
  desativado.
- `tsc` e `eslint` sem erros novos.

## 9. Decisões Resolvidas

- **D-1 (chip clicável):** O chip "N/2 selecionados" **inclui um botão "X" que limpa a
  seleção** (zera para 0/2) sem sair do modo; o botão "Cancelar" continua saindo do
  modo por completo. Ver FR-4.
- **D-2 (Filme vs Série):** Cada coluna mostra um **badge de tipo** ("Filme"/"Série",
  via `TYPE_LABEL`). Comparar tipos diferentes é permitido, sem aviso extra. Ver FR-15a.
- **D-3 (erro no fetch):** Se **um** dos dois fetches de detalhe falhar, **bloquear**:
  não abrir o dialog, exibir mensagem de erro geral e manter o modo de seleção com os
  2 títulos selecionados para nova tentativa. Ver FR-16a.
