# PRD: Épico 7 — Refinamento e Deploy (Hostinger VPS)

## 1. Introdução / Visão Geral

Este épico prepara o **Sessão a Dois** (Couple Media Tracker) para produção. Até aqui, os Épicos 1 a 6 entregaram autenticação, integração TMDB, tracking de mídia, sistema de match e dashboard analítico — tudo rodando em ambiente de desenvolvimento local.

O objetivo do Épico 7 é levar a aplicação ao ar de forma **estável, segura e reproduzível** em uma VPS Hostinger, servindo o frontend estático e o backend via Nginx como reverse proxy, com HTTPS, no domínio `sessaoadois.luisgosampaio.com`. Também inclui um reforço final de cobertura de testes automatizados no backend para garantir confiança antes do deploy.

**Problema que resolve:** hoje a aplicação só funciona localmente; não há forma de o casal usá-la de verdade. Falta empacotamento de produção, infraestrutura, HTTPS, gestão segura de segredos e um processo de deploy repetível.

## 2. Goals

- Atingir cobertura ampla de testes unitários no backend (Services, Controllers e Repositories, incluindo casos de erro e borda).
- Empacotar backend + banco de dados em um `docker-compose-prod.yml` com rede isolada, volumes persistentes e `restart: always`.
- Gerar build de produção otimizado do frontend com variáveis de ambiente corretas.
- Servir a aplicação em `https://sessaoadois.luisgosampaio.com` com Nginx como reverse proxy e SSL válido (Let's Encrypt).
- Automatizar o deploy em um único script (build do frontend → SCP → `docker compose up` remoto via SSH).
- Gerenciar segredos (credenciais do banco, JWT secret, TMDB API Key) de forma segura via `.env` na VPS, fora do controle de versão.

## 3. User Stories

### US-001: Reforçar cobertura de testes do backend
**Description:** Como desenvolvedor, quero cobertura ampla de testes unitários para Services, Controllers e Repositories, incluindo casos de erro e borda, para ter confiança de que o deploy não quebrará funcionalidades existentes.

**Acceptance Criteria:**
- [ ] Todos os Services (`user`, `couple`, `media`, `tracking`, `match`) têm testes cobrindo happy path e ao menos um caso de erro/borda (ex.: entidade inexistente, código de convite inválido, like duplicado).
- [ ] Todos os Controllers têm testes (ex.: `@WebMvcTest` ou MockMvc) validando status HTTP de sucesso e de erro (400/401/404 conforme aplicável).
- [ ] Repositories com queries customizadas (ex.: stats do dashboard) têm testes (ex.: `@DataJpaTest`) validando os resultados.
- [ ] Mocks do TMDB (Mockito) cobrem resposta válida e resposta de falha/timeout.
- [ ] `mvn test` executa toda a suíte com sucesso (0 falhas).
- [ ] Relatório de cobertura gerado (ex.: JaCoCo) e conferido; lacunas relevantes documentadas ou cobertas.

### US-002: Criar docker-compose de produção
**Description:** Como responsável pelo deploy, quero um `docker-compose-prod.yml` com backend e banco em rede isolada e dados persistentes, para que a aplicação rode de forma estável e resiliente a reinícios na VPS.

**Acceptance Criteria:**
- [ ] Arquivo `docker-compose-prod.yml` na raiz define serviços `api` (Spring Boot) e `db` (PostgreSQL).
- [ ] Volume nomeado persiste os dados do PostgreSQL entre reinícios do container.
- [ ] Rede Docker interna dedicada; a porta do banco **não** é exposta ao host/internet (apenas acessível pela `api`).
- [ ] Ambos os serviços usam `restart: always`.
- [ ] Todas as credenciais/segredos vêm de variáveis de ambiente (via `.env`), sem valores hardcoded no compose.
- [ ] `api` só inicia após o `db` estar saudável (`depends_on` com healthcheck **simples** — check de TCP/porta ou endpoint HTTP existente, sem adicionar Spring Boot Actuator).
- [ ] A `api` expõe a porta **`8085`** no host (livre; segue a convenção 8080–8084 dos outros projetos da VPS) para o Nginx fazer proxy; a porta é configurável via `.env`.
- [ ] Imagem otimizada para x86_64 (arquitetura padrão da VPS).

### US-003: Configurar variáveis de ambiente e build de produção do frontend
**Description:** Como desenvolvedor, quero um build de produção do frontend apontando para a URL correta da API, para que a SPA funcione servida pelo Nginx no domínio de produção.

**Acceptance Criteria:**
- [ ] `.env.production` do client define **`VITE_API_URL=https://sessaoadois.luisgosampaio.com`** (variável real usada em `client/src/lib/api.ts`; **host puro, sem `/api` e sem porta** — as chamadas axios já incluem o prefixo `/api/...`).
- [ ] O WebSocket (SockJS em `useMatchStore.ts`, `${VITE_API_URL}/ws`) resolve para `https://sessaoadois.luisgosampaio.com/ws` (o Nginx faz o upgrade para WSS via TLS terminado nele).
- [ ] `bun run build` gera artefatos estáticos otimizados em `client/dist` sem erros.
- [ ] Nenhuma URL de `localhost`/porta de dev presente no bundle de produção.
- [ ] Typecheck/lint passa.
- [ ] Verificar no browser (build de produção servido localmente) usando a skill dev-browser: telas principais carregam e consomem a API configurada.

### US-004: Configurar Nginx como reverse proxy com SSL
**Description:** Como usuário, quero acessar a aplicação por HTTPS em um domínio real, para que o uso seja seguro e confiável.

> **✅ Topologia da VPS confirmada (Cenário A — Nginx único no host):** a VPS roda **Nginx 1.24 direto no host** (não há Nginx Proxy Manager nem Traefik), servindo múltiplos sites via `sites-enabled/` roteados por `server_name`. O Certbot já gerencia os certificados **por domínio** (`certbot --nginx`), com renovação automática pelo timer existente. Cada projeto sobe seu container em uma porta alta sequencial (`8080–8084`); **a porta `8085` está livre** e será usada pela API deste projeto. A estratégia deste épico segue esse padrão já consolidado.

**Acceptance Criteria:**
- [ ] Arquivo de site em `/etc/nginx/sites-available/sessaoadois.luisgosampaio.com` com symlink em `sites-enabled/`, espelhando as convenções dos sites existentes (ex.: `futspring`).
- [ ] `server` block com `server_name sessaoadois.luisgosampaio.com`.
- [ ] Nginx serve os arquivos estáticos do frontend a partir do diretório de deploy (ex.: `/var/www/sessaoadois`), populado via SCP.
- [ ] `location /api/` encaminha para `http://localhost:8085` **sem barra final** no `proxy_pass` — os controllers do backend são mapeados **com** o prefixo `/api` (`@RequestMapping("/api/...")`); usar barra final (como no site `futspring`) removeria o `/api` e causaria 404.
- [ ] `location /ws/` encaminha para `http://localhost:8085/ws/` (endpoint STOMP/SockJS registrado em `/ws`) com `proxy_http_version 1.1;`, `proxy_set_header Upgrade $http_upgrade;` e `Connection "upgrade";`, e `proxy_read_timeout/send_timeout 3600`.
- [ ] SSL emitido com `sudo certbot --nginx -d sessaoadois.luisgosampaio.com` (mesmo método já usado nos outros domínios), certificado válido.
- [ ] Redirecionamento automático de HTTP (80) para HTTPS (443) — gerado pelo próprio Certbot `--nginx`.
- [ ] Rota SPA fallback (`try_files $uri $uri/ /index.html`) para o React Router funcionar em refresh de rotas internas.
- [ ] Renovação automática do certificado confirmada (herda do `certbot.timer`/cron já existente na VPS).
- [ ] Pré-condição: registro A de `sessaoadois.luisgosampaio.com` apontando para o IP da VPS antes da emissão.

### US-005: Criar script de deploy automatizado
**Description:** Como responsável pelo deploy, quero um único script que faça build do frontend, envie por SCP e suba o backend via SSH, para que o deploy seja rápido, repetível e menos sujeito a erro humano.

**Acceptance Criteria:**
- [ ] Script de deploy (ex.: `scripts/deploy.sh`) executa em ordem: build do frontend (`bun run build`) → SCP do `dist` para a VPS → SSH executando `docker compose -f docker-compose-prod.yml up -d --build` remotamente.
- [ ] Dados de acesso (`SSH_HOST`, `SSH_USER`, `SSH_PORT`, caminho remoto) são **parametrizados via variáveis de ambiente/`.env`**, sem valores hardcoded — a preencher com base nos scripts de deploy já existentes de outros projetos do usuário na VPS.
- [ ] Script falha explicitamente (exit code ≠ 0) se qualquer etapa falhar (`set -e`).
- [ ] Documentação de uso (pré-requisitos: chave SSH configurada, `.env` presente na VPS) incluída no README ou em comentário no script.
- [ ] Execução de ponta a ponta valida a aplicação no ar em `https://sessaoadois.luisgosampaio.com`.

### US-006: Configurar gestão de segredos na VPS
**Description:** Como responsável pela segurança, quero as chaves secretas gerenciadas por um `.env` na VPS fora do versionamento, para que credenciais nunca sejam expostas no repositório.

**Acceptance Criteria:**
- [ ] Arquivo `.env.example` no repositório documenta todas as variáveis necessárias (DB credentials, `JWT_SECRET`, `TMDB_API_KEY`, URLs) **sem valores reais**.
- [ ] `.env` real reside apenas na VPS e está listado no `.gitignore`.
- [ ] `docker-compose-prod.yml` consome o `.env` da VPS.
- [ ] Confirmado que nenhum segredo real foi commitado (histórico e working tree limpos).
- [ ] Permissões restritas no `.env` da VPS (ex.: `chmod 600`).

## 4. Functional Requirements

- **FR-1:** O backend deve possuir suíte de testes unitários cobrindo Services, Controllers e Repositories (happy path + erros/borda), executável via `mvn test`.
- **FR-2:** Deve existir um `docker-compose-prod.yml` com serviços `api` e `db`, rede isolada, volume persistente para o banco e `restart: always`.
- **FR-3:** A porta do banco de dados não deve ser exposta publicamente; apenas o serviço `api` acessa o `db` pela rede interna.
- **FR-4:** O frontend deve ter build de produção (`bun run build`) parametrizado por variáveis de ambiente apontando para o domínio de produção (HTTPS/WSS).
- **FR-5:** O Nginx deve servir o frontend estático, fazer proxy de `/api` para a `api`, encaminhar WebSocket corretamente e aplicar fallback SPA.
- **FR-6:** O acesso deve ser via HTTPS em `sessaoadois.luisgosampaio.com`, com certificado Let's Encrypt (método Certbot definido conforme a topologia da VPS — provavelmente `--nginx`) e redirecionamento HTTP→HTTPS.
- **FR-7:** Deve existir um script único de deploy que faça build do frontend, SCP para a VPS e `docker compose up` remoto via SSH, com falha explícita em qualquer etapa.
- **FR-8:** Segredos devem ser injetados via `.env` presente apenas na VPS, com `.env.example` versionado sem valores reais e `.env` no `.gitignore`.
- **FR-9:** A renovação do certificado SSL deve ser automatizada.

## 5. Non-Goals (Fora de Escopo)

- Não implementar refresh token (Decisão #3 — apenas access token JWT).
- Não implementar cache de TMDB (Decisão #5).
- Não configurar CI/CD (GitHub Actions); o deploy será via script local (resposta 4A).
- Não containerizar o frontend; ele será servido como build estático pelo Nginx (resposta 3A).
- Não adicionar novas funcionalidades de produto (nenhuma tela ou regra de negócio nova).
- Não configurar monitoramento/observabilidade avançada (APM, dashboards de logs) — fora do escopo deste épico.
- Não configurar backups automáticos do banco (pode virar tarefa futura).
- Não implementar balanceamento de carga ou múltiplas instâncias.

## 6. Design Considerations

- Nenhuma mudança visual de produto neste épico. O frontend usa o build já aprovado nos épicos anteriores.
- A única verificação visual necessária é confirmar que o build de produção carrega e consome corretamente a API/WebSocket no domínio configurado (US-003).

## 7. Technical Considerations

- **Arquitetura da VPS:** x86_64 Linux (Hostinger); imagens Docker devem ser compatíveis.
- **Dockerfile do backend:** multi-stage já previsto (build Maven → runtime JRE 21) do Épico 1; reutilizar.
- **Topologia da VPS (confirmada — Cenário A):** Nginx 1.24 direto no host servindo vários sites por `server_name` via `sites-enabled/`; Certbot já emite/renova certificados por domínio com `--nginx`. Sites existentes: `futspring`, `lila`, `molda`, `oriens`, `pilling`, `portfolio`, `zugaming`. **Não** há Nginx Proxy Manager nem Traefik. Portanto o SSL usa `certbot --nginx` (standalone/webroot descartados). Sem restrição de horário para a emissão (não há usuários ativos).
- **Porta da API:** expor a `api` em **`8085`** no host (livre; a VPS já usa 8080–8084 para oriens/futspring/zugaming/pilling/molda), configurável via `.env`. O Nginx faz proxy_pass de `/api` e do WebSocket para `127.0.0.1:8085`.
- **Diretório do frontend:** build estático servido de um diretório no host (ex.: `/var/www/sessaoadois`), populado via SCP — mesmo padrão dos demais sites.
- **Healthcheck:** usar healthcheck **simples** (check de TCP/porta ou endpoint HTTP já existente). Não adicionar Spring Boot Actuator neste épico.
- **WebSocket/STOMP:** o proxy Nginx precisa de `proxy_set_header Upgrade $http_upgrade;` e `proxy_set_header Connection "upgrade";` para o match em tempo real funcionar.
- **CORS:** revisar a configuração de CORS do backend para permitir o domínio de produção.
- **Domínio:** `sessaoadois.luisgosampaio.com` (resposta 1B). A inconsistência com `ARCHITECTURE.md` e `BACKLOG.md` (Decisão #11, que citavam `sessao.luisgosampaio.com`) será corrigida nesses documentos como parte deste épico.
- **DNS:** o registro A do domínio precisa apontar para o IP da VPS antes da emissão do certificado.
- **Deploy SSH:** dados de acesso parametrizados via `.env`; reaproveitar convenções dos scripts de deploy já existentes de outros projetos do usuário na VPS.
- **Cobertura:** usar JaCoCo para medir; sem meta numérica rígida obrigatória, mas cobertura ampla com casos de erro (resposta 2B).

## 8. Success Metrics

- Aplicação acessível e funcional em `https://sessaoadois.luisgosampaio.com` (login, tracking, match em tempo real e dashboard funcionando ponta a ponta).
- Certificado SSL válido (cadeado no navegador; nota A em teste SSL básico).
- `mvn test` verde com cobertura ampla verificada por relatório JaCoCo.
- Deploy completo executável por um único comando/script, reproduzível.
- Nenhum segredo presente no repositório (working tree e histórico).
- Banco de dados persiste dados após `docker compose down && up`.

## 9. Open Questions (Resolvidas)

Todas as questões em aberto foram decididas com o usuário:

- **Domínio definitivo:** ✅ `sessaoadois.luisgosampaio.com`. Os documentos `ARCHITECTURE.md` e `BACKLOG.md` (Decisão #11) serão atualizados para eliminar a inconsistência (parte deste épico).
- **Janela de emissão SSL:** ✅ Sem restrição de horário (não há usuários ativos). Estratégia definida: `certbot --nginx` (ver abaixo).
- **Acesso SSH do script de deploy:** ✅ Parametrizado via `.env`/variáveis; reaproveitar convenções dos scripts de deploy de outros projetos do usuário (não acessíveis a partir deste ambiente).
- **Healthcheck:** ✅ Simples (TCP/porta ou endpoint existente); sem Spring Boot Actuator neste épico.
- **Backup do PostgreSQL:** ✅ Fora deste épico (non-goal); fica para tarefa/épico futuro.

### Topologia da VPS — VERIFICADA ✅

- **Cenário A confirmado:** Nginx único no host (v1.24) servindo sites por `server_name`; Certbot `--nginx` por domínio com renovação automática. API na porta **`8085`**. Não restam itens em aberto — o épico está pronto para implementação.
- **Única pré-condição operacional:** apontar o registro A de `sessaoadois.luisgosampaio.com` para o IP da VPS antes de rodar o Certbot.
