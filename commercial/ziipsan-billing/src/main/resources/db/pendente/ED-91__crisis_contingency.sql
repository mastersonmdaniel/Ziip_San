-- ============================================================================
-- ED-91 (PENDENTE DE NUMERACAO): crise hidrica e contingencia - rodizio,
-- racionamento, tarifa sazonal e carro-pipa (OPE-10; GES-07; CAD-72, CAD-74).
-- Prefixo db/pendente/ ED-91__ : NENHUM numero Flyway inventado (regra da
-- cascata). Na integracao, atribuir o proximo slot livre da serie tenant do
-- billing e conferir FKs contra V4_1 (billing), setores da ED-54 (GIS) e
-- unidades essenciais da P1.3.
--
-- NENHUM DADO DE NEGOCIO SEMEADO: percentuais, gatilhos e faixas sao do ato
-- do tenant/agencia. Parametros registrados no bloco COMENTADO ao final
-- (forma da V4_4): crisis.rotation_enabled, crisis.tariff_flag_enabled
-- (classe b), tariff.contingency_* (classe b, com aceite),
-- event.* por efeito (classe b), connection.temporary_max_days
-- (SEM fabrica - [A DEFINIR]) e crisis.tanker_truck_min_chlorine_mg_l
-- (padrao 0,5 mg/L, limite MIN 0,5 - so amplia).
-- Os 14 valores da P3.9 NAO sao tocados por este SQL.
-- ============================================================================

CREATE TABLE IF NOT EXISTS crisis_exceptional_measures (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    act_kind VARCHAR(40) NOT NULL CHECK (act_kind IN
        ('EMERGENCY_DECREE','CALAMITY_DECREE','WATER_AUTHORITY_DECLARATION','AGENCY_ACT')),
    act_reference VARCHAR(200) NOT NULL CHECK (act_reference <> ''),
    valid_from DATE NOT NULL, valid_to DATE NOT NULL CHECK (valid_to >= valid_from),
    area_kind VARCHAR(20) NOT NULL CHECK (area_kind IN ('LOCALITY','SECTOR','ROUTE','GIS_POLYGON')),
    area_code VARCHAR(100) NOT NULL CHECK (area_code <> ''),
    member_sector_codes JSONB NOT NULL DEFAULT '[]'::jsonb
        CHECK (area_kind = 'SECTOR' OR jsonb_array_length(member_sector_codes) > 0),
    effects TEXT[] NOT NULL CHECK (cardinality(effects) >= 1),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ACTIVE','EXPIRED','REVOKED')),
    batch_order_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
SELECT register_tenant_table('crisis_exceptional_measures');

CREATE TABLE IF NOT EXISTS crisis_contingency_components (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('CONTINGENCY','SEASONAL')),
    pct NUMERIC(9,4) NOT NULL CHECK (pct <> 0 AND pct > -100),
    valid_from DATE NOT NULL, valid_to DATE NOT NULL CHECK (valid_to >= valid_from),
    authorization_act VARCHAR(200) NOT NULL CHECK (authorization_act <> ''),
    affected_blocks TEXT[] NOT NULL CHECK (cardinality(affected_blocks) >= 1),
    essential_units_exempt BOOLEAN NOT NULL DEFAULT TRUE CHECK (essential_units_exempt = TRUE),
    bill_note TEXT NOT NULL CHECK (bill_note <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
SELECT register_tenant_table('crisis_contingency_components');

CREATE TABLE IF NOT EXISTS crisis_rotation_entries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    sector_code VARCHAR(100) NOT NULL CHECK (sector_code <> ''),
    off_start TIMESTAMPTZ NOT NULL, off_end TIMESTAMPTZ NOT NULL CHECK (off_end > off_start),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
SELECT register_tenant_table('crisis_rotation_entries');

CREATE TABLE IF NOT EXISTS crisis_tanker_deliveries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    authorization_ref VARCHAR(200) NOT NULL CHECK (authorization_ref <> ''),
    supply_document_ref VARCHAR(200) NOT NULL CHECK (supply_document_ref <> ''),
    measured_chlorine_mg_l NUMERIC(6,3) NOT NULL,
    required_min_chlorine_mg_l NUMERIC(6,3) NOT NULL CHECK (required_min_chlorine_mg_l >= 0.5),
    volume_liters NUMERIC(12,2) NOT NULL CHECK (volume_liters > 0),
    destination_unit_id UUID NOT NULL,
    CHECK (measured_chlorine_mg_l >= required_min_chlorine_mg_l),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
SELECT register_tenant_table('crisis_tanker_deliveries');

CREATE TABLE IF NOT EXISTS crisis_temporary_connections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    purpose VARCHAR(20) NOT NULL CHECK (purpose IN ('WORK','FAIR','EVENT')),
    valid_from DATE NOT NULL, valid_to DATE NOT NULL CHECK (valid_to >= valid_from),
    tariff_or_fee_code VARCHAR(100) NOT NULL CHECK (tariff_or_fee_code <> ''),
    expiry_notice_sent BOOLEAN NOT NULL DEFAULT FALSE,
    closure_order_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
SELECT register_tenant_table('crisis_temporary_connections');

CREATE TABLE IF NOT EXISTS crisis_hydrant_permits (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    hydrant_code VARCHAR(100) NOT NULL CHECK (hydrant_code <> ''),
    permit_ref VARCHAR(200) NOT NULL CHECK (permit_ref <> ''),
    metered_volume_m3 NUMERIC(12,3) NOT NULL CHECK (metered_volume_m3 > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
SELECT register_tenant_table('crisis_hydrant_permits');

CREATE TABLE IF NOT EXISTS crisis_filling_point_sales (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    filling_point_code VARCHAR(100) NOT NULL CHECK (filling_point_code <> ''),
    volume_liters NUMERIC(12,2) NOT NULL CHECK (volume_liters > 0),
    amount NUMERIC(15,2) NOT NULL CHECK (amount >= 0),
    receipt_code VARCHAR(100) NOT NULL CHECK (receipt_code <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
SELECT register_tenant_table('crisis_filling_point_sales');

-- Parametros (DESCOMENTAR na integracao, forma literal da V4_4):
-- INSERT INTO public.parameter_definitions (key, ...) VALUES
--  ('crisis.rotation_enabled', ..., 'b', ...),
--  ('crisis.tariff_flag_enabled', ..., 'b', ...),
--  ('tariff.contingency_enabled', ..., 'b', ...), -- familia tariff.contingency_*, com aceite
--  ('event.suspend_cut', ..., 'b', ...), ('event.suspend_negative_listing', ..., 'b', ...),
--  ('event.suspend_charges', ..., 'b', ...), ('event.grant_discount_or_exemption', ..., 'b', ...),
--  ('event.extend_due_date', ..., 'b', ...), ('event.apply_contingency_tariff', ..., 'b', ...),
--  ('crisis.tanker_truck_min_chlorine_mg_l', ..., default 0.5, ...),
--  ('connection.temporary_max_days', ..., '[A DEFINIR] sem fabrica', ...)
-- ON CONFLICT DO NOTHING;
-- INSERT INTO public.parameter_legal_limits: tanker chlorine MIN 0.5.
