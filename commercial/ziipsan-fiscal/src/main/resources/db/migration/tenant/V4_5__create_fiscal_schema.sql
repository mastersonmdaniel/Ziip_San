-- Fiscal Module Migration for Tenant Schema
-- Version: 4.5.0
-- Description: Tributos com vigencia (CBS e IBS) e motor fiscal (P1.8, plano v3.0; ADR-0003;
-- FISCAL-PORTE-JAVA §3.2, §3.4, §3.5, §3.8): certificados (so referencia ao cofre), series,
-- documentos, eventos, lotes, XML autorizado e versoes de leiaute.
--
-- Depende de: V1_2 (register_tenant_table).
--
-- NENHUM DADO E SEMEADO. Aliquota e politica do prestador, regulada por agencia e pela
-- legislacao vigente (LC 214/2025; P2.4); qualquer valor padrao inventado seria tratado como
-- dado real. As linhas de 2026 (ano de teste: CBS 0,9% e IBS 0,1%) e as de 2027 em diante sao
-- CADASTRAVEIS pelo tenant, nunca constantes no codigo (R40, ADR-0002) e nunca seed aqui.
--
-- SEGREDOS: nenhuma tabela desta migration guarda PFX, senha de certificado, CPF ou CNPJ em
-- claro. O emitente e identificado por document_hash (indice cego) + document_masked; o
-- certificado, por thumbprint + vault_ref (referencia ao cofre local, fora do banco, ADR-0033
-- §3.2). O desenho com PFX cifrado em coluna vale para a producao (plano LC.4) e NAO entra
-- aqui: no ambiente local o cofre fica fora do banco e nenhum certificado de cliente entra
-- (ADR-0033, ajuste v2.8). Um trigger de auditoria copiaria a linha inteira para o audit_log
-- (FISCAL-PORTE-JAVA §3.2); por isso fiscal_documents_xml e registrada SEM auditoria
-- (p_audit=false): o XML ja e imutavel por trigger proprio e tem hash encadeavel, e a
-- auditoria do ciclo de vida do documento vive em fiscal_document_events.
--
-- XML DE HOMOLOGACAO: o XML assinado na producao restrita (e-CNPJ da propria Ziip/TGP) NUNCA
-- entra em fiscal_documents_xml (ADR-0033, ajuste v2.9): ele vai para a area separada da
-- homologacao fiscal local e e apagado ao fim de cada ciclo de testes. Esta tabela recebe
-- SOMENTE XML do simulador ou sintetico de teste (source SIMULATOR / SYNTHETIC_TEST).
--
-- Dinheiro: NUMERIC(14,2). Aliquotas: NUMERIC(7,4) como fracao (0,009 = 0,9%).
-- Vigencia: intervalo [valid_from, valid_to] em DATAS; valid_to NULL = aberta. A restricao de
-- exclusao impede duas aliquotas do mesmo tributo vigentes no mesmo dia (P2.4: sem
-- sobreposicao de vigencias), no mesmo padrao de tariff_versions (V4_1).

-- ========================================================================
-- TRIBUTOS COM VIGENCIA
-- ========================================================================

CREATE TABLE IF NOT EXISTS tax_rates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    -- CBS, IBS, ISS, IRRF, CSRF, PIS, COFINS, INSS. CBS/IBS sao os tributos da reforma
    -- (LC 214/2025); os demais sao os do motor C# portado (FISCAL-PORTE-JAVA §1).
    tax_code VARCHAR(20) NOT NULL CHECK (tax_code IN ('CBS', 'IBS', 'ISS', 'IRRF', 'CSRF', 'PIS', 'COFINS', 'INSS')),
    -- NATIONAL por padrao; permite desdobramento futuro por UF/municipio sem mudar a tabela.
    scope VARCHAR(30) NOT NULL DEFAULT 'NATIONAL',
    valid_from DATE NOT NULL,
    valid_to DATE,
    rate NUMERIC(7,4) NOT NULL CHECK (rate >= 0 AND rate <= 1),
    -- Base legal declarada da aliquota (ex.: LC 214/2025, art. 544). NULL = sem base declarada;
    -- o motor recusa emitir com aliquota sem cobertura para a competencia? Nao: a ausencia de
    -- LINHA para a competencia e o erro (P2.4); a base legal e rastro, nao trava.
    legal_basis VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
ALTER TABLE tax_rates ADD CONSTRAINT uq_tax_rates_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE tax_rates ADD CONSTRAINT excl_fiscal_tax_rates_no_overlap
    EXCLUDE USING gist (tenant_id WITH =, tax_code WITH =, scope WITH =, daterange(valid_from, valid_to, '[]') WITH &&);
CREATE INDEX idx_tax_rates_lookup ON tax_rates(tenant_id, tax_code, scope, valid_from);

-- ========================================================================
-- EMITENTE E CERTIFICADO (SO REFERENCIA AO COFRE)
-- ========================================================================

CREATE TABLE IF NOT EXISTS fiscal_issuers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    -- Codigo IBGE do municipio do emitente (7 digitos), primeiro campo do Id da DPS.
    ibge CHAR(7) NOT NULL CHECK (ibge ~ '^[0-9]{7}$'),
    document_kind VARCHAR(4) NOT NULL CHECK (document_kind IN ('CPF', 'CNPJ')),
    -- Indice cego (HMAC-SHA-256, mesmo padrao de customers.document_hash, V3_1) e mascara
    -- para exibicao. O documento em claro NUNCA e gravado aqui: quem grava calcula o hash
    -- (ver ziipsan-security, FieldProtector) e a mascara (DocumentNumber.mask).
    document_hash VARCHAR(64) NOT NULL,
    document_masked VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE fiscal_issuers ADD CONSTRAINT uq_fiscal_issuers_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_fiscal_issuers_document ON fiscal_issuers(tenant_id, document_hash);

CREATE TABLE IF NOT EXISTS fiscal_certificates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    issuer_id UUID NOT NULL,
    -- Impressao digital do certificado (SHA-256 do certificado publico, hexadecimal).
    thumbprint VARCHAR(64) NOT NULL,
    -- Referencia ao cofre local, fora do banco (ex.: 'local-vault:<thumbprint>'). NUNCA um
    -- caminho com segredo, NUNCA o PFX e NUNCA a senha: o cofre local guarda o material e
    -- esta tabela so aponta para ele (ADR-0033 §3.2; FISCAL-PORTE-JAVA §3.2).
    vault_ref VARCHAR(300) NOT NULL,
    key_type VARCHAR(2) NOT NULL CHECK (key_type IN ('A1', 'A3')),
    not_after TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);
ALTER TABLE fiscal_certificates ADD CONSTRAINT uq_fiscal_certificates_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE fiscal_certificates ADD CONSTRAINT fk_fiscal_certificate_issuer
    FOREIGN KEY (tenant_id, issuer_id) REFERENCES fiscal_issuers(tenant_id, id);
CREATE UNIQUE INDEX idx_fiscal_certificates_thumbprint ON fiscal_certificates(tenant_id, issuer_id, thumbprint);

-- Cada acesso ao certificado e registrado (quem, quando, para que) — ADR-0033, ajuste v2.8.
CREATE TABLE IF NOT EXISTS fiscal_certificate_access_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    certificate_id UUID NOT NULL,
    accessed_by UUID NOT NULL,
    purpose VARCHAR(200) NOT NULL,
    accessed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE fiscal_certificate_access_log ADD CONSTRAINT uq_fiscal_cert_access_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE fiscal_certificate_access_log ADD CONSTRAINT fk_fiscal_cert_access_certificate
    FOREIGN KEY (tenant_id, certificate_id) REFERENCES fiscal_certificates(tenant_id, id);
CREATE INDEX idx_fiscal_cert_access_certificate ON fiscal_certificate_access_log(tenant_id, certificate_id, accessed_at);

-- ========================================================================
-- SERIES E NUMERACAO
-- ========================================================================
-- A numeracao sem numero repetido nem perdido (P1.8) depende desta tabela + da reserva
-- transacional do servico: SELECT ... FOR UPDATE na linha da serie, na mesma transacao que
-- grava o documento em RESERVED. Numero reservado nunca volta ao estoque: documento
-- rejeitado, pendente ou cancelado mantem o numero (FISCAL-PORTE-JAVA §3.4).

CREATE TABLE IF NOT EXISTS fiscal_series (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    issuer_id UUID NOT NULL,
    document_type VARCHAR(20) NOT NULL CHECK (document_type IN ('NFSE_NACIONAL', 'NFAG')),
    series VARCHAR(5) NOT NULL CHECK (series ~ '^[0-9]{5}$'),
    next_number BIGINT NOT NULL DEFAULT 1 CHECK (next_number >= 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE fiscal_series ADD CONSTRAINT uq_fiscal_series_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE fiscal_series ADD CONSTRAINT fk_fiscal_series_issuer
    FOREIGN KEY (tenant_id, issuer_id) REFERENCES fiscal_issuers(tenant_id, id);
CREATE UNIQUE INDEX idx_fiscal_series_unique ON fiscal_series(tenant_id, issuer_id, document_type, series);

-- ========================================================================
-- DOCUMENTOS
-- ========================================================================

CREATE TABLE IF NOT EXISTS fiscal_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    issuer_id UUID NOT NULL,
    series_id UUID NOT NULL,
    document_type VARCHAR(20) NOT NULL CHECK (document_type IN ('NFSE_NACIONAL', 'NFAG')),
    series VARCHAR(5) NOT NULL CHECK (series ~ '^[0-9]{5}$'),
    number BIGINT NOT NULL CHECK (number >= 1),
    -- Id da DPS, 42 posicoes (so NFSE_NACIONAL; NULL na NFAg-e ate o MOC definir a chave).
    dps_id VARCHAR(42),
    -- Chave de acesso, 50 posicoes (so apos autorizacao; NULL antes).
    access_key VARCHAR(50),
    -- Idempotencia por origem (FISCAL-PORTE-JAVA §3.4): a conta (source_type='BILL',
    -- source_id=bill_id) gera no maximo um documento vivo do tipo; reprocessar o ciclo nao
    -- emite de novo. A unicidade e por (origem, tipo): a mesma conta pode ter NFAg-e e NFS-e
    -- avulsa, mas nunca duas NFAg-e.
    source_type VARCHAR(30) NOT NULL,
    source_id UUID NOT NULL,
    -- Estados do porte (§3.3): RESERVED -> SIGNED -> TRANSMITTED -> AUTHORIZED | REJECTED |
    -- PENDING_RECONCILIATION, mais CANCELLED, REPLACED e VOIDED (inutilizado).
    status VARCHAR(30) NOT NULL CHECK (status IN ('RESERVED', 'SIGNED', 'TRANSMITTED', 'AUTHORIZED', 'REJECTED', 'PENDING_RECONCILIATION', 'CANCELLED', 'REPLACED', 'VOIDED')),
    -- SIMULATED (simulador, sem tpAmb), HOMOLOGATION (producao restrita / tpAmb=2),
    -- PRODUCTION (tpAmb=1, so com a trava da FISCAL-PORTE-JAVA §3.6).
    environment VARCHAR(20) NOT NULL CHECK (environment IN ('SIMULATED', 'HOMOLOGATION', 'PRODUCTION')),
    tp_amb SMALLINT,
    gross NUMERIC(14,2) NOT NULL CHECK (gross >= 0),
    net NUMERIC(14,2) CHECK (net IS NULL OR net >= 0),
    -- DPS nao assinada persistida ANTES do envio (porte §3.3): sem ela, uma queda apos o
    -- envio impede a reconciliacao/retransmissao com o mesmo conteudo. O XML assinado
    -- autorizado vai para fiscal_documents_xml; este campo guarda so o nao assinado.
    unsigned_xml TEXT,
    protocol VARCHAR(100),
    rejection_code VARCHAR(20),
    rejection_message VARCHAR(500),
    issued_at TIMESTAMPTZ,
    authorized_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    -- Documento que substitui este (quando status=REPLACED).
    replaced_by_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK ((environment = 'SIMULATED' AND tp_amb IS NULL)
        OR (environment = 'HOMOLOGATION' AND tp_amb = 2)
        OR (environment = 'PRODUCTION' AND tp_amb = 1))
);
ALTER TABLE fiscal_documents ADD CONSTRAINT uq_fiscal_documents_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE fiscal_documents ADD CONSTRAINT fk_fiscal_document_issuer
    FOREIGN KEY (tenant_id, issuer_id) REFERENCES fiscal_issuers(tenant_id, id);
ALTER TABLE fiscal_documents ADD CONSTRAINT fk_fiscal_document_series
    FOREIGN KEY (tenant_id, series_id) REFERENCES fiscal_series(tenant_id, id);
ALTER TABLE fiscal_documents ADD CONSTRAINT fk_fiscal_document_replaced_by
    FOREIGN KEY (tenant_id, replaced_by_id) REFERENCES fiscal_documents(tenant_id, id);
-- Numero unico por (emitente, tipo, serie): nem repetido, nem reutilizado apos rejeicao.
CREATE UNIQUE INDEX idx_fiscal_documents_number ON fiscal_documents(tenant_id, issuer_id, document_type, series, number);
CREATE UNIQUE INDEX idx_fiscal_documents_dps ON fiscal_documents(tenant_id, dps_id) WHERE dps_id IS NOT NULL;
CREATE UNIQUE INDEX idx_fiscal_documents_access_key ON fiscal_documents(tenant_id, access_key) WHERE access_key IS NOT NULL;
CREATE UNIQUE INDEX idx_fiscal_documents_source ON fiscal_documents(tenant_id, source_type, source_id, document_type);
CREATE INDEX idx_fiscal_documents_status ON fiscal_documents(tenant_id, status);

-- Desdobramento tributario do documento, por tributo, com a aliquota vigente aplicada.
CREATE TABLE IF NOT EXISTS fiscal_document_taxes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    document_id UUID NOT NULL,
    tax_code VARCHAR(20) NOT NULL CHECK (tax_code IN ('CBS', 'IBS', 'ISS', 'IRRF', 'CSRF', 'PIS', 'COFINS', 'INSS')),
    rate NUMERIC(7,4) NOT NULL CHECK (rate >= 0 AND rate <= 1),
    amount NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
    -- IBS/CBS informativos em 2026 (nao compoem o liquido nem sao retidos — TaxEngine).
    informative BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE fiscal_document_taxes ADD CONSTRAINT uq_fiscal_document_taxes_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE fiscal_document_taxes ADD CONSTRAINT fk_fiscal_document_tax_document
    FOREIGN KEY (tenant_id, document_id) REFERENCES fiscal_documents(tenant_id, id);
CREATE UNIQUE INDEX idx_fiscal_document_taxes_unique ON fiscal_document_taxes(tenant_id, document_id, tax_code);

-- ========================================================================
-- EVENTOS (CANCELAMENTO, SUBSTITUICAO, INUTILIZACAO)
-- ========================================================================

CREATE TABLE IF NOT EXISTS fiscal_document_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    document_id UUID NOT NULL,
    -- Id do evento: 'PRE' + chave de acesso (50) + tipo (6) = 59 posicoes (porte §1).
    event_id VARCHAR(59) NOT NULL,
    -- Tipo do evento no leiaute (cancelamento NFS-e: e101101, porte §1).
    event_type VARCHAR(6) NOT NULL,
    event_kind VARCHAR(20) NOT NULL CHECK (event_kind IN ('CANCELLATION', 'SUBSTITUTION', 'VOID')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('REGISTERED', 'ACCEPTED', 'REJECTED')),
    reason VARCHAR(500),
    requested_by UUID,
    -- Aprovacao dupla do cancelamento em lote (porte §3.5): os dois papeis distintos ficam
    -- registrados; o servico exige o segundo aprovador no cancelamento em lote.
    approved_by UUID,
    approved_by_second UUID,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE fiscal_document_events ADD CONSTRAINT uq_fiscal_document_events_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE fiscal_document_events ADD CONSTRAINT fk_fiscal_document_event_document
    FOREIGN KEY (tenant_id, document_id) REFERENCES fiscal_documents(tenant_id, id);
CREATE UNIQUE INDEX idx_fiscal_document_events_event ON fiscal_document_events(tenant_id, event_id);
CREATE INDEX idx_fiscal_document_events_document ON fiscal_document_events(tenant_id, document_id);

-- ========================================================================
-- LOTES (EMISSAO EM LOTE PELO WORKER, PORTE §3.3)
-- ========================================================================

CREATE TABLE IF NOT EXISTS fiscal_document_batches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    document_type VARCHAR(20) NOT NULL CHECK (document_type IN ('NFSE_NACIONAL', 'NFAG')),
    environment VARCHAR(20) NOT NULL CHECK (environment IN ('SIMULATED', 'HOMOLOGATION', 'PRODUCTION')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'PROCESSING', 'DONE', 'FAILED')),
    total_count INT NOT NULL DEFAULT 0 CHECK (total_count >= 0),
    authorized_count INT NOT NULL DEFAULT 0 CHECK (authorized_count >= 0),
    rejected_count INT NOT NULL DEFAULT 0 CHECK (rejected_count >= 0),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE fiscal_document_batches ADD CONSTRAINT uq_fiscal_document_batches_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fiscal_document_batch_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    document_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE fiscal_document_batch_items ADD CONSTRAINT uq_fiscal_document_batch_items_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE fiscal_document_batch_items ADD CONSTRAINT fk_fiscal_batch_item_batch
    FOREIGN KEY (tenant_id, batch_id) REFERENCES fiscal_document_batches(tenant_id, id);
ALTER TABLE fiscal_document_batch_items ADD CONSTRAINT fk_fiscal_batch_item_document
    FOREIGN KEY (tenant_id, document_id) REFERENCES fiscal_documents(tenant_id, id);
CREATE UNIQUE INDEX idx_fiscal_batch_items_unique ON fiscal_document_batch_items(tenant_id, batch_id, document_id);

-- ========================================================================
-- XML AUTORIZADO (SO INSERCAO; NUNCA UPDATE/DELETE)
-- ========================================================================

CREATE TABLE IF NOT EXISTS fiscal_documents_xml (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    document_id UUID NOT NULL,
    xml_gzip BYTEA NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    -- SIMULATOR: autorizado pelo simulador. SYNTHETIC_TEST: sintetico assinado com
    -- certificado de teste. NUNCA HOMOLOGATION aqui (ver cabecalho desta migration).
    source VARCHAR(30) NOT NULL CHECK (source IN ('SIMULATOR', 'SYNTHETIC_TEST')),
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE fiscal_documents_xml ADD CONSTRAINT uq_fiscal_documents_xml_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE fiscal_documents_xml ADD CONSTRAINT fk_fiscal_documents_xml_document
    FOREIGN KEY (tenant_id, document_id) REFERENCES fiscal_documents(tenant_id, id);
CREATE UNIQUE INDEX idx_fiscal_documents_xml_document ON fiscal_documents_xml(tenant_id, document_id);

CREATE OR REPLACE FUNCTION fiscal_documents_xml_immutable() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'fiscal_documents_xml e somente-insercao: UPDATE e DELETE sao recusados';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER fiscal_documents_xml_no_update
    BEFORE UPDATE ON fiscal_documents_xml
    FOR EACH ROW EXECUTE FUNCTION fiscal_documents_xml_immutable();
CREATE TRIGGER fiscal_documents_xml_no_delete
    BEFORE DELETE ON fiscal_documents_xml
    FOR EACH ROW EXECUTE FUNCTION fiscal_documents_xml_immutable();

-- ========================================================================
-- VERSOES DE LEIAUTE (NT x SCHEMA, ADR-0003 §5.5)
-- ========================================================================

CREATE TABLE IF NOT EXISTS fiscal_layout_versions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    document_type VARCHAR(20) NOT NULL CHECK (document_type IN ('NFSE_NACIONAL', 'NFAG')),
    nt VARCHAR(50) NOT NULL,
    schema_version VARCHAR(20) NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
ALTER TABLE fiscal_layout_versions ADD CONSTRAINT uq_fiscal_layout_versions_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_fiscal_layout_versions_unique ON fiscal_layout_versions(tenant_id, document_type, nt, schema_version);

-- ========================================================================
-- ISOLAMENTO E AUDITORIA
-- ========================================================================
-- fiscal_documents_xml sem auditoria (p_audit=false): o trigger de auditoria copiaria o
-- XML inteiro para o audit_log, duplicando o armazenamento sem valor (ver cabecalho).

SELECT register_tenant_table('tax_rates');
SELECT register_tenant_table('fiscal_issuers');
SELECT register_tenant_table('fiscal_certificates');
SELECT register_tenant_table('fiscal_certificate_access_log');
SELECT register_tenant_table('fiscal_series');
SELECT register_tenant_table('fiscal_documents');
SELECT register_tenant_table('fiscal_document_taxes');
SELECT register_tenant_table('fiscal_document_events');
SELECT register_tenant_table('fiscal_document_batches');
SELECT register_tenant_table('fiscal_document_batch_items');
SELECT register_tenant_table('fiscal_documents_xml', true, false);
SELECT register_tenant_table('fiscal_layout_versions');
