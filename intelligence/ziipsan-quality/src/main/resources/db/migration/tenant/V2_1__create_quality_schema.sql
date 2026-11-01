-- Quality Module Migration for Tenant Schema
-- Version: 2.1.0
-- Description: Create Quality module tables (Parameters, Sampling Points, Samples, Analysis Results)
--
-- RLS, policy de tenant, trigger de auditoria e as colunas version/tenant_schema sao aplicados por
-- register_tenant_table() (V1_2), chamada no fim deste arquivo.

-- ========================================================================
-- QUALITY PARAMETERS
-- ========================================================================

CREATE TABLE IF NOT EXISTS quality_parameter (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    unit VARCHAR(20),
    category VARCHAR(30) CHECK (category IN ('PHYSICAL', 'CHEMICAL', 'MICROBIOLOGICAL', 'RADIOLOGICAL', 'ORGANOLEPTIC')),
    portaria_ms_888 BOOLEAN NOT NULL DEFAULT FALSE,
    vmp DECIMAL(10,4),
    alert_min DECIMAL(10,4),
    alert_max DECIMAL(10,4),
    critical_min DECIMAL(10,4),
    critical_max DECIMAL(10,4),
    method_code VARCHAR(50),
    method_description TEXT,
    sampling_frequency VARCHAR(50),
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

-- FKs compostas (tenant_id, id) exigem uma constraint unica correspondente no pai.
ALTER TABLE quality_parameter ADD CONSTRAINT uq_quality_parameter_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_quality_param_tenant_code ON quality_parameter(tenant_id, code);
CREATE INDEX idx_quality_param_category ON quality_parameter(tenant_id, category);
CREATE INDEX idx_quality_param_portaria ON quality_parameter(tenant_id, portaria_ms_888);

-- ========================================================================
-- SAMPLING POINTS
-- ========================================================================

CREATE TABLE IF NOT EXISTS sampling_point (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    type VARCHAR(30) CHECK (type IN (
        'RAW_WATER', 'TREATED_WATER', 'DISTRIBUTION_NETWORK', 'RESERVOIR',
        'ETA_INLET', 'ETA_OUTLET', 'ETE_INLET', 'ETE_OUTLET', 'SLUDGE',
        'REUSE', 'CONSUMER_TAP', 'HYDRANT', 'WELL', 'SPRING'
    )),
    address JSONB,
    coordinates GEOMETRY(Point, 4674),
    system_id UUID,
    sector_id UUID,
    responsible_user_id UUID,
    parameters JSONB,
    sampling_frequency VARCHAR(50),
    collection_method VARCHAR(100),
    preservation_method VARCHAR(100),
    transport_conditions TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

-- FKs compostas (tenant_id, id) exigem uma constraint unica correspondente no pai.
ALTER TABLE sampling_point ADD CONSTRAINT uq_sampling_point_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_sampling_point_tenant_code ON sampling_point(tenant_id, code);
CREATE INDEX idx_sampling_point_type ON sampling_point(tenant_id, type);
CREATE INDEX idx_sampling_point_coords ON sampling_point USING GIST (coordinates);

-- ========================================================================
-- QUALITY SAMPLES
-- ========================================================================

CREATE TABLE IF NOT EXISTS quality_sample (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    sample_code VARCHAR(50) NOT NULL UNIQUE,
    sampling_point_id UUID NOT NULL,
    sample_type VARCHAR(30) CHECK (sample_type IN (
        'ROUTINE', 'SPECIAL', 'COMPLAINT', 'OPERATIONAL', 'RESEARCH', 'VALIDATION', 'AUDIT'
    )),
    status VARCHAR(30) NOT NULL DEFAULT 'PROGRAMMED' CHECK (status IN (
        'PROGRAMMED', 'COLLECTED', 'IN_TRANSIT', 'RECEIVED', 'IN_ANALYSIS',
        'ANALYZED', 'VALIDATED', 'PUBLISHED', 'REJECTED', 'RECOLLECT', 'ARCHIVED'
    )),
    collected_at TIMESTAMPTZ,
    collector_id UUID,
    collector_name VARCHAR(200),
    collection_method VARCHAR(100),
    volume_ml INT,
    container_type VARCHAR(50),
    preservative VARCHAR(100),
    temperature_celsius DECIMAL(5,2),
    ph_field DECIMAL(4,2),
    chlorine_residual_mg_l DECIMAL(6,3),
    turbidity_ntu DECIMAL(8,3),
    received_at TIMESTAMPTZ,
    received_by UUID,
    lab_id UUID,
    lab_sample_code VARCHAR(50),
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

-- FKs compostas (tenant_id, id) exigem uma constraint unica correspondente no pai.
ALTER TABLE quality_sample ADD CONSTRAINT uq_quality_sample_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_quality_sample_code ON quality_sample(tenant_id, sample_code);
CREATE INDEX idx_quality_sample_point ON quality_sample(tenant_id, sampling_point_id);
CREATE INDEX idx_quality_sample_status ON quality_sample(tenant_id, status);
CREATE INDEX idx_quality_sample_collected ON quality_sample(tenant_id, collected_at);
CREATE INDEX idx_quality_sample_type ON quality_sample(tenant_id, sample_type);

ALTER TABLE quality_sample ADD CONSTRAINT fk_quality_sample_point
    FOREIGN KEY (tenant_id, sampling_point_id) REFERENCES sampling_point(tenant_id, id);

-- ========================================================================
-- QUALITY ANALYSIS RESULTS
-- ========================================================================

CREATE TABLE IF NOT EXISTS quality_analysis_result (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    sample_id UUID NOT NULL,
    parameter_id UUID NOT NULL,
    result_value DECIMAL(12,6),
    result_text VARCHAR(500),
    unit VARCHAR(20),
    method_code VARCHAR(50),
    method_description TEXT,
    detection_limit DECIMAL(12,6),
    quantification_limit DECIMAL(12,6),
    uncertainty DECIMAL(8,4),
    vmp DECIMAL(10,4),
    conformity VARCHAR(20) CHECK (conformity IN ('CONFORMING', 'NON_CONFORMING', 'INCONCLUSIVE', 'NOT_APPLICABLE')),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (status IN (
        'PENDING', 'IN_PROGRESS', 'COMPLETED', 'VALIDATED', 'PUBLISHED', 'REJECTED', 'REANALYSIS'
    )),
    analyzed_at TIMESTAMPTZ,
    analyzed_by UUID,
    validated_at TIMESTAMPTZ,
    validated_by UUID,
    published_at TIMESTAMPTZ,
    published_by UUID,
    instrument_id UUID,
    instrument_reading TEXT,
    quality_control JSONB,
    observations TEXT,
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE INDEX idx_analysis_sample ON quality_analysis_result(tenant_id, sample_id);
CREATE INDEX idx_analysis_parameter ON quality_analysis_result(tenant_id, parameter_id);
CREATE INDEX idx_analysis_status ON quality_analysis_result(tenant_id, status);
CREATE INDEX idx_analysis_analyzed ON quality_analysis_result(tenant_id, analyzed_at);
CREATE INDEX idx_analysis_conformity ON quality_analysis_result(tenant_id, conformity);

ALTER TABLE quality_analysis_result ADD CONSTRAINT fk_analysis_sample
    FOREIGN KEY (tenant_id, sample_id) REFERENCES quality_sample(tenant_id, id);
ALTER TABLE quality_analysis_result ADD CONSTRAINT fk_analysis_parameter
    FOREIGN KEY (tenant_id, parameter_id) REFERENCES quality_parameter(tenant_id, id);

-- ========================================================================
-- SCADA TAGS
-- ========================================================================
-- Mapeia ScadaTag. Estas tabelas existiam como entidade JPA sem nenhuma migration: o primeiro
-- acesso falharia com "relation scada_tag does not exist".

CREATE TABLE IF NOT EXISTS scada_tag (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    tag_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    source_system VARCHAR(50),
    source_tag VARCHAR(100),
    unit VARCHAR(20),
    scale_factor NUMERIC(10,6) DEFAULT 1,
    offset_value NUMERIC(10,6) DEFAULT 0,
    min_raw NUMERIC,
    max_raw NUMERIC,
    min_engineering NUMERIC,
    max_engineering NUMERIC,
    deadband NUMERIC(10,6),
    scan_rate_ms INT DEFAULT 1000,
    compression_enabled BOOLEAN DEFAULT TRUE,
    compression_deviation NUMERIC(5,2) DEFAULT 0.01,
    sampling_point_id UUID,
    quality_parameter_id UUID,
    data_type VARCHAR(20) DEFAULT 'FLOAT',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    last_value NUMERIC,
    last_quality INT DEFAULT 192,
    last_timestamp TIMESTAMPTZ,
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

ALTER TABLE scada_tag ADD CONSTRAINT uq_scada_tag_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_scada_tag_tenant_name ON scada_tag(tenant_id, tag_name);
CREATE INDEX idx_scada_tag_name ON scada_tag(tag_name);
CREATE INDEX idx_scada_tag_source ON scada_tag(source_system);
CREATE INDEX idx_scada_tag_point ON scada_tag(sampling_point_id);

ALTER TABLE scada_tag ADD CONSTRAINT fk_scada_tag_point
    FOREIGN KEY (tenant_id, sampling_point_id) REFERENCES sampling_point(tenant_id, id);
ALTER TABLE scada_tag ADD CONSTRAINT fk_scada_tag_parameter
    FOREIGN KEY (tenant_id, quality_parameter_id) REFERENCES quality_parameter(tenant_id, id);

-- ========================================================================
-- SCADA DATA POINTS (serie temporal)
-- ========================================================================
-- Volume alto (uma linha por leitura). Sem trigger de auditoria: auditar cada leitura em JSONB
-- duplicaria o armazenamento sem valor. Esta tabela e candidata a hypertable no TimescaleDB.

CREATE TABLE IF NOT EXISTS scada_data_point (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    "timestamp" TIMESTAMPTZ NOT NULL,
    value NUMERIC(18,6),
    raw_value NUMERIC,
    quality INT DEFAULT 192,
    flags VARCHAR(50),
    interpolated BOOLEAN DEFAULT FALSE,
    annotation TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE INDEX idx_scada_data_tag_time ON scada_data_point(tag_id, "timestamp");
CREATE INDEX idx_scada_data_time ON scada_data_point("timestamp");
CREATE INDEX idx_scada_data_quality ON scada_data_point(quality);

ALTER TABLE scada_data_point ADD CONSTRAINT fk_scada_data_tag
    FOREIGN KEY (tenant_id, tag_id) REFERENCES scada_tag(tenant_id, id);

-- ========================================================================
-- LAB INSTRUMENTS
-- ========================================================================

CREATE TABLE IF NOT EXISTS lab_instrument (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    manufacturer VARCHAR(100),
    model VARCHAR(100),
    serial_number VARCHAR(100),
    instrument_type VARCHAR(50),
    location VARCHAR(200),
    parameters JSONB,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    calibration_due TIMESTAMPTZ,
    calibration_frequency_days INT DEFAULT 365,
    certification JSONB,
    software_version VARCHAR(50),
    communication_protocol VARCHAR(50),
    connection_config JSONB,
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

ALTER TABLE lab_instrument ADD CONSTRAINT uq_lab_instrument_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_lab_inst_tenant_code ON lab_instrument(tenant_id, code);
CREATE INDEX idx_lab_inst_type ON lab_instrument(instrument_type);
CREATE INDEX idx_lab_inst_status ON lab_instrument(status);

-- ========================================================================
-- LAB ANALYSIS METHODS
-- ========================================================================

CREATE TABLE IF NOT EXISTS lab_analysis_method (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    parameter_id UUID,
    instrument_id UUID,
    standard_method VARCHAR(100),
    method_version VARCHAR(20),
    sample_preparation TEXT,
    "procedure" TEXT,
    calculation_formula TEXT,
    reportable_limit NUMERIC(12,6),
    detection_limit NUMERIC(12,6),
    quantification_limit NUMERIC(12,6),
    precision_rsd NUMERIC(5,2),
    accuracy_recovery NUMERIC(5,2),
    measurement_uncertainty NUMERIC(8,4),
    reagents JSONB,
    equipment_setup JSONB,
    quality_control JSONB,
    safety_notes TEXT,
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

ALTER TABLE lab_analysis_method ADD CONSTRAINT uq_lab_analysis_method_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_lab_method_tenant_code ON lab_analysis_method(tenant_id, code);
CREATE INDEX idx_lab_method_code ON lab_analysis_method(code);
CREATE INDEX idx_lab_method_param ON lab_analysis_method(parameter_id);
CREATE INDEX idx_lab_method_inst ON lab_analysis_method(instrument_id);

ALTER TABLE lab_analysis_method ADD CONSTRAINT fk_lab_method_parameter
    FOREIGN KEY (tenant_id, parameter_id) REFERENCES quality_parameter(tenant_id, id);
ALTER TABLE lab_analysis_method ADD CONSTRAINT fk_lab_method_instrument
    FOREIGN KEY (tenant_id, instrument_id) REFERENCES lab_instrument(tenant_id, id);

-- ========================================================================
-- LAB QC SAMPLES
-- ========================================================================

CREATE TABLE IF NOT EXISTS lab_qc_sample (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    method_id UUID NOT NULL,
    qc_type VARCHAR(30),
    sample_id VARCHAR(50),
    expected_value NUMERIC(12,6),
    measured_value NUMERIC(12,6),
    unit VARCHAR(20),
    deviation NUMERIC(8,4),
    deviation_percent NUMERIC(8,4),
    control_limit_upper NUMERIC(12,6),
    control_limit_lower NUMERIC(12,6),
    warning_limit_upper NUMERIC(12,6),
    warning_limit_lower NUMERIC(12,6),
    status VARCHAR(20) DEFAULT 'PENDING',
    analyzed_at TIMESTAMPTZ,
    analyzed_by UUID,
    instrument_id UUID,
    run_id VARCHAR(50),
    observations TEXT,
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE INDEX idx_qc_sample_method ON lab_qc_sample(method_id);
CREATE INDEX idx_qc_sample_type ON lab_qc_sample(qc_type);
CREATE INDEX idx_qc_sample_analyzed ON lab_qc_sample(analyzed_at);
CREATE INDEX idx_qc_sample_status ON lab_qc_sample(status);

ALTER TABLE lab_qc_sample ADD CONSTRAINT fk_qc_sample_method
    FOREIGN KEY (tenant_id, method_id) REFERENCES lab_analysis_method(tenant_id, id);
ALTER TABLE lab_qc_sample ADD CONSTRAINT fk_qc_sample_instrument
    FOREIGN KEY (tenant_id, instrument_id) REFERENCES lab_instrument(tenant_id, id);

-- ========================================================================
-- CATALOGO DE PARAMETROS DE QUALIDADE: INTENCIONALMENTE VAZIO
-- ========================================================================
-- Uma versao anterior semeava 28 parametros 'da Portaria MS 888/2021'. Conferidos contra o texto
-- da norma (docs/levantamento/09-regulatorio-qualidade-da-agua.md), 16 divergiam ou nao tinham
-- base: acidos haloaceticos com valor diferente do da norma, fluoreto com VMP errado, cloro residual
-- (que e piso, nao VMP) tratado como limite maximo, 'agrotoxicos totais' que nao existe, e a
-- Portaria GM/MS 2.472/2021 (que substituiu o Anexo 13) ignorada. Dos 100 parametros dos Anexos
-- 9 a 11, 84 faltavam. Um VMP escalar tambem nao representa a norma: coliformes totais variam por
-- populacao, turbidez e percentil mensal, subprodutos sao media movel de 12 meses.
--
-- Conformidade de agua potavel e dado regulatorio. O catalogo deve ser carregado a partir do texto
-- da norma, numa migration propria, com modelo que suporte regra de avaliacao (nao so um valor),
-- e conferido por responsavel tecnico em qualidade da agua.

-- ========================================================================
-- ISOLAMENTO E AUDITORIA
-- ========================================================================

SELECT register_tenant_table('quality_parameter');
SELECT register_tenant_table('sampling_point');
SELECT register_tenant_table('quality_sample');
SELECT register_tenant_table('quality_analysis_result');
SELECT register_tenant_table('scada_tag');
SELECT register_tenant_table('scada_data_point', p_audit => false);
SELECT register_tenant_table('lab_instrument');
SELECT register_tenant_table('lab_analysis_method');
SELECT register_tenant_table('lab_qc_sample');
