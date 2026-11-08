-- ============================================================================
-- ED-77 (PENDENTE DE NUMERACAO): motor de fichas de indicador ISO 24510/24512
-- e NR 9/ANA sobre o registro unico da P3.41 (JOR-19).
-- SQL sem numero: prefixo ED-77__ em db/pendente/ (regra da cascata).
-- Depende de: V15_2 (P3.41: indicator_definitions/closings - a ficha NAO tem
-- tabela de registro propria; sheet_meta e so o acrescimo da ED-77).
-- LICAO C3 aplicada: TODA FK composta (tenant_id, X) deste arquivo aponta
-- para pai com UNIQUE (tenant_id, id) declarado NESTE arquivo
-- (incentive_assessments). Nenhuma FK para tabelas de outras tarefas.
-- NENHUM DADO SEMEADO: base IBGE, metas de perdas (Portaria MCID 788/2024) e
-- fatores perf.* sao carga declarada na integracao - sem linha de fabrica.
-- O piso federal (99%/90% em 2033, Lei 11.445 art. 11-B) vive como CHECK
-- espelho da classe UniversalizationGoal, nao como linha semeada.
-- ============================================================================

-- Base de domicilios do IBGE: GLOBAL e versionada (sem tenant_id).
CREATE TABLE IF NOT EXISTS sheet_ibge_bases (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    base_version VARCHAR(80) NOT NULL,
    source_description VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_sheet_ibge_bases_version UNIQUE (base_version),
    CONSTRAINT ck_sheet_ibge_bases_source CHECK (btrim(source_description) <> '')
);

CREATE TABLE IF NOT EXISTS sheet_ibge_households (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    base_id UUID NOT NULL,
    municipality_code VARCHAR(20) NOT NULL,
    total_households BIGINT NOT NULL CHECK (total_households >= 0),
    urban_households BIGINT NOT NULL CHECK (urban_households >= 0),
    rural_households BIGINT NOT NULL CHECK (rural_households >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_sheet_ibge_households_recorte UNIQUE (base_id, municipality_code),
    CONSTRAINT ck_sheet_ibge_households_zones CHECK (urban_households + rural_households = total_households),
    CONSTRAINT fk_sheet_ibge_households_base FOREIGN KEY (base_id)
        REFERENCES sheet_ibge_bases (id)
);
CREATE INDEX IF NOT EXISTS idx_sheet_ibge_households_base
    ON sheet_ibge_households(base_id, municipality_code);
-- sheet_ibge_* NAO registradas em register_tenant_table: base global/publica
-- (mesma justificativa de benchmark_references da ED-48).

-- Acrescimo da ficha por tenant: arcabouco, nivel e rateio. A definicao,
-- as variaveis, o contexto e o grau continuam na V15_2 (sem registro proprio).
CREATE TABLE IF NOT EXISTS indicator_sheet_meta (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    indicator_code VARCHAR(60) NOT NULL,
    framework VARCHAR(20) NOT NULL
        CHECK (framework IN ('ISO_24510', 'ISO_24512', 'NR9_ANA', 'AGENCY_SC')),
    sheet_level VARCHAR(12) NOT NULL CHECK (sheet_level IN ('LEVEL_I', 'LEVEL_II', 'NA')),
    municipality_code VARCHAR(20) NOT NULL,
    total_percent NUMERIC(9, 4) NOT NULL,
    urban_percent NUMERIC(9, 4) NOT NULL,
    rural_percent NUMERIC(9, 4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_indicator_sheet_meta_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_indicator_sheet_meta_key UNIQUE (tenant_id, indicator_code, municipality_code),
    CONSTRAINT ck_indicator_sheet_meta_split CHECK (urban_percent + rural_percent = total_percent)
);
SELECT register_tenant_table('indicator_sheet_meta', true, false);

-- Metas de universalizacao versionadas por municipio/ano (GES-03, classe d REG).
CREATE TABLE IF NOT EXISTS universalization_goals (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    municipality_code VARCHAR(20) NOT NULL,
    target_year INT NOT NULL,
    service VARCHAR(10) NOT NULL CHECK (service IN ('WATER', 'SEWAGE')),
    target_percent NUMERIC(7, 4) NOT NULL CHECK (target_percent >= 0 AND target_percent <= 100),
    origin VARCHAR(20) NOT NULL
        CHECK (origin IN ('CONTRACT', 'NR', 'MUNICIPAL_PLAN', 'FEDERAL_LAW')),
    agency_consent_for_extension BOOLEAN NOT NULL DEFAULT FALSE,
    valid_from DATE NOT NULL,
    valid_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_universalization_goals_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_universalization_goals_key
        UNIQUE (tenant_id, municipality_code, target_year, service, valid_from),
    CONSTRAINT ck_universalization_goals_validity CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT ck_universalization_goals_limit CHECK (target_year <= 2040),
    CONSTRAINT ck_universalization_goals_extension
        CHECK (target_year <= 2033 OR agency_consent_for_extension = TRUE),
    CONSTRAINT ck_universalization_goals_floor CHECK (target_year <> 2033
        OR (service = 'WATER' AND target_percent >= 99)
        OR (service = 'SEWAGE' AND target_percent >= 90))
);
SELECT register_tenant_table('universalization_goals', true, false);

-- Apuracao auditavel da regulacao por incentivo (GES-30).
CREATE TABLE IF NOT EXISTS incentive_assessments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    indicator_code VARCHAR(60) NOT NULL,
    formula_version INT NOT NULL CHECK (formula_version >= 1),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    area_code VARCHAR(60),
    frozen_value NUMERIC(24, 10) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID,
    -- Pai da FK composta da trilha (licao C3): UNIQUE (tenant_id, id).
    CONSTRAINT uq_incentive_assessments_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_incentive_assessments_period CHECK (period_end >= period_start),
    CONSTRAINT ck_incentive_assessments_hash CHECK (btrim(content_hash) <> '')
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_incentive_assessments_key
    ON incentive_assessments(tenant_id, indicator_code, period_start, COALESCE(area_code, ''));
SELECT register_tenant_table('incentive_assessments', true, false);

-- Trilha ao dado primario (leitura, ocorrencia ED-92, protocolo).
CREATE TABLE IF NOT EXISTS incentive_assessment_lineage (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    assessment_id UUID NOT NULL,
    link_kind VARCHAR(20) NOT NULL
        CHECK (link_kind IN ('READING', 'NETWORK_INCIDENT', 'PROTOCOL')),
    primary_id UUID,
    primary_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID,
    -- Correcao do finalizador (2026-11-08, prova em PG real): a forma
    -- original (btrim(primary_code) <> '') nao recusava primary_code NULL
    -- (logica trivalorada: FALSE OR NULL = NULL, e CHECK so falha em FALSE),
    -- divergindo do Java (PrimaryDataLink recusa elo sem id e sem codigo).
    CONSTRAINT ck_lineage_reference CHECK (primary_id IS NOT NULL
        OR (primary_code IS NOT NULL AND btrim(primary_code) <> '')),
    CONSTRAINT fk_lineage_assessment FOREIGN KEY (tenant_id, assessment_id)
        REFERENCES incentive_assessments (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_lineage_assessment
    ON incentive_assessment_lineage(tenant_id, assessment_id);
SELECT register_tenant_table('incentive_assessment_lineage', true, false);
