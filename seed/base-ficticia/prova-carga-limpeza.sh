#!/usr/bin/env bash
# PROVA DE CARGA E LIMPEZA — BASE FICTÍCIA DE TESTES DO ZIIPSAN (decisão nº 34)
# Cluster PostgreSQL 16 DESCARTÁVEL em /tmp/bf12k-pg (porta 55441, socket /tmp).
# Sobe, prova (7 asserções do pacote) e DERRUBA o cluster no fim.
# Uso: bash prova-carga-limpeza.sh
# Variáveis: SEED_DIR (padrão: diretório deste script), FIXTURE (padrão:
# ../../../trabalho/fixture.sql a partir deste script).
set -u
SEED_DIR="${SEED_DIR:-$(cd "$(dirname "$0")" && pwd)}"
FIXTURE="${FIXTURE:-$SEED_DIR/../../../trabalho/fixture.sql}"
PGROOT=/tmp/bf12k-pg
PGBIN=/usr/lib/postgresql/16/bin
PORT=55441
DB=bf12k
TENANT='11111111-2222-4333-8444-555555555555'
SENTINELA='22222222-3333-4444-8555-666666666666'
TOTAL_CHECAGENS=0
PASSOU=0

como_pg() {
  if [ "$(id -u)" = "0" ]; then runuser -u postgres -- "$@"; else "$@"; fi
}
psql_bf() {
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

echo "== cluster descartavel =="
derruba
mkdir -p "$PGROOT"
chown postgres:postgres "$PGROOT" 2>/dev/null || true
como_pg "$PGBIN/initdb" -D "$PGROOT/data" -A trust -U postgres >/dev/null || { echo "FALHA initdb"; exit 1; }
como_pg "$PGBIN/pg_ctl" -D "$PGROOT/data" -o "-p $PORT -k /tmp -c listen_addresses=''" -l "$PGROOT/log.txt" start >/dev/null || { echo "FALHA start"; exit 1; }
como_pg "$PGBIN/createdb" -h /tmp -p $PORT -U postgres $DB || { echo "FALHA createdb"; exit 1; }

echo "== staging legivel pelo usuario postgres =="
STAGE=/tmp/bf12k-stage
rm -rf "$STAGE"; mkdir -p "$STAGE"
cp "$FIXTURE" "$STAGE/fixture.sql"
cp "$SEED_DIR"/00-tenant-ficticio.sql "$SEED_DIR"/carga-*.sql "$SEED_DIR"/limpeza-base-ficticia.sql "$SEED_DIR"/matriz.csv "$STAGE"/
chmod -R a+rX "$STAGE"
FIXTURE="$STAGE/fixture.sql"
SEED_DIR="$STAGE"

echo "== fixture =="
psql_bf -q -f "$FIXTURE" >/dev/null || { echo "FALHA fixture"; exit 1; }

echo "== sentinela (outro tenant, ANTES da carga) =="
psql_bf -q >/dev/null <<SQL
INSERT INTO public.tenant_catalog (id, schema_name, name, status) VALUES ('$SENTINELA', 'tenant_sentinela', 'Tenant Sentinela — Não Tocar', 'ACTIVE');
INSERT INTO localities (id, tenant_id, code, name) VALUES ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1', '$SENTINELA', 99, 'Localidade Sentinela');
INSERT INTO commercial_sectors (id, tenant_id, locality_id, code, name) VALUES ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2', '$SENTINELA', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1', 99, 'Setor Sentinela');
INSERT INTO reading_routes (id, tenant_id, code, name) VALUES ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3', '$SENTINELA', 99, 'Rota Sentinela');
INSERT INTO blocks (id, tenant_id, sector_id, code, has_water_network, has_sewer_network) VALUES ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa4', '$SENTINELA', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2', 99, true, true);
INSERT INTO categories (id, tenant_id, code, name) VALUES ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa5', '$SENTINELA', 'SENTINELA', 'Categoria Sentinela');
INSERT INTO subcategories (id, tenant_id, category_id, code, name) VALUES ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa6', '$SENTINELA', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa5', 'SENT-PADRAO', 'Sub Sentinela');
INSERT INTO water_statuses (id, tenant_id, code, name, bills, connected, supplies) VALUES ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa7', '$SENTINELA', 'SENT', 'Status Sentinela', true, true, true);
INSERT INTO sewer_statuses (id, tenant_id, code, name, bills, connected) VALUES ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa8', '$SENTINELA', 'SENT', 'Esgoto Sentinela', true, true);
INSERT INTO customers (id, tenant_id, person_type, name) VALUES ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', '$SENTINELA', 'PF', 'Titular Sentinela');
INSERT INTO addresses (id, tenant_id, entity_type, entity_id, type, street, city, state, country, is_primary) VALUES ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2', '$SENTINELA', 'CONSUMER_UNIT', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb3', 'RESIDENTIAL', 'Rua Sentinela', 'Cidade Sentinela', 'SC', 'BR', true);
INSERT INTO consumer_units (id, tenant_id, matricula, dv, block_id, lot, sublot, address_id, water_status_id, sewer_status_id, reading_route_id, status) VALUES ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb3', '$SENTINELA', 'SENT0001', '9', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa4', 1, 0, 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa7', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa8', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3', 'ACTIVE');
INSERT INTO unit_customers (id, tenant_id, unit_id, customer_id, role, start_date, show_on_bill) VALUES ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb4', '$SENTINELA', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb3', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'USER', '2026-01-05', true);
INSERT INTO unit_subcategories (id, tenant_id, unit_id, subcategory_id, economies) VALUES ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb5', '$SENTINELA', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb3', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa6', 1);
INSERT INTO tariffs (id, tenant_id, code, name, calc_type) VALUES ('cccccccc-cccc-4ccc-8ccc-ccccccccccc1', '$SENTINELA', 'TAR-SENT', 'Tarifa Sentinela', 'BLOCKS');
INSERT INTO tariff_versions (id, tenant_id, tariff_id, valid_from) VALUES ('cccccccc-cccc-4ccc-8ccc-ccccccccccc2', '$SENTINELA', 'cccccccc-cccc-4ccc-8ccc-ccccccccccc1', '2026-01-01');
INSERT INTO bills (id, tenant_id, unit_id, reference_month, status, water_value, sewer_value, debits_value, credits_value, taxes_value, total_value, due_date, completed) VALUES ('cccccccc-cccc-4ccc-8ccc-ccccccccccc3', '$SENTINELA', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb3', '2026-10-01', 'NORMAL', 77.00, 0, 0, 0, 0, 77.00, '2026-11-20', true);
SQL
[ $? -eq 0 ] || { echo "FALHA sentinela"; exit 1; }
hash_sentinela() {
  psql_bf -At -c "
    SELECT (SELECT count(*) FROM consumer_units WHERE tenant_id='$SENTINELA') || '|' ||
           (SELECT count(*) FROM customers WHERE tenant_id='$SENTINELA') || '|' ||
           (SELECT count(*) FROM bills WHERE tenant_id='$SENTINELA') || '|' ||
           (SELECT coalesce(sum(total_value),0) FROM bills WHERE tenant_id='$SENTINELA') || '|' ||
           (SELECT count(*) FROM public.tenant_catalog WHERE id='$SENTINELA');"
}
SENT_ANTES="$(hash_sentinela)"

echo "== carga (00 + carga-*) =="
psql_bf -q -f "$SEED_DIR/00-tenant-ficticio.sql" >/dev/null || { echo "FALHA 00-tenant"; exit 1; }
for f in "$SEED_DIR"/carga-*.sql; do
  psql_bf -q -f "$f" >/dev/null || { echo "FALHA carga: $f"; exit 1; }
done
echo "carga concluida"

# (1) total de UCs
checa "UCs do tenant ficticio = 12000" "12000" \
  "$(psql_bf -At -c "SELECT count(*) FROM consumer_units WHERE tenant_id='$TENANT'")"

# (2) distribuicao por situacao = matriz.csv
psql_bf -At -F',' -c "
SELECT cu.status,
       (SELECT cat.code FROM unit_subcategories us
          JOIN subcategories s ON s.tenant_id=us.tenant_id AND s.id=us.subcategory_id
          JOIN categories cat ON cat.tenant_id=s.tenant_id AND cat.id=s.category_id
         WHERE us.tenant_id=cu.tenant_id AND us.unit_id=cu.id LIMIT 1),
       ws.code,
       CASE WHEN EXISTS (SELECT 1 FROM social_tariff_enrollments e WHERE e.tenant_id=cu.tenant_id AND e.unit_id=cu.id AND e.status='ACTIVE') THEN 'SIM' ELSE 'NAO' END,
       CASE WHEN mi.id IS NULL THEN 'NONE'
            WHEN mt.status='INSTALLED' THEN 'INSTALLED'
            WHEN mt.status='IN_MAINTENANCE' THEN 'MAINTENANCE'
            WHEN mt.status='WRITTEN_OFF' THEN 'WRITTEN_OFF' ELSE mt.status END,
       CASE WHEN EXISTS (SELECT 1 FROM meter_readings r WHERE r.tenant_id=cu.tenant_id AND r.unit_id=cu.id AND r.anomaly_id IS NOT NULL) THEN 'ANOMALIA'
            WHEN cr.id IS NULL THEN 'NONE'
            WHEN cr.consumption_type='REAL' THEN 'NORMAL'
            WHEN cr.consumption_type='ESTIMATED' THEN 'ESTIMADA' ELSE cr.consumption_type END,
       CASE WHEN b.id IS NULL THEN 'SEM_CONTA'
            WHEN b.status='INSTALLMENT' THEN 'PARCELADA'
            WHEN b.status='CANCELLED' THEN 'CANCELADA'
            WHEN b.status='PAID' THEN 'PAGA'
            WHEN b.status='NORMAL' AND b.due_date >= DATE '2026-11-01' THEN 'ABERTA'
            WHEN b.status='NORMAL' THEN 'VENCIDA' ELSE b.status END,
       CASE WHEN EXISTS (SELECT 1 FROM debit_receivables d WHERE d.tenant_id=cu.tenant_id AND d.unit_id=cu.id AND d.status='NORMAL') THEN 'SIM' ELSE 'NAO' END,
       CASE WHEN so.id IS NULL THEN 'SEM_OS'
            WHEN so.status IN ('CLOSED_EXECUTED','CLOSED_NOT_EXECUTED') THEN 'OS_FECHADA' ELSE 'OS_ABERTA' END,
       count(*)
FROM consumer_units cu
JOIN water_statuses ws ON ws.tenant_id=cu.tenant_id AND ws.id=cu.water_status_id
LEFT JOIN LATERAL (SELECT mi2.id FROM meter_installations mi2 WHERE mi2.tenant_id=cu.tenant_id AND mi2.unit_id=cu.id ORDER BY mi2.installed_on DESC LIMIT 1) mi ON true
LEFT JOIN meters mt ON mt.tenant_id=cu.tenant_id AND mt.id=(SELECT mi3.meter_id FROM meter_installations mi3 WHERE mi3.tenant_id=cu.tenant_id AND mi3.id=mi.id)
LEFT JOIN LATERAL (SELECT cr2.id, cr2.consumption_type FROM consumption_records cr2 WHERE cr2.tenant_id=cu.tenant_id AND cr2.unit_id=cu.id AND cr2.service_kind='WATER' ORDER BY cr2.reference DESC LIMIT 1) cr ON true
LEFT JOIN LATERAL (SELECT b2.id, b2.status, b2.due_date FROM bills b2 WHERE b2.tenant_id=cu.tenant_id AND b2.unit_id=cu.id ORDER BY b2.reference_month DESC LIMIT 1) b ON true
LEFT JOIN LATERAL (SELECT so2.id, so2.status FROM service_orders so2 JOIN service_order_targets t2 ON t2.tenant_id=so2.tenant_id AND t2.order_id=so2.id AND t2.target_type='CONSUMER_UNIT' AND t2.target_id=cu.id WHERE so2.tenant_id=cu.tenant_id ORDER BY so2.created_at LIMIT 1) so ON true
WHERE cu.tenant_id='$TENANT'
GROUP BY 1,2,3,4,5,6,7,8,9 ORDER BY 1,2,3,4,5,6,7,8,9;" > /tmp/bf12k-atual.csv
python3 - "$SEED_DIR/matriz.csv" /tmp/bf12k-atual.csv <<'PYEOF'
import csv, sys
esp = {}
with open(sys.argv[1], newline='', encoding='utf-8') as f:
    for row in csv.DictReader(f):
        chave = (row['status_uc'], row['categoria'], row['situacao_agua'], row['tarifa_social'],
                 row['hidrometro'], row['leitura'], row['conta'], row['debito'], row['ordem_servico'])
        esp[chave] = int(row['contagem'])
obt = {}
with open(sys.argv[2], newline='', encoding='utf-8') as f:
    for row in csv.reader(f):
        if row:
            obt[tuple(row[:9])] = int(row[9])
if esp == obt:
    print("DISTRIBUICAO OK")
else:
    print("DISTRIBUICAO DIVERGE")
    for k in sorted(set(esp) | set(obt)):
        if esp.get(k) != obt.get(k):
            print(" ", k, "esperado", esp.get(k), "obtido", obt.get(k))
    sys.exit(1)
PYEOF
checa "distribuicao por situacao = matriz.csv" "0" "$?"

# (3) orfaos por FK = 0 (FKs sao impostas na carga; varredura explicita das principais)
ORFAOS="$(psql_bf -At -c "
SELECT
 (SELECT count(*) FROM consumer_units cu WHERE cu.tenant_id='$TENANT' AND NOT EXISTS (SELECT 1 FROM addresses a WHERE a.tenant_id=cu.tenant_id AND a.id=cu.address_id)) +
 (SELECT count(*) FROM bills b WHERE b.tenant_id='$TENANT' AND NOT EXISTS (SELECT 1 FROM consumer_units u WHERE u.tenant_id=b.tenant_id AND u.id=b.unit_id)) +
 (SELECT count(*) FROM bill_items i WHERE i.tenant_id='$TENANT' AND NOT EXISTS (SELECT 1 FROM bills b WHERE b.tenant_id=i.tenant_id AND b.id=i.bill_id)) +
 (SELECT count(*) FROM meter_readings r WHERE r.tenant_id='$TENANT' AND NOT EXISTS (SELECT 1 FROM meter_installations mi WHERE mi.tenant_id=r.tenant_id AND mi.id=r.installation_id)) +
 (SELECT count(*) FROM installment_schedule s WHERE s.tenant_id='$TENANT' AND NOT EXISTS (SELECT 1 FROM installment_agreements a WHERE a.tenant_id=s.tenant_id AND a.id=s.agreement_id)) +
 (SELECT count(*) FROM service_order_targets t WHERE t.tenant_id='$TENANT' AND NOT EXISTS (SELECT 1 FROM service_orders o WHERE o.tenant_id=t.tenant_id AND o.id=t.order_id));")"
checa "orfaos por FK = 0" "0" "$ORFAOS"

# (4) sentinela intacta apos a carga
checa "sentinela intacta apos carga" "$SENT_ANTES" "$(hash_sentinela)"

echo "== limpeza =="
psql_bf -q -f "$SEED_DIR/limpeza-base-ficticia.sql" >/dev/null || { echo "FALHA limpeza"; exit 1; }

# (5) zero linhas do tenant ficticio em TODAS as tabelas tocadas
TABELAS="localities commercial_sectors reading_routes blocks categories subcategories water_statuses sewer_statuses customers addresses consumer_units unit_customers unit_subcategories tariffs tariff_versions social_tariff_end_reasons social_tariff_enrollments meters meter_installations reading_anomalies consumption_anomalies meter_readings consumption_records debit_receivables bills bill_items installment_agreements installment_schedule service_types service_order_close_reasons service_orders service_order_targets"
RESTO=0
for t in $TABELAS; do
  c="$(psql_bf -At -c "SELECT count(*) FROM $t WHERE tenant_id='$TENANT'")"
  RESTO=$((RESTO + c))
done
checa "0 linhas do tenant ficticio em todas as tabelas tocadas (32)" "0" "$RESTO"

# (6) linha do tenant removida
checa "tenant ficticio removido do tenant_catalog" "0" \
  "$(psql_bf -At -c "SELECT count(*) FROM public.tenant_catalog WHERE id='$TENANT'")"

# (7) sentinela identica apos limpeza
checa "sentinela identica apos limpeza" "$SENT_ANTES" "$(hash_sentinela)"

echo "PLACAR: $PASSOU/$TOTAL_CHECAGENS"
[ "$PASSOU" = "$TOTAL_CHECAGENS" ]
