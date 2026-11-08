-- ============================================================================
-- ED-96 (PENDENTE DE NUMERACAO): instrumento contratual regulatorio do
-- prestador (GES-02; deps ED-56 e P3.31; NR 8/ANA, NR 5/ANA, NR 3/ANA;
-- Decreto 11.598/2023).
--
-- POR QUE PENDENTE: SQL sem numero confirmado livre na serie tenant
-- do core. Regra da cascata: NENHUM numero inventado — este arquivo
-- entra como db/pendente/ com o prefixo ED-96__ e recebe o numero da
-- sequencia global na integracao (precedentes: ED-56, ED-92, ED-99).
--
-- Depende de: cert_risks da P3.31 (V21_3 — tem UNIQUE (tenant_id, id))
-- e da entidade asset da P1.35 (inventario da ED-56 — a FK composta
-- para asset (tenant_id, id) exige a mesma UNIQUE na migration real
-- da P1.35, a confirmar na integracao, como na ED-56).
--
-- LICAO DA C3 (aplicada): TODA tabela referenciada por FK composta
-- (tenant_id, X) tem UNIQUE (tenant_id, id) declarada NESTE arquivo:
-- reg_contracts e referenciada por metas, aditivos, documentos,
-- matriz, vinculos e paineis — a UNIQUE esta na tabela-mae.
--
-- O QUE ESTE SQL FAZ:
--   (1) reg_contracts: o registro por municipio e por servico, com
--       partes, ato de regulacao, vigencia, clausula de reequilibrio
--       e indenizacao (NR 3/ANA) como referencias declaradas —
--       NENHUM texto de clausula e semeado;
--   (2) reg_contract_goals: metas contratuais declaradas (as metas
--       de universalizacao por municipio sao da ED-77);
--   (3) reg_contract_amendments: aditivos, inclusive GOAL_UPDATE_NR8;
--   (4) reg_contract_documents: metadado dos documentos;
--   (5) reg_contract_risk_matrix: linhas da matriz (NR 5/ANA) que
--       REFERENCIAM cert_risks da P3.31 — primeiro uso, sem segundo
--       registro de riscos;
--   (6) reg_contract_reversible_assets: vinculo com os bens
--       reversiveis do inventario da ED-56 (so o id do ativo, DAD-26);
--   (7) reg_financial_capacity_snapshots: fotografia dos agregados
--       da exportacao do ERP e dos quatro indicadores calculados
--       (Decreto 11.598/2023), por exercicio.
--
-- NENHUM DADO DE NEGOCIO E SEMEADO: instrumentos, metas, aditivos e
-- paineis sao do tenant e nascem do uso.
-- CONVENCOES: mensagens sem diacriticos; RLS via register_tenant_table.
--
-- CORRECAO POS-REVISAO (ED-96, defeitos D1-D3 da revisao independente):
--   D1: a unicidade do instrumento e por municipio x servico x
--       referencia (o mesmo instrumento rende UM registro POR SERVICO,
--       como no modelo Java) — nao mais (tenant_id, contract_ref);
--   D2: a clausula de reequilibrio exige no banco so a REFERENCIA
--       (nao-branca), como no record Java RebalancingClause — a
--       descricao e opcional; a indenizacao segue exigindo referencia
--       nao-branca E metodo declarado, como no Java;
--   D3: a sobreposicao de vigencia e recusada tambem no BANCO, por
--       constraint de exclusao (btree_gist + daterange) no MESMO
--       recorte do servico Java: tenant x municipio x servico (o tipo
--       do instrumento NAO entra no recorte — o servico recusa PPP
--       sobreposto a CONCESSION no mesmo municipio+servico, cf. o
--       teste overlappingValidityForSameMunicipalityAndServiceIsRefused).
-- ============================================================================

-- D3: igualdade GiST para uuid/texto/varchar + && de daterange.
CREATE EXTENSION IF NOT EXISTS btree_gist;

SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- (1) O instrumento ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reg_contracts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    municipality_code VARCHAR(7) NOT NULL CHECK (municipality_code ~ '^[0-9]{7}$'),
    municipality_name TEXT NOT NULL CHECK (NULLIF(btrim(municipality_name), '') IS NOT NULL),
    service_kind TEXT NOT NULL CHECK (service_kind IN ('WATER_SUPPLY', 'SEWAGE', 'SOLID_WASTE', 'URBAN_DRAINAGE')),
    contract_type TEXT NOT NULL CHECK (contract_type IN ('PROGRAM_CONTRACT', 'CONCESSION', 'PPP', 'SUBDELEGATION', 'DIRECT_PROVISION')),
    contract_ref TEXT NOT NULL CHECK (NULLIF(btrim(contract_ref), '') IS NOT NULL),
    grantor TEXT NOT NULL CHECK (NULLIF(btrim(grantor), '') IS NOT NULL),
    provider TEXT NOT NULL CHECK (NULLIF(btrim(provider), '') IS NOT NULL),
    regulator_agency TEXT,
    regulation_act TEXT NOT NULL CHECK (NULLIF(btrim(regulation_act), '') IS NOT NULL),
    valid_from DATE NOT NULL,
    valid_to DATE,
    rebalancing_clause_ref TEXT,
    rebalancing_clause_description TEXT,
    indemnity_clause_ref TEXT,
    indemnity_method_description TEXT,
    CHECK (valid_to IS NULL OR valid_to > valid_from),
    -- D2: reequilibrio = so a referencia (nao-branca), como no Java; descricao opcional.
    CHECK ((rebalancing_clause_ref IS NULL AND rebalancing_clause_description IS NULL)
        OR (NULLIF(btrim(rebalancing_clause_ref), '') IS NOT NULL)),
    -- Indenizacao (NR 3/ANA) = referencia nao-branca E metodo declarado, como no Java.
    CHECK ((indemnity_clause_ref IS NULL AND indemnity_method_description IS NULL)
        OR (NULLIF(btrim(indemnity_clause_ref), '') IS NOT NULL
            AND NULLIF(btrim(indemnity_method_description), '') IS NOT NULL)),
    CONSTRAINT uq_reg_contracts_tenant_id UNIQUE (tenant_id, id),
    -- D1: mesmo instrumento = um registro por servico (municipio x servico x referencia).
    CONSTRAINT uq_reg_contracts_tenant_ref UNIQUE (tenant_id, municipality_code, service_kind, contract_ref),
    -- D3: sem sobreposicao de vigencia no recorte do servico Java (tenant x municipio x servico);
    -- daterange '[]' = extremos inclusivos como em RegulatoryContract.Validity.overlaps;
    -- valid_to NULL = vigencia aberta (sem fim), como o LocalDate.MAX do Java.
    CONSTRAINT ex_reg_contracts_no_overlap EXCLUDE USING gist (
        tenant_id WITH =,
        municipality_code WITH =,
        service_kind WITH =,
        daterange(valid_from, valid_to, '[]') WITH &&
    )
);
SELECT register_tenant_table('reg_contracts');

-- (2) Metas contratuais declaradas ----------------------------------------------
CREATE TABLE IF NOT EXISTS reg_contract_goals (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    description TEXT NOT NULL CHECK (NULLIF(btrim(description), '') IS NOT NULL),
    indicator_code TEXT,
    target_value NUMERIC,
    target_unit TEXT,
    due_year INTEGER CHECK (due_year IS NULL OR (due_year BETWEEN 1900 AND 2200)),
    amendment_id UUID,
    CHECK ((target_value IS NULL) = (target_unit IS NULL)),
    CONSTRAINT uq_reg_contract_goals_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_reg_contract_goals_contract
        FOREIGN KEY (tenant_id, contract_id) REFERENCES reg_contracts (tenant_id, id)
);
SELECT register_tenant_table('reg_contract_goals');

-- (3) Aditivos -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reg_contract_amendments (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    amendment_ref TEXT NOT NULL CHECK (NULLIF(btrim(amendment_ref), '') IS NOT NULL),
    signed_on DATE NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('GENERAL', 'GOAL_UPDATE_NR8', 'TERM_EXTENSION', 'ECONOMIC_REBALANCING', 'OTHER_DECLARED')),
    description TEXT NOT NULL CHECK (NULLIF(btrim(description), '') IS NOT NULL),
    document_ref TEXT,
    CONSTRAINT uq_reg_contract_amendments_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_reg_contract_amendments_tenant_ref UNIQUE (tenant_id, contract_id, amendment_ref),
    CONSTRAINT fk_reg_contract_amendments_contract
        FOREIGN KEY (tenant_id, contract_id) REFERENCES reg_contracts (tenant_id, id)
);
SELECT register_tenant_table('reg_contract_amendments');

-- Amarragem meta -> aditivo (a tabela de aditivos ja existe neste ponto;
-- a UNIQUE (tenant_id, id) dela, declarada acima, sustenta a FK composta).
ALTER TABLE reg_contract_goals
    ADD CONSTRAINT fk_reg_contract_goals_amendment
        FOREIGN KEY (tenant_id, amendment_id) REFERENCES reg_contract_amendments (tenant_id, id);

-- (4) Documentos -----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reg_contract_documents (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('CONTRACT', 'AMENDMENT', 'REGULATION_ACT', 'RISK_MATRIX', 'OTHER')),
    title TEXT NOT NULL CHECK (NULLIF(btrim(title), '') IS NOT NULL),
    storage_ref TEXT NOT NULL CHECK (NULLIF(btrim(storage_ref), '') IS NOT NULL),
    registered_on DATE NOT NULL,
    CONSTRAINT uq_reg_contract_documents_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_reg_contract_documents_contract
        FOREIGN KEY (tenant_id, contract_id) REFERENCES reg_contracts (tenant_id, id)
);
SELECT register_tenant_table('reg_contract_documents');

-- (5) Matriz de riscos (NR 5/ANA) sobre cert_risks da P3.31 -----------------------
CREATE TABLE IF NOT EXISTS reg_contract_risk_matrix (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    risk_id UUID NOT NULL,
    allocation TEXT NOT NULL CHECK (allocation IN ('PROVIDER', 'GRANTING_AUTHORITY', 'SHARED')),
    matrix_ref TEXT NOT NULL CHECK (NULLIF(btrim(matrix_ref), '') IS NOT NULL),
    CONSTRAINT uq_reg_contract_risk_matrix_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_reg_contract_risk_matrix_risk UNIQUE (tenant_id, contract_id, risk_id),
    CONSTRAINT fk_reg_contract_risk_matrix_contract
        FOREIGN KEY (tenant_id, contract_id) REFERENCES reg_contracts (tenant_id, id),
    CONSTRAINT fk_reg_contract_risk_matrix_risk
        FOREIGN KEY (tenant_id, risk_id) REFERENCES cert_risks (tenant_id, id)
);
SELECT register_tenant_table('reg_contract_risk_matrix');

-- (6) Vinculo com bens reversiveis do inventario da ED-56 -------------------------
CREATE TABLE IF NOT EXISTS reg_contract_reversible_assets (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    CONSTRAINT uq_reg_contract_reversible_assets_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_reg_contract_reversible_assets_link UNIQUE (tenant_id, contract_id, asset_id),
    CONSTRAINT fk_reg_contract_reversible_assets_contract
        FOREIGN KEY (tenant_id, contract_id) REFERENCES reg_contracts (tenant_id, id),
    CONSTRAINT fk_reg_contract_reversible_assets_asset
        FOREIGN KEY (tenant_id, asset_id) REFERENCES asset (tenant_id, id)
);
SELECT register_tenant_table('reg_contract_reversible_assets');

-- (7) Fotografia do painel de capacidade (Decreto 11.598/2023) -------------------
CREATE TABLE IF NOT EXISTS reg_financial_capacity_snapshots (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    exercise INTEGER NOT NULL CHECK (exercise BETWEEN 1900 AND 2200),
    export_ref TEXT NOT NULL CHECK (NULLIF(btrim(export_ref), '') IS NOT NULL),
    net_income NUMERIC NOT NULL,
    depreciation NUMERIC NOT NULL CHECK (depreciation >= 0),
    amortization NUMERIC NOT NULL CHECK (amortization >= 0),
    net_revenue NUMERIC NOT NULL,
    total_liabilities NUMERIC NOT NULL,
    total_assets NUMERIC NOT NULL,
    equity NUMERIC NOT NULL,
    available_cash_flow NUMERIC NOT NULL,
    debt_service_due NUMERIC NOT NULL,
    margin_value NUMERIC,
    leverage_value NUMERIC,
    return_on_equity_value NUMERIC,
    cash_sufficiency_value NUMERIC,
    CONSTRAINT uq_reg_financial_capacity_snapshots_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_reg_financial_capacity_exercise UNIQUE (tenant_id, contract_id, exercise),
    CONSTRAINT fk_reg_financial_capacity_contract
        FOREIGN KEY (tenant_id, contract_id) REFERENCES reg_contracts (tenant_id, id)
);
SELECT register_tenant_table('reg_financial_capacity_snapshots');
