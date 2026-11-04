-- Service protocol (P1.36, plan v3.0 line 663; ADR-0039 item 7; owner decision 7
-- of 28/10/2026; NR 11, art. 52, §1º).
-- Version: 8.1.0
-- Description: the service protocol (protocolo de atendimento) is the origin of
-- every external request. It is NOT the support ticket of P1.23: support
-- tickets are numbered SUP-AAAA-NNNNNN by the platform sequence (COE-23),
-- while a service protocol is numbered per tenant and year (protocol_year +
-- protocol_number, UNIQUE) by the sequence table below. The term used
-- everywhere for this entity is always "protocolo de atendimento".
--
-- ORDERING TRAP — READ BEFORE RENUMBERING:
-- * This is a TENANT migration: it runs once per tenant schema through
--   TenantMigrator (classpath db/migration/tenant), ordered by version in
--   ONE Flyway history. V8_1 therefore runs BEFORE V9_1 (the OS engine,
--   P1.31) on a fresh install, so service_orders does NOT exist yet here.
-- * The FK from service_orders.protocol_id to service_protocols is
--   therefore CONDITIONAL in this file (to_regclass, same pattern as the
--   layer-B fix 42a0fb5): on an existing tenant, where V9_1 is already
--   applied, the FK is created here. On a fresh install it is skipped
--   here and created by the repeatable R__service_protocol_order_fk.sql,
--   which runs after the versioned migrations and re-runs when introduced:
--   a fully migrated tenant always ends with the FK, whichever path it
--   took. Never make this FK unconditional in V8_1, and never edit V9_1
--   (already applied) to create it.
-- * The FKs to consumer_units (registry V3_1) and customers (registry
--   V3_1) are conditional for the same partial-classpath reason as in
--   42a0fb5: V3_1 sorts before V8_1, so in the full assembly the tables
--   exist and the FKs apply; in a core-only classpath they do not exist
--   and the migration must not break provisioning.
--
-- WHAT THIS MIGRATION DOES NOT DO (other fronts own it):
-- * Full service (atendimento completo), ombudsman flow and the 360 view
--   are ED-35; the art. 54 report built on the close reason is P3.22;
--   the portal screens that open protocols are P3.4 (this migration only
--   supplies the tables, the deadline engine and the service it reuses).
-- * No seeded spec and no seeded close reason: both are tenant policy
--   (precedent: service_types and service_order_close_reasons start empty
--   in V9_1). Seeding a spec here would freeze agency content in the
--   engine, against ADR-0045 item 4.
-- * No document column of any kind on the protocol: a requester who is
--   not a registered customer is recorded by name + minimum contact
--   only (plan line 663). CPF/CNPJ live encrypted on customers (registry)
--   and are never copied here.
-- * The protocol's "point" when there is no consumer unit is the textual
--   location (location_text, same convention as
--   service_order_targets.location_text). A georeferenced GIS point, when
--   the request generates a field order, travels on the order's
--   LOCATION_ONLY target (GIS, P1.26 / ED-35), not on this table.
--
-- STATE MACHINE (enforced by ProtocolStatus in code; the CLOSED
-- close-reason rule and the terminal timestamp are CHECKs below):
--   OPEN -> IN_PROGRESS, CANCELLED
--   IN_PROGRESS -> ANSWER_PENDING, CLOSED, CANCELLED
--   ANSWER_PENDING -> ANSWERED, IN_PROGRESS, CANCELLED
--   ANSWERED -> CLOSED, IN_PROGRESS
--   CLOSED, CANCELLED are terminal. Closing requires a classified reason
--   (close_reason_code, FK to the tenant's catalog); cancelling does not,
--   but may carry one.
-- DUPLICITY (plan line 663; sac.duplicate_handling, ED-35): a duplicate
-- is never refused — it is LINKED. duplicate_of_protocol_id points at
-- the original protocol; agency_reference carries the agency's own
-- reference for the case (regulator / ombudsman channel).

-- ========================================================================
-- REQUEST SPECIFICATION CATALOG (prazo, documentos exigidos, exigência
-- de matrícula / endereço, por tenant)
-- ========================================================================
CREATE TABLE IF NOT EXISTS service_protocol_specs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL CHECK (btrim(code) <> ''),
    description VARCHAR(300) NOT NULL CHECK (btrim(description) <> ''),
    deadline_days INT NOT NULL CHECK (deadline_days > 0),
    required_documents JSONB NOT NULL DEFAULT '[]'::jsonb,
    requires_consumer_unit BOOLEAN NOT NULL DEFAULT FALSE,
    requires_address BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE service_protocol_specs ADD CONSTRAINT uq_protocol_specs_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE service_protocol_specs ADD CONSTRAINT uq_protocol_specs_code UNIQUE (tenant_id, code);
CREATE INDEX idx_protocol_specs_active ON service_protocol_specs(tenant_id, active);

-- ========================================================================
-- CLASSIFIED CLOSE REASONS (obrigatório no encerramento; base do
-- relatório do art. 54 na P3.22)
-- ========================================================================
CREATE TABLE IF NOT EXISTS service_protocol_close_reasons (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL CHECK (btrim(code) <> ''),
    description VARCHAR(300) NOT NULL CHECK (btrim(description) <> ''),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE service_protocol_close_reasons ADD CONSTRAINT uq_protocol_close_reasons_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE service_protocol_close_reasons ADD CONSTRAINT uq_protocol_close_reasons_code UNIQUE (tenant_id, code);

-- ========================================================================
-- PROTOCOLS
-- ========================================================================
CREATE TABLE IF NOT EXISTS service_protocols (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    protocol_year INT NOT NULL CHECK (protocol_year BETWEEN 2000 AND 9999),
    protocol_number INT NOT NULL CHECK (protocol_number > 0),
    channel VARCHAR(20) NOT NULL CHECK (channel IN ('COUNTER', 'PHONE', 'PORTAL', 'APP', 'EMAIL', 'WHATSAPP', 'REGULATOR', 'OMBUDSMAN')),
    spec_id UUID NOT NULL,
    consumer_unit_id UUID,
    location_text VARCHAR(500),
    requester_customer_id UUID,
    requester_name VARCHAR(200),
    requester_contact VARCHAR(200),
    requester_role VARCHAR(20) NOT NULL CHECK (requester_role IN ('HOLDER', 'USER', 'THIRD_PARTY', 'INTERNAL')),
    opened_date DATE NOT NULL,
    original_due_date DATE NOT NULL,
    current_due_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'IN_PROGRESS', 'ANSWER_PENDING', 'ANSWERED', 'CLOSED', 'CANCELLED')),
    close_reason_code VARCHAR(60),
    closed_at TIMESTAMPTZ,
    duplicate_of_protocol_id UUID,
    agency_reference VARCHAR(200),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    CHECK ((requester_customer_id IS NOT NULL)
        OR (NULLIF(btrim(requester_name), '') IS NOT NULL
            AND NULLIF(btrim(requester_contact), '') IS NOT NULL)),
    CHECK (consumer_unit_id IS NOT NULL OR NULLIF(btrim(location_text), '') IS NOT NULL),
    CHECK (status <> 'CLOSED' OR close_reason_code IS NOT NULL),
    CHECK (status NOT IN ('CLOSED', 'CANCELLED') OR closed_at IS NOT NULL),
    CHECK (original_due_date >= opened_date),
    CHECK (current_due_date >= original_due_date),
    CHECK (duplicate_of_protocol_id IS NULL OR duplicate_of_protocol_id <> id)
);
ALTER TABLE service_protocols ADD CONSTRAINT uq_service_protocols_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE service_protocols ADD CONSTRAINT uq_service_protocols_number UNIQUE (tenant_id, protocol_year, protocol_number);
ALTER TABLE service_protocols ADD CONSTRAINT fk_protocols_spec
    FOREIGN KEY (tenant_id, spec_id) REFERENCES service_protocol_specs(tenant_id, id);
ALTER TABLE service_protocols ADD CONSTRAINT fk_protocols_close_reason
    FOREIGN KEY (tenant_id, close_reason_code) REFERENCES service_protocol_close_reasons(tenant_id, code);
ALTER TABLE service_protocols ADD CONSTRAINT fk_protocols_duplicate_of
    FOREIGN KEY (tenant_id, duplicate_of_protocol_id) REFERENCES service_protocols(tenant_id, id);
CREATE INDEX idx_protocols_status ON service_protocols(tenant_id, status);
CREATE INDEX idx_protocols_unit ON service_protocols(tenant_id, consumer_unit_id);
CREATE INDEX idx_protocols_spec ON service_protocols(tenant_id, spec_id);
CREATE INDEX idx_protocols_duplicate ON service_protocols(tenant_id, duplicate_of_protocol_id);

-- ========================================================================
-- PER-TENANT-YEAR NUMBER SEQUENCE (row lock in ProtocolService.open
-- serializes parallel openings; the UNIQUE above is the backstop)
-- ========================================================================
CREATE TABLE IF NOT EXISTS service_protocol_sequences (
    tenant_id UUID NOT NULL,
    protocol_year INT NOT NULL CHECK (protocol_year BETWEEN 2000 AND 9999),
    last_number INT NOT NULL DEFAULT 0 CHECK (last_number >= 0),
    PRIMARY KEY (tenant_id, protocol_year)
);

-- ========================================================================
-- EXTENSIONS (prorrogação como evento: motivo, autor e aviso; o prazo
-- original nunca é apagado — current_due_date muda, original_due_date não)
-- ========================================================================
CREATE TABLE IF NOT EXISTS service_protocol_extensions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    protocol_id UUID NOT NULL,
    previous_due_date DATE NOT NULL,
    new_due_date DATE NOT NULL,
    reason VARCHAR(500) NOT NULL CHECK (btrim(reason) <> ''),
    created_by UUID NOT NULL,
    notice VARCHAR(500) NOT NULL CHECK (btrim(notice) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (new_due_date > previous_due_date)
);
ALTER TABLE service_protocol_extensions ADD CONSTRAINT uq_protocol_extensions_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE service_protocol_extensions ADD CONSTRAINT fk_protocol_extensions_protocol
    FOREIGN KEY (tenant_id, protocol_id) REFERENCES service_protocols(tenant_id, id);
CREATE INDEX idx_protocol_extensions_protocol ON service_protocol_extensions(tenant_id, protocol_id);

-- ========================================================================
-- CONDITIONAL FKs (see header: ordering trap + partial classpaths)
-- ========================================================================
DO $$
BEGIN
    IF to_regclass('consumer_units') IS NOT NULL THEN
        ALTER TABLE service_protocols ADD CONSTRAINT fk_protocols_unit
            FOREIGN KEY (tenant_id, consumer_unit_id) REFERENCES consumer_units(tenant_id, id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('customers') IS NOT NULL THEN
        ALTER TABLE service_protocols ADD CONSTRAINT fk_protocols_requester_customer
            FOREIGN KEY (tenant_id, requester_customer_id) REFERENCES customers(tenant_id, id);
    END IF;
END $$;

-- The service-order FK: applies on upgrade tenants (V9_1 already there).
-- On a fresh install service_orders does not exist yet (V8_1 < V9_1) and
-- this block skips; R__service_protocol_order_fk.sql creates the FK after
-- V9_1. Both blocks are idempotent by constraint name.
DO $$
BEGIN
    IF to_regclass('service_orders') IS NOT NULL THEN
        BEGIN
            ALTER TABLE service_orders ADD CONSTRAINT fk_orders_protocol
                FOREIGN KEY (tenant_id, protocol_id) REFERENCES service_protocols(tenant_id, id);
        EXCEPTION WHEN duplicate_object THEN
            NULL;
        END;
    END IF;
END $$;

-- ========================================================================
-- ISOLATION AND AUDIT (V1_2 pattern, as in V9_1)
-- ========================================================================
SELECT register_tenant_table('service_protocol_specs');
SELECT register_tenant_table('service_protocol_close_reasons');
SELECT register_tenant_table('service_protocols');
SELECT register_tenant_table('service_protocol_sequences');
SELECT register_tenant_table('service_protocol_extensions');

-- Free text that may carry personal data: requester name/contact and the
-- location (which may be a residence) are classified, as in V9_1.
INSERT INTO public.data_classification (table_name, column_name, class, reason) VALUES
    ('service_protocols', 'requester_name', 'ID', 'nome do solicitante nao cadastrado'),
    ('service_protocols', 'requester_contact', 'ID', 'contato do solicitante nao cadastrado'),
    ('service_protocols', 'location_text', 'LOC', 'local do atendimento, que pode ser uma residencia')
ON CONFLICT (table_name, column_name) DO NOTHING;

INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('service_protocols', 'requester_name', 'REDACT', 'free text: may contain personal data of the requester'),
    ('service_protocols', 'requester_contact', 'REDACT', 'free text: may contain personal data of the requester'),
    ('service_protocols', 'location_text', 'OMIT', 'service location, which may be a residence')
ON CONFLICT (table_name, column_name) DO NOTHING;
