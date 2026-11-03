#!/bin/bash
# Verificacao completa local (o mesmo que um CI faria): banco de integracao virgem, build Java com
# todos os testes e o frontend (typecheck, lint quando configurado, testes, build e audit).
#
# Uso (Git Bash): scripts/ci.sh
# Sai com erro na primeira falha. Trava compartilhada com build.sh/reset-it-db.sh.
source "$(dirname "$0")/lib.sh"

acquire_lock

echo "== banco de integracao"
bash "$SRC/scripts/reset-it-db.sh"

echo "== backend (mvn clean verify)"
bash "$SRC/scripts/build.sh" clean verify

echo "== frontend"
FE="$BUILD_DIR/ci-frontend"
mkdir -p "$FE"
rc=0
powershell.exe -NoProfile -Command "robocopy '$(cygpath -w "$SRC/frontend")' '$(cygpath -w "$FE")' /MIR /XD node_modules dist coverage /NFL /NDL /NJH /NJS /NP | Out-Null; exit \$LASTEXITCODE" || rc=$?
[ "$rc" -lt 8 ] || die "robocopy do frontend falhou (codigo $rc)"
(
  cd "$FE"
  npm ci --no-audit --no-fund --loglevel=error
  npm run -s typecheck
  if npm run -s 2>/dev/null | grep -q "^  lint"; then npm run -s lint; fi
  npx vitest run
  npm run -s build
  # Relatado, nao bloqueante: as correcoes atuais exigem versao principal nova (ver diario).
  npm audit --omit=dev --audit-level=high || echo "AVISO: npm audit encontrou vulnerabilidades (ver acima)"
)
echo "== marca, temas e icones (P0.34/P0.35/P0.36/P0.46)"
node "$SRC/scripts/brand/check-brand.mjs"
bash "$SRC/scripts/brand/sync-brand-resources.sh" --check
bash "$SRC/scripts/brand/check-brand-literals.sh" "$FE"
bash "$SRC/scripts/icons/check-svg.sh" "$FE"
node "$SRC/scripts/theme/check-theme-script-hash.mjs"
node "$SRC/scripts/brand/test-brand-swap.mjs"
echo "== OK"
