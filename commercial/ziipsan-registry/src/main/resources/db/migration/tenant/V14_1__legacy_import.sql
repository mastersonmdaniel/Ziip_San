-- P1.2 — Importador v0 do cadastro com conciliação (plano v3.0, linha 658).
--
-- VERSÃO (sem colisão, ver o cabeçalho da core V14_1): a V14_1 de CORE
-- (tenant_migration_status, P1.14) e a V14_1 de TENANT (esta migration, P1.2)
-- vivem em históricos Flyway separados — public contra o schema do tenant — e o
-- §3 do plano as distingue pelo prefixo "core". Nenhuma V14_1 de tenant existia
-- na base 5376a2d (verificado antes de criar este arquivo); se outra frente
-- criar uma, a integração precisa resolver antes do merge, nunca renomear aqui.
--
-- O QUE É (framework declarativo de migração por legado, PLT-16):
--   * legacy_import_batches: uma carga = um lote, identificado por
--     (source_system, external_batch_key). Reimportar a mesma chave devolve o
--     lote existente sem duplicar staging, unidades nem OS (idempotência).
--   * legacy_import_staging_units: uma linha por unidade do legado, com o
--     perfil de qualidade em issues (JSONB) e a data de ligação (connected_on,
--     DAD-10) levada ao staging — a coluna connected_on da ligação
--     (service_connections) é da P2.23, não desta migration.
--   * legacy_import_staging_installments: prestações a vencer dos parcelamentos
--     ativos, com a referência ao acordo do legado (legacy_agreement_ref),
--     APENAS encenadas para a carga da P2.10 — a v0 nunca as aplica (status
--     fica STAGED; a P2.10 é dona da virada para APPLIED).
--   * legacy_import_lineage: um registro por unidade/prestação, da origem
--     (sistema, chave, mapeamento) ao staging, à unidade e à OS de lote.
--   * legacy_import_reconciliation: conciliação encenada por setor, rota,
--     categoria e saldo (totais do legado contra totais do staging).
--
-- DOCUMENTO (P1.39, P0.11): o staging NUNCA guarda o documento bruto. O
-- motor valida o CPF/CNPJ (inclusive o alfanumérico) em memória e grava só
-- a espécie (holder_document_kind), a máscara de exibição
-- (holder_document_masked) e a validade (holder_document_valid). O vínculo
-- titular–unidade (cliente cifrado) é da P1.3, não da v0.
--
-- DADO PESSOAL E AUDITORIA (P0.11, P0.45): as tabelas passam por
-- register_tenant_table (RLS FORÇADA + trigger de auditoria), então as
-- colunas que casam com o padrão do teste de cobertura ganham regra de
-- redação + classificação aqui mesmo (precedente V3_2/V13_3 — tenant
-- migrado não re-roda a repetível R__data_classification). holder_name e
-- holder_document_masked são dado pessoal (a máscara ainda identifica);
-- holder_document_kind, holder_document_valid e mapping_name NÃO são dado
-- pessoal e vão para a allowlist de PersonalDataColumns (precedente
-- fiscal_issuers.document_kind), sem regra.
--
-- FORA DO ESCOPO (não inventado aqui, dono entre parênteses):
--   * unidade sem usuário vigente e revalidação externa (P1.41 — as
--     unidades da v0 sem titular são o caso VACANT dela, sinalizado na
--     linhagem, não resolvido aqui);
--   * cruzamento de edificações com unidades para perdas aparentes (ED-95);
--   * planilhas avulsas pelo importador genérico (P1.40 — esta frente é
--     dona do motor de LEGADO; a integração reconcilia os dois);
--   * dado real: só sintético ou anonimizado na origem; dado real só na
--     produção do lançamento (LC.10).
--
-- Nenhuma coluna nova é NOT NULL sem default em tabela existente (todas as
-- tabelas são novas), e nenhum dado existente é reescrito.

-- ========================================================================
-- Lotes de carga
-- ========================================================================

CREATE TABLE IF NOT EXISTS legacy_import_batches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    source_system VARCHAR(60) NOT NULL,
    mapping_name VARCHAR(100) NOT NULL,
    mapping_version VARCHAR(20) NOT NULL,
    external_batch_key VARCHAR(200) NOT NULL,
    source_kind VARCHAR(20) NOT NULL
        CHECK (source_kind IN ('CSV', 'SQL_DUMP', 'LEGACY_DB')),
    status VARCHAR(20) NOT NULL DEFAULT 'STAGED'
        CHECK (status IN ('STAGED', 'APPLIED')),
    batch_order_id UUID,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE legacy_import_batches ADD CONSTRAINT uq_legacy_import_batches_tenant_id
    UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_legacy_import_batches_key
    ON legacy_import_batches(tenant_id, source_system, external_batch_key);
SELECT register_tenant_table('legacy_import_batches', false);

-- ========================================================================
-- Staging de unidades
-- ========================================================================

CREATE TABLE IF NOT EXISTS legacy_import_staging_units (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    source_row_no INT NOT NULL CHECK (source_row_no >= 1),
    legacy_key VARCHAR(100) NOT NULL,
    matricula VARCHAR(50),
    matricula_dv VARCHAR(1),
    locality_code VARCHAR(20),
    sector_code VARCHAR(20),
    block_code VARCHAR(20),
    route_code VARCHAR(20),
    category_code VARCHAR(20),
    lot INT CHECK (lot IS NULL OR lot >= 0),
    sublot INT CHECK (sublot IS NULL OR sublot >= 0),
    reading_sequence INT CHECK (reading_sequence IS NULL OR reading_sequence >= 1),
    balance_cents BIGINT,
    connected_on DATE,
    holder_name VARCHAR(200),
    holder_document_kind VARCHAR(10)
        CHECK (holder_document_kind IS NULL
            OR holder_document_kind IN ('CPF', 'CNPJ', 'UNKNOWN', 'NONE')),
    holder_document_masked VARCHAR(30),
    holder_document_valid BOOLEAN,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPLIED', 'REJECTED')),
    applied_unit_id UUID,
    issues JSONB NOT NULL DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE legacy_import_staging_units ADD CONSTRAINT uq_legacy_import_staging_units_tenant_id
    UNIQUE (tenant_id, id);
ALTER TABLE legacy_import_staging_units ADD CONSTRAINT fk_legacy_staging_units_batch
    FOREIGN KEY (tenant_id, batch_id) REFERENCES legacy_import_batches(tenant_id, id);
CREATE UNIQUE INDEX idx_legacy_staging_units_batch_key
    ON legacy_import_staging_units(tenant_id, batch_id, legacy_key);
CREATE INDEX idx_legacy_staging_units_batch
    ON legacy_import_staging_units(tenant_id, batch_id);
SELECT register_tenant_table('legacy_import_staging_units', false);

-- ========================================================================
-- Staging de prestações a vencer (só encenado para a P2.10)
-- ========================================================================

CREATE TABLE IF NOT EXISTS legacy_import_staging_installments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    staging_unit_id UUID,
    unit_legacy_key VARCHAR(100) NOT NULL,
    legacy_agreement_ref VARCHAR(100) NOT NULL,
    installment_no INT NOT NULL CHECK (installment_no >= 1),
    due_date DATE,
    amount_cents BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'STAGED'
        CHECK (status IN ('STAGED', 'APPLIED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE legacy_import_staging_installments ADD CONSTRAINT uq_legacy_staging_installments_tenant_id
    UNIQUE (tenant_id, id);
ALTER TABLE legacy_import_staging_installments ADD CONSTRAINT fk_legacy_staging_installments_batch
    FOREIGN KEY (tenant_id, batch_id) REFERENCES legacy_import_batches(tenant_id, id);
ALTER TABLE legacy_import_staging_installments ADD CONSTRAINT fk_legacy_staging_installments_unit
    FOREIGN KEY (tenant_id, staging_unit_id) REFERENCES legacy_import_staging_units(tenant_id, id);
CREATE UNIQUE INDEX idx_legacy_staging_installments_key
    ON legacy_import_staging_installments(tenant_id, batch_id, unit_legacy_key, legacy_agreement_ref, installment_no);
CREATE INDEX idx_legacy_staging_installments_batch
    ON legacy_import_staging_installments(tenant_id, batch_id);
SELECT register_tenant_table('legacy_import_staging_installments', false);

-- ========================================================================
-- Linhagem por registro
-- ========================================================================

CREATE TABLE IF NOT EXISTS legacy_import_lineage (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    record_kind VARCHAR(20) NOT NULL CHECK (record_kind IN ('UNIT', 'INSTALLMENT')),
    source_system VARCHAR(60) NOT NULL,
    source_key VARCHAR(200) NOT NULL,
    mapping_name VARCHAR(100) NOT NULL,
    mapping_version VARCHAR(20) NOT NULL,
    staging_id UUID,
    target_table VARCHAR(100),
    target_id UUID,
    service_order_id UUID,
    applied_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE legacy_import_lineage ADD CONSTRAINT uq_legacy_import_lineage_tenant_id
    UNIQUE (tenant_id, id);
ALTER TABLE legacy_import_lineage ADD CONSTRAINT fk_legacy_import_lineage_batch
    FOREIGN KEY (tenant_id, batch_id) REFERENCES legacy_import_batches(tenant_id, id);
CREATE INDEX idx_legacy_import_lineage_batch
    ON legacy_import_lineage(tenant_id, batch_id);
SELECT register_tenant_table('legacy_import_lineage', false);

-- ========================================================================
-- Conciliação
-- ========================================================================

CREATE TABLE IF NOT EXISTS legacy_import_reconciliation (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    dimension VARCHAR(20) NOT NULL
        CHECK (dimension IN ('SECTOR', 'ROUTE', 'CATEGORY', 'BALANCE', 'BALANCE_BY_SECTOR')),
    dimension_key VARCHAR(100) NOT NULL,
    legacy_count BIGINT,
    staged_count BIGINT,
    legacy_amount_cents BIGINT,
    staged_amount_cents BIGINT,
    matches BOOLEAN NOT NULL,
    computed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE legacy_import_reconciliation ADD CONSTRAINT uq_legacy_import_reconciliation_tenant_id
    UNIQUE (tenant_id, id);
ALTER TABLE legacy_import_reconciliation ADD CONSTRAINT fk_legacy_import_reconciliation_batch
    FOREIGN KEY (tenant_id, batch_id) REFERENCES legacy_import_batches(tenant_id, id);
CREATE UNIQUE INDEX idx_legacy_import_reconciliation_key
    ON legacy_import_reconciliation(tenant_id, batch_id, dimension, dimension_key);
SELECT register_tenant_table('legacy_import_reconciliation', false);

-- ========================================================================
-- Grants explícitos (precedente V3_10; o provisionamento também concede,
-- mas a migration deixa a permissão visível nela mesma)
-- ========================================================================

GRANT SELECT, INSERT, UPDATE, DELETE ON legacy_import_batches TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON legacy_import_staging_units TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON legacy_import_staging_installments TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON legacy_import_lineage TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON legacy_import_reconciliation TO ziipsan_app;

-- ========================================================================
-- Redação + classificação do dado pessoal do staging (precedente V3_2/V13_3)
-- ========================================================================

INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('legacy_import_staging_units', 'holder_name', 'REDACT',
     'nome do titular do legado (dado sintético ou anonimizado na origem até a LC.10)'),
    ('legacy_import_staging_units', 'holder_document_masked', 'REDACT',
     'máscara do CPF/CNPJ ainda identifica a pessoa (precedente customers.document_masked)')
ON CONFLICT (table_name, column_name) DO NOTHING;

INSERT INTO public.data_classification (table_name, column_name, class, reason) VALUES
    ('legacy_import_staging_units', 'holder_name', 'ID',
     'nome do titular do legado (dado sintético ou anonimizado na origem até a LC.10)'),
    ('legacy_import_staging_units', 'holder_document_masked', 'ID',
     'máscara do CPF/CNPJ ainda identifica a pessoa (precedente customers.document_masked)')
ON CONFLICT (table_name, column_name) DO NOTHING;
