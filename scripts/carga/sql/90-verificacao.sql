-- P2.9 -- Verificacao pos-carga: os numeros que entram no registro.
-- Variaveis psql: tenant_id, reference_month, ligacoes, faturar.
\set ON_ERROR_STOP on
SELECT 'unidades' AS metrica, count(*) AS valor FROM consumer_units
 WHERE tenant_id = :'tenant_id'::uuid
UNION ALL SELECT 'hidrometros', count(*) FROM meters
 WHERE tenant_id = :'tenant_id'::uuid
UNION ALL SELECT 'leituras_referencia', count(*) FROM meter_readings
 WHERE tenant_id = :'tenant_id'::uuid AND reference = :'reference_month'::date
UNION ALL SELECT 'consumos_referencia', count(*) FROM consumption_records
 WHERE tenant_id = :'tenant_id'::uuid AND reference = :'reference_month'::date
UNION ALL SELECT 'contas_vivas_referencia', count(*) FROM bills
 WHERE tenant_id = :'tenant_id'::uuid AND reference_month = :'reference_month'::date
   AND status NOT IN ('CANCELLED','CANCELLED_BY_RECTIFICATION','PROCESSING_ERROR')
UNION ALL SELECT 'contas_concluidas_referencia', count(*) FROM bills
 WHERE tenant_id = :'tenant_id'::uuid AND reference_month = :'reference_month'::date
   AND completed = TRUE
   AND status NOT IN ('CANCELLED','CANCELLED_BY_RECTIFICATION','PROCESSING_ERROR')
UNION ALL SELECT 'contas_erro_referencia', count(*) FROM bills
 WHERE tenant_id = :'tenant_id'::uuid AND reference_month = :'reference_month'::date
   AND status = 'PROCESSING_ERROR';
-- Soma de conferencia: o total faturado da referencia (agua+esgoto+debitos
-- -creditos-impostos ja e o total_value conferido pelo CHECK da V4_1).
SELECT 'soma_total_contas' AS metrica,
       COALESCE(sum(total_value), 0) AS valor FROM bills
 WHERE tenant_id = :'tenant_id'::uuid AND reference_month = :'reference_month'::date
   AND status NOT IN ('CANCELLED','CANCELLED_BY_RECTIFICATION','PROCESSING_ERROR');
