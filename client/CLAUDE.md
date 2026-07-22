# Frontend (client/)

React + TypeScript + Vite, Tailwind v4 (CSS-based config in `src/index.css`, no `tailwind.config.*`), Shadcn UI primitives under `src/components/ui`.

## Conventions

- Screens live in `src/screens/*.tsx`, route wiring in `src/App.tsx`, nav links in `src/components/Header.tsx`'s `NAV_ITEMS`.
- Dark theme prototype colors are inlined via Tailwind arbitrary values (`bg-[#161513]`, `border-[rgba(255,255,255,.07)]`, etc.) rather than theme tokens — match this pattern for new screens instead of introducing new design tokens, since the prototype in `docs/design/claude-design-project/Sessao a Dois.dc.html` is the source of truth for exact colors/spacing.
- `font-display` (Bricolage Grotesque) for headings/big numbers, `font-auth-body` (DM Sans) for body text — both defined in `src/index.css`.
- API calls go through `src/lib/api.ts` (`api.get/post(...)`), which attaches the JWT and redirects to `/login` on 401. Response DTO shapes are hand-mirrored as TS interfaces under `src/types/*.ts` (e.g. `types/tracking.ts`, `types/stats.ts`) — keep them in sync with the backend records manually, there's no codegen.
- Routes requiring a couple use the `RequireCouple` guard (`src/routes/guards.tsx`); routes requiring auth use `ProtectedRoute`.

## Sandbox gotcha: Vite dev server / build can't start

In this sandbox, `npm run dev` (and `vite build`) fails with `Cannot find native binding` from `rolldown` (vite's native bundler) — npm doesn't hoist/link the platform-specific `@rolldown/binding-*` optional dependency correctly, and `node_modules/rolldown/node_modules/@rolldown/` is left as an empty dir that shadows the real one at the top level. Installing the matching-version binding package (`npm i @rolldown/binding-linux-arm64-gnu@<version from node_modules/rolldown/package.json> --no-save`) and removing that empty shadow dir did NOT fix it in this environment — still worth trying first since it's cheap, but don't assume it will work. `tsc --noEmit` / `tsc -b` and `eslint` both work fine and were used to verify frontend changes instead. Flag this explicitly rather than claiming the dev server or a browser check actually ran.
