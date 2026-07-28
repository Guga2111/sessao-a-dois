# Frontend (client/)

React + TypeScript + Vite, Tailwind v4 (CSS-based config in `src/index.css`, no `tailwind.config.*`), Shadcn UI primitives under `src/components/ui`.

## Conventions

- Screens live in `src/screens/*.tsx`, route wiring in `src/App.tsx`, nav links in `src/components/Header.tsx`'s `NAV_ITEMS`.
- Dark theme prototype colors are inlined via Tailwind arbitrary values (`bg-[#161513]`, `border-[rgba(255,255,255,.07)]`, etc.) rather than theme tokens — match this pattern for new screens instead of introducing new design tokens, since the prototype in `docs/design/claude-design-project/Sessao a Dois.dc.html` is the source of truth for exact colors/spacing.
- `font-display` (Bricolage Grotesque) for headings/big numbers, `font-auth-body` (DM Sans) for body text — both defined in `src/index.css`.
- API calls go through `src/lib/api.ts` (`api.get/post(...)`), which attaches the JWT and redirects to `/login` on 401. Response DTO shapes are hand-mirrored as TS interfaces under `src/types/*.ts` (e.g. `types/tracking.ts`, `types/stats.ts`) — keep them in sync with the backend records manually, there's no codegen.
- Routes requiring a couple use the `RequireCouple` guard (`src/routes/guards.tsx`); routes requiring auth use `ProtectedRoute`.

## Lint gotcha: resetting state on route/prop change

The `eslint-plugin-react-hooks` config here forbids calling `setState` synchronously inside a `useEffect` body (`react-hooks/set-state-in-effect`) and forbids reading/writing `ref.current` during render (`react-hooks/refs`). To reset a subtree's local state when something external changes (e.g. closing a menu on route navigation), extract the stateful part into its own subcomponent and mount it with `key={someChangingValue}` from the parent — remounting resets `useState` without effects or refs. See `src/components/Header.tsx`'s `MobileNav` for an example.

## Modal patterns

Two structural patterns exist for modals/dialogs, don't assume they're all the same:
- shadcn/base-ui `Dialog` (currently only `TitleModal.tsx`) — width/height controlled via the `className` prop on `DialogContent` (`src/components/ui/dialog.tsx`), which already centers the popup itself.
- Hand-rolled `fixed inset-0` backdrop `<div>` with a plain inner `<div>` (`MediaDetailModal`, `PendingDetailModal`, `ReviewModal`, `WatchModal`, `DeleteTrackDialog`) — has its own click-outside/Escape-key handling; width/height go directly on the inner div.

For mobile-responsive sizing on either pattern, use `w-[calc(100vw-32px)] max-w-[calc(100vw-32px)]` as the mobile default and restore the desktop fixed width at `sm:` (e.g. `sm:w-full sm:max-w-[420px]`), plus `max-h-[90svh] overflow-y-auto` for vertical overflow — unless the modal already has its own internal scroll region (like `TitleModal`'s body div), in which case don't double up scrolling on the outer container.

A shadcn `Dialog` that a component opens on behalf of something else (e.g. `RatingRequestDialog`, opened from `NotificationDropdown` when a notification is clicked) must be rendered as a **sibling** of any `Popover`/`PopoverContent` in the same parent, not nested inside `<Popover>` — base-ui's `Popover.Root` only expects `Trigger`/`Content` children. Wrap the parent's return in a Fragment (`<>...</>`) to place them side by side. Same rule applies when a list-item component (e.g. `MediaCard`) has an outer clickable `<div onClick={...}>` wrapping the whole card and needs to open its own `Dialog`: render the `Dialog` as a sibling of that div (Fragment), not a child — Portal-rendered content still bubbles synthetic clicks through the React tree, so a `Dialog` nested inside the clickable div would trigger the card's `onClick` on every click inside the dialog.

## Sandbox gotcha: no network access for `npx shadcn add`

This sandbox has no route to `ui.shadcn.com` (`npx shadcn add <component>` fails with `EHOSTUNREACH`), so any shadcn primitive not already under `src/components/ui` (e.g. `select.tsx` added for US-008) must be hand-written against the installed `@base-ui/react` primitive instead of fetched. Follow the existing file for the same family (`popover.tsx` for portal/positioner/popup-based components, `collapsible.tsx`/`button.tsx` for simpler ones): `data-slot` on every part, `cn(...)` for class merging, theme tokens (`bg-popover`, `border-border`, `bg-primary`, etc. from `src/index.css`) as the component's own default styling, and let screens override those tokens with the prototype's arbitrary hex values via `className` (same pattern `NotificationDropdown.tsx` already uses on `PopoverContent`). Check `node_modules/@base-ui/react/<primitive>/**/*.d.ts` for the exact part names/props (e.g. `Select.Root`/`Trigger`/`Value`/`Icon`/`Portal`/`Positioner`/`Popup`/`Item`/`ItemText`/`ItemIndicator`) before writing the wrapper.

## Toggle/ToggleGroup (multi-select chips)

`src/components/ui/toggle-group.tsx` wraps `@base-ui/react/toggle` and `@base-ui/react/toggle-group` the same way `select.tsx` wraps `@base-ui/react/select` (see the sandbox gotcha below — not fetched via `npx shadcn add`). Both `Toggle`/`ToggleGroup` are exported as plain function components (not `.Root`-namespaced like `Select`/`Popover`). `ToggleGroup`'s `multiple` prop enables multi-select chip groups (each `ToggleGroupItem` needs a unique string `value`); `onValueChange` receives `(value: string[], eventDetails)`, so a bare `setState` setter can be passed directly since JS/TS allow assigning a callback with fewer params. Active/pressed state styling is driven by the `data-pressed` attribute (`data-[pressed]:...` Tailwind variant), matching the `MatchScreen.tsx` filter chips (Data de lancamento / Classificacao / Generos).

## Range slider (two thumbs)

`src/components/ui/slider.tsx` wraps `@base-ui/react/slider` (`Slider.Root/Control/Track/Indicator/Thumb`, namespace import `Slider as SliderPrimitive`, same pattern as `select.tsx`) — not fetched via `npx shadcn add` (see sandbox gotcha below). For a two-thumb range, pass `value`/`onValueChange` as a `[number, number]` tuple and render two `<SliderThumb index={0} />`/`<SliderThumb index={1} />` inside one `<SliderControl>`; `minStepsBetweenValues={1}` stops the thumbs from crossing. See `MatchScreen.tsx`'s Nota TMDB / Duracao filters (US-010) for the reference usage.

## Sandbox gotcha: Vite dev server / build can't start

`npm run dev` (and `vite build`) intermittently fails in this sandbox with `Cannot find module './<name>.linux-arm64-gnu.node'` — a plain `npm install` doesn't always pull the optional arm64 native bindings for every native-dependent package. As of 2026-07-25, the fix that worked was installing the three missing optional packages directly: `npm install @rolldown/binding-linux-arm64-gnu lightningcss-linux-arm64-gnu @tailwindcss/oxide-linux-arm64-gnu` (rolldown → lightningcss → tailwind oxide failed in that order, one at a time, each surfaced by re-running `npm run dev` after the previous fix). After all three are present, `npm run dev` starts cleanly (`VITE ready`). No browser (Chromium/Playwright) is installed in this sandbox and there's no sudo to install its system deps (`libnspr4`, `libnss3`, etc.), so even with the dev server running, only static code review / `tsc` / `eslint` are available — no real screenshots or rendered-page checks. Note the API (Spring Boot) still has no JDK/Maven in this sandbox, so full end-to-end (frontend hitting a live backend) can't be verified here either.
