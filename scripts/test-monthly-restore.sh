#!/bin/bash
# Teste do scripts/monthly-restore.sh (P4.7) SEM Docker/Postgres de verdade:
# um runtime falso (podman stub) entra no PATH e responde as chamadas que a
# rotina faz (run, exec pg_isready/pg_restore/psql, cp, rm), com respostas
# conhecidas. Assim o teste confere o comportamento da ROTINA - codigos de
# saida, relatorio, arquivo de alerta e idempotencia - em qualquer maquina com
# bash, inclusive no PC do dono antes de agendar. A restauracao real, com
# container de verdade, e o ensaio mensal no ambiente local (ver runbook).
#
#   scripts/test-monthly-restore.sh
#
# Sai 0 se todos os casos passam; 1 se algum falha. Nenhum caso afrouxa
# assercao: cada um compara codigo de saida E artefato (relatorio/alerta).
set -uo pipefail
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="$SRC/scripts/monthly-restore.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
PASS=0; FAILN=0

ok()   { PASS=$((PASS+1));  echo "ok - $1"; }
bad()  { FAILN=$((FAILN+1)); echo "FALHOU - $1" >&2; }
check(){ # check <descricao> <condicao...>
  local desc="$1"; shift
  if "$@" >/dev/null 2>&1; then ok "$desc"; else bad "$desc"; fi
}

# ------------------------------------------------------------ runtime falso
mkdir -p "$TMP/bin"
cat > "$TMP/bin/podman" <<'STUB'
#!/bin/bash
# Stub do runtime de container para o teste da P4.7. Responde pelo conteudo
# da chamada; STUB_FAIL_RESTORE=1 faz o pg_restore falhar.
ALL=" $*"
case "$ALL" in
  *" rm "*) exit 0 ;;
  *" run "*) echo "stub-container-id"; exit 0 ;;
  *" cp "*) exit 0 ;;
  *pg_isready*) exit 0 ;;
  *pg_restore*) [ "${STUB_FAIL_RESTORE:-0}" = "1" ] && { echo "stub: pg_restore falhou" >&2; exit 1; }; exit 0 ;;
  *psql*)
    case "$ALL" in
      *"count(*) FROM pg_tables WHERE"*) echo "7" ;;
      *"schemaname||'|'||tablename"*) printf 'public|t1\npublic|t2\n' ;;
      *'FROM "public"."t1"'*) echo "10" ;;
      *'FROM "public"."t2"'*) echo "20" ;;
      *"count(*) FROM pg_policies"*) echo "2" ;;
      *"WHERE rowsecurity"*) echo "1" ;;
      *"SELECT version()"*) echo "PostgreSQL 15 (stub do teste)" ;;
      *) echo "0" ;;
    esac
    exit 0 ;;
  *) echo "stub: chamada nao reconhecida: $ALL" >&2; exit 1 ;;
esac
STUB
chmod +x "$TMP/bin/podman"

# ------------------------------------------------------------ massa do teste
BK="$TMP/backup"; mkdir -p "$BK"
printf 'dump-falso-do-teste-p47' > "$BK/ziipsan.dump"
echo "12345/67890" > "$BK/wal-lsn.txt"
REPORTS="$TMP/relatorios"
export ZIIPSAN_RESTORE_REPORT_DIR="$REPORTS"
export PATH="$TMP/bin:$PATH"
MONTH="$(date +%Y-%m)"
unset ZIIPSAN_CONTAINER_RUNTIME ZIIPSAN_BACKUP_DIR || true

run_case() { bash "$SCRIPT" "$@" >"$TMP/out.txt" 2>"$TMP/err.txt"; echo $?; }

# 1. dry-run local: plano impresso, nada gravado, saida 0
RC="$(run_case --dry-run "$BK")"
[ "$RC" = "0" ]; check "dry-run local sai 0" test $? -eq 0
check "dry-run nao grava relatorio" test ! -e "$REPORTS/$MONTH.md"

# 2. backup ausente: saida 2, relatorio de FALHA e arquivo de alerta
RC="$(run_case "$TMP/backup-que-nao-existe")"
[ "$RC" = "2" ]; check "backup ausente sai 2" test $? -eq 0
check "backup ausente grava relatorio FALHA" grep -q "^- Resultado: FALHA" "$REPORTS/$MONTH.md"
check "backup ausente cria arquivo de alerta" test -f "$REPORTS/ALERTA-RESTAURACAO-MENSAL.md"

# 3. caminho feliz com o stub: saida 0, SUCESSO, contagens 7 tabelas/30 linhas,
#    checksum presente, alerta removido
RC="$(run_case "$BK")"
[ "$RC" = "0" ]; check "caminho feliz sai 0" test $? -eq 0
check "relatorio de sucesso" grep -q "^- Resultado: SUCESSO" "$REPORTS/$MONTH.md"
check "relatorio traz 7 tabelas" grep -q "Tabelas (fora pg_catalog/information_schema): 7" "$REPORTS/$MONTH.md"
check "relatorio traz 30 linhas (10+20)" grep -q "Linhas (contagem exata, soma por tabela): 30" "$REPORTS/$MONTH.md"
check "relatorio traz o SHA-256 do dump" grep -q "^- SHA-256 do dump: [0-9a-f]" "$REPORTS/$MONTH.md"
check "relatorio traz o LSN do backup" grep -q "12345/67890" "$REPORTS/$MONTH.md"
check "sucesso apaga o arquivo de alerta" test ! -f "$REPORTS/ALERTA-RESTAURACAO-MENSAL.md"

# 4. idempotencia: mesmo mes, sem --force, sai 0 sem rodar o runtime de novo
RC="$(run_case "$BK")"
[ "$RC" = "0" ]; check "idempotencia sai 0" test $? -eq 0
check "idempotencia avisa que ja executou" grep -q "ja executada com sucesso" "$TMP/out.txt"

# 5. falha no pg_restore (com --force): saida 3, FALHA na fase restauracao, alerta
RC="$(STUB_FAIL_RESTORE=1 run_case --force "$BK")"
[ "$RC" = "3" ]; check "falha de pg_restore sai 3" test $? -eq 0
check "relatorio marca a fase restauracao" grep -q "^- Fase final: restauracao" "$REPORTS/$MONTH.md"
check "falha recria o arquivo de alerta" test -f "$REPORTS/ALERTA-RESTAURACAO-MENSAL.md"
check "stderr traz a linha ALERTA P4.7" grep -q "ALERTA P4.7" "$TMP/err.txt"

# 6. modo RDS sem dry-run: saida 4, NAO EXECUTADO, sem fingir restauracao
RC="$(ZIIPSAN_RESTORE_SOURCE=rds run_case)"
[ "$RC" = "4" ]; check "modo rds sai 4" test $? -eq 0
check "relatorio rds diz NAO EXECUTADO" grep -q "^- Resultado: NAO EXECUTADO" "$REPORTS/$MONTH.md"

# 7. modo RDS com dry-run: plano impresso, saida 0
RC="$(run_case --source rds --dry-run)"
[ "$RC" = "0" ]; check "rds --dry-run sai 0" test $? -eq 0
check "rds --dry-run mostra restore-db-instance" grep -q "restore-db-instance-from-db-snapshot" "$TMP/out.txt"

echo
echo "test-monthly-restore: $PASS passaram, $FAILN falharam"
[ "$FAILN" -eq 0 ]
