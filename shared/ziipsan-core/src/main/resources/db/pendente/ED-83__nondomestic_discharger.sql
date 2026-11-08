-- ED-83 — Esgoto nao domestico e carga poluidora do usuario
-- (CAD-70, OPE-13; NR 11/ANA)
-- SQL PENDENTE de numeracao Flyway (regra da cascata: nenhum numero
-- inventado; prefixo ED-83__ em db/pendente/). Os slots Flyway
-- reservados por decisoes anteriores e a migration legada intocada
-- estao nomeados no despacho desta tarefa e no RELATORIO-ED-83:
-- nenhum deles e usado, referenciado ou alterado aqui. Na integracao: atribuir o
-- proximo slot livre da sequencia global e conferir as FKs externas
-- adiadas (unidades consumidoras do registry, amostras da ED-50,
-- OS do motor vigente — comentarios "FK adiada" abaixo).
--
-- REGRA DURA: este SQL NAO semeia NENHUM padrao de lancamento, limite,
-- intervalo de amostragem, percentual, preco, taxa ou valor de
-- penalidade. O catalogo de padrao de lancamento do prestador
-- (nondomestic_user_standards) nasce VAZIO e so recebe linhas pela
-- carga revisada, SOBRE o catalogo de padrao de efluente da ED-105
-- (effluent_standards, referenciado por versao). Os parametros
-- effluent.user_standard_catalog, effluent.sampling_interval_days e
-- sewer.nondomestic_charge_model sao PARAMETROS do tenant, nao dado
-- semeado. Os 14 valores da P3.9 nao sao tocados por esta tarefa.
--
-- MARCA NA UNIDADE CONSUMIDORA: o aceite fixa a marca
-- `non_domestic_discharger` NA unidade consumidora. A coluna/marca
-- vive na tabela de unidades do registry (fora desta entrega): a
-- integracao a grava para toda unidade com linha ativa em
-- nondomestic_dischargers (pendencia nomeada no relatorio). Aqui a
-- marca aparece como o fato do cadastro (flag espelho NOT NULL com
-- CHECK = TRUE, para a consulta operacional nao depender da fiacao).
--
-- LICAO DA C3: toda FK composta (tenant_id, X) aponta para pai com
-- PRIMARY KEY (tenant_id, id) nesta entrega. FKs para tabelas de
-- outras entregas NAO sao declaradas aqui: ficam como pendencia de
-- integracao nomeada (FK adiada), no padrao da ED-105.
--
-- IDEMPOTENCIA: todos os CREATE TABLE usam IF NOT EXISTS — a
-- reaplicacao integral nao falha.

-- ---------------------------------------------------------------------
-- Cadastro do usuario nao domestico gerador de efluente (CAD-70)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS nondomestic_dischargers (
    tenant_id                 UUID NOT NULL,
    id                        UUID NOT NULL,
    consumer_unit_id          UUID NOT NULL,
    route                     TEXT NOT NULL
        CHECK (route IN ('COMMERCIAL_UNIT', 'CASUAL_VIA_ED22')),
    kind                      TEXT NOT NULL
        CHECK (kind IN ('INDUSTRY', 'AGROINDUSTRY', 'HOSPITAL', 'FUEL_STATION', 'LAUNDRY', 'OTHER')),
    kind_description          TEXT,
    discharge_point_code      TEXT NOT NULL,
    non_domestic_discharger   BOOLEAN NOT NULL DEFAULT TRUE
        CHECK (non_domestic_discharger = TRUE),
    status                    TEXT NOT NULL DEFAULT 'REGISTERED'
        CHECK (status IN ('REGISTERED', 'AUTHORIZED', 'SUSPENDED', 'CLOSED')),
    commitment_term_ref       TEXT,
    authorization_ref         TEXT,
    authorization_valid_from  DATE,
    authorization_valid_to    DATE,
    suspension_reason         TEXT,
    closed_at                 TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, consumer_unit_id),
    CHECK ((kind = 'OTHER') = (kind_description IS NOT NULL AND kind_description <> '')),
    CHECK (authorization_valid_to IS NULL OR authorization_valid_from IS NOT NULL),
    CHECK (authorization_valid_to IS NULL OR authorization_valid_to >= authorization_valid_from),
    CHECK ((status = 'AUTHORIZED') = (authorization_ref IS NOT NULL AND authorization_valid_from IS NOT NULL)
        OR status IN ('SUSPENDED', 'CLOSED')),
    CHECK (status <> 'AUTHORIZED' OR commitment_term_ref IS NOT NULL),
    CHECK ((status = 'SUSPENDED') = (suspension_reason IS NOT NULL) OR status = 'CLOSED'),
    CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))
    -- FK (tenant_id, consumer_unit_id) -> unidade consumidora do
    -- registry (P1.3/ED-12; avulsa CASUAL da ED-22 na rota
    -- CASUAL_VIA_ED22): conferir na integracao (FK adiada).
);
SELECT register_tenant_table('nondomestic_dischargers');

-- ---------------------------------------------------------------------
-- Caracterizacao do efluente e pre-tratamento as expensas do usuario
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS nondomestic_characterizations (
    tenant_id                    UUID NOT NULL,
    id                           UUID NOT NULL,
    discharger_id                UUID NOT NULL,
    pretreatment_description     TEXT NOT NULL,
    pretreatment_at_user_expense BOOLEAN NOT NULL
        CHECK (pretreatment_at_user_expense = TRUE),
    declared_by_ref              TEXT NOT NULL,
    declared_at                  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, discharger_id),
    FOREIGN KEY (tenant_id, discharger_id)
        REFERENCES nondomestic_dischargers (tenant_id, id)
);
SELECT register_tenant_table('nondomestic_characterizations');

CREATE TABLE IF NOT EXISTS nondomestic_characterization_params (
    tenant_id           UUID NOT NULL,
    characterization_id UUID NOT NULL,
    parameter_key       TEXT NOT NULL,
    numeric_value       NUMERIC,
    qualitative_value   TEXT,
    unit                TEXT,
    PRIMARY KEY (tenant_id, characterization_id, parameter_key),
    FOREIGN KEY (tenant_id, characterization_id)
        REFERENCES nondomestic_characterizations (tenant_id, id),
    CHECK ((numeric_value IS NOT NULL) <> (qualitative_value IS NOT NULL))
);
SELECT register_tenant_table('nondomestic_characterization_params');

-- ---------------------------------------------------------------------
-- Catalogo de padrao de lancamento DO PRESTADOR, SOBRE o catalogo de
-- padrao de efluente da ED-105 (effluent.user_standard_catalog).
-- Nasce VAZIO: linhas so pela carga revisada. base_catalog_version e
-- a versao do catalogo da ED-105 sobre o qual este catalogo se apoia.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS nondomestic_user_catalog_versions (
    tenant_id          UUID NOT NULL,
    version            TEXT NOT NULL,
    base_catalog_version TEXT NOT NULL,
    loaded_by          TEXT NOT NULL,
    loaded_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, version)
    -- FK (tenant_id, base_catalog_version) ->
    -- effluent_catalog_versions (tenant_id, version) da ED-105:
    -- conferir na integracao (FK adiada; a ED-105 pousa na mesma
    -- camada anterior da cascata, C5, e a ordem de aplicacao e da
    -- integracao).
);
SELECT register_tenant_table('nondomestic_user_catalog_versions');

CREATE TABLE IF NOT EXISTS nondomestic_user_standards (
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
    jurisdiction                       TEXT NOT NULL
        CHECK (jurisdiction IN ('FEDERAL', 'STATE')),
    state_code                         TEXT NOT NULL DEFAULT '',
    publication_status                 TEXT NOT NULL
        CHECK (publication_status IN ('PUBLISHED')),
    reviewed_by_technical_responsible  BOOLEAN NOT NULL DEFAULT FALSE,
    reviewer_ref                       TEXT,
    effective_from                     DATE NOT NULL,
    effective_to                       DATE,
    PRIMARY KEY (tenant_id, catalog_version, parameter_key, jurisdiction, state_code, effective_from),
    FOREIGN KEY (tenant_id, catalog_version)
        REFERENCES nondomestic_user_catalog_versions (tenant_id, version),
    CHECK ((jurisdiction = 'FEDERAL' AND state_code = '')
        OR (jurisdiction = 'STATE' AND state_code <> '')),
    CHECK (reviewed_by_technical_responsible = FALSE OR reviewer_ref IS NOT NULL),
    CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CHECK (
        (comparator = 'MAX'   AND limit_max IS NOT NULL AND limit_min IS NULL AND qualitative_expected IS NULL) OR
        (comparator = 'MIN'   AND limit_min IS NOT NULL AND limit_max IS NULL AND qualitative_expected IS NULL) OR
        (comparator = 'RANGE' AND limit_min IS NOT NULL AND limit_max IS NOT NULL AND limit_min <= limit_max AND qualitative_expected IS NULL) OR
        (comparator = 'QUALITATIVE_EQUALS' AND qualitative_expected IS NOT NULL AND limit_min IS NULL AND limit_max IS NULL)
    )
);
SELECT register_tenant_table('nondomestic_user_standards');

-- ---------------------------------------------------------------------
-- Coletas do plano de amostragem do prestador no ponto de lancamento.
-- A amostra e a Sample da ED-50 (etiqueta/custodia); sample_id fica
-- sem FK aqui (FK adiada para quality_samples da ED-50).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS nondomestic_sampling_collections (
    tenant_id          UUID NOT NULL,
    id                 UUID NOT NULL,
    discharger_id      UUID NOT NULL,
    sample_id          UUID NOT NULL,
    parameter_key      TEXT NOT NULL,
    collection_date    DATE NOT NULL,
    received_by_lab    BOOLEAN NOT NULL DEFAULT FALSE,
    verdict            TEXT
        CHECK (verdict IS NULL OR verdict IN ('CONFORMING', 'NON_CONFORMING', 'INDETERMINATE')),
    standard_source    TEXT
        CHECK (standard_source IS NULL OR standard_source IN ('PROVIDER', 'BASE_ED105', 'NONE')),
    norm_reference     TEXT,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, sample_id, parameter_key),
    FOREIGN KEY (tenant_id, discharger_id)
        REFERENCES nondomestic_dischargers (tenant_id, id),
    CHECK (verdict IS NULL OR received_by_lab = TRUE)
    -- FK (tenant_id, sample_id) -> quality_samples (tenant_id, id)
    -- da ED-50: conferir na integracao (FK adiada).
);
SELECT register_tenant_table('nondomestic_sampling_collections');

-- ---------------------------------------------------------------------
-- Automonitoramento informado pelo usuario
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS nondomestic_self_monitoring_reports (
    tenant_id     UUID NOT NULL,
    id            UUID NOT NULL,
    discharger_id UUID NOT NULL,
    period_start  DATE NOT NULL,
    period_end    DATE NOT NULL,
    reported_by_ref TEXT NOT NULL,
    reported_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, discharger_id)
        REFERENCES nondomestic_dischargers (tenant_id, id),
    CHECK (period_end >= period_start)
);
SELECT register_tenant_table('nondomestic_self_monitoring_reports');

CREATE TABLE IF NOT EXISTS nondomestic_self_monitoring_results (
    tenant_id           UUID NOT NULL,
    report_id           UUID NOT NULL,
    parameter_key       TEXT NOT NULL,
    numeric_value       NUMERIC,
    qualitative_value   TEXT,
    verdict             TEXT
        CHECK (verdict IS NULL OR verdict IN ('CONFORMING', 'NON_CONFORMING', 'INDETERMINATE')),
    PRIMARY KEY (tenant_id, report_id, parameter_key),
    FOREIGN KEY (tenant_id, report_id)
        REFERENCES nondomestic_self_monitoring_reports (tenant_id, id),
    CHECK ((numeric_value IS NOT NULL) <> (qualitative_value IS NOT NULL))
);
SELECT register_tenant_table('nondomestic_self_monitoring_results');

-- ---------------------------------------------------------------------
-- Vistorias por OS no ponto de lancamento (field_order_id e a OS do
-- motor vigente — ED-20/P1.31; FK adiada para a tabela de OS).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS nondomestic_inspections (
    tenant_id     UUID NOT NULL,
    id            UUID NOT NULL,
    discharger_id UUID NOT NULL,
    field_order_id UUID NOT NULL,
    reason        TEXT NOT NULL,
    opened_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, discharger_id)
        REFERENCES nondomestic_dischargers (tenant_id, id)
);
SELECT register_tenant_table('nondomestic_inspections');

-- ---------------------------------------------------------------------
-- Nao conformidade: notificacao ANTES da penalidade (espelho do
-- nucleo). Valor e base da penalidade sao dados do processo — as
-- colunas guardam o fato, nenhum valor e semeado ou presumido.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS nondomestic_nonconformities (
    tenant_id              UUID NOT NULL,
    id                     UUID NOT NULL,
    discharger_id          UUID NOT NULL,
    origin                 TEXT NOT NULL
        CHECK (origin IN ('PROVIDER_SAMPLING', 'SELF_MONITORING', 'INSPECTION')),
    parameter_key          TEXT NOT NULL,
    detail                 TEXT NOT NULL,
    detected_at            TIMESTAMPTZ NOT NULL,
    status                 TEXT NOT NULL DEFAULT 'DETECTED'
        CHECK (status IN ('DETECTED', 'NOTIFIED', 'PENALIZED', 'RESOLVED')),
    notification_ref       TEXT,
    notified_at            TIMESTAMPTZ,
    penalty_amount         NUMERIC,
    penalty_basis_ref      TEXT,
    penalized_at           TIMESTAMPTZ,
    correction_evidence_ref TEXT,
    reinspection_order_id  UUID,
    resolved_at            TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, discharger_id)
        REFERENCES nondomestic_dischargers (tenant_id, id),
    CHECK ((status = 'DETECTED') = (notification_ref IS NULL AND notified_at IS NULL)),
    CHECK (status <> 'PENALIZED' OR (penalty_amount IS NOT NULL AND penalty_amount > 0
        AND penalty_basis_ref IS NOT NULL AND penalized_at IS NOT NULL
        AND notified_at IS NOT NULL AND penalized_at >= notified_at)),
    CHECK (status <> 'RESOLVED' OR (correction_evidence_ref IS NOT NULL
        AND reinspection_order_id IS NOT NULL AND resolved_at IS NOT NULL))
);
SELECT register_tenant_table('nondomestic_nonconformities');

-- ---------------------------------------------------------------------
-- Componente de cobranca complementar para a conta (motor da P2.5).
-- Guarda o componente calculado; NAO e debito, NAO liga cobranca
-- automatica e NAO toca CDA (ED-31 desligada por desenho).
-- Guarda do aceite: modelo POLLUTANT_LOAD exige a previsao da agencia.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS nondomestic_charge_components (
    tenant_id                   UUID NOT NULL,
    id                          UUID NOT NULL,
    discharger_id               UUID NOT NULL,
    competence_year             INTEGER NOT NULL CHECK (competence_year BETWEEN 2000 AND 2200),
    competence_month            INTEGER NOT NULL CHECK (competence_month BETWEEN 1 AND 12),
    charge_model                TEXT NOT NULL
        CHECK (charge_model IN ('VOLUME_PERCENT', 'POLLUTANT_LOAD', 'FIXED_BY_CLASS')),
    component_code              TEXT NOT NULL DEFAULT 'NONDOMESTIC_EFFLUENT'
        CHECK (component_code = 'NONDOMESTIC_EFFLUENT'),
    amount                      NUMERIC NOT NULL CHECK (amount >= 0),
    water_volume_m3             NUMERIC,
    billable_volume_m3          NUMERIC,
    total_load_kg               NUMERIC,
    charge_class_code           TEXT,
    agency_foresees_load_billing BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, discharger_id, competence_year, competence_month),
    FOREIGN KEY (tenant_id, discharger_id)
        REFERENCES nondomestic_dischargers (tenant_id, id),
    CHECK (charge_model <> 'POLLUTANT_LOAD' OR agency_foresees_load_billing = TRUE),
    CHECK (charge_model <> 'VOLUME_PERCENT' OR (water_volume_m3 IS NOT NULL AND billable_volume_m3 IS NOT NULL)),
    CHECK (charge_model <> 'POLLUTANT_LOAD' OR total_load_kg IS NOT NULL),
    CHECK (charge_model <> 'FIXED_BY_CLASS' OR charge_class_code IS NOT NULL)
);
SELECT register_tenant_table('nondomestic_charge_components');

-- ---------------------------------------------------------------------
-- Parametros do tenant (definitions para conferencia contra o
-- catalogo de parametros vigente na integracao — NAO atribuir slot
-- sem conferir; nenhum valor de fabrica):
--   effluent.user_standard_catalog  versao vigente do catalogo de
--                               padrao de lancamento do prestador
--                               sobre o catalogo da ED-105.
--   effluent.sampling_interval_days intervalo, em dias, do plano de
--                               amostragem do prestador no ponto de
--                               lancamento do usuario.
--   sewer.nondomestic_charge_model  VOLUME_PERCENT | POLLUTANT_LOAD |
--                               FIXED_BY_CLASS (classe d).
-- ---------------------------------------------------------------------
