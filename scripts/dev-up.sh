#!/bin/bash
# Sobe o ambiente de DESENVOLVIMENTO (PostgreSQL + Keycloak do docker-compose) a partir do fonte no G:.
#
# O Docker Desktop nao enxerga o G: (Google Drive). Os diretorios que o compose monta (init do
# PostgreSQL e realm do Keycloak) vao para uma pasta local NOVA a cada execucao (o Docker guarda em
# cache a primeira visao de um diretorio montado; ver reset-it-db.sh), e o compose recebe os caminhos
# por ZIIPSAN_PG_INIT_DIR e ZIIPSAN_KC_IMPORT_DIR.
#
# Senhas: lidas do .env na raiz (copie de .env.example). Sem elas o compose recusa subir.
#
# Uso (Git Bash):
#   scripts/dev-up.sh            # sobe postgres-primary e keycloak
#   scripts/dev-up.sh --reset    # APAGA os volumes de dados (banco e Keycloak) e sobe do zero
#
# Projeto compose "ziipsan": o mesmo nome da copia antiga em C:\PROJETOS\Ziip SAN\ziipsan, entao os
# mesmos volumes sao reaproveitados. A copia em C: nao e tocada.
source "$(dirname "$0")/lib.sh"

[ -f "$SRC/.env" ] || die "crie $SRC/.env a partir de .env.example (senhas sem valor padrao)"

RUN_DIR="$BUILD_DIR/dev-up/$(date +%s)"
mkdir -p "$RUN_DIR/pg-init" "$RUN_DIR/kc-realms"
cp -rf "$SRC/infra/postgres/init/." "$RUN_DIR/pg-init/"
cp -rf "$SRC/infra/keycloak/realms/." "$RUN_DIR/kc-realms/"
cp -f "$SRC/docker-compose.yml" "$RUN_DIR/docker-compose.yml"
cp -f "$SRC/.env" "$RUN_DIR/.env"
# P0.29: o compose monta ./infra/keycloak/certs (relativo ao compose copiado) para o
# TLS do Keycloak. Gera o certificado local (se ainda nao existir) e copia a pasta.
"$SRC/scripts/local-tls.sh" >/dev/null
mkdir -p "$RUN_DIR/infra/keycloak"
cp -rf "$SRC/infra/keycloak/certs" "$RUN_DIR/infra/keycloak/"

export ZIIPSAN_PG_INIT_DIR="$(cygpath -m "$RUN_DIR/pg-init")"
export ZIIPSAN_KC_IMPORT_DIR="$(cygpath -m "$RUN_DIR/kc-realms")"

compose() {
  (cd "$RUN_DIR" && MSYS_NO_PATHCONV=1 docker compose -p ziipsan -f docker-compose.yml --env-file .env "$@")
}

if [ "${1:-}" = "--reset" ]; then
  echo "APAGANDO os volumes de dados do projeto ziipsan (banco de desenvolvimento e Keycloak)..."
  compose down -v --remove-orphans
fi

compose up -d postgres-primary keycloak
echo "PostgreSQL em 127.0.0.1:5432, Keycloak em http://localhost:8080 (realm ziipsan)."
echo "Crie seu usuario: scripts/kc-dev-user.sh <usuario> VIEWER OPERATOR"
