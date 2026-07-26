# PRD: Landing Page Pública — "Sessão·a·Dois"

## 1. Introdução / Visão Geral

Hoje o app "Sessão·a·Dois" abre direto em rotas autenticadas: um visitante anônimo que
acessa `/` cai no `ProtectedRoute` e é jogado para `/login`, sem nunca entender o que o
produto faz nem por que criar uma conta. Não existe nenhuma porta de entrada pública que
apresente a proposta de valor.

Esta feature cria uma **landing page pública** (rota `/`) — uma página estática de
marketing, sem back-end novo — cujo único objetivo é converter o visitante anônimo em um
clique em **"Criar conta"** (`/register`) ou **"Entrar"** (`/login`). A página herda o
sistema visual do protótipo (tema escuro, acentos amarelo/laranja/verde, tipografia
Bricolage Grotesque + DM Sans, cards com raio 18–24px, glows sutis, ícones Lucide) e
comunica os diferenciais do produto: lista compartilhada, Match em tempo real, notas &
opiniões individuais e o Dashboard do casal.

Como efeito colateral necessário, o Hub autenticado — que hoje mora em `/` — é **migrado
para `/hub`**, liberando a raiz do domínio para a landing.

**Tom de voz:** pt-BR, caloroso, íntimo e a dois — nunca corporativo. Casal de referência:
"Ana & Léo".

## 2. Goals (Objetivos)

- Dar ao visitante anônimo uma página pública em `/` que renderiza **sem autenticação**.
- Comunicar a proposta de valor central (lista compartilhada + Match) e os 4 diferenciais
  em uma única página rolável.
- Levar o visitante a **`/register`** (CTA primário) ou **`/login`** (CTA secundário) a
  partir de múltiplos pontos da página (header, hero, CTA final).
- Manter 100% de coerência visual com o design system do protótipo/app.
- Ser 100% responsiva (mobile-first, ~360px → desktop largo) e acessível (contraste,
  teclado, `alt`/`aria`, `prefers-reduced-motion`).
- Migrar o Hub de `/` para `/hub` sem regressão para usuários autenticados.
- Não introduzir back-end, endpoints ou dependências desnecessárias.

## 3. User Stories

> Ordem de implementação sugerida: US-001 (fontes) → US-002 (roteamento/migração do Hub)
> → US-003 (scaffold) → US-004..US-012 (seções) → US-013 (a11y/motion) → US-014 (QA
> responsivo). Cada story cabe em uma sessão focada.

### US-001: Adicionar as fontes reais do protótipo (Bricolage Grotesque + DM Sans)
**Description:** Como desenvolvedor, quero as fontes do design system instaladas de fato,
para que `font-display` e `font-auth-body` deixem de cair no fallback (Instrument Sans) e a
landing use a tipografia exata do protótipo.

**Acceptance Criteria:**
- [ ] Adicionar `@fontsource/bricolage-grotesque` e `@fontsource/dm-sans` ao
      `client/package.json` (self-hosted, seguindo o padrão já usado com
      `@fontsource-variable/instrument-sans`).
- [ ] Importar os pesos usados no `client/src/index.css`: Bricolage Grotesque 600/700/800 e
      DM Sans 400/500/600/700.
- [ ] `.font-display` renderiza em Bricolage Grotesque e `.font-auth-body` em DM Sans
      (verificável no inspetor: computed `font-family` resolve para a fonte instalada, não o
      fallback).
- [ ] Nenhum `<link>` de Google Fonts adicionado ao `index.html` (mantém o padrão
      self-hosted, sem requisição externa em runtime).
- [ ] `tsc --noEmit` e `eslint` passam.
- [ ] Verificar no navegador usando a skill dev-browser.

### US-002: Migrar o Hub de `/` para `/hub` e liberar a raiz
**Description:** Como usuário autenticado, quero que meu Hub continue funcionando após a
raiz `/` virar pública, acessando-o em `/hub` sem quebras de navegação.

**Acceptance Criteria:**
- [ ] Em `client/src/App.tsx`, a rota do `HubScreen` passa de `path="/"` para `path="/hub"`,
      mantendo os guards `ProtectedRoute` + `RequireCouple`.
- [ ] `client/src/components/Header.tsx` (`NAV_ITEMS`) e qualquer link/`Navigate` que aponte
      para `/` como Hub passa a apontar para `/hub` (buscar todas as referências: guards,
      `api.ts` redirect pós-login, telas de auth, `PublicOnlyRoute`).
- [ ] `PublicOnlyRoute` (`src/routes/guards.tsx`) redireciona usuário autenticado **com
      casal** para `/hub` (antes `/`); sem casal continua indo para `/join`.
- [ ] O catch-all `*` continua levando a um destino válido (Hub `/hub` para autenticados; a
      raiz `/` agora é pública — ver US-003 para a lógica de `/`).
- [ ] Fluxo pós-login/registro leva o usuário autenticado+casal ao `/hub`.
- [ ] Nenhum link morto para `/` como Hub permanece no código.
- [ ] `tsc --noEmit` e `eslint` passam.
- [ ] Verificar no navegador usando a skill dev-browser (login → cai em `/hub`; navegação do
      Header funciona).

### US-003: Rota pública `/` com lógica de sessão (LandingScreen + guard)
**Description:** Como visitante, quero que `/` mostre a landing quando não estou logado, e
como usuário logado quero ser levado direto ao app — sem ver a página de marketing.

**Acceptance Criteria:**
- [ ] Nova rota pública `path="/"` renderiza `LandingScreen`, **fora** de `ProtectedRoute`
      (renderiza sem JWT).
- [ ] Visitante **sem sessão** → vê a `LandingScreen`.
- [ ] Usuário **autenticado com JWT válido** (e casal) que acessa `/` → redirecionado para
      `/hub`; autenticado sem casal → `/join`.
- [ ] Usuário com **JWT presente porém inválido/expirado** → sessão é limpa (logout) e
      redirecionado para `/login`. (Reaproveitar o mecanismo existente de 401 do
      `src/lib/api.ts`/`useAuthStore`; não inventar validação nova de token no cliente além
      do que já existe.)
- [ ] A landing NÃO faz nenhuma chamada de API de conteúdo (é estática).
- [ ] `tsc --noEmit` e `eslint` passam.
- [ ] Verificar no navegador usando a skill dev-browser (anônimo vê landing; logado é
      redirecionado; token inválido cai em /login).

### US-004: Scaffold do `LandingScreen` + `LandingHeader` público
**Description:** Como visitante, quero um header público fixo com a marca e CTAs sempre
acessíveis, para poder criar conta ou entrar a qualquer momento da rolagem.

**Acceptance Criteria:**
- [ ] Criar `client/src/screens/LandingScreen.tsx` como composição de seções isoladas em
      `client/src/components/landing/`.
- [ ] Criar `LandingHeader` com: logo "Sessão·a·Dois" (mesmo estilo do protótipo, ícone
      Lucide — sem emoji de coração), links de âncora para as seções (ex.: Como funciona,
      Recursos, FAQ) e botões **"Entrar"** (link `/login`) e **"Criar conta"** (link
      `/register`).
- [ ] Header é `sticky top-0` com fundo translúcido + `backdrop-blur` e borda inferior
      `1px rgba(255,255,255,.07)`, como o protótipo.
- [ ] Em telas estreitas (~360px), o header se adapta: âncoras colapsam/escondem, mas o CTA
      "Criar conta" permanece acessível (ex.: menu ou botão compacto).
- [ ] Links de navegação usam `<Link>`/`<a href="#ancora">`; navegáveis por teclado com
      `:focus-visible` visível.
- [ ] `tsc --noEmit` e `eslint` passam.
- [ ] Verificar no navegador usando a skill dev-browser.

### US-005: Seção Hero (`LandingHero`)
**Description:** Como visitante, quero uma headline emocional que me faça sentir que este app
é "para nós dois", com um preview visual da lista do casal.

**Acceptance Criteria:**
- [ ] `LandingHero` com: rótulo/eyebrow uppercase, headline (`font-display`, 700/800),
      subtítulo (`font-auth-body`, cor `#a6a39a`), CTA primário **"Começar a dois"** →
      `/register` e CTA secundário (ex.: "Já temos conta" → `/login`).
- [ ] Fundo com `radial-gradient` sutil (mesmos gradientes do protótipo) sobre `#09090a`.
- [ ] Um mock/preview visual reaproveitando o estilo dos cards de mídia (`#161513`, raio
      18–24px, borda `1px rgba(255,255,255,.07)`, badges/pílulas) — pode ser card estático.
- [ ] CTA primário usa o acento `#ffcb2b` com glow discreto (`box-shadow`), texto escuro.
- [ ] Layout fluido com `clamp()`; sem quebra/overflow horizontal entre 360px e desktop.
- [ ] `tsc --noEmit` e `eslint` passam.
- [ ] Verificar no navegador usando a skill dev-browser.

### US-006: Seção "Como funciona" (`HowItWorks`, 3 passos)
**Description:** Como visitante, quero entender em 3 passos como começar, para reduzir a
fricção de criar conta.

**Acceptance Criteria:**
- [ ] `HowItWorks` com 3 passos numerados: (1) Criar conta → (2) Parear com o código de
      convite → (3) Assistir e avaliar juntos.
- [ ] Cada passo tem ícone Lucide, título curto e microcopy; layout em grid responsivo
      (`auto-fit`/`clamp()`), empilhando no mobile.
- [ ] Nenhum emoji usado como ícone.
- [ ] `tsc --noEmit` e `eslint` passam.
- [ ] Verificar no navegador usando a skill dev-browser.

### US-007: Grid de recursos (`FeatureGrid`, 4 destaques)
**Description:** Como visitante, quero ver os diferenciais do produto de forma escaneável,
para me convencer do valor.

**Acceptance Criteria:**
- [ ] `FeatureGrid` com 4 cards: **Lista compartilhada** (Assistindo / Queremos Ver / Já
      Vimos), **Match em tempo real**, **Notas & opiniões individuais**, **Dashboard do
      casal**.
- [ ] Cada card: ícone Lucide + título (`font-display`) + microcopy (`font-auth-body`),
      estilo de card do design system (superfície `#161513`, borda sutil, raio 18–24px).
- [ ] Grid `grid-template-columns: repeat(auto-fit, minmax(...))` fluido; 1 coluna no
      mobile, múltiplas no desktop.
- [ ] `tsc --noEmit` e `eslint` passam.
- [ ] Verificar no navegador usando a skill dev-browser.

### US-008: Prova visual do Match (`MatchShowcase`)
**Description:** Como visitante, quero ver a mágica do "os dois curtiram → deu Match",
porque é o diferencial central do produto.

**Acceptance Criteria:**
- [ ] `MatchShowcase` mostra dois cards "curtidos" e uma micro-animação **em loop CSS** que
      culmina no momento de Match ("Deu Match!") e no título entrando na pílula "Queremos
      Ver".
- [ ] Animação é puramente visual (CSS/estado interno de UI), **sem API, sem WebSocket, sem
      back-end**, sem estado global (`useMatchStore` não é tocado).
- [ ] `prefers-reduced-motion: reduce` → sem loop de animação; renderiza o **estado final**
      (match já concluído) de forma estática.
- [ ] Ícones Lucide (coração/sparkles), nenhum emoji.
- [ ] `tsc --noEmit` e `eslint` passam.
- [ ] Verificar no navegador usando a skill dev-browser (rodar com e sem
      `prefers-reduced-motion`).

### US-009: Prévia do Dashboard (`DashboardPreview`)
**Description:** Como visitante, quero um teaser das estatísticas do casal, para desejar ter
o meu próprio dashboard.

**Acceptance Criteria:**
- [ ] `DashboardPreview` reproduz o estilo do `DashboardScreen` real com **dados fixos/fake**
      (ex.: horas juntos, filmes vs séries, gêneros favoritos).
- [ ] Barras/KPIs animam suavemente ao entrar na viewport (ex.: `IntersectionObserver` ou
      animação CSS on-scroll); respeitam `prefers-reduced-motion` (estado final estático).
- [ ] Nenhuma chamada a `/api/tracking/stats` nem a qualquer endpoint.
- [ ] `tsc --noEmit` e `eslint` passam.
- [ ] Verificar no navegador usando a skill dev-browser.

### US-010: Depoimentos (`Testimonials`)
**Description:** Como visitante, quero ver casais falando bem do app, para ganhar confiança.

**Acceptance Criteria:**
- [ ] `Testimonials` com 2–3 depoimentos **claramente ilustrativos** no tom "Ana & Léo"
      (conteúdo fictício de marketing, não avaliações reais de terceiros).
- [ ] Cada depoimento: citação, nome do casal e avatar estilizado (mesmo estilo de avatar
      duplo do protótipo — sem foto real).
- [ ] Layout responsivo (carrossel simples ou grid que empilha no mobile); sem dependência
      nova de carrossel — usar primitivos existentes/CSS.
- [ ] `tsc --noEmit` e `eslint` passam.
- [ ] Verificar no navegador usando a skill dev-browser.

### US-011: FAQ (`FaqSection`)
**Description:** Como visitante com dúvidas, quero respostas rápidas às objeções comuns,
para remover barreiras antes de criar conta.

**Acceptance Criteria:**
- [ ] `FaqSection` com 4–6 perguntas (ex.: "Preciso pagar?", "Como funciona o código de
      convite?", "De onde vêm os dados dos filmes?" → TMDB pt-BR, "Meus dados são
      privados?").
- [ ] Itens expansíveis/colapsáveis reaproveitando `src/components/ui/collapsible.tsx`
      (já existe), com acessibilidade de accordion (`aria-expanded`, foco por teclado).
- [ ] Nenhuma dependência nova introduzida.
- [ ] `tsc --noEmit` e `eslint` passam.
- [ ] Verificar no navegador usando a skill dev-browser.

### US-012: CTA final (`FinalCta`) + Rodapé (`LandingFooter`)
**Description:** Como visitante que rolou até o fim, quero um último empurrão para criar
conta e um rodapé com a marca.

**Acceptance Criteria:**
- [ ] `FinalCta`: bloco de fechamento com headline emocional + CTA primário "Começar a dois"
      (`/register`) e link secundário para `/login`.
- [ ] `LandingFooter`: logo/marca "Sessão·a·Dois", links de âncora e para `/login` /
      `/register`. Sem links quebrados ou para páginas inexistentes.
- [ ] Ambos seguem o design system (cores, tipografia, glows discretos).
- [ ] `tsc --noEmit` e `eslint` passam.
- [ ] Verificar no navegador usando a skill dev-browser.

### US-013: Acessibilidade e `prefers-reduced-motion` (passada final)
**Description:** Como usuário de teclado/leitor de tela ou sensível a movimento, quero
navegar e ler a landing confortavelmente.

**Acceptance Criteria:**
- [ ] Toda a página é navegável por teclado; ordem de foco lógica; `:focus-visible` visível
      em todos os interativos (links, botões, accordion).
- [ ] Elementos interativos e ícones informativos têm `alt`/`aria-label` adequados; ícones
      puramente decorativos são `aria-hidden`.
- [ ] Contraste de texto atende WCAG AA no tema escuro (primário `#f6f4ec`, secundário
      `#a6a39a` — validar em superfícies).
- [ ] Todas as animações (hero, match, dashboard, hovers) respeitam
      `prefers-reduced-motion: reduce`.
- [ ] Uma landmark structure correta (`<header>`, `<main>`, `<footer>`, seções com heading).
- [ ] `tsc --noEmit` e `eslint` passam.
- [ ] Verificar no navegador usando a skill dev-browser (teclado + reduced-motion).

### US-014: QA responsivo (~360px → desktop largo)
**Description:** Como visitante em qualquer dispositivo, quero a página sem quebras de
layout.

**Acceptance Criteria:**
- [ ] Sem overflow horizontal nem sobreposição em 360px, 768px, 1024px e ≥1440px.
- [ ] Header, hero, grids e demos permanecem legíveis e alinhados nos breakpoints.
- [ ] Imagens/mocks não estouram seus containers; tipografia escala via `clamp()`.
- [ ] `tsc --noEmit` e `eslint` passam.
- [ ] Verificar no navegador usando a skill dev-browser nos tamanhos acima.

## 4. Functional Requirements

- **FR-1:** O sistema deve servir `LandingScreen` na rota pública `/`, renderizável sem JWT.
- **FR-2:** O Hub autenticado deve ser movido de `/` para `/hub`, mantendo os guards
  `ProtectedRoute` + `RequireCouple`.
- **FR-3:** Todas as referências internas a `/` como Hub (Header `NAV_ITEMS`, guards,
  redirects de auth/`api.ts`, `PublicOnlyRoute`, catch-all) devem apontar para `/hub`.
- **FR-4:** Ao acessar `/`, se o usuário estiver autenticado com JWT válido e casal, o
  sistema deve redirecioná-lo para `/hub`; se autenticado sem casal, para `/join`.
- **FR-5:** Ao acessar `/` com JWT presente porém inválido/expirado, o sistema deve limpar a
  sessão e redirecionar para `/login` (reaproveitando o tratamento de 401 existente).
- **FR-6:** A landing deve oferecer navegação para `/register` (CTA primário) e `/login`
  (CTA secundário) a partir de header, hero, CTA final e rodapé.
- **FR-7:** A landing deve ser composta por seções isoladas em `src/components/landing/`:
  `LandingHeader`, `LandingHero`, `HowItWorks`, `FeatureGrid`, `MatchShowcase`,
  `DashboardPreview`, `Testimonials`, `FaqSection`, `FinalCta`, `LandingFooter`.
- **FR-8:** As seções `MatchShowcase` e `DashboardPreview` devem usar mocks estáticos com
  micro-animações CSS/UI, sem qualquer chamada de API, WebSocket ou estado global.
- **FR-9:** As fontes Bricolage Grotesque (600/700/800) e DM Sans (400/500/600/700) devem
  ser instaladas via `@fontsource` e importadas no `index.css`.
- **FR-10:** Todos os ícones devem vir de `lucide-react`; nenhum emoji pode ser usado como
  ícone.
- **FR-11:** Todas as animações devem respeitar `prefers-reduced-motion: reduce`.
- **FR-12:** A página deve ser responsiva de ~360px a desktop largo, usando `clamp()` e
  grids `auto-fit`, sem overflow horizontal.
- **FR-13:** O `FaqSection` deve reutilizar `src/components/ui/collapsible.tsx`; nenhuma
  dependência nova pode ser adicionada além das fontes `@fontsource` da FR-9.

## 5. Non-Goals (Fora de Escopo)

- Nenhum back-end, endpoint, migração de banco ou entidade nova.
- Nenhuma coleta de e-mail/newsletter, formulário de contato ou analytics/tracking de
  terceiros.
- Nenhuma internacionalização — a página é **somente pt-BR**.
- Nenhum conteúdo dinâmico/CMS: textos e mocks são estáticos no código.
- Nenhuma integração real com TMDB, WebSocket, `useMatchStore` ou `useAuthStore` além do
  redirect de sessão descrito.
- Nenhuma página legal (Termos/Privacidade) real — links de rodapé podem ser âncoras ou
  omitidos, não criar rotas novas fora de `/`.
- Depoimentos e estatísticas são ilustrativos; não representam usuários/dados reais.
- Sem modo claro (light theme): a landing é dark, coerente com o app.

## 6. Design Considerations

- **Fonte da verdade visual:** `docs/design/claude-design-project/Sessao a Dois.dc.html`
  (cores, espaçamentos, gradientes, estilos de card/badge/avatar exatos).
- **Cores:** fundo `#09090a`; superfícies/cards `#161513`; inputs `#201e18`; acentos
  `#ffcb2b` (amarelo/ação primária), `#ff9e2c` (laranja), `#3ddc97` (verde/sucesso); texto
  `#f6f4ec` (primário), `#a6a39a` (secundário), `#8a877c` (rótulos uppercase). Seguir o
  padrão do projeto de inlinear via **Tailwind arbitrary values** (`bg-[#161513]`,
  `border-[rgba(255,255,255,.07)]`) em vez de criar tokens novos (ver `client/CLAUDE.md`).
- **Tipografia:** `.font-display` (Bricolage Grotesque) para títulos/números grandes;
  `.font-auth-body` (DM Sans) para corpo.
- **Linguagem visual:** `radial-gradient` sutil no fundo, cards raio 18–24px, bordas
  `1px rgba(255,255,255,.07)`, pílulas/badges, glows discretos (`box-shadow` com o amarelo),
  avatar duplo do casal (estilo do protótipo).
- **Reuso:** primitivos de `src/components/ui/` (`button`, `card`, `collapsible`, `avatar`)
  quando fizer sentido; ícones de `lucide-react`.
- **Componentização:** `LandingScreen` é só composição; cada seção é um componente próprio,
  tipado, em `src/components/landing/`.

## 7. Technical Considerations

- **Roteamento:** React Router via `<Routes>` em `src/App.tsx` (não `createBrowserRouter`).
  A rota `/` da landing fica fora de `ProtectedRoute`. Cuidado com o catch-all `*` e com
  `PublicOnlyRoute` (que hoje manda logados para `/`).
- **Sessão/redirect em `/`:** reaproveitar `useAuthStore` (`isAuthenticated`, `couple`) e o
  tratamento de 401 já existente em `src/lib/api.ts` para o caso de JWT inválido — não
  implementar verificação de assinatura de token no cliente.
- **Lint gotchas (ver `client/CLAUDE.md`):** proibido `setState` síncrono dentro de
  `useEffect` (`react-hooks/set-state-in-effect`) e ler/escrever `ref.current` durante o
  render (`react-hooks/refs`). Para animações on-scroll (`DashboardPreview`), preferir
  `IntersectionObserver` dentro de `useEffect` com atualização de estado no callback (não
  sincronamente no corpo do effect), ou animação puramente CSS.
- **Skill obrigatória:** qualquer código em `client/` deve usar a skill `frontend-design`
  antes de proceder (CLAUDE.md do projeto).
- **Verificação no sandbox:** `npm run dev` sobe; `tsc --noEmit` e `eslint` são os checks
  rápidos e confiáveis. A landing é estática, então dá para verificar renderização completa
  no navegador (não depende do backend Spring, que não roda no sandbox).
- **Sem novas deps** além de `@fontsource/bricolage-grotesque` e `@fontsource/dm-sans`.

## 8. Success Metrics

- Visitante anônimo consegue, a partir de `/`, chegar em `/register` ou `/login` em **1
  clique** de qualquer ponto principal (header, hero, CTA final, rodapé).
- Zero quebras de layout entre 360px e ≥1440px.
- `tsc --noEmit` e `eslint` sem erros; nenhuma dependência ou endpoint novo além do previsto.
- Usuários autenticados existentes não sofrem regressão: login leva a `/hub` e a navegação
  do Header funciona.
- Auditoria de acessibilidade (teclado + contraste + reduced-motion) sem bloqueios.

## 9. Open Questions

- Os links "legais" do rodapé (Termos/Privacidade) devem apontar para âncoras placeholder,
  ser ocultados, ou já se planeja criar essas páginas depois? (Assumido: âncoras/omitidos —
  não criar rotas novas neste escopo.)
- Copy final (headline, microcopy, perguntas do FAQ, texto dos depoimentos): o texto deste
  PRD é sugestão; há aprovação de copy do time ou seguimos com a redação proposta no tom
  "Ana & Léo"?
- Existe favicon/OpenGraph/meta tags de SEO a incluir agora, ou fica para uma etapa
  posterior? (Assumido: fora de escopo por ora.)
