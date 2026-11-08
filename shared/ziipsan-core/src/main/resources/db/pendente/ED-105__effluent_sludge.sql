-- ED-105 — Efluente da ETE e lodo de ETA e ETE: obrigacao legal para
-- todo tenant com estacao (JOR-16, OPE-25, RAS-17;
-- CONAMA 430/2011 e CONAMA 498/2020)
-- SQL PENDENTE de numeracao Flyway (regra da cascata: nenhum numero
-- inventado; prefixo ED-105__ em db/pendente/). Na integracao:
-- atribuir o proximo slot livre e conferir as FKs externas adiadas
-- (unidades operacionais da ED-87, abaixo).
--
-- REGRA DURA: este SQL NAO semeia NENHUM limite de efluente, destino
-- de lodo, origem isenta ou frequencia de automonitoramento. O
-- catalogo de padrao (effluent_standards) nasce VAZIO e so recebe
-- linhas pela carga revisada pelo responsavel tecnico a partir do
-- catalogo de normas; waste.sludge_destination_catalog e
-- waste.mtr_exempt_origins sao PARAMETROS do tenant, nao dado
-- semeado. Os 14 valores da P3.9 nao sao tocados por esta tarefa.
--
-- CONAMA 430/2011 EM REVISAO: a nova resolucao foi aprovada em
-- 02/09/2026 e NAO publicada (fato do aceite) — por isso o CHECK de
-- publication_status so admite 'PUBLISHED': linha de norma nao
-- publicada e recusada no banco como no nucleo (EffluentStandard);
-- depois da publicacao, os limites novos entram como NOVA versao
-- do catalogo com data de efeito (effective_from futura).
--
-- LICAO DA C3: toda FK composta (tenant_id, X) aponta para pai com
-- PRIMARY KEY (tenant_id, id) ou (tenant_id, version) — a unicidade
-- esta na PK de cada tabela pai desta entrega. FKs para tabelas de
-- outras entregas (ops_station_units da ED-87) NAO sao declaradas
-- aqui: ficam como pendencia de integracao nomeada no relatorio,
-- no padrao das FKs adiadas da ED-87.
--
-- IDEMPOTENCIA: todos os CREATE TABLE usam IF NOT EXISTS
-- (padrao ED-87/D4) — a reaplicacao integral nao falha.
--
-- CORRECAO D3 (revisao da ED-105): vigencias SOBREPOSTAS do mesmo
-- parametro na mesma esfera sao recusadas tambem no banco, como o
-- nucleo ja fazia (EffluentCatalog): exclusion constraint sobre o
-- intervalo de vigencia (daterange + btree_gist) em
-- effluent_standards. effective_to NULL = vigencia aberta
-- (daterange sem teto); intervalos apenas ADJACENTES (a linha
-- antiga fecha em effective_to e a nova comeca no dia seguinte)
-- NAO se sobrepoem e continuam aceitos — a sucessao legitima e a
-- mesma do nucleo (limites inclusivos nos dois extremos).

-- ---------------------------------------------------------------------
-- Catalogo de padrao de efluente, versionado (federal + estadual)
-- ---------------------------------------------------------------------
-- Extensao da exclusion constraint de vigencia (D3): igualdade de
-- uuid/text em indice GiST. IF NOT EXISTS mantem a reaplicacao
-- idempotente.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE IF NOT EXISTS effluent_catalog_versions (
    tenant_id    UUID NOT NULL,
    version      TEXT NOT NULL,
    loaded_by    TEXT NOT NULL,
    loaded_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, version)
);
SELECT register_tenant_table('effluent_catalog_versions');

CREATE TABLE IF NOT EXISTS effluent_standards (
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
        REFERENCES effluent_catalog_versions (tenant_id, version),
    CHECK ((jurisdiction = 'FEDERAL' AND state_code = '')
        OR (jurisdiction = 'STATE' AND state_code <> '')),
    CHECK (reviewed_by_technical_responsible = FALSE OR reviewer_ref IS NOT NULL),
    CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CHECK (
        (comparator = 'MAX'   AND limit_max IS NOT NULL AND limit_min IS NULL AND qualitative_expected IS NULL) OR
        (comparator = 'MIN'   AND limit_min IS NOT NULL AND limit_max IS NULL AND qualitative_expected IS NULL) OR
        (comparator = 'RANGE' AND limit_min IS NOT NULL AND limit_max IS NOT NULL AND limit_min <= limit_max AND qualitative_expected IS NULL) OR
        (comparator = 'QUALITATIVE_EQUALS' AND qualitative_expected IS NOT NULL AND limit_min IS NULL AND limit_max IS NULL)
    ),
    -- D3: nenhuma segunda vigencia sobreposta do mesmo parametro,
    -- na mesma versao do catalogo e na mesma esfera (federal ou
    -- estadual+UF), dentro do mesmo tenant. Espelha a recusa do
    -- EffluentCatalog no nucleo.
    EXCLUDE USING gist (
        tenant_id WITH =,
        catalog_version WITH =,
        parameter_key WITH =,
        jurisdiction WITH =,
        state_code WITH =,
        daterange(effective_from, effective_to, '[]') WITH &&
    )
);
SELECT register_tenant_table('effluent_standards');

-- ---------------------------------------------------------------------
-- Plano de automonitoramento do efluente da ETE (coleta/amostragem
-- pelo laboratorio e pela cadeia de custodia da ED-50)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS effluent_monitoring_plans (
    tenant_id            UUID NOT NULL,
    id                   UUID NOT NULL,
    wastewater_unit_id   UUID NOT NULL,
    period_start         DATE NOT NULL,  -- primeiro dia do mes inicial
    period_end           DATE NOT NULL,  -- primeiro dia do mes final
    catalog_version      TEXT NOT NULL,
    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, catalog_version)
        REFERENCES effluent_catalog_versions (tenant_id, version),
    CHECK (period_end >= period_start)
    -- FK (tenant_id, wastewater_unit_id) -> ops_station_units
    -- (tenant_id, id) da ED-87: conferir na integracao (FK adiada).
);
SELECT register_tenant_table('effluent_monitoring_plans');

CREATE TABLE IF NOT EXISTS effluent_monitoring_plan_lines (
    tenant_id          UUID NOT NULL,
    plan_id            UUID NOT NULL,
    sampling_point_code TEXT NOT NULL,
    parameter_key      TEXT NOT NULL,
    planned_count      INTEGER NOT NULL CHECK (planned_count > 0),
    PRIMARY KEY (tenant_id, plan_id, sampling_point_code, parameter_key),
    FOREIGN KEY (tenant_id, plan_id) REFERENCES effluent_monitoring_plans (tenant_id, id)
);
SELECT register_tenant_table('effluent_monitoring_plan_lines');

-- Eficiencia de remocao de DBO apurada por ETE e periodo (fato
-- calculado sobre o automonitoramento; as medias sao as do calculo).
CREATE TABLE IF NOT EXISTS effluent_bod_assessments (
    tenant_id          UUID NOT NULL,
    id                 UUID NOT NULL,
    wastewater_unit_id UUID NOT NULL,
    period_start       DATE NOT NULL,
    period_end         DATE NOT NULL,
    pair_count         INTEGER NOT NULL CHECK (pair_count > 0),
    mean_influent      NUMERIC NOT NULL CHECK (mean_influent > 0),
    mean_effluent      NUMERIC NOT NULL CHECK (mean_effluent >= 0),
    removal_percent    NUMERIC NOT NULL,
    PRIMARY KEY (tenant_id, id),
    CHECK (period_end >= period_start)
    -- FK da unidade -> ops_station_units (ED-87): adiada, integracao.
);
SELECT register_tenant_table('effluent_bod_assessments');

-- ---------------------------------------------------------------------
-- Lodo e residuos da estacao: geracao, armazenamento, saida (OPE-25)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sludge_records (
    tenant_id            UUID NOT NULL,
    id                   UUID NOT NULL,
    unit_id              UUID NOT NULL,
    unit_kind            TEXT NOT NULL
        CHECK (unit_kind IN ('WTP', 'WWTP')),
    residue_kind         TEXT NOT NULL
        CHECK (residue_kind IN ('SLUDGE', 'SCREENINGS', 'GRIT')),
    origin_code          TEXT NOT NULL,
    quantity             NUMERIC NOT NULL CHECK (quantity > 0),
    quantity_unit        TEXT NOT NULL,
    quantity_method      TEXT NOT NULL
        CHECK (quantity_method IN ('WEIGHED', 'VOLUME_ESTIMATED')),
    generated_at         TIMESTAMPTZ NOT NULL,
    shift_log_id         UUID,
    status               TEXT NOT NULL DEFAULT 'GENERATED'
        CHECK (status IN ('GENERATED', 'STORED', 'DISPATCHED', 'CLOSED')),
    storage_location     TEXT,
    stored_at            TIMESTAMPTZ,
    transporter_ref      TEXT,
    destination_code     TEXT,
    transport_document_ref TEXT,
    dispatched_at        TIMESTAMPTZ,
    closed_at            TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, id),
    CHECK (residue_kind = 'SLUDGE' OR unit_kind = 'WWTP'),
    CHECK ((status IN ('STORED', 'DISPATCHED', 'CLOSED'))
        = (storage_location IS NOT NULL AND stored_at IS NOT NULL)),
    CHECK ((status IN ('DISPATCHED', 'CLOSED'))
        = (transporter_ref IS NOT NULL AND destination_code IS NOT NULL
           AND transport_document_ref IS NOT NULL AND dispatched_at IS NOT NULL)),
    CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))
    -- FKs de unit_id/shift_log_id -> ops_station_units/ops_shift_logs
    -- (ED-87): adiadas, conferir na integracao.
);
SELECT register_tenant_table('sludge_records');

-- Recepcao de lodo de fossa na ETE (volume e origem declarados).
CREATE TABLE IF NOT EXISTS septage_receptions (
    tenant_id          UUID NOT NULL,
    id                 UUID NOT NULL,
    receiving_unit_id  UUID NOT NULL,
    volume             NUMERIC NOT NULL CHECK (volume > 0),
    volume_unit        TEXT NOT NULL,
    origin_code        TEXT NOT NULL,
    origin_description TEXT NOT NULL,
    carrier_ref        TEXT NOT NULL,
    received_at        TIMESTAMPTZ NOT NULL,
    shift_log_id       UUID,
    PRIMARY KEY (tenant_id, id)
    -- FK de receiving_unit_id -> ops_station_units (ED-87): adiada.
);
SELECT register_tenant_table('septage_receptions');

-- Vinculo do lodo com o MTR do gerador (RAS-17): manual via agenda
-- da ED-98 enquanto a ED-104 nao for liberada; um vinculo por saida.
CREATE TABLE IF NOT EXISTS sludge_mtr_links (
    tenant_id       UUID NOT NULL,
    id              UUID NOT NULL,
    sludge_record_id UUID NOT NULL,
    status          TEXT NOT NULL
        CHECK (status IN ('PENDING_MANUAL', 'EMITTED_MANUALLY', 'LINKED_AUTOMATIC', 'EXEMPT')),
    source          TEXT NOT NULL
        CHECK (source IN ('MANUAL_ED98', 'AUTOMATIC_ED104', 'EXEMPTION')),
    agenda_item_ref TEXT,
    mtr_number      TEXT,
    emitted_at      TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, sludge_record_id),
    FOREIGN KEY (tenant_id, sludge_record_id) REFERENCES sludge_records (tenant_id, id),
    CHECK (status <> 'PENDING_MANUAL'
        OR (agenda_item_ref IS NOT NULL AND mtr_number IS NULL AND source = 'MANUAL_ED98')),
    CHECK (status <> 'EXEMPT'
        OR (source = 'EXEMPTION' AND mtr_number IS NULL AND agenda_item_ref IS NULL)),
    CHECK (status NOT IN ('EMITTED_MANUALLY', 'LINKED_AUTOMATIC') OR mtr_number IS NOT NULL),
    CHECK (status <> 'LINKED_AUTOMATIC' OR source = 'AUTOMATIC_ED104')
);
SELECT register_tenant_table('sludge_mtr_links');

-- ---------------------------------------------------------------------
-- Parametros do tenant (definitions para conferencia contra o
-- catalogo de parametros vigente na integracao — NAO atribuir slot
-- sem conferir)
--   waste.sludge_destination_catalog  sem valor de fabrica: codigos
--                               de destino aceitos do tenant (OPE-25).
--   waste.mtr_exempt_origins          sem valor de fabrica: codigos
--                               de origem isentos de MTR; a ausencia
--                               e erro — declarar [] = nenhuma
--                               origem isenta (RAS-17).
-- ---------------------------------------------------------------------
