-- P3.23 -- Sementes do tenant de DEMONSTRACAO.
-- Proveniencia: adaptacao direta das sementes verificadas da P2.9
-- (scripts/carga/sql/10, 20 e 30), trocando o rotulo da massa para
-- DEMO-P323, a variavel ligacoes por unidades e o subconjunto faturavel:
-- na demonstracao TODAS as unidades sao faturaveis (billable = TRUE),
-- porque a POC demonstra o ciclo completo sobre a massa inteira.
-- Formulas e UUIDs: os mesmos do gerar-massa-demo.py (fonte unica la).
-- Variaveis psql: tenant_id, semente, unidades, reference_month.
--
-- *** TRECHO [CONFERIR-V3_1] -- LEIA ANTES DE RODAR ***
-- A DDL da V3_1 (registry: consumer_units, categories, subcategories,
-- titulares/documentos e os vinculos) NAO estava verificada nos espelhos
-- locais, e o espelho via MCP foi interrompido por HTTP 429 (mesma
-- situacao registrada no relatorio da P2.9). Por isso:
--   * categorias entram com o INSERT minimo marcado, como na P2.9;
--   * unidades e titulares/documentos ficam em ESQUELETO COMENTADO:
--     a massa canonica deles e o unidades.csv do gerador (titular
--     sintetico, documento com DV e MARCA SINTETICO em toda linha);
--     o INSERT real so pode ser escrito depois de conferir, no PC do
--     dono, \d consumer_units e a tabela de titulares contra a V3_1;
--   * o orquestrador PARA na contagem de unidades se o esqueleto nao
--     tiver sido materializado -- nada e dado por semeado sem medida.
-- Todo o restante (tarifas V4_1, grupos V4_3, medicao V5_1) usa so DDL
-- verificada nos espelhos, igual a P2.9.
--
-- *** REVISAO CRUZADA P3.23 -- DUAS TRANSACOES, nao uma ***
-- A V5_1 (verificada no espelho) tem FKs de meter_installations,
-- meter_readings e consumption_records para consumer_units
-- (fk_installation_unit, fk_reading_unit, fk_consumption_unit). Com o
-- trecho (2) comentado, a medicao viola a FK: numa transacao UNICA o
-- aborto derrubava tambem categorias/tarifas/grupos ja inseridos -- a
-- afirmacao "tarifas/medicao semeiam" nao se sustentava. Por isso:
-- transacao 1 = secoes (1) e (3), sem dependencia de consumer_units,
-- comita sozinha; transacao 2 = secao (4) medicao, que so pode rodar
-- DEPOIS de o trecho (2) ser materializado contra a V3_1. Sem isso ela
-- falha fechada na FK, sem tocar na transacao 1.
\set ON_ERROR_STOP on
BEGIN;

-- (1) CATEGORIAS [CONFERIR-V3_1: colunas alem de id/tenant_id/code/name]
INSERT INTO categories (id, tenant_id, code, name)  -- [CONFERIR-V3_1]
VALUES ('00000000-0000-0000-0000-000000000001', :'tenant_id'::uuid, 'RES', 'Residencial (massa sintetica P3.23)'),
       ('00000000-0000-0000-0000-000000000002', :'tenant_id'::uuid, 'COM', 'Comercial (massa sintetica P3.23)')
ON CONFLICT DO NOTHING;

-- (2) UNIDADES E TITULARES -- ESQUELETO [CONFERIR-V3_1]
-- ids deterministicos identicos aos do unidades.csv e da medicao abaixo:
-- INSERT INTO consumer_units (id, tenant_id, /* [CONFERIR-V3_1] categoria,
--     economias, rota, endereco (cep/street_name/address_number do
--     RegistryCsvSchema da P1.3) */)
-- SELECT (substr(h,1,8)||'-'||substr(h,9,4)||'-5'||substr(h,14,3)||'-'||
--         (ARRAY['8','9','a','b'])[1 + (('x'||substr(h,17,1))::bit(4)::int % 4)]||
--         substr(h,18,3)||'-'||substr(h,21,12))::uuid,
--        :'tenant_id'::uuid /* , ... */
--   FROM (SELECT md5(:'semente' || ':unit:' || g) AS h
--           FROM generate_series(1, :unidades) AS g) s
-- ON CONFLICT DO NOTHING;
-- TITULAR/DOCUMENTO: o titular sintetico e o documento marcado vivem no
-- unidades.csv (colunas titular, tipo_documento, documento,
-- documento_sintetico=SIM, marca=SINTETICO). A tabela/coluna do documento
-- no cadastro mora na V3_1: ao materializar, grave a marca junto do
-- documento (a varredura da P0.45 recusa documento sem a marca).

-- (3) TARIFA FICTICIA + GRUPOS (V4_1/V4_3 verificadas; valores da fixture
-- FictitiousTariffs do produto, declarada ficticia -- mesmos da P2.9).
-- A referencia NAO e semeada por SQL: quem abre e o CycleService
-- (openReference), como a P2.9 registrou (correcao pos-revisao dela).
INSERT INTO tariffs (id, tenant_id, code, name, calc_type, active)
VALUES ('00000000-0000-0000-0000-000000000100', :'tenant_id'::uuid,
        'DEMO-P323', 'Tarifa ficticia da demonstracao P3.23', 'BLOCKS', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO tariff_versions (id, tenant_id, tariff_id, valid_from, valid_to,
                             social_min_charge_discount_pct)
VALUES ('00000000-0000-0000-0000-000000000101', :'tenant_id'::uuid,
        '00000000-0000-0000-0000-000000000100', DATE '2020-01-01', NULL, NULL)
ON CONFLICT DO NOTHING;

INSERT INTO tariff_category_rules (id, tenant_id, version_id, category_id,
                                   subcategory_id, min_consumption, min_charge, economy_factor)
VALUES ('00000000-0000-0000-0000-000000000111', :'tenant_id'::uuid,
        '00000000-0000-0000-0000-000000000101',
        '00000000-0000-0000-0000-000000000001', NULL, 10, 30.0000, NULL),
       ('00000000-0000-0000-0000-000000000112', :'tenant_id'::uuid,
        '00000000-0000-0000-0000-000000000101',
        '00000000-0000-0000-0000-000000000002', NULL, 10, 50.0000, NULL)
ON CONFLICT DO NOTHING;

INSERT INTO tariff_blocks (id, tenant_id, rule_id, block_order, to_m3, price_per_m3)
VALUES
 ('00000000-0000-0000-0000-000000000121', :'tenant_id'::uuid, '00000000-0000-0000-0000-000000000111', 1, 20, 4.0000),
 ('00000000-0000-0000-0000-000000000122', :'tenant_id'::uuid, '00000000-0000-0000-0000-000000000111', 2, 50, 6.0000),
 ('00000000-0000-0000-0000-000000000123', :'tenant_id'::uuid, '00000000-0000-0000-0000-000000000111', 3, NULL, 9.0000),
 ('00000000-0000-0000-0000-000000000124', :'tenant_id'::uuid, '00000000-0000-0000-0000-000000000112', 1, 20, 7.0000),
 ('00000000-0000-0000-0000-000000000125', :'tenant_id'::uuid, '00000000-0000-0000-0000-000000000112', 2, 50, 10.0000),
 ('00000000-0000-0000-0000-000000000126', :'tenant_id'::uuid, '00000000-0000-0000-0000-000000000112', 3, NULL, 14.0000)
ON CONFLICT DO NOTHING;

INSERT INTO billing_groups (id, tenant_id, code, name, route_code, active)
SELECT (substr(h,1,8)||'-'||substr(h,9,4)||'-5'||substr(h,14,3)||'-'||
        (ARRAY['8','9','a','b'])[1 + (('x'||substr(h,17,1))::bit(4)::int % 4)]||
        substr(h,18,3)||'-'||substr(h,21,12))::uuid,
       :'tenant_id'::uuid, 'DEMO-' || lpad(g::text, 3, '0'),
       'Grupo de demonstracao P3.23 ' || g, 'ROTA-' || lpad(g::text, 3, '0'), TRUE
  FROM (SELECT g, md5(:'semente' || ':group:' || g) AS h
          FROM generate_series(1, 100) AS g) s
ON CONFLICT DO NOTHING;

COMMIT;

BEGIN;

-- (4) MEDICAO (V5_1 verificada): hidrometros, instalacoes, leituras e
-- consumo de TODAS as unidades da demo, billable = TRUE para todas.
-- Depende das unidades do trecho (2) materializadas: as FKs da V5_1
-- (instalacao/leitura/consumo -> consumer_units) abortam ESTA
-- transacao, e so ela, enquanto elas nao existirem.
INSERT INTO meters (id, tenant_id, meter_number, digits, status)
SELECT (substr(h,1,8)||'-'||substr(h,9,4)||'-5'||substr(h,14,3)||'-'||
        (ARRAY['8','9','a','b'])[1 + (('x'||substr(h,17,1))::bit(4)::int % 4)]||
        substr(h,18,3)||'-'||substr(h,21,12))::uuid,
       :'tenant_id'::uuid, 'DEMO-' || lpad(g::text, 7, '0'), 5, 'INSTALLED'
  FROM (SELECT g, md5(:'semente' || ':meter:' || g) AS h
          FROM generate_series(1, :unidades) AS g) s
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
          FROM generate_series(1, :unidades) AS g) s
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
          FROM generate_series(1, :unidades) AS g) s
ON CONFLICT DO NOTHING;

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
       'WATER', :'reference_month'::date, consumo, consumo, TRUE, FALSE,
       'REAL', 0, ((g * 37) % 90000) + consumo,
       (:'reference_month'::date + INTERVAL '20 days')::date
  FROM (SELECT g, md5(:'semente' || ':consumption:' || g) AS hc,
                   md5(:'semente' || ':unit:' || g) AS hu,
                   CASE WHEN g % 5 = 0 THEN 30 + ((g * 11) % 120)
                        ELSE 12 + ((g * 7) % 25) END AS consumo
          FROM generate_series(1, :unidades) AS g) s
ON CONFLICT DO NOTHING;

COMMIT;
