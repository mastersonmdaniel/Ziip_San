#!/bin/bash
# Roda o Maven do ZIIPSAN num container, a partir de um ESPELHO LOCAL do fonte.
#
# Por que espelho: o fonte fica no Google Drive (G:), que o Docker Desktop nao enxerga. Montar
# G:\... no container resulta em /app vazio e "there is no POM in this directory". O script copia o
# fonte para uma pasta em C: (robocopy /MIR, preserva target/ para builds incrementais) e monta
# essa copia. Edite sempre no G:; o espelho e descartavel.
#
# Uso (Git Bash):
#   scripts/build.sh clean test                                   # tudo
#   scripts/build.sh -pl shared/ziipsan-core test                  # um modulo
#   scripts/build.sh -pl platform/ziipsan-app -am test -Dtest=FreshInstallTest -Dsurefire.failIfNoSpecifiedTests=false
#
# Os testes de banco (core, platform, app) usam o PostgreSQL DESCARTAVEL de integracao: rode
# scripts/reset-it-db.sh antes. Nenhum teste usa o banco de desenvolvimento (porta 5432).
#
# Variaveis:
#   ZIIPSAN_BUILD_DIR   pasta do espelho (padrao: %LOCALAPPDATA%\ziipsan-build). Precisa ser um
#                      caminho que o Docker Desktop enxerga (qualquer pasta em C:\Users). Em
#                      ambientes com escrita restrita (ex.: sessao do Claude Code), o padrao pode
#                      nao ser visivel ao Docker: aponte para uma pasta permitida.
#   ZIIPSAN_IT_PORT     porta do PostgreSQL de integracao (padrao: 55433), ver scripts/reset-it-db.sh
source "$(dirname "$0")/lib.sh"
# P0.3: it-db.env e LOCAL e nao versionado (ver scripts/it-db.env.example). Sem ele,
# as senhas precisam vir exportadas no ambiente; sem elas o container do Maven sobe
# sem as variaveis e os testes de banco falham explicito, em vez de usar um padrao.
if [ -f "$(dirname "$0")/it-db.env" ]; then
  # shellcheck disable=SC1091
  source "$(dirname "$0")/it-db.env"
fi
: "${ZIIPSAN_IT_DB_PASSWORD:?defina ZIIPSAN_IT_DB_PASSWORD (it-db.env local ou ambiente)}"
: "${ZIIPSAN_IT_MIGRATOR_PASSWORD:?defina ZIIPSAN_IT_MIGRATOR_PASSWORD (it-db.env local ou ambiente)}"

acquire_lock
mirror

# Imagem do Maven com cliente PostgreSQL (correcao de ambiente da CI,
# achado da rodada de 09/11/2026): os testes de escala do core
# (P0.21 — ShardCatalogScaleTest) chamam o binario pg_dump de DENTRO do
# contentor do Maven, e a imagem oficial maven:3.9-eclipse-temurin-21
# nao o traz ("Cannot run program pg_dump"). A imagem local abaixo e a
# oficial + postgresql-client, construida uma unica vez (idempotente).
MAVEN_IMAGE="ziipsan/maven-pg:3.9-temurin-21"
if ! docker image inspect "$MAVEN_IMAGE" >/dev/null 2>&1; then
  echo "== a construir a imagem do Maven com pg_dump (uma unica vez)"
  printf 'FROM maven:3.9-eclipse-temurin-21\nRUN apt-get update && apt-get install -y --no-install-recommends postgresql-client && rm -rf /var/lib/apt/lists/*\n' \
    | docker build -t "$MAVEN_IMAGE" - \
    || die "falha ao construir a imagem $MAVEN_IMAGE (pg_dump em falta no contentor da CI)"
fi

MSYS_NO_PATHCONV=1 docker run --rm \
  -e ZIIPSAN_IT_JDBC_URL="jdbc:postgresql://host.docker.internal:${IT_PORT}/ziipsan" \
  -e ZIIPSAN_IT_DB_PASSWORD="$ZIIPSAN_IT_DB_PASSWORD" \
  -e ZIIPSAN_IT_MIGRATOR_PASSWORD="$ZIIPSAN_IT_MIGRATOR_PASSWORD" \
  -v "$(cygpath -m "$MIRROR"):/app" -v ziipsan-m2:/root/.m2 -w /app \
  "$MAVEN_IMAGE" mvn -B "$@"
