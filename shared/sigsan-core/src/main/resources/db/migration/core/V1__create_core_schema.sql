-- Baseline migration for SIGSAN Core
-- Version: 1.0.0
-- Description: Create core tables and tenant schema structure

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS "btree_gin";
CREATE EXTENSION IF NOT EXISTS "btree_gist";

-- Create schema for shared/core tables (public schema)
-- Tenant-specific tables will be created in tenant schemas

-- ========================================================================
-- TENANT CATALOG (in public schema - shared across all tenants)
-- ========================================================================

CREATE TABLE IF NOT EXISTS public.tenant_catalog (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    schema_name VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    cnpj VARCHAR(18) UNIQUE,
    email VARCHAR(255),
    phone VARCHAR(20),
    address JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    plan VARCHAR(50),
    settings JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE INDEX idx_tenant_catalog_status ON public.tenant_catalog(status);
CREATE INDEX idx_tenant_catalog_cnpj ON public.tenant_catalog(cnpj);

-- ========================================================================
-- AUDIT LOG TABLE (in public schema - shared, partitioned by tenant)
-- ========================================================================

CREATE TABLE IF NOT EXISTS public.audit_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(20) NOT NULL,
    user_id UUID,
    correlation_id UUID,
    ip_address VARCHAR(45), -- IPv4/IPv6 em texto; AuditLog mapeia String (INET exigiria tipo proprio)
    user_agent TEXT,
    request_id VARCHAR(100),
    old_values JSONB,
    new_values JSONB,
    changed_fields JSONB,
    metadata JSONB DEFAULT '{}',
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- AuditLog estende BaseEntity, que declara id, createdAt, updatedAt, version, createdBy e
    -- updatedBy. Se alguma destas colunas faltar, o JPA falha em runtime ao ler ou gravar,
    -- com "column X of relation audit_log does not exist".
    updated_at TIMESTAMPTZ,
    version BIGINT,
    created_by UUID,
    updated_by UUID
);

-- Partition by tenant_id for performance and isolation
-- Note: Actual partitioning setup requires pg_partman or manual partition creation

CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_entity ON public.audit_log (tenant_id, entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_user_time ON public.audit_log (tenant_id, user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_time ON public.audit_log (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_correlation ON public.audit_log (tenant_id, correlation_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON public.audit_log (created_at DESC);

-- ========================================================================
-- ROW LEVEL SECURITY NAS TABELAS COMPARTILHADAS
-- ========================================================================
--
-- Estas tabelas vivem em public, o que significa que as policies de tenant por schema nao se
-- aplicam a elas. Sem RLS aqui, um SELECT em public.audit_log retorna o historico de TODOS os
-- tenants, incluindo os snapshots em JSONB de cada linha alterada.
--
-- FORCE e necessario: o dono das tabelas (sigsan_migrator) ignoraria RLS sem ele, e essa role
-- esta na mesma JVM que a aplicacao.

ALTER TABLE public.audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_log FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS audit_log_isolation ON public.audit_log;
CREATE POLICY audit_log_isolation ON public.audit_log
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- tenant_catalog NAO recebe RLS. E o cadastro global da plataforma. Uma policy por
-- id = current_tenant_id() quebraria o proprio provisionamento (o INSERT do tenant passaria
-- a depender de um contexto que so existe depois do cadastro) e impediria a operacao
-- legitima de listar tenants. O controle de acesso a lista global e problema de autorizacao
-- de aplicacao, resolvido na camada de API.

-- ========================================================================
-- TENANT SETTINGS (shared configuration)
-- ========================================================================

CREATE TABLE IF NOT EXISTS public.tenant_settings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    setting_key VARCHAR(100) NOT NULL,
    setting_value JSONB NOT NULL,
    description TEXT,
    is_sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

-- Uma configuracao por (tenant, chave). Antes, tenant_id UNIQUE permitia uma unica linha por tenant, o
-- que inutilizava o desenho chave/valor: so a primeira configuracao era aceita.
CREATE UNIQUE INDEX idx_tenant_settings_tenant_key ON public.tenant_settings(tenant_id, setting_key);

-- Configuracao por tenant tambem e dado sensivel: pode conter chave de integracao,
-- endpoint e segredo. Mesma protecao das demais.
ALTER TABLE public.tenant_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tenant_settings FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_settings_isolation ON public.tenant_settings;
CREATE POLICY tenant_settings_isolation ON public.tenant_settings
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- ========================================================================
-- FUNCOES DE CONTEXTO DE TENANT
-- ========================================================================
--
-- current_tenant_id() NAO e definida aqui: ela vem de infra/postgres/init/01-security-roles.sql,
-- que roda como dono do cluster antes de qualquer migration, e as policies RLS desta migration
-- dependem dela. Uma segunda definicao aqui falha (must be owner of function) porque a
-- sigsan_migrator nao e dona da funcao.
--
-- Removidas, por serem legado sem uso e perigosas:
--   set_current_tenant()     gravava a GUC no escopo da SESSAO (is_local = false); com pool de
--                            conexoes o tenant vazaria para a proxima requisicao. O contexto
--                            deve ser sempre local a transacao (set_config(..., true)).
--   create_tenant_schema()   criava o schema com AUTHORIZATION sigsan_app; dono de tabela ignora
--                            RLS. O provisionamento correto esta em TenantProvisioningService.
--   run_tenant_migration()   era um no-op que so emitia NOTICE.

-- ========================================================================
-- ROW LEVEL SECURITY POLICIES (template - applied per tenant schema)
-- ========================================================================

-- Enable RLS on a table (to be run in each tenant schema)
-- ALTER TABLE schema_name.table_name ENABLE ROW LEVEL SECURITY;

-- Example policy for tenant isolation (to be created in each tenant schema)
-- CREATE POLICY tenant_isolation ON schema_name.table_name
--     USING (tenant_id = current_tenant_id());

-- ========================================================================
-- COMMENTS
-- ========================================================================

COMMENT ON TABLE public.tenant_catalog IS 'Catalog of all tenants in the system';
COMMENT ON TABLE public.audit_log IS 'Centralized audit log for all tenants (partitioned by tenant_id)';
COMMENT ON TABLE public.tenant_settings IS 'Shared tenant configuration settings';

-- ========================================================================
-- PRIVILEGIOS DA APLICACAO
-- ========================================================================
--
-- ALTER DEFAULT PRIVILEGES (infra/postgres/init) vale apenas para objetos criados pela role que
-- executa o ALTER, nao pela sigsan_migrator que roda esta migration. Sem GRANT explicito, a
-- aplicacao recebe "permission denied" nas tabelas abaixo.
--
-- audit_log e somente-insercao para a aplicacao: uma trilha de auditoria que a propria aplicacao
-- pode editar ou apagar nao e trilha.

GRANT SELECT, INSERT, UPDATE, DELETE ON public.tenant_catalog TO sigsan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.tenant_settings TO sigsan_app;
GRANT SELECT, INSERT ON public.audit_log TO sigsan_app;
