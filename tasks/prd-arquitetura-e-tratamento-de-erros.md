# PRD: Arquitetura e Tratamento de Erros (Épico 6)

> Origem: `POST-MVP-TASK.md` → Épico 6 (T6.1 – T6.5).
> Escopo confirmado com o usuário: épico completo, 5 tasks, uma user story por task.

## 1. Introdução

O backend documenta *package-by-feature* em `docs/ARCHITECTURE.md` §3, mas o código
divergiu em quatro pontos concretos:

1. `ResourceNotFoundException` mora em `com.app.tracking` e é importada por
   `com.app.match` e `com.app.notification` — uma exceção de feature virou a exceção
   global de fato.
2. Existem **seis** `@RestControllerAdvice` com `basePackages`, e quatro deles
   reimplementam `MethodArgumentNotValidException` com corpo idêntico (mais dois que
   reimplementam `RateLimitExceededException` byte a byte). Um bug de formato num deles
   não aparece nos outros. Não há fallback para exceção não tratada — hoje, uma
   `NullPointerException` num controller cai no tratamento default do Spring.
3. `MatchService` escreve direto no `MediaTrackRepository`, repositório de outra
   feature, criando **dois caminhos de criação** de `MediaTrack` com regras diferentes.
4. Controllers injetam `UserRepository` e chamam `findById` direto, e o mapeamento
   `Couple` → `CoupleResponse` está duplicado em dois controllers.

Somam-se a isso `docs/ARCHITECTURE.md` desatualizado (não lista `com.app.auth`,
`com.app.notification` nem `com.app.common`, que existem) e seis `catch` silenciosos
no frontend que escondem falhas do usuário — incluindo like/reject, onde o card sai da
fila **como se a operação tivesse dado certo**.

Este épico não muda nenhum contrato de API. É reorganização estrutural + tratamento de
erro consistente.

### Estado do código na data deste PRD (verificado, não presumido)

O `POST-MVP-TASK.md` foi escrito em 2026-08-02 e alguns achados já foram resolvidos por
épicos posteriores. Confirmado por inspeção:

| Achado original | Estado hoje |
|---|---|
| `com.app.common` não existe (T6.1) | **Já existe**, com `PageResponse` (criado no Épico 3). A T6.1 só adiciona classes a ele. |
| `MatchService:103` — `catch (RuntimeException ignored) {}` (T6.5) | **Já corrigido.** Hoje é `catch (RuntimeException ex)` com `log.warn` contextualizado em `healMetadata`, linha 131. |
| `MediaCard.tsx:93` — `.catch(() => {})` (T6.5) | **Já removido** pela T3.2 (o card não busca mais detalhes na montagem). |
| T6.5 depende da T5.5 (correlation id) | **Satisfeita.** `CorrelationIdFilter` existe e devolve o id no header `X-Request-Id` de toda resposta. |

Os `catch` silenciosos que **sobraram** no frontend são seis:
`MatchScreen.tsx:421` (reject), `:441` (like), `:765` (gêneros), `:776` (chaves de
tracking), `MediaDetailModal.tsx:91` e `PendingDetailModal.tsx:49`.

## 2. Objetivos

- Ter **um** ponto de verdade para cada erro transversal: validação, acesso negado,
  recurso não encontrado, rate limit e falha inesperada.
- Garantir que uma exceção não tratada retorne 500 genérico, sem vazar stacktrace,
  nome de classe ou mensagem interna.
- Restaurar a regra de dependência entre features: nenhuma feature importa classe
  interna de outra, apenas portas explícitas.
- Garantir que criar um `MediaTrack` por match e criá-lo manualmente apliquem as
  **mesmas** regras.
- Nenhum controller injeta repositório.
- `docs/ARCHITECTURE.md` volta a descrever o código que existe — importa mais neste
  repositório porque o agente Ralph o lê como fonte de verdade a cada iteração.
- Nenhuma falha de rede desaparece em silêncio para o usuário.

**Restrição que atravessa todas as stories:** o JSON de erro de todos os endpoints
existentes permanece **idêntico** (`{message}` ou `{message, errors}`), com os mesmos
status HTTP. Os testes de controller existentes passam **sem alteração de asserção** —
esse é o critério objetivo de que nada quebrou.

## 3. User Stories

### US-001: `GlobalExceptionHandler` e exceções transversais em `com.app.common`
**Description:** Como desenvolvedor, quero um único handler para os erros transversais
para que um ajuste de formato de erro valha para a aplicação inteira, e para que uma
exceção não prevista não vaze detalhe interno na resposta.

**Acceptance Criteria:**
- [ ] `ResourceNotFoundException` foi movida de `com.app.tracking` para `com.app.common`; nenhum arquivo em `api/src` importa mais `com.app.tracking.ResourceNotFoundException`.
- [ ] Existe `com.app.common.GlobalExceptionHandler`, anotado com `@RestControllerAdvice` **sem** `basePackages`.
- [ ] O `GlobalExceptionHandler` trata: `MethodArgumentNotValidException` (400, corpo `{message: "dados invalidos", errors: {campo: msg}}`), `AccessDeniedException` (403), `ResourceNotFoundException` (404), `RateLimitExceededException` (429 com header `Retry-After`) e `Exception` (500).
- [ ] O fallback de `Exception` responde `{"message": "erro interno"}` — sem stacktrace, sem nome de classe, sem `ex.getMessage()` — e registra `log.error` com a exceção completa. O correlation id chega ao cliente pelo header `X-Request-Id` que o `CorrelationIdFilter` já emite; **não** é adicionado ao corpo.
- [ ] Existe exatamente **um** `@ExceptionHandler(MethodArgumentNotValidException.class)` em `api/src/main/java` (verificável por `grep -rc`).
- [ ] Existe exatamente **um** `@ExceptionHandler(RateLimitExceededException.class)`.
- [ ] Os advices por feature ficam **apenas** com exceções do próprio domínio: `AuthExceptionHandler` (`EmailAlreadyExistsException`, `InvalidCredentialsException`, `InvalidRefreshTokenException`, `RefreshReuseDetectedException`), `CoupleExceptionHandler` (as 7 de couple), `MatchExceptionHandler` (`TitleAlreadyTrackedException`), `MediaExceptionHandler` (as 5 de media), `TrackingExceptionHandler` (`IllegalArgumentException` de tracking); `NotificationExceptionHandler` fica vazio e **é deletado**.
- [ ] Os testes de controller existentes passam **sem nenhuma alteração de asserção** — status e corpo de erro idênticos aos de hoje.
- [ ] Existe `GlobalExceptionHandlerTest` cobrindo os 5 tipos tratados, incluindo a asserção explícita de que o corpo do 500 não contém a mensagem original da exceção.
- [ ] `./mvnw test` passa.

---

### US-002: `TrackingFacade` para desacoplar `com.app.match`
**Description:** Como desenvolvedor, quero que a criação de `MediaTrack` passe por uma
única porta para que uma regra nova de criação valha automaticamente para os dois
caminhos (manual e por match), em vez de precisar ser lembrada em dois lugares.

**Depende de:** US-001.

**Acceptance Criteria:**
- [ ] Existe `com.app.tracking.TrackingFacade` (componente Spring) expondo a criação de track a partir de um match.
- [ ] `MatchService` **não** importa `MediaTrackRepository`, `MediaTrack` nem `MediaStatus` — depende só do `TrackingFacade` e do DTO que ele devolve/recebe.
- [ ] A entidade criada por `MatchService.createMatch` tem exatamente os mesmos campos preenchidos de hoje: `couple`, `tmdbId`, `mediaType`, `status = WANT_TO_SEE`, `genreIds`, `title`, `posterUrl`, `releaseYear`.
- [ ] O facade não introduz chamada nova ao TMDB: `createMatch` continua fazendo **uma** chamada a `mediaDetailsService.getDetails` e reaproveitando a resposta.
- [ ] `ratingRequestService.onRatingRegistered` continua **não** sendo disparado no caminho de match (o track nasce `WANT_TO_SEE`, não `WATCHED`) — comportamento preservado, não "corrigido".
- [ ] O evento STOMP em `/topic/couple/{id}/match` e a notificação de match continuam sendo emitidos.
- [ ] `MatchServiceTest` passa com o `TrackingFacade` mockado.
- [ ] Existe teste comparando os campos da entidade criada pelos dois caminhos.
- [ ] `./mvnw test` passa.

**Fora do escopo desta story:** mover o `SimpMessagingTemplate.convertAndSend` de
`MatchService` para o padrão after-commit usado por
`NotificationService.scheduleBroadcast`. É melhoria real e documentada, mas é task
própria.

---

### US-003: Tirar repositórios dos controllers
**Description:** Como desenvolvedor, quero que os controllers dependam apenas de
serviços para que a camada de acesso a dados não seja acessível pela borda HTTP, e para
eliminar a duplicação do mapeamento `Couple` → `CoupleResponse`.

**Acceptance Criteria:**
- [ ] `AuthController` e `CoupleController` não injetam `UserRepository`.
- [ ] Nenhum controller em `api/src/main/java` injeta qualquer `*Repository` (verificável por grep).
- [ ] Existe **uma** implementação de `Couple` → `CoupleResponse` (em `CoupleService` ou num `CoupleResponseMapper` dedicado), consumida por ambos os controllers; os dois métodos `toResponse` duplicados foram removidos.
- [ ] O JSON de `POST /api/auth/login`, `GET /api/auth/me`, `GET /api/couple/me`, `POST /api/couple` e `POST /api/couple/join` é idêntico ao anterior, incluindo o caso de casal **sem parceiro** (`partner: null`).
- [ ] `AuthControllerTest` e `CoupleControllerTest` passam sem alteração de asserção.
- [ ] `./mvnw test` passa.

---

### US-004: Sincronizar `docs/ARCHITECTURE.md` com o código
**Description:** Como desenvolvedor (ou agente Ralph, que lê este arquivo como fonte de
verdade a cada iteração), quero que a documentação de arquitetura descreva os pacotes
que realmente existem, para que ela deixe de induzir a decisões erradas.

**Acceptance Criteria:**
- [ ] `docs/ARCHITECTURE.md` §3 lista **todos** os pacotes de `api/src/main/java/com/app/`, cada um com uma linha de responsabilidade. Hoje faltam `com.app.auth`, `com.app.notification` e `com.app.common`; e a descrição de `com.app.user` precisa refletir que ele ficou só com `User` + `UserRepository`.
- [ ] A regra de dependência entre features está escrita explicitamente: features não importam classes internas de outras features, apenas portas explícitas (ex.: `TrackingFacade` da US-002) e tipos de `com.app.common`.
- [ ] Existe uma seção **"Anti-patterns"** com no mínimo 5 itens, cada um com exemplo do que evitar e a alternativa correta: (a) controller acessando repositório, (b) exceção de feature usada como global, (c) `setState` síncrono dentro de `useEffect`, (d) `catch` vazio sem log, (e) criação de entidade fora do serviço dono dela.
- [ ] `api/CLAUDE.md` não contradiz o `ARCHITECTURE.md` atualizado.
- [ ] Nenhuma afirmação nova no documento é falsa — cada pacote e regra citada foi conferida contra o código.

---

### US-005: Eliminar `catch` silenciosos no frontend
**Description:** Como usuário, quero ver quando uma ação falha e poder tentar de novo,
para não achar que curti ou passei um título que na verdade continua pendente.

**Padrão visual:** reaproveitar o que `SearchTab` já usa (`fetchError` + botão "Tentar
novamente", `MatchScreen.tsx:1130-1147`). **Nenhum estado de erro visual novo é
desenhado** — por isso a skill `frontend-design` está dispensada aqui, conforme decidido
com o usuário e alinhado à exceção de refatoração registrada no `CLAUDE.md` da raiz. Se
durante a implementação ficar claro que algum caso precisa de um visual que não existe,
**parar e chamar a skill** antes de inventar.

**Acceptance Criteria:**
- [ ] `grep -rn "catch(() => {})\|catch { *$" client/src` não retorna nenhum `catch` vazio ou sem tratamento visível ao usuário.
- [ ] **Like que falha** (`MatchScreen.tsx:441`): o card **não** sai da fila, uma mensagem de erro aparece e há botão de tentar novamente.
- [ ] **Reject que falha** (`MatchScreen.tsx:421`): mesmo comportamento — o card permanece na fila.
- [ ] Falha ao carregar gêneros (`:765`) e chaves de tracking (`:776`) não quebra a tela: a busca continua utilizável e a falha é registrada em `console.error` com contexto.
- [ ] Falha ao carregar detalhes em `MediaDetailModal.tsx:91` e `PendingDetailModal.tsx:49` mostra estado degradado visível (mensagem no lugar do conteúdo), não um modal vazio silencioso.
- [ ] Nenhuma exceção é engolida no backend sem `log` de nível apropriado e contexto suficiente para investigação (`tmdbId`, `coupleId` etc.) — reauditar `api/src`, já que o caso original de `MatchService` foi corrigido antes deste épico.
- [ ] `bun run typecheck` e `bun run lint` passam.
- [ ] Verificar no navegador com a skill `dev-browser`: forçar falha de rede no like e confirmar que o card permanece na fila com erro e retry.

## 4. Requisitos Funcionais

- **FR-1:** `ResourceNotFoundException` deve residir em `com.app.common`.
- **FR-2:** Deve existir um `@RestControllerAdvice` sem `basePackages` tratando `MethodArgumentNotValidException`, `AccessDeniedException`, `ResourceNotFoundException`, `RateLimitExceededException` e `Exception`.
- **FR-3:** Ao capturar uma `Exception` não prevista, o sistema deve responder 500 com corpo `{"message": "erro interno"}` e registrar a exceção completa em `log.error`.
- **FR-4:** O corpo de resposta de erro não deve conter stacktrace, nome de classe Java, SQL nem mensagem de exceção não tratada.
- **FR-5:** Cada `@RestControllerAdvice` por feature deve tratar somente exceções declaradas no próprio pacote.
- **FR-6:** `com.app.match` não deve importar nenhuma classe de `com.app.tracking` além da porta `TrackingFacade` e seus DTOs.
- **FR-7:** A criação de `MediaTrack` originada de um match deve passar pelo `TrackingFacade`.
- **FR-8:** Nenhum `@RestController` deve injetar um `*Repository`.
- **FR-9:** Deve existir uma única implementação de `Couple` → `CoupleResponse`.
- **FR-10:** `docs/ARCHITECTURE.md` §3 deve listar todos os pacotes existentes e a regra de dependência entre features.
- **FR-11:** `docs/ARCHITECTURE.md` deve conter uma seção de anti-patterns com no mínimo os 5 itens da US-004.
- **FR-12:** Quando `POST /api/match/like` ou `POST /api/match/reject` falhar, o item deve permanecer na fila e a interface deve exibir erro com opção de tentar novamente.
- **FR-13:** Toda falha de rede no cliente deve produzir sinal visível ao usuário ou, no mínimo, `console.error` com contexto — nunca um bloco vazio.

## 5. Não-Objetivos (Fora do Escopo)

- **Mudar qualquer contrato de API.** Status HTTP, formato do corpo de erro e payloads de sucesso permanecem idênticos.
- **Mover o `convertAndSend` de `MatchService` para after-commit.** Melhoria conhecida, task própria.
- **Redesenhar telas ou criar estados visuais novos.** A US-005 reaproveita o padrão de erro existente.
- **Introduzir biblioteca de tratamento de erro (Problem Details / RFC 7807).** Mudaria o contrato — se desejado, vira task própria.
- **Refatorar `MatchScreen.tsx`** (1439 linhas) — isso é o Épico 7 (T7.2). A US-005 encosta no arquivo apenas nos pontos de `catch`.
- **Adicionar testes ao `client/`.** Não há infraestrutura de teste no frontend; isso é a T8.1.
- **Cobrir exceções de WebSocket/STOMP.** `@RestControllerAdvice` só vale para MVC; o canal STOMP tem tratamento próprio e não é alvo deste épico.

## 6. Considerações Técnicas

- **Ordem de execução:** US-001 → US-002 → US-003. US-004 depende conceitualmente de US-001 e US-002 (documenta `com.app.common` e a regra de portas), então deve vir depois. US-005 é independente e pode ser feita em paralelo.
- **Precedência de advices:** um `@RestControllerAdvice` sem `basePackages` e um com `basePackages` competem pelo mesmo tipo de exceção. O Spring escolhe o handler mais específico pelo tipo; para tipos idênticos a ordem é indefinida. Por isso o critério "exatamente um handler por tipo transversal" é obrigatório, não estético. Se algum conflito residual aparecer, usar `@Order` explícito com o global em `Ordered.LOWEST_PRECEDENCE`.
- **`Exception.class` como fallback:** cuidado para não capturar exceções que o Spring Security precisa que subam pela cadeia de filtros. O `@RestControllerAdvice` só atua dentro do `DispatcherServlet`, então `AuthenticationException` lançada no `JwtAuthenticationFilter` não passa por ele — comportamento atual preservado. Confirmar com teste que 401 de token inválido continua sendo 401, não 500.
- **`IllegalArgumentException`:** hoje só o `TrackingExceptionHandler` a trata (400). **Não** promover para o global — Spring e bibliotecas lançam `IllegalArgumentException` em situações que não são erro do cliente, e mapear tudo para 400 mascararia bug como erro de usuário.
- **Correlation id:** `CorrelationIdFilter` já popula o MDC e devolve `X-Request-Id`. O `log.error` do fallback herda o id automaticamente.
- **Nomenclatura pt-BR sem acento** nas mensagens de erro do backend (padrão do código: "casal nao encontrado", "dados invalidos"). Manter.

## 7. Métricas de Sucesso

- Handlers de `MethodArgumentNotValidException`: **4 → 1**.
- Handlers de `RateLimitExceededException`: **2 → 1**.
- `@RestControllerAdvice` no total: **6 → 6** (5 de feature + 1 global; `NotificationExceptionHandler` sai, `GlobalExceptionHandler` entra).
- Controllers injetando repositório: **2 → 0**.
- Implementações de `Couple` → `CoupleResponse`: **2 → 1**.
- `catch` silenciosos em `client/src`: **6 → 0**.
- Exceção não tratada: de tratamento default do Spring → 500 genérico controlado, com log correlacionado.
- Testes existentes: 100% verdes **sem alteração de asserção** (prova de que nenhum contrato mudou).

## 8. Questões em Aberto

1. `NotificationExceptionHandler` fica vazio depois da US-001 e o PRD manda deletá-lo. Confirmar que não há intenção de adicionar exceções específicas de notificação no curto prazo.
2. O `TrackingFacade` deve receber `Couple` (entidade de `com.app.couple`) ou `UUID coupleId`? Passar a entidade mantém a assinatura sugerida no `POST-MVP-TASK.md` e evita um `findById` redundante; passar o UUID desacopla mais. Recomendação: **`Couple`**, porque `MatchService` já o tem carregado em transação — decidir na implementação e registrar no código.
3. A US-005 pede reauditoria de `catch` no backend. Se aparecer um caso que exija mudar comportamento (e não só adicionar log), tratar como achado novo e escalar antes de mudar.
