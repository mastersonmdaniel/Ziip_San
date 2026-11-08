-- Core Migration — Version: 12
-- Description: ED-55 / GIS-10 — platform.gis_db_role: mapeamento da role
-- de login do QGIS para tenant e usuario (ADR-0034, decisao 7;
-- GIS-CADASTRO-TECNICO §7.1 item 1; plano v3, tabela de versoes
-- reservadas: core V12, reservada para a ED-55 na onda B).
--
-- O QUE ESTA MIGRATION FAZ:
--   * platform.gis_db_role: uma linha por role de login do QGIS. A role
--     e INDIVIDUAL (um usuario, um tenant), sem privilegio, sem
--     BYPASSRLS e nunca dona de tabela — essas propriedades sao da role
--     do PostgreSQL, criada pela rotina privilegiada de implantacao;
--     aqui fica o MAPEAMENTO (role -> tenant/usuario), o estado e a
--     validade, que sao o que as views qgis_* (migration tenant em
--     db/pendente da ED-55) consultam para resolver o tenant PELA
--     IDENTIDADE DA ROLE (session_user), nunca por GUC de sessao.
--   * platform.gis_db_role_audit: trilha das decisoes sobre a role
--     (pedido, aprovacao, criacao, revogacao) — quem pediu e quem
--     aprovou sao pessoas diferentes (CHECK), como em toda revisao do
--     projeto.
--
-- QUEM GRAVA (texto do acceptance da ED-55/GIS-10 e §7.1 item 1:
-- "gravada SO pela rotina privilegiada de implantacao"): a tabela
-- platform.gis_db_role e gravada SOMENTE pela rotina privilegiada de
-- implantacao — criacao da linha, aprovacao, ativacao, suspensao,
-- revogacao e a criacao da role de banco. A api NAO grava nesta
-- tabela em nenhuma hipotese (nem o pedido). O PEDIDO do
-- administrador do tenant nasce em estrutura PROPRIA,
-- platform.gis_db_role_request (abaixo), que a api grava com INSERT
-- estreito; a rotina le os pedidos PENDING, aplica o quatro-olhos e
-- MATERIALIZA o pedido aprovado criando a linha em gis_db_role
-- (status REQUESTED -> transicoes) e a role de banco. A api recebe
-- SELECT em gis_db_role (exibir o estado) e INSERT/SELECT em
-- gis_db_role_request — GRANTs aplicados pela rotina de implantacao,
-- passo declarado da integracao. (Correcao D6 da revisao cruzada: a
-- versao anterior alinhava este comentario ao codigo que inseria o
-- pedido na propria gis_db_role; o alinhamento correto e ao
-- acceptance — pedido e role sao estruturas separadas.)
-- Nao ha GRANT para roles de tenant nem para as roles qgis_* nesta
-- tabela — a resolucao do tenant dentro do banco e feita pela funcao
-- SECURITY DEFINER platform.gis_role_tenant(), que devolve SOMENTE o
-- tenant da propria role chamadora.
--
-- QUEM LE A FUNCAO: a funcao nao recebe parametro de role: ela usa
-- session_user internamente. Assim, uma role nao consegue consultar o
-- mapeamento de outra role nem de outro tenant.
--
-- REVOGACAO: status = 'REVOKED' (ou validade vencida) faz
-- gis_role_tenant() devolver NULL e as views qgis_* devolverem zero
-- linhas; o encerramento da conexao em si e ato da rotina de
-- implantacao (ALTER ROLE ... NOLOGIN / pg_terminate_backend), fora
-- desta migration — ver docs/gis/ED-55-qgis-acesso.md.

CREATE SCHEMA IF NOT EXISTS platform;

CREATE TABLE IF NOT EXISTS platform.gis_db_role (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    -- Nome da role de login no PostgreSQL. Um nome, uma role, um
    -- usuario: UNIQUE garante a individualidade (§7.1 item 1).
    role_name TEXT NOT NULL,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    status TEXT NOT NULL DEFAULT 'REQUESTED' CHECK (status IN
        ('REQUESTED', 'APPROVED', 'ACTIVE', 'SUSPENDED', 'REVOKED')),
    -- Validade da senha/credencial (§7.1 item 1: validade propria ou
    -- credencial de curta duracao). NULL so enquanto REQUESTED/APPROVED.
    valid_until TIMESTAMPTZ,
    -- Limites proprios da role (§7.1 item 1), registrados para a rotina
    -- de implantacao aplica-los no CREATE/ALTER ROLE.
    connection_limit INT NOT NULL DEFAULT 2 CHECK (connection_limit > 0),
    statement_timeout_ms INT NOT NULL DEFAULT 30000
        CHECK (statement_timeout_ms > 0),
    requested_by UUID NOT NULL,
    approved_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_gis_db_role_name UNIQUE (role_name),
    CONSTRAINT uq_gis_db_role_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_gis_db_role_approval
        CHECK (status NOT IN ('APPROVED', 'ACTIVE', 'SUSPENDED')
               OR approved_by IS NOT NULL),
    CONSTRAINT ck_gis_db_role_four_eyes
        CHECK (approved_by IS NULL OR approved_by <> requested_by),
    CONSTRAINT ck_gis_db_role_active_validity
        CHECK (status <> 'ACTIVE' OR valid_until IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_gis_db_role_tenant_user
    ON platform.gis_db_role (tenant_id, user_id, status);

-- Pedido de role QGIS (estrutura PROPRIA do pedido, correcao D6):
-- quem grava aqui e a api (INSERT estreito do pedido, em nome do
-- tenant da sessao); quem DECIDE e materializa e a rotina
-- privilegiada — ela le os PENDING, confere o quatro-olhos
-- (decided_by <> requested_by) e, ao aprovar, cria a linha em
-- gis_db_role e a role de banco, ligando materialized_role_id.
-- Um so pedido PENDING por (tenant, usuario): o indice unico parcial
-- e a autoridade; pedido repetido vira conflito na api.
CREATE TABLE IF NOT EXISTS platform.gis_db_role_request (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    -- Nome que a rotina usara na role de banco (derivado por
    -- GisQgisRules.roleNameFor na api; a rotina confere o padrao).
    role_name TEXT NOT NULL,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    -- Validade pedida; a validade efetiva e a da linha materializada.
    valid_until TIMESTAMPTZ,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN
        ('PENDING', 'MATERIALIZED', 'REJECTED')),
    requested_by UUID NOT NULL,
    decided_by UUID,
    decided_at TIMESTAMPTZ,
    materialized_role_id UUID REFERENCES platform.gis_db_role (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_gis_db_role_request_four_eyes
        CHECK (decided_by IS NULL OR decided_by <> requested_by),
    CONSTRAINT ck_gis_db_role_request_decision
        CHECK (status = 'PENDING'
               OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
    CONSTRAINT ck_gis_db_role_request_materialized
        CHECK (status <> 'MATERIALIZED' OR materialized_role_id IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_gis_db_role_request_pending
    ON platform.gis_db_role_request (tenant_id, user_id)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_gis_db_role_request_tenant
    ON platform.gis_db_role_request (tenant_id, status, created_at);

CREATE TABLE IF NOT EXISTS platform.gis_db_role_audit (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    gis_db_role_id UUID NOT NULL
        REFERENCES platform.gis_db_role (id),
    action TEXT NOT NULL CHECK (action IN
        ('REQUESTED', 'APPROVED', 'ACTIVATED', 'SUSPENDED',
         'REVOKED', 'EXPIRED')),
    actor_id UUID,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_gis_db_role_audit_role
    ON platform.gis_db_role_audit (gis_db_role_id, created_at);

-- Resolucao do tenant pela identidade da role chamadora. SECURITY
-- DEFINER com search_path fixo: a role qgis nao tem SELECT na tabela;
-- ela so descobre o PROPRIO tenant, e somente com mapeamento ACTIVE
-- dentro da validade. Qualquer outro caso devolve NULL (as views
-- qgis_* comparam tenant_id = gis_role_tenant() e devolvem nada).
CREATE OR REPLACE FUNCTION platform.gis_role_tenant()
RETURNS UUID
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = platform, pg_temp
AS $$
    SELECT r.tenant_id
      FROM platform.gis_db_role r
     WHERE r.role_name = session_user
       AND r.status = 'ACTIVE'
       AND r.valid_until > NOW()
$$;

CREATE OR REPLACE FUNCTION platform.gis_role_user()
RETURNS UUID
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = platform, pg_temp
AS $$
    SELECT r.user_id
      FROM platform.gis_db_role r
     WHERE r.role_name = session_user
       AND r.status = 'ACTIVE'
       AND r.valid_until > NOW()
$$;

REVOKE ALL ON FUNCTION platform.gis_role_tenant() FROM PUBLIC;
REVOKE ALL ON FUNCTION platform.gis_role_user() FROM PUBLIC;
-- O GRANT EXECUTE para cada role qgis_* e dado pela rotina de
-- implantacao na criacao da role (a migration nao conhece os nomes).

-- Nenhum GRANT e concedido nesta migration: para a api, SELECT em
-- gis_db_role e INSERT/SELECT em gis_db_role_request (NENHUM INSERT
-- em gis_db_role); para as roles qgis_*, os GRANTs de EXECUTE/views —
-- tudo aplicado pela rotina de implantacao, que conhece os nomes
-- reais de cada ambiente.
