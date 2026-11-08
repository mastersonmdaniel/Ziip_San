-- ============================================================================
-- ED-94 (PENDENTE DE NUMERACAO): deteccao de anomalia explicavel em
-- series (OPE-36; ADR-0042) — alertas propostos, revisao humana e o
-- desfecho pos-revisao.
-- SQL sem numero: prefixo ED-94__ em db/pendente/ (regra da cascata).
-- Faixa Flyway propria reservada no SS3 do plano: V22_x (losses, amplia
-- V2_2 e V2_10; a linha da faixa nomeia expressamente "anomalia em
-- series" entre as entregas ED-78/ED-85/ED-86/ED-94/ED-95) — o numero
-- concreto so e atribuido na integracao.
-- Depende de: NENHUMA tabela por FK entre modulos — a serie e
-- referenciada pelo codigo estavel da fonte (series_ref: DMC da ED-85,
-- ponto de medicao da ED-17 ou ligacao), como a ED-85 referencia os
-- pontos da ED-17 pelo codigo. A OS/pesquisa do desfecho vive no motor
-- de OS (P1.31): aqui guarda-se so a REFERENCIA devolvida por ele.
-- Licao da C3 aplicada: a FK composta (tenant_id, alert_id) de
-- anomaly_reviews tem a UNIQUE (tenant_id, id) correspondente
-- declarada em anomaly_alerts NESTA entrega.
-- FRONTEIRA DO ADR-0042 GRAVADA NO BANCO:
--   * anomaly_alerts.status nasce PROPOSED (DEFAULT) e o CHECK so
--     admite PROPOSED/CONFIRMED/DISMISSED;
--   * anomaly_reviews tem UNIQUE (tenant_id, alert_id): UMA revisao
--     por alerta — nao existe segunda revisao que "desdescarte";
--   * o CHECK ck_anomaly_reviews_follow_up admite SOMENTE as tres
--     formas finais da revisao (correcao R3 da revisao cruzada):
--     descartado = kind e ref NULL; confirmacao de ciencia = kind e
--     ref NULL; desfecho = kind E ref NOT NULL. Nao existe forma
--     "pela metade" (kind sem ref): a referencia volta gravada na
--     mesma linha, o estado final tem os dois ou nenhum.
-- NENHUM DADO SEMEADO: regras ligadas e limiares sao PARAMETROS do
-- tenant (anomaly.rules_enabled / anomaly.sensitivity); nenhum valor
-- de limiar nasce aqui. O bloco de parameter_definitions ao final esta
-- COMENTADO (pendencia de integracao, forma da V4_4 SS7).
-- ============================================================================
SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- Alerta proposto pelo motor de regras: auto-contido para auditoria
-- (trecho da serie em evidence, numeros da comparacao, snapshot dos
-- parametros anomaly.* usados na avaliacao).
CREATE TABLE IF NOT EXISTS anomaly_alerts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    series_kind TEXT NOT NULL CHECK (series_kind IN
        ('NIGHT_FLOW_DMC','PRESSURE_POINT','CONSUMPTION_CONNECTION')),
    series_ref TEXT NOT NULL,
    unit TEXT NOT NULL,
    rule_code TEXT NOT NULL CHECK (rule_code IN
        ('SAME_HOUR_WEEKDAY_DEVIATION','CONTINUOUS_CONSUMPTION',
         'ZERO_CONSUMPTION','SUDDEN_PRESSURE_DROP')),
    detected_at TIMESTAMP NOT NULL,
    window_start TIMESTAMP NOT NULL,
    window_end TIMESTAMP NOT NULL CHECK (window_end >= window_start),
    observed_value NUMERIC NOT NULL,
    baseline_median NUMERIC,
    baseline_sigma NUMERIC,
    deviation_sigma NUMERIC,
    threshold_description TEXT NOT NULL,
    confidence NUMERIC NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
    explanation TEXT NOT NULL,
    evidence JSONB NOT NULL,
    parameter_snapshot TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PROPOSED' CHECK (status IN
        ('PROPOSED','CONFIRMED','DISMISSED')),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_anomaly_alerts_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('anomaly_alerts');

CREATE INDEX IF NOT EXISTS idx_anomaly_alerts_tenant_status
    ON anomaly_alerts (tenant_id, status, detected_at);
CREATE INDEX IF NOT EXISTS idx_anomaly_alerts_tenant_series
    ON anomaly_alerts (tenant_id, series_kind, series_ref, detected_at);

-- Revisao humana (ADR-0042): uma por alerta, com revisor,
-- justificativa e instante; o desfecho pos-revisao (OS ou pesquisa)
-- so existe em CONFIRMED com tipo declarado, e a referencia criada
-- pelo motor de OS volta gravada aqui.
CREATE TABLE IF NOT EXISTS anomaly_reviews (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    alert_id UUID NOT NULL,
    reviewer_id TEXT NOT NULL,
    decision TEXT NOT NULL CHECK (decision IN ('CONFIRMED','DISMISSED')),
    rationale TEXT NOT NULL,
    reviewed_at TIMESTAMP NOT NULL,
    follow_up_kind TEXT CHECK (follow_up_kind IS NULL OR follow_up_kind IN
        ('SERVICE_ORDER','FIELD_SURVEY')),
    follow_up_ref TEXT,
    CONSTRAINT uq_anomaly_reviews_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_anomaly_reviews_tenant_alert UNIQUE (tenant_id, alert_id),
    CONSTRAINT ck_anomaly_reviews_follow_up CHECK (
        (decision = 'DISMISSED' AND follow_up_kind IS NULL AND follow_up_ref IS NULL)
        OR (decision = 'CONFIRMED' AND follow_up_kind IS NULL AND follow_up_ref IS NULL)
        OR (decision = 'CONFIRMED' AND follow_up_kind IS NOT NULL AND follow_up_ref IS NOT NULL)),
    CONSTRAINT fk_anomaly_reviews_alert FOREIGN KEY (tenant_id, alert_id)
        REFERENCES anomaly_alerts (tenant_id, id)
);
SELECT register_tenant_table('anomaly_reviews');

-- ----------------------------------------------------------------------------
-- PENDENCIA DE INTEGRACAO: registro das chaves em parameter_definitions
-- (forma literal da V4_4 SS7; descomentar e numerar na integracao).
-- Nenhum valor padrao e proposto: as duas chaves nascem SEM default —
-- ausencia e erro explicito (MissingParameterException), como decide o
-- desenho da ED-94 (nenhum limiar presumido como verdade de dominio).
--
-- INSERT INTO parameter_definitions (key, module, value_type, default_value,
--     scope, category, description) VALUES
--   ('anomaly.rules_enabled', 'losses', 'STRING', NULL, 'TENANT', 'c',
--    'Regras de anomalia ligadas (codigos separados por virgula ou ALL)'),
--   ('anomaly.sensitivity', 'losses', 'STRING', NULL, 'TENANT', 'c',
--    'Limiares das regras: deviationK;minBaselineSamples;continuousHours;zeroHours;pressureDropPercent');
-- ----------------------------------------------------------------------------
