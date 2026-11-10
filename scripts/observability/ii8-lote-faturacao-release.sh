#!/usr/bin/env bash
# II-8 — LOTE DE FATURAÇÃO COMO PROVA DE RELEASE (harness sobre a massa)
#
# Corre a conferência integral do lote de faturação sobre a base
# fictícia de 12.000 UCs (decisão 51), em blocos de 500 contas, num
# cluster PostgreSQL 16 DESCARTÁVEL, e regista placar + tempos como
# evidência de release — a unidade de baseline do SLO-3
# (docs/observability/slo-go-live-ii8.md). A conferência recomputa cada
# conta a partir dos seus itens (ver ii8-lote-faturacao-release.sql);
# no ambiente de release, o mesmo desenho corre pelo harness Java
# BillingBatchReleaseProof sobre o motor real (P2.5/C2/C4), que este
# pacote não altera.
#
# Massa declarada (totais conhecidos da base 12k, manifesto dela):
#   12.000 UCs; 11.300 contas faturáveis (= linhas em bills); 11.300
#   registos de consumo; 9.150 leituras; municípios 9.000/3.000.
#   As 700 UCs sem conta (S16 suspensas + S19 provisórias) não faturam.
#
# Uso: bash ii8-lote-faturacao-release.sh
# Variáveis: SEED_DIR (dir. seed/base-ficticia da base 12k),
#            FIXTURE (fixture.sql da base 12k), BLOCO (tamanho do bloco).
set -u
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SEED_DIR="${SEED_DIR:-$SCRIPT_DIR/../../seed/base-ficticia}"
FIXTURE="${FIXTURE:-$SCRIPT_DIR/../../seed/base-ficticia/fixture.sql}"
BLOCO="${BLOCO:-500}"
PGROOT=/tmp/ii8-pg
PGBIN=/usr/lib/postgresql/16/bin
PORT=55442
DB=ii8lote
TENANT='11111111-2222-4333-8444-555555555555'
ESPERADO_UCS=12000
ESPERADO_CONTAS=11300
ESPERADO_CONSUMOS=11300
ESPERADO_LEITURAS=9150
TOTAL_CHECAGENS=0
PASSOU=0

como_pg() {
  if [ "$(id -u)" = "0" ]; then runuser -u postgres -- "$@"; else "$@"; fi
}
psql_ii8() {
  como_pg "$PGBIN/psql" -h /tmp -p $PORT -U postgres -d $DB -v ON_ERROR_STOP=1 "$@"
}
checa() { # checa <descricao> <esperado> <obtido>
  TOTAL_CHECAGENS=$((TOTAL_CHECAGENS + 1))
  if [ "$2" = "$3" ]; then
    PASSOU=$((PASSOU + 1)); echo "PASS [$TOTAL_CHECAGENS] $1"
  else
    echo "FAIL [$TOTAL_CHECAGENS] $1 — esperado=[$2] obtido=[$3]"
  fi
}
derruba() {
  como_pg "$PGBIN/pg_ctl" -D "$PGROOT/data" -m immediate stop >/dev/null 2>&1 || true
  rm -rf "$PGROOT"
}
trap derruba EXIT

echo "== II-8 lote de faturacao como prova de release =="
echo "massa declarada: base ficticia 12k (tenant $TENANT); bloco=$BLOCO contas"
derruba
mkdir -p "$PGROOT"
chown postgres:postgres "$PGROOT" 2>/dev/null || true
como_pg "$PGBIN/initdb" -D "$PGROOT/data" -A trust -U postgres >/dev/null || { echo "FALHA initdb"; exit 1; }
como_pg "$PGBIN/pg_ctl" -D "$PGROOT/data" -o "-p $PORT -k /tmp -c listen_addresses=''" -l "$PGROOT/log.txt" start >/dev/null || { echo "FALHA start"; exit 1; }
como_pg "$PGBIN/createdb" -h /tmp -p $PORT -U postgres $DB || { echo "FALHA createdb"; exit 1; }

STAGE=/tmp/ii8-stage
rm -rf "$STAGE"; mkdir -p "$STAGE"
cp "$FIXTURE" "$STAGE/fixture.sql"
cp "$SEED_DIR"/00-tenant-ficticio.sql "$SEED_DIR"/carga-*.sql "$STAGE"/
cp "$SCRIPT_DIR/ii8-lote-faturacao-release.sql" "$STAGE/"
chmod -R a+rX "$STAGE"
SQL_LOTE="$STAGE/ii8-lote-faturacao-release.sql"

echo "== carga da massa =="
psql_ii8 -q -f "$STAGE/fixture.sql" >/dev/null || { echo "FALHA fixture"; exit 1; }
psql_ii8 -q -f "$STAGE/00-tenant-ficticio.sql" >/dev/null || { echo "FALHA 00-tenant"; exit 1; }
for f in "$STAGE"/carga-*.sql; do
  psql_ii8 -q -f "$f" >/dev/null || { echo "FALHA carga: $f"; exit 1; }
done
echo "carga concluida"

checa "UCs da massa = 12000" "$ESPERADO_UCS" \
  "$(psql_ii8 -At -c "SELECT count(*) FROM consumer_units WHERE tenant_id='$TENANT'")"
checa "contas faturaveis = 11300" "$ESPERADO_CONTAS" \
  "$(psql_ii8 -At -c "SELECT count(*) FROM bills WHERE tenant_id='$TENANT'")"
checa "registos de consumo = 11300" "$ESPERADO_CONSUMOS" \
  "$(psql_ii8 -At -c "SELECT count(*) FROM consumption_records WHERE tenant_id='$TENANT'")"
checa "leituras = 9150" "$ESPERADO_LEITURAS" \
  "$(psql_ii8 -At -c "SELECT count(*) FROM meter_readings WHERE tenant_id='$TENANT'")"
checa "municipios = 9000/3000" "9000/3000" \
  "$(psql_ii8 -At -c "SELECT (SELECT count(*) FROM consumer_units cu JOIN addresses a ON a.tenant_id=cu.tenant_id AND a.id=cu.address_id WHERE cu.tenant_id='$TENANT' AND a.city='Porto das Gaivotas') || '/' || (SELECT count(*) FROM consumer_units cu JOIN addresses a ON a.tenant_id=cu.tenant_id AND a.id=cu.address_id WHERE cu.tenant_id='$TENANT' AND a.city='Vale do Rio Claro')")"

echo "== lote: conferencia em blocos de $BLOCO =="
INICIO=$(date +%s%N)
PROC=0; CONF=0; DIV=0; SOMA=0
DESLOC=0
while [ "$DESLOC" -lt "$ESPERADO_CONTAS" ]; do
  LINHA="$(psql_ii8 -At -F'|' -v tenant="$TENANT" -v limite="$BLOCO" -v deslocamento="$DESLOC" -f "$SQL_LOTE")" || { echo "FALHA bloco offset=$DESLOC"; exit 1; }
  P=$(echo "$LINHA" | cut -d'|' -f1); C=$(echo "$LINHA" | cut -d'|' -f2); D=$(echo "$LINHA" | cut -d'|' -f3); S=$(echo "$LINHA" | cut -d'|' -f4)
  echo "bloco offset=$DESLOC processados=$P conformes=$C divergentes=$D soma=$S"
  PROC=$((PROC + P)); CONF=$((CONF + C)); DIV=$((DIV + D))
  SOMA=$(python3 -c "print(f'{$SOMA + $S:.2f}')")
  DESLOC=$((DESLOC + BLOCO))
done
FIM=$(date +%s%N)
DUR_MS=$(( (FIM - INICIO) / 1000000 ))
echo "lote concluido: processados=$PROC conformes=$CONF divergentes=$DIV soma_total=$SOMA duracao_ms=$DUR_MS"

checa "lote processou todas as contas" "$ESPERADO_CONTAS" "$PROC"
checa "lote sem divergencias (placar bate com o esperado da base)" "0" "$DIV"
checa "soma do lote = soma das contas da massa" \
  "$(psql_ii8 -At -c "SELECT coalesce(sum(total_value),0) FROM bills WHERE tenant_id='$TENANT'")" "$SOMA"

echo "RELATORIO-LOTE total=$PROC sucessos=$CONF falhas=$DIV duracao_ms=$DUR_MS soma=$SOMA"
echo "PLACAR: $PASSOU/$TOTAL_CHECAGENS"
[ "$PASSOU" = "$TOTAL_CHECAGENS" ]
