# PRD: Épicos 13 e 14 — Governança do Design System e Documentação de Entrada

**Origem:** `POST-MVP-TASK.md`, Épico 13 (T13.1–T13.8) e Épico 14 (T14.1).
**Data:** 2026-08-13.
**Decisões vinculantes:** D11 (skill `frontend-design` dispensada em refatoração pura),
D17, D18, D19 (registradas em `POST-MVP-TASK.md`).

---

## 1. Introdução / Visão geral

O `client/` tem um design system completo que o app quase não usa.
`client/src/components/ui/` já traz 15 primitivos (`button.tsx` sozinho declara 6 variantes
e 8 tamanhos via `cva`), e `client/src/index.css` já define tokens de cor em `oklch` e uma
escala de raio de `sm` a `4xl` dentro de `@theme inline`. Fora de `components/ui/`, nada
disso é consumido: a identidade visual do produto vive **copiada e colada em string de
classe**, arquivo por arquivo.

Este PRD cobre duas frentes que fecham a segunda rodada do backlog pós-MVP:

- **Épico 13** — fazer os tokens e os primitivos serem a fonte da verdade, e instalar o portão que impede o app de contorná-los de novo.
- **Épico 14** — escrever o `README.md` da raiz, que hoje tem **15 bytes**.

### O problema, medido em 2026-08-13

Todas as medidas excluem `components/ui/` (onde o hex é legítimo) e arquivos de teste.

| Medida | 2026-08-06 | 2026-08-13 | Δ em 1 semana |
|---|---|---|---|
| Literais hexadecimais em `.tsx` | 689 | **831** | +142 (+21%) |
| Utilitários de cor arbitrários (`bg-[…]`, `text-[…]`, …) | 1033 | **1376** | +343 (+33%) |
| `rounded-[…]` arbitrário | — | **87** | — |
| `<button>` cru em vez de `Button` | 12 | **13** | +1 |
| Modais com backdrop `fixed inset-0` montado à mão | — | **9 arquivos** | — |
| "Pill"/badge manual (`rounded-full` + `px-*`) | — | **36 usos em 18 arquivos** | — |
| **Arquivos afetados por cor** | — | **53** | — |

**A segunda coluna é o argumento central deste PRD.** O crescimento de 21%/33% aconteceu
durante os Épicos 11 e 12, sem que ninguém tenha decidido "vamos usar mais hex" — cada
linha nova foi localmente razoável. É exatamente assim que se chega a 831: um de cada vez.
Por isso a **D18** antecipa o portão de CI (US-002) para antes da migração em massa: sem
ele, a migração persegue um alvo móvel.

**Consequência operacional:** os números acima são um retrato datado e continuarão subindo
até o portão existir. Toda story deve **remedir na hora de executar** em vez de confiar
nesta tabela.

### A paleta real do produto

Os hex mais repetidos mostram que a paleta existe — ela só não mora em token:

| Cor | Ocorrências | Papel aparente |
|---|---|---|
| `#ffcb2b` | 182 | primária / destaque |
| `#a6a39a` | 174 | texto secundário |
| `#f6f4ec` | 135 | texto sobre fundo escuro |
| `#161513` | 59 | superfície de card |
| `#09090a` | 49 | fundo |
| `#ff6b6b` | 35 | destrutivo / erro |
| `#ffb3b3` | 23 | destrutivo, variante clara |
| `#ffe08a` | 22 | destaque, variante clara |

### O que foi investigado e descartado

**Imports diretos da biblioteca de baixo nível contornando `components/ui/`: não existe.**
`client/package.json` não tem `@radix-ui/*`; os primitivos embrulham `@base-ui/react` e
`react-day-picker`. Busca por esses imports fora de `components/ui/` retorna **0
ocorrências**. Registrado para não precisar reinvestigar — não vira story.

---

## 2. Objetivos

1. **Tornar os tokens a fonte da verdade da cor.** Zero hex fora de `components/ui/` e de SVG declaradamente ilustrativo.
2. **Tornar a escala de raio a fonte da verdade do arredondamento.** 69% dos 87 casos já batem exato com um token existente.
3. **Instalar o portão antes da limpeza**, não depois — para que o número pare de crescer enquanto o trabalho acontece.
4. **Fazer o app reusar os primitivos que já tem**: `Button` no lugar dos 13 `<button>` crus, `Dialog` no lugar dos 9 modais montados à mão.
5. **Criar o primitivo que falta** (`Badge`, possivelmente também um chip de filtro), em vez de deixar 36 reimplementações manuais.
6. **Fechar a meia-feature do tema** assumindo dark-only (D17).
7. **Escrever a porta de entrada do repositório** (`README.md`).
8. **Deixar escrito quando usar cada coisa**, para que a próxima pessoa não recomece o ciclo.

---

## 3. Decisões confirmadas (E13.x)

Vinculantes para as stories abaixo. Reabrir exige registrar o motivo em `POST-MVP-TASK.md`.

| # | Questão | Decisão | Consequência |
|---|---------|---------|--------------|
| E13.1 | Destino do tema claro (D17) | **Dark-only.** Remover `ThemeProvider`, fixar `.dark` no `<html>`, apagar tokens `:root` claros não usados. | US-019 é remoção pura, dispensada da skill `frontend-design`. Tema claro de verdade continua possível depois — a US-003..009 é justamente o que o torna barato. |
| E13.2 | Ordem do portão de CI (D18) | **Antecipar para logo depois dos tokens**, com allowlist que encolhe. | US-002 vem antes da migração. A allowlist é o placar da migração, não exceção permanente. |
| E13.3 | Verificação visual (D19) | **Gate humano.** | O sandbox não tem navegador (limitação já registrada em `client/CLAUDE.md` e nas notas da US-006 do Épico 12). Critérios visuais ficam `- [ ]` com o motivo ao lado, conferidos pelo mantenedor antes do merge. |
| E13.4 | Skill `frontend-design` (D11) | **Dispensada em refatoração pixel-idêntica; obrigatória onde há decisão de design.** | Dispensada: US-001 a US-012, US-015, US-016, US-019. **Obrigatória: US-013/US-014** (snap de raio move pixels) e **US-017** (criar primitivo é decisão de design). |
| E13.5 | Escopo da medição | **Excluir `components/ui/` e arquivos de teste** de toda contagem. | `components/ui/` é onde o valor cru é legítimo (é a camada que define o token em termos concretos). |

---

## 4. User Stories

### Fase 1 — Fundação: tokens e portão

#### US-001: Extrair a paleta real para tokens semânticos
**Descrição:** Como desenvolvedor, quero que `index.css` declare a identidade visual do
produto, para que quem lê o arquivo descubra a cara do app e para que trocar a cor primária
seja uma edição, não 182.

**Critérios de aceite:**
- [ ] As 8 cores da tabela da seção 1 têm token semântico correspondente (`--background`, `--card`, `--foreground`, `--muted-foreground`, `--primary`, `--destructive`, e o que mais couber).
- [ ] Valores em `oklch`, consistentes com o resto do arquivo.
- [ ] Cada token tem comentário dizendo o papel que exerce **neste app** (o que é `--card` aqui, não em abstrato).
- [ ] Token novo só onde nenhum semântico existente couber — e, nesse caso, o comentário explica por quê.
- [ ] **Nenhuma mudança visual**: os componentes ainda usam hex, então redefinir token não move pixel. Isso é deliberado — esta story vai sozinha e é conferível isoladamente.
- [ ] `bun run build` passa.
- [ ] **[gate humano]** Conferência visual das telas principais antes/depois — sem navegador no sandbox (D19).

---

#### US-002: Portão de CI contra cor crua, com allowlist
**Descrição:** Como mantenedor, quero que o CI recuse hex novo fora de `components/ui/`,
para que a contagem pare de crescer enquanto a migração acontece.

**Depende de:** US-001 (a mensagem de erro precisa poder apontar qual token usar).

**Critérios de aceite:**
- [ ] Um `#ffcb2b` novo em `src/screens/` faz o CI falhar.
- [ ] O mesmo hex dentro de `src/components/ui/` **não** falha.
- [ ] A mensagem de erro diz **qual token usar** em vez da cor crua, não só "cor crua proibida".
- [ ] A allowlist inicial contém **exatamente** os 53 arquivos que hoje têm hex/arbitrário — nenhum a mais. Um arquivo já limpo entrando na lista seria uma porta aberta silenciosa.
- [ ] Um hex novo em arquivo **da allowlist** que já foi migrado no meio-tempo também falha — a lista é conferida contra a realidade, não confiada cegamente.
- [ ] A allowlist tem comentário no topo dizendo que é dívida temporária e que o PR que apagar a última linha fecha a migração.
- [ ] O CI não fica mais lento de forma perceptível.
- [ ] Implementado por ESLint `no-restricted-syntax` **ou** step de `grep` no CI — o que for mais simples e funcionar (mesmo espírito do gate de `bun audit` da US-005 do Épico 8).

---

### Fase 2 — Migração de cor (53 arquivos, ~2200 ocorrências)

> **Regra comum a US-003 … US-009.** Substituição mecânica e **pixel-idêntica**:
> `bg-[#161513]`→`bg-card`, `text-[#a6a39a]`→`text-muted-foreground`, e assim por diante.
> Se algum pixel mudar, o mapeamento da US-001 está errado — **corrigir o token, não o
> componente**. Onde a cor não tiver token correspondente, **parar e voltar à US-001** em
> vez de inventar arbitrário novo. Um commit por arquivo ou grupo pequeno, para revisão
> viável e revert cirúrgico. Ao terminar cada grupo, **remover os arquivos correspondentes
> da allowlist da US-002** — é o que transforma a lista em placar.
>
> Critérios que se repetem em todas as sete: os testes do Épico 11 continuam verdes;
> `bun run typecheck`, `bun run lint` e `bun run build` passam; **[gate humano]**
> verificação visual tela a tela (D19).

#### US-003: Migrar cor — modais e diálogos
**Descrição:** Como desenvolvedor, quero os modais consumindo tokens, começando pelo maior
volume, onde o retorno por arquivo é mais alto.

**Arquivos (~588 ocorrências):** `ComparisonDialog.tsx` (110), `MediaDetailModal.tsx` (103),
`PendingDetailModal.tsx` (98), `TitleModal.tsx` (89), `WatchModal.tsx` (46),
`ReviewModal.tsx` (46), `RatingRequestDialog.tsx` (45), `DeleteTrackDialog.tsx` (28),
`MatchCelebrationModal.tsx` (23).

**Critérios de aceite:**
- [ ] Zero hex e zero utilitário de cor arbitrário nos 9 arquivos.
- [ ] Os 9 arquivos saíram da allowlist da US-002.
- [ ] Regra comum acima cumprida.

---

#### US-004: Migrar cor — cards e mídia
**Descrição:** Como desenvolvedor, quero os cards de mídia consumindo tokens.

**Arquivos (~168):** `MediaCard.tsx` (98), `SearchResultCard.tsx` (44), `CoupleAvatars.tsx` (9),
`skeletons/AppShellSkeleton.tsx` (5), `skeletons/MediaCardSkeleton.tsx` (3),
`skeletons/KpiCardSkeleton.tsx` (3), `skeletons/ChartSkeleton.tsx` (3).

**Critérios de aceite:**
- [ ] Zero hex e zero arbitrário de cor nos 7 arquivos.
- [ ] Os skeletons continuam visualmente coerentes com o componente que representam.
- [ ] Os 7 arquivos saíram da allowlist.
- [ ] Regra comum acima cumprida.

---

#### US-005: Migrar cor — telas de match
**Descrição:** Como desenvolvedor, quero as telas de match consumindo tokens.

**Arquivos (~255):** `SuggestionsTab.tsx` (73), `FiltersPanel.tsx` (73), `SearchTab.tsx` (46),
`compareUi.tsx` (34), `MatchScreen.tsx` (29).

**Critérios de aceite:**
- [ ] Zero hex e zero arbitrário de cor nos 5 arquivos.
- [ ] Os testes de `SearchTab`/`SuggestionsTab` do Épico 11 continuam verdes (consultam por papel/texto acessível, não por classe — devem ser indiferentes à troca).
- [ ] Os 5 arquivos saíram da allowlist.
- [ ] Regra comum acima cumprida.

---

#### US-006: Migrar cor — telas de conta
**Descrição:** Como desenvolvedor, quero as telas de conta consumindo tokens.

**Arquivos (~303):** `DeleteAccountSection.tsx` (81), `CoupleSection.tsx` (78),
`PasswordSection.tsx` (60), `ProfileSection.tsx` (39), `AccountSection.tsx` (25),
`AccountScreen.tsx` (20).

**Critérios de aceite:**
- [ ] Zero hex e zero arbitrário de cor nos 6 arquivos.
- [ ] O pill de "casal dissolvido" (`CoupleSection.tsx:57-58`) fica visualmente idêntico — ele será substituído por primitivo na US-018, não aqui.
- [ ] Os 6 arquivos saíram da allowlist.
- [ ] Regra comum acima cumprida.

---

#### US-007: Migrar cor — landing
**Descrição:** Como desenvolvedor, quero a landing consumindo tokens.

**Arquivos (~331):** `LandingHero.tsx` (71), `MatchShowcase.tsx` (53), `DashboardPreview.tsx` (49),
`LandingFooter.tsx` (26), `LandingHeader.tsx` (25), `Testimonials.tsx` (24),
`HowItWorks.tsx` (22), `FinalCta.tsx` (20), `FeatureGrid.tsx` (20), `FaqSection.tsx` (18),
`LandingScreen.tsx` (3).

**Critérios de aceite:**
- [ ] Zero hex e zero arbitrário de cor nos 11 arquivos, **exceto** SVG/gradiente declaradamente ilustrativo — cada exceção com comentário explicando por que é ilustração e não cor de interface.
- [ ] Os 11 arquivos saíram da allowlist (ou permanecem com exceção documentada linha a linha).
- [ ] Regra comum acima cumprida.

---

#### US-008: Migrar cor — rotas de autenticação
**Descrição:** Como desenvolvedor, quero as telas de auth consumindo tokens.

**Arquivos (~264):** `JoinPage.tsx` (52), `InviteCodeTicket.tsx` (52), `ResetPasswordPage.tsx` (44),
`LoginPage.tsx` (28), `ForgotPasswordPage.tsx` (26), `RegisterPage.tsx` (22),
`BondDissolvedNotice.tsx` (20), `AuthLayout.tsx` (20).

**Critérios de aceite:**
- [ ] Zero hex e zero arbitrário de cor nos 8 arquivos.
- [ ] Os testes dos guards de rota do Épico 11 continuam verdes.
- [ ] Os 8 arquivos saíram da allowlist.
- [ ] Regra comum acima cumprida.

---

#### US-009: Migrar cor — shell da aplicação
**Descrição:** Como desenvolvedor, quero o shell (header, hub, dashboard, notificações)
consumindo tokens, fechando os 53 arquivos.

**Arquivos (~301):** `DashboardScreen.tsx` (95), `HubScreen.tsx` (56), `Header.tsx` (54),
`NotificationDropdown.tsx` (52), `ErrorBoundary.tsx` (18), `NotificationBell.tsx` (15),
`TrackSection.tsx` (11).

**Critérios de aceite:**
- [ ] Zero hex e zero arbitrário de cor nos 7 arquivos.
- [ ] Os testes de `HubScreen`/`TrackSection` do Épico 11 continuam verdes.
- [ ] Os gráficos do `DashboardScreen` (barras, donut, breakdown de gênero) continuam com as mesmas cores — se a série de dados usa cor fora da paleta semântica, criar token de chart (`--chart-1`… já existem no arquivo) em vez de manter hex.
- [ ] Os 7 arquivos saíram da allowlist.
- [ ] Regra comum acima cumprida.

---

#### US-010: Substituir os 13 `<button>` crus pelo primitivo `Button`
**Descrição:** Como desenvolvedor, quero que todo botão do app use o primitivo, para que
estado de foco, `disabled` e variante venham de um lugar só.

**Arquivos:** `NotificationDropdown.tsx` (3), `compareUi.tsx` (2), `HubScreen.tsx` (2),
`FiltersPanel.tsx` (1), `NotificationBell.tsx` (1), `MediaCard.tsx` (1), `Header.tsx` (1),
`ErrorBoundary.tsx` (1), `ComparisonDialog.tsx` (1).

**Critérios de aceite:**
- [ ] Os 13 `<button>` viraram `Button`, com a variante e o tamanho equivalentes ao estilo manual que substituíram.
- [ ] Nenhum deles perdeu comportamento: `disabled`, `type="button"`/`type="submit"`, `aria-*` e handlers preservados.
- [ ] Se algum caso não couber em nenhuma das 6 variantes existentes, **estender `button.tsx`** com a variante nova — não voltar ao `<button>` cru nem usar `className` para recriar a aparência por fora.
- [ ] Remedir: `grep -rno "<button" --include="*.tsx" client/src | grep -v components/ui/` retorna zero fora de teste.
- [ ] Os testes do Épico 11 continuam verdes.
- [ ] `bun run typecheck`, `bun run lint` e `bun run build` passam.
- [ ] **[gate humano]** Verificação visual dos 9 arquivos (D19).

---

#### US-011: Esvaziar a allowlist e endurecer o portão
**Descrição:** Como mantenedor, quero a allowlist vazia e o portão valendo para o
repositório inteiro, para que a migração fique fechada de verdade.

**Depende de:** US-003 … US-010.

**Critérios de aceite:**
- [ ] A allowlist da US-002 está vazia ou removida do arquivo de configuração.
- [ ] Restam apenas exceções pontuais, **cada uma com comentário** explicando por que aquela cor é ilustração e não interface — mesma disciplina exigida dos `--ignore` do `bun audit`.
- [ ] Um hex novo em **qualquer** arquivo fora de `components/ui/` faz o CI falhar.
- [ ] Contagem final registrada no PR (hex e arbitrários restantes, com a justificativa de cada um).

---

### Fase 3 — Arredondamento

#### US-012: Migrar raio — os 60 casos com token exato
**Descrição:** Como desenvolvedor, quero o arredondamento vindo da escala onde ela já
resolve exatamente, para que o raio deixe de ser número mágico.

**Contexto:** `--radius: 0.625rem` → `sm` 6px · `md` 8px · `lg` 10px · `xl` 14px ·
`2xl` 18px · `3xl` 22px · `4xl` 26px. Mapeamento exato:
`10px`(19)→`rounded-lg` · `22px`(14)→`rounded-3xl` · `18px`(13)→`rounded-2xl` ·
`14px`(12)→`rounded-xl` · `8px`(2)→`rounded-md`.

**Critérios de aceite:**
- [ ] As 60 ocorrências com equivalência exata foram substituídas.
- [ ] O diff é **pixel-idêntico por construção** — o valor calculado do token é o mesmo número que o arbitrário declarava.
- [ ] As 27 ocorrências sem token exato **não** foram tocadas nesta story (ficam para US-013/US-014).
- [ ] Os testes do Épico 11 continuam verdes.
- [ ] `bun run typecheck`, `bun run lint` e `bun run build` passam.

---

#### US-013: Decidir o destino dos 27 raios sem token exato
**Descrição:** Como mantenedor, quero decidir explicitamente o que fazer com os raios que
não têm equivalente na escala, porque snapar move pixels e isso é decisão de design, não
refatoração.

**Contexto:** `20px`×11, `12px`×8, `3px`×4, `16px`×3, `24px`×1. Snapar move 1–2px; o `3px`
dobraria para `sm`=6px.

**⚠️ Esta story usa a skill `frontend-design`** (E13.4).

**Critérios de aceite:**
- [ ] A decisão está registrada em `POST-MVP-TASK.md` como decisão nova, com justificativa, **antes** de qualquer edição de código.
- [ ] A decisão escolhe, por grupo de valor, entre: **(a)** snapar para o vizinho, **(b)** estender a escala com o degrau faltante, **(c)** manter arbitrário com comentário.
- [ ] A recomendação de partida do épico foi considerada e aceita ou recusada com motivo: (a) para `20px`/`16px`/`24px` (15 usos, diferença ≤2px); (b) para `12px` (8 usos, e `lg`→`xl` é salto de 4px, visível em elemento pequeno); os 4 `3px` avaliados caso a caso por serem provavelmente detalhe decorativo.
- [ ] Nenhum código alterado nesta story.

---

#### US-014: Executar a decisão de raio e estender o portão
**Descrição:** Como desenvolvedor, quero os 27 casos resolvidos conforme a decisão e o
portão cobrindo raio, para que o padrão não volte.

**Depende de:** US-013.

**⚠️ Usa a skill `frontend-design`** se a decisão foi (a) ou (b) (E13.4).

**Critérios de aceite:**
- [ ] As 27 ocorrências seguem a decisão da US-013.
- [ ] Zero `rounded-[…]` fora de `components/ui/`, exceto os que a opção (c) preservar — cada um com comentário.
- [ ] A diferença de 1–2px (se houve snap) está **declarada no PR**, não escondida atrás de "sem mudança visual".
- [ ] O portão da US-002 passa a barrar `rounded-[…]` novo também, com a mesma mensagem apontando o token.
- [ ] Os testes do Épico 11 continuam verdes.
- [ ] `bun run typecheck`, `bun run lint` e `bun run build` passam.
- [ ] **[gate humano]** Verificação visual dos elementos afetados (D19).

---

### Fase 4 — Reuso e criação de primitivos

#### US-015: Consolidar 8 modais no primitivo `Dialog`
**Descrição:** Como desenvolvedor, quero que os modais usem o `Dialog` que já existe, para
que foco preso, `aria-modal` e fechamento por Escape sejam resolvidos uma vez.

**Contexto:** 9 arquivos montam backdrop `fixed inset-0` à mão, enquanto `TitleModal.tsx`,
`ComparisonDialog.tsx` e `RatingRequestDialog.tsx` já usam o primitivo corretamente —
prova de que ele dá conta. `CoupleSection.tsx:84` tem comentário admitindo a escolha:
*"backdrop `fixed inset-0` proprio + Escape"*.

**Arquivos (8):** `DeleteTrackDialog.tsx`, `WatchModal.tsx`, `MediaDetailModal.tsx`,
`PendingDetailModal.tsx`, `ReviewModal.tsx`, `MatchCelebrationModal.tsx`,
`account/CoupleSection.tsx`, `account/DeleteAccountSection.tsx`.
(`ErrorBoundary.tsx` fica para a US-016.)

**Critérios de aceite:**
- [ ] Os 8 arquivos usam `Dialog` de `components/ui/`, sem `fixed inset-0` próprio.
- [ ] Foco preso, Escape e clique-fora funcionam em todos, **via comportamento do primitivo**, não reimplementados.
- [ ] O conteúdo interno de cada modal foi preservado — esta story troca o invólucro, não redesenha nada.
- [ ] Se algum dos 8 hoje **não** fecha com Escape e passa a fechar, isso é correção de bug: registrar no PR (não é motivo para acionar a skill `frontend-design`).
- [ ] Um commit por arquivo.
- [ ] Os testes do Épico 11 continuam verdes.
- [ ] `bun run typecheck`, `bun run lint` e `bun run build` passam.
- [ ] **[gate humano]** Verificação visual dos 8 modais (D19).

---

#### US-016: Decidir o caso do `ErrorBoundary`
**Descrição:** Como desenvolvedor, quero avaliar se o `ErrorBoundary` deve mesmo usar
`Dialog`, porque ele renderiza justamente quando a árvore React quebrou.

**Contexto:** É o único dos 9 que não é modal de fluxo normal. Trocar por `Dialog` o faz
depender de mais contexto React (portal, estado do primitivo) exatamente no momento em que
menos se pode confiar nisso.

**Critérios de aceite:**
- [ ] A análise foi feita e registrada: `ErrorBoundary` migra ou fica.
- [ ] **Se ficar**, há comentário no próprio arquivo explicando por que ele é exceção deliberada à regra "modal é `Dialog`" — e a US-020 registra a exceção no documento do design system.
- [ ] **Se migrar**, há teste ou verificação manual de que a tela de erro renderiza corretamente em cenário de erro **simulado**, não só no caminho feliz.
- [ ] Qualquer que seja o resultado, `ErrorBoundary.tsx` continua capturando exceção vinda de fora do `BrowserRouter` (comportamento documentado no comentário atual do arquivo, linha 16).

---

#### US-017: Desenhar o(s) primitivo(s) que faltam para "pill"
**Descrição:** Como desenvolvedor, quero um primitivo para pills/badges, porque hoje há 36
reimplementações manuais e nenhum componente para reusar.

**Contexto:** Os 36 usos **não são todos a mesma coisa**. A amostra mistura badge de status
estático (`CoupleSection.tsx:57-58`) com chip clicável de filtro (`FiltersPanel.tsx:136`,
`compareUi.tsx:28` — têm `cursor-pointer`, estado ativo/inativo e `disabled:`) e até um
`SelectTrigger` estilizado como pill (`FiltersPanel.tsx:117`). Provavelmente são **dois**
primitivos, não um com variantes demais.

**⚠️ Esta story usa a skill `frontend-design`** — criar componente é decisão de design
(quais variantes, quais tokens, quais tamanhos), não move de lugar algo que já existe (E13.4).

**Critérios de aceite:**
- [ ] Os 36 usos foram levantados e **agrupados por papel visual** antes de qualquer código — o agrupamento define as variantes, não o contrário.
- [ ] A decisão "um primitivo ou dois" está registrada com justificativa.
- [ ] O(s) primitivo(s) vivem em `components/ui/`, seguindo o padrão de `cva` já usado em `button.tsx`.
- [ ] Consomem os tokens da US-001 — **nenhum token de cor novo** criado aqui sem passar pela US-001.
- [ ] O `SelectTrigger` estilizado como pill foi avaliado: vira variante do primitivo novo ou continua sendo `Select` com classe — decidido explicitamente, não por omissão.
- [ ] `bun run typecheck`, `bun run lint` e `bun run build` passam.
- [ ] **[gate humano]** Verificação visual do primitivo em cada variante (D19).

---

#### US-018: Migrar os 36 usos manuais de pill para o primitivo
**Descrição:** Como desenvolvedor, quero os 36 usos consumindo o primitivo novo.

**Depende de:** US-017.

**Critérios de aceite:**
- [ ] Os 36 usos em 18 arquivos (ou a contagem real remedida) usam o(s) primitivo(s) da US-017.
- [ ] Nenhum `rounded-full` + `px-*` manual sobrou fora de `components/ui/`, exceto o que não for pill (avatar, ponto indicador, spinner) — esses não são alvo desta story.
- [ ] Os testes do Épico 11 continuam verdes.
- [ ] `bun run typecheck`, `bun run lint` e `bun run build` passam.
- [ ] **[gate humano]** Verificação visual tela a tela (D19).

---

### Fase 5 — Tema e documentação

#### US-019: Assumir dark-only e remover o tema morto
**Descrição:** Como desenvolvedor, quero remover a meia-feature de tema, porque ela dá
trabalho de manter e não entrega nada ao usuário.

**Contexto (D17/E13.1):** `main.tsx:13` monta `ThemeProvider` (230 linhas, com
`localStorage` e listener de `prefers-color-scheme`) e **nada no app consome o contexto** —
`useTheme` não é chamado em lugar nenhum fora do próprio arquivo. Não há alternador. As
telas assumem escuro.

**Critérios de aceite:**
- [ ] `client/src/components/theme-provider.tsx` removido.
- [ ] `main.tsx` não referencia mais `ThemeProvider`.
- [ ] `.dark` fixo no `<html>` (em `index.html` ou equivalente), e o app renderiza **idêntico** ao de antes.
- [ ] Tokens `:root` claros não utilizados removidos de `index.css`.
- [ ] `grep -rn "ThemeProvider\|useTheme" client/src` não retorna nada.
- [ ] O mock de `matchMedia` em `test/setup.ts` foi reavaliado: continua necessário para `lib/useIsMobile.ts` (que também o usa) — **não remover junto por engano**.
- [ ] O comentário de `ErrorBoundary.tsx:16`, que cita o `ThemeProvider`, foi atualizado.
- [ ] Os testes do Épico 11 continuam verdes.
- [ ] `bun run typecheck`, `bun run lint` e `bun run build` passam.
- [ ] **[gate humano]** Conferir que nenhuma tela regrediu (D19).

---

#### US-020: Documentar o inventário e o critério de uso
**Descrição:** Como desenvolvedor entrando no `client/`, quero saber **quando usar cada
primitivo e cada token**, para não recomeçar o ciclo que produziu 831 hex.

**Depende de:** US-014, US-016, US-018 (documenta o estado final).

**Critérios de aceite:**
- [ ] `client/docs/DESIGN-SYSTEM.md` existe e cobre todos os primitivos de `components/ui/` (os 15 atuais + o(s) da US-017), com uma linha por primitivo dizendo o que resolve.
- [ ] `Button`, `Card`, `Input`, `Dialog` e o primitivo novo têm critério explícito de escolha de variante, **com exemplo de uso errado** ao lado do certo.
- [ ] Tabela de tokens de **cor** (US-001) e da escala de **raio**, com nome, valor e quando usar.
- [ ] A regra está escrita: **primitivo antes de elemento cru; token antes de cor ou pixel de raio.** Se nenhum atende, a saída é **estender o primitivo**, não contorná-lo.
- [ ] Inclui explicitamente "não montar `fixed inset-0` na mão: é `Dialog`" e a exceção do `ErrorBoundary`, se a US-016 tiver decidido mantê-la.
- [ ] `client/CLAUDE.md` aponta para o documento, para virar contexto obrigatório de quem mexer em `client/`.
- [ ] Documento curto e operacional — não catálogo enfeitado.
- [ ] Nenhum código alterado.

---

#### US-021: Escrever o README da raiz (Épico 14 / T14.1)
**Descrição:** Como alguém que acabou de clonar o repositório, quero descobrir o que é o
projeto e como subi-lo, sem precisar ler `docs/` inteiro.

**Contexto:** `README.md` tem **15 bytes**, enquanto `docs/` acumulou material denso e
correto (`ARCHITECTURE.md`, `DEPLOY.md` com 949 linhas, `FLYWAY.md`, `SCHEMA_BASELINE.md`,
`BACKLOG.md`, `POST-MVP-TASK.md`).

**Critérios de aceite:**
- [ ] O que é o app, em dois parágrafos.
- [ ] Stack em uma tabela: Java 21 + Spring Boot + Postgres | React + TS + Vite + Bun | Docker + Nginx na VPS.
- [ ] Como subir local: `docker compose -f docker-compose-dev.yml up`, `./mvnw spring-boot:run`, `bun install && bun run dev` — com as variáveis obrigatórias e **o que acontece se faltarem** (fail-fast do `JWT_SECRET` e da chave do TMDB).
- [ ] Como rodar os testes de cada lado.
- [ ] Mapa do `docs/`: **uma linha por arquivo** dizendo quando consultar cada um.
- [ ] Estrutura do repositório em um parágrafo (`api/`, `client/`, `deploy/`, `scripts/`, `tasks/`).
- [ ] **Nenhum conteúdo copiado de `docs/`** — só referência. README que duplica documentação desatualiza primeiro e passa a mentir.
- [ ] Nenhum valor real de segredo aparece.
- [ ] Comandos **testados de verdade**, não escritos de memória.
- [ ] Alguém que nunca viu o projeto sobe o ambiente local seguindo só o README — **[gate humano]**, exige uma pessoa que não conhece o repo.

---

## 5. Requisitos funcionais

**Tokens e cor**
- FR-1: `index.css` deve declarar em `oklch` tokens semânticos para as 8 cores mais frequentes da paleta real, cada um com comentário do papel que exerce neste app.
- FR-2: Nenhum arquivo `.tsx` fora de `components/ui/` deve conter literal hexadecimal, exceto SVG declaradamente ilustrativo com comentário justificando.
- FR-3: Nenhum arquivo `.tsx` fora de `components/ui/` deve conter utilitário de cor arbitrário (`bg-[…]`, `text-[…]`, `border-[…]`, …) sem justificativa comentada.

**Portão de CI**
- FR-4: O CI deve falhar quando um literal de cor novo aparecer fora de `components/ui/`.
- FR-5: A mensagem de falha deve indicar **qual token** usar no lugar da cor crua.
- FR-6: O portão deve nascer com allowlist contendo exatamente os arquivos ainda não migrados, e essa lista deve encolher a cada story de migração até ficar vazia.
- FR-7: Depois da US-014, o portão deve barrar também `rounded-[…]` novo.

**Arredondamento**
- FR-8: Toda ocorrência de `rounded-[Npx]` cujo valor bata exato com um token da escala deve usar o token.
- FR-9: Ocorrências sem equivalência exata só podem ser alteradas depois de decisão registrada (US-013), porque a alteração move pixels.

**Primitivos**
- FR-10: Todo elemento clicável com aparência de botão deve usar o primitivo `Button`; se nenhuma variante servir, `button.tsx` deve ser estendido.
- FR-11: Todo modal deve usar o primitivo `Dialog`, salvo exceção documentada no código e no `DESIGN-SYSTEM.md`.
- FR-12: Deve existir primitivo para pill/badge em `components/ui/`, e os usos manuais devem consumi-lo.

**Tema**
- FR-13: O app deve assumir tema escuro fixo; `ThemeProvider`, `useTheme` e os tokens `:root` claros não utilizados devem ser removidos.

**Documentação**
- FR-14: Deve existir `client/docs/DESIGN-SYSTEM.md` cobrindo inventário de primitivos, critério de variante e tabela de tokens, referenciado por `client/CLAUDE.md`.
- FR-15: O `README.md` da raiz deve permitir subir o ambiente local sem consultar `docs/`, e mapear `docs/` sem duplicar seu conteúdo.

---

## 6. Não-objetivos (fora do escopo)

- **Redesenhar qualquer tela.** Só a origem do valor muda (hex → token), não o valor.
- **Escolher cor nova.** A paleta é a que já existe, medida; a US-001 a nomeia, não a inventa.
- **Entregar tema claro funcionando.** Decidido dark-only (D17). Continua possível depois, e mais barato por causa deste épico — mas não é objetivo agora.
- **Alternador de tema, tema por casal, transição animada entre temas.**
- **Mudar o `--radius` base**, o que moveria a escala inteira.
- **Lint de espaçamento, tipografia ou ordem de classes** — `prettier-plugin-tailwindcss` já cobre ordenação.
- **Storybook, site de documentação, catálogo navegável de componentes.**
- **Redesenhar o conteúdo interno de qualquer modal**, ou mudar quando cada um abre e fecha.
- **Redefinir o significado visual de cada status** na US-017/US-018 — a migração troca a implementação, não decide de novo o que cada cor comunica.
- **Badges, screenshots, `CONTRIBUTING.md`, licença ou tradução** no README.
- **Migrar `components/ui/` para tokens.** É a camada que define o token em termos concretos; hex ali é legítimo.

---

## 7. Considerações de design

**Onde a skill `frontend-design` é obrigatória e onde é dispensada (D11/E13.4).** A regra
do `CLAUDE.md` da raiz exige a skill para qualquer implementação em `client/`, com exceção
de refatoração pixel-idêntica. Este PRD aplica o critério objetivo:

| Stories | Skill | Motivo |
|---|---|---|
| US-001 … US-012, US-015, US-016, US-019 | **Dispensada** | Refatoração pura: o resultado deve ser pixel-idêntico. Nenhuma decisão de design é tomada — se um pixel muda, é bug de mapeamento. |
| US-013, US-014 | **Obrigatória** | Snapar raio move 1–2px. É decisão de design disfarçada de refatoração, e tratá-la como mecânica seria "corrigir" o design sem dizer. |
| US-017 | **Obrigatória** | Criar primitivo é decisão de design: quais variantes existem, quais tokens usam, quais tamanhos. |
| US-018, US-020, US-021 | **Dispensada** | US-018 consome decisão já tomada na US-017; as outras duas são documentação. |

**Fonte de verdade visual.** O protótipo em `docs/design/claude-design-project/Sessao a
Dois.dc.html` é a origem dos valores hoje espalhados — ele usa `style=` inline com px e hex
crus, que foram copiados literalmente para as classes. Ao mapear token, o protótipo é a
referência do **valor**; o `index.css` passa a ser a referência do **nome**.

**Ordem de migração por volume decrescente.** Começar pelos arquivos mais afetados
(`ComparisonDialog` 110, `MediaDetailModal` 103) entrega o maior retorno por revisão e
expõe cedo qualquer lacuna do mapeamento de token — melhor descobrir que falta um token no
primeiro arquivo do que no quadragésimo.

---

## 8. Considerações técnicas

**A rede de testes existe e é adequada.** O Épico 11 (T11.1–T11.4, US-001 a US-018,
concluído em 2026-08-12) cobre guards de rota, `HubScreen`/`TrackSection` e
`SearchTab`/`SuggestionsTab` com testes que consultam **por papel e texto acessível, não
por classe CSS**. Isso é exatamente o que a migração precisa: os testes são indiferentes à
troca de `bg-[#161513]` por `bg-card` e quebram se a estrutura mudar. Rodar a suíte a cada
arquivo migrado é a rede que torna 53 arquivos viável.

**Limite conhecido dessa rede:** ela **não** detecta diferença de 1–2px de raio nem cor
trocada por outra cor válida. Por isso os critérios visuais são gate humano (D19) e por
isso a US-013 existe como decisão separada.

**Tailwind 4 e `@theme inline`.** Os tokens de raio estão em `@theme inline`
(`index.css:56-64`), o que faz o utilitário gerado carregar o `calc(...)` literal em vez de
referenciar `var(--radius-*)`. Os utilitários `rounded-sm` … `rounded-4xl` existem e já são
usados corretamente em `card.tsx`, `input.tsx`, `popover.tsx`, `select.tsx` e `tooltip.tsx`
— não há nada a configurar, só a consumir.

**Biblioteca de base.** Os primitivos embrulham `@base-ui/react` (`dialog`, `select`,
`popover`, `tooltip`, `collapsible`, `toggle-group`, `scroll-area`, `slider`) e
`react-day-picker` (`calendar`). Nenhum arquivo fora de `components/ui/` importa essas
libs diretamente — a camada de abstração está íntegra e deve continuar assim.

**`ErrorBoundary` e ordem de montagem.** O comentário em `ErrorBoundary.tsx:16` registra
que ele é montado **por fora** do `ThemeProvider` e do `BrowserRouter`, justamente para
capturar exceção vinda de fora do router. A US-019 remove o `ThemeProvider` (o comentário
precisa ser atualizado) e a US-016 avalia se `Dialog` é seguro nesse ponto da árvore.

**Sem navegador no sandbox.** Limitação já registrada em `client/CLAUDE.md` e nas notas da
US-006 do Épico 12. Todo critério que exige olho humano está marcado **[gate humano]** e
fica `- [ ]` com o motivo ao lado, no padrão que `POST-MVP-TASK.md` já adota — nunca
marcado por otimismo.

**Remedir sempre.** Comandos de referência (a partir de `client/src`, excluindo
`components/ui/` e testes):

```bash
# hex
grep -rno "#[0-9a-fA-F]\{6\}" --include="*.tsx" . | grep -v "components/ui/" | grep -vE "test|spec" | wc -l
# utilitários de cor arbitrários
grep -rno "\(bg\|text\|border\|ring\|from\|to\|via\|shadow\|fill\|stroke\)-\[[^]]*\]" --include="*.tsx" . | grep -v "components/ui/" | grep -vE "test|spec" | wc -l
# raio arbitrário
grep -rno "rounded[a-z-]*-\[[^]]*\]" --include="*.tsx" . | grep -v "components/ui/" | grep -vE "test|spec" | wc -l
```

---

## 9. Métricas de sucesso

| Métrica | Antes (2026-08-13) | Meta |
|---|---|---|
| Literais hex fora de `components/ui/` | 831 | **0** (salvo exceção comentada) |
| Utilitários de cor arbitrários | 1376 | punhado justificado caso a caso |
| `rounded-[…]` arbitrário | 87 | **0** (salvo decisão (c) da US-013) |
| `<button>` cru | 13 | **0** |
| Modais com backdrop manual | 9 | **0 ou 1** (exceção do `ErrorBoundary`, se justificada) |
| Pills manuais | 36 usos / 18 arquivos | **0** |
| Arquivos na allowlist do portão | 53 (no dia 1) | **0** |
| Trocar a cor primária do produto | 182 edições | **1** |
| `README.md` | 15 bytes | sobe o ambiente sem consultar `docs/` |

**Métrica de processo, a mais importante:** um mês depois de fechado o épico, as contagens
continuam em zero. É o que distingue "limpamos uma vez" de "instalamos governança" — e é
exatamente o que o crescimento de 21%/33% em uma semana mostrou que não acontece sozinho.

---

## 10. Questões em aberto

1. **Qual valor de `rounded-[3px]` (4 usos)?** Provavelmente detalhe decorativo (barra, indicador), onde o vizinho `sm`=6px dobraria o raio. Resolvido na US-013, mas pode virar exceção permanente em vez de snap.
2. **Um primitivo de pill ou dois?** A amostra sugere `Badge` estático + chip de filtro interativo como componentes distintos. Fechado na US-017, depois do agrupamento dos 36 usos.
3. **O `SelectTrigger` estilizado como pill (`FiltersPanel.tsx:117`) vira variante do primitivo novo ou continua `Select` com classe?** Decisão explícita exigida na US-017.
4. **A allowlist do portão deve viver no ESLint ou num script de CI?** A US-002 escolhe a mais simples que funcionar; a decisão afeta o quanto a US-011 tem de desmontar depois.
5. **Os gráficos do `DashboardScreen` usam cor de série fora da paleta semântica?** Se sim, a US-009 precisa dos tokens `--chart-*` (que já existem em `index.css`) — confirmar na execução.
6. **Vale estender o portão para tipografia** (tamanhos `text-[13px]` aparecem na amostra de pills)? Fora do escopo deste PRD, mas o padrão é o mesmo e a infraestrutura da US-002 serviria. Registrar como candidato a épico futuro se a contagem justificar.
