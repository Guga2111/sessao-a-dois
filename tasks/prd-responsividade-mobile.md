# PRD: Responsividade Mobile — Carrossel e Menu Hamburguer

## Introduction

A aplicação "Sessão a Dois" é atualmente otimizada para desktop. Este PRD cobre a adaptação completa para uso mobile, com foco em dois comportamentos distintos:

1. **Header**: menu hamburguer no mobile (< 768px) substituindo a barra de navegação inline.
2. **HubScreen — seções de títulos**: no mobile, as listas em grid viram carrosséis horizontais com "peek" do próximo card e paginação automática ao chegar no último item.

O restante da aplicação (telas de autenticação, Match, Dashboard e modais) recebe ajustes de padding/tamanho para funcionar corretamente em telas pequenas.

---

## Goals

- Toda a aplicação deve ser usável em celulares comuns (≥ 320px de largura) sem scroll horizontal indesejado ou elementos cortados.
- As seções do Hub Principal devem ser navegáveis via swipe horizontal no mobile.
- A paginação de títulos no mobile deve acontecer automaticamente ao chegar no último card, sem botão.
- O header deve exibir um menu hamburguer no mobile, mantendo o logo visível.
- O breakpoint entre layout mobile e desktop é `768px` (equivalente ao `md:` do Tailwind).

---

## User Stories

### US-001: Header com menu hamburguer no mobile
**Description:** As a mobile user, I want a hamburger menu instead of an inline nav bar so the header doesn't overflow or shrink the logo.

**Acceptance Criteria:**
- [ ] Em telas `< 768px`, o `<nav>` com os 3 links de navegação fica oculto e um ícone hamburguer (☰) aparece no lado direito do header
- [ ] Ao clicar no ícone hamburguer, um menu suspenso ou drawer exibe os 3 itens de navegação ("Hub Principal", "Match ♥", "Dashboard")
- [ ] O item ativo recebe destaque visual (mesma cor `#ffcb2b` já usada no desktop)
- [ ] Ao clicar em um item do menu, o menu fecha e a navegação ocorre
- [ ] Em telas `≥ 768px`, o comportamento atual (nav inline) permanece idêntico
- [ ] O bloco de nomes/avatares do casal permanece visível no header mobile (pode ser simplificado para só os avatares)
- [ ] Typecheck passa (`tsc --noEmit`)
- [ ] Verify in browser using dev-browser skill

### US-002: Carrossel horizontal nas seções do Hub (mobile)
**Description:** As a mobile user, I want to swipe through movie/series cards horizontally in each section so I can browse titles comfortably on a small screen.

**Acceptance Criteria:**
- [ ] Em telas `< 768px`, cada seção do Hub exibe os cards em um container com scroll horizontal em vez de grid
- [ ] O carrossel mostra **1 card completo** + **~50% do próximo card visível** (efeito "peek"), indicando que há mais conteúdo
- [ ] O scroll tem `scroll-snap-type: x mandatory` com snap por card (`scroll-snap-align: start`)
- [ ] Os cards têm largura fixa de aproximadamente `72vw` no mobile (ajustar para que o peek do próximo seja natural)
- [ ] O estado collapsible das seções (recolher/expandir) funciona normalmente no mobile
- [ ] Em telas `≥ 768px`, o grid atual permanece inalterado
- [ ] Skeleton de carregamento respeita o layout de carrossel no mobile
- [ ] Estado vazio (sem títulos) continua sendo exibido normalmente
- [ ] Typecheck passa
- [ ] Verify in browser using dev-browser skill

### US-003: Paginação automática no carrossel mobile
**Description:** As a mobile user, I want the carousel to automatically load more titles when I reach the last card so I don't have to tap a button to continue browsing.

**Acceptance Criteria:**
- [ ] Ao chegar no **último card** do carrossel (o 20º, ou o último da página atual), os próximos 20 títulos são buscados automaticamente via API, sem qualquer interação do usuário
- [ ] Os novos cards são **anexados ao carrossel** sem resetar a posição de scroll (o usuário continua de onde estava)
- [ ] Um indicador de carregamento (spinner ou skeleton card) aparece **no final do carrossel** enquanto os dados são buscados
- [ ] Se não houver mais títulos (`items.length >= total`), nenhuma requisição extra é feita
- [ ] O botão "Carregar mais" **não aparece** no layout mobile (apenas no desktop)
- [ ] Em desktop, o botão "Carregar mais" continua funcionando exatamente como antes
- [ ] A detecção de "chegou no último card" usa `IntersectionObserver` apontando para um elemento sentinela no fim do carrossel
- [ ] Typecheck passa
- [ ] Verify in browser using dev-browser skill

### US-004: Responsividade geral — telas e modais
**Description:** As a mobile user, I want all screens and modals to fit my screen without overflow so everything is readable and usable.

**Acceptance Criteria:**
- [ ] `HubScreen`: padding lateral reduzido no mobile (`px-4`), título h1 com `clamp` que já funciona (verificar), botão "Adicionar Título" no topo ocupa largura total em telas muito pequenas (`w-full sm:w-auto`)
- [ ] `MatchScreen`: cards de swipe e botões de ação cabem na viewport sem overflow horizontal
- [ ] `DashboardScreen`: cards de KPI e gráficos empilham verticalmente no mobile
- [ ] `AuthLayout` e páginas de auth (`LoginPage`, `RegisterPage`, `JoinPage`): formulários centralizam corretamente, sem padding excessivo
- [ ] `TitleModal`, `MediaDetailModal`, `ReviewModal`, `WatchModal`, `DeleteTrackDialog`: em mobile, os dialogs ocupam quase a tela inteira (`max-h-[90svh]`, com scroll interno quando necessário) em vez de aparecer como caixas flutuantes pequenas
- [ ] Nenhuma tela exibe scroll horizontal indesejado em viewport de 375px (iPhone SE)
- [ ] Typecheck passa
- [ ] Verify in browser using dev-browser skill

---

## Functional Requirements

- **FR-1:** O breakpoint que separa layout mobile do desktop é `768px` (`md:` no Tailwind). Abaixo desse valor, os layouts alternativos de carrossel e hamburguer se ativam.
- **FR-2:** O `Header` deve renderizar condicionalmente: `<nav>` inline apenas em `md:`+ e ícone hamburguer apenas abaixo de `md:`.
- **FR-3:** O menu hamburguer deve abrir/fechar via estado local em `Header.tsx`. Ao navegar para outra rota, o menu deve fechar automaticamente.
- **FR-4:** No mobile, cada seção do Hub renderiza um `<div>` com `overflow-x: auto`, `display: flex`, `scroll-snap-type: x mandatory` e `gap` entre os cards.
- **FR-5:** Cada card no carrossel mobile recebe `min-width: 72vw` e `scroll-snap-align: start`, garantindo o efeito de peek.
- **FR-6:** O container do carrossel deve ter `padding-right` suficiente (≥ `10vw`) para que o próximo card seja visível sem ser clicável.
- **FR-7:** No mobile, um elemento sentinela (`<div ref={sentinelRef}>`) é renderizado como **último filho** do carrossel. Um `IntersectionObserver` monitora esse elemento; quando ele entra no viewport e `hasMore === true` e `!loadingMore`, `handleLoadMore` é chamado.
- **FR-8:** O elemento sentinela e o `IntersectionObserver` só são ativados no mobile (verificar via `window.innerWidth < 768` ou hook `useIsMobile`). No desktop, o botão existente permanece.
- **FR-9:** O botão "Carregar mais" é renderizado somente quando `window.innerWidth >= 768` (ou equivalente via hook/media query).
- **FR-10:** Ao adicionar novos cards via paginação automática, o scroll do carrossel não deve ser resetado — novos itens são apenas concatenados ao state existente (já funciona assim em `fetchSectionPage`).
- **FR-11:** Em mobile, os `Dialog` (modais) devem ter `className` que inclua `w-full max-w-[calc(100vw-32px)] max-h-[90svh] overflow-y-auto` ou equivalente, substituindo os valores fixos de largura desktop.
- **FR-12:** A barra de navegação hamburguer deve usar `z-index` maior que o header (`z-50`) para ficar por cima do conteúdo ao abrir.

---

## Non-Goals

- Não será criado um app nativo ou PWA — apenas responsividade web.
- Não serão adicionados gestos de swipe programáticos (drag com JS/framer-motion) — o scroll nativo CSS (`overflow-x: auto` + `scroll-snap`) é suficiente.
- Não haverá indicadores de posição (dots) no carrossel — decidido nas perguntas de clarificação.
- Não será alterada a lógica de paginação no desktop.
- Não será implementado lazy loading de imagens ou otimizações de performance além do escopo de paginação já existente.
- Não haverá modo paisagem (landscape) específico — o layout mobile serve para portrait.

---

## Design Considerations

- **Cores e fontes**: permanecem idênticas ao design atual. Nenhum novo token de cor é introduzido.
- **Card no carrossel**: o `MediaCard` existente (`src/components/MediaCard.tsx`) é reutilizado sem modificação de conteúdo — apenas o container muda.
- **Hamburguer**: usar ícone `Menu` do `lucide-react` (já instalado). O dropdown pode ser um `<div>` absoluto simples com `bg-[#161513] border border-white/10 rounded-2xl`, sem dependência de Shadcn extra.
- **Scroll do carrossel**: usar `scrollbar-width: none` e `::-webkit-scrollbar { display: none }` no container para esconder a barra de scroll horizontal.
- **Sentinela de paginação**: elemento `<div className="w-1 shrink-0" />` invisível no final do carrossel — pequeno o suficiente para não interferir no layout.

---

## Technical Considerations

- **Hook `useIsMobile`**: criar `src/lib/useIsMobile.ts` usando `window.matchMedia('(max-width: 767px)')` com listener de resize para retornar `boolean`. Isso evita duplicar lógica em vários componentes.
- **`IntersectionObserver`**: usar `useEffect` + `useRef` no `HubScreen` para cada seção. O observer deve ter `root: null` (viewport) e `threshold: 0.5`.
- **Evitar flash**: o hook `useIsMobile` deve inicializar com `window.matchMedia(...).matches` para não causar flash de layout na primeira renderização.
- **Tailwind v4**: este projeto usa Tailwind v4 com CSS-based config em `src/index.css`. Prefixar classes mobile com `max-md:` (equivalente a `@media (max-width: 767px)`) — verificar se o projeto usa `md:` (mobile-first) ou `max-md:` (desktop-first) para manter consistência com o código existente.
- **`scroll-snap`**: não disponível como classe Tailwind nativa em v4 sem config — usar `style={{ scrollSnapType: 'x mandatory' }}` inline ou adicionar as classes ao CSS global em `index.css`.

---

## Success Metrics

- Nenhum elemento fora do viewport horizontal em telas de 375px de largura.
- O carrossel carrega a próxima página automaticamente ao chegar no 20º item sem falha ou loop infinito.
- O menu hamburguer abre/fecha em < 150ms (sem animação pesada).
- Typecheck (`tsc --noEmit`) e linting passam sem novos erros.

---

## Open Questions

- O `PendingDetailModal` (`src/components/PendingDetailModal.tsx`) precisa de ajuste mobile? Não foi mencionado explicitamente — incluir no US-004 por precaução.
- O `InviteCodeTicket` na tela de convite tem layout próprio — verificar se já é responsivo ou precisa de ajuste no US-004.
- O carrossel deve ter `overscroll-behavior-x: contain` para evitar que o scroll do carrossel acione o scroll da página? Recomendado incluir.
