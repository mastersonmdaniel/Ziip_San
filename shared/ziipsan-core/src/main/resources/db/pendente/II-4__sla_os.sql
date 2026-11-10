-- II-4 SLA de OS com duplo prazo + exigencias por tipo + problema-causa-remedio
-- — SQL PENDENTE DE NUMERACAO FLYWAY (prefixo do pacote: II-4).
-- Nenhum numero Flyway atribuido: na integracao, atribuir o proximo slot
-- livre da historia tenant do modulo shared/ziipsan-core, renomear o
-- arquivo e conferir FKs contra V9_1 (service_orders/service_types da
-- P1.31). Escopo: SO o que a P1.31 nao tem — a maquina de estados e as
-- tabelas da V9_1 nao sao tocadas.
-- Prazos reais (interno/regulador, por tipo): [A DEFINIR] — colunas
-- anulaveis por desenho; NULL = prazo ainda nao fixado pela fonte
-- (contrato de concessao/norma do regulador do tenant), nunca um valor
-- de fabrica. Conteudo do catalogo problema-causa-remedio: [A DEFINIR]
-- (dados do tenant; a ED-36 nao fixou catalogo — ver RELATORIO-II-4).

-- Configuracao de SLA por tipo de OS.
CREATE TABLE IF NOT EXISTS service_order_sla_configs (
    tenant_id UUID NOT NULL,
    type_code VARCHAR(60) NOT NULL,
    internal_deadline_hours INTEGER CHECK (internal_deadline_hours IS NULL OR internal_deadline_hours > 0),
    regulator_deadline_hours INTEGER CHECK (regulator_deadline_hours IS NULL OR regulator_deadline_hours > 0),
    risk_threshold_percent INTEGER NOT NULL CHECK (risk_threshold_percent BETWEEN 1 AND 99),
    escalation_thresholds_percent INTEGER[] NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, type_code),
    FOREIGN KEY (tenant_id, type_code) REFERENCES service_types(tenant_id, code)
);

-- Registo de eventos de SLA (alerta de risco, quebra, escalonamento).
CREATE TABLE IF NOT EXISTS service_order_sla_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    event_kind VARCHAR(20) NOT NULL CHECK (event_kind IN ('RISK_ALERT', 'BREACH', 'ESCALATED')),
    deadline_kind VARCHAR(20) CHECK (deadline_kind IS NULL OR deadline_kind IN ('INTERNAL', 'REGULATOR')),
    escalation_level INTEGER NOT NULL DEFAULT 0 CHECK (escalation_level >= 0),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (tenant_id, order_id) REFERENCES service_orders(tenant_id, id)
);
ALTER TABLE service_order_sla_events ADD CONSTRAINT uq_so_sla_events_tenant_id UNIQUE (tenant_id, id);

-- Exigencias de encerramento por tipo de OS.
CREATE TABLE IF NOT EXISTS service_order_close_requirements (
    tenant_id UUID NOT NULL,
    type_code VARCHAR(60) NOT NULL,
    min_cleared_photos INTEGER NOT NULL DEFAULT 0 CHECK (min_cleared_photos >= 0),
    required_checklist_items TEXT[] NOT NULL DEFAULT '{}',
    required_document_kinds TEXT[] NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, type_code),
    FOREIGN KEY (tenant_id, type_code) REFERENCES service_types(tenant_id, code)
);

-- Catalogo problema-causa-remedio do tenant (conteudo [A DEFINIR];
-- a tabela fixa apenas a estrutura problema -> causa -> remedio).
CREATE TABLE IF NOT EXISTS service_order_pcr_catalog (
    tenant_id UUID NOT NULL,
    problem_code VARCHAR(60) NOT NULL,
    cause_code VARCHAR(60) NOT NULL,
    remedy_code VARCHAR(60) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, problem_code, cause_code, remedy_code)
);

-- Codificacao registada no encerramento executado da OS.
CREATE TABLE IF NOT EXISTS service_order_close_codings (
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    problem_code VARCHAR(60) NOT NULL,
    cause_code VARCHAR(60) NOT NULL,
    remedy_code VARCHAR(60) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, order_id),
    FOREIGN KEY (tenant_id, order_id) REFERENCES service_orders(tenant_id, id),
    FOREIGN KEY (tenant_id, problem_code, cause_code, remedy_code)
        REFERENCES service_order_pcr_catalog(tenant_id, problem_code, cause_code, remedy_code)
);

-- NOTA DE INTEGRACAO: registar as 5 tabelas no mecanismo de tabelas do
-- tenant do modulo (register_tenant_table, padrao das migrations da
-- P1.31/ED-36) ao atribuir o numero Flyway; SQL nao aplicado em banco
-- nesta frente (sem PostgreSQL/Docker no ambiente) — prova dele e a CI.
