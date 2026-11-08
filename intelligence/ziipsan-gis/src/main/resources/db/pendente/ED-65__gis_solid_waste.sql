-- ============================================================================
-- ED-65 (PENDENTE DE NUMERACAO): residuos solidos no GIS — GIS-14
-- (GIS-CADASTRO-TECNICO §4.5, §5.4, §5.7, §11 e linha GIS-14 do §13.2;
-- ADR-0034 decisao 1; TR-1049; SG-1340 parte).
--
-- POR QUE PENDENTE: na main (HEAD 2f7758214d3f3772c4edd40ec005161a6a77fec5,
-- conferido via MCP em 07/11/2026) a serie tenant do GIS vai ate a
-- V13_12 (GIS-06/P3.25) e NENHUM numero seguinte esta confirmado livre
-- — as trilhas GIS-11 a GIS-13 (ED-54/ED-64/ED-63) disputam os mesmos
-- slots. Conforme a regra da tarefa, NENHUM numero foi inventado: este
-- SQL entra como db/pendente/ e recebe o numero da sequencia global na
-- integracao (precedentes: ED-22, ED-25, ED-55, ED-67).
--
-- Depende de: V13_1 (gis_domain WASTE, classes/tipos), V13_2 (gis_poi,
-- gis_structure, gis_zone com zone_type COLLECTION_SECTOR,
-- gis_attachment), V13_11 (gis_import_profile/gis_cad_layer_map),
-- consumer_units (registry, mesmo schema de tenant) e da extensao
-- pgrouting SOMENTE para a funcao de distancia (secao 5): sem a
-- extensao, a funcao levanta erro explicito e rota nenhuma nasce com
-- distancia inventada.
--
-- O QUE ESTA MIGRATION FAZ:
--   (1) catalogo do §4.5: classes WASTE_POINT/WASTE_FACILITY/
--       COLLECTION_SECTOR e os 13 tipos com factory_code gis14:<code>,
--       attributes_schema espelho das regras Java (GisWasteRules) e
--       style_key do simbolo (ADR-0036 — a ARTE e da P1.30; aqui so a
--       chave, marcador declarado no relatorio);
--   (2) listas de valores do §4.5/§4.6 (gis_value_list);
--   (3) grafo de logradouros para o pgRouting: vertices e arestas com
--       custo em metros (objeto de apoio da GIS-14, §5.7);
--   (4) projecao feicao -> vertice mais proximo (paradas da rota);
--   (5) funcao de distancia por pgr_dijkstra (guarda de extensao);
--   (6) gis_waste_route / gis_waste_route_stop (§3.4: objeto
--       operacional, nao feicao de cadastro);
--   (7) gis_waste_property_sector: o vinculo imovel <-> setor que a
--       taxa da ED-25 le (decisao LINKED/UNLINKED/REVIEW gravada);
--   (8) gis_waste_cleanup_request: o pedido de OS de limpeza do
--       descarte irregular (a OS em si e do nucleo, ED-36; o
--       service_order_id e preenchido na integracao);
--   (9) camadas de residuos no perfil GENERICO de importacao
--       (SG-1340, parte): gis_cad_layer_map da V13_11.
--
-- NENHUM DADO DE NEGOCIO E SEMEADO: setores, pontos, rotas, executor,
-- licencas e parametros sao do tenant. Volumes de conteiner, veiculos
-- e o limite de RCC do ecoponto sao "valor de referencia a validar"
-- no proprio §4.5 — entram nas listas/schemas como referencia, nunca
-- como norma.
--
-- CONVENCOES (as mesmas das V13_8/V13_9/V13_11): funcoes chamadas pela
-- api resolvem o schema por public.tenant_catalog via GUC
-- app.current_tenant (fallback: schema corrente); mensagens de erro
-- sem diacriticos; RLS via register_tenant_table.
-- ============================================================================

SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- ============================================================================
-- (1) Catalogo do §4.5 — classes
-- ============================================================================

INSERT INTO gis_feature_class (tenant_id, code, domain, topology_role, geometry_type, origin, active)
VALUES
    ('${tenant_id}', 'WASTE_POINT', 'WASTE', 'POI', 'Point', 'FACTORY', TRUE),
    ('${tenant_id}', 'WASTE_FACILITY', 'WASTE', 'STRUCTURE', 'MultiPolygon', 'FACTORY', TRUE),
    ('${tenant_id}', 'COLLECTION_SECTOR', 'WASTE', 'ZONE', 'MultiPolygon', 'FACTORY', TRUE)
ON CONFLICT (tenant_id, code) DO NOTHING;

-- ============================================================================
-- (1b) Catalogo do §4.5 — tipos (attributes_schema = espelho do
-- GisWasteRules; style_key = chave do simbolo, ADR-0036)
-- ============================================================================

INSERT INTO gis_feature_type (tenant_id, class_id, code, label, attributes_schema,
                              style_key, origin, factory_code, active, approval_status)
SELECT '${tenant_id}', c.id, t.code, t.label, t.schema::jsonb, t.style_key,
       'FACTORY', 'gis14:' || t.code, TRUE, 'APPROVED'
FROM gis_feature_class c
JOIN (VALUES
    ('WASTE_POINT', 'COLLECTION_POINT', 'Ponto de coleta',
     '{"type":"object","required":["waste_fraction","collection_frequency","shift"],"properties":{"waste_fraction":{"type":"string","enum":["REJECT","ORGANIC","DRY_RECYCLABLE","GLASS","MIXED","CONSTRUCTION","BULKY","PRUNING","HEALTHCARE","HAZARDOUS"]},"collection_frequency":{"type":"string"},"shift":{"type":"string","enum":["MORNING","AFTERNOON","NIGHT"]}}}',
     'waste-collection-point'),
    ('WASTE_POINT', 'CONTAINER', 'Contêiner',
     '{"type":"object","required":["waste_fraction","container_model","volume"],"properties":{"waste_fraction":{"type":"string","enum":["REJECT","ORGANIC","DRY_RECYCLABLE","GLASS","MIXED","CONSTRUCTION","BULKY","PRUNING","HEALTHCARE","HAZARDOUS"]},"container_model":{"type":"string","enum":["WHEELED_PE","STATIONARY_SKIP","SEMI_BURIED","UNDERGROUND"]},"volume":{"type":"number","exclusiveMinimum":0},"material":{"type":"string"},"asset_code":{"type":"string"}}}',
     'waste-container'),
    ('WASTE_POINT', 'DROP_OFF_POINT', 'PEV — ponto de entrega voluntária',
     '{"type":"object","required":["accepted_fractions","responsible_party_kind"],"properties":{"accepted_fractions":{"type":"array","minItems":1,"items":{"type":"string","enum":["REJECT","ORGANIC","DRY_RECYCLABLE","GLASS","MIXED","CONSTRUCTION","BULKY","PRUNING","HEALTHCARE","HAZARDOUS"]}},"responsible_party_kind":{"type":"string","enum":["MUNICIPALITY","REVERSE_LOGISTICS_SYSTEM","OPERATOR"]}}}',
     'waste-drop-off-point'),
    ('WASTE_FACILITY', 'ECOPOINT', 'Ecoponto',
     '{"type":"object","required":["accepted_materials"],"properties":{"accepted_materials":{"type":"array","minItems":1,"items":{"type":"string"}},"rcc_limit_m3":{"type":"number","minimum":0},"opening_hours":{"type":"string"},"capacity_m3":{"type":"number","minimum":0},"waste_class":{"type":"string","enum":["CLASS_1","CLASS_2"]}}}',
     'waste-ecopoint'),
    ('WASTE_POINT', 'LITTER_BIN', 'Lixeira pública',
     '{"type":"object","required":["volume_l"],"properties":{"volume_l":{"type":"number","exclusiveMinimum":0},"bin_kind":{"type":"string"}}}',
     'waste-litter-bin'),
    ('WASTE_POINT', 'ILLEGAL_DUMP', 'Ponto de descarte irregular',
     '{"type":"object","required":["identified_on","dump_kind","estimated_volume_m3","dump_status"],"properties":{"identified_on":{"type":"string"},"dump_kind":{"type":"string","enum":["CONSTRUCTION","BULKY","DOMESTIC","MIXED"]},"estimated_volume_m3":{"type":"number","minimum":0},"dump_status":{"type":"string","enum":["ACTIVE","CLEANING","ELIMINATED","REINCIDENT"]}}}',
     'waste-illegal-dump'),
    ('COLLECTION_SECTOR', 'COLLECTION_SECTOR', 'Setor de coleta',
     '{"type":"object","required":["collection_kind","collection_days","shift","executor"],"properties":{"collection_kind":{"type":"string","enum":["CONVENTIONAL","SELECTIVE","HEALTHCARE","SWEEPING"]},"collection_days":{"type":"array","minItems":1,"items":{"type":"string","enum":["MON","TUE","WED","THU","FRI","SAT","SUN"]}},"shift":{"type":"string","enum":["MORNING","AFTERNOON","NIGHT"]},"executor":{"type":"string"}}}',
     'waste-collection-sector'),
    ('WASTE_FACILITY', 'TRANSFER_STATION', 'Área de transbordo',
     '{"type":"object","required":["capacity_t_per_day"],"properties":{"capacity_t_per_day":{"type":"number","exclusiveMinimum":0},"license_number":{"type":"string"},"license_valid_to":{"type":"string"},"waste_class":{"type":"string","enum":["CLASS_1","CLASS_2"]}}}',
     'waste-transfer-station'),
    ('WASTE_FACILITY', 'SORTING_FACILITY', 'Unidade de triagem',
     '{"type":"object","required":["operator_kind"],"properties":{"operator_kind":{"type":"string","enum":["COOPERATIVE","ASSOCIATION","COMPANY","MUNICIPALITY"]},"capacity_t_per_day":{"type":"number","exclusiveMinimum":0}}}',
     'waste-sorting-facility'),
    ('WASTE_FACILITY', 'COMPOSTING_SITE', 'Compostagem',
     '{"type":"object","properties":{"capacity_t_per_day":{"type":"number","exclusiveMinimum":0},"license_number":{"type":"string"},"license_valid_to":{"type":"string"}}}',
     'waste-composting-site'),
    ('WASTE_FACILITY', 'SANITARY_LANDFILL', 'Aterro sanitário',
     '{"type":"object","properties":{"license_number":{"type":"string"},"license_valid_to":{"type":"string"},"useful_life_years":{"type":"number","minimum":0},"waste_class":{"type":"string","enum":["CLASS_1","CLASS_2"]}}}',
     'waste-sanitary-landfill'),
    ('WASTE_FACILITY', 'INERT_LANDFILL', 'Aterro de inertes',
     '{"type":"object","properties":{"license_number":{"type":"string"},"license_valid_to":{"type":"string"},"capacity_m3":{"type":"number","minimum":0}}}',
     'waste-inert-landfill'),
    ('WASTE_FACILITY', 'CONSTRUCTION_WASTE_FACILITY', 'Área de transbordo e triagem de RCC',
     '{"type":"object","properties":{"license_number":{"type":"string"},"license_valid_to":{"type":"string"},"capacity_m3":{"type":"number","minimum":0}}}',
     'waste-construction-facility')
) AS t(class_code, code, label, schema, style_key) ON t.class_code = c.code
WHERE c.tenant_id = '${tenant_id}'
ON CONFLICT (tenant_id, class_id, code) DO NOTHING;

-- ============================================================================
-- (2) Listas de valores (§4.5/§4.6) — widgets do QGIS e da web
-- ============================================================================

INSERT INTO gis_value_list (tenant_id, list_code, code, label, sort_order, origin, active)
VALUES
    ('${tenant_id}', 'waste_fraction', 'REJECT', 'Rejeito', 1, 'FACTORY', TRUE),
    ('${tenant_id}', 'waste_fraction', 'ORGANIC', 'Orgânico', 2, 'FACTORY', TRUE),
    ('${tenant_id}', 'waste_fraction', 'DRY_RECYCLABLE', 'Reciclável seco', 3, 'FACTORY', TRUE),
    ('${tenant_id}', 'waste_fraction', 'GLASS', 'Vidro', 4, 'FACTORY', TRUE),
    ('${tenant_id}', 'waste_fraction', 'MIXED', 'Misto', 5, 'FACTORY', TRUE),
    ('${tenant_id}', 'waste_fraction', 'CONSTRUCTION', 'RCC', 6, 'FACTORY', TRUE),
    ('${tenant_id}', 'waste_fraction', 'BULKY', 'Volumoso', 7, 'FACTORY', TRUE),
    ('${tenant_id}', 'waste_fraction', 'PRUNING', 'Podas', 8, 'FACTORY', TRUE),
    ('${tenant_id}', 'waste_fraction', 'HEALTHCARE', 'Serviços de saúde', 9, 'FACTORY', TRUE),
    ('${tenant_id}', 'waste_fraction', 'HAZARDOUS', 'Perigoso (classe 1)', 10, 'FACTORY', TRUE),
    ('${tenant_id}', 'collection_kind', 'CONVENTIONAL', 'Convencional', 1, 'FACTORY', TRUE),
    ('${tenant_id}', 'collection_kind', 'SELECTIVE', 'Seletiva', 2, 'FACTORY', TRUE),
    ('${tenant_id}', 'collection_kind', 'HEALTHCARE', 'Serviços de saúde', 3, 'FACTORY', TRUE),
    ('${tenant_id}', 'collection_kind', 'SWEEPING', 'Varrição', 4, 'FACTORY', TRUE),
    ('${tenant_id}', 'dump_kind', 'CONSTRUCTION', 'RCC', 1, 'FACTORY', TRUE),
    ('${tenant_id}', 'dump_kind', 'BULKY', 'Volumoso', 2, 'FACTORY', TRUE),
    ('${tenant_id}', 'dump_kind', 'DOMESTIC', 'Doméstico', 3, 'FACTORY', TRUE),
    ('${tenant_id}', 'dump_kind', 'MIXED', 'Misto', 4, 'FACTORY', TRUE),
    ('${tenant_id}', 'dump_status', 'ACTIVE', 'Ativo', 1, 'FACTORY', TRUE),
    ('${tenant_id}', 'dump_status', 'CLEANING', 'Em limpeza', 2, 'FACTORY', TRUE),
    ('${tenant_id}', 'dump_status', 'ELIMINATED', 'Eliminado', 3, 'FACTORY', TRUE),
    ('${tenant_id}', 'dump_status', 'REINCIDENT', 'Reincidente', 4, 'FACTORY', TRUE),
    ('${tenant_id}', 'vehicle_kind', 'COMPACTOR_15M3', 'Compactador 15 m³', 1, 'FACTORY', TRUE),
    ('${tenant_id}', 'vehicle_kind', 'COMPACTOR_19M3', 'Compactador 19 m³', 2, 'FACTORY', TRUE),
    ('${tenant_id}', 'vehicle_kind', 'BOX_TRUCK', 'Baú', 3, 'FACTORY', TRUE),
    ('${tenant_id}', 'vehicle_kind', 'SKIP_LOADER', 'Poliguindaste', 4, 'FACTORY', TRUE)
ON CONFLICT DO NOTHING;

-- ============================================================================
-- (3) Grafo de logradouros (§5.7): vertices e arestas com custo em metros.
-- A origem do grafo e a camada de contexto STREET da importacao
-- (gis_context_feature) ou o cadastro do tenant: esta migration cria
-- as tabelas; a CARGA do grafo (quebrar vias nos cruzamentos, montar
-- source/target) e rotina de conjunto da implantacao, registrada na
-- secao (4) como funcao de projecao — nenhum logradouro e inventado.
-- pgr_id e o id numerico que o pgRouting exige (id/source/target).
-- ============================================================================

CREATE TABLE IF NOT EXISTS gis_waste_street_vertex (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    pgr_id BIGINT GENERATED ALWAYS AS IDENTITY,
    geom geometry(Point, 4674) NOT NULL,
    source_ref TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_waste_street_vertex_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_waste_street_vertex_pgr UNIQUE (tenant_id, pgr_id)
);

SELECT register_tenant_table('gis_waste_street_vertex');

CREATE INDEX IF NOT EXISTS idx_gis_waste_street_vertex_geom
    ON gis_waste_street_vertex USING GIST (geom);

CREATE TABLE IF NOT EXISTS gis_waste_street_edge (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    pgr_id BIGINT GENERATED ALWAYS AS IDENTITY,
    source_pgr BIGINT NOT NULL,
    target_pgr BIGINT NOT NULL,
    cost_m NUMERIC NOT NULL CHECK (cost_m >= 0),
    -- Mao unica: reverse_cost = -1 (convencao do pgRouting).
    reverse_cost_m NUMERIC NOT NULL DEFAULT -1,
    name TEXT,
    geom geometry(LineString, 4674),
    source_batch_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_waste_street_edge_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_waste_street_edge_pgr UNIQUE (tenant_id, pgr_id),
    CONSTRAINT fk_gis_waste_street_edge_source
        FOREIGN KEY (tenant_id, source_pgr)
        REFERENCES gis_waste_street_vertex (tenant_id, pgr_id),
    CONSTRAINT fk_gis_waste_street_edge_target
        FOREIGN KEY (tenant_id, target_pgr)
        REFERENCES gis_waste_street_vertex (tenant_id, pgr_id)
);

SELECT register_tenant_table('gis_waste_street_edge');

CREATE INDEX IF NOT EXISTS idx_gis_waste_street_edge_geom
    ON gis_waste_street_edge USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_gis_waste_street_edge_source
    ON gis_waste_street_edge (tenant_id, source_pgr);
CREATE INDEX IF NOT EXISTS idx_gis_waste_street_edge_target
    ON gis_waste_street_edge (tenant_id, target_pgr);

-- ============================================================================
-- (4) Projecao feicao -> vertice mais proximo (paradas da rota)
-- ============================================================================

CREATE TABLE IF NOT EXISTS gis_waste_feature_vertex (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    feature_table TEXT NOT NULL CHECK (feature_table IN ('gis_poi', 'gis_structure')),
    feature_id UUID NOT NULL,
    vertex_pgr_id BIGINT NOT NULL,
    distance_m NUMERIC NOT NULL CHECK (distance_m >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_waste_feature_vertex_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_waste_feature_vertex_feature
        UNIQUE (tenant_id, feature_table, feature_id),
    CONSTRAINT fk_gis_waste_feature_vertex_vertex
        FOREIGN KEY (tenant_id, vertex_pgr_id)
        REFERENCES gis_waste_street_vertex (tenant_id, pgr_id)
);

SELECT register_tenant_table('gis_waste_feature_vertex');

-- (Re)projeta os pontos de residuos EM OPERACAO no vertice mais
-- proximo (distancia geodesica, geography). Devolve quantas
-- projecoes foram gravadas. Sem vertice no grafo, nada e projetado
-- (a rota recusa a parada sem projecao — nunca presume vertice).
CREATE OR REPLACE FUNCTION gis_waste_project_features(p_tenant_id UUID)
RETURNS INT AS $$
DECLARE
    v_schema TEXT;
    v_count INT;
BEGIN
    SELECT schema_name INTO v_schema
    FROM public.tenant_catalog
    WHERE id = NULLIF(current_setting('app.current_tenant', true), '')::uuid;
    IF v_schema IS NULL THEN
        v_schema := current_schema();
    END IF;
    PERFORM set_config('search_path', v_schema || ', public', true);

    WITH points AS (
        SELECT 'gis_poi'::text AS feature_table, p.id AS feature_id, p.geom AS pt
          FROM gis_poi p
          JOIN gis_feature_type ft
            ON ft.id = p.feature_type_id AND ft.tenant_id = p.tenant_id
         WHERE p.tenant_id = p_tenant_id AND p.lifecycle_status = 'IN_SERVICE'
           AND ft.factory_code LIKE 'gis14:%'
        UNION ALL
        SELECT 'gis_structure', s.id,
               COALESCE(s.geom_point, ST_Centroid(s.geom))
          FROM gis_structure s
          JOIN gis_feature_type ft
            ON ft.id = s.feature_type_id AND ft.tenant_id = s.tenant_id
         WHERE s.tenant_id = p_tenant_id AND s.lifecycle_status = 'IN_SERVICE'
           AND ft.factory_code = 'gis14:ECOPOINT'
    ),
    nearest AS (
        SELECT pt.feature_table, pt.feature_id, v.pgr_id,
               ST_Distance(v.geom::geography, pt.pt::geography) AS distance_m
          FROM points pt
          CROSS JOIN LATERAL (
              SELECT sv.pgr_id, sv.geom
                FROM gis_waste_street_vertex sv
               WHERE sv.tenant_id = p_tenant_id
               ORDER BY sv.geom::geography <-> pt.pt::geography
               LIMIT 1
          ) v
    )
    INSERT INTO gis_waste_feature_vertex (tenant_id, feature_table, feature_id,
            vertex_pgr_id, distance_m)
    SELECT p_tenant_id, feature_table, feature_id, pgr_id, distance_m
      FROM nearest
    ON CONFLICT (tenant_id, feature_table, feature_id) DO UPDATE
        SET vertex_pgr_id = EXCLUDED.vertex_pgr_id,
            distance_m = EXCLUDED.distance_m, updated_at = NOW();
    GET DIAGNOSTICS v_count = ROW_COUNT;
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- (5) Distancia por pgRouting (pgr_dijkstra). Sem a extensao, ERRO
-- EXPLICITO: distancia nenhuma e inventada fora do grafo.
-- Devolve NULL quando nao ha caminho entre os vertices.
-- ============================================================================

CREATE OR REPLACE FUNCTION gis_waste_pgr_distance(
    p_tenant_id UUID, p_from BIGINT, p_to BIGINT)
RETURNS NUMERIC AS $$
DECLARE
    v_schema TEXT;
    v_sql TEXT;
    v_cost NUMERIC;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pgrouting') THEN
        RAISE EXCEPTION 'gis: extensao pgrouting nao disponivel neste banco; distancia de rota nao calculada';
    END IF;
    SELECT schema_name INTO v_schema
    FROM public.tenant_catalog
    WHERE id = NULLIF(current_setting('app.current_tenant', true), '')::uuid;
    IF v_schema IS NULL THEN
        v_schema := current_schema();
    END IF;
    PERFORM set_config('search_path', v_schema || ', public', true);

    v_sql := format(
        'SELECT pgr_id AS id, source_pgr AS source, target_pgr AS target, '
        || 'cost_m AS cost, reverse_cost_m AS reverse_cost '
        || 'FROM gis_waste_street_edge WHERE tenant_id = %L', p_tenant_id);
    SELECT MAX(agg_cost) INTO v_cost
    FROM pgr_dijkstra(v_sql, p_from, p_to, directed := true);
    RETURN v_cost;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- (6) Rotas de coleta (§3.4: objeto operacional, nao feicao)
-- ============================================================================

CREATE TABLE IF NOT EXISTS gis_waste_route (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code TEXT NOT NULL,
    sector_zone_id UUID NOT NULL,
    vehicle_kind TEXT NOT NULL CHECK (vehicle_kind IN
        ('COMPACTOR_15M3', 'COMPACTOR_19M3', 'BOX_TRUCK', 'SKIP_LOADER')),
    crew_label TEXT,
    depot_vertex BIGINT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PLANNED' CHECK (status IN
        ('PLANNED', 'ACTIVE', 'INACTIVE')),
    -- Extensao total (tour + retorno ao deposito), em metros, vinda do
    -- grafo. Estimativa de tempo SO existe com os parametros de quem
    -- pede (velocidade media e tempo por parada): sem eles, NULL —
    -- nenhum tempo padrao e inventado (GisWasteRules.estimate).
    total_extension_m NUMERIC CHECK (total_extension_m >= 0),
    estimated_minutes NUMERIC CHECK (estimated_minutes >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_waste_route_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_waste_route_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_gis_waste_route_sector
        FOREIGN KEY (tenant_id, sector_zone_id) REFERENCES gis_zone (tenant_id, id),
    CONSTRAINT fk_gis_waste_route_depot
        FOREIGN KEY (tenant_id, depot_vertex)
        REFERENCES gis_waste_street_vertex (tenant_id, pgr_id)
);

SELECT register_tenant_table('gis_waste_route');

CREATE TABLE IF NOT EXISTS gis_waste_route_stop (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    route_id UUID NOT NULL,
    feature_table TEXT NOT NULL CHECK (feature_table IN ('gis_poi', 'gis_structure')),
    feature_id UUID NOT NULL,
    sequence INT NOT NULL CHECK (sequence > 0),
    leg_cost_m NUMERIC NOT NULL CHECK (leg_cost_m >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_waste_route_stop_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_waste_route_stop_sequence UNIQUE (tenant_id, route_id, sequence),
    CONSTRAINT uq_gis_waste_route_stop_feature
        UNIQUE (tenant_id, route_id, feature_table, feature_id),
    CONSTRAINT fk_gis_waste_route_stop_route
        FOREIGN KEY (tenant_id, route_id) REFERENCES gis_waste_route (tenant_id, id)
);

SELECT register_tenant_table('gis_waste_route_stop');

CREATE INDEX IF NOT EXISTS idx_gis_waste_route_sector
    ON gis_waste_route (tenant_id, sector_zone_id) WHERE status <> 'INACTIVE';

-- ============================================================================
-- (7) Vinculo imovel <-> setor de coleta (a leitura da taxa da ED-25).
-- property_unit_id e consumer_units.id (registry, mesmo schema): sem
-- FK, pelo precedente da V13_12 (modulo vizinho); a decisao gravada e
-- LINKED (um setor), UNLINKED (nenhum) ou REVIEW (sobreposicao).
-- ============================================================================

CREATE TABLE IF NOT EXISTS gis_waste_property_sector (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    property_unit_id UUID NOT NULL,
    sector_zone_id UUID,
    status TEXT NOT NULL CHECK (status IN ('LINKED', 'UNLINKED', 'REVIEW')),
    decided_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_waste_property_sector_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_waste_property_sector_unit UNIQUE (tenant_id, property_unit_id),
    CONSTRAINT ck_gis_waste_property_sector_link
        CHECK ((status = 'LINKED' AND sector_zone_id IS NOT NULL)
               OR (status <> 'LINKED' AND sector_zone_id IS NULL)),
    CONSTRAINT fk_gis_waste_property_sector_zone
        FOREIGN KEY (tenant_id, sector_zone_id) REFERENCES gis_zone (tenant_id, id)
);

SELECT register_tenant_table('gis_waste_property_sector');

CREATE INDEX IF NOT EXISTS idx_gis_waste_property_sector_zone
    ON gis_waste_property_sector (tenant_id, sector_zone_id) WHERE status = 'LINKED';

-- ============================================================================
-- (8) Pedido de OS de limpeza do descarte irregular (GIS-14: "com foto
-- e OS de limpeza"). A foto e pre-condicao conferida na api
-- (gis_attachment PHOTO) e a situacao do ponto anda em attributes
-- (dump_status). A OS em si e do nucleo de OS (ED-36): o
-- service_order_id nasce NULL e a integracao o preenche ao abrir a OS
-- e move o pedido a ORDER_OPENED — nenhum tipo de OS e inventado aqui.
-- ============================================================================

CREATE TABLE IF NOT EXISTS gis_waste_cleanup_request (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    dump_poi_id UUID NOT NULL,
    service_order_id UUID,
    status TEXT NOT NULL DEFAULT 'REQUESTED' CHECK (status IN
        ('REQUESTED', 'ORDER_OPENED', 'COMPLETED', 'CANCELLED')),
    requested_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_waste_cleanup_request_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_gis_waste_cleanup_request_dump
        FOREIGN KEY (tenant_id, dump_poi_id) REFERENCES gis_poi (tenant_id, id)
);

SELECT register_tenant_table('gis_waste_cleanup_request');

CREATE INDEX IF NOT EXISTS idx_gis_waste_cleanup_request_dump
    ON gis_waste_cleanup_request (tenant_id, dump_poi_id, requested_on);

-- ============================================================================
-- (9) Camadas de residuos na importacao (SG-1340, parte) — perfil
-- GENERICO da GIS-07 (V13_11). Convencao desta entrega: a camada de
-- origem vem nomeada pelo codigo do tipo do §4.5; o tenant renomeia
-- no mapeamento quando a origem usa outro nome (perfil generico,
-- §9.2). Nenhuma camada de residuos e pessoal. As camadas dos perfis
-- Corsan/Saneago sao da GIS-07 e NAO sao tocadas aqui.
-- ============================================================================

INSERT INTO gis_cad_layer_map
    (tenant_id, profile_id, source_layer, target_kind, feature_table, feature_type_code, personal_data)
SELECT '${tenant_id}', p.id, m.layer, 'FEATURE', m.tbl, 'gis14:' || m.layer, FALSE
FROM gis_import_profile p,
     (VALUES
        ('COLLECTION_POINT', 'gis_poi'),
        ('CONTAINER', 'gis_poi'),
        ('DROP_OFF_POINT', 'gis_poi'),
        ('ECOPOINT', 'gis_structure'),
        ('LITTER_BIN', 'gis_poi'),
        ('ILLEGAL_DUMP', 'gis_poi'),
        ('COLLECTION_SECTOR', 'gis_zone'),
        ('TRANSFER_STATION', 'gis_structure'),
        ('SORTING_FACILITY', 'gis_structure'),
        ('COMPOSTING_SITE', 'gis_structure'),
        ('SANITARY_LANDFILL', 'gis_structure'),
        ('INERT_LANDFILL', 'gis_structure'),
        ('CONSTRUCTION_WASTE_FACILITY', 'gis_structure')
     ) AS m(layer, tbl)
WHERE p.tenant_id = '${tenant_id}' AND p.key = 'GENERICO'
  AND NOT EXISTS (SELECT 1 FROM gis_cad_layer_map x
                  WHERE x.tenant_id = p.tenant_id AND x.profile_id = p.id
                    AND x.source_layer = m.layer);
