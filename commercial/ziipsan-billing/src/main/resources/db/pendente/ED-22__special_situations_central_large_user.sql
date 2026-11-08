-- ============================================================================
-- ED-22 (PENDENTE DE NUMERACAO): situacoes especiais de faturamento, usuario
-- avulso e orgao centralizador.
--
-- POR QUE PENDENTE: o plano/mapa nao reserva versao Flyway para a ED-22 e a
-- faixa do billing ja esta ocupada ate V4_17 (mais V4_12/V4_16 nomeados).
-- Conforme a regra da tarefa, NENHUM numero foi inventado: este SQL entra
-- como db/pendente/ e recebe o numero da sequencia global na integracao
-- (precedentes: ED-14, ED-17, P2.24).
--
-- Depende de: V4_1 (bills, consumer_units via registry V3), V5_1/V5_6
-- (consumption_records e seu versionamento) e public.parameter_definitions.
-- NENHUM DADO DE NEGOCIO E SEMEADO: tipos, motivos, bases legais, tarifas
-- negociadas e percentuais sao do tenant/agencia. Os unicos INSERTs sao o
-- catalogo de parametros (precedente V4_4).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. SITUACOES ESPECIAIS (23b S3.17 / DAD-22)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS special_situations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    domain VARCHAR(20) NOT NULL CHECK (domain IN ('BILLING', 'CHARGING')),
    type_code VARCHAR(30) NOT NULL CHECK (type_code IN
        ('FIXED_VOLUME', 'BILLING_PAUSED', 'EXEMPTION', 'REGISTERED_ONLY')),
    reason TEXT NOT NULL,
    legal_basis TEXT,
    start_month DATE NOT NULL,
    end_month DATE,
    withdrawn_month DATE,
    fixed_water_volume INT CHECK (fixed_water_volume IS NULL OR fixed_water_volume >= 0),
    fixed_sewer_volume INT CHECK (fixed_sewer_volume IS NULL OR fixed_sewer_volume >= 0),
    opening_os_id UUID NOT NULL,
    closing_os_id UUID,
    batch_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (end_month IS NULL OR end_month >= start_month),
    CHECK (withdrawn_month IS NULL OR withdrawn_month >= start_month),
    CHECK (withdrawn_month IS NULL OR closing_os_id IS NOT NULL),
    -- Isencao exige base legal (tipo com requiresLegalBasis no dominio).
    CHECK (type_code <> 'EXEMPTION' OR (legal_basis IS NOT NULL AND length(trim(legal_basis)) > 0)),
    -- Volume fixo so nos tipos que fixam volume.
    CHECK (type_code = 'FIXED_VOLUME' OR (fixed_water_volume IS NULL AND fixed_sewer_volume IS NULL)),
    -- Correcao pos-revisao (D2): FIXED_VOLUME com os dois volumes nulos nao
    -- fixa nada - ao menos um volume e exigido (espelha o Java).
    CHECK (type_code <> 'FIXED_VOLUME' OR fixed_water_volume IS NOT NULL OR fixed_sewer_volume IS NOT NULL)
);
ALTER TABLE special_situations ADD CONSTRAINT uq_special_situations_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_special_situations_unit ON special_situations(tenant_id, unit_id, start_month);
CREATE INDEX idx_special_situations_batch ON special_situations(tenant_id, batch_id)
    WHERE batch_id IS NOT NULL;

-- ----------------------------------------------------------------------------
-- 2. CONSUMO SPECIAL_SITUATION_FIXED EXIGE special_situation_id (FK + CHECK)
-- A coluna nasce anulavel; o CHECK garante a regra do acceptance sem quebrar
-- os consumos dos demais tipos. A FK e composta (tenant_id, id), padrao do
-- projeto.
-- ----------------------------------------------------------------------------
ALTER TABLE consumption_records ADD COLUMN IF NOT EXISTS special_situation_id UUID;
ALTER TABLE consumption_records ADD CONSTRAINT fk_consumption_special_situation
    FOREIGN KEY (tenant_id, special_situation_id) REFERENCES special_situations(tenant_id, id);
ALTER TABLE consumption_records ADD CONSTRAINT chk_consumption_special_situation_link
    CHECK ((consumption_type = 'SPECIAL_SITUATION_FIXED') = (special_situation_id IS NOT NULL));

-- ----------------------------------------------------------------------------
-- 3. ORGAO CENTRALIZADOR: vinculo por OS e fatura agrupadora de codigo unico
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS central_billing_links (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    centralizer_customer_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    link_os_id UUID NOT NULL,
    start_month DATE NOT NULL,
    end_month DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (end_month IS NULL OR end_month >= start_month)
);
ALTER TABLE central_billing_links ADD CONSTRAINT uq_central_billing_links_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_central_billing_links_centralizer
    ON central_billing_links(tenant_id, centralizer_customer_id);

CREATE TABLE IF NOT EXISTS grouped_bills (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    centralizer_customer_id UUID NOT NULL,
    reference_month DATE NOT NULL,
    total_value NUMERIC(14,2) NOT NULL CHECK (total_value >= 0),
    invoice_mode VARCHAR(20) NOT NULL CHECK (invoice_mode IN ('SINGLE', 'PER_UNIT', 'NONE')),
    fiscal_document_count INT NOT NULL CHECK (fiscal_document_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE grouped_bills ADD CONSTRAINT uq_grouped_bills_tenant_id UNIQUE (tenant_id, id);
-- Codigo unico do documento agrupador, por tenant.
CREATE UNIQUE INDEX idx_grouped_bills_code ON grouped_bills(tenant_id, code);

CREATE TABLE IF NOT EXISTS grouped_bill_members (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    grouped_bill_id UUID NOT NULL,
    bill_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    total_value NUMERIC(14,2) NOT NULL CHECK (total_value >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE grouped_bill_members ADD CONSTRAINT uq_grouped_bill_members_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE grouped_bill_members ADD CONSTRAINT fk_grouped_member_grouped
    FOREIGN KEY (tenant_id, grouped_bill_id) REFERENCES grouped_bills(tenant_id, id);
-- Correcao pos-revisao (D5): bill_id ganha FK composta para bills, no
-- padrao da V4_1 (uq_bills_tenant_id UNIQUE (tenant_id, id); as tabelas
-- filhas de bills referenciam exatamente assim), e unicidade
-- (tenant_id, bill_id): a mesma conta nao entra em duas agrupadoras.
ALTER TABLE grouped_bill_members ADD CONSTRAINT fk_grouped_member_bill
    FOREIGN KEY (tenant_id, bill_id) REFERENCES bills(tenant_id, id);
CREATE UNIQUE INDEX idx_grouped_bill_members_bill
    ON grouped_bill_members(tenant_id, bill_id);
CREATE UNIQUE INDEX idx_grouped_bill_members_unit
    ON grouped_bill_members(tenant_id, grouped_bill_id, unit_id);

-- ----------------------------------------------------------------------------
-- 4. CONTRATO ESPECIAL DE GRANDE USUARIO (NR 11, art. 19)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS large_user_contracts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    modality VARCHAR(30) NOT NULL CHECK (modality IN ('LARGE_USER', 'BULK_TREATED_WATER_SALE')),
    contracted_volume_m3 INT NOT NULL CHECK (contracted_volume_m3 >= 1),
    minimum_volume_m3 INT NOT NULL CHECK (minimum_volume_m3 >= 0),
    negotiated_tariff NUMERIC(14,4) NOT NULL CHECK (negotiated_tariff >= 0),
    valid_from DATE NOT NULL,
    valid_to DATE,
    auto_renew BOOLEAN NOT NULL DEFAULT FALSE,
    readjustment_index VARCHAR(100) NOT NULL,
    excess_penalty_percent NUMERIC(7,2) NOT NULL
        CHECK (excess_penalty_percent >= 0 AND excess_penalty_percent <= 100),
    metering_point_ids JSONB,
    agency_approved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (minimum_volume_m3 <= contracted_volume_m3),
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
ALTER TABLE large_user_contracts ADD CONSTRAINT uq_large_user_contracts_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_large_user_contracts_unit ON large_user_contracts(tenant_id, unit_id);

CREATE TABLE IF NOT EXISTS large_user_metering_points (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    metering_point_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE large_user_metering_points ADD CONSTRAINT fk_large_user_point_contract
    FOREIGN KEY (tenant_id, contract_id) REFERENCES large_user_contracts(tenant_id, id);
CREATE UNIQUE INDEX idx_large_user_points_unique
    ON large_user_metering_points(tenant_id, contract_id, metering_point_id);

-- ----------------------------------------------------------------------------
-- 5. ISOLAMENTO (padrao register_tenant_table da core)
-- ----------------------------------------------------------------------------
SELECT register_tenant_table('special_situations');
SELECT register_tenant_table('central_billing_links');
SELECT register_tenant_table('grouped_bills');
SELECT register_tenant_table('grouped_bill_members');
SELECT register_tenant_table('large_user_contracts');
SELECT register_tenant_table('large_user_metering_points');

-- ----------------------------------------------------------------------------
-- 6. PARAMETROS NO CATALOGO GLOBAL (public.parameter_definitions)
-- So o catalogo (chave, tipo, descricao): NENHUM valor e semeado - valor e
-- politica do tenant/agencia, aceito pelo fluxo de parametros (P0.19/P1.6).
-- A forma exata das colunas segue a V4_4; conferir na integracao.
-- ----------------------------------------------------------------------------
-- special_situation.max_months            INT
-- quality.noncompliance_compensation      ENUM NONE|CREDIT_PERCENT|FIXED_CREDIT
-- quality.noncompliance_compensation_percent DECIMAL (correcao pos-revisao, D7:
--                                          percentual do modo CREDIT_PERCENT)
-- bill.advance_billing                    BOOLEAN
-- fiscal.central_billing_invoice_mode     ENUM SINGLE|PER_UNIT|NONE
-- large_user.min_volume_required          BOOLEAN
-- large_user.excess_penalty_percent       DECIMAL
-- large_user.agency_approval_required     BOOLEAN
-- large_user.report_enabled               BOOLEAN
-- (INSERTs no catalogo entram na integracao, copiando o formato literal da
--  V4_4 secao 7 apos a numeracao desta migration ser reservada.)
