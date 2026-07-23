#!/usr/bin/env bash
#
# deploy.sh - Deploy de producao do "Sessao a Dois" (sessaoadois.luisgosampaio.com).
#
# Faz, em ordem:
#   1. Build estatico do frontend (client/) com `bun run build`.
#   2. Build da imagem Docker da API localmente (linux/amd64).
#   3. Exporta a imagem como .tar.gz e envia tudo para a VPS por SCP.
#   4. Na VPS: carrega a imagem e sobe via docker compose.
#
# ---------------------------------------------------------------------------
# PRE-REQUISITOS
#   - Chave SSH configurada para acesso sem senha ao usuario/host da VPS.
#   - `bun` instalado localmente (build do frontend).
#   - `docker` instalado localmente (build da imagem).
#   - Na VPS: Docker + docker compose instalados.
#   - Diretorio web /var/www/sessaoadois existente e gravavel pelo SSH_USER.
#
# CONFIGURACAO
#   Leia de um arquivo .env na raiz do repo (git-ignorado) ou exporte no shell.
#   Veja .env.example para a lista completa de variaveis.
# ---------------------------------------------------------------------------

set -euo pipefail

PROJECT="sessao-a-dois"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
CLIENT_DIR="${REPO_ROOT}/client"
API_DIR="${REPO_ROOT}/api"

# Carrega variaveis do .env da raiz, se existir (nao sobrescreve as ja exportadas).
if [[ -f "${REPO_ROOT}/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${REPO_ROOT}/.env"
  set +a
fi

# Variaveis obrigatorias.
: "${SSH_HOST:?defina SSH_HOST (host/IP da VPS) no .env ou no ambiente}"
: "${SSH_USER:?defina SSH_USER (usuario SSH) no .env ou no ambiente}"

# Variaveis com default.
SSH_PORT="${SSH_PORT:-22}"
API_PORT="${API_PORT:-8085}"

VERSION=$(date +%Y%m%d-%H%M%S)

echo "==> Deploying ${PROJECT} ${VERSION}..."

# ====================
# 1. BUILD FRONTEND
# ====================
echo "==> [1/5] Build do frontend (bun run build)"
cd "${CLIENT_DIR}"
bun install
bun run build

# ====================
# 2. BUILD DOCKER IMAGE
# ====================
echo "==> [2/5] Build da imagem Docker (linux/amd64)"
cd "${API_DIR}"
docker build --platform linux/amd64 -t sessao-api:${VERSION} -t sessao-api:latest .

# ====================
# 3. SAVE DOCKER IMAGE
# ====================
echo "==> [3/5] Exportando imagem Docker"
cd "${REPO_ROOT}"
docker save sessao-api:${VERSION} sessao-api:latest | gzip > sessao-api-${VERSION}.tar.gz

FILE_SIZE=$(du -h sessao-api-${VERSION}.tar.gz | cut -f1)
echo "    Tamanho da imagem: ${FILE_SIZE}"

# ====================
# 4. UPLOAD TO VPS
# ====================
echo "==> [4/5] Enviando arquivos para a VPS"

ssh -p "${SSH_PORT}" ${SSH_USER}@${SSH_HOST} "mkdir -p ~/projects/${PROJECT} /var/www/sessaoadois"

# Frontend static files
scp -P "${SSH_PORT}" -r "${CLIENT_DIR}/dist/"* ${SSH_USER}@${SSH_HOST}:/var/www/sessaoadois/

# Backend image + compose file
scp -P "${SSH_PORT}" sessao-api-${VERSION}.tar.gz ${SSH_USER}@${SSH_HOST}:~/projects/${PROJECT}/
scp -P "${SSH_PORT}" "${REPO_ROOT}/docker-compose-prod.yml" ${SSH_USER}@${SSH_HOST}:~/projects/${PROJECT}/

# .env com segredos (gerado a partir das vars carregadas, nunca hardcoded na VPS)
cat > /tmp/${PROJECT}.env << EOF
DB_URL=${DB_URL}
DB_USER=${DB_USER}
DB_PASSWORD=${DB_PASSWORD}
JWT_SECRET=${JWT_SECRET}
CORS_ALLOWED_ORIGIN=${CORS_ALLOWED_ORIGIN}
TMDB_API_KEY=${TMDB_API_KEY}
API_PORT=${API_PORT}
EOF
scp -P "${SSH_PORT}" /tmp/${PROJECT}.env ${SSH_USER}@${SSH_HOST}:~/projects/${PROJECT}/.env
rm /tmp/${PROJECT}.env

# ====================
# 5. DEPLOY ON VPS
# ====================
echo "==> [5/5] Deployando na VPS"
ssh -p "${SSH_PORT}" ${SSH_USER}@${SSH_HOST} bash << ENDSSH
set -e
cd ~/projects/${PROJECT}

echo "    Carregando imagem Docker ${VERSION}..."
docker load < sessao-api-${VERSION}.tar.gz

echo "    Parando containers antigos..."
docker compose -f docker-compose-prod.yml down 2>/dev/null || true

echo "    Subindo containers..."
docker compose -f docker-compose-prod.yml up -d

echo "    Aguardando API iniciar..."
sleep 15

echo "    Status dos containers:"
docker compose -f docker-compose-prod.yml ps

echo "    Logs da API (ultimas 30 linhas):"
docker compose -f docker-compose-prod.yml logs --tail 30 api

echo "    Limpando imagem antiga..."
rm sessao-api-${VERSION}.tar.gz
docker images sessao-api --format "{{.Tag}}" | grep -v latest | tail -n +4 | xargs -I {} docker rmi sessao-api:{} 2>/dev/null || true
docker image prune -f

echo "    Deploy concluido na VPS!"
ENDSSH

# Limpeza local
rm sessao-api-${VERSION}.tar.gz

echo ""
echo "==> Deploy completo!"
echo "    https://sessaoadois.luisgosampaio.com"
echo "    Versao: ${VERSION}"
