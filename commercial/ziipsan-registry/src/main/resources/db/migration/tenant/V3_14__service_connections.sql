-- GIS-05 (P2.23) — base da ligacao: service_connections, catalogo unico
-- connection_statuses, vinculo ligacao <-> unidades consumidoras e
-- connection_status_history substituindo unit_status_history
-- (GIS-CADASTRO-TECNICO §3.11; ADR-0034; DAD-09; DAD-10; glossario do
-- plano §8, COE-17).
--
-- NUMERACAO: a spec reservava V3_7 para esta migration, mas no repo real
-- V3_7 ja foi consumido (V3_7__catalog_frontend_fields.sql) e a serie
-- V3 do registry vai ate V3_13 (V3_13__vulnerable_proof_redaction.sql).
-- Seguindo o precedente do projeto (slot ocupado -> proxima versao livre
-- da serie, sem editar migration aplicada — o mesmo que levou a P2.21 a
-- V13_8 e a P2.22 a V13_9), esta migration e a V3_14. Evidencia:
-- listagem MCP de commercial/ziipsan-registry/src/main/resources/db/
-- migration/tenant/ na main (HEAD 0fbcd22) em 07/11/2026 — ver
-- RELATORIO-P2.23.md.
--
-- ORDEM NA CADEIA (consequencia do numero): esta migration roda ANTES de
-- V5_1 (meter_installations), de V9_1 (service_orders), de V13_x (GIS) e
-- de V14_1 (staging do legado). Por isso:
--   * connection_status_history.service_order_id nasce NOT NULL mas SEM
--     FK: a FK para service_orders chega na V13_10 (que roda depois da
--     V9_1), guardada por to_regclass — o mesmo desenho que a V9_3 usou
--     para as FKs do cadastro e que a V9_1 usou para a FK da antiga
--     unit_status_history;
--   * a leitura do staging do legado (legacy_import_staging_units,
--     V14_1) para preencher connected_on e GUARDADA por to_regclass:
--     em tenant ja migrado (upgrade) a tabela existe e a carga e lida;
--     em tenant novo o staging ainda nao existe nesta altura da cadeia
--     e as ligacoes nascem das demais fontes (ver connected_on abaixo).
--
-- DESENHO:
-- (1) Imovel fisico (lote) x unidade consumidora (glossario §8): o lote
--     NAO e a unidade. No modelo vigente o lote de um conjunto de
--     unidades e ancorado pela unidade-mae do condominio: a ancora
--     (property_unit_id) e parent_unit_id quando
--     parent_relation_type = 'CONDOMINIUM_MACRO' e a propria unidade
--     nos demais casos. service_connections.property_unit_id aponta
--     para a ancora; service_connection_units liga a ligacao a TODAS as
--     unidades atendidas (varias por ligacao, em condominio).
-- (2) 1:1 enquanto ativa: indice unico parcial sobre
--     (tenant_id, property_unit_id, kind) WHERE ended_on IS NULL —
--     uma ligacao nao encerrada por tipo e por imovel; encerrada
--     (ended_on preenchido), outra pode nascer para o mesmo imovel.
-- (3) Catalogo unico connection_statuses: as linhas de water_statuses
--     (kind WATER) e de sewer_statuses (kind SEWER) sao copiadas
--     PRESERVANDO O ID, para que os valores hoje gravados em
--     consumer_units e no historico continuem validos sem traducao.
--     kind, system_role e days_to_cut sao as colunas novas da spec:
--     system_role e derivado das flags legadas por regra deterministica
--     documentada abaixo (nenhum valor e inventado linha a linha);
--     days_to_cut nasce NULL (o legado nao tem o dado; quem preenche e
--     o tenant no catalogo — a P3.4 consome).
--     Mapeamento de system_role:
--       active = false                       -> INACTIVE
--       connected AND supplies (agua)        -> ACTIVE
--       connected (esgoto)                   -> ACTIVE
--       connected AND NOT supplies (agua)    -> CUT
--       NOT connected AND has_connection     -> DISCONNECTED
--       NOT has_connection                   -> NO_CONNECTION
--     SUSPENDED fica reservado ao uso do tenant no catalogo unico.
-- (4) Situacao mora na ligacao: service_connections.status_id e a
--     situacao vigente. As colunas consumer_units.water_status_id /
--     sewer_status_id NAO sao removidas aqui: sao a projecao que o
--     cadastro e o ciclo da P2.5 leem hoje, e o §3.11 manda o
--     faturamento continuar lendo o imovel ate a ED-13 trocar a
--     leitura para a ligacao. A escrita nova (servico da ligacao e o
--     fluxo de alteracao de situacao do cadastro) grava a ligacao e o
--     connection_status_history; a projecao legada e mantida pelo
--     mesmo fluxo, sem dupla fonte silenciosa (ver o servico).
-- (5) connection_status_history SUBSTITUI unit_status_history: todo o
--     historico existente e migrado (com a proveniencia da unidade em
--     consumer_unit_id) e a contagem e conferida DENTRO da migration —
--     divergencia aborta a migration (DAD-09). So depois da conferencia
--     a tabela antiga e removida. Historico de unidade cujo imovel/
--     tipo nao tem ligacao pelos status atuais tambem migra: a
--     ligacao correspondente e criada a partir da ULTIMA situacao do
--     historico do grupo (anchor, tipo), para nenhuma linha ficar orfa.
-- (6) connected_on (DAD-10; NR 11, art. 22) e NOT NULL na ligacao, com
--     a fonte gravada em connected_on_source (proveniencia, nunca
--     silenciosa). Cadeia de preenchimento da carga inicial:
--       LEGACY_STAGING    — legacy_import_staging_units.connected_on
--                           da unidade ancora ja aplicada (status
--                           APPLIED, applied_unit_id = ancora), quando
--                           o staging existe nesta altura da cadeia;
--       STATUS_HISTORY    — data da primeira mudanca de situacao do
--                           grupo (unidade/tipo) no historico migrado;
--       UNIT_REGISTRATION — data de criacao da unidade ancora
--                           (created_at), o fato registrado mais
--                           antigo disponivel.
--     Ligacao criada pela api nasce com connected_on informado
--     (obrigatorio no servico) e fonte SERVICE_ORDER ou MANUAL.

SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- ======================================================================
-- (1) Catalogo unico de situacoes da ligacao
-- ======================================================================

CREATE TABLE IF NOT EXISTS connection_statuses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    tenant_schema VARCHAR(100),
    kind VARCHAR(5) NOT NULL CHECK (kind IN ('WATER', 'SEWER')),
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    abbreviation VARCHAR(5),
    system_role VARCHAR(20) NOT NULL CHECK (system_role IN
        ('ACTIVE', 'CUT', 'SUSPENDED', 'DISCONNECTED', 'NO_CONNECTION', 'INACTIVE')),
    days_to_cut INT CHECK (days_to_cut IS NULL OR days_to_cut >= 0),
    has_network BOOLEAN NOT NULL DEFAULT FALSE,
    has_connection BOOLEAN NOT NULL DEFAULT FALSE,
    bills BOOLEAN NOT NULL DEFAULT FALSE,
    requires_network BOOLEAN NOT NULL DEFAULT FALSE,
    connected BOOLEAN NOT NULL DEFAULT FALSE,
    supplies BOOLEAN NOT NULL DEFAULT FALSE,
    minimum_consumption BOOLEAN NOT NULL DEFAULT FALSE,
    minimum_billing_consumption INT,
    minimum_billing_volume NUMERIC(10, 2),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_connection_statuses_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_connection_statuses_tenant_kind_code UNIQUE (tenant_id, kind, code)
);

SELECT register_tenant_table('connection_statuses');

-- Copia dos catalogos separados, preservando o id (ver cabecalho (3)).
INSERT INTO connection_statuses (
    id, tenant_id, tenant_schema, kind, code, name, abbreviation, system_role,
    days_to_cut, has_network, has_connection, bills, requires_network,
    connected, supplies, minimum_consumption, minimum_billing_consumption,
    minimum_billing_volume, active, created_at, updated_at, created_by,
    updated_by, version)
SELECT w.id, w.tenant_id, w.tenant_schema, 'WATER', w.code, w.name,
       w.abbreviation,
       CASE
           WHEN NOT w.active THEN 'INACTIVE'
           WHEN w.connected AND w.supplies THEN 'ACTIVE'
           WHEN w.connected THEN 'CUT'
           WHEN w.has_connection THEN 'DISCONNECTED'
           ELSE 'NO_CONNECTION'
       END,
       NULL, w.has_network, w.has_connection, w.bills, w.requires_network,
       w.connected, w.supplies, w.minimum_consumption,
       w.minimum_billing_consumption, NULL, w.active,
       w.created_at, w.updated_at, w.created_by, w.updated_by,
       COALESCE(w.version, 0)
FROM water_statuses w
ON CONFLICT (tenant_id, id) DO NOTHING;

INSERT INTO connection_statuses (
    id, tenant_id, tenant_schema, kind, code, name, abbreviation, system_role,
    days_to_cut, has_network, has_connection, bills, requires_network,
    connected, supplies, minimum_consumption, minimum_billing_consumption,
    minimum_billing_volume, active, created_at, updated_at, created_by,
    updated_by, version)
SELECT s.id, s.tenant_id, s.tenant_schema, 'SEWER', s.code, s.name,
       s.abbreviation,
       CASE
           WHEN NOT s.active THEN 'INACTIVE'
           WHEN s.connected THEN 'ACTIVE'
           WHEN s.has_connection THEN 'DISCONNECTED'
           ELSE 'NO_CONNECTION'
       END,
       NULL, s.has_network, s.has_connection, s.bills, s.requires_network,
       s.connected, FALSE, s.minimum_consumption, NULL,
       s.minimum_billing_volume, s.active,
       s.created_at, s.updated_at, s.created_by, s.updated_by,
       COALESCE(s.version, 0)
FROM sewer_statuses s
ON CONFLICT (tenant_id, id) DO NOTHING;

-- ======================================================================
-- (2) Ligacao e vinculo com as unidades atendidas
-- ======================================================================

CREATE TABLE IF NOT EXISTS service_connections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    tenant_schema VARCHAR(100),
    property_unit_id UUID NOT NULL,
    kind VARCHAR(5) NOT NULL CHECK (kind IN ('WATER', 'SEWER')),
    code VARCHAR(30),
    status_id UUID NOT NULL,
    connected_on DATE NOT NULL,
    connected_on_source VARCHAR(20) NOT NULL CHECK (connected_on_source IN
        ('LEGACY_STAGING', 'STATUS_HISTORY', 'UNIT_REGISTRATION',
         'SERVICE_ORDER', 'MANUAL')),
    disconnected_on DATE,
    ended_on DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_service_connections_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_service_connection_property
        FOREIGN KEY (tenant_id, property_unit_id)
        REFERENCES consumer_units (tenant_id, id),
    CONSTRAINT fk_service_connection_status
        FOREIGN KEY (tenant_id, status_id)
        REFERENCES connection_statuses (tenant_id, id),
    CONSTRAINT ck_service_connection_dates
        CHECK ((disconnected_on IS NULL OR disconnected_on >= connected_on)
               AND (ended_on IS NULL OR ended_on >= connected_on))
);

SELECT register_tenant_table('service_connections');

-- 1:1 com o imovel fisico (lote) por tipo, enquanto ativa (cabecalho (2)).
CREATE UNIQUE INDEX IF NOT EXISTS uq_service_connections_active_property
    ON service_connections (tenant_id, property_unit_id, kind)
    WHERE ended_on IS NULL;

CREATE INDEX IF NOT EXISTS idx_service_connections_property
    ON service_connections (tenant_id, property_unit_id, kind);
CREATE INDEX IF NOT EXISTS idx_service_connections_status
    ON service_connections (tenant_id, status_id);

CREATE TABLE IF NOT EXISTS service_connection_units (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    tenant_schema VARCHAR(100),
    connection_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_service_connection_units_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_service_connection_units_pair UNIQUE (tenant_id, connection_id, unit_id),
    CONSTRAINT fk_service_connection_units_connection
        FOREIGN KEY (tenant_id, connection_id)
        REFERENCES service_connections (tenant_id, id),
    CONSTRAINT fk_service_connection_units_unit
        FOREIGN KEY (tenant_id, unit_id)
        REFERENCES consumer_units (tenant_id, id)
);

SELECT register_tenant_table('service_connection_units');

CREATE INDEX IF NOT EXISTS idx_service_connection_units_unit
    ON service_connection_units (tenant_id, unit_id);

-- ======================================================================
-- (3) Historico da situacao da ligacao (substitui unit_status_history)
-- ======================================================================

CREATE TABLE IF NOT EXISTS connection_status_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    tenant_schema VARCHAR(100),
    connection_id UUID NOT NULL,
    consumer_unit_id UUID,
    kind VARCHAR(5) NOT NULL CHECK (kind IN ('WATER', 'SEWER')),
    from_status_id UUID,
    to_status_id UUID NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(200),
    service_order_id UUID NOT NULL,
    parameter_version_ids JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_connection_status_history_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_connection_history_connection
        FOREIGN KEY (tenant_id, connection_id)
        REFERENCES service_connections (tenant_id, id),
    CONSTRAINT fk_connection_history_unit
        FOREIGN KEY (tenant_id, consumer_unit_id)
        REFERENCES consumer_units (tenant_id, id),
    CONSTRAINT fk_connection_history_from_status
        FOREIGN KEY (tenant_id, from_status_id)
        REFERENCES connection_statuses (tenant_id, id),
    CONSTRAINT fk_connection_history_to_status
        FOREIGN KEY (tenant_id, to_status_id)
        REFERENCES connection_statuses (tenant_id, id),
    CONSTRAINT ck_connection_history_param_versions
        CHECK (parameter_version_ids IS NULL
               OR jsonb_typeof(parameter_version_ids) = 'array')
);

SELECT register_tenant_table('connection_status_history');

CREATE INDEX IF NOT EXISTS idx_connection_history_connection
    ON connection_status_history (tenant_id, connection_id, changed_at DESC);
CREATE INDEX IF NOT EXISTS idx_connection_history_unit
    ON connection_status_history (tenant_id, consumer_unit_id, changed_at DESC);

-- ======================================================================
-- (4) Carga inicial das ligacoes a partir das unidades vigentes
-- ======================================================================
-- Grupos (ancora, tipo): uniao dos grupos com situacao atual e dos
-- grupos que so existem no historico (cabecalho (5)). A situacao da
-- ligacao e a da unidade ancora quando ela tem situacao do tipo; sem
-- ela, a da unidade mais antiga do grupo; sem situacao atual, a ultima
-- situacao do historico do grupo.

-- Tabela temporaria por EXECUTE: CREATE TABLE dentro do corpo
-- PL/pgSQL nao e garantido; o SQL dinamico e o caminho seguro.
DO $$
BEGIN
    IF to_regclass('legacy_import_staging_units') IS NOT NULL THEN
        EXECUTE 'CREATE TEMPORARY TABLE tmp_sc_staging_connected_on '
             || 'ON COMMIT DROP AS '
             || 'SELECT s.applied_unit_id AS unit_id, '
             || 'MIN(s.connected_on) AS connected_on '
             || 'FROM legacy_import_staging_units s '
             || 'WHERE s.status = ''APPLIED'' '
             || 'AND s.applied_unit_id IS NOT NULL '
             || 'AND s.connected_on IS NOT NULL '
             || 'GROUP BY s.applied_unit_id';
    ELSE
        EXECUTE 'CREATE TEMPORARY TABLE tmp_sc_staging_connected_on '
             || 'ON COMMIT DROP AS '
             || 'SELECT NULL::UUID AS unit_id, NULL::DATE AS connected_on '
             || 'WHERE FALSE';
    END IF;
END $$;

WITH anchors AS (
    SELECT u.id AS unit_id,
           u.tenant_id,
           CASE WHEN u.parent_relation_type = 'CONDOMINIUM_MACRO'
                     AND u.parent_unit_id IS NOT NULL
                THEN u.parent_unit_id ELSE u.id END AS property_unit_id
    FROM consumer_units u
),
groups AS (
    SELECT a.tenant_id, a.property_unit_id, k.kind
    FROM anchors a
    CROSS JOIN (VALUES ('WATER'), ('SEWER')) AS k(kind)
    GROUP BY a.tenant_id, a.property_unit_id, k.kind
),
current_status AS (
    SELECT g.tenant_id, g.property_unit_id, g.kind,
           (SELECT CASE WHEN g.kind = 'WATER' THEN u2.water_status_id
                        ELSE u2.sewer_status_id END
            FROM anchors a2
            JOIN consumer_units u2
              ON u2.id = a2.unit_id AND u2.tenant_id = a2.tenant_id
            WHERE a2.tenant_id = g.tenant_id
              AND a2.property_unit_id = g.property_unit_id
              AND (CASE WHEN g.kind = 'WATER' THEN u2.water_status_id
                        ELSE u2.sewer_status_id END) IS NOT NULL
            ORDER BY (a2.unit_id = g.property_unit_id) DESC, u2.created_at, u2.id
            LIMIT 1) AS status_id
    FROM groups g
),
history_status AS (
    SELECT g.tenant_id, g.property_unit_id, g.kind,
           (SELECT h.to_status_id
            FROM unit_status_history h
            JOIN anchors a3
              ON a3.unit_id = h.unit_id AND a3.tenant_id = h.tenant_id
            WHERE a3.tenant_id = g.tenant_id
              AND a3.property_unit_id = g.property_unit_id
              AND h.service_kind::text = g.kind
            ORDER BY h.changed_at DESC, h.created_at DESC, h.id DESC
            LIMIT 1) AS status_id,
           (SELECT MIN(h2.changed_at)
            FROM unit_status_history h2
            JOIN anchors a4
              ON a4.unit_id = h2.unit_id AND a4.tenant_id = h2.tenant_id
            WHERE a4.tenant_id = g.tenant_id
              AND a4.property_unit_id = g.property_unit_id
              AND h2.service_kind::text = g.kind) AS first_change_at
    FROM groups g
),
chosen AS (
    SELECT g.tenant_id, g.property_unit_id, g.kind,
           COALESCE(cs.status_id, hs.status_id) AS status_id,
           hs.first_change_at
    FROM groups g
    LEFT JOIN current_status cs
      ON cs.tenant_id = g.tenant_id
     AND cs.property_unit_id = g.property_unit_id
     AND cs.kind = g.kind
    LEFT JOIN history_status hs
      ON hs.tenant_id = g.tenant_id
     AND hs.property_unit_id = g.property_unit_id
     AND hs.kind = g.kind
    WHERE COALESCE(cs.status_id, hs.status_id) IS NOT NULL
)
INSERT INTO service_connections (
    id, tenant_id, property_unit_id, kind, status_id, connected_on,
    connected_on_source, ended_on, created_at, updated_at)
SELECT uuid_generate_v4(), c.tenant_id, c.property_unit_id, c.kind,
       c.status_id,
       COALESCE(st.connected_on,
                c.first_change_at::date,
                anchor.created_at::date),
       CASE WHEN st.connected_on IS NOT NULL THEN 'LEGACY_STAGING'
            WHEN c.first_change_at IS NOT NULL THEN 'STATUS_HISTORY'
            ELSE 'UNIT_REGISTRATION' END,
       anchor.deleted_at::date,
       NOW(), NOW()
FROM chosen c
JOIN consumer_units anchor
  ON anchor.id = c.property_unit_id AND anchor.tenant_id = c.tenant_id
LEFT JOIN tmp_sc_staging_connected_on st
  ON st.unit_id = c.property_unit_id;

-- Vinculo ligacao <-> unidades atendidas (todas as do grupo, condominio
-- incluido). A CTE de ancoras e repetida porque CTE nao atravessa
-- comandos.
WITH anchors AS (
    SELECT u.id AS unit_id,
           u.tenant_id,
           CASE WHEN u.parent_relation_type = 'CONDOMINIUM_MACRO'
                     AND u.parent_unit_id IS NOT NULL
                THEN u.parent_unit_id ELSE u.id END AS property_unit_id
    FROM consumer_units u
)
INSERT INTO service_connection_units (id, tenant_id, connection_id, unit_id)
SELECT uuid_generate_v4(), sc.tenant_id, sc.id, a.unit_id
FROM service_connections sc
JOIN anchors a
  ON a.tenant_id = sc.tenant_id
 AND a.property_unit_id = sc.property_unit_id
ON CONFLICT (tenant_id, connection_id, unit_id) DO NOTHING;

-- ======================================================================
-- (5) Migracao do historico, com conferencia por contagem (DAD-09)
-- ======================================================================

INSERT INTO connection_status_history (
    id, tenant_id, tenant_schema, connection_id, consumer_unit_id, kind,
    from_status_id, to_status_id, changed_at, reason, service_order_id,
    parameter_version_ids, created_at, updated_at, created_by, updated_by,
    version)
SELECT h.id, h.tenant_id, h.tenant_schema, sc.id, h.unit_id,
       h.service_kind::text, h.from_status_id, h.to_status_id,
       h.changed_at, h.reason, h.service_order_id, NULL,
       h.created_at, h.updated_at, h.created_by, h.updated_by,
       COALESCE(h.version, 0)
FROM unit_status_history h
JOIN service_connection_units scu
  ON scu.tenant_id = h.tenant_id AND scu.unit_id = h.unit_id
JOIN service_connections sc
  ON sc.tenant_id = scu.tenant_id AND sc.id = scu.connection_id
 AND sc.kind = h.service_kind::text;

DO $$
DECLARE
    v_old BIGINT;
    v_new BIGINT;
BEGIN
    SELECT count(*) INTO v_old FROM unit_status_history;
    SELECT count(*) INTO v_new FROM connection_status_history;
    IF v_old <> v_new THEN
        RAISE EXCEPTION 'gis05: migracao do historico divergente: unit_status_history tem % linhas e connection_status_history recebeu %', v_old, v_new;
    END IF;
END $$;

-- Substituicao conferida: a tabela antiga sai (a FK fk_ush_service_order
-- da V9_1, quando ja aplicada em upgrade, e da propria tabela e cai com
-- ela; em instalacao nova a V9_1 roda depois e nao a recria).
DROP TABLE unit_status_history;
