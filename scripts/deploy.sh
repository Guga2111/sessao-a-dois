#!/bin/bash
set -e

PROJECT="sessao-a-dois"
VPS_IP="31.97.169.38"
VPS_USER="root"

BASE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." && pwd )"
CLIENT_DIR="${BASE_DIR}/client"
API_DIR="${BASE_DIR}/api"

# Carrega variaveis do .env da raiz
if [[ ! -f "${BASE_DIR}/.env" ]]; then
  echo "❌ Ficheiro .env nao encontrado na raiz do repo."
  echo "   Copia .env.example para .env e preenche com valores reais."
  exit 1
fi

set -a
source "${BASE_DIR}/.env"
set +a

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

# .env file (written locally, uploaded — never hardcoded on the VPS in plaintext)
cat > /tmp/${PROJECT}.env << EOF
DB_URL=${DB_URL}
DB_USER=${DB_USER}
DB_PASSWORD=${DB_PASSWORD}
JWT_SECRET=${JWT_SECRET}
CORS_ALLOWED_ORIGIN=${CORS_ALLOWED_ORIGIN}
TMDB_API_KEY=${TMDB_API_KEY}
API_PORT=${API_PORT}
EOF
scp /tmp/${PROJECT}.env ${VPS_USER}@${VPS_IP}:~/projects/${PROJECT}/.env
rm /tmp/${PROJECT}.env

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
