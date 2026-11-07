-- P2.19 — Importador: documentos anteriores à primeira produção
-- (plano v3.0, linha da P2.19; ADR-0022; ADR-0033).
--
-- Reserva do §3: `V14_3` | importação de legado | lotes de XML autorizados
-- anteriores à primeira produção, resultado da validação (assinatura,
-- protocolo, emitente, duplicidade) e origem do documento | P2.19.
-- A V14_1 (P1.2) criou lotes/staging/conciliação do cadastro e a V14_2
-- (P2.10) o histórico de 120 meses; a V14_4 (P1.40) é do importador
-- genérico de planilhas. Esta V14_3 cria SÓ o do importador de XML:
--
--   * legacy_xml_import_batches: um lote de XML = uma carga, com a
--     identidade NOS MOLDES do framework declarativo da P1.2 (PLT-16) —
--     sistema de origem (sistema antigo ou emissor do intervalo) + chave
--     externa do lote (precisão da correção A5 da revisão: declaração
--     própria neste formato, NÃO o framework/código da P1.2).
--     Reimportar a mesma chave devolve o lote existente SE ele estiver
--     concluído (report_json gravado é o marcador de conclusão —
--     correção A2 da revisão; lote sem report_json é lote interrompido e
--     a leitura dele como relatório final é recusada no código; a
--     idempotência é do código, sobre o índice único abaixo; a tabela de
--     lotes da P1.2 NÃO é reutilizada porque o source_kind dela só admite
--     CSV/SQL_DUMP/LEGACY_DB — lote de XML não é nenhum dos três).
--     report_json é o relatório por lote (totais, recusas por motivo,
--     aceitos por competência), serializado com o Json do core.
--     expected_issuer_cnpj é o emitente esperado declarado na importação
--     (dado do tenant) — contra ele cada documento é conferido.
--   * legacy_xml_import_documents: uma linha por ARQUIVO do lote, aceito
--     ou recusado, com o resultado da validação nomeado em
--     rejection_code (SIGNATURE_INVALID, MISSING_AUTHORIZATION_PROTOCOL,
--     ISSUER_MISMATCH, DUPLICATE, MODEL_NOT_ACCEPTED, ACCESS_KEY_INVALID,
--     XML_INVALID — vocabulário do enum LegacyXmlRejection do código).
--     Aceito: guarda chave de acesso, competência (primeiro dia do mês),
--     emitente, protocolo, o SHA-256 dos bytes EXATOS recebidos, a origem
--     LEGACY_IMPORT (fixa, por CHECK) e a referência da cópia no
--     armazenamento imutável da P2.18 (chave do objeto, URI e elo da
--     HashChain). O XML em si NÃO fica no banco: fica na retenção da
--     P2.18. Documento recusado nunca tem referência de retenção (CHECK).
--
-- DADO PESSOAL (ADR-0033): NENHUMA coluna guarda o destinatário. O XML
-- real do piloto traz dado pessoal do destinatário e é importado só na
-- produção (LC.10); mesmo lá, esta tabela registra só emitente (pessoa
-- jurídica), chave de acesso, protocolo e hashes. O nome do arquivo é o
-- nome que o piloto deu ao arquivo do lote — metadado da carga, não dado
-- de pessoa extraído do XML.
--
-- CLASSIFICAÇÃO P0.11 (correção A11 da revisão — declaração expressa
-- nesta migration, precedente V14_1): as tabelas passam por
-- register_tenant_table (RLS + auditoria), então as colunas candidatas ao
-- padrão do teste de cobertura PersonalDataColumns são classificadas
-- aqui: expected_issuer_cnpj e issuer_cnpj são CNPJ de PESSOA JURÍDICA
-- (o emitente prestador do tenant) — NÃO são dado pessoal e são
-- candidatas à allowlist pelo precedente fiscal_issuers.document_kind
-- citado na V14_1, sem regra de redação; file_name é metadado da carga
-- (nome dado ao arquivo pelo piloto), não conteúdo extraído do XML.
-- Nenhuma coluna guarda nome, CPF ou documento de pessoa física. O
-- veredito do teste de cobertura na CI da camada segue sendo a prova
-- final desta declaração (pendência registrada no relatório da tarefa).
--
-- IMUTABILIDADE: documento gravado não muda — correção de uma carga é um
-- lote novo (mesmo espírito do histórico da V14_2). Por isso o GRANT de
-- documents é só SELECT/INSERT. O índice único PARCIAL garante no banco o
-- que o código confere: no máximo um documento ACEITO por (tenant, chave
-- de acesso) — as recusas por duplicidade continuam registráveis.
--
-- Nenhuma coluna nova é NOT NULL sem default em tabela existente (todas
-- as tabelas são novas) e nenhum dado existente é reescrito.

-- ========================================================================
-- Lotes de XML de legado
-- ========================================================================

CREATE TABLE IF NOT EXISTS legacy_xml_import_batches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    source_system VARCHAR(60) NOT NULL,
    mapping_name VARCHAR(100) NOT NULL,
    mapping_version VARCHAR(20) NOT NULL,
    external_batch_key VARCHAR(200) NOT NULL,
    expected_issuer_cnpj VARCHAR(14) NOT NULL,
    report_json JSONB,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE legacy_xml_import_batches ADD CONSTRAINT uq_legacy_xml_batches_tenant_id
    UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_legacy_xml_batches_key
    ON legacy_xml_import_batches(tenant_id, source_system, external_batch_key);
SELECT register_tenant_table('legacy_xml_import_batches', false);

-- ========================================================================
-- Documentos de um lote (aceitos e recusados)
-- ========================================================================

CREATE TABLE IF NOT EXISTS legacy_xml_import_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    file_name VARCHAR(300) NOT NULL,
    access_key VARCHAR(44),
    competence DATE CHECK (competence IS NULL OR EXTRACT(DAY FROM competence) = 1),
    issuer_cnpj VARCHAR(14),
    authorization_protocol VARCHAR(30),
    xml_sha256 CHAR(64) NOT NULL CHECK (xml_sha256 ~ '^[0-9a-f]{64}$'),
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('ACCEPTED', 'REJECTED')),
    rejection_code VARCHAR(40)
        CHECK (rejection_code IS NULL OR rejection_code IN
            ('XML_INVALID', 'ACCESS_KEY_INVALID', 'SIGNATURE_INVALID',
             'MISSING_AUTHORIZATION_PROTOCOL', 'ISSUER_MISMATCH',
             'MODEL_NOT_ACCEPTED', 'DUPLICATE')),
    origin VARCHAR(20) NOT NULL DEFAULT 'LEGACY_IMPORT'
        CHECK (origin = 'LEGACY_IMPORT'),
    retention_key VARCHAR(300),
    retention_uri VARCHAR(500),
    retention_hash_chain CHAR(64)
        CHECK (retention_hash_chain IS NULL OR retention_hash_chain ~ '^[0-9a-f]{64}$'),
    detail_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_legacy_xml_documents_status_detail
        CHECK ((status = 'ACCEPTED' AND rejection_code IS NULL
                AND access_key IS NOT NULL AND competence IS NOT NULL
                AND issuer_cnpj IS NOT NULL AND authorization_protocol IS NOT NULL
                AND retention_key IS NOT NULL AND retention_uri IS NOT NULL
                AND retention_hash_chain IS NOT NULL)
               OR (status = 'REJECTED' AND rejection_code IS NOT NULL
                AND retention_key IS NULL AND retention_uri IS NULL
                AND retention_hash_chain IS NULL))
);
ALTER TABLE legacy_xml_import_documents ADD CONSTRAINT uq_legacy_xml_documents_tenant_id
    UNIQUE (tenant_id, id);
ALTER TABLE legacy_xml_import_documents ADD CONSTRAINT fk_legacy_xml_documents_batch
    FOREIGN KEY (tenant_id, batch_id) REFERENCES legacy_xml_import_batches(tenant_id, id);
CREATE UNIQUE INDEX idx_legacy_xml_documents_accepted_key
    ON legacy_xml_import_documents(tenant_id, access_key)
    WHERE status = 'ACCEPTED';
CREATE INDEX idx_legacy_xml_documents_batch
    ON legacy_xml_import_documents(tenant_id, batch_id);
CREATE INDEX idx_legacy_xml_documents_competence
    ON legacy_xml_import_documents(tenant_id, competence)
    WHERE status = 'ACCEPTED';
SELECT register_tenant_table('legacy_xml_import_documents', false);

-- ========================================================================
-- Grants explícitos (precedente V14_1/V14_2; documents é INSERT-only
-- além da leitura, pela imutabilidade declarada no cabeçalho)
-- ========================================================================

GRANT SELECT, INSERT, UPDATE ON legacy_xml_import_batches TO ziipsan_app;
GRANT SELECT, INSERT ON legacy_xml_import_documents TO ziipsan_app;
