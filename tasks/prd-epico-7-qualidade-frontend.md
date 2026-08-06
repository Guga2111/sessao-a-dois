# PRD: Qualidade do Frontend (Épico 7)

> Origem: `POST-MVP-TASK.md` → Épico 7 (T7.1 – T7.3).
> Escopo confirmado com o usuário em 2026-08-05: épico completo (3 tasks), **mais** a
> decomposição do `HubScreen` (extensão explícita além do backlog), verificação por
> `typecheck`/`lint`/`build`/diff de JSX, e correção do `catch` silencioso da store.

## 1. Introdução

Três problemas de qualidade no `client/`, todos sem efeito visual:

1. **Duplicação.** A máquina de estados de "comparar 2 títulos" existe duas vezes:
   como hook genérico em `MatchScreen.tsx:212-310` e reimplementada inline em
   `HubScreen.tsx:164-239` (mesmos 6 `useState`, mesmo efeito `Promise.all` embrulhado
   em `setTimeout(..., 0)`, mesmo listener de Escape, mesmo truque de retry por nova
   identidade de array). O construtor de `ComparisonItem` existe **três** vezes.
2. **Complexidade.** `MatchScreen.tsx` tem **1482 linhas** com 3 componentes e 7
   funções auxiliares. Só o `SearchTab` (linhas 691-1420) tem **19 `useState` + 2
   `useRef`** e uma cadeia de 5 blocos condicionais mutuamente exclusivos
   (linhas 1165-1206) que é uma máquina de estados implícita escrita como JSX.
   `HubScreen.tsx` tem 609 linhas e 13 `useState` num único componente.
3. **Vazamento de estado.** `useMatchStore.ts:38` guarda `celebratedMatchKeys` num
   `Set` de escopo de módulo que **nunca é limpo** — nem por `disconnect()`
   (linhas 94-99) nem por `useAuthStore.logout()` (linhas 115-125, que chama
   `disconnect()`). Isso cresce sem limite e, pior, produz um bug de sessão real:
   usuário A faz logout, usuário B entra na mesma aba e **não vê a celebração** de um
   match cujo `tmdbId` A já havia celebrado.

**Este épico é refatoração pura.** Nenhum pixel muda, nenhuma chamada de API é criada
ou removida, nenhum contrato com o backend é tocado. A única exceção declarada é a
correção dos dois `catch` silenciosos descrita nas US-007 e US-008.

### Estado do código nesta data (verificado, não presumido)

O `POST-MVP-TASK.md` foi escrito em 2026-08-02; os Épicos 1-6 mexeram nos mesmos
arquivos desde então. Confirmado por inspeção em 2026-08-05:

| Achado original | Estado hoje |
|---|---|
| `MatchScreen.tsx` com 1439 linhas | **1482 linhas** — cresceu; a US-005 do Épico 6 (catches silenciosos) encostou no arquivo |
| `useCompareSelection` em `MatchScreen.tsx:212-310` | Confirmado, inalterado |
| `HubScreen.tsx:164-240` reimplementa o hook inline | Confirmado, hoje em `164-239` |
| 2 construtores de `ComparisonItem` duplicados | São **3**: `MatchScreen.tsx:160`, `:187` e `HubScreen.tsx:28` |
| `celebratedMatchKeys` em escopo de módulo | Confirmado (`useMatchStore.ts:38`) |
| Assimetria de subscription STOMP | Confirmado (`useMatchStore.ts:65`, retorno do 2º `subscribe` descartado) |
| Corrida connect/disconnect | Confirmado (`useMatchStore.ts:50` + o efeito de `App.tsx:40-58`, que chama `disconnect()` no cleanup **e** no ramo `else`) |
| `catch` silenciosos zerados pelo Épico 6 | **Sobraram dois**, ambos fora das telas auditadas: `useMatchStore.ts:110` (`fetchPending`) e `HubScreen.tsx:260` (`fetchSectionPage`). O `catch` de `HubScreen.tsx:219` **não** conta — ele preenche `compareError`, visível ao usuário |

### Decisões tomadas com o usuário (2026-08-05)

| # | Questão | Decisão |
|---|---------|---------|
| E1 | Escopo do PRD | As 3 tasks do épico (T7.1, T7.2, T7.3), completas. |
| E2 | `HubScreen` (609 linhas) também é decomposto? | **Sim** (US-006). Extensão consciente além do `POST-MVP-TASK.md`, que só citava `MatchScreen`. |
| E3 | Como provar "pixel-idêntico" sem browser no sandbox | `typecheck` + `lint` + `build` + revisão do diff: **nenhuma string de `className`/`style` alterada** e nenhuma chamada de API criada/removida. Critério objetivo e verificável por `grep`/`diff`. |
| E4 | `catch` silenciosos remanescentes | **Corrigir** os dois, nos arquivos que o épico já reescreve (US-007 e US-008). |

As decisões **E5 a E9**, tomadas durante a redação deste PRD, estão na seção 9 — que
registra decisões, não perguntas: este documento não tem questões em aberto.

> **Skill `frontend-design`: dispensada neste épico** — decisão D11 do
> `POST-MVP-TASK.md`, registrada no `CLAUDE.md` da raiz. O critério é objetivo: o
> resultado deve ser pixel-idêntico ao atual, então não há nenhuma decisão de design a
> tomar. **Se durante a implementação alguma story exigir um visual que não existe
> hoje, parar e chamar a skill** em vez de inventar.

## 2. Objetivos

- **Uma** implementação da seleção-para-comparar, consumida pelas duas telas.
- **Uma** função de construção de `ComparisonItem`.
- Nenhum arquivo de tela acima de 400 linhas; nenhum componente com mais de 8 `useState`.
- A cadeia de condicionais que decide o que a lista de busca renderiza vira uma máquina
  de estados explícita (`status` + `switch`), não 5 booleanos combinados no JSX.
- `logout` seguido de login de outro usuário na mesma aba deixa a store de match num
  estado limpo — nenhum dado de sessão sobrevive à troca de usuário.
- O par `connect`/`disconnect` é idempotente e não deixa cliente STOMP órfão.
- Nenhuma falha de rede desaparece em silêncio nos arquivos tocados por este épico.

**Restrição que atravessa todas as stories:** a UI é **pixel-idêntica** à atual. As
cores em valores arbitrários do Tailwind (`bg-[#161513]`, `border-[rgba(255,255,255,.07)]`)
vêm do protótipo em `docs/design/claude-design-project/Sessao a Dois.dc.html`, que é a
fonte de verdade (`client/CLAUDE.md`) — **não** substituir por tokens de tema durante a
movimentação de código.

## 3. User Stories

Ordem obrigatória: US-001 → US-002 → US-003 (T7.1), depois US-004 → US-005 → US-006
(T7.2 + extensão). US-007 e US-008 são independentes e podem ser feitas a qualquer
momento, inclusive em paralelo.

---

### US-001: Extrair `useCompareSelection` para arquivo próprio
**Description:** Como desenvolvedor, quero o hook de seleção-para-comparar num arquivo
compartilhado para que as duas telas consumam a mesma máquina de estados em vez de duas
cópias que podem divergir.

**Acceptance Criteria:**
- [ ] `useCompareSelection<T>` vive em `client/src/lib/useCompareSelection.ts` e não existe mais em `MatchScreen.tsx`.
- [ ] `MatchScreen.tsx` importa o hook de `@/lib/useCompareSelection`; as duas chamadas existentes (`SuggestionsTab` com `PendingMatch`, `SearchTab` com `MediaSearchResult`) funcionam sem alteração de assinatura.
- [ ] O `setTimeout(..., 0)` que embrulha o efeito de fetch é **preservado literalmente** — é o contorno documentado em `client/CLAUDE.md` para a regra `react-hooks/set-state-in-effect`, não um resquício.
- [ ] O retry por nova identidade de array (`setSelected(prev => [...prev])`) é preservado.
- [ ] O diff de `MatchScreen.tsx` nesta story é apenas remoção + import: nenhuma linha de JSX alterada (`git diff` não mostra nenhuma mudança em `className`/`style`).
- [ ] `bun run typecheck`, `bun run lint` e `bun run build` passam, sem novos avisos de `react-hooks`.

---

### US-002: Unificar os três construtores de `ComparisonItem`
**Description:** Como desenvolvedor, quero uma única função que monte um
`ComparisonItem` para que uma mudança no formato do card de comparação não precise ser
lembrada em três arquivos.

**Depende de:** US-001.

**Acceptance Criteria:**
- [ ] Existe **uma** função `buildComparisonItem(mediaType, tmdbId, overrides?: Partial<ComparisonItem>)` em `client/src/lib/comparisonItem.ts`.
- [ ] As três cópias saem: `buildComparisonItemFromDetails` (`MatchScreen.tsx:160`), `buildComparisonItemFromSearchResult` (`:187`) e `buildComparisonItem` (`HubScreen.tsx:28`).
- [ ] `buildComparisonItemFromPending` (`MatchScreen.tsx:183`), que é só um repasse, ou vira chamada direta da função unificada ou desaparece.
- [ ] Os `overrides` preservam exatamente a preferência de hoje: quando o resultado da busca já traz título/ano/pôster, esses valores vencem os do endpoint de detalhes; quando não traz, o endpoint manda.
- [ ] `grep -rn "GET /api/media\|/api/media/" client/src` mostra o **mesmo** conjunto de chamadas de antes — a unificação não cria nem remove request.
- [ ] Comparar 2 títulos no `HubScreen` e nas duas abas do `MatchScreen` continua produzindo o mesmo conteúdo no `ComparisonDialog`.
- [ ] `bun run typecheck`, `bun run lint` e `bun run build` passam.

---

### US-003: `HubScreen` passa a consumir o hook compartilhado
**Description:** Como desenvolvedor, quero remover a reimplementação inline do
`HubScreen` para que uma correção no fluxo de comparação valha para as duas telas de
uma vez.

**Depende de:** US-001, US-002.

**Acceptance Criteria:**
- [ ] `HubScreen.tsx` chama `useCompareSelection<MediaTrackResponse>(buildComparisonItem, (t) => t.id)` e não declara mais nenhum dos 6 `useState` de comparação (`compareMode`, `selectedForCompare`, `compareLeft`, `compareRight`, `compareLoading`, `compareError`).
- [ ] O efeito de `Promise.all` (linhas 206-231) e o listener de Escape (linhas 232-239) foram removidos — o hook já os fornece.
- [ ] `HubScreen.tsx` fica com **7** `useState` (13 hoje menos os 6 de comparação).
- [ ] O comportamento de comparação é idêntico: entrar/sair do modo comparar, selecionar 2, contador "n/2 selecionados", Escape cancela, o dialog só abre com ambos carregados, erro mostra retry ao lado da seleção (não dentro do dialog), retry funciona.
- [ ] `MediaCard` continua recebendo os mesmos props (`compareMode`/`compareSelected`/`compareOrder`/`onCompareToggle`) com a mesma semântica — o contrato descrito em `client/CLAUDE.md` não muda.
- [ ] `HubScreen.tsx` perde ~75 linhas e nenhuma linha de JSX é alterada além da fonte das variáveis.
- [ ] `bun run typecheck`, `bun run lint` e `bun run build` passam.

---

### US-004: Extrair `useDiscoverSearch` com `useReducer`
**Description:** Como desenvolvedor, quero o estado da busca do `SearchTab` num hook
com `useReducer` para que os 19 `useState` virem os ~4 agregados que eles realmente
são, antes de mover o componente de arquivo.

**Acceptance Criteria:**
- [ ] Existe `client/src/screens/match/useDiscoverSearch.ts` (decisão E5 — na tela, não em `lib/`), implementado com `useReducer`, expondo no mínimo `{query, results, page, totalPages, totalResults, searching, searched, fetchError, resultsContext, runFetch}`.
- [ ] Os 19 `useState` do `SearchTab` (`MatchScreen.tsx:692-718`) ficam agrupados em ~4 fatias: **filtros** (`sortBy`, `selectedDecades`, `selectedCertifications`, `selectedGenres`, `voteRange`, `runtimeRange`, `filtersOpen`, `genres`), **resultado** (`results`, `totalResults`, `resultsContext`, `searched`), **paginação** (`page`, `totalPages`) e **status de rede** (`searching`, `fetchError`).
- [ ] O padrão `lastFetchRef`/`lastAttemptRef` (`MatchScreen.tsx:724-725`), documentado em `client/CLAUDE.md`, é preservado **integralmente**: trocar de página replica a última requisição bem-sucedida mudando só `page`, sem re-derivar qual endpoint/filtro está ativo.
- [ ] O debounce da busca e o `setTimeout(..., 0)` do efeito de fetch inicial (`/api/media/trending`) são preservados — mesma razão da US-001.
- [ ] `likeStates` e `trackedKeys` **não** entram no hook: são estado de interação com o backend de match, não de busca. Ficam no componente, e **não** viram um hook próprio compartilhado com o `SuggestionsTab` (decisão E7).
- [ ] A sequência de requests observável é idêntica: mesmo endpoint, mesmos params, mesma quantidade, para busca com texto, filtros aplicados, trending inicial e troca de página.
- [ ] `bun run typecheck`, `bun run lint` e `bun run build` passam.

---

### US-005: Quebrar `MatchScreen.tsx` em `screens/match/`
**Description:** Como desenvolvedor, quero o `MatchScreen` dividido em arquivos por
responsabilidade para conseguir ler e alterar uma aba sem carregar 1482 linhas de
contexto.

**Depende de:** US-001, US-002, US-004.

**Acceptance Criteria:**
- [ ] Existe o diretório `client/src/screens/match/` com no mínimo: `MatchScreen.tsx` (shell + tabs), `SearchTab.tsx`, `SuggestionsTab.tsx`, `FiltersPanel.tsx`, `SearchResultCard.tsx`.
- [ ] **Nenhum arquivo** em `client/src/screens/match/` passa de 400 linhas (`wc -l` como prova).
- [ ] **Nenhum componente** do diretório tem mais de 8 `useState`.
- [ ] A cadeia de 5 blocos condicionais da lista (`MatchScreen.tsx:1165-1206`) é substituída por um `status: 'idle' | 'loading' | 'error' | 'empty' | 'ready'` **derivado** e renderizado por um `switch`; os 5 ramos produzem exatamente o mesmo JSX de hoje, incluindo os textos ("Comecem digitando…", "Nao foi possivel carregar os titulos em alta agora.", "Nada encontrado para…", etc.) e a distinção `trending` vs `discover` vs busca por texto nas mensagens.
- [ ] **O grid de resultados continua montado enquanto o skeleton aparece.** Hoje ele não é desmontado: recebe `className={cn(..., showSearchSkeleton && "hidden")}` (linha 1200). Um `switch` ingênuo que renderize `<Skeleton/>` **ou** `<Grid/>` desmontaria o grid a cada busca — mudança de comportamento invisível num diff de `className`. O ramo `loading` deve renderizar o skeleton **e** o grid oculto, como hoje.
- [ ] A rota em `client/src/App.tsx` continua funcionando (o import muda de caminho; a URL não).
- [ ] Os helpers compartilhados (`trackKey`, `paginationRange`, `formatResultsCount`, `formatVoteRangeLabel`, `formatMinutes`, `formatRuntimeRangeLabel`, `keyOfCompareItem`) vão para um módulo do próprio diretório, sem alteração de lógica.
- [ ] **Prova de pixel-identidade:** `git diff` do épico não contém nenhuma alteração de valor de `className` ou `style` — apenas movimentação de linhas entre arquivos. Todos os estados continuam existindo: busca com debounce, filtros, ordenação, paginação numerada, modo comparar, like nos 5 estados (`idle`/`loading`/`liked`/`matched`/`error`), skeletons via `useDelayedLoading` e erro com retry.
- [ ] **Nenhuma chamada de API nova ou removida** — a lista de endpoints consumidos por `screens/match/` é idêntica à consumida por `MatchScreen.tsx` hoje.
- [ ] `bun run typecheck`, `bun run lint` e `bun run build` passam.

---

### US-006: Decompor `HubScreen.tsx` em `screens/hub/`
**Description:** Como desenvolvedor, quero o mesmo tratamento no `HubScreen` para que a
tela principal do app não continue sendo um único arquivo de 600 linhas com 7 estados e
5 modais.

**Depende de:** US-003.
**Nota de escopo:** esta story **não** está no `POST-MVP-TASK.md` — foi acrescentada
por decisão do usuário (E2). O `HubScreen` sai da US-003 com ~535 linhas.

**Acceptance Criteria:**
- [ ] Existe `client/src/screens/hub/` com no mínimo: `HubScreen.tsx` (shell + orquestração), `TrackSection.tsx` (uma seção colapsável com seu grid e paginação incremental) e `LoadMoreSentinel.tsx`.
- [ ] Nenhum arquivo em `client/src/screens/hub/` passa de 400 linhas.
- [ ] Nenhum componente do diretório tem mais de 8 `useState`.
- [ ] A orquestração dos 5 modais (`TitleModal`, `MediaDetailModal`, `WatchModal`, `ReviewModal`, `DeleteTrackDialog`) fica no shell; as seções recebem callbacks, não estado de modal.
- [ ] O `IntersectionObserver` do `LoadMoreSentinel` e o padrão `sectionsRef` (que evita re-registrar o observer a cada render) são preservados.
- [ ] O `catch` silencioso de `fetchSectionPage` (`HubScreen.tsx:260`) passa a registrar `console.error` com contexto (`status` da seção, `page`) — é o mesmo anti-pattern #4 do `docs/ARCHITECTURE.md` que o Épico 6 corrigiu nas outras telas. **Única mudança não-pura desta story**, e ela não altera nenhum pixel: o estado visual de falha (parar o skeleton, manter o que já carregou) permanece exatamente o de hoje.
- [ ] A UI é pixel-idêntica: as 3 seções com contador, colapso/expansão, skeletons, scroll horizontal com carregamento incremental, modo comparar e todos os 5 modais.
- [ ] Nenhuma chamada de API nova ou removida.
- [ ] `bun run typecheck`, `bun run lint` e `bun run build` passam.

---

### US-007: Corrigir os vazamentos da `useMatchStore`
**Description:** Como usuário, quero que trocar de conta na mesma aba me dê uma sessão
limpa, para não perder a celebração de um match só porque a pessoa anterior já tinha
visto aquele título.

**Acceptance Criteria:**
- [ ] `celebratedMatchKeys` sai do escopo de módulo (`useMatchStore.ts:38`) e passa a viver **dentro** do estado da store.
- [ ] `disconnect()` zera `celebratedMatchKeys`.
- [ ] **Teste manual obrigatório:** usuário A celebra um match do `tmdbId` X → logout → usuário B entra na mesma aba → um match do mesmo `tmdbId` X abre o modal de celebração normalmente. (Hoje não abre — este é o bug.)
- [ ] A deduplicação continua funcionando **dentro** de uma sessão: o mesmo match, chegando pelo evento `/topic/couple/{id}/match` **e** pela notificação, abre o modal **uma** vez.
- [ ] **Todas** as subscriptions vão para um array no estado (hoje o retorno do `subscribe` de `/notifications`, linha 65, é descartado) e `disconnect()` desinscreve todas.
- [ ] Existe um flag `connecting` que torna o par connect/disconnect idempotente: `connect` não ativa um cliente novo enquanto um `deactivate()` anterior não terminou, e `disconnect` durante uma conexão em andamento não deixa cliente órfão.
- [ ] `disconnect()` guarda a promise de `deactivate()` no estado (`pendingDisconnect`) e a limpa ao concluir; `connect` a aguarda com **`Promise.race` contra um timeout de 3s** (decisão E8) — nunca `await` nu. Justificativa registrada em comentário no código: `deactivate()` do `@stomp/stompjs` 7.3.0 só resolve no evento `close` do socket e **não tem timeout próprio**, então um socket que nunca fecha travaria a reconexão em definitivo.
- [ ] O timeout é uma constante nomeada no arquivo, não um número literal solto.
- [ ] Ciclos rápidos de connect/disconnect (o efeito de `App.tsx:40-58` dispara nas mudanças de `isAuthenticated`/`hasPartner`/`coupleId` e no cleanup) não geram conexão duplicada nem `Client` vazando.
- [ ] Um `deactivate()` que nunca resolve (simulável forçando a promise a pendurar) **não** impede uma reconexão posterior — o timeout vence e o cliente novo sobe.
- [ ] `bun run typecheck` e `bun run lint` passam.

---

### US-008: Falha ao carregar a fila de pendentes deixa de ser silenciosa
**Description:** Como usuário, quero distinguir "não há sugestões pendentes" de "não deu
para carregar as sugestões", para não achar que avaliei tudo quando na verdade a
requisição falhou.

**Depende de:** US-007 (mesmo arquivo).
**Nota de escopo:** achado fora do `POST-MVP-TASK.md`. O Épico 6 (US-005) zerou seis
`catch` silenciosos em telas e modais, mas não alcançou este, que está na store.
Incluído por decisão do usuário (E4).

**Acceptance Criteria:**
- [ ] `fetchPending` (`useMatchStore.ts:110`) registra `console.error` com contexto no `catch`, em vez de engolir a exceção.
- [ ] A store expõe um flag de erro (ex.: `pendingError`) distinto de "fila vazia".
- [ ] O `SuggestionsTab` usa o padrão de erro **que já existe** no `SearchTab` (mensagem + botão "Tentar novamente") — **nenhum estado visual novo é desenhado**. Se ficar claro que o caso exige um visual inexistente, **parar e chamar a skill `frontend-design`** antes de inventar.
- [ ] Falha no `GET /api/match/pending` mostra erro com retry, não o estado "Fim das sugestões".
- [ ] `grep -rn "catch {$\|catch (.*) {}\|catch(() => {})" client/src` não retorna nenhum bloco que engula exceção sem log ou sinal visível ao usuário.
- [ ] `bun run typecheck` e `bun run lint` passam.

## 4. Requisitos Funcionais

- **FR-1:** Deve existir exatamente uma implementação de `useCompareSelection`, em `client/src/lib/useCompareSelection.ts`.
- **FR-2:** Deve existir exatamente uma função de construção de `ComparisonItem`, aceitando overrides opcionais.
- **FR-3:** `HubScreen` e `MatchScreen` devem consumir o mesmo hook de comparação; nenhuma das duas pode manter estado de comparação inline.
- **FR-4:** Nenhum arquivo em `client/src/screens/match/` ou `client/src/screens/hub/` pode passar de 400 linhas.
- **FR-5:** Nenhum componente desses diretórios pode declarar mais de 8 `useState`.
- **FR-6:** O estado da lista de resultados de busca deve ser um valor único de `'idle' | 'loading' | 'error' | 'empty' | 'ready'`, renderizado por `switch`, sem desmontar o grid de resultados no estado `loading`.
- **FR-7:** O estado do `SearchTab` deve ser gerido por `useReducer` num hook `useDiscoverSearch`, não por 19 `useState` soltos.
- **FR-8:** `celebratedMatchKeys` deve viver no estado da store e ser zerado em `disconnect()`.
- **FR-9:** `disconnect()` deve desinscrever todas as subscriptions STOMP ativas.
- **FR-10:** `connect`/`disconnect` devem ser idempotentes, com o `connect` aguardando o `deactivate()` pendente antes de ativar um cliente novo.
- **FR-11:** Nenhum `catch` em `client/src` pode engolir uma exceção sem `console.error` com contexto ou sinal visível ao usuário.
- **FR-12:** Nenhum valor de `className` ou `style` pode ser alterado por este épico.
- **FR-13:** O conjunto de endpoints consumidos pelo `client/` ao fim do épico deve ser idêntico ao do início.

## 5. Não-Objetivos (Fora do Escopo)

- **Qualquer mudança visual.** Inclui "melhorar" espaçamento, trocar cor arbitrária por token de tema, ajustar responsividade ou renomear texto de UI. Pixel-idêntico é o critério, não uma meta aproximada.
- **Mudar contrato de API.** Nenhum endpoint novo, removido ou com payload diferente.
- **Adicionar testes ao `client/`.** Não há vitest/testing-library no `package.json`; criar essa infraestrutura é a **T8.1** do Épico 8. Este épico não a antecipa.
- **Decompor `DashboardScreen.tsx`** (341 linhas). Está abaixo do limiar e não foi apontado pela auditoria — ver decisão E9 para o gatilho de revisitar.
- **Substituir Zustand, adotar React Query ou mudar a estratégia de fetch.** A movimentação preserva o padrão atual (`api.get` + `useEffect`).
- **Refatorar `useNotificationStore` ou `useAuthStore`.** Só a `useMatchStore` foi apontada, e só nos 3 pontos da T7.3 (+ o `catch` da US-008).
- **Mudar o transporte do WebSocket ou a lógica de reconexão** além do flag `connecting`. O `reconnectDelay: 5000` do STOMP fica como está.
- **Reescrever `MediaCard`.** Ele só é consumido pelas telas movidas; seu contrato de props não muda.

## 6. Considerações de Design

Nenhuma decisão de design é tomada neste épico — é essa a justificativa objetiva para a
dispensa da skill `frontend-design` (decisão D11). Restrições que sustentam isso:

- As cores em valores arbitrários do Tailwind vêm do protótipo
  (`docs/design/claude-design-project/Sessao a Dois.dc.html`), que é a fonte de verdade
  segundo `client/CLAUDE.md`. Movimentar um bloco de JSX entre arquivos **não** é
  oportunidade de "padronizar" essas cores.
- Os dois padrões estruturais de modal (shadcn `Dialog` vs. backdrop `fixed inset-0`
  feito à mão) coexistem de propósito, documentados em `client/CLAUDE.md`. A US-006 move
  os modais de arquivo, não converte um padrão no outro.
- A regra "`Dialog` como irmão, nunca filho, de um `Popover` ou de um `div` clicável"
  (`client/CLAUDE.md`) precisa sobreviver à decomposição — quebrar um componente em dois
  é exatamente o momento em que um `Dialog` acidentalmente vira filho de um `div` com
  `onClick`.
- O padrão de loading é fixo: `Skeleton` + `useDelayedLoading` para leitura, `Loader2`
  só em botão de mutação. Nenhuma story troca um pelo outro.

## 7. Considerações Técnicas

- **`react-hooks/set-state-in-effect`:** o `setTimeout(..., 0)` que embrulha efeitos com
  `setState` síncrono é o contorno estabelecido do projeto, documentado em
  `client/CLAUDE.md`. Ele aparece no hook de comparação, no fetch inicial de trending e
  no debounce de busca. **Preservar em todos.** Removê-lo "porque parece gambiarra"
  quebra o lint.
- **`react-hooks/refs` e `react-hooks/purity`:** proíbem, respectivamente, ler/escrever
  `ref.current` durante o render e chamar `Date.now()`/`new Date()` no corpo do
  componente. Ao mover código entre arquivos, o que era legal dentro de um `useCallback`
  pode acabar no corpo de um componente novo — rodar `bun run lint` a cada story, não só
  no fim.
- **Reset de estado por `key`:** as duas abas do `MatchScreen` são renderizadas como
  `{cond && <TabA/>}` / `{cond && <TabB/>}`, o que já desmonta/remonta cada uma na troca
  de aba e reseta seu estado local de graça (`client/CLAUDE.md`). **Preservar essa
  estrutura** — trocar para renderizar as duas simultaneamente com `hidden` mudaria
  comportamento de forma invisível ao diff visual.
- **`tsc -b`, não `tsc --noEmit`:** o `typecheck` do projeto precisa rodar em build mode
  ou não checa arquivo nenhum e sempre passa (`client/CLAUDE.md`). Se alguma story
  encostar em `tsconfig*.json` ou nos scripts do `package.json`, validar com um erro de
  tipo deliberado antes de confiar num run limpo.
- **Sandbox sem browser:** não há Chromium/Playwright nem sudo para instalar as libs de
  sistema, então a skill `dev-browser` não roda aqui e nenhuma story pode depender dela.
  A prova de pixel-identidade é o diff (decisão E3). O `bun run dev`/`build` pode exigir
  os três pacotes nativos arm64 listados em `client/CLAUDE.md`.
- **Ordem das stories:** US-004 (extrair o hook de estado) **antes** da US-005 (mover os
  arquivos) é deliberado — mover 700 linhas de JSX e reescrever 19 `useState` no mesmo
  commit torna o diff impossível de revisar, e o diff é justamente o critério de aceite.

## 8. Métricas de Sucesso

- Implementações de `useCompareSelection`: **2 → 1**.
- Construtores de `ComparisonItem`: **3 → 1**.
- `MatchScreen.tsx`: **1482 linhas → nenhum arquivo acima de 400**.
- `HubScreen.tsx`: **609 linhas → nenhum arquivo acima de 400**.
- Maior contagem de `useState` num componente: **19 → ≤ 8**.
- Blocos condicionais mutuamente exclusivos na lista de busca: **5 → 1 `switch`**.
- Estado de sessão sobrevivendo a um logout na store de match: **1 (`celebratedMatchKeys`) → 0**.
- Subscriptions STOMP não desinscritas em `disconnect()`: **1 → 0**.
- `catch` silenciosos em `client/src`: **2 → 0**.
- Diffs de `className`/`style`: **0** (critério objetivo de refatoração pura).
- `bun run typecheck`, `bun run lint` e `bun run build`: verdes ao fim de **cada** story.

## 9. Decisões de implementação (resolvidas)

Não há questões em aberto. As cinco dúvidas levantadas na redação deste PRD foram
decididas em 2026-08-05 e são vinculantes — reabrir só registrando o motivo aqui.

| # | Questão | Decisão |
|---|---------|---------|
| E5 | Onde mora `useDiscoverSearch` | `client/src/screens/match/useDiscoverSearch.ts` |
| E6 | Subdividir `SuggestionsTab` | Não |
| E7 | Hook `useLikeActions` compartilhado | Não criar |
| E8 | Como o flag `connecting` espera o `deactivate()` | `Promise.race` com timeout de 3s |
| E9 | `DashboardScreen.tsx` | Fora do escopo, com gatilho objetivo para revisitar |

### E5 — `useDiscoverSearch` fica em `screens/match/`, não em `lib/`

O critério é acoplamento, não tamanho: `useCompareSelection` vai para `lib/` porque tem
**dois** consumidores em telas diferentes (US-001); `useDiscoverSearch` conhece os
endpoints do TMDB, o formato dos filtros e o `resultsContext` de uma tela só. Pôr em
`lib/` sugeriria uma reusabilidade que não existe e convidaria o próximo consumidor a
acoplar-se a detalhes do `SearchTab`. Se um dia surgir um segundo consumidor real, mover
é um `git mv`.

### E6 — `SuggestionsTab` não é subdividido

Ficam ~300 linhas num arquivo só, dentro do limite de 400 (FR-4). Quebrar mais criaria
indireção sem ganho de legibilidade, e cada arquivo extra é mais uma chance de mover um
`Dialog` para dentro de um `div` clicável por acidente — o risco estrutural que a seção 6
descreve. O limite de 400 linhas é um teto, não uma meta.

### E7 — Não criar `useLikeActions`

Verificado no código: os dois tabs modelam a mesma ação de formas legitimamente
diferentes. `SearchTab` mantém `likeStates: Record<string, LikeState>` — uma máquina de 5
estados **por card**, porque a lista mostra dezenas de resultados simultâneos.
`SuggestionsTab` mantém `actionLoading`/`actionError`/`lastAction` — estado **único**,
porque a UI é um card por vez. Unificar isso exigiria um hook que atende os dois casos
sendo bom em nenhum: é abstração prematura sobre uma semelhança de nome, não de forma.
`likeStates` e `trackedKeys` permanecem no `SearchTab`, como já diz a US-004.

### E8 — `Promise.race([deactivate(), timeout(3000)])`

Verificado em `node_modules/@stomp/stompjs/esm6/client.js` (v7.3.0):

1. `deactivate()` resolve **na hora** se o cliente já está `INACTIVE` ou se o socket já
   está `CLOSED`.
2. Caso contrário, devolve uma promise que só resolve quando o `onWebSocketClose` do
   handler dispara. **Não há timeout na biblioteca.** Se o socket nunca emitir `close`
   (rede caída sem FIN, broker que não responde ao DISCONNECT), essa promise **nunca**
   resolve.
3. `activate()` já trata o estado `DEACTIVATING` — ele espera a desativação terminar
   antes de conectar. Mas isso vale para a **mesma** instância de `Client`, e a store cria
   uma instância nova a cada `connect()`, então essa proteção não nos alcança hoje.

Portanto: esperar a promise nua **não** é o caminho mais determinístico — é o que troca
uma conexão duplicada (bug de hoje) por uma sessão que nunca mais reconecta (bug pior,
e sem sintoma até o usuário perceber que parou de receber match). A implementação guarda
a desativação pendente numa promise no estado (`pendingDisconnect`) e o `connect` a
aguarda via `Promise.race` com um timeout de **3 segundos**; vencido o timeout, segue e
ativa o cliente novo. O caso degradado (dois sockets por instantes, como hoje) fica
restrito a uma falha de rede real, em vez de ser o comportamento padrão. O timeout é
declarado como constante nomeada no arquivo, com o motivo em comentário e link para este
item.

### E9 — `DashboardScreen.tsx` fica fora, com gatilho objetivo

341 linhas hoje, abaixo do teto de 400 do FR-4, e nenhuma auditoria o apontou. Não entra
neste épico. **Gatilho para revisitar:** se passar de 400 linhas ou de 8 `useState`, vira
task própria no `POST-MVP-TASK.md` — não é anexado a este épico depois do fato.
