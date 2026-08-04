# PRD: Épico 4 — Migração de Autenticação (Cookie HttpOnly + Refresh Token)

## 1. Introdução/Overview

Este épico tira o token de sessão do alcance do JavaScript, dá ao servidor poder
real de revogação e reduz a janela de validade de uma credencial vazada de
**7 dias para 15 minutos**. Ele cobre as tasks T4.1–T4.5 do `POST-MVP-TASK.md`,
derivadas da auditoria de 2026-08-02.

O modelo atual, verificado no código em 2026-08-03:

1. `POST /api/auth/login` devolve `LoginResponse{token, user, couple}`
   (`AuthController.java:50`).
2. `setAuthToken(data.token)` grava em `localStorage` sob a chave
   `sessaoADois.token` (`client/src/lib/authToken.ts`).
3. Um interceptor do axios injeta `Authorization: Bearer <jwt>` em toda
   requisição (`client/src/lib/api.ts:9-15`).
4. `JwtAuthenticationFilter` lê esse header e popula o `SecurityContext`.
5. O WebSocket recebe o token na **query string**
   (`useMatchStore.ts` → `${VITE_API_URL}/ws?token=${token}`) e
   `JwtHandshakeInterceptor.extractToken` o lê de lá.

Três riscos concretos daí:

- **Qualquer XSS lê a credencial.** `localStorage.getItem("sessaoADois.token")`
  é uma linha de script.
- **Não existe revogação.** `useAuthStore.logout()` só apaga o storage local; o
  token continua válido no servidor por até 7 dias
  (`app.jwt.expiration-days=${JWT_EXPIRATION_DAYS:7}`). Não há `jti`, nem
  denylist, nem endpoint de revogação.
- **O token viaja na URL do handshake.** Mitigado pela T1.2 (`access_log off;`
  no `location /ws/`, já em produção), mas ainda exposto a histórico de
  navegador e proxies intermediários.

A solução é **cookie HttpOnly + refresh token com rotação** (decisão D2 — não
BFF: front e API são same-origin em `sessaoadois.luisgosampaio.com`, com o nginx
fazendo proxy de `/api` e `/ws`, o que torna `SameSite=Strict` plenamente eficaz
e dispensa o hop extra).

> **Ressalva que vale para todo o épico:** cookie HttpOnly impede a
> **exfiltração** do token, não o XSS. Um atacante com execução de script ainda
> cavalga a sessão dentro da própria página. É a CSP da T1.4 que dá sentido a
> este épico — e ela **já está em produção**
> (`deploy/nginx/sessaoadois.luisgosampaio.com.conf`, bloco 443:
> `default-src 'self'; ... frame-ancestors 'none'; base-uri 'self'`).

### Estado verificado do repositório (2026-08-03)

Os Épicos 1, 2 e 3 estão implementados. Isso muda o tamanho de algumas tasks
deste épico:

| Pré-requisito | Estado | Consequência para o Épico 4 |
|---|---|---|
| T1.1 — fail-fast do `JWT_SECRET` | **Feito** (`JwtService.validateSecret`) | O construtor de `JwtService` já valida; a US-002 só acrescenta claims e troca o TTL, sem mexer na validação do segredo. |
| T1.2 — `access_log off` no `/ws/` | **Feito** (nginx conf, ambos os blocos) | Mantida como defesa em profundidade mesmo depois da US-008, conforme a T4.5 já instruía. |
| T1.3 — autorização por destino no STOMP | **Feito** (`WebSocketSecurityConfig` + `CoupleDestinationAuthorizationManager`) | Inclui um `csrfChannelInterceptor` **no-op** com comentário `"Ver T4.3"` — resolvido pela decisão E3 abaixo. |
| T1.4 — headers de segurança HTTP | **Feito** (CSP/HSTS/nosniff/Referrer-Policy no bloco 443) | A CSP já cobre o cenário pós-migração. `connect-src 'self' wss://sessaoadois.luisgosampaio.com` já libera o WebSocket. |
| `spring.jpa.open-in-view=false` (T2.3) | **Feito** | Todo método que lê/escreve `RefreshToken` fora de um `@Transactional` explode com `LazyInitializationException` — a US-001 e a US-006 precisam anotar corretamente. |
| `com.app.common.PageResponse` (T3.3/US-009) | **Feito** | Existe o pacote `com.app.common`; qualquer DTO transversal novo pode morar lá. |

### Decisões de escopo tomadas em 2026-08-03

Quatro pontos onde o `POST-MVP-TASK.md` era ambíguo ou conflitava com o código:

| # | Questão | Decisão |
|---|---------|---------|
| **E1** | A T4.1 manda `access_token` com `Path=/api`, mas o handshake do WebSocket é em `/ws` — o cookie não seria enviado, e a T4.5 (remover o `?token=`) não fecharia. | **`access_token` usa `Path=/`.** O `refresh_token` continua restrito a `Path=/api/auth/refresh`. Custo aceito: o access cookie também viaja nas requisições de arquivo estático do SPA. |
| **E2** | Sem token no JS, como o bootstrap sabe que há sessão? `GET /api/couple/me` responde 404 para "logado sem casal", indistinguível de "deslogado" para essa finalidade. | **Criar `GET /api/auth/me`**, devolvendo `{user, couple}` — o mesmo payload que `LoginResponse` já monta hoje, menos o token. 200 = sessão válida, 401 = sem sessão. |
| **E3** | O `csrfChannelInterceptor` no-op em `WebSocketSecurityConfig` ao reativar CSRF (T4.3). | **Manter o no-op.** O handshake já é autenticado por cookie, o SUBSCRIBE já é autorizado por destino (T1.3), todo SEND de cliente é negado e o nginx aplica `frame-ancestors 'none'`. Ação: atualizar o comentário registrando a decisão, sem código novo. |
| **E4** | "Janela de tolerância" para refresh concorrente (T4.4) — o single-flight do cliente resolve dentro de uma aba, mas duas abas têm interceptors independentes. | **Janela de graça de 30 segundos.** Um refresh revogado há menos de 30s devolve o par que o substituiu (via `replaced_by_id`) em vez de revogar a família. Passados 30s, revoga tudo e retorna 401. |
| **E5** | O TTL de 30 dias do refresh é contado a partir de cada rotação, ou herdado do token original? (A T4.1 diz "30 dias" e não especifica.) | **Deslizante:** cada rotação emite um refresh com **30 dias frescos**. Um usuário que abre o app ao menos uma vez por mês nunca precisa logar de novo. Sem teto absoluto. |
| **E6** | `POST /api/auth/refresh` e `POST /api/auth/logout` devem exigir token CSRF? | **Não — ambos isentos.** Exigir CSRF no refresh acoplaria dois ciclos de vida independentes (sessão e token CSRF) e criaria um caminho onde o refresh transparente falha sem o usuário poder resolver. O pior caso da isenção é um logout forçado por site de terceiros — irritação, não comprometimento — e `SameSite=Strict` já o bloqueia em todo browser moderno. |
| **E7** | `GET /api/couple/me` fica sem consumidor no cliente depois da US-008 (hoje só `useAuthStore.ts:152` o chama). Deletar, como a T3.3 fez com o endpoint órfão? | **Manter.** Os contratos são legitimamente diferentes (`/api/auth/me` responde 200 + `couple: null` para quem não tem casal; `/api/couple/me` responde 404) e ele é o fixture autenticado de `SecurityConfigTest`, `JwtAuthenticationFilterTest` e `CoupleControllerTest`. |
| **E8** | `POST /api/auth/register` deveria emitir os cookies direto, eliminando o `login()` que `useAuthStore.register` dispara logo em seguida? | **Não neste épico.** O fluxo atual continua funcionando sem alteração — o `login()` interno passa a receber `Set-Cookie` em vez de um token no corpo. Mudar o contrato do registro aumentaria o escopo sem ganho de segurança. |

## 2. Goals

- Nenhum código JavaScript do cliente consegue ler a credencial de sessão —
  `client/src/lib/authToken.ts` deixa de existir.
- A janela de validade de um access token vazado cai de **7 dias para 15
  minutos**.
- O servidor passa a ter revogação real: `POST /api/auth/logout` invalida a
  sessão **no banco**, não só no cliente.
- Um refresh token roubado e reutilizado derruba a família inteira de tokens
  daquele usuário — sem derrubar sessões legítimas concorrentes (duas abas).
- O JWT ganha `iss`/`aud` e passa a **validá-los**, fechando o buraco de
  `JwtService.parseSubject` aceitar hoje qualquer token assinado com a chave.
- CSRF volta a ficar ativo, porque a partir daqui o browser passa a enviar a
  credencial automaticamente.
- A URL do handshake WebSocket não contém mais `token`.
- O usuário não percebe a expiração do access token: um refresh transparente,
  **único** mesmo com N requisições concorrentes recebendo 401.

## 3. User Stories

**Ordem obrigatória:** US-001 → US-002 → US-003 → US-004 → US-005 → US-006 →
US-007 → US-008 → US-009. Cada uma depende da anterior; a branch fica sem login
funcional entre a US-003 e a US-004, e é por isso que **todas vão ao mesmo
deploy** (ver seção 7).

---

### US-001: Migration V5, entidade e serviço de refresh token
**Description:** Como desenvolvedor do backend, eu quero uma tabela de refresh
tokens com hash, expiração e cadeia de substituição, para que o servidor tenha
onde registrar e revogar sessões.

**Arquivos:** nova `api/src/main/resources/db/migration/V5__create_refresh_token.sql`,
novos `api/src/main/java/com/app/auth/RefreshToken.java`,
`RefreshTokenRepository.java`, `RefreshTokenService.java`,
`api/src/main/resources/application.properties`,
`api/src/test/resources/application.properties`, `docs/SCHEMA_BASELINE.md`,
`docs/FLYWAY.md`, novo `api/src/test/java/com/app/auth/RefreshTokenServiceTest.java`

**Acceptance Criteria:**
- [ ] `V5__create_refresh_token.sql` cria a tabela `refresh_token` com:
      `id` (UUID, PK, **sem default de banco** — `@GeneratedValue(strategy = UUID)`
      gera client-side, conforme `api/CLAUDE.md`), `user_id` (UUID NOT NULL,
      FK → `users.id`), `token_hash` (VARCHAR(64) NOT NULL UNIQUE),
      `expires_at` (TIMESTAMP WITH TIME ZONE NOT NULL), `revoked_at`
      (TIMESTAMP WITH TIME ZONE, nullable), `replaced_by_id` (UUID, nullable,
      FK → `refresh_token.id`), `user_agent` (VARCHAR(255), nullable), `ip`
      (VARCHAR(45), nullable — cabe IPv6), `created_at` (TIMESTAMP WITH TIME
      ZONE NOT NULL).
- [ ] Todos os campos `Instant` mapeiam para `TIMESTAMP WITH TIME ZONE`, não
      `TIMESTAMP` (`api/CLAUDE.md` — `LocalDateTime` é o que vira `TIMESTAMP`;
      não misturar).
- [ ] A migration cria `idx_refresh_token_user` em `(user_id)` e
      `idx_refresh_token_expires` em `(expires_at)`, ambos com
      `IF NOT EXISTS`, sem `DROP`/`TRUNCATE`, sintaxe compatível com o modo
      PostgreSQL do H2.
- [ ] `RefreshTokenService.issue(user, userAgent, ip)` gera um valor opaco de
      **32 bytes de `SecureRandom`** codificado em Base64 URL-safe, persiste
      **apenas o SHA-256 em hex** desse valor, e devolve o valor em claro ao
      chamador — que é a única vez em que ele existe fora do browser.
- [ ] Não há nenhum caminho de código que recupere o valor original a partir do
      banco. Um `grep` na entidade não encontra campo de token em claro.
- [ ] `RefreshTokenService.findActive(rawToken)` localiza pelo hash e considera
      ativo apenas o que tem `revoked_at == null` **e** `expires_at > now`.
- [ ] `RefreshTokenService.revokeFamily(userId)` marca `revoked_at` em todos os
      tokens ativos daquele usuário numa **única** instrução `UPDATE`
      (`@Modifying @Query`), não carregando entidades em memória — mesmo
      raciocínio da T2.4.
- [ ] TTL do refresh token: **30 dias**, configurável por
      `app.auth.refresh-token-ttl` (tipo `Duration`, default `30d`).
- [ ] **TTL deslizante (decisão E5):** `expires_at` é sempre `now + 30 dias` no
      momento da emissão — inclusive nas emissões por rotação (US-006). Um
      token novo **não** herda o vencimento do que substituiu, e não existe
      teto absoluto de sessão. Consequência: um usuário que abre o app ao menos
      uma vez a cada 30 dias nunca é deslogado.
- [ ] As propriedades novas são declaradas **também** em
      `api/src/test/resources/application.properties` — esse arquivo
      **substitui** o principal nos testes de contexto completo
      (`api/CLAUDE.md`), então uma propriedade obrigatória só no principal
      quebra `SessaoADoisApplicationTests.contextLoads` em silêncio.
- [ ] `FlywayMigrationTest` aplica `V1`→`V5` em sequência e valida a entidade
      `RefreshToken` contra o schema resultante com `ddl-auto=validate`. Sem
      isso a `V5` iria a produção sem nunca ter executado.
- [ ] `docs/SCHEMA_BASELINE.md` documenta a tabela `refresh_token`.
- [ ] `docs/FLYWAY.md` registra a `V5` na lista de migrations e repete a
      ressalva do pooler do Supabase: o deploy que aplicar a `V5` usa a
      connection string direta (5432) ou o pooler em modo **Session**, nunca o
      modo Transaction (6543).
- [ ] `RefreshTokenServiceTest` cobre: emissão (valor devolvido ≠ valor
      persistido), `findActive` para token válido / expirado / revogado /
      inexistente, e `revokeFamily`.
- [ ] `./mvnw test` passa.

---

### US-002: Access token de 15 minutos com `iss`/`aud` validados
**Description:** Como operador do sistema, eu quero que o access token expire em
15 minutos e carregue emissor e audiência verificados, para que um token vazado
tenha janela curta e não possa vir de outra origem.

**Depende de:** US-001

**Arquivos:** `api/src/main/java/com/app/security/JwtService.java`,
`api/src/main/resources/application.properties`,
`api/src/test/resources/application.properties`,
`api/src/test/java/com/app/security/JwtServiceTest.java`, `docs/DEPLOY.md`

**Acceptance Criteria:**
- [ ] `app.jwt.expiration-days` é **removida** e substituída por
      `app.jwt.access-token-ttl` (tipo `Duration`, default `15m`, env
      `JWT_ACCESS_TOKEN_TTL`). A variável antiga `JWT_EXPIRATION_DAYS` sai de
      `application.properties` e de `docs/DEPLOY.md`.
- [ ] `JwtService.generateToken` emite os claims `iss` (`app.jwt.issuer`,
      default `sessao-a-dois`) e `aud` (`app.jwt.audience`, default
      `sessao-a-dois-client`), além do `sub`/`iat`/`exp` atuais.
- [ ] `JwtService.parseSubject` **valida** `iss` e `aud` — hoje (linhas 41-48)
      ele só verifica a assinatura. Um token com `iss` ou `aud` diferentes é
      rejeitado com `JwtException`, mesmo assinado com a chave correta.
- [ ] Um token emitido há mais de 15 minutos é rejeitado.
- [ ] A validação de segredo já existente (`validateSecret`: branco, igual ao
      default de dev, < 32 bytes) **permanece intacta** — esta story não toca
      nela.
- [ ] As três propriedades novas são espelhadas em
      `api/src/test/resources/application.properties`, e `app.jwt.expiration-days`
      é removida de lá também.
- [ ] `JwtServiceTest` cobre: TTL de 15 min aplicado, token com `iss` errado
      rejeitado, token com `aud` errado rejeitado, token válido aceito, e os 4
      cenários de segredo inválido que já testava (sem regressão).
- [ ] `docs/DEPLOY.md` reflete as variáveis de ambiente novas e a remoção de
      `JWT_EXPIRATION_DAYS`.
- [ ] `./mvnw test` passa.

---

### US-003: Login emite cookies, para de devolver o token, e nasce `GET /api/auth/me`
**Description:** Como usuário do app, eu quero que o login estabeleça a sessão
por cookies que o JavaScript não consegue ler, para que um XSS não consiga
roubar minha credencial.

**Depende de:** US-002

**Arquivos:** `api/src/main/java/com/app/auth/AuthController.java`,
`AuthService.java`, `LoginResponse.java`, novo `AuthCookieService.java` em
`com.app.auth`, `api/src/main/resources/application.properties`,
`api/src/test/resources/application.properties`,
`api/src/test/java/com/app/auth/AuthControllerTest.java`,
`api/src/test/java/com/app/auth/AuthServiceTest.java`

**Acceptance Criteria:**
- [ ] `POST /api/auth/login` responde com **dois** `Set-Cookie`:

      | Cookie | Flags | TTL | Path |
      |---|---|---|---|
      | `access_token` | HttpOnly, Secure*, SameSite=Strict | 15 min | `/` |
      | `refresh_token` | HttpOnly, Secure*, SameSite=Strict | 30 dias | `/api/auth/refresh` |

      \* `Secure` é controlado por `app.auth.cookie.secure`
      (`${AUTH_COOKIE_SECURE:true}`), definido como `false` no dev local para
      não quebrar `http://localhost`. O default é `true` — produção nunca
      depende de alguém lembrar de setar.
- [ ] `access_token` usa `Path=/` (decisão **E1**) — é o que permite ao cookie
      chegar ao handshake em `/ws`, que fica fora de `/api`. O `Path` restrito
      do `refresh_token` garante que ele não é enviado em nenhuma requisição
      normal.
- [ ] O record `LoginResponse` **não tem mais o campo `token`** — passa a ser
      `LoginResponse(UserSummary user, CoupleResponse couple)`. Cutover direto,
      sem período de compatibilidade (D1).
- [ ] Existe `GET /api/auth/me` (decisão **E2**), autenticado, devolvendo
      `{user, couple}` — exatamente o mesmo shape de `LoginResponse`. 200 =
      sessão válida; 401 = sem sessão; **nunca** 404 (usuário sem casal devolve
      200 com `couple: null`, ao contrário de `GET /api/couple/me`).
- [ ] A montagem de `CoupleResponse` em `AuthController` é reaproveitada pelos
      dois endpoints — não há uma terceira cópia de `toResponse`. (A cópia
      duplicada entre `AuthController` e `CoupleController` é problema da T6.3
      e **não** entra neste épico; só não pode crescer para três.)
- [ ] `AuthService.login` emite o refresh token via `RefreshTokenService.issue`,
      registrando `user_agent` e `ip` da requisição.
- [ ] O IP registrado é o do cliente final, lido de `X-Forwarded-For` — o nginx
      já o envia (`proxy_set_header X-Forwarded-For`). Sem isso, todo login
      apareceria como o IP do proxy.
- [ ] `POST /api/auth/register` **não muda** — continua devolvendo 201 +
      `RegisterResponse`, sem cookies. O fluxo do cliente segue registrando e
      depois chamando o login.
- [ ] `AuthControllerTest` cobre: os dois `Set-Cookie` com todas as flags,
      ausência do campo `token` no corpo, `Secure` respeitando a propriedade em
      ambos os valores, `GET /api/auth/me` com e sem sessão, e
      `GET /api/auth/me` de usuário sem casal (200 + `couple: null`).
- [ ] `./mvnw test` passa.

**Fora do escopo:** consumir o cookie no filtro — é a US-004. Entre esta story e
a próxima a branch fica **sem login funcional**; as duas vão ao mesmo deploy.

---

### US-004: Filtro autentica pelo cookie, não pelo header
**Description:** Como desenvolvedor do backend, eu quero que o
`JwtAuthenticationFilter` leia o token do cookie `access_token`, para que a
sessão emitida pela US-003 realmente autentique as requisições.

**Depende de:** US-003

**Arquivos:** `api/src/main/java/com/app/security/JwtAuthenticationFilter.java`,
`api/src/test/java/com/app/security/JwtAuthenticationFilterTest.java`

**Acceptance Criteria:**
- [ ] O filtro lê o token do cookie `access_token` via
      `request.getCookies()`, **substituindo** a leitura de
      `Authorization: Bearer` (linhas 47-53).
- [ ] **Sem fallback de header.** Uma requisição com `Authorization: Bearer
      <token válido>` e **sem** cookie **não** autentica. (Numa base grande
      manteríamos as duas fontes por semanas; com 8 usuários conhecidos — D1 —
      isso é cerimônia pura.)
- [ ] Requisição **sem** cookie segue a cadeia sem autenticação — o
      comportamento atual é preservado, e a rejeição continua a cargo do
      `authorizeHttpRequests`/`authenticationEntryPoint` do `SecurityConfig`.
- [ ] Cookie com token inválido ou expirado limpa o `SecurityContext` e segue a
      cadeia (não lança).
- [ ] Cookie presente mas com valor vazio, em branco ou malformado **não** gera
      500 — cai no mesmo caminho de token inválido.
- [ ] `JwtAuthenticationFilterTest` cobre os 5 casos acima e os testes de header
      existentes são **substituídos** (não apenas removidos: o caso "header
      presente, cookie ausente ⇒ não autentica" precisa existir explicitamente).
- [ ] `./mvnw test` passa.

---

### US-005: Reativar CSRF
**Description:** Como operador do sistema, eu quero proteção CSRF ativa, porque
a partir da US-004 o browser envia a credencial automaticamente — o que é
exatamente a pré-condição de um ataque CSRF.

**Depende de:** US-004

**Arquivos:** `api/src/main/java/com/app/security/SecurityConfig.java`,
`api/src/main/java/com/app/websocket/WebSocketSecurityConfig.java` (só
comentário), `client/src/lib/api.ts`,
`api/src/test/java/com/app/security/SecurityConfigTest.java`

**Acceptance Criteria:**
- [ ] `SecurityConfig` troca `csrf.disable()` por
      `CookieCsrfTokenRepository.withHttpOnlyFalse()` +
      `XorCsrfTokenRequestAttributeHandler`.
- [ ] `POST`/`PUT`/`PATCH`/`DELETE` autenticados **sem** `X-XSRF-TOKEN`
      retornam 403.
- [ ] Os mesmos verbos **com** o token funcionam.
- [ ] `GET` não exige token CSRF.
- [ ] `/api/auth/login`, `/api/auth/register` e `/api/health` ficam isentos
      (`ignoringRequestMatchers`) — não têm sessão a proteger. `/api/auth/refresh`
      e `/api/auth/logout` **também** ficam isentos (decisão **E6**): ambos
      precisam funcionar a partir de um estado em que o cliente pode não ter um
      token CSRF válido, e ambos já dependem do cookie `refresh_token` com
      `SameSite=Strict` + `Path` restrito. Exigir CSRF no refresh acoplaria o
      ciclo de vida da sessão ao do token CSRF e criaria um caminho em que o
      refresh transparente falha sem o usuário ter como resolver.
- [ ] `client/src/lib/api.ts` configura `xsrfCookieName: "XSRF-TOKEN"` e
      `xsrfHeaderName: "X-XSRF-TOKEN"` no `axios.create` — o cliente obtém e
      envia o token **automaticamente**, sem código manual por chamada.
- [ ] `SecurityConfig.corsConfigurationSource` passa a permitir o header
      `X-XSRF-TOKEN` e **remove** `Authorization` de `setAllowedHeaders` (deixou
      de ser usado na US-004). `allowCredentials(true)` é mantido.
- [ ] **Decisão E3 aplicada:** o bean `csrfChannelInterceptor` no-op em
      `WebSocketSecurityConfig` é **mantido**, e seu javadoc é atualizado para
      registrar a decisão em vez de apontar para "reavaliar na T4.3". Motivo:
      o handshake já é autenticado por cookie e validado pelo
      `JwtHandshakeInterceptor`, o `SUBSCRIBE` já é autorizado por destino
      (T1.3), todo `SEND` de cliente é negado por `anyMessage().denyAll()`, e a
      CSP em produção aplica `frame-ancestors 'none'`.
- [ ] `SecurityConfigTest` cobre aceite e rejeição por CSRF, e prova que os 5
      endpoints isentos continuam funcionando sem token.
- [ ] `./mvnw test` e `bun run typecheck` passam.

---

### US-006: Rotação com detecção de reuso e logout server-side
**Description:** Como usuário do app, eu quero que minha sessão se renove
sozinha e que "sair" realmente encerre a sessão no servidor, e como operador
quero que um refresh token roubado seja detectado e derrube a família inteira.

**Depende de:** US-005

**Arquivos:** `api/src/main/java/com/app/auth/AuthController.java`,
`RefreshTokenService.java`, `AuthCookieService.java`,
`api/src/main/java/com/app/security/SecurityConfig.java`,
`api/src/test/java/com/app/auth/AuthControllerTest.java`,
`api/src/test/java/com/app/auth/RefreshTokenServiceTest.java`

**Acceptance Criteria:**
- [ ] `POST /api/auth/refresh` (permitAll no `authorizeHttpRequests` — o access
      token está expirado por definição quando ele é chamado) valida o cookie
      `refresh_token`, emite um **novo par** (access + refresh) e **revoga o
      anterior**, gravando `replaced_by_id` no registro revogado.
- [ ] O refresh emitido pela rotação tem `expires_at = now + 30 dias`
      (deslizante, decisão E5) — **não** herda o vencimento do token que
      substituiu. Existe teste provando que rotacionar um refresh com 2 dias de
      vida restante produz um novo com ~30 dias.
- [ ] **Detecção de reuso:** apresentar um refresh **já revogado** revoga a
      família inteira de tokens ativos daquele usuário e retorna 401.
- [ ] **Janela de graça de 30 segundos (decisão E4):** se o token revogado foi
      revogado há **menos de 30s**, o servidor devolve o par que o substituiu
      (seguindo `replaced_by_id`) em vez de revogar a família. Passados 30s,
      revoga. A janela é configurável por `app.auth.refresh-reuse-grace`
      (`Duration`, default `30s`).
- [ ] Refresh **expirado** (não revogado) retorna 401 **sem** revogar a família
      — expiração natural não é sinal de roubo.
- [ ] Requisição a `/api/auth/refresh` **sem** cookie retorna 401, não 500.
- [ ] A detecção de reuso emite um `log.warn` com `userId` e IP. **Nunca** loga
      o valor do token nem o hash. (O logger de auditoria estruturado é a T5.5,
      Épico 5 — aqui basta o warn.)
- [ ] `POST /api/auth/logout` (permitAll) revoga o refresh apresentado **no
      servidor** e devolve `Set-Cookie` de expiração para **ambos** os cookies
      (`Max-Age=0`, mesmo `Path` da emissão — um `Path` diferente não apaga o
      cookie). Chamar logout sem cookie é idempotente: 204, sem erro.
- [ ] Depois do logout, o access token anterior deixa de funcionar em ≤ 15 min
      (expiração natural) e o refresh **imediatamente**.
- [ ] Ambos os endpoints são declarados no `authorizeHttpRequests` de
      `SecurityConfig` junto de `/api/auth/login` e `/api/auth/register`.
- [ ] Testes cobrindo: rotação bem-sucedida, reuso fora da janela (revoga
      família + 401), reuso **dentro** da janela de 30s (devolve o par
      substituto, **não** revoga), expiração (401 sem revogar), refresh sem
      cookie (401), logout com e sem cookie, e logout tornando o refresh
      inválido.
- [ ] `./mvnw test` passa.

**Fora do escopo:** tela de "encerrar sessão em todos os dispositivos" — o
schema já suporta (`revokeFamily`), mas a UI é feature de produto. Rate limiting
em `/api/auth/refresh` é a T5.1 (Épico 5).

---

### US-007: Handshake do WebSocket lê o cookie
**Description:** Como desenvolvedor do backend, eu quero que o
`JwtHandshakeInterceptor` autentique pelo cookie `access_token`, para que o
frontend possa parar de colocar o token na URL.

**Depende de:** US-006

**Arquivos:** `api/src/main/java/com/app/websocket/JwtHandshakeInterceptor.java`,
`api/src/test/java/com/app/websocket/JwtHandshakeInterceptorTest.java`

**Acceptance Criteria:**
- [ ] `JwtHandshakeInterceptor.extractToken` lê o cookie `access_token` do
      `ServletServerHttpRequest`, **substituindo** a leitura do query param
      `token`.
- [ ] Handshake **sem** o cookie é rejeitado com 401 antes de qualquer conexão
      ser aberta — comportamento atual preservado.
- [ ] Cookie com token inválido/expirado/malformado é rejeitado com 401, sem
      lançar 500.
- [ ] O `USER_ID_ATTRIBUTE` continua sendo gravado nos `attributes` da sessão —
      `CoupleDestinationAuthorizationManager` depende dele e **não pode
      regredir** (a autorização por destino da T1.3 quebraria em silêncio,
      liberando o tópico de outro casal).
- [ ] `JwtHandshakeInterceptorTest` cobre: cookie válido (aceita + grava o
      `userId`), cookie ausente (401), cookie inválido (401), cookie vazio
      (401), e query param `?token=` presente **sem** cookie (401 — sem
      fallback, mesma regra da US-004).
- [ ] `WebSocketSecurityConfigTest` e `CoupleDestinationAuthorizationManagerTest`
      continuam passando sem alteração de asserção.
- [ ] `./mvnw test` passa.

---

### US-008: Frontend — remover o token do JavaScript
**Description:** Como usuário do app, eu quero que minha sessão se renove sem
que eu perceba e que nada da minha credencial fique guardado no navegador, para
que um XSS não tenha o que roubar e eu não seja deslogado a cada 15 minutos.

**Depende de:** US-007

**Arquivos:** deletar `client/src/lib/authToken.ts`; alterar
`client/src/lib/api.ts`, `client/src/stores/useAuthStore.ts`,
`client/src/stores/useMatchStore.ts`, `client/src/App.tsx`

**Acceptance Criteria:**
- [ ] `client/src/lib/authToken.ts` **não existe mais**, e nenhum arquivo o
      importa.
- [ ] `grep -rn "localStorage" client/src` não retorna nenhuma ocorrência
      relacionada a token ou credencial. A persistência de `user`/`couple` em
      `sessaoADois.session` **permanece**, explicitamente como cache de UI —
      nunca como credencial, e nunca como fonte de `isAuthenticated`.
- [ ] `api.ts` adiciona `withCredentials: true` no `axios.create` e **remove**
      o interceptor de request que injetava `Authorization` (linhas 9-15).
- [ ] O interceptor de 401 (`api.ts:17-27`) vira **single-flight**: na primeira
      401, chama `POST /api/auth/refresh` **uma única vez**, enfileira as
      requisições concorrentes, e reexecuta cada original quando o refresh
      sucede. Hoje qualquer 401 derruba a sessão direto.
- [ ] Cinco requisições concorrentes recebendo 401 disparam **um** refresh, não
      cinco.
- [ ] Uma 401 vinda do próprio `/api/auth/refresh` **não** entra no loop de
      retry — falha de refresh limpa o estado local e redireciona para `/login`.
- [ ] `useAuthStore`: o campo `token` sai do estado e da interface `AuthState`;
      o tipo `LoginResponse` espelhado perde o campo `token`.
- [ ] `isAuthenticated` passa a derivar da resposta de `GET /api/auth/me`, não
      de um token local. Como o cliente não tem mais como saber se há sessão
      antes de perguntar, o estado inicial vira `loading: true` /
      `isAuthenticated: false`, e `loadCurrentUser()` roda **sempre** no
      bootstrap (`App.tsx`), não só quando havia token.
- [ ] `loadCurrentUser()` chama `GET /api/auth/me`: 200 popula `user` + `couple`
      e marca `isAuthenticated: true`; 401 limpa o estado e a sessão persistida.
      A distinção "logado sem casal" deixa de depender do 404 de
      `/api/couple/me`.
- [ ] `logout()` chama `POST /api/auth/logout` **antes** de limpar o estado
      local, e limpa o estado mesmo se a chamada falhar (rede caída não pode
      prender o usuário logado na UI).
- [ ] `useMatchStore.connect` remove o `?token=` da URL do SockJS
      (`${VITE_API_URL}/ws`) e deixa de chamar `getAuthToken()`. O early-return
      `if (!token) return` é substituído pela condição que já existe no efeito
      de `App.tsx` (`isAuthenticated && hasPartner && coupleId`).
- [ ] O SockJS envia credenciais no handshake — verificar em dev que o cookie
      chega ao `/ws` (front em `:5173`, API em `:8080`: portas diferentes são
      cross-**origin** mas same-**site**, então `SameSite=Strict` não bloqueia;
      `AUTH_COOKIE_SECURE=false` é obrigatório nesse ambiente).
- [ ] Expirar o access token durante o uso dispara um refresh transparente e a
      requisição original é reexecutada — o usuário não vê nada.
- [ ] `bun run typecheck` e `bun run lint` passam.
- [ ] **Verificação manual pelo dono do projeto antes do merge/deploy** (mesmo
      arranjo da US-004 do Épico 3 — este sandbox não tem browser, ver seção 9):
      login, navegação, logout, e `Application → Storage` do DevTools sem
      nenhuma chave de token; DevTools → Network mostrando a URL do `/ws` sem
      `token`.

**Skill `frontend-design`:** **dispensada.** Esta story não altera nenhum pixel —
troca origem de dados e plumbing de sessão. A exceção de refatoração pura do
`CLAUDE.md` raiz (decisão D11) se aplica. O único efeito potencialmente visível
é o skeleton de bootstrap (ver seção 6), que já existe e é atenuado por
`useDelayedLoading`.

---

### US-009: Atualizar a documentação que a migração tornou falsa
**Description:** Como desenvolvedor futuro (ou como o agente Ralph, que lê
`ARCHITECTURE.md` como fonte de verdade a cada iteração), eu quero que a
documentação descreva o modelo de sessão real, para não implementar contra um
modelo que não existe mais.

**Depende de:** US-008

**Arquivos:** `docs/ARCHITECTURE.md`, `docs/BACKLOG.md`, `docs/DEPLOY.md`,
`api/CLAUDE.md`, `client/CLAUDE.md`

**Acceptance Criteria:**
- [ ] `docs/ARCHITECTURE.md` §2 deixa de descrever `useAuthStore` como
      "Gerencia JWT" e passa a descrever o modelo por cookie HttpOnly + refresh
      com rotação.
- [ ] A **decisão #3** de `docs/BACKLOG.md` ("Refresh token: Nao por enquanto")
      é marcada como **superada**, com a data e o ponteiro para este épico —
      não apagada, para que o histórico da decisão continue legível.
- [ ] `docs/DEPLOY.md` documenta as variáveis de ambiente novas
      (`JWT_ACCESS_TOKEN_TTL`, `AUTH_COOKIE_SECURE`, e as de TTL/graça do
      refresh), a remoção de `JWT_EXPIRATION_DAYS`, e o aviso de que **este
      deploy desloga todos os usuários uma vez**.
- [ ] `docs/DEPLOY.md` mantém a instrução da T1.2 de que o `access_log off;` do
      `location /ws/` **continua valendo** mesmo depois desta migração — o token
      saiu da URL, mas defesa em profundidade custa uma linha de nginx.
- [ ] `api/CLAUDE.md` ganha um parágrafo sobre `com.app.auth`: o fluxo de
      cookies, que `RefreshToken` guarda **apenas hash**, a janela de graça de
      30s, e que toda propriedade nova precisa ser espelhada em
      `src/test/resources/application.properties`.
- [ ] `client/CLAUDE.md` corrige a frase "API calls go through `src/lib/api.ts`,
      which attaches the JWT and redirects to `/login` on 401" — o cliente não
      anexa mais nada, e a 401 agora tenta refresh antes de redirecionar.
- [ ] Nenhum arquivo de documentação menciona `localStorage` como local da
      credencial.

## 4. Functional Requirements

1. Deve existir uma tabela `refresh_token` com `token_hash`, `expires_at`,
   `revoked_at`, `replaced_by_id`, `user_agent`, `ip` e `created_at`, criada
   pela migration `V5`.
2. O valor do refresh token deve ser persistido **apenas como SHA-256**; não
   pode haver caminho de código que recupere o valor original do banco.
3. O access token deve expirar em **15 minutos** (configurável) e carregar
   `iss`/`aud`, ambos **validados** na leitura.
4. `POST /api/auth/login` deve emitir `access_token` (HttpOnly, Secure
   condicional, SameSite=Strict, `Path=/`, 15 min) e `refresh_token` (mesmas
   flags, `Path=/api/auth/refresh`, 30 dias), e **não** devolver o token no
   corpo.
5. Deve existir `GET /api/auth/me` devolvendo `{user, couple}`, com 200 para
   sessão válida (inclusive sem casal, com `couple: null`) e 401 sem sessão.
6. `JwtAuthenticationFilter` deve autenticar exclusivamente pelo cookie
   `access_token`, sem fallback de header.
7. CSRF deve estar ativo com `CookieCsrfTokenRepository.withHttpOnlyFalse()`,
   isentando `login`, `register`, `health`, `refresh` e `logout`.
8. `POST /api/auth/refresh` deve rotacionar o par, revogar o anterior e gravar
   `replaced_by_id`.
9. Apresentar um refresh já revogado deve revogar a família inteira do usuário e
   retornar 401 — **exceto** dentro da janela de graça de 30s, quando devolve o
   par substituto sem revogar.
10. Refresh expirado deve retornar 401 **sem** revogar a família.
11. `POST /api/auth/logout` deve revogar o refresh no servidor e expirar ambos
    os cookies, de forma idempotente.
12. `JwtHandshakeInterceptor` deve autenticar pelo cookie `access_token` e
    continuar gravando `USER_ID_ATTRIBUTE` nos atributos da sessão.
13. Nenhum código do cliente pode ler ou gravar a credencial; `authToken.ts`
    deve ser deletado.
14. O interceptor de 401 do axios deve ser single-flight: N requisições
    concorrentes com 401 produzem **um** refresh.
15. A URL do handshake WebSocket não pode conter o parâmetro `token`.
16. `Secure` nos cookies deve ser condicionado a `app.auth.cookie.secure`, com
    default `true`.

## 5. Non-Goals (Out of Scope)

- **BFF / proxy de sessão** — descartado em D2. Front e API são same-origin em
  produção; `SameSite=Strict` já é eficaz e um hop extra não pagaria seu custo.
- **Fallback de header `Authorization` durante uma janela de transição** —
  descartado em D1: 8 usuários conhecidos, downtime tolerável, cutover direto.
- **Algoritmo assimétrico (RS256) e rotação de chave JWT** — a rotação do
  `JWT_SECRET` já aconteceu na T1.2 (D5); trocar HS256 por RS256 é task própria,
  sem gatilho hoje.
- **Rate limiting** em `login`/`register`/`refresh`/`join` — é a T5.1, Épico 5,
  que pode rodar em paralelo mas não faz parte da entrega deste épico.
- **Logger de auditoria estruturado com correlation id** — é a T5.5, Épico 5.
  Aqui a detecção de reuso emite apenas um `log.warn`.
- **Tela de "encerrar sessão em todos os dispositivos"** — o schema já suporta
  (`revokeFamily`), a UI é feature de produto.
- **CSRF no frame CONNECT do STOMP** — decisão E3: o `csrfChannelInterceptor`
  no-op é mantido.
- **Teto absoluto de sessão** (`family_expires_at` ou herança do `expires_at`
  original na rotação) — descartado em E5. O TTL é deslizante, sem limite de
  vida da família. Se um dia fizer sentido, é uma coluna nova e uma task
  própria.
- **Deletar `GET /api/couple/me`** — decisão E7: ele fica sem consumidor no
  cliente, mas os contratos são legitimamente diferentes (200 + `couple: null`
  vs. 404) e ele é o fixture autenticado de três testes de segurança.
- **Auto-login no registro** (`POST /api/auth/register` emitindo cookies e
  eliminando o `login()` subsequente do `useAuthStore`) — decisão E8. O fluxo
  de dois round-trips continua como está.
- **Job de limpeza de `refresh_token` expirados** — a tabela cresce devagar
  (uma linha por login/refresh, 8 usuários) e `expires_at` já tem índice.
  Vira task quando o volume justificar; o padrão a copiar já existe em
  `NotificationCleanupService`.
- **Limpar `celebratedMatchKeys` no logout** (`useMatchStore.ts:39`, o `Set` de
  escopo de módulo que nunca é limpo e faz o usuário B não ver a celebração
  depois do logout de A) — é a **T7.3**, Épico 7. O logout desta epic passa a
  chamar o servidor, mas não conserta esse vazamento.
- **Remover o e-mail do parceiro de `PartnerSummary`** — é a T5.6. O
  `GET /api/auth/me` criado aqui devolve o mesmo shape que o login já devolve
  hoje, e não pode antecipar essa mudança.
- **Unificar `AuthController.toResponse` e `CoupleController.toResponse`** — é a
  T6.3. Aqui a regra é apenas não criar uma terceira cópia.

## 6. Design Considerations

Este épico é backend + plumbing de sessão. **Nenhuma story altera pixels**, e a
skill `frontend-design` está dispensada por D11 (a exceção de refatoração pura
do `CLAUDE.md` raiz). Dois pontos de comportamento visível, ainda assim:

- **Bootstrap de usuário deslogado.** Hoje o app sabe instantaneamente que não
  há sessão (`Boolean(initialToken)` lido do `localStorage`). Depois da US-008
  ele precisa de um round-trip a `GET /api/auth/me` para descobrir. Isso
  significa que `loading` começa `true` para **todo mundo**, inclusive quem
  nunca logou. O `useDelayedLoading` (`delayMs=150`) já evita que o
  `AppShellSkeleton` pisque numa resposta rápida — é o mesmo mecanismo que o
  `client/CLAUDE.md` documenta para leituras. A verificação manual da US-008
  deve confirmar que abrir `/login` com sessão inexistente não mostra flash de
  skeleton em rede local.
- **Refresh transparente.** Quando o access token expira no meio do uso, o
  usuário **não pode ver nada** — nem toast, nem redirect, nem estado de erro.
  A requisição original é reexecutada e a UI segue. Só a falha do refresh
  produz efeito visível (redirect para `/login`), que é o comportamento que já
  existe hoje para qualquer 401.

## 7. Technical Considerations

- **Ordem obrigatória:** US-001 → US-002 → US-003 → US-004 → US-005 → US-006 →
  US-007 → US-008 → US-009. Não há stories paralelizáveis neste épico.
- **Todas as stories vão ao mesmo deploy.** Entre a US-003 e a US-004 a branch
  fica **sem login funcional** (o login emite cookies que ninguém lê ainda), e
  entre a US-007 e a US-008 o WebSocket fica sem autenticar (o backend lê cookie,
  o front ainda manda query string). `scripts/deploy.sh` já sobe front e back
  numa tacada só.
- **Todos os usuários são deslogados uma vez no deploy** — o formato da sessão
  muda por completo. Esperado e aceito (D1/D5).
- **Deploy com a `V5`:** usar `DB_URL` com a connection string **direta (5432)**
  ou o pooler do Supabase em modo **Session**, nunca o modo Transaction (6543).
  O risco de advisory lock do Flyway está documentado em `docs/FLYWAY.md` e vale
  para toda migration nova.
- **`api/src/test/resources/application.properties` SUBSTITUI o principal** nos
  testes de contexto completo — não mescla (`api/CLAUDE.md`). As seis
  propriedades novas deste épico (`app.jwt.access-token-ttl`, `app.jwt.issuer`,
  `app.jwt.audience`, `app.auth.cookie.secure`, `app.auth.refresh-token-ttl`,
  `app.auth.refresh-reuse-grace`) precisam ser declaradas lá **e** a antiga
  `app.jwt.expiration-days` removida de lá, senão `SessaoADoisApplicationTests`
  quebra em silêncio.
- **`FlywayMigrationTest` é o único teste que executa migrations de verdade.**
  Sem passar a `V5` por ele, a migration vai a produção sem nunca ter rodado —
  exatamente o problema que a T2.0 existia para resolver. Confirmar que ele está
  no lugar antes de começar a US-001.
- **`spring.jpa.open-in-view=false`** (Épico 2): todo método que lê ou escreve
  `RefreshToken` e depois toca associações precisa ser `@Transactional`. A
  revogação de família é um `@Modifying @Query`, que exige `@Transactional` no
  chamador por definição.
- **Ambiente de dev é cross-origin, mas same-site.** Front em
  `localhost:5173`, API em `localhost:8080`: a porta não conta para `SameSite`,
  então `Strict` funciona. O que quebra dev é o `Secure` — daí
  `AUTH_COOKIE_SECURE=false` no `.env` local. O CORS já tem
  `allowCredentials(true)`.
- **CORS em produção é irrelevante** (same-origin via nginx), mas a
  configuração continua existindo para o dev — por isso `X-XSRF-TOKEN` precisa
  entrar em `setAllowedHeaders`.
- **`X-Forwarded-For`** já é enviado pelo nginx em ambos os blocos (`/api/` e
  `/ws/`). Sem lê-lo, o `ip` gravado em `refresh_token` seria sempre o do proxy.
- **Sem codegen entre backend e frontend** (`client/CLAUDE.md`): a remoção do
  campo `token` de `LoginResponse` tem que ser espelhada à mão na interface
  `LoginResponse` de `useAuthStore.ts`, e o shape de `GET /api/auth/me` também.
- **`./mvnw test` roda no devcontainer** (JDK 21 + Maven 3.9.16, `api/CLAUDE.md`)
  — nunca declarar uma story de backend concluída sem executá-lo. `docker` e
  `psql` não existem: a validação de sintaxe Postgres-only da `V5` acontece no
  CI, contra o `postgres:17-alpine` do workflow.
- **Não há browser no sandbox** (`client/CLAUDE.md`): sem Chromium/Playwright e
  sem sudo para as libs de sistema. A US-008 fecha com `typecheck` + `lint` +
  revisão estática e aceite manual do dono do projeto.

## 8. Success Metrics

- Janela de validade de um access token vazado: de **7 dias para 15 minutos**.
- Credenciais legíveis por JavaScript: de **1** (`sessaoADois.token` no
  `localStorage`) para **0**.
- Sessões revogáveis pelo servidor: de **0** para **todas** (`POST
  /api/auth/logout` + `revokeFamily` na detecção de reuso).
- Requisições que carregam o token em texto claro numa URL: de **1 por
  handshake WebSocket** para **0**.
- Claims validados na leitura do JWT: de **1** (assinatura) para **4**
  (assinatura, `exp`, `iss`, `aud`).
- Refreshes disparados por N requisições concorrentes com 401: de **N**
  (hoje cada 401 derruba a sessão) para **1**.
- Endpoints protegidos contra CSRF: de **0** para **todos** os mutadores
  autenticados.
- `./mvnw test` verde nas 7 stories de backend; `bun run typecheck` +
  `bun run lint` verdes na story de frontend; sem regressão nos 38 arquivos de
  teste existentes.

## 9. Open Questions

**Nenhuma pendência em aberto.** As oito ambiguidades encontradas entre o
`POST-MVP-TASK.md` e o código atual foram resolvidas com o dono do projeto em
2026-08-03 e estão registradas como E1–E8 na seção 1. O quadro abaixo existe
para que não sejam reabertas sem motivo novo.

| # | Questão | Decisão | Onde vive |
|---|---------|---------|-----------|
| E1 | `Path` do `access_token` vs. handshake em `/ws` | `Path=/` no access token; `Path=/api/auth/refresh` no refresh | US-003, US-007 |
| E2 | Como o bootstrap descobre que há sessão | Novo `GET /api/auth/me` (200/401, `couple: null` quando não há casal) | US-003, US-008 |
| E3 | CSRF no frame CONNECT do STOMP | Manter o `csrfChannelInterceptor` no-op; atualizar o javadoc registrando a decisão | US-005 |
| E4 | Janela de tolerância para refresh concorrente | 30 segundos, configurável por `app.auth.refresh-reuse-grace` | US-006 |
| E5 | TTL do refresh na rotação: deslizante ou herdado | **Deslizante** — 30 dias frescos a cada rotação, sem teto absoluto | US-001, US-006, seção 5 |
| E6 | CSRF em `/api/auth/refresh` e `/api/auth/logout` | **Ambos isentos** — `SameSite=Strict` + `Path` restrito são a defesa; o pior caso é logout forçado | US-005 |
| E7 | `GET /api/couple/me` fica órfão no cliente | **Manter** — contrato distinto (404 vs. `couple: null`) e fixture de 3 testes de segurança | seção 5 |
| E8 | Registro emitir cookies direto (auto-login) | **Não neste épico** — o fluxo de dois round-trips segue inalterado | seção 5 |

### Pendências de verificação

| # | Questão | Resolução |
|---|---------|-----------|
| V1 | Como verificar a US-008 sem browser no sandbox | **Aceite manual do dono antes do merge/deploy**, mesmo arranjo da US-004 do Épico 3. O autor entrega com `typecheck` + `lint` + revisão estática; o dono roda `bun run dev`, confere login/navegação/logout, o Storage do DevTools sem chave de token, e a URL do `/ws` sem `token`. |
| V2 | O SockJS realmente envia o cookie no handshake em dev (cross-origin)? | A conferir na verificação manual da US-008. Portas diferentes são cross-origin mas **same-site**, então `SameSite=Strict` não bloqueia; o que precisa estar certo é `AUTH_COOKIE_SECURE=false` no `.env` de dev. Se falhar, a alternativa é um proxy `/api` + `/ws` no `vite.config.ts` (que tornaria o dev same-origin como a produção) — mas só se necessário, não preventivamente. |

### O que continua fora de discussão

As decisões vinculantes D1–D12 do `POST-MVP-TASK.md` (2026-08-02) seguem
válidas, em especial **D1** (base de ~8 usuários conhecidos, downtime tolerável
⇒ cutover direto, sem fallback de header nem janela de transição), **D2**
(cookie HttpOnly, não BFF), **D5** (`JWT_SECRET` já rotacionado na T1.2) e
**D7** (15 min / 30 dias).
