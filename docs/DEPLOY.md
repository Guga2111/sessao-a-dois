# Deploy - Sessao a Dois

Tutorial completo para colocar a aplicacao em producao na VPS Hostinger.

## Arquitetura de Producao

```
Maquina local OU runner do GitHub          VPS Hostinger
(mesmo scripts/deploy.sh)                  ──────────────
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

### VPS Hostinger

- **Docker** + **docker compose** instalados
- **Nginx** instalado
- **Certbot** instalado (para SSL Let's Encrypt)

### Servicos externos

- **Supabase**: projeto criado com banco PostgreSQL
- **TMDB**: conta criada com API Read Access Token (v4)
- **Dominio**: `sessaoadois.luisgosampaio.com` apontando para o IP da VPS (registro A no DNS)

---

## Passo 1 - Criar o banco no Supabase

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

## Passo 2 - Preencher o .env

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

# API
API_PORT=8085

# Autenticacao (Epico 4 - cookie HttpOnly + refresh token, todas opcionais,
# ja tem default seguro em api/src/main/resources/application.properties)
# JWT_ACCESS_TOKEN_TTL=15m
# JWT_ISSUER=sessao-a-dois
# JWT_AUDIENCE=sessao-a-dois-client
# AUTH_COOKIE_SECURE=true
# AUTH_REFRESH_TOKEN_TTL=30d
# AUTH_REFRESH_REUSE_GRACE=30s
```

> O `.env` esta no `.gitignore` e nunca e commitado. E usado tanto pelo `docker-compose-dev.yml` (dev local) quanto pelo `scripts/deploy.sh` (producao).

> **Variaveis novas do Epico 4 (migracao de autenticacao):** `JWT_ACCESS_TOKEN_TTL`
> (default `15m`, substitui a antiga `JWT_EXPIRATION_DAYS` de 7 dias, que foi
> REMOVIDA de `application.properties` e nao tem mais efeito nenhum se definida),
> `JWT_ISSUER`/`JWT_AUDIENCE` (validados no token, default `sessao-a-dois`/
> `sessao-a-dois-client`), `AUTH_COOKIE_SECURE` (default `true` - so vale `false`
> em dev local sem HTTPS), `AUTH_REFRESH_TOKEN_TTL` (default `30d`, TTL
> deslizante do refresh token) e `AUTH_REFRESH_REUSE_GRACE` (default `30s`,
> janela de graca da deteccao de reuso). Todas tem default de producao seguro -
> so precisam ir no `.env` se voce quiser um valor diferente do default.
>
> **Aviso de deploy:** a primeira execucao com este epico troca o modelo de
> sessao inteiro (de JWT em `localStorage` para cookies HttpOnly + refresh
> token). Isso desloga TODOS os usuarios uma unica vez nesse deploy - a sessao
> antiga simplesmente para de ser reconhecida, nao ha migracao de sessao
> existente. Com ~8 usuarios conhecidos (ver `docs/BACKLOG.md`/decisao #3
> SUPERADA), o cutover e direto e sem janela de transicao; avisar os dois
> usuarios do casal antes do deploy para que nao estranhem o pedido de login.

---

## Passo 3 - Configurar Nginx na VPS (primeira vez)

Conecta a VPS e executa:

```bash
ssh root@31.97.169.38
```

### 3.1 Criar o diretorio do frontend

```bash
sudo mkdir -p /var/www/sessaoadois
sudo chown $USER:$USER /var/www/sessaoadois
```

### 3.2 Instalar a configuracao do Nginx

O ficheiro de configuracao esta no repo em `deploy/nginx/sessaoadois.luisgosampaio.com.conf`. Envia-o para a VPS e configura:

```bash
# Da tua maquina local, envia o ficheiro
scp deploy/nginx/sessaoadois.luisgosampaio.com.conf \
    root@31.97.169.38:/tmp/sessaoadois.luisgosampaio.com

# Na VPS
sudo mv /tmp/sessaoadois.luisgosampaio.com /etc/nginx/sites-available/
sudo ln -s /etc/nginx/sites-available/sessaoadois.luisgosampaio.com \
           /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

### 3.3 O que o Nginx faz

| Location | Destino | Descricao |
|----------|---------|-----------|
| `/` | `/var/www/sessaoadois/` | SPA React (fallback para `index.html`) |
| `/api/` | `localhost:8085` | Proxy reverso para a API Spring Boot |
| `/ws/` | `localhost:8085/ws/` | WebSocket/STOMP com headers de Upgrade |

### 3.4 Criar o diretorio da trilha de auditoria (uma vez, antes do primeiro deploy com esta mudanca)

`docker-compose-prod.yml` monta um volume da VPS para o log de auditoria de
seguranca (`security.audit`, ver "Seguranca operacional" abaixo), para que ele
sobreviva ao `docker compose down` + `up` da etapa 5/5 do `scripts/deploy.sh`
(sem o bind mount, o log ficaria dentro do container e desapareceria a cada
recriacao). Antes do primeiro deploy com esta mudanca, cria o diretorio na VPS:

```bash
ssh root@31.97.169.38 "mkdir -p /var/lib/sessao-a-dois/security-audit-logs"
ssh root@31.97.169.38 "chown -R 10001:10001 /var/lib/sessao-a-dois/security-audit-logs"
```

Se o diretorio nao existir, o `docker compose up` cria-o automaticamente como
`root` (comportamento padrao do Docker para bind mounts inexistentes) - o
`mkdir` acima e so para deixar explicito e evitar surpresas de permissao.

**O `chown` e obrigatorio e nao e cosmetico.** Desde o Epico 8 (US-002) o
`api/Dockerfile` roda o processo Java como o usuario nao-privilegiado `app`,
de UID/GID fixos `10001` **dentro do container**. O diretorio do bind mount
pertence a `root` na VPS, entao sem o `chown` o `RollingFileAppender` do logger
`security.audit` nao consegue criar `security-audit.log` - e o Logback **nao
derruba a aplicacao por causa disso**: a API sobe normalmente, o site funciona,
e a trilha de auditoria simplesmente deixa de existir, em silencio. Ou seja, a
falha so aparece no dia em que alguem precisar investigar um incidente e
descobrir que nao ha registro. Por isso o `chown` roda **antes** do primeiro
deploy com container nao-root, nao depois.

Verificacao apos o primeiro deploy com esta mudanca (um login deve gerar linha
nova no arquivo):

```bash
ssh root@31.97.169.38 "ls -l /var/lib/sessao-a-dois/security-audit-logs/"
ssh root@31.97.169.38 "tail -f /var/lib/sessao-a-dois/security-audit-logs/security-audit.log"
```

### 3.5 Emitir certificado SSL

```bash
sudo certbot --nginx -d sessaoadois.luisgosampaio.com
```

O Certbot modifica automaticamente o conf do Nginx para:
- Adicionar o bloco `server` na porta 443 com SSL
- Redirecionar HTTP (80) para HTTPS (443)
- Renovacao automatica via cron/systemd timer

---

## Passo 4 - Executar o deploy

De volta a tua maquina local, na raiz do repo:

```bash
./scripts/deploy.sh
```

> A partir da automacao de CD (ver "CI/CD - deploy automatico no merge para a
> main"), este passo manual so e necessario no primeiro deploy ou em emergencia -
> o merge de `dev` para `main` executa exatamente este mesmo script no runner.

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

## Passo 5 - Verificar

```bash
# Status do container na VPS
ssh root@31.97.169.38 "cd ~/projects/sessao-a-dois && docker compose -f docker-compose-prod.yml ps"

# Logs da API
ssh root@31.97.169.38 "cd ~/projects/sessao-a-dois && docker compose -f docker-compose-prod.yml logs --tail 50 api"

# Testar no browser
open https://sessaoadois.luisgosampaio.com
```

---

## Deploys seguintes

Apos a configuracao inicial (passos 1-3), o deploy e **automatico**: todo merge de
um PR de `dev` para `main` dispara o pipeline de CD (ver seccao abaixo). O
`./scripts/deploy.sh` continua a funcionar a partir da maquina local e e o mesmo
script que o CI executa - fica como via manual para emergencias e para quando a
Action estiver indisponivel.

O script cuida de tudo: build, envio e deploy. Imagens antigas sao limpas automaticamente (mantem as 3 ultimas versoes).

### Variaveis de configuracao do script

| Variavel | Default | Para que serve |
|----------|---------|----------------|
| `VPS_IP` | `31.97.169.38` | Host de destino |
| `VPS_USER` | `root` | Utilizador SSH (decisao D10: mantem-se `root`) |
| `ENV_FILE` | `<raiz>/.env` | Ficheiro de segredos enviado por scp para a VPS |

Nenhum segredo passa por `/tmp`: o `ENV_FILE` e copiado diretamente para
`~/projects/sessao-a-dois/.env` na VPS.

---

## CI/CD - deploy automatico no merge para a main

`.github/workflows/deploy.yml` executa o `scripts/deploy.sh` num runner do GitHub
sempre que ha um push na `main` (que e o que um merge de PR produz), e tambem
manualmente por `workflow_dispatch` na aba **Actions**.

```
PR dev -> main mergeado
        │
        ▼
job "tests"  ── reutiliza .github/workflows/ci.yml (workflow_call)
        │       API: ./mvnw -B test  |  client: typecheck + lint + build
        ▼ (so avanca se tudo passar)
job "deploy" ── bun build + docker build + scp + docker compose up -d
        │
        ▼
smoke test: curl https://sessaoadois.luisgosampaio.com/api/health (12 tentativas, 10s)
```

Pontos de desenho:

- **A suite roda outra vez no codigo ja mergeado.** O CI do PR valida a branch
  antes do merge; este job valida o resultado do merge, e cobre tambem push
  direto na `main`.
- **`concurrency: deploy-production` com `cancel-in-progress: false`.** Dois
  deploys em paralelo na mesma VPS deixariam o `docker compose down`/`up` num
  estado indefinido; cancelar um deploy a meio seria pior ainda, por isso o
  segundo espera em vez de matar o primeiro.
- **Rollback:** `git revert` do commit na `main` e merge - o pipeline redeploya
  a versao anterior sozinho. Nao ha botao de rollback separado.

### Secrets e variables a configurar no GitHub

Em **Settings > Secrets and variables > Actions** (podem ficar no nivel do repo
ou no environment `production`, que o workflow referencia):

**Secrets** (obrigatorios - o deploy falha explicitamente se algum faltar):

| Secret | Conteudo |
|--------|----------|
| `VPS_SSH_KEY` | Chave **privada** SSH (ed25519, sem passphrase) com acesso ao `root@31.97.169.38` |
| `DB_URL` | Connection string do Supabase |
| `DB_USER` | Utilizador do Supabase |
| `DB_PASSWORD` | Password do Supabase |
| `JWT_SECRET` | Segredo de assinatura (>= 32 bytes, `openssl rand -base64 48`) |
| `TMDB_API_KEY` | API Read Access Token v4 do TMDB |

**Secret opcional:**

| Secret | Conteudo |
|--------|----------|
| `VPS_SSH_KNOWN_HOSTS` | Saida de `ssh-keyscan -H 31.97.169.38`. Se estiver definido, a host key fica fixada; se nao, o workflow faz `ssh-keyscan` a cada execucao (trust-on-first-use, aceitavel mas menos seguro). |

**Variables** (nao sao segredos; todas tem default no workflow, so definir para
mudar o alvo): `VPS_IP`, `VPS_USER`, `APP_URL`, `CORS_ALLOWED_ORIGIN`, `API_PORT`.

### Gerar a chave de deploy

Na tua maquina local:

```bash
ssh-keygen -t ed25519 -C "github-actions-deploy" -f ~/.ssh/sessao_deploy -N ""

# Autorizar a chave publica na VPS
ssh-copy-id -i ~/.ssh/sessao_deploy.pub root@31.97.169.38

# Conteudo para o secret VPS_SSH_KEY (a chave PRIVADA, ficheiro inteiro)
cat ~/.ssh/sessao_deploy

# Conteudo para o secret opcional VPS_SSH_KNOWN_HOSTS
ssh-keyscan -H 31.97.169.38
```

> A chave privada **nunca** entra no repo - so no secret do GitHub. O workflow
> escreve-a em `~/.ssh/id_deploy` do runner (efemero) e apaga-a com `shred` num
> passo `if: always()`, tal como faz ao `.env`.

### Requisitos na VPS

Nenhum. A imagem continua a viajar como `.tar.gz` por scp e a ser carregada com
`docker load` - nao ha registry, nem `docker login`, nem nada novo para instalar.
O que ja estava configurado nos passos 1-3 continua a ser suficiente.

---

## Troubleshooting

### API nao inicia

```bash
# Ver logs completos
ssh root@31.97.169.38 "cd ~/projects/sessao-a-dois && docker compose -f docker-compose-prod.yml logs api"
```

Causas comuns:
- `DB_URL` incorreta (verificar connection string do Supabase)
- `TMDB_API_KEY` vazia (TmdbConfig falha ao subir sem ela)
- `JWT_SECRET` com menos de 32 bytes

### Nginx retorna 502 Bad Gateway

A API ainda nao subiu ou crashou. Verificar:

```bash
# Container esta rodando?
ssh root@31.97.169.38 "docker ps"

# Porta 8085 esta escutando?
ssh root@31.97.169.38 "curl -s http://localhost:8085/api/health || echo 'API offline'"
```

### Frontend carrega mas API falha (CORS)

Verificar que `CORS_ALLOWED_ORIGIN` no `.env` corresponde exatamente ao dominio com `https://`.

### Certificado SSL expirado

```bash
ssh root@31.97.169.38 "sudo certbot renew"
```

---

## Seguranca operacional

### JWT no access.log do Nginx (corrigido)

O handshake do WebSocket carrega o JWT na query string (`/ws/websocket?token=...`).
Ate esta correcao, `location /ws/` no `deploy/nginx/sessaoadois.luisgosampaio.com.conf`
nao tinha `access_log off;`, entao cada conexao gravava uma credencial valida em
texto claro em `/var/log/nginx/access.log`. O conf atual ja inclui `access_log off;`
so no bloco `/ws/` - `/api/` e `/` continuam com o access log normal.

**Esta instrucao CONTINUA valendo depois da migracao de autenticacao (Epico 4):**
o handshake do WebSocket parou de carregar o token na query string (`?token=...`)
e passou a autenticar pelo cookie `access_token`, HttpOnly (US-009/US-012) - a
URL do `/ws` hoje nao carrega mais nenhuma credencial. Ainda assim, `access_log off;`
no bloco `/ws/` deve permanecer no conf: e defesa em profundidade barata (uma
linha de nginx) contra qualquer regressao futura que volte a colocar algo
sensivel na URL do handshake, e nao ha custo em manter.

Para que a correcao valha em producao:

**(a) Reinstalar o conf na VPS e recarregar o Nginx**

```bash
scp deploy/nginx/sessaoadois.luisgosampaio.com.conf \
    root@31.97.169.38:/tmp/sessaoadois.luisgosampaio.com
ssh root@31.97.169.38 "sudo mv /tmp/sessaoadois.luisgosampaio.com /etc/nginx/sites-available/sessaoadois.luisgosampaio.com && sudo nginx -t && sudo systemctl reload nginx"
```

**(b) Purgar os logs existentes (incluindo os rotacionados)**

Os logs antigos ja gravados contem JWTs validos e precisam ser apagados, nao so
o `access.log` atual mas tambem toda a rotacao (`access.log.1`, `access.log.2.gz`,
etc. - o `logrotate` padrao do Nginx mantem varias gerações comprimidas):

```bash
ssh root@31.97.169.38 "sudo truncate -s 0 /var/log/nginx/access.log && sudo rm -f /var/log/nginx/access.log.*"
```

**(c) Janela de validade dos tokens vazados**

`JWT_ACCESS_TOKEN_TTL` (default `15m`, ver `api/src/main/resources/application.properties`;
substituiu a antiga `JWT_EXPIRATION_DAYS` de 7 dias) significa que qualquer token
que aparece em logs de ate 15 minutos antes da purga ainda pode ser valido no
momento da purga. Purgar os logs sozinho nao invalida os tokens ja emitidos -
so evita que novos handshakes continuem vazando.

**(d) Rotacionar o `JWT_SECRET`**

Para invalidar de fato qualquer token ja vazado nos logs historicos (nao so
impedir vazamentos futuros), o segredo de assinatura precisa mudar - isso
invalida todas as sessoes ativas, entao os dois usuarios do casal precisam
logar de novo:

```bash
openssl rand -base64 48
```

Atualizar `JWT_SECRET` no `.env` da VPS (`~/projects/sessao-a-dois/.env`, ver
"Passo 2" acima) com o valor gerado e reiniciar o container da API:

```bash
ssh root@31.97.169.38 "cd ~/projects/sessao-a-dois && docker compose -f docker-compose-prod.yml up -d --force-recreate api"
```

O procedimento completo (a)-(d) aplicado na VPS, incluindo a verificacao pratica
de que nenhuma linha nova de `/ws/` grava `token=`, fica registrado como tarefa
operacional separada (ver US-010 do epico de contencao de seguranca).

### Headers de seguranca HTTP e CSP

`deploy/nginx/sessaoadois.luisgosampaio.com.conf` declara um bloco `server`
para a porta 443 com 4 headers, todos com `always` (para saírem tambem em
respostas de erro 4xx/5xx):

- `Strict-Transport-Security` - forca HTTPS em todas as visitas seguintes.
- `X-Content-Type-Options: nosniff` - impede o browser de "adivinhar" o
  content-type e executar um asset como script.
- `Referrer-Policy: strict-origin-when-cross-origin` - nao vaza a URL completa
  (que pode conter dados sensiveis) para terceiros em requisicoes cross-origin.
- `Content-Security-Policy` - restringe de onde o app pode carregar scripts,
  estilos, imagens e conexoes:
  `default-src 'self'; img-src 'self' https://image.tmdb.org data:; style-src 'self' 'unsafe-inline'; font-src 'self' data:; connect-src 'self' wss://sessaoadois.luisgosampaio.com; frame-ancestors 'none'; base-uri 'self'`.

**Por que nao ha nenhum host do Google Fonts na CSP:** as fontes (`Bricolage
Grotesque`, `DM Sans`, `Instrument Sans`) sao servidas via pacotes
`@fontsource*` (`client/src/index.css`), empacotadas no build do Vite e
servidas pelo proprio dominio (`self`) - nao ha nenhuma requisicao a
`fonts.googleapis.com`/`fonts.gstatic.com` em producao. Se algum dia alguem
"corrigir" um erro de fonte adicionando `https://fonts.googleapis.com` de
volta a CSP, primeiro confira `client/src/index.css`: o problema quase certamente
esta em outro lugar (fonte nao instalada, build sem o CSS importado), nao na
CSP.

**Onde esses headers realmente vivem na VPS:** o bloco 443 e normalmente
gerado e mantido pelo Certbot (`sudo certbot --nginx -d sessaoadois.luisgosampaio.com`),
que copia as `location`s do bloco 80 e adiciona `listen 443 ssl;` +
`ssl_certificate`/`ssl_certificate_key`. O Certbot **nao sabe** desses 4
headers - eles precisam ser colados manualmente dentro do bloco 443 gerado
por ele. **Se o certificado for reemitido do zero** (`certbot --nginx` de
novo do zero, ou uma renovacao com `--force-renewal` que reescreva o bloco),
o Certbot sobrescreve o `server { listen 443 ... }` e os 4 headers somem -
precisam ser reaplicados manualmente depois. A aplicacao pratica na VPS e a
verificacao (`curl -I`, console do browser sem violacao de CSP) ficam
registradas em US-010.

### Trilha de auditoria de seguranca (security.audit)

`com.app.security.SecurityAuditLogger` (US-012) emite eventos de seguranca
(login OK/falho, logout, refresh, deteccao de reuso de refresh token, criacao
e entrada em casal, regeneracao de codigo de convite, bloqueio por rate limit)
no logger dedicado `security.audit`, sempre com o correlation id da requisicao
(US-011). Nenhuma linha carrega senha, token em claro/hash ou o valor do
codigo de convite - so identificadores (id, e-mail, IP).

**Onde o arquivo vive:** `api/src/main/resources/logback-spring.xml` declara
um `RollingFileAppender` so para esse logger, escrevendo em
`app.security.audit-log.path` (env `SECURITY_AUDIT_LOG_PATH`, default
`/var/log/sessao-a-dois/security-audit.log` **dentro do container**). O
`docker-compose-prod.yml` monta esse caminho a partir de
`/var/lib/sessao-a-dois/security-audit-logs` na VPS (ver "3.4 Criar o
diretorio da trilha de auditoria" acima) - por isso o arquivo sobrevive a um
`docker compose down` + `up`, ao contrario do resto do filesystem do
container. Como o processo Java roda como o usuario `app` (UID 10001), o
diretorio na VPS precisa pertencer a esse UID - ver o `chown` da secao 3.4. O logger continua saindo tambem no `stdout` normal (`docker compose
logs`), o arquivo e so a copia persistente.

**Politica de rotacao:** 10MB por arquivo, historico de 10 arquivos, teto
total de 100MB (`totalSizeCap`) - ao ultrapassar o teto, os arquivos mais
antigos sao descartados automaticamente pelo Logback, o disco da VPS nunca
cresce sem limite.

**Como consultar:**

```bash
# Direto no host (o volume e um bind mount, nao e preciso entrar no container)
ssh root@31.97.169.38 "tail -f /var/lib/sessao-a-dois/security-audit-logs/security-audit.log"

# Ou via docker compose, de dentro do container
ssh root@31.97.169.38 "cd ~/projects/sessao-a-dois && docker compose -f docker-compose-prod.yml exec api tail -f /var/log/sessao-a-dois/security-audit.log"

# Buscar por um correlation id especifico (cruzar com o header X-Request-Id devolvido ao cliente)
ssh root@31.97.169.38 "grep 'correlationId=<id>' /var/lib/sessao-a-dois/security-audit-logs/security-audit.log"
```

Rodar a aplicacao localmente sem o diretorio `/var/log/sessao-a-dois` montado
nao quebra o startup - o `RollingFileAppender` cria o diretorio e o arquivo
sozinho na primeira escrita (testado tambem em `api/src/test/resources/application.properties`,
que aponta `app.security.audit-log.path` para `target/test-logs/` em vez do
default de producao, para nao tentar escrever em `/var/log` durante os testes).

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

/var/lib/sessao-a-dois/security-audit-logs/   # Trilha de auditoria (bind mount, sobrevive a down+up)
  └── security-audit.log

/etc/nginx/sites-available/
  └── sessaoadois.luisgosampaio.com   # Config Nginx + SSL (Certbot)
```
