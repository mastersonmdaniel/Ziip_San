-- ED-50 — Qualidade da agua operacional (JOR-22, JOR-37, OPE-26)
-- SQL PENDENTE de numeracao Flyway (regra da cascata: nenhum numero
-- inventado). Na integracao: atribuir o proximo slot livre, renomear
-- para V<slot>__water_quality.sql e conferir as FKs contra V8_1
-- (service_protocols) e o cadastro de unidades/pontos vigente.
--
-- REGRA DURA: este SQL NAO semeia NENHUM limite, frequencia, quantidade
-- de plano nem leiaute. O catalogo normativo (quality_parameter_standards)
-- nasce VAZIO e so recebe linhas pela carga revisada pelo responsavel
-- tecnico. Os 14 valores da P3.9 nao sao tocados por esta tarefa.

-- ---------------------------------------------------------------------
-- Catalogo normativo versionado (linhas entram so por carga revisada)
-- ---------------------------------------------------------------------
CREATE TABLE quality_catalog_versions (
    tenant_id    UUID NOT NULL,
    version      TEXT NOT NULL,
    loaded_by    TEXT NOT NULL,
    loaded_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, version)
);
SELECT register_tenant_table('quality_catalog_versions');

CREATE TABLE quality_parameter_standards (
    tenant_id                          UUID NOT NULL,
    catalog_version                    TEXT NOT NULL,
    parameter_key                      TEXT NOT NULL,
    display_name                       TEXT NOT NULL,
    unit                               TEXT,
    comparator                         TEXT NOT NULL
        CHECK (comparator IN ('MAX', 'MIN', 'RANGE', 'QUALITATIVE_EQUALS')),
    limit_min                          NUMERIC,
    limit_max                          NUMERIC,
    qualitative_expected               TEXT,
    norm_reference                     TEXT NOT NULL,
    health_risk_relevant               BOOLEAN NOT NULL DEFAULT FALSE,
    reviewed_by_technical_responsible  BOOLEAN NOT NULL DEFAULT FALSE,
    reviewer_ref                       TEXT,
    effective_from                     DATE NOT NULL,
    effective_to                       DATE,
    PRIMARY KEY (tenant_id, catalog_version, parameter_key, effective_from),
    FOREIGN KEY (tenant_id, catalog_version)
        REFERENCES quality_catalog_versions (tenant_id, version),
    CHECK (reviewed_by_technical_responsible = FALSE OR reviewer_ref IS NOT NULL),
    CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CHECK (
        (comparator = 'MAX'   AND limit_max IS NOT NULL AND limit_min IS NULL AND qualitative_expected IS NULL) OR
        (comparator = 'MIN'   AND limit_min IS NOT NULL AND limit_max IS NULL AND qualitative_expected IS NULL) OR
        (comparator = 'RANGE' AND limit_min IS NOT NULL AND limit_max IS NOT NULL AND limit_min <= limit_max AND qualitative_expected IS NULL) OR
        (comparator = 'QUALITATIVE_EQUALS' AND qualitative_expected IS NOT NULL AND limit_min IS NULL AND limit_max IS NULL)
    )
);
SELECT register_tenant_table('quality_parameter_standards');

-- ---------------------------------------------------------------------
-- Pontos e plano de amostragem (quantidades definidas no plano do tenant)
-- ---------------------------------------------------------------------
CREATE TABLE quality_sampling_points (
    tenant_id       UUID NOT NULL,
    id              UUID NOT NULL,
    code            TEXT NOT NULL,
    supply_system   TEXT NOT NULL,
    kind            TEXT NOT NULL
        CHECK (kind IN ('TREATMENT_OUTLET', 'DISTRIBUTION_NETWORK', 'RESERVOIR', 'CONSUMER_POINT', 'RAW_WATER_INLET')),
    consumer_unit_id UUID,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, code),
    CHECK ((kind = 'CONSUMER_POINT') = (consumer_unit_id IS NOT NULL))
);
SELECT register_tenant_table('quality_sampling_points');

CREATE TABLE quality_sampling_plans (
    tenant_id       UUID NOT NULL,
    id              UUID NOT NULL,
    supply_system   TEXT NOT NULL,
    period_start    DATE NOT NULL,  -- primeiro dia do mes inicial
    period_end      DATE NOT NULL,  -- primeiro dia do mes final
    catalog_version TEXT NOT NULL,
    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, catalog_version)
        REFERENCES quality_catalog_versions (tenant_id, version),
    CHECK (period_end >= period_start)
);
SELECT register_tenant_table('quality_sampling_plans');

CREATE TABLE quality_sampling_plan_lines (
    tenant_id     UUID NOT NULL,
    plan_id       UUID NOT NULL,
    point_id      UUID NOT NULL,
    parameter_key TEXT NOT NULL,
    planned_count INTEGER NOT NULL CHECK (planned_count > 0),
    PRIMARY KEY (tenant_id, plan_id, point_id, parameter_key),
    FOREIGN KEY (tenant_id, plan_id) REFERENCES quality_sampling_plans (tenant_id, id),
    FOREIGN KEY (tenant_id, point_id) REFERENCES quality_sampling_points (tenant_id, id)
);
SELECT register_tenant_table('quality_sampling_plan_lines');

-- ---------------------------------------------------------------------
-- Amostras e cadeia de custodia (JOR-37)
-- ---------------------------------------------------------------------
CREATE TABLE quality_samples (
    tenant_id        UUID NOT NULL,
    id               UUID NOT NULL,
    point_id         UUID NOT NULL,
    sample_type      TEXT NOT NULL
        CHECK (sample_type IN ('ROUTINE', 'COMPLAINT', 'RECOLLECTION', 'PROCESS_CONTROL')),
    label_code       TEXT NOT NULL,
    field_order_id   UUID,
    protocol_id      UUID,
    original_sample_id UUID,
    status           TEXT NOT NULL
        CHECK (status IN ('PLANNED', 'COLLECTED', 'IN_TRANSIT', 'RECEIVED_BY_LAB', 'ANALYZED', 'VALIDATED', 'PUBLISHED', 'CANCELLED')),
    lab_receiver_ref TEXT,
    lab_received_at  TIMESTAMPTZ,
    cancel_reason    TEXT,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, label_code),
    FOREIGN KEY (tenant_id, point_id) REFERENCES quality_sampling_points (tenant_id, id),
    FOREIGN KEY (tenant_id, original_sample_id) REFERENCES quality_samples (tenant_id, id),
    CHECK (sample_type <> 'COMPLAINT' OR protocol_id IS NOT NULL),
    CHECK ((sample_type = 'RECOLLECTION') = (original_sample_id IS NOT NULL))
    -- FK (tenant_id, protocol_id) -> service_protocols (tenant_id, id) da V8_1:
    -- conferir na integracao (padrao das FKs de protocolo do repo).
);
SELECT register_tenant_table('quality_samples');

CREATE TABLE quality_custody_events (
    tenant_id UUID NOT NULL,
    sample_id UUID NOT NULL,
    seq       INTEGER NOT NULL,
    kind      TEXT NOT NULL
        CHECK (kind IN ('COLLECTED', 'SEALED', 'DISPATCHED', 'RECEIVED_BY_LAB')), -- correcao m-2: RETURNED removido (enum morto; aceite sem devolucao)
    actor_ref TEXT NOT NULL,
    at        TIMESTAMPTZ NOT NULL,
    note      TEXT,
    PRIMARY KEY (tenant_id, sample_id, seq),
    FOREIGN KEY (tenant_id, sample_id) REFERENCES quality_samples (tenant_id, id)
);
SELECT register_tenant_table('quality_custody_events');

-- ---------------------------------------------------------------------
-- Resultados (versao com justificativa; historico nunca apagado)
-- ---------------------------------------------------------------------
CREATE TABLE quality_analysis_results (
    tenant_id                UUID NOT NULL,
    id                       UUID NOT NULL,
    sample_id                UUID NOT NULL,
    parameter_key            TEXT NOT NULL,
    numeric_value            NUMERIC,
    qualitative_value        TEXT,
    unit                     TEXT,
    method_ref               TEXT,
    analyst_ref              TEXT NOT NULL,
    analyzed_at              TIMESTAMPTZ NOT NULL,
    version                  INTEGER NOT NULL CHECK (version >= 1),
    supersedes_id            UUID,
    correction_justification TEXT,
    validated                BOOLEAN NOT NULL DEFAULT FALSE,
    validator_ref            TEXT,
    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, sample_id) REFERENCES quality_samples (tenant_id, id),
    FOREIGN KEY (tenant_id, supersedes_id) REFERENCES quality_analysis_results (tenant_id, id),
    CHECK ((numeric_value IS NOT NULL) <> (qualitative_value IS NOT NULL)),
    CHECK ((version = 1 AND supersedes_id IS NULL AND correction_justification IS NULL)
        OR (version > 1 AND supersedes_id IS NOT NULL AND correction_justification IS NOT NULL)),
    CHECK ((validated AND validator_ref IS NOT NULL) OR (NOT validated AND validator_ref IS NULL))
);
SELECT register_tenant_table('quality_analysis_results');

-- ---------------------------------------------------------------------
-- Fechamento mensal
-- ---------------------------------------------------------------------
CREATE TABLE quality_monthly_closings (
    tenant_id     UUID NOT NULL,
    id            UUID NOT NULL,
    supply_system TEXT NOT NULL,
    ref_month     DATE NOT NULL,  -- primeiro dia do mes
    closed        BOOLEAN NOT NULL DEFAULT FALSE,
    closed_by_ref TEXT,
    closed_at     TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, supply_system, ref_month),
    CHECK ((closed AND closed_by_ref IS NOT NULL AND closed_at IS NOT NULL)
        OR (NOT closed AND closed_by_ref IS NULL AND closed_at IS NULL))
);
SELECT register_tenant_table('quality_monthly_closings');

CREATE TABLE quality_monthly_closing_lines (
    tenant_id     UUID NOT NULL,
    closing_id    UUID NOT NULL,
    parameter_key TEXT NOT NULL,
    planned       INTEGER NOT NULL,
    collected     INTEGER NOT NULL,
    analyzed      INTEGER NOT NULL,
    conforming    INTEGER NOT NULL,
    non_conforming INTEGER NOT NULL,
    indeterminate INTEGER NOT NULL,
    PRIMARY KEY (tenant_id, closing_id, parameter_key),
    FOREIGN KEY (tenant_id, closing_id) REFERENCES quality_monthly_closings (tenant_id, id)
);
SELECT register_tenant_table('quality_monthly_closing_lines');

-- ---------------------------------------------------------------------
-- Reclamacao de qualidade (OPE-26) e comunicacao de risco (JOR-22)
-- ---------------------------------------------------------------------
CREATE TABLE quality_complaint_flows (
    tenant_id           UUID NOT NULL,
    protocol_id         UUID NOT NULL,
    consumer_point_id   UUID NOT NULL,
    kind                TEXT NOT NULL CHECK (kind IN ('COLOR', 'TASTE', 'ODOR', 'PRESSURE', 'OTHER')),
    opened_on           DATE NOT NULL,
    deadline_days       INTEGER NOT NULL CHECK (deadline_days > 0 AND deadline_days <= 30),
    stage               TEXT NOT NULL,
    collection_order_id UUID,
    complaint_sample_id UUID,
    recollection_order_id UUID,
    response_reference  TEXT,
    PRIMARY KEY (tenant_id, protocol_id),
    FOREIGN KEY (tenant_id, consumer_point_id) REFERENCES quality_sampling_points (tenant_id, id),
    FOREIGN KEY (tenant_id, complaint_sample_id) REFERENCES quality_samples (tenant_id, id)
);
SELECT register_tenant_table('quality_complaint_flows');

CREATE TABLE quality_health_communications (
    tenant_id          UUID NOT NULL,
    id                 UUID NOT NULL,
    sample_id          UUID NOT NULL,
    parameter_key      TEXT NOT NULL,
    deadline           DATE NOT NULL,  -- [SEM FONTE] declarado pelo tenant apos conferencia (JOR-22)
    articles_checked   BOOLEAN NOT NULL DEFAULT FALSE,
    checker_ref        TEXT,
    authority_dispatch_ref  TEXT,
    authority_dispatched_at TIMESTAMPTZ,
    population_dispatch_ref  TEXT,
    population_dispatched_at TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, sample_id) REFERENCES quality_samples (tenant_id, id),
    CHECK (articles_checked = FALSE OR checker_ref IS NOT NULL)
);
SELECT register_tenant_table('quality_health_communications');

-- ---------------------------------------------------------------------
-- Parametros do tenant (definitions para conferencia contra o catalogo
-- de parametros vigente na integracao — NAO atribuir slot sem conferir)
--   sac.quality_complaint_deadline_days  classe "so reduz", teto 30 do
--       Decreto 5.440/2005 conforme o aceite (OPE-26): definition com
--       valor de fabrica 30 registrado para o fluxo de aceite do tenant;
--       o nucleo exige o valor vigente explicito e recusa acima de 30.
--   quality.sisagua_layout               [SEM FONTE] sem valor de fabrica.
--   quality.risk_template_key            [SEM FONTE] sem valor de fabrica;
--       a chave so existe depois da carga do modelo conferida (JOR-22).
-- ---------------------------------------------------------------------
