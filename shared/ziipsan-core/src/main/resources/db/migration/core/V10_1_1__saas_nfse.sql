-- Core Migration — Version: 10.1.1
-- Description: P2.13 — NFS-e da venda SaaS pelo motor fiscal portado
-- (ADR-0003, ADR-0019), no schema `platform`.
--
-- Resolve os pontos de extensao deixados pela P2.12 (core V10_1):
--   * `saas_invoice.nfse_document_ref` passa a ser resolvido: apos a
--     emissao, guarda o Id da DPS (42 posicoes) da NFS-e autorizada.
--   * O numero da fatura continua `FAT-<seq global>` (identidade da
--     fatura no contrato); a NFS-e tem numeracao propria, por serie
--     fiscal (`platform.saas_nfse_series`), reservada pelo motor — a
--     P2.12 ja previa que a numeracao por serie chegaria com o motor.
--   * O tomador sai de `platform.tenant_profile` (fonte unica, DAD-32),
--     nunca do `contractor_cnpj` do contrato: o perfil e o cadastro
--     vigente do tomador; divergencia entre perfil e contrato fica
--     registrada no detalhe da auditoria da emissao, sem bloquear.
--
-- Desenho (fronteira de modulos, P0.47): o modulo `platform` NAO
-- importa o motor fiscal — ele so guarda o contexto, as aliquotas e
-- a leitura da NFS-e. O motor (modulo `commercial/ziipsan-fiscal`) e
-- orquestrado pelo modulo `app` (`SaaSNfseService`), sobre o
-- repositorio `PlatformFiscalDocumentRepository`, que persiste nas
-- tabelas abaixo. O prestador e a propria plataforma (Ziip SAN); o
-- tomador e o tenant do contrato.
--
-- SEGREDOS E DADOS: nenhuma linha e semeada aqui. O contexto fiscal
-- da plataforma (CNPJ real da Ziip, municipio, inscricao municipal,
-- regime, serie) e dado do dono, nao inventado: ele e registrado
-- pelo servico (`SaaSFiscalRegistry.registerContext`), e a emissao
-- sem contexto ativo e recusada. As aliquotas seguem a mesma regra
-- da V4_5 (cadastradas, com vigencia, nunca constantes e nunca seed
-- — os valores de teste vivem nos testes, nao aqui). Nenhuma tabela
-- guarda PFX, senha de certificado ou documento em claro alem do
-- que `tenant_profile` e `saas_contract` ja guardam no mesmo schema:
-- o CNPJ do prestador fica em claro (como no perfil) e o indice cego
-- (SHA-256 da forma normalizada) acompanha, no padrao fiscal.
--
-- AMBIENTE: `SIMULATED` autoriza no simulador do porte. `HOMOLOGATION`
-- e `PRODUCTION` sao recusados antes de qualquer escrita (falha
-- fechada, `RestrictedProductionUnavailableException`): o cliente
-- mTLS real da Sefin e o e-CNPJ da Ziip/TGP sao pendencias do dono
-- (P1.8), e nenhuma emissao fora do simulador e fingida aqui. O XML
-- autorizado guardado e o do simulador (fonte SIMULATOR), como na
-- V4_5; o XML da producao restrita nunca entra nestas tabelas.
--
-- Versao livre verificada: `V10_2` esta reservada a P2.15; `V10_1_1`
-- e a faixa desta tarefa, dentro da faixa da P2.12. Idempotente
-- (IF NOT EXISTS / ON CONFLICT DO NOTHING) no que e semente — e
-- aqui nao ha semente.

-- ======================================================================
-- Contexto fiscal da plataforma (o prestador da NFS-e do SaaS)
-- ======================================================================

CREATE TABLE IF NOT EXISTS platform.platform_fiscal_context (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    legal_name VARCHAR(200) NOT NULL,
    -- CHAR(14) sem mascara; alfanumerico (IN RFB 2.229/2024) validado
    -- por `DocumentNumber` na aplicacao, como em tenant_profile.
    cnpj CHAR(14) NOT NULL CHECK (cnpj ~ '^[0-9A-Z]{14}$'),
    -- Indice cego do CNPJ do prestador (SHA-256 da forma normalizada),
    -- no padrao de fiscal_issuers (V4_5), sem cifrar o documento aqui
    -- porque o schema platform ja o guarda em claro no perfil.
    document_hash CHAR(64) NOT NULL CHECK (document_hash ~ '^[0-9a-f]{64}$'),
    municipality_ibge CHAR(7) NOT NULL CHECK (municipality_ibge ~ '^[0-9]{7}$'),
    -- Serie fiscal da NFS-e da plataforma (5 digitos, padrao do porte).
    series CHAR(5) NOT NULL DEFAULT '00001' CHECK (series ~ '^[0-9]{5}$'),
    environment VARCHAR(20) NOT NULL DEFAULT 'SIMULATED'
        CHECK (environment IN ('SIMULATED', 'HOMOLOGATION', 'PRODUCTION')),
    tax_regime VARCHAR(20)
        CHECK (tax_regime IN ('SIMPLES_NACIONAL', 'LUCRO_PRESUMIDO', 'LUCRO_REAL', 'EXEMPT')),
    certificate_vault_ref VARCHAR(300),
    -- Verdadeiro ate o dono registrar o contexto real: um contexto de
    -- producao nunca pode ser test_only (trava no servico, nao so aqui,
    -- porque o servico distingue SIMULATED forcado de PRODUCTION).
    test_only BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Um unico contexto ativo por vez: o prestador da plataforma e um so.
CREATE UNIQUE INDEX IF NOT EXISTS uq_platform_fiscal_context_active
    ON platform.platform_fiscal_context ((status = 'ACTIVE'))
    WHERE status = 'ACTIVE';

-- ======================================================================
-- Aliquotas da plataforma (vigencia, como tax_rates da V4_5, sem seed)
-- ======================================================================

CREATE TABLE IF NOT EXISTS platform.saas_fiscal_tax_rate (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tax_code VARCHAR(20) NOT NULL
        CHECK (tax_code IN ('CBS', 'IBS', 'ISS', 'IRRF', 'CSRF', 'PIS', 'COFINS', 'INSS')),
    scope VARCHAR(30) NOT NULL DEFAULT 'NATIONAL',
    valid_from DATE NOT NULL,
    valid_to DATE,
    rate NUMERIC(7, 4) NOT NULL CHECK (rate >= 0 AND rate <= 1),
    legal_basis VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE INDEX IF NOT EXISTS idx_saas_fiscal_tax_rate_lookup
    ON platform.saas_fiscal_tax_rate (tax_code, scope, valid_from);

-- ======================================================================
-- Serie fiscal e documento da NFS-e do SaaS (espelho, no schema
-- platform, de fiscal_series / fiscal_documents da V4_5)
-- ======================================================================

CREATE TABLE IF NOT EXISTS platform.saas_nfse_series (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    context_id UUID NOT NULL REFERENCES platform.platform_fiscal_context (id),
    series CHAR(5) NOT NULL CHECK (series ~ '^[0-9]{5}$'),
    next_number BIGINT NOT NULL DEFAULT 1 CHECK (next_number >= 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_saas_nfse_series_context_series UNIQUE (context_id, series)
);

CREATE TABLE IF NOT EXISTS platform.saas_nfse (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    -- Uma NFS-e por fatura: a unicidade de origem do motor
    -- (source SAAS_INVOICE + invoice_id) vive neste UNIQUE e no
    -- indice de origem abaixo; a segunda emissao devolve a existente.
    invoice_id UUID NOT NULL UNIQUE REFERENCES platform.saas_invoice (id),
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    context_id UUID NOT NULL REFERENCES platform.platform_fiscal_context (id),
    source_type VARCHAR(30) NOT NULL DEFAULT 'SAAS_INVOICE',
    source_id UUID NOT NULL,
    series CHAR(5) NOT NULL CHECK (series ~ '^[0-9]{5}$'),
    number BIGINT NOT NULL CHECK (number >= 1),
    -- Id da DPS (42 posicoes), preenchido na assinatura; NULL enquanto
    -- RESERVED. E o mesmo valor gravado em nfse_document_ref.
    dps_id CHAR(42) CHECK (dps_id IS NULL OR dps_id ~ '^[0-9A-Z]{42}$'),
    access_key CHAR(50) CHECK (access_key IS NULL OR access_key ~ '^[0-9A-Z]{50}$'),
    protocol VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'RESERVED'
        CHECK (status IN ('RESERVED', 'SIGNED', 'TRANSMITTED', 'PENDING_RECONCILIATION',
                          'AUTHORIZED', 'REJECTED', 'CANCELLED', 'REPLACED')),
    environment VARCHAR(20) NOT NULL DEFAULT 'SIMULATED'
        CHECK (environment IN ('SIMULATED', 'HOMOLOGATION', 'PRODUCTION')),
    tp_amb INTEGER CHECK (tp_amb IS NULL OR tp_amb IN (1, 2)),
    gross NUMERIC(19, 2) NOT NULL CHECK (gross >= 0),
    net NUMERIC(19, 2),
    taker_name VARCHAR(200),
    -- Tomador, copiado de tenant_profile no momento da emissao (fonte
    -- unica DAD-32; a copia congela o tomador do documento emitido).
    taker_cnpj CHAR(14) CHECK (taker_cnpj IS NULL OR taker_cnpj ~ '^[0-9A-Z]{14}$'),
    -- XML da DPS nao assinado (texto) e XML autorizado (gzip), com
    -- hash SHA-256 do XML autorizado — o download da P2.14 le daqui.
    dps_xml TEXT,
    xml_gzip BYTEA,
    xml_sha256 CHAR(64) CHECK (xml_sha256 IS NULL OR xml_sha256 ~ '^[0-9a-f]{64}$'),
    xml_source VARCHAR(30),
    rejection_code VARCHAR(50),
    rejection_message VARCHAR(500),
    replaced_by_id UUID REFERENCES platform.saas_nfse (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_saas_nfse_source UNIQUE (source_type, source_id, series),
    CONSTRAINT uq_saas_nfse_series_number UNIQUE (context_id, series, number)
);

CREATE INDEX IF NOT EXISTS idx_saas_nfse_tenant_status
    ON platform.saas_nfse (tenant_id, status);

CREATE TABLE IF NOT EXISTS platform.saas_nfse_tax (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nfse_id UUID NOT NULL REFERENCES platform.saas_nfse (id),
    tax_code VARCHAR(20) NOT NULL,
    rate NUMERIC(7, 4) NOT NULL CHECK (rate >= 0 AND rate <= 1),
    amount NUMERIC(19, 2) NOT NULL CHECK (amount >= 0),
    informative BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_saas_nfse_tax_nfse_code UNIQUE (nfse_id, tax_code)
);

CREATE TABLE IF NOT EXISTS platform.saas_nfse_event (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nfse_id UUID NOT NULL REFERENCES platform.saas_nfse (id),
    event_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(10) NOT NULL,
    kind VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    reason VARCHAR(500),
    requested_by UUID,
    approved_by UUID,
    approved_by_second UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ======================================================================
-- Privilegios (padrao da V10_1: app escreve, central so le)
-- ======================================================================

GRANT USAGE ON SCHEMA platform TO ziipsan_app, ziipsan_central;
GRANT SELECT, INSERT, UPDATE ON platform.platform_fiscal_context TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.saas_fiscal_tax_rate TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.saas_nfse_series TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.saas_nfse TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.saas_nfse_tax TO ziipsan_app;
GRANT SELECT, INSERT ON platform.saas_nfse_event TO ziipsan_app;
GRANT SELECT ON platform.platform_fiscal_context TO ziipsan_central;
GRANT SELECT ON platform.saas_fiscal_tax_rate TO ziipsan_central;
GRANT SELECT ON platform.saas_nfse TO ziipsan_central;
GRANT SELECT ON platform.saas_nfse_tax TO ziipsan_central;
GRANT SELECT ON platform.saas_nfse_event TO ziipsan_central;
GRANT SELECT, UPDATE ON platform.saas_invoice TO ziipsan_app;
