-- Tenant Schema Template Migration
-- Version: 1.1.0
-- Description: Core tables created in each tenant schema
-- This migration is run against each tenant schema individually

-- ========================================================================
-- TENANT METADATA (one row per tenant schema)
-- ========================================================================

CREATE TABLE IF NOT EXISTS tenant_metadata (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL UNIQUE,
    schema_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(200),
    cnpj VARCHAR(18),
    address JSONB,
    settings JSONB DEFAULT '{}',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_tenant_metadata_tenant_id ON tenant_metadata(tenant_id);

-- ========================================================================
-- USERS AND ROLES
-- ========================================================================

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    full_name VARCHAR(200),
    phone VARCHAR(20),
    password_hash VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMPTZ,
    failed_login_attempts INT DEFAULT 0,
    locked_until TIMESTAMPTZ,
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_secret VARCHAR(255),
    preferences JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE UNIQUE INDEX idx_users_tenant_username ON users(tenant_id, username);
CREATE UNIQUE INDEX idx_users_tenant_email ON users(tenant_id, email);
CREATE INDEX idx_users_tenant_status ON users(tenant_id, status);

CREATE TABLE IF NOT EXISTS roles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    permissions JSONB DEFAULT '[]',
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_roles_tenant_code ON roles(tenant_id, code);

CREATE TABLE IF NOT EXISTS user_roles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    assigned_by UUID,
    expires_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_user_roles_unique ON user_roles(user_id, role_id);

-- ========================================================================
-- CONSUMER UNITS (UNIDADES CONSUMIDORAS)
-- ========================================================================

CREATE TABLE IF NOT EXISTS consumer_units (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    matricula VARCHAR(50) NOT NULL,
    dv CHAR(1),
    owner_id UUID REFERENCES users(id),
    responsible_id UUID REFERENCES users(id),
    category_id UUID,
    tariff_type_id UUID,
    water_economies INT DEFAULT 1,
    sewage_economies INT DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    water_connection_date DATE,
    sewage_connection_date DATE,
    address JSONB,
    coordinates GEOMETRY(POINT, 4674),
    reading_route_id UUID,
    reading_sequence INT,
    meter_id UUID,
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE UNIQUE INDEX idx_consumer_units_tenant_matricula ON consumer_units(tenant_id, matricula);
CREATE INDEX idx_consumer_units_tenant_status ON consumer_units(tenant_id, status);
CREATE INDEX idx_consumer_units_coordinates ON consumer_units USING GIST (coordinates);

-- ========================================================================
-- ADDRESSES
-- ========================================================================

CREATE TABLE IF NOT EXISTS addresses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL, -- PHYSICAL, BILLING, CORRESPONDENCE, ALTERNATIVE
    cep VARCHAR(10),
    street VARCHAR(200),
    number VARCHAR(20),
    complement VARCHAR(100),
    neighborhood VARCHAR(100),
    city VARCHAR(100),
    state CHAR(2),
    country VARCHAR(2) DEFAULT 'BR',
    coordinates GEOMETRY(POINT, 4674),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_addresses_entity ON addresses(tenant_id, entity_type, entity_id);

-- ========================================================================
-- DOCUMENTS (anexos, fotos, comprovantes)
-- ========================================================================

CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL, -- PHOTO, CONTRACT, INVOICE, PROOF, CERTIFICATE, etc.
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100),
    file_size BIGINT,
    storage_path VARCHAR(500) NOT NULL, -- MinIO path
    checksum VARCHAR(64), -- SHA-256
    metadata JSONB DEFAULT '{}',
    uploaded_by UUID,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_documents_entity ON documents(tenant_id, entity_type, entity_id);
CREATE INDEX idx_documents_type ON documents(tenant_id, type);

-- ========================================================================
-- SERVICE ORDERS (OS)
-- ========================================================================

CREATE TABLE IF NOT EXISTS service_orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    order_number VARCHAR(50) NOT NULL,
    service_code_id UUID,
    consumer_unit_id UUID REFERENCES consumer_units(id),
    address_id UUID REFERENCES addresses(id),
    requester_id UUID REFERENCES users(id),
    assignee_id UUID REFERENCES users(id),
    team_id UUID,
    vehicle_id UUID,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    scheduled_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    cancellation_reason TEXT,
    description TEXT,
    resolution TEXT,
    materials_used JSONB DEFAULT '[]',
    photos JSONB DEFAULT '[]',
    gps_track GEOMETRY(LINESTRING, 4674),
    form_data JSONB DEFAULT '{}',
    parent_order_id UUID REFERENCES service_orders(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE UNIQUE INDEX idx_service_orders_tenant_number ON service_orders(tenant_id, order_number);
CREATE INDEX idx_service_orders_tenant_status ON service_orders(tenant_id, status);
CREATE INDEX idx_service_orders_tenant_assignee ON service_orders(tenant_id, assignee_id);
CREATE INDEX idx_service_orders_scheduled ON service_orders(tenant_id, scheduled_at);

-- ========================================================================
-- ENABLE ROW LEVEL SECURITY ON ALL TABLES
-- ========================================================================
--
-- FORCE e obrigatorio aqui, nao redundante. O dono das tabelas e sigsan_migrator (o schema e
-- criado com AUTHORIZATION sigsan_migrator) e o dono ignora RLS a menos que FORCE esteja
-- ligado. Como o migratorDataSource esta na mesma JVM que a aplicacao, qualquer JdbcTemplate
-- futuro construído sobre ele leria qualquer schema de tenant sem restricao. FORCE fecha essa
-- porta com custo zero.

ALTER TABLE tenant_metadata ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_metadata FORCE ROW LEVEL SECURITY;
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;
ALTER TABLE roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE roles FORCE ROW LEVEL SECURITY;
ALTER TABLE user_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_roles FORCE ROW LEVEL SECURITY;
ALTER TABLE consumer_units ENABLE ROW LEVEL SECURITY;
ALTER TABLE consumer_units FORCE ROW LEVEL SECURITY;
ALTER TABLE addresses ENABLE ROW LEVEL SECURITY;
ALTER TABLE addresses FORCE ROW LEVEL SECURITY;
ALTER TABLE documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE documents FORCE ROW LEVEL SECURITY;
ALTER TABLE service_orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE service_orders FORCE ROW LEVEL SECURITY;

-- ========================================================================
-- RLS POLICIES
-- ========================================================================

-- tenant_metadata
CREATE POLICY tenant_metadata_isolation ON tenant_metadata
    USING (tenant_id = current_tenant_id());

-- users
CREATE POLICY users_isolation ON users
    USING (tenant_id = current_tenant_id());

-- roles
CREATE POLICY roles_isolation ON roles
    USING (tenant_id = current_tenant_id());

-- user_roles
CREATE POLICY user_roles_isolation ON user_roles
    USING (tenant_id = current_tenant_id());

-- consumer_units
CREATE POLICY consumer_units_isolation ON consumer_units
    USING (tenant_id = current_tenant_id());

-- addresses
CREATE POLICY addresses_isolation ON addresses
    USING (tenant_id = current_tenant_id());

-- documents
CREATE POLICY documents_isolation ON documents
    USING (tenant_id = current_tenant_id());

-- service_orders
CREATE POLICY service_orders_isolation ON service_orders
    USING (tenant_id = current_tenant_id());

-- ========================================================================
-- AUDIT TRIGGER FUNCTION
-- ========================================================================

CREATE OR REPLACE FUNCTION audit_trigger_function() RETURNS TRIGGER AS $$
DECLARE
    old_row JSONB;
    new_row JSONB;
    changed_fields JSONB;
BEGIN
    -- to_jsonb(NEW) serializa a linha INTEIRA, o que colocava password_hash e mfa_secret
    -- em texto claro dentro de public.audit_log. Como audit_log era legivel por todos os
    -- tenants, qualquer requisicao autenticada extraia credencial em massa. As colunas
    -- sensiveis sao removidas antes de gravar.
    IF TG_OP = 'INSERT' THEN
        new_row := to_jsonb(NEW)
            - 'password_hash' - 'mfa_secret' - 'password' - 'secret'
            - 'token' - 'refresh_token' - 'api_key' - 'private_key';
        INSERT INTO public.audit_log (tenant_id, entity_type, entity_id, action, new_values, created_at, created_by)
        VALUES (current_tenant_id(), TG_TABLE_NAME, NEW.id, 'CREATE', new_row, NOW(), current_setting('app.current_user', TRUE)::UUID);
        RETURN NEW;
    ELSIF TG_OP = 'UPDATE' THEN
        old_row := to_jsonb(OLD)
            - 'password_hash' - 'mfa_secret' - 'password' - 'secret'
            - 'token' - 'refresh_token' - 'api_key' - 'private_key';
        new_row := to_jsonb(NEW)
            - 'password_hash' - 'mfa_secret' - 'password' - 'secret'
            - 'token' - 'refresh_token' - 'api_key' - 'private_key';
        changed_fields := (
            SELECT jsonb_object_agg(key, new_row[key])
            FROM jsonb_object_keys(new_row) AS key
            WHERE NOT (old_row[key] IS NOT DISTINCT FROM new_row[key])
        );
        INSERT INTO public.audit_log (tenant_id, entity_type, entity_id, action, old_values, new_values, changed_fields, created_at, created_by)
        VALUES (current_tenant_id(), TG_TABLE_NAME, NEW.id, 'UPDATE', old_row, new_row, changed_fields, NOW(), current_setting('app.current_user', TRUE)::UUID);
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        old_row := to_jsonb(OLD)
            - 'password_hash' - 'mfa_secret' - 'password' - 'secret'
            - 'token' - 'refresh_token' - 'api_key' - 'private_key';
        INSERT INTO public.audit_log (tenant_id, entity_type, entity_id, action, old_values, created_at, created_by)
        VALUES (current_tenant_id(), TG_TABLE_NAME, OLD.id, 'DELETE', old_row, NOW(), current_setting('app.current_user', TRUE)::UUID);
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Apply audit triggers to all tables
DO $$
DECLARE
    tbl RECORD;
BEGIN
    FOR tbl IN
        SELECT table_name FROM information_schema.tables
        WHERE table_schema = current_schema()
        AND table_type = 'BASE TABLE'
        AND table_name NOT IN ('tenant_metadata', 'flyway_schema_history')
    LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS audit_trigger ON %I', tbl.table_name);
        EXECUTE format('CREATE TRIGGER audit_trigger AFTER INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION audit_trigger_function()', tbl.table_name);
    END LOOP;
END $$;