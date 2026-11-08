-- V15_2 - P3.41 (Registro unico de indicadores e metas, JOR-19).
-- Migration RESERVADA a esta tarefa (plano v3.0, secao 3: faixa reporting,
-- "registro unico de indicadores: definicao, formula, fonte, meta,
-- periodicidade, grau de confianca, fechamento e semaforo", V15_2 da
-- P3.41). Modulo intelligence/ziipsan-reporting (criado pela P3.22, V15_1).
--
-- Desenho:
--   * indicator_definitions: um registro por (tenant, codigo); variaveis em
--     indicator_variables com a fonte declarada travada por CHECK — o banco
--     recusa variavel sem fonte mesmo que o servico seja contornado.
--   * indicator_formula_versions: formula versionada com vigencia; versao
--     nova nunca reescreve a anterior (UNIQUE por versao; sem UPDATE de
--     expressao previsto no servico).
--   * indicator_targets: meta por periodo e por area (area NULL = geral),
--     com a tolerancia da faixa amarela declarada.
--   * indicator_closings: fechamento congelado (valor, versao da formula,
--     retrato JSON das variaveis, semaforo). So UM fechamento CLOSED por
--     chave (indice unico parcial); reabertura muda o status e exige
--     motivo (CHECK), preservando o historico.
--   * indicator_audit_trail: trilha de definicao, formula, meta,
--     fechamento e reabertura.
--   * NENHUM dado de tenant e semeado: indicadores sao definicao do tenant.

CREATE TABLE IF NOT EXISTS indicator_definitions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(200) NOT NULL,
    definition_text TEXT NOT NULL,
    unit VARCHAR(40) NOT NULL,
    periodicity VARCHAR(20) NOT NULL
        CHECK (periodicity IN ('MONTHLY', 'BIMONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL')),
    confidence_grade VARCHAR(10) NOT NULL
        CHECK (confidence_grade IN ('HIGH', 'MEDIUM', 'LOW')),
    context TEXT NOT NULL,
    framework_tag VARCHAR(40),
    profile_tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_indicator_definitions_tenant_code
    ON indicator_definitions(tenant_id, code);

CREATE TABLE IF NOT EXISTS indicator_variables (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    indicator_code VARCHAR(60) NOT NULL,
    name VARCHAR(80) NOT NULL,
    source_type VARCHAR(20) NOT NULL CHECK (source_type IN ('REPORT_VIEW', 'MANUAL')),
    report_view VARCHAR(80),
    manual_responsible_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID,
    -- Fonte declarada, travada no banco: REPORT_VIEW exige a view (rpt_* do
    -- nucleo da P3.22); MANUAL exige o responsavel. Sem fonte, sem linha.
    CHECK ((source_type = 'REPORT_VIEW' AND report_view IS NOT NULL
            AND report_view LIKE 'rpt\_%' AND manual_responsible_id IS NULL)
        OR (source_type = 'MANUAL' AND manual_responsible_id IS NOT NULL
            AND report_view IS NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_indicator_variables_name
    ON indicator_variables(tenant_id, indicator_code, name);

CREATE TABLE IF NOT EXISTS indicator_formula_versions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    indicator_code VARCHAR(60) NOT NULL,
    version_number INT NOT NULL CHECK (version_number >= 1),
    expression TEXT NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_indicator_formula_versions_number
    ON indicator_formula_versions(tenant_id, indicator_code, version_number);

CREATE TABLE IF NOT EXISTS indicator_targets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    indicator_code VARCHAR(60) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    area_code VARCHAR(60),
    target_value NUMERIC(20, 6) NOT NULL,
    yellow_tolerance_percent NUMERIC(7, 4) NOT NULL
        CHECK (yellow_tolerance_percent >= 0 AND yellow_tolerance_percent <= 100),
    direction VARCHAR(20) NOT NULL
        CHECK (direction IN ('HIGHER_IS_BETTER', 'LOWER_IS_BETTER')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID,
    CHECK (period_end >= period_start)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_indicator_targets_period_area
    ON indicator_targets(tenant_id, indicator_code, period_start, COALESCE(area_code, ''));

CREATE TABLE IF NOT EXISTS indicator_closings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    indicator_code VARCHAR(60) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    area_code VARCHAR(60),
    frozen_value NUMERIC(24, 10) NOT NULL,
    formula_version INT NOT NULL CHECK (formula_version >= 1),
    variable_values JSONB NOT NULL,
    semaphore VARCHAR(12) NOT NULL
        CHECK (semaphore IN ('GREEN', 'YELLOW', 'RED', 'NO_TARGET')),
    status VARCHAR(12) NOT NULL DEFAULT 'CLOSED'
        CHECK (status IN ('CLOSED', 'REOPENED')),
    closed_by UUID,
    closed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reopened_by UUID,
    reopened_at TIMESTAMPTZ,
    reopen_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID,
    CHECK (period_end >= period_start),
    -- Reabertura so com motivo e responsavel registrados.
    CHECK (status <> 'REOPENED' OR (reopen_reason IS NOT NULL AND reopen_reason <> ''
            AND reopened_by IS NOT NULL AND reopened_at IS NOT NULL))
);
-- Um so fechamento vigente (CLOSED) por chave; REOPENED fica como historico.
CREATE UNIQUE INDEX IF NOT EXISTS uq_indicator_closings_closed_key
    ON indicator_closings(tenant_id, indicator_code, period_start, COALESCE(area_code, ''))
    WHERE status = 'CLOSED';
CREATE INDEX IF NOT EXISTS idx_indicator_closings_lookup
    ON indicator_closings(tenant_id, indicator_code, period_start);

CREATE TABLE IF NOT EXISTS indicator_audit_trail (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    indicator_code VARCHAR(60) NOT NULL,
    action VARCHAR(30) NOT NULL
        CHECK (action IN ('DEFINE', 'FORMULA_VERSION', 'TARGET', 'CLOSE', 'REOPEN')),
    detail VARCHAR(500),
    reason TEXT,
    actor UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID
);
CREATE INDEX IF NOT EXISTS idx_indicator_audit_trail_indicator
    ON indicator_audit_trail(tenant_id, indicator_code, created_at);

SELECT register_tenant_table('indicator_definitions', true, false);
SELECT register_tenant_table('indicator_variables', true, false);
SELECT register_tenant_table('indicator_formula_versions', true, false);
SELECT register_tenant_table('indicator_targets', true, false);
SELECT register_tenant_table('indicator_closings', true, false);
SELECT register_tenant_table('indicator_audit_trail', true, false);
