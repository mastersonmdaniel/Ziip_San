-- ED-37 Empreiteiras terceirizadas — SQL PENDENTE DE NUMERACAO FLYWAY.
-- Nenhum numero foi CONFIRMADO livre para esta frente: a historia tenant do
-- shared/ziipsan-core ja tem V21_3 (P3.31) e pacotes locais pendentes (ED-36
-- e outros) disputam slots. NAO inventar numero: na integracao, atribuir o
-- proximo slot livre da faixa do modulo, renomear o arquivo e conferir as
-- FKs contra V21_3 (cert_suppliers) e V9_1 (service_orders).
-- Escopo: SO a extensao de empreiteira. Razao social/documento/contato
-- continuam em cert_suppliers (P3.31) — NENHUMA coluna de cadastro e
-- duplicada aqui. FORA: campos de integracao orcamentaria da ED-62
-- (empenho/ordem de compra/PNCP — nenhuma coluna) e obras/projetos da ED-84
-- (nenhuma tabela/coluna).

-- SG-492: perfil de empreiteira = extensao 1:1 do fornecedor CONTRACTOR.
CREATE TABLE IF NOT EXISTS contractor_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    supplier_id UUID NOT NULL,
    specialties TEXT[] NOT NULL CHECK (cardinality(specialties) > 0),
    coverage_ref VARCHAR(300) NOT NULL CHECK (NULLIF(btrim(coverage_ref),'') IS NOT NULL),
    technical_responsible_ref VARCHAR(300) NOT NULL CHECK (NULLIF(btrim(technical_responsible_ref),'') IS NOT NULL),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_contractor_profiles_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_contractor_profiles_supplier UNIQUE (tenant_id, supplier_id),
    CONSTRAINT fk_contractor_profiles_supplier FOREIGN KEY (tenant_id, supplier_id)
        REFERENCES cert_suppliers (tenant_id, id)
);
SELECT register_tenant_table('contractor_profiles');

-- SG-493: contratos (sem campos orcamentarios — ED-62; sem obra/projeto — ED-84).
CREATE TABLE IF NOT EXISTS contractor_contracts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    contractor_supplier_id UUID NOT NULL,
    contract_number VARCHAR(80) NOT NULL CHECK (NULLIF(btrim(contract_number),'') IS NOT NULL),
    object_description TEXT NOT NULL CHECK (NULLIF(btrim(object_description),'') IS NOT NULL),
    signed_on DATE,
    valid_from DATE NOT NULL,
    valid_to DATE NOT NULL CHECK (valid_to >= valid_from),
    total_value NUMERIC(15,2) NOT NULL CHECK (total_value >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','ACTIVE','SUSPENDED','CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_contractor_contracts_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_contractor_contracts_number UNIQUE (tenant_id, contract_number),
    CONSTRAINT fk_contractor_contracts_supplier FOREIGN KEY (tenant_id, contractor_supplier_id)
        REFERENCES cert_suppliers (tenant_id, id),
    CHECK (signed_on IS NULL OR signed_on <= valid_from)
);
SELECT register_tenant_table('contractor_contracts');

-- SG-494: clausulas penais PARAMETRIZADAS por contrato. Nenhuma linha de
-- fabrica: sem INSERT neste arquivo (o catalogo juridico da P3.31 ficou
-- vazio por desenho — nenhuma penalidade padrao e semeada).
CREATE TABLE IF NOT EXISTS contractor_penalty_clauses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL CHECK (NULLIF(btrim(code),'') IS NOT NULL),
    description TEXT NOT NULL CHECK (NULLIF(btrim(description),'') IS NOT NULL),
    basis VARCHAR(30) NOT NULL CHECK (basis IN ('FIXED_AMOUNT','PERCENT_OF_CONTRACT','PERCENT_OF_MEASUREMENT','PER_DAY')),
    value NUMERIC(15,2) NOT NULL CHECK (value > 0),
    cap_value NUMERIC(15,2) CHECK (cap_value IS NULL OR cap_value > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_contractor_penalty_clauses_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_contractor_penalty_clauses_code UNIQUE (tenant_id, contract_id, code),
    CONSTRAINT fk_contractor_penalty_clauses_contract FOREIGN KEY (tenant_id, contract_id)
        REFERENCES contractor_contracts (tenant_id, id),
    CHECK (basis NOT IN ('PERCENT_OF_CONTRACT','PERCENT_OF_MEASUREMENT') OR value <= 100)
);
SELECT register_tenant_table('contractor_penalty_clauses');

CREATE TABLE IF NOT EXISTS contractor_penalty_applications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    clause_id UUID NOT NULL,
    measurement_id UUID,
    occurred_on DATE NOT NULL,
    description TEXT NOT NULL CHECK (NULLIF(btrim(description),'') IS NOT NULL),
    days INT NOT NULL DEFAULT 0 CHECK (days >= 0),
    amount NUMERIC(15,2) NOT NULL CHECK (amount >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('APPLIED','WAIVED')),
    waive_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_contractor_penalty_applications_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_contractor_penalty_applications_contract FOREIGN KEY (tenant_id, contract_id)
        REFERENCES contractor_contracts (tenant_id, id),
    CONSTRAINT fk_contractor_penalty_applications_clause FOREIGN KEY (tenant_id, clause_id)
        REFERENCES contractor_penalty_clauses (tenant_id, id),
    CHECK (status <> 'WAIVED' OR NULLIF(btrim(waive_reason),'') IS NOT NULL)
);
SELECT register_tenant_table('contractor_penalty_applications');

-- SG-495/SG-496: medicao e seus itens (1 item = 1 OS executada pela
-- empreiteira). A trava de dupla medicao vive no DOMINIO
-- (ContractorService.buildMeasurement, codigo ORDER_ALREADY_MEASURED);
-- este SQL NAO cria nenhum indice unico parcial — a trava equivalente
-- de banco fica para a integracao, como declara a nota ao final
-- deste arquivo.
CREATE TABLE IF NOT EXISTS contractor_measurements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    contractor_supplier_id UUID NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL CHECK (period_end >= period_start),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_contractor_measurements_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_contractor_measurements_contract FOREIGN KEY (tenant_id, contract_id)
        REFERENCES contractor_contracts (tenant_id, id),
    CONSTRAINT fk_contractor_measurements_supplier FOREIGN KEY (tenant_id, contractor_supplier_id)
        REFERENCES cert_suppliers (tenant_id, id)
);
SELECT register_tenant_table('contractor_measurements');

CREATE TABLE IF NOT EXISTS contractor_measurement_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    measurement_id UUID NOT NULL,
    order_id UUID NOT NULL,
    description TEXT NOT NULL CHECK (NULLIF(btrim(description),'') IS NOT NULL),
    quantity NUMERIC(15,3) NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(15,2) NOT NULL CHECK (unit_price >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_contractor_measurement_items_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_contractor_measurement_items_measurement FOREIGN KEY (tenant_id, measurement_id)
        REFERENCES contractor_measurements (tenant_id, id),
    CONSTRAINT fk_contractor_measurement_items_order FOREIGN KEY (tenant_id, order_id)
        REFERENCES service_orders (tenant_id, id)
);
SELECT register_tenant_table('contractor_measurement_items');
-- FK diferida da aplicacao de penalidade para a medicao (ordem de criacao).
ALTER TABLE contractor_penalty_applications
    ADD CONSTRAINT fk_contractor_penalty_applications_measurement
    FOREIGN KEY (tenant_id, measurement_id) REFERENCES contractor_measurements (tenant_id, id);

-- Uma OS so pode estar em UMA medicao viva (nao REJECTED) por contrato:
-- implementado na integracao como indice sobre a juncao item->medicao
-- (a regra em si ja e travada no dominio, ContractorService.buildMeasurement,
-- codigo ORDER_ALREADY_MEASURED; o indice equivalente exige a forma final
-- escolhida na integracao — declarado aqui para nao fingir trava de banco
-- que este SQL, sozinho, nao expressa).

CREATE TABLE IF NOT EXISTS contractor_audit_trail (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    action VARCHAR(60) NOT NULL,
    detail TEXT NOT NULL DEFAULT '',
    actor_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_contractor_audit_trail_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('contractor_audit_trail');
