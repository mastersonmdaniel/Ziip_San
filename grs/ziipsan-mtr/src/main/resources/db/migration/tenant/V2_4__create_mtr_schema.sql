-- MTR Module Migration for Tenant Schema
-- Version: 2.4.0
-- Description: Create MTR (Manifesto de Transporte de Resíduos) tables
--
-- RLS, policy de tenant, trigger de auditoria e as colunas version/tenant_schema sao aplicados por
-- register_tenant_table() (V1_2) no fim do arquivo.

-- ========================================================================
-- WASTE CLASS (Classificação NBR 10.004 / CONAMA)
-- ========================================================================

CREATE TABLE IF NOT EXISTS waste_class (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    description VARCHAR(500) NOT NULL,
    hazard_class VARCHAR(3) NOT NULL CHECK (hazard_class IN ('I', 'II', 'III')),
    physical_state VARCHAR(1) CHECK (physical_state IN ('S', 'L', 'P', 'G')),
    sinir_code VARCHAR(20),
    mtr_code VARCHAR(20),
    default_destination_type VARCHAR(50),
    requires_special_handling BOOLEAN NOT NULL DEFAULT FALSE,
    un_number VARCHAR(10),
    packaging_group VARCHAR(10) CHECK (packaging_group IN ('I', 'II', 'III')),
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

-- FKs compostas (tenant_id, id) exigem uma constraint unica correspondente no pai.
ALTER TABLE waste_class ADD CONSTRAINT uq_waste_class_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_waste_class_tenant_code ON waste_class(tenant_id, code);
CREATE INDEX idx_waste_class_hazard ON waste_class(tenant_id, hazard_class);

-- ========================================================================
-- WASTE GENERATOR (Gerador de Resíduos)
-- ========================================================================

CREATE TABLE IF NOT EXISTS waste_generator (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    cnpj_cpf VARCHAR(18) NOT NULL,
    corporate_name VARCHAR(200) NOT NULL,
    trade_name VARCHAR(200),
    address JSONB,
    cnae_code VARCHAR(7),
    environmental_license JSONB,
    responsible_technician JSONB,
    cadri_number VARCHAR(50),
    cadri_validity DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

-- FKs compostas (tenant_id, id) exigem uma constraint unica correspondente no pai.
ALTER TABLE waste_generator ADD CONSTRAINT uq_waste_generator_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_waste_generator_cnpj ON waste_generator(tenant_id, cnpj_cpf);
CREATE INDEX idx_waste_generator_status ON waste_generator(tenant_id, status);
CREATE INDEX idx_waste_generator_cadri ON waste_generator(tenant_id, cadri_number);

-- ========================================================================
-- WASTE TRANSPORTER (Transportador)
-- ========================================================================

CREATE TABLE IF NOT EXISTS waste_transporter (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    cnpj VARCHAR(18) NOT NULL UNIQUE,
    corporate_name VARCHAR(200) NOT NULL,
    trade_name VARCHAR(200),
    address JSONB,
    rntrc VARCHAR(20) UNIQUE,
    antt_license JSONB,
    vehicles JSONB,
    drivers JSONB,
    insurance JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    operational_area JSONB,
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

-- FKs compostas (tenant_id, id) exigem uma constraint unica correspondente no pai.
ALTER TABLE waste_transporter ADD CONSTRAINT uq_waste_transporter_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_waste_transporter_cnpj ON waste_transporter(tenant_id, cnpj);
CREATE INDEX idx_waste_transporter_rntrc ON waste_transporter(tenant_id, rntrc);
CREATE INDEX idx_waste_transporter_status ON waste_transporter(tenant_id, status);

-- ========================================================================
-- WASTE DESTINATION (Destinador)
-- ========================================================================

CREATE TABLE IF NOT EXISTS waste_destination (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    cnpj VARCHAR(18) NOT NULL UNIQUE,
    corporate_name VARCHAR(200) NOT NULL,
    trade_name VARCHAR(200),
    address JSONB,
    destination_type VARCHAR(30) NOT NULL CHECK (destination_type IN (
        'LANDFILL', 'INCINERATION', 'RECYCLING', 'COMPOSTING',
        'COPROCESSING', 'TRANSFER_STATION', 'TREATMENT_PLANT', 'REUSE'
    )),
    environmental_license JSONB,
    operational_license JSONB,
    accepted_waste_classes JSONB,
    capacity_daily_tons DECIMAL(10,3),
    capacity_total_tons DECIMAL(12,3),
    current_volume_tons DECIMAL(12,3),
    coordinates GEOMETRY(Point, 4674),
    monitoring_data JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

-- FKs compostas (tenant_id, id) exigem uma constraint unica correspondente no pai.
ALTER TABLE waste_destination ADD CONSTRAINT uq_waste_destination_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_waste_destination_cnpj ON waste_destination(tenant_id, cnpj);
CREATE INDEX idx_waste_destination_type ON waste_destination(tenant_id, destination_type);
CREATE INDEX idx_waste_destination_status ON waste_destination(tenant_id, status);

-- ========================================================================
-- WASTE MTR (Manifesto de Transporte de Resíduos)
-- ========================================================================

CREATE TABLE IF NOT EXISTS waste_mtr (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    mtr_number VARCHAR(50) NOT NULL UNIQUE,
    generator_id UUID NOT NULL,
    transporter_id UUID NOT NULL,
    destination_id UUID NOT NULL,
    waste_class_id UUID NOT NULL,
    quantity_kg DECIMAL(12,3) NOT NULL,
    packaging_type VARCHAR(50),
    packaging_count INT,
    vehicle_plate VARCHAR(10),
    driver_cpf VARCHAR(14),
    route_geometry GEOMETRY(LineString, 4674),
    status VARCHAR(30) NOT NULL DEFAULT 'EMITTED' CHECK (status IN (
        'EMITTED', 'COLLECTED', 'IN_TRANSIT', 'RECEIVED', 'DESTINED',
        'CANCELLED', 'REJECTED', 'EXPIRED'
    )),
    issued_at TIMESTAMPTZ NOT NULL,
    collected_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ,
    destined_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    cancellation_reason TEXT,
    generator_signature JSONB,
    transporter_signature JSONB,
    destination_signature JSONB,
    hash_chain VARCHAR(128),
    previous_mtr_id UUID,
    is_complementary BOOLEAN NOT NULL DEFAULT FALSE,
    parent_mtr_id UUID,
    sinir_sync_status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (sinir_sync_status IN (
        'PENDING', 'SYNCING', 'SYNCED', 'FAILED', 'RETRYING'
    )),
    sinir_sync_at TIMESTAMPTZ,
    sinir_response JSONB,
    sinir_protocol VARCHAR(50),
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

-- FKs compostas (tenant_id, id) exigem uma constraint unica correspondente no pai.
ALTER TABLE waste_mtr ADD CONSTRAINT uq_waste_mtr_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_waste_mtr_number ON waste_mtr(tenant_id, mtr_number);
CREATE INDEX idx_waste_mtr_generator ON waste_mtr(tenant_id, generator_id);
CREATE INDEX idx_waste_mtr_transporter ON waste_mtr(tenant_id, transporter_id);
CREATE INDEX idx_waste_mtr_destination ON waste_mtr(tenant_id, destination_id);
CREATE INDEX idx_waste_mtr_status ON waste_mtr(tenant_id, status);
CREATE INDEX idx_waste_mtr_issued ON waste_mtr(tenant_id, issued_at);
CREATE INDEX idx_waste_mtr_collected ON waste_mtr(tenant_id, collected_at);
CREATE INDEX idx_waste_mtr_delivered ON waste_mtr(tenant_id, received_at);
CREATE INDEX idx_waste_mtr_destined ON waste_mtr(tenant_id, destined_at);
CREATE INDEX idx_waste_mtr_sinir ON waste_mtr(tenant_id, sinir_sync_status);

-- Foreign Keys
ALTER TABLE waste_mtr ADD CONSTRAINT fk_waste_mtr_generator
    FOREIGN KEY (tenant_id, generator_id) REFERENCES waste_generator(tenant_id, id);
ALTER TABLE waste_mtr ADD CONSTRAINT fk_waste_mtr_transporter
    FOREIGN KEY (tenant_id, transporter_id) REFERENCES waste_transporter(tenant_id, id);
ALTER TABLE waste_mtr ADD CONSTRAINT fk_waste_mtr_destination
    FOREIGN KEY (tenant_id, destination_id) REFERENCES waste_destination(tenant_id, id);
ALTER TABLE waste_mtr ADD CONSTRAINT fk_waste_mtr_waste_class
    FOREIGN KEY (tenant_id, waste_class_id) REFERENCES waste_class(tenant_id, id);
ALTER TABLE waste_mtr ADD CONSTRAINT fk_waste_mtr_previous
    FOREIGN KEY (tenant_id, previous_mtr_id) REFERENCES waste_mtr(tenant_id, id);
ALTER TABLE waste_mtr ADD CONSTRAINT fk_waste_mtr_parent
    FOREIGN KEY (tenant_id, parent_mtr_id) REFERENCES waste_mtr(tenant_id, id);

-- ========================================================================
-- WASTE CDF (Certificado de Destinação Final)
-- ========================================================================

CREATE TABLE IF NOT EXISTS waste_cdf (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    mtr_id UUID NOT NULL UNIQUE,
    destination_id UUID NOT NULL,
    destination_method VARCHAR(30) NOT NULL CHECK (destination_method IN (
        'LANDFILL', 'INCINERATION', 'RECYCLING', 'COMPOSTING',
        'COPROCESSING', 'TREATMENT', 'REUSE', 'ENERGY_RECOVERY'
    )),
    process_details JSONB,
    technician_id UUID,
    technician_name VARCHAR(200),
    technician_crea VARCHAR(30),
    issued_at TIMESTAMPTZ NOT NULL,
    pdf_document BYTEA,
    pdf_checksum VARCHAR(64),
    hash_chain VARCHAR(128),
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE UNIQUE INDEX idx_waste_cdf_mtr ON waste_cdf(tenant_id, mtr_id);
CREATE INDEX idx_waste_cdf_destination ON waste_cdf(tenant_id, destination_id);
CREATE INDEX idx_waste_cdf_issued ON waste_cdf(tenant_id, issued_at);

ALTER TABLE waste_cdf ADD CONSTRAINT fk_waste_cdf_mtr
    FOREIGN KEY (tenant_id, mtr_id) REFERENCES waste_mtr(tenant_id, id);
ALTER TABLE waste_cdf ADD CONSTRAINT fk_waste_cdf_destination
    FOREIGN KEY (tenant_id, destination_id) REFERENCES waste_destination(tenant_id, id);

-- ========================================================================
-- ISOLAMENTO E AUDITORIA
-- ========================================================================

SELECT register_tenant_table('waste_class');
SELECT register_tenant_table('waste_generator');
SELECT register_tenant_table('waste_transporter');
SELECT register_tenant_table('waste_destination');
SELECT register_tenant_table('waste_mtr');
SELECT register_tenant_table('waste_cdf');

-- ========================================================================
-- CATALOGO DE CLASSES DE RESIDUO: INTENCIONALMENTE VAZIO
-- ========================================================================
-- Uma versao anterior semeava classes com codigos inventados (A.1.1, B.1.3...) e rotulos
-- incorretos (grupos de residuos de saude misturados com classes da NBR 10.004; um codigo
-- repetido). Um MTR e documento legal enviado ao SINIR: classificacao errada nao pode vir como
-- padrao. O catalogo deve ser carregado a partir da lista oficial do IBAMA (Lista Brasileira de
-- Residuos Solidos, IN IBAMA 13/2012: confirmar a norma vigente) e conferido por responsavel
-- tecnico, numa migration propria.
