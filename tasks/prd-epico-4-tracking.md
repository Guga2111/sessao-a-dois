# PRD: Épico 4 — Tracking de Mídia (Core App)

## Introdução

O Épico 4 implementa o núcleo funcional do app: o casal pode adicionar filmes e séries à sua lista compartilhada, registrar avaliações individuais (nota e opinião), mudar o status de um título e visualizar tudo organizado em três seções — "Assistindo Atualmente", "Queremos Ver" e "Já Vimos".

O backend expõe uma API REST para gerenciar `MediaTrack` (registro compartilhado do casal) e `UserReview` (avaliação individual de cada membro). O frontend renderiza o `HubScreen.tsx` com os cards e o `TitleModal.tsx` para adição de novos títulos.

Pré-requisito: Épico 2 (autenticação JWT + couple) e Épico 3 (busca TMDB) devem estar funcionando.

---

## Objetivos

- Permitir que o casal adicione filmes e séries buscados via TMDB à lista compartilhada.
- Registrar o status de cada título: WATCHING, WANT_TO_SEE ou WATCHED.
- Permitir que cada usuário registre sua própria nota (1–5 estrelas) e opinião textual.
- Listar os títulos por status no Hub, exibindo as avaliações dos dois membros lado a lado nos cards.
- Armazenar o `runtime` (duração em minutos) ao adicionar filmes, preparando os dados para o dashboard do Épico 6.

---

## User Stories

### US-001: Criar entidades e migrações do banco de dados
**Descrição:** Como desenvolvedor, preciso das tabelas `media_track` e `user_review` no banco para persistir o tracking de mídia do casal.

**Acceptance Criteria:**
- [ ] Criar entidade JPA `MediaTrack` com campos: `id` (UUID), `couple_id` (FK para `couples`), `tmdb_id` (Long), `media_type` (enum: MOVIE, TV), `status` (enum: WATCHING, WANT_TO_SEE, WATCHED), `watched_date` (LocalDate, nullable), `runtime` (Integer nullable — minutos, apenas filmes), `created_at` (LocalDateTime).
- [ ] Criar entidade JPA `UserReview` com campos: `id` (UUID), `media_track_id` (FK para `media_track`), `user_id` (FK para `users`), `rating` (Integer 1–5, nullable), `opinion` (Text, nullable).
- [ ] Relacionamento: `MediaTrack` tem lista de `UserReview` (OneToMany). `UserReview` tem relação ManyToOne com `MediaTrack`.
- [ ] Constraint única em `user_review(media_track_id, user_id)` — cada usuário tem no máximo uma review por título.
- [ ] Script de migração SQL (ou equivalente via JPA DDL) executado com sucesso em banco limpo.
- [ ] Typecheck/build Maven passa sem erros.

---

### US-002: Implementar repositórios e serviço de MediaTrack
**Descrição:** Como desenvolvedor, preciso da camada de acesso a dados e regras de negócio para `MediaTrack` no pacote `com.app.tracking`.

**Acceptance Criteria:**
- [ ] `MediaTrackRepository` estende `JpaRepository<MediaTrack, UUID>` com método `findByCoupleIdAndStatus(UUID coupleId, MediaStatus status)`.
- [ ] `MediaTrackService` implementa:
  - `addTrack(UUID coupleId, CreateMediaTrackRequest req)` — cria um `MediaTrack` e já cria um `UserReview` vazio para o usuário que adicionou.
  - `listByStatus(UUID coupleId, MediaStatus status)` — retorna lista de `MediaTrackResponse` com os reviews de ambos os membros.
  - `updateStatus(UUID trackId, UUID coupleId, MediaStatus newStatus)` — muda o status; valida que o track pertence ao casal.
  - `deleteTrack(UUID trackId, UUID coupleId)` — remove o track e reviews associados; valida pertencimento ao casal.
- [ ] Lançar `ResourceNotFoundException` (404) se o track não existir ou não pertencer ao casal.
- [ ] Typecheck/build Maven passa sem erros.

---

### US-003: Implementar repositório e serviço de UserReview
**Descrição:** Como desenvolvedor, preciso gerenciar a avaliação individual de cada membro do casal.

**Acceptance Criteria:**
- [ ] `UserReviewRepository` estende `JpaRepository<UserReview, UUID>` com método `findByMediaTrackIdAndUserId(UUID trackId, UUID userId)`.
- [ ] `UserReviewService` implementa:
  - `upsertReview(UUID trackId, UUID userId, UpsertReviewRequest req)` — cria ou atualiza rating e opinion do usuário no track especificado.
- [ ] Se o `MediaTrack` não existir ou não pertencer ao casal do usuário, lançar 403/404 conforme o caso.
- [ ] Typecheck/build Maven passa sem erros.

---

### US-004: Criar endpoints REST de Tracking
**Descrição:** Como frontend, preciso de endpoints para listar, adicionar, mudar status e remover títulos da lista do casal.

**Acceptance Criteria:**
- [ ] `GET /api/tracking?status={STATUS}` — retorna lista de `MediaTrackResponse` filtrada por status. O status é opcional; sem ele, retorna todos.
- [ ] `POST /api/tracking` — body: `{ tmdbId, mediaType, status, watchedDate?, runtime?, rating?, opinion? }`. Cria `MediaTrack` e `UserReview` inicial do usuário logado. Retorna 201 com o recurso criado.
- [ ] `PATCH /api/tracking/{id}/status` — body: `{ status, watchedDate? }`. Atualiza o status do track. Retorna 200.
- [ ] `DELETE /api/tracking/{id}` — remove o track. Retorna 204.
- [ ] `PUT /api/tracking/{id}/review` — body: `{ rating?, opinion? }`. Cria ou atualiza a review do usuário logado. Retorna 200.
- [ ] Todos os endpoints exigem JWT válido (401 sem token).
- [ ] `MediaTrackResponse` inclui: dados do track + lista de reviews com `{ userId, userName, rating, opinion }` para ambos os membros do casal (review ausente retorna `null` para rating e opinion).
- [ ] Typecheck/build Maven passa sem erros.

---

### US-005: Escrever testes unitários para a camada de tracking
**Descrição:** Como desenvolvedor, preciso de cobertura de testes nos Services e Controllers do módulo de tracking para garantir comportamento correto e resiliência.

**Acceptance Criteria:**
- [ ] Testes unitários (JUnit 5 + Mockito) para `MediaTrackService`:
  - `addTrack` cria o track e a review inicial corretamente.
  - `updateStatus` lança exceção ao tentar atualizar track de outro casal.
  - `deleteTrack` remove corretamente e lança exceção se não encontrado.
- [ ] Testes unitários para `UserReviewService`:
  - `upsertReview` cria review quando não existe.
  - `upsertReview` atualiza review existente sem criar duplicata.
- [ ] Testes de integração leves para `MediaTrackController` (MockMvc):
  - `GET /api/tracking?status=WATCHING` retorna 200 com lista correta.
  - `POST /api/tracking` sem JWT retorna 401.
  - `DELETE /api/tracking/{id}` de outro casal retorna 403 ou 404.
- [ ] Todos os testes passam com `mvn test`.

---

### US-006: Desenvolver componente `TitleModal.tsx` (adição de título)
**Descrição:** Como usuário, quero um modal para buscar e adicionar um título à lista do casal, informando minha nota e status.

**Acceptance Criteria:**
- [ ] Modal abre ao clicar em "Adicionar Título" (botão no header do Hub) ou no FAB (botão flutuante).
- [ ] Campo de busca chama `GET /api/media/search?q=...` com debounce de 400ms e exibe resultados abaixo do input (título, ano, tipo).
- [ ] Ao selecionar um resultado, o campo de busca exibe o título escolhido e os dados do filme/série ficam prontos para envio.
- [ ] Pills de status permitem escolher: "Assistindo", "Queremos Ver" ou "Já Vimos" (apenas um selecionado por vez).
- [ ] Picker de 5 estrelas para nota do usuário logado (estado visual: estrelas preenchidas em amarelo até a nota escolhida).
- [ ] Campo de data assistida (input date) — obrigatório apenas quando status = "Já Vimos".
- [ ] Campo de textarea para opinião do usuário logado (opcional).
- [ ] Botão "Salvar Título" chama `POST /api/tracking` e fecha o modal ao sucesso.
- [ ] Botão "Cancelar" fecha o modal sem salvar.
- [ ] Estado de loading no botão Salvar durante a requisição (texto muda para "Salvando…" e fica desabilitado).
- [ ] Toast/mensagem de erro visível se a requisição falhar.
- [ ] Ao fechar, os campos são resetados para o estado inicial.
- [ ] Design segue o protótipo: fundo `#161513`, inputs com borda `rgba(255,255,255,.12)`, foco com borda `#ffcb2b` e glow amarelo, botão primário `#ffcb2b` com texto `#111`.
- [ ] Typecheck/lint passa.
- [ ] Verificar no browser via skill dev-browser.

---

### US-007: Desenvolver tela `HubScreen.tsx` com as três listas
**Descrição:** Como usuário, quero ver os títulos do casal organizados em "Assistindo Atualmente", "Queremos Ver" e "Já Vimos".

**Acceptance Criteria:**
- [ ] Ao montar, `HubScreen.tsx` busca `GET /api/tracking` para cada status e armazena em estado local (ou store Zustand).
- [ ] Seção "Assistindo Atualmente" (indicador laranja `#ff9e2c`) exibe os cards com status WATCHING.
- [ ] Seção "Queremos Ver" (indicador amarelo `#ffcb2b`) exibe os cards com status WANT_TO_SEE, cada card tem botão "Marcar como visto" que chama `PATCH /api/tracking/{id}/status` com `{ status: "WATCHED" }` e move o card para a seção correta.
- [ ] Seção "Já Vimos" (indicador verde `#3ddc97`) exibe os cards com status WATCHED.
- [ ] Cada seção mostra contador de itens (badge cinza ao lado do título da seção).
- [ ] Estado vazio: cada seção exibe mensagem quando não há títulos.
- [ ] Estado de loading: skeleton/placeholder animado enquanto os dados carregam.
- [ ] Header da página tem título "O que estamos vendo", subtítulo e botão "Adicionar Título" que abre o `TitleModal.tsx`.
- [ ] FAB fixo no canto inferior direito também abre o `TitleModal.tsx`.
- [ ] Após salvar no modal, as listas são recarregadas automaticamente.
- [ ] Design segue o protótipo: fundo radial-gradient escuro, fontes Bricolage Grotesque (títulos) e DM Sans (corpo).
- [ ] Typecheck/lint passa.
- [ ] Verificar no browser via skill dev-browser.

---

### US-008: Componentizar os cards de mídia (`MediaCard.tsx`)
**Descrição:** Como usuário, quero cards visuais que mostrem as informações do título e as avaliações dos dois membros do casal.

**Acceptance Criteria:**
- [ ] Componente `MediaCard.tsx` aceita props: `track` (dados do MediaTrack) + `reviews` (array de até 2 UserReview) + `onStatusChange?` + `onClick?`.
- [ ] Área de pôster: gradiente colorido baseado em `hue` derivado do `tmdb_id` (ex: `hsl((tmdbId % 360), 42%, 24%)`), com badge de gênero no canto superior esquerdo.
- [ ] Dados exibidos: título, ano, badge de tipo (Filme/Série), badges de streaming (serviços retornados pela API), data assistida (se houver).
- [ ] Avaliação: se ao menos um usuário tiver review, exibir estrelas e nota. Se dois usuários tiverem review, exibir as duas avaliações lado a lado com o nome de cada um.
- [ ] Trecho de opinião: exibir a opinião do usuário logado em card estilizado (fundo amarelo suave) dentro do card. Se houver opinião do parceiro, exibi-la também.
- [ ] Card com status WATCHED tem ícone de check verde (`#3ddc97`) no canto superior direito do pôster.
- [ ] Hover: leve elevação (`translateY(-4px)`) e borda colorida (amarela para WATCHING/WANT_TO_SEE, verde para WATCHED).
- [ ] Cards com status WANT_TO_SEE: não exibem avaliação (ainda não assistido), exibem quem adicionou ("Adicionado por X") e botão "Marcar como visto".
- [ ] Typecheck/lint passa.
- [ ] Verificar no browser via skill dev-browser.

---

## Requisitos Funcionais

- **FR-1:** `MediaTrack` pertence a um `couple_id`; apenas membros do casal autenticado podem criar, ler, atualizar e deletar seus tracks.
- **FR-2:** Cada usuário do casal pode ter exatamente uma `UserReview` por `MediaTrack` (constraint única no banco).
- **FR-3:** Ao criar um `MediaTrack`, o backend cria automaticamente uma `UserReview` para o usuário que fez a requisição com os dados de rating/opinion enviados (podem ser nulos).
- **FR-4:** O campo `runtime` (duração em minutos) deve ser salvo no `MediaTrack` apenas para filmes (`media_type = MOVIE`). Para séries, o campo fica `null`.
- **FR-5:** A listagem (`GET /api/tracking`) retorna os reviews de ambos os membros embutidos no response de cada track.
- **FR-6:** `watched_date` é obrigatório no frontend apenas quando o status for WATCHED; o backend aceita null para os outros status.
- **FR-7:** A mudança de status (PATCH) atualiza apenas o status e a `watched_date`; não toca nas reviews individuais.
- **FR-8:** O frontend só exibe o formulário de avaliação (nota/opinião) para o usuário logado no modal de adição. As avaliações do parceiro são lidas apenas na listagem.

---

## Non-Goals (Fora de Escopo)

- Edição de um título já cadastrado pelo modal (será refinamento futuro).
- Paginação das listas (todas as listas retornam completas por ora).
- Ordenação customizável pelo usuário.
- Upload de poster real — o design usa gradiente como placeholder.
- Notificações push ao parceiro quando um título é adicionado.
- Cálculo de estatísticas do dashboard (Épico 6).
- Sistema de likes/match (Épico 5).

---

## Considerações de Design

O design visual segue fielmente o protótipo em `docs/design/claude-design-project/Sessao a Dois.dc.html`:

- **Paleta:** fundo `#09090a`, superfícies `#161513`, amarelo primário `#ffcb2b`, laranja `#ff9e2c`, verde `#3ddc97`.
- **Tipografia:** `Bricolage Grotesque` (700–800) para títulos de seção e modal; `DM Sans` (400–700) para corpo.
- **Cards:** `border-radius: 18px`, borda `rgba(255,255,255,.07)`, hover com `border-color` colorido por status.
- **Pôster:** `aspect-ratio: 3/4`, gradiente `hsl()` derivado do conteúdo.
- **Inputs no modal:** borda `rgba(255,255,255,.12)`, foco com `border-color: #ffcb2b` e `box-shadow: 0 0 0 3px rgba(255,203,43,.2)`.
- **Botão primário:** `background: #ffcb2b`, `color: #111`, `font-weight: 700`, `box-shadow: 0 8px 22px rgba(255,203,43,.35)`.
- **Fontes:** carregadas via Google Fonts (já configuradas no head da página principal).

---

## Considerações Técnicas

- **Pacote backend:** `com.app.tracking` (MediaTrackController, MediaTrackService, MediaTrackRepository, UserReviewService, UserReviewRepository + entidades).
- **Autenticação:** todos os endpoints de tracking exigem JWT; o `couple_id` é extraído do usuário autenticado (via `SecurityContext`), não do request body — isso evita que um usuário acesse dados de outro casal.
- **DTO de resposta:** `MediaTrackResponse` deve incluir `reviews: [{ userId, userName, rating, opinion }]`. O frontend diferencia qual review é "minha" pelo `userId` comparado ao user logado no `useAuthStore`.
- **Frontend — store:** os dados das listas podem ser gerenciados em estado local do `HubScreen.tsx` (não precisa de nova Zustand store). Recarregar após mutações (add/status change) chamando novamente os GETs.
- **Hue dos cards:** derivar com `tmdbId % 360` para variar as cores dos gradientes dos pôsters.
- **Runtime no TMDB:** a chamada `GET /api/media/search` retorna dados básicos. Para obter o `runtime`, o frontend deve chamar `GET /api/media/{id}?type={type}` (endpoint de detalhes — Épico 3, Task 3.3) antes de exibir o modal de confirmação ou ao selecionar um resultado.
- **Testes:** JUnit 5 + Mockito para Services; MockMvc para Controllers; repositórios testados via teste de integração ou mockados.

---

## Métricas de Sucesso

- Usuário consegue adicionar um título em menos de 4 cliques (buscar → selecionar → definir status → salvar).
- As listas do Hub carregam em menos de 1 segundo em ambiente local.
- Ao mudar o status de "Queremos Ver" para "Já Vimos", o card se move para a seção correta sem recarregar a página inteira.
- Avaliações dos dois membros do casal visíveis no mesmo card sem necessidade de navegação adicional.

---

## Perguntas Abertas

- O endpoint `GET /api/tracking` sem filtro de status deve retornar todos os títulos em uma única lista ou o frontend deve fazer 3 chamadas separadas (uma por status)?
- Ao exibir dois reviews no card, qual é a ordem — "meu review primeiro" ou ordem de criação?
- Deve haver uma forma de remover um título já adicionado via UI (botão de deletar no card), ou a deleção fica fora do escopo do Épico 4?
