# PRD: Épico 11 — Rede de Testes do Frontend

## Introdução

O `api/` tem 48+ arquivos de teste e um gate de 90% de linha no JaCoCo. O `client/`
tem **zero**: `client/package.json` declara `dev`, `build`, `lint`, `format`,
`typecheck` e `preview` — nenhum `test`, nenhuma dependência de teste, nenhum runner.

Isso não é uma lacuna teórica. Os Épicos 7, 9 e 10 fizeram as mudanças mais invasivas
da história do `client/` sem nenhuma verificação automática de que o comportamento se
manteve: `HubScreen` e `MatchScreen` foram decompostos em `screens/hub/` e
`screens/match/`, a `useMatchStore` teve vazamento de subscription e crescimento
ilimitado de `celebratedMatchKeys` corrigidos, o `useAuthStore` ganhou seis ações
novas de mutação, e o interceptor de `lib/api.ts` acumulou três guardas nascidas de
três bugs reais em produção/dev (loop de reload no bootstrap, CSRF chegando como 401,
401 de senha errada expulsando o usuário do formulário). **Todos foram corrigidos sem
teste** — nada impede a regressão, e o `client/CLAUDE.md` documenta cada um desses
bugs justamente porque a única rede que existe hoje é a memória de quem leu o arquivo.

Este épico entrega a rede: runner + convenções (T11.1), testes das stores, hooks e do
interceptor (T11.2), testes de comportamento das telas críticas e dos guards de rota
(T11.3), e o gate no CI que faz tudo isso valer alguma coisa (T11.4).

**Origem:** varredura de lacunas de MVP (2026-08-06), lacuna #4 do `POST-MVP-TASK.md`
(T11.1 – T11.4). Fecha também o non-goal que o PRD do Épico 8 deixou explícito
("Testes no frontend — é épico próprio, não um item de CI/CD").

**Motivo para não adiar:** o `POST-MVP-TASK.md` recomenda expressamente fazer o
Épico 11 **antes** do 13, porque a T13.2 faz substituição mecânica de cor em ~30
arquivos visuais (1033 utilitários arbitrários, 689 hex) e hoje não existe nada que
detecte uma quebra nesse caminho.

### Convenção de commits deste épico

> **Os commits deste épico NÃO devem conter o trailer `Co-Authored-By`.** Nem
> `Co-Authored-By: Claude ...`, nem nenhuma outra variação. A mensagem termina no
> corpo do commit. Vale para todo commit da implementação, inclusive os de correção
> de CI e os de documentação, e vale também para o corpo do Pull Request.
>
> *(Convenção herdada do PRD do Épico 10. Se não valer mais, riscar aqui antes de começar.)*

### Estado do código na abertura deste épico

Levantado em 2026-08-11, no repositório, não presumido do backlog:

| Área | Estado |
|---|---|
| Épicos | 1 a 10 concluídos. Os Épicos 4→9 foram para produção em 2026-08-10; o 10 está mergeado na `dev` (PR #41). |
| `client/package.json` | Scripts: `dev`, `build`, `lint`, `format`, `typecheck`, `preview`. **Nenhum `test`.** Nenhuma devDependency de teste. |
| `client/vite.config.ts` | 17 linhas: `react()`, `tailwindcss()`, alias `@` → `./src`, `define: { global: "globalThis" }` (necessário para o `sockjs-client`). Nenhum bloco `test`. |
| `tsconfig` | Project references (`tsconfig.json` com `files: []`). `tsconfig.app.json` tem `include: ["src"]`, `types: ["vite/client"]`, `strict`, `noUnusedLocals`, `noUnusedParameters`, `erasableSyntaxOnly`, `verbatimModuleSyntax`. **Qualquer arquivo de teste dentro de `src/` entra no `tsc -b`.** |
| Stores | `useAuthStore` (14 ações, lê `localStorage` **em escopo de módulo**), `useMatchStore` (STOMP/SockJS, `connect`/`disconnect`/`celebrateMatch`/`fetchPending`), `useNotificationStore` (paginação, marcação otimista com rollback, `pushIncoming`). |
| Hooks | `lib/useCompareSelection.ts` (107 linhas, 3 call sites), `screens/match/useDiscoverSearch.ts` (380 linhas, `useReducer` com 15 actions), `lib/useDelayedLoading.ts`, `lib/useIsMobile.ts`. |
| `lib/api.ts` | 152 linhas. `refreshClient` separado, `refreshPromise` single-flight em escopo de módulo, `isSessionProbe`, `isPasswordChallenge`, `redirectToLogin` com breadcrumb em `sessionStorage` e guarda de no-op em `/login`. **Nada disso existia quando o Épico 11 foi escrito.** |
| Guards | `routes/guards.tsx` — `ProtectedRoute`, `PublicOnlyRoute`, `RequireCouple`, `RedirectIfCoupled`. 45 linhas, sem teste. |
| Telas | `screens/hub/` (HubScreen/TrackSection/LoadMoreSentinel/helpers), `screens/match/` (MatchScreen/SearchTab/SuggestionsTab/FiltersPanel/SearchResultCard/compareUi/helpers/useDiscoverSearch), `screens/account/`, `DashboardScreen`, `LandingScreen`. |
| CI | `.github/workflows/ci.yml`, job `frontend`: `bun install` → `Audit dependencies` (gate, com 6 `--ignore`) → `Typecheck` → `Lint` → `Build`. **Nenhum step de teste.** |
| Lockfiles | `client/bun.lock` **e** `client/package-lock.json`, ambos versionados. O CI usa `bun install`. |
| Sandbox | `bun` não vem instalado, mas **instala via `npm install -g bun`** (v1.3.14 — a mesma do `client/CLAUDE.md`) e resolve o `bun.lock` existente. Registro de rede npm acessível. Não há navegador (Chromium/Playwright) nem JDK. |

### Decisões tomadas para este épico

Tomadas em 2026-08-11, com o mantenedor. Vinculantes; se alguma for revertida durante
a implementação, registrar aqui o motivo.

| # | Questão | Decisão |
|---|---|---|
| **E11.1** | Escopo | **T11.1 → T11.4 completo**, num PRD só. O gate de CI só faz sentido depois que existirem testes, e a rede só protege o Épico 13 se estiver inteira. |
| **E11.2** | `lib/api.ts` entra na T11.2? | **Sim, como story própria (US-008).** O épico foi escrito em 2026-08-06, antes dos Épicos 9 e 10 — o interceptor de hoje não existia. É o módulo de maior risco do `client/`: três regressões documentadas, todas com sintoma enganoso. Não estar na lista original é data, não decisão. |
| **E11.3** | Instalação das devDependencies | **`bun` no sandbox, via `npm install -g bun`.** Verificado na prática: v1.3.14, `bun install --dry-run` resolve o `bun.lock` existente em 8ms. Logo, `bun add -d …` atualiza `package.json` **e** `bun.lock` juntos, que é exatamente o que o `bun install` do CI espera. **Nenhum gate humano de lockfile.** Não usar `npm install` no `client/` — geraria divergência entre os dois lockfiles versionados. |
| **E11.4** | Limiar de cobertura | **Global, no patamar medido** ao fim da T11.3, arredondado para baixo. Mesma abordagem do JaCoCo na T8.1. Sem limiar por arquivo/diretório: um arquivo novo sem teste não deve barrar um PR não relacionado. |
| **E11.5** | Runner e ambiente | **Vitest + `@testing-library/react` + `@testing-library/user-event` + `jsdom`**, configurado **dentro do `vite.config.ts` já existente** (sem `vitest.config.ts` separado), conforme a T11.1. |
| **E11.6** | Mock de HTTP | **Sem MSW.** Duas estratégias, por camada: (a) para os testes de **stores, hooks e componentes**, `vi.mock("@/lib/api")` — o `axios` é o único cliente e todo acesso passa por esse módulo; (b) para os testes **do próprio `lib/api.ts`** (US-008), um **adapter falso do axios**, instalado em `axios.defaults.adapter` **antes** do `import` do módulo, de modo que `api` e `refreshClient` (que não é exportado) herdem os dois. Zero dependência nova. Reavaliar MSW só se a US-008 provar que o adapter não dá conta — e, se entrar, registrar aqui. |
| **E11.7** | `globals: true` do Vitest | **Não.** Imports explícitos de `vitest` em cada arquivo. `types: ["vite/client"]` no `tsconfig.app.json` fica intocado, e o `eslint` não precisa de globals novos. O `@testing-library/jest-dom/vitest` no `setup.ts` traz os matchers **e** a augmentação de tipo (o setup mora em `src/`, então o `tsc -b` o enxerga). |
| **E11.8** | Localização dos testes | **Co-localizados**: `useMatchStore.test.ts` ao lado de `useMatchStore.ts`. `src/test/` guarda **só** o `setup.ts` e os utilitários compartilhados. Motivo: o `client/` é organizado por feature/tela, e co-locação mantém o import relativo curto e o teste visível para quem edita o arquivo. (O `api/` usa estrutura espelhada porque é convenção do Maven, não porque seja melhor.) |
| **E11.9** | Reset das stores Zustand | As stores são singletons de módulo. Reset via `store.setState(store.getInitialState(), true)` num `afterEach` global (Zustand 5 expõe `getInitialState()`). **Exceção documentada:** o `useAuthStore` lê `localStorage` em escopo de módulo (`const initialSession = loadPersistedSession()`), então testar um estado persistido diferente exige `vi.resetModules()` + `await import(...)` **depois** de semear o `localStorage`. |
| **E11.10** | Scripts | `test` (watch), `test:run` (`vitest run`), `test:coverage` (`vitest run --coverage`). **O CI roda `test:coverage`** — um step só, que cobre as duas ACs da T11.4 (falhar em teste vermelho e ter limiar declarado), já que os thresholds vivem no `vite.config.ts`. |
| **E11.11** | Verificação em navegador | **Não se aplica a nenhuma story deste épico.** Nenhuma muda um pixel: são arquivos de teste, config e CI. Por consequência, a **D11** dispensa a skill `frontend-design`, e não há AC de "verificar no navegador" (que, aliás, seria impossível — não há Chromium no sandbox, ver `client/CLAUDE.md`). |

## Goals

- Dar ao `client/` uma rede de regressão automática, executável em um comando
  (`bun run test:run`) e no CI.
- Cobrir primeiro **o que já quebrou**: os bugs de vazamento de subscription, de
  `celebratedMatchKeys`, e as três guardas do interceptor de 401 — cada um vira um
  teste que falharia se a correção sumisse.
- Cobrir os quatro guards de rota e os estados de carregando/vazio/**erro** das telas
  decompostas no Épico 7.
- Consultar por papel/texto acessível, nunca por classe CSS, para que o Épico 13 possa
  reescrever estilo sem quebrar teste.
- Fechar o ciclo no CI: um PR com teste quebrado é bloqueado, e o limiar de cobertura
  fica declarado no patamar real alcançado.

## User Stories

Cada story cabe numa sessão focada. A ordem é a de implementação: **US-001 e US-002
bloqueiam todas as demais** (é a T11.1); US-012 → US-014 fecham o épico.

Todas as stories, sem exceção, incluem `bun run typecheck` e `bun run lint` nos
critérios de aceite — os arquivos de teste ficam sob `src/`, então entram no `tsc -b`
e no `eslint .` exatamente como código de produção.

---

### US-001: Infraestrutura de teste (T11.1)

**Descrição:** Como desenvolvedor, quero um runner de teste configurado no `client/`
para que exista onde escrever o primeiro teste.

**Critérios de Aceite:**
- [ ] `bun` disponível no ambiente (`npm install -g bun` se não estiver; registrar o comando em `client/CLAUDE.md`, corrigindo o "Sandbox gotcha" que hoje só fala de `npm`).
- [ ] Dependências adicionadas **com `bun add -d`** (nunca `npm install`): `vitest`, `@vitest/coverage-v8`, `@testing-library/react`, `@testing-library/user-event`, `@testing-library/jest-dom`, `jsdom`.
- [ ] `client/bun.lock` **e** `client/package.json` atualizados pelo mesmo comando; `package-lock.json` **não** é tocado nesta story.
- [ ] `vite.config.ts` ganha o bloco `test` (`environment: "jsdom"`, `setupFiles: ["./src/test/setup.ts"]`, `globals: false`, `css: false`), importando `defineConfig` de `vitest/config`. **Sem `vitest.config.ts` separado.**
- [ ] Scripts `test`, `test:run` e `test:coverage` no `package.json`, conforme E11.10.
- [ ] `src/test/setup.ts` com `@testing-library/jest-dom/vitest` e `cleanup()` entre testes.
- [ ] Um teste-canário (`src/test/canary.test.tsx`) que renderiza um componente React real em jsdom e consulta por papel acessível.
- [ ] `bun run test:run` executa e passa localmente.
- [ ] `bun run typecheck` e `bun run lint` continuam passando **com os arquivos de teste incluídos**.
- [ ] Nenhuma mudança de comportamento no app: `git diff` não toca nada em `src/` fora de `src/test/`.

---

### US-002: Utilitários de teste e convenções documentadas (T11.1)

**Descrição:** Como desenvolvedor, quero os utilitários compartilhados e a convenção
escrita, para que as 11 stories seguintes não inventem cada uma o seu jeito.

**Critérios de Aceite:**
- [ ] `src/test/setup.ts` instala os stubs de ambiente que o jsdom não tem e que este app usa: **`window.matchMedia`** (`theme-provider.tsx`, `lib/useIsMobile.ts`), **`IntersectionObserver`** (`screens/hub/LoadMoreSentinel.tsx`, `components/landing/DashboardPreview.tsx`) e **`ResizeObserver`** (posicionamento dos primitivos `@base-ui/react`).
- [ ] `src/test/storeReset.ts` (ou equivalente) reseta as três stores via `getInitialState()` num `afterEach` global, conforme E11.9.
- [ ] `src/test/renderWithRouter.tsx` — helper que monta um componente dentro de `MemoryRouter` com rota inicial parametrizável (as stories US-009 em diante dependem dele).
- [ ] O helper de render vive num arquivo **separado** dos helpers não-componente: `react-refresh/only-export-components` proíbe um `.tsx` exportar componente e função comum juntos (mesma pegadinha já registrada em `screens/match/` e `screens/hub/`).
- [ ] `client/CLAUDE.md` ganha uma seção de testes documentando: comando (`bun run test:run` / `test:coverage`), localização (co-localizados, `src/test/` só para infra), convenção de nome (`*.test.ts` / `*.test.tsx`), a estratégia de mock de HTTP das duas camadas (E11.6) e a pegadinha do `useAuthStore` lendo `localStorage` em escopo de módulo (E11.9).
- [ ] `bun run test:run`, `bun run typecheck` e `bun run lint` verdes.

---

### US-003: Testes da `useMatchStore` (T11.2)

**Descrição:** Como desenvolvedor, quero a store do WebSocket coberta, para que os
dois bugs corrigidos na T7.3 não voltem em silêncio.

**Nota de implementação:** `@stomp/stompjs` e `sockjs-client` são mockados
(`vi.mock`) — jsdom não abre socket e o teste não deve depender de servidor STOMP.
O mock do `Client` precisa expor `activate`, `deactivate` (retornando `Promise`),
`subscribe` (retornando objeto com `unsubscribe`) e permitir disparar `onConnect`.

**Critérios de Aceite:**
- [ ] `connect` com uma conexão já ativa (ou em andamento) **não** cria um segundo `Client` — idempotência verificada contando instanciações.
- [ ] `connect` após `disconnect` aguarda o `pendingDisconnect` e conecta; um `deactivate()` que nunca resolve não trava a reconexão além do `DEACTIVATE_TIMEOUT_MS` (3s) — testado com timers falsos.
- [ ] **Existe teste que falharia se o vazamento de subscription da T7.3 voltasse:** `disconnect` chama `unsubscribe()` em **todas** as subscriptions criadas no `onConnect` (match e notifications) e zera `subscriptions`.
- [ ] **Existe teste que falharia se `celebratedMatchKeys` voltasse a crescer sem limite:** `disconnect` substitui o `Set` por um vazio, e uma celebração repetida com a mesma chave depois do `disconnect` volta a abrir o modal.
- [ ] `celebrateMatch` abre o modal na primeira chamada e é no-op na segunda com o mesmo `dedupeKey` (é o que impede a celebração dupla entre o evento `/match` e a notificação `MATCH`).
- [ ] `fetchPending`: caminho feliz popula `pendingQueue`; falha zera a fila, liga `pendingError` e **loga** (o `console.error` é parte do contrato — anti-pattern #4 da `docs/ARCHITECTURE.md`); `pendingLoading` volta a `false` nos dois casos.
- [ ] Nenhum teste depende de rede real nem de servidor STOMP.
- [ ] `bun run test:run`, `typecheck` e `lint` verdes.

---

### US-004: Testes da `useAuthStore` (T11.2)

**Descrição:** Como desenvolvedor, quero o bootstrap de sessão e as mutações de conta
cobertos, para que o cache de UI nunca volte a ser tratado como credencial.

**Critérios de Aceite:**
- [ ] `loadCurrentUser` com `GET /api/auth/me` respondendo 200 popula `user`/`couple`, marca `isAuthenticated`, persiste o cache e desliga `loading`.
- [ ] `loadCurrentUser` com 401 zera o estado, **remove** o cache do `localStorage` e desliga `loading` — sem lançar.
- [ ] `loadCurrentUser` com erro que **não** é 401 relança, mas ainda assim desliga `loading` (o `finally`) — é o que impede o app de ficar preso no `AppShellSkeleton`.
- [ ] `bondDissolved` acende quando o cache tinha `couple.partner` e a resposta vem sem casal; **não** acende para quem nunca teve casal; e é apagado por `joinCouple`/`createCouple`/`clearSession`.
- [ ] Existe teste provando que `bondDissolved` **não** é persistido em `localStorage` (a AC da US-013 do Épico 9 proíbe flag paralela).
- [ ] O que vai para o `localStorage` é **só** `{ user, couple }` — nenhum campo com cara de credencial. Um `localStorage` com JSON corrompido não quebra o boot (cai no `catch` e volta vazio).
- [ ] `clearSession` desconecta a `useMatchStore`, limpa o cache e zera `user`/`couple`/`isAuthenticated`/`bondDissolved`.
- [ ] `logout` chama `POST /api/auth/logout` e limpa o estado **mesmo quando o POST falha** (a rede não pode prender o usuário logado na UI).
- [ ] `dissolveCouple` desconecta a `useMatchStore` **e** zera só o `couple`, mantendo a sessão.
- [ ] `updateProfile` escreve store e cache juntos; `changePassword` e `deleteAccount` **não** mexem no estado local (quem chama decide quando derrubar).
- [ ] `bun run test:run`, `typecheck` e `lint` verdes.

---

### US-005: Testes da `useNotificationStore` (T11.2)

**Descrição:** Como desenvolvedor, quero a contagem de não-lidas e a marcação otimista
cobertas, para que um erro de rede não deixe a UI mentindo.

**Critérios de Aceite:**
- [ ] `fetchNotifications` popula `notifications`/`page`/`hasMore` a partir do `PagedNotificationResponse` e desliga `loading` **inclusive quando a chamada falha** (é `finally`).
- [ ] `fetchMore` concatena a página seguinte e é no-op quando `hasMore` é `false`.
- [ ] `markAsRead` aplica a mudança **antes** da resposta (otimista), decrementa `unreadCount` sem passar de zero, e **reverte notificações e contador** quando o `PATCH` falha, relançando o erro.
- [ ] `markAsRead` é no-op para id inexistente ou já lida (não decrementa o contador duas vezes).
- [ ] `markAllAsRead` zera o contador e reverte igual no erro.
- [ ] `pushIncoming` insere no topo, incrementa o contador e, **só** para `type === "MATCH"`, chama `celebrateMatch` com a chave `tmdb:<id>` (a mesma do evento STOMP — é o que faz a deduplicação funcionar entre os dois caminhos).
- [ ] `bun run test:run`, `typecheck` e `lint` verdes.

---

### US-006: Testes do `useDiscoverSearch` (T11.2)

**Descrição:** Como desenvolvedor, quero as transições do `useReducer` introduzido na
T7.4 cobertas, para que busca, filtro e paginação não regridam na próxima refatoração.

**Critérios de Aceite:**
- [ ] O `reducer` é testado como função pura para as transições que carregam regra: `QUERY_CHANGED` com string vazia limpa `results` e `searched`; `FILTERS_CLEARED` volta aos defaults (`voteRange` `[0,10]`, `runtimeRange` `[0,240]`, listas vazias); `FETCH_STARTED` → `FETCH_SUCCEEDED` → `FETCH_SETTLED` e `FETCH_STARTED` → `FETCH_FAILED` → `FETCH_SETTLED` deixam `searching`/`fetchError` coerentes.
- [ ] O hook é testado com `renderHook`: busca com query debounced dispara **uma** chamada, não uma por tecla.
- [ ] Erro de rede acende `fetchError` sem limpar os resultados anteriores da tela (comportamento atual — o grid segue montado).
- [ ] Troca de página replica a última requisição bem-sucedida (`lastFetchRef`) com só o `page` diferente, sem re-derivar endpoint/filtros.
- [ ] `TMDB_MAX_PAGE` é respeitado no limite superior da paginação.
- [ ] Nenhum teste depende de rede real (`vi.mock("@/lib/api")`).
- [ ] `bun run test:run`, `typecheck` e `lint` verdes.

---

### US-007: Testes do `useCompareSelection` (T11.2)

**Descrição:** Como desenvolvedor, quero os limites de seleção e a limpeza do modo
comparar cobertos, já que o hook tem três call sites com formatos de item diferentes.

**Critérios de Aceite:**
- [ ] `toggle` adiciona, remove pelo `keyOf` e **para em 2 itens** (o terceiro é ignorado, não substitui).
- [ ] Ao chegar a 2 selecionados, dispara `Promise.all` dos dois `buildItem` e abre o diálogo (`dialogOpen`) — com `left`/`right` preenchidos.
- [ ] Falha em qualquer um dos dois `buildItem` deixa `dialogOpen` `false`, `left`/`right` nulos e uma mensagem de erro visível **preservando a seleção** para o retry.
- [ ] `retry` refaz o fetch sem duplicar a lógica (nova identidade de array em `selected`).
- [ ] `exitCompareMode` e `clearSelection` zeram seleção, itens, loading e erro.
- [ ] `Escape` sai do modo comparar **só** enquanto `compareMode` está ligado, e o listener é removido no unmount (verificar que não sobra listener em `window`).
- [ ] `bun run test:run`, `typecheck` e `lint` verdes.

---

### US-008: Testes do interceptor de `lib/api.ts` (T11.2 — decisão E11.2)

**Descrição:** Como desenvolvedor, quero cada guarda do interceptor de 401 coberta por
um teste, porque as três nasceram de bugs reais com sintoma enganoso e nenhuma tem
verificação hoje.

**Nota de implementação:** adapter falso do axios instalado em
`axios.defaults.adapter` **antes** do `import` do módulo (E11.6), de modo que `api` e
`refreshClient` o herdem. Usar `vi.resetModules()` entre testes para zerar o
`refreshPromise` de escopo de módulo. `window.location` e `sessionStorage` são
stubbados para observar o redirect sem navegar de verdade.

**Critérios de Aceite:**
- [ ] Um 401 numa chamada comum dispara **um** `POST /api/auth/refresh` e **reexecuta** a requisição original de forma transparente.
- [ ] **Cinco requisições concorrentes recebendo 401 disparam um único refresh**, não cinco (é o single-flight do `refreshPromise`).
- [ ] Refresh que falha redireciona para `/login` **e** grava o breadcrumb em `sessionStorage` com `{ method, url, reason, at }`, com `reason` distinguindo "refresh falhou" de "401 também após renovar".
- [ ] Segundo 401 na mesma requisição (`_retry` já marcado) redireciona sem tentar refresh de novo.
- [ ] **Guarda do password challenge:** 401 em `PUT /api/auth/password` e em `DELETE /api/user/me` é rejeitado **cru** — sem refresh, sem redirect (senão o usuário é expulso do formulário em vez de ver "senha errada").
- [ ] **Guarda da sondagem de sessão:** 401 em `GET /api/auth/me` **tenta** o refresh mas **não** redireciona quando ele falha (é o que impedia o loop infinito de reload no bootstrap).
- [ ] **Guarda do no-op em `/login`:** estando em `/login`, `redirectToLogin` não atribui `window.location.href` (atribuir a URL atual recarrega a página).
- [ ] Erro que não é 401 (403, 500, timeout) passa direto, sem refresh e sem redirect.
- [ ] O `console.error` do motivo continua acontecendo em todo redirect (anti-pattern #4).
- [ ] `bun run test:run`, `typecheck` e `lint` verdes.

---

### US-009: Testes dos guards de rota (T11.3)

**Descrição:** Como usuário, quero ser levado para a tela certa em cada estado de
sessão, e como desenvolvedor quero isso verificado, porque os quatro guards decidem
sozinhos o acesso a todas as rotas do app.

**Critérios de Aceite:**
- [ ] `ProtectedRoute`: sem sessão redireciona para `/login`; com sessão renderiza o filho.
- [ ] `PublicOnlyRoute`: sem sessão renderiza o filho; **com** sessão redireciona para `/hub` quando há parceiro e para `/join` quando não há.
- [ ] `RequireCouple`: sem `couple.partner` redireciona para `/join`; com parceiro renderiza o filho.
- [ ] `RedirectIfCoupled`: com parceiro redireciona para `/hub`; sem parceiro renderiza o filho.
- [ ] O caso "casal criado mas ainda sem parceiro" (`couple` presente, `partner` nulo) é testado nos quatro — é o estado que a dissolução (Épico 9) tornou comum e onde um `!couple` no lugar de `!couple?.partner` passaria despercebido.
- [ ] Todos os redirecionamentos usam `replace` (o histórico não acumula a rota barrada).
- [ ] Consultas por papel/texto acessível, nunca por classe CSS.
- [ ] `bun run test:run`, `typecheck` e `lint` verdes.

---

### US-010: Testes do `HubScreen`/`TrackSection` (T11.3)

**Descrição:** Como usuário, quero que uma falha de carregamento apareça em vez de me
deixar com uma seção vazia sem explicação — e quero isso verificado.

**Critérios de Aceite:**
- [ ] `TrackSection` renderiza os três estados: **carregando** (skeleton), **vazio** (a `emptyMessage` da seção) e **com itens** (grid).
- [ ] O botão/sentinela de "carregar mais" só aparece quando há próxima página, e o `IntersectionObserver` stubbado dispara `onLoadMore` uma única vez por interseção.
- [ ] **Existe teste que falharia se a falha de carregamento voltasse a ser silenciosa:** com o `GET` de uma seção falhando, o `fetchSectionPage` do `HubScreen` **para o skeleton** (não fica girando para sempre) **e** registra `console.error` com o status e a página — exatamente o comportamento que a US-007 do Épico 7 introduziu no lugar do `catch` mudo.
- [ ] O `HubScreen` monta as três seções (`Assistindo`, `Queremos Ver`, `Já Vimos`) e cada uma consulta seu próprio status.
- [ ] Consultas por papel/texto acessível, nunca por classe CSS.
- [ ] `bun run test:run`, `typecheck` e `lint` verdes.

---

### US-011: Testes do `SearchTab` e do `SuggestionsTab` (T11.3)

**Descrição:** Como desenvolvedor, quero o `switch` de status que substituiu a cadeia
de condicionais na T7 coberto em todos os ramos, incluindo o de erro.

**Critérios de Aceite:**
- [ ] `SearchTab` cobre os cinco estados de `SearchStatus`: `idle`, `loading`, `error`, `empty` e `ready`.
- [ ] Em `loading`, o **grid de resultados continua montado** (escondido por classe, não desmontado) — é o que preserva a posição de scroll e o estado dos cards entre buscas. Um teste que assuma desmontagem está errado; escrever o teste que trava o comportamento atual.
- [ ] Em `error`, a mensagem de falha é visível e o cabeçalho "Resultados para…" não aparece.
- [ ] `SuggestionsTab` com `pendingError` renderiza o ramo de erro visível (e não a fila vazia silenciosa).
- [ ] Erro de ação (like/reject falhando) mostra "Nao foi possivel registrar. Tentem novamente." sem derrubar a fila.
- [ ] Consultas por papel/texto acessível, nunca por classe CSS — **esta AC é a razão de o Épico 13 conseguir mexer em estilo depois sem quebrar nada**.
- [ ] `bun run test:run`, `typecheck` e `lint` verdes.

---

### US-012: Cobertura medida e limiar declarado (T11.4)

**Descrição:** Como mantenedor, quero saber em que patamar a cobertura ficou e
congelá-lo, para que ela não caia sem alguém perceber.

**Critérios de Aceite:**
- [ ] `bun run test:coverage` produz relatório com `@vitest/coverage-v8` (reporters `text` + `html`, com o diretório de saída no `.gitignore` do `client/`).
- [ ] `coverage.exclude` deixa de fora o que não é código de app: `src/test/**`, `src/components/ui/**` (primitivos shadcn/base-ui, não escritos aqui), `src/main.tsx`, `*.config.*` e `src/types/**` (interfaces puras, sem runtime).
- [ ] O patamar real é **medido** e registrado no PR (linhas/statements/functions/branches).
- [ ] `coverage.thresholds` no `vite.config.ts` fixado nesse patamar **arredondado para baixo**, com um comentário dizendo que é o valor medido em 2026-08-xx e que subi-lo é task própria (fora de escopo, conforme o épico).
- [ ] Baixar deliberadamente a cobertura (comentar um teste) faz `bun run test:coverage` **sair com código 1** — verificado na prática, não presumido.
- [ ] `bun run typecheck` e `bun run lint` verdes.

---

### US-013: Gate no CI (T11.4)

**Descrição:** Como mantenedor, quero que um PR com teste quebrado seja bloqueado,
porque teste que ninguém executa não é rede de segurança.

**Critérios de Aceite:**
- [ ] `.github/workflows/ci.yml`, job `frontend`: novo step **`Test`** rodando `bun run test:coverage`, posicionado **depois** do `Lint` e **antes** do `Build`.
- [ ] Sem `continue-on-error` e sem `|| true` — mesma postura do step `Audit dependencies` (é gate, tem que quebrar o build).
- [ ] Um teste deliberadamente quebrado reprova o job — verificado na prática e registrado no PR.
- [ ] Como o `deploy.yml` reusa o `ci.yml` via `workflow_call`, um teste quebrado **também barra o deploy** — declarado explicitamente no comentário do step.
- [ ] O tempo total do job `frontend` **antes e depois** é registrado no corpo do PR (AC do épico).
- [ ] Nenhum step existente (`Audit dependencies`, `Typecheck`, `Lint`, `Build`) é alterado.

---

### US-014: Documentação e fechamento do épico

**Descrição:** Como próxima pessoa a mexer no `client/`, quero descobrir a convenção
de teste pelo repositório, não por arqueologia de PR.

**Critérios de Aceite:**
- [ ] `client/CLAUDE.md`: a seção de testes da US-002 está completa e correta ao fim da implementação (comando, localização, mock de HTTP, reset de store, os stubs de jsdom, o limiar de cobertura e onde ele mora).
- [ ] `client/CLAUDE.md`: o "Sandbox gotcha: Vite dev server / build can't start" ganha a nota de que **`bun` instala via `npm install -g bun`** e que o `client/` deve ser instalado com `bun install` (nunca `npm install`, sob pena de divergir os dois lockfiles versionados).
- [ ] `docs/ARCHITECTURE.md` §2 ganha uma linha sobre a estratégia de teste do frontend (hoje o documento só declara JUnit 5/Mockito para o backend).
- [ ] `POST-MVP-TASK.md`: T11.1 a T11.4 marcadas com a linha `**Status:** ✅ **Concluída em <data>** — …` e os critérios de aceite viram `- [x]`. Critério não verificável fica `- [ ]` **com o motivo escrito ao lado**, nunca marcado por otimismo.
- [ ] A nota do Épico 13 ("Fortemente recomendado fazer o Épico 11 antes") é atualizada para registrar que a dependência foi satisfeita.
- [ ] `bun run test:run`, `typecheck`, `lint` e `build` verdes.

---

## Requisitos Funcionais

- **FR-1:** O `client/` deve ter os scripts `test` (watch), `test:run` (sem watch) e `test:coverage`.
- **FR-2:** A configuração do Vitest deve viver dentro do `client/vite.config.ts`; não deve existir `vitest.config.ts`.
- **FR-3:** Os testes rodam em `jsdom`, com `src/test/setup.ts` instalando `@testing-library/jest-dom/vitest`, `cleanup()` entre testes e os stubs de `matchMedia`, `IntersectionObserver` e `ResizeObserver`.
- **FR-4:** Nenhum teste pode fazer requisição de rede real, abrir WebSocket real ou depender de servidor STOMP.
- **FR-5:** Testes de store/hook/componente mockam `@/lib/api`; testes do próprio `lib/api.ts` usam adapter falso do axios instalado em `axios.defaults.adapter` antes do import.
- **FR-6:** Arquivos de teste são co-localizados com o módulo testado, nomeados `<módulo>.test.ts(x)`; `src/test/` contém apenas setup e utilitários.
- **FR-7:** As três stores Zustand são resetadas entre testes via `getInitialState()`.
- **FR-8:** Consultas em testes de componente usam papel/texto acessível (`getByRole`, `getByText`, `getByLabelText`); consulta por classe CSS é proibida.
- **FR-9:** Devem existir os 6 módulos de lógica cobertos com caminho feliz **e** caminho de erro: `useMatchStore`, `useAuthStore`, `useNotificationStore`, `useDiscoverSearch`, `useCompareSelection` e `lib/api.ts`.
- **FR-10:** Os 4 guards de `routes/guards.tsx` têm teste para cada estado de sessão relevante, incluindo "casal sem parceiro".
- **FR-11:** As telas `screens/hub/` e `screens/match/` têm cobertura dos estados carregando, vazio e erro.
- **FR-12:** `coverage.thresholds` é declarado no `vite.config.ts`, no patamar medido arredondado para baixo, e faz o comando sair com código 1 quando a cobertura cai abaixo dele.
- **FR-13:** O job `frontend` do `ci.yml` executa `bun run test:coverage` entre `Lint` e `Build`, como gate (sem `continue-on-error`, sem `|| true`).
- **FR-14:** Dependências do `client/` são instaladas com `bun`; `package.json` e `bun.lock` são atualizados na mesma operação.

## Non-Goals (Fora de Escopo)

- **Teste E2E** (Playwright, Cypress) e **regressão visual** — explicitamente fora, pelo próprio épico. Não há navegador no sandbox de qualquer forma.
- **MSW** — não entra por decisão (E11.6). Se a US-008 provar que o adapter falso não dá conta, vira decisão nova registrada aqui, não improviso.
- **Cobrir os 14 primitivos de `src/components/ui/`** — são wrappers de `@base-ui/react`, não código de domínio; ficam excluídos da cobertura.
- **Testes da landing page** (`components/landing/*`, `LandingScreen`) — o épico exclui explicitamente.
- **Telas nascidas depois do épico** (`screens/account/`, `ForgotPasswordPage`, `ResetPasswordPage`, `DashboardScreen`) — ficam sem teste de componente nesta rodada, por decisão E11.1. Se virarem prioridade, é task nova.
- **Subir o limiar de cobertura**, badge de cobertura, publicar relatório em serviço externo — todos fora, pelo próprio épico.
- **Qualquer mudança de comportamento ou de pixel no app.** Se uma story exigir mexer em código de produção para tornar algo testável, **parar e registrar** — a decisão é do mantenedor, não do implementador.
- **Testes do `api/`** — o backend já tem 48+ arquivos e gate JaCoCo de 90%.
- **Migrar o CI de `bun` para `npm`.**

## Considerações de Design

Nenhuma. Este épico não produz interface: são arquivos de teste, configuração e CI.

Por consequência direta, e conforme **E11.11**: a regra do `CLAUDE.md` da raiz que
exige a skill `frontend-design` para código em `client/` **não se aplica a nenhuma
story deste PRD** — o critério objetivo é "se qualquer pixel muda, a regra vale", e
aqui nenhum muda. É a mesma dispensa que a **D11** deu às refatorações puras do
Épico 7. Se, no meio da implementação, alguma story precisar alterar um componente de
forma visível para torná-lo testável, a dispensa cai para aquela story e a skill volta
a ser obrigatória — mas o caminho correto é **não** fazer isso (ver Não-Goals).

## Considerações Técnicas

**Os arquivos de teste entram no `tsc -b` e no `eslint .`.** `tsconfig.app.json` tem
`include: ["src"]`, e a co-locação (E11.8) põe os testes dentro de `src/`. Isso é
desejável — teste com erro de tipo é bug — mas significa que `strict`,
`noUnusedLocals`, `noUnusedParameters`, `verbatimModuleSyntax` e `erasableSyntaxOnly`
valem para eles. Em particular, `verbatimModuleSyntax` exige `import type` para
imports só-de-tipo, inclusive em mocks.

**O `useAuthStore` lê `localStorage` em escopo de módulo.**
`const initialSession = loadPersistedSession()` roda **uma vez, no import**. Um teste
que semeie o `localStorage` depois disso não vê efeito nenhum. O caminho é
`vi.resetModules()` + `await import("@/stores/useAuthStore")` **depois** de semear.
Essa é a pegadinha mais provável de custar meia hora a quem escrever a US-004.

**`refreshPromise` do `lib/api.ts` também é de escopo de módulo.** Mesmo remédio:
`vi.resetModules()` entre testes da US-008, senão um single-flight de um teste vaza
para o seguinte.

**`define: { global: "globalThis" }` no `vite.config.ts` existe por causa do
`sockjs-client`.** Ele precisa continuar valendo no ambiente de teste; o bloco `test`
não deve substituir a config, só somar.

**Zustand 5 expõe `getInitialState()`**, o que torna o reset trivial. Não escrever um
`createStore` de teste paralelo nem re-exportar as stores — a store testada tem que
ser a mesma que o app usa, senão o teste não prova nada sobre o app.

**`bun` não é permanente no sandbox.** Se uma sessão nova não o encontrar,
`npm install -g bun` resolve em ~3s (verificado). O que **não** pode acontecer é
alguém rodar `npm install` dentro de `client/` para instalar dependência: os dois
lockfiles são versionados, o CI usa o `bun.lock`, e a divergência só apareceria no CI.

**O `Audit dependencies` do CI é gate.** As devDependencies novas desta US-001 passam
a ser auditadas por `bun audit --audit-level=high`. Se alguma trouxer advisory
high/critical, o job `frontend` quebra — e a decisão (corrigir via `overrides` ou
`--ignore` com justificativa) segue a tabela do `client/CLAUDE.md`, atualizada junto.

**Tempo de CI.** O job `frontend` hoje faz install + audit + typecheck + lint + build.
O step de teste soma. A T11.4 pede o registro do antes/depois no PR justamente para
que a conta seja explícita — e é o insumo para decidir, no futuro, se vale paralelizar
o job.

## Métricas de Sucesso

- `bun run test:run` executa a suíte inteira em **menos de 30 segundos** localmente (se passar disso, é sinal de teste fazendo trabalho demais — timer real, fetch não mockado).
- **100% dos módulos do FR-9** têm caminho feliz e caminho de erro cobertos.
- Reintroduzir deliberadamente qualquer um dos **cinco bugs históricos** (vazamento de subscription, `celebratedMatchKeys` sem limpeza, redirect no session probe, refresh em password challenge, falha de carregamento silenciosa) faz a suíte ficar **vermelha** — verificado um a um, não presumido.
- Um PR com teste quebrado é **bloqueado** pelo CI.
- O tempo do job `frontend` cresce em **menos de 60s**.
- A T13.2 (Épico 13) consegue trocar hex por token em ~30 arquivos com a suíte verde do começo ao fim — é a prova final de que os testes consultam comportamento, não estilo.

## Questões em Aberto

1. **`src/components/ui/` fica fora da cobertura — para sempre?** A US-012 os exclui por serem wrappers de `@base-ui/react`. O `select.tsx`, o `slider.tsx` e o `toggle-group.tsx`, porém, foram **escritos à mão** neste projeto (o sandbox não alcança `ui.shadcn.com`), então não são "código de terceiros" no mesmo sentido dos demais. Manter excluídos nesta rodada; revisitar se algum deles ganhar lógica própria.
2. **O `DashboardScreen` não tem nenhum teste e consome só `GET /api/tracking/stats`.** Ficou fora por decisão de escopo (E11.1), mas é a tela com mais cálculo derivado do app. Vale uma story avulsa depois do épico?
3. **Vale um step separado de teste no `deploy.yml`?** Hoje o `deploy.yml` reusa o `ci.yml` inteiro via `workflow_call`, então o gate já cobre o deploy sem mudança nenhuma. Registrado só para não ser redescoberto como lacuna.
4. **`package-lock.json` continua versionado sem uso.** O CI usa `bun.lock`; o `package-lock.json` só existe por inércia e é uma fonte permanente de confusão sobre qual gerenciador usar. Removê-lo é uma decisão de topologia de dependências, fora deste épico — mas alguém deveria tomá-la.
