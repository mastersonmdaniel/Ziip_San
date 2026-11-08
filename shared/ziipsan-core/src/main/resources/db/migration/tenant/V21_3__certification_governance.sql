-- V21_3 - P3.31 (plano v3.0 secao 3, faixa certification, reservada a P3.31).
-- Riscos, objetivos, auditoria interna, analise critica, competencia, fornecedor e requisitos legais.
-- NENHUM dado semeado: criterio, fornecedor e requisito sao declarados pelo tenant ou pelo pacote sanitation.
-- Correcoes da revisao cruzada (CORRECAO-P3.31.md):
--   D1: rpt_cert_suppliers agrega a avaliacao mais recente de cert_supplier_evaluations (nada de NULL constante).
--   D2: rpt_cert_pending cobre os 6 tipos do painel (os mesmos de GovernanceService.pendingItems).
--   D7: UNIQUE (tenant_id, id) em todas as tabelas (padrao da V21_1) + FKs intra-modulo compostas com tenant;
--       cert_legal_requirements ganha conformity_evaluated_on (a data da avaliacao nao e descartada);
--       cert_risks ganha occurrence_id com FK para cert_occurrence da P2.25 (V21_2).
--   D9: CHECKs casando com o Java — contingencia em branco nao passa (btrim), apetite com teto da escala
--       declarada e probability/impact >= 1. O TETO de probability/impact depende do criterio declarado
--       pelo tenant (cert_risk_criteria) e por isso fica no servico, nao num CHECK estatico.

CREATE TABLE IF NOT EXISTS cert_risk_criteria (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL, max_probability INT NOT NULL CHECK (max_probability>=1), max_impact INT NOT NULL CHECK (max_impact>=1), appetite_threshold INT NOT NULL CHECK (appetite_threshold>=1 AND appetite_threshold <= max_probability*max_impact), description TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0, tenant_schema VARCHAR(100), created_by UUID, updated_by UUID,
    CONSTRAINT uq_cert_risk_criteria_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('cert_risk_criteria', true, false);

CREATE TABLE IF NOT EXISTS cert_risks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL, title VARCHAR(200) NOT NULL, kind VARCHAR(20) NOT NULL CHECK (kind IN ('RISK','OPPORTUNITY')), category VARCHAR(30) NOT NULL CHECK (category IN ('OPERATIONAL','CYBER','CLIMATE_RESILIENCE','PROVIDER_CONTINUITY','GENERAL_DECLARED')), probability INT NOT NULL CHECK (probability>=1), impact INT NOT NULL CHECK (impact>=1), contingency_plan TEXT, interruption_ref VARCHAR(200), occurrence_id UUID, review_due DATE NOT NULL, CHECK (category NOT IN ('OPERATIONAL','CYBER','PROVIDER_CONTINUITY') OR (contingency_plan IS NOT NULL AND char_length(btrim(contingency_plan)) > 0)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0, tenant_schema VARCHAR(100), created_by UUID, updated_by UUID,
    CONSTRAINT uq_cert_risks_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_cert_risks_occurrence FOREIGN KEY (tenant_id, occurrence_id) REFERENCES cert_occurrence (tenant_id, id)
);
SELECT register_tenant_table('cert_risks', true, false);

CREATE TABLE IF NOT EXISTS cert_objectives (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL, title VARCHAR(200) NOT NULL, indicator_code VARCHAR(60) NOT NULL, due_date DATE NOT NULL, owner_area VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0, tenant_schema VARCHAR(100), created_by UUID, updated_by UUID,
    CONSTRAINT uq_cert_objectives_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('cert_objectives', true, false);

CREATE TABLE IF NOT EXISTS cert_suppliers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL, legal_name VARCHAR(200) NOT NULL, document_ref VARCHAR(80), supplier_type VARCHAR(30) NOT NULL CHECK (supplier_type IN ('MATERIAL','SERVICE','EXTERNAL_LABORATORY','CONTRACTOR')), contact_ref VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0, tenant_schema VARCHAR(100), created_by UUID, updated_by UUID,
    CONSTRAINT uq_cert_suppliers_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('cert_suppliers', true, false);

CREATE TABLE IF NOT EXISTS cert_supplier_evaluations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL, supplier_id UUID NOT NULL, evaluated_on DATE NOT NULL, score INT NOT NULL CHECK (score BETWEEN 0 AND 100), criteria_declared TEXT NOT NULL, next_evaluation_due DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0, tenant_schema VARCHAR(100), created_by UUID, updated_by UUID,
    CONSTRAINT uq_cert_supplier_evaluations_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_cert_supplier_evaluations_supplier FOREIGN KEY (tenant_id, supplier_id) REFERENCES cert_suppliers (tenant_id, id)
);
SELECT register_tenant_table('cert_supplier_evaluations', true, false);

CREATE TABLE IF NOT EXISTS cert_competences (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL, person_id UUID NOT NULL, competence_code VARCHAR(60) NOT NULL, service_order_types JSONB NOT NULL DEFAULT '[]'::jsonb, valid_until DATE NOT NULL, evidence_ref VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0, tenant_schema VARCHAR(100), created_by UUID, updated_by UUID,
    CONSTRAINT uq_cert_competences_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('cert_competences', true, false);

CREATE TABLE IF NOT EXISTS cert_legal_requirements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL, title VARCHAR(300) NOT NULL, source_ref TEXT NOT NULL, origin VARCHAR(30) NOT NULL CHECK (origin IN ('TENANT_DECLARED','SANITATION_FACTORY_CATALOG')), evaluation_due DATE NOT NULL, conformity VARCHAR(30) NOT NULL DEFAULT 'UNDER_EVALUATION' CHECK (conformity IN ('UNDER_EVALUATION','CONFORMING','NON_CONFORMING','NOT_APPLICABLE_DECLARED')), conformity_evaluated_on DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0, tenant_schema VARCHAR(100), created_by UUID, updated_by UUID,
    CONSTRAINT uq_cert_legal_requirements_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('cert_legal_requirements', true, false);

CREATE TABLE IF NOT EXISTS cert_audit_programs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL, title VARCHAR(200) NOT NULL, planned_for DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0, tenant_schema VARCHAR(100), created_by UUID, updated_by UUID,
    CONSTRAINT uq_cert_audit_programs_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('cert_audit_programs', true, false);

CREATE TABLE IF NOT EXISTS cert_audit_findings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL, program_id UUID NOT NULL, description TEXT NOT NULL, severity VARCHAR(20) NOT NULL CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')), due_date DATE NOT NULL, closed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0, tenant_schema VARCHAR(100), created_by UUID, updated_by UUID,
    CONSTRAINT uq_cert_audit_findings_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_cert_audit_findings_program FOREIGN KEY (tenant_id, program_id) REFERENCES cert_audit_programs (tenant_id, id)
);
SELECT register_tenant_table('cert_audit_findings', true, false);

CREATE TABLE IF NOT EXISTS cert_management_reviews (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL, held_on DATE NOT NULL, decisions JSONB NOT NULL DEFAULT '[]'::jsonb, minutes_ref VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0, tenant_schema VARCHAR(100), created_by UUID, updated_by UUID,
    CONSTRAINT uq_cert_management_reviews_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('cert_management_reviews', true, false);

CREATE TABLE IF NOT EXISTS cert_governance_audit_trail (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL, action VARCHAR(60) NOT NULL, detail JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0, tenant_schema VARCHAR(100), created_by UUID, updated_by UUID,
    CONSTRAINT uq_cert_governance_audit_trail_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('cert_governance_audit_trail', true, false);

CREATE OR REPLACE VIEW rpt_cert_risks WITH (security_invoker = true) AS
  SELECT tenant_id, title, kind, category, probability, impact, (probability*impact) AS score, review_due FROM cert_risks;
-- D2: os 6 tipos do painel — RISK_REVIEW, OBJECTIVE, COMPETENCE_EXPIRY, LEGAL_EVALUATION,
-- AUDIT_FINDING (so abertos) e SUPPLIER_EVALUATION (item_ref = id da avaliacao, como no Java).
CREATE OR REPLACE VIEW rpt_cert_pending WITH (security_invoker = true) AS
  SELECT tenant_id, 'RISK_REVIEW' AS item_type, id AS item_ref, review_due AS due_date, title AS detail FROM cert_risks
  UNION ALL SELECT tenant_id, 'OBJECTIVE', id, due_date, title FROM cert_objectives
  UNION ALL SELECT tenant_id, 'COMPETENCE_EXPIRY', id, valid_until, competence_code FROM cert_competences
  UNION ALL SELECT tenant_id, 'LEGAL_EVALUATION', id, evaluation_due, title FROM cert_legal_requirements
  UNION ALL SELECT tenant_id, 'AUDIT_FINDING', id, due_date, description FROM cert_audit_findings WHERE NOT closed
  UNION ALL SELECT e.tenant_id, 'SUPPLIER_EVALUATION', e.id, e.next_evaluation_due, s.legal_name FROM cert_supplier_evaluations e JOIN cert_suppliers s ON s.tenant_id = e.tenant_id AND s.id = e.supplier_id;
-- D1: last_score/next_evaluation_due vem da avaliacao mais recente (evaluated_on maximo) de cada fornecedor.
CREATE OR REPLACE VIEW rpt_cert_suppliers WITH (security_invoker = true) AS
  SELECT s.tenant_id, s.legal_name, s.supplier_type, e.score AS last_score, e.next_evaluation_due AS next_evaluation_due FROM cert_suppliers s
  LEFT JOIN LATERAL (SELECT ev.score, ev.next_evaluation_due FROM cert_supplier_evaluations ev WHERE ev.tenant_id = s.tenant_id AND ev.supplier_id = s.id ORDER BY ev.evaluated_on DESC, ev.created_at DESC LIMIT 1) e ON true;
CREATE OR REPLACE VIEW rpt_cert_legal_conformity WITH (security_invoker = true) AS
  SELECT tenant_id, title, origin, conformity, evaluation_due FROM cert_legal_requirements;
