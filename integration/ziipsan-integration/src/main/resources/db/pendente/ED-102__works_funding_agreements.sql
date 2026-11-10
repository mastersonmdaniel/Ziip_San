-- ED-102 -- Obras e convenios de investimento, Transferegov e afins
-- (GES-12; decisao 54 do dono: ambito COMPLETO = recorte de integracao):
-- convenios e contratos de repasse federais/estaduais ligados a
-- projetos/pacotes do GIS (ED-53) por referencia, medicao, cronograma
-- fisico-financeiro, dados da prestacao de contas e ponto de integracao
-- com o ERP de obras (padrao ED-62).
-- Status: PENDENTE DE NUMERACAO -- SQL sem numero Flyway, em
-- db/pendente/ com prefixo ED-102__ (regra da cascata). Nenhuma
-- migration publicada e tocada e nenhum numero e atribuido aqui.
--
-- [SEM FONTE] O leiaute oficial do Transferegov NAO esta nas fontes
-- locais: nenhuma coluna desta DDL pretende ser campo oficial do
-- Transferegov; a traducao vive no mapeamento declarado
-- (TransferegovLayoutMapping, officialLayout=false).
--
-- TRAVAS POR LINHA (as que cruzam linhas -- acumulado <= 100%,
-- acumulado <= valor do contrato, medicao <= cronograma -- sao do
-- servico WorksAgreementService, provadas em JUnit):
-- vigencia valida; contrapartida entre 0 e o total; percentual de
-- medicao entre 0 e 100; etapa com percentual em (0,100]; valores
-- nao negativos. FK COMPOSTA: nenhuma FK (tenant_id e a particao RLS);
-- a ligacao ao GIS e por referencia textual validada pela porta.

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('works.agreements', 'OBJECT', 'd', NULL,
     'ED-102 (GES-12); ED-53; ED-62; decisao 54 do dono',
     NULL,
     'Perfil de convenios/obras do tenant (mapeamentos declarados do Transferegov [SEM FONTE: leiaute oficial pendente] e do ERP de obras por tenant). Sem valor de fabrica.')
ON CONFLICT (definition_key) DO NOTHING;

CREATE TABLE IF NOT EXISTS works_funding_agreements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    kind VARCHAR(40) NOT NULL CHECK (kind IN (
        'FEDERAL_CONVENIO', 'FEDERAL_CONTRATO_REPASSE',
        'ESTADUAL_CONVENIO', 'ESTADUAL_CONTRATO_REPASSE')),
    external_ref VARCHAR(80) NOT NULL CHECK (btrim(external_ref) <> ''),
    concedente_name VARCHAR(200) NOT NULL CHECK (btrim(concedente_name) <> ''),
    total_value NUMERIC(19,2) NOT NULL CHECK (total_value > 0),
    counterpart_value NUMERIC(19,2) NOT NULL
        CHECK (counterpart_value >= 0 AND counterpart_value <= total_value),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL CHECK (end_date >= start_date),
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'CLOSED', 'ACCOUNT_RENDERED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_works_agreements_tenant_id UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_works_agreement_external_ref
    ON works_funding_agreements(tenant_id, external_ref);

CREATE TABLE IF NOT EXISTS works_agreement_gis_links (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    agreement_id UUID NOT NULL,
    project_ref VARCHAR(120) NOT NULL CHECK (btrim(project_ref) <> ''),
    package_ref VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_works_gis_links_tenant_id UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_works_gis_link
    ON works_agreement_gis_links(tenant_id, agreement_id, project_ref, COALESCE(package_ref, ''));

CREATE TABLE IF NOT EXISTS works_schedule_stages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    agreement_id UUID NOT NULL,
    stage_ref VARCHAR(80) NOT NULL CHECK (btrim(stage_ref) <> ''),
    description VARCHAR(300) NOT NULL CHECK (btrim(description) <> ''),
    planned_start DATE NOT NULL,
    planned_end DATE NOT NULL CHECK (planned_end >= planned_start),
    planned_percent NUMERIC(7,4) NOT NULL CHECK (planned_percent > 0 AND planned_percent <= 100),
    planned_amount NUMERIC(19,2) NOT NULL CHECK (planned_amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_works_schedule_stages_tenant_id UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_works_schedule_stage
    ON works_schedule_stages(tenant_id, agreement_id, stage_ref);

CREATE TABLE IF NOT EXISTS works_measurements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    agreement_id UUID NOT NULL,
    sequence INT NOT NULL CHECK (sequence >= 1),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL CHECK (period_end >= period_start),
    physical_percent_period NUMERIC(7,4) NOT NULL
        CHECK (physical_percent_period >= 0 AND physical_percent_period <= 100),
    amount NUMERIC(19,2) NOT NULL CHECK (amount >= 0),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'APPROVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_works_measurements_tenant_id UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_works_measurement_sequence
    ON works_measurements(tenant_id, agreement_id, sequence);

SELECT register_tenant_table('works_funding_agreements');
SELECT register_tenant_table('works_agreement_gis_links');
SELECT register_tenant_table('works_schedule_stages');
SELECT register_tenant_table('works_measurements');
