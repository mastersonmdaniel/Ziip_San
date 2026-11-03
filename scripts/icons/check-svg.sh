#!/bin/bash
# P0.46 — recusa SVG nao sanitizado ou com cor literal no frontend.
#  - arquivos .svg em frontend/src: proibidos (icone vai pelo IconRegistry);
#  - <svg> inline em .tsx: proibido pelo mesmo motivo;
#  - se um .svg aparecer em frontend/src, ele tambem nao pode ter <script>,
#    foreignObject, atributo on*, referencia externa nem fill/stroke com
#    cor literal (so currentColor/none/var).
# Os ativos do pacote de marca (branding/ e sua copia em public/brand/)
# NAO entram aqui: o pacote e validado por scripts/brand/check-brand.mjs
# (que ja recusa script/foreignObject/on*/externo) e suas cores SAO a marca.
# Uso: bash scripts/icons/check-svg.sh [dir-do-frontend]
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FE="${1:-$ROOT/frontend}"
fail=0
if grep -rn --include='*.tsx' --include='*.ts' '<svg' "$FE/src" 2>/dev/null | grep -q .; then
  echo "ERRO: <svg> inline no frontend — use o IconRegistry (<Icon>):"
  grep -rn --include='*.tsx' --include='*.ts' '<svg' "$FE/src" || true
  fail=1
fi
while IFS= read -r file; do
  echo "ERRO: arquivo .svg em frontend/src (use o IconRegistry): $file"
  fail=1
  if grep -qE '<script|foreignObject|on[a-z]+ *=|href *= *"https?:|javascript:' "$file"; then
    echo "  + SVG nao sanitizado (script/foreignObject/on*/externo)"
  fi
  if grep -qE '(fill|stroke) *= *"#[0-9a-fA-F]' "$file"; then
    echo "  + SVG com cor literal (use currentColor)"
  fi
done < <(find "$FE/src" -name '*.svg' 2>/dev/null || true)
if [ "$fail" -ne 0 ]; then exit 1; fi
echo "OK: nenhum SVG nao sanitizado ou com cor literal no frontend"
