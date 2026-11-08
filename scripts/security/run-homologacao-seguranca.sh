#!/bin/bash
# Rodada de seguranca da homologacao local (P3.8) — encadeia, parando na
# primeira falha (mesmo espirito do scripts/ci.sh):
#   1. testes unitarios da suite de seguranca (platform + core)
#   2. matriz requisito-teste-evidencia (validador estatico)
#   3. DAST autenticada + portao de achados (exige stack local de pe)
# Uso (Git Bash, na raiz do repo): bash scripts/security/run-homologacao-seguranca.sh
set -euo pipefail
SRC="$(cd "$(dirname "$0")/../.." && pwd)"
echo "== testes da suite de seguranca (P3.8)"
bash "$SRC/scripts/build.sh" -pl platform/ziipsan-platform,shared/ziipsan-core -am \
  -Dtest='br.com.ziipsan.platform.securityreview.*,br.com.ziipsan.platform.portal.*,br.com.ziipsan.core.access.*' -Dsurefire.failIfNoSpecifiedTests=false test
echo "== matriz requisito-teste-evidencia"
python3 "$SRC/scripts/security/validar-matriz.py" "$SRC/docs/security/matriz-requisito-teste-evidencia.csv"
echo "== DAST autenticada + portao de achados"
bash "$SRC/scripts/security/run-dast.sh"
echo "== OK (seguranca da homologacao local)"
