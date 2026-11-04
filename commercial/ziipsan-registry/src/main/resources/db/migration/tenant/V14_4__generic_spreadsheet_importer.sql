-- Tenant Migration (registry)
-- Version: 14.4.0
-- Description: P1.40 — importador generico de planilha com aplicacao por OS de
--   lote (plano v3.0 linha 659; ADR-0039 item 9; JOR-26).
--
--   import_jobs: historico de cada carga (quem, quando, arquivo, totais
--     aceitos/recusados, OS de lote e OS inversa). O arquivo em si nao fica
--     aqui: o que identifica a carga e o SHA-256 do conteudo recebido, mais o
--     nome original. A area de preparacao sao as linhas cruas (import_rows);
--     o relatorio de recusas sao as linhas de import_rejections (uma por
--     linha, campo e motivo), de onde sai o CSV para baixar.
--
--   P1.20: nao existe rota REST de upload de documento no app nesta base, so
--     a biblioteca do pipeline (DocumentService). O importador recebe o
--     arquivo no proprio endpoint multipart e aplica a mesma disciplina do
--     pipeline: o conteudo fica em quarentena na area de preparacao ate a
--     validacao, e so linha validada chega a aplicacao. A coluna
--     source_file_id e o ponto de ligacao futuro com o documento do pipeline
--     (quando a rota de upload existir, o job passa a referencia-lo); nesta
--     frente ela fica nula e NAO e FK, para nao acoplar o job a um documento
--     que o endpoint atual nao cria.
--
--   OS de lote (ADR-0039 item 9): uma OS interna por carga, nao uma por
--     registro. A OS nao carrega alvos por linha: as classes de alvo da P1.31
--     nomeiam entidades do dominio (unidade, hidrometro, feicao...), e a
--     linha de uma planilha generica nao e nenhuma delas. O livro-razau por
--     linha e import_rows (applied + alvo aplicado); a OS e o fecho do lote
--     (DRAFT -> OPEN -> EXECUTED -> CLOSED_EXECUTED, terminal pelo trigger
--     da V9_1). A correcao e outra OS, com reverses_order_id para a OS do
--     lote (reversal_order_id no job), que roda o inverso declarado pelo
--     esquema (nos catalogos embutidos: desativar o que foi inserido).
--
--   Efeito aplicado uma vez: (1) a maquina de estados do job (VALIDATED ->
--     APPLIED e terminal para aplicacao; reaplicar e no-op); (2) a linha
--     aplicada tem applied=true e nao e reaplicada; (3) as tabelas de destino
--     tem UNIQUE por tenant na chave (categories.code, subcategories.code).
--     A aplicacao roda em passos idempotentes (linhas, depois OS, depois
--     job) porque o motor de OS abre a propria transacao: um crash no meio
--     deixa o job em APPLYING e a reaplicacao retoma sem duplicar.
--
--   Reenvio do mesmo arquivo nao duplica: UNIQUE (tenant_id, schema_code,
--     sha256) no job. Reenviar devolve o job existente, sem novas linhas e
--     sem nova OS. Um arquivo corrigido tem hash diferente e e uma carga nova.
--
--   Auditoria: import_jobs e import_rejections usam o trigger padrao (nunca
--     carregam valor de linha, so campo e motivo — a mensagem de recusa nao
--     ecoa o valor recusado). import_rows NAO usa o trigger: a linha crua
--     pode conter dado pessoal nos esquemas futuros (beneficiarios, ED-57) e
--     a trilha de auditoria nao pode virar copia da planilha; o registro
--     duravel da carga e o job + as recusas, e o arquivo original vive no
--     pipeline de documentos. register_tenant_table com p_entity=false nas
--     tres (nenhuma e entidade JPA) e p_audit=false so em import_rows.
--
--   Nomes de coluna sem padrao de dado pessoal (P0.11): nenhum casa com o
--     padrao do PersonalDataColumns (field, e nao field_name; source_file_id,
--     e nao document_id), entao nenhuma entrada nova na allowlist e necessaria.
--   Nenhuma linha de fabrica e semeada: os esquemas sao declarados em codigo
--     (ImportSchema) e os valores de dominio vivem no esquema, nao aqui.
--   Sintetico apenas: nenhum seed, nenhum documento, nenhum nome de pessoa.

-- ========================================================================
-- import_jobs
-- ========================================================================

CREATE TABLE IF NOT EXISTS import_jobs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    schema_code TEXT NOT NULL CHECK (btrim(schema_code) <> ''),
    original_filename TEXT NOT NULL CHECK (btrim(original_filename) <> ''),
    sha256 CHAR(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    -- Cabecalho do arquivo recebido, para a validacao assincrona conferir o
    -- modelo sem precisar do arquivo original (que nao fica no banco).
    header_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    source_file_id UUID,
    status TEXT NOT NULL DEFAULT 'RECEIVED'
        CHECK (status IN ('RECEIVED', 'VALIDATING', 'VALIDATED', 'APPLYING', 'APPLIED',
                          'REVERSED', 'FAILED')),
    total_rows INT NOT NULL DEFAULT 0 CHECK (total_rows >= 0),
    accepted_count INT NOT NULL DEFAULT 0 CHECK (accepted_count >= 0),
    rejected_count INT NOT NULL DEFAULT 0 CHECK (rejected_count >= 0),
    applied_count INT NOT NULL DEFAULT 0 CHECK (applied_count >= 0),
    batch_order_id UUID,
    reversal_order_id UUID,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_import_jobs_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_import_jobs_resubmit UNIQUE (tenant_id, schema_code, sha256),
    CONSTRAINT fk_import_jobs_batch_order
        FOREIGN KEY (tenant_id, batch_order_id)
        REFERENCES service_orders (tenant_id, id),
    CONSTRAINT fk_import_jobs_reversal_order
        FOREIGN KEY (tenant_id, reversal_order_id)
        REFERENCES service_orders (tenant_id, id),
    CONSTRAINT chk_import_jobs_counts
        CHECK (accepted_count + rejected_count <= total_rows OR total_rows = 0),
    CONSTRAINT chk_import_jobs_applied
        CHECK (applied_count <= accepted_count)
);

SELECT register_tenant_table('import_jobs', false, true);

CREATE INDEX IF NOT EXISTS idx_import_jobs_schema ON import_jobs(tenant_id, schema_code, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_import_jobs_batch ON import_jobs(tenant_id, batch_order_id);

-- ========================================================================
-- import_rows (area de preparacao; sem trigger de auditoria, ver cabecalho)
-- ========================================================================

CREATE TABLE IF NOT EXISTS import_rows (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    import_id UUID NOT NULL,
    row_number INT NOT NULL CHECK (row_number >= 1),
    raw_json JSONB NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    applied BOOLEAN NOT NULL DEFAULT FALSE,
    applied_at TIMESTAMPTZ,
    applied_target_table TEXT,
    applied_target_id UUID,
    applied_target_key TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_import_rows_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_import_rows_job
        FOREIGN KEY (tenant_id, import_id)
        REFERENCES import_jobs (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_import_rows_number UNIQUE (tenant_id, import_id, row_number),
    CONSTRAINT chk_import_rows_applied
        CHECK ((applied) = (applied_at IS NOT NULL))
);

SELECT register_tenant_table('import_rows', false, false);

CREATE INDEX IF NOT EXISTS idx_import_rows_job ON import_rows(tenant_id, import_id, row_number);

-- ========================================================================
-- import_rejections (relatorio de recusas: linha, campo, motivo)
-- ========================================================================

CREATE TABLE IF NOT EXISTS import_rejections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    import_id UUID NOT NULL,
    row_number INT NOT NULL CHECK (row_number >= 0),
    field TEXT NOT NULL CHECK (btrim(field) <> ''),
    reason_code TEXT NOT NULL CHECK (btrim(reason_code) <> ''),
    message TEXT NOT NULL CHECK (btrim(message) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_import_rejections_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_import_rejections_job
        FOREIGN KEY (tenant_id, import_id)
        REFERENCES import_jobs (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_import_rejections_row_field_reason
        UNIQUE (tenant_id, import_id, row_number, field, reason_code)
);

SELECT register_tenant_table('import_rejections', false, true);

CREATE INDEX IF NOT EXISTS idx_import_rejections_job ON import_rejections(tenant_id, import_id, row_number);
