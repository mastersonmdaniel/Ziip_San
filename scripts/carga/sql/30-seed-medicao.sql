-- P2.9 -- Semente de medicao da massa: hidrometros, instalacoes, leituras e
-- consumo faturado para TODAS as ligacoes (padrao 300 mil). DDL: V5_1
-- (metering) -- verificada nos espelhos. Formulas IDENTICAS as do
-- gerar-massa.py (fonte unica documentada la):
--   categoria: seq % 5 = 0 -> COM, senao RES (so muda o consumo)
--   consumo RES = 12 + ((seq*7) % 25);  COM = 30 + ((seq*11) % 120)
--   leitura anterior = (seq*37) % 90000; atual = anterior + consumo
-- UUIDs: md5('<semente>:<tipo>:<seq>') com versao 5 e variante 10xx,
-- a mesma derivacao do CSV (unit/meter/installation por seq).
-- Variaveis psql: tenant_id, semente, ligacoes, faturar, reference_month.
--
-- CORRECAO POS-REVISAO (achado 6): o subconjunto faturavel e MATERIALIZADO
-- no banco, nao so no CSV: consumption_records.billable = TRUE apenas para
-- seq <= faturar (padrao 100 mil) e FALSE para as demais 200 mil -- e o
-- flag que o motor de conta consome, logo o disparo fatura exatamente o
-- subconjunto medido. O vinculo unidade<->grupo/rota que o despacho usa e
-- o route_code: os grupos CARGA-001..100 (20-seed) tem route_code
-- ROTA-001..100 e a rota de cada unidade segue a MESMA formula do CSV
-- (((seq-1) % 100) + 1) -- ver o trecho de unidades do 10-seed-registry
-- ([CONFERIR-V3_1]: a coluna de rota da unidade mora no registry).
-- Pre-condicao (o orquestrador confere): consumer_units do tenant ja
-- carregadas com os mesmos ids deterministicos (passo 10).
\set ON_ERROR_STOP on
BEGIN;

-- Expressao do UUID deterministico (repetida por tabela, SQL nao tem macro):
--   (substr(h,1,8)||'-'||substr(h,9,4)||'-5'||substr(h,14,3)||'-'||
--    (ARRAY['8','9','a','b'])[1+(('x'||substr(h,17,1))::bit(4)::int % 4)]||
--    substr(h,18,3)||'-'||substr(h,21,12))::uuid   com h = md5(semente:tipo:seq)

INSERT INTO meters (id, tenant_id, meter_number, digits, status)
SELECT (substr(h,1,8)||'-'||substr(h,9,4)||'-5'||substr(h,14,3)||'-'||
        (ARRAY['8','9','a','b'])[1 + (('x'||substr(h,17,1))::bit(4)::int % 4)]||
        substr(h,18,3)||'-'||substr(h,21,12))::uuid,
       :'tenant_id'::uuid, 'CARGA-' || lpad(g::text, 7, '0'), 5, 'INSTALLED'
  FROM (SELECT g, md5(:'semente' || ':meter:' || g) AS h
          FROM generate_series(1, :ligacoes) AS g) s
ON CONFLICT DO NOTHING;

INSERT INTO meter_installations (id, tenant_id, meter_id, unit_id, kind,
                                 installed_on, installed_reading, is_replacement)
SELECT (substr(hi,1,8)||'-'||substr(hi,9,4)||'-5'||substr(hi,14,3)||'-'||
        (ARRAY['8','9','a','b'])[1 + (('x'||substr(hi,17,1))::bit(4)::int % 4)]||
        substr(hi,18,3)||'-'||substr(hi,21,12))::uuid,
       :'tenant_id'::uuid,
       (substr(hm,1,8)||'-'||substr(hm,9,4)||'-5'||substr(hm,14,3)||'-'||
        (ARRAY['8','9','a','b'])[1 + (('x'||substr(hm,17,1))::bit(4)::int % 4)]||
        substr(hm,18,3)||'-'||substr(hm,21,12))::uuid,
       (substr(hu,1,8)||'-'||substr(hu,9,4)||'-5'||substr(hu,14,3)||'-'||
        (ARRAY['8','9','a','b'])[1 + (('x'||substr(hu,17,1))::bit(4)::int % 4)]||
        substr(hu,18,3)||'-'||substr(hu,21,12))::uuid,
       'WATER', DATE '2020-01-15', (g * 37) % 90000, FALSE
  FROM (SELECT g, md5(:'semente' || ':installation:' || g) AS hi,
                   md5(:'semente' || ':meter:' || g) AS hm,
                   md5(:'semente' || ':unit:' || g) AS hu
          FROM generate_series(1, :ligacoes) AS g) s
ON CONFLICT DO NOTHING;

INSERT INTO meter_readings (id, tenant_id, unit_id, installation_id, kind,
                            reference, read_on, reading, reading_status,
                            source, captured_at)
SELECT (substr(hr,1,8)||'-'||substr(hr,9,4)||'-5'||substr(hr,14,3)||'-'||
        (ARRAY['8','9','a','b'])[1 + (('x'||substr(hr,17,1))::bit(4)::int % 4)]||
        substr(hr,18,3)||'-'||substr(hr,21,12))::uuid,
       :'tenant_id'::uuid,
       (substr(hu,1,8)||'-'||substr(hu,9,4)||'-5'||substr(hu,14,3)||'-'||
        (ARRAY['8','9','a','b'])[1 + (('x'||substr(hu,17,1))::bit(4)::int % 4)]||
        substr(hu,18,3)||'-'||substr(hu,21,12))::uuid,
       (substr(hi,1,8)||'-'||substr(hi,9,4)||'-5'||substr(hi,14,3)||'-'||
        (ARRAY['8','9','a','b'])[1 + (('x'||substr(hi,17,1))::bit(4)::int % 4)]||
        substr(hi,18,3)||'-'||substr(hi,21,12))::uuid,
       'WATER', :'reference_month'::date,
       (:'reference_month'::date + INTERVAL '20 days')::date,
       ((g * 37) % 90000) + consumo, 'REALIZED', 'MOBILE',
       (:'reference_month'::date + INTERVAL '20 days')::timestamptz
  FROM (SELECT g, md5(:'semente' || ':reading:' || g) AS hr,
                   md5(:'semente' || ':unit:' || g) AS hu,
                   md5(:'semente' || ':installation:' || g) AS hi,
                   CASE WHEN g % 5 = 0 THEN 30 + ((g * 11) % 120)
                        ELSE 12 + ((g * 7) % 25) END AS consumo
          FROM generate_series(1, :ligacoes) AS g) s
ON CONFLICT DO NOTHING;

-- Consumo faturado (WATER) de todas as ligacoes na referencia: e a entrada
-- que o motor de conta consome. SEWER nao e semeado: o volume de esgoto e
-- derivado pelo motor (percentual sobre a agua), como na V4_1.
INSERT INTO consumption_records (id, tenant_id, unit_id, service_kind,
                                 reference, billed_consumption,
                                 calculated_consumption, billable,
                                 billing_paused, consumption_type,
                                 discarded_remainder, billed_reading,
                                 billed_reading_on)
SELECT (substr(hc,1,8)||'-'||substr(hc,9,4)||'-5'||substr(hc,14,3)||'-'||
        (ARRAY['8','9','a','b'])[1 + (('x'||substr(hc,17,1))::bit(4)::int % 4)]||
        substr(hc,18,3)||'-'||substr(hc,21,12))::uuid,
       :'tenant_id'::uuid,
       (substr(hu,1,8)||'-'||substr(hu,9,4)||'-5'||substr(hu,14,3)||'-'||
        (ARRAY['8','9','a','b'])[1 + (('x'||substr(hu,17,1))::bit(4)::int % 4)]||
        substr(hu,18,3)||'-'||substr(hu,21,12))::uuid,
       'WATER', :'reference_month'::date, consumo, consumo, (g <= :faturar), FALSE,
       'REAL', 0, ((g * 37) % 90000) + consumo,
       (:'reference_month'::date + INTERVAL '20 days')::date
  FROM (SELECT g, md5(:'semente' || ':consumption:' || g) AS hc,
                   md5(:'semente' || ':unit:' || g) AS hu,
                   CASE WHEN g % 5 = 0 THEN 30 + ((g * 11) % 120)
                        ELSE 12 + ((g * 7) % 25) END AS consumo
          FROM generate_series(1, :ligacoes) AS g) s
ON CONFLICT DO NOTHING;

COMMIT;
