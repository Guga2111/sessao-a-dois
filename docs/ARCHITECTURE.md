# Architecture & Technical Decisions: Couple Media Tracker

## 1. Visão Geral do Sistema
O sistema é um aplicativo para casais gerenciarem o consumo de filmes e séries, composto por uma Single Page Application (SPA) e uma API RESTful atuando como Backend for Frontend (BFF).

## 2. Decisões de Frontend (Client-Side)
- **Stack Base:** React + TypeScript + Vite.
- **Gerenciador de Pacotes:** Bun (`bun install`, `bun run dev`).
- **Estado Global:** Zustand.
  - Store `useAuthStore`: Gerencia dados do usuário ativo e o status do vínculo do casal (código de pareamento). Não guarda nenhum token — a sessão vive em cookies HttpOnly (`access_token` de 15 minutos + `refresh_token` de 30 dias com rotação e detecção de reuso, ver Epico 4 de autenticação), inacessíveis ao JavaScript. O bootstrap do app chama `GET /api/auth/me` para descobrir se há sessão válida; `user`/`couple` continuam em `localStorage` apenas como cache de UI, nunca como credencial.
  - Store `useMatchStore`: Gerencia a fila de WebSockets e o estado global da tela de Match.
- **Testes:** Vitest + Testing Library + jsdom (Épico 11), configurados dentro do `vite.config.ts` já existente. Cobre as três stores Zustand, `useDiscoverSearch`/`useCompareSelection`, o interceptor de 401 de `lib/api.ts`, os quatro guards de rota e os estados de carregando/vazio/erro das telas decompostas no Épico 7 — mock de HTTP em duas camadas (`vi.mock('@/lib/api')` para consumidores; adapter falso do axios para testar o próprio `lib/api.ts`). Gate no job `frontend` do CI com limiar de cobertura declarado; convenções completas em `client/CLAUDE.md`.
- **UI & Estilização:** Shadcn UI + Tailwind CSS.
  - Comando de setup: `bunx --bun shadcn@latest init --preset bbb02Km --template vite --pointer`.
- **Mapeamento do Protótipo (Claude Design) para Componentes React:**
  - O arquivo monolítico atual de interface[cite: 2] será fragmentado nos seguintes módulos principais:
    - `Header.tsx`: Navegação global e Avatar do casal[cite: 2].
    - `HubScreen.tsx`: Listas "Assistindo Atualmente", "Queremos Ver" e "Já Vimos"[cite: 2].
    - `DashboardScreen.tsx`: KPIs (tempo juntos, filmes vs séries, gêneros favoritos) e gráficos utilizando Tailwind[cite: 2].
    - `MatchScreen.tsx`: Interface de swipe ("Passar" e "Curtir") e modal de notificação de Match[cite: 2].
    - `TitleModal.tsx`: Formulário de adição/edição contendo campos de busca, status, nota em estrelas e opinião[cite: 2].

## 3. Decisões de Backend (Server-Side)
- **Linguagem & Framework:** Java 21 e Spring Boot.
- **Build Tool:** Maven.
- **Banco de Dados:** PostgreSQL.
- **Testes:** JUnit 5 e Mockito (obrigatório cobertura para Controllers, Services e Repositories).
- **Arquitetura de Pacotes (Package-by-Feature):**
  - `com.app.auth`: Login, registro e sessão (cookies HttpOnly `access_token`/`refresh_token`, rotação e detecção de reuso). O cadastro/login de usuário mora aqui, não em `com.app.user`.
  - `com.app.common`: Tipos e infraestrutura transversal compartilhados por todas as features — `GlobalExceptionHandler` (único `@RestControllerAdvice` sem `basePackages`, trata os erros genéricos da aplicação), `ResourceNotFoundException` (404 genérico) e `PageResponse<T>` (contrato JSON de paginação).
  - `com.app.user`: A entidade `User`, o `UserRepository` e a borda HTTP do perfil — `UserProfileController` (`PATCH /api/user/me`, edita nome e/ou e-mail; `DELETE /api/user/me`, exclui a conta exigindo a senha atual no corpo) sobre `UserProfileService` e `UserDeletionService`. O cadastro/login continuam em `com.app.auth` (que também é dono de `PUT /api/auth/password`, a troca de senha autenticada). `UserDeletionService` **orquestra** a exclusão: cada feature apaga o que é dela atrás da sua própria porta (`CoupleFacade`, `TrackingFacade`, `MatchFacade`, `NotificationFacade`, `RefreshTokenService`), numa única transação — `com.app.user` nunca importa entidade ou repositório de outra feature.
  - `com.app.couple`: Geração de código de convite, vinculação de duas contas em uma entidade `Couple` e dissolução do vínculo (`DELETE /api/couple/me`). Expõe a porta `CoupleFacade` — ao lado do `TrackingFacade`, é como qualquer feature de fora resolve "o casal deste usuário": `findActiveCoupleId(userId)`, `requireActiveCoupleId(userId)` (404 via `com.app.common.ResourceNotFoundException`), `memberIds(coupleId)` e `dissolveIfActive(userId)`. **Só UUID cruza essa fronteira**: nenhum método aceita ou devolve a entidade `Couple`, e é exatamente isso que impede outra feature de contornar o filtro de casal dissolvido — o filtro `dissolved_at IS NULL` existe num lugar só. Não adicionar um método que exponha a entidade "só desta vez".
  - `com.app.media`: Integração exclusiva com TMDB API (buscas, detalhes, watch providers, gêneros). O frontend não consome o TMDB diretamente.
  - `com.app.tracking`: Registro das avaliações (notas, datas, opiniões) vinculadas ao `Couple`, estatísticas do dashboard e o `TrackingFacade` — a porta explícita pela qual outras features criam um `MediaTrack`.
  - `com.app.match`: Lógica de validação de likes simultâneos e emissão de eventos. Depende de `com.app.tracking` apenas através do `TrackingFacade` e de `com.app.couple` apenas através do `CoupleFacade` — nunca de `MediaTrackRepository`/`MediaTrack`/`MediaStatus` nem de `CoupleRepository`/`Couple` diretamente. Expõe a porta `MatchFacade` (`deleteUserData(userId)`, apaga `match_like` **e** `match_reject` do usuário), usada pela exclusão de conta.
  - `com.app.notification`: Persistência e push (STOMP) de notificações do casal (match, no-match, pedido de avaliação). `NotificationService.notifyCouple`/`notifyRatingRequest` são os únicos pontos de entrada, e ambos recebem **`UUID coupleId`**, não mais a entidade `Couple` (E9.19) — os ids dos membros vêm do `CoupleFacade.memberIds`. Expõe a porta `NotificationFacade` (`deleteUserData(userId)`, apaga as notificações em que o usuário é destinatário **ou** ator), usada pela exclusão de conta.
  - `com.app.email`: Envio de e-mail transacional, atrás da porta `EmailSender` (`sendPasswordReset`, `sendPasswordChangedNotice`) — nenhuma outra feature monta corpo de e-mail ou conhece o provedor. Duas implementações trocáveis por `app.email.provider` (`@ConditionalOnProperty`, nunca `if` dentro de serviço): `LoggingEmailSender` (`log`, default — só loga, sem rede, usada em dev/test) e `ResendEmailSender` (`resend`, produção — POST à API do [Resend](https://resend.com) via um `RestClient` dedicado, mesmo padrão de `com.app.media.TmdbConfig`). `EmailProperties` faz fail-fast das credenciais no startup (`RESEND_API_KEY`, `EMAIL_FROM`, `app.public-url`) quando `provider=resend`, no mesmo molde de `TmdbConfig`/`JwtService`. Consumida por `com.app.auth` para o fluxo de esqueci-minha-senha e o aviso de troca de senha — ver `docs/DEPLOY.md` para as variáveis de deploy e o limite do free tier.
  - `com.app.security`: Configurações de autenticação, filtros JWT, rate limiting e auditoria de segurança.
  - `com.app.observability`: `POST /api/client-errors` (US-003, Épico 12) — recebe o erro de render capturado pelo error boundary do frontend e o entrega ao `ClientErrorLogger`, que grava uma linha `event=client_error` no log da aplicação (nível `warn`, com o `correlationId` do MDC preenchido por `com.app.security.CorrelationIdFilter`, sanitizada contra log forging). Endpoint público (E12.5), sem sessão e sem persistência em banco — o log é a única agregação. `ClientErrorController` não formata a linha de log na mão, mesmo molde de `com.app.security.SecurityAuditLogger`.
  - `com.app.websocket`: Configuração do broker de mensagens e endpoints STOMP. **É a exceção deliberada à regra de dependência abaixo (E9.19):** `CoupleDestinationAuthorizationManager` continua importando `Couple`/`CoupleService` para autorizar os tópicos `/topic/couple/{id}/**`. Isso é infraestrutura de transporte, não uma feature de domínio, e o acoplamento herda de graça o filtro de casal ativo pelo `CoupleService.getCurrentCouple` — não há nenhuma linha sobre dissolução aqui, e não deve nascer nenhuma.
- **Regra de dependência entre features:** uma feature não importa classes internas de outra feature (entidade, repositório, enum de domínio). A comunicação entre features só acontece através de (a) uma porta explícita exposta pela feature dona do dado (`com.app.tracking.TrackingFacade`, `com.app.couple.CoupleFacade`, `com.app.match.MatchFacade`, `com.app.notification.NotificationFacade`) ou (b) tipos de `com.app.common`. Se uma nova regra de negócio precisar de dado de outra feature, adicionar um método à porta existente (ou criar uma nova) em vez de importar o repositório/entidade diretamente. A única exceção registrada é `com.app.websocket`, acima.
  - **Nota sobre o tamanho da superfície (Épico 9):** três das quatro portas (`CoupleFacade`, `MatchFacade`, `NotificationFacade`) nasceram no mesmo épico, e duas delas (`MatchFacade`, `NotificationFacade`) têm hoje **um único método** — `deleteUserData(UUID)`, chamado só pelo `UserDeletionService`. Elas existem por causa da regra de dependência acima, não porque a feature precisava de uma fachada rica: sem elas, `com.app.user` importaria `MatchLikeRepository`/`NotificationRepository` direto. Quem for mexer nelas não precisa procurar um design maior que não está lá — mas também não deve contornar a porta por ela ser fina.

## 4. Modelagem de Dados e Relacionamento
- **Entidade `User`:** Contas individuais (ID, Nome, Email, Senha/Hash).
- **Entidade `Couple`:** Registra o vínculo.
  - Campos: `ID`, `user1_id`, `user2_id`, `invite_code`, `invite_code_expires_at`, `created_at`, `dissolved_at`.
  - Regra de Negócio: Um usuário gera um `invite_code`. O outro usuário insere o código para formar o `Couple`. Todo rastreamento de mídia a partir desse ponto pertence ao `couple_id`.
  - Regra de Negócio (dissolução, D13 — **dissolver, não apagar**): `dissolved_at` nulo significa casal ativo; `DELETE /api/couple/me` grava o instante (`Couple.dissolve()`, que também limpa o `invite_code`) e **nunca** apaga a linha. A ação é **unilateral** — qualquer um dos dois desfaz o vínculo, sem confirmação do parceiro, que descobre na requisição seguinte. O histórico é **preservado no banco e inacessível pela API**: `media_track`, `user_review` e `notification` do casal dissolvido continuam lá, mas somem de todos os endpoints, porque toda resolução de casal filtra `dissolved_at IS NULL` (`CoupleRepository.findActiveByUserId` para o casal do usuário, `findActiveByInviteCode` para o casal do convite). Depois de dissolver, **os dois** ficam livres para formar um casal novo, do zero. Consequência estrutural: um usuário pode ter **várias** linhas em `couples` (uma ativa e N dissolvidas), então qualquer consulta de casal sem o filtro devolve `Optional` com mais de uma linha e vira `500`.
- **Entidade `MediaTrack`:** Registro compartilhado do casal sobre um título.
  - Campos: `ID`, `couple_id`, `tmdb_id`, `media_type` (MOVIE/TV), `status` (WATCHING, WANT_TO_SEE, WATCHED), `watched_date`.
  - `couple_id` é mapeado como `@Column UUID coupleId`, **sem associação JPA** (`@ManyToOne Couple`) — o mesmo vale para `MatchLike`, `MatchReject` e `Notification`. É a mesma coluna e a mesma FK do banco (nenhuma migration foi necessária); o que muda é que a entidade `Couple` deixa de atravessar a fronteira das features, que resolvem membros e nomes pelo `CoupleFacade`.
- **Entidade `UserReview`:** Avaliação individual de cada membro do casal.
  - Campos: `ID`, `media_track_id`, `user_id`, `rating` (1-5), `opinion`.
  - Regra de Negócio: Cada usuário do casal pode dar sua própria nota e escrever sua própria opinião sobre um título.

## 5. Integração em Tempo Real (WebSockets)
- **Tecnologia:** Spring WebSockets com STOMP (Simple Text Oriented Messaging Protocol).
- **Fluxo do Match:**
  1. Usuário A dá "Like" num filme na tela de Match[cite: 2]. O frontend envia requisição HTTP POST padrão para o backend.
  2. Backend registra o Like na tabela temporária de interações.
  3. Backend verifica se o Usuário B já deu Like no mesmo `tmdb_id`.
  4. Se sim, o backend dispara uma mensagem via WebSocket para o tópico específico do casal (ex: `/topic/couple/{couple_id}/match`).
  5. O frontend, inscrito neste tópico via Zustand/WebSocket hook, recebe o evento instantaneamente e renderiza a celebração visual de Match[cite: 2].

## 6. Infraestrutura e DevOps (Docker)
- **Imagens Multi-Stage:** O `Dockerfile` do Spring Boot usará um estágio de `build` (Maven) e um estágio de `runtime` enxuto (JRE 21).
- **Desenvolvimento Local (`docker-compose-dev.yml`):**
  - Banco PostgreSQL nativo para arquitetura ARM64 (garantindo compatibilidade e performance).
  - Inicialização de banco de dados volátil para testes rápidos e desenvolvimento da SPA.
- **Produção na Hostinger (`docker-compose-prod.yml`):**
  - Otimizado para arquitetura x86_64 padrão de VPS Linux.
  - Definição de rede fechada (backend e banco de dados).
  - Aplicação de `restart: always`.
  - Injeção segura de credenciais via arquivo `.env` (credenciais do banco de dados, JWT Secret, TMDB API Key).
- **Deploy Frontend:** Build estático via `bun run build`, enviado por SCP para a VPS.
- **Reverse Proxy:** Nginx servindo frontend estático e proxy para API, com SSL via Certbot/Let's Encrypt.
- **Domínio:** `sessaoadois.luisgosampaio.com`.

## 7. Anti-patterns

Padrões que já causaram retrabalho neste projeto e que devem ser evitados em código novo. Se um destes aparecer numa revisão, é motivo de correção antes do merge.

1. **Controller acessando repositório diretamente.** Um `@RestController` não deve injetar um `*Repository` — isso pula a camada de serviço e espalha regra de negócio (mapeamento, validação) pela borda HTTP. Errado: `AuthController`/`CoupleController` injetando `UserRepository` para montar a resposta na mão. Correto: o controller depende só de serviços (`AuthService`, `CoupleService`) e de um mapper dedicado (`CoupleResponseMapper`) quando a mesma conversão é usada em mais de um lugar.

2. **Exceção de feature usada como exceção global.** Uma exceção que vive dentro do pacote de uma feature (ex.: `com.app.tracking.ResourceNotFoundException`) não deve ser lançada/capturada por outras features como se fosse um tipo genérico — isso cria acoplamento invisível e obriga a mover a classe toda vez que uma feature nova precisar dela. Errado: `com.app.match`/`com.app.notification` importando `ResourceNotFoundException` de `com.app.tracking`. Correto: exceções realmente transversais moram em `com.app.common` e são tratadas por um único `GlobalExceptionHandler`; exceções específicas de domínio (ex.: `TitleAlreadyTrackedException`) continuam no `*ExceptionHandler` da própria feature.

3. **`setState` síncrono dentro de `useEffect` sem guarda de dependência.** Atualizar estado incondicionalmente dentro de um `useEffect` que também depende desse mesmo estado (ou de um valor derivado dele) cria um loop de re-render ou dispara o efeito mais vezes do que o necessário. Errado: `useEffect(() => setFilters(computeFilters(items)), [items, filters])` quando `computeFilters` é determinístico. Correto: derivar o valor com `useMemo` quando possível, ou limitar as dependências do efeito ao que realmente precisa disparar a atualização (ex.: só `[items]`).

4. **Bloco `catch` vazio sem log.** Um `catch` que engole a exceção silenciosamente (`catch {}`/`.catch(() => {})`) faz uma falha real (rede, TMDB fora do ar, dado inconsistente) desaparecer sem deixar rastro, tanto para quem investiga um bug quanto para o usuário, que não sabe que algo falhou. Errado: `.catch(() => {})` no fetch de gêneros/chaves de tracking do `MatchScreen`, ou um `catch (RuntimeException e) {}` no backend. Correto: no mínimo `console.error`/`log.warn` com contexto (ex.: `tmdbId`, `coupleId`); quando a falha afeta o que o usuário vê, também um estado degradado visível (mensagem de erro + retry), não uma tela vazia sem explicação.

5. **Criação de entidade fora do serviço dono dela.** Só o serviço "dono" de uma entidade deve construir e salvar instâncias dela; outra feature que precisa da mesma entidade não deve importar o repositório/construtor diretamente, porque uma regra nova de criação (um campo obrigatório, uma validação) só seria lembrada num dos dois lugares. Errado: `MatchService` construindo um `MediaTrack` na mão e chamando `MediaTrackRepository.save` diretamente. Correto: expor uma porta explícita na feature dona (`com.app.tracking.TrackingFacade.createTrackFromMatch(...)`) e a feature consumidora (`com.app.match`) depende só dela, nunca da entidade/repositório de `com.app.tracking`.