-- Tenant Migration (platform)
-- Version: 1.12.0
-- Description: P2.30 — motor único de modelos de documento e mensagem por tenant:
--   `document_templates`, `template_versions` (variáveis tipadas, vigência sem
--   sobreposição, aprovação por outra pessoa, prévia antes da aprovação, blocos
--   travados) e `generated_documents` (documento gerado com o modelo e a versão
--   usados, em instantâneo — mudança posterior do modelo não o altera).
--
-- VERSÃO: a `V1_12` é a reserva do §3 do plano v3.0 para esta tarefa (P2.30);
--   estava livre (nenhum arquivo a ocupava), ao contrário da `V1_10` da P1.33
--   e da `V1_16` da P1.20, que precisaram desviar. O `TenantMigrator` usa
--   `outOfOrder(true)` (decisão G10a), de modo que a ordem de aplicação não
--   depende do número.
--
-- DESENHO (aceite da P2.30, que vale sobre qualquer resumo):
--   * Cada modelo tem tipo de documento (CHECK fechado, os 14 tipos dos modelos
--     de fábrica), canal (PDF, EMAIL, SMS, WHATSAPP ou THERMAL), título e
--     código único por tenant. O código é o que P3.4, P3.5, P3.31, P4.1 e as
--     EDs usam para pedir um documento ao motor, sem criar leiaute próprio.
--   * Cada versão tem blocos de conteúdo em JSONB (ver `TemplateBlock`: cada
--     bloco tem id, kind FREE ou LOCKED e origin TENANT ou SYSTEM), variáveis
--     declaradas em JSONB (nome + tipo do catálogo por tipo de documento),
--     vigência [valid_from, valid_to] e estado (DRAFT, PROPOSED, APPROVED,
--     REJECTED). A vigência de duas versões do mesmo modelo nunca se sobrepõe
--     (EXCLUDE, como `parameter_versions` da V1_5 da P0.19): a sobreposição é
--     recusada pelo banco, não só pelo serviço.
--   * Aprovação por outra pessoa (envelope de proposta e aprovação da P0.19):
--     APPROVED exige approved_by diferente de proposed_by E do autor
--     (created_by), approved_at preenchido e prévia já gerada (previewed_at
--     preenchido — a prévia com massa sintética acontece antes da aprovação).
--     As travas vivem em CHECKs, como na V1_5; o serviço só traduz o erro.
--   * Blocos travados: o mecanismo está aqui (kind LOCKED, origin SYSTEM
--     reservada ao conteúdo legal da P2.32 futura). NENHUM conteúdo legal é
--     semeado nesta migration: inventar o conteúdo da P2.32 violaria o aceite.
--     O serviço impede que uma versão nova altere ou remova um bloco LOCKED
--     da versão anterior, e impede a criação de bloco SYSTEM por esta frente.
--   * Marca do tenant (P2.17, futura): NÃO implementada aqui. O gancho é o
--     `TemplateBrandProvider` do renderizador: a P2.17 o liga ao BrandProvider
--     sem tocar nestas tabelas. Nenhuma coluna de marca é criada agora.
--   * Arquivos (P1.24/P1.20): o documento gerado guarda o texto renderizado
--     (instantâneo) e, quando o arquivo passa pelo pipeline da P1.20, o
--     `document_id` aponta para a linha de `documents`. O motor não grava
--     arquivo fora do pipeline: sem `document_id`, o registro é o instantâneo
--     de texto; com ele, o arquivo guardado é o do pipeline.
--   * Modelos de fábrica: NÃO semeados como linhas. Como os perfis de fábrica
--     da P1.37, vivem no catálogo em código (`FactoryTemplates`) e o tenant os
--     copia (e ajusta) pela API — semear linhas de fábrica em todo tenant
--     criaria modelos "do sistema" indistinguíveis dos do tenant e sem dono
--     para aprovar. A coluna `factory_key` registra de qual fábrica a cópia
--     veio, sem FK (o catálogo é código, não tabela).
--
-- DADOS SINTÉTICOS: nenhuma linha é semeada. Os testes criam o que precisam.
--
-- Depende de V1_2 (register_tenant_table) e de btree_gist (init do banco, como V1_5).
-- `register_tenant_table` acrescenta `version` e `tenant_schema` (BaseEntity /
-- TenantAwareEntity), RLS forçada por tenant e o trigger de auditoria.

-- ======================================================================
-- document_templates
-- ======================================================================

CREATE TABLE IF NOT EXISTS document_templates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code TEXT NOT NULL CHECK (btrim(code) <> ''),
    title TEXT NOT NULL CHECK (btrim(title) <> ''),
    document_type TEXT NOT NULL
        CHECK (document_type IN ('PROTOCOL_RECEIPT', 'USER_RESPONSE', 'CUT_NOTICE',
                                 'COLLECTION_LETTER', 'INSTALLMENT_TERM', 'INFRACTION_NOTICE',
                                 'CERTIFICATE', 'CDA', 'SERVICE_ORDER_PRINT', 'LAB_REPORT',
                                 'AUDIT_REPORT', 'EMAIL', 'SMS', 'WHATSAPP')),
    channel TEXT NOT NULL
        CHECK (channel IN ('PDF', 'EMAIL', 'SMS', 'WHATSAPP', 'THERMAL')),
    -- Chave do modelo de fábrica copiado (catálogo em código, sem FK); nulo
    -- quando o modelo foi criado do zero pelo tenant.
    factory_key TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_document_templates_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_document_templates_tenant_code UNIQUE (tenant_id, code)
);

SELECT register_tenant_table('document_templates');

-- ======================================================================
-- template_versions
-- ======================================================================

CREATE TABLE IF NOT EXISTS template_versions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    template_id UUID NOT NULL,
    version_number INT NOT NULL CHECK (version_number >= 1),
    -- Blocos de conteúdo (array JSON): [{id, kind: FREE|LOCKED,
    -- origin: TENANT|SYSTEM, text}]. A renderização concatena os textos na
    -- ordem e substitui as variáveis {{nome}}.
    blocks JSONB NOT NULL DEFAULT '[]'::jsonb,
    -- Variáveis declaradas (array JSON): [{name, type}] com type em
    -- TEXT, NUMBER, MONEY, DATE ou BOOLEAN. A validação confere cada uma
    -- contra o catálogo do tipo de documento (existência e tipo).
    variables JSONB NOT NULL DEFAULT '[]'::jsonb,
    valid_from DATE NOT NULL,
    valid_to DATE,
    status TEXT NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PROPOSED', 'APPROVED', 'REJECTED')),
    proposed_by UUID,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    -- Prévia com massa sintética (exigida antes da aprovação) e o texto
    -- renderizado na prévia, para auditoria do que foi aprovado.
    previewed_at TIMESTAMPTZ,
    preview_rendered TEXT,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    -- Envelope da P0.19, estendido: quem aprova não é quem propôs nem o autor,
    -- a aprovação tem data e só acontece depois da prévia.
    CHECK (status <> 'APPROVED' OR (approved_by IS NOT NULL AND approved_by <> proposed_by)),
    CHECK (status <> 'APPROVED' OR (approved_by IS NOT NULL AND approved_by <> created_by)),
    CHECK (status <> 'APPROVED' OR approved_at IS NOT NULL),
    CHECK (status <> 'APPROVED' OR previewed_at IS NOT NULL),
    CHECK (status <> 'PROPOSED' OR proposed_by IS NOT NULL),
    CONSTRAINT uq_template_versions_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_template_versions_template_number UNIQUE (tenant_id, template_id, version_number),
    CONSTRAINT fk_template_versions_template
        FOREIGN KEY (tenant_id, template_id) REFERENCES document_templates(tenant_id, id)
);

SELECT register_tenant_table('template_versions');

-- Vigência sem sobreposição por modelo (como parameter_versions, V1_5).
ALTER TABLE template_versions ADD CONSTRAINT excl_template_versions_no_overlap
    EXCLUDE USING gist (tenant_id WITH =, template_id WITH =,
                        daterange(valid_from, valid_to, '[]') WITH &&);

CREATE INDEX IF NOT EXISTS idx_template_versions_template_from
    ON template_versions(tenant_id, template_id, valid_from);

-- ======================================================================
-- generated_documents
-- ======================================================================

CREATE TABLE IF NOT EXISTS generated_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    template_id UUID NOT NULL,
    template_version_id UUID NOT NULL,
    -- Instantâneo do texto renderizado na geração: mudança posterior do
    -- modelo (versão nova, ou outra versão vigente) NUNCA altera esta linha.
    rendered_body TEXT NOT NULL,
    -- Nomes das variáveis referenciadas na geração (array JSON de nomes).
    -- Os VALORES não são persistidos aqui (minimização LGPD): o valor
    -- impresso já está no instantâneo do texto, que é o documento emitido,
    -- e não há motivo para duplicá-lo em coluna à parte.
    variables_used JSONB NOT NULL DEFAULT '[]'::jsonb,
    -- Arquivo guardado pelo pipeline da P1.20 (P1.24), quando houver; nulo
    -- quando só o instantâneo de texto foi gerado.
    document_id UUID,
    generated_by UUID,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_generated_documents_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_generated_documents_template
        FOREIGN KEY (tenant_id, template_id) REFERENCES document_templates(tenant_id, id),
    CONSTRAINT fk_generated_documents_version
        FOREIGN KEY (tenant_id, template_version_id) REFERENCES template_versions(tenant_id, id)
);

SELECT register_tenant_table('generated_documents');

CREATE INDEX IF NOT EXISTS idx_generated_documents_template
    ON generated_documents(tenant_id, template_id, generated_at);

-- O texto renderizado é o documento emitido: contém dado do titular e é
-- redigido na trilha de auditoria, como os filtros de `saved_views`
-- (precedente V1_17). A prévia (`template_versions.preview_rendered`) NÃO
-- é redigida: a prévia usa só massa sintética, nunca dado real, e o texto
-- aprovado precisa ficar legível na trilha.
INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('generated_documents', 'rendered_body', 'REDACT', 'documento emitido contem dado do titular')
ON CONFLICT (table_name, column_name) DO NOTHING;

INSERT INTO public.data_classification (table_name, column_name, class, reason) VALUES
    ('generated_documents', 'rendered_body', 'ID', 'documento emitido contem dado do titular')
ON CONFLICT (table_name, column_name) DO NOTHING;
