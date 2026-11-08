-- ============================================================================
-- ED-64 (PENDENTE DE NUMERACAO): drenagem no GIS — GIS-13
-- (GIS-CADASTRO-TECNICO §4.4, §5.3, §5.4, §10.2 e linha GIS-13 do §13.2;
-- ADR-0034 decisao 1; NR 12/ANA — Res. ANA 245/2025, arts. 11 e 12;
-- TR-1048; OPE-32).
--
-- POR QUE PENDENTE: na main (HEAD 2f7758214d3f3772c4edd40ec005161a6a77fec5,
-- conferido via MCP em 08/11/2026) a serie tenant do GIS vai ate a
-- V13_12 (GIS-06/P3.25) e NENHUM numero seguinte esta confirmado livre
-- — as trilhas ED-53/54/63 disputam os mesmos slots e a P3.27 cita uma
-- V13_13 de tematicos ainda nao integrada. Conforme a regra da tarefa,
-- NENHUM numero foi inventado: este SQL entra como db/pendente/ e
-- recebe o numero da sequencia global na integracao (precedentes:
-- ED-22, ED-25, ED-55, ED-65, ED-67).
--
-- Depende de: V13_1 (gis_domain STORM, gis_feature_class/type,
-- gis_material, gis_catalog_item, gis_value_list,
-- gis_connectivity_rule), V13_2 (gis_node, gis_arc, gis_structure,
-- gis_zone com zone_type STORM_BASIN/STORM_SUBCATCHMENT,
-- gis_attachment, gis_containment) e dos parametros do core
-- drain.* (arquivo pendente irmao no ziipsan-core).
--
-- O QUE ESTA MIGRATION FAZ:
--   (1) catalogo: classes STORM_PIPE/STORM_NODE/
--       STORM_STRUCTURE/STORM_ZONE e 20 tipos com factory_code
--       gis13:<code> — 19 da tabela do §4.4 + STORM_BASIN, cuja
--       fonte real e a lista de zone_type da V13_2 (§3.6.5 do
--       GIS-CADASTRO-TECNICO): DECISAO registrada (correcao D2 da
--       revisao da ED-64), nao linha do §4.4. attributes_schema
--       espelho das regras Java
--       (GisDrainRules) e style_key do simbolo (ADR-0036 — a ARTE e
--       da P1.30; aqui so a chave, marcador declarado no relatorio);
--   (2) material novo do §4.4 (aco corrugado) e itens de fabrica das
--       galerias (series DN de referencia a validar, como na P1.27:
--       todo item nasce REFERENCE_TO_VALIDATE);
--   (3) listas de valores do §4.4;
--   (4) regra de conectividade da boca de lobo (§5.3);
--   (5) gis_drain_inspection: inspecao com conservacao, obstrucao,
--       foto e referencia de video da ED-56 (OPE-12, reaproveitavel);
--   (6) plano de limpeza por classe (gis_drain_cleaning_plan) e os
--       pedidos recorrentes (gis_drain_cleaning_request) — a OS e do
--       nucleo (ED-36), service_order_id nasce nulo;
--   (7) monitoramento pluviometrico: gis_drain_rain_station e
--       gis_drain_rain_reading (fonte citada em cada leitura; SEM
--       integracao por API do Cemaden, nao confirmada);
--   (8) gis_drain_flood_occurrence: alagamento ligado a chuva do
--       periodo, com o marcador de causa da ED-92;
--   (9) views do tematico de conservacao e dos relatorios GIS-003
--       (cadastro: extensao por material e estado) e GIS-004
--       (indicadores operacionais, OPE-32).
--
-- NENHUM DADO DE NEGOCIO E SEMEADO: feicoes, inspecoes, intervalos de
-- limpeza, estacoes e leituras de chuva sao do tenant. Em especial,
-- NENHUM intervalo de limpeza entra como padrao: a periodicidade por
-- classe e do parametro drain.cleaning_interval_by_class (NR 12/ANA,
-- arts. 11 e 12 — a norma exige o plano, nao fixa os meses neste
-- pacote). DN/secoes/classes de carga do §4.4 sao "valor de
-- referencia a validar" no proprio §4.4.
--
-- CONVENCOES (as mesmas das V13_8/V13_11 e da ED-65): mensagens de
-- erro sem diacriticos; RLS via register_tenant_table.
-- ============================================================================

SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- ============================================================================
-- (1) Catalogo — classes (dominio STORM, que nao tinha classe).
-- Fontes: §4.4 (19 tipos) + zone_type STORM_BASIN da V13_2/§3.6.5
-- (decisao D2 registrada no cabecalho e no GisDrainCatalog).
-- ============================================================================

INSERT INTO gis_feature_class (tenant_id, code, domain, topology_role, geometry_type, origin, active)
VALUES
    ('${tenant_id}', 'STORM_PIPE', 'STORM', 'ARC', 'LineStringZ', 'FACTORY', TRUE),
    ('${tenant_id}', 'STORM_NODE', 'STORM', 'NODE', 'PointZ', 'FACTORY', TRUE),
    ('${tenant_id}', 'STORM_STRUCTURE', 'STORM', 'STRUCTURE', 'MultiPolygon', 'FACTORY', TRUE),
    ('${tenant_id}', 'STORM_ZONE', 'STORM', 'ZONE', 'MultiPolygon', 'FACTORY', TRUE)
ON CONFLICT (tenant_id, code) DO NOTHING;

-- ============================================================================
-- (1b) Catalogo — tipos: 19 do §4.4 + STORM_BASIN da V13_2/§3.6.5
-- (attributes_schema = espelho do GisDrainRules; style_key =
-- chave do simbolo, ADR-0036)
-- ============================================================================

INSERT INTO gis_feature_type (tenant_id, class_id, code, label, attributes_schema,
                              style_key, origin, factory_code, active, approval_status)
SELECT '${tenant_id}', c.id, t.code, t.label, t.schema::jsonb, t.style_key,
       'FACTORY', 'gis13:' || t.code, TRUE, 'APPROVED'
FROM gis_feature_class c
JOIN (VALUES
    ('STORM_PIPE', 'STORM_PIPE', 'Galeria tubular',
     '{"type":"object","properties":{"dn_mm":{"type":"integer","exclusiveMinimum":0}}}',
     'storm-pipe'),
    ('STORM_PIPE', 'BOX_CULVERT', 'Galeria celular',
     '{"type":"object","required":["cell_count"],"properties":{"cell_count":{"type":"integer","minimum":1},"base_m":{"type":"number","exclusiveMinimum":0},"height_m":{"type":"number","exclusiveMinimum":0}}}',
     'storm-box-culvert'),
    ('STORM_PIPE', 'OPEN_CHANNEL', 'Canal',
     '{"type":"object","required":["channel_section","channel_lining"],"properties":{"channel_section":{"type":"string","enum":["TRAPEZOIDAL","RECTANGULAR","NATURAL"]},"channel_lining":{"type":"string","enum":["CONCRETE","GABION","RIPRAP","GRASS","EARTH"]},"base_m":{"type":"number","exclusiveMinimum":0},"height_m":{"type":"number","exclusiveMinimum":0}}}',
     'storm-open-channel'),
    ('STORM_PIPE', 'CULVERT', 'Bueiro',
     '{"type":"object","required":["culvert_shape"],"properties":{"culvert_shape":{"type":"string","enum":["PIPE","BOX"]},"dn_mm":{"type":"integer","exclusiveMinimum":0}}}',
     'storm-culvert'),
    ('STORM_PIPE', 'INLET_LEAD', 'Ligação boca de lobo–PV',
     '{"type":"object","properties":{"dn_mm":{"type":"integer","exclusiveMinimum":0}}}',
     'storm-inlet-lead'),
    ('STORM_PIPE', 'GUTTER', 'Sarjeta',
     '{"type":"object","properties":{}}',
     'storm-gutter'),
    ('STORM_PIPE', 'VALLEY_GUTTER', 'Sarjetão',
     '{"type":"object","properties":{}}',
     'storm-valley-gutter'),
    ('STORM_PIPE', 'DITCH', 'Valeta',
     '{"type":"object","properties":{}}',
     'storm-ditch'),
    ('STORM_NODE', 'INLET', 'Boca de lobo',
     '{"type":"object","required":["inlet_kind"],"properties":{"inlet_kind":{"type":"string","enum":["SIMPLE","DOUBLE","TRIPLE","WITH_GRATE","WITHOUT_GRATE","CURB_OPENING","LATERAL"]},"capture_capacity_lps":{"type":"number","minimum":0},"depression_mm":{"type":"number","minimum":0}}}',
     'storm-inlet'),
    ('STORM_NODE', 'GRATE_INLET', 'Grelha',
     '{"type":"object","required":["load_class"],"properties":{"load_class":{"type":"string","enum":["A15","B125","C250","D400","E600","F900"]}}}',
     'storm-grate-inlet'),
    ('STORM_NODE', 'STORM_MANHOLE', 'PV pluvial',
     '{"type":"object","properties":{"diameter_mm":{"type":"integer","exclusiveMinimum":0}}}',
     'storm-manhole'),
    ('STORM_NODE', 'STORM_JUNCTION_BOX', 'Caixa de ligação ou de passagem',
     '{"type":"object","properties":{}}',
     'storm-junction-box'),
    ('STORM_NODE', 'DROP_SHAFT', 'Poço de queda',
     '{"type":"object","required":["drop_height_m"],"properties":{"drop_height_m":{"type":"number","exclusiveMinimum":0}}}',
     'storm-drop-shaft'),
    ('STORM_NODE', 'ENERGY_DISSIPATOR', 'Dissipador de energia',
     '{"type":"object","required":["dissipator_kind"],"properties":{"dissipator_kind":{"type":"string","enum":["STEPS","STILLING_BASIN","RIPRAP"]}}}',
     'storm-energy-dissipator'),
    ('STORM_NODE', 'OUTFALL', 'Lançamento ou exutório',
     '{"type":"object","required":["receiving_body"],"properties":{"receiving_body":{"type":"string"},"has_flap_gate":{"type":"boolean"}}}',
     'storm-outfall'),
    ('STORM_STRUCTURE', 'HEADWALL', 'Ala e testa',
     '{"type":"object","properties":{"material":{"type":"string"}}}',
     'storm-headwall'),
    ('STORM_STRUCTURE', 'DETENTION_BASIN', 'Bacia de detenção',
     '{"type":"object","required":["volume_m3"],"properties":{"volume_m3":{"type":"number","exclusiveMinimum":0},"area_m2":{"type":"number","exclusiveMinimum":0}}}',
     'storm-detention-basin'),
    ('STORM_STRUCTURE', 'RETENTION_BASIN', 'Bacia de retenção',
     '{"type":"object","required":["volume_m3"],"properties":{"volume_m3":{"type":"number","exclusiveMinimum":0},"area_m2":{"type":"number","exclusiveMinimum":0}}}',
     'storm-retention-basin'),
    -- STORM_BASIN: NAO e linha do §4.4; fonte = zone_type da V13_2
    -- (§3.6.5). Decisao D2 registrada (ver cabecalho).
    ('STORM_ZONE', 'STORM_BASIN', 'Bacia de drenagem',
     '{"type":"object","properties":{}}',
     'storm-basin'),
    ('STORM_ZONE', 'STORM_SUBCATCHMENT', 'Sub-bacia',
     '{"type":"object","required":["area_ha","impervious_pct","slope_pct","width_m","infiltration_model","outlet_ref"],"properties":{"area_ha":{"type":"number","exclusiveMinimum":0},"impervious_pct":{"type":"number","minimum":0,"maximum":100},"slope_pct":{"type":"number","minimum":0},"width_m":{"type":"number","exclusiveMinimum":0},"infiltration_model":{"type":"string","enum":["HORTON","GREEN_AMPT","CURVE_NUMBER"]},"outlet_ref":{"type":"string"},"infiltration_max_rate_mm_h":{"type":"number","exclusiveMinimum":0},"infiltration_min_rate_mm_h":{"type":"number","exclusiveMinimum":0},"infiltration_decay_per_h":{"type":"number","exclusiveMinimum":0},"suction_head_mm":{"type":"number","exclusiveMinimum":0},"hydraulic_conductivity_mm_h":{"type":"number","exclusiveMinimum":0},"initial_deficit":{"type":"number","minimum":0,"maximum":1},"curve_number":{"type":"number","exclusiveMinimum":0,"maximum":100}}}',
     'storm-subcatchment')
) AS t(class_code, code, label, schema, style_key) ON t.class_code = c.code
WHERE c.tenant_id = '${tenant_id}'
ON CONFLICT (tenant_id, class_id, code) DO NOTHING;

-- ============================================================================
-- (2) Material novo do §4.4 e itens de fabrica das galerias.
-- Aco corrugado: sem n de Manning no §4.4 para ele — manning_n fica
-- NULL (nenhum numero inventado). Itens: series DN de REFERENCIA do
-- §4.4, todos REFERENCE_TO_VALIDATE (curadoria da P1.28 pendente,
-- como em toda a fabrica GIS).
-- ============================================================================

INSERT INTO gis_material (tenant_id, code, family, standard_ref, manning_n,
                          legacy_only, origin, active)
VALUES
    ('${tenant_id}', 'CORRUGATED_STEEL', 'ACO', NULL, NULL, FALSE, 'FACTORY', TRUE)
ON CONFLICT (tenant_id, code) DO NOTHING;

-- Galeria tubular: concreto (NBR 8890:2020 citada no §4.4), DN 300 a 1500.
INSERT INTO gis_catalog_item (tenant_id, feature_type_id, material_id, dn_mm, origin,
                              factory_code, active, reference_status, standard_ref)
SELECT '${tenant_id}', ft.id, m.id, d, 'FACTORY',
       'gis13:ITEM:STORM_PIPE:' || m.code || ':' || d, TRUE, 'REFERENCE_TO_VALIDATE',
       m.standard_ref
FROM gis_feature_type ft
JOIN gis_feature_class c ON c.id = ft.class_id
JOIN gis_material m ON m.tenant_id = ft.tenant_id
CROSS JOIN unnest(ARRAY[300, 400, 500, 600, 700, 800, 900, 1000, 1200, 1500]) AS d
WHERE ft.tenant_id = '${tenant_id}' AND c.code = 'STORM_PIPE' AND ft.code = 'STORM_PIPE'
  AND m.code = 'CONCRETE'
ON CONFLICT (tenant_id, factory_code) WHERE factory_code IS NOT NULL DO NOTHING;

-- Galeria tubular: PVC/PE estruturados (NBR ISO 21138 citada), DN 300 a 1000.
INSERT INTO gis_catalog_item (tenant_id, feature_type_id, material_id, dn_mm, origin,
                              factory_code, active, reference_status, standard_ref)
SELECT '${tenant_id}', ft.id, m.id, d, 'FACTORY',
       'gis13:ITEM:STORM_PIPE:' || m.code || ':' || d, TRUE, 'REFERENCE_TO_VALIDATE',
       m.standard_ref
FROM gis_feature_type ft
JOIN gis_feature_class c ON c.id = ft.class_id
JOIN gis_material m ON m.tenant_id = ft.tenant_id
CROSS JOIN unnest(ARRAY[300, 400, 500, 600, 700, 800, 900, 1000]) AS d
WHERE ft.tenant_id = '${tenant_id}' AND c.code = 'STORM_PIPE' AND ft.code = 'STORM_PIPE'
  AND m.code IN ('PVC_STRUCTURED', 'HDPE_STRUCTURED')
ON CONFLICT (tenant_id, factory_code) WHERE factory_code IS NOT NULL DO NOTHING;

-- Ligacao boca de lobo–PV: DN 300 a 400 (§4.4), concreto e PVC estruturado.
INSERT INTO gis_catalog_item (tenant_id, feature_type_id, material_id, dn_mm, origin,
                              factory_code, active, reference_status, standard_ref)
SELECT '${tenant_id}', ft.id, m.id, d, 'FACTORY',
       'gis13:ITEM:INLET_LEAD:' || m.code || ':' || d, TRUE, 'REFERENCE_TO_VALIDATE',
       m.standard_ref
FROM gis_feature_type ft
JOIN gis_feature_class c ON c.id = ft.class_id
JOIN gis_material m ON m.tenant_id = ft.tenant_id
CROSS JOIN unnest(ARRAY[300, 400]) AS d
WHERE ft.tenant_id = '${tenant_id}' AND c.code = 'STORM_PIPE' AND ft.code = 'INLET_LEAD'
  AND m.code IN ('CONCRETE', 'PVC_STRUCTURED')
ON CONFLICT (tenant_id, factory_code) WHERE factory_code IS NOT NULL DO NOTHING;

-- ============================================================================
-- (3) Listas de valores (§4.4) — widgets do QGIS e da web
-- ============================================================================

INSERT INTO gis_value_list (tenant_id, list_code, code, label, sort_order, origin, active)
VALUES
    ('${tenant_id}', 'inlet_kind', 'SIMPLE', 'Simples', 1, 'FACTORY', TRUE),
    ('${tenant_id}', 'inlet_kind', 'DOUBLE', 'Dupla', 2, 'FACTORY', TRUE),
    ('${tenant_id}', 'inlet_kind', 'TRIPLE', 'Tripla', 3, 'FACTORY', TRUE),
    ('${tenant_id}', 'inlet_kind', 'WITH_GRATE', 'Com grelha', 4, 'FACTORY', TRUE),
    ('${tenant_id}', 'inlet_kind', 'WITHOUT_GRATE', 'Sem grelha', 5, 'FACTORY', TRUE),
    ('${tenant_id}', 'inlet_kind', 'CURB_OPENING', 'Guia chapéu', 6, 'FACTORY', TRUE),
    ('${tenant_id}', 'inlet_kind', 'LATERAL', 'Lateral', 7, 'FACTORY', TRUE),
    ('${tenant_id}', 'cover_load_class', 'A15', 'A15', 1, 'FACTORY', TRUE),
    ('${tenant_id}', 'cover_load_class', 'B125', 'B125', 2, 'FACTORY', TRUE),
    ('${tenant_id}', 'cover_load_class', 'C250', 'C250', 3, 'FACTORY', TRUE),
    ('${tenant_id}', 'cover_load_class', 'D400', 'D400', 4, 'FACTORY', TRUE),
    ('${tenant_id}', 'cover_load_class', 'E600', 'E600', 5, 'FACTORY', TRUE),
    ('${tenant_id}', 'cover_load_class', 'F900', 'F900', 6, 'FACTORY', TRUE),
    ('${tenant_id}', 'channel_section', 'TRAPEZOIDAL', 'Trapezoidal', 1, 'FACTORY', TRUE),
    ('${tenant_id}', 'channel_section', 'RECTANGULAR', 'Retangular', 2, 'FACTORY', TRUE),
    ('${tenant_id}', 'channel_section', 'NATURAL', 'Natural', 3, 'FACTORY', TRUE),
    ('${tenant_id}', 'channel_lining', 'CONCRETE', 'Concreto', 1, 'FACTORY', TRUE),
    ('${tenant_id}', 'channel_lining', 'GABION', 'Gabião', 2, 'FACTORY', TRUE),
    ('${tenant_id}', 'channel_lining', 'RIPRAP', 'Enrocamento', 3, 'FACTORY', TRUE),
    ('${tenant_id}', 'channel_lining', 'GRASS', 'Grama', 4, 'FACTORY', TRUE),
    ('${tenant_id}', 'channel_lining', 'EARTH', 'Terra', 5, 'FACTORY', TRUE),
    ('${tenant_id}', 'dissipator_kind', 'STEPS', 'Degraus', 1, 'FACTORY', TRUE),
    ('${tenant_id}', 'dissipator_kind', 'STILLING_BASIN', 'Bacia de dissipação', 2, 'FACTORY', TRUE),
    ('${tenant_id}', 'dissipator_kind', 'RIPRAP', 'Enrocamento', 3, 'FACTORY', TRUE),
    ('${tenant_id}', 'culvert_shape', 'PIPE', 'Tubular', 1, 'FACTORY', TRUE),
    ('${tenant_id}', 'culvert_shape', 'BOX', 'Celular', 2, 'FACTORY', TRUE),
    ('${tenant_id}', 'infiltration_model', 'HORTON', 'Horton (SWMM)', 1, 'FACTORY', TRUE),
    ('${tenant_id}', 'infiltration_model', 'GREEN_AMPT', 'Green-Ampt (SWMM)', 2, 'FACTORY', TRUE),
    ('${tenant_id}', 'infiltration_model', 'CURVE_NUMBER', 'Curve Number (SWMM)', 3, 'FACTORY', TRUE),
    ('${tenant_id}', 'rain_source', 'CEMADEN_IMPORT', 'Cemaden (importação do download mensal)', 1, 'FACTORY', TRUE),
    ('${tenant_id}', 'rain_source', 'OWN_GAUGE', 'Pluviômetro próprio', 2, 'FACTORY', TRUE),
    ('${tenant_id}', 'drain_element_class', 'INLET', 'Boca de lobo', 1, 'FACTORY', TRUE),
    ('${tenant_id}', 'drain_element_class', 'STORM_PIPE', 'Galeria tubular', 2, 'FACTORY', TRUE),
    ('${tenant_id}', 'drain_element_class', 'BOX_CULVERT', 'Galeria celular', 3, 'FACTORY', TRUE),
    ('${tenant_id}', 'drain_element_class', 'OPEN_CHANNEL', 'Canal', 4, 'FACTORY', TRUE),
    ('${tenant_id}', 'drain_element_class', 'STORM_MANHOLE', 'PV pluvial', 5, 'FACTORY', TRUE),
    ('${tenant_id}', 'drain_element_class', 'DETENTION_BASIN', 'Bacia de detenção', 6, 'FACTORY', TRUE),
    ('${tenant_id}', 'inspection_method', 'VISUAL', 'Visual', 1, 'FACTORY', TRUE),
    ('${tenant_id}', 'inspection_method', 'PHOTO_SURVEY', 'Levantamento fotográfico', 2, 'FACTORY', TRUE),
    ('${tenant_id}', 'inspection_method', 'CCTV_EXTERNAL_REF', 'Vídeo (ED-56, referência externa)', 3, 'FACTORY', TRUE)
ON CONFLICT DO NOTHING;

-- ============================================================================
-- (4) Conectividade (§5.3): boca de lobo liga a PV pluvial por
-- INLET_LEAD (erro). condition registra o arco exigido; a conferencia
-- efetiva no grafo e do GisDrainGraph/worker da GIS-03.
-- ============================================================================

INSERT INTO gis_connectivity_rule (tenant_id, from_type_id, to_type_id, relation,
                                   min_arcs, condition)
SELECT '${tenant_id}', fi.id, fm.id, 'CONNECTS', 1,
       '{"via_arc_type_code": "INLET_LEAD", "source": "GIS-CADASTRO-TECNICO §5.3"}'::jsonb
FROM gis_feature_type fi, gis_feature_type fm
WHERE fi.tenant_id = '${tenant_id}' AND fm.tenant_id = '${tenant_id}'
  AND fi.factory_code = 'gis13:INLET' AND fm.factory_code = 'gis13:STORM_MANHOLE'
  AND NOT EXISTS (
      SELECT 1 FROM gis_connectivity_rule r
       WHERE r.tenant_id = '${tenant_id}' AND r.from_type_id = fi.id
         AND r.to_type_id = fm.id AND r.relation = 'CONNECTS');

-- ============================================================================
-- (5) Inspecao de drenagem (NR 12/2025 via §4.4). feature polimorfica
-- sem FK, como gis_attachment/gis_validation_issue. video_ref e o
-- reaproveitamento da inspecao por video da ED-56 (OPE-12): guarda a
-- referencia do arquivo/ensaio dela; nada de video e gravado aqui.
-- ============================================================================

CREATE TABLE IF NOT EXISTS gis_drain_inspection (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    feature_table TEXT NOT NULL
        CHECK (feature_table IN ('gis_node', 'gis_arc', 'gis_structure', 'gis_zone')),
    feature_id UUID NOT NULL,
    inspected_on DATE NOT NULL,
    method TEXT NOT NULL CHECK (method IN ('VISUAL', 'PHOTO_SURVEY', 'CCTV_EXTERNAL_REF')),
    condition_rating TEXT CHECK (condition_rating IN
        ('GOOD', 'FAIR', 'POOR', 'CRITICAL', 'UNKNOWN')),
    obstruction_pct NUMERIC CHECK (obstruction_pct >= 0 AND obstruction_pct <= 100),
    photo_attachment_id UUID,
    video_ref TEXT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_drain_inspection_tenant_id UNIQUE (tenant_id, id),
    -- Correcao D5 da revisao: video_ref em branco ('') tambem e
    -- recusado, alinhado a regra Java (GisDrainRules.ensureInspection
    -- exige referencia nao vazia para CCTV_EXTERNAL_REF e recusa
    -- referencia em qualquer outro metodo).
    CONSTRAINT ck_gis_drain_inspection_video
        CHECK ((method = 'CCTV_EXTERNAL_REF')
               = (video_ref IS NOT NULL AND btrim(video_ref) <> '')
               AND (video_ref IS NULL OR btrim(video_ref) <> ''))
);

SELECT register_tenant_table('gis_drain_inspection');

CREATE INDEX IF NOT EXISTS idx_gis_drain_inspection_feature
    ON gis_drain_inspection (tenant_id, feature_table, feature_id, inspected_on DESC);

-- ============================================================================
-- (6) Plano de operacao e manutencao da drenagem (NR 12/ANA, Res.
-- ANA 245/2025, arts. 11 e 12): periodicidade e criterios POR CLASSE.
-- interval_months nasce NULL — a periodicidade e do parametro
-- drain.cleaning_interval_by_class do tenant; a linha so e criada
-- pela tela/parametro, nunca semeada com um numero inventado.
-- ============================================================================

CREATE TABLE IF NOT EXISTS gis_drain_cleaning_plan (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    element_class TEXT NOT NULL CHECK (element_class IN
        ('INLET', 'STORM_PIPE', 'BOX_CULVERT', 'OPEN_CHANNEL',
         'STORM_MANHOLE', 'DETENTION_BASIN')),
    interval_months INT CHECK (interval_months > 0),
    obstruction_threshold_pct NUMERIC
        CHECK (obstruction_threshold_pct >= 0 AND obstruction_threshold_pct <= 100),
    include_poor_or_critical BOOLEAN NOT NULL DEFAULT FALSE,
    plan_start DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_drain_cleaning_plan_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_drain_cleaning_plan_class UNIQUE (tenant_id, element_class)
);

SELECT register_tenant_table('gis_drain_cleaning_plan');

-- Pedido recorrente de limpeza: a OS e do nucleo (ED-36);
-- service_order_id e preenchido pela integracao ao abrir a OS.
CREATE TABLE IF NOT EXISTS gis_drain_cleaning_request (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    feature_table TEXT NOT NULL
        CHECK (feature_table IN ('gis_node', 'gis_arc', 'gis_structure')),
    feature_id UUID NOT NULL,
    element_class TEXT NOT NULL,
    due_on DATE NOT NULL,
    basis TEXT NOT NULL CHECK (basis IN ('CALENDAR', 'CRITERIA', 'CALENDAR_AND_CRITERIA')),
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ORDERED', 'DONE', 'CANCELLED')),
    service_order_id UUID,
    completed_on DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_drain_cleaning_request_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_gis_drain_cleaning_request_done
        CHECK (status <> 'DONE' OR completed_on IS NOT NULL)
);

SELECT register_tenant_table('gis_drain_cleaning_request');

CREATE INDEX IF NOT EXISTS idx_gis_drain_cleaning_request_due
    ON gis_drain_cleaning_request (tenant_id, status, due_on);
CREATE INDEX IF NOT EXISTS idx_gis_drain_cleaning_request_feature
    ON gis_drain_cleaning_request (tenant_id, feature_id);

-- ============================================================================
-- (7) Monitoramento pluviometrico (drain.rain_source). A fonte e
-- citada em CADA leitura (source_ref do lote importado): chuva sem
-- fonte nao entra. Nenhuma integracao por API do Cemaden: a entrada
-- e o download mensal importado ou o pluviometro proprio.
-- ============================================================================

CREATE TABLE IF NOT EXISTS gis_drain_rain_station (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code TEXT NOT NULL,
    source_kind TEXT NOT NULL CHECK (source_kind IN ('CEMADEN_IMPORT', 'OWN_GAUGE')),
    name TEXT,
    source_ref TEXT,
    geom geometry(Point, 4674),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_drain_rain_station_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_drain_rain_station_code UNIQUE (tenant_id, code)
);

SELECT register_tenant_table('gis_drain_rain_station');

CREATE TABLE IF NOT EXISTS gis_drain_rain_reading (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    station_id UUID NOT NULL,
    read_at TIMESTAMPTZ NOT NULL,
    rain_mm NUMERIC NOT NULL CHECK (rain_mm >= 0),
    source_ref TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_drain_rain_reading_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_drain_rain_reading_station_instant
        UNIQUE (tenant_id, station_id, read_at),
    CONSTRAINT fk_gis_drain_rain_reading_station
        FOREIGN KEY (tenant_id, station_id)
        REFERENCES gis_drain_rain_station (tenant_id, id)
);

SELECT register_tenant_table('gis_drain_rain_reading');

CREATE INDEX IF NOT EXISTS idx_gis_drain_rain_reading_period
    ON gis_drain_rain_reading (tenant_id, read_at);

-- ============================================================================
-- (8) Ocorrencia de alagamento ligada a chuva do periodo.
-- network_cause_code e o marcador da causa no catalogo de causas da
-- ocorrencia de rede da ED-92 ('ALAGAMENTO' — marcador proposto por
-- esta entrega, a reconciliar com o catalogo da ED-92 quando ele for
-- carregado); network_occurrence_id liga os dois registros na
-- integracao e nasce nulo. rain_mm_in_period NULL = sem leitura no
-- periodo (desconhecido, nunca zero inventado).
-- ============================================================================

CREATE TABLE IF NOT EXISTS gis_drain_flood_occurrence (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    feature_table TEXT CHECK (feature_table IN
        ('gis_node', 'gis_arc', 'gis_structure', 'gis_zone')),
    feature_id UUID,
    subcatchment_zone_id UUID,
    rain_mm_in_period NUMERIC,
    network_cause_code TEXT NOT NULL DEFAULT 'ALAGAMENTO',
    network_occurrence_id UUID,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_drain_flood_occurrence_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_gis_drain_flood_period CHECK (period_end >= period_start),
    CONSTRAINT ck_gis_drain_flood_feature
        CHECK ((feature_table IS NULL) = (feature_id IS NULL))
);

SELECT register_tenant_table('gis_drain_flood_occurrence');

CREATE INDEX IF NOT EXISTS idx_gis_drain_flood_period
    ON gis_drain_flood_occurrence (tenant_id, period_start);

-- ============================================================================
-- (9) Views: tematico de conservacao e relatorios GIS-003/GIS-004.
-- ============================================================================

-- Tematico de conservacao (GIS-08/GIS-13): uma linha por feicao de
-- drenagem (arcos, nos e estruturas de tipos gis13:*) com o estado.
-- security_invoker como nas demais views de leitura do GIS.
CREATE OR REPLACE VIEW gis_thematic_drain_condition
    WITH (security_invoker = true) AS
SELECT a.id AS feature_id, 'gis_arc' AS feature_table, a.tenant_id,
       ft.code AS type_code, COALESCE(a.condition_rating, 'UNKNOWN') AS condition_rating,
       a.geom
  FROM gis_arc a
  JOIN gis_feature_type ft ON ft.id = a.feature_type_id AND ft.tenant_id = a.tenant_id
 WHERE ft.factory_code LIKE 'gis13:%'
UNION ALL
SELECT n.id, 'gis_node', n.tenant_id, ft.code,
       COALESCE(n.condition_rating, 'UNKNOWN'), n.geom
  FROM gis_node n
  JOIN gis_feature_type ft ON ft.id = n.feature_type_id AND ft.tenant_id = n.tenant_id
 WHERE ft.factory_code LIKE 'gis13:%'
UNION ALL
SELECT s.id, 'gis_structure', s.tenant_id, ft.code,
       COALESCE(s.condition_rating, 'UNKNOWN'),
       COALESCE(s.geom, ST_Multi(s.geom_point::geometry))
  FROM gis_structure s
  JOIN gis_feature_type ft ON ft.id = s.feature_type_id AND ft.tenant_id = s.tenant_id
 WHERE ft.factory_code LIKE 'gis13:%';

-- GIS-003: relatorio do cadastro de drenagem — extensao por material
-- e estado de conservacao (linha GIS-13 do §13.2; relatorio para a
-- ERI da NR 12/2025, §11 do GIS-CADASTRO-TECNICO).
CREATE OR REPLACE VIEW rpt_gis_drenagem_cadastro
    WITH (security_invoker = true) AS
SELECT COALESCE(m.code, 'NAO_INFORMADO') AS material,
       COALESCE(a.condition_rating, 'UNKNOWN') AS estado_conservacao,
       ft.code AS tipo,
       SUM(a.length_m) AS extensao_m,
       COUNT(*) AS arcos,
       AVG(NULLIF(a.attributes ->> 'obstruction_pct', '')::numeric) AS obstrucao_media_pct
  FROM gis_arc a
  JOIN gis_feature_type ft ON ft.id = a.feature_type_id AND ft.tenant_id = a.tenant_id
  LEFT JOIN gis_catalog_item ci ON ci.id = a.catalog_item_id AND ci.tenant_id = a.tenant_id
  LEFT JOIN gis_material m ON m.id = ci.material_id AND m.tenant_id = a.tenant_id
 WHERE ft.factory_code LIKE 'gis13:%'
   AND a.lifecycle_status = 'IN_SERVICE'
 GROUP BY COALESCE(m.code, 'NAO_INFORMADO'),
          COALESCE(a.condition_rating, 'UNKNOWN'), ft.code;

-- GIS-004: indicadores operacionais da drenagem (OPE-32), por mes:
-- ocorrencias de alagamento e chuva media dos seus periodos,
-- inspecoes, limpezas devidas/concluidas no mes do vencimento.
CREATE OR REPLACE VIEW rpt_gis_drenagem_indicadores
    WITH (security_invoker = true) AS
WITH months AS (
    SELECT DISTINCT date_trunc('month', period_start) AS month_start, tenant_id
      FROM gis_drain_flood_occurrence
    UNION
    SELECT DISTINCT date_trunc('month', inspected_on::timestamptz), tenant_id
      FROM gis_drain_inspection
    UNION
    SELECT DISTINCT date_trunc('month', due_on::timestamptz), tenant_id
      FROM gis_drain_cleaning_request
)
SELECT to_char(mo.month_start, 'YYYY-MM') AS mes,
       (SELECT COUNT(*) FROM gis_drain_flood_occurrence f
         WHERE f.tenant_id = mo.tenant_id
           AND date_trunc('month', f.period_start) = mo.month_start)
           AS ocorrencias_alagamento,
       (SELECT AVG(f.rain_mm_in_period) FROM gis_drain_flood_occurrence f
         WHERE f.tenant_id = mo.tenant_id
           AND date_trunc('month', f.period_start) = mo.month_start)
           AS chuva_media_periodo_alagamento_mm,
       (SELECT COUNT(*) FROM gis_drain_inspection i
         WHERE i.tenant_id = mo.tenant_id
           AND date_trunc('month', i.inspected_on::timestamptz) = mo.month_start)
           AS inspecoes,
       (SELECT COUNT(*) FROM gis_drain_cleaning_request r
         WHERE r.tenant_id = mo.tenant_id
           AND date_trunc('month', r.due_on::timestamptz) = mo.month_start)
           AS limpezas_devidas,
       (SELECT COUNT(*) FROM gis_drain_cleaning_request r
         WHERE r.tenant_id = mo.tenant_id
           AND date_trunc('month', r.due_on::timestamptz) = mo.month_start
           AND r.status = 'DONE')
           AS limpezas_concluidas,
       (SELECT 100.0 * COUNT(*) FILTER (WHERE t.condition_rating IN ('POOR', 'CRITICAL'))
               / NULLIF(COUNT(*) FILTER (
                   WHERE t.condition_rating IS NOT NULL
                     AND t.condition_rating <> 'UNKNOWN'), 0)
          FROM gis_thematic_drain_condition t
         WHERE t.tenant_id = mo.tenant_id)
           AS pct_pobre_ou_critico,
       (SELECT AVG(i.obstruction_pct) FROM gis_drain_inspection i
         WHERE i.tenant_id = mo.tenant_id
           AND date_trunc('month', i.inspected_on::timestamptz) = mo.month_start)
           AS obstrucao_media_pct
  FROM months mo;
