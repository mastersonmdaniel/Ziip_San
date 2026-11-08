-- ============================================================================
-- ED-92 (PENDENTE DE NUMERACAO): ocorrencia de rede como entidade (OPE-16;
-- DAD-27, JOR-33, RAS-29; NR 11/ANA art. 41; NR 9/ANA I-04/I-05).
-- SQL sem numero: prefixo ED-92__ em db/pendente/ (regra da cascata).
-- Depende de: V9_1 (service_orders da P1.31), V13 (GIS, area da ED-54),
-- ED-35 (interrupcao programada, subtipo), ED-64 (gis_drain_flood_occurrence
-- com network_occurrence_id a ligar na integracao).
-- NENHUM DADO SEMEADO: o catalogo de causas e PARAMETRO do tenant
-- (outage.cause_catalog); a unica causa com fonte e ALAGAMENTO (ED-64),
-- registrada na NOTA DE RECONCILIACAO do relatorio — nenhuma linha de
-- causa, severidade ou SLA nasce neste SQL.
-- ============================================================================
SELECT set_config('app.current_tenant', '${tenant_id}', true);

CREATE TABLE IF NOT EXISTS network_incidents (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code TEXT NOT NULL,
    incident_type TEXT NOT NULL CHECK (incident_type IN
        ('INTERRUPTION','RUPTURE','OVERFLOW','OBSTRUCTION','POWER_OUTAGE','FLOODING')),
    planned BOOLEAN NOT NULL DEFAULT FALSE,
    origin TEXT NOT NULL CHECK (origin IN ('COMPLAINTS','TELEMETRY','CREW','PLANNED')),
    status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CONTROLLED','RESTORED','CLOSED')),
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,
    cause_code TEXT,
    affected_unit_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    total_economies INTEGER NOT NULL DEFAULT 0,
    sewer_network_km NUMERIC,
    agency_notified_at TIMESTAMP,
    users_notified_at TIMESTAMP,
    emergency_supply_started_at TIMESTAMP,
    planned_outage_id UUID,
    lost_volume_m3 NUMERIC,
    repair_minutes BIGINT,
    repair_work_order_id UUID,
    CONSTRAINT uq_network_incidents_tenant_code UNIQUE (tenant_id, code),
    -- Correcao G1 (revisao ED-92): a FK composta de
    -- network_incident_work_orders REFERENCES (tenant_id, id) exige
    -- esta UNIQUE — sem ela a migration NAO aplica (provado em PG16).
    CONSTRAINT uq_network_incidents_tenant_id UNIQUE (tenant_id, id),
    -- Correcao M1: BICONDICIONAL do subtipo programada (espelho do
    -- construtor Java): planned <=> origem PLANNED <=> vinculo ED-35.
    CONSTRAINT ck_ni_planned_origin CHECK (planned = (origin = 'PLANNED')),
    CONSTRAINT ck_ni_planned_link CHECK (planned = (planned_outage_id IS NOT NULL)),
    -- Correcao m1: causa de encerramento nao-vazia (btrim), como no Java.
    CONSTRAINT ck_ni_closed_cause CHECK ((status <> 'CLOSED')
        OR (cause_code IS NOT NULL AND btrim(cause_code) <> ''))
);
SELECT register_tenant_table('network_incidents');

CREATE TABLE IF NOT EXISTS network_incident_work_orders (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    incident_id UUID NOT NULL,
    work_order_id UUID NOT NULL,
    CONSTRAINT uq_niwo UNIQUE (tenant_id, incident_id, work_order_id),
    CONSTRAINT fk_niwo_incident FOREIGN KEY (tenant_id, incident_id)
        REFERENCES network_incidents (tenant_id, id)
);
SELECT register_tenant_table('network_incident_work_orders');

-- DAD-27: achado de pesquisa de vazamento SEM ciclo proprio de reparo
-- (sem coluna de estado/data/responsavel de reparo — o reparo e so da OS).
CREATE TABLE IF NOT EXISTS leak_detection_findings (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code TEXT NOT NULL,
    found_at TIMESTAMP NOT NULL,
    location_text TEXT NOT NULL,
    disposition TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (disposition IN ('PENDING','CONFIRMED_LINKED','NOT_A_LEAK')),
    network_incident_id UUID,
    work_order_id UUID,
    CONSTRAINT uq_ldf_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_ldf_link CHECK ((disposition <> 'CONFIRMED_LINKED')
        OR (network_incident_id IS NOT NULL AND work_order_id IS NOT NULL))
);
SELECT register_tenant_table('leak_detection_findings');

-- JOR-33: relatorio por causa (so encerradas tem causa).
-- Correcao M2: contadores de CONHECIDOS expostos (repair_known/
-- volume_known) — SUM/AVG ja devolvem NULL quando tudo e NULL
-- (desconhecido = ausente, nunca zero), e os contadores distinguem
-- o total parcial. Correcao m1: causa vazia nunca agrupa.
CREATE OR REPLACE VIEW network_incident_cause_report AS
SELECT tenant_id, cause_code, COUNT(*) AS incidents,
       COUNT(repair_minutes) AS repair_known,
       AVG(repair_minutes) FILTER (WHERE repair_minutes IS NOT NULL) AS avg_repair_minutes,
       COUNT(lost_volume_m3) AS volume_known,
       SUM(lost_volume_m3) AS total_lost_volume_m3
  FROM network_incidents
 WHERE status = 'CLOSED' AND cause_code IS NOT NULL AND btrim(cause_code) <> ''
 GROUP BY tenant_id, cause_code;
