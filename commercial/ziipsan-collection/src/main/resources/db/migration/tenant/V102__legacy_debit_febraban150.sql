-- V6_4 - P3.24 (FEBRABAN 150 legado + campanha de conversao ao Pix Automatico).
-- Migration RESERVADA a esta tarefa (plano v3.0, secao 3). Modulo:
-- commercial/ziipsan-collection (schema do tenant).
--
-- Conferencia no HEAD c76eb2e da main: as migrations de tenant do modulo sao
-- V6_1, V6_2 (P3.1), V6_3 (P3.2), V6_5 (P3.29) e V6_6 -> V6_4 LIVRE, e o slot
-- da faixa da propria P3.24 (a sequencia global da P3.12/V100 vale para as
-- tarefas posteriores a ela, como a P4.5 - nao para esta faixa reservada).
--
-- O que esta migration faz:
--   1. Cria `legacy_debit_authorizations`: autorizacoes IMPORTADAS do legado,
--      conferidas POR MATRICULA (UNIQUE por tenant); a condicao de mesmo
--      banco da P0.30 fica legivel nas duas colunas de banco (a elegibilidade
--      e decisao da aplicacao, registrada no status).
--   2. Cria `legacy_debit_conversion_consents`: consentimento GUARDADO da
--      conversao ao Pix Automatico, com prova (hash SHA-256 + referencia).
--   3. Cria `legacy_debit_entries`: debitos por ciclo, chave da baixa
--      idempotente do retorno; `outcome_code` guarda o codigo do banco
--      verbatim (nenhuma tabela de codigos e presumida).
--
-- NENHUM DADO E SEMEADO: `pay.legacy_debit_enabled` (classe d) NAO e semeado;
-- ausencia do parametro e erro explicito na aplicacao. A data de desligamento
-- NAO esta nesta migration nem em parametro: e deduzida em
-- BcbDebitRegimeRules (Res. CMN 5.278/2025 e Res. BCB 544/2025 -> 01/01/2027
-- para convenio de servico publico; Res. BCB 565/2026 -> 01/07/2027 no regime
-- geral). Dinheiro: NUMERIC(14,2). Depende de register_tenant_table (V1_2).

CREATE TABLE IF NOT EXISTS legacy_debit_authorizations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    enrollment_id VARCHAR(40) NOT NULL,
    unit_id UUID,
    payer_document VARCHAR(14) NOT NULL,
    payer_bank_code VARCHAR(3) NOT NULL CHECK (payer_bank_code ~ '^[0-9]{3}$'),
    receiver_bank_code VARCHAR(3) NOT NULL CHECK (receiver_bank_code ~ '^[0-9]{3}$'),
    legacy_reference VARCHAR(200) NOT NULL,
    status VARCHAR(28) NOT NULL DEFAULT 'IMPORTADA_NAO_CONFERIDA' CHECK (status IN
        ('IMPORTADA_NAO_CONFERIDA', 'ATIVA_LEGADO', 'INELEGIVEL_BANCO_DIVERSO',
         'CANCELADA', 'CONVERTIDA_PIX')),
    imported_at TIMESTAMPTZ NOT NULL,
    conferred_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    converted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100)
);
ALTER TABLE legacy_debit_authorizations ADD CONSTRAINT uq_legacy_debit_auth_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_legacy_debit_auth_enrollment ON legacy_debit_authorizations(tenant_id, enrollment_id);

CREATE TABLE IF NOT EXISTS legacy_debit_conversion_consents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    authorization_id UUID NOT NULL,
    enrollment_id VARCHAR(40) NOT NULL,
    proof_hash VARCHAR(64) NOT NULL CHECK (proof_hash ~ '^[0-9a-f]{64}$'),
    proof_reference VARCHAR(200) NOT NULL,
    channel VARCHAR(40) NOT NULL,
    consented_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100),
    CONSTRAINT fk_legacy_consent_auth FOREIGN KEY (tenant_id, authorization_id)
        REFERENCES legacy_debit_authorizations (tenant_id, id)
);
ALTER TABLE legacy_debit_conversion_consents ADD CONSTRAINT uq_legacy_consent_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS legacy_debit_entries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    authorization_id UUID NOT NULL,
    enrollment_id VARCHAR(40) NOT NULL,
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    debit_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REMETIDO' CHECK (status IN ('REMETIDO', 'BAIXADO', 'CANCELADO')),
    remittance_count INTEGER NOT NULL DEFAULT 1 CHECK (remittance_count >= 1),
    outcome_code VARCHAR(40),
    settled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100),
    CONSTRAINT fk_legacy_entry_auth FOREIGN KEY (tenant_id, authorization_id)
        REFERENCES legacy_debit_authorizations (tenant_id, id)
);
ALTER TABLE legacy_debit_entries ADD CONSTRAINT uq_legacy_entry_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_legacy_entry_enrollment ON legacy_debit_entries(tenant_id, enrollment_id);

SELECT register_tenant_table('legacy_debit_authorizations');
SELECT register_tenant_table('legacy_debit_conversion_consents');
SELECT register_tenant_table('legacy_debit_entries');
