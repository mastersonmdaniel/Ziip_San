-- LGPD Module Migration for Tenant Schema
-- Version: 2.5.0
-- Description: Create LGPD tables (Consent, Data Subject Requests, DPIA, Retention Schedule)
--
-- As quatro entidades (ConsentRecord, DataSubjectRequest, DpiaRecord, RetentionSchedule) existiam
-- sem nenhuma migration: o primeiro acesso falharia com "relation lgpd_consent does not exist".
--
-- RLS, policy de tenant, trigger de auditoria e as colunas version/tenant_schema sao aplicados por
-- register_tenant_table() (V1_2) no fim do arquivo.

-- ========================================================================
-- CONSENT (Art. 7 e 8 da LGPD)
-- ========================================================================

CREATE TABLE IF NOT EXISTS lgpd_consent (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    subject_type VARCHAR(30) NOT NULL,
    purpose VARCHAR(200) NOT NULL,
    purpose_category VARCHAR(50),
    legal_basis VARCHAR(30) NOT NULL CHECK (legal_basis IN (
        'CONSENT', 'CONTRACT', 'LEGAL_OBLIGATION', 'VITAL_INTEREST', 'PUBLIC_TASK',
        'LEGITIMATE_INTEREST', 'CREDIT_PROTECTION'
    )),
    status VARCHAR(20) NOT NULL DEFAULT 'GRANTED' CHECK (status IN (
        'GRANTED', 'REVOKED', 'EXPIRED', 'PENDING', 'WITHDRAWN'
    )),
    granted_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    consent_text TEXT,
    consent_hash VARCHAR(64),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    geolocation JSONB,
    withdrawal_method VARCHAR(50),
    withdrawal_reason TEXT,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE INDEX idx_consent_tenant_subject ON lgpd_consent(tenant_id, subject_id);
CREATE INDEX idx_consent_purpose ON lgpd_consent(purpose);
CREATE INDEX idx_consent_status ON lgpd_consent(status);
CREATE INDEX idx_consent_expires ON lgpd_consent(expires_at);

-- ========================================================================
-- DATA SUBJECT REQUESTS (Art. 18 da LGPD)
-- ========================================================================

CREATE TABLE IF NOT EXISTS lgpd_data_subject_request (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    subject_type VARCHAR(30),
    request_type VARCHAR(30) NOT NULL CHECK (request_type IN (
        'CONFIRMATION', 'ACCESS', 'CORRECTION', 'ANONYMIZATION', 'BLOCKING', 'DELETION',
        'PORTABILITY', 'INFORMATION', 'REVOCATION', 'OPPOSITION'
    )),
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED' CHECK (status IN (
        'RECEIVED', 'VERIFICATION_PENDING', 'VERIFIED', 'IN_PROGRESS', 'COMPLETED',
        'DENIED', 'EXTENDED', 'EXPIRED', 'CANCELLED'
    )),
    description TEXT,
    requested_data_categories JSONB,
    verification_method VARCHAR(50),
    verified_at TIMESTAMPTZ,
    verified_by UUID,
    deadline_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    completed_by UUID,
    response_summary TEXT,
    response_data JSONB,
    denial_reason TEXT,
    extension_reason TEXT,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE INDEX idx_dsr_tenant_subject ON lgpd_data_subject_request(tenant_id, subject_id);
CREATE INDEX idx_dsr_type_status ON lgpd_data_subject_request(request_type, status);
CREATE INDEX idx_dsr_deadline ON lgpd_data_subject_request(deadline_at);

-- ========================================================================
-- DPIA / RIPD (Relatorio de Impacto a Protecao de Dados Pessoais)
-- ========================================================================

CREATE TABLE IF NOT EXISTS lgpd_dpia (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    processing_activity VARCHAR(200) NOT NULL,
    processing_description TEXT,
    data_categories JSONB,
    special_categories JSONB,
    data_subjects JSONB,
    recipients JSONB,
    third_country_transfers JSONB,
    retention_period VARCHAR(100),
    security_measures JSONB,
    risk_level VARCHAR(20) CHECK (risk_level IN ('NEGLIGIBLE', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    risk_score NUMERIC(5,2),
    likelihood VARCHAR(20),
    severity VARCHAR(20),
    mitigation_measures JSONB,
    residual_risk VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN (
        'DRAFT', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'EXPIRED', 'ARCHIVED'
    )),
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    review_date TIMESTAMPTZ,
    dpo_signature JSONB,
    report_pdf BYTEA,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE INDEX idx_dpia_tenant_status ON lgpd_dpia(tenant_id, status);
CREATE INDEX idx_dpia_processing ON lgpd_dpia(processing_activity);
CREATE INDEX idx_dpia_risk ON lgpd_dpia(risk_level);

-- ========================================================================
-- RETENTION SCHEDULE (Art. 15 e 16 da LGPD)
-- ========================================================================
-- Prazos de retencao NAO sao semeados: o motor le o que o controlador declara e nunca inventa
-- prazo (ver RetentionSchedule). Um prazo padrao errado e passivo juridico.

CREATE TABLE IF NOT EXISTS lgpd_retention_schedule (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    category VARCHAR(200) NOT NULL,
    retention_days INT NOT NULL,
    retention_anchor VARCHAR(50),
    legal_hold_reason TEXT,
    disposal_action VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_run_at TIMESTAMPTZ,
    next_run_at TIMESTAMPTZ,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE INDEX idx_retention_tenant_entity ON lgpd_retention_schedule(tenant_id, entity_type);
CREATE INDEX idx_retention_next_run ON lgpd_retention_schedule(next_run_at);

-- ========================================================================
-- ISOLAMENTO E AUDITORIA
-- ========================================================================

SELECT register_tenant_table('lgpd_consent');
SELECT register_tenant_table('lgpd_data_subject_request');
SELECT register_tenant_table('lgpd_dpia');
SELECT register_tenant_table('lgpd_retention_schedule');
