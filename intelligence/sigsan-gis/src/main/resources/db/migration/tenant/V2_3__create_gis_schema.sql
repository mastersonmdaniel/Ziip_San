-- GIS Module Migration for Tenant Schema
-- Version: 2.3.0
-- Description: Create GIS module tables (Network Elements, EPANET Simulation)
--
-- Depende de dma_district (V2_2, modulo losses). RLS, policy de tenant, trigger de auditoria e as
-- colunas version/tenant_schema sao aplicados por register_tenant_table() (V1_2) no fim do arquivo.

-- ========================================================================
-- NETWORK ELEMENTS (Unified GIS Layer)
-- ========================================================================

CREATE TABLE IF NOT EXISTS gis_network_element (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    element_code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200),
    element_type VARCHAR(30) NOT NULL CHECK (element_type IN (
        -- Água
        'PIPE', 'VALVE', 'HYDRANT', 'METER', 'MACRO_METER', 'PRESSURE_REDUCING_VALVE',
        'AIR_VALVE', 'BLOWOFF_VALVE', 'CHECK_VALVE', 'RESERVOIR', 'TANK', 'PUMP_STATION',
        'TREATMENT_PLANT', 'WELL', 'SPRING', 'INTAKE', 'CONNECTION', 'SERVICE_LINE',
        -- Esgoto
        'SEWER_PIPE', 'MANHOLE', 'SEWER_VALVE', 'LIFT_STATION', 'SEWER_TREATMENT_PLANT',
        'SEWER_CONNECTION', 'GREASE_TRAP', 'SIPHON', 'OVERFLOW_WEIR',
        -- Comum
        'NODE', 'JUNCTION', 'REDUCER', 'TEE', 'CROSS', 'CAP', 'FLANGE', 'COUPLING',
        'SUPPORT', 'ANCHOR', 'THRUST_BLOCK', 'CATHODIC_PROTECTION', 'MONITORING_POINT'
    )),
    geometry GEOMETRY(Geometry, 4674) NOT NULL,
    dma_id UUID,
    material VARCHAR(50),
    diameter_mm INT,
    length_m DECIMAL(10,2),
    installation_date TIMESTAMPTZ,
    age_years INT,
    depth_m DECIMAL(6,2),
    pressure_rating_bar DECIMAL(6,2),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    condition_rating VARCHAR(20),
    criticality VARCHAR(20),
    properties JSONB DEFAULT '{}',
    last_inspection_at TIMESTAMPTZ,
    next_inspection_due TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE UNIQUE INDEX idx_gis_elem_tenant_code ON gis_network_element(tenant_id, element_code);
CREATE INDEX idx_gis_elem_type ON gis_network_element(tenant_id, element_type);
CREATE INDEX idx_gis_elem_geometry ON gis_network_element USING GIST (geometry);
CREATE INDEX idx_gis_elem_dma ON gis_network_element(tenant_id, dma_id);
CREATE INDEX idx_gis_elem_status ON gis_network_element(tenant_id, status);

ALTER TABLE gis_network_element ADD CONSTRAINT fk_gis_elem_dma
    FOREIGN KEY (tenant_id, dma_id) REFERENCES dma_district(tenant_id, id);

-- ========================================================================
-- EPANET SIMULATION RESULTS
-- ========================================================================

CREATE TABLE IF NOT EXISTS epanet_simulation (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    dma_id UUID,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    inp_content TEXT,
    simulation_options JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN (
        'PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'
    )),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    duration_seconds DECIMAL(10,3),
    node_results JSONB, -- {node_id: {pressure, head, demand, quality}}
    link_results JSONB, -- {link_id: {flow, velocity, headloss, status, setting}}
    warnings JSONB,
    error_message TEXT,
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

-- FKs compostas (tenant_id, id) exigem uma constraint unica correspondente no pai.
ALTER TABLE epanet_simulation ADD CONSTRAINT uq_epanet_simulation_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_epanet_dma ON epanet_simulation(tenant_id, dma_id);
CREATE INDEX idx_epanet_status ON epanet_simulation(tenant_id, status);
CREATE INDEX idx_epanet_created ON epanet_simulation(tenant_id, created_at);

ALTER TABLE epanet_simulation ADD CONSTRAINT fk_epanet_dma
    FOREIGN KEY (tenant_id, dma_id) REFERENCES dma_district(tenant_id, id);

-- ========================================================================
-- SIMULATION NODE RESULTS (Time-series)
-- ========================================================================

CREATE TABLE IF NOT EXISTS epanet_node_result (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    simulation_id UUID NOT NULL,
    node_id VARCHAR(100) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    demand DECIMAL(12,6),
    head DECIMAL(12,4),
    pressure DECIMAL(12,4),
    quality DECIMAL(12,6),
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_epanet_node_sim ON epanet_node_result(tenant_id, simulation_id);
CREATE INDEX idx_epanet_node_time ON epanet_node_result(tenant_id, node_id, timestamp);

ALTER TABLE epanet_node_result ADD CONSTRAINT fk_enode_sim
    FOREIGN KEY (tenant_id, simulation_id) REFERENCES epanet_simulation(tenant_id, id);

-- ========================================================================
-- SIMULATION LINK RESULTS (Time-series)
-- ========================================================================

CREATE TABLE IF NOT EXISTS epanet_link_result (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    simulation_id UUID NOT NULL,
    link_id VARCHAR(100) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    flow DECIMAL(12,4),
    velocity DECIMAL(8,4),
    headloss DECIMAL(12,4),
    status INT,
    setting DECIMAL(10,4),
    reynolds DECIMAL(12,4),
    froude DECIMAL(8,4),
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_epanet_link_sim ON epanet_link_result(tenant_id, simulation_id);
CREATE INDEX idx_epanet_link_time ON epanet_link_result(tenant_id, link_id, timestamp);

ALTER TABLE epanet_link_result ADD CONSTRAINT fk_elink_sim
    FOREIGN KEY (tenant_id, simulation_id) REFERENCES epanet_simulation(tenant_id, id);

-- ========================================================================
-- HYDRAULIC INDICATORS (Calculated from simulations)
-- ========================================================================

CREATE TABLE IF NOT EXISTS hydraulic_indicators (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    dma_id UUID,
    simulation_id UUID,
    period_start TIMESTAMPTZ,
    period_end TIMESTAMPTZ,
    avg_pressure DECIMAL(10,4),
    min_pressure DECIMAL(10,4),
    max_pressure DECIMAL(10,4),
    pressure_deficit_count INT,
    pressure_excess_count INT,
    avg_velocity DECIMAL(8,4),
    max_velocity DECIMAL(8,4),
    high_velocity_count INT,
    total_headloss DECIMAL(14,4),
    energy_efficiency DECIMAL(8,4),
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE INDEX idx_hydro_dma ON hydraulic_indicators(tenant_id, dma_id);
CREATE INDEX idx_hydro_period ON hydraulic_indicators(tenant_id, period_start, period_end);

ALTER TABLE hydraulic_indicators ADD CONSTRAINT fk_hydro_dma
    FOREIGN KEY (tenant_id, dma_id) REFERENCES dma_district(tenant_id, id);
ALTER TABLE hydraulic_indicators ADD CONSTRAINT fk_hydro_sim
    FOREIGN KEY (tenant_id, simulation_id) REFERENCES epanet_simulation(tenant_id, id);

-- ========================================================================
-- SCENARIO ANALYSIS
-- ========================================================================

CREATE TABLE IF NOT EXISTS scenario_analysis (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    dma_id UUID,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    base_simulation_id UUID,
    scenario_simulation_id UUID,
    scenario_type VARCHAR(50), -- PIPE_BREAK, VALVE_CLOSURE, DEMAND_CHANGE, PUMP_FAILURE, NEW_CONNECTION
    modifications JSONB, -- Changes applied
    comparison_results JSONB, -- Differences between base and scenario
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN (
        'PENDING', 'RUNNING', 'COMPLETED', 'FAILED'
    )),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE INDEX idx_scenario_dma ON scenario_analysis(tenant_id, dma_id);
CREATE INDEX idx_scenario_status ON scenario_analysis(tenant_id, status);

ALTER TABLE scenario_analysis ADD CONSTRAINT fk_scenario_dma
    FOREIGN KEY (tenant_id, dma_id) REFERENCES dma_district(tenant_id, id);
ALTER TABLE scenario_analysis ADD CONSTRAINT fk_scenario_base_sim
    FOREIGN KEY (tenant_id, base_simulation_id) REFERENCES epanet_simulation(tenant_id, id);
ALTER TABLE scenario_analysis ADD CONSTRAINT fk_scenario_scenario_sim
    FOREIGN KEY (tenant_id, scenario_simulation_id) REFERENCES epanet_simulation(tenant_id, id);

-- ========================================================================
-- ISOLAMENTO E AUDITORIA
-- ========================================================================
-- Resultados de simulacao sao series temporais reproduziveis a partir do .inp: sem entidade
-- (nao herdam de BaseEntity) e sem trigger de auditoria, que duplicaria cada linha em JSONB.

SELECT register_tenant_table('gis_network_element');
SELECT register_tenant_table('epanet_simulation');
SELECT register_tenant_table('epanet_node_result', p_entity => false, p_audit => false);
SELECT register_tenant_table('epanet_link_result', p_entity => false, p_audit => false);
SELECT register_tenant_table('hydraulic_indicators');
SELECT register_tenant_table('scenario_analysis');
