-- V107 — NFAg-e bill fiscal columns (P2.6), moved here from the fiscal V4_8
-- (lote R22-A-CURA-P01, 10/11/2026; pendencia P-R22-A-01, Rodada 22).
--
-- Origin: sections 1 and 2 of Part 2 of
-- commercial/ziipsan-fiscal/src/main/resources/db/migration/tenant/V4_8__nfse_avulsa_parte.sql
-- (lines 117-178), moved VERBATIM (body byte-identical; only this header is new).
-- The fiscal V4_8 keeps sections 3-5 (fiscal_nfage_details / fiscal_nfage_payments /
-- fiscal_nfage_events) and no longer touches bills/bill_items.
--
-- Why the move (two reasons, both verified):
-- 1. Tenant provisioning in the platform test classpath failed: the fiscal V4_8 ran
--    ALTER TABLE bills while bills (V4_1, billing) is absent from that classpath
--    (platform/pom.xml depends on fiscal in test scope only and does not depend on
--    billing at all) -- Flyway stopped with 'relation "bills" does not exist'
--    (P-R22-A-01; 13/16 provisioning failures in the R22-A re-proof). bills and
--    bill_items belong to this module, so their columns live in a billing migration.
-- 2. Production loss: V4_14 recreates bills and bill_items with an explicit column
--    list that does NOT include these fiscal columns, so in full Flyway order the
--    V4_8 columns were dropped again by V4_14. V107 runs AFTER V4_14 (and after
--    V4_16), so the columns survive the recreation. The unique index below includes
--    the partition key (reference_month), as PostgreSQL requires for unique indexes
--    on partitioned tables.
--
-- Known deviation (honest): the header of V4_16 (lines 26-27) declares a dependency
-- on "V4_7 fiscal (P2.6, bills.fiscal_document_id)". The column now arrives in V107,
-- AFTER V4_16. Harmless: V4_16 never references the column in SQL (only its header
-- line 27 mentions it). V4_16 is published and immutable (Flyway refuses an altered
-- checksum), so its header cannot be fixed.
--
-- Precedent for the cross-module FK: V6_2 (collection) already declares
-- FOREIGN KEY (tenant_id, fiscal_document_id) REFERENCES fiscal_documents
-- (tenant_id, id) -- a table of another module pointing at the fiscal dictionary
-- is the same pattern, now in the module that owns the table.
--
-- NO SEEDED DATA: rates and values come from the bill item (tax cadastre), never
-- from this migration.

-- 1. Bill -> NFAg-e (one live NFAg-e per bill, one bill per document)
-- ------------------------------------------------------------------------

ALTER TABLE bills ADD COLUMN IF NOT EXISTS fiscal_document_id UUID;
ALTER TABLE bills ADD CONSTRAINT fk_bill_fiscal_document
    FOREIGN KEY (tenant_id, fiscal_document_id) REFERENCES fiscal_documents(tenant_id, id);
CREATE INDEX idx_bills_fiscal_document ON bills(tenant_id, fiscal_document_id)
    WHERE fiscal_document_id IS NOT NULL;
-- A fiscal document is linked to at most one bill within the bill's reference month
-- (reference_month is the partition key and must be part of the unique index). The
-- cross-month guarantee is the dictionary's live-origin uniqueness
-- (source_type, source_id) in fiscal_documents plus emission idempotency in code.
CREATE UNIQUE INDEX uq_bills_fiscal_document ON bills(tenant_id, reference_month, fiscal_document_id)
    WHERE fiscal_document_id IS NOT NULL;
COMMENT ON COLUMN bills.fiscal_document_id IS
    'NFAg-e viva da conta (fiscal_documents, modelo 75, P2.6/V4_8). NULL enquanto a conta nao tem NFAg-e.';

-- ------------------------------------------------------------------------
-- 2. Bill items: fiscal item code and the per-item IBS/CBS group (NT 2026.002)
-- ------------------------------------------------------------------------
-- CONDITIONAL ON THE DEFINITIVE MOC: fiscal_item_code (codigo de classificacao do
-- item), the CST and cClassTrib codes and the very presence/shape of the per-item
-- IBS/CBS group depend on the definitive MOC layout version. The columns are
-- therefore all nullable: an item without the group is an item whose layout version
-- does not carry it yet, never an item with invented values. IBS/CBS rates and
-- values come from the bill item (tax cadastre), never from this migration.

ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS fiscal_item_code VARCHAR(60);
ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS ibs_cbs_cst VARCHAR(10);
ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS ibs_cbs_cclass_trib VARCHAR(20);
ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS ibs_cbs_base NUMERIC(14,2);
ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS ibs_rate NUMERIC(7,4);
ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS ibs_value NUMERIC(14,2);
ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS cbs_rate NUMERIC(7,4);
ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS cbs_value NUMERIC(14,2);
ALTER TABLE bill_items ADD CONSTRAINT chk_bill_items_ibs_cbs_nonnegative
    CHECK ((ibs_cbs_base IS NULL OR ibs_cbs_base >= 0)
       AND (ibs_rate IS NULL OR ibs_rate >= 0)
       AND (ibs_value IS NULL OR ibs_value >= 0)
       AND (cbs_rate IS NULL OR cbs_rate >= 0)
       AND (cbs_value IS NULL OR cbs_value >= 0));
-- A tax value without its calculation base is a broken item, never a partial group.
ALTER TABLE bill_items ADD CONSTRAINT chk_bill_items_ibs_cbs_base
    CHECK ((ibs_value IS NULL OR ibs_cbs_base IS NOT NULL)
       AND (cbs_value IS NULL OR ibs_cbs_base IS NOT NULL));
COMMENT ON COLUMN bill_items.fiscal_item_code IS
    'Codigo de classificacao fiscal do item da NFAg-e (NT 2026.002). CONDICIONAL ao MOC definitivo: obrigatoriedade e dominio seguem a versao do leiaute.';
COMMENT ON COLUMN bill_items.ibs_cbs_cst IS
    'CST do grupo IBS/CBS por item (NT 2026.002). CONDICIONAL ao MOC definitivo.';
COMMENT ON COLUMN bill_items.ibs_cbs_cclass_trib IS
    'cClassTrib do grupo IBS/CBS por item (NT 2026.002). CONDICIONAL ao MOC definitivo.';
COMMENT ON COLUMN bill_items.ibs_cbs_base IS
    'Base de calculo do grupo IBS/CBS por item (NT 2026.002). CONDICIONAL ao MOC definitivo.';
COMMENT ON COLUMN bill_items.ibs_rate IS
    'Aliquota do IBS do item (NT 2026.002): vem do cadastro tributario, nunca de constante. CONDICIONAL ao MOC definitivo.';
COMMENT ON COLUMN bill_items.ibs_value IS
    'Valor do IBS do item (NT 2026.002). CONDICIONAL ao MOC definitivo.';
COMMENT ON COLUMN bill_items.cbs_rate IS
    'Aliquota da CBS do item (NT 2026.002): vem do cadastro tributario, nunca de constante. CONDICIONAL ao MOC definitivo.';
COMMENT ON COLUMN bill_items.cbs_value IS
    'Valor da CBS do item (NT 2026.002). CONDICIONAL ao MOC definitivo.';

