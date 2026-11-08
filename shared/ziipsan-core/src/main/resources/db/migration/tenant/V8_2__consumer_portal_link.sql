-- Consumer portal link, 120-month history support and contract delivery (P3.20;
-- ADR-0022; ADR-0043 item 5; ADR-0008; NR 11 arts. 18 and 53).
-- Version: 8.2.0
-- Description: the link between a portal consumer account and a consumer unit.
-- The holder document and the linking code are NEVER stored in clear: only
-- their blind indexes (FieldProtector contexts portal.link.holder_document
-- and portal.linking_code) live here. Holder change revokes every link of
-- the unit and invalidates the current linking code (status + code index
-- cleared by the application; revoked rows are kept as evidence).
-- FKs to consumer_units/customers are conditional for the same
-- partial-classpath reason as V8_1 (42a0fb5 pattern).

CREATE TABLE IF NOT EXISTS consumer_portal_links (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    consumer_subject VARCHAR(200) NOT NULL CHECK (btrim(consumer_subject) <> ''),
    consumer_unit_id UUID NOT NULL,
    holder_document_index VARCHAR(128) NOT NULL,
    linking_code_index VARCHAR(128),
    -- Metodo de comprovação do vínculo (conteúdo do plano §3 para a V8_2): o
    -- único método do desenho é código da fatura + documento do titular, os
    -- dois por índice cego — a coluna nasce com esse valor por DEFAULT.
    proof_method VARCHAR(40) NOT NULL DEFAULT 'CODE_AND_DOCUMENT'
        CHECK (proof_method = 'CODE_AND_DOCUMENT'),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    linked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ,
    revoke_reason VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_consumer_portal_links_active
    ON consumer_portal_links (tenant_id, consumer_subject, consumer_unit_id) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS ix_consumer_portal_links_unit
    ON consumer_portal_links (tenant_id, consumer_unit_id, status);

CREATE TABLE IF NOT EXISTS consumer_portal_link_attempts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    consumer_unit_id UUID,
    source_ip VARCHAR(64) NOT NULL,
    success BOOLEAN NOT NULL,
    volume_alert BOOLEAN NOT NULL DEFAULT FALSE,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_consumer_portal_link_attempts_window
    ON consumer_portal_link_attempts (tenant_id, attempted_at);

CREATE TABLE IF NOT EXISTS consumer_contract_deliveries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    consumer_unit_id UUID NOT NULL,
    contract_version VARCHAR(40) NOT NULL CHECK (btrim(contract_version) <> ''),
    trigger_kind VARCHAR(20) NOT NULL CHECK (trigger_kind IN ('NEW_CONNECTION', 'HOLDER_CHANGE')),
    channel VARCHAR(20) NOT NULL CHECK (channel IN ('PORTAL', 'PROTOCOL_RECEIPT')),
    delivered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    acknowledged_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_consumer_contract_deliveries_unit
    ON consumer_contract_deliveries (tenant_id, consumer_unit_id, contract_version);

-- Registro das tabelas do tenant (RLS): padrão uniforme das migrations
-- vizinhas (V8_1, V1_15, V1_8) — sem este registro as tabelas nascem fora
-- do cadastro que sustenta o isolamento por tenant (correção D1 da
-- revisão independente da P3.20).
SELECT register_tenant_table('consumer_portal_links');
SELECT register_tenant_table('consumer_portal_link_attempts');
SELECT register_tenant_table('consumer_contract_deliveries');

-- Classificação de dados (P0.11), no padrão da V8_1/V1_8: o sujeito do
-- portal, os índices cegos do documento do titular e do código de
-- vinculação e o IP de origem das tentativas são identificadores (ID).
INSERT INTO public.data_classification (table_name, column_name, class, reason) VALUES
    ('consumer_portal_links', 'consumer_subject', 'ID', 'identificador da conta do consumidor no portal (P3.20)'),
    ('consumer_portal_links', 'holder_document_index', 'ID', 'indice cego do documento (CPF/CNPJ) do titular (P3.20)'),
    ('consumer_portal_links', 'linking_code_index', 'ID', 'indice cego do codigo de vinculacao da fatura (P3.20)'),
    ('consumer_portal_link_attempts', 'source_ip', 'ID', 'endereco IP de origem da tentativa de vinculo (P3.20)')
ON CONFLICT (table_name, column_name) DO NOTHING;
