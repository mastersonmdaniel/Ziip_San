-- Metering Module Migration for Tenant Schema
-- Version: 5.1.0
-- Description: Hidrometro, historico de instalacao, leitura, catalogos de anormalidade e consumo
-- faturado por unidade e referencia.
--
-- Depende de V3_1 (registry): consumer_units com UNIQUE (tenant_id, id).
--
-- NENHUM catalogo e semeado. Anormalidades de leitura e de consumo, e os efeitos de cada uma, sao
-- politica do prestador. Os quatro efeitos de reading_anomalies sao NOT NULL e sem DEFAULT: cada
-- anormalidade e uma decisao explicita para os dois casos (com e sem leitura), e uma linha de
-- catalogo incompleta nao pode passar por "padrao". Ver docs/DIARIO-DE-BORDA.md, secao 15.4.
--
-- Valores de enum (consumption_type, kind, effects...) acompanham os enums de
-- br.com.sigsan.operations.metering.rules; um CHECK por coluna impede valor fora da lista.

-- btree_gist e criado uma vez por banco, em public (infra/postgres/init/01-security-roles.sql).
-- NAO criar aqui: dentro da migration de um tenant a extensao cairia no schema do tenant e os
-- demais tenants nao a enxergariam. Se faltar, a migration para com mensagem clara.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'btree_gist') THEN
        RAISE EXCEPTION 'extensao btree_gist ausente: crie-a no banco (infra/postgres/init/01-security-roles.sql) antes de migrar tenants';
    END IF;
END
$$;

-- ========================================================================
-- HIDROMETRO
-- ========================================================================

CREATE TABLE IF NOT EXISTS meters (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    meter_number VARCHAR(30) NOT NULL,
    brand VARCHAR(60),
    model VARCHAR(60),
    metrological_class VARCHAR(10),
    nominal_diameter_mm NUMERIC(6,1) CHECK (nominal_diameter_mm > 0),
    -- digitos do mostrador: define a virada (10^digits) e o limite da leitura
    digits INT NOT NULL CHECK (digits BETWEEN 1 AND 9),
    manufacture_year INT CHECK (manufacture_year BETWEEN 1900 AND 2200),
    -- hidrometro deve ter aprovacao de modelo do Inmetro (relatorio 08, R-03)
    inmetro_approval VARCHAR(60),
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('AVAILABLE', 'INSTALLED', 'IN_MAINTENANCE', 'STOLEN', 'WRITTEN_OFF')),
    last_revision_on DATE,
    notes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE meters ADD CONSTRAINT uq_meters_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_meters_number ON meters(tenant_id, meter_number);
CREATE INDEX idx_meters_status ON meters(tenant_id, status);

-- ========================================================================
-- INSTALACAO (historico temporal)
-- ========================================================================
-- Intervalo [installed_on, removed_on): o dia da retirada ja e livre para uma nova instalacao
-- (troca no mesmo dia). removed_on nulo = instalacao vigente. Duas constraints de exclusao:
--   * uma unidade nao tem dois hidrometros do mesmo tipo (agua, poco) ao mesmo tempo;
--   * um hidrometro nao esta em duas unidades ao mesmo tempo.
-- removed_on > installed_on (e nao >=): o intervalo vazio [d, d) nunca se sobrepoe a nada e
-- escaparia da exclusao. Instalar e retirar no mesmo dia nao e registravel; corrija o registro.

CREATE TABLE IF NOT EXISTS meter_installations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    meter_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    kind VARCHAR(5) NOT NULL CHECK (kind IN ('WATER', 'WELL')),
    installed_on DATE NOT NULL,
    installed_reading INT NOT NULL CHECK (installed_reading >= 0),
    removed_on DATE,
    removed_reading INT CHECK (removed_reading >= 0),
    is_replacement BOOLEAN NOT NULL,
    seal_number VARCHAR(40),
    protection_type VARCHAR(40),
    location_description VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (removed_on IS NULL OR removed_on > installed_on),
    CHECK ((removed_on IS NULL) = (removed_reading IS NULL))
);
ALTER TABLE meter_installations ADD CONSTRAINT uq_meter_installations_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE meter_installations ADD CONSTRAINT fk_installation_meter
    FOREIGN KEY (tenant_id, meter_id) REFERENCES meters(tenant_id, id);
ALTER TABLE meter_installations ADD CONSTRAINT fk_installation_unit
    FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
ALTER TABLE meter_installations ADD CONSTRAINT ex_installation_unit_period
    EXCLUDE USING gist (tenant_id WITH =, unit_id WITH =, kind WITH =,
                        daterange(installed_on, removed_on, '[)') WITH &&);
ALTER TABLE meter_installations ADD CONSTRAINT ex_installation_meter_period
    EXCLUDE USING gist (tenant_id WITH =, meter_id WITH =,
                        daterange(installed_on, removed_on, '[)') WITH &&);
CREATE INDEX idx_installations_unit ON meter_installations(tenant_id, unit_id, kind, installed_on DESC);

-- ========================================================================
-- CATALOGO: ANORMALIDADE DE LEITURA (sem semente)
-- ========================================================================
-- Efeito da anormalidade sobre a leitura faturada e sobre o consumo, com e sem leitura atual.
-- Sem leitura so existem efeitos que nao dependem de um valor medido.
-- system_role liga a linha a um papel que a cascata usa (hidrometro parado): no legado isso era um
-- id numerico fixo no codigo. No maximo uma linha por papel e tenant.

CREATE TABLE IF NOT EXISTS reading_anomalies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(20) NOT NULL,
    description VARCHAR(200) NOT NULL,
    for_unit_without_meter BOOLEAN NOT NULL,
    requires_reading BOOLEAN NOT NULL,
    generates_service_order BOOLEAN NOT NULL,
    reading_effect_without_reading VARCHAR(30) NOT NULL
        CHECK (reading_effect_without_reading IN ('PREVIOUS_PLUS_AVERAGE', 'PREVIOUS', 'PREVIOUS_PLUS_CONSUMPTION')),
    reading_effect_with_reading VARCHAR(30) NOT NULL
        CHECK (reading_effect_with_reading IN ('PREVIOUS_PLUS_AVERAGE', 'PREVIOUS', 'PREVIOUS_PLUS_CONSUMPTION', 'INFORMED')),
    consumption_effect_without_reading VARCHAR(30) NOT NULL
        CHECK (consumption_effect_without_reading IN ('AVERAGE', 'MINIMUM', 'NOT_METERED')),
    consumption_effect_with_reading VARCHAR(30) NOT NULL
        CHECK (consumption_effect_with_reading IN ('AVERAGE', 'MINIMUM', 'NORMAL',
                                                   'MAX_OF_AVERAGE_AND_MEASURED', 'MIN_OF_AVERAGE_AND_MEASURED',
                                                   'NOT_METERED')),
    factor_without_reading NUMERIC(7,4) CHECK (factor_without_reading >= 0),
    factor_with_reading NUMERIC(7,4) CHECK (factor_with_reading >= 0),
    suspend_reading_months INT CHECK (suspend_reading_months >= 0),
    suspend_after_occurrences INT CHECK (suspend_after_occurrences >= 1),
    loses_social_tariff BOOLEAN NOT NULL,
    system_role VARCHAR(30) CHECK (system_role IN ('METER_STOPPED', 'METER_STOPPED_NO_CONSUMPTION')),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE reading_anomalies ADD CONSTRAINT uq_reading_anomalies_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_reading_anomalies_code ON reading_anomalies(tenant_id, code);
CREATE UNIQUE INDEX idx_reading_anomalies_role ON reading_anomalies(tenant_id, system_role)
    WHERE system_role IS NOT NULL;

-- ========================================================================
-- CATALOGO: ANORMALIDADE DE CONSUMO (sem semente)
-- ========================================================================
-- kind e a condicao que a cascata detecta (enum ConsumptionAnomalyKind); a linha do catalogo da
-- a ela descricao, mensagem na conta e se entra na media. Uma linha por kind e tenant: o onboarding
-- do tenant as cria (nao ha semente aqui porque descricao e mensagem sao do prestador).

CREATE TABLE IF NOT EXISTS consumption_anomalies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(20) NOT NULL,
    kind VARCHAR(30) NOT NULL
        CHECK (kind IN ('CONSUMPTION_INFORMED', 'LOW_CONSUMPTION', 'BURST', 'HIGH_CONSUMPTION',
                        'READING_BELOW_PROJECTED', 'READING_BELOW_PREVIOUS', 'METER_REPLACED_INFORMED',
                        'READING_NOT_INFORMED', 'BURST_CHARGED_AT_AVERAGE', 'MINIMUM_FIXED', 'OUT_OF_RANGE',
                        'METER_ROLLOVER', 'READING_ANOMALY')),
    description VARCHAR(200) NOT NULL,
    bill_message VARCHAR(500),
    counts_for_average BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE consumption_anomalies ADD CONSTRAINT uq_consumption_anomalies_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_consumption_anomalies_code ON consumption_anomalies(tenant_id, code);
CREATE UNIQUE INDEX idx_consumption_anomalies_kind ON consumption_anomalies(tenant_id, kind);

-- ========================================================================
-- LEITURA
-- ========================================================================
-- Uma linha por leitura aceita (a que passou na validacao do retorno). A linha recusada vai para o
-- relatorio de erros e nao e gravada. Leitura e consumo sao tabelas separadas: o consumo faturado
-- pode diferir da leitura (media, ajuste por dias, anormalidade), e e isso que permite auditar
-- "por que cobrou X".
--
-- Auditoria LIGADA (register_tenant_table sem p_audit => false): uma leitura por unidade e mes nao
-- e serie temporal de alto volume, e a correcao de uma leitura e exatamente o que precisa de
-- trilha (adulteracao de leitura). Quando existir a tabela bruta de telemetria (leituras de
-- minuto a minuto), ESSA sim usa p_audit => false.

CREATE TABLE IF NOT EXISTS meter_readings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    installation_id UUID NOT NULL,
    kind VARCHAR(5) NOT NULL CHECK (kind IN ('WATER', 'WELL')),
    reference DATE NOT NULL CHECK (EXTRACT(DAY FROM reference) = 1),
    read_on DATE NOT NULL,
    reading INT CHECK (reading >= 0),
    reading_status VARCHAR(15) NOT NULL
        CHECK (reading_status IN ('REALIZED', 'NOT_REALIZED', 'CONFIRMED', 'CHANGED')),
    anomaly_id UUID,
    informed_consumption INT CHECK (informed_consumption >= 0),
    source VARCHAR(15) NOT NULL CHECK (source IN ('MANUAL', 'MOBILE', 'COLLECTOR', 'TELEMETRY')),
    -- usuario do sistema que lancou a leitura; sem FK porque users nao tem UNIQUE (tenant_id, id)
    reader_id UUID,
    captured_at TIMESTAMPTZ NOT NULL,
    validation_warnings JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    -- uma linha sem leitura so existe para registrar uma anormalidade
    CHECK (reading IS NOT NULL OR anomaly_id IS NOT NULL)
);
ALTER TABLE meter_readings ADD CONSTRAINT uq_meter_readings_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE meter_readings ADD CONSTRAINT fk_reading_unit
    FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
ALTER TABLE meter_readings ADD CONSTRAINT fk_reading_installation
    FOREIGN KEY (tenant_id, installation_id) REFERENCES meter_installations(tenant_id, id);
ALTER TABLE meter_readings ADD CONSTRAINT fk_reading_anomaly
    FOREIGN KEY (tenant_id, anomaly_id) REFERENCES reading_anomalies(tenant_id, id);
CREATE INDEX idx_readings_unit_reference ON meter_readings(tenant_id, unit_id, kind, reference);
CREATE INDEX idx_readings_installation ON meter_readings(tenant_id, installation_id);

-- ========================================================================
-- CONSUMO FATURADO
-- ========================================================================
-- Uma linha por unidade, servico (agua ou esgoto) e referencia. Recalcular (retificacao) atualiza a
-- linha; a historia das versoes fica no audit_log.
-- reference e sempre o dia 1 do mes (a competencia).
-- calculated_consumption e a base da media futura (nao e zerado quando billable e falso).
-- warnings e calculation_snapshot guardam o que a cascata avisou e os insumos, para auditoria.

CREATE TABLE IF NOT EXISTS consumption_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    service_kind VARCHAR(5) NOT NULL CHECK (service_kind IN ('WATER', 'SEWER')),
    reference DATE NOT NULL CHECK (EXTRACT(DAY FROM reference) = 1),
    billed_consumption INT NOT NULL CHECK (billed_consumption >= 0),
    calculated_consumption INT NOT NULL CHECK (calculated_consumption >= 0),
    billable BOOLEAN NOT NULL,
    billing_paused BOOLEAN NOT NULL,
    consumption_type VARCHAR(30) NOT NULL
        CHECK (consumption_type IN ('REAL', 'ADJUSTED_AVERAGE', 'METER_AVERAGE', 'INFORMED', 'NOT_METERED',
                                    'ESTIMATED', 'MINIMUM_FIXED', 'NO_CONSUMPTION', 'UNIT_AVERAGE',
                                    'SPECIAL_SITUATION_FIXED')),
    anomaly_id UUID,
    billed_reading_anomaly_id UUID,
    reading_id UUID,
    billed_reading INT CHECK (billed_reading >= 0),
    billed_reading_on DATE,
    average_consumption INT CHECK (average_consumption >= 0),
    average_months INT CHECK (average_months >= 1),
    average_source VARCHAR(20) CHECK (average_source IN ('HISTORY', 'CONNECTION_MINIMUM')),
    connection_minimum INT CHECK (connection_minimum >= 0),
    discarded_remainder INT NOT NULL CHECK (discarded_remainder >= 0),
    applied_action_month INT CHECK (applied_action_month IN (1, 2, 3)),
    -- so o esgoto tem percentual de coleta (reduz o volume)
    collection_percent NUMERIC(7,4) CHECK (collection_percent BETWEEN 0 AND 100),
    -- condominio: o registro do macro e o consumo rateado a este micro
    condominium_macro_record_id UUID,
    apportioned_consumption INT CHECK (apportioned_consumption >= 0),
    warnings JSONB,
    calculation_snapshot JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (billable OR billed_consumption = 0),
    CHECK (NOT billing_paused OR NOT billable),
    CHECK (service_kind = 'SEWER' OR collection_percent IS NULL)
);
ALTER TABLE consumption_records ADD CONSTRAINT uq_consumption_records_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE consumption_records ADD CONSTRAINT fk_consumption_unit
    FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
ALTER TABLE consumption_records ADD CONSTRAINT fk_consumption_anomaly
    FOREIGN KEY (tenant_id, anomaly_id) REFERENCES consumption_anomalies(tenant_id, id);
ALTER TABLE consumption_records ADD CONSTRAINT fk_consumption_reading_anomaly
    FOREIGN KEY (tenant_id, billed_reading_anomaly_id) REFERENCES reading_anomalies(tenant_id, id);
ALTER TABLE consumption_records ADD CONSTRAINT fk_consumption_reading
    FOREIGN KEY (tenant_id, reading_id) REFERENCES meter_readings(tenant_id, id);
ALTER TABLE consumption_records ADD CONSTRAINT fk_consumption_macro
    FOREIGN KEY (tenant_id, condominium_macro_record_id) REFERENCES consumption_records(tenant_id, id);
-- Idempotencia: refaturar a mesma unidade e referencia atualiza, nunca duplica
CREATE UNIQUE INDEX idx_consumption_unit_period ON consumption_records(tenant_id, unit_id, service_kind, reference);
-- Somas por periodo (consumo faturado por referencia, entrada do balanco hidrico)
CREATE INDEX idx_consumption_reference ON consumption_records(tenant_id, service_kind, reference);
CREATE INDEX idx_consumption_macro ON consumption_records(tenant_id, condominium_macro_record_id)
    WHERE condominium_macro_record_id IS NOT NULL;

-- ========================================================================
-- ISOLAMENTO E AUDITORIA
-- ========================================================================

SELECT register_tenant_table('meters');
SELECT register_tenant_table('meter_installations');
SELECT register_tenant_table('reading_anomalies');
SELECT register_tenant_table('consumption_anomalies');
SELECT register_tenant_table('meter_readings');
SELECT register_tenant_table('consumption_records');
