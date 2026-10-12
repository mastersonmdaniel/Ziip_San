#!/bin/bash
# Recria do zero o PostgreSQL DESCARTAVEL dos testes de integracao (core, platform e sigsan-app).
#
# Mesma imagem e mesmo init de seguranca do docker-compose (roles sigsan_app/sigsan_migrator sem
# SUPERUSER/BYPASSRLS), numa porta propria. Equivale a "docker compose down -v && up" para uma
# instalacao nova, sem tocar no banco de desenvolvimento.
#
# Rode antes de cada build com testes: a migration de core roda sobre `public` e o Flyway recusa
# uma migration ja aplicada e depois alterada (checksum).
#
# Variaveis: SIGSAN_IT_PORT (padrao 55433), SIGSAN_BUILD_DIR (mesmo padrao do build.sh).
source "$(dirname "$0")/lib.sh"

acquire_lock

# O init precisa estar visivel ao Docker, entao vai para uma pasta local (ver scripts/build.sh).
# A pasta e NOVA e unica a cada execucao, com o arquivo ja dentro antes do primeiro mount: o Docker
# Desktop guarda em cache a primeira visao de um diretorio compartilhado, e uma pasta que foi
# montada vazia continua parecendo vazia, o que faz o entrypoint do Postgres ignorar o init
# ("ignoring /docker-entrypoint-initdb.d/*") e subir sem as roles de seguranca.
INIT_ROOT="$BUILD_DIR/it-init"
rm -rf "$INIT_ROOT"
INIT_DIR="$INIT_ROOT/$(date +%s)"
mkdir -p "$INIT_DIR"
cp -f "$SRC/infra/postgres/init/"*.sql "$INIT_DIR/"

docker rm -f sigsan-it-pg >/dev/null 2>&1 || true
MSYS_NO_PATHCONV=1 docker run -d --name sigsan-it-pg -p "${IT_PORT}:5432" \
  -e POSTGRES_DB=sigsan -e POSTGRES_USER=sigsan_app -e POSTGRES_PASSWORD=sigsan_dev_password \
  -e POSTGRES_INITDB_ARGS="--auth-host=scram-sha-256" \
  -v "$(cygpath -m "$INIT_DIR"):/docker-entrypoint-initdb.d:ro" \
  postgis/postgis:15-3.4 >/dev/null || die "falha ao iniciar o container (porta $IT_PORT ocupada?)"

# O entrypoint reinicia o servidor depois do init: espera a role de migracao existir e o servidor
# aceitar conexoes, nessa ordem.
for _ in $(seq 1 60); do
  if docker exec sigsan-it-pg psql -U sigsan_app -d sigsan -tAc "select 1 from pg_roles where rolname='sigsan_migrator'" 2>/dev/null | grep -q 1; then
    sleep 2
    if docker exec sigsan-it-pg pg_isready -U sigsan_app -d sigsan >/dev/null 2>&1; then
      echo "banco virgem pronto na porta ${IT_PORT}"
      exit 0
    fi
  fi
  sleep 2
done
docker logs sigsan-it-pg 2>&1 | tail -20
die "falha ao subir o banco de integracao"
