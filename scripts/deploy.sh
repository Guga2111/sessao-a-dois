#!/usr/bin/env bash
#
# deploy.sh - Deploy de producao do "Sessao a Dois" (sessaoadois.luisgosampaio.com).
#
# Faz, em ordem:
#   1. Build estatico do frontend (client/) com `bun run build`.
#   2. Envio do build (client/dist) para a VPS por SCP (rsync-free), no diretorio
#      servido pelo Nginx (root /var/www/sessaoadois - veja deploy/nginx/).
#   3. Sobe/atualiza o backend na VPS por SSH:
#      `docker compose -f docker-compose-prod.yml up -d --build`.
#
# ---------------------------------------------------------------------------
# PRE-REQUISITOS
#   - Chave SSH ja configurada para acesso sem senha ao usuario/host da VPS
#     (ssh-agent carregado ou ~/.ssh/config apontando a chave certa).
#   - `bun` instalado localmente (build do frontend).
#   - Na VPS: repositorio ja clonado em ${REMOTE_APP_DIR} contendo
#     docker-compose-prod.yml e um arquivo .env com os segredos reais
#     (veja .env.example). Docker + docker compose instalados na VPS.
#   - Diretorio web ${REMOTE_WEB_ROOT} existente e gravavel pelo ${SSH_USER}
#     (ou o usuario com permissao de sudo, conforme sua configuracao).
#
# CONFIGURACAO (variaveis de ambiente - SEM valores hardcoded)
#   Leia de um arquivo .env na raiz do repo (git-ignorado) ou exporte no shell:
#     SSH_HOST         host/IP da VPS                (obrigatoria)
#     SSH_USER         usuario SSH                   (obrigatoria)
#     SSH_PORT         porta SSH                     (opcional, default 22)
#     REMOTE_APP_DIR   dir do repo na VPS            (opcional, default ~/sessao-a-dois)
#     REMOTE_WEB_ROOT  root do Nginx na VPS          (opcional, default /var/www/sessaoadois)
#
# USO
#   ./scripts/deploy.sh
#   SSH_HOST=1.2.3.4 SSH_USER=deploy ./scripts/deploy.sh
# ---------------------------------------------------------------------------

set -euo pipefail

# Diretorios do repo (o script funciona a partir de qualquer CWD).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Carrega variaveis do .env da raiz, se existir (nao sobrescreve as ja exportadas).
if [[ -f "${REPO_ROOT}/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${REPO_ROOT}/.env"
  set +a
fi

# Variaveis obrigatorias (fail-fast se ausentes).
: "${SSH_HOST:?defina SSH_HOST (host/IP da VPS) no .env ou no ambiente}"
: "${SSH_USER:?defina SSH_USER (usuario SSH) no .env ou no ambiente}"

# Variaveis com default.
SSH_PORT="${SSH_PORT:-22}"
REMOTE_APP_DIR="${REMOTE_APP_DIR:-~/sessao-a-dois}"
REMOTE_WEB_ROOT="${REMOTE_WEB_ROOT:-/var/www/sessaoadois}"

echo "==> [1/3] Build do frontend (bun run build)"
cd "${REPO_ROOT}/client"
bun run build

echo "==> [2/3] Enviando client/dist para ${SSH_USER}@${SSH_HOST}:${REMOTE_WEB_ROOT} (porta ${SSH_PORT})"
# Recria o conteudo do diretorio web na VPS a partir do build local.
scp -P "${SSH_PORT}" -r "${REPO_ROOT}/client/dist/." \
  "${SSH_USER}@${SSH_HOST}:${REMOTE_WEB_ROOT}/"

echo "==> [3/3] Subindo o backend na VPS (docker compose up -d --build)"
ssh -p "${SSH_PORT}" "${SSH_USER}@${SSH_HOST}" \
  "cd ${REMOTE_APP_DIR} && docker compose -f docker-compose-prod.yml up -d --build"

echo "==> Deploy concluido com sucesso: https://${DOMAIN:-sessaoadois.luisgosampaio.com}"
