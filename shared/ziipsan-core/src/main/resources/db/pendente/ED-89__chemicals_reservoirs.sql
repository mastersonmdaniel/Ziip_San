-- ED-89 — Insumos quimicos de tratamento e reservatorios (OPE-06, OPE-07)
-- SQL PENDENTE de numeracao Flyway (regra da cascata: nenhum numero
-- inventado). Na integracao: atribuir o proximo slot livre da historia
-- tenant do modulo shared/ziipsan-core e renomear o arquivo.
--
-- REGRAS DURAS desta entrega:
--  * NENHUM dado e semeado: nenhum produto, dosagem, tolerancia, lista
--    de controlados, licenca ou intervalo nasce neste SQL. A lista de
--    produtos controlados (chem_controlled_list_*) nasce VAZIA e so
--    recebe linhas pela carga normativa do Ziip.
--  * TODA FK composta (tenant_id, X) tem no pai a UNIQUE (tenant_id, id)
--    correspondente (licao da C3 / correcao G1 da ED-92) — declarada
--    explicitamente em TODAS as tabelas de tenant abaixo.
--  * Saldo/estoque NAO e duplicado: o produto referencia o item da
--    ED-58 por codigo (inv_items.tenant_id, inv_items.code); lote e
--    saldo seguem na ED-58 — aqui ficam lote RECEBIDO com laudo, o
--    consumo, o controle PF e a limpeza de reservatorio.
--  * Os 14 valores da P3.9 NAO sao tocados por esta entrega.

-- ---------------------------------------------------------------------
-- Lista GLOBAL de produtos controlados (Policia Federal) — sem tenant:
-- carga normativa versionada do Ziip (Lei 10.357/2001; Portaria MJSP
-- 204/2022, alterada pela Portaria MJSP 223/2022). NASCE VAZIA.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chem_controlled_list_versions (
    version        TEXT PRIMARY KEY,
    norm_reference TEXT NOT NULL,
    effective_from DATE NOT NULL,
    effective_to   DATE,
    CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE TABLE IF NOT EXISTS chem_controlled_list_entries (
    list_version TEXT NOT NULL REFERENCES chem_controlled_list_versions (version),
    product_key  TEXT NOT NULL,
    product_name TEXT NOT NULL,
    PRIMARY KEY (list_version, product_key)
);

-- ---------------------------------------------------------------------
-- Produto quimico de tratamento (sobre o item CHEMICAL da ED-58)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chem_products (
    id                         UUID PRIMARY KEY,
    tenant_id                  UUID NOT NULL,
    inventory_item_code        VARCHAR(100) NOT NULL,
    name                       VARCHAR(200) NOT NULL,
    storage_kind               VARCHAR(12) NOT NULL CHECK (storage_kind IN ('SILO','TANK','CYLINDER')),
    storage_location_code      VARCHAR(60) NOT NULL,
    safety_data_sheet_ref      TEXT NOT NULL CHECK (btrim(safety_data_sheet_ref) <> ''),
    safety_data_sheet_revision DATE NOT NULL,
    active                     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_chem_products_tenant_item UNIQUE (tenant_id, inventory_item_code),
    CONSTRAINT uq_chem_products_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('chem_products');

-- ---------------------------------------------------------------------
-- Recebimento por NF-e (cabecalho) e lotes recebidos com laudo
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chem_receipts (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    nfe_access_key   VARCHAR(60) NOT NULL,
    supplier_document VARCHAR(20),
    received_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_chem_receipts_tenant_key UNIQUE (tenant_id, nfe_access_key),
    CONSTRAINT uq_chem_receipts_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('chem_receipts');

CREATE TABLE IF NOT EXISTS chem_lot_receipts (
    id                       UUID PRIMARY KEY,
    tenant_id                UUID NOT NULL,
    product_id               UUID NOT NULL,
    receipt_id               UUID,
    lot_code                 VARCHAR(80) NOT NULL,
    expiry_date              DATE NOT NULL,
    supplier_certificate_ref TEXT NOT NULL CHECK (btrim(supplier_certificate_ref) <> ''),
    qty                      NUMERIC(19,4) NOT NULL CHECK (qty > 0),
    received_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_chem_lot_receipts_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_chem_lot_product FOREIGN KEY (tenant_id, product_id)
        REFERENCES chem_products (tenant_id, id),
    CONSTRAINT fk_chem_lot_receipt FOREIGN KEY (tenant_id, receipt_id)
        REFERENCES chem_receipts (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_chem_lot_expiry ON chem_lot_receipts (tenant_id, expiry_date);
SELECT register_tenant_table('chem_lot_receipts');

-- ---------------------------------------------------------------------
-- Consumo diario e conciliacao (dosagem DECLARADA por lancamento)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chem_daily_consumption (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    product_id          UUID NOT NULL,
    location_code       VARCHAR(60),
    treatment_unit_code VARCHAR(80) NOT NULL,
    consumption_date    DATE NOT NULL,
    treated_volume_m3   NUMERIC(19,4) NOT NULL CHECK (treated_volume_m3 >= 0),
    dosage_per_m3       NUMERIC(19,6) NOT NULL CHECK (dosage_per_m3 >= 0),
    actual_qty          NUMERIC(19,4) NOT NULL CHECK (actual_qty >= 0),
    CONSTRAINT uq_chem_consumption_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_chem_consumption_product FOREIGN KEY (tenant_id, product_id)
        REFERENCES chem_products (tenant_id, id),
    CHECK (treated_volume_m3 > 0 OR dosage_per_m3 = 0)
);
CREATE INDEX IF NOT EXISTS idx_chem_consumption_day ON chem_daily_consumption (tenant_id, product_id, consumption_date);
SELECT register_tenant_table('chem_daily_consumption');

-- ---------------------------------------------------------------------
-- Controle Policia Federal: conferencia previa, cadastros, licencas,
-- estado e informacoes periodicas (prazo declarado [SEM FONTE])
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chem_pf_checks (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    list_version      TEXT NOT NULL REFERENCES chem_controlled_list_versions (version),
    checked_on        DATE NOT NULL,
    matched_item_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_chem_pf_checks_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('chem_pf_checks');

CREATE TABLE IF NOT EXISTS chem_pf_registrations (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    product_id      UUID NOT NULL,
    registration_ref TEXT NOT NULL CHECK (btrim(registration_ref) <> ''),
    registered_on   DATE NOT NULL,
    CONSTRAINT uq_chem_pf_reg_tenant_item UNIQUE (tenant_id, product_id),
    CONSTRAINT uq_chem_pf_reg_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_chem_pf_reg_product FOREIGN KEY (tenant_id, product_id)
        REFERENCES chem_products (tenant_id, id)
);
SELECT register_tenant_table('chem_pf_registrations');

CREATE TABLE IF NOT EXISTS chem_pf_licences (
    id         UUID PRIMARY KEY,
    tenant_id  UUID NOT NULL,
    licence_ref TEXT NOT NULL,
    issued_on  DATE NOT NULL,
    expires_on DATE NOT NULL,
    CONSTRAINT uq_chem_pf_lic_tenant_id UNIQUE (tenant_id, id),
    CHECK (expires_on > issued_on)
);
SELECT register_tenant_table('chem_pf_licences');

CREATE TABLE IF NOT EXISTS chem_pf_states (
    tenant_id            UUID PRIMARY KEY,
    enabled              BOOLEAN NOT NULL DEFAULT FALSE,
    enabled_list_version TEXT REFERENCES chem_controlled_list_versions (version),
    enabled_at           TIMESTAMPTZ,
    CHECK (enabled = FALSE OR (enabled_list_version IS NOT NULL AND enabled_at IS NOT NULL))
);
SELECT register_tenant_table('chem_pf_states');

CREATE TABLE IF NOT EXISTS chem_pf_periodic_reports (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    period_label  VARCHAR(80) NOT NULL,
    due_on        DATE NOT NULL,
    submitted_on  DATE,
    submission_ref TEXT,
    CONSTRAINT uq_chem_pf_periodic_tenant_label UNIQUE (tenant_id, period_label),
    CONSTRAINT uq_chem_pf_periodic_tenant_id UNIQUE (tenant_id, id),
    CHECK ((submitted_on IS NULL) = (submission_ref IS NULL OR btrim(submission_ref) = ''))
);
SELECT register_tenant_table('chem_pf_periodic_reports');

-- ---------------------------------------------------------------------
-- Reservatorios, plano de limpeza, OS geradas e execucoes com laudo
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chem_reservoirs (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    code          VARCHAR(60) NOT NULL,
    name          VARCHAR(200) NOT NULL,
    supply_system VARCHAR(120) NOT NULL,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_chem_reservoirs_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_chem_reservoirs_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('chem_reservoirs');

CREATE TABLE IF NOT EXISTS chem_cleaning_plans (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    reservoir_id   UUID NOT NULL,
    interval_months INTEGER NOT NULL CHECK (interval_months > 0),
    anchor_on      DATE NOT NULL,
    last_cleaning_on DATE,
    next_due_on    DATE NOT NULL,
    CONSTRAINT uq_chem_plans_tenant_reservoir UNIQUE (tenant_id, reservoir_id),
    CONSTRAINT uq_chem_plans_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_chem_plans_reservoir FOREIGN KEY (tenant_id, reservoir_id)
        REFERENCES chem_reservoirs (tenant_id, id)
);
SELECT register_tenant_table('chem_cleaning_plans');

CREATE TABLE IF NOT EXISTS chem_cleaning_orders (
    tenant_id     UUID NOT NULL,
    reservoir_id  UUID NOT NULL,
    due_on        DATE NOT NULL,
    order_id      UUID NOT NULL,
    PRIMARY KEY (tenant_id, reservoir_id, due_on),
    CONSTRAINT fk_chem_orders_reservoir FOREIGN KEY (tenant_id, reservoir_id)
        REFERENCES chem_reservoirs (tenant_id, id)
);
SELECT register_tenant_table('chem_cleaning_orders');

CREATE TABLE IF NOT EXISTS chem_cleaning_executions (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    reservoir_id        UUID NOT NULL,
    order_id            UUID NOT NULL,
    executed_on         DATE NOT NULL,
    report_ref          TEXT NOT NULL CHECK (btrim(report_ref) <> ''),
    product_id          UUID NOT NULL,
    product_qty         NUMERIC(19,4) NOT NULL CHECK (product_qty > 0),
    discarded_volume_m3 NUMERIC(19,4) NOT NULL CHECK (discarded_volume_m3 >= 0),
    CONSTRAINT uq_chem_exec_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_chem_exec_reservoir FOREIGN KEY (tenant_id, reservoir_id)
        REFERENCES chem_reservoirs (tenant_id, id),
    CONSTRAINT fk_chem_exec_product FOREIGN KEY (tenant_id, product_id)
        REFERENCES chem_products (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_chem_exec_period ON chem_cleaning_executions (tenant_id, executed_on);
SELECT register_tenant_table('chem_cleaning_executions');

-- ---------------------------------------------------------------------
-- Parametros do tenant (definitions para conferencia contra o catalogo
-- de parametros vigente na integracao — NAO atribuir classe/slot sem
-- conferir; padrao ED-50). TODAS sem valor de fabrica:
--   chem.min_stock_days                     INTEGER dias minimos de
--       cobertura de estoque (chave nomeada pelo acceptance).
--   chem.controlled_products_list           TEXT versao vigente da
--       lista de controlados adotada pelo tenant [SEM FONTE: a lista
--       e carga normativa do Ziip, tabelas chem_controlled_list_*].
--   ops.reservoir_cleaning_interval_months  INTEGER periodicidade da
--       limpeza de reservatorio — valor da agencia, sem padrao de
--       norma confirmado (chave nomeada pelo acceptance).
--   chem.pf_control_enabled                 BOOLEAN controle opcional
--       PF por tenant — CHAVE PROPOSTA pela ED-89 (o acceptance pede o
--       controle opcional sem nomear a chave); confirmar na integracao.
--   chem.reconciliation_tolerance_pct       DECIMAL tolerancia (%) da
--       conciliacao dosagem x real — CHAVE PROPOSTA pela ED-89; sem
--       ela, a conciliacao reporta sem veredito. [A DEFINIR]
-- ---------------------------------------------------------------------
