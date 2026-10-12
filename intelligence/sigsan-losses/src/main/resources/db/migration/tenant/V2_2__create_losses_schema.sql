-- Losses Module Migration for Tenant Schema
-- Version: 2.2.0
-- Description: Create Losses module tables (Water Balance, DMA, Leak Detection, Pressure Analysis)
--
-- RLS, policy de tenant, trigger de auditoria e as colunas version/tenant_schema sao aplicados por
-- register_tenant_table() (V1_2), chamada no fim deste arquivo.

-- ========================================================================
-- DMA DISTRICTS
-- ========================================================================

CREATE TABLE IF NOT EXISTS dma_district (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    boundary GEOMETRY(Polygon, 4674),
    area_km2 DECIMAL(10,4),
    population_served INT,
    connections_count INT,
    pipe_length_km DECIMAL(10,3),
    avg_pressure_m DECIMAL(8,2),
    min_pressure_m DECIMAL(8,2),
    max_pressure_m DECIMAL(8,2),
    elevation_min_m DECIMAL(8,2),
    elevation_max_m DECIMAL(8,2),
    input_meter_id UUID,
    output_meter_id UUID,
    parent_dma_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN (
        'ACTIVE', 'INACTIVE', 'PLANNING', 'UNDER_CONSTRUCTION', 'DECOMMISSIONED'
    )),
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

-- FKs compostas (tenant_id, id) exigem uma constraint unica correspondente no pai.
ALTER TABLE dma_district ADD CONSTRAINT uq_dma_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_dma_tenant_code ON dma_district(tenant_id, code);
CREATE INDEX idx_dma_boundary ON dma_district USING GIST (boundary);
CREATE INDEX idx_dma_status ON dma_district(tenant_id, status);
CREATE INDEX idx_dma_parent ON dma_district(tenant_id, parent_dma_id);

ALTER TABLE dma_district ADD CONSTRAINT fk_dma_parent
    FOREIGN KEY (tenant_id, parent_dma_id) REFERENCES dma_district(tenant_id, id);

-- ========================================================================
-- WATER BALANCE (IWA/AWWA Methodology)
-- ========================================================================

CREATE TABLE IF NOT EXISTS water_balance (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    dma_id UUID,
    system_input_volume DECIMAL(14,3),
    authorized_consumption DECIMAL(14,3),
    billed_authorized DECIMAL(14,3),
    unbilled_authorized DECIMAL(14,3),
    apparent_losses DECIMAL(14,3),
    unauthorized_consumption DECIMAL(14,3),
    metering_inaccuracies DECIMAL(14,3),
    real_losses DECIMAL(14,3),
    leakage_transmission DECIMAL(14,3),
    leakage_storage DECIMAL(14,3),
    leakage_service_connections DECIMAL(14,3),
    non_revenue_water DECIMAL(14,3),
    nrw_percentage DECIMAL(8,4),
    infrastructure_leakage_index DECIMAL(8,4),
    unavoidable_annual_real_losses DECIMAL(14,3),
    data_quality_grade VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'APPROVED', 'ARCHIVED')),
    calculated_at TIMESTAMPTZ,
    calculated_by UUID,
    approved_at TIMESTAMPTZ,
    approved_by UUID,
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE INDEX idx_wb_period ON water_balance(tenant_id, period_start, period_end);
CREATE INDEX idx_wb_dma ON water_balance(tenant_id, dma_id);
CREATE INDEX idx_wb_status ON water_balance(tenant_id, status);

ALTER TABLE water_balance ADD CONSTRAINT fk_wb_dma
    FOREIGN KEY (tenant_id, dma_id) REFERENCES dma_district(tenant_id, id);

-- ========================================================================
-- LEAK DETECTION
-- ========================================================================

CREATE TABLE IF NOT EXISTS leak_detection (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    detection_code VARCHAR(50) NOT NULL UNIQUE,
    location GEOMETRY(Point, 4674) NOT NULL,
    address JSONB,
    dma_id UUID,
    detection_method VARCHAR(30) NOT NULL CHECK (detection_method IN (
        'ACOUSTIC_LOGGER', 'GROUND_MICROPHONE', 'CORRELATOR', 'GPR', 'THERMAL_CAMERA',
        'SATELLITE_INSAR', 'DRONE_THERMAL', 'SMART_METER_ANALYSIS', 'PRESSURE_ANALYSIS',
        'FLOW_ANALYSIS', 'CUSTOMER_REPORT', 'VISUAL_INSPECTION', 'TRACER_GAS'
    )),
    confidence_score DECIMAL(5,4),
    leak_type VARCHAR(30) CHECK (leak_type IN (
        'SERVICE_CONNECTION', 'MAIN_PIPE', 'VALVE', 'FITTING', 'HYDRANT',
        'METER', 'RESERVOIR', 'TREATMENT_PLANT', 'UNKNOWN'
    )),
    estimated_flow_lps DECIMAL(10,3),
    estimated_daily_loss_m3 DECIMAL(10,3),
    pipe_material VARCHAR(50),
    pipe_diameter_mm INT,
    pipe_age_years INT,
    depth_m DECIMAL(6,2),
    status VARCHAR(20) NOT NULL DEFAULT 'DETECTED' CHECK (status IN (
        'DETECTED', 'CONFIRMED', 'SCHEDULED', 'IN_REPAIR', 'REPAIRED', 'MONITORING', 'FALSE_POSITIVE', 'CLOSED'
    )),
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    detected_at TIMESTAMPTZ NOT NULL,
    detected_by UUID,
    confirmed_at TIMESTAMPTZ,
    confirmed_by UUID,
    repaired_at TIMESTAMPTZ,
    repaired_by UUID,
    repair_cost DECIMAL(12,2),
    repair_method VARCHAR(100),
    repair_duration_hours DECIMAL(10,2),
    water_saved_m3_day DECIMAL(10,3),
    photos JSONB,
    notes TEXT,
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE UNIQUE INDEX idx_leak_code ON leak_detection(tenant_id, detection_code);
CREATE INDEX idx_leak_coords ON leak_detection USING GIST (location);
CREATE INDEX idx_leak_status ON leak_detection(tenant_id, status);
CREATE INDEX idx_leak_detected ON leak_detection(tenant_id, detected_at);
CREATE INDEX idx_leak_dma ON leak_detection(tenant_id, dma_id);
CREATE INDEX idx_leak_priority ON leak_detection(tenant_id, priority);

ALTER TABLE leak_detection ADD CONSTRAINT fk_leak_dma
    FOREIGN KEY (tenant_id, dma_id) REFERENCES dma_district(tenant_id, id);

-- ========================================================================
-- PRESSURE ANALYSIS
-- ========================================================================

CREATE TABLE IF NOT EXISTS pressure_analysis (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    dma_id UUID,
    timestamp TIMESTAMPTZ NOT NULL,
    avg_pressure_m DECIMAL(8,2),
    min_pressure_m DECIMAL(8,2),
    max_pressure_m DECIMAL(8,2),
    pressure_variance DECIMAL(8,4),
    pressure_std_dev DECIMAL(8,4),
    transients_count INT,
    negative_pressure_events INT,
    avg_flow_lps DECIMAL(10,3),
    night_flow_lps DECIMAL(10,3),
    minimum_night_flow_lps DECIMAL(10,3),
    background_leakage_lps DECIMAL(10,3),
    burst_leakage_lps DECIMAL(10,3),
    is_anomaly BOOLEAN NOT NULL DEFAULT FALSE,
    anomaly_score DECIMAL(5,4),
    anomaly_type VARCHAR(50),
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE INDEX idx_pressure_dma_time ON pressure_analysis(tenant_id, dma_id, timestamp);
CREATE INDEX idx_pressure_time ON pressure_analysis(tenant_id, timestamp);
CREATE INDEX idx_pressure_anomaly ON pressure_analysis(tenant_id, is_anomaly);

ALTER TABLE pressure_analysis ADD CONSTRAINT fk_pressure_dma
    FOREIGN KEY (tenant_id, dma_id) REFERENCES dma_district(tenant_id, id);

-- ========================================================================
-- ISOLAMENTO E AUDITORIA
-- ========================================================================
-- Sem dados de exemplo aqui: um DMA fabricado num tenant real alimentaria o balanco hidrico com
-- numeros inventados. Dados de demonstracao pertencem a um seed explicito de demo, nao ao
-- provisionamento padrao.

SELECT register_tenant_table('dma_district');
SELECT register_tenant_table('water_balance');
SELECT register_tenant_table('leak_detection');
SELECT register_tenant_table('pressure_analysis');
