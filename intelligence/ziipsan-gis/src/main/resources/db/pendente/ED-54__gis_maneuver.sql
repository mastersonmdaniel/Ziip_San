-- ============================================================================
-- ED-54 (PENDENTE DE NUMERACAO): tracados, isolamento e manobras — GIS-11
-- (GIS-CADASTRO-TECNICO §5.5/§5.6, linha GIS-11 do §13.2; ADR-0028 (b);
-- ADR-0034; SG-852, SG-887, SG-889; TR-1064, TR-1065; SAMAE 2.27).
--
-- POR QUE PENDENTE: SQL sem numero confirmado livre na serie tenant do
-- GIS (a serie publicada vai alem da V13_12 e os slots seguintes sao
-- disputados pelas trilhas GIS em integracao). Regra da cascata:
-- NENHUM numero inventado — este arquivo entra como db/pendente/ com o
-- prefixo ED-54__ e recebe o numero da sequencia global na integracao
-- (precedentes: ED-53, ED-63, ED-64, ED-65).
--
-- Depende de: V13_1/V13_2 (gis_node, gis_arc, gis_connection),
-- V13_8 (topologia persistida e snap da GIS-03/P2.21),
-- V13_10 (gis_connection.service_connection_id da GIS-05/P2.23),
-- V3_14 (service_connections/service_connection_units da P2.23),
-- V9_1 (service_orders do nucleo de OS da P1.31, consumido pela ED-36).
--
-- O QUE ESTE SQL FAZ:
--   (1) gis_maneuver_sector: setores de manobra PRE-CALCULADOS
--       (particao sem os nos das valvulas operaveis; recalculados pela
--       rotina que consome a area suja da GIS-03 — o publicador da
--       area suja e a PEND-01 herdada da P2.21, NAO resolvida aqui);
--   (2) gis_maneuver_plan + gis_maneuver_plan_valve: plano de manobra
--       com o resultado do isolamento (valvulas a fechar, valvulas
--       inoperaveis ATRAVESSADAS, nos/arcos afetados em JSONB) e o
--       vinculo com os lotes de OS da ED-36 (batch ids) e com o aviso
--       da ED-41 (dispatch reference) — nenhum envio e nenhuma OS
--       nascem neste SQL;
--   (3) gis_maneuver_outage_period: periodo de desabastecimento POR
--       IMOVEL, aberto no isolamento efetivo e fechado na reabertura
--       completa (regra do ManeuverPlanService);
--   (4) funcoes de tracado sobre a topologia persistida:
--       gis_trace_downstream / gis_trace_upstream (CTE recursiva pelo
--       flow_direction da V13_2; UNKNOWN nao gera aresta dirigida) e
--       gis_routing_polygon (ST_ConvexHull dos nos de um conjunto —
--       poligono da AREA de trabalho, SG-889; NAO e rota de equipe:
--       o solver pgRouting de equipes segue bloqueado pelo O8-1/ADR);
--   (5) view gis_maneuver_affected_units: imoveis/economias/unidades
--       afetados de um plano pelas conexoes funcionais (SG-852).
--       A view NAO ordena nem marca sensivel/essencial: a ordem do
--       aviso (sensivel primeiro, essencial depois) e calculada e
--       provada no Java (IsolationEngine.NOTICE_ORDER, gravada em
--       affected_unit_ids do plano) — correcao D3 da revisao, que
--       alinhou este cabecalho a verdade (antes ele prometia na
--       view uma ordenacao que ela nao tem).
--
-- PostGIS real e a conferencia destas funcoes contra os motores Java
-- (ManeuverGraph/IsolationEngine) em massa sintetica sao prova da CI
-- (pendencia nomeada P-ED54-02 no relatorio) — nesta VM nao ha
-- PostgreSQL/PostGIS.
--
-- NENHUM DADO DE NEGOCIO E SEMEADO: setores, planos e periodos sao do
-- tenant e nascem do calculo/execucao. Nenhum parametro novo de
-- tenant e criado: janela e motivo vem do plano (ED-35/manobra) e o
-- aviso e da ED-41.
--
-- CONVENCOES: mensagens sem diacriticos; RLS via register_tenant_table.
-- ============================================================================

SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- (1) Setores de manobra pre-calculados --------------------------------------
CREATE TABLE IF NOT EXISTS gis_maneuver_sector (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    sector_code TEXT NOT NULL,
    node_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    arc_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    boundary_valve_node_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    connection_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    total_economies INTEGER NOT NULL DEFAULT 0,
    has_source BOOLEAN NOT NULL DEFAULT FALSE,
    computed_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_gis_maneuver_sector_tenant_code UNIQUE (tenant_id, sector_code)
);
SELECT register_tenant_table('gis_maneuver_sector');

-- (2) Plano de manobra e suas valvulas ---------------------------------------
CREATE TABLE IF NOT EXISTS gis_maneuver_plan (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    target_arc_id UUID,
    target_node_id UUID,
    isolable BOOLEAN NOT NULL,
    failure TEXT,
    affected_node_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    affected_arc_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    affected_unit_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    total_economies INTEGER NOT NULL DEFAULT 0,
    scheduled_start TIMESTAMP NOT NULL,
    expected_return TIMESTAMP NOT NULL,
    reason TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PLANNED'
        CHECK (status IN ('PLANNED', 'NOTICE_SENT', 'ORDERS_CREATED', 'ISOLATED', 'COMPLETED')),
    notice_dispatch_reference TEXT,
    close_batch_id UUID,
    reopen_batch_id UUID,
    isolation_effective_at TIMESTAMP,
    supply_restored_at TIMESTAMP,
    requested_by UUID NOT NULL,
    CONSTRAINT ck_gis_maneuver_plan_target
        CHECK ((target_arc_id IS NULL) <> (target_node_id IS NULL)),
    -- R3-G1 (auditoria rodada 3): UNIQUE exigida pelas FKs compostas
    -- fk_gis_maneuver_plan_valve_plan e fk_gis_maneuver_outage_plan,
    -- que referenciam gis_maneuver_plan (tenant_id, id). Sem ela a
    -- migration falha ao ser aplicada (mesmo padrao da V13_2 para
    -- gis_arc/gis_node). Nenhum numero Flyway atribuido aqui.
    CONSTRAINT uq_gis_maneuver_plan_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_gis_maneuver_plan_arc
        FOREIGN KEY (tenant_id, target_arc_id) REFERENCES gis_arc (tenant_id, id),
    CONSTRAINT fk_gis_maneuver_plan_node
        FOREIGN KEY (tenant_id, target_node_id) REFERENCES gis_node (tenant_id, id)
);
SELECT register_tenant_table('gis_maneuver_plan');

CREATE TABLE IF NOT EXISTS gis_maneuver_plan_valve (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    valve_node_id UUID NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('TO_CLOSE', 'INOPERABLE_TRAVERSED')),
    close_work_order_id UUID,
    reopen_work_order_id UUID,
    closed_at TIMESTAMP,
    reopened_at TIMESTAMP,
    CONSTRAINT uq_gis_maneuver_plan_valve UNIQUE (tenant_id, plan_id, valve_node_id),
    CONSTRAINT fk_gis_maneuver_plan_valve_plan
        FOREIGN KEY (tenant_id, plan_id) REFERENCES gis_maneuver_plan (tenant_id, id),
    CONSTRAINT fk_gis_maneuver_plan_valve_node
        FOREIGN KEY (tenant_id, valve_node_id) REFERENCES gis_node (tenant_id, id)
);
SELECT register_tenant_table('gis_maneuver_plan_valve');

-- (3) Periodo de desabastecimento por imovel ---------------------------------
CREATE TABLE IF NOT EXISTS gis_maneuver_outage_period (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    property_id UUID NOT NULL,
    consumer_unit_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,
    CONSTRAINT fk_gis_maneuver_outage_plan
        FOREIGN KEY (tenant_id, plan_id) REFERENCES gis_maneuver_plan (tenant_id, id)
);
SELECT register_tenant_table('gis_maneuver_outage_period');

-- (4) Tracados sobre a topologia persistida ----------------------------------
-- Jusante: segue o flow_direction (WITH_GEOMETRY: no1->no2;
-- AGAINST_GEOMETRY: no2->no1; BOTH: os dois; UNKNOWN: nenhum).
CREATE OR REPLACE FUNCTION gis_trace_downstream(p_tenant UUID, p_node UUID)
RETURNS TABLE (node_id UUID) AS $$
    WITH RECURSIVE edges AS (
        SELECT node_1_id AS from_node, node_2_id AS to_node
          FROM gis_arc
         WHERE tenant_id = p_tenant
           AND COALESCE(lifecycle_status, 'IN_SERVICE') = 'IN_SERVICE'
           AND flow_direction IN ('WITH_GEOMETRY', 'BOTH')
        UNION ALL
        SELECT node_2_id, node_1_id
          FROM gis_arc
         WHERE tenant_id = p_tenant
           AND COALESCE(lifecycle_status, 'IN_SERVICE') = 'IN_SERVICE'
           AND flow_direction IN ('AGAINST_GEOMETRY', 'BOTH')
    ), walk AS (
        SELECT p_node AS node_id
        UNION
        SELECT e.to_node FROM edges e JOIN walk w ON e.from_node = w.node_id
    )
    SELECT node_id FROM walk;
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION gis_trace_upstream(p_tenant UUID, p_node UUID)
RETURNS TABLE (node_id UUID) AS $$
    WITH RECURSIVE edges AS (
        SELECT node_1_id AS from_node, node_2_id AS to_node
          FROM gis_arc
         WHERE tenant_id = p_tenant
           AND COALESCE(lifecycle_status, 'IN_SERVICE') = 'IN_SERVICE'
           AND flow_direction IN ('WITH_GEOMETRY', 'BOTH')
        UNION ALL
        SELECT node_2_id, node_1_id
          FROM gis_arc
         WHERE tenant_id = p_tenant
           AND COALESCE(lifecycle_status, 'IN_SERVICE') = 'IN_SERVICE'
           AND flow_direction IN ('AGAINST_GEOMETRY', 'BOTH')
    ), walk AS (
        SELECT p_node AS node_id
        UNION
        SELECT e.from_node FROM edges e JOIN walk w ON e.to_node = w.node_id
    )
    SELECT node_id FROM walk;
$$ LANGUAGE sql STABLE;

-- Poligono de roteamento da AREA (SG-889): envolvente convexa dos nos
-- do conjunto. Geometria de delimitacao de area — nao e caminho nem
-- rota de deslocamento de equipe (O8-1: solver de equipes bloqueado).
CREATE OR REPLACE FUNCTION gis_routing_polygon(p_tenant UUID, p_node_ids UUID[])
RETURNS geometry AS $$
    SELECT ST_ConvexHull(ST_Collect(geom))
      FROM gis_node
     WHERE tenant_id = p_tenant AND id = ANY (p_node_ids);
$$ LANGUAGE sql STABLE;

-- (5) Imoveis afetados de um plano (sem ordenacao: a ordem do aviso,
-- sensivel/essencial primeiro, e a do Java — ver cabecalho, item 5) ---------
CREATE OR REPLACE VIEW gis_maneuver_affected_units AS
SELECT p.tenant_id,
       p.id AS plan_id,
       c.id AS connection_id,
       c.service_connection_id,
       scu.consumer_unit_id,
       p.total_economies
  FROM gis_maneuver_plan p
  JOIN gis_connection c
    ON c.tenant_id = p.tenant_id
   AND (c.tap_node_id::text IN (SELECT jsonb_array_elements_text(p.affected_node_ids))
        OR c.arc_id::text IN (SELECT jsonb_array_elements_text(p.affected_arc_ids)))
  LEFT JOIN service_connection_units scu
    ON scu.tenant_id = c.tenant_id
   AND scu.service_connection_id = c.service_connection_id
 WHERE p.isolable;
