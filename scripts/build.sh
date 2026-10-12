#!/bin/bash
# Roda o Maven do SIGSAN num container, a partir de um ESPELHO LOCAL do fonte.
#
# Por que espelho: o fonte fica no Google Drive (G:), que o Docker Desktop nao enxerga. Montar
# G:\... no container resulta em /app vazio e "there is no POM in this directory". O script copia o
# fonte para uma pasta em C: (robocopy /MIR, preserva target/ para builds incrementais) e monta
# essa copia. Edite sempre no G:; o espelho e descartavel.
#
# Uso (Git Bash):
#   scripts/build.sh clean test                                   # tudo
#   scripts/build.sh -pl shared/sigsan-core test                  # um modulo
#   scripts/build.sh -pl platform/sigsan-app -am test -Dtest=FreshInstallTest -Dsurefire.failIfNoSpecifiedTests=false
#
# Variaveis:
#   SIGSAN_BUILD_DIR   pasta do espelho (padrao: %LOCALAPPDATA%\sigsan-build). Precisa ser um
#                      caminho que o Docker Desktop enxerga (qualquer pasta em C:\Users). Em
#                      ambientes com escrita restrita (ex.: sessao do Claude Code), o padrao pode
#                      nao ser visivel ao Docker: aponte para uma pasta permitida.
#   SIGSAN_IT_PORT     porta do PostgreSQL de integracao (padrao: 55433), ver scripts/reset-it-db.sh
set -u

SRC="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="${SIGSAN_BUILD_DIR:-$(cygpath -u "$LOCALAPPDATA")/sigsan-build}"
MIRROR="$BUILD_DIR/sigsan"
IT_PORT="${SIGSAN_IT_PORT:-55433}"

mkdir -p "$MIRROR"
# robocopy devolve 0-7 em sucesso; o PowerShell normaliza para 0 para nao confundir o shell.
powershell.exe -NoProfile -Command "robocopy '$(cygpath -w "$SRC")' '$(cygpath -w "$MIRROR")' /MIR /XD target node_modules levantamento .git /XF *.tar /NFL /NDL /NJH /NJS /NP | Out-Null; exit 0" >/dev/null 2>&1

MSYS_NO_PATHCONV=1 docker run --rm \
  -e SIGSAN_IT_JDBC_URL="jdbc:postgresql://host.docker.internal:${IT_PORT}/sigsan" \
  -v "$(cygpath -m "$MIRROR"):/app" -v sigsan-m2:/root/.m2 -w /app \
  maven:3.9-eclipse-temurin-21 mvn -B "$@"
