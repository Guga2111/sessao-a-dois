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

### Decisões da segunda rodada (2026-08-06) — Épicos 9 a 14

Os Épicos 9–14 vieram de uma varredura de lacunas de **MVP** (o que falta para o app
servir um usuário real), não da auditoria técnica de 2026-08-02. As decisões abaixo
são vinculantes da mesma forma.

| # | Questão | Decisão | Consequência |
|---|---------|---------|--------------|
| D13 | **O que acontece com os dados quando um casal se desfaz** | **Dissolver, não apagar.** O `couples` ganha `dissolved_at`; o casal dissolvido some das consultas, mas `media_track`/`user_review`/`notification` continuam no banco. Ambos ficam livres para formar um casal novo, do zero. | T9.1 precisa de migration e de filtro em **toda** consulta que hoje resolve o casal do usuário. Não há "histórico do relacionamento anterior" na UI — dado preservado ≠ dado acessível. |
| D14 | **E-mail transacional** | **Entra no roadmap** (Épico 10), em provedor com free tier suficiente para ~8 usuários. **Supera a D4**, que dizia "não há infraestrutura de e-mail transacional no roadmap". | Recuperação de senha vira possível. A D4 em si (manter `409` no registro) **continua valendo** — reabrir enumeração de conta não é objetivo do Épico 10, e está no Fora do escopo dele. |
| D15 | **Backup gerenciado do banco** | **Não fazer.** Backup automatizado sai do free tier do Supabase e o custo não se justifica hoje. | **Não existe Épico de backup.** A recuperação de um incidente de dados depende do que o free tier oferecer no momento + do snapshot manual pontual que a `docs/FLYWAY.md` já recomenda antes de deploy com migration. Risco aceito conscientemente: perda de dados é possível e não há RPO definido. Revisitar se o app ganhar usuários fora do círculo conhecido, ou pela via gratuita (`pg_dump` em cron na VPS, custo zero), se um dia virar prioridade. |
| D16 | **Escopo de microsserviços** | **Não agora.** A feature de comunidade, quando vier, é um pacote `com.app.community` dentro do monólito modular, seguindo a regra de dependência da `docs/ARCHITECTURE.md` seção 3. | Nenhum épico de API gateway / extração de serviço. O package-by-feature com portas explícitas já é o que torna a extração barata *depois*, se a escala justificar. |

### Decisões da revisão do Épico 13 (2026-08-13)

| # | Questão | Decisão | Consequência |
|---|---------|---------|--------------|
| D17 | **Destino do tema claro** (T13.3) | **(a) Assumir dark-only.** Remover o `ThemeProvider`, fixar `.dark` no `<html>`, apagar os tokens `:root` claros não usados. | Fecha a Open Question da T13.3. Remoção pura, dispensada da skill `frontend-design`. Entregar tema claro de verdade continua possível depois — a T13.2 (sem hex fixo) é justamente o que torna isso barato no futuro, mas não é objetivo agora. |
| D18 | **Ordem da T13.4** (portão de CI) | **Antecipar para logo depois da T13.1**, antes da migração em massa da T13.2, com allowlist dos arquivos ainda não migrados. | Medido: entre 2026-08-06 e 2026-08-13 o hex cresceu 21% e o utilitário arbitrário 33%, sem portão. Sem antecipar, a T13.2 persegue um alvo móvel. Custo: a regra precisa nascer com allowlist e ir encolhendo, em vez de nascer limpa. |
| D19 | **Verificação visual das stories de UI** | **Gate humano**, não critério automatizável. | O sandbox não tem navegador (limitação já registrada em `client/CLAUDE.md` e nas notas da US-006 do Épico 12). Critérios visuais ficam `- [ ]` com o motivo escrito ao lado, no padrão que este arquivo já usa, e são conferidos pelo mantenedor antes do merge. |
| D20 | **Destino dos 27 raios sem token exato** (T13.6, metade B — US-043) | Decisão **por grupo de valor**, avaliada com a skill `frontend-design` (E13.4): **(a) snapar** `20px`→`rounded-2xl` (18px), `16px`→`rounded-2xl` (18px) e `24px`→`rounded-3xl` (22px) — 15 usos, diferença ≤2px, imperceptível, e a direção do snap (sempre para o vizinho que já é a família visual do `Card`/modal existente) evita introduzir uma segunda "família" de raio grande. **(b) estender a escala** para `12px` (8 usos): novo token `--radius-chip: calc(var(--radius) * 1.2)` (=12px com a base atual), inserido entre `--radius-lg` (×1.0) e `--radius-xl` (×1.4) — mantém pixel-idêntico porque o salto `lg→xl` é 4px, visível nas pills de aviso coral (`PasswordSection`/`DeleteAccountSection`/`CoupleSection`/`ProfileSection`) e no item de notificação onde o valor aparece. Nome semântico (não `--radius-lg2`) porque o uso é consistentemente "chip"/pill, não um raio de superfície genérica. **(c) manter arbitrário com comentário** para os 4 `rounded-[3px]`/`rounded-b-[3px]` (dois quadradinhos de legenda de gráfico de 11×11px e o canto inferior de barra/skeleton de gráfico, todos em `DashboardScreen.tsx`/`ChartSkeleton.tsx`) — snapar dobraria o raio para `sm`=6px num elemento de 11px, mudança visível; é detalhe decorativo de gráfico, não elemento de UI recorrente, então não justifica um token novo. | A recomendação de partida do épico (linha "Recomendação" da T13.6) foi **aceita integralmente**: (a) para `20px`/`16px`/`24px`, (b) para `12px`, e os quatro `3px` avaliados caso a caso confirmaram a hipótese de "detalhe decorativo" (grep confirmou: são exatamente os 4 usos descritos, todos em contexto de gráfico). A implementação da metade B (edição real dos `.tsx` + o novo token em `index.css`) fica para uma story futura — esta é só a decisão, nenhum `.tsx` foi tocado (US-043 não altera código). Verificação visual tela a tela do resultado de (a)/(b), quando implementado, continua sob D19 (gate humano). |

**Ordem de execução:** Épicos 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8, com a **T8.1 (CI)
antecipada** para junto do Épico 1. As dependências declaradas por task são as únicas
restrições rígidas; o resto da ordem é negociável.

**Segunda rodada:** Épicos 9 → 10 → 11 → 12 → 13 → 14. O 9 vem primeiro por ser a
lacuna funcional mais visível para o usuário; o 11 (testes de frontend) vale antecipar
se o Épico 13 for encarado, porque o 13 mexe em arquivo visual em massa. O 14 é de
meia hora e pode entrar em qualquer momento.

## Legenda de criticidade

- **Crítico** — risco ativo em produção (exposição de credencial, degradação sob carga real).
- **Alto** — risco relevante ou dívida que trava evolução.
- **Médio** — qualidade, manutenibilidade, defesa em profundidade.
- **Baixo** — polimento.

## Índice

| Épico | Tema | Tasks | Criticidade máx. |
|-------|------|-------|------------------|
| [1](#épico-1--contenção-de-segurança) | Contenção de Segurança | T1.1 – T1.4 | Crítico |
| [2](#épico-2--fundação-de-performance-de-banco) | Fundação de Performance de Banco | T2.0 – T2.4 | Crítico |
| [3](#épico-3--resiliência-e-latência-do-tmdb) | Resiliência e Latência do TMDB | T3.1 – T3.3 | Alto |
| [4](#épico-4--migração-de-autenticação) | Migração de Autenticação | T4.1 – T4.5 | Alto |
| [5](#épico-5--endurecimento-de-contas-e-auth) | Endurecimento de Contas e Auth | T5.1 – T5.6 | Alto |
| [6](#épico-6--arquitetura-e-tratamento-de-erros) | Arquitetura e Tratamento de Erros | T6.1 – T6.5 | Médio |
| [7](#épico-7--qualidade-do-frontend) | Qualidade do Frontend | T7.1 – T7.3 | Alto |
| [8](#épico-8--cicd-e-infraestrutura) | CI/CD e Infraestrutura | T8.1 – T8.5 | Médio |
| [9](#épico-9--ciclo-de-vida-de-conta-e-casal) | Ciclo de Vida de Conta e Casal | T9.1 – T9.5 | Alto |
| [10](#épico-10--recuperação-de-senha-e-e-mail-transacional) | Recuperação de Senha e E-mail Transacional | T10.1 – T10.4 | Alto |
| [11](#épico-11--rede-de-testes-do-frontend) | Rede de Testes do Frontend | T11.1 – T11.4 | Alto |
| [12](#épico-12--observabilidade-e-alerta) | Observabilidade e Alerta | T12.1 – T12.5 | Médio |
| [13](#épico-13--governança-do-design-system) | Governança do Design System | T13.1 – T13.8 | Médio |
| [14](#épico-14--documentação-de-entrada) | Documentação de Entrada | T14.1 | Baixo |

> **Como uma task concluída é marcada:** linha `**Status:** ✅ **Concluída em <data>** —
> <épico>, <stories> (<branch>)` logo abaixo do título, e os critérios de aceite viram
> `- [x]`. Um critério que **não** pôde ser verificado (ex.: precisa de navegador ou de
> credencial de produção) fica `- [ ]` com o motivo escrito ao lado, em vez de marcado
> por otimismo. Os épicos 1–8 foram entregues antes desta convenção existir e por isso
> não estão marcados; a partir do Épico 9 ela vale.

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

### Extra (achado posterior, mesmo arquivo de config)
O log de `SessaoADoisApplicationTests` mostra a auto-config de usuário padrão do Spring
ativa: `Using generated security password: ...` + `Global AuthenticationManager
configured with UserDetailsService bean with name inMemoryUserDetailsManager`. Como o
app é stateless com JWT e não define `UserDetailsService`, o Spring cria um usuário
in-memory com senha aleatória a cada boot. **Não é explorável hoje** (`formLogin` e
`httpBasic` estão desabilitados em `SecurityConfig:48-49`), mas é ruído no log de
produção e vira risco real se alguém reativar `httpBasic`. Excluir
`UserDetailsServiceAutoConfiguration` ou declarar um `AuthenticationManager` vazio.

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

**Dependências:** T2.0 vem antes da T2.1. T2.3 depende de T2.2 (o mapper extraído é onde o `EntityGraph` passa a ser consumido).

---

## T2.0 — Fazer o CI validar as migrations de verdade

**Criticidade:** Alto
**Descoberta em:** 2026-08-02, no primeiro `./mvnw test` executável do projeto
**Arquivos:** `api/src/test/resources/application.properties`, novo teste em `api/src/test/java/com/app/`, `.github/workflows/`, `api/CLAUDE.md`

### Problema
`api/src/test/resources/application.properties:29-30`:

```properties
spring.flyway.enabled=false
spring.jpa.hibernate.ddl-auto=create-drop
```

**Nenhum teste executa as migrations.** O log de `./mvnw test` confirma: só
`Hibernate: drop table / create table`, zero linhas de Flyway. Consequências:

1. `V1`/`V2` (e qualquer migration futura) nunca rodam fora de produção. Produção é o primeiro ambiente onde a migration é executada de verdade.
2. `ddl-auto=validate` nunca é exercitado — a divergência entidade↔migration, exatamente o que `validate` existe para pegar, passa batida.
3. O comentário do arquivo afirma que os schemas são "equivalentes". Não são. O schema gerado pelo Hibernate nos testes usa `media_type enum ('MOVIE','TV')` onde a migration declara `VARCHAR(255)`, e nomes de FK autogerados (`FKkub7yp2ofpmk5lckeqpv6ly0l`) onde a migration usa nomes explícitos (`fk_media_track_couple`).

Foi essa lacuna que produziu os commits `c78de9f` e `18f9b57` — as duas correções
imediatas ao épico Flyway, ambas sobre incompatibilidade de migration que nenhum teste
podia ter pego.

### O que fazer
1. **Não** reabilitar Flyway globalmente nos testes. O comentário em `application.properties:21-26` documenta corretamente por que isso falhou: `@DataJpaTest` injeta `spring.test.database.replace=ANY` via `PropertyMappingContextCustomizer`, que tem precedência e cria um H2 puro (sem `MODE=PostgreSQL`), incompatível com a sintaxe Postgres das migrations. Manter os slices de `@DataJpaTest` como estão.
2. Criar **uma** classe de teste dedicada — `@SpringBootTest` puro, sem `@DataJpaTest`, logo sem o `PropertyMappingContextCustomizer` — com `@TestPropertySource` declarando explicitamente:
   - `spring.datasource.url=jdbc:h2:mem:migrations;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`
   - `spring.flyway.enabled=true`
   - `spring.jpa.hibernate.ddl-auto=validate`

   Se o contexto sobe, três coisas ficam provadas de uma vez: as migrations aplicam em sequência, produzem um schema válido, e as entidades JPA batem com ele.
3. **Verdade de Postgres real no CI.** O workflow já sobe um `postgres:17-alpine` como service e passa `DB_URL` — mas hoje nenhum teste o usa, porque todos os slices substituem o datasource por H2. Adicionar um step que roda esse teste de migrations contra o Postgres do CI (`@AutoConfigureTestDatabase(replace = NONE)` + `DB_URL` do ambiente). É o único lugar onde uma sintaxe Postgres-only pode ser validada — `docker` não existe no sandbox, então testcontainers segue fora.
4. Corrigir `api/CLAUDE.md`, que hoje afirma *"production and dev/test both go through the same `V*__*.sql` files"* e *"Hibernate is `ddl-auto=validate` everywhere"* — as duas frases são falsas desde `18f9b57`.

### Critérios de aceite
- [ ] Existe um teste que executa `V1`, `V2` (+ futuras) em sequência e falha se qualquer uma não aplicar.
- [ ] Esse teste roda com `ddl-auto=validate` e falha se uma entidade JPA divergir do schema das migrations.
- [ ] Introduzir um erro deliberado numa migration (ex.: coluna com nome errado) faz `./mvnw test` **falhar** — verificar na prática, não presumir.
- [ ] O CI executa esse teste contra o Postgres 17 real que o workflow já sobe.
- [ ] Os demais testes (`@DataJpaTest`, `@SpringBootTest` existentes) continuam usando `create-drop` e passando — 264 testes verdes.
- [ ] `api/CLAUDE.md` descreve o arranjo real: slices em `create-drop`, um teste dedicado validando migrations.

### Fora do escopo
Migrar toda a suíte para Flyway/Postgres. O ganho está em **uma** porta de validação
confiável, não em reescrever 264 testes que hoje funcionam bem.

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
- [ ] `./mvnw test` passa.
- [ ] A aplicação sobe contra Postgres limpo com V1+V2+V3 aplicadas em sequência.
- [ ] `EXPLAIN` de `SELECT * FROM couples WHERE user1_id = ? OR user2_id = ?` usa índice em vez de seq scan.
- [ ] Nenhuma tabela existente é recriada ou alterada.
- [ ] `docs/FLYWAY.md` menciona a ressalva do pooler para este deploy específico.

> ⚠️ **`./mvnw test` NÃO valida esta migration.** `api/src/test/resources/application.properties:29-30`
> define `spring.flyway.enabled=false` + `ddl-auto=create-drop`, então nenhum teste
> executa `V*__*.sql` — a suíte passa igual com a migration correta ou quebrada.
> **Faça a T2.0 antes desta task**, ou o V3 vai para produção sem nunca ter rodado.

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
3. **(Achado posterior)** `MediaTrackController.listByStatus` e `NotificationController.list` devolvem `Page<T>` direto, e o Spring avisa a cada execução: *"Serializing PageImpl instances as-is is not supported, meaning that there is no guarantee about the stability of the resulting JSON structure"*. O cliente espelha `content`/`number`/`totalPages`/`totalElements` à mão (`client/src/types/*.ts`), sem codegen — uma mudança de formato numa atualização do Spring quebra o frontend em silêncio.

### O que fazer
1. Criar `GET /api/tracking/keys` devolvendo `[{mediaType, tmdbId}]` — resposta enxuta, sem reviews nem metadados. `MatchScreen` passa a consumi-lo.
2. **Expandir o `StatsService`** com o que faltar para o `DashboardScreen` (decisão D8) — não construir variante paginada do endpoint completo. `MediaTrackController.stats` (`GET /api/tracking/stats`) já cobre a maior parte, e a orientação de `api/CLAUDE.md` é que uma estatística nova começa por uma agregação nova no repositório, não por consulta de entidades.
3. Reescrever `findPendingForUser` trocando os 3 `NOT IN` por `NOT EXISTS` (mais eficiente com os índices da T2.1 e NULL-safe).
4. **Remover** o endpoint `GET /api/tracking` sem `status` assim que 1 e 2 estiverem no lugar. Com os dois consumidores migrados, ele fica órfão — deletar em vez de deixar depreciado.
5. Estabilizar o formato da resposta paginada: `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` ou um DTO de página próprio. Elimina o warning e congela o contrato com o frontend, que hoje espelha os campos à mão.

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

# Épico 9 — Ciclo de Vida de Conta e Casal

**Objetivo:** fechar o caminho de volta. Hoje conta e casal só têm o caminho de ida —
cria-se, entra-se, e não há como sair, corrigir ou apagar. Este épico entrega
dissolução de casal, edição de perfil, troca de senha e exclusão de conta.

**Origem:** varredura de lacunas de MVP (2026-08-06), lacunas #2 e #3.

**Dependências:** T9.2, T9.3 e T9.4 dependem da T9.1 (todas precisam do conceito de
casal dissolvido). A T9.5 depende das quatro.

---

## T9.1 — Dissolver o vínculo do casal

**Status:** ✅ **Concluída em 2026-08-07** — Épico 9, US-001 a US-004 (`epic9/couple-lifecycle`).
**Criticidade:** Alto
**Arquivos:** `api/src/main/java/com/app/couple/{CoupleController,CoupleService,Couple,CoupleRepository}.java`, `api/src/main/resources/db/migration/V7__add_couple_dissolved_at.sql`, `api/src/main/java/com/app/tracking/MediaTrackService.java`, `api/src/main/java/com/app/match/MatchService.java`

### Problema
`CoupleController` expõe `POST /api/couple`, `GET /api/couple/me`, `POST /api/couple/join`
e `POST /api/couple/invite-code/regenerate`. **Não há saída.** Quem digitou o código de
convite errado e entrou no casal errado fica preso ali para sempre: `joinCouple` lança
`UserAlreadyInCoupleException` em qualquer tentativa de entrar em outro, e não existe
endpoint que desfaça o vínculo. O mesmo vale para um casal que simplesmente termina.

É a lacuna mais visível do domínio central do app — o único caminho de correção hoje é
`UPDATE` manual no banco.

### O que fazer
Conforme a **decisão D13** (dissolver, não apagar):

1. Migration `V7__add_couple_dissolved_at.sql`: `ALTER TABLE couples ADD COLUMN IF NOT
   EXISTS dissolved_at TIMESTAMPTZ NULL;` — seguir o padrão aditivo e idempotente das
   migrations existentes (`IF NOT EXISTS`, sem `DROP`/`TRUNCATE`, ver `docs/FLYWAY.md`).
2. `Couple` ganha `dissolvedAt` + método de domínio `dissolve()`.
3. `CoupleRepository.findByUser1IdOrUser2Id` passa a ignorar casais dissolvidos (novo
   método `findActiveByUserId`, ou cláusula `AND dissolvedAt IS NULL`). **Este é o ponto
   de risco da task:** essa consulta é a porta pela qual o app inteiro descobre o casal
   do usuário (`CoupleService:38,50,68,90`). Um caminho não coberto significa usuário
   dissolvido ainda enxergando dados do casal antigo.
4. `POST /api/couple/leave` (ou `DELETE /api/couple/me`): marca `dissolved_at = now()`,
   registra no `SecurityAuditLogger` (método novo `coupleDissolved`) e responde `204`.
   Ação **unilateral** — qualquer um dos dois dissolve, sem confirmação do parceiro. O
   parceiro descobre na próxima requisição; notificar via STOMP está fora do escopo.
5. Rate limit por usuário reaproveitando a infra da `CoupleService` (chave
   `couple-dissolve:user:<id>`), para o endpoint não virar ferramenta de flood.
6. **Achado colateral a corrigir junto:** `MediaTrackService:64` e `MatchService:57,73,87`
   importam `com.app.couple.CoupleRepository` **diretamente**, o que viola a regra de
   dependência entre features da `docs/ARCHITECTURE.md` seção 3 e é exatamente o
   anti-pattern #2/#5 registrado lá. Como esta task muda a semântica de "casal do
   usuário", é o momento certo: expor uma porta `CoupleFacade` em `com.app.couple` (no
   mesmo espírito do `TrackingFacade`) e fazer `tracking`/`match` dependerem só dela.

### Critérios de aceite
- [x] `DELETE /api/couple/me` dissolve o casal e responde `204`. *(o backlog propunha `POST /api/couple/leave` "ou `DELETE /api/couple/me`" — ficou o segundo)*
- [x] Depois de dissolver, `GET /api/couple/me` responde `404` para **ambos** os usuários.
- [x] Depois de dissolver, ambos conseguem criar um casal novo e entrar num casal novo.
- [x] Nenhum endpoint de `tracking`, `match` ou `notification` retorna dado do casal dissolvido para os ex-membros.
- [x] As linhas de `media_track`, `user_review` e `notification` do casal dissolvido **continuam no banco** (verificar por contagem antes/depois).
- [x] Dissolver um casal inexistente responde `404`, não `500`.
- [x] `tracking` e `match` não importam mais `CoupleRepository`; a dependência passa por uma porta explícita (`CoupleFacade`).
- [x] Testes: `CoupleServiceTest` e `CoupleControllerTest` cobrem dissolver, dissolver duas vezes, dissolver sem casal, e recriar depois de dissolver.

### Fora do escopo
Notificar o parceiro em tempo real; exigir confirmação dos dois lados; qualquer UI de
"histórico de relacionamentos anteriores"; exportação dos dados antes de dissolver.

---

## T9.2 — Editar perfil (nome e e-mail)

**Status:** ✅ **Concluída em 2026-08-07** — Épico 9, US-005 (`epic9/couple-lifecycle`).
**Criticidade:** Médio
**Arquivos:** novos em `api/src/main/java/com/app/user/`, `api/src/main/java/com/app/auth/UserSummary.java`

### Problema
`com.app.user` tem apenas a entidade `User` e o `UserRepository` — **nenhum endpoint**.
Um usuário que digitou o nome errado no cadastro, ou que trocou de e-mail, não tem como
corrigir. O `User` já expõe `setName`/`setEmail`; falta só a camada de cima.

### O que fazer
Criar `UserProfileController` + `UserProfileService` em `com.app.user` (a feature dona
do dado), com `PATCH /api/user/me` aceitando `name` e/ou `email`.

- Validar `email` com `@Email` e unicidade — colidir com e-mail existente responde `409`,
  coerente com a **D4** e com o `EmailAlreadyExistsException` que já existe em `com.app.auth`.
- Trocar o e-mail **não** invalida a sessão (o token carrega `userId`, não e-mail).
- Registrar no `SecurityAuditLogger` (`profileUpdated`), sem logar o valor novo do e-mail.
- Não tocar em senha — isso é a T9.3.
- Respeitar o anti-pattern #1: o controller não injeta `UserRepository`.

### Critérios de aceite
- [x] `PATCH /api/user/me` altera nome, e-mail, ou ambos, e responde com o perfil atualizado (`UserProfileResponse`, E9.15).
- [x] E-mail já usado por outra conta responde `409` (`EmailAlreadyExistsException` reusada de `com.app.auth`, com `UserExceptionHandler` próprio — o advice de `auth` é escopado por `basePackages`).
- [x] E-mail malformado responde `400` pelo `GlobalExceptionHandler`.
- [x] `GET /api/auth/me` reflete o valor novo na requisição seguinte.
- [x] A sessão continua válida depois da troca de e-mail (provado com cookie `access_token` real em `UserProfileIntegrationTest`).
- [x] Testes de service e controller cobrindo sucesso, conflito e payload inválido.

### Fora do escopo
Verificação do e-mail novo por link de confirmação (depende do Épico 10); upload de foto
de perfil; alterar o nome exibido ao parceiro de forma diferente do nome da conta.

---

## T9.3 — Trocar a senha estando autenticado

**Status:** ✅ **Concluída em 2026-08-07** — Épico 9, US-006 (`epic9/couple-lifecycle`).
**Criticidade:** Alto
**Arquivos:** `api/src/main/java/com/app/auth/{AuthController,AuthService}.java`, `api/src/main/java/com/app/auth/RefreshTokenService.java`

### Problema
Não existe troca de senha. Um usuário que suspeita que a senha vazou não tem nenhuma
ação disponível — nem trocar, nem derrubar as sessões ativas. O Épico 4 construiu
refresh token com rotação e `revokeFamily(userId)`, mas nada no app chama isso fora do
logout.

### O que fazer
`PUT /api/auth/password`, exigindo `currentPassword` + `newPassword`:

1. Conferir a senha atual com o mesmo encoder do login; errada → `401`, sem revelar mais nada.
2. Aplicar a **mesma política de senha** da T5.3 no `newPassword` (não duplicar a regra — reusar o validador existente).
3. Salvar o hash novo e chamar `RefreshTokenService.revokeFamily(userId)` — **todas** as sessões caem, inclusive a que fez a troca. É o comportamento correto: se a senha estava comprometida, a sessão do atacante morre junto. O frontend trata isso redirecionando para o login.
4. Limpar os cookies `access_token`/`refresh_token` na resposta, via `AuthCookieService`.
5. Rate limit por usuário e log de auditoria (`passwordChanged`).

### Critérios de aceite
- [x] `PUT /api/auth/password` com senha atual correta troca a senha e responde `204`.
- [x] Senha atual errada responde `401` e **não** troca nada.
- [x] Senha nova fora da política responde `400` com a mesma mensagem da T5.3 (`@Size(min = 8, max = 72)` replicada no `ChangePasswordRequest`, E9.9).
- [x] Depois da troca, o refresh token antigo não funciona mais (`POST /api/auth/refresh` → `401`).
- [x] Depois da troca, o login com a senha nova funciona e com a antiga falha.
- [x] Os cookies de sessão vêm limpos na resposta (E9.8 — **todas** as sessões caem, inclusive a que trocou).
- [x] `AuthServiceTest`/`AuthControllerTest` cobrem os cinco cenários acima, mais `PasswordChangeIntegrationTest` com os cookies reais do login.

### Fora do escopo
Troca de senha sem estar autenticado (é o Épico 10); manter viva a sessão que fez a
troca; notificar por e-mail que a senha mudou (depende do Épico 10).

---

## T9.4 — Excluir a conta

**Status:** ✅ **Concluída em 2026-08-07** — Épico 9, US-007 (`epic9/couple-lifecycle`).
**Criticidade:** Alto
**Arquivos:** novos em `api/src/main/java/com/app/user/`, porta de `com.app.couple`, `api/src/main/java/com/app/tracking/`

### Problema
Não há como excluir a conta. Além de ser o pedido mais básico de privacidade, é
exigência da LGPD (art. 18, eliminação de dado pessoal a pedido do titular). Hoje a
única forma é `DELETE` manual no banco — e o `user_review.user_id` tem FK para `users`,
então nem isso funciona sem cuidado.

### O que fazer
`DELETE /api/user/me`, exigindo a senha atual no corpo (mesma verificação da T9.3 —
exclusão é irreversível e não pode depender só do cookie).

Ordem da operação, em uma transação:

1. Dissolver o casal, se houver — reusar a porta da T9.1, não duplicar a lógica.
2. Apagar os `user_review` do usuário (opinião e nota são dado pessoal dele).
3. Apagar os `refresh_token` do usuário.
4. Apagar os `match_like`/`match_reject` atribuídos a ele.
5. Apagar a linha de `users`.

Os `media_track` **permanecem**: pertencem ao `couple_id`, não ao usuário, e apagá-los
destruiria o histórico do parceiro que não pediu nada. Coerente com a D13.

Definir a ordem respeitando as FKs declaradas em `V1__baseline.sql` (`fk_user_review_user`,
`fk_refresh_token_user`) — a exclusão precisa passar sem violar constraint.

### Critérios de aceite
- [x] `DELETE /api/user/me` com a senha correta apaga a conta e responde `204`.
- [x] Senha errada responde `401` e **nada** é apagado.
- [x] Depois da exclusão, o login com aquele e-mail responde `401` e o e-mail fica livre para um cadastro novo (E9.13).
- [x] O casal foi dissolvido e o ex-parceiro consegue formar um casal novo.
- [x] Os `media_track` do casal continuam no banco; os `user_review` do usuário excluído, não. **Além do previsto na task:** as `notification` em que o usuário é destinatário **ou** ator também são apagadas (E9.2 — o nome do ator aparece na UI do outro membro).
- [x] Nenhuma violação de FK — `UserDeletionIntegrityTest` roda contra o schema das migrations (`replace = NONE`, `${DB_URL}`); **sem `DB_URL` ele cai em H2 silenciosamente**, então a prova contra Postgres de verdade só acontece no CI.
- [x] A exclusão é atômica: falha no meio não deixa conta meio-apagada (`UserDeletionAtomicityTest`, rollback forçado).

### Fora do escopo
Período de carência / "desfazer exclusão" em N dias; exportação dos dados antes de
apagar (portabilidade); anonimização em vez de exclusão.

---

## T9.5 — Tela de Conta no frontend

**Status:** ✅ **Concluída em 2026-08-07** — Épico 9, US-008 a US-013 (`epic9/couple-lifecycle`).
**Criticidade:** Médio
**Arquivos:** `client/src/screens/` (nova tela), `client/src/App.tsx`, `client/src/stores/useAuthStore.ts`, `client/src/lib/api.ts`

### Problema
As T9.1–T9.4 não existem para o usuário sem UI. Hoje o app não tem nenhuma tela de
configurações — `App.tsx` roteia landing, login, register, join, hub, match e dashboard,
e nada mais.

### O que fazer
Rota protegida `/conta`, alcançável pelo `Header`, com quatro blocos:

1. **Perfil** — nome e e-mail editáveis (T9.2).
2. **Senha** — senha atual + nova (T9.3), com redirect para `/login` no sucesso, já que todas as sessões caem.
3. **Casal** — mostra o parceiro e o botão de desfazer o vínculo (T9.1), atrás de confirmação explícita que diga o que acontece com o histórico.
4. **Excluir conta** — zona destrutiva, exige senha e confirmação por digitação (T9.4).

**Esta task usa a skill `frontend-design`** — é UI nova, então a regra do `CLAUDE.md` da
raiz vale integralmente (a dispensa da D11 é só para refatoração sem mudança visual).

Cuidados de coerência com o que já existe: erros vindos da API precisam aparecer na tela
(anti-pattern #4 — nada de `.catch(() => {})`); depois de dissolver o casal, limpar o
`couple` do `useAuthStore` e desconectar o WebSocket, senão a store fica apontando para
um casal que não existe mais.

### Critérios de aceite
- [x] `/conta` é protegida e acessível pelo `Header` — é a única rota autenticada **fora** do `RequireCouple`, porque quem acabou de dissolver o vínculo precisa chegar nela.
- [ ] Os quatro blocos funcionam ponta a ponta contra a API. **Pendente do lado humano:** o sandbox do agente não tem Chromium/Playwright nem backend rodando, então a verificação foi por leitura do caminho de código, não em navegador. Os quatro estão implementados (`screens/account/{ProfileSection,PasswordSection,CoupleSection,DeleteAccountSection}.tsx`) contra os endpoints das T9.1–T9.4.
- [x] Ações destrutivas (dissolver, excluir) exigem confirmação explícita e dizem exatamente o que será perdido (excluir exige a senha atual **e** confirmação por digitação).
- [x] Todo erro da API vira mensagem visível, nunca falha silenciosa — inclusive o `401` de senha errada, que precisou entrar na lista `isPasswordChallenge` de `lib/api.ts` para não ser transformado em logout pelo interceptor de refresh.
- [x] Depois de dissolver, a store e a conexão STOMP são limpas e o app vai para o fluxo de convite (`useAuthStore.dissolveCouple`).
- [x] `bun run typecheck`, `bun run lint` e `bun run build` passam.
- [x] A skill `frontend-design` foi invocada antes da implementação.

**Extra entregue além da task (E9.10):** o ex-parceiro, que descobriria o vínculo
desfeito sem nenhuma explicação, ganha um aviso estático em `/join` (US-013).

### Fora do escopo
Preferências (tema, idioma, notificações); avatar; qualquer redesenho de tela existente.

---

# Épico 10 — Recuperação de Senha e E-mail Transacional

**Objetivo:** eliminar o único caminho hoje sem volta — esquecer a senha significa
perder a conta e todo o histórico do casal.

**Origem:** varredura de lacunas de MVP (2026-08-06), lacuna #1.

**Dependências:** T10.2 depende da T10.1. A T10.3 depende da T10.2. Todo o épico se
apoia na política de senha da T5.3 e no `RefreshTokenService` do Épico 4.

**Decisão habilitante:** **D14** — e-mail transacional entra no roadmap, superando a
parte da D4 que dizia o contrário.

---

## T10.1 — Infraestrutura de e-mail

**Status:** ✅ **Concluída em 2026-08-11** — Épico 10, US-001 a US-003, US-013 (`task10/emails-verification`).
**Criticidade:** Alto
**Arquivos:** novo pacote `api/src/main/java/com/app/email/`, `api/src/main/resources/application.properties`, `docker-compose-prod.yml`, `docs/DEPLOY.md`

### Problema
Não há nenhuma forma de o backend alcançar o usuário fora da sessão HTTP. Isso bloqueou
a recuperação de senha, empurrou a D4 para "manter o 409" e deixa qualquer aviso de
segurança (senha alterada, login novo) impossível.

### O que fazer
Criar `com.app.email` como feature nova, seguindo package-by-feature:

- Interface `EmailSender` — a **porta** que as outras features usam (`sendPasswordReset(...)`). Nenhuma feature fora daqui conhece o provedor.
- Implementação sobre um provedor com free tier compatível com ~8 usuários (Resend, Brevo ou equivalente — decidir na task e **registrar a escolha e o limite do plano** no `docs/DEPLOY.md`). Nada de SMTP do Gmail com senha de app.
- Fail-fast da credencial no startup, no mesmo padrão de `TmdbConfig:31-34` e da T1.1: sem API key em produção, a aplicação não sobe.
- Implementação **no-op que loga** para dev/test, selecionada por property — o devcontainer não pode depender de rede para rodar a suíte.
- A chave entra no `.env` da VPS e no `docker-compose-prod.yml` como os outros segredos, nunca versionada.

### Critérios de aceite
- [x] `EmailSender` é a única superfície pública da feature; nenhuma outra feature importa classe do provedor.
- [x] Subir em produção sem a API key falha no startup com mensagem em pt-BR nomeando a variável.
- [x] Em teste/dev a implementação no-op é usada e a suíte roda sem rede.
- [ ] Um e-mail real chega à caixa de entrada em um teste manual documentado no PR — não verificável por agente (exige credencial real da conta Resend e domínio verificado no DNS); gate humano, ver `docs/DEPLOY.md`.
- [x] `docs/DEPLOY.md` registra provedor, variável de ambiente, limite do free tier e o que fazer se estourar.
- [x] Nenhuma credencial no repositório.

### Fora do escopo
Templates elaborados em HTML; fila/retry de envio; e-mail de boas-vindas ou marketing;
verificação de e-mail no cadastro.

---

## T10.2 — Fluxo de "esqueci minha senha"

**Status:** ✅ **Concluída em 2026-08-11** — Épico 10, US-004 a US-009 (`task10/emails-verification`).
**Criticidade:** Alto
**Arquivos:** `api/src/main/java/com/app/auth/`, `api/src/main/resources/db/migration/V8__create_password_reset_token.sql`, `api/src/main/java/com/app/security/RateLimitProperties.java`

### Problema
`AuthController` tem register, login, refresh, logout e me. Sem recuperação, senha
esquecida = conta perdida.

### O que fazer
Modelar o token de reset **com o mesmo rigor do refresh token** (Épico 4), não como um
UUID solto:

1. Migration `V8__create_password_reset_token.sql`, aditiva e idempotente: `id`, `user_id` (FK), `token_hash`, `expires_at`, `used_at`, `created_at`, índice por `token_hash`. **Guardar apenas o hash**, como o `refresh_token` faz.
2. `POST /api/auth/forgot-password` — recebe o e-mail, responde **sempre `202`**, independente de a conta existir. Isso não conflita com a D4: ali o oráculo aceito é o `409` do registro; aqui não há motivo para criar um segundo.
3. Token de **uso único**, TTL curto (30 min), invalidado no uso e na emissão de um novo.
4. `POST /api/auth/reset-password` — token + senha nova. Aplica a política da T5.3, salva o hash, marca `used_at` e chama `revokeFamily(userId)`, derrubando todas as sessões.
5. Rate limit em **duas dimensões**: por IP e por e-mail alvo, seguindo o padrão `login`/`loginByEmail` que já existe em `RateLimitProperties:19-24` e nas properties `app.rate-limit.*`.
6. Log de auditoria para pedido e conclusão do reset (sem o token, sem o e-mail completo).
7. Limpeza dos tokens expirados, no mesmo molde do `NotificationCleanupService`.

### Critérios de aceite
- [x] `POST /api/auth/forgot-password` responde `202` para e-mail existente e inexistente, com tempo de resposta equivalente.
- [x] O e-mail chega com link contendo o token em texto claro; o banco guarda só o hash.
- [x] Token válido redefine a senha; token usado, expirado ou adulterado responde `400`, sem distinguir os casos.
- [x] Depois do reset, todas as sessões anteriores estão revogadas.
- [x] Senha nova fora da política responde `400` com a mensagem da T5.3.
- [x] Rate limit por IP e por e-mail alvo funcionando, com teste.
- [x] Testes cobrindo: fluxo feliz, token expirado, token reusado, e-mail inexistente, política violada.

### Fora do escopo
Reabrir a D4 (enumeração no registro); verificação de e-mail no cadastro; 2FA;
"magic link" como forma de login.

---

## T10.3 — Telas de recuperação no frontend

**Status:** ✅ **Concluída em 2026-08-11** — Épico 10, US-011 e US-012 (`task10/emails-verification`).
**Criticidade:** Médio
**Arquivos:** `client/src/routes/auth/`, `client/src/App.tsx`

### Problema
O fluxo da T10.2 não existe para o usuário sem tela. `LoginPage` também não tem link
nenhum apontando para recuperação.

### O que fazer
Duas rotas públicas, no `AuthLayout` já existente, reaproveitando o visual de
`LoginPage`/`RegisterPage`:

- `/esqueci-senha` — campo de e-mail, e confirmação genérica ("se existir uma conta com esse e-mail, enviamos as instruções"), coerente com o `202` do backend.
- `/redefinir-senha?token=...` — senha nova + confirmação, com os requisitos de senha visíveis **antes** do submit, e redirect para `/login` no sucesso.
- Link "Esqueci minha senha" na `LoginPage`.

**Esta task usa a skill `frontend-design`** (telas novas).

### Critérios de aceite
- [x] As duas rotas existem, são públicas e usam o `AuthLayout`.
- [x] A confirmação do pedido é genérica e não revela se a conta existe.
- [x] Token ausente ou inválido na URL mostra estado de erro claro, com caminho para pedir outro.
- [x] Erros da API viram mensagem visível.
- [x] `bun run typecheck`, `bun run lint` e `bun run build` passam (sandbox sem `bun`: `npx tsc -b`/`npx eslint .`/`npx vite build` sobre o mesmo `node_modules`, ver `scripts/ralph/progress.txt`).
- [x] A skill `frontend-design` foi invocada antes da implementação.
- [ ] Verificação manual em navegador real — não verificável por agente (sandbox sem Chromium, ver `client/CLAUDE.md`); gate humano.

### Fora do escopo
Medidor de força de senha; mudar o visual das telas de login/cadastro existentes.

---

## T10.4 — Aviso de segurança por e-mail

**Status:** ✅ **Concluída em 2026-08-11** — Épico 10, US-010 (`task10/emails-verification`).
**Criticidade:** Baixo
**Arquivos:** `api/src/main/java/com/app/auth/AuthService.java`, `api/src/main/java/com/app/email/`

### Problema
Com a T9.3 e a T10.2, a senha pode mudar por dois caminhos. Se a mudança não foi o
titular, ele não fica sabendo por lugar nenhum.

### O que fazer
Disparar e-mail informativo (não acionável, sem link de "reverter") quando a senha for
alterada, pelos dois caminhos. Falha no envio **não** pode derrubar a operação — a senha
já mudou; logar em `warn` e seguir.

### Critérios de aceite
- [x] Troca autenticada (T9.3) e reset (T10.2) disparam o aviso.
- [x] Provedor de e-mail fora do ar não faz a troca de senha falhar; o erro aparece no log com contexto.
- [x] O e-mail não contém senha, token nem link de ação.
- [x] Teste garantindo que a falha de envio é tolerada.

### Fora do escopo
Avisos de login em dispositivo novo; digest de atividade; preferências de notificação.

---

# Épico 11 — Rede de Testes do Frontend

**Objetivo:** dar ao `client/` a mesma rede de regressão que o `api/` já tem, e fechar
o non-goal que o PRD do Épico 8 deixou explicitamente em aberto ("Testes no frontend —
é épico próprio, não um item de CI/CD").

**Origem:** varredura de lacunas de MVP (2026-08-06), lacuna #4.

**Dependências:** T11.2, T11.3 e T11.4 dependem da T11.1.

---

## T11.1 — Infraestrutura de teste

**Status:** ✅ **Concluída em 2026-08-11** — Épico 11, US-001, US-002 (`epico11/frontend-tests`).
**Criticidade:** Alto
**Arquivos:** `client/package.json`, `client/vite.config.ts`, novo `client/src/test/setup.ts`, `client/CLAUDE.md`

### Problema
`client/package.json` tem `dev`, `build`, `lint`, `format`, `typecheck` e `preview` —
**nenhum `test`**, nenhuma dependência de teste. O `api/` tem 48 arquivos de teste; o
`client/` tem zero. O Épico 7 acabou de fazer as refatorações mais invasivas do projeto
(decompor `HubScreen`, `SearchTab`, corrigir vazamentos da `useMatchStore`) sem nenhuma
verificação automática de que o comportamento se manteve.

### O que fazer
Vitest + `@testing-library/react` + `@testing-library/user-event` + `jsdom`,
configurados dentro do `vite.config.ts` já existente (sem arquivo de config separado).

- Scripts `test` (watch) e `test:run` (CI, sem watch).
- `setup.ts` com `@testing-library/jest-dom` e limpeza entre testes.
- Um teste-canário trivial provando que a infra roda.
- Registrar em `client/CLAUDE.md` como rodar e onde ficam os testes — é a convenção que as tasks seguintes vão seguir.
- Decidir e registrar a estratégia de mock de HTTP: `axios` é o cliente (`client/src/lib/api.ts`), então mockar o módulo já resolve. MSW só se a T11.3 provar que é necessário — não introduzir a dependência antes.

### Critérios de aceite
- [x] `bun run test:run` executa e passa localmente.
- [x] `bun run typecheck` continua passando com os tipos de teste incluídos.
- [x] O teste-canário roda em ambiente jsdom com um componente React real. _(`src/test/canary.test.tsx`)_
- [x] `client/CLAUDE.md` documenta comando, localização e convenção de nome dos testes.
- [x] Nenhuma mudança de comportamento no app.

### Fora do escopo
Teste E2E (Playwright/Cypress); teste de regressão visual; cobertura mínima (é a T11.4).

---

## T11.2 — Testes das stores e hooks

**Status:** ✅ **Concluída em 2026-08-12** — Épico 11, US-003 a US-009 (`epico11/frontend-tests`).
**Criticidade:** Alto
**Arquivos:** testes novos para `client/src/stores/{useAuthStore,useMatchStore,useNotificationStore}.ts`, `client/src/screens/match/useDiscoverSearch.ts`, `client/src/lib/useCompareSelection.ts`

### Problema
É onde mora a lógica de verdade e onde os bugs do Épico 7 apareceram: vazamento de
subscription na `useMatchStore`, `celebratedMatchKeys` sem limpeza, assimetria de
subscription STOMP. Todos foram corrigidos **sem teste** — nada impede a regressão.

### O que fazer
Priorizar por risco, não por cobertura:

1. `useMatchStore` — `connect`/`disconnect` idempotentes, subscription liberada no disconnect, `celebratedMatchKeys` sem crescimento ilimitado. São exatamente os bugs da T7.3.
2. `useAuthStore` — bootstrap via `GET /api/auth/me`, sessão ausente, e o cache de UI em `localStorage` não sendo tratado como credencial.
3. `useDiscoverSearch` — as transições do `useReducer` introduzido na T7 (busca, filtro, paginação, erro).
4. `useCompareSelection` — limites de seleção e limpeza.
5. `useNotificationStore` — contagem de não-lidas e marcar-como-lida.

### Critérios de aceite
- [x] Cada um dos 5 módulos tem teste cobrindo o caminho feliz **e** o caso de erro. — `useMatchStore`, `useAuthStore`, `useNotificationStore` e `useCompareSelection` cobertos (US-003/004/005/006/009). `useDiscoverSearch` (US-007/US-008) exigiu duas exceções autorizadas pelo mantenedor em 2026-08-12: (a) exportar `reducer`/`initialState`/`State`/`Action`, mudança puramente aditiva; (b) reescrever a AC "erro não limpa `results` anteriores" para o comportamento real do `reducer` (`FETCH_FAILED` zera `results`/`totalResults`/`totalPages` incondicionalmente — a UI decide o que mostrar via `fetchError`/`searched`). Nenhuma mudança de comportamento em produção. Ver `scripts/ralph/progress.txt` (seções US-007/US-008) e `scripts/ralph/prd.json`.
- [x] Existe teste que falharia se o vazamento de subscription da T7.3 voltasse. _(`src/stores/useMatchStore.test.ts`, US-003)_
- [x] Existe teste que falharia se `celebratedMatchKeys` voltasse a crescer sem limite. _(idem)_
- [x] Nenhum teste depende de rede real.
- [x] `bun run test:run` verde.

### Fora do escopo
Testar componentes (é a T11.3); testar o servidor STOMP de verdade.

---

## T11.3 — Testes de componente das telas críticas

**Status:** ✅ **Concluída em 2026-08-11** — Épico 11, US-011 a US-015 (`epico11/frontend-tests`).
**Criticidade:** Médio
**Arquivos:** testes para `client/src/screens/hub/`, `client/src/screens/match/`, `client/src/routes/guards.tsx`

### Problema
As telas decompostas no Épico 7 (`HubScreen` → `screens/hub/`, `MatchScreen` →
`screens/match/`) e os guards de rota concentram o comportamento visível do app e não
têm nenhuma verificação.

### O que fazer
Testes de comportamento observável pelo usuário, não de detalhe de implementação:

- `guards.tsx` — `ProtectedRoute`, `PublicOnlyRoute`, `RequireCouple`, `RedirectIfCoupled`: cada um redireciona para onde deve, em cada estado de sessão.
- `TrackSection`/`HubScreen` — listas vazia, carregando e com erro (o Épico 7 tornou a falha da fila de pendentes visível na US-009; garantir que continua).
- `SearchTab` — o `switch` que substituiu a cadeia de condicionais na T7, com todos os estados.

### Critérios de aceite
- [x] Os 4 guards têm teste para cada estado de sessão relevante. _(`src/routes/guards.test.tsx`, US-011)_
- [x] Estados de carregando, vazio e **erro** cobertos nas telas listadas. _(`TrackSection`/`HubScreen` US-012/013, `SearchTab`/`SuggestionsTab` US-014/015)_
- [x] Existe teste que falharia se a falha de carregamento voltasse a ser silenciosa. _(`HubScreen.test.tsx`, US-013)_
- [x] Os testes consultam por papel/texto acessível, não por classe CSS — assim o Épico 13 pode mexer em estilo sem quebrá-los.
- [x] `bun run test:run` verde.

### Fora do escopo
Cobrir todos os 14 componentes de `client/src/components/`; testes de landing page.

---

## T11.4 — Gate no CI

**Status:** ✅ **Concluída em 2026-08-11** — Épico 11, US-016, US-017 (`epico11/frontend-tests`).
**Criticidade:** Médio
**Arquivos:** `.github/workflows/ci.yml`

### Problema
O job `frontend` do `ci.yml` roda `typecheck`, `lint` e `build`. Depois das T11.1–T11.3
haverá testes que ninguém executa automaticamente.

### O que fazer
Adicionar `bun run test:run` ao job `frontend`, antes do `build`. Configurar limiar de
cobertura no patamar recém-alcançado, **não** num número aspiracional — mesma abordagem
adotada para o JaCoCo na T8.1.

### Critérios de aceite
- [x] O CI roda os testes do frontend e falha se algum quebrar. _(step `Test` do job `frontend`, entre `Lint` e `Build`, sem `continue-on-error`, US-017)_
- [x] Limiar de cobertura declarado e no patamar atual. _(52/37/40/55 — statements/branches/functions/lines medidos em 2026-08-11, US-016)_
- [x] Um PR com teste quebrado é bloqueado. _(verificado localmente com um teste deliberadamente quebrado — `bun run test:coverage` saiu com código 1 — não há PR real aberto nesta iteração, ver `scripts/ralph/progress.txt` seção US-017)_
- [ ] O tempo total do CI continua aceitável (registrar o antes/depois no PR). — não medido: o agente não executa o workflow real do GitHub Actions no sandbox (sem runner) e nenhum PR foi aberto nesta iteração para comparar os tempos; gate humano no próximo PR real que exercitar o step `Test`.

### Fora do escopo
Subir o limiar de cobertura; badge de cobertura; publicar relatório em serviço externo.

---

# Épico 12 — Observabilidade e Alerta

**Objetivo:** saber que a aplicação caiu antes do usuário avisar, e conseguir investigar
depois com o que ficou registrado.

**Origem:** varredura de lacunas de MVP (2026-08-06), lacuna #5.

**Dependências:** T12.1 depende da T12.2 (monitorar um health check raso é
falso-negativo garantido). O resto é independente.

**Restrição de custo:** tudo aqui cabe em free tier ou em infra que já existe. Coerente
com a **D15**, nenhuma task deste épico introduz custo recorrente.

---

## T12.1 — Uptime check externo

**Status:** ✅ **Concluída em 2026-08-13** — Épico 12, US-011 (`epico12/alert-observability`). *A ativação do monitor em si é a US-012, story operacional fora do `prd.json` do ralph (mesmo tratamento das stories operacionais do Épico 8) — ver critérios abaixo.*
**Criticidade:** Médio
**Arquivos:** `docs/DEPLOY.md`

### Problema
`docs/DEPLOY.md` só oferece investigação manual (`docker compose logs`, `docker ps` via
SSH). Não há nada que perceba a aplicação fora do ar. Se a API cair de madrugada, a
descoberta vem pelo parceiro reclamando no dia seguinte.

### O que fazer
Configurar um monitor externo gratuito (UptimeRobot, BetterStack ou equivalente) sobre
`https://sessaoadois.luisgosampaio.com/api/health`, com alerta por e-mail e/ou Telegram.
Intervalo de 5 min é suficiente.

Task **operacional** — executada pelo mantenedor no painel do serviço, não por um
agente. A entrega em repositório é a documentação.

### Critérios de aceite
- [ ] Monitor ativo, apontando para o health check, com alerta configurado. — não executável por agente: exige conta num serviço de monitoramento externo e acesso ao respectivo painel; gate humano (US-012, ver checklist na seção "Monitoramento" de `docs/DEPLOY.md`).
- [ ] Derrubar a API deliberadamente (`docker compose stop api`) gera alerta em até 10 min — teste feito e registrado. — mesma razão acima; gate humano (US-012).
- [x] `docs/DEPLOY.md` ganha seção de monitoramento: serviço, o que é monitorado, para onde vai o alerta, e como pausar durante deploy planejado. *(US-011)*
- [ ] Nenhum custo recorrente. — não verificável por agente: exige confirmar no painel do serviço de monitoramento escolhido, que ainda não foi ativado; gate humano (US-012).

### Fora do escopo
APM, tracing distribuído, dashboard de métricas, SLO formal.

---

## T12.2 — Health check com profundidade

**Status:** ✅ **Concluída em 2026-08-12** — Épico 12, US-001, US-002 (`epico12/alert-observability`).
**Criticidade:** Médio
**Arquivos:** `api/src/main/java/com/app/HealthController.java`, `api/src/test/java/com/app/HealthControllerTest.java`, `api/Dockerfile`

### Problema
`HealthController:16-18` responde `Map.of("status", "UP")` — uma constante. Ele diz
apenas "o processo Java está de pé e o Tomcat aceita conexão". Se o Postgres cair ou as
credenciais expirarem, o health check continua `200 UP`, o `HEALTHCHECK` do container
(T8.2) continua saudável e o monitor da T12.1 continua verde, com o app 100% quebrado.

### O que fazer
Verificar a dependência crítica antes de responder:

- Um `SELECT 1` com timeout curto (1–2s) contra o `DataSource`. Falhou → `503` com o motivo, sem vazar detalhe de conexão (host, usuário, senha).
- Manter a resposta barata: o endpoint é chamado a cada 30s pelo container e a cada 5 min pelo monitor externo.
- Manter `/api/health` público (`SecurityConfig`), como já é hoje e como a `SecurityConfigTest` afirma.
- Avaliar Spring Boot Actuator: o `pom.xml` **não** o inclui hoje. Se entrar, expor **somente** o grupo de health, sem `/actuator/**` aberto — caso contrário, resolver no controller manual, que é o caminho mais simples.

### Critérios de aceite
- [x] Banco no ar → `200` com status detalhado.
- [x] Banco fora → `503` em no máximo ~2s, sem pendurar a thread.
- [x] A resposta de falha não contém credencial, host nem stack trace.
- [x] `/api/health` continua público e a `SecurityConfigTest` continua passando.
- [x] `HealthControllerTest` cobre os dois cenários.
- [ ] O `HEALTHCHECK` do container passa a refletir o estado real (container fica `unhealthy` com o banco fora). — não verificável por agente: sandbox sem Docker (ver `api/CLAUDE.md`), o cenário "banco fora" foi provado com datasource/indicador falso no `HealthControllerTest`, não derrubando um Postgres real nem observando o `HEALTHCHECK` de um container de verdade; gate humano.

### Fora do escopo
Checar TMDB no health (dependência externa fora do ar não deve derrubar o container);
métricas de negócio.

---

## T12.3 — Erro não tratado no frontend deixa de ser tela branca

**Status:** ✅ **Concluída em 2026-08-12** — Épico 12, US-003 a US-006 (`epico12/alert-observability`).
**Criticidade:** Médio
**Arquivos:** `client/src/main.tsx`, novo componente de error boundary, `api/src/main/java/com/app/`

### Problema
Não existe **nenhum** error boundary no `client/` (busca por `ErrorBoundary` em
`client/src/` não retorna nada). Uma exceção durante o render desmonta a árvore React e
o usuário fica com tela branca, sem mensagem e sem qualquer rastro do lado do servidor.
O Épico 6 tratou os `catch` silenciosos das chamadas de API; o erro de render não foi
coberto.

### O que fazer
1. Error boundary no topo da árvore (`main.tsx`), com tela de erro que ofereça recarregar. **Usa a skill `frontend-design`** — é UI nova, ainda que raramente vista.
2. Endpoint `POST /api/client-errors` que recebe mensagem, stack e o correlation id, e grava no log do backend usando o `CorrelationIdFilter` que já existe. **Sem serviço externo, custo zero** — dado o volume (~8 usuários), o log do backend é agregação suficiente.
3. Proteger o endpoint: rate limit por IP na infra que já existe, limite de tamanho do corpo, e nada de refletir o conteúdo recebido em resposta.

### Critérios de aceite
- [x] Erro de render mostra a tela de erro, não tela branca.
- [x] O erro chega ao log do backend com correlation id, rota e mensagem.
- [x] O endpoint é rate-limited e rejeita corpo acima do limite.
- [x] Nenhum dado sensível (token, e-mail) é enviado no relatório.
- [x] Teste do boundary (renderizar filho que lança) e teste do endpoint.
- [x] A skill `frontend-design` foi invocada para a tela de erro. *(a verificação visual da tela em navegador real ficou de gate humano — sandbox sem Chromium, ver `client/CLAUDE.md` e progress.txt da US-006)*

### Fora do escopo
Sentry ou qualquer SaaS de erro; source maps em produção; captura de `unhandledrejection`
global.

---

## T12.4 — Retenção e consulta de log

**Status:** ✅ **Concluída em 2026-08-12** — Épico 12, US-007, US-008 (`epico12/alert-observability`).
**Criticidade:** Baixo
**Arquivos:** `api/src/main/resources/logback-spring.xml`, `docs/DEPLOY.md`

### Problema
`logback-spring.xml` já configura o `RollingFileAppender` do logger `security.audit`,
montado em volume na VPS (`docs/DEPLOY.md:177-208`). O log **da aplicação**, porém, vive
só no `docker logs`: some no `docker compose down`, não tem retenção definida, e não há
procedimento escrito para investigar um incidente por correlation id.

### O que fazer
- Definir política de retenção explícita para o audit log (tamanho máximo total e histórico em dias) — hoje o rolling não tem teto documentado, e volume cheio na VPS derruba tudo.
- Decidir e registrar se o log da aplicação também vai para arquivo, ou se `docker logs` com `max-size`/`max-file` no compose basta. **Recomendação:** limitar no compose, que é mais simples e evita o risco de disco cheio.
- Documentar o procedimento de investigação em `docs/DEPLOY.md`: dado um correlation id vindo da T12.3, quais comandos rodar.

### Critérios de aceite
- [x] Retenção do audit log declarada em configuração, com teto de tamanho.
- [x] Log da aplicação com limite de tamanho, sem risco de encher o disco.
- [x] `docs/DEPLOY.md` traz o passo a passo de investigação por correlation id.
- [ ] Um incidente simulado é rastreado ponta a ponta seguindo só a documentação. — parcialmente verificável por agente: os passos 2 (log da aplicação) e 3 (audit log, `grep` contra arquivo real) foram rastreados de ponta a ponta contra saída real de teste; o passo 1 (header `X-Request-Id` via `curl` ao vivo contra a aplicação de pé) não, por não haver Docker/Postgres no sandbox (ver `api/CLAUDE.md`) — coberto em vez disso pelo teste unitário pré-existente `CorrelationIdFilterTest`. Detalhe completo em `scripts/ralph/progress.txt`, entrada da US-008; gate humano para o ciclo 100% real contra a VPS.

### Fora do escopo
ELK, Loki, Grafana; log estruturado em JSON; envio de log para fora da VPS.

---

## T12.5 — Alerta de falha de deploy

**Status:** ✅ **Concluída em 2026-08-13** — Épico 12, US-009, US-010 (`epico12/alert-observability`).
**Criticidade:** Baixo
**Arquivos:** `.github/workflows/deploy.yml`

### Problema
Fecha a **Open Question #1** do PRD do Épico 8. Hoje uma falha de deploy só aparece na
aba Actions do GitHub. O e-mail automático do GitHub vai para o autor do commit e é
fácil de perder.

### O que fazer
Step `if: failure()` no final do `deploy.yml` disparando notificação que alcance o
celular (webhook de Telegram é o caminho gratuito e mais direto; o token entra como
secret do repositório). A mensagem precisa dizer qual job falhou e trazer o link da run.

### Critérios de aceite
- [ ] Falha no deploy dispara a notificação; sucesso não dispara nada. — a metade "sucesso não dispara nada" é garantida por construção (`if: failure()` no job `notify-failure`), mas nenhuma das duas metades foi observada numa run real do GitHub Actions — este sandbox não dispara workflows reais; gate humano, passo a passo em `scripts/ralph/progress.txt` (entrada da US-009).
- [x] A mensagem identifica o job e linka a run. *(lê `needs.tests.result`/`needs.deploy.result` e monta o link com `github.server_url`/`github.repository`/`github.run_id`)*
- [x] O token está em secret do repositório, nunca no YAML. *(`RESEND_API_KEY` e `ALERT_EMAIL_TO`, ambos `secrets.*`)*
- [ ] Testado com uma falha forçada, registrada no PR. — não executável por agente: exigiria forçar uma falha numa run real do `deploy.yml`; gate humano, passo a passo em `scripts/ralph/progress.txt` (entrada da US-009).
- [x] A Open Question #1 do PRD do Épico 8 é marcada como fechada, referenciando esta task. *(US-010, ver seção logo abaixo)*

### Fora do escopo
Rollback automático (segue sendo `git revert` + merge, por decisão do Épico 8);
notificação de deploy bem-sucedido; abrir issue automática.

### Fechamento da Open Question #1 do Épico 8 (2026-08-13)
**Resolvida.** Implementada pela US-009 do PRD do Épico 12
(`tasks/prd-epico-12-observabilidade-e-alerta.md`): job `notify-failure` em
`.github/workflows/deploy.yml`, `needs: [tests, deploy]` + `if: failure()`, alerta por
e-mail via Resend (não Telegram — decisão E12.4 do PRD do Épico 12, reaproveitando o
provedor de e-mail já pago zero desde o Épico 10 em vez de introduzir um segundo canal).
O PRD original do Épico 8 (`tasks/prd-epico-8-cicd-e-infraestrutura.md`) **não existe
mais no repo** — a pasta `tasks/` é esvaziada quando um épico fecha, e o que sobrou é
`scripts/ralph/archive/2026-08-06-epico8-cicd-infraestrutura/` (só `prd.json` e
`progress.txt`, sem o texto da Open Question #1). Por isso o fechamento fica registrado
aqui, não lá (decisão E12.8 do PRD do Épico 12).

---

# Épico 13 — Governança do Design System

**Objetivo:** fazer os tokens de design serem a fonte da verdade. Os primitivos já
existem; o que não existe é a regra que impede o app de contorná-los.

**Origem:** varredura de lacunas de MVP (2026-08-06).

**Dependências e ordem:** T13.2 depende da T13.1. A **T13.4 depende só da T13.1 e vem logo
depois dela** (D18) — antes da migração em massa, com allowlist que encolhe. A T13.5 depende
de T13.6, T13.7 e T13.8 (documenta o estado final dos três — fazer por último no épico).
Ordem sugerida: **T13.1 → T13.4 → T13.2 → T13.6 → T13.7 → T13.8 → T13.3 → T13.5**.
**Fortemente
recomendado fazer o Épico 11 antes** — a T13.2 toca dezenas de arquivos visuais e hoje
não há nada que detecte uma quebra. **Dependência satisfeita em 2026-08-11, completa em
2026-08-12:** o Épico 11 (T11.1–T11.4, US-001 a US-018 — as 18 stories) já cobre guards de rota,
`HubScreen`/`TrackSection` e `SearchTab`/`SuggestionsTab` com testes que consultam por
papel/texto acessível, não por classe CSS — a rede de regressão que a T13.2 precisa para
não quebrar em silêncio já existe.

**Revisão de 2026-08-13 — três eixos além de cor:** a auditoria original mediu só cor.
Reaplicando o mesmo método a arredondamento e a duplicação de primitivo, dois dos três
eixos levantados se confirmaram como o mesmo hábito medido de outro ângulo — viram
**T13.6** e **T13.7**, sem dependência de T13.1–T13.5 (são refatoração pura, no mesmo
espírito da T13.2, e podem ser feitas em qualquer ordem entre si e com as tasks de cor).
O terceiro eixo (imports diretos da lib de baixo nível contornando `components/ui/`) foi
investigado e **não é um problema hoje** — `client/package.json` não tem `@radix-ui/*`, os
primitivos embrulham `@base-ui/react`/`react-day-picker`, e nenhum arquivo fora de
`components/ui/` importa essas libs diretamente. Registrado aqui para não precisar
reinvestigar, não vira task. Uma quarta lacuna apareceu durante a investigação e não é
dedup — é ausência: não existe primitivo `Badge`, e 15 arquivos reimplementam "pill" na
mão para preencher esse vazio. Vira **T13.8**, mas é **criação**, não governança de algo
que já existe — por isso aciona a skill `frontend-design` (decisão de design nova:
variantes, cores, tamanho), diferente de todas as outras tasks deste épico.

### O diagnóstico, medido

O `client/` **não** precisa de Button/Card/Input: `client/src/components/ui/` já tem **15**
primitivos, e o `button.tsx` sozinho declara 6 variantes e 8 tamanhos via `cva`.
`index.css` já tem tokens em `oklch`, escala de raio de `sm` a `4xl` e três famílias
tipográficas.

O problema é que **o app quase não usa nada disso**. Fora de `components/ui/`:

| Medida | 2026-08-06 | 2026-08-13 | Δ |
|---|---|---|---|
| Literais hexadecimais em `.tsx` | 689 | **831** | +142 (+21%) |
| Utilitários de cor arbitrários (`bg-[...]`, `text-[...]`, `border-[...]`) | 1033 | **1376** | +343 (+33%) |
| `<button>` cru em vez do componente `Button` | 12 | **13** | +1 |
| Arquivo mais afetado | — | `ComparisonDialog.tsx` (72 arbitrários), `PendingDetailModal.tsx` (71), `MediaCard.tsx` (70) | — |

> **A segunda coluna é o argumento da T13.4, medido.** Em **uma semana** (2026-08-06 →
> 2026-08-13, período dos Épicos 11 e 12) o hex cresceu 21% e o utilitário arbitrário 33%,
> sem que ninguém tenha decidido "vamos usar mais hex" — cada linha nova foi localmente
> razoável. Todas as medidas deste épico excluem `components/ui/` e arquivos de teste.
> **Consequência prática para a execução:** os números aqui são um retrato datado e vão
> continuar subindo enquanto o portão da T13.4 não existir; qualquer task deste épico deve
> **remedir na hora de executar** em vez de confiar nesta tabela, e a T13.4 vale mais cedo
> do que a ordem numérica sugere.

Os hex mais repetidos revelam que a paleta real do app existe — ela só mora
copiada-e-colada dentro de strings de classe, não em token:

| Cor | Ocorrências (2026-08-13) | Papel aparente |
|---|---|---|
| `#ffcb2b` | 182 | primária / destaque |
| `#a6a39a` | 174 | texto secundário |
| `#f6f4ec` | 135 | texto sobre fundo escuro |
| `#161513` | 59 | superfície de card |
| `#09090a` | 49 | fundo |
| `#ff6b6b` | 35 | destrutivo / erro |
| `#ffb3b3` | 23 | destrutivo, variante clara |
| `#ffe08a` | 22 | destaque, variante clara |

Consequência concreta: `main.tsx:11` monta um `ThemeProvider` que suporta
`dark`/`light`/`system` — e **nenhum componente do app consome esse contexto**. Não há
alternador de tema em lugar nenhum, e as telas fixam cores escuras em hex. Um usuário com
sistema em modo claro recebe os primitivos de `ui/` em tokens claros **por cima** de telas
codificadas em escuro.

**O mesmo padrão se repete em arredondamento.** `index.css:57-63` já define uma escala
completa (`--radius-sm` a `--radius-4xl`, derivada de `--radius: 0.625rem`) e o Tailwind 4
já expõe cada uma como utilitário (`rounded-sm`…`rounded-4xl` — confirmado em
`components/ui/card.tsx`, `input.tsx`, `popover.tsx`, que já usam `rounded-2xl`/`rounded-lg`
corretamente). Fora de `ui/`, isso é ignorado:

A escala calculada, com `--radius: 0.625rem` (=16px base do browser → 10px):
`sm` 6px · `md` 8px · `lg` 10px · `xl` 14px · `2xl` 18px · `3xl` 22px · `4xl` 26px.

Fora de `ui/` (e fora de testes) há **87** `rounded-[…]` arbitrários. O ponto decisivo é
que **69% deles batem exato com um token que já existe** — não é uma escala inadequada
sendo contornada por necessidade, é a escala sendo ignorada por desconhecimento:

| Valor | Ocorrências | Token equivalente |
|---|---|---|
| `10px` | 19 | `rounded-lg` — **exato** |
| `22px` | 14 | `rounded-3xl` — **exato** |
| `18px` | 13 | `rounded-2xl` — **exato** |
| `14px` | 12 | `rounded-xl` — **exato** |
| `8px` | 2 | `rounded-md` — **exato** |
| | **60 (69%)** | **subtotal com token exato** |
| `20px` | 11 | sem match — entre `2xl` (18px) e `3xl` (22px) |
| `12px` | 8 | sem match — entre `lg` (10px) e `xl` (14px) |
| `3px` | 4 | sem match — **abaixo** de `sm` (6px) |
| `16px` | 3 | sem match — entre `xl` (14px) e `2xl` (18px) |
| `24px` | 1 | sem match — entre `3xl` (22px) e `4xl` (26px) |
| | **27 (31%)** | **subtotal sem token exato** |

Arquivos mais afetados: `DashboardScreen.tsx` (9), `Header.tsx` (7),
`landing/DashboardPreview.tsx` (6), `skeletons/AppShellSkeleton.tsx` (5) — sobrepõem os da
tabela de cor acima. Não é coincidência: é o mesmo hábito de copiar o valor do protótipo
(`docs/design/claude-design-project/`, que usa px cru em `style=` inline) em vez de mapear
para token, medido por outro eixo.

**E também na reutilização dos primitivos de `ui/`.** Além dos "12 `<button>` crus" já
contados (hoje 13), a mesma varredura encontrou duas categorias que o diagnóstico original
não media:

| Padrão duplicado | Ocorrências | Primitivo que já resolveria |
|---|---|---|
| Modal com backdrop `fixed inset-0` montado na mão | 9 arquivos | `Dialog` (já usado corretamente em `TitleModal`, `ComparisonDialog`, `RatingRequestDialog`) |
| "Pill"/badge (`rounded-full` + `px-*` manual) | 36 usos em 18 arquivos | nenhum — não existe `Badge` em `ui/` |
| Shell de card repetido (`rounded-[18px] border … bg-[#161513]`) | 5 arquivos | `Card` |

Os dois primeiros casos são o mesmo problema dos hex e dos raios — o primitivo existe e é
contornado. O `Badge` é diferente: a lacuna é a ausência do primitivo, não o desuso dele.

---

## T13.1 — Extrair a paleta real para tokens

**Criticidade:** Médio
**Arquivos:** `client/src/index.css`

### Problema
Os tokens de `:root`/`.dark` em `index.css:68-133` são majoritariamente o preset neutro
do shadcn (`oklch(0.145 0 0)`, `oklch(0.97 0 0)`…). A identidade visual do app — o
amarelo `#ffcb2b`, o creme `#f6f4ec`, os cinzas quentes — não está lá. Quem lê o
`index.css` não descobre a cara do produto.

### O que fazer
Mapear a paleta medida acima para os tokens semânticos existentes (`--background`,
`--card`, `--foreground`, `--muted-foreground`, `--primary`, `--destructive`), convertendo
para `oklch` como o resto do arquivo. Adicionar token novo apenas onde nenhum semântico
existente couber — e, nesse caso, comentar o porquê.

**Sem nenhuma mudança visual nesta task**: os componentes ainda usam hex, então redefinir
token não move pixel. É deliberado — a T13.1 pode ir sozinha e ser conferida isoladamente.

### Critérios de aceite
- [ ] As 6+ cores mais frequentes têm token semântico correspondente.
- [ ] Valores em `oklch`, consistentes com o arquivo.
- [ ] Cada token tem comentário dizendo o papel (o que é `--card` neste app).
- [ ] O app está **pixel-idêntico** ao anterior (comparar antes/depois nas telas principais).
- [ ] `bun run build` passa.

### Fora do escopo
Trocar o hex nos componentes (T13.2); redesenhar qualquer coisa; escolher cor nova.

---

## T13.2 — Substituir cor crua por token nos componentes

**Criticidade:** Médio
**Arquivos:** `client/src/components/`, `client/src/screens/`, `client/src/routes/`

### Problema
1033 utilitários arbitrários e 689 hex espalhados por ~30 arquivos. Trocar o amarelo do
produto hoje significa 140 edições manuais, e errar uma passa despercebido.

### O que fazer
Substituição mecânica, **por arquivo**, do maior para o menor volume: `bg-[#161513]` →
`bg-card`, `text-[#a6a39a]` → `text-muted-foreground`, e assim por diante.

- **Refatoração pura, pixel-idêntica** — se algum pixel mudar, o mapeamento da T13.1 está errado; corrigir o token, não o componente. Por ser refatoração sem mudança visual, a **D11** dispensa a skill `frontend-design`.
- Um commit por arquivo (ou por grupo pequeno), para revisão viável e revert cirúrgico.
- Substituir também os **12 `<button>` crus** pelo componente `Button` com a variante equivalente.
- Onde a cor não tiver token correspondente, **parar e voltar à T13.1** em vez de inventar arbitrário novo.

### Critérios de aceite
- [ ] Zero literal hexadecimal fora de `components/ui/` e de SVG decorativo declaradamente ilustrativo.
- [ ] Utilitários de cor arbitrários reduzidos a um punhado justificado caso a caso.
- [ ] Os 12 `<button>` crus viraram `Button`.
- [ ] Nenhuma mudança visual — verificado tela a tela.
- [ ] Os testes do Épico 11 continuam verdes (é o principal motivo de fazer o 11 antes).
- [ ] `bun run typecheck`, `bun run lint` e `bun run build` passam.

### Fora do escopo
Mudar layout, espaçamento ou tipografia; redesenhar componentes; tema claro (T13.3).

---

## T13.3 — Decidir o destino do tema claro

**Criticidade:** Médio
**Arquivos:** `client/src/main.tsx`, `client/src/components/theme-provider.tsx`, `client/src/index.css`

### Problema
`ThemeProvider` está montado em `main.tsx:11`, suporta `dark`/`light`/`system`, tem
`localStorage` e listener de `prefers-color-scheme` — e **nada no app o consome**. Não há
alternador. As telas assumem escuro. É um recurso pela metade que dá trabalho de manter e
não entrega nada ao usuário.

### O que fazer
Escolher **um** caminho e executar até o fim:

**(a) Assumir dark-only.** Remover o `ThemeProvider`, fixar `.dark` no `<html>`, apagar os
tokens `:root` claros não usados. Mais barato, honesto sobre o que o app é hoje.

**(b) Entregar o tema claro de verdade.** Depende da T13.2 estar completa (sem hex fixo,
o tema passa a funcionar quase sozinho); revisar contraste de cada token claro e adicionar
o alternador no `Header` ou na tela de Conta da T9.5.

**DECIDIDO em 2026-08-13 (D17): caminho (a), dark-only.** Remover o `ThemeProvider`, fixar
`.dark` no `<html>`, apagar os tokens `:root` claros não usados. É remoção pura, dispensada
da skill `frontend-design` pela D11.

O caminho (b) continua possível no futuro e fica mais barato depois da T13.2 (sem hex fixo,
o tema passa a funcionar quase sozinho) — mas não é objetivo agora, e reabri-lo exige
registrar o motivo aqui, como manda a regra do topo deste arquivo.

### Critérios de aceite
- [x] A decisão está registrada na tabela de decisões deste arquivo, com justificativa (D17, 2026-08-13).
- [x] Nenhuma referência residual a `theme`/`ThemeProvider` no `client/` (US-059, 2026-08-17).
- [x] Os tokens `:root` claros não utilizados foram removidos de `index.css` (US-059) — `--radius` (a única variável dimensional que vivia em `:root`, não é cor) foi movida para dentro de `.dark`, já que é consumida por `--radius-sm`/`md`/`lg`/etc no bloco `@theme inline`.
- [x] `.dark` fixo no `<html>` (`client/index.html`), e o app renderiza idêntico ao de antes — verificação estática (grep + typecheck/lint/test/build); verificação visual em navegador fica **PENDENTE DE GATE HUMANO** (D19, sem navegador no sandbox).
- [x] `bun run typecheck`, `bun run lint` e `bun run build` passam (US-059).

### Fora do escopo
Temas adicionais; tema por casal; transição animada entre temas.

---

## T13.4 — Barrar a regressão no CI

**Criticidade:** Médio
**Depende de:** T13.1 (os tokens precisam existir para a mensagem de erro poder apontar qual usar)
**Ordem:** **antecipada para logo depois da T13.1** (D18) — antes da migração em massa da T13.2
**Arquivos:** `client/eslint.config.js` ou `.github/workflows/ci.yml`

### Problema
Sem um portão, o hex volta. Foi assim que chegaram 689 — um de cada vez, cada um
justificável isoladamente.

**E continua acontecendo, medido:** entre 2026-08-06 e 2026-08-13 (Épicos 11 e 12) o hex
foi de 689 para **831** e o utilitário arbitrário de 1033 para **1376** — +21% e +33% em
uma semana, sem que ninguém tenha decidido usar mais hex. Por isso a **D18** antecipa esta
task para antes da T13.2: sem o portão, a migração em massa persegue um alvo móvel.

**Consequência da antecipação:** a regra nasce com uma **allowlist** dos arquivos ainda não
migrados (senão o CI fica vermelho no dia 1), e essa lista encolhe a cada arquivo que a
T13.2/T13.6 migram — até sumir. A allowlist é o placar da migração, não uma exceção
permanente: cada entrada nela é dívida declarada, e o PR que esvazia a última linha é o que
fecha a T13.2.

### O que fazer
Regra que falhe quando aparecer literal de cor fora de `components/ui/`. Duas opções, a
mais simples que funcionar:

- ESLint `no-restricted-syntax` sobre literais de string de className, com `overrides` liberando `src/components/ui/**`.
- Ou, se a regra de ESLint ficar frágil, um step de CI com `grep` sobre o padrão, no
  mesmo espírito do gate de `bun audit` da US-005 do Épico 8.

Toda exceção precisa de comentário explicando por quê — mesma disciplina exigida dos
`--ignore` do `bun audit`.

### Critérios de aceite
- [ ] Um `#ffcb2b` novo em `src/screens/` faz o CI falhar.
- [ ] O mesmo hex dentro de `src/components/ui/` não falha.
- [ ] A mensagem de erro diz qual token usar em vez da cor crua.
- [ ] A allowlist inicial contém **exatamente** os arquivos que hoje têm hex/arbitrário — nenhum a mais (um arquivo já limpo entrando na lista viraria porta aberta silenciosa).
- [ ] Um hex novo **em arquivo que está na allowlist** também falha, se o arquivo tiver sido migrado no meio-tempo — ou seja, a lista é conferida contra a realidade, não confiada cegamente.
- [ ] O CI não fica mais lento de forma perceptível.

### Fora do escopo
Lint de espaçamento, tipografia ou ordem de classes (o `prettier-plugin-tailwindcss` já
cobre ordenação).

---

## T13.5 — Documentar o inventário e o critério de uso

**Criticidade:** Baixo
**Depende de:** T13.6, T13.7, T13.8 (documenta o estado final dos três; fazer por último no épico)
**Arquivos:** novo `client/docs/DESIGN-SYSTEM.md`, `client/CLAUDE.md`

### Problema
Mesmo com tokens e lint, falta a parte que só se resolve escrevendo: **quando usar cada
variante**. `Button` tem 6 variantes e 8 tamanhos e nada diz qual usar onde — foi assim
que apareceram 12 `<button>` crus, provavelmente porque era mais fácil que descobrir a
variante certa. O mesmo vale para `Dialog` (T13.7) e para o novo `Badge` (T13.8): sem
critério escrito, o próximo modal ou pill customizado volta a ser reimplementado na mão.

### O que fazer
Documento curto e operacional, não catálogo enfeitado:

- Inventário dos primitivos de `ui/` (**15 hoje** — a contagem de "14" no diagnóstico de 2026-08-06 ficou desatualizada; 16+ depois da T13.8), com o que cada um resolve.
- Para `Button`, `Card`, `Input`, `Dialog` e `Badge`: qual variante em qual situação, com exemplo de uso errado.
- Tabela de tokens de cor (T13.1) **e** da escala de raio (`index.css:57-63`): nome, papel/valor, quando usar.
- A regra: **primitivo antes de elemento cru; token antes de cor ou pixel de raio**. Se nenhum atende, a saída é estender o primitivo, não contornar — inclui explicitamente "não montar `fixed inset-0` na mão: é `Dialog`".
- Link a partir de `client/CLAUDE.md`, para virar contexto obrigatório de quem for mexer em `client/`.

### Critérios de aceite
- [ ] O documento existe e cobre todos os primitivos de `ui/`, incluindo `Dialog` e `Badge`.
- [ ] `Button`, `Card`, `Input`, `Dialog` e `Badge` têm critério explícito de escolha de variante.
- [ ] Tabela de tokens de cor **e** de raio, com papel/valor de cada um.
- [ ] `client/CLAUDE.md` aponta para ele.
- [ ] Nenhum código alterado.

### Fora do escopo
Storybook; site de documentação; catálogo de componentes navegável.

---

## T13.6 — Substituir raio arbitrário pelos tokens de raio já existentes

**Criticidade:** Médio
**Arquivos:** `client/src/components/` (fora de `ui/`), `client/src/screens/`, `client/src/routes/`

### Problema
`index.css:57-63` já define `--radius-sm` a `--radius-4xl` dentro do `@theme inline`, e o
Tailwind já expõe cada um como utilitário (`rounded-sm`…`rounded-4xl`) —
`components/ui/card.tsx`, `input.tsx` e `popover.tsx` já usam a escala corretamente. Fora
de `ui/`, **87** ocorrências de `rounded-[…]` reinventam o valor em pixel, nos mesmos
arquivos já flagrados pela T13.2 para cor — é o hábito de copiar o px cru do protótipo em
vez de mapear para token, medido por outro eixo.

### O que fazer
Esta task tem **duas metades com naturezas diferentes**, e a distinção importa mais que a
substituição em si (ver tabela na abertura do épico):

**Metade A — 60 ocorrências (69%) com token exato.** Substituição mecânica pura, no mesmo
espírito da T13.2, **pixel-idêntica por construção**, dispensada da skill `frontend-design`
pela D11:

`10px`→`rounded-lg` · `14px`→`rounded-xl` · `18px`→`rounded-2xl` · `22px`→`rounded-3xl` · `8px`→`rounded-md`

**Metade B — 27 ocorrências (31%) sem token exato** (`20px`×11, `12px`×8, `3px`×4,
`16px`×3, `24px`×1). Aqui **não existe substituição pixel-idêntica**: snapar para o token
vizinho move o raio em 1–2px (o `3px` move 3px, dobrando o raio para `sm`=6px). Isso é uma
**decisão de design, não refatoração** — assumir o contrário é como a T13.2 acabaria
"corrigindo" o design sem dizer. A saída é escolher **um** caminho e registrar:

- **(a) Snapar para o vizinho mais próximo**, aceitando a diferença de 1–2px como custo de ter uma escala. Barato, e ninguém percebe 1px num raio — mas é mudança visual, então **usa a skill `frontend-design`** para a metade B.
- **(b) Estender a escala** com os degraus que faltam, se o padrão do protótipo mostrar que `12px`/`20px` são intencionais e recorrentes (8 e 11 usos não é ruído). Mantém pixel-idêntico ao custo de uma escala maior.
- **(c) Deixar os 27 como arbitrários**, cada um com comentário justificando — honesto, mas esvazia o portão da T13.4 para raio.

**Recomendação: (a) para `20px`/`16px`/`24px` (15 usos, diferença ≤2px, imperceptível) e
(b) para `12px` (8 usos, e `lg`→`xl` é um salto de 4px, visível em elemento pequeno).** Os
4 `3px` provavelmente são detalhe decorativo (barra/indicador) e merecem olhar caso a caso.
Decidir na execução, com a skill, e registrar a escolha aqui.

**Decisão registrada (US-043, D20 — ver tabela "Decisões da revisão do Épico 13"):**
recomendação de partida aceita integralmente, com a skill `frontend-design` invocada antes
da decisão (E13.4).
- `20px`×11 e `16px`×3 → **(a)** snapar para `rounded-2xl` (18px).
- `24px`×1 (`MatchCelebrationModal`) → **(a)** snapar para `rounded-3xl` (22px).
- `12px`×8 (pills de aviso coral + item de notificação) → **(b)** estender a escala com um
  token novo, `--radius-chip: calc(var(--radius) * 1.2)`, entre `--radius-lg` e
  `--radius-xl`.
- `3px`×4 (confirmados por grep: os dois quadradinhos de legenda de 11×11px e o canto
  inferior de barra/skeleton, todos em `DashboardScreen.tsx`/`ChartSkeleton.tsx`) →
  **(c)** manter arbitrário com comentário — snapar dobraria o raio num elemento de 11px.

Esta story (US-043) só registra a decisão — nenhum `.tsx` foi alterado. A implementação
(edição dos arquivos da metade B + o novo `--radius-chip` em `index.css`) é uma story
futura, que deve citar D20 e reusar exatamente este mapeamento.

Disciplina de execução para as duas metades: um commit por arquivo (ou grupo pequeno),
mesma da T13.2. **Fazer a metade A inteira antes da B** — assim 69% do ganho entra como
refatoração revisável de forma trivial, e a discussão de design fica isolada nos 31%.

### Critérios de aceite
- [ ] Metade A: as 60 ocorrências com token exato foram substituídas, e o diff é comprovadamente pixel-idêntico.
- [ ] Metade B: a decisão (a)/(b)/(c) está registrada neste arquivo, com justificativa, antes de qualquer edição da metade B.
- [ ] Zero `rounded-[…]` fora de `components/ui/`, exceto os que a decisão (c) preservar — cada um com comentário.
- [ ] Se a metade B usou (a) ou (b): verificação visual tela a tela, e a diferença de 1–2px está declarada no PR, não escondida atrás de "sem mudança visual".
- [ ] Os testes do Épico 11 continuam verdes.
- [ ] `bun run typecheck`, `bun run lint` e `bun run build` passam.

### Fora do escopo
Mudar o `--radius` base (move a escala inteira); T13.4 (a regra de lint pode ganhar o mesmo
padrão para raio depois, mas não é criada aqui).

---

## T13.7 — Consolidar modais customizados no primitivo `Dialog`

**Criticidade:** Médio
**Arquivos:** `DeleteTrackDialog.tsx`, `WatchModal.tsx`, `MediaDetailModal.tsx`, `PendingDetailModal.tsx`, `ReviewModal.tsx`, `ErrorBoundary.tsx`, `MatchCelebrationModal.tsx`, `screens/account/CoupleSection.tsx`, `screens/account/DeleteAccountSection.tsx`

### Problema
9 arquivos montam um overlay de modal na mão (`fixed inset-0` + backdrop + fechamento por
Escape/clique-fora reimplementados), enquanto `TitleModal.tsx`, `ComparisonDialog.tsx` e
`RatingRequestDialog.tsx` já usam o primitivo `Dialog` corretamente — provando que ele dá
conta do caso. `screens/account/CoupleSection.tsx:84` tem inclusive um comentário admitindo
a escolha: *"backdrop `fixed inset-0` próprio + Escape"*. Cada reimplementação é uma chance
a mais de esquecer foco preso (focus trap), `aria-modal`, ou o fechamento por Escape que o
primitivo já resolve uma vez.

### O que fazer
Trocar o backdrop/wrapper manual de cada um dos 9 arquivos pelo primitivo `Dialog` de
`components/ui/dialog.tsx`, preservando o conteúdo interno de cada modal.

- **Refatoração pura quando o resultado for pixel-idêntico** — dispensada da skill `frontend-design` pela D11. Se o comportamento de acessibilidade do `Dialog` (focus trap, `aria-modal`, Escape) mudar algo visível hoje ausente (ex.: um dos 9 não fecha com Escape hoje), isso é uma correção de bug, não mudança de design — registrar no PR, não é motivo para acionar a skill.
- **`ErrorBoundary.tsx` é o caso que pode não valer a pena.** É o único da lista que não é modal de fluxo normal, e sim a tela de erro que renderiza justamente quando a árvore React quebrou — trocar por `Dialog` o faz depender de mais contexto React (portal, estado do primitivo) exatamente no momento em que menos se pode confiar nisso. Se a análise confirmar esse risco, **deixá-lo como está e registrar o porquê** é o resultado correto da task, não uma falha: são 8 arquivos migrados e uma exceção documentada.
- Um commit por arquivo.

### Critérios de aceite
- [ ] Os 9 arquivos usam `Dialog` de `components/ui/`, sem `fixed inset-0` próprio — ou, para o que ficar de fora (provavelmente `ErrorBoundary.tsx`), há justificativa escrita no código e no PR.
- [ ] Foco, Escape e clique-fora funcionam em todos, via comportamento do primitivo (não reimplementado).
- [ ] Nenhuma mudança visual não-intencional — verificado tela a tela.
- [ ] `ErrorBoundary.tsx` continua funcionando em cenário de erro simulado (não só no caminho feliz).
- [ ] Os testes do Épico 11 continuam verdes.
- [ ] `bun run typecheck`, `bun run lint` e `bun run build` passam.

### Fora do escopo
Redesenhar o conteúdo interno de qualquer modal; mudar quando cada modal abre/fecha.

---

## T13.8 — Criar o primitivo `Badge` e migrar os usos manuais de "pill"

**Criticidade:** Médio
**Arquivos:** novo `client/src/components/ui/badge.tsx`, 18 arquivos consumidores (remedir na execução — a contagem é de 2026-08-13 e cresce, ver a nota de deriva na abertura do épico)

### Problema
**36 usos em 18 arquivos** reimplementam um "pill" (`rounded-full` + `px-*`/`py-*` manual,
tipicamente com cor de status — assistido, pendente, gênero, filtro ativo) sem primitivo
para reusar. Ao contrário da T13.6 e da T13.7, aqui não há o que consolidar: **o primitivo
não existe**. É a única lacuna dos eixos revisados que é criação, não governança de algo
já disponível.

Nem todos os 36 são o mesmo componente: a amostra mistura **badge de status estático**
(`CoupleSection.tsx:57-58`, pill de "casal dissolvido") com **chip clicável de filtro**
(`FiltersPanel.tsx:136`, `compareUi.tsx:28` — têm `cursor-pointer`, estado ativo/inativo e
`disabled:`) e até um `SelectTrigger` estilizado como pill (`FiltersPanel.tsx:117`). Isso
provavelmente são **dois** primitivos (`Badge` estático e algo como `FilterChip`
interativo), não um com muitas variantes — decidir isso é justamente o trabalho do passo 1.

### O que fazer
Esta task **usa a skill `frontend-design`** — diferente de todo o resto do épico, que é
refatoração pura dispensada pela D11. Criar um componente novo é decisão de design (quais
variantes, quais cores/tokens, quais tamanhos), não move de lugar algo que já existe.

1. Levantar os 15 usos manuais e agrupar por papel visual (ex.: status de tracking, gênero, contagem/badge numérico) — o agrupamento define as variantes necessárias, não o inverso.
2. Com a skill, desenhar `Badge` em `components/ui/badge.tsx`, seguindo o padrão de `cva` já usado em `button.tsx` (variantes + tamanhos), consumindo os tokens de cor da T13.1 e de raio (`rounded-full` provavelmente permanece, mas via token/classe padrão do primitivo, não repetido em cada call site).
3. Migrar os 15 consumidores para o novo primitivo.

### Critérios de aceite
- [ ] `Badge` existe em `components/ui/`, com variantes cobrindo os papéis identificados no passo 1.
- [ ] Os 36 usos manuais em 18 arquivos (ou a contagem real remedida na execução) migraram para o(s) primitivo(s).
- [ ] Nenhum token de cor novo criado sem passar pela T13.1 (reusa os semânticos existentes).
- [ ] `bun run typecheck`, `bun run lint` e `bun run build` passam.

### Fora do escopo
Redesenhar o significado visual de cada status (a task migra a implementação, não decide de novo o que cada cor significa, a menos que a skill `frontend-design` aponte uma inconsistência real entre os usos que justifique unificar).

---

# Épico 14 — Documentação de Entrada

**Objetivo:** o `README.md` da raiz tem **15 bytes**. Quem clona o repositório — outra
pessoa, ou você mesmo daqui a seis meses — não descobre o que é o projeto, como subir,
nem por onde começar a ler.

**Origem:** varredura de lacunas de MVP (2026-08-06), lacuna #7.

**Dependências:** nenhuma.

---

## T14.1 — Escrever o README da raiz

**Criticidade:** Baixo
**Arquivos:** `README.md`

### Problema
`README.md` está praticamente vazio, enquanto `docs/` acumulou material denso e correto
(`ARCHITECTURE.md`, `DEPLOY.md` com 609 linhas, `FLYWAY.md`, `SCHEMA_BASELINE.md`,
`BACKLOG.md`, este arquivo). Falta a porta de entrada que aponta para tudo isso.

### O que fazer
README enxuto, apontando em vez de duplicar:

1. O que é o app, em dois parágrafos.
2. Stack em uma tabela (Java 21 + Spring Boot + Postgres | React + TS + Vite + Bun | Docker + Nginx na VPS).
3. **Como subir local**: `docker compose -f docker-compose-dev.yml up`, `./mvnw spring-boot:run`, `bun install && bun run dev` — com as variáveis de ambiente obrigatórias e o que acontece se faltarem (fail-fast do `JWT_SECRET` e da chave do TMDB).
4. Como rodar os testes de cada lado.
5. Mapa do `docs/`: uma linha por arquivo dizendo quando consultar cada um.
6. Estrutura do repositório em um parágrafo (`api/`, `client/`, `deploy/`, `scripts/`, `tasks/`).

Duplicar conteúdo de `docs/` é o erro a evitar — README que repete documentação
desatualiza primeiro e passa a mentir.

### Critérios de aceite
- [ ] Alguém que nunca viu o projeto sobe o ambiente local seguindo **só** o README.
- [ ] Todo arquivo de `docs/` aparece no mapa com uma frase de quando consultar.
- [ ] Variáveis de ambiente obrigatórias listadas, sem nenhum valor real.
- [ ] Nenhum conteúdo copiado de `docs/` — só referência.
- [ ] Comandos testados de verdade, não escritos de memória.

### Fora do escopo
Badges; screenshots; CONTRIBUTING.md; licença; tradução para inglês.

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

## Achados posteriores à auditoria

Descobertos depois do relatório original, ao executar a suíte pela primeira vez
(2026-08-02, após o devcontainer ganhar JDK 21 + Maven).

| Achado | Origem | Task |
|---|---|---|
| Migrations nunca executadas por nenhum teste; `ddl-auto=validate` nunca exercitado | `spring.flyway.enabled=false` em `api/src/test/resources/application.properties:29` | **T2.0** |
| `PageImpl` serializado direto nos endpoints paginados — formato de wire sem estabilidade garantida entre versões do Spring | Warning `ration$PageModule$WarningLoggingModifier` no log de teste | **T3.3** |
| Auto-config de usuário padrão do Spring ativa (`inMemoryUserDetailsManager` + senha gerada no boot) | `UserDetailsServiceAutoConfiguration` no log de `SessaoADoisApplicationTests` | **T1.1** |

## Rastreabilidade — lacunas de MVP (2026-08-06) → task

Segunda rodada. Origem diferente da auditoria de 2026-08-02: aqui a pergunta não foi
"onde o código está errado", e sim "o que falta para o app servir um usuário real".

| Lacuna | Evidência | Épico / Task |
|---|---|---|
| Sem recuperação de senha | `AuthController` só tem register/login/refresh/logout/me | **Épico 10** (T10.1–T10.4) |
| Sem desfazer o vínculo do casal | `CoupleController` sem sair/dissolver | **T9.1** |
| Sem editar perfil | Nenhum endpoint em `com.app.user` | **T9.2** |
| Sem trocar senha autenticado | `revokeFamily` existente, nunca usado fora do logout | **T9.3** |
| Sem excluir conta (LGPD) | Nenhum endpoint em `com.app.user` | **T9.4** |
| Frontend sem testes | `client/package.json` sem vitest/testing-library; 0 testes contra 48 no `api/` | **Épico 11** (T11.1–T11.4) |
| Sem observabilidade | `docs/DEPLOY.md` só com `docker logs` manual | **Épico 12** (T12.1, T12.4) |
| Health check raso | `HealthController:16-18` retorna constante | **T12.2** |
| Sem error boundary no frontend | Nenhuma ocorrência de `ErrorBoundary` em `client/src/` | **T12.3** |
| Falha de deploy sem alerta | Open Question #1 do PRD do Épico 8 | **T12.5** |
| Design system contornado | 689 hex + 1033 utilitários de cor arbitrários fora de `components/ui/` | **Épico 13** (T13.1–T13.5) |
| Tema claro pela metade | `ThemeProvider` montado em `main.tsx:11` sem nenhum consumidor | **T13.3** |
| `tracking`/`match` importam `CoupleRepository` direto | Viola `docs/ARCHITECTURE.md` §3; `MediaTrackService:64`, `MatchService:57,73,87` | **T9.1** (corrigido junto) |
| README vazio | 15 bytes | **T14.1** |
| Backup do banco | — | **Sem épico**, por decisão **D15** (custo). Risco de perda de dados aceito. |
| Microsserviços / API gateway | — | **Sem épico**, por decisão **D16**. Comunidade vira `com.app.community` no monólito modular. |
