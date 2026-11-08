-- P3.34 — Assinatura eletronica dos documentos do consumidor e do tenant
-- (plano v3.0 §3, tenant V1_14, modulo platform): "assinaturas eletronicas por
-- documento (nivel da Lei 14.063/2020, signatario, carimbo de tempo, verificacao)".
--
-- O QUE MUDA
--   * document_signatures: uma linha por assinatura produzida pelo servico
--     (interface DocumentSigner): nivel SIMPLE/ADVANCED/QUALIFIED da Lei
--     14.063/2020, tipo de documento candidato da spec, hash SHA-256 do
--     conteudo assinado, signatario, instante, codigo publico de verificacao
--     e o CHECKSUM de integridade do registro (verification_digest: SHA-256
--     SEM SEGREDO de hash+ codigo+instante — detecta corrupcao e reescrita
--     inconsistente, NAO protege contra adulteracao por quem escreve na
--     tabela; correcao D1 da revisao cruzada da P3.34, ver SignatureVerification).
--     Para a QUALIFICADA: formato (PAdES), PSC (sign.psc_provider), politica
--     de assinatura declarada pelo PSC (tabela do DOC-ICP-15.03, conferida
--     na homologacao) e carimbo do tempo da ACT (sign.timestamp_authority,
--     RFC 3161) com o hash do token. psc_provider e timestamp_authority sao
--     colunas EXCLUSIVAS da qualificada: o provedor da AVANCADA vive so na
--     evidencia (correcao D5) e o CHECK abaixo veda as duas colunas fora
--     da qualificada. A evidencia completa fica em evidence (jsonb montado
--     pela classe Json do core): na simples, a referencia ao aceite da
--     P1.24 (usuario, codigo/versao do documento, hash, data, IP,
--     autenticacao); na avancada, provedor + vinculo de identidade + hash
--     do selo; na qualificada, PSC + politica + certificado + carimbo.
--   * signature_verification_attempts: cada verificacao publica pelo codigo
--     (pagina de validacao apontada pelo QR) registrada com o desfecho —
--     inclusive RATE_LIMITED, o registro da consulta que estourou o limite
--     de tentativas por codigo/origem (correcao D7).
--
-- A assinatura SIMPLES nao duplica o aceite: a evidencia vive em
-- platform.document_acceptance (core V9_5, P1.24) e esta tabela guarda a
-- assinatura que a referencia — sem infraestrutura paralela (regra da P1.24).
--
-- Sem FK para generated_documents/documents: a referencia do documento e
-- textual (document_reference) porque os consumidores (P4.1, ED-19, ED-23)
-- assinam documentos de donos diferentes; inventar uma FK unica amarraria o
-- servico a um so dono. Sem semente: nenhum nivel minimo, PSC ou ACT nasce
-- aqui — sign.min_level.*, sign.psc_provider e sign.timestamp_authority sao
-- parametros classe d do tenant (parameter_versions da V1_5).
--
-- CHECKs espelham EXATAMENTE os enums do codigo (conferidos pelo
-- SigningMigrationConsistencyTest): level = SignatureLevel;
-- document_type = SignableDocumentType.

CREATE TABLE IF NOT EXISTS document_signatures (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    document_type VARCHAR(40) NOT NULL CHECK (document_type IN ('ADHESION_CONTRACT', 'INSTALLMENT_TERM', 'LAB_REPORT', 'SERVICE_ORDER_ACCEPTANCE', 'SOCIAL_TARIFF_SELF_DECLARATION', 'DEBIT_AUTHORIZATION', 'TENANT_DELIVERY_DOCUMENT')),
    document_reference VARCHAR(200) NOT NULL CHECK (btrim(document_reference) <> ''),
    level VARCHAR(20) NOT NULL CHECK (level IN ('SIMPLE', 'ADVANCED', 'QUALIFIED')),
    content_hash CHAR(64) NOT NULL CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    signer_subject VARCHAR(200) NOT NULL CHECK (btrim(signer_subject) <> ''),
    signed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    verification_code VARCHAR(64) NOT NULL CHECK (btrim(verification_code) <> ''),
    verification_digest CHAR(64) NOT NULL CHECK (verification_digest ~ '^[0-9a-f]{64}$'),
    signature_format VARCHAR(20),
    psc_provider VARCHAR(200),
    signature_policy_id VARCHAR(200),
    timestamp_authority VARCHAR(200),
    timestamp_token_hash CHAR(64) CHECK (timestamp_token_hash IS NULL OR timestamp_token_hash ~ '^[0-9a-f]{64}$'),
    evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Qualificada e um todo: formato PAdES + PSC + politica + ACT + token.
    CHECK (level <> 'QUALIFIED' OR (signature_format = 'PAdES' AND psc_provider IS NOT NULL
        AND signature_policy_id IS NOT NULL AND timestamp_authority IS NOT NULL
        AND timestamp_token_hash IS NOT NULL)),
    -- Quem nao e qualificada nao declara aparato de qualificada: formato,
    -- PSC, politica, ACT e token sao todos NULL fora da QUALIFIED (D5).
    CHECK (level = 'QUALIFIED' OR (signature_format IS NULL AND psc_provider IS NULL
        AND signature_policy_id IS NULL AND timestamp_authority IS NULL
        AND timestamp_token_hash IS NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_document_signatures_verification_code
    ON document_signatures (verification_code);
CREATE INDEX IF NOT EXISTS idx_document_signatures_document
    ON document_signatures (tenant_id, document_type, document_reference);
ALTER TABLE document_signatures ADD CONSTRAINT uq_document_signatures_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS signature_verification_attempts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID,
    verification_code VARCHAR(64) NOT NULL CHECK (btrim(verification_code) <> ''),
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('VALID', 'RECORD_TAMPERED', 'NOT_FOUND', 'RATE_LIMITED')),
    source_ip VARCHAR(64),
    verified_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_signature_verification_attempts_code
    ON signature_verification_attempts (verification_code, verified_at);

-- So document_signatures e registrada (RLS + trilha): a verificacao publica
-- roda FORA de contexto de tenant (o codigo e a credencial, como na P3.35) e
-- a tentativa NOT_FOUND nao tem tenant — registra-la sob RLS de tenant a
-- esconderia justamente de quem audita tentativas sobre codigos inexistentes.
-- Precedente: consumer_portal_link_attempts da V8_2 tambem nao e registrada.
SELECT register_tenant_table('document_signatures');
