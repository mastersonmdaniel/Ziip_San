-- P1.35 — cadastro minimo de ativos e hierarquia de seis niveis (plano v3.0 §3,
-- reserva V27_1). Modulo dono: operations/ziipsan-asset.
--
-- Hierarquia (ALVOS-DA-OS-E-HIERARQUIA-DE-ATIVOS §2, ISO 14224 como analogia
-- tecnica): 1 municipio e localidade, 2 sistema, 3 natureza e subdivisao do
-- servico, 4 instalacao, 5 equipamento, 6 componente (opcional). A peca (nivel 9
-- da ISO 14224) NAO e ativo: e material consumido pela OS (ED-58).
--
-- RESSALVAS REGISTRADAS NESTA MIGRATION (nao inventadas como decisoes):
-- 1. Tamanho de UP: o manual e internamente divergente — a tabela 4 da 2 digitos
--    e a figura 2 da 3 (ALVOS §5; plano v3.0, pendencia P1.35). A versao 2 de 2025
--    do manual da ARSESP (Deliberacao 1.636/2025), que revisou as tabelas, nao foi
--    lida por este projeto. Ate a conferencia, a coluna aceita 2 OU 3 digitos
--    (CHECK ^\d{2,3}$): acomoda as duas formas documentadas sem inventar uma.
-- 2. NTS-SDS: o MCPSB fixa 19 codigos, mas a lista fechada de codigos nao esta
--    reproduzida em nenhum documento lido do projeto; semear 19 siglas seria
--    inventar dado regulatorio. A coluna valida so o formato; a lista fechada
--    entra com o inventario regulatorio da ED-56, que a confere na tabela
--    original do MCPSB antes de virar dado do produto.
-- 3. Par UP/UAR por tipo: a UAR e atribuida pela entidade reguladora e a
--    numeracao das UP do MCPSB difere da da ARSESP (ALVOS §5). Nenhum numero de
--    UP/UAR e semeado aqui: asset_type_mcpsb nasce vazia e o tenant/agencia a
--    preenche; quando a linha existe, o ativo precisa respeitar o par (regra do
--    servico). Vida util, custo historico, inventario completo, conciliacao,
--    manutencao e risco sao da ED-56; ISO 55001, da ED-72; catalogo de tipos,
--    da P1.27 (aqui so se referencia a ele).
-- 4. Poco umido: o catalogo de fabrica da P1.27 nao tem tipo proprio WET_WELL;
--    o poco umido vive na elevatoria de esgoto (SEWAGE_LIFT_STATION), que e o
--    tipo semeado como espaco confinado para esse caso.
-- 5. asset_codes JSONB (V13_2) continua existindo nas feicoes por ser migration
--    aplicada (nao editada); o que esta migration garante e que nenhum codigo
--    MCPSB pode entrar ali: o gatilho abaixo recusa as chaves patrimoniais, e a
--    feicao liga-se ao ativo pela FK asset_id. O codigo patrimonial so existe
--    na tabela asset.
--
-- RLS: como em toda migration de tenant, a GUC abaixo e obrigatoria, porque a
-- RLS e FORCE tambem para o migrador e os INSERTs de seed esbarrariam no
-- WITH CHECK sem ela. O placeholder ${tenant_id} e substituido pelo
-- TenantMigrator (mesmo padrao da V13_2).

SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- ======================================================================
-- Classes de equipamento (ALVOS §4.4) e suas fronteiras (ISO 14224,
-- clausula 8.1, como analogia tecnica: fronteiras sem sobreposicao).
-- A sobreposicao e impedida pela unicidade de asset_type_mcpsb por tipo:
-- um tipo de catalogo pertence a no maximo uma classe.
-- ======================================================================

CREATE TABLE IF NOT EXISTS asset_equipment_class (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code TEXT NOT NULL,
    label TEXT NOT NULL,
    boundary_text TEXT NOT NULL,
    origin TEXT NOT NULL CHECK (origin IN ('FACTORY', 'TENANT')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_asset_equipment_class_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_asset_equipment_class_tenant_code UNIQUE (tenant_id, code)
);

SELECT register_tenant_table('asset_equipment_class');

-- ======================================================================
-- Correspondencia tipo de catalogo -> par UP/UAR do MCPSB (P1.27 le o tipo;
-- esta tabela guarda o par). Sem seed de numeros: ver ressalva 3 acima.
-- ======================================================================

CREATE TABLE IF NOT EXISTS asset_type_mcpsb (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    catalog_type_code TEXT NOT NULL,
    equipment_class_code TEXT NOT NULL,
    up VARCHAR(3) CHECK (up IS NULL OR up ~ '^\d{2,3}$'),
    uar VARCHAR(7) CHECK (uar IS NULL OR uar ~ '^\d{7}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_asset_type_mcpsb_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_asset_type_mcpsb_tenant_type UNIQUE (tenant_id, catalog_type_code),
    CONSTRAINT fk_asset_type_mcpsb_class
        FOREIGN KEY (tenant_id, equipment_class_code)
        REFERENCES asset_equipment_class (tenant_id, code)
);

SELECT register_tenant_table('asset_type_mcpsb');

-- ======================================================================
-- Tipos propensos a espaco confinado (NR-33, 33.3.3, a: cadastro de todos
-- os espacos confinados). Codigos do catalogo de fabrica da P1.27; o servico
-- so aceita confined_space = TRUE num tipo desta lista.
-- ======================================================================

CREATE TABLE IF NOT EXISTS asset_confined_space_type (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    catalog_type_code TEXT NOT NULL,
    note TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_asset_confined_space_type_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_asset_confined_space_type_tenant_code UNIQUE (tenant_id, catalog_type_code)
);

SELECT register_tenant_table('asset_confined_space_type');

-- ======================================================================
-- asset
-- ======================================================================

CREATE TABLE IF NOT EXISTS asset (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code TEXT NOT NULL,
    parent_asset_id UUID,
    hierarchy_level INT NOT NULL CHECK (hierarchy_level BETWEEN 1 AND 6),
    asset_class TEXT,
    catalog_type_code TEXT NOT NULL,
    gis_feature_kind TEXT CHECK (gis_feature_kind IN
        ('NODE', 'ARC', 'CONNECTION', 'STRUCTURE', 'ZONE', 'POI')),
    gis_feature_id UUID,
    is_building BOOLEAN NOT NULL DEFAULT FALSE,
    confined_space BOOLEAN NOT NULL DEFAULT FALSE,
    -- Codigos MCPSB da NR 16/2026 da ANA (Resolucao ANA 302/2026). So aqui:
    -- nenhuma outra tabela do tenant guarda codigo patrimonial.
    cib VARCHAR(25),
    loc_ibge VARCHAR(7) NOT NULL,
    ac VARCHAR(6),
    nts_sds TEXT,
    up VARCHAR(3),
    uar VARCHAR(7),
    at1 VARCHAR(2) NOT NULL DEFAULT '00',
    at2 VARCHAR(2) NOT NULL DEFAULT '00',
    at3 VARCHAR(2) NOT NULL DEFAULT '00',
    at4 VARCHAR(2) NOT NULL DEFAULT '00',
    at5 VARCHAR(2) NOT NULL DEFAULT '00',
    control_mode TEXT NOT NULL CHECK (control_mode IN ('INDIVIDUAL', 'MASS')),
    unit_of_measure TEXT,
    bcp BOOLEAN NOT NULL DEFAULT FALSE,
    brv BOOLEAN NOT NULL DEFAULT FALSE,
    -- Ficha tecnica minima.
    manufacturer TEXT,
    model TEXT,
    serial_number TEXT,
    manufacture_year INT CHECK (manufacture_year IS NULL
        OR manufacture_year BETWEEN 1900 AND 2200),
    power_kw NUMERIC CHECK (power_kw IS NULL OR power_kw > 0),
    capacity NUMERIC CHECK (capacity IS NULL OR capacity >= 0),
    capacity_unit TEXT,
    in_service_on DATE,
    lifecycle_status TEXT NOT NULL DEFAULT 'PLANNED'
        CHECK (lifecycle_status IN
            ('PLANNED', 'IN_SERVICE', 'OUT_OF_SERVICE', 'DECOMMISSIONED')),
    location_text TEXT,
    -- Proveniencia: toda inclusao e alteracao nasce de uma OS interna da
    -- P1.31. O servico exige a OS; o NOT NULL + FK impede a escrita direta
    -- sem OS no banco (teste de arquitetura da P1.35).
    created_via_order_id UUID NOT NULL,
    updated_via_order_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_asset_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_asset_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_asset_parent
        FOREIGN KEY (tenant_id, parent_asset_id) REFERENCES asset (tenant_id, id),
    CONSTRAINT fk_asset_class
        FOREIGN KEY (tenant_id, asset_class) REFERENCES asset_equipment_class (tenant_id, code),
    CONSTRAINT fk_asset_created_via_order
        FOREIGN KEY (tenant_id, created_via_order_id) REFERENCES service_orders (tenant_id, id),
    CONSTRAINT fk_asset_updated_via_order
        FOREIGN KEY (tenant_id, updated_via_order_id) REFERENCES service_orders (tenant_id, id),
    CONSTRAINT ck_asset_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT ck_asset_root
        CHECK ((hierarchy_level = 1 AND parent_asset_id IS NULL)
            OR (hierarchy_level > 1 AND parent_asset_id IS NOT NULL)),
    CONSTRAINT ck_asset_building_level
        CHECK (NOT is_building OR hierarchy_level = 4),
    CONSTRAINT ck_asset_gis_link
        CHECK ((gis_feature_kind IS NULL) = (gis_feature_id IS NULL)),
    CONSTRAINT ck_asset_cib
        CHECK ((control_mode = 'INDIVIDUAL' AND cib ~ '^\d{25}$')
            OR (control_mode = 'MASS' AND cib IS NULL)),
    CONSTRAINT ck_asset_loc_ibge CHECK (loc_ibge ~ '^\d{7}$'),
    CONSTRAINT ck_asset_ac CHECK (ac IS NULL OR ac ~ '^\d{6}$'),
    CONSTRAINT ck_asset_up CHECK (up IS NULL OR up ~ '^\d{2,3}$'),
    CONSTRAINT ck_asset_uar CHECK (uar IS NULL OR uar ~ '^\d{7}$'),
    CONSTRAINT ck_asset_at CHECK (at1 ~ '^\d{2}$' AND at2 ~ '^\d{2}$'
        AND at3 ~ '^\d{2}$' AND at4 ~ '^\d{2}$' AND at5 ~ '^\d{2}$'),
    CONSTRAINT ck_asset_nts_sds
        CHECK (nts_sds IS NULL OR nts_sds ~ '^[A-Z0-9_]{1,30}$'),
    CONSTRAINT ck_asset_mass_unit
        CHECK (control_mode <> 'MASS' OR unit_of_measure IS NOT NULL)
);

SELECT register_tenant_table('asset');

CREATE INDEX IF NOT EXISTS idx_asset_parent ON asset (tenant_id, parent_asset_id);
CREATE INDEX IF NOT EXISTS idx_asset_level ON asset (tenant_id, hierarchy_level);
CREATE INDEX IF NOT EXISTS idx_asset_confined ON asset (tenant_id, confined_space);
CREATE INDEX IF NOT EXISTS idx_asset_gis_feature ON asset (tenant_id, gis_feature_kind, gis_feature_id);
CREATE INDEX IF NOT EXISTS idx_asset_class ON asset (tenant_id, asset_class);

-- Guarda de hierarquia: o pai tem exatamente um nivel acima do filho.
-- Sem ela, o CHECK de raiz nao impediria equipamento sob sistema; com ela,
-- ciclo tambem e impossivel (o nivel cai a cada passo rumo a raiz, e a raiz
-- nao tem pai). Componente (6) so aceita pai equipamento (5) por construcao.
CREATE OR REPLACE FUNCTION asset_hierarchy_guard() RETURNS TRIGGER AS $$
DECLARE
    v_parent_level INT;
BEGIN
    IF NEW.parent_asset_id IS NULL THEN
        RETURN NEW;
    END IF;
    IF NEW.parent_asset_id = NEW.id THEN
        RAISE EXCEPTION 'asset cannot be its own parent' USING ERRCODE = '23514';
    END IF;
    SELECT hierarchy_level INTO v_parent_level FROM asset
    WHERE tenant_id = NEW.tenant_id AND id = NEW.parent_asset_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'asset parent % not found for tenant', NEW.parent_asset_id
            USING ERRCODE = '23503';
    END IF;
    IF v_parent_level <> NEW.hierarchy_level - 1 THEN
        RAISE EXCEPTION 'asset level % requires a level % parent (got %)',
            NEW.hierarchy_level, NEW.hierarchy_level - 1, v_parent_level
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_asset_hierarchy ON asset;
CREATE TRIGGER trg_asset_hierarchy
    BEFORE INSERT OR UPDATE OF parent_asset_id, hierarchy_level ON asset
    FOR EACH ROW EXECUTE FUNCTION asset_hierarchy_guard();

-- ======================================================================
-- Feicao do GIS e hidrometro referenciam o ativo por FK (DAD-26).
-- ======================================================================

DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['gis_node', 'gis_arc', 'gis_connection',
                             'gis_structure', 'gis_zone', 'gis_poi'] LOOP
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS asset_id UUID', t);
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
            WHERE c.conname = 'fk_' || t || '_asset' AND n.nspname = current_schema()
        ) THEN
            EXECUTE format('ALTER TABLE %I ADD CONSTRAINT fk_%s_asset '
                || 'FOREIGN KEY (tenant_id, asset_id) REFERENCES asset (tenant_id, id)', t, t);
        END IF;
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_asset ON %I (tenant_id, asset_id)', t, t);
    END LOOP;
    ALTER TABLE meters ADD COLUMN IF NOT EXISTS asset_id UUID;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
        WHERE c.conname = 'fk_meters_asset' AND n.nspname = current_schema()
    ) THEN
        ALTER TABLE meters ADD CONSTRAINT fk_meters_asset
            FOREIGN KEY (tenant_id, asset_id) REFERENCES asset (tenant_id, id);
    END IF;
    CREATE INDEX IF NOT EXISTS idx_meters_asset ON meters (tenant_id, asset_id);
END
$$;

-- Codigo patrimonial so no ativo: recusa as chaves MCPSB no asset_codes
-- JSONB das feicoes. O historico das feicoes copia a linha principal, que
-- ja passou por este gatilho, por isso so as tabelas principais o recebem.
CREATE OR REPLACE FUNCTION gis_asset_codes_no_mcpsb_guard() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.asset_codes IS NOT NULL AND NEW.asset_codes ?| ARRAY[
        'cib', 'loc_ibge', 'ac', 'nts_sds', 'up', 'uar',
        'at1', 'at2', 'at3', 'at4', 'at5', 'bcp', 'brv'] THEN
        RAISE EXCEPTION 'patrimonial MCPSB codes live only in asset, not in gis asset_codes'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['gis_node', 'gis_arc', 'gis_connection',
                             'gis_structure', 'gis_zone', 'gis_poi'] LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS trg_%s_no_mcpsb ON %I', t, t);
        EXECUTE format('CREATE TRIGGER trg_%s_no_mcpsb BEFORE INSERT OR UPDATE OF asset_codes '
            || 'ON %I FOR EACH ROW EXECUTE FUNCTION gis_asset_codes_no_mcpsb_guard()', t, t);
    END LOOP;
END
$$;

-- ======================================================================
-- Seeds de fabrica (tenant): classes de equipamento e tipos de espaco
-- confinado. Reaplicacao nao duplica (ON CONFLICT DO NOTHING).
-- ======================================================================

INSERT INTO asset_equipment_class (tenant_id, code, label, boundary_text, origin)
VALUES
    ('${tenant_id}', 'PUMPING', 'Bombeamento',
     'Fronteira: conjunto motobomba, motor, acoplamento, base, partida e instrumentacao propria. Fora: tubulacao de succao e recalque, valvulas da linha e painel de outra classe.',
     'FACTORY'),
    ('${tenant_id}', 'ELECTRICAL', 'Eletrica',
     'Fronteira: cabine, transformador, grupo gerador, nobreak, bancos, inversores, CCM e paineis eletricos. Fora: cargas acionadas (bombas, sopradores) e automacao logica.',
     'FACTORY'),
    ('${tenant_id}', 'AUTOMATION', 'Telecontrole e automacao',
     'Fronteira: CLP, remotas, transmissores, controladores e armarios de instrumentacao. Fora: o equipamento de processo medido ou acionado.',
     'FACTORY'),
    ('${tenant_id}', 'TREATMENT', 'Tratamento',
     'Fronteira: dosadores, cloradores, aeradores, sopradores, misturadores, grades, peneiras, removedores de lodo, filtros de lodo, comportas e analisadores on-line. Fora: estruturas civis que os abrigam.',
     'FACTORY'),
    ('${tenant_id}', 'LOSSES_METROLOGY', 'Controle de perdas e metrologia',
     'Fronteira: hidrometros, macromedidores, bancadas de afericao, data loggers, correlacionadores, geofones e detectores. Fora: a rede medida e o cavalete como estrutura.',
     'FACTORY'),
    ('${tenant_id}', 'LABORATORY', 'Laboratorio',
     'Fronteira: balancas, pHmetros, turbidimetros, medidores, jar-test, estufas, incubadoras, amostradores e analisadores de bancada. Fora: analisadores on-line de processo (classe TREATMENT).',
     'FACTORY'),
    ('${tenant_id}', 'LOAD_HANDLING', 'Movimentacao de carga',
     'Fronteira: pontes rolantes, talhas, porticos, monovias e plataformas elevadoras. Fora: a carga movimentada e a estrutura predial.',
     'FACTORY')
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO asset_confined_space_type (tenant_id, catalog_type_code, note)
VALUES
    ('${tenant_id}', 'MANHOLE', 'Poco de visita (NR-33, 33.3.3, a).'),
    ('${tenant_id}', 'VALVE_CHAMBER', 'Camara de valvula / caixa de registro.'),
    ('${tenant_id}', 'RESERVOIR', 'Reservatorio.'),
    ('${tenant_id}', 'ELEVATED_TANK', 'Tanque elevado (reservatorio).'),
    ('${tenant_id}', 'HYDROPNEUMATIC_TANK', 'Reservatorio hidropneumatico.'),
    ('${tenant_id}', 'ONE_WAY_FEED_TANK', 'Tanque de alimentacao unidirecional (TAU).'),
    ('${tenant_id}', 'SEWAGE_LIFT_STATION', 'Elevatoria de esgoto: o poco umido vive nela (sem tipo proprio no catalogo de fabrica).'),
    ('${tenant_id}', 'WWTP', 'ETE: tanques da ETE.')
ON CONFLICT (tenant_id, catalog_type_code) DO NOTHING;
