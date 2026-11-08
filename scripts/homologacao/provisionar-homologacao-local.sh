#!/bin/bash
# GL.1: plano executável da homologação local -- provisionamento do ambiente
# + portões da carga anonimizada, abortando na PRIMEIRA falha.
#
#   scripts/homologacao/provisionar-homologacao-local.sh \
#     --manifesto <manifesto-carga.json> \
#     --entrada-dir <dir com os arquivos extraídos JÁ anonimizados na origem> \
#     --totais-carregados <totais-carregados.json> \
#     [--dry-run] [--preflight-only]
#
# O que este orquestrador FAZ (e o que ele NÃO faz):
#   (a) pré-voo: ambiente local de homologação da P0.27 existe
#       (scripts/local-env.sh + docker-compose.yml + .env), a flag fiscal
#       de produção está DESLIGADA e o manifesto da carga é válido
#       (validar-manifesto.py). O portão fiscal confere a FONTE QUE
#       CONTROLA a variável: no compose real da P0.27,
#       ZIIPSAN_FISCAL_PRODUCTION_ALLOWED é literal ("false") no
#       `environment:` de `api`/`migrate` e NÃO consta do .env.example --
#       logo o compose é a fonte autoritativa: TODA ocorrência no compose
#       tem de ser literalmente false; variável ausente no compose, valor
#       interpolado (${...}) ou qualquer outro valor = NÃO DETERMINADO/
#       LIGADO e a carga é RECUSADA (fail-closed). Como defesa extra, o
#       .env também é conferido: qualquer ocorrência ligada (parsing
#       robusto: maiúsculas/minúsculas, com ou sem aspas) recusa a carga.
#       Homologação local nunca emite em produção (tpAmb=1);
#   (b) provisionamento: sobe o perfil `homolog` pelo scripts/local-env.sh
#       da P0.27 (a pilha é DELE; este script não duplica compose nem
#       inventa imagem). Este script NUNCA derruba o ambiente ao fim (o
#       ambiente é do operador; ele só registra que o ambiente ficou no
#       ar; ver roteiro). Não existe flag para isso: a antiga --keep-env
#       era código morto e foi REMOVIDA (correção D6 da revisão);
#   (c) varredura de ENTRADA: nenhum arquivo da extração pode conter
#       CPF/CNPJ formatado ou e-mail (dado pessoal real não entra nem em
#       arquivo -- a anonimização é NA ORIGEM, roteiro da P0.45). A
#       varredura cobre TODOS os arquivos, SEM exceção de extensão:
#       inclui Markdown (*.md) e lê arquivos binários como texto
#       (grep -a, sem -I e sem --exclude; correção D2 da revisão).
#       LIMITE DECLARADO da varredura: ela é por PADRÕES (CPF/CNPJ
#       formatados e e-mail); documento só com dígitos puros e nome/
#       endereço em texto livre NÃO são detectados por ela -- os portões
#       fortes continuam sendo a anonimização na origem (P0.45) e a
#       varredura pós-carga (f). XML real é recusado pelo manifesto
#       (xmlOrigem) antes desta etapa;
#   (d) cargas, NA ORDEM, pelos MESMOS importadores da carga real (LC.10):
#       cadastro pela P1.2, histórico de 120 meses pela P2.10, XML
#       anteriores pela P2.19 (só sintéticos, certificado de teste) e
#       cadastro técnico pela P3.26. Os importadores são SERVIÇOS Java do
#       produto, sem CLI neste repo: cada etapa roda o comando que o
#       operador/integração fiar em ZIIPSAN_GL1_CMD_CADASTRO /
#       _HISTORICO / _XML / _GIS. Sem comando fiado, a etapa FALHA
#       (saída 4) declarando a pendência de fiação -- nada é fingido.
#   (e) conciliação: os totais carregados (relatórios dos importadores,
#       consolidados pelo operador em --totais-carregados) têm de ser
#       IGUAIS aos totais do sistema do piloto declarados no manifesto;
#       divergência reprova (saída 5) e fica registrada, sem ajuste manual.
#       Este portão confere SÓ os totais gerais: a conciliação FINA
#       (setor/rota/categoria da P1.2, saldo por código substituto da
#       P2.10, contagem por competência da P2.19) é a dos relatórios dos
#       próprios importadores -- o registro gerado declara essa fronteira
#       e os relatórios finos (ou a referência a eles: caminho/
#       identificador de lote) DEVEM acompanhar o registro da carga,
#       conforme o modelo de registro (correção D4 da revisão);
#   (f) varredura PÓS-CARGA da P0.45 (SyntheticDataScanTest / suíte do
#       produto), pelo comando fiado em ZIIPSAN_GL1_CMD_VARREDURA_POS.
#       Sem comando, reprova (saída 6) -- "sem nenhum dado pessoal real na
#       base local" é critério de aceite, não declaração;
#   (g) REGISTRO da carga preenchido com os valores MEDIDOS/declarados nos
#       artefatos desta execução, em
#       docs/homologacao/cargas-homologacao/<carimbo>-gl1/registro-carga.md.
#       O registro NUNCA afirma resultado de etapa que não rodou.
#
# Modos: --dry-run imprime o plano e sai 0 sem executar nem gravar nada;
# --preflight-only executa (a)+(c) e grava registro PARCIAL (etapas de
# carga/conciliação/varredura marcadas NÃO EXECUTADAS).
#
# Códigos de saída (distintos por etapa; a primeira falha para tudo):
#   0  concluído (ou pré-voo+varredura de entrada OK em --preflight-only)
#   2  pré-voo: parâmetro ausente, ambiente/manifesto inválido, fiscal
#      de produção ligado, validador ausente
#   3  varredura de entrada reprovada (dado pessoal em arquivo)
#   4  etapa de carga: comando do importador ausente (fiação pendente)
#      ou comando saiu != 0
#   5  conciliação divergente ou arquivo de totais ilegível
#   6  varredura pós-carga ausente (fiação pendente) ou reprovada
#
# Sem `set -e`: toda falha precisa gravar o registro antes de sair.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="${ZIIPSAN_GL1_SRC:-$(cd "$SCRIPT_DIR/../.." && pwd)}"
VALIDADOR="$SCRIPT_DIR/validar-manifesto.py"

MANIFESTO=""; ENTRADA_DIR=""; TOTAIS=""; DRY_RUN=0; PREFLIGHT_ONLY=0
ETAPA="pre-voo"; STATUS="EM_EXECUCAO"; CAUSA=""
declare -a ETAPAS_LOG=()
START_TS="$(date +%s)"
STAMP="$(date +%Y%m%d-%H%M%S)"
REG_DIR="$SRC/docs/homologacao/cargas-homologacao/${STAMP}-gl1"
REG_FILE="$REG_DIR/registro-carga.md"

log_etapa() { ETAPAS_LOG+=("$1"); echo "$1"; }

# gravar_registro/falhar/trap são definidos ANTES da leitura dos argumentos
# de propósito (correção D5 da revisão): erro de sintaxe de argumentos
# também é uma FALHA da execução e tem de sair pelo caminho que GRAVA o
# registro (saída 2 com registro), nunca como exit 2 silencioso. Única
# exceção, por definição: em --dry-run nada é gravado (nem registro de
# falha) -- ver gravar_registro.
gravar_registro() {
  [ "$DRY_RUN" = "1" ] && return 0
  mkdir -p "$REG_DIR"
  local fim dur
  fim="$(date +%s)"; dur=$((fim - START_TS))
  {
    echo "# Registro de carga — GL.1 (homologação local)"
    echo
    echo "- Carimbo: $STAMP"
    echo "- Resultado: **$STATUS**"
    [ -n "$CAUSA" ] && echo "- Etapa da falha / causa: $CAUSA"
    echo "- Duração total medida: ${dur}s"
    echo "- Manifesto: ${MANIFESTO:-<ausente>}"
    echo "- Entrada: ${ENTRADA_DIR:-<ausente>}"
    echo
    echo "## Etapas (nesta execução)"
    echo
    if [ "${#ETAPAS_LOG[@]}" -eq 0 ]; then echo "- (nenhuma etapa concluída)"; fi
    local linha; for linha in "${ETAPAS_LOG[@]}"; do echo "- $linha"; done
    echo
    echo "## Conciliação fina (evidência dos importadores — D4)"
    echo
    echo "- O portão (e) deste orquestrador confere SÓ os totais gerais."
    echo "  A conciliação fina é a dos relatórios dos próprios importadores"
    echo "  (P1.2: setor/rota/categoria; P2.10: saldo por código substituto;"
    echo "  P2.19: contagem por competência) e os relatórios finos — ou a"
    echo "  referência a eles (caminho/identificador de lote, ver modelo de"
    echo "  registro) — DEVEM acompanhar este registro. Registro CONCLUIDO"
    echo "  sem essa evidência ao lado está INCOMPLETO."
    echo
    echo "> Registro gerado pela execução do orquestrador da GL.1. Ele afirma"
    echo "> somente o que esta execução mediu/executou; etapa não executada"
    echo "> está marcada como tal. Não reescrever à mão para \"melhorar\""
    echo "> resultado: divergência é resultado da carga e fica registrada."
  } > "$REG_FILE"
  echo "registro: $REG_FILE" >&2
}

falhar() { # $1=código $2=causa
  STATUS="FALHA"; CAUSA="$ETAPA — $2"
  echo "FALHA GL.1 [$ETAPA]: $2" >&2
  gravar_registro
  exit "$1"
}

trap 'STATUS="FALHA"; CAUSA="${CAUSA:-interrompido por SIGINT/SIGTERM na etapa $ETAPA}"; gravar_registro; exit 143' TERM
trap 'STATUS="FALHA"; CAUSA="${CAUSA:-interrompido por SIGINT/SIGTERM na etapa $ETAPA}"; gravar_registro; exit 130' INT

while [ $# -gt 0 ]; do
  case "$1" in
    --manifesto) [ $# -ge 2 ] || falhar 2 "--manifesto exige o caminho do manifesto"; shift; MANIFESTO="$1" ;;
    --manifesto=*) MANIFESTO="${1#--manifesto=}" ;;
    --entrada-dir) [ $# -ge 2 ] || falhar 2 "--entrada-dir exige o diretório da extração"; shift; ENTRADA_DIR="$1" ;;
    --entrada-dir=*) ENTRADA_DIR="${1#--entrada-dir=}" ;;
    --totais-carregados) [ $# -ge 2 ] || falhar 2 "--totais-carregados exige o JSON de totais"; shift; TOTAIS="$1" ;;
    --totais-carregados=*) TOTAIS="${1#--totais-carregados=}" ;;
    --dry-run) DRY_RUN=1 ;;
    --preflight-only) PREFLIGHT_ONLY=1 ;;
    -*) falhar 2 "opção desconhecida: $1" ;;
    *) falhar 2 "argumento inesperado: $1" ;;
  esac
  shift
done

# ---------------------------------------------------------------- dry-run
if [ "$DRY_RUN" = "1" ]; then
  echo "PLANO GL.1 (dry-run — nada será executado nem gravado):"
  echo "  1. pré-voo: local-env.sh/docker-compose/.env, fiscal de produção desligado (compose=false literal; .env sem ligação), manifesto válido"
  echo "  2. provisionar: scripts/local-env.sh homolog"
  echo "  3. varredura de entrada em: ${ENTRADA_DIR:-<--entrada-dir>}"
  echo "  4. cargas (hooks): P1.2 cadastro -> P2.10 histórico -> P2.19 XML sintético -> P3.26 GIS"
  echo "  5. conciliação: manifesto.totaisPiloto == ${TOTAIS:-<--totais-carregados>}"
  echo "  6. varredura pós-carga (P0.45) via ZIIPSAN_GL1_CMD_VARREDURA_POS"
  echo "  7. registro em docs/homologacao/cargas-homologacao/<carimbo>-gl1/registro-carga.md"
  exit 0
fi

# ---------------------------------------------------------------- (a) pré-voo
ETAPA="pre-voo"
[ -n "$MANIFESTO" ] && [ -f "$MANIFESTO" ] || falhar 2 "manifesto ausente ou não encontrado (--manifesto)"
[ -n "$ENTRADA_DIR" ] && [ -d "$ENTRADA_DIR" ] || falhar 2 "diretório de entrada ausente (--entrada-dir)"
[ -f "$SRC/scripts/local-env.sh" ] || falhar 2 "scripts/local-env.sh (P0.27) não encontrado em $SRC"
[ -f "$SRC/docker-compose.yml" ] || falhar 2 "docker-compose.yml não encontrado em $SRC"
[ -f "$SRC/.env" ] || falhar 2 ".env não encontrado em $SRC (copie de .env.example)"
[ -f "$VALIDADOR" ] || falhar 2 "validador do manifesto não encontrado: $VALIDADOR"
command -v python3 >/dev/null 2>&1 || falhar 2 "python3 não encontrado (validador/conciliação)"
# Portão fiscal (correção D3 da revisão): a fonte AUTORITATIVA é o
# docker-compose.yml -- no compose real da P0.27 a variável é literal
# "false" no environment de api/migrate e nem consta do .env.example, logo
# conferir só o .env era conferir o arquivo que NÃO controla a variável.
# Regras (fail-closed): (i) TODA ocorrência no compose tem de ter valor
# literal false (aspas e grafia livres); ocorrência ausente, valor
# interpolado (${...}) ou qualquer outro valor => recusa, porque o portão
# não consegue determinar que a emissão de produção está desligada;
# (ii) defesa extra: qualquer ocorrência LIGADA no .env também recusa.
# O parsing (python3, ferramenta de operação) é tolerante a maiúsculas/
# minúsculas e a aspas simples/duplas nas duas fontes -- "TRUE" e "\"true\""
# NÃO passam mais.
FISCAL_RC=0
python3 - "$SRC/docker-compose.yml" "$SRC/.env" <<'PYEOF' || FISCAL_RC=$?
import re, sys

VAR = "ZIIPSAN_FISCAL_PRODUCTION_ALLOWED"

def valores(texto):
    """Valores brutos de toda ocorrência da variável (forma `VAR: v` ou `VAR=v`)."""
    return re.findall(VAR + r"\s*[:=]\s*([^\s#]+)", texto)

def normaliza(bruto):
    return bruto.strip().strip("\"'").lower()

def main():
    compose_path, env_path = sys.argv[1], sys.argv[2]
    compose = open(compose_path, encoding="utf-8", errors="replace").read()
    ocorrencias = valores(compose)
    if not ocorrencias:
        print("INDETERMINADO: variável ausente no docker-compose.yml", file=sys.stderr)
        return 3
    for bruto in ocorrencias:
        if "${" in bruto or normaliza(bruto) != "false":
            print("compose com valor nao-false ou nao literal", file=sys.stderr)
            return 1
    env = open(env_path, encoding="utf-8", errors="replace").read()
    for bruto in valores(env):
        if normaliza(bruto) == "true":
            print(".env com a variável ligada", file=sys.stderr)
            return 2
    return 0

sys.exit(main())
PYEOF
case "$FISCAL_RC" in
  0) ;;
  1) falhar 2 "ZIIPSAN_FISCAL_PRODUCTION_ALLOWED no docker-compose.yml NÃO é literalmente false em todas as ocorrências: homologação local NUNCA emite em produção (tpAmb=1)" ;;
  2) falhar 2 "ZIIPSAN_FISCAL_PRODUCTION_ALLOWED ligada no .env (defesa extra): homologação local NUNCA emite em produção (tpAmb=1)" ;;
  *) falhar 2 "não foi possível DETERMINAR ZIIPSAN_FISCAL_PRODUCTION_ALLOWED no docker-compose.yml (fonte que controla a variável): ausente ou interpolada — portão fiscal recusa sem determinação (fail-closed)" ;;
esac
if ! python3 "$VALIDADOR" "$MANIFESTO" >/dev/null; then
  python3 "$VALIDADOR" "$MANIFESTO" >&2 || true
  falhar 2 "manifesto inválido (ver erros do validador acima)"
fi
log_etapa "pre-voo OK: ambiente P0.27 presente, fiscal de produção desligado (compose=false literal, .env sem ligação), manifesto válido"

# ------------------------------------------------------- (b) provisionamento
ETAPA="provisionamento"
if [ "$PREFLIGHT_ONLY" != "1" ]; then
  mkdir -p "$(dirname "$REG_DIR")"
  if bash "$SRC/scripts/local-env.sh" homolog >"$REG_DIR.env.log" 2>&1; then
    log_etapa "provisionamento OK: perfil homolog da P0.27 no ar (log ao lado do registro)"
    mkdir -p "$REG_DIR"; mv "$REG_DIR.env.log" "$REG_DIR/local-env.log"
  else
    mkdir -p "$REG_DIR"; mv "$REG_DIR.env.log" "$REG_DIR/local-env.log" 2>/dev/null || true
    falhar 2 "scripts/local-env.sh homolog falhou (ver local-env.log ao lado do registro)"
  fi
else
  log_etapa "provisionamento NÃO EXECUTADO (--preflight-only)"
fi

# ------------------------------------------------------ (c) varredura entrada
ETAPA="varredura-entrada"
# CPF formatado, CNPJ formatado ou e-mail em QUALQUER arquivo da extração:
# a anonimização é na origem (P0.45) — achou, a carga nem começa.
# Cobertura (correção D2 da revisão): SEM --exclude e SEM -I — inclui
# arquivos Markdown (*.md) e lê binários como texto (-a); a versão anterior
# excluía *.md e ignorava binários SEM declarar, e PII nesses arquivos
# passava com saída 0. Limite que PERMANECE, declarado no cabeçalho: a
# varredura é por padrões formatados (dígitos puros e texto livre passam).
ACHADOS="$(grep -ranE \
  -e '[0-9]{3}\.[0-9]{3}\.[0-9]{3}-[0-9]{2}' \
  -e '[0-9]{2}\.[0-9]{3}\.[0-9]{3}/[0-9]{4}-[0-9]{2}' \
  -e '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' \
  "$ENTRADA_DIR" 2>/dev/null | cut -d: -f1 | sort -u || true)"
if [ -n "$ACHADOS" ]; then
  echo "arquivos com possível dado pessoal (nomes apenas; valores NÃO reproduzidos):" >&2
  echo "$ACHADOS" >&2
  falhar 3 "varredura de entrada reprovou arquivo(s) da extração — anonimizar NA ORIGEM (P0.45) e extrair de novo"
fi
log_etapa "varredura-entrada OK: nenhum CPF/CNPJ formatado ou e-mail nos arquivos da extração (todos os arquivos, incl. *.md e binários lidos como texto)"

if [ "$PREFLIGHT_ONLY" = "1" ]; then
  STATUS="PARCIAL (--preflight-only: cargas, conciliação e varredura pós-carga NÃO EXECUTADAS)"
  log_etapa "encerrado em --preflight-only: etapas (d)-(f) NÃO EXECUTADAS"
  gravar_registro
  echo "GL.1 pré-voo OK (parcial). Registro: $REG_FILE"
  exit 0
fi

# ---------------------------------------------------------------- (d) cargas
rodar_carga() { # $1=rótulo $2=variável do comando
  ETAPA="carga-$1"
  local cmd="${2:-}"
  if [ -z "$cmd" ]; then
    falhar 4 "comando do importador $1 não fiado (defina a variável correspondente) — pendência de fiação declarada, etapa NÃO executada"
  fi
  echo "== carga $1: executando comando fiado"
  if bash -c "$cmd"; then
    log_etapa "carga-$1 OK: importador executado pelo comando fiado"
  else
    falhar 4 "importador $1 saiu com erro"
  fi
}
rodar_carga "cadastro-P1.2" "${ZIIPSAN_GL1_CMD_CADASTRO:-}"
rodar_carga "historico-P2.10" "${ZIIPSAN_GL1_CMD_HISTORICO:-}"
rodar_carga "xml-P2.19" "${ZIIPSAN_GL1_CMD_XML:-}"
rodar_carga "gis-P3.26" "${ZIIPSAN_GL1_CMD_GIS:-}"

# ------------------------------------------------------------ (e) conciliação
ETAPA="conciliacao"
[ -n "$TOTAIS" ] && [ -f "$TOTAIS" ] || falhar 5 "arquivo de totais carregados ausente (--totais-carregados)"
if python3 - "$MANIFESTO" "$TOTAIS" <<'PYEOF'
import json, sys
man = json.load(open(sys.argv[1], encoding="utf-8"))
car = json.load(open(sys.argv[2], encoding="utf-8"))
esp = man.get("totaisPiloto", {})
div = [k for k in ("unidades", "saldoTotalCentavos", "registrosHistorico", "xmlAceitos")
       if car.get(k) != esp.get(k)]
if div:
    print("DIVERGÊNCIA nos campos: " + ", ".join(div), file=sys.stderr)
    sys.exit(1)
print("CONCILIACAO_OK")
PYEOF
then
  log_etapa "conciliação OK: totais carregados == totais do sistema do piloto (manifesto)"
else
  falhar 5 "conciliação divergente ou ilegível — divergência registrada, NÃO ajustar à mão"
fi

# ------------------------------------------------- (f) varredura pós-carga
ETAPA="varredura-pos-carga"
if [ -z "${ZIIPSAN_GL1_CMD_VARREDURA_POS:-}" ]; then
  falhar 6 "varredura pós-carga da P0.45 não fiada (ZIIPSAN_GL1_CMD_VARREDURA_POS) — critério 'sem dado pessoal real na base local' NÃO provado"
fi
if bash -c "$ZIIPSAN_GL1_CMD_VARREDURA_POS"; then
  log_etapa "varredura-pos-carga OK: teste de varredura da P0.45 verde (comando fiado)"
else
  falhar 6 "teste de varredura da P0.45 reprovou — há dado pessoal real ou documento sem marca de sintético na base local"
fi

STATUS="CONCLUIDO"
log_etapa "carga da homologação local concluída: ambiente provisionado, cargas pelos importadores, conciliação e varreduras verdes"
gravar_registro
echo "GL.1 concluída. Registro: $REG_FILE"
exit 0
