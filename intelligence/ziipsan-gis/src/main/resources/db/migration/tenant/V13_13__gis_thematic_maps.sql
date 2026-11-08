-- GIS-08 (P3.27) — tematicos configuraveis, precisao, OS no mapa e
-- relatorios do cadastro (GIS-CADASTRO-TECNICO §3.4 "Publicacao",
-- §3.8 metadados de captura e qualidade, §10.3 mapa web, §11
-- integracoes e §12 LGPD; ADR-0034; ED-52).
--
-- NUMERACAO: a spec (§3.4) e o plano v3.0 reservavam a V13_8 para a
-- gis_thematic_map e as views do relatorio de extensao, mas no repo
-- real a V13_8 ja foi consumida (gis_topology_validation, GIS-03/
-- P2.21, na main) e a serie tenant do GIS segue ocupada ate a V13_12
-- (GIS-06/P3.25, publicacao). Precedente do projeto (V13_9, V13_10,
-- V13_11, V13_12): slot ocupado -> proxima versao livre da serie,
-- sem editar migration aplicada. Esta e a V13_13. Evidencia:
-- listagem via MCP da pasta de migrations na main (V2_3, V13_1 a
-- V13_8) e os espelhos locais das entregas P2.22 (V13_9), P2.23
-- (V13_10), P3.26 (V13_11) e P3.25 (V13_12); a conferencia final na
-- main e da integracao, antes do push.
--
-- O QUE ESTA MIGRATION FAZ:
--   (1) gis_thematic_map: a definicao do tematico (spec §3.4) —
--       fonte (view da lista fechada do GisThematicSources), campo
--       de categoria, campo de medida numerica, classes e filtros
--       em JSONB (sempre lidos/gravados pela classe Json do core no
--       Java), regra de agregacao publica e o marcador personal
--       (fonte ligada a imovel/ligacao: §12, itens 1 a 3);
--   (2) views-fonte dos tematicos (security_invoker, como a
--       gis_domain_visible da V13_1): base da unidade (categoria,
--       quadra, setor, economias), consumo vigente, ligacoes por
--       situacao, hidrometros vigentes, faturas vencidas ha mais de
--       60 dias, OS com posicao resolvida, arcos de rede e feicoes
--       com o grupo de precisao do §3.8;
--   (3) relatorio de extensao: a view rpt_gis_extensao_rede (foto
--       na data corrente, regra de "rede na data" do §3.7) para o
--       nucleo de relatorios da P3.22, e a funcao
--       gis_network_extension_at(date) para a mesma conta em
--       qualquer data, agrupada por dominio, funcao, material, DN,
--       decada de instalacao e setor;
--   (4) relatorio de precisao: a view rpt_gis_precisao_cadastro
--       (uma linha por feicao, para o nucleo agrupar por classe,
--       metodo e grupo de precisao);
--   (5) sementes de fabrica dos cenarios do aceite (SG-873 a
--       SG-883, TR-1060 a TR-1063, SG-491/TR-534, TR-1059 e da
--       precisao), SEM limiar inventado: o cenario de grandes
--       consumidores (SG-882) nasce sem classes numericas — nenhum
--       documento do plano fixa o corte — e os cenarios de OS de
--       vazamento/falta d'agua (SG-877 a SG-879) nascem sem codigos
--       de tipo de OS, porque service_types e catalogo do tenant
--       (V9_1, sem semente); os dois casos so rendem depois da
--       configuracao do tenant, com recusa explicita antes disso
--       (mesma doutrina do "nenhum dado e semeado" da V4_1).
--
-- O QUE ESTA MIGRATION NAO FAZ (de proposito):
--   * nao publica geometria de imovel/ligacao fora do backoffice:
--     isso e regra do servico Java (agregacao com o minimo de
--     gis.public_aggregation_min_units, piso 5, classe c — core
--     V9_6), nao da view; a view-fonte e de uso interno da api;
--   * nao cria a quadra geometrica: blocks (V3_1) nao tem
--     geometria; o agregado publico por setor usa o poligono da
--     gis_zone COMMERCIAL_SECTOR e o por quadra sai tabular;
--   * nao altera service_orders: a posicao da OS e resolvida na
--     view gis_thematic_service_order a partir dos alvos da OS
--     (coordenada propria, unidade consumidora ou feicao GIS).

SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- ======================================================================
-- (1) gis_thematic_map — definicao do tematico (spec §3.4)
-- ======================================================================

CREATE TABLE IF NOT EXISTS gis_thematic_map (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    key TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    source_kind TEXT NOT NULL CHECK (source_kind IN
        ('UNIT_CONSUMPTION', 'CONNECTION_STATUS', 'METER_AGE',
         'UNIT_OVERDUE', 'SERVICE_ORDER', 'NETWORK_ARC', 'PRECISION')),
    category_field TEXT,
    measure_field TEXT,
    personal BOOLEAN NOT NULL DEFAULT FALSE,
    aggregation TEXT NOT NULL DEFAULT 'NONE' CHECK (aggregation IN
        ('NONE', 'BLOCK', 'SECTOR')),
    classes_json JSONB NOT NULL DEFAULT '[]',
    filter_json JSONB NOT NULL DEFAULT '{}',
    origin TEXT NOT NULL CHECK (origin IN ('FACTORY', 'TENANT')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_thematic_map_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_thematic_map_tenant_key UNIQUE (tenant_id, key),
    CONSTRAINT ck_gis_thematic_map_key
        CHECK (key ~ '^[a-z0-9][a-z0-9-]{2,59}$'),
    CONSTRAINT ck_gis_thematic_map_classes_array
        CHECK (jsonb_typeof(classes_json) = 'array'),
    CONSTRAINT ck_gis_thematic_map_filter_object
        CHECK (jsonb_typeof(filter_json) = 'object'),
    CONSTRAINT ck_gis_thematic_map_personal_aggregation
        CHECK (NOT personal OR aggregation IN ('BLOCK', 'SECTOR'))
);

SELECT register_tenant_table('gis_thematic_map');

CREATE INDEX IF NOT EXISTS idx_gis_thematic_map_source
    ON gis_thematic_map (tenant_id, source_kind) WHERE active;

-- ======================================================================
-- (2) Views-fonte dos tematicos
-- ======================================================================

-- Base da unidade consumidora para os tematicos pessoais: ponto do
-- imovel (consumer_units.coordinates, GIS-05), quadra e setor do
-- cadastro comercial (V3_1) e a categoria principal da unidade
-- (SG-883: a de menor codigo entre as subcategorias vigentes, para
-- a escolha ser deterministica). Unidade excluida (deleted_at) nao
-- entra em tematico. As duas subconsultas aplicam o predicado de
-- vigencia canonico de unit_subcategories (V3_12; consumidores da
-- ED-13): valid_from <= CURRENT_DATE AND (valid_to IS NULL OR
-- valid_to >= CURRENT_DATE) — sem ele, categoria expirada venceria
-- o desempate e as economias somariam dois periodos.
CREATE OR REPLACE VIEW gis_thematic_unit_base
    WITH (security_invoker = true) AS
SELECT u.tenant_id,
       u.id AS unit_id,
       u.coordinates AS geom,
       u.block_id,
       b.sector_id,
       (SELECT c.code
          FROM unit_subcategories us
          JOIN subcategories s
            ON s.tenant_id = us.tenant_id AND s.id = us.subcategory_id
          JOIN categories c
            ON c.tenant_id = s.tenant_id AND c.id = s.category_id
         WHERE us.tenant_id = u.tenant_id AND us.unit_id = u.id
           AND us.valid_from <= CURRENT_DATE
           AND (us.valid_to IS NULL OR us.valid_to >= CURRENT_DATE)
         ORDER BY c.code, s.code
         LIMIT 1) AS category_code,
       (SELECT COALESCE(SUM(us.economies), 0)
          FROM unit_subcategories us
         WHERE us.tenant_id = u.tenant_id AND us.unit_id = u.id
           AND us.valid_from <= CURRENT_DATE
           AND (us.valid_to IS NULL OR us.valid_to >= CURRENT_DATE)) AS economies
  FROM consumer_units u
  LEFT JOIN blocks b
    ON b.tenant_id = u.tenant_id AND b.id = u.block_id
 WHERE u.deleted_at IS NULL;

-- Consumo vigente da unidade (ultima referencia nao substituida do
-- metering, P2.5): comparacao com a media do proprio registro
-- (SG-873), zero (SG-875) e faturado pela media (SG-874:
-- consumption_type = METER_AVERAGE, "consumo igual a media do
-- hidrometro" do ConsumptionType; ADJUSTED_AVERAGE e proporcional
-- aos dias, nao e a media do hidrometro, e fica fora de proposito).
CREATE OR REPLACE VIEW gis_thematic_unit_consumption
    WITH (security_invoker = true) AS
SELECT b.tenant_id, b.unit_id, b.geom, b.category_code, b.block_id,
       b.sector_id,
       cr.reference AS reference_month,
       cr.billed_consumption,
       cr.average_consumption,
       cr.consumption_type,
       CASE
           WHEN cr.average_consumption IS NULL THEN 'NO_AVERAGE'
           WHEN cr.billed_consumption < cr.average_consumption
               THEN 'BELOW_AVERAGE'
           WHEN cr.billed_consumption > cr.average_consumption
               THEN 'ABOVE_AVERAGE'
           ELSE 'EQUAL_AVERAGE'
       END AS comparison,
       (cr.billed_consumption = 0) AS zero_consumption,
       (cr.consumption_type = 'METER_AVERAGE') AS billed_by_average
  FROM gis_thematic_unit_base b
  JOIN LATERAL (
        SELECT c.reference, c.billed_consumption, c.average_consumption,
               c.consumption_type
          FROM consumption_records c
         WHERE c.tenant_id = b.tenant_id AND c.unit_id = b.unit_id
           AND c.service_kind = 'WATER'
           AND c.superseded_by_id IS NULL
         ORDER BY c.reference DESC
         LIMIT 1
       ) cr ON true
 WHERE b.geom IS NOT NULL;

-- Faturas vencidas ha mais de 60 dias (SG-881): conta viva (fora
-- paga, cancelada e erro de processamento) com due_date anterior a
-- CURRENT_DATE - 60 dias. O "60" e o texto do proprio cenario.
CREATE OR REPLACE VIEW gis_thematic_unit_overdue
    WITH (security_invoker = true) AS
SELECT b.tenant_id, b.unit_id, b.geom, b.category_code, b.block_id,
       b.sector_id,
       'OVERDUE_60' AS overdue_group,
       agg.overdue_bills,
       agg.oldest_due_date,
       agg.total_due
  FROM gis_thematic_unit_base b
  JOIN (
        SELECT tenant_id, unit_id, COUNT(*) AS overdue_bills,
               MIN(due_date) AS oldest_due_date,
               SUM(total_value) AS total_due
          FROM bills
         WHERE status NOT IN ('PAID', 'CANCELLED',
                              'CANCELLED_BY_RECTIFICATION',
                              'PROCESSING_ERROR')
           AND due_date < CURRENT_DATE - 60
         GROUP BY tenant_id, unit_id
       ) agg
    ON agg.tenant_id = b.tenant_id AND agg.unit_id = b.unit_id
 WHERE b.geom IS NOT NULL;

-- Ligacoes por situacao (SG-876): o papel do sistema da situacao
-- (connection_statuses.system_role da V3_14) ja traz o vocabulario
-- do cenario — CUT (cortada), SUSPENDED (suprimida) e DISCONNECTED
-- (desligada); o recorte e filtro do tematico, nao da view.
CREATE OR REPLACE VIEW gis_thematic_connection_status
    WITH (security_invoker = true) AS
SELECT b.tenant_id, b.unit_id, b.geom, b.category_code, b.block_id,
       b.sector_id,
       sc.id AS connection_id,
       sc.kind,
       cs.code AS status_code,
       cs.system_role
  FROM gis_thematic_unit_base b
  JOIN service_connection_units scu
    ON scu.tenant_id = b.tenant_id AND scu.unit_id = b.unit_id
  JOIN service_connections sc
    ON sc.tenant_id = scu.tenant_id AND sc.id = scu.connection_id
  JOIN connection_statuses cs
    ON cs.tenant_id = sc.tenant_id AND cs.id = sc.status_id
 WHERE b.geom IS NOT NULL
   AND sc.ended_on IS NULL;

-- Hidrometros vigentes por tempo de instalacao e tipo (SG-880):
-- instalacao vigente (removed_on nula) de agua; tipo = modelo do
-- hidrometro (ou a marca, quando nao ha modelo).
CREATE OR REPLACE VIEW gis_thematic_meter_age
    WITH (security_invoker = true) AS
SELECT b.tenant_id, b.unit_id, b.geom, b.category_code, b.block_id,
       b.sector_id,
       mi.meter_id,
       COALESCE(m.model, m.brand) AS meter_type,
       m.brand,
       m.model,
       m.metrological_class,
       mi.installed_on,
       DATE_PART('year', AGE(CURRENT_DATE, mi.installed_on))::int
           AS age_years
  FROM gis_thematic_unit_base b
  JOIN meter_installations mi
    ON mi.tenant_id = b.tenant_id AND mi.unit_id = b.unit_id
   AND mi.kind = 'WATER' AND mi.removed_on IS NULL
  JOIN meters m
    ON m.tenant_id = mi.tenant_id AND m.id = mi.meter_id
 WHERE b.geom IS NOT NULL;

-- OS no mapa (SG-491, TR-534, TR-1061): service_orders nao tem
-- geometria propria (V9_1); a posicao e a do alvo, por precedencia:
-- (1) coordenada gravada no alvo, (2) ponto da unidade consumidora
-- alvo, (3) centroide da feicao GIS alvo. OS sem posicao resolvida
-- nao entra na view (nao se inventa ponto). Equipe = usuarios da
-- service_order_crew (ids; nome de pessoa nao trafega no mapa).
CREATE OR REPLACE VIEW gis_thematic_service_order
    WITH (security_invoker = true) AS
SELECT o.tenant_id,
       o.id AS order_id,
       st.code AS service_type_code,
       st.name AS service_type_name,
       o.status,
       o.priority,
       o.scheduled_for,
       o.created_at,
       addr.location_text AS address_text,
       crew.crew_user_ids,
       pos.geom
  FROM service_orders o
  JOIN service_types st
    ON st.tenant_id = o.tenant_id AND st.id = o.service_type_id
  LEFT JOIN LATERAL (
        SELECT t.location_text
          FROM service_order_targets t
         WHERE t.tenant_id = o.tenant_id AND t.order_id = o.id
           AND t.location_text IS NOT NULL
         LIMIT 1
       ) addr ON true
  LEFT JOIN LATERAL (
        SELECT array_agg(DISTINCT c.user_id) AS crew_user_ids
          FROM service_order_crew c
         WHERE c.tenant_id = o.tenant_id AND c.order_id = o.id
       ) crew ON true
  JOIN LATERAL (
        SELECT g.geom
          FROM (
                SELECT 1 AS prec, t.coordinates AS geom
                  FROM service_order_targets t
                 WHERE t.tenant_id = o.tenant_id AND t.order_id = o.id
                   AND t.coordinates IS NOT NULL
                UNION ALL
                SELECT 2, u.coordinates
                  FROM service_order_targets t
                  JOIN consumer_units u
                    ON u.tenant_id = t.tenant_id AND u.id = t.target_id
                 WHERE t.tenant_id = o.tenant_id AND t.order_id = o.id
                   AND t.target_type = 'CONSUMER_UNIT'
                   AND u.coordinates IS NOT NULL
                UNION ALL
                SELECT 3, ST_Centroid(f.geom)
                  FROM service_order_targets t
                  JOIN (
                        SELECT id, geom, tenant_id FROM gis_node
                        UNION ALL SELECT id, geom, tenant_id FROM gis_arc
                        UNION ALL SELECT id, COALESCE(geom, geom_point), tenant_id
                                  FROM gis_structure
                        UNION ALL SELECT id, geom, tenant_id FROM gis_connection
                        UNION ALL SELECT id, geom, tenant_id FROM gis_zone
                        UNION ALL SELECT id, geom, tenant_id FROM gis_poi
                       ) f
                    ON f.id = t.target_id AND f.tenant_id = t.tenant_id
                 WHERE t.tenant_id = o.tenant_id AND t.order_id = o.id
                   AND t.target_type IN ('GIS_POINT', 'GIS_LINEAR')
               ) g
         ORDER BY g.prec
         LIMIT 1
       ) pos ON true;

-- Arcos de rede para os tematicos de rede em operacao ou
-- implantacao (TR-1062 agua, TR-1063 esgoto): o dominio vem da
-- classe da feicao, como nos tiles da GIS-06.
CREATE OR REPLACE VIEW gis_thematic_network_arc
    WITH (security_invoker = true) AS
SELECT a.tenant_id,
       a.id AS feature_id,
       fc.domain,
       ft.code AS type_code,
       a.lifecycle_status,
       a.operational_status,
       a.accuracy_status,
       a.network_function,
       a.length_m,
       a.geom
  FROM gis_arc a
  JOIN gis_feature_type ft
    ON ft.tenant_id = a.tenant_id AND ft.id = a.feature_type_id
  JOIN gis_feature_class fc
    ON fc.tenant_id = ft.tenant_id AND fc.id = ft.class_id
 WHERE a.geom IS NOT NULL;

-- Precisao do cadastro (§3.8): uma linha por feicao das seis
-- tabelas de papel, com o grupo que o tematico e o relatorio
-- exibem — levantado na meta (WITHIN_TARGET), levantado fora da
-- meta (OUT_OF_TARGET) e aproximado (APPROXIMATE); UNKNOWN fica
-- explicito, nunca fundido com aproximado.
CREATE OR REPLACE VIEW gis_thematic_precision_feature
    WITH (security_invoker = true) AS
SELECT f.tenant_id, f.id AS feature_id, 'gis_node' AS feature_table,
       fc.code AS class_code, fc.domain, f.capture_method,
       f.accuracy_status,
       CASE f.accuracy_status
           WHEN 'WITHIN_TARGET' THEN 'SURVEYED_WITHIN_TARGET'
           WHEN 'OUT_OF_TARGET' THEN 'SURVEYED_OUT_OF_TARGET'
           WHEN 'APPROXIMATE' THEN 'APPROXIMATE'
           ELSE 'UNKNOWN'
       END AS precision_group,
       ST_Centroid(f.geom) AS geom
  FROM gis_node f
  JOIN gis_feature_type ft
    ON ft.tenant_id = f.tenant_id AND ft.id = f.feature_type_id
  JOIN gis_feature_class fc
    ON fc.tenant_id = ft.tenant_id AND fc.id = ft.class_id
UNION ALL
SELECT f.tenant_id, f.id, 'gis_arc', fc.code, fc.domain,
       f.capture_method, f.accuracy_status,
       CASE f.accuracy_status
           WHEN 'WITHIN_TARGET' THEN 'SURVEYED_WITHIN_TARGET'
           WHEN 'OUT_OF_TARGET' THEN 'SURVEYED_OUT_OF_TARGET'
           WHEN 'APPROXIMATE' THEN 'APPROXIMATE'
           ELSE 'UNKNOWN'
       END,
       ST_Centroid(f.geom)
  FROM gis_arc f
  JOIN gis_feature_type ft
    ON ft.tenant_id = f.tenant_id AND ft.id = f.feature_type_id
  JOIN gis_feature_class fc
    ON fc.tenant_id = ft.tenant_id AND fc.id = ft.class_id
UNION ALL
SELECT f.tenant_id, f.id, 'gis_connection', fc.code, fc.domain,
       f.capture_method, f.accuracy_status,
       CASE f.accuracy_status
           WHEN 'WITHIN_TARGET' THEN 'SURVEYED_WITHIN_TARGET'
           WHEN 'OUT_OF_TARGET' THEN 'SURVEYED_OUT_OF_TARGET'
           WHEN 'APPROXIMATE' THEN 'APPROXIMATE'
           ELSE 'UNKNOWN'
       END,
       ST_Centroid(f.geom)
  FROM gis_connection f
  JOIN gis_feature_type ft
    ON ft.tenant_id = f.tenant_id AND ft.id = f.feature_type_id
  JOIN gis_feature_class fc
    ON fc.tenant_id = ft.tenant_id AND fc.id = ft.class_id
UNION ALL
SELECT f.tenant_id, f.id, 'gis_structure', fc.code, fc.domain,
       f.capture_method, f.accuracy_status,
       CASE f.accuracy_status
           WHEN 'WITHIN_TARGET' THEN 'SURVEYED_WITHIN_TARGET'
           WHEN 'OUT_OF_TARGET' THEN 'SURVEYED_OUT_OF_TARGET'
           WHEN 'APPROXIMATE' THEN 'APPROXIMATE'
           ELSE 'UNKNOWN'
       END,
       ST_Centroid(COALESCE(f.geom, f.geom_point))
  FROM gis_structure f
  JOIN gis_feature_type ft
    ON ft.tenant_id = f.tenant_id AND ft.id = f.feature_type_id
  JOIN gis_feature_class fc
    ON fc.tenant_id = ft.tenant_id AND fc.id = ft.class_id
UNION ALL
SELECT f.tenant_id, f.id, 'gis_zone', fc.code, fc.domain,
       f.capture_method, f.accuracy_status,
       CASE f.accuracy_status
           WHEN 'WITHIN_TARGET' THEN 'SURVEYED_WITHIN_TARGET'
           WHEN 'OUT_OF_TARGET' THEN 'SURVEYED_OUT_OF_TARGET'
           WHEN 'APPROXIMATE' THEN 'APPROXIMATE'
           ELSE 'UNKNOWN'
       END,
       ST_Centroid(f.geom)
  FROM gis_zone f
  JOIN gis_feature_type ft
    ON ft.tenant_id = f.tenant_id AND ft.id = f.feature_type_id
  JOIN gis_feature_class fc
    ON fc.tenant_id = ft.tenant_id AND fc.id = ft.class_id
UNION ALL
SELECT f.tenant_id, f.id, 'gis_poi', fc.code, fc.domain,
       f.capture_method, f.accuracy_status,
       CASE f.accuracy_status
           WHEN 'WITHIN_TARGET' THEN 'SURVEYED_WITHIN_TARGET'
           WHEN 'OUT_OF_TARGET' THEN 'SURVEYED_OUT_OF_TARGET'
           WHEN 'APPROXIMATE' THEN 'APPROXIMATE'
           ELSE 'UNKNOWN'
       END,
       ST_Centroid(f.geom)
  FROM gis_poi f
  JOIN gis_feature_type ft
    ON ft.tenant_id = f.tenant_id AND ft.id = f.feature_type_id
  JOIN gis_feature_class fc
    ON fc.tenant_id = ft.tenant_id AND fc.id = ft.class_id;

-- ======================================================================
-- (3) Relatorio de extensao (nucleo da P3.22) e funcao "na data"
-- ======================================================================

-- Uma linha por arco na data corrente (regra do §3.7:
-- installed_on <= data e abandoned_on nula ou > data), com as
-- dimensoes do aceite. O nucleo da P3.22 filtra/agrupa/soma
-- (extensao_m e arcos sao somaveis). O setor e o da gis_zone
-- COMMERCIAL_SECTOR que cobre o centroide do arco (a ligacao
-- zona -> setor comercial e a gis_zone.commercial_sector_id do
-- §3.6.5); arco fora de zona de setor sai com setor nulo.
CREATE OR REPLACE VIEW rpt_gis_extensao_rede
    WITH (security_invoker = true) AS
SELECT a.tenant_id,
       fc.domain AS dominio,
       a.network_function AS funcao,
       m.code AS material,
       ci.dn_mm,
       CASE WHEN a.installed_on IS NULL THEN NULL
            ELSE (FLOOR(EXTRACT(YEAR FROM a.installed_on) / 10) * 10)::int
       END AS decada_inicio,
       sec.setor_codigo,
       sec.setor,
       a.length_m AS extensao_m,
       1 AS arcos
  FROM gis_arc a
  JOIN gis_feature_type ft
    ON ft.tenant_id = a.tenant_id AND ft.id = a.feature_type_id
  JOIN gis_feature_class fc
    ON fc.tenant_id = ft.tenant_id AND fc.id = ft.class_id
  LEFT JOIN gis_catalog_item ci
    ON ci.tenant_id = a.tenant_id AND ci.id = a.catalog_item_id
  LEFT JOIN gis_material m
    ON m.tenant_id = ci.tenant_id AND m.id = ci.material_id
  LEFT JOIN LATERAL (
        SELECT cs.code AS setor_codigo, cs.name AS setor
          FROM gis_zone z
          JOIN commercial_sectors cs
            ON cs.tenant_id = z.tenant_id
           AND cs.id = z.commercial_sector_id
         WHERE z.tenant_id = a.tenant_id
           AND z.zone_type = 'COMMERCIAL_SECTOR'
           AND z.commercial_sector_id IS NOT NULL
           AND ST_Covers(z.geom, ST_Centroid(a.geom))
         LIMIT 1
       ) sec ON true
 WHERE a.installed_on IS NOT NULL
   AND a.installed_on <= CURRENT_DATE
   AND (a.abandoned_on IS NULL OR a.abandoned_on > CURRENT_DATE);

-- A mesma conta em qualquer data, ja agrupada: e a funcao que o
-- endpoint de relatorio do GIS chama (o nucleo da P3.22 nao
-- expressa a regra de dois tempos da "rede na data" — ver o
-- cabecalho). A soma de extensao_m e o ST_Length geodesico da
-- coluna gerada da V13_2: soma igual ao calculo geodesico.
CREATE OR REPLACE FUNCTION gis_network_extension_at(p_date date)
RETURNS TABLE (
    dominio text, funcao text, material text, dn_mm int,
    decada_inicio int, setor_codigo text, setor text,
    extensao_m numeric, arcos bigint
)
LANGUAGE sql
STABLE
AS $$
    SELECT fc.domain,
           a.network_function,
           m.code,
           ci.dn_mm,
           CASE WHEN a.installed_on IS NULL THEN NULL
                ELSE (FLOOR(EXTRACT(YEAR FROM a.installed_on) / 10) * 10)::int
           END,
           sec.setor_codigo,
           sec.setor,
           SUM(a.length_m),
           COUNT(*)
      FROM gis_arc a
      JOIN gis_feature_type ft
        ON ft.tenant_id = a.tenant_id AND ft.id = a.feature_type_id
      JOIN gis_feature_class fc
        ON fc.tenant_id = ft.tenant_id AND fc.id = ft.class_id
      LEFT JOIN gis_catalog_item ci
        ON ci.tenant_id = a.tenant_id AND ci.id = a.catalog_item_id
      LEFT JOIN gis_material m
        ON m.tenant_id = ci.tenant_id AND m.id = ci.material_id
      LEFT JOIN LATERAL (
            SELECT cs.code AS setor_codigo, cs.name AS setor
              FROM gis_zone z
              JOIN commercial_sectors cs
                ON cs.tenant_id = z.tenant_id
               AND cs.id = z.commercial_sector_id
             WHERE z.tenant_id = a.tenant_id
               AND z.zone_type = 'COMMERCIAL_SECTOR'
               AND z.commercial_sector_id IS NOT NULL
               AND ST_Covers(z.geom, ST_Centroid(a.geom))
             LIMIT 1
           ) sec ON true
     WHERE p_date IS NOT NULL
       AND a.installed_on IS NOT NULL
       AND a.installed_on <= p_date
       AND (a.abandoned_on IS NULL OR a.abandoned_on > p_date)
       AND (NULLIF(current_setting('app.current_tenant', true), '') IS NULL
            OR a.tenant_id
               = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
     GROUP BY fc.domain, a.network_function, m.code, ci.dn_mm,
              CASE WHEN a.installed_on IS NULL THEN NULL
                   ELSE (FLOOR(EXTRACT(YEAR FROM a.installed_on) / 10) * 10)::int
              END,
              sec.setor_codigo, sec.setor
$$;

-- ======================================================================
-- (4) Relatorio de precisao (nucleo da P3.22)
-- ======================================================================

-- Uma linha por feicao; o nucleo agrupa por classe, metodo e grupo
-- somando feicoes. Nenhuma coluna pessoal: precisao e do ativo.
CREATE OR REPLACE VIEW rpt_gis_precisao_cadastro
    WITH (security_invoker = true) AS
SELECT tenant_id,
       class_code AS classe,
       capture_method AS metodo_captura,
       precision_group AS grupo_precisao,
       domain AS dominio,
       1 AS feicoes
  FROM gis_thematic_precision_feature;

-- ======================================================================
-- (5) Sementes de fabrica dos cenarios do aceite
-- ======================================================================

INSERT INTO gis_thematic_map
    (tenant_id, key, name, description, source_kind, category_field,
     measure_field, personal, aggregation, classes_json, filter_json,
     origin, active)
VALUES
    ('${tenant_id}', 'consumo-vs-media',
     'Consumo abaixo e acima da média',
     'SG-873: unidades pelo consumo faturado vigente contra a média do próprio registro (P2.5).',
     'UNIT_CONSUMPTION', 'comparison', NULL, TRUE, 'SECTOR',
     '[{"key":"BELOW_AVERAGE","label":"Abaixo da média","colorToken":"map-thematic-1"},{"key":"ABOVE_AVERAGE","label":"Acima da média","colorToken":"map-thematic-2"},{"key":"EQUAL_AVERAGE","label":"Na média","colorToken":"map-thematic-3"},{"key":"NO_AVERAGE","label":"Sem média de referência","colorToken":"map-thematic-4"}]'::jsonb,
     '{}'::jsonb, 'FACTORY', TRUE),
    ('${tenant_id}', 'consumo-faturado-media',
     'Consumo faturado pela média',
     'SG-874: unidades cujo consumo vigente foi faturado pela média do hidrômetro (consumption_type = METER_AVERAGE).',
     'UNIT_CONSUMPTION', 'billed_by_average', NULL, TRUE, 'SECTOR',
     '[{"key":"true","label":"Faturado pela média","colorToken":"map-thematic-1"}]'::jsonb,
     '{"billed_by_average":true}'::jsonb, 'FACTORY', TRUE),
    ('${tenant_id}', 'consumo-lido-zero',
     'Consumo lido zero',
     'SG-875: unidades com consumo faturado vigente igual a zero.',
     'UNIT_CONSUMPTION', 'zero_consumption', NULL, TRUE, 'SECTOR',
     '[{"key":"true","label":"Consumo zero","colorToken":"map-thematic-1"}]'::jsonb,
     '{"zero_consumption":true}'::jsonb, 'FACTORY', TRUE),
    ('${tenant_id}', 'ligacoes-cortadas',
     'Ligações cortadas, suprimidas ou desligadas',
     'SG-876: ligações vigentes pelo papel do sistema da situação (connection_statuses.system_role, V3_14).',
     'CONNECTION_STATUS', 'system_role', NULL, TRUE, 'SECTOR',
     '[{"key":"CUT","label":"Cortada","colorToken":"map-thematic-1"},{"key":"SUSPENDED","label":"Suprimida","colorToken":"map-thematic-2"},{"key":"DISCONNECTED","label":"Desligada","colorToken":"map-thematic-3"}]'::jsonb,
     '{"system_role":["CUT","SUSPENDED","DISCONNECTED"]}'::jsonb,
     'FACTORY', TRUE),
    ('${tenant_id}', 'os-no-mapa',
     'Ordens de serviço no mapa',
     'SG-491/TR-534/TR-1061: OS solicitadas e programadas com posição resolvida, com filtros por tipo, equipe e endereço.',
     'SERVICE_ORDER', 'service_type_code', NULL, TRUE, 'SECTOR',
     '[]'::jsonb,
     '{"statuses":["OPEN","SCHEDULED"]}'::jsonb, 'FACTORY', TRUE),
    ('${tenant_id}', 'os-vazamento-cavalete',
     'OS abertas de vazamento no cavalete',
     'SG-877: exige a configuração dos códigos de tipo de OS do tenant (service_types é catálogo do tenant, sem semente de fábrica).',
     'SERVICE_ORDER', 'service_type_code', NULL, TRUE, 'SECTOR',
     '[]'::jsonb,
     '{"statuses":["OPEN","SCHEDULED","IN_EXECUTION","WAITING_DEPENDENCY"],"serviceTypeCodes":[]}'::jsonb,
     'FACTORY', TRUE),
    ('${tenant_id}', 'os-vazamento-rede',
     'OS abertas de vazamento na rede',
     'SG-878: exige a configuração dos códigos de tipo de OS do tenant (service_types é catálogo do tenant, sem semente de fábrica).',
     'SERVICE_ORDER', 'service_type_code', NULL, TRUE, 'SECTOR',
     '[]'::jsonb,
     '{"statuses":["OPEN","SCHEDULED","IN_EXECUTION","WAITING_DEPENDENCY"],"serviceTypeCodes":[]}'::jsonb,
     'FACTORY', TRUE),
    ('${tenant_id}', 'os-falta-agua-qualidade',
     'OS abertas de falta d''água e qualidade',
     'SG-879: exige a configuração dos códigos de tipo de OS do tenant (service_types é catálogo do tenant, sem semente de fábrica).',
     'SERVICE_ORDER', 'service_type_code', NULL, TRUE, 'SECTOR',
     '[]'::jsonb,
     '{"statuses":["OPEN","SCHEDULED","IN_EXECUTION","WAITING_DEPENDENCY"],"serviceTypeCodes":[]}'::jsonb,
     'FACTORY', TRUE),
    ('${tenant_id}', 'hidrometros-idade-tipo',
     'Hidrômetros por tempo de instalação e tipo',
     'SG-880: instalações vigentes de água; categoria pelo tipo do hidrômetro e idade calculada da instalação.',
     'METER_AGE', 'meter_type', NULL, TRUE, 'SECTOR',
     '[]'::jsonb, '{}'::jsonb, 'FACTORY', TRUE),
    ('${tenant_id}', 'faturas-vencidas-60',
     'Faturas vencidas há mais de 60 dias',
     'SG-881: unidades com conta viva vencida há mais de 60 dias.',
     'UNIT_OVERDUE', 'overdue_group', NULL, TRUE, 'SECTOR',
     '[{"key":"OVERDUE_60","label":"Vencida há mais de 60 dias","colorToken":"map-thematic-1"}]'::jsonb,
     '{}'::jsonb, 'FACTORY', TRUE),
    ('${tenant_id}', 'grandes-consumidores',
     'Grandes consumidores',
     'SG-882: sem classes de fábrica — nenhum documento do plano fixa o corte de grande consumidor; o tenant define as faixas numéricas de billed_consumption antes do primeiro uso.',
     'UNIT_CONSUMPTION', NULL, 'billed_consumption', TRUE, 'SECTOR',
     '[]'::jsonb, '{}'::jsonb, 'FACTORY', TRUE),
    ('${tenant_id}', 'rede-agua-operacao',
     'Rede de água em operação ou implantação',
     'TR-1062: arcos do domínio WATER em operação ou em implantação.',
     'NETWORK_ARC', 'lifecycle_status', NULL, FALSE, 'NONE',
     '[{"key":"IN_SERVICE","label":"Em operação","colorToken":"map-thematic-1"},{"key":"UNDER_CONSTRUCTION","label":"Em implantação","colorToken":"map-thematic-2"}]'::jsonb,
     '{"domain":"WATER","lifecycle_status":["IN_SERVICE","UNDER_CONSTRUCTION"]}'::jsonb,
     'FACTORY', TRUE),
    ('${tenant_id}', 'rede-esgoto-operacao',
     'Rede de esgoto ativa ou em implantação',
     'TR-1063: arcos do domínio SEWER em operação ou em implantação.',
     'NETWORK_ARC', 'lifecycle_status', NULL, FALSE, 'NONE',
     '[{"key":"IN_SERVICE","label":"Em operação","colorToken":"map-thematic-1"},{"key":"UNDER_CONSTRUCTION","label":"Em implantação","colorToken":"map-thematic-2"}]'::jsonb,
     '{"domain":"SEWER","lifecycle_status":["IN_SERVICE","UNDER_CONSTRUCTION"]}'::jsonb,
     'FACTORY', TRUE),
    ('${tenant_id}', 'precisao-cadastro',
     'Precisão do cadastro',
     'GIS-08 (§3.8): feições separando levantado na meta, levantado fora da meta e aproximado.',
     'PRECISION', 'precision_group', NULL, FALSE, 'NONE',
     '[{"key":"SURVEYED_WITHIN_TARGET","label":"Levantado na meta","colorToken":"map-thematic-1"},{"key":"SURVEYED_OUT_OF_TARGET","label":"Levantado fora da meta","colorToken":"map-thematic-2"},{"key":"APPROXIMATE","label":"Aproximado","colorToken":"map-thematic-3"},{"key":"UNKNOWN","label":"Sem classificação","colorToken":"map-thematic-4"}]'::jsonb,
     '{}'::jsonb, 'FACTORY', TRUE)
ON CONFLICT (tenant_id, key) DO NOTHING;
