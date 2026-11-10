-- ED-68 Cobranca por empresa terceirizada, com remuneracao por resultado.
-- Migration V106__dunning_third_party_collection.sql — familia `dunning`
-- (PLANO-v3.0: V10_x = dunning). O numero 106 e o PROXIMO LIVRE da
-- sequencia global lido na lista viva published-tenant-migrations.txt
-- (118 entradas; ultima V105; V104 reservada pelo contract da V103) a
-- data da producao (09/11/2026). RECONFIRMAR NO POUSO: a lista e viva —
-- se o 106 estiver ocupado, renumerar para o proximo livre (precedente
-- R2-G1) e actualizar este cabecalho. Este pacote NAO altera a lista de
-- publicadas; ela muda no pouso.
-- Escopo: SO o nucleo da ED-68. A empresa NAO e recadastrada aqui: a
-- coluna company_supplier_id referencia o fornecedor CONTRACTOR da
-- ED-37/P3.31 (cert_suppliers). Percentual, janela e criterios sao dados
-- do contrato (ADR-0038): NENHUM INSERT neste arquivo — nada e semeado.

-- E1: contrato com a empresa de cobranca (janela e percentual DO contrato).
CREATE TABLE IF NOT EXISTS dunning_third_party_contracts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    company_supplier_id UUID NOT NULL,
    contract_number VARCHAR(80) NOT NULL CHECK (NULLIF(btrim(contract_number),'') IS NOT NULL),
    window_start DATE NOT NULL,
    window_end DATE NOT NULL CHECK (window_end >= window_start),
    remuneration_percent NUMERIC(5,2) NOT NULL CHECK (remuneration_percent > 0 AND remuneration_percent <= 100),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_dunning_third_party_contracts_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_dunning_third_party_contracts_number UNIQUE (tenant_id, contract_number),
    CONSTRAINT fk_dunning_third_party_contracts_company FOREIGN KEY (tenant_id, company_supplier_id)
        REFERENCES cert_suppliers (tenant_id, id)
);
SELECT register_tenant_table('dunning_third_party_contracts');

-- E2: registo do contrato de operador (LGPD) — gate do primeiro envio.
-- Um contrato de cobranca, um registo de operador.
CREATE TABLE IF NOT EXISTS dunning_third_party_operator_contracts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    third_party_contract_id UUID NOT NULL,
    reference VARCHAR(300) NOT NULL CHECK (NULLIF(btrim(reference),'') IS NOT NULL),
    registered_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_dunning_third_party_operator_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_dunning_third_party_operator_contract UNIQUE (tenant_id, third_party_contract_id),
    CONSTRAINT fk_dunning_third_party_operator_contract FOREIGN KEY (tenant_id, third_party_contract_id)
        REFERENCES dunning_third_party_contracts (tenant_id, id)
);
SELECT register_tenant_table('dunning_third_party_operator_contracts');

-- E5/E8: cessao de conta — guarda SO o payload minimo enviado (whitelist:
-- divida, UC, valor, vencimento, identificador minimo do devedor) e o
-- estado de retirada com causa enumerada.
CREATE TABLE IF NOT EXISTS dunning_third_party_assignments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    debt_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    amount NUMERIC(15,2) NOT NULL CHECK (amount >= 0),
    due_date DATE NOT NULL,
    debtor_identifier VARCHAR(120) NOT NULL CHECK (NULLIF(btrim(debtor_identifier),'') IS NOT NULL),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','WITHDRAWN')),
    withdrawal_cause VARCHAR(30) CHECK (withdrawal_cause IN ('PAYMENT','INSTALLMENT_AGREEMENT','WINDOW_END')),
    withdrawn_on DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_dunning_third_party_assignments_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_dunning_third_party_assignments_contract FOREIGN KEY (tenant_id, contract_id)
        REFERENCES dunning_third_party_contracts (tenant_id, id),
    CHECK ((status = 'WITHDRAWN') = (withdrawal_cause IS NOT NULL AND withdrawn_on IS NOT NULL))
);
SELECT register_tenant_table('dunning_third_party_assignments');

-- Trava de dupla cessao no banco: uma divida so pode ter UMA cessao
-- ACTIVA por tenant (a regra tambem vive no dominio, codigo
-- DEBT_ALREADY_ASSIGNED em ThirdPartyCollectionService.assignDebts).
CREATE UNIQUE INDEX IF NOT EXISTS uq_dunning_third_party_assignments_active_debt
    ON dunning_third_party_assignments (tenant_id, debt_id) WHERE status = 'ACTIVE';

-- E9: remuneracao calculada pelo evento de pagamento atribuido.
-- payment_event_id unico por tenant = idempotencia por evento no banco.
CREATE TABLE IF NOT EXISTS dunning_third_party_remunerations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    company_supplier_id UUID NOT NULL,
    assignment_id UUID NOT NULL,
    payment_event_id UUID NOT NULL,
    paid_on DATE NOT NULL,
    payment_amount NUMERIC(15,2) NOT NULL CHECK (payment_amount > 0),
    percent_applied NUMERIC(5,2) NOT NULL CHECK (percent_applied > 0 AND percent_applied <= 100),
    remuneration_amount NUMERIC(15,2) NOT NULL CHECK (remuneration_amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_dunning_third_party_remunerations_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_dunning_third_party_remunerations_event UNIQUE (tenant_id, payment_event_id),
    CONSTRAINT fk_dunning_third_party_remunerations_contract FOREIGN KEY (tenant_id, contract_id)
        REFERENCES dunning_third_party_contracts (tenant_id, id),
    CONSTRAINT fk_dunning_third_party_remunerations_assignment FOREIGN KEY (tenant_id, assignment_id)
        REFERENCES dunning_third_party_assignments (tenant_id, id)
);
SELECT register_tenant_table('dunning_third_party_remunerations');

CREATE TABLE IF NOT EXISTS dunning_third_party_audit_trail (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    action VARCHAR(60) NOT NULL,
    detail TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_dunning_third_party_audit_trail_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('dunning_third_party_audit_trail');
