#!/bin/bash
# GL.2: orquestrador do paralelo local -- dois ou tres ciclos de faturamento
# comparados conta a conta com o sistema do piloto, pelo codigo substituto.
#
#   scripts/paralelo/paralelo-local.sh \
#     --config <config-paralelo.json> \
#     --piloto-dir <dir com ciclo-1/, ciclo-2/[, ciclo-3/]> \
#     [--dry-run] [--preflight-only]
#
# Diretorio do piloto (por ciclo N): ciclo-N/contas-piloto.json (obrigatorio),
# ciclo-N/explicacoes.json (opcional), ciclo-N/ocorrencias.json (opcional:
# {"devolucoes": n, "pagamentosNaoIdentificados": n}).
#
# O que este orquestrador FAZ (e o que ele NAO faz):
#   (a) pre-voo (fail-closed): config valida (validar-config-paralelo.py:
#       2-3 ciclos INTEIROS -- correcao D1 da revisao: tipo int exigido,
#       2.0/"2" recusados; e defesa em profundidade AQUI: o NCICLOS
#       extraido so aceita o literal 2|3, qualquer outro valor reprova
#       com saida 2 ANTES de qualquer etapa; tpAmb=2/simulador, perfil
#       P2.31 ativo e com vigencia conferida ESTRITAMENTE antes do 1o
#       ciclo (correcao R1: conferidoEm < dataPrimeiroCiclo), criterio
#       de divergencia com fonte P0.30, conteudo P2.32, escopo P3.30);
#       registro da carga da GL.1 com resultado CONCLUIDO conferido na
#       LINHA de resultado (correcao D2 da revisao: ancora em
#       '- Resultado: **CONCLUIDO**', o formato que o orquestrador da
#       GL.1 grava; a palavra CONCLUIDO solta no corpo NAO passa, e
#       resultado FALHA/PARCIAL na linha e recusado explicitamente);
#       fiscal de producao DESLIGADO na fonte que controla a
#       variavel (docker-compose.yml: toda ocorrencia de
#       ZIIPSAN_FISCAL_PRODUCTION_ALLOWED literalmente false; ausente ou
#       interpolada = nao determinado = recusa -- mesmo portao da GL.1);
#       arquivos do piloto de todos os ciclos presentes;
#   (b) por ciclo, NA ORDEM:
#       1. faturamento pelo hook ZIIPSAN_GL2_CMD_FATURAMENTO (o motor de
#          faturamento do produto, P2.5; sem CLI neste repo -- como os
#          importadores da GL.1, a fiacao e por variavel de ambiente e a
#          ausencia REPROVA, nao finge). O hook recebe GL2_CICLO,
#          GL2_SAIDA_CONTAS (onde gravar contas-ziipsan.json),
#          GL2_FISCAL_DIR (area separada dos XML do ciclo, P3.7),
#          GL2_ORG_UNIDADE e GL2_ESCOPO_AREA (operacao P3.30);
#       2. conferencia do conteudo minimo da fatura (P2.32) pelo hook
#          ZIIPSAN_GL2_CMD_CONTEUDO sobre as contas do ciclo -- ausencia
#          reprova (saida 3): "conferido em cada conta" e criterio;
#       3. escopo (P3.30): se ZIIPSAN_GL2_CMD_ESCOPO estiver fiado, ele tem
#          de sair 0 (a presenca do escopo na config e portao do pre-voo);
#       4. comparacao conta a conta (comparar-contas.py) contra
#          ciclo-N/contas-piloto.json, com o criterio da config (fonte
#          P0.30) e as explicacoes do ciclo. Fora do criterio ou com
#          divergencia sem explicacao: o relatorio fica gravado, o hook de
#          relatorios (P3.22) e chamado como evidencia quando fiado, e o
#          paralelo REPROVA o ciclo (saida 5) -- divergencia nao se
#          "ajusta" para passar. Com divergencias DENTRO do criterio e
#          todas explicadas, o ciclo so fecha depois da retificacao da
#          P2.20 (ZIIPSAN_GL2_CMD_RETIFICACAO) e da conferencia pelos
#          relatorios da P3.22 (ZIIPSAN_GL2_CMD_RELATORIOS): hook ausente
#          ou falho reprova (saida 7). Sem divergencia, os dois hooks nao
#          sao exigidos naquele ciclo;
#       5. devolucoes e pagamentos nao identificados (P3.29): se o ciclo
#          declara ocorrencias (> 0 em ciclo-N/ocorrencias.json), o hook
#          ZIIPSAN_GL2_CMD_DEVOLUCOES e obrigatorio (ausente/falho: saida
#          7); se o hook estiver fiado, ele roda em todo ciclo;
#       6. fechamento fiscal do ciclo (P3.7): nenhum XML pode existir FORA
#          da area separada do ciclo (GL2_FISCAL_DIR) -- achou, reprova
#          (saida 6). ESCOPO DA VARREDURA (correcao R2 da revisao): toda
#          a arvore do ciclo no diretorio do registro
#          ($REG_DIR/ciclo-N/, que e onde o hook de faturamento grava as
#          contas e artefatos do ciclo), excluida a subarvore da area
#          separada (ciclo-N/fiscal/); a busca e case-insensitive
#          (-iname '*.xml': cobre .xml, .XML e mistos). Os XML assinados
#          da area sao APAGADOS ao fim do
#          ciclo, com o registro da exclusao gravado ao lado do registro
#          do paralelo (ciclo, quantidade e hash do lote -- o hash e dos
#          conteudos, calculado ANTES de apagar; nenhum XML e copiado
#          para o registro);
#   (c) REGISTRO do ensaio de POC em
#       docs/paralelo/paralelos/<carimbo>-gl2/registro-paralelo.md, com os
#       valores MEDIDOS por esta execucao (relatorios do comparador e
#       registros de limpeza). O registro NUNCA afirma ciclo que nao rodou.
#       Correcao D1(c): CONCLUIDO exige >= 2 ciclos EFETIVAMENTE
#       comparados e concluidos (contador CICLOS_CONCLUIDOS == NCICLOS
#       e >= 2); o fechamento e a propria gravacao do registro recusam
#       CONCLUIDO com zero ou menos ciclos do que o configurado.
#
# Modos: --dry-run imprime o plano e sai 0 sem executar nem gravar nada;
# --preflight-only executa (a) e grava registro PARCIAL.
#
# Codigos de saida (distintos por etapa; a primeira falha para tudo):
#   0  concluido (ou pre-voo OK em --preflight-only)
#   2  pre-voo: parametro/config/GL.1/fiscal/piloto invalidos; defesa
#      D1 (NCICLOS nao literal 2|3) e fechamento D1(c) sem >= 2 ciclos
#      efetivamente concluidos tambem saem por aqui
#   3  conferencia do conteudo minimo (P2.32): hook ausente ou falho
#   4  faturamento: hook ausente, falho ou sem contas geradas
#   5  comparacao: fora do criterio P0.30, divergencia sem explicacao ou
#      entrada da comparacao invalida
#   6  fechamento fiscal (P3.7): XML fora da area separada ou limpeza falhou
#   7  pos-divergencia/ocorrencias: retificacao (P2.20), relatorios
#      (P3.22), devolucoes (P3.29) ou escopo (P3.30) ausente/falho quando
#      exigido
#
# Sem `set -e`: toda falha precisa gravar o registro antes de sair.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="${ZIIPSAN_GL2_SRC:-$(cd "$SCRIPT_DIR/../.." && pwd)}"
VALIDADOR="$SCRIPT_DIR/validar-config-paralelo.py"
COMPARADOR="$SCRIPT_DIR/comparar-contas.py"

CONFIG=""; PILOTO_DIR=""; DRY_RUN=0; PREFLIGHT_ONLY=0
ETAPA="pre-voo"; STATUS="EM_EXECUCAO"; CAUSA=""
declare -a ETAPAS_LOG=()
declare -a CICLOS_LOG=()
START_TS="$(date +%s)"
STAMP="$(date +%Y%m%d-%H%M%S)"
REG_DIR="$SRC/docs/paralelo/paralelos/${STAMP}-gl2"
REG_FILE="$REG_DIR/registro-paralelo.md"
NCICLOS=0
CICLOS_CONCLUIDOS=0

log_etapa() { ETAPAS_LOG+=("$1"); echo "$1"; }

# gravar_registro/falhar/trap ANTES da leitura dos argumentos (padrao da
# GL.1, correcao D5): erro de sintaxe de argumentos tambem grava registro.
gravar_registro() {
  [ "$DRY_RUN" = "1" ] && return 0
  # Correcao D1(c), defesa na gravacao: CONCLUIDO com menos de 2 ciclos
  # efetivamente concluidos NUNCA e gravado -- vira FALHA registrada.
  if [ "$STATUS" = "CONCLUIDO" ] && [ "$CICLOS_CONCLUIDOS" -lt 2 ]; then
    STATUS="FALHA"
    CAUSA="${CAUSA:-fechamento — CONCLUIDO recusado: apenas $CICLOS_CONCLUIDOS ciclo(s) concluido(s) (minimo 2 ciclos comparados)}"
    echo "FALHA GL.2 [fechamento]: CONCLUIDO recusado com $CICLOS_CONCLUIDOS ciclo(s) concluido(s)" >&2
  fi
  mkdir -p "$REG_DIR"
  local fim dur
  fim="$(date +%s)"; dur=$((fim - START_TS))
  {
    echo "# Registro do paralelo local — GL.2 (ensaio de POC)"
    echo
    echo "- Carimbo: $STAMP"
    echo "- Resultado: **$STATUS**"
    [ -n "$CAUSA" ] && echo "- Etapa da falha / causa: $CAUSA"
    echo "- Duracao total medida: ${dur}s"
    echo "- Config: ${CONFIG:-<ausente>}"
    echo "- Piloto (dir): ${PILOTO_DIR:-<ausente>}"
    echo
    echo "## Etapas (nesta execucao)"
    echo
    if [ "${#ETAPAS_LOG[@]}" -eq 0 ]; then echo "- (nenhuma etapa concluida)"; fi
    local linha; for linha in "${ETAPAS_LOG[@]}"; do echo "- $linha"; done
    echo
    echo "## Ciclos (resumo medido pelo comparador e pela limpeza fiscal)"
    echo
    if [ "${#CICLOS_LOG[@]}" -eq 0 ]; then echo "- (nenhum ciclo concluido)"; fi
    local c; for c in "${CICLOS_LOG[@]}"; do echo "- $c"; done
    echo
    echo "> Registro gerado pela execucao do orquestrador da GL.2 — e o"
    echo "> 'ensaio de POC registrado' do acceptance. Ele afirma somente o"
    echo "> que esta execucao mediu/executou; ciclo nao executado esta"
    echo "> marcado como tal. Nao reescrever a mao para \"melhorar\""
    echo "> resultado: divergencia e resultado do paralelo e fica registrada"
    echo "> (relatorio-comparacao-ciclo-N.json ao lado deste registro)."
  } > "$REG_FILE"
  echo "registro: $REG_FILE" >&2
}

falhar() { # $1=codigo $2=causa
  STATUS="FALHA"; CAUSA="$ETAPA — $2"
  echo "FALHA GL.2 [$ETAPA]: $2" >&2
  gravar_registro
  exit "$1"
}

trap 'STATUS="FALHA"; CAUSA="${CAUSA:-interrompido por SIGINT/SIGTERM na etapa $ETAPA}"; gravar_registro; exit 143' TERM
trap 'STATUS="FALHA"; CAUSA="${CAUSA:-interrompido por SIGINT/SIGTERM na etapa $ETAPA}"; gravar_registro; exit 130' INT

while [ $# -gt 0 ]; do
  case "$1" in
    --config) [ $# -ge 2 ] || falhar 2 "--config exige o caminho da config"; shift; CONFIG="$1" ;;
    --config=*) CONFIG="${1#--config=}" ;;
    --piloto-dir) [ $# -ge 2 ] || falhar 2 "--piloto-dir exige o diretorio do piloto"; shift; PILOTO_DIR="$1" ;;
    --piloto-dir=*) PILOTO_DIR="${1#--piloto-dir=}" ;;
    --dry-run) DRY_RUN=1 ;;
    --preflight-only) PREFLIGHT_ONLY=1 ;;
    -*) falhar 2 "opcao desconhecida: $1" ;;
    *) falhar 2 "argumento inesperado: $1" ;;
  esac
  shift
done

# ---------------------------------------------------------------- dry-run
if [ "$DRY_RUN" = "1" ]; then
  echo "PLANO GL.2 (dry-run — nada sera executado nem gravado):"
  echo "  1. pre-voo: config valida (P2.31 ativo/vigencia conferida, criterio P0.30, P2.32, P3.30), GL.1 CONCLUIDO, fiscal de producao desligado (compose=false literal)"
  echo "  2. por ciclo (2 ou 3): faturamento (hook) -> conteudo P2.32 (hook) -> escopo P3.30 (hook, se fiado) -> comparacao conta a conta (comparar-contas.py)"
  echo "  3. divergencia dentro do criterio e explicada: retificacao P2.20 + relatorios P3.22 (hooks obrigatorios); fora/sem explicacao: reprova (5) com relatorio gravado"
  echo "  4. ocorrencias do ciclo: devolucoes/pagamentos nao identificados pela P3.29 (hook obrigatorio se houver ocorrencia)"
  echo "  5. fechamento fiscal do ciclo (P3.7): XML so na area separada; assinados apagados com registro da exclusao (quantidade + hash do lote)"
  echo "  6. registro do ensaio de POC em docs/paralelo/paralelos/<carimbo>-gl2/registro-paralelo.md"
  exit 0
fi

# ---------------------------------------------------------------- (a) pre-voo
ETAPA="pre-voo"
[ -n "$CONFIG" ] && [ -f "$CONFIG" ] || falhar 2 "config ausente ou nao encontrada (--config)"
[ -n "$PILOTO_DIR" ] && [ -d "$PILOTO_DIR" ] || falhar 2 "diretorio do piloto ausente (--piloto-dir)"
[ -f "$VALIDADOR" ] || falhar 2 "validador da config nao encontrado: $VALIDADOR"
[ -f "$COMPARADOR" ] || falhar 2 "comparador nao encontrado: $COMPARADOR"
command -v python3 >/dev/null 2>&1 || falhar 2 "python3 nao encontrado (validador/comparador)"
if ! python3 "$VALIDADOR" "$CONFIG" >/dev/null; then
  python3 "$VALIDADOR" "$CONFIG" >&2 || true
  falhar 2 "config invalida (ver erros do validador acima)"
fi
# Valores da config de que o orquestrador precisa (a config ja foi validada).
CFG_JSON="$(python3 - "$CONFIG" <<'PYEOF'
import json, sys
cfg = json.load(open(sys.argv[1], encoding="utf-8"))
print(cfg["ciclos"])
print(cfg["cargaGl1"]["registroCarga"])
print(cfg["operacao"]["unidadeOrganizacional"])
print(cfg["operacao"]["escopoArea"])
PYEOF
)" || falhar 2 "config ilegivel ao extrair ciclos/GL.1/operacao"
NCICLOS="$(echo "$CFG_JSON" | sed -n '1p')"
# Correcao D1(b), defesa em profundidade: mesmo que o validador seja
# contornado/trocado, o orquestrador so aceita o LITERAL 2 ou 3. Qualquer
# outro valor (2.0, "2", 4, vazio) reprova com saida 2 AQUI, antes de
# qualquer etapa -- os lacos abaixo nunca sao pulados em silencio.
case "$NCICLOS" in
  2|3) ;;
  *) falhar 2 "ciclos extraido da config nao e o literal 2 ou 3: '$NCICLOS' — defesa D1: o orquestrador recusa qualquer outro valor antes de qualquer etapa" ;;
esac
REG_GL1="$(echo "$CFG_JSON" | sed -n '2p')"
ORG_UNIDADE="$(echo "$CFG_JSON" | sed -n '3p')"
ESCOPO_AREA="$(echo "$CFG_JSON" | sed -n '4p')"
case "$REG_GL1" in
  /*) REG_GL1_PATH="$REG_GL1" ;;
  *) REG_GL1_PATH="$SRC/$REG_GL1" ;;
esac
[ -f "$REG_GL1_PATH" ] || falhar 2 "registro da carga da GL.1 nao encontrado: $REG_GL1_PATH (o paralelo parte da carga CONCLUIDA da GL.1)"
# Correcao D2: a conferencia e ANCORADA na linha/campo de resultado do
# registro da GL.1, no formato exato que o orquestrador da GL.1 grava
# ("- Resultado: **CONCLUIDO**", ver gravar_registro da GL.1 e o modelo
# MODELO-REGISTRO-CARGA-GL1.md). A palavra CONCLUIDO solta no corpo NAO
# passa; resultado FALHA/PARCIAL na linha de resultado recusa explicito.
if grep -qE '^- Resultado: \*\*(FALHA|PARCIAL)' "$REG_GL1_PATH"; then
  falhar 2 "registro da GL.1 com resultado FALHA/PARCIAL na linha de resultado: $REG_GL1_PATH — paralelo nao comeca sem a carga da homologacao local concluida"
fi
grep -qE '^- Resultado: \*\*CONCLUIDO\*\*' "$REG_GL1_PATH" || falhar 2 "registro da GL.1 sem a linha de resultado '- Resultado: **CONCLUIDO**': $REG_GL1_PATH — a palavra CONCLUIDO fora da linha de resultado nao conta; paralelo nao comeca sem a carga da homologacao local concluida"
# Portao fiscal (mesma fonte e mesma regra da GL.1): o compose controla a
# variavel; toda ocorrencia literalmente false; defesa extra no .env.
[ -f "$SRC/docker-compose.yml" ] || falhar 2 "docker-compose.yml nao encontrado em $SRC (ambiente da homologacao local, P0.27)"
FISCAL_RC=0
python3 - "$SRC/docker-compose.yml" "$SRC/.env" <<'PYEOF' || FISCAL_RC=$?
import os, re, sys

VAR = "ZIIPSAN_FISCAL_PRODUCTION_ALLOWED"

def valores(texto):
    return re.findall(VAR + r"\s*[:=]\s*([^\s#]+)", texto)

def normaliza(bruto):
    return bruto.strip().strip("\"'").lower()

compose = open(sys.argv[1], encoding="utf-8", errors="replace").read()
ocorrencias = valores(compose)
if not ocorrencias:
    print("INDETERMINADO: variavel ausente no docker-compose.yml", file=sys.stderr)
    sys.exit(3)
for bruto in ocorrencias:
    if "${" in bruto or normaliza(bruto) != "false":
        print("compose com valor nao-false ou nao literal", file=sys.stderr)
        sys.exit(1)
if os.path.exists(sys.argv[2]):
    env = open(sys.argv[2], encoding="utf-8", errors="replace").read()
    for bruto in valores(env):
        if normaliza(bruto) == "true":
            print(".env com a variavel ligada", file=sys.stderr)
            sys.exit(2)
sys.exit(0)
PYEOF
case "$FISCAL_RC" in
  0) ;;
  1) falhar 2 "ZIIPSAN_FISCAL_PRODUCTION_ALLOWED no docker-compose.yml NAO e literalmente false: o paralelo local NUNCA emite em producao (tpAmb=1)" ;;
  2) falhar 2 "ZIIPSAN_FISCAL_PRODUCTION_ALLOWED ligada no .env (defesa extra): o paralelo local NUNCA emite em producao (tpAmb=1)" ;;
  *) falhar 2 "nao foi possivel DETERMINAR ZIIPSAN_FISCAL_PRODUCTION_ALLOWED no docker-compose.yml: ausente ou interpolada — portao fiscal recusa sem determinacao (fail-closed)" ;;
esac
# Arquivos do piloto de TODOS os ciclos antes do primeiro ciclo.
N=1
while [ "$N" -le "$NCICLOS" ]; do
  [ -f "$PILOTO_DIR/ciclo-$N/contas-piloto.json" ] || falhar 2 "contas do piloto do ciclo $N ausentes: $PILOTO_DIR/ciclo-$N/contas-piloto.json"
  N=$((N+1))
done
# Criterio do ciclo, extraido da config validada para o comparador.
mkdir -p "$REG_DIR"
python3 - "$CONFIG" "$REG_DIR/criterio.json" <<'PYEOF' || falhar 2 "falha ao extrair o criterio da config"
import json, sys
cfg = json.load(open(sys.argv[1], encoding="utf-8"))
json.dump(cfg["criterioDivergencia"], open(sys.argv[2], "w", encoding="utf-8"))
PYEOF
log_etapa "pre-voo OK: config valida (ciclos=$NCICLOS; perfil P2.31 ativo com vigencia conferida; criterio fonte P0.30; conteudo P2.32; escopo P3.30 unidade=$ORG_UNIDADE area=$ESCOPO_AREA), GL.1 CONCLUIDO, fiscal de producao desligado, contas do piloto presentes nos $NCICLOS ciclos"

if [ "$PREFLIGHT_ONLY" = "1" ]; then
  STATUS="PARCIAL (--preflight-only: ciclos NAO EXECUTADOS)"
  log_etapa "encerrado em --preflight-only: nenhum ciclo executado"
  gravar_registro
  echo "GL.2 pre-voo OK (parcial). Registro: $REG_FILE"
  exit 0
fi

export GL2_ORG_UNIDADE="$ORG_UNIDADE" GL2_ESCOPO_AREA="$ESCOPO_AREA"

# ---------------------------------------------------------------- (b) ciclos
N=1
while [ "$N" -le "$NCICLOS" ]; do
  CICLO_DIR="$REG_DIR/ciclo-$N"
  mkdir -p "$CICLO_DIR/fiscal"
  CONTAS_Z="$CICLO_DIR/contas-ziipsan.json"
  CONTAS_P="$PILOTO_DIR/ciclo-$N/contas-piloto.json"
  EXPL="$PILOTO_DIR/ciclo-$N/explicacoes.json"
  OCORR="$PILOTO_DIR/ciclo-$N/ocorrencias.json"
  REL="$CICLO_DIR/relatorio-comparacao.json"
  export GL2_CICLO="$N" GL2_SAIDA_CONTAS="$CONTAS_Z" GL2_FISCAL_DIR="$CICLO_DIR/fiscal"

  # -- 1. faturamento (hook do motor do produto)
  ETAPA="faturamento-ciclo-$N"
  if [ -z "${ZIIPSAN_GL2_CMD_FATURAMENTO:-}" ]; then
    falhar 4 "hook de faturamento nao fiado (ZIIPSAN_GL2_CMD_FATURAMENTO) — pendencia de fiacao declarada, ciclo $N NAO executado"
  fi
  if ! bash -c "$ZIIPSAN_GL2_CMD_FATURAMENTO" >"$CICLO_DIR/faturamento.log" 2>&1; then
    falhar 4 "hook de faturamento do ciclo $N saiu com erro (ver faturamento.log ao lado do registro)"
  fi
  [ -f "$CONTAS_Z" ] || falhar 4 "hook de faturamento do ciclo $N nao gerou as contas do ZiipSan ($CONTAS_Z)"
  log_etapa "ciclo $N: faturamento executado pelo hook fiado"

  # -- 2. conteudo minimo da fatura (P2.32), em cada conta do ciclo
  ETAPA="conteudo-ciclo-$N"
  if [ -z "${ZIIPSAN_GL2_CMD_CONTEUDO:-}" ]; then
    falhar 3 "hook de conferencia do conteudo minimo (P2.32) nao fiado (ZIIPSAN_GL2_CMD_CONTEUDO) — 'conferido em cada conta' e criterio do acceptance, ciclo $N NAO conferido"
  fi
  export GL2_CONTAS="$CONTAS_Z"
  if ! bash -c "$ZIIPSAN_GL2_CMD_CONTEUDO" >"$CICLO_DIR/conteudo.log" 2>&1; then
    falhar 3 "conferencia do conteudo minimo (P2.32) reprovou no ciclo $N (ver conteudo.log ao lado do registro)"
  fi
  log_etapa "ciclo $N: conteudo minimo da fatura (P2.32) conferido pelo hook fiado"

  # -- 3. escopo (P3.30): hook, quando fiado, tem de passar
  ETAPA="escopo-ciclo-$N"
  if [ -n "${ZIIPSAN_GL2_CMD_ESCOPO:-}" ]; then
    if ! bash -c "$ZIIPSAN_GL2_CMD_ESCOPO" >"$CICLO_DIR/escopo.log" 2>&1; then
      falhar 7 "hook de escopo (P3.30) reprovou no ciclo $N (ver escopo.log ao lado do registro)"
    fi
    log_etapa "ciclo $N: escopo por area (P3.30) conferido pelo hook fiado"
  else
    log_etapa "ciclo $N: operacao sob unidade/area da config (P3.30); hook de escopo nao fiado — conferencia automatica do escopo e pendencia de fiacao declarada"
  fi

  # -- 4. comparacao conta a conta
  ETAPA="comparacao-ciclo-$N"
  CMP_ARGS=(--ziipsan "$CONTAS_Z" --piloto "$CONTAS_P" --criterio "$REG_DIR/criterio.json" --saida "$REL")
  [ -f "$EXPL" ] && CMP_ARGS+=(--explicacoes "$EXPL")
  CMP_RC=0
  python3 "$COMPARADOR" "${CMP_ARGS[@]}" >"$CICLO_DIR/comparacao.log" 2>&1 || CMP_RC=$?
  case "$CMP_RC" in
    0) ;;
    3) falhar 5 "entrada da comparacao do ciclo $N invalida (ver comparacao.log e relatorio-comparacao.json, quando gravado) — entrada invalida nunca vira 'sem divergencia'" ;;
    1|2)
      if [ -n "${ZIIPSAN_GL2_CMD_RELATORIOS:-}" ]; then
        bash -c "$ZIIPSAN_GL2_CMD_RELATORIOS" >"$CICLO_DIR/relatorios-divergencia.log" 2>&1 || true
      fi
      if [ "$CMP_RC" = "1" ]; then
        falhar 5 "ciclo $N FORA do criterio de divergencia da P0.30 — relatorio gravado em relatorio-comparacao.json; divergencia se corrige pela retificacao da P2.20 e se registra, nao se ajusta para passar"
      else
        falhar 5 "ciclo $N com divergencia SEM explicacao — o acceptance exige TODA divergencia explicada; relatorio gravado em relatorio-comparacao.json"
      fi
      ;;
    *) falhar 5 "comparador do ciclo $N saiu com codigo inesperado $CMP_RC" ;;
  esac
  # Resumo medido do relatorio + divergencias dentro do criterio exigem
  # retificacao (P2.20) e conferencia por relatorios (P3.22).
  RESUMO="$(python3 - "$REL" <<'PYEOF'
import json, sys
r = json.load(open(sys.argv[1], encoding="utf-8"))
print(r["contasComparadas"], r["iguais"], r["totalDivergencias"], r["somaDiffCentavos"], r["ausencias"])
PYEOF
  )" || falhar 5 "relatorio da comparacao do ciclo $N ilegivel"
  NCOMP="$(echo "$RESUMO" | cut -d' ' -f1)"; NIG="$(echo "$RESUMO" | cut -d' ' -f2)"
  NDIV="$(echo "$RESUMO" | cut -d' ' -f3)"; SDIFF="$(echo "$RESUMO" | cut -d' ' -f4)"
  NAUS="$(echo "$RESUMO" | cut -d' ' -f5)"
  log_etapa "ciclo $N: comparacao conta a conta OK — $NCOMP contas comparadas, $NIG iguais, $NDIV divergencia(s) dentro do criterio e explicada(s), soma de diferencas de ${SDIFF} centavos, $NAUS ausencia(s)"

  ETAPA="pos-divergencia-ciclo-$N"
  if [ "$NDIV" -gt 0 ]; then
    if [ -z "${ZIIPSAN_GL2_CMD_RETIFICACAO:-}" ]; then
      falhar 7 "ciclo $N tem $NDIV divergencia(s) explicada(s) dentro do criterio, mas o hook de retificacao da P2.20 nao esta fiado (ZIIPSAN_GL2_CMD_RETIFICACAO) — divergencia sem correcao conferida nao fecha o ciclo"
    fi
    if ! bash -c "$ZIIPSAN_GL2_CMD_RETIFICACAO" >"$CICLO_DIR/retificacao.log" 2>&1; then
      falhar 7 "retificacao (P2.20) do ciclo $N saiu com erro (ver retificacao.log ao lado do registro)"
    fi
    if [ -z "${ZIIPSAN_GL2_CMD_RELATORIOS:-}" ]; then
      falhar 7 "ciclo $N retificado, mas o hook de relatorios da P3.22 nao esta fiado (ZIIPSAN_GL2_CMD_RELATORIOS) — a conferencia da correcao pelos relatorios e criterio do acceptance"
    fi
    if ! bash -c "$ZIIPSAN_GL2_CMD_RELATORIOS" >"$CICLO_DIR/relatorios.log" 2>&1; then
      falhar 7 "relatorios (P3.22) do ciclo $N sairam com erro (ver relatorios.log ao lado do registro)"
    fi
    log_etapa "ciclo $N: divergencias corrigidas pela retificacao (P2.20) e conferidas pelos relatorios (P3.22), pelos hooks fiados"
  fi

  # -- 5. devolucoes e pagamentos nao identificados (P3.29)
  ETAPA="devolucoes-ciclo-$N"
  NOCORR=0
  if [ -f "$OCORR" ]; then
    NOCORR="$(python3 - "$OCORR" <<'PYEOF'
import json, sys
o = json.load(open(sys.argv[1], encoding="utf-8"))
print(int(o.get("devolucoes", 0)) + int(o.get("pagamentosNaoIdentificados", 0)))
PYEOF
    )" || falhar 7 "ocorrencias.json do ciclo $N ilegivel"
  fi
  if [ "$NOCORR" -gt 0 ] && [ -z "${ZIIPSAN_GL2_CMD_DEVOLUCOES:-}" ]; then
    falhar 7 "ciclo $N declara $NOCORR ocorrencia(s) de devolucao/pagamento nao identificado, mas o hook da P3.29 nao esta fiado (ZIIPSAN_GL2_CMD_DEVOLUCOES)"
  fi
  if [ -n "${ZIIPSAN_GL2_CMD_DEVOLUCOES:-}" ]; then
    if ! bash -c "$ZIIPSAN_GL2_CMD_DEVOLUCOES" >"$CICLO_DIR/devolucoes.log" 2>&1; then
      falhar 7 "tratamento de devolucoes/pagamentos nao identificados (P3.29) do ciclo $N saiu com erro (ver devolucoes.log ao lado do registro)"
    fi
    log_etapa "ciclo $N: devolucoes/pagamentos nao identificados tratados pela P3.29 (hook fiado; ocorrencias declaradas: $NOCORR)"
  else
    log_etapa "ciclo $N: sem ocorrencias de devolucao/pagamento nao identificado declaradas e sem hook da P3.29 fiado"
  fi

  # -- 6. fechamento fiscal do ciclo (P3.7)
  ETAPA="fiscal-ciclo-$N"
  # Correcao R2: -iname cobre .xml/.XML/mistos; escopo = arvore do ciclo
  # no diretorio do registro ($CICLO_DIR), fora da area separada fiscal/.
  STRAY="$(find "$CICLO_DIR" -iname '*.xml' -not -path "$CICLO_DIR/fiscal/*" | head -1)"
  if [ -n "$STRAY" ]; then
    falhar 6 "XML fora da area separada do ciclo $N: $STRAY — NFAg-e do paralelo so vive na area separada e e apagado ao fim do ciclo (P3.7)"
  fi
  LIMPEZA="$CICLO_DIR/limpeza-fiscal.json"
  python3 - "$CICLO_DIR/fiscal" "$N" "$LIMPEZA" <<'PYEOF' || falhar 6 "limpeza fiscal do ciclo $N falhou (registro da exclusao nao gravado ou XML remanescente)"
import hashlib, json, os, sys
from datetime import datetime, timezone

fiscal_dir, ciclo, saida = sys.argv[1], int(sys.argv[2]), sys.argv[3]
xmls = []
if os.path.isdir(fiscal_dir):
    xmls = sorted(f for f in os.listdir(fiscal_dir) if f.lower().endswith(".xml"))
hashes = []
for nome in xmls:
    with open(os.path.join(fiscal_dir, nome), "rb") as fh:
        hashes.append(hashlib.sha256(fh.read()).hexdigest())
hash_lote = hashlib.sha256("\n".join(hashes).encode()).hexdigest() if hashes else None
for nome in xmls:
    os.remove(os.path.join(fiscal_dir, nome))
restantes = []
if os.path.isdir(fiscal_dir):
    restantes = [f for f in os.listdir(fiscal_dir) if f.lower().endswith(".xml")]
registro = {
    "ciclo": ciclo,
    "quantidadeApagada": len(xmls),
    "hashDoLoteSha256": hash_lote,
    "apagadosEm": datetime.now(timezone.utc).isoformat(),
    "fonte": "P3.7 — XML assinados da homologacao/simulador apagados ao fim do ciclo",
}
with open(saida, "w", encoding="utf-8") as fh:
    json.dump(registro, fh, ensure_ascii=False, indent=1)
if restantes:
    print(f"ERRO: {len(restantes)} XML remanescente(s) apos a limpeza", file=sys.stderr)
    sys.exit(1)
print(f"LIMPEZA_OK {len(xmls)}")
PYEOF
  NXML="$(python3 -c "import json,sys; print(json.load(open(sys.argv[1]))['quantidadeApagada'])" "$LIMPEZA")"
  log_etapa "ciclo $N: fechamento fiscal (P3.7) OK — $NXML XML assinado(s) apagado(s) da area separada, exclusao registrada em limpeza-fiscal.json (quantidade + hash do lote)"
  CICLOS_LOG+=("ciclo $N: $NCOMP contas comparadas conta a conta, $NIG iguais, $NDIV divergencia(s) dentro do criterio P0.30 e explicada(s), soma de diferencas ${SDIFF} centavos, $NAUS ausencia(s); conteudo P2.32 conferido; devolucoes/nao identificados declarados: $NOCORR; XML fiscais apagados ao fim do ciclo: $NXML")
  CICLOS_CONCLUIDOS=$((CICLOS_CONCLUIDOS+1))

  N=$((N+1))
done

# Correcao D1(c): o fechamento exige >= 2 ciclos EFETIVAMENTE comparados
# e concluidos, e exatamente os NCICLOS configurados -- zero ciclo (ou
# menos do que o configurado) NUNCA fecha como CONCLUIDO.
ETAPA="fechamento"
if [ "$CICLOS_CONCLUIDOS" -lt 2 ] || [ "$CICLOS_CONCLUIDOS" -ne "$NCICLOS" ]; then
  falhar 2 "fechamento recusado: $CICLOS_CONCLUIDOS de $NCICLOS ciclo(s) concluido(s) — CONCLUIDO exige >= 2 ciclos efetivamente comparados e todos os ciclos configurados"
fi
STATUS="CONCLUIDO"
log_etapa "paralelo local concluido: $NCICLOS ciclo(s) comparados conta a conta, com o registro do ensaio de POC gravado"
gravar_registro
echo "GL.2 concluida. Registro: $REG_FILE"
exit 0
