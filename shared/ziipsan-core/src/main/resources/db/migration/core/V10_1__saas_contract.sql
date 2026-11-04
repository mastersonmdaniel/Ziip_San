-- Core Migration — Version: 10.1
-- Description: P2.12 — cobranca do SaaS por contrato (ADR-0019), no schema
-- `platform`, antes do primeiro contrato.
--
-- Tabelas do plano v3.0 §3 (core `V10_1`, reservada a P2.12–P2.14):
-- `saas_contract` (com a politica de SLA referenciada), `saas_contract_addendum`,
-- `saas_commitment` (empenho), `saas_readjustment`, `saas_invoice`,
-- `saas_payment`, `saas_restriction_notice`, `usage_ledger`,
-- `usage_connection_snapshot`, `usage_statement` e
-- `provider_cost_allocation`.
--
-- Decisoes de modelo (todas documentadas aqui para a revisao; nenhuma
-- delas afrouxa a ADR-0019):
--   * A politica de SLA e REFERENCIADA, nao FK: `support.sla_policy` so
--     nasce no core `V10_2` (P2.15). `saas_contract.sla_policy_key` guarda
--     a chave da politica; a P2.15 valida a referencia e pode promover a
--     coluna a FK sem reescrever esta migration (ponto de extensao).
--   * `saas_invoice` carrega `invoice_document_ref` e `nfse_document_ref`
--     como referencias de documento (texto), nao o documento em si: a
--     fatura paga em PDF e a NFS-e em PDF/XML chegam na P2.13 (motor
--     fiscal) e na P2.14 (area do tenant), que resolvem as referencias.
--   * Saldo do empenho: ao emitir fatura vinculada, o servico abate o
--     total do saldo. Saldo negativo NAO recusa a emissao (a ADR-0019 nao
--     a proibe): o alerta `saas.contract.commitment_low` (saldo abaixo
--     de duas faturas) e o que sinaliza, e o financeiro decide.
--   * Reajuste: a proposta nasce PROPOSED e so vira APPLIED com
--     `applied_addendum_id` (apostilamento ou aditivo registrado). O
--     CHECK abaixo trava isso no banco, nao so no servico.
--   * `usage_ledger` nao tem coluna de dado pessoal, por desenho: so
--     (tenant, mes, metrica, quantidade). A prova e o teste de colunas
--     (nenhuma coluna fora da lista fechada) e a ausencia de qualquer
--     identificador de usuario, unidade consumidora ou mensagem.
--   * Fotografia (`usage_connection_snapshot`): o hash e SHA-256 do
--     conteudo canonico (tenant, mes, quantidade). Alterar a quantidade
--     sem recalcular o hash faz `verify` falhar — e o teste prova isso
--     com UPDATE direto no banco. O hash nao e segredo: ele detecta
--     alteracao posterior, nao autenticacao; a entrega ao cliente e o
--     `usage_statement` com janela de contestacao.
--   * Cotas: `saas_contract.quotas` guarda as chaves `quota.*` do
--     contrato (ex.: `quota.active_connections`); a medida e o mesmo
--     `usage_ledger` do mes, nunca uma segunda contagem.
--   * NENHUMA tabela desta migration toca `tenant_lifecycle`: alertas,
--     faturas vencidas e cotas estouradas nunca suspendem sozinhos. A
--     unica escrita em `tenant_lifecycle` ligada a contrato passa pelo
--     servico de restricao manual (aviso + ativacao), com MFA, step-up,
--     justificativa, fatura vencida e aviso registrados — e o teste
--     prova que a avaliacao de alertas nao altera o ciclo de vida.
--   * Alertas do item 7 da ADR-0019 vivem em `platform.alert_event`
--     (tabela da V9_1), com regras proprias abaixo; o indice unico
--     parcial da V9_1 impede duplicar um alerta aberto por regra+tenant.
--
-- Versao livre verificada: `V10` esta reservada a P2.1 (job_runs) e
-- `V10_2` a P2.15; `V10_1` e a faixa desta tarefa. Idempotente
-- (IF NOT EXISTS / ON CONFLICT DO NOTHING) no que e semente.

-- ======================================================================
-- Contrato SaaS e seus anexos
-- ======================================================================

CREATE TABLE IF NOT EXISTS platform.saas_contract (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    contract_number VARCHAR(100) NOT NULL UNIQUE,
    bidding_process VARCHAR(100),
    bidding_modality VARCHAR(100),
    contractor_cnpj CHAR(14) NOT NULL CHECK (contractor_cnpj ~ '^[0-9A-Z]{14}$'),
    starts_at DATE NOT NULL,
    ends_at DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'ACTIVE', 'EXPIRED', 'TERMINATED')),
    monthly_value NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (monthly_value >= 0),
    index_key VARCHAR(100),
    index_base_date DATE,
    -- Referencia a politica de SLA de suporte (ADR-0020, core V10_2 /
    -- P2.15): chave, nao FK, ate a politica existir. NULL = sem politica
    -- referenciada (contrato ainda sem SLA contratado).
    sla_policy_key VARCHAR(100),
    -- Cotas do contrato em JSONB, chaves `quota.*` (ex.:
    -- {"quota.active_connections": 5000, "quota.users": 120,
    -- "quota.storage_mb": 10240}). Medidas pelo usage_ledger.
    quotas JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (ends_at >= starts_at)
);

CREATE INDEX IF NOT EXISTS idx_saas_contract_tenant
    ON platform.saas_contract (tenant_id, status);

CREATE TABLE IF NOT EXISTS platform.saas_contract_addendum (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    contract_id UUID NOT NULL REFERENCES platform.saas_contract (id),
    addendum_number VARCHAR(100) NOT NULL,
    kind VARCHAR(20) NOT NULL
        CHECK (kind IN ('TERM', 'VALUE', 'OBJECT', 'READJUSTMENT')),
    description TEXT NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    value_delta NUMERIC(19, 2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_saas_addendum_contract_number UNIQUE (contract_id, addendum_number),
    CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE TABLE IF NOT EXISTS platform.saas_commitment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    contract_id UUID NOT NULL REFERENCES platform.saas_contract (id),
    commitment_number VARCHAR(100) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount >= 0),
    balance NUMERIC(19, 2) NOT NULL,
    issued_at DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_saas_commitment_contract_number UNIQUE (contract_id, commitment_number)
);

CREATE SEQUENCE IF NOT EXISTS platform.saas_invoice_seq;

CREATE TABLE IF NOT EXISTS platform.saas_readjustment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    contract_id UUID NOT NULL REFERENCES platform.saas_contract (id),
    index_key VARCHAR(100) NOT NULL,
    index_value_pct NUMERIC(10, 4) NOT NULL,
    base_monthly_value NUMERIC(19, 2) NOT NULL CHECK (base_monthly_value >= 0),
    proposed_monthly_value NUMERIC(19, 2) NOT NULL CHECK (proposed_monthly_value >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PROPOSED'
        CHECK (status IN ('PROPOSED', 'APPLIED', 'REJECTED')),
    applied_addendum_id UUID REFERENCES platform.saas_contract_addendum (id),
    proposed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    applied_at TIMESTAMPTZ,
    CHECK (status <> 'APPLIED' OR applied_addendum_id IS NOT NULL),
    CHECK (status <> 'APPLIED' OR applied_at IS NOT NULL)
);

-- ======================================================================
-- Faturas e pagamentos conciliados
-- ======================================================================

CREATE TABLE IF NOT EXISTS platform.saas_invoice (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    contract_id UUID NOT NULL REFERENCES platform.saas_contract (id),
    commitment_id UUID REFERENCES platform.saas_commitment (id),
    invoice_number VARCHAR(100) NOT NULL UNIQUE,
    competence CHAR(7) NOT NULL CHECK (competence ~ '^[0-9]{4}-[0-9]{2}$'),
    total NUMERIC(19, 2) NOT NULL CHECK (total >= 0),
    paid_amount NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
    due_date DATE NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'PARTIALLY_PAID', 'PAID', 'CANCELLED')),
    invoice_document_ref VARCHAR(500),
    nfse_document_ref VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_saas_invoice_contract_competence
    ON platform.saas_invoice (contract_id, competence);
CREATE INDEX IF NOT EXISTS idx_saas_invoice_due_status
    ON platform.saas_invoice (due_date, status);

CREATE TABLE IF NOT EXISTS platform.saas_payment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    -- NULL = pagamento sem fatura correspondente (alerta do item 7 da
    -- ADR-0019); nunca inventar fatura para casa-lo.
    invoice_id UUID REFERENCES platform.saas_invoice (id),
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    paid_at DATE NOT NULL,
    bank_order_ref VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_saas_payment_invoice
    ON platform.saas_payment (invoice_id);
CREATE INDEX IF NOT EXISTS idx_saas_payment_tenant_unmatched
    ON platform.saas_payment (tenant_id) WHERE invoice_id IS NULL;

-- ======================================================================
-- Restricao manual (aviso previo + ativacao). A restricao em si vive
-- em platform.tenant_lifecycle (V9), com motivo CONTRACT_DEFAULT; esta
-- tabela e o elo exigido pela ADR-0019: fatura vencida + aviso.
-- ======================================================================

CREATE TABLE IF NOT EXISTS platform.saas_restriction_notice (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    contract_id UUID NOT NULL REFERENCES platform.saas_contract (id),
    invoice_id UUID NOT NULL REFERENCES platform.saas_invoice (id),
    justification TEXT NOT NULL,
    actor_id UUID,
    notice_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    effective_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'EFFECTIVE')),
    activated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (status <> 'EFFECTIVE' OR activated_at IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_saas_restriction_notice_tenant
    ON platform.saas_restriction_notice (tenant_id, status);

-- ======================================================================
-- Registro de uso (sem dado pessoal) e fotografia mensal
-- ======================================================================

CREATE TABLE IF NOT EXISTS platform.usage_ledger (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    period_month DATE NOT NULL,
    metric_key VARCHAR(200) NOT NULL,
    quantity NUMERIC(19, 4) NOT NULL CHECK (quantity >= 0),
    unit VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_usage_ledger_tenant_month_metric
        UNIQUE (tenant_id, period_month, metric_key),
    CHECK (period_month = date_trunc('month', period_month)::DATE)
);

CREATE INDEX IF NOT EXISTS idx_usage_ledger_tenant_month
    ON platform.usage_ledger (tenant_id, period_month);

CREATE TABLE IF NOT EXISTS platform.usage_connection_snapshot (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    period_month DATE NOT NULL,
    active_connections BIGINT NOT NULL CHECK (active_connections >= 0),
    snapshot_hash CHAR(64) NOT NULL CHECK (snapshot_hash ~ '^[0-9a-f]{64}$'),
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_usage_snapshot_tenant_month UNIQUE (tenant_id, period_month),
    CHECK (period_month = date_trunc('month', period_month)::DATE)
);

CREATE TABLE IF NOT EXISTS platform.usage_statement (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    period_month DATE NOT NULL,
    snapshot_id UUID NOT NULL REFERENCES platform.usage_connection_snapshot (id),
    status VARCHAR(20) NOT NULL DEFAULT 'DELIVERED'
        CHECK (status IN ('DELIVERED', 'CONTESTED', 'CONFIRMED')),
    delivered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    contest_until TIMESTAMPTZ NOT NULL,
    contested_at TIMESTAMPTZ,
    contest_note TEXT,
    confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_usage_statement_tenant_month UNIQUE (tenant_id, period_month),
    CHECK (status <> 'CONTESTED' OR contested_at IS NOT NULL),
    CHECK (status <> 'CONFIRMED' OR confirmed_at IS NOT NULL)
);

-- ======================================================================
-- Custo do provedor atribuido por tenant
-- ======================================================================

CREATE TABLE IF NOT EXISTS platform.provider_cost_allocation (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    period_month DATE NOT NULL,
    cost_key VARCHAR(200) NOT NULL,
    basis VARCHAR(20) NOT NULL
        CHECK (basis IN ('TAG', 'PROXY_SCHEMA_SIZE', 'PROXY_MESSAGES', 'PROXY_TOKENS')),
    amount NUMERIC(19, 2) NOT NULL CHECK (amount >= 0),
    notes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_provider_cost_tenant_month_key_basis
        UNIQUE (tenant_id, period_month, cost_key, basis),
    CHECK (period_month = date_trunc('month', period_month)::DATE)
);

-- ======================================================================
-- Parametro e alertas (item 7 da ADR-0019)
-- ======================================================================

-- Janela de contestacao do relatorio de uso, em dias, classe d (livre,
-- P2.12). Padrao de fabrica 30 dias: o plano e a ADR-0019 fixam a
-- existencia da janela, nao o numero — o numero e pendencia declarada
-- do dono (ver relatorio da P2.12), e o servico le este padrao em vez
-- de fixar o numero no codigo.
INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('usage.statement_contest_days', 'INT', 'd', '30',
     'P2.12; ADR-0019 (janela de contestacao do relatorio de uso)',
     NULL,
     'Dias para contestar o relatorio mensal de uso (fotografia de ligacoes ativas) a partir da entrega. Classe d. O numero de dias e pendencia declarada do dono: o plano fixa a janela, nao o numero.')
ON CONFLICT (definition_key) DO NOTHING;

INSERT INTO platform.metric_definition (metric_key, unit, metric_type, description, version, owner)
VALUES
    ('saas.contract.days_to_expiry', 'days', 'gauge', 'Dias para o fim da vigencia do contrato SaaS', 1, 'platform'),
    ('saas.contract.commitment_balance_invoices', 'invoices', 'gauge', 'Saldo do empenho do contrato SaaS, em faturas mensais (saldo / valor mensal)', 1, 'platform'),
    ('saas.contract.quota_ratio', 'ratio', 'gauge', 'Uso sobre a cota do contrato (1 = 100%)', 1, 'platform'),
    ('saas.invoice.overdue_count', 'count', 'gauge', 'Faturas vencidas e nao pagas do contrato SaaS', 1, 'platform'),
    ('saas.payment.unmatched_count', 'count', 'gauge', 'Pagamentos sem fatura correspondente', 1, 'platform')
ON CONFLICT (metric_key) DO NOTHING;

-- Regras dos alertas do item 7. O vencimento usa uma regra so, com
-- severidade calculada na avaliacao (WARNING a 180/90 dias, CRITICAL a
-- 30); a cota usa duas regras (80% WARNING, 100% CRITICAL), porque o
-- indice unico da V9_1 permite um evento aberto por regra+tenant. O
-- saldo de empenho e medido em faturas (saldo / valor mensal), como a
-- ADR-0019 o define ("abaixo de duas faturas"); com valor mensal zero,
-- o avaliador trata saldo positivo como folga e saldo zero como alerta.
INSERT INTO platform.alert_rule (rule_key, metric_key, comparator, threshold,
    consecutive_readings, severity, scope, owner)
VALUES
    ('saas.contract.expiring', 'saas.contract.days_to_expiry', '<=', 180, 1, 'WARNING', 'TENANT', 'platform'),
    ('saas.contract.commitment_low', 'saas.contract.commitment_balance_invoices', '<', 2, 1, 'WARNING', 'TENANT', 'platform'),
    ('saas.contract.quota_80', 'saas.contract.quota_ratio', '>=', 0.8, 1, 'WARNING', 'TENANT', 'platform'),
    ('saas.contract.quota_100', 'saas.contract.quota_ratio', '>=', 1, 1, 'CRITICAL', 'TENANT', 'platform'),
    ('saas.invoice.overdue', 'saas.invoice.overdue_count', '>=', 1, 1, 'WARNING', 'TENANT', 'platform'),
    ('saas.payment.unmatched', 'saas.payment.unmatched_count', '>=', 1, 1, 'WARNING', 'TENANT', 'platform')
ON CONFLICT (rule_key) DO NOTHING;

-- ======================================================================
-- Privilegios (padrao da V9/V9_1: app escreve, central so le). UPDATE
-- junto do INSERT onde o servico usa ON CONFLICT DO UPDATE (snapshot,
-- custo, ledger, relatorio): sem ele, o upsert falha com permission
-- denied para a role da aplicacao, mesmo com INSERT concedido.
-- ======================================================================

GRANT USAGE ON SCHEMA platform TO ziipsan_app, ziipsan_central;
GRANT SELECT, INSERT, UPDATE ON platform.saas_contract TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.saas_contract_addendum TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.saas_commitment TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.saas_readjustment TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.saas_invoice TO ziipsan_app;
GRANT SELECT, INSERT ON platform.saas_payment TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.saas_restriction_notice TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.usage_ledger TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.usage_connection_snapshot TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.usage_statement TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.provider_cost_allocation TO ziipsan_app;
GRANT USAGE, SELECT ON SEQUENCE platform.saas_invoice_seq TO ziipsan_app;
GRANT SELECT ON platform.saas_contract TO ziipsan_central;
GRANT SELECT ON platform.saas_contract_addendum TO ziipsan_central;
GRANT SELECT ON platform.saas_commitment TO ziipsan_central;
GRANT SELECT ON platform.saas_readjustment TO ziipsan_central;
GRANT SELECT ON platform.saas_invoice TO ziipsan_central;
GRANT SELECT ON platform.saas_payment TO ziipsan_central;
GRANT SELECT ON platform.saas_restriction_notice TO ziipsan_central;
GRANT SELECT ON platform.usage_ledger TO ziipsan_central;
GRANT SELECT ON platform.usage_connection_snapshot TO ziipsan_central;
GRANT SELECT ON platform.usage_statement TO ziipsan_central;
GRANT SELECT ON platform.provider_cost_allocation TO ziipsan_central;
GRANT SELECT ON public.parameter_definitions TO ziipsan_app;
