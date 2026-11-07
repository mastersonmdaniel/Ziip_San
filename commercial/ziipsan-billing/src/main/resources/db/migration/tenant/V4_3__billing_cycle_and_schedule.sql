-- Billing cycle and monthly schedule (P2.5, front F1; migration reserved for
-- the cycle in plan section 3).
-- Version: 4.3.0
-- Description: billing groups, monthly billing reference (the cycle), the
-- product activity catalog (order, mandatory flag, repetition, precedence)
-- and the monthly schedule per group: one item per group, reference and
-- activity, with planned, commanded and done dates.
--
-- Depends on: V4_1 (billing) and V1_2 (register_tenant_table).
--
-- NO SEEDED DATA. The activity catalog table starts EMPTY, like every other
-- catalog of this module (tariffs in V4_1, end reasons in V4_2): the rows of
-- the PRODUCT catalog (READING, TRANSMISSION, BILLING, CLOSING, in this
-- order, with the precedence chain) are loaded by CycleService from
-- ProductActivityCatalog, never by this migration. A migration must not
-- invent tenant data, and a catalog row written by SQL could not be told
-- apart from tenant policy. The absence of a catalog row is an explicit
-- error in the service (the reference cannot be opened), never a silent
-- default.
--
-- The batch execution itself is NOT modelled here: commanding the BILLING
-- activity dispatches a run through the P2.1 foundation (public.job_runs,
-- core V10) and only the returned run id is kept, in job_run_id, with no
-- foreign key -- the same shape as bill_audit_batches.run_id in V4_6.

-- ========================================================================
-- BILLING GROUP
-- ========================================================================
-- A group is the set of units billed together in a reference, on one
-- reading route. route_code is the route identifier handed to the P2.1
-- batch foundation when the group's billing is dispatched (one run per
-- tenant and route); it is data of the tenant, never seeded.

CREATE TABLE IF NOT EXISTS billing_groups (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    route_code VARCHAR(40) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE billing_groups ADD CONSTRAINT uq_billing_groups_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_billing_groups_code ON billing_groups(tenant_id, code);
CREATE INDEX idx_billing_groups_active ON billing_groups(tenant_id, active);

-- ========================================================================
-- BILLING REFERENCE (the monthly cycle)
-- ========================================================================
-- reference_month: first day of the competence month, as in bills (V4_1).
-- status: OPEN (activities may be commanded, bills may be generated) or
-- CLOSED (the month is closed after every group; no generation is allowed
-- in a closed reference -- enforced by CycleService and asserted by test).
-- closed_at exists exactly when the reference is CLOSED.

CREATE TABLE IF NOT EXISTS billing_references (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    reference_month DATE NOT NULL CHECK (EXTRACT(DAY FROM reference_month) = 1),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
    closed_at TIMESTAMPTZ,
    closed_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))
);
ALTER TABLE billing_references ADD CONSTRAINT uq_billing_references_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_billing_references_month ON billing_references(tenant_id, reference_month);
CREATE INDEX idx_billing_references_status ON billing_references(tenant_id, status, reference_month DESC);

-- ========================================================================
-- PRODUCT ACTIVITY CATALOG
-- ========================================================================
-- One row per product activity of the cycle. sequence_order is the order
-- of the activity in the cycle; mandatory says a group cannot be finished
-- (and the reference cannot be closed) without it; repeatable says a DONE
-- activity may be commanded again as a new occurrence (re-reading,
-- retransmission) -- a non-repeatable activity (BILLING, CLOSING) is
-- commanded at most once per group and reference.
-- predecessor_id: the activity that must be DONE before this one may be
-- commanded; NULL only for the first activity of the chain. The chain is
-- acyclic by construction in the product catalog loaded by the service;
-- the database enforces only that the predecessor belongs to the same
-- tenant and is not the activity itself.

CREATE TABLE IF NOT EXISTS cycle_activities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    sequence_order SMALLINT NOT NULL CHECK (sequence_order >= 1),
    mandatory BOOLEAN NOT NULL,
    repeatable BOOLEAN NOT NULL,
    predecessor_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (predecessor_id IS NULL OR predecessor_id <> id)
);
ALTER TABLE cycle_activities ADD CONSTRAINT uq_cycle_activities_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE cycle_activities ADD CONSTRAINT fk_cycle_activity_predecessor
    FOREIGN KEY (tenant_id, predecessor_id) REFERENCES cycle_activities(tenant_id, id);
CREATE UNIQUE INDEX idx_cycle_activities_code ON cycle_activities(tenant_id, code);
CREATE UNIQUE INDEX idx_cycle_activities_order ON cycle_activities(tenant_id, sequence_order);

-- ========================================================================
-- MONTHLY SCHEDULE PER GROUP (activity instances)
-- ========================================================================
-- One row per (reference, group, activity, occurrence): the schedule of
-- the group in the month. planned_on is the planned date of the activity;
-- commanded_at/commanded_by record the command; done_at records the
-- completion. status follows the dates exactly:
--   PENDING   -- not commanded yet (both instants NULL)
--   COMMANDED -- commanded, not done (done_at NULL)
--   DONE      -- commanded and done (both instants present)
--   CANCELLED -- given up before completion (done_at NULL)
-- occurrence_no starts at 1; a repeatable activity commanded again after
-- DONE creates occurrence 2, 3, ... -- the history of repetitions is kept,
-- never overwritten.
-- job_run_id: the P2.1 run (public.job_runs) dispatched by the command of
-- this item, when the command dispatches a batch (BILLING). No FK, as in
-- bill_audit_batches.run_id (V4_6): job_runs lives in the public schema.

CREATE TABLE IF NOT EXISTS cycle_schedule_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    reference_id UUID NOT NULL,
    group_id UUID NOT NULL,
    activity_id UUID NOT NULL,
    occurrence_no SMALLINT NOT NULL DEFAULT 1 CHECK (occurrence_no >= 1),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'COMMANDED', 'DONE', 'CANCELLED')),
    planned_on DATE NOT NULL,
    commanded_at TIMESTAMPTZ,
    commanded_by UUID,
    done_at TIMESTAMPTZ,
    job_run_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK ((status = 'PENDING' AND commanded_at IS NULL AND done_at IS NULL)
        OR (status = 'COMMANDED' AND commanded_at IS NOT NULL AND done_at IS NULL)
        OR (status = 'DONE' AND commanded_at IS NOT NULL AND done_at IS NOT NULL)
        OR (status = 'CANCELLED' AND done_at IS NULL)),
    CHECK (commanded_at IS NULL OR commanded_by IS NOT NULL)
);
ALTER TABLE cycle_schedule_items ADD CONSTRAINT uq_cycle_schedule_items_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE cycle_schedule_items ADD CONSTRAINT fk_cycle_item_reference
    FOREIGN KEY (tenant_id, reference_id) REFERENCES billing_references(tenant_id, id);
ALTER TABLE cycle_schedule_items ADD CONSTRAINT fk_cycle_item_group
    FOREIGN KEY (tenant_id, group_id) REFERENCES billing_groups(tenant_id, id);
ALTER TABLE cycle_schedule_items ADD CONSTRAINT fk_cycle_item_activity
    FOREIGN KEY (tenant_id, activity_id) REFERENCES cycle_activities(tenant_id, id);
CREATE UNIQUE INDEX idx_cycle_schedule_unique ON cycle_schedule_items(tenant_id, reference_id, group_id, activity_id, occurrence_no);
CREATE INDEX idx_cycle_schedule_reference ON cycle_schedule_items(tenant_id, reference_id, group_id, status);
CREATE INDEX idx_cycle_schedule_group ON cycle_schedule_items(tenant_id, group_id, reference_id DESC);

-- ========================================================================
-- ISOLATION AND AUDIT
-- ========================================================================

SELECT register_tenant_table('billing_groups');
SELECT register_tenant_table('billing_references');
SELECT register_tenant_table('cycle_activities');
SELECT register_tenant_table('cycle_schedule_items');
