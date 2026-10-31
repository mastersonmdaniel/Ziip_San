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
IT_PORT="${SIGSAN_IT_PORT:-55433}"
IT_DATA="${SIGSAN_IT_DATA:-/tmp/sigsan-it-pg}"
# Major do Postgres da instancia dedicada (17 desde o turno 3, apos instalacao via PGDG;
# antes era 16, por indisponibilidade no apt padrao). Binarios em /usr/lib/postgresql/$IT_PG/.
IT_PG="${SIGSAN_IT_PG:-17}"
PG_BIN="/usr/lib/postgresql/$IT_PG/bin"
IT_LOG="${SIGSAN_IT_LOG:-/tmp/sigsan-it-pg.log}"
LOCK_DIR="/tmp/sigsan-ci-linux.lock"

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
echo "$SUPER_PW" > /tmp/sigsan-it-super.pw
chown postgres:postgres /tmp/sigsan-it-super.pw
chmod 600 /tmp/sigsan-it-super.pw
trap 'rm -f /tmp/sigsan-it-super.pw; cleanup' EXIT

echo "== (a) instancia dedicada PostgreSQL $IT_PG em 127.0.0.1:$IT_PORT (virgem)"
su postgres -c "$PG_BIN/pg_ctl -D '$IT_DATA' -m fast stop" >/dev/null 2>&1 || true
rm -rf "$IT_DATA" "$IT_LOG"
mkdir -p "$IT_DATA"
chown postgres:postgres "$IT_DATA"
chmod 700 "$IT_DATA"
su postgres -c "$PG_BIN/initdb -D '$IT_DATA' -U postgres --pwfile=/tmp/sigsan-it-super.pw --auth=scram-sha-256" >>"$IT_LOG" 2>&1 \
  || die "initdb falhou (ver $IT_LOG)"
# PostGIS precisa estar disponivel no shared_preload? Nao: CREATE EXTENSION basta, com o pacote instalado.
# O log do initdb acima e criado como root; o pg_ctl (como postgres) precisa do proprio arquivo.
rm -f "$IT_LOG"; touch "$IT_LOG"; chown postgres:postgres "$IT_LOG"; chmod 600 "$IT_LOG"
su postgres -c "$PG_BIN/pg_ctl -D '$IT_DATA' -l '$IT_LOG' -o '-p $IT_PORT -h 127.0.0.1 -k /tmp' start" \
  || die "falha ao subir a instancia dedicada (ver $IT_LOG)"
export PGPASSWORD="$SUPER_PW"
for _ in $(seq 1 60); do
  pg_isready -h 127.0.0.1 -p "$IT_PORT" -U postgres >/dev/null 2>&1 && break
  sleep 2
done
pg_isready -h 127.0.0.1 -p "$IT_PORT" -U postgres >/dev/null 2>&1 || die "instancia dedicada nao ficou pronta"
psql -h 127.0.0.1 -p "$IT_PORT" -U postgres -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE sigsan;" >/dev/null
# security.sql cria roles, extensoes (incl. postgis) e current_tenant_id(); pular o bloco Keycloak.
psql -h 127.0.0.1 -p "$IT_PORT" -U postgres -d sigsan -v ON_ERROR_STOP=1 \
  -v app_pw="$APP_PW" -v migrator_pw="$MIG_PW" \
  -f "$SRC/infra/postgres/init/sql/security.sql" >/dev/null
OWNER_OK="$(psql -h 127.0.0.1 -p "$IT_PORT" -U postgres -d sigsan -tAc \
  "select 1 from pg_proc where proname = 'current_tenant_id' and pg_get_userbyid(proowner) = 'sigsan_owner'")"
[ "$OWNER_OK" = "1" ] || die "verificacao do init falhou: current_tenant_id nao pertence a sigsan_owner"
echo "banco virgem pronto em 127.0.0.1:$IT_PORT"

export SIGSAN_IT_JDBC_URL="jdbc:postgresql://127.0.0.1:${IT_PORT}/sigsan"
export SIGSAN_IT_DB_USER="sigsan_app"
export SIGSAN_IT_DB_PASSWORD="$APP_PW"
export SIGSAN_IT_MIGRATOR_USER="sigsan_migrator"
export SIGSAN_IT_MIGRATOR_PASSWORD="$MIG_PW"
unset PGPASSWORD

echo "== (b) backend (mvn clean verify)"
export JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true${JAVA_TOOL_OPTIONS:+ $JAVA_TOOL_OPTIONS}"
# Timeouts do Wagon: sem eles, um repositorio lento/ausente (ex.: jboss-public, que
# responde 404 para jackson-bom 2.18.5) deixa o Maven parado em leitura SSL indefinidamente.
(cd "$SRC" && mvn -B -s "$SRC/scripts/ci-linux-settings.xml" -Dmaven.wagon.http.connectionTimeout=30000 -Dmaven.wagon.http.readTimeout=60000 clean verify)

echo "== (c) frontend"
FE="$(mktemp -d /tmp/sigsan-ci-frontend.XXXXXX)"
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
rm -rf "$FE"

echo "== (d) portas publicadas do docker-compose.yml (todas devem ser 127.0.0.1)"
python3 - "$SRC/docker-compose.yml" <<'PY'
import re, sys
bad = []
for n, raw in enumerate(open(sys.argv[1]), 1):
    line = raw.rstrip("\n")
    if line.lstrip().startswith("#"):
        continue
    m = re.search(r'-\s*"?(\d+\.\d+\.\d+\.\d+|\$\{[^}]+\}):\d+', line)
    if m and not line.strip().startswith("- \"127.0.0.1:") and not line.strip().startswith("- '127.0.0.1:"):
        bad.append((n, line.strip()))
    # forma curta sem host: "- 5432:5432" tambem publica em todas as interfaces
    if re.search(r'-\s*"?\d+:\d+', line) and "127.0.0.1" not in line:
        bad.append((n, line.strip()))
if bad:
    print("PORTAS EM TODAS AS INTERFACES (falha, esperado ate a P0.7):")
    for n, l in sorted(set(bad)):
        print(f"  linha {n}: {l}")
    sys.exit(1)
print("todas as portas publicadas estao em 127.0.0.1")
PY

echo "== OK"
