# Sessão a Dois

Sessão a Dois é um app para casais acompanharem juntos o que estão assistindo. Cada
conta se vincula à do parceiro por um código de convite, e a partir daí filmes e
séries pesquisados na TMDB entram numa lista compartilhada (Assistindo, Queremos Ver,
Já Vimos), com nota e opinião individuais por usuário.

A dinâmica central é o Match: os dois pesquisam e curtem títulos separadamente, e
quando ambos curtem o mesmo título ele cai automaticamente em "Queremos Ver", com uma
celebração em tempo real via WebSocket. Um dashboard resume o consumo do casal (tempo
assistido, gêneros favoritos, comparativo entre parceiros).

## Stack

| Camada | Tecnologias |
|---|---|
| Backend | Java 21 + Spring Boot + PostgreSQL |
| Frontend | React + TypeScript + Vite + Bun |
| Infra | Docker + Nginx (VPS) |

## Estrutura do repositório

- `api/` — backend Spring Boot (package-by-feature: auth, user, couple, media, tracking, match, notification, security, websocket).
- `client/` — frontend React/Vite.
- `deploy/` — configuração de infraestrutura (conf do Nginx usado na VPS).
- `scripts/` — automação (`deploy.sh` e o agente autônomo Ralph em `scripts/ralph/`).
- `tasks/` — PRDs de épicos em andamento.
- `docs/` — documentação densa de arquitetura, deploy e schema (mapa abaixo).

## Como subir local

Pré-requisitos: Docker, Java 21 + Maven wrapper (já incluso), Bun.

```bash
# Banco de dados PostgreSQL local
docker compose -f docker-compose-dev.yml up

# Backend (api/), noutro terminal
cd api
./mvnw spring-boot:run

# Frontend (client/), noutro terminal
cd client
bun install && bun run dev
```

## Variáveis de ambiente

Copie `.env.example` para `.env` na raiz e preencha os valores. As obrigatórias:

- `DB_URL`, `DB_USER`, `DB_PASSWORD` — conexão com o PostgreSQL.
- `JWT_SECRET` — segredo de assinatura dos tokens de sessão (mínimo 32 bytes). Sem
  ela, ou com um valor curto/igual ao default de dev, `JwtService` falha o startup
  da aplicação (fail-fast).
- `TMDB_API_KEY` — API Read Access Token (v4) da TMDB. Sem ela, `TmdbConfig` falha
  o startup da aplicação (fail-fast).

Variáveis adicionais (e-mail transacional, CORS, TTLs de autenticação) estão
documentadas com seus defaults em `.env.example` e em `docs/DEPLOY.md`.

## Como rodar os testes

```bash
# Backend
cd api
./mvnw test

# Frontend
cd client
bun run test:run
```

## Mapa da documentação (`docs/`)

- `docs/ARCHITECTURE.md` — decisões de arquitetura de frontend/backend, modelagem de dados, WebSockets e anti-patterns do projeto; consulte antes de adicionar uma feature nova ou entender como as camadas se comunicam.
- `docs/BACKLOG.md` — backlog original por épicos e decisões técnicas registradas; consulte para entender o histórico de decisões de produto.
- `docs/DEPLOY.md` — tutorial completo de deploy na VPS Hostinger (Nginx, SSL, CI/CD, segurança operacional, monitoramento); consulte antes de mexer em infraestrutura ou fazer deploy manual.
- `docs/FLYWAY.md` — estratégia de baseline do Flyway e checklist de deploy com migrations; consulte antes de qualquer deploy que inclua uma migration nova.
- `docs/SCHEMA_BASELINE.md` — schema físico de produção tabela a tabela; consulte para saber o formato exato de uma tabela existente antes de escrever uma migration.
- `docs/design/claude-design-project/` — protótipo visual de referência do frontend; consulte para conferir o design original antes de implementar uma tela nova.

Veja também `client/docs/DESIGN-SYSTEM.md`, o design system do frontend (tokens,
componentes, convenções visuais).

## Gate humano

O critério "alguém que nunca viu o projeto sobe o ambiente seguindo só o README" é
gate humano — não pode ser verificado por agente, que não tem como avaliar a
experiência de um leitor novo. Ver `scripts/ralph/progress.txt` para o registro
desta verificação.
