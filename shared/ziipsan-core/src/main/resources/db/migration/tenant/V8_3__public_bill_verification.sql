-- Public bill authenticity verification, fraud reports and official channels
-- (P3.35; PLT-11; ADR-0043 item 4).
-- Version: 8.3.0
-- Description: the public verification code of a bill, the verification
-- queries (recorded WITHOUT any holder data) and the fraud reports that
-- feed the Central Security tab.
-- The verification code is NEVER stored in clear: only its blind index
-- (FieldProtector context portal.bill_verification_code) lives here.
-- The receiver is the TENANT's legal identity (public supplier data,
-- CDC art. 42-A, already printed on the bill): receiver_name/receiver_tax_id
-- are the tenant's, NEVER the holder's. There is deliberately NO column
-- for holder name, holder document, address, consumer unit or consumption
-- in any table of this migration.
-- FK to bills is conditional for the same partial-classpath reason as
-- V8_1/V8_2 (billing lives in another module).

CREATE TABLE IF NOT EXISTS bill_verification_codes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    bill_id UUID NOT NULL,
    bill_version_no INTEGER NOT NULL CHECK (bill_version_no >= 1),
    code_index VARCHAR(128) NOT NULL,
    document_hash CHAR(64) NOT NULL CHECK (document_hash ~ '^[0-9a-f]{64}$'),
    amount NUMERIC(14, 2) NOT NULL,
    due_date DATE NOT NULL,
    reference_month DATE NOT NULL,
    receiver_name VARCHAR(200) NOT NULL CHECK (btrim(receiver_name) <> ''),
    receiver_tax_id VARCHAR(20) NOT NULL CHECK (btrim(receiver_tax_id) <> ''),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL)),
    CHECK (reference_month = date_trunc('month', reference_month)::date)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_bill_verification_codes_index
    ON bill_verification_codes (tenant_id, code_index);
CREATE UNIQUE INDEX IF NOT EXISTS uq_bill_verification_codes_bill_version
    ON bill_verification_codes (tenant_id, bill_id, bill_version_no);
CREATE INDEX IF NOT EXISTS ix_bill_verification_codes_bill
    ON bill_verification_codes (tenant_id, bill_id, status);

CREATE TABLE IF NOT EXISTS bill_verification_queries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code_index VARCHAR(128),
    source_ip VARCHAR(64) NOT NULL,
    result VARCHAR(20) NOT NULL CHECK (result IN ('AUTHENTIC', 'NOT_AUTHENTIC', 'RATE_LIMITED')),
    queried_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_bill_verification_queries_window
    ON bill_verification_queries (tenant_id, queried_at);
CREATE INDEX IF NOT EXISTS ix_bill_verification_queries_origin
    ON bill_verification_queries (tenant_id, source_ip, queried_at);
CREATE INDEX IF NOT EXISTS ix_bill_verification_queries_code
    ON bill_verification_queries (tenant_id, code_index, queried_at);

CREATE TABLE IF NOT EXISTS fraud_reports (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL CHECK (channel IN ('LINK', 'MESSAGE', 'EMAIL', 'PHONE_CALL', 'SOCIAL', 'OTHER')),
    reported_value VARCHAR(500) NOT NULL CHECK (btrim(reported_value) <> ''),
    description VARCHAR(2000) NOT NULL CHECK (btrim(description) <> ''),
    reporter_contact VARCHAR(200),
    source_ip VARCHAR(64) NOT NULL,
    central_alerted BOOLEAN NOT NULL DEFAULT FALSE,
    reported_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_fraud_reports_tenant_time
    ON fraud_reports (tenant_id, reported_at);

-- Published snapshot of the tenant's official channels: the source of truth
-- is the portal.official_channels parameter (classe d) derived from
-- branding.contact; this table is the publication record of what was
-- validated against branding.contact and shown on bill, portal and e-mails.
CREATE TABLE IF NOT EXISTS published_official_channels (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    channel_type VARCHAR(20) NOT NULL CHECK (channel_type IN ('WEBSITE', 'PORTAL', 'EMAIL', 'PHONE', 'WHATSAPP', 'APP')),
    label VARCHAR(120) NOT NULL CHECK (btrim(label) <> ''),
    channel_value VARCHAR(500) NOT NULL CHECK (btrim(channel_value) <> ''),
    published_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_published_official_channels_tenant
    ON published_official_channels (tenant_id, published_at);
