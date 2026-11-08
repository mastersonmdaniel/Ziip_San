-- ED-28 (Cartao no parcelamento pela credenciadora do cliente; 18e-E14/D3
-- opcao A; ADR-0028 (h)) — DDL PENDENTE DE NUMERO.
--
-- POR QUE SEM NUMERO: as migrations de tenant na main sao V6_1, V6_2,
-- V6_3, V6_5 e V6_6 (sem V6_4; ziipsan-collection) e a trilha
-- pos-P3.12 usa a sequencia GLOBAL a
-- partir de V100, cujo proximo numero livre depende da maior versao
-- publicada no repo no momento da integracao — precedentes: P4.1 e
-- P4.5 entregaram SQL em db/pendente/ exatamente assim. NENHUM numero
-- foi inventado aqui. Na integracao, este arquivo vira
-- V<nnn>__card_acquirer_checkout_settlement.sql.
--
-- Conteudo:
--   * card_acquirer_profiles: a credenciadora CONTRATADA PELO TENANT
--     (codigo + modo), sem credencial: chave de API e segredo de
--     webhook vivem no cofre de credenciais, NUNCA nesta tabela.
--   * card_checkout_sessions: a sessao de link/checkout hospedado
--     aberta para uma parcela do acordo da P4.1 (installment_number
--     0 = entrada; 1..n = prestacao da agenda).
--   * card_acquirer_events: o evento de retorno ja processado
--     (idempotencia por tenant + credenciadora + event_id). Guarda so
--     o HASH do payload e os campos extraidos — o payload bruto NAO
--     e persistido, por desenho (ED-28: nada de cartao no banco).
--     O hash (SHA-256) e o received_at sao calculados/entregues pelo
--     CardReturnProcessor via CardStore.recordEvent (correcao D4 da
--     revisao de 08/11/2026): a coluna tem fonte real no codigo.
--   * card_settlements: a baixa por cartao (payments de canal CARTAO
--     da P3.1), com bruto/taxa/liquido e a transacao da credenciadora.
--   * card_statement_lines: as linhas do extrato importado da
--     credenciadora, base da conciliacao automatica.
--   * Definicoes de parametros card.* em public.parameter_definitions
--     (ON CONFLICT DO NOTHING).
--
-- REGRA DO ACEITE, NO BANCO: NENHUMA tabela tem coluna de dado de
-- cartao (numero, validade, codigo de seguranca, nome impresso,
-- trilha). O teste CardMigrationConsistencyTest varre este arquivo e
-- recusa qualquer coluna assim. Dinheiro: NUMERIC(14,2). Nenhum valor
-- de tenant e semeado; card.enabled nasce SEM padrao de fabrica (a
-- definicao nao presume ligado nem desligado — precedente
-- pay.withholding_enabled da P3.1).

-- ========================================================================
-- CREDENCIADORA DO TENANT (sem credencial — credencial e do cofre)
-- ========================================================================

CREATE TABLE IF NOT EXISTS card_acquirer_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    acquirer_code VARCHAR(60) NOT NULL,
    display_name TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100)
);
ALTER TABLE card_acquirer_profiles ADD CONSTRAINT uq_card_acquirer_profiles_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_card_acquirer_profiles_code ON card_acquirer_profiles(tenant_id, acquirer_code);

-- ========================================================================
-- SESSAO DE LINK/CHECKOUT (hospedada pela credenciadora)
-- ========================================================================

CREATE TABLE IF NOT EXISTS card_checkout_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    agreement_id UUID NOT NULL,
    installment_number INT NOT NULL CHECK (installment_number >= 0),
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    acquirer_code VARCHAR(60) NOT NULL,
    checkout_mode VARCHAR(20) NOT NULL CHECK (checkout_mode IN ('PAYMENT_LINK', 'HOSTED_CHECKOUT')),
    reference VARCHAR(80) NOT NULL,
    acquirer_session_id VARCHAR(120) NOT NULL,
    checkout_url TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED' CHECK (status IN
        ('CREATED', 'PAID', 'REFUSED', 'CANCELED', 'EXPIRED', 'CHARGED_BACK')),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100)
    -- agreement_id SEM FK: a tabela do acordo (installment_agreements)
    -- e do SQL PENDENTE da P4.1, ainda sem numero na sequencia; o
    -- precedente payments.bill_id (V6_2) tambem nao tem FK. A FK
    -- composta entra na integracao, depois do numero da P4.1.
);
ALTER TABLE card_checkout_sessions ADD CONSTRAINT uq_card_checkout_sessions_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_card_sessions_reference ON card_checkout_sessions(tenant_id, reference);
CREATE INDEX idx_card_sessions_agreement ON card_checkout_sessions(tenant_id, agreement_id, installment_number);

-- ========================================================================
-- EVENTOS DE RETORNO (idempotencia; payload bruto NUNCA persistido)
-- ========================================================================

CREATE TABLE IF NOT EXISTS card_acquirer_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    acquirer_code VARCHAR(60) NOT NULL,
    event_id VARCHAR(120) NOT NULL,
    acquirer_transaction_id VARCHAR(120) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_schema VARCHAR(100)
);
ALTER TABLE card_acquirer_events ADD CONSTRAINT uq_card_acquirer_events_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_card_events_idempotency ON card_acquirer_events(tenant_id, acquirer_code, event_id);
CREATE INDEX idx_card_events_transaction ON card_acquirer_events(tenant_id, acquirer_transaction_id);

-- ========================================================================
-- BAIXA POR CARTAO (espelho do pagamento CARTAO da P3.1)
-- ========================================================================

CREATE TABLE IF NOT EXISTS card_settlements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    agreement_id UUID NOT NULL,
    installment_number INT NOT NULL CHECK (installment_number >= 0),
    payment_id UUID NOT NULL,
    acquirer_code VARCHAR(60) NOT NULL,
    acquirer_transaction_id VARCHAR(120) NOT NULL,
    gross_amount NUMERIC(14,2) NOT NULL CHECK (gross_amount > 0),
    fee_amount NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (fee_amount >= 0),
    net_amount NUMERIC(14,2) NOT NULL CHECK (net_amount >= 0),
    paid_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100),
    CONSTRAINT fk_card_settlements_payment FOREIGN KEY (tenant_id, payment_id)
        REFERENCES payments (tenant_id, id),
    CHECK (net_amount = gross_amount - fee_amount)
);
ALTER TABLE card_settlements ADD CONSTRAINT uq_card_settlements_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_card_settlements_transaction ON card_settlements(tenant_id, acquirer_transaction_id);
CREATE INDEX idx_card_settlements_agreement ON card_settlements(tenant_id, agreement_id, installment_number);

-- ========================================================================
-- EXTRATO DA CREDENCIADORA (conciliacao)
-- ========================================================================

CREATE TABLE IF NOT EXISTS card_statement_lines (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    acquirer_code VARCHAR(60) NOT NULL,
    acquirer_transaction_id VARCHAR(120) NOT NULL,
    session_reference VARCHAR(80),
    gross_amount NUMERIC(14,2) NOT NULL CHECK (gross_amount > 0),
    fee_amount NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (fee_amount >= 0),
    net_amount NUMERIC(14,2) NOT NULL CHECK (net_amount >= 0),
    settlement_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_schema VARCHAR(100),
    CHECK (net_amount = gross_amount - fee_amount)
);
ALTER TABLE card_statement_lines ADD CONSTRAINT uq_card_statement_lines_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_card_statement_tx ON card_statement_lines(tenant_id, acquirer_transaction_id);
CREATE INDEX idx_card_statement_date ON card_statement_lines(tenant_id, settlement_date);

-- ========================================================================
-- PARAMETROS card.* (definicoes; nenhum valor de tenant semeado)
-- ========================================================================

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('card.enabled', 'BOOLEAN', 'd', NULL,
     '18e-E14/D3 (cartao pela credenciadora contratada pelo tenant)',
     'COM',
     'Liga o pagamento com cartao pela credenciadora do tenant. Sem valor aceito, o recurso nao opera (erro explicito): nao ha padrao presumido.')
    ,
    ('card.acquirer_code', 'TEXT', 'd', NULL,
     '18e-E14/D3 (contrato do tenant com a credenciadora)',
     'COM',
     'Codigo da credenciadora contratada pelo tenant, no cadastro de credenciadoras. Obrigatorio com card.enabled ligado. Credenciais NAO vivem em parametro: ficam no cofre.')
    ,
    ('card.checkout_mode', 'TEXT', 'd', NULL,
     '18e-E14/D3 (link de pagamento ou checkout hospedado)',
     'COM',
     'PAYMENT_LINK ou HOSTED_CHECKOUT: como o pagador chega a pagina da credenciadora, onde o cartao e digitado fora do produto.')
    ,
    ('card.statement_tolerance', 'DECIMAL', 'd', NULL,
     'ED-28 (conciliacao automatica pelo extrato)',
     'FIN',
     'Tolerancia em reais para a divergencia do liquido na conciliacao entre a baixa e o extrato da credenciadora.')
ON CONFLICT (definition_key) DO NOTHING;

-- ========================================================================
-- ISOLAMENTO (RLS) - mesmo helper das demais migrations do tenant
-- ========================================================================

SELECT register_tenant_table('card_acquirer_profiles');
SELECT register_tenant_table('card_checkout_sessions');
SELECT register_tenant_table('card_acquirer_events');
SELECT register_tenant_table('card_settlements');
SELECT register_tenant_table('card_statement_lines');
