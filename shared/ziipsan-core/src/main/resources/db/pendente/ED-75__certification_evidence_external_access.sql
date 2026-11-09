-- ED-75 -- Pacote de evidencia e auditor externo: pacote de evidencia
-- por norma ligada (ED-69..ED-74), secao de evidencia do Ziip como
-- fornecedor, concessao de acesso do observador externo (auditor de
-- certificacao e regulador/titular do servico) e registro de cada
-- leitura, sobre o modulo certification (ADR-0041) e os parametros do
-- core V3.
-- Status: PENDENTE DE NUMERACAO -- SQL sem numero global, em
-- db/pendente/ com prefixo ED-75__ (regra da cascata). Na integracao,
-- atribui-se o proximo numero livre da sequencia global da P3.12
-- (V100+), atualiza-se
-- migration-policy/published-tenant-migrations.txt e move-se o
-- arquivo para db/migration/tenant/V<n>__certification_evidence_external_access.sql.
-- Nenhuma migration publicada e tocada e nenhum numero e atribuido
-- neste arquivo.
--
-- PARAMETRO NOVO reg.external_access (aceite da ED-75): o catalogo do
-- core V3 ja tem cert.external_auditor_access (BOOLEAN, classe b,
-- padrao false, ADR-0041 item 10) para o auditor, e
-- cert.evidence_pack_personal_data (BOOLEAN, classe a, TRAVADO em
-- false, com limite LOCKED no V3) para o pacote. O aceite da ED-75
-- generaliza o papel (GES-22, JOR-23) e nomeia reg.external_access
-- (classe b, com aceite) para o mecanismo generalizado; a chave NAO
-- existe no V3, por isso e registrada abaixo no catalogo global, no
-- padrao do precedente da ED-06 (INSERT em
-- public.parameter_definitions, idempotente por ON CONFLICT).
-- Classe b (ADR-0015): padrao de fabrica com aceite fora do padrao --
-- ligar exige aceite formal registrado, com proponente diferente do
-- aceitante (regua repetida no codigo, em ExternalAccessGrant).
--
-- SEM DADO PESSOAL NO PACOTE: a coluna data_kind das evidencias so
-- admite AGGREGATE, MASKED ou PSEUDONYMIZED -- PERSONAL nao grava
-- (trava repetida no codigo, em EvidenceItem; parametro
-- cert.evidence_pack_personal_data travado em false, classe a).
--
-- EXPIRACAO OBRIGATORIA: cert_ext_access_grants.expires_at e NOT NULL
-- com CHECK expires_at > valid_from -- concessao sem expiracao nao
-- grava (regua repetida no codigo, em ExternalAccessGrant).
--
-- FK COMPOSTA: toda FK (tenant_id, X) aponta para pai com
-- UNIQUE (tenant_id, id) -- constraints declaradas INLINE nos CREATE
-- TABLE, na ordem pai -> filho, o que torna a reaplicacao integral
-- idempotente.
--
-- SEM DELETE: desligar norma ou acesso nao apaga registro (ADR-0041;
-- cert.deactivation_keeps_records travado em true). Nenhum comando
-- deste arquivo apaga linha de tabela alguma. Revogacao de concessao
-- e UPDATE de revoked_at, nunca exclusao.

-- ======================================================================
-- 0. CATALOGO GLOBAL DE PARAMETROS (core, P0.19) -- chave nova da ED-75
-- ======================================================================

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('reg.external_access', 'BOOLEAN', 'b', 'false',
     'ADR-0041; 21a §8, CQ-14; GES-22, JOR-23 (ED-75)',
     NULL,
     'Acesso de observador externo (auditor de certificacao e regulador ou titular do servico) aos escopos da ED-75. Desligado por padrao; ligar exige aceite formal registrado (classe b), concessao somente leitura, com expiracao obrigatoria e registro de cada leitura.')
ON CONFLICT (definition_key) DO NOTHING;

-- ======================================================================
-- 1. PACOTE DE EVIDENCIA (cabecalho)
-- ======================================================================

CREATE TABLE IF NOT EXISTS cert_ext_evidence_packs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    manifest_sha256 CHAR(64) NOT NULL CHECK (manifest_sha256 ~ '^[0-9a-f]{64}$'),
    personal_data BOOLEAN NOT NULL DEFAULT false CHECK (personal_data = false),
    supplier_evidence_complete BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_ext_packs_tenant_id UNIQUE (tenant_id, id)
);

CREATE TABLE IF NOT EXISTS cert_ext_evidence_pack_standards (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    pack_id UUID NOT NULL,
    standard VARCHAR(16) NOT NULL CHECK (standard IN
        ('ISO_9001', 'ISO_14001', 'ISO_45001', 'ISO_55001', 'ISO_27001', 'ISO_27701')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ext_pack_standards_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_ext_pack_standards_pack
        FOREIGN KEY (tenant_id, pack_id)
        REFERENCES cert_ext_evidence_packs(tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_ext_pack_standard
    ON cert_ext_evidence_pack_standards(tenant_id, pack_id, standard);

-- ======================================================================
-- 2. ITENS DO PACOTE (por referencia; so dado agregado/mascarado/
--    pseudonimizado -- PERSONAL nao esta no CHECK, por desenho)
-- ======================================================================

CREATE TABLE IF NOT EXISTS cert_ext_evidence_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    pack_id UUID NOT NULL,
    standard VARCHAR(16) NOT NULL CHECK (standard IN
        ('ISO_9001', 'ISO_14001', 'ISO_45001', 'ISO_55001', 'ISO_27001', 'ISO_27701')),
    data_kind VARCHAR(16) NOT NULL CHECK (data_kind IN
        ('AGGREGATE', 'MASKED', 'PSEUDONYMIZED')),
    evidence_ref VARCHAR(200) NOT NULL CHECK (btrim(evidence_ref) <> '' AND evidence_ref = btrim(evidence_ref)),
    sha256 CHAR(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    CONSTRAINT uq_ext_items_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_ext_items_pack
        FOREIGN KEY (tenant_id, pack_id)
        REFERENCES cert_ext_evidence_packs(tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_ext_items_pack
    ON cert_ext_evidence_items(tenant_id, pack_id);
-- Dobra R6-A (R6-A1): EvidenceItem faz trim() do ref no construtor,
-- mas o CHECK antigo so exigia btrim(evidence_ref) <> '' e o UNIQUE
-- comparava o ref cru — "SYN-qms" e " SYN-qms" eram duplicata em
-- memoria e duas linhas no banco. O CHECK estendido acima exige
-- tambem evidence_ref = btrim(evidence_ref), repetindo no banco a
-- regra em memoria, no padrao do comentario da dobra R5-A (F1-B2).
-- Dobra R5-A (F1-B2): a nao-duplicacao de artefatos do pacote vive
-- so em memoria (EvidencePack: mesma norma + mesma referencia e
-- recusada no construtor). O indice repete a guarda no banco, na
-- forma idempotente ja usada neste arquivo (CREATE UNIQUE INDEX IF
-- NOT EXISTS, como uq_ext_pack_standard, com tenant_id no padrao das
-- demais chaves), para que a fiacao futura (P-ED75-05) nao grave
-- duplicado que o codigo recusaria.
CREATE UNIQUE INDEX IF NOT EXISTS uq_ext_items_pack_standard_ref
    ON cert_ext_evidence_items(tenant_id, pack_id, standard, evidence_ref);

-- ======================================================================
-- 3. SECAO DE EVIDENCIA DO FORNECEDOR (Ziip) NO PACOTE
-- ======================================================================

CREATE TABLE IF NOT EXISTS cert_ext_supplier_evidence (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    pack_id UUID NOT NULL,
    kind VARCHAR(28) NOT NULL CHECK (kind IN
        ('SECURITY_BASELINE_P38', 'AUDIT_TRAIL_EXPORT_P17',
         'CONTRACT_DOCUMENT_P39', 'SECURITY_INCIDENT_P317')),
    evidence_ref VARCHAR(200) NOT NULL CHECK (btrim(evidence_ref) <> '' AND evidence_ref = btrim(evidence_ref)),
    sha256 CHAR(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    CONSTRAINT uq_ext_supplier_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_ext_supplier_pack
        FOREIGN KEY (tenant_id, pack_id)
        REFERENCES cert_ext_evidence_packs(tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_ext_supplier_pack
    ON cert_ext_supplier_evidence(tenant_id, pack_id);
-- Dobra R6-A (R6-A1): mesmo caso da secao 2, para a secao do
-- fornecedor (SupplierEvidenceItem tambem faz trim() do ref no
-- construtor); o CHECK estendido acima repete no banco a regra em
-- memoria, no padrao do comentario da dobra R5-A (F1-B2).
-- Dobra R5-A (F1-B2): mesmo caso da secao 2, para a secao do
-- fornecedor (mesmo tipo + mesma referencia, recusada em memoria
-- por EvidencePack).
CREATE UNIQUE INDEX IF NOT EXISTS uq_ext_supplier_pack_kind_ref
    ON cert_ext_supplier_evidence(tenant_id, pack_id, kind, evidence_ref);

-- ======================================================================
-- 4. CONCESSAO DE ACESSO EXTERNO (somente leitura; expiracao
--    obrigatoria; proponente diferente do aceitante -- classe b)
-- ======================================================================

CREATE TABLE IF NOT EXISTS cert_ext_access_grants (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    profile VARCHAR(28) NOT NULL CHECK (profile IN
        ('CERTIFICATION_AUDITOR', 'REGULATOR_SERVICE_OWNER')),
    external_subject_ref VARCHAR(80) NOT NULL CHECK (btrim(external_subject_ref) <> ''),
    evidence_standards TEXT NOT NULL DEFAULT '',
    granted_by UUID NOT NULL,
    accepted_by UUID NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (granted_by <> accepted_by),
    CHECK (expires_at > valid_from),
    CHECK (profile <> 'REGULATOR_SERVICE_OWNER' OR evidence_standards = ''),
    CONSTRAINT uq_ext_grants_tenant_id UNIQUE (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_ext_grants_tenant_profile
    ON cert_ext_access_grants(tenant_id, profile);

-- ======================================================================
-- 5. REGISTRO DE CADA LEITURA (concedida ou negada)
-- ======================================================================

CREATE TABLE IF NOT EXISTS cert_ext_read_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    grant_id UUID NOT NULL,
    profile VARCHAR(28) NOT NULL CHECK (profile IN
        ('CERTIFICATION_AUDITOR', 'REGULATOR_SERVICE_OWNER')),
    resource VARCHAR(24) NOT NULL CHECK (resource IN
        ('EVIDENCE_PACK', 'REGULATORY_DATA', 'REGULATORY_REPORT',
         'PERSONAL_DATA', 'PERSONAL_DATA_MINIMUM')),
    resource_ref TEXT NOT NULL CHECK (btrim(resource_ref) <> ''),
    outcome VARCHAR(8) NOT NULL CHECK (outcome IN ('GRANTED', 'DENIED')),
    denial_reason TEXT,
    read_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK ((outcome = 'DENIED') = (denial_reason IS NOT NULL)),
    CONSTRAINT uq_ext_read_log_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_ext_read_log_grant
        FOREIGN KEY (tenant_id, grant_id)
        REFERENCES cert_ext_access_grants(tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_ext_read_log_grant
    ON cert_ext_read_log(tenant_id, grant_id);

-- ======================================================================
-- 6. ISOLAMENTO (RLS, padrao do projeto)
-- ======================================================================

SELECT register_tenant_table('cert_ext_evidence_packs');
SELECT register_tenant_table('cert_ext_evidence_pack_standards');
SELECT register_tenant_table('cert_ext_evidence_items');
SELECT register_tenant_table('cert_ext_supplier_evidence');
SELECT register_tenant_table('cert_ext_access_grants');
SELECT register_tenant_table('cert_ext_read_log');
