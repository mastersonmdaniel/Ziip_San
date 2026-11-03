#!/bin/bash
# Verificacao completa Linux (equivalente ao scripts/ci.sh do Windows, sem robocopy/PowerShell/
# Docker Desktop/host.docker.internal).
#
# O que faz, nesta ordem:
#   (a) sobe uma instancia DEDICADA e VIRGEM do PostgreSQL 17 (PostGIS 3.7.0 via PGDG;
#       decisao do dono, 31/10/2026: projeto atualizado para PostGIS 3.7.0, em vez
#       da 3.5 do plano original, porque o PGDG entrega 3.7.0; imagem Docker
#       postgis/postgis:17-3.7 ainda nao publicada — referencia de imagem pendente,
#       verificacao local via PGDG/host continua valendo)
#       em 127.0.0.1:55433, sem tocar nos clusters principais (5432 PG16, 5433 PG17);
#   (b) roda mvn clean verify no host, com as 5 variaveis de integracao exportadas
#       (host.docker.internal NAO resolve no host Linux — o JDBC aponta para 127.0.0.1);
#   (c) roda o frontend copiado (sem node_modules/dist/coverage): typecheck, lint (se existir),
#       vitest run, build; npm audit e apenas relatado, nao bloqueante;
#   (d) checa as portas publicadas do docker-compose.yml: falha se alguma nao estiver em
#       127.0.0.1 (esperado VERMELHO ate a P0.7).
#
# Senhas: todas efemeras, geradas a cada execucao (openssl rand). Nenhuma credencial do Drive
# e reaproveitada e nenhuma senha e versionada. A instancia e descartavel e local.
#
# JAVA_TOOL_OPTIONS: -Djava.net.preferIPv4Stack=true e OBRIGATORIO nesta VM.
# Sem ele, a JVM reseta o socket no primeiro byte ao falar com o Postgres em 127.0.0.1
# (reproduzido 3/3: Connection reset, com socket Java puro e com JDBC, enquanto psql e
# socket Python ao mesmo servidor respondem normalmente); com a flag, 3/3 OK.
# JAVA_TOOL_OPTIONS e herdado por TODA JVM lancada, incluindo os forks do Surefire —
# MAVEN_OPTS sozinho NAO basta, pois nao alcanca os processos de teste forkados.
# Isto e um desvio de ambiente, nao do codigo: documentado em STATUS.md (turno 3).
#
# Uso: scripts/ci-linux.sh
# Sai com erro na primeira falha. A instancia dedicada e parada no fim (trap), mesmo com falha.
set -euo pipefail

SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IT_PORT="${ZIIPSAN_IT_PORT:-55433}"
IT_DATA="${ZIIPSAN_IT_DATA:-/tmp/ziipsan-it-pg}"
# Major do Postgres da instancia dedicada (17 desde o turno 3, apos instalacao via PGDG;
# antes era 16, por indisponibilidade no apt padrao). Binarios em /usr/lib/postgresql/$IT_PG/.
IT_PG="${ZIIPSAN_IT_PG:-17}"
PG_BIN="/usr/lib/postgresql/$IT_PG/bin"
IT_LOG="${ZIIPSAN_IT_LOG:-/tmp/ziipsan-it-pg.log}"
LOCK_DIR="/tmp/ziipsan-ci-linux.lock"

die() { echo "ERRO: $*" >&2; exit 1; }

# --- trava exclusiva (processo morto libera) ---
if ! mkdir "$LOCK_DIR" 2>/dev/null; then
  holder="$(cat "$LOCK_DIR/pid" 2>/dev/null || true)"
  if [ -n "$holder" ] && ! kill -0 "$holder" 2>/dev/null; then
    echo "trava velha do processo $holder (nao existe mais): removendo"
    rm -rf "$LOCK_DIR"; mkdir "$LOCK_DIR" || die "nao consegui adquirir a trava"
  else
    die "outro ci-linux em andamento (pid ${holder:-?})"
  fi
fi
echo $$ > "$LOCK_DIR/pid"

cleanup() {
  echo "== parada da instancia dedicada (porta $IT_PORT)"
  su postgres -c "$PG_BIN/pg_ctl -D '$IT_DATA' -m fast stop" >/dev/null 2>&1 || true
  # O FE do frontend (mktemp em /tmp, tmpfs de 512M) tambem vai embora aqui: sem
  # isso, uma falha no typecheck deixava 290M para tras e o /tmp seguinte enchia,
  # truncando o node_modules da proxima execucao (turno 4, P0.33).
  rm -rf "${FE:-}"
  rm -rf "$LOCK_DIR"
}
trap cleanup EXIT

command -v java >/dev/null || die "java nao encontrado"
command -v mvn >/dev/null || die "mvn nao encontrado"
command -v psql >/dev/null || die "psql nao encontrado"
command -v node >/dev/null || die "node nao encontrado"
command -v npm >/dev/null || die "npm nao encontrado"
[ -f "$SRC/infra/postgres/init/sql/security.sql" ] || die "security.sql nao encontrado"

SUPER_PW="$(openssl rand -hex 16)"
APP_PW="$(openssl rand -hex 16)"
MIG_PW="$(openssl rand -hex 16)"
echo "$SUPER_PW" > /tmp/ziipsan-it-super.pw
chown postgres:postgres /tmp/ziipsan-it-super.pw
chmod 600 /tmp/ziipsan-it-super.pw
trap 'rm -f /tmp/ziipsan-it-super.pw; cleanup' EXIT

echo "== (a) instancia dedicada PostgreSQL $IT_PG em 127.0.0.1:$IT_PORT (virgem)"
su postgres -c "$PG_BIN/pg_ctl -D '$IT_DATA' -m fast stop" >/dev/null 2>&1 || true
rm -rf "$IT_DATA" "$IT_LOG"
mkdir -p "$IT_DATA"
chown postgres:postgres "$IT_DATA"
chmod 700 "$IT_DATA"
su postgres -c "$PG_BIN/initdb -D '$IT_DATA' -U postgres --pwfile=/tmp/ziipsan-it-super.pw --auth=scram-sha-256" >>"$IT_LOG" 2>&1 \
  || die "initdb falhou (ver $IT_LOG)"
# PostGIS precisa estar disponivel no shared_preload? Nao: CREATE EXTENSION basta, com o pacote instalado.
# O log do initdb acima e criado como root; o pg_ctl (como postgres) precisa do proprio arquivo.
rm -f "$IT_LOG"; touch "$IT_LOG"; chown postgres:postgres "$IT_LOG"; chmod 600 "$IT_LOG"
# P0.8: preload do pg_stat_statements na instancia dedicada (sem ele a extensao criada
# pelo security.sql nao coleta; ver security.sql).
su postgres -c "$PG_BIN/pg_ctl -D '$IT_DATA' -l '$IT_LOG' -o '-p $IT_PORT -h 127.0.0.1 -k /tmp -c shared_preload_libraries=pg_stat_statements' start" \
  || die "falha ao subir a instancia dedicada (ver $IT_LOG)"
export PGPASSWORD="$SUPER_PW"
for _ in $(seq 1 60); do
  pg_isready -h 127.0.0.1 -p "$IT_PORT" -U postgres >/dev/null 2>&1 && break
  sleep 2
done
pg_isready -h 127.0.0.1 -p "$IT_PORT" -U postgres >/dev/null 2>&1 || die "instancia dedicada nao ficou pronta"
psql -h 127.0.0.1 -p "$IT_PORT" -U postgres -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE ziipsan;" >/dev/null
# security.sql cria roles, extensoes (incl. postgis) e current_tenant_id(); pular o bloco Keycloak.
psql -h 127.0.0.1 -p "$IT_PORT" -U postgres -d ziipsan -v ON_ERROR_STOP=1 \
  -v app_pw="$APP_PW" -v migrator_pw="$MIG_PW" \
  -f "$SRC/infra/postgres/init/sql/security.sql" >/dev/null
OWNER_OK="$(psql -h 127.0.0.1 -p "$IT_PORT" -U postgres -d ziipsan -tAc \
  "select 1 from pg_proc where proname = 'current_tenant_id' and pg_get_userbyid(proowner) = 'ziipsan_owner'")"
[ "$OWNER_OK" = "1" ] || die "verificacao do init falhou: current_tenant_id nao pertence a ziipsan_owner"
echo "banco virgem pronto em 127.0.0.1:$IT_PORT"

export ZIIPSAN_IT_JDBC_URL="jdbc:postgresql://127.0.0.1:${IT_PORT}/ziipsan"
export ZIIPSAN_IT_DB_USER="ziipsan_app"
export ZIIPSAN_IT_DB_PASSWORD="$APP_PW"
export ZIIPSAN_IT_MIGRATOR_USER="ziipsan_migrator"
export ZIIPSAN_IT_MIGRATOR_PASSWORD="$MIG_PW"
unset PGPASSWORD

echo "== (b) backend (mvn clean verify)"
export JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true${JAVA_TOOL_OPTIONS:+ $JAVA_TOOL_OPTIONS}"
# Timeouts do Wagon: sem eles, um repositorio lento/ausente (ex.: jboss-public, que
# responde 404 para jackson-bom 2.18.5) deixa o Maven parado em leitura SSL indefinidamente.
(cd "$SRC" && mvn -B -s "$SRC/scripts/ci-linux-settings.xml" -Dmaven.wagon.http.connectionTimeout=30000 -Dmaven.wagon.http.readTimeout=60000 clean verify)

echo "== (c) frontend"
FE="$(mktemp -d /tmp/ziipsan-ci-frontend.XXXXXX)"
cp -a "$SRC/frontend/." "$FE/"
rm -rf "$FE/node_modules" "$FE/dist" "$FE/coverage"
(
  cd "$FE"
  npm ci --no-audit --no-fund --loglevel=error
  npm run -s typecheck
  if npm run -s 2>/dev/null | grep -q "^  lint"; then npm run -s lint; fi
  npx vitest run
  npm run -s build
  npm audit --omit=dev --audit-level=high || echo "AVISO: npm audit encontrou vulnerabilidades (ver acima)"
)
echo "== (c2) marca, temas e icones (P0.34/P0.35/P0.36/P0.46)"
node "$SRC/scripts/brand/check-brand.mjs"
bash "$SRC/scripts/brand/sync-brand-resources.sh" --check
bash "$SRC/scripts/brand/check-brand-literals.sh" "$FE"
bash "$SRC/scripts/icons/check-svg.sh" "$FE"
node "$SRC/scripts/theme/check-theme-script-hash.mjs"
node "$SRC/scripts/brand/test-brand-swap.mjs"
# Arquivos gerados (palettes, marca do frontend) devem estar atualizados no commit.
node "$SRC/scripts/theme/generate-palettes.mjs"
node "$SRC/scripts/brand/generate-frontend-brand.mjs"
if git -C "$SRC" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  if [ -n "$(git -C "$SRC" status --porcelain -- frontend/src/theme/palettes.css frontend/src/theme/palettes.generated.ts frontend/src/theme/palette-ids.json frontend/src/brand/brand.generated.ts frontend/brand.json frontend/public/brand)" ]; then
    git -C "$SRC" status --porcelain -- frontend/src/theme frontend/src/brand frontend/brand.json frontend/public/brand >&2
    die "arquivos gerados de marca/temas desatualizados: regenere e commite (scripts/theme/generate-palettes.mjs, scripts/brand/generate-frontend-brand.mjs)"
  fi
fi
rm -rf "$FE"

echo "== (d) portas publicadas do docker-compose.yml (todas devem ser 127.0.0.1)"
# P0.7: verificacao extraida para scripts/check-compose-ports.py (com teste negativo
# em scripts/test-check-compose-ports.py); o ci-linux apenas a invoca.
python3 "$SRC/scripts/check-compose-ports.py" "$SRC/docker-compose.yml"
echo "== OK"
