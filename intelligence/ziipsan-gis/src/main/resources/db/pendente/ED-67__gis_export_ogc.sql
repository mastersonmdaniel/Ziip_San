-- ED-67 (GIS-16/GIS-17) — SQL SEM número de Flyway reservado.
-- O plano reserva apenas a família genérica V13_x para ED-53..ED-67
-- (PLANO, linha da V13_x), sem número específico para a ED-67.
-- Pela regra da tarefa, NENHUM número foi inventado: este arquivo
-- fica em db/pendente/ e só vira migration quando a integração
-- atribuir o próximo V13 livre conferido na main.
--
-- Conteúdo: apenas as tabelas PRÓPRIAS da ED-67. As views de
-- publicação pub_* lidas pelo GeoServer (role gis_ro_<tenant>)
-- dependem das colunas finais das tabelas de feição (V13_2/P1.26)
-- e são item de integração — NÃO são criadas aqui com colunas
-- supostas.

CREATE TABLE IF NOT EXISTS gis_export_job (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    requested_by    uuid NOT NULL,
    format          varchar(16) NOT NULL,
    layer_key       varchar(80) NOT NULL,
    profile         varchar(40) NOT NULL DEFAULT 'GENERIC',
    audience        varchar(16) NOT NULL,
    personal        boolean NOT NULL DEFAULT false,
    personal_access_registered boolean NOT NULL DEFAULT false,
    profile_validated boolean NOT NULL DEFAULT true,
    purpose         text,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_gis_export_job_tenant ON gis_export_job (tenant_id, created_at DESC);

-- Credencial OGC emitida ao tenant (§7.2: sem acesso anônimo; a
-- credencial pertence a um usuário ou a uma integração do tenant).
-- O segredo NÃO fica nesta tabela: secret_ref aponta para o cofre.
CREATE TABLE IF NOT EXISTS gis_ogc_credential (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    owner_user_id   uuid,
    integration_name varchar(120),
    third_party     boolean NOT NULL DEFAULT false,
    secret_ref      varchar(200) NOT NULL,
    can_view_personal boolean NOT NULL DEFAULT false,
    revoked_at      timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CHECK (owner_user_id IS NOT NULL OR integration_name IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_gis_ogc_credential_tenant ON gis_ogc_credential (tenant_id) WHERE revoked_at IS NULL;

-- PENDENTE DE INTEGRAÇÃO (declarado): views pub_<camada> de
-- publicação para a role somente-leitura do GeoServer, filtradas
-- pelo tenant da role no schema compartilhado (§7.2 item 2), e a
-- RLS destas tabelas no padrão do módulo.
