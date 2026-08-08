# Frontend (client/)

React + TypeScript + Vite, Tailwind v4 (CSS-based config in `src/index.css`, no `tailwind.config.*`), Shadcn UI primitives under `src/components/ui`.

## Conventions

- Screens live in `src/screens/*.tsx`, route wiring in `src/App.tsx`, nav links in `src/components/Header.tsx`'s `NAV_ITEMS`.
- Dark theme prototype colors are inlined via Tailwind arbitrary values (`bg-[#161513]`, `border-[rgba(255,255,255,.07)]`, etc.) rather than theme tokens — match this pattern for new screens instead of introducing new design tokens, since the prototype in `docs/design/claude-design-project/Sessao a Dois.dc.html` is the source of truth for exact colors/spacing.
- `font-display` (Bricolage Grotesque) for headings/big numbers, `font-auth-body` (DM Sans) for body text — both defined in `src/index.css`.
- API calls go through `src/lib/api.ts` (`api.get/post(...)`), which sends `withCredentials: true` and attaches nothing itself — the session lives in HttpOnly cookies (`access_token`/`refresh_token`), never in a header or `localStorage` the client can read. A 401 triggers a single-flight `POST /api/auth/refresh` (via a separate `refreshClient` with no response interceptor, so a 401 from the refresh call itself can't re-enter the retry loop) and replays the original request once; if the refresh also fails, it redirects to `/login`. Response DTO shapes are hand-mirrored as TS interfaces under `src/types/*.ts` (e.g. `types/tracking.ts`, `types/stats.ts`) — keep them in sync with the backend records manually, there's no codegen.
- Routes requiring a couple use the `RequireCouple` guard (`src/routes/guards.tsx`); routes requiring auth use `ProtectedRoute`.

## Triagem de vulnerabilidades (`bun audit`) — Epico 8, US-004

Estado em **2026-08-06**, depois de `bun update` (sem `--latest`): **15 advisories, 6 high, 9 moderate**. Nenhum advisory critico. `bun audit` (1.3.14) **nao tem** flag `--prod`/`--production` (so `--json`, `--audit-level`, `--ignore`), entao a separacao producao/build e feita por esta tabela e pela lista de `--ignore` do `ci.yml` (decisao E8.9 do PRD), nunca por flag.

Os 6 **high** e a decisao de cada um:

| Pacote (versao resolvida) | Advisory | Caminho de dependencia | Tipo | Versao corrigida | Decisao |
|---|---|---|---|---|---|
| `brace-expansion` 5.0.7 | `GHSA-mh99-v99m-4gvg` (DoS por expansao ilimitada) | `eslint › @eslint/config-array › minimatch`, `typescript-eslint › … › minimatch`, `shadcn › ts-morph › @ts-morph/common › minimatch` | **devDependency** (via `eslint`/`typescript-eslint`) e `shadcn` (ver nota abaixo) | sim, `5.0.9` | **Ignorar.** So roda no lint/CLI, sobre um glob que nos mesmos escrevemos. Nao entra no bundle. |
| `brace-expansion` 5.0.7 | `GHSA-rgw5-rvv9-x895` (DoS, bypass da mitigacao anterior) | idem | idem | sim, `5.0.9` | **Ignorar**, mesmo motivo. |
| `fast-uri` 3.1.4 | `GHSA-7p8r-x3mc-p8w7` (host confusion via backslash) | `eslint › ajv`, `shadcn › @modelcontextprotocol/sdk › ajv` | **devDependency** / CLI | sim, `3.1.5` | **Ignorar.** `ajv` aqui so valida schema de config do eslint/MCP em build time; nao ha URL de atacante nesse caminho. |
| `ip-address` 10.2.0 | `GHSA-mwp4-54f8-5fhr` (SSRF via octeto com zero a esquerda) | `shadcn › @modelcontextprotocol/sdk › express-rate-limit` | CLI do shadcn | sim, `>10.3.0` | **Ignorar.** O `express-rate-limit` vem do servidor MCP embutido na CLI do shadcn — nunca executado por esta app nem pelo build. |
| `undici` 7.28.0 | `GHSA-4cwx-7wf7-3272` (cache poisoning / disclosure) | `shadcn › @dotenvx/dotenvx` | CLI do shadcn | sim, `7.29.0` | **Ignorar.** Cliente HTTP usado pela CLI; o runtime do usuario usa `fetch`/axios do browser. |
| `react-router` 7.18.2 | `GHSA-qwww-vcr4-c8h2` (RSC Mode CSRF bypass) | `react-router-dom › react-router` | **dependencies (producao)** | so no major `8.3.0` | **Ignorar com justificativa.** O vetor e o *RSC mode* do React Router; esta app e uma **SPA em Vite sem React Server Components** — nao ha server action para executar antes do 400. A correcao e o major `7.x → 8.x`, que **nao entra no Epico 8**: vem por PR do Dependabot (US-003), revisado a parte. Revisitar se algum dia a app adotar SSR/RSC. |

Por que o `bun update` **nao** corrigiu os que ja tem patch disponivel: `bun update` sem argumento so mexe nas dependencias **diretas**; as transitivas ficam na resolucao gravada no `bun.lock`. E `bun update <pacote-transitivo>` **nao** e o caminho — ele *adiciona o pacote como dependencia direta* no `package.json` (verificado na pratica: uma tentativa acrescentou `brace-expansion`, `undici`, `fast-uri`, `ip-address`, `postcss`, `hono` e `@hono/node-server` ao bloco `dependencies`). Para forcar uma transitiva sem sujar o `package.json` o mecanismo e `overrides`/`resolutions` — nao usado aqui porque todos os patches pendentes sao de devDependency/CLI, risco zero para o usuario, e um `override` de major (`undici` 8, `fast-uri` 4) pode quebrar a ferramenta que o consome.

**Nota sobre `shadcn`:** esta declarado em `dependencies` no `package.json`, mas e uma **CLI**, nao e importada por nenhum arquivo de `src/` (`grep -rn "from ['\"]shadcn" src/` nao retorna nada) e portanto nao entra no bundle — todos os advisories abaixo dela sao build-time na pratica. A classificacao no `package.json` esta imprecisa; mover para `devDependencies` e seguro (nada em producao instala estas deps — o deploy envia o `dist/` pronto por `scp`), mas ficou **fora** desta story para nao mexer na topologia de dependencias sem decisao do mantenedor. Se mover, a tabela acima continua valida.

Os 9 **moderate** (`@hono/node-server`, `hono`, `postcss`, mais os outros de `ip-address`/`undici`) nao quebram o gate — `--audit-level=high` (decisao E8.2). O `postcss` e o unico que toca o build de verdade (`vite › postcss`), e o vetor exige `sourceMappingURL` controlado por atacante dentro do proprio CSS do repo.

**Ao revisar esta tabela** (PR do Dependabot, advisory novo): a fonte da verdade e `cd client && bun audit`; a lista de `--ignore` do step **`Audit dependencies`** do job `frontend` em `.github/workflows/ci.yml` tem que ficar em sincronia com as linhas "Ignorar" acima — advisory removido daqui e `--ignore` que sobra la vira ponto cego.

Desde a **US-005 (2026-08-06)** esse step existe e **e gate**: `bun audit --audit-level=high` com os 6 `--ignore` desta tabela, sem `continue-on-error` e sem `|| true`. Consequencia pratica: um advisory **high/critical novo** (ou um dos 6 acima que perca o `--ignore`) **quebra o job `frontend` e, por tabela, o deploy** — o `deploy.yml` reusa o `ci.yml` via `workflow_call`. Moderate e low nao quebram nada, so aparecem no log.

Detalhes que economizam tempo em quem for mexer no step:
- O `--ignore` do `bun audit` **aceita o id GHSA** (`GHSA-xxxx-xxxx-xxxx`), o mesmo que o comando imprime — verificado na pratica na US-005. Nao e preciso traduzir para CVE.
- Os ignores estao num array bash (`IGNORES=(...)` / `IGNORES+=(...)`, uma linha por advisory, com o comentario acima) em vez de uma unica linha com `\` de continuacao: assim cada `--ignore` carrega o porque e o que faria revisitar a decisao, sem depender de truque de comentario dentro de continuacao de linha.
- Para reproduzir o gate localmente, rode o comando com os mesmos ignores; para provar que ele ainda morde, tire um `--ignore` e confirme que sai com codigo 1.

## `typecheck` script must use `tsc -b`, not `tsc --noEmit`

The root `tsconfig.json` has `"files": []` and only `references` to `tsconfig.app.json`/`tsconfig.node.json` (standard Vite project-references setup). Running plain `tsc --noEmit` against it checks **zero files** and always exits 0 — it never actually type-checks `src/`, silently. `package.json`'s `typecheck` script must use `tsc -b` (build mode, which follows `references`), same as the first half of the `build` script (`tsc -b && vite build`). If you ever touch `tsconfig*.json` or the `typecheck`/`build` scripts, verify with a deliberate type error (add one, confirm the script exits non-zero, revert) rather than trusting a clean run — a no-op script produces a clean run too.

## Lint gotcha: resetting state on route/prop change

The `eslint-plugin-react-hooks` config here forbids calling `setState` synchronously inside a `useEffect` body (`react-hooks/set-state-in-effect`) and forbids reading/writing `ref.current` during render (`react-hooks/refs`). To reset a subtree's local state when something external changes (e.g. closing a menu on route navigation), extract the stateful part into its own subcomponent and mount it with `key={someChangingValue}` from the parent — remounting resets `useState` without effects or refs. See `src/components/Header.tsx`'s `MobileNav` for an example.

## Lint gotcha: `Date.now()` (or `new Date()`) called during render

`react-hooks/purity` flags any impure call (e.g. `Date.now()`) made directly in a component's render body, even just to derive a value like "ms until expiry" — not only the `useEffect`/state-reset cases above. Fix: capture it once via `const [now] = useState(() => Date.now())` (the impure call is only inside the lazy initializer, which the rule allows) and derive everything else from that captured value instead of calling `Date.now()` inline. See `InviteCodeTicket.tsx`'s expiry countdown (US-010) for an example.

## Modal patterns

Two structural patterns exist for modals/dialogs, don't assume they're all the same:
- shadcn/base-ui `Dialog` (`TitleModal.tsx`, `ComparisonDialog.tsx`) — width/height controlled via the `className` prop on `DialogContent` (`src/components/ui/dialog.tsx`), which already centers the popup itself. Pass `showCloseButton={false}` and roll your own close `Button` with a Portuguese `aria-label` (e.g. `"Fechar"`) instead of the default English "Close" one; also add `<DialogTitle className="sr-only">…</DialogTitle>` (+ `DialogDescription` if useful) since base-ui's `Dialog.Popup` expects an accessible name.
- Hand-rolled `fixed inset-0` backdrop `<div>` with a plain inner `<div>` (`MediaDetailModal`, `PendingDetailModal`, `ReviewModal`, `WatchModal`, `DeleteTrackDialog`) — has its own click-outside/Escape-key handling; width/height go directly on the inner div.

For mobile-responsive sizing on either pattern, use `w-[calc(100vw-32px)] max-w-[calc(100vw-32px)]` as the mobile default and restore the desktop fixed width at `sm:` (e.g. `sm:w-full sm:max-w-[420px]`), plus `max-h-[90svh] overflow-y-auto` for vertical overflow — unless the modal already has its own internal scroll region (like `TitleModal`'s body div), in which case don't double up scrolling on the outer container.

A shadcn `Dialog` that a component opens on behalf of something else (e.g. `RatingRequestDialog`, opened from `NotificationDropdown` when a notification is clicked) must be rendered as a **sibling** of any `Popover`/`PopoverContent` in the same parent, not nested inside `<Popover>` — base-ui's `Popover.Root` only expects `Trigger`/`Content` children. Wrap the parent's return in a Fragment (`<>...</>`) to place them side by side. Same rule applies when a list-item component (e.g. `MediaCard`) has an outer clickable `<div onClick={...}>` wrapping the whole card and needs to open its own `Dialog`: render the `Dialog` as a sibling of that div (Fragment), not a child — Portal-rendered content still bubbles synthetic clicks through the React tree, so a `Dialog` nested inside the clickable div would trigger the card's `onClick` on every click inside the dialog.

## Sandbox gotcha: no network access for `npx shadcn add`

This sandbox has no route to `ui.shadcn.com` (`npx shadcn add <component>` fails with `EHOSTUNREACH`), so any shadcn primitive not already under `src/components/ui` (e.g. `select.tsx` added for US-008) must be hand-written against the installed `@base-ui/react` primitive instead of fetched. Follow the existing file for the same family (`popover.tsx` for portal/positioner/popup-based components, `collapsible.tsx`/`button.tsx` for simpler ones): `data-slot` on every part, `cn(...)` for class merging, theme tokens (`bg-popover`, `border-border`, `bg-primary`, etc. from `src/index.css`) as the component's own default styling, and let screens override those tokens with the prototype's arbitrary hex values via `className` (same pattern `NotificationDropdown.tsx` already uses on `PopoverContent`). Check `node_modules/@base-ui/react/<primitive>/**/*.d.ts` for the exact part names/props (e.g. `Select.Root`/`Trigger`/`Value`/`Icon`/`Portal`/`Positioner`/`Popup`/`Item`/`ItemText`/`ItemIndicator`) before writing the wrapper.

## Toggle/ToggleGroup (multi-select chips)

`src/components/ui/toggle-group.tsx` wraps `@base-ui/react/toggle` and `@base-ui/react/toggle-group` the same way `select.tsx` wraps `@base-ui/react/select` (see the sandbox gotcha below — not fetched via `npx shadcn add`). Both `Toggle`/`ToggleGroup` are exported as plain function components (not `.Root`-namespaced like `Select`/`Popover`). `ToggleGroup`'s `multiple` prop enables multi-select chip groups (each `ToggleGroupItem` needs a unique string `value`); `onValueChange` receives `(value: string[], eventDetails)`, so a bare `setState` setter can be passed directly since JS/TS allow assigning a callback with fewer params. Active/pressed state styling is driven by the `data-pressed` attribute (`data-[pressed]:...` Tailwind variant), matching the `MatchScreen.tsx` filter chips (Data de lancamento / Classificacao / Generos).

## Range slider (two thumbs)

`src/components/ui/slider.tsx` wraps `@base-ui/react/slider` (`Slider.Root/Control/Track/Indicator/Thumb`, namespace import `Slider as SliderPrimitive`, same pattern as `select.tsx`) — not fetched via `npx shadcn add` (see sandbox gotcha below). For a two-thumb range, pass `value`/`onValueChange` as a `[number, number]` tuple and render two `<SliderThumb index={0} />`/`<SliderThumb index={1} />` inside one `<SliderControl>`; `minStepsBetweenValues={1}` stops the thumbs from crossing. See `MatchScreen.tsx`'s Nota TMDB / Duracao filters (US-010) for the reference usage.

## Lint gotcha: fetching data on mount that needs a synchronous `setLoading(true)`

`react-hooks/set-state-in-effect` also flags a `setState` call written as a direct statement in a `useEffect` body (e.g. `setLoading(true)` right before an `api.get(...)`), even on mount (`[]` deps) — not just resets on prop change (see the other lint gotcha above). The existing debounced-search effect in `MatchScreen.tsx` already sidesteps this by wrapping the whole body in `setTimeout(() => { ... }, N)`, since a `setState` call nested inside a further callback (timeout/promise `.then()`) isn't flagged, only one directly in the effect's own function body. Reuse that `setTimeout(..., 0)` (+ `cancelled` flag, cleared via the effect's cleanup) wrapper for any new "fetch on mount" effect that needs a synchronous loading flag — see the initial `/api/media/trending` load in `SearchTab` (US-012).

## Pagination (numbered pager)

`src/components/ui/pagination.tsx` is plain `<nav>/<ul>/<li>/<button>` styled with `buttonVariants` from `button.tsx` — unlike Select/Slider/ToggleGroup, shadcn's own Pagination isn't built on a `@base-ui/react` primitive, so this one didn't need the hand-roll-against-base-ui treatment, just the same `data-slot`/`cn(...)`/theme-token-default pattern. `PaginationLink`/`PaginationPrevious`/`PaginationNext` accept a plain `disabled` prop (they're `<button>`s, not links) since this app drives pagination via component state (`page`), not routes. See `MatchScreen.tsx`'s `SearchTab` (US-014) for the reference usage, including `paginationRange(current, total)` (windowed page numbers with `"ellipsis"` markers) and the `lastFetchRef` pattern: every fetch that populates `results` (trending/search/discover) stores `{url, params, context}` (params without `page`) after success, so `handlePageChange` can replay the same request with just a different `page` without re-deriving which endpoint/filters are active.

## `screens/match/` — MatchScreen fully decomposed (Epico 7, US-005/US-006)

`src/screens/MatchScreen.tsx` (the old 1482-line root file) no longer exists. Everything lives under `src/screens/match/`: the shell (`MatchScreen.tsx`, tab switcher), `SuggestionsTab.tsx`, `SearchTab.tsx` (orchestration: `useDiscoverSearch` + `useCompareSelection` + `trackedKeys`/`likeStates` local state + `handleLike`), `FiltersPanel.tsx` (the search box/sort/filters `Collapsible`, presentational — all filter state passed in as props), and `SearchResultCard.tsx` (one result card, presentational). Shared helpers (`trackKey`, `paginationRange`, `formatResultsCount`, `formatVoteRangeLabel`, `formatMinutes`, `formatRuntimeRangeLabel`, `keyOfCompareItem`, `TYPE_LABEL`, `LikeState`) are in `helpers.ts`; the two small shared components (`CompareToggleButton`, `CompareSelectionChip`) are in `compareUi.tsx` — kept in a **separate file from the helpers** because `eslint-plugin-react-refresh`'s `react-refresh/only-export-components` rule forbids a `.tsx` file from exporting both components and plain functions/constants; when moving shared UI + shared logic together, always split them this way, don't put them in one `shared.tsx`. `App.tsx` imports the screen from `@/screens/match/MatchScreen`.

`SearchTab.tsx`'s results-list "5 mutually exclusive conditional blocks" (idle/loading/error/empty/no-explicit-block-for-ready) collapsed into one derived `status: 'idle' | 'loading' | 'error' | 'empty' | 'ready'` rendered via `switch` — but the results grid itself and the "Resultados para..." header are still rendered **unconditionally outside the switch** (same `searched`/`fetchError`/`showSearchSkeleton && "hidden"` guards as before), because they were never part of the original conditional chain — only the message/skeleton region between the header and the grid was. Don't fold the grid or header into the switch's `ready` branch; `ready` intentionally renders `null` since the "content" for that state is the always-mounted grid below it, exactly matching pre-refactor behavior. The grid must stay mounted (className-`hidden`, not unmounted) while `status === "loading"`, or pagination scroll position / individual card state would reset on every search.

## `SearchTab`'s search/filter/pagination state lives in `useDiscoverSearch`

`src/screens/match/useDiscoverSearch.ts` (US-004, Epico 7) is a `useReducer`-backed hook that owns query, filters (`sortBy`/`filtersOpen`/`genres`/`selectedDecades`/`selectedCertifications`/`selectedGenres`/`voteRange`/`runtimeRange`), results (`results`/`totalResults`/`resultsContext`/`searched`), pagination (`page`/`totalPages`) and network status (`searching`/`fetchError`) for `MatchScreen.tsx`'s `SearchTab` — one flat `State` object plus a discriminated `Action` union, not `useState` per field. It also exports `SORT_OPTIONS` and `TMDB_MAX_PAGE` (both consumed by `SearchTab`'s JSX, not just the hook's own logic) — import them from there rather than redeclaring. `trackedKeys`/`likeStates` deliberately stay as local `useState` in `SearchTab` itself (match/like state, not search state, per PRD decision E7) — don't fold those into the hook. The `lastFetchRef`/`lastAttemptRef` pagination-replay pattern and the debounced-search `setTimeout(...,0)` effect (see the lint gotcha above) both live inside this hook now, preserved verbatim from before the extraction — treat them the same way (don't "clean up" the setTimeout wrapper). It lives in `screens/match/` rather than `lib/` because it has exactly one consumer (decision E5) — don't promote it to `lib/` unless a second screen actually needs the same search/filter state machine.

## `screens/hub/` — HubScreen decomposed (Epico 7, US-007)

`src/screens/HubScreen.tsx` (the old ~523-line root file) no longer exists. Everything lives under `src/screens/hub/`: `HubScreen.tsx` (shell — all `useState`/effects/modal orchestration, 7 `useState` total, none moved into the child components), `TrackSection.tsx` (one collapsible section: header/count/chevron, skeleton/empty/grid branches, the incremental "load more" button+skeleton — fully presentational, takes the section's `SectionState` slice and every callback as props, no local state), and `LoadMoreSentinel.tsx` (the IntersectionObserver sentinel, moved verbatim). Plain constants/types/helpers (`SECTIONS`, `Section`, `PAGE_SIZE`, `SectionState`, `emptySectionState`/`emptySections`, `buildComparisonItem`, `COMPARE_TOOLTIP`) live in `helpers.ts` — same component/helper file split as `screens/match/` (`react-refresh/only-export-components` forbids mixing them in one `.tsx`). `TrackSection` takes the raw `compareSelected: MediaTrackResponse[]` array (not a precomputed boolean/index) so its per-card `compareSelected`/`compareOrder` lookup logic is identical to what the shell used to do inline. `App.tsx` imports the screen from `@/screens/hub/HubScreen`.

The one intentional non-pure change from this story: `fetchSectionPage`'s catch (in `HubScreen.tsx`) now does `console.error` with the section status and page before clearing the loading flags, instead of swallowing the error silently — the visual failure behavior (stop the skeleton, keep whatever already loaded) is unchanged.

## `screens/account/` — tela `/conta` e o token destrutivo

`/conta` (Epico 9, US-008) e a primeira tela de configuracoes e a unica rota **autenticada
fora do `RequireCouple`** — quem nao tem casal (inclusive quem acabou de dissolver) precisa
chegar nela, entao envolva-a so em `ProtectedRoute`. Entrada no `Header`: **nao** entra em
`NAV_ITEMS` (aquele pill agrupa as rotas que exigem casal); e um `NavLink` com icone de
engrenagem no cluster da direita (`hidden md:grid`) + um item apos um divisor dentro do
`MobileNav`.

O **tratamento destrutivo** do design system nasceu em `screens/account/AccountSection.tsx`
e nao existe em outro lugar — reuse de la em vez de escolher um vermelho novo:
`#ff5c47` (coral quente, vizinho de matiz do `#ff9e2c` ja usado, para ler como "o mesmo
mundo ficando hostil", nao como um vermelho de sistema), borda `rgba(255,92,71,.28)`,
tinta de fundo `rgba(255,92,71,.07)` em gradiente vertical, titulo `#ffb3a5`, chip
`#ff8f7c`. Passe `destructive` ao `AccountSection` em vez de aplicar as cores na mao.

O marcador estrutural da tela e o **alcance** (`reach`), nao uma numeracao: `"you"`
(ambar) para o que so mexe na sua conta, `"both"` (coral) para o que alcanca o parceiro
— desfazer o vinculo e excluir a conta. Toda secao nova precisa declarar o seu.

## Loading-state pattern: Skeleton vs. button spinner

Every read (GET/fetch) loading state uses `Skeleton`-based components (`src/components/ui/skeleton.tsx` + the layout-aware compositions in `src/components/skeletons/`) gated through `useDelayedLoading` (`src/lib/useDelayedLoading.ts`, `delayMs=150`/`minVisibleMs=400` defaults) — never a spinner (`Loader2`/`animate-spin`) for a read. Action buttons (POST/PUT/DELETE submits, e.g. "Curtir"/"Salvando…") keep their own `Loader2`/`animate-spin` button-feedback state as-is; that's a different UX concern (in-flight mutation, not "content not ready yet") and is intentionally left alone. When a screen/store needs a delayed-loading gate but has no data-correctness-only flag yet (e.g. `hasMore`, `isEmpty`), keep that flag on the raw (non-delayed) boolean — only the *visual* skeleton/content branch should read the delayed `showSkeleton` value.

## Selection mode on an existing list/card screen (compare-titles pattern)

`HubScreen.tsx` (US-003) adds a toggleable "Comparar" mode without changing `MediaCard`'s core click contract: pass `compareMode`/`compareSelected`/`compareOrder`/`onCompareToggle` as new optional props (all default to off/undefined), and route the card's single root `onClick` through a ternary (`compareMode ? onCompareToggle?.(track) : onClick?.(track)`) rather than adding a second click handler. Same idea applies to any other card grid that needs a similar selection mode: keep the existing `onClick` prop meaning intact for callers that don't pass `compareMode`, and gate any state-mutating footer actions (start watching, mark watched, rate) behind `!compareMode` so they can't fire accidentally while the user is mid-selection.

`MatchScreen.tsx` (US-004) reuses the same compare-mode idea across its two tabs (`SearchTab`/`SuggestionsTab`), and `HubScreen.tsx` (US-003) reuses it a third time, via a shared `useCompareSelection<T>(buildItem, keyOf)` hook that now lives in its own file (`src/lib/useCompareSelection.ts`, extracted in US-001) rather than module-scope in `MatchScreen.tsx` — 3 call sites, not 2. It owns the mode flag, the 2-item selection array, the `Promise.all`-both-details fetch effect, and the Escape-key listener; `T` is a different source shape per call site (`MediaSearchResult`/`PendingMatch`/`MediaTrackResponse`), so `buildItem`/`keyOf` are passed in rather than hardcoded. When two tab components are rendered as `{cond && <TabA/>}` / `{cond && <TabB/>}` (mutually exclusive, different component types), switching tabs already fully unmounts/remounts each one — any `useState` local to a tab component (including a `useCompareSelection` instance) resets on tab switch for free, no `key={activeTab}` needed. Only reach for the `key={changingValue}`-on-a-subcomponent trick (see the lint gotcha above) when the resettable state lives somewhere that *stays* mounted across the change, e.g. a shared parent. `HubScreen.tsx` has no tabs to unmount into, so its single `compare` instance just lives for the screen's lifetime — exiting compare mode is what resets it, via the hook's own `exitCompareMode`. `CompareToggleButton`/`CompareSelectionChip` are still local to `MatchScreen.tsx` (not shared yet — US-005 moves them to `screens/match/`), so `HubScreen.tsx` keeps its own inline JSX for the toggle button and selection chip, just wired to the shared hook's return values instead of local state. Also: a "compare 2 items" flow doesn't have to reuse the screen's primary list UI — `SuggestionsTab`'s normal view is a one-at-a-time swipe card with no way to see a 2nd item, so its compare mode swaps to a small grid over the same `pendingQueue` data instead of forcing multi-select onto the swipe metaphor.

For a "fetch 2 things then show a dialog" flow (2nd selection triggers `Promise.all` of two detail fetches): don't open the real content dialog until both fetches resolve. `ComparisonDialog.tsx`'s `left`/`right` props accept `ComparisonItem | null` — passing `null` renders a skeleton column (same chrome, `Skeleton` blocks shaped like the real content) instead of needing a separate loading modal. Drive the dialog's `open` prop as `loading || (left !== null && right !== null)`; on fetch failure, leave `loading` false and both items `null` so the dialog simply never opens, and surface the error next to the selection UI (with a retry) instead of inside the dialog — keeps the "2 selected" state intact for retry without an extra error-handling branch inside the dialog component itself. To retry without duplicating the fetch effect's body, give the effect's dependency (`selected` inside the hook) a new array identity (`setSelected(prev => [...prev])`, exposed as the hook's `retry`) rather than writing a second copy of the fetch logic.

## `useDelayedLoading` gotcha: fetches that can fail

`useDelayedLoading(loading)` only stops showing the skeleton once `loading` turns `false`. If a fetch effect adds its own `xError` state alongside the `data` state (e.g. to show a degraded "couldn't load" message instead of the real content), the `loading` argument must be `!data && !xError`, not just `!data` — otherwise a failed fetch leaves `data` permanently `null`, `loading` never turns `false`, and the skeleton spins forever instead of handing off to the error branch. See `MediaDetailModal.tsx`/`PendingDetailModal.tsx` (US-007) for the pattern: a `detailsError` boolean, an extracted `fetchDetails(current, isCancelled)` callback reused by both the effect and a `handleRetryDetails` handler, and a three-way render (`detailsError ? <error+retry> : showSkeleton ? <skeleton> : <content>`).

## Sandbox gotcha: Vite dev server / build can't start

`npm run dev` (and `vite build`) intermittently fails in this sandbox with `Cannot find module './<name>.linux-arm64-gnu.node'` — a plain `npm install` doesn't always pull the optional arm64 native bindings for every native-dependent package. As of 2026-07-25, the fix that worked was installing the three missing optional packages directly: `npm install @rolldown/binding-linux-arm64-gnu lightningcss-linux-arm64-gnu @tailwindcss/oxide-linux-arm64-gnu` (rolldown → lightningcss → tailwind oxide failed in that order, one at a time, each surfaced by re-running `npm run dev` after the previous fix). After all three are present, `npm run dev` starts cleanly (`VITE ready`). No browser (Chromium/Playwright) is installed in this sandbox and there's no sudo to install its system deps (`libnspr4`, `libnss3`, etc.), so even with the dev server running, only static code review / `tsc` / `eslint` are available — no real screenshots or rendered-page checks. Note the API (Spring Boot) still has no JDK/Maven in this sandbox, so full end-to-end (frontend hitting a live backend) can't be verified here either.

### Mutacao de perfil: quem escreve no `useAuthStore`

Uma tela **nunca** faz `api.patch("/api/user/me")` e depois um `set({user})` por fora: o
`localStorage` (`sessaoADois.session`) e o `user` do store tem que mudar juntos, e a funcao
`persistSession` e privada do modulo. O caminho e a acao `updateProfile(request)` do
`useAuthStore` (US-009), que manda o PATCH, persiste e devolve o `AuthUser` novo — e por
isso que o `Header` reflete o nome novo sem reload. Toda mutacao futura do proprio usuario
(senha, dissolucao, exclusao) deve nascer como acao do store pelo mesmo motivo.

### Encerrar a sessao do lado do cliente: `clearSession`, nao `logout`

`useAuthStore.clearSession()` (US-010) e o unico lugar que derruba o estado de sessao do
cliente: desconecta o WebSocket da `useMatchStore`, limpa o `localStorage` e zera
`user`/`couple`/`isAuthenticated`. `logout()` agora e so `POST /api/auth/logout` + essa
acao. Uma tela que ja recebeu do backend uma resposta que **invalidou os cookies**
(`PUT /api/auth/password` responde 204 com os dois cookies expirados — E9.8) deve chamar
`clearSession()` direto: chamar `logout()` ali dispararia um POST que so pode tomar 401.

Quando a sessao cai **de proposito**, o motivo tem que sobreviver ao redirect, senao a
tela de login parece bug. O caminho e `navigate("/login", { replace: true, state: { notice } })`
— a `LoginPage` le `useLocation().state?.notice` e mostra a faixa ambar. A constante da
mensagem mora em `screens/account/helpers.ts`, nao no `.tsx` do bloco: um arquivo de
componente que exporta tambem um valor comum reprova em `react-refresh/only-export-components`
(exportar dois componentes, ou um componente + `export type`, passa).

### Desfazer o vinculo: `dissolveCouple` (e o WebSocket junto)

`useAuthStore.dissolveCouple()` (US-011) manda o `DELETE /api/couple/me`, **desconecta a
`useMatchStore`** e zera o `couple` (store + `localStorage`), mantendo a sessao. O
disconnect nao e opcional: sem ele a store continua assinando `/topic/couple/{id}/**` de
um casal que nao existe mais. Mesma regra da mutacao de perfil — a tela nunca chama a API
e mexe no store por fora. Erros do endpoint: **404** = ja nao ha casal ativo, **429** =
limite de 5/h por usuario; cada um com texto proprio.

### Link com cara de botao: `render`, nao `asChild`

O `Button` de `components/ui/button.tsx` embrulha `@base-ui/react/button`, que **nao tem**
`asChild` (padrao do Radix): o equivalente e a prop `render`
(`<Button render={<Link to="/join" />}>Formar um casal</Button>`), com o texto ainda como
children. Mesma prop usada por `TooltipTrigger`/`PopoverTrigger` no resto do codebase.

Mapeamento de erro do `PATCH /api/user/me` em `screens/account/ProfileSection.tsx`, para
reusar nos outros blocos: **409** -> mensagem no campo de e-mail; **400** -> o corpo do
`GlobalExceptionHandler` e `{ message, errors: { <campo>: <mensagem> } }`, entao da para
jogar cada mensagem no seu campo (`types/user.ts` espelha esse shape); **429** -> o limite
e por usuario (`app.rate-limit.profile-update`, 10/h), mensagem geral. Nenhum ramo engole a
excecao — o fallback faz `console.error` com contexto (anti-pattern #4).

### 401 de "senha errada" nao pode passar pelo interceptor de refresh

O interceptor de `lib/api.ts` trata **todo** 401 como sessao expirada: renova e repete a
chamada; se o segundo 401 vier, manda o usuario para `/login`. Isso e correto para leitura,
e **errado** para endpoint que recebe senha no corpo — ali o 401 significa "essa nao e a sua
senha", e o comportamento padrao expulsaria a pessoa do formulario em vez de mostrar o erro.
Por isso existe `isPasswordChallenge(config)`: uma lista curta de `metodo + url`
(`PUT /api/auth/password`, `DELETE /api/user/me`) cujo 401 e rejeitado **cru**, sem refresh e
sem redirect. **Todo endpoint novo que valide senha no corpo precisa entrar nessa lista** —
senao a AC de "erro visivel que nao fecha o dialogo" e impossivel de cumprir na tela.

### Excluir a conta: `deleteAccount` + `clearSession`, nessa ordem

`useAuthStore.deleteAccount({ password })` (US-012) manda `DELETE /api/user/me` com o corpo
em `{ data }` (axios nao aceita body posicional no `delete`) e **nao** mexe no estado local —
mesma divisao do `changePassword`: a tela mostra o desfecho, espera, e so entao chama
`clearSession()` + `navigate("/", { replace: true })` no mesmo handler. Limpar antes
desmontaria a tela pelo `ProtectedRoute` e engoliria a mensagem. Erros do endpoint:
**401** senha errada (fica no dialogo), **400** validacao, **429** limite de 3/h por usuario
(`app.rate-limit.account-delete`).

### O aviso de vinculo desfeito nasce do cache, nao de uma flag

O ex-parceiro descobre a dissolucao em `/join` (US-013) por um sinal que ja existia: o
`couple` do cache de UI dizia que havia um casal **com parceiro** e o `GET /api/auth/me`
voltou sem casal. Esse `if` mora dentro do `loadCurrentUser` do `useAuthStore` (o unico
ponto onde o "antes" e o "depois" coexistem, porque o `persistSession` sobrescreve o cache
na linha seguinte) e acende `bondDissolved`, um booleano **so em memoria** —
**nao criar flag paralela em `localStorage`**, a AC proibe e ela seria redundante. Tres
consequencias que saem de graca: quem nunca teve casal nao tem cache e nao ve nada; quem
**fez** a dissolucao ja zerou o `couple` no `dissolveCouple`; e o aviso nao sobrevive ao
reload, porque o cache que o dispara ja foi sobrescrito. `joinCouple`/`createCouple`
tambem apagam o booleano — formar casal novo encerra o assunto.

A landing (`/`) e `PublicOnlyRoute`, entao ela so aceita o usuario **depois** do
`clearSession()` — as duas chamadas no mesmo handler sao batidas num render so e o destino
ja resolve com `isAuthenticated: false`.
