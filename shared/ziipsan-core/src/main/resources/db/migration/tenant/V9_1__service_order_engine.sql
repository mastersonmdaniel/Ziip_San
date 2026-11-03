-- Service order engine (P1.31, plan v3.0 line 657; ADR-0039, ADR-0045).
-- Version: 9.1.0
-- Description: generic work-order engine in the nucleus. The engine knows no
-- sanitation type by name (ADR-0045): service_types starts EMPTY; the package
-- declares its types in packages/sanitation/vertical.json (section
-- serviceTypes) and the tenant registers them through the engine. Effect
-- handlers and target-existence verifiers are registered by the module that
-- owns the entity (ADR-0045 item 3); a target class with no registered
-- verifier cannot be declared by a service type (enforced by the engine).
--
-- VERSION HISTORY -- READ BEFORE RENUMBERING:
-- * This is a TENANT migration: it runs once per tenant schema through
--   TenantMigrator (classpath db/migration/tenant). Its tables are tenant
--   tables (tenant_id, forced RLS, register_tenant_table).
-- * It is NOT the core-history V9_1 named by the P1.17 row (Central, public
--   schema, db/migration/core). Core and tenant histories are separate
--   Flyway histories (separate flyway_schema_history tables), so the shared
--   number cannot collide; the P1.17 core V9_1 file does not exist in this
--   checkout. Do not "fix" this number: plan section 3 reserves V9_1 for the
--   OS engine, and the tenant history has no V9_1 yet (verified: tenant
--   versions in use are V1_x, V2_x, V3_x, V4_x, V5_x, V6_x, V13_x).
-- * The V1_1 rewrite the plan row mentions ("created over the rewrite of
--   V1_1 without the old service_orders") is NOT needed anymore: P0.25
--   already removed service_orders from V1_1 (see the V1_1 header comment:
--   "o modelo de OS renasce na P1.31 (V9_1)"). This migration therefore
--   creates the engine from scratch; it does not rewrite V1_1 and does not
--   migrate any old service_orders row (none can exist: V1_1 never created
--   the table after P0.25).
--
-- SCOPE (what this migration deliberately does NOT do):
-- * protocol_id is RESERVED on service_orders with NO foreign key: the FK is
--   created by P1.36 (V8_1, protocol table). The origin/protocol CHECK below
--   already enforces the P1.31 side: EXTERNAL orders carry a protocol_id,
--   INTERNAL (backoffice) orders never do.
-- * No price, billing posting or charge validity: price_item_ref on
--   service_types is a reference only; price, validity and posting are ED-34.
-- * No cut/reconnect blocks (P3.4, V9_2), no backoffice scheduling/execution
--   screens (ED-36), no offline execution (ED-20), no station diary (ED-87),
--   no holder situations (P1.41), no confined-space registry (P1.35).
-- * No seeded service type and no seeded close reason: both are tenant
--   policy (precedent: social_tariff_end_reasons starts empty in V4_2).
--   Seeding a type here would freeze package content in the engine,
--   against ADR-0045 item 4.
-- * Materials go to the requisition (ADR-0032) and photos to documents:
--   neither table is created here (requisition ED-58, documents P1.20).
--
-- STATE MACHINE (also enforced by the trigger below and by the engine):
--   DRAFT -> OPEN, PENDING_APPROVAL, CLOSED_NOT_EXECUTED
--   OPEN -> SCHEDULED, IN_EXECUTION, WAITING_DEPENDENCY, PENDING_APPROVAL,
--           EXECUTED, CLOSED_NOT_EXECUTED
--   SCHEDULED -> IN_EXECUTION, WAITING_DEPENDENCY, CLOSED_NOT_EXECUTED
--   IN_EXECUTION -> EXECUTED, WAITING_DEPENDENCY, CLOSED_NOT_EXECUTED
--   WAITING_DEPENDENCY -> OPEN, SCHEDULED, IN_EXECUTION, CLOSED_NOT_EXECUTED
--   PENDING_APPROVAL -> OPEN, CLOSED_NOT_EXECUTED
--   EXECUTED -> CLOSED_EXECUTED
--   CLOSED_* are terminal. INTERNAL orders may go OPEN -> EXECUTED directly
--   (no field trip); FIELD orders go through SCHEDULED/IN_EXECUTION (ED-36
--   owns the scheduling screens, the engine only guards the transitions).
--   Effect application happens on the EXECUTED transition, once per target
--   (service_order_effects + targets.effect_applied); there is no reversal:
--   a correction is a new order linked by reverses_order_id.
--
-- WORK SAFETY (SST), normative anchors, all cited in the engine code:
--   NR-33 (confined space): entry permit (PET) per entry, traceable,
--   retained 5 years (33.3.3 f/g/j, 33.3.3.1, 33.5.3); no lone work
--   (33.3.4.4 -> min crew 2, class a floor in sst.min_crew_confined_space);
--   refresher training every 12 months (33.3.5.3/4/6 -> factory default of
--   sst.training_validity_months, tenant only reduces).
--   NR-18 (construction): excavation above 1.25 m starts only with a
--   qualified professional release (18.7.2.3).
--   NR-35 (height): risk analysis before every height job (35.5.5), work
--   permit for non-routine activity (35.5.7), formally authorized worker
--   (35.4.1).
--   NR-10 (electricity): cited in the wording in force from 01/06/2027
--   (Portaria MTE 737/2026); the engine records the norm reference on the
--   type, it does not hardcode the future wording.
--   NR-1 (occupational risk management): the umbrella norm for risk
--   analysis; all four (NR-1, NR-18, NR-33, NR-35) are mandatory references
--   in ADR-0041. The tenant parameter sst.required_controls_by_service_type
--   is class c: the tenant only ADDS controls, never removes the factory
--   ones (enforced by the engine's parameter validation).
--   Competency is checked against the P3.31 / ED-59 competency registry
--   THROUGH A CONTRACT, when such a registry is bound; until then the
--   declared qualification and its validity date on the crew row govern
--   (fail-closed: no qualification, no schedule, no close).
--   Optional steps enabled by a linked norm (risk analysis, work permit,
--   environmental occurrence) are OFF by default on types without a safety
--   flag: requires_* defaults to false below.
--
-- HISTORY TAB: service_order_history records every transition with actor,
--   time, before and after; service_order_effects records every applied
--   effect; unit_status_history and the new histories carry service_order_id
--   NOT NULL (ADR-0039 item 6). The tab itself is a read over these tables
--   plus the version tables (parameter_versions, bill versions, tariff
--   versions, gis_*_history) and the audit trail; the frontend tab is P1.12.
--
-- PRIVACY: service_order_notes.body is free text and may carry personal
--   data (the plan warns against sensitive personal data in free text):
--   REDACT in the audit trail + data_classification ID (derived by the
--   R__data_classification repeatable; the explicit row below also covers
--   tenants migrated out of order, where the repeatable does not re-run).
--   service_order_targets.coordinates is a service location, which may be a
--   residence: OMIT in the audit trail + LOC. service_types.name is a
--   catalog name, not personal data (NOT_PERSONAL allowlist entry, same
--   precedent as categories.name).
--
-- NO SYNTHETIC PERSONAL DATA: no seed row below carries a document, a
--   name of a person, an address or a phone number.

-- ========================================================================
-- SERVICE TYPE CATALOG (tenant policy; starts empty)
-- ========================================================================
-- code: @NotBlank in the engine (CHECK below); never optional, never null.
-- operation: what is done (operation key of the type, e.g. the field or
--   backoffice operation the type stands for).
-- effect_code: effect identifier dispatched to a registered EffectHandler;
--   NULL exactly for operational types (no commercial effect), which must
--   then carry an origin.
-- indicator: INTERNAL (backoffice, no field trip) or FIELD (field work;
--   an order of a FIELD type may still run as CONTRACTOR).
-- requires_approval: ADR-0039 item 8 factory default per type (approval by
--   another person for billing-changing types, direct application for
--   contact data); the tenant parameter service_type.internal_approval is
--   the per-type override, the column is the factory value.
-- target_classes: accepted target classes, each element in the 14-class
--   catalog of ALVOS-DA-OS-E-HIERARQUIA-DE-ATIVOS; the engine refuses to
--   declare a class with no registered existence verifier (except
--   LOCATION_ONLY, which carries a location instead of an entity).
-- price_item_ref: reference only (ED-34 owns price, validity, posting).
-- deadline_hours: agency deadline of the type; the tenant parameter
--   so.deadline_hours_by_type may only REDUCE it, never exceed it.
-- Safety flags: confined_space, height_work, trench_work, electrical_work
--   plus the controls they imply (CHECKs below mirror the norms, so a type
--   cannot claim a hazard without its controls).

CREATE TABLE IF NOT EXISTS service_types (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(200) NOT NULL,
    operation VARCHAR(100) NOT NULL,
    effect_code VARCHAR(100),
    effect_schema JSONB,
    indicator VARCHAR(20) NOT NULL CHECK (indicator IN ('INTERNAL', 'FIELD')),
    requires_approval BOOLEAN NOT NULL DEFAULT FALSE,
    operational BOOLEAN NOT NULL DEFAULT FALSE,
    origin VARCHAR(30) CHECK (origin IN ('NETWORK_INCIDENT', 'PREVENTIVE_PLAN', 'INSPECTION', 'COMPLAINT')),
    target_classes TEXT[] NOT NULL,
    price_item_ref VARCHAR(100),
    deadline_hours INT CHECK (deadline_hours IS NULL OR deadline_hours > 0),
    min_crew INT CHECK (min_crew IS NULL OR min_crew >= 1),
    confined_space BOOLEAN NOT NULL DEFAULT FALSE,
    height_work BOOLEAN NOT NULL DEFAULT FALSE,
    trench_work BOOLEAN NOT NULL DEFAULT FALSE,
    electrical_work BOOLEAN NOT NULL DEFAULT FALSE,
    requires_risk_analysis BOOLEAN NOT NULL DEFAULT FALSE,
    requires_entry_permit BOOLEAN NOT NULL DEFAULT FALSE,
    requires_work_permit BOOLEAN NOT NULL DEFAULT FALSE,
    required_norm_codes TEXT[] NOT NULL DEFAULT '{}',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (char_length(btrim(code)) > 0),
    CHECK (char_length(btrim(name)) > 0),
    CHECK (char_length(btrim(operation)) > 0),
    CHECK (cardinality(target_classes) > 0),
    CHECK (target_classes <@ ARRAY['CONSUMER_UNIT', 'WATER_CONNECTION', 'SEWER_CONNECTION', 'METER', 'GIS_LINEAR', 'GIS_POINT', 'OPERATIONAL_SITE', 'EQUIPMENT', 'COMPONENT', 'BUILDING', 'FLEET_ASSET', 'NETWORK_INCIDENT', 'ZONE', 'LOCATION_ONLY']),
    CHECK ((operational AND effect_code IS NULL AND origin IS NOT NULL)
        OR (NOT operational AND effect_code IS NOT NULL)),
    CHECK (NOT confined_space OR (requires_risk_analysis AND requires_entry_permit)),
    CHECK (NOT height_work OR (requires_risk_analysis AND requires_work_permit)),
    CHECK (NOT trench_work OR requires_risk_analysis),
    CHECK (NOT electrical_work OR requires_work_permit),
    CHECK (NOT confined_space OR min_crew IS NULL OR min_crew >= 2)
);
ALTER TABLE service_types ADD CONSTRAINT uq_service_types_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE service_types ADD CONSTRAINT uq_service_types_code UNIQUE (tenant_id, code);
CREATE INDEX idx_service_types_active ON service_types(tenant_id, active);

-- ========================================================================
-- CLOSE-REASON CATALOG (tenant policy; starts empty)
-- ========================================================================

CREATE TABLE IF NOT EXISTS service_order_close_reasons (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    description VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (char_length(btrim(code)) > 0),
    CHECK (char_length(btrim(description)) > 0)
);
ALTER TABLE service_order_close_reasons ADD CONSTRAINT uq_close_reasons_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE service_order_close_reasons ADD CONSTRAINT uq_close_reasons_code UNIQUE (tenant_id, code);

-- ========================================================================
-- BATCHES (ADR-0039 item 9: one internal batch order references each unit)
-- ========================================================================

CREATE TABLE IF NOT EXISTS service_order_batches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    description VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (char_length(btrim(code)) > 0)
);
ALTER TABLE service_order_batches ADD CONSTRAINT uq_batches_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE service_order_batches ADD CONSTRAINT uq_batches_code UNIQUE (tenant_id, code);

-- ========================================================================
-- SERVICE ORDERS
-- ========================================================================
-- created_by is the registrar (NOT NULL): the external requester comes
-- from the protocol (P1.36), never from a column here.
-- protocol_id: RESERVED, no FK yet (P1.36 creates it). The origin CHECK is
-- the P1.31 side of the protocol contract, enforced from day one.

CREATE TABLE IF NOT EXISTS service_orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    service_type_id UUID NOT NULL,
    batch_id UUID,
    parent_order_id UUID,
    reverses_order_id UUID,
    protocol_id UUID,
    origin VARCHAR(20) NOT NULL CHECK (origin IN ('EXTERNAL', 'INTERNAL')),
    execution_mode VARCHAR(20) NOT NULL CHECK (execution_mode IN ('FIELD', 'INTERNAL', 'CONTRACTOR')),
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL' CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'OPEN', 'SCHEDULED', 'IN_EXECUTION', 'EXECUTED', 'WAITING_DEPENDENCY', 'PENDING_APPROVAL', 'CLOSED_EXECUTED', 'CLOSED_NOT_EXECUTED')),
    created_by UUID NOT NULL,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    scheduled_for TIMESTAMPTZ,
    due_at TIMESTAMPTZ,
    executed_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    close_reason_code VARCHAR(60),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    CHECK ((origin = 'EXTERNAL') = (protocol_id IS NOT NULL)),
    CHECK (approved_by IS NULL OR approved_by <> created_by),
    CHECK ((approved_by IS NULL) = (approved_at IS NULL)),
    CHECK (reverses_order_id IS NULL OR reverses_order_id <> id),
    CHECK (parent_order_id IS NULL OR parent_order_id <> id),
    CHECK (status <> 'CLOSED_EXECUTED' OR executed_at IS NOT NULL),
    CHECK (status NOT IN ('CLOSED_EXECUTED', 'CLOSED_NOT_EXECUTED') OR closed_at IS NOT NULL),
    CHECK (status <> 'CLOSED_NOT_EXECUTED' OR close_reason_code IS NOT NULL)
);
ALTER TABLE service_orders ADD CONSTRAINT uq_service_orders_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE service_orders ADD CONSTRAINT fk_orders_type
    FOREIGN KEY (tenant_id, service_type_id) REFERENCES service_types(tenant_id, id);
ALTER TABLE service_orders ADD CONSTRAINT fk_orders_batch
    FOREIGN KEY (tenant_id, batch_id) REFERENCES service_order_batches(tenant_id, id);
ALTER TABLE service_orders ADD CONSTRAINT fk_orders_parent
    FOREIGN KEY (tenant_id, parent_order_id) REFERENCES service_orders(tenant_id, id);
ALTER TABLE service_orders ADD CONSTRAINT fk_orders_reverses
    FOREIGN KEY (tenant_id, reverses_order_id) REFERENCES service_orders(tenant_id, id);
ALTER TABLE service_orders ADD CONSTRAINT fk_orders_close_reason
    FOREIGN KEY (tenant_id, close_reason_code) REFERENCES service_order_close_reasons(tenant_id, code);
CREATE INDEX idx_orders_type ON service_orders(tenant_id, service_type_id);
CREATE INDEX idx_orders_status ON service_orders(tenant_id, status);
CREATE INDEX idx_orders_batch ON service_orders(tenant_id, batch_id);
CREATE INDEX idx_orders_protocol ON service_orders(tenant_id, protocol_id);
CREATE INDEX idx_orders_reverses ON service_orders(tenant_id, reverses_order_id);

-- State machine trigger: an invalid transition is refused at the database
-- level too, not only by the engine (plan: "transicao invalida recusada").
-- INSERT is allowed only in DRAFT; the engine creates every order there.
CREATE OR REPLACE FUNCTION service_order_transition_guard() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'service order must be created in DRAFT (got %)', NEW.status
                USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;
    IF OLD.status IS DISTINCT FROM NEW.status THEN
        IF NOT (
            (OLD.status = 'DRAFT' AND NEW.status IN ('OPEN', 'PENDING_APPROVAL', 'CLOSED_NOT_EXECUTED'))
            OR (OLD.status = 'OPEN' AND NEW.status IN ('SCHEDULED', 'IN_EXECUTION', 'WAITING_DEPENDENCY', 'PENDING_APPROVAL', 'EXECUTED', 'CLOSED_NOT_EXECUTED'))
            OR (OLD.status = 'SCHEDULED' AND NEW.status IN ('IN_EXECUTION', 'WAITING_DEPENDENCY', 'CLOSED_NOT_EXECUTED'))
            OR (OLD.status = 'IN_EXECUTION' AND NEW.status IN ('EXECUTED', 'WAITING_DEPENDENCY', 'CLOSED_NOT_EXECUTED'))
            OR (OLD.status = 'WAITING_DEPENDENCY' AND NEW.status IN ('OPEN', 'SCHEDULED', 'IN_EXECUTION', 'CLOSED_NOT_EXECUTED'))
            OR (OLD.status = 'PENDING_APPROVAL' AND NEW.status IN ('OPEN', 'CLOSED_NOT_EXECUTED'))
            OR (OLD.status = 'EXECUTED' AND NEW.status = 'CLOSED_EXECUTED')
        ) THEN
            RAISE EXCEPTION 'invalid service order transition % -> %', OLD.status, NEW.status
                USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_service_order_transition ON service_orders;
CREATE TRIGGER trg_service_order_transition
    BEFORE INSERT OR UPDATE ON service_orders
    FOR EACH ROW EXECUTE FUNCTION service_order_transition_guard();

-- ========================================================================
-- TYPED TARGETS (one order, many targets; one applied marker per target)
-- ========================================================================
-- LOCATION_ONLY carries a location (location_text / coordinates) and no
-- entity id; every other class carries an entity id and no location.

CREATE TABLE IF NOT EXISTS service_order_targets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    target_type VARCHAR(30) NOT NULL CHECK (target_type IN ('CONSUMER_UNIT', 'WATER_CONNECTION', 'SEWER_CONNECTION', 'METER', 'GIS_LINEAR', 'GIS_POINT', 'OPERATIONAL_SITE', 'EQUIPMENT', 'COMPONENT', 'BUILDING', 'FLEET_ASSET', 'NETWORK_INCIDENT', 'ZONE', 'LOCATION_ONLY')),
    target_id UUID,
    location_text VARCHAR(500),
    coordinates GEOMETRY(POINT, 4674),
    effect_code VARCHAR(100),
    effect_payload JSONB,
    effect_applied BOOLEAN NOT NULL DEFAULT FALSE,
    effect_applied_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK ((target_type = 'LOCATION_ONLY') = (target_id IS NULL)),
    CHECK (target_type = 'LOCATION_ONLY' OR (location_text IS NULL AND coordinates IS NULL)),
    CHECK ((effect_applied) = (effect_applied_at IS NOT NULL))
);
ALTER TABLE service_order_targets ADD CONSTRAINT uq_targets_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE service_order_targets ADD CONSTRAINT fk_targets_order
    FOREIGN KEY (tenant_id, order_id) REFERENCES service_orders(tenant_id, id);
ALTER TABLE service_order_targets ADD CONSTRAINT uq_targets_order_target
    UNIQUE (tenant_id, order_id, target_type, target_id);
CREATE INDEX idx_targets_order ON service_order_targets(tenant_id, order_id);
CREATE INDEX idx_targets_entity ON service_order_targets(tenant_id, target_type, target_id);

-- ========================================================================
-- APPLIED EFFECTS (applied once; no reversal, correction by inverse order)
-- ========================================================================

CREATE TABLE IF NOT EXISTS service_order_effects (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    target_id UUID,
    effect_code VARCHAR(100) NOT NULL,
    payload JSONB,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    applied_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (char_length(btrim(effect_code)) > 0)
);
ALTER TABLE service_order_effects ADD CONSTRAINT uq_effects_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE service_order_effects ADD CONSTRAINT fk_effects_order
    FOREIGN KEY (tenant_id, order_id) REFERENCES service_orders(tenant_id, id);
ALTER TABLE service_order_effects ADD CONSTRAINT fk_effects_target
    FOREIGN KEY (tenant_id, target_id) REFERENCES service_order_targets(tenant_id, id);
ALTER TABLE service_order_effects ADD CONSTRAINT uq_effects_order_target_code
    UNIQUE (tenant_id, order_id, target_id, effect_code);
CREATE UNIQUE INDEX idx_effects_order_level_code ON service_order_effects(tenant_id, order_id, effect_code)
    WHERE target_id IS NULL;
CREATE INDEX idx_effects_order ON service_order_effects(tenant_id, order_id);

-- ========================================================================
-- ORDER HISTORY (History tab source: who, when, before, after)
-- ========================================================================

CREATE TABLE IF NOT EXISTS service_order_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    event_type VARCHAR(30) NOT NULL CHECK (event_type IN ('CREATED', 'TRANSITION', 'APPROVAL', 'EFFECT_APPLIED')),
    from_status VARCHAR(30) CHECK (from_status IS NULL OR from_status IN ('DRAFT', 'OPEN', 'SCHEDULED', 'IN_EXECUTION', 'EXECUTED', 'WAITING_DEPENDENCY', 'PENDING_APPROVAL', 'CLOSED_EXECUTED', 'CLOSED_NOT_EXECUTED')),
    to_status VARCHAR(30) CHECK (to_status IS NULL OR to_status IN ('DRAFT', 'OPEN', 'SCHEDULED', 'IN_EXECUTION', 'EXECUTED', 'WAITING_DEPENDENCY', 'PENDING_APPROVAL', 'CLOSED_EXECUTED', 'CLOSED_NOT_EXECUTED')),
    actor_id UUID NOT NULL,
    reason VARCHAR(500),
    payload JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK ((event_type <> 'TRANSITION') OR (from_status IS NOT NULL AND to_status IS NOT NULL))
);
ALTER TABLE service_order_history ADD CONSTRAINT uq_history_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE service_order_history ADD CONSTRAINT fk_history_order
    FOREIGN KEY (tenant_id, order_id) REFERENCES service_orders(tenant_id, id);
CREATE INDEX idx_history_order_time ON service_order_history(tenant_id, order_id, occurred_at);

-- ========================================================================
-- INTERNAL NOTES (observations on the order, with author and visibility)
-- ========================================================================
-- Notes on the consumer unit itself live in consumer_units.notes; notes on
-- the protocol, the bill and the network incident arrive with the tables
-- that own them (P1.36 and later tasks) and reuse this same shape: author,
-- visibility (INTERNAL or USER_VISIBLE) and a personal-data alert on free
-- text. The alert is a warning, not a refusal: the plan asks for an alert
-- against sensitive personal data in free text, and the audit trail
-- redacts the body regardless.

CREATE TABLE IF NOT EXISTS service_order_notes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    author_id UUID NOT NULL,
    visibility VARCHAR(20) NOT NULL CHECK (visibility IN ('INTERNAL', 'USER_VISIBLE')),
    body TEXT NOT NULL,
    personal_data_alert BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (char_length(btrim(body)) > 0)
);
ALTER TABLE service_order_notes ADD CONSTRAINT uq_notes_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE service_order_notes ADD CONSTRAINT fk_notes_order
    FOREIGN KEY (tenant_id, order_id) REFERENCES service_orders(tenant_id, id);
CREATE INDEX idx_notes_order ON service_order_notes(tenant_id, order_id);

-- ========================================================================
-- WORK SAFETY: per-order safety record, crew, entry permits (PET)
-- ========================================================================
-- One safety record per order. The schedule gate and the close gate (in
-- the engine) require, for each flag on the type: risk analysis reference,
-- entry/work permit reference, a crew of at least min_crew with a vigente
-- qualification each, and, for a trench deeper than 1.25 m, a qualified
-- professional release (NR-18, 18.7.2.3 -- enforced by the engine's
-- schedule gate, not by a table CHECK: the depth must stay recordable).
-- PET: one row per entry (NR-33: a permit is valid for a single entry
-- only), traceable by permit_code, retained at least 5 years (CHECK below).

CREATE TABLE IF NOT EXISTS service_order_safety (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    risk_analysis_ref VARCHAR(200),
    entry_permit_ref VARCHAR(200),
    work_permit_ref VARCHAR(200),
    trench_depth_m NUMERIC(6, 2) CHECK (trench_depth_m IS NULL OR trench_depth_m >= 0),
    qualified_release_by UUID,
    qualified_release_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK ((qualified_release_by IS NULL) = (qualified_release_at IS NULL))
    -- NOTE: no depth-to-release CHECK here on purpose. A depth above 1.25 m
    -- must be RECORDABLE without a release (the record documents the very
    -- condition that blocks the work); NR-18 18.7.2.3 bars STARTING the
    -- excavation, so the schedule gate in the engine enforces it.
);
ALTER TABLE service_order_safety ADD CONSTRAINT uq_safety_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE service_order_safety ADD CONSTRAINT fk_safety_order
    FOREIGN KEY (tenant_id, order_id) REFERENCES service_orders(tenant_id, id);
ALTER TABLE service_order_safety ADD CONSTRAINT uq_safety_order UNIQUE (tenant_id, order_id);

CREATE TABLE IF NOT EXISTS service_order_crew (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(60) NOT NULL,
    qualification_code VARCHAR(100),
    qualification_valid_until DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (char_length(btrim(role)) > 0)
);
ALTER TABLE service_order_crew ADD CONSTRAINT uq_crew_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE service_order_crew ADD CONSTRAINT fk_crew_order
    FOREIGN KEY (tenant_id, order_id) REFERENCES service_orders(tenant_id, id);
ALTER TABLE service_order_crew ADD CONSTRAINT uq_crew_order_user UNIQUE (tenant_id, order_id, user_id);
CREATE INDEX idx_crew_order ON service_order_crew(tenant_id, order_id);

CREATE TABLE IF NOT EXISTS service_order_entry_permits (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    permit_code VARCHAR(60) NOT NULL,
    entry_at TIMESTAMPTZ NOT NULL,
    entered_by UUID,
    supervisor_id UUID,
    closed_at TIMESTAMPTZ,
    retained_until TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (char_length(btrim(permit_code)) > 0),
    CHECK (retained_until >= entry_at + INTERVAL '5 years'),
    CHECK (closed_at IS NULL OR closed_at >= entry_at)
);
ALTER TABLE service_order_entry_permits ADD CONSTRAINT uq_pet_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE service_order_entry_permits ADD CONSTRAINT fk_pet_order
    FOREIGN KEY (tenant_id, order_id) REFERENCES service_orders(tenant_id, id);
ALTER TABLE service_order_entry_permits ADD CONSTRAINT uq_pet_order_code UNIQUE (tenant_id, order_id, permit_code);
CREATE INDEX idx_pet_order ON service_order_entry_permits(tenant_id, order_id);

-- ========================================================================
-- HISTORY FK + NOT NULL: unit_status_history (V3_1) and the social tariff
-- enrollment (V4_2, P1.5). ADR-0039 item 6: no history row without the
-- order that caused it. The FK and the obligation were explicitly deferred
-- to this migration by the P1.5 header comment on V4_2.
-- ========================================================================
-- NOTE ON EXISTING ROWS: a tenant migrated out of order that already holds
-- history/enrollment rows without an order will fail here, loudly, instead
-- of silently keeping orphan history. That is the intended fail-closed
-- behavior: pre-production tenants are re-provisioned; production tenants
-- do not exist yet. The backfill of a real orphan row is a data migration
-- owned by the task that finds it, not an invented order id here.

ALTER TABLE unit_status_history ADD CONSTRAINT fk_ush_service_order
    FOREIGN KEY (tenant_id, service_order_id) REFERENCES service_orders(tenant_id, id);
ALTER TABLE unit_status_history ALTER COLUMN service_order_id SET NOT NULL;
CREATE INDEX idx_ush_service_order ON unit_status_history(tenant_id, service_order_id);

ALTER TABLE social_tariff_enrollments ADD CONSTRAINT fk_social_enroll_order
    FOREIGN KEY (tenant_id, service_order_id) REFERENCES service_orders(tenant_id, id);
ALTER TABLE social_tariff_enrollments ALTER COLUMN service_order_id SET NOT NULL;
CREATE INDEX idx_social_enroll_order ON social_tariff_enrollments(tenant_id, service_order_id);

-- ========================================================================
-- ISOLATION AND AUDIT
-- ========================================================================

SELECT register_tenant_table('service_types');
SELECT register_tenant_table('service_order_close_reasons');
SELECT register_tenant_table('service_order_batches');
SELECT register_tenant_table('service_orders');
SELECT register_tenant_table('service_order_targets');
SELECT register_tenant_table('service_order_effects');
SELECT register_tenant_table('service_order_history');
SELECT register_tenant_table('service_order_notes');
SELECT register_tenant_table('service_order_safety');
SELECT register_tenant_table('service_order_crew');
SELECT register_tenant_table('service_order_entry_permits');

-- Free text that may carry personal data is redacted from the audit trail;
-- the repeatable R__data_classification derives the classification from
-- these rules (coordinates -> LOC, other free text -> ID). The explicit
-- classification rows below cover tenants migrated out of order, where
-- the repeatable does not re-run.
INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('service_order_targets', 'coordinates', 'OMIT', 'service location, which may be a residence'),
    ('service_order_notes', 'body', 'REDACT', 'free text: may contain personal data of the user')
ON CONFLICT (table_name, column_name) DO NOTHING;

INSERT INTO public.data_classification (table_name, column_name, class, reason) VALUES
    ('service_order_targets', 'coordinates', 'LOC', 'service location, which may be a residence'),
    ('service_order_notes', 'body', 'ID', 'free text: may contain personal data of the user')
ON CONFLICT (table_name, column_name) DO NOTHING;
