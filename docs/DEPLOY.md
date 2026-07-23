# Deploy - Sessao a Dois

Tutorial completo para colocar a aplicacao em producao na VPS Hostinger.

## Arquitetura de Producao

```
Maquina local                              VPS Hostinger
─────────────                              ──────────────
bun run build (client/)
docker build (api/)
docker save | gzip
       │
       ├── SCP frontend ──────────────►  /var/www/sessaoadois/
       │                                       │
       ├── SCP imagem .tar.gz ────────►  ~/projects/sessao-a-dois/
       ├── SCP docker-compose.prod.yml ►       │
       ├── SCP .env ──────────────────►        │
       │                                       │
       └── SSH ───────────────────────►  docker load + docker compose up
                                               │
                                          sessao-api:8085
                                               │
                                          Nginx (reverse proxy + SSL)
                                            /      ► SPA estatica
                                            /api/  ► localhost:8085
                                            /ws/   ► localhost:8085 (WebSocket)
                                               │
                                          Certbot (HTTPS :443)
                                               │
                                          Supabase (PostgreSQL externo)
```

O banco PostgreSQL roda no Supabase (externo). Na VPS so sobe o container da API.

---

## Pre-requisitos

### Maquina local

- **bun** instalado (`bun --version`)
- **Docker** instalado e rodando (`docker --version`)
- **Chave SSH** configurada para acesso sem senha a VPS

### VPS Hostinger

- **Docker** + **docker compose** instalados
- **Nginx** instalado
- **Certbot** instalado (para SSL Let's Encrypt)

### Servicos externos

- **Supabase**: projeto criado com banco PostgreSQL
- **TMDB**: conta criada com API Read Access Token (v4)
- **Dominio**: `sessaoadois.luisgosampaio.com` apontando para o IP da VPS (registro A no DNS)

---

## Passo 1 - Configurar chave SSH (primeira vez)

Se ainda nao tens chave SSH:

```bash
# Gerar chave
ssh-keygen -t ed25519 -C "teu-email@exemplo.com"

# Copiar para a VPS (pede a senha pela ultima vez)
ssh-copy-id -p <PORTA_SSH> <USER>@<IP_DA_VPS>

# Testar (deve imprimir OK sem pedir senha)
ssh -p <PORTA_SSH> <USER>@<IP_DA_VPS> "echo OK"
```

---

## Passo 2 - Criar o banco no Supabase

1. Acede a [supabase.com](https://supabase.com) e cria um novo projeto
2. Vai a **Project Settings > Database**
3. Copia a **Connection string** do **Connection Pooler** (modo Transaction, porta 6543)
4. Anota o **password** do projeto

O formato da connection string e:

```
jdbc:postgresql://aws-0-xx-xxxx-x.pooler.supabase.com:6543/postgres?prepareThreshold=0
```

O Spring Boot cria as tabelas automaticamente via JPA/Hibernate na primeira execucao.

---

## Passo 3 - Preencher o .env local

Na raiz do repo, copia o template e preenche com valores reais:

```bash
cp .env.example .env
```

Edita o `.env`:

```bash
# Banco Supabase
DB_URL=jdbc:postgresql://aws-0-xx-xxxx-x.pooler.supabase.com:6543/postgres?prepareThreshold=0
DB_USER=postgres.teu-project-ref
DB_PASSWORD=tua-senha-supabase

# JWT (gera com: openssl rand -base64 48)
JWT_SECRET=teu-segredo-jwt-com-no-minimo-32-bytes

# CORS
CORS_ALLOWED_ORIGIN=https://sessaoadois.luisgosampaio.com

# TMDB
TMDB_API_KEY=teu-tmdb-api-read-access-token-v4

# Deploy
SSH_HOST=ip-da-vps
SSH_USER=root
SSH_PORT=22
API_PORT=8085
```

> O `.env` esta no `.gitignore` e nunca e commitado.

---

## Passo 4 - Configurar Nginx na VPS (primeira vez)

Conecta a VPS e executa:

```bash
ssh <USER>@<IP_DA_VPS>
```

### 4.1 Criar o diretorio do frontend

```bash
sudo mkdir -p /var/www/sessaoadois
sudo chown $USER:$USER /var/www/sessaoadois
```

### 4.2 Instalar a configuracao do Nginx

O ficheiro de configuracao esta no repo em `deploy/nginx/sessaoadois.luisgosampaio.com.conf`. Envia-o para a VPS e configura:

```bash
# Da tua maquina local, envia o ficheiro
scp deploy/nginx/sessaoadois.luisgosampaio.com.conf \
    <USER>@<IP>:/tmp/sessaoadois.luisgosampaio.com

# Na VPS
sudo mv /tmp/sessaoadois.luisgosampaio.com /etc/nginx/sites-available/
sudo ln -s /etc/nginx/sites-available/sessaoadois.luisgosampaio.com \
           /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

### 4.3 O que o Nginx faz

| Location | Destino | Descricao |
|----------|---------|-----------|
| `/` | `/var/www/sessaoadois/` | SPA React (fallback para `index.html`) |
| `/api/` | `localhost:8085` | Proxy reverso para a API Spring Boot |
| `/ws/` | `localhost:8085/ws/` | WebSocket/STOMP com headers de Upgrade |

### 4.4 Emitir certificado SSL

```bash
sudo certbot --nginx -d sessaoadois.luisgosampaio.com
```

O Certbot modifica automaticamente o conf do Nginx para:
- Adicionar o bloco `server` na porta 443 com SSL
- Redirecionar HTTP (80) para HTTPS (443)
- Renovacao automatica via cron/systemd timer

---

## Passo 5 - Executar o deploy

De volta a tua maquina local, na raiz do repo:

```bash
./scripts/deploy.sh
```

O script executa 5 etapas automaticamente:

| Etapa | O que faz |
|-------|-----------|
| 1/5 | `bun install` + `bun run build` no `client/` (usa `client/.env.production` com `VITE_API_URL=https://sessaoadois.luisgosampaio.com`) |
| 2/5 | `docker build --platform linux/amd64` da API no `api/` (multi-stage: JDK 21 build + JRE 21 runtime) |
| 3/5 | `docker save \| gzip` — exporta a imagem como `.tar.gz` |
| 4/5 | SCP para a VPS: frontend, imagem, `docker-compose-prod.yml` e `.env` |
| 5/5 | SSH na VPS: `docker load` + `docker compose down` + `docker compose up -d` |

Ao final mostra os logs da API e o status do container.

---

## Passo 6 - Verificar

```bash
# Status do container na VPS
ssh <USER>@<IP> "cd ~/projects/sessao-a-dois && docker compose -f docker-compose-prod.yml ps"

# Logs da API
ssh <USER>@<IP> "cd ~/projects/sessao-a-dois && docker compose -f docker-compose-prod.yml logs --tail 50 api"

# Testar no browser
open https://sessaoadois.luisgosampaio.com
```

---

## Deploys seguintes

Apos a configuracao inicial (passos 1-4), basta repetir:

```bash
./scripts/deploy.sh
```

O script cuida de tudo: build, envio e deploy. Imagens antigas sao limpas automaticamente (mantem as 3 ultimas versoes).

---

## Troubleshooting

### API nao inicia

```bash
# Ver logs completos
ssh <USER>@<IP> "cd ~/projects/sessao-a-dois && docker compose -f docker-compose-prod.yml logs api"
```

Causas comuns:
- `DB_URL` incorreta (verificar connection string do Supabase)
- `TMDB_API_KEY` vazia (TmdbConfig falha ao subir sem ela)
- `JWT_SECRET` com menos de 32 bytes

### Nginx retorna 502 Bad Gateway

A API ainda nao subiu ou crashou. Verificar:

```bash
# Container esta rodando?
ssh <USER>@<IP> "docker ps"

# Porta 8085 esta escutando?
ssh <USER>@<IP> "curl -s http://localhost:8085/api/health || echo 'API offline'"
```

### Frontend carrega mas API falha (CORS)

Verificar que `CORS_ALLOWED_ORIGIN` no `.env` corresponde exatamente ao dominio com `https://`.

### Certificado SSL expirado

```bash
ssh <USER>@<IP> "sudo certbot renew"
```

---

## Estrutura de ficheiros na VPS

```
~/projects/sessao-a-dois/
  ├── docker-compose-prod.yml    # Compose so com a API
  └── .env                       # Segredos (DB, JWT, TMDB, etc.)

/var/www/sessaoadois/            # Build estatico do frontend
  ├── index.html
  ├── assets/
  └── ...

/etc/nginx/sites-available/
  └── sessaoadois.luisgosampaio.com   # Config Nginx + SSL (Certbot)
```
