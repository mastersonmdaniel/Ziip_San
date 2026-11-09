-- ED-51 — Apoio a ISO/IEC 17025 (18g-E-LIMS17025; ADR-0028 (a); ADR-0041)
-- SQL PENDENTE de numeracao Flyway (regra da cascata: nenhum numero
-- inventado). Na integracao: atribuir o proximo slot livre da faixa
-- certification (pacotes por norma sobre o nucleo das V21_1 a V21_3,
-- como a ED-69), renomear para V<slot>__lims17025.sql e conferir as
-- FKs contra cert_suppliers (V21_3), quality_samples (SQL pendente da
-- ED-50) e cert_occurrence (V21_2, vinculo opcional do trabalho nao
-- conforme).
--
-- REGRA DURA: este SQL NAO semeia NENHUM limite ambiental, NENHUM
-- valor de material de referencia, NENHUM metodo/parametro de escopo
-- e NENHUMA chave de modelo de laudo. Escopo, requisitos ambientais,
-- orcamentos de incerteza e validacoes sao DADO DECLARADO do tenant/
-- laboratorio. A chave lims.lab_report_template_key [SEM FONTE] vive
-- no registro de parametros do tenant, nao aqui.
--
-- SEM DELETE: desligar a norma nao apaga registro (ADR-0041;
-- cert.deactivation_keeps_records travado em true). Nenhum comando
-- deste arquivo apaga linha de tabela alguma. Historicos (calibracao,
-- versoes de laudo) sao por INSERT de registro novo.

-- ---------------------------------------------------------------------
-- Laboratorio e escopo declarado (metodo x parametro, acreditado ou nao)
-- ---------------------------------------------------------------------
CREATE TABLE lims_laboratories (
    tenant_id UUID NOT NULL,
    id        UUID NOT NULL,
    name      TEXT NOT NULL CHECK (btrim(name) <> ''),
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('lims_laboratories');

CREATE TABLE lims_lab_scope (
    tenant_id    UUID NOT NULL,
    laboratory_id UUID NOT NULL,
    method_ref   TEXT NOT NULL CHECK (btrim(method_ref) <> ''),
    parameter_key TEXT NOT NULL CHECK (btrim(parameter_key) <> ''),
    accredited   BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (tenant_id, laboratory_id, method_ref, parameter_key),
    FOREIGN KEY (tenant_id, laboratory_id)
        REFERENCES lims_laboratories (tenant_id, id)
);
SELECT register_tenant_table('lims_lab_scope');

-- ---------------------------------------------------------------------
-- Equipamentos e historico de calibracao (bloqueio e regua do servico:
-- depende da data de uso, nao de uma coluna)
-- ---------------------------------------------------------------------
CREATE TABLE lims_equipment (
    tenant_id   UUID NOT NULL,
    id          UUID NOT NULL,
    code        TEXT NOT NULL CHECK (btrim(code) <> ''),
    description TEXT NOT NULL CHECK (btrim(description) <> ''),
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, code),
    UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('lims_equipment');

CREATE TABLE lims_calibrations (
    tenant_id     UUID NOT NULL,
    id            UUID NOT NULL,
    equipment_id  UUID NOT NULL,
    calibrated_on DATE NOT NULL,
    valid_until   DATE NOT NULL CHECK (valid_until >= calibrated_on),
    certificate_ref TEXT NOT NULL CHECK (btrim(certificate_ref) <> ''),
    calibrated_by_ref TEXT NOT NULL CHECK (btrim(calibrated_by_ref) <> ''),
    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, equipment_id)
        REFERENCES lims_equipment (tenant_id, id)
);
SELECT register_tenant_table('lims_calibrations');

-- ---------------------------------------------------------------------
-- Autorizacao de analista por metodo (segregacao: quem autoriza nao
-- e o proprio analista — CHECK espelha o Java)
-- ---------------------------------------------------------------------
CREATE TABLE lims_analyst_authorizations (
    tenant_id      UUID NOT NULL,
    id             UUID NOT NULL,
    analyst_ref    TEXT NOT NULL CHECK (btrim(analyst_ref) <> ''),
    method_ref     TEXT NOT NULL CHECK (btrim(method_ref) <> ''),
    authorized_from DATE NOT NULL,
    authorized_to   DATE,
    authorized_by_ref TEXT NOT NULL CHECK (btrim(authorized_by_ref) <> ''),
    PRIMARY KEY (tenant_id, id),
    CHECK (authorized_to IS NULL OR authorized_to >= authorized_from),
    CHECK (authorized_by_ref <> analyst_ref)
);
SELECT register_tenant_table('lims_analyst_authorizations');

-- ---------------------------------------------------------------------
-- Condicoes ambientais: requisito DECLARADO e leituras registradas
-- ---------------------------------------------------------------------
CREATE TABLE lims_environmental_requirements (
    tenant_id      UUID NOT NULL,
    id             UUID NOT NULL,
    temperature_min NUMERIC,
    temperature_max NUMERIC,
    humidity_min    NUMERIC,
    humidity_max    NUMERIC,
    declared_by_ref TEXT NOT NULL CHECK (btrim(declared_by_ref) <> ''),
    PRIMARY KEY (tenant_id, id),
    CHECK (temperature_min IS NOT NULL OR temperature_max IS NOT NULL
        OR humidity_min IS NOT NULL OR humidity_max IS NOT NULL),
    CHECK (temperature_min IS NULL OR temperature_max IS NULL OR temperature_min <= temperature_max),
    CHECK (humidity_min IS NULL OR humidity_max IS NULL OR humidity_min <= humidity_max)
);
SELECT register_tenant_table('lims_environmental_requirements');

CREATE TABLE lims_environmental_readings (
    tenant_id   UUID NOT NULL,
    id          UUID NOT NULL,
    temperature NUMERIC,
    humidity    NUMERIC,
    read_at     TIMESTAMPTZ NOT NULL,
    recorded_by_ref TEXT NOT NULL CHECK (btrim(recorded_by_ref) <> ''),
    PRIMARY KEY (tenant_id, id),
    CHECK (temperature IS NOT NULL OR humidity IS NOT NULL)
);
SELECT register_tenant_table('lims_environmental_readings');

-- ---------------------------------------------------------------------
-- Padroes e materiais de referencia (fornecedor = cert_suppliers da
-- P3.31, JOR-18; o papel MATERIAL e conferido no servico)
-- ---------------------------------------------------------------------
CREATE TABLE lims_reference_materials (
    tenant_id   UUID NOT NULL,
    id          UUID NOT NULL,
    code        TEXT NOT NULL CHECK (btrim(code) <> ''),
    description TEXT NOT NULL CHECK (btrim(description) <> ''),
    lot_ref     TEXT NOT NULL CHECK (btrim(lot_ref) <> ''),
    certified_value NUMERIC NOT NULL,
    certified_uncertainty NUMERIC NOT NULL CHECK (certified_uncertainty >= 0),
    unit        TEXT,
    expires_on  DATE NOT NULL,
    supplier_id UUID NOT NULL,
    certificate_ref TEXT NOT NULL CHECK (btrim(certificate_ref) <> ''),
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, code, lot_ref),
    FOREIGN KEY (tenant_id, supplier_id)
        REFERENCES cert_suppliers (tenant_id, id)
);
SELECT register_tenant_table('lims_reference_materials');

-- ---------------------------------------------------------------------
-- Validacao de metodo (dossie obrigatorio; LQ >= LD quando ambos)
-- ---------------------------------------------------------------------
CREATE TABLE lims_method_validations (
    tenant_id   UUID NOT NULL,
    id          UUID NOT NULL,
    method_ref  TEXT NOT NULL CHECK (btrim(method_ref) <> ''),
    parameter_key TEXT NOT NULL CHECK (btrim(parameter_key) <> ''),
    detection_limit NUMERIC CHECK (detection_limit IS NULL OR detection_limit >= 0),
    quantification_limit NUMERIC CHECK (quantification_limit IS NULL OR quantification_limit >= 0),
    dossier_ref TEXT NOT NULL CHECK (btrim(dossier_ref) <> ''),
    validated_by_ref TEXT NOT NULL CHECK (btrim(validated_by_ref) <> ''),
    validated_on DATE NOT NULL,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, method_ref, parameter_key),
    CHECK (detection_limit IS NULL OR quantification_limit IS NULL
        OR quantification_limit >= detection_limit)
);
SELECT register_tenant_table('lims_method_validations');

-- ---------------------------------------------------------------------
-- Orcamentos de incerteza (u e k declarados; regra de decisao declarada)
-- ---------------------------------------------------------------------
CREATE TABLE lims_uncertainty_budgets (
    tenant_id   UUID NOT NULL,
    id          UUID NOT NULL,
    method_ref  TEXT NOT NULL CHECK (btrim(method_ref) <> ''),
    parameter_key TEXT NOT NULL CHECK (btrim(parameter_key) <> ''),
    standard_uncertainty NUMERIC NOT NULL CHECK (standard_uncertainty > 0),
    coverage_factor NUMERIC NOT NULL CHECK (coverage_factor > 0),
    decision_rule TEXT NOT NULL CHECK (decision_rule IN ('SIMPLE_ACCEPTANCE', 'GUARD_BAND')),
    declared_by_ref TEXT NOT NULL CHECK (btrim(declared_by_ref) <> ''),
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, method_ref, parameter_key)
);
SELECT register_tenant_table('lims_uncertainty_budgets');

-- ---------------------------------------------------------------------
-- Controle de Westgard: material (media/desvio ATRIBUIDOS) e observacoes
-- ---------------------------------------------------------------------
CREATE TABLE lims_qc_materials (
    tenant_id   UUID NOT NULL,
    id          UUID NOT NULL,
    code        TEXT NOT NULL CHECK (btrim(code) <> ''),
    parameter_key TEXT NOT NULL CHECK (btrim(parameter_key) <> ''),
    assigned_mean NUMERIC NOT NULL,
    assigned_sd NUMERIC NOT NULL CHECK (assigned_sd > 0),
    unit        TEXT,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, code)
);
SELECT register_tenant_table('lims_qc_materials');

CREATE TABLE lims_qc_observations (
    tenant_id   UUID NOT NULL,
    id          UUID NOT NULL,
    qc_material_id UUID NOT NULL,
    observed_value NUMERIC NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, qc_material_id)
        REFERENCES lims_qc_materials (tenant_id, id)
);
SELECT register_tenant_table('lims_qc_observations');

-- ---------------------------------------------------------------------
-- Ensaios (camada 17025 sobre a amostra da ED-50, referenciada por id;
-- a FK para quality_samples e conferida na integracao, quando o SQL
-- pendente da ED-50 for numerado — ver cabecalho)
-- ---------------------------------------------------------------------
CREATE TABLE lims_assays (
    tenant_id   UUID NOT NULL,
    id          UUID NOT NULL,
    sample_id   UUID NOT NULL,
    laboratory_id UUID NOT NULL,
    method_ref  TEXT NOT NULL CHECK (btrim(method_ref) <> ''),
    parameter_key TEXT NOT NULL CHECK (btrim(parameter_key) <> ''),
    analyst_ref TEXT NOT NULL CHECK (btrim(analyst_ref) <> ''),
    equipment_code TEXT NOT NULL CHECK (btrim(equipment_code) <> ''),
    analysis_date DATE NOT NULL,
    environmental_verdict TEXT NOT NULL
        CHECK (environmental_verdict IN ('WITHIN_LIMITS', 'OUT_OF_LIMITS', 'NOT_DECLARED')),
    qc_status   TEXT NOT NULL CHECK (qc_status IN ('IN_CONTROL', 'WARNING', 'OUT_OF_CONTROL')),
    reference_material_id UUID,
    status      TEXT NOT NULL CHECK (status IN ('IN_PROGRESS', 'RESULT_RECORDED', 'VALIDATED')),
    numeric_value NUMERIC,
    qualitative_value TEXT,
    unit        TEXT,
    recorded_at TIMESTAMPTZ,
    validator_ref TEXT,
    nonconformity_note TEXT,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, laboratory_id)
        REFERENCES lims_laboratories (tenant_id, id),
    FOREIGN KEY (tenant_id, reference_material_id)
        REFERENCES lims_reference_materials (tenant_id, id),
    CHECK ((numeric_value IS NULL) <> (qualitative_value IS NULL) OR status = 'IN_PROGRESS'),
    CHECK (status <> 'VALIDATED' OR validator_ref IS NOT NULL),
    CHECK (validator_ref IS NULL OR validator_ref <> analyst_ref)
);
SELECT register_tenant_table('lims_assays');

-- ---------------------------------------------------------------------
-- Ensaios de proficiencia (provedor = cert_suppliers, papel conferido
-- no servico; avaliacao como reportada pelo provedor)
-- ---------------------------------------------------------------------
CREATE TABLE lims_proficiency_rounds (
    tenant_id   UUID NOT NULL,
    id          UUID NOT NULL,
    provider_supplier_id UUID NOT NULL,
    scheme_ref  TEXT NOT NULL CHECK (btrim(scheme_ref) <> ''),
    round_ref   TEXT NOT NULL CHECK (btrim(round_ref) <> ''),
    method_ref  TEXT NOT NULL CHECK (btrim(method_ref) <> ''),
    parameter_key TEXT NOT NULL CHECK (btrim(parameter_key) <> ''),
    assigned_value NUMERIC NOT NULL,
    scheme_sigma NUMERIC NOT NULL CHECK (scheme_sigma > 0),
    laboratory_value NUMERIC NOT NULL,
    provider_evaluation TEXT NOT NULL
        CHECK (provider_evaluation IN ('SATISFACTORY', 'QUESTIONABLE', 'UNSATISFACTORY')),
    reported_on DATE NOT NULL,
    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, provider_supplier_id)
        REFERENCES cert_suppliers (tenant_id, id)
);
SELECT register_tenant_table('lims_proficiency_rounds');

-- ---------------------------------------------------------------------
-- Ensaios em laboratorio externo (fornecedor EXTERNAL_LABORATORY da
-- P3.31; laudo externo referenciado obrigatorio)
-- ---------------------------------------------------------------------
CREATE TABLE lims_external_lab_assays (
    tenant_id   UUID NOT NULL,
    id          UUID NOT NULL,
    sample_id   UUID NOT NULL,
    external_lab_supplier_id UUID NOT NULL,
    method_ref  TEXT NOT NULL CHECK (btrim(method_ref) <> ''),
    parameter_key TEXT NOT NULL CHECK (btrim(parameter_key) <> ''),
    external_report_ref TEXT NOT NULL CHECK (btrim(external_report_ref) <> ''),
    received_on DATE NOT NULL,
    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, external_lab_supplier_id)
        REFERENCES cert_suppliers (tenant_id, id)
);
SELECT register_tenant_table('lims_external_lab_assays');

-- ---------------------------------------------------------------------
-- Laudos e emendas (versao seguinte com motivo; anterior SUPERSEDED,
-- preservada — historico por INSERT, sem UPDATE de conteudo)
-- ---------------------------------------------------------------------
CREATE TABLE lims_lab_reports (
    tenant_id   UUID NOT NULL,
    id          UUID NOT NULL,
    laboratory_id UUID NOT NULL,
    template_key TEXT NOT NULL CHECK (btrim(template_key) <> ''),
    rendered_ref TEXT NOT NULL CHECK (btrim(rendered_ref) <> ''),
    issued_by_ref TEXT NOT NULL CHECK (btrim(issued_by_ref) <> ''),
    issued_at   TIMESTAMPTZ NOT NULL,
    version     INTEGER NOT NULL CHECK (version >= 1),
    supersedes_id UUID,
    amendment_reason TEXT,
    status      TEXT NOT NULL CHECK (status IN ('ISSUED', 'SUPERSEDED')),
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, laboratory_id)
        REFERENCES lims_laboratories (tenant_id, id),
    FOREIGN KEY (tenant_id, supersedes_id)
        REFERENCES lims_lab_reports (tenant_id, id),
    CHECK ((version = 1 AND supersedes_id IS NULL AND amendment_reason IS NULL)
        OR (version > 1 AND supersedes_id IS NOT NULL
            AND amendment_reason IS NOT NULL AND btrim(amendment_reason) <> ''))
);
SELECT register_tenant_table('lims_lab_reports');

CREATE TABLE lims_lab_report_assays (
    tenant_id UUID NOT NULL,
    report_id UUID NOT NULL,
    assay_id  UUID NOT NULL,
    PRIMARY KEY (tenant_id, report_id, assay_id),
    FOREIGN KEY (tenant_id, report_id)
        REFERENCES lims_lab_reports (tenant_id, id),
    FOREIGN KEY (tenant_id, assay_id)
        REFERENCES lims_assays (tenant_id, id)
);
SELECT register_tenant_table('lims_lab_report_assays');

-- ---------------------------------------------------------------------
-- Trabalho nao conforme (fechamento exige a acao tomada; occurrence_id
-- opcional para a ocorrencia unica da P2.25 — FK conferida na integracao)
-- ---------------------------------------------------------------------
CREATE TABLE lims_nonconforming_work (
    tenant_id   UUID NOT NULL,
    id          UUID NOT NULL,
    assay_id    UUID,
    report_id   UUID,
    occurrence_id UUID,
    description TEXT NOT NULL CHECK (btrim(description) <> ''),
    disposition TEXT NOT NULL
        CHECK (disposition IN ('REPEAT_ASSAY', 'DISCARD_RESULT', 'AMEND_REPORT', 'NOTIFY_CLIENT', 'OTHER')),
    disposition_justification TEXT,
    opened_by_ref TEXT NOT NULL CHECK (btrim(opened_by_ref) <> ''),
    opened_at   TIMESTAMPTZ NOT NULL,
    status      TEXT NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    action_taken TEXT,
    closed_by_ref TEXT,
    closed_at   TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, assay_id)
        REFERENCES lims_assays (tenant_id, id),
    FOREIGN KEY (tenant_id, report_id)
        REFERENCES lims_lab_reports (tenant_id, id),
    CHECK (assay_id IS NOT NULL OR report_id IS NOT NULL),
    CHECK (disposition <> 'OTHER' OR (disposition_justification IS NOT NULL
        AND btrim(disposition_justification) <> '')),
    CHECK ((status = 'OPEN' AND action_taken IS NULL AND closed_at IS NULL)
        OR (status = 'CLOSED' AND action_taken IS NOT NULL AND btrim(action_taken) <> ''
            AND closed_by_ref IS NOT NULL AND closed_at IS NOT NULL AND closed_at >= opened_at))
);
SELECT register_tenant_table('lims_nonconforming_work');
