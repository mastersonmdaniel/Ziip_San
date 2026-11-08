-- P3.40 — Privacidade do titular para todo tenant (LGPD): registro de operações de
-- tratamento (art. 37), pedidos do titular ligados ao protocolo de atendimento da
-- P1.36 e política de privacidade versionada.
-- Version: 23.1.0 — RESERVA do §3 do plano v3.0: "V23_1 | lgpd (amplia V2_5, V2_7) |
-- registro de operações de tratamento por finalidade e base legal, pedidos do titular
-- ligados ao protocolo de atendimento, encarregado de dados do tenant | P3.40".
-- A faixa V23_x além da V23_1 é da ED-74 (o que a ISO/IEC 27701 pede além da lei).
--
-- O encarregado de dados do tenant NÃO ganha tabela aqui: a fonte única é a da P1.6
-- (DAD-32), parâmetro lgpd.dpo_contact (classe d) — duplicar a fonte criaria duas
-- verdades para o mesmo nome/contato exibido no portal, na política e nos documentos.
--
-- FKs condicionais seguem o padrão corrigido da V8_1: a guarda exige a chave única
-- (tenant_id, id) — ou (tenant_id, code) — na tabela-alvo, para a migration não quebrar
-- classpath parcial de módulo; na montagem completa as FKs aplicam.

-- ======================================================================
-- 1. PEDIDO DO TITULAR LIGADO AO PROTOCOLO (amplia lgpd_data_subject_request)
-- ======================================================================

ALTER TABLE lgpd_data_subject_request
    ADD COLUMN IF NOT EXISTS protocol_id UUID;
ALTER TABLE lgpd_data_subject_request
    ADD COLUMN IF NOT EXISTS origin_channel VARCHAR(20);
ALTER TABLE lgpd_data_subject_request
    ADD COLUMN IF NOT EXISTS representative_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_dsr_origin_channel') THEN
        ALTER TABLE lgpd_data_subject_request ADD CONSTRAINT ck_dsr_origin_channel
            CHECK (origin_channel IS NULL OR origin_channel IN ('PORTAL', 'COUNTER', 'PHONE'));
    END IF;
END $$;

-- FK ao protocolo somente se service_protocols (V8_1) existir com a chave (tenant_id, id).
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint c
        WHERE c.conrelid = to_regclass('service_protocols')
          AND c.contype IN ('u', 'p')
          AND (SELECT array_agg(a.attname::text ORDER BY k.ord)
               FROM unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord)
               JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum)
              = ARRAY['tenant_id', 'id']
    ) AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_dsr_protocol') THEN
        ALTER TABLE lgpd_data_subject_request ADD CONSTRAINT fk_dsr_protocol
            FOREIGN KEY (tenant_id, protocol_id) REFERENCES service_protocols(tenant_id, id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_dsr_protocol
    ON lgpd_data_subject_request(tenant_id, protocol_id);

-- Correção de referência pedida no aceite: o prazo da declaração completa é o do
-- art. 19, II (15 dias), não o do art. 18 (que lista os direitos).
COMMENT ON COLUMN lgpd_data_subject_request.deadline_at IS
    'Prazo da declaração completa: 15 dias, LGPD art. 19, II (não art. 18).';

-- ======================================================================
-- 2. FINALIDADE DO CONSENTIMENTO PELO CATÁLOGO (DAD-30, P1.3)
-- ======================================================================
-- lgpd_consent.purpose (V2_5) é texto livre VARCHAR(200); a finalidade passa a ter o
-- código do catálogo consent_purposes (V3_12, code VARCHAR(40)) em coluna própria,
-- sem apagar o texto histórico (expand/contract: a coluna antiga sai em release
-- seguinte à que deixou de usá-la, regra do §3).

ALTER TABLE lgpd_consent
    ADD COLUMN IF NOT EXISTS purpose_code VARCHAR(40);

-- ATENÇÃO (correção da revisão da P3.40): em consent_purposes, a unicidade de
-- (tenant_id, code) é um ÍNDICE único (idx_consent_purposes_code, V3_12), não uma
-- constraint — uma guarda só de pg_constraint nunca dispararia e a FK nunca nasceria.
-- A guarda aceita constraint OU índice único com exatamente essas colunas (FK do
-- PostgreSQL referencia qualquer um dos dois).
DO $$
BEGIN
    IF (EXISTS (
            SELECT 1 FROM pg_constraint c
            WHERE c.conrelid = to_regclass('consent_purposes')
              AND c.contype IN ('u', 'p')
              AND (SELECT array_agg(a.attname::text ORDER BY k.ord)
                   FROM unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord)
                   JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum)
                  = ARRAY['tenant_id', 'code']
        ) OR EXISTS (
            SELECT 1 FROM pg_index i
            JOIN pg_class t ON t.oid = i.indrelid
            WHERE t.relname = 'consent_purposes' AND i.indisunique
              AND (SELECT array_agg(a.attname::text ORDER BY k.ord)
                   FROM unnest(i.indkey) WITH ORDINALITY AS k(attnum, ord)
                   JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum)
                  = ARRAY['tenant_id', 'code']
        ))
        AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_consent_purpose_code') THEN
        ALTER TABLE lgpd_consent ADD CONSTRAINT fk_consent_purpose_code
            FOREIGN KEY (tenant_id, purpose_code) REFERENCES consent_purposes(tenant_id, code);
    END IF;
END $$;

-- ======================================================================
-- 3. REGISTRO DE OPERAÇÕES DE TRATAMENTO (LGPD, art. 37)
-- ======================================================================

CREATE TABLE IF NOT EXISTS lgpd_processing_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL CHECK (btrim(code) <> ''),
    purpose_code VARCHAR(40) NOT NULL CHECK (btrim(purpose_code) <> ''),
    legal_basis VARCHAR(30) NOT NULL CHECK (legal_basis IN (
        'CONSENT', 'CONTRACT', 'LEGAL_OBLIGATION', 'VITAL_INTEREST', 'PUBLIC_TASK',
        'LEGITIMATE_INTEREST', 'CREDIT_PROTECTION'
    )),
    data_categories JSONB NOT NULL DEFAULT '[]'::jsonb,
    data_subject_categories VARCHAR(200) NOT NULL CHECK (btrim(data_subject_categories) <> ''),
    sharing_description TEXT NOT NULL CHECK (btrim(sharing_description) <> ''),
    retention_schedule_id UUID,
    operator_name VARCHAR(200) NOT NULL CHECK (btrim(operator_name) <> ''),
    subprocessors JSONB NOT NULL DEFAULT '[]'::jsonb,
    version INT NOT NULL DEFAULT 1 CHECK (version >= 1),
    origin VARCHAR(10) NOT NULL DEFAULT 'TENANT' CHECK (origin IN ('FACTORY', 'TENANT')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE lgpd_processing_records
    ADD CONSTRAINT uq_processing_records_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE lgpd_processing_records
    ADD CONSTRAINT uq_processing_records_code_version UNIQUE (tenant_id, code, version);
CREATE INDEX IF NOT EXISTS idx_processing_records_purpose
    ON lgpd_processing_records(tenant_id, purpose_code);
CREATE INDEX IF NOT EXISTS idx_processing_records_active
    ON lgpd_processing_records(tenant_id, active);

-- No máximo UMA versão ativa por código: editar supersede a anterior (version + 1).
CREATE UNIQUE INDEX IF NOT EXISTS uq_processing_records_one_active
    ON lgpd_processing_records(tenant_id, code) WHERE active;

-- Finalidade pelo catálogo da P1.3, quando o catálogo existir com a chave (tenant_id, code)
-- como constraint OU índice único (mesma correção da fk_consent_purpose_code acima).
DO $$
BEGIN
    IF (EXISTS (
            SELECT 1 FROM pg_constraint c
            WHERE c.conrelid = to_regclass('consent_purposes')
              AND c.contype IN ('u', 'p')
              AND (SELECT array_agg(a.attname::text ORDER BY k.ord)
                   FROM unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord)
                   JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum)
                  = ARRAY['tenant_id', 'code']
        ) OR EXISTS (
            SELECT 1 FROM pg_index i
            JOIN pg_class t ON t.oid = i.indrelid
            WHERE t.relname = 'consent_purposes' AND i.indisunique
              AND (SELECT array_agg(a.attname::text ORDER BY k.ord)
                   FROM unnest(i.indkey) WITH ORDINALITY AS k(attnum, ord)
                   JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum)
                  = ARRAY['tenant_id', 'code']
        ))
        AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_processing_purpose') THEN
        ALTER TABLE lgpd_processing_records ADD CONSTRAINT fk_processing_purpose
            FOREIGN KEY (tenant_id, purpose_code) REFERENCES consent_purposes(tenant_id, code);
    END IF;
END $$;

-- Retenção vinculada, quando lgpd_retention_schedule tiver a chave (tenant_id, id).
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint c
        WHERE c.conrelid = to_regclass('lgpd_retention_schedule')
          AND c.contype IN ('u', 'p')
          AND (SELECT array_agg(a.attname::text ORDER BY k.ord)
               FROM unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord)
               JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum)
              = ARRAY['tenant_id', 'id']
    ) AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_processing_retention') THEN
        ALTER TABLE lgpd_processing_records ADD CONSTRAINT fk_processing_retention
            FOREIGN KEY (tenant_id, retention_schedule_id)
            REFERENCES lgpd_retention_schedule(tenant_id, id);
    END IF;
END $$;

-- ======================================================================
-- 4. VÍNCULO COLUNA CLASSIFICADA <-> OPERAÇÃO (cobertura, DAD-29)
-- ======================================================================
-- Nomes de tabela/coluna são identificadores PostgreSQL: teto de 63 bytes.

CREATE TABLE IF NOT EXISTS lgpd_processing_record_columns (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    record_id UUID NOT NULL,
    table_name VARCHAR(63) NOT NULL CHECK (btrim(table_name) <> ''),
    column_name VARCHAR(63) NOT NULL CHECK (btrim(column_name) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID
);
ALTER TABLE lgpd_processing_record_columns
    ADD CONSTRAINT uq_processing_columns_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE lgpd_processing_record_columns
    ADD CONSTRAINT uq_processing_columns_link
        UNIQUE (tenant_id, record_id, table_name, column_name);
ALTER TABLE lgpd_processing_record_columns
    ADD CONSTRAINT fk_processing_columns_record
        FOREIGN KEY (tenant_id, record_id) REFERENCES lgpd_processing_records(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_processing_columns_column
    ON lgpd_processing_record_columns(tenant_id, table_name, column_name);

-- ======================================================================
-- 5. POLÍTICA DE PRIVACIDADE PUBLICADA E VERSIONADA
-- ======================================================================

CREATE TABLE IF NOT EXISTS lgpd_privacy_policies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    version INT NOT NULL CHECK (version >= 1),
    title VARCHAR(200) NOT NULL CHECK (btrim(title) <> ''),
    body TEXT NOT NULL CHECK (btrim(body) <> ''),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED')),
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL),
    CHECK (status <> 'DRAFT' OR published_at IS NULL)
);
ALTER TABLE lgpd_privacy_policies
    ADD CONSTRAINT uq_privacy_policies_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE lgpd_privacy_policies
    ADD CONSTRAINT uq_privacy_policies_version UNIQUE (tenant_id, version);
CREATE UNIQUE INDEX IF NOT EXISTS uq_privacy_policies_one_published
    ON lgpd_privacy_policies(tenant_id) WHERE status = 'PUBLISHED';

-- ======================================================================
-- 6. ISOLAMENTO, AUDITORIA, REDAÇÃO E CLASSIFICAÇÃO
-- ======================================================================

SELECT register_tenant_table('lgpd_processing_records');
SELECT register_tenant_table('lgpd_processing_record_columns');
SELECT register_tenant_table('lgpd_privacy_policies');

-- Texto livre do registro de operações pode nomear pessoa de contato de destinatário:
-- classifica (DAD-29) e redige na auditoria, no padrão da V2_7 e da V8_1.
INSERT INTO public.data_classification (table_name, column_name, class, reason) VALUES
    ('lgpd_processing_records', 'sharing_description', 'ID',
     'texto livre de compartilhamento: pode nomear pessoa de contato do destinatário')
ON CONFLICT (table_name, column_name) DO NOTHING;

INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('lgpd_processing_records', 'sharing_description', 'REDACT',
     'texto livre: pode conter dado pessoal de contato do destinatário')
ON CONFLICT (table_name, column_name) DO NOTHING;
