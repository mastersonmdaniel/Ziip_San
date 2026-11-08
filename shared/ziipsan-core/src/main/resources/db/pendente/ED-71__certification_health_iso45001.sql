-- ED-71 — Pacote ISO 45001 (saude e seguranca ocupacional): inventario
-- de perigos e riscos, etapas de SST da OS, entrega de EPI, investigacao
-- de acidente com dado de saude restrito e autoavaliacao de clausulas,
-- sobre o nucleo das V21_1 (P1.32), V21_2 (P2.25), V9_1 (P1.31) e
-- V1_8 (P3.30).
-- Status: PENDENTE DE NUMERACAO — SQL sem numero global, em
-- db/pendente/ com prefixo ED-71__ (regra do brief). Na integracao,
-- atribui-se o proximo numero livre da sequencia global da P3.12
-- (V100+; ultimo usado: V101), atualiza-se
-- migration-policy/published-tenant-migrations.txt e move-se o arquivo
-- para db/migration/tenant/V<n>__certification_health_iso45001.sql.
--
-- FRONTEIRA (aceite da ED-71; ADR-0041): a ativacao da norma
-- (cert.iso45001.enabled / cert.iso45001.edition = ["2018"], escopo
-- declarado cert.scope_statement e aceite por pessoas diferentes) e do
-- nucleo da P1.32 (V21_1, core V3) — nao e duplicada aqui. A ocorrencia
-- unica e da P2.25 (V21_2); os fatos de seguranca da OS (refs, equipe e
-- habilitacao, PET) sao da P1.31 (V9_1: service_order_safety,
-- service_order_crew, service_order_entry_permits) — nao redefinidos
-- aqui; o estoque/requisicao/lote de EPI sao da ED-58 (inv_*). Aqui
-- nasce so o acrescimo da ISO 45001.
--
-- SEM TEXTO DE NORMA: nenhuma tabela guarda texto nem TITULO LITERAL de
-- clausula da ISO 45001 (norma protegida, sem licenca). A coluna
-- literal_title da autoavaliacao e travada no marcador '[A DEFINIR]'
-- (CHECK abaixo) ate a decisao do dono sobre licenca x reescrita
-- (pendencia nomeada no relatorio da ED-71). Guardam-se referencias
-- (numero de clausula), resumos em palavras proprias e a situacao
-- declarada pelo cliente.
--
-- SEM FABRICA PRESUMIDA: nenhum perigo, atividade, local, EPI ou fator
-- psicossocial e semeado aqui — catalogos sao do tenant.
--
-- DADO DE SAUDE RESTRITO (LGPD; ADR-0041, item 9): o detalhe de saude
-- mora em tabela PROPRIA (cert_h45001_accident_health_data), separada
-- da investigacao, e cada leitura autorizada grava linha no livro
-- cert_h45001_health_access_log. A autorizacao (politica de acessantes
-- + finalidade) e regua do codigo (AccidentInvestigation); o banco
-- impoe a separacao fisica e o registro do acesso.
--
-- FK COMPOSTA: toda FK (tenant_id, X) aponta para pai com
-- UNIQUE (tenant_id, id) — service_orders (V9_1), cert_occurrence
-- (V21_2), org_units (V1_8) e as tabelas novas deste arquivo, que
-- declaram a UNIQUE antes das FKs que as usam (licao da cascata C3).
--
-- SEM DELETE: desligar a norma nao apaga registro (ADR-0041;
-- cert.deactivation_keeps_records travado em true). Nenhum comando
-- deste arquivo apaga linha de tabela alguma.

-- ======================================================================
-- 1. INVENTARIO DE PERIGOS E RISCOS (por atividade e local; area P3.30)
-- ======================================================================
-- Controles e fatores psicossociais como JSONB de textos declarados
-- pelo tenant. A escala (scale_max) e declarada por linha; o banco
-- repete as travas do codigo (valores dentro da escala; campo
-- psicossocial obrigatorio so na categoria PSYCHOSOCIAL e vazio fora
-- dela; ao menos um controle).

CREATE TABLE IF NOT EXISTS cert_h45001_hazards (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    activity_code VARCHAR(60) NOT NULL CHECK (btrim(activity_code) <> ''),
    activity_description TEXT NOT NULL CHECK (btrim(activity_description) <> ''),
    location_ref VARCHAR(200) NOT NULL CHECK (btrim(location_ref) <> ''),
    org_unit_id UUID NOT NULL,
    category VARCHAR(20) NOT NULL CHECK (category IN
        ('PHYSICAL', 'CHEMICAL', 'BIOLOGICAL', 'ERGONOMIC', 'MECHANICAL', 'ELECTRICAL',
         'PSYCHOSOCIAL', 'OTHER')),
    hazard_description TEXT NOT NULL CHECK (btrim(hazard_description) <> ''),
    severity INT NOT NULL,
    likelihood INT NOT NULL,
    scale_max INT NOT NULL CHECK (scale_max BETWEEN 3 AND 10),
    controls JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(controls) = 'array'),
    psychosocial_factors JSONB NOT NULL DEFAULT '[]'::jsonb
        CHECK (jsonb_typeof(psychosocial_factors) = 'array'),
    assessed_by UUID NOT NULL,
    assessed_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (severity BETWEEN 1 AND scale_max),
    CHECK (likelihood BETWEEN 1 AND scale_max),
    CHECK (jsonb_array_length(controls) >= 1),
    CHECK ((category = 'PSYCHOSOCIAL') = (jsonb_array_length(psychosocial_factors) >= 1))
);
ALTER TABLE cert_h45001_hazards
    ADD CONSTRAINT uq_h45001_hazards_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE cert_h45001_hazards
    ADD CONSTRAINT fk_h45001_hazards_org_unit
        FOREIGN KEY (tenant_id, org_unit_id)
        REFERENCES org_units(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_h45001_hazards_area
    ON cert_h45001_hazards(tenant_id, org_unit_id, activity_code);

-- ======================================================================
-- 2. ETAPAS DE SST DA OS (analise de risco + permissao de trabalho)
-- ======================================================================
-- Uma avaliacao por OS (UNIQUE tenant+order). Os fatos crus continuam
-- nas tabelas da P1.31 (service_order_safety/crew); aqui fica a
-- avaliacao do portao da ED-71: flags do tipo de risco, refs das etapas
-- e o canal (ED-36 backoffice, ED-20 campo). O resultado do portao
-- (bloqueios) e calculado pelo codigo (OsSafetyGate) — depende da data
-- de inicio e das habilitacoes, nao de uma coluna.

CREATE TABLE IF NOT EXISTS cert_h45001_os_assessments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    service_type_code VARCHAR(60) NOT NULL CHECK (btrim(service_type_code) <> ''),
    channel VARCHAR(12) NOT NULL CHECK (channel IN ('BACKOFFICE', 'FIELD')),
    confined_space BOOLEAN NOT NULL DEFAULT FALSE,
    trench_work BOOLEAN NOT NULL DEFAULT FALSE,
    height_work BOOLEAN NOT NULL DEFAULT FALSE,
    electrical_work BOOLEAN NOT NULL DEFAULT FALSE,
    trench_depth_m NUMERIC(6, 2) CHECK (trench_depth_m IS NULL OR trench_depth_m >= 0),
    risk_analysis_ref VARCHAR(200),
    risk_analysis_approved_by UUID,
    work_permit_ref VARCHAR(200),
    qualified_release_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK ((risk_analysis_ref IS NULL) = (risk_analysis_approved_by IS NULL)),
    CHECK (trench_work OR trench_depth_m IS NULL)
);
ALTER TABLE cert_h45001_os_assessments
    ADD CONSTRAINT uq_h45001_os_assessments_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE cert_h45001_os_assessments
    ADD CONSTRAINT fk_h45001_os_assessments_order
        FOREIGN KEY (tenant_id, order_id)
        REFERENCES service_orders(tenant_id, id);
ALTER TABLE cert_h45001_os_assessments
    ADD CONSTRAINT uq_h45001_os_assessments_order UNIQUE (tenant_id, order_id);

-- Habilitacoes dos executores consideradas na avaliacao (espelho de
-- leitura do service_order_crew da P1.31: codigo do catalogo de SST e
-- validade — OPE-14; a NR-10 do mesmo catalogo e o codigo 'NR-10',
-- RAS-25). A fonte de verdade continua sendo a P1.31; esta tabela e o
-- retrato que o portao avaliou.
CREATE TABLE IF NOT EXISTS cert_h45001_os_qualifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    assessment_id UUID NOT NULL,
    worker_id UUID NOT NULL,
    qualification_code VARCHAR(100) NOT NULL CHECK (btrim(qualification_code) <> ''),
    valid_until DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID
);
ALTER TABLE cert_h45001_os_qualifications
    ADD CONSTRAINT uq_h45001_os_qualifications_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE cert_h45001_os_qualifications
    ADD CONSTRAINT fk_h45001_os_qualifications_assessment
        FOREIGN KEY (tenant_id, assessment_id)
        REFERENCES cert_h45001_os_assessments(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_h45001_os_qualifications_assessment
    ON cert_h45001_os_qualifications(tenant_id, assessment_id);

-- ======================================================================
-- 3. ENTREGA DE EPI (ED-58) COM VINCULO A TRABALHADOR E FUNCAO
-- ======================================================================
-- O item/lote/CA sao fatos da ED-58 (inv_items/inv_lots, onde o CA ja
-- e exigido no lote de EPI); aqui fica o registro de SST da entrega.
-- Sem FK para inv_*: a ED-58 referencia-se por codigo + requisicao
-- (requisition_ref), como os demais pacotes consomem o almoxarifado.

CREATE TABLE IF NOT EXISTS cert_h45001_ppe_deliveries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    worker_id UUID NOT NULL,
    function_code VARCHAR(60) NOT NULL CHECK (btrim(function_code) <> ''),
    item_code VARCHAR(60) NOT NULL CHECK (btrim(item_code) <> ''),
    lot_code VARCHAR(60) NOT NULL CHECK (btrim(lot_code) <> ''),
    approval_certificate VARCHAR(200) NOT NULL CHECK (btrim(approval_certificate) <> ''),
    quantity INT NOT NULL CHECK (quantity >= 1),
    requisition_ref VARCHAR(200) NOT NULL CHECK (btrim(requisition_ref) <> ''),
    warehouse_location_code VARCHAR(60) NOT NULL CHECK (btrim(warehouse_location_code) <> ''),
    delivered_by UUID NOT NULL,
    delivered_on DATE NOT NULL,
    lot_valid_until DATE NOT NULL CHECK (lot_valid_until >= delivered_on),
    worker_acknowledged BOOLEAN NOT NULL CHECK (worker_acknowledged),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID
);
ALTER TABLE cert_h45001_ppe_deliveries
    ADD CONSTRAINT uq_h45001_ppe_deliveries_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_h45001_ppe_deliveries_worker
    ON cert_h45001_ppe_deliveries(tenant_id, worker_id, delivered_on);

-- ======================================================================
-- 4. INVESTIGACAO DE ACIDENTE (ocorrencia da P2.25) — SEM DADO DE SAUDE
-- ======================================================================

CREATE TABLE IF NOT EXISTS cert_h45001_accident_investigations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    occurrence_id UUID NOT NULL,
    order_id UUID,
    occurred_on DATE NOT NULL,
    event_description TEXT NOT NULL CHECK (btrim(event_description) <> ''),
    severity VARCHAR(12) NOT NULL CHECK (severity IN
        ('NEAR_MISS', 'NO_INJURY', 'INJURY', 'FATAL')),
    root_cause TEXT NOT NULL CHECK (btrim(root_cause) <> ''),
    corrective_action_refs JSONB NOT NULL DEFAULT '[]'::jsonb
        CHECK (jsonb_typeof(corrective_action_refs) = 'array'),
    investigator_id UUID NOT NULL,
    psychosocial_related BOOLEAN NOT NULL DEFAULT FALSE,
    psychosocial_factors JSONB NOT NULL DEFAULT '[]'::jsonb
        CHECK (jsonb_typeof(psychosocial_factors) = 'array'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (jsonb_array_length(corrective_action_refs) >= 1),
    CHECK (psychosocial_related = (jsonb_array_length(psychosocial_factors) >= 1))
);
ALTER TABLE cert_h45001_accident_investigations
    ADD CONSTRAINT uq_h45001_investigations_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE cert_h45001_accident_investigations
    ADD CONSTRAINT fk_h45001_investigations_occurrence
        FOREIGN KEY (tenant_id, occurrence_id)
        REFERENCES cert_occurrence(tenant_id, id);
ALTER TABLE cert_h45001_accident_investigations
    ADD CONSTRAINT fk_h45001_investigations_order
        FOREIGN KEY (tenant_id, order_id)
        REFERENCES service_orders(tenant_id, id);

-- ======================================================================
-- 5. DADO DE SAUDE RESTRITO (tabela propria) + LIVRO DE ACESSOS
-- ======================================================================
-- Separacao fisica deliberada: nenhuma consulta a tabela 4 carrega dado
-- de saude. A leitura passa pela regua do codigo (politica de
-- acessantes + finalidade) e grava linha no livro abaixo. A
-- classificacao formal desta tabela na taxonomia de dados do projeto
-- (dado sensivel de saude, LGPD) e pendencia de integracao declarada
-- no relatorio da ED-71 — nao inventada aqui.

CREATE TABLE IF NOT EXISTS cert_h45001_accident_health_data (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    investigation_id UUID NOT NULL,
    detail TEXT NOT NULL CHECK (btrim(detail) <> ''),
    recorded_by UUID NOT NULL,
    recorded_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID
);
ALTER TABLE cert_h45001_accident_health_data
    ADD CONSTRAINT uq_h45001_health_data_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE cert_h45001_accident_health_data
    ADD CONSTRAINT fk_h45001_health_data_investigation
        FOREIGN KEY (tenant_id, investigation_id)
        REFERENCES cert_h45001_accident_investigations(tenant_id, id);
ALTER TABLE cert_h45001_accident_health_data
    ADD CONSTRAINT uq_h45001_health_data_investigation UNIQUE (tenant_id, investigation_id);

CREATE TABLE IF NOT EXISTS cert_h45001_health_access_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    investigation_id UUID NOT NULL,
    accessor_id UUID NOT NULL,
    purpose TEXT NOT NULL CHECK (btrim(purpose) <> ''),
    accessed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE cert_h45001_health_access_log
    ADD CONSTRAINT uq_h45001_health_access_log_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE cert_h45001_health_access_log
    ADD CONSTRAINT fk_h45001_health_access_log_investigation
        FOREIGN KEY (tenant_id, investigation_id)
        REFERENCES cert_h45001_accident_investigations(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_h45001_health_access_log_investigation
    ON cert_h45001_health_access_log(tenant_id, investigation_id, accessed_at);

-- ======================================================================
-- 6. AUTOAVALIACAO DE CLAUSULAS (mapa como autoavaliacao do cliente)
-- ======================================================================
-- literal_title TRAVADO no marcador '[A DEFINIR]': titulo literal de
-- clausula da ISO 45001 nao se reproduz sem licenca (regra dura;
-- pendencia nomeada). CONFORME sem evidencia e recusado pela regua do
-- codigo (OhsClauseAssessment); a evidencia mora na tabela 7, sem CHECK
-- possivel no SQL (limite declarado, como nos pacotes irmaos).

CREATE TABLE IF NOT EXISTS cert_h45001_clause_assessments (
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
    CHECK (conformity <> 'NOT_APPLICABLE' OR btrim(justification) <> '')
);
ALTER TABLE cert_h45001_clause_assessments
    ADD CONSTRAINT uq_h45001_clause_assessments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cert_h45001_clause_evidence (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    assessment_id UUID NOT NULL,
    kind VARCHAR(24) NOT NULL CHECK (kind IN
        ('DOCUMENT', 'OCCURRENCE', 'HAZARD', 'OS_SAFETY', 'PPE_DELIVERY',
         'ACCIDENT_INVESTIGATION', 'OTHER')),
    evidence_ref VARCHAR(200) NOT NULL CHECK (btrim(evidence_ref) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID
);
ALTER TABLE cert_h45001_clause_evidence
    ADD CONSTRAINT uq_h45001_clause_evidence_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE cert_h45001_clause_evidence
    ADD CONSTRAINT fk_h45001_clause_evidence_assessment
        FOREIGN KEY (tenant_id, assessment_id)
        REFERENCES cert_h45001_clause_assessments(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_h45001_clause_evidence_assessment
    ON cert_h45001_clause_evidence(tenant_id, assessment_id);

-- ======================================================================
-- 7. ISOLAMENTO (RLS, padrao do projeto)
-- ======================================================================

SELECT register_tenant_table('cert_h45001_hazards');
SELECT register_tenant_table('cert_h45001_os_assessments');
SELECT register_tenant_table('cert_h45001_os_qualifications');
SELECT register_tenant_table('cert_h45001_ppe_deliveries');
SELECT register_tenant_table('cert_h45001_accident_investigations');
SELECT register_tenant_table('cert_h45001_accident_health_data');
SELECT register_tenant_table('cert_h45001_health_access_log');
SELECT register_tenant_table('cert_h45001_clause_assessments');
SELECT register_tenant_table('cert_h45001_clause_evidence');
