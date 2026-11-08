-- ED-73 — Pacote ISO/IEC 27001: Declaração de Aplicabilidade (SoA) por
-- referência, matriz de responsabilidade compartilhada, campanha de
-- recertificação de acessos por papel/escopo, pacote de evidência do
-- fornecedor e autoavaliação de cláusulas do SGSI, sobre o núcleo das
-- V21_1 (P1.32), V21_2 (P2.25) e V21_3 (P3.31).
-- Status: PENDENTE DE NUMERAÇÃO — pela regra vigente do projeto vale só a
-- sequência global da P3.12 (V100+; último número usado: V101). Este SQL
-- fica em db/pendente/ com prefixo ED-73__ e NÃO recebe número inventado:
-- na integração/pouso, atribui-se o próximo número global livre, atualiza-se
-- migration-policy/published-tenant-migrations.txt e o arquivo é movido
-- para db/migration/tenant/V<n>__certification_infosec_iso27001.sql.
--
-- FRONTEIRA (aceite da ED-73; ADR-0041): a ativação da norma
-- (cert.iso27001.enabled / cert.iso27001.edition = "2022" com a Emenda
-- 1:2024, escopo declarado cert.scope_statement e aceite por pessoas
-- diferentes) é do núcleo da P1.32 (V21_1, core V3) — não é duplicada aqui.
-- A ocorrência única é da P2.25 (V21_2); o catálogo de permissões é da
-- ED-06 (shared/ziipsan-security, PermissionCatalog) e a recertificação
-- por usuário é da ED-06 (platform) — aqui nasce só o acréscimo da
-- ISO/IEC 27001: SoA, matriz, campanha por papel/escopo, pacote de
-- evidência do fornecedor e autoavaliação de cláusulas.
--
-- SEM TEXTO DE NORMA: nenhuma tabela guarda título ou identificador
-- literal do Anexo A. Os identificadores e títulos literais dos 93
-- controles só serão reproduzidos depois de decisão do dono sobre licença
-- ou reescrita (pendência nomeada da ED-73). Guardam-se a chave de
-- referência declarada pelo tenant/curadoria e o título declarado, que
-- nasce '[A DEFINIR]' enquanto a decisão não existe (title_pending).
--
-- SEM DELETE: desligar a norma não apaga registro (ADR-0041;
-- cert.deactivation_keeps_records travado em true). Nenhum comando
-- deste arquivo apaga linha de tabela alguma.

-- ======================================================================
-- 1. DECLARAÇÃO DE APLICABILIDADE — LINHAS POR REFERÊNCIA (93 controles)
-- ======================================================================
-- Uma linha por (tenant, chave de controle). A contagem exata de 93 e a
-- unicidade das chaves são réguas do código (StatementOfApplicability);
-- o banco garante a unicidade por tenant e as travas que não cruzam
-- tabelas: justificativa sempre presente e título pendente sinalizado.
-- Correção D2 da revisão cruzada: o banco impõe TAMBÉM o padrão de
-- referência da chave (o mesmo do Java: grupos alfanuméricos separados
-- por ponto) e a coerência título × title_pending derivada do Java
-- (title_pending é verdadeiro SE E SOMENTE SE o título é o marcador
-- '[A DEFINIR]') — título literal com pendente falso, ou marcador com
-- pendente falso, não gravam.

CREATE TABLE IF NOT EXISTS cert_i27001_soa_entries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    control_key VARCHAR(40) NOT NULL CHECK (btrim(control_key) <> '')
        CHECK (control_key ~ '^[A-Za-z0-9]+(\.[A-Za-z0-9]+)*$'),
    title TEXT NOT NULL DEFAULT '[A DEFINIR]' CHECK (btrim(title) <> ''),
    title_pending BOOLEAN NOT NULL DEFAULT true,
    applicability VARCHAR(20) NOT NULL CHECK (applicability IN
        ('APPLICABLE', 'NOT_APPLICABLE')),
    justification TEXT NOT NULL CHECK (btrim(justification) <> ''),
    status VARCHAR(20) NOT NULL CHECK (status IN
        ('NOT_STARTED', 'IN_PROGRESS', 'IMPLEMENTED')),
    owner_id UUID NOT NULL,
    last_reviewed_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (title_pending = (title = '[A DEFINIR]'))
);
ALTER TABLE cert_i27001_soa_entries
    ADD CONSTRAINT uq_i27001_soa_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_i27001_soa_control
    ON cert_i27001_soa_entries(tenant_id, control_key);

CREATE TABLE IF NOT EXISTS cert_i27001_soa_evidence (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    soa_entry_id UUID NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN
        ('DOCUMENT', 'OCCURRENCE', 'SUPPLIER_EVIDENCE', 'OTHER')),
    evidence_ref VARCHAR(200) NOT NULL CHECK (btrim(evidence_ref) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID
);
ALTER TABLE cert_i27001_soa_evidence
    ADD CONSTRAINT fk_i27001_soa_evidence_entry
        FOREIGN KEY (tenant_id, soa_entry_id)
        REFERENCES cert_i27001_soa_entries(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_i27001_soa_evidence_entry
    ON cert_i27001_soa_evidence(tenant_id, soa_entry_id);

-- ======================================================================
-- 2. MATRIZ DE RESPONSABILIDADE COMPARTILHADA ZIIP × CLIENTE
-- ======================================================================
-- "Implementado pelo fornecedor" sem evidência do fornecedor é recusado
-- pela régua do código (SharedResponsibilityEntry); o vínculo de
-- evidência é a tabela seguinte, no desenho do cert_evidence_link (V21_1).

CREATE TABLE IF NOT EXISTS cert_i27001_shared_responsibility (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    control_key VARCHAR(40) NOT NULL CHECK (btrim(control_key) <> '')
        CHECK (control_key ~ '^[A-Za-z0-9]+(\.[A-Za-z0-9]+)*$'),
    responsibility VARCHAR(20) NOT NULL CHECK (responsibility IN
        ('SUPPLIER', 'CLIENT', 'SHARED')),
    implemented_by_supplier BOOLEAN NOT NULL DEFAULT false,
    note TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE cert_i27001_shared_responsibility
    ADD CONSTRAINT uq_i27001_matrix_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_i27001_matrix_control
    ON cert_i27001_shared_responsibility(tenant_id, control_key);
-- Existência da chave contra a SoA (correção D3 da revisão): a matriz só
-- aponta para controle declarado na Declaração de Aplicabilidade do
-- mesmo tenant — no Java, a fábrica que recebe a SoA confere o mesmo.
ALTER TABLE cert_i27001_shared_responsibility
    ADD CONSTRAINT fk_i27001_matrix_soa_control
        FOREIGN KEY (tenant_id, control_key)
        REFERENCES cert_i27001_soa_entries(tenant_id, control_key);

CREATE TABLE IF NOT EXISTS cert_i27001_matrix_evidence (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    matrix_entry_id UUID NOT NULL,
    evidence_ref VARCHAR(200) NOT NULL CHECK (btrim(evidence_ref) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID
);
ALTER TABLE cert_i27001_matrix_evidence
    ADD CONSTRAINT fk_i27001_matrix_evidence_entry
        FOREIGN KEY (tenant_id, matrix_entry_id)
        REFERENCES cert_i27001_shared_responsibility(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_i27001_matrix_evidence_entry
    ON cert_i27001_matrix_evidence(tenant_id, matrix_entry_id);

-- ======================================================================
-- 3. CAMPANHA DE RECERTIFICAÇÃO DE ACESSOS POR PAPEL E POR ESCOPO
-- ======================================================================
-- Itens nascem do retrato do catálogo de permissões da ED-06 lido pela
-- porta do código (PermissionCatalogPort). A decisão do gestor é
-- registrada por item; item sem decisão NÃO é mantido por omissão
-- (mesmo princípio da recertificação por usuário da ED-06).

CREATE TABLE IF NOT EXISTS cert_i27001_recert_campaigns (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    campaign_name TEXT NOT NULL CHECK (btrim(campaign_name) <> ''),
    opens_on DATE NOT NULL,
    due_on DATE NOT NULL CHECK (due_on > opens_on),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID
);
ALTER TABLE cert_i27001_recert_campaigns
    ADD CONSTRAINT uq_i27001_recert_campaigns_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cert_i27001_recert_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    campaign_id UUID NOT NULL,
    role_name VARCHAR(120) NOT NULL CHECK (btrim(role_name) <> ''),
    scope_name VARCHAR(120) NOT NULL CHECK (btrim(scope_name) <> ''),
    permission_name VARCHAR(120) NOT NULL CHECK (btrim(permission_name) <> ''),
    decision VARCHAR(20) CHECK (decision IS NULL OR decision IN ('KEEP', 'REVOKE')),
    decided_by UUID,
    decided_at TIMESTAMPTZ,
    note TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK ((decision IS NULL AND decided_by IS NULL AND decided_at IS NULL)
        OR (decision IS NOT NULL AND decided_by IS NOT NULL AND decided_at IS NOT NULL
            AND btrim(note) <> ''))
);
ALTER TABLE cert_i27001_recert_items
    ADD CONSTRAINT fk_i27001_recert_items_campaign
        FOREIGN KEY (tenant_id, campaign_id)
        REFERENCES cert_i27001_recert_campaigns(tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_i27001_recert_item
    ON cert_i27001_recert_items(tenant_id, campaign_id, role_name, scope_name, permission_name);

-- ======================================================================
-- 4. PACOTE DE EVIDÊNCIA DO FORNECEDOR (por referência + SHA-256)
-- ======================================================================
-- Só referências a artefatos existentes: baseline da P3.8 (PLT-08),
-- exportação da trilha da P1.7 (PLT-19), documentos da P3.9 (referência à
-- minuta, sem copiar os valores abertos) e incidentes da P3.17.

CREATE TABLE IF NOT EXISTS cert_i27001_evidence_pack_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    pack_ref VARCHAR(120) NOT NULL CHECK (btrim(pack_ref) <> ''),
    kind VARCHAR(30) NOT NULL CHECK (kind IN
        ('SECURITY_BASELINE_P38', 'AUDIT_TRAIL_EXPORT_P17',
         'CONTRACT_DOCUMENT_P39', 'SECURITY_INCIDENT_P317')),
    artifact_ref VARCHAR(200) NOT NULL CHECK (btrim(artifact_ref) <> ''),
    sha256_hex CHAR(64) NOT NULL CHECK (sha256_hex ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_i27001_pack_item
    ON cert_i27001_evidence_pack_items(tenant_id, pack_ref, kind, artifact_ref);

-- ======================================================================
-- 5. AUTOAVALIAÇÃO DE CLÁUSULAS DO SGSI (mapa como autoavaliação)
-- ======================================================================
-- Só referências, título em palavras próprias e a situação declarada.
-- CONFORME sem evidência é recusado pela régua do código
-- (IsmsClauseAssessment); o vínculo de evidência é a tabela seguinte.

CREATE TABLE IF NOT EXISTS cert_i27001_clause_assessments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    clause_number VARCHAR(40) NOT NULL CHECK (btrim(clause_number) <> '')
        CHECK (clause_number ~ '^[A-Za-z0-9]+(\.[A-Za-z0-9]+)*$'),
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
ALTER TABLE cert_i27001_clause_assessments
    ADD CONSTRAINT uq_i27001_clause_assessments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cert_i27001_clause_evidence (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    assessment_id UUID NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN
        ('DOCUMENT', 'OCCURRENCE', 'SOA_ENTRY', 'SUPPLIER_EVIDENCE', 'OTHER')),
    evidence_ref VARCHAR(200) NOT NULL CHECK (btrim(evidence_ref) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID
);
ALTER TABLE cert_i27001_clause_evidence
    ADD CONSTRAINT fk_i27001_clause_evidence_assessment
        FOREIGN KEY (tenant_id, assessment_id)
        REFERENCES cert_i27001_clause_assessments(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_i27001_clause_evidence_assessment
    ON cert_i27001_clause_evidence(tenant_id, assessment_id);

-- ======================================================================
-- 6. ISOLAMENTO E AUDITORIA (RLS + trilha, padrão do projeto)
-- ======================================================================
-- Nenhuma coluna nova guarda dado pessoal: chaves, referências, situações
-- declaradas e hashes — por isso não há linha nova em data_classification
-- (mesmo raciocínio da V21_1). Justificativas e notas são texto de gestão
-- do tenant; se um tenant as usar para dado pessoal, a classificação é
-- pendência da integração, declarada no relatório da ED-73.

SELECT register_tenant_table('cert_i27001_soa_entries');
SELECT register_tenant_table('cert_i27001_soa_evidence');
SELECT register_tenant_table('cert_i27001_shared_responsibility');
SELECT register_tenant_table('cert_i27001_matrix_evidence');
SELECT register_tenant_table('cert_i27001_recert_campaigns');
SELECT register_tenant_table('cert_i27001_recert_items');
SELECT register_tenant_table('cert_i27001_evidence_pack_items');
SELECT register_tenant_table('cert_i27001_clause_assessments');
SELECT register_tenant_table('cert_i27001_clause_evidence');
