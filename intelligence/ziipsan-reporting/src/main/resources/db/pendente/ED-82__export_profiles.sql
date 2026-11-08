-- ============================================================================
-- ED-82 (PENDENTE DE NUMERACAO): perfis de exportacao ISO 37120 e PNQS e
-- checklist de cadastro das NBR 12586/12587/13133/14166.
-- SQL sem numero: prefixo ED-82__ em db/pendente/ (regra da cascata).
-- Depende de: fichas da ED-77 sobre o registro da P3.41 (V15_2) - este
-- arquivo NAO cria registro de indicador proprio; guarda so os documentos
-- de exportacao gerados e as avaliacoes do checklist.
-- LICAO C3: a FK composta (tenant_id, document_id) aponta para pai com
-- UNIQUE (tenant_id, id) declarado NESTE arquivo (export_documents).
-- NENHUM DADO SEMEADO: nenhum mapeamento ISO 37120 de fabrica (a referencia
-- da norma e declarada na integracao), nenhum valor de checklist avaliado.
-- PNQS: profile_kind='PNQS' NAO e certificacao - e o registro da geracao
-- da exportacao neutra para preenchimento na plataforma da ABES; o
-- regulamento do PNQS 2026 (v1.2) nao define leiaute de troca de dados.
-- ============================================================================

CREATE TABLE IF NOT EXISTS export_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    profile_kind VARCHAR(20) NOT NULL,
    normative_reference VARCHAR(300) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    generated_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_export_documents_tenant_id UNIQUE (tenant_id, id),
    -- Pai da FK composta por kind das linhas (paridade Java x SQL, correcao D1 da revisao da ED-82).
    CONSTRAINT uq_export_documents_tenant_id_kind UNIQUE (tenant_id, id, profile_kind),
    CONSTRAINT ck_export_documents_kind CHECK (profile_kind IN ('ISO_37120', 'PNQS')),
    CONSTRAINT ck_export_documents_reference CHECK (btrim(normative_reference) <> ''),
    -- Coerencia kind x referencia normativa (OWN5 da revisao): o servico Java so emite
    -- a referencia literal de ExportReferences para cada kind; o banco espelha a regra.
    CONSTRAINT ck_export_documents_kind_reference CHECK (
        (profile_kind = 'ISO_37120' AND normative_reference = 'ABNT NBR ISO 37120:2021')
        OR (profile_kind = 'PNQS' AND normative_reference =
            'PNQS 2026 - regulamento versao 1.2 (ABES), sem leiaute nem formato de troca de dados definido'))
);

CREATE TABLE IF NOT EXISTS export_document_lines (
    tenant_id UUID NOT NULL,
    document_id UUID NOT NULL,
    -- profile_kind DESNORMALIZADO do documento pai (correcao D1 da revisao da ED-82):
    -- sem ele, a regra ISO x PNQS sobre external_reference nao e expressavel em CHECK
    -- de tabela unica. A FK composta por kind abaixo garante que o kind da linha e
    -- exatamente o kind do documento pai (nao e um kind declarado a parte).
    profile_kind VARCHAR(20) NOT NULL,
    indicator_code VARCHAR(60) NOT NULL,
    external_reference VARCHAR(300),
    points_count INTEGER NOT NULL CHECK (points_count >= 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT pk_export_document_lines PRIMARY KEY (tenant_id, document_id, indicator_code),
    CONSTRAINT fk_export_document_lines_document FOREIGN KEY (tenant_id, document_id)
        REFERENCES export_documents (tenant_id, id),
    CONSTRAINT fk_export_document_lines_document_kind FOREIGN KEY (tenant_id, document_id, profile_kind)
        REFERENCES export_documents (tenant_id, id, profile_kind),
    CONSTRAINT ck_export_document_lines_kind CHECK (profile_kind IN ('ISO_37120', 'PNQS')),
    -- ISO 37120 exige a referencia declarada (presente e nao-branca); PNQS nao tem
    -- referencia externa (sempre NULL). Espelha o que o Java impoe: o servico ISO so
    -- emite linha com o mapeamento declarado e o servico PNQS sempre emite NULL.
    CONSTRAINT ck_export_document_lines_reference CHECK (
        (profile_kind = 'ISO_37120' AND external_reference IS NOT NULL
            AND btrim(external_reference) <> '')
        OR (profile_kind = 'PNQS' AND external_reference IS NULL))
);
CREATE INDEX IF NOT EXISTS idx_export_document_lines_document
    ON export_document_lines(tenant_id, document_id);

CREATE TABLE IF NOT EXISTS cadastre_checklist_evaluations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    item_code VARCHAR(80) NOT NULL,
    norm_reference VARCHAR(30) NOT NULL,
    status VARCHAR(25) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    reference_network_linked BOOLEAN NOT NULL DEFAULT FALSE,
    evaluated_by UUID,
    evaluated_at TIMESTAMPTZ,
    note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_cadastre_checklist_item UNIQUE (tenant_id, item_code),
    CONSTRAINT ck_cadastre_checklist_norm CHECK (norm_reference IN
        ('ABNT NBR 12586', 'ABNT NBR 12587', 'ABNT NBR 13133', 'ABNT NBR 14166')),
    CONSTRAINT ck_cadastre_checklist_status CHECK (status IN
        ('PENDING_VERIFICATION', 'VERIFIED', 'NON_CONFORMING', 'NOT_APPLICABLE')),
    CONSTRAINT ck_cadastre_checklist_evaluator CHECK (
        status = 'PENDING_VERIFICATION' OR evaluated_by IS NOT NULL),
    -- Correcao D2 da revisao (paridade com o construtor de Evaluation): NOT_APPLICABLE
    -- exige justificativa nao-vazia. O "note IS NOT NULL" e obrigatorio na expressao:
    -- btrim(NULL) e NULL no PostgreSQL e um CHECK com resultado NULL PASSA.
    CONSTRAINT ck_cadastre_checklist_note_for_not_applicable CHECK (
        status <> 'NOT_APPLICABLE' OR (note IS NOT NULL AND btrim(note) <> '')),
    -- Correcao D1 (item de rede), calibrada com a semantica do isComplete do Java:
    -- os status que CONCLUEM o item (VERIFIED, NOT_APPLICABLE) so valem com a rede de
    -- referencia ligada nos itens '%-REDE-REFERENCIA'; os status que nunca completam
    -- (PENDING_VERIFICATION, NON_CONFORMING) podem ser gravados sem o vinculo - e
    -- justamente a falta do vinculo que uma avaliacao NON_CONFORMING registra.
    CONSTRAINT ck_cadastre_checklist_network_link CHECK (
        item_code NOT LIKE '%-REDE-REFERENCIA'
        OR reference_network_linked
        OR status IN ('PENDING_VERIFICATION', 'NON_CONFORMING')),
    -- Correcao D1 (coerencia item x norma, OWN8): o prefixo NBR do item_code nomeado
    -- pela fabrica CadastreChecklist.items() tem de ser a norma gravada na linha.
    CONSTRAINT ck_cadastre_checklist_item_norm CHECK (
        (item_code LIKE 'NBR12586-%' AND norm_reference = 'ABNT NBR 12586')
        OR (item_code LIKE 'NBR12587-%' AND norm_reference = 'ABNT NBR 12587')
        OR (item_code LIKE 'NBR13133-%' AND norm_reference = 'ABNT NBR 13133')
        OR (item_code LIKE 'NBR14166-%' AND norm_reference = 'ABNT NBR 14166'))
);

SELECT register_tenant_table('export_documents', true, true);
SELECT register_tenant_table('export_document_lines', true, true);
SELECT register_tenant_table('cadastre_checklist_evaluations', true, true);
