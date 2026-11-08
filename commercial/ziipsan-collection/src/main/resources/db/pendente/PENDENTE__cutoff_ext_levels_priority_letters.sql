-- ED-29 (complementos de cobranca: corte e religacao por nivel) — DDL PENDENTE DE NUMERO.
--
-- POR QUE SEM NUMERO: a sequencia de migrations do tenant e GLOBAL e o
-- proximo numero livre depende da maior versao publicada no repo no
-- momento da integracao — nao confirmavel a partir dos pacotes locais
-- (precedentes: P4.1, P4.5, ED-28, ED-36). Na integracao, este arquivo
-- vira V<nnn>__cutoff_ext_levels_priority_letters.sql. NENHUM numero
-- foi inventado aqui.
--
-- Conteudo:
--   * cutoff_level_executions: execucao do corte POR NIVEL (TR-539/
--     SG-501: cavalete, ramal, supressao), com a situacao resultante da
--     ligacao; os niveis VALVE/BRANCH sao os da P3.4 (V9_2) e a
--     SUPPRESSION e o nivel novo da ED-29 (efeito: ligacao SUPRIMIDA).
--   * cutoff_daily_plans / cutoff_daily_plan_items: o planejamento do
--     dia (TR-409/SG-393 prioridade; TR-410/SG-394 cota), com as
--     adiadas e o motivo registrado — nada e descartado em silencio.
--   * cut_unit_notices: notificacoes de pos-corte das unidades cortadas
--     (POC-32 / TR-401), com as variaveis CONGELADAS na emissao e o
--     codigo/versao do modelo da P2.30 — a reimpressao da POC-33 le
--     ESTA linha, nunca re-renderiza (regra da P2.30).
--   * collection_letters: cartas de cobranca emitidas (TR-390/SG-375),
--     com o retrato dos filtros aplicados.
--   * sewer_release_letters: cartas de liberacao de esgoto (SG-504;
--     comunicado de texto parametrizado, TR-581/SG-558).
--   * Definicoes de parametros cut.* novos em
--     public.parameter_definitions (ON CONFLICT DO NOTHING), TODOS sem
--     padrao de fabrica: cota, ordem de prioridade, calendario por
--     nivel, codigos de modelo e passo de corte da regua sao do tenant.
--
-- Dinheiro: NUMERIC(14,2). NENHUM valor de tenant e semeado.

-- ========================================================================
-- EXECUCAO POR NIVEL (TR-539/SG-501)
-- ========================================================================

CREATE TABLE IF NOT EXISTS cutoff_level_executions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    service_order_id UUID,
    cut_level VARCHAR(16) NOT NULL
        CHECK (cut_level IN ('VALVE', 'BRANCH', 'SUPPRESSION')),
    resulting_water_status VARCHAR(16) NOT NULL
        CHECK (resulting_water_status IN ('CUT', 'SUPPRESSED')),
    executed_at TIMESTAMPTZ NOT NULL,
    executed_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((cut_level = 'SUPPRESSION') = (resulting_water_status = 'SUPPRESSED'))
);

CREATE INDEX IF NOT EXISTS idx_cutoff_level_exec_unit
    ON cutoff_level_executions (tenant_id, unit_id, executed_at);

-- ========================================================================
-- PLANEJAMENTO DIARIO: PRIORIDADE E COTA (TR-409/SG-393; TR-410/SG-394)
-- ========================================================================

CREATE TABLE IF NOT EXISTS cutoff_daily_plans (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    plan_date DATE NOT NULL,
    daily_limit INT NOT NULL CHECK (daily_limit > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, plan_date)
);

CREATE TABLE IF NOT EXISTS cutoff_daily_plan_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    plan_id UUID NOT NULL REFERENCES cutoff_daily_plans (id),
    unit_id UUID NOT NULL,
    cut_level VARCHAR(16) NOT NULL
        CHECK (cut_level IN ('VALVE', 'BRANCH', 'SUPPRESSION')),
    outcome VARCHAR(16) NOT NULL CHECK (outcome IN ('PLANNED', 'DEFERRED')),
    position INT CHECK (position IS NULL OR position > 0),
    defer_reason VARCHAR(64),
    special_crew_only BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((outcome = 'PLANNED') = (position IS NOT NULL)),
    CHECK ((outcome = 'DEFERRED') = (defer_reason IS NOT NULL))
);

-- ========================================================================
-- NOTIFICACOES DE POS-CORTE (POC-32/POC-33; TR-401; TR-405)
-- ========================================================================

CREATE TABLE IF NOT EXISTS cut_unit_notices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    template_version VARCHAR(32) NOT NULL,
    document_ref VARCHAR(128) NOT NULL,
    frozen_variables JSONB NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cut_unit_notices_query
    ON cut_unit_notices (tenant_id, unit_id, issued_at);

-- ========================================================================
-- CARTAS DE COBRANCA (TR-390/SG-375)
-- ========================================================================

CREATE TABLE IF NOT EXISTS collection_letters (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    template_version VARCHAR(32) NOT NULL,
    document_ref VARCHAR(128) NOT NULL,
    filters_snapshot JSONB NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ========================================================================
-- CARTAS DE LIBERACAO DE ESGOTO (SG-504; TR-581/SG-558)
-- ========================================================================

CREATE TABLE IF NOT EXISTS sewer_release_letters (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    template_version VARCHAR(32) NOT NULL,
    document_ref VARCHAR(128) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ========================================================================
-- DEFINICOES DE PARAMETROS (sem padrao de fabrica: ausencia = erro)
-- ========================================================================

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('cut.daily_order_limit', 'INT', 'd', NULL,
     'TR-410/SG-394 (editais VISAN/SAMAE)', 'OPR',
     'Quantidade maxima de OS de corte geradas por dia. Sem valor aceito, nao ha planejamento diario de corte.'),
    ('cut.priority_order', 'JSON', 'd', NULL,
     'TR-409/SG-393 (editais VISAN/SAMAE)', 'OPR',
     'Ordem dos criterios de prioridade das OS de corte e postos de tarifa, categoria e perfil do devedor.'),
    ('cut.level_schedule', 'JSON', 'd', NULL,
     'TR-415/SG-398 e TR-417/SG-400 (editais VISAN/SAMAE)', 'OPR',
     'Dias, faixa horaria e intervalo minimo por nivel de corte (cavalete, ramal, supressao). Nivel sem linha nao pode ser gerado.'),
    ('cut.collection_letter_template', 'STRING', 'd', NULL,
     'TR-390/SG-375; motor de modelos da P2.30 (JOR-06)', 'OPR',
     'Codigo do modelo da carta de cobranca no catalogo de modelos do tenant.'),
    ('cut.cut_unit_notice_template', 'STRING', 'd', NULL,
     'TR-401/SG-386 (POC-32); motor de modelos da P2.30 (JOR-06)', 'OPR',
     'Codigo do modelo da notificacao para unidade com ligacao de agua cortada.'),
    ('cut.sewer_release_template', 'STRING', 'd', NULL,
     'TR-581/SG-558 e SG-504; motor de modelos da P2.30 (JOR-06)', 'OPR',
     'Codigo do modelo da carta de liberacao de esgoto (texto parametrizado).'),
    ('cut.ruler_cutoff_step_code', 'STRING', 'd', NULL,
     'Regua de cobranca da P4.1 (CAD-100, dun.ruler)', 'OPR',
     'Codigo, na regua do tenant, do passo que manda gerar a OS de corte.')
ON CONFLICT (definition_key) DO NOTHING;

-- ========================================================================
-- ISOLAMENTO (padrao register_tenant_table)
-- ========================================================================

SELECT register_tenant_table('cutoff_level_executions');
SELECT register_tenant_table('cutoff_daily_plans');
SELECT register_tenant_table('cutoff_daily_plan_items');
SELECT register_tenant_table('cut_unit_notices');
SELECT register_tenant_table('collection_letters');
SELECT register_tenant_table('sewer_release_letters');
