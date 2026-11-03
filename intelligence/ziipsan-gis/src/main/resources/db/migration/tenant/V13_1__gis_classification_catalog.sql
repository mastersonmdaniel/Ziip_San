-- GIS-01 (P1.26) — classificacao e catalogo do nucleo do GIS.
--
-- Cria as tabelas de classificacao e catalogo (GIS-CADASTRO-TECNICO §3.2 e §3.4):
-- gis_domain, gis_feature_class, gis_feature_type, gis_material, gis_catalog_item,
-- gis_value_list, gis_connectivity_rule, gis_quality_target e gis_responsible_party.
-- As feicoes, o historico e a migracao do gis_network_element ficam na V13_2.
--
-- RLS: como em toda migration de tenant, a RLS e FORCE tambem para o migrador. O seed
-- abaixo so enxerga/grava o tenant da migration por causa da GUC definida na primeira
-- linha (mesmo padrao da V2_8 do MTR). Sem ela, o INSERT esbarraria no WITH CHECK da
-- policy e a migration falharia em vez de semear silenciosamente nada.
--
-- gis_domain substitui a lista fixa de domain (plano v3.0 §3, ajuste v2.13): a coluna
-- package diz de qual pacote de vertical o dominio veio; a visibilidade por pacote e a
-- view gis_domain_visible (so o dominio aparece para o tenant que tem o pacote ativo
-- em platform.tenant_vertical). A view so e criada se platform.tenant_vertical existir:
-- o core V6_1 (P0.47) a cria, mas uma migration de tenant nao pode depender de uma
-- tabela do core que ainda nao foi aplicada. Sem ela, a tabela base continua valendo e
-- a view passa a existir na proxima aplicacao que a encontrar (reaplicacao do Flyway
-- nao reaplica migrations versionadas; ver a ressalva no relatorio da P1.26).

SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- ======================================================================
-- gis_domain
-- ======================================================================

CREATE TABLE IF NOT EXISTS gis_domain (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    key TEXT NOT NULL,
    package TEXT NOT NULL,
    label TEXT NOT NULL,
    map_color_token TEXT NOT NULL,
    sort_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_domain_tenant_key UNIQUE (tenant_id, key)
);

SELECT register_tenant_table('gis_domain');

-- Cinco dominios do pacote sanitation (plano v3.0, P1.26; GIS-CADASTRO-TECNICO §3.4).
-- map_color_token segue o vocabulario de tokens map-* do ADR-0017; os valores exatos
-- de cor por tema sao da P1.30 (simbologia), nao desta migration.
INSERT INTO gis_domain (tenant_id, key, package, label, map_color_token, sort_order)
VALUES
    ('${tenant_id}', 'WATER', 'sanitation', 'Água', 'map-water', 1),
    ('${tenant_id}', 'SEWER', 'sanitation', 'Esgoto', 'map-sewer', 2),
    ('${tenant_id}', 'STORM', 'sanitation', 'Drenagem', 'map-storm', 3),
    ('${tenant_id}', 'WASTE', 'sanitation', 'Resíduos sólidos', 'map-waste', 4),
    ('${tenant_id}', 'CONTEXT', 'sanitation', 'Contexto', 'map-context', 5)
ON CONFLICT (tenant_id, key) DO NOTHING;

DO $$
BEGIN
    IF to_regclass('platform.tenant_vertical') IS NOT NULL THEN
        EXECUTE $view$
            CREATE OR REPLACE VIEW gis_domain_visible
            WITH (security_invoker = true) AS
            SELECT d.*
            FROM gis_domain d
            WHERE EXISTS (
                SELECT 1 FROM platform.tenant_vertical tv
                WHERE tv.tenant_id = d.tenant_id
                  AND tv.vertical_id = d.package
                  AND tv.state = 'ACTIVE'
            )
        $view$;
    END IF;
END
$$;

-- ======================================================================
-- gis_feature_class
-- ======================================================================

CREATE TABLE IF NOT EXISTS gis_feature_class (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code TEXT NOT NULL,
    domain TEXT NOT NULL,
    topology_role TEXT NOT NULL CHECK (topology_role IN
        ('NODE', 'ARC', 'CONNECTION', 'STRUCTURE', 'ZONE', 'POI')),
    geometry_type TEXT NOT NULL,
    is_isolating BOOLEAN NOT NULL DEFAULT FALSE,
    hydraulic_role TEXT,
    origin TEXT NOT NULL CHECK (origin IN ('FACTORY', 'TENANT')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_feature_class_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_feature_class_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_gis_feature_class_domain
        FOREIGN KEY (tenant_id, domain) REFERENCES gis_domain (tenant_id, key)
);

SELECT register_tenant_table('gis_feature_class');

-- ======================================================================
-- gis_feature_type
-- ======================================================================

CREATE TABLE IF NOT EXISTS gis_feature_type (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    class_id UUID NOT NULL,
    code TEXT NOT NULL,
    label TEXT NOT NULL,
    attributes_schema JSONB NOT NULL DEFAULT '{}',
    schema_version INT NOT NULL DEFAULT 1,
    terminal_config JSONB,
    min_zoom INT,
    style_key TEXT,
    origin TEXT NOT NULL CHECK (origin IN ('FACTORY', 'TENANT')),
    factory_code TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    approval_status TEXT,
    requires_catalog_item BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_feature_type_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_feature_type_tenant_class_code UNIQUE (tenant_id, class_id, code),
    CONSTRAINT fk_gis_feature_type_class
        FOREIGN KEY (tenant_id, class_id) REFERENCES gis_feature_class (tenant_id, id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_gis_feature_type_tenant_factory_code
    ON gis_feature_type (tenant_id, factory_code) WHERE factory_code IS NOT NULL;

SELECT register_tenant_table('gis_feature_type');

-- ======================================================================
-- gis_material
-- ======================================================================

CREATE TABLE IF NOT EXISTS gis_material (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code TEXT NOT NULL,
    family TEXT,
    standard_ref TEXT,
    hazen_williams_c NUMERIC,
    manning_n NUMERIC,
    roughness_mm NUMERIC,
    service_life_years INT,
    legacy_only BOOLEAN NOT NULL DEFAULT FALSE,
    origin TEXT NOT NULL CHECK (origin IN ('FACTORY', 'TENANT')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_material_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_material_tenant_code UNIQUE (tenant_id, code)
);

SELECT register_tenant_table('gis_material');

-- ======================================================================
-- gis_catalog_item
-- ======================================================================

CREATE TABLE IF NOT EXISTS gis_catalog_item (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    feature_type_id UUID NOT NULL,
    material_id UUID,
    dn_mm INT,
    dn2_mm INT,
    angle_deg NUMERIC,
    pressure_class TEXT,
    stiffness_class TEXT,
    joint_type TEXT,
    internal_diameter_mm NUMERIC,
    external_diameter_mm NUMERIC,
    sdr NUMERIC,
    section_shape TEXT,
    geom1 NUMERIC,
    geom2 NUMERIC,
    geom3 NUMERIC,
    geom4 NUMERIC,
    manufacturer TEXT,
    model TEXT,
    standard_ref TEXT,
    reference_status TEXT NOT NULL DEFAULT 'REFERENCE_TO_VALIDATE'
        CHECK (reference_status IN ('STANDARD', 'REFERENCE_TO_VALIDATE', 'TENANT')),
    origin TEXT NOT NULL CHECK (origin IN ('FACTORY', 'TENANT')),
    factory_code TEXT,
    factory_retired_on DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_catalog_item_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_gis_catalog_item_type
        FOREIGN KEY (tenant_id, feature_type_id) REFERENCES gis_feature_type (tenant_id, id),
    CONSTRAINT fk_gis_catalog_item_material
        FOREIGN KEY (tenant_id, material_id) REFERENCES gis_material (tenant_id, id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_gis_catalog_item_tenant_factory_code
    ON gis_catalog_item (tenant_id, factory_code) WHERE factory_code IS NOT NULL;

SELECT register_tenant_table('gis_catalog_item');

-- ======================================================================
-- gis_value_list
-- ======================================================================

CREATE TABLE IF NOT EXISTS gis_value_list (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    list_code TEXT NOT NULL,
    code TEXT NOT NULL,
    label TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    origin TEXT NOT NULL CHECK (origin IN ('FACTORY', 'TENANT')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_value_list_tenant_list_code UNIQUE (tenant_id, list_code, code)
);

SELECT register_tenant_table('gis_value_list');

-- ======================================================================
-- gis_connectivity_rule
-- ======================================================================

CREATE TABLE IF NOT EXISTS gis_connectivity_rule (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    from_type_id UUID,
    from_class_id UUID,
    to_type_id UUID,
    to_class_id UUID,
    relation TEXT NOT NULL CHECK (relation IN ('CONNECTS', 'CONTAINS', 'ATTACHES')),
    min_arcs INT,
    max_arcs INT,
    condition JSONB,
    severity TEXT NOT NULL DEFAULT 'ERROR' CHECK (severity IN ('ERROR', 'WARNING')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_connectivity_rule_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_gis_connectivity_rule_from_one
        CHECK ((from_type_id IS NULL) <> (from_class_id IS NULL)),
    CONSTRAINT ck_gis_connectivity_rule_to_one
        CHECK ((to_type_id IS NULL) <> (to_class_id IS NULL)),
    CONSTRAINT fk_gis_connectivity_rule_from_type
        FOREIGN KEY (tenant_id, from_type_id) REFERENCES gis_feature_type (tenant_id, id),
    CONSTRAINT fk_gis_connectivity_rule_from_class
        FOREIGN KEY (tenant_id, from_class_id) REFERENCES gis_feature_class (tenant_id, id),
    CONSTRAINT fk_gis_connectivity_rule_to_type
        FOREIGN KEY (tenant_id, to_type_id) REFERENCES gis_feature_type (tenant_id, id),
    CONSTRAINT fk_gis_connectivity_rule_to_class
        FOREIGN KEY (tenant_id, to_class_id) REFERENCES gis_feature_class (tenant_id, id)
);

SELECT register_tenant_table('gis_connectivity_rule');

-- ======================================================================
-- gis_quality_target
-- ======================================================================

CREATE TABLE IF NOT EXISTS gis_quality_target (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    class_id UUID NOT NULL,
    h_target_m NUMERIC NOT NULL,
    v_target_m NUMERIC,
    valid_from DATE NOT NULL,
    valid_to DATE,
    proposed_by UUID,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_quality_target_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_gis_quality_target_validity
        CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT fk_gis_quality_target_class
        FOREIGN KEY (tenant_id, class_id) REFERENCES gis_feature_class (tenant_id, id)
);

SELECT register_tenant_table('gis_quality_target');

-- ======================================================================
-- gis_responsible_party
-- ======================================================================
-- Responsavel tecnico do levantamento ou do projeto (GIS-CADASTRO-TECNICO §3.4).
-- Nome de profissional, com a finalidade registrada em purpose: sem finalidade, a
-- linha nao entra. Nao e cadastro de cliente nem de consumidor.

CREATE TABLE IF NOT EXISTS gis_responsible_party (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    name TEXT NOT NULL,
    registry_type TEXT CHECK (registry_type IN ('CREA', 'CAU', 'CFT')),
    registry TEXT,
    art_rrt TEXT,
    purpose TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_responsible_party_tenant_id UNIQUE (tenant_id, id)
);

SELECT register_tenant_table('gis_responsible_party');

-- ======================================================================
-- Validacao de attributes contra o JSON Schema do tipo (GIS-CADASTRO-TECNICO §3.3)
-- ======================================================================
-- pg_jsonschema nao esta disponivel neste banco (nem no RDS, a confirmar na LC.1),
-- por isso o subconjunto abaixo e PL/pgSQL, como o proprio §3.3 preve como fallback:
-- required, type (string, integer, number, boolean, object, array, null), enum e
-- $ref no formato "gis_value_list:<list_code>" (lista de gis_value_list). A api
-- repete a mesma validacao em Java (GisAttributesValidator) para dar mensagem por
-- campo; o banco e a ultima linha, porque ha escrita fora da api (QGIS, importacao).
-- additionalProperties: false recusa chave nao declarada em properties; sem ele, o
-- comportamento padrao do JSON Schema vale (chave extra permitida).

CREATE OR REPLACE FUNCTION gis_validate_attributes(
    p_feature_type_id UUID,
    p_tenant_id UUID,
    p_attributes JSONB
) RETURNS VOID AS $$
DECLARE
    v_schema JSONB;
    v_required TEXT;
    v_key TEXT;
    v_prop JSONB;
    v_value JSONB;
    v_type TEXT;
    v_enum JSONB;
    v_ref TEXT;
    v_list_code TEXT;
    v_additional BOOLEAN;
BEGIN
    SELECT attributes_schema INTO v_schema
    FROM gis_feature_type
    WHERE id = p_feature_type_id AND tenant_id = p_tenant_id;

    IF v_schema IS NULL THEN
        RAISE EXCEPTION 'gis: tipo de feicao % nao encontrado no tenant', p_feature_type_id;
    END IF;

    IF p_attributes IS NULL THEN
        RAISE EXCEPTION 'gis: attributes nulo';
    END IF;

    FOR v_required IN
        SELECT jsonb_array_elements_text(COALESCE(v_schema -> 'required', '[]'::jsonb))
    LOOP
        IF NOT (p_attributes ? v_required) THEN
            RAISE EXCEPTION 'gis: attributes sem a chave obrigatoria "%"', v_required;
        END IF;
    END LOOP;

    IF v_schema ? 'properties' THEN
        FOR v_key, v_prop IN
            SELECT key, value FROM jsonb_each(v_schema -> 'properties')
        LOOP
            IF p_attributes ? v_key THEN
                v_value := p_attributes -> v_key;

                IF v_prop ? '$ref' THEN
                    v_ref := v_prop ->> '$ref';
                    IF v_ref NOT LIKE 'gis_value_list:%' THEN
                        RAISE EXCEPTION 'gis: $ref nao suportado em "%": %', v_key, v_ref;
                    END IF;
                    v_list_code := substr(v_ref, length('gis_value_list:') + 1);
                    IF jsonb_typeof(v_value) <> 'string' THEN
                        RAISE EXCEPTION 'gis: "%" deve ser texto da lista %', v_key, v_list_code;
                    END IF;
                    IF NOT EXISTS (
                        SELECT 1 FROM gis_value_list
                        WHERE tenant_id = p_tenant_id
                          AND list_code = v_list_code
                          AND code = (v_value #>> '{}')
                          AND active
                    ) THEN
                        RAISE EXCEPTION 'gis: "%" fora da lista %: %', v_key, v_list_code, (v_value #>> '{}');
                    END IF;
                END IF;

                IF v_prop ? 'enum' THEN
                    v_enum := v_prop -> 'enum';
                    IF NOT EXISTS (
                        SELECT 1 FROM jsonb_array_elements(v_enum) e
                        WHERE e.value = v_value
                    ) THEN
                        RAISE EXCEPTION 'gis: "%" fora do enum do tipo', v_key;
                    END IF;
                END IF;

                IF v_prop ? 'type' THEN
                    v_type := v_prop ->> 'type';
                    IF v_type = 'string' AND jsonb_typeof(v_value) <> 'string' THEN
                        RAISE EXCEPTION 'gis: "%" deve ser texto', v_key;
                    ELSIF v_type = 'boolean' AND jsonb_typeof(v_value) <> 'boolean' THEN
                        RAISE EXCEPTION 'gis: "%" deve ser booleano', v_key;
                    ELSIF v_type = 'object' AND jsonb_typeof(v_value) <> 'object' THEN
                        RAISE EXCEPTION 'gis: "%" deve ser objeto', v_key;
                    ELSIF v_type = 'array' AND jsonb_typeof(v_value) <> 'array' THEN
                        RAISE EXCEPTION 'gis: "%" deve ser lista', v_key;
                    ELSIF v_type = 'null' AND jsonb_typeof(v_value) <> 'null' THEN
                        RAISE EXCEPTION 'gis: "%" deve ser nulo', v_key;
                    ELSIF v_type = 'number' AND jsonb_typeof(v_value) <> 'number' THEN
                        RAISE EXCEPTION 'gis: "%" deve ser numero', v_key;
                    ELSIF v_type = 'integer' AND (
                        jsonb_typeof(v_value) <> 'number'
                        OR (v_value #>> '{}') ~ '\.|e|E'
                    ) THEN
                        RAISE EXCEPTION 'gis: "%" deve ser inteiro', v_key;
                    END IF;
                END IF;
            END IF;
        END LOOP;

        v_additional := (v_schema ->> 'additionalProperties') = 'false';
        IF v_additional THEN
            FOR v_key IN SELECT jsonb_object_keys(p_attributes) LOOP
                IF NOT (v_schema -> 'properties' ? v_key) THEN
                    RAISE EXCEPTION 'gis: attributes com chave nao declarada no tipo: "%"', v_key;
                END IF;
            END LOOP;
        END IF;
    END IF;
END;
$$ LANGUAGE plpgsql;
