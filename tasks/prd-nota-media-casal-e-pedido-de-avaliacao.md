# PRD: Nota média do casal (com tooltip) + Pedido de avaliação via notificação

> App: **Sessão a Dois** — tracker de filmes/séries para casais.
> Escopo: **full-stack** (`api/` Spring Boot + `client/` React/Vite/Zustand/Shadcn).
> Este PRD está ancorado no código existente. **Não reconstruir** o que já está pronto (entidades, notificações, cálculo de `coupleAvg`, endpoints de review) — apenas estender.

---

## 1. Introdução / Visão geral

Hoje o card de um título (`client/src/components/MediaCard.tsx`) já sabe separar `myReview` / `partnerReview` e já calcula `coupleAvg` (média dos `reviews` com `rating`), mas **falta a UI de tooltip** que revele a nota individual de cada membro. Além disso, quando um membro avalia um título como "Já Vimos", **o outro membro não é convidado a dar a sua própria nota** — a avaliação a dois fica incompleta e silenciosa.

Esta feature entrega duas coisas complementares:

1. **Nota no card como média do casal + tooltip individual** — comportamento de exibição já quase pronto no front; falta só o tooltip com a nota de cada um.
2. **Fluxo de solicitação de avaliação via notificação** — quando um membro (o "avaliador") registra sua nota em um título, o outro membro (o "parceiro pendente") recebe uma notificação pedindo que ele também avalie. Clicar na notificação abre um `Dialog` (shadcn) para atribuir nota+opinião. O botão **"Reavaliar"** só fica disponível para o parceiro **depois** que ele deu sua nota inicial.

### Resultado esperado
- Cards mostram a média do casal quando os dois avaliaram, ou a nota individual quando só um avaliou, sempre com tooltip detalhando cada membro.
- Ninguém "esquece" de avaliar: o parceiro é notificado e tem um caminho de 1 clique para dar sua nota.
- A notificação de pedido se resolve sozinha (marca como lida) assim que o parceiro avalia.

---

## 2. Objetivos (mensuráveis)

- Exibir no card a **média** (dois avaliadores) ou a **nota individual** (um avaliador), com **tooltip** mostrando a nota de cada membro.
- Formato numérico da média fixo: **uma casa decimal, separador vírgula** (ex.: `4,3`; inteiros mostram `4,0`) — coerente com o protótipo (`Nota média do casal: 4,3 ★`) e com o formato já usado no `Dashboard`/`StatsService`.
- Disparar **uma** notificação `RATING_REQUEST` **somente para o parceiro** sempre que um membro registra uma nota e o parceiro ainda não tem nota.
- Resolver (marcar como lida) automaticamente a notificação `RATING_REQUEST` pendente quando o parceiro registra sua nota.
- Bloquear o botão **"Reavaliar"** do card para um membro enquanto ele não tiver dado a nota inicial daquele título.
- Nenhuma regressão nos fluxos existentes de `MATCH`/`NO_MATCH`, de "marcar como assistido" e de "Reavaliar".

---

## 3. Personas / atores

- **Avaliador** — o membro do casal que registra sua nota primeiro (ex.: Ana). É o `actorUserId` da notificação gerada.
- **Parceiro pendente** — o outro membro do casal (ex.: Léo), que ainda não avaliou aquele título e é o `recipientUserId` da notificação.
- Os papéis são **por título**: em outro título os papéis podem se inverter.
- **Casal sem parceiro** — `Couple` com `user2Id == null`: não há parceiro pendente, então nenhuma `RATING_REQUEST` é criada.

---

## 4. Decisões fixadas (não reabrir)

Decisões do prompt + respostas de esclarecimento — tratar como requisitos:

- **D1.** Novo valor no enum: `NotificationType.RATING_REQUEST`.
- **D2.** A `RATING_REQUEST` é enviada **apenas ao parceiro**, nunca a quem avaliou.
- **D3.** O gatilho dispara em **todos os três** caminhos em que um membro registra nota: `MediaTrackService.markAsWatched` (PATCH `/watch`), `MediaTrackService.addTrack` com `status=WATCHED` e `rating != null` (POST), e `UserReviewService.upsertReview` (PUT `/review`).
- **D4.** **Não** disparar se o parceiro **já** possui nota naquele título (nada a pedir).
- **D5.** Ao atribuir a nota, a `RATING_REQUEST` pendente do parceiro é **resolvida/marcada como lida automaticamente**.
- **D6.** Nota parcial (só um avaliador): exibe a nota individual desse membro, **sem** rótulo de "média".
- **D7.** A **média continua sendo calculada no frontend** (`MediaCard` já faz; o DTO já traz os dois `rating`). **Nenhuma** mudança de DTO só por causa da média.
- **D8.** O componente de avaliação aberto pela notificação é um **`Dialog` do shadcn** (`client/src/components/ui/dialog.tsx`).
- **D9.** (Fallback de `title`) Se o TMDB estiver indisponível no disparo, gravar um **texto genérico** (`"seu título"`) — a notificação sempre é criada e a persistência da nota nunca falha por causa do TMDB.
- **D10.** (Entrada pelo card) Manter o botão **"Avaliar"** no card `WATCHED` como caminho alternativo à notificação, quando o usuário atual ainda não avaliou.
- **D11.** (Limpeza de órfãs) Ao **deletar** um `MediaTrack`, as `RATING_REQUEST` associadas são **removidas**.
- **D12.** (Sync entre dispositivos) A resolução automática da `RATING_REQUEST` **não** emite push STOMP de "resolvida" — resolução local + `unread-count` no próximo fetch. Fora do MVP.

---

## 5. User Stories

> Estimativa: cada story cabe em uma sessão focada. Stories de UI incluem verificação em navegador (dev-browser skill).

### Backend

#### US-B1: Adicionar `RATING_REQUEST` ao `NotificationType`
**Descrição:** Como desenvolvedor, preciso de um novo tipo de notificação para representar o pedido de avaliação.

**Critérios de aceite:**
- [ ] `NotificationType` (`api/.../notification/NotificationType.java`) passa a ter `MATCH, NO_MATCH, RATING_REQUEST`.
- [ ] **Given** o enum é persistido como `EnumType.STRING`, **when** uma `RATING_REQUEST` é salva, **then** a coluna `type` grava a string `"RATING_REQUEST"` (sem migração de dados dos registros existentes).
- [ ] Compila (`./mvnw -DskipTests package`).

#### US-B2: Carregar `mediaTrackId` na notificação
**Descrição:** Como front, preciso saber **qual track** abrir ao clicar numa `RATING_REQUEST`, já que a avaliação usa `PUT /api/tracking/{id}/review` (por `trackId`, não por `tmdbId`).

**Critérios de aceite:**
- [ ] `Notification` ganha coluna `media_track_id UUID` **nullable** (nulo para `MATCH`/`NO_MATCH`; preenchido para `RATING_REQUEST`).
- [ ] `NotificationDto` ganha `UUID mediaTrackId` (nulo quando não aplicável).
- [ ] **Given** uma `RATING_REQUEST` recém-criada, **when** ela é serializada no DTO/STOMP, **then** `mediaTrackId` traz o id do `MediaTrack` avaliado.
- [ ] **Given** um evento `MATCH`/`NO_MATCH`, **when** serializado, **then** `mediaTrackId` é `null` e o fluxo de match segue idêntico.
- [ ] Cobertura de teste do mapeamento DTO. Compila.

#### US-B3: Método de notificação a um único destinatário (o parceiro)
**Descrição:** Como serviço, preciso criar a notificação **só para o parceiro** — `notifyCouple(...)` cria uma por membro, o que não serve para `RATING_REQUEST`.

**Critérios de aceite:**
- [ ] Novo método em `NotificationService` (ex.: `notifyRatingRequest(Couple couple, UUID recipientUserId, UUID actorUserId, Long tmdbId, MediaType mediaType, String title, UUID mediaTrackId)`) que persiste **uma** `Notification` para `recipientUserId`.
- [ ] Reusa o padrão **persist-then-push-after-commit** já existente (`TransactionSynchronizationManager` → `afterCommit`) para o broadcast STOMP em `/topic/couple/{coupleId}/notifications`.
- [ ] **Given** o casal só tem `user1` (parceiro `null`), **when** o método é chamado sem destinatário válido, **then** nenhuma notificação é criada e nenhum push é enviado.
- [ ] Teste unitário (Mockito) cobrindo: cria 1 notificação; push após commit; caso destinatário nulo. Compila.

#### US-B4: Disparar `RATING_REQUEST` ao registrar uma nota (3 caminhos)
**Descrição:** Como parceiro pendente, quero ser notificado quando o outro membro avaliar um título que eu ainda não avaliei.

**Regra de disparo (todas as condições verdadeiras):**
1. O ator registrou `rating != null` naquele título.
2. O casal tem parceiro (`user2Id != null`).
3. O parceiro **não** tem `UserReview` com `rating != null` naquele `MediaTrack` (D4).
4. Não existe já uma `RATING_REQUEST` **não lida** para `(recipient=parceiro, couple, tmdbId)` (evita duplicatas).

**Critérios de aceite:**
- [ ] Existe um método orquestrador único (ex.: `RatingRequestService.onRatingRegistered(track, actorUserId)`) chamado ao final de `markAsWatched`, `addTrack` (quando `WATCHED` + `rating`) e `upsertReview`, dentro da mesma transação.
- [ ] O `title` da notificação é resolvido via `MediaDetailsService.getDetails(mediaType, tmdbId).title()`, seguindo o padrão "enriquecer via TMDB sem quebrar a persistência" (`catch (RuntimeException)` com fallback — ver Considerações Técnicas). A falha do TMDB **não** faz o registro da nota falhar.
- [ ] **Given** Ana marca um título como assistido com nota e Léo ainda não avaliou, **when** a transação commita, **then** existe exatamente 1 `RATING_REQUEST` para Léo (`actorUserId = Ana`, `recipientUserId = Léo`, `mediaTrackId` correto) e um push STOMP chega após o commit.
- [ ] **Given** Léo já tinha nota naquele título, **when** Ana avalia, **then** nenhuma `RATING_REQUEST` é criada (D4).
- [ ] **Given** o casal não tem parceiro, **when** Ana avalia, **then** nenhuma notificação é criada.
- [ ] **Given** já existe uma `RATING_REQUEST` não lida para Léo naquele título, **when** Ana avalia/reavalia de novo, **then** **não** é criada uma segunda (idempotência).
- [ ] **Given** Ana registra sem nota (`rating == null`), **when** a operação ocorre, **then** nenhuma `RATING_REQUEST` é criada.
- [ ] Testes de serviço cobrindo os 3 caminhos + as condições acima. Compila.

#### US-B5: Resolver automaticamente a `RATING_REQUEST` quando o parceiro avalia
**Descrição:** Como parceiro pendente, quando eu finalmente atribuo minha nota, o pedido pendente deve sumir do meu sino (marcado como lido) sem ação manual.

**Critérios de aceite:**
- [ ] No mesmo orquestrador, **antes/ao** registrar a nota do ator, quaisquer `RATING_REQUEST` **não lidas** cujo `recipientUserId == actorUserId` e mesmo `(couple, tmdbId)` são marcadas como lidas.
- [ ] Vale para os **3 caminhos** (`markAsWatched`, `addTrack WATCHED`, `upsertReview`).
- [ ] **Given** Léo tem uma `RATING_REQUEST` não lida para "Duna", **when** Léo dá sua nota (por qualquer caminho), **then** essa notificação fica `read = true` no banco.
- [ ] **Given** não há `RATING_REQUEST` pendente para o ator naquele título, **when** ele avalia, **then** a operação não falha e nada é alterado em notificações.
- [ ] Query de repositório dedicada para localizar as pendências (`findByRecipientUserIdAndTmdbIdAndTypeAndReadFalse` ou equivalente, escopada ao casal). Testes cobrindo resolução e no-op. Compila.

#### US-B6: Remover `RATING_REQUEST` ao deletar o track (D11)
**Descrição:** Como usuário, ao remover um título da lista, não quero que sobrem pedidos de avaliação apontando para um track inexistente.

**Critérios de aceite:**
- [ ] Em `MediaTrackService.deleteTrack` (DELETE `/api/tracking/{id}`), antes/junto da remoção do track, todas as `RATING_REQUEST` associadas àquele `mediaTrackId` (ou ao par `couple`+`tmdbId`) são apagadas, dentro da mesma transação.
- [ ] **Given** existe uma `RATING_REQUEST` para Léo em "Duna", **when** o título é deletado, **then** essa notificação não existe mais no banco.
- [ ] **Given** o track não tem `RATING_REQUEST` associada, **when** é deletado, **then** a operação segue normal (no-op de notificações) e o comportamento de delete existente é preservado.
- [ ] A limpeza cobre apenas `RATING_REQUEST` — notificações `MATCH`/`NO_MATCH` **não** são afetadas.
- [ ] Query de deleção dedicada no `NotificationRepository` (escopada por casal). Testes cobrindo deleção e no-op. Compila.

### Frontend

#### US-F1: Tipos TS de notificação
**Descrição:** Como front, preciso refletir o novo tipo e o novo campo do DTO.

**Critérios de aceite:**
- [ ] `client/src/types/notification.ts`: `NotificationType = "MATCH" | "NO_MATCH" | "RATING_REQUEST"`.
- [ ] Interface `Notification` ganha `mediaTrackId: string | null`.
- [ ] `tsc` passa.

#### US-F2: `Dialog` de avaliação (shadcn) a partir da notificação
**Descrição:** Como parceiro pendente, quero clicar na notificação e abrir um Dialog para dar minha nota e opinião.

**Critérios de aceite:**
- [ ] Novo componente (ex.: `RatingRequestDialog.tsx`) usando o **`Dialog` do shadcn** (`components/ui/dialog.tsx`), com campos: estrelas 1–5 (opcional) e opinião (opcional), no mesmo visual/tokens do `ReviewModal` (cores inline do protótipo).
- [ ] Submete via `PUT /api/tracking/{mediaTrackId}/review` (endpoint existente `upsertReview`) com `{ rating, opinion }` (`rating` `null` se 0).
- [ ] **Given** o Dialog aberto, **when** o parceiro salva uma nota válida, **then** a chamada retorna o `MediaTrackResponse` atualizado e o Dialog fecha.
- [ ] **Given** o `mediaTrackId` aponta para um track já removido, **when** o parceiro salva, **then** a resposta 404 é tratada com mensagem amigável ("Este título não está mais na sua lista.") e o Dialog não quebra.
- [ ] `tsc`/lint passam. Verificar no navegador com a dev-browser skill.

#### US-F3: Clique na `RATING_REQUEST` abre o Dialog + resolve a notificação
**Descrição:** Como parceiro pendente, clicar numa notificação de pedido deve abrir o Dialog (e não só marcar como lida como as demais).

**Critérios de aceite:**
- [ ] Em `NotificationDropdown.tsx`, o `onSelect` diferencia por `type`: `RATING_REQUEST` → abre `RatingRequestDialog` com o `mediaTrackId`/`title`/`mediaType` da notificação; demais tipos mantêm o comportamento atual (só `markAsRead`).
- [ ] Após salvar a nota com sucesso, a notificação correspondente é marcada como lida no `useNotificationStore` (badge do sino decrementa) e, se a Hub estiver montada, o track exibido é atualizado.
- [ ] **Given** uma `RATING_REQUEST` no dropdown, **when** clico nela, **then** o Dialog abre com o título correto; **when** salvo, **then** a notificação vira lida sem novo fetch obrigatório.
- [ ] `tsc`/lint passam. Verificar no navegador com a dev-browser skill.

#### US-F4: Texto e ícone da `RATING_REQUEST` no dropdown
**Descrição:** Como usuário, quero entender que aquela notificação é um pedido de avaliação.

**Critérios de aceite:**
- [ ] `notificationText(...)` cobre `RATING_REQUEST` (ex.: `"{actorName} avaliou \"{title}\" — dê sua nota também."`, com fallback `"Seu par"` quando `actorName` é nulo).
- [ ] `NotificationIcon` tem um ícone próprio para `RATING_REQUEST` (ex.: estrela) distinto de `MATCH` (coração) e `NO_MATCH` (X).
- [ ] `tsc`/lint passam. Verificar no navegador com a dev-browser skill.

#### US-F5: Tooltip da nota individual no `MediaCard`
**Descrição:** Como usuário, quero ver a nota de cada membro ao passar o mouse sobre a nota do card.

**Critérios de aceite:**
- [ ] Adicionar o primitivo shadcn **Tooltip** (`components/ui/tooltip.tsx`, hoje inexistente) e envolver o bloco de estrelas+nota do card.
- [ ] Conteúdo do tooltip lista **cada membro** com sua nota, ex.: `Ana: 4 ★ · Léo: 5 ★`. Membro sem nota aparece como `Léo: sem nota`.
- [ ] Os nomes vêm de `review.userName` (já presente no `ReviewDto`).
- [ ] **Given** os dois avaliaram, **when** passo o mouse sobre a nota, **then** o card mostra a **média** (`4,0`/`4,3`) e o tooltip mostra as duas notas individuais.
- [ ] **Given** só um avaliou, **when** passo o mouse, **then** o card mostra a **nota individual** desse membro (sem tratar como média) e o tooltip mostra a nota dele e "sem nota" para o outro.
- [ ] Formato numérico: uma casa decimal com vírgula (`coupleAvg.toFixed(1).replace(".", ",")`, já usado). Nada muda no cálculo de `coupleAvg` (D7).
- [ ] `tsc`/lint passam. Verificar no navegador com a dev-browser skill.

#### US-F6: Gate do "Reavaliar" no `MediaCard`
**Descrição:** Como parceiro que ainda não avaliou, não devo poder "Reavaliar" — devo primeiro dar minha nota inicial.

**Critérios de aceite:**
- [ ] Para `status === "WATCHED"`:
  - Se o **usuário atual não tem nota** naquele título (`myReview?.rating` ausente): o botão do card é **"Avaliar"** e abre o mesmo `RatingRequestDialog` (nota inicial).
  - Se o **usuário atual já tem nota**: o botão é **"Reavaliar"** e mantém o `ReviewModal` atual.
- [ ] **Given** Léo não avaliou "Duna" (que Ana já marcou como visto), **when** ele vê o card, **then** o botão é "Avaliar"; **when** ele salva a nota, **then** o botão passa a "Reavaliar".
- [ ] **Given** Léo já avaliou, **when** ele vê o card, **then** o botão é "Reavaliar" (comportamento atual intacto).
- [ ] `tsc`/lint passam. Verificar no navegador com a dev-browser skill.

---

## 6. Requisitos funcionais (numerados)

**Exibição da nota (frontend, D6/D7):**
- FR-1: Com **dois** `reviews` com `rating`, o card exibe a **média** (`coupleAvg`), formatada com 1 casa decimal e vírgula.
- FR-2: Com **um** `review` com `rating`, o card exibe a **nota individual** desse membro (sem rótulo/semântica de média).
- FR-3: O card exibe um **tooltip** (shadcn Tooltip) listando a nota de **cada membro** do casal; membro sem nota aparece como "sem nota".
- FR-4: O cálculo da média permanece **no frontend**; nenhum campo de média é adicionado ao `MediaTrackResponse`.

**Pedido de avaliação (backend):**
- FR-5: `NotificationType` inclui `RATING_REQUEST`.
- FR-6: `Notification`/`NotificationDto` carregam `mediaTrackId` (nullable; preenchido só em `RATING_REQUEST`).
- FR-7: Ao registrar uma nota (`markAsWatched`, `addTrack` WATCHED+rating, `upsertReview`), o sistema cria **1** `RATING_REQUEST` para o **parceiro** se, e somente se: ator tem `rating != null`; parceiro existe; parceiro **não** tem nota; e não há `RATING_REQUEST` não lida pendente para o mesmo `(parceiro, couple, tmdbId)`.
- FR-8: A `RATING_REQUEST` é enviada **apenas ao parceiro** (nunca ao ator).
- FR-9: O `title` da notificação é resolvido via TMDB (`MediaDetailsService`); se o TMDB falhar, grava o texto genérico `"seu título"` (D9) — a notificação é sempre criada e a persistência da nota nunca falha.
- FR-10: Ao registrar a nota do parceiro (qualquer um dos 3 caminhos), qualquer `RATING_REQUEST` não lida dele para aquele `(couple, tmdbId)` é marcada como **lida**.
- FR-11: O push STOMP da `RATING_REQUEST` reusa o padrão persist-then-push **after-commit** em `/topic/couple/{coupleId}/notifications`.
- FR-17: Ao deletar um `MediaTrack` (DELETE `/api/tracking/{id}`), o sistema remove as `RATING_REQUEST` associadas (D11); `MATCH`/`NO_MATCH` não são afetadas.
- FR-18: A resolução automática (FR-10) **não** emite push STOMP de "resolvida" — apenas o dispositivo que avaliou atualiza o store localmente (D12).

**Pedido de avaliação (frontend):**
- FR-12: Clicar numa notificação `RATING_REQUEST` abre o **`Dialog` shadcn** de avaliação, pré-carregando `mediaTrackId`/`title`/`mediaType`.
- FR-13: O Dialog salva via `PUT /api/tracking/{mediaTrackId}/review`.
- FR-14: Após salvar, a notificação é marcada como lida no `useNotificationStore` e o track visível na Hub é atualizado.
- FR-15: No card `WATCHED`, "Reavaliar" só aparece após o usuário atual ter nota; caso contrário aparece "Avaliar" abrindo o mesmo Dialog.
- FR-16: `NotificationDropdown` tem texto e ícone próprios para `RATING_REQUEST`.

---

## 7. Regras de negócio e edge cases

- **Casal sem parceiro (`user2Id == null`):** nenhuma `RATING_REQUEST` é criada (não há destinatário).
- **Parceiro já tinha nota antes do pedido:** não dispara (FR-7 / D4). Isso cobre o caso de ambos terem avaliado no cadastro.
- **Ator sem nota (`rating == null`):** não dispara.
- **Título deletado antes de avaliar:** ao deletar o track, as `RATING_REQUEST` associadas são removidas (FR-17). Ainda assim, por corrida (notificação em trânsito no STOMP ou já no dropdown de outro dispositivo), ao clicar e salvar o `PUT /review` pode retornar 404 — o Dialog trata isso com mensagem amigável sem quebrar.
- **Múltiplas avaliações/pedidos para o mesmo título:** idempotência — no máximo **uma** `RATING_REQUEST` não lida por `(parceiro, couple, tmdbId)`; reavaliações do ator não geram duplicatas.
- **Média quando notas são iguais:** `(4+4)/2 = 4,0` — exibido como `4,0` (sempre 1 casa decimal).
- **Média com decimais:** ex.: `(4+5)/2 = 4,5`; `(4+3+... )` não se aplica (máx. 2 avaliadores). Arredondamento das estrelas visuais continua via `Math.round` (comportamento atual de `Stars`), independente do número textual.
- **"Reavaliar" já existente:** inalterado para quem já tem nota. O gate só afeta quem ainda não avaliou (passa a ver "Avaliar").
- **Auto-resolução e múltiplos dispositivos:** o backend é a fonte da verdade (marca lida). O cliente do parceiro que salvou atualiza o store localmente. Sincronização em tempo real da resolução para uma **segunda aba/dispositivo** do mesmo parceiro → ver Perguntas em aberto.
- **Ordem ator/parceiro:** os papéis são por título e por evento; `actorUserId` é sempre quem registrou a nota que disparou o pedido.

---

## 8. Considerações técnicas

- **Backend — reuso de padrões (`api/CLAUDE.md`):**
  - `NotificationService.notifyCouple(...)` **não** serve para pedido (cria uma por membro); criar método de destinatário único e reusar o `afterCommit()` de `TransactionSynchronizationManager`.
  - `actorUserId` = usuário que registrou a nota disparadora.
  - Resolver o `title` via `MediaDetailsService.getDetails(mediaType, tmdbId).title()` dentro de `try/catch (RuntimeException)` com fallback (o mesmo padrão de `MediaTrackService.fetchGenreIds`). Fallback sugerido: usar um texto genérico (ex.: `"seu título"`) apenas para não gravar `title` nulo — **decisão de fallback listada em Perguntas em aberto**.
  - Novas queries agregadas/lookup como `@Query` no `NotificationRepository`, escopadas por casal/destinatário (nunca confiar em `coupleId`/`userId` de fora do JWT).
  - Coluna `media_track_id` nullable: com `ddl-auto`/schema gerenciado do projeto, garantir que registros antigos (`MATCH`/`NO_MATCH`) permaneçam válidos com valor nulo.
  - Testes obrigatórios (JUnit 5 + Mockito) para Service/Repository/Controller afetados; lembrar do `ReflectionTestUtils.setField(entity, "id", uuid)` para ids gerados.
- **Frontend — reuso de padrões (`client/CLAUDE.md`):**
  - Usar `Dialog` shadcn (padrão do `TitleModal`), **não** o backdrop hand-rolled do `ReviewModal`.
  - Adicionar o primitivo `components/ui/tooltip.tsx` (não existe hoje) via shadcn; cores/spacing inline do protótipo, sem novos tokens.
  - Manter DTOs TS em sincronia manual com os records Java (sem codegen).
  - Reuso do `PUT /api/tracking/{id}/review` — **não** criar endpoint novo de rating.
  - STOMP já assina `/topic/couple/{coupleId}/notifications` em `useMatchStore`; `pushIncoming` do `useNotificationStore` só precisa aceitar o novo tipo (sem celebração; `MATCH` continua sendo o único que abre celebração).
  - **Gotcha de sandbox:** sem Chromium e sem JDK/Maven no sandbox, a verificação ponta-a-ponta e screenshots reais podem não rodar aqui — usar `tsc`/`eslint` e dev-browser onde disponível.

---

## 9. Fora de escopo (Non-Goals)

- Alterar os fluxos de `MATCH`/`NO_MATCH` (swipe, celebração, `MatchService`).
- Mudar a escala de nota (continua 1–5) ou o `UserReview` (unique `(media_track_id, user_id)` mantido).
- Mover o cálculo da média para o backend / adicionar campo de média ao `MediaTrackResponse`.
- Editar/ver a **opinião** do parceiro em detalhe além do que o card já mostra.
- Notificações por push nativo/e-mail; digest; agrupamento de notificações.
- Alterar a média do dashboard (`StatsService` já produz `4,3 ★`).
- Novo endpoint dedicado para avaliação (reusa `PUT /review`).
- Push STOMP de "notificação resolvida" para sincronizar segunda aba/dispositivo do parceiro (D12).

---

## 10. Decisões tomadas (antes eram perguntas em aberto)

Todas resolvidas pelo product owner — sem pendências que bloqueiem a implementação:

1. **Fallback de `title` com TMDB indisponível:** gravar texto genérico `"seu título"` — nunca perder o pedido nem bloquear a nota. → D9 / FR-9.
2. **Resolução entre dispositivos do parceiro:** **fora do MVP** — resolução local + `unread-count` no próximo fetch. Sem push STOMP de "resolvida". → D12 / FR-18.
3. **Entrada de avaliação pelo card:** **manter** o botão "Avaliar" no card como caminho alternativo à notificação. → D10 / US-F6 / FR-15.
4. **Limpeza de notificações órfãs:** ao deletar o track, **remover** as `RATING_REQUEST` associadas. → D11 / US-B6 / FR-17.
