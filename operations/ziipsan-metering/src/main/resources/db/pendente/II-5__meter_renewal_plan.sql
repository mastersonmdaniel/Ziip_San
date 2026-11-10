-- II-5 -- Plano anual selectivo de troca do parque de hidrometros (Onda II do PDCA-01).
--
-- *** SEM NUMERO FLYWAY -- db/pendente/ ***
-- Como a ED-14 (precedente ED-17): nenhuma versao esta reservada a II-5 no mapa do
-- plano; este ficheiro fica em db/pendente/ com o prefixo do pacote e recebe o numero
-- da sequencia global na integracao. Nenhum numero inventado.
--
-- Tabelas TENANT da persistencia do plano: o nucleo (pacote hydrometry.renewal) e puro
-- e nao depende destas tabelas; elas guardam o plano fechado e as OS geradas, para a
-- tela/consulta futura (decisao 57) e para o dedup inter-geracoes do despacho
-- (OpenRenewalOrderLookup le service_order_id das linhas de planos anteriores).
-- Nenhuma tabela da ED-14 e alterada: meters/meter_verifications sao apenas lidas
-- por quem monta os MeterRenewalSnapshot na integracao.

CREATE TABLE IF NOT EXISTS meter_renewal_plans (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    plan_year INT NOT NULL CHECK (plan_year > 0),
    -- Restricao anual (parametro meter.renewal_annual_restriction [A DEFINIR]):
    -- exactamente uma das duas formas, como no nucleo (RenewalRestriction).
    restriction_kind VARCHAR(10) NOT NULL CHECK (restriction_kind IN ('COUNT', 'BUDGET')),
    max_replacements INT CHECK (max_replacements >= 0),
    max_budget NUMERIC(14,2) CHECK (max_budget >= 0),
    min_score NUMERIC(9,6) CHECK (min_score IS NULL OR (min_score >= 0 AND min_score <= 1)),
    eligible_count INT NOT NULL CHECK (eligible_count >= 0),
    selected_count INT NOT NULL CHECK (selected_count >= 0),
    -- NULL quando alguma linha seleccionada nao tem custo (total desconhecido no nucleo).
    total_estimated_cost NUMERIC(14,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID, updated_by UUID,
    CHECK ((restriction_kind = 'COUNT' AND max_replacements IS NOT NULL)
        OR (restriction_kind = 'BUDGET' AND max_budget IS NOT NULL)));
ALTER TABLE meter_renewal_plans ADD CONSTRAINT uq_meter_renewal_plans_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_renewal_plans_year ON meter_renewal_plans(tenant_id, plan_year);

CREATE TABLE IF NOT EXISTS meter_renewal_plan_lines (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    position INT NOT NULL CHECK (position > 0),
    meter_id UUID NOT NULL,
    unit_id UUID,
    meter_number VARCHAR(30) NOT NULL,
    total_score NUMERIC(9,6) NOT NULL CHECK (total_score >= 0 AND total_score <= 1),
    dominant_criterion VARCHAR(20) NOT NULL
        CHECK (dominant_criterion IN ('AGE', 'VOLUME', 'CLASS_TECHNOLOGY', 'WATER_QUALITY')),
    clock_status VARCHAR(30) NOT NULL
        CHECK (clock_status IN ('OVERDUE', 'DUE_WITHIN_HORIZON', 'CURRENT', 'UNKNOWN_NO_VERIFICATION')),
    next_verification_on DATE,
    replacement_cost NUMERIC(14,2) CHECK (replacement_cost IS NULL OR replacement_cost >= 0),
    -- OS selectiva gerada para a linha (nucleo de OS da P1.31); NULL ate ao despacho.
    service_order_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID, updated_by UUID);
ALTER TABLE meter_renewal_plan_lines ADD CONSTRAINT uq_meter_renewal_plan_lines_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE meter_renewal_plan_lines ADD CONSTRAINT fk_renewal_line_plan
    FOREIGN KEY (tenant_id, plan_id) REFERENCES meter_renewal_plans(tenant_id, id);
ALTER TABLE meter_renewal_plan_lines ADD CONSTRAINT fk_renewal_line_meter
    FOREIGN KEY (tenant_id, meter_id) REFERENCES meters(tenant_id, id);
-- Sem duplicados: um hidrometro por plano, uma linha por posicao.
CREATE UNIQUE INDEX idx_renewal_lines_meter ON meter_renewal_plan_lines(tenant_id, plan_id, meter_id);
CREATE UNIQUE INDEX idx_renewal_lines_position ON meter_renewal_plan_lines(tenant_id, plan_id, position);
CREATE INDEX idx_renewal_lines_order ON meter_renewal_plan_lines(tenant_id, service_order_id);

SELECT register_tenant_table('meter_renewal_plans');
SELECT register_tenant_table('meter_renewal_plan_lines');
