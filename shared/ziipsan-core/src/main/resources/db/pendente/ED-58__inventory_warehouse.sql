-- ED-58 — Almoxarifado: materiais e estoque para a OS, com alcada e baixa
-- automatica — SQL PENDENTE DE NUMERACAO FLYWAY (prefixo ED-58__).
-- Nenhum numero foi inventado: na integracao, atribuir o proximo slot livre
-- da historia tenant do modulo shared/ziipsan-core e renomear o arquivo.
-- NENHUM valor de tenant e semeado: alcadas, limite de autoaprovacao, prazo
-- de regularizacao e janela de validade sao parametros por tenant SEM
-- fabrica ([A DEFINIR]); ponto de reposicao e por item, tambem sem numero
-- presumido. Os 14 valores da P3.9 NAO sao tocados por esta entrega.

-- (1) Locais e itens ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS inv_locations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_locations_code ON inv_locations (tenant_id, code);

CREATE TABLE IF NOT EXISTS inv_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('NETWORK_MATERIAL','METER','REAGENT','CHEMICAL','PPE','CONSUMABLE')),
    unit_of_measure VARCHAR(20) NOT NULL,
    controlled BOOLEAN NOT NULL DEFAULT FALSE,
    serialized BOOLEAN NOT NULL DEFAULT FALSE,
    lot_tracked BOOLEAN NOT NULL DEFAULT FALSE,
    reorder_point NUMERIC(15,3),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (reorder_point IS NULL OR reorder_point >= 0),
    CHECK (NOT serialized OR kind = 'METER'),
    CHECK (NOT lot_tracked OR kind IN ('REAGENT','CHEMICAL','PPE'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_items_code ON inv_items (tenant_id, code);

-- (2) Saldos e ledger ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS inv_balances (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    item_code VARCHAR(100) NOT NULL,
    location_code VARCHAR(60) NOT NULL,
    physical_qty NUMERIC(19,4) NOT NULL DEFAULT 0,
    reserved_qty NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (reserved_qty >= 0),
    average_cost NUMERIC(19,4) NOT NULL DEFAULT 0,
    negative_since TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_balances_item_loc ON inv_balances (tenant_id, item_code, location_code);

CREATE TABLE IF NOT EXISTS inv_movements (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    type VARCHAR(24) NOT NULL CHECK (type IN ('ENTRY','ADJUSTMENT','RESERVATION','RESERVATION_RELEASE','DELIVERY','RETURN','WRITE_OFF_USED','FIELD_WRITE_OFF','TRANSFER_OUT','TRANSFER_IN','INVENTORY_COUNT','SCRAP')),
    item_code VARCHAR(100) NOT NULL,
    location_code VARCHAR(60) NOT NULL,
    qty NUMERIC(19,4) NOT NULL CHECK (qty <> 0),
    unit_cost NUMERIC(19,4),
    reference VARCHAR(200),
    actor_id UUID NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_inv_movements_item ON inv_movements (tenant_id, item_code, location_code, created_at);

-- (3) Requisicoes com alcada ------------------------------------------------------
CREATE TABLE IF NOT EXISTS inv_requisitions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    requester_id UUID NOT NULL,
    location_code VARCHAR(60) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING_APPROVAL','APPROVED','REJECTED','DELIVERED','CLOSED','CANCELLED')),
    approval_mode VARCHAR(18) NOT NULL DEFAULT 'NONE' CHECK (approval_mode IN ('NONE','AUTO','HUMAN','POSTERIOR_PENDING')),
    approver_id UUID,
    approval_note VARCHAR(500),
    total_value NUMERIC(19,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ,
    CHECK (requester_id <> approver_id OR approver_id IS NULL OR approval_mode = 'AUTO')
);
ALTER TABLE inv_requisitions ADD CONSTRAINT uq_inv_requisitions_tenant_id UNIQUE (tenant_id, id);
CREATE TABLE IF NOT EXISTS inv_requisition_lines (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    requisition_id UUID NOT NULL,
    item_code VARCHAR(100) NOT NULL,
    qty NUMERIC(19,4) NOT NULL CHECK (qty > 0),
    unit_avg_cost NUMERIC(19,4) NOT NULL,
    FOREIGN KEY (tenant_id, requisition_id) REFERENCES inv_requisitions (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_inv_req_order ON inv_requisitions (tenant_id, order_id);

-- (4) Transferencias, lotes, seriais ----------------------------------------------
CREATE TABLE IF NOT EXISTS inv_transfers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    item_code VARCHAR(100) NOT NULL,
    qty NUMERIC(19,4) NOT NULL CHECK (qty > 0),
    from_location VARCHAR(60) NOT NULL,
    to_location VARCHAR(60) NOT NULL,
    status VARCHAR(12) NOT NULL CHECK (status IN ('IN_TRANSIT','RECEIVED','CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    CHECK (from_location <> to_location)
);

CREATE TABLE IF NOT EXISTS inv_lots (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    item_code VARCHAR(100) NOT NULL,
    location_code VARCHAR(60) NOT NULL,
    lot_code VARCHAR(80) NOT NULL,
    expiry_date DATE NOT NULL,
    approval_certificate VARCHAR(80),
    qty NUMERIC(19,4) NOT NULL CHECK (qty > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_inv_lots_expiry ON inv_lots (tenant_id, expiry_date);

CREATE TABLE IF NOT EXISTS inv_serial_units (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    serial_number VARCHAR(100) NOT NULL,
    item_code VARCHAR(100) NOT NULL,
    seal_code VARCHAR(100) NOT NULL,
    location_code VARCHAR(60),
    custody VARCHAR(20) NOT NULL CHECK (custody IN ('RECEIVED','STORED','IN_TRANSIT','ISSUED_TO_ORDER','RETURNED','SCRAPPED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_serial ON inv_serial_units (tenant_id, serial_number);
CREATE TABLE IF NOT EXISTS inv_serial_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    serial_number VARCHAR(100) NOT NULL,
    event_type VARCHAR(16) NOT NULL CHECK (event_type IN ('RECEIPT','STORE','TRANSFER_OUT','TRANSFER_IN','ISSUE','RETURN','SEAL_CHANGE','SCRAP')),
    from_location VARCHAR(60),
    to_location VARCHAR(60),
    seal_code VARCHAR(100),
    reason VARCHAR(500),
    actor_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

-- (5) Baixa de campo (ED-20), alcada posterior, pendencias, evidencias -------------
CREATE TABLE IF NOT EXISTS inv_field_tasks (
    tenant_id UUID NOT NULL,
    task_id VARCHAR(120) NOT NULL CHECK (btrim(task_id) <> ''),
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, task_id)
);
CREATE TABLE IF NOT EXISTS inv_posterior_approvals (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    task_id VARCHAR(120) NOT NULL,
    item_code VARCHAR(100) NOT NULL,
    qty NUMERIC(19,4) NOT NULL CHECK (qty > 0),
    status VARCHAR(10) NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    created_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ,
    decided_by UUID
);
CREATE TABLE IF NOT EXISTS inv_shortfall_pendencies (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    item_code VARCHAR(100) NOT NULL,
    location_code VARCHAR(60),
    missing_qty NUMERIC(19,4) NOT NULL CHECK (missing_qty > 0),
    reference VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE IF NOT EXISTS inv_task_consumptions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    task_id VARCHAR(120),
    order_id UUID,
    item_code VARCHAR(100) NOT NULL,
    qty NUMERIC(19,4) NOT NULL,
    ppe BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_inv_consumption_task ON inv_task_consumptions (tenant_id, task_id);

-- (6) Inventario e kits --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS inv_counts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    location_code VARCHAR(60) NOT NULL,
    justification VARCHAR(500),
    actor_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE IF NOT EXISTS inv_kits (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    order_type_code VARCHAR(80) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_kits_type ON inv_kits (tenant_id, order_type_code);
CREATE TABLE IF NOT EXISTS inv_kit_lines (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    kit_id UUID NOT NULL,
    item_code VARCHAR(100) NOT NULL,
    qty NUMERIC(19,4) NOT NULL CHECK (qty > 0)
);

SELECT register_tenant_table('inv_locations');
SELECT register_tenant_table('inv_items');
SELECT register_tenant_table('inv_balances');
SELECT register_tenant_table('inv_movements');
SELECT register_tenant_table('inv_requisitions');
SELECT register_tenant_table('inv_requisition_lines');
SELECT register_tenant_table('inv_transfers');
SELECT register_tenant_table('inv_lots');
SELECT register_tenant_table('inv_serial_units');
SELECT register_tenant_table('inv_serial_events');
SELECT register_tenant_table('inv_field_tasks');
SELECT register_tenant_table('inv_posterior_approvals');
SELECT register_tenant_table('inv_shortfall_pendencies');
SELECT register_tenant_table('inv_task_consumptions');
SELECT register_tenant_table('inv_counts');
SELECT register_tenant_table('inv_kits');
SELECT register_tenant_table('inv_kit_lines');

-- (7) Definitions de parametros do grupo inv.* (CORE — destacar na integracao) -------
-- factory_default NULL em TODAS: alcadas e prazos sao do tenant [A DEFINIR].
INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('inv.auto_approval_limit', 'DECIMAL', 'd', NULL,
     'ED-58 (JOR-32)', NULL,
     'Teto em R$ da aprovacao automatica de requisicao (sem item controlado). Sem valor vigente, nao ha aprovacao automatica.'),
    ('inv.approval_bands', 'JSON', 'd', NULL,
     'ED-58 (JOR-32)', NULL,
     'Faixas de valor -> papel aprovador da requisicao. Sem valor vigente, a aprovacao humana por alcada nao opera.'),
    ('inv.negative_regularization_days', 'INTEGER', 'd', NULL,
     'ED-58', NULL,
     'Prazo em dias para regularizar saldo negativo; alem dele, o relatorio diario marca o item como vencido.'),
    ('inv.lot_expiry_alert_days', 'INTEGER', 'd', NULL,
     'ED-58 (chave nomeada pelo acceptance)', NULL,
     'Janela em dias do alerta de validade de lote (reagentes, quimicos e EPI).')
ON CONFLICT (definition_key) DO NOTHING;
