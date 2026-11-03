#!/bin/bash
# P0.27: ambientes locais recriaveis do zero, por script, com tempo medido.
#
#   scripts/local-env.sh dev [--reset]        # PostgreSQL + Keycloak + PgBouncer + cofre + S3
#   scripts/local-env.sh homolog [--reset]    # dev + job migrate + api (imagem da P0.5)
#   scripts/local-env.sh demo [--reset]       # homolog (perfil de demonstracao local, P3.23:
#                                             # mesma pilha; a massa de demonstracao e da P3.23,
#                                             # nenhum dado real e carregado aqui)
#   scripts/local-env.sh down                 # para tudo (sem apagar volumes)
#   scripts/local-env.sh rollback             # volta a api para a imagem anterior
#                                             # (ZIIPSAN_APP_IMAGE_PREVIOUS) e mede o tempo
#
# Runtime: podman compose quando houver podman, senao docker compose. Nesta VM o
# daemon Docker e inutilizavel (pull falha, runc falha): use Podman com
# --storage-driver=vfs --runtime crun --cgroups=disabled --network=host no `run`
# (o compose do Podman ja resolve a rede interna sozinho; ver build-image.sh).
# Senhas: do .env na raiz (copie de .env.example). Sem elas o compose recusa subir.
# Portas: todas em 127.0.0.1 (conferido por scripts/check-local-profiles.sh).
# AWS: nenhum recurso — o script falha se o compose mencionar amazonaws.
# Tempo: cada execucao imprime o tempo decorrido (recriacao do zero medida).
set -euo pipefail
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$SRC"
START=$(date +%s)
PROFILE="${1:-dev}"
RESET=""
[ "${2:-}" = "--reset" ] && RESET="--reset"

[ -f "$SRC/.env" ] || { echo "ERRO: crie $SRC/.env a partir de .env.example" >&2; exit 1; }
# shellcheck disable=SC1091
set -a; source "$SRC/.env"; set +a

if command -v podman >/dev/null 2>&1 && podman compose version >/dev/null 2>&1; then
  COMPOSE=(podman compose -p ziipsan)
elif command -v docker >/dev/null 2>&1; then
  COMPOSE=(docker compose -p ziipsan)
else
  echo "ERRO: nem podman compose nem docker compose encontrados" >&2; exit 1
fi

"$SRC/scripts/local-tls.sh" >/dev/null
"$SRC/scripts/check-local-profiles.sh" "$SRC/docker-compose.yml"

profiles_for() {
  case "$1" in
    dev) echo "--profile storage --profile vault --profile pool" ;;
    homolog|demo) echo "--profile storage --profile vault --profile pool --profile app" ;;
    *) echo "ERRO: perfil desconhecido: $1 (dev|homolog|demo|down|rollback)" >&2; exit 1 ;;
  esac
}

elapsed() { echo "tempo decorrido: $(( $(date +%s) - START ))s"; }

case "$PROFILE" in
  down)
    "${COMPOSE[@]}" down --remove-orphans; elapsed; exit 0 ;;
  rollback)
    [ -n "${ZIIPSAN_APP_IMAGE_PREVIOUS:-}" ] || { echo "ERRO: defina ZIIPSAN_APP_IMAGE_PREVIOUS no .env" >&2; exit 1; }
    ZIIPSAN_APP_IMAGE="$ZIIPSAN_APP_IMAGE_PREVIOUS" "${COMPOSE[@]}" --profile app up -d api
    echo "rollback: api voltou para $ZIIPSAN_APP_IMAGE_PREVIOUS"; elapsed; exit 0 ;;
  dev|homolog|demo)
    if [ "$RESET" = "--reset" ]; then
      echo "APAGANDO volumes locais (banco, Keycloak, cofre, S3, WAL)..."
      "${COMPOSE[@]}" $(profiles_for "$PROFILE") down -v --remove-orphans || true
    fi
    # shellcheck disable=SC2086
    "${COMPOSE[@]}" $(profiles_for "$PROFILE") up -d postgres-primary keycloak pgbouncer vault minio
    if [ "$PROFILE" != "dev" ]; then
      # Homolog/demo: migrate antes da api (P0.6), pela mesma imagem (P0.5).
      if [ -z "${ZIIPSAN_APP_IMAGE:-}" ]; then
        echo "AVISO: ZIIPSAN_APP_IMAGE nao definida — rode scripts/build-image.sh e exporte a tag;"
        echo "a api/migrate nao sobem sem imagem. Infra (banco/Keycloak/pool/cofre/S3) ja esta no ar."
      else
        "${COMPOSE[@]}" --profile app run --rm migrate
        "${COMPOSE[@]}" --profile app up -d api
      fi
    fi
    echo "PostgreSQL 127.0.0.1:5432 (PgBouncer 127.0.0.1:6432), Keycloak http://localhost:8080 e https://localhost:8443,"
    echo "cofre 127.0.0.1:8200, S3 127.0.0.1:9000. Perfil: $PROFILE."
    elapsed ;;
esac
