# Architecture & Technical Decisions: Couple Media Tracker

## 1. Visão Geral do Sistema
O sistema é um aplicativo para casais gerenciarem o consumo de filmes e séries, composto por uma Single Page Application (SPA) e uma API RESTful atuando como Backend for Frontend (BFF).

## 2. Decisões de Frontend (Client-Side)
- **Stack Base:** React + TypeScript + Vite.
- **Gerenciador de Pacotes:** Bun (`bun install`, `bun run dev`).
- **Estado Global:** Zustand.
  - Store `useAuthStore`: Gerencia JWT, dados do usuário ativo e o status do vínculo do casal (código de pareamento).
  - Store `useMatchStore`: Gerencia a fila de WebSockets e o estado global da tela de Match.
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
  - `com.app.user`: Cadastro independente de usuários.
  - `com.app.couple`: Geração de código de convite e vinculação de duas contas em uma entidade `Couple`.
  - `com.app.media`: Integração exclusiva com TMDB API (buscas, detalhes, watch providers). O frontend não consome o TMDB diretamente.
  - `com.app.tracking`: Registro das avaliações (notas, datas, opiniões) vinculadas ao `Couple`.
  - `com.app.match`: Lógica de validação de likes simultâneos e emissão de eventos.
  - `com.app.security`: Configurações de autenticação, filtros JWT e rotas públicas/privadas.
  - `com.app.websocket`: Configuração do broker de mensagens e endpoints STOMP.

## 4. Modelagem de Dados e Relacionamento
- **Entidade `User`:** Contas individuais (ID, Nome, Email, Senha/Hash).
- **Entidade `Couple`:** Registra o vínculo.
  - Campos: `ID`, `user1_id`, `user2_id`, `invite_code`, `created_at`.
  - Regra de Negócio: Um usuário gera um `invite_code`. O outro usuário insere o código para formar o `Couple`. Todo rastreamento de mídia a partir desse ponto pertence ao `couple_id`.
- **Entidade `MediaTrack`:** Registro compartilhado do casal sobre um título.
  - Campos: `ID`, `couple_id`, `tmdb_id`, `media_type` (MOVIE/TV), `status` (WATCHING, WANT_TO_SEE, WATCHED), `watched_date`.
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