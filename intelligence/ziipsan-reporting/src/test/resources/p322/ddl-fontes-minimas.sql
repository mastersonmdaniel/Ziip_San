-- DDL MINIMA das tabelas-fonte, so com as colunas que as views da V15_1 leem
-- (conferidas nas migrations reais: V6_2, V4_1, V4_3, V5_1, V3_x, V8_1, V9_1,
-- V9_2, V4_2, V4_5). Serve ao ReportViewsDatabaseTest; NAO e migration.
CREATE TABLE payments (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, bill_id UUID, unit_id UUID,
    channel VARCHAR(20), gross_amount NUMERIC(14,2), segregated_tax_amount NUMERIC(14,2),
    withheld_amount NUMERIC(14,2), net_amount NUMERIC(14,2), paid_on DATE, collection_month DATE,
    fiscal_document_id UUID, payer_document VARCHAR(14), status VARCHAR(20),
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE return_files (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, nsa VARCHAR(20),
    file_hash VARCHAR(64), file_name VARCHAR(200), receiver_document VARCHAR(14),
    received_on DATE, status VARCHAR(30), created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE return_file_details (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, return_file_id UUID,
    payment_id UUID, line_number INT, barcode VARCHAR(60), capture_form VARCHAR(30),
    payment_form VARCHAR(30), collecting_branch VARCHAR(20), authentication VARCHAR(60),
    payer_document VARCHAR(14), paid_amount NUMERIC(14,2), paid_on DATE,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE bills (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, unit_id UUID, reference_month DATE,
    status VARCHAR(30), water_consumption INT, water_value NUMERIC(14,2), sewer_value NUMERIC(14,2),
    debits_value NUMERIC(14,2), credits_value NUMERIC(14,2), taxes_value NUMERIC(14,2),
    total_value NUMERIC(14,2), due_date DATE, issued_on DATE, rectification_count INT DEFAULT 0,
    cancel_reason_id UUID, origin_bill_id UUID, completed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW());
-- bill_cancel_reasons: catalogo real da V4_16 (DAD-16), que substituiu o texto
-- livre bills.cancel_reason (removido pela V4_16) — correcao D72-V15_1: a
-- fixture espelhava a V4_1; as colunas sao as que a RPT-010 le (id/tenant_id)
-- mais code/description do catalogo real.
CREATE TABLE bill_cancel_reasons (id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL, description VARCHAR(200) NOT NULL);
CREATE TABLE categories (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, code VARCHAR(30), name VARCHAR(100));
CREATE TABLE bill_items (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, bill_id UUID,
    service_kind VARCHAR(5), category_id UUID, subcategory_id UUID, tariff_version_id UUID,
    period_from DATE, period_to DATE, proration_factor NUMERIC(7,4), line_type VARCHAR(7),
    economies INT, social_economies INT DEFAULT 0, from_m3 INT, to_m3 INT, price_per_m3 NUMERIC(14,4),
    consumption INT, gross_value NUMERIC(14,2), discount_value NUMERIC(14,2) DEFAULT 0,
    net_value NUMERIC(14,2), created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE billing_groups (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, code VARCHAR(30),
    name VARCHAR(100), route_code VARCHAR(40), active BOOLEAN DEFAULT TRUE);
CREATE TABLE billing_references (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, reference_month DATE,
    status VARCHAR(20), closed_at TIMESTAMPTZ);
CREATE TABLE cycle_schedule_items (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, reference_id UUID,
    group_id UUID, activity_id UUID, occurrence_no INT, status VARCHAR(20));
CREATE TABLE consumption_records (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, unit_id UUID,
    service_kind VARCHAR(5), reference DATE, billed_consumption INT, calculated_consumption INT,
    billable BOOLEAN, billing_paused BOOLEAN, consumption_type VARCHAR(30), anomaly_id UUID,
    superseded_by_id UUID, created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE reading_anomalies (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, code VARCHAR(20), description VARCHAR(200));
CREATE TABLE meter_readings (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, unit_id UUID,
    installation_id UUID, kind VARCHAR(5), reference DATE, read_on DATE, reading INT,
    reading_status VARCHAR(15), anomaly_id UUID, created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE property_profiles (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, name VARCHAR(100));
CREATE TABLE consumer_units (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, registration_number VARCHAR(50),
    property_profile_id UUID, parent_unit_id UUID, status VARCHAR(20), occupancy_status VARCHAR(10),
    deleted_at TIMESTAMPTZ);
CREATE TABLE service_protocols (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, protocol_year INT,
    protocol_number INT, channel VARCHAR(20), spec_id UUID, consumer_unit_id UUID,
    requester_role VARCHAR(20), opened_date DATE, original_due_date DATE, current_due_date DATE,
    status VARCHAR(20), close_reason_code VARCHAR(60), closed_at TIMESTAMPTZ, created_by UUID,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE service_types (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, code VARCHAR(40), name VARCHAR(100));
CREATE TABLE service_orders (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, service_type_id UUID,
    protocol_id UUID, origin VARCHAR(20), execution_mode VARCHAR(20), status VARCHAR(30),
    created_by UUID, created_at TIMESTAMPTZ DEFAULT NOW(), executed_at TIMESTAMPTZ, closed_at TIMESTAMPTZ);
CREATE TABLE cutoff_notices (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, consumer_unit_id UUID,
    notice_date DATE, earliest_cut_date DATE, template_code TEXT, template_version TEXT,
    delivery_proof_status VARCHAR(20), total_amount NUMERIC(14,2), status VARCHAR(20),
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE cutoff_non_generation_reasons (id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    consumer_unit_id UUID, evaluated_at TIMESTAMPTZ, reason_code VARCHAR(40),
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE social_tariff_enrollments (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, unit_id UUID,
    eligibility_basis VARCHAR(20), status VARCHAR(20), social_economies INT,
    valid_from DATE, valid_to DATE, created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE fiscal_documents (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, status VARCHAR(30),
    created_at TIMESTAMPTZ DEFAULT NOW());
-- Correcao pos-revisao (B1/B2): tabelas REAIS que faltavam - a omissao delas
-- era o que deixava as views protegidas "passarem" vazias no teste.
-- optional_category_enrollments: DDL real da V4_4 (P2.5) - SEM coluna status.
CREATE TABLE optional_category_enrollments (id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL, category_code VARCHAR(40) NOT NULL, criterion TEXT NOT NULL,
    proof TEXT NOT NULL, valid_from DATE NOT NULL, valid_to DATE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW());
-- P3.29 real (V6_5): devolucoes, execucoes, fila de nao identificados e eventos.
CREATE TABLE refund_requests (id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    service_order_id UUID NOT NULL, origin_payment_id UUID,
    refund_type VARCHAR(24) NOT NULL, source VARCHAR(40) NOT NULL,
    base_amount NUMERIC(14,2), refund_amount NUMERIC(14,2),
    double_applied BOOLEAN NOT NULL DEFAULT FALSE,
    refund_mode VARCHAR(20) NOT NULL, chosen_mode VARCHAR(20),
    refund_destination VARCHAR(24), status VARCHAR(24) NOT NULL DEFAULT 'PENDENTE',
    pending_reason VARCHAR(28), requested_by UUID NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE refund_executions (id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    refund_request_id UUID NOT NULL, service_order_id UUID NOT NULL,
    effect VARCHAR(24) NOT NULL, amount NUMERIC(14,2) NOT NULL,
    credited_bill_id UUID, bank_reference VARCHAR(80),
    executed_at TIMESTAMPTZ DEFAULT NOW(), created_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE unidentified_payments (id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    payment_id UUID NOT NULL, queue_reason VARCHAR(28) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'NA_FILA', unit_id UUID,
    document_type VARCHAR(30), document_id UUID,
    identification_service_order_id UUID, identified_by UUID, identified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE payment_events (id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
    payment_id UUID NOT NULL, event_type VARCHAR(40) NOT NULL, payload JSONB NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW());
