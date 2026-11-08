-- ============================================================================
-- ED-25 (PENDENTE DE NUMERACAO): residuos solidos na conta - cofaturamento
-- pela NR 13, arts. 46 a 56.
--
-- POR QUE PENDENTE: o plano/mapa nao reserva versao Flyway para a ED-25 e a
-- faixa do billing ja esta ocupada ate V4_17 (mais os slots nomeados nos
-- despachos vizinhos). Conforme a regra da tarefa, NENHUM numero foi
-- inventado: este SQL entra como db/pendente/ e recebe o numero da
-- sequencia global na integracao (precedentes: ED-14, ED-17, ED-22, ED-34).
--
-- Depende de: V4_1 (bills) e public.parameter_definitions.
-- NENHUM DADO DE NEGOCIO E SEMEADO: prestadores, rotulos, valores do
-- servico e parametros sao do tenant/agencia. Os unicos INSERTs seriam o
-- catalogo de parametros (precedente V4_4/ED-22) - ver secao 5, comentada.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. CONTRATO DE COFATURAMENTO POR SERVICO
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cobill_contracts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    service_kind VARCHAR(30) NOT NULL CHECK (service_kind IN ('SOLID_WASTE')),
    provider_id UUID NOT NULL,
    provider_name VARCHAR(200) NOT NULL,
    bill_label VARCHAR(200) NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    notice_date DATE NOT NULL,
    start_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CHECK (start_date >= valid_from),
    -- Antecedencia minima do aviso: piso travado de 60 dias (acceptance da
    -- ED-25). O parametro cobill.notice_min_days pode AMPLIAR o piso; o
    -- banco trava o piso, a regra Java aplica o parametro vigente.
    CHECK (start_date >= notice_date + 60)
);
ALTER TABLE cobill_contracts ADD CONSTRAINT uq_cobill_contracts_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_cobill_contracts_provider ON cobill_contracts(tenant_id, provider_id);
CREATE INDEX idx_cobill_contracts_service ON cobill_contracts(tenant_id, service_kind, valid_from);

-- ----------------------------------------------------------------------------
-- 2. COBRANCA COFATURADA: documento com IDENTIFICADOR UNICO
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cobill_charges (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    bill_id UUID,
    -- Identificador com o UUID COMPLETO da unidade (correcao pos-reprovacao
    -- D1): COBILL-<uuid 36>-<AAAAMM>-<seq 3> = 54 caracteres.
    document_identifier VARCHAR(60) NOT NULL,
    reference_month DATE NOT NULL,
    billing_mode VARCHAR(20) NOT NULL CHECK (billing_mode IN
        ('COBILLED', 'SEPARATE_INVOICE', 'BOOKLET_MONTHLY', 'BOOKLET_ANNUAL')),
    amount NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
    exempt BOOLEAN NOT NULL DEFAULT FALSE,
    settled_amount NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (settled_amount >= 0),
    transferred_amount NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (transferred_amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (settled_amount <= amount),
    CHECK (transferred_amount <= settled_amount),
    -- Linha de isencao nasce zerada e marcada (discriminada na conta).
    CHECK (NOT exempt OR amount = 0),
    -- bill_id so no modo COBILLED (padrao da V4_1: FK composta bills):
    -- exigido no COBILLED e PROIBIDO nos demais modos (correcao
    -- pos-reprovacao D4 - o CHECK original so tinha o primeiro ramo).
    CHECK (billing_mode <> 'COBILLED' OR bill_id IS NOT NULL),
    CHECK (billing_mode = 'COBILLED' OR bill_id IS NULL)
);
ALTER TABLE cobill_charges ADD CONSTRAINT uq_cobill_charges_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE cobill_charges ADD CONSTRAINT fk_cobill_charge_contract
    FOREIGN KEY (tenant_id, contract_id) REFERENCES cobill_contracts(tenant_id, id);
ALTER TABLE cobill_charges ADD CONSTRAINT fk_cobill_charge_bill
    FOREIGN KEY (tenant_id, bill_id) REFERENCES bills(tenant_id, id);
-- IDENTIFICADOR UNICO do documento de cobranca, por tenant.
CREATE UNIQUE INDEX idx_cobill_charges_document ON cobill_charges(tenant_id, document_identifier);
CREATE INDEX idx_cobill_charges_unit ON cobill_charges(tenant_id, unit_id, reference_month);
-- Uma cobranca por unidade/competencia/contrato (reemissao e nova versao
-- da conta, nao segunda cobranca).
CREATE UNIQUE INDEX idx_cobill_charges_competence
    ON cobill_charges(tenant_id, contract_id, unit_id, reference_month);

-- ----------------------------------------------------------------------------
-- 3. RETIRADA A PEDIDO DO USUARIO (protocolo = prova do pedido)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cobill_withdrawals (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    protocol VARCHAR(60) NOT NULL,
    requested_on DATE NOT NULL,
    -- Competencia de efetivacao gravada como DIA 1 do mes (YearMonth do
    -- dominio; mesmo padrao de reference_month das cobrancas).
    effective_from DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    -- Granularidade MENSAL, como a regra de dominio (correcao
    -- pos-reprovacao D2): a regra (CobillWithdrawalRule) compara
    -- YearMonth - pedido em 05/04 com efetivacao na competencia 04 e
    -- LEGITIMO, e effective_from e gravado como dia 1 da competencia
    -- (mesmo padrao de reference_month). O CHECK original comparava
    -- datas cheias (2026-04-01 >= 2026-04-05 = falso) e recusava no
    -- banco o caso que o dominio aceita. Nenhum prazo pedido->
    -- efetivacao e criado aqui: so a nao-retroacao por competencia,
    -- exatamente como declarado no dominio (prazo da NR 13 nao foi
    -- inventado).
    CHECK (date_trunc('month', effective_from) >= date_trunc('month', requested_on))
);
ALTER TABLE cobill_withdrawals ADD CONSTRAINT uq_cobill_withdrawals_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE cobill_withdrawals ADD CONSTRAINT fk_cobill_withdrawal_contract
    FOREIGN KEY (tenant_id, contract_id) REFERENCES cobill_contracts(tenant_id, id);
CREATE UNIQUE INDEX idx_cobill_withdrawals_protocol ON cobill_withdrawals(tenant_id, protocol);
CREATE INDEX idx_cobill_withdrawals_unit ON cobill_withdrawals(tenant_id, unit_id, effective_from);

-- ----------------------------------------------------------------------------
-- 4. ISOLAMENTO (padrao register_tenant_table da core)
-- ----------------------------------------------------------------------------
SELECT register_tenant_table('cobill_contracts');
SELECT register_tenant_table('cobill_charges');
SELECT register_tenant_table('cobill_withdrawals');

-- ----------------------------------------------------------------------------
-- 5. PARAMETROS NO CATALOGO GLOBAL (public.parameter_definitions)
-- So o catalogo (chave, tipo, descricao): NENHUM valor e semeado - valor e
-- politica do tenant/agencia, aceito pelo fluxo de parametros (P0.19/P1.6).
-- A forma exata das colunas segue a V4_4; conferir na integracao.
-- ----------------------------------------------------------------------------
-- cobill.enabled                 BOOLEAN
-- cobill.notice_min_days         INT     (piso travado 60; tenant so amplia)
-- cobill.billing_mode            ENUM    COBILLED|SEPARATE_INVOICE|BOOKLET_MONTHLY|BOOKLET_ANNUAL
-- cobill.payment_allocation      ENUM    OWN_FIRST|PRO_RATA
-- cut.third_party_debt_blocks    BOOLEAN (nome dado pelo acceptance da ED-25;
--                                          a definicao entra no grupo cut.* da
--                                          P3.4 na integracao, sem duplicar)
-- (INSERTs no catalogo entram na integracao, copiando o formato literal da
--  V4_4 secao 7 apos a numeracao desta migration ser reservada.)
