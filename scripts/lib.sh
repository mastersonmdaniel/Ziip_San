#!/bin/bash
# Funcoes comuns dos scripts do SIGSAN. Uso: source "$(dirname "$0")/lib.sh"
#
# - modo estrito (set -euo pipefail);
# - SRC, BUILD_DIR, MIRROR, IT_PORT com os mesmos padroes em todos os scripts;
# - trava (lock) que impede dois builds ou resets simultaneos: eles compartilham o espelho, o target/,
#   o volume sigsan-m2 e o banco de integracao, e um derrubava o outro;
# - espelho do fonte (robocopy) que FALHA de verdade: antes, o exit code era forcado a 0 e, com o
#   Drive offline, o Maven compilava um espelho velho sem aviso.
set -euo pipefail

SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="${SIGSAN_BUILD_DIR:-$(cygpath -u "$LOCALAPPDATA")/sigsan-build}"
MIRROR="$BUILD_DIR/sigsan"
IT_PORT="${SIGSAN_IT_PORT:-55433}"
# Nome do container do banco de integracao. Pipelines paralelos (outra copia de trabalho) usam
# SIGSAN_IT_PORT, SIGSAN_IT_CONTAINER e SIGSAN_BUILD_DIR proprios e nao se enxergam.
IT_CONTAINER="${SIGSAN_IT_CONTAINER:-sigsan-it-pg}"
LOCK_DIR="$BUILD_DIR/.lock"

die() { echo "ERRO: $*" >&2; exit 1; }

# Trava exclusiva. mkdir e atomico; o PID gravado permite detectar trava de processo morto.
# Um script chamado por outro que ja tem a trava (ex.: ci.sh) herda SIGSAN_LOCK_HELD=1.
acquire_lock() {
  if [ "${SIGSAN_LOCK_HELD:-0}" = "1" ]; then
    return 0
  fi
  mkdir -p "$BUILD_DIR"
  local waited=0
  until mkdir "$LOCK_DIR" 2>/dev/null; do
    local holder
    holder="$(cat "$LOCK_DIR/pid" 2>/dev/null || true)"
    if [ -n "$holder" ] && ! kill -0 "$holder" 2>/dev/null; then
      echo "trava velha do processo $holder (nao existe mais): removendo"
      rm -rf "$LOCK_DIR"
      continue
    fi
    if [ "$waited" -eq 0 ]; then
      echo "outro build/reset em andamento (pid ${holder:-?}); aguardando..."
    fi
    waited=$((waited + 5))
    [ "$waited" -le "${SIGSAN_LOCK_TIMEOUT:-3600}" ] || die "tempo esgotado esperando a trava $LOCK_DIR"
    sleep 5
  done
  echo $$ > "$LOCK_DIR/pid"
  export SIGSAN_LOCK_HELD=1
  trap 'rm -rf "$LOCK_DIR"' EXIT
}

# Copia o fonte (G:) para o espelho local que o Docker enxerga. robocopy: 0-7 = sucesso, >= 8 = falha.
mirror() {
  [ -f "$SRC/pom.xml" ] || die "fonte inacessivel em $SRC (Google Drive offline?)"
  mkdir -p "$MIRROR"
  local rc=0
  powershell.exe -NoProfile -Command "robocopy '$(cygpath -w "$SRC")' '$(cygpath -w "$MIRROR")' /MIR /XD target node_modules levantamento .git /XF *.tar .git /NFL /NDL /NJH /NJS /NP | Out-Null; exit \$LASTEXITCODE" || rc=$?
  [ "$rc" -lt 8 ] || die "robocopy falhou (codigo $rc) ao espelhar $SRC para $MIRROR"
  [ -f "$MIRROR/pom.xml" ] || die "espelho sem pom.xml em $MIRROR"
}
