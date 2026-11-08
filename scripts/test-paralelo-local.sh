#!/bin/bash
# Harness da GL.2 — roda SEM Docker/Postgres/JDK, com stubs (padrao GL.1/GL.3):
# monta um repo falso minimo (compose stub com fiscal=false literal, .env,
# registro da GL.1 CONCLUIDO), configs/massas 100% sinteticas e hooks stub
# que registram a ordem das chamadas. Prova o comportamento REAL do
# validador, do comparador conta a conta e do orquestrador do paralelo
# (pre-voo fail-closed, ordem das etapas, saidas distintas por etapa,
# limpeza fiscal da P3.7 e campos do registro). O paralelo real, com o
# motor de faturamento e a stack completa, roda no PC do dono
# (docs/paralelo/ROTEIRO-PARALELO-LOCAL-GL2.md).
#
#   scripts/test-paralelo-local.sh
#
# Sai 0 se todos os casos passam; 1 se algum falha. Nenhum caso afrouxa
# assercao: cada um compara codigo de saida E artefato (registro/relatorio).
set -uo pipefail

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORQ="$AQUI/paralelo/paralelo-local.sh"
VAL="$AQUI/paralelo/validar-config-paralelo.py"
CMP_PY="$AQUI/paralelo/comparar-contas.py"
PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); echo "ok   - $1"; }
bad()  { FAIL=$((FAIL+1)); echo "FALHOU - $1"; }
espera() { if [ "$2" = "$3" ]; then ok "$1 (saida $3)"; else bad "$1 (esperava $2, veio $3)"; fi; }
confere() { local desc="$1"; shift; if "$@" >/dev/null 2>&1; then ok "$desc"; else bad "$desc"; fi; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
SRC_FALSO="$TMP/repo"
mkdir -p "$SRC_FALSO/docs/homologacao/cargas-homologacao/teste-gl1" "$TMP/piloto" "$TMP/bin" "$TMP/ordem"
export STUB_LOG="$TMP/ordem/stub.log"; : > "$STUB_LOG"

escrever_compose() { # $1=valor fiscal literal, ou AUSENTE
  if [ "$1" = "AUSENTE" ]; then
    printf 'services:\n  api:\n    environment:\n      ZIIPSAN_PORT: 8090\n' > "$SRC_FALSO/docker-compose.yml"
  else
    printf 'services:\n  api:\n    environment:\n      ZIIPSAN_FISCAL_PRODUCTION_ALLOWED: "%s"\n  migrate:\n    environment:\n      ZIIPSAN_FISCAL_PRODUCTION_ALLOWED: "%s"\n' "$1" "$1" > "$SRC_FALSO/docker-compose.yml"
  fi
}
escrever_compose false
printf 'POSTGRES_SUPERUSER_PASSWORD=stub\n' > "$SRC_FALSO/.env"
printf '# Registro de carga — GL.1\n\n- Resultado: **CONCLUIDO**\n' > "$SRC_FALSO/docs/homologacao/cargas-homologacao/teste-gl1/registro-carga.md"

config_ok() { # $1=arquivo — massa 100% sintetica, JSON valido
  python3 - "$1" <<'PY'
import json, sys
cfg = {
  "tenantId": "11111111-1111-1111-1111-111111111111",
  "ciclos": 2,
  "dataPrimeiroCiclo": "2027-03-10",
  "tpAmb": 2,
  "ambienteFiscal": "HOMOLOGACAO_SEFAZ",
  "perfilRegulatorio": {"agencia": "AGENCIA-SINTETICA", "perfilId": "PERFIL-SINTETICO-1",
                        "ativo": True, "vigenciaInicio": "2027-01-01", "vigenciaFim": "2027-12-31",
                        "conferidoEm": "2027-03-01"},
  "criterioDivergencia": {"fonte": "P0.30", "toleranciaCentavosPorConta": 10,
                          "toleranciaCentavosTotalCiclo": 100, "toleranciaAusencias": 0},
  "conteudoMinimo": {"fonte": "P2.32", "perfilConteudo": "CONTEUDO-SINTETICO-1"},
  "operacao": {"unidadeOrganizacional": "UNIDADE-SINTETICA", "escopoArea": "AREA-SINTETICA"},
  "cargaGl1": {"registroCarga": "docs/homologacao/cargas-homologacao/teste-gl1/registro-carga.md"},
}
json.dump(cfg, open(sys.argv[1], "w"), ensure_ascii=False, indent=1)
PY
}
CFG="$TMP/config.json"; config_ok "$CFG"

mutar_config() { # $1=origem $2=destino $3=expressao python sobre cfg
  python3 - "$1" "$2" "$3" <<'PY'
import json, sys
cfg = json.load(open(sys.argv[1]))
exec(sys.argv[3])
json.dump(cfg, open(sys.argv[2], "w"), ensure_ascii=False, indent=1)
PY
}

contas_piloto() { # $1=arquivo
  python3 - "$1" <<'PY'
import json, sys
doc = {"contas": [
  {"codigoSubstituto": "SUB-001", "totalCentavos": 10000, "consumoM3": 10.0, "vencimento": "2027-03-20"},
  {"codigoSubstituto": "SUB-002", "totalCentavos": 23456, "consumoM3": 21.5, "vencimento": "2027-03-20"},
]}
json.dump(doc, open(sys.argv[1], "w"), ensure_ascii=False, indent=1)
PY
}
for n in 1 2 3; do mkdir -p "$TMP/piloto/ciclo-$n"; contas_piloto "$TMP/piloto/ciclo-$n/contas-piloto.json"; done

# ------------------------------------------------------------- hooks stub
cat > "$TMP/bin/faturamento-stub.sh" <<'STUB'
#!/bin/bash
echo "ciclo${GL2_CICLO}:faturamento" >> "$STUB_LOG"
python3 - "$GL2_SAIDA_CONTAS" <<'PY'
import json, os, sys
c = os.environ.get("STUB_CENARIO", "igual")
contas = [
  {"codigoSubstituto": "SUB-001", "totalCentavos": 10000, "consumoM3": 10.0, "vencimento": "2027-03-20"},
  {"codigoSubstituto": "SUB-002", "totalCentavos": 23456, "consumoM3": 21.5, "vencimento": "2027-03-20"},
]
if c == "div-explicada": contas[1]["totalCentavos"] = 23450
if c == "div-alem": contas[1]["totalCentavos"] = 18456
if c == "consumo-div": contas[1]["consumoM3"] = 22.0
json.dump({"contas": contas}, open(sys.argv[1], "w"), ensure_ascii=False, indent=1)
PY
[ "${STUB_XML:-0}" = "1" ] && printf '<nfe>xml sintetico de teste</nfe>\n' > "$GL2_FISCAL_DIR/doc-ciclo-${GL2_CICLO}.xml"
[ "${STUB_STRAY:-0}" = "1" ] && printf '<nfe>xml fora da area</nfe>\n' > "$(dirname "$GL2_SAIDA_CONTAS")/solto.xml"
[ "${STUB_STRAY_UPPER:-0}" = "1" ] && printf '<nfe>xml fora da area, extensao maiuscula</nfe>\n' > "$(dirname "$GL2_SAIDA_CONTAS")/SOLTO.XML"
exit "${STUB_FAT_RC:-0}"
STUB
cat > "$TMP/bin/simples-stub.sh" <<'STUB'
#!/bin/bash
echo "ciclo${GL2_CICLO}:${STUB_NOME:-hook}" >> "$STUB_LOG"
exit "${STUB_RC:-0}"
STUB
chmod +x "$TMP/bin/"*.sh

hooks_base() {
  export ZIIPSAN_GL2_CMD_FATURAMENTO="bash $TMP/bin/faturamento-stub.sh"
  export ZIIPSAN_GL2_CMD_CONTEUDO="STUB_NOME=conteudo bash $TMP/bin/simples-stub.sh"
  export ZIIPSAN_GL2_CMD_RETIFICACAO="STUB_NOME=retificacao bash $TMP/bin/simples-stub.sh"
  export ZIIPSAN_GL2_CMD_RELATORIOS="STUB_NOME=relatorios bash $TMP/bin/simples-stub.sh"
  unset ZIIPSAN_GL2_CMD_DEVOLUCOES ZIIPSAN_GL2_CMD_ESCOPO || true
}
# rodar como SUBSHELL: prefixos STUB_* da chamada valem dentro dela e NAO
# vazam para os casos seguintes (prefixo em funcao comum persiste no bash).
rodar() ( ZIIPSAN_GL2_SRC="$SRC_FALSO" bash "$ORQ" "$@" )
ultimo_registro() { ls -t "$SRC_FALSO"/docs/paralelo/paralelos/*/registro-paralelo.md 2>/dev/null | head -1; }

echo "== validador da config =="
python3 "$VAL" "$CFG" >/dev/null 2>&1; espera "config valida aceita" 0 $?
mutar_config "$CFG" "$TMP/cfg-ciclos.json" "cfg['ciclos']=4"
python3 "$VAL" "$TMP/cfg-ciclos.json" >/dev/null 2>&1; espera "ciclos=4 recusado (acceptance: 2 ou 3)" 1 $?
mutar_config "$CFG" "$TMP/cfg-ciclos-float.json" "cfg['ciclos']=2.0"
python3 "$VAL" "$TMP/cfg-ciclos-float.json" >/dev/null 2>&1; espera "ciclos=2.0 (float) recusado — correcao D1 (2.0 in (2,3) era True)" 1 $?
mutar_config "$CFG" "$TMP/cfg-ciclos-str.json" "cfg['ciclos']='2'"
python3 "$VAL" "$TMP/cfg-ciclos-str.json" >/dev/null 2>&1; espera "ciclos='2' (string) recusado — correcao D1 (tipo inteiro exigido)" 1 $?
mutar_config "$CFG" "$TMP/cfg-tpamb.json" "cfg['tpAmb']=1"
python3 "$VAL" "$TMP/cfg-tpamb.json" >/dev/null 2>&1; espera "tpAmb=1 (producao) recusado" 1 $?
mutar_config "$CFG" "$TMP/cfg-tpamb-float.json" "cfg['tpAmb']=2.0"
python3 "$VAL" "$TMP/cfg-tpamb-float.json" >/dev/null 2>&1; espera "tpAmb=2.0 (float) recusado — correcao D1 (tipo inteiro exigido)" 1 $?
mutar_config "$CFG" "$TMP/cfg-tpamb-str.json" "cfg['tpAmb']='2'"
python3 "$VAL" "$TMP/cfg-tpamb-str.json" >/dev/null 2>&1; espera "tpAmb='2' (string) recusado — correcao D1 (tipo inteiro exigido)" 1 $?
mutar_config "$CFG" "$TMP/cfg-inativo.json" "cfg['perfilRegulatorio']['ativo']=False"
python3 "$VAL" "$TMP/cfg-inativo.json" >/dev/null 2>&1; espera "perfil P2.31 inativo recusado" 1 $?
mutar_config "$CFG" "$TMP/cfg-vigencia.json" "cfg['perfilRegulatorio']['vigenciaFim']='2027-02-01'"
python3 "$VAL" "$TMP/cfg-vigencia.json" >/dev/null 2>&1; espera "perfil fora de vigencia no 1o ciclo recusado" 1 $?
mutar_config "$CFG" "$TMP/cfg-conferido.json" "cfg['perfilRegulatorio']['conferidoEm']='2027-03-11'"
python3 "$VAL" "$TMP/cfg-conferido.json" >/dev/null 2>&1; espera "vigencia conferida DEPOIS do 1o ciclo recusada" 1 $?
mutar_config "$CFG" "$TMP/cfg-conferido-mesmo-dia.json" "cfg['perfilRegulatorio']['conferidoEm']='2027-03-10'"
python3 "$VAL" "$TMP/cfg-conferido-mesmo-dia.json" >/dev/null 2>&1; espera "vigencia conferida NO DIA do 1o ciclo recusada — correcao R1 (conferidoEm < dataPrimeiroCiclo, estrito)" 1 $?
mutar_config "$CFG" "$TMP/cfg-fonte.json" "cfg['criterioDivergencia']['fonte']='INTERNO'"
python3 "$VAL" "$TMP/cfg-fonte.json" >/dev/null 2>&1; espera "criterio sem fonte P0.30 recusado (criterio nao se inventa)" 1 $?
mutar_config "$CFG" "$TMP/cfg-tenant.json" "cfg['tenantId']=12345"
python3 "$VAL" "$TMP/cfg-tenant.json" >/dev/null 2>&1; espera "tenantId numerico recusado" 1 $?
mutar_config "$CFG" "$TMP/cfg-escopo.json" "cfg['operacao']['escopoArea']=''"
python3 "$VAL" "$TMP/cfg-escopo.json" >/dev/null 2>&1; espera "escopo por area vazio (P3.30) recusado" 1 $?

echo "== comparador conta a conta =="
CRIT="$TMP/criterio.json"
python3 -c "import json; json.dump({'toleranciaCentavosPorConta':10,'toleranciaCentavosTotalCiclo':100,'toleranciaAusencias':0}, open('$CRIT','w'))"
PIL="$TMP/piloto/ciclo-1/contas-piloto.json"
mkzip() { STUB_CENARIO="$1" GL2_SAIDA_CONTAS="$TMP/contas-z.json" GL2_CICLO=1 GL2_FISCAL_DIR="$TMP" bash "$TMP/bin/faturamento-stub.sh" >/dev/null 2>&1; }
printf '{}\n' > "$TMP/expl-vazia.json"
printf '{"SUB-002": "motivo sintetico de teste"}\n' > "$TMP/expl.json"
mkzip igual
python3 "$CMP_PY" --ziipsan "$TMP/contas-z.json" --piloto "$PIL" --criterio "$CRIT" --saida "$TMP/rel.json" >/dev/null 2>&1; espera "contas iguais aprovadas" 0 $?
mkzip div-explicada
python3 "$CMP_PY" --ziipsan "$TMP/contas-z.json" --piloto "$PIL" --criterio "$CRIT" --explicacoes "$TMP/expl.json" --saida "$TMP/rel.json" >/dev/null 2>&1; espera "divergencia dentro do criterio E explicada aprovada" 0 $?
python3 "$CMP_PY" --ziipsan "$TMP/contas-z.json" --piloto "$PIL" --criterio "$CRIT" --explicacoes "$TMP/expl-vazia.json" --saida "$TMP/rel.json" >/dev/null 2>&1; espera "divergencia dentro do criterio SEM explicacao reprovada" 2 $?
mkzip div-alem
python3 "$CMP_PY" --ziipsan "$TMP/contas-z.json" --piloto "$PIL" --criterio "$CRIT" --explicacoes "$TMP/expl.json" --saida "$TMP/rel.json" >/dev/null 2>&1; espera "divergencia alem do criterio reprovada mesmo explicada" 1 $?
mkzip consumo-div
python3 "$CMP_PY" --ziipsan "$TMP/contas-z.json" --piloto "$PIL" --criterio "$CRIT" --explicacoes "$TMP/expl.json" --saida "$TMP/rel.json" >/dev/null 2>&1; espera "divergencia de consumo explicada registrada sem reprovar o criterio monetario" 0 $?
python3 "$CMP_PY" --ziipsan "$TMP/contas-z.json" --piloto "$PIL" --criterio "$CRIT" --saida "$TMP/rel.json" >/dev/null 2>&1; espera "divergencia de consumo SEM explicacao reprovada" 2 $?
python3 - "$TMP/contas-z.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1])); d["contas"] = d["contas"][:1]
json.dump(d, open(sys.argv[1], "w"))
PY
python3 "$CMP_PY" --ziipsan "$TMP/contas-z.json" --piloto "$PIL" --criterio "$CRIT" --explicacoes "$TMP/expl.json" --saida "$TMP/rel.json" >/dev/null 2>&1; espera "conta ausente de um lado reprovada (ausencias toleradas = 0)" 1 $?
mkzip igual
python3 - "$TMP/contas-z.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1])); d["contas"].append(dict(d["contas"][0]))
json.dump(d, open(sys.argv[1], "w"))
PY
python3 "$CMP_PY" --ziipsan "$TMP/contas-z.json" --piloto "$PIL" --criterio "$CRIT" --saida "$TMP/rel.json" >/dev/null 2>&1; espera "codigo substituto duplicado = entrada invalida" 3 $?

echo "== orquestrador: dry-run e pre-voo =="
rodar --config "$CFG" --piloto-dir "$TMP/piloto" --dry-run >/dev/null 2>&1; espera "dry-run sai 0" 0 $?
confere "dry-run nao grava registro" bash -c "[ ! -d '$SRC_FALSO/docs/paralelo' ]"
rodar --piloto-dir "$TMP/piloto" >/dev/null 2>&1; espera "sem --config reprova no pre-voo" 2 $?
confere "falha de argumento grava registro de FALHA (linha de resultado ancorada)" bash -c "grep -qE '^- Resultado: \*\*FALHA\*\*' '$(ultimo_registro)'"
rodar --config "$CFG" --piloto-dir "$TMP/piloto" --opcao-inventada >/dev/null 2>&1; espera "opcao desconhecida reprova no pre-voo" 2 $?
escrever_compose true
hooks_base; STUB_CENARIO=igual rodar --config "$CFG" --piloto-dir "$TMP/piloto" >/dev/null 2>&1; espera "fiscal de producao ligado no compose reprova" 2 $?
escrever_compose AUSENTE
hooks_base; STUB_CENARIO=igual rodar --config "$CFG" --piloto-dir "$TMP/piloto" >/dev/null 2>&1; espera "fiscal indeterminado no compose reprova (fail-closed)" 2 $?
escrever_compose false
printf '# Registro de carga — GL.1\n\n- Resultado: **PARCIAL**\n' > "$SRC_FALSO/docs/homologacao/cargas-homologacao/teste-gl1/registro-carga.md"
hooks_base; STUB_CENARIO=igual rodar --config "$CFG" --piloto-dir "$TMP/piloto" >/dev/null 2>&1; espera "GL.1 nao CONCLUIDO reprova o pre-voo" 2 $?
printf '# Registro de carga — GL.1\n\n- Resultado: **FALHA**\n\n- Nota: o ensaio anterior havia sido CONCLUIDO, esta carga nao.\n' > "$SRC_FALSO/docs/homologacao/cargas-homologacao/teste-gl1/registro-carga.md"
hooks_base; STUB_CENARIO=igual rodar --config "$CFG" --piloto-dir "$TMP/piloto" --preflight-only >/dev/null 2>&1; espera "GL.1 FALHA mencionando CONCLUIDO no corpo reprova o pre-voo — correcao D2 (ancora na linha de resultado)" 2 $?
printf '# Registro de carga — GL.1\n\n- Resultado: **CONCLUIDO**\n' > "$SRC_FALSO/docs/homologacao/cargas-homologacao/teste-gl1/registro-carga.md"
hooks_base; STUB_CENARIO=igual rodar --config "$CFG" --piloto-dir "$TMP/piloto" --preflight-only >/dev/null 2>&1; espera "preflight-only limpo sai 0" 0 $?
confere "preflight-only grava registro PARCIAL (linha de resultado ancorada)" bash -c "grep -qE '^- Resultado: \*\*PARCIAL' '$(ultimo_registro)'"
rm -f "$TMP/piloto/ciclo-2/contas-piloto.json"
hooks_base; STUB_CENARIO=igual rodar --config "$CFG" --piloto-dir "$TMP/piloto" >/dev/null 2>&1; espera "contas do piloto do ciclo 2 ausentes reprovam o pre-voo" 2 $?
contas_piloto "$TMP/piloto/ciclo-2/contas-piloto.json"
hooks_base; STUB_CENARIO=igual rodar --config "$TMP/cfg-ciclos-float.json" --piloto-dir "$TMP/piloto" >/dev/null 2>&1; espera "orquestrador com ciclos=2.0 reprova no pre-voo (nunca CONCLUIDO sem ciclo) — correcao D1" 2 $?
REG_D1="$(ultimo_registro)"
confere "registro da execucao ciclos=2.0 e FALHA com '(nenhum ciclo concluido)' e NUNCA CONCLUIDO — correcao D1(c)" bash -c "grep -qE '^- Resultado: \*\*FALHA\*\*' '$REG_D1' && grep -q '(nenhum ciclo concluido)' '$REG_D1' && ! grep -qE '^- Resultado: \*\*CONCLUIDO\*\*' '$REG_D1'"

echo "== orquestrador: caminho feliz (2 ciclos, sem divergencia, com XML fiscal) =="
: > "$STUB_LOG"
hooks_base; STUB_CENARIO=igual STUB_XML=1 rodar --config "$CFG" --piloto-dir "$TMP/piloto" >/dev/null 2>&1; espera "caminho feliz conclui" 0 $?
REG="$(ultimo_registro)"
confere "registro CONCLUIDO gravado (linha de resultado ancorada)" bash -c "grep -qE '^- Resultado: \*\*CONCLUIDO\*\*' '$REG'"
confere "registro resume os 2 ciclos" bash -c "grep -q 'ciclo 1:' '$REG' && grep -q 'ciclo 2:' '$REG'"
confere "ordem faturamento antes de conteudo no ciclo 1" bash -c "awk '/ciclo1:faturamento/{f=NR} /ciclo1:conteudo/{c=NR} END{exit !(f && c && f<c)}' '$STUB_LOG'"
REGDIR="$(dirname "$REG")"
confere "XML assinados apagados ao fim de cada ciclo (busca -iname, correcao R2)" bash -c "[ -z \"\$(find '$REGDIR' -iname '*.xml')\" ]"
confere "limpeza fiscal registrada com quantidade 1 por ciclo" bash -c "grep -q '\"quantidadeApagada\": 1' '$REGDIR/ciclo-1/limpeza-fiscal.json' && grep -q '\"quantidadeApagada\": 1' '$REGDIR/ciclo-2/limpeza-fiscal.json'"
confere "relatorio de comparacao gravado por ciclo" bash -c "[ -f '$REGDIR/ciclo-1/relatorio-comparacao.json' ] && [ -f '$REGDIR/ciclo-2/relatorio-comparacao.json' ]"

echo "== orquestrador: divergencias =="
printf '{"SUB-002": "motivo sintetico de teste"}\n' > "$TMP/piloto/ciclo-1/explicacoes.json"
printf '{"SUB-002": "motivo sintetico de teste"}\n' > "$TMP/piloto/ciclo-2/explicacoes.json"
: > "$STUB_LOG"
hooks_base; STUB_CENARIO=div-explicada rodar --config "$CFG" --piloto-dir "$TMP/piloto" >/dev/null 2>&1; espera "divergencia explicada dentro do criterio conclui com retificacao+relatorios" 0 $?
confere "retificacao (P2.20) chamada depois da comparacao" bash -c "grep -q 'ciclo1:retificacao' '$STUB_LOG' && grep -q 'ciclo1:relatorios' '$STUB_LOG'"
export ZIIPSAN_GL2_CMD_RETIFICACAO=""
STUB_CENARIO=div-explicada rodar --config "$CFG" --piloto-dir "$TMP/piloto" >/dev/null 2>&1; espera "divergencia explicada SEM hook de retificacao reprova" 7 $?
hooks_base; STUB_CENARIO=div-alem rodar --config "$CFG" --piloto-dir "$TMP/piloto" >/dev/null 2>&1; espera "divergencia fora do criterio reprova o ciclo" 5 $?
confere "reprovacao por divergencia grava registro de FALHA (linha ancorada) com o relatorio ao lado" bash -c "grep -qE '^- Resultado: \*\*FALHA\*\*' '$(ultimo_registro)' && [ -f \"\$(dirname '$(ultimo_registro)')/ciclo-1/relatorio-comparacao.json\" ]"
rm -f "$TMP/piloto/ciclo-1/explicacoes.json" "$TMP/piloto/ciclo-2/explicacoes.json"
hooks_base; STUB_CENARIO=div-explicada rodar --config "$CFG" --piloto-dir "$TMP/piloto" >/dev/null 2>&1; espera "divergencia sem explicacao reprova o ciclo" 5 $?

echo "== orquestrador: hooks ausentes/falhos e fiscal =="
hooks_base; export ZIIPSAN_GL2_CMD_FATURAMENTO=""
STUB_CENARIO=igual rodar --config "$CFG" --piloto-dir "$TMP/piloto" >/dev/null 2>&1; espera "sem hook de faturamento reprova" 4 $?
hooks_base; export ZIIPSAN_GL2_CMD_CONTEUDO=""
STUB_CENARIO=igual rodar --config "$CFG" --piloto-dir "$TMP/piloto" >/dev/null 2>&1; espera "sem hook de conteudo (P2.32) reprova" 3 $?
hooks_base; STUB_CENARIO=igual STUB_STRAY=1 rodar --config "$CFG" --piloto-dir "$TMP/piloto" >/dev/null 2>&1; espera "XML fora da area separada reprova o fechamento fiscal" 6 $?
hooks_base; STUB_CENARIO=igual STUB_STRAY_UPPER=1 rodar --config "$CFG" --piloto-dir "$TMP/piloto" >/dev/null 2>&1; espera "XML maiusculo (.XML) fora da area separada reprova o fechamento fiscal — correcao R2 (-iname)" 6 $?
printf '{"devolucoes": 1, "pagamentosNaoIdentificados": 0}\n' > "$TMP/piloto/ciclo-1/ocorrencias.json"
hooks_base; STUB_CENARIO=igual rodar --config "$CFG" --piloto-dir "$TMP/piloto" >/dev/null 2>&1; espera "ocorrencia declarada SEM hook da P3.29 reprova" 7 $?
export ZIIPSAN_GL2_CMD_DEVOLUCOES="STUB_NOME=devolucoes bash $TMP/bin/simples-stub.sh"
: > "$STUB_LOG"
STUB_CENARIO=igual rodar --config "$CFG" --piloto-dir "$TMP/piloto" >/dev/null 2>&1; espera "ocorrencia com hook da P3.29 fiado conclui" 0 $?
confere "hook da P3.29 chamado no ciclo da ocorrencia" grep -q "ciclo1:devolucoes" "$STUB_LOG"
rm -f "$TMP/piloto/ciclo-1/ocorrencias.json"

echo
echo "HARNESS GL.2: $PASS ok, $FAIL falhas"
[ "$FAIL" = "0" ]
