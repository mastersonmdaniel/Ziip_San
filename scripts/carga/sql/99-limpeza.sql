-- P2.9 -- Limpeza da massa sintetica. DESTRUTIVO e propositalmente cercado:
-- so apaga o tenant da carga, so com a confirmacao explicita, e na ordem
-- inversa das FKs. Rode com -v confirmacao=APAGAR-CARGA-P29.
\set ON_ERROR_STOP on
\if :{?confirmacao}
\else
  \echo 'ERRO: defina -v confirmacao=APAGAR-CARGA-P29'
  \quit
\endif
SELECT CASE WHEN :'confirmacao' = 'APAGAR-CARGA-P29' THEN 1
            ELSE (SELECT 1/0) END AS guarda;
BEGIN;
DELETE FROM bills               WHERE tenant_id = :'tenant_id'::uuid AND reference_month = :'reference_month'::date;
DELETE FROM consumption_records WHERE tenant_id = :'tenant_id'::uuid AND reference = :'reference_month'::date;
DELETE FROM meter_readings      WHERE tenant_id = :'tenant_id'::uuid AND reference = :'reference_month'::date;
DELETE FROM meter_installations WHERE tenant_id = :'tenant_id'::uuid;
DELETE FROM meters              WHERE tenant_id = :'tenant_id'::uuid;
-- Raio limitado ao que a carga criou (filtro da massa CARGA-% / ids da
-- tarifa CARGA-P29 / referencia da carga), nunca o tenant inteiro:
DELETE FROM cycle_schedule_items WHERE tenant_id = :'tenant_id'::uuid
  AND reference_id IN (SELECT id FROM billing_references
                        WHERE tenant_id = :'tenant_id'::uuid
                          AND reference_month = :'reference_month'::date);
DELETE FROM billing_references  WHERE tenant_id = :'tenant_id'::uuid AND reference_month = :'reference_month'::date;
DELETE FROM billing_groups      WHERE tenant_id = :'tenant_id'::uuid AND code LIKE 'CARGA-%';
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
DELETE FROM tariffs             WHERE tenant_id = :'tenant_id'::uuid AND code = 'CARGA-P29';
-- consumer_units e categories da massa: apague pelas chaves deterministicas
-- SOMENTE depois de conferir a V3_1 (ver 10-seed-registry.sql) -- nao
-- automatizamos DELETE sobre schema nao verificado.
COMMIT;
