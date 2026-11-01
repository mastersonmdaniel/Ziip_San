#!/bin/bash
# Gera o jar da aplicacao (platform/ziipsan-app) e roda num container Java 21, perfil local, porta 8090.
#
# Pre-requisito: scripts/dev-up.sh (PostgreSQL e Keycloak no ar) e .env com as senhas.
# A aplicacao fala com o banco e com o Keycloak pela maquina host (host.docker.internal).
#
# P0.6: fluxo em 2 PASSOS, sempre nesta ordem.
#   1. migrate (perfis local,migrate): migra core + tenants e provisiona o tenant de
#      desenvolvimento (se ZIIPSAN_DEV_TENANT_ID estiver definido); o job sai sozinho.
#   2. api (perfil local): sobe SEM a senha do migrador — ela nem e passada ao container
#      da api. Quem tentar subir a api sem o passo 1 pega um schema desatualizado (ou
#      vazio): a api nao migra mais sozinha.
#
# Uso (Git Bash): scripts/run-app.sh
source "$(dirname "$0")/lib.sh"

[ -f "$SRC/.env" ] || die "crie $SRC/.env a partir de .env.example"
set -a
# shellcheck disable=SC1091
source "$SRC/.env"
set +a
: "${ZIIPSAN_APP_PASSWORD:?defina ZIIPSAN_APP_PASSWORD no .env}"
: "${ZIIPSAN_MIGRATOR_PASSWORD:?defina ZIIPSAN_MIGRATOR_PASSWORD no .env (so o passo migrate usa)}"
: "${ZIIPSAN_DEV_MASTER_KEY:?defina ZIIPSAN_DEV_MASTER_KEY no .env (32 bytes em Base64; so desenvolvimento)}"

# Nomes que a aplicacao le. Vao ao container so pelo nome (-e NOME), sem o valor na linha de
# comando, que apareceria na lista de processos da maquina.
export ZIIPSAN_DB_APP_PASSWORD="$ZIIPSAN_APP_PASSWORD"
export ZIIPSAN_DB_MIGRATOR_PASSWORD="$ZIIPSAN_MIGRATOR_PASSWORD"

bash "$SRC/scripts/build.sh" -q -pl platform/ziipsan-app -am package -DskipTests

JAR="$(ls "$MIRROR"/platform/ziipsan-app/target/ziipsan-app-*.jar | grep -v original | head -1)"
[ -n "$JAR" ] || die "jar nao encontrado em $MIRROR/platform/ziipsan-app/target"

# Passo 1 — migrate: perfis local,migrate + migrate-on-startup=true. O job migra e sai
# (MigrateExitRunner); se ele falhar, o script para aqui e a api nem sobe.
echo "== passo 1/2: migrate (perfis local,migrate)"
MSYS_NO_PATHCONV=1 docker run --rm --name ziipsan-migrate -p 127.0.0.1:8091:8091 \
  -v "$(cygpath -m "$(dirname "$JAR")"):/app:ro" \
  -e SPRING_PROFILES_ACTIVE=local,migrate \
  -e ZIIPSAN_DB_URL="jdbc:postgresql://host.docker.internal:5432/ziipsan" \
  -e ZIIPSAN_DB_APP_PASSWORD -e ZIIPSAN_DB_MIGRATOR_PASSWORD \
  -e ZIIPSAN_DB_MIGRATE_ON_STARTUP=true \
  -e ZIIPSAN_OIDC_JWK_SET_URI="http://host.docker.internal:8080/realms/ziipsan/protocol/openid-connect/certs" \
  -e ZIIPSAN_CRYPTO_PROVIDER=dev-env -e ZIIPSAN_DEV_MASTER_KEY \
  -e ZIIPSAN_DEV_TENANT_ID -e ZIIPSAN_DEV_TENANT_SCHEMA \
  -e ZIIPSAN_PORT=8091 \
  eclipse-temurin:21-jre java -jar "/app/$(basename "$JAR")"

# Passo 2 — api: perfil local, SEM a senha do migrador (P0.6: a api nem tem o bean).
# O emissor do token e o do navegador (localhost:8080); as chaves sao buscadas pela maquina host.
echo "== passo 2/2: api (perfil local, porta 8090)"
MSYS_NO_PATHCONV=1 docker run --rm --name ziipsan-app -p 127.0.0.1:8090:8090 \
  -v "$(cygpath -m "$(dirname "$JAR")"):/app:ro" \
  -e SPRING_PROFILES_ACTIVE=local \
  -e ZIIPSAN_DB_URL="jdbc:postgresql://host.docker.internal:5432/ziipsan" \
  -e ZIIPSAN_DB_APP_PASSWORD \
  -e ZIIPSAN_OIDC_JWK_SET_URI="http://host.docker.internal:8080/realms/ziipsan/protocol/openid-connect/certs" \
  -e ZIIPSAN_CRYPTO_PROVIDER=dev-env -e ZIIPSAN_DEV_MASTER_KEY \
  eclipse-temurin:21-jre java -jar "/app/$(basename "$JAR")"
