-- Tenant Migration — Version: 21.2
-- Description: P2.25 — informacao documentada, ocorrencia e acao
-- (ADR-0041; plano v3.0 §3, tenant V21_2, reservada, P2).
--
-- O QUE ESTA VERSAO CRIA (schema do tenant; RLS forcada + auditoria via
-- register_tenant_table, como a V21_1 da P1.32):
--   * cert_document: o documento controlado do sistema de gestao, sobre a
--     infraestrutura de documentos da P1.24 (tipo por catalogo, classe de
--     retencao, conteudo imutavel por versao). O ciclo e
--     DRAFT -> UNDER_REVIEW -> APPROVED -> PUBLISHED, com nova versao
--     (mesmo codigo, outra doc_version) marcando a anterior SUPERSEDED —
--     nunca UPDATE de titulo ou de content_hash: o conteudo publicado e
--     imutavel, como o arquivo do pipeline da P1.20. A revisao por outra
--     pessoa segue o envelope da P0.19 (revisor <> autor, aprovado por
--     quem revisou). A classificacao do documento e o trio (doc_type,
--     retention_class, confidentiality): tipo por catalogo do tenant,
--     classe de retencao e sigilo (PUBLIC / INTERNAL / RESTRICTED /
--     CONFIDENTIAL). A coluna de versao chama-se doc_version porque
--     register_tenant_table ja acrescenta `version` BIGINT de controle
--     de entidade — chamar a versao do documento de `version` colidiria
--     com ela.
--   * cert_document_acknowledgement: a ciencia do documento publicado,
--     no mesmo padrao do aceite da P1.24 (platform.document_acceptance):
--     usuario, versao, hash do conteudo e momento. So se registra
--     ciencia da versao PUBLICADA, e o hash tem de ser igual ao
--     content_hash da versao — a ciencia aponta o texto exato lido.
--     Re-registrar a mesma ciencia e idempotente (devolve o registro
--     original, sem duplicar), como o re-aceite da P1.24.
--   * cert_occurrence: a ocorrencia UNICA (ADR-0041 item 4) — um so
--     registro para nao conformidade, incidente ou melhoria, sem
--     duplicar por norma. O ciclo e OPEN -> IN_ANALYSIS -> ACTION_PLAN
--     -> EFFECTIVENESS_CHECK -> CLOSED (ou CANCELLED). Causa raiz, acao
--     e verificacao de eficacia sao exigidas no fechamento SO quando a
--     norma ativa pede (ADR-0041 item 3): na pratica, quando a
--     ocorrencia esta vinculada a pelo menos uma clausula cujo mapa
--     esta ACTIVE e cuja ativacao esta ACTIVE. Sem vinculo ativo, a
--     ocorrencia e registro livre e fecha sem essas exigencias — o
--     servico decide isso em tempo de fechamento, nunca por flag
--     gravada, para o desligamento de uma norma afrouxar a exigencia
--     sem reescrever a ocorrencia.
--   * cert_occurrence_clause: vinculo muitos-para-muitos entre
--     ocorrencia e linha do mapa de clausulas da P1.32 — um registro
--     vinculado a varias clausulas, uma clausula reunindo varias
--     ocorrencias (o mesmo padrao do cert_evidence_link).
--   * cert_occurrence_link: as pontes da ocorrencia — PROTOCOL (FK para
--     service_protocols), SERVICE_ORDER (FK para service_orders),
--     SECURITY_INCIDENT (referencia livre: ainda nao existe tabela de
--     incidente de seguranca; a P3.10 cria o incidente e resolve esta
--     ponte, sem quebrar o que a P2.25 gravar) e DOCUMENT (FK para
--     cert_document). Exatamente um alvo por linha (CHECK).
--   * cert_inspection: a fiscalizacao recebida como origem de
--     ocorrencia (JOR-24). Uma fiscalizacao por ocorrencia, origem em
--     AGENCIA_REGULADORA, TRIBUNAL_CONTAS ou ORGAO_AMBIENTAL, com
--     termo, constatacoes (tabela filha), prazo de resposta contado
--     pelo calendario do tenant (tabela holidays da V1_5 + motor
--     DeadlineCalculator da P1.36, com regra CALENDAR ou BUSINESS
--     registrada na fiscalizacao) e, no atendimento, plano de acao e
--     evidencia de atendimento. O fechamento de ocorrencia de
--     fiscalizacao exige plano de acao, evidencia de atendimento e
--     todas as constatacoes atendidas.
--   * cert_inspection_finding: cada constatacao do termo, com estado
--     OPEN / ADDRESSED e referencia da evidencia do atendimento.
--   * cert_action: a acao da ocorrencia, com responsavel e prazo — o
--     contrato que a P2.28 consumira em minhas tarefas (ver
--     DocumentedInformationService.listOpenWorkItems; a tabela
--     work_items da V1_11 e da P2.28, NAO e criada aqui). A verificacao
--     de eficacia vive na ocorrencia (effectiveness_*) e pode tambem
--     ser registrada por acao; o fechamento com norma ativa exige a
--     verificacao da ocorrencia.
--
-- O QUE ESTA VERSAO DELIBERADAMENTE NAO FAZ:
--   * Nenhum item de minhas tarefas: a P2.28 cria work_items (V1_11) e
--     le este contrato; aqui nao ha tabela work_items nem escrita nela.
--   * Nenhum lembrete pela agenda de obrigacoes: e da ED-98, fora.
--   * Nenhuma tabela de incidente de seguranca e nenhum RIPD: sao da
--     P3.10, que liga o incidente a esta ocorrencia.
--   * Nenhum DELETE de ocorrencia, documento, acao ou fiscalizacao: o
--     cancelamento e estado (CANCELLED), e o desligamento de norma
--     nunca apaga registro (ADR-0041 item 5).
--   * Baixa logica do vinculo de evidencia da P1.32: a V21_1 prometeu a
--     correcao para a P2.25, e ela vem aqui — cert_evidence_link
--     ganha active + deactivated_at/by (o servico desativa, nunca
--     apaga; a lista continua devolvendo o vinculo, agora com o estado).
--
-- TRAVAS EM BANCO (o servico as repete com mensagem legivel):
--   * Documento APPROVED exige aprovado por/em; PUBLISHED exige
--     published_at; revisor <> autor; versao unica por codigo.
--   * Ciencia so faz sentido sobre versao publicada: a trava de
--     versao/hash e do servico (o banco nao cruza tabelas em CHECK),
--     e a unicidade (tenant, documento, versao, usuario) e do banco.
--   * Ocorrencia CLOSED exige closed_at/by; verificacao de eficacia
--     consistente (at/by/resultado juntos).
--   * Acao DONE exige done_at; verificacao de eficacia da acao
--     consistente (at/by/resultado juntos).
--   * Fiscalizacao: response_due_date sempre calculado na criacao, e
--     response_days > 0.

-- ========================================================================
-- CONTROLLED DOCUMENT
-- ========================================================================
CREATE TABLE IF NOT EXISTS cert_document (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code TEXT NOT NULL CHECK (char_length(btrim(code)) > 0),
    title TEXT NOT NULL CHECK (char_length(btrim(title)) > 0),
    doc_type TEXT NOT NULL CHECK (char_length(btrim(doc_type)) > 0),
    retention_class TEXT NOT NULL CHECK (char_length(btrim(retention_class)) > 0),
    confidentiality TEXT NOT NULL DEFAULT 'INTERNAL'
        CHECK (confidentiality IN ('PUBLIC', 'INTERNAL', 'RESTRICTED', 'CONFIDENTIAL')),
    doc_version TEXT NOT NULL CHECK (char_length(btrim(doc_version)) > 0),
    content_hash CHAR(64) NOT NULL CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    status TEXT NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'UNDER_REVIEW', 'APPROVED', 'PUBLISHED', 'SUPERSEDED')),
    author_id UUID NOT NULL,
    reviewer_id UUID,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    review_due_date DATE,
    supersedes_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_cert_document_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_cert_document_tenant_code_version UNIQUE (tenant_id, code, doc_version),
    CONSTRAINT chk_cert_document_reviewer_not_author
        CHECK (reviewer_id IS NULL OR reviewer_id <> author_id),
    CONSTRAINT chk_cert_document_approved_requires_record
        CHECK (status NOT IN ('APPROVED', 'PUBLISHED')
               OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)),
    CONSTRAINT chk_cert_document_published_requires_date
        CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);
ALTER TABLE cert_document ADD CONSTRAINT fk_cert_document_supersedes
    FOREIGN KEY (tenant_id, supersedes_id) REFERENCES cert_document (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_cert_document_tenant_code
    ON cert_document (tenant_id, code);
CREATE INDEX IF NOT EXISTS idx_cert_document_tenant_status
    ON cert_document (tenant_id, status);

-- ========================================================================
-- DOCUMENT ACKNOWLEDGEMENT (ciencia)
-- ========================================================================
CREATE TABLE IF NOT EXISTS cert_document_acknowledgement (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    document_id UUID NOT NULL,
    doc_version TEXT NOT NULL CHECK (char_length(btrim(doc_version)) > 0),
    user_id UUID NOT NULL,
    content_hash CHAR(64) NOT NULL CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    acknowledged_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_cert_document_ack_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_cert_document_ack_document
        FOREIGN KEY (tenant_id, document_id) REFERENCES cert_document (tenant_id, id),
    CONSTRAINT uq_cert_document_ack UNIQUE (tenant_id, document_id, doc_version, user_id)
);
CREATE INDEX IF NOT EXISTS idx_cert_document_ack_user
    ON cert_document_acknowledgement (tenant_id, user_id);

-- ========================================================================
-- OCCURRENCE (unica: nao conformidade, incidente, melhoria)
-- ========================================================================
CREATE TABLE IF NOT EXISTS cert_occurrence (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('NONCONFORMITY', 'INCIDENT', 'IMPROVEMENT')),
    title TEXT NOT NULL CHECK (char_length(btrim(title)) > 0),
    description TEXT NOT NULL CHECK (char_length(btrim(description)) > 0),
    origin TEXT NOT NULL DEFAULT 'INTERNAL'
        CHECK (origin IN ('INTERNAL', 'AUDIT', 'CUSTOMER', 'INSPECTION', 'OTHER')),
    status TEXT NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'IN_ANALYSIS', 'ACTION_PLAN', 'EFFECTIVENESS_CHECK', 'CLOSED', 'CANCELLED')),
    root_cause TEXT,
    reported_by UUID NOT NULL,
    reported_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at TIMESTAMPTZ,
    closed_by UUID,
    effectiveness_verified_at TIMESTAMPTZ,
    effectiveness_verified_by UUID,
    effectiveness_result TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_cert_occurrence_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_cert_occurrence_closed_requires_record
        CHECK (status <> 'CLOSED' OR (closed_at IS NOT NULL AND closed_by IS NOT NULL)),
    CONSTRAINT chk_cert_occurrence_effectiveness_consistent
        CHECK ((effectiveness_verified_at IS NULL AND effectiveness_verified_by IS NULL
                AND effectiveness_result IS NULL)
               OR (effectiveness_verified_at IS NOT NULL AND effectiveness_verified_by IS NOT NULL
                   AND effectiveness_result IS NOT NULL
                   AND char_length(btrim(effectiveness_result)) > 0))
);
CREATE INDEX IF NOT EXISTS idx_cert_occurrence_tenant_status
    ON cert_occurrence (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_cert_occurrence_tenant_kind
    ON cert_occurrence (tenant_id, kind);

-- ========================================================================
-- OCCURRENCE <-> CLAUSE MAP (muitos-para-muitos)
-- ========================================================================
CREATE TABLE IF NOT EXISTS cert_occurrence_clause (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    occurrence_id UUID NOT NULL,
    clause_map_id UUID NOT NULL,
    linked_by UUID,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_cert_occurrence_clause_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_cert_occurrence_clause_occurrence
        FOREIGN KEY (tenant_id, occurrence_id) REFERENCES cert_occurrence (tenant_id, id),
    CONSTRAINT fk_cert_occurrence_clause_map
        FOREIGN KEY (tenant_id, clause_map_id) REFERENCES cert_clause_map (tenant_id, id),
    CONSTRAINT uq_cert_occurrence_clause UNIQUE (tenant_id, occurrence_id, clause_map_id)
);
CREATE INDEX IF NOT EXISTS idx_cert_occurrence_clause_map
    ON cert_occurrence_clause (tenant_id, clause_map_id);

-- ========================================================================
-- OCCURRENCE LINKS (pontes: protocolo, OS, incidente de seguranca, documento)
-- ========================================================================
CREATE TABLE IF NOT EXISTS cert_occurrence_link (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    occurrence_id UUID NOT NULL,
    link_kind TEXT NOT NULL
        CHECK (link_kind IN ('PROTOCOL', 'SERVICE_ORDER', 'SECURITY_INCIDENT', 'DOCUMENT')),
    protocol_id UUID,
    service_order_id UUID,
    security_incident_id UUID,
    document_id UUID,
    link_ref TEXT,
    linked_by UUID,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_cert_occurrence_link_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_cert_occurrence_link_occurrence
        FOREIGN KEY (tenant_id, occurrence_id) REFERENCES cert_occurrence (tenant_id, id),
    CONSTRAINT fk_cert_occurrence_link_protocol
        FOREIGN KEY (tenant_id, protocol_id) REFERENCES service_protocols (tenant_id, id),
    CONSTRAINT fk_cert_occurrence_link_service_order
        FOREIGN KEY (tenant_id, service_order_id) REFERENCES service_orders (tenant_id, id),
    CONSTRAINT fk_cert_occurrence_link_document
        FOREIGN KEY (tenant_id, document_id) REFERENCES cert_document (tenant_id, id),
    CONSTRAINT chk_cert_occurrence_link_single_target
        CHECK ((link_kind = 'PROTOCOL' AND protocol_id IS NOT NULL
                AND service_order_id IS NULL AND security_incident_id IS NULL AND document_id IS NULL)
               OR (link_kind = 'SERVICE_ORDER' AND service_order_id IS NOT NULL
                   AND protocol_id IS NULL AND security_incident_id IS NULL AND document_id IS NULL)
               OR (link_kind = 'SECURITY_INCIDENT' AND security_incident_id IS NOT NULL
                   AND protocol_id IS NULL AND service_order_id IS NULL AND document_id IS NULL)
               OR (link_kind = 'DOCUMENT' AND document_id IS NOT NULL
                   AND protocol_id IS NULL AND service_order_id IS NULL
                   AND security_incident_id IS NULL))
);
-- Deduplicacao por alvo: UNIQUE de tabela nao barra duplicata quando as
-- demais colunas sao NULL (NULL <> NULL), por isso um indice parcial por
-- tipo — o servico tambem recusa a duplicata com mensagem legivel.
CREATE UNIQUE INDEX IF NOT EXISTS uq_cert_occurrence_link_protocol
    ON cert_occurrence_link (tenant_id, occurrence_id, protocol_id)
    WHERE link_kind = 'PROTOCOL';
CREATE UNIQUE INDEX IF NOT EXISTS uq_cert_occurrence_link_service_order
    ON cert_occurrence_link (tenant_id, occurrence_id, service_order_id)
    WHERE link_kind = 'SERVICE_ORDER';
CREATE UNIQUE INDEX IF NOT EXISTS uq_cert_occurrence_link_security_incident
    ON cert_occurrence_link (tenant_id, occurrence_id, security_incident_id)
    WHERE link_kind = 'SECURITY_INCIDENT';
CREATE UNIQUE INDEX IF NOT EXISTS uq_cert_occurrence_link_document
    ON cert_occurrence_link (tenant_id, occurrence_id, document_id)
    WHERE link_kind = 'DOCUMENT';
CREATE INDEX IF NOT EXISTS idx_cert_occurrence_link_occurrence
    ON cert_occurrence_link (tenant_id, occurrence_id);

-- ========================================================================
-- INSPECTION (fiscalizacao recebida, origem da ocorrencia — JOR-24)
-- ========================================================================
CREATE TABLE IF NOT EXISTS cert_inspection (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    occurrence_id UUID NOT NULL,
    authority_origin TEXT NOT NULL
        CHECK (authority_origin IN ('REGULATORY_AGENCY', 'AUDIT_COURT', 'ENVIRONMENTAL_AGENCY')),
    term_number TEXT NOT NULL CHECK (char_length(btrim(term_number)) > 0),
    received_date DATE NOT NULL,
    response_days INT NOT NULL CHECK (response_days > 0),
    counting_rule TEXT NOT NULL DEFAULT 'CALENDAR' CHECK (counting_rule IN ('CALENDAR', 'BUSINESS')),
    response_due_date DATE NOT NULL,
    action_plan TEXT,
    attendance_evidence_kind TEXT
        CHECK (attendance_evidence_kind IS NULL
               OR attendance_evidence_kind IN ('DOCUMENT', 'RECORD', 'OTHER')),
    attendance_evidence_id UUID,
    attendance_evidence_ref TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_cert_inspection_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_cert_inspection_occurrence
        FOREIGN KEY (tenant_id, occurrence_id) REFERENCES cert_occurrence (tenant_id, id),
    CONSTRAINT uq_cert_inspection_occurrence UNIQUE (tenant_id, occurrence_id),
    CONSTRAINT chk_cert_inspection_attendance_consistent
        CHECK ((attendance_evidence_kind IS NULL AND attendance_evidence_id IS NULL)
               OR (attendance_evidence_kind IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_cert_inspection_tenant_due
    ON cert_inspection (tenant_id, response_due_date);

-- ========================================================================
-- INSPECTION FINDINGS (constatacoes do termo)
-- ========================================================================
CREATE TABLE IF NOT EXISTS cert_inspection_finding (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    inspection_id UUID NOT NULL,
    description TEXT NOT NULL CHECK (char_length(btrim(description)) > 0),
    status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ADDRESSED')),
    addressed_evidence_ref TEXT,
    addressed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_cert_inspection_finding_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_cert_inspection_finding_inspection
        FOREIGN KEY (tenant_id, inspection_id) REFERENCES cert_inspection (tenant_id, id),
    CONSTRAINT chk_cert_inspection_finding_addressed
        CHECK ((status = 'OPEN' AND addressed_at IS NULL)
               OR (status = 'ADDRESSED' AND addressed_at IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_cert_inspection_finding_inspection
    ON cert_inspection_finding (tenant_id, inspection_id);

-- ========================================================================
-- ACTION (acao da ocorrencia, com responsavel e prazo)
-- ========================================================================
CREATE TABLE IF NOT EXISTS cert_action (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    occurrence_id UUID NOT NULL,
    title TEXT NOT NULL CHECK (char_length(btrim(title)) > 0),
    responsible_id UUID NOT NULL,
    due_date DATE NOT NULL,
    status TEXT NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'DONE', 'VERIFIED')),
    done_at TIMESTAMPTZ,
    effectiveness_verified_at TIMESTAMPTZ,
    effectiveness_verified_by UUID,
    effectiveness_result TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_cert_action_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_cert_action_occurrence
        FOREIGN KEY (tenant_id, occurrence_id) REFERENCES cert_occurrence (tenant_id, id),
    CONSTRAINT chk_cert_action_done_requires_date
        CHECK (status NOT IN ('DONE', 'VERIFIED') OR done_at IS NOT NULL),
    CONSTRAINT chk_cert_action_effectiveness_consistent
        CHECK ((effectiveness_verified_at IS NULL AND effectiveness_verified_by IS NULL
                AND effectiveness_result IS NULL)
               OR (effectiveness_verified_at IS NOT NULL AND effectiveness_verified_by IS NOT NULL
                   AND effectiveness_result IS NOT NULL
                   AND char_length(btrim(effectiveness_result)) > 0))
);
CREATE INDEX IF NOT EXISTS idx_cert_action_occurrence
    ON cert_action (tenant_id, occurrence_id);
CREATE INDEX IF NOT EXISTS idx_cert_action_responsible
    ON cert_action (tenant_id, responsible_id, status);

-- ========================================================================
-- EVIDENCE LINK LOGICAL DEACTIVATION (pendencia da V21_1, entregue aqui)
-- ========================================================================
ALTER TABLE cert_evidence_link ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE cert_evidence_link ADD COLUMN IF NOT EXISTS deactivated_at TIMESTAMPTZ;
ALTER TABLE cert_evidence_link ADD COLUMN IF NOT EXISTS deactivated_by UUID;

-- ========================================================================
-- ISOLATION AND AUDIT
-- ========================================================================
SELECT register_tenant_table('cert_document');
SELECT register_tenant_table('cert_document_acknowledgement');
SELECT register_tenant_table('cert_occurrence');
SELECT register_tenant_table('cert_occurrence_clause');
SELECT register_tenant_table('cert_occurrence_link');
SELECT register_tenant_table('cert_inspection');
SELECT register_tenant_table('cert_inspection_finding');
SELECT register_tenant_table('cert_action');
