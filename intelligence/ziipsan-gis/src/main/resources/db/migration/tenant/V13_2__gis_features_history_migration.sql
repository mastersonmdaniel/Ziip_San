-- GIS-01 (P1.26) — feicoes, historico, migracao do gis_network_element e view de
-- compatibilidade (GIS-CADASTRO-TECNICO §3.4 a §3.8, passos M1 a M3 do §2.3).
--
-- ORDEM DESTA MIGRATION: (1) tabelas e gatilhos; (2) fim do UNIQUE global de code em
-- dma_district e leak_detection; (3) seed das classes e tipos do mapeamento §2.4;
-- (4) rename da tabela antiga, conversao das linhas e relatorio de nao mapeadas;
-- (5) view gis_network_element e funcao de consulta por data.
--
-- RLS: idem V13_1 — a GUC abaixo e obrigatoria, porque a RLS e FORCE tambem para o
-- migrador e os INSERTs de seed e de conversao esbarrariam no WITH CHECK sem ela.
-- O placeholder ${tenant_id} e substituido pelo TenantMigrator (mesmo padrao da V2_8).

SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- ======================================================================
-- Colunas comuns (§3.5), repetidas por tabela para leitura direta.
-- lifecycle_status: PLANNED, UNDER_CONSTRUCTION, IN_SERVICE, ABANDONED.
-- NENHUMA coluna de idade armazenada: a idade e calculada (view e relatorios).
-- dma_id e o vinculo transitorio carregado da migracao: o §3.6.1 preve DMA derivada,
-- e a derivacao por zona (gis_zone do tipo DMA) chega na GIS-11; ate la, o vinculo
-- migrado e o que alimenta a view de compatibilidade (M3). A coluna sai com a view,
-- na M5/M6 da GIS-12.
-- node_1_id/node_2_id sao anulaveis aqui: o legado nao tem pontas, e o snap com
-- criacao de JUNCTION e da GIS-03 (P2.21). A obrigatoriedade em IN_SERVICE e
-- UNDER_CONSTRUCTION entra la, quando houver como preencher.
-- change_set_id nao tem FK: os pacotes chegam na V13_4 (GIS-04).
-- ======================================================================

CREATE TABLE IF NOT EXISTS gis_node (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code TEXT NOT NULL,
    feature_type_id UUID NOT NULL,
    catalog_item_id UUID,
    name TEXT,
    lifecycle_status TEXT NOT NULL DEFAULT 'PLANNED'
        CHECK (lifecycle_status IN ('PLANNED', 'UNDER_CONSTRUCTION', 'IN_SERVICE', 'ABANDONED')),
    retired_reason TEXT CHECK (retired_reason IN ('ABANDONED_IN_PLACE', 'REMOVED')),
    operational_status TEXT,
    normal_status TEXT,
    condition_rating TEXT CHECK (condition_rating IN ('GOOD', 'FAIR', 'POOR', 'CRITICAL', 'UNKNOWN')),
    installed_on DATE,
    installed_on_precision TEXT CHECK (installed_on_precision IN ('DAY', 'MONTH', 'YEAR', 'UNKNOWN')),
    abandoned_on DATE,
    change_set_id UUID,
    attributes JSONB NOT NULL DEFAULT '{}',
    schema_version INT NOT NULL DEFAULT 1,
    capture_method TEXT NOT NULL CHECK (capture_method IN
        ('RTK_FIXED', 'RTK_FLOAT', 'TOTAL_STATION', 'GNSS_NAVIGATION', 'TAPE_TIE',
         'DIGITIZED', 'CAD_IMPORT', 'AUTO_SERVICE_LINE', 'DESIGN', 'ESTIMATED')),
    h_accuracy_m NUMERIC,
    v_accuracy_m NUMERIC,
    vertical_datum TEXT NOT NULL DEFAULT 'UNKNOWN'
        CHECK (vertical_datum IN ('IMBITUBA', 'SANTANA', 'ELLIPSOIDAL', 'UNKNOWN')),
    captured_on DATE,
    responsible_party_id UUID,
    source TEXT CHECK (source IN
        ('PROJECT', 'AS_BUILT', 'SERVICE_ORDER', 'FIELD', 'IMPORT', 'DIGITIZATION', 'AUTO')),
    source_batch_id UUID,
    source_ref TEXT,
    legacy_object_id TEXT,
    accuracy_status TEXT CHECK (accuracy_status IN
        ('WITHIN_TARGET', 'OUT_OF_TARGET', 'APPROXIMATE', 'UNKNOWN')),
    asset_codes JSONB,
    dma_id UUID,
    ground_elevation_m NUMERIC,
    rim_elevation_m NUMERIC,
    invert_elevation_m NUMERIC,
    depth_m NUMERIC GENERATED ALWAYS AS (
        CASE WHEN rim_elevation_m IS NOT NULL AND invert_elevation_m IS NOT NULL
             THEN rim_elevation_m - invert_elevation_m END) STORED,
    symbol_rotation_deg NUMERIC,
    pressure_zone TEXT,
    setting NUMERIC,
    geom geometry(PointZ, 4674) NOT NULL,
    geom_web geometry(Geometry, 3857),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_node_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_node_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_gis_node_type
        FOREIGN KEY (tenant_id, feature_type_id) REFERENCES gis_feature_type (tenant_id, id),
    CONSTRAINT fk_gis_node_item
        FOREIGN KEY (tenant_id, catalog_item_id) REFERENCES gis_catalog_item (tenant_id, id),
    CONSTRAINT fk_gis_node_responsible
        FOREIGN KEY (tenant_id, responsible_party_id) REFERENCES gis_responsible_party (tenant_id, id),
    CONSTRAINT fk_gis_node_dma
        FOREIGN KEY (tenant_id, dma_id) REFERENCES dma_district (tenant_id, id),
    CONSTRAINT ck_gis_node_retired_reason
        CHECK ((lifecycle_status = 'ABANDONED' AND retired_reason IS NOT NULL)
               OR (lifecycle_status <> 'ABANDONED' AND retired_reason IS NULL))
);

CREATE TABLE IF NOT EXISTS gis_arc (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code TEXT NOT NULL,
    feature_type_id UUID NOT NULL,
    catalog_item_id UUID,
    name TEXT,
    lifecycle_status TEXT NOT NULL DEFAULT 'PLANNED'
        CHECK (lifecycle_status IN ('PLANNED', 'UNDER_CONSTRUCTION', 'IN_SERVICE', 'ABANDONED')),
    retired_reason TEXT CHECK (retired_reason IN ('ABANDONED_IN_PLACE', 'REMOVED')),
    operational_status TEXT,
    normal_status TEXT,
    condition_rating TEXT CHECK (condition_rating IN ('GOOD', 'FAIR', 'POOR', 'CRITICAL', 'UNKNOWN')),
    installed_on DATE,
    installed_on_precision TEXT CHECK (installed_on_precision IN ('DAY', 'MONTH', 'YEAR', 'UNKNOWN')),
    abandoned_on DATE,
    change_set_id UUID,
    attributes JSONB NOT NULL DEFAULT '{}',
    schema_version INT NOT NULL DEFAULT 1,
    capture_method TEXT NOT NULL CHECK (capture_method IN
        ('RTK_FIXED', 'RTK_FLOAT', 'TOTAL_STATION', 'GNSS_NAVIGATION', 'TAPE_TIE',
         'DIGITIZED', 'CAD_IMPORT', 'AUTO_SERVICE_LINE', 'DESIGN', 'ESTIMATED')),
    h_accuracy_m NUMERIC,
    v_accuracy_m NUMERIC,
    vertical_datum TEXT NOT NULL DEFAULT 'UNKNOWN'
        CHECK (vertical_datum IN ('IMBITUBA', 'SANTANA', 'ELLIPSOIDAL', 'UNKNOWN')),
    captured_on DATE,
    responsible_party_id UUID,
    source TEXT CHECK (source IN
        ('PROJECT', 'AS_BUILT', 'SERVICE_ORDER', 'FIELD', 'IMPORT', 'DIGITIZATION', 'AUTO')),
    source_batch_id UUID,
    source_ref TEXT,
    legacy_object_id TEXT,
    accuracy_status TEXT CHECK (accuracy_status IN
        ('WITHIN_TARGET', 'OUT_OF_TARGET', 'APPROXIMATE', 'UNKNOWN')),
    asset_codes JSONB,
    dma_id UUID,
    node_1_id UUID,
    node_2_id UUID,
    length_m NUMERIC GENERATED ALWAYS AS (ST_Length(geom::geography)) STORED,
    declared_length_m NUMERIC,
    upstream_invert_m NUMERIC,
    downstream_invert_m NUMERIC,
    slope NUMERIC GENERATED ALWAYS AS (
        CASE WHEN upstream_invert_m IS NOT NULL AND downstream_invert_m IS NOT NULL
                  AND ST_Length(geom::geography) > 0
             THEN (downstream_invert_m - upstream_invert_m) / ST_Length(geom::geography)
        END) STORED,
    pressurized BOOLEAN,
    flow_direction TEXT CHECK (flow_direction IN ('WITH_GEOMETRY', 'AGAINST_GEOMETRY', 'BOTH', 'UNKNOWN')),
    network_function TEXT,
    depth_m NUMERIC,
    street_side TEXT,
    dist_to_property_line_m NUMERIC,
    dist_to_curb_m NUMERIC,
    pavement_type TEXT,
    geom geometry(LineStringZ, 4674) NOT NULL,
    geom_web geometry(Geometry, 3857),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_arc_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_arc_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_gis_arc_type
        FOREIGN KEY (tenant_id, feature_type_id) REFERENCES gis_feature_type (tenant_id, id),
    CONSTRAINT fk_gis_arc_item
        FOREIGN KEY (tenant_id, catalog_item_id) REFERENCES gis_catalog_item (tenant_id, id),
    CONSTRAINT fk_gis_arc_responsible
        FOREIGN KEY (tenant_id, responsible_party_id) REFERENCES gis_responsible_party (tenant_id, id),
    CONSTRAINT fk_gis_arc_dma
        FOREIGN KEY (tenant_id, dma_id) REFERENCES dma_district (tenant_id, id),
    CONSTRAINT fk_gis_arc_node_1
        FOREIGN KEY (tenant_id, node_1_id) REFERENCES gis_node (tenant_id, id),
    CONSTRAINT fk_gis_arc_node_2
        FOREIGN KEY (tenant_id, node_2_id) REFERENCES gis_node (tenant_id, id)
);

CREATE TABLE IF NOT EXISTS gis_connection (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code TEXT NOT NULL,
    feature_type_id UUID NOT NULL,
    catalog_item_id UUID,
    name TEXT,
    lifecycle_status TEXT NOT NULL DEFAULT 'PLANNED'
        CHECK (lifecycle_status IN ('PLANNED', 'UNDER_CONSTRUCTION', 'IN_SERVICE', 'ABANDONED')),
    retired_reason TEXT CHECK (retired_reason IN ('ABANDONED_IN_PLACE', 'REMOVED')),
    operational_status TEXT,
    normal_status TEXT,
    condition_rating TEXT CHECK (condition_rating IN ('GOOD', 'FAIR', 'POOR', 'CRITICAL', 'UNKNOWN')),
    installed_on DATE,
    installed_on_precision TEXT CHECK (installed_on_precision IN ('DAY', 'MONTH', 'YEAR', 'UNKNOWN')),
    abandoned_on DATE,
    change_set_id UUID,
    attributes JSONB NOT NULL DEFAULT '{}',
    schema_version INT NOT NULL DEFAULT 1,
    capture_method TEXT NOT NULL CHECK (capture_method IN
        ('RTK_FIXED', 'RTK_FLOAT', 'TOTAL_STATION', 'GNSS_NAVIGATION', 'TAPE_TIE',
         'DIGITIZED', 'CAD_IMPORT', 'AUTO_SERVICE_LINE', 'DESIGN', 'ESTIMATED')),
    h_accuracy_m NUMERIC,
    v_accuracy_m NUMERIC,
    vertical_datum TEXT NOT NULL DEFAULT 'UNKNOWN'
        CHECK (vertical_datum IN ('IMBITUBA', 'SANTANA', 'ELLIPSOIDAL', 'UNKNOWN')),
    captured_on DATE,
    responsible_party_id UUID,
    source TEXT CHECK (source IN
        ('PROJECT', 'AS_BUILT', 'SERVICE_ORDER', 'FIELD', 'IMPORT', 'DIGITIZATION', 'AUTO')),
    source_batch_id UUID,
    source_ref TEXT,
    legacy_object_id TEXT,
    accuracy_status TEXT CHECK (accuracy_status IN
        ('WITHIN_TARGET', 'OUT_OF_TARGET', 'APPROXIMATE', 'UNKNOWN')),
    asset_codes JSONB,
    dma_id UUID,
    arc_id UUID,
    tap_node_id UUID,
    service_connection_id UUID,
    endpoint_type TEXT CHECK (endpoint_type IN ('METER_SETTING', 'SEWER_CONNECTION_BOX')),
    length_m NUMERIC GENERATED ALWAYS AS (ST_Length(geom::geography)) STORED,
    depth_m NUMERIC,
    dist_to_property_line_m NUMERIC,
    dist_to_curb_m NUMERIC,
    geom geometry(LineStringZ, 4674) NOT NULL,
    geom_web geometry(Geometry, 3857),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_connection_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_connection_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_gis_connection_type
        FOREIGN KEY (tenant_id, feature_type_id) REFERENCES gis_feature_type (tenant_id, id),
    CONSTRAINT fk_gis_connection_item
        FOREIGN KEY (tenant_id, catalog_item_id) REFERENCES gis_catalog_item (tenant_id, id),
    CONSTRAINT fk_gis_connection_responsible
        FOREIGN KEY (tenant_id, responsible_party_id) REFERENCES gis_responsible_party (tenant_id, id),
    CONSTRAINT fk_gis_connection_dma
        FOREIGN KEY (tenant_id, dma_id) REFERENCES dma_district (tenant_id, id),
    CONSTRAINT fk_gis_connection_arc
        FOREIGN KEY (tenant_id, arc_id) REFERENCES gis_arc (tenant_id, id),
    CONSTRAINT fk_gis_connection_tap_node
        FOREIGN KEY (tenant_id, tap_node_id) REFERENCES gis_node (tenant_id, id)
);

CREATE TABLE IF NOT EXISTS gis_structure (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code TEXT NOT NULL,
    feature_type_id UUID NOT NULL,
    catalog_item_id UUID,
    name TEXT,
    lifecycle_status TEXT NOT NULL DEFAULT 'PLANNED'
        CHECK (lifecycle_status IN ('PLANNED', 'UNDER_CONSTRUCTION', 'IN_SERVICE', 'ABANDONED')),
    retired_reason TEXT CHECK (retired_reason IN ('ABANDONED_IN_PLACE', 'REMOVED')),
    operational_status TEXT,
    normal_status TEXT,
    condition_rating TEXT CHECK (condition_rating IN ('GOOD', 'FAIR', 'POOR', 'CRITICAL', 'UNKNOWN')),
    installed_on DATE,
    installed_on_precision TEXT CHECK (installed_on_precision IN ('DAY', 'MONTH', 'YEAR', 'UNKNOWN')),
    abandoned_on DATE,
    change_set_id UUID,
    attributes JSONB NOT NULL DEFAULT '{}',
    schema_version INT NOT NULL DEFAULT 1,
    capture_method TEXT NOT NULL CHECK (capture_method IN
        ('RTK_FIXED', 'RTK_FLOAT', 'TOTAL_STATION', 'GNSS_NAVIGATION', 'TAPE_TIE',
         'DIGITIZED', 'CAD_IMPORT', 'AUTO_SERVICE_LINE', 'DESIGN', 'ESTIMATED')),
    h_accuracy_m NUMERIC,
    v_accuracy_m NUMERIC,
    vertical_datum TEXT NOT NULL DEFAULT 'UNKNOWN'
        CHECK (vertical_datum IN ('IMBITUBA', 'SANTANA', 'ELLIPSOIDAL', 'UNKNOWN')),
    captured_on DATE,
    responsible_party_id UUID,
    source TEXT CHECK (source IN
        ('PROJECT', 'AS_BUILT', 'SERVICE_ORDER', 'FIELD', 'IMPORT', 'DIGITIZATION', 'AUTO')),
    source_batch_id UUID,
    source_ref TEXT,
    legacy_object_id TEXT,
    accuracy_status TEXT CHECK (accuracy_status IN
        ('WITHIN_TARGET', 'OUT_OF_TARGET', 'APPROXIMATE', 'UNKNOWN')),
    asset_codes JSONB,
    dma_id UUID,
    volume_m3 NUMERIC,
    nominal_flow_lps NUMERIC,
    power_kw NUMERIC,
    head_m NUMERIC,
    geom geometry(MultiPolygon, 4674),
    geom_point geometry(Point, 4674),
    geom_web geometry(Geometry, 3857),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_structure_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_structure_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_gis_structure_one_geom CHECK (geom IS NOT NULL OR geom_point IS NOT NULL),
    CONSTRAINT ck_gis_structure_retired_reason
        CHECK ((lifecycle_status = 'ABANDONED' AND retired_reason IS NOT NULL)
               OR (lifecycle_status <> 'ABANDONED' AND retired_reason IS NULL)),
    CONSTRAINT fk_gis_structure_type
        FOREIGN KEY (tenant_id, feature_type_id) REFERENCES gis_feature_type (tenant_id, id),
    CONSTRAINT fk_gis_structure_item
        FOREIGN KEY (tenant_id, catalog_item_id) REFERENCES gis_catalog_item (tenant_id, id),
    CONSTRAINT fk_gis_structure_responsible
        FOREIGN KEY (tenant_id, responsible_party_id) REFERENCES gis_responsible_party (tenant_id, id),
    CONSTRAINT fk_gis_structure_dma
        FOREIGN KEY (tenant_id, dma_id) REFERENCES dma_district (tenant_id, id)
);

CREATE TABLE IF NOT EXISTS gis_zone (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code TEXT NOT NULL,
    feature_type_id UUID NOT NULL,
    catalog_item_id UUID,
    name TEXT,
    lifecycle_status TEXT NOT NULL DEFAULT 'PLANNED'
        CHECK (lifecycle_status IN ('PLANNED', 'UNDER_CONSTRUCTION', 'IN_SERVICE', 'ABANDONED')),
    retired_reason TEXT CHECK (retired_reason IN ('ABANDONED_IN_PLACE', 'REMOVED')),
    operational_status TEXT,
    normal_status TEXT,
    condition_rating TEXT CHECK (condition_rating IN ('GOOD', 'FAIR', 'POOR', 'CRITICAL', 'UNKNOWN')),
    installed_on DATE,
    installed_on_precision TEXT CHECK (installed_on_precision IN ('DAY', 'MONTH', 'YEAR', 'UNKNOWN')),
    abandoned_on DATE,
    change_set_id UUID,
    attributes JSONB NOT NULL DEFAULT '{}',
    schema_version INT NOT NULL DEFAULT 1,
    capture_method TEXT NOT NULL CHECK (capture_method IN
        ('RTK_FIXED', 'RTK_FLOAT', 'TOTAL_STATION', 'GNSS_NAVIGATION', 'TAPE_TIE',
         'DIGITIZED', 'CAD_IMPORT', 'AUTO_SERVICE_LINE', 'DESIGN', 'ESTIMATED')),
    h_accuracy_m NUMERIC,
    v_accuracy_m NUMERIC,
    vertical_datum TEXT NOT NULL DEFAULT 'UNKNOWN'
        CHECK (vertical_datum IN ('IMBITUBA', 'SANTANA', 'ELLIPSOIDAL', 'UNKNOWN')),
    captured_on DATE,
    responsible_party_id UUID,
    source TEXT CHECK (source IN
        ('PROJECT', 'AS_BUILT', 'SERVICE_ORDER', 'FIELD', 'IMPORT', 'DIGITIZATION', 'AUTO')),
    source_batch_id UUID,
    source_ref TEXT,
    legacy_object_id TEXT,
    accuracy_status TEXT CHECK (accuracy_status IN
        ('WITHIN_TARGET', 'OUT_OF_TARGET', 'APPROXIMATE', 'UNKNOWN')),
    asset_codes JSONB,
    dma_id UUID,
    zone_type TEXT NOT NULL CHECK (zone_type IN
        ('SUPPLY_SYSTEM', 'PRESSURE_ZONE', 'DMA', 'MANEUVER_SECTOR', 'SEWER_BASIN',
         'SEWER_SUBBASIN', 'STORM_BASIN', 'STORM_SUBCATCHMENT', 'COLLECTION_SECTOR',
         'COMMERCIAL_SECTOR', 'OPERATIONAL_SECTOR')),
    derivation TEXT NOT NULL DEFAULT 'DRAWN' CHECK (derivation IN ('DRAWN', 'TRACED')),
    dma_district_id UUID,
    commercial_sector_id UUID,
    geom geometry(MultiPolygon, 4674) NOT NULL,
    geom_web geometry(Geometry, 3857),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_zone_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_zone_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_gis_zone_retired_reason
        CHECK ((lifecycle_status = 'ABANDONED' AND retired_reason IS NOT NULL)
               OR (lifecycle_status <> 'ABANDONED' AND retired_reason IS NULL)),
    CONSTRAINT fk_gis_zone_type
        FOREIGN KEY (tenant_id, feature_type_id) REFERENCES gis_feature_type (tenant_id, id),
    CONSTRAINT fk_gis_zone_item
        FOREIGN KEY (tenant_id, catalog_item_id) REFERENCES gis_catalog_item (tenant_id, id),
    CONSTRAINT fk_gis_zone_responsible
        FOREIGN KEY (tenant_id, responsible_party_id) REFERENCES gis_responsible_party (tenant_id, id),
    CONSTRAINT fk_gis_zone_dma
        FOREIGN KEY (tenant_id, dma_id) REFERENCES dma_district (tenant_id, id),
    CONSTRAINT fk_gis_zone_dma_district
        FOREIGN KEY (tenant_id, dma_district_id) REFERENCES dma_district (tenant_id, id)
    -- commercial_sector_id sem FK: o setor comercial vive em commercial_sectors
    -- (registry, V3_1) e o vinculo com FK chega na GIS-05, junto de service_connections.
);

CREATE TABLE IF NOT EXISTS gis_poi (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code TEXT NOT NULL,
    feature_type_id UUID NOT NULL,
    catalog_item_id UUID,
    name TEXT,
    lifecycle_status TEXT NOT NULL DEFAULT 'PLANNED'
        CHECK (lifecycle_status IN ('PLANNED', 'UNDER_CONSTRUCTION', 'IN_SERVICE', 'ABANDONED')),
    retired_reason TEXT CHECK (retired_reason IN ('ABANDONED_IN_PLACE', 'REMOVED')),
    operational_status TEXT,
    normal_status TEXT,
    condition_rating TEXT CHECK (condition_rating IN ('GOOD', 'FAIR', 'POOR', 'CRITICAL', 'UNKNOWN')),
    installed_on DATE,
    installed_on_precision TEXT CHECK (installed_on_precision IN ('DAY', 'MONTH', 'YEAR', 'UNKNOWN')),
    abandoned_on DATE,
    change_set_id UUID,
    attributes JSONB NOT NULL DEFAULT '{}',
    schema_version INT NOT NULL DEFAULT 1,
    capture_method TEXT NOT NULL CHECK (capture_method IN
        ('RTK_FIXED', 'RTK_FLOAT', 'TOTAL_STATION', 'GNSS_NAVIGATION', 'TAPE_TIE',
         'DIGITIZED', 'CAD_IMPORT', 'AUTO_SERVICE_LINE', 'DESIGN', 'ESTIMATED')),
    h_accuracy_m NUMERIC,
    v_accuracy_m NUMERIC,
    vertical_datum TEXT NOT NULL DEFAULT 'UNKNOWN'
        CHECK (vertical_datum IN ('IMBITUBA', 'SANTANA', 'ELLIPSOIDAL', 'UNKNOWN')),
    captured_on DATE,
    responsible_party_id UUID,
    source TEXT CHECK (source IN
        ('PROJECT', 'AS_BUILT', 'SERVICE_ORDER', 'FIELD', 'IMPORT', 'DIGITIZATION', 'AUTO')),
    source_batch_id UUID,
    source_ref TEXT,
    legacy_object_id TEXT,
    accuracy_status TEXT CHECK (accuracy_status IN
        ('WITHIN_TARGET', 'OUT_OF_TARGET', 'APPROXIMATE', 'UNKNOWN')),
    asset_codes JSONB,
    dma_id UUID,
    geom geometry(Point, 4674) NOT NULL,
    geom_web geometry(Geometry, 3857),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_poi_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_poi_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_gis_poi_retired_reason
        CHECK ((lifecycle_status = 'ABANDONED' AND retired_reason IS NOT NULL)
               OR (lifecycle_status <> 'ABANDONED' AND retired_reason IS NULL)),
    CONSTRAINT fk_gis_poi_type
        FOREIGN KEY (tenant_id, feature_type_id) REFERENCES gis_feature_type (tenant_id, id),
    CONSTRAINT fk_gis_poi_item
        FOREIGN KEY (tenant_id, catalog_item_id) REFERENCES gis_catalog_item (tenant_id, id),
    CONSTRAINT fk_gis_poi_responsible
        FOREIGN KEY (tenant_id, responsible_party_id) REFERENCES gis_responsible_party (tenant_id, id),
    CONSTRAINT fk_gis_poi_dma
        FOREIGN KEY (tenant_id, dma_id) REFERENCES dma_district (tenant_id, id)
);

-- gis_attachment nao e feicao com code: foto, croqui, documento ou monografia
-- ligada a uma feicao, com posicao e azimute opcionais (§3.4).

CREATE TABLE IF NOT EXISTS gis_attachment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    target_feature_table TEXT NOT NULL,
    target_feature_id UUID NOT NULL,
    attachment_type TEXT NOT NULL,
    document_id UUID,
    description TEXT,
    geom geometry(Point, 4674),
    azimuth_deg NUMERIC,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_attachment_tenant_id UNIQUE (tenant_id, id)
);

CREATE TABLE IF NOT EXISTS gis_containment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    container_table TEXT NOT NULL,
    container_id UUID NOT NULL,
    contained_table TEXT NOT NULL,
    contained_id UUID NOT NULL,
    relation TEXT NOT NULL CHECK (relation IN ('CONTAINS', 'ATTACHES')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_containment_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_containment_link UNIQUE
        (tenant_id, container_table, container_id, contained_table, contained_id, relation)
);

-- Feicoes SEM trigger de auditoria (p_audit => false), por desenho — e nao por
-- esquecimento. A trilha de auditoria das feicoes SAO as tabelas gis_*_history
-- (estado anterior completo, valid_period, autor, papel e canal), mais ricas que
-- uma linha em audit_log. E o trigger de auditoria e TECNICAMENTE incompativel
-- com estas tabelas: audit_trigger_function faz to_jsonb(NEW), e a geometria
-- vira GeoJSON pelo cast geometry->json — mas Z desconhecido e NaN (nunca 0
-- inventado), e NaN nao e token JSON valido. Prova da IT GIS-01: INSERT de no
-- com POINTZ NaN falha com "invalid input syntax for type json", token NaN, no
-- audit_trigger_function. Como TODA feicao migrada do legado nasce com Z NaN (o
-- legado e 2D), a propria migracao nao rodaria com o trigger ligado. Anexos,
-- contencao e nao-mapeadas mantem o trigger (geometria 2D ou nenhuma).
SELECT register_tenant_table('gis_node', p_audit => false);
SELECT register_tenant_table('gis_arc', p_audit => false);
SELECT register_tenant_table('gis_connection', p_audit => false);
SELECT register_tenant_table('gis_structure', p_audit => false);
SELECT register_tenant_table('gis_zone', p_audit => false);
SELECT register_tenant_table('gis_poi', p_audit => false);
SELECT register_tenant_table('gis_attachment');
SELECT register_tenant_table('gis_containment');

CREATE INDEX IF NOT EXISTS idx_gis_node_geom ON gis_node USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_gis_arc_geom ON gis_arc USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_gis_connection_geom ON gis_connection USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_gis_structure_geom ON gis_structure USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_gis_structure_geom_point ON gis_structure USING GIST (geom_point);
CREATE INDEX IF NOT EXISTS idx_gis_zone_geom ON gis_zone USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_gis_poi_geom ON gis_poi USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_gis_node_type ON gis_node (tenant_id, feature_type_id);
CREATE INDEX IF NOT EXISTS idx_gis_arc_type ON gis_arc (tenant_id, feature_type_id);
CREATE INDEX IF NOT EXISTS idx_gis_connection_type ON gis_connection (tenant_id, feature_type_id);
CREATE INDEX IF NOT EXISTS idx_gis_structure_type ON gis_structure (tenant_id, feature_type_id);
CREATE INDEX IF NOT EXISTS idx_gis_zone_type ON gis_zone (tenant_id, feature_type_id);
CREATE INDEX IF NOT EXISTS idx_gis_poi_type ON gis_poi (tenant_id, feature_type_id);

-- ======================================================================
-- Historico (§3.7): uma tabela por feicao, com a versao anterior completa e
-- valid_period tstzrange. A ORDEM das colunas replica a da tabela da feicao
-- (incluindo version e tenant_schema, acrescentados pelo register_tenant_table)
-- mais valid_period, history_author, history_role e history_channel no fim: o
-- gatilho insere com SELECT (OLD).* posicional. Sem sobreposicao, por EXCLUDE
-- USING gist com btree_gist (o PostgreSQL 17 nao tem WITHOUT OVERLAPS).
-- Sem auditoria por linha e sem heranca de BaseEntity, como as series do V2_3:
-- o historico JA E o registro; audita-lo duplicaria cada linha em JSONB.
-- ======================================================================

CREATE TABLE IF NOT EXISTS gis_node_history (
    id UUID NOT NULL, tenant_id UUID NOT NULL, code TEXT NOT NULL,
    feature_type_id UUID NOT NULL, catalog_item_id UUID, name TEXT,
    lifecycle_status TEXT NOT NULL, retired_reason TEXT, operational_status TEXT,
    normal_status TEXT, condition_rating TEXT, installed_on DATE,
    installed_on_precision TEXT, abandoned_on DATE, change_set_id UUID,
    attributes JSONB NOT NULL, schema_version INT NOT NULL, capture_method TEXT NOT NULL,
    h_accuracy_m NUMERIC, v_accuracy_m NUMERIC, vertical_datum TEXT NOT NULL,
    captured_on DATE, responsible_party_id UUID, source TEXT, source_batch_id UUID,
    source_ref TEXT, legacy_object_id TEXT, accuracy_status TEXT, asset_codes JSONB,
    dma_id UUID, ground_elevation_m NUMERIC, rim_elevation_m NUMERIC,
    invert_elevation_m NUMERIC, depth_m NUMERIC, symbol_rotation_deg NUMERIC,
    pressure_zone TEXT, setting NUMERIC, geom geometry(PointZ, 4674),
    geom_web geometry(Geometry, 3857), created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL, created_by UUID, updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0, tenant_schema VARCHAR(100),
    valid_period tstzrange NOT NULL, history_author UUID, history_role TEXT,
    history_channel TEXT
);

CREATE TABLE IF NOT EXISTS gis_arc_history (
    id UUID NOT NULL, tenant_id UUID NOT NULL, code TEXT NOT NULL,
    feature_type_id UUID NOT NULL, catalog_item_id UUID, name TEXT,
    lifecycle_status TEXT NOT NULL, retired_reason TEXT, operational_status TEXT,
    normal_status TEXT, condition_rating TEXT, installed_on DATE,
    installed_on_precision TEXT, abandoned_on DATE, change_set_id UUID,
    attributes JSONB NOT NULL, schema_version INT NOT NULL, capture_method TEXT NOT NULL,
    h_accuracy_m NUMERIC, v_accuracy_m NUMERIC, vertical_datum TEXT NOT NULL,
    captured_on DATE, responsible_party_id UUID, source TEXT, source_batch_id UUID,
    source_ref TEXT, legacy_object_id TEXT, accuracy_status TEXT, asset_codes JSONB,
    dma_id UUID, node_1_id UUID, node_2_id UUID, length_m NUMERIC,
    declared_length_m NUMERIC, upstream_invert_m NUMERIC, downstream_invert_m NUMERIC,
    slope NUMERIC, pressurized BOOLEAN, flow_direction TEXT, network_function TEXT,
    depth_m NUMERIC, street_side TEXT, dist_to_property_line_m NUMERIC,
    dist_to_curb_m NUMERIC, pavement_type TEXT, geom geometry(LineStringZ, 4674),
    geom_web geometry(Geometry, 3857), created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL, created_by UUID, updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0, tenant_schema VARCHAR(100),
    valid_period tstzrange NOT NULL, history_author UUID, history_role TEXT,
    history_channel TEXT
);

CREATE TABLE IF NOT EXISTS gis_connection_history (
    id UUID NOT NULL, tenant_id UUID NOT NULL, code TEXT NOT NULL,
    feature_type_id UUID NOT NULL, catalog_item_id UUID, name TEXT,
    lifecycle_status TEXT NOT NULL, retired_reason TEXT, operational_status TEXT,
    normal_status TEXT, condition_rating TEXT, installed_on DATE,
    installed_on_precision TEXT, abandoned_on DATE, change_set_id UUID,
    attributes JSONB NOT NULL, schema_version INT NOT NULL, capture_method TEXT NOT NULL,
    h_accuracy_m NUMERIC, v_accuracy_m NUMERIC, vertical_datum TEXT NOT NULL,
    captured_on DATE, responsible_party_id UUID, source TEXT, source_batch_id UUID,
    source_ref TEXT, legacy_object_id TEXT, accuracy_status TEXT, asset_codes JSONB,
    dma_id UUID, arc_id UUID, tap_node_id UUID, service_connection_id UUID,
    endpoint_type TEXT, length_m NUMERIC, depth_m NUMERIC,
    dist_to_property_line_m NUMERIC, dist_to_curb_m NUMERIC,
    geom geometry(LineStringZ, 4674), geom_web geometry(Geometry, 3857),
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID, updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0, tenant_schema VARCHAR(100),
    valid_period tstzrange NOT NULL, history_author UUID, history_role TEXT,
    history_channel TEXT
);

CREATE TABLE IF NOT EXISTS gis_structure_history (
    id UUID NOT NULL, tenant_id UUID NOT NULL, code TEXT NOT NULL,
    feature_type_id UUID NOT NULL, catalog_item_id UUID, name TEXT,
    lifecycle_status TEXT NOT NULL, retired_reason TEXT, operational_status TEXT,
    normal_status TEXT, condition_rating TEXT, installed_on DATE,
    installed_on_precision TEXT, abandoned_on DATE, change_set_id UUID,
    attributes JSONB NOT NULL, schema_version INT NOT NULL, capture_method TEXT NOT NULL,
    h_accuracy_m NUMERIC, v_accuracy_m NUMERIC, vertical_datum TEXT NOT NULL,
    captured_on DATE, responsible_party_id UUID, source TEXT, source_batch_id UUID,
    source_ref TEXT, legacy_object_id TEXT, accuracy_status TEXT, asset_codes JSONB,
    dma_id UUID, volume_m3 NUMERIC, nominal_flow_lps NUMERIC, power_kw NUMERIC,
    head_m NUMERIC, geom geometry(MultiPolygon, 4674),
    geom_point geometry(Point, 4674), geom_web geometry(Geometry, 3857),
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID, updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0, tenant_schema VARCHAR(100),
    valid_period tstzrange NOT NULL, history_author UUID, history_role TEXT,
    history_channel TEXT
);

CREATE TABLE IF NOT EXISTS gis_zone_history (
    id UUID NOT NULL, tenant_id UUID NOT NULL, code TEXT NOT NULL,
    feature_type_id UUID NOT NULL, catalog_item_id UUID, name TEXT,
    lifecycle_status TEXT NOT NULL, retired_reason TEXT, operational_status TEXT,
    normal_status TEXT, condition_rating TEXT, installed_on DATE,
    installed_on_precision TEXT, abandoned_on DATE, change_set_id UUID,
    attributes JSONB NOT NULL, schema_version INT NOT NULL, capture_method TEXT NOT NULL,
    h_accuracy_m NUMERIC, v_accuracy_m NUMERIC, vertical_datum TEXT NOT NULL,
    captured_on DATE, responsible_party_id UUID, source TEXT, source_batch_id UUID,
    source_ref TEXT, legacy_object_id TEXT, accuracy_status TEXT, asset_codes JSONB,
    dma_id UUID, zone_type TEXT NOT NULL, derivation TEXT NOT NULL,
    dma_district_id UUID, commercial_sector_id UUID,
    geom geometry(MultiPolygon, 4674) NOT NULL, geom_web geometry(Geometry, 3857),
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID, updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0, tenant_schema VARCHAR(100),
    valid_period tstzrange NOT NULL, history_author UUID, history_role TEXT,
    history_channel TEXT
);

CREATE TABLE IF NOT EXISTS gis_poi_history (
    id UUID NOT NULL, tenant_id UUID NOT NULL, code TEXT NOT NULL,
    feature_type_id UUID NOT NULL, catalog_item_id UUID, name TEXT,
    lifecycle_status TEXT NOT NULL, retired_reason TEXT, operational_status TEXT,
    normal_status TEXT, condition_rating TEXT, installed_on DATE,
    installed_on_precision TEXT, abandoned_on DATE, change_set_id UUID,
    attributes JSONB NOT NULL, schema_version INT NOT NULL, capture_method TEXT NOT NULL,
    h_accuracy_m NUMERIC, v_accuracy_m NUMERIC, vertical_datum TEXT NOT NULL,
    captured_on DATE, responsible_party_id UUID, source TEXT, source_batch_id UUID,
    source_ref TEXT, legacy_object_id TEXT, accuracy_status TEXT, asset_codes JSONB,
    dma_id UUID, geom geometry(Point, 4674) NOT NULL,
    geom_web geometry(Geometry, 3857),
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID, updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0, tenant_schema VARCHAR(100),
    valid_period tstzrange NOT NULL, history_author UUID, history_role TEXT,
    history_channel TEXT
);

SELECT register_tenant_table('gis_node_history', p_entity => false, p_audit => false);
SELECT register_tenant_table('gis_arc_history', p_entity => false, p_audit => false);
SELECT register_tenant_table('gis_connection_history', p_entity => false, p_audit => false);
SELECT register_tenant_table('gis_structure_history', p_entity => false, p_audit => false);
SELECT register_tenant_table('gis_zone_history', p_entity => false, p_audit => false);
SELECT register_tenant_table('gis_poi_history', p_entity => false, p_audit => false);

ALTER TABLE gis_node_history
    ADD CONSTRAINT excl_gis_node_history_no_overlap
    EXCLUDE USING gist (id WITH =, valid_period WITH &&);
ALTER TABLE gis_arc_history
    ADD CONSTRAINT excl_gis_arc_history_no_overlap
    EXCLUDE USING gist (id WITH =, valid_period WITH &&);
ALTER TABLE gis_connection_history
    ADD CONSTRAINT excl_gis_connection_history_no_overlap
    EXCLUDE USING gist (id WITH =, valid_period WITH &&);
ALTER TABLE gis_structure_history
    ADD CONSTRAINT excl_gis_structure_history_no_overlap
    EXCLUDE USING gist (id WITH =, valid_period WITH &&);
ALTER TABLE gis_zone_history
    ADD CONSTRAINT excl_gis_zone_history_no_overlap
    EXCLUDE USING gist (id WITH =, valid_period WITH &&);
ALTER TABLE gis_poi_history
    ADD CONSTRAINT excl_gis_poi_history_no_overlap
    EXCLUDE USING gist (id WITH =, valid_period WITH &&);

CREATE INDEX IF NOT EXISTS idx_gis_node_history_lookup ON gis_node_history (tenant_id, id, valid_period);
CREATE INDEX IF NOT EXISTS idx_gis_arc_history_lookup ON gis_arc_history (tenant_id, id, valid_period);
CREATE INDEX IF NOT EXISTS idx_gis_connection_history_lookup ON gis_connection_history (tenant_id, id, valid_period);
CREATE INDEX IF NOT EXISTS idx_gis_structure_history_lookup ON gis_structure_history (tenant_id, id, valid_period);
CREATE INDEX IF NOT EXISTS idx_gis_zone_history_lookup ON gis_zone_history (tenant_id, id, valid_period);
CREATE INDEX IF NOT EXISTS idx_gis_poi_history_lookup ON gis_poi_history (tenant_id, id, valid_period);

-- CHECK de ciclo de vida que faltou em gis_arc e gis_connection na criacao.
ALTER TABLE gis_arc ADD CONSTRAINT ck_gis_arc_retired_reason
    CHECK ((lifecycle_status = 'ABANDONED' AND retired_reason IS NOT NULL)
           OR (lifecycle_status <> 'ABANDONED' AND retired_reason IS NULL));
ALTER TABLE gis_connection ADD CONSTRAINT ck_gis_connection_retired_reason
    CHECK ((lifecycle_status = 'ABANDONED' AND retired_reason IS NOT NULL)
           OR (lifecycle_status <> 'ABANDONED' AND retired_reason IS NULL));

-- ======================================================================
-- Gatilhos
-- ======================================================================

-- Validacao da feicao antes de gravar: JSON Schema do tipo (V13_1), item de
-- catalogo do mesmo tipo, item obrigatorio quando o tipo exige, e accuracy_status
-- pela regra do §3.8 (gravado por gatilho, nao pela aplicacao). A meta vigente da
-- classe vem de gis_quality_target; sem meta cadastrada, vale o padrao de fabrica
-- da decisao 5 (±0,10 m H e ±0,05 m V). Transicoes de ciclo de vida ficam para a
-- GIS-04 (P2.22), que e dona da maquina de estados; aqui so o dominio e validado.
CREATE OR REPLACE FUNCTION gis_check_feature() RETURNS TRIGGER AS $$
DECLARE
    v_item_type_id UUID;
    v_requires_item BOOLEAN;
    v_class_id UUID;
    v_h_target NUMERIC;
    v_v_target NUMERIC;
BEGIN
    IF NEW.attributes IS NULL THEN
        NEW.attributes := '{}'::jsonb;
    END IF;
    PERFORM gis_validate_attributes(NEW.feature_type_id, NEW.tenant_id, NEW.attributes);

    IF NEW.catalog_item_id IS NOT NULL THEN
        SELECT feature_type_id INTO v_item_type_id
        FROM gis_catalog_item
        WHERE id = NEW.catalog_item_id AND tenant_id = NEW.tenant_id;
        IF v_item_type_id IS NULL THEN
            RAISE EXCEPTION 'gis: item de catalogo % nao encontrado no tenant', NEW.catalog_item_id;
        ELSIF v_item_type_id <> NEW.feature_type_id THEN
            RAISE EXCEPTION 'gis: item de catalogo % e de outro tipo de feicao', NEW.catalog_item_id;
        END IF;
    END IF;

    SELECT requires_catalog_item, class_id INTO v_requires_item, v_class_id
    FROM gis_feature_type
    WHERE id = NEW.feature_type_id AND tenant_id = NEW.tenant_id;
    IF v_requires_item AND NEW.catalog_item_id IS NULL THEN
        RAISE EXCEPTION 'gis: tipo de feicao exige item de catalogo';
    END IF;

    IF NEW.capture_method IN ('DIGITIZED', 'AUTO_SERVICE_LINE', 'DESIGN', 'ESTIMATED') THEN
        NEW.accuracy_status := 'APPROXIMATE';
    ELSIF NEW.capture_method = 'CAD_IMPORT' AND NEW.h_accuracy_m IS NULL THEN
        NEW.accuracy_status := 'APPROXIMATE';
    ELSIF NEW.h_accuracy_m IS NOT NULL OR NEW.v_accuracy_m IS NOT NULL THEN
        SELECT h_target_m, v_target_m INTO v_h_target, v_v_target
        FROM gis_quality_target
        WHERE tenant_id = NEW.tenant_id AND class_id = v_class_id
          AND valid_from <= CURRENT_DATE
          AND (valid_to IS NULL OR valid_to >= CURRENT_DATE)
        ORDER BY valid_from DESC LIMIT 1;
        v_h_target := COALESCE(v_h_target, 0.10);
        v_v_target := COALESCE(v_v_target, 0.05);
        IF (NEW.h_accuracy_m IS NULL OR NEW.h_accuracy_m <= v_h_target)
           AND (NEW.v_accuracy_m IS NULL OR NEW.v_accuracy_m <= v_v_target) THEN
            NEW.accuracy_status := 'WITHIN_TARGET';
        ELSE
            NEW.accuracy_status := 'OUT_OF_TARGET';
        END IF;
    ELSE
        NEW.accuracy_status := 'UNKNOWN';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- geom_web em 3857: ST_Transform e STABLE, nao IMMUTABLE, por isso nao pode ser
-- coluna gerada (previsao do proprio §3.5); vai por gatilho.
CREATE OR REPLACE FUNCTION gis_maintain_geom_web() RETURNS TRIGGER AS $$
BEGIN
    -- geom_web e 2D (mapa web) e NEW.geom e Z: ST_Transform preserva o Z, entao
    -- sem o Force2D a atribuicao falha com "Geometry has Z dimension but
    -- column does not" em todo INSERT de no/arco/ramal (achado da IT GIS-01).
    NEW.geom_web := ST_Force2D(ST_Transform(NEW.geom, 3857));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION gis_maintain_geom_web_structure() RETURNS TRIGGER AS $$
BEGIN
    -- Mesmo Force2D da gis_maintain_geom_web (aqui o Z so chegaria via
    -- geom_point, que e 2D, mas o custo de garantir e zero).
    NEW.geom_web := ST_Force2D(ST_Transform(COALESCE(NEW.geom, NEW.geom_point), 3857));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Historico: grava a versao anterior com valid_period [inicio, clock_timestamp()).
-- clock_timestamp(), nao now(): now() e o inicio da transacao, entao uma feicao
-- criada e atualizada na mesma transacao ganhava periodo VAZIO [T0, T0) — a
-- consulta por data nao devolvia o estado antigo (achado da IT GIS-01: os dois
-- periodos do teste de historico sairam 'empty'). O inicio e
-- o fim da versao anterior, ou o created_at na primeira alteracao. O gatilho insere
-- posicional (OLD).* mais os quatro extras, por isso a ordem das colunas do
-- historico replica a da feicao (ver o cabecalho da secao de historico).
CREATE OR REPLACE FUNCTION gis_write_history() RETURNS TRIGGER AS $$
DECLARE
    v_hist TEXT := TG_TABLE_NAME || '_history';
    v_lower TIMESTAMPTZ;
    v_channel TEXT;
BEGIN
    EXECUTE format('SELECT max(upper(valid_period)) FROM %I WHERE id = $1', v_hist)
        INTO v_lower USING OLD.id;
    IF v_lower IS NULL THEN
        v_lower := OLD.created_at;
    END IF;
    v_channel := COALESCE(NULLIF(current_setting('app.channel', true), ''), 'SQL_DIRECT');
    EXECUTE format('INSERT INTO %I SELECT ($1).*, $2, $3, $4, $5', v_hist)
        USING OLD, tstzrange(v_lower, clock_timestamp()),
              COALESCE(OLD.updated_by, OLD.created_by), session_user, v_channel;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Gate de pacote em gis_feature_class: classe criada pelo tenant (origin TENANT)
-- exige o pacote do dominio ativo em platform.tenant_vertical. Linhas FACTORY sao
-- o proprio conteudo do pacote (carregadas pela migration e, na P1.27, pela R__ do
-- catalogo) e nao passam pelo gate; a visibilidade continua pela gis_domain_visible.
-- Se platform.tenant_vertical nao existir (core anterior a V6_1), o gate nao trava:
-- a tabela base e a view continuam valendo e o gate passa a valer quando a tabela
-- existir. Dominio inexistente continua recusado pela FK, antes de chegar aqui.
CREATE OR REPLACE FUNCTION gis_check_class_package() RETURNS TRIGGER AS $$
DECLARE
    v_package TEXT;
    v_found BOOLEAN;
BEGIN
    IF NEW.origin = 'TENANT' THEN
        SELECT package INTO v_package FROM gis_domain
        WHERE tenant_id = NEW.tenant_id AND key = NEW.domain;
        IF v_package IS NOT NULL AND to_regclass('platform.tenant_vertical') IS NOT NULL THEN
            EXECUTE 'SELECT EXISTS (SELECT 1 FROM platform.tenant_vertical '
                 || 'WHERE tenant_id = $1 AND vertical_id = $2 AND state = ''ACTIVE'')'
                INTO v_found USING NEW.tenant_id, v_package;
            IF NOT v_found THEN
                RAISE EXCEPTION 'gis: pacote % do dominio % nao ativo no tenant', v_package, NEW.domain;
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS gis_check_trigger ON gis_node;
DROP TRIGGER IF EXISTS gis_check_trigger ON gis_arc;
DROP TRIGGER IF EXISTS gis_check_trigger ON gis_connection;
DROP TRIGGER IF EXISTS gis_check_trigger ON gis_structure;
DROP TRIGGER IF EXISTS gis_check_trigger ON gis_zone;
DROP TRIGGER IF EXISTS gis_check_trigger ON gis_poi;
CREATE TRIGGER gis_check_trigger BEFORE INSERT OR UPDATE ON gis_node
    FOR EACH ROW EXECUTE FUNCTION gis_check_feature();
CREATE TRIGGER gis_check_trigger BEFORE INSERT OR UPDATE ON gis_arc
    FOR EACH ROW EXECUTE FUNCTION gis_check_feature();
CREATE TRIGGER gis_check_trigger BEFORE INSERT OR UPDATE ON gis_connection
    FOR EACH ROW EXECUTE FUNCTION gis_check_feature();
CREATE TRIGGER gis_check_trigger BEFORE INSERT OR UPDATE ON gis_structure
    FOR EACH ROW EXECUTE FUNCTION gis_check_feature();
CREATE TRIGGER gis_check_trigger BEFORE INSERT OR UPDATE ON gis_zone
    FOR EACH ROW EXECUTE FUNCTION gis_check_feature();
CREATE TRIGGER gis_check_trigger BEFORE INSERT OR UPDATE ON gis_poi
    FOR EACH ROW EXECUTE FUNCTION gis_check_feature();

DROP TRIGGER IF EXISTS gis_geom_web_trigger ON gis_node;
DROP TRIGGER IF EXISTS gis_geom_web_trigger ON gis_arc;
DROP TRIGGER IF EXISTS gis_geom_web_trigger ON gis_connection;
DROP TRIGGER IF EXISTS gis_geom_web_trigger ON gis_zone;
DROP TRIGGER IF EXISTS gis_geom_web_trigger ON gis_poi;
DROP TRIGGER IF EXISTS gis_geom_web_trigger ON gis_structure;
CREATE TRIGGER gis_geom_web_trigger BEFORE INSERT OR UPDATE OF geom ON gis_node
    FOR EACH ROW EXECUTE FUNCTION gis_maintain_geom_web();
CREATE TRIGGER gis_geom_web_trigger BEFORE INSERT OR UPDATE OF geom ON gis_arc
    FOR EACH ROW EXECUTE FUNCTION gis_maintain_geom_web();
CREATE TRIGGER gis_geom_web_trigger BEFORE INSERT OR UPDATE OF geom ON gis_connection
    FOR EACH ROW EXECUTE FUNCTION gis_maintain_geom_web();
CREATE TRIGGER gis_geom_web_trigger BEFORE INSERT OR UPDATE OF geom ON gis_zone
    FOR EACH ROW EXECUTE FUNCTION gis_maintain_geom_web();
CREATE TRIGGER gis_geom_web_trigger BEFORE INSERT OR UPDATE OF geom ON gis_poi
    FOR EACH ROW EXECUTE FUNCTION gis_maintain_geom_web();
CREATE TRIGGER gis_geom_web_trigger BEFORE INSERT OR UPDATE OF geom, geom_point ON gis_structure
    FOR EACH ROW EXECUTE FUNCTION gis_maintain_geom_web_structure();

DROP TRIGGER IF EXISTS gis_history_trigger ON gis_node;
DROP TRIGGER IF EXISTS gis_history_trigger ON gis_arc;
DROP TRIGGER IF EXISTS gis_history_trigger ON gis_connection;
DROP TRIGGER IF EXISTS gis_history_trigger ON gis_structure;
DROP TRIGGER IF EXISTS gis_history_trigger ON gis_zone;
DROP TRIGGER IF EXISTS gis_history_trigger ON gis_poi;
CREATE TRIGGER gis_history_trigger AFTER UPDATE OR DELETE ON gis_node
    FOR EACH ROW EXECUTE FUNCTION gis_write_history();
CREATE TRIGGER gis_history_trigger AFTER UPDATE OR DELETE ON gis_arc
    FOR EACH ROW EXECUTE FUNCTION gis_write_history();
CREATE TRIGGER gis_history_trigger AFTER UPDATE OR DELETE ON gis_connection
    FOR EACH ROW EXECUTE FUNCTION gis_write_history();
CREATE TRIGGER gis_history_trigger AFTER UPDATE OR DELETE ON gis_structure
    FOR EACH ROW EXECUTE FUNCTION gis_write_history();
CREATE TRIGGER gis_history_trigger AFTER UPDATE OR DELETE ON gis_zone
    FOR EACH ROW EXECUTE FUNCTION gis_write_history();
CREATE TRIGGER gis_history_trigger AFTER UPDATE OR DELETE ON gis_poi
    FOR EACH ROW EXECUTE FUNCTION gis_write_history();

DROP TRIGGER IF EXISTS gis_class_package_trigger ON gis_feature_class;
CREATE TRIGGER gis_class_package_trigger BEFORE INSERT OR UPDATE ON gis_feature_class
    FOR EACH ROW EXECUTE FUNCTION gis_check_class_package();

-- ======================================================================
-- Rede na data (mecanismo; a rota /api/v1/gis/network-at e da GIS-04, P2.22).
-- Devolve a versao da feicao vigente em p_at: a linha do historico cujo periodo
-- contem p_at, ou a linha atual quando ela ja existia em p_at e ainda nao foi
-- substituida. Feicao apagada antes de p_at nao devolve nada (o periodo da ultima
-- versao termina na exclusao).
-- ======================================================================

CREATE OR REPLACE FUNCTION gis_feature_at(
    p_table TEXT, p_feature_id UUID, p_at TIMESTAMPTZ
) RETURNS JSONB AS $$
DECLARE
    v_hist TEXT;
    v_out JSONB;
BEGIN
    v_hist := CASE p_table
        WHEN 'gis_node' THEN 'gis_node_history'
        WHEN 'gis_arc' THEN 'gis_arc_history'
        WHEN 'gis_connection' THEN 'gis_connection_history'
        WHEN 'gis_structure' THEN 'gis_structure_history'
        WHEN 'gis_zone' THEN 'gis_zone_history'
        WHEN 'gis_poi' THEN 'gis_poi_history'
        ELSE NULL END;
    IF v_hist IS NULL THEN
        RAISE EXCEPTION 'gis: tabela de feicao desconhecida: %', p_table;
    END IF;
    EXECUTE format('SELECT to_jsonb(h) FROM %I h WHERE h.id = $1 AND h.valid_period @> $2 '
                || 'ORDER BY lower(h.valid_period) DESC LIMIT 1', v_hist)
        INTO v_out USING p_feature_id, p_at;
    IF v_out IS NOT NULL THEN
        RETURN v_out;
    END IF;
    EXECUTE format('SELECT to_jsonb(f) FROM %I f WHERE f.id = $1 AND f.created_at <= $2', p_table)
        INTO v_out USING p_feature_id, p_at;
    RETURN v_out;
END;
$$ LANGUAGE plpgsql;

-- ======================================================================
-- Fim do UNIQUE global de code em dma_district e leak_detection.
-- Os dois ja tinham indice unico (tenant_id, code) desde a V2_2; o que travava o
-- mesmo code em dois tenants era o UNIQUE de coluna (constraint gerada
-- {tabela}_{coluna}_key). Cair a constraint global e manter o indice por tenant
-- e exatamente o que a P1.26 pede. O UNIQUE (tenant_id, code) de dma_district ja
-- existia (idx_dma_tenant_code); o de leak_detection ja existia
-- (idx_leak_code, sobre tenant_id e detection_code, o code da tabela).
-- ======================================================================

ALTER TABLE dma_district DROP CONSTRAINT IF EXISTS dma_district_code_key;
ALTER TABLE leak_detection DROP CONSTRAINT IF EXISTS leak_detection_detection_code_key;

-- ======================================================================
-- Seed das classes e tipos do mapeamento §2.4 (so o necessario a conversao).
-- origin FACTORY, factory_code 'gis01:<CODE>': a R__ do catalogo da P1.27 faz
-- upsert pela mesma chave estavel, sem duplicar estas linhas. NENHUM item aqui
-- cita norma: as normas da P1.28 nao foram compradas (sem aprovacao do dono),
-- entao todo item derivado do legado nasce REFERENCE_TO_VALIDATE (fallback do
-- proprio plano). requires_catalog_item fica falso nestes seeds: a obrigatoriedade
-- por tipo e curadoria da P1.27, nao da migracao (o legado nem sempre tem DN).
-- Tensao registrada do §2.4: a classe PIPE (e VALVE, FITTING, JUNCTION) e
-- compartilhada por agua e esgoto na tabela do plano, embora a classe carregue um
-- so dominio; o seed segue a tabela literalmente (dominio WATER) e a separacao
-- por dominio, se a curadoria quiser, e dado (nova classe), nao migration.
-- ======================================================================

INSERT INTO gis_feature_class (tenant_id, code, domain, topology_role, geometry_type, origin, active)
VALUES
    ('${tenant_id}', 'PIPE', 'WATER', 'ARC', 'LineStringZ', 'FACTORY', TRUE),
    ('${tenant_id}', 'VALVE', 'WATER', 'NODE', 'PointZ', 'FACTORY', TRUE),
    ('${tenant_id}', 'HYDRANT', 'WATER', 'NODE', 'PointZ', 'FACTORY', TRUE),
    ('${tenant_id}', 'METER_STATION', 'WATER', 'NODE', 'PointZ', 'FACTORY', TRUE),
    ('${tenant_id}', 'STORAGE', 'WATER', 'STRUCTURE', 'MultiPolygon', 'FACTORY', TRUE),
    ('${tenant_id}', 'PUMP_STATION', 'WATER', 'STRUCTURE', 'MultiPolygon', 'FACTORY', TRUE),
    ('${tenant_id}', 'TREATMENT_PLANT', 'WATER', 'STRUCTURE', 'MultiPolygon', 'FACTORY', TRUE),
    ('${tenant_id}', 'SOURCE', 'WATER', 'STRUCTURE', 'MultiPolygon', 'FACTORY', TRUE),
    ('${tenant_id}', 'SERVICE_LINE', 'WATER', 'CONNECTION', 'LineStringZ', 'FACTORY', TRUE),
    ('${tenant_id}', 'MANHOLE', 'SEWER', 'NODE', 'PointZ', 'FACTORY', TRUE),
    ('${tenant_id}', 'BOX', 'SEWER', 'NODE', 'PointZ', 'FACTORY', TRUE),
    ('${tenant_id}', 'OUTFALL', 'SEWER', 'NODE', 'PointZ', 'FACTORY', TRUE),
    ('${tenant_id}', 'JUNCTION', 'WATER', 'NODE', 'PointZ', 'FACTORY', TRUE),
    ('${tenant_id}', 'FITTING', 'WATER', 'NODE', 'PointZ', 'FACTORY', TRUE),
    ('${tenant_id}', 'ATTACHMENT', 'WATER', 'STRUCTURE', 'MultiPolygon', 'FACTORY', TRUE),
    ('${tenant_id}', 'MONITORING', 'WATER', 'NODE', 'PointZ', 'FACTORY', TRUE),
    ('${tenant_id}', 'HYDRAULIC_NODE', 'WATER', 'NODE', 'PointZ', 'FACTORY', TRUE)
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO gis_feature_type (tenant_id, class_id, code, label, origin, factory_code, active, approval_status)
SELECT '${tenant_id}', c.id, t.code, t.label, 'FACTORY', 'gis01:' || t.code, TRUE, 'APPROVED'
FROM gis_feature_class c
JOIN (VALUES
    ('PIPE', 'WATER_MAIN', 'Rede de distribuição'),
    ('PIPE', 'GRAVITY_SEWER', 'Rede coletora por gravidade'),
    ('PIPE', 'FORCE_MAIN', 'Emissário / recalque de esgoto'),
    ('PIPE', 'INVERTED_SIPHON', 'Sifão invertido'),
    ('VALVE', 'GATE_VALVE', 'Registro de gaveta'),
    ('VALVE', 'PRV', 'Válvula redutora de pressão'),
    ('VALVE', 'AIR_VALVE', 'Ventosa'),
    ('VALVE', 'BLOWOFF', 'Descarga'),
    ('VALVE', 'CHECK_VALVE', 'Válvula de retenção'),
    ('HYDRANT', 'HYDRANT_PILLAR', 'Hidrante de coluna'),
    ('METER_STATION', 'MACRO_METER', 'Macromedidor'),
    ('STORAGE', 'RESERVOIR', 'Reservatório'),
    ('STORAGE', 'ELEVATED_TANK', 'Tanque elevado'),
    ('PUMP_STATION', 'WATER_PUMP_STATION', 'Elevatória de água'),
    ('PUMP_STATION', 'SEWAGE_LIFT_STATION', 'Elevatória de esgoto'),
    ('TREATMENT_PLANT', 'WTP', 'Estação de tratamento de água'),
    ('TREATMENT_PLANT', 'WWTP', 'Estação de tratamento de esgoto'),
    ('SOURCE', 'WELL', 'Poço'),
    ('SOURCE', 'SPRING', 'Nascente'),
    ('SOURCE', 'SURFACE_INTAKE', 'Captação superficial'),
    ('SERVICE_LINE', 'WATER_SERVICE_LINE', 'Ramal de água'),
    ('SERVICE_LINE', 'SEWER_SERVICE_LINE', 'Ramal de esgoto'),
    ('MANHOLE', 'MANHOLE', 'Poço de visita'),
    ('BOX', 'GREASE_TRAP', 'Caixa de gordura'),
    ('OUTFALL', 'OVERFLOW', 'Extravasor'),
    ('JUNCTION', 'JUNCTION', 'Junção'),
    ('FITTING', 'REDUCER', 'Redução'),
    ('FITTING', 'TEE', 'Tê'),
    ('FITTING', 'CROSS', 'Cruzeta'),
    ('FITTING', 'CAP', 'Cap'),
    ('FITTING', 'FLANGE_ADAPTER', 'Adaptador flangeado'),
    ('FITTING', 'COUPLING', 'Luva'),
    ('ATTACHMENT', 'PIPE_SUPPORT', 'Suporte de tubulação'),
    ('ATTACHMENT', 'ANCHOR_BLOCK', 'Bloco de ancoragem'),
    ('ATTACHMENT', 'THRUST_BLOCK', 'Bloco de empuxo'),
    ('ATTACHMENT', 'CATHODIC_PROTECTION', 'Proteção catódica'),
    ('MONITORING', 'PRESSURE_POINT', 'Ponto de pressão'),
    ('MONITORING', 'QUALITY_POINT', 'Ponto de qualidade'),
    ('HYDRAULIC_NODE', 'HYD_TANK', 'Nó hidráulico de tanque'),
    ('HYDRAULIC_NODE', 'HYD_PUMP', 'Nó hidráulico de bomba'),
    ('HYDRAULIC_NODE', 'HYD_SOURCE', 'Nó hidráulico de fonte')
) AS t(class_code, code, label) ON t.class_code = c.code
WHERE c.tenant_id = '${tenant_id}'
ON CONFLICT (tenant_id, class_id, code) DO NOTHING;

-- Meta padrao de fabrica da decisao 5 para as classes semeadas (±0,10 m H,
-- ±0,05 m V). O tenant altera por classe com vigencia e aprovacao (§3.8).
INSERT INTO gis_quality_target (tenant_id, class_id, h_target_m, v_target_m, valid_from)
SELECT '${tenant_id}', id, 0.10, 0.05, CURRENT_DATE
FROM gis_feature_class WHERE tenant_id = '${tenant_id}'
ON CONFLICT DO NOTHING;

-- ======================================================================
-- M2: rename, conversao e relatorio de nao mapeadas.
-- Material do legado vira codigo de gis_material VERBATIM (sem normalizacao):
-- a rugosidade do EPANET (roughnessFor) depende do texto ('PVC' vira plastico,
-- 'LEGACY_PVC' nao), e a view precisa reproduzir o .inp antigo. A normalizacao
-- para o catalogo de fabrica e curadoria da P1.27, nao da migracao.
-- Todo item derivado do legado nasce REFERENCE_TO_VALIDATE: nenhuma norma foi
-- comprada na P1.28 (sem aprovacao do dono), entao nenhum valor migrado pode ser
-- apresentado como conferido (fallback do plano).
-- Geometria multiparte de uma so parte e aceita (primeira parte); multiparte de
-- varias partes, ou tipo incompativel com o papel, vai para o relatorio.
-- METER nao tem destino no GIS (hidrometro e da ligacao, metering): vai para o
-- relatorio e permanece na tabela legada, que nao e apagada aqui.
-- ======================================================================

ALTER TABLE gis_network_element RENAME TO gis_network_element_legacy;

INSERT INTO gis_material (tenant_id, code, origin, active)
SELECT DISTINCT l.tenant_id,
    CASE WHEN l.material IS NULL OR btrim(l.material) = '' THEN 'UNKNOWN'
         ELSE btrim(l.material) END,
    'TENANT', TRUE
FROM gis_network_element_legacy l
WHERE l.element_type <> 'METER'
ON CONFLICT (tenant_id, code) DO NOTHING;

-- Itens sob demanda, so para linhas conversiveis (tipo mapeado e geometria
-- compativel com o papel). dn do diameter_mm do legado, ou de diameter_m nas
-- properties (metros para milimetros). Deduplicado por NOT EXISTS, porque dn e
-- material podem ser nulos e o UNIQUE nao os distingue.
INSERT INTO gis_catalog_item
    (tenant_id, feature_type_id, material_id, dn_mm, origin, active, reference_status)
SELECT DISTINCT l.tenant_id, ft.id, m.id,
    COALESCE(l.diameter_mm,
        CASE WHEN jsonb_typeof(l.properties -> 'diameter_m') = 'number'
             THEN ((l.properties ->> 'diameter_m')::numeric * 1000)::int END),
    'TENANT', TRUE, 'REFERENCE_TO_VALIDATE'
FROM gis_network_element_legacy l
JOIN gis_feature_type ft
    ON ft.tenant_id = l.tenant_id
    AND ft.factory_code = 'gis01:' || CASE l.element_type
        WHEN 'PIPE' THEN 'WATER_MAIN'
        WHEN 'VALVE' THEN 'GATE_VALVE'
        WHEN 'PRESSURE_REDUCING_VALVE' THEN 'PRV'
        WHEN 'AIR_VALVE' THEN 'AIR_VALVE'
        WHEN 'BLOWOFF_VALVE' THEN 'BLOWOFF'
        WHEN 'CHECK_VALVE' THEN 'CHECK_VALVE'
        WHEN 'HYDRANT' THEN 'HYDRANT_PILLAR'
        WHEN 'MACRO_METER' THEN 'MACRO_METER'
        WHEN 'RESERVOIR' THEN 'RESERVOIR'
        WHEN 'TANK' THEN 'ELEVATED_TANK'
        WHEN 'PUMP_STATION' THEN 'WATER_PUMP_STATION'
        WHEN 'TREATMENT_PLANT' THEN 'WTP'
        WHEN 'WELL' THEN 'WELL'
        WHEN 'SPRING' THEN 'SPRING'
        WHEN 'INTAKE' THEN 'SURFACE_INTAKE'
        WHEN 'CONNECTION' THEN 'WATER_SERVICE_LINE'
        WHEN 'SERVICE_LINE' THEN 'WATER_SERVICE_LINE'
        WHEN 'SEWER_PIPE' THEN CASE WHEN (l.properties ->> 'pressurized') = 'true'
            THEN 'FORCE_MAIN' ELSE 'GRAVITY_SEWER' END
        WHEN 'MANHOLE' THEN 'MANHOLE'
        WHEN 'SEWER_VALVE' THEN 'GATE_VALVE'
        WHEN 'LIFT_STATION' THEN 'SEWAGE_LIFT_STATION'
        WHEN 'SEWER_TREATMENT_PLANT' THEN 'WWTP'
        WHEN 'SEWER_CONNECTION' THEN 'SEWER_SERVICE_LINE'
        WHEN 'GREASE_TRAP' THEN 'GREASE_TRAP'
        WHEN 'SIPHON' THEN 'INVERTED_SIPHON'
        WHEN 'OVERFLOW_WEIR' THEN 'OVERFLOW'
        WHEN 'NODE' THEN 'JUNCTION'
        WHEN 'JUNCTION' THEN 'JUNCTION'
        WHEN 'REDUCER' THEN 'REDUCER'
        WHEN 'TEE' THEN 'TEE'
        WHEN 'CROSS' THEN 'CROSS'
        WHEN 'CAP' THEN 'CAP'
        WHEN 'FLANGE' THEN 'FLANGE_ADAPTER'
        WHEN 'COUPLING' THEN 'COUPLING'
        WHEN 'SUPPORT' THEN 'PIPE_SUPPORT'
        WHEN 'ANCHOR' THEN 'ANCHOR_BLOCK'
        WHEN 'THRUST_BLOCK' THEN 'THRUST_BLOCK'
        WHEN 'CATHODIC_PROTECTION' THEN 'CATHODIC_PROTECTION'
        WHEN 'MONITORING_POINT' THEN 'PRESSURE_POINT'
        END
JOIN gis_material m
    ON m.tenant_id = l.tenant_id
    AND m.code = CASE WHEN l.material IS NULL OR btrim(l.material) = ''
        THEN 'UNKNOWN' ELSE btrim(l.material) END
WHERE l.element_type <> 'METER'
  AND (
    (l.element_type IN ('VALVE', 'PRESSURE_REDUCING_VALVE', 'AIR_VALVE', 'BLOWOFF_VALVE',
        'CHECK_VALVE', 'HYDRANT', 'MACRO_METER', 'MANHOLE', 'SEWER_VALVE', 'GREASE_TRAP',
        'OVERFLOW_WEIR', 'NODE', 'JUNCTION', 'REDUCER', 'TEE', 'CROSS', 'CAP', 'FLANGE',
        'COUPLING', 'MONITORING_POINT')
     AND (ST_GeometryType(l.geometry) = 'ST_Point'
        OR (ST_GeometryType(l.geometry) = 'ST_MultiPoint' AND ST_NumGeometries(l.geometry) = 1)))
    OR (l.element_type IN ('PIPE', 'SEWER_PIPE', 'SIPHON', 'CONNECTION', 'SERVICE_LINE',
        'SEWER_CONNECTION')
     AND (ST_GeometryType(l.geometry) = 'ST_LineString'
        OR (ST_GeometryType(l.geometry) = 'ST_MultiLineString' AND ST_NumGeometries(l.geometry) = 1)))
    OR (l.element_type IN ('RESERVOIR', 'TANK', 'PUMP_STATION', 'TREATMENT_PLANT', 'WELL',
        'SPRING', 'INTAKE', 'LIFT_STATION', 'SEWER_TREATMENT_PLANT', 'SUPPORT', 'ANCHOR',
        'THRUST_BLOCK', 'CATHODIC_PROTECTION')
     AND (ST_GeometryType(l.geometry) IN ('ST_Polygon', 'ST_MultiPolygon', 'ST_Point')
        OR (ST_GeometryType(l.geometry) = 'ST_MultiPoint' AND ST_NumGeometries(l.geometry) = 1)))
  )
  AND NOT EXISTS (
    SELECT 1 FROM gis_catalog_item ci
    WHERE ci.tenant_id = l.tenant_id AND ci.feature_type_id = ft.id
      AND ci.material_id = m.id
      AND ci.dn_mm IS NOT DISTINCT FROM COALESCE(l.diameter_mm,
        CASE WHEN jsonb_typeof(l.properties -> 'diameter_m') = 'number'
             THEN ((l.properties ->> 'diameter_m')::numeric * 1000)::int END)
  );

-- Nos (pontos). Z desconhecido vira NaN, nunca 0 inventado: ST_Force3D(geom, NaN).
-- elevation_m vira ground_elevation_m; setting fica tipado; o resto das properties
-- numericas conhecidas sai de attributes (a view as reemite a partir das colunas).
INSERT INTO gis_node
    (tenant_id, code, feature_type_id, catalog_item_id, name, lifecycle_status,
     retired_reason, condition_rating, installed_on, installed_on_precision,
     attributes, schema_version, capture_method, vertical_datum, source,
     legacy_object_id, dma_id, ground_elevation_m, rim_elevation_m,
     invert_elevation_m, setting, geom)
SELECT l.tenant_id, l.element_code, ft.id, ci.id, l.name,
    CASE WHEN l.status = 'PLANNED' THEN 'PLANNED'
         WHEN l.status IN ('INACTIVE', 'ABANDONED', 'REMOVED') THEN 'ABANDONED'
         ELSE 'IN_SERVICE' END,
    CASE WHEN l.status = 'REMOVED' THEN 'REMOVED'
         WHEN l.status IN ('INACTIVE', 'ABANDONED') THEN 'ABANDONED_IN_PLACE' END,
    l.condition_rating, l.installation_date::date, 'UNKNOWN',
    COALESCE(l.properties, '{}'::jsonb)
        - ARRAY['elevation_m', 'rim_elevation_m', 'invert_elevation_m', 'setting'],
    ft.schema_version, 'CAD_IMPORT', 'UNKNOWN', 'IMPORT', l.id::text, l.dma_id,
    CASE WHEN jsonb_typeof(l.properties -> 'elevation_m') = 'number'
         THEN (l.properties ->> 'elevation_m')::numeric END,
    CASE WHEN jsonb_typeof(l.properties -> 'rim_elevation_m') = 'number'
         THEN (l.properties ->> 'rim_elevation_m')::numeric END,
    CASE WHEN jsonb_typeof(l.properties -> 'invert_elevation_m') = 'number'
         THEN (l.properties ->> 'invert_elevation_m')::numeric END,
    CASE WHEN jsonb_typeof(l.properties -> 'setting') = 'number'
         THEN (l.properties ->> 'setting')::numeric END,
    CASE WHEN ST_GeometryType(l.geometry) = 'ST_MultiPoint'
         THEN ST_Force3D(ST_GeometryN(l.geometry, 1), 'NaN'::double precision)
         ELSE ST_Force3D(l.geometry, 'NaN'::double precision) END
FROM gis_network_element_legacy l
JOIN gis_feature_type ft
    ON ft.tenant_id = l.tenant_id
    AND ft.factory_code = 'gis01:' || CASE l.element_type
        WHEN 'VALVE' THEN 'GATE_VALVE'
        WHEN 'PRESSURE_REDUCING_VALVE' THEN 'PRV'
        WHEN 'AIR_VALVE' THEN 'AIR_VALVE'
        WHEN 'BLOWOFF_VALVE' THEN 'BLOWOFF'
        WHEN 'CHECK_VALVE' THEN 'CHECK_VALVE'
        WHEN 'HYDRANT' THEN 'HYDRANT_PILLAR'
        WHEN 'MACRO_METER' THEN 'MACRO_METER'
        WHEN 'MANHOLE' THEN 'MANHOLE'
        WHEN 'SEWER_VALVE' THEN 'GATE_VALVE'
        WHEN 'GREASE_TRAP' THEN 'GREASE_TRAP'
        WHEN 'OVERFLOW_WEIR' THEN 'OVERFLOW'
        WHEN 'NODE' THEN 'JUNCTION'
        WHEN 'JUNCTION' THEN 'JUNCTION'
        WHEN 'REDUCER' THEN 'REDUCER'
        WHEN 'TEE' THEN 'TEE'
        WHEN 'CROSS' THEN 'CROSS'
        WHEN 'CAP' THEN 'CAP'
        WHEN 'FLANGE' THEN 'FLANGE_ADAPTER'
        WHEN 'COUPLING' THEN 'COUPLING'
        WHEN 'MONITORING_POINT' THEN 'PRESSURE_POINT'
        END
LEFT JOIN gis_catalog_item ci
    ON ci.tenant_id = l.tenant_id AND ci.feature_type_id = ft.id
    AND ci.material_id = (SELECT id FROM gis_material
        WHERE tenant_id = l.tenant_id
          AND code = CASE WHEN l.material IS NULL OR btrim(l.material) = ''
              THEN 'UNKNOWN' ELSE btrim(l.material) END)
    AND ci.dn_mm IS NOT DISTINCT FROM COALESCE(l.diameter_mm,
        CASE WHEN jsonb_typeof(l.properties -> 'diameter_m') = 'number'
             THEN ((l.properties ->> 'diameter_m')::numeric * 1000)::int END)
WHERE l.element_type IN ('VALVE', 'PRESSURE_REDUCING_VALVE', 'AIR_VALVE', 'BLOWOFF_VALVE',
    'CHECK_VALVE', 'HYDRANT', 'MACRO_METER', 'MANHOLE', 'SEWER_VALVE', 'GREASE_TRAP',
    'OVERFLOW_WEIR', 'NODE', 'JUNCTION', 'REDUCER', 'TEE', 'CROSS', 'CAP', 'FLANGE',
    'COUPLING', 'MONITORING_POINT')
  AND (ST_GeometryType(l.geometry) = 'ST_Point'
    OR (ST_GeometryType(l.geometry) = 'ST_MultiPoint' AND ST_NumGeometries(l.geometry) = 1));

-- Arcos. length_m e calculado (geodesico) pela coluna gerada; o comprimento do
-- legado vai para declared_length_m, e a view favorece o declarado: o builder do
-- EPANET prefere lengthM cadastrado, e o .inp antigo usava o valor do legado.
INSERT INTO gis_arc
    (tenant_id, code, feature_type_id, catalog_item_id, name, lifecycle_status,
     retired_reason, condition_rating, installed_on, installed_on_precision,
     attributes, schema_version, capture_method, vertical_datum, source,
     legacy_object_id, dma_id, declared_length_m, upstream_invert_m,
     downstream_invert_m, pressurized, flow_direction, network_function, depth_m, geom)
SELECT l.tenant_id, l.element_code, ft.id, ci.id, l.name,
    CASE WHEN l.status = 'PLANNED' THEN 'PLANNED'
         WHEN l.status IN ('INACTIVE', 'ABANDONED', 'REMOVED') THEN 'ABANDONED'
         ELSE 'IN_SERVICE' END,
    CASE WHEN l.status = 'REMOVED' THEN 'REMOVED'
         WHEN l.status IN ('INACTIVE', 'ABANDONED') THEN 'ABANDONED_IN_PLACE' END,
    l.condition_rating, l.installation_date::date, 'UNKNOWN',
    COALESCE(l.properties, '{}'::jsonb) - ARRAY['diameter_m', 'pressurized'],
    ft.schema_version, 'CAD_IMPORT', 'UNKNOWN', 'IMPORT', l.id::text, l.dma_id,
    l.length_m,
    CASE WHEN jsonb_typeof(l.properties -> 'upstream_invert_m') = 'number'
         THEN (l.properties ->> 'upstream_invert_m')::numeric END,
    CASE WHEN jsonb_typeof(l.properties -> 'downstream_invert_m') = 'number'
         THEN (l.properties ->> 'downstream_invert_m')::numeric END,
    CASE WHEN l.element_type = 'PIPE' THEN TRUE
         WHEN (l.properties ->> 'pressurized') = 'true' THEN TRUE
         WHEN (l.properties ->> 'pressurized') = 'false' THEN FALSE END,
    'UNKNOWN',
    CASE WHEN l.element_type = 'PIPE' THEN 'DISTRIBUTION'
         WHEN l.element_type = 'SIPHON' THEN 'INVERTED_SIPHON'
         WHEN (l.properties ->> 'pressurized') = 'true' THEN 'PRESSURE'
         ELSE 'GRAVITY' END,
    l.depth_m,
    CASE WHEN ST_GeometryType(l.geometry) = 'ST_MultiLineString'
         THEN ST_Force3D(ST_GeometryN(l.geometry, 1), 'NaN'::double precision)
         ELSE ST_Force3D(l.geometry, 'NaN'::double precision) END
FROM gis_network_element_legacy l
JOIN gis_feature_type ft
    ON ft.tenant_id = l.tenant_id
    AND ft.factory_code = 'gis01:' || CASE l.element_type
        WHEN 'PIPE' THEN 'WATER_MAIN'
        WHEN 'SEWER_PIPE' THEN CASE WHEN (l.properties ->> 'pressurized') = 'true'
            THEN 'FORCE_MAIN' ELSE 'GRAVITY_SEWER' END
        WHEN 'SIPHON' THEN 'INVERTED_SIPHON'
        END
LEFT JOIN gis_catalog_item ci
    ON ci.tenant_id = l.tenant_id AND ci.feature_type_id = ft.id
    AND ci.material_id = (SELECT id FROM gis_material
        WHERE tenant_id = l.tenant_id
          AND code = CASE WHEN l.material IS NULL OR btrim(l.material) = ''
              THEN 'UNKNOWN' ELSE btrim(l.material) END)
    AND ci.dn_mm IS NOT DISTINCT FROM COALESCE(l.diameter_mm,
        CASE WHEN jsonb_typeof(l.properties -> 'diameter_m') = 'number'
             THEN ((l.properties ->> 'diameter_m')::numeric * 1000)::int END)
WHERE l.element_type IN ('PIPE', 'SEWER_PIPE', 'SIPHON')
  AND (ST_GeometryType(l.geometry) = 'ST_LineString'
    OR (ST_GeometryType(l.geometry) = 'ST_MultiLineString' AND ST_NumGeometries(l.geometry) = 1));

-- Ramais (CONNECTION). arc_id fica nulo: o legado nao diz a qual arco o ramal se
-- liga; o vinculo chega na GIS-05 (P2.23), com o ramal automatico.
INSERT INTO gis_connection
    (tenant_id, code, feature_type_id, catalog_item_id, name, lifecycle_status,
     retired_reason, condition_rating, installed_on, installed_on_precision,
     attributes, schema_version, capture_method, vertical_datum, source,
     legacy_object_id, dma_id, endpoint_type, depth_m, geom)
SELECT l.tenant_id, l.element_code, ft.id, ci.id, l.name,
    CASE WHEN l.status = 'PLANNED' THEN 'PLANNED'
         WHEN l.status IN ('INACTIVE', 'ABANDONED', 'REMOVED') THEN 'ABANDONED'
         ELSE 'IN_SERVICE' END,
    CASE WHEN l.status = 'REMOVED' THEN 'REMOVED'
         WHEN l.status IN ('INACTIVE', 'ABANDONED') THEN 'ABANDONED_IN_PLACE' END,
    l.condition_rating, l.installation_date::date, 'UNKNOWN',
    COALESCE(l.properties, '{}'::jsonb) - ARRAY['diameter_m'],
    ft.schema_version, 'CAD_IMPORT', 'UNKNOWN', 'IMPORT', l.id::text, l.dma_id,
    CASE WHEN l.element_type = 'SEWER_CONNECTION' THEN 'SEWER_CONNECTION_BOX'
         ELSE 'METER_SETTING' END,
    l.depth_m,
    CASE WHEN ST_GeometryType(l.geometry) = 'ST_MultiLineString'
         THEN ST_Force3D(ST_GeometryN(l.geometry, 1), 'NaN'::double precision)
         ELSE ST_Force3D(l.geometry, 'NaN'::double precision) END
FROM gis_network_element_legacy l
JOIN gis_feature_type ft
    ON ft.tenant_id = l.tenant_id
    AND ft.factory_code = 'gis01:' || CASE l.element_type
        WHEN 'CONNECTION' THEN 'WATER_SERVICE_LINE'
        WHEN 'SERVICE_LINE' THEN 'WATER_SERVICE_LINE'
        WHEN 'SEWER_CONNECTION' THEN 'SEWER_SERVICE_LINE'
        END
LEFT JOIN gis_catalog_item ci
    ON ci.tenant_id = l.tenant_id AND ci.feature_type_id = ft.id
    AND ci.material_id = (SELECT id FROM gis_material
        WHERE tenant_id = l.tenant_id
          AND code = CASE WHEN l.material IS NULL OR btrim(l.material) = ''
              THEN 'UNKNOWN' ELSE btrim(l.material) END)
    AND ci.dn_mm IS NOT DISTINCT FROM COALESCE(l.diameter_mm,
        CASE WHEN jsonb_typeof(l.properties -> 'diameter_m') = 'number'
             THEN ((l.properties ->> 'diameter_m')::numeric * 1000)::int END)
WHERE l.element_type IN ('CONNECTION', 'SERVICE_LINE', 'SEWER_CONNECTION')
  AND (ST_GeometryType(l.geometry) = 'ST_LineString'
    OR (ST_GeometryType(l.geometry) = 'ST_MultiLineString' AND ST_NumGeometries(l.geometry) = 1));

-- Estruturas. Poligono vai para geom (MultiPolygon); ponto vai para geom_point.
INSERT INTO gis_structure
    (tenant_id, code, feature_type_id, catalog_item_id, name, lifecycle_status,
     retired_reason, condition_rating, installed_on, installed_on_precision,
     attributes, schema_version, capture_method, vertical_datum, source,
     legacy_object_id, dma_id, volume_m3, power_kw, head_m, geom, geom_point)
SELECT l.tenant_id, l.element_code, ft.id, ci.id, l.name,
    CASE WHEN l.status = 'PLANNED' THEN 'PLANNED'
         WHEN l.status IN ('INACTIVE', 'ABANDONED', 'REMOVED') THEN 'ABANDONED'
         ELSE 'IN_SERVICE' END,
    CASE WHEN l.status = 'REMOVED' THEN 'REMOVED'
         WHEN l.status IN ('INACTIVE', 'ABANDONED') THEN 'ABANDONED_IN_PLACE' END,
    l.condition_rating, l.installation_date::date, 'UNKNOWN',
    COALESCE(l.properties, '{}'::jsonb)
        - ARRAY['head_m', 'power_kw', 'volume_m3', 'elevation_m'],
    ft.schema_version, 'CAD_IMPORT', 'UNKNOWN', 'IMPORT', l.id::text, l.dma_id,
    CASE WHEN jsonb_typeof(l.properties -> 'volume_m3') = 'number'
         THEN (l.properties ->> 'volume_m3')::numeric END,
    CASE WHEN jsonb_typeof(l.properties -> 'power_kw') = 'number'
         THEN (l.properties ->> 'power_kw')::numeric END,
    CASE WHEN jsonb_typeof(l.properties -> 'head_m') = 'number'
         THEN (l.properties ->> 'head_m')::numeric END,
    -- Estruturas sao 2D por desenho (geom MultiPolygon e geom_point Point, ambos
    -- sem Z; a cota vive nas colunas, nao no vertice): Force2D, nao Force3D —
    -- o Force3D aqui quebrava com "Geometry has Z dimension but column does
    -- not" (achado da IT GIS-01, segundo da serie). O no hidraulico, que e
    -- PointZ, ganha o Z NaN no INSERT seguinte, a partir da estrutura 2D.
    CASE WHEN ST_GeometryType(l.geometry) IN ('ST_Polygon', 'ST_MultiPolygon')
         THEN ST_Force2D(ST_Multi(l.geometry)) END,
    CASE WHEN ST_GeometryType(l.geometry) = 'ST_Point'
         THEN ST_Force2D(l.geometry)
         WHEN ST_GeometryType(l.geometry) = 'ST_MultiPoint'
         THEN ST_Force2D(ST_GeometryN(l.geometry, 1)) END
FROM gis_network_element_legacy l
JOIN gis_feature_type ft
    ON ft.tenant_id = l.tenant_id
    AND ft.factory_code = 'gis01:' || CASE l.element_type
        WHEN 'RESERVOIR' THEN 'RESERVOIR'
        WHEN 'TANK' THEN 'ELEVATED_TANK'
        WHEN 'PUMP_STATION' THEN 'WATER_PUMP_STATION'
        WHEN 'TREATMENT_PLANT' THEN 'WTP'
        WHEN 'WELL' THEN 'WELL'
        WHEN 'SPRING' THEN 'SPRING'
        WHEN 'INTAKE' THEN 'SURFACE_INTAKE'
        WHEN 'LIFT_STATION' THEN 'SEWAGE_LIFT_STATION'
        WHEN 'SEWER_TREATMENT_PLANT' THEN 'WWTP'
        WHEN 'SUPPORT' THEN 'PIPE_SUPPORT'
        WHEN 'ANCHOR' THEN 'ANCHOR_BLOCK'
        WHEN 'THRUST_BLOCK' THEN 'THRUST_BLOCK'
        WHEN 'CATHODIC_PROTECTION' THEN 'CATHODIC_PROTECTION'
        END
LEFT JOIN gis_catalog_item ci
    ON ci.tenant_id = l.tenant_id AND ci.feature_type_id = ft.id
    AND ci.material_id = (SELECT id FROM gis_material
        WHERE tenant_id = l.tenant_id
          AND code = CASE WHEN l.material IS NULL OR btrim(l.material) = ''
              THEN 'UNKNOWN' ELSE btrim(l.material) END)
    AND ci.dn_mm IS NOT DISTINCT FROM COALESCE(l.diameter_mm,
        CASE WHEN jsonb_typeof(l.properties -> 'diameter_m') = 'number'
             THEN ((l.properties ->> 'diameter_m')::numeric * 1000)::int END)
WHERE l.element_type IN ('RESERVOIR', 'TANK', 'PUMP_STATION', 'TREATMENT_PLANT', 'WELL',
    'SPRING', 'INTAKE', 'LIFT_STATION', 'SEWER_TREATMENT_PLANT', 'SUPPORT', 'ANCHOR',
    'THRUST_BLOCK', 'CATHODIC_PROTECTION')
  AND (ST_GeometryType(l.geometry) IN ('ST_Polygon', 'ST_MultiPolygon', 'ST_Point')
    OR (ST_GeometryType(l.geometry) = 'ST_MultiPoint' AND ST_NumGeometries(l.geometry) = 1));

-- Nos hidraulicos das estruturas (§2.4: RESERVOIR e TANK com no TANK; elevatorias
-- com no PUMP; poco, nascente e captacao com no SOURCE). O no carrega o code da
-- estrutura e copia os attributes dela: a view emite o no (nao a estrutura) para
-- essas estruturas, e o builder do EPANET le init_level_m, min_level_m e
-- max_level_m das properties — sem a copia, esses valores sumiriam da view.
INSERT INTO gis_node
    (tenant_id, code, feature_type_id, name, lifecycle_status, retired_reason,
     condition_rating, installed_on, installed_on_precision, attributes,
     schema_version, capture_method, vertical_datum, source, legacy_object_id,
     dma_id, ground_elevation_m, geom)
SELECT s.tenant_id, s.code, hft.id, s.name, s.lifecycle_status, s.retired_reason,
    s.condition_rating, s.installed_on, s.installed_on_precision, s.attributes,
    hft.schema_version, 'CAD_IMPORT', 'UNKNOWN', 'IMPORT', s.legacy_object_id,
    s.dma_id,
    CASE WHEN jsonb_typeof(l.properties -> 'elevation_m') = 'number'
         THEN (l.properties ->> 'elevation_m')::numeric END,
    -- geom_point e 2D e o no e PointZ: o ramo do ponto precisa do Force3D
    -- como o ramo do poligono (achado da IT GIS-01, terceiro da serie: o
    -- WELL ponto do legado caia aqui com "Column has Z dimension but
    -- geometry does not").
    CASE WHEN s.geom_point IS NOT NULL THEN ST_Force3D(s.geom_point, 'NaN'::double precision)
         ELSE ST_Force3D(ST_PointOnSurface(s.geom), 'NaN'::double precision) END
FROM gis_structure s
JOIN gis_network_element_legacy l ON l.id::text = s.legacy_object_id
JOIN gis_feature_type hft
    ON hft.tenant_id = s.tenant_id
    AND hft.factory_code = 'gis01:' || CASE l.element_type
        WHEN 'RESERVOIR' THEN 'HYD_TANK'
        WHEN 'TANK' THEN 'HYD_TANK'
        WHEN 'PUMP_STATION' THEN 'HYD_PUMP'
        WHEN 'LIFT_STATION' THEN 'HYD_PUMP'
        WHEN 'WELL' THEN 'HYD_SOURCE'
        WHEN 'INTAKE' THEN 'HYD_SOURCE'
        END
WHERE l.element_type IN ('RESERVOIR', 'TANK', 'PUMP_STATION', 'LIFT_STATION', 'WELL', 'INTAKE');

INSERT INTO gis_containment
    (tenant_id, container_table, container_id, contained_table, contained_id, relation)
SELECT s.tenant_id, 'gis_structure', s.id, 'gis_node', n.id, 'CONTAINS'
FROM gis_structure s
JOIN gis_node n
    ON n.tenant_id = s.tenant_id AND n.legacy_object_id = s.legacy_object_id
JOIN gis_feature_type hft
    ON hft.tenant_id = n.tenant_id AND hft.id = n.feature_type_id
    AND hft.factory_code IN ('gis01:HYD_TANK', 'gis01:HYD_PUMP', 'gis01:HYD_SOURCE');

-- Relatorio de nao mapeadas: 100% das linhas do legado estao convertidas ou aqui.
-- A tabela legada e preservada integralmente, entao "nao mapeada" nao significa
-- dado perdido: significa feicao sem destino no modelo novo, a tratar nas frentes
-- seguintes (METER vai para metering via ligacao; geometria incompativel exige
-- correcao do dado de origem).
CREATE TABLE IF NOT EXISTS gis_migration_unmapped (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    legacy_id UUID NOT NULL,
    element_code TEXT NOT NULL,
    element_type TEXT NOT NULL,
    reason TEXT NOT NULL CHECK (reason IN ('NO_DESTINATION', 'GEOMETRY_TYPE_MISMATCH')),
    detail TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_migration_unmapped_tenant_id UNIQUE (tenant_id, id)
);

SELECT register_tenant_table('gis_migration_unmapped');

INSERT INTO gis_migration_unmapped
    (tenant_id, legacy_id, element_code, element_type, reason, detail)
SELECT l.tenant_id, l.id, l.element_code, l.element_type,
    CASE WHEN l.element_type = 'METER' THEN 'NO_DESTINATION'
         ELSE 'GEOMETRY_TYPE_MISMATCH' END,
    CASE WHEN l.element_type = 'METER'
         THEN 'Hidrometro pertence a ligacao (metering); sem destino no GIS (GIS-CADASTRO-TECNICO §2.4).'
         ELSE 'Geometria ' || ST_GeometryType(l.geometry) || ' incompativel com o papel '
            || CASE WHEN l.element_type IN ('VALVE', 'PRESSURE_REDUCING_VALVE', 'AIR_VALVE',
                'BLOWOFF_VALVE', 'CHECK_VALVE', 'HYDRANT', 'MACRO_METER', 'MANHOLE',
                'SEWER_VALVE', 'GREASE_TRAP', 'OVERFLOW_WEIR', 'NODE', 'JUNCTION',
                'REDUCER', 'TEE', 'CROSS', 'CAP', 'FLANGE', 'COUPLING', 'MONITORING_POINT')
                THEN 'NODE'
                WHEN l.element_type IN ('PIPE', 'SEWER_PIPE', 'SIPHON') THEN 'ARC'
                WHEN l.element_type IN ('CONNECTION', 'SERVICE_LINE', 'SEWER_CONNECTION')
                THEN 'CONNECTION' ELSE 'STRUCTURE' END
            || '.' END
FROM gis_network_element_legacy l
WHERE l.element_type = 'METER'
   OR NOT (
    (l.element_type IN ('VALVE', 'PRESSURE_REDUCING_VALVE', 'AIR_VALVE', 'BLOWOFF_VALVE',
        'CHECK_VALVE', 'HYDRANT', 'MACRO_METER', 'MANHOLE', 'SEWER_VALVE', 'GREASE_TRAP',
        'OVERFLOW_WEIR', 'NODE', 'JUNCTION', 'REDUCER', 'TEE', 'CROSS', 'CAP', 'FLANGE',
        'COUPLING', 'MONITORING_POINT')
     AND (ST_GeometryType(l.geometry) = 'ST_Point'
        OR (ST_GeometryType(l.geometry) = 'ST_MultiPoint' AND ST_NumGeometries(l.geometry) = 1)))
    OR (l.element_type IN ('PIPE', 'SEWER_PIPE', 'SIPHON', 'CONNECTION', 'SERVICE_LINE',
        'SEWER_CONNECTION')
     AND (ST_GeometryType(l.geometry) = 'ST_LineString'
        OR (ST_GeometryType(l.geometry) = 'ST_MultiLineString' AND ST_NumGeometries(l.geometry) = 1)))
    OR (l.element_type IN ('RESERVOIR', 'TANK', 'PUMP_STATION', 'TREATMENT_PLANT', 'WELL',
        'SPRING', 'INTAKE', 'LIFT_STATION', 'SEWER_TREATMENT_PLANT', 'SUPPORT', 'ANCHOR',
        'THRUST_BLOCK', 'CATHODIC_PROTECTION')
     AND (ST_GeometryType(l.geometry) IN ('ST_Polygon', 'ST_MultiPolygon', 'ST_Point')
        OR (ST_GeometryType(l.geometry) = 'ST_MultiPoint' AND ST_NumGeometries(l.geometry) = 1)))
  );

-- ======================================================================
-- M3: view de compatibilidade gis_network_element.
-- Material: a migracao cria item com material 'UNKNOWN' quando o legado nao
-- tem material; a view devolve NULL nesse caso (NULLIF), para reproduzir o
-- legado coluna a coluna — sem isso, a IT GIS-01 acusava divergencia em toda
-- feicao sem material (J1, N1, MH1, PRV1). O .inp nao mudaria (UNKNOWN e NULL
-- dao a rugosidade padrao no builder), mas a view e de compatibilidade: ela
-- deve devolver o que a tabela antiga devolvia.
-- Reproduz as colunas que a entidade JPA GisNetworkElement le, a partir das
-- feicoes novas, para que o EpanetSimulationService continue funcionando so em
-- leitura ate a GIS-12 (M5) remove-la. security_invoker = true: a RLS das
-- tabelas base vale para quem consulta, nao para o dono da view.
-- Fidelidade ao .inp antigo, ponto a ponto:
--   * material: o texto verbatim do legado (o codigo do material migrado), porque
--     a rugosidade do EPANET depende dele (roughnessFor('PVC') <> roughnessFor
--     de qualquer normalizacao);
--   * comprimento do arco: declared_length_m (o valor do legado) antes do
--     geodesico calculado — o builder prefere o comprimento cadastrado;
--   * diametro: dn do item de catalogo (do diameter_mm do legado);
--   * numericos das properties: reemitidos a partir das colunas tipadas
--     (elevation_m, setting, power_kw, head_m, diameter_m);
--   * estruturas com no hidraulico emitem o no (com o tipo antigo da estrutura),
--     nao a estrutura — o .inp antigo tinha uma linha por elemento do legado;
--   * o que o modelo novo nao tem (criticality, pressure_rating_bar, inspecoes,
--     length de no) sai NULL: continua preservado na tabela legada, e o .inp nao
--     usa essas colunas.
-- Dois tipos antigos fundidos num tipo novo escolhem um representante na volta:
-- WATER_SERVICE_LINE volta como SERVICE_LINE, SEWER_SERVICE_LINE como
-- SEWER_CONNECTION, e NODE/JUNCTION voltam como JUNCTION. Nenhum deles altera o
-- .inp: o toSpec ignora os tres (sem papel hidraulico).
-- ======================================================================

CREATE OR REPLACE VIEW gis_network_element
WITH (security_invoker = true) AS
SELECT n.id, n.tenant_id, n.tenant_schema, n.created_at, n.updated_at, n.version,
    n.created_by, n.updated_by, n.code AS element_code, n.name,
    CASE WHEN cs.id IS NOT NULL THEN CASE cft.code
        WHEN 'WATER_MAIN' THEN 'PIPE'
        WHEN 'GATE_VALVE' THEN 'VALVE'
        WHEN 'PRV' THEN 'PRESSURE_REDUCING_VALVE'
        WHEN 'AIR_VALVE' THEN 'AIR_VALVE'
        WHEN 'BLOWOFF' THEN 'BLOWOFF_VALVE'
        WHEN 'CHECK_VALVE' THEN 'CHECK_VALVE'
        WHEN 'HYDRANT_PILLAR' THEN 'HYDRANT'
        WHEN 'MACRO_METER' THEN 'MACRO_METER'
        WHEN 'RESERVOIR' THEN 'RESERVOIR'
        WHEN 'ELEVATED_TANK' THEN 'TANK'
        WHEN 'WATER_PUMP_STATION' THEN 'PUMP_STATION'
        WHEN 'WTP' THEN 'TREATMENT_PLANT'
        WHEN 'WELL' THEN 'WELL'
        WHEN 'SPRING' THEN 'SPRING'
        WHEN 'SURFACE_INTAKE' THEN 'INTAKE'
        WHEN 'WATER_SERVICE_LINE' THEN 'SERVICE_LINE'
        WHEN 'GRAVITY_SEWER' THEN 'SEWER_PIPE'
        WHEN 'FORCE_MAIN' THEN 'SEWER_PIPE'
        WHEN 'MANHOLE' THEN 'MANHOLE'
        WHEN 'SEWAGE_LIFT_STATION' THEN 'LIFT_STATION'
        WHEN 'WWTP' THEN 'SEWER_TREATMENT_PLANT'
        WHEN 'SEWER_SERVICE_LINE' THEN 'SEWER_CONNECTION'
        WHEN 'GREASE_TRAP' THEN 'GREASE_TRAP'
        WHEN 'INVERTED_SIPHON' THEN 'SIPHON'
        WHEN 'OVERFLOW' THEN 'OVERFLOW_WEIR'
        WHEN 'JUNCTION' THEN 'JUNCTION'
        WHEN 'REDUCER' THEN 'REDUCER'
        WHEN 'TEE' THEN 'TEE'
        WHEN 'CROSS' THEN 'CROSS'
        WHEN 'CAP' THEN 'CAP'
        WHEN 'FLANGE_ADAPTER' THEN 'FLANGE'
        WHEN 'COUPLING' THEN 'COUPLING'
        WHEN 'PIPE_SUPPORT' THEN 'SUPPORT'
        WHEN 'ANCHOR_BLOCK' THEN 'ANCHOR'
        WHEN 'THRUST_BLOCK' THEN 'THRUST_BLOCK'
        WHEN 'CATHODIC_PROTECTION' THEN 'CATHODIC_PROTECTION'
        WHEN 'PRESSURE_POINT' THEN 'MONITORING_POINT'
        WHEN 'QUALITY_POINT' THEN 'MONITORING_POINT'
        ELSE 'NODE' END
    ELSE CASE ft.code
        WHEN 'WATER_MAIN' THEN 'PIPE'
        WHEN 'GATE_VALVE' THEN 'VALVE'
        WHEN 'PRV' THEN 'PRESSURE_REDUCING_VALVE'
        WHEN 'AIR_VALVE' THEN 'AIR_VALVE'
        WHEN 'BLOWOFF' THEN 'BLOWOFF_VALVE'
        WHEN 'CHECK_VALVE' THEN 'CHECK_VALVE'
        WHEN 'HYDRANT_PILLAR' THEN 'HYDRANT'
        WHEN 'MACRO_METER' THEN 'MACRO_METER'
        WHEN 'RESERVOIR' THEN 'RESERVOIR'
        WHEN 'ELEVATED_TANK' THEN 'TANK'
        WHEN 'WATER_PUMP_STATION' THEN 'PUMP_STATION'
        WHEN 'WTP' THEN 'TREATMENT_PLANT'
        WHEN 'WELL' THEN 'WELL'
        WHEN 'SPRING' THEN 'SPRING'
        WHEN 'SURFACE_INTAKE' THEN 'INTAKE'
        WHEN 'WATER_SERVICE_LINE' THEN 'SERVICE_LINE'
        WHEN 'GRAVITY_SEWER' THEN 'SEWER_PIPE'
        WHEN 'FORCE_MAIN' THEN 'SEWER_PIPE'
        WHEN 'MANHOLE' THEN 'MANHOLE'
        WHEN 'SEWAGE_LIFT_STATION' THEN 'LIFT_STATION'
        WHEN 'WWTP' THEN 'SEWER_TREATMENT_PLANT'
        WHEN 'SEWER_SERVICE_LINE' THEN 'SEWER_CONNECTION'
        WHEN 'GREASE_TRAP' THEN 'GREASE_TRAP'
        WHEN 'INVERTED_SIPHON' THEN 'SIPHON'
        WHEN 'OVERFLOW' THEN 'OVERFLOW_WEIR'
        WHEN 'JUNCTION' THEN 'JUNCTION'
        WHEN 'REDUCER' THEN 'REDUCER'
        WHEN 'TEE' THEN 'TEE'
        WHEN 'CROSS' THEN 'CROSS'
        WHEN 'CAP' THEN 'CAP'
        WHEN 'FLANGE_ADAPTER' THEN 'FLANGE'
        WHEN 'COUPLING' THEN 'COUPLING'
        WHEN 'PIPE_SUPPORT' THEN 'SUPPORT'
        WHEN 'ANCHOR_BLOCK' THEN 'ANCHOR'
        WHEN 'THRUST_BLOCK' THEN 'THRUST_BLOCK'
        WHEN 'CATHODIC_PROTECTION' THEN 'CATHODIC_PROTECTION'
        WHEN 'PRESSURE_POINT' THEN 'MONITORING_POINT'
        WHEN 'QUALITY_POINT' THEN 'MONITORING_POINT'
        ELSE 'NODE' END
    END AS element_type,
    n.geom AS geometry, n.dma_id,
    NULLIF(COALESCE(m.code, cm.code), 'UNKNOWN') AS material,
    COALESCE(ci.dn_mm, cci.dn_mm) AS diameter_mm,
    NULL::numeric AS length_m,
    n.installed_on::timestamp AT TIME ZONE 'UTC' AS installation_date,
    CASE WHEN n.installed_on IS NULL THEN NULL
         ELSE DATE_PART('year', age(CURRENT_DATE, n.installed_on))::int END AS age_years,
    n.depth_m, NULL::numeric AS pressure_rating_bar,
    CASE n.lifecycle_status
        WHEN 'IN_SERVICE' THEN 'ACTIVE'
        WHEN 'UNDER_CONSTRUCTION' THEN 'UNDER_CONSTRUCTION'
        WHEN 'PLANNED' THEN 'PLANNED'
        ELSE 'ABANDONED' END AS status,
    n.condition_rating, NULL::text AS criticality,
    jsonb_strip_nulls(n.attributes || jsonb_build_object(
        'elevation_m', n.ground_elevation_m,
        'setting', n.setting,
        'power_kw', cs.power_kw,
        'diameter_m', CASE WHEN COALESCE(ci.dn_mm, cci.dn_mm) IS NOT NULL
            THEN COALESCE(ci.dn_mm, cci.dn_mm) / 1000.0 END)) AS properties,
    NULL::timestamptz AS last_inspection_at,
    NULL::timestamptz AS next_inspection_due
FROM gis_node n
JOIN gis_feature_type ft ON ft.tenant_id = n.tenant_id AND ft.id = n.feature_type_id
LEFT JOIN gis_catalog_item ci ON ci.tenant_id = n.tenant_id AND ci.id = n.catalog_item_id
LEFT JOIN gis_material m ON m.tenant_id = n.tenant_id AND m.id = ci.material_id
LEFT JOIN gis_containment c ON c.tenant_id = n.tenant_id
    AND c.contained_table = 'gis_node' AND c.contained_id = n.id AND c.relation = 'CONTAINS'
LEFT JOIN gis_structure cs ON cs.tenant_id = n.tenant_id AND cs.id = c.container_id
LEFT JOIN gis_feature_type cft ON cft.tenant_id = cs.tenant_id AND cft.id = cs.feature_type_id
LEFT JOIN gis_catalog_item cci ON cci.tenant_id = cs.tenant_id AND cci.id = cs.catalog_item_id
LEFT JOIN gis_material cm ON cm.tenant_id = cs.tenant_id AND cm.id = cci.material_id
UNION ALL
SELECT a.id, a.tenant_id, a.tenant_schema, a.created_at, a.updated_at, a.version,
    a.created_by, a.updated_by, a.code AS element_code, a.name,
    CASE ft.code
        WHEN 'WATER_MAIN' THEN 'PIPE'
        WHEN 'GRAVITY_SEWER' THEN 'SEWER_PIPE'
        WHEN 'FORCE_MAIN' THEN 'SEWER_PIPE'
        WHEN 'INVERTED_SIPHON' THEN 'SIPHON'
        ELSE 'PIPE' END AS element_type,
    a.geom AS geometry, a.dma_id, NULLIF(m.code, 'UNKNOWN') AS material, ci.dn_mm AS diameter_mm,
    COALESCE(a.declared_length_m, a.length_m) AS length_m,
    a.installed_on::timestamp AT TIME ZONE 'UTC' AS installation_date,
    CASE WHEN a.installed_on IS NULL THEN NULL
         ELSE DATE_PART('year', age(CURRENT_DATE, a.installed_on))::int END AS age_years,
    a.depth_m, NULL::numeric AS pressure_rating_bar,
    CASE a.lifecycle_status
        WHEN 'IN_SERVICE' THEN 'ACTIVE'
        WHEN 'UNDER_CONSTRUCTION' THEN 'UNDER_CONSTRUCTION'
        WHEN 'PLANNED' THEN 'PLANNED'
        ELSE 'ABANDONED' END AS status,
    a.condition_rating, NULL::text AS criticality,
    jsonb_strip_nulls(a.attributes || jsonb_build_object(
        'diameter_m', CASE WHEN ci.dn_mm IS NOT NULL THEN ci.dn_mm / 1000.0 END)) AS properties,
    NULL::timestamptz AS last_inspection_at,
    NULL::timestamptz AS next_inspection_due
FROM gis_arc a
JOIN gis_feature_type ft ON ft.tenant_id = a.tenant_id AND ft.id = a.feature_type_id
LEFT JOIN gis_catalog_item ci ON ci.tenant_id = a.tenant_id AND ci.id = a.catalog_item_id
LEFT JOIN gis_material m ON m.tenant_id = a.tenant_id AND m.id = ci.material_id
UNION ALL
SELECT s.id, s.tenant_id, s.tenant_schema, s.created_at, s.updated_at, s.version,
    s.created_by, s.updated_by, s.code AS element_code, s.name,
    CASE ft.code
        WHEN 'RESERVOIR' THEN 'RESERVOIR'
        WHEN 'ELEVATED_TANK' THEN 'TANK'
        WHEN 'WATER_PUMP_STATION' THEN 'PUMP_STATION'
        WHEN 'WTP' THEN 'TREATMENT_PLANT'
        WHEN 'WELL' THEN 'WELL'
        WHEN 'SPRING' THEN 'SPRING'
        WHEN 'SURFACE_INTAKE' THEN 'INTAKE'
        WHEN 'SEWAGE_LIFT_STATION' THEN 'LIFT_STATION'
        WHEN 'WWTP' THEN 'SEWER_TREATMENT_PLANT'
        WHEN 'PIPE_SUPPORT' THEN 'SUPPORT'
        WHEN 'ANCHOR_BLOCK' THEN 'ANCHOR'
        WHEN 'THRUST_BLOCK' THEN 'THRUST_BLOCK'
        WHEN 'CATHODIC_PROTECTION' THEN 'CATHODIC_PROTECTION'
        ELSE 'NODE' END AS element_type,
    COALESCE(s.geom, s.geom_point) AS geometry, s.dma_id, NULLIF(m.code, 'UNKNOWN') AS material,
    ci.dn_mm AS diameter_mm, NULL::numeric AS length_m,
    s.installed_on::timestamp AT TIME ZONE 'UTC' AS installation_date,
    CASE WHEN s.installed_on IS NULL THEN NULL
         ELSE DATE_PART('year', age(CURRENT_DATE, s.installed_on))::int END AS age_years,
    NULL::numeric AS depth_m, NULL::numeric AS pressure_rating_bar,
    CASE s.lifecycle_status
        WHEN 'IN_SERVICE' THEN 'ACTIVE'
        WHEN 'UNDER_CONSTRUCTION' THEN 'UNDER_CONSTRUCTION'
        WHEN 'PLANNED' THEN 'PLANNED'
        ELSE 'ABANDONED' END AS status,
    s.condition_rating, NULL::text AS criticality,
    jsonb_strip_nulls(s.attributes || jsonb_build_object(
        'head_m', s.head_m,
        'power_kw', s.power_kw,
        'diameter_m', CASE WHEN ci.dn_mm IS NOT NULL THEN ci.dn_mm / 1000.0 END)) AS properties,
    NULL::timestamptz AS last_inspection_at,
    NULL::timestamptz AS next_inspection_due
FROM gis_structure s
JOIN gis_feature_type ft ON ft.tenant_id = s.tenant_id AND ft.id = s.feature_type_id
LEFT JOIN gis_catalog_item ci ON ci.tenant_id = s.tenant_id AND ci.id = s.catalog_item_id
LEFT JOIN gis_material m ON m.tenant_id = s.tenant_id AND m.id = ci.material_id
WHERE NOT EXISTS (
    SELECT 1 FROM gis_containment c
    WHERE c.tenant_id = s.tenant_id AND c.container_table = 'gis_structure'
      AND c.container_id = s.id AND c.relation = 'CONTAINS'
)
UNION ALL
SELECT cn.id, cn.tenant_id, cn.tenant_schema, cn.created_at, cn.updated_at, cn.version,
    cn.created_by, cn.updated_by, cn.code AS element_code, cn.name,
    CASE ft.code
        WHEN 'WATER_SERVICE_LINE' THEN 'SERVICE_LINE'
        WHEN 'SEWER_SERVICE_LINE' THEN 'SEWER_CONNECTION'
        ELSE 'SERVICE_LINE' END AS element_type,
    cn.geom AS geometry, cn.dma_id, NULLIF(m.code, 'UNKNOWN') AS material, ci.dn_mm AS diameter_mm,
    cn.length_m,
    cn.installed_on::timestamp AT TIME ZONE 'UTC' AS installation_date,
    CASE WHEN cn.installed_on IS NULL THEN NULL
         ELSE DATE_PART('year', age(CURRENT_DATE, cn.installed_on))::int END AS age_years,
    cn.depth_m, NULL::numeric AS pressure_rating_bar,
    CASE cn.lifecycle_status
        WHEN 'IN_SERVICE' THEN 'ACTIVE'
        WHEN 'UNDER_CONSTRUCTION' THEN 'UNDER_CONSTRUCTION'
        WHEN 'PLANNED' THEN 'PLANNED'
        ELSE 'ABANDONED' END AS status,
    cn.condition_rating, NULL::text AS criticality,
    jsonb_strip_nulls(cn.attributes || jsonb_build_object(
        'diameter_m', CASE WHEN ci.dn_mm IS NOT NULL THEN ci.dn_mm / 1000.0 END)) AS properties,
    NULL::timestamptz AS last_inspection_at,
    NULL::timestamptz AS next_inspection_due
FROM gis_connection cn
JOIN gis_feature_type ft ON ft.tenant_id = cn.tenant_id AND ft.id = cn.feature_type_id
LEFT JOIN gis_catalog_item ci ON ci.tenant_id = cn.tenant_id AND ci.id = cn.catalog_item_id
LEFT JOIN gis_material m ON m.tenant_id = cn.tenant_id AND m.id = ci.material_id;
