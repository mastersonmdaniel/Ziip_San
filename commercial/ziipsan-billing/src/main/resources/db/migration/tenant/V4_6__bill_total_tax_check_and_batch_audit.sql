-- Bill total with tax, and batch-audit foundation (P1.16, plan v3.0 line 642; migration
-- reserved in plan section 3 for P1-P2).
-- Version: 4.6.0 (P1 part only)
-- Description: pins the tax meaning of the bill total, and adds the batch-audit tables
-- that P2.3 will fill per lot. Depends on: V4_1 (bills).
--
-- WHAT THIS MIGRATION DOES IN P1 (and what it deliberately does NOT do):
-- 1. CHECK of the total with tax. The V4_1 formula is
--      total = water + sewer + debits - credits - taxes
--    and it stays untouched (Flyway migrations are immutable). What V4_1 never stated is
--    WHAT taxes means. It means ONLY withholding tax (bill_taxes rows produced by
--    TaxWithholding: base truncated DOWN, each tax HALF_DOWN, the last absorbing the
--    residue). IBS/CBS are NOT in this total: in 2026 they are only highlighted on the
--    fiscal document, and whether they sit inside or outside the price is still undecided
--    (FISCAL-PORTE-JAVA section 4; P2.6 decides, with the NT 2026.004 rule that from 2027
--    the document total is net + IBS + CBS). This migration therefore adds a NAMED check
--    that pins the formula together with a tax_treatment column locked to the single
--    value WITHHOLDING_ONLY. Any future regime (IBS/CBS inside or outside the price)
--    must arrive as a new migration that changes this check explicitly; no front may
--    silently stuff IBS/CBS into taxes_value and keep passing the old formula.
--    The named check repeats the V4_1 formula on purpose: V4_1's check is unnamed
--    (PostgreSQL auto-name) and cannot be renamed without editing an applied migration.
-- 2. Batch audit (foundation for P2.3). bill_audit_batches records one billing run
--    (run_id) per reference month with its bill count and total; bills gains a nullable
--    audit_batch_id pointing at it. Nullable on purpose: P1 does not change the billing
--    write path, so no existing insert breaks; P2.3 makes the lot mandatory per run and
--    proves that a whole lot's trail query is pruned to the lot's partitions.
-- 3. NO declarative partitioning of bills/bill_items in P1. Partitioning bills by
--    reference_month requires a composite primary key (tenant_id, reference_month, id)
--    and a reference_month column on every child FK (bill_items, bill_debits,
--    bill_credits, bill_taxes), plus a JPA composite key in BillEntity and all its
--    repositories. That is a P2-scale change, it collides with P1.8 (which adds
--    fiscal_document_id to bills in V4_7) and with P2.5 (which adds parameter_version_ids
--    and billed_customer columns to bills), and doing it half-way (a second partitioned
--    copy table, or hash partitioning on id, which prunes nothing by month) would be
--    worse than not doing it. Partitioning therefore stays in P2.3, in this same
--    reserved V4_6 file, as the plan's "P1-P2" reservation already states. This file
--    only lays the audit_batch_id column that the partitioned lot queries will use.
--
-- NO SEEDED DATA. The batch table starts empty; a batch row is written by a billing run,
-- never by a migration.

-- ========================================================================
-- 1. TAX MEANING OF THE TOTAL (P1.16 CHECK)
-- ========================================================================

ALTER TABLE bills ADD COLUMN IF NOT EXISTS tax_treatment VARCHAR(20) NOT NULL DEFAULT 'WITHHOLDING_ONLY';
ALTER TABLE bills ADD CONSTRAINT chk_bills_tax_treatment
    CHECK (tax_treatment IN ('WITHHOLDING_ONLY'));
-- Named restatement of the V4_1 total formula, with the tax term pinned to withholding.
-- Kept alongside (not instead of) the V4_1 check, which is immutable.
ALTER TABLE bills ADD CONSTRAINT chk_bills_total_with_withholding_tax
    CHECK (total_value = water_value + sewer_value + debits_value - credits_value - taxes_value);

-- ========================================================================
-- 2. BATCH AUDIT (foundation for P2.3)
-- ========================================================================

CREATE TABLE IF NOT EXISTS bill_audit_batches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    run_id UUID NOT NULL,
    reference_month DATE NOT NULL CHECK (EXTRACT(DAY FROM reference_month) = 1),
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    bill_count INT NOT NULL DEFAULT 0 CHECK (bill_count >= 0),
    total_value NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (total_value >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE bill_audit_batches ADD CONSTRAINT uq_bill_audit_batches_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_bill_audit_batches_run ON bill_audit_batches(tenant_id, run_id);
CREATE INDEX idx_bill_audit_batches_month ON bill_audit_batches(tenant_id, reference_month, status);

ALTER TABLE bills ADD COLUMN IF NOT EXISTS audit_batch_id UUID;
ALTER TABLE bills ADD CONSTRAINT fk_bill_audit_batch
    FOREIGN KEY (tenant_id, audit_batch_id) REFERENCES bill_audit_batches(tenant_id, id);
CREATE INDEX idx_bills_audit_batch ON bills(tenant_id, audit_batch_id);

-- ========================================================================
-- ISOLATION AND AUDIT
-- ========================================================================

SELECT register_tenant_table('bill_audit_batches');
