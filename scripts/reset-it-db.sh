#!/bin/bash
# Recria do zero o PostgreSQL DESCARTAVEL dos testes de integracao (core, platform e sigsan-app).
#
# Mesma imagem e mesmo init de seguranca do docker-compose (infra/postgres/init: superusuario de
# bootstrap "postgres", dono sigsan_owner, roles sigsan_app/sigsan_migrator sem SUPERUSER/BYPASSRLS),
# numa porta propria. Equivale a "docker compose down -v && up" para uma instalacao nova, sem tocar
# no banco de desenvolvimento.
#
# Rode antes de cada build com testes: a migration de core roda sobre `public` e o Flyway recusa
# uma migration ja aplicada e depois alterada (checksum).
#
# Variaveis: SIGSAN_IT_PORT (padrao 55433), SIGSAN_IT_CONTAINER, SIGSAN_BUILD_DIR (ver lib.sh).
# Senhas: scripts/it-db.env (so para este banco descartavel).
source "$(dirname "$0")/lib.sh"
source "$(dirname "$0")/it-db.env"

acquire_lock

# O init precisa estar visivel ao Docker, entao vai para uma pasta local (ver scripts/build.sh).
# A pasta e NOVA e unica a cada execucao, com os arquivos ja dentro antes do primeiro mount: o
# Docker Desktop guarda em cache a primeira visao de um diretorio compartilhado, e uma pasta que
# foi montada vazia continua parecendo vazia, o que faz o entrypoint do Postgres ignorar o init
# ("ignoring /docker-entrypoint-initdb.d/*") e subir sem as roles de seguranca.
INIT_ROOT="$BUILD_DIR/it-init"
rm -rf "$INIT_ROOT"
INIT_DIR="$INIT_ROOT/$(date +%s)"
mkdir -p "$INIT_DIR"
cp -rf "$SRC/infra/postgres/init/." "$INIT_DIR/"

docker rm -f "$IT_CONTAINER" >/dev/null 2>&1 || true
MSYS_NO_PATHCONV=1 docker run -d --name "$IT_CONTAINER" -p "${IT_PORT}:5432" \
  -e POSTGRES_DB=sigsan -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD="$SIGSAN_IT_SUPERUSER_PASSWORD" \
  -e SIGSAN_APP_PASSWORD="$SIGSAN_IT_DB_PASSWORD" \
  -e SIGSAN_MIGRATOR_PASSWORD="$SIGSAN_IT_MIGRATOR_PASSWORD" \
  -e POSTGRES_INITDB_ARGS="--auth-host=scram-sha-256" \
  -v "$(cygpath -m "$INIT_DIR"):/docker-entrypoint-initdb.d:ro" \
  postgis/postgis:15-3.4 >/dev/null || die "falha ao iniciar o container (porta $IT_PORT ocupada?)"

# O entrypoint reinicia o servidor depois do init. Pronto = a verificacao do init passou (a role
# sigsan_owner e dona de current_tenant_id) e o servidor definitivo aceita conexoes. Se o init
# abortar (verificacao de seguranca falhou), o container para: falha imediata com o log.
for _ in $(seq 1 60); do
  if [ "$(docker inspect -f '{{.State.Running}}' "$IT_CONTAINER" 2>/dev/null)" != "true" ]; then
    docker logs "$IT_CONTAINER" 2>&1 | tail -30
    die "o container do banco de integracao parou (init abortado?)"
  fi
  if docker exec "$IT_CONTAINER" psql -U postgres -d sigsan -tAc \
       "select 1 from pg_proc where proname = 'current_tenant_id' and pg_get_userbyid(proowner) = 'sigsan_owner'" \
       2>/dev/null | grep -q 1; then
    sleep 2
    if docker exec "$IT_CONTAINER" pg_isready -U postgres -d sigsan >/dev/null 2>&1; then
      echo "banco virgem pronto na porta ${IT_PORT}"
      exit 0
    fi
  fi
  sleep 2
done
docker logs "$IT_CONTAINER" 2>&1 | tail -30
die "falha ao subir o banco de integracao"
