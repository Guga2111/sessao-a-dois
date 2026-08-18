# Design System — quando usar o quê

Documento operacional, não catálogo. Se você está prestes a escrever um `<button>` cru, um hex,
um `rounded-[Npx]` ou um `fixed inset-0` à mão, este arquivo existe para te parar antes.

## A regra

1. **Primitivo antes de elemento cru.** Precisa de um botão, card, input, modal ou pill? Comece
   em `src/components/ui/`, não em `<button>`/`<div>` estilizado na mão.
2. **Token antes de cor ou pixel de raio.** Precisa de uma cor ou de um `border-radius`? Comece
   na tabela de tokens/escala de raio abaixo, não num hex ou `rounded-[Npx]` novo.
3. **Se nenhum atende, a saída é ESTENDER o primitivo (ou o token/escala), nunca contorná-lo.**
   Um `className` arbitrário por cima do primitivo errado, ou um hex novo porque "esse tom não
   está na paleta", é exatamente o padrão que produziu 831 hex e 1376 utilitários arbitrários
   antes do Épico 13. Faltou um tone no `Badge`? Adicione o tone. Faltou uma cor na paleta? Vire
   token. Faltou um degrau na escala de raio? Foi isso que `--radius-chip` resolveu (US-044) —
   mesmo caminho para o próximo caso, não uma exceção pontual.

O CI aplica a metade "cor/raio" desta regra mecanicamente: `bun run check:colors` e
`bun run check:radius` (rodam no job `frontend` do CI) falham o build se um hex, uma cor
Tailwind arbitrária (`bg-[#...]`, `text-[rgba(...)]` etc.) ou um `rounded-[...]` aparecer fora de
`src/components/ui/`, em qualquer arquivo do repo — sem allowlist por arquivo. A única fuga é um
comentário `color-ok: <motivo>` / `radius-ok: <motivo>` na linha (ou na linha imediatamente
anterior) da `className`, reservado para cor/raio genuinamente sem token/escala equivalente
(ilustração SVG, cor de marca externa como o `#01b47f` do TMDB, um radius decorativo de poucos
px) — nunca como atalho para duplicar um token que já existe. Ver `client/CLAUDE.md` para o
detalhe de cada gate.

**"Não montar `fixed inset-0` na mão: é `Dialog`."** Todo modal/backdrop do app usa o primitivo
`Dialog` (`src/components/ui/dialog.tsx`, ver seção abaixo) — não um `<div className="fixed
inset-0">` com click-outside/Escape escritos à mão. **Exceção deliberada:** `ErrorBoundary.tsx`
continua com backdrop manual e cores inline (Epico 13, US-053) — ele renderiza no exato momento
em que a árvore React quebrou, e depender de mais infraestrutura React (portal do base-ui,
Context, ciclo de montagem) é menos confiável ali. Não "corrigir" isso numa limpeza futura sem
reabrir essa análise.

## Primitivos (`src/components/ui/`)

| Primitivo | Resolve |
|---|---|
| `avatar.tsx` | Avatar de usuário com fallback, badge de status e agrupamento (`AvatarGroup`) |
| `badge.tsx` | Pill **estática** (span, sem interação) — status, contagem, categoria |
| `button.tsx` | Botão/link-com-cara-de-botão clicável, 6 variantes × 8 tamanhos |
| `calendar.tsx` | Grade de datas para date picker |
| `card.tsx` | Superfície de conteúdo agrupado (header/content/footer) |
| `chip.tsx` | Pill **interativa** (button real, com `active`/`disabled`) — filtro, toggle |
| `collapsible.tsx` | Seção que expande/recolhe (`FiltersPanel`, seções do Hub) |
| `dialog.tsx` | Modal/backdrop centralizado — todo overlay do app passa por aqui |
| `input.tsx` | Campo de texto de linha única |
| `pagination.tsx` | Paginador numerado com reticências |
| `popover.tsx` | Painel flutuante ancorado a um trigger (dropdown de notificação, menus) |
| `scroll-area.tsx` | Contêiner com scrollbar customizada |
| `select.tsx` | Dropdown de seleção única/múltipla |
| `skeleton.tsx` | Placeholder de carregamento (nunca spinner, para leitura — ver `client/CLAUDE.md`) |
| `slider.tsx` | Range de um ou dois thumbs (filtros de nota/duração) |
| `toggle-group.tsx` | Grupo de chips de seleção múltipla |
| `tooltip.tsx` | Texto de apoio ao hover/focus |

## Button

6 variantes (`default`, `outline`, `secondary`, `ghost`, `destructive`, `link`) × 8 tamanhos
(`default`, `xs`, `sm`, `lg`, `icon`, `icon-xs`, `icon-sm`, `icon-lg`).

Critério de variante:
- **`default`** — a ação primária da tela/seção (uma só por contexto visível: "Salvar",
  "Adicionar Título", "Curtir"). Fundo amarelo sólido, alto contraste.
- **`outline`** — ação secundária que ainda compete por atenção (ex.: "Cancelar" ao lado de um
  `default`, filtro com estado). Não use `outline` como segunda ação primária — se duas ações
  parecem igualmente importantes, uma delas está classificada errado.
- **`secondary`** — ação de apoio sem hierarquia de urgência, fundo neutro sólido.
- **`ghost`** — ação de baixa ênfase, ícone-only ou dentro de uma lista/toolbar densa.
- **`destructive`** — a própria ação é destrutiva ("Excluir conta", "Desfazer vínculo"). Não use
  `destructive` para *avisar* sobre algo destrutivo perto — a variante descreve o que o clique
  faz, não o assunto da tela.
- **`link`** — ação com aparência de link de texto, sem chrome de botão.

**Errado:** `<button className="bg-primary/10 text-destructive ...">Cancelar</button>` — reimplementa
`variant="ghost"`/`"outline"` na mão, sem `focus-visible`/`disabled`/`aria-invalid` de graça.
**Certo:** `<Button variant="outline" onClick={onCancel}>Cancelar</Button>`.

Tamanho segue o contexto de densidade (`sm`/`xs` em barras de filtro e listas compactas,
`default`/`lg` em CTAs de tela cheia), nunca em função de "esse botão parece pequeno demais" —
se o tamanho certo não existe na escala, é caso de estender `buttonVariants`, não empilhar
`className="h-7"` por cima de um `size` existente.

Link com cara de botão: `<Button render={<Link to="/join" />}>Formar um casal</Button>` — o
primitivo embrulha `@base-ui/react/button`, que não tem `asChild` (padrão Radix); o equivalente é
a prop `render`.

## Card

`size="default"` (padding generoso, `--card-spacing: 24px`) ou `size="sm"` (`16px`, listas
densas). Composição via `CardHeader`/`CardTitle`/`CardDescription`/`CardAction`/`CardContent`/
`CardFooter` — não hardcode padding/gap direto num `<div className="bg-card ...">`.

**Errado:** `<div className="bg-card rounded-2xl p-6 ring-1 ring-foreground/10">...</div>` —
reproduz o shell do `Card` sem os slots, então o próximo dev não sabe onde o footer "deveria"
morar.
**Certo:** `<Card><CardHeader><CardTitle>...</CardTitle></CardHeader><CardContent>...</CardContent></Card>`.

## Input

Um único visual (borda `white/10`, foco em `#ffcb2b`, inválido em `#ff6b6b`) — não há variantes
de tamanho/tom. Se uma tela precisa de um input visualmente diferente, isso é sinal de que falta
um caso de uso a resolver no próprio primitivo (prop nova), não um `className` que sobrescreve
cor de foco/borda.

**Errado:** `<input className="h-11 rounded-2xl border border-white/10 bg-white/[0.04] ..." />` —
é o `Input` reescrito à mão, campo a campo.
**Certo:** `<Input aria-invalid={!!error} ... />`.

## Dialog

Composição: `Dialog` (root) → `DialogTrigger` (opcional) → `DialogContent` (`DialogPortal` +
`DialogOverlay` já embutidos) → `DialogHeader`/`DialogTitle`/`DialogDescription`/corpo/
`DialogFooter`. `DialogContent` já centraliza e já tem o backdrop (`bg-black/80
backdrop-blur-xs`, sem passthrough de className — todo modal-Dialog do app usa o mesmo backdrop).

Critério de escolha — não há variante, há *padrão de montagem*:
- **Sempre montado**, `open={estado}` — quando o conteúdo não tem estado local para resetar
  (`MatchCelebrationModal`, `CoupleSection`'s `DissolveDialog`).
- **Montagem condicional**, `{condicao && <Dialog open ...>}` — quando o conteúdo tem estado
  local que precisa resetar a cada abertura (`DeleteAccountSection`'s `DeleteDialog`, que tem
  campos de senha/confirmação).

`showCloseButton={false}` quando o modal já tem seu próprio affordance de fechar (botão
"Cancelar", ou nenhum X). Toda instância precisa de `DialogTitle` acessível — visível ou
`className="sr-only"` se o heading visual já é texto plano.

**Errado:** `<div onClick={close} className="fixed inset-0 bg-black/80 ...">` com listener de
Escape escrito à mão — é o padrão pré-Épico-13 que US-045 a US-052 eliminaram do app inteiro
(exceto `ErrorBoundary`, ver acima).
**Certo:** `<Dialog open={open} onOpenChange={setOpen}><DialogContent>...</DialogContent></Dialog>`.

## Pill: Badge vs. Chip

Dois primitivos, não um — `Badge` é `<span>` de exibição (sem `disabled`/`cursor-pointer`/
`aria-pressed`), `Chip` é `<button>` de interação. Misturar os dois numa "pill com prop
`interactive`" juntaria uma API de display com uma de ação — mesmo motivo pelo qual `Button` e
`ToggleGroupItem` continuam separados.

- **`Badge`** (`tone`: `neutral`/`primary`/`coral`/`muted`) — status, contagem, categoria que o
  usuário só lê (gênero de filme, badge "Sugestão", status de faixa).
- **`Chip`** (`active`, `disabled`) — algo que o usuário clica para alternar (filtro, toggle de
  comparação). Se o base-ui já expõe um `*Trigger` (`CollapsibleTrigger`, `PopoverTrigger`), use
  `render={<Chip active={...} />}` nele em vez de montar o `Chip` solto ao lado — o trigger clona
  seu próprio `aria-*`/estado de abertura para dentro do elemento renderizado.

**Errado (Badge):** `<span className="rounded-full border border-primary/30 bg-primary/10 px-2.5 py-1 text-[11px] text-primary">Drama</span>`
— pill hand-rolled que reimplementa `tone="primary"`.
**Certo:** `<Badge tone="primary">Drama</Badge>`.

**Errado (Chip):** `<button onClick={toggle} className="rounded-full border px-4 py-2.5 ...">Ação</button>`
com a cor de "ativo" calculada na mão a cada call site.
**Certo:** `<Chip active={isActive} onClick={toggle}>Ação</Chip>`.

Migrando uma pill hand-rolled existente: a AC é equivalência de pixel, não pureza de tone — pegue
o `tone`/`active` mais próximo pela família de cor e sobrescreva o que difere via `className`
(`cn()` resolve conflito de mesma categoria a favor do `className`), em vez de arredondar
padding/opacidade reais para o default do tone. Ver `client/CLAUDE.md` ("Badge / Chip") para os
três padrões observados na migração (tone exato, mesma família com opacidade/raio diferentes,
cor 100% calculada em runtime que não mapeia para nenhum tone estático).

## Tokens de cor

`.dark` em `src/index.css` (o app é dark-only, D17 — `:root`/tema claro está morto, não usar).

| Token | Papel | Quando usar |
|---|---|---|
| `--background` | Fundo base de toda tela escura | `bg-background` no body/shell de tela |
| `--foreground` | Texto principal sobre o fundo escuro | Texto de destaque, títulos |
| `--card` | Superfície de card/modal/painel | `bg-card` em `MediaCard`, `TitleModal`, `FiltersPanel` |
| `--primary` | Amarelo de marca (`#ffcb2b`) | CTA primário, destaque, foco — a cor mais frequente do app |
| `--muted-foreground` | Texto secundário/legenda | Datas, contadores, descrições curtas |
| `--accent` | Amarelo claro (`#ffe08a`) | Hover do primário, link em destaque |
| `--destructive` | Vermelho de erro/ação destrutiva (`#ff6b6b`) | Borda/ícone/texto de aviso de sistema |
| `--destructive-foreground` | Vermelho claro (`#ffb3b3`) | Texto sobre superfície destrutiva translúcida |
| `--series` | Laranja de marca (`#ff9e2c`) | Tudo ligado a "Séries/TV" (ícones, labels, badges) |
| `--coral` / `--coral-foreground` / `--coral-chip` | Família destrutiva alternativa (`screens/account/`) | Desfazer vínculo, excluir conta — deliberadamente distinta de `--destructive` para não ler como "erro de sistema" |
| `--success` / `--success-foreground` | Verde de status positivo | Nota TMDB, badge WATCHED, botão "Curtir" |
| `--rating` | Dourado da estrela de nota | Componente de estrelas, distinto de `--primary` |
| `--pill-foreground` | Texto de pill de gênero/provedor não-compartilhada | `ComparisonDialog`, `MediaDetailModal`, `MediaCard` |
| `--label-foreground` | Texto de label de campo de formulário | `TitleModal` |
| `--tertiary-foreground` | Texto terciário, mais apagado que `--muted-foreground` | `screens/account/` |
| `--accent-strong` | Amarelo mais claro que `--accent` | Título selecionado em `ComparisonDialog`/`TitleModal` |
| `--destructive-soft` | Vermelho claro de hover sobre botão destrutivo | Distinto de `--destructive-foreground` |
| `--surface-secondary` | Um degrau acima de `--card` | Input, dropdown, tooltip |
| `--on-primary` / `--on-success` / `--on-destructive` | Texto quase-preto sobre superfície sólida da respectiva cor | Badge/pill/botão sobre fundo `--primary`/`--success`/`--destructive` sólido |
| `--want-to-see` | Roxo do badge de status WANT_TO_SEE | Único uso, `MediaDetailModal` |
| `--opinion-foreground` / `--synopsis-foreground` / `--caption-foreground` | Variações de texto secundário em itálico/legenda/sinopse | Ver comentário de cada uma em `index.css` para o hex de origem |
| `--scrollbar-thumb` | Cor da scrollbar customizada | `ComparisonDialog` |
| `--warning-foreground` | Texto de aviso/contagem regressiva | `InviteCodeTicket` |
| `--backdrop` | Fundo do overlay de modal | Deliberadamente distinto de `--background` (mais escuro, leve roxo) |
| `--nav-surface` | Superfície do dropdown de navegação mobile | `Header`, um degrau mais escuro que `--card` |

Além da tabela: um banco `--shadow-glow-*`/`--shadow-elevation-*` para todo `box-shadow`
reaproveitado em mais de um lugar (consumir como `shadow-[var(--shadow-elevation-3)]`, não
copiar a string rgba), e classes `.bg-coral-fade-h`/`.bg-primary-fade-h`/`.bg-coral-card-wash`/
etc. para gradientes de dois stops que não cabem num modificador de opacidade de token. Antes de
assumir que uma cor não tem token, confira essa lista e o comentário acima do bloco `.dark` em
`index.css` — `rgba(255,255,255,X)`/`rgba(8,7,11,X)` também convertem para `white/X`/`backdrop/X`
(cor nomeada do Tailwind + modificador de opacidade), não para um token novo.

## Escala de raio

`--radius-*` em `index.css` (`@theme inline`), todas `calc(var(--radius) * N)` sobre
`--radius: 0.625rem` (10px).

| Nome | Valor | Quando usar |
|---|---|---|
| `--radius-sm` | 6px | Elementos pequenos (swatch de legenda de gráfico, barra de chart) |
| `--radius-md` | 8px | Cantos internos de card (`CardHeader`/`CardFooter` top/bottom) |
| `--radius-lg` | 10px | Raio "padrão" de elemento médio |
| `--radius-chip` | 12px | **Só** pill/chip (`rounded-full` já cobre a forma; use quando o elemento não é totalmente arredondado mas é da família pill) — não é um degrau de tamanho genérico, foi adicionado (US-044) especificamente entre `lg` e `xl` para esse caso |
| `--radius-xl` | 14px | Card/imagem de tamanho médio |
| `--radius-2xl` | 18px | `Card` (raio default do primitivo) |
| `--radius-3xl` | 22px | Superfícies grandes (painel, seção) |
| `--radius-4xl` | 26px | `Button`, `DialogContent` — maior raio do app |

Se nenhum degrau bate exatamente com o design (caso raro, decorativo), o valor arbitrário
sobrevive ao gate só com um comentário `radius-ok: <motivo>` na linha da `className` — não crie
um novo hex de raio sem essa marca, e não "arredonde" para o degrau mais próximo se isso muda o
visual de forma perceptível num elemento pequeno (ex.: um swatch de 11px não deveria virar
`--radius-sm`/6px, que dobraria o raio).

## Ver também

`client/CLAUDE.md` cobre convenções mais amplas de `client/` (testes, stores, padrões de tela) —
este documento é só a parte "quando usar qual peça do design system".
