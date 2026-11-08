-- ED-79 — Sistemas de abastecimento e de esgoto, Plano de Seguranca da
-- Agua, relatorio anual e Carta de Servicos (ADR-0041; 21b L-06, L-08 a L-10)
-- SQL PENDENTE de numeracao Flyway (regra da cascata: nenhum numero
-- inventado). Na integracao: atribuir o proximo slot livre da historia
-- tenant do modulo shared/ziipsan-core e renomear o arquivo.
--
-- REGRAS DURAS desta entrega:
--  * NENHUM dado e semeado: nenhum sistema, tipo, parametro, limite,
--    prazo ou texto de Carta nasce neste SQL. Tipos de componente sao
--    CHAVES do catalogo da P1.27 (gis_factory_catalog), referenciadas
--    como texto — sem FK para o catalogo (a reaplicacao dele e da
--    P1.27, e tipo desativado continua valido nos componentes).
--  * TODA FK composta (tenant_id, X) tem no pai a UNIQUE (tenant_id, id)
--    correspondente (licao da C3 / correcao G1 da ED-92). A UNIQUE e
--    declarada nas tabelas que sao ALVO de FK composta (ws_systems,
--    ws_safety_plans, ws_annual_reports, ws_annual_report_issuances,
--    ws_system_components, ws_service_charters). As tabelas-FILHAS de
--    PK composta iniciada por tenant_id (ws_system_treatment_units,
--    ws_safety_plan_risk_links, ws_annual_report_lines,
--    ws_annual_report_months, ws_annual_report_blocks,
--    ws_annual_report_cleanings, ws_service_charter_entries,
--    ws_service_charter_entry_items) NAO declaram UNIQUE (tenant_id, id)
--    propria: nenhuma FK as referencia por (tenant_id, id) e a PK
--    composta ja garante a unicidade por tenant (correcao D5 da
--    revisao da ED-79, 08/11/2026 — o comentario anterior afirmava a
--    UNIQUE em TODAS as tabelas, o que nao valia para
--    ws_system_treatment_units nem para as demais filhas).
--  * O vinculo do PSA com o risco da P3.31 tem FK composta REAL para
--    cert_risks (tenant_id, id) da V21_3 (P3.31 ja esta na main).
--  * gis_feature_id (ED-53) e consumer_unit_id (cadastro) sao
--    referencias inter-modulo SEM FK neste SQL: conferir na integracao
--    (padrao das FKs inter-modulo das ED-50/ED-53).
--  * Os 14 valores da P3.9 NAO sao tocados por esta entrega.
--  * Bases legais citadas sao SO as do aceite: Decreto 5.440/2005,
--    art. 3o, III (relatorio anual) e Lei 13.460/2017 (Carta).
--  * TRAVAS DE EMISSAO NO SQL (correcao D1 da revisao da ED-79,
--    08/11/2026, correcao de codigo ED-79-CORR-2 — SUPERSEDE o registo
--    da correcao documental ED-79-CORR-1, que documentara a assimetria
--    SEM CHECK): (a) ws_annual_reports.legal_basis tem CHECK de
--    igualdade com a constante do aceite (a mesma do Java); (b)
--    constraint triggers DIFERIDAS (avaliadas no COMMIT, depois que
--    as linhas-filhas da transacao existem) recusam relatorio
--    READY/ISSUED sem os 12 meses presentes, relatorio ISSUED sem
--    registro de emissao ISSUED correspondente e emissao ISSUED cujo
--    relatorio nao esteja ISSUED no fim da transacao — os ataques SQL
--    da revisao (ISSUED direto, READY vazio, base legal inventada)
--    passam a ser recusados pelo banco. O que o SQL NAO pode
--    verificar estruturalmente — a AUTENTICIDADE do legal_proof_ref
--    (a referencia da prova da P2.32 e texto cuja emissao real vive
--    no motor dela) e a do portal_ref da Carta — vive SOMENTE na
--    camada de servico, com o risco aceito por escrito na declaracao
--    formal da pendencia P-ED79-03 (RELATORIO-CORRECAO-CODIGO-ED-79).

-- ---------------------------------------------------------------------
-- Sistemas de abastecimento e de esgoto
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ws_systems (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    code              VARCHAR(60) NOT NULL,
    name              VARCHAR(200) NOT NULL,
    kind              VARCHAR(10) NOT NULL CHECK (kind IN ('SUPPLY','SEWAGE')),
    served_population INTEGER NOT NULL CHECK (served_population >= 0),
    population_as_of  DATE NOT NULL,
    source_kind       VARCHAR(12) CHECK (source_kind IN ('SURFACE','GROUNDWATER','MIXED')),
    source_name       VARCHAR(200),
    source_grant_ref  TEXT,
    treatment_description TEXT,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ws_systems_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_ws_systems_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_ws_systems_source CHECK (
        (kind = 'SUPPLY' AND source_kind IS NOT NULL AND source_name IS NOT NULL AND btrim(source_name) <> '')
        OR (kind = 'SEWAGE' AND source_kind IS NULL AND source_name IS NULL)
    )
);
SELECT register_tenant_table('ws_systems');

-- Unidades/tipos de tratamento declarados do sistema (ED-53 / P1.27)
CREATE TABLE IF NOT EXISTS ws_system_treatment_units (
    tenant_id        UUID NOT NULL,
    system_id        UUID NOT NULL,
    seq              INTEGER NOT NULL CHECK (seq >= 1),
    unit_code        VARCHAR(80),
    catalog_type_key VARCHAR(120),
    PRIMARY KEY (tenant_id, system_id, seq),
    CONSTRAINT fk_ws_treatment_system FOREIGN KEY (tenant_id, system_id)
        REFERENCES ws_systems (tenant_id, id),
    CHECK (unit_code IS NOT NULL OR catalog_type_key IS NOT NULL)
);
SELECT register_tenant_table('ws_system_treatment_units');

-- Componentes do sistema (tipo = chave do catalogo da P1.27)
CREATE TABLE IF NOT EXISTS ws_system_components (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    system_id          UUID NOT NULL,
    component_type_key VARCHAR(120) NOT NULL,
    gis_feature_id     UUID,
    name               VARCHAR(200) NOT NULL,
    catalog_type_active BOOLEAN NOT NULL DEFAULT TRUE,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ws_components_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_ws_components_system FOREIGN KEY (tenant_id, system_id)
        REFERENCES ws_systems (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_ws_components_system ON ws_system_components (tenant_id, system_id);
SELECT register_tenant_table('ws_system_components');

-- ---------------------------------------------------------------------
-- Plano de Seguranca da Agua (sobre SUPPLY) e vinculos a riscos (P3.31)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ws_safety_plans (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    system_id     UUID NOT NULL,
    version       INTEGER NOT NULL CHECK (version >= 1),
    status        VARCHAR(12) NOT NULL CHECK (status IN ('DRAFT','APPROVED','SUPERSEDED')),
    approved_by   TEXT,
    approved_at   TIMESTAMPTZ,
    review_due    DATE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ws_plans_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_ws_plans_tenant_system_version UNIQUE (tenant_id, system_id, version),
    CONSTRAINT fk_ws_plans_system FOREIGN KEY (tenant_id, system_id)
        REFERENCES ws_systems (tenant_id, id),
    CONSTRAINT ck_ws_plans_approval CHECK (
        (status = 'APPROVED' AND approved_by IS NOT NULL AND btrim(approved_by) <> ''
            AND approved_at IS NOT NULL AND review_due IS NOT NULL)
        OR (status = 'SUPERSEDED' AND approved_by IS NOT NULL AND approved_at IS NOT NULL)
        OR (status = 'DRAFT' AND approved_by IS NULL)
    )
);
SELECT register_tenant_table('ws_safety_plans');

CREATE TABLE IF NOT EXISTS ws_safety_plan_risk_links (
    tenant_id               UUID NOT NULL,
    plan_id                 UUID NOT NULL,
    risk_id                 UUID NOT NULL,
    control_measure         TEXT NOT NULL CHECK (btrim(control_measure) <> ''),
    monitoring_parameter_key VARCHAR(120),
    corrective_action       TEXT NOT NULL CHECK (btrim(corrective_action) <> ''),
    PRIMARY KEY (tenant_id, plan_id, risk_id),
    CONSTRAINT fk_ws_links_plan FOREIGN KEY (tenant_id, plan_id)
        REFERENCES ws_safety_plans (tenant_id, id),
    CONSTRAINT fk_ws_links_risk FOREIGN KEY (tenant_id, risk_id)
        REFERENCES cert_risks (tenant_id, id)
);
SELECT register_tenant_table('ws_safety_plan_risk_links');

-- ---------------------------------------------------------------------
-- Relatorio anual de qualidade da agua (Decreto 5.440/2005, art. 3o, III)
-- Um por ligacao e ano.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ws_annual_reports (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    consumer_unit_id UUID NOT NULL,
    system_id        UUID NOT NULL,
    ref_year         INTEGER NOT NULL CHECK (ref_year BETWEEN 2000 AND 2100),
    status           VARCHAR(8) NOT NULL CHECK (status IN ('DRAFT','READY','ISSUED')),
    legal_basis      TEXT NOT NULL CHECK (legal_basis = 'Decreto 5.440/2005, art. 3º, III'),
    generated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ws_reports_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_ws_reports_tenant_unit_year UNIQUE (tenant_id, consumer_unit_id, ref_year),
    CONSTRAINT fk_ws_reports_system FOREIGN KEY (tenant_id, system_id)
        REFERENCES ws_systems (tenant_id, id)
);
SELECT register_tenant_table('ws_annual_reports');

CREATE TABLE IF NOT EXISTS ws_annual_report_lines (
    tenant_id      UUID NOT NULL,
    report_id      UUID NOT NULL,
    parameter_key  VARCHAR(120) NOT NULL,
    months_present INTEGER NOT NULL CHECK (months_present BETWEEN 0 AND 12),
    planned        INTEGER NOT NULL CHECK (planned >= 0),
    collected      INTEGER NOT NULL CHECK (collected >= 0),
    analyzed       INTEGER NOT NULL CHECK (analyzed >= 0),
    conforming     INTEGER NOT NULL CHECK (conforming >= 0),
    non_conforming INTEGER NOT NULL CHECK (non_conforming >= 0),
    indeterminate  INTEGER NOT NULL CHECK (indeterminate >= 0),
    PRIMARY KEY (tenant_id, report_id, parameter_key),
    CONSTRAINT fk_ws_report_lines_report FOREIGN KEY (tenant_id, report_id)
        REFERENCES ws_annual_reports (tenant_id, id),
    CHECK (conforming + non_conforming + indeterminate <= analyzed)
);
SELECT register_tenant_table('ws_annual_report_lines');

CREATE TABLE IF NOT EXISTS ws_annual_report_months (
    tenant_id UUID NOT NULL,
    report_id UUID NOT NULL,
    ref_month DATE NOT NULL,
    present   BOOLEAN NOT NULL,
    PRIMARY KEY (tenant_id, report_id, ref_month),
    CONSTRAINT fk_ws_report_months_report FOREIGN KEY (tenant_id, report_id)
        REFERENCES ws_annual_reports (tenant_id, id),
    CHECK (EXTRACT(DAY FROM ref_month) = 1)
);
SELECT register_tenant_table('ws_annual_report_months');

-- Linhas do bloco mensal da P2.24 transportadas (sem remontagem)
CREATE TABLE IF NOT EXISTS ws_annual_report_blocks (
    tenant_id      UUID NOT NULL,
    report_id      UUID NOT NULL,
    reference_month DATE NOT NULL,
    parameter_key  VARCHAR(120) NOT NULL,
    parameter_name VARCHAR(200),
    unit           VARCHAR(40),
    result_text    TEXT NOT NULL CHECK (btrim(result_text) <> ''),
    reference_text TEXT,
    PRIMARY KEY (tenant_id, report_id, reference_month, parameter_key),
    CONSTRAINT fk_ws_report_blocks_report FOREIGN KEY (tenant_id, report_id)
        REFERENCES ws_annual_reports (tenant_id, id),
    CHECK (EXTRACT(DAY FROM reference_month) = 1)
);
SELECT register_tenant_table('ws_annual_report_blocks');

-- Limpezas de reservatorio do ano (ED-89/OPE-07, so com laudo)
CREATE TABLE IF NOT EXISTS ws_annual_report_cleanings (
    tenant_id       UUID NOT NULL,
    report_id       UUID NOT NULL,
    reservoir_code  VARCHAR(60) NOT NULL,
    reservoir_name  VARCHAR(200),
    executed_on     DATE NOT NULL,
    report_ref      TEXT NOT NULL CHECK (btrim(report_ref) <> ''),
    product_item_code VARCHAR(100),
    PRIMARY KEY (tenant_id, report_id, reservoir_code, executed_on, report_ref),
    CONSTRAINT fk_ws_report_cleanings_report FOREIGN KEY (tenant_id, report_id)
        REFERENCES ws_annual_reports (tenant_id, id)
);
SELECT register_tenant_table('ws_annual_report_cleanings');

-- Emissoes: prova da P2.32 + tentativa/resultado da P3.5 (sem motor proprio)
CREATE TABLE IF NOT EXISTS ws_annual_report_issuances (
    id                       UUID PRIMARY KEY,
    tenant_id                UUID NOT NULL,
    report_id                UUID NOT NULL,
    legal_proof_ref          TEXT NOT NULL CHECK (btrim(legal_proof_ref) <> ''),
    content_catalog_version  INTEGER NOT NULL,
    message_catalog_version  INTEGER NOT NULL,
    notification_attempt_id  UUID,
    outcome                  VARCHAR(20) NOT NULL CHECK (outcome IN ('ISSUED','DELIVERY_NOT_SENT')),
    delivery_reason          TEXT,
    issued_at                TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_ws_issuances_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_ws_issuances_report FOREIGN KEY (tenant_id, report_id)
        REFERENCES ws_annual_reports (tenant_id, id),
    CONSTRAINT ck_ws_issuances_outcome CHECK (
        (outcome = 'ISSUED' AND notification_attempt_id IS NOT NULL)
        OR (outcome = 'DELIVERY_NOT_SENT' AND delivery_reason IS NOT NULL AND btrim(delivery_reason) <> '')
    )
);
CREATE INDEX IF NOT EXISTS idx_ws_issuances_report ON ws_annual_report_issuances (tenant_id, report_id);
SELECT register_tenant_table('ws_annual_report_issuances');

-- ---------------------------------------------------------------------
-- Carta de Servicos (Lei 13.460/2017) — publicacao pela P3.20
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ws_service_charters (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    version      INTEGER NOT NULL CHECK (version >= 1),
    status       VARCHAR(12) NOT NULL CHECK (status IN ('DRAFT','PUBLISHED','SUPERSEDED')),
    portal_ref   TEXT,
    published_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ws_charters_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_ws_charters_tenant_version UNIQUE (tenant_id, version),
    CONSTRAINT ck_ws_charters_publication CHECK (
        (status = 'PUBLISHED' AND portal_ref IS NOT NULL AND btrim(portal_ref) <> '' AND published_at IS NOT NULL)
        OR (status = 'SUPERSEDED' AND portal_ref IS NOT NULL AND published_at IS NOT NULL)
        OR (status = 'DRAFT' AND portal_ref IS NULL)
    )
);
SELECT register_tenant_table('ws_service_charters');

CREATE TABLE IF NOT EXISTS ws_service_charter_entries (
    tenant_id        UUID NOT NULL,
    charter_id       UUID NOT NULL,
    seq              INTEGER NOT NULL CHECK (seq >= 1),
    service_name     VARCHAR(200) NOT NULL,
    description      TEXT NOT NULL CHECK (btrim(description) <> ''),
    deadline_declared TEXT,
    PRIMARY KEY (tenant_id, charter_id, seq),
    CONSTRAINT fk_ws_charter_entries_charter FOREIGN KEY (tenant_id, charter_id)
        REFERENCES ws_service_charters (tenant_id, id)
);
SELECT register_tenant_table('ws_service_charter_entries');

CREATE TABLE IF NOT EXISTS ws_service_charter_entry_items (
    tenant_id UUID NOT NULL,
    charter_id UUID NOT NULL,
    seq       INTEGER NOT NULL,
    item_kind VARCHAR(12) NOT NULL CHECK (item_kind IN ('REQUIREMENT','STEP','CHANNEL')),
    item_text TEXT NOT NULL CHECK (btrim(item_text) <> ''),
    PRIMARY KEY (tenant_id, charter_id, seq, item_kind, item_text),
    CONSTRAINT fk_ws_charter_items_entry FOREIGN KEY (tenant_id, charter_id, seq)
        REFERENCES ws_service_charter_entries (tenant_id, charter_id, seq)
);
SELECT register_tenant_table('ws_service_charter_entry_items');

-- ---------------------------------------------------------------------
-- Travas de estado terminal do relatorio anual (correcao D1 da
-- revisao da ED-79): constraint triggers DIFERIDAS — avaliadas no
-- COMMIT, quando as linhas-filhas escritas na mesma transacao
-- (meses presentes, registro de emissao) ja existem. Sem elas, um
-- INSERT/UPDATE direto podia nascer READY vazio ou ISSUED sem
-- emissao (ataques SQL da revisao, secao 4).
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION ws_guard_report_terminal_state() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status IN ('READY', 'ISSUED') THEN
        IF (SELECT COUNT(*) FROM ws_annual_report_months m
             WHERE m.tenant_id = NEW.tenant_id AND m.report_id = NEW.id
               AND m.present AND EXTRACT(YEAR FROM m.ref_month) = NEW.ref_year) <> 12 THEN
            RAISE EXCEPTION 'ws guard (ED-79 D1): relatorio % % sem os 12 meses presentes', NEW.id, NEW.status;
        END IF;
    END IF;
    IF NEW.status = 'ISSUED' THEN
        IF NOT EXISTS (SELECT 1 FROM ws_annual_report_issuances i
                        WHERE i.tenant_id = NEW.tenant_id AND i.report_id = NEW.id
                          AND i.outcome = 'ISSUED') THEN
            RAISE EXCEPTION 'ws guard (ED-79 D1): relatorio % ISSUED sem registro de emissao ISSUED', NEW.id;
        END IF;
    END IF;
    RETURN NULL;
END;
$$;
DROP TRIGGER IF EXISTS trg_ws_reports_terminal_guard ON ws_annual_reports;
CREATE CONSTRAINT TRIGGER trg_ws_reports_terminal_guard
    AFTER INSERT OR UPDATE ON ws_annual_reports
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION ws_guard_report_terminal_state();

CREATE OR REPLACE FUNCTION ws_guard_issuance_terminal_state() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.outcome = 'ISSUED' THEN
        IF NOT EXISTS (SELECT 1 FROM ws_annual_reports r
                        WHERE r.tenant_id = NEW.tenant_id AND r.id = NEW.report_id
                          AND r.status = 'ISSUED') THEN
            RAISE EXCEPTION 'ws guard (ED-79 D1): emissao ISSUED % de relatorio nao emitido', NEW.id;
        END IF;
    END IF;
    RETURN NULL;
END;
$$;
DROP TRIGGER IF EXISTS trg_ws_issuances_terminal_guard ON ws_annual_report_issuances;
CREATE CONSTRAINT TRIGGER trg_ws_issuances_terminal_guard
    AFTER INSERT OR UPDATE ON ws_annual_report_issuances
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION ws_guard_issuance_terminal_state();
