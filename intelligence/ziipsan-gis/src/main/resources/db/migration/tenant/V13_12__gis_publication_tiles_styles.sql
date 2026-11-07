-- GIS-06 (P3.25) — publicacao: camadas, estilos (fonte unica), mapas
-- base, fontes WMS cliente e tiles sujos (GIS-CADASTRO-TECNICO §3.4
-- "Publicacao", §7.2 cliente WMS, §10.1 tiles, §10.2 simbologia e
-- §10.3 mapa web; ADR-0034 decisoes 6 e 7; ADR-0036 simbolos; ED-52).
--
-- NUMERACAO: a spec (§3.4) reservava a V13_6 para as tabelas de
-- publicacao, mas no repo real a V13_6 ja foi consumida
-- (gis_check_feature_search_path, fix da integracao P1c) e a serie
-- tenant do GIS vai ate a V13_11 (GIS-07/P3.26, import staging).
-- Precedente do projeto (V13_8, V13_10, V13_11): slot ocupado ->
-- proxima versao livre da serie, sem editar migration aplicada.
-- Esta e a V13_12. Evidencia: espelhos locais das entregas P2.21
-- (V13_1..V13_8), P2.22 (V13_9), P2.23 (V13_10) e P3.26 (V13_11);
-- a conferencia final na main e da integracao (ver relatorio).
--
-- O QUE ESTA MIGRATION FAZ:
--   (1) gis_layer: camadas publicadas (uma linha MVT por dominio e
--       papel, §10.1), com personal (exige gis.view-personal, §6.6),
--       zoom minimo de tile e layer_version — a versao entra na chave
--       de cache (tenant, camada, z, x, y, versao), §10.1;
--   (2) gis_style: a FONTE UNICA da simbologia (§10.2) — por dominio,
--       por tipo e por estado (ciclo de vida + accuracy_status), com
--       origin FACTORY (padrao Ziip) e TENANT (override sem codigo;
--       a resolucao FACTORY x TENANT e por campo, no servico Java);
--   (3) gis_basemap e gis_wms_source: mapas base (OSM padrao; outro
--       provedor so com license_ref, §3.10 gis.basemap_provider) e
--       fontes WMS cadastradas pelo tenant (cliente WMS, §7.2);
--   (4) gis_tile_dirty + gis_tile_range + marcacao na APROVACAO de
--       pacote: gatilho AFTER UPDATE em gis_change_set quando o
--       status vira APPLIED marca os tiles afetados (z10 a z18) das
--       camadas atingidas e incrementa layer_version. Como a chave
--       de cache carrega a versao, o tile velho fica inalcançavel no
--       mesmo instante da transacao de aprovacao — a janela de
--       invalidacao de 1 min do aceite e cumprida pela versao, e a
--       gis_tile_dirty e a lista de trabalho para a eviccao fisica
--       do worker (nunca a unica garantia).
--
-- O QUE ESTA MIGRATION NAO FAZ (de proposito):
--   * nao cria o sprite binario dos simbolos da P1.30: o gis_style
--     guarda o icon_key (o style_key do tipo, §10.2/ADR-0036) e a
--     api publica o manifesto de simbolos dos tipos ativos; a arte
--     SVG e o sprite sao entregaveis da P1.30, consumidos por chave
--     — nenhum SVG e inventado aqui;
--   * nao define os valores hex dos tokens map-*: eles sao do
--     ADR-0017/P0.36 (palettes.css do frontend) e sao resolvidos no
--     navegador pelos temas claro e escuro; aqui so trafega o NOME
--     do token (o mesmo vocabulario de gis_domain.map_color_token);
--   * nao altera gis_apply_change_set (V13_9) nem nenhuma funcao das
--     series anteriores: a marcacao e por gatilho proprio, como a
--     area suja da V13_8;
--   * a camada properties le consumer_units.coordinates (o ponto do
--     imovel da GIS-05, V13_10) — tabela do registry no mesmo schema
--     de tenant; a migration NAO cria FK para ela (modulo vizinho,
--     precedente da V13_10 com guardas por to_regclass).
--
-- CONVENCOES (as mesmas das V13_8/V13_9/V13_11): funcoes de gatilho
-- fixam o search_path LOCAL para o schema do tenant
-- (TG_TABLE_SCHEMA); funcoes chamadas pela api resolvem o tenant
-- pela GUC app.current_tenant; mensagens de erro sem diacriticos;
-- RLS via register_tenant_table.

SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- ======================================================================
-- (1) gis_layer — camadas publicadas (§3.4, §10.1)
-- ======================================================================

CREATE TABLE IF NOT EXISTS gis_layer (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    key TEXT NOT NULL,
    label TEXT NOT NULL,
    group_label TEXT NOT NULL,
    source_kind TEXT NOT NULL CHECK (source_kind IN
        ('FEATURE', 'CONTEXT', 'PROPERTIES')),
    feature_table TEXT CHECK (feature_table IN
        ('gis_node', 'gis_arc', 'gis_connection', 'gis_structure',
         'gis_zone', 'gis_poi', 'gis_context_feature')),
    domain_key TEXT,
    personal BOOLEAN NOT NULL DEFAULT FALSE,
    tile_min_zoom INT NOT NULL DEFAULT 0 CHECK (tile_min_zoom BETWEEN 0 AND 22),
    layer_version BIGINT NOT NULL DEFAULT 1,
    origin TEXT NOT NULL DEFAULT 'FACTORY' CHECK (origin IN ('FACTORY', 'TENANT')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_layer_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_layer_tenant_key UNIQUE (tenant_id, key),
    CONSTRAINT ck_gis_layer_feature_source
        CHECK (source_kind <> 'FEATURE' OR feature_table IS NOT NULL)
);

SELECT register_tenant_table('gis_layer');

CREATE INDEX IF NOT EXISTS idx_gis_layer_domain
    ON gis_layer (tenant_id, feature_table, domain_key) WHERE active;

-- Camadas de fabrica (§10.1: uma camada MVT por dominio e papel;
-- ramais e conexoes a partir de z17; imoveis e ligacoes sao pessoais
-- e exigem gis.view-personal, §6.6/§7.2).
INSERT INTO gis_layer (tenant_id, key, label, group_label, source_kind,
        feature_table, domain_key, personal, tile_min_zoom, origin, sort_order)
VALUES
    ('${tenant_id}', 'water-arcs', 'Água — arcos', 'Água', 'FEATURE',
        'gis_arc', 'WATER', FALSE, 0, 'FACTORY', 10),
    ('${tenant_id}', 'water-nodes', 'Água — nós', 'Água', 'FEATURE',
        'gis_node', 'WATER', FALSE, 0, 'FACTORY', 11),
    ('${tenant_id}', 'sewer-arcs', 'Esgoto — arcos', 'Esgoto', 'FEATURE',
        'gis_arc', 'SEWER', FALSE, 0, 'FACTORY', 20),
    ('${tenant_id}', 'sewer-nodes', 'Esgoto — nós', 'Esgoto', 'FEATURE',
        'gis_node', 'SEWER', FALSE, 0, 'FACTORY', 21),
    ('${tenant_id}', 'storm-arcs', 'Drenagem — arcos', 'Drenagem', 'FEATURE',
        'gis_arc', 'STORM', FALSE, 0, 'FACTORY', 30),
    ('${tenant_id}', 'storm-nodes', 'Drenagem — nós', 'Drenagem', 'FEATURE',
        'gis_node', 'STORM', FALSE, 0, 'FACTORY', 31),
    ('${tenant_id}', 'structures', 'Estruturas', 'Rede', 'FEATURE',
        'gis_structure', NULL, FALSE, 0, 'FACTORY', 40),
    ('${tenant_id}', 'zones', 'Zonas e setores', 'Rede', 'FEATURE',
        'gis_zone', NULL, FALSE, 0, 'FACTORY', 41),
    ('${tenant_id}', 'pois', 'Pontos de interesse', 'Contexto', 'FEATURE',
        'gis_poi', NULL, FALSE, 0, 'FACTORY', 50),
    ('${tenant_id}', 'context', 'Contexto importado', 'Contexto', 'CONTEXT',
        'gis_context_feature', NULL, FALSE, 0, 'FACTORY', 51),
    ('${tenant_id}', 'service-lines', 'Ligações e ramais', 'Comercial', 'FEATURE',
        'gis_connection', NULL, TRUE, 17, 'FACTORY', 60),
    ('${tenant_id}', 'properties', 'Imóveis', 'Comercial', 'PROPERTIES',
        NULL, NULL, TRUE, 0, 'FACTORY', 61)
ON CONFLICT (tenant_id, key) DO NOTHING;

-- ======================================================================
-- (2) gis_style — fonte unica da simbologia (§10.2)
-- ======================================================================
-- Uma linha define os campos que declara (NULL = herda da linha
-- menos especifica): a resolucao por campo, da mais para a menos
-- especifica, e TENANT(tipo+accuracy) > TENANT(tipo) >
-- FACTORY(tipo+accuracy) > FACTORY(tipo) > TENANT(dominio+accuracy)
-- > TENANT(dominio) > FACTORY(dominio+accuracy) > FACTORY(dominio)
-- > generica. O servico Java (GisStyleRules) e o espelho testado
-- desta ordem; o banco guarda os dados e as unicidades.

CREATE TABLE IF NOT EXISTS gis_style (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    domain_key TEXT,
    feature_type_id UUID,
    lifecycle_status TEXT NOT NULL CHECK (lifecycle_status IN
        ('PLANNED', 'UNDER_CONSTRUCTION', 'IN_SERVICE', 'ABANDONED')),
    accuracy_status TEXT CHECK (accuracy_status IN
        ('WITHIN_TARGET', 'OUT_OF_TARGET', 'APPROXIMATE', 'UNKNOWN')),
    color_token TEXT CHECK (color_token IS NULL OR color_token LIKE 'map-%'),
    line_width NUMERIC CHECK (line_width IS NULL OR line_width > 0),
    line_dash JSONB CHECK (line_dash IS NULL OR jsonb_typeof(line_dash) = 'array'),
    halo BOOLEAN,
    icon_key TEXT,
    label_field TEXT CHECK (label_field IS NULL OR label_field IN ('code', 'name')),
    label_min_zoom INT CHECK (label_min_zoom IS NULL OR label_min_zoom BETWEEN 0 AND 22),
    origin TEXT NOT NULL CHECK (origin IN ('FACTORY', 'TENANT')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_style_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_gis_style_type
        FOREIGN KEY (tenant_id, feature_type_id)
        REFERENCES gis_feature_type (tenant_id, id)
);

SELECT register_tenant_table('gis_style');

CREATE UNIQUE INDEX IF NOT EXISTS uq_gis_style_scope
    ON gis_style (tenant_id, origin,
        COALESCE(domain_key, ''),
        COALESCE(feature_type_id, '00000000-0000-0000-0000-000000000000'::uuid),
        lifecycle_status,
        COALESCE(accuracy_status, ''));

CREATE INDEX IF NOT EXISTS idx_gis_style_lookup
    ON gis_style (tenant_id, feature_type_id, lifecycle_status) WHERE active;

-- Padrao Ziip de fabrica (§10.2): por dominio e estado — em operacao,
-- traco continuo na cor do dominio; projetado, tracejado; em obra,
-- traco e ponto; abandonado, cinza (token map-abandoned, a registrar
-- na paleta do ADR-0017) tracejado fino. Estado nunca so por cor:
-- o padrao de traco muda junto. Fora da meta de precisao: halo.
INSERT INTO gis_style (tenant_id, domain_key, lifecycle_status,
        color_token, line_width, line_dash, halo, origin)
SELECT '${tenant_id}', d.key, s.lifecycle_status,
       CASE WHEN s.lifecycle_status = 'ABANDONED' THEN 'map-abandoned'
            ELSE d.map_color_token END,
       s.line_width, s.line_dash::jsonb, FALSE, 'FACTORY'
FROM gis_domain d
CROSS JOIN (VALUES
    ('IN_SERVICE', 2.0, '[]'),
    ('PLANNED', 2.0, '[3, 2]'),
    ('UNDER_CONSTRUCTION', 2.0, '[4, 2, 1, 2]'),
    ('ABANDONED', 1.0, '[2, 2]')
) AS s(lifecycle_status, line_width, line_dash)
ON CONFLICT DO NOTHING;

-- Precisao (§10.2): fora da meta ganha halo; aproximado mantem a cor
-- e o marcador proprio vem do icon_key do tipo na legenda/estilo.
INSERT INTO gis_style (tenant_id, domain_key, lifecycle_status,
        accuracy_status, halo, origin)
SELECT '${tenant_id}', d.key, 'IN_SERVICE', 'OUT_OF_TARGET', TRUE, 'FACTORY'
FROM gis_domain d
ON CONFLICT DO NOTHING;

-- Generica (sem dominio nem tipo): o ultimo recurso da resolucao.
INSERT INTO gis_style (tenant_id, domain_key, lifecycle_status,
        color_token, line_width, line_dash, halo, origin)
VALUES
    ('${tenant_id}', NULL, 'IN_SERVICE', 'map-context', 2.0, '[]'::jsonb, FALSE, 'FACTORY'),
    ('${tenant_id}', NULL, 'PLANNED', 'map-context', 2.0, '[3, 2]'::jsonb, FALSE, 'FACTORY'),
    ('${tenant_id}', NULL, 'UNDER_CONSTRUCTION', 'map-context', 2.0, '[4, 2, 1, 2]'::jsonb, FALSE, 'FACTORY'),
    ('${tenant_id}', NULL, 'ABANDONED', 'map-abandoned', 1.0, '[2, 2]'::jsonb, FALSE, 'FACTORY')
ON CONFLICT DO NOTHING;

-- ======================================================================
-- (3) gis_basemap e gis_wms_source (§3.10, §7.2, §10.3)
-- ======================================================================

CREATE TABLE IF NOT EXISTS gis_basemap (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    key TEXT NOT NULL,
    label TEXT NOT NULL,
    provider TEXT NOT NULL CHECK (provider IN ('OSM', 'CUSTOM_RASTER', 'CUSTOM_VECTOR')),
    tiles_url TEXT,
    style_url TEXT,
    attribution TEXT,
    license_ref TEXT,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    origin TEXT NOT NULL DEFAULT 'TENANT' CHECK (origin IN ('FACTORY', 'TENANT')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_basemap_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_basemap_tenant_key UNIQUE (tenant_id, key),
    CONSTRAINT ck_gis_basemap_license
        CHECK (provider = 'OSM' OR (license_ref IS NOT NULL AND btrim(license_ref) <> '')),
    CONSTRAINT ck_gis_basemap_source
        CHECK (tiles_url IS NOT NULL OR style_url IS NOT NULL)
);

SELECT register_tenant_table('gis_basemap');

CREATE UNIQUE INDEX IF NOT EXISTS uq_gis_basemap_one_default
    ON gis_basemap (tenant_id) WHERE is_default AND active;

INSERT INTO gis_basemap (tenant_id, key, label, provider, tiles_url,
        attribution, is_default, origin, sort_order)
VALUES ('${tenant_id}', 'osm', 'OpenStreetMap', 'OSM',
        'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
        '© OpenStreetMap contributors', TRUE, 'FACTORY', 1)
ON CONFLICT (tenant_id, key) DO NOTHING;

CREATE TABLE IF NOT EXISTS gis_wms_source (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    label TEXT NOT NULL,
    url TEXT NOT NULL,
    layers TEXT NOT NULL,
    wms_version TEXT NOT NULL DEFAULT '1.3.0',
    format TEXT NOT NULL DEFAULT 'image/png',
    transparent BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_wms_source_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_wms_source_tenant_label UNIQUE (tenant_id, label)
);

SELECT register_tenant_table('gis_wms_source');

-- ======================================================================
-- (4) gis_tile_dirty e a marcacao na aprovacao de pacote (§10.1)
-- ======================================================================

CREATE TABLE IF NOT EXISTS gis_tile_dirty (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    layer_key TEXT NOT NULL,
    z INT NOT NULL CHECK (z BETWEEN 0 AND 22),
    x INT NOT NULL CHECK (x >= 0),
    y INT NOT NULL CHECK (y >= 0),
    change_set_id UUID,
    reason TEXT NOT NULL DEFAULT 'CHANGE_SET_APPLIED' CHECK (reason IN
        ('CHANGE_SET_APPLIED', 'MANUAL')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT uq_gis_tile_dirty_tenant_id UNIQUE (tenant_id, id)
);

-- Correcao pos-revisao M2: a gis_tile_dirty entra no mesmo regime
-- das irmas (RLS via register_tenant_table); a versao original
-- desta migration a criava sem registro, restando so o filtro
-- explicito de tenant nas consultas do servico.
SELECT register_tenant_table('gis_tile_dirty');

CREATE UNIQUE INDEX IF NOT EXISTS uq_gis_tile_dirty_pending
    ON gis_tile_dirty (tenant_id, layer_key, z, x, y)
    WHERE processed_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_gis_tile_dirty_pending_age
    ON gis_tile_dirty (created_at) WHERE processed_at IS NULL;

-- Faixa de tiles (x/y) que um envelope em geom_web (3857) toca no
-- zoom p_z — matematica Web Mercator padrao sobre o envelope em
-- 4326, com latitude limitada a faixa do Mercator esferico.
CREATE OR REPLACE FUNCTION gis_tile_range(p_geom_web geometry, p_z INT)
RETURNS TABLE (x_min INT, x_max INT, y_min INT, y_max INT) AS $$
DECLARE
    v_env geometry := ST_Envelope(ST_Transform(p_geom_web, 4326));
    v_n INT := (2 ^ p_z)::INT;
    v_lon_min DOUBLE PRECISION := ST_XMin(v_env);
    v_lon_max DOUBLE PRECISION := ST_XMax(v_env);
    v_lat_min DOUBLE PRECISION := GREATEST(ST_YMin(v_env), -85.05112878);
    v_lat_max DOUBLE PRECISION := LEAST(ST_YMax(v_env), 85.05112878);
    v_rad DOUBLE PRECISION;
BEGIN
    x_min := GREATEST(0, LEAST(v_n - 1,
        floor((v_lon_min + 180.0) / 360.0 * v_n)::INT));
    x_max := GREATEST(0, LEAST(v_n - 1,
        floor((v_lon_max + 180.0) / 360.0 * v_n)::INT));
    v_rad := v_lat_max * pi() / 180.0;
    y_min := GREATEST(0, LEAST(v_n - 1,
        floor((1.0 - ln(tan(v_rad) + 1.0 / cos(v_rad)) / pi()) / 2.0 * v_n)::INT));
    v_rad := v_lat_min * pi() / 180.0;
    y_max := GREATEST(0, LEAST(v_n - 1,
        floor((1.0 - ln(tan(v_rad) + 1.0 / cos(v_rad)) / pi()) / 2.0 * v_n)::INT));
    RETURN NEXT;
END;
$$ LANGUAGE plpgsql STABLE;

-- Marca os tiles afetados por um pacote APLICADO e sobe a versao
-- das camadas atingidas. Teto de seguranca: se uma faixa passar de
-- 4096 tiles num zoom, a linha daquele zoom e pulada — a versao
-- (que e a garantia) ja subiu, e a eviccao fisica daquele zoom fica
-- para a varredura por versao do worker; nada velho volta a ser
-- servido porque a chave de cache carrega a versao nova.
CREATE OR REPLACE FUNCTION gis_mark_change_set_tiles_dirty(p_change_set_id UUID)
RETURNS INT AS $$
DECLARE
    v_tenant UUID;
    v_schema TEXT := current_schema();
    v_marked INT := 0;
    v_row RECORD;
    v_range RECORD;
    v_z INT;
    v_count INT;
    v_bumped TEXT[] := '{}';
BEGIN
    PERFORM set_config('search_path', v_schema || ', public', true);
    SELECT tenant_id INTO v_tenant FROM gis_change_set WHERE id = p_change_set_id;
    IF v_tenant IS NULL THEN
        RAISE EXCEPTION 'gis: pacote % nao encontrado para marcar tiles', p_change_set_id;
    END IF;

    FOR v_row IN
        SELECT DISTINCT l.key AS layer_key, g.geom_web AS geom_web
        FROM gis_change_set_item i
        JOIN gis_layer l
          ON l.tenant_id = v_tenant AND l.active
         AND l.source_kind = 'FEATURE' AND l.feature_table = i.feature_table
        JOIN LATERAL (
            SELECT geom_web FROM gis_node n
             WHERE i.feature_table = 'gis_node' AND n.id = i.feature_id AND n.tenant_id = v_tenant
            UNION ALL
            SELECT geom_web FROM gis_arc a
             WHERE i.feature_table = 'gis_arc' AND a.id = i.feature_id AND a.tenant_id = v_tenant
            UNION ALL
            SELECT geom_web FROM gis_connection c
             WHERE i.feature_table = 'gis_connection' AND c.id = i.feature_id AND c.tenant_id = v_tenant
            UNION ALL
            SELECT geom_web FROM gis_structure s
             WHERE i.feature_table = 'gis_structure' AND s.id = i.feature_id AND s.tenant_id = v_tenant
            UNION ALL
            SELECT geom_web FROM gis_zone zn
             WHERE i.feature_table = 'gis_zone' AND zn.id = i.feature_id AND zn.tenant_id = v_tenant
            UNION ALL
            SELECT geom_web FROM gis_poi p
             WHERE i.feature_table = 'gis_poi' AND p.id = i.feature_id AND p.tenant_id = v_tenant
        ) g ON g.geom_web IS NOT NULL
        WHERE i.change_set_id = p_change_set_id AND i.tenant_id = v_tenant
          AND (l.domain_key IS NULL OR l.domain_key = (
                SELECT fc.domain FROM gis_feature_type ft
                JOIN gis_feature_class fc
                  ON fc.id = ft.class_id AND fc.tenant_id = ft.tenant_id
                WHERE ft.tenant_id = v_tenant AND ft.id = (
                    SELECT feature_type_id FROM (
                        SELECT feature_type_id FROM gis_node n2
                         WHERE i.feature_table = 'gis_node' AND n2.id = i.feature_id AND n2.tenant_id = v_tenant
                        UNION ALL SELECT feature_type_id FROM gis_arc a2
                         WHERE i.feature_table = 'gis_arc' AND a2.id = i.feature_id AND a2.tenant_id = v_tenant
                        UNION ALL SELECT feature_type_id FROM gis_connection c2
                         WHERE i.feature_table = 'gis_connection' AND c2.id = i.feature_id AND c2.tenant_id = v_tenant
                        UNION ALL SELECT feature_type_id FROM gis_structure s2
                         WHERE i.feature_table = 'gis_structure' AND s2.id = i.feature_id AND s2.tenant_id = v_tenant
                        UNION ALL SELECT feature_type_id FROM gis_zone z2
                         WHERE i.feature_table = 'gis_zone' AND z2.id = i.feature_id AND z2.tenant_id = v_tenant
                        UNION ALL SELECT feature_type_id FROM gis_poi p2
                         WHERE i.feature_table = 'gis_poi' AND p2.id = i.feature_id AND p2.tenant_id = v_tenant
                    ) one LIMIT 1)))
    LOOP
        -- Correcao pos-revisao B1: a versao sobe UMA vez por camada
        -- por aprovacao (a semantica e "versao do pacote"), nao uma
        -- vez por feicao distinta da mesma camada.
        IF NOT (v_row.layer_key = ANY (v_bumped)) THEN
            UPDATE gis_layer SET layer_version = layer_version + 1,
                   updated_at = clock_timestamp()
             WHERE tenant_id = v_tenant AND key = v_row.layer_key;
            v_bumped := array_append(v_bumped, v_row.layer_key);
        END IF;
        FOR v_z IN 10..18 LOOP
            SELECT * INTO v_range FROM gis_tile_range(v_row.geom_web, v_z);
            IF (v_range.x_max - v_range.x_min + 1)::BIGINT
               * (v_range.y_max - v_range.y_min + 1) > 4096 THEN
                CONTINUE;
            END IF;
            INSERT INTO gis_tile_dirty (tenant_id, layer_key, z, x, y, change_set_id)
            SELECT v_tenant, v_row.layer_key, v_z, gx, gy, p_change_set_id
            FROM generate_series(v_range.x_min, v_range.x_max) gx
            CROSS JOIN generate_series(v_range.y_min, v_range.y_max) gy
            ON CONFLICT DO NOTHING;
            GET DIAGNOSTICS v_count = ROW_COUNT;
            v_marked := v_marked + v_count;
        END LOOP;
    END LOOP;
    RETURN v_marked;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION gis_on_change_set_applied() RETURNS TRIGGER AS $$
BEGIN
    PERFORM set_config('search_path', TG_TABLE_SCHEMA || ', public', true);
    PERFORM gis_mark_change_set_tiles_dirty(NEW.id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS gis_tiles_dirty_on_apply ON gis_change_set;
CREATE TRIGGER gis_tiles_dirty_on_apply
    AFTER UPDATE OF status ON gis_change_set
    FOR EACH ROW
    WHEN (NEW.status = 'APPLIED' AND OLD.status IS DISTINCT FROM 'APPLIED')
    EXECUTE FUNCTION gis_on_change_set_applied();
