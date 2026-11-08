-- ============================================================================
-- P3.32 — Qualidade cadastral continua (CAD-98) — RASCUNHO DE MIGRATION
-- ----------------------------------------------------------------------------
-- ESTE ARQUIVO NAO E UMA MIGRATION APLICAVEL AINDA: o mapa de versoes do
-- plano v3.0 (secao 3) NAO reserva numero fixo para a P3.32 — a linha diz
-- "regras e fila de qualidade cadastral (P3.32): vem depois da P3.12, pela
-- sequencia global com registry no nome". Pela regra da tarefa e do plano,
-- NENHUM numero foi inventado: na integracao, este conteudo entra como
--   V<proximo da sequencia global>__registry_cadastral_quality.sql
-- no location de tenant do modulo ziipsan-registry, DEPOIS da P3.12 fechar
-- o outOfOrder. Os nomes de tabela/coluna abaixo sao os das entidades JPA
-- entregues (CadastralQualityRunEntity, CadastralQualityFindingEntity,
-- CadastralQualityScoreEntity) — mudar aqui exige mudar la.
--
-- Convencoes seguidas (das migrations reais do registry, ex. V3_1/V3_12):
-- UUID PK com uuid_generate_v4(), tenant_id em toda tabela, UNIQUE
-- (tenant_id, id) para FK composta, timestamps, e as colunas version e
-- tenant_schema NAO sao declaradas aqui: quem as acrescenta e o
-- register_tenant_table(...) (como na V3_1), que tambem liga RLS/auditoria.
-- Nenhum catalogo e semeado.
-- ============================================================================

-- Varredura (uma execucao do motor)
CREATE TABLE IF NOT EXISTS cadastral_quality_runs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    reference_date DATE NOT NULL,
    units_evaluated INT NOT NULL CHECK (units_evaluated >= 0),
    customers_evaluated INT NOT NULL CHECK (customers_evaluated >= 0),
    findings_count INT NOT NULL CHECK (findings_count >= 0),
    skipped_rules_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE cadastral_quality_runs ADD CONSTRAINT uq_cq_runs_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_cq_runs_reference ON cadastral_quality_runs(tenant_id, reference_date DESC);

-- Achado = item da fila de correcao
CREATE TABLE IF NOT EXISTS cadastral_quality_findings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    run_id UUID NOT NULL,
    finding_key VARCHAR(160) NOT NULL,
    rule_code VARCHAR(40) NOT NULL CHECK (rule_code IN (
        'DOCUMENT_MISSING', 'DOCUMENT_INVALID', 'DOCUMENT_IRREGULAR',
        'CONTACT_UNVERIFIED', 'CATEGORY_CNAE_MISMATCH', 'ECONOMIES_AREA_MISMATCH',
        'ACTIVE_UNIT_WITHOUT_METER', 'UNIT_WITHOUT_READING',
        'METER_VERIFICATION_EXPIRED', 'ADDRESS_WITHOUT_COORDINATES',
        'ADDRESS_OUT_OF_SECTOR', 'HOLDER_POSSIBLY_DECEASED',
        'DUPLICATE_DOCUMENT', 'DUPLICATE_INSCRIPTION',
        'UNIT_WITHOUT_USER', 'BILL_UNDELIVERED')),
    severity VARCHAR(10) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    unit_id UUID,
    customer_id UUID,
    detail VARCHAR(500) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'BATCHED', 'RESOLVED', 'DISMISSED')),
    service_order_id UUID,
    dismiss_reason VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (unit_id IS NOT NULL OR customer_id IS NOT NULL),
    -- Correcao so por OS: RESOLVED exige a OS; BATCHED idem (a fila confere
    -- no servico que e a MESMA OS que agrupou).
    CHECK (status NOT IN ('BATCHED', 'RESOLVED') OR service_order_id IS NOT NULL)
);
ALTER TABLE cadastral_quality_findings ADD CONSTRAINT uq_cq_findings_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE cadastral_quality_findings ADD CONSTRAINT fk_cq_findings_run
    FOREIGN KEY (tenant_id, run_id) REFERENCES cadastral_quality_runs(tenant_id, id);
ALTER TABLE cadastral_quality_findings ADD CONSTRAINT fk_cq_findings_unit
    FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
ALTER TABLE cadastral_quality_findings ADD CONSTRAINT fk_cq_findings_customer
    FOREIGN KEY (tenant_id, customer_id) REFERENCES customers(tenant_id, id);
-- SEM FK para service_order_id aqui: service_orders e criada pela V9_1 do
-- core (ordem entre locations e da integracao, como na V3_12 -> V9_3).
CREATE INDEX IF NOT EXISTS idx_cq_findings_key ON cadastral_quality_findings(tenant_id, finding_key);
CREATE INDEX IF NOT EXISTS idx_cq_findings_status ON cadastral_quality_findings(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_cq_findings_order ON cadastral_quality_findings(tenant_id, service_order_id);

-- Pontuacao por grupo e varredura
CREATE TABLE IF NOT EXISTS cadastral_quality_scores (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    run_id UUID NOT NULL,
    group_type VARCHAR(10) NOT NULL CHECK (group_type IN ('TENANT', 'ROUTE', 'SECTOR')),
    group_id UUID,
    total_units INT NOT NULL CHECK (total_units > 0),
    units_with_findings INT NOT NULL CHECK (units_with_findings >= 0),
    findings_count INT NOT NULL CHECK (findings_count >= 0),
    penalty_points INT NOT NULL CHECK (penalty_points >= 0),
    score NUMERIC(6,2) NOT NULL CHECK (score BETWEEN 0 AND 100),
    completeness_percent NUMERIC(6,2) NOT NULL CHECK (completeness_percent BETWEEN 0 AND 100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK ((group_type = 'TENANT') = (group_id IS NULL))
);
ALTER TABLE cadastral_quality_scores ADD CONSTRAINT uq_cq_scores_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE cadastral_quality_scores ADD CONSTRAINT fk_cq_scores_run
    FOREIGN KEY (tenant_id, run_id) REFERENCES cadastral_quality_runs(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_cq_scores_group ON cadastral_quality_scores(tenant_id, group_type, group_id);

SELECT register_tenant_table('cadastral_quality_runs');
SELECT register_tenant_table('cadastral_quality_findings');
SELECT register_tenant_table('cadastral_quality_scores');

-- ============================================================================
-- Views do nucleo de relatorios da P3.22 (CadastralQualityReports):
-- colunas identicas as declaradas nas definicoes QUAL-001/002/003.
-- ============================================================================

CREATE OR REPLACE VIEW rpt_cadastral_quality_route AS
SELECT r.reference_date AS referencia,
       s.group_id::text AS rota_id,
       s.total_units AS total_unidades,
       s.units_with_findings AS unidades_com_achados,
       s.findings_count AS achados,
       s.penalty_points AS pontos_penalidade,
       s.score AS pontuacao,
       s.completeness_percent AS completude,
       s.tenant_id AS tenant_id
  FROM cadastral_quality_scores s
  JOIN cadastral_quality_runs r ON r.tenant_id = s.tenant_id AND r.id = s.run_id
 WHERE s.group_type = 'ROUTE';

CREATE OR REPLACE VIEW rpt_cadastral_quality_sector AS
SELECT r.reference_date AS referencia,
       s.group_id::text AS setor_id,
       s.total_units AS total_unidades,
       s.units_with_findings AS unidades_com_achados,
       s.findings_count AS achados,
       s.penalty_points AS pontos_penalidade,
       s.score AS pontuacao,
       s.completeness_percent AS completude,
       s.tenant_id AS tenant_id
  FROM cadastral_quality_scores s
  JOIN cadastral_quality_runs r ON r.tenant_id = s.tenant_id AND r.id = s.run_id
 WHERE s.group_type = 'SECTOR';

CREATE OR REPLACE VIEW rpt_cadastral_quality_trend AS
SELECT r.reference_date AS referencia,
       s.total_units AS unidades_avaliadas,
       s.findings_count AS achados,
       s.penalty_points AS pontos_penalidade,
       s.score AS pontuacao,
       s.completeness_percent AS completude,
       s.tenant_id AS tenant_id
  FROM cadastral_quality_scores s
  JOIN cadastral_quality_runs r ON r.tenant_id = s.tenant_id AND r.id = s.run_id
 WHERE s.group_type = 'TENANT';
