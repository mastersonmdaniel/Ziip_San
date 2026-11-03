-- Tenant Migration (platform)
-- Version: 1.16.0
-- Description: P1.20 — pipeline de arquivos enviados com ClamAV: documento anexo
--   com tipo por catalogo, classe de retencao (grupo G6), classificacao de dado,
--   situacao da varredura, checksum SHA-256 e marca de imutabilidade, mais o
--   registro de eventos da varredura (quarentena, limpo, infectado, varredor
--   indisponivel, assinatura desatualizada).
--
-- Versao: a linha da P1.20 no plano v3.0 nao reserva versao no mapa do §3, e o
--   core V9_7 esta explicitamente vedado para reaproveitamento ("vago ...; nao e
--   reaproveitado"). Usa-se aqui a proxima versao livre do schema do tenant do
--   modulo platform (V1_16; V1_11–V1_15 estao reservadas para P2/P3 e o migrador
--   usa outOfOrder, decisao G10a). O registro desta versao no mapa do §3 fica
--   para o coordenador da onda, para nao haver edicao concorrente do plano por
--   varias frentes da camada B.
--
-- Catalogos (document_types, retention_classes): o codigo e obrigatorio
--   (NOT NULL + CHECK de nao-vazio — o equivalente em DDL ao @NotBlank) e unico
--   por tenant; nunca texto livre. Nenhuma linha de fabrica e semeada aqui: os
--   tipos e as classes sao definidos pelo tenant (a classe de retencao do grupo
--   G6 so amplia pisos fixados pelo produto — ADR-0002 — e fixar valores aqui
--   seria inventar dado). Os testes criam as linhas de catalogo de que precisam.
--
-- documents.scan_status: QUARANTINED (em quarentena, aguardando varredura),
--   CLEAN (varrido e limpo — unico estado liberado para download), INFECTED
--   (bloqueado — nunca liberado) e NOT_SCANNED (varredura tentada com o varredor
--   indisponivel — continua em quarentena e NUNCA e marcado como limpo).
--   A quarentena e fisica (chave quarantine/ no armazenamento) e logica
--   (somente CLEAN e servido): ver DocumentService.
--
-- data_classification reusa os 6 codigos do catalogo public.data_classification
--   (ID, CT, LOC, FIN, SOC, SENS, P0.11): a classificacao do conteudo do
--   documento, nao o catalogo coluna-a-coluna.
--
-- document_scan_events.document_id e documents.document_type_id casam com o
--   padrao de dado pessoal dos testes de cobertura por nome ("document"), mas
--   sao ids internos (referencia ao catalogo / ao documento), nao dado pessoal:
--   entram na allowlist NOT_PERSONAL de PersonalDataColumns, no mesmo precedente
--   de payments.document_id e fiscal_documents.document_type. As colunas foram
--   nomeadas para evitar o padrao onde possivel: threat (e nao threat_name) e
--   av_db_updated_at (e nao signature_*).
--
-- ======================================================================
-- document_types
-- ======================================================================

CREATE TABLE IF NOT EXISTS document_types (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code TEXT NOT NULL CHECK (btrim(code) <> ''),
    label TEXT NOT NULL CHECK (btrim(label) <> ''),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_document_types_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_document_types_tenant_code UNIQUE (tenant_id, code)
);

SELECT register_tenant_table('document_types');

-- ======================================================================
-- retention_classes
-- ======================================================================

CREATE TABLE IF NOT EXISTS retention_classes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code TEXT NOT NULL CHECK (btrim(code) <> ''),
    label TEXT NOT NULL CHECK (btrim(label) <> ''),
    retention_months INT NOT NULL CHECK (retention_months >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_retention_classes_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_retention_classes_tenant_code UNIQUE (tenant_id, code)
);

SELECT register_tenant_table('retention_classes');

-- ======================================================================
-- documents
-- ======================================================================

CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    document_type_id UUID NOT NULL,
    retention_class_id UUID NOT NULL,
    data_classification TEXT NOT NULL
        CHECK (data_classification IN ('ID', 'CT', 'LOC', 'FIN', 'SOC', 'SENS')),
    scan_status TEXT NOT NULL DEFAULT 'QUARANTINED'
        CHECK (scan_status IN ('QUARANTINED', 'CLEAN', 'INFECTED', 'NOT_SCANNED')),
    original_filename TEXT NOT NULL CHECK (btrim(original_filename) <> ''),
    declared_content_type TEXT,
    detected_content_type TEXT NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    sha256 CHAR(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    storage_bucket TEXT NOT NULL,
    storage_key TEXT NOT NULL,
    immutable BOOLEAN NOT NULL DEFAULT FALSE,
    scanned_at TIMESTAMPTZ,
    scanner TEXT,
    av_db_updated_at TIMESTAMPTZ,
    threat TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_documents_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_documents_type
        FOREIGN KEY (tenant_id, document_type_id)
        REFERENCES document_types (tenant_id, id),
    CONSTRAINT fk_documents_retention
        FOREIGN KEY (tenant_id, retention_class_id)
        REFERENCES retention_classes (tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_documents_tenant_status ON documents (tenant_id, scan_status);
CREATE INDEX IF NOT EXISTS idx_documents_tenant_type ON documents (tenant_id, document_type_id);

SELECT register_tenant_table('documents');

-- ======================================================================
-- document_scan_events (trilha + alertas da varredura)
-- ======================================================================

CREATE TABLE IF NOT EXISTS document_scan_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    document_id UUID,
    event_type TEXT NOT NULL
        CHECK (event_type IN ('SCAN_QUARANTINED', 'SCAN_CLEAN', 'SCAN_INFECTED',
                              'SCAN_UNAVAILABLE', 'SIGNATURE_STALE')),
    threat TEXT,
    detail TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_document_scan_events_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_document_scan_events_document
        FOREIGN KEY (tenant_id, document_id)
        REFERENCES documents (tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_document_scan_events_tenant_type
    ON document_scan_events (tenant_id, event_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_document_scan_events_document
    ON document_scan_events (tenant_id, document_id, created_at DESC);

SELECT register_tenant_table('document_scan_events');
