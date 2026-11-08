-- ED-74 — Pacote ISO/IEC 27701: o que a norma pede além da LGPD, sobre a V23_1.
-- Status: PENDENTE DE NUMERAÇÃO — o §3 do plano v3.0 reserva para a ED-74 a
-- faixa "V23_x | lgpd (amplia V2_5, V2_7) | o que a ISO/IEC 27701 pede além
-- da lei, sobre a V23_1", SEM número fechado (a V23_1 é da P3.40). Pelo
-- precedente ED-17/ED-14, este SQL fica em db/pendente/ e NÃO recebe número
-- inventado: na integração, o dono do plano atribui o próximo V23_x livre e
-- o arquivo é renomeado para V23_<n>__lgpd_privacy_iso27701.sql.
--
-- FRONTEIRA (aceite da ED-74, JOR-15): direitos do titular, registro de
-- operações de tratamento e canal do titular são da P3.40 (obrigação de
-- todo tenant, V23_1); incidente com dado pessoal e RIPD são da P3.10.
-- A ativação da norma (cert.iso27701.enabled / cert.iso27701.edition,
-- escopo declarado e aceite) é do núcleo da P1.32 (V21_1, V3). Aqui só
-- nasce o acréscimo da 27701: papel por tratamento, escopo do SGPI,
-- autoavaliação de cláusulas/controles e verificação desde a concepção.
--
-- SEM TEXTO DE NORMA: nenhuma tabela guarda texto da ISO/IEC 27701
-- (norma protegida). Guardam-se referências (número de cláusula/controle),
-- a audiência, a situação declarada pelo cliente e referências de
-- evidência — título/resumo em palavras próprias são da curadoria da
-- ED-76 (platform.cert_clause, V9_8), exibidos via cert_clause_map (V21_1).
--
-- SEM DELETE: desligar a norma não apaga registro (ADR-0041;
-- cert.deactivation_keeps_records travado em true). Nenhum comando deste
-- arquivo apaga linha de tabela alguma, nem das tabelas da P3.40.
--
-- FKs condicionais seguem o padrão da V23_1/V8_1: a guarda exige a chave
-- única (tenant_id, id) — ou (tenant_id, code) — na tabela-alvo.

-- ======================================================================
-- 1. PAPEL DO TENANT POR TRATAMENTO (controlador ou operador)
-- ======================================================================
-- Vínculo pelo código do registro de operações da P3.40. Sem FK: o código
-- é versionado (uq_processing_records_code_version + uma versão ativa),
-- e referenciar (tenant_id, code) não tem chave única simples; a
-- amarração contra a versão ativa é validada na integração/serviço.

CREATE TABLE IF NOT EXISTS lgpd_processing_roles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    processing_record_code VARCHAR(60) NOT NULL CHECK (btrim(processing_record_code) <> ''),
    role VARCHAR(20) NOT NULL CHECK (role IN ('CONTROLLER', 'PROCESSOR')),
    counterparty_note TEXT NOT NULL DEFAULT '',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE lgpd_processing_roles
    ADD CONSTRAINT uq_processing_roles_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_processing_roles_one_active
    ON lgpd_processing_roles(tenant_id, processing_record_code) WHERE active;

-- ======================================================================
-- 2. ESCOPO DO SISTEMA DE GESTÃO DA PRIVACIDADE DA INFORMAÇÃO (SGPI)
-- ======================================================================
-- Um escopo ativo por tenant, ligado à ativação do núcleo (V21_1) quando
-- ela existir com a chave (tenant_id, id) — FK condicional, padrão V23_1.

CREATE TABLE IF NOT EXISTS lgpd_pims_scopes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    activation_id UUID,
    pims_scope TEXT NOT NULL CHECK (btrim(pims_scope) <> ''),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE lgpd_pims_scopes
    ADD CONSTRAINT uq_pims_scopes_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_pims_scopes_one_active
    ON lgpd_pims_scopes(tenant_id) WHERE active;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint c
        WHERE c.conrelid = to_regclass('cert_standard_activation')
          AND c.contype IN ('u', 'p')
          AND (SELECT array_agg(a.attname::text ORDER BY k.ord)
               FROM unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord)
               JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum)
              = ARRAY['tenant_id', 'id']
    ) AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_pims_scope_activation') THEN
        ALTER TABLE lgpd_pims_scopes ADD CONSTRAINT fk_pims_scope_activation
            FOREIGN KEY (tenant_id, activation_id)
            REFERENCES cert_standard_activation(tenant_id, id);
    END IF;
END $$;

-- ======================================================================
-- 3. AUTOAVALIAÇÃO DE CLÁUSULAS/CONTROLES (mapa como autoavaliação)
-- ======================================================================
-- Só referências e a situação declarada. CONFORME sem evidência é
-- recusado pela régua do código (PrivacyClauseAssessment); o vínculo de
-- evidência é a tabela 4, no desenho do cert_evidence_link da V21_1.

CREATE TABLE IF NOT EXISTS lgpd_privacy_clause_assessments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    clause_number VARCHAR(40) NOT NULL CHECK (btrim(clause_number) <> ''),
    control_ref VARCHAR(40),
    audience VARCHAR(20) NOT NULL CHECK (audience IN ('CONTROLLER', 'PROCESSOR', 'COMMON')),
    conformity VARCHAR(20) NOT NULL CHECK (conformity IN ('CONFORMING', 'PARTIAL', 'NON_CONFORMING', 'NOT_APPLICABLE')),
    justification TEXT NOT NULL DEFAULT '',
    assessed_by UUID NOT NULL,
    assessed_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (conformity <> 'NOT_APPLICABLE' OR btrim(justification) <> '')
);
ALTER TABLE lgpd_privacy_clause_assessments
    ADD CONSTRAINT uq_clause_assessments_tenant_id UNIQUE (tenant_id, id);
-- Sem UNIQUE por (cláusula, controle, audiência): control_ref é anulável e o
-- PostgreSQL trata NULLs como distintos — uma UNIQUE aqui prometeria uma
-- unicidade que não entrega. Uma avaliação vigente por item é régua do
-- serviço na integração, não do banco (limite declarado, não disfarçado).

CREATE TABLE IF NOT EXISTS lgpd_privacy_clause_evidence (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    assessment_id UUID NOT NULL,
    kind VARCHAR(30) NOT NULL CHECK (kind IN ('PROCESSING_RECORD', 'DATA_SUBJECT_REQUEST', 'DPIA', 'INCIDENT', 'DOCUMENT', 'OTHER')),
    evidence_ref VARCHAR(200) NOT NULL CHECK (btrim(evidence_ref) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID
);
ALTER TABLE lgpd_privacy_clause_evidence
    ADD CONSTRAINT fk_clause_evidence_assessment
        FOREIGN KEY (tenant_id, assessment_id)
        REFERENCES lgpd_privacy_clause_assessments(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_clause_evidence_assessment
    ON lgpd_privacy_clause_evidence(tenant_id, assessment_id);

-- ======================================================================
-- 4. VERIFICAÇÃO DE PRIVACIDADE DESDE A CONCEPÇÃO NA MUDANÇA
-- ======================================================================
-- O banco repete a trava do código: tratamento novo/alterado exige a
-- referência do RIPD (P3.10); sem tratamento novo/alterado, exige o motivo.

CREATE TABLE IF NOT EXISTS lgpd_privacy_by_design_checks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    change_ref VARCHAR(200) NOT NULL CHECK (btrim(change_ref) <> ''),
    new_or_changed BOOLEAN NOT NULL,
    dpia_ref VARCHAR(200),
    rationale TEXT NOT NULL DEFAULT '',
    checked_by UUID NOT NULL,
    checked_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (new_or_changed = FALSE OR (dpia_ref IS NOT NULL AND btrim(dpia_ref) <> '')),
    CHECK (new_or_changed = TRUE OR btrim(rationale) <> '')
);
ALTER TABLE lgpd_privacy_by_design_checks
    ADD CONSTRAINT uq_by_design_checks_tenant_id UNIQUE (tenant_id, id);

-- ======================================================================
-- 5. ISOLAMENTO E AUDITORIA (RLS + trilha, padrão do projeto)
-- ======================================================================
-- Nenhuma coluna nova guarda dado pessoal: códigos, referências, papéis e
-- situações declaradas — por isso não há linha nova em data_classification
-- nem regra de redação (mesmo raciocínio da V21_1). counterparty_note e
-- justification são texto livre de gestão, sem nome de titular por desenho
-- do processo; se um tenant os usar para dado pessoal, a classificação é
-- pendência da integração, declarada no relatório da ED-74.

SELECT register_tenant_table('lgpd_processing_roles');
SELECT register_tenant_table('lgpd_pims_scopes');
SELECT register_tenant_table('lgpd_privacy_clause_assessments');
SELECT register_tenant_table('lgpd_privacy_clause_evidence');
SELECT register_tenant_table('lgpd_privacy_by_design_checks');
