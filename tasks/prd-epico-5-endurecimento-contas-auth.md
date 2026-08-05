# PRD: Épico 5 — Endurecimento de Contas e Auth

## Introdução

O Épico 4 tirou o token de sessão do alcance do JavaScript (cookies HttpOnly, refresh
com rotação e detecção de reuso, TTL de 15 minutos). Isso resolve o roubo de credencial
**depois** que o usuário está autenticado — e não toca em nada do que acontece **antes**
ou **em volta** disso.

Este épico fecha os quatro buracos restantes, todos derivados da auditoria de
2026-08-02 registrada em `POST-MVP-TASK.md` (T5.1 – T5.6):

1. **Força bruta é livre.** `POST /api/auth/login` não tem lockout, captcha, delay nem
   contador. `POST /api/couple/join` também não, o que transforma um código de convite
   curto num alvo prático.
2. **Enumeração de usuários por timing.** `AuthService.login:36-37` lança
   `InvalidCredentialsException` para e-mail inexistente **sem executar BCrypt**; para
   e-mail existente com senha errada, executa. A diferença de tempo é mensurável e
   revela quais e-mails têm conta.
3. **Código de convite sem ciclo de vida.** `InviteCodeGenerator` gera de 6 a 8
   caracteres (pior caso 31⁶ ≈ 8,8×10⁸), o código nunca expira e continua sendo
   devolvido pela API indefinidamente, mesmo depois do casal formado.
4. **Nenhuma trilha de auditoria.** Não há registro de login (OK ou falho), logout,
   refresh, pareamento nem revogação de token, e não há correlation id. Um incidente
   hoje é praticamente não-investigável.

Fecha ainda um item de higiene: o e-mail do parceiro é entregue pela API
(`PartnerSummary.email`) sem que nenhum componente do frontend o consuma.

**Estado do código na abertura deste épico:** Épicos 1–4 concluídos. Migrations até
`V5__create_refresh_token.sql` (a próxima é a **V6**). `com.app.common` existe (só com
`PageResponse`). CSRF já está ativo com `CookieCsrfTokenRepository.withHttpOnlyFalse()`.

## Goals

- Tornar força bruta inviável em login, registro, refresh e entrada em casal, com
  limites configuráveis e resposta 429 correta.
- Igualar o tempo de resposta do login entre e-mail inexistente e senha incorreta.
- Endurecer a validação de entrada dos endpoints de auth e elevar o custo do hash de
  senha ao patamar recomendado para 2026.
- Dar ciclo de vida completo ao código de convite: comprimento fixo, expiração,
  invalidação após uso e regeneração — com a UI refletindo tudo isso.
- Produzir uma trilha de auditoria de segurança que **sobrevive ao deploy** e permite
  correlacionar todas as linhas de log de uma mesma request.
- Parar de expor o e-mail do parceiro.

## Decisões tomadas para este épico

Estas complementam as decisões D1–D12 de `POST-MVP-TASK.md`, que continuam vinculantes.

| # | Questão | Decisão | Consequência |
|---|---------|---------|--------------|
| E5.1 | Implementação do rate limit | **bucket4j em memória** (`bucket4j-core` no `pom.xml`) | Sem Redis. Os contadores morrem no restart do container — aceito: o app roda numa instância única e reiniciar não é vetor de ataque prático. |
| E5.2 | Custo do BCrypt | **Subir de 10 (default) para 12** | ~4× mais CPU por login (~250ms num core). Hashes antigos de força 10 continuam validando normalmente — **sem re-hash** automático das senhas existentes. |
| E5.3 | Alcance da T5.4 | **Backend + UI completa** | `JoinPage.tsx`/`InviteCodeTicket.tsx` passam a mostrar a expiração e a oferecer regeneração. Por ser UI alterada, a US-010 usa a skill `frontend-design` (regra do `CLAUDE.md` da raiz). |
| E5.4 | Destino do log de auditoria | **stdout + arquivo rotacionado com bind mount** | `scripts/deploy.sh` faz `docker compose down` + `up`, o que **destrói os logs do container**. Só stdout apagaria a trilha a cada deploy. Exige `logback-spring.xml` (não existe hoje) e volume no `docker-compose-prod.yml`. |
| E5.5 | Correlation id | **Entra neste épico** | Filtro + MDC, propagado para todos os logs da aplicação, não só os de auditoria. |
| E5.6 | Status HTTP do convite expirado | **410 Gone** | Semanticamente exato (o recurso existiu e não existe mais) e sem colisão com os `case 404/400/409` já presentes em `JoinPage.tsx:43-55`. |
| E5.7 | Rotação do log de auditoria | **10MB por arquivo × 10 arquivos, `totalSizeCap` de 100MB** | Rotação por tamanho com teto explícito — o disco da VPS nunca enche, independentemente do volume de eventos. |
| E5.8 | Rate limit em dev | **Ativo em todo ambiente**, com `app.rate-limit.enabled` (default `true`) como off-switch | Mesma filosofia de `app.auth.cookie.secure`: default seguro, dev sobrescreve. Produção não depende de alguém lembrar de ligar. |

## User Stories

---

### US-001: Infraestrutura de rate limit (bucket4j, propriedades e IP do cliente)

**Description:** As a desenvolvedor, I want uma base de rate limiting configurável e um
resolvedor único do IP real so that as histórias seguintes apliquem limites sem
duplicar lógica de parsing de `X-Forwarded-For`.

**Acceptance Criteria:**
- [ ] `bucket4j-core` adicionado ao `api/pom.xml` com versão explícita.
- [ ] Novo `ClientIpResolver` em `com.app.security` lê o primeiro IP de `X-Forwarded-For` e cai em `request.getRemoteAddr()` quando o header está ausente ou em branco.
- [ ] `AuthController.clientIp` (linhas 122-128) é **removido** e o controller passa a usar o `ClientIpResolver` — existe uma única implementação dessa lógica na aplicação.
- [ ] Novo `RateLimitProperties` (`@ConfigurationProperties(prefix = "app.rate-limit")`) expondo capacidade e janela por endpoint, com os defaults da tabela de FR-2.
- [ ] `application.properties` declara cada limite via variável de ambiente com default (padrão dos demais: `${VAR:default}`).
- [ ] Novo `RateLimitService` que resolve/cria buckets por chave (`String`) e responde se a requisição é permitida, devolvendo os segundos até a próxima liberação quando não for.
- [ ] Buckets ficam num cache com expiração por inatividade — uma chave nunca usada de novo não vaza memória indefinidamente.
- [ ] `RateLimitServiceTest` e `ClientIpResolverTest` cobrem: liberação dentro do limite, bloqueio ao exceder, liberação após a janela, e as três formas do `X-Forwarded-For` (ausente, único IP, cadeia de IPs).
- [ ] `./mvnw test` passa.

---

### US-002: Rate limit por IP nos endpoints de auth e de casal

**Description:** As a operador do sistema, I want que tentativas repetidas do mesmo IP
sejam bloqueadas so that força bruta contra login e contra código de convite deixe de
ser viável.

**Depends on:** US-001

**Acceptance Criteria:**
- [ ] Novo `RateLimitFilter` em `com.app.security` aplica os limites **por IP** a `POST /api/auth/login`, `POST /api/auth/register`, `POST /api/auth/refresh` e `POST /api/couple/join`.
- [ ] O filtro roda **antes** da cadeia do Spring Security (registrado com `@Order` baixo), para que a requisição bloqueada não chegue a consumir BCrypt nem banco.
- [ ] Exceder o limite retorna **429** com header `Retry-After` em segundos e corpo `{"message": "..."}` em pt-BR, no mesmo formato dos demais erros da aplicação.
- [ ] Existe a property `app.rate-limit.enabled` (default `true`, decisão E5.8). Com `false`, o filtro deixa passar tudo sem contar — é o off-switch de dev local, nunca de produção.
- [ ] Os limites valem também em desenvolvimento com os mesmos números do default; não há profile com tetos diferentes.
- [ ] Nenhum outro endpoint é afetado — `GET /api/tracking`, `GET /api/auth/me` etc. respondem normalmente sob qualquer volume.
- [ ] Requisições dentro do limite passam sem alteração de comportamento ou de payload.
- [ ] Teste de integração cobre: bloqueio ao exceder em cada um dos 4 endpoints, presença e valor plausível do `Retry-After`, liberação após a janela, e que IPs diferentes têm contadores independentes.
- [ ] `./mvnw test` passa.

---

### US-003: Rate limit por conta no login e no join

**Description:** As a operador do sistema, I want um limite adicional por e-mail e por
usuário so that um atacante distribuído em vários IPs ainda esbarre num teto por conta
alvo.

**Depends on:** US-002

**Acceptance Criteria:**
- [ ] `POST /api/auth/login` aplica **10/hora por e-mail**, além do limite por IP.
- [ ] `POST /api/couple/join` aplica **20/hora por usuário autenticado**, além do limite por IP.
- [ ] O limite por conta é aplicado na **camada de serviço** (`AuthService.login`, `CoupleService.joinCouple`), não no filtro — o filtro não pode ler o corpo da requisição sem consumir o `InputStream`, e nessa camada o e-mail/usuário já está desserializado.
- [ ] Nova `RateLimitExceededException` (com o `Retry-After` embutido) tratada por handler que retorna **429**, com o mesmo corpo e header da US-002.
- [ ] A chave por e-mail é normalizada (minúsculas, sem espaços nas pontas) para que variações de caixa não multipliquem o teto.
- [ ] O limite por e-mail dispara mesmo quando o e-mail **não existe** — caso contrário ele próprio vira um oráculo de enumeração.
- [ ] `AuthServiceTest` e `CoupleServiceTest` cobrem bloqueio e liberação.
- [ ] `./mvnw test` passa.

---

### US-004: Nivelar o tempo de resposta do login (fechar enumeração por timing)

**Description:** As a usuário, I want que o login demore o mesmo tempo para um e-mail
que não existe e para uma senha errada so that ninguém descubra quais e-mails têm conta
cronometrando as respostas.

**Acceptance Criteria:**
- [ ] `AuthService.login`, no caminho de e-mail inexistente, executa `passwordEncoder.matches` contra um **hash dummy constante** antes de lançar `InvalidCredentialsException`.
- [ ] O hash dummy é gerado uma única vez (constante estática ou no construtor), nunca por requisição, e corresponde a uma senha que não pertence a ninguém.
- [ ] O tempo de resposta de `POST /api/auth/login` para e-mail inexistente e para senha incorreta difere em **menos de 10%**, medido com ≥100 amostras.
- [ ] A mensagem de erro continua idêntica nos dois casos (comportamento atual, preservado).
- [ ] `AuthServiceTest` cobre explicitamente que o caminho de e-mail inexistente invoca o `passwordEncoder`.
- [ ] `docs/BACKLOG.md` registra a decisão D4 (manter o **409 Conflict** no registro), com a justificativa — não há infraestrutura de e-mail transacional no roadmap — e a mitigação associada (rate limit da US-002).
- [ ] `./mvnw test` passa.

---

### US-005: Validação de entrada do login e teto de senha no registro

**Description:** As a usuário, I want erros de validação claros em vez de 401 ou 500
so that entradas malformadas sejam rejeitadas com 400 antes de tocar o banco.

**Acceptance Criteria:**
- [ ] `LoginRequest`: `@NotBlank` + `@Email` no e-mail, `@NotBlank` na senha, com mensagens em pt-BR no estilo das de `RegisterRequest`.
- [ ] `AuthController.login` recebe `@Valid @RequestBody` (hoje, linha 54, está sem `@Valid`).
- [ ] `RegisterRequest.password` ganha `@Size(min = 8, max = 72)` — BCrypt trunca silenciosamente em 72 bytes, e sem o máximo uma senha longa dá falsa sensação de força.
- [ ] `POST /api/auth/login` com e-mail nulo, em branco ou malformado retorna **400**, não 401 nem 500.
- [ ] Senha com mais de 72 caracteres no registro retorna **400** com mensagem clara em pt-BR.
- [ ] O corpo do erro de validação é o mesmo já produzido por `AuthExceptionHandler.handleValidation` (`{message, errors}`) — sem mudança de formato.
- [ ] `AuthControllerTest` cobre os novos casos de 400.
- [ ] `./mvnw test` passa.

---

### US-006: BCrypt com força 12

**Description:** As a operador do sistema, I want senhas hasheadas com custo maior
so that um vazamento do banco seja significativamente mais caro de explorar.

**Acceptance Criteria:**
- [ ] `SecurityConfig.passwordEncoder` (linha 122) usa `new BCryptPasswordEncoder(12)`.
- [ ] Usuários com hash gravado em força 10 continuam conseguindo logar — BCrypt lê o custo do próprio hash.
- [ ] **Não** há re-hash automático de senhas existentes (decisão E5.2).
- [ ] Um teste verifica que um hash produzido com força 10 ainda valida com o encoder configurado em 12.
- [ ] O tempo de um `matches` com força 12 é medido e registrado no PR/commit, para conferir que cabe no container de 1 CPU / 512M.
- [ ] `./mvnw test` passa.

---

### US-007: Migration V6 e comprimento fixo do código de convite

**Description:** As a desenvolvedor, I need a coluna de expiração e um código de
tamanho previsível so that o ciclo de vida do convite possa ser implementado.

**Acceptance Criteria:**
- [ ] Nova `V6__add_invite_code_expiry.sql` adiciona `invite_code_expires_at TIMESTAMP WITH TIME ZONE` a `couples`, **nullable**, com `IF NOT EXISTS`, sem `DROP`/`TRUNCATE` e compatível com o modo PostgreSQL do H2 — seguindo o padrão de `V5__create_refresh_token.sql` e as convenções de `api/CLAUDE.md`.
- [ ] `Couple` ganha o campo `inviteCodeExpiresAt` (`Instant`) e o construtor passa a recebê-lo/derivá-lo.
- [ ] `InviteCodeGenerator` gera códigos de **exatamente 8** caracteres (`MIN_LENGTH`/`MAX_LENGTH` viram um único `LENGTH = 8`), mantendo o alfabeto sem caracteres ambíguos.
- [ ] Casais **já existentes** com código de 6-7 caracteres e `invite_code_expires_at` nulo continuam funcionando — nulo é tratado como "não expira".
- [ ] O TTL do convite é **7 dias** (decisão D6), configurável por propriedade (`app.couple.invite-code-ttl`, default `7d`).
- [ ] O teste dedicado de migrations (T2.0) sobe com V1–V6 aplicadas em sequência e `ddl-auto=validate` — a entidade bate com o schema.
- [ ] `InviteCodeGeneratorTest` verifica comprimento fixo 8 e alfabeto.
- [ ] `./mvnw test` passa.

---

### US-008: Expiração e invalidação do código de convite

**Description:** As a usuário, I want que meu código de convite expire e pare de
circular depois que meu par entra so that um código adivinhado ou vazado não sequestre
o vínculo do casal.

**Depends on:** US-007

**Acceptance Criteria:**
- [ ] `CoupleService.createCouple` grava `inviteCodeExpiresAt = agora + TTL`.
- [ ] `CoupleService.joinCouple` rejeita código expirado com uma exceção **específica** (nova `InviteCodeExpiredException`), distinta de `InviteCodeNotFoundException`, mapeada no `CoupleExceptionHandler` para **410 Gone** (decisão E5.6) com mensagem própria em pt-BR.
- [ ] O 410 não colide com nenhum status já usado pelos demais erros de `/api/couple/join` (404 código inexistente, 400 próprio código, 409 já pareado/já em casal).
- [ ] `joinCouple` bem-sucedido **invalida** o código (limpa ou marca como usado), de forma que nenhuma tentativa posterior com ele tenha efeito.
- [ ] `CoupleResponse` deixa de expor `inviteCode` depois do pareamento concluído (campo nulo quando `user2Id != null`), e passa a expor `inviteCodeExpiresAt`.
- [ ] A invalidação não quebra a `UniqueConstraint` de `invite_code` quando houver vários casais formados (atenção: vários `NULL` são permitidos em unique no Postgres; se a escolha for marcar em vez de limpar, garantir unicidade).
- [ ] `CoupleServiceTest` cobre: código expirado, código válido, código já usado, e ausência do campo na resposta pós-pareamento.
- [ ] `./mvnw test` passa.

---

### US-009: Endpoint de regeneração do código de convite

**Description:** As a usuário cujo código expirou antes do meu par entrar, I want gerar
um novo código so that eu não precise apagar a conta e recomeçar.

**Depends on:** US-008

**Acceptance Criteria:**
- [ ] `POST /api/couple/invite-code/regenerate` existe, exige autenticação e devolve `CoupleResponse` com o código novo e a nova data de expiração.
- [ ] Só o **criador** do casal (`user1Id`) pode regenerar.
- [ ] Regenerar um casal **já pareado** é rejeitado (não há mais código a compartilhar).
- [ ] O código antigo deixa de funcionar imediatamente após a regeneração.
- [ ] O novo código também tem 8 caracteres e passa pela mesma checagem de unicidade de `generateUniqueInviteCode`.
- [ ] O endpoint entra no rate limit por usuário (reaproveitando a infra da US-003) para não virar um gerador de códigos em massa.
- [ ] Por ser POST autenticado, exige `X-XSRF-TOKEN` — o axios já o envia automaticamente, sem código novo no cliente.
- [ ] `CoupleServiceTest` e `CoupleControllerTest` cobrem sucesso, casal pareado e usuário não-criador.
- [ ] `./mvnw test` passa.

---

### US-010: UI — expiração visível e ação de regenerar

**Description:** As a usuário na tela de convite, I want ver quando meu código expira e
poder gerar um novo so that eu entenda o prazo e resolva sozinho quando ele passar.

**Depends on:** US-009

**Acceptance Criteria:**
- [ ] **Usar a skill `frontend-design` antes de escrever o código** — é UI alterada, não refatoração pura (regra do `CLAUDE.md` da raiz; decisão D11 não se aplica aqui).
- [ ] `InviteCodeTicket.tsx` substitui o texto fixo *"Válido até alguém usar"* (linha 32-33), que passa a ser **falso**, por uma indicação real de prazo (ex.: "Expira em 6 dias").
- [ ] Quando o código está expirado, o ticket comunica isso claramente e a ação de regenerar fica em evidência.
- [ ] Existe um botão de regenerar que chama `POST /api/couple/invite-code/regenerate` e atualiza o ticket com o código novo, sem recarregar a página.
- [ ] O tipo `Couple` em `client/src/stores/useAuthStore.ts` espelha `inviteCodeExpiresAt` e o `inviteCode` opcional/nulo (não há codegen — a sincronização é manual, conforme `client/CLAUDE.md`).
- [ ] `JoinPage.tsx` ganha um `case 410` no switch de erro (linhas 43-55) com mensagem própria, distinta do "não encontramos esse código" do `case 404`, orientando o usuário a pedir um código novo ao par.
- [ ] Estados de carregamento e de erro do botão de regenerar seguem o padrão já usado em `handleCreate`.
- [ ] `bun run typecheck` e `bun run lint` passam.
- [ ] Verificar no browser com a skill `dev-browser`: ticket com prazo, ticket expirado, e regeneração bem-sucedida.

---

### US-011: Correlation id por request

**Description:** As a quem investiga um incidente, I want que todas as linhas de log de
uma mesma requisição compartilhem um identificador so that eu consiga reconstruir o que
aconteceu sem cruzar timestamps na mão.

**Acceptance Criteria:**
- [ ] Novo filtro em `com.app.security` gera um correlation id por request (ou reaproveita o header `X-Request-Id` quando presente) e o coloca no **MDC**.
- [ ] O MDC é **sempre** limpo ao fim da request, inclusive quando ela lança exceção (`try/finally`) — senão o id vaza para a próxima request na mesma thread do pool.
- [ ] O padrão de log da aplicação inclui o correlation id em toda linha.
- [ ] O id é devolvido ao cliente no header de resposta, para que um relato de erro do usuário possa ser amarrado ao log.
- [ ] Duas requisições concorrentes produzem ids distintos, e nenhuma linha de uma aparece com o id da outra.
- [ ] `./mvnw test` passa.

---

### US-012: Logger de auditoria de segurança e emissão dos eventos

**Description:** As a operador do sistema, I want um registro estruturado dos eventos de
segurança so that eu consiga responder "quem entrou, quando, de onde" depois de um
incidente.

**Depends on:** US-002, US-008, US-011

**Acceptance Criteria:**
- [ ] Novo componente de auditoria em `com.app.security` escrevendo num logger dedicado de nome `security.audit`, com formato estruturado (par chave=valor ou JSON), separado do log de aplicação.
- [ ] Eventos registrados: **login OK**, **login falho** (com e-mail e IP), **logout**, **refresh**, **detecção de reuso de refresh token**, **criação de casal**, **entrada em casal**, **regeneração de código de convite** e **bloqueio por rate limit (429)**.
- [ ] Cada entrada carrega o correlation id da US-011.
- [ ] **Nenhuma** entrada contém senha, token em claro, hash de token ou o valor do código de convite — verificado por teste que inspeciona a saída do logger.
- [ ] Os pontos de emissão ficam em `AuthService`, `CoupleService`, `RefreshTokenService` e no `RateLimitFilter`, sem que nenhum deles construa a string de log à mão (formatação centralizada no componente).
- [ ] O nível do logger `security.audit` é configurável independentemente do resto da aplicação.
- [ ] Teste cobre a emissão de pelo menos login OK, login falho e detecção de reuso.
- [ ] `./mvnw test` passa.

---

### US-013: Persistir o log de auditoria fora do ciclo de vida do container

**Description:** As a operador do sistema, I want que a trilha de auditoria sobreviva ao
deploy so that ela sirva de fato para investigar um incidente antigo.

**Depends on:** US-012

**Acceptance Criteria:**
- [ ] Novo `api/src/main/resources/logback-spring.xml` (hoje não existe nenhum) mantendo o console appender e adicionando um `RollingFileAppender` para o logger `security.audit`, com `SizeBasedTriggeringPolicy` de **10MB por arquivo**, `maxHistory` de **10 arquivos** e `totalSizeCap` de **100MB** (decisão E5.7).
- [ ] O `totalSizeCap` é verificado na prática: gerar volume acima do teto e conferir que os arquivos mais antigos são descartados em vez de o disco crescer.
- [ ] O caminho do arquivo é configurável por propriedade, com default apontando para um diretório fora do código-fonte da aplicação.
- [ ] O logger `security.audit` continua saindo **também** no stdout — `docker compose logs` segue sendo o lugar óbvio de olhar no dia a dia.
- [ ] `docker-compose-prod.yml` monta um volume da VPS para o diretório do log, de forma que `docker compose down` + `up` (o que o `scripts/deploy.sh` faz na etapa 5/5) **não** apague a trilha.
- [ ] `docker-compose-prod.yml` limita o log driver `json-file` (`max-size`/`max-file`) — hoje ele cresce sem limite no disco da VPS.
- [ ] Rodar a aplicação localmente sem o diretório montado **não** quebra o startup.
- [ ] `docs/DEPLOY.md` documenta: criar o diretório na VPS antes do primeiro deploy com esta mudança, onde o arquivo vive, qual é a política de rotação e como consultá-lo.
- [ ] Verificado na prática que, após um `docker compose down` + `up`, as entradas anteriores continuam no arquivo.

---

### US-014: Remover o e-mail do parceiro da API

**Description:** As a usuário, I want que meu e-mail não seja entregue ao aplicativo do
meu parceiro so that PII não circule sem necessidade.

**Acceptance Criteria:**
- [ ] `PartnerSummary` não tem mais o campo `email` (hoje: `PartnerSummary.java:5`).
- [ ] `AuthController.toResponse` (linha 133) e `CoupleController.toPartnerSummary` (linha 60) param de preenchê-lo.
- [ ] Nenhuma resposta da API expõe o e-mail do parceiro — conferido em `POST /api/auth/login`, `GET /api/auth/me`, `GET /api/couple/me`, `POST /api/couple` e `POST /api/couple/join`.
- [ ] O tipo `PartnerSummary` em `client/src/stores/useAuthStore.ts` (linhas 15-19) é espelhado sem `email`.
- [ ] A UI renderiza **igual** ao anterior — o campo não era consumido por nenhum componente (verificado na auditoria). Nenhuma mudança visual, portanto a skill `frontend-design` **não** se aplica (decisão D11).
- [ ] O e-mail do **próprio** usuário continua sendo devolvido em `UserSummary` — só o do parceiro sai.
- [ ] `./mvnw test` e `bun run typecheck` passam.

---

## Functional Requirements

**Rate limiting (T5.1)**

- **FR-1:** O sistema deve limitar requisições por IP e por conta nos endpoints de auth e de entrada em casal, respondendo **429** com header `Retry-After` quando o limite for excedido.
- **FR-2:** Os limites default devem ser:

  | Endpoint | Por IP | Por conta |
  |---|---|---|
  | `POST /api/auth/login` | 5/min | 10/hora por e-mail |
  | `POST /api/auth/register` | 3/min | — |
  | `POST /api/auth/refresh` | 30/min | — |
  | `POST /api/couple/join` | 5/min | 20/hora por usuário |
  | `POST /api/couple/invite-code/regenerate` | — | reaproveita o teto por usuário |

- **FR-3:** Todos os limites devem ser configuráveis por propriedade/variável de ambiente, e devem valer em todos os ambientes. Deve existir `app.rate-limit.enabled` (default `true`) como único mecanismo de desligamento.
- **FR-4:** O contador deve usar o IP do cliente final lido de `X-Forwarded-For` (o nginx já envia), nunca o IP do proxy.

**Enumeração e validação (T5.2, T5.3)**

- **FR-5:** `AuthService.login` deve executar BCrypt contra um hash dummy constante quando o e-mail não existir, nivelando o tempo de resposta.
- **FR-6:** O registro deve continuar respondendo **409 Conflict** para e-mail já cadastrado (decisão D4), com a decisão registrada em `docs/BACKLOG.md`.
- **FR-7:** `LoginRequest` deve validar `@NotBlank @Email` no e-mail e `@NotBlank` na senha, e `AuthController.login` deve usar `@Valid`.
- **FR-8:** `RegisterRequest.password` deve ter `@Size(min = 8, max = 72)`.
- **FR-9:** `PasswordEncoder` deve usar BCrypt com força 12, sem re-hash de senhas existentes.

**Ciclo de vida do convite (T5.4)**

- **FR-10:** Códigos de convite novos devem ter exatamente 8 caracteres.
- **FR-11:** `couples` deve ganhar `invite_code_expires_at` (migration **V6**), com TTL de 7 dias configurável.
- **FR-12:** Um código expirado deve ser rejeitado com **410 Gone** e mensagem específica, distinta do 404 de "código não encontrado".
- **FR-13:** Um pareamento bem-sucedido deve invalidar o código.
- **FR-14:** `CoupleResponse` não deve expor `inviteCode` após o pareamento, e deve expor `inviteCodeExpiresAt`.
- **FR-15:** Deve existir `POST /api/couple/invite-code/regenerate`, restrito ao criador do casal e bloqueado para casais já pareados.
- **FR-16:** A UI deve mostrar o prazo de expiração e oferecer a ação de regenerar.
- **FR-17:** Casais existentes com código de 6-7 caracteres e expiração nula devem continuar funcionando.

**Auditoria (T5.5)**

- **FR-18:** Deve existir um logger `security.audit` separado, com formato estruturado e nível/destino próprios.
- **FR-19:** Devem ser registrados: login OK/falho, logout, refresh, reuso de refresh token, criação/entrada em casal, regeneração de convite e 429 de rate limit.
- **FR-20:** Nenhum log pode conter senha, token, hash de token ou o valor do código de convite.
- **FR-21:** Toda linha de log de uma mesma request deve compartilhar um correlation id.
- **FR-22:** O log de auditoria deve persistir em arquivo montado por volume, sobrevivendo ao `docker compose down` do deploy, com rotação de 10MB por arquivo, 10 arquivos e teto total de 100MB.

**PII (T5.6)**

- **FR-23:** `PartnerSummary` não deve conter `email`, no backend nem no tipo espelhado do cliente.

## Non-Goals (Out of Scope)

- **Rate limiting distribuído** (Redis, bucket4j-redis). O app roda numa instância única; contadores em memória bastam (decisão E5.1).
- **Mudar o 409 do registro** para uma resposta genérica, ou construir e-mail de confirmação. Decisão D4 é vinculante e vale como está.
- **Checagem de senha contra listas vazadas** (HaveIBeenPwned) — exige chamada externa, vira task própria.
- **Re-hash das senhas existentes** para força 12. Hashes antigos seguem válidos (decisão E5.2).
- **Tela de "encerrar sessão em todos os dispositivos"** — o banco já suporta desde a T4.4, mas a UI é feature de produto.
- **Encaminhar logs para fora da VPS** (Loki, Papertrail, etc.). Arquivo local rotacionado é suficiente para 8 usuários.
- **Captcha** em qualquer endpoint.
- **Redesenhar as telas de auth.** A US-010 altera apenas o ticket de convite e as mensagens correlatas; o resto de `JoinPage` fica como está.
- Qualquer task dos Épicos 6, 7 e 8.

## Design Considerations

- A única história com mudança visual é a **US-010**, e ela **deve** passar pela skill `frontend-design` antes da implementação, conforme o `CLAUDE.md` da raiz. As demais são backend ou refatoração sem pixel alterado.
- `InviteCodeTicket.tsx` tem uma identidade visual forte e deliberada (ingresso de cinema, amarelo `#ffcb2b`, picote tracejado). A informação de expiração deve entrar **dentro** dessa metáfora — um ingresso tem data — e não como um aviso genérico colado por cima.
- O texto atual *"Válido até alguém usar"* (`InviteCodeTicket.tsx:32-33`) torna-se factualmente errado com esta mudança e precisa sair.
- Reutilizar o padrão de erro em `role="alert"` já usado em `JoinPage.tsx` para a mensagem de código expirado.

## Technical Considerations

- **Ordem sugerida:** US-001 → US-002 → US-003 (rate limit) · US-004, US-005, US-006 (independentes entre si) · US-007 → US-008 → US-009 → US-010 (convite) · US-011 → US-012 → US-013 (auditoria) · US-014 (independente). A US-012 depende de US-002, US-008 e US-011 porque registra eventos originados nelas.
- **O filtro não pode ler o corpo da requisição.** Ler o `InputStream` no filtro para extrair o e-mail o consumiria antes do `@RequestBody`. Por isso a US-003 aplica os limites por conta na camada de serviço, não no filtro. Não tentar resolver isso com `ContentCachingRequestWrapper` — a separação por camada é mais simples e mais testável.
- **Ordem dos filtros.** O `RateLimitFilter` precisa rodar antes da cadeia do Spring Security para que uma requisição bloqueada não custe BCrypt. Atenção ao padrão já usado em `SecurityConfig.jwtFilterRegistration` (linhas 113-118): o `JwtAuthenticationFilter` é registrado como bean e **desabilitado** na cadeia de servlet padrão para não rodar duas vezes. O filtro de rate limit tem o requisito oposto e não deve copiar esse arranjo por inércia.
- **Unicidade do invite code na invalidação (US-008).** `couples` tem `UniqueConstraint(columnNames = "invite_code")` e a coluna é `nullable = false` hoje. Limpar o código exige tornar a coluna nullable na V6; marcar como usado exige um campo separado. Escolher explicitamente e registrar a escolha no comentário da migration.
- **Flyway e o pooler do Supabase.** A V6 é uma migration nova sobre uma base já com histórico — vale a ressalva de `docs/FLYWAY.md` sobre advisory locks com o pooler em modo Transaction (porta 6543). Usar conexão direta (5432) ou pooler em modo Session no deploy que a carregar.
- **A V6 é validada de verdade.** O teste dedicado de migrations criado na T2.0 roda V1–V6 com `ddl-auto=validate` — a divergência entidade↔schema falha o build. Não presumir: rodar.
- **Correlation id e pool de threads.** Sem `finally` limpando o MDC, o id vaza para a próxima request atendida pela mesma thread do Tomcat. É o bug clássico dessa implementação.
- **`logback-spring.xml` não existe hoje.** Criá-lo substitui a configuração default do Spring Boot — o console appender precisa ser reproduzido explicitamente, ou o log normal da aplicação muda de formato sem querer.
- **Container apertado:** 1 CPU / 512M (`docker-compose-prod.yml:15-19`). BCrypt força 12 e os buckets em memória entram nesse orçamento; medir antes de dar por concluído.

## Success Metrics

- 6 tentativas de login em um minuto do mesmo IP: a 6ª retorna 429 com `Retry-After`.
- Diferença de tempo de resposta entre e-mail inexistente e senha errada abaixo de 10% em ≥100 amostras.
- Espaço de busca do código de convite passa de 31⁶ (~8,8×10⁸) para 31⁸ (~8,5×10¹¹), e o código deixa de ser válido indefinidamente.
- Após um deploy completo (`docker compose down` + `up`), as entradas de auditoria anteriores continuam legíveis.
- Grep por senha/token/hash em toda a saída de log de um fluxo completo (registro → login → join → refresh → logout) retorna zero ocorrências.
- `./mvnw test`, `bun run typecheck` e `bun run lint` verdes ao fim de cada história.

## Open Questions

**Nenhuma pendente.** As três questões levantadas na primeira versão deste PRD foram
resolvidas em 2026-08-04 e viraram as decisões **E5.6** (410 Gone para convite
expirado), **E5.7** (rotação 10MB × 10, teto de 100MB) e **E5.8** (rate limit ativo em
todo ambiente, com off-switch). Estão na tabela de decisões acima e refletidas nos
critérios de aceite das US-002, US-008, US-010 e US-013.

Duas coisas a **verificar durante a implementação** — não são decisões em aberto, são
medições que só o código responde:

- O custo real do BCrypt força 12 no container de 1 CPU / 512M (US-006). Se o login
  passar de ~500ms, revisitar a decisão E5.2 registrando o número medido.
- Se limpar `invite_code` (tornando a coluna nullable) ou marcar como usado com campo
  separado (US-008). A escolha depende de como a `UniqueConstraint` existente se
  comporta com múltiplos `NULL` — decidir ao escrever a V6 e justificar no comentário
  da migration.
