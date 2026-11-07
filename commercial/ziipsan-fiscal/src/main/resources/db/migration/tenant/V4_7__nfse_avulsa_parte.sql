-- V4_7 — RESERVED, SHARED migration (plan v3.0, section 3): "NFAg-e e NFS-e avulsa:
-- leiaute, contingência, inutilização".
--
-- *** THIS FILE NOW CONTAINS BOTH PARTS. ***
-- PART 1 (below, first) is the NFS-e avulsa part of P3.6: its SQL is VERBATIM the P3.6
-- delivery (not one SQL line changed); only this header was updated to declare both parts.
-- PART 2 (appended after part 1) is the NFAg-e part of P2.6 (modelo 75): bills/bill_items
-- fiscal columns, the NFAg-e extension tables over the fiscal dictionary of V4_5 (P1.8)
-- and the payment link (NT 2026.001). P2.6 creates NO new fiscal dictionary: issuers,
-- series, documents, states, access key, protocol and events live in fiscal_issuers /
-- fiscal_series / fiscal_documents of V4_5 (DAD-18, dictionary of 23b section 3.13);
-- part 2 only EXTENDS that dictionary for the modelo 75 and links it to the bill.
--
-- P3.6 part (acceptance): avulso service types carry the IBS/CBS obligation stage of Ato
-- Conjunto RFB/CGIBS 4/2026, and each NFS-e generated from a service order records the
-- stage applied, the issue date and whether the IBS/CBS groups were included.
-- Stage codes mirror br.com.ziipsan.commercial.fiscal.nfse.NfseEtapa:
--   ISS_FORA_HIPOTESES_ESPECIAIS (from 2026-10-01), DEMAIS_HIPOTESES (from 2026-12-01),
--   SIMPLES_NACIONAL (from 2027-01-01).
--
-- NO SEEDED DATA: service types are tenant cadastre, never migration seed.

CREATE TABLE IF NOT EXISTS fiscal_avulso_service_types (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    description VARCHAR(200) NOT NULL,
    nfse_etapa VARCHAR(40) NOT NULL CHECK (nfse_etapa IN
        ('ISS_FORA_HIPOTESES_ESPECIAIS', 'DEMAIS_HIPOTESES', 'SIMPLES_NACIONAL')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100)
);
ALTER TABLE fiscal_avulso_service_types ADD CONSTRAINT uq_fiscal_avulso_types_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_fiscal_avulso_types_code ON fiscal_avulso_service_types(tenant_id, code);

CREATE TABLE IF NOT EXISTS fiscal_nfse_avulsas (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    service_order_id UUID NOT NULL,
    service_type_code VARCHAR(40) NOT NULL,
    etapa_aplicavel VARCHAR(40) NOT NULL CHECK (etapa_aplicavel IN
        ('ISS_FORA_HIPOTESES_ESPECIAIS', 'DEMAIS_HIPOTESES', 'SIMPLES_NACIONAL')),
    issue_date DATE NOT NULL,
    prestador_cnpj VARCHAR(14) NOT NULL,
    tomador_cnpj VARCHAR(14) NOT NULL,
    service_amount NUMERIC(14,2) NOT NULL CHECK (service_amount > 0),
    com_grupos_ibs_cbs BOOLEAN NOT NULL,
    ambiente VARCHAR(30) NOT NULL CHECK (ambiente IN ('PRODUCAO_RESTRITA')),
    status VARCHAR(30) NOT NULL CHECK (status IN ('GERADA_NAO_TRANSMITIDA')),
    dps_payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100)
);
ALTER TABLE fiscal_nfse_avulsas ADD CONSTRAINT uq_fiscal_nfse_avulsas_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_fiscal_nfse_avulsas_os ON fiscal_nfse_avulsas(tenant_id, service_order_id);
CREATE INDEX idx_fiscal_nfse_avulsas_issue ON fiscal_nfse_avulsas(tenant_id, issue_date);

-- ========================================================================
-- ISOLATION AND AUDIT (same helper used by V4_6)
-- ========================================================================

SELECT register_tenant_table('fiscal_avulso_service_types');
SELECT register_tenant_table('fiscal_nfse_avulsas');

-- ========================================================================
-- PART 2 — NFAg-e (modelo 75), P2.6
-- ========================================================================
--
-- The fiscal document itself (issuer in fiscal_issuers, document_type, modelo 75,
-- series/number without gaps via fiscal_series, unique access_key with check digit,
-- unique (source_type, source_id) among live documents, the 9 states RESERVED, SIGNED,
-- TRANSMITTED, AUTHORIZED, REJECTED, PENDING_RECONCILIATION, CANCELLED, REPLACED and
-- VOIDED, environment with the production triple lock, issued_at in America/Sao_Paulo,
-- authorization/rejection protocol, xml_sha256 and xml_storage_uri, substitution and
-- cancellation event) is the V4_5 dictionary (P1.8, DAD-18) and is NOT recreated here.
-- This part adds only what is specific to the modelo 75 and to the bill link:
--
-- 1. bills.fiscal_document_id: the bill points at its ONE live NFAg-e (the document row
--    in fiscal_documents). Numbering without gaps and voiding (inutilização) are the
--    dictionary's job (fiscal_series / fiscal_documents), never duplicated here.
-- 2. bill_items: fiscal_item_code and the per-item IBS/CBS group (NT 2026.002).
-- 3. fiscal_nfage_details: the modelo-75 extension of fiscal_documents (recipient
--    municipality IBGE from the P1.3 address, DAD-04; contingency with reason; layout
--    version; MOC-conditional recipient fields).
-- 4. fiscal_nfage_payments: link between the NFAg-e and payments (NT 2026.001).
-- 5. fiscal_nfage_events: cancellation/substitution/voiding events of the NFAg-e, in
--    the same separate-event style as the platform mirror of the dictionary.
--
-- The bill-total CHECK with tax (DAD-17) already exists in V4_6
-- (chk_bills_total_with_withholding_tax: total = water + sewer + debits - credits
-- - taxes, withholding only) and is NOT recreated or changed here. The NT 2026.004
-- rule (from 2027 the DOCUMENT total is net + IBS + CBS) is a fiscal-document rule,
-- implemented in code (NfageTotalRule); it does not rewrite the bill total.
--
-- PARTITIONING NOTE (integration, honest): bills and bill_items are recreated by
-- V4_14 with an explicit column list. V4_7 runs before V4_14 in Flyway order, so the
-- columns/constraints added here on bills/bill_items must be carried into the V4_14
-- recreation (or applied after it) by the integration layer; this file does not edit
-- V4_14. The unique index below includes the partition key (reference_month), as
-- PostgreSQL requires for unique indexes on partitioned tables.
--
-- NO SEEDED DATA in this part either: issuers, series and rates are tenant cadastre.

-- ------------------------------------------------------------------------
-- 1. Bill -> NFAg-e (one live NFAg-e per bill, one bill per document)
-- ------------------------------------------------------------------------

ALTER TABLE bills ADD COLUMN IF NOT EXISTS fiscal_document_id UUID;
ALTER TABLE bills ADD CONSTRAINT fk_bill_fiscal_document
    FOREIGN KEY (tenant_id, fiscal_document_id) REFERENCES fiscal_documents(tenant_id, id);
CREATE INDEX idx_bills_fiscal_document ON bills(tenant_id, fiscal_document_id)
    WHERE fiscal_document_id IS NOT NULL;
-- A fiscal document is linked to at most one bill within the bill's reference month
-- (reference_month is the partition key and must be part of the unique index). The
-- cross-month guarantee is the dictionary's live-origin uniqueness
-- (source_type, source_id) in fiscal_documents plus emission idempotency in code.
CREATE UNIQUE INDEX uq_bills_fiscal_document ON bills(tenant_id, reference_month, fiscal_document_id)
    WHERE fiscal_document_id IS NOT NULL;
COMMENT ON COLUMN bills.fiscal_document_id IS
    'NFAg-e viva da conta (fiscal_documents, modelo 75, P2.6/V4_7). NULL enquanto a conta nao tem NFAg-e.';

-- ------------------------------------------------------------------------
-- 2. Bill items: fiscal item code and the per-item IBS/CBS group (NT 2026.002)
-- ------------------------------------------------------------------------
-- CONDITIONAL ON THE DEFINITIVE MOC: fiscal_item_code (codigo de classificacao do
-- item), the CST and cClassTrib codes and the very presence/shape of the per-item
-- IBS/CBS group depend on the definitive MOC layout version. The columns are
-- therefore all nullable: an item without the group is an item whose layout version
-- does not carry it yet, never an item with invented values. IBS/CBS rates and
-- values come from the bill item (tax cadastre), never from this migration.

ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS fiscal_item_code VARCHAR(60);
ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS ibs_cbs_cst VARCHAR(10);
ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS ibs_cbs_cclass_trib VARCHAR(20);
ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS ibs_cbs_base NUMERIC(14,2);
ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS ibs_rate NUMERIC(7,4);
ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS ibs_value NUMERIC(14,2);
ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS cbs_rate NUMERIC(7,4);
ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS cbs_value NUMERIC(14,2);
ALTER TABLE bill_items ADD CONSTRAINT chk_bill_items_ibs_cbs_nonnegative
    CHECK ((ibs_cbs_base IS NULL OR ibs_cbs_base >= 0)
       AND (ibs_rate IS NULL OR ibs_rate >= 0)
       AND (ibs_value IS NULL OR ibs_value >= 0)
       AND (cbs_rate IS NULL OR cbs_rate >= 0)
       AND (cbs_value IS NULL OR cbs_value >= 0));
-- A tax value without its calculation base is a broken item, never a partial group.
ALTER TABLE bill_items ADD CONSTRAINT chk_bill_items_ibs_cbs_base
    CHECK ((ibs_value IS NULL OR ibs_cbs_base IS NOT NULL)
       AND (cbs_value IS NULL OR ibs_cbs_base IS NOT NULL));
COMMENT ON COLUMN bill_items.fiscal_item_code IS
    'Codigo de classificacao fiscal do item da NFAg-e (NT 2026.002). CONDICIONAL ao MOC definitivo: obrigatoriedade e dominio seguem a versao do leiaute.';
COMMENT ON COLUMN bill_items.ibs_cbs_cst IS
    'CST do grupo IBS/CBS por item (NT 2026.002). CONDICIONAL ao MOC definitivo.';
COMMENT ON COLUMN bill_items.ibs_cbs_cclass_trib IS
    'cClassTrib do grupo IBS/CBS por item (NT 2026.002). CONDICIONAL ao MOC definitivo.';
COMMENT ON COLUMN bill_items.ibs_cbs_base IS
    'Base de calculo do grupo IBS/CBS por item (NT 2026.002). CONDICIONAL ao MOC definitivo.';
COMMENT ON COLUMN bill_items.ibs_rate IS
    'Aliquota do IBS do item (NT 2026.002): vem do cadastro tributario, nunca de constante. CONDICIONAL ao MOC definitivo.';
COMMENT ON COLUMN bill_items.ibs_value IS
    'Valor do IBS do item (NT 2026.002). CONDICIONAL ao MOC definitivo.';
COMMENT ON COLUMN bill_items.cbs_rate IS
    'Aliquota da CBS do item (NT 2026.002): vem do cadastro tributario, nunca de constante. CONDICIONAL ao MOC definitivo.';
COMMENT ON COLUMN bill_items.cbs_value IS
    'Valor da CBS do item (NT 2026.002). CONDICIONAL ao MOC definitivo.';

-- ------------------------------------------------------------------------
-- 3. NFAg-e extension of fiscal_documents (modelo 75)
-- ------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS fiscal_nfage_details (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    fiscal_document_id UUID NOT NULL,
    -- Versao do leiaute (MOC) sob a qual o documento foi montado: os campos
    -- condicionais abaixo so ganham sentido lidos com esta versao.
    layout_version VARCHAR(20) NOT NULL,
    -- Municipio do destinatario (codigo IBGE de 7 digitos), do endereco da P1.3 (DAD-04).
    recipient_municipality_ibge CHAR(7) NOT NULL CHECK (recipient_municipality_ibge ~ '^[0-9]{7}$'),
    -- CONDICIONAL AO MOC DEFINITIVO: destinatario sem CPF ou CNPJ. Enquanto o MOC nao
    -- define o indicador, a coluna registra o fato declarado na emissao; o documento
    -- cifrado/hash abaixo segue a mesma condicionalidade.
    recipient_without_document BOOLEAN NOT NULL DEFAULT FALSE,
    -- CONDICIONAL AO MOC DEFINITIVO: documento do destinatario SOMENTE cifrado
    -- (FieldCipher do cadastro), nunca em claro; o hash e o indice cego SHA-256 da
    -- forma normalizada validada pela P1.39 (RAS-03).
    recipient_document_encrypted TEXT,
    recipient_document_hash CHAR(64) CHECK (recipient_document_hash IS NULL OR recipient_document_hash ~ '^[0-9a-f]{64}$'),
    -- Contingencia: modo e motivo andam juntos — contingencia sem motivo e erro.
    contingency_mode VARCHAR(20) NOT NULL DEFAULT 'NORMAL'
        CHECK (contingency_mode IN ('NORMAL', 'CONTINGENCY')),
    contingency_reason VARCHAR(500),
    contingency_started_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100),
    CHECK ((contingency_mode = 'CONTINGENCY') = (contingency_reason IS NOT NULL)),
    -- Correcao da revisao cruzada da P2.6: a versao original deste CHECK exigia
    -- documento cifrado ou hash para TODO destinatario com documento, o que
    -- contradiz a condicionalidade ao MOC destes campos (ambos podem estar
    -- ausentes ate o MOC defini-los) e bloquearia a persistencia do caminho
    -- normal. A contradicao real — a mesma que NfageEmissionService valida — e
    -- a inversa: destinatario declarado SEM documento nao pode carregar
    -- documento cifrado nem hash.
    CHECK (NOT recipient_without_document OR (recipient_document_encrypted IS NULL AND recipient_document_hash IS NULL))
);
ALTER TABLE fiscal_nfage_details ADD CONSTRAINT uq_fiscal_nfage_details_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE fiscal_nfage_details ADD CONSTRAINT fk_nfage_detail_document
    FOREIGN KEY (tenant_id, fiscal_document_id) REFERENCES fiscal_documents(tenant_id, id);
CREATE UNIQUE INDEX uq_fiscal_nfage_details_document ON fiscal_nfage_details(tenant_id, fiscal_document_id);
CREATE INDEX idx_fiscal_nfage_details_recipient_mun ON fiscal_nfage_details(tenant_id, recipient_municipality_ibge);

-- ------------------------------------------------------------------------
-- 4. Link with payments (NT 2026.001)
-- ------------------------------------------------------------------------
-- payment_id is the payment's id in the collection module; no cross-module FK is
-- declared here (the payments table is not part of this migration's dictionary),
-- the link is by id, unique per (document, payment).

CREATE TABLE IF NOT EXISTS fiscal_nfage_payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    nfage_detail_id UUID NOT NULL,
    payment_id UUID NOT NULL,
    payment_reference VARCHAR(100),
    linked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100)
);
ALTER TABLE fiscal_nfage_payments ADD CONSTRAINT uq_fiscal_nfage_payments_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE fiscal_nfage_payments ADD CONSTRAINT fk_nfage_payment_detail
    FOREIGN KEY (tenant_id, nfage_detail_id) REFERENCES fiscal_nfage_details(tenant_id, id);
CREATE UNIQUE INDEX uq_fiscal_nfage_payments_doc_payment ON fiscal_nfage_payments(tenant_id, nfage_detail_id, payment_id);
CREATE INDEX idx_fiscal_nfage_payments_payment ON fiscal_nfage_payments(tenant_id, payment_id);

-- ------------------------------------------------------------------------
-- 5. NFAg-e events: cancellation, substitution, voiding (inutilização)
-- ------------------------------------------------------------------------
-- The document's own lifecycle states stay in fiscal_documents (V4_5); this table
-- records the modelo-75 events that change it from outside the emission flow:
-- CANCELLATION (evento de cancelamento), SUBSTITUTION (documento substituto em
-- related_document_id; o substituido vai a REPLACED no dicionario) e VOIDING
-- (inutilizacao de faixa/numero reservado; o documento vai a VOIDED).

CREATE TABLE IF NOT EXISTS fiscal_nfage_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    fiscal_document_id UUID NOT NULL,
    event_type VARCHAR(20) NOT NULL CHECK (event_type IN ('CANCELLATION', 'SUBSTITUTION', 'VOIDING')),
    protocol VARCHAR(100),
    reason VARCHAR(500) NOT NULL,
    related_document_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100),
    CHECK (event_type <> 'SUBSTITUTION' OR related_document_id IS NOT NULL)
);
ALTER TABLE fiscal_nfage_events ADD CONSTRAINT uq_fiscal_nfage_events_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE fiscal_nfage_events ADD CONSTRAINT fk_nfage_event_document
    FOREIGN KEY (tenant_id, fiscal_document_id) REFERENCES fiscal_documents(tenant_id, id);
CREATE INDEX idx_fiscal_nfage_events_document ON fiscal_nfage_events(tenant_id, fiscal_document_id, event_type);

-- ========================================================================
-- ISOLATION AND AUDIT — PART 2 (same helper used by V4_6)
-- ========================================================================

SELECT register_tenant_table('fiscal_nfage_details');
SELECT register_tenant_table('fiscal_nfage_payments');
SELECT register_tenant_table('fiscal_nfage_events');
