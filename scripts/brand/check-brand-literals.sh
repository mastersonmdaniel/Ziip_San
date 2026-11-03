#!/bin/bash
# P0.34 — falha se um literal de marca aparecer fora do pacote de marca.
# Escopo: frontend/src, frontend/index.html e frontend/public/config.json.
# Permitido (fonte da verdade ou copia gerada dela):
#   - branding/ (o pacote em si, fora do escopo deste grep);
#   - frontend/src/brand/ (modulo gerado do pacote + provider que o consome);
#   - identificadores tecnicos (__ZIIPSAN_CONFIG__, ziipsan-web, nomes de pacotes):
#     o grep usa palavra inteira, e '_' e caractere de palavra, logo esses nao casam.
# Uso: bash scripts/brand/check-brand-literals.sh [dir-do-frontend]
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FE="${1:-$ROOT/frontend}"
fail=0
check() {
  local pattern="$1" label="$2"
  # Exclui o diretorio do pacote no frontend; o resto e varrido.
  if grep -rn --include='*.ts' --include='*.tsx' --include='*.html' --include='*.json' -w "$pattern" \
      "$FE/src" "$FE/index.html" "$FE/public/config.json" 2>/dev/null \
      | grep -v "$FE/src/brand/" | grep -q .; then
    echo "ERRO: literal de marca '$label' fora do pacote de marca:"
    grep -rn --include='*.ts' --include='*.tsx' --include='*.html' --include='*.json' -w "$pattern" \
      "$FE/src" "$FE/index.html" "$FE/public/config.json" 2>/dev/null \
      | grep -v "$FE/src/brand/" || true
    fail=1
  fi
}
check 'ZIIPSAN' 'ZIIPSAN'
check 'Sistema Integrado de Gestão de Saneamento' 'descricao do produto'
if [ "$fail" -ne 0 ]; then
  echo "Use o BrandProvider / o pacote branding/ em vez de literais de marca."
  exit 1
fi
echo "OK: nenhum literal de marca fora do pacote"
