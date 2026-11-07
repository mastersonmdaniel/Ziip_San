-- P3.23 -- Limpeza da massa sintetica da demonstracao. DESTRUTIVO e
-- cercado: so apaga o tenant demo, so com confirmacao explicita, no raio
-- da massa DEMO-% / tarifa DEMO-P323. Rode com
--   -v confirmacao=APAGAR-DEMO-P323 -v tenant_id=... -v reference_month=...
-- (Adaptado do 99-limpeza.sql da P2.9.)
\set ON_ERROR_STOP on
\if :{?confirmacao}
\else
  \echo 'ERRO: defina -v confirmacao=APAGAR-DEMO-P323'
  \quit
\endif
SELECT CASE WHEN :'confirmacao' = 'APAGAR-DEMO-P323' THEN 1
            ELSE (SELECT 1/0) END AS guarda;
BEGIN;
DELETE FROM bills               WHERE tenant_id = :'tenant_id'::uuid AND reference_month = :'reference_month'::date;
DELETE FROM consumption_records WHERE tenant_id = :'tenant_id'::uuid AND reference = :'reference_month'::date;
DELETE FROM meter_readings      WHERE tenant_id = :'tenant_id'::uuid AND reference = :'reference_month'::date;
DELETE FROM meter_installations WHERE tenant_id = :'tenant_id'::uuid;
DELETE FROM meters              WHERE tenant_id = :'tenant_id'::uuid;
DELETE FROM cycle_schedule_items WHERE tenant_id = :'tenant_id'::uuid
  AND reference_id IN (SELECT id FROM billing_references
                        WHERE tenant_id = :'tenant_id'::uuid
                          AND reference_month = :'reference_month'::date);
DELETE FROM billing_references  WHERE tenant_id = :'tenant_id'::uuid AND reference_month = :'reference_month'::date;
DELETE FROM billing_groups      WHERE tenant_id = :'tenant_id'::uuid AND code LIKE 'DEMO-%';
DELETE FROM tariff_blocks       WHERE tenant_id = :'tenant_id'::uuid
  AND rule_id IN (SELECT id FROM tariff_category_rules
                   WHERE tenant_id = :'tenant_id'::uuid
                     AND version_id IN (SELECT id FROM tariff_versions
                                         WHERE tenant_id = :'tenant_id'::uuid
                                           AND tariff_id = '00000000-0000-0000-0000-000000000100'));
DELETE FROM tariff_category_rules WHERE tenant_id = :'tenant_id'::uuid
  AND version_id IN (SELECT id FROM tariff_versions
                      WHERE tenant_id = :'tenant_id'::uuid
                        AND tariff_id = '00000000-0000-0000-0000-000000000100');
DELETE FROM tariff_versions     WHERE tenant_id = :'tenant_id'::uuid
  AND tariff_id = '00000000-0000-0000-0000-000000000100';
DELETE FROM tariffs             WHERE tenant_id = :'tenant_id'::uuid AND code = 'DEMO-P323';
-- consumer_units, categorias e titulares da massa: apagar pelas chaves
-- deterministicas SOMENTE depois de conferir a V3_1 (ver 10-seed-demo.sql)
-- -- nao automatizamos DELETE sobre schema nao verificado.
COMMIT;
