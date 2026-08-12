# PRD: Épico 12 — Observabilidade e Alerta

**Branch sugerida:** `epico12/observabilidade-e-alerta`
**Origem:** `POST-MVP-TASK.md`, Épico 12 (T12.1 a T12.5), varredura de lacunas de MVP de 2026-08-06, lacuna #5.
**Data:** 2026-08-12

---

## 1. Introdução / Visão geral

Hoje a aplicação não avisa quando cai, e quando algo quebra não há por onde puxar o fio.
Três buracos concretos, todos medidos no código atual:

1. **`HealthController` mente.** `api/src/main/java/com/app/HealthController.java:15-18`
   devolve `Map.of("status", "UP")` — uma constante. Se o Postgres do Supabase cair ou a
   credencial expirar, o endpoint continua `200 UP`, o `HEALTHCHECK` do container continua
   verde e o smoke test do CD passa, com o app 100% quebrado para o usuário.
2. **Ninguém observa o app de fora.** `docs/DEPLOY.md` só oferece investigação manual por
   SSH (`docker compose logs`, `docker ps`). Uma queda de madrugada é descoberta no dia
   seguinte, pelo parceiro reclamando.
3. **Erro de render no frontend = tela branca.** Não existe **nenhum** error boundary em
   `client/src/` (busca por `ErrorBoundary` não retorna nada). Uma exceção durante o render
   desmonta a árvore React inteira: o usuário fica com uma página em branco, sem mensagem, e
   o servidor não fica sabendo de nada. O Épico 6 tratou os `catch` silenciosos das chamadas
   de API; o erro de **render** nunca foi coberto.

Este épico fecha os três, mais a retenção/consulta de log (T12.4) e o alerta de falha de
deploy (T12.5), **sem introduzir nenhum custo recorrente** (decisão D15).

### O que já está pronto (medido em 2026-08-12, antes de qualquer story)

O Épico 12 foi escrito em 2026-08-06 e parte do estado mudou desde então. Confirmado no
código atual, para ninguém refazer trabalho feito:

| Item | Estado real hoje | Consequência para o PRD |
|---|---|---|
| Teto de tamanho do audit log | **Já existe** — `logback-spring.xml:24-29` tem `maxFileSize 10MB`, `maxHistory 10`, `totalSizeCap 100MB` | A T12.4 vira quase toda documentação. Não reconfigurar o que já está lá; confirmar, comentar e documentar. |
| Limite do `docker logs` da API | **Já existe** — `docker-compose-prod.yml` tem `logging: json-file` com `max-size: "10m"`, `max-file: "3"` | Idem. O risco de disco cheio já está mitigado; falta estar escrito em `docs/DEPLOY.md`. |
| Consulta por correlation id | **Parcial** — `docs/DEPLOY.md:718-719` tem um `grep 'correlationId=<id>'` no audit log, e só | Falta o procedimento ponta a ponta (log da aplicação + relatório do cliente), que é o que a T12.4 pede. |
| `CorrelationIdFilter` | **Já existe** — gera/reaproveita `X-Request-Id` no MDC e devolve no header da resposta | A T12.3 se apoia nele; não criar mecanismo novo de correlação. |
| Rate limit por IP | **Já existe** — `RateLimitFilter` + `RateLimitService` + `RateLimitProperties` | A T12.3 adiciona uma entrada na infra existente, não constrói nada novo. |

---

## 2. Objetivos

- **G1.** Saber que a aplicação caiu **antes** do usuário avisar, com alerta que chega ao mantenedor em até 10 minutos.
- **G2.** Fazer o health check dizer a verdade: banco inacessível ⇒ `503`, container `unhealthy`, monitor vermelho.
- **G3.** Substituir a tela branca por uma tela de erro que explica o que houve e oferece saída.
- **G4.** Fazer o erro de render do cliente chegar ao log do servidor, correlacionado com o `X-Request-Id`.
- **G5.** Ter retenção de log declarada e um procedimento escrito de investigação por correlation id, executável só com a documentação.
- **G6.** Fazer uma falha de deploy chegar ao mantenedor por e-mail, identificando o job e linkando a run.
- **G7.** Custo recorrente **zero** (D15) — tudo em free tier ou na infra que já existe.

---

## 3. Decisões confirmadas (E12.x)

Respondidas pelo mantenedor em 2026-08-12. Vinculantes para as stories abaixo; não reabrir
sem registrar o motivo aqui.

| # | Questão | Decisão | Consequência |
|---|---|---|---|
| **E12.1** | Escopo do PRD | **As 5 tasks (T12.1–T12.5).** A T12.1 entra como story de documentação + checklist operacional executado pelo mantenedor. | US-011 (doc, agente) e US-012 (operacional, mantenedor). O `prd.json` do ralph deve excluir a US-012, como fez o Épico 8 com as suas stories operacionais. |
| **E12.2** | Como implementar o health profundo | **Spring Boot Actuator**, expondo **somente** o grupo de health — e nem isso pela web. | `spring-boot-starter-actuator` entra no `pom.xml`; `/actuator/**` fica **fechado** (exposure web vazia) e `/api/health` continua sendo a única porta pública, agora delegando ao `HealthEndpoint`. Ver E12.3. |
| **E12.3** | Como `/api/health` usa o Actuator | **`HealthController` continua existindo e delega ao `HealthEndpoint`** em vez de ser substituído pelo mapeamento web do Actuator. | Preserva os três consumidores que dependem da rota (`HEALTHCHECK` do Dockerfile, smoke test do `deploy.yml`, bootstrap do cookie `XSRF-TOKEN` afirmado por `SecurityConfigTest`) e mantém o shape da resposta sob nosso controle. |
| **E12.4** | Canal de alerta (T12.1 e T12.5) | **E-mail nos dois.** | O uptime usa o alerta por e-mail do próprio serviço de monitoramento (free tier). O deploy usa a API do **Resend**, que já está configurada e paga zero (Épico 10) — sem provedor novo, sem token novo além do destinatário. |
| **E12.5** | Autenticação do `POST /api/client-errors` | **Público** (`permitAll` + fora do CSRF), com **rate limit por IP** no `RateLimitFilter` existente. | Captura também erro de render na landing/login/registro, que é justamente onde não há sessão. O custo é uma superfície pública a mais — mitigada por rate limit, limite de tamanho, sanitização e resposta sem eco (US-003/US-004). |
| **E12.6** | Log da aplicação vai para arquivo? | **Não.** Fica no `docker logs` com o `max-size`/`max-file` que já existe. | A T12.4 é documentação + verificação, não configuração nova de appender. Nada de segundo `RollingFileAppender`. |
| **E12.7** | Onde mora o endpoint de erro do cliente | Pacote novo **`com.app.observability`**, seguindo package-by-feature. | `ClientErrorController` + `ClientErrorRequest` + `ClientErrorLogger` (o molde é `SecurityAuditLogger`: um lugar só formata a linha de log). `docs/ARCHITECTURE.md` §3 ganha a entrada do pacote. |
| **E12.8** | Fechamento da Open Question #1 do Épico 8 | Registrar **aqui e no `POST-MVP-TASK.md`**, não no PRD do Épico 8. | O arquivo `tasks/prd-epico-8-cicd-e-infraestrutura.md` **não existe mais no repo** (a pasta `tasks/` é esvaziada quando um épico fecha; sobra `scripts/ralph/archive/2026-08-06-epico8-cicd-infraestrutura/`). A AC original da T12.5 é impossível como escrita — US-010 a cumpre no lugar em que a informação continua legível. |

---

## 4. User Stories

Ordem de execução: **US-001 → US-012**. A única dependência rígida é a do próprio épico
(T12.1 depende da T12.2: monitorar um health check raso é falso-negativo garantido), que
aqui vira **US-011/US-012 só depois de US-001/US-002**. O resto é independente e pode ser
reordenado.

### US-001: Actuator no classpath com `/actuator/**` fechado

**Descrição:** Como mantenedor, quero o Spring Boot Actuator disponível para os health
indicators sem abrir nenhum endpoint novo na web, para ganhar a checagem de banco pronta
sem ganhar superfície de ataque.

**Critérios de aceite:**
- [ ] `spring-boot-starter-actuator` adicionado ao `api/pom.xml` (sem `<version>` — vem do parent 4.1.0).
- [ ] `management.endpoints.web.exposure.include` explicitamente **vazio** em `application.properties`, com comentário dizendo que é deliberado (o default do Boot expõe `health` em `/actuator/health`).
- [ ] `management.endpoint.health.show-details=never` declarado, para que nenhum detalhe de conexão vaze caso alguém exponha o endpoint no futuro.
- [ ] Teste novo prova que `GET /actuator/health` e `GET /actuator` **não** respondem `200` para requisição anônima (o esperado é `401` pelo `anyRequest().authenticated()` ou `404` pela exposição fechada — asserte o que o app realmente fizer, e comente qual dos dois é).
- [ ] `SecurityConfigTest` continua passando sem alteração.
- [ ] `api/src/test/resources/application.properties` espelha qualquer propriedade nova de que um bean instanciado ansiosamente dependa (ver a regra em `api/CLAUDE.md`).
- [ ] `./mvnw test` passa.

**Notas de implementação:**
- **Não assumir os pacotes da documentação do Spring Boot 3.x.** O Boot 4.1 moveu classes de auto-configuração entre artefatos (ver `api/CLAUDE.md`). Antes de referenciar qualquer `*AutoConfiguration`/`*HealthIndicator` por nome, confirme o pacote real com `unzip -l ~/.m2/repository/org/springframework/boot/<artefato>/4.1.0/*.jar | grep <Classe>`.
- Um build verde não prova que a dependência entrou como você espera: confira com `./mvnw dependency:tree -Dincludes=org.springframework.boot:spring-boot-starter-actuator`.

---

### US-002: `/api/health` verifica o banco antes de responder

**Descrição:** Como mantenedor, quero que `/api/health` só responda `UP` quando o banco
estiver acessível, para que o `HEALTHCHECK` do container e o monitor externo reflitam o
estado real da aplicação.

**Critérios de aceite:**
- [ ] `HealthController` (segue em `com.app`, raiz do pacote) delega ao `HealthEndpoint` do Actuator em vez de devolver constante.
- [ ] Banco no ar ⇒ `200` com status detalhado, no formato `{"status":"UP","db":"UP"}` (vocabulário fixo, sem texto vindo da exceção).
- [ ] Banco fora ⇒ `503` com `{"status":"DOWN","db":"DOWN"}`, **em no máximo ~2s**, medido no teste.
- [ ] A resposta de falha **não** contém credencial, host/URL do datasource, nome de usuário nem stack trace — asserção explícita no teste, não só inspeção visual.
- [ ] A espera é **limitada** (a checagem roda com timeout de 2s e o controller responde `503` se estourar), e o executor usado é **limitado e daemon**, para que um banco pendurado não acumule thread a cada probe (30s do container + 5min do monitor).
- [ ] `spring.datasource.hikari.connection-timeout` **não** é reduzido globalmente para atender este requisito (ver Considerações técnicas).
- [ ] `/api/health` continua `permitAll` em `SecurityConfig` **e** continua na lista de `ignoringRequestMatchers` do CSRF.
- [ ] `SecurityConfigTest` continua verde, incluindo `healthResponseSetsTheCsrfCookieForTheSpaToRead`.
- [ ] `HealthControllerTest` cobre os **dois** cenários (banco no ar, banco fora) e o cenário de timeout.
- [ ] Efeito em cascata verificado e registrado no PR: com o banco fora, o `HEALTHCHECK` do container passa a marcar `unhealthy` (o `wget -qO-` do `api/Dockerfile:43-44` já sai com código ≠ 0 em `503`) e o step **Smoke test do endpoint publico de health** do `deploy.yml` (`curl -fsS`) passa a falhar o deploy. Ambos são desejados; o segundo precisa estar dito em voz alta no PR porque muda quando um deploy é considerado bem-sucedido.
- [ ] `./mvnw test` passa.

**Fora do escopo desta story:** checar o TMDB no health (dependência externa fora do ar não
deve derrubar o container); métricas de negócio.

---

### US-003: Endpoint `POST /api/client-errors`

**Descrição:** Como mantenedor, quero receber no log do backend os erros de render do
frontend, para conseguir investigar depois um "ficou tudo branco" que o usuário relatou.

**Critérios de aceite:**
- [ ] Pacote novo `com.app.observability` com `ClientErrorController`, o DTO `ClientErrorRequest` e um `ClientErrorLogger` (o controller não formata a linha de log na mão — mesmo molde de `SecurityAuditLogger`).
- [ ] `POST /api/client-errors` aceita `{ message, stack, route, userAgent }`, todos opcionais exceto `message`.
- [ ] Limites de tamanho por campo via Bean Validation: `message` ≤ 500, `stack` ≤ 4000, `route` ≤ 300, `userAgent` ≤ 300. Corpo acima do limite ⇒ `400` pelo `GlobalExceptionHandler` existente, **sem** ecoar o conteúdo recebido.
- [ ] Resposta de sucesso é `204 No Content` **sem corpo** — nada do que foi recebido volta na resposta (nem em mensagem de erro).
- [ ] A linha de log sai no logger da própria classe, nível `warn`, no formato `event=client_error correlationId=... route="..." message="..."`, e o `correlationId` vem do MDC preenchido pelo `CorrelationIdFilter` (que gera um se o cliente não mandar `X-Request-Id`) — **não** criar mecanismo de correlação novo.
- [ ] **Sanitização contra log forging:** quebras de linha e caracteres de controle em `message`/`stack`/`route`/`userAgent` são removidos ou escapados antes de ir para o log. Entrada pública e não autenticada não pode injetar linha falsa no log.
- [ ] `SecurityConfig`: `POST /api/client-errors` em `permitAll` **e** em `ignoringRequestMatchers` do CSRF (E12.5).
- [ ] `docs/ARCHITECTURE.md` §3 ganha a entrada do pacote `com.app.observability`, no mesmo formato das outras features.
- [ ] Teste de controller cobre: sucesso (`204`), corpo acima do limite (`400`), requisição anônima é aceita, e a linha de log emitida contém o correlation id (use um `ListAppender` do Logback, o padrão já usado em `MediaTrackQueryCountTest`).
- [ ] `./mvnw test` passa.

---

### US-004: Rate limit por IP do `POST /api/client-errors`

**Descrição:** Como mantenedor, quero limitar quantos relatórios de erro um mesmo IP pode
mandar por minuto, para que um endpoint público e não autenticado não vire vetor de flood do
log.

**Critérios de aceite:**
- [ ] `RateLimitProperties` ganha `clientErrors`, default **10/min** (mesma forma dos existentes: `new Limit(10, Duration.ofMinutes(1))`), configurável por `app.rate-limit.client-errors`.
- [ ] `RateLimitFilter` ganha a entrada `CLIENT_ERRORS("POST", "/api/client-errors")` no enum de rotas cobertas — chaveada por IP via `ClientIpResolver`, como as outras.
- [ ] Estouro devolve `429` com header `Retry-After`, no mesmo shape das outras rotas.
- [ ] Teste prova o bloqueio com `@TestPropertySource(properties = {"app.rate-limit.enabled=true", ...})` e capacidade/janela pequenas (o default do `application.properties` de teste é `enabled=false` de propósito — ver `api/CLAUDE.md`).
- [ ] O 429 continua sendo auditado uma vez, pelo caminho que já existe em `RateLimitFilter` (`SecurityAuditLogger.rateLimitExceeded`) — sem ponto de emissão novo.
- [ ] `./mvnw test` passa.

---

### US-005: O cliente sabe reportar um erro (`reportClientError`)

**Descrição:** Como desenvolvedor, quero uma função única que envia o relatório de erro ao
backend com o correlation id da última resposta, para que o boundary da US-006 não precise
saber nada de HTTP.

**Critérios de aceite:**
- [ ] `client/src/lib/api.ts` guarda em escopo de módulo o último `X-Request-Id` visto numa resposta (interceptor de resposta, ramo de sucesso **e** de erro).
- [ ] Função nova `reportClientError({ message, stack, route })` manda o `POST /api/client-errors` com o header `X-Request-Id` quando houver um guardado, e sem ele quando não houver (o backend gera).
- [ ] O envio é **fire-and-forget** e nunca lança para quem chamou; a falha é tratada com `console.error` com contexto — nada de `catch {}` (anti-pattern #4).
- [ ] O relatório **não** inclui nenhum dado sensível: nada de `localStorage`, nada de cookie (são HttpOnly de qualquer forma), nada de e-mail ou nome do usuário, e a `route` é só o `pathname` — **sem query string** e sem hash.
- [ ] O `stack` é truncado no cliente em 4000 caracteres, para casar com o limite do servidor e não perder o relatório inteiro por causa do tamanho.
- [ ] A chamada não passa pelo tratamento de 401 do interceptor de forma nociva: um erro no `/api/client-errors` nunca pode disparar refresh nem redirecionar para `/login`. Verifique contra as guardas existentes (`isSessionProbe`/`isPasswordChallenge`) e, se preciso, adicione a rota à isenção — a regra do `client/CLAUDE.md` é que **toda chamada automática, sem gesto do usuário, precisa dessa isenção**.
- [ ] Teste (`*.test.ts`) com o adapter falso de axios (padrão do Épico 11 para testar o próprio `lib/api.ts`) cobrindo: header enviado quando há id guardado, ausência do header quando não há, e falha de rede não propagando exceção.
- [ ] `bun run typecheck`, `bun run lint` e `bun run test:run` passam.

**Nota:** esta story é lógica pura + rede, **sem pixel novo** — pela D11, está dispensada da
skill `frontend-design`. A US-006 não está.

---

### US-006: Error boundary no topo da árvore React

**Descrição:** Como usuário, quero ver uma tela explicando que algo deu errado e um botão
para recarregar, em vez de uma página em branco, quando o app quebra.

**Critérios de aceite:**
- [ ] Componente novo (classe React — não há equivalente em hook para `componentDidCatch`) montado em `client/src/main.tsx` **por fora** do `ThemeProvider` e do `BrowserRouter`, para também capturar uma exceção vinda de dentro deles.
- [ ] Como o boundary vive fora do `ThemeProvider`, a tela de erro **não** depende do contexto de tema: as cores vêm inline, no padrão do resto do `client/` (ver Considerações de design).
- [ ] Erro de render mostra a tela de erro — **nunca** tela branca.
- [ ] A tela oferece **recarregar a página** como ação primária.
- [ ] O `componentDidCatch` chama `reportClientError` (US-005) com mensagem, stack e `location.pathname`.
- [ ] A tela mostra o correlation id (quando houver) em texto pequeno, para o usuário poder citá-lo — é o que liga a tela ao procedimento de investigação da US-008.
- [ ] Nenhum stack trace é mostrado ao usuário.
- [ ] Teste (`*.test.tsx`) renderiza um filho que lança e afirma que (a) a tela de erro aparece, (b) `reportClientError` foi chamado. Silencie o `console.error` que o React emite nesse caso, para o output do teste continuar legível.
- [ ] A skill **`frontend-design` foi invocada** para a tela de erro (obrigatório pelo `CLAUDE.md` da raiz — é UI nova, ainda que raramente vista).
- [ ] Verificar no browser com a skill `dev-browser`. **Se o sandbox não tiver browser** (é o caso hoje: sem Chromium e sem sudo para as libs — ver `client/CLAUDE.md`), registrar isso explicitamente no `progress.txt`/PR e deixar a verificação visual como **gate humano**, sem marcar a AC como cumprida.
- [ ] `bun run typecheck`, `bun run lint`, `bun run test:run` e `bun run build` passam.

---

### US-007: Retenção de log declarada e documentada

**Descrição:** Como mantenedor, quero a política de retenção dos dois logs escrita e com teto
de tamanho conhecido, para que a VPS não encha o disco e eu saiba de quanto histórico
disponho ao investigar.

**Critérios de aceite:**
- [ ] `docs/DEPLOY.md` ganha, na seção de segurança operacional (perto do bloco do audit log), a política dos **dois** logs:
  - audit log — `maxFileSize 10MB`, `maxHistory 10`, `totalSizeCap 100MB` (já em `logback-spring.xml`), com o **pior caso em disco** explícito e o caminho do volume (`/var/lib/sessao-a-dois/security-audit-logs`);
  - log da aplicação — `json-file` com `max-size 10m` × `max-file 3` (já em `docker-compose-prod.yml`), pior caso ~30MB, **sem arquivo dedicado** (E12.6), com o motivo escrito.
- [ ] `logback-spring.xml` ganha um comentário curto apontando para essa seção de `docs/DEPLOY.md` (a configuração não muda — ver "O que já está pronto").
- [ ] Fica dito que o log da aplicação **some** no `docker compose down` e que isso é aceito conscientemente: o que precisa sobreviver é o audit log, que está em volume.
- [ ] Nenhuma mudança de comportamento de logging — o formato do console continua idêntico (a razão de `logback-spring.xml` não incluir `base.xml` continua valendo, ver `api/CLAUDE.md`).

---

### US-008: Procedimento de investigação por correlation id

**Descrição:** Como mantenedor, quero um passo a passo que, partindo de um correlation id,
me leve ao que aconteceu, para conseguir investigar um incidente sem redescobrir os comandos.

**Critérios de aceite:**
- [ ] `docs/DEPLOY.md` ganha a seção de investigação, cobrindo os **três** lugares onde o id aparece: header `X-Request-Id` da resposta, log da aplicação (`docker compose logs`) e audit log (o `grep` que já existe em `docs/DEPLOY.md:718`).
- [ ] O passo a passo começa nas duas portas de entrada reais: (a) o id que o usuário leu na tela de erro da US-006, e (b) o `event=client_error` no log, quando ninguém relatou nada.
- [ ] Os comandos estão escritos por extenso e testados (`docker compose logs api | grep 'correlationId=<id>'`, `--since`, etc.), não descritos em prosa.
- [ ] A seção diz o que **não** dá para achar: nada anterior ao último `docker compose down`, nada além do que os tetos da US-007 seguram.
- [ ] **Incidente simulado rastreado ponta a ponta seguindo só a documentação**, e o resultado registrado no PR: provocar um erro de render (ou um `POST /api/client-errors` manual), pegar o id na tela e chegar até a linha de log. Se o ambiente disponível não permitir o ciclo completo, registrar exatamente qual metade foi verificada e qual ficou de gate humano.

---

### US-009: Alerta de falha de deploy por e-mail

**Descrição:** Como mantenedor, quero receber um e-mail quando o deploy falhar, para não
depender de olhar a aba Actions do GitHub.

**Critérios de aceite:**
- [ ] `.github/workflows/deploy.yml` ganha um job **`notify-failure`** com `needs: [tests, deploy]` e `if: failure()`. **Não** um step `if: failure()` dentro do job `deploy`: quando o job `tests` falha, o `deploy` é **skipped**, não failed — um step lá dentro nunca rodaria, e a falha mais comum (CI vermelha) passaria batida.
- [ ] A mensagem identifica **qual job** falhou, lendo `needs.tests.result` e `needs.deploy.result`, e traz o **link da run** (`${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}`), além de branch e commit sha.
- [ ] O envio usa a API do Resend por `curl` (E12.4), reaproveitando o secret `RESEND_API_KEY` e a variable `EMAIL_FROM` que já existem — **nenhum provedor novo, nenhum custo novo**.
- [ ] Destinatário em **secret novo `ALERT_EMAIL_TO`** (é endereço pessoal; fica fora do YAML e da UI de variables). O job não roda o envio se o secret estiver ausente — nesse caso, falha com mensagem explícita em vez de morrer em silêncio dentro de um `curl`.
- [ ] O corpo JSON é montado com `jq -n --arg ...`, nunca por interpolação direta de string — mensagem de commit e nome de branch são conteúdo arbitrário e quebrariam o JSON (ou pior).
- [ ] Nenhum segredo aparece na mensagem nem no log do runner (nada de `.env`, chave SSH, `DB_*`, `JWT_SECRET`).
- [ ] **Sucesso não dispara nada** — verificado numa run verde.
- [ ] **Testado com uma falha forçada e registrado no PR** (ex.: um step temporário com `exit 1`, ou um `workflow_dispatch` a partir de uma branch com o teste quebrado), mostrando que o e-mail chegou e que a mensagem identifica o job certo.
- [ ] `docs/DEPLOY.md` ganha `ALERT_EMAIL_TO` na tabela de secrets, com a nota de que ele **não** entra no `.env` da VPS nem no `docker-compose-prod.yml` — é usado só pelo workflow, e portanto é a exceção à regra dos "três lugares" documentada em `docs/DEPLOY.md:144-151`.

**Nota sobre o Resend:** o remetente precisa do domínio verificado (já está, desde o Épico
10); o destinatário pode ser qualquer endereço. O volume é irrisório perto do free tier
(3.000/mês, 100/dia) — um e-mail por deploy falho.

---

### US-010: Fechar a Open Question #1 do Épico 8

**Descrição:** Como mantenedor, quero a questão em aberto do Épico 8 marcada como resolvida
no lugar onde ela ainda é legível, para o histórico de decisão não ficar pendurado.

**Critérios de aceite:**
- [ ] O registro do fechamento entra em `POST-MVP-TASK.md`, na T12.5, referenciando a US-009 deste PRD e a data.
- [ ] Fica escrito que o PRD original do Épico 8 (`tasks/prd-epico-8-cicd-e-infraestrutura.md`) **não existe mais no repo** — o que sobrou é `scripts/ralph/archive/2026-08-06-epico8-cicd-infraestrutura/` — e que por isso o fechamento foi registrado aqui (E12.8).
- [ ] Nenhum arquivo é recriado só para receber a marcação.

---

### US-011: Seção de monitoramento em `docs/DEPLOY.md`

**Descrição:** Como mantenedor, quero a configuração do monitor externo documentada, para
conseguir refazê-la ou pausá-la sem depender de memória.

**Critérios de aceite:**
- [ ] `docs/DEPLOY.md` ganha a seção **Monitoramento** com: qual serviço, o que é monitorado (`https://sessaoadois.luisgosampaio.com/api/health`), intervalo (5 min), para onde vai o alerta (e-mail, E12.4) e **como pausar durante um deploy planejado** (e como despausar — um monitor esquecido em pausa é pior que nenhum).
- [ ] A seção diz explicitamente que o valor do monitor depende da US-002: antes dela, o endpoint respondia `UP` constante e o monitor seria decorativo.
- [ ] Fica registrado que o alerta de uptime e o de deploy (US-009) chegam pelo mesmo canal, e como distinguir um do outro pelo assunto.
- [ ] Nenhum custo recorrente — o plano usado é free tier.
- [ ] O checklist operacional da US-012 fica escrito nesta mesma seção, em forma de passos, para o mantenedor executar.

---

### US-012 (OPERACIONAL — executada pelo mantenedor): ativar o monitor

**Descrição:** Como mantenedor, quero o monitor externo ativo e comprovadamente funcionando,
para descobrir uma queda por alerta e não por reclamação.

> **Story operacional.** Exige conta no serviço de monitoramento e acesso à VPS de produção;
> **não** entra no `prd.json` do ralph e **não** é executada por agente — mesmo tratamento das
> stories operacionais do Épico 8.

**Critérios de aceite:**
- [ ] Monitor ativo apontando para `https://sessaoadois.luisgosampaio.com/api/health`, intervalo de 5 min, alerta por e-mail configurado e testado (e-mail de teste recebido).
- [ ] **Teste real de queda:** `docker compose stop api` na VPS gera alerta em até 10 min; o horário da parada, o horário do alerta e o retorno ao verde ficam registrados (no PR ou no `progress.txt`).
- [ ] Complementar, agora que a US-002 existe: **derrubar só o banco** (ou apontar o `DB_URL` para um destino inválido momentaneamente) também acende o alerta — é o cenário que o health raso escondia, e é o motivo de a T12.1 depender da T12.2.
- [ ] Nenhum custo recorrente (plano gratuito confirmado no painel).
- [ ] A seção da US-011 é atualizada com o serviço realmente escolhido, se diferente do documentado.

---

## 5. Requisitos funcionais

**Health check (T12.2)**
- **FR-1:** O sistema deve verificar a acessibilidade do banco antes de responder a `GET /api/health`.
- **FR-2:** Quando o banco estiver acessível, `GET /api/health` deve responder `200` com `{"status":"UP","db":"UP"}`.
- **FR-3:** Quando o banco estiver inacessível, `GET /api/health` deve responder `503` com `{"status":"DOWN","db":"DOWN"}` em no máximo ~2 segundos.
- **FR-4:** A resposta de falha não deve conter credencial, host, URL de datasource, nome de usuário nem stack trace.
- **FR-5:** `GET /api/health` deve continuar público (`permitAll`) e continuar isento de CSRF.
- **FR-6:** Nenhum endpoint sob `/actuator/**` deve responder `200` a uma requisição anônima.

**Relatório de erro do cliente (T12.3)**
- **FR-7:** O sistema deve expor `POST /api/client-errors`, público, aceitando `message` (obrigatório), `stack`, `route` e `userAgent`.
- **FR-8:** O sistema deve rejeitar com `400` qualquer campo acima do limite (`message` 500, `stack` 4000, `route` 300, `userAgent` 300), sem ecoar o conteúdo recebido.
- **FR-9:** O sistema deve responder `204` sem corpo em caso de sucesso.
- **FR-10:** O sistema deve gravar o relatório no log do backend em nível `warn`, com o `correlationId` do `CorrelationIdFilter`, a rota e a mensagem, com quebras de linha e caracteres de controle neutralizados.
- **FR-11:** O sistema deve limitar `POST /api/client-errors` a 10 requisições por minuto por IP, respondendo `429` com `Retry-After` no excedente.
- **FR-12:** Quando a árvore React lançar durante o render, o cliente deve exibir uma tela de erro com ação de recarregar, e nunca uma tela em branco.
- **FR-13:** Ao capturar o erro, o cliente deve enviar `message`, `stack` (truncado em 4000) e `pathname` para `POST /api/client-errors`, com o header `X-Request-Id` quando houver um conhecido.
- **FR-14:** O cliente não deve incluir no relatório dado de sessão, e-mail, nome de usuário, conteúdo de `localStorage` ou query string.
- **FR-15:** Uma falha no envio do relatório não deve propagar exceção, disparar refresh de sessão nem redirecionar para `/login`.

**Log (T12.4)**
- **FR-16:** A retenção do audit log deve estar declarada em configuração com teto de tamanho total, e documentada em `docs/DEPLOY.md` com o pior caso em disco.
- **FR-17:** O log da aplicação deve ter limite de tamanho configurado (via `docker logs`), documentado, sem risco de encher o disco da VPS.
- **FR-18:** `docs/DEPLOY.md` deve trazer o passo a passo de investigação a partir de um correlation id, com os comandos por extenso.

**Alerta (T12.1, T12.5)**
- **FR-19:** Uma falha em qualquer job do `deploy.yml` deve disparar um e-mail identificando o job e linkando a run; um deploy bem-sucedido não deve disparar nada.
- **FR-20:** O destinatário do alerta deve vir de secret do repositório, nunca do YAML.
- **FR-21:** Um monitor externo deve consultar `https://sessaoadois.luisgosampaio.com/api/health` a cada 5 minutos e alertar por e-mail em caso de indisponibilidade.
- **FR-22:** Nenhum requisito deste épico pode introduzir custo recorrente.

---

## 6. Não-objetivos (fora do escopo)

Herdado das seções "Fora do escopo" de cada task do épico, mais o que este PRD decidiu não fazer:

- **APM, tracing distribuído, dashboard de métricas, SLO formal.**
- **Checar o TMDB no health check** — dependência externa fora do ar não deve derrubar o container.
- **Métricas de negócio** no endpoint de health.
- **Expor `/actuator/**`** — nem o grupo de health, nem `/actuator/info`, nem `/actuator/metrics`.
- **Sentry ou qualquer SaaS de erro**; **source maps em produção**; **captura global de `unhandledrejection`** (só o error boundary de render).
- **Persistir os relatórios de erro em banco** — dado o volume (~8 usuários), o log do backend é agregação suficiente.
- **ELK, Loki, Grafana**; **log estruturado em JSON**; **envio de log para fora da VPS**; **segundo `RollingFileAppender` para o log da aplicação** (E12.6).
- **Rollback automático de deploy** — segue sendo `git revert` + merge, por decisão do Épico 8.
- **Notificação de deploy bem-sucedido**; **abertura automática de issue**.
- **Backup de banco** — decisão D15, não existe épico de backup.
- **Mudar o comportamento de logging existente** (formato do console, `security.audit`).
- **Qualquer mudança visual fora da tela de erro da US-006.**

---

## 7. Considerações de design (US-006)

A tela de erro é a única UI nova do épico. **A skill `frontend-design` é obrigatória**
(`CLAUDE.md` da raiz); o que segue são os limites dentro dos quais ela decide, não o desenho
pronto.

- **Fonte da verdade visual:** `docs/design/claude-design-project/Sessao a Dois.dc.html`. O
  card de estado vazio do Match ("Fim das sugestões!", linhas 300-307) é a peça mais próxima
  do que esta tela precisa e é o ponto de partida natural: card centrado, `max-width` ~420px,
  fundo `#161513`, borda `rgba(255,255,255,.08)`, raio 22px, padding generoso, ícone grande,
  título, parágrafo curto e um botão primário `#ffcb2b` com texto escuro.
- **Paleta:** fundo `#09090a`, superfície `#161513`, texto `#f6f4ec`, texto secundário
  `#a6a39a`, primária `#ffcb2b`. Cores **inline** (arbitrary values do Tailwind), como o
  resto do `client/` — a T13.2 fará a migração para tokens em massa depois; não antecipar
  aqui, nem inventar token novo.
- **Tipografia:** `font-display` (Bricolage Grotesque) no título, `font-auth-body` (DM Sans)
  no corpo — as duas já definidas em `src/index.css`.
- **Não usar o tratamento destrutivo** (`#ff5c47` e companhia, de `screens/account/`): aquilo
  marca ação que alcança o parceiro. Aqui não houve ação do usuário — o tom é "algo do nosso
  lado quebrou", não "cuidado com o que você vai fazer".
- **Conteúdo:** título curto em pt-BR, uma frase de explicação sem jargão (nada de
  "unhandled exception"), botão primário **Recarregar a página**. O correlation id vai em
  texto pequeno e discreto, com um rótulo que deixe claro para que serve ao relatar o
  problema. Nada de stack trace, nada de mensagem técnica bruta.
- **Independência de contexto:** o boundary fica fora do `ThemeProvider` e do
  `BrowserRouter` — a tela não pode usar `useNavigate`, `Link` nem token de tema. Se houver
  um segundo botão de navegação, ele é um `<a href="/">`, não um `<Link>`.
- **Responsivo:** mesmo padrão dos modais do app —
  `w-[calc(100vw-32px)] max-w-[calc(100vw-32px)]` no mobile, largura fixa a partir de `sm:`.

---

## 8. Considerações técnicas

**Actuator no Spring Boot 4.1 (US-001).** O Boot 4.1 reorganizou artefatos e pacotes de
auto-configuração (foi assim com `spring-boot-security`, ver `api/CLAUDE.md`). Confirme o
pacote real de qualquer classe do Actuator no jar antes de importá-la; a documentação e os
exemplos de Boot 3.x são a principal fonte de erro aqui. O `HealthEndpoint` existe como bean
mesmo com a exposição web fechada — a exposição só controla o mapeamento HTTP, não a
existência do endpoint. É exatamente isso que permite "usar o Actuator sem abrir o Actuator".

**O timeout de 2s não sai de graça do Hikari (US-002).** A checagem de datasource do Actuator
pega uma conexão do pool, e o `connection-timeout` default do Hikari é **30 segundos** — uma
ordem de grandeza acima do orçamento da AC. Baixar essa propriedade **globalmente** para 2s
resolveria o health e criaria um problema muito pior: qualquer contenção momentânea do pool
passaria a falhar requisições reais de usuário. Por isso a espera limitada mora **no
controller** (checagem em executor limitado + espera de 2s + `503` se estourar), e o
`connection-timeout` fica como está. O executor precisa ser limitado e daemon: com o banco
pendurado, o container sonda a cada 30s e o monitor a cada 5min, e threads bloqueadas se
acumulam.

**Três consumidores dependem de `/api/health` (US-002).** `api/Dockerfile:43-44`
(`HEALTHCHECK` com `wget`), o step de smoke test do `deploy.yml` (`curl -fsS`) e
`SecurityConfigTest.healthResponseSetsTheCsrfCookieForTheSpaToRead` (a rota é de onde a SPA
faz o bootstrap do cookie `XSRF-TOKEN`). Mudar shape de resposta ou postura de segurança
quebra três coisas não relacionadas de uma vez — é por isso que a E12.3 mantém o controller
manual como fachada em vez de deixar o Actuator mapear a rota.

**Consequência desejada, mas com efeito colateral (US-002).** Com `503` real, um deploy com o
banco fora passa a **falhar** no smoke test em vez de ser dado como concluído. Isso é o
comportamento correto, mas muda o significado de "deploy verde" e precisa estar explícito no
PR — e, combinado com a US-009, significa que um banco caído durante o deploy gera e-mail.

**Superfície pública nova (US-003/US-004).** `POST /api/client-errors` é o primeiro endpoint
público não-auth que **escreve** (no log) neste app. As quatro defesas — rate limit por IP,
limite de tamanho por campo, sanitização de caracteres de controle e resposta sem eco — são
todas necessárias e nenhuma substitui a outra. Se o volume virar ruído no log em produção,
o ajuste é `app.rate-limit.client-errors`, não remover a checagem.

**`CorrelationIdFilter` já resolve a correlação (US-003/US-005).** Ele gera o id quando o
cliente não manda `X-Request-Id` e devolve o mesmo no header da resposta. O cliente só
precisa lembrar o último id visto; não há mecanismo novo a construir, e não deve nascer um.

**`failure()` em job vs step (US-009).** Um job cujo `needs` falhou fica **skipped**, e todo
step dentro dele — inclusive um com `if: failure()` — não roda. Alertar de dentro do job
`deploy` cobriria só as falhas do próprio deploy e perderia silenciosamente a falha da CI,
que é a mais comum. Daí o job separado com `needs: [tests, deploy]`.

**A regra dos "três lugares" não se aplica ao `ALERT_EMAIL_TO` (US-009).** `docs/DEPLOY.md`
manda toda variável de produção existir no GitHub, no passo que escreve o `.env` e no
`environment:` do compose. Este secret é consumido **só pelo workflow** — não vai para o
`.env` nem para dentro do container. É exceção, e precisa estar dito, senão a próxima pessoa
"conserta" o que não está quebrado.

**Ambiente do agente.** `./mvnw test` roda no devcontainer e **deve** ser executado — nenhuma
mudança de backend é dada como pronta sem ele. Não há `docker` nem `psql` no sandbox, então
o cenário "banco fora" da US-002 se prova com mock/datasource inválido no teste, não
derrubando um Postgres real; e a verificação do `HEALTHCHECK` do container é gate humano. No
`client/`, `bun` se instala com `npm install -g bun` e o diretório **só** pode ser gerenciado
com `bun install`/`bun add` (dois lockfiles versionados divergem em silêncio com `npm`).

---

## 9. Métricas de sucesso

- Derrubar o banco em produção faz o `/api/health` responder `503` em ≤ 2s, o container ficar `unhealthy` e o alerta chegar em ≤ 10 min — hoje nada disso acontece.
- Zero telas brancas: todo erro de render vira tela de erro **e** uma linha `event=client_error` no log do servidor.
- Partindo de um correlation id, chegar à linha de log correspondente usando **só** `docs/DEPLOY.md`, sem consultar código.
- Toda falha de deploy gera exatamente um e-mail; todo deploy verde gera zero.
- Custo recorrente adicional: **R$ 0,00** — verificado nos painéis do Resend e do serviço de uptime.
- `./mvnw test`, `bun run test:run`, `bun run lint`, `bun run typecheck` e `bun run build` verdes; cobertura do frontend não cai abaixo do limiar de `vite.config.ts` (52/37/40/55).

---

## 10. Questões em aberto

1. **Qual serviço de uptime?** UptimeRobot (5 min no free tier, e-mail incluso) é o default óbvio; BetterStack tem free tier menor em número de monitores mas melhor UI. Decisão do mantenedor na US-012 — a US-011 documenta o que for escolhido.
2. **O alerta de uptime e o de deploy vão para o mesmo endereço?** Assumido que sim (E12.4). Se forem endereços diferentes, `ALERT_EMAIL_TO` continua sendo só o do deploy e a US-011 registra o outro.
3. **Vale reduzir também os timeouts do Hikari?** Fora do escopo desta iteração (ver Considerações técnicas). Se um dia o pool virar gargalo real, é task própria, com medição antes.
4. **Volume de `client-errors` em produção.** 10/min por IP é chute conservador para ~8 usuários. Se um bug de render em loop encher o log, o ajuste é a propriedade — mas vale reavaliar o número depois do primeiro incidente real.
5. **Amostragem/deduplicação de relatórios repetidos** (o mesmo erro 200 vezes no mesmo minuto) ficou de fora deliberadamente. Se acontecer, vira task nova — não antecipar.
