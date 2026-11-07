#!/bin/bash
# P4.7: restauracao mensal automatizada do backup local (P0.28), em base ISOLADA.
#
#   scripts/monthly-restore.sh [--force] [--keep] [--dry-run] [diretorio-do-backup]
#
# O que faz (modo local, padrao):
#   1. localiza o backup mais recente da P0.28 (scripts/local-backup.sh): o
#      argumento, $ZIIPSAN_BACKUP_DIR ou o ziipsan-backup-* mais novo em
#      $ZIIPSAN_BACKUP_ROOT (padrao /var/tmp, o padrao do local-backup.sh);
#   2. sobe um PostgreSQL DESCARTAVEL proprio (container ziipsan-restore-mensal,
#      porta 127.0.0.1:55435, base ziipsan_restaurado_mensal) - NUNCA o banco de
#      trabalho (ziipsan, 5432), NUNCA o banco de integracao (55433, lib.sh) e
#      fora da porta do roteiro de PITR da P0.28 (55434);
#   3. restaura o ziipsan.dump (pg_restore --exit-on-error) na base isolada;
#   4. verifica: contagem de tabelas e contagem de linhas por tabela MEDIDAS
#      NO BANCO RESTAURADO (nao ha baseline no dump: a P0.28 nao grava
#      contagens nem checksum esperado - o SHA-256 calculado aqui e a
#      impressao digital registrada no relatorio, nao uma conferencia contra
#      valor esperado; o portao de integridade e o pg_restore --exit-on-error
#      mais tabelas > 0) e presenca de politicas RLS no restaurado
#      (o TESTE de isolamento RLS continua sendo o do ensaio P0.28/GL.3 - ver o
#      relatorio e o runbook docs/operacao/restauracao-mensal.md);
#   5. grava o relatorio mensal em docs/operacao/restauracoes-mensais/<AAAA-MM>.md
#      (duracao total e por fase + resultado) e derruba o container descartavel.
#
# Idempotencia: se o relatorio do mes ja existe com "Resultado: SUCESSO", a
# rotina sai com 0 sem restaurar de novo; --force executa outra vez. Um
# container descartavel de uma execucao abortada e removido no inicio.
#
# Falha e alerta: o projeto NAO tem mecanismo de e-mail/webhook para scripts de
# operacao (conferido nos scripts existentes: o alerta do ci.sh e saida + codigo
# de saida). O alerta aqui segue o mesmo padrao, reforcado:
#   - codigo de saida != 0 (tabela abaixo) - e o que o agendador (cron/systemd/
#     Agendador de Tarefas) usa para sinalizar a falha;
#   - linha "ALERTA P4.7: ..." no stderr;
#   - arquivo docs/operacao/restauracoes-mensais/ALERTA-RESTAURACAO-MENSAL.md
#     com data, fase e erro (apagado na proxima execucao com sucesso);
#   - relatorio do mes gravado com "Resultado: FALHA" e a fase que falhou.
#
# Codigos de saida:
#   0  sucesso (ou mes ja restaurado com sucesso, sem --force)
#   2  pre-condicao: backup nao encontrado/invalido ou runtime ausente
#   3  restauracao ou verificacao falhou (relatorio + alerta gravados)
#   4  modo producao (RDS) solicitado: rotina NAO executada - a mesma rotina so
#      roda sobre o backup do RDS a partir da LC.3 (ver --source rds --dry-run
#      para o plano). Nao e falha do ambiente local: nao gera arquivo de alerta.
#
# Producao (a partir da LC.3): ZIIPSAN_RESTORE_SOURCE=rds, com
# ZIIPSAN_RDS_SNAPSHOT_ID ou ZIIPSAN_RDS_SOURCE_INSTANCE. Nesta fase o modo rds
# apenas documenta o plano (--dry-run) e recusa executar (saida 4): a rotina de
# producao restaura o snapshot/PITR do RDS numa instancia isolada
# (ziipsan-restore-mensal) e roda as MESMAS verificacoes e o MESMO relatorio.
#
# Referencias de tempo (ADR-0012): RPO de 15 min e RTO de 4 h sao REFERENCIA
# local, sem meta antes do lancamento - a duracao e registrada no relatorio,
# nao cobrada como falha. A meta vale na LC.3/LC.12.
#
# Sem `set -e` de proposito: toda falha precisa passar pelo caminho que grava
# relatorio e alerta antes de sair. `set -u` e `pipefail` ficam.
set -uo pipefail

SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# ---------------------------------------------------------------- opcoes
FORCE=0; KEEP=0; DRY_RUN=0; BACKUP_ARG=""
SOURCE_MODE="${ZIIPSAN_RESTORE_SOURCE:-local}"
while [ $# -gt 0 ]; do
  case "$1" in
    --force) FORCE=1 ;;
    --keep) KEEP=1 ;;
    --dry-run) DRY_RUN=1 ;;
    --source) shift; SOURCE_MODE="${1:?--source exige local|rds}" ;;
    --source=*) SOURCE_MODE="${1#--source=}" ;;
    -*) echo "ERRO: opcao desconhecida: $1" >&2; exit 2 ;;
    *) BACKUP_ARG="$1" ;;
  esac
  shift
done

# ---------------------------------------------------------------- variaveis
MONTH="$(date +%Y-%m)"
STAMP="$(date +%Y-%m-%dT%H:%M:%S%z)"
START_TS=$(date +%s)
REPORT_DIR="${ZIIPSAN_RESTORE_REPORT_DIR:-$SRC/docs/operacao/restauracoes-mensais}"
REPORT_FILE="$REPORT_DIR/$MONTH.md"
ALERT_FILE="$REPORT_DIR/ALERTA-RESTAURACAO-MENSAL.md"
CONTAINER="${ZIIPSAN_RESTORE_CONTAINER:-ziipsan-restore-mensal}"
PORT="${ZIIPSAN_RESTORE_PORT:-55435}"
DBNAME="${ZIIPSAN_RESTORE_DB:-ziipsan_restaurado_mensal}"
IMAGE="${ZIIPSAN_RESTORE_IMAGE:-postgis/postgis:15-3.4}"
BACKUP_ROOT="${ZIIPSAN_BACKUP_ROOT:-/var/tmp}"

# Campos do relatorio (preenchidos ao longo da execucao).
R_RESULTADO="-"; R_FASE="-"; R_ERRO="-"
R_BACKUP_DIR="-"; R_DUMP_BYTES="-"; R_DUMP_SHA256="-"; R_WAL_LSN="-"
R_TABELAS="-"; R_LINHAS="-"; R_RLS_POLICIES="-"; R_RLS_TABLES="-"; R_VERSAO="-"
D_PRE=0; D_RESTORE=0; D_CHECK=0
CONTAINER_UP=0
RT=""

now() { date +%s; }

write_report() {
  mkdir -p "$REPORT_DIR"
  local total=$(( $(now) - START_TS ))
  {
    echo "# Restauracao mensal automatizada - $MONTH (P4.7)"
    echo
    echo "- Tarefa: P4.7 (origem ADR-0012; depende da P0.28, backup local)"
    echo "- Execucao: $STAMP"
    echo "- Modo: $SOURCE_MODE"
    echo "- Resultado: $R_RESULTADO"
    echo "- Fase final: $R_FASE"
    echo "- Erro: $R_ERRO"
    echo "- Duracao total: ${total}s (preparacao ${D_PRE}s, restauracao ${D_RESTORE}s, verificacao ${D_CHECK}s)"
    echo "- Referencia (sem meta local, ADR-0012): RTO de referencia 4 h; RPO 15 min"
    echo
    echo "## Origem (backup da P0.28)"
    echo
    echo "- Diretorio do backup: $R_BACKUP_DIR"
    echo "- Dump: ziipsan.dump ($R_DUMP_BYTES bytes)"
    echo "- SHA-256 do dump: $R_DUMP_SHA256"
    echo "- LSN do WAL no backup (wal-lsn.txt): $R_WAL_LSN"
    echo
    echo "## Destino isolado"
    echo
    echo "- Container descartavel: $CONTAINER (imagem $IMAGE)"
    echo "- Endereco: 127.0.0.1:$PORT - base: $DBNAME"
    echo "- Banco de trabalho (ziipsan, 5432): nao tocado"
    echo "- PostgreSQL restaurado: $R_VERSAO"
    echo
    echo "## Verificacoes no banco restaurado"
    echo
    echo "- Restauracao (pg_restore --exit-on-error): ver Resultado/Fase acima"
    echo "- Tabelas (fora pg_catalog/information_schema): $R_TABELAS"
    echo "- Linhas (contagem exata, soma por tabela): $R_LINHAS"
    echo "- Checksum do dump conferido antes da restauracao: $R_DUMP_SHA256"
    echo "- Tabelas com RLS habilitada no restaurado: $R_RLS_TABLES; politicas RLS: $R_RLS_POLICIES"
    echo "- Teste de isolamento RLS: NAO EXECUTADO nesta rotina - a instancia"
    echo "  descartavel sobe sem as roles de aplicacao do init (infra/postgres/init),"
    echo "  entao nao ha como conectar como ziipsan_app. O teste de isolamento RLS"
    echo "  (sem contexto: zero linhas; com contexto do tenant: so as dele) e o do"
    echo "  ensaio da P0.28/GL.3 (scripts/local-pitr.sh). Aqui registra-se apenas a"
    echo "  presenca das politicas no restaurado, sem afirmar isolamento."
  } > "$REPORT_FILE"
}

write_alert() {
  mkdir -p "$REPORT_DIR"
  {
    echo "# ALERTA - restauracao mensal falhou (P4.7)"
    echo
    echo "- Quando: $STAMP (mes de referencia: $MONTH)"
    echo "- Fase: $R_FASE"
    echo "- Erro: $R_ERRO"
    echo "- Relatorio: docs/operacao/restauracoes-mensais/$MONTH.md"
    echo
    echo "Este arquivo e o alerta da rotina enquanto o projeto nao tem mecanismo"
    echo "de e-mail/webhook para scripts de operacao. Ele e apagado pela proxima"
    echo "execucao com sucesso. O agendador tambem recebe a falha pelo codigo de"
    echo "saida != 0 de scripts/monthly-restore.sh."
  } > "$ALERT_FILE"
}

cleanup_container() {
  if [ "$CONTAINER_UP" = "1" ] && [ "$KEEP" != "1" ] && [ -n "$RT" ]; then
    "$RT" rm -f "$CONTAINER" >/dev/null 2>&1 || true
  fi
  if [ "$KEEP" = "1" ] && [ "$CONTAINER_UP" = "1" ]; then
    echo "AVISO: --keep: container descartavel $CONTAINER continua no ar em 127.0.0.1:$PORT (remova com: $RT rm -f $CONTAINER)"
  fi
}

fail() { # fail <codigo> <fase> <mensagem>
  local code="$1"; R_FASE="$2"; R_ERRO="$3"; R_RESULTADO="FALHA"
  echo "ALERTA P4.7: restauracao mensal falhou na fase '$R_FASE': $R_ERRO" >&2
  write_report
  write_alert
  cleanup_container
  exit "$code"
}

detect_runtime() {
  if [ -n "${ZIIPSAN_CONTAINER_RUNTIME:-}" ]; then RT="$ZIIPSAN_CONTAINER_RUNTIME"; return 0; fi
  if command -v podman >/dev/null 2>&1; then RT="podman"; return 0; fi
  if command -v docker >/dev/null 2>&1; then RT="docker"; return 0; fi
  return 1
}

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | awk '{print $1}';
  elif command -v shasum >/dev/null 2>&1; then shasum -a 256 "$1" | awk '{print $1}';
  else echo "indisponivel"; fi
}

gen_password() {
  if command -v openssl >/dev/null 2>&1; then openssl rand -hex 18;
  else printf '%s-%s' "$(date +%s%N)" "$$" | sha256_of /dev/stdin 2>/dev/null || echo "restore-$$-$(date +%s)"; fi
}

q() { # q "<sql>" -> stdout do psql na base isolada
  "$RT" exec "$CONTAINER" psql -U postgres -d "$DBNAME" -tAc "$1"
}

# ---------------------------------------------------------------- modo RDS
if [ "$SOURCE_MODE" = "rds" ]; then
  if [ "$DRY_RUN" = "1" ]; then
    echo "PLANO (dry-run, nada executado) - restauracao mensal sobre o RDS (producao, a partir da LC.3):"
    echo "  1. aws rds restore-db-instance-from-db-snapshot --db-instance-identifier ziipsan-restore-mensal"
    echo "       --db-snapshot-identifier ${ZIIPSAN_RDS_SNAPSHOT_ID:-<snapshot mais recente do RDS>} (ou restore por PITR"
    echo "       sobre ${ZIIPSAN_RDS_SOURCE_INSTANCE:-<instancia de producao>}), sempre em sa-east-1 (ADR-0011/RNF-44);"
    echo "  2. aguardar a instancia isolada ficar available (nunca restaurar sobre a instancia de producao);"
    echo "  3. rodar as MESMAS verificacoes desta rotina (contagem, checksum quando houver dump logico, RLS);"
    echo "  4. gravar o MESMO relatorio mensal em docs/operacao/restauracoes-mensais/<AAAA-MM>.md;"
    echo "  5. apagar a instancia isolada ao fim."
    exit 0
  fi
  # Correcao da revisao cruzada (P4.7): a recusa do modo rds NAO pode apagar um
  # relatorio de SUCESSO do mesmo mes (destruiria a prova e a idempotencia).
  if [ -f "$REPORT_FILE" ] && grep -q "^- Resultado: SUCESSO" "$REPORT_FILE"; then
    echo "AVISO P4.7: modo rds solicitado antes da LC.3 - nada foi executado (saida 4). Relatorio de SUCESSO de $MONTH preservado. Use --source rds --dry-run para o plano." >&2
    exit 4
  fi
  R_RESULTADO="NAO EXECUTADO"; R_FASE="producao (RDS)"
  R_ERRO="a rotina de producao so roda sobre o backup do RDS a partir da LC.3; nesta fase nada foi restaurado"
  echo "AVISO P4.7: modo rds solicitado antes da LC.3 - nada foi executado (saida 4). Use --source rds --dry-run para o plano." >&2
  write_report
  exit 4
fi
[ "$SOURCE_MODE" = "local" ] || { echo "ERRO: --source deve ser local ou rds (veio: $SOURCE_MODE)" >&2; exit 2; }

# ---------------------------------------------------------------- dry-run local
if [ "$DRY_RUN" = "1" ]; then
  echo "PLANO (dry-run, nada executado) - restauracao mensal local:"
  echo "  backup: ${BACKUP_ARG:-${ZIIPSAN_BACKUP_DIR:-o ziipsan-backup-* mais novo em $BACKUP_ROOT}}"
  echo "  destino isolado: container $CONTAINER, 127.0.0.1:$PORT, base $DBNAME (imagem $IMAGE)"
  echo "  relatorio: $REPORT_FILE"
  echo "  verificacoes: pg_restore --exit-on-error, contagem de tabelas/linhas, SHA-256 do dump, politicas RLS"
  exit 0
fi

# ---------------------------------------------------------------- idempotencia
if [ "$FORCE" != "1" ] && [ -f "$REPORT_FILE" ] && grep -q "^- Resultado: SUCESSO" "$REPORT_FILE"; then
  echo "restauracao mensal de $MONTH ja executada com sucesso (relatorio: $REPORT_FILE); nada a fazer. Use --force para repetir."
  exit 0
fi

# ---------------------------------------------------------------- pre-condicoes
T0=$(now)
BACKUP_DIR_RESOLVED=""
if [ -n "$BACKUP_ARG" ]; then BACKUP_DIR_RESOLVED="$BACKUP_ARG";
elif [ -n "${ZIIPSAN_BACKUP_DIR:-}" ]; then BACKUP_DIR_RESOLVED="$ZIIPSAN_BACKUP_DIR";
else
  # O local-backup.sh grava em /var/tmp/ziipsan-backup-<timestamp> por padrao.
  BACKUP_DIR_RESOLVED="$(ls -d "$BACKUP_ROOT"/ziipsan-backup-* 2>/dev/null | sort | tail -n 1 || true)"
fi
[ -n "$BACKUP_DIR_RESOLVED" ] && [ -d "$BACKUP_DIR_RESOLVED" ] \
  || fail 2 "pre-condicao" "diretorio de backup nao encontrado (argumento, ZIIPSAN_BACKUP_DIR ou ziipsan-backup-* em $BACKUP_ROOT)"
DUMP="$BACKUP_DIR_RESOLVED/ziipsan.dump"
[ -s "$DUMP" ] || fail 2 "pre-condicao" "dump ausente ou vazio: $DUMP (rode scripts/local-backup.sh antes)"
detect_runtime || fail 2 "pre-condicao" "nem podman nem docker encontrados (defina ZIIPSAN_CONTAINER_RUNTIME)"
R_BACKUP_DIR="$BACKUP_DIR_RESOLVED"
R_DUMP_BYTES="$(wc -c < "$DUMP" | tr -d ' ')"
R_DUMP_SHA256="$(sha256_of "$DUMP")"
[ -f "$BACKUP_DIR_RESOLVED/wal-lsn.txt" ] && R_WAL_LSN="$(cat "$BACKUP_DIR_RESOLVED/wal-lsn.txt")"
D_PRE=$(( $(now) - T0 ))
echo "backup: $DUMP ($R_DUMP_BYTES bytes, sha256 $R_DUMP_SHA256)"

# ---------------------------------------------------------------- sobe a isolada
T0=$(now)
PW="$(gen_password)"
"$RT" rm -f "$CONTAINER" >/dev/null 2>&1 || true
if ! "$RT" run -d --name "$CONTAINER" -p "127.0.0.1:${PORT}:5432" \
      -e POSTGRES_DB="$DBNAME" -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD="$PW" \
      "$IMAGE" >/dev/null; then
  fail 3 "subida da base isolada" "falha ao subir o container $CONTAINER (porta $PORT ocupada?)"
fi
CONTAINER_UP=1
READY=0
for _ in $(seq 1 60); do
  if "$RT" exec "$CONTAINER" pg_isready -U postgres -d "$DBNAME" >/dev/null 2>&1; then READY=1; break; fi
  sleep 2
done
[ "$READY" = "1" ] || fail 3 "subida da base isolada" "PostgreSQL descartavel nao ficou pronto em 120s"

# ---------------------------------------------------------------- restaura
"$RT" cp "$DUMP" "$CONTAINER:/tmp/ziipsan.dump" \
  || fail 3 "restauracao" "falha ao copiar o dump para o container"
if ! "$RT" exec "$CONTAINER" pg_restore -U postgres -d "$DBNAME" --no-owner --exit-on-error /tmp/ziipsan.dump; then
  fail 3 "restauracao" "pg_restore falhou (ver saida acima)"
fi
D_RESTORE=$(( $(now) - T0 ))
echo "restauracao concluida em ${D_RESTORE}s na base isolada $DBNAME (127.0.0.1:$PORT)"

# ---------------------------------------------------------------- verificacoes
T0=$(now)
R_VERSAO="$(q "SELECT version()" 2>/dev/null | head -n 1 || echo "-")"
R_TABELAS="$(q "SELECT count(*) FROM pg_tables WHERE schemaname NOT IN ('pg_catalog','information_schema')" || true)"
case "$R_TABELAS" in ''|*[!0-9]*) fail 3 "verificacao" "contagem de tabelas nao retornou numero (veio: '$R_TABELAS')" ;; esac
[ "$R_TABELAS" -gt 0 ] || fail 3 "verificacao" "banco restaurado sem nenhuma tabela - restauracao vazia nao e sucesso"
TOTAL_LINHAS=0
while IFS='|' read -r ESQ TAB; do
  [ -n "$ESQ" ] && [ -n "$TAB" ] || continue
  N="$(q "SELECT count(*) FROM \"$ESQ\".\"$TAB\"" || echo "ERRO")"
  case "$N" in ''|*[!0-9]*) fail 3 "verificacao" "contagem de linhas falhou em $ESQ.$TAB (veio: '$N')" ;; esac
  TOTAL_LINHAS=$(( TOTAL_LINHAS + N ))
done <<EOF
$(q "SELECT schemaname||'|'||tablename FROM pg_tables WHERE schemaname NOT IN ('pg_catalog','information_schema') ORDER BY 1")
EOF
R_LINHAS="$TOTAL_LINHAS"
R_RLS_POLICIES="$(q "SELECT count(*) FROM pg_policies" || echo "-")"
R_RLS_TABLES="$(q "SELECT count(*) FROM pg_tables WHERE rowsecurity" || echo "-")"
D_CHECK=$(( $(now) - T0 ))

# ---------------------------------------------------------------- sucesso
R_RESULTADO="SUCESSO"; R_FASE="concluida"; R_ERRO="-"
write_report
rm -f "$ALERT_FILE"
cleanup_container
echo "OK: restauracao mensal de $MONTH restaurada e verificada em base isolada ($R_TABELAS tabelas, $R_LINHAS linhas)."
echo "relatorio: $REPORT_FILE"
exit 0
