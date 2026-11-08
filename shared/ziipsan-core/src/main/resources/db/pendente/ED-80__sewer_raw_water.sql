-- ED-80 — Pacote de esgoto e agua bruta (outorga/licenca/captacao,
-- Declaracao de Carga Poluidora, biossolido e portao de reuso)
-- SQL PENDENTE de numeracao Flyway (regra da cascata: nenhum numero
-- inventado; prefixo ED-80__ em db/pendente/). PROIBIDO usar
-- V102/V103 (reservados) e proibido tocar em V6_4 — nenhum dos dois
-- e usado aqui. Na integracao: atribuir o proximo slot livre da
-- sequencia global.
--
-- Fontes do aceite (declaradas, sem valor inventado):
--   Lei 9.433/1997; Resolucao ANA 236/2024 (rios de dominio da
--   Uniao); CONAMA 498/2020 (biossolido — criterios sao linhas
--   DECLARADAS pelo responsavel tecnico, nenhuma linha semeada);
--   DCP a partir do automonitoramento da ED-105, com a base
--   normativa conferida no catalogo dela depois da publicacao da
--   norma que substitui a CONAMA 430/2011 (em revisao — por isso
--   successor_basis_confirmed nasce FALSE e so vira TRUE por
--   declaracao conferida, nunca por default de carga);
--   reuso so depois da publicacao da resolucao conjunta
--   CNRH/CONAMA aprovada em 01/09/2026 (a confirmar no catalogo):
--   reuse_readiness_checks guarda a PRONTIDAO declarada, nenhum
--   parametro de uso e semeado. CNRH 54/2005 e 121/2010 sao
--   referencias anteriores (texto declarado, nao seed de regra).
--
-- REGRA DURA: este SQL NAO semeia outorga, licenca, criterio,
-- limite ou prazo algum. As tabelas nascem vazias.
-- FKs compostas (tenant_id, id) apenas para pais DESTA entrega e
-- para sludge_records da ED-105 (PK (tenant_id, id) — aplicar a
-- ED-105 antes ou junto na integracao). system_code da ED-79 e
-- legal_requirement_id da P3.31 sao referencias inter-modulo SEM
-- FK (padrao das entregas vizinhas), declaradas aqui.
-- IDEMPOTENCIA: CREATE TABLE IF NOT EXISTS em tudo.

CREATE TABLE IF NOT EXISTS raw_water_grants (
    tenant_id          UUID NOT NULL,
    id                 UUID NOT NULL,
    grant_number       TEXT NOT NULL,
    issuing_body       TEXT NOT NULL,
    legal_basis_ref    TEXT NOT NULL,
    water_domain       TEXT NOT NULL CHECK (water_domain IN ('UNION_FEDERAL', 'STATE')),
    valid_from         DATE NOT NULL,
    valid_to           DATE NOT NULL,
    granted_flow_value NUMERIC,
    granted_flow_unit  TEXT,
    PRIMARY KEY (tenant_id, id),
    CHECK (valid_to >= valid_from),
    CHECK ((granted_flow_value IS NULL AND granted_flow_unit IS NULL)
        OR (granted_flow_value IS NOT NULL AND granted_flow_unit IS NOT NULL AND granted_flow_value >= 0))
);
SELECT register_tenant_table('raw_water_grants');

CREATE TABLE IF NOT EXISTS raw_water_grant_conditions (
    tenant_id   UUID NOT NULL,
    grant_id    UUID NOT NULL,
    seq         INT  NOT NULL,
    description TEXT NOT NULL,
    fulfilled   BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (tenant_id, grant_id, seq),
    FOREIGN KEY (tenant_id, grant_id) REFERENCES raw_water_grants (tenant_id, id)
);
SELECT register_tenant_table('raw_water_grant_conditions');

CREATE TABLE IF NOT EXISTS raw_water_licenses (
    tenant_id     UUID NOT NULL,
    id            UUID NOT NULL,
    license_number TEXT NOT NULL,
    license_kind  TEXT NOT NULL,
    issuing_body  TEXT NOT NULL,
    valid_from    DATE NOT NULL,
    valid_to      DATE NOT NULL,
    PRIMARY KEY (tenant_id, id),
    CHECK (valid_to >= valid_from)
);
SELECT register_tenant_table('raw_water_licenses');

CREATE TABLE IF NOT EXISTS raw_water_license_conditions (
    tenant_id   UUID NOT NULL,
    license_id  UUID NOT NULL,
    seq         INT  NOT NULL,
    description TEXT NOT NULL,
    fulfilled   BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (tenant_id, license_id, seq),
    FOREIGN KEY (tenant_id, license_id) REFERENCES raw_water_licenses (tenant_id, id)
);
SELECT register_tenant_table('raw_water_license_conditions');

CREATE TABLE IF NOT EXISTS raw_water_intakes (
    tenant_id            UUID NOT NULL,
    id                   UUID NOT NULL,
    system_code          TEXT NOT NULL,  -- ED-79 (sem FK inter-modulo, declarado)
    code                 TEXT NOT NULL,
    name                 TEXT NOT NULL,
    water_body_name      TEXT NOT NULL,
    source_kind          TEXT NOT NULL CHECK (source_kind IN ('SURFACE', 'GROUNDWATER', 'MIXED')),
    grant_id             UUID NOT NULL,
    license_id           UUID NOT NULL,
    legal_requirement_id UUID NOT NULL,  -- P3.31 (sem FK inter-modulo, declarado)
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, code),
    FOREIGN KEY (tenant_id, grant_id) REFERENCES raw_water_grants (tenant_id, id),
    FOREIGN KEY (tenant_id, license_id) REFERENCES raw_water_licenses (tenant_id, id)
);
SELECT register_tenant_table('raw_water_intakes');

CREATE TABLE IF NOT EXISTS pollutant_load_declarations (
    tenant_id                        UUID NOT NULL,
    id                               UUID NOT NULL,
    wastewater_unit_id               UUID NOT NULL,
    period_start                     DATE NOT NULL,
    period_end                       DATE NOT NULL,
    effluent_catalog_version         TEXT NOT NULL,  -- catalogo da ED-105 (referencia declarada)
    successor_basis_confirmed        BOOLEAN NOT NULL,
    effluent_volume_m3               NUMERIC NOT NULL CHECK (effluent_volume_m3 > 0),
    bod_pair_count                   INT NOT NULL CHECK (bod_pair_count > 0),
    bod_removal_percent              NUMERIC NOT NULL,
    total_load_kg                    NUMERIC NOT NULL CHECK (total_load_kg >= 0),
    declarant_ref                    TEXT NOT NULL,
    declared_on                      DATE NOT NULL,
    PRIMARY KEY (tenant_id, id),
    CHECK (period_end >= period_start),
    -- Uma DCP persistida so existe com a base sucessora confirmada
    -- no catalogo (o nucleo recusa gerar sem ela — DcpService).
    CHECK (successor_basis_confirmed = TRUE)
);
SELECT register_tenant_table('pollutant_load_declarations');

CREATE TABLE IF NOT EXISTS pollutant_load_lines (
    tenant_id       UUID NOT NULL,
    declaration_id  UUID NOT NULL,
    parameter_key   TEXT NOT NULL,
    mean_concentration NUMERIC NOT NULL CHECK (mean_concentration >= 0),
    concentration_unit TEXT NOT NULL,
    load_kg         NUMERIC NOT NULL CHECK (load_kg >= 0),
    norm_reference  TEXT NOT NULL,  -- referencia DO CATALOGO da ED-105
    PRIMARY KEY (tenant_id, declaration_id, parameter_key),
    FOREIGN KEY (tenant_id, declaration_id) REFERENCES pollutant_load_declarations (tenant_id, id)
);
SELECT register_tenant_table('pollutant_load_lines');

CREATE TABLE IF NOT EXISTS biosolid_lots (
    tenant_id        UUID NOT NULL,
    id               UUID NOT NULL,
    sludge_record_id UUID NOT NULL,
    source_unit_id   UUID NOT NULL,
    quantity         NUMERIC NOT NULL CHECK (quantity > 0),
    quantity_unit    TEXT NOT NULL,
    status           TEXT NOT NULL CHECK (status IN ('ELIGIBLE', 'NOT_ELIGIBLE', 'INDETERMINATE')),
    criteria_declared INT NOT NULL CHECK (criteria_declared > 0),
    criteria_evaluated INT NOT NULL CHECK (criteria_evaluated >= 0),
    reason           TEXT NOT NULL,
    PRIMARY KEY (tenant_id, id),
    CHECK (criteria_evaluated <= criteria_declared),
    CHECK ((status = 'ELIGIBLE' AND criteria_evaluated = criteria_declared)
        OR status <> 'ELIGIBLE'),
    FOREIGN KEY (tenant_id, sludge_record_id) REFERENCES sludge_records (tenant_id, id)
);
SELECT register_tenant_table('biosolid_lots');

CREATE TABLE IF NOT EXISTS reuse_readiness_checks (
    tenant_id          UUID NOT NULL,
    id                 UUID NOT NULL,
    status             TEXT NOT NULL CHECK (status IN ('NOT_CODIFIED', 'CODIFIED_BASIS_CONFIRMED')),
    successor_reference TEXT,
    reason             TEXT NOT NULL,
    checked_on         DATE NOT NULL,
    PRIMARY KEY (tenant_id, id),
    CHECK ((status = 'CODIFIED_BASIS_CONFIRMED' AND successor_reference IS NOT NULL)
        OR status = 'NOT_CODIFIED')
);
SELECT register_tenant_table('reuse_readiness_checks');
