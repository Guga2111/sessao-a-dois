#!/bin/bash
set -e

PROJECT="sessao-a-dois"

# Alvo do deploy. Configuravel por variavel de ambiente (o workflow de CD passa
# estes valores); os defaults sao a VPS Hostinger de producao.
VPS_IP="${VPS_IP:-31.97.169.38}"
VPS_USER="${VPS_USER:-root}"

BASE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." && pwd )"
CLIENT_DIR="${BASE_DIR}/client"
API_DIR="${BASE_DIR}/api"

# Ficheiro de segredos enviado para a VPS. E copiado por scp tal como esta -
# nenhum segredo e reescrito para /tmp nem para qualquer outro temporario.
ENV_FILE="${ENV_FILE:-${BASE_DIR}/.env}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "❌ Ficheiro de ambiente nao encontrado: ${ENV_FILE}"
  echo "   Local: copia .env.example para .env e preenche com valores reais."
  echo "   CI:    o workflow de CD escreve o .env a partir dos secrets do GitHub."
  exit 1
fi

VERSION=$(date +%Y%m%d-%H%M%S)

echo "🚀 Deploying Sessao a Dois ${VERSION}..."
echo "📁 Base: ${BASE_DIR}"

# ====================
# BUILD FRONTEND
# ====================
echo "🏗️  Building frontend..."
cd "${CLIENT_DIR}"
bun install
bun run build

# ====================
# BUILD BACKEND
# ====================
echo "📦 Building Docker image (linux/amd64)..."
cd "${API_DIR}"
docker build --platform linux/amd64 -t sessao-api:${VERSION} -t sessao-api:latest .

# ====================
# SAVE DOCKER IMAGE
# ====================
echo "💾 Saving Docker image..."
cd "${BASE_DIR}"
docker save sessao-api:${VERSION} sessao-api:latest | gzip > sessao-api-${VERSION}.tar.gz

FILE_SIZE=$(du -h sessao-api-${VERSION}.tar.gz | cut -f1)
echo "📦 Image size: ${FILE_SIZE}"

# ====================
# UPLOAD TO VPS
# ====================
echo "📤 Uploading to VPS..."

ssh ${VPS_USER}@${VPS_IP} "mkdir -p ~/projects/${PROJECT} /var/www/sessaoadois"

# Frontend static files
scp -r "${CLIENT_DIR}/dist/"* ${VPS_USER}@${VPS_IP}:/var/www/sessaoadois/

# Backend image + compose file
scp sessao-api-${VERSION}.tar.gz ${VPS_USER}@${VPS_IP}:~/projects/${PROJECT}/
scp "${BASE_DIR}/docker-compose-prod.yml" ${VPS_USER}@${VPS_IP}:~/projects/${PROJECT}/

# .env file — copiado diretamente da origem, sem passar por /tmp
scp "${ENV_FILE}" ${VPS_USER}@${VPS_IP}:~/projects/${PROJECT}/.env

# ====================
# DEPLOY ON VPS
# ====================
echo "🚀 Deploying on VPS..."
ssh ${VPS_USER}@${VPS_IP} bash << ENDSSH
set -e
cd ~/projects/${PROJECT}

echo "📦 Loading Docker image ${VERSION}..."
docker load < sessao-api-${VERSION}.tar.gz

echo "🛑 Stopping old containers..."
docker compose -f docker-compose-prod.yml down 2>/dev/null || true

echo "🚀 Starting containers..."
docker compose -f docker-compose-prod.yml up -d

echo "⏳ Waiting for API to start..."
sleep 15

echo "🔍 Container status:"
docker compose -f docker-compose-prod.yml ps

echo "📋 API logs (last 30 lines):"
docker compose -f docker-compose-prod.yml logs --tail 30 api

echo "🧹 Cleaning up..."
rm sessao-api-${VERSION}.tar.gz

# Keep only the 3 latest tagged versions
docker images sessao-api --format "{{.Tag}}" | grep -v latest | tail -n +4 | xargs -I {} docker rmi sessao-api:{} 2>/dev/null || true
docker image prune -f

echo "✅ ${VERSION} deployed successfully!"
ENDSSH

# Local cleanup
rm sessao-api-${VERSION}.tar.gz

echo ""
echo "✨ Deploy completo!"
echo "🌐 https://sessaoadois.luisgosampaio.com"
echo "📊 Versão: ${VERSION}"
