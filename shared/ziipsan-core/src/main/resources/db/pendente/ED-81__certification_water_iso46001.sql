-- ============================================================================
-- ED-81 (PENDENTE DE NUMERACAO): uso proprio de agua, ISO 46001 —
-- unidades de uso operacional, fonte do balanco da ED-85 (OPE-05),
-- revisao do uso, linha de base e plano de acao ligado a P3.31.
-- SQL sem numero: prefixo ED-81__ em db/pendente/ (regra da cascata).
-- Faixa Flyway propria do plano SS3: V21_x (certification, pacotes por
-- norma sobre V21_1..V21_3; ED-69 a ED-77, ED-80 a ED-82) — o numero
-- concreto so e atribuido na integracao (sequencia global da P3.12).
-- Depende de: V3_1/V3_6 (consumer_units da P1.3, com UNIQUE
-- (tenant_id, id)), V21_1 (ativacao da norma da P1.32), V21_3
-- (cert_objectives da P3.31, com UNIQUE (tenant_id, id)).
-- FRONTEIRAS:
--  * a unidade de uso operacional NASCE SOBRE consumer_units (FK
--    composta abaixo) — nenhum cadastro paralelo de unidade;
--  * o hidrometro e da ED-14 (operations/metering): meter_id e
--    referencia por id, SEM FK entre modulos (padrao ED-85 para
--    pontos da ED-17); a instalacao vigente e conferida pela
--    aplicacao via porta da hidrometria, fail-closed;
--  * a fonte do uso proprio vem da ED-85 (intelligence/losses):
--    balance_ref referencia o snapshot loss_monthly_balances, SEM
--    FK entre modulos e SEM nenhuma coluna que recalcule o
--    balanco. VERDADE DA FONTE (correcao D1 da revisao): o
--    snapshot NAO tem split operacional/emergencial — ele guarda
--    exported_m3 e o authorized_unbilled_m3 UNICO/agregado, que
--    INCLUI o social (SOCIAL_TANKER). Os componentes guardados
--    aqui chegam JA DECOMPOSTOS pelo adapter (P-ED81-01):
--    operacional = soma de NETWORK_FLUSHING+RESERVOIR_CLEANING e
--    emergencial = soma de FIREFIGHTING, ambos de
--    loss_authorized_unbilled_volumes por DMC/mes de occurred_on,
--    SOCIAL_TANKER excluido; exportada = exported_m3 do snapshot.
--    Decompor por categoria nao recalcula o balanco;
--  * o plano de acao liga-se a cert_objectives da P3.31 por FK
--    composta real (mesmo modulo certification, pai com UNIQUE).
-- ATIVACAO: cert.iso46001.enabled (classe d, padrao false) e
-- cert.iso46001.edition do core V3 + escopo cert.scope_statement e
-- escopo de instalacao declarado por linha (installation_ref).
-- ISO 46001:2019 esta EM REVISAO e o projeto da edicao 2 e so
-- ACOMPANHADO: a tabela de acompanhamento trava, por CHECK, que
-- linha DRAFT_TRACKED nunca e adotavel; NENHUM requisito de edicao
-- nao publicada e guardado (sem coluna de requisito/clausula aqui).
-- SEM DADO SEMEADO: linha de base, criterio de significancia,
-- finalidades e economias esperadas sao do tenant ([A DEFINIR]
-- quando ausentes); nenhum INSERT neste arquivo. SEM DELETE:
-- desligar a norma nao apaga registro (ADR-0041).
-- FK COMPOSTA: toda FK (tenant_id, X) tem a UNIQUE (tenant_id, id)
-- correspondente declarada nas tabelas novas; os pais pre-existentes
-- (consumer_units, cert_objectives) ja tem a sua nas migrations deles.
-- ============================================================================
SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- Acompanhamento de edicoes no catalogo (aceite: 2019 em revisao;
-- projeto da edicao 2 acompanhado). Linhas sao gravadas pela
-- aplicacao a partir do catalogo da P1.32 — nada semeado aqui.
CREATE TABLE IF NOT EXISTS cert_w46001_edition_tracking (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    edition_label TEXT NOT NULL CHECK (btrim(edition_label) <> ''),
    edition_status TEXT NOT NULL CHECK (edition_status IN
        ('PUBLISHED_UNDER_REVISION','DRAFT_TRACKED')),
    adoptable BOOLEAN NOT NULL DEFAULT FALSE,
    tracked_on DATE NOT NULL,
    CONSTRAINT uq_w46001_edition_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_w46001_edition_tenant_label UNIQUE (tenant_id, edition_label),
    CONSTRAINT ck_w46001_draft_not_adoptable CHECK (
        edition_status <> 'DRAFT_TRACKED' OR adoptable = FALSE)
);
SELECT register_tenant_table('cert_w46001_edition_tracking');

-- Unidade de uso operacional sobre o cadastro da P1.3, medida pela
-- hidrometria da ED-14 (meter_id referenciado; instalacao conferida
-- pela aplicacao). Uma so ficha de uso proprio por unidade/tenant.
CREATE TABLE IF NOT EXISTS cert_w46001_use_units (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    consumer_unit_id UUID NOT NULL,
    installation_ref TEXT NOT NULL CHECK (btrim(installation_ref) <> ''),
    meter_id UUID NOT NULL,
    use_nature TEXT NOT NULL CHECK (use_nature IN ('OPERATIONAL','EMERGENCY')),
    purpose_code TEXT NOT NULL CHECK (btrim(purpose_code) <> ''),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT uq_w46001_units_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_w46001_units_tenant_unit UNIQUE (tenant_id, consumer_unit_id),
    CONSTRAINT fk_w46001_units_consumer_unit FOREIGN KEY (tenant_id, consumer_unit_id)
        REFERENCES consumer_units (tenant_id, id)
);
SELECT register_tenant_table('cert_w46001_use_units');

-- Fonte do uso proprio: componentes da ED-85 (OPE-05) JA
-- DECOMPOSTOS pelo adapter (correcao D1; contrato na P-ED81-01):
-- operacional/emergencial vem da soma por categoria de
-- loss_authorized_unbilled_volumes (social SOCIAL_TANKER
-- excluido); exportada vem do exported_m3 do snapshot
-- loss_monthly_balances. O total e a soma dos componentes
-- declarados (coluna gerada), nao um recalculo do balanco:
-- nenhuma entrada, perda ou ILI da ED-85 e refeita aqui.
CREATE TABLE IF NOT EXISTS cert_w46001_source_snapshots (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    balance_ref UUID NOT NULL,
    dmc_profile_ref UUID NOT NULL,
    period_year INTEGER NOT NULL CHECK (period_year BETWEEN 1900 AND 2100),
    period_month INTEGER NOT NULL CHECK (period_month BETWEEN 1 AND 12),
    operational_m3 NUMERIC NOT NULL CHECK (operational_m3 >= 0),
    emergency_m3 NUMERIC NOT NULL CHECK (emergency_m3 >= 0),
    exported_m3 NUMERIC NOT NULL CHECK (exported_m3 >= 0),
    total_m3 NUMERIC GENERATED ALWAYS AS (operational_m3 + emergency_m3 + exported_m3) STORED,
    CONSTRAINT uq_w46001_sources_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_w46001_sources_tenant_balance UNIQUE (tenant_id, balance_ref)
);
SELECT register_tenant_table('cert_w46001_source_snapshots');

-- Revisao do uso: periodo, instalacao do escopo, criterio de
-- significancia declarado pelo tenant e linha de base declarada
-- (rotulo + valor juntos, ou ambos nulos = [A DEFINIR]; revisao
-- CLOSED exige a base, pelo CHECK).
CREATE TABLE IF NOT EXISTS cert_w46001_use_reviews (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    installation_ref TEXT NOT NULL CHECK (btrim(installation_ref) <> ''),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL CHECK (period_end >= period_start),
    source_snapshot_id UUID NOT NULL,
    significance_criterion TEXT NOT NULL CHECK (btrim(significance_criterion) <> ''),
    significant_uses JSONB NOT NULL DEFAULT '[]'::jsonb
        CHECK (jsonb_typeof(significant_uses) = 'array'),
    baseline_period_label TEXT CHECK (baseline_period_label IS NULL OR btrim(baseline_period_label) <> ''),
    baseline_m3 NUMERIC CHECK (baseline_m3 IS NULL OR baseline_m3 >= 0),
    status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','CLOSED')),
    CONSTRAINT uq_w46001_reviews_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_w46001_baseline_pair CHECK (
        (baseline_period_label IS NULL) = (baseline_m3 IS NULL)),
    CONSTRAINT ck_w46001_closed_needs_baseline CHECK (
        status <> 'CLOSED' OR baseline_m3 IS NOT NULL),
    CONSTRAINT fk_w46001_reviews_source FOREIGN KEY (tenant_id, source_snapshot_id)
        REFERENCES cert_w46001_source_snapshots (tenant_id, id)
);
SELECT register_tenant_table('cert_w46001_use_reviews');

-- UNICIDADE DE REVISAO FECHADA POR ESCOPO (correcao D3 da revisao):
-- no maximo UMA revisao CLOSED por (tenant, instalacao, periodo) —
-- sem isto, duas baselines diferentes para o mesmo escopo/periodo
-- eram aceitas e a view devolvia duas linhas ambiguas. Indice
-- PARCIAL: rascunhos (DRAFT) do mesmo escopo/periodo seguem
-- permitidos; so o fechamento e unico.
CREATE UNIQUE INDEX IF NOT EXISTS uq_w46001_reviews_one_closed_per_scope
    ON cert_w46001_use_reviews (tenant_id, installation_ref, period_start, period_end)
    WHERE status = 'CLOSED';

-- Plano de acao ligado ao objetivo da P3.31 (FK composta real).
CREATE TABLE IF NOT EXISTS cert_w46001_action_plans (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    objective_id UUID NOT NULL,
    installation_ref TEXT NOT NULL CHECK (btrim(installation_ref) <> ''),
    title TEXT NOT NULL CHECK (btrim(title) <> ''),
    CONSTRAINT uq_w46001_plans_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_w46001_plans_objective FOREIGN KEY (tenant_id, objective_id)
        REFERENCES cert_objectives (tenant_id, id)
);
SELECT register_tenant_table('cert_w46001_action_plans');

-- Acoes do plano: responsavel e prazo obrigatorios; economia
-- esperada e estimativa do tenant (nula = [A DEFINIR]).
CREATE TABLE IF NOT EXISTS cert_w46001_action_items (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    description TEXT NOT NULL CHECK (btrim(description) <> ''),
    owner TEXT NOT NULL CHECK (btrim(owner) <> ''),
    due_date DATE NOT NULL,
    expected_saving_m3 NUMERIC CHECK (expected_saving_m3 IS NULL OR expected_saving_m3 >= 0),
    status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','DONE','CANCELLED')),
    CONSTRAINT uq_w46001_items_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_w46001_items_plan FOREIGN KEY (tenant_id, plan_id)
        REFERENCES cert_w46001_action_plans (tenant_id, id)
);
SELECT register_tenant_table('cert_w46001_action_items');

-- Leitura do uso proprio por instalacao/periodo (revisao fechada).
CREATE OR REPLACE VIEW cert_w46001_own_use_by_installation AS
SELECT r.tenant_id, r.installation_ref, r.period_start, r.period_end,
       s.operational_m3, s.emergency_m3, s.exported_m3, s.total_m3,
       r.baseline_period_label, r.baseline_m3,
       CASE WHEN r.baseline_m3 IS NULL OR r.baseline_m3 = 0 THEN NULL
            ELSE (s.total_m3 - r.baseline_m3) / r.baseline_m3 * 100.0
       END AS variation_vs_baseline_pct
  FROM cert_w46001_use_reviews r
  JOIN cert_w46001_source_snapshots s
    ON s.tenant_id = r.tenant_id AND s.id = r.source_snapshot_id
 WHERE r.status = 'CLOSED';
