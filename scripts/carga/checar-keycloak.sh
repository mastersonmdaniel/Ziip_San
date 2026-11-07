#!/bin/bash
# P2.9 -- Sonda do realm do portal durante a carga (perna PLT-28).
#
# HONESTIDADE DA MEDICAO (correcao pos-revisao): esta sonda mede a latencia
# do endpoint de DISCOVERY do realm. Os limites da P0.23 transcritos em
# carga-p0.23.env descrevem OUTRAS grandezas -- p95 de LOGIN
# (KC_LOGIN_P95_MAX_MS), pico sustentado do portal (KC_PORTAL_PEAK_RPS) e
# taxa de erro do cenario de benchmark (KC_MAX_ERROR_RATE). Comparar p95 de
# discovery com limite de p95 de login seria comparar grandezas diferentes,
# e esta sonda nao gera carga de login nem mede vazao: portanto ela NAO
# aprova a perna PLT-28. O resultado e registrado como MEDICAO_PARCIAL,
# sem veredito de aprovacao (falha fechada): a aprovacao da PLT-28 exige o
# cenario que os limites descrevem (login/renovacao/client_credentials em
# massa, com o kit de benchmark da P0.23), que este aparato nao executa.
# Uso: checar-keycloak.sh <dir-saida> <duracao-seg>
set -uo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:?dir de saida}"; DUR="${2:?duracao em seg}"
[ -f "$DIR/carga-p0.23.env" ] || { echo "ERRO: falta scripts/carga/carga-p0.23.env (transcreva o registro da P0.23)"; exit 2; }
set -a; source "$DIR/carga-p0.23.env"; set +a
[ -n "${KEYCLOAK_PORTAL_REALM:-}" ] || { echo "ERRO: KEYCLOAK_PORTAL_REALM vazio"; exit 2; }
mkdir -p "$OUT"
AMOSTRAS="$OUT/keycloak-amostras.csv"; echo "epoch,ms,status" > "$AMOSTRAS"
URL="${KEYCLOAK_BASE_URL}/realms/${KEYCLOAK_PORTAL_REALM}/.well-known/openid-configuration"
FIM=$(( $(date +%s) + DUR ))
while [ "$(date +%s)" -lt "$FIM" ]; do
  RESP="$(curl -s -o /dev/null -w '%{http_code} %{time_total}' --max-time 30 "$URL" 2>/dev/null || echo '000 30')"
  COD="${RESP%% *}"; SEG="${RESP##* }"
  MS="$(awk -v s="$SEG" 'BEGIN{printf "%.1f", s*1000}')"
  echo "$(date +%s),$MS,$COD" >> "$AMOSTRAS"
  sleep 5
done
python3 - "$AMOSTRAS" "$OUT/keycloak.json" << 'PY'
import csv, json, math, sys
amostras, saida = sys.argv[1], sys.argv[2]
ms, erros = [], 0
with open(amostras, encoding="utf-8") as f:
    for row in csv.DictReader(f):
        try:
            ms.append(float(row["ms"]))
        except (TypeError, ValueError):
            continue  # ultima linha truncada por kill: ignora, nao derruba
        if row["status"] != "200":
            erros += 1
ms.sort()
def percentil_nr(s, p):  # nearest-rank, mesma regua do k6 e de medir-api.py
    return s[max(1, math.ceil(p / 100.0 * len(s))) - 1] if s else None
res = {"amostras": len(ms), "erros": erros,
       "taxa_erro": (erros / len(ms)) if ms else None,
       "p95_discovery_ms": percentil_nr(ms, 95),
       "medicao": "latencia do endpoint de discovery do realm do portal sob a carga",
       "veredito": "MEDICAO_PARCIAL",
       "nota": ("MEDICAO PARCIAL, sem veredito de aprovacao da PLT-28: os limites da "
                "P0.23 (p95 de login, pico de req/s, taxa de erro do cenario de "
                "benchmark) descrevem grandezas que esta sonda nao mede; comparar "
                "o p95 de discovery com o limite de p95 de login seria invalido.")}
json.dump(res, open(saida, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print(json.dumps(res, ensure_ascii=False, indent=2))
PY
