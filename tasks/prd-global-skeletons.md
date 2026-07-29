# PRD: Skeletons Globais de Carregamento (Shadcn UI)

## 1. Introdução / Visão Geral

Hoje o app "Sessão a Dois" mistura estratégias de espera durante requisições de leitura (GET/FETCH): spinners (`Loader2` com `animate-spin`) no Match e na busca TMDB, um flag `loading` no Dashboard, e um `SkeletonCard` artesanal (divs com `animate-pulse`) escrito à mão dentro do `HubScreen.tsx`. Isso gera uma experiência de espera inconsistente e a sensação de "carregando" que queremos evitar.

Este PRD define a adoção de um **sistema global de skeletons** baseado no componente `Skeleton` da **Shadcn UI**, aplicado a **toda requisição de leitura (GET/FETCH)** do app. Os skeletons devem ser **layout-aware** (espelhar o formato real de cada tela/componente), com **proteção anti-flicker**, substituindo os spinners de leitura por uma percepção de velocidade e continuidade visual.

**Fora do escopo desta troca:** spinners/estados de botões de **ação do usuário (POST)** — ex.: "Curtir/Curtindo…", "Salvar Título", "Marcar como visto" — que **permanecem** como estão (feedback direto da ação, não é leitura).

## 2. Goals

- Instalar e padronizar o componente `Skeleton` da Shadcn UI como primitivo único de carregamento de leitura.
- Substituir **todos** os spinners e estados de "carregando" ligados a **GET/FETCH** por skeletons.
- Criar skeletons **layout-aware** que espelham o formato real de cada área (card de mídia, KPI, gráfico, linha de notificação, detalhe de título, resultados de busca).
- Cobrir globalmente: Hub, Dashboard, Match, modais de detalhe/review, busca TMDB, notificações e telas de autenticação.
- Implementar **anti-flicker**: skeleton só aparece após ~150ms de espera e permanece por no mínimo ~400ms quando aparece.
- Manter os estilos visuais do design (tema escuro `#161513`, bordas `rgba(255,255,255,.07)`, raios de 18px etc.).

## 3. User Stories

### US-001: Adicionar e adaptar o primitivo `Skeleton` da Shadcn UI
**Description:** Como desenvolvedor, quero um componente `Skeleton` global e alinhado ao tema, para reutilizar em todo o app.

**Acceptance Criteria:**
- [ ] Criar `src/components/ui/skeleton.tsx` **escrito à mão** (o sandbox não tem rede para `bunx shadcn add` — ver `client/CLAUDE.md`), seguindo o padrão dos demais primitivos: `data-slot="skeleton"`, `cn(...)` para merge de classes.
- [ ] Base de estilo com token do tema (`bg-accent`/`bg-white/[0.06]`), cantos arredondados e `animate-pulse` (animação padrão — decisão confirmada).
- [ ] Componente exporta via alias `@/components/ui/skeleton`.
- [ ] Typecheck e lint passam.
- [ ] Verificar no browser usando a skill dev-browser (render isolado do primitivo).

### US-002: Hook anti-flicker `useDelayedLoading`
**Description:** Como usuário, não quero ver o skeleton "piscar" quando a resposta é muito rápida.

**Acceptance Criteria:**
- [ ] Criar `src/lib/useDelayedLoading.ts` exportando um hook que recebe o estado real `loading: boolean` e retorna `showSkeleton: boolean`.
- [ ] `showSkeleton` só vira `true` se `loading` continuar `true` por mais de ~150ms (delay configurável).
- [ ] Uma vez visível, `showSkeleton` permanece `true` por no mínimo ~400ms (mínimo configurável), mesmo que `loading` vire `false` antes.
- [ ] Sem vazamento de timers (cleanup em `useEffect`).
- [ ] Typecheck e lint passam.

### US-003: Skeletons layout-aware reutilizáveis (biblioteca de skeletons)
**Description:** Como desenvolvedor, quero skeletons que espelham cada formato de UI, para reuso consistente entre telas.

**Acceptance Criteria:**
- [ ] Criar `src/components/skeletons/` com composições baseadas no primitivo `Skeleton`: `MediaCardSkeleton`, `KpiCardSkeleton`, `ChartSkeleton`, `NotificationRowSkeleton`, `DetailModalSkeleton`, `SearchResultSkeleton`.
- [ ] Cada skeleton reproduz as dimensões/proporções do componente real (ex.: `MediaCardSkeleton` usa `aspect-[3/4]` e o mesmo card container).
- [ ] `MediaCardSkeleton` substitui o `SkeletonCard` artesanal atual do `HubScreen.tsx` (remoção da versão inline).
- [ ] Typecheck e lint passam.
- [ ] Verificar no browser usando a skill dev-browser.

### US-004: Hub — skeletons nas 3 listas e no infinite scroll
**Description:** Como usuário, ao abrir o Hub quero ver o layout das listas se formando (skeletons) em vez de vazio/spinner.

**Acceptance Criteria:**
- [ ] Cada seção ("Assistindo", "Queremos Ver", "Já Vimos") mostra um grid de `MediaCardSkeleton` (mesma contagem/placeholder de hoje) enquanto `loading`.
- [ ] O contador do cabeçalho mostra um skeleton curto no lugar de "…" enquanto carrega.
- [ ] O carregamento incremental (infinite scroll / "Carregar mais") mostra `MediaCardSkeleton` no lugar de texto "Carregando…".
- [ ] Skeletons controlados pelo hook `useDelayedLoading` (anti-flicker).
- [ ] Nenhum spinner de leitura remanescente no Hub.
- [ ] Typecheck e lint passam.
- [ ] Verificar no browser usando a skill dev-browser.

### US-005: Dashboard — skeletons de KPIs e gráficos
**Description:** Como usuário, quero ver o esqueleto dos cards e gráficos do Dashboard enquanto as estatísticas carregam.

**Acceptance Criteria:**
- [ ] KPIs (tempo juntos, filmes vs séries, gênero favorito, total assistido) mostram `KpiCardSkeleton` enquanto `loading`.
- [ ] Os gráficos (barras por mês, donut, barras de gênero) mostram `ChartSkeleton` enquanto `loading`.
- [ ] Substitui o tratamento atual do flag `loading` do `DashboardScreen.tsx`.
- [ ] Estado vazio (`isEmpty`) continua funcionando após o carregamento (skeleton não conflita com empty state).
- [ ] Anti-flicker via `useDelayedLoading`.
- [ ] Typecheck e lint passam.
- [ ] Verificar no browser usando a skill dev-browser.

### US-006: Match — skeleton da fila pendente e da busca TMDB
**Description:** Como usuário, quero skeletons no Match (fila de sugestões e resultados de busca) em vez de spinners de leitura.

**Acceptance Criteria:**
- [ ] O `pendingLoading` da fila de sugestões renderiza um skeleton em formato de card de swipe no lugar do `Loader2` central (linha ~192-195 de `MatchScreen.tsx`).
- [ ] A busca TMDB mostra `SearchResultSkeleton` (linhas de resultado) no lugar do `Loader2` do input (linha ~527).
- [ ] O spinner de leitura da fila e da busca são removidos.
- [ ] **Mantido:** o botão "Curtir/Curtindo…" (POST) continua com seu `Loader2`/estado atual — é ação do usuário, não leitura.
- [ ] Anti-flicker via `useDelayedLoading` nas leituras.
- [ ] Typecheck e lint passam.
- [ ] Verificar no browser usando a skill dev-browser.

### US-007: Busca do TitleModal — skeleton nos resultados
**Description:** Como usuário, ao buscar um título no modal de adicionar, quero skeletons nos resultados em vez de spinner.

**Acceptance Criteria:**
- [ ] A busca do `TitleModal.tsx` mostra `SearchResultSkeleton` no lugar do `Loader2` do input (linha ~260).
- [ ] **Mantido:** o botão "Salvar Título" (POST) permanece com seu estado atual.
- [ ] Anti-flicker via `useDelayedLoading`.
- [ ] Typecheck e lint passam.
- [ ] Verificar no browser usando a skill dev-browser.

### US-008: Modais de detalhe/review — skeleton de leitura
**Description:** Como usuário, ao abrir o detalhe de um título quero ver o esqueleto do conteúdo enquanto os dados carregam.

**Acceptance Criteria:**
- [ ] `MediaDetailModal.tsx` / `PendingDetailModal.tsx` exibem `DetailModalSkeleton` (pôster + blocos de metadados + sinopse) enquanto os dados de leitura carregam.
- [ ] **Mantido:** botões de ação dentro dos modais (POST) permanecem com estado próprio.
- [ ] Anti-flicker via `useDelayedLoading`.
- [ ] Typecheck e lint passam.
- [ ] Verificar no browser usando a skill dev-browser.

### US-009: Notificações — skeleton do dropdown
**Description:** Como usuário, ao abrir o sino de notificações quero ver linhas-skeleton enquanto a lista carrega.

**Acceptance Criteria:**
- [ ] `NotificationDropdown.tsx` mostra uma lista de `NotificationRowSkeleton` enquanto o GET de notificações está em andamento.
- [ ] Empty state de "sem notificações" continua funcionando após carregamento.
- [ ] Anti-flicker via `useDelayedLoading`.
- [ ] Typecheck e lint passam.
- [ ] Verificar no browser usando a skill dev-browser.

### US-010: Bootstrap do App — gate de skeleton enquanto carrega o casal
**Description:** Como usuário autenticado, ao abrir o app quero ver skeleton enquanto os dados do casal (`/api/couple/me`) carregam, evitando um "flash" de conteúdo sem casal ou de redirecionamento indevido.

**Contexto de código:** o único GET de leitura no fluxo de bootstrap é `loadCurrentUser()` → `GET /api/couple/me`, disparado em `App.tsx:34` no mount quando `isAuthenticated`. Hoje **não há** loading gate: os guards em `guards.tsx` leem `couple` de forma síncrona, podendo renderizar/redirecionar antes do GET resolver. Os formulários de auth (login/registro/join) são **POST** e ficam fora deste escopo.

**Acceptance Criteria:**
- [ ] Adicionar um estado de loading no `useAuthStore` (ou no `App`) que fique `true` durante `loadCurrentUser()` e `false` ao concluir/errar.
- [ ] Enquanto esse loading estiver ativo (sob `useDelayedLoading`), o App exibe um skeleton de layout (ex.: Header + área de conteúdo) em vez de renderizar guards/telas.
- [ ] Após a resolução, guards e rotas passam a funcionar normalmente sem "flash" de estado incorreto.
- [ ] **Mantido:** submits de login/registro/join (POST) mantêm feedback de botão atual.
- [ ] Typecheck e lint passam.
- [ ] Verificar no browser usando a skill dev-browser.

### US-011: Varredura final — remover spinners de leitura remanescentes
**Description:** Como desenvolvedor, quero garantir que nenhum spinner de leitura sobrou no app.

**Acceptance Criteria:**
- [ ] `grep` por `animate-spin`/`Loader2` mostra ocorrências apenas em botões de ação (POST), nunca em fluxo de leitura GET/FETCH.
- [ ] Nenhuma referência ao `SkeletonCard` inline antigo permanece (migrado para `MediaCardSkeleton`).
- [ ] Documentar no CLAUDE.md do `client/` (ou criar) o padrão: "toda leitura usa `Skeleton` + `useDelayedLoading`; ações POST mantêm feedback de botão".
- [ ] Typecheck e lint passam.
- [ ] Verificar no browser usando a skill dev-browser.

## 4. Functional Requirements

- FR-1: Adicionar o componente `Skeleton` da Shadcn UI (`src/components/ui/skeleton.tsx`, escrito à mão — sandbox sem rede para `shadcn add`) adaptado ao tema escuro, com `animate-pulse`.
- FR-2: Fornecer hook `useDelayedLoading(loading, { delayMs = 150, minVisibleMs = 400 })` retornando `showSkeleton`.
- FR-3: Fornecer biblioteca de skeletons layout-aware em `src/components/skeletons/`: `MediaCardSkeleton`, `KpiCardSkeleton`, `ChartSkeleton`, `NotificationRowSkeleton`, `DetailModalSkeleton`, `SearchResultSkeleton`.
- FR-4: Toda requisição de **leitura (GET/FETCH)** deve renderizar o skeleton correspondente durante a espera, controlado por `useDelayedLoading`.
- FR-5: Remover spinners/estados de "carregando" ligados a leitura em Hub, Dashboard, Match, TitleModal, modais de detalhe, notificações e auth.
- FR-6: Preservar spinners/estados de botões de **ação (POST)**: "Curtir/Curtindo…", "Salvar Título", "Marcar como visto" e submits de auth.
- FR-7: Cada skeleton deve espelhar as dimensões/proporções do componente real que substitui (mesmo container, `aspect-ratio`, grid).
- FR-8: Empty states e error states existentes devem continuar funcionando e não conflitar com o skeleton (skeleton só durante a espera).

## 5. Non-Goals (Out of Scope)

- Substituir feedback de botões de ação (POST) por skeleton — permanecem como estão.
- Introduzir uma biblioteca de data-fetching nova (ex.: React Query) — este PRD só trata da camada visual de espera.
- Skeleton para a Landing page pública estática (não depende de GET crítico).
- Animações de "shimmer" customizadas além do `animate-pulse` padrão do Shadcn (pode ser avaliado depois).
- Suspense boundaries / streaming SSR (o app é SPA/Vite CSR).

## 6. Design Considerations

- Manter paleta e estilo: fundo de card `#161513`, borda `rgba(255,255,255,.07)`, raio 18px; blocos de skeleton em `bg-white/[0.06]` com `animate-pulse`.
- `MediaCardSkeleton` deve reaproveitar exatamente o container do `MediaCard`/`SkeletonCard` atual (incl. `min-w-[72vw]` no mobile e grid no desktop).
- Skeletons de gráfico no Dashboard devem ocupar a mesma área dos gráficos reais para evitar "salto" de layout (CLS ~0).
- Reutilizar `cn`/`utils` e o primitivo `Skeleton` — não recriar blocos manualmente fora da biblioteca de skeletons.

## 7. Technical Considerations

- Stack: React 19 + Vite + Tailwind v4 + Shadcn UI (preset já configurado em `components.json`, style `base-maia`).
- Fetches usam `axios` via `src/lib/api.ts`; estados de loading hoje são locais (`useState`) ou em stores Zustand (`useMatchStore.pendingLoading`, `useNotificationStore`).
- O hook `useDelayedLoading` deve envolver esses estados sem alterar a lógica de fetch.
- Atenção a timers no `useDelayedLoading` para evitar leaks e updates após unmount.
- Evitar layout shift: skeleton e conteúdo real devem ter a mesma "pegada" de layout.

## 8. Success Metrics

- 0 spinners de leitura (GET/FETCH) remanescentes no app (verificável por `grep`).
- Percepção de carregamento contínuo: nenhuma tela de leitura mostra spinner central.
- Sem "piscada" de skeleton em respostas rápidas (< ~150ms).
- Sem layout shift perceptível entre skeleton e conteúdo real (CLS próximo de 0).
- Cobertura: Hub, Dashboard, Match, TitleModal, modais de detalhe, notificações e auth usando skeletons.

## 9. Resolved Questions (decisões travadas)

- **Tempos anti-flicker:** `delayMs = 150` e `minVisibleMs = 400` — confirmados como padrão. Valores ficam configuráveis no hook para calibração fina no browser, mas o default é esse.
- **Animação:** `animate-pulse` padrão do Shadcn/Tailwind (sem shimmer customizado). Shimmer fica como possível melhoria futura, fora do escopo.
- **Escopo de auth (US-010):** confirmado cobrir o **bootstrap do casal no App** (`GET /api/couple/me` via `loadCurrentUser`), com gate de skeleton. Formulários de login/registro/join são POST e permanecem fora.
- **Sandbox:** `bunx/npx shadcn add` não tem rede neste ambiente e não há browser para screenshots — o primitivo `Skeleton` é escrito à mão e a verificação visual ("dev-browser") depende de ambiente com browser disponível.
