-- DDL minima das fontes REAIS da ED-46 (colunas copiadas das migrations
-- verdadeiras; so as colunas que as views leem):
--   service_protocols          <- V8_1  (P1.36)
--   cutoff_compensations       <- V9_2  (P3.4) + V110 (P-VP5-ED46-01: formula VARCHAR(80))
--   unit_subcategories/subcategories/categories <- V3_1 (registry)
--   meter_readings             <- V5_1  (P2.7) - com reader_id/captured_at reais
--   personal_data_access_log   <- V1_6  (P0.11)
--   report_definitions         <- V15_1 (P3.22) - so para a seed das definicoes
CREATE TABLE service_protocols (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, protocol_year INT,
    protocol_number INT, channel VARCHAR(20), spec_id UUID, consumer_unit_id UUID,
    requester_role VARCHAR(20), opened_date DATE, original_due_date DATE, current_due_date DATE,
    status VARCHAR(20), close_reason_code VARCHAR(60), closed_at TIMESTAMPTZ, created_by UUID,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE cutoff_compensations (id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    consumer_unit_id UUID NOT NULL, cutoff_order_id UUID NOT NULL, trigger_reason VARCHAR(20) NOT NULL,
    formula VARCHAR(80) NOT NULL, credit_amount NUMERIC(14,2), status VARCHAR(20) NOT NULL,
    reconnection_due_at TIMESTAMPTZ, created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE categories (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, code VARCHAR(30), name VARCHAR(100));
CREATE TABLE subcategories (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, category_id UUID NOT NULL,
    code VARCHAR(20), name VARCHAR(100));
CREATE TABLE unit_subcategories (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, unit_id UUID NOT NULL,
    subcategory_id UUID NOT NULL, economies INT NOT NULL);
CREATE TABLE meter_readings (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, unit_id UUID,
    installation_id UUID, kind VARCHAR(5), reference DATE, read_on DATE, reading INT,
    reading_status VARCHAR(15), anomaly_id UUID, source VARCHAR(15), reader_id UUID,
    captured_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE personal_data_access_log (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, user_id UUID,
    accessed_at TIMESTAMPTZ NOT NULL, access_type VARCHAR(20) NOT NULL, entity_type VARCHAR(100),
    entity_id UUID, result VARCHAR(20) NOT NULL);
CREATE TABLE report_definitions (id UUID PRIMARY KEY DEFAULT uuid_generate_v4(), tenant_id UUID,
    code VARCHAR(60) NOT NULL, title VARCHAR(200) NOT NULL, origin VARCHAR(20) NOT NULL DEFAULT 'BUILTIN',
    view_name VARCHAR(80) NOT NULL, required_permission VARCHAR(80) NOT NULL,
    columns_spec JSONB NOT NULL, filters_spec JSONB NOT NULL DEFAULT '[]'::jsonb,
    default_sort VARCHAR(200), max_rows INT NOT NULL DEFAULT 10000, active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID, updated_by UUID);
