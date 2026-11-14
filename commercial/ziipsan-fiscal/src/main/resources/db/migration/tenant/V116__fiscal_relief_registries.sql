-- PA-02 (M2, fiscal): cadastros CFOP/NCM, tipos de substituicao da NFAg e seus
-- vinculos, e tipos de contingencia (DESENHO-PA02 secao 3.7/7.1; PA-01 V-43).
--
-- NUMERACAO PROVISORIA DE PACOTE STAGED: V114 (ver cabecalho da M1/V113).
-- Depende de: V1_2 (register_tenant_table) e V4_5 (padrao fiscal_*).
--
-- NENHUM DADO DE NEGOCIO E SEMEADO. Os tipos de substituicao 01-09 (F-16,
-- art. 898) entram por CARGA DE CADASTRO do tenant a partir da lista de
-- referencia, com a fonte citada no registo de carga — nao por seed nesta
-- migration.

-- ========================================================================
-- CFOP (4 digitos, F-14) / NCM (8 digitos, F-15): apagar so se nunca usado.
-- ========================================================================
CREATE TABLE IF NOT EXISTS fiscal_cfop_codes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code CHAR(4) NOT NULL CHECK (code ~ '^\d{4}$'),
    description VARCHAR(300) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE fiscal_cfop_codes ADD CONSTRAINT uq_fiscal_cfop_codes_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_fiscal_cfop_codes_code ON fiscal_cfop_codes(tenant_id, code);

CREATE TABLE IF NOT EXISTS fiscal_ncm_codes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code CHAR(8) NOT NULL CHECK (code ~ '^\d{8}$'),
    description VARCHAR(300) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE fiscal_ncm_codes ADD CONSTRAINT uq_fiscal_ncm_codes_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_fiscal_ncm_codes_code ON fiscal_ncm_codes(tenant_id, code);

-- ========================================================================
-- TIPOS DE SUBSTITUICAO DA NFAg (01-09, F-16): codigo imutavel apos criacao;
-- so descricao e estado mudam (garantido no servico, sem UPDATE de codigo).
-- ========================================================================
CREATE TABLE IF NOT EXISTS fiscal_nfage_substitution_types (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code CHAR(2) NOT NULL CHECK (code ~ '^\d{2}$'),
    description VARCHAR(300) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    -- Fonte citada no registo de carga (ex.: F-16/art. 898). Obrigatoria na carga.
    load_source_ref VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE fiscal_nfage_substitution_types ADD CONSTRAINT uq_fiscal_subst_types_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_fiscal_subst_types_code ON fiscal_nfage_substitution_types(tenant_id, code);

-- ========================================================================
-- VINCULOS substituicao x motivos (F-17 a F-20). motive_ref referencia a
-- linha da tabela de motivos do dominio dono, por UUID + tipo declarado
-- (precedente de referencias por UUID entre modulos; sem FK cruzada inventada).
-- ========================================================================
CREATE TABLE IF NOT EXISTS fiscal_substitution_links (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    substitution_type_id UUID NOT NULL,
    motive_kind VARCHAR(35) NOT NULL CHECK (motive_kind IN
        ('CANCELLATION_DIVERSE_CHARGE', 'CANCELLATION_DEBIT_RESEND', 'CANCELLATION_TRANSFER',
         'CANCELLATION_PAYMENT_REVERSAL', 'BILL_STATUS', 'BILL_ALTERATION')),
    motive_ref VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE fiscal_substitution_links ADD CONSTRAINT uq_fiscal_subst_links_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE fiscal_substitution_links ADD CONSTRAINT fk_fiscal_subst_link_type
    FOREIGN KEY (tenant_id, substitution_type_id) REFERENCES fiscal_nfage_substitution_types(tenant_id, id);
CREATE UNIQUE INDEX idx_fiscal_subst_links_motive
    ON fiscal_substitution_links(tenant_id, motive_kind, motive_ref);

-- ========================================================================
-- TIPOS DE CONTINGENCIA (PA-01 V-43 = PARCIAL; analogo a CFOP/NCM, art. 901).
-- SEM coluna de timeout: V-31 = AUSENTE o valor; os 15 s do Sansys nao entram.
-- ========================================================================
CREATE TABLE IF NOT EXISTS fiscal_contingency_types (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(30) NOT NULL,
    description VARCHAR(300) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE fiscal_contingency_types ADD CONSTRAINT uq_fiscal_contingency_types_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_fiscal_contingency_types_code ON fiscal_contingency_types(tenant_id, code);

SELECT register_tenant_table('fiscal_cfop_codes');
SELECT register_tenant_table('fiscal_ncm_codes');
SELECT register_tenant_table('fiscal_nfage_substitution_types');
SELECT register_tenant_table('fiscal_substitution_links');
SELECT register_tenant_table('fiscal_contingency_types');
