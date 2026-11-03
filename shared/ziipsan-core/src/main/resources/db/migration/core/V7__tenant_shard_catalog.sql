-- P0.21 — Shard no catálogo (plano v3.0, seção P0; core V7 — a V4 foi ocupada
-- pela P0.17 na integração, então o conteúdo V4 planejado da P0.21 veio para a V7;
-- esta migration entrega a parte da P0.21).
--
-- O QUE MUDA
--   * tenant_catalog.cluster_id: em qual cluster (banco) o tenant vive. Padrão
--     'default' (o cluster único atual). O roteamento tenant → cluster da P4.6 e o
--     rollout por tenant da P1.14 leem esta coluna; nenhum código a escreve ainda
--     além do provisionamento (TenantProvisioningService), que a recebe no pedido
--     e usa 'default' quando ausente.
--   * tenant_catalog.isolation_mode: SCHEMA (schema próprio, padrão do ADR-0005),
--     SHARED (schema compartilhado por tenant_id e RLS, sob demanda, P4.6) ou
--     DEDICATED (banco dedicado, P4.6 / core V13). O CHECK impede modo inventado.
--   * tenant_catalog.tenant_schema_version: versão Flyway de tenant aplicada por
--     último no schema (NULL = nunca migrado). Gravada pelo job migrate
--     (DatabaseMigrator) após cada tenant; a quarentena e o canário da P1.14
--     usam esta coluna, não uma tabela nova.
--   * REVISÃO DO schema_name UNIQUE (core/V1:21): o UNIQUE simples impedia o mesmo
--     nome de schema em clusters diferentes — impossível num modelo multi-cluster,
--     onde cada cluster tem seu próprio namespace de schemas. Vira
--     UNIQUE (cluster_id, schema_name). O provisionamento passa a usar
--     ON CONFLICT (cluster_id, schema_name) (a forma antiga quebraria aqui com
--     "there is no unique or exclusion constraint matching the ON CONFLICT").
--   * ziipsan_app SÓ COM SELECT no catálogo: a V1 dava SELECT, INSERT, UPDATE e
--     DELETE. A escrita no catálogo é operação privilegiada do job migrate
--     (provisionamento via role de migração); a api só lê (TenantCatalog, filtro
--     JWT). REVOKE aqui, não na V1: migrations existentes nunca são reescritas.
--
-- COMPATIBILIDADE: colunas novas têm DEFAULT, então linhas antigas continuam
-- válidas; nenhum código existente cita as colunas novas. A troca do UNIQUE exige
-- que não existam dois tenants com o mesmo schema_name — em banco sem dado real
-- (o único estado até aqui) isso vale; se a migration falhar por duplicata, é
-- sinal de catálogo inconsistente, não de erro da migration.

ALTER TABLE public.tenant_catalog
    ADD COLUMN IF NOT EXISTS cluster_id TEXT NOT NULL DEFAULT 'default';

ALTER TABLE public.tenant_catalog
    ADD COLUMN IF NOT EXISTS isolation_mode VARCHAR(20) NOT NULL DEFAULT 'SCHEMA'
        CHECK (isolation_mode IN ('SCHEMA', 'SHARED', 'DEDICATED'));

ALTER TABLE public.tenant_catalog
    ADD COLUMN IF NOT EXISTS tenant_schema_version TEXT;

-- Revisão do UNIQUE de schema_name: de global para por cluster.
ALTER TABLE public.tenant_catalog DROP CONSTRAINT IF EXISTS tenant_catalog_schema_name_key;
ALTER TABLE public.tenant_catalog
    ADD CONSTRAINT uq_tenant_catalog_cluster_schema UNIQUE (cluster_id, schema_name);

CREATE INDEX IF NOT EXISTS idx_tenant_catalog_cluster ON public.tenant_catalog (cluster_id);

-- A aplicação só lê o catálogo. A escrita (provisionamento) é do migrador.
REVOKE INSERT, UPDATE, DELETE ON public.tenant_catalog FROM ziipsan_app;
GRANT SELECT ON public.tenant_catalog TO ziipsan_app;

COMMENT ON COLUMN public.tenant_catalog.cluster_id IS
    'P0.21: cluster (banco) onde o tenant vive; default = cluster único atual. Teto: 300 schemas por cluster (ARQUITETURA-ALVO §3).';
COMMENT ON COLUMN public.tenant_catalog.isolation_mode IS
    'P0.21: SCHEMA = schema próprio (padrão, ADR-0005); SHARED = schema compartilhado (P4.6, sob demanda); DEDICATED = banco dedicado (P4.6).';
COMMENT ON COLUMN public.tenant_catalog.tenant_schema_version IS
    'P0.21: versão Flyway de tenant aplicada por último neste tenant; NULL = nunca migrado. Gravada pelo job migrate; base da P1.14.';
