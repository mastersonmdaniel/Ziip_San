-- PA-02 (M1, billing): programa de desoneracao tarifaria (DESENHO-PA02 secoes 3.2-3.4,
-- com ADENDO-FONTES-PRIMARIAS sobre a NT 40/2026, anexo da Res. ANA 307/2026).
--
-- NUMERACAO PROVISORIA DE PACOTE STAGED: V113 (o maior global em origin/main
-- cf5523df era V112). O numero final confirma-se na integracao/pouso, como nos
-- pacotes anteriores; nunca se fixa numero como final neste degrau.
--
-- Depende de: V1_2 (register_tenant_table) e V4_1 (tariffs/tariff_versions).
--
-- NENHUM DADO E SEMEADO. Programa, aliquota efectiva e ligacoes sao cadastro
-- do tenant com proveniencia declarada; qualquer valor padrao inventado seria
-- tratado como dado real. A aliquota efectiva NUNCA tem default.
--
-- Dinheiro: NUMERIC(14,4) nos precos (padrao V4_1). Aliquota: percentual
-- NUMERIC(7,4) (convencao do billing: tax_withholding_rates.rate_pct).

-- ========================================================================
-- PROGRAMA DE DESONERACAO
-- ========================================================================
CREATE TABLE IF NOT EXISTS tariff_relief_programs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(200) NOT NULL,
    -- NULL = empresa (comportamento vigente); escopo municipal anulavel (decisao 45).
    municipality_ibge CHAR(7),
    -- Acto declarado pelo tenant/reguladora no cadastro; o sistema NAO pre-preenche.
    legal_basis VARCHAR(300) NOT NULL,
    source_document_ref VARCHAR(300),
    -- Data do corte: primeira data de EMISSAO que deve usar tabelas desoneradas.
    -- A fonte indica 01/01/2027, mas o valor entra pelo cadastro, nao como default.
    effective_from DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN
        ('DRAFT', 'SIMULATED', 'APPROVED', 'PUBLISHED', 'ACTIVE', 'SUPERSEDED', 'CANCELLED')),
    -- Marcos do cronograma (NT Tabela 2): homologacao da tabela desonerada pela
    -- entidade reguladora infranacional ate 15/11/2026; divulgacao aos usuarios
    -- ate 02/12/2026. Cadastrados; so para alerta/relatorio, nao regra de calculo.
    homologation_deadline DATE,
    disclosure_deadline DATE,
    homologating_regulator VARCHAR(200),
    -- Reserva de Fase II (NT 4.7.3/7.3): o acto consigna que a carga tributaria
    -- efectiva sera apurada depois (LC 214/2025, arts. 373-377). Texto do acto.
    phase_two_reserve_note VARCHAR(500),
    -- Via provisoria (NT 5.3; Q-D2 = A): NULL = nao declarado (desligada).
    provisional_fallback_allowed BOOLEAN,
    -- Precisao da tabela publicada (Q-D3 = B com NT 6.3.2): criterio uniforme do
    -- acto; defeito = casas da tabela vigente. Sem default de DDL: o cadastro decide.
    rounding_scale INT CHECK (rounding_scale IS NULL OR rounding_scale BETWEEN 0 AND 6),
    published_on DATE,
    regulatory_act VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (municipality_ibge IS NULL OR municipality_ibge ~ '^\d{7}$')
);
ALTER TABLE tariff_relief_programs ADD CONSTRAINT uq_tariff_relief_programs_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_tariff_relief_programs_code
    ON tariff_relief_programs(tenant_id, COALESCE(municipality_ibge, ''), code);

-- ========================================================================
-- ALIQUOTA EFECTIVA APLICAVEL (o unico numero da formula; sempre do tenant)
-- ========================================================================
CREATE TABLE IF NOT EXISTS tariff_relief_rates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    program_id UUID NOT NULL,
    scope_kind VARCHAR(10) NOT NULL CHECK (scope_kind IN ('TENANT', 'TARIFF', 'CATEGORY', 'SERVICE')),
    scope_ref VARCHAR(200),
    -- Percentual em [0, 100). Sem default, sem seed.
    effective_rate_pct NUMERIC(7,4) NOT NULL CHECK (effective_rate_pct >= 0 AND effective_rate_pct < 100),
    -- Proveniencia declarada do numero (apuracao contabilistica/regulatoria do tenant). Obrigatoria.
    rate_source VARCHAR(300) NOT NULL,
    legal_basis VARCHAR(300),
    valid_from DATE NOT NULL,
    valid_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CHECK ((scope_kind = 'TENANT' AND scope_ref IS NULL) OR (scope_kind <> 'TENANT' AND scope_ref IS NOT NULL))
);
ALTER TABLE tariff_relief_rates ADD CONSTRAINT uq_tariff_relief_rates_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE tariff_relief_rates ADD CONSTRAINT fk_tariff_relief_rate_program
    FOREIGN KEY (tenant_id, program_id) REFERENCES tariff_relief_programs(tenant_id, id);
-- Anti-sobreposicao de vigencia no mesmo escopo (padrao tariff_versions/tax_rates).
ALTER TABLE tariff_relief_rates ADD CONSTRAINT excl_tariff_relief_rates_no_overlap
    EXCLUDE USING gist (tenant_id WITH =, program_id WITH =, scope_kind WITH =,
        COALESCE(scope_ref, '') WITH =, daterange(valid_from, valid_to, '[]') WITH &&);

-- ========================================================================
-- LIGACAO ORIGEM -> DESONERADA (proveniencia de cada tabela publicada)
-- ========================================================================
CREATE TABLE IF NOT EXISTS tariff_relief_links (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    program_id UUID NOT NULL,
    target_kind VARCHAR(25) NOT NULL CHECK (target_kind IN
        ('TARIFF_VERSION', 'SERVICE_PRICE_TABLE', 'FIXED_FEE')),
    source_version_id UUID NOT NULL,
    relieved_version_id UUID NOT NULL,
    applied_rate_pct NUMERIC(7,4) NOT NULL,
    factor NUMERIC(9,6) NOT NULL,
    -- Politica efectiva usada, gravada por ligacao (nunca implicita).
    rounding_policy VARCHAR(30) NOT NULL,
    -- Classificacao previa das multas (NT 6.6.2); so para FIXED_FEE.
    fine_classification VARCHAR(30) CHECK (fine_classification IS NULL OR fine_classification IN
        ('PERCENTUAL', 'NOMINAL_RECOVERY_REVENUE', 'NOMINAL_INDEMNITY', 'NOMINAL_UNCLASSIFIED')),
    generation_run_id UUID NOT NULL,
    generated_by UUID,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE tariff_relief_links ADD CONSTRAINT uq_tariff_relief_links_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE tariff_relief_links ADD CONSTRAINT fk_tariff_relief_link_program
    FOREIGN KEY (tenant_id, program_id) REFERENCES tariff_relief_programs(tenant_id, id);
CREATE UNIQUE INDEX idx_tariff_relief_links_origin
    ON tariff_relief_links(tenant_id, program_id, target_kind, source_version_id);

SELECT register_tenant_table('tariff_relief_programs');
SELECT register_tenant_table('tariff_relief_rates');
SELECT register_tenant_table('tariff_relief_links');
