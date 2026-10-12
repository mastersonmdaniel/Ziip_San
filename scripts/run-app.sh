#!/bin/bash
# Gera o jar da aplicacao (platform/sigsan-app) e roda num container Java 21, perfil local, porta 8090.
#
# Pre-requisito: scripts/dev-up.sh (PostgreSQL e Keycloak no ar) e .env com as senhas.
# A aplicacao fala com o banco e com o Keycloak pela maquina host (host.docker.internal).
#
# Uso (Git Bash): scripts/run-app.sh
source "$(dirname "$0")/lib.sh"

[ -f "$SRC/.env" ] || die "crie $SRC/.env a partir de .env.example"
set -a
# shellcheck disable=SC1091
source "$SRC/.env"
set +a
: "${SIGSAN_APP_PASSWORD:?defina SIGSAN_APP_PASSWORD no .env}"
: "${SIGSAN_MIGRATOR_PASSWORD:?defina SIGSAN_MIGRATOR_PASSWORD no .env}"
: "${SIGSAN_DEV_MASTER_KEY:?defina SIGSAN_DEV_MASTER_KEY no .env (32 bytes em Base64; so desenvolvimento)}"

# Nomes que a aplicacao le. Vao ao container so pelo nome (-e NOME), sem o valor na linha de
# comando, que apareceria na lista de processos da maquina.
export SIGSAN_DB_APP_PASSWORD="$SIGSAN_APP_PASSWORD"
export SIGSAN_DB_MIGRATOR_PASSWORD="$SIGSAN_MIGRATOR_PASSWORD"

bash "$SRC/scripts/build.sh" -q -pl platform/sigsan-app -am package -DskipTests

JAR="$(ls "$MIRROR"/platform/sigsan-app/target/sigsan-app-*.jar | grep -v original | head -1)"
[ -n "$JAR" ] || die "jar nao encontrado em $MIRROR/platform/sigsan-app/target"

# O emissor do token e o do navegador (localhost:8080); as chaves sao buscadas pela maquina host.
MSYS_NO_PATHCONV=1 docker run --rm --name sigsan-app -p 127.0.0.1:8090:8090 \
  -v "$(cygpath -m "$(dirname "$JAR")"):/app:ro" \
  -e SPRING_PROFILES_ACTIVE=local \
  -e SIGSAN_DB_URL="jdbc:postgresql://host.docker.internal:5432/sigsan" \
  -e SIGSAN_DB_APP_PASSWORD -e SIGSAN_DB_MIGRATOR_PASSWORD \
  -e SIGSAN_OIDC_JWK_SET_URI="http://host.docker.internal:8080/realms/sigsan/protocol/openid-connect/certs" \
  -e SIGSAN_CRYPTO_PROVIDER=dev-env -e SIGSAN_DEV_MASTER_KEY \
  -e SIGSAN_DEV_TENANT_ID -e SIGSAN_DEV_TENANT_SCHEMA \
  eclipse-temurin:21-jre java -jar "/app/$(basename "$JAR")"
