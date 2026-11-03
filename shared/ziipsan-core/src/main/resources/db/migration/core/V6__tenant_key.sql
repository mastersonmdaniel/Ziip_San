-- P0.28: registro das chaves por tenant (DEK por tenant, envelope com KEK).
--
-- Uma linha por (tenant, versao). A versao ATIVA e a corrente para cifrar dados novos;
-- versoes ROTATED sao mantidas para que um job futuro de re-cifra consiga decifrar os
-- campos antigos (a rotacao, sozinha, NAO re-cifra: ver LocalVaultTenantKeyProvider).
-- Na destruicao (crypto-shredding) todas as linhas do tenant passam a DESTROYED e o
-- material cifrado e apagado (wrapped_* = NULL): sem ele, nenhum campo do tenant decifra.
--
-- Sem RLS, deliberadamente, como public.tenant_catalog (ver V1): a tabela so e acessada
-- pelo provedor de chaves (LocalVaultTenantKeyProvider / JdbcTenantKeyStore), nunca
-- diretamente por uma query de negocio, e a role da aplicacao so deve receber
-- SELECT/INSERT/UPDATE nela pelo job de migracao — o GRANT nao vai nesta migration,
-- pelo mesmo motivo de V1 (a migration nao pode presumir roles que nem todo banco tem).
-- A KEK nunca vai para esta tabela: ela fica no cofre local (SecretStore); aqui so
-- ficam DEKs ja cifradas pela KEK (envelope).
CREATE TABLE IF NOT EXISTS public.tenant_key (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    key_version INTEGER NOT NULL CHECK (key_version >= 1),
    wrapped_encryption_key BYTEA,
    wrapped_blind_index_key BYTEA,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'ROTATED', 'DESTROYED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    rotated_at TIMESTAMPTZ,
    destroyed_at TIMESTAMPTZ,
    CONSTRAINT tenant_key_version_unique UNIQUE (tenant_id, key_version),
    CONSTRAINT tenant_key_active_material CHECK (
        (status = 'DESTROYED' AND wrapped_encryption_key IS NULL AND wrapped_blind_index_key IS NULL)
        OR (status IN ('ACTIVE', 'ROTATED')
            AND wrapped_encryption_key IS NOT NULL AND wrapped_blind_index_key IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_tenant_key_tenant_status
    ON public.tenant_key (tenant_id, status);
