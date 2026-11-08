#!/bin/bash
# DAST autenticada (OWASP ZAP) da P3.8 contra o ambiente LOCAL de homologacao.
# Pre-condicoes (falha fechada, sem varredura pela metade):
#   - stack local de pe (P0.27): ZIIPSAN_BASE_URL apontando para 127.0.0.1/local
#   - usuarios sinteticos criados na massa local (ADR-0033), senhas via ambiente:
#       ZAP_PORTAL_USER, ZAP_PORTAL_PASS, ZAP_BACKOFFICE_USER, ZAP_BACKOFFICE_PASS
#   - docker disponivel para a imagem oficial do ZAP
# Saida: relatorio JSON em $ZAP_OUT (padrao /tmp/zap/p38-zap-report.json),
# avaliado por avaliar-achados.py (alto aberto = exit 1).
set -euo pipefail
: "${ZIIPSAN_BASE_URL:?ZIIPSAN_BASE_URL obrigatoria (ambiente local)}"
case "$ZIIPSAN_BASE_URL" in
  # Host local EXATO (fim, :porta ou /caminho logo apos o host): um host
  # como localhost.exemplo.com NAO e local (mesma regra do ZapScanPlan).
  http://127.0.0.1|http://127.0.0.1:*|http://127.0.0.1/*|http://localhost|http://localhost:*|http://localhost/*) ;;
  *) echo "RECUSADO: a P3.8 so varre o ambiente local (ADR-0033); producao e da LC.8" >&2; exit 2 ;;
esac
: "${ZAP_PORTAL_USER:?usuario sintetico do portal obrigatorio}"
: "${ZAP_PORTAL_PASS:?senha do usuario sintetico do portal obrigatoria}"
: "${ZAP_BACKOFFICE_USER:?usuario sintetico do backoffice obrigatorio}"
: "${ZAP_BACKOFFICE_PASS:?senha do usuario sintetico do backoffice obrigatoria}"
# Correcao D2 da revisao cruzada: CADA usuario e conferido SEPARADAMENTE,
# espelhando ZapScanPlan.problems() — contem o marcador 'sintetico' E
# termina em '.invalid'. A versao anterior testava a CONCATENACAO dos
# dois: bastava UM usuario sintetico para um usuario REAL passar, e o
# sufixo .invalid nunca era conferido.
for zap_user in "$ZAP_PORTAL_USER" "$ZAP_BACKOFFICE_USER"; do
  case "$zap_user" in
    *sintetico*.invalid) ;;
    *) echo "RECUSADO: usuario da varredura nao e sintetico marcado (*sintetico*.invalid): $zap_user" >&2; exit 2 ;;
  esac
done
OUT="${ZAP_OUT:-/tmp/zap/p38-zap-report.json}"
mkdir -p "$(dirname "$OUT")"
PLAN="$(cd "$(dirname "$0")/../.." && pwd)/docs/security/zap-plano-autenticado.yaml"
echo "== ZAP autenticado (portal + backoffice) contra $ZIIPSAN_BASE_URL"
docker run --rm --network host -v "$(dirname "$OUT"):/zap/wrk" -v "$(dirname "$PLAN"):/zap/plan:ro" \
  zaproxy/zap-stable zap.sh -cmd -autorun "/zap/plan/$(basename "$PLAN")"
python3 "$(dirname "$0")/avaliar-achados.py" "$OUT"
