-- ED-17 -- Telemetria e supervisório por REST e arquivo.
--
-- *** PENDÊNCIA DE NUMERAÇÃO — LER ANTES DE APLICAR ***
-- O mapa de versões Flyway do plano v3.0 (§3) NÃO reserva versão para a
-- ED-17: a tarefa é da onda A, posterior à P3.12, e as migrations dessa
-- fase usam a sequência global a partir de V100, com o módulo no nome
-- (ex.: V1nn__metering_telemetry.sql) — e o número concreto só sai na
-- ordem do merge, como o próprio §3 manda para a sequência global.
-- Por isso este arquivo NÃO está em db/migration/tenant/ e NÃO deve ser
-- renomeado por chute: na integração, ele recebe o próximo número livre
-- da sequência global e o nome V<numero>__metering_telemetry.sql — e, no
-- mesmo commit, o TelemetryMigrationConsistencyTest passa a ler o SQL do
-- novo caminho. Nenhum número foi inventado nesta entrega.
--
-- O QUE CRIA
--   * Modelo de medição: measuring_points (ponto, com relógio/fuso e base
--     legal do consumo horário), telemetry_devices (dispositivo) e
--     telemetry_channels (canal).
--   * telemetry_readings: leituras AMR brutas (pré-VEE), particionada por
--     mês pelo padrão da P2.3 (o da V5_4/V5_5), SEM auditoria por linha
--     (register_tenant_table com p_audit => false, como a V5_1 anuncia
--     para a tabela bruta de telemetria), idempotência por referência
--     externa única por (tenant, mês, chave).
--   * telemetry_hourly_profile: a curva horária, particionada por mês,
--     sem auditoria por linha, única por (tenant, mês, ponto, canal, hora)
--     e com índice por ponto e tempo; telemetry_hourly_daily_summary é a
--     tabela de resumo dos agregados (tele.rollup_policy).
--   * meter_alarms: alarmes persistidos do medidor (com a marca de grande
--     consumidor), com auditoria — alarme é fato de negócio, não série.
--   * supervisory_samples: amostras do supervisório, particionada por mês,
--     sem auditoria por linha, idempotência por amostra (sample_key).
--   * station_outages: paradas de estação — nesta entrega só o registro
--     MANUAL da ETA é produzido; a origem SUPERVISORY fica reservada à
--     integração com o diário de operação da ED-87, e nenhum caminho desta
--     entrega a grava.
--
-- PADRÃO DE PARTIÇÃO (P2.3, V5_4/V5_5): RANGE no mês, PK com a chave de
-- partição, sem partição DEFAULT (mês sem partição falha em vez de cair
-- num balde sem poda), janela pré-criada 2026-01 .. mês corrente + 12,
-- função de criação por mês só do migrador, RLS forçada e policy em cada
-- partição pela função de blindagem.
--
-- NENHUM dado é semeado: ponto, dispositivo, canal, limiar e base legal são
-- do tenant e da implantação.

-- ========================================================================
-- MODELO DE MEDIÇÃO: PONTO, DISPOSITIVO, CANAL
-- ========================================================================

CREATE TABLE IF NOT EXISTS measuring_points (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    unit_id UUID NOT NULL,
    installation_id UUID NOT NULL,
    kind VARCHAR(5) NOT NULL CHECK (kind IN ('WATER', 'WELL')),
    timezone_id VARCHAR(50) NOT NULL,
    legal_basis VARCHAR(80),
    large_consumer BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE measuring_points ADD CONSTRAINT uq_measuring_points_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE measuring_points ADD CONSTRAINT fk_measuring_point_unit
    FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
ALTER TABLE measuring_points ADD CONSTRAINT fk_measuring_point_installation
    FOREIGN KEY (tenant_id, installation_id) REFERENCES meter_installations(tenant_id, id);
CREATE UNIQUE INDEX idx_measuring_points_code ON measuring_points(tenant_id, code);
CREATE INDEX idx_measuring_points_unit ON measuring_points(tenant_id, unit_id);

CREATE TABLE IF NOT EXISTS telemetry_devices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    measuring_point_id UUID NOT NULL,
    serial_number VARCHAR(60) NOT NULL,
    vendor VARCHAR(60),
    model VARCHAR(60),
    clock_drift_seconds INT,
    last_seen_at TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE telemetry_devices ADD CONSTRAINT uq_telemetry_devices_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE telemetry_devices ADD CONSTRAINT fk_telemetry_device_point
    FOREIGN KEY (tenant_id, measuring_point_id) REFERENCES measuring_points(tenant_id, id);
CREATE UNIQUE INDEX idx_telemetry_devices_serial ON telemetry_devices(tenant_id, serial_number);
CREATE INDEX idx_telemetry_devices_point ON telemetry_devices(tenant_id, measuring_point_id);

CREATE TABLE IF NOT EXISTS telemetry_channels (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    device_id UUID NOT NULL,
    measuring_point_id UUID NOT NULL,
    code VARCHAR(30) NOT NULL,
    description VARCHAR(120),
    unit_of_measure VARCHAR(20),
    dial_digits INT CHECK (dial_digits BETWEEN 1 AND 9),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE telemetry_channels ADD CONSTRAINT uq_telemetry_channels_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE telemetry_channels ADD CONSTRAINT fk_telemetry_channel_device
    FOREIGN KEY (tenant_id, device_id) REFERENCES telemetry_devices(tenant_id, id);
ALTER TABLE telemetry_channels ADD CONSTRAINT fk_telemetry_channel_point
    FOREIGN KEY (tenant_id, measuring_point_id) REFERENCES measuring_points(tenant_id, id);
CREATE UNIQUE INDEX idx_telemetry_channels_point_code ON telemetry_channels(tenant_id, measuring_point_id, code);

SELECT register_tenant_table('measuring_points');
SELECT register_tenant_table('telemetry_devices');
SELECT register_tenant_table('telemetry_channels');

-- ========================================================================
-- GESTÃO DE PARTIÇÕES DAS SÉRIES (só o migrador: DDL no schema do tenant)
-- ========================================================================
-- Uma função de blindagem e uma de criação por tabela particionada, no
-- padrão da V5_5: a partição NÃO herda a policy da mãe, então cada
-- partição recebe RLS FORÇADA e a sua policy na criação — e a blindagem é
-- reaplicada quando a partição já existe.

CREATE OR REPLACE FUNCTION telemetry_secure_partition(p_schema TEXT, p_part TEXT)
RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE
    v_policy TEXT := p_part || '_isolation';
BEGIN
    EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', p_schema, p_part);
    EXECUTE format('ALTER TABLE %I.%I FORCE ROW LEVEL SECURITY', p_schema, p_part);
    IF NOT EXISTS (SELECT 1 FROM pg_policies
                   WHERE schemaname = p_schema AND tablename = p_part AND policyname = v_policy) THEN
        EXECUTE format('CREATE POLICY %I ON %I.%I USING (tenant_id = current_tenant_id()) '
                       || 'WITH CHECK (tenant_id = current_tenant_id())',
                       v_policy, p_schema, p_part);
    END IF;
END;
$$;
REVOKE ALL ON FUNCTION telemetry_secure_partition(TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION telemetry_secure_partition(TEXT, TEXT) TO ziipsan_migrator;

CREATE OR REPLACE FUNCTION telemetry_readings_create_partition(p_month DATE)
RETURNS TEXT LANGUAGE plpgsql AS $$
DECLARE
    v_schema TEXT := current_schema();
    v_start DATE := date_trunc('month', p_month)::DATE;
    v_end DATE := (v_start + INTERVAL '1 month')::DATE;
    v_name TEXT := 'telemetry_readings_' || to_char(v_start, 'YYYY_MM');
BEGIN
    IF to_regclass(v_schema || '.' || v_name) IS NULL THEN
        EXECUTE format('CREATE TABLE %I.%I PARTITION OF %I.telemetry_readings '
                       || 'FOR VALUES FROM (%L) TO (%L)',
                       v_schema, v_name, v_schema, v_start, v_end);
    END IF;
    PERFORM telemetry_secure_partition(v_schema, v_name);
    RETURN v_name;
END;
$$;
REVOKE ALL ON FUNCTION telemetry_readings_create_partition(DATE) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION telemetry_readings_create_partition(DATE) TO ziipsan_migrator;

CREATE OR REPLACE FUNCTION telemetry_hourly_create_partition(p_month DATE)
RETURNS TEXT LANGUAGE plpgsql AS $$
DECLARE
    v_schema TEXT := current_schema();
    v_start DATE := date_trunc('month', p_month)::DATE;
    v_end DATE := (v_start + INTERVAL '1 month')::DATE;
    v_name TEXT := 'telemetry_hourly_profile_' || to_char(v_start, 'YYYY_MM');
BEGIN
    IF to_regclass(v_schema || '.' || v_name) IS NULL THEN
        EXECUTE format('CREATE TABLE %I.%I PARTITION OF %I.telemetry_hourly_profile '
                       || 'FOR VALUES FROM (%L) TO (%L)',
                       v_schema, v_name, v_schema, v_start, v_end);
    END IF;
    PERFORM telemetry_secure_partition(v_schema, v_name);
    RETURN v_name;
END;
$$;
REVOKE ALL ON FUNCTION telemetry_hourly_create_partition(DATE) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION telemetry_hourly_create_partition(DATE) TO ziipsan_migrator;

CREATE OR REPLACE FUNCTION supervisory_samples_create_partition(p_month DATE)
RETURNS TEXT LANGUAGE plpgsql AS $$
DECLARE
    v_schema TEXT := current_schema();
    v_start DATE := date_trunc('month', p_month)::DATE;
    v_end DATE := (v_start + INTERVAL '1 month')::DATE;
    v_name TEXT := 'supervisory_samples_' || to_char(v_start, 'YYYY_MM');
BEGIN
    IF to_regclass(v_schema || '.' || v_name) IS NULL THEN
        EXECUTE format('CREATE TABLE %I.%I PARTITION OF %I.supervisory_samples '
                       || 'FOR VALUES FROM (%L) TO (%L)',
                       v_schema, v_name, v_schema, v_start, v_end);
    END IF;
    PERFORM telemetry_secure_partition(v_schema, v_name);
    RETURN v_name;
END;
$$;
REVOKE ALL ON FUNCTION supervisory_samples_create_partition(DATE) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION supervisory_samples_create_partition(DATE) TO ziipsan_migrator;

CREATE OR REPLACE FUNCTION telemetry_ensure_partitions()
RETURNS INT LANGUAGE plpgsql AS $$
DECLARE
    v_month DATE := DATE '2026-01-01';
    v_last DATE := (date_trunc('month', CURRENT_DATE) + INTERVAL '12 months')::DATE;
    v_created INT := 0;
    v_schema TEXT := current_schema();
BEGIN
    WHILE v_month <= v_last LOOP
        IF to_regclass(v_schema || '.telemetry_readings_' || to_char(v_month, 'YYYY_MM')) IS NULL THEN
            v_created := v_created + 1;
        END IF;
        PERFORM telemetry_readings_create_partition(v_month);
        PERFORM telemetry_hourly_create_partition(v_month);
        PERFORM supervisory_samples_create_partition(v_month);
        v_month := (v_month + INTERVAL '1 month')::DATE;
    END LOOP;
    RETURN v_created;
END;
$$;
REVOKE ALL ON FUNCTION telemetry_ensure_partitions() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION telemetry_ensure_partitions() TO ziipsan_migrator;

-- ========================================================================
-- LEITURAS AMR BRUTAS (pré-VEE), PARTICIONADAS, SEM AUDITORIA POR LINHA
-- ========================================================================

CREATE TABLE IF NOT EXISTS telemetry_readings (
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    measuring_point_id UUID NOT NULL,
    channel_id UUID NOT NULL,
    reference_month DATE NOT NULL CHECK (EXTRACT(DAY FROM reference_month) = 1),
    read_at TIMESTAMPTZ NOT NULL,
    dial_reading INT CHECK (dial_reading >= 0),
    data_state VARCHAR(15) NOT NULL
        CHECK (data_state IN ('MEASURED', 'ESTIMATED', 'SUBSTITUTED', 'LATE')),
    vee_status VARCHAR(15) NOT NULL
        CHECK (vee_status IN ('RECEIVED', 'VALIDATED', 'ESTIMATED', 'EDITED', 'REJECTED', 'PROMOTED')),
    vee_detail JSONB,
    idempotency_key VARCHAR(120) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    promoted_reading_id UUID,
    promoted_reference DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    PRIMARY KEY (tenant_id, reference_month, id)
) PARTITION BY RANGE (reference_month);
ALTER TABLE telemetry_readings ADD CONSTRAINT fk_telemetry_reading_point
    FOREIGN KEY (tenant_id, measuring_point_id) REFERENCES measuring_points(tenant_id, id);
ALTER TABLE telemetry_readings ADD CONSTRAINT fk_telemetry_reading_channel
    FOREIGN KEY (tenant_id, channel_id) REFERENCES telemetry_channels(tenant_id, id);
CREATE UNIQUE INDEX idx_telemetry_readings_idempotency
    ON telemetry_readings(tenant_id, reference_month, idempotency_key);
CREATE INDEX idx_telemetry_readings_point_time
    ON telemetry_readings(tenant_id, measuring_point_id, read_at);

-- ========================================================================
-- CURVA HORÁRIA, PARTICIONADA, SEM AUDITORIA POR LINHA + TABELA DE RESUMO
-- ========================================================================

CREATE TABLE IF NOT EXISTS telemetry_hourly_profile (
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    measuring_point_id UUID NOT NULL,
    channel_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    bucket_month DATE NOT NULL CHECK (EXTRACT(DAY FROM bucket_month) = 1),
    hour_start TIMESTAMPTZ NOT NULL,
    volume_litres NUMERIC(14,3) NOT NULL CHECK (volume_litres >= 0),
    reverse_volume_litres NUMERIC(14,3) CHECK (reverse_volume_litres >= 0),
    data_state VARCHAR(15) NOT NULL
        CHECK (data_state IN ('MEASURED', 'ESTIMATED', 'SUBSTITUTED', 'LATE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    PRIMARY KEY (tenant_id, bucket_month, id)
) PARTITION BY RANGE (bucket_month);
ALTER TABLE telemetry_hourly_profile ADD CONSTRAINT fk_telemetry_hourly_point
    FOREIGN KEY (tenant_id, measuring_point_id) REFERENCES measuring_points(tenant_id, id);
ALTER TABLE telemetry_hourly_profile ADD CONSTRAINT fk_telemetry_hourly_channel
    FOREIGN KEY (tenant_id, channel_id) REFERENCES telemetry_channels(tenant_id, id);
ALTER TABLE telemetry_hourly_profile ADD CONSTRAINT fk_telemetry_hourly_unit
    FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
CREATE UNIQUE INDEX idx_telemetry_hourly_point_hour
    ON telemetry_hourly_profile(tenant_id, bucket_month, measuring_point_id, channel_id, hour_start);
-- Índice por ponto e tempo: a consulta da curva é sempre por ponto e intervalo.
CREATE INDEX idx_telemetry_hourly_point_time
    ON telemetry_hourly_profile(tenant_id, measuring_point_id, hour_start);

-- Tabela de resumo: os agregados diários da curva (tele.rollup_policy),
-- que sobrevivem ao descarte da série horária pela retenção.
CREATE TABLE IF NOT EXISTS telemetry_hourly_daily_summary (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    measuring_point_id UUID NOT NULL,
    channel_id UUID NOT NULL,
    day DATE NOT NULL,
    total_volume_litres NUMERIC(16,3) NOT NULL CHECK (total_volume_litres >= 0),
    total_reverse_litres NUMERIC(16,3) NOT NULL DEFAULT 0 CHECK (total_reverse_litres >= 0),
    samples INT NOT NULL CHECK (samples >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE telemetry_hourly_daily_summary ADD CONSTRAINT uq_telemetry_summary_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE telemetry_hourly_daily_summary ADD CONSTRAINT fk_telemetry_summary_point
    FOREIGN KEY (tenant_id, measuring_point_id) REFERENCES measuring_points(tenant_id, id);
CREATE UNIQUE INDEX idx_telemetry_summary_point_day
    ON telemetry_hourly_daily_summary(tenant_id, measuring_point_id, channel_id, day);

-- ========================================================================
-- ALARMES PERSISTIDOS (com auditoria: fato de negócio, não série)
-- ========================================================================

CREATE TABLE IF NOT EXISTS meter_alarms (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    measuring_point_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    kind VARCHAR(20) NOT NULL
        CHECK (kind IN ('CONTINUOUS_FLOW', 'REVERSE_FLOW', 'EMPTY_PIPE', 'TAMPERING', 'BATTERY_LOW', 'OVERFLOW')),
    status VARCHAR(15) NOT NULL CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    raised_at TIMESTAMPTZ NOT NULL,
    detail JSONB,
    action VARCHAR(20) CHECK (action IN ('OPEN_SERVICE_ORDER', 'NOTIFY_CUSTOMER', 'BOTH')),
    service_order_id UUID,
    notified_at TIMESTAMPTZ,
    large_consumer BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE meter_alarms ADD CONSTRAINT uq_meter_alarms_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE meter_alarms ADD CONSTRAINT fk_meter_alarm_point
    FOREIGN KEY (tenant_id, measuring_point_id) REFERENCES measuring_points(tenant_id, id);
ALTER TABLE meter_alarms ADD CONSTRAINT fk_meter_alarm_unit
    FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
CREATE INDEX idx_meter_alarms_unit ON meter_alarms(tenant_id, unit_id, raised_at DESC);
CREATE INDEX idx_meter_alarms_open ON meter_alarms(tenant_id, measuring_point_id, kind) WHERE status = 'OPEN';

-- ========================================================================
-- AMOSTRAS DO SUPERVISÓRIO, PARTICIONADAS, SEM AUDITORIA POR LINHA
-- ========================================================================

CREATE TABLE IF NOT EXISTS supervisory_samples (
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    station_code VARCHAR(40) NOT NULL,
    tag_code VARCHAR(60) NOT NULL,
    bucket_month DATE NOT NULL CHECK (EXTRACT(DAY FROM bucket_month) = 1),
    sampled_at TIMESTAMPTZ NOT NULL,
    numeric_value NUMERIC(18,6),
    text_value VARCHAR(200),
    unit_of_measure VARCHAR(20),
    quality VARCHAR(30),
    sample_key VARCHAR(120) NOT NULL,
    envelope_id VARCHAR(80),
    received_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    PRIMARY KEY (tenant_id, bucket_month, id)
) PARTITION BY RANGE (bucket_month);
CREATE UNIQUE INDEX idx_supervisory_samples_key
    ON supervisory_samples(tenant_id, bucket_month, sample_key);
CREATE INDEX idx_supervisory_samples_station_time
    ON supervisory_samples(tenant_id, station_code, tag_code, sampled_at);

-- ========================================================================
-- PARADAS DE ESTAÇÃO (registro manual da ETA; o diário é da ED-87)
-- ========================================================================

CREATE TABLE IF NOT EXISTS station_outages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    station_code VARCHAR(40) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    reason VARCHAR(300) NOT NULL,
    source VARCHAR(15) NOT NULL CHECK (source IN ('MANUAL', 'SUPERVISORY')),
    registered_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (ended_at IS NULL OR ended_at > started_at)
);
ALTER TABLE station_outages ADD CONSTRAINT uq_station_outages_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_station_outages_station ON station_outages(tenant_id, station_code, started_at DESC);

-- ========================================================================
-- ISOLAMENTO, AUDITORIA E JANELA DE PARTIÇÕES
-- ========================================================================
-- As tabelas-série entram SEM auditoria por linha (p_audit => false), como
-- a V5_1 anuncia para a tabela bruta de telemetria; RLS e grants são do
-- helper, e cada partição é blindada pela função de criação (V5_5).

SELECT register_tenant_table('telemetry_readings', p_audit => false);
SELECT register_tenant_table('telemetry_hourly_profile', p_audit => false);
SELECT register_tenant_table('supervisory_samples', p_audit => false);
SELECT register_tenant_table('telemetry_hourly_daily_summary');
SELECT register_tenant_table('meter_alarms');
SELECT register_tenant_table('station_outages');

SELECT telemetry_ensure_partitions();

GRANT SELECT, INSERT, UPDATE, DELETE ON telemetry_readings TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON telemetry_hourly_profile TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON supervisory_samples TO ziipsan_app;
