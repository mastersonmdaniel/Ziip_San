-- ============================================================================
-- ED-90 (PENDENTE DE NUMERACAO): eficiencia energetica e ESG (OPE-08, GES-21).
-- SQL sem numero: prefixo ED-90__ em db/pendente/ (regra da cascata).
-- Espelho das classes de br.com.ziipsan.reporting.energy.
-- LICAO C3 aplicada: TODA FK composta (tenant_id, X) deste arquivo aponta
-- para pai com UNIQUE (tenant_id, id) declarado NESTE arquivo
-- (energy_installations). Nenhuma FK para tabelas de outras tarefas:
-- a ligacao com o registro da P3.41 e por indicator_code (chave logica,
-- como indicator_sheet_meta da ED-77), e a unidade operacional e UUID
-- logico da operacao (a ED-90 nao recria cadastro operacional).
-- NENHUM DADO SEMEADO: o catalogo global de fatores de emissao nasce
-- VAZIO (fronteira dura da tarefa: fator sem fonte citada nao entra;
-- a carga com as fontes e da integracao - P-ED90-02) e nenhum leiaute
-- de distribuidora e registrado (conferido na implantacao - P-ED90-01).
-- ============================================================================

-- Instalacoes de energia por unidade operacional (OPE-08), com o numero
-- da instalacao na distribuidora.
CREATE TABLE IF NOT EXISTS energy_installations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    operational_unit_id UUID NOT NULL,
    unit_kind VARCHAR(20) NOT NULL
        CHECK (unit_kind IN ('WATER_SYSTEM', 'ETA', 'PUMPING_STATION', 'ETE')),
    distributor_name VARCHAR(160) NOT NULL,
    installation_number VARCHAR(80) NOT NULL,
    contracted_demand_kw NUMERIC(16, 4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_energy_installations_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_energy_installations_number
        UNIQUE (tenant_id, distributor_name, installation_number),
    CONSTRAINT ck_energy_installations_distributor
        CHECK (btrim(distributor_name) <> ''),
    CONSTRAINT ck_energy_installations_number
        CHECK (btrim(installation_number) <> ''),
    CONSTRAINT ck_energy_installations_demand
        CHECK (contracted_demand_kw IS NULL OR contracted_demand_kw > 0)
);
CREATE INDEX IF NOT EXISTS idx_energy_installations_unit
    ON energy_installations(tenant_id, operational_unit_id);
SELECT register_tenant_table('energy_installations', true, false);

-- Leiaute de fatura por distribuidora: nasce NAO verificado; a versao e
-- a nota de conferencia sao exigidas quando verificado (espelho de
-- DistributorLayout). Sem linha semeada: nenhuma distribuidora e
-- presumida.
CREATE TABLE IF NOT EXISTS energy_distributor_layouts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    distributor_key VARCHAR(80) NOT NULL,
    layout_version VARCHAR(80),
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    verification_note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_energy_distributor_layouts_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_energy_distributor_layouts_key UNIQUE (tenant_id, distributor_key),
    -- CORRECAO D5 (REVISAO-ED-90): chave da distribuidora em branco e
    -- recusada (btrim), mesma lacuna fechada em esg_emission_factors.
    CONSTRAINT ck_energy_distributor_layouts_key CHECK (btrim(distributor_key) <> ''),
    CONSTRAINT ck_energy_distributor_layouts_verified CHECK (verified = FALSE
        OR (layout_version IS NOT NULL AND btrim(layout_version) <> ''
            AND verification_note IS NOT NULL AND btrim(verification_note) <> ''))
);
SELECT register_tenant_table('energy_distributor_layouts', true, false);
-- CORRECAO D5 (reaplicacao): em base ja criada pela versao anterior
-- deste arquivo (sem o CHECK inline), a reaplicacao acrescenta a
-- constraint; onde ela ja existe, o duplicate_object e ignorado —
-- reaplicar o arquivo inteiro segue idempotente.
DO $$ BEGIN
    ALTER TABLE energy_distributor_layouts
        ADD CONSTRAINT ck_energy_distributor_layouts_key
        CHECK (btrim(distributor_key) <> '');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- Faturas/leituras de energia por instalacao e mes (OPE-08).
CREATE TABLE IF NOT EXISTS energy_bills (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    installation_id UUID NOT NULL,
    reference_month DATE NOT NULL,
    total_kwh NUMERIC(20, 4) NOT NULL CHECK (total_kwh >= 0),
    peak_kwh NUMERIC(20, 4),
    measured_demand_kw NUMERIC(16, 4),
    contracted_demand_kw NUMERIC(16, 4),
    power_factor NUMERIC(6, 4),
    source VARCHAR(20) NOT NULL
        CHECK (source IN ('IMPORTED_BILL', 'TELEMETRY', 'MANUAL')),
    layout_version VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_energy_bills_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_energy_bills_month UNIQUE (tenant_id, installation_id, reference_month, source),
    CONSTRAINT ck_energy_bills_peak CHECK (peak_kwh IS NULL
        OR (peak_kwh >= 0 AND peak_kwh <= total_kwh)),
    CONSTRAINT ck_energy_bills_demands CHECK ((measured_demand_kw IS NULL OR measured_demand_kw >= 0)
        AND (contracted_demand_kw IS NULL OR contracted_demand_kw > 0)),
    CONSTRAINT ck_energy_bills_pf CHECK (power_factor IS NULL
        OR (power_factor >= 0 AND power_factor <= 1)),
    -- Espelho de EnergyBill: versao de leiaute so em fatura importada,
    -- e nela e obrigatoria (forma NULL-segura, licao da ED-77).
    CONSTRAINT ck_energy_bills_layout CHECK (
        (source = 'IMPORTED_BILL' AND layout_version IS NOT NULL AND btrim(layout_version) <> '')
        OR (source <> 'IMPORTED_BILL' AND layout_version IS NULL)),
    CONSTRAINT fk_energy_bills_installation FOREIGN KEY (tenant_id, installation_id)
        REFERENCES energy_installations (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_energy_bills_installation
    ON energy_bills(tenant_id, installation_id, reference_month);
SELECT register_tenant_table('energy_bills', true, false);

-- Catalogo GLOBAL e versionado de fatores de emissao (GES-21): sem
-- tenant, sem RLS (como sheet_ibge_bases da ED-77). NASCE VAZIO.
CREATE TABLE IF NOT EXISTS esg_emission_factors (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    catalog_version VARCHAR(80) NOT NULL,
    source_key VARCHAR(30) NOT NULL
        CHECK (source_key IN ('ELECTRICITY', 'WASTEWATER_METHANE', 'FLEET_FUEL')),
    factor_value NUMERIC(24, 10) NOT NULL CHECK (factor_value > 0),
    activity_unit VARCHAR(40) NOT NULL,
    emission_unit VARCHAR(40) NOT NULL,
    source_description VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_esg_emission_factors_key UNIQUE (catalog_version, source_key),
    -- CORRECAO D5 (REVISAO-ED-90, negativa N2 do revisor): versao do
    -- catalogo em branco ('  ') era aceita; o Java exige versao nao
    -- vazia (EMISSION_FACTOR_VERSION_REQUIRED) — o SQL agora tambem.
    CONSTRAINT ck_esg_emission_factors_version CHECK (btrim(catalog_version) <> ''),
    CONSTRAINT ck_esg_emission_factors_units CHECK (btrim(activity_unit) <> ''
        AND btrim(emission_unit) <> ''),
    CONSTRAINT ck_esg_emission_factors_source CHECK (btrim(source_description) <> '')
);
-- CORRECAO D5 (reaplicacao): idem layouts — acrescenta o CHECK em
-- base criada pela versao anterior; duplicate_object ignorado.
DO $$ BEGIN
    ALTER TABLE esg_emission_factors
        ADD CONSTRAINT ck_esg_emission_factors_version
        CHECK (btrim(catalog_version) <> '');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
-- esg_emission_factors NAO registrada em register_tenant_table: base
-- global/publica do produto (mesma justificativa de sheet_ibge_bases).

-- Linhas do inventario de emissoes por tenant/periodo (GES-21), com o
-- retrato do fator usado (versao, valor e unidades) para auditoria.
CREATE TABLE IF NOT EXISTS esg_emission_inventory_lines (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    source_key VARCHAR(30) NOT NULL
        CHECK (source_key IN ('ELECTRICITY', 'WASTEWATER_METHANE', 'FLEET_FUEL')),
    scope INT NOT NULL CHECK (scope IN (1, 2)),
    quantity NUMERIC(24, 6) NOT NULL CHECK (quantity > 0),
    activity_unit VARCHAR(40) NOT NULL,
    factor_catalog_version VARCHAR(80) NOT NULL,
    factor_value NUMERIC(24, 10) NOT NULL CHECK (factor_value > 0),
    emission_unit VARCHAR(40) NOT NULL,
    emissions NUMERIC(24, 6) NOT NULL CHECK (emissions >= 0),
    origin VARCHAR(20) NOT NULL
        CHECK (origin IN ('IMPORTED_BILL', 'TELEMETRY', 'FLEET_ED59', 'MANUAL')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_esg_inventory_lines_tenant_id UNIQUE (tenant_id, id),
    -- Escopo amarrado a fonte (espelho de EmissionSource): eletricidade
    -- e escopo 2; metano e combustivel sao escopo 1.
    CONSTRAINT ck_esg_inventory_lines_scope CHECK (
        (source_key = 'ELECTRICITY' AND scope = 2)
        OR (source_key IN ('WASTEWATER_METHANE', 'FLEET_FUEL') AND scope = 1)),
    CONSTRAINT ck_esg_inventory_lines_period CHECK (period_end >= period_start),
    CONSTRAINT ck_esg_inventory_lines_units CHECK (btrim(activity_unit) <> ''
        AND btrim(emission_unit) <> '' AND btrim(factor_catalog_version) <> '')
);
CREATE INDEX IF NOT EXISTS idx_esg_inventory_lines_period
    ON esg_emission_inventory_lines(tenant_id, period_start, period_end);
SELECT register_tenant_table('esg_emission_inventory_lines', true, false);
