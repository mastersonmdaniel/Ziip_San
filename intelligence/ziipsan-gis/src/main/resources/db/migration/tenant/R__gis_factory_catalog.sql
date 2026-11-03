-- GIS-02 (P1.27) — catalogo de fabrica de agua e esgoto (GIS-CADASTRO-TECNICO §4.2, §4.3 e §4.6).
--
-- Migration REPETIVEL: roda depois de todas as versionadas a cada provisionamento e a cada
-- reprovisionamento. Upsert pela chave estavel factory_code ('gis02:<...>'); a reaplicacao
-- NUNCA altera o active definido pelo tenant nem toca linha de tenant (ON CONFLICT DO NOTHING
-- em tudo, e os UPDATEs do fim so mexem em factory_retired_on e no attributes_schema de
-- tipos de fabrica). Item retirado da fabrica recebe factory_retired_on e continua valido
-- nas feicoes existentes (nunca e apagado). Item desativado pelo tenant some da criacao
-- (a API so oferece APPROVED + active) e continua referenciado pelas feicoes existentes.
--
-- NENHUM item e STANDARD nesta frente — e esta e a ressalva central do relatorio:
-- a compra das 33 normas da P1.28 esta PENDENTE de aprovacao do dono (lista fechada em
-- docs/arquitetura/P1-28-LISTA-FECHADA-NORMAS-ABNT.md), logo nada foi conferido no texto
-- de norma alguma. Todo item de fabrica nasce REFERENCE_TO_VALIDATE. O standard_ref abaixo
-- e CITACAO BIBLIOGRAFICA (numero e edicao como citados no §4 do projeto), nao verificacao:
-- quando a compra for aprovada e feita, a curadoria passa os itens conferidos para STANDARD
-- com norma e edicao — nunca antes. Valores numericos (DN, C de Hazen-Williams, n de
-- Manning, angulos, secoes) sao, todos, valores de referencia a validar, como o proprio §4
-- ja marca para a maioria deles.
--
-- Convenções:
-- - PE: o §4 da DE comerciais, nao DN; o item guarda a DE em dn_mm (nao ha coluna de DE
--   no item, e a V13_2 ja mapeava diameter_mm para dn_mm), documentado no relatorio.
-- - Classes compartilhadas agua/esgoto seguem o precedente da V13_2 (§2.4): PIPE, VALVE,
--   FITTING e TREATMENT_PLANT sao de dominio WATER e abrigam tambem os tipos de esgoto
--   (FORCE_MAIN, WWTP); a separacao por dominio, se a curadoria quiser, e dado novo.
-- - Tipos sem item de fabrica (sem serie DN/secao no §4): o tipo existe e o tenant cria
--   os itens; o relatorio lista quais sao.
-- - Classes novas desta frente: DAM (barragem), SURGE_PROTECTION (chamine de equilibrio,
--   TAU, reservatorio hidropneumatico) e BASIN (bacias de esgotamento, ZONE).
-- - Elevatorias: o attributes_schema guarda suction_pressurized (NBR 12208:2020: pressao a
--   montante superior a atmosferica => a elevatoria passa a se chamar booster). BOOSTER e
--   MOBILE_BOOSTER exigem suction_pressurized = true (enum); WATER_PUMP_STATION e
--   SEWAGE_LIFT_STATION (semeados na V13_2 como 'gis01:') ganham a propriedade no UPDATE
--   do fim, sem tocar no active deles.

SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- ======================================================================
-- Classes novas da P1.27 (as demais ja existem da V13_2)
-- ======================================================================

INSERT INTO gis_feature_class (tenant_id, code, domain, topology_role, geometry_type, origin, active)
VALUES
    ('${tenant_id}', 'DAM', 'WATER', 'STRUCTURE', 'MultiPolygon', 'FACTORY', TRUE),
    ('${tenant_id}', 'SURGE_PROTECTION', 'WATER', 'STRUCTURE', 'MultiPolygon', 'FACTORY', TRUE),
    ('${tenant_id}', 'BASIN', 'SEWER', 'ZONE', 'MultiPolygon', 'FACTORY', TRUE)
ON CONFLICT (tenant_id, code) DO NOTHING;

-- ======================================================================
-- Materiais (§4.2.1 agua, §4.3.1 esgoto)
-- Sem factory_code na tabela: upsert pelo code. standard_ref e citacao (§4),
-- nao verificacao; C e n sao valores de referencia a validar.
-- ======================================================================

INSERT INTO gis_material (tenant_id, code, family, standard_ref, hazen_williams_c, manning_n,
                          legacy_only, origin, active)
VALUES
    ('${tenant_id}', 'PVC_PBA', 'PVC', 'NBR 5647-1:2025', 140, NULL, FALSE, 'FACTORY', TRUE),
    ('${tenant_id}', 'PVC_DEFOFO', 'PVC', 'NBR 7665:2025', 140, NULL, FALSE, 'FACTORY', TRUE),
    ('${tenant_id}', 'PVC_O', 'PVC', 'NBR 15750:2024', 145, NULL, FALSE, 'FACTORY', TRUE),
    ('${tenant_id}', 'PE80', 'PE', 'NBR 15561:2024', 150, NULL, FALSE, 'FACTORY', TRUE),
    ('${tenant_id}', 'PE100', 'PE', 'NBR 15561:2024', 150, NULL, FALSE, 'FACTORY', TRUE),
    ('${tenant_id}', 'DUCTILE_IRON', 'FERRO_FUNDIDO', 'NBR 7675:2026', 130, NULL, FALSE, 'FACTORY', TRUE),
    ('${tenant_id}', 'STEEL', 'ACO', NULL, 120, NULL, FALSE, 'FACTORY', TRUE),
    ('${tenant_id}', 'GRP', 'PRFV', NULL, 145, NULL, FALSE, 'FACTORY', TRUE),
    ('${tenant_id}', 'ASBESTOS_CEMENT', 'FIBROCIMENTO', NULL, 120, 0.012, TRUE, 'FACTORY', TRUE),
    ('${tenant_id}', 'CAST_IRON', 'FERRO_FUNDIDO', NULL, 100, NULL, TRUE, 'FACTORY', TRUE),
    ('${tenant_id}', 'GALVANIZED_STEEL', 'ACO', NULL, 120, NULL, TRUE, 'FACTORY', TRUE),
    ('${tenant_id}', 'PVC_SOLID_WALL', 'PVC', 'NBR 7362:2025', NULL, 0.010, FALSE, 'FACTORY', TRUE),
    ('${tenant_id}', 'PVC_STRUCTURED', 'PVC', 'NBR ISO 21138-1:2021', NULL, 0.010, FALSE, 'FACTORY', TRUE),
    ('${tenant_id}', 'HDPE_STRUCTURED', 'PE', 'NBR ISO 21138-3:2021', NULL, 0.010, FALSE, 'FACTORY', TRUE),
    ('${tenant_id}', 'CONCRETE', 'CONCRETO', 'NBR 8890:2020', NULL, 0.013, FALSE, 'FACTORY', TRUE),
    ('${tenant_id}', 'VITRIFIED_CLAY', 'CERAMICA', NULL, NULL, 0.013, FALSE, 'FACTORY', TRUE)
ON CONFLICT (tenant_id, code) DO NOTHING;

-- ======================================================================
-- Tipos de fabrica (§4.2.2 a §4.2.6, §4.3.2) + os 14 acrescidos do manual da
-- ARSESP e do glossario do SINISA que faltavam no §4 (marcados com [*]):
-- UTS, poco amazonas, captacao flutuante, barragem, lododuto, travessia aerea,
-- canal, galeria, tunel, chamine de equilibrio, tanque de alimentacao
-- unidirecional, reservatorio hidropneumatico, estacao de pre-condicionamento
-- e booster movel. BOOSTER fixo vem junto (§4.2.5 ja o lista, sem seed na V13_2).
-- Upsert por (tenant_id, class_id, code): tipo ja semeado pela V13_2 (mesmo code
-- e classe) nao e duplicado nem alterado.
-- ======================================================================

INSERT INTO gis_feature_type (tenant_id, class_id, code, label, attributes_schema,
                              origin, factory_code, active, approval_status)
SELECT '${tenant_id}', c.id, t.code, t.label, t.schema::jsonb, 'FACTORY',
       'gis02:' || t.code, TRUE, 'APPROVED'
FROM gis_feature_class c
JOIN (VALUES
    -- Conexoes (§4.2.2)
    ('FITTING', 'BEND', 'Curva', '{}'),
    ('FITTING', 'DISMANTLING_JOINT', 'Junta de desmontagem', '{}'),
    ('FITTING', 'PLUG', 'Tampão', '{}'),
    ('FITTING', 'BLIND_FLANGE', 'Flange cego', '{}'),
    ('FITTING', 'WYE', 'Junção (Y)', '{}'),
    ('FITTING', 'SERVICE_SADDLE', 'Colar de tomada', '{}'),
    ('FITTING', 'SPECIAL_PIECE', 'Peça especial', '{}'),
    -- Registros e valvulas (§4.2.3)
    ('VALVE', 'BUTTERFLY_VALVE', 'Válvula borboleta', '{}'),
    ('VALVE', 'BALL_VALVE', 'Registro de esfera', '{}'),
    ('VALVE', 'PSV', 'Válvula sustentadora', '{}'),
    ('VALVE', 'PRSV', 'Válvula redutora-sustentadora', '{}'),
    ('VALVE', 'FCV', 'Válvula controladora de vazão', '{}'),
    ('VALVE', 'PBV', 'Válvula quebra-pressão', '{}'),
    ('VALVE', 'SURGE_RELIEF_VALVE', 'Válvula antigolpe / de alívio', '{}'),
    ('VALVE', 'CURB_STOP', 'Registro de passeio', '{}'),
    ('HYDRANT', 'HYDRANT_UNDERGROUND', 'Hidrante subterrâneo', '{}'),
    ('HYDRANT', 'HYDRANT_SELF_SEALING', 'Hidrante com obturação própria', '{}'),
    -- Unidades operacionais (§4.2.5) e caixas
    ('ATTACHMENT', 'VALVE_CHAMBER', 'Caixa de registro', '{}'),
    ('ATTACHMENT', 'METER_CHAMBER', 'Abrigo de macromedidor', '{}'),
    ('METER_STATION', 'METER_SETTING', 'Cavalete', '{}'),
    -- Arcos: ramal de hidrante, lododuto, travessia, canal, galeria, tunel
    ('PIPE', 'HYDRANT_LATERAL', 'Ramal de hidrante', '{}'),
    ('PIPE', 'SLUDGE_PIPELINE', 'Lododuto [*]', '{}'),
    ('PIPE', 'AERIAL_CROSSING', 'Travessia aérea [*]', '{}'),
    ('PIPE', 'CANAL', 'Canal (adução) [*]', '{}'),
    ('PIPE', 'GALLERY', 'Galeria [*]', '{}'),
    ('PIPE', 'TUNNEL', 'Túnel [*]', '{}'),
    -- Elevatorias: booster guarda a condicao de succao em pressao (NBR 12208:2020).
    -- A propriedade e obrigatoria no schema do booster (enum [true]); nas
    -- elevatorias existentes da V13_2 ela entra pelo UPDATE do fim.
    ('PUMP_STATION', 'BOOSTER', 'Booster',
     '{"type": "object", "properties": {"suction_pressurized": {"type": "boolean", "enum": [true], "description": "Sucção em pressão (NBR 12208:2020): pressão a montante superior à atmosférica; booster sempre true"}}}'),
    ('PUMP_STATION', 'MOBILE_BOOSTER', 'Booster móvel [*]',
     '{"type": "object", "properties": {"suction_pressurized": {"type": "boolean", "enum": [true], "description": "Sucção em pressão (NBR 12208:2020): pressão a montante superior à atmosférica; booster sempre true"}}}'),
    -- Tratamento e captacao: os acrescidos [*]
    ('TREATMENT_PLANT', 'UTS', 'Unidade de tratamento simplificado (UTS) [*]', '{}'),
    ('TREATMENT_PLANT', 'PRECONDITIONING_STATION', 'Estação de pré-condicionamento [*]', '{}'),
    ('SOURCE', 'AMAZON_WELL', 'Poço amazonas [*]', '{}'),
    ('SOURCE', 'FLOATING_INTAKE', 'Captação flutuante [*]', '{}'),
    ('DAM', 'DAM', 'Barragem [*]', '{}'),
    -- Protecao contra transientes (classe nova)
    ('SURGE_PROTECTION', 'SURGE_SHAFT', 'Chaminé de equilíbrio [*]', '{}'),
    ('SURGE_PROTECTION', 'ONE_WAY_FEED_TANK', 'Tanque de alimentação unidirecional (TAU) [*]', '{}'),
    ('SURGE_PROTECTION', 'HYDROPNEUMATIC_TANK', 'Reservatório hidropneumático [*]', '{}'),
    -- Esgoto: singularidades (§4.3.2)
    ('MANHOLE', 'TIL', 'Tubo de inspeção e limpeza', '{}'),
    ('MANHOLE', 'TERMINAL_CLEANOUT', 'Terminal de limpeza', '{}'),
    ('MANHOLE', 'SIPHON_CHAMBER', 'Câmara de sifão', '{}'),
    ('BOX', 'JUNCTION_CHAMBER', 'Caixa de passagem', '{}'),
    ('BOX', 'INSPECTION_BOX', 'Caixa de inspeção', '{}'),
    ('BOX', 'SEWER_CONNECTION_BOX', 'Caixa de ligação', '{}'),
    ('BOX', 'FLUSH_CHAMBER', 'Caixa de descarga', '{}'),
    ('OUTFALL', 'DISCHARGE_POINT', 'Ponto de lançamento', '{}'),
    ('BASIN', 'SEWER_BASIN', 'Bacia de esgotamento', '{}'),
    ('BASIN', 'SEWER_SUBBASIN', 'Sub-bacia de esgotamento', '{}')
) AS t(class_code, code, label, schema) ON t.class_code = c.code
WHERE c.tenant_id = '${tenant_id}'
ON CONFLICT (tenant_id, class_id, code) DO NOTHING;

-- ======================================================================
-- Itens de fabrica: tubos (§4.2.1, §4.3.1).
-- Series DN/DE como no §4; todo item REFERENCE_TO_VALIDATE (compra P1.28
-- pendente). standard_ref do item = citacao da norma do material (§4).
-- Upsert por factory_code: reaplicacao nao duplica nem altera item do tenant.
-- ======================================================================

-- Rede de agua: PVC_PBA DN 50, 75, 100 (DE 60, 85, 110 no §4; DN em dn_mm).
INSERT INTO gis_catalog_item (tenant_id, feature_type_id, material_id, dn_mm, origin,
                              factory_code, active, reference_status, standard_ref)
SELECT '${tenant_id}', ft.id, m.id, d, 'FACTORY',
       'gis02:ITEM:WATER_MAIN:' || m.code || ':' || d, TRUE, 'REFERENCE_TO_VALIDATE', m.standard_ref
FROM gis_feature_type ft
JOIN gis_feature_class c ON c.id = ft.class_id
JOIN gis_material m ON m.tenant_id = ft.tenant_id
CROSS JOIN unnest(ARRAY[50, 75, 100]) AS d
WHERE ft.tenant_id = '${tenant_id}' AND c.code = 'PIPE' AND ft.code = 'WATER_MAIN' AND m.code = 'PVC_PBA'
ON CONFLICT (tenant_id, factory_code) WHERE factory_code IS NOT NULL DO NOTHING;

-- Rede de agua: PVC DEFOFO e PVC-O, DN 100 a 500.
INSERT INTO gis_catalog_item (tenant_id, feature_type_id, material_id, dn_mm, origin,
                              factory_code, active, reference_status, standard_ref)
SELECT '${tenant_id}', ft.id, m.id, d, 'FACTORY',
       'gis02:ITEM:WATER_MAIN:' || m.code || ':' || d, TRUE, 'REFERENCE_TO_VALIDATE', m.standard_ref
FROM gis_feature_type ft
JOIN gis_feature_class c ON c.id = ft.class_id
JOIN gis_material m ON m.tenant_id = ft.tenant_id
CROSS JOIN unnest(ARRAY[100, 150, 200, 250, 300, 350, 400, 450, 500]) AS d
WHERE ft.tenant_id = '${tenant_id}' AND c.code = 'PIPE' AND ft.code = 'WATER_MAIN'
  AND m.code IN ('PVC_DEFOFO', 'PVC_O')
ON CONFLICT (tenant_id, factory_code) WHERE factory_code IS NOT NULL DO NOTHING;

-- Rede de agua: PE100, DE 63 a 500 (DE guardada em dn_mm, ver cabecalho).
INSERT INTO gis_catalog_item (tenant_id, feature_type_id, material_id, dn_mm, origin,
                              factory_code, active, reference_status, standard_ref)
SELECT '${tenant_id}', ft.id, m.id, d, 'FACTORY',
       'gis02:ITEM:WATER_MAIN:' || m.code || ':' || d, TRUE, 'REFERENCE_TO_VALIDATE', m.standard_ref
FROM gis_feature_type ft
JOIN gis_feature_class c ON c.id = ft.class_id
JOIN gis_material m ON m.tenant_id = ft.tenant_id
CROSS JOIN unnest(ARRAY[63, 75, 90, 110, 125, 140, 160, 180, 200, 225, 250, 280, 315, 355, 400, 450, 500]) AS d
WHERE ft.tenant_id = '${tenant_id}' AND c.code = 'PIPE' AND ft.code = 'WATER_MAIN' AND m.code = 'PE100'
ON CONFLICT (tenant_id, factory_code) WHERE factory_code IS NOT NULL DO NOTHING;

-- Rede de agua: ferro fundido ductil, DN 80 a 1200.
INSERT INTO gis_catalog_item (tenant_id, feature_type_id, material_id, dn_mm, origin,
                              factory_code, active, reference_status, standard_ref)
SELECT '${tenant_id}', ft.id, m.id, d, 'FACTORY',
       'gis02:ITEM:WATER_MAIN:' || m.code || ':' || d, TRUE, 'REFERENCE_TO_VALIDATE', m.standard_ref
FROM gis_feature_type ft
JOIN gis_feature_class c ON c.id = ft.class_id
JOIN gis_material m ON m.tenant_id = ft.tenant_id
CROSS JOIN unnest(ARRAY[80, 100, 150, 200, 250, 300, 350, 400, 450, 500, 600, 700, 800, 900, 1000, 1200]) AS d
WHERE ft.tenant_id = '${tenant_id}' AND c.code = 'PIPE' AND ft.code = 'WATER_MAIN' AND m.code = 'DUCTILE_IRON'
ON CONFLICT (tenant_id, factory_code) WHERE factory_code IS NOT NULL DO NOTHING;

-- Rede de agua: aco, DN 150 a 1500 (adutoras e travessias).
INSERT INTO gis_catalog_item (tenant_id, feature_type_id, material_id, dn_mm, origin,
                              factory_code, active, reference_status, standard_ref)
SELECT '${tenant_id}', ft.id, m.id, d, 'FACTORY',
       'gis02:ITEM:WATER_MAIN:' || m.code || ':' || d, TRUE, 'REFERENCE_TO_VALIDATE', m.standard_ref
FROM gis_feature_type ft
JOIN gis_feature_class c ON c.id = ft.class_id
JOIN gis_material m ON m.tenant_id = ft.tenant_id
CROSS JOIN unnest(ARRAY[150, 200, 250, 300, 350, 400, 450, 500, 600, 700, 800, 900, 1000, 1200, 1500]) AS d
WHERE ft.tenant_id = '${tenant_id}' AND c.code = 'PIPE' AND ft.code = 'WATER_MAIN' AND m.code = 'STEEL'
ON CONFLICT (tenant_id, factory_code) WHERE factory_code IS NOT NULL DO NOTHING;

-- Rede de agua: PRFV, DN 300 a 1500.
INSERT INTO gis_catalog_item (tenant_id, feature_type_id, material_id, dn_mm, origin,
                              factory_code, active, reference_status, standard_ref)
SELECT '${tenant_id}', ft.id, m.id, d, 'FACTORY',
       'gis02:ITEM:WATER_MAIN:' || m.code || ':' || d, TRUE, 'REFERENCE_TO_VALIDATE', m.standard_ref
FROM gis_feature_type ft
JOIN gis_feature_class c ON c.id = ft.class_id
JOIN gis_material m ON m.tenant_id = ft.tenant_id
CROSS JOIN unnest(ARRAY[300, 350, 400, 450, 500, 600, 700, 800, 900, 1000, 1200, 1500]) AS d
WHERE ft.tenant_id = '${tenant_id}' AND c.code = 'PIPE' AND ft.code = 'WATER_MAIN' AND m.code = 'GRP'
ON CONFLICT (tenant_id, factory_code) WHERE factory_code IS NOT NULL DO NOTHING;

-- Rede de agua, so legado: fibrocimento DN 50 a 500, ferro cinzento DN 50 a 600.
-- legacy_only: feicao nova com estes materiais e recusada (regra da API/GIS-03);
-- o item existe para o legado ja cadastrado.
INSERT INTO gis_catalog_item (tenant_id, feature_type_id, material_id, dn_mm, origin,
                              factory_code, active, reference_status, standard_ref)
SELECT '${tenant_id}', ft.id, m.id, d, 'FACTORY',
       'gis02:ITEM:WATER_MAIN:' || m.code || ':' || d, TRUE, 'REFERENCE_TO_VALIDATE', m.standard_ref
FROM gis_feature_type ft
JOIN gis_feature_class c ON c.id = ft.class_id
JOIN gis_material m ON m.tenant_id = ft.tenant_id
CROSS JOIN unnest(ARRAY[50, 75, 100, 150, 200, 250, 300, 350, 400, 450, 500]) AS d
WHERE ft.tenant_id = '${tenant_id}' AND c.code = 'PIPE' AND ft.code = 'WATER_MAIN'
  AND m.code = 'ASBESTOS_CEMENT'
ON CONFLICT (tenant_id, factory_code) WHERE factory_code IS NOT NULL DO NOTHING;

INSERT INTO gis_catalog_item (tenant_id, feature_type_id, material_id, dn_mm, origin,
                              factory_code, active, reference_status, standard_ref)
SELECT '${tenant_id}', ft.id, m.id, d, 'FACTORY',
       'gis02:ITEM:WATER_MAIN:' || m.code || ':' || d, TRUE, 'REFERENCE_TO_VALIDATE', m.standard_ref
FROM gis_feature_type ft
JOIN gis_feature_class c ON c.id = ft.class_id
JOIN gis_material m ON m.tenant_id = ft.tenant_id
CROSS JOIN unnest(ARRAY[50, 75, 100, 150, 200, 250, 300, 350, 400, 450, 500, 600]) AS d
WHERE ft.tenant_id = '${tenant_id}' AND c.code = 'PIPE' AND ft.code = 'WATER_MAIN'
  AND m.code = 'CAST_IRON'
ON CONFLICT (tenant_id, factory_code) WHERE factory_code IS NOT NULL DO NOTHING;

-- Esgoto por gravidade: PVC parede macica DN 100 a 400.
INSERT INTO gis_catalog_item (tenant_id, feature_type_id, material_id, dn_mm, origin,
                              factory_code, active, reference_status, standard_ref)
SELECT '${tenant_id}', ft.id, m.id, d, 'FACTORY',
       'gis02:ITEM:GRAVITY_SEWER:' || m.code || ':' || d, TRUE, 'REFERENCE_TO_VALIDATE', m.standard_ref
FROM gis_feature_type ft
JOIN gis_feature_class c ON c.id = ft.class_id
JOIN gis_material m ON m.tenant_id = ft.tenant_id
CROSS JOIN unnest(ARRAY[100, 150, 200, 250, 300, 350, 400]) AS d
WHERE ft.tenant_id = '${tenant_id}' AND c.code = 'PIPE' AND ft.code = 'GRAVITY_SEWER'
  AND m.code IN ('PVC_SOLID_WALL', 'VITRIFIED_CLAY', 'ASBESTOS_CEMENT')
ON CONFLICT (tenant_id, factory_code) WHERE factory_code IS NOT NULL DO NOTHING;

-- Esgoto por gravidade: PVC/PE de parede estruturada, DN 150 a 1000.
INSERT INTO gis_catalog_item (tenant_id, feature_type_id, material_id, dn_mm, origin,
                              factory_code, active, reference_status, standard_ref)
SELECT '${tenant_id}', ft.id, m.id, d, 'FACTORY',
       'gis02:ITEM:GRAVITY_SEWER:' || m.code || ':' || d, TRUE, 'REFERENCE_TO_VALIDATE', m.standard_ref
FROM gis_feature_type ft
JOIN gis_feature_class c ON c.id = ft.class_id
JOIN gis_material m ON m.tenant_id = ft.tenant_id
CROSS JOIN unnest(ARRAY[150, 200, 250, 300, 350, 400, 450, 500, 600, 700, 800, 900, 1000]) AS d
WHERE ft.tenant_id = '${tenant_id}' AND c.code = 'PIPE' AND ft.code = 'GRAVITY_SEWER'
  AND m.code IN ('PVC_STRUCTURED', 'HDPE_STRUCTURED')
ON CONFLICT (tenant_id, factory_code) WHERE factory_code IS NOT NULL DO NOTHING;

-- Esgoto por gravidade: concreto, DN 300 a 1500.
INSERT INTO gis_catalog_item (tenant_id, feature_type_id, material_id, dn_mm, origin,
                              factory_code, active, reference_status, standard_ref)
SELECT '${tenant_id}', ft.id, m.id, d, 'FACTORY',
       'gis02:ITEM:GRAVITY_SEWER:' || m.code || ':' || d, TRUE, 'REFERENCE_TO_VALIDATE', m.standard_ref
FROM gis_feature_type ft
JOIN gis_feature_class c ON c.id = ft.class_id
JOIN gis_material m ON m.tenant_id = ft.tenant_id
CROSS JOIN unnest(ARRAY[300, 350, 400, 450, 500, 600, 700, 800, 900, 1000, 1200, 1500]) AS d
WHERE ft.tenant_id = '${tenant_id}' AND c.code = 'PIPE' AND ft.code = 'GRAVITY_SEWER'
  AND m.code = 'CONCRETE'
ON CONFLICT (tenant_id, factory_code) WHERE factory_code IS NOT NULL DO NOTHING;

-- Recalque de esgoto: ferro fundido ductil DN 80 a 1200, PE100 DE 63 a 500.
INSERT INTO gis_catalog_item (tenant_id, feature_type_id, material_id, dn_mm, origin,
                              factory_code, active, reference_status, standard_ref)
SELECT '${tenant_id}', ft.id, m.id, d, 'FACTORY',
       'gis02:ITEM:FORCE_MAIN:' || m.code || ':' || d, TRUE, 'REFERENCE_TO_VALIDATE', m.standard_ref
FROM gis_feature_type ft
JOIN gis_feature_class c ON c.id = ft.class_id
JOIN gis_material m ON m.tenant_id = ft.tenant_id
CROSS JOIN unnest(ARRAY[80, 100, 150, 200, 250, 300, 350, 400, 450, 500, 600, 700, 800, 900, 1000, 1200]) AS d
WHERE ft.tenant_id = '${tenant_id}' AND c.code = 'PIPE' AND ft.code = 'FORCE_MAIN'
  AND m.code = 'DUCTILE_IRON'
ON CONFLICT (tenant_id, factory_code) WHERE factory_code IS NOT NULL DO NOTHING;

INSERT INTO gis_catalog_item (tenant_id, feature_type_id, material_id, dn_mm, origin,
                              factory_code, active, reference_status, standard_ref)
SELECT '${tenant_id}', ft.id, m.id, d, 'FACTORY',
       'gis02:ITEM:FORCE_MAIN:' || m.code || ':' || d, TRUE, 'REFERENCE_TO_VALIDATE', m.standard_ref
FROM gis_feature_type ft
JOIN gis_feature_class c ON c.id = ft.class_id
JOIN gis_material m ON m.tenant_id = ft.tenant_id
CROSS JOIN unnest(ARRAY[63, 75, 90, 110, 125, 140, 160, 180, 200, 225, 250, 280, 315, 355, 400, 450, 500]) AS d
WHERE ft.tenant_id = '${tenant_id}' AND c.code = 'PIPE' AND ft.code = 'FORCE_MAIN'
  AND m.code = 'PE100'
ON CONFLICT (tenant_id, factory_code) WHERE factory_code IS NOT NULL DO NOTHING;

-- ======================================================================
-- Itens de fabrica: ramais, curvas, hidrantes, ventosas, travessia, galeria.
-- ======================================================================

-- Ramal de agua: PE80 e PE100, DE 20, 25 e 32 (DE guardada em dn_mm).
INSERT INTO gis_catalog_item (tenant_id, feature_type_id, material_id, dn_mm, origin,
                              factory_code, active, reference_status, standard_ref)
SELECT '${tenant_id}', ft.id, m.id, d, 'FACTORY',
       'gis02:ITEM:WATER_SERVICE_LINE:' || m.code || ':' || d, TRUE, 'REFERENCE_TO_VALIDATE', m.standard_ref
FROM gis_feature_type ft
JOIN gis_feature_class c ON c.id = ft.class_id
JOIN gis_material m ON m.tenant_id = ft.tenant_id
CROSS JOIN unnest(ARRAY[20, 25, 32]) AS d
WHERE ft.tenant_id = '${tenant_id}' AND c.code = 'SERVICE_LINE' AND ft.code = 'WATER_SERVICE_LINE'
  AND m.code IN ('PE80', 'PE100')
ON CONFLICT (tenant_id, factory_code) WHERE factory_code IS NOT NULL DO NOTHING;

-- Ramal de esgoto: PVC parede macica, DN 100 (valor de referencia a validar).
INSERT INTO gis_catalog_item (tenant_id, feature_type_id, material_id, dn_mm, origin,
                              factory_code, active, reference_status, standard_ref)
SELECT '${tenant_id}', ft.id, m.id, d, 'FACTORY',
       'gis02:ITEM:SEWER_SERVICE_LINE:' || m.code || ':' || d, TRUE, 'REFERENCE_TO_VALIDATE', m.standard_ref
FROM gis_feature_type ft
JOIN gis_feature_class c ON c.id = ft.class_id
JOIN gis_material m ON m.tenant_id = ft.tenant_id
CROSS JOIN unnest(ARRAY[100]) AS d
WHERE ft.tenant_id = '${tenant_id}' AND c.code = 'SERVICE_LINE' AND ft.code = 'SEWER_SERVICE_LINE'
  AND m.code = 'PVC_SOLID_WALL'
ON CONFLICT (tenant_id, factory_code) WHERE factory_code IS NOT NULL DO NOTHING;

-- Curva: serie comercial 11,25 / 22,5 / 45 / 90 graus (§4.2.2), junta elastica.
INSERT INTO gis_catalog_item (tenant_id, feature_type_id, material_id, dn_mm, angle_deg, joint_type,
                              origin, factory_code, active, reference_status, standard_ref)
SELECT '${tenant_id}', ft.id, m.id, d, a, 'ELASTIC', 'FACTORY',
       'gis02:ITEM:BEND:' || m.code || ':' || d || ':' || a, TRUE, 'REFERENCE_TO_VALIDATE', m.standard_ref
FROM gis_feature_type ft
JOIN gis_feature_class c ON c.id = ft.class_id
JOIN gis_material m ON m.tenant_id = ft.tenant_id
CROSS JOIN unnest(ARRAY[100, 150, 200, 250, 300, 400, 500]) AS d
CROSS JOIN unnest(ARRAY[11.25, 22.5, 45, 90]) AS a
WHERE ft.tenant_id = '${tenant_id}' AND c.code = 'FITTING' AND ft.code = 'BEND'
  AND m.code = 'DUCTILE_IRON'
ON CONFLICT (tenant_id, factory_code) WHERE factory_code IS NOT NULL DO NOTHING;

INSERT INTO gis_catalog_item (tenant_id, feature_type_id, material_id, dn_mm, angle_deg, joint_type,
                              origin, factory_code, active, reference_status, standard_ref)
SELECT '${tenant_id}', ft.id, m.id, d, a, 'ELASTIC', 'FACTORY',
       'gis02:ITEM:BEND:' || m.code || ':' || d || ':' || a, TRUE, 'REFERENCE_TO_VALIDATE', m.standard_ref
FROM gis_feature_type ft
JOIN gis_feature_class c ON c.id = ft.class_id
JOIN gis_material m ON m.tenant_id = ft.tenant_id
CROSS JOIN unnest(ARRAY[50, 75, 100]) AS d
CROSS JOIN unnest(ARRAY[11.25, 22.5, 45, 90]) AS a
WHERE ft.tenant_id = '${tenant_id}' AND c.code = 'FITTING' AND ft.code = 'BEND'
  AND m.code = 'PVC_PBA'
ON CONFLICT (tenant_id, factory_code) WHERE factory_code IS NOT NULL DO NOTHING;

-- Hidrante de coluna: DN 75, 100, 150 (§4.2.3; citacao NBR 5667-1, nao verificacao).
INSERT INTO gis_catalog_item (tenant_id, feature_type_id, dn_mm, origin,
                              factory_code, active, reference_status, standard_ref)
SELECT '${tenant_id}', ft.id, d, 'FACTORY',
       'gis02:ITEM:HYDRANT_PILLAR::' || d, TRUE, 'REFERENCE_TO_VALIDATE',
       'NBR 5667-1:2006 (conf. 04/2024)'
FROM gis_feature_type ft
JOIN gis_feature_class c ON c.id = ft.class_id
CROSS JOIN unnest(ARRAY[75, 100, 150]) AS d
WHERE ft.tenant_id = '${tenant_id}' AND c.code = 'HYDRANT' AND ft.code = 'HYDRANT_PILLAR'
ON CONFLICT (tenant_id, factory_code) WHERE factory_code IS NOT NULL DO NOTHING;

-- Ventosa: DN 50, 80, 100 (valor de referencia a validar).
INSERT INTO gis_catalog_item (tenant_id, feature_type_id, dn_mm, origin,
                              factory_code, active, reference_status, standard_ref)
SELECT '${tenant_id}', ft.id, d, 'FACTORY',
       'gis02:ITEM:AIR_VALVE::' || d, TRUE, 'REFERENCE_TO_VALIDATE', NULL
FROM gis_feature_type ft
JOIN gis_feature_class c ON c.id = ft.class_id
CROSS JOIN unnest(ARRAY[50, 80, 100]) AS d
WHERE ft.tenant_id = '${tenant_id}' AND c.code = 'VALVE' AND ft.code = 'AIR_VALVE'
ON CONFLICT (tenant_id, factory_code) WHERE factory_code IS NOT NULL DO NOTHING;

-- Travessia aerea [*]: aco, DN 150 a 600 (o §4.2.1 da aco DN 150 a 1500 para
-- "adutoras e travessias"; a serie aqui e o subconjunto ate 600, a validar).
INSERT INTO gis_catalog_item (tenant_id, feature_type_id, material_id, dn_mm, origin,
                              factory_code, active, reference_status, standard_ref)
SELECT '${tenant_id}', ft.id, m.id, d, 'FACTORY',
       'gis02:ITEM:AERIAL_CROSSING:' || m.code || ':' || d, TRUE, 'REFERENCE_TO_VALIDATE', m.standard_ref
FROM gis_feature_type ft
JOIN gis_feature_class c ON c.id = ft.class_id
JOIN gis_material m ON m.tenant_id = ft.tenant_id
CROSS JOIN unnest(ARRAY[150, 200, 250, 300, 400, 500, 600]) AS d
WHERE ft.tenant_id = '${tenant_id}' AND c.code = 'PIPE' AND ft.code = 'AERIAL_CROSSING'
  AND m.code = 'STEEL'
ON CONFLICT (tenant_id, factory_code) WHERE factory_code IS NOT NULL DO NOTHING;

-- Galeria [*]: secoes retangulares 1,0x1,0 a 3,0x3,0 m em concreto. Faixa tomada
-- da galeria celular de drenagem do §4.4 (o §4.2/§4.3 nao da secao de galeria de
-- aducao): valor de referencia a validar, como todo item desta migration.
INSERT INTO gis_catalog_item (tenant_id, feature_type_id, material_id, section_shape, geom1, geom2,
                              origin, factory_code, active, reference_status, standard_ref)
SELECT '${tenant_id}', ft.id, m.id, 'RECTANGULAR', s, s, 'FACTORY',
       'gis02:ITEM:GALLERY:' || m.code || ':' || s, TRUE, 'REFERENCE_TO_VALIDATE', m.standard_ref
FROM gis_feature_type ft
JOIN gis_feature_class c ON c.id = ft.class_id
JOIN gis_material m ON m.tenant_id = ft.tenant_id
CROSS JOIN unnest(ARRAY[1.0, 1.5, 2.0, 2.5, 3.0]) AS s
WHERE ft.tenant_id = '${tenant_id}' AND c.code = 'PIPE' AND ft.code = 'GALLERY'
  AND m.code = 'CONCRETE'
ON CONFLICT (tenant_id, factory_code) WHERE factory_code IS NOT NULL DO NOTHING;

-- Itens de fabrica nascem APROVADOS (o catalogo de fabrica e aprovado pela
-- curadoria, nao por um usuario do tenant); o tenant so muda o active deles.
-- O UPDATE cobre todos os blocos acima de uma vez e so toca linha gis02 com
-- approval ainda nulo: nunca altera linha de tenant nem linha gis01.
UPDATE gis_catalog_item SET approval_status = 'APPROVED'
WHERE tenant_id = '${tenant_id}' AND origin = 'FACTORY'
  AND factory_code LIKE 'gis02:%' AND approval_status IS NULL;

-- ======================================================================
-- Listas de valores (§4.6) — so listas cujos valores o projeto cita.
-- Upsert por (tenant_id, list_code, code). Sem factory_code na tabela.
-- ======================================================================

INSERT INTO gis_value_list (tenant_id, list_code, code, label, sort_order, origin, active)
SELECT '${tenant_id}', v.list_code, v.code, v.label, v.ord, 'FACTORY', TRUE
FROM (VALUES
    ('lifecycle_status', 'PLANNED', 'Projetado', 1),
    ('lifecycle_status', 'UNDER_CONSTRUCTION', 'Em obra', 2),
    ('lifecycle_status', 'IN_SERVICE', 'Em operação', 3),
    ('lifecycle_status', 'ABANDONED', 'Abandonado', 4),
    ('retired_reason', 'ABANDONED_IN_PLACE', 'Abandonado no local', 1),
    ('retired_reason', 'REMOVED', 'Removido', 2),
    ('operational_status', 'OPEN', 'Aberto', 1),
    ('operational_status', 'CLOSED', 'Fechado', 2),
    ('operational_status', 'PARTIAL', 'Parcial', 3),
    ('operational_status', 'INOPERABLE', 'Inoperante', 4),
    ('operational_status', 'UNKNOWN', 'Desconhecido', 5),
    ('joint_type', 'ELASTIC', 'Elástica', 1),
    ('joint_type', 'WELDED', 'Soldável', 2),
    ('joint_type', 'FLANGED', 'Flangeada', 3),
    ('joint_type', 'MECHANICAL', 'Mecânica', 4),
    ('joint_type', 'RESTRAINED', 'Travada', 5),
    ('joint_type', 'ELECTROFUSION', 'Eletrofusão', 6),
    ('joint_type', 'BUTT_FUSION', 'Termofusão de topo', 7),
    ('joint_type', 'COMPRESSION', 'Compressão', 8),
    ('joint_type', 'THREADED', 'Rosca', 9),
    ('joint_type', 'MORTAR', 'Argamassa', 10),
    ('cover_load_class', 'A15', 'A15', 1),
    ('cover_load_class', 'B125', 'B125', 2),
    ('cover_load_class', 'C250', 'C250', 3),
    ('cover_load_class', 'D400', 'D400', 4),
    ('cover_load_class', 'E600', 'E600', 5),
    ('cover_load_class', 'F900', 'F900', 6),
    ('network_function', 'WATER_DISTRIBUTION', 'Rede de distribuição', 1),
    ('network_function', 'RAW_WATER_MAIN', 'Adutora de água bruta', 2),
    ('network_function', 'TREATED_WATER_MAIN', 'Adutora de água tratada', 3),
    ('network_function', 'SUBMAIN', 'Subadutora', 4),
    ('network_function', 'INTERCONNECTION', 'Interligação', 5),
    ('network_function', 'PUMPING_LINE', 'Linha de recalque', 6),
    ('network_function', 'HYDRANT_BRANCH', 'Ramal de hidrante', 7),
    ('network_function', 'SEWER_NETWORK', 'Rede coletora', 8),
    ('network_function', 'TRUNK_SEWER', 'Coletor-tronco', 9),
    ('network_function', 'INTERCEPTOR', 'Interceptor', 10),
    ('network_function', 'OUTFALL_SEWER', 'Emissário', 11),
    ('network_function', 'SEWER_PUMPING_LINE', 'Linha de recalque de esgoto', 12),
    ('network_function', 'OVERFLOW_FN', 'Extravasor', 13),
    ('network_function', 'INVERTED_SIPHON_FN', 'Sifão invertido', 14),
    ('network_function', 'SEWER_BRANCH', 'Ramal predial de esgoto', 15),
    ('network_function', 'SLUDGE_PIPELINE_FN', 'Lododuto', 16),
    ('pavement_type', 'ASPHALT', 'Asfalto', 1),
    ('pavement_type', 'CONCRETE', 'Concreto', 2),
    ('pavement_type', 'SETT_STONE', 'Paralelepípedo', 3),
    ('pavement_type', 'PAVER', 'Lajota', 4),
    ('pavement_type', 'BLOCK', 'Bloquete', 5),
    ('pavement_type', 'EARTH', 'Terra', 6),
    ('street_side', 'EVEN', 'Par', 1),
    ('street_side', 'ODD', 'Ímpar', 2),
    ('street_side', 'AXIS', 'Eixo', 3),
    ('valve_actuator', 'HEAD', 'Cabeçote', 1),
    ('valve_actuator', 'HANDWHEEL', 'Volante', 2),
    ('valve_actuator', 'GEAR', 'Redutor', 3),
    ('valve_actuator', 'ELECTRIC_ACTUATOR', 'Atuador elétrico', 4),
    ('closing_direction', 'CLOCKWISE', 'Horário', 1),
    ('closing_direction', 'COUNTERCLOCKWISE', 'Anti-horário', 2)
) AS v(list_code, code, label, ord)
ON CONFLICT (tenant_id, list_code, code) DO NOTHING;

-- ======================================================================
-- Elevatorias da V13_2 ('gis01:'): guardam a condicao de succao em pressao
-- (NBR 12208:2020) no attributes_schema, sem tocar em active nem em linha
-- de tenant. BOOSTER e MOBILE_BOOSTER (gis02, acima) ja nascem com a
-- propriedade exigindo true.
-- ======================================================================

UPDATE gis_feature_type
SET attributes_schema = COALESCE(attributes_schema, '{}'::jsonb)
    || '{"type": "object", "properties": {"suction_pressurized": {"type": "boolean", "description": "Sucção em pressão (NBR 12208:2020): pressão a montante superior à atmosférica — a elevatória passa a se chamar booster"}}}'
WHERE tenant_id = '${tenant_id}'
  AND origin = 'FACTORY'
  AND code IN ('WATER_PUMP_STATION', 'SEWAGE_LIFT_STATION');

-- ======================================================================
-- Retirada de fabrica (§4.1): codigo gis02 fora da lista vigente recebe
-- factory_retired_on e continua valido nas feicoes existentes; codigo que
-- voltou a lista tem o carimbo limpo. Nunca mexe em active, nunca toca
-- linha gis01 (migracao) nem linha de tenant.
-- A lista de itens e reconstruida das mesmas series dos INSERTs acima, numa
-- tabela temporaria da propria execucao (some no fim da sessao).
-- ======================================================================

CREATE TEMP TABLE IF NOT EXISTS gis02_factory_codes (kind TEXT, code TEXT) ON COMMIT DROP;
TRUNCATE gis02_factory_codes;

INSERT INTO gis02_factory_codes (kind, code)
SELECT 'TYPE', 'gis02:' || t.code
FROM (VALUES
    ('BEND'), ('DISMANTLING_JOINT'), ('PLUG'), ('BLIND_FLANGE'), ('WYE'),
    ('SERVICE_SADDLE'), ('SPECIAL_PIECE'), ('BUTTERFLY_VALVE'), ('BALL_VALVE'),
    ('PSV'), ('PRSV'), ('FCV'), ('PBV'), ('SURGE_RELIEF_VALVE'), ('CURB_STOP'),
    ('HYDRANT_UNDERGROUND'), ('HYDRANT_SELF_SEALING'), ('VALVE_CHAMBER'),
    ('METER_CHAMBER'), ('METER_SETTING'), ('HYDRANT_LATERAL'), ('SLUDGE_PIPELINE'),
    ('AERIAL_CROSSING'), ('CANAL'), ('GALLERY'), ('TUNNEL'), ('BOOSTER'),
    ('MOBILE_BOOSTER'), ('UTS'), ('PRECONDITIONING_STATION'), ('AMAZON_WELL'),
    ('FLOATING_INTAKE'), ('DAM'), ('SURGE_SHAFT'), ('ONE_WAY_FEED_TANK'),
    ('HYDROPNEUMATIC_TANK'), ('TIL'), ('TERMINAL_CLEANOUT'), ('SIPHON_CHAMBER'),
    ('JUNCTION_CHAMBER'), ('INSPECTION_BOX'), ('SEWER_CONNECTION_BOX'),
    ('FLUSH_CHAMBER'), ('DISCHARGE_POINT'), ('SEWER_BASIN'), ('SEWER_SUBBASIN')
) AS t(code);

INSERT INTO gis02_factory_codes (kind, code)
SELECT 'ITEM', 'gis02:ITEM:WATER_MAIN:' || m.code || ':' || d
FROM gis_material m CROSS JOIN unnest(ARRAY[50, 75, 100]) AS d
WHERE m.tenant_id = '${tenant_id}' AND m.code = 'PVC_PBA'
UNION ALL
SELECT 'ITEM', 'gis02:ITEM:WATER_MAIN:' || m.code || ':' || d
FROM gis_material m CROSS JOIN unnest(ARRAY[100, 150, 200, 250, 300, 350, 400, 450, 500]) AS d
WHERE m.tenant_id = '${tenant_id}' AND m.code IN ('PVC_DEFOFO', 'PVC_O')
UNION ALL
SELECT 'ITEM', 'gis02:ITEM:WATER_MAIN:' || m.code || ':' || d
FROM gis_material m CROSS JOIN unnest(ARRAY[63, 75, 90, 110, 125, 140, 160, 180, 200, 225, 250, 280, 315, 355, 400, 450, 500]) AS d
WHERE m.tenant_id = '${tenant_id}' AND m.code = 'PE100'
UNION ALL
SELECT 'ITEM', 'gis02:ITEM:WATER_MAIN:' || m.code || ':' || d
FROM gis_material m CROSS JOIN unnest(ARRAY[80, 100, 150, 200, 250, 300, 350, 400, 450, 500, 600, 700, 800, 900, 1000, 1200]) AS d
WHERE m.tenant_id = '${tenant_id}' AND m.code = 'DUCTILE_IRON'
UNION ALL
SELECT 'ITEM', 'gis02:ITEM:WATER_MAIN:' || m.code || ':' || d
FROM gis_material m CROSS JOIN unnest(ARRAY[150, 200, 250, 300, 350, 400, 450, 500, 600, 700, 800, 900, 1000, 1200, 1500]) AS d
WHERE m.tenant_id = '${tenant_id}' AND m.code = 'STEEL'
UNION ALL
SELECT 'ITEM', 'gis02:ITEM:WATER_MAIN:' || m.code || ':' || d
FROM gis_material m CROSS JOIN unnest(ARRAY[300, 350, 400, 450, 500, 600, 700, 800, 900, 1000, 1200, 1500]) AS d
WHERE m.tenant_id = '${tenant_id}' AND m.code = 'GRP'
UNION ALL
SELECT 'ITEM', 'gis02:ITEM:WATER_MAIN:' || m.code || ':' || d
FROM gis_material m CROSS JOIN unnest(ARRAY[50, 75, 100, 150, 200, 250, 300, 350, 400, 450, 500]) AS d
WHERE m.tenant_id = '${tenant_id}' AND m.code = 'ASBESTOS_CEMENT'
UNION ALL
SELECT 'ITEM', 'gis02:ITEM:WATER_MAIN:' || m.code || ':' || d
FROM gis_material m CROSS JOIN unnest(ARRAY[50, 75, 100, 150, 200, 250, 300, 350, 400, 450, 500, 600]) AS d
WHERE m.tenant_id = '${tenant_id}' AND m.code = 'CAST_IRON'
UNION ALL
SELECT 'ITEM', 'gis02:ITEM:GRAVITY_SEWER:' || m.code || ':' || d
FROM gis_material m CROSS JOIN unnest(ARRAY[100, 150, 200, 250, 300, 350, 400]) AS d
WHERE m.tenant_id = '${tenant_id}' AND m.code IN ('PVC_SOLID_WALL', 'VITRIFIED_CLAY', 'ASBESTOS_CEMENT')
UNION ALL
SELECT 'ITEM', 'gis02:ITEM:GRAVITY_SEWER:' || m.code || ':' || d
FROM gis_material m CROSS JOIN unnest(ARRAY[150, 200, 250, 300, 350, 400, 450, 500, 600, 700, 800, 900, 1000]) AS d
WHERE m.tenant_id = '${tenant_id}' AND m.code IN ('PVC_STRUCTURED', 'HDPE_STRUCTURED')
UNION ALL
SELECT 'ITEM', 'gis02:ITEM:GRAVITY_SEWER:' || m.code || ':' || d
FROM gis_material m CROSS JOIN unnest(ARRAY[300, 350, 400, 450, 500, 600, 700, 800, 900, 1000, 1200, 1500]) AS d
WHERE m.tenant_id = '${tenant_id}' AND m.code = 'CONCRETE'
UNION ALL
SELECT 'ITEM', 'gis02:ITEM:FORCE_MAIN:' || m.code || ':' || d
FROM gis_material m CROSS JOIN unnest(ARRAY[80, 100, 150, 200, 250, 300, 350, 400, 450, 500, 600, 700, 800, 900, 1000, 1200]) AS d
WHERE m.tenant_id = '${tenant_id}' AND m.code = 'DUCTILE_IRON'
UNION ALL
SELECT 'ITEM', 'gis02:ITEM:FORCE_MAIN:' || m.code || ':' || d
FROM gis_material m CROSS JOIN unnest(ARRAY[63, 75, 90, 110, 125, 140, 160, 180, 200, 225, 250, 280, 315, 355, 400, 450, 500]) AS d
WHERE m.tenant_id = '${tenant_id}' AND m.code = 'PE100'
UNION ALL
SELECT 'ITEM', 'gis02:ITEM:WATER_SERVICE_LINE:' || m.code || ':' || d
FROM gis_material m CROSS JOIN unnest(ARRAY[20, 25, 32]) AS d
WHERE m.tenant_id = '${tenant_id}' AND m.code IN ('PE80', 'PE100')
UNION ALL
SELECT 'ITEM', 'gis02:ITEM:SEWER_SERVICE_LINE:' || m.code || ':' || d
FROM gis_material m CROSS JOIN unnest(ARRAY[100]) AS d
WHERE m.tenant_id = '${tenant_id}' AND m.code = 'PVC_SOLID_WALL'
UNION ALL
SELECT 'ITEM', 'gis02:ITEM:BEND:' || m.code || ':' || d || ':' || a
FROM gis_material m
CROSS JOIN unnest(ARRAY[100, 150, 200, 250, 300, 400, 500]) AS d
CROSS JOIN unnest(ARRAY[11.25, 22.5, 45, 90]) AS a
WHERE m.tenant_id = '${tenant_id}' AND m.code = 'DUCTILE_IRON'
UNION ALL
SELECT 'ITEM', 'gis02:ITEM:BEND:' || m.code || ':' || d || ':' || a
FROM gis_material m
CROSS JOIN unnest(ARRAY[50, 75, 100]) AS d
CROSS JOIN unnest(ARRAY[11.25, 22.5, 45, 90]) AS a
WHERE m.tenant_id = '${tenant_id}' AND m.code = 'PVC_PBA'
UNION ALL
SELECT 'ITEM', 'gis02:ITEM:HYDRANT_PILLAR::' || d
FROM unnest(ARRAY[75, 100, 150]) AS d
UNION ALL
SELECT 'ITEM', 'gis02:ITEM:AIR_VALVE::' || d
FROM unnest(ARRAY[50, 80, 100]) AS d
UNION ALL
SELECT 'ITEM', 'gis02:ITEM:AERIAL_CROSSING:' || m.code || ':' || d
FROM gis_material m CROSS JOIN unnest(ARRAY[150, 200, 250, 300, 400, 500, 600]) AS d
WHERE m.tenant_id = '${tenant_id}' AND m.code = 'STEEL'
UNION ALL
SELECT 'ITEM', 'gis02:ITEM:GALLERY:' || m.code || ':' || s
FROM gis_material m CROSS JOIN unnest(ARRAY[1.0, 1.5, 2.0, 2.5, 3.0]) AS s
WHERE m.tenant_id = '${tenant_id}' AND m.code = 'CONCRETE';

UPDATE gis_feature_type
SET factory_retired_on = CURRENT_DATE
WHERE tenant_id = '${tenant_id}' AND origin = 'FACTORY'
  AND factory_code LIKE 'gis02:%'
  AND factory_code NOT IN (SELECT code FROM gis02_factory_codes WHERE kind = 'TYPE')
  AND factory_retired_on IS NULL;

UPDATE gis_feature_type
SET factory_retired_on = NULL
WHERE tenant_id = '${tenant_id}' AND origin = 'FACTORY'
  AND factory_code IN (SELECT code FROM gis02_factory_codes WHERE kind = 'TYPE')
  AND factory_retired_on IS NOT NULL;

UPDATE gis_catalog_item
SET factory_retired_on = CURRENT_DATE
WHERE tenant_id = '${tenant_id}' AND origin = 'FACTORY'
  AND factory_code LIKE 'gis02:%'
  AND factory_code NOT IN (SELECT code FROM gis02_factory_codes WHERE kind = 'ITEM')
  AND factory_retired_on IS NULL;

UPDATE gis_catalog_item
SET factory_retired_on = NULL
WHERE tenant_id = '${tenant_id}' AND origin = 'FACTORY'
  AND factory_code IN (SELECT code FROM gis02_factory_codes WHERE kind = 'ITEM')
  AND factory_retired_on IS NOT NULL;

DROP TABLE IF EXISTS gis02_factory_codes;
