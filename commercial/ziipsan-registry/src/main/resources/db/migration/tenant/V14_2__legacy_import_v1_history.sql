-- P2.10 — Importador v1 com 120 meses (plano v3.0, linha da P2.10).
--
-- Reserva do §3: `V14_1`, `V14_2` | importação de legado | lotes, staging,
-- conciliação; histórico de 120 meses | P1.2, P2.10. A V14_1 (P1.2) criou
-- lotes, staging de unidades, staging de prestações, linhagem e conciliação.
-- Esta V14_2 cria SÓ o que falta à P2.10, no mesmo histórico Flyway de
-- tenant do módulo ziipsan-registry:
--
--   * legacy_import_staging_history: uma linha por (unidade, mês) do
--     histórico de até 120 meses da v1, encenada pelo mapeamento declarativo
--     da P2.10 sobre o framework da P1.2 (PLT-16) — ensaio antes de aplicar,
--     como no restante do framework.
--   * legacy_import_history: o histórico aplicado (imutável depois de
--     gravado; a retificação é nova carga, nunca UPDATE — mesmo espírito do
--     versionamento da P2.5, DAD-14).
--   * legacy_import_agreements: a identidade migrada de cada acordo de
--     parcelamento do legado, por lote. É o objeto que o `source_id` dos
--     débitos de parcelamento da P2.5 (DAD-20) referencia: o aceite da P2.5
--     exige source_id em INSTALLMENT_AGREEMENT e o legado só tem a
--     referência textual do acordo — ela fica em legacy_reference, e esta
--     tabela dá ao acordo migrado um UUID estável e idempotente.
--     NENHUMA operação de parcelamento vive aqui (simular, desfazer,
--     reparcelar e antecipar são da P4.1).
--   * legacy_import_installment_debits: vínculo prestação encenada →
--     débito a cobrar criado em debit_receivables (P2.5). É a prova da
--     conversão um a um e a trava de idempotência da virada STAGED →
--     APPLIED das prestações (a V14_1 deixou essa virada para a P2.10).
--   * legacy_import_unit_situations: a situação de ocupação aplicada a
--     cada unidade migrada (CAD-68, P1.41): unidade sem usuário vigente
--     entra VACANT; com usuário vigente, OCCUPIED. A coluna viva é a
--     occupancy_status da unidade (P1.41); esta tabela é o registro da
--     decisão da importação, por unidade e lote.
--
-- Débitos: esta migration NÃO cria coluna em debit_receivables — as colunas
-- de origem (source_type, source_id, legacy_reference, service_order_id,
-- accounting_month, collection_month, financing_term, debit_type_id) são
-- da V4_4 da P2.5 e são apenas CONSUMIDAS pelo gateway JDBC da P2.10.
--
-- Nenhum dado pessoal novo: o histórico é por matrícula/chave legada, sem
-- nome nem documento (o titular já vive, mascarado, no staging da V14_1).
-- Nenhuma coluna nova é NOT NULL sem default em tabela existente (todas as
-- tabelas são novas) e nenhum dado existente é reescrito — a única escrita
-- em tabela da V14_1 é a virada de status das prestações, feita pelo
-- código da P2.10 no momento da aplicação, não por esta migration.

-- ========================================================================
-- Staging do histórico mensal (até 120 meses por unidade)
-- ========================================================================

CREATE TABLE IF NOT EXISTS legacy_import_staging_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    staging_unit_id UUID,
    unit_legacy_key VARCHAR(100) NOT NULL,
    source_row_no INT NOT NULL CHECK (source_row_no >= 1),
    reference_month DATE NOT NULL CHECK (EXTRACT(DAY FROM reference_month) = 1),
    consumption_m3 NUMERIC(12, 3) CHECK (consumption_m3 IS NULL OR consumption_m3 >= 0),
    billed_amount_cents BIGINT CHECK (billed_amount_cents IS NULL OR billed_amount_cents >= 0),
    due_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'STAGED'
        CHECK (status IN ('STAGED', 'APPLIED', 'REJECTED')),
    issues JSONB NOT NULL DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE legacy_import_staging_history ADD CONSTRAINT uq_legacy_staging_history_tenant_id
    UNIQUE (tenant_id, id);
ALTER TABLE legacy_import_staging_history ADD CONSTRAINT fk_legacy_staging_history_batch
    FOREIGN KEY (tenant_id, batch_id) REFERENCES legacy_import_batches(tenant_id, id);
ALTER TABLE legacy_import_staging_history ADD CONSTRAINT fk_legacy_staging_history_unit
    FOREIGN KEY (tenant_id, staging_unit_id) REFERENCES legacy_import_staging_units(tenant_id, id);
CREATE UNIQUE INDEX idx_legacy_staging_history_key
    ON legacy_import_staging_history(tenant_id, batch_id, unit_legacy_key, reference_month);
CREATE INDEX idx_legacy_staging_history_batch
    ON legacy_import_staging_history(tenant_id, batch_id);
SELECT register_tenant_table('legacy_import_staging_history', false);

-- ========================================================================
-- Histórico aplicado (120 meses)
-- ========================================================================

CREATE TABLE IF NOT EXISTS legacy_import_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    staging_history_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    unit_legacy_key VARCHAR(100) NOT NULL,
    reference_month DATE NOT NULL CHECK (EXTRACT(DAY FROM reference_month) = 1),
    consumption_m3 NUMERIC(12, 3) CHECK (consumption_m3 IS NULL OR consumption_m3 >= 0),
    billed_amount_cents BIGINT CHECK (billed_amount_cents IS NULL OR billed_amount_cents >= 0),
    due_date DATE,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    applied_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE legacy_import_history ADD CONSTRAINT uq_legacy_import_history_tenant_id
    UNIQUE (tenant_id, id);
ALTER TABLE legacy_import_history ADD CONSTRAINT fk_legacy_import_history_batch
    FOREIGN KEY (tenant_id, batch_id) REFERENCES legacy_import_batches(tenant_id, id);
ALTER TABLE legacy_import_history ADD CONSTRAINT fk_legacy_import_history_staging
    FOREIGN KEY (tenant_id, staging_history_id) REFERENCES legacy_import_staging_history(tenant_id, id);
CREATE UNIQUE INDEX idx_legacy_import_history_key
    ON legacy_import_history(tenant_id, unit_legacy_key, reference_month);
CREATE INDEX idx_legacy_import_history_unit
    ON legacy_import_history(tenant_id, unit_id, reference_month);
SELECT register_tenant_table('legacy_import_history', false);

-- ========================================================================
-- Acordos de parcelamento migrados (identidade para o source_id da P2.5)
-- ========================================================================

CREATE TABLE IF NOT EXISTS legacy_import_agreements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    legacy_agreement_ref VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE legacy_import_agreements ADD CONSTRAINT uq_legacy_import_agreements_tenant_id
    UNIQUE (tenant_id, id);
ALTER TABLE legacy_import_agreements ADD CONSTRAINT fk_legacy_import_agreements_batch
    FOREIGN KEY (tenant_id, batch_id) REFERENCES legacy_import_batches(tenant_id, id);
CREATE UNIQUE INDEX idx_legacy_import_agreements_key
    ON legacy_import_agreements(tenant_id, batch_id, legacy_agreement_ref);
SELECT register_tenant_table('legacy_import_agreements', false);

-- ========================================================================
-- Vínculo prestação encenada → débito a cobrar (P2.5), um a um
-- ========================================================================

CREATE TABLE IF NOT EXISTS legacy_import_installment_debits (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    staging_installment_id UUID NOT NULL,
    debit_receivable_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE legacy_import_installment_debits ADD CONSTRAINT uq_legacy_import_inst_debits_tenant_id
    UNIQUE (tenant_id, id);
ALTER TABLE legacy_import_installment_debits ADD CONSTRAINT fk_legacy_inst_debits_batch
    FOREIGN KEY (tenant_id, batch_id) REFERENCES legacy_import_batches(tenant_id, id);
ALTER TABLE legacy_import_installment_debits ADD CONSTRAINT fk_legacy_inst_debits_staging
    FOREIGN KEY (tenant_id, staging_installment_id)
        REFERENCES legacy_import_staging_installments(tenant_id, id);
CREATE UNIQUE INDEX idx_legacy_import_inst_debits_key
    ON legacy_import_installment_debits(tenant_id, staging_installment_id);
CREATE INDEX idx_legacy_import_inst_debits_batch
    ON legacy_import_installment_debits(tenant_id, batch_id);
SELECT register_tenant_table('legacy_import_installment_debits', false);

-- ========================================================================
-- Vínculo unidade → débito de resíduo de saldo (no máximo um por unidade)
-- ========================================================================

CREATE TABLE IF NOT EXISTS legacy_import_residual_debits (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    staging_unit_id UUID NOT NULL,
    debit_receivable_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE legacy_import_residual_debits ADD CONSTRAINT uq_legacy_import_residual_debits_tenant_id
    UNIQUE (tenant_id, id);
ALTER TABLE legacy_import_residual_debits ADD CONSTRAINT fk_legacy_residual_debits_batch
    FOREIGN KEY (tenant_id, batch_id) REFERENCES legacy_import_batches(tenant_id, id);
ALTER TABLE legacy_import_residual_debits ADD CONSTRAINT fk_legacy_residual_debits_staging
    FOREIGN KEY (tenant_id, staging_unit_id) REFERENCES legacy_import_staging_units(tenant_id, id);
CREATE UNIQUE INDEX idx_legacy_import_residual_debits_key
    ON legacy_import_residual_debits(tenant_id, staging_unit_id);
SELECT register_tenant_table('legacy_import_residual_debits', false);

-- ========================================================================
-- Situação de ocupação aplicada na importação (CAD-68, P1.41)
-- ========================================================================

CREATE TABLE IF NOT EXISTS legacy_import_unit_situations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    staging_unit_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    occupancy_status VARCHAR(20) NOT NULL
        CHECK (occupancy_status IN ('OCCUPIED', 'VACANT', 'UNKNOWN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE legacy_import_unit_situations ADD CONSTRAINT uq_legacy_import_unit_situations_tenant_id
    UNIQUE (tenant_id, id);
ALTER TABLE legacy_import_unit_situations ADD CONSTRAINT fk_legacy_unit_situations_batch
    FOREIGN KEY (tenant_id, batch_id) REFERENCES legacy_import_batches(tenant_id, id);
ALTER TABLE legacy_import_unit_situations ADD CONSTRAINT fk_legacy_unit_situations_staging
    FOREIGN KEY (tenant_id, staging_unit_id) REFERENCES legacy_import_staging_units(tenant_id, id);
CREATE UNIQUE INDEX idx_legacy_import_unit_situations_key
    ON legacy_import_unit_situations(tenant_id, batch_id, staging_unit_id);
SELECT register_tenant_table('legacy_import_unit_situations', false);

-- ========================================================================
-- Grants explícitos (precedente V14_1)
-- ========================================================================

GRANT SELECT, INSERT, UPDATE, DELETE ON legacy_import_staging_history TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON legacy_import_history TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON legacy_import_agreements TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON legacy_import_installment_debits TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON legacy_import_residual_debits TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON legacy_import_unit_situations TO ziipsan_app;
