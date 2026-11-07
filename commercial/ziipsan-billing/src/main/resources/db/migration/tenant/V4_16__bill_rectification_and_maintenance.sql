-- Billing Module Migration for Tenant Schema
-- Version: 4.16.0 (P2.20 -- retificacao e manutencao de conta pelo backoffice;
-- origem ED-21; ADR-0039; DAD-16, JOR-09)
-- Description: motivos de revisao/cancelamento em catalogo no lugar do texto
-- livre, protocol_id/service_order_id/version_no na conta, eventos de
-- situacao so de insercao, trilha por campo, revisoes abertas, registro de
-- idempotencia das operacoes e decisoes da fila de analise; registro das
-- chaves bill.* / service_type.internal_approval no catalogo global.
--
-- NUMERACAO (desvio ao plano, correcao G2 da revisao cruzada da P2.20): a
-- spec reservava a V4_8 para esta entrega, mas o numero 4.8 e inviavel no
-- historico ordenado do tenant: num tenant NOVO, a 4.8 executaria ANTES da
-- V4_14, e a V4_14 recria bills com lista explicita de colunas -- ela
-- DEFINE review_reason/cancel_reason (linhas 246/248) e as copia no
-- INSERT ... SELECT (linhas 320/328). Esta migration DERRUBA justamente
-- essas duas colunas: com o numero 4.8, a copia da V4_14 falharia e as FKs
-- novas para bills ficariam penduradas na recriacao. Precedente do projeto
-- (P2.3: V4_6->V4_14; P2.21: V13_3->V13_8): slot inviavel -> primeira
-- versao livre da serie. Na serie tenant do billing existem V4_1, V4_2,
-- V4_3, V4_4, V4_6, V4_13, V4_14 e V4_15 (e V4_5/V4_7 no fiscal, mesmo
-- historico) -- a primeira livre APOS a V4_15 e a V4_16, adotada aqui.
-- Em tenant existente o outOfOrder mascararia o problema; em tenant novo
-- (e na CI de provisionamento) ele quebra -- por isso o desvio e de
-- numero, nao de conteudo.
--
-- Depende de: V4_1 (bills), V4_4 (P2.5), V4_7 fiscal (P2.6,
-- bills.fiscal_document_id) e V4_14/V4_15 (bills particionada na forma
-- vigente -- esta migration roda DEPOIS delas, ver NUMERACAO acima).
-- A V4_4 NAO criou nada disto: la a conta so tinha
-- review_reason/cancel_reason em texto livre (da V4_1) e os recebiveis
-- ganharam service_order_id proprio -- nao ha duplicacao.
--
-- O QUE ESTA MIGRATION FAZ
-- 1. Catalogos de motivos (DAD-16): bill_review_reasons e
--    bill_cancel_reasons substituem o texto livre. O motivo de
--    cancelamento declara covered_debit_type_codes (JSON, array de codigos
--    de debit_types): no cancelamento, debito de tipo abrangido nao volta
--    a ser cobrado; os demais voltam. Catalogos comecam VAZIOS (politica
--    do prestador; nenhum motivo e inventado).
-- 2. Conta: bills ganha review_reason_id e cancel_reason_id (as colunas de
--    texto livre review_reason/cancel_reason sao REMOVIDAS -- "no lugar
--    do texto livre" do aceite; nao ha backfill possivel de texto para
--    catalogo, e manter os dois criaria duas fontes de verdade),
--    protocol_id (protocolo da P1.36 quando o pedido veio do usuario; NULL
--    na alteracao interna, ADR-0039 item 7), service_order_id (a OS interna
--    tipada da P1.31 que gerou a versao) e version_no (numero da versao na
--    cadeia da conta, DAD-14: 1 = emissao; cada manutencao insere a
--    seguinte, ligada por origin_bill_id).
-- 3. bill_status_events (DAD-16): situacao anterior e nova, motivo (por
--    id), OS, protocolo, anotacao obrigatoria e momento. SO DE INSERCAO:
--    REVOKE de UPDATE/DELETE para o papel da aplicacao + gatilho que
--    recusa UPDATE/DELETE de qualquer origem.
-- 4. bill_field_changes: trilha por campo de cada nova versao (valor
--    anterior e posterior; usuario e momento sao created_by/created_at).
--    So de insercao como os eventos (correcao M1 da revisao: REVOKE +
--    gatilho nas DUAS tabelas de trilha, sem assimetria).
-- 5. bill_reviews: revisao aberta da conta (motivo, OS, abertura e teto
--    fotografado de bill.review_max_days); no maximo uma aberta por conta.
-- 6. bill_maintenance_operations: idempotencia das operacoes
--    (Idempotency-Key unica por tenant) e base das contagens de teto
--    (retificacao contra o usuario; alteracao de vencimento no ano).
-- 7. reading_analysis_decisions (JOR-09): decisao da fila de analise
--    (liberar, manter, abrir BILL_REVIEW, pedir releitura) -- a fila e
--    consulta; a decisao e o que persiste.
-- 8. Parametros no catalogo global (public.parameter_definitions, mesmo
--    precedente da V4_4 secao 7): bill.review_effects (padrao de fabrica
--    SUSPEND_ALL, declarado no aceite), bill.review_max_days,
--    bill.rectification_limits_against_user, bill.due_change_max_per_year,
--    bill.due_change_min_days_before_due e service_type.internal_approval
--    (a P1.31 a deixou pendente de registro -- V9_7 do core; quem a
--    consome aqui a registra, ON CONFLICT DO NOTHING).
--
-- NENHUM DADO DE NEGOCIO E SEMEADO.

-- ========================================================================
-- 1. CATALOGOS DE MOTIVOS (DAD-16)
-- ========================================================================

CREATE TABLE IF NOT EXISTS bill_review_reasons (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    description VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (char_length(btrim(code)) > 0),
    CHECK (char_length(btrim(description)) > 0)
);
ALTER TABLE bill_review_reasons ADD CONSTRAINT uq_bill_review_reasons_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE bill_review_reasons ADD CONSTRAINT uq_bill_review_reasons_code UNIQUE (tenant_id, code);

CREATE TABLE IF NOT EXISTS bill_cancel_reasons (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    description VARCHAR(200) NOT NULL,
    -- Tipos de debito (debit_types.code) que o motivo abrange: no
    -- cancelamento eles NAO voltam a ser cobrados. [] = nao abrange nenhum.
    covered_debit_type_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (char_length(btrim(code)) > 0),
    CHECK (char_length(btrim(description)) > 0),
    CHECK (jsonb_typeof(covered_debit_type_codes) = 'array')
);
ALTER TABLE bill_cancel_reasons ADD CONSTRAINT uq_bill_cancel_reasons_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE bill_cancel_reasons ADD CONSTRAINT uq_bill_cancel_reasons_code UNIQUE (tenant_id, code);

-- ========================================================================
-- 2. CONTA: MOTIVO POR ID, PROTOCOLO, OS E VERSAO (DAD-16)
-- ========================================================================

ALTER TABLE bills ADD COLUMN IF NOT EXISTS review_reason_id UUID;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS cancel_reason_id UUID;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS protocol_id UUID;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS service_order_id UUID;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS version_no INT NOT NULL DEFAULT 1;
ALTER TABLE bills ADD CONSTRAINT chk_bills_version_no CHECK (version_no >= 1);
ALTER TABLE bills ADD CONSTRAINT fk_bills_review_reason
    FOREIGN KEY (tenant_id, review_reason_id) REFERENCES bill_review_reasons(tenant_id, id);
ALTER TABLE bills ADD CONSTRAINT fk_bills_cancel_reason
    FOREIGN KEY (tenant_id, cancel_reason_id) REFERENCES bill_cancel_reasons(tenant_id, id);

-- O texto livre sai (aceite: os ids entram "no lugar do texto livre").
ALTER TABLE bills DROP COLUMN IF EXISTS review_reason;
ALTER TABLE bills DROP COLUMN IF EXISTS cancel_reason;

CREATE INDEX idx_bills_service_order ON bills(tenant_id, service_order_id)
    WHERE service_order_id IS NOT NULL;
CREATE INDEX idx_bills_protocol ON bills(tenant_id, protocol_id)
    WHERE protocol_id IS NOT NULL;
-- Cadeia de versoes da conta (portal/relatorios leem a vigente; a aba
-- Historico da P1.31 lista a cadeia por esta ordem).
CREATE INDEX idx_bills_version_chain ON bills(tenant_id, unit_id, reference_month, version_no);

-- ========================================================================
-- 3. EVENTOS DE SITUACAO -- SO DE INSERCAO (DAD-16)
-- ========================================================================

CREATE TABLE IF NOT EXISTS bill_status_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    bill_id UUID NOT NULL,
    bill_reference_month DATE NOT NULL CHECK (EXTRACT(DAY FROM bill_reference_month) = 1),
    previous_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    review_reason_id UUID,
    cancel_reason_id UUID,
    service_order_id UUID,
    protocol_id UUID,
    note VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (char_length(btrim(note)) > 0)
);
ALTER TABLE bill_status_events ADD CONSTRAINT uq_bill_status_events_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE bill_status_events ADD CONSTRAINT fk_bill_status_events_bill
    FOREIGN KEY (tenant_id, bill_reference_month, bill_id) REFERENCES bills(tenant_id, reference_month, id);
ALTER TABLE bill_status_events ADD CONSTRAINT fk_bill_status_events_review_reason
    FOREIGN KEY (tenant_id, review_reason_id) REFERENCES bill_review_reasons(tenant_id, id);
ALTER TABLE bill_status_events ADD CONSTRAINT fk_bill_status_events_cancel_reason
    FOREIGN KEY (tenant_id, cancel_reason_id) REFERENCES bill_cancel_reasons(tenant_id, id);
CREATE INDEX idx_bill_status_events_bill ON bill_status_events(tenant_id, bill_id, occurred_at);

CREATE OR REPLACE FUNCTION forbid_bill_status_event_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'bill_status_events e so de insercao (DAD-16): % recusado', TG_OP;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_bill_status_events_insert_only ON bill_status_events;
CREATE TRIGGER trg_bill_status_events_insert_only
    BEFORE UPDATE OR DELETE ON bill_status_events
    FOR EACH ROW EXECUTE FUNCTION forbid_bill_status_event_mutation();
REVOKE UPDATE, DELETE ON bill_status_events FROM ziipsan_app;

-- ========================================================================
-- 4. TRILHA POR CAMPO
-- ========================================================================

CREATE TABLE IF NOT EXISTS bill_field_changes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    bill_id UUID NOT NULL,
    bill_reference_month DATE NOT NULL CHECK (EXTRACT(DAY FROM bill_reference_month) = 1),
    previous_bill_id UUID NOT NULL,
    service_order_id UUID,
    field_name VARCHAR(40) NOT NULL,
    previous_value VARCHAR(100),
    new_value VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (char_length(btrim(field_name)) > 0)
);
ALTER TABLE bill_field_changes ADD CONSTRAINT uq_bill_field_changes_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE bill_field_changes ADD CONSTRAINT fk_bill_field_changes_bill
    FOREIGN KEY (tenant_id, bill_reference_month, bill_id) REFERENCES bills(tenant_id, reference_month, id);
CREATE INDEX idx_bill_field_changes_bill ON bill_field_changes(tenant_id, bill_id, created_at);

CREATE OR REPLACE FUNCTION forbid_bill_field_change_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'bill_field_changes e so de insercao (DAD-16): % recusado', TG_OP;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_bill_field_changes_insert_only ON bill_field_changes;
CREATE TRIGGER trg_bill_field_changes_insert_only
    BEFORE UPDATE OR DELETE ON bill_field_changes
    FOR EACH ROW EXECUTE FUNCTION forbid_bill_field_change_mutation();
REVOKE UPDATE, DELETE ON bill_field_changes FROM ziipsan_app;

-- ========================================================================
-- 5. REVISOES ABERTAS
-- ========================================================================

CREATE TABLE IF NOT EXISTS bill_reviews (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    bill_id UUID NOT NULL,
    bill_reference_month DATE NOT NULL CHECK (EXTRACT(DAY FROM bill_reference_month) = 1),
    review_reason_id UUID NOT NULL,
    service_order_id UUID,
    protocol_id UUID,
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
    opened_on DATE NOT NULL,
    deadline_on DATE NOT NULL,
    closed_on DATE,
    note VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (deadline_on >= opened_on),
    CHECK ((status = 'CLOSED') = (closed_on IS NOT NULL))
);
ALTER TABLE bill_reviews ADD CONSTRAINT uq_bill_reviews_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE bill_reviews ADD CONSTRAINT fk_bill_reviews_bill
    FOREIGN KEY (tenant_id, bill_reference_month, bill_id) REFERENCES bills(tenant_id, reference_month, id);
ALTER TABLE bill_reviews ADD CONSTRAINT fk_bill_reviews_reason
    FOREIGN KEY (tenant_id, review_reason_id) REFERENCES bill_review_reasons(tenant_id, id);
CREATE UNIQUE INDEX idx_bill_reviews_one_open ON bill_reviews(tenant_id, bill_id)
    WHERE status = 'OPEN';
CREATE INDEX idx_bill_reviews_open ON bill_reviews(tenant_id, status, deadline_on);

-- ========================================================================
-- 6. IDEMPOTENCIA DAS OPERACOES
-- ========================================================================

CREATE TABLE IF NOT EXISTS bill_maintenance_operations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    operation VARCHAR(40) NOT NULL,
    bill_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    result_bill_id UUID,
    service_order_id UUID,
    status VARCHAR(20) NOT NULL CHECK (status IN ('EXECUTED', 'AWAITING_APPROVAL')),
    against_user BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE bill_maintenance_operations ADD CONSTRAINT uq_bill_maintenance_operations_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE bill_maintenance_operations ADD CONSTRAINT uq_bill_maintenance_operations_key UNIQUE (tenant_id, idempotency_key);
CREATE INDEX idx_bill_maintenance_operations_unit_year ON bill_maintenance_operations(tenant_id, unit_id, created_at);

-- ========================================================================
-- 7. FILA DE ANALISE: DECISOES (JOR-09)
-- ========================================================================

CREATE TABLE IF NOT EXISTS reading_analysis_decisions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    reading_id UUID,
    bill_id UUID,
    unit_id UUID NOT NULL,
    action VARCHAR(20) NOT NULL CHECK (action IN ('RELEASE', 'KEEP_HELD', 'OPEN_REVIEW', 'REQUEST_REREADING')),
    service_order_id UUID,
    note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (reading_id IS NOT NULL OR bill_id IS NOT NULL)
);
ALTER TABLE reading_analysis_decisions ADD CONSTRAINT uq_reading_analysis_decisions_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_reading_analysis_decisions_unit ON reading_analysis_decisions(tenant_id, unit_id, created_at);

-- ========================================================================
-- 8. PARAMETROS NO CATALOGO GLOBAL (public.parameter_definitions)
-- ========================================================================

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('bill.review_effects', 'ENUM', NULL, '"SUSPEND_ALL"',
     'P2.20 (aceite, DAD-16)',
     NULL,
     'Efeitos suspensos por revisao aberta de conta: SUSPEND_ALL ou lista de COLLECTION, LATE_FEES, NEGATIVE_LISTING, CUT, AUTO_DEBIT. Padrao SUSPEND_ALL declarado no aceite. O corte de conta em revisao e sempre vedado, mesmo fora da lista.'),
    ('bill.review_max_days', 'INT', NULL, NULL,
     'P2.20 (aceite, DAD-16)',
     NULL,
     'Teto em dias de uma revisao aberta de conta. Sem padrao de fabrica no plano: ausencia e erro explicito.'),
    ('bill.rectification_limits_against_user', 'OBJECT', NULL, NULL,
     'P2.20 (aceite, DAD-16)',
     NULL,
     'Limites de retificacao que so alcancam retificacao contra o usuario: {"maxPerUnitAndYear": n}; sem a chave maxPerUnitAndYear, sem teto numerico. Correcao a favor do usuario nunca e limitada.'),
    ('bill.due_change_max_per_year', 'INT', NULL, NULL,
     'P2.20 (aceite, DAD-16)',
     NULL,
     'Maximo de alteracoes de vencimento por conta e ano civil. Conta = a cadeia de versoes da conta (mesma unidade e mesma referencia; a contagem soma as alteracoes de todas as versoes da cadeia no ano, sobre bill_maintenance_operations). Sem padrao de fabrica no plano: ausencia e erro explicito.'),
    ('bill.due_change_min_days_before_due', 'INT', NULL, NULL,
     'P2.20 (aceite, DAD-16)',
     NULL,
     'Antecedencia minima, em dias, entre hoje e o vencimento vigente para alterar o vencimento. Sem padrao de fabrica no plano: ausencia e erro explicito.'),
    ('service_type.internal_approval', 'OBJECT', NULL, NULL,
     'P1.31 (ADR-0039, item 8); registro pendente apontado na V9_7 do core',
     NULL,
     'Sobreposicao por tipo de OS interna da exigencia de aprovacao: {tipo: "REQUIRED"|"NONE"}. Sem entrada para o tipo, vale o requires_approval de fabrica declarado no tipo (service_types).')
ON CONFLICT (definition_key) DO NOTHING;

-- ========================================================================
-- 9. ISOLAMENTO (RLS/auditoria, padrao register_tenant_table)
-- ========================================================================

SELECT register_tenant_table('bill_review_reasons');
SELECT register_tenant_table('bill_cancel_reasons');
SELECT register_tenant_table('bill_status_events');
SELECT register_tenant_table('bill_field_changes');
SELECT register_tenant_table('bill_reviews');
SELECT register_tenant_table('bill_maintenance_operations');
SELECT register_tenant_table('reading_analysis_decisions');
