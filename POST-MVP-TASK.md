# Post-MVP — Backlog Técnico

Backlog derivado da auditoria de arquitetura, clean code, performance e segurança
realizada em 2026-08-02 sobre `api/` e `client/`.

Cada task abaixo é **autocontida e não-ambígua**: pode ser pega isoladamente, tem
critérios de aceite verificáveis e um escopo negativo explícito ("Fora do escopo")
para evitar sobreposição com outras tasks. As dependências entre tasks estão
declaradas — respeitá-las é obrigatório, o resto da ordem é livre.

## Decisões confirmadas

Todas as questões em aberto foram resolvidas em 2026-08-02. Estas decisões são
vinculantes para as tasks abaixo — não reabrir sem registrar o motivo aqui.

| # | Questão | Decisão | Consequência |
|---|---------|---------|--------------|
| D1 | **Base de usuários atual** | ~8 pessoas (4 casais), todas conhecidas. **Downtime é tolerável.** | Simplifica drasticamente o Épico 4: cutover direto, sem janela de transição nem fallback de header. Eram 6 tasks, viraram 5, e as mais complexas sumiram. |
| D2 | Modelo de autenticação | **Cookie HttpOnly + refresh token com rotação** (não BFF). Front e API são same-origin (`sessaoadois.luisgosampaio.com`, nginx faz proxy de `/api` e `/ws`), o que torna `SameSite=Strict` plenamente eficaz e dispensa o hop extra de um BFF. | Épico 4 conforme escrito. |
| D3 | `spring.jpa.open-in-view` | **Desligar** (T2.3), em deploy isolado para rollback trivial. | Um N+1 futuro passa a falhar alto em vez de degradar em silêncio. Risco de algum endpoint não rastreado quebrar é aceito, dado D1. |
| D4 | Enumeração de usuários no registro | **Manter o 409 Conflict** (opção B), mitigado pelo rate limit da T5.1. Não há infraestrutura de e-mail transacional no roadmap. | T5.2 fica só com a correção do vetor de *timing*. |
| D5 | Rotação do `JWT_SECRET` | **Rotacionar já, junto com a T1.2.** Logout forçado de todos é aceitável (D1). | Invalida de imediato os tokens já vazados para o `access.log` do nginx. |
| D6 | TTL do invite code | **7 dias**, com botão de regenerar. | T5.4 usa 7 dias, não 24h. |
| D7 | TTL de sessão | **Access token: 15 min. Refresh: 30 dias.** | T4.1 conforme escrito. |
| D8 | Futuro do Dashboard | **Expandir o `StatsService`** e depreciar de vez o `GET /api/tracking` sem `status`. Não construir variante paginada dele. | T3.3 muda de abordagem. |
| D9 | Primeiro deploy com Flyway | **Já ocorreu em produção.** | T8.5 está desbloqueada. |
| D10 | Usuário de deploy não-root | **Manter `root`.** O modelo de ameaça (projeto pessoal, um administrador, 8 usuários conhecidos) não justifica o custo de setup nem o risco de quebrar o pipeline. | T8.3 fica só com a correção dos segredos em `/tmp`. Revisitar se o app ganhar usuários desconhecidos. |
| D11 | Skill `frontend-design` em refatoração | **Não usar** quando não há mudança visual. A regra do `CLAUDE.md` da raiz vale para UI nova ou alterada. | T7.1, T7.2 e T7.3 são refatorações puras e estão dispensadas. Registrado no `CLAUDE.md` da raiz. |
| D12 | Cache do TMDB | A decisão #5 de `docs/BACKLOG.md` ("Cache TMDB: não por enquanto") continua válida. A T3.2 resolve a latência por **desnormalização**, não por cache. | Se depois de medir o cache ainda fizer sentido, vira task nova. |

**Ordem de execução:** Épicos 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8, com a **T8.1 (CI)
antecipada** para junto do Épico 1. As dependências declaradas por task são as únicas
restrições rígidas; o resto da ordem é negociável.

## Legenda de criticidade

- **Crítico** — risco ativo em produção (exposição de credencial, degradação sob carga real).
- **Alto** — risco relevante ou dívida que trava evolução.
- **Médio** — qualidade, manutenibilidade, defesa em profundidade.
- **Baixo** — polimento.

## Índice

| Épico | Tema | Tasks | Criticidade máx. |
|-------|------|-------|------------------|
| [1](#épico-1--contenção-de-segurança) | Contenção de Segurança | T1.1 – T1.4 | Crítico |
| [2](#épico-2--fundação-de-performance-de-banco) | Fundação de Performance de Banco | T2.1 – T2.4 | Crítico |
| [3](#épico-3--resiliência-e-latência-do-tmdb) | Resiliência e Latência do TMDB | T3.1 – T3.3 | Alto |
| [4](#épico-4--migração-de-autenticação) | Migração de Autenticação | T4.1 – T4.5 | Alto |
| [5](#épico-5--endurecimento-de-contas-e-auth) | Endurecimento de Contas e Auth | T5.1 – T5.6 | Alto |
| [6](#épico-6--arquitetura-e-tratamento-de-erros) | Arquitetura e Tratamento de Erros | T6.1 – T6.5 | Médio |
| [7](#épico-7--qualidade-do-frontend) | Qualidade do Frontend | T7.1 – T7.3 | Alto |
| [8](#épico-8--cicd-e-infraestrutura) | CI/CD e Infraestrutura | T8.1 – T8.5 | Médio |

---

# Épico 1 — Contenção de Segurança

**Objetivo:** eliminar os riscos de exposição de credencial e de acesso indevido sem
alterar nenhum contrato entre `api/` e `client/`. Tudo aqui é infra/config e é
deployável independentemente.

**Nenhuma task deste épico depende de outra.** Podem ser feitas em paralelo.

---

## T1.1 — Fail-fast do `JWT_SECRET`

**Criticidade:** Crítico
**Arquivos:** `api/src/main/java/com/app/security/JwtService.java`, `api/src/main/resources/application.properties`, `api/src/test/resources/application.properties`

### Problema
`application.properties:23` define:

```
app.jwt.secret=${JWT_SECRET:dev-only-secret-please-override-in-prod-32bytes+}
```

Se a variável de ambiente `JWT_SECRET` não for injetada em produção (env perdida no
compose, typo, container reiniciado sem `.env`), a aplicação **sobe normalmente**
assinando tokens com uma chave versionada no repositório. Qualquer pessoa com acesso
ao repo consegue forjar um token válido para qualquer `userId`.

### O que fazer
Aplicar o mesmo padrão de fail-fast que já existe para a chave do TMDB em
`api/src/main/java/com/app/media/TmdbConfig.java:31-34`.

No construtor de `JwtService` (linhas 24-29), lançar `IllegalStateException` quando o
segredo:
1. estiver em branco/nulo;
2. for exatamente igual ao valor default de desenvolvimento;
3. tiver menos de 32 bytes em UTF-8.

Remover o valor default de `api/src/main/resources/application.properties`
(deixar `app.jwt.secret=${JWT_SECRET:}`), mantendo um segredo fixo apenas em
`api/src/test/resources/application.properties` — conforme documentado em
`api/CLAUDE.md`, esse arquivo **substitui** o principal nos testes de contexto
completo, então ele precisa declarar a propriedade explicitamente.

### Critérios de aceite
- [ ] Subir a aplicação sem `JWT_SECRET` definido falha no startup com mensagem em pt-BR nomeando a variável de ambiente.
- [ ] Subir com `JWT_SECRET` de 31 bytes ou menos falha no startup.
- [ ] Subir com `JWT_SECRET` igual ao antigo default falha no startup.
- [ ] Subir com um segredo válido de ≥32 bytes funciona normalmente.
- [ ] `./mvnw test` passa (o `application.properties` de teste fornece um segredo válido).
- [ ] Existe teste unitário cobrindo os 4 cenários acima.

### Fora do escopo
Rotação de chave, algoritmo assimétrico (RS256), claims `iss`/`aud` — ficam na T4.1.

---

## T1.2 — Parar de gravar JWT em texto claro nos logs

**Criticidade:** Crítico
**Arquivos:** `deploy/nginx/sessaoadois.luisgosampaio.com.conf`, `docs/DEPLOY.md`

### Problema
`client/src/stores/useMatchStore.ts:62` monta a URL do handshake como
`${VITE_API_URL}/ws?token=<JWT>`. O bloco `location /ws/` do nginx não desabilita
nem filtra o `access_log`, então **todo handshake WebSocket grava um JWT válido em
disco na VPS** (`/var/log/nginx/access.log`), com rotação e backup incluídos. O token
também fica no histórico do navegador e em qualquer proxy intermediário.

Isso é independente da questão do `localStorage` e é o achado mais grave da auditoria.

### O que fazer
No `location /ws/` do conf do nginx, desabilitar o access log **ou** mascarar o
parâmetro `token` via `map` sobre `$request_uri`. Preferir `access_log off;` pela
simplicidade — o handshake não tem valor de observabilidade que justifique o risco.

Documentar em `docs/DEPLOY.md` que:
1. o conf precisa ser reinstalado na VPS (`/etc/nginx/sites-available/`) e o nginx recarregado;
2. os logs **existentes** contêm tokens e devem ser purgados manualmente uma vez;
3. como o `JWT_EXPIRATION_DAYS` atual é 7, tokens em logs de até 7 dias atrás ainda são válidos.

**Rotacionar o `JWT_SECRET` nesta task** (decisão D5). Isso invalida de imediato tudo
que já vazou para o log e desloga todos os usuários — aceito, dado D1. Gerar com
`openssl rand -base64 48`, conforme `docs/DEPLOY.md` já instrui.

### Critérios de aceite
- [ ] `location /ws/` não emite linha de access log contendo o valor do parâmetro `token`.
- [ ] `nginx -t` passa no conf alterado.
- [ ] `location /api/` e `location /` mantêm o access log intacto (só o `/ws/` muda).
- [ ] Os logs antigos da VPS foram purgados.
- [ ] O `JWT_SECRET` foi rotacionado e o `.env` da VPS atualizado.
- [ ] `docs/DEPLOY.md` tem um passo explícito de purga de logs e de rotação do segredo.

### Fora do escopo
Remover o `?token=` da URL — isso depende do cookie HttpOnly e é feito na **T4.5**.
Esta task é a mitigação imediata enquanto a query string ainda existe.

---

## T1.3 — Autorização por destino no STOMP

**Criticidade:** Alto (OWASP A01 — Broken Access Control)
**Arquivos:** `api/src/main/java/com/app/websocket/WebSocketConfig.java`, novo arquivo em `com.app.websocket`

### Problema
`SecurityConfig.java:44` libera `/ws/**` — correto, porque a autenticação acontece no
handshake via `JwtHandshakeInterceptor`. Mas `WebSocketConfig.java:41` apenas registra
`registry.enableSimpleBroker("/topic")`, **sem nenhuma autorização por mensagem**.

Consequência: qualquer usuário autenticado pode enviar
`SUBSCRIBE /topic/couple/{coupleId}/notifications` para o `coupleId` de outro casal e
receber títulos assistidos, nomes de parceiro e eventos de match. O `coupleId` é um
UUID (difícil de adivinhar), mas circula no cliente de ambos os parceiros — não é
segredo.

### O que fazer
Habilitar `@EnableWebSocketSecurity` e registrar um `AuthorizationManager` para
mensagens que, em `SUBSCRIBE` para `/topic/couple/{coupleId}/**`, valide que o
`coupleId` do destino pertence ao usuário da sessão.

A infraestrutura necessária já existe: `JwtHandshakeInterceptor.java:50` já guarda o
`userId` nos `attributes` da sessão sob a constante `USER_ID_ATTRIBUTE`. Basta
recuperá-lo e comparar com `CoupleService.getCurrentCouple(userId)`.

Bloquear também `SEND` para `/topic/**` (o broker simples permite publicação direta de
cliente para tópico por default, o que permitiria a um usuário forjar um evento de
match no tópico de outro casal).

### Critérios de aceite
- [ ] `SUBSCRIBE` em `/topic/couple/{id}/match` e `/topic/couple/{id}/notifications` com `id` do próprio casal é aceito.
- [ ] `SUBSCRIBE` nos mesmos destinos com `id` de outro casal é rejeitado.
- [ ] `SUBSCRIBE` com `id` malformado (não-UUID) é rejeitado sem lançar 500.
- [ ] `SEND` direto de cliente para qualquer destino `/topic/**` é rejeitado.
- [ ] Os pushes do servidor (`SimpMessagingTemplate.convertAndSend` em `MatchService:117` e `NotificationService:147`) continuam funcionando — a autorização vale para mensagens de entrada, não para as do broker.
- [ ] Existe teste de integração cobrindo aceite e rejeição.

### Fora do escopo
Trocar o transporte do token no handshake (T4.5). O `JwtHandshakeInterceptor` continua
lendo da query string nesta task.

---

## T1.4 — Headers de segurança HTTP

**Criticidade:** Alto (OWASP A05 — Security Misconfiguration)
**Arquivos:** `deploy/nginx/sessaoadois.luisgosampaio.com.conf`, `docs/DEPLOY.md`

### Problema
O conf do nginx não define `Content-Security-Policy`, `Strict-Transport-Security`,
`X-Frame-Options`/`frame-ancestors`, `X-Content-Type-Options` nem `Referrer-Policy`.
O Certbot adiciona o redirect 80→443 mas **não** adiciona HSTS.

O `client/src` não tem nenhum `dangerouslySetInnerHTML`, `eval` ou `innerHTML`
(verificado na auditoria), então a superfície de XSS própria é pequena — o risco real
é dependência de terceiros comprometida. A CSP é justamente a defesa que torna a
migração para cookie HttpOnly (Épico 4) efetiva: sem ela, um atacante com execução de
script cavalga a sessão dentro da página mesmo sem conseguir ler o token.

### O que fazer
Adicionar ao bloco `server` da porta 443 (o que o Certbot gera):

```nginx
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
add_header X-Content-Type-Options "nosniff" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Content-Security-Policy "default-src 'self'; img-src 'self' https://image.tmdb.org data:; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'" always;
```

`img-src` precisa liberar `image.tmdb.org` — é o host dos pôsteres
(`MediaDetailsService.java:14,16`). `style-src` precisa de `'unsafe-inline'` e do
Google Fonts enquanto o app carregar fontes por CDN.

Documentar em `docs/DEPLOY.md` que estes headers vivem no bloco gerado pelo Certbot e
precisam ser reaplicados se o certificado for reemitido do zero.

### Critérios de aceite
- [ ] `curl -I https://sessaoadois.luisgosampaio.com` retorna os 4 headers.
- [ ] O app carrega sem violação de CSP no console: pôsteres do TMDB aparecem, fontes carregam, chamadas a `/api` e o WebSocket funcionam.
- [ ] `nginx -t` passa.
- [ ] `docs/DEPLOY.md` explica a interação com o Certbot.

### Fora do escopo
Auto-hospedar as fontes para eliminar `unsafe-inline`/CDN da CSP — melhoria futura,
não bloqueia esta task.

---

# Épico 2 — Fundação de Performance de Banco

**Objetivo:** eliminar os N+1 e a ausência de índices antes que o volume de dados
torne o problema visível. Nenhuma mudança de contrato de API.

**Dependências:** T2.3 depende de T2.2 (o mapper extraído é onde o `EntityGraph` passa a ser consumido).

---

## T2.1 — Migration V3: índices

**Criticidade:** Crítico
**Arquivos:** novo `api/src/main/resources/db/migration/V3__add_indexes.sql`

### Problema
`V1__baseline.sql` cria PKs e constraints unique, mas **nenhum índice** em coluna de
FK ou de filtro. Postgres não indexa FK automaticamente. Impacto direto:

- `CoupleRepository.findByUser1IdOrUser2Id` roda em **toda request autenticada** (`MediaTrackController.java:107`, `MatchController.java:35,44,53`) → seq scan em `couples`.
- Todas as queries de `MediaTrackRepository` filtram por `couple.id`.
- `MatchLikeRepository.findPendingForUser:18-24` faz 3 subconsultas `NOT IN` sem índice nenhum.

É a correção de maior retorno por linha escrita de todo este backlog.

### O que fazer
Criar `V3__add_indexes.sql`:

```sql
CREATE INDEX IF NOT EXISTS idx_couples_user1 ON couples (user1_id);
CREATE INDEX IF NOT EXISTS idx_couples_user2 ON couples (user2_id);
CREATE INDEX IF NOT EXISTS idx_media_track_couple_status ON media_track (couple_id, status);
CREATE INDEX IF NOT EXISTS idx_media_track_couple_tmdb   ON media_track (couple_id, tmdb_id);
CREATE INDEX IF NOT EXISTS idx_user_review_track  ON user_review (media_track_id);
CREATE INDEX IF NOT EXISTS idx_user_review_user   ON user_review (user_id);
CREATE INDEX IF NOT EXISTS idx_match_like_couple_tmdb   ON match_like (couple_id, tmdb_id);
CREATE INDEX IF NOT EXISTS idx_match_reject_couple_user ON match_reject (couple_id, user_id);
CREATE INDEX IF NOT EXISTS idx_media_track_genre_track  ON media_track_genre (media_track_id);
```

Respeitar as convenções de `api/CLAUDE.md`: SQL compatível com o modo PostgreSQL do
H2 usado nos testes, sem `DROP`/`TRUNCATE`, `IF NOT EXISTS` em tudo.

**Atenção ao deploy:** `docs/FLYWAY.md` documenta o risco do pooler do Supabase em
modo Transaction (porta 6543) com advisory locks do Flyway. Como esta é a primeira
migration nova depois do baseline, usar conexão direta (5432) ou pooler em modo
Session no deploy que a carregar.

### Critérios de aceite
- [ ] `./mvnw test` passa (migrations aplicam sob H2 em modo PostgreSQL).
- [ ] A aplicação sobe contra Postgres limpo com V1+V2+V3 aplicadas em sequência.
- [ ] `EXPLAIN` de `SELECT * FROM couples WHERE user1_id = ? OR user2_id = ?` usa índice em vez de seq scan.
- [ ] Nenhuma tabela existente é recriada ou alterada.
- [ ] `docs/FLYWAY.md` menciona a ressalva do pooler para este deploy específico.

### Fora do escopo
Reescrever `findPendingForUser` de `NOT IN` para `NOT EXISTS` — fica na T3.3.

---

## T2.2 — Eliminar N+1 na resolução de nomes e extrair o mapper

**Criticidade:** Crítico
**Arquivos:** `api/src/main/java/com/app/tracking/MediaTrackService.java`, novo `MediaTrackMapper.java`, `api/src/main/java/com/app/tracking/UserReviewService.java`

### Problema
`MediaTrackService.java:199`:

```java
private ReviewDto toReviewDto(UUID memberId, MediaTrack track) {
    ...
    String userName = userRepository.findById(memberId).map(User::getName).orElse(null);
```

`toResponse` (linhas 178-180) chama isso 1× por membro do casal, e `listByStatus`
(linha 81) chama `toResponse` 1× por track. Listagem de 50 títulos =
**100 SELECTs em `users`** para resolver 2 nomes distintos que não mudam durante a
request.

Agravante estrutural: `MediaTrackService` acumula CRUD + mapeamento DTO +
enriquecimento TMDB + orquestração de notificação (5 dependências, linhas 32-36), e o
`toResponse` package-private é chamado de fora por `UserReviewService.java:57`,
criando acoplamento bidirecional.

### O que fazer
1. Extrair um `MediaTrackMapper` (componente Spring próprio, em `com.app.tracking`) responsável apenas por `MediaTrack` → `MediaTrackResponse`.
2. O mapper recebe o `Map<UUID, String>` de nomes **já resolvido** como parâmetro — não injeta `UserRepository`.
3. Nos pontos de entrada (`listByStatus`, `listByStatusPaged`, `addTrack`, `markAsWatched`, `startWatching`, `deleteTrack`, `UserReviewService.upsertReview`), resolver os nomes uma única vez por request:

```java
Map<UUID,String> names = userRepository.findAllById(memberIds).stream()
    .collect(toMap(User::getId, User::getName));
```

O padrão correto já existe no próprio código —
`NotificationService.resolveActorNames:122-130` faz exatamente isso com `findAllById`.
É replicar.

4. `UserReviewService` passa a depender de `MediaTrackMapper`, não de `MediaTrackService`.

### Critérios de aceite
- [ ] `GET /api/tracking?status=WATCHED` com 50 tracks executa **no máximo 1** query em `users`, independentemente da quantidade de tracks (verificar com `spring.jpa.show-sql=true` ou contador de queries no teste).
- [ ] `MediaTrackService` não injeta mais `UserRepository` para fins de mapeamento.
- [ ] `UserReviewService` não depende mais de `MediaTrackService`.
- [ ] O JSON de resposta de todos os endpoints de `/api/tracking` é byte a byte idêntico ao anterior.
- [ ] Testes existentes de `MediaTrackServiceTest`, `MediaTrackControllerTest` e `UserReviewServiceTest` passam (ajustados para o novo mapper onde necessário).
- [ ] Existe `MediaTrackMapperTest`.

### Fora do escopo
As coleções LAZY (`reviews`, `couple`, `genreIds`) — T2.3.

---

## T2.3 — Eliminar N+1 nas coleções LAZY e desligar OSIV

**Criticidade:** Crítico
**Depende de:** T2.2
**Arquivos:** `api/src/main/java/com/app/tracking/MediaTrackRepository.java`, `MediaTrackService.java`, `api/src/main/resources/application.properties`

### Problema
`MediaTrack.reviews` é `@OneToMany` LAZY (`MediaTrack.java:67`), `MediaTrack.couple` é
`@ManyToOne(LAZY)` (linha 37) e `genreIds` é `@ElementCollection` LAZY (linha 58). O
mapeamento para DTO toca as três por track. No endpoint **não paginado**
`GET /api/tracking` (consumido por `MatchScreen.tsx:767` e pelo Dashboard) isso são ~3
queries extras por título, crescendo sem limite.

Problema correlato: `MediaTrackService.startWatching` (linhas 127-139) é o **único**
mutador sem `@Transactional` — comparar com `addTrack:48`, `markAsWatched:98`,
`deleteTrack:141`. Ele lê `track.getCouple()` e `track.getReviews()` (ambos LAZY) fora
de transação e só funciona porque `spring.jpa.open-in-view` está no default `true`,
que é um anti-pattern reconhecido e a própria causa de os N+1 passarem despercebidos.

### O que fazer
1. Adicionar `@EntityGraph(attributePaths = {"reviews", "reviews.user", "couple"})` aos finders de `MediaTrackRepository` (`findByCoupleId`, `findByCoupleIdAndStatus`, `findByCoupleIdAndStatusOrderByCreatedAtDesc`).
2. **Atenção na variante paginada** (`MediaTrackRepository.java:18`): `JOIN FETCH` de coleção combinado com `Pageable` faz o Hibernate paginar **em memória** (aviso `HHH000104`). Usar `@EntityGraph` com `countQuery` separado, ou o padrão de duas queries (buscar os ids paginados, depois fazer fetch por `IN`).
3. Anotar `startWatching` com `@Transactional`.
4. **Só depois** de 1-3, definir `spring.jpa.open-in-view=false` em `application.properties`.

A ordem importa: desligar OSIV antes transforma os N+1 em `LazyInitializationException`
— que é, aliás, o comportamento desejável a longo prazo (falha explícita em vez de
lentidão silenciosa), mas quebra o app se feito primeiro.

**Deploy isolado** (decisão D3): o passo 4 muda comportamento global, não só de
`tracking`. Se algum endpoint não rastreado acessar dado LAZY fora de transação, ele
quebra. Subir esta task sozinha, sem nada mais junto, para que o rollback seja um
único revert.

### Critérios de aceite
- [ ] `GET /api/tracking` (sem `status`) com 30 tracks executa número **constante** de queries, não proporcional à quantidade de tracks.
- [ ] `GET /api/tracking?status=WATCHED&page=0&size=20` pagina no banco: o log do Hibernate não emite `HHH000104`.
- [ ] `spring.jpa.open-in-view=false` e todos os endpoints de `/api/tracking`, `/api/match` e `/api/notifications` respondem sem `LazyInitializationException`.
- [ ] `startWatching` é `@Transactional`.
- [ ] `./mvnw test` passa.

### Fora do escopo
Paginar o endpoint sem `status` — T3.3.

---

## T2.4 — Delete em massa de notificações expiradas

**Criticidade:** Médio
**Arquivos:** `api/src/main/java/com/app/notification/NotificationRepository.java`, `NotificationCleanupService.java`

### Problema
`NotificationRepository.java:20` usa `deleteByCreatedAtBefore`, um delete derivado do
Spring Data — que carrega **todas** as entidades correspondentes em memória e as
deleta uma a uma. Com 2 notificações por evento e retenção de 30 dias
(`NotificationCleanupService.java:17`) ainda é pequeno, mas cresce linearmente com a
base e roda num job agendado diário sem limite de lote.

### O que fazer
Trocar por um delete em massa:

```java
@Modifying
@Query("DELETE FROM Notification n WHERE n.createdAt < :cutoff")
int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
```

Ajustar o tipo de retorno em `NotificationCleanupService.java:29` (`long` → `int`) e
manter o log existente.

### Critérios de aceite
- [ ] O job emite **uma** instrução `DELETE` independentemente do volume.
- [ ] `NotificationCleanupServiceTest` e `NotificationRepositoryTest` passam.
- [ ] O log continua reportando a quantidade removida.

### Fora do escopo
Mudar a política de retenção de 30 dias ou o horário do cron.

---

# Épico 3 — Resiliência e Latência do TMDB

**Objetivo:** tirar o TMDB do caminho crítico de request e impedir que uma
instabilidade externa derrube o app.

**Dependências:** nenhuma entre as tasks do épico. T3.2 é a maior e pode ser fatiada.

---

## T3.1 — Timeouts no `RestClient` do TMDB

**Criticidade:** Alto
**Arquivos:** `api/src/main/java/com/app/media/TmdbConfig.java`

### Problema
`TmdbConfig.java:36-40` constrói o `RestClient` sem `requestFactory`, logo sem timeout
de conexão nem de leitura. Uma conexão pendurada prende a thread do Tomcat
indefinidamente. Combinado com a T3.2 (hoje são 10 chamadas sequenciais numa única
request), um TMDB lento esgota o pool. O container tem `memory: 512M` e `cpus: 1.0`
(`docker-compose-prod.yml:16-18`) — a margem é estreita.

### O que fazer
```java
var factory = new SimpleClientHttpRequestFactory();
factory.setConnectTimeout(Duration.ofSeconds(3));
factory.setReadTimeout(Duration.ofSeconds(5));
return RestClient.builder().requestFactory(factory)...
```

O caminho de erro já existe: timeouts surgem como `ResourceAccessException`, já
capturada e convertida em `TmdbUnavailableException` → 502 por
`MediaExceptionHandler.java:37`. Nenhum tratamento novo é necessário.

### Critérios de aceite
- [ ] `tmdbRestClient` tem connect timeout de 3s e read timeout de 5s, ambos configuráveis por propriedade com esses defaults.
- [ ] Um TMDB que não responde produz 502 em ≤ 6s, não uma thread pendurada.
- [ ] `TmdbConfigTest` cobre a configuração dos timeouts.
- [ ] `./mvnw test` passa.

### Fora do escopo
Retry, circuit breaker, cache.

---

## T3.2 — Desnormalizar título e pôster para tirar o TMDB do caminho quente

**Criticidade:** Alto
**Arquivos:** `api/src/main/java/com/app/match/MatchLike.java`, `MatchService.java`, `LikeRequest.java`, `api/src/main/java/com/app/tracking/MediaTrack.java`, `MediaTrackResponse.java`, `MediaTrackService.java`, nova migration `V4__denormalize_media_metadata.sql`, `client/src/components/MediaCard.tsx`, `client/src/types/tracking.ts`

### Problema
Dois gargalos com a mesma raiz — o app vai ao TMDB em tempo de leitura para dados que
já conhecia em tempo de escrita:

1. **Backend:** `MatchService.getPending:96-107` itera até 10 likes pendentes e faz `mediaDetailsService.getDetails()` (round-trip HTTP a `api.themoviedb.org`) **em série**, sem cache. A 200ms por chamada são ~2s de thread presa por abertura da aba Sugestões.
2. **Frontend:** `MediaCard.tsx:87-94` faz `GET /api/media/{type}/{id}` **por card montado**. O Hub renderiza 3 seções × `PAGE_SIZE` cards; ~30 cards = 30 requests, cada uma virando uma chamada ao TMDB no backend. E `HubScreen.handleModalSuccess:293-296` recarrega as 3 seções, remontando tudo.

### O que fazer
1. Adicionar colunas `title` e `poster_url` a `match_like` e a `media_track` (migration `V4`, seguindo as convenções de `api/CLAUDE.md`: `IF NOT EXISTS`, sem default no lado do banco para colunas geradas pela app, compatível com H2 em modo PostgreSQL). Ambas nullable, para tolerar linhas históricas.
2. Popular no momento da escrita:
   - `MatchService.like` — os dados vêm do `SearchTab`, que já os tem; estender `LikeRequest` com `title`/`posterUrl` opcionais e, quando ausentes, cair no `mediaDetailsService` como hoje.
   - `MediaTrackService.addTrack` e `MatchService.createMatch` — ambos já chamam `mediaDetailsService.getDetails()` para os `genreIds` (`MediaTrackService.java:150`, `MatchService.java:110`); aproveitar a mesma resposta para persistir título e pôster. **Zero chamadas novas ao TMDB.**
3. `MatchService.getPending` passa a ler dos campos persistidos, sem nenhuma chamada externa. Manter o fallback ao TMDB apenas para linhas antigas com `title` nulo.
4. Expor `title` e `posterUrl` em `MediaTrackResponse`; espelhar em `client/src/types/tracking.ts` (não há codegen — sincronização é manual, conforme `client/CLAUDE.md`).
5. Remover o `useEffect` de fetch de `MediaCard.tsx:87-94`. O que o card ainda precisar do TMDB e não estiver persistido deve ser buscado **sob demanda** (ao abrir o `MediaDetailModal`), nunca na montagem do card.

### Critérios de aceite
- [ ] `GET /api/match/pending` não faz nenhuma chamada ao TMDB para likes criados após esta task.
- [ ] `GET /api/match/pending` responde em < 200ms com 10 itens (medido sem o TMDB no caminho).
- [ ] Renderizar o Hub com 30 cards dispara **3** requests HTTP (uma por seção), não 33.
- [ ] Likes/tracks criados **antes** da migration continuam renderizando título e pôster corretamente (via fallback).
- [ ] Nenhuma chamada nova ao TMDB é introduzida no caminho de escrita — a contagem de chamadas em `addTrack` e `createMatch` permanece a mesma.
- [ ] `./mvnw test` e `bun run typecheck` passam.

### Fora do escopo
Cache do TMDB (Caffeine/`@Cacheable`). Esta task **substitui** a necessidade dele no
caminho quente. Se depois de medir o cache ainda fizer sentido, vira task própria e
exige revisitar formalmente a decisão #5 de `docs/BACKLOG.md`.

---

## T3.3 — Paginar/enxugar `GET /api/tracking` e otimizar `findPendingForUser`

**Criticidade:** Médio
**Arquivos:** `api/src/main/java/com/app/tracking/MediaTrackController.java`, `MediaTrackService.java`, `api/src/main/java/com/app/match/MatchLikeRepository.java`, `client/src/screens/MatchScreen.tsx`, `client/src/screens/DashboardScreen.tsx`

### Problema
1. `MediaTrackController.java:54-58` devolve a biblioteca inteira do casal sem paginação. `MatchScreen.tsx:767` consome esse endpoint **só** para montar um `Set` de chaves `mediaType-tmdbId` (para saber quais títulos já estão na lista). Payload cresce sem limite para um dado que são dois campos por linha.
2. `MatchLikeRepository.findPendingForUser:18-24` usa 3 subconsultas `NOT IN` correlacionadas. Além do custo, `NOT IN` tem semântica traiçoeira com `NULL`.

### O que fazer
1. Criar `GET /api/tracking/keys` devolvendo `[{mediaType, tmdbId}]` — resposta enxuta, sem reviews nem metadados. `MatchScreen` passa a consumi-lo.
2. **Expandir o `StatsService`** com o que faltar para o `DashboardScreen` (decisão D8) — não construir variante paginada do endpoint completo. `MediaTrackController.stats` (`GET /api/tracking/stats`) já cobre a maior parte, e a orientação de `api/CLAUDE.md` é que uma estatística nova começa por uma agregação nova no repositório, não por consulta de entidades.
3. Reescrever `findPendingForUser` trocando os 3 `NOT IN` por `NOT EXISTS` (mais eficiente com os índices da T2.1 e NULL-safe).
4. **Remover** o endpoint `GET /api/tracking` sem `status` assim que 1 e 2 estiverem no lugar. Com os dois consumidores migrados, ele fica órfão — deletar em vez de deixar depreciado.

### Critérios de aceite
- [ ] `GET /api/tracking/keys` existe, é escopado ao casal do JWT e responde em payload proporcional a 2 campos por track.
- [ ] `MatchScreen` não consome mais o endpoint completo.
- [ ] `DashboardScreen` consome apenas `GET /api/tracking/stats` e renderiza os mesmos números de antes.
- [ ] O endpoint `GET /api/tracking` sem `status` não existe mais, e nenhum código do cliente o referencia.
- [ ] `findPendingForUser` usa `NOT EXISTS` e retorna exatamente o mesmo conjunto de resultados de antes (teste comparativo).
- [ ] `MatchLikeRepositoryTest` cobre o caso de nenhum like do parceiro, likes já rejeitados e likes já rastreados.
- [ ] `./mvnw test` e `bun run typecheck` passam.

### Fora do escopo
Redesenhar as telas. Só a origem dos dados muda.

---

# Épico 4 — Migração de Autenticação

**Objetivo:** tirar o token do alcance de JavaScript, dar poder de revogação ao
servidor e reduzir a janela de validade de 7 dias para 15 minutos.

**Decisões aplicadas:** D2 (cookie HttpOnly, não BFF), D7 (15 min / 30 dias) e,
sobretudo, **D1** — com ~8 usuários conhecidos e downtime tolerável, este épico faz
**cutover direto**. Some toda a cerimônia de compatibilidade que existiria numa base
grande: sem fallback de header, sem janela de transição, sem task de limpeza posterior.
Eram 6 tasks; são 5, e as duas mais chatas desapareceram.

**Ordem obrigatória:** T4.1 → T4.2 → T4.3 → T4.4 → T4.5.

**Deploy:** backend e frontend precisam subir **juntos** — o `scripts/deploy.sh` já faz
isso numa tacada só. No momento do deploy todos os usuários são deslogados uma vez e
precisam entrar de novo. Isso é esperado e foi aceito (D1/D5).

### Diagnóstico consolidado

Fluxo atual:
1. `POST /api/auth/login` → `LoginResponse{token,...}` (`AuthController.java:50`)
2. `setAuthToken(data.token)` → `localStorage` (`client/src/lib/authToken.ts:8`)
3. Interceptor injeta `Authorization: Bearer` (`client/src/lib/api.ts:10-13`)
4. `JwtAuthenticationFilter.java:47-53` valida e popula o `SecurityContext`
5. WebSocket: token na query string (`useMatchStore.ts:62` → `JwtHandshakeInterceptor.java:71-79`)

Riscos: qualquer XSS lê `localStorage.getItem("sessaoADois.token")`; `logout()`
(`useAuthStore.ts:121-126`) só apaga do storage e o token **continua válido no
servidor por até 7 dias** (`application.properties:24`), sem `jti`, sem denylist, sem
endpoint de revogação.

> **Ressalva a manter em mente durante todo o épico:** cookie HttpOnly impede a
> *exfiltração* do token, não o XSS. Um atacante com execução de script ainda cavalga
> a sessão dentro da própria página. Por isso a CSP da **T1.4** não é opcional — é o
> que dá sentido a este épico inteiro.

---

## T4.1 — Refresh token persistido e emissão via cookie

**Criticidade:** Alto
**Arquivos:** nova migration `V5__create_refresh_token.sql`, novos `RefreshToken.java`/`RefreshTokenRepository.java`/`RefreshTokenService.java` em `com.app.auth`, `AuthController.java`, `AuthService.java`, `JwtService.java`, `application.properties`

### O que fazer
1. Entidade `RefreshToken`: `id`, `user_id`, `token_hash` (SHA-256 — **nunca** o valor em claro), `expires_at`, `revoked_at`, `replaced_by_id`, `user_agent`, `ip`, `created_at`. Migration seguindo as convenções de `api/CLAUDE.md` (UUID gerado client-side, `Instant` → `TIMESTAMP WITH TIME ZONE`).
2. Reduzir o TTL do access token de 7 dias para **15 minutos**. Trocar `app.jwt.expiration-days` por `app.jwt.access-token-ttl` (`Duration`), mantendo compatibilidade de leitura durante a transição.
3. Adicionar claims `iss` e `aud` ao token e **validá-los** em `JwtService.parseSubject` (hoje, linhas 41-48, não são verificados).
4. `POST /api/auth/login` passa a emitir dois cookies e **deixa de devolver o token no corpo** (cutover direto, conforme D1):

| Cookie | Flags | TTL | Path |
|---|---|---|---|
| `access_token` | HttpOnly, Secure, SameSite=Strict | 15 min | `/api` |
| `refresh_token` | HttpOnly, Secure, SameSite=Strict | 30 dias | `/api/auth/refresh` |

O `Path` restrito do refresh garante que ele nem sequer é enviado nas requisições normais.

5. Remover o campo `token` de `LoginResponse` e do tipo espelhado em `client/src/stores/useAuthStore.ts:34-38`.

### Critérios de aceite
- [ ] Login retorna `Set-Cookie` para ambos os cookies, com todas as flags acima.
- [ ] O access token expira em 15 minutos.
- [ ] Um token com `iss`/`aud` incorretos é rejeitado.
- [ ] `refresh_token` é persistido apenas como hash — não há como recuperar o valor original do banco.
- [ ] `LoginResponse` **não** contém mais `token`.
- [ ] `Secure` é condicionado ao ambiente para não quebrar dev em `http://localhost`.
- [ ] Existem testes para emissão, TTL, hashing e validação de claims.

### Fora do escopo
Consumir o cookie no filtro (T4.2), rotação (T4.4). Enquanto a T4.2 não estiver pronta,
a branch fica temporariamente sem login funcional — as duas tasks vão para produção no
mesmo deploy.

---

## T4.2 — Filtro lê o cookie

**Criticidade:** Alto
**Depende de:** T4.1
**Arquivos:** `api/src/main/java/com/app/security/JwtAuthenticationFilter.java`

### O que fazer
`JwtAuthenticationFilter` passa a ler o token do cookie `access_token`, substituindo a
leitura do header `Authorization: Bearer` (linhas 47-53).

**Sem fallback de header.** Numa base grande, manteríamos as duas fontes por algumas
semanas para não deslogar ninguém no deploy — com 8 usuários conhecidos (D1) isso é
cerimônia pura. Corta direto.

### Critérios de aceite
- [ ] Request com cookie válido autentica.
- [ ] Request com `Authorization: Bearer` e sem cookie **não** autentica.
- [ ] Request sem cookie segue a cadeia sem autenticação (comportamento atual preservado — a rejeição continua a cargo do `authorizeHttpRequests`).
- [ ] Cookie com token inválido ou expirado limpa o `SecurityContext`.
- [ ] Cookie presente mas vazio/malformado não lança 500.
- [ ] `JwtAuthenticationFilterTest` cobre os 5 casos.

### Fora do escopo
Mudar o cliente — T4.5.

---

## T4.3 — Reativar CSRF

**Criticidade:** Alto
**Depende de:** T4.2
**Arquivos:** `api/src/main/java/com/app/security/SecurityConfig.java`, `client/src/lib/api.ts`

### Problema
`SecurityConfig.java:38` faz `csrf.disable()`. Isso está **correto hoje** (autenticação
por header não é enviada automaticamente pelo browser), mas vira um buraco no momento
em que o cookie começa a autenticar.

### O que fazer
Reativar CSRF com `CookieCsrfTokenRepository.withHttpOnlyFalse()` +
`XorCsrfTokenRequestAttributeHandler`. O axios envia `X-XSRF-TOKEN` nativamente via
`xsrfCookieName`/`xsrfHeaderName` — basta configurar em `client/src/lib/api.ts`.

Manter `/api/auth/login`, `/api/auth/register` e `/api/health` isentos (não têm sessão
a proteger).

Com `SameSite=Strict` + deploy same-origin, isso é defesa em profundidade, não a linha
única — mas é obrigatório porque `SameSite` não é garantia universal entre browsers e
versões.

### Critérios de aceite
- [ ] POST/PUT/PATCH/DELETE autenticados **sem** `X-XSRF-TOKEN` retornam 403.
- [ ] Os mesmos verbos **com** o token funcionam.
- [ ] GET não exige token CSRF.
- [ ] Login e registro funcionam sem token CSRF prévio.
- [ ] O cliente obtém e envia o token automaticamente, sem código manual por chamada.
- [ ] `SecurityConfig` tem teste de integração cobrindo aceite e rejeição.

---

## T4.4 — Rotação com detecção de reuso e logout server-side

**Criticidade:** Alto
**Depende de:** T4.3
**Arquivos:** `AuthController.java`, `RefreshTokenService.java`, `client/src/stores/useAuthStore.ts`

### O que fazer
1. `POST /api/auth/refresh`: valida o refresh recebido, emite um novo par (access + refresh) e **revoga o anterior**, gravando `replaced_by_id`.
2. **Detecção de reuso:** se um refresh **já revogado** for apresentado, revogar a **família inteira** de tokens daquele usuário — é a assinatura de um token roubado. Registrar em log de auditoria.
3. `POST /api/auth/logout`: revoga o refresh atual no servidor e limpa ambos os cookies. Resolve o gap onde `useAuthStore.logout()` só apagava o `localStorage`.

### Critérios de aceite
- [ ] Refresh válido devolve novo par e invalida o anterior.
- [ ] Reapresentar um refresh já rotacionado revoga todos os tokens ativos do usuário e retorna 401.
- [ ] Refresh expirado retorna 401 sem revogar a família.
- [ ] `POST /api/auth/logout` invalida a sessão **no servidor** — o access token anterior deixa de funcionar em ≤15min e o refresh imediatamente.
- [ ] Refreshes concorrentes (duas abas) não derrubam a sessão por falso positivo de reuso — definir e testar a janela de tolerância.
- [ ] Testes cobrindo rotação, reuso, expiração e concorrência.

### Fora do escopo
"Encerrar sessão em todos os dispositivos" na UI — a base de dados já suporta,
mas a tela é feature de produto.

---

## T4.5 — Frontend: remover o token do JavaScript e fechar a migração

**Criticidade:** Alto
**Depende de:** T4.4
**Arquivos:** deletar `client/src/lib/authToken.ts`; alterar `client/src/lib/api.ts`, `client/src/stores/useAuthStore.ts`, `client/src/stores/useMatchStore.ts`, `api/src/main/java/com/app/websocket/JwtHandshakeInterceptor.java`, `docs/ARCHITECTURE.md`, `docs/BACKLOG.md`

### O que fazer
1. **Deletar `client/src/lib/authToken.ts` inteiro.** Nenhum código do cliente volta a tocar em `localStorage` para credenciais.
2. `api.ts`: adicionar `withCredentials: true`; remover o interceptor de request que injeta `Authorization` (linhas 9-15).
3. Interceptor de 401 (`api.ts:17-27`) vira **single-flight**: na primeira 401, chama `/api/auth/refresh` **uma vez**, enfileira as requisições concorrentes, e reexecuta a original ao suceder. Só redireciona para `/login` se o refresh falhar. Hoje, qualquer 401 já derruba a sessão direto.
4. `useAuthStore`: `token` sai do estado. `isAuthenticated` passa a derivar da presença de sessão do usuário (via `/api/couple/me` no bootstrap ou de um `/api/auth/me`), não de um token local. Manter a persistência de `user`/`couple` em `sessionStorage`/`localStorage` apenas como cache de UI — **nunca** credencial.
5. `useMatchStore.connect` (linha 62): remover o `?token=` da URL do SockJS. Sendo same-origin, o cookie viaja no handshake automaticamente.
6. `JwtHandshakeInterceptor.extractToken` (linhas 67-80): ler do cookie em vez da query string. Isso torna a mitigação da T1.2 (desabilitar o access log do `/ws/`) redundante — mas **mantenha-a assim mesmo**, defesa em profundidade custa uma linha de nginx.
7. Atualizar a documentação que passa a estar desatualizada: `docs/ARCHITECTURE.md` §2 descreve `useAuthStore` como "Gerencia JWT", e a decisão #3 de `docs/BACKLOG.md` ("Refresh token: não por enquanto") fica superada.

### Critérios de aceite
- [ ] `grep -r "localStorage" client/src` não retorna nenhuma ocorrência relacionada a token/credencial.
- [ ] `client/src/lib/authToken.ts` não existe mais.
- [ ] A URL do handshake WebSocket não contém `token`.
- [ ] Expirar o access token durante o uso dispara **um único** refresh, e a requisição original é reexecutada de forma transparente ao usuário.
- [ ] Cinco requisições concorrentes recebendo 401 disparam **um** refresh, não cinco.
- [ ] Falha no refresh redireciona para `/login` e limpa o estado local.
- [ ] Logout invalida a sessão no servidor e no cliente.
- [ ] `docs/ARCHITECTURE.md` §2 e a decisão #3 de `docs/BACKLOG.md` refletem o modelo novo.
- [ ] `bun run typecheck`, `bun run lint` e `./mvnw test` passam.

### Nota de deploy
Esta task e as T4.1–T4.4 vão a produção **no mesmo deploy** (`scripts/deploy.sh` sobe
front e back juntos). Todos os usuários são deslogados uma vez — esperado e aceito
(D1/D5).

---

# Épico 5 — Endurecimento de Contas e Auth

**Objetivo:** proteger o que a migração de sessão não cobre — força bruta, enumeração,
ciclo de vida do convite e rastreabilidade.

**Dependências:** nenhuma. Podem rodar em paralelo ao Épico 4.

---

## T5.1 — Rate limiting

**Criticidade:** Alto (OWASP A07)
**Arquivos:** novo filtro/config em `com.app.security`, `pom.xml`

### Problema
`POST /api/auth/login` (`AuthController.java:42`) não tem lockout, captcha, delay nem
contador — força bruta é livre. `POST /api/couple/join` (`CoupleController.java:44`)
também não, o que habilita o ataque descrito na T5.4.

### O que fazer
Adicionar rate limiting (bucket4j ou filtro próprio) com contadores **por IP e por
conta** nos endpoints:

| Endpoint | Limite sugerido |
|---|---|
| `POST /api/auth/login` | 5/min por IP, 10/hora por e-mail |
| `POST /api/auth/register` | 3/min por IP |
| `POST /api/couple/join` | 5/min por IP, 20/hora por usuário |
| `POST /api/auth/refresh` | 30/min por IP |

Responder 429 com `Retry-After`. Ler o IP real de `X-Forwarded-For` — o nginx já o
envia (`deploy/nginx/*.conf`), e sem isso todo tráfego aparece como o IP do proxy.

### Critérios de aceite
- [ ] Exceder o limite retorna 429 com `Retry-After`.
- [ ] O contador usa o IP do cliente final, não o do proxy.
- [ ] Requisições legítimas dentro do limite não são afetadas.
- [ ] Os limites são configuráveis por propriedade.
- [ ] Existem testes de integração para bloqueio e liberação após janela.

---

## T5.2 — Fechar a enumeração de usuários

**Criticidade:** Alto (OWASP A07)
**Arquivos:** `api/src/main/java/com/app/auth/AuthExceptionHandler.java`, `AuthService.java`

### Problema
Dois vetores:
1. `POST /api/auth/register` devolve **409 Conflict** para e-mail já cadastrado (`AuthExceptionHandler.java:18`) → confirma diretamente se um e-mail tem conta.
2. `AuthService.login:33-37`: e-mail inexistente faz `orElseThrow` **sem executar BCrypt**; e-mail existente com senha errada executa. Diferença de tempo mensurável → enumeração por timing.

Ponto positivo já existente: a mensagem de erro de login é idêntica nos dois casos.

### O que fazer
1. No caminho de e-mail inexistente do login, executar um `passwordEncoder.matches` contra um hash dummy constante, para nivelar o tempo de resposta.
2. **Manter o 409 Conflict no registro** (decisão D4 — opção (b)). Não há infraestrutura de e-mail transacional no roadmap, e o vetor fica mitigado pelo rate limit da T5.1. Registrar isso como decisão explícita em `docs/BACKLOG.md`, para que não seja relido no futuro como descuido.

### Critérios de aceite
- [ ] O tempo de resposta de `POST /api/auth/login` para e-mail inexistente e para senha incorreta difere em menos de 10% (medido com ≥100 amostras).
- [ ] `docs/BACKLOG.md` registra a decisão sobre o 409 no registro, com justificativa e a mitigação associada.
- [ ] `AuthServiceTest` cobre o caminho de hash dummy.

---

## T5.3 — Política de senha e validação de entrada do login

**Criticidade:** Médio
**Arquivos:** `api/src/main/java/com/app/auth/RegisterRequest.java`, `LoginRequest.java`, `AuthController.java`

### Problema
1. `RegisterRequest.java:17` só tem `@Size(min = 8)`. Sem máximo: **BCrypt trunca silenciosamente em 72 bytes**, então uma senha de 100 caracteres dá falsa sensação de força.
2. `LoginRequest.java:3` é um record cru, sem nenhuma validação, e `AuthController.java:43` recebe `@RequestBody` **sem `@Valid`** — contraste com `register` na linha 36. Um `email: null` chega até `userRepository.findByEmail(null)`.

### O que fazer
1. `RegisterRequest.password`: `@Size(min = 8, max = 72)`.
2. `LoginRequest`: `@NotBlank @Email` no e-mail, `@NotBlank` na senha.
3. `AuthController.login`: adicionar `@Valid`.
4. Avaliar `BCryptPasswordEncoder` com força 12 (hoje o default é 10 em `SecurityConfig.java:76`) — medir o custo antes de mudar; 10 é aceitável, 12 é o recomendável para 2026.

### Critérios de aceite
- [ ] Senha com mais de 72 bytes é rejeitada com 400 e mensagem clara em pt-BR.
- [ ] `POST /api/auth/login` com e-mail nulo/em branco/malformado retorna 400, não 401 nem 500.
- [ ] O formato de erro de validação é o mesmo dos demais endpoints (ver T6.1).
- [ ] `AuthControllerTest` cobre os novos casos de 400.

### Fora do escopo
Checagem contra lista de senhas vazadas (HaveIBeenPwned) — task futura, exige chamada externa.

---

## T5.4 — Ciclo de vida do código de convite

**Criticidade:** Médio (OWASP A04)
**Depende de:** T5.1 (o rate limit é parte da mitigação)
**Arquivos:** `api/src/main/java/com/app/couple/InviteCodeGenerator.java`, `Couple.java`, `CoupleService.java`, `CoupleResponse.java`, nova migration

### Problema
`InviteCodeGenerator.java:20-27` usa `SecureRandom` sobre alfabeto de 31 caracteres,
mas com **comprimento variável de 6 a 8** — o pior caso são 31⁶ ≈ 8,8×10⁸
combinações. `CoupleService.joinCouple:32-50` não limita tentativas, e o código
**nunca expira nem é invalidado após o pareamento**: continua sendo devolvido em
`CoupleResponse` indefinidamente.

O impacto é contido por `CoupleAlreadyFullException` (linhas 44-46) depois que o casal
está formado, mas **antes** do pareamento um código adivinhado sequestra o vínculo.

### O que fazer
1. Fixar o comprimento em **8 caracteres** (31⁸ ≈ 8,5×10¹¹).
2. Adicionar `invite_code_expires_at` a `couples` (migration), com TTL de **7 dias** (decisão D6 — 24h gerava atrito no fluxo real "criei a conta, meu par entra no fim de semana").
3. `joinCouple` bem-sucedido invalida o código (limpa ou marca como usado).
4. `CoupleResponse` deixa de expor o código depois do pareamento concluído.
5. Endpoint para regenerar o código, caso expire antes do parceiro entrar. A tela `JoinPage`/`InviteCodeTicket` precisa expor essa ação.

### Critérios de aceite
- [ ] Códigos novos têm exatamente 8 caracteres.
- [ ] Código com mais de 7 dias é rejeitado com erro específico e distinto de "código não encontrado".
- [ ] A UI mostra quando o código expira e oferece regenerar.
- [ ] Após o pareamento, o código não aparece mais em nenhuma resposta da API.
- [ ] O usuário consegue regenerar um código expirado.
- [ ] Casais **já existentes** com código de 6-7 caracteres continuam funcionando (a mudança vale para códigos novos).
- [ ] `InviteCodeGeneratorTest` e `CoupleServiceTest` cobrem expiração, invalidação e regeneração.

---

## T5.5 — Log de auditoria de segurança

**Criticidade:** Médio (OWASP A09)
**Arquivos:** novo componente em `com.app.security`, `AuthService.java`, `CoupleService.java`, `api/src/main/java/com/app/match/MatchService.java`

### Problema
Não há registro de login bem-sucedido/falho, logout, pareamento nem revogação de
token. Não há correlation id. Somado às exceções engolidas sem log
(`MatchService.java:103` — `catch (RuntimeException ignored) {}`), um incidente é
praticamente não-investigável. A ironia: o único log rico que existe hoje é o do
nginx, que grava JWTs (T1.2).

### O que fazer
1. Logger dedicado (`security.audit`) com formato estruturado, registrando: login OK/falho (com e-mail e IP), logout, refresh, detecção de reuso de token (T4.4), criação/entrada em casal, e 429 de rate limit (T5.1).
2. **Nunca** logar senha, token, hash de token ou o valor do invite code.
3. Correlation id por request (filtro + MDC), propagado para todos os logs da aplicação.

### Critérios de aceite
- [ ] Todos os eventos listados geram entrada de auditoria.
- [ ] Nenhum log contém credencial, token ou hash.
- [ ] Toda linha de log de uma mesma request compartilha o mesmo correlation id.
- [ ] O logger de auditoria é separado do log de aplicação e pode ter nível/destino próprios.

---

## T5.6 — Remover o e-mail do parceiro da API

**Criticidade:** Baixo
**Arquivos:** `api/src/main/java/com/app/couple/PartnerSummary.java`, `CoupleController.java`, `AuthController.java`, `client/src/stores/useAuthStore.ts`

### Problema
`PartnerSummary` carrega `email` (`AuthController.java:56`, `CoupleController.java:60`),
mas **nenhum componente do frontend consome esse campo** (verificado na auditoria). É
PII entregue sem necessidade.

### O que fazer
Remover `email` de `PartnerSummary` e do tipo espelhado em
`client/src/stores/useAuthStore.ts:16-20`.

### Critérios de aceite
- [ ] `PartnerSummary` não tem mais `email`.
- [ ] Nenhuma resposta da API expõe o e-mail do parceiro.
- [ ] A UI renderiza igual (o campo não era usado).
- [ ] `./mvnw test` e `bun run typecheck` passam.

---

# Épico 6 — Arquitetura e Tratamento de Erros

**Objetivo:** restaurar o *package-by-feature* documentado em `docs/ARCHITECTURE.md` §3
e unificar o tratamento de erros.

**Dependências:** T6.2 depende de T6.1 (precisa da exceção movida para `com.app.common`).

---

## T6.1 — `com.app.common` e `GlobalExceptionHandler`

**Criticidade:** Médio
**Arquivos:** novo pacote `com.app.common`; `AuthExceptionHandler.java`, `CoupleExceptionHandler.java`, `MatchExceptionHandler.java`, `TrackingExceptionHandler.java`, `MediaExceptionHandler.java`, `NotificationExceptionHandler.java`

### Problema
Dois sintomas da mesma causa:

1. **`ResourceNotFoundException` mora em `com.app.tracking`** mas é importada por `com.app.match.MatchService:12`, `com.app.match.MatchController:5` e `com.app.notification.NotificationService:5`. Uma exceção de uma feature virou a exceção global de fato — vazamento que quebra o *package-by-feature*.
2. **Seis `@RestControllerAdvice` com `basePackages`**, e quatro deles (`AuthExceptionHandler:26`, `CoupleExceptionHandler:36`, `MatchExceptionHandler:28`, `TrackingExceptionHandler:32`) implementam `MethodArgumentNotValidException` com corpo praticamente idêntico. Um bug de formato num deles não aparece nos outros.

### O que fazer
1. Criar `com.app.common` e mover `ResourceNotFoundException` para lá.
2. Criar `GlobalExceptionHandler` (`@RestControllerAdvice` sem `basePackages`) tratando o que é transversal: `MethodArgumentNotValidException`, `AccessDeniedException`, `ResourceNotFoundException` e um fallback para `Exception` que retorna 500 genérico **sem stacktrace nem detalhe interno**.
3. Reduzir os advices por feature às exceções realmente específicas do domínio: `TitleAlreadyTrackedException`, `TmdbUnavailableException`, `InviteCodeNotFoundException`, `EmailAlreadyExistsException`, etc.
4. Manter o formato de resposta de erro **exatamente** como está hoje (`{message, errors}`) — esta task não muda contrato de API.

### Critérios de aceite
- [ ] `ResourceNotFoundException` está em `com.app.common`.
- [ ] Existe exatamente **um** handler para `MethodArgumentNotValidException` na aplicação.
- [ ] O JSON de erro de todos os endpoints é idêntico ao anterior (verificado pelos testes de controller existentes, sem alteração de asserção).
- [ ] Uma exceção não tratada retorna 500 genérico sem vazar stacktrace, classe ou mensagem interna.
- [ ] `./mvnw test` passa.

---

## T6.2 — `TrackingFacade` para desacoplar `com.app.match`

**Criticidade:** Médio
**Depende de:** T6.1
**Arquivos:** `api/src/main/java/com/app/match/MatchService.java`, novo `TrackingFacade` em `com.app.tracking`

### Problema
`MatchService` importa `MediaTrack`, `MediaTrackRepository` e `MediaStatus` de
`com.app.tracking` (linhas 9-11) e **escreve direto no repositório de outra feature**
(`mediaTrackRepository.save(track)`, linha 114).

Consequência concreta: a criação de `MediaTrack` tem hoje dois caminhos com regras
diferentes. `MediaTrackService.addTrack` enriquece gêneros e dispara
`RatingRequestService`; `MatchService.createMatch:109-121` grava gêneros mas **não**
passa pelo orquestrador. Qualquer regra nova de criação precisa ser lembrada em dois
lugares.

### O que fazer
Expor uma porta `TrackingFacade` em `com.app.tracking` com
`createTrackFromMatch(Couple couple, Long tmdbId, MediaType mediaType, ...)`,
encapsulando a criação e todas as suas regras. `MatchService` passa a depender dela e
deixa de importar `MediaTrackRepository`.

Isso restaura a inversão de dependência (D do SOLID) e garante que uma regra nova de
criação valha para os dois caminhos automaticamente.

### Critérios de aceite
- [ ] `MatchService` não importa `MediaTrackRepository`.
- [ ] Criar um track por match e criá-lo manualmente produzem entidades equivalentes (mesmos campos preenchidos, mesmas regras aplicadas).
- [ ] O evento STOMP de match e a notificação continuam sendo emitidos.
- [ ] `MatchServiceTest` passa com o facade mockado.

### Fora do escopo
Mover o `SimpMessagingTemplate.convertAndSend` de `MatchService:117` para o padrão
after-commit usado por `NotificationService.scheduleBroadcast:132-143`. É uma melhoria
real (documentada em `api/CLAUDE.md` como pendência conhecida) mas é task própria.

---

## T6.3 — Tirar repositórios dos controllers

**Criticidade:** Médio
**Arquivos:** `api/src/main/java/com/app/auth/AuthController.java`, `api/src/main/java/com/app/couple/CoupleController.java`, `CoupleService.java`

### Problema
Ambos os controllers injetam `UserRepository` e chamam `findById` diretamente
(`AuthController.java:27,55`; `CoupleController.java:24,53`), pulando a camada de
serviço prevista na arquitetura. E `AuthController.toResponse:53-59` é **cópia byte a
byte** de `CoupleController.toResponse:51-57`.

### O que fazer
Mover o mapeamento para `CoupleService.toResponse(couple, currentUserId)` (ou um
`CoupleResponseMapper` dedicado) e remover `UserRepository` dos dois controllers.

### Critérios de aceite
- [ ] Nenhum controller injeta repositório.
- [ ] Existe **uma** implementação de `Couple` → `CoupleResponse`.
- [ ] O JSON de `POST /api/auth/login`, `GET /api/couple/me`, `POST /api/couple` e `POST /api/couple/join` é idêntico ao anterior.
- [ ] `AuthControllerTest` e `CoupleControllerTest` passam.

---

## T6.4 — Sincronizar `docs/ARCHITECTURE.md` com o código

**Criticidade:** Médio
**Arquivos:** `docs/ARCHITECTURE.md`, `api/CLAUDE.md`

### Problema
`docs/ARCHITECTURE.md:27-34` define exatamente 7 pacotes, e `docs/BACKLOG.md` Task 2.1
manda os endpoints de registro/login em `com.app.user`. A realidade:
`com.app.auth` existe com 10 classes, `com.app.user` ficou só com `User` +
`UserRepository`, e `com.app.notification` (9 classes) não consta em lugar nenhum.

Isso importa mais neste repositório do que no comum: o agente Ralph lê `ARCHITECTURE.md`
como fonte de verdade a cada iteração (`scripts/ralph/CLAUDE.md`), então documentação
divergente é ativamente prejudicial, não apenas desatualizada.

### O que fazer
Atualizar `docs/ARCHITECTURE.md` §3 incluindo `com.app.auth`, `com.app.notification` e
`com.app.common` (criado na T6.1), com uma linha de responsabilidade para cada.
Documentar a regra de dependência entre pacotes: features não importam classes internas
de outras features, apenas portas explícitas (T6.2).

Adicionar uma seção **"Anti-patterns"** — hoje não existe nenhum arquivo com essa
função, e as diretrizes estão espalhadas por 4 lugares (`docs/ARCHITECTURE.md`,
`api/CLAUDE.md`, `client/CLAUDE.md`, `scripts/ralph/progress.txt`). No mínimo:
controller acessando repositório, exceção de feature usada como global, `setState`
síncrono dentro de `useEffect`, `catch` vazio sem log, e criação de entidade fora do
serviço dono dela.

### Critérios de aceite
- [ ] `docs/ARCHITECTURE.md` §3 lista todos os pacotes que existem no código.
- [ ] Existe uma seção de anti-patterns com no mínimo os 5 itens acima, cada um com exemplo e alternativa correta.
- [ ] A regra de dependência entre features está escrita.

---

## T6.5 — Eliminar `catch` silenciosos

**Criticidade:** Médio
**Depende de:** T5.5 (usa o correlation id na correlação dos erros)
**Arquivos:** `api/src/main/java/com/app/match/MatchService.java`, `client/src/screens/MatchScreen.tsx`, `client/src/components/MediaCard.tsx`

### Problema
| Local | Código | Efeito |
|---|---|---|
| `MatchService.java:103` | `catch (RuntimeException ignored) {}` | TMDB cai, aba Sugestões volta vazia, **nada** é registrado |
| `MatchScreen.tsx:420,437` | `catch { // silently ignore }` | Like/reject falha e o card some da fila como se tivesse dado certo |
| `MediaCard.tsx:93` | `.catch(() => {})` | Card renderiza sem detalhes, sem sinal de erro |
| `MatchScreen.tsx:762,773` | `.catch(() => {})` | Filtros de gênero somem silenciosamente |

### O que fazer
**Backend:** `log.warn` com contexto (`tmdbId`, `coupleId`). O padrão correto já existe
no próprio código — `MediaTrackService.java:154` e `RatingRequestService.java:125`
fazem exatamente isso; só não foi aplicado em `MatchService`.

**Frontend:** estado de erro por operação com retry, seguindo o padrão que `SearchTab`
já usa (`fetchError` + botão "Tentar novamente", `MatchScreen.tsx:1130-1147`). O caso
mais grave é o like/reject: hoje a fila avança como se a operação tivesse dado certo,
o que corrompe o modelo mental do usuário.

### Critérios de aceite
- [ ] Nenhum `catch` vazio ou `.catch(() => {})` sem log ou tratamento em `api/src` e `client/src`.
- [ ] Like/reject que falha **não** remove o item da fila e mostra erro com retry.
- [ ] Falha de detalhes num card mostra estado degradado visível, não um card vazio silencioso.
- [ ] Toda exceção engolida no backend gera log com nível apropriado e contexto suficiente para investigação.

---

# Épico 7 — Qualidade do Frontend

**Objetivo:** eliminar duplicação, reduzir complexidade e corrigir os vazamentos de
estado da store.

**Dependências:** T7.2 depende de T7.1 (o hook precisa estar extraído antes da decomposição).

> **Skill `frontend-design`: dispensada neste épico** (decisão D11). As três tasks são
> refatorações puras — o critério de aceite literal é "pixel-idêntico ao atual". A regra
> do `CLAUDE.md` da raiz vale para UI nova ou alterada, não para reorganização de código
> sem efeito visual. A exceção está registrada no próprio `CLAUDE.md`.

---

## T7.1 — Extrair `useCompareSelection` compartilhado

**Criticidade:** Alto
**Arquivos:** novo `client/src/lib/useCompareSelection.ts`, `client/src/screens/MatchScreen.tsx`, `client/src/screens/HubScreen.tsx`

### Problema
`MatchScreen.tsx:212-310` define o hook genérico `useCompareSelection<T>`.
`HubScreen.tsx:164-240` **reimplementa a mesma máquina de estados inline**: mesmos 6
`useState`, mesmo efeito `Promise.all` embrulhado em `setTimeout(..., 0)`, mesmo
listener de Escape, mesmo truque de retry via nova identidade de array
(`setSelectedForCompare(prev => [...prev])`).

Duplicação adicional: `buildComparisonItemFromDetails` e
`buildComparisonItemFromSearchResult` (`MatchScreen.tsx:160-206`) chamam o **mesmo**
endpoint; a segunda só prefere título/ano/pôster já presentes no resultado da busca.

### O que fazer
1. Mover `useCompareSelection` para `client/src/lib/useCompareSelection.ts`.
2. `HubScreen` passa a consumi-lo com `buildComparisonItem` e `(t) => t.id`, removendo os ~75 linhas duplicadas.
3. Unificar as duas funções de `buildComparisonItem` numa só, com parâmetro `overrides?: Partial<ComparisonItem>`.

O `setTimeout(..., 0)` deve ser **preservado**: `client/CLAUDE.md` documenta que a
regra `react-hooks/set-state-in-effect` proíbe `setState` direto no corpo de um
`useEffect`, e esse wrapper é o contorno estabelecido no projeto.

### Critérios de aceite
- [ ] `useCompareSelection` existe em um único arquivo e é consumido pelas duas telas.
- [ ] `HubScreen` não tem mais estado de comparação inline.
- [ ] Existe uma única função de construção de `ComparisonItem`.
- [ ] O comportamento de comparação nas duas telas é idêntico ao atual: seleção de 2, Escape cancela, retry funciona, dialog só abre com ambos carregados.
- [ ] `bun run lint` passa sem novos avisos de `react-hooks`.
- [ ] `bun run typecheck` passa.

---

## T7.2 — Decompor `MatchScreen.tsx`

**Criticidade:** Alto
**Depende de:** T7.1
**Arquivos:** `client/src/screens/MatchScreen.tsx` → novo diretório `client/src/screens/match/`

### Problema
`MatchScreen.tsx` tem **1439 linhas** com 3 componentes. `SearchTab` (linhas 658-1377)
sozinho tem **19 `useState` + 2 `useRef`**, ternários aninhados em 3 níveis no JSX
(linhas 489-560, 1182-1231), uma cadeia de 5 blocos condicionais mutuamente exclusivos
para o estado da lista (1099-1155) — que é uma máquina de estados implícita — e 7
handlers de negócio misturados com layout.

### O que fazer
1. Quebrar em `client/src/screens/match/`: `MatchScreen.tsx` (shell + tabs), `SearchTab.tsx`, `SuggestionsTab.tsx`, `FiltersPanel.tsx`, `SearchResultCard.tsx`.
2. Extrair um hook `useDiscoverSearch()` encapsulando `{query, results, page, totalPages, searching, fetchError, runFetch}`. Os 19 estados são na prática ~4 agregados — *filtros*, *resultado*, *paginação*, *status de rede* — então usar `useReducer`.
3. Trocar a cadeia de 5 condicionais por um `status: 'idle' | 'loading' | 'error' | 'empty' | 'ready'` derivado, renderizado por um `switch`.
4. Preservar integralmente o padrão `lastFetchRef`/`lastAttemptRef` (documentado em `client/CLAUDE.md`), que permite repetir a mesma requisição só trocando a página.

**Refatoração pura:** nenhuma mudança visual ou de comportamento. As cores em valores
arbitrários do Tailwind vêm do protótipo e são a fonte de verdade
(`client/CLAUDE.md`) — não substituir por tokens de tema.

### Critérios de aceite
- [ ] Nenhum arquivo em `client/src/screens/match/` passa de 400 linhas.
- [ ] Nenhum componente tem mais de 8 `useState`.
- [ ] A UI é pixel-idêntica à atual: busca com debounce, filtros, ordenação, paginação, modo comparar, like com todos os estados (`idle`/`loading`/`liked`/`matched`/`error`), skeletons e estados de erro com retry.
- [ ] `bun run lint` e `bun run typecheck` passam.
- [ ] Nenhuma chamada de API nova ou removida.

---

## T7.3 — Corrigir vazamentos da store de match

**Criticidade:** Médio
**Arquivos:** `client/src/stores/useMatchStore.ts`, `client/src/stores/useAuthStore.ts`, `client/src/App.tsx`

### Problema
1. **Vazamento + bug de sessão:** `useMatchStore.ts:39` declara `celebratedMatchKeys` como `Set` em escopo de módulo que **nunca é limpo**. `disconnect()` (linhas 101-106) não o toca, e `useAuthStore.logout()` (linhas 121-126) também não. Consequências: (a) cresce indefinidamente durante a sessão; (b) **usuário A faz logout, usuário B entra na mesma aba e não vê a celebração** de um match cujo `tmdbId` A já havia celebrado.
2. **Assimetria de subscription:** `useMatchStore.ts:72-81` — o retorno do segundo `client.subscribe` (notifications) é descartado; só o primeiro vai para `set({subscription})` e é desinscrito em `disconnect`. Na prática `client.deactivate()` derruba o socket, então o vazamento é limitado, mas a assimetria é risco latente.
3. **Corrida no connect/disconnect:** `connect` faz early-return em `if (get().client)` (linha 51) enquanto `disconnect` zera `client` de forma síncrona e `deactivate()` é assíncrono. O efeito de `App.tsx:42-60` pode disparar reativação sobre um cliente ainda encerrando.

### O que fazer
1. Mover `celebratedMatchKeys` para dentro do estado da store e zerá-lo em `disconnect()`.
2. Guardar **todas** as subscriptions num array e desinscrever todas em `disconnect()`.
3. Adicionar um flag `connecting` para tornar o par connect/disconnect idempotente, aguardando o `deactivate()` antes de permitir nova ativação.

### Critérios de aceite
- [ ] Logout seguido de login com outro usuário na mesma aba mostra a celebração de match normalmente.
- [ ] `disconnect()` desinscreve todas as subscriptions ativas.
- [ ] Ciclos rápidos de connect/disconnect não deixam cliente STOMP órfão nem geram conexão duplicada.
- [ ] A deduplicação de celebração continua funcionando **dentro** de uma sessão (o mesmo match não abre o modal duas vezes, vindo do evento `/match` e da notificação).
- [ ] `bun run typecheck` passa.

---

# Épico 8 — CI/CD e Infraestrutura

**Objetivo:** fechar o loop de qualidade e endurecer o pipeline de deploy.

**Dependências:** nenhuma. **T8.1 deve ser antecipada** para junto do Épico 1 — é
barata e protege todos os épicos seguintes.

---

## T8.1 — CI completo e testes faltantes

**Criticidade:** Médio
**Arquivos:** `.github/workflows/`, `client/package.json`, novos testes em `api/src/test/java/com/app/security/`

### Problema
`docs/ARCHITECTURE.md:26` e `api/CLAUDE.md` exigem cobertura de Controllers, Services e
Repositories. Estão **sem nenhum teste**:

| Classe | Por que importa |
|---|---|
| `security/JwtService` | Núcleo de assinatura/parse do token |
| `security/SecurityConfig` | Nada garante quais rotas são públicas — um `permitAll()` acidental passa no CI |
| `websocket/JwtHandshakeInterceptor` | Único portão de autenticação do WebSocket |
| `HealthController` | Probe de deploy |

Pior: `client/` **não tem nenhum teste** (não há vitest/testing-library no
`package.json`), e o workflow roda só `./mvnw -B test` em `api/` — os scripts
`typecheck` e `lint` do frontend existem mas **nunca rodam no CI**. O workflow também
só dispara em `pull_request → dev`, então merge/push em `main` (produção) não roda
teste nenhum.

### O que fazer
1. `JwtServiceTest`: token válido, expirado, assinatura trocada, subject não-UUID, segredo curto (casa com a T1.1).
2. `SecurityConfigTest` com `@SpringBootTest` + `MockMvc`: afirmar 401 em cada rota privada e 200 nas públicas (`/api/auth/login`, `/api/auth/register`, `/api/health`).
3. `JwtHandshakeInterceptorTest`: token ausente, inválido, válido.
4. Job `frontend` no CI: `bun install && bun run typecheck && bun run lint && bun run build`.
5. Adicionar `main` aos branches-alvo do workflow.
6. Configurar limiar mínimo de cobertura no JaCoCo (o plugin já está declarado em `api/pom.xml` com as execuções `prepare-agent` e `report`, mas sem o goal `check`) — começar no patamar atual para não travar, e subir gradualmente.

### Critérios de aceite
- [ ] Os 3 testes de segurança existem e passam.
- [ ] O CI roda typecheck, lint e build do frontend, e falha se qualquer um falhar.
- [ ] O CI dispara em PRs para `dev` **e** `main`.
- [ ] JaCoCo tem `check` configurado com limiar mínimo declarado.
- [ ] Um PR que quebre o typecheck do frontend é bloqueado pelo CI.

---

## T8.2 — Container não-root e healthcheck

**Criticidade:** Médio
**Arquivos:** `api/Dockerfile`, `docker-compose-prod.yml`

### Problema
`api/Dockerfile` não define `USER` — o processo Java roda como **root**. Escape de
container significa root no host. Também não há `HEALTHCHECK`, embora o endpoint
`/api/health` exista (`HealthController.java:15`) e o `docs/DEPLOY.md` já o use no
troubleshooting.

### O que fazer
No estágio de runtime:
```dockerfile
RUN addgroup -S app && adduser -S app -G app
USER app
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget -qO- http://localhost:8080/api/health || exit 1
```

Preservar a estrutura multi-stage e a ordem de cache de camadas documentada em
`api/CLAUDE.md` (copiar `.mvn/`, `mvnw`, `pom.xml` e rodar `dependency:go-offline`
**antes** de `COPY src/`).

### Critérios de aceite
- [ ] `docker run ... whoami` dentro do container não retorna `root`.
- [ ] `docker ps` mostra o status de health do container.
- [ ] A aplicação sobe e responde normalmente como usuário não-privilegiado.
- [ ] O tamanho da imagem não aumenta significativamente.
- [ ] A ordem de cache das camadas foi preservada.

---

## T8.3 — Segredos no `deploy.sh`

**Criticidade:** Médio
**Arquivos:** `scripts/deploy.sh`

### Problema
`scripts/deploy.sh` escreve `DB_PASSWORD`, `JWT_SECRET` e `TMDB_API_KEY` em texto claro
em `/tmp/sessao-a-dois.env` antes do `scp`, e remove com `rm` (sem `shred`). Numa
máquina multiusuário há uma janela de leitura. O IP da VPS também está hardcoded num
script versionado.

Ponto positivo confirmado na auditoria: o `.env` está corretamente no `.gitignore` e
**nunca foi commitado** (verificado no histórico completo do git).

### O que fazer
1. Fazer `scp` direto do `.env` da raiz — ele já existe, já é ignorado pelo git e tem exatamente as mesmas chaves que o heredoc monta hoje. Elimina o arquivo temporário por completo.
2. Se um temporário for inevitável por algum motivo: `umask 077` + `mktemp` + `trap 'rm -f "$tmp"' EXIT`.
3. Parametrizar `VPS_IP` e `VPS_USER` por variável de ambiente, com os valores atuais como fallback.

### Critérios de aceite
- [ ] Nenhum segredo é escrito em `/tmp`.
- [ ] O deploy funciona fim a fim (build → upload → `docker compose up`).
- [ ] `VPS_IP` e `VPS_USER` são configuráveis sem editar o script.

### Fora do escopo
**Usuário de deploy não-root** (decisão D10): mantém `root`. O modelo de ameaça
— projeto pessoal, um único administrador, 8 usuários conhecidos — não justifica o
custo de setup na VPS (criar usuário, grupo `docker`, permissão em
`/var/www/sessaoadois`) nem o risco de quebrar o pipeline. Revisitar se o app ganhar
usuários desconhecidos.

---

## T8.4 — Varredura de dependências

**Criticidade:** Médio (OWASP A06)
**Arquivos:** `.github/dependabot.yml`, `.github/workflows/`, `api/pom.xml`

### Problema
jjwt `0.12.6` está pinado à mão no `pom.xml` (fora do BOM do Spring Boot → vai
defasar silenciosamente). Não há Dependabot, `dependency-check` nem `npm audit`/`bun
audit` em nenhum lugar do CI.

### O que fazer
1. `.github/dependabot.yml` cobrindo Maven (`api/`) e npm (`client/`), com agrupamento para reduzir ruído de PR.
2. Adicionar `bun audit` ao job de frontend criado na T8.1, falhando em vulnerabilidade de severidade alta ou crítica.
3. Avaliar o `dependency-check-maven` no job do backend (atenção: o download inicial da base NVD é lento — cachear).
4. Documentar por que o jjwt está pinado manualmente, ou removê-lo do pin se o BOM do Spring Boot 4.1.0 já o gerencia.

### Critérios de aceite
- [ ] Dependabot abre PRs para Maven e npm.
- [ ] Vulnerabilidade alta/crítica em dependência do frontend falha o CI.
- [ ] A situação do pin do jjwt está resolvida ou documentada.

---

## T8.5 — Remover `SPRING_FLYWAY_BASELINE_ON_MIGRATE` do compose

**Criticidade:** Baixo
**Depende de:** nada — o primeiro deploy com Flyway **já ocorreu** em produção (decisão D9), então esta task está desbloqueada.
**Arquivos:** `docker-compose-prod.yml`, `docs/FLYWAY.md`, `docs/DEPLOY.md`

### Problema
`docker-compose-prod.yml:12` fixa `SPRING_FLYWAY_BASELINE_ON_MIGRATE: "true"`, enquanto
`application.properties:14-19` documenta explicitamente que isso deve ser passado
**apenas no primeiro deploy** contra o schema pré-existente do Supabase. Deixado
permanentemente ligado, uma migration futura que não seja aplicada pode ser
silenciosamente marcada como baseline em vez de falhar visivelmente.

Este é o achado mais recente — foi introduzido no commit `059690c`.

### O que fazer
1. Conferir uma última vez em produção que `flyway_schema_history` tem as linhas de V1 (baseline) e V2 (aplicada) com `success = true` — é uma query, e confirma o que D9 já afirma.
2. Remover a variável do compose.
3. Documentar em `docs/DEPLOY.md` como passá-la pontualmente (`SPRING_FLYWAY_BASELINE_ON_MIGRATE=true docker compose up -d`) caso algum ambiente novo precise.

### Critérios de aceite
- [ ] A variável não está mais em `docker-compose-prod.yml`.
- [ ] Deploy subsequente sobe normalmente e aplica migrations pendentes.
- [ ] `flyway_schema_history` foi verificado antes da remoção.
- [ ] `docs/DEPLOY.md` documenta o uso pontual.

---

# Rastreabilidade — auditoria → task

Cada achado da auditoria de 2026-08-02 mapeia para **exatamente uma** task. Nenhum
achado foi descartado, nenhum aparece em duas tasks.

| Achado | Pilar | Task |
|---|---|---|
| Pacotes `auth`/`notification` fora da ARCHITECTURE | Arquitetura | T6.4 |
| `ResourceNotFoundException` como exceção global de fato | Arquitetura | T6.1 |
| `MatchService` escrevendo em repositório de outra feature | Arquitetura | T6.2 |
| 4 handlers duplicados de validação | Arquitetura | T6.1 |
| Controllers injetando `UserRepository` + `toResponse` duplicado | Arquitetura | T6.3 |
| "BFF" declarado mas parcial | Arquitetura | Épico 4 (T4.1–T4.5) |
| `SPRING_FLYWAY_BASELINE_ON_MIGRATE` fixo | Arquitetura | T8.5 |
| `MatchScreen.tsx` com 1439 linhas | Clean Code | T7.2 |
| `useCompareSelection` duplicado | Clean Code | T7.1 |
| `buildComparisonItem*` duplicado | Clean Code | T7.1 |
| `startWatching` sem `@Transactional` + OSIV | Clean Code | T2.3 |
| `catch` silenciosos | Clean Code | T6.5 |
| `LoginRequest` sem validação | Clean Code | T5.3 |
| Testes faltantes + CI incompleto | Clean Code | T8.1 |
| `MediaTrackService` com responsabilidades demais | Clean Code | T2.2 |
| N+1 em `toReviewDto` | Performance | T2.2 |
| N+1 nas coleções LAZY | Performance | T2.3 |
| Ausência de índices | Performance | T2.1 |
| `getPending` com 10 chamadas TMDB sequenciais | Performance | T3.2 |
| `RestClient` sem timeout | Performance | T3.1 |
| `MediaCard` com fetch por card | Performance | T3.2 |
| `GET /api/tracking` sem paginação | Performance | T3.3 |
| `findPendingForUser` com `NOT IN` | Performance | T3.3 |
| Delete derivado de notificações | Performance | T2.4 |
| `celebratedMatchKeys` sem limpeza | Performance | T7.3 |
| Assimetria de subscription STOMP | Performance | T7.3 |
| JWT no log do nginx | Segurança | T1.2 |
| `JWT_SECRET` com default público | Segurança | T1.1 |
| Sem autorização por destino STOMP | Segurança | T1.3 |
| Token em `localStorage`, 7d, sem revogação | Segurança | Épico 4 (T4.1–T4.5) |
| Sem rate limiting | Segurança | T5.1 |
| Enumeração de usuários (409 + timing) | Segurança | T5.2 |
| Sem headers de segurança | Segurança | T1.4 |
| Política de senha frouxa | Segurança | T5.3 |
| Invite code sem expiração/limite | Segurança | T5.4 |
| Segredos em `/tmp` no deploy | Segurança | T8.3 |
| Container como root | Segurança | T8.2 |
| Sem varredura de dependências | Segurança | T8.4 |
| E-mail do parceiro exposto | Segurança | T5.6 |
| Sem log de auditoria | Segurança | T5.5 |
| Injeção SQL/JPQL | Segurança | **Sem achado** — todas as queries são parametrizadas |
