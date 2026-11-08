-- ED-100 - Projeca de receita, arrecadacao e caixa por cenario (GES-10).
-- NUMERACAO PENDENTE (declarado): o plano v3.0 NAO reserva numero nominal
-- para a ED-100. Na faixa do reporting (V15_x) estao ocupados/reservados:
-- V15_1 (P3.22, nucleo e views) e V15_2 (P3.41, registro de indicadores).
-- Nenhum numero foi inventado: na integracao, atribuir o proximo slot da
-- faixa V15_x (ou a sequencia global do plano, decisao da integracao),
-- mover este arquivo para db/migration/tenant/V15_N__forecast.sql e
-- atualizar ForecastMigrationConsistencyTest, que le este caminho.
--
-- Desenho:
--   * forecast_runs: uma execucao de projecao por tenant (mes-base,
--     horizonte de fc.horizon_months, tecnica declarada, saldo inicial e
--     receita base — as premissas ficam gravadas com o resultado).
--   * forecast_scenarios: os cenarios de fc.scenarios da execucao, com
--     TODAS as premissas explicitas (nenhuma constante no banco).
--   * forecast_collection_curve: curva de recebimento por faixa estimada
--     na execucao (faturado, recebido, recuperacao, perda).
--   * forecast_loss_curve: curva de perda por faixa (exposicao x taxa) —
--     a fonte que a integracao entrega a provisao EXPECTED_LOSS da ED-32.
--   * forecast_monthly: receita, arrecadacao e caixa por mes e cenario.
--   * Views fc_v_* : conjuntos de dados dos paineis do BI da ED-47
--     (ForecastBiPanels), lidos SOMENTE na replica pela guarda da ED-47.
--   * NENHUM dado de tenant e semeado: premissas sao do tenant (fc.*).

CREATE TABLE IF NOT EXISTS forecast_runs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    base_month DATE NOT NULL,
    horizon_months INT NOT NULL CHECK (horizon_months BETWEEN 1 AND 120),
    technique VARCHAR(20) NOT NULL CHECK (technique IN ('WEIGHTED_AVERAGE')),
    base_monthly_billed NUMERIC(16, 2) NOT NULL CHECK (base_monthly_billed >= 0),
    opening_cash_balance NUMERIC(16, 2) NOT NULL,
    total_billed_observed NUMERIC(16, 2) NOT NULL,
    total_collected_observed NUMERIC(16, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID
);
CREATE INDEX IF NOT EXISTS idx_forecast_runs_tenant_base
    ON forecast_runs(tenant_id, base_month DESC);

CREATE TABLE IF NOT EXISTS forecast_scenarios (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    run_id UUID NOT NULL,
    scenario_name VARCHAR(80) NOT NULL,
    tariff_adjustment_percent NUMERIC(9, 4) NOT NULL,
    monthly_growth_percent NUMERIC(9, 4) NOT NULL,
    social_tariff_monthly_discount NUMERIC(16, 2) NOT NULL CHECK (social_tariff_monthly_discount >= 0),
    cashback_monthly_amount NUMERIC(16, 2) NOT NULL CHECK (cashback_monthly_amount >= 0),
    recovery_shift NUMERIC(7, 6) NOT NULL CHECK (recovery_shift BETWEEN -1 AND 1),
    monthly_cash_outflow NUMERIC(16, 2) NOT NULL CHECK (monthly_cash_outflow >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_forecast_scenarios_run_name
    ON forecast_scenarios(tenant_id, run_id, scenario_name);

CREATE TABLE IF NOT EXISTS forecast_collection_curve (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    run_id UUID NOT NULL,
    bracket_code VARCHAR(40) NOT NULL,
    bracket_min_days INT NOT NULL CHECK (bracket_min_days >= 0),
    bracket_max_days INT,
    billed_amount NUMERIC(16, 2) NOT NULL CHECK (billed_amount > 0),
    collected_amount NUMERIC(16, 2) NOT NULL CHECK (collected_amount >= 0),
    recovery_rate NUMERIC(7, 6) NOT NULL CHECK (recovery_rate BETWEEN 0 AND 1),
    loss_rate NUMERIC(7, 6) NOT NULL CHECK (loss_rate BETWEEN 0 AND 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID,
    CHECK (bracket_max_days IS NULL OR bracket_max_days >= bracket_min_days),
    CHECK (collected_amount <= billed_amount)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_forecast_curve_run_bracket
    ON forecast_collection_curve(tenant_id, run_id, bracket_code);

CREATE TABLE IF NOT EXISTS forecast_loss_curve (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    run_id UUID NOT NULL,
    bracket_code VARCHAR(40) NOT NULL,
    exposure NUMERIC(16, 2) NOT NULL CHECK (exposure >= 0),
    expected_loss_rate NUMERIC(7, 6) NOT NULL CHECK (expected_loss_rate BETWEEN 0 AND 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_forecast_loss_run_bracket
    ON forecast_loss_curve(tenant_id, run_id, bracket_code);

CREATE TABLE IF NOT EXISTS forecast_monthly (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    run_id UUID NOT NULL,
    scenario_name VARCHAR(80) NOT NULL,
    forecast_month DATE NOT NULL,
    gross_billed NUMERIC(16, 2) NOT NULL,
    social_tariff_discount NUMERIC(16, 2) NOT NULL,
    cashback_amount NUMERIC(16, 2) NOT NULL,
    net_billed NUMERIC(16, 2) NOT NULL,
    collection_from_new_billing NUMERIC(16, 2) NOT NULL,
    collection_from_opening_stock NUMERIC(16, 2) NOT NULL,
    total_collection NUMERIC(16, 2) NOT NULL,
    cash_outflow NUMERIC(16, 2) NOT NULL,
    cash_balance NUMERIC(16, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_forecast_monthly_run_scenario_month
    ON forecast_monthly(tenant_id, run_id, scenario_name, forecast_month);

-- Conjuntos de dados dos paineis do BI (ED-47): leitura so na replica.
CREATE OR REPLACE VIEW fc_v_forecast_monthly AS
SELECT tenant_id, run_id, scenario_name, forecast_month, gross_billed,
       social_tariff_discount, cashback_amount, net_billed,
       collection_from_new_billing, collection_from_opening_stock,
       total_collection
  FROM forecast_monthly;

CREATE OR REPLACE VIEW fc_v_forecast_cash AS
SELECT tenant_id, run_id, scenario_name, forecast_month,
       cash_outflow, total_collection, cash_balance
  FROM forecast_monthly;

CREATE OR REPLACE VIEW fc_v_collection_curve AS
SELECT tenant_id, run_id, bracket_code, bracket_min_days, bracket_max_days,
       billed_amount, collected_amount, recovery_rate, loss_rate
  FROM forecast_collection_curve;

CREATE OR REPLACE VIEW fc_v_loss_curve AS
SELECT tenant_id, run_id, bracket_code, exposure, expected_loss_rate,
       ROUND(exposure * expected_loss_rate, 2) AS expected_loss
  FROM forecast_loss_curve;

SELECT register_tenant_table('forecast_runs', true, false);
SELECT register_tenant_table('forecast_scenarios', true, false);
SELECT register_tenant_table('forecast_collection_curve', true, false);
SELECT register_tenant_table('forecast_loss_curve', true, false);
SELECT register_tenant_table('forecast_monthly', true, false);
