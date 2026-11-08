-- ED-70 — Pacote ISO 14001: aspectos e impactos ambientais (catalogo
-- de fabrica sanitation em codigo), criterio de significancia do
-- tenant, licencas e condicionantes com alerta de vencimento,
-- emergencia ambiental por cenario e autoavaliacao de clausulas,
-- sobre o nucleo das V21_1 (P1.32) e V21_2 (P2.25).
-- Status: PENDENTE DE NUMERACAO — SQL sem numero global, em
-- db/pendente/ com prefixo ED-70__ (regra da cascata). Na integracao,
-- atribui-se o proximo numero livre da sequencia global da P3.12
-- (V100+), atualiza-se
-- migration-policy/published-tenant-migrations.txt e move-se o
-- arquivo para db/migration/tenant/V<n>__certification_env_iso14001.sql.
-- Nenhum numero reservado a outro lote e usado e nenhuma migration
-- publicada e tocada — nenhum numero e atribuido neste arquivo.
--
-- FRONTEIRA (aceite da ED-70; ADR-0041): a ativacao da norma
-- (cert.iso14001.enabled / cert.iso14001.edition = ["2015", "2026"],
-- 2026 alvo e 2015 so em transicao, escopo declarado
-- cert.scope_statement e aceite por pessoas diferentes) e do nucleo
-- da P1.32 (V21_1, core V3) — nao e duplicada aqui. A ocorrencia
-- unica e da P2.25 (V21_2).
--
-- LODO NAO TEM TABELA AQUI: a geracao e a destinacao do lodo sao
-- registradas pela ED-105 (sludge_records, JOR-16/OPE-25) e valem
-- com a norma ligada ou desligada; a ED-70 apenas LE o resumo
-- (SludgeAspectSummary/SludgeAspectView). Nenhuma tabela de residuo
-- e criada neste arquivo, por desenho.
--
-- SEM TEXTO DE NORMA: nenhuma tabela guarda texto nem TITULO
-- LITERAL de clausula da ISO 14001 (norma protegida, sem licenca).
-- A coluna literal_title da autoavaliacao e travada no marcador
-- '[A DEFINIR]' (CHECK abaixo) ate a decisao do dono sobre licenca
-- x reescrita (pendencia nomeada no relatorio da ED-70). Guardam-se
-- referencias (numero de clausula), resumos em palavras proprias e
-- a situacao declarada pelo cliente.
--
-- SEM FABRICA PRESUMIDA: nenhum aspecto, fator de significancia,
-- limiar, licenca, condicionante, cenario ou janela de alerta e
-- semeado aqui — sao declaracoes do tenant. O catalogo de fabrica
-- de ETA/ETE/rede e CODIGO (SanitationAspectCatalog, pacote
-- sanitation), nao seed de banco.
--
-- FK COMPOSTA: toda FK (tenant_id, X) aponta para pai com
-- UNIQUE (tenant_id, id) — constraints declaradas INLINE nos
-- CREATE TABLE, na ordem de criacao pai -> filho (licao da cascata
-- C3), o que torna a reaplicacao integral idempotente (provado em
-- PostgreSQL local, ver o relatorio). Neste arquivo, apenas as
-- tabelas novas; FKs para tabelas de outras entregas nao sao
-- declaradas (pendencia de integracao nomeada no relatorio).
--
-- SEM DELETE: desligar a norma nao apaga registro (ADR-0041;
-- cert.deactivation_keeps_records travado em true). Nenhum comando
-- deste arquivo apaga linha de tabela alguma.

-- ======================================================================
-- 1. ASPECTOS E IMPACTOS AMBIENTAIS (ETA, ETE e rede)
-- ======================================================================
-- template_code referencia o catalogo de fabrica em codigo
-- (SanitationAspectCatalog): nulo no aspecto livre do tenant. A
-- coerencia template x area x sludge_linked e regua do codigo
-- (EnvironmentalAspect); o banco impoe as formas e os enums.

CREATE TABLE IF NOT EXISTS cert_e14001_aspects (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    template_code VARCHAR(60),
    area VARCHAR(12) NOT NULL CHECK (area IN ('WTP', 'WWTP', 'NETWORK')),
    aspect_description TEXT NOT NULL CHECK (btrim(aspect_description) <> ''),
    impact_description TEXT NOT NULL CHECK (btrim(impact_description) <> ''),
    situation VARCHAR(12) NOT NULL CHECK (situation IN ('NORMAL', 'ABNORMAL', 'EMERGENCY')),
    sludge_linked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (NOT sludge_linked OR template_code IS NOT NULL),
    CONSTRAINT uq_e14001_aspects_tenant_id UNIQUE (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_e14001_aspects_area
    ON cert_e14001_aspects(tenant_id, area, situation);

-- ======================================================================
-- 2. CRITERIO DE SIGNIFICANCIA DO TENANT + AVALIACOES
-- ======================================================================
-- factors: JSONB de [{"name": "...", "maxScore": n}] declarados pelo
-- tenant; threshold no intervalo [0, soma dos maximos] e regua do
-- codigo (SignificanceCriteria) — o banco impoe a forma do JSONB e o
-- threshold nao-negativo.

CREATE TABLE IF NOT EXISTS cert_e14001_significance_criteria (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL CHECK (btrim(name) <> ''),
    factors JSONB NOT NULL CHECK (jsonb_typeof(factors) = 'array'
        AND jsonb_array_length(factors) >= 1),
    threshold NUMERIC NOT NULL CHECK (threshold >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_e14001_criteria_tenant_id UNIQUE (tenant_id, id)
);

CREATE TABLE IF NOT EXISTS cert_e14001_significance_evaluations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    aspect_id UUID NOT NULL,
    criteria_id UUID NOT NULL,
    scores JSONB NOT NULL CHECK (jsonb_typeof(scores) = 'object'),
    total NUMERIC NOT NULL CHECK (total >= 0),
    significant BOOLEAN NOT NULL,
    evaluated_by UUID NOT NULL,
    evaluated_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    CONSTRAINT uq_e14001_evaluations_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_e14001_evaluations_aspect
        FOREIGN KEY (tenant_id, aspect_id)
        REFERENCES cert_e14001_aspects(tenant_id, id),
    CONSTRAINT fk_e14001_evaluations_criteria
        FOREIGN KEY (tenant_id, criteria_id)
        REFERENCES cert_e14001_significance_criteria(tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_e14001_evaluations_aspect
    ON cert_e14001_significance_evaluations(tenant_id, aspect_id, evaluated_on);

-- ======================================================================
-- 3. LICENCAS, CONDICIONANTES E EVIDENCIAS
-- ======================================================================
-- O alerta de vencimento e CALCULADO pelo codigo
-- (EnvironmentalLicense.expiryStatus) com a janela em dias declarada
-- pelo chamador — nao ha coluna de alerta nem janela de fabrica.

CREATE TABLE IF NOT EXISTS cert_e14001_licenses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    license_code VARCHAR(80) NOT NULL CHECK (btrim(license_code) <> ''),
    description TEXT NOT NULL CHECK (btrim(description) <> ''),
    issuer_ref VARCHAR(200) NOT NULL CHECK (btrim(issuer_ref) <> ''),
    valid_from DATE NOT NULL,
    valid_until DATE NOT NULL,
    responsible_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_until >= valid_from),
    CONSTRAINT uq_e14001_licenses_tenant_id UNIQUE (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_e14001_licenses_validity
    ON cert_e14001_licenses(tenant_id, valid_until);

CREATE TABLE IF NOT EXISTS cert_e14001_license_conditions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    license_id UUID NOT NULL,
    description TEXT NOT NULL CHECK (btrim(description) <> ''),
    due_date DATE,
    evidence_ref VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    CHECK (evidence_ref IS NULL OR btrim(evidence_ref) <> ''),
    CONSTRAINT uq_e14001_conditions_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_e14001_conditions_license
        FOREIGN KEY (tenant_id, license_id)
        REFERENCES cert_e14001_licenses(tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_e14001_conditions_license
    ON cert_e14001_license_conditions(tenant_id, license_id);

CREATE TABLE IF NOT EXISTS cert_e14001_license_evidence (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    license_id UUID NOT NULL,
    kind VARCHAR(40) NOT NULL CHECK (btrim(kind) <> ''),
    evidence_ref VARCHAR(200) NOT NULL CHECK (btrim(evidence_ref) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    CONSTRAINT uq_e14001_license_evidence_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_e14001_license_evidence_license
        FOREIGN KEY (tenant_id, license_id)
        REFERENCES cert_e14001_licenses(tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_e14001_license_evidence_license
    ON cert_e14001_license_evidence(tenant_id, license_id);

-- ======================================================================
-- 4. EMERGENCIA AMBIENTAL POR CENARIO
-- ======================================================================
-- response_actions e linked_aspect_codes sao JSONB de textos/codigos
-- declarados pelo tenant; ao menos uma acao e regua repetida no banco.

CREATE TABLE IF NOT EXISTS cert_e14001_emergency_scenarios (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    scenario_code VARCHAR(80) NOT NULL CHECK (btrim(scenario_code) <> ''),
    description TEXT NOT NULL CHECK (btrim(description) <> ''),
    response_actions JSONB NOT NULL CHECK (jsonb_typeof(response_actions) = 'array'
        AND jsonb_array_length(response_actions) >= 1),
    responsible_id UUID NOT NULL,
    linked_aspect_codes JSONB NOT NULL DEFAULT '[]'::jsonb
        CHECK (jsonb_typeof(linked_aspect_codes) = 'array'),
    last_reviewed_on DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_e14001_scenarios_tenant_id UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_e14001_scenarios_code
    ON cert_e14001_emergency_scenarios(tenant_id, scenario_code);

-- ======================================================================
-- 5. AUTOAVALIACAO DE CLAUSULAS (mapa como autoavaliacao do cliente)
-- ======================================================================
-- literal_title TRAVADO no marcador '[A DEFINIR]': titulo literal de
-- clausula da ISO 14001 nao se reproduz sem licenca (regra dura;
-- pendencia nomeada). CONFORME sem evidencia e recusado pela regua do
-- codigo (EmsClauseAssessment); a evidencia mora na tabela seguinte,
-- sem CHECK possivel no SQL (limite declarado, como nos irmaos).

CREATE TABLE IF NOT EXISTS cert_e14001_clause_assessments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    clause_number VARCHAR(40) NOT NULL
        CHECK (clause_number ~ '^[A-Za-z0-9]+(\.[A-Za-z0-9]+)*$'),
    literal_title VARCHAR(20) NOT NULL DEFAULT '[A DEFINIR]'
        CHECK (literal_title = '[A DEFINIR]'),
    own_words_summary TEXT NOT NULL DEFAULT '',
    conformity VARCHAR(20) NOT NULL CHECK (conformity IN
        ('CONFORMING', 'PARTIAL', 'NON_CONFORMING', 'NOT_APPLICABLE')),
    justification TEXT NOT NULL DEFAULT '',
    assessed_by UUID NOT NULL,
    assessed_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (conformity <> 'NOT_APPLICABLE' OR btrim(justification) <> ''),
    CONSTRAINT uq_e14001_clause_assessments_tenant_id UNIQUE (tenant_id, id)
);

CREATE TABLE IF NOT EXISTS cert_e14001_clause_evidence (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    assessment_id UUID NOT NULL,
    kind VARCHAR(24) NOT NULL CHECK (kind IN
        ('DOCUMENT', 'OCCURRENCE', 'ASPECT', 'SIGNIFICANCE_EVALUATION', 'LICENSE',
         'EMERGENCY_SCENARIO', 'OTHER')),
    evidence_ref VARCHAR(200) NOT NULL CHECK (btrim(evidence_ref) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    CONSTRAINT uq_e14001_clause_evidence_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_e14001_clause_evidence_assessment
        FOREIGN KEY (tenant_id, assessment_id)
        REFERENCES cert_e14001_clause_assessments(tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_e14001_clause_evidence_assessment
    ON cert_e14001_clause_evidence(tenant_id, assessment_id);

-- ======================================================================
-- 6. ISOLAMENTO (RLS, padrao do projeto)
-- ======================================================================

SELECT register_tenant_table('cert_e14001_aspects');
SELECT register_tenant_table('cert_e14001_significance_criteria');
SELECT register_tenant_table('cert_e14001_significance_evaluations');
SELECT register_tenant_table('cert_e14001_licenses');
SELECT register_tenant_table('cert_e14001_license_conditions');
SELECT register_tenant_table('cert_e14001_license_evidence');
SELECT register_tenant_table('cert_e14001_emergency_scenarios');
SELECT register_tenant_table('cert_e14001_clause_assessments');
SELECT register_tenant_table('cert_e14001_clause_evidence');
