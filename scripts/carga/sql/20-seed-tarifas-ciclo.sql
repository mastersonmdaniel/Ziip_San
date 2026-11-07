-- P2.9 -- Tarifa ficticia da carga + grupos de faturamento (rotas).
-- DDL: V4_1 (billing) e V4_3 (ciclo, P2.5) -- verificadas nos espelhos.
--
-- CORRECAO POS-REVISAO (achado 5): este seeder NAO insere mais a referencia
-- (billing_references) por SQL direto. Motivo: CycleService.openReference
-- (P2.5) lanca conflito se a referencia ja existe, e e ELE quem cria, junto
-- da referencia, o catalogo de atividades do produto e os
-- cycle_schedule_items (um por grupo x atividade, incl. a atividade
-- BILLING). Sem cronograma nao ha instancia da atividade BILLING para
-- comandar -- semear a referencia por SQL bloquearia o fluxo normal e
-- fingiria um ciclo que nao existe. Aqui ficam so tarifa e grupos/rotas;
-- a ABERTURA da referencia (com catalogo e cronograma) acontece na etapa
-- de disparo, via CycleService (BILLING_TRIGGER_CMD / fiacao M7), que deve
-- chamar openReference para a referencia da carga antes de comandar a
-- atividade BILLING dos grupos.
-- VALORES: os da fixture FictitiousTariffs do proprio produto, declarada
-- ficticia ("Nenhum destes valores e tarifa real de prestador nenhum"):
--   RES: min 10 m3, minima 30,00; ate 20 -> 4,00; ate 50 -> 6,00; aberta -> 9,00
--   COM: min 10 m3, minima 50,00; ate 20 -> 7,00; ate 50 -> 10,00; aberta -> 14,00
-- Sao valores DE CARGA, escolhidos para exercitar as 3 faixas e o minimo --
-- nao sao politica tarifaria de ninguem.
-- Variaveis psql: tenant_id, semente, reference_month, ligacoes, faturar.
-- Convencao de ids: tarifa/versao/regas deterministicos pequenos (uuid 0,n),
-- no mesmo estilo da fixture; grupos de faturamento: 100 grupos (1 por rota).
\set ON_ERROR_STOP on
BEGIN;

INSERT INTO tariffs (id, tenant_id, code, name, calc_type, active)
VALUES ('00000000-0000-0000-0000-000000000100', :'tenant_id'::uuid,
        'CARGA-P29', 'Tarifa ficticia da carga P2.9', 'BLOCKS', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO tariff_versions (id, tenant_id, tariff_id, valid_from, valid_to,
                             social_min_charge_discount_pct)
VALUES ('00000000-0000-0000-0000-000000000101', :'tenant_id'::uuid,
        '00000000-0000-0000-0000-000000000100', DATE '2020-01-01', NULL, NULL)
ON CONFLICT DO NOTHING;

-- Regras por categoria (RES id ...001, COM id ...002 -- ver 10-seed-registry).
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

-- Grupos de faturamento: 100 grupos, um por rota da massa (ROTA-001..100).
INSERT INTO billing_groups (id, tenant_id, code, name, route_code, active)
SELECT (substr(h,1,8)||'-'||substr(h,9,4)||'-5'||substr(h,14,3)||'-'||
        (ARRAY['8','9','a','b'])[1 + (('x'||substr(h,17,1))::bit(4)::int % 4)]||
        substr(h,18,3)||'-'||substr(h,21,12))::uuid,
       :'tenant_id'::uuid, 'CARGA-' || lpad(g::text, 3, '0'),
       'Grupo de carga P2.9 ' || g, 'ROTA-' || lpad(g::text, 3, '0'), TRUE
  FROM (SELECT g, md5(:'semente' || ':group:' || g) AS h
          FROM generate_series(1, 100) AS g) s
ON CONFLICT DO NOTHING;

-- (Sem INSERT em billing_references / cycle_activities /
--  cycle_schedule_items: ver a nota de correcao no cabecalho -- a abertura
--  da referencia e do CycleService, na etapa de disparo.)

COMMIT;
