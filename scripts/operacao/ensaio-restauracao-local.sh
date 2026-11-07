#!/bin/bash
# GL.3: ensaio de restauracao local -- orquestrador.
#
#   scripts/operacao/ensaio-restauracao-local.sh \
#     --backup-dir <dir do backup da P0.28> \
#     --tenant-schema <schema do tenant-alvo> \
#     --testemunha-schema <schema do tenant-testemunha> \
#     --instante-alvo <ISO-8601, ex.: 2027-02-01T12:00:00Z> \
#     --consentimento <consentimento.json do tenant-alvo (ADR-0020)> \
#     --lote-run-id <run_id do lote aplicado> \
#     --lote-dir <dir da execucao do lote: run.json + reverter.sql> \
#     [--lote-schema <schema do lote; padrao: o do tenant-alvo>] \
#     [--os-inversa-json <descritor InverseOrderRequest, se o lote tem OS>] \
#     [--keep] [--dry-run]
#
# O ensaio inteiro, numa execucao so, abortando na PRIMEIRA falha:
#   (a) pre-voo: runtime de container (podman -- os scripts da P3.33 o
#       chamam literalmente), dependencias da P3.33 presentes no repo,
#       backup da P0.28 presente e valido, espaco em disco para as bases
#       descartaveis e parametros do ensaio completos;
#   (b) restauracao COMPLETA do backup local da P0.28 (ziipsan.dump) numa
#       base ISOLADA descartavel propria, CRONOMETRADA por etapa e no
#       total. Nunca o banco de trabalho (ziipsan-postgres-primary, 5432),
#       nunca o de integracao (55433), nunca a lateral do PITR da P0.28
#       (55434) nem a da restauracao mensal da P4.7 (55435):
#         principal: ziipsan-ensaio-gl3,        127.0.0.1:55436
#         lateral:   ziipsan-ensaio-gl3-lateral, 127.0.0.1:55437
#       A base isolada chama-se `ziipsan` DENTRO do container descartavel
#       para os scripts da P3.33 (-d ziipsan) funcionarem sem alteracao,
#       apontados por ZIIPSAN_PG_CONTAINER/ZIIPSAN_LATERAL_CONTAINER.
#       A lateral e uma segunda restauracao do MESMO dump: ela representa
#       o instante-alvo quando o ensaio usa o instante do backup. PITR
#       com replay de WAL ate um instante DIVERSO do backup continua
#       sendo o roteiro de scripts/local-pitr.sh (P0.28) -- ver o roteiro
#       da GL.3; este orquestrador nao finge replay de WAL.
#   (c) verificacoes no banco restaurado (SQL proprio em
#       scripts/operacao/sql/): contagem de registros por tabela e
#       checksum SHA-256 por tabela (verificacao-contagem-checksum.sql,
#       o mesmo hash canonico da fotografia da P3.33) e teste de RLS
#       executado COMO ziipsan_app (teste-rls-restaurado.sql: sem
#       contexto -> zero linhas; contexto do tenant -> so as dele;
#       contexto de outro tenant -> zero). A role ziipsan_app NAO vem no
#       dump (roles nao viajam no pg_dump): ela e criada AQUI, so no
#       container descartavel, NOSUPERUSER/NOBYPASSRLS, com senha efemera
#       gerada na execucao e nunca registrada -- e some com o container.
#   (d) restauracao SELETIVA de um tenant ao instante-alvo, pelos scripts
#       da P3.33, sem reimplementar nada: PITR lateral pronta (b),
#       verificar-isolamento-tenant.sh registrar ANTES, sondar DURANTE,
#       restaurar-tenant.sh extrair/comparar/aplicar (REPLACE, com o
#       consentimento do operador), verificar-isolamento conferir DEPOIS
#       e checksum do schema-testemunha antes == depois (SQL da GL.3).
#   (e) desfazer do lote: desfazer-lote.sh da P3.33. Se o lote gerou
#       efeito por OS (run.json com businessEffectOrderId), a remocao de
#       linhas e RECUSADA pelo script de proposito (ADR-0039) e o desfazer
#       e a OS inversa: o ensaio exige o descritor InverseOrderRequest
#       (--os-inversa-json, bulkRunId == run_id), a confirmacao do
#       operador de que a OS inversa foi aberta e encerrada na camada de
#       OS durante o ensaio (GL3_OS_INVERSA_CONFIRMADA=<run_id>) e prova
#       o efeito no banco (verificacao-os-inversa.sql: OS com
#       reverses_order_id = OS de origem, CLOSED_EXECUTED, com efeitos).
#       Sem efeito por OS, o desfazer e a remocao por execucao do proprio
#       desfazer-lote.sh e a prova e a fotografia do schema do lote mudar
#       exatamente pela reversao (checksum antes != depois) + a trilha.
#   (f) limpeza dos containers descartaveis (salvo --keep) e REGISTRO DO
#       ENSAIO preenchido com os valores MEDIDOS nesta execucao, em
#       docs/operacao/ensaios-restauracao/<carimbo>-gl3/registro-ensaio.md.
#
# Tempos e a referencia de 4 h (ADR-0012): cada etapa e cronometrada
# (inicio, fim e duracao) e o total e registrado AO LADO da referencia
# de RTO de 4 h. E registro de ensaio, SEM META: cumprir ou estourar a
# referencia nao aprova nem reprova o ensaio -- a meta vale na LC.12.
#
# Codigos de saida (distintos por etapa; a primeira falha para o ensaio):
#   0  ensaio concluido (registro gravado)
#   2  pre-voo: parametro ausente, backup ausente/invalido, dependencia
#      da P3.33 ausente, runtime ausente ou espaco insuficiente
#   3  restauracao completa (subida da isolada/lateral ou pg_restore)
#   4  verificacao de contagem/checksum no banco restaurado
#   5  teste de RLS no banco restaurado
#   6  restauracao seletiva do tenant ou prova de isolamento (P3.33)
#   7  desfazer do lote / OS inversa
#
# Sem `set -e` de proposito (como na P4.7): toda falha passa pelo caminho
# que grava o registro do ensaio antes de sair. `set -u` e `pipefail` ficam.
# Interrupcao por sinal (SIGINT/SIGTERM) tambem grava o registro e limpa os
# containers descartaveis (trap, correcao M2 da revisao); erro de sintaxe de
# argumentos grava registro minimo de pre-voo (guarda de EXIT, correcao B-b).
# A secao de Desvios do registro acumula os desvios REAIS da execucao
# (add_desvio) e os declarados por quem executa em ZIIPSAN_GL3_DESVIOS (B-c).
set -uo pipefail

SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SQL_DIR="$SRC/scripts/operacao/sql"

# ------------------------------------------- registro de erro de argumentos
# Erro de sintaxe da linha de comando tambem gera registro de FALHA no
# pre-voo (correcao B-b da revisao): os caminhos de saida da leitura de
# opcoes sao anteriores ao fail(), entao uma guarda de EXIT, armada so
# durante a leitura (PARSING=1), grava um registro minimo e sai 2.
STAMP="$(date +%Y-%m-%dT%H:%M:%S%z)"
STAMP_DIR="$(date +%Y%m%d-%H%M%S)"
START_TS=$(date +%s)
REPORT_ROOT="${ZIIPSAN_GL3_REPORT_DIR:-$SRC/docs/operacao/ensaios-restauracao}"
ENSAIO_DIR="$REPORT_ROOT/$STAMP_DIR-gl3"
REPORT_FILE="$ENSAIO_DIR/registro-ensaio.md"
ARG_ERR=""
arg_registro() {
  mkdir -p "$ENSAIO_DIR" 2>/dev/null || return 1
  {
    echo "# Registro do ensaio de restauracao local - GL.3"
    echo
    echo "> Resultado registrado como ENSAIO, sem meta. A referencia de RTO de 4 h"
    echo "> e comparada apenas como registro (ADR-0012); a meta vale na LC.12."
    echo
    echo "- Tarefa: GL.3 (depende de P0.28 - backup local - e P3.33 - restauracao seletiva e desfazer de lote, PLT-03)"
    echo "- Execucao: $STAMP"
    echo "- Resultado: FALHA"
    echo "- Etapa final: pre-voo"
    echo "- Erro: erro de sintaxe de argumentos: ${ARG_ERR:-uso incorreto das opcoes (detalhe no stderr)}"
  } > "$REPORT_FILE"
}
PARSING=1
trap 'if [ "${PARSING:-0}" = "1" ]; then arg_registro || true; exit 2; fi' EXIT

# ---------------------------------------------------------------- opcoes
KEEP=0; DRY_RUN=0
BACKUP_DIR=""; TENANT_SCHEMA=""; TESTEMUNHA_SCHEMA=""; INSTANTE_ALVO=""
CONSENTIMENTO=""; LOTE_RUN_ID=""; LOTE_DIR=""; LOTE_SCHEMA=""; OS_INVERSA_JSON=""
while [ $# -gt 0 ]; do
  case "$1" in
    --keep) KEEP=1 ;;
    --dry-run) DRY_RUN=1 ;;
    --backup-dir) shift; BACKUP_DIR="${1:?--backup-dir exige valor}" ;;
    --backup-dir=*) BACKUP_DIR="${1#--backup-dir=}" ;;
    --tenant-schema) shift; TENANT_SCHEMA="${1:?--tenant-schema exige valor}" ;;
    --tenant-schema=*) TENANT_SCHEMA="${1#--tenant-schema=}" ;;
    --testemunha-schema) shift; TESTEMUNHA_SCHEMA="${1:?--testemunha-schema exige valor}" ;;
    --testemunha-schema=*) TESTEMUNHA_SCHEMA="${1#--testemunha-schema=}" ;;
    --instante-alvo) shift; INSTANTE_ALVO="${1:?--instante-alvo exige valor}" ;;
    --instante-alvo=*) INSTANTE_ALVO="${1#--instante-alvo=}" ;;
    --consentimento) shift; CONSENTIMENTO="${1:?--consentimento exige valor}" ;;
    --consentimento=*) CONSENTIMENTO="${1#--consentimento=}" ;;
    --lote-run-id) shift; LOTE_RUN_ID="${1:?--lote-run-id exige valor}" ;;
    --lote-run-id=*) LOTE_RUN_ID="${1#--lote-run-id=}" ;;
    --lote-dir) shift; LOTE_DIR="${1:?--lote-dir exige valor}" ;;
    --lote-dir=*) LOTE_DIR="${1#--lote-dir=}" ;;
    --lote-schema) shift; LOTE_SCHEMA="${1:?--lote-schema exige valor}" ;;
    --lote-schema=*) LOTE_SCHEMA="${1#--lote-schema=}" ;;
    --os-inversa-json) shift; OS_INVERSA_JSON="${1:?--os-inversa-json exige valor}" ;;
    --os-inversa-json=*) OS_INVERSA_JSON="${1#--os-inversa-json=}" ;;
    -*) ARG_ERR="opcao desconhecida: $1"; echo "ERRO: $ARG_ERR" >&2; exit 2 ;;
    *) ARG_ERR="argumento inesperado: $1 (use as opcoes nomeadas)"; echo "ERRO: $ARG_ERR" >&2; exit 2 ;;
  esac
  shift
done
PARSING=0
LOTE_SCHEMA="${LOTE_SCHEMA:-$TENANT_SCHEMA}"

# ---------------------------------------------------------------- variaveis
CONTAINER="${ZIIPSAN_GL3_CONTAINER:-ziipsan-ensaio-gl3}"
PORT="${ZIIPSAN_GL3_PORT:-55436}"
LATERAL="${ZIIPSAN_GL3_LATERAL_CONTAINER:-ziipsan-ensaio-gl3-lateral}"
LATERAL_PORT="${ZIIPSAN_GL3_LATERAL_PORT:-55437}"
DBNAME="ziipsan"
IMAGE="${ZIIPSAN_GL3_IMAGE:-postgis/postgis:15-3.4}"
RTO_REF_S=14400

# Campos do registro (preenchidos ao longo da execucao; "-" = nao medido).
R_RESULTADO="-"; R_ETAPA="-"; R_ERRO="-"
R_BACKUP_DIR="-"; R_DUMP_BYTES="-"; R_DUMP_SHA256="-"; R_WAL_LSN="-"
R_TABELAS="-"; R_LINHAS="-"; R_CHECKSUM_GLOBAL="-"; R_RLS="-"
R_TENANT_ID="-"; R_TESTEMUNHA_ID="-"; R_SELETIVA="-"; R_ISOLAMENTO="-"
R_LOTE="-"; R_OS_INVERSA="-"; R_LOTE_PROVA="-"
R_DESVIOS="-"
T_PRE="-"; T_RES="-"; T_VER="-"; T_RLS="-"; T_SEL="-"; T_LOTE="-"
CONTAINERS_UP=0
RT=""

now() { date +%s; }
iso() { date +%Y-%m-%dT%H:%M:%S%z; }

write_report() {
  mkdir -p "$ENSAIO_DIR" || return 1
  local total=$(( $(now) - START_TS ))
  local cmp_ref="-"
  if [ "$R_RESULTADO" = "CONCLUIDO" ]; then
    if [ "$total" -le "$RTO_REF_S" ]; then cmp_ref="total de ${total}s, abaixo da referencia de ${RTO_REF_S}s";
    else cmp_ref="total de ${total}s, acima da referencia de ${RTO_REF_S}s"; fi
  fi
  {
    echo "# Registro do ensaio de restauracao local - GL.3"
    echo
    echo "> Resultado registrado como ENSAIO, sem meta. A referencia de RTO de 4 h"
    echo "> e comparada abaixo apenas como registro (ADR-0012); a meta vale na LC.12."
    echo
    echo "- Tarefa: GL.3 (depende de P0.28 - backup local - e P3.33 - restauracao seletiva e desfazer de lote, PLT-03)"
    echo "- Execucao: $STAMP"
    echo "- Ambiente: local (bases descartaveis $CONTAINER:$PORT e $LATERAL:$LATERAL_PORT; banco de trabalho nao tocado)"
    echo "- Resultado: $R_RESULTADO"
    echo "- Etapa final: $R_ETAPA"
    echo "- Erro: $R_ERRO"
    echo
    echo "## Tempos medidos (cronometragem do ensaio)"
    echo
    echo "- pre-voo: $T_PRE"
    echo "- restauracao-completa: $T_RES"
    echo "- verificacao-contagem-checksum: $T_VER"
    echo "- teste-rls: $T_RLS"
    echo "- restauracao-seletiva-tenant: $T_SEL"
    echo "- desfazer-lote-os-inversa: $T_LOTE"
    echo "- Duracao total: ${total}s"
    echo "- Referencia de RTO (sem meta neste ensaio): ${RTO_REF_S}s (4 h) - comparacao registrada: $cmp_ref"
    echo
    echo "## Origem (backup da P0.28)"
    echo
    echo "- Diretorio do backup: $R_BACKUP_DIR"
    echo "- Dump: ziipsan.dump ($R_DUMP_BYTES bytes)"
    echo "- SHA-256 do dump: $R_DUMP_SHA256"
    echo "- LSN do WAL no backup (wal-lsn.txt): $R_WAL_LSN"
    echo
    echo "## Verificacoes no banco restaurado (restauracao completa)"
    echo
    echo "- Tabelas verificadas (fora pg_catalog/information_schema): $R_TABELAS"
    echo "- Registros (soma das contagens por tabela): $R_LINHAS"
    echo "- Checksum global (SHA-256 do TSV de contagem+checksum por tabela): $R_CHECKSUM_GLOBAL"
    echo "- Artefato: contagens-checksum.tsv (neste diretorio do ensaio)"
    echo "- Teste de RLS (como ziipsan_app; sem contexto = 0, contexto proprio = so as linhas dele, contexto alheio = 0): $R_RLS"
    echo
    echo "## Restauracao seletiva de um tenant (P3.33)"
    echo
    echo "- Tenant-alvo: schema $TENANT_SCHEMA (id $R_TENANT_ID)"
    echo "- Instante-alvo: $INSTANTE_ALVO"
    echo "- Tenant-testemunha: schema $TESTEMUNHA_SCHEMA (id $R_TESTEMUNHA_ID)"
    echo "- Restauracao seletiva (extrair/comparar/aplicar REPLACE via restaurar-tenant.sh): $R_SELETIVA"
    echo "- Prova de nao-afetacao dos demais (fotografia da P3.33 conferir + checksum do schema-testemunha antes == depois): $R_ISOLAMENTO"
    echo
    echo "## Desfazer de lote (P3.33)"
    echo
    echo "- Lote (run_id): $LOTE_RUN_ID (diretorio: $LOTE_DIR)"
    echo "- Desfazer via desfazer-lote.sh: $R_LOTE"
    echo "- OS inversa (quando o lote tem efeito por OS, ADR-0039): $R_OS_INVERSA"
    echo "- Prova do efeito: $R_LOTE_PROVA"
    echo
    echo "## Desvios e observacoes"
    echo
    echo "- Desvios registrados nesta execucao: $R_DESVIOS"
    echo "- Desvios declarados por quem executou (ZIIPSAN_GL3_DESVIOS): ${ZIIPSAN_GL3_DESVIOS:--}"
    echo "- Lateral do ensaio: segunda restauracao do mesmo dump (instante-alvo = instante do backup);"
    echo "  PITR com replay de WAL ate instante diverso segue o roteiro de scripts/local-pitr.sh (P0.28)."
  } > "$REPORT_FILE"
}

cleanup_containers() {
  if [ "$CONTAINERS_UP" = "1" ] && [ "$KEEP" != "1" ] && [ -n "$RT" ]; then
    "$RT" rm -f "$CONTAINER" >/dev/null 2>&1 || true
    "$RT" rm -f "$LATERAL" >/dev/null 2>&1 || true
  fi
  if [ "$KEEP" = "1" ] && [ "$CONTAINERS_UP" = "1" ]; then
    echo "AVISO: --keep: containers descartaveis no ar ($CONTAINER:$PORT, $LATERAL:$LATERAL_PORT); remova com: $RT rm -f $CONTAINER $LATERAL"
  fi
}

fail() { # fail <codigo> <etapa> <mensagem>
  local code="$1"; R_ETAPA="$2"; R_ERRO="$3"; R_RESULTADO="FALHA"
  echo "ALERTA GL.3: ensaio falhou na etapa '$R_ETAPA': $R_ERRO" >&2
  write_report || echo "ALERTA GL.3: nem o registro do ensaio pode ser gravado em $REPORT_FILE" >&2
  cleanup_containers
  exit "$code"
}

detect_runtime() {
  if [ -n "${ZIIPSAN_CONTAINER_RUNTIME:-}" ]; then RT="$ZIIPSAN_CONTAINER_RUNTIME"; return 0; fi
  if command -v podman >/dev/null 2>&1; then RT="podman"; return 0; fi
  return 1
}

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | awk '{print $1}';
  elif command -v shasum >/dev/null 2>&1; then shasum -a 256 "$1" | awk '{print $1}';
  else echo "indisponivel"; fi
}

gen_password() {
  if command -v openssl >/dev/null 2>&1; then openssl rand -hex 18;
  else printf '%s-%s' "$(date +%s%N)" "$$" | sha256_of /dev/stdin 2>/dev/null || echo "gl3-$$-$(date +%s)"; fi
}

validar_schema() {
  case "$1" in
    *[!a-z0-9_]*|'') return 1 ;;
    [0-9]*) return 1 ;;
    *) return 0 ;;
  esac
}

validar_instante() {
  case "$1" in
    *[!0-9TZ:.+-]*|'') return 1 ;;
    *) return 0 ;;
  esac
}

q() { # q <container> "<sql>" -> psql -tAc como postgres na base isolada
  "$RT" exec "$1" psql -U postgres -d "$DBNAME" -tAc "$2"
}

run_sql_file() { # run_sql_file <container> <usuario> <arquivo.sql> <saida> [vars psql...]
  local container="$1" usuario="$2" arquivo="$3" saida="$4"; shift 4
  local remoto="/tmp/gl3-$(basename "$arquivo")"
  "$RT" cp "$arquivo" "$container:$remoto" || return 1
  if [ "$usuario" = "ziipsan_app" ]; then
    "$RT" exec -e PGPASSWORD="$APP_PW" "$container" psql -U ziipsan_app -d "$DBNAME" \
      -v ON_ERROR_STOP=1 "$@" -f "$remoto" > "$saida"
  else
    "$RT" exec "$container" psql -U postgres -d "$DBNAME" \
      -v ON_ERROR_STOP=1 "$@" -f "$remoto" > "$saida"
  fi
}

wait_ready() { # wait_ready <container>
  local i
  for i in $(seq 1 60); do
    if "$RT" exec "$1" pg_isready -U postgres -d "$DBNAME" >/dev/null 2>&1; then return 0; fi
    sleep 2
  done
  return 1
}

start_isolated() { # start_isolated <container> <porta> <senha>
  "$RT" rm -f "$1" >/dev/null 2>&1 || true
  "$RT" run -d --name "$1" -p "127.0.0.1:$2:5432" \
    -e POSTGRES_DB="$DBNAME" -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD="$3" \
    "$IMAGE" >/dev/null
}

restore_dump_into() { # restore_dump_into <container> <dump>
  "$RT" cp "$2" "$1:/tmp/ziipsan.dump" || return 1
  "$RT" exec "$1" pg_restore -U postgres -d "$DBNAME" --no-owner --exit-on-error /tmp/ziipsan.dump
}

stage_time() { # stage_time <inicio-epoch> -> "inicio ISO -> fim ISO (Ns)"
  local ini="$1" fim; fim=$(now)
  echo "$(date -d "@$ini" +%Y-%m-%dT%H:%M:%S%z 2>/dev/null || echo $ini) -> $(iso) ($(( fim - ini ))s)"
}

APP_PW=""

add_desvio() { # acumula um desvio REAL da execucao para o registro (B-c)
  if [ "$R_DESVIOS" = "-" ]; then R_DESVIOS="$1"; else R_DESVIOS="$R_DESVIOS; $1"; fi
}

# ------------------------------------------------- interrupcao por sinal (M2)
# SIGINT/SIGTERM no meio do ensaio: grava o registro como FALHA na etapa
# corrente e limpa os containers descartaveis (respeitando --keep, como o
# fail()). Sem este trap, uma interrupcao vazaria os containers 55436/55437
# e perderia o registro. Instalado aqui, depois de write_report/cleanup.
on_interrupt() {
  local nome="$1" codigo="$2"
  R_RESULTADO="FALHA"
  R_ERRO="ensaio interrompido por $nome na etapa $R_ETAPA"
  echo "ALERTA GL.3: ensaio interrompido por $nome na etapa '$R_ETAPA' - gravando registro e limpando containers" >&2
  write_report || true
  cleanup_containers
  exit "$codigo"
}
trap 'on_interrupt SIGINT 130' INT
trap 'on_interrupt SIGTERM 143' TERM

# ---------------------------------------------------------------- dry-run
if [ "$DRY_RUN" = "1" ]; then
  echo "PLANO (dry-run, nada executado) - ensaio de restauracao local GL.3:"
  echo "  backup da P0.28: ${BACKUP_DIR:-<nao informado>}"
  echo "  destino isolado: $CONTAINER (127.0.0.1:$PORT) + lateral $LATERAL (127.0.0.1:$LATERAL_PORT), base $DBNAME, imagem $IMAGE"
  echo "  etapas: pre-voo -> restauracao completa cronometrada -> contagem/checksum (SQL) -> RLS como ziipsan_app (SQL)"
  echo "          -> restauracao seletiva P3.33 (tenant ${TENANT_SCHEMA:-?} ao instante ${INSTANTE_ALVO:-?}; testemunha ${TESTEMUNHA_SCHEMA:-?})"
  echo "          -> desfazer lote ${LOTE_RUN_ID:-?} (desfazer-lote.sh; OS inversa se houver efeito por OS) -> limpeza + registro"
  echo "  registro: $REPORT_FILE"
  echo "  referencia (sem meta; a meta vale na LC.12): RTO de referencia ${RTO_REF_S}s (4 h)"
  exit 0
fi

# ---------------------------------------------------------------- (a) pre-voo
R_ETAPA="pre-voo"; T0=$(now)
[ -n "$BACKUP_DIR" ] || fail 2 "pre-voo" "--backup-dir e obrigatorio (diretorio do backup da P0.28)"
[ -n "$TENANT_SCHEMA" ] || fail 2 "pre-voo" "--tenant-schema e obrigatorio (schema do tenant-alvo do ensaio)"
[ -n "$TESTEMUNHA_SCHEMA" ] || fail 2 "pre-voo" "--testemunha-schema e obrigatorio (schema do tenant-testemunha)"
[ -n "$INSTANTE_ALVO" ] || fail 2 "pre-voo" "--instante-alvo e obrigatorio (ISO-8601)"
[ -n "$CONSENTIMENTO" ] || fail 2 "pre-voo" "--consentimento e obrigatorio (arquivo do consentimento do tenant-alvo, ADR-0020)"
[ -n "$LOTE_RUN_ID" ] || fail 2 "pre-voo" "--lote-run-id e obrigatorio (run_id do lote aplicado a desfazer)"
[ -n "$LOTE_DIR" ] || fail 2 "pre-voo" "--lote-dir e obrigatorio (diretorio da execucao do lote: run.json + reverter.sql)"
validar_schema "$TENANT_SCHEMA" || fail 2 "pre-voo" "schema do tenant-alvo invalido: '$TENANT_SCHEMA'"
validar_schema "$TESTEMUNHA_SCHEMA" || fail 2 "pre-voo" "schema do tenant-testemunha invalido: '$TESTEMUNHA_SCHEMA'"
validar_schema "$LOTE_SCHEMA" || fail 2 "pre-voo" "schema do lote invalido: '$LOTE_SCHEMA'"
[ "$TENANT_SCHEMA" != "$TESTEMUNHA_SCHEMA" ] || fail 2 "pre-voo" "tenant-alvo e tenant-testemunha precisam ser schemas diferentes"
validar_instante "$INSTANTE_ALVO" || fail 2 "pre-voo" "instante-alvo invalido (esperado ISO-8601): '$INSTANTE_ALVO'"
case "$LOTE_RUN_ID" in
  *[!a-f0-9-]*) fail 2 "pre-voo" "run_id do lote invalido (esperado UUID): '$LOTE_RUN_ID'" ;;
esac
[ -d "$BACKUP_DIR" ] || fail 2 "pre-voo" "diretorio de backup nao encontrado: $BACKUP_DIR (rode scripts/local-backup.sh da P0.28 antes)"
DUMP="$BACKUP_DIR/ziipsan.dump"
[ -s "$DUMP" ] || fail 2 "pre-voo" "dump ausente ou vazio: $DUMP"
[ -f "$CONSENTIMENTO" ] || fail 2 "pre-voo" "arquivo de consentimento nao encontrado: $CONSENTIMENTO"
[ -f "$LOTE_DIR/run.json" ] || fail 2 "pre-voo" "run.json ausente em $LOTE_DIR (contrato da execucao do lote, P3.33)"
[ -f "$LOTE_DIR/reverter.sql" ] || fail 2 "pre-voo" "reverter.sql ausente em $LOTE_DIR (imagem-anterior da execucao, P3.33)"
for dep in restaurar-tenant.sh desfazer-lote.sh verificar-isolamento-tenant.sh; do
  [ -f "$SRC/scripts/operacao/$dep" ] || fail 2 "pre-voo" "dependencia da P3.33 ausente no repo: scripts/operacao/$dep"
done
for sqlf in verificacao-contagem-checksum.sql teste-rls-restaurado.sql verificacao-os-inversa.sql; do
  [ -f "$SQL_DIR/$sqlf" ] || fail 2 "pre-voo" "SQL do ensaio ausente: scripts/operacao/sql/$sqlf"
done
detect_runtime || fail 2 "pre-voo" "podman nao encontrado (os scripts da P3.33 chamam podman; defina ZIIPSAN_CONTAINER_RUNTIME se o seu podman tiver outro nome)"
R_BACKUP_DIR="$BACKUP_DIR"
R_DUMP_BYTES="$(wc -c < "$DUMP" | tr -d ' ')"
R_DUMP_SHA256="$(sha256_of "$DUMP")"
[ -f "$BACKUP_DIR/wal-lsn.txt" ] && R_WAL_LSN="$(cat "$BACKUP_DIR/wal-lsn.txt")"
# Espaco: medido no sistema de arquivos do repositorio, onde ficam o
# registro, os artefatos e a extracao da P3.33 (ZIIPSAN_RESTAURACAO_DIR e
# apontado para o diretorio do ensaio). O armazenamento de imagens e
# volumes do runtime de container e do proprio runtime. Estimativa:
# dump x6 (copias + duas bases restauradas, com folga) + 256 MB.
NEED_KB=$(( (R_DUMP_BYTES / 1024 + 1) * 6 + 262144 ))
AVAIL_KB="$(df -Pk "$SRC" | awk 'NR==2 {print $4}')"
case "$AVAIL_KB" in ''|*[!0-9]*) AVAIL_KB=0 ;; esac
[ "$AVAIL_KB" -ge "$NEED_KB" ] || fail 2 "pre-voo" "espaco insuficiente no sistema de arquivos do repositorio: disponivel ${AVAIL_KB} KB, necessario ~${NEED_KB} KB (dump x6 + 256 MB de folga)"
T_PRE="$(stage_time "$T0")"
echo "pre-voo OK: backup $DUMP ($R_DUMP_BYTES bytes, sha256 $R_DUMP_SHA256); espaco ${AVAIL_KB} KB livres"

# ---------------------------------------------------------------- (b) restauracao completa cronometrada
R_ETAPA="restauracao-completa"; T0=$(now)
PW="$(gen_password)"; APP_PW="$(gen_password)"
start_isolated "$CONTAINER" "$PORT" "$PW" || fail 3 "restauracao-completa" "falha ao subir o container principal $CONTAINER (porta $PORT ocupada?)"
CONTAINERS_UP=1
start_isolated "$LATERAL" "$LATERAL_PORT" "$PW" || fail 3 "restauracao-completa" "falha ao subir o container lateral $LATERAL (porta $LATERAL_PORT ocupada?)"
wait_ready "$CONTAINER" || fail 3 "restauracao-completa" "PostgreSQL principal descartavel nao ficou pronto em 120s"
wait_ready "$LATERAL" || fail 3 "restauracao-completa" "PostgreSQL lateral descartavel nao ficou pronto em 120s"
# A role da aplicacao nao viaja no dump: criada so no descartavel, antes do
# pg_restore (para os GRANTs do dump valerem), sem BYPASSRLS, senha efemera.
if [ "$(q "$CONTAINER" "SELECT count(*) FROM pg_roles WHERE rolname = 'ziipsan_app'")" != "1" ]; then
  "$RT" exec "$CONTAINER" psql -U postgres -d "$DBNAME" -v ON_ERROR_STOP=1 -c \
    "CREATE ROLE ziipsan_app LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE PASSWORD '$APP_PW'" >/dev/null \
    || fail 3 "restauracao-completa" "falha ao criar a role ziipsan_app no container descartavel"
  add_desvio "role ziipsan_app nao existia no banco restaurado (roles nao viajam no pg_dump); criada apenas no container descartavel, com senha efemera"
fi
restore_dump_into "$CONTAINER" "$DUMP" || fail 3 "restauracao-completa" "pg_restore falhou na base isolada principal (ver saida acima)"
restore_dump_into "$LATERAL" "$DUMP" || fail 3 "restauracao-completa" "pg_restore falhou na lateral do ensaio (ver saida acima)"
add_desvio "lateral do ensaio e a segunda restauracao do mesmo dump (instante-alvo = instante do backup, sem replay de WAL)"
T_RES="$(stage_time "$T0")"
echo "restauracao completa concluida: $T_RES"

# ---------------------------------------------------------------- (c1) contagem + checksum
R_ETAPA="verificacao-contagem-checksum"; T0=$(now)
mkdir -p "$ENSAIO_DIR" || fail 4 "verificacao-contagem-checksum" "nao foi possivel criar o diretorio do ensaio: $ENSAIO_DIR"
run_sql_file "$CONTAINER" postgres "$SQL_DIR/verificacao-contagem-checksum.sql" \
  "$ENSAIO_DIR/contagens-checksum.tsv" -v schema_filtro=% \
  || fail 4 "verificacao-contagem-checksum" "SQL de contagem/checksum falhou no banco restaurado"
R_TABELAS="$(grep -c . "$ENSAIO_DIR/contagens-checksum.tsv" || true)"
case "$R_TABELAS" in ''|*[!0-9]*) R_TABELAS=0 ;; esac
[ "$R_TABELAS" -gt 0 ] || fail 4 "verificacao-contagem-checksum" "banco restaurado sem nenhuma tabela - restauracao vazia nao e sucesso"
R_LINHAS="$(awk -F '\t' '{s += $3} END {print s+0}' "$ENSAIO_DIR/contagens-checksum.tsv")"
R_CHECKSUM_GLOBAL="$(sha256_of "$ENSAIO_DIR/contagens-checksum.tsv")"
T_VER="$(stage_time "$T0")"
echo "verificacao OK: $R_TABELAS tabelas, $R_LINHAS registros, checksum global $R_CHECKSUM_GLOBAL"

# ---------------------------------------------------------------- (c2) teste de RLS
R_ETAPA="teste-rls"; T0=$(now)
R_TENANT_ID="$(q "$CONTAINER" "SELECT id FROM public.tenant_catalog WHERE schema_name = '$TENANT_SCHEMA'")"
R_TESTEMUNHA_ID="$(q "$CONTAINER" "SELECT id FROM public.tenant_catalog WHERE schema_name = '$TESTEMUNHA_SCHEMA'")"
[ -n "$R_TENANT_ID" ] || fail 5 "teste-rls" "tenant-alvo nao encontrado no catalogo do banco restaurado (schema $TENANT_SCHEMA)"
[ -n "$R_TESTEMUNHA_ID" ] || fail 5 "teste-rls" "tenant-testemunha nao encontrado no catalogo do banco restaurado (schema $TESTEMUNHA_SCHEMA)"
run_sql_file "$CONTAINER" ziipsan_app "$SQL_DIR/teste-rls-restaurado.sql" \
  "$ENSAIO_DIR/teste-rls.tsv" -v schema_alvo="$TENANT_SCHEMA" -v tenant_alvo="$R_TENANT_ID" -v tenant_outro="$R_TESTEMUNHA_ID" \
  || fail 5 "teste-rls" "SQL do teste de RLS falhou (ver $ENSAIO_DIR/teste-rls.tsv e a saida do psql)"
if grep -q "RLS_RESUMO.*OK" "$ENSAIO_DIR/teste-rls.tsv"; then
  R_RLS="OK (resumo RLS_RESUMO=OK em teste-rls.tsv)"
elif grep -q "RLS_RESUMO.*INCONCLUSIVO" "$ENSAIO_DIR/teste-rls.tsv"; then
  fail 5 "teste-rls" "teste de RLS inconclusivo: nenhuma tabela com RLS e massa no schema do tenant-alvo (ensaio sem massa nao e prova)"
else
  fail 5 "teste-rls" "teste de RLS reprovado no banco restaurado (ver teste-rls.tsv)"
fi
T_RLS="$(stage_time "$T0")"
echo "teste de RLS OK no banco restaurado (como ziipsan_app)"

# ---------------------------------------------------------------- (d) restauracao seletiva (P3.33)
R_ETAPA="restauracao-seletiva-tenant"; T0=$(now)
export ZIIPSAN_PG_CONTAINER="$CONTAINER"
export ZIIPSAN_LATERAL_CONTAINER="$LATERAL"
RESTORE_WORK="${ZIIPSAN_GL3_WORK_DIR:-$ENSAIO_DIR/p3.33}"
mkdir -p "$RESTORE_WORK"
export ZIIPSAN_RESTAURACAO_DIR="$RESTORE_WORK"
# Fotografia + checksum da testemunha ANTES (a prova de nao-afetacao).
bash "$SRC/scripts/operacao/verificar-isolamento-tenant.sh" registrar "$TESTEMUNHA_SCHEMA" \
  "$ENSAIO_DIR/testemunha-antes.tsv" \
  || fail 6 "restauracao-seletiva-tenant" "verificar-isolamento (registrar) falhou antes da restauracao seletiva"
run_sql_file "$CONTAINER" postgres "$SQL_DIR/verificacao-contagem-checksum.sql" \
  "$ENSAIO_DIR/testemunha-checksum-antes.tsv" -v schema_filtro="$TESTEMUNHA_SCHEMA" \
  || fail 6 "restauracao-seletiva-tenant" "checksum da testemunha (antes) falhou"
bash "$SRC/scripts/operacao/restaurar-tenant.sh" extrair "$TENANT_SCHEMA" "$INSTANTE_ALVO" \
  || fail 6 "restauracao-seletiva-tenant" "restaurar-tenant.sh extrair falhou (lateral no instante-alvo?)"
EXTRACTION_DIR="$(ls -d "$RESTORE_WORK/$TENANT_SCHEMA"/*/ 2>/dev/null | sort | tail -n 1)"
[ -n "$EXTRACTION_DIR" ] || fail 6 "restauracao-seletiva-tenant" "diretorio da extracao da P3.33 nao encontrado em $RESTORE_WORK/$TENANT_SCHEMA"
bash "$SRC/scripts/operacao/restaurar-tenant.sh" comparar "$TENANT_SCHEMA" "$EXTRACTION_DIR" \
  || fail 6 "restauracao-seletiva-tenant" "restaurar-tenant.sh comparar falhou"
# DURANTE a restauracao: a testemunha le e grava (sonda da P3.33).
bash "$SRC/scripts/operacao/verificar-isolamento-tenant.sh" sondar "$TESTEMUNHA_SCHEMA" \
  || fail 6 "restauracao-seletiva-tenant" "sonda de isolamento falhou durante a restauracao seletiva"
APLICAR_RESTAURACAO="$TENANT_SCHEMA" CONSENTIMENTO_JSON="$CONSENTIMENTO" \
  bash "$SRC/scripts/operacao/restaurar-tenant.sh" aplicar "$TENANT_SCHEMA" "$EXTRACTION_DIR" REPLACE \
  || fail 6 "restauracao-seletiva-tenant" "restaurar-tenant.sh aplicar (REPLACE) falhou ou foi recusado (consentimento/janela/hash)"
R_SELETIVA="OK (extrair/comparar/aplicar REPLACE; extracao em $EXTRACTION_DIR)"
# Prova de nao-afetacao DEPOIS: fotografia da P3.33 + checksum da GL.3.
bash "$SRC/scripts/operacao/verificar-isolamento-tenant.sh" conferir "$TESTEMUNHA_SCHEMA" \
  "$ENSAIO_DIR/testemunha-antes.tsv" \
  || fail 6 "restauracao-seletiva-tenant" "ISOLAMENTO REPROVADO pela fotografia da P3.33: tabelas da testemunha mudaram"
run_sql_file "$CONTAINER" postgres "$SQL_DIR/verificacao-contagem-checksum.sql" \
  "$ENSAIO_DIR/testemunha-checksum-depois.tsv" -v schema_filtro="$TESTEMUNHA_SCHEMA" \
  || fail 6 "restauracao-seletiva-tenant" "checksum da testemunha (depois) falhou"
if diff -q "$ENSAIO_DIR/testemunha-checksum-antes.tsv" "$ENSAIO_DIR/testemunha-checksum-depois.tsv" >/dev/null; then
  R_ISOLAMENTO="OK (fotografia P3.33 conferir OK; checksum da testemunha antes == depois)"
else
  fail 6 "restauracao-seletiva-tenant" "ISOLAMENTO REPROVADO pelo checksum da GL.3: contagem/checksum da testemunha mudou (diff em testemunha-checksum-antes/depois.tsv)"
fi
T_SEL="$(stage_time "$T0")"
echo "restauracao seletiva OK: tenant $TENANT_SCHEMA restaurado ao instante $INSTANTE_ALVO; testemunha $TESTEMUNHA_SCHEMA intacta"

# ---------------------------------------------------------------- (e) desfazer lote / OS inversa (P3.33)
R_ETAPA="desfazer-lote-os-inversa"; T0=$(now)
run_sql_file "$CONTAINER" postgres "$SQL_DIR/verificacao-contagem-checksum.sql" \
  "$ENSAIO_DIR/lote-checksum-antes.tsv" -v schema_filtro="$LOTE_SCHEMA" \
  || fail 7 "desfazer-lote-os-inversa" "checksum do schema do lote (antes) falhou"
LOTE_TEM_OS=0
if grep -Eq '"businessEffectOrderId": *"' "$LOTE_DIR/run.json"; then LOTE_TEM_OS=1; fi
UNDO_OUT="$ENSAIO_DIR/desfazer-lote.out"
if bash "$SRC/scripts/operacao/desfazer-lote.sh" "$LOTE_RUN_ID" "$LOTE_DIR" > "$UNDO_OUT" 2>&1; then
  UNDO_RC=0
else
  UNDO_RC=$?
fi
if [ "$LOTE_TEM_OS" = "1" ]; then
  # Caminho da OS inversa: a recusa da remocao de linhas e ESPERADA e faz
  # parte da prova; sem ela, algo esta errado no contrato da P3.33.
  if [ "$UNDO_RC" = "0" ]; then
    fail 7 "desfazer-lote-os-inversa" "lote tem efeito por OS, mas desfazer-lote.sh NAO recusou a remocao de linhas (esperada a recusa do ADR-0039)"
  fi
  grep -q "OS inversa" "$UNDO_OUT" || fail 7 "desfazer-lote-os-inversa" "desfazer-lote.sh recusou o lote, mas sem a justificativa de OS inversa (ver desfazer-lote.out)"
  R_LOTE="remocao de linhas recusada como manda o ADR-0039 (efeito por OS) - ver desfazer-lote.out"
  [ -n "$OS_INVERSA_JSON" ] && [ -f "$OS_INVERSA_JSON" ] \
    || fail 7 "desfazer-lote-os-inversa" "lote com efeito por OS exige --os-inversa-json (descritor InverseOrderRequest da P3.33)"
  grep -Eq "\"bulkRunId\": *\"$LOTE_RUN_ID\"" "$OS_INVERSA_JSON" \
    || fail 7 "desfazer-lote-os-inversa" "descritor da OS inversa com bulkRunId diverso do run_id do lote"
  OS_ORIGEM="$(grep -oE '"businessEffectOrderId": *"[^"]*"' "$LOTE_DIR/run.json" | head -n 1 | cut -d'"' -f4)"
  grep -Eq "\"originalOrderId\": *\"$OS_ORIGEM\"" "$OS_INVERSA_JSON" \
    || fail 7 "desfazer-lote-os-inversa" "descritor da OS inversa com originalOrderId diverso do businessEffectOrderId do lote ($OS_ORIGEM)"
  # Conferencia COMPLETA do descritor (correcao B-e): todos os campos
  # obrigatorios do record InverseOrderRequest da P3.33, nao so os de
  # ligacao -- tenantId contra o run.json do lote e presenca de
  # reason/requestedBy/requestedAt (requestedBy como UUID).
  TENANT_LOTE="$(grep -oE '"tenantId": *"[^"]*"' "$LOTE_DIR/run.json" | head -n 1 | cut -d'"' -f4)"
  [ -n "$TENANT_LOTE" ] || fail 7 "desfazer-lote-os-inversa" "run.json do lote sem tenantId"
  grep -Eq "\"tenantId\": *\"$TENANT_LOTE\"" "$OS_INVERSA_JSON" \
    || fail 7 "desfazer-lote-os-inversa" "descritor da OS inversa com tenantId diverso do tenant do lote ($TENANT_LOTE)"
  for campo in reason requestedBy requestedAt; do
    grep -Eq "\"$campo\": *\"[^\"]+\"" "$OS_INVERSA_JSON" \
      || fail 7 "desfazer-lote-os-inversa" "descritor da OS inversa sem o campo obrigatorio $campo (record InverseOrderRequest da P3.33)"
  done
  REQ_BY="$(grep -oE '"requestedBy": *"[^"]*"' "$OS_INVERSA_JSON" | head -n 1 | cut -d'"' -f4)"
  case "$REQ_BY" in
    *[!a-f0-9-]*|'') fail 7 "desfazer-lote-os-inversa" "requestedBy do descritor nao e um UUID: '$REQ_BY'" ;;
  esac
  add_desvio "lote com efeito por OS: desfazer pela OS inversa (ADR-0039), com confirmacao do operador (GL3_OS_INVERSA_CONFIRMADA)"
  [ "${GL3_OS_INVERSA_CONFIRMADA:-}" = "$LOTE_RUN_ID" ] \
    || fail 7 "desfazer-lote-os-inversa" "OS inversa nao confirmada: abra e encerre a OS inversa na camada de OS e rode com GL3_OS_INVERSA_CONFIRMADA=$LOTE_RUN_ID"
  run_sql_file "$CONTAINER" postgres "$SQL_DIR/verificacao-os-inversa.sql" \
    "$ENSAIO_DIR/os-inversa.tsv" -v schema_lote="$LOTE_SCHEMA" -v os_origem="$OS_ORIGEM" \
    || fail 7 "desfazer-lote-os-inversa" "SQL de verificacao da OS inversa falhou"
  IFS="$(printf '\t')" read -r N_OS_INV N_EFEITOS < "$ENSAIO_DIR/os-inversa.tsv" || true
  case "$N_OS_INV" in ''|*[!0-9]*) N_OS_INV=0 ;; esac
  case "$N_EFEITOS" in ''|*[!0-9]*) N_EFEITOS=0 ;; esac
  [ "$N_OS_INV" -ge 1 ] && [ "$N_EFEITOS" -ge 1 ] \
    || fail 7 "desfazer-lote-os-inversa" "prova da OS inversa insuficiente no banco: OS inversas encerradas=$N_OS_INV, efeitos=$N_EFEITOS (esperado >= 1 e >= 1)"
  R_OS_INVERSA="OK (descritor conferido; OS inversa da OS $OS_ORIGEM encerrada com $N_EFEITOS efeito(s) aplicado(s))"
  R_LOTE_PROVA="verificacao-os-inversa.sql: $N_OS_INV OS inversa(s) CLOSED_EXECUTED com reverses_order_id=$OS_ORIGEM e $N_EFEITOS efeito(s) em service_order_effects"
else
  if [ "$UNDO_RC" != "0" ]; then
    fail 7 "desfazer-lote-os-inversa" "desfazer-lote.sh falhou para o lote sem efeito por OS (ver desfazer-lote.out)"
  fi
  R_LOTE="OK (remocao por execucao via desfazer-lote.sh)"
  add_desvio "lote sem efeito por OS: desfazer por remocao por execucao (reverter.sql da P3.33)"
  R_OS_INVERSA="nao aplicavel (lote sem efeito de negocio por OS)"
  run_sql_file "$CONTAINER" postgres "$SQL_DIR/verificacao-contagem-checksum.sql" \
    "$ENSAIO_DIR/lote-checksum-depois.tsv" -v schema_filtro="$LOTE_SCHEMA" \
    || fail 7 "desfazer-lote-os-inversa" "checksum do schema do lote (depois) falhou"
  if diff -q "$ENSAIO_DIR/lote-checksum-antes.tsv" "$ENSAIO_DIR/lote-checksum-depois.tsv" >/dev/null; then
    fail 7 "desfazer-lote-os-inversa" "desfazer nao deixou efeito mensuravel: checksum do schema do lote antes == depois"
  fi
  R_LOTE_PROVA="checksum do schema do lote mudou com a reversao (lote-checksum-antes.tsv != lote-checksum-depois.tsv) e a reversao foi registrada em public.audit_log pelo desfazer-lote.sh"
fi
T_LOTE="$(stage_time "$T0")"
echo "desfazer do lote OK: $R_LOTE"

# ---------------------------------------------------------------- (f) registro + limpeza
[ "$KEEP" = "1" ] && add_desvio "containers descartaveis mantidos no ar a pedido do operador (--keep)"
R_RESULTADO="CONCLUIDO"; R_ETAPA="concluida"; R_ERRO="-"
write_report || { echo "ALERTA GL.3: falha ao gravar o registro do ensaio em $REPORT_FILE" >&2; cleanup_containers; exit 7; }
cleanup_containers
echo "OK: ensaio GL.3 concluido (registro: $REPORT_FILE)"
echo "tempos: total $(( $(now) - START_TS ))s contra a referencia de ${RTO_REF_S}s (4 h) - registro de ensaio, sem meta (a meta vale na LC.12)"
exit 0
