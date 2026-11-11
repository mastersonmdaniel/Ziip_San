-- ============================================================================
-- D72-V46 (decisao 72 do dono, Opcao A) -- Re-asserticao pos-V4_15 do estado
-- da V4_4/V4_12 sobre bills (pendencia P-R27-A-01 do diagnostico R27-A).
-- Version: 109 (reivindicada neste lote; verificada livre na main e1f4bf80).
--
-- WHY THIS MIGRATION EXISTS. In a CLEAN provisioning the Flyway order is
-- V4_4 (4.4) -> V4_6 (4.6) -> V4_12 (4.12) -> V4_14 (4.14) -> V4_15 (4.15).
-- V4_14 recreates bills as a partitioned table and its CREATE TABLE does NOT
-- carry the columns that V4_4 added (fixed_part_value, parameter_version_ids,
-- billed_customer_id, billed_customer_snapshot) nor tax_event_date (V4_12),
-- and it recreates the total/credits CHECKs with the OLD formulas (without
-- fixed_part_value); V4_15 then renames the anonymous total check to the
-- contract name chk_bills_total_with_withholding_tax, pinning the old
-- formula under the contract name. No later migration restores them, so a
-- clean-provisioned tenant ended with bills missing NOT NULL columns that
-- BillEntity maps and with a total check that rejects every bill whose
-- fixed_part_value > 0 (exactly what V4_4 section 5 warns against).
-- Live bases never saw this because their order was V4_6 -> V4_14 -> V4_15
-- -> V4_4 (upgrade, outOfOrder era): there V4_4 ran AFTER the recreation.
--
-- WHAT THIS MIGRATION DOES. It re-asserts, AFTER V4_15, the exact V4_4/V4_12
-- state for bills, with the canonical total formula of V4_4 (with
-- fixed_part_value -- BillCalculator/BillEntity; decision 72 keeps the V4_4
-- formula canonical). Every statement is guarded so the migration is a
-- no-op on bases whose bills already carries the V4_4 state (upgrade order)
-- and repairs bases provisioned in clean order. It changes no data: bills
-- written under the old check also satisfy the canonical total check only
-- when fixed_part_value = 0; on a clean-provisioned tenant bills is empty
-- at provisioning time, and on an upgrade-order tenant the added columns
-- arrive with the same defaults V4_4 used.
--
-- Companion fix in this same lot (decision 72, via (a)): V4_6 gained a
-- DROP CONSTRAINT IF EXISTS guard before re-adding
-- chk_bills_total_with_withholding_tax, which V4_4 already creates in clean
-- order (the 42710 that aborted every clean provisioning at V4_6:52).
--
-- NO SEEDED DATA.
-- ============================================================================

-- ------------------------------------------------------------------------
-- 1. COLUMNS (V4_4 sections 2 and 5; V4_12 section 4)
-- ------------------------------------------------------------------------
ALTER TABLE bills ADD COLUMN IF NOT EXISTS fixed_part_value NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS parameter_version_ids JSONB;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS billed_customer_id UUID;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS billed_customer_snapshot TEXT;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS tax_event_date DATE;

COMMENT ON COLUMN bills.tax_event_date IS
    'Data do fato gerador para fins de IBS/CBS (P2.34/V4_12), gravada separada de due_date e de issued_on. Re-assertido pela V109 (a recriacao da V4_14 o apagava em provisionamento limpo).';

-- ------------------------------------------------------------------------
-- 2. FIXED PART CHECK AND BILLED-CUSTOMER PAIR CHECK (V4_4 sections 2 and 5)
-- ------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conrelid = 'bills'::regclass
                     AND conname = 'chk_bills_fixed_part_value') THEN
        EXECUTE 'ALTER TABLE bills ADD CONSTRAINT chk_bills_fixed_part_value CHECK (fixed_part_value >= 0)';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conrelid = 'bills'::regclass
                     AND conname = 'chk_bills_billed_customer_pair') THEN
        EXECUTE 'ALTER TABLE bills ADD CONSTRAINT chk_bills_billed_customer_pair CHECK ((billed_customer_id IS NULL) = (billed_customer_snapshot IS NULL))';
    END IF;
END $$;

-- ------------------------------------------------------------------------
-- 3. BILLED-CUSTOMER FK AND INDEX (V4_4 section 2)
-- ------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conrelid = 'bills'::regclass
                     AND conname = 'fk_bill_billed_customer') THEN
        EXECUTE 'ALTER TABLE bills ADD CONSTRAINT fk_bill_billed_customer FOREIGN KEY (tenant_id, billed_customer_id) REFERENCES customers(tenant_id, id)';
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_bills_billed_customer ON bills(tenant_id, billed_customer_id)
    WHERE billed_customer_id IS NOT NULL;

-- ------------------------------------------------------------------------
-- 4. CANONICAL CHECKS (V4_4 section 5): drop the surviving old-formula
-- checks and recreate both with fixed_part_value in the formula.
-- The total check is dropped BOTH by its contract name AND by its
-- definition text: V4_15 only renames the anonymous check when its
-- automatic name is bills_check1 (observed on PG 17.11); on PG 16 the
-- automatic name is bills_check3, the rename does not fire and the old
-- formula survives under an automatic name (proven in this lot, POS3
-- first run). A parallel old check would keep rejecting every bill with
-- fixed_part_value > 0, exactly the failure V4_4 section 5 warns against.
-- The credits check is dropped by its definition text, exactly as V4_4
-- does, for the same reason.
-- ------------------------------------------------------------------------
ALTER TABLE bills DROP CONSTRAINT IF EXISTS chk_bills_total_with_withholding_tax;

DO $$
DECLARE
    v_total_check TEXT;
    v_credits_check TEXT;
BEGIN
    SELECT conname INTO v_total_check
      FROM pg_constraint
     WHERE conrelid = 'bills'::regclass
       AND contype = 'c'
       AND pg_get_constraintdef(oid) LIKE '%total_value =%'
       AND pg_get_constraintdef(oid) NOT LIKE '%fixed_part_value%';
    IF v_total_check IS NOT NULL THEN
        EXECUTE format('ALTER TABLE bills DROP CONSTRAINT %I', v_total_check);
    END IF;
    SELECT conname INTO v_credits_check
      FROM pg_constraint
     WHERE conrelid = 'bills'::regclass
       AND contype = 'c'
       AND pg_get_constraintdef(oid) LIKE '%credits_value <=%'
       AND pg_get_constraintdef(oid) NOT LIKE '%fixed_part_value%';
    IF v_credits_check IS NOT NULL THEN
        EXECUTE format('ALTER TABLE bills DROP CONSTRAINT %I', v_credits_check);
    END IF;
END $$;

ALTER TABLE bills ADD CONSTRAINT chk_bills_total_with_withholding_tax
    CHECK (total_value = water_value + sewer_value + fixed_part_value + debits_value - credits_value - taxes_value);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conrelid = 'bills'::regclass
                     AND conname = 'chk_bills_credits_cap') THEN
        EXECUTE 'ALTER TABLE bills ADD CONSTRAINT chk_bills_credits_cap CHECK (credits_value <= water_value + sewer_value + fixed_part_value + debits_value)';
    END IF;
END $$;
