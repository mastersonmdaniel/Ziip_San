-- Billing Module Migration for Tenant Schema
-- Version: 4.10.0 (P2.32) — reserva do plano v3.0 §3: V4_10 (billing) +
-- as repetiveis R__invoice_required_content.sql e R__legal_message_catalog.sql.
-- Description: conteudo minimo da fatura como dado versionado (por lei e por
-- ERI), catalogo versionado de mensagens legais e prova de emissao por conta.
--
-- O QUE ESTA MIGRATION FAZ
-- 1. invoice_required_fields: a lista versionada de campos obrigatorios
--    (catalog_version + profile_key; profile_key NULL = base federal). A
--    carga e da R__invoice_required_content.sql, gerada do catalogo Java
--    RequiredBillContent (fonte unica, conferida por teste de consistencia).
-- 2. legal_message_catalog: mensagens legais e slots, versionados, com
--    chave por norma, classe do parametro (a/b), disparo, vigencia e ordem.
--    Carga da R__legal_message_catalog.sql (fonte: LegalMessageCatalog).
-- 3. bill_message_emissions: PROVA DE EMISSAO — uma linha por mensagem que
--    saiu numa versao da conta, com a versao do modelo e dos catalogos.
--
-- RAS-28: NENHUMA coluna ou linha de valor aproximado de tributos
-- (Lei 12.741/2012) e criada aqui: o campo aguarda parecer juridico e nao
-- entra no modelo sem ele (ver PendingTaxDisclosure no codigo).
--
-- Nenhum dado de tenant e semeado: as cargas R__ sao catalogo de fabrica.

CREATE TABLE IF NOT EXISTS invoice_required_fields (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    catalog_version INT NOT NULL,
    profile_key VARCHAR(60),
    field_key VARCHAR(80) NOT NULL,
    source_norm VARCHAR(160) NOT NULL,
    source_reference VARCHAR(240) NOT NULL,
    locked_block BOOLEAN NOT NULL DEFAULT FALSE,
    origin VARCHAR(30) NOT NULL CHECK (origin IN ('FEDERAL', 'REGULATORY_PROFILE')),
    valid_from DATE,
    valid_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE TABLE IF NOT EXISTS legal_message_catalog (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    catalog_version INT NOT NULL,
    message_key VARCHAR(80) NOT NULL,
    norm_key VARCHAR(160) NOT NULL,
    parameter_class VARCHAR(1) NOT NULL CHECK (parameter_class IN ('A', 'B', 'C', 'D')),
    trigger_condition VARCHAR(60) NOT NULL,
    print_order INT NOT NULL,
    legal BOOLEAN NOT NULL DEFAULT FALSE,
    slot_only BOOLEAN NOT NULL DEFAULT FALSE,
    activating_task VARCHAR(20),
    valid_from DATE,
    valid_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE TABLE IF NOT EXISTS bill_message_emissions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    bill_id UUID NOT NULL,
    bill_version_no INT NOT NULL,
    bill_template_version_id UUID,
    message_key VARCHAR(80) NOT NULL,
    norm_key VARCHAR(160) NOT NULL,
    message_catalog_version INT NOT NULL,
    content_catalog_version INT NOT NULL,
    print_position INT NOT NULL,
    issued_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

-- Chaves estaveis das cargas repetiveis (a R__ faz upsert por elas).
CREATE UNIQUE INDEX IF NOT EXISTS uq_invoice_required_fields_key
    ON invoice_required_fields(tenant_id, catalog_version, COALESCE(profile_key, ''), field_key);
CREATE UNIQUE INDEX IF NOT EXISTS uq_legal_message_catalog_key
    ON legal_message_catalog(tenant_id, catalog_version, message_key);

CREATE INDEX IF NOT EXISTS idx_bill_message_emissions_bill
    ON bill_message_emissions(tenant_id, bill_id, bill_version_no, print_position);

SELECT register_tenant_table('invoice_required_fields');
SELECT register_tenant_table('legal_message_catalog');
SELECT register_tenant_table('bill_message_emissions');
