-- ED-87 — Operacao de estacoes sem supervisorio: diario, ronda e turno
-- (OPE-02, JOR-21; Portaria GM/MS 888/2021, art. 28, §3o)
-- SQL PENDENTE de numeracao Flyway (regra da cascata: nenhum numero
-- inventado; prefixo ED-87__ em db/pendente/). Na integracao: atribuir
-- o proximo slot livre e conferir as FKs externas adiadas (abaixo).
--
-- REGRA DURA: este SQL NAO semeia NENHUMA unidade, roteiro, item de
-- checklist, turno ou intervalo. Turnos (ops.shift_pattern) e
-- intervalos de ronda (ops.round_interval_by_unit) sao PARAMETROS do
-- tenant; o teto normativo do intervalo de ronda NAO tem numero nas
-- fontes do projeto [SEM FONTE] e e conferido na implantacao — o
-- unico numero normativo aqui e o CHECK documental das 2 h da
-- turbidez, que vive no nucleo (StationOpsParameters), nao em dado.
-- Os 14 valores da P3.9 nao sao tocados por esta tarefa.
--
-- LICAO DA C3: toda FK composta (tenant_id, X) aponta para pai com
-- PRIMARY KEY (tenant_id, id) — a unicidade (tenant_id, id) esta na
-- PK de cada tabela pai desta entrega.
--
-- IDEMPOTENCIA (correcao ED-87/D4): os 9 CREATE TABLE usam
-- IF NOT EXISTS, no padrao da ED-17 e da ED-92 — a reaplicacao
-- integral deste arquivo (recuperacao manual, reexecucao fora do
-- Flyway) nao falha com "relation already exists". O bloco DO ao
-- final ja era idempotente e nao mudou.
--
-- SERIE DA ED-17: as leituras manuais NAO ganham tabela nova — elas
-- sao gravadas em supervisory_samples (ED-17) com origem MANUAL pela
-- porta ManualSeriesPort. O bloco DO ao final acrescenta a coluna de
-- origem SOMENTE se a tabela da ED-17 ja existir neste banco; sem
-- ela, o bloco e no-op e a integracao aplica o mesmo ALTER depois da
-- migration da ED-17.

-- ---------------------------------------------------------------------
-- Unidade operacional (ETA, ETE, elevatoria, reservatorio)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ops_station_units (
    tenant_id       UUID NOT NULL,
    id              UUID NOT NULL,
    code            TEXT NOT NULL,
    kind            TEXT NOT NULL
        CHECK (kind IN ('WTP', 'WWTP', 'PUMP_STATION', 'RESERVOIR')),
    supply_system   TEXT NOT NULL,
    filtration      TEXT NOT NULL
        CHECK (filtration IN ('RAPID', 'SLOW', 'NOT_APPLICABLE')),
    filter_unit_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, code),
    CHECK (kind = 'WTP' OR filtration = 'NOT_APPLICABLE'),
    CHECK (filtration <> 'RAPID' OR jsonb_array_length(filter_unit_codes) > 0)
);
SELECT register_tenant_table('ops_station_units');

-- ---------------------------------------------------------------------
-- Diario de operacao por turno
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ops_shift_logs (
    tenant_id       UUID NOT NULL,
    id              UUID NOT NULL,
    unit_id         UUID NOT NULL,
    shift_date      DATE NOT NULL,
    shift_code      TEXT NOT NULL,
    opened_by_ref   TEXT NOT NULL,
    opened_at       TIMESTAMPTZ NOT NULL,
    status          TEXT NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'HANDED_OVER')),
    handover_outgoing_ref TEXT,
    handover_incoming_ref TEXT,
    handover_at     TIMESTAMPTZ,
    handover_pending_items JSONB,
    handover_notes  TEXT,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, unit_id, shift_date, shift_code),
    FOREIGN KEY (tenant_id, unit_id) REFERENCES ops_station_units (tenant_id, id),
    CHECK ((status = 'HANDED_OVER')
        = (handover_incoming_ref IS NOT NULL AND handover_at IS NOT NULL))
);
SELECT register_tenant_table('ops_shift_logs');

CREATE TABLE IF NOT EXISTS ops_shift_equipment (
    tenant_id       UUID NOT NULL,
    shift_log_id    UUID NOT NULL,
    equipment_code  TEXT NOT NULL,
    situation       TEXT NOT NULL
        CHECK (situation IN ('OPERATING', 'STANDBY_RESERVE', 'OUT_OF_SERVICE')),
    horimeter       NUMERIC,
    starts          INTEGER,
    PRIMARY KEY (tenant_id, shift_log_id, equipment_code),
    FOREIGN KEY (tenant_id, shift_log_id) REFERENCES ops_shift_logs (tenant_id, id),
    CHECK (horimeter IS NULL OR horimeter >= 0),
    CHECK (starts IS NULL OR starts >= 0)
);
SELECT register_tenant_table('ops_shift_equipment');

-- Leituras do diario (a SERIE fica na ED-17; esta tabela e o lancamento
-- do diario, com a chave da amostra gravada na serie para reconciliar).
CREATE TABLE IF NOT EXISTS ops_diary_readings (
    tenant_id       UUID NOT NULL,
    id              UUID NOT NULL,
    shift_log_id    UUID NOT NULL,
    kind            TEXT NOT NULL
        CHECK (kind IN ('LEVEL', 'FLOW', 'PRESSURE', 'TURBIDITY', 'CHLORINE', 'PH')),
    filter_unit_code TEXT,
    value           NUMERIC NOT NULL,
    unit_of_measure TEXT NOT NULL,
    measured_at     TIMESTAMPTZ NOT NULL,
    series_sample_key TEXT NOT NULL,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, series_sample_key),
    FOREIGN KEY (tenant_id, shift_log_id) REFERENCES ops_shift_logs (tenant_id, id),
    CHECK ((kind = 'TURBIDITY') = (filter_unit_code IS NOT NULL))
);
SELECT register_tenant_table('ops_diary_readings');

-- Ocorrencia do diario -> OS da P1.31 (COE-10), uma OS por ocorrencia.
CREATE TABLE IF NOT EXISTS ops_diary_occurrences (
    tenant_id       UUID NOT NULL,
    id              UUID NOT NULL,
    shift_log_id    UUID NOT NULL,
    description     TEXT NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL,
    reported_by     TEXT NOT NULL,
    work_order_id   UUID,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, work_order_id),
    FOREIGN KEY (tenant_id, shift_log_id) REFERENCES ops_shift_logs (tenant_id, id)
    -- FK (tenant_id, work_order_id) -> service_orders (tenant_id, id) da
    -- V9_1 (P1.31): conferir na integracao (padrao das FKs adiadas).
);
SELECT register_tenant_table('ops_diary_occurrences');

-- ---------------------------------------------------------------------
-- Roteiro e execucao de ronda (formulario offline, sync idempotente)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ops_round_routes (
    tenant_id       UUID NOT NULL,
    id              UUID NOT NULL,
    unit_id         UUID NOT NULL,
    code            TEXT NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, code),
    FOREIGN KEY (tenant_id, unit_id) REFERENCES ops_station_units (tenant_id, id)
);
SELECT register_tenant_table('ops_round_routes');

CREATE TABLE IF NOT EXISTS ops_round_route_items (
    tenant_id       UUID NOT NULL,
    route_id        UUID NOT NULL,
    sequence        INTEGER NOT NULL CHECK (sequence >= 1),
    item_key        TEXT NOT NULL,
    label           TEXT NOT NULL,
    required        BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (tenant_id, route_id, sequence),
    UNIQUE (tenant_id, route_id, item_key),
    FOREIGN KEY (tenant_id, route_id) REFERENCES ops_round_routes (tenant_id, id)
);
SELECT register_tenant_table('ops_round_route_items');

CREATE TABLE IF NOT EXISTS ops_round_executions (
    tenant_id       UUID NOT NULL,
    id              UUID NOT NULL,
    route_id        UUID NOT NULL,
    unit_id         UUID NOT NULL,
    due_at          TIMESTAMPTZ NOT NULL,
    client_execution_key TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'SCHEDULED'
        CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'MISSED')),
    executor_ref    TEXT,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, client_execution_key),
    FOREIGN KEY (tenant_id, route_id) REFERENCES ops_round_routes (tenant_id, id),
    FOREIGN KEY (tenant_id, unit_id) REFERENCES ops_station_units (tenant_id, id),
    CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL))
);
SELECT register_tenant_table('ops_round_executions');

CREATE TABLE IF NOT EXISTS ops_round_execution_items (
    tenant_id       UUID NOT NULL,
    execution_id    UUID NOT NULL,
    item_key        TEXT NOT NULL,
    result          TEXT NOT NULL
        CHECK (result IN ('OK', 'NOT_OK', 'NOT_APPLICABLE')),
    note            TEXT,
    PRIMARY KEY (tenant_id, execution_id, item_key),
    FOREIGN KEY (tenant_id, execution_id) REFERENCES ops_round_executions (tenant_id, id),
    CHECK (result <> 'NOT_OK' OR note IS NOT NULL)
);
SELECT register_tenant_table('ops_round_execution_items');

-- ---------------------------------------------------------------------
-- Origem MANUAL na serie da ED-17 (somente se a tabela ja existir)
-- ---------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_tables WHERE tablename = 'supervisory_samples') THEN
        ALTER TABLE supervisory_samples
            ADD COLUMN IF NOT EXISTS origin TEXT NOT NULL DEFAULT 'SUPERVISORY';
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_supervisory_samples_origin') THEN
            ALTER TABLE supervisory_samples
                ADD CONSTRAINT ck_supervisory_samples_origin
                CHECK (origin IN ('MANUAL', 'SUPERVISORY'));
        END IF;
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- Parametros do tenant (definitions para conferencia contra o catalogo
-- de parametros vigente na integracao — NAO atribuir slot sem conferir)
--   ops.shift_pattern            sem valor de fabrica: lista de turnos
--                                (codigo, inicio, fim) do prestador.
--   ops.round_interval_by_unit   sem valor de fabrica: minutos por
--                                unidade; o tenant so REDUZ contra o
--                                teto normativo conferido na
--                                implantacao [SEM FONTE como numero].
-- ---------------------------------------------------------------------
