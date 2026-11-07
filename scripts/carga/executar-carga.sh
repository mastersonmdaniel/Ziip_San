#!/bin/bash
# P2.9 -- Orquestrador da carga de 300 mil ligacoes (rode no PC do dono,
# Git Bash, com o ambiente local de homologacao no ar -- perfil homolog da
# P0.27 -- e o banco migrado). NUNCA contra producao: o tenant e descartavel.
#
# Sequencia: config -> massa (CSV+manifesto) -> preflight (tabelas e
# referencia, SEM categorias) -> sementes (registry; checagem de categorias
# e de unidades LOGO APOS o registry; tarifas/grupos; medicao) ->
# [EM PARALELO: sonda de p95 da API + coleta de recursos + sonda do
# Keycloak] + disparo do faturamento com relogio (limite do aceite:
# 7200 s para 100 mil contas; o disparo, via BILLING_TRIGGER_CMD/fiacao
# M7, e quem abre a referencia pelo CycleService e comanda a atividade
# BILLING) -> verificacao SQL -> registro (resultado.json na saida).
#
# Falha fechada, sem fingir: sem BILLING_TRIGGER_CMD (fiacao M7 da p2b) ou
# sem os numeros da P0.23 em carga-p0.23.env, o script PARA dizendo o que
# falta. Se o disparo FALHAR, as sondas sao encerradas, a consolidacao roda
# e o fracasso fica registrado em resultado.json (nada de aborto silencioso
# com sonda orfa). Saida: diretorio carga-<data-hora>/ com os artefatos.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
[ -f "$DIR/carga.env" ] || { echo "ERRO: copie carga.env.example para carga.env e preencha"; exit 2; }
set -a; source "$DIR/carga.env"; set +a
: "${CARGA_TENANT_ID:?defina CARGA_TENANT_ID (tenant descartavel da carga)}"
: "${BILLING_TRIGGER_CMD:?defina BILLING_TRIGGER_CMD -- sem o disparo real do ciclo (fiacao M7 da integracao p2b) a carga nao fatura; ver carga.env.example}"
[ -f "$DIR/carga-p0.23.env" ] || { echo "ERRO: falta carga-p0.23.env com a capacidade medida na P0.23 (PLT-28)"; exit 2; }
set -a; source "$DIR/carga-p0.23.env"; set +a  # baseline/capacidade da P0.23

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="${CARGA_OUT_DIR:-$DIR/saidas/carga-$STAMP}"
mkdir -p "$OUT"
echo "== saida: $OUT"
PSQL=(psql -v ON_ERROR_STOP=1 -v tenant_id="$CARGA_TENANT_ID"
      -v semente="${CARGA_SEMENTE:-ziipsan-carga-p29-v1}"
      -v ligacoes="${CARGA_LIGACOES:-300000}" -v faturar="${CARGA_FATURAR:-100000}"
      -v reference_month="${CARGA_REFERENCE_MONTH:-2026-10-01}")

echo "== 1/6 massa sintetica (deterministica)"
python3 "$DIR/gerar-massa.py" --ligacoes "${CARGA_LIGACOES:-300000}" \
  --faturar "${CARGA_FATURAR:-100000}" --semente "${CARGA_SEMENTE:-ziipsan-carga-p29-v1}" \
  --saida "$OUT/massa" > "$OUT/massa.log"

echo "== 2/6 preflight (etapa 1: tabelas no schema do tenant + referencia livre)"
"${PSQL[@]}" -f "$DIR/sql/00-preflight.sql" > "$OUT/preflight.log" 2>&1

echo "== 3/6 sementes"
"${PSQL[@]}" -f "$DIR/sql/10-seed-registry.sql"  > "$OUT/seed-registry.log" 2>&1
# Etapa 2 do preflight (pos-registry): as categorias RES/COM so existem
# DEPOIS do seed do registry -- exigir antes abortava a primeira execucao
# em tenant novo por construcao (correcao pos-revisao).
CATEGORIAS="$("${PSQL[@]}" -tA -c "SELECT count(*) FROM categories WHERE tenant_id = '$CARGA_TENANT_ID'")"
[ "$CATEGORIAS" -ge 2 ] || {
  echo "ERRO: categories do tenant = $CATEGORIAS < 2 apos o 10-seed-registry.sql." >&2
  echo "Confira o trecho de categorias contra a V3_1 (ver o arquivo). Carga abortada ANTES da medicao." >&2
  exit 3; }
UNIDADES="$("${PSQL[@]}" -tA -c "SELECT count(*) FROM consumer_units WHERE tenant_id = '$CARGA_TENANT_ID'")"
[ "$UNIDADES" -ge "${CARGA_LIGACOES:-300000}" ] || {
  echo "ERRO: consumer_units do tenant = $UNIDADES < ${CARGA_LIGACOES:-300000}." >&2
  echo "O passo de unidades do 10-seed-registry.sql precisa ser confirmado contra a V3_1 (ver o arquivo). Carga abortada ANTES da medicao." >&2
  exit 3; }
"${PSQL[@]}" -f "$DIR/sql/20-seed-tarifas-ciclo.sql" > "$OUT/seed-tarifas.log" 2>&1
"${PSQL[@]}" -f "$DIR/sql/30-seed-medicao.sql"       > "$OUT/seed-medicao.log" 2>&1

echo "== 4/6 medicao em paralelo + faturamento (limite ${CARGA_LIMITE_SEG:-7200}s)"
# As tres medicoes cobrem a MESMA janela do faturamento, em segundo plano.
bash "$DIR/coletar-recursos.sh" "$OUT" "${CARGA_LIMITE_SEG:-7200}" 10 & PID_RES=$!
bash "$DIR/checar-keycloak.sh" "$OUT" "${CARGA_LIMITE_SEG:-7200}" & PID_KC=$!
API_BASE_URL="${API_BASE_URL:-}" P95_MAX_MS="${P95_MAX_MS:-}" P95_BASELINE_MS="${P95_BASELINE_MS:-}" \
  API_PROBE_PATHS="${API_PROBE_PATHS:-/actuator/health}" API_BEARER_TOKEN="${API_BEARER_TOKEN:-}" \
  python3 "$DIR/medir-api.py" --duracao-seg "${CARGA_LIMITE_SEG:-7200}" \
  --concorrencia "${API_PROBE_CONCORRENCIA:-8}" --saida "$OUT/api" & PID_API=$!
INI=$(date +%s)
# Disparo: se falhar, NAO abortamos em silencio -- registramos o rc,
# encerramos as sondas, consolidamos e o resultado.json sai de falha.
TRIGGER_RC=0
bash -c "$BILLING_TRIGGER_CMD" > "$OUT/faturamento-disparo.log" 2>&1 || TRIGGER_RC=$?
if [ "$TRIGGER_RC" -ne 0 ]; then
  echo "ERRO: BILLING_TRIGGER_CMD falhou (rc=$TRIGGER_RC); ver $OUT/faturamento-disparo.log" >&2
  echo "Encerrando as sondas e registrando o fracasso no resultado.json." >&2
else
  # Espera ativa ate o lote concluir (ou estourar o limite do aceite).
  while : ; do
    AGORA=$(date +%s); DECORRIDO=$((AGORA - INI))
    if [ -n "${BILLING_DONE_CMD:-}" ]; then
      bash -c "$BILLING_DONE_CMD" >/dev/null 2>&1 && break
    else
      CONTAS="$("${PSQL[@]}" -tA -c "SELECT count(*) FROM bills WHERE tenant_id = '$CARGA_TENANT_ID' AND reference_month = '${CARGA_REFERENCE_MONTH:-2026-10-01}' AND completed = TRUE AND status NOT IN ('CANCELLED','CANCELLED_BY_RECTIFICATION','PROCESSING_ERROR')" 2>/dev/null || echo 0)"
      [ "${CONTAS:-0}" -ge "${CARGA_FATURAR:-100000}" ] && break
    fi
    [ "$DECORRIDO" -ge "${CARGA_LIMITE_SEG:-7200}" ] && break
    sleep 15
  done
fi
FIM=$(date +%s); DURACAO=$((FIM - INI))
# Janela fechou: encerra as medicoes e consolida os resumos a partir dos
# CSVs amostrados (os processos de fundo so escreveriam o resumo ao fim do
# prazo cheio; a consolidacao usa exatamente as amostras da janela real).
kill "$PID_RES" "$PID_KC" "$PID_API" 2>/dev/null || true
wait "$PID_RES" "$PID_KC" "$PID_API" 2>/dev/null || true
# Consolidacao (sobrevive a set -e: linhas truncadas pelo kill sao
# ignoradas com guarda, e o bloco inteiro nao derruba o orquestrador).
P95_MAX_MS="${P95_MAX_MS:-}" P95_BASELINE_MS="${P95_BASELINE_MS:-}" \
python3 - "$OUT" << 'PYEOF' || echo "AVISO: consolidacao parcial falhou; o registro saira incompleto" >&2
import csv, json, math, os, sys
out = sys.argv[1]
def percentil(s, p):
    # NEAREST-RANK, a regua do k6 e de medir-api.py: ceil(p/100 * n).
    return s[max(1, math.ceil(p / 100.0 * len(s))) - 1] if s else None
def veredito_p95(p95, teto, base):
    # MESMA semantica de medir-api.py: REPROVADO de QUALQUER criterio
    # definido (teto OU baseline+15%) prevalece; APROVADO so se TODOS os
    # criterios definidos passarem; nenhum definido -> sem teto.
    criterios = []
    if teto and p95 is not None:
        criterios.append(p95 <= float(teto))
    if base and p95 is not None:
        criterios.append(p95 <= float(base) * 1.15)
    if not criterios:
        return "MEDIDO_SEM_TETO_DEFINIDO"
    return "APROVADO" if all(criterios) else "REPROVADO"
# API: consolida de api/amostras-api.csv se o resumo final nao existir.
api_csv = os.path.join(out, "api", "amostras-api.csv")
api_json = os.path.join(out, "api", "resumo-api.json")
if os.path.exists(api_csv) and not os.path.exists(api_json):
    ms, erros = [], 0
    with open(api_csv, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            try:
                valor, status = float(row["ms"]), int(row["status"])
            except (TypeError, ValueError, KeyError):
                continue  # ultima linha truncada pelo kill: ignora
            ms.append(valor)
            if row.get("erro") or status >= 500 or status == 0:
                erros += 1
    ms.sort()
    resumo = {"amostras": len(ms), "erros": erros,
              "p50_ms": percentil(ms, 50), "p95_ms": percentil(ms, 95),
              "p99_ms": percentil(ms, 99), "max_ms": ms[-1] if ms else None,
              "consolidado_da_janela": True}
    teto, base = os.environ.get("P95_MAX_MS", ""), os.environ.get("P95_BASELINE_MS", "")
    if teto:
        resumo["teto_ms"] = float(teto)
    if base:
        resumo["baseline_ms"] = float(base)
        resumo["limite_regressao_15pct_ms"] = float(base) * 1.15
    resumo["veredito_p95"] = veredito_p95(resumo["p95_ms"], teto, base)
    json.dump(resumo, open(api_json, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
# Keycloak: idem, de keycloak-amostras.csv. SEMPRE MEDICAO_PARCIAL: a sonda
# mede latencia de DISCOVERY; os limites da P0.23 sao de LOGIN/vazao/erro
# do cenario de benchmark -- grandezas incomparaveis nao geram aprovacao.
kc_csv = os.path.join(out, "keycloak-amostras.csv")
kc_json = os.path.join(out, "keycloak.json")
if os.path.exists(kc_csv) and not os.path.exists(kc_json):
    ms, erros = [], 0
    with open(kc_csv, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            try:
                ms.append(float(row["ms"]))
            except (TypeError, ValueError, KeyError):
                continue  # ultima linha truncada pelo kill: ignora
            if row.get("status") != "200":
                erros += 1
    ms.sort()
    json.dump({"amostras": len(ms), "erros": erros, "p95_discovery_ms": percentil(ms, 95),
               "medicao": "latencia do endpoint de discovery do realm do portal sob a carga",
               "veredito": "MEDICAO_PARCIAL",
               "nota": ("MEDICAO PARCIAL, sem veredito de aprovacao da PLT-28: os limites "
                        "da P0.23 descrevem grandezas que esta sonda nao mede."),
               "consolidado_da_janela": True},
              open(kc_json, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
PYEOF
echo "duracao do faturamento: ${DURACAO}s (limite ${CARGA_LIMITE_SEG:-7200}s)"

echo "== 5/6 verificacao"
"${PSQL[@]}" -f "$DIR/sql/90-verificacao.sql" > "$OUT/verificacao.txt" 2>&1 || true

echo "== 6/6 registro"
python3 - "$OUT" "$DURACAO" "${CARGA_LIMITE_SEG:-7200}" "$TRIGGER_RC" << 'PY'
import csv, json, os, sys
out, duracao, limite, trigger_rc = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
def ler(p):
    try:
        return json.load(open(os.path.join(out, p), encoding="utf-8"))
    except Exception:
        return None
manifesto = ler("massa/manifesto.json") or {}
api = ler("api/resumo-api.json") or {}
kc = ler("keycloak.json") or {}
# Volume = contagens MEDIDAS no banco (90-verificacao.sql), nao o alvo do
# manifesto. O manifesto entra so como alvo de comparacao.
medido = {}
try:
    for linha in open(os.path.join(out, "verificacao.txt"), encoding="utf-8"):
        if "|" in linha:
            nome, _, valor = linha.partition("|")
            try:
                medido[nome.strip()] = int(valor.strip())
            except ValueError:
                pass
except Exception:
    pass
contas = medido.get("contas_concluidas_referencia")
alvo = (manifesto.get("faturaveis") or 0)
# Uso de recursos: picos consolidados de recursos.csv por container.
picos = {}
try:
    with open(os.path.join(out, "recursos.csv"), encoding="utf-8") as f:
        for row in csv.DictReader(f):
            nome = (row.get("container") or "").strip()
            if not nome:
                continue
            p = picos.setdefault(nome, {"cpu_pct_max": None, "mem_pct_max": None, "amostras": 0})
            p["amostras"] += 1
            for chave, col in (("cpu_pct_max", "cpu_pct"), ("mem_pct_max", "mem_pct")):
                try:
                    v = float((row.get(col) or "").strip().rstrip("%"))
                except ValueError:
                    continue
                p[chave] = v if p[chave] is None else max(p[chave], v)
except Exception:
    pass
registro = {
    "tarefa": "P2.9", "massa": "sintetica declarada",
    "semente": manifesto.get("semente"),
    "volume": {"ligacoes_medidas": medido.get("unidades"),
               "leituras_medidas": medido.get("leituras_referencia"),
               "consumos_medidos": medido.get("consumos_referencia"),
               "contas_alvo": alvo,
               "contas_concluidas": contas,
               "contas_erro": medido.get("contas_erro_referencia")},
    "duracao_faturamento_seg": duracao, "limite_seg": limite,
    "dentro_do_limite": bool(trigger_rc == 0 and contas is not None
                             and contas >= alvo and duracao < limite),
    "disparo_faturamento": {"comando_rc": trigger_rc, "ok": trigger_rc == 0},
    "api": api, "keycloak": kc,
    "recursos": {"arquivo_csv": "recursos.csv", "picos_por_container": picos},
    "veredito": None,
}
# Veredito global, FALHA FECHADA: MEDIDO_SEM_TETO_DEFINIDO nao aprova --
# sem teto nem baseline, a perna de p95 fica inconclusiva e o resultado
# NAO sai APROVADO. A perna do Keycloak, em MEDICAO_PARCIAL, tambem nao
# aprova (a PLT-28 exige o cenario dos limites, nao a sonda de discovery).
ok = (registro["dentro_do_limite"]
      and api.get("veredito_p95") == "APROVADO"
      and kc.get("veredito") == "APROVADO")
registro["veredito"] = "APROVADO" if ok else "REPROVADO_OU_INCOMPLETO"
json.dump(registro, open(os.path.join(out, "resultado.json"), "w", encoding="utf-8"),
          ensure_ascii=False, indent=2)
print(json.dumps(registro, ensure_ascii=False, indent=2))
sys.exit(0 if ok else 1)
PY
echo "== fim: artefatos em $OUT (preencha docs/carga/REGISTRO a partir do resultado.json)"
