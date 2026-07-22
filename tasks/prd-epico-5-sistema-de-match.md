# PRD: Épico 5 — Sistema de Match (Pesquisa + Like)

## 1. Introdução / Visão Geral

O Sistema de Match permite que cada membro do casal, de forma independente, pesquise títulos (filmes/séries) via TMDB e registre um "Like" nos que tem interesse em assistir. Quando **ambos** os membros curtem o **mesmo título**, ocorre um **Match**: o título é adicionado automaticamente à lista "Queremos Ver" do casal e os dois recebem uma notificação em tempo real (celebração visual) via WebSocket.

O objetivo é resolver a "paralisia de decisão" do casal na hora de escolher o que ver juntos, transformando a escolha em uma descoberta compartilhada e divertida.

**Referências de arquitetura:**
- `docs/ARCHITECTURE.md` — Seção 5 (Integração em Tempo Real / WebSockets) e pacotes `com.app.match` / `com.app.websocket`.
- `docs/BACKLOG.md` — Épico 5 e Decisões #9 (fonte: pesquisa manual + like) e #10 (destino: lista "Queremos Ver").
- Protótipo `Sessao a Dois.dc.html` — telas `MatchScreen` e modal de celebração de Match.

## 2. Objetivos

- Permitir que cada usuário registre "Likes" individuais em títulos buscados via TMDB.
- Detectar automaticamente quando os dois membros do casal curtiram o mesmo `tmdb_id`.
- Ao detectar o Match, criar automaticamente um `MediaTrack` com status `WANT_TO_SEE` (sem duplicar registros existentes).
- Notificar **ambos** os parceiros em tempo real via WebSocket (STOMP) no tópico do casal.
- Excluir do fluxo de Match títulos que o casal já possui em qualquer lista (Assistindo / Queremos Ver / Já Vimos).
- Renderizar a celebração de Match no frontend conforme o design aprovado no protótipo.

## 3. Decisões Confirmadas (deste épico)

| # | Decisão | Escolha |
|---|---------|---------|
| M1 | Fonte do Like | **Apenas busca manual (TMDB) + botão Curtir.** Sem baralho de swipe/recomendações neste épico. |
| M2 | Notificação de Match | **Ambos os parceiros** recebem via `/topic/couple/{id}/match`. |
| M3 | Ciclo do Like | **Like é permanente**, sem funcionalidade de desfazer neste épico. |
| M4 | Títulos duplicados | **Excluídos do Match**: um título já presente em qualquer lista do casal não pode ser curtido. |

## 4. User Stories

### US-501: Configurar WebSocket (STOMP) no backend
**Description:** Como desenvolvedor, preciso de um broker de mensagens STOMP configurado para poder emitir eventos de Match em tempo real para o casal.

**Acceptance Criteria:**
- [ ] Criar `WebSocketConfig` em `com.app.websocket` habilitando STOMP (`@EnableWebSocketMessageBroker`).
- [ ] Registrar endpoint STOMP (ex.: `/ws`) com fallback SockJS e CORS liberado para a origem do frontend.
- [ ] Configurar broker simples (`enableSimpleBroker`) com destino de tópico `/topic`.
- [ ] Prefixo de aplicação (`setApplicationDestinationPrefixes`) definido (ex.: `/app`).
- [ ] Conexão WebSocket protegida/consciente do JWT (handshake valida o usuário autenticado).
- [ ] Typecheck/compilação (Maven) passa.

### US-502: Criar entidade MatchLike e persistência
**Description:** Como desenvolvedor, preciso armazenar os Likes individuais de cada usuário para poder detectar quando ambos curtiram o mesmo título.

**Acceptance Criteria:**
- [ ] Criar entidade `MatchLike` em `com.app.match` com campos: `id`, `couple_id`, `user_id`, `tmdb_id`, `media_type` (MOVIE/TV), `created_at`.
- [ ] Restrição de unicidade em (`couple_id`, `user_id`, `tmdb_id`) para impedir Likes duplicados do mesmo usuário no mesmo título.
- [ ] Criar `MatchLikeRepository` com método para verificar se o parceiro já curtiu um `tmdb_id`.
- [ ] Migration/DDL gerada e aplicada com sucesso.
- [ ] Compilação (Maven) passa.

### US-503: Endpoint de registro de Like
**Description:** Como usuário, quero curtir um título buscado para que ele entre na fila de possíveis matches do casal.

**Acceptance Criteria:**
- [ ] Endpoint `POST /api/match/like` recebendo `tmdb_id` e `media_type`.
- [ ] O Like é vinculado ao `couple_id` e `user_id` do usuário autenticado (extraídos do JWT, nunca do corpo da requisição).
- [ ] Rejeita (com erro claro, ex.: 409) título que o casal já possui em qualquer lista (`MediaTrack` existente) — Decisão M4.
- [ ] Rejeita/ignora idempotentemente Like repetido do mesmo usuário no mesmo título (não cria duplicado).
- [ ] Retorna se a ação resultou ou não em Match (ex.: `{ matched: boolean, ... }`).
- [ ] Compilação (Maven) passa.

### US-504: Lógica de detecção de Match e criação automática
**Description:** Como casal, quando os dois curtem o mesmo título, quero que ele seja adicionado automaticamente à lista "Queremos Ver" e que sejamos notificados.

**Acceptance Criteria:**
- [ ] Ao registrar um Like, o serviço verifica se o parceiro já curtiu o mesmo `tmdb_id` no mesmo `couple_id`.
- [ ] Se sim, cria um `MediaTrack` com `status = WANT_TO_SEE` vinculado ao `couple_id` (reaproveitando o serviço de tracking do Épico 4).
- [ ] Não cria `MediaTrack` duplicado caso já exista para aquele `tmdb_id` (guarda de segurança — Decisão M4).
- [ ] Emite evento STOMP para `/topic/couple/{couple_id}/match` com payload contendo ao menos: `tmdb_id`, `title`, `media_type`.
- [ ] A operação (criação do track + emissão) é transacional e consistente.
- [ ] Compilação (Maven) passa.

### US-505: Testes unitários do domínio Match
**Description:** Como desenvolvedor, preciso garantir que a lógica de match (a regra de negócio mais crítica deste épico) funcione corretamente e de forma resiliente.

**Acceptance Criteria:**
- [ ] Teste: primeiro Like (parceiro ainda não curtiu) → NÃO gera Match, apenas persiste o Like.
- [ ] Teste: segundo Like do parceiro no mesmo título → gera Match, cria `MediaTrack` WANT_TO_SEE e emite evento.
- [ ] Teste: Like em título já rastreado → rejeitado (Decisão M4).
- [ ] Teste: Like duplicado do mesmo usuário → não cria registro duplicado.
- [ ] Cobertura para Controller, Service e Repository do pacote `com.app.match` (Mockito), conforme obrigatoriedade da ARCHITECTURE.md.
- [ ] `mvn test` passa.

### US-506: Store de WebSocket no frontend (useMatchStore)
**Description:** Como usuário, quero que o app mantenha uma conexão ativa com o servidor para receber a notificação de Match instantaneamente.

**Acceptance Criteria:**
- [ ] Criar `useMatchStore` (Zustand) que gerencia a conexão STOMP/WebSocket.
- [ ] Ao autenticar/entrar na área logada, conecta e se inscreve em `/topic/couple/{couple_id}/match` (JWT no handshake).
- [ ] Ao receber evento de match, armazena os dados do título e sinaliza estado `matchOpen = true` para a UI reagir.
- [ ] Desconecta/limpa a inscrição no logout ou unmount apropriado (sem vazamento de conexões).
- [ ] Typecheck/lint passa.

### US-507: Tela MatchScreen (busca + curtir)
**Description:** Como usuário, quero buscar títulos e curti-los para tentar formar matches com meu parceiro.

**Acceptance Criteria:**
- [ ] `MatchScreen.tsx` com campo de busca que consome o endpoint de busca TMDB do Épico 3 (`GET /api/media/search`).
- [ ] Resultados listados como cards com botão "Curtir ♥" chamando `POST /api/match/like`.
- [ ] Título já presente em alguma lista do casal aparece desabilitado/indicado como já rastreado (Decisão M4).
- [ ] Feedback visual ao curtir (estado de loading e confirmação).
- [ ] Segue o design aprovado no protótipo (identidade visual: fundo escuro, destaque amarelo `#ffcb2b`, tipografia Bricolage Grotesque / DM Sans).
- [ ] **Usar a skill `frontend-design` antes de implementar** (obrigatório para `client/`).
- [ ] Typecheck/lint passa.
- [ ] Verificar no navegador usando a skill dev-browser.

### US-508: Modal de celebração de Match
**Description:** Como casal, quando damos match, quero ver uma celebração visual clara indicando o título e que ele entrou na lista "Queremos Ver".

**Acceptance Criteria:**
- [ ] Modal de celebração renderiza ao `matchOpen === true` (disparado pelo `useMatchStore`).
- [ ] Exibe o título do match e mensagem de que foi adicionado a "Queremos Ver".
- [ ] Botões "Ver na lista" (navega ao Hub) e "Continuar" (fecha o modal).
- [ ] Aparece para **ambos** os parceiros conectados (Decisão M2).
- [ ] Segue o design do protótipo (bloco `MATCH CELEBRATION`).
- [ ] **Usar a skill `frontend-design` antes de implementar** (obrigatório para `client/`).
- [ ] Typecheck/lint passa.
- [ ] Verificar no navegador usando a skill dev-browser.

## 5. Requisitos Funcionais

- **FR-1:** O sistema deve expor um endpoint STOMP de WebSocket para conexões em tempo real, com o broker roteando mensagens em `/topic`.
- **FR-2:** O sistema deve persistir cada Like como `MatchLike` (`couple_id`, `user_id`, `tmdb_id`, `media_type`, `created_at`), com unicidade por (`couple_id`, `user_id`, `tmdb_id`).
- **FR-3:** O endpoint `POST /api/match/like` deve derivar `user_id` e `couple_id` do JWT autenticado, nunca do corpo da requisição.
- **FR-4:** Ao registrar um Like, o sistema deve verificar se o parceiro já curtiu o mesmo `tmdb_id`.
- **FR-5:** Havendo Like de ambos, o sistema deve criar um `MediaTrack` com status `WANT_TO_SEE` vinculado ao `couple_id`, sem duplicar registro já existente.
- **FR-6:** Após criar o Match, o sistema deve emitir um evento para `/topic/couple/{couple_id}/match` recebido por ambos os membros.
- **FR-7:** O sistema deve impedir Like em títulos que o casal já possui em qualquer lista (Assistindo / Queremos Ver / Já Vimos).
- **FR-8:** O frontend deve manter conexão WebSocket ativa via `useMatchStore` e renderizar o modal de celebração ao receber o evento.
- **FR-9:** A `MatchScreen` deve permitir buscar títulos via TMDB e curtir resultados individualmente.
- **FR-10:** Likes são permanentes; o sistema não precisa oferecer funcionalidade de desfazer neste épico.

## 6. Não-Objetivos (Fora de Escopo)

- **Sem interface de swipe / baralho de recomendações** (Passar/Curtir por cards). Apesar de existir no protótipo, a fonte de Likes neste épico é exclusivamente a busca manual (Decisão M1). O visual de swipe pode ser reavaliado em um épico futuro.
- **Sem desfazer/remover Like** (Decisão M3).
- **Sem sistema de recomendações personalizadas** (não há fonte de sugestões automáticas neste épico).
- **Sem notificações push/e-mail** — a notificação é apenas in-app via WebSocket para sessões conectadas.
- **Sem histórico/telemetria de matches** além do `MediaTrack` resultante.
- **Sem estatísticas de match no Dashboard** (isso pertence ao Épico 6).

## 7. Considerações de Design

- Reutilizar a identidade visual do protótipo `Sessao a Dois.dc.html`: fundo `#09090a`, destaque `#ffcb2b`/`#ff9e2c`, verde de sucesso `#3ddc97`, fontes Bricolage Grotesque (títulos) e DM Sans (texto).
- Reaproveitar o layout do modal de celebração já desenhado no protótipo (seção "MATCH CELEBRATION").
- A `MatchScreen` deve adaptar o layout do protótipo (que era swipe) para uma lista de busca + curtir, mantendo a mesma linguagem visual dos cards.
- Componentes UI (Shadcn) já existentes no projeto (Dialog, Card) devem ser reutilizados quando fizer sentido.
- **Obrigatório:** usar a skill `frontend-design` antes de qualquer implementação em `client/` (regra do CLAUDE.md).

## 8. Considerações Técnicas

- **Backend:** Spring Boot 21, pacotes `com.app.match` (lógica/entidade/endpoint) e `com.app.websocket` (config STOMP).
- **WebSocket:** Spring WebSocket + STOMP com broker simples. Avaliar segurança do handshake com JWT (mesmo filtro/segredo do `com.app.security`).
- **Dependência de épicos anteriores:**
  - Épico 2 (Auth/Couple): necessário para derivar `user_id`/`couple_id` e proteger rotas.
  - Épico 3 (TMDB): a busca da `MatchScreen` usa `GET /api/media/search`.
  - Épico 4 (Tracking): a criação automática do Match reutiliza o serviço/entidade `MediaTrack`.
- **Frontend:** `useMatchStore` (Zustand) para a conexão STOMP; usar cliente STOMP compatível (ex.: `@stomp/stompjs` + SockJS). Axios (já configurado no Épico 2) para o `POST /api/match/like`.
- **Consistência:** a verificação "parceiro já curtiu?" + criação do `MediaTrack` deve ser transacional para evitar corrida quando ambos curtem quase simultaneamente.

## 9. Métricas de Sucesso

- Um casal consegue formar um Match de ponta a ponta (dois Likes no mesmo título → título em "Queremos Ver") sem intervenção manual.
- A celebração de Match aparece para ambos os parceiros conectados em menos de ~1s após o segundo Like.
- Nenhum `MediaTrack` duplicado é criado por matches.
- Cobertura de testes unitários satisfatória para `com.app.match` (Controller, Service, Repository).

## 10. Questões em Aberto

- **Q1:** Qual biblioteca STOMP/WebSocket o frontend usará (`@stomp/stompjs` + SockJS vs. WebSocket nativo)? (Sugestão: `@stomp/stompjs` + SockJS para compatibilidade com o fallback do Spring.)
- **Q2:** O que acontece se um usuário curte um título e depois o parceiro o adiciona manualmente à lista antes do segundo like — o Like órfão deve ser limpo? (Provável: irrelevante pois M4 bloqueia curtir títulos já rastreados; validar comportamento do like pré-existente.)
- **Q3:** A conexão WebSocket deve ser estabelecida globalmente (ao logar) ou apenas ao entrar na `MatchScreen`? (Sugestão: global, para receber matches disparados pelo parceiro mesmo fora da tela.)
- **Q4:** Deve haver algum indicador na `MatchScreen` de que "o parceiro já curtiu este título" (badge do protótipo), ou isso revela demais as escolhas do parceiro? (Definir se o efeito surpresa do match é desejado.)
