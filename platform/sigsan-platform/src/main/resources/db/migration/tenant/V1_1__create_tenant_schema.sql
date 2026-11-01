-- Tenant Schema Template Migration
-- Version: 1.1.0
-- Description: Core tables created in each tenant schema
-- This migration is run against each tenant schema individually
--
-- P0.25 (limpeza): esta migration continha tabelas de um modelo antigo que voltam
-- em fases posteriores, nao aqui:
--   * tenant_metadata, users, roles, user_roles — removidas; usuarios e papeis
--     renascem na P0.13 (catalogo de usuarios), nao como seed de provisionamento;
--   * documents — removido; volta com o modelo de documentos da fase de cadastro;
--   * service_orders — removido; o modelo de OS renasce na P1.31 (V9_1).
-- Ficam apenas consumer_units (raiz do cadastro, P1) e addresses, com RLS forcada.
-- consumer_units.owner_id/responsible_id referenciavam users e sairam junto:
-- voltam quando users existir (P0.13), nao como FK para tabela inexistente.

-- ========================================================================
-- CONSUMER UNITS (UNIDADES CONSUMIDORAS)
-- ========================================================================

CREATE TABLE IF NOT EXISTS consumer_units (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    matricula VARCHAR(50) NOT NULL,
    dv CHAR(1),
    -- P0.25: owner_id/responsible_id voltam com users na P0.13; FK para tabela
    -- inexistente quebraria a migration.
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
-- ENABLE ROW LEVEL SECURITY ON ALL TABLES
-- ========================================================================
--
-- FORCE e obrigatorio aqui, nao redundante. O dono das tabelas e sigsan_migrator (o schema e
-- criado com AUTHORIZATION sigsan_migrator) e o dono ignora RLS a menos que FORCE esteja
-- ligado. Como o migratorDataSource esta na mesma JVM que a aplicacao, qualquer JdbcTemplate
-- futuro construído sobre ele leria qualquer schema de tenant sem restricao. FORCE fecha essa
-- porta com custo zero.

ALTER TABLE consumer_units ENABLE ROW LEVEL SECURITY;
ALTER TABLE consumer_units FORCE ROW LEVEL SECURITY;
ALTER TABLE addresses ENABLE ROW LEVEL SECURITY;
ALTER TABLE addresses FORCE ROW LEVEL SECURITY;

-- ========================================================================
-- RLS POLICIES
-- ========================================================================

-- consumer_units
CREATE POLICY consumer_units_isolation ON consumer_units
    USING (tenant_id = current_tenant_id());

-- addresses
CREATE POLICY addresses_isolation ON addresses
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
        AND table_name NOT IN ('flyway_schema_history')
    LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS audit_trigger ON %I', tbl.table_name);
        EXECUTE format('CREATE TRIGGER audit_trigger AFTER INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION audit_trigger_function()', tbl.table_name);
    END LOOP;
END $$;