# PRD: Épico 2 — Autenticação e Gestão de Casais (Domínio User/Couple)

## 1. Introdução / Visão Geral

Este épico entrega a base de identidade do aplicativo **Sessão a Dois**: cadastro e login de usuários individuais, autenticação via JWT (apenas access token, sem refresh token) e o vínculo entre duas contas para formar um **Casal** (`Couple`).

A partir do momento em que duas contas estão vinculadas, todo o rastreamento de mídia do app pertence ao `couple_id`. Portanto, este épico é pré-requisito de todos os épicos seguintes (TMDB, Tracking, Match, Dashboard).

O problema que resolve: hoje não há como um usuário entrar no sistema, se identificar de forma segura, nem se conectar ao parceiro. Sem isso, nenhuma funcionalidade de casal existe.

**Fluxo central do vínculo:**
1. Usuário A se cadastra e faz login.
2. Usuário A gera/possui um `invite_code`.
3. Usuário A compartilha o código com o parceiro (fora do app).
4. Usuário B se cadastra, faz login e insere o `invite_code`.
5. O sistema cria o `Couple` ligando A e B. A partir daí ambos compartilham o mesmo `couple_id`.

## 2. Objetivos

- Permitir que qualquer pessoa crie uma conta individual com nome, e-mail e senha.
- Permitir login que retorna um access token JWT válido por 7 dias.
- Proteger todos os endpoints privados exigindo um JWT válido, deixando públicos apenas registro, login e health.
- Habilitar CORS para o frontend consumir a API.
- Permitir que um usuário gere um `invite_code` e forme um `Couple` ao vincular o parceiro.
- Garantir que o frontend injete o JWT automaticamente em todas as requisições autenticadas.
- Entregar as telas de Login, Cadastro e Inserção de Código de Convite com rotas protegidas.

## 3. User Stories

### US-001: Entidade User e repositório
**Description:** Como desenvolvedor, preciso de uma entidade `User` persistida para armazenar contas individuais.

**Acceptance Criteria:**
- [ ] Criar entidade `User` no pacote `com.app.user` com campos: `id` (UUID gerado), `name`, `email` (único, não nulo), `passwordHash` (não nulo), `createdAt`.
- [ ] Criar `UserRepository` (Spring Data JPA) com método `findByEmail(String email)` e `existsByEmail(String email)`.
- [ ] A coluna `email` possui restrição de unicidade no banco.
- [ ] A tabela é criada corretamente ao subir a aplicação (contra o PostgreSQL do `docker-compose-dev.yml`).
- [ ] Compilação e testes existentes passam (`./mvnw compile` / `./mvnw test`).

### US-002: Endpoint de Registro
**Description:** Como novo usuário, quero criar uma conta com nome, e-mail e senha para acessar o app.

**Acceptance Criteria:**
- [ ] Endpoint `POST /api/auth/register` recebe `{ name, email, password }`.
- [ ] Validação: `name` não vazio; `email` em formato válido; `password` com no mínimo 8 caracteres (usar Bean Validation / `@Valid`).
- [ ] A senha é armazenada com hash usando `BCryptPasswordEncoder` (nunca em texto puro).
- [ ] Se o e-mail já existir, retorna `409 Conflict` com mensagem clara (não vaza se o e-mail existe de forma insegura — mensagem genérica de "e-mail já cadastrado").
- [ ] Em caso de sucesso, retorna `201 Created`. **O registro NÃO retorna JWT** — o token é obtido depois via login (ver US-009, onde o frontend faz o login automaticamente por baixo dos panos).
- [ ] Erros de validação retornam `400 Bad Request` com corpo descrevendo os campos inválidos.
- [ ] Teste unitário/integração cobre: registro com sucesso, e-mail duplicado, senha curta, e-mail inválido.

### US-003: Endpoint de Login e geração de JWT
**Description:** Como usuário cadastrado, quero fazer login e receber um token para autenticar minhas requisições.

**Acceptance Criteria:**
- [ ] Endpoint `POST /api/auth/login` recebe `{ email, password }`.
- [ ] Valida a senha comparando com o hash BCrypt armazenado.
- [ ] Em caso de sucesso, retorna `200 OK` com `{ token, user: { id, name, email }, couple: <resumo ou null> }`.
- [ ] O JWT contém, no mínimo, o `sub` (id do usuário) e `exp` de 7 dias a partir da emissão.
- [ ] Credenciais inválidas retornam `401 Unauthorized` com mensagem genérica ("credenciais inválidas"), sem revelar se o e-mail existe.
- [ ] Teste cobre: login com sucesso, senha errada, e-mail inexistente.

### US-004: Configuração Spring Security + filtro JWT + CORS
**Description:** Como desenvolvedor, preciso proteger a API para que apenas requisições autenticadas acessem endpoints privados.

**Acceptance Criteria:**
- [ ] Criar `com.app.security` com `SecurityConfig` (Spring Security 6, `SecurityFilterChain`).
- [ ] Substituir a configuração temporária `permitAll` (introduzida no Épico 1) pela configuração real.
- [ ] Rotas públicas: `POST /api/auth/register`, `POST /api/auth/login` e o endpoint de health. Todas as demais exigem JWT válido.
- [ ] Criar `JwtService` (geração e validação do token) usando um segredo lido de variável de ambiente/`application.properties` (`app.jwt.secret`), com expiração configurável (default 7 dias).
- [ ] Criar `JwtAuthenticationFilter` (`OncePerRequestFilter`) que lê o header `Authorization: Bearer <token>`, valida e popula o `SecurityContext` com o usuário autenticado.
- [ ] Sessão configurada como `STATELESS`.
- [ ] CORS habilitado para a origem do frontend em dev (`http://localhost:5173`), permitindo métodos GET/POST/PUT/DELETE e o header `Authorization`. Origem configurável por propriedade.
- [ ] Token inválido/expirado/ausente em rota protegida retorna `401 Unauthorized`.
- [ ] Teste cobre: acesso negado sem token, acesso permitido com token válido, token expirado rejeitado.

### US-005: Entidade Couple e geração de invite_code
**Description:** Como desenvolvedor, preciso da entidade `Couple` e da lógica de geração de código de convite para vincular duas contas.

**Acceptance Criteria:**
- [ ] Criar entidade `Couple` no pacote `com.app.couple` com campos: `id`, `user1_id`, `user2_id` (nulo até o parceiro vincular), `invite_code` (único), `created_at`.
- [ ] Criar `CoupleRepository` com `findByInviteCode(String code)`.
- [ ] Gerar `invite_code` alfanumérico curto de 6 a 8 caracteres (ex.: `A3F9K2`), evitando caracteres ambíguos (0/O, 1/l) e garantindo unicidade (regenerar em caso de colisão).
- [ ] Endpoint `POST /api/couple` (autenticado) cria um `Couple` para o usuário atual como `user1`, gera o `invite_code` e o retorna.
- [ ] Regra: um usuário só pode pertencer a um casal. Se já estiver em um casal (como user1 ou user2), a criação retorna `409 Conflict`.
- [ ] Endpoint `GET /api/couple/me` (autenticado) retorna o casal do usuário atual (incluindo `invite_code` e dados do parceiro) ou `404`/`null` se ainda não houver.
- [ ] Teste cobre: criação de casal, unicidade do invite_code, bloqueio de segundo casal.

### US-006: Endpoint de vínculo via invite_code
**Description:** Como usuário, quero inserir o código do meu parceiro para nos conectarmos como um casal.

**Acceptance Criteria:**
- [ ] Endpoint `POST /api/couple/join` (autenticado) recebe `{ inviteCode }`.
- [ ] Localiza o `Couple` pelo `invite_code`. Se não existir, retorna `404 Not Found`.
- [ ] Preenche `user2_id` com o usuário atual e persiste o vínculo.
- [ ] Regras de bloqueio, cada uma com resposta clara:
  - [ ] Usuário não pode entrar no próprio casal (se ele é o `user1`) → `400 Bad Request`.
  - [ ] Usuário já pertence a outro casal → `409 Conflict`.
  - [ ] Casal já está completo (`user2_id` preenchido) → `409 Conflict`.
- [ ] Em caso de sucesso, retorna `200 OK` com o resumo do casal já vinculado (ambos os usuários).
- [ ] Teste cobre: vínculo com sucesso, código inexistente, auto-vínculo, casal cheio, usuário já pareado.

### US-007: Configurar Axios com interceptor de JWT (Frontend)
**Description:** Como desenvolvedor frontend, preciso de um cliente HTTP que injete o JWT automaticamente em todas as requisições.

**Acceptance Criteria:**
- [ ] Instalar Axios em versão sem vulnerabilidades conhecidas (verificar com `bun pm ls` / `bun audit` ou equivalente).
- [ ] Criar instância central do Axios (`src/lib/api.ts`) com `baseURL` vinda de variável de ambiente (`VITE_API_URL`).
- [ ] Interceptor de request injeta o header `Authorization: Bearer <token>` quando houver token no estado de autenticação.
- [ ] Interceptor de response trata `401` global: limpa a sessão e redireciona para `/login`.
- [ ] Typecheck passa (`bun run build` ou `tsc --noEmit`).
- [ ] Usar a skill `frontend-design` antes de qualquer trabalho visual associado.

### US-008: Store useAuthStore (Zustand) (Frontend)
**Description:** Como desenvolvedor frontend, preciso de um estado global de autenticação para guardar o token e os dados do usuário/casal.

**Acceptance Criteria:**
- [ ] Criar `useAuthStore` (Zustand) em `src/stores/useAuthStore.ts` com estado: `token`, `user`, `couple`, `isAuthenticated`.
- [ ] Ações: `login(credentials)`, `register(data)`, `logout()`, `joinCouple(inviteCode)`, `createCouple()`, `loadCurrentUser()`.
- [ ] A ação `register(data)` chama `POST /api/auth/register` e, em caso de sucesso, chama `login` automaticamente com as mesmas credenciais — dando ao usuário a sensação de login imediato após o cadastro. Se o login automático falhar, redireciona para `/login`.
- [ ] Token persistido (ex.: `localStorage`) para sobreviver a refresh da página; `logout()` limpa a persistência.
- [ ] Ao iniciar o app, o estado é reidratado a partir do token persistido.
- [ ] Typecheck passa.

### US-009: React Router com rotas protegidas + telas de Auth (Frontend)
**Description:** Como usuário, quero telas de Login, Cadastro e inserção de código de convite, com proteção de rotas.

**Acceptance Criteria:**
- [ ] Configurar React Router com rotas: `/login`, `/register`, `/join` (inserir código) e as rotas privadas do app (Hub etc.).
- [ ] Componente `ProtectedRoute` redireciona para `/login` quando `isAuthenticated` é falso.
- [ ] Usuário autenticado, mas **sem casal**, é redirecionado para `/join` (inserir código ou gerar o próprio).
- [ ] Tela de **Login**: campos e-mail/senha, exibe erro de credenciais inválidas, chama `useAuthStore.login`.
- [ ] Tela de **Cadastro**: campos nome/e-mail/senha, validação de senha mín. 8 caracteres no cliente, exibe erro de e-mail duplicado. Após o cadastro com sucesso, o frontend faz o login automaticamente (chama `register` do store, que encadeia o `login`), sem o usuário precisar digitar as credenciais de novo.
- [ ] Tela de **Código de Convite**: permite (a) inserir um código para se vincular e (b) gerar o próprio código e exibi-lo para compartilhar.
- [ ] Após login/cadastro bem-sucedido com casal já formado, redireciona ao Hub.
- [ ] As telas seguem o design do protótipo (`Sessão a Dois.dc.html`): tema escuro `#09090a`, acento amarelo `#ffcb2b`, fontes Bricolage Grotesque (títulos) e DM Sans (corpo).
- [ ] Usar a skill `frontend-design` antes de implementar.
- [ ] Typecheck passa.
- [ ] Verificar no navegador usando a skill `dev-browser` (fluxo completo: cadastro → login → inserir código → chega ao Hub).

## 4. Functional Requirements

- FR-1: O sistema deve permitir registro de usuário via `POST /api/auth/register` com nome, e-mail e senha. O registro **não** retorna JWT; o frontend chama `POST /api/auth/login` automaticamente logo após um registro bem-sucedido para autenticar o usuário.
- FR-2: A senha deve ter no mínimo 8 caracteres e ser armazenada com hash BCrypt.
- FR-3: O e-mail deve ser único; registro com e-mail existente retorna `409`.
- FR-4: O sistema deve permitir login via `POST /api/auth/login`, retornando um access token JWT válido por 7 dias.
- FR-5: O JWT deve carregar o id do usuário (`sub`) e a expiração (`exp`); não há refresh token.
- FR-6: Todos os endpoints, exceto registro, login e health, exigem JWT válido no header `Authorization: Bearer`.
- FR-7: O CORS deve permitir a origem do frontend (configurável), incluindo o header `Authorization`.
- FR-8: O sistema deve permitir a um usuário autenticado criar um `Couple` e obter um `invite_code` alfanumérico de 6-8 caracteres, único.
- FR-9: O sistema deve permitir a um usuário autenticado vincular-se a um `Couple` existente via `invite_code` (`POST /api/couple/join`).
- FR-10: Um usuário pode pertencer a no máximo um casal; tentativas de segundo vínculo retornam `409`.
- FR-11: Um usuário não pode se vincular ao próprio casal, nem a um casal já completo.
- FR-12: O frontend deve injetar o JWT em todas as requisições autenticadas via interceptor Axios.
- FR-13: O frontend deve, em resposta `401`, limpar a sessão e redirecionar para `/login`.
- FR-14: O frontend deve proteger rotas privadas, redirecionando não autenticados para `/login` e autenticados-sem-casal para `/join`.

## 5. Non-Goals (Fora de Escopo)

- **Refresh token** — apenas access token JWT nesta fase (Decisão Técnica #3).
- **Desvincular / sair do casal**, desfazer par ou trocar de parceiro (opção 4A).
- **Regenerar** o `invite_code` de um casal existente.
- **Recuperação de senha** ("esqueci minha senha"), verificação de e-mail e login social (OAuth).
- **Papéis / permissões administrativas** — todos são usuários comuns.
- **Expiração/validade temporal do `invite_code`** (o código não expira nesta fase).
- **Notificações** de vínculo formado (WebSocket entra apenas no Épico 5).

## 6. Design Considerations

- Reutilizar a linguagem visual do protótipo `docs/design/claude-design-project/Sessão a Dois.dc.html`:
  - Fundo `#09090a` com gradientes radiais em amarelo/laranja; superfícies de card `#161513`; bordas `rgba(255,255,255,.07)`.
  - Acento primário `#ffcb2b` (botões de ação), secundário `#ff9e2c`.
  - Tipografia: **Bricolage Grotesque** (600/700/800) para títulos; **DM Sans** para corpo.
  - Inputs seguem o padrão do modal do protótipo (fundo `#201e18`, borda focal amarela).
- As telas de Auth são novas (não existem no protótipo, que assume usuário já logado) — devem seguir a mesma identidade visual do Hub/Modal.
- **Obrigatório** usar a skill `frontend-design` antes de escrever/alterar qualquer código em `client/` (regra do CLAUDE.md).

## 7. Technical Considerations

- **Backend:** Java 21 + Spring Boot + Maven; arquitetura package-by-feature (`com.app.user`, `com.app.couple`, `com.app.security`).
- **Segurança:** Spring Security 6 (`SecurityFilterChain`, sessão `STATELESS`), `BCryptPasswordEncoder`, biblioteca JWT (ex.: `jjwt`); segredo JWT via variável de ambiente (`app.jwt.secret`) — nunca commitado.
- **Banco:** PostgreSQL do `docker-compose-dev.yml`; entidades `User` e `Couple` conforme seção 4 do `ARCHITECTURE.md`.
- **Testes:** JUnit 5 + Mockito, cobrindo Controllers, Services e Repositories (obrigatório por decisão de arquitetura).
- **Frontend:** React + TypeScript + Vite, Bun, Zustand, Shadcn UI + Tailwind; Axios em versão segura (Decisão Técnica #4).
- **Dependências entre épicos:** este épico substitui o `SecurityConfig` temporário `permitAll` criado na US-003 do Épico 1; o `couple_id` produzido aqui é consumido pelo Épico 4 (Tracking) em diante.
- **Endpoints (resumo):**
  - `POST /api/auth/register` (público)
  - `POST /api/auth/login` (público)
  - `POST /api/couple` (autenticado) — cria casal e gera invite_code
  - `GET /api/couple/me` (autenticado) — casal atual
  - `POST /api/couple/join` (autenticado) — vincula via invite_code

## 8. Success Metrics

- Um novo usuário consegue: cadastrar → logar → gerar/inserir código → formar casal, ponta a ponta, sem erro.
- 100% das rotas privadas rejeitam requisições sem JWT válido (verificado por teste).
- Cobertura de testes unitários/integração para os Services e Controllers de `user`, `couple` e `security`.
- O frontend anexa o JWT automaticamente e trata `401` global sem intervenção manual.
- Nenhuma senha em texto puro persistida; segredo JWT fora do código-fonte.

## 9. Decisões Confirmadas

- **Registro não retorna JWT.** O endpoint devolve `201 Created`; o token é obtido via login. No frontend, o cadastro encadeia automaticamente a chamada de login por baixo dos panos, dando ao usuário a sensação de já estar logado ao concluir o cadastro.
- **Biblioteca JWT:** `jjwt` (io.jsonwebtoken).
- **`id` das entidades:** `UUID`.
- **CORS em dev:** origem do frontend `http://localhost:5173` (Vite padrão), configurável por propriedade.

## 10. Open Questions

- Nenhuma pendência aberta no momento — os pontos acima foram confirmados com o solicitante.
