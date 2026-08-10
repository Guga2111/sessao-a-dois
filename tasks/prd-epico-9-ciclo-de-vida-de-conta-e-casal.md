# PRD: Épico 9 — Ciclo de Vida de Conta e Casal

## Introdução

Hoje o app só tem o **caminho de ida**. Cria-se conta, cria-se casal, entra-se num
casal — e acabou. Não há como sair, corrigir ou apagar nada:

- Quem digitou o código de convite errado e entrou no casal errado fica preso ali
  para sempre (`CoupleService.joinCouple` lança `UserAlreadyInCoupleException` em
  qualquer tentativa de entrar em outro, e não existe endpoint que desfaça o vínculo).
- Quem errou o nome no cadastro ou trocou de e-mail não tem como corrigir —
  `com.app.user` tem só a entidade `User` e o `UserRepository`, **zero endpoints**.
- Quem suspeita que a senha vazou não tem nenhuma ação disponível: não existe troca
  de senha, e o `RefreshTokenService.revokeFamily(userId)` construído no Épico 4 só
  é chamado no logout.
- Quem quer sair do app não consegue: não há exclusão de conta. Além de ser o pedido
  mais básico de privacidade, é exigência da LGPD (art. 18, eliminação de dado
  pessoal a pedido do titular).

O único caminho de correção para qualquer um destes casos hoje é `UPDATE`/`DELETE`
manual no banco de produção.

Este épico entrega os quatro caminhos de volta — dissolução de casal, edição de
perfil, troca de senha e exclusão de conta — e a tela que os torna alcançáveis.

**Origem:** varredura de lacunas de MVP (2026-08-06), lacunas #2 e #3 do
`POST-MVP-TASK.md` (T9.1 – T9.5).

### Estado do código na abertura deste épico

Levantado em 2026-08-06, no repositório, não presumido do backlog:

| Área | Estado |
|---|---|
| Épicos | 1 a 8 concluídos. |
| Migrations | Até `V6__add_invite_code_expiry.sql`. A próxima é a **V7**, criada por este épico — e é a **única**. |
| Sessão | Cookies HttpOnly `access_token` (15 min) / `refresh_token` (30 dias) com rotação e detecção de reuso. `AuthCookieService`, `RefreshTokenService.revokeFamily` e `SecurityAuditLogger` já existem e são reusados aqui. |
| Rate limit | `RateLimitFilter` (por IP, casando método+path) + `RateLimitService.tryConsume(key, capacity, window)` + `RateLimitProperties`. O limite **por usuário** já tem precedente: `CoupleService.enforceJoinRateLimit` e `enforceRegenerateInviteCodeRateLimit` consomem o **mesmo** `Limit` (`coupleJoinByUser`) com **chaves diferentes** (`couple-join:user:<id>` e `invite-code-regenerate:user:<id>`), direto do serviço. Este épico estende o mecanismo, mas **muda esse detalhe**: cada endpoint novo ganha propriedade própria (E9.3), não reaproveita um `Limit` alheio. |
| Política de senha | `RegisterRequest.password` com `@Size(min = 8, max = 72)` (T5.3). Não existe classe validadora dedicada — a política **é** a anotação. |
| `com.app.couple` | `CoupleController` expõe `POST /api/couple`, `GET /api/couple/me`, `POST /api/couple/join`, `POST /api/couple/invite-code/regenerate`. Nenhuma saída. |
| Acoplamento a corrigir | `MediaTrackService`/`MatchService` importam `CoupleRepository`; `MediaTrackController`/`MatchController` importam `CoupleService` e `Couple`; `MediaTrack`, `MatchLike` e `MatchReject` têm `@ManyToOne Couple`; `TrackingFacade.createTrackFromMatch(Couple, ...)` recebe a entidade; **`NotificationService.notifyCouple(Couple, ...)` e `notifyRatingRequest(Couple, ...)` também recebem a entidade — e quem chama é `MatchService` e `RatingRequestService`, ou seja, o import de `Couple` em `tracking`/`match` não fecha sem mexer nessas duas assinaturas** (E9.19). Anti-patterns #2/#5 da `docs/ARCHITECTURE.md`. |
| Fora do escopo do desacoplamento | `com.app.websocket.CoupleDestinationAuthorizationManager` também importa `Couple`/`CoupleService` (autorização dos tópicos STOMP). **Permanece como está** (E9.19) — mas passa a herdar o filtro de casal ativo pelo `getCurrentCouple`, o que é justamente o comportamento desejado após uma dissolução. |
| Frontend | `App.tsx` roteia `/`, `/login`, `/register`, `/join`, `/hub`, `/match`, `/dashboard`. **Não existe nenhuma tela de configurações.** |
| Testes | 48 arquivos de teste no backend, gate do JaCoCo em 90% de linha. O `client/` continua sem runner de teste — isso é o Épico 11 e permanece fora de escopo aqui. |

### Fatos de schema que condicionam a exclusão de conta

Levantados em `V1__baseline.sql`, `V2__create_notification.sql` e
`V5__create_refresh_token.sql`:

| Tabela | Referência ao usuário | FK declarada? |
|---|---|---|
| `user_review` | `user_id` | **Sim** (`fk_user_review_user`) — bloqueia o `DELETE` de `users` se não for apagada antes. |
| `refresh_token` | `user_id` | **Sim** (`fk_refresh_token_user`) — idem. |
| `match_like` / `match_reject` | `user_id` | **Não.** FK só para `couples`. Apagar é escolha de privacidade, não obrigação de integridade. |
| `notification` | `recipient_user_id`, `actor_user_id` | **Não.** FK só para `couples` — e essa FK é `NOT NULL`, então nenhuma operação deste épico pode apagar uma linha de `couples` (a dissolução é `UPDATE`, nunca `DELETE`). |
| `refresh_token` | `replaced_by_id` | **Sim, para a própria tabela** (`fk_refresh_token_replaced_by`). Condiciona *como* se apaga, não *o que* — ver FR-17b. |
| `media_track` | — | Pertence ao `couple_id`, nunca ao usuário. **Permanece** (D13). |

`couples.invite_code` é **nullable** desde a V6 e é **limpo** (`NULL`) no join
bem-sucedido. Postgres trata cada `NULL` como distinto num unique constraint, então
casais dissolvidos não colidem no `uk_couples_invite_code`.

Isso resolve a *unicidade*, mas **não** a *validade*: um casal criado e nunca pareado
mantém o `invite_code` preenchido, e `CoupleService.joinCouple` resolve o convite por
`coupleRepository.findByInviteCode` sem nenhum filtro. Sem tratamento, o código de um
casal dissolvido continuaria aceitando um parceiro novo para dentro de um casal morto.
Por isso a dissolução **tem** de limpar o código (E9.17).

## Goals

- Dar ao usuário um **caminho de volta** para cada caminho de ida que o app já tem:
  desfazer o vínculo do casal, corrigir o perfil, trocar a senha e apagar a conta.
- Preservar o histórico do casal ao dissolver (D13: **dissolver, não apagar**), sem
  deixar nenhum resíduo *acessível* para os ex-membros.
- Cumprir o mínimo de LGPD art. 18: exclusão de conta a pedido do titular, sem
  destruir o histórico do parceiro que não pediu nada.
- Dar ao usuário poder de revogação real: trocar a senha derruba **todas** as sessões,
  usando o `revokeFamily` que o Épico 4 construiu e que ninguém chama.
- Fechar de vez o acoplamento de `tracking`/`match` a `com.app.couple`, substituindo-o
  por uma porta explícita (`CoupleFacade`) que trafega apenas `UUID`. A semântica de
  "casal do usuário" muda nesta entrega, então é o momento certo de centralizá-la.
- Entregar a primeira tela de configurações do app (`/conta`), sem a qual nada acima
  existe para o usuário.

## Decisões tomadas para este épico

Complementam as decisões D1–D16 do `POST-MVP-TASK.md`, que continuam vinculantes —
em especial **D13** (dissolver, não apagar) e **D1** (~8 usuários conhecidos,
downtime tolerável).

| # | Questão | Decisão | Consequência |
|---|---------|---------|--------------|
| E9.1 | Nome do endpoint de dissolução | **`DELETE /api/couple/me`**, não `POST /api/couple/leave`. | Simétrico com o `GET /api/couple/me` que já existe, e com o `DELETE /api/user/me` da US-007. O verbo HTTP já comunica a destrutividade, sem precisar de um substantivo de ação na URL. |
| E9.2 | Notificações na exclusão de conta | **Apagar** as linhas de `notification` em que o usuário é `recipient_user_id` **ou** `actor_user_id`. | A T9.4 do backlog não citava essa tabela. Não há FK, então nada quebrava — mas o nome do ator aparece na UI e é dado pessoal dele. A D13 protege o histórico do **casal dissolvido**; uma exclusão de conta é um pedido do titular e tem peso maior. |
| E9.3 | Limites de rate limit dos endpoints novos | `coupleDissolve` 5/h, `passwordChange` 5/h, `accountDelete` 3/h, `profileUpdate` 10/h — todos **por usuário**. | Seguem o padrão já existente do `coupleJoinByUser` (20/h): a chave é o `userId`, o consumo acontece no serviço, não no `RateLimitFilter` (que casa por método+path e chaveia por IP). Configuráveis por `app.rate-limit.*`. |
| E9.4 | O que a porta `CoupleFacade` trafega | **Apenas `UUID`** — `Optional<UUID> findActiveCoupleId(UUID userId)`, `UUID requireActiveCoupleId(UUID userId)` e `List<UUID> memberIds(UUID coupleId)`. A entidade `Couple` **nunca** cruza a fronteira. | Sem isso, cada feature filtraria `dissolved_at` por conta própria e a primeira que esquecesse vazaria dado de casal dissolvido. Com a porta, o filtro existe **num lugar só**. Exige a E9.5 para ser viável. |
| E9.5 | Como a associação JPA sobrevive a uma porta só-UUID | **Trocar `@ManyToOne Couple couple` por `@Column(name="couple_id") UUID coupleId`** em `MediaTrack`, `MatchLike` e `MatchReject`. | É a **mesma coluna** no banco e a FK continua declarada lá — **nenhuma migration**. É o único caminho que de fato zera o import de `Couple` em `tracking`/`match`; sem ele, a porta devolveria `UUID` mas a entidade continuaria atravessando a fronteira pela associação. Bônus: o `@EntityGraph` da T2.3 perde o path `"couple"`, ficando só `{"reviews","reviews.user"}`. Custo: JPQL `mt.couple.id` → `mt.coupleId` em 6 queries de `MediaTrackRepository` + 1 de `UserReviewRepository`, `TrackingFacade.createTrackFromMatch(Couple, ...)` → `(UUID, ...)`, `NotificationService.notifyCouple`/`notifyRatingRequest` → `(UUID, ...)` (E9.19), `MediaTrackMapper.memberIds(Couple)` → porta, `RatingRequestService.partnerOf` → porta, e os testes correspondentes. Por isso é **US própria** (US-003), não um item enfiado noutra story. |
| E9.6 | Filtro de casal dissolvido: consulta nova ou cláusula na existente? | **Consulta nova** (`findActiveByUserId`), e o `findByUser1IdOrUser2Id` antigo deixa de ser usado fora do repositório. | Uma cláusula adicionada ao método existente seria invisível na chamada; um método com nome novo obriga cada ponto de uso a ser visitado conscientemente — que é exatamente a proteção que o "ponto de risco da task" pede. |
| E9.7 | `Couple.dissolve()` num casal já dissolvido | **Lança exceção**, não é no-op. | O método de domínio protege o próprio invariante. Na prática o endpoint nunca chega lá (casal dissolvido já não é "ativo" → `404` antes), então a exceção é rede contra bug interno futuro — por exemplo a US-007 chamando `dissolve()` duas vezes na mesma transação. |
| E9.8 | A troca de senha mantém viva a sessão que a fez? | **Não.** Todas as sessões caem, inclusive a atual, e os cookies vêm limpos na resposta. | Se a senha estava comprometida, a sessão do atacante morre junto. O frontend trata redirecionando para `/login` com mensagem explicando por quê. |
| E9.9 | Política de senha reusada na troca | **`@Size(min = 8, max = 72)`**, a mesma anotação da T5.3, aplicada ao `newPassword` do request. | Não existe validador dedicado a reusar — a "política da T5.3" **é** a anotação. Aplicá-la no DTO novo mantém a mensagem de erro idêntica e sob o mesmo `GlobalExceptionHandler`. |
| E9.10 | Dissolução avisa o parceiro em tempo real? | **Não** por STOMP. Mas o ex-parceiro ganha um **aviso estático** em `/join` (US-013). | Notificar em tempo real exigiria evento novo no broker e tratamento de tela para um caso que, com 8 usuários que se conhecem, é conversado fora do app. O aviso estático é barato e evita que o vínculo suma sem nenhuma explicação. |
| E9.11 | Verificação do e-mail novo na edição de perfil | **Não.** Troca direta, sem link de confirmação. | Depende da infra de e-mail transacional, que é o **Épico 10**. A D4 (manter `409` em colisão de e-mail) continua valendo e se aplica igual aqui. |
| E9.12 | Exclusão de conta exige senha? | **Sim**, no corpo da requisição, mesma verificação da troca de senha. | Exclusão é irreversível. Depender só do cookie de sessão significaria que um notebook desbloqueado apaga a conta em dois cliques. |
| E9.13 | Reuso do e-mail após a exclusão | **Liberado.** O e-mail volta a ser cadastrável e o recadastro **não herda nada** — conta nova, sem casal, sem histórico, sem avaliações. | Bloquear o reuso exigiria guardar os e-mails excluídos numa tabela nova — ou seja, continuar armazenando exatamente o dado pessoal que o titular pediu para eliminar, contrariando o motivo da US-007. Registrado por ser contraintuitivo para quem chegar depois. |
| E9.14 | Quem apaga as linhas das outras features na exclusão de conta | **Uma porta por feature.** `UserDeletionService` (em `com.app.user`) orquestra: `CoupleFacade.dissolveIfActive`, `TrackingFacade.deleteUserData`, `MatchFacade.deleteUserData`, `NotificationFacade.deleteUserData`, `RefreshTokenService.deleteAllForUser`, e por fim o próprio `UserRepository`. | Um serviço injetando os quatro repositórios seria literalmente o anti-pattern #5 — e reintroduziria na US-007 exatamente o acoplamento que a US-003 acaba de remover. `MatchFacade` e `NotificationFacade` **não existem hoje** e nascem aqui, cada uma com um único método. A ordem das chamadas continua sendo a do FR-17, e a transação única fica no orquestrador. |
| E9.15 | DTO de resposta do perfil | **`UserProfileResponse` novo em `com.app.user`.** Não reusar `com.app.auth.UserSummary`. | `UserSummary` vive em `com.app.auth`, é parte do contrato de login/`/api/auth/me`, e não é importado por ninguém fora de `auth` hoje. Fazer `com.app.user` depender dele acoplaria o contrato do perfil ao contrato da sessão — mudar um passaria a arriscar o outro. Os dois records serem parecidos é custo aceitável. |
| E9.16 | Exceção lançada por `requireActiveCoupleId` | **`com.app.common.ResourceNotFoundException`**, não `CoupleNotFoundException`. | `CoupleNotFoundException` vive dentro de `com.app.couple` e é tratada pelo `CoupleExceptionHandler` da própria feature; fazer `tracking`/`match` a capturarem seria o anti-pattern #2. `ResourceNotFoundException` é o tipo transversal de `com.app.common` que `tracking` e `match` **já** usam hoje nesse mesmo ponto — o comportamento observável (404) não muda. |
| E9.17 | O que acontece com o `invite_code` ao dissolver | **`dissolve()` limpa o código** (`clearInviteCode()`, o método que a US-008 do Épico 5 já criou), **e** `CoupleService.joinCouple` passa a resolver por uma consulta que ignora casal dissolvido. Cinto e suspensório. | `joinCouple` resolve o convite por `findByInviteCode`, não pelo casal do usuário — nenhum filtro de "casal ativo" no caminho de entrada passa por ali. Sem limpar, este cenário entra sem erro nenhum: A cria o casal, manda o código, dissolve; B digita o código, vira `user2_id` de um casal morto e leva `404` no `GET /api/couple/me` seguinte. Limpar resolve os casais dissolvidos daqui pra frente; o filtro na consulta cobre linhas legadas e um `dissolve()` futuro que esqueça de limpar. |
| E9.18 | Notificações de um casal dissolvido | **Filtrar, não apagar** — as duas consultas por destinatário (`findByRecipientUserIdOrderByCreatedAtDesc` e `countByRecipientUserIdAndReadFalse`) passam a ser escopadas ao **casal ativo** do usuário. Sem casal ativo, lista vazia e contador zero. | É a única AC da US-004 que **não** sai de graça. `tracking` e `match` resolvem o casal no controller e já devolvem `404` depois da dissolução; `notification` filtra só por `recipient_user_id`, então os MATCH/NO_MATCH do casal antigo continuariam listados e contados no sino. Apagar seria mais simples, mas contraria a D13 (dissolver, não apagar) — e o mesmo dado volta a fazer sentido se o histórico algum dia for exposto. Filtrar mantém a linha no banco e fora do alcance, que é exatamente o contrato do FR-7. |
| E9.19 | `NotificationService` entra no escopo da US-003? | **Sim.** `notifyCouple` e `notifyRatingRequest` passam a receber `UUID coupleId` + os ids dos membros, em vez da entidade `Couple`. **`com.app.websocket` fica de fora**, explicitamente. | Sem isso a US-003 **não passa no próprio grep**: quem chama esses dois métodos é `MatchService` e `RatingRequestService`, ou seja, o import de `com.app.couple.Couple` sobrevive dentro de `tracking`/`match` mesmo depois de trocar as entidades por `UUID`. O `CoupleDestinationAuthorizationManager` também importa `Couple`, mas está em `com.app.websocket` — que é infraestrutura de transporte, não uma feature de domínio, e cujo acoplamento não é o que o anti-pattern #5 descreve. Fica como está e herda o filtro de casal ativo de graça. |
| E9.20 | `findByUser1IdOrUser2Id` é evitado ou removido? | **Removido do `CoupleRepository`.** A E9.6 dizia "deixa de ser usado fora do repositório" — não basta. | O método devolve `Optional`. A partir da US-004 um usuário pode ter **duas** linhas em `couples` (uma dissolvida, uma nova), e aí ele lança `IncorrectResultSizeDataAccessException` → `500`, exatamente no fluxo que este épico existe para destravar. Enquanto o método existir, qualquer caller esquecido é uma bomba-relógio. Callers hoje: `CoupleService.createCouple`, `getCurrentCouple`, `joinCouple`, `regenerateInviteCode` — e, via `getCurrentCouple`, o `CoupleDestinationAuthorizationManager`. Apagar o método transforma "esqueci de migrar um caller" em erro de compilação. |

## User Stories

**Ordem obrigatória:** US-001 → US-002 → US-003 → US-004 (o backend do casal é
sequencial e cada passo depende do anterior). US-005, US-006 e US-007 dependem da
US-004 (a US-007 dissolve o casal reusando a porta). US-008 a US-013 (frontend)
dependem dos endpoints correspondentes já existirem. A US-014 fecha o épico.

---

### US-001: Migration V7 e o conceito de casal dissolvido

**Description:** As a desenvolvedor, I want a coluna `dissolved_at` no `couples` e o
comportamento de dissolução na própria entidade so that exista um lugar único e
testável definindo o que "casal dissolvido" significa.

**Acceptance Criteria:**
- [ ] Existe `api/src/main/resources/db/migration/V7__add_couple_dissolved_at.sql` com `ALTER TABLE couples ADD COLUMN IF NOT EXISTS dissolved_at TIMESTAMP WITH TIME ZONE;` — aditivo e idempotente, sem `DROP`/`TRUNCATE`, seguindo as convenções de `api/CLAUDE.md` e `docs/FLYWAY.md`.
- [ ] `Couple` tem o campo `dissolvedAt` (`Instant`, nullable) e o método de domínio `dissolve()`.
- [ ] `dissolve()` num casal **já dissolvido lança exceção** (E9.7), com teste cobrindo os dois casos — primeira chamada grava o instante, segunda lança.
- [ ] `dissolve()` **também limpa o `invite_code`** (E9.17), reusando o `clearInviteCode()` que já existe na entidade — com teste provando que o código sai `NULL` junto com o `dissolved_at`.
- [ ] `CoupleRepository.findActiveByUserId(UUID)` existe, filtrando `dissolved_at IS NULL`, com teste de repositório provando que um casal dissolvido não é retornado.
- [ ] `CoupleRepository.findActiveByInviteCode(String)` existe, filtrando `dissolved_at IS NULL`, e `findByInviteCode` é **removido** — é o caminho de entrada do convite (E9.17) e não pode ficar sem filtro. Teste de repositório: um casal dissolvido com `invite_code` preenchido à mão (linha legada) não é retornado.
- [ ] O teste dedicado de migrations (`FlywayMigrationTest`) sobe com `V1`–`V7` aplicadas em sequência e `ddl-auto=validate` verde — ou seja, a entidade bate com o schema.
- [ ] `docs/FLYWAY.md` ganha a linha da `V7` na lista de migrations do corpo do documento, e a ressalva do pooler do Supabase (porta 5432 direta ou pooler em modo Session no deploy que a carregar) é mencionada para esta migration, no mesmo formato usado para V3–V5. **A mesma lista está faltando a linha da `V6`** — ela só aparece na seção de gate de 2026-08-06. Acrescentar as duas de uma vez.
- [ ] `./mvnw test` passa.

---

### US-002: Porta `CoupleFacade` — só `UUID` cruza a fronteira

**Description:** As a mantenedor, I want uma porta explícita em `com.app.couple` que
trafega apenas identificadores so that outras features resolvam o casal do usuário
sem conhecer a entidade, e o filtro de casal dissolvido exista num lugar só.

**Depende de:** US-001

**Acceptance Criteria:**
- [ ] Existe `com.app.couple.CoupleFacade` expondo, no mínimo:
  - `Optional<UUID> findActiveCoupleId(UUID userId)`
  - `UUID requireActiveCoupleId(UUID userId)` — lança `com.app.common.ResourceNotFoundException` quando não há casal ativo (E9.16), preservando o 404 que `tracking`/`match` já produzem hoje
  - `List<UUID> memberIds(UUID coupleId)` — os ids dos membros, pulando um `user2Id` nulo (casal ainda sem parceiro), replicando a semântica atual de `MediaTrackMapper.memberIds`
  - `void dissolveIfActive(UUID userId)` — dissolve o casal do usuário se houver um ativo, e é **no-op** se não houver. Consumido pela US-007, que não pode esbarrar na exceção da E9.7 ao excluir a conta de quem já está sem casal
- [ ] **Nenhum método da porta retorna, aceita ou expõe a entidade `Couple`** (E9.4).
- [ ] A porta resolve o casal por `findActiveByUserId` — um casal dissolvido nunca é retornado.
- [ ] `CoupleService` continua sendo o dono da regra; a porta é fachada sobre ele, não uma segunda implementação da mesma lógica.
- [ ] **`findByUser1IdOrUser2Id` é removido do `CoupleRepository`** (E9.20) e os quatro callers de `CoupleService` (`createCouple`, `getCurrentCouple`, `joinCouple`, `regenerateInviteCode`) passam a usar `findActiveByUserId`. Verificação objetiva: `grep -rn "findByUser1IdOrUser2Id" api/src` retorna **vazio**.
- [ ] `CoupleDestinationAuthorizationManager` (autorização dos tópicos STOMP) resolve o casal por `getCurrentCouple` e herda o filtro sem alteração de código — com teste provando que um ex-membro **não** consegue assinar `/topic/couple/{id}/**` do casal dissolvido.
- [ ] Existe `CoupleFacadeTest` cobrindo: casal ativo, casal dissolvido, usuário sem casal, `memberIds` com e sem parceiro, e `dissolveIfActive` nos dois caminhos (com casal e sem casal, este último sem lançar).
- [ ] `./mvnw test` passa e o gate do JaCoCo (90% de linha) continua verde.

---

### US-003: Trocar a associação `Couple` por `UUID` em `tracking` e `match`

**Description:** As a mantenedor, I want as entidades de `tracking` e `match`
guardando `couple_id` como `UUID` em vez de uma associação para `Couple` so that o
import da entidade e do repositório de outra feature desapareça de vez.

**Depende de:** US-002

**Nota:** refatoração de mapeamento, **sem mudança de schema e sem migration** — é a
mesma coluna `couple_id`, e a FK continua declarada no banco pela `V1`. Nenhum
contrato de API muda.

**Acceptance Criteria:**
- [ ] `MediaTrack`, `MatchLike` e `MatchReject` trocam `@ManyToOne(fetch = LAZY) @JoinColumn(name="couple_id") private Couple couple` por `@Column(name="couple_id", nullable = false) private UUID coupleId`.
- [ ] **Nenhuma migration é criada.** O teste de migrations da T2.0 continua verde com `ddl-auto=validate`, provando que o mapeamento novo bate com o schema existente.
- [ ] As **6** queries JPQL de `MediaTrackRepository` passam de `mt.couple.id` para `mt.coupleId`, e `UserReviewRepository` passa de `ur.mediaTrack.couple.id` para `ur.mediaTrack.coupleId`. Os derived queries `findByCoupleId*`/`existsByCoupleId*`/`countByCoupleId*` continuam funcionando sem mudança de assinatura.
- [ ] Os **dois** `@EntityGraph` de `MediaTrackRepository` (`findByCoupleIdAndStatus` e `findByIdIn`) perdem o path `"couple"`, ficando `{"reviews", "reviews.user"}`.
- [ ] `MediaTrackMapper.memberIds(Couple)` some; o mapper passa a receber os nomes já resolvidos, obtidos via `CoupleFacade.memberIds(coupleId)` — o padrão de resolução única por request estabelecido na T2.2 é preservado (**no máximo 1 query em `users`** por request, verificável por contador de queries no teste).
- [ ] `TrackingFacade.createTrackFromMatch(Couple couple, ...)` passa a receber `UUID coupleId`.
- [ ] **`NotificationService.notifyCouple(Couple, ...)` e `notifyRatingRequest(Couple, ...)` passam a receber `UUID coupleId`** (E9.19) — os ids dos membros que hoje saem da entidade vêm do `CoupleFacade.memberIds`. Sem isso o grep abaixo **não fecha**: quem chama esses métodos é `MatchService` e `RatingRequestService`, dentro das features que este AC quer limpar.
- [ ] `RatingRequestService` deixa de usar `track.getCouple()` para achar o parceiro (`partnerOf`) e passa a resolver por `CoupleFacade.memberIds(coupleId)` — **uma** chamada por operação, nunca uma por track/notificação (é o caminho quente da T2.2; o mesmo teste de contador de queries cobre este ponto).
- [ ] `MediaTrackController` e `MatchController` resolvem o casal por `CoupleFacade`, não por `CoupleService` + entidade `Couple`. `MediaTrackService` e `MatchService` param de injetar `CoupleRepository`.
- [ ] Verificação objetiva: `grep -rn "com.app.couple.Couple\b\|com.app.couple.CoupleRepository\|com.app.couple.CoupleService" api/src/main/java/com/app/tracking api/src/main/java/com/app/match` retorna **vazio**.
- [ ] Nenhum endpoint muda de contrato: o JSON de resposta de `/api/tracking`, `/api/tracking/stats`, `/api/tracking/keys` e `/api/match` é idêntico ao anterior.
- [ ] Todos os testes existentes de `tracking` e `match` passam, ajustados ao mapeamento novo.
- [ ] `./mvnw test` passa e o gate do JaCoCo continua verde.

---

### US-004: `DELETE /api/couple/me` — dissolver o vínculo

**Description:** As a usuário que entrou no casal errado (ou cujo relacionamento
acabou), I want desfazer o vínculo sozinho so that eu possa formar um casal novo sem
depender de intervenção manual no banco.

**Depende de:** US-003

**Acceptance Criteria:**
- [ ] `DELETE /api/couple/me` dissolve o casal do usuário autenticado e responde `204`, sem corpo.
- [ ] A ação é **unilateral**: qualquer um dos dois membros dissolve, sem confirmação do parceiro.
- [ ] Depois de dissolver, `GET /api/couple/me` responde `404` para **ambos** os ex-membros.
- [ ] Depois de dissolver, ambos conseguem `POST /api/couple` (criar) e `POST /api/couple/join` (entrar) num casal novo, sem `UserAlreadyInCoupleException`.
- [ ] O `invite_code` do casal é limpo na dissolução (E9.17): um `POST /api/couple/join` com o código de um casal dissolvido responde **`404` de convite inexistente**, nunca entra no casal morto — com teste cobrindo tanto o caso normal (código limpo pelo `dissolve()`) quanto a linha legada (código preenchido à mão + `dissolved_at`, barrado por `findActiveByInviteCode`).
- [ ] Nenhum endpoint de `tracking`, `match` ou `notification` retorna dado do casal dissolvido para os ex-membros — verificado por teste de integração que cria tracks, dissolve e reconsulta.
- [ ] Especificamente para `notification` (E9.18): `GET /api/notifications` e `GET /api/notifications/unread-count` passam a ser **escopados ao casal ativo** do usuário. Depois de dissolver, a lista vem vazia e o contador zera para ambos os ex-membros; formado um casal novo, só as notificações dele aparecem. Isso exige mudar `findByRecipientUserIdOrderByCreatedAtDesc` e `countByRecipientUserIdAndReadFalse` — hoje filtram só por destinatário — e é a única AC desta story que não é consequência automática do filtro de casal ativo.
- [ ] As linhas de `media_track`, `user_review` e `notification` do casal dissolvido **continuam no banco** — verificado por contagem antes/depois no mesmo teste.
- [ ] `DELETE /api/couple/me` sem casal ativo responde `404`, não `500`.
- [ ] Dissolver duas vezes: a segunda chamada responde `404` (o casal já não é ativo), sem que a exceção da E9.7 escape como `500`.
- [ ] O evento é registrado pelo `SecurityAuditLogger` num método novo `coupleDissolved(userId, coupleId)`, no mesmo formato dos eventos existentes.
- [ ] Rate limit **por usuário** de 5/hora, com a propriedade `app.rate-limit.couple-dissolve` em `RateLimitProperties` (`Limit` **próprio**, não reaproveitado do `coupleJoinByUser`), consumida no `CoupleService` no mesmo padrão de `enforceJoinRateLimit` (chave `couple-dissolve:user:<id>`). Exceder retorna `429` com `Retry-After`.
- [ ] `CoupleServiceTest` e `CoupleControllerTest` cobrem: dissolver com sucesso, dissolver duas vezes, dissolver sem casal, recriar casal depois de dissolver, entrar com o código de convite de um casal dissolvido, e o bloqueio por rate limit.
- [ ] Teste explícito do cenário que a E9.20 protege: usuário com **um casal dissolvido e um ativo** responde `200` no `GET /api/couple/me` (não `500`), e o mesmo vale para `POST /api/couple/invite-code/regenerate`.

---

### US-005: `PATCH /api/user/me` — editar nome e e-mail

**Description:** As a usuário que errou o nome no cadastro ou trocou de e-mail, I
want corrigir meus dados so that a conta reflita quem eu sou hoje.

**Depende de:** US-004 (só por ordem de entrega; não há dependência técnica)

**Acceptance Criteria:**
- [ ] Existem `UserProfileController` e `UserProfileService` em `com.app.user` — a feature dona do dado.
- [ ] `PATCH /api/user/me` aceita `name`, `email`, ou ambos, e responde `200` com um `UserProfileResponse` **próprio de `com.app.user`** — `com.app.auth.UserSummary` não é reusado nem importado (E9.15).
- [ ] Campo ausente no corpo não é alterado (semântica de `PATCH`, não de `PUT`).
- [ ] E-mail já usado por outra conta responde `409`, reusando o `EmailAlreadyExistsException` de `com.app.auth` — coerente com a D4.
- [ ] E-mail malformado ou nome em branco respondem `400`, no mesmo formato de erro de validação dos demais endpoints (`GlobalExceptionHandler`).
- [ ] `GET /api/auth/me` reflete os valores novos na requisição seguinte.
- [ ] A sessão **continua válida** depois da troca de e-mail — o token carrega `userId`, não e-mail.
- [ ] O evento é registrado pelo `SecurityAuditLogger` (`profileUpdated`), **sem logar o valor novo do e-mail** (só quais campos mudaram).
- [ ] O controller **não** injeta `UserRepository` (anti-pattern #1 da `docs/ARCHITECTURE.md`).
- [ ] Rate limit por usuário de 10/hora (`app.rate-limit.profile-update`), `429` com `Retry-After` ao exceder.
- [ ] `UserProfileServiceTest` e `UserProfileControllerTest` cobrem sucesso (cada campo e ambos), conflito de e-mail, payload inválido e rate limit.

---

### US-006: `PUT /api/auth/password` — trocar a senha estando autenticado

**Description:** As a usuário que suspeita que a senha vazou, I want trocá-la e
derrubar todas as sessões ativas so that um eventual atacante perca o acesso na hora.

**Depende de:** US-004 (só por ordem de entrega)

**Acceptance Criteria:**
- [ ] `PUT /api/auth/password` aceita `currentPassword` + `newPassword` e responde `204` no sucesso.
- [ ] Senha atual errada responde `401` e **nada** é alterado — nem a senha, nem as sessões.
- [ ] `newPassword` é validado com `@Size(min = 8, max = 72)`, a mesma política da T5.3, e uma senha fora dela responde `400` com a mesma mensagem do cadastro (E9.9).
- [ ] A senha nova é gravada com o mesmo `PasswordEncoder` usado no login.
- [ ] `RefreshTokenService.revokeFamily(userId)` é chamado: **todas** as sessões caem, inclusive a que fez a troca (E9.8).
- [ ] A resposta limpa os cookies `access_token` e `refresh_token` via `AuthCookieService.expiredAccessTokenCookie()`/`expiredRefreshTokenCookie()`.
- [ ] Depois da troca, `POST /api/auth/refresh` com o refresh anterior responde `401`.
- [ ] Depois da troca, o login com a senha nova funciona e com a antiga responde `401`.
- [ ] O evento é registrado pelo `SecurityAuditLogger` (`passwordChanged`), sem nenhuma das duas senhas.
- [ ] Rate limit por usuário de 5/hora (`app.rate-limit.password-change`), `429` com `Retry-After`.
- [ ] `AuthServiceTest` e `AuthControllerTest` cobrem os cinco cenários acima mais o rate limit.

---

### US-007: `DELETE /api/user/me` — excluir a conta

**Description:** As a usuário que quer sair do app, I want apagar minha conta e meus
dados pessoais so that eu exerça o direito de eliminação previsto na LGPD sem
destruir o histórico do meu ex-parceiro.

**Depende de:** US-004 e US-006 (reusa a porta de dissolução e a verificação de senha)

**Acceptance Criteria:**
- [ ] `DELETE /api/user/me` exige `password` no corpo (E9.12) e responde `204` no sucesso.
- [ ] Senha errada responde `401` e **nada** é apagado.
- [ ] Existe `UserDeletionService` em `com.app.user`, que **orquestra** a exclusão chamando uma porta por feature (E9.14) — nunca um repositório de outra feature. Verificação objetiva: `grep -rn "import com.app.tracking\.\|import com.app.match\.\|import com.app.notification\." api/src/main/java/com/app/user` só pode casar as portas (`*Facade`), nunca `*Repository` nem entidade.
- [ ] Nascem duas portas novas, cada uma com um único método: `com.app.match.MatchFacade.deleteUserData(UUID userId)` e `com.app.notification.NotificationFacade.deleteUserData(UUID userId)`. `TrackingFacade` ganha `deleteUserData(UUID userId)`; `RefreshTokenService` ganha `deleteAllForUser(UUID userId)`.
- [ ] A operação roda em **uma transação**, nesta ordem, respeitando as FKs de `V1`/`V5`:
  1. `coupleFacade.dissolveIfActive(userId)` — no-op se não houver casal ativo, sem esbarrar na exceção da E9.7;
  2. `trackingFacade.deleteUserData(userId)` — apaga os `user_review` do usuário (`fk_user_review_user`);
  3. `refreshTokenService.deleteAllForUser(userId)` — apaga os `refresh_token` (`fk_refresh_token_user`) em **um único statement** (ver AC da auto-FK abaixo);
  4. `matchFacade.deleteUserData(userId)` — apaga os `match_like` e `match_reject` com o `user_id` dele;
  5. `notificationFacade.deleteUserData(userId)` — apaga as `notification` em que ele é `recipient_user_id` **ou** `actor_user_id` (E9.2);
  6. `userRepository.delete(user)`.
- [ ] `refreshTokenService.deleteAllForUser` é um **`@Modifying @Query` de statement único** (`delete from RefreshToken r where r.userId = :userId`), nunca um `deleteAll(entidades)`. Motivo: `refresh_token` tem auto-FK `fk_refresh_token_replaced_by`, e apagar linha a linha viola a constraint assim que um token ainda referenciado por `replaced_by_id` de outro token do mesmo usuário sai primeiro. Num `DELETE` único o Postgres checa a integridade só no fim do statement, e a cadeia inteira cai junto. Teste obrigatório: usuário com uma **cadeia de rotação** (pelo menos 3 tokens encadeados por `replaced_by_id`), contra o Postgres real.
- [ ] Nenhuma linha de `couples` é apagada em momento algum — a dissolução é `UPDATE dissolved_at`. `notification.couple_id` é `NOT NULL` com FK para `couples`, e `media_track` também referencia a tabela: um `DELETE` ali quebraria as duas.
- [ ] Cada porta nova apaga **apenas** o que pertence à sua feature e tem teste próprio provando o escopo — em especial, `trackingFacade.deleteUserData` **não** toca em `media_track`.
- [ ] Os `media_track` do casal **permanecem** no banco — pertencem ao `couple_id`, não ao usuário (D13). Verificado por contagem no teste.
- [ ] Depois da exclusão, o login com aquele e-mail responde `401` e o e-mail fica **livre** para um cadastro novo (`POST /api/auth/register` responde `201`, não `409`), e a conta nova **não herda nada** (E9.13) — verificado por teste.
- [ ] O ex-parceiro consegue formar um casal novo depois da exclusão.
- [ ] **Nenhuma violação de FK**: existe teste de integração rodando contra o **Postgres real do CI** (o mesmo arranjo da T2.0, `@AutoConfigureTestDatabase(replace = NONE)`), não só mock — H2 não é prova suficiente aqui.
- [ ] A exclusão é **atômica**: um teste que força falha no meio da operação confirma rollback completo (conta intacta, nada meio-apagado).
- [ ] O evento é registrado pelo `SecurityAuditLogger` (`accountDeleted`), com o `userId` e sem o e-mail completo.
- [ ] Rate limit por usuário de 3/hora (`app.rate-limit.account-delete`).
- [ ] Testes de service e controller cobrindo: sucesso com casal, sucesso sem casal, senha errada, atomicidade e integridade referencial.

---

### US-008: Rota `/conta` — casca, layout e entrada no `Header`

**Description:** As a usuário, I want uma tela de Conta alcançável pela navegação so
that eu descubra que essas ações existem sem precisar de uma URL decorada.

**Depende de:** US-004 a US-007

**Acceptance Criteria:**
- [ ] Existe a rota **protegida** `/conta` em `client/src/App.tsx`, dentro de `ProtectedRoute` — e **fora** do guard `RequireCouple`, que envolve `/hub`, `/match` e `/dashboard`. Quem não tem casal (inclusive quem acabou de dissolver) precisa chegar na tela; é a US-011 que trata o estado vazio.
- [ ] A tela é alcançável pelo `Header` — tanto no menu desktop quanto no menu mobile já existentes (`client/src/components/Header.tsx`), sem quebrar o layout dos itens atuais.
- [ ] A tela renderiza os quatro blocos (Perfil, Senha, Casal, Excluir conta) como seções distintas e rotuladas, ainda que o conteúdo funcional venha nas US-009 a US-012.
- [ ] O visual é coerente com o design system já em uso no app e com o protótipo em `docs/design/claude-design-project/`: fundo `#09090a`, cartões `#161513` com borda `rgba(255,255,255,.07)` e raio de 18px, títulos em `Bricolage Grotesque`, corpo em `DM Sans`, acento âmbar `#ffcb2b`, texto secundário `#a6a39a`.
- [ ] A **skill `frontend-design` foi invocada antes** de escrever qualquer código desta story — é UI nova, e a regra do `CLAUDE.md` da raiz vale integralmente (a dispensa da D11 é só para refatoração sem mudança visual).
- [ ] `bun run typecheck`, `bun run lint` e `bun run build` passam.
- [ ] Verificado no browser com a skill `dev-browser`.

---

### US-009: Bloco Perfil — editar nome e e-mail

**Description:** As a usuário, I want editar meu nome e e-mail na tela de Conta so
that eu corrija meus dados sem sair do app.

**Depende de:** US-005 e US-008

**Acceptance Criteria:**
- [ ] O bloco carrega os valores atuais do usuário a partir do `useAuthStore` e permite editar ambos os campos.
- [ ] Salvar chama `PATCH /api/user/me` e atualiza o `user` do `useAuthStore` com a resposta — o `Header` reflete o nome novo sem reload.
- [ ] `409` (e-mail em uso) vira mensagem visível e específica junto ao campo de e-mail.
- [ ] `400` (validação) vira mensagem visível, sem `.catch(() => {})` em lugar nenhum (anti-pattern #4).
- [ ] Botão de salvar fica desabilitado enquanto a requisição está em voo e mostra estado de carregamento.
- [ ] Sucesso mostra confirmação visível.
- [ ] Os tipos do request/response estão espelhados à mão em `client/src/types/` — não há codegen (`client/CLAUDE.md`).
- [ ] `bun run typecheck` e `bun run lint` passam. Verificado no browser com a skill `dev-browser`.

---

### US-010: Bloco Senha — trocar a senha

**Description:** As a usuário, I want trocar minha senha na tela de Conta so that eu
reaja a uma suspeita de vazamento sem depender de suporte.

**Depende de:** US-006 e US-008

**Acceptance Criteria:**
- [ ] O bloco tem os campos senha atual, senha nova e confirmação da senha nova.
- [ ] Os requisitos da senha (8 a 72 caracteres) aparecem **antes** do submit, não só como erro depois.
- [ ] Confirmação divergente é barrada no cliente, sem chamar a API.
- [ ] Submeter chama `PUT /api/auth/password`.
- [ ] `401` (senha atual errada) vira mensagem visível junto ao campo de senha atual.
- [ ] No sucesso, a tela **avisa que todas as sessões foram encerradas**, limpa o estado do `useAuthStore`, desconecta o WebSocket da `useMatchStore` e redireciona para `/login` — a sessão atual caiu junto (E9.8).
- [ ] `bun run typecheck` e `bun run lint` passam. Verificado no browser com a skill `dev-browser`.

---

### US-011: Bloco Casal — desfazer o vínculo

**Description:** As a usuário, I want desfazer o vínculo do casal pela tela de Conta
so that eu corrija um pareamento errado ou encerre o relacionamento no app.

**Depende de:** US-004 e US-008

**Acceptance Criteria:**
- [ ] O bloco mostra o parceiro atual (nome/avatar, reusando o que o `CoupleAvatars` já faz) e a data de início do vínculo, quando disponível.
- [ ] Sem casal ativo, o bloco mostra estado vazio com caminho para `/join`, em vez de erro.
- [ ] A ação de desfazer exige **confirmação explícita** num diálogo que diz exatamente o que acontece: o vínculo acaba, o histórico do casal deixa de ser acessível para os dois, e ambos ficam livres para formar um casal novo do zero.
- [ ] Confirmar chama `DELETE /api/couple/me`.
- [ ] No sucesso, o `couple` do `useAuthStore` é limpo **e** a conexão STOMP da `useMatchStore` é desconectada — senão a store fica assinando o tópico de um casal que não existe mais.
- [ ] Depois do sucesso, o app leva o usuário para o fluxo de convite (`/join`).
- [ ] `404` e `429` da API viram mensagem visível, cada um com texto próprio (sem casal / muitas tentativas).
- [ ] `bun run typecheck` e `bun run lint` passam. Verificado no browser com a skill `dev-browser`.

---

### US-012: Bloco Excluir conta — zona destrutiva

**Description:** As a usuário, I want apagar minha conta pela tela de Conta so that eu
saia do app definitivamente sem pedir nada a ninguém.

**Depende de:** US-007 e US-008

**Acceptance Criteria:**
- [ ] O bloco é visualmente destacado como zona destrutiva, separado dos demais.
- [ ] A exclusão exige **duas** barreiras: a senha atual **e** confirmação por digitação (o usuário digita uma palavra de confirmação exibida na tela).
- [ ] O diálogo diz explicitamente o que será apagado (conta, avaliações, notificações, sessões) e o que **permanece** (o histórico de títulos do casal, que fica com o ex-parceiro).
- [ ] Confirmar chama `DELETE /api/user/me` com a senha no corpo.
- [ ] `401` (senha errada) vira mensagem visível e **não** fecha o diálogo.
- [ ] No sucesso, todo o estado local é limpo — `useAuthStore` (que já expõe a limpeza da chave única `SESSION_STORAGE_KEY` do `localStorage`, em `client/src/stores/useAuthStore.ts`), `useMatchStore` desconectado — e o usuário vai para a landing (`/`).
- [ ] `bun run typecheck`, `bun run lint` e `bun run build` passam. Verificado no browser com a skill `dev-browser`.

---

### US-013: Aviso em `/join` para o ex-parceiro

**Description:** As a usuário cujo parceiro desfez o vínculo sem avisar, I want
entender por que o casal sumiu so that eu não ache que o app perdeu meus dados.

**Depende de:** US-011

**Nota:** é o meio-termo da E9.10 — sem STOMP, sem notificação, só um estado de tela.

**Acceptance Criteria:**
- [ ] Quando `GET /api/auth/me` (ou `GET /api/couple/me`) volta sem casal **e** o cliente tinha um `couple` no cache de UI (`SESSION_STORAGE_KEY` do `useAuthStore`), `/join` exibe um aviso do tipo "seu vínculo foi desfeito" com explicação curta do que acontece com o histórico.
- [ ] O gatilho é exatamente esse cache — nenhuma flag paralela nova em `localStorage`. Consequência de graça: quem **fez** a dissolução não vê o aviso, porque a US-011 já limpou o `couple` do store no sucesso.
- [ ] O aviso aparece **uma vez**: depois de visto (ou depois de formar um casal novo), o cache que o dispara é limpo e ele não volta.
- [ ] Um usuário que nunca teve casal vê o `/join` normal, sem nenhum aviso — verificado explicitamente.
- [ ] O usuário que **fez** a dissolução não vê o aviso (o fluxo dele já explicou tudo no diálogo da US-011).
- [ ] `bun run typecheck` e `bun run lint` passam. Verificado no browser com a skill `dev-browser`.

---

### US-014: Fechar a documentação do épico

**Description:** As a próximo desenvolvedor (ou agente), I want a documentação
refletindo os endpoints, a porta e o mapeamento novos so that eu não descubra o
`CoupleFacade` e o `dissolved_at` só lendo o código.

**Depende de:** US-001 a US-013

**Acceptance Criteria:**
- [ ] `docs/ARCHITECTURE.md` seção 3 descreve `com.app.user` com os endpoints de perfil e exclusão (hoje diz "apenas a entidade `User` e `UserRepository`") e registra o `CoupleFacade` como porta de `com.app.couple`, ao lado do `TrackingFacade`, deixando explícito que ela trafega **só `UUID`**.
- [ ] `docs/ARCHITECTURE.md` seção 3 atualiza a descrição de `com.app.match`, que hoje diz depender de `tracking` só pelo `TrackingFacade` — passa a valer o mesmo para `couple` via `CoupleFacade` — e registra o `MatchFacade` e o `NotificationFacade` como portas novas das respectivas features.
- [ ] `docs/ARCHITECTURE.md` seção 4 registra `dissolved_at` nos campos da entidade `Couple`, a regra de negócio da dissolução (unilateral, preserva o histórico, libera os dois para um casal novo) e o fato de `MediaTrack` guardar `couple_id` como `UUID`, sem associação JPA.
- [ ] `docs/FLYWAY.md` lista a `V6` e a `V7` na lista de migrations do corpo (feito na US-001 — aqui só se confere que continua correto ao fim do épico).
- [ ] `docs/ARCHITECTURE.md` seção 3 registra que `com.app.notification` passou a receber `UUID coupleId` em vez da entidade `Couple`, e que `com.app.websocket` é a exceção deliberada à regra de dependência (E9.19).
- [ ] `POST-MVP-TASK.md` tem as T9.1–T9.5 marcadas como concluídas, no mesmo formato usado pelos épicos anteriores.
- [ ] `api/CLAUDE.md` e `client/CLAUDE.md` mencionam os endpoints novos onde já listam os existentes.

## Requisitos Funcionais

- **FR-1:** A tabela `couples` deve ter a coluna `dissolved_at` (`TIMESTAMP WITH TIME ZONE`, nullable), adicionada pela migration `V7` — a única migration do épico.
- **FR-2:** Um casal com `dissolved_at` preenchido não deve ser retornado por nenhuma consulta que resolva "o casal do usuário".
- **FR-3:** `Couple.dissolve()` deve lançar exceção quando o casal já estiver dissolvido.
- **FR-4:** `DELETE /api/couple/me` deve marcar `dissolved_at = now()` e responder `204`; sem casal ativo, `404`.
- **FR-4a:** A dissolução deve limpar o `invite_code` do casal, e a resolução do convite (`POST /api/couple/join`) deve ignorar casais dissolvidos — um código de casal dissolvido nunca pode formar vínculo.
- **FR-5:** A dissolução deve ser unilateral — não exige confirmação do parceiro nem o notifica em tempo real.
- **FR-6:** Após a dissolução, ambos os ex-membros devem poder criar ou entrar em um casal novo.
- **FR-7:** Os dados de `media_track`, `user_review` e `notification` do casal dissolvido devem permanecer no banco, sem nenhum caminho de acesso pela API — incluindo a listagem e o contador de não lidas de `notification`, que devem passar a ser escopados ao casal ativo do usuário, e a assinatura dos tópicos STOMP do casal, que deve ser negada aos ex-membros.
- **FR-8:** `com.app.couple` deve expor a porta `CoupleFacade`, que trafega apenas `UUID` e nunca a entidade `Couple`.
- **FR-9:** `com.app.tracking` e `com.app.match` não devem importar `Couple`, `CoupleRepository` nem `CoupleService` — a comunicação passa exclusivamente pelo `CoupleFacade`.
- **FR-9a:** Para que o FR-9 seja alcançável, `NotificationService.notifyCouple` e `notifyRatingRequest` devem receber `UUID coupleId` em vez da entidade `Couple`. `com.app.websocket` está fora deste requisito.
- **FR-9b:** `CoupleRepository.findByUser1IdOrUser2Id` e `findByInviteCode` devem ser removidos, substituídos pelas variantes que filtram `dissolved_at IS NULL`. Nenhum caminho de resolução de casal pode existir sem esse filtro.
- **FR-10:** `MediaTrack`, `MatchLike` e `MatchReject` devem guardar `couple_id` como coluna `UUID`, sem associação JPA para `Couple`, **sem alteração de schema**.
- **FR-11:** `PATCH /api/user/me` deve alterar `name` e/ou `email` do usuário autenticado, respondendo `200` com o perfil atualizado, `409` em colisão de e-mail e `400` em payload inválido.
- **FR-12:** A troca de e-mail não deve invalidar a sessão.
- **FR-13:** `PUT /api/auth/password` deve exigir `currentPassword` e `newPassword`, responder `204` no sucesso e `401` quando a senha atual estiver errada.
- **FR-14:** `newPassword` deve obedecer `@Size(min = 8, max = 72)`, a mesma política do cadastro.
- **FR-15:** A troca de senha deve chamar `RefreshTokenService.revokeFamily(userId)` e limpar os cookies `access_token` e `refresh_token` na resposta.
- **FR-16:** `DELETE /api/user/me` deve exigir a senha atual no corpo e responder `401` se ela estiver errada, sem apagar nada.
- **FR-17:** A exclusão de conta deve, em uma única transação: dissolver o casal (se houver e se ainda ativo), apagar `user_review`, `refresh_token`, `match_like`, `match_reject` e `notification` do usuário, e por fim a linha de `users`.
- **FR-17a:** Cada passo do FR-17 deve ser executado por uma **porta da feature dona do dado** (`CoupleFacade`, `TrackingFacade`, `MatchFacade`, `NotificationFacade`, `RefreshTokenService`). `com.app.user` não deve importar repositório nem entidade de outra feature.
- **FR-17b:** A remoção dos `refresh_token` deve acontecer em um único statement SQL, por causa da auto-FK `fk_refresh_token_replaced_by` — apagar linha a linha viola a constraint numa cadeia de rotação.
- **FR-18:** A exclusão de conta **não** deve apagar `media_track` — o histórico pertence ao casal. Também não deve apagar nenhuma linha de `couples`: `media_track` e `notification` mantêm FK `NOT NULL` para ela.
- **FR-19:** Após a exclusão, o e-mail deve ficar disponível para um novo cadastro, e a conta nova não deve herdar casal, histórico nem avaliações.
- **FR-20:** Os endpoints `DELETE /api/couple/me`, `PATCH /api/user/me`, `PUT /api/auth/password` e `DELETE /api/user/me` devem ter rate limit **por usuário** (5/h, 10/h, 5/h e 3/h respectivamente), configurável por `app.rate-limit.*`, respondendo `429` com `Retry-After`.
- **FR-21:** Todos os quatro endpoints devem registrar evento no `SecurityAuditLogger`, sem gravar senha, token ou e-mail completo.
- **FR-22:** Deve existir a rota protegida `/conta`, alcançável pelo `Header`, com blocos de Perfil, Senha, Casal e Excluir conta.
- **FR-23:** Toda ação destrutiva na UI (dissolver, excluir) deve exigir confirmação explícita e declarar o que será perdido e o que permanece.
- **FR-24:** Todo erro retornado pela API deve virar mensagem visível na tela — nenhum `catch` silencioso.
- **FR-25:** Após dissolver o casal ou excluir a conta, o cliente deve limpar o `useAuthStore` e desconectar o WebSocket da `useMatchStore`.
- **FR-26:** O ex-parceiro que perde o vínculo sem ter feito a ação deve ver, uma única vez, um aviso estático em `/join` explicando o que aconteceu.

## Non-Goals (Fora de Escopo)

- **Notificar o parceiro em tempo real** sobre a dissolução (STOMP), ou exigir confirmação dos dois lados. O aviso estático da US-013 é o limite.
- Qualquer UI de **"histórico de relacionamentos anteriores"** — dado preservado ≠ dado acessível (D13).
- **Exportação/portabilidade** dos dados antes de dissolver ou excluir.
- **Período de carência / desfazer exclusão** em N dias; anonimização em vez de exclusão.
- **Bloquear o reuso do e-mail** de uma conta excluída (E9.13).
- **Verificação do e-mail novo** por link de confirmação — depende do Épico 10.
- **Notificar por e-mail** que a senha mudou — é a T10.4, Épico 10.
- **Recuperação de senha sem estar autenticado** ("esqueci minha senha") — é o Épico 10 inteiro.
- **Reabrir a D4**: o `409` do registro (e o da troca de e-mail) permanece.
- **Manter viva a sessão que trocou a senha** (E9.8).
- **"Encerrar sessão em todos os dispositivos"** como tela própria — o banco já suporta, mas a UI é feature de produto.
- **Upload de foto de perfil / avatar**, apelido diferente do nome da conta, preferências (tema, idioma, notificações).
- **Testes automatizados de frontend** — é o Épico 11.
- **Qualquer alteração de schema além da `V7`.** A US-003 é refatoração de mapeamento sobre a coluna que já existe.
- Qualquer **redesenho** de tela existente. A `/conta` é nova; o resto não muda.

## Considerações de Design

- A `/conta` é a primeira tela de configurações do app e não tem correspondente no
  protótipo de `docs/design/claude-design-project/`. O visual deve ser **derivado** do
  que já existe, não inventado: fundo `#09090a`, cartões `#161513` com borda
  `rgba(255,255,255,.07)` e raio 18px, headings em `Bricolage Grotesque` com
  `letter-spacing:-.02em`, corpo em `DM Sans`, acento âmbar `#ffcb2b`, secundário
  `#a6a39a`, verde `#3ddc97` para confirmação.
- A zona destrutiva precisa de um tratamento visual próprio (vermelho, borda de
  alerta) — o design system atual não tem esse token definido, então ele nasce aqui e
  deve ser aplicado de forma consistente nos dois blocos destrutivos.
- Componentes a reusar: `CoupleAvatars` (bloco Casal), os diálogos de confirmação já
  existentes (`DeleteTrackDialog` é o precedente mais próximo de confirmação
  destrutiva), e os campos de formulário das telas de auth.
- A skill `frontend-design` é obrigatória nas US-008 a US-013 (UI nova).

## Considerações Técnicas

- **O ponto de risco do épico são as US-002/US-003/US-004.** `findByUser1IdOrUser2Id`
  é a porta pela qual o app inteiro descobre o casal do usuário. Um caminho não
  coberto significa ex-membro de casal dissolvido ainda enxergando dados do casal
  antigo. Por isso a ordem é rígida: primeiro a porta, depois o desacoplamento das
  entidades, e só então a mudança de semântica.
- **Há dois caminhos de resolução de casal, não um.** Além do
  `findByUser1IdOrUser2Id` (casal *do usuário*), existe o `findByInviteCode` (casal
  *do convite*), que é como um usuário **entra** num casal. O segundo é fácil de
  esquecer porque não parte do `userId` — e é exatamente por ele que alguém entraria
  num casal dissolvido (E9.17). Os dois somem na US-002.
- **Casal dissolvido quebra a cardinalidade que o código assume hoje.** Antes deste
  épico, um usuário tem no máximo uma linha em `couples`, e por isso todas as
  consultas devolvem `Optional`. Depois da US-004 ele pode ter várias — uma ativa e N
  dissolvidas. Qualquer consulta que continue devolvendo `Optional` sem filtrar
  `dissolved_at` vira `IncorrectResultSizeDataAccessException` (`500`) no primeiro
  usuário que dissolver e formar um casal novo. Daí a E9.20 exigir a **remoção** dos
  métodos antigos, não só o desuso: erro de compilação em vez de erro de produção.
- **A US-003 é a maior da entrega e não muda comportamento nenhum.** É pura
  refatoração de mapeamento (`@ManyToOne Couple` → `UUID coupleId`) sobre a mesma
  coluna `couple_id`, com a FK preservada no banco. O sinal de que deu certo é
  duplo: o teste de migrations com `ddl-auto=validate` verde, e o JSON de resposta
  byte a byte idêntico. Vale subir em deploy próprio, para que um eventual rollback
  seja um único revert.
- **Cuidado com a regressão de N+1 na US-003.** A T2.2 estabeleceu a resolução de
  nomes com **uma** query em `users` por request, e o `@EntityGraph` da T2.3 evitava
  o N+1 no `couple`. Perder a associação não pode virar uma consulta ao
  `CoupleFacade` por track — o `memberIds` é resolvido uma vez por request, e o
  teste com contador de queries é obrigatório. O ponto mais escorregadio é o
  `RatingRequestService`: hoje ele tem a entidade `Couple` em mãos (via
  `track.getCouple()`) e acha o parceiro de graça; com `UUID`, cada notificação
  passa a poder disparar uma consulta se a resolução não for içada para fora do
  laço.
- **Deploy com migration.** A `V7` é a primeira migration nova desde a `V6`. O deploy
  que a carregar deve usar a connection string direta (5432) ou o pooler do Supabase
  em modo **Session**, nunca o modo Transaction (6543) — risco de advisory lock
  documentado em `docs/FLYWAY.md`. Vale lembrar a D15: **não há backup gerenciado**;
  o snapshot manual antes do deploy com migration é a única rede.
- **Rate limit por usuário não passa pelo `RateLimitFilter`.** O filtro casa por
  método+path e chaveia por IP; o limite por usuário é consumido dentro do serviço,
  como `CoupleService.enforceJoinRateLimit`/`enforceRegenerateInviteCodeRateLimit` já
  fazem. Seguir esse mecanismo, não criar um segundo. Uma diferença deliberada: esses
  dois métodos compartilham o **mesmo** `Limit` (`coupleJoinByUser`) com chaves
  diferentes; os quatro endpoints deste épico ganham `Limit` próprio (E9.3), porque
  3/h de exclusão e 20/h de join não são o mesmo apetite de risco.
- **A exclusão de conta precisa de teste contra Postgres real.** H2 em modo PostgreSQL
  não é prova suficiente de integridade referencial. Usar o arranjo do
  `FlywayMigrationTest` (`@AutoConfigureTestDatabase(replace = NONE)` + `DB_URL` do
  CI), que é o único lugar do projeto onde um Postgres de verdade participa da suíte.
  Atenção: sem `DB_URL` no ambiente, esse arranjo cai para H2 silenciosamente — o
  teste passa localmente e só o CI exerce o caminho que importa.
- **A auto-FK de `refresh_token` decide a *forma* do delete.** `fk_refresh_token_replaced_by`
  aponta para a própria tabela; a rotação de sessão encadeia os tokens de um usuário
  por `replaced_by_id`. Um `deleteAll(entidades)` do JPA emite um `DELETE` por linha e
  estoura a FK assim que a ordem escolhida pelo Hibernate apagar um token ainda
  referenciado. Um `@Modifying @Query` único derruba a cadeia inteira num statement,
  com a checagem de integridade no fim (FR-17b).
- **`invite_code` precisa ser limpo na dissolução** (E9.17). O fato de a coluna ser
  nullable desde a V6 e de o Postgres considerar cada `NULL` distinto no unique
  constraint resolve a **colisão** — casais dissolvidos não brigam entre si nem com
  casais novos —, mas não resolve a **validade**: um casal criado e nunca pareado
  ainda tem código preenchido, e `findByInviteCode` não filtra nada. Limpar no
  `dissolve()` + filtrar na consulta cobre os dois lados.
- **Sincronização de tipos é manual** entre `api/` e `client/src/types/` — não há
  codegen (`client/CLAUDE.md`).
- **Gate do JaCoCo:** 90% de linha. Toda story de backend precisa entregar teste
  junto, não depois.

## Métricas de Sucesso

- Zero necessidade de `UPDATE`/`DELETE` manual no banco de produção para corrigir
  pareamento errado, dado de perfil, senha ou pedido de exclusão — hoje é 100% dos
  casos.
- Um usuário consegue dissolver o casal e formar um novo em menos de 1 minuto, sem
  ajuda.
- Um pedido de exclusão de conta (LGPD art. 18) é atendido pelo próprio titular, sem
  intervenção do mantenedor.
- Nenhum dado de casal dissolvido acessível pela API — verificado por teste de
  integração, não por inspeção. Inclui a lista/contador de notificações, o tópico
  STOMP do casal e o código de convite.
- `grep -rn "findByUser1IdOrUser2Id\|findByInviteCode" api/src` retorna vazio.
- `grep -rn "com.app.couple.Couple\b\|com.app.couple.CoupleRepository\|com.app.couple.CoupleService" api/src/main/java/com/app/tracking api/src/main/java/com/app/match` retorna vazio.
- `GET /api/tracking?status=WATCHED` com 50 tracks continua executando **no máximo 1**
  query em `users` depois da US-003 — sem regressão do ganho da T2.2.
- `./mvnw test` verde com o gate de 90%, e `bun run typecheck`/`lint`/`build` verdes.

## Questões em Aberto

Nenhuma. Todas as questões levantadas nas revisões deste PRD foram resolvidas em
2026-08-06 e estão registradas como decisões (as quatro últimas vieram da revisão do
PRD contra o código, também em 2026-08-06):

| Questão | Decisão |
|---|---|
| `dissolve()` em casal já dissolvido | **E9.7** — lança exceção |
| O que a porta `CoupleFacade` trafega | **E9.4** — só `UUID` |
| Como a associação JPA sobrevive a isso | **E9.5** — `@ManyToOne Couple` → `@Column UUID coupleId`, sem migration (US-003) |
| Ex-parceiro descobre o vínculo desfeito | **E9.10** — aviso estático em `/join` (US-013) |
| Reuso do e-mail após exclusão | **E9.13** — liberado, recadastro do zero |
| Quem apaga o dado das outras features | **E9.14** — uma porta por feature, orquestradas por `UserDeletionService` |
| DTO de resposta do perfil | **E9.15** — `UserProfileResponse` próprio, sem reusar `UserSummary` |
| Exceção de `requireActiveCoupleId` | **E9.16** — `com.app.common.ResourceNotFoundException` |
| Código de convite de casal dissolvido | **E9.17** — `dissolve()` limpa o código e a consulta de convite filtra dissolvidos |
| Notificações do casal dissolvido | **E9.18** — filtrar por casal ativo, não apagar (US-004) |
| `NotificationService` recebe a entidade `Couple` | **E9.19** — passa a receber `UUID` na US-003; `com.app.websocket` fica de fora |
| `findByUser1IdOrUser2Id` sobrevive ao épico? | **E9.20** — removido, não apenas evitado (senão vira `500` por `Optional` com 2 linhas) |

**Ponto de atenção para a implementação, não questão em aberto:** este épico cria
três portas (`CoupleFacade`, `MatchFacade`, `NotificationFacade`) além do
`TrackingFacade` que já existia. É um salto de superfície arquitetural relevante para
um único épico. Se durante a execução alguma dessas portas se mostrar um único método
que nunca ganha um segundo, vale registrar isso no PR — não para reverter, mas para
que a próxima pessoa saiba que a porta existe por causa da regra de dependência, não
porque a feature precisava de uma fachada rica.
