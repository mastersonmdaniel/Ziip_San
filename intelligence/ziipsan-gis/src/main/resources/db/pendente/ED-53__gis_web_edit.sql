-- GIS-09 (ED-53) — edicao web completa e cadastro de unidades.
--
-- NUMERACAO: nenhum numero de Flyway especifico esta reservado a
-- ED-53 no plano (so a familia generica V13_x). Evidencia: listagem
-- MCP de intelligence/ziipsan-gis/.../db/migration/tenant na main
-- (HEAD 2f77582): serie ate V13_12, com V13_13 ja ocupado pelo
-- pacote local da P3.27. Seguindo o precedente ED-55/ED-67, este
-- SQL fica em db/pendente SEM numero inventado; a integracao o
-- promove a proximo V13_x livre no momento do pouso.
--
-- DESENHO:
-- (1) gis_layer_permission (SG-864/SG-865): permissao por camada
--     E por operacao, por usuario do tenant. Sem linha = sem
--     permissao (a leitura do mapa segue a GIS-06).
-- (2) gis_user_layer / gis_user_feature (SG-840): camada do
--     usuario, isolada por tenant e dono; nunca referencia a rede
--     em operacao.
-- (3) gis_table_join (SG-841): definicao de juncao entre tabelas
--     da lista fechada (a guarda de lista vive tambem em
--     GisWebEditRules; o CHECK abaixo espelha tabelas e chaves).
--     Correcao D5: a definicao e EXECUTAVEL pelo servico
--     (executeJoin monta a consulta filtrada por tenant_id).
-- (4) gis_project (SG-858, TR-1050, TR-1051): projeto/obra com os
--     DOIS pacotes proprios (PROJECT e AS_BUILT, GIS-04/V13_9).
-- (5) gis_drawn_connection_sync (SG-866, TR-1053): estado da
--     sincronizacao ramal desenhado (gis_connection) <-> ligacao
--     do cadastro (service_connections, ED-13/P2.23), idempotente
--     pela feicao GIS.
-- (6) Funcoes PostGIS de apoio (split/merge pela projecao) sao
--     DELIBERADAMENTE nao criadas aqui: a matematica pura vive em
--     GisGeom (provada por teste unitario sem ambiente); a
--     segmentacao persistida passa pelos itens do pacote da
--     GIS-04, cuja aplicacao ja e transacional. Criar funcao de
--     split no banco sem ensaio em PostGIS real seria codigo nao
--     provado — declarado na pendencia, nao entregue como pronto.

CREATE TABLE IF NOT EXISTS gis_layer_permission (
    tenant_id UUID NOT NULL,
    layer_id UUID NOT NULL,
    layer_key TEXT NOT NULL,
    user_id UUID NOT NULL,
    operation TEXT NOT NULL CHECK (operation IN
        ('VIEW', 'CREATE', 'EDIT_GEOMETRY', 'EDIT_ATTRIBUTES',
         'SPLIT', 'MERGE', 'INSERT_NODE', 'DELETE', 'MANAGE_LAYER')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    PRIMARY KEY (tenant_id, layer_id, user_id, operation)
);
SELECT register_tenant_table('gis_layer_permission');

CREATE TABLE IF NOT EXISTS gis_user_layer (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    name TEXT NOT NULL,
    geometry_type TEXT NOT NULL CHECK (geometry_type IN
        ('POINT', 'LINESTRING', 'POLYGON')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_user_layer_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('gis_user_layer');

CREATE TABLE IF NOT EXISTS gis_user_feature (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    layer_id UUID NOT NULL,
    geom geometry(Geometry, 4674),
    attributes JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_user_feature_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_gis_user_feature_layer
        FOREIGN KEY (tenant_id, layer_id)
        REFERENCES gis_user_layer (tenant_id, id)
);
SELECT register_tenant_table('gis_user_feature');
CREATE INDEX IF NOT EXISTS idx_gis_user_feature_geom
    ON gis_user_feature USING GIST (geom);

CREATE TABLE IF NOT EXISTS gis_table_join (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    -- Correcao D5 (revisao ED-53): a juncao pertence a uma camada
    -- (a permissao VIEW/MANAGE_LAYER da SG-864/SG-865 e conferida
    -- contra ela na criacao e na EXECUCAO da juncao).
    layer_id UUID,
    left_table TEXT NOT NULL CHECK (left_table IN
        ('gis_node', 'gis_arc', 'gis_connection', 'gis_structure',
         'gis_zone', 'consumer_units')),
    right_table TEXT NOT NULL CHECK (right_table IN
        ('gis_node', 'gis_arc', 'gis_connection', 'gis_structure',
         'gis_zone', 'consumer_units')),
    join_type TEXT NOT NULL CHECK (join_type IN ('INNER', 'LEFT')),
    join_key TEXT NOT NULL CHECK (join_key IN
        ('feature_id', 'service_connection_id', 'consumer_unit_id',
         'code', 'dma_id')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_table_join_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('gis_table_join');

CREATE TABLE IF NOT EXISTS gis_project (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    work_ref TEXT,
    status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN
        ('DRAFT', 'IN_PROGRESS', 'BUILT', 'CLOSED', 'CANCELLED')),
    project_change_set_id UUID,
    as_built_change_set_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_project_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_gis_project_set
        FOREIGN KEY (tenant_id, project_change_set_id)
        REFERENCES gis_change_set (tenant_id, id),
    CONSTRAINT fk_gis_project_asbuilt
        FOREIGN KEY (tenant_id, as_built_change_set_id)
        REFERENCES gis_change_set (tenant_id, id)
);
SELECT register_tenant_table('gis_project');

CREATE TABLE IF NOT EXISTS gis_drawn_connection_sync (
    tenant_id UUID NOT NULL,
    gis_connection_id UUID NOT NULL,
    service_connection_id UUID,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN
        ('PENDING', 'SYNCED', 'CONFLICT', 'RETIRED')),
    idempotency_key TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, gis_connection_id),
    CONSTRAINT uq_gis_drawn_sync_key UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT fk_gis_drawn_sync_connection
        FOREIGN KEY (tenant_id, gis_connection_id)
        REFERENCES gis_connection (tenant_id, id)
);
SELECT register_tenant_table('gis_drawn_connection_sync');
