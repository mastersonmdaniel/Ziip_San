-- Registry Migration for Tenant Schema
-- Version: 3.11.0
-- Description: P1.12 — arquivos gerados (entrega das exportações assíncronas).
--
-- Sem trigger de auditoria (register_tenant_table com p_audit = false): o
-- conteúdo é o próprio artefato (CSV mascarado) e copiá-lo integral para o
-- audit_log duplicaria o arquivo a cada mudança de estado — o mesmo motivo
-- dos XMLs fiscais em fiscal_documents_xml. A tabela entra em
-- NOT_AUDITED_BY_DESIGN no FreshInstallTest com esta justificativa. RLS
-- forçada e policy de tenant continuam valendo, como em toda tabela de tenant.
CREATE TABLE IF NOT EXISTS generated_files (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_id UUID,
    resource VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('QUEUED', 'READY', 'FAILED')),
    file_name VARCHAR(200) NOT NULL,
    row_count BIGINT,
    content BYTEA,
    error VARCHAR(500),
    ready_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by UUID,
    updated_by UUID
);
CREATE INDEX IF NOT EXISTS idx_generated_files_tenant_created
    ON generated_files(tenant_id, created_at DESC);

SELECT register_tenant_table('generated_files', true, false);
