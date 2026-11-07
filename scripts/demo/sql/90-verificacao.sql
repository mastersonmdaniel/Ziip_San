-- P3.23 -- Verificacao pos-semeadura da demonstracao: os numeros que
-- entram no registro da recriacao. Variaveis: tenant_id, reference_month.
-- O orquestrador (recriar-demo.sh) exige unidades >= DEMO_UNIDADES a
-- partir da MESMA contagem de consumer_units feita por ele apos as
-- sementes; este arquivo registra o retrato completo.
\set ON_ERROR_STOP on
SELECT 'unidades' AS metrica, count(*) AS valor FROM consumer_units
 WHERE tenant_id = :'tenant_id'::uuid
UNION ALL SELECT 'hidrometros', count(*) FROM meters
 WHERE tenant_id = :'tenant_id'::uuid
UNION ALL SELECT 'leituras_referencia', count(*) FROM meter_readings
 WHERE tenant_id = :'tenant_id'::uuid AND reference = :'reference_month'::date
UNION ALL SELECT 'consumos_referencia', count(*) FROM consumption_records
 WHERE tenant_id = :'tenant_id'::uuid AND reference = :'reference_month'::date;
-- NOTA: a contagem de DOCUMENTOS MARCADOS como sinteticos no banco depende
-- da DDL do cadastro de titulares (V3_1, nao verificada -- ver o trecho
-- [CONFERIR-V3_1] do 10-seed-demo.sql). Enquanto ela nao e confirmada, a
-- prova da marcacao e a do GERADOR (testes/test_demo.py confere linha a
-- linha do unidades.csv: documento_sintetico=SIM e marca=SINTETICO em
-- 100% das linhas). Nao inventamos aqui uma consulta sobre coluna que
-- nao conferimos.
