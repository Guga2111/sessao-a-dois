# PRD: Épico 10 — Recuperação de Senha e E-mail Transacional

## Introdução

Hoje o app tem exatamente **um caminho sem volta**: esquecer a senha. Não existe
`POST /api/auth/forgot-password`, não existe tela de recuperação, e a `LoginPage`
não tem sequer um link apontando para lugar nenhum. O `AuthController` expõe
`register`, `login`, `refresh`, `logout`, `me` e `PUT /password` (esta última exige
estar autenticado, ou seja, exige saber a senha). Quem esquece a senha perde a conta
e, com ela, o acesso a todo o histórico do casal — e o único remédio é um `UPDATE`
manual no banco de produção.

A causa raiz não é a falta do endpoint: é a falta de **qualquer forma de o backend
alcançar o usuário fora da sessão HTTP**. Foi isso que empurrou a D4 para "manter o
409 no registro", que impede qualquer aviso de segurança e que torna a recuperação
de senha impossível de construir com segurança.

Este épico entrega as duas coisas, nesta ordem: a infraestrutura de e-mail
transacional (uma porta `EmailSender` com um adaptador de provedor real e um no-op
para dev/test) e, sobre ela, o fluxo completo de recuperação de senha — backend com
o mesmo rigor do refresh token do Épico 4, telas no frontend e o aviso de "sua senha
foi alterada".

**Origem:** varredura de lacunas de MVP (2026-08-06), lacuna #1 do
`POST-MVP-TASK.md` (T10.1 – T10.4).

**Decisão habilitante:** **D14** — e-mail transacional entra no roadmap, superando a
parte da D4 que dizia o contrário. A D4 em si (manter `409 Conflict` no registro)
**continua valendo** e está no Fora de Escopo.

### Convenção de commits deste épico

> **Os commits deste épico NÃO devem conter o trailer `Co-Authored-By`.** Nem
> `Co-Authored-By: Claude ...`, nem `Co-Authored-By: Opus ...`, nem nenhuma outra
> variação. A mensagem termina no corpo do commit. Isso vale para todo commit criado
> durante a implementação deste PRD, inclusive os de correção de CI e os de
> documentação, e vale também para o corpo do Pull Request — nada de assinatura de
> co-autoria de agente.

### Estado do código na abertura deste épico

Levantado em 2026-08-10, no repositório, não presumido do backlog:

| Área | Estado |
|---|---|
| Épicos | 1 a 9 concluídos **e em produção**. O deploy dos Épicos 4→9 saiu em 2026-08-10, com V5, V6 e V7 aplicadas (ver `docs/FLYWAY.md`, seção de atualização de 2026-08-10). O cutover de autenticação do Épico 4 já aconteceu e não se repete. |
| Migrations | Produção está na `V7__add_couple_dissolved_at.sql`. A próxima é a **V8**, criada por este épico — é a **única** dele, e é a **única pendente** no próximo deploy. |
| `com.app.auth` | `AuthController` (`register`, `login`, `refresh`, `logout`, `me`, `PUT /password`), `AuthService`, `AuthCookieService`, `AuthExceptionHandler`, `RefreshToken`/`RefreshTokenRepository`/`RefreshTokenService` (com `revokeFamily`). |
| `com.app.email` | **Não existe.** Nenhuma dependência de e-mail no `pom.xml`, nenhum `spring-boot-starter-mail`, nenhum cliente de provedor. |
| Política de senha | `@Size(min = 8, max = 72, message = "senha deve ter entre 8 e 72 caracteres")` em `RegisterRequest.password` e `ChangePasswordRequest.newPassword`. Não existe classe validadora dedicada — a política **é** a anotação (T5.3). |
| Hash de token | `refresh_token.token_hash` é `VARCHAR(64)`, SHA-256 em hex, valor em claro nunca persistido. É o padrão a copiar. |
| Rate limit | `RateLimitFilter` (por IP, casa `método + path` exato via enum `Endpoint`) + `RateLimitService.tryConsume(key, capacity, window)` + `RateLimitProperties` (`login`, `register`, `refresh`, `coupleJoin`, `loginByEmail`, `coupleJoinByUser`, `coupleDissolve`, `profileUpdate`, `passwordChange`, `accountDelete`). O limite **por dimensão que não é IP** já tem precedente: `loginByEmail`, consumido de dentro do serviço com chave própria. |
| Auditoria | `SecurityAuditLogger` com `loginSuccess/loginFailure/logout/refresh/refreshReuseDetected/coupleCreated/coupleJoined/coupleDissolved/profileUpdated/passwordChanged/accountDeleted/inviteCodeRegenerated/rateLimitExceeded`. Logger dedicado `security.audit`, com correlation id. |
| Limpeza agendada | Precedente único: `NotificationCleanupService` (`@Scheduled(cron = "0 0 3 * * *")` + `@Transactional` + delete em massa + log da quantidade). |
| `SecurityConfig` | CSRF ativo com `CookieCsrfTokenRepository.withHttpOnlyFalse()`, `ignoringRequestMatchers("/api/auth/login", "/api/auth/register", "/api/health", ...)`; `permitAll` em `POST /api/auth/{register,login,refresh}` e `GET /api/health`. |
| Frontend | `client/src/routes/auth/` tem `AuthLayout`, `LoginPage`, `RegisterPage`, `JoinPage`, `BondDissolvedNotice`, `InviteCodeTicket`. `App.tsx` roteia `/`, `/login`, `/register`, `/join`, `/hub`, `/match`, `/dashboard`, `/conta`. Não há nenhuma rota de recuperação. |
| Testes | 48+ arquivos no backend, gate JaCoCo em 90% de linha. `client/` continua **sem runner de teste** — isso é o Épico 11 e permanece fora de escopo aqui. |
| Deploy | `.github/workflows/deploy.yml` escreve o `.env` a partir de secrets (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `TMDB_API_KEY`) e vars (`CORS_ALLOWED_ORIGIN`, `API_PORT`). Já existe a var `APP_URL` (default `https://sessaoadois.luisgosampaio.com`), hoje usada **só** no smoke test — ela não chega ao `.env` nem ao container. |

### Decisões tomadas para este épico

Tomadas em 2026-08-10, com o mantenedor. Vinculantes; se alguma for revertida
durante a implementação, registrar aqui o motivo.

| # | Questão | Decisão |
|---|---|---|
| **E10.1** | Provedor de e-mail | **Resend.** Free tier de 3.000 e-mails/mês e 100/dia — folga enorme para ~8 usuários. API HTTP simples (`POST https://api.resend.com/emails`, `Authorization: Bearer <key>`), consumida com `RestClient` puro, no mesmo molde do `TmdbConfig`. **Sem SDK, sem `spring-boot-starter-mail`, sem SMTP.** |
| **E10.2** | Remetente e domínio | Resend só entrega para destinatário arbitrário com **domínio verificado**. O remetente é `EMAIL_FROM` (ex.: `Sessão a Dois <nao-responda@sessaoadois.luisgosampaio.com>`), e a verificação de DNS do domínio é **gate humano** (US-012). O `onboarding@resend.dev` só entrega para o e-mail dono da conta e serve apenas para o smoke test inicial. |
| **E10.3** | Base do link do e-mail | **Nova variável, alimentada pelo GitHub Actions.** `app.public-url` (env `APP_PUBLIC_URL`), escrita no `.env` pelo `deploy.yml` a partir da var **`APP_URL` que já existe** no workflow, e propagada no `docker-compose-prod.yml`. Não reusar `CORS_ALLOWED_ORIGIN` — sobrecarregar uma variável de segurança com um segundo significado é como se perde o controle de qual é qual. |
| **E10.10** | Onde cada credencial vive no GitHub | `RESEND_API_KEY` é **secret** (Settings > Secrets and variables > Actions), obrigatório no loop de validação do `deploy.yml`. `EMAIL_FROM` é **variable**, não secret — é um endereço de remetente que aparece em todo e-mail enviado, não há o que proteger, e como variable fica legível na aba Actions e pode ter default no workflow, igual a `CORS_ALLOWED_ORIGIN` e `APP_URL`. |
| **E10.4** | T10.4 (aviso de senha alterada) | **Entra neste PRD** (US-008). Fecha o épico sem ponta solta. |
| **E10.5** | Envio real de e-mail | **Gate humano explícito** (US-012), na mesma convenção da US-007 do Épico 8 e do gate do `docs/FLYWAY.md`. Um agente no devcontainer não tem credencial de provedor nem DNS; o critério fica `- [ ]` com o motivo escrito, nunca marcado por otimismo. |
| **E10.6** | Timing do `forgot-password` | O envio do e-mail **não** acontece no caminho síncrono da resposta. `POST /api/auth/forgot-password` responde `202` imediatamente e despacha o envio para um executor dedicado. É o que torna "tempo de resposta equivalente" verdadeiro por construção, em vez de por sorte. |
| **E10.7** | TTL do token de reset | **30 minutos**, configurável por `app.auth.password-reset-ttl` (`Duration`, default `30m`). Uso único; emitir um token novo invalida os anteriores do mesmo usuário. |
| **E10.8** | Falha de e-mail no `forgot-password` | Não altera a resposta (`202` sempre) e não desfaz o token emitido. Loga em `warn` com contexto. O usuário pede de novo. |
| **E10.9** | Conteúdo do e-mail | Texto simples (`text/plain`) + um HTML mínimo derivado do mesmo conteúdo. Sem template engine, sem imagens, sem CSS elaborado — isso está no Fora de Escopo da T10.1. |

## Goals

- Eliminar o único caminho sem volta do app: senha esquecida deixa de significar
  conta perdida.
- Criar uma **porta** de e-mail (`EmailSender`) que qualquer feature futura possa usar
  sem conhecer o provedor, respeitando a regra de dependência da
  `docs/ARCHITECTURE.md` seção 3.
- Manter a suíte de testes **offline**: nenhum teste pode depender de rede ou de
  credencial de provedor.
- Modelar o token de reset com o mesmo rigor do refresh token do Épico 4 — só hash no
  banco, uso único, TTL curto, revogação em cascata das sessões.
- Não abrir nenhum oráculo novo de enumeração de contas: `forgot-password` responde
  igual para e-mail existente e inexistente, em conteúdo e em tempo.
- Dar ao usuário um aviso quando a senha dele mudar, por qualquer um dos dois
  caminhos, sem que a falha desse aviso derrube a operação.

## User Stories

**Ordem obrigatória:** US-001 → US-002 (a porta antes do adaptador). US-003 → US-004
→ US-005 → US-006 (o backend do reset é sequencial). US-007 depende da US-003.
US-008 depende da US-002 e da US-005. US-009 e US-010 (frontend) dependem da US-004 e
US-005. US-011 fecha a documentação e o wiring de deploy. US-012 é o gate humano e
roda **depois** de tudo, contra produção.

O resto da ordem é livre. Cada story cabe numa sessão focada.

---

### US-001: Porta `EmailSender` e implementação no-op

**Description:** As a mantenedor, I want uma porta de e-mail com uma implementação
que só loga so that o resto do app possa depender de "enviar e-mail" sem que a suíte
de testes precise de rede ou de credencial.

**Acceptance Criteria:**
- [ ] Existe o pacote `api/src/main/java/com/app/email/` com a interface `EmailSender`, expondo `sendPasswordReset(String toEmail, String recipientName, String resetLink)` e `sendPasswordChangedNotice(String toEmail, String recipientName)`.
- [ ] `EmailSender` é a **única** superfície pública da feature: nenhuma classe fora de `com.app.email` importa tipo do provedor, nem monta corpo de e-mail, nem conhece a URL do provedor.
- [ ] Existe `LoggingEmailSender` (no-op que loga em `info` o destinatário e o tipo de e-mail, **nunca** o link nem o token completo — o link aparece truncado ou só em `debug`).
- [ ] A escolha da implementação é por property `app.email.provider` (`log` | `resend`), com **`log` como default**, e é feita por `@ConditionalOnProperty` (ou um `@Configuration` equivalente) — não por `if` dentro de um serviço.
- [ ] `api/src/test/resources/application.properties` declara `app.email.provider=log` explicitamente, e não só herda o default.
- [ ] Existe `LoggingEmailSenderTest`.
- [ ] `./mvnw test` passa e **nenhum teste faz chamada de rede**.

---

### US-002: Adaptador Resend com fail-fast da credencial

**Description:** As a operador, I want o adaptador do provedor real com fail-fast da
API key so that a aplicação nunca suba em produção fingindo que consegue enviar
e-mail.

**Depende de:** US-001

**Acceptance Criteria:**
- [ ] Existe `ResendEmailSender` em `com.app.email`, ativo apenas com `app.email.provider=resend`, que faz `POST https://api.resend.com/emails` com `Authorization: Bearer ${RESEND_API_KEY}` usando um `RestClient` **dedicado** (não o do TMDB).
- [ ] O `RestClient` tem connect timeout de 3s e read timeout de 5s, configuráveis por property com esses defaults — mesmo padrão da T3.1 (`TmdbConfig`).
- [ ] Com `app.email.provider=resend` e `RESEND_API_KEY` em branco/nula, a aplicação **falha no startup** com `IllegalStateException` e mensagem em pt-BR nomeando a variável de ambiente — mesmo padrão de `TmdbConfig` e da T1.1 (`JwtService`).
- [ ] `app.email.from` (env `EMAIL_FROM`) é obrigatória quando o provedor é `resend`, com o mesmo fail-fast.
- [ ] `app.public-url` (env `APP_PUBLIC_URL`) tem default `https://sessaoadois.luisgosampaio.com`, é validada como URL absoluta no startup e é a base usada para montar o link de reset.
- [ ] Erro HTTP do provedor (4xx/5xx) e timeout viram uma exceção própria da feature (`EmailDeliveryException`), **nunca** propagam tipo do `RestClient` para fora do pacote.
- [ ] O corpo da requisição não contém senha nem nada além de destinatário, remetente, assunto e corpo do e-mail.
- [ ] `ResendEmailSenderTest` cobre: sucesso, 4xx do provedor, 5xx do provedor e timeout — todos com o cliente HTTP mockado (`MockRestServiceServer` ou equivalente), sem rede.
- [ ] Existe teste provando o fail-fast nos três cenários (key ausente, `from` ausente, `public-url` inválida).
- [ ] Nenhuma credencial no repositório: `grep -ri "re_" api/src client/src` não retorna chave de API.
- [ ] `./mvnw test` passa.

---

### US-003: Migration V8, entidade e repositório do token de reset

**Description:** As a desenvolvedor, I want o token de reset persistido só como hash
so that vazar o banco não entregue a capacidade de redefinir senha de ninguém.

**Acceptance Criteria:**
- [ ] Existe `api/src/main/resources/db/migration/V8__create_password_reset_token.sql`, aditiva e idempotente (`CREATE TABLE IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS`), sem `DROP`/`TRUNCATE`, compatível com o H2 em modo PostgreSQL dos testes, seguindo `api/CLAUDE.md` e `docs/FLYWAY.md`.
- [ ] A tabela `password_reset_token` tem: `id` UUID PK, `user_id` UUID NOT NULL com FK para `users.id`, `token_hash` VARCHAR(64) NOT NULL UNIQUE, `expires_at` TIMESTAMP WITH TIME ZONE NOT NULL, `used_at` TIMESTAMP WITH TIME ZONE nullable, `created_at` TIMESTAMP WITH TIME ZONE NOT NULL.
- [ ] Existem os índices `idx_password_reset_token_user` (`user_id`) e `idx_password_reset_token_expires` (`expires_at`).
- [ ] Existe a entidade `PasswordResetToken` em `com.app.auth`, com UUID gerado no lado da aplicação e `Instant` mapeado para `TIMESTAMP WITH TIME ZONE` — as mesmas convenções de `RefreshToken`.
- [ ] A entidade tem os métodos de domínio `isExpired(Instant now)`, `isUsed()` e `markUsed(Instant now)`, com teste cobrindo cada um.
- [ ] Existe `PasswordResetTokenRepository` com `findByTokenHash(String)`, `deleteByUserId(UUID)` (ou equivalente de invalidação em massa) e `deleteByExpiresAtBefore(Instant)` como **delete em massa** (`@Modifying @Query`, retornando `int`), nunca delete derivado que carrega entidades — a lição da T2.4.
- [ ] O teste dedicado de migrations (`FlywayMigrationTest`) sobe com `V1`–`V8` em sequência e `ddl-auto=validate` verde.
- [ ] `PasswordResetTokenRepositoryTest` existe.
- [ ] `./mvnw test` passa.

---

### US-004: `POST /api/auth/forgot-password`

**Description:** As a usuário que esqueceu a senha, I want pedir um link de
redefinição pelo meu e-mail so that eu recupere o acesso sem depender de ninguém.

**Depende de:** US-002, US-003

**Acceptance Criteria:**
- [ ] `POST /api/auth/forgot-password` aceita `{ "email": "..." }` (`@NotBlank @Email`) e responde **`202 Accepted` com corpo vazio**, tanto para e-mail existente quanto inexistente.
- [ ] Para e-mail existente: gera um token de **32 bytes** de `SecureRandom` codificado em Base64 URL-safe, persiste **apenas** o SHA-256 em hex (`token_hash`), com `expires_at = now + app.auth.password-reset-ttl` (default 30 min).
- [ ] Emitir um token novo **invalida** os anteriores daquele usuário (nenhum token válido sobra além do último emitido), com teste provando.
- [ ] O link enviado é `${app.public-url}/redefinir-senha?token=<valor em claro>` — o valor em claro existe apenas em memória e no corpo do e-mail, nunca no banco nem em nenhum log.
- [ ] O envio é despachado para um executor dedicado (E10.6): o handler não bloqueia esperando o provedor, e a resposta sai antes do resultado do envio.
- [ ] Falha no envio **não** altera a resposta e **não** desfaz o token; é logada em `warn` com contexto (id do usuário, nunca o token).
- [ ] Teste comparando o caminho de e-mail existente e inexistente: mesmo status, mesmo corpo, e nenhuma diferença de trabalho síncrono entre os dois — o único ramo extra (persistir + despachar) roda fora do caminho de resposta.
- [ ] `SecurityAuditLogger` ganha `passwordResetRequested(...)`, registrando o pedido **sem** o token e **sem** o e-mail completo (mascarado, no mesmo padrão já usado pela classe).
- [ ] O endpoint é `permitAll` em `SecurityConfig` e está em `ignoringRequestMatchers` do CSRF — usuário deslogado não tem token CSRF para apresentar.
- [ ] `AuthControllerTest`/`AuthServiceTest` cobrem: e-mail existente, e-mail inexistente, e-mail malformado (`400`), provedor fora do ar.
- [ ] `./mvnw test` passa.

---

### US-005: `POST /api/auth/reset-password`

**Description:** As a usuário com o link em mãos, I want definir uma senha nova so
that eu volte a entrar — e que qualquer sessão aberta com a senha antiga morra junto.

**Depende de:** US-004

**Acceptance Criteria:**
- [ ] `POST /api/auth/reset-password` aceita `{ "token": "...", "newPassword": "..." }` e responde `204` no sucesso.
- [ ] `newPassword` é validado com `@Size(min = 8, max = 72)` e **a mesma mensagem** do cadastro e da troca autenticada (T5.3) — política violada responde `400`.
- [ ] Token válido: grava o hash da senha nova com o mesmo `PasswordEncoder` do login, marca `used_at` e chama `RefreshTokenService.revokeFamily(userId)` — **todas** as sessões anteriores caem.
- [ ] Token **inexistente**, **expirado**, **já usado** ou **adulterado** respondem todos `400` com **a mesma mensagem genérica**, sem distinguir os casos e sem revelar se o e-mail existe.
- [ ] Reapresentar o mesmo token depois de um reset bem-sucedido responde `400` — uso único, com teste.
- [ ] Depois do reset, o login com a senha nova funciona e com a antiga responde `401`.
- [ ] Depois do reset, `POST /api/auth/refresh` com um refresh emitido antes responde `401`.
- [ ] O reset **não** autentica o usuário: nenhum cookie de sessão é emitido na resposta. O usuário vai para `/login`.
- [ ] `SecurityAuditLogger` ganha `passwordResetCompleted(UUID userId)`, sem token e sem senha.
- [ ] O endpoint é `permitAll` e isento de CSRF, como o `forgot-password`.
- [ ] Testes cobrindo: fluxo feliz, token expirado, token reusado, token inexistente/adulterado, política de senha violada, revogação das sessões.
- [ ] `./mvnw test` passa.

---

### US-006: Rate limit por IP e por e-mail alvo

**Description:** As a operador, I want limitar o `forgot-password` nas duas dimensões
so that o endpoint não vire ferramenta de spam contra o e-mail de um usuário nem de
varredura a partir de um IP.

**Depende de:** US-005

**Acceptance Criteria:**
- [ ] `RateLimitProperties` ganha `forgotPassword` (default 5/hora), `forgotPasswordByEmail` (default 3/hora) e `resetPassword` (default 10/hora), cada uma com propriedade própria em `application.properties` no formato `app.rate-limit.<nome>.capacity` / `.window` e env var correspondente — **sem reaproveitar `Limit` de outro endpoint** (a regra E9.3 do Épico 9).
- [ ] O limite por IP de `POST /api/auth/forgot-password` e `POST /api/auth/reset-password` é aplicado pelo `RateLimitFilter`, via entradas novas no enum `Endpoint`.
- [ ] O limite **por e-mail alvo** é consumido de dentro do serviço, com chave própria (`password-reset:email:<hash ou e-mail normalizado>`), no mesmo molde de `loginByEmail`.
- [ ] O limite por e-mail alvo é consumido **para e-mail existente e inexistente** — se só contasse no caminho existente, o próprio limite viraria oráculo de enumeração.
- [ ] Estourar qualquer um dos limites responde `429` com header `Retry-After`, e o evento vai para `SecurityAuditLogger.rateLimitExceeded`.
- [ ] O e-mail não aparece em claro na chave de rate limit se as chaves forem logadas em algum ponto — normalizar (trim + lowercase) e, se a chave for logada, mascarar.
- [ ] Testes de rate limit para as três novas propriedades, cobrindo aceite dentro do limite e `429` acima dele.
- [ ] `./mvnw test` passa.

---

### US-007: Limpeza agendada dos tokens expirados

**Description:** As a mantenedor, I want os tokens de reset expirados removidos
automaticamente so that a tabela não cresça indefinidamente com lixo criptográfico.

**Depende de:** US-003

**Acceptance Criteria:**
- [ ] Existe `PasswordResetTokenCleanupService` em `com.app.auth`, com `@Scheduled` + `@Transactional`, no mesmo molde de `NotificationCleanupService`.
- [ ] O job apaga tokens com `expires_at` no passado (e os já usados) em **uma única instrução `DELETE`**, independentemente do volume.
- [ ] O horário do cron não colide com o das 3h do `NotificationCleanupService`.
- [ ] O job loga a quantidade removida em `info`.
- [ ] Existe teste cobrindo: token expirado é removido, token válido não é removido, e o retorno da contagem é o que vai para o log.
- [ ] `./mvnw test` passa.

---

### US-008: Aviso por e-mail quando a senha muda (T10.4)

**Description:** As a titular da conta, I want ser avisado quando minha senha mudar so
that eu perceba imediatamente se não fui eu.

**Depende de:** US-002, US-005

**Acceptance Criteria:**
- [ ] `PUT /api/auth/password` (troca autenticada, Épico 9) dispara `EmailSender.sendPasswordChangedNotice`.
- [ ] `POST /api/auth/reset-password` (US-005) dispara o mesmo aviso.
- [ ] O e-mail é **informativo e não acionável**: sem senha, sem token, sem link de "reverter", sem link de login. Só o fato, o horário e a instrução de procurar o mantenedor se não foi o titular.
- [ ] Falha do provedor **não** faz a operação falhar: a senha já mudou e a resposta continua `204`. O erro é logado em `warn` com contexto (anti-pattern #4 — nada de `catch` vazio).
- [ ] Existe teste com o `EmailSender` lançando exceção, provando que a troca de senha conclui com sucesso mesmo assim, nos **dois** caminhos.
- [ ] O disparo acontece depois do commit da transação da troca de senha — nunca se envia aviso de uma mudança que deu rollback.
- [ ] `./mvnw test` passa.

---

### US-009: Tela `/esqueci-senha`

**Description:** As a usuário que esqueceu a senha, I want uma tela para pedir o link
so that o fluxo exista de fato para quem não lê documentação de API.

**Depende de:** US-004

**Acceptance Criteria:**
- [ ] Existe a rota **pública** `/esqueci-senha` em `client/src/App.tsx`, dentro de `PublicOnlyRoute`, usando o `AuthLayout` já existente.
- [ ] A tela tem um campo de e-mail, validação de formato antes do submit, e botão desabilitado com estado de carregamento enquanto a requisição está em voo.
- [ ] O sucesso mostra confirmação **genérica** — "Se existir uma conta com esse e-mail, enviamos as instruções" — coerente com o `202` do backend, sem revelar se a conta existe.
- [ ] `429` vira mensagem específica ("muitas tentativas, tente novamente mais tarde"), distinta da confirmação genérica.
- [ ] Qualquer outro erro da API vira mensagem visível; nenhum `.catch(() => {})` (anti-pattern #4).
- [ ] A `LoginPage` ganha o link **"Esqueci minha senha"**, apontando para `/esqueci-senha`, sem alterar o resto do visual dela.
- [ ] Os tipos de request/response estão espelhados à mão em `client/src/types/` — não há codegen (`client/CLAUDE.md`).
- [ ] O visual é coerente com `LoginPage`/`RegisterPage` e com o protótipo em `docs/design/claude-design-project/`: fundo `#09090a`, cartão `#161513` com borda `rgba(255,255,255,.07)` e raio 18px, heading em `Bricolage Grotesque` com `letter-spacing:-.02em`, corpo em `DM Sans`, acento âmbar `#ffcb2b`, texto secundário `#a6a39a`.
- [ ] A **skill `frontend-design` foi invocada antes** de escrever qualquer código desta story — é UI nova e a regra do `CLAUDE.md` da raiz vale integralmente (a dispensa da D11 é só para refatoração sem mudança visual).
- [ ] `bun run typecheck`, `bun run lint` e `bun run build` passam.
- [ ] Verificado no browser com a skill `dev-browser`.

---

### US-010: Tela `/redefinir-senha`

**Description:** As a usuário que clicou no link do e-mail, I want definir a senha
nova numa tela clara so that eu não descubra a política de senha por tentativa e erro.

**Depende de:** US-005, US-009

**Acceptance Criteria:**
- [ ] Existe a rota **pública** `/redefinir-senha` em `client/src/App.tsx`, lendo o `token` da query string, no `AuthLayout`.
- [ ] A tela tem os campos de senha nova e confirmação, e a divergência entre eles é barrada no cliente antes do submit.
- [ ] Os requisitos de senha (8 a 72 caracteres) ficam **visíveis antes** do submit, não só na mensagem de erro.
- [ ] Token **ausente** na URL mostra estado de erro claro, com link para `/esqueci-senha` pedir outro.
- [ ] `400` do backend (token inválido, expirado ou usado) mostra a mesma mensagem clara, também com o caminho para pedir outro link — a tela não tenta adivinhar qual dos casos foi.
- [ ] Sucesso mostra confirmação e redireciona para `/login`; o usuário **não** entra logado.
- [ ] Nenhum erro da API é engolido; botão desabilitado e estado de carregamento durante a requisição.
- [ ] Mesma coerência visual e mesmos tokens de design da US-009.
- [ ] A **skill `frontend-design` foi invocada antes** da implementação.
- [ ] `bun run typecheck`, `bun run lint` e `bun run build` passam.
- [ ] Verificado no browser com a skill `dev-browser`.

---

### US-011: Wiring de deploy e documentação

**Description:** As a operador, I want a chave do provedor e a URL pública chegando ao
container pelo pipeline so that o épico funcione em produção sem ninguém editar
arquivo à mão na VPS.

**Depende de:** US-002 a US-010

**Acceptance Criteria:**
- [ ] `.github/workflows/deploy.yml` passa a escrever no `.env`: `RESEND_API_KEY` (secret), `EMAIL_FROM` (var, com default), `APP_PUBLIC_URL` (a partir da var **`APP_URL` já existente**, mesmo default) e `APP_EMAIL_PROVIDER=resend`.
- [ ] `RESEND_API_KEY` entra no loop de secrets obrigatórios do workflow — deploy sem ela **falha explicitamente**, com a mensagem no mesmo formato dos outros. **Consequência a respeitar: o secret precisa existir no GitHub antes do merge**, ou o próximo deploy quebra; isso está escrito no PRD e no `docs/DEPLOY.md`.
- [ ] `docker-compose-prod.yml` propaga as quatro variáveis para o container.
- [ ] `.env.example` ganha as quatro, com comentário e sem valor real.
- [ ] `docs/DEPLOY.md` registra: provedor escolhido (Resend), o limite do free tier (3.000/mês, 100/dia), o que fazer se estourar, as variáveis novas, o passo de verificação de domínio no DNS e a nota de que `APP_URL` agora tem dois usos (smoke test **e** base do link de e-mail).
- [ ] `docs/FLYWAY.md` ganha a linha da `V8` na lista de migrations e a ressalva do pooler do Supabase (porta 5432 direta ou pooler em modo Session no deploy que a carregar), no mesmo formato usado para V3–V7.
- [ ] `docs/SCHEMA_BASELINE.md` ganha a tabela `password_reset_token`, no mesmo formato da seção da `refresh_token` (V5).
- [ ] `docs/ARCHITECTURE.md` seção 3 ganha o pacote `com.app.email`, descrito como porta (`EmailSender`) com adaptador de provedor trocável — e deixa explícito que nenhuma feature importa classe do provedor.
- [ ] `POST-MVP-TASK.md` marca T10.1 a T10.4 conforme a convenção do documento (`**Status:** ✅ **Concluída em <data>** — <épico>, <stories> (<branch>)`), com os critérios não verificáveis por agente deixados em `- [ ]` e o motivo escrito ao lado.
- [ ] Nenhum commit deste épico contém trailer `Co-Authored-By` (ver "Convenção de commits" acima) — verificável com `git log --format=%B <base>..HEAD | grep -i "co-authored-by"` retornando vazio.
- [ ] `./mvnw test`, `bun run typecheck`, `bun run lint` e `bun run build` passam.

---

### US-012: Gate humano — envio real de e-mail em produção

**Description:** As a mantenedor, I want validar o envio real antes de considerar o
épico entregue so that a gente não descubra que nada chega no dia em que alguém
precisar recuperar a senha de verdade.

**Depende de:** US-011

> **Esta story NÃO pode ser executada por um agente.** Exige conta no provedor,
> acesso ao DNS de `luisgosampaio.com` e credencial de produção — nada disso existe no
> devcontainer. Os critérios abaixo ficam `- [ ]` até o mantenedor executá-los, e
> **não devem ser marcados por otimismo** (convenção do `POST-MVP-TASK.md`).

**Roteiro para o mantenedor:**

1. Criar conta no [resend.com](https://resend.com) e gerar uma API key (`re_...`).
2. Em **Domains**, adicionar `sessaoadois.luisgosampaio.com` (ou o domínio raiz) e publicar os registros DNS pedidos (SPF/DKIM). Aguardar a verificação ficar verde.
3. No GitHub, em **Settings > Secrets and variables > Actions**, criar o secret `RESEND_API_KEY` e a variable `EMAIL_FROM` (ex.: `Sessão a Dois <nao-responda@sessaoadois.luisgosampaio.com>`). Confirmar que a variable `APP_URL` existe e aponta para `https://sessaoadois.luisgosampaio.com`.
4. Fazer o deploy e conferir nos logs que a API subiu com `app.email.provider=resend` sem erro de fail-fast.
5. Pedir um reset para um e-mail real que exista no banco, seguir o link e concluir a redefinição.

**Acceptance Criteria:**
- [ ] **GATE HUMANO** — Domínio verificado no Resend (SPF/DKIM verdes).
- [ ] **GATE HUMANO** — Secret `RESEND_API_KEY` e variable `EMAIL_FROM` configurados no GitHub **antes** do merge para a `main` (sem eles o deploy falha no loop de secrets obrigatórios).
- [ ] **GATE HUMANO** — Um e-mail real de recuperação chega à caixa de entrada (e não ao spam), com o link apontando para `https://sessaoadois.luisgosampaio.com/redefinir-senha?token=...`.
- [ ] **GATE HUMANO** — O fluxo completo funciona em produção: pedir, clicar, redefinir, logar com a senha nova, e a sessão antiga (outro navegador) deixa de funcionar.
- [ ] **GATE HUMANO** — O e-mail de aviso de senha alterada (US-008) também chega.
- [ ] **GATE HUMANO** — `security-audit.log` na VPS mostra `passwordResetRequested` e `passwordResetCompleted`, sem token e sem e-mail completo.
- [ ] O resultado da verificação é registrado no PR, com data — no mesmo formato do gate de `docs/FLYWAY.md`.

## Requisitos Funcionais

- **FR-1:** Deve existir o pacote `com.app.email`, cuja única superfície pública é a interface `EmailSender`. Nenhuma feature fora dele pode importar classe do provedor ou montar corpo de e-mail.
- **FR-2:** Deve existir uma implementação no-op que apenas loga, selecionada por `app.email.provider=log`, e ela deve ser o **default** — inclusive em dev e teste.
- **FR-3:** A suíte de testes não pode fazer nenhuma chamada de rede nem exigir credencial de provedor.
- **FR-4:** Com `app.email.provider=resend`, a ausência de `RESEND_API_KEY` ou de `EMAIL_FROM` deve **impedir o startup**, com mensagem em pt-BR nomeando a variável de ambiente.
- **FR-5:** O adaptador do provedor deve ter connect timeout de 3s e read timeout de 5s, configuráveis por property.
- **FR-6:** Deve existir a propriedade `app.public-url` (env `APP_PUBLIC_URL`), validada como URL absoluta no startup, usada como base do link de redefinição.
- **FR-7:** A tabela `password_reset_token` deve ser criada pela migration `V8`, aditiva e idempotente — a única migration deste épico.
- **FR-8:** O token de reset deve ser persistido **apenas** como SHA-256 em hex. O valor em claro não pode aparecer no banco nem em nenhum log.
- **FR-9:** O token deve ter TTL de 30 minutos (`app.auth.password-reset-ttl`), ser de uso único e ser invalidado tanto no uso quanto na emissão de um token novo para o mesmo usuário.
- **FR-10:** `POST /api/auth/forgot-password` deve responder `202` para e-mail existente e inexistente, com corpo idêntico e tempo de resposta equivalente.
- **FR-11:** O envio do e-mail não pode acontecer no caminho síncrono da resposta do `forgot-password`.
- **FR-12:** Falha no envio do e-mail de recuperação não deve alterar a resposta nem desfazer o token; deve ser logada em `warn` com contexto.
- **FR-13:** `POST /api/auth/reset-password` deve aceitar token + senha nova e responder `204` no sucesso.
- **FR-14:** `newPassword` deve obedecer `@Size(min = 8, max = 72)`, com **a mesma mensagem** do cadastro e da troca autenticada (T5.3).
- **FR-15:** Token inexistente, expirado, já usado ou adulterado devem responder `400` com a **mesma** mensagem genérica, sem distinguir os casos.
- **FR-16:** Concluir o reset deve chamar `RefreshTokenService.revokeFamily(userId)`, derrubando todas as sessões anteriores.
- **FR-17:** O reset **não** deve autenticar o usuário — nenhum cookie de sessão na resposta.
- **FR-18:** `POST /api/auth/forgot-password` e `POST /api/auth/reset-password` devem ser públicos (`permitAll`) e isentos de CSRF.
- **FR-19:** `forgot-password` deve ter rate limit em duas dimensões — por IP e por e-mail alvo — com propriedades próprias em `app.rate-limit.*`; `reset-password` deve ter rate limit por IP. Estouro responde `429` com `Retry-After`.
- **FR-20:** O rate limit por e-mail alvo deve ser consumido igualmente para e-mail existente e inexistente.
- **FR-21:** Pedido e conclusão do reset devem gerar evento no `SecurityAuditLogger`, sem token, sem senha e sem o e-mail completo.
- **FR-22:** Deve existir job agendado que apaga tokens expirados e usados em uma única instrução `DELETE`, logando a quantidade removida.
- **FR-23:** A troca de senha pelos **dois** caminhos (`PUT /api/auth/password` e reset) deve disparar e-mail informativo de aviso, após o commit da transação.
- **FR-24:** O e-mail de aviso não pode conter senha, token nem link de ação, e sua falha não pode fazer a troca de senha falhar.
- **FR-25:** Devem existir as rotas públicas `/esqueci-senha` e `/redefinir-senha`, no `AuthLayout`, e a `LoginPage` deve ter link para a primeira.
- **FR-26:** A confirmação do pedido de recuperação na UI deve ser genérica e não revelar se a conta existe.
- **FR-27:** A tela de redefinição deve exibir os requisitos de senha **antes** do submit e tratar token ausente/inválido com estado de erro claro e caminho para pedir outro link.
- **FR-28:** Todo erro da API deve virar mensagem visível na tela — nenhum `catch` silencioso (anti-pattern #4).
- **FR-29:** `RESEND_API_KEY`, `EMAIL_FROM`, `APP_PUBLIC_URL` e `APP_EMAIL_PROVIDER` devem chegar ao container pelo pipeline (`deploy.yml` → `.env` → `docker-compose-prod.yml`), com `APP_PUBLIC_URL` derivada da var `APP_URL` já existente.
- **FR-30:** Nenhuma credencial de provedor pode ser versionada no repositório.
- **FR-31:** Nenhum commit deste épico pode conter o trailer `Co-Authored-By`.

## Non-Goals (Fora de Escopo)

- **Reabrir a D4.** O `409 Conflict` em `POST /api/auth/register` permanece. Este épico habilita a alternativa tecnicamente, mas mudá-la é decisão nova, com PRD próprio.
- **Verificação de e-mail no cadastro** e confirmação do e-mail novo ao editar o perfil (a ponta que o Épico 9 deixou aberta). Depende desta infraestrutura, mas é escopo separado.
- **2FA** e **"magic link" como forma de login**.
- **Fila, retry ou dead-letter de envio.** Falhou, logou, o usuário pede de novo.
- **E-mail de boas-vindas, marketing, digest de atividade, avisos de login em dispositivo novo** e qualquer tela de **preferências de notificação**.
- **Templates elaborados em HTML**, template engine, imagens ou identidade visual no e-mail.
- **Medidor de força de senha** e qualquer mudança na política da T5.3.
- **Mudar o visual das telas de login/cadastro existentes** — a US-009 acrescenta um link, nada mais.
- **Testes de frontend** — é o Épico 11.
- **Webhooks do provedor** (bounce, spam report, entrega confirmada).
- **Encerrar sessão em todos os dispositivos pela UI** — o backend já suporta desde o Épico 4, a tela continua fora de escopo.

## Considerações de Design

- As duas telas novas **não** têm correspondente no protótipo de
  `docs/design/claude-design-project/`. O visual deve ser **derivado** do que já
  existe em `LoginPage`/`RegisterPage`, não inventado: fundo `#09090a`, cartão
  `#161513` com borda `rgba(255,255,255,.07)` e raio 18px, headings em
  `Bricolage Grotesque` com `letter-spacing:-.02em`, corpo em `DM Sans`, acento âmbar
  `#ffcb2b`, secundário `#a6a39a`, verde `#3ddc97` para confirmação.
- Componentes a reusar: `AuthLayout`, os campos de formulário e o padrão de mensagem
  de erro das telas de auth, e o `BondDissolvedNotice` como precedente de "aviso
  estático explicando um estado incomum" (útil para o estado de token inválido).
- A confirmação genérica do `/esqueci-senha` é uma decisão de **segurança** com
  consequência de **UX**: o usuário que digitou o e-mail errado não recebe nada e não
  é avisado disso. Compensar com um texto que já diga isso — "confira a caixa de spam
  e o endereço digitado" — em vez de deixar o silêncio inexplicado.
- A skill `frontend-design` é obrigatória nas US-009 e US-010 (UI nova).

## Considerações Técnicas

- **O ponto de risco do épico é o timing do `forgot-password` (US-004).** O critério
  "tempo de resposta equivalente" é fácil de escrever e fácil de quebrar sem
  perceber: qualquer trabalho síncrono a mais no caminho do e-mail existente (buscar
  usuário, gerar token, chamar o provedor) mede como diferença e vira oráculo de
  enumeração. Por isso a E10.6 tira o envio do caminho da resposta — é uma decisão de
  arquitetura, não uma otimização.
- **Não reaproveitar `Limit` de outro endpoint** no rate limit (regra E9.3 do Épico
  9): cada limite novo ganha propriedade própria, mesmo que o valor default seja
  igual ao de outro.
- **O disparo de e-mail depois do commit** (US-008) evita avisar sobre uma mudança que
  deu rollback. `TransactionSynchronizationManager`/`@TransactionalEventListener(AFTER_COMMIT)`
  é o mecanismo idiomático; o executor assíncrono da E10.6 vale para os dois casos.
- **`api/CLAUDE.md` e `docs/FLYWAY.md`** governam a migration: `IF NOT EXISTS` em
  tudo, sem `DROP`/`TRUNCATE`, compatível com o H2 em modo PostgreSQL, e o
  `FlywayMigrationTest` é a porta que prova que ela aplica.
- **Deploy:** produção está na `V7` desde 2026-08-10, então a `V8` sobe **sozinha** —
  uma migration aditiva, num deploy comum. Ainda assim vale a ressalva permanente do
  `docs/FLYWAY.md`: qualquer deploy com migration pendente usa conexão direta (5432)
  ou pooler em modo Session, por causa dos advisory locks do Flyway.
- **Toda variável de produção nasce no GitHub, nunca na VPS.** O `scripts/deploy.sh`
  copia o `.env` inteiro por `scp`, sem merge, e no CD esse arquivo é gerado do zero
  no runner. Editar o `.env` da VPS à mão funciona até o próximo merge para a `main` —
  depois a edição some em silêncio, sem erro. Por isso cada variável nova deste épico
  é uma mudança em **três** lugares: secret/variable no GitHub, linha no passo que
  escreve o `.env` em `deploy.yml`, e entrada em `environment:` do
  `docker-compose-prod.yml` (+ `.env.example` para o dev local). Faltar o terceiro é o
  caso mais traiçoeiro: o deploy passa, o container sobe, e a variável simplesmente
  não existe lá dentro. Ver `docs/DEPLOY.md`, "O `.env` da VPS é SOBRESCRITO a cada
  deploy".
- **`RESEND_API_KEY` como secret obrigatório no workflow** significa que o pipeline
  de CD passa a falhar sem ela. Ou o secret existe antes do merge para a `main`, ou o
  deploy quebra — não há caminho intermediário, e isso é deliberado: um app que sobe
  achando que consegue enviar e-mail e não consegue é pior que um deploy que falha.
- **Cobertura:** o gate do JaCoCo é 90% de linha. O pacote `com.app.email` é pequeno e
  cheio de caminho de erro — cobrir os ramos de falha do adaptador não é opcional
  para o build passar.

## Métricas de Sucesso

- Um usuário que esqueceu a senha volta a ter acesso **sem intervenção manual no
  banco** — hoje isso é impossível, e é a única métrica que realmente importa.
- `POST /api/auth/forgot-password` responde em tempo estatisticamente indistinguível
  para e-mail existente e inexistente.
- Zero credenciais de provedor no repositório e zero tokens de reset em claro no
  banco ou nos logs.
- A suíte de testes continua rodando offline, sem nenhum teste novo dependente de
  rede.
- Consumo de e-mails muito abaixo do free tier (100/dia): com ~8 usuários, o esperado
  é da ordem de unidades por mês.

## Questões em Aberto

- **O e-mail do aviso de senha alterada deve incluir IP e user-agent de quem fez a
  troca?** Ajudaria o titular a reconhecer se foi ele, mas expõe um dado a mais num
  canal que pode estar comprometido. Assumido **não** no PRD; reabrir se o gate
  humano da US-012 mostrar que o aviso fica vago demais para ser útil.
- **O que acontece se o free tier estourar?** O PRD manda documentar em
  `docs/DEPLOY.md`, mas não define comportamento em runtime. Com ~8 usuários é
  hipótese remota; se um dia deixar de ser, vira task de circuit breaker / degradação
  explícita.
- **Deve haver um limite global (não por IP nem por e-mail) de e-mails por dia**, como
  proteção contra um ataque distribuído que respeite os dois limites atuais?
  Provavelmente prematuro para esta base de usuários — registrado para não se perder.
