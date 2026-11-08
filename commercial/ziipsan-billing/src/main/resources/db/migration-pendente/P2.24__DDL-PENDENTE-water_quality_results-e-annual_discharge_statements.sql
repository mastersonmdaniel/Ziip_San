-- ============================================================================
-- P2.24 — DDL PENDENTE (ISTO NAO E UMA MIGRATION FLYWAY)
-- ----------------------------------------------------------------------------
-- O plano v3.0 SS3 (mapa de versoes Flyway) NAO reserva versao para a P2.24:
-- nao ha V4_x livre atribuida a esta tarefa (V4_10 e da P2.32,
-- V4_11 e fiscal/P2.33, V4_12 e da P2.34, V4_13 ja entregue) e nenhuma
-- outra faixa a nomeia. Por isso este arquivo NAO esta em
-- db/migration/tenant/ e NAO tem numero de versao: ele e o DDL pronto
-- das duas tabelas da tarefa, aguardando a reserva de versao para ser
-- promovido a migration (renomear para V<reservada>__....sql e mover).
-- Nenhum numero foi inventado.
--
-- Conteudo:
-- 1. water_quality_results — resultados mensais de qualidade da agua
--    (Decreto 5.440/2005), alimentados pela planilha do laboratorio via
--    importador generico da P1.40 (esquema billing.water_quality_lab)
--    ou pelo modulo de qualidade da ED-50, conforme
--    bill.water_quality_source.
-- 2. annual_discharge_statements — registro da declaracao anual de
--    quitacao emitida (Lei 12.007/2009), uma por (unidade, ano).
-- Forma identica a da V4_10 (P2.32): colunas herdadas de
-- BaseEntity/TenantAwareEntity fora do CREATE, como nas demais
-- migrations do modulo, e register_tenant_table para a RLS.
-- ============================================================================

CREATE TABLE IF NOT EXISTS water_quality_results (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    reference_month DATE NOT NULL CHECK (EXTRACT(DAY FROM reference_month) = 1),
    supply_system VARCHAR(120) NOT NULL,
    parameter_key VARCHAR(80) NOT NULL,
    parameter_name VARCHAR(160) NOT NULL,
    unit VARCHAR(30),
    result_text VARCHAR(120) NOT NULL,
    reference_text VARCHAR(240),
    source VARCHAR(30) NOT NULL CHECK (source IN ('LAB_SPREADSHEET', 'QUALITY_MODULE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE TABLE IF NOT EXISTS annual_discharge_statements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    reference_year INT NOT NULL,
    channel VARCHAR(30) NOT NULL CHECK (channel IN ('INVOICE_SPACE', 'ANNEX_DOCUMENT', 'PORTAL')),
    emission_trigger VARCHAR(30) NOT NULL CHECK (emission_trigger IN ('MAY_CYCLE', 'LATE_SETTLEMENT')),
    issued_bill_id UUID,
    issued_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

-- A mesma chave composta do esquema de importacao da P1.40: um resultado
-- por (mes, sistema, parametro, fonte). Reimportar a mesma planilha nao
-- duplica (a validacao da P1.40 recusa como DUPLICATE_IN_DATABASE antes).
CREATE UNIQUE INDEX IF NOT EXISTS uq_water_quality_results_key
    ON water_quality_results(tenant_id, reference_month, supply_system, parameter_key, source);

-- A declaracao anual sai UMA vez por (unidade, ano declarado).
CREATE UNIQUE INDEX IF NOT EXISTS uq_annual_discharge_statements_year
    ON annual_discharge_statements(tenant_id, unit_id, reference_year);

CREATE INDEX IF NOT EXISTS idx_water_quality_results_month
    ON water_quality_results(tenant_id, reference_month, supply_system, parameter_key);
CREATE INDEX IF NOT EXISTS idx_annual_discharge_statements_unit
    ON annual_discharge_statements(tenant_id, unit_id, reference_year);

SELECT register_tenant_table('water_quality_results');
SELECT register_tenant_table('annual_discharge_statements');
