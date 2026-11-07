#!/bin/bash
# P3.23 -- Recriacao DO ZERO do ambiente local de demonstracao, com tempo
# medido por etapa e no total.
#
# Sequencia: guardas (separacao da homologacao + fiscal desligado) ->
# ambiente (perfil demo da P0.27, scripts/local-env.sh demo --reset) ->
# massa sintetica (gerar-massa-demo.py) -> preflight -> sementes ->
# verificacao (contagem MEDIDA no banco >= 10.000) -> registro
# (recriacao.json com os tempos reais desta execucao).
#
# Falha fechada: sem demo.env, com tenant igual ao de dev/homolog, com
# fiscal liberado ou com contagem abaixo do piso, o script PARA dizendo
# o que falta. Nenhum tempo e estimado: os numeros do registro nascem
# dos relogios DESTA execucao, no PC do dono.
#
# Rode no PC do dono (Git Bash), com o fonte na raiz do repo:
#   cp scripts/demo/demo.env.example scripts/demo/demo.env  # e preencha
#   bash scripts/demo/recriar-demo.sh
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$(cd "$DIR/../.." && pwd)"
[ -f "$DIR/demo.env" ] || { echo "ERRO: copie demo.env.example para demo.env e preencha" >&2; exit 2; }
set -a; source "$DIR/demo.env"; set +a
: "${DEMO_TENANT_ID:?defina DEMO_TENANT_ID (tenant proprio da demonstracao)}"
: "${DEMO_TENANT_SCHEMA:?defina DEMO_TENANT_SCHEMA}"
UNIDADES="${DEMO_UNIDADES:-12000}"
SEMENTE="${DEMO_SEMENTE:-ziipsan-demo-p323-v1}"
REF="${DEMO_REFERENCE_MONTH:-2026-10-01}"
[ "$UNIDADES" -ge 10000 ] || { echo "ERRO: DEMO_UNIDADES=$UNIDADES < 10000 (piso do aceite da P3.23)" >&2; exit 2; }

# --- Guardas de separacao e de seguranca ---------------------------------
# 1) Demonstracao separada: tenant diferente do de desenvolvimento.
if [ -f "$SRC/.env" ]; then
  DEV_TENANT="$(grep -E '^ZIIPSAN_DEV_TENANT_ID=' "$SRC/.env" | cut -d= -f2- || true)"
  [ -z "$DEV_TENANT" ] || [ "$DEV_TENANT" != "$DEMO_TENANT_ID" ] || {
    echo "ERRO: DEMO_TENANT_ID igual ao ZIIPSAN_DEV_TENANT_ID -- demonstracao precisa de tenant proprio" >&2; exit 2; }
  DEV_SCHEMA="$(grep -E '^ZIIPSAN_DEV_TENANT_SCHEMA=' "$SRC/.env" | cut -d= -f2- || true)"
  [ -z "$DEV_SCHEMA" ] || [ "$DEV_SCHEMA" != "$DEMO_TENANT_SCHEMA" ] || {
    echo "ERRO: DEMO_TENANT_SCHEMA igual ao schema de desenvolvimento" >&2; exit 2; }
  # 2) Fiscal nunca liberado em perfil local (P0.27).
  if grep -Eq '^ZIIPSAN_FISCAL_PRODUCTION_ALLOWED=true' "$SRC/.env"; then
    echo "ERRO: ZIIPSAN_FISCAL_PRODUCTION_ALLOWED=true no .env -- perfil local nunca emite em producao" >&2; exit 2
  fi
fi
[ "${ZIIPSAN_FISCAL_PRODUCTION_ALLOWED:-false}" = "false" ] || {
  echo "ERRO: ZIIPSAN_FISCAL_PRODUCTION_ALLOWED nao e false no ambiente" >&2; exit 2; }
[ -x "$SRC/scripts/local-env.sh" ] || {
  echo "ERRO: scripts/local-env.sh nao encontrado -- rode a partir do fonte do repo (perfil demo e da P0.27)" >&2; exit 2; }

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="${DEMO_OUT_DIR:-$DIR/saidas/recriacao-$STAMP}"
mkdir -p "$OUT"
echo "== saida: $OUT"
T_INICIO=$(date +%s)
declare -a ETAPAS=()
etapa() { # etapa <nome> <segundos>
  ETAPAS+=("$1=$2")
  echo "   [$1] ${2}s"
}

PSQL=(psql -v ON_ERROR_STOP=1 -v tenant_id="$DEMO_TENANT_ID"
      -v semente="$SEMENTE" -v unidades="$UNIDADES"
      -v reference_month="$REF" -v demo_schema="$DEMO_TENANT_SCHEMA")
export PGOPTIONS="-c search_path=$DEMO_TENANT_SCHEMA,public"

echo "== 1/5 ambiente (perfil demo da P0.27, do zero)"
T0=$(date +%s)
bash "$SRC/scripts/local-env.sh" demo --reset > "$OUT/local-env.log" 2>&1
etapa "ambiente_demo_reset" $(( $(date +%s) - T0 ))

echo "== 2/5 massa sintetica (>= 10.000 unidades, documentos marcados)"
T0=$(date +%s)
python3 "$DIR/gerar-massa-demo.py" --unidades "$UNIDADES" --semente "$SEMENTE" \
  --saida "$OUT/massa" > "$OUT/massa.log"
etapa "geracao_massa" $(( $(date +%s) - T0 ))

echo "== 3/5 preflight"
T0=$(date +%s)
"${PSQL[@]}" -f "$DIR/sql/00-preflight.sql" > "$OUT/preflight.log" 2>&1
etapa "preflight" $(( $(date +%s) - T0 ))

echo "== 4/5 sementes (registry [CONFERIR-V3_1], tarifas/grupos e medicao)"
T0=$(date +%s)
# Revisao cruzada P3.23: sem este desvio, o set -e matava o script na
# falha esperada da medicao (FK para consumer_units enquanto o trecho
# [CONFERIR-V3_1] nao e materializado) e a orientacao abaixo -- o
# caminho de falha desenhado -- nunca era alcancada.
if ! "${PSQL[@]}" -f "$DIR/sql/10-seed-demo.sql" > "$OUT/seed-demo.log" 2>&1; then
  echo "ERRO: sementes abortaram (log em $OUT/seed-demo.log)." >&2
  echo "Causa esperada enquanto o trecho [CONFERIR-V3_1] nao for materializado:" >&2
  echo "a medicao (V5_1) tem FK para consumer_units, que o esqueleto nao insere." >&2
  echo "Categorias/tarifas/grupos (transacao 1 do 10-seed-demo.sql) ja commitaram;" >&2
  echo "materialize unidades/titulares contra a V3_1 e rode de novo." >&2
  echo "Recriacao registrada como FALHA (ver $OUT); nada foi dado por pronto." >&2
  exit 3
fi
etapa "sementes" $(( $(date +%s) - T0 ))
MEDIDAS="$("${PSQL[@]}" -tA -c "SELECT count(*) FROM consumer_units WHERE tenant_id = '$DEMO_TENANT_ID'")"
[ "$MEDIDAS" -ge "$UNIDADES" ] || {
  echo "ERRO: consumer_units do tenant demo = $MEDIDAS < $UNIDADES." >&2
  echo "Confira o trecho [CONFERIR-V3_1] do sql/10-seed-demo.sql contra a V3_1 real." >&2
  echo "Recriacao registrada como FALHA (ver $OUT); nada foi dado por pronto." >&2
  exit 3; }

echo "== 5/5 verificacao"
T0=$(date +%s)
"${PSQL[@]}" -f "$DIR/sql/90-verificacao.sql" > "$OUT/verificacao.log" 2>&1
etapa "verificacao" $(( $(date +%s) - T0 ))

TOTAL=$(( $(date +%s) - T_INICIO ))
# Registro da recriacao: tempos MEDIDOS desta execucao (JSON via python3,
# nunca montado por concatenacao de shell).
python3 - "$OUT/recriacao.json" "$DEMO_TENANT_ID" "$UNIDADES" "$TOTAL" "${ETAPAS[*]}" <<'PY'
import json, sys
etapas = {}
for par in sys.argv[5].split():
    nome, seg = par.split("=")
    etapas[nome] = int(seg)
registro = {
    "tarefa": "P3.23",
    "tipo": "recriacao_do_zero_ambiente_demonstracao_local",
    "tenant_demo": sys.argv[2],
    "unidades_semeadas": int(sys.argv[3]),
    "tempo_total_seg": int(sys.argv[4]),
    "tempo_por_etapa_seg": etapas,
    "massa": "sintetica declarada (ver massa/manifesto.json)",
}
with open(sys.argv[1], "w", encoding="utf-8") as f:
    json.dump(registro, f, ensure_ascii=False, indent=2)
    f.write("\n")
print(json.dumps(registro, ensure_ascii=False, indent=2))
PY
echo "== OK: ambiente de demonstracao recriado em ${TOTAL}s (registro em $OUT/recriacao.json)"
