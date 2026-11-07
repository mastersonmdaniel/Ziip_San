#!/usr/bin/env bash
# ED-04 -- sobe o pacote de reversao na ordem certa e espera a pilha
# ficar pronta.
#
# Ordem (a mesma do ambiente real, scripts/local-env.sh): primeiro a
# infraestrutura, depois o job migrate, por fim a api. A api NAO depende
# do servico migrate no compose de proposito (ver o cabecalho do
# docker-compose.reversao.yml): quem garante a ordem e este script.
# Sem servico worker: ele nao existe no ambiente da P0.27.
set -euo pipefail
cd "$(dirname "$0")"

[ -f .env.reversao ] || { echo "ERRO: .env.reversao nao encontrado (copie de .env.reversao.example e preencha fora do repositorio)"; exit 1; }
command -v docker >/dev/null || { echo "ERRO: docker nao encontrado (ver REQUISITOS-MINIMOS)"; exit 1; }
docker compose version >/dev/null || { echo "ERRO: docker compose v2 nao encontrado"; exit 1; }

# Le uma variavel do .env.reversao SEM executar o arquivo (sem source:
# valores com espacos ou $ nao sao expandidos por este script).
ler_var() {
  grep -E "^$1=" .env.reversao 2>/dev/null | head -n 1 | cut -d= -f2- | tr -d '\r' || true
}
REVERSAO_KC_PORT="$(ler_var REVERSAO_KC_PORT)"; REVERSAO_KC_PORT="${REVERSAO_KC_PORT:-8080}"
REVERSAO_API_PORT="$(ler_var REVERSAO_API_PORT)"; REVERSAO_API_PORT="${REVERSAO_API_PORT:-8090}"

export COMPOSE_FILE=docker-compose.reversao.yml
COMPOSE="docker compose --env-file .env.reversao"

# 1. Infraestrutura: banco, identidade, cofre e armazenamento S3.
$COMPOSE up -d postgres-primary keycloak vault minio

# 2. Espera o realm ziipsan do Keycloak responder (realm importado na
#    subida). O Keycloak leva tempo para build/start: timeout de ~180s.
echo "Aguardando o realm ziipsan do Keycloak (ate ~180s)..."
PRONTO=""
for _ in $(seq 1 90); do
  COD=$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:${REVERSAO_KC_PORT}/realms/ziipsan" || true)
  if [ "$COD" = "200" ]; then PRONTO=1; break; fi
  sleep 2
done
[ -n "$PRONTO" ] || { echo "ERRO: Keycloak nao respondeu com o realm ziipsan em http://127.0.0.1:${REVERSAO_KC_PORT}/realms/ziipsan apos ~180s; verifique 'docker compose --env-file .env.reversao logs keycloak'"; exit 1; }
echo "Keycloak pronto (realm ziipsan respondendo)."

# 3. Migrations (job unico; sai apos migrar).
$COMPOSE run --rm migrate

# 4. API.
$COMPOSE up -d api

# 5. Espera a api responder no endpoint de versao/ambiente (~120s).
echo "Aguardando a api em /api/v1/about (ate ~120s)..."
PRONTO=""
for _ in $(seq 1 60); do
  COD=$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:${REVERSAO_API_PORT}/api/v1/about" || true)
  if [ "$COD" = "200" ]; then PRONTO=1; break; fi
  sleep 2
done
[ -n "$PRONTO" ] || { echo "ERRO: api nao respondeu com HTTP 200 em http://127.0.0.1:${REVERSAO_API_PORT}/api/v1/about apos ~120s; verifique 'docker compose --env-file .env.reversao logs api'"; exit 1; }

echo "Pacote de reversao iniciado. Rode ./testes-fumaca.sh para a fumaca."
