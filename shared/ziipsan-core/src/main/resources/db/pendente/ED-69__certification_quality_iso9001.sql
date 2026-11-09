-- ED-69 — Pacote ISO 9001: mapa de processos, calibração, saída não
-- conforme classificada e prontidão/autoavaliação, sobre o núcleo das
-- V21_1 (P1.32), V21_2 (P2.25) e V21_3 (P3.31).
-- Status: PENDENTE DE NUMERAÇÃO — o §3 do plano v3.0 reserva para os
-- pacotes por norma (ED-69 a ED-77) a faixa "V21_x | certification |
-- pacotes por norma sobre o núcleo das V21_1 a V21_3", SEM número
-- fechado. Pelo precedente ED-74/ED-17/ED-14, este SQL fica em
-- db/pendente/ e NÃO recebe número inventado: na integração, atribui-se
-- o próximo V21_x livre e o arquivo é renomeado para
-- V21_<n>__certification_quality_iso9001.sql.
--
-- FRONTEIRA (aceite da ED-69; ADR-0041): a ativação da norma
-- (cert.iso9001.enabled / cert.iso9001.edition, escopo declarado
-- cert.scope_statement e aceite por pessoas diferentes) é do núcleo da
-- P1.32 (V21_1, core V3) — não é duplicada aqui. A ocorrência única é da
-- P2.25 (V21_2); o registro formal de riscos é da P3.31 (V21_3); o
-- registro de indicadores é da P3.41. Aqui nasce só o acréscimo da
-- ISO 9001: processos, calibrações, classificação da saída não conforme
-- sobre a ocorrência, autoavaliação de cláusulas e checklist de
-- prontidão por edição.
--
-- SEM TEXTO DE NORMA: nenhuma tabela guarda texto da ISO 9001 (norma
-- protegida). Guardam-se referências (número de cláusula), títulos e
-- perguntas em palavras próprias (curadoria ED-76 / tenant) e a situação
-- declarada pelo cliente — título/resumo oficiais são da curadoria
-- (platform.cert_clause, V9_8), exibidos via cert_clause_map (V21_1).
--
-- SEM DELETE: desligar a norma não apaga registro (ADR-0041;
-- cert.deactivation_keeps_records travado em true). Nenhum comando
-- deste arquivo apaga linha de tabela alguma.

-- ======================================================================
-- 1. MAPA DE PROCESSOS (responsável, entradas, saídas, indicador, risco)
-- ======================================================================
-- Entradas/saídas como JSONB de textos declarados pelo tenant. O
-- indicador é o código do registro da P3.41 (sem FK: o registro é
-- versionado por tenant e a existência é validada no serviço, como a
-- P3.31 faz com IndicatorCatalog). O risco é a descrição declarada; o
-- registro formal de riscos é o cert_risks da V21_3.
--
-- D2 (lote R3-D, B2-10): as réguas do Java valem aqui também, no banco —
-- `ProcessMapEntry.requireEntries` exige ao menos uma entrada/saída e
-- nenhum item em branco. O CHECK (mesmo padrão das ED-71/ED-87/ED-91:
-- `jsonb_array_length(...) > 0`) fecha o array vazio; o item em branco
-- ou não-texto é recusado pelo trigger abaixo — CHECK não aceita
-- subconsulta, por isso a regra vive num trigger BEFORE (mesmo mecanismo
-- dos triggers da ED-72), não numa segunda fonte de verdade.

CREATE TABLE IF NOT EXISTS cert_q9001_processes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    process_code VARCHAR(60) NOT NULL CHECK (btrim(process_code) <> ''),
    process_name TEXT NOT NULL CHECK (btrim(process_name) <> ''),
    owner_id UUID NOT NULL,
    inputs JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(inputs) = 'array' AND jsonb_array_length(inputs) > 0),
    outputs JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(outputs) = 'array' AND jsonb_array_length(outputs) > 0),
    indicator_code VARCHAR(60) NOT NULL CHECK (btrim(indicator_code) <> ''),
    risk_description TEXT NOT NULL CHECK (btrim(risk_description) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE cert_q9001_processes
    ADD CONSTRAINT uq_q9001_processes_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_q9001_processes_code
    ON cert_q9001_processes(tenant_id, process_code);

-- D2 (lote R3-D): espelho no banco de `ProcessMapEntry.requireEntries` —
-- cada item de inputs/outputs tem de ser texto não em branco. O guarda
-- ignora valor não-array (o CHECK acima já o recusa — a ordem de
-- avaliação entre trigger BEFORE e CHECK não é garantida, por isso o
-- trigger não assume o formato).
CREATE OR REPLACE FUNCTION cert_q9001_check_process_entries()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    item jsonb;
BEGIN
    IF jsonb_typeof(NEW.inputs) = 'array' THEN
        FOR item IN SELECT jsonb_array_elements(NEW.inputs) LOOP
            IF jsonb_typeof(item) <> 'string' OR btrim(item #>> '{}') = '' THEN
                RAISE EXCEPTION 'cert_q9001: inputs do processo % tem item em branco ou nao-texto (ProcessMapEntry.requireEntries)', NEW.process_code;
            END IF;
        END LOOP;
    END IF;
    IF jsonb_typeof(NEW.outputs) = 'array' THEN
        FOR item IN SELECT jsonb_array_elements(NEW.outputs) LOOP
            IF jsonb_typeof(item) <> 'string' OR btrim(item #>> '{}') = '' THEN
                RAISE EXCEPTION 'cert_q9001: outputs do processo % tem item em branco ou nao-texto (ProcessMapEntry.requireEntries)', NEW.process_code;
            END IF;
        END LOOP;
    END IF;
    RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_q9001_process_entries_not_blank
    ON cert_q9001_processes;
CREATE TRIGGER trg_q9001_process_entries_not_blank
    BEFORE INSERT OR UPDATE OF inputs, outputs ON cert_q9001_processes
    FOR EACH ROW EXECUTE FUNCTION cert_q9001_check_process_entries();

-- ======================================================================
-- 2. CALIBRAÇÃO DE INSTRUMENTOS DE MEDIÇÃO (validade e bloqueio)
-- ======================================================================
-- Cada calibração é um registro novo (histórico preservado). O banco
-- repete a trava do código: validade nunca anterior à calibração. O
-- BLOQUEIO de uso com validade vencida é régua do serviço
-- (InstrumentCalibration.requireUsableOn), porque depende da data de
-- uso, não de uma coluna — limite declarado, não disfarçado.

CREATE TABLE IF NOT EXISTS cert_q9001_calibrations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    instrument_code VARCHAR(60) NOT NULL CHECK (btrim(instrument_code) <> ''),
    instrument_description TEXT NOT NULL CHECK (btrim(instrument_description) <> ''),
    calibrated_on DATE NOT NULL,
    valid_until DATE NOT NULL CHECK (valid_until >= calibrated_on),
    certificate_ref VARCHAR(200) NOT NULL CHECK (btrim(certificate_ref) <> ''),
    calibrated_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID
);
ALTER TABLE cert_q9001_calibrations
    ADD CONSTRAINT uq_q9001_calibrations_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_q9001_calibrations_instrument
    ON cert_q9001_calibrations(tenant_id, instrument_code, valid_until);

-- ======================================================================
-- 3. SAÍDA NÃO CONFORME CLASSIFICADA NA OCORRÊNCIA DA P2.25
-- ======================================================================
-- FK composta contra cert_occurrence (V21_2 tem UNIQUE (tenant_id, id)):
-- ocorrência inexistente ou de outro tenant não aceita classificação.
-- Que a ocorrência é kind = 'NONCONFORMITY' é conferido no serviço de
-- integração (CHECK não cruza tabelas; limite declarado no relatório).

CREATE TABLE IF NOT EXISTS cert_q9001_nonconforming_outputs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    occurrence_id UUID NOT NULL,
    process_code VARCHAR(60) NOT NULL CHECK (btrim(process_code) <> ''),
    description TEXT NOT NULL CHECK (btrim(description) <> ''),
    disposition VARCHAR(20) NOT NULL CHECK (disposition IN
        ('REWORK', 'REPAIR', 'CONCESSION', 'SEGREGATION', 'SCRAP', 'RETURN_TO_ORIGIN')),
    decided_by UUID NOT NULL,
    decided_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID
);
ALTER TABLE cert_q9001_nonconforming_outputs
    ADD CONSTRAINT uq_q9001_nonconforming_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE cert_q9001_nonconforming_outputs
    ADD CONSTRAINT fk_q9001_nonconforming_occurrence
        FOREIGN KEY (tenant_id, occurrence_id)
        REFERENCES cert_occurrence(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_q9001_nonconforming_occurrence
    ON cert_q9001_nonconforming_outputs(tenant_id, occurrence_id);

-- ======================================================================
-- 4. AUTOAVALIAÇÃO DE CLÁUSULAS (mapa como autoavaliação do cliente)
-- ======================================================================
-- Só referências, título em palavras próprias e a situação declarada.
-- CONFORME sem evidência é recusado pela régua do código
-- (QualityClauseAssessment); o vínculo de evidência é a tabela 5, no
-- desenho do cert_evidence_link da V21_1.

CREATE TABLE IF NOT EXISTS cert_q9001_clause_assessments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    -- D2 (lote R3-D): padrao de referencia espelhado do Java
    -- (`QualityClauseAssessment.REFERENCE`: grupos alfanumericos separados
    -- por ponto, teto 40 ja imposto pelo VARCHAR(40)); o btrim antes do
    -- match espelha o trim do Java. "clausula sobre contexto" e recusado
    -- aqui como la.
    clause_number VARCHAR(40) NOT NULL CHECK (btrim(clause_number) ~ '^[A-Za-z0-9]+(\.[A-Za-z0-9]+)*$'),
    own_words_title TEXT NOT NULL CHECK (btrim(own_words_title) <> ''),
    conformity VARCHAR(20) NOT NULL CHECK (conformity IN
        ('CONFORMING', 'PARTIAL', 'NON_CONFORMING', 'NOT_APPLICABLE')),
    justification TEXT NOT NULL DEFAULT '',
    assessed_by UUID NOT NULL,
    assessed_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (conformity <> 'NOT_APPLICABLE' OR btrim(justification) <> '')
);
ALTER TABLE cert_q9001_clause_assessments
    ADD CONSTRAINT uq_q9001_clause_assessments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cert_q9001_clause_evidence (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    assessment_id UUID NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN
        ('DOCUMENT', 'OCCURRENCE', 'PROCESS_MAP', 'CALIBRATION', 'OTHER')),
    evidence_ref VARCHAR(200) NOT NULL CHECK (btrim(evidence_ref) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID
);
ALTER TABLE cert_q9001_clause_evidence
    ADD CONSTRAINT fk_q9001_clause_evidence_assessment
        FOREIGN KEY (tenant_id, assessment_id)
        REFERENCES cert_q9001_clause_assessments(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_q9001_clause_evidence_assessment
    ON cert_q9001_clause_evidence(tenant_id, assessment_id);

-- ======================================================================
-- 5. CHECKLIST DE PRONTIDÃO POR EDIÇÃO
-- ======================================================================
-- Um item por (tenant, edição, cláusula). Edições válidas são as do
-- parâmetro cert.iso9001.edition (2015 transição, 2026 alvo) — o CHECK
-- repete a lista fechada do core V3.

CREATE TABLE IF NOT EXISTS cert_q9001_readiness_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    edition VARCHAR(10) NOT NULL CHECK (edition IN ('2015', '2026')),
    -- D2 (lote R3-D): mesmo padrao de referencia do Java
    -- (`ReadinessChecklist.REFERENCE`, identico ao de
    -- `QualityClauseAssessment`); ver comentario na tabela 4.
    clause_number VARCHAR(40) NOT NULL CHECK (btrim(clause_number) ~ '^[A-Za-z0-9]+(\.[A-Za-z0-9]+)*$'),
    prompt_own_words TEXT NOT NULL CHECK (btrim(prompt_own_words) <> ''),
    status VARCHAR(20) NOT NULL CHECK (status IN ('READY', 'PENDING', 'NOT_APPLICABLE')),
    justification TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (status <> 'NOT_APPLICABLE' OR btrim(justification) <> '')
);
ALTER TABLE cert_q9001_readiness_items
    ADD CONSTRAINT uq_q9001_readiness_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_q9001_readiness_item
    ON cert_q9001_readiness_items(tenant_id, edition, clause_number);

-- ======================================================================
-- 6. ISOLAMENTO E AUDITORIA (RLS + trilha, padrão do projeto)
-- ======================================================================
-- Nenhuma coluna nova guarda dado pessoal: códigos, referências,
-- descrições de processo e situações declaradas — por isso não há linha
-- nova em data_classification (mesmo raciocínio da V21_1). Descrições
-- são texto de gestão do tenant; se um tenant as usar para dado
-- pessoal, a classificação é pendência da integração, declarada no
-- relatório da ED-69.

SELECT register_tenant_table('cert_q9001_processes');
SELECT register_tenant_table('cert_q9001_calibrations');
SELECT register_tenant_table('cert_q9001_nonconforming_outputs');
SELECT register_tenant_table('cert_q9001_clause_assessments');
SELECT register_tenant_table('cert_q9001_clause_evidence');
SELECT register_tenant_table('cert_q9001_readiness_items');
