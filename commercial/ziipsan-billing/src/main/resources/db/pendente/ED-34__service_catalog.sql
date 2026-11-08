-- ED-34 — Catalogo de servicos e servicos faturaveis.
-- NUMERACAO PENDENTE: o plano nao reservou numero nominal para a ED-34 na faixa
-- do billing (V4_x vai ate V4_17 nos pacotes locais). NAO inventar numero:
-- na integracao, atribuir o proximo slot livre da historia tenant do billing
-- e renomear este arquivo (o teste de consistencia da integracao confere).
-- Depende de: V9_1 tenant (service_types, service_orders) e V4_1 (billing).
-- Padrao das demais migrations tenant: tenant_id + RLS forcada via
-- register_tenant_table (conferir a chamada exata na V4_1/V4_3 na integracao).

CREATE TABLE IF NOT EXISTS service_price_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(100) NOT NULL,
    description VARCHAR(300) NOT NULL,
    revenue_nature VARCHAR(30) NOT NULL CHECK (revenue_nature IN ('TARIFF','COMPLEMENTARY','ANCILLARY','ASSOCIATED_PROJECT')),
    unit_price NUMERIC(15,2) NOT NULL CHECK (unit_price > 0),
    valid_from DATE NOT NULL,
    valid_to DATE,
    homologation_required BOOLEAN NOT NULL DEFAULT FALSE,
    homologation_ref VARCHAR(200),
    sharing_pct NUMERIC(5,2) CHECK (sharing_pct IS NULL OR (sharing_pct >= 0 AND sharing_pct <= 100)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CHECK (revenue_nature = 'TARIFF' OR sharing_pct IS NOT NULL)
    -- homologacao pendente NAO bloqueia o cadastro do item: quem bloqueia o
    -- faturamento e a regra de dominio ServicePriceItem.billable().
);
ALTER TABLE service_price_items ADD CONSTRAINT uq_service_price_items_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX uq_service_price_items_code_vigencia ON service_price_items(tenant_id, code, valid_from);

CREATE TABLE IF NOT EXISTS material_price_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(100) NOT NULL,
    description VARCHAR(300) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    unit_price NUMERIC(15,2) NOT NULL CHECK (unit_price > 0),
    valid_from DATE NOT NULL,
    valid_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
ALTER TABLE material_price_items ADD CONSTRAINT uq_material_price_items_tenant_id UNIQUE (tenant_id, id);

-- Documentos exigidos e fluxo encadeado vivem no tipo de servico (P1.31 guarda
-- so a referencia de preco; ED-34 e a dona destas duas colunas jsonb).
ALTER TABLE service_types ADD COLUMN IF NOT EXISTS required_documents JSONB;
ALTER TABLE service_types ADD COLUMN IF NOT EXISTS workflow JSONB;

-- Lancamento no encerramento da OS: idempotencia por OS + item.
CREATE TABLE IF NOT EXISTS service_charges (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    service_order_id UUID NOT NULL,
    consumer_unit_id UUID,
    price_item_code VARCHAR(100) NOT NULL,
    revenue_nature VARCHAR(30) NOT NULL,
    service_amount NUMERIC(15,2) NOT NULL,
    materials_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    total NUMERIC(15,2) NOT NULL CHECK (total = service_amount + materials_amount),
    installments INT NOT NULL DEFAULT 1 CHECK (installments >= 1),
    source_type VARCHAR(30) NOT NULL DEFAULT 'SERVICE_ORDER' CHECK (source_type = 'SERVICE_ORDER'),
    idempotency_key VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE service_charges ADD CONSTRAINT uq_service_charges_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX uq_service_charges_idempotency ON service_charges(tenant_id, idempotency_key);
-- Nao-sobreposicao de vigencias GARANTIDA NO BANCO (D4 da revisao cruzada),
-- alem da garantia de dominio da PriceTable: exclusion constraint sobre o
-- periodo [valid_from, valid_to] por (tenant, codigo); valid_to nulo = aberto.
-- Requer btree_gist (extensao padrao do PostgreSQL, conferir na integracao se
-- o ambiente ja a habilita por outra migration).
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE service_price_items ADD CONSTRAINT ex_service_price_items_no_overlap
    EXCLUDE USING gist (tenant_id WITH =, code WITH =,
        daterange(valid_from, COALESCE(valid_to, DATE 'infinity'), '[]') WITH &&);
ALTER TABLE material_price_items ADD CONSTRAINT ex_material_price_items_no_overlap
    EXCLUDE USING gist (tenant_id WITH =, code WITH =,
        daterange(valid_from, COALESCE(valid_to, DATE 'infinity'), '[]') WITH &&);

-- RLS forcada (D3 da revisao cruzada): padrao V4_1/V9_1, cada tabela nova e
-- registrada na propria migration.
SELECT register_tenant_table('service_price_items');
SELECT register_tenant_table('material_price_items');
SELECT register_tenant_table('service_charges');

-- FKs para service_orders/service_price_items: criar na integracao conferindo
-- o padrao (tenant_id, id) das V9_1/V4_x, como nas demais ligacoes do projeto.
