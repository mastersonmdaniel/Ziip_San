#!/bin/bash
# Gera o jar da aplicacao (platform/ziipsan-app) e roda num container Java 21, perfil local, porta 8090.
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
: "${ZIIPSAN_APP_PASSWORD:?defina ZIIPSAN_APP_PASSWORD no .env}"
: "${ZIIPSAN_MIGRATOR_PASSWORD:?defina ZIIPSAN_MIGRATOR_PASSWORD no .env}"
: "${ZIIPSAN_DEV_MASTER_KEY:?defina ZIIPSAN_DEV_MASTER_KEY no .env (32 bytes em Base64; so desenvolvimento)}"

# Nomes que a aplicacao le. Vao ao container so pelo nome (-e NOME), sem o valor na linha de
# comando, que apareceria na lista de processos da maquina.
export ZIIPSAN_DB_APP_PASSWORD="$ZIIPSAN_APP_PASSWORD"
export ZIIPSAN_DB_MIGRATOR_PASSWORD="$ZIIPSAN_MIGRATOR_PASSWORD"

bash "$SRC/scripts/build.sh" -q -pl platform/ziipsan-app -am package -DskipTests

JAR="$(ls "$MIRROR"/platform/ziipsan-app/target/ziipsan-app-*.jar | grep -v original | head -1)"
[ -n "$JAR" ] || die "jar nao encontrado em $MIRROR/platform/ziipsan-app/target"

# O emissor do token e o do navegador (localhost:8080); as chaves sao buscadas pela maquina host.
MSYS_NO_PATHCONV=1 docker run --rm --name ziipsan-app -p 127.0.0.1:8090:8090 \
  -v "$(cygpath -m "$(dirname "$JAR")"):/app:ro" \
  -e SPRING_PROFILES_ACTIVE=local \
  -e ZIIPSAN_DB_URL="jdbc:postgresql://host.docker.internal:5432/ziipsan" \
  -e ZIIPSAN_DB_APP_PASSWORD -e ZIIPSAN_DB_MIGRATOR_PASSWORD \
  -e ZIIPSAN_OIDC_JWK_SET_URI="http://host.docker.internal:8080/realms/ziipsan/protocol/openid-connect/certs" \
  -e ZIIPSAN_CRYPTO_PROVIDER=dev-env -e ZIIPSAN_DEV_MASTER_KEY \
  -e ZIIPSAN_DEV_TENANT_ID -e ZIIPSAN_DEV_TENANT_SCHEMA \
  eclipse-temurin:21-jre java -jar "/app/$(basename "$JAR")"
