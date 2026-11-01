-- Platform Migration for Tenant Schema
-- Version: 1.9.0
-- Description: P0.13 — tabela de usuarios do tenant e atribuicao de perfis de fabrica.
--
-- A tabela users saiu da V1_1 na P0.25 e renasce aqui, sem password_hash nem mfa_secret:
-- credencial fica so no Keycloak. O vinculo com o Keycloak e o `sub` do token
-- (keycloak_sub): o cadastro no Keycloak e manual e a API propria vincula o registro
-- pelo sub; nao ha keycloak-admin-client (removido na P0.25) e SCIM fica na ED-06.
--
-- status e dominio fechado (DAD-31): ACTIVE, SUSPENDED ou DEACTIVATED, nunca texto livre.
-- UNIQUE (tenant_id, id) existe para as chaves estrangeiras compostas de leiturista,
-- executor e aprovador (DAD-13, P1). UNIQUE (tenant_id, keycloak_sub): um sub do Keycloak
-- corresponde a no maximo um usuario por tenant.
--
-- user_profiles: atribuicao de perfis de fabrica do catalogo P0.13 ao usuario, com
-- vigencia (valid_from / valid_until, fim nulo = vigente). Um usuario pode ter mais de
-- um perfil; a permissao efetiva e a uniao das permissoes dos perfis vigentes. A
-- restricao negativa e os perfis compostos pelo ADMIN ficam na ED-06.
--
-- NOTA sobre consumer_units.owner_id/responsible_id: o comentario da V1_1 dizia que
-- voltariam com users na P0.13, mas a V3_1 ja decidiu o contrario — proprietario e
-- responsavel de um imovel sao CLIENTES, nao usuarios, e o vinculo correto e temporal
-- em unit_customers. Por isso a V1_9 NAO recria essas colunas em consumer_units.

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    keycloak_sub VARCHAR(255) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    email VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DEACTIVATED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE users ADD CONSTRAINT uq_users_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_users_tenant_sub ON users(tenant_id, keycloak_sub);
CREATE INDEX idx_users_tenant_status ON users(tenant_id, status);

CREATE TABLE IF NOT EXISTS user_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    profile VARCHAR(20) NOT NULL
        CHECK (profile IN ('ADMIN', 'MANAGER', 'OPERATOR', 'VIEWER')),
    valid_from TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (tenant_id, user_id) REFERENCES users(tenant_id, id),
    CONSTRAINT chk_user_profiles_validity CHECK (valid_until IS NULL OR valid_until > valid_from)
);
CREATE UNIQUE INDEX idx_user_profiles_tenant_user_profile ON user_profiles(tenant_id, user_id, profile);
CREATE INDEX idx_user_profiles_tenant_user ON user_profiles(tenant_id, user_id);

-- ========================================================================
-- ISOLAMENTO E AUDITORIA (RLS FORCE + trigger, via helper da V1_2)
-- ========================================================================

SELECT register_tenant_table('users');
SELECT register_tenant_table('user_profiles');
