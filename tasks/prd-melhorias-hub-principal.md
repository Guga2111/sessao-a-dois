# PRD: Melhorias no Hub Principal (Sessão a Dois)

## Introdução/Visão Geral

O Hub Principal (`client/src/screens/HubScreen.tsx`) é a tela onde o casal vê seus títulos
organizados em três seções por status: **Assistindo Atualmente** (`WATCHING`),
**Queremos Ver** (`WANT_TO_SEE`) e **Já Vimos** (`WATCHED`). Cada card representa um
`MediaTrack` compartilhado pelo casal.

Hoje o Hub tem três limitações:
1. Não há como remover um título da lista pela UI.
2. Todos os títulos de todas as seções são carregados de uma vez (`GET /api/tracking`),
   o que não escala quando o casal acumula muitos títulos.
3. O fluxo de status não é linear: em "Queremos Ver" o botão "Marcar como visto" pula
   direto para "Já Vimos", e em "Já Vimos" não há como reeditar uma avaliação.

Este PRD cobre três funcionalidades para resolver isso, mantendo o design atual
(Shadcn UI + Tailwind, tema dark, acento `#ffcb2b`) e o modelo de dados existente
(`com.app.tracking`).

## Goals

- Permitir excluir um título do Hub em qualquer das três seções, com confirmação, refletindo
  a remoção imediatamente na UI e no contador da seção.
- Paginar cada seção de forma independente, carregando no máximo 20 títulos por vez, sem buscar
  tudo de uma vez, mantendo o contador total visível por seção.
- Tornar o fluxo de status linear e explícito:
  `Queremos Ver → Assistindo Atualmente → Já Vimos`, e permitir reeditar a avaliação em
  "Já Vimos" sem mudar o status.
- Não introduzir regressão visual: reaproveitar componentes e o padrão de cores inline do protótipo.

## Decisões já tomadas (respostas do solicitante)

- **UserReviews ao excluir:** exclusão em cascata — remover o `MediaTrack` remove permanentemente
  as `UserReview` de ambos os membros. (Já é o comportamento do banco: `MediaTrack.reviews` usa
  `cascade = ALL, orphanRemoval = true`.)
- **UI de paginação:** botão **"Carregar mais"** (acumula +20 por clique, mantém os já exibidos).
- **Reavaliar sem review prévia do usuário atual:** abrir o modal **vazio** e criar a review ao
  salvar (sem mudar status), reutilizando `PUT /api/tracking/{id}/review`.
- **Permissão para excluir:** qualquer membro do casal pode excluir (escopo por `couple_id`,
  já é o comportamento atual do `deleteTrack`).

## User Stories

### US-001: Endpoint de exclusão acessível e coberto por teste
**Description:** Como desenvolvedor, quero garantir que a exclusão de um `MediaTrack`
(e suas reviews em cascata) funcione via API para que o frontend possa consumi-la com segurança.

**Contexto:** `DELETE /api/tracking/{id}` já existe (`MediaTrackController.delete` →
`MediaTrackService.deleteTrack`) e o cascade/orphanRemoval já remove as reviews. Esta story é de
**verificação e cobertura**, não de nova implementação de endpoint.

**Acceptance Criteria:**
- [ ] Confirmar que `DELETE /api/tracking/{id}` retorna `204 No Content` ao excluir um track do
      próprio casal.
- [ ] Confirmar que excluir um track remove em cascata as `UserReview` de ambos os membros
      (nenhuma linha órfã em `user_review`).
- [ ] Excluir um `id` inexistente ou de outro casal retorna `404` (mensagem genérica
      "titulo nao encontrado", sem vazar existência).
- [ ] Teste em `MediaTrackServiceTest` e/ou `MediaTrackControllerTest` cobrindo: exclusão OK,
      cascade das reviews, e 404 para track de outro casal.
- [ ] `./mvnw test` passa.

### US-002: Ação de excluir no card com confirmação
**Description:** Como membro do casal, quero excluir um título direto do card, com uma confirmação
antes, para remover algo adicionado por engano ou que não interessa mais.

**Acceptance Criteria:**
- [ ] Cada `MediaCard` das três seções expõe uma ação de excluir (ex.: ícone de lixeira / item
      em menu no card), sem disparar o clique de abertura do detalhe (`event.stopPropagation()`).
- [ ] Ao acionar, abre um diálogo de confirmação (reusar/usar `AlertDialog` do Shadcn ou um modal
      no mesmo padrão visual do `WatchModal`) deixando claro que a ação é **compartilhada** entre
      os dois usuários e **irreversível** (remove também as avaliações).
- [ ] Confirmar chama `DELETE /api/tracking/{id}`; cancelar fecha sem efeito.
- [ ] Em caso de sucesso, o card some da UI e o contador da seção decrementa **imediatamente**.
- [ ] Em caso de erro (rede/servidor), exibe mensagem de erro e mantém o card na lista.
- [ ] Consistência visual com tema dark / acento `#ffcb2b`; ação destrutiva com cor de alerta.
- [ ] Typecheck (`tsc -b`) e lint passam.
- [ ] Verificar no browser usando a skill dev-browser.

### US-003: Suporte a paginação (page/size) no endpoint de listagem
**Description:** Como desenvolvedor, quero que `GET /api/tracking` aceite `status`, `page` e `size`
e retorne uma página de resultados junto do total daquele status, para o frontend paginar por seção.

**Acceptance Criteria:**
- [ ] `GET /api/tracking?status={STATUS}&page={n}&size={20}` retorna no máximo `size` itens
      (default `size=20`, `page=0`) daquele status, ordenados de forma estável (ex.: `createdAt` desc).
- [ ] A resposta expõe o **total** de itens do status (para o contador da seção) além dos itens da
      página — ex.: retornar `Page<MediaTrackResponse>` (Spring Data `Page` com `content` +
      `totalElements`) OU um DTO `{ items, total, page, size }`. Documentar a forma escolhida.
- [ ] Adicionar método paginado em `MediaTrackRepository` (ex.: `findByCoupleIdAndStatus(coupleId,
      status, Pageable)`), sempre com escopo `couple.id` derivado do JWT (nunca do cliente).
- [ ] Chamada **sem** `status` continua compatível (comportamento atual) OU é ajustada e
      documentada; a decisão não pode quebrar `DashboardScreen`/`MatchScreen` se consumirem o endpoint.
- [ ] `size` é limitado a um máximo sensato no servidor (ex.: teto de 20–50) para evitar abuso.
- [ ] Testes de repositório e controller cobrindo: primeira página, segunda página, total correto,
      e `size` default.
- [ ] Atualizar o tipo TS em `client/src/types/tracking.ts` para refletir a nova forma de resposta.
- [ ] `./mvnw test` passa.

### US-004: Paginação por seção com "Carregar mais" no Hub
**Description:** Como membro do casal, quero que cada seção carregue 20 títulos por vez com um botão
"Carregar mais", para a tela não travar quando tivermos muitos títulos.

**Acceptance Criteria:**
- [ ] Cada seção (`WATCHING`, `WANT_TO_SEE`, `WATCHED`) busca sua própria página via
      `GET /api/tracking?status=...&page=...&size=20`, de forma **independente**.
- [ ] Cada seção mantém seu próprio estado de `page`, itens acumulados e flag "tem mais".
- [ ] O botão "Carregar mais" aparece apenas quando `itens exibidos < total` da seção; ao clicar,
      concatena os próximos 20 aos já exibidos (não substitui).
- [ ] O contador ao lado do título da seção mostra o **total** do status (não apenas os carregados).
- [ ] Estados de loading (skeletons na carga inicial; indicador no botão durante "Carregar mais").
- [ ] O layout de grid atual (`repeat(auto-fill,minmax(250px,1fr))`) e o `Collapsible` por seção
      são preservados.
- [ ] Typecheck e lint passam.
- [ ] Verificar no browser usando a skill dev-browser.

### US-005: Endpoint de transição de status WANT_TO_SEE → WATCHING
**Description:** Como desenvolvedor, quero um endpoint para mover um título de "Queremos Ver" para
"Assistindo Atualmente" sem exigir avaliação, para suportar o botão "Começar a assistir".

**Contexto:** Hoje só existe `PATCH /api/tracking/{id}/watch` (vai direto para `WATCHED` e grava
review). Não há transição para `WATCHING`.

**Acceptance Criteria:**
- [ ] Novo endpoint (ex.: `PATCH /api/tracking/{id}/status` recebendo `{ "status": "WATCHING" }`,
      ou `PATCH /api/tracking/{id}/start`) que altera o status do track para `WATCHING`.
- [ ] Não cria nem exige `UserReview`; não define `watchedDate`.
- [ ] Valida escopo por `couple_id` (404 se não pertencer ao casal), como os demais endpoints.
- [ ] (Recomendado) Validar que a transição só é permitida a partir de `WANT_TO_SEE` — definir se
      transições inválidas retornam `400` ou são idempotentes; documentar a decisão.
- [ ] Retorna o `MediaTrackResponse` atualizado.
- [ ] Testes de service e controller cobrindo a transição e o caso de track de outro casal (404).
- [ ] `./mvnw test` passa.

### US-006: Botão "Começar a assistir" em "Queremos Ver"
**Description:** Como membro do casal, quero, em "Queremos Ver", um botão "Começar a assistir" que
move o título para "Assistindo Atualmente", para refletir que começamos a ver.

**Acceptance Criteria:**
- [ ] No `MediaCard`, quando `status === "WANT_TO_SEE"`, o botão atual "Marcar como visto" é
      **substituído** por "Começar a assistir".
- [ ] Clicar chama o endpoint da US-005 (`WANT_TO_SEE → WATCHING`) sem abrir o modal de avaliação.
- [ ] Em sucesso, o card sai de "Queremos Ver" e aparece em "Assistindo Atualmente"; os contadores
      das duas seções se atualizam.
- [ ] Erro exibe feedback e mantém o card onde estava.
- [ ] Estilo consistente com o CTA atual do card (botão full-width no rodapé).
- [ ] Typecheck e lint passam.
- [ ] Verificar no browser usando a skill dev-browser.

### US-007: Manter "Marcar como assistido" em "Assistindo Atualmente"
**Description:** Como membro do casal, quero, em "Assistindo Atualmente", manter o botão
"Marcar como assistido" que move o título para "Já Vimos" e abre a avaliação.

**Contexto:** Fluxo já existente via `WatchModal` + `PATCH /{id}/watch`. Hoje esse CTA só é
renderizado para `WANT_TO_SEE`; precisa aparecer para `WATCHING`.

**Acceptance Criteria:**
- [ ] No `MediaCard`, quando `status === "WATCHING"`, existe o botão "Marcar como assistido".
- [ ] Clicar abre o `WatchModal` (estrelas + opinião, opcionais) e, ao confirmar,
      `PATCH /{id}/watch` move para `WATCHED`.
- [ ] Em sucesso, o card sai de "Assistindo Atualmente" e aparece em "Já Vimos"; contadores atualizam.
- [ ] Typecheck e lint passam.
- [ ] Verificar no browser usando a skill dev-browser.

### US-008: Botão "Reavaliar" em "Já Vimos"
**Description:** Como membro do casal, quero, em "Já Vimos", um botão "Reavaliar" que reabre o modal
de avaliação para editar minha avaliação (estrelas, data e opinião) **sem** mudar o status.

**Acceptance Criteria:**
- [ ] No `MediaCard`, quando `status === "WATCHED"`, existe o botão "Reavaliar".
- [ ] Clicar abre um modal de avaliação **pré-preenchido** com a review atual do usuário logado
      (estrelas, opinião; e a data assistida, se o modal a expuser).
- [ ] Se o usuário logado **ainda não tem review** neste título, o modal abre **vazio** e cria a
      review ao salvar (sem alterar o status). Reutiliza `PUT /api/tracking/{id}/review`.
- [ ] Salvar chama `PUT /api/tracking/{id}/review` e **não** altera `status` nem `watchedDate` do track.
- [ ] Após salvar, o card em "Já Vimos" reflete a nova nota/opinião; o título permanece na mesma seção.
- [ ] Editar/permitir data assistida: definir se o "Reavaliar" edita `watchedDate`. (Ver Open Questions.)
- [ ] Consistência visual com o `WatchModal` (mesmo layout de estrelas/opinião, tema dark, acento).
- [ ] Typecheck e lint passam.
- [ ] Verificar no browser usando a skill dev-browser.

## Functional Requirements

**Exclusão**
- FR-1: Cada card das três seções deve oferecer ação de excluir sem disparar a abertura do detalhe.
- FR-2: A exclusão deve exigir confirmação explícita antes de chamar a API, avisando que é
  compartilhada e irreversível (remove também as avaliações).
- FR-3: A exclusão deve chamar `DELETE /api/tracking/{id}` e remover as `UserReview` em cascata.
- FR-4: Após excluir, a UI deve remover o card e decrementar o contador da seção imediatamente.

**Paginação**
- FR-5: `GET /api/tracking` deve aceitar `status`, `page` e `size` (default `page=0`, `size=20`)
  e retornar uma página de resultados daquele status.
- FR-6: A resposta paginada deve incluir o total de itens do status, para o contador da seção.
- FR-7: Cada seção do Hub deve paginar de forma independente, carregando +20 por "Carregar mais".
- FR-8: O contador de cada seção deve exibir o total do status, não apenas os itens carregados.
- FR-9: A query de repositório deve sempre escopar por `couple.id` do JWT.

**Fluxo de status**
- FR-10: Deve existir endpoint para transição `WANT_TO_SEE → WATCHING` sem exigir/gravar review.
- FR-11: Em "Queremos Ver", o CTA deve ser "Começar a assistir" (substitui "Marcar como visto")
  e mover para "Assistindo Atualmente".
- FR-12: Em "Assistindo Atualmente", o CTA "Marcar como assistido" deve abrir o `WatchModal` e
  mover para "Já Vimos" via `PATCH /{id}/watch`.
- FR-13: Em "Já Vimos", o botão "Reavaliar" deve abrir o modal de avaliação pré-preenchido (ou vazio,
  se o usuário não tiver review) e salvar via `PUT /{id}/review`, sem alterar o status.

## Non-Goals (Out of Scope)

- Soft delete / arquivamento de títulos (a exclusão é definitiva, em cascata).
- Restringir exclusão a "quem adicionou" (qualquer membro do casal pode excluir).
- Undo/lixeira/restauração de títulos excluídos.
- Paginação numérica ("1 2 3 …") — a UI é apenas "Carregar mais".
- Filtros, busca ou ordenação configurável dentro das seções.
- Transições de status "para trás" (ex.: WATCHED → WATCHING) ou remoção do fluxo linear.
- Reavaliar a review do parceiro (cada usuário só edita a própria).
- Sincronização em tempo real (WebSocket) das mudanças entre os dois dispositivos do casal.

## Design Considerations

- Fonte da verdade visual: `docs/design/claude-design-project/Sessao a Dois.dc.html`. Cores/espaçamentos
  inline via Tailwind arbitrary values (`bg-[#161513]`, `border-[rgba(255,255,255,.07)]`, acento
  `#ffcb2b`, verde `#3ddc97` para "assistido", laranja `#ff9e2c` para "assistindo").
- Reusar componentes: `Button`, `Collapsible`, `WatchModal` (base para "Reavaliar"), skeletons já
  existentes em `HubScreen`. Para confirmação de exclusão, usar `AlertDialog` do Shadcn (ou modal no
  padrão do `WatchModal`).
- A ação destrutiva (excluir) deve ter tratamento visual de alerta, distinto dos CTAs positivos.
- Botões de CTA no card mantêm o padrão full-width no rodapé do card (como o atual "Marcar como visto").

## Technical Considerations

- Backend em `com.app.tracking`:
  - Reaproveitar `MediaTrackController` / `MediaTrackService` / `MediaTrackRepository` /
    `UserReviewService`.
  - `DELETE /api/tracking/{id}` e cascade das reviews **já existem** — foco em verificação/testes.
  - `PUT /api/tracking/{id}/review` (`UpsertReviewRequest`) **já existe** e cobre "Reavaliar"
    (cria ou edita a review do usuário logado sem mexer no status).
  - Novo: método paginado no repositório (`Pageable`) e ajuste do `GET /api/tracking` para
    `page/size` + total.
  - Novo: endpoint de transição `WANT_TO_SEE → WATCHING`.
  - Lembrar do gotcha de testes: `src/test/resources/application.properties` substitui o principal;
    não adicionar propriedade obrigatória sem espelhar.
- Frontend em `client/`:
  - Manter tipos TS em `types/tracking.ts` sincronizados manualmente com os records do backend
    (sem codegen), incluindo a nova forma de resposta paginada.
  - Usar `frontend-design` skill antes de escrever/alterar código em `client/` (regra do projeto).
  - Gotcha do sandbox: `vite build`/`npm run dev` pode falhar por binding do rolldown; verificar com
    `tsc -b` + `eslint` e sinalizar se o dev server não pôde ser executado de fato.

## Casos de Borda (Edge Cases)

- **Seção vazia:** manter a `emptyMessage` atual; não mostrar "Carregar mais"; contador = 0.
- **Exclusão durante paginação:** ao excluir um item de uma seção já paginada, decrementar o total e
  os itens exibidos sem recarregar tudo; garantir que "Carregar mais" continue coerente (não pular
  nem duplicar itens). Considerar reconsultar a página atual se necessário para não deixar buracos.
- **Reavaliar item sem review prévia do usuário logado:** abrir modal vazio e criar a review ao salvar
  (não deve dar erro nem exigir review preexistente).
- **Excluir título que o parceiro está vendo ao mesmo tempo:** a exclusão é do casal; após excluído,
  novas chamadas ao `id` retornam 404 (a outra sessão só reflete ao recarregar — sem realtime).
- **"Começar a assistir" em item que já mudou de status em outra sessão:** definir se retorna 400 ou
  é idempotente (ver US-005 / Open Questions).
- **Total vs. carregados:** o contador sempre mostra o total do status; se o total mudar (exclusão),
  o contador deve refletir.
- **`size` fora do intervalo:** servidor limita ao teto definido.

## Success Metrics

- É possível excluir um título em ≤ 2 cliques (ação + confirmar), com remoção imediata na UI.
- Cada seção nunca renderiza mais de 20 cards por página; "Carregar mais" traz exatamente os próximos 20.
- O fluxo `Queremos Ver → Assistindo Atualmente → Já Vimos` é percorrível inteiramente pela UI, e
  "Reavaliar" edita a avaliação sem mudar o status.
- Sem regressão visual em relação ao protótipo (tema dark, acento `#ffcb2b`).
- `./mvnw test` e `tsc -b` + lint passam.

## Open Questions

- Forma da resposta paginada: `Page<MediaTrackResponse>` do Spring Data vs. DTO próprio
  `{ items, total, page, size }`? (Afeta o tipo TS e o parsing no frontend.)
- Ordenação dentro de cada seção: `createdAt desc`? Para "Já Vimos", ordenar por `watchedDate`?
- "Reavaliar" deve permitir editar a **data assistida** (`watchedDate`) além de nota/opinião? Hoje
  `watchedDate` é do track (não da review) e `PUT /{id}/review` não a altera — se for necessário
  editar a data, é preciso decidir onde isso é gravado.
- Transição de status inválida (ex.: "Começar a assistir" num item que já é `WATCHING`/`WATCHED`):
  retornar `400` ou tratar como idempotente?
- O `GET /api/tracking` sem `status` (usado hoje pelo Hub e possivelmente por outras telas) deve
  permanecer não paginado por compatibilidade, ou todas as telas migram para o formato paginado?
</content>
</invoke>
