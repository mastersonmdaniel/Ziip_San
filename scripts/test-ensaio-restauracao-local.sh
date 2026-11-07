#!/bin/bash
# Teste do scripts/operacao/ensaio-restauracao-local.sh (GL.3) SEM
# Docker/Postgres de verdade, no padrao do test-monthly-restore.sh (P4.7).
# Inclui verificacao ESTATICA dos SQL entregues (correcao B1 da revisao):
# nenhuma variavel psql (:'...' ou :"...") pode aparecer dentro de bloco
# DO $$...$$ -- o psql nao interpola variaveis em corpos dollar-quoted e,
# em PostgreSQL real, as etapas de verificacao falhariam sempre.
# um runtime falso (podman stub) entra no PATH e responde as chamadas que o
# orquestrador faz (run, exec pg_isready/pg_restore/psql, cp, rm), e os
# scripts da P3.33 sao substituidos por stubs que registram a ordem das
# chamadas e simulam sucesso/falha/recusa. O teste confere o COMPORTAMENTO
# do orquestrador -- ordem das etapas, aborto na primeira falha, codigos de
# saida por etapa, cronometragem e campos do registro do ensaio. O ensaio
# real, com containers de verdade, roda no ambiente local do dono
# (docs/operacao/ROTEIRO-ENSAIO-RESTAURACAO-LOCAL-GL3.md).
#
#   scripts/test-ensaio-restauracao-local.sh
#
# Sai 0 se todos os casos passam; 1 se algum falha. Nenhum caso afrouxa
# assercao: cada um compara codigo de saida E artefato (registro/stub log).
set -uo pipefail
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
PASS=0; FAILN=0

ok()   { PASS=$((PASS+1));  echo "ok - $1"; }
bad()  { FAILN=$((FAILN+1)); echo "FALHOU - $1" >&2; }
check(){ # check <descricao> <condicao...>
  local desc="$1"; shift
  if "$@" >/dev/null 2>&1; then ok "$desc"; else bad "$desc"; fi
}

# ------------------------------------------------- arvore falsa do repo
REPO="$TMP/repo"
mkdir -p "$REPO/scripts/operacao/sql" "$REPO/docs"
cp "$SRC/scripts/operacao/ensaio-restauracao-local.sh" "$REPO/scripts/operacao/"
cp "$SRC/scripts/operacao/sql/"*.sql "$REPO/scripts/operacao/sql/"
SCRIPT="$REPO/scripts/operacao/ensaio-restauracao-local.sh"

# Stubs dos scripts da P3.33 (mesmas assinaturas; registram a chamada).
cat > "$REPO/scripts/operacao/restaurar-tenant.sh" <<'STUB'
#!/bin/bash
echo "P3.33 restaurar-tenant $*" >> "$STUB_LOG"
[ "${STUB_FAIL_RESTAURAR:-0}" = "1" ] && { echo "stub: restaurar-tenant falhou" >&2; exit 1; }
if [ "$1" = "extrair" ]; then
  DEST="${ZIIPSAN_RESTAURACAO_DIR:?}/$2/20270101-000000"
  mkdir -p "$DEST"
  echo "extraido" > "$DEST/fotografia-extraida.tsv"
fi
exit 0
STUB
cat > "$REPO/scripts/operacao/verificar-isolamento-tenant.sh" <<'STUB'
#!/bin/bash
echo "P3.33 verificar-isolamento $*" >> "$STUB_LOG"
[ "${STUB_FAIL_ISOLAMENTO:-0}" = "1" ] && { echo "stub: isolamento reprovado" >&2; exit 1; }
if [ "$1" = "registrar" ]; then printf 'tabela_stub\t1\thashstub\n' > "$3"; fi
echo "isolamento OK (stub)"
exit 0
STUB
cat > "$REPO/scripts/operacao/desfazer-lote.sh" <<'STUB'
#!/bin/bash
echo "P3.33 desfazer-lote $*" >> "$STUB_LOG"
if grep -Eq '"businessEffectOrderId": *"' "$2/run.json"; then
  echo "ERRO: a execucao tem efeito de negocio por OS - a correcao e a OS inversa (ADR-0039), nao a remocao de linhas. Nada foi desfeito." >&2
  exit 1
fi
[ "${STUB_FAIL_DESFAZER:-0}" = "1" ] && { echo "stub: desfazer falhou" >&2; exit 1; }
# A reversao muda o "banco": a marca faz o stub de runtime devolver outro
# checksum para o schema do lote na verificacao DEPOIS do desfazer.
touch "$STUB_LOTE_DEPOIS"
echo "execucao desfeita (stub)"
exit 0
STUB
chmod +x "$REPO/scripts/operacao/"*.sh

# ------------------------------------------------------------ runtime falso
mkdir -p "$TMP/bin"
cat > "$TMP/bin/podman" <<'STUB'
#!/bin/bash
# Stub do runtime para o teste da GL.3. STUB_FAIL_RESTORE=1 faz o
# pg_restore falhar; STUB_RLS=FALHOU|INCONCLUSIVO muda o resumo do RLS;
# STUB_COUNT=empty faz a verificacao nao devolver tabela alguma;
# STUB_LOTE_EFEITO muda o checksum "depois" do lote (a reversao teve efeito).
ALL=" $*"
echo "podman $*" >> "$STUB_LOG"
case "$ALL" in
  *" rm "*) exit 0 ;;
  *" run "*) echo "stub-container-id"; exit 0 ;;
  *" cp "*) exit 0 ;;
  *pg_isready*) exit 0 ;;
  *pg_restore*) [ "${STUB_SLEEP_RESTORE:-0}" != "0" ] && sleep "$STUB_SLEEP_RESTORE"; [ "${STUB_FAIL_RESTORE:-0}" = "1" ] && { echo "stub: pg_restore falhou" >&2; exit 1; }; exit 0 ;;
  *psql*)
    case "$ALL" in
      *"teste-rls-restaurado.sql"*)
        printf 'tabela_rls\t0\t5\t0\tOK\n'
        printf 'RLS_RESUMO\t%s\n' "${STUB_RLS:-OK}"
        ;;
      *"verificacao-os-inversa.sql"*)
        if [ "${STUB_OSINV:-ok}" = "insuficiente" ]; then printf '0\t0\n'; else printf '1\t2\n'; fi
        ;;
      *"verificacao-contagem-checksum.sql"*)
        if [ "${STUB_COUNT:-ok}" = "empty" ]; then
          : # nenhuma tabela: restauracao vazia
        elif echo "$ALL" | grep -q "schema_filtro=tenant_testemunha"; then
          if [ "${STUB_TESTEMUNHA_DIVERGE:-0}" = "1" ] && [ -f "$STUB_MARCA_TESTEMUNHA" ]; then
            printf 'tenant_testemunha\tt1\t3\thash-testemunha-depois\n'
          else
            [ "${STUB_TESTEMUNHA_DIVERGE:-0}" = "1" ] && touch "$STUB_MARCA_TESTEMUNHA"
            printf 'tenant_testemunha\tt1\t3\thash-testemunha\n'
          fi
        elif echo "$ALL" | grep -q "schema_filtro=tenant_alvo"; then
          # O checksum do schema do lote muda depois da reversao.
          if [ -f "$STUB_LOTE_DEPOIS" ]; then printf 'tenant_alvo\tt1\t2\thash-depois\n';
          else printf 'tenant_alvo\tt1\t5\thash-antes\n'; fi
        else
          printf 'public\tt1\t10\thash-t1\n'
          printf 'tenant_alvo\tt2\t20\thash-t2\n'
        fi
        ;;
      *"tenant_catalog"*"tenant_testemunha"*) echo "22222222-2222-2222-2222-222222222222" ;;
      *"tenant_catalog"*) echo "11111111-1111-1111-1111-111111111111" ;;
      *"pg_roles"*) echo "1" ;;
      *) echo "0" ;;
    esac
    exit 0 ;;
  *) echo "stub: chamada nao reconhecida: $ALL" >&2; exit 1 ;;
esac
STUB
chmod +x "$TMP/bin/podman"
export PATH="$TMP/bin:$PATH"
export STUB_LOG="$TMP/stub.log"
export STUB_LOTE_DEPOIS="$TMP/lote-depois.marca"
export STUB_MARCA_TESTEMUNHA="$TMP/marca-testemunha"
# O desfazer de verdade muda o banco; o stub de runtime nao tem banco.
# Quem simula esse efeito e o stub do desfazer-lote.sh: ao desfazer com
# sucesso ele cria a marca $STUB_LOTE_DEPOIS, e dai em diante o stub de
# runtime devolve outro checksum para o schema do lote -- a cadeia causal
# antes -> desfazer -> depois fica de pe. run_case apaga a marca a cada caso.

# ------------------------------------------------------------ massa do teste
BK="$TMP/backup"; mkdir -p "$BK"
printf 'dump-falso-do-teste-gl3' > "$BK/ziipsan.dump"
echo "12345/67890" > "$BK/wal-lsn.txt"
CONS="$TMP/consentimento.json"
printf '{"tenantId": "11111111-1111-1111-1111-111111111111", "targetInstant": "2027-02-01T12:00:00Z", "mode": "REPLACE"}\n' > "$CONS"
RUN_ID="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
LOTE="$TMP/lote"; mkdir -p "$LOTE"
printf '{"runId": "%s", "tenantId": "11111111-1111-1111-1111-111111111111", "status": "APPLIED"}\n' "$RUN_ID" > "$LOTE/run.json"
echo "-- imagem-anterior do lote (stub)" > "$LOTE/reverter.sql"
LOTE_OS="$TMP/lote-os"; mkdir -p "$LOTE_OS"
OS_ID="33333333-3333-3333-3333-333333333333"
printf '{"runId": "%s", "tenantId": "11111111-1111-1111-1111-111111111111", "status": "APPLIED", "businessEffectOrderId": "%s"}\n' "$RUN_ID" "$OS_ID" > "$LOTE_OS/run.json"
echo "-- lote com efeito por OS: reverter.sql nao e usado (ADR-0039)" > "$LOTE_OS/reverter.sql"
OSINV="$TMP/os-inversa.json"
printf '{"tenantId": "11111111-1111-1111-1111-111111111111", "originalOrderId": "%s", "bulkRunId": "%s", "reason": "ensaio GL.3", "requestedBy": "44444444-4444-4444-4444-444444444444", "requestedAt": "2027-02-01T11:00:00Z", "kind": "INVERSE_SERVICE_ORDER (ADR-0039)"}\n' "$OS_ID" "$RUN_ID" > "$OSINV"
OSINV_BAD="$TMP/os-inversa-sem-reason.json"
printf '{"tenantId": "11111111-1111-1111-1111-111111111111", "originalOrderId": "%s", "bulkRunId": "%s", "requestedBy": "44444444-4444-4444-4444-444444444444", "requestedAt": "2027-02-01T11:00:00Z"}\n' "$OS_ID" "$RUN_ID" > "$OSINV_BAD"
REPORTS="$TMP/relatorios"
export ZIIPSAN_GL3_REPORT_DIR="$REPORTS"
export ZIIPSAN_GL3_WORK_DIR="$TMP/work"
unset ZIIPSAN_CONTAINER_RUNTIME || true

ultimo_registro() { ls -d "$REPORTS"/*-gl3/registro-ensaio.md 2>/dev/null | sort | tail -n 1; }

absent() { ! grep -q "$1" "$2"; } # padrao NAO aparece no arquivo

run_case() { # roda o orquestrador e devolve o codigo de saida
  : > "$STUB_LOG"
  rm -f "$STUB_LOTE_DEPOIS" "$STUB_MARCA_TESTEMUNHA"
  bash "$SCRIPT" "$@" >"$TMP/out.txt" 2>"$TMP/err.txt"; echo $?
}
ARGS_BASE=(--backup-dir "$BK" --tenant-schema tenant_alvo --testemunha-schema tenant_testemunha
  --instante-alvo 2027-02-01T12:00:00Z --consentimento "$CONS" --lote-run-id "$RUN_ID" --lote-dir "$LOTE")

# 0. ESTATICA B1: nenhuma variavel psql dentro de bloco DO $$...$$ nos SQL
#    entregues (o psql nao interpola em corpo dollar-quoted; em PostgreSQL
#    real as etapas falhariam sempre) + a ponte via GUC tem de existir.
sql_sem_var_em_do() { # $1 = arquivo SQL (linhas de comentario -- ignoradas:
  # os cabecalhos citam o padrao proibido em texto, fora de qualquer bloco)
  python3 - "$1" <<'PYEOF'
import re, sys
lines = [l for l in open(sys.argv[1]).read().splitlines()
         if not l.lstrip().startswith("--")]
txt = "\n".join(lines)
blocks = re.findall(r"DO \$\$(.*?)\$\$;", txt, re.S)
sys.exit(1 if any(re.search(r":['\"]", b) for b in blocks) else 0)
PYEOF
}
check "B1: verificacao-contagem-checksum.sql sem variavel psql dentro de DO" sql_sem_var_em_do "$SRC/scripts/operacao/sql/verificacao-contagem-checksum.sql"
check "B1: teste-rls-restaurado.sql sem variavel psql dentro de DO" sql_sem_var_em_do "$SRC/scripts/operacao/sql/teste-rls-restaurado.sql"
check "B1: verificacao-os-inversa.sql sem variavel psql dentro de DO" sql_sem_var_em_do "$SRC/scripts/operacao/sql/verificacao-os-inversa.sql"
check "B1: ponte GUC presente na contagem/checksum (set_config fora + current_setting dentro)" bash -c "grep -q \"set_config('gl3.schema_filtro'\" '$SRC/scripts/operacao/sql/verificacao-contagem-checksum.sql' && grep -q \"current_setting('gl3.schema_filtro')\" '$SRC/scripts/operacao/sql/verificacao-contagem-checksum.sql'"
check "B1: ponte GUC presente no RLS (current_setting dos 3 parametros)" bash -c "grep -q \"current_setting('gl3.schema_alvo')\" '$SRC/scripts/operacao/sql/teste-rls-restaurado.sql' && grep -q \"current_setting('gl3.tenant_alvo')\" '$SRC/scripts/operacao/sql/teste-rls-restaurado.sql' && grep -q \"current_setting('gl3.tenant_outro')\" '$SRC/scripts/operacao/sql/teste-rls-restaurado.sql'"
check "B-d: filtro de schema por igualdade (sem LIKE sobre o parametro)" bash -c "! grep -Eq 'LIKE[[:space:]]+(v_filtro|current_setting|:)' '$SRC/scripts/operacao/sql/verificacao-contagem-checksum.sql'"

# 1. dry-run: plano impresso, nada executado, saida 0, nenhum registro
RC="$(run_case --dry-run "${ARGS_BASE[@]}")"
[ "$RC" = "0" ]; check "dry-run sai 0" test $? -eq 0
check "dry-run mostra a referencia de 4 h sem meta" grep -q "sem meta" "$TMP/out.txt"
check "dry-run nao chama o runtime" test ! -s "$STUB_LOG"
check "dry-run nao grava registro" test -z "$(ultimo_registro)"

# 2. sem backup presente: saida 2 (pre-voo), registro de FALHA, nada restaurado
RC="$(run_case --backup-dir "$TMP/backup-que-nao-existe" --tenant-schema tenant_alvo --testemunha-schema tenant_testemunha --instante-alvo 2027-02-01T12:00:00Z --consentimento "$CONS" --lote-run-id "$RUN_ID" --lote-dir "$LOTE")"
[ "$RC" = "2" ]; check "backup ausente sai 2" test $? -eq 0
check "backup ausente grava registro FALHA no pre-voo" grep -q "^- Resultado: FALHA" "$(ultimo_registro)"
check "registro marca a etapa pre-voo" grep -q "^- Etapa final: pre-voo" "$(ultimo_registro)"
check "backup ausente nao tenta restaurar" absent "pg_restore" "$STUB_LOG"

# 3. parametro do ensaio ausente (sem --lote-dir): saida 2
RC="$(run_case --backup-dir "$BK" --tenant-schema tenant_alvo --testemunha-schema tenant_testemunha --instante-alvo 2027-02-01T12:00:00Z --consentimento "$CONS" --lote-run-id "$RUN_ID")"
[ "$RC" = "2" ]; check "sem --lote-dir sai 2" test $? -eq 0

# 4. caminho feliz completo (lote sem OS): saida 0 e registro com TODOS os
#    campos do aceite preenchidos com os valores dos stubs
RC="$(run_case "${ARGS_BASE[@]}")"
REG="$(ultimo_registro)"
[ "$RC" = "0" ]; check "caminho feliz sai 0" test $? -eq 0
check "registro CONCLUIDO" grep -q "^- Resultado: CONCLUIDO" "$REG"
check "registro declara ensaio sem meta (meta na LC.12)" grep -q "a meta vale na LC.12" "$REG"
check "registro traz o SHA-256 do dump" grep -q "^- SHA-256 do dump: [0-9a-f]" "$REG"
check "registro traz 2 tabelas verificadas" grep -q "Tabelas verificadas (fora pg_catalog/information_schema): 2" "$REG"
check "registro traz 30 registros (10+20)" grep -q "Registros (soma das contagens por tabela): 30" "$REG"
check "registro traz checksum global" grep -q "Checksum global (SHA-256 do TSV" "$REG"
check "registro traz RLS OK" grep -q "Teste de RLS (como ziipsan_app" "$REG"
check "registro traz restauracao seletiva OK" grep -q "Restauracao seletiva (extrair/comparar/aplicar REPLACE" "$REG"
check "registro traz isolamento OK" grep -q "Prova de nao-afetacao dos demais" "$REG"
check "registro traz desfazer do lote OK" grep -q "Desfazer via desfazer-lote.sh: OK" "$REG"
check "registro cronometra a restauracao-completa" grep -q "^- restauracao-completa: .* ([0-9]*s)" "$REG"
check "registro cronometra a restauracao seletiva" grep -q "^- restauracao-seletiva-tenant: .* ([0-9]*s)" "$REG"
check "registro traz duracao total e referencia de 4 h" grep -q "Referencia de RTO (sem meta neste ensaio): 14400s (4 h)" "$REG"
check "artefato de contagens/checksum gravado ao lado do registro" test -f "$(dirname "$REG")/contagens-checksum.tsv"
check "registro traz desvios REAIS da execucao (lateral = mesmo dump)" grep -q "segunda restauracao do mesmo dump" "$REG"
# ordem das etapas no log: restore -> verificacao -> RLS -> extrair -> aplicar -> desfazer
ordem_ok() {
  local p_restore p_rls p_extrair p_aplicar p_desfazer
  p_restore=$(grep -n "pg_restore" "$STUB_LOG" | head -n 1 | cut -d: -f1)
  p_rls=$(grep -n "teste-rls-restaurado" "$STUB_LOG" | head -n 1 | cut -d: -f1)
  p_extrair=$(grep -n "restaurar-tenant extrair" "$STUB_LOG" | head -n 1 | cut -d: -f1)
  p_aplicar=$(grep -n "restaurar-tenant aplicar" "$STUB_LOG" | head -n 1 | cut -d: -f1)
  p_desfazer=$(grep -n "desfazer-lote" "$STUB_LOG" | head -n 1 | cut -d: -f1)
  [ -n "$p_restore" ] && [ -n "$p_rls" ] && [ -n "$p_extrair" ] && [ -n "$p_aplicar" ] && [ -n "$p_desfazer" ] \
    && [ "$p_restore" -lt "$p_rls" ] && [ "$p_rls" -lt "$p_extrair" ] \
    && [ "$p_extrair" -lt "$p_aplicar" ] && [ "$p_aplicar" -lt "$p_desfazer" ]
}
check "ordem das etapas: restore < RLS < extrair < aplicar < desfazer" ordem_ok
check "sonda de isolamento roda durante a seletiva" grep -q "verificar-isolamento sondar" "$STUB_LOG"

# 5. falha no pg_restore: saida 3, aborta na primeira falha (nada de P3.33)
RC="$(STUB_FAIL_RESTORE=1 run_case "${ARGS_BASE[@]}")"
[ "$RC" = "3" ]; check "falha de pg_restore sai 3" test $? -eq 0
check "registro marca a etapa restauracao-completa" grep -q "^- Etapa final: restauracao-completa" "$(ultimo_registro)"
check "aborto na primeira falha: P3.33 nao e chamada" absent "P3.33" "$STUB_LOG"

# 6. restauracao vazia (0 tabelas): saida 4
RC="$(STUB_COUNT=empty run_case "${ARGS_BASE[@]}")"
[ "$RC" = "4" ]; check "restauracao vazia sai 4" test $? -eq 0
check "registro marca a etapa de verificacao" grep -q "^- Etapa final: verificacao-contagem-checksum" "$(ultimo_registro)"

# 7. RLS reprovado: saida 5 e a seletiva nao roda
RC="$(STUB_RLS=FALHOU run_case "${ARGS_BASE[@]}")"
[ "$RC" = "5" ]; check "RLS reprovado sai 5" test $? -eq 0
check "RLS reprovado aborta antes da seletiva" absent "restaurar-tenant" "$STUB_LOG"

# 8. falha na restauracao seletiva (P3.33): saida 6 e o lote nao e desfeito
RC="$(STUB_FAIL_RESTAURAR=1 run_case "${ARGS_BASE[@]}")"
[ "$RC" = "6" ]; check "falha na seletiva sai 6" test $? -eq 0
check "falha na seletiva aborta antes do desfazer" absent "desfazer-lote" "$STUB_LOG"

# 9. lote COM efeito por OS, caminho da OS inversa completo: saida 0
RC="$(GL3_OS_INVERSA_CONFIRMADA="$RUN_ID" run_case "${ARGS_BASE[@]/$LOTE/$LOTE_OS}" --os-inversa-json "$OSINV")"
[ "$RC" = "0" ]; check "lote com OS + OS inversa confirmada sai 0" test $? -eq 0
check "registro prova a OS inversa" grep -q "OS inversa (quando o lote tem efeito por OS, ADR-0039): OK" "$(ultimo_registro)"
check "registro prova o efeito da OS inversa no banco" grep -q "CLOSED_EXECUTED" "$(ultimo_registro)"

# 10. lote COM efeito por OS, SEM descritor da OS inversa: saida 7
RC="$(GL3_OS_INVERSA_CONFIRMADA="$RUN_ID" run_case "${ARGS_BASE[@]/$LOTE/$LOTE_OS}")"
[ "$RC" = "7" ]; check "lote com OS sem descritor sai 7" test $? -eq 0

# 11. lote COM efeito por OS, SEM a confirmacao do encerramento: saida 7
RC="$(run_case "${ARGS_BASE[@]/$LOTE/$LOTE_OS}" --os-inversa-json "$OSINV")"
[ "$RC" = "7" ]; check "lote com OS sem confirmacao do encerramento sai 7" test $? -eq 0

# 12. RLS INCONCLUSIVO (sem massa): saida 5, etapa teste-rls (M1)
RC="$(STUB_RLS=INCONCLUSIVO run_case "${ARGS_BASE[@]}")"
[ "$RC" = "5" ]; check "RLS inconclusivo sai 5" test $? -eq 0
check "registro marca a etapa teste-rls" grep -q "^- Etapa final: teste-rls" "$(ultimo_registro)"

# 13. falha de isolamento (fotografia da P3.33): saida 6, sem desfazer (M1)
RC="$(STUB_FAIL_ISOLAMENTO=1 run_case "${ARGS_BASE[@]}")"
[ "$RC" = "6" ]; check "falha de isolamento sai 6" test $? -eq 0
check "falha de isolamento aborta antes do desfazer" absent "desfazer-lote" "$STUB_LOG"

# 14. falha do desfazer-lote.sh (lote sem OS): saida 7 (M1)
RC="$(STUB_FAIL_DESFAZER=1 run_case "${ARGS_BASE[@]}")"
[ "$RC" = "7" ]; check "falha do desfazer sai 7" test $? -eq 0
check "registro marca a etapa do desfazer" grep -q "^- Etapa final: desfazer-lote-os-inversa" "$(ultimo_registro)"

# 15. prova da OS inversa insuficiente no banco (0 OS / 0 efeitos): saida 7 (M1)
RC="$(STUB_OSINV=insuficiente GL3_OS_INVERSA_CONFIRMADA="$RUN_ID" run_case "${ARGS_BASE[@]/$LOTE/$LOTE_OS}" --os-inversa-json "$OSINV")"
[ "$RC" = "7" ]; check "prova da OS inversa insuficiente sai 7" test $? -eq 0

# 16. descritor da OS inversa sem o campo obrigatorio reason: saida 7 (B-e)
RC="$(GL3_OS_INVERSA_CONFIRMADA="$RUN_ID" run_case "${ARGS_BASE[@]/$LOTE/$LOTE_OS}" --os-inversa-json "$OSINV_BAD")"
[ "$RC" = "7" ]; check "descritor sem reason sai 7" test $? -eq 0

# 17. dependencia da P3.33 ausente no repo: saida 2 no pre-voo (M1)
mv "$REPO/scripts/operacao/desfazer-lote.sh" "$TMP/desfazer-lote.bak"
RC="$(run_case "${ARGS_BASE[@]}")"
mv "$TMP/desfazer-lote.bak" "$REPO/scripts/operacao/desfazer-lote.sh"
[ "$RC" = "2" ]; check "dependencia da P3.33 ausente sai 2" test $? -eq 0
check "registro marca o pre-voo na ausencia da dependencia" grep -q "^- Etapa final: pre-voo" "$(ultimo_registro)"

# 18. dump vazio em diretorio existente: saida 2 (M1)
BK2="$TMP/backup-vazio"; mkdir -p "$BK2"; : > "$BK2/ziipsan.dump"
RC="$(run_case "${ARGS_BASE[@]/$BK/$BK2}")"
[ "$RC" = "2" ]; check "dump vazio sai 2" test $? -eq 0

# 19. opcao desconhecida: saida 2 COM registro de FALHA (B-b)
RC="$(run_case --opcao-que-nao-existe)"
[ "$RC" = "2" ]; check "opcao desconhecida sai 2" test $? -eq 0
check "opcao desconhecida grava registro de FALHA" grep -q "erro de sintaxe de argumentos" "$(ultimo_registro)"

# 20. checksum da testemunha diverge depois da seletiva: saida 6 (M1)
RC="$(STUB_TESTEMUNHA_DIVERGE=1 run_case "${ARGS_BASE[@]}")"
[ "$RC" = "6" ]; check "checksum da testemunha divergente sai 6" test $? -eq 0
check "registro marca a etapa da seletiva na divergencia" grep -q "^- Etapa final: restauracao-seletiva-tenant" "$(ultimo_registro)"

# 21. SIGTERM no meio da restauracao: trap sai 143, grava registro da
#     interrupcao e limpa os containers descartaveis (M2)
: > "$STUB_LOG"; rm -f "$STUB_LOTE_DEPOIS" "$STUB_MARCA_TESTEMUNHA"
STUB_SLEEP_RESTORE=3 bash "$SCRIPT" "${ARGS_BASE[@]}" >"$TMP/out.txt" 2>"$TMP/err.txt" &
PID_ENSAIO=$!
for _ in $(seq 1 40); do grep -q "pg_restore" "$STUB_LOG" 2>/dev/null && break; sleep 0.2; done
kill -TERM "$PID_ENSAIO"
wait "$PID_ENSAIO"; RC_INT=$?
[ "$RC_INT" = "143" ]; check "SIGTERM no meio do ensaio sai 143" test $? -eq 0
check "registro da interrupcao gravado como FALHA" grep -q "interrompido por SIGTERM" "$(ultimo_registro)"
# A subida ja faz 1 rm -f do container principal (auto-recuperacao); a
# limpeza do trap e o SEGUNDO -- contar ocorrencias distingue os dois.
N_RM_PRINCIPAL="$(grep -c "podman rm -f ziipsan-ensaio-gl3$" "$STUB_LOG" || true)"
[ "$N_RM_PRINCIPAL" -ge 2 ]; check "trap limpou os containers descartaveis (2o rm -f do principal)" test $? -eq 0

echo
echo "test-ensaio-restauracao-local: $PASS passaram, $FAILN falharam"
[ "$FAILN" -eq 0 ]
