# PRD: Sistema de Notificações (Match & No-Match)

## Introdução

O **Sessão a Dois** permite que um casal avalie filmes e séries. Hoje, quando os dois curtem o mesmo título ocorre um **Match** (o título entra na lista "Queremos Ver") e o evento já viaja por WebSocket/STOMP. Falta, porém, uma camada de **notificações** que registre e comunique esses momentos de forma persistente e visível no Header.

Esta primeira versão do sistema de notificações cobre **dois gatilhos**:

1. **Match** — os dois membros do casal curtem o mesmo título. O título é adicionado automaticamente à aba "Queremos Ver" (comportamento já existente) e uma notificação é gerada para **ambos**.
2. **No-Match** — divergência de avaliação: um membro **passa** um título que o parceiro havia **curtido** na tela de Match. Gera notificação para **ambos**.

O objetivo é dar ao casal um histórico e um feedback claro dessas interações, com um ícone de notificações no Header (ao lado do avatar do casal) mostrando contagem de não-lidas e um dropdown com a listagem.

> **Nota sobre a lógica de No-Match:** o descarte/ocultação do título recusado **já existe** na aplicação (um título recusado não volta a aparecer para curtir/passar). Este PRD foca **exclusivamente na notificação**, não em alterar essa regra.

## Goals

- Notificar ambos os membros do casal, em tempo real, quando ocorre um Match ou No-Match.
- Persistir as notificações no banco para garantir histórico e sobrevivência a refresh/offline.
- Exibir um ícone de notificações no Header, ao lado do avatar do casal, com badge de não-lidas.
- Permitir abrir um dropdown listando as notificações e marcá-las como lidas.
- Reaproveitar a infraestrutura STOMP/WebSocket existente, sem introduzir novas tecnologias de tempo real.

## Decisões Registradas (desta feature)

| # | Decisão | Escolha |
|---|---------|---------|
| N1 | Entrega em tempo real | **STOMP (reuso do WebSocket existente) + persistência no banco** |
| N2 | Destinatários | **Ambos os membros do casal** recebem Match e No-Match |
| N3 | Estado lido/não-lido | **Individual por usuário** (cada membro tem seu próprio "read") |
| N4 | Gatilho No-Match | Parceiro **passa** um título que o outro **curtiu** (divergência inerente) |
| N5 | Foco do escopo | **Somente a notificação.** A regra de descarte do título recusado já existe e não é alterada |
| N6 | Match → "Queremos Ver" | Comportamento já existente é mantido; a notificação é gerada em conjunto |
| N7 | Retenção | Notificações **expiram após 30 dias** (limpeza automática) |
| N8 | Carregamento do dropdown | **Limite fixo de 20** mais recentes + botão **"ver mais"** (paginação incremental) |
| N9 | Texto do No-Match | **Neutro para quem passou; com nome do parceiro para o outro membro** |

## User Stories

### US-001: Entidade e persistência de Notificação (Backend)
**Description:** Como desenvolvedor, preciso persistir notificações no banco para que o histórico sobreviva a refresh e a períodos offline.

**Acceptance Criteria:**
- [ ] Criar entidade `Notification` no pacote `com.app.notification` (ver Modelagem em Considerações Técnicas).
- [ ] Campos: `id`, `couple_id`, `recipient_user_id`, `type` (`MATCH` | `NO_MATCH`), `tmdb_id`, `media_type` (`MOVIE` | `TV`), `title` (snapshot do nome exibível), `actor_user_id` (quem completou a ação), `read` (boolean, default `false`), `created_at`.
- [ ] `NotificationRepository` com consultas: listar por `recipient_user_id` ordenado por `created_at desc` e contar não-lidas.
- [ ] Índice em (`recipient_user_id`, `read`).
- [ ] Tabela criada automaticamente via JPA/Hibernate na subida.
- [ ] Rotina de limpeza (`@Scheduled`) que remove notificações com `created_at` anterior a **30 dias** (rodar 1x/dia).
- [ ] Testes unitários (Mockito) para o repositório/serviço passam, incluindo a consulta de expiração (30 dias).

### US-002: Gerar notificações no fluxo de Match (Backend)
**Description:** Como membro do casal, quero ser notificado quando um Match acontece, para saber que um título entrou na nossa lista.

**Acceptance Criteria:**
- [ ] Quando a lógica de match confirma que ambos curtiram o mesmo `tmdb_id`, criar **duas** `Notification` do tipo `MATCH` (uma para cada `recipient_user_id` do casal).
- [ ] `actor_user_id` = usuário que deu o segundo like (quem fechou o match).
- [ ] A criação da notificação ocorre na mesma transação da criação do `MediaTrack` (status `WANT_TO_SEE`) para garantir consistência.
- [ ] Após persistir, emitir evento STOMP para `/topic/couple/{couple_id}/notifications` com o payload da notificação.
- [ ] O fluxo existente de adicionar o título a "Queremos Ver" continua funcionando sem regressão.
- [ ] Testes unitários cobrindo: match cria 2 notificações + emite evento; não-match não cria notificação de match.

### US-003: Gerar notificação no fluxo de No-Match (Backend)
**Description:** Como membro do casal, quero ser notificado quando há divergência (o parceiro passou um título que eu curti), para saber que aquele título não virou match.

**Acceptance Criteria:**
- [ ] Quando um usuário **passa** um título que o parceiro havia **curtido**, criar **duas** `Notification` do tipo `NO_MATCH` (uma para cada membro).
- [ ] `actor_user_id` = usuário que passou o título (permite ao front decidir o texto por destinatário).
- [ ] Emitir evento STOMP para `/topic/couple/{couple_id}/notifications` com o payload.
- [ ] **Não** alterar a regra existente de descarte/ocultação do título recusado (fora de escopo).
- [ ] Testes unitários cobrindo geração de 2 notificações de `NO_MATCH` e emissão do evento.

> **Texto por destinatário (renderizado no front):** quem passou o título vê texto **neutro** (ex.: "Sem match desta vez em *{título}*"); o parceiro vê texto **com o nome** de quem passou (ex.: "{actorName} passou *{título}* — sem match desta vez"). O backend não duplica textos: envia `actorUserId`/`actorName` e o front compara com o usuário atual.

### US-004: Endpoints REST de notificações (Backend)
**Description:** Como frontend, preciso listar notificações, contar não-lidas e marcá-las como lidas.

**Acceptance Criteria:**
- [ ] `GET /api/notifications` — lista as notificações do usuário autenticado (mais recentes primeiro; suportar paginação simples `?page=&size=`).
- [ ] `GET /api/notifications/unread-count` — retorna `{ count: number }` de não-lidas do usuário autenticado.
- [ ] `PATCH /api/notifications/{id}/read` — marca uma notificação do usuário como lida (403 se não pertencer ao usuário).
- [ ] `PATCH /api/notifications/read-all` — marca todas as não-lidas do usuário como lidas.
- [ ] Todos exigem JWT válido e escopam por `recipient_user_id` = usuário autenticado.
- [ ] Testes de Controller (Mockito/MockMvc) cobrindo os quatro endpoints, incluindo o caso 403.

### US-005: Broadcast STOMP e tópico do casal (Backend)
**Description:** Como frontend, quero receber as novas notificações em tempo real pelo WebSocket que já mantenho aberto.

**Acceptance Criteria:**
- [ ] Registrar/estender o destino `/topic/couple/{couple_id}/notifications` na configuração STOMP existente.
- [ ] O payload emitido segue o mesmo DTO retornado pelo `GET /api/notifications` (mesma tipagem no front).
- [ ] O evento é emitido **após** o commit da persistência (notificação já existe no banco quando o push chega).
- [ ] Teste unitário verificando que o `SimpMessagingTemplate` é chamado com o destino e payload corretos.

### US-006: Tipagens e store de notificações (Frontend)
**Description:** Como desenvolvedor frontend, preciso de tipos TypeScript e um estado global para gerenciar notificações.

**Acceptance Criteria:**
- [ ] Criar tipos em `client/src/types/notification.ts` (ver Considerações Técnicas).
- [ ] Criar `useNotificationStore` (Zustand) com: `notifications`, `unreadCount`, `fetchNotifications()`, `markAsRead(id)`, `markAllAsRead()`, `pushIncoming(n)`.
- [ ] Ao subir a app (usuário autenticado + casal vinculado), buscar `GET /api/notifications` e `unread-count`.
- [ ] Assinar `/topic/couple/{couple_id}/notifications` reusando a conexão STOMP do `useMatchStore`; ao receber evento, chamar `pushIncoming` (incrementa `unreadCount` e faz prepend na lista).
- [ ] `typecheck` (tsc) passa.

### US-007: Ícone de notificações no Header (Frontend)
**Description:** Como membro do casal, quero ver um ícone de notificações no Header, ao lado do avatar do casal, indicando quando há notificações não-lidas.

**Acceptance Criteria:**
- [ ] Usar a skill `frontend-design` antes de implementar (obrigatório para `client/`).
- [ ] Ícone de sino posicionado no Header **imediatamente ao lado do avatar do casal** (bloco direito, junto de "Ana & Léo" / avatar sobreposto).
- [ ] Estado **sem não-lidas**: sino neutro, sem badge.
- [ ] Estado **com não-lidas**: badge com a contagem (`unreadCount`); exibir "9+" quando > 9.
- [ ] Visual consistente com o design (tema escuro, acentos `#ffcb2b`/`#ff9e2c`, fontes Bricolage Grotesque/DM Sans).
- [ ] Acessível: `aria-label` descritivo (ex: "Notificações, 3 não lidas") e navegável por teclado.
- [ ] `typecheck` passa.
- [ ] Verificar no navegador usando a skill `dev-browser`.

### US-008: Dropdown de listagem de notificações (Frontend)
**Description:** Como membro do casal, quero abrir um dropdown com minhas notificações e marcá-las como lidas.

**Acceptance Criteria:**
- [ ] Clicar no ícone abre um dropdown ancorado ao sino, com a lista das **20 mais recentes** (mais recentes primeiro).
- [ ] Botão **"Ver mais"** ao fim da lista carrega o próximo lote via `GET /api/notifications?page=&size=`; some quando não há mais itens.
- [ ] Cada item mostra: ícone/cor por tipo (Match = comemorativo/positivo; No-Match = neutro/divergência), título do filme/série, texto contextual (No-Match: neutro para quem passou, com nome do parceiro para o outro) e tempo relativo (ex: "há 2 min").
- [ ] Item não-lido tem indicador visual (ex: ponto/realce); item lido é atenuado.
- [ ] Ação "Marcar todas como lidas" no topo do dropdown (chama `markAllAsRead`).
- [ ] Abrir o dropdown **não** marca tudo como lido automaticamente; marcar acontece por clique no item (ou no "marcar todas").
- [ ] Estado vazio: mensagem amigável quando não há notificações.
- [ ] Fecha ao clicar fora e com `Esc`.
- [ ] `typecheck` passa.
- [ ] Verificar no navegador usando a skill `dev-browser`.

### US-009: Celebração de Match reutilizando a notificação (Frontend)
**Description:** Como casal, quero que o modal de comemoração de Match continue aparecendo e fique coerente com a nova notificação.

**Acceptance Criteria:**
- [ ] Ao receber um evento de notificação `MATCH` pelo STOMP, o modal/celebração de Match existente continua sendo disparado (sem regressão).
- [ ] A notificação de `MATCH` também aparece no dropdown e conta no badge.
- [ ] Evitar duplicidade: um mesmo match não gera duas celebrações no cliente que fez a ação.
- [ ] `typecheck` passa.
- [ ] Verificar no navegador usando a skill `dev-browser`.

## Functional Requirements

- **FR-1:** Ao confirmar um Match, o sistema deve criar duas notificações `MATCH` (uma por membro) e emiti-las via STOMP.
- **FR-2:** Ao ocorrer um No-Match (um membro passa um título que o outro curtiu), o sistema deve criar duas notificações `NO_MATCH` (uma por membro) e emiti-las via STOMP.
- **FR-3:** A notificação de Match deve ser criada na mesma transação em que o título é adicionado a "Queremos Ver".
- **FR-4:** Cada notificação pertence a um `recipient_user_id` e tem estado `read` individual.
- **FR-5:** O sistema deve expor `GET /api/notifications`, `GET /api/notifications/unread-count`, `PATCH /api/notifications/{id}/read` e `PATCH /api/notifications/read-all`, todos escopados ao usuário autenticado.
- **FR-6:** O Header deve exibir um ícone de notificações ao lado do avatar do casal, com badge de não-lidas quando `unreadCount > 0` ("9+" acima de 9).
- **FR-7:** Ao clicar no ícone, o sistema deve abrir um dropdown listando as notificações do usuário, mais recentes primeiro.
- **FR-8:** O usuário deve poder marcar uma notificação individual como lida e "marcar todas como lidas".
- **FR-9:** Novas notificações devem chegar em tempo real via STOMP sem recarregar a página, atualizando lista e badge.
- **FR-10:** Ao carregar a aplicação (autenticado + casal vinculado), o front deve buscar as notificações e a contagem de não-lidas do backend.
- **FR-11:** O comportamento existente de Match (adicionar a "Queremos Ver" + celebração) e a regra de descarte de No-Match não devem sofrer regressão.

## Non-Goals (Fora de Escopo)

- Alterar a lógica de descarte/ocultação de títulos em No-Match (já existe).
- Notificações por e-mail, push do navegador ou mobile.
- Preferências/configurações de notificação (mutar, filtrar por tipo).
- Novos gatilhos além de Match e No-Match (ex: convite de casal, novo título adicionado pelo parceiro).
- Agrupamento/resumo de notificações ("3 novos matches").
- Exclusão de notificações pelo usuário.
- Substituir Polling/SSE — a decisão é reusar STOMP.

## Considerações de Design e UX

**Contexto do Header (do protótipo `Sessão a Dois.dc.html`):** o Header é `sticky`, tema escuro (`rgba(9,9,10,.72)` com blur), dividido em três blocos — logo (esquerda), nav central (Hub / Match / Dashboard) e, à **direita**, o nome do casal ("Ana & Léo" + "312 dias juntos") ao lado do **avatar sobreposto do casal** (dois círculos + coração). O ícone de notificações entra **neste bloco direito, imediatamente à esquerda do avatar**.

- **Ícone (sino):** herda o estilo dos controles do Header; alvo de toque ≥ 40px; usa acentos `#ffcb2b`/`#ff9e2c`.
- **Badge:** círculo pequeno no canto superior direito do sino, fundo de acento, número em contraste alto; "9+" acima de 9. Sem badge quando não há não-lidas.
- **Dropdown:** painel ancorado ao sino, fundo escuro coerente com o Header, largura ~360px, scroll interno, header do painel com "Notificações" + ação "Marcar todas como lidas".
- **Item de notificação:**
  - `MATCH`: tom positivo/comemorativo (coração/acento quente), texto ex.: *"Match! Vocês dois curtiram **{título}** — já está em Queremos Ver."*
  - `NO_MATCH`: tom neutro. **Texto depende do destinatário** — quem passou vê *"Sem match desta vez em **{título}**."*; o parceiro vê *"{actorName} passou **{título}** — sem match desta vez."*
  - Não-lido: ponto/realce à esquerda; lido: atenuado.
  - Tempo relativo ("há 2 min").
- **Acessibilidade:** `aria-label` no botão refletindo a contagem; foco visível; navegável por teclado; fecha com `Esc` e clique fora; respeitar `prefers-reduced-motion` nas animações de abertura/badge.
- **Estado vazio:** mensagem amigável (ex.: "Nada por aqui ainda. Curtam títulos no Match! ♥").
- **Reuso:** componente de dropdown/popover do Shadcn UI já presente no stack; cards/estilos do design aprovado.

## Considerações Técnicas

### Backend (Spring Boot — `com.app.notification`)

**Modelagem de dados — entidade `Notification`:**

| Campo | Tipo | Descrição |
|---|---|---|
| `id` | UUID/Long (PK) | Identificador |
| `couple_id` | FK → Couple | Casal ao qual a notificação pertence |
| `recipient_user_id` | FK → User | **Quem recebe** (uma linha por membro) |
| `type` | enum `MATCH` \| `NO_MATCH` | Tipo do gatilho |
| `tmdb_id` | Long | Título relacionado |
| `media_type` | enum `MOVIE` \| `TV` | Tipo de mídia |
| `title` | String | Snapshot do nome exibível (evita novo fetch ao TMDB) |
| `actor_user_id` | FK → User | Quem completou a ação (2º like no Match; quem passou no No-Match) |
| `read` | boolean (default false) | Estado lido **individual** |
| `created_at` | timestamp | Data de criação |

- **Índice:** (`recipient_user_id`, `read`) para a contagem de não-lidas.
- **Padrão:** cada evento (Match/No-Match) gera **duas** linhas — uma por `recipient_user_id` — o que mantém o estado `read` independente por membro (decisão N3).
- **Retenção (N7):** rotina `@Scheduled` (1x/dia) apaga notificações com `created_at` anterior a 30 dias. Consulta indexada por `created_at`.

**Endpoints REST:**

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/notifications?page=&size=` | Lista do usuário autenticado, `created_at desc` (front usa `size=20` + "ver mais") |
| `GET` | `/api/notifications/unread-count` | `{ count }` de não-lidas |
| `PATCH` | `/api/notifications/{id}/read` | Marca uma como lida (403 se não for do usuário) |
| `PATCH` | `/api/notifications/read-all` | Marca todas as não-lidas como lidas |

**WebSocket/STOMP:** novo destino `/topic/couple/{couple_id}/notifications`, emitido via `SimpMessagingTemplate` **após** o commit. Reusa o `WebSocketConfig` existente (o Nginx de produção já expõe `/ws/` com headers de Upgrade — ver `DEPLOY.md`).

**DTO de resposta (`NotificationDto`)** — usado tanto no REST quanto no payload STOMP (contrato único):
```
{ id, type, tmdbId, mediaType, title, actorUserId, actorName, read, createdAt }
```

**Integração:** a geração de notificações se conecta à lógica existente em `com.app.match` (verificação de likes simultâneos → Match; e o caso de "passar" um título curtido pelo parceiro → No-Match). Preferir um `NotificationService` chamado pelo `MatchService`, mantendo o pacote por feature.

### Frontend (React + TypeScript + Zustand)

**Tipos (`client/src/types/notification.ts`):**
```ts
export type NotificationType = 'MATCH' | 'NO_MATCH';
export type MediaType = 'MOVIE' | 'TV';

export interface Notification {
  id: string;
  type: NotificationType;
  tmdbId: number;
  mediaType: MediaType;
  title: string;
  actorUserId: string;
  actorName: string;
  read: boolean;
  createdAt: string; // ISO
}
```

**Estado (`useNotificationStore`, Zustand):**
```ts
interface NotificationState {
  notifications: Notification[];
  unreadCount: number;
  page: number;
  hasMore: boolean; // controla o botão "Ver mais"
  fetchNotifications: () => Promise<void>;        // 1ª página (size=20)
  fetchMore: () => Promise<void>;                 // próximo lote; atualiza hasMore
  markAsRead: (id: string) => Promise<void>;
  markAllAsRead: () => Promise<void>;
  pushIncoming: (n: Notification) => void;        // do STOMP: prepend + unreadCount++
}
```

- Reusar o cliente Axios com interceptor JWT já configurado.
- Reusar a **conexão STOMP única** mantida pelo `useMatchStore` — assinar `/topic/couple/{couple_id}/notifications` no mesmo ponto onde já se assina `/match`, evitando duas conexões.
- Ao dar `markAsRead`/`markAllAsRead`: atualização otimista da UI + chamada REST (reverter em erro).

**Componentes:** `NotificationBell.tsx` (ícone + badge) e `NotificationDropdown.tsx` (painel/lista), montados no `Header.tsx` no bloco do avatar. Usar Shadcn UI (popover/dropdown) + Tailwind.

### Consistência entre canais
O DTO do REST e o payload do STOMP são idênticos, então o `pushIncoming` recebe exatamente o mesmo shape de `fetchNotifications`, sem transformação divergente.

## Edge Cases (Casos de Exceção)

- **Likes quase simultâneos (corrida no Match):** os dois membros curtem "ao mesmo tempo". A verificação de match e a criação das notificações devem ser feitas de forma **idempotente/atômica** (ex: transação + restrição única lógica por `couple_id + tmdb_id`) para não gerar match/notificação duplicados. Testar concorrência.
- **Lentidão de rede no momento do match:** o STOMP pode atrasar. Como as notificações estão **persistidas**, o front as recupera via `GET /api/notifications` ao (re)carregar — nada se perde. O badge reflete o banco, não só o push.
- **Usuário offline quando o evento ocorre:** ao reconectar/recarregar, `fetchNotifications` + `unread-count` restauram o estado. O push perdido não causa inconsistência.
- **Desfazer curtida (unlike) antes do match fechar:** se o parceiro remove o like antes do segundo like, **não há match** e nenhuma notificação de `MATCH` deve ser criada. A verificação de match deve reconsultar o estado atual dos likes no momento do segundo like (não confiar em estado em cache do cliente).
- **Reconexão do WebSocket:** ao reassinar o tópico após queda, não reprocessar eventos antigos como novos — a fonte de verdade do que é "não-lido" é o banco (`read`), então basta re-sincronizar via REST.
- **Duplicidade de celebração no cliente que agiu:** o usuário que deu o 2º like recebe tanto a resposta HTTP quanto o push STOMP; garantir que a celebração de Match dispare **uma única vez** (deduplicar por `notification.id` ou por `tmdb_id`).
- **Notificação de item já removido/indisponível:** como guardamos `title` (snapshot), a notificação continua legível mesmo que o título saia de listas ou o TMDB fique indisponível.
- **Permissão cruzada:** `PATCH /read` de uma notificação que não pertence ao usuário deve retornar 403 (nunca vazar/alterar notificação de outro membro).
- **Badge com muitas não-lidas:** exibir "9+" acima de 9 para não quebrar o layout do Header.

## Métricas de Sucesso

- 100% dos Matches e No-Matches geram notificação para **ambos** os membros (verificável em testes/integração).
- Notificação de Match aparece para o usuário online em < 2s após o evento (via STOMP).
- Nenhuma notificação perdida após refresh/reconexão (histórico persistido).
- Zero regressão no fluxo existente de Match → "Queremos Ver" e na celebração.
- Badge de não-lidas reflete corretamente o estado do banco após ações de leitura.

## Resolvidas (antes Open Questions)

- **Retenção:** notificações **expiram após 30 dias** via rotina agendada (decisão N7).
- **Carregamento do dropdown:** **limite fixo de 20** mais recentes + botão **"Ver mais"** usando a paginação do endpoint (decisão N8).
- **Texto do No-Match:** **neutro para quem passou**; **com o nome do parceiro** para o outro membro. O front decide comparando `actorUserId` com o usuário atual (decisão N9).

## Open Questions

- Nenhuma pendente. Pronto para implementação.
