# Product Backlog: Couple Media Tracker

Este backlog divide a arquitetura e as regras de negocio em epicos e tarefas incrementais para orientar o desenvolvimento nas pastas `api/` (Backend) e `client/` (Frontend).

## Decisoes Tecnicas Registradas

| # | Decisao | Escolha |
|---|---------|---------|
| 1 | Build tool backend | Maven |
| 2 | PgAdmin no dev | Manter (avaliar necessidade durante uso) |
| 3 | Refresh token | Nao por enquanto (apenas access token JWT) |
| 4 | HTTP Client frontend | Axios (versao segura, sem vulnerabilidades conhecidas) |
| 5 | Cache TMDB | Nao por enquanto |
| 14 | Total de horas (dashboard) | Apenas filmes (series so contam quantidade). Runtime guardado no MediaTrack |
| 6 | Idioma TMDB | pt-BR fixo |
| 7 | Rating | Individual por usuario |
| 8 | Opinion | Separada por usuario |
| 9 | Fonte do Match | Pesquisa manual do usuario + like |
| 10 | Destino do Match | Vai direto para lista "Queremos Ver" |
| 11 | Deploy frontend | SCP para VPS + Nginx como reverse proxy (sessao.luisgosampaio.com) |
| 12 | SSL/HTTPS | Sim, incluir configuracao |
| 13 | Ordem de execucao | Sequencial (Epico 1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7) |

---

## Epico 1: Setup e Infraestrutura (Base)
**Objetivo:** Inicializar os projetos e configurar os ambientes de desenvolvimento.

- [ ] **Task 1.1 (Infra):** Criar `docker-compose-dev.yml` na raiz com PostgreSQL e PgAdmin (imagem otimizada para ARM64/Apple Silicon).
- [ ] **Task 1.2 (Backend):** Inicializar projeto Spring Boot com Maven e adicionar dependencias base (Web, Data JPA, PostgreSQL, Validation, Security, WebSocket).
- [ ] **Task 1.3 (Infra):** Criar `Dockerfile` multi-stage para a API (build com Maven, runtime com JRE 21) e configurar testes de build.

## Epico 2: Autenticacao e Gestao de Casais (Dominio User/Couple)
**Objetivo:** Permitir cadastro, login e o vinculo entre duas contas. Apenas access token JWT (sem refresh token).

- [ ] **Task 2.1 (Backend):** Implementar entidade `User`, `UserRepository` e endpoints de Registro/Login (`com.app.user`).
- [ ] **Task 2.2 (Backend):** Configurar Spring Security com geracao/validacao de token JWT e CORS habilitado para o frontend (`com.app.security`).
- [ ] **Task 2.3 (Backend):** Implementar entidade `Couple` e logica de geracao de `invite_code`.
- [ ] **Task 2.4 (Backend):** Criar endpoint para vincular o usuario atual a um `Couple` existente via `invite_code` (`com.app.couple`).
- [ ] **Task 2.5 (Frontend):** Configurar Axios com interceptor para injetar o JWT nas requisicoes. Usar versao sem vulnerabilidades conhecidas.
- [ ] **Task 2.6 (Frontend):** Criar a store `useAuthStore` no Zustand para gerenciar estado de login e dados do usuario/casal.
- [ ] **Task 2.7 (Frontend):** Configurar React Router com rotas protegidas (redirect para login se nao autenticado) e desenvolver telas de Login, Cadastro e Insercao de Codigo de Convite.

## Epico 3: Integracao TMDB (Dominio Media/BFF)
**Objetivo:** Backend atuar como proxy seguro para a API do TMDB. Resultados sempre em pt-BR.

- [ ] **Task 3.1 (Backend):** Configurar `RestClient` (Spring 3.2+) para chamadas externas com `language=pt-BR` como parametro padrao.
- [ ] **Task 3.2 (Backend):** Criar endpoint de busca de filmes/series (`GET /api/media/search?q=...`) ocultando a API Key do TMDB (`com.app.media`).
- [ ] **Task 3.3 (Backend):** Criar endpoint de detalhes do titulo combinando `append_to_response=watch/providers` para retornar onde assistir.
- [ ] **Task 3.4 (Backend):** Escrever testes unitarios com Mockito para simular as respostas do TMDB e garantir resiliencia.

## Epico 4: Tracking de Midia (Core App)
**Objetivo:** Permitir que o casal adicione, avalie e liste filmes/series. Notas e opinioes sao individuais por usuario.

- [ ] **Task 4.1 (Backend):** Criar entidade `MediaTrack` vinculada ao `couple_id` (campos: id, couple_id, tmdb_id, media_type, status, watched_date). Criar entidade `UserReview` vinculada a `MediaTrack` + `user_id` (campos: id, media_track_id, user_id, rating 1-5, opinion).
- [ ] **Task 4.2 (Backend):** Desenvolver CRUD para `MediaTrack` e `UserReview` (`com.app.tracking`). Ao listar, retornar as reviews de ambos os usuarios do casal.
- [ ] **Task 4.3 (Frontend):** Desenvolver componente `TitleModal.tsx` com formulario de insercao (busca TMDB, nota individual, status, opiniao individual).
- [ ] **Task 4.4 (Frontend):** Desenvolver tela `HubScreen.tsx` e integrar as listas "Assistindo", "Queremos Ver" e "Ja Vimos" consumindo os endpoints do tracking.
- [ ] **Task 4.5 (Frontend):** Componentizar os cards de midia garantindo o design aprovado no prototipo.

## Epico 5: Sistema de Match (Pesquisa + Like)
**Objetivo:** Usuarios pesquisam titulos, curtem individualmente, e quando ambos curtem o mesmo titulo, ocorre um Match que adiciona automaticamente a lista "Queremos Ver".

- [ ] **Task 5.1 (Backend):** Configurar `WebSocketConfig` implementando STOMP e roteamento de mensagens.
- [ ] **Task 5.2 (Backend):** Criar entidade `MatchLike` (campos: id, couple_id, user_id, tmdb_id, media_type, created_at) e implementar endpoint HTTP (`POST /api/match/like`) para registrar o Like (`com.app.match`).
- [ ] **Task 5.3 (Backend):** Criar logica de verificacao: se o parceiro ja curtiu o mesmo `tmdb_id`, criar automaticamente um `MediaTrack` com status "WANT_TO_SEE" e emitir evento para `/topic/couple/{id}/match`.
- [ ] **Task 5.4 (Frontend):** Criar `useMatchStore` (Zustand) para manter a conexao WebSocket ativa e escutar mensagens de match.
- [ ] **Task 5.5 (Frontend):** Desenvolver a tela `MatchScreen.tsx` com busca de titulos, botao de "Curtir", e renderizacao do modal de Match ao receber o evento STOMP.

## Epico 6: Dashboard Analitico
**Objetivo:** Gerar estatisticas de consumo do casal.

- [ ] **Task 6.1 (Backend):** Criar consultas JPA/SQL personalizadas (total de horas apenas de filmes, media de notas por usuario, generos mais vistos, comparativo entre parceiros). Guardar `runtime` no `MediaTrack` ao adicionar um filme.
- [ ] **Task 6.2 (Backend):** Expor endpoint `GET /api/tracking/stats`.
- [ ] **Task 6.3 (Frontend):** Desenvolver `DashboardScreen.tsx` integrando os dados e construindo os graficos/barras com Tailwind CSS (conforme design original).

## Epico 7: Refinamento e Deploy (Hostinger VPS)
**Objetivo:** Preparar a aplicacao para producao. Frontend servido via Nginx como reverse proxy no dominio sessao.luisgosampaio.com.

- [ ] **Task 7.1 (Backend):** Finalizar e validar cobertura de testes unitarios para Services e Controllers.
- [ ] **Task 7.2 (Infra):** Criar `docker-compose-prod.yml` com rede isolada, banco de dados persistente (volumes) e API.
- [ ] **Task 7.3 (Frontend):** Ajustar variaveis de ambiente de producao (URLs base) e build otimizado (`bun run build`).
- [ ] **Task 7.4 (Infra):** Configurar Nginx como reverse proxy com SSL (Certbot/Let's Encrypt) para o dominio `sessao.luisgosampaio.com`.
- [ ] **Task 7.5 (Infra):** Criar script de deploy (SCP do build frontend para VPS, docker compose up para backend).
- [ ] **Task 7.6 (Infra):** Configurar `.env` na VPS para gerenciar chaves secretas (DB credentials, JWT secret, TMDB API Key).
