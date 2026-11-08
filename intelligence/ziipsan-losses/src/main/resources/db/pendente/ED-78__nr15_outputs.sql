-- ============================================================================
-- ED-78 (PENDENTE DE NUMERACAO): saida da NR 15/ANA (Res. ANA 275/2025)
-- sobre o modulo losses da ED-85 - balanco padronizado anual (base
-- SINISA), declaracao anual, indicadores e Plano de Gestao de Reducao
-- e Controle de Perdas com publicacao.
-- SQL sem numero: prefixo ED-78__ em db/pendente/ (regra da cascata).
-- Faixa Flyway propria do plano SS3: V22_x (losses, a mesma da ED-85,
-- que amplia V2_2 e V2_10) - o numero concreto so e atribuido na
-- integracao.
-- Depende de: ED-85 (loss_monthly_balances e LIDA pela aplicacao para
-- montar estas saidas; nenhum FK e criado para ela - o balanco anual
-- agrega N fotos mensais, e a foto agregada guarda os totais somados,
-- nao uma referencia 1:1), P4.2 (volume produzido lido pela porta da
-- aplicacao; o valor lido e gravado em produced_volume_p42_m3 para
-- auditoria da divergencia), P2.31 (perfil regulatorio: a agencia e o
-- status da adocao sao gravados como valores declarados na declaracao,
-- sem FK entre modulos, como a ED-85 referencia pontos da ED-17).
-- Licao da C3 aplicada: TODA FK composta (tenant_id, X) deste arquivo
-- aponta para pai com UNIQUE (tenant_id, id) declarada NESTE arquivo.
-- NENHUM DADO SEMEADO: o catalogo da norma (codigo ana-nr-15-res-275-
-- 2025 da P1.34 e a data de verificacao de 20/05/2028, art. 14,
-- paragrafo unico da NR 15) vive no codigo (Nr15NormCatalog) e no
-- catalogo de normas - NENHUMA linha nasce aqui, nenhum limiar ou
-- meta da Res. ANA 275/2025 e gravado como constante.
-- IVI e ILI sao o MESMO indicador (razao CARL/UARL da IWA; IVI e o
-- nome em portugues): a coluna ivi e travada igual a ili pelo CHECK
-- ck_nr15_balances_ivi_eq_ili (IS NOT DISTINCT FROM cobre os nulos).
-- ============================================================================
SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- Balanco hidrico padronizado anual (NR 15, art. 4o e Anexo I):
-- somas das fotos loss_monthly_balances da ED-85 do ano civil
-- (periodo de referencia de 12 meses, janeiro a dezembro - Anexo I,
-- obs. 4). Valores em m3; a apresentacao da matriz (mil m3/ano,
-- Anexo I, obs. 1) e conversao de leitura, nao de gravacao.
CREATE TABLE IF NOT EXISTS nr15_annual_balances (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    base_year INTEGER NOT NULL CHECK (base_year BETWEEN 2000 AND 2200),
    dmc_count INTEGER NOT NULL CHECK (dmc_count > 0),
    snapshot_count INTEGER NOT NULL CHECK (snapshot_count > 0),
    system_input_m3 NUMERIC NOT NULL CHECK (system_input_m3 >= 0),
    exported_m3 NUMERIC NOT NULL CHECK (exported_m3 >= 0),
    net_input_m3 NUMERIC NOT NULL CHECK (net_input_m3 >= 0),
    billed_metered_m3 NUMERIC NOT NULL CHECK (billed_metered_m3 >= 0),
    billed_unmetered_m3 NUMERIC NOT NULL CHECK (billed_unmetered_m3 >= 0),
    billed_total_m3 NUMERIC NOT NULL CHECK (billed_total_m3 >= 0),
    authorized_unbilled_m3 NUMERIC NOT NULL CHECK (authorized_unbilled_m3 >= 0),
    authorized_consumption_m3 NUMERIC NOT NULL CHECK (authorized_consumption_m3 >= 0),
    total_losses_m3 NUMERIC NOT NULL,
    apparent_losses_m3 NUMERIC CHECK (apparent_losses_m3 IS NULL OR apparent_losses_m3 >= 0),
    -- Perda real e residuo (ED-85): sem CHECK >= 0, como em loss_monthly_balances.
    real_losses_m3 NUMERIC,
    non_revenue_water_m3 NUMERIC NOT NULL CHECK (non_revenue_water_m3 >= 0),
    non_revenue_percent NUMERIC NOT NULL,
    produced_volume_p42_m3 NUMERIC NOT NULL CHECK (produced_volume_p42_m3 >= 0),
    produced_divergence_m3 NUMERIC NOT NULL,
    confidence_grade TEXT NOT NULL CHECK (confidence_grade IN ('HIGH','MEDIUM','LOW')),
    total_losses_l_per_conn_day NUMERIC NOT NULL,
    real_losses_l_per_conn_day NUMERIC,
    apparent_losses_l_per_conn_day NUMERIC CHECK (apparent_losses_l_per_conn_day IS NULL OR apparent_losses_l_per_conn_day >= 0),
    ili NUMERIC CHECK (ili IS NULL OR ili > 0),
    ivi NUMERIC CHECK (ivi IS NULL OR ivi > 0),
    hydro_metering_percent NUMERIC CHECK (hydro_metering_percent IS NULL OR (hydro_metering_percent >= 0 AND hydro_metering_percent <= 100)),
    macro_metering_percent NUMERIC CHECK (macro_metering_percent IS NULL OR (macro_metering_percent >= 0 AND macro_metering_percent <= 100)),
    CONSTRAINT uq_nr15_balances_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_nr15_balances_tenant_year UNIQUE (tenant_id, base_year),
    CONSTRAINT ck_nr15_balances_ivi_eq_ili CHECK (ivi IS NOT DISTINCT FROM ili)
);
SELECT register_tenant_table('nr15_annual_balances');

-- Declaracao anual do diagnostico (NR 15, art. 4o), submetida a ERI
-- que consta do perfil regulatorio do tenant. Publicacao (art. 12,
-- paragrafo unico; art. 14, III) so com canal, referencia e instante.
CREATE TABLE IF NOT EXISTS nr15_annual_declarations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    balance_id UUID NOT NULL,
    base_year INTEGER NOT NULL CHECK (base_year BETWEEN 2000 AND 2200),
    agency_code TEXT NOT NULL CHECK (btrim(agency_code) <> ''),
    adoption_status TEXT,
    status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','SUBMITTED','PUBLISHED')),
    submitted_at TIMESTAMPTZ,
    publication_channel TEXT CHECK (publication_channel IS NULL OR publication_channel IN ('PROVIDER_SITE','REGULATOR_SITE')),
    publication_ref TEXT,
    published_at TIMESTAMPTZ,
    CONSTRAINT uq_nr15_declarations_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_nr15_declarations_balance UNIQUE (tenant_id, balance_id),
    CONSTRAINT ck_nr15_declarations_submitted CHECK (status = 'DRAFT' OR submitted_at IS NOT NULL),
    CONSTRAINT ck_nr15_declarations_published CHECK (status <> 'PUBLISHED'
        OR (publication_channel IS NOT NULL AND publication_ref IS NOT NULL
            AND btrim(publication_ref) <> '' AND published_at IS NOT NULL)),
    CONSTRAINT fk_nr15_declarations_balance FOREIGN KEY (tenant_id, balance_id)
        REFERENCES nr15_annual_balances (tenant_id, id)
);
SELECT register_tenant_table('nr15_annual_declarations');

-- Plano de Gestao de Reducao e Controle de Perdas (NR 15, arts. 6o a
-- 9o), por municipio (art. 8o), versionado; revisao ligada a versao
-- anterior com motivo (art. 9o, paragrafo 1o).
CREATE TABLE IF NOT EXISTS nr15_management_plans (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    municipality_code TEXT NOT NULL CHECK (btrim(municipality_code) <> ''),
    plan_version INTEGER NOT NULL CHECK (plan_version >= 1),
    base_year INTEGER NOT NULL CHECK (base_year BETWEEN 2000 AND 2200),
    declaration_id UUID NOT NULL,
    agency_code TEXT NOT NULL CHECK (btrim(agency_code) <> ''),
    baseline_total_losses_l_per_conn_day NUMERIC NOT NULL CHECK (baseline_total_losses_l_per_conn_day >= 0),
    baseline_real_losses_l_per_conn_day NUMERIC,
    baseline_apparent_losses_l_per_conn_day NUMERIC CHECK (baseline_apparent_losses_l_per_conn_day IS NULL OR baseline_apparent_losses_l_per_conn_day >= 0),
    monitoring_description TEXT NOT NULL CHECK (btrim(monitoring_description) <> ''),
    monitoring_periodicity_declared TEXT NOT NULL CHECK (btrim(monitoring_periodicity_declared) <> ''),
    complementary_indicators TEXT NOT NULL CHECK (btrim(complementary_indicators) <> ''),
    operational_structure TEXT NOT NULL CHECK (btrim(operational_structure) <> ''),
    operational_cost_estimate NUMERIC NOT NULL CHECK (operational_cost_estimate >= 0),
    status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','SUBMITTED','PUBLISHED')),
    revision_of_id UUID,
    revision_reason TEXT,
    CONSTRAINT uq_nr15_plans_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_nr15_plans_tenant_municipality_version UNIQUE (tenant_id, municipality_code, plan_version),
    CONSTRAINT ck_nr15_plans_revision CHECK (
        (plan_version = 1 AND revision_of_id IS NULL)
        OR (plan_version > 1 AND revision_of_id IS NOT NULL
            AND revision_reason IS NOT NULL AND btrim(revision_reason) <> '')),
    CONSTRAINT fk_nr15_plans_declaration FOREIGN KEY (tenant_id, declaration_id)
        REFERENCES nr15_annual_declarations (tenant_id, id),
    CONSTRAINT fk_nr15_plans_revision FOREIGN KEY (tenant_id, revision_of_id)
        REFERENCES nr15_management_plans (tenant_id, id)
);
SELECT register_tenant_table('nr15_management_plans');

-- Acoes do Plano (art. 9o, IV): cronograma fisico-financeiro e
-- justificativa tecnico-economica obrigatorios.
CREATE TABLE IF NOT EXISTS nr15_plan_actions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    description TEXT NOT NULL CHECK (btrim(description) <> ''),
    focus TEXT NOT NULL CHECK (focus IN ('REAL','APPARENT','BOTH')),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    estimated_cost NUMERIC NOT NULL CHECK (estimated_cost >= 0),
    technical_economic_justification TEXT NOT NULL CHECK (btrim(technical_economic_justification) <> ''),
    CONSTRAINT uq_nr15_actions_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_nr15_actions_schedule CHECK (end_date >= start_date),
    CONSTRAINT fk_nr15_actions_plan FOREIGN KEY (tenant_id, plan_id)
        REFERENCES nr15_management_plans (tenant_id, id)
);
SELECT register_tenant_table('nr15_plan_actions');

-- Serie de evolucao da hidrometacao e da macromedicao (art. 9o, III):
-- pontos medidos e planejados por ano, ao menos um indice por ponto.
CREATE TABLE IF NOT EXISTS nr15_plan_metering_evolution (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    ref_year INTEGER NOT NULL CHECK (ref_year BETWEEN 2000 AND 2200),
    point_kind TEXT NOT NULL CHECK (point_kind IN ('MEASURED','PLANNED')),
    hydro_metering_percent NUMERIC CHECK (hydro_metering_percent IS NULL OR (hydro_metering_percent >= 0 AND hydro_metering_percent <= 100)),
    macro_metering_percent NUMERIC CHECK (macro_metering_percent IS NULL OR (macro_metering_percent >= 0 AND macro_metering_percent <= 100)),
    CONSTRAINT uq_nr15_evolution_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_nr15_evolution_point UNIQUE (tenant_id, plan_id, ref_year, point_kind),
    CONSTRAINT ck_nr15_evolution_any_index CHECK (hydro_metering_percent IS NOT NULL OR macro_metering_percent IS NOT NULL),
    CONSTRAINT fk_nr15_evolution_plan FOREIGN KEY (tenant_id, plan_id)
        REFERENCES nr15_management_plans (tenant_id, id)
);
SELECT register_tenant_table('nr15_plan_metering_evolution');

-- Alvos declarados por ano (ato da ERI/prestador): DADO declarado,
-- nunca constante desta entrega - sem linha, a convergencia do
-- acompanhamento e desconhecida (Nr15PlanService).
CREATE TABLE IF NOT EXISTS nr15_plan_declared_targets (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    target_year INTEGER NOT NULL CHECK (target_year BETWEEN 2000 AND 2200),
    real_losses_l_per_conn_day_target NUMERIC NOT NULL CHECK (real_losses_l_per_conn_day_target > 0),
    source_description TEXT NOT NULL CHECK (btrim(source_description) <> ''),
    CONSTRAINT uq_nr15_targets_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_nr15_targets_plan_year UNIQUE (tenant_id, plan_id, target_year),
    CONSTRAINT fk_nr15_targets_plan FOREIGN KEY (tenant_id, plan_id)
        REFERENCES nr15_management_plans (tenant_id, id)
);
SELECT register_tenant_table('nr15_plan_declared_targets');

-- Acompanhamento do Plano (art. 9o, paragrafo 1o; art. 12): indicadores
-- observados por periodo (period_month = 0 e o agregado anual).
CREATE TABLE IF NOT EXISTS nr15_plan_monitoring (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    period_year INTEGER NOT NULL CHECK (period_year BETWEEN 2000 AND 2200),
    period_month INTEGER NOT NULL DEFAULT 0 CHECK (period_month BETWEEN 0 AND 12),
    real_losses_l_per_conn_day NUMERIC,
    apparent_losses_l_per_conn_day NUMERIC CHECK (apparent_losses_l_per_conn_day IS NULL OR apparent_losses_l_per_conn_day >= 0),
    ili NUMERIC CHECK (ili IS NULL OR ili > 0),
    hydro_metering_percent NUMERIC CHECK (hydro_metering_percent IS NULL OR (hydro_metering_percent >= 0 AND hydro_metering_percent <= 100)),
    macro_metering_percent NUMERIC CHECK (macro_metering_percent IS NULL OR (macro_metering_percent >= 0 AND macro_metering_percent <= 100)),
    CONSTRAINT uq_nr15_monitoring_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_nr15_monitoring_period UNIQUE (tenant_id, plan_id, period_year, period_month),
    CONSTRAINT fk_nr15_monitoring_plan FOREIGN KEY (tenant_id, plan_id)
        REFERENCES nr15_management_plans (tenant_id, id)
);
SELECT register_tenant_table('nr15_plan_monitoring');
