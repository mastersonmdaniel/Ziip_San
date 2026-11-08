-- ============================================================================
-- ED-86 (PENDENTE DE NUMERACAO): gestao de pressao e controle ativo de
-- vazamento (OPE-04, OPE-17).
-- SQL sem numero: prefixo ED-86__ em db/pendente/ (regra da cascata).
-- Faixa Flyway propria reservada no SS3 do plano: V22_x (losses, amplia
-- V2_2/V2_10, mesma faixa da ED-85) — o numero concreto so e atribuido
-- na integracao.
-- Depende de: ED-85 (loss_dmc_profiles — DMC referenciado por FK
-- composta; a UNIQUE (tenant_id, id) dela esta declarada na entrega da
-- ED-85), ED-17 (pontos de medicao referenciados PELO CODIGO estavel;
-- sem FK entre modulos, como na ED-85), ED-92 (leak_detection_findings —
-- o achado continua sendo da ED-92; aqui so a LIGACAO com a campanha,
-- pelo codigo do achado, sem tabela de achado paralela).
-- FRONTEIRA DURA: pressure.min_mca / pressure.max_mca SEM valor de
-- fabrica enquanto a ABNT NBR 12218:2017 nao for conferida — as colunas
-- min_mca/max_mca de pressure_zones sao NOT NULL e SEM DEFAULT: a zona
-- so nasce com os limites resolvidos do parametro do tenant.
-- Licao da C3 aplicada: TODA FK composta (tenant_id, X) tem a UNIQUE
-- (tenant_id, id) correspondente declarada NESTA entrega.
-- CORRECAO POS-REVISAO (ED-86, ressalvas D2/D3 da revisao
-- independente): (D2) o vinculo de reclamacao amarra zona x ponto por
-- FK composta (tenant_id, zone_id, point_id) contra a UNIQUE
-- (tenant_id, zone_id, id) de pressure_points — zone_id divergente da
-- zona do ponto e RECUSADO pelo banco; (D3) CHECKs de consistencia
-- aritmetica dos retratos em tabela unica (ganho da campanha =
-- antes - depois; liquida do retrato noturno = minima - legitimo;
-- desvio do alarme = |observada - limite|; contadores e desfecho do
-- vinculo coerentes). O que nao e expressivel em tabela unica
-- (percentual do ganho — escala/arredondamento; menor pressao contra
-- o minimo da zona — cruzamento de tabelas) ficou nomeado na
-- P-ED86-01 do relatorio.
-- NENHUM DADO SEMEADO: limites, metas (leak.repair_phase_targets_hours)
-- e ciclos (leak.survey_cycle_days_by_dmc_class) sao PARAMETROS do
-- tenant; nenhuma linha nasce neste SQL.
-- ============================================================================
SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- Zona de pressao (PRESSURE_ZONE) com os limites resolvidos do tenant.
CREATE TABLE IF NOT EXISTS pressure_zones (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    min_mca NUMERIC NOT NULL CHECK (min_mca >= 0),
    max_mca NUMERIC NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT uq_pressure_zones_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_pressure_zones_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_pressure_zones_limits CHECK (max_mca > min_mca)
);
SELECT register_tenant_table('pressure_zones');

-- Ponto de controle (PRESSURE_POINT): pertence a zona; a serie vem do
-- ponto de medicao da ED-17 referenciado pelo codigo estavel (sem FK
-- entre modulos — a existencia e conferida pela aplicacao, fail-closed).
CREATE TABLE IF NOT EXISTS pressure_points (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    zone_id UUID NOT NULL,
    code TEXT NOT NULL,
    measuring_point_code TEXT NOT NULL,
    location_text TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_pressure_points_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_pressure_points_tenant_zone_id UNIQUE (tenant_id, zone_id, id),
    CONSTRAINT uq_pressure_points_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_pressure_points_zone FOREIGN KEY (tenant_id, zone_id)
        REFERENCES pressure_zones (tenant_id, id)
);
SELECT register_tenant_table('pressure_points');

-- Alarme de pressao baixa/alta: retrato da amostra que disparou.
CREATE TABLE IF NOT EXISTS pressure_alarms (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    point_id UUID NOT NULL,
    alarm_kind TEXT NOT NULL CHECK (alarm_kind IN ('LOW','HIGH')),
    observed_mca NUMERIC NOT NULL CHECK (observed_mca >= 0),
    limit_mca NUMERIC NOT NULL CHECK (limit_mca >= 0),
    deviation_mca NUMERIC NOT NULL CHECK (deviation_mca > 0),
    observed_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_pressure_alarms_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_pressure_alarms_deviation CHECK (deviation_mca = ABS(observed_mca - limit_mca)),
    CONSTRAINT fk_pressure_alarms_point FOREIGN KEY (tenant_id, point_id)
        REFERENCES pressure_points (tenant_id, id)
);
SELECT register_tenant_table('pressure_alarms');

-- Ajuste de valvula redutora de pressao por OS: quem, quando, motivo,
-- valor anterior e valor novo (aceite literal). work_order_id sem FK
-- (a OS vive no motor de OS; existencia conferida pela aplicacao).
CREATE TABLE IF NOT EXISTS prv_adjustments (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    zone_id UUID NOT NULL,
    prv_code TEXT NOT NULL,
    work_order_id UUID NOT NULL,
    adjusted_by UUID NOT NULL,
    adjusted_at TIMESTAMP NOT NULL,
    reason TEXT NOT NULL CHECK (btrim(reason) <> ''),
    previous_setpoint_mca NUMERIC NOT NULL CHECK (previous_setpoint_mca >= 0),
    new_setpoint_mca NUMERIC NOT NULL CHECK (new_setpoint_mca >= 0),
    CONSTRAINT uq_prv_adjustments_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_prv_adjustments_change CHECK (new_setpoint_mca <> previous_setpoint_mca),
    CONSTRAINT fk_prv_adjustments_zone FOREIGN KEY (tenant_id, zone_id)
        REFERENCES pressure_zones (tenant_id, id)
);
SELECT register_tenant_table('prv_adjustments');

-- Estatistica por arco (trecho de rede): pressao x rompimentos do
-- periodo — insumo da correlacao; nao e cadastro de rede (o arco e
-- referenciado pelo codigo do GIS, sem FK).
CREATE TABLE IF NOT EXISTS pressure_arc_stats (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    arc_id TEXT NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    mean_pressure_mca NUMERIC NOT NULL CHECK (mean_pressure_mca >= 0),
    max_pressure_mca NUMERIC NOT NULL,
    break_count INTEGER NOT NULL CHECK (break_count >= 0),
    length_km NUMERIC CHECK (length_km IS NULL OR length_km > 0),
    CONSTRAINT uq_pressure_arc_stats_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_pressure_arc_stats_arc_period UNIQUE (tenant_id, arc_id, period_start, period_end),
    CONSTRAINT ck_pressure_arc_stats_period CHECK (period_end >= period_start),
    CONSTRAINT ck_pressure_arc_stats_max CHECK (max_pressure_mca >= mean_pressure_mca)
);
SELECT register_tenant_table('pressure_arc_stats');

-- Vinculo da reclamacao de pouca agua (protocolo do SAC da ED-35, por
-- codigo, sem FK) com a apuracao de pressao da zona/ponto. Correcao
-- D2: a FK do ponto e COMPOSTA (tenant_id, zone_id, point_id) — o
-- zone_id gravado tem de ser a zona do proprio ponto referenciado;
-- par zona x ponto divergente e recusado pelo banco.
CREATE TABLE IF NOT EXISTS low_water_complaint_links (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    protocol_ref TEXT NOT NULL,
    zone_id UUID NOT NULL,
    point_id UUID NOT NULL,
    complained_at TIMESTAMP NOT NULL,
    assessed_at TIMESTAMP NOT NULL,
    outcome TEXT NOT NULL CHECK (outcome IN
        ('CORROBORATED_LOW_PRESSURE','NOT_CORROBORATED','NO_DATA')),
    lowest_observed_mca NUMERIC CHECK (lowest_observed_mca IS NULL OR lowest_observed_mca >= 0),
    samples_count INTEGER NOT NULL CHECK (samples_count >= 0),
    low_samples_count INTEGER NOT NULL CHECK (low_samples_count >= 0),
    CONSTRAINT uq_low_water_links_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_low_water_links_nodata CHECK ((outcome <> 'NO_DATA')
        OR (lowest_observed_mca IS NULL AND samples_count = 0)),
    CONSTRAINT ck_low_water_links_counts CHECK (low_samples_count <= samples_count),
    CONSTRAINT ck_low_water_links_outcome CHECK (
        (outcome = 'CORROBORATED_LOW_PRESSURE' AND samples_count > 0
            AND low_samples_count > 0 AND lowest_observed_mca IS NOT NULL)
        OR (outcome = 'NOT_CORROBORATED' AND samples_count > 0
            AND low_samples_count = 0 AND lowest_observed_mca IS NOT NULL)
        OR (outcome = 'NO_DATA' AND samples_count = 0
            AND low_samples_count = 0 AND lowest_observed_mca IS NULL)),
    CONSTRAINT fk_low_water_links_zone FOREIGN KEY (tenant_id, zone_id)
        REFERENCES pressure_zones (tenant_id, id),
    CONSTRAINT fk_low_water_links_point FOREIGN KEY (tenant_id, zone_id, point_id)
        REFERENCES pressure_points (tenant_id, zone_id, id)
);
SELECT register_tenant_table('low_water_complaint_links');

-- Retrato da vazao noturna por DMC (saida do NightFlowAnalyzer da
-- ED-85 gravada): insumo da fila de suspeitas e das medicoes
-- antes/depois da campanha.
CREATE TABLE IF NOT EXISTS leak_night_flow_snapshots (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    dmc_profile_id UUID NOT NULL,
    measured_on DATE NOT NULL,
    minimum_night_flow_l_per_h NUMERIC NOT NULL CHECK (minimum_night_flow_l_per_h >= 0),
    legit_night_use_total_l_per_h NUMERIC NOT NULL CHECK (legit_night_use_total_l_per_h >= 0),
    net_night_flow_l_per_h NUMERIC NOT NULL,
    samples_in_window INTEGER NOT NULL CHECK (samples_in_window > 0),
    CONSTRAINT uq_leak_nf_snapshots_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_leak_nf_snapshots_net CHECK (net_night_flow_l_per_h
        = minimum_night_flow_l_per_h - legit_night_use_total_l_per_h),
    CONSTRAINT uq_leak_nf_snapshots_dmc_day UNIQUE (tenant_id, dmc_profile_id, measured_on),
    CONSTRAINT fk_leak_nf_snapshots_dmc FOREIGN KEY (tenant_id, dmc_profile_id)
        REFERENCES loss_dmc_profiles (tenant_id, id)
);
SELECT register_tenant_table('leak_night_flow_snapshots');

-- Campanha de pesquisa de vazamento nao visivel por DMC (OPE-17):
-- hipotese, equipamento, equipe, medicoes antes/depois e ganho.
CREATE TABLE IF NOT EXISTS leak_survey_campaigns (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code TEXT NOT NULL,
    dmc_profile_id UUID NOT NULL,
    dmc_class TEXT NOT NULL,
    hypothesis TEXT NOT NULL CHECK (btrim(hypothesis) <> ''),
    equipment JSONB NOT NULL DEFAULT '[]'::jsonb,
    team_member_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
    status TEXT NOT NULL DEFAULT 'PLANNED' CHECK (status IN
        ('PLANNED','IN_PROGRESS','COMPLETED','CANCELLED')),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    before_net_night_flow_l_per_h NUMERIC,
    after_net_night_flow_l_per_h NUMERIC,
    gain_l_per_h NUMERIC,
    gain_percent NUMERIC,
    CONSTRAINT uq_leak_campaigns_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_leak_campaigns_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_leak_campaigns_completed CHECK ((status <> 'COMPLETED')
        OR (before_net_night_flow_l_per_h IS NOT NULL
            AND after_net_night_flow_l_per_h IS NOT NULL AND gain_l_per_h IS NOT NULL)),
    CONSTRAINT ck_leak_campaigns_gain CHECK (gain_l_per_h IS NULL
        OR (before_net_night_flow_l_per_h IS NOT NULL
            AND after_net_night_flow_l_per_h IS NOT NULL
            AND gain_l_per_h = before_net_night_flow_l_per_h - after_net_night_flow_l_per_h)),
    CONSTRAINT fk_leak_campaigns_dmc FOREIGN KEY (tenant_id, dmc_profile_id)
        REFERENCES loss_dmc_profiles (tenant_id, id)
);
SELECT register_tenant_table('leak_survey_campaigns');

-- Evidencia da campanha (referencia externa: documento/foto/arquivo).
CREATE TABLE IF NOT EXISTS leak_survey_campaign_evidence (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    campaign_id UUID NOT NULL,
    evidence_ref TEXT NOT NULL,
    CONSTRAINT uq_leak_evidence_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_leak_evidence_campaign FOREIGN KEY (tenant_id, campaign_id)
        REFERENCES leak_survey_campaigns (tenant_id, id)
);
SELECT register_tenant_table('leak_survey_campaign_evidence');

-- Ligacao campanha x achado: o achado E o leak_detection_findings da
-- ED-92 (DAD-27), referenciado pelo codigo unico do tenant — nenhuma
-- tabela de achado e recriada aqui e o reparo continua so na OS.
CREATE TABLE IF NOT EXISTS leak_survey_campaign_findings (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    campaign_id UUID NOT NULL,
    finding_code TEXT NOT NULL,
    CONSTRAINT uq_leak_campaign_findings_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_leak_campaign_findings_link UNIQUE (tenant_id, campaign_id, finding_code),
    CONSTRAINT fk_leak_campaign_findings_campaign FOREIGN KEY (tenant_id, campaign_id)
        REFERENCES leak_survey_campaigns (tenant_id, id)
);
SELECT register_tenant_table('leak_survey_campaign_findings');

-- Tempo de reparo por fases (OPE-17): conhecimento, localizacao e
-- reparo. incident_id/work_order_id sem FK (ocorrencia e da ED-92 e a
-- OS do motor de OS; retrato das fases, como o anexo JOR-33 da ED-85).
CREATE TABLE IF NOT EXISTS leak_repair_timelines (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    incident_id UUID,
    work_order_id UUID,
    started_at TIMESTAMP NOT NULL,
    known_at TIMESTAMP NOT NULL,
    located_at TIMESTAMP,
    repaired_at TIMESTAMP,
    CONSTRAINT uq_leak_timelines_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_leak_timelines_known CHECK (known_at >= started_at),
    CONSTRAINT ck_leak_timelines_located CHECK (located_at IS NULL OR located_at >= known_at),
    CONSTRAINT ck_leak_timelines_repaired CHECK (repaired_at IS NULL
        OR (located_at IS NOT NULL AND repaired_at >= located_at))
);
SELECT register_tenant_table('leak_repair_timelines');

-- Fila de suspeitas ponderada pela vazao noturna: ultimo retrato de
-- cada DMC com sinal positivo, peso = vazao liquida por ligacao
-- (ligacoes do perfil da ED-85), maior peso primeiro.
CREATE OR REPLACE VIEW leak_suspect_queue AS
SELECT s.tenant_id, s.dmc_profile_id, p.connections_count,
       s.measured_on, s.net_night_flow_l_per_h,
       (s.net_night_flow_l_per_h / NULLIF(p.connections_count, 0)) AS weight_l_per_h_per_connection
  FROM leak_night_flow_snapshots s
  JOIN loss_dmc_profiles p
    ON p.tenant_id = s.tenant_id AND p.id = s.dmc_profile_id
 WHERE s.net_night_flow_l_per_h > 0
   AND s.measured_on = (SELECT MAX(s2.measured_on)
                          FROM leak_night_flow_snapshots s2
                         WHERE s2.tenant_id = s.tenant_id
                           AND s2.dmc_profile_id = s.dmc_profile_id)
 ORDER BY weight_l_per_h_per_connection DESC, s.net_night_flow_l_per_h DESC;
