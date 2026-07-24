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

## Sandbox gotcha: Vite dev server / build can't start

Previously `npm run dev` (and `vite build`) failed in this sandbox with `Cannot find native binding` from `rolldown`. As of 2026-07-23 this is no longer reproducing — `npm run dev` starts cleanly (`VITE v8.1.5 ready`) after a plain `npm install`. If it fails again for you, the old workaround was installing the matching-version `@rolldown/binding-linux-arm64-gnu` package and removing an empty shadow dir at `node_modules/rolldown/node_modules/@rolldown/`, though that never actually fixed it before — don't assume it will. Note the API (Spring Boot) still has no JDK/Maven in this sandbox, so full end-to-end (frontend hitting a live backend) still can't be verified here even though the dev server itself now runs — only static rendering/console errors can be checked, not real API flows. `tsc --noEmit` and `eslint` remain the fast, reliable checks.
