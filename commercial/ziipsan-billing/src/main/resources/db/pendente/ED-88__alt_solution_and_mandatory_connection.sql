-- ED-88 — Solucoes individuais, limpa-fossa e ligacao obrigatoria a rede.
-- NUMERACAO PENDENTE: nenhum numero Flyway foi CONFIRMADO livre para a ED-88
-- nesta frente (o plano nao reserva numero nominal; pacotes locais do billing
-- vao ate V4_17 e ha slots nomeados por outras tarefas). NAO inventar numero:
-- na integracao, atribuir o proximo slot livre da historia tenant do billing,
-- renomear este arquivo e conferir register_tenant_table/RLS e as FKs contra
-- V4_1 (billing), V3_x (registry: imoveis/unidades) e V9_1 (service_orders).
-- Nenhum dado de negocio semeado: precos, prazos e fontes tarifarias sao do
-- tenant/agencia.

-- Cadastro da solucao individual por imovel (SG-154).
CREATE TABLE IF NOT EXISTS alt_sewer_solutions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    property_id UUID NOT NULL,
    consumer_unit_id UUID NOT NULL,
    components TEXT[] NOT NULL CHECK (cardinality(components) >= 1),
    volume_liters NUMERIC(12,2) NOT NULL CHECK (volume_liters > 0),
    last_cleaning_on DATE,
    sludge_destination VARCHAR(300) NOT NULL CHECK (sludge_destination <> ''),
    registered_on DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE','DEACTIVATED_BY_CONNECTION')),
    deactivated_on DATE,
    deactivation_service_order_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- sem CHECK last_cleaning_on <= registered_on: a coluna e atualizada a
    -- cada esgotamento executado e passa a ser posterior ao cadastro (a trava
    -- do snapshot inicial vive na fabrica AltSolution.register).
    CHECK ((status = 'ACTIVE' AND deactivated_on IS NULL AND deactivation_service_order_id IS NULL)
        OR (status = 'DEACTIVATED_BY_CONNECTION' AND deactivated_on IS NOT NULL
            AND deactivation_service_order_id IS NOT NULL))
);
ALTER TABLE alt_sewer_solutions ADD CONSTRAINT uq_alt_sewer_solutions_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_alt_sewer_solutions_property ON alt_sewer_solutions(tenant_id, property_id, status);

-- Execucao do esgotamento: OS com caminhao/roteiro + ticket de descarga na ETE.
CREATE TABLE IF NOT EXISTS alt_solution_cleanings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    alt_solution_id UUID NOT NULL,
    service_order_id UUID NOT NULL,
    truck_id VARCHAR(100) NOT NULL CHECK (truck_id <> ''),
    route_sequence INT NOT NULL CHECK (route_sequence >= 1),
    scheduled_on DATE NOT NULL,
    executed_on DATE NOT NULL,
    collected_volume_liters NUMERIC(12,2) NOT NULL CHECK (collected_volume_liters > 0),
    discharge_ticket_code VARCHAR(100) NOT NULL CHECK (discharge_ticket_code <> ''),
    treatment_plant_id VARCHAR(100) NOT NULL CHECK (treatment_plant_id <> ''),
    discharged_volume_liters NUMERIC(12,2) NOT NULL CHECK (discharged_volume_liters > 0),
    discharged_on DATE NOT NULL,
    price_item_code VARCHAR(100) NOT NULL,
    tariff_source VARCHAR(100) NOT NULL CHECK (tariff_source <> ''),
    billed_quantity NUMERIC(12,2) NOT NULL CHECK (billed_quantity > 0),
    amount NUMERIC(15,2) NOT NULL CHECK (amount >= 0),
    billing_route VARCHAR(20) NOT NULL
        CHECK (billing_route IN ('REGULAR_BILL','CASUAL_GUIDE','GROUPED_BILL')),
    idempotency_key VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (discharged_volume_liters = collected_volume_liters),
    CHECK (discharged_on >= executed_on),
    FOREIGN KEY (tenant_id, alt_solution_id) REFERENCES alt_sewer_solutions(tenant_id, id)
);
ALTER TABLE alt_solution_cleanings ADD CONSTRAINT uq_alt_solution_cleanings_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX uq_alt_solution_cleanings_idempotency
    ON alt_solution_cleanings(tenant_id, idempotency_key);

-- Ciclo da ligacao obrigatoria (Lei 11.445, art. 45). Nao ha coluna de
-- "obrigacao desligada": a obrigacao legal nao e desativavel.
CREATE TABLE IF NOT EXISTS network_availability_cases (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    property_id UUID NOT NULL,
    consumer_unit_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL
        CHECK (status IN ('AVAILABILITY_COMMUNICATED','BILL_NOTICE_ISSUED','CONNECTED','EXEMPTED','COMPULSORY_ORDER_ISSUED')),
    availability_communicated_on DATE NOT NULL,
    bill_notice_on DATE,
    first_availability_charge_on DATE,
    connected_on DATE,
    connection_service_order_id UUID,
    exemption_proof_ref VARCHAR(300),
    compulsory_issued_on DATE,
    compulsory_kind VARCHAR(30) CHECK (compulsory_kind IN ('NOTIFICATION','COMPULSORY_CONNECTION_ORDER')),
    fine_price_item_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (bill_notice_on IS NULL OR bill_notice_on >= availability_communicated_on),
    CHECK (status <> 'EXEMPTED' OR (exemption_proof_ref IS NOT NULL AND exemption_proof_ref <> '')),
    CHECK (status <> 'CONNECTED' OR (connected_on IS NOT NULL AND connection_service_order_id IS NOT NULL)),
    CHECK (status NOT IN ('BILL_NOTICE_ISSUED','COMPULSORY_ORDER_ISSUED')
        OR (bill_notice_on IS NOT NULL AND first_availability_charge_on IS NOT NULL))
);
ALTER TABLE network_availability_cases ADD CONSTRAINT uq_network_availability_cases_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_availability_cases_status ON network_availability_cases(tenant_id, status, property_id);

SELECT register_tenant_table('alt_sewer_solutions');
SELECT register_tenant_table('alt_solution_cleanings');
SELECT register_tenant_table('network_availability_cases');

-- Parametros (public.parameter_definitions): bloco COMENTADO, como na ED-22 —
-- a forma literal das colunas deve ser copiada da V4_4 §7 na integracao.
--   sewer.alt_solution_service_enabled        BOOLEAN classe b (servico prestado ou so cadastro)
--   sewer.alt_solution_service_interval_months INT    (agenda periodica; sem padrao inventado)
--   sewer.alt_solution_tariff_source          TEXT    (fonte definida pela agencia; sem enumeracao no produto)
--   avail.notice_days                         INT     classe c, piso 90
--   avail.max_connect_months                  INT     classe c, teto 12
--   avail.exemption_requires_proof            BOOLEAN classe a, travado true
-- NAO registrar avail.connection_obligation_enabled: a obrigacao nao e parametro.
