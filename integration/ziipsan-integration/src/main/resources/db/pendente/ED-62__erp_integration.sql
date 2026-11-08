-- ED-62 — Integracao com ERP do cliente (Betha, IPM, Fiorilli) — SQL PENDENTE
-- DE NUMERACAO FLYWAY (prefixo ED-62__). Nenhum numero foi inventado: na
-- integracao, atribuir o proximo slot livre da historia tenant do modulo
-- integration/ziipsan-integration e renomear o arquivo.
-- NENHUM leiaute de fornecedor e semeado: mapeamentos, versoes de leiaute,
-- oficialidade do estoque e naturezas de despesa sao DECLARADOS por tenant
-- ([A DEFINIR]); os 14 valores da P3.9 NAO sao tocados por esta entrega.
-- Licao da C3 aplicada: TODA FK composta (tenant_id, X) tem no pai a
-- UNIQUE (tenant_id, id) correspondente (erp_connections).

-- (1) Conexao ERP do tenant (uma por fornecedor) --------------------------------
CREATE TABLE IF NOT EXISTS erp_connections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    provider VARCHAR(16) NOT NULL CHECK (provider IN ('BETHA','IPM','FIORILLI')),
    connector_name VARCHAR(40) NOT NULL,
    transport VARCHAR(8) NOT NULL CHECK (transport IN ('API','FILE')),
    stock_official VARCHAR(8) NOT NULL CHECK (stock_official IN ('ZIIPSAN','ERP')),
    layout_version VARCHAR(60) NOT NULL,
    credential_ref VARCHAR(200),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_erp_connections_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_erp_connections_provider UNIQUE (tenant_id, provider)
);

-- (2) Mapeamento de leiaute DECLARADO (campo canonico -> campo do ERP) ----------
CREATE TABLE IF NOT EXISTS erp_layout_mappings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    connection_id UUID NOT NULL,
    canonical_field VARCHAR(60) NOT NULL,
    erp_field VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_erp_layout_mappings_conn FOREIGN KEY (tenant_id, connection_id)
        REFERENCES erp_connections (tenant_id, id),
    CONSTRAINT uq_erp_layout_mappings_field UNIQUE (tenant_id, connection_id, canonical_field)
);

-- (3) Ganchos: centro de custo (P3.30), natureza de despesa (ED-58), contrato ---
CREATE TABLE IF NOT EXISTS erp_cost_center_links (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    connection_id UUID NOT NULL,
    org_unit_code VARCHAR(80) NOT NULL,
    erp_cost_center_code VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_erp_cost_center_links_conn FOREIGN KEY (tenant_id, connection_id)
        REFERENCES erp_connections (tenant_id, id),
    CONSTRAINT uq_erp_cost_center_links_unit UNIQUE (tenant_id, connection_id, org_unit_code)
);

CREATE TABLE IF NOT EXISTS erp_material_hooks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    connection_id UUID NOT NULL,
    material_code VARCHAR(100) NOT NULL,
    expense_nature_code VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_erp_material_hooks_conn FOREIGN KEY (tenant_id, connection_id)
        REFERENCES erp_connections (tenant_id, id),
    CONSTRAINT uq_erp_material_hooks_material UNIQUE (tenant_id, connection_id, material_code)
);

CREATE TABLE IF NOT EXISTS erp_contract_hooks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    connection_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    commitment_number VARCHAR(80),
    external_purchase_order VARCHAR(80),
    pncp_number VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_erp_contract_hooks_conn FOREIGN KEY (tenant_id, connection_id)
        REFERENCES erp_connections (tenant_id, id),
    CONSTRAINT uq_erp_contract_hooks_contract UNIQUE (tenant_id, connection_id, contract_id),
    CHECK (commitment_number IS NOT NULL OR external_purchase_order IS NOT NULL)
);

-- (4) Lotes de exportacao (idempotencia) e indicadores lidos --------------------
CREATE TABLE IF NOT EXISTS erp_export_batches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    connection_id UUID NOT NULL,
    operation VARCHAR(60) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    rendered_payload TEXT NOT NULL,
    outcome VARCHAR(16) NOT NULL CHECK (outcome IN ('SUCCESS','REJECTED','UNAVAILABLE')),
    external_id VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_erp_export_batches_conn FOREIGN KEY (tenant_id, connection_id)
        REFERENCES erp_connections (tenant_id, id),
    CONSTRAINT uq_erp_export_batches_idem UNIQUE (tenant_id, connection_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS erp_indicator_snapshots (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    connection_id UUID NOT NULL,
    area VARCHAR(12) NOT NULL CHECK (area IN ('PURCHASES','HR','BUDGET')),
    indicator_code VARCHAR(80) NOT NULL,
    indicator_value NUMERIC(19,4) NOT NULL,
    competence DATE NOT NULL,
    source_reference VARCHAR(200) NOT NULL,
    read_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_erp_indicator_snapshots_conn FOREIGN KEY (tenant_id, connection_id)
        REFERENCES erp_connections (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_erp_indicator_snapshots_panel
    ON erp_indicator_snapshots (tenant_id, competence, area);

SELECT register_tenant_table('erp_connections');
SELECT register_tenant_table('erp_layout_mappings');
SELECT register_tenant_table('erp_cost_center_links');
SELECT register_tenant_table('erp_material_hooks');
SELECT register_tenant_table('erp_contract_hooks');
SELECT register_tenant_table('erp_export_batches');
SELECT register_tenant_table('erp_indicator_snapshots');

-- (5) Definitions de parametros erp.* (factory_default NULL em TODAS) -----------
INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('erp.betha.layout_version', 'TEXT', 'd', NULL, 'ED-62 (GES-23)', NULL,
     'Versao do leiaute/API Betha DECLARADA na implantacao. Sem valor, o conector erp-betha nao opera.'),
    ('erp.betha.stock_official', 'TEXT', 'd', NULL, 'ED-62 (ADR-0032)', NULL,
     'ZIIPSAN ou ERP: quem detem o estoque oficial. Sem valor, nenhum movimento e exportado.'),
    ('erp.betha.transport', 'TEXT', 'd', NULL, 'ED-62 (GES-23)', NULL,
     'API ou FILE: canal do conector Betha.'),
    ('erp.ipm.layout_version', 'TEXT', 'd', NULL, 'ED-62 (GES-23)', NULL,
     'Versao do leiaute/API IPM DECLARADA na implantacao. Sem valor, o conector erp-ipm nao opera.'),
    ('erp.ipm.stock_official', 'TEXT', 'd', NULL, 'ED-62 (ADR-0032)', NULL,
     'ZIIPSAN ou ERP: quem detem o estoque oficial. Sem valor, nenhum movimento e exportado.'),
    ('erp.ipm.transport', 'TEXT', 'd', NULL, 'ED-62 (GES-23)', NULL,
     'API ou FILE: canal do conector IPM.'),
    ('erp.fiorilli.layout_version', 'TEXT', 'd', NULL, 'ED-62 (GES-23)', NULL,
     'Versao do leiaute/API Fiorilli DECLARADA na implantacao. Sem valor, o conector erp-fiorilli nao opera.'),
    ('erp.fiorilli.stock_official', 'TEXT', 'd', NULL, 'ED-62 (ADR-0032)', NULL,
     'ZIIPSAN ou ERP: quem detem o estoque oficial. Sem valor, nenhum movimento e exportado.'),
    ('erp.fiorilli.transport', 'TEXT', 'd', NULL, 'ED-62 (GES-23)', NULL,
     'API ou FILE: canal do conector Fiorilli.')
ON CONFLICT (definition_key) DO NOTHING;
