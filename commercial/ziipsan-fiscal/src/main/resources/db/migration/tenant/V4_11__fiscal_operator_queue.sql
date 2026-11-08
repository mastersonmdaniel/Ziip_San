-- V4_11 — RESERVED in plan v3.0, section 3, for P2.33 (fiscal): "pendencias e acoes
-- do operador sobre documento fiscal do tenant (rejeicao, contingencia a transmitir,
-- inutilizacao, cancelamento pendente, reenvio), com historico".
--
-- This migration creates NO fiscal dictionary: documents, issuers, series, states,
-- access key and events stay in fiscal_documents / fiscal_issuers / fiscal_series
-- (V4_5, P1.8, DAD-18) and in the modelo-75 extension (V4_7, P2.6/P3.6). V4_11 adds
-- only the OPERATOR layer over that dictionary:
--
-- 1. fiscal_operator_actions: the trail (historico) of every operator action on the
--    queue — RESEND, VOIDING, CANCELLATION_REQUEST — each with its own permission
--    code, mandatory reason, environment (tp_amb, for the voiding event) and
--    outcome. The idempotency_key is unique per tenant: a repeated resend finds the
--    recorded attempt and never generates a second document (acceptance of P2.33).
--    VOIDING has no document (the range is of UNUSED numbers), so fiscal_document_id
--    is nullable and the range lives in number_from/number_to; the CHECKs keep each
--    action type coherent (voiding requires issuer/series/range, the others require
--    the document).
-- 2. fiscal_pendency_work_items: link between a queue pendency (REJECTION,
--    CONTINGENCY, CANCELLATION_PENDING) and the "minhas tarefas" item it generated
--    in the P2.28 inbox (work_items, V1_11 — another module, linked by id, no
--    cross-module FK, same style as the payment link of V4_7). The partial unique
--    index guarantees at most one OPEN item per (document, pendency kind).
--
-- A pending cancellation is an operator action with status PENDING here, NOT a new
-- document state: the dictionary's 9 states do not change.
--
-- NO SEEDED DATA: issuers, series and contract responsibles are tenant cadastre.

CREATE TABLE IF NOT EXISTS fiscal_operator_actions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    action_type VARCHAR(30) NOT NULL CHECK (action_type IN ('RESEND', 'VOIDING', 'CANCELLATION_REQUEST')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('REQUESTED', 'PENDING', 'COMPLETED', 'FAILED')),
    fiscal_document_id UUID,
    issuer_id UUID,
    serie INTEGER,
    number_from BIGINT,
    number_to BIGINT,
    actor_id UUID NOT NULL,
    permission_code VARCHAR(60) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    tp_amb INTEGER CHECK (tp_amb IS NULL OR tp_amb IN (1, 2)),
    protocol VARCHAR(100),
    idempotency_key VARCHAR(200) NOT NULL,
    detail_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100),
    CHECK (action_type <> 'VOIDING' OR (issuer_id IS NOT NULL AND serie IS NOT NULL
        AND number_from IS NOT NULL AND number_to IS NOT NULL AND number_from >= 1
        AND number_to >= number_from)),
    CHECK (action_type = 'VOIDING' OR fiscal_document_id IS NOT NULL),
    CHECK (action_type <> 'VOIDING' OR tp_amb IS NOT NULL)
);
ALTER TABLE fiscal_operator_actions ADD CONSTRAINT uq_fiscal_operator_actions_tenant_id UNIQUE (tenant_id, id);
-- No FK on fiscal_document_id in either table: the queue covers BOTH document kinds
-- of the same fiscal model (NFAg-e in fiscal_documents, NFS-e avulsa in
-- fiscal_nfse_avulsas, P3.6), and a single FK cannot point at both. The link is by
-- (tenant_id, id) resolved by the read port — the same style as the payment link of
-- V4_7 (payment_id without cross-module FK).
CREATE UNIQUE INDEX uq_fiscal_operator_actions_idempotency
    ON fiscal_operator_actions(tenant_id, idempotency_key);
-- At most one PENDING cancellation request per document (the operator-layer pendency).
CREATE UNIQUE INDEX uq_fiscal_operator_actions_pending_cancellation
    ON fiscal_operator_actions(tenant_id, fiscal_document_id)
    WHERE action_type = 'CANCELLATION_REQUEST' AND status = 'PENDING';
CREATE INDEX idx_fiscal_operator_actions_document
    ON fiscal_operator_actions(tenant_id, fiscal_document_id, action_type);
CREATE INDEX idx_fiscal_operator_actions_issuer_range
    ON fiscal_operator_actions(tenant_id, issuer_id, serie, number_from, number_to)
    WHERE action_type = 'VOIDING';

CREATE TABLE IF NOT EXISTS fiscal_pendency_work_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    fiscal_document_id UUID NOT NULL,
    pendency_kind VARCHAR(30) NOT NULL CHECK (pendency_kind IN ('REJECTION', 'CONTINGENCY', 'CANCELLATION_PENDING')),
    work_item_id UUID NOT NULL,
    contract_responsible_id UUID NOT NULL,
    closed_at TIMESTAMPTZ,
    close_reason VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100),
    CHECK ((closed_at IS NULL) = (close_reason IS NULL))
);
ALTER TABLE fiscal_pendency_work_items ADD CONSTRAINT uq_fiscal_pendency_work_items_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX uq_fiscal_pendency_open
    ON fiscal_pendency_work_items(tenant_id, fiscal_document_id, pendency_kind)
    WHERE closed_at IS NULL;
CREATE INDEX idx_fiscal_pendency_work_item
    ON fiscal_pendency_work_items(tenant_id, work_item_id);

-- ========================================================================
-- ISOLATION AND AUDIT (same helper used by V4_7)
-- ========================================================================

SELECT register_tenant_table('fiscal_operator_actions');
SELECT register_tenant_table('fiscal_pendency_work_items');
