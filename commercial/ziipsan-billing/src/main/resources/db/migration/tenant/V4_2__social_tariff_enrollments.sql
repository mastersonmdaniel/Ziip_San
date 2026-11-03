-- Social tariff enrollments (P1.5, plan v3.0 line 630; migration reserved in plan section 3).
-- Version: 4.2.0
-- Description: satellite of the social tariff (R3 v1): enrollment per unit, official-list
-- import base, and end-reason catalog. Depends on: V4_1 (billing) and V3_1 (consumer_units,
-- customers).
--
-- PRIVACY BY DESIGN: no table here stores a CPF, a NIS, a family code, or an income in
-- clear text. The only personal-data columns are blind indexes (HMAC-SHA-256 hashes, the
-- same pattern as customers.document_hash in V3_1): family_code_hash and
-- person_document_hash. Matching against the official list happens on hashes; the plain
-- values never enter the database. Income is NOT stored at all: the official list already
-- states eligibility (income at or below half a minimum wage per capita, Lei 14.898/2024),
-- so the import records the eligibility basis and the CadUnico update date, never the income.
--
-- NO PAYMENT-STATUS FIELD (class a, Lei 14.898/2024): granting the benefit must never
-- depend on payment standing. There is deliberately no column for it on any table here;
-- the domain rule is asserted by a test (SocialEnrollmentTest).
--
-- NO SEEDED DATA. The end-reason catalog starts empty: reasons are policy of the tenant
-- and of the regulator, and an invented reason would be treated as real policy.
--
-- service_order_id is created here WITHOUT a foreign key and WITHOUT NOT NULL, exactly
-- as the plan states: the FK and the obligation are added by P1.31. A column added now
-- with a FK to a service_orders shape that P1.31 still defines would lock the wrong shape.
--
-- The CadUnico age limit (social.cadunico_max_age_months, 24) is NOT a CHECK: PostgreSQL
-- CHECK expressions must be immutable and cannot reference the current date. The limit is
-- enforced by SocialEligibility in the domain and asserted by a test (a CadUnico update
-- 25 months old is ineligible). The same applies to the loss-notice minimum (3 months)
-- and the grace period (3 months): they are date arithmetic in SocialEnrollmentLifecycle.
--
-- The combination lock between social.base_import / social.list_source and
-- reg.legal_nature (direct CadUnico assignment only reaches a public-law body,
-- Decreto 11.016/2022) is a tenant-parameter coherence rule, not a row CHECK: the legal
-- nature lives in tenant configuration, not in these tables. It is enforced where
-- parameters are validated (platform), and is recorded as a pendency in ADR-0047's
-- companion note and in the P1.5 report; it is NOT silently approved here.

-- ========================================================================
-- END-REASON CATALOG (tenant policy; starts empty)
-- ========================================================================

CREATE TABLE IF NOT EXISTS social_tariff_end_reasons (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    description VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE social_tariff_end_reasons ADD CONSTRAINT uq_social_end_reasons_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE social_tariff_end_reasons ADD CONSTRAINT uq_social_end_reasons_code UNIQUE (tenant_id, code);

-- ========================================================================
-- ENROLLMENT: one active enrollment per family, one per unit
-- ========================================================================
-- status: ACTIVE (benefit applies), LOSS_NOTICE (loss warned on the bill, benefit still
-- applies until the notice period ends), GRACE (no longer eligible, benefit kept for the
-- grace period), ENDED (benefit over, end reason recorded).
-- enrollment_source: OFFICIAL_LIST (automatic classification from the official list, no
-- request needed) or MANUAL_DOCUMENTS (the user filed the documents required by
-- Lei 14.898/2024 art. 5; refusing to keep such a user out of the tariff is improper
-- charging, art. 5 section 2).
-- eligibility_basis: CADUNICO, BPC, LOCAL_PROGRAM.
-- cadunico_updated_on is mandatory exactly when the basis is CADUNICO; BPC and local
-- programs do not carry a CadUnico update date.

CREATE TABLE IF NOT EXISTS social_tariff_enrollments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    holder_customer_id UUID NOT NULL,
    family_code_hash VARCHAR(64) NOT NULL,
    eligibility_basis VARCHAR(20) NOT NULL CHECK (eligibility_basis IN ('CADUNICO', 'BPC', 'LOCAL_PROGRAM')),
    enrollment_source VARCHAR(20) NOT NULL CHECK (enrollment_source IN ('OFFICIAL_LIST', 'MANUAL_DOCUMENTS')),
    cadunico_updated_on DATE,
    valid_from DATE NOT NULL,
    valid_to DATE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'LOSS_NOTICE', 'GRACE', 'ENDED')),
    loss_notice_started_on DATE,
    grace_started_on DATE,
    end_reason_code VARCHAR(40),
    legal_basis VARCHAR(200) NOT NULL,
    social_economies INT NOT NULL CHECK (social_economies >= 1),
    service_order_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CHECK ((eligibility_basis = 'CADUNICO') = (cadunico_updated_on IS NOT NULL)),
    CHECK ((status = 'LOSS_NOTICE') = (loss_notice_started_on IS NOT NULL)),
    CHECK ((status = 'GRACE') = (grace_started_on IS NOT NULL)),
    CHECK ((status = 'ENDED') = (end_reason_code IS NOT NULL))
);
ALTER TABLE social_tariff_enrollments ADD CONSTRAINT uq_social_enrollments_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE social_tariff_enrollments ADD CONSTRAINT fk_social_enroll_unit
    FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
ALTER TABLE social_tariff_enrollments ADD CONSTRAINT fk_social_enroll_holder
    FOREIGN KEY (tenant_id, holder_customer_id) REFERENCES customers(tenant_id, id);
ALTER TABLE social_tariff_enrollments ADD CONSTRAINT fk_social_enroll_end_reason
    FOREIGN KEY (tenant_id, end_reason_code) REFERENCES social_tariff_end_reasons(tenant_id, code);
-- One economy per family: at most one enrollment in ACTIVE, LOSS_NOTICE or GRACE per
-- family hash. ENDED enrollments do not block a later enrollment of the same family.
CREATE UNIQUE INDEX idx_social_enroll_family_active ON social_tariff_enrollments(tenant_id, family_code_hash)
    WHERE status IN ('ACTIVE', 'LOSS_NOTICE', 'GRACE');
-- At most one active enrollment per unit, for the same reason.
CREATE UNIQUE INDEX idx_social_enroll_unit_active ON social_tariff_enrollments(tenant_id, unit_id)
    WHERE status IN ('ACTIVE', 'LOSS_NOTICE', 'GRACE');
CREATE INDEX idx_social_enroll_unit ON social_tariff_enrollments(tenant_id, unit_id);
CREATE INDEX idx_social_enroll_status ON social_tariff_enrollments(tenant_id, status);

-- ========================================================================
-- OFFICIAL-LIST IMPORT BASE (social.base_import / social.list_source)
-- ========================================================================
-- list_source: REGULATOR_LIST (list handed over by the regulator), MUNICIPAL_BASE
-- (municipal base), CONECTA_API (Conecta API). Class c in the plan: the source follows
-- the provider's legal nature; see the header note about the combination lock.
-- purge_at: retention deadline of the imported lot (ret.social_base_days); NULL until
-- the tenant sets the retention parameter, never a default invented here.

CREATE TABLE IF NOT EXISTS social_base_imports (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    list_source VARCHAR(20) NOT NULL CHECK (list_source IN ('REGULATOR_LIST', 'MUNICIPAL_BASE', 'CONECTA_API')),
    layout_version VARCHAR(50) NOT NULL,
    received_on DATE NOT NULL,
    record_count INT NOT NULL CHECK (record_count >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('RECEIVED', 'VALIDATED', 'APPLIED', 'REJECTED')),
    purge_at DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE social_base_imports ADD CONSTRAINT uq_social_imports_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_social_imports_source ON social_base_imports(tenant_id, list_source, received_on DESC);

-- One row per listed person (holder or family member), identified only by blind indexes.
-- match_status: PENDING (not yet matched), MATCHED (a unit was found), UNMATCHED
-- (no unit found: the active-search list, social.active_search, is exactly the set of
-- UNMATCHED records of an APPLIED import).
CREATE TABLE IF NOT EXISTS social_base_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    import_id UUID NOT NULL,
    family_code_hash VARCHAR(64) NOT NULL,
    person_document_hash VARCHAR(64) NOT NULL,
    person_role VARCHAR(10) NOT NULL CHECK (person_role IN ('HOLDER', 'MEMBER')),
    eligibility_basis VARCHAR(20) NOT NULL CHECK (eligibility_basis IN ('CADUNICO', 'BPC', 'LOCAL_PROGRAM')),
    cadunico_updated_on DATE,
    matched_unit_id UUID,
    matched_enrollment_id UUID,
    match_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (match_status IN ('PENDING', 'MATCHED', 'UNMATCHED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK ((eligibility_basis = 'CADUNICO') = (cadunico_updated_on IS NOT NULL)),
    CHECK ((match_status = 'MATCHED') = (matched_unit_id IS NOT NULL)),
    CHECK ((matched_enrollment_id IS NULL) OR (matched_unit_id IS NOT NULL))
);
ALTER TABLE social_base_records ADD CONSTRAINT uq_social_records_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE social_base_records ADD CONSTRAINT fk_social_record_import
    FOREIGN KEY (tenant_id, import_id) REFERENCES social_base_imports(tenant_id, id);
ALTER TABLE social_base_records ADD CONSTRAINT fk_social_record_unit
    FOREIGN KEY (tenant_id, matched_unit_id) REFERENCES consumer_units(tenant_id, id);
ALTER TABLE social_base_records ADD CONSTRAINT fk_social_record_enrollment
    FOREIGN KEY (tenant_id, matched_enrollment_id) REFERENCES social_tariff_enrollments(tenant_id, id);
CREATE UNIQUE INDEX idx_social_record_person ON social_base_records(tenant_id, import_id, family_code_hash, person_document_hash, person_role);
CREATE INDEX idx_social_record_unmatched ON social_base_records(tenant_id, import_id)
    WHERE match_status = 'UNMATCHED';
CREATE INDEX idx_social_record_family ON social_base_records(tenant_id, family_code_hash);

-- ========================================================================
-- ISOLATION AND AUDIT
-- ========================================================================

SELECT register_tenant_table('social_tariff_end_reasons');
SELECT register_tenant_table('social_tariff_enrollments');
SELECT register_tenant_table('social_base_imports');
SELECT register_tenant_table('social_base_records');

-- Blind indexes must never appear in the audit trail: they would let records be linked.
INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('social_tariff_enrollments', 'family_code_hash', 'OMIT', 'blind index of the family code: links records'),
    ('social_base_records', 'family_code_hash', 'OMIT', 'blind index of the family code: links records'),
    ('social_base_records', 'person_document_hash', 'OMIT', 'blind index of the document: links records')
ON CONFLICT (table_name, column_name) DO NOTHING;
