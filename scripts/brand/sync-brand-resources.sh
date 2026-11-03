#!/bin/bash
# P0.34 — mantem as copias da marca empacotadas no backend iguais ao pacote.
# Uso: bash scripts/brand/sync-brand-resources.sh  (escreve)
#       bash scripts/brand/check-brand-sync.sh     (so confere; falha se divergir)
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SRC="$ROOT/branding/ziipsan"
DST="$ROOT/platform/ziipsan-app/src/main/resources/branding/ziipsan"
if [ "${1:-}" = "--check" ]; then
  if ! diff -r -q "$SRC/brand.json" "$DST/brand.json" >/dev/null 2>&1 || ! diff -r -q "$SRC/texts" "$DST/texts" >/dev/null 2>&1; then
    echo "ERRO: copia da marca no backend divergente do pacote branding/ziipsan. Rode sync-brand-resources.sh."
    exit 1
  fi
  echo "OK: copia da marca no backend igual ao pacote"
else
  mkdir -p "$DST"
  cp "$SRC/brand.json" "$DST/brand.json"
  cp -r "$SRC/texts" "$DST/"
  echo "OK: marca sincronizada para o backend"
fi
