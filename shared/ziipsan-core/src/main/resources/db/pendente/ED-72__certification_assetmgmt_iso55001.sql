-- ED-72 — Pacote ISO 55001 (gestão de ativos): inventário/ciclo de vida
-- sob a norma com prevalência NR/ANA, plano estratégico com critérios e
-- registro de decisões, plano de investimento/manutenção por ativo,
-- sinais preditivos, vínculos OPE-20/OPE-38 da ED-56 e autoavaliação de
-- cláusulas, sobre o núcleo das V21_1 (P1.32).
-- Status: PENDENTE DE NUMERAÇÃO — pela regra vigente do projeto vale só a
-- sequência global da P3.12 (V100+; último número usado: V101). Este SQL
-- fica em db/pendente/ com prefixo ED-72__ e NÃO recebe número inventado:
-- na integração/pouso, atribui-se o próximo número global livre,
-- atualiza-se migration-policy/published-tenant-migrations.txt e o
-- arquivo é movido para
-- db/migration/tenant/V<n>__certification_assetmgmt_iso55001.sql.
--
-- FRONTEIRA (aceite da ED-72; ADR-0041): a ativação da norma
-- (cert.iso55001.enabled / cert.iso55001.edition = "2024", escopo
-- declarado cert.scope_statement e aceite por pessoas diferentes) é do
-- núcleo da P1.32 (V21_1, core V3) — não é duplicada aqui. O cadastro do
-- ativo é da P1.35 (COE-05) e o inventário regulatório/risco é da ED-56 —
-- aqui guardam-se a visão sob a norma, os planos e os registros da
-- gestão de ativos. A frota NÃO entra (veículos são da ED-59).
--
-- SEM TEXTO DE NORMA: nenhuma tabela guarda título ou texto literal da
-- ISO 55001. Títulos de cláusula nascem '[A DEFINIR]' (title_pending)
-- até a curadoria do tenant/ED-76 declará-los em palavras próprias —
-- textos/títulos literais só com licença (pendência nomeada da ED-72).
--
-- SEM DELETE: desligar a norma não apaga registro (ADR-0041;
-- cert.deactivation_keeps_records travado em true). Nenhum comando
-- deste arquivo apaga linha de tabela alguma.
--
-- FK COMPOSTA (lição da cascata C3): toda FK (tenant_id, X) referencia
-- UNIQUE (tenant_id, ...) declarado na tabela pai ANTES da FK.

-- ======================================================================
-- 1. INVENTÁRIO E CICLO DE VIDA SOB A NORMA (visão COE-05 × ED-56)
-- ======================================================================
-- risk_* é CÓPIA LIDA do RiskView da ED-56 (P×C decomposto): o banco
-- impõe a mesma consistência da leitura do Java — excluído da régua não
-- tem P/score; presente, score = P×C. Nada aqui recalcula risco.

CREATE TABLE IF NOT EXISTS cert_i55001_asset_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    patrimonial_code VARCHAR(120) NOT NULL CHECK (btrim(patrimonial_code) <> ''),
    asset_kind VARCHAR(30) NOT NULL CHECK (asset_kind IN
        ('LINEAR', 'NON_LINEAR_EQUIPMENT', 'OTHER')),
    lifecycle VARCHAR(30) NOT NULL CHECK (lifecycle IN
        ('PLANNED', 'IN_OPERATION', 'UNDER_MAINTENANCE', 'DECOMMISSIONED', 'DISPOSED')),
    risk_probability INTEGER,
    risk_consequence INTEGER,
    risk_score INTEGER,
    risk_band VARCHAR(40),
    risk_excluded_reason TEXT,
    registered_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK ((risk_excluded_reason IS NOT NULL AND btrim(risk_excluded_reason) <> ''
            AND risk_probability IS NULL AND risk_score IS NULL)
        OR (risk_excluded_reason IS NULL AND risk_probability IS NOT NULL
            AND risk_consequence IS NOT NULL AND risk_score IS NOT NULL
            AND risk_score = risk_probability * risk_consequence
            AND risk_band IS NOT NULL AND btrim(risk_band) <> ''))
);
DO $$ BEGIN
    ALTER TABLE cert_i55001_asset_records
        ADD CONSTRAINT uq_i55001_asset_records_tenant_id UNIQUE (tenant_id, id);
EXCEPTION WHEN duplicate_object OR duplicate_table THEN NULL;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uq_i55001_asset_record
    ON cert_i55001_asset_records(tenant_id, asset_id);

CREATE TABLE IF NOT EXISTS cert_i55001_asset_fields (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    asset_record_id UUID NOT NULL,
    field_name VARCHAR(120) NOT NULL CHECK (btrim(field_name) <> ''),
    coe05_value TEXT,
    regulatory_value TEXT,
    effective_value TEXT NOT NULL CHECK (btrim(effective_value) <> ''),
    field_source VARCHAR(20) NOT NULL CHECK (field_source IN
        ('REGULATORY_NR_ANA', 'COE05_ONLY', 'REGULATORY_ONLY')),
    divergent BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    CHECK (coe05_value IS NOT NULL OR regulatory_value IS NOT NULL),
    CHECK (effective_value = COALESCE(regulatory_value, coe05_value)),
    CHECK (divergent = (coe05_value IS NOT NULL AND regulatory_value IS NOT NULL
        AND coe05_value <> regulatory_value)),
    CHECK (field_source = CASE
        WHEN regulatory_value IS NOT NULL AND coe05_value IS NOT NULL THEN 'REGULATORY_NR_ANA'
        WHEN regulatory_value IS NOT NULL THEN 'REGULATORY_ONLY'
        ELSE 'COE05_ONLY' END)
);
DO $$ BEGIN
    ALTER TABLE cert_i55001_asset_fields
        ADD CONSTRAINT fk_i55001_asset_fields_record
            FOREIGN KEY (tenant_id, asset_record_id)
            REFERENCES cert_i55001_asset_records(tenant_id, id);
EXCEPTION WHEN duplicate_object OR duplicate_table THEN NULL;
END $$;
CREATE INDEX IF NOT EXISTS idx_i55001_asset_fields_record
    ON cert_i55001_asset_fields(tenant_id, asset_record_id);

-- ======================================================================
-- 2. PLANO ESTRATÉGICO: CRITÉRIOS DE DECISÃO E REGISTRO DAS DECISÕES
-- ======================================================================
-- Cobertura dos quatro desfechos (RENEW/REPAIR/MAINTAIN/DISPOSE) é régua
-- do código (StrategicAssetPlan); o banco garante unicidade da chave de
-- critério por plano e a amarração decisão → critério do mesmo plano E
-- DO MESMO DESFECHO (correção D1 da revisão): UNIQUE quádruplo
-- (tenant_id, plan_id, criterion_key, outcome) nos critérios e FK
-- composta quádrupla das decisões para essa chave — espelho do
-- construtor canônico de DecisionRecord, que exige o critério-objeto.

CREATE TABLE IF NOT EXISTS cert_i55001_strategic_plans (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    plan_name TEXT NOT NULL CHECK (btrim(plan_name) <> ''),
    objectives JSONB NOT NULL,
    approved_by UUID NOT NULL,
    approved_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
DO $$ BEGIN
    ALTER TABLE cert_i55001_strategic_plans
        ADD CONSTRAINT uq_i55001_strategic_plans_tenant_id UNIQUE (tenant_id, id);
EXCEPTION WHEN duplicate_object OR duplicate_table THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS cert_i55001_decision_criteria (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    criterion_key VARCHAR(120) NOT NULL CHECK (btrim(criterion_key) <> ''),
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN
        ('RENEW', 'REPAIR', 'MAINTAIN', 'DISPOSE')),
    condition_description TEXT NOT NULL CHECK (btrim(condition_description) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID
);
DO $$ BEGIN
    ALTER TABLE cert_i55001_decision_criteria
        ADD CONSTRAINT uq_i55001_criteria_tenant_id UNIQUE (tenant_id, id);
EXCEPTION WHEN duplicate_object OR duplicate_table THEN NULL;
END $$;
DO $$ BEGIN
    ALTER TABLE cert_i55001_decision_criteria
        ADD CONSTRAINT fk_i55001_criteria_plan
            FOREIGN KEY (tenant_id, plan_id)
            REFERENCES cert_i55001_strategic_plans(tenant_id, id);
EXCEPTION WHEN duplicate_object OR duplicate_table THEN NULL;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uq_i55001_criterion_key
    ON cert_i55001_decision_criteria(tenant_id, plan_id, criterion_key);
DO $$ BEGIN
    ALTER TABLE cert_i55001_decision_criteria
        ADD CONSTRAINT uq_i55001_criteria_plan_key UNIQUE (tenant_id, plan_id, criterion_key);
EXCEPTION WHEN duplicate_object OR duplicate_table THEN NULL;
END $$;
DO $$ BEGIN
    ALTER TABLE cert_i55001_decision_criteria
        ADD CONSTRAINT uq_i55001_criteria_plan_key_outcome UNIQUE (tenant_id, plan_id, criterion_key, outcome);
EXCEPTION WHEN duplicate_object OR duplicate_table THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS cert_i55001_decision_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN
        ('RENEW', 'REPAIR', 'MAINTAIN', 'DISPOSE')),
    criterion_key VARCHAR(120) NOT NULL CHECK (btrim(criterion_key) <> ''),
    rationale TEXT NOT NULL CHECK (btrim(rationale) <> ''),
    decided_by UUID NOT NULL,
    decided_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID
);
DO $$ BEGIN
    ALTER TABLE cert_i55001_decision_records
        ADD CONSTRAINT fk_i55001_decision_criterion
            FOREIGN KEY (tenant_id, plan_id, criterion_key, outcome)
            REFERENCES cert_i55001_decision_criteria(tenant_id, plan_id, criterion_key, outcome);
EXCEPTION WHEN duplicate_object OR duplicate_table THEN NULL;
END $$;
CREATE INDEX IF NOT EXISTS idx_i55001_decision_asset
    ON cert_i55001_decision_records(tenant_id, asset_id);

-- ======================================================================
-- 3. PLANO DE INVESTIMENTO E MANUTENÇÃO POR ATIVO (ED-36 + ED-58)
-- ======================================================================
-- Custos são os APROPRIADOS lidos: total da OS da ED-36 e quantidade ×
-- custo médio da ED-58. Linha sem nenhum dos dois exige no_cost_reason.

CREATE TABLE IF NOT EXISTS cert_i55001_investment_plans (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    period_from DATE NOT NULL,
    period_to DATE NOT NULL CHECK (period_to >= period_from),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
DO $$ BEGIN
    ALTER TABLE cert_i55001_investment_plans
        ADD CONSTRAINT uq_i55001_investment_plans_tenant_id UNIQUE (tenant_id, id);
EXCEPTION WHEN duplicate_object OR duplicate_table THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS cert_i55001_investment_lines (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    kind VARCHAR(30) NOT NULL CHECK (kind IN
        ('INVESTMENT', 'PREVENTIVE_MAINTENANCE', 'CORRECTIVE_MAINTENANCE',
         'CONDITION_MAINTENANCE')),
    description TEXT NOT NULL CHECK (btrim(description) <> ''),
    work_order_ref VARCHAR(120),
    work_order_cost_total NUMERIC(15,2),
    material_item_code VARCHAR(120),
    material_quantity NUMERIC(15,4),
    material_unit_average_cost NUMERIC(15,4),
    no_cost_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    CHECK (work_order_cost_total IS NULL OR work_order_cost_total >= 0),
    CHECK (work_order_cost_total IS NULL OR work_order_ref IS NOT NULL),
    CHECK ((material_item_code IS NULL AND material_quantity IS NULL
            AND material_unit_average_cost IS NULL)
        OR (material_item_code IS NOT NULL AND material_quantity IS NOT NULL
            AND material_quantity > 0 AND material_unit_average_cost IS NOT NULL
            AND material_unit_average_cost >= 0)),
    CHECK (work_order_cost_total IS NOT NULL OR material_item_code IS NOT NULL
        OR (no_cost_reason IS NOT NULL AND btrim(no_cost_reason) <> ''))
);
DO $$ BEGIN
    ALTER TABLE cert_i55001_investment_lines
        ADD CONSTRAINT fk_i55001_investment_lines_plan
            FOREIGN KEY (tenant_id, plan_id)
            REFERENCES cert_i55001_investment_plans(tenant_id, id);
EXCEPTION WHEN duplicate_object OR duplicate_table THEN NULL;
END $$;
CREATE INDEX IF NOT EXISTS idx_i55001_investment_lines_plan
    ON cert_i55001_investment_lines(tenant_id, plan_id);

-- ======================================================================
-- 4. SINAIS PREDITIVOS (regra estatística simples; parâmetros do tenant)
-- ======================================================================
-- Só sinais (veredito SIGNAL) são gravados; INSUFFICIENT_DATA e NO_SIGNAL
-- são resultados de avaliação, não registros. min_samples e o
-- multiplicador k gravados são os parâmetros do tenant usados na
-- avaliação — nenhum valor de fábrica existe neste pacote.

CREATE TABLE IF NOT EXISTS cert_i55001_predictive_signals (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    measurement VARCHAR(120) NOT NULL CHECK (btrim(measurement) <> ''),
    unit VARCHAR(40) NOT NULL CHECK (btrim(unit) <> ''),
    observed_on DATE NOT NULL,
    observed_value DOUBLE PRECISION NOT NULL,
    window_mean DOUBLE PRECISION NOT NULL,
    window_std_dev DOUBLE PRECISION NOT NULL,
    min_samples INTEGER NOT NULL CHECK (min_samples >= 2),
    deviation_multiplier DOUBLE PRECISION NOT NULL CHECK (deviation_multiplier > 0),
    direction VARCHAR(10) NOT NULL CHECK (direction IN ('ABOVE', 'BELOW', 'EITHER')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID
);

-- ======================================================================
-- 5. VÍNCULOS DE MANUTENÇÃO DA ED-56 (OPE-20 e OPE-38, por referência)
-- ======================================================================

CREATE TABLE IF NOT EXISTS cert_i55001_maintenance_links (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    link_kind VARCHAR(10) NOT NULL CHECK (link_kind IN ('OPE20', 'OPE38')),
    pm_plan_line_ref VARCHAR(200),
    pm_trigger VARCHAR(20) CHECK (pm_trigger IS NULL OR pm_trigger IN
        ('TIME', 'RUNNING_HOURS', 'PUMPED_VOLUME')),
    measurement VARCHAR(120),
    critical_part_code VARCHAR(120),
    spare_status VARCHAR(20) CHECK (spare_status IS NULL OR spare_status IN
        ('OK', 'AT_REORDER_POINT', 'BELOW_MINIMUM')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    CHECK ((link_kind = 'OPE20' AND pm_plan_line_ref IS NOT NULL
            AND btrim(pm_plan_line_ref) <> '' AND pm_trigger IS NOT NULL
            AND measurement IS NULL AND critical_part_code IS NULL
            AND spare_status IS NULL)
        OR (link_kind = 'OPE38' AND measurement IS NOT NULL
            AND btrim(measurement) <> '' AND pm_plan_line_ref IS NULL
            AND pm_trigger IS NULL
            AND ((critical_part_code IS NULL AND spare_status IS NULL)
                OR (critical_part_code IS NOT NULL AND spare_status IS NOT NULL))))
);

-- ======================================================================
-- 6. AUTOAVALIAÇÃO DE CLÁUSULAS DO SGA (mapa como autoavaliação)
-- ======================================================================
-- Só referências, título em palavras próprias ou '[A DEFINIR]' e a
-- situação declarada. Coerência título × title_pending imposta também
-- no banco. CONFORMING sem evidência é recusado NAS DUAS camadas
-- (correção D3 da revisão): no código por AmsClauseAssessment e no
-- banco pela constraint trigger diferida
-- trg_i55001_conforming_requires_evidence (após a tabela de evidências)
-- e pela guarda trg_i55001_evidence_removal_guard.

CREATE TABLE IF NOT EXISTS cert_i55001_clause_assessments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    clause_number VARCHAR(40) NOT NULL CHECK (btrim(clause_number) <> '')
        CHECK (clause_number ~ '^[A-Za-z0-9]+(\.[A-Za-z0-9]+)*$'),
    own_words_title TEXT NOT NULL DEFAULT '[A DEFINIR]' CHECK (btrim(own_words_title) <> ''),
    title_pending BOOLEAN NOT NULL DEFAULT true,
    conformity VARCHAR(20) NOT NULL CHECK (conformity IN
        ('CONFORMING', 'PARTIAL', 'NON_CONFORMING', 'NOT_APPLICABLE')),
    justification TEXT NOT NULL DEFAULT '',
    assessed_by UUID NOT NULL,
    assessed_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (title_pending = (own_words_title = '[A DEFINIR]')),
    CHECK (conformity <> 'NOT_APPLICABLE' OR btrim(justification) <> '')
);
DO $$ BEGIN
    ALTER TABLE cert_i55001_clause_assessments
        ADD CONSTRAINT uq_i55001_clause_assessments_tenant_id UNIQUE (tenant_id, id);
EXCEPTION WHEN duplicate_object OR duplicate_table THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS cert_i55001_clause_evidence (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    assessment_id UUID NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN
        ('DOCUMENT', 'OCCURRENCE', 'STRATEGIC_PLAN', 'INVESTMENT_PLAN',
         'DECISION_RECORD', 'OTHER')),
    evidence_ref VARCHAR(200) NOT NULL CHECK (btrim(evidence_ref) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID
);
DO $$ BEGIN
    ALTER TABLE cert_i55001_clause_evidence
        ADD CONSTRAINT fk_i55001_clause_evidence_assessment
            FOREIGN KEY (tenant_id, assessment_id)
            REFERENCES cert_i55001_clause_assessments(tenant_id, id);
EXCEPTION WHEN duplicate_object OR duplicate_table THEN NULL;
END $$;
CREATE INDEX IF NOT EXISTS idx_i55001_clause_evidence_assessment
    ON cert_i55001_clause_evidence(tenant_id, assessment_id);

-- CONFORMING exige evidência TAMBÉM no banco (correção D3 da revisão;
-- antes a régua vivia só no código). Mecanismo mínimo declarativo:
-- (1) constraint trigger DIFERIDA sobre a avaliação — diferida para a
-- evidência poder nascer na mesma transação, depois da avaliação que a
-- referencia (a FK evidência → avaliação impõe essa ordem); no commit,
-- CONFORMING sem nenhuma linha em cert_i55001_clause_evidence é
-- recusado. (2) guarda na tabela de evidências: remover ou mover a
-- ÚLTIMA evidência de uma avaliação CONFORMING é recusado.
CREATE OR REPLACE FUNCTION cert_i55001_check_conforming_evidence()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.conformity = 'CONFORMING' AND NOT EXISTS (
        SELECT 1 FROM cert_i55001_clause_evidence ev
        WHERE ev.tenant_id = NEW.tenant_id AND ev.assessment_id = NEW.id) THEN
        RAISE EXCEPTION 'cert_i55001: avaliacao CONFORMING exige ao menos uma '
            'evidencia vinculada (cert_i55001_clause_evidence)';
    END IF;
    RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_i55001_conforming_requires_evidence
    ON cert_i55001_clause_assessments;
CREATE CONSTRAINT TRIGGER trg_i55001_conforming_requires_evidence
    AFTER INSERT OR UPDATE ON cert_i55001_clause_assessments
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION cert_i55001_check_conforming_evidence();

CREATE OR REPLACE FUNCTION cert_i55001_guard_evidence_removal()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM cert_i55001_clause_assessments a
               WHERE a.tenant_id = OLD.tenant_id AND a.id = OLD.assessment_id
                 AND a.conformity = 'CONFORMING')
       AND NOT EXISTS (SELECT 1 FROM cert_i55001_clause_evidence ev
               WHERE ev.tenant_id = OLD.tenant_id
                 AND ev.assessment_id = OLD.assessment_id AND ev.id <> OLD.id) THEN
        RAISE EXCEPTION 'cert_i55001: remover/mover a ultima evidencia de '
            'avaliacao CONFORMING e recusado (CONFORMING exige evidencia)';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_i55001_evidence_removal_guard
    ON cert_i55001_clause_evidence;
CREATE TRIGGER trg_i55001_evidence_removal_guard
    BEFORE DELETE OR UPDATE OF tenant_id, assessment_id
    ON cert_i55001_clause_evidence
    FOR EACH ROW EXECUTE FUNCTION cert_i55001_guard_evidence_removal();

-- ======================================================================
-- 7. ISOLAMENTO E AUDITORIA (RLS + trilha, padrão do projeto)
-- ======================================================================
-- Nenhuma coluna nova guarda dado pessoal: chaves, referências, situações
-- declaradas e valores de gestão do tenant — por isso não há linha nova
-- em data_classification (mesmo raciocínio da V21_1/ED-73). Justificativas
-- e descrições são texto de gestão do tenant; se um tenant os usar para
-- dado pessoal, a classificação é pendência da integração, declarada no
-- relatório da ED-72.

SELECT register_tenant_table('cert_i55001_asset_records');
SELECT register_tenant_table('cert_i55001_asset_fields');
SELECT register_tenant_table('cert_i55001_strategic_plans');
SELECT register_tenant_table('cert_i55001_decision_criteria');
SELECT register_tenant_table('cert_i55001_decision_records');
SELECT register_tenant_table('cert_i55001_investment_plans');
SELECT register_tenant_table('cert_i55001_investment_lines');
SELECT register_tenant_table('cert_i55001_predictive_signals');
SELECT register_tenant_table('cert_i55001_maintenance_links');
SELECT register_tenant_table('cert_i55001_clause_assessments');
SELECT register_tenant_table('cert_i55001_clause_evidence');
