# PRD: Épico 1 — Contenção de Segurança (+ T8.1 antecipada)

**Origem:** `POST-MVP-TASK.md` — Épico 1 (T1.1 a T1.4) e T8.1 (do Épico 8, antecipada
por decisão explícita do backlog: *"Ordem de execução: Épicos 1 → 2 → ... com a T8.1
(CI) antecipada para junto do Épico 1"*).

**Criado em:** 2026-08-02

---

## 1. Introdução / Visão Geral

Este PRD cobre a primeira leva de correções do backlog pós-MVP: eliminar os riscos de
exposição de credencial e de acesso indevido encontrados na auditoria de arquitetura, e
fechar as lacunas do CI que permitiram que esses problemas chegassem a produção sem
detecção.

Quatro problemas concretos são resolvidos aqui:

1. **`JWT_SECRET` com default versionado no repositório.** Se a variável de ambiente não
   for injetada em produção, a aplicação sobe assinando tokens com uma chave pública.
   Qualquer pessoa com acesso ao repo forja um token válido para qualquer `userId`.
2. **JWT em texto claro no disco da VPS.** Todo handshake WebSocket passa o token na query
   string (`/ws?token=<JWT>`) e o nginx grava a URL completa no `access.log`, com rotação
   e backup. É o achado mais grave da auditoria.
3. **Broker STOMP sem autorização por mensagem.** Qualquer usuário autenticado pode
   assinar `/topic/couple/{coupleId}/notifications` de outro casal e ler títulos
   assistidos, nomes de parceiro e eventos de match.
4. **CI cego.** O workflow só roda `./mvnw test` em PRs para `dev`. As classes de
   segurança (`JwtService`, `SecurityConfig`, `JwtHandshakeInterceptor`) não têm nenhum
   teste, o `typecheck`/`lint` do frontend nunca rodam, e merges para `main` (produção)
   não executam teste algum.

**Nenhuma mudança de contrato entre `api/` e `client/` acontece neste PRD.** Tudo é
infra, configuração e teste. O frontend não é tocado (exceto pelo CI passar a validá-lo).

---

## 2. Objetivos

- A aplicação **não sobe** em nenhum ambiente com um `JWT_SECRET` ausente, fraco ou igual
  ao default de desenvolvimento.
- Zero JWTs novos gravados no `access.log` do nginx, e os logs históricos purgados.
- O `JWT_SECRET` de produção rotacionado, invalidando tudo que já vazou para o log
  (decisão D5 — logout forçado de todos é aceitável dada a base de ~8 usuários, D1).
- Um usuário só consegue assinar tópicos STOMP do **seu próprio** casal; publicação
  direta de cliente para `/topic/**` é bloqueada.
- Os 4 headers de segurança HTTP (`HSTS`, `X-Content-Type-Options`, `Referrer-Policy`,
  `Content-Security-Policy`) presentes em toda resposta de produção, sem quebrar pôsteres,
  fontes, chamadas a `/api` ou o WebSocket.
- CI que roda em PRs para `dev` **e** `main`, valida o frontend (`typecheck`, `lint`,
  `build`) e falha se a cobertura do backend cair abaixo do patamar atual.
- As 4 classes de segurança sem teste passam a ter teste.

---

## 3. User Stories

Doze stories. Cada uma cabe em uma sessão focada de implementação.

**Dependências (as únicas restrições rígidas de ordem):**

- US-007 depende de US-006.
- US-010 depende de US-008 e US-009.
- US-012 depende de US-003, US-004, US-005 e US-007 (o limiar de cobertura só faz sentido
  medido *depois* dos testes novos).

Todo o resto pode ser feito em qualquer ordem.

---

### US-001: Fail-fast do `JWT_SECRET`

**Origem:** T1.1

**Description:** Como operador do sistema, quero que a aplicação se recuse a subir com um
`JWT_SECRET` ausente, curto ou igual ao default de desenvolvimento, para que uma variável
de ambiente perdida no deploy vire um crash visível em vez de uma aplicação assinando
tokens com uma chave pública.

**Contexto de código:**
- `api/src/main/java/com/app/security/JwtService.java:24-29` (construtor, hoje sem validação)
- `api/src/main/resources/application.properties:23` — `app.jwt.secret=${JWT_SECRET:dev-only-secret-please-override-in-prod-32bytes+}`
- Padrão a replicar: `api/src/main/java/com/app/media/TmdbConfig.java:31-34` (fail-fast já existente para a chave do TMDB)

**Acceptance Criteria:**
- [ ] O construtor de `JwtService` lança `IllegalStateException` quando o segredo é nulo ou em branco.
- [ ] Lança `IllegalStateException` quando o segredo é exatamente igual à string `dev-only-secret-please-override-in-prod-32bytes+`.
- [ ] Lança `IllegalStateException` quando o segredo tem menos de 32 bytes em UTF-8 (contar **bytes**, não caracteres — um segredo com acentos pode ter menos caracteres que bytes).
- [ ] A mensagem da exceção é em pt-BR e nomeia explicitamente a variável de ambiente `JWT_SECRET`.
- [ ] Um segredo válido de ≥32 bytes constrói o bean normalmente.
- [ ] `api/src/main/resources/application.properties` passa a declarar `app.jwt.secret=${JWT_SECRET:}` (sem valor default).
- [ ] `api/src/test/resources/application.properties` continua declarando um segredo fixo válido — esse arquivo **substitui** o de `main` inteiramente para `@SpringBootTest` (ver `api/CLAUDE.md`), então sem isso todo teste de contexto completo quebra.
- [ ] Existe um teste unitário cobrindo os 4 cenários (nulo/branco, igual ao default, curto, válido).
- [ ] `./mvnw test` passa.

**Atenção:** o segredo de teste atual é `test-only-secret-please-override-in-prod-32bytes+`
e o do CI (`.github/workflows/ci.yml:46`) é `ci-only-secret-please-override-in-prod-32bytes+`.
Nenhum dos dois é igual ao default de dev (`dev-only-...`), então a nova validação não os
rejeita. Verificar isso na prática antes de considerar a story pronta.

---

### US-002: Silenciar a auto-config de usuário in-memory do Spring Security

**Origem:** T1.1, seção "Extra"

**Description:** Como desenvolvedor, quero eliminar o `UserDetailsService` in-memory que o
Spring Boot cria automaticamente, para que o log de produção pare de imprimir uma senha
gerada a cada boot e para que reativar `httpBasic` no futuro não abra uma porta acidental.

**Contexto:** o log de `SessaoADoisApplicationTests` mostra
`Using generated security password: ...` + `Global AuthenticationManager configured with
UserDetailsService bean with name inMemoryUserDetailsManager`. O app é stateless com JWT e
não define nenhum `UserDetailsService`, então o Spring cria um usuário in-memory com senha
aleatória a cada inicialização. **Não é explorável hoje** — `formLogin` e `httpBasic` estão
desabilitados em `SecurityConfig.java:48-49` — mas é ruído no log e vira risco real se
alguém reativar `httpBasic`.

**Acceptance Criteria:**
- [ ] `UserDetailsServiceAutoConfiguration` está excluída **ou** existe um `AuthenticationManager`/`UserDetailsService` vazio declarado que a desativa.
- [ ] O log de inicialização (`./mvnw test`, qualquer `@SpringBootTest` de contexto completo) não contém mais `Using generated security password`.
- [ ] Nenhum endpoint muda de comportamento: as rotas públicas continuam públicas e as privadas continuam retornando 401 sem token.
- [ ] `./mvnw test` passa.

---

### US-003: `JwtServiceTest`

**Origem:** T8.1, item 1

**Description:** Como desenvolvedor, quero testes para o núcleo de assinatura e parse do
token, para que uma regressão em `JwtService` seja pega pelo CI em vez de em produção.

**Acceptance Criteria:**
- [ ] Existe `api/src/test/java/com/app/security/JwtServiceTest.java`.
- [ ] Token gerado por `generateToken(uuid)` e lido por `parseSubject` devolve o mesmo UUID.
- [ ] Token **expirado** faz `parseSubject` lançar (`ExpiredJwtException`).
- [ ] Token assinado com **outra chave** faz `parseSubject` lançar (assinatura inválida).
- [ ] Token com `subject` que não é um UUID faz `parseSubject` lançar `IllegalArgumentException` (não um erro genérico e não um 500 silencioso).
- [ ] Token malformado / string arbitrária faz `parseSubject` lançar, sem `NullPointerException`.
- [ ] Os testes de validação do segredo curto/ausente **não** são duplicados aqui — eles vivem na US-001. Esta story cobre comportamento do token, não do construtor.
- [ ] `./mvnw test` passa.

---

### US-004: `SecurityConfigTest` e teste do `HealthController`

**Origem:** T8.1, item 2 + linha `HealthController` da tabela

**Description:** Como desenvolvedor, quero um teste que afirme exatamente quais rotas são
públicas e quais exigem autenticação, para que um `permitAll()` acidental não passe pelo CI.

**Contexto:** `SecurityConfig.java:41-45` — hoje são públicas: `POST /api/auth/register`,
`POST /api/auth/login`, `GET /api/health` e `/ws/**`. Todo o resto é `authenticated()`.

**Acceptance Criteria:**
- [ ] Existe `api/src/test/java/com/app/security/SecurityConfigTest.java` com `@SpringBootTest` + `MockMvc`.
- [ ] Afirma **200** (ou pelo menos "não 401") para as rotas públicas: `POST /api/auth/login`, `POST /api/auth/register`, `GET /api/health`.
- [ ] Afirma **401** sem token para pelo menos uma rota de cada controller privado: `/api/tracking`, `/api/match/pending`, `/api/media/search`, `/api/couple/me`, `/api/notifications`.
- [ ] Afirma que `GET /api/health` responde 200 **sem** nenhum header `Authorization`.
- [ ] O teste falha se alguém adicionar um `permitAll()` novo a uma das rotas privadas listadas.
- [ ] `./mvnw test` passa.

---

### US-005: `JwtHandshakeInterceptorTest`

**Origem:** T8.1, item 3

**Description:** Como desenvolvedor, quero testes para o único portão de autenticação do
WebSocket, para que uma mudança no `JwtHandshakeInterceptor` não abra o handshake sem que
ninguém perceba.

**Contexto:** `api/src/main/java/com/app/websocket/JwtHandshakeInterceptor.java` — lê o
token de `?token=` na query string (linhas 67-80), valida via `JwtService.parseSubject` e
guarda o `userId` em `attributes` sob `USER_ID_ATTRIBUTE` (linha 50).

**Acceptance Criteria:**
- [ ] Existe `api/src/test/java/com/app/websocket/JwtHandshakeInterceptorTest.java`.
- [ ] Handshake **sem** `?token=` retorna `false` e seta 401 na resposta.
- [ ] Handshake com `?token=` vazio ou em branco retorna `false` e seta 401.
- [ ] Handshake com token inválido (assinatura errada / expirado) retorna `false` e seta 401.
- [ ] Handshake com token válido retorna `true` **e** grava o `userId` correto em `attributes` sob a chave `JwtHandshakeInterceptor.USER_ID_ATTRIBUTE` — esta asserção é pré-requisito da US-006, que lê exatamente esse atributo.
- [ ] `./mvnw test` passa.

---

### US-006: `AuthorizationManager` de destino do casal

**Origem:** T1.3 (implementação)

**Description:** Como usuário, quero que ninguém fora do meu casal consiga assinar os
tópicos do meu casal, para que meus títulos assistidos, o nome do meu parceiro e meus
eventos de match não vazem para outra conta.

**Contexto do problema:** `SecurityConfig.java:44` libera `/ws/**` (correto — a
autenticação acontece no handshake), mas `WebSocketConfig.java:41` só faz
`registry.enableSimpleBroker("/topic")`, sem nenhuma autorização por mensagem. O
`coupleId` é um UUID, difícil de adivinhar, mas circula no cliente de ambos os parceiros —
não é segredo.

**O que construir:** um componente em `com.app.websocket` que, dado um `Message<?>`,
extrai o `coupleId` do destino e o `userId` das *session attributes* (colocado lá pelo
`JwtHandshakeInterceptor`), e decide se autoriza — comparando com
`CoupleService.getCurrentCouple(userId)`, que retorna `Optional<Couple>`.

**Acceptance Criteria:**
- [ ] Existe um componente novo em `api/src/main/java/com/app/websocket/` responsável apenas por essa decisão.
- [ ] Destino `/topic/couple/{id}/match` com `id` igual ao casal do usuário da sessão → autorizado.
- [ ] Destino `/topic/couple/{id}/notifications` com `id` igual ao casal do usuário → autorizado.
- [ ] Destino com `id` de **outro** casal → negado.
- [ ] `id` malformado (não parseável como UUID) → **negado**, sem lançar exceção que vire 500.
- [ ] Usuário sem casal (`getCurrentCouple` devolve `Optional.empty()`) → negado.
- [ ] Sessão sem `userId` nos atributos → negado.
- [ ] Existe teste unitário cobrindo os 6 casos acima.
- [ ] `./mvnw test` passa.

---

### US-007: Ligar a autorização de mensagens no broker STOMP

**Origem:** T1.3 (wiring + teste de integração)
**Depende de:** US-006

**Description:** Como usuário, quero que a regra da US-006 seja efetivamente aplicada a
toda mensagem STOMP de entrada, e que publicação direta de cliente para `/topic/**` seja
bloqueada, para que ninguém consiga forjar um evento de match no tópico de outro casal.

**Postura de autorização: deny-by-default.** Só `SUBSCRIBE` em
`/topic/couple/{id}/**` com o `id` do próprio casal é permitido. Todo o resto — incluindo
`SEND` para qualquer destino e qualquer mensagem para `/app/**` — é negado. Hoje isso não
quebra nada: o projeto não tem **nenhum** `@MessageMapping`, e o prefixo `/app`
(`WebSocketConfig.java:42`) está registrado mas nunca é usado. O efeito prático é que o
primeiro `@MessageMapping` que alguém adicionar vai precisar declarar sua autorização
explicitamente, em vez de nascer aberto.

**Dependência nova.** `spring-security-messaging` **não está no classpath** —
`spring-boot-starter-security` não o traz (verificado: o `~/.m2` do projeto tem
`spring-security-core`, `-config`, `-web`, `-crypto` e `-test`, e nada mais). Sem ele,
`@EnableWebSocketSecurity` não funciona. Adicionar
`org.springframework.security:spring-security-messaging` ao `api/pom.xml`, sem `<version>`
— o `spring-boot-starter-parent` 4.1.0 já gerencia (resolve para 7.1.0).

**Acceptance Criteria:**
- [ ] `org.springframework.security:spring-security-messaging` foi adicionado ao `api/pom.xml`, sem `<version>` explícita (versão gerenciada pelo parent).
- [ ] `@EnableWebSocketSecurity` está habilitado e o `AuthorizationManager` da US-006 registrado para mensagens de entrada, via `MessageMatcherDelegatingAuthorizationManager.builder()`.
- [ ] Existe um bean `ChannelInterceptor` **com o nome exato `csrfChannelInterceptor`** que é um no-op, sobrescrevendo o `XorCsrfChannelInterceptor` default. O bean carrega um comentário apontando para a T4.3, que é quando o CSRF de verdade entra. Ver a seção 7 para o porquê.
- [ ] `SUBSCRIBE` em `/topic/couple/{id}/match` e `/topic/couple/{id}/notifications` com o `id` do próprio casal é **aceito**.
- [ ] `SUBSCRIBE` nos mesmos destinos com `id` de outro casal é **rejeitado**.
- [ ] `SUBSCRIBE` com `id` malformado é rejeitado sem 500.
- [ ] `SEND` direto de cliente para **qualquer** destino é rejeitado — não só `/topic/**`.
- [ ] Qualquer mensagem de cliente para `/app/**` é rejeitada.
- [ ] Os pushes do servidor continuam funcionando: `MatchService.java:117` (`/topic/couple/{id}/match`) e `NotificationService.java:147` (`/topic/couple/{id}/notifications`) entregam normalmente. A autorização vale para mensagens **de entrada**, não para as que o broker emite.
- [ ] O handshake e o frame `CONNECT` do cliente atual continuam funcionando, sem nenhuma mudança em `client/src/stores/useMatchStore.ts`.
- [ ] Existe teste de integração cobrindo aceite e rejeição.
- [ ] `./mvnw test` passa.

**Fora do escopo:** trocar o transporte do token no handshake. O `JwtHandshakeInterceptor`
continua lendo da query string — isso muda na T4.5. Implementar o token CSRF real no frame
`CONNECT` também fica fora: é T4.3.

---

### US-008: Parar de gravar JWT no `access.log` do nginx

**Origem:** T1.2 (parte de repositório)

**Description:** Como operador, quero que o handshake WebSocket pare de gravar o JWT em
disco na VPS, para fechar o vazamento contínuo de credencial válida para
`/var/log/nginx/access.log`.

**Contexto:** `client/src/stores/useMatchStore.ts:62` monta a URL como
`${VITE_API_URL}/ws?token=<JWT>`. O bloco `location /ws/` de
`deploy/nginx/sessaoadois.luisgosampaio.com.conf:52-63` não desabilita nem filtra o
`access_log`.

**Acceptance Criteria:**
- [ ] `location /ws/` no conf tem `access_log off;`. (A alternativa de mascarar o `token` via `map` sobre `$request_uri` é aceitável, mas `access_log off` é a escolha desta story — o handshake não tem valor de observabilidade que justifique o risco.)
- [ ] `location /api/` e `location /` mantêm o `access_log` **intacto** — só o `/ws/` muda.
- [ ] `docs/DEPLOY.md` ganha uma seção de segurança operacional documentando: (a) que o conf precisa ser reinstalado em `/etc/nginx/sites-available/` e o nginx recarregado; (b) que os logs **existentes** contêm JWTs válidos e devem ser purgados uma vez, incluindo os arquivos rotacionados (`access.log.1`, `access.log.*.gz`); (c) que com `JWT_EXPIRATION_DAYS=7`, tokens em logs de até 7 dias atrás ainda são válidos; (d) o procedimento de rotação do `JWT_SECRET` (`openssl rand -base64 48`) e a atualização do `.env` da VPS.
- [ ] A sintaxe do conf alterado é válida (`nginx -t` — executado na VPS, ver US-010).

**Fora do escopo:** remover o `?token=` da URL. Isso depende do cookie HttpOnly e é feito
na T4.5. Esta story é a mitigação imediata enquanto a query string ainda existe.

---

### US-009: Headers de segurança HTTP e CSP

**Origem:** T1.4 (parte de repositório)

**Description:** Como usuário, quero que o navegador aplique HSTS, bloqueie sniffing de
MIME type, restrinja o `Referer` e imponha uma Content-Security-Policy, para reduzir o
impacto de uma dependência de terceiros comprometida.

**Contexto:** o conf do nginx não define nenhum desses headers. O Certbot adiciona o
redirect 80→443 mas **não** adiciona HSTS. O `client/src` não usa `dangerouslySetInnerHTML`,
`eval` nem `innerHTML` (verificado na auditoria), então a superfície de XSS própria é
pequena — o risco real é dependência comprometida. A CSP é também o que dá sentido à
migração para cookie HttpOnly do Épico 4: sem ela, um atacante com execução de script
cavalga a sessão dentro da página mesmo sem conseguir ler o token.

**CSP a aplicar** (corrigida em relação ao rascunho da T1.4 — ver justificativa abaixo):

```nginx
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
add_header X-Content-Type-Options "nosniff" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Content-Security-Policy "default-src 'self'; img-src 'self' https://image.tmdb.org data:; style-src 'self' 'unsafe-inline'; font-src 'self' data:; connect-src 'self' wss://sessaoadois.luisgosampaio.com; frame-ancestors 'none'; base-uri 'self'" always;
```

**Duas correções em relação ao texto da T1.4:**

1. **Google Fonts foi removido.** A T1.4 libera `https://fonts.googleapis.com` e
   `https://fonts.gstatic.com` "enquanto o app carregar fontes por CDN". Ele **não carrega**:
   `client/src/index.css:4-11` importa `@fontsource-variable/instrument-sans`,
   `@fontsource/bricolage-grotesque` e `@fontsource/dm-sans` — pacotes npm que o Vite
   empacota como assets do próprio domínio. `client/index.html` não tem nenhuma `<link>`
   para o Google. Os dois hosts do rascunho seriam permissão morta. (O protótipo em
   `docs/design/claude-design-project/` usa Google Fonts, mas ele não é servido em produção.)
2. **`wss://` foi declarado explicitamente em `connect-src`.** A CSP3 diz que `'self'`
   casa com `ws:`/`wss:` do mesmo host, mas o comportamento variou entre browsers e
   versões, e o SockJS abre `wss://sessaoadois.luisgosampaio.com/ws/...`. Declarar
   explicitamente custa nada e elimina a chance de a CSP derrubar o WebSocket.

`'unsafe-inline'` em `style-src` **permanece necessário**: os primitivos `@base-ui/react`
(Popover, Select, Slider, Dialog) posicionam via atributo `style` inline em runtime.

**Acceptance Criteria:**
- [ ] Os 4 headers estão no conf do nginx, no bloco `server` da porta 443, todos com o flag `always`.
- [ ] A CSP é exatamente a listada acima (sem os hosts do Google Fonts, com `wss://` explícito).
- [ ] `docs/DEPLOY.md` explica que esses headers vivem no bloco gerado pelo Certbot e **precisam ser reaplicados** se o certificado for reemitido do zero (`certbot --nginx` reescreve o bloco).
- [ ] `docs/DEPLOY.md` registra por que Google Fonts não está na CSP (fontes self-hosted via `@fontsource`), para que ninguém "conserte" isso adicionando de volta.
- [ ] A verificação em produção (`curl -I`, console sem violação de CSP) é feita na US-010.

**Fora do escopo:** eliminar o `'unsafe-inline'` do `style-src`. Exige mudar como o
`@base-ui/react` aplica posicionamento — melhoria futura, não bloqueia esta story.

---

### US-010: [OPERACIONAL] Aplicar as mudanças de segurança na VPS

**Origem:** T1.2 + T1.4 (execução)
**Depende de:** US-008 e US-009
**Executor:** manual (não automatizável a partir do repositório)

**Description:** Como operador, quero aplicar na VPS as mudanças de nginx e a rotação do
segredo, e confirmar que nada quebrou, para que as correções das US-008 e US-009 saiam do
repositório e passem a valer em produção.

**Acceptance Criteria (checklist manual):**
- [ ] O conf atualizado foi copiado para `/etc/nginx/sites-available/sessaoadois.luisgosampaio.com` na VPS.
- [ ] Os 4 headers da US-009 foram inseridos no bloco `server` da **porta 443** (o gerado pelo Certbot), não no bloco 80.
- [ ] `sudo nginx -t` passa.
- [ ] `sudo systemctl reload nginx` executado sem erro.
- [ ] `curl -I https://sessaoadois.luisgosampaio.com` retorna os 4 headers.
- [ ] Um handshake de WebSocket novo **não** produz linha em `/var/log/nginx/access.log` contendo `token=`.
- [ ] `/api/` e `/` continuam gravando access log normalmente.
- [ ] Os logs históricos foram purgados — incluindo os rotacionados (`access.log.1`, `access.log.*.gz`).
- [ ] Um `JWT_SECRET` novo foi gerado (`openssl rand -base64 48`) e o `.env` da VPS atualizado.
- [ ] O container da API foi reiniciado com o segredo novo e sobe sem erro (a validação da US-001 está ativa — um `.env` mal preenchido agora impede o boot, que é o comportamento desejado).
- [ ] Todos os usuários foram deslogados uma vez e conseguem entrar de novo. (Esperado e aceito — decisões D1 e D5.)
- [ ] O app carrega sem nenhuma violação de CSP no console do browser: pôsteres do TMDB aparecem, fontes carregam, chamadas a `/api` respondem e o WebSocket conecta (testar recebendo um evento de match ou notificação real).

---

### US-011: CI — job de frontend e gatilho para `main`

**Origem:** T8.1, itens 4 e 5

**Description:** Como desenvolvedor, quero que o CI valide o frontend e rode também em PRs
para `main`, para que um erro de typecheck não chegue a produção e para que merges na
branch de produção não passem sem teste nenhum.

**Contexto:** `.github/workflows/ci.yml` dispara só em `pull_request → dev` e roda apenas
`./mvnw -B test` em `api/`. Os scripts `typecheck`, `lint` e `build` existem em
`client/package.json:9-12` mas nunca rodam no CI.

**Acceptance Criteria:**
- [ ] Existe um job `frontend` no workflow que roda `bun install`, `bun run typecheck`, `bun run lint` e `bun run build` em `client/`.
- [ ] O job usa `oven-sh/setup-bun` (o projeto usa Bun, não npm — ver `docs/ARCHITECTURE.md` §2).
- [ ] O job **falha** se qualquer um dos quatro comandos falhar.
- [ ] Os branches-alvo do workflow incluem `dev` **e** `main`.
- [ ] O job de backend (`test`) continua funcionando exatamente como antes, com o serviço `postgres:17-alpine` e as env vars atuais.
- [ ] Verificado na prática: introduzir um erro de tipo deliberado em `client/src` faz o job `frontend` falhar. Reverter depois.

**Nota de ambiente:** `client/CLAUDE.md` documenta que o sandbox local às vezes falha em
`vite build` por falta dos bindings nativos arm64. Isso é um problema **do sandbox**, não
do CI (que roda x86_64 no GitHub). Se o `bun run build` falhar localmente com
`Cannot find module './<name>.linux-arm64-gnu.node'`, seguir o procedimento documentado lá
antes de concluir que o workflow está errado.

---

### US-012: `jacoco:check` com limiar derivado da cobertura atual

**Origem:** T8.1, item 6
**Depende de:** US-003, US-004, US-005, US-007

**Description:** Como desenvolvedor, quero um limiar mínimo de cobertura no build, para que
a cobertura só possa subir, nunca cair silenciosamente.

**Contexto:** o `jacoco-maven-plugin` já está declarado em `api/pom.xml:125-127` (v0.8.13)
com as execuções `prepare-agent` e `report`, mas **sem** o goal `check` — o relatório é
gerado e ignorado.

**Forma da regra (decidida):** uma única regra — `LINE` coverage, escopo `BUNDLE`,
quebrando o build (`haltOnFailure` no default `true`). Um número só, fácil de subir depois.
Sem `BRANCH` e sem escopo `CLASS` por enquanto: os dois travariam o build por razões que
não são "a cobertura caiu", que é a única coisa que esta story quer detectar.

**Acceptance Criteria:**
- [ ] `./mvnw test` foi executado **depois** das stories US-003 a US-007 e o relatório do JaCoCo (`api/target/site/jacoco/index.html`) foi lido para obter a cobertura real de linha.
- [ ] Uma execução `check` foi adicionada ao plugin com **uma** regra: `element=BUNDLE`, `counter=LINE`, `value=COVEREDRATIO`, `minimum` fixado **logo abaixo** da cobertura medida (arredondar para baixo, com folga de alguns pontos para não travar por variação de arredondamento).
- [ ] `haltOnFailure` fica no default (`true`) — o build **falha** quando a regra é violada.
- [ ] O número escolhido e a cobertura medida que o originou estão registrados num comentário no `pom.xml` — quem subir o limiar depois precisa saber de onde veio.
- [ ] `./mvnw test` (ou `verify`, conforme a fase em que o `check` for vinculado) passa com o limiar novo.
- [ ] Verificado na prática: subir deliberadamente o `minimum` para um valor acima da cobertura atual faz o build **falhar**. Reverter para o valor correto depois.
- [ ] Se o `check` for vinculado à fase `verify`, o comando do CI é ajustado para executá-la — caso contrário o limiar nunca roda.

---

## 4. Requisitos Funcionais

**Fail-fast e configuração**
- **FR-1:** `JwtService` deve lançar `IllegalStateException` no construtor quando `app.jwt.secret` for nulo, em branco, igual a `dev-only-secret-please-override-in-prod-32bytes+`, ou menor que 32 bytes em UTF-8.
- **FR-2:** A mensagem da exceção do FR-1 deve ser em pt-BR e nomear a variável de ambiente `JWT_SECRET`.
- **FR-3:** `api/src/main/resources/application.properties` não deve conter nenhum valor default para `app.jwt.secret`.
- **FR-4:** `api/src/test/resources/application.properties` deve declarar um `app.jwt.secret` válido de ≥32 bytes e diferente do default de dev.
- **FR-5:** O sistema não deve mais registrar um `UserDetailsService` in-memory automático; o log de boot não deve conter `Using generated security password`.

**Autorização STOMP**
- **FR-6:** O sistema deve autorizar cada mensagem STOMP de entrada com destino `/topic/couple/{coupleId}/**`, aceitando apenas quando `coupleId` for o casal do usuário da sessão.
- **FR-7:** O sistema deve rejeitar `SUBSCRIBE` cujo `coupleId` não seja um UUID válido, sem produzir erro 500.
- **FR-8:** O sistema deve adotar postura **deny-by-default** no canal de entrada: apenas o `SUBSCRIBE` descrito no FR-6 é permitido. Toda mensagem `SEND`, e toda mensagem para `/app/**`, deve ser rejeitada.
- **FR-9:** As mensagens emitidas pelo servidor via `SimpMessagingTemplate.convertAndSend` devem continuar sendo entregues sem passar pela autorização de entrada.
- **FR-10:** O `userId` usado na decisão do FR-6 deve vir das session attributes populadas por `JwtHandshakeInterceptor` (chave `USER_ID_ATTRIBUTE`), não de nenhum dado enviado pelo cliente na mensagem.
- **FR-11:** O `api/pom.xml` deve declarar `org.springframework.security:spring-security-messaging`, sem `<version>` explícita.
- **FR-12:** O sistema deve declarar um bean `ChannelInterceptor` de nome `csrfChannelInterceptor` que não faz nada, sobrescrevendo o `XorCsrfChannelInterceptor` que o `@EnableWebSocketSecurity` instala por default, de modo que o frame `CONNECT` do cliente atual continue sendo aceito sem token CSRF.

**Nginx**
- **FR-13:** O bloco `location /ws/` deve ter `access_log off;`.
- **FR-14:** Os blocos `location /api/` e `location /` devem manter o access log habilitado.
- **FR-15:** O bloco `server` da porta 443 deve emitir, com o flag `always`: `Strict-Transport-Security: max-age=31536000; includeSubDomains`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin` e a `Content-Security-Policy` especificada na US-009.
- **FR-16:** A `Content-Security-Policy` deve liberar `https://image.tmdb.org` e `data:` em `img-src`, `'unsafe-inline'` em `style-src`, `wss://sessaoadois.luisgosampaio.com` em `connect-src`, e definir `frame-ancestors 'none'` e `base-uri 'self'`.
- **FR-17:** A `Content-Security-Policy` **não** deve liberar `fonts.googleapis.com` nem `fonts.gstatic.com`.

**Documentação**
- **FR-18:** `docs/DEPLOY.md` deve conter um procedimento explícito de purga dos logs do nginx (incluindo rotacionados) e de rotação do `JWT_SECRET`.
- **FR-19:** `docs/DEPLOY.md` deve documentar que os headers de segurança vivem no bloco gerado pelo Certbot e precisam ser reaplicados se o certificado for reemitido do zero.

**CI e testes**
- **FR-20:** Devem existir `JwtServiceTest`, `SecurityConfigTest`, `JwtHandshakeInterceptorTest` e cobertura de `HealthController`.
- **FR-21:** O workflow de CI deve disparar em pull requests para `dev` e para `main`.
- **FR-22:** O workflow deve executar `bun install`, `bun run typecheck`, `bun run lint` e `bun run build` em `client/`, falhando se qualquer um falhar.
- **FR-23:** O `jacoco-maven-plugin` deve ter o goal `check` configurado com uma regra `BUNDLE`/`LINE`/`COVEREDRATIO`, `haltOnFailure=true`, e `minimum` derivado da cobertura medida após os testes novos.

---

## 5. Não-Objetivos (Fora de Escopo)

Explicitamente **fora** deste PRD — cada item tem dono em outra task do backlog:

- **Rotação de chave programática, algoritmo assimétrico (RS256), claims `iss`/`aud`** — T4.1. A rotação feita na US-010 é uma operação manual pontual, não um mecanismo.
- **Remover o `?token=` da URL do WebSocket** — T4.5. Depende do cookie HttpOnly. A US-008 é a mitigação enquanto a query string existe.
- **Cookie HttpOnly, refresh token, CSRF do HTTP, logout server-side** — Épico 4 inteiro.
- **Rate limiting em `/api/auth/login`** — T5.1.
- **Migrations validadas por teste no CI** — T2.0. Este PRD não toca em Flyway nem no schema.
- **Container não-root e `HEALTHCHECK`** — T8.2.
- **Segredos em `/tmp` no `deploy.sh`** — T8.3.
- **Usuário de deploy não-root na VPS** — decisão D10: manter `root`.
- **Vitest / Testing Library no `client/`** — a T8.1 não pede, e é escopo próprio. Este PRD só faz o CI rodar `typecheck`, `lint` e `build`.
- **Eliminar `'unsafe-inline'` do `style-src`** — exige mudar como o `@base-ui/react` aplica posicionamento.
- **Qualquer mudança visual ou de componente em `client/`** — nenhuma story aqui toca em UI. A skill `frontend-design` não se aplica a este PRD.

---

## 6. Considerações de Design

Nenhuma. Este PRD não altera nenhum pixel do frontend. O único arquivo do `client/` tocado
seria... nenhum: a US-011 apenas faz o CI executar scripts que já existem em
`client/package.json`.

O único efeito perceptível ao usuário final é o logout único no momento da rotação do
segredo (US-010) — esperado e aceito pelas decisões D1 e D5.

---

## 7. Considerações Técnicas

**O risco principal deste PRD: CSRF do STOMP na US-007 — investigado e com saída confirmada.**

`@EnableWebSocketSecurity` liga por padrão um interceptor de CSRF no canal de entrada que
exige um token no frame `CONNECT`. O cliente atual (`@stomp/stompjs` + SockJS via
`useMatchStore`) **não envia esse token**, e o app ainda tem `csrf.disable()` em
`SecurityConfig.java:38` — CSRF do HTTP só volta na T4.3. Sem tratamento, ligar
`@EnableWebSocketSecurity` quebraria todas as conexões WebSocket em produção.

Isto foi verificado contra o artefato real (`spring-security-config:7.1.0` e
`spring-security-messaging:7.1.0`, resolvidos pelo `spring-boot-starter-parent` 4.1.0),
inspecionando o bytecode de `WebSocketMessageBrokerSecurityConfiguration`:

- O default é `XorCsrfChannelInterceptor`, mas a classe o obtém por
  `getBeanOrNull("csrfChannelInterceptor", ChannelInterceptor.class)`. **Declarar um bean
  com esse nome exato sobrescreve o default** — a saída no-op existe e é a decidida.
- A wiring da autorização é `MessageMatcherDelegatingAuthorizationManager.builder()`, com
  um setter que aceita `AuthorizationManager<Message<?>>`.
- A mesma classe também instala um `CsrfTokenHandshakeInterceptor` no handler mapping do
  SockJS/WebSocket. Confirmar no teste de integração da US-007 que isso não interfere no
  `JwtHandshakeInterceptor` existente.

O no-op é aceitável agora porque a proteção de origem já existe:
`WebSocketConfig.java:34` faz `setAllowedOrigins(allowedOrigin)`. E implementar CSRF só no
canal STOMP enquanto o HTTP inteiro está com `csrf.disable()` seria incoerente — as duas
coisas devem virar juntas, na T4.3.

**`spring-security-messaging` não está no classpath.** Verificado: o `~/.m2` do projeto tem
`spring-security-core`, `-config`, `-web`, `-crypto` e `-test` — e nada mais.
`spring-boot-starter-security` não traz o módulo de messaging. Sem ele,
`@EnableWebSocketSecurity` não resolve. A US-007 adiciona a dependência (sem `<version>`;
o parent gerencia).

**A CSP pode quebrar o WebSocket.** Além do `connect-src` já corrigido, o SockJS faz
fallback para `xhr-streaming`/`iframe` quando o `wss://` falha. O fallback por iframe
esbarra em `frame-ancestors 'none'`. Na prática o WebSocket nativo funciona
same-origin sobre HTTPS, mas a verificação da US-010 ("o WebSocket conecta") precisa ser
feita de verdade no browser, não presumida.

**`src/test/resources/application.properties` substitui, não mescla.** Documentado em
`api/CLAUDE.md`: o Spring Boot carrega um único `application.properties` por classpath
root e `test-classes` ganha de `classes`. Toda propriedade que um bean instanciado
avidamente exige precisa estar declarada lá. Isso é diretamente relevante à US-001.

**H2 em `MODE=PostgreSQL` nos testes.** Nenhuma story aqui escreve migration, mas os testes
de `@SpringBootTest` (US-004, US-007) sobem contra o H2 configurado em
`src/test/resources/application.properties` com `spring.flyway.enabled=false` e
`ddl-auto=create-drop`. Não mexer nisso — é escopo da T2.0.

**`./mvnw test` roda de verdade no devcontainer.** `api/CLAUDE.md` é explícito: *"Never
claim a backend change is done without running `./mvnw test`."* O devcontainer tem Temurin
JDK 21 + Maven 3.9.16 e o firewall libera o Maven Central. Se falhar com erro de rede,
rodar `sudo /usr/local/bin/init-firewall.sh` antes de concluir qualquer coisa — Maven
Central está atrás de CDN com IPs rotativos.

**`docker` e `psql` não existem no sandbox.** Testcontainers e Postgres real não estão
disponíveis localmente. O CI roda contra Postgres 17 real, então algo que passa local pode
falhar lá. Nenhuma story deste PRD depende de Postgres, mas a US-011 mexe no workflow —
qualquer mudança no job de backend precisa ser verificada no próprio CI.

**As stories US-008, US-009 e US-010 não têm verificação automatizada.** Não há teste que
prove que o nginx está correto — a única prova é `nginx -t` e o `curl -I` na VPS, ambos na
US-010. Não marcar US-008 e US-009 como concluídas achando que "está feito": elas entregam
o arquivo, a US-010 entrega o efeito.

**Ordem de deploy sugerida.** As mudanças de `api/` (US-001 a US-007) e as de nginx (US-008,
US-009) são independentes e deployáveis separadamente. A rotação do segredo (US-010) deve
acontecer **depois** de a US-001 estar em produção, para que um `.env` mal preenchido falhe
no boot em vez de subir com a chave vazia.

---

## 8. Métricas de Sucesso

- Zero ocorrências de `token=` em `/var/log/nginx/access.log` na VPS após a US-010.
- Zero logs históricos do nginx contendo JWTs (os antigos purgados, incluindo rotacionados).
- Uma tentativa de `SUBSCRIBE` no tópico de outro casal é rejeitada — verificado por teste automatizado, não por inspeção.
- `curl -I https://sessaoadois.luisgosampaio.com` retorna 4 headers de segurança onde antes retornava 0.
- Subir a API sem `JWT_SECRET` falha no boot em 100% das tentativas (hoje: sobe com chave pública).
- Classes de segurança sem teste: de 4 para 0.
- Um PR que quebra o typecheck do frontend é bloqueado pelo CI (hoje: passa).
- O CI dispara em PRs para `main` (hoje: não dispara).
- A suíte de backend continua verde do começo ao fim — nenhuma story pode deixar `./mvnw test` vermelho.

---

## 9. Questões em Aberto

Nenhuma questão de decisão permanece. As três que existiam foram fechadas em 2026-08-02:

| # | Questão | Resolução |
|---|---------|-----------|
| Q1 | Forma da regra do `jacoco:check` | Uma regra: `BUNDLE`/`LINE`/`COVEREDRATIO`, `haltOnFailure=true`. Sem `BRANCH`, sem escopo `CLASS`. Ver US-012. |
| Q2 | A API de CSRF do STOMP no Spring Security 7 permite a saída no-op? | **Sim**, confirmado por inspeção do bytecode de `WebSocketMessageBrokerSecurityConfiguration` (7.1.0): o interceptor é resolvido por `getBeanOrNull("csrfChannelInterceptor", ChannelInterceptor.class)`. Ver seção 7. |
| Q3 | `SEND` só para `/topic/**` ou deny-by-default? | **Deny-by-default**: só o `SUBSCRIBE` autorizado passa; `SEND` e `/app/**` são negados. Ver US-007. |

Resta um único valor a **medir**, não a decidir:

- **O `minimum` do `jacoco:check` (US-012).** Só existe depois que as US-003 a US-007
  estiverem no lugar e `./mvnw test` tiver gerado o relatório. O método está definido na
  story; o número sai da execução e vai comentado no `pom.xml`.
