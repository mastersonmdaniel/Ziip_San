#!/bin/bash
# Harness da GL.1 — roda SEM Docker/Postgres/JDK, com stubs (padrão P4.7/GL.3):
# monta um repo falso mínimo (local-env.sh stub, compose stub, .env),
# manifestos e extrações sintéticas, e prova o comportamento do
# orquestrador provisionar-homologacao-local.sh + validar-manifesto.py.
# Nenhuma asserção é afrouxada para passar: cada caso espera o código de
# saída e o registro que o orquestrador promete no cabeçalho dele.
set -uo pipefail

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORQ="$AQUI/homologacao/provisionar-homologacao-local.sh"
VAL="$AQUI/homologacao/validar-manifesto.py"
PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); echo "ok   - $1"; }
bad()  { FAIL=$((FAIL+1)); echo "FALHOU - $1"; }
espera() { # $1=rótulo $2=código esperado $3=código obtido
  if [ "$2" = "$3" ]; then ok "$1 (saída $3)"; else bad "$1 (esperava $2, veio $3)"; fi
}

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
SRC_FALSO="$TMP/repo"
mkdir -p "$SRC_FALSO/scripts" "$SRC_FALSO/docs/homologacao/cargas-homologacao" "$TMP/entrada" "$TMP/ordem"
printf '#!/bin/bash\necho "local-env stub: perfil $1 no ar"\n' > "$SRC_FALSO/scripts/local-env.sh"
# Compose falso no FORMATO do compose real da P0.27 (conferido no espelho):
# a variável fiscal é literal "false" no environment de api e de migrate e
# NÃO vem do .env -- o portão fiscal (D3) confere ESTA fonte e recusa sem ela.
escrever_compose() { # $1=valor fiscal literal p/ api+migrate, ou "AUSENTE"
  if [ "$1" = "AUSENTE" ]; then
    printf 'services:\n  api:\n    environment:\n      ZIIPSAN_PORT: 8090\n' > "$SRC_FALSO/docker-compose.yml"
  else
    printf 'services:\n  api:\n    environment:\n      ZIIPSAN_FISCAL_PRODUCTION_ALLOWED: "%s"\n  migrate:\n    environment:\n      ZIIPSAN_FISCAL_PRODUCTION_ALLOWED: "%s"\n' "$1" "$1" > "$SRC_FALSO/docker-compose.yml"
  fi
}
escrever_compose false
printf 'POSTGRES_SUPERUSER_PASSWORD=x\nZIIPSAN_FISCAL_PRODUCTION_ALLOWED=false\n' > "$SRC_FALSO/.env"

manifesto_ok() { # $1=arquivo — massa 100% sintética, escrita em JSON válido
  python3 - "$1" <<'PY'
import json,sys
m={"tenantId":"11111111-1111-1111-1111-111111111111",
   "codigoSubstituto":True,
   "totaisPiloto":{"unidades":3,"saldoTotalCentavos":133456,"registrosHistorico":36,"xmlAceitos":2},
   "xmlOrigem":"SINTETICO_CERTIFICADO_TESTE",
   "camadasGis":[
     {"nome":"rede-agua","tipo":"REDE_REAL","acessoRestrito":{"grupo":"homologacao-gis","responsaveis":["responsavel-sintetico-exemplo"]}},
     {"nome":"ramais","tipo":"RAMAL","metodoAnonimizacao":"SHIFTED_WITHIN_BLOCK"},
     {"nome":"imoveis","tipo":"IMOVEL","metodoAnonimizacao":"AGGREGATED_BY_BLOCK"}]}
json.dump(m,open(sys.argv[1],"w"),ensure_ascii=False,indent=1)
PY
}
MAN="$TMP/manifesto.json"; manifesto_ok "$MAN"
printf 'codigo_substituto;saldo_centavos\nSUB-001;10000\nSUB-002;23456\n' > "$TMP/entrada/cadastro.csv"
printf '{"unidades":3,"saldoTotalCentavos":133456,"registrosHistorico":36,"xmlAceitos":2}\n' > "$TMP/totais-ok.json"
printf '{"unidades":3,"saldoTotalCentavos":999,"registrosHistorico":36,"xmlAceitos":2}\n' > "$TMP/totais-div.json"

rodar() { # executa o orquestrador contra o repo falso; "$@" = args extras
  ZIIPSAN_GL1_SRC="$SRC_FALSO" bash "$ORQ" "$@"
}
ultimo_registro() { ls -t "$SRC_FALSO"/docs/homologacao/cargas-homologacao/*/registro-carga.md 2>/dev/null | head -1; }

echo "== validador do manifesto =="
python3 "$VAL" "$MAN" >/dev/null 2>&1; espera "manifesto válido aceito" 0 $?
python3 - "$MAN" "$TMP/man-real.json" <<'PY'
import json,sys
m=json.load(open(sys.argv[1])); m["xmlOrigem"]="REAL"
json.dump(m,open(sys.argv[2],"w"))
PY
python3 "$VAL" "$TMP/man-real.json" >/dev/null 2>&1; espera "XML real recusado no manifesto" 1 $?
python3 - "$MAN" "$TMP/man-gis.json" <<'PY'
import json,sys
m=json.load(open(sys.argv[1]))
m["camadasGis"][1].pop("metodoAnonimizacao")
json.dump(m,open(sys.argv[2],"w"))
PY
python3 "$VAL" "$TMP/man-gis.json" >/dev/null 2>&1; espera "camada pessoal sem método recusada" 1 $?
python3 - "$MAN" "$TMP/man-rede.json" <<'PY'
import json,sys
m=json.load(open(sys.argv[1]))
m["camadasGis"][0].pop("acessoRestrito")
json.dump(m,open(sys.argv[2],"w"))
PY
python3 "$VAL" "$TMP/man-rede.json" >/dev/null 2>&1; espera "rede real sem acesso restrito recusada" 1 $?

echo "== validador: correções D1 (grafia/whitelist) e D7 (tenantId/responsaveis) =="
python3 - "$MAN" "$TMP/man-ramal-min.json" <<'PY'
import json,sys
m=json.load(open(sys.argv[1]))
m["camadasGis"][1]={"nome":"ramais","tipo":"ramal"}  # minúsculo e SEM método
json.dump(m,open(sys.argv[2],"w"))
PY
python3 "$VAL" "$TMP/man-ramal-min.json" >/dev/null 2>&1; espera "D1: 'ramal' minúsculo sem método recusado" 1 $?
python3 - "$MAN" "$TMP/man-ramal-min-ok.json" <<'PY'
import json,sys
m=json.load(open(sys.argv[1]))
m["camadasGis"][1]={"nome":"ramais","tipo":"ramal","metodoAnonimizacao":"SHIFTED_WITHIN_BLOCK"}
json.dump(m,open(sys.argv[2],"w"))
PY
python3 "$VAL" "$TMP/man-ramal-min-ok.json" >/dev/null 2>&1; espera "D1: 'ramal' minúsculo COM método aceito (grafia normalizada)" 0 $?
python3 - "$MAN" "$TMP/man-tipo-desc.json" <<'PY'
import json,sys
m=json.load(open(sys.argv[1]))
m["camadasGis"][1]={"nome":"x","tipo":"CAMADA_INVENTADA","metodoAnonimizacao":"SHIFTED_WITHIN_BLOCK"}
json.dump(m,open(sys.argv[2],"w"))
PY
python3 "$VAL" "$TMP/man-tipo-desc.json" >/dev/null 2>&1; espera "D1: tipo desconhecido recusado (whitelist fail-closed)" 1 $?
python3 - "$MAN" "$TMP/man-tenant-num.json" <<'PY'
import json,sys
m=json.load(open(sys.argv[1])); m["tenantId"]=12345
json.dump(m,open(sys.argv[2],"w"))
PY
python3 "$VAL" "$TMP/man-tenant-num.json" >/dev/null 2>&1; espera "D7: tenantId numérico recusado" 1 $?
python3 - "$MAN" "$TMP/man-sem-resp.json" <<'PY'
import json,sys
m=json.load(open(sys.argv[1]))
m["camadasGis"][0]["acessoRestrito"]={"grupo":"homologacao-gis","responsaveis":[]}
json.dump(m,open(sys.argv[2],"w"))
PY
python3 "$VAL" "$TMP/man-sem-resp.json" >/dev/null 2>&1; espera "D7: rede real sem responsaveis nomeados recusada" 1 $?
python3 - "$MAN" "$TMP/man-rede-min.json" <<'PY'
import json,sys
m=json.load(open(sys.argv[1]))
m["camadasGis"][0]={"nome":"rede-agua","tipo":"rede_real"}  # minúsculo e SEM acesso restrito
json.dump(m,open(sys.argv[2],"w"))
PY
python3 "$VAL" "$TMP/man-rede-min.json" >/dev/null 2>&1; espera "D1+D7: 'rede_real' minúscula sem acesso restrito recusada" 1 $?

echo "== dry-run =="
SAIDA="$(rodar --manifesto "$MAN" --entrada-dir "$TMP/entrada" --totais-carregados "$TMP/totais-ok.json" --dry-run 2>&1)"; RC=$?
espera "dry-run sai 0" 0 $RC
case "$SAIDA" in *"PLANO GL.1"*) ok "dry-run imprime o plano";; *) bad "dry-run imprime o plano";; esac
[ -z "$(ls "$SRC_FALSO"/docs/homologacao/cargas-homologacao/ 2>/dev/null)" ] && ok "dry-run não grava registro" || bad "dry-run não grava registro"

echo "== pré-voo =="
rodar --entrada-dir "$TMP/entrada" >/dev/null 2>&1; espera "sem manifesto -> 2" 2 $?
printf 'ZIIPSAN_FISCAL_PRODUCTION_ALLOWED=true\n' >> "$SRC_FALSO/.env"
rodar --manifesto "$MAN" --entrada-dir "$TMP/entrada" --preflight-only >/dev/null 2>&1; espera "fiscal de produção ligado -> 2" 2 $?
printf 'POSTGRES_SUPERUSER_PASSWORD=x\nZIIPSAN_FISCAL_PRODUCTION_ALLOWED=false\n' > "$SRC_FALSO/.env"

echo "== pré-voo: portão fiscal D3 (fonte = compose; parsing robusto) =="
printf 'ZIIPSAN_FISCAL_PRODUCTION_ALLOWED=TRUE\n' >> "$SRC_FALSO/.env"
rodar --manifesto "$MAN" --entrada-dir "$TMP/entrada" --preflight-only >/dev/null 2>&1; espera "D3: fiscal =TRUE (maiúsculas) no .env -> 2" 2 $?
printf 'POSTGRES_SUPERUSER_PASSWORD=x\nZIIPSAN_FISCAL_PRODUCTION_ALLOWED=false\n' > "$SRC_FALSO/.env"
printf 'ZIIPSAN_FISCAL_PRODUCTION_ALLOWED="true"\n' >> "$SRC_FALSO/.env"
rodar --manifesto "$MAN" --entrada-dir "$TMP/entrada" --preflight-only >/dev/null 2>&1; espera "D3: fiscal =\"true\" (com aspas) no .env -> 2" 2 $?
printf 'POSTGRES_SUPERUSER_PASSWORD=x\nZIIPSAN_FISCAL_PRODUCTION_ALLOWED=false\n' > "$SRC_FALSO/.env"
escrever_compose true
rodar --manifesto "$MAN" --entrada-dir "$TMP/entrada" --preflight-only >/dev/null 2>&1; espera "D3: fiscal true no COMPOSE (fonte que controla) -> 2" 2 $?
escrever_compose AUSENTE
rodar --manifesto "$MAN" --entrada-dir "$TMP/entrada" --preflight-only >/dev/null 2>&1; espera "D3: fiscal ausente no compose (indeterminado) -> 2" 2 $?
escrever_compose false

echo "== erro de argumento grava registro (D5/D6) =="
rodar --opcao-que-nao-existe >/dev/null 2>&1; espera "D5: opção desconhecida -> 2" 2 $?
REG="$(ultimo_registro)"; grep -q "FALHA" "$REG" && ok "D5: erro de argumento gravou registro de FALHA" || bad "D5: erro de argumento gravou registro de FALHA"
rodar --keep-env >/dev/null 2>&1; espera "D6: --keep-env removida -> 2 (opção desconhecida, com registro)" 2 $?

echo "== varredura de entrada =="
printf 'nome;documento\nFulano Sintetico;123.456.789-09\n' > "$TMP/entrada/ruim.csv"
rodar --manifesto "$MAN" --entrada-dir "$TMP/entrada" --preflight-only >/dev/null 2>&1; espera "CPF formatado na extração -> 3" 3 $?
REG="$(ultimo_registro)"; grep -q "FALHA" "$REG" && ok "recusa gravou registro de FALHA" || bad "recusa gravou registro de FALHA"
rm "$TMP/entrada/ruim.csv"
printf '# nota da extracao\nFulano Sintetico;123.456.789-09\n' > "$TMP/entrada/nota.md"
rodar --manifesto "$MAN" --entrada-dir "$TMP/entrada" --preflight-only >/dev/null 2>&1; espera "D2: CPF formatado em arquivo .md -> 3 (sem exclusão de Markdown)" 3 $?
rm "$TMP/entrada/nota.md"
printf '\x00\x02BIN\x00 contato sintetico@exemplo.invalid \x00' > "$TMP/entrada/lote.bin"
rodar --manifesto "$MAN" --entrada-dir "$TMP/entrada" --preflight-only >/dev/null 2>&1; espera "D2: e-mail em arquivo binário -> 3 (binário lido como texto)" 3 $?
rm "$TMP/entrada/lote.bin"
rodar --manifesto "$MAN" --entrada-dir "$TMP/entrada" --preflight-only >/dev/null 2>&1; espera "preflight-only limpo -> 0" 0 $?
REG="$(ultimo_registro)"; grep -q "NÃO EXECUTADAS" "$REG" && ok "registro parcial declara etapas não executadas" || bad "registro parcial declara etapas não executadas"

echo "== cargas sem fiação =="
rodar --manifesto "$MAN" --entrada-dir "$TMP/entrada" --totais-carregados "$TMP/totais-ok.json" >/dev/null 2>&1
espera "sem comando do importador fiado -> 4 (pendência declarada)" 4 $?

echo "== caminho feliz com hooks stub + ordem =="
export ZIIPSAN_GL1_CMD_CADASTRO="echo cadastro >> $TMP/ordem/etapas.txt"
export ZIIPSAN_GL1_CMD_HISTORICO="echo historico >> $TMP/ordem/etapas.txt"
export ZIIPSAN_GL1_CMD_XML="echo xml >> $TMP/ordem/etapas.txt"
export ZIIPSAN_GL1_CMD_GIS="echo gis >> $TMP/ordem/etapas.txt"
export ZIIPSAN_GL1_CMD_VARREDURA_POS="true"
rodar --manifesto "$MAN" --entrada-dir "$TMP/entrada" --totais-carregados "$TMP/totais-ok.json" >/dev/null 2>&1
espera "caminho feliz -> 0" 0 $?
[ "$(cat "$TMP/ordem/etapas.txt")" = "$(printf 'cadastro\nhistorico\nxml\ngis')" ] && ok "ordem das cargas: P1.2 -> P2.10 -> P2.19 -> P3.26" || bad "ordem das cargas"
REG="$(ultimo_registro)"; grep -q "CONCLUIDO" "$REG" && ok "registro CONCLUIDO gravado" || bad "registro CONCLUIDO gravado"
grep -q "Conciliação fina" "$REG" && grep -q "INCOMPLETO" "$REG" && ok "D4: registro amarra a conciliação fina dos importadores como evidência" || bad "D4: registro amarra a conciliação fina dos importadores como evidência"

echo "== conciliação divergente =="
rodar --manifesto "$MAN" --entrada-dir "$TMP/entrada" --totais-carregados "$TMP/totais-div.json" >/dev/null 2>&1
espera "totais divergentes -> 5" 5 $?

echo "== varredura pós-carga =="
export ZIIPSAN_GL1_CMD_VARREDURA_POS="false"
rodar --manifesto "$MAN" --entrada-dir "$TMP/entrada" --totais-carregados "$TMP/totais-ok.json" >/dev/null 2>&1
espera "varredura pós-carga reprovada -> 6" 6 $?
unset ZIIPSAN_GL1_CMD_VARREDURA_POS
rodar --manifesto "$MAN" --entrada-dir "$TMP/entrada" --totais-carregados "$TMP/totais-ok.json" >/dev/null 2>&1
espera "varredura pós-carga não fiada -> 6" 6 $?

echo "== importador com erro =="
export ZIIPSAN_GL1_CMD_CADASTRO="false"
export ZIIPSAN_GL1_CMD_VARREDURA_POS="true"
rodar --manifesto "$MAN" --entrada-dir "$TMP/entrada" --totais-carregados "$TMP/totais-ok.json" >/dev/null 2>&1
espera "importador sai != 0 -> 4" 4 $?

echo
echo "RESULTADO: $PASS ok, $FAIL falhas"
[ "$FAIL" = "0" ]
