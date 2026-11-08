-- ============================================================================
-- ED-10 (PENDENTE DE NUMERACAO): politica comercial e preco por porte.
--
-- POR QUE PENDENTE: nenhum numero global foi atribuido a esta tarefa
-- (regra da cascata ED). Este SQL vive em db/pendente/ com prefixo ED-10__
-- e recebe o numero da sequencia global na integracao. Nao e executado
-- pelo Flyway ate la.
--
-- NENHUM DADO COMERCIAL E SEMEADO: faixas de porte, componentes de custo,
-- perfis/homem-hora, margem, desconto de reversao, franquias e custos
-- unitarios sao [A DEFINIR] - entram por versao de planilha aprovada e
-- pelos parametros saas.* (secao 5, SEM valor padrao). Os unicos valores
-- fixos do dominio (garantia 12 meses, correcao de vicios 48 h, tetos
-- VISAN/SAMAE) estao no codigo com a fonte citada (acceptance da ED-10)
-- e nao precisam de linha nesta migration.
--
-- Depende de: schema platform da P2.12 (contratos SaaS / usage_ledger) -
-- as tabelas abaixo sao da politica comercial da plataforma, por tenant.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. VERSAO DA PLANILHA
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS saas_price_sheets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    version VARCHAR(40) NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE UNIQUE INDEX idx_saas_price_sheets_version ON saas_price_sheets(version);

-- ----------------------------------------------------------------------------
-- 2. FAIXAS DE PORTE (cortes sao dado da versao - nenhum corte presumido)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS saas_price_bands (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sheet_id UUID NOT NULL REFERENCES saas_price_sheets(id),
    band_code VARCHAR(40) NOT NULL,
    min_connections BIGINT NOT NULL CHECK (min_connections >= 0),
    max_connections BIGINT NOT NULL CHECK (max_connections >= min_connections),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX idx_saas_price_bands_sheet_code ON saas_price_bands(sheet_id, band_code);

-- ----------------------------------------------------------------------------
-- 3. COMPONENTES DE CUSTO (tabela de custo da P0.26) E PERFIS/HOMEM-HORA
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS saas_cost_components (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sheet_id UUID NOT NULL REFERENCES saas_price_sheets(id),
    name VARCHAR(200) NOT NULL,
    monthly_amount NUMERIC(14,2) NOT NULL CHECK (monthly_amount >= 0),
    allocation VARCHAR(20) NOT NULL CHECK (allocation IN
        ('FIXED_PER_TENANT', 'PER_CONNECTION', 'SHARED_POOL')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_saas_cost_components_sheet ON saas_cost_components(sheet_id);

CREATE TABLE IF NOT EXISTS saas_professional_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sheet_id UUID NOT NULL REFERENCES saas_price_sheets(id),
    role VARCHAR(200) NOT NULL,
    hourly_rate NUMERIC(14,2) NOT NULL CHECK (hourly_rate >= 0),
    monthly_hours NUMERIC(10,2) NOT NULL CHECK (monthly_hours >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_saas_professional_profiles_sheet ON saas_professional_profiles(sheet_id);

-- ----------------------------------------------------------------------------
-- 4. REPASSE DE USO CALCULADO (franquia/repasse - ED-41/P2.12, GES-19)
--    A fotografia de ligacoes ativas e a da P2.12 (usage_ledger); aqui so
--    se grava o RESULTADO do calculo da ED-10 por competencia.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS saas_usage_charges (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    competence DATE NOT NULL,
    category VARCHAR(30) NOT NULL CHECK (category IN
        ('WHATSAPP_MARKETING', 'WHATSAPP_UTILITY', 'WHATSAPP_AUTHENTICATION', 'SMS', 'LLM_TOKENS')),
    measured_quantity BIGINT NOT NULL CHECK (measured_quantity >= 0),
    franchise_quantity BIGINT NOT NULL DEFAULT 0 CHECK (franchise_quantity >= 0),
    billable_quantity BIGINT NOT NULL CHECK (billable_quantity >= 0),
    unit_cost NUMERIC(19,4),
    amount NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (billable_quantity <= measured_quantity),
    CHECK (measured_quantity = 0 OR unit_cost IS NOT NULL)
);
CREATE UNIQUE INDEX idx_saas_usage_charges_competence
    ON saas_usage_charges(tenant_id, competence, category);

-- ----------------------------------------------------------------------------
-- 5. PARAMETROS saas.* no catalogo (padrao das entregas vizinhas).
--    PROPOSTA de chaves (o acceptance nao as nomeia). SEM default_value:
--    parametro obrigatorio sem valor de fabrica - [A DEFINIR].
--    (Secao de catalogo global: destacar para a migration de core
--    correspondente na integracao, como na ED-41.)
-- ----------------------------------------------------------------------------
-- INSERT INTO public.parameter_definitions (key, description, value_type, scope)
-- VALUES
--   ('saas.margin_percent', 'Margem sobre o custo na planilha de preco por porte (ED-10) [A DEFINIR]', 'DECIMAL', 'PLATFORM'),
--   ('saas.reversion_discount_percent', 'Desconto da reversao sobre a mensalidade (ADR-0024) [A DEFINIR]', 'DECIMAL', 'PLATFORM'),
--   ('saas.pass_through_mode', 'Modo de repasse de mensagens/LLM: FRANCHISE ou FULL_PASS_THROUGH (ED-10) [A DEFINIR]', 'ENUM', 'PLATFORM')
-- ON CONFLICT (key) DO NOTHING;
