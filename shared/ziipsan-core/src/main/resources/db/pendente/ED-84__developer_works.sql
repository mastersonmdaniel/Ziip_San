-- ED-84 — fluxo do empreendedor e obras. SEM numero Flyway: nenhum numero
-- livre foi confirmado na main nesta frente; aplicar so apos confirmacao.
-- Parametros da agencia (classe d) NAO sao semeados aqui: developer.study_fee,
-- developer.reimbursement_rule, works.feasibility_deadline_days,
-- works.network_acceptance_checklist e developer.deadline_days.<etapa>
-- nascem da configuracao do tenant/agencia — sem valor, o servico recusa.

CREATE TABLE IF NOT EXISTS developer_case (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    protocol_id UUID NOT NULL,               -- protocolo da P1.36 (nascimento)
    developer_name TEXT NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('LOTEAMENTO','CONDOMINIO')),
    planned_units INT NOT NULL CHECK (planned_units > 0),
    unit_lot_status TEXT NOT NULL DEFAULT 'PROVISIONAL',  -- ADR-0039 item 3
    stage TEXT NOT NULL,
    opinion_network TEXT, opinion_pressure_kpa NUMERIC(12,3), opinion_diameter_mm NUMERIC(12,3),
    feasibility_due_date DATE, feasibility_done_date DATE, feasibility_late BOOLEAN NOT NULL DEFAULT FALSE,
    project_ref TEXT, approved_feature_ids JSONB NOT NULL DEFAULT '[]',
    asbuilt_package_id UUID, asbuilt_version INT,
    acceptance_approved BOOLEAN NOT NULL DEFAULT FALSE,
    checklist_results JSONB NOT NULL DEFAULT '{}',
    eligible_network_cost NUMERIC(14,2),
    stage_due_dates JSONB NOT NULL DEFAULT '{}'  -- prazos por etapa aplicados (developer.deadline_days.<etapa>)
);

-- Visitas de fiscalizacao da obra do empreendedor (SupervisionVisit) — D10.
CREATE TABLE IF NOT EXISTS developer_supervision_visit (
    case_id UUID NOT NULL REFERENCES developer_case(id),
    visit_date DATE NOT NULL,
    inspector TEXT NOT NULL,
    conforming BOOLEAN NOT NULL,
    findings TEXT NOT NULL,
    PRIMARY KEY (case_id, visit_date, inspector)
);

CREATE TABLE IF NOT EXISTS developer_fee_charge (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES developer_case(id),
    service_type TEXT NOT NULL CHECK (service_type LIKE 'DEVELOPMENT\_%'),
    amount NUMERIC(14,2) NOT NULL,           -- taxa lida do catalogo da ED-34
    paid BOOLEAN NOT NULL DEFAULT FALSE,
    paid_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS developer_transfer_term (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES developer_case(id),
    version INT NOT NULL CHECK (version > 0),
    feature_ids JSONB NOT NULL,              -- == feicoes aprovadas, contidas no as-built (ED-53)
    signed_by TEXT NOT NULL, signed_at TIMESTAMPTZ NOT NULL,
    UNIQUE (case_id, version)
);

CREATE TABLE IF NOT EXISTS developer_incorporated_asset (
    feature_id TEXT NOT NULL,
    transfer_term_id UUID NOT NULL REFERENCES developer_transfer_term(id),
    incorporated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (feature_id, transfer_term_id)
);

CREATE TABLE IF NOT EXISTS provider_work (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL, title TEXT NOT NULL,
    planned_start DATE NOT NULL, planned_end DATE NOT NULL,
    budget NUMERIC(14,2) NOT NULL CHECK (budget > 0),
    milestones JSONB NOT NULL DEFAULT '[]',
    contractor_measurement_id UUID,          -- medicao da ED-37
    measured_amount NUMERIC(14,2)
);

-- Visitas de fiscalizacao das obras do prestador (SupervisionVisit) — D10.
CREATE TABLE IF NOT EXISTS provider_work_supervision_visit (
    work_id UUID NOT NULL REFERENCES provider_work(id),
    visit_date DATE NOT NULL,
    inspector TEXT NOT NULL,
    conforming BOOLEAN NOT NULL,
    findings TEXT NOT NULL,
    PRIMARY KEY (work_id, visit_date, inspector)
);

CREATE TABLE IF NOT EXISTS pavement_restoration (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    repair_order_id UUID NOT NULL,           -- OS de reparo da qual deriva
    pavement_type TEXT NOT NULL,             -- SG-138, SG-139
    area_m2 NUMERIC(12,2) NOT NULL CHECK (area_m2 > 0),
    contractor_measurement_id UUID           -- medicao da ED-37
);

CREATE TABLE IF NOT EXISTS field_order_attachment (
    order_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,                 -- D10: anexo pertence a um tenant
    -- licenca e consulta sao obrigatorias no registro (D10): colunas da licenca/consulta sao NOT NULL na pratica do servico
    license_number TEXT, license_authority TEXT, license_valid_from DATE, license_valid_to DATE,
    interference_consulted_on DATE, interference_services JSONB,
    interference_note TEXT,
    national_integration_confirmed BOOLEAN NOT NULL DEFAULT FALSE
        CHECK (national_integration_confirmed = FALSE)  -- OPE-27: nao confirmada
);
