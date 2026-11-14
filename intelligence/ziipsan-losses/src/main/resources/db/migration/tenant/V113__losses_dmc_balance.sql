-- ============================================================================
-- V113 — ED-85: modulo de perdas operacional — DMC, vazao minima noturna e
-- balanco por DMC (OPE-01, OPE-05, JOR-34; COE-11; ficha de perdas da
-- NR 9/ANA).
--
-- NUMERACAO (complemento do Ciclo 4 do programa 5x PDCA GIS; recomendacao
-- formal da revisao independente do ciclo, REVISAO-CICLO-4.md seccao 4.3,
-- pendencia P-PDCAGIS4-02): este SQL vivia em db/pendente/ como
-- ED-85__losses_dmc_balance.sql, com a faixa V22_x reservada no cabecalho.
-- A faixa esta ENCERRADA: a NOTA-INTEGRACAO-NUMERACAO.md (autoritativa,
-- 08/11/2026) e a politica da P3.12 (TenantMigrationVersionPolicy) mandam
-- TODA migration nova de tenant para a sequencia global unica aberta pela
-- V100 — versao maior que a maior publicada, sem faixas de modulo. Maior
-- publicada na base desta numeracao: V112 (lista migration-policy/
-- published-tenant-migrations.txt, 113 entradas). A ED-85 e o II-6 sao
-- numerados NO MESMO ACTO: esta ED-85 recebe V113, o numero imediatamente
-- anterior ao V114 do II-6 (V114__dma_aggregation.sql), cuja FK
-- fk_ii6_dma_member_dmc referencia loss_dmc_profiles, tabela criada AQUI
-- — sem esta migration aplicada antes, o II-6 falha nessa FK (provado em
-- BD na revisao do Ciclo 4, nos dois sentidos). A lista de publicadas e
-- actualizada no mesmo acto com as duas linhas, nesta ordem (nota
-- autoritativa, seccao 2). Todas as dependencias versionadas desta
-- migration sao <= V112 (ver paragrafo seguinte), logo a travessia esta
-- completa quando ela corre.
--
-- BASES QUE JA CORRERAM O FICHEIRO PENDENTE: nenhuma pela travessia (o
-- TenantMigrator so aplica db/migration/tenant; o historico Flyway nao
-- contem a ED-85). O SQL e defensivo (CREATE TABLE IF NOT EXISTS +
-- register_tenant_table; a vista final usa CREATE OR REPLACE), pelo que
-- uma aplicacao manual previa fora da travessia e absorvida sem erro.
-- Depende de: V2_2 (dma_district, modulo losses — registro que esta
-- entrega ESTENDE, nao recria), V13_2 (gis_zone — fonte unica da
-- geometria; zona DMA referenciada, nunca duplicada), ED-17 (pontos de
-- medicao referenciados PELO CODIGO estavel; sem FK entre modulos,
-- como o servico de OS referencia alvos), ED-92 (network_incidents —
-- consumida pela aplicacao; o balanco guarda o SNAPSHOT dos totais do
-- anexo JOR-33, sem FK, porque o anexo e foto do periodo).
-- Licao da C3 aplicada: TODA FK composta (tenant_id, X) tem a UNIQUE
-- (tenant_id, id) correspondente declarada NESTA entrega; as dos pais
-- pre-existentes (dma_district, gis_zone) ja existem nas migrations
-- deles (V2_3/V13_2 referenciam dma_district(tenant_id, id) e
-- gis_zone declara uq_gis_zone_tenant_id).
-- NENHUM DADO SEMEADO: metas, janela, uso legitimo e metodos sao
-- PARAMETROS do tenant (loss.*); nenhum valor de norma nasce aqui.
-- ============================================================================
SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- Perfil operacional do DMC: extensao 1:1 de dma_district (V2_2) com o
-- vinculo a zona DMA de gis_zone (fonte unica: um DMC por zona e um
-- perfil por dma_district, pelas UNIQUEs abaixo).
CREATE TABLE IF NOT EXISTS loss_dmc_profiles (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    dma_id UUID NOT NULL,
    gis_zone_id UUID NOT NULL,
    sector_code TEXT NOT NULL,
    connections_count INTEGER NOT NULL DEFAULT 0 CHECK (connections_count >= 0),
    mains_length_km NUMERIC CHECK (mains_length_km IS NULL OR mains_length_km >= 0),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT uq_loss_dmc_profiles_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_loss_dmc_profiles_tenant_dma UNIQUE (tenant_id, dma_id),
    CONSTRAINT uq_loss_dmc_profiles_tenant_zone UNIQUE (tenant_id, gis_zone_id),
    CONSTRAINT fk_loss_dmc_dma FOREIGN KEY (tenant_id, dma_id)
        REFERENCES dma_district (tenant_id, id),
    CONSTRAINT fk_loss_dmc_zone FOREIGN KEY (tenant_id, gis_zone_id)
        REFERENCES gis_zone (tenant_id, id)
);
SELECT register_tenant_table('loss_dmc_profiles');

-- Macromedidores de entrada/saida: vinculo ao ponto de medicao da ED-17
-- pelo codigo estavel do tenant (sem FK entre modulos).
CREATE TABLE IF NOT EXISTS loss_macro_meter_links (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    dmc_profile_id UUID NOT NULL,
    direction TEXT NOT NULL CHECK (direction IN ('INLET','OUTLET')),
    measuring_point_code TEXT NOT NULL,
    CONSTRAINT uq_loss_meter_links_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_loss_meter_link UNIQUE (tenant_id, dmc_profile_id, direction, measuring_point_code),
    CONSTRAINT fk_loss_meter_link_dmc FOREIGN KEY (tenant_id, dmc_profile_id)
        REFERENCES loss_dmc_profiles (tenant_id, id)
);
SELECT register_tenant_table('loss_macro_meter_links');

-- Volume autorizado nao faturado (OPE-05): um registro por uso na OS
-- operacional; work_order_id sem FK (padrao ED-92: a OS vive no motor
-- de OS e a existencia e conferida pela aplicacao).
CREATE TABLE IF NOT EXISTS loss_authorized_unbilled_volumes (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    dmc_profile_id UUID NOT NULL,
    work_order_id UUID NOT NULL,
    category TEXT NOT NULL CHECK (category IN
        ('NETWORK_FLUSHING','RESERVOIR_CLEANING','FIREFIGHTING','SOCIAL_TANKER')),
    estimate_method TEXT NOT NULL CHECK (estimate_method IN
        ('MEASURED','FLOW_TIME','FIXED_BY_SERVICE_TYPE')),
    volume_m3 NUMERIC NOT NULL CHECK (volume_m3 >= 0),
    measured_m3 NUMERIC CHECK (measured_m3 IS NULL OR measured_m3 >= 0),
    flow_litres_per_second NUMERIC CHECK (flow_litres_per_second IS NULL OR flow_litres_per_second >= 0),
    duration_seconds BIGINT CHECK (duration_seconds IS NULL OR duration_seconds >= 0),
    fixed_by_service_type_m3 NUMERIC CHECK (fixed_by_service_type_m3 IS NULL OR fixed_by_service_type_m3 >= 0),
    occurred_on DATE NOT NULL,
    CONSTRAINT uq_loss_unbilled_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_loss_unbilled_method_inputs CHECK (
        (estimate_method = 'MEASURED' AND measured_m3 IS NOT NULL)
        OR (estimate_method = 'FLOW_TIME' AND flow_litres_per_second IS NOT NULL AND duration_seconds IS NOT NULL)
        OR (estimate_method = 'FIXED_BY_SERVICE_TYPE' AND fixed_by_service_type_m3 IS NOT NULL)),
    CONSTRAINT fk_loss_unbilled_dmc FOREIGN KEY (tenant_id, dmc_profile_id)
        REFERENCES loss_dmc_profiles (tenant_id, id)
);
SELECT register_tenant_table('loss_authorized_unbilled_volumes');

-- Fornecimento em bloco (OPE-05): contrato que autoriza a exclusao da
-- agua tratada exportada do balanco.
CREATE TABLE IF NOT EXISTS loss_bulk_export_contracts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    contract_ref TEXT NOT NULL,
    delivery_point TEXT NOT NULL,
    macro_meter_point_code TEXT NOT NULL,
    contracted_volume_m3 NUMERIC NOT NULL CHECK (contracted_volume_m3 >= 0),
    CONSTRAINT uq_loss_bulk_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_loss_bulk_tenant_ref UNIQUE (tenant_id, contract_ref)
);
SELECT register_tenant_table('loss_bulk_export_contracts');

-- Balanco hidrico mensal por DMC: snapshot completo do periodo, com o
-- metodo de ajuste (JOR-34), o grau de confianca e o anexo JOR-33
-- gravados — e a entrada real que a ED-78 (NR 15) le.
CREATE TABLE IF NOT EXISTS loss_monthly_balances (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    dmc_profile_id UUID NOT NULL,
    period_year INTEGER NOT NULL,
    period_month INTEGER NOT NULL CHECK (period_month BETWEEN 1 AND 12),
    period_days INTEGER NOT NULL CHECK (period_days > 0),
    connections_count INTEGER NOT NULL CHECK (connections_count > 0),
    system_input_m3 NUMERIC NOT NULL CHECK (system_input_m3 >= 0),
    exported_m3 NUMERIC NOT NULL CHECK (exported_m3 >= 0 AND exported_m3 <= system_input_m3),
    net_input_m3 NUMERIC NOT NULL,
    billed_metered_adjusted_m3 NUMERIC NOT NULL CHECK (billed_metered_adjusted_m3 >= 0),
    billed_unmetered_m3 NUMERIC NOT NULL CHECK (billed_unmetered_m3 >= 0),
    authorized_unbilled_m3 NUMERIC NOT NULL CHECK (authorized_unbilled_m3 >= 0),
    authorized_consumption_m3 NUMERIC NOT NULL,
    billed_total_m3 NUMERIC NOT NULL,
    total_losses_m3 NUMERIC NOT NULL,
    apparent_losses_m3 NUMERIC CHECK (apparent_losses_m3 IS NULL OR apparent_losses_m3 >= 0),
    real_losses_m3 NUMERIC,
    non_revenue_water_m3 NUMERIC NOT NULL,
    non_revenue_percent NUMERIC NOT NULL,
    real_losses_l_per_conn_day NUMERIC,
    ili NUMERIC,
    confidence_grade TEXT NOT NULL CHECK (confidence_grade IN ('HIGH','MEDIUM','LOW')),
    target_status TEXT NOT NULL CHECK (target_status IN
        ('AT_OR_BELOW_TARGET','ABOVE_TARGET','NO_TARGET')),
    target_l_per_conn_day NUMERIC CHECK (target_l_per_conn_day IS NULL OR target_l_per_conn_day > 0),
    adjustment_method TEXT NOT NULL CHECK (adjustment_method IN ('NONE','PRO_RATA_DAILY_AVERAGE')),
    incident_closed_count BIGINT NOT NULL DEFAULT 0,
    incident_volume_known_count BIGINT NOT NULL DEFAULT 0,
    incident_lost_volume_m3 NUMERIC,
    incident_repair_known_count BIGINT NOT NULL DEFAULT 0,
    incident_avg_repair_minutes NUMERIC,
    CONSTRAINT uq_loss_balances_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_loss_balances_dmc_period UNIQUE (tenant_id, dmc_profile_id, period_year, period_month),
    CONSTRAINT fk_loss_balances_dmc FOREIGN KEY (tenant_id, dmc_profile_id)
        REFERENCES loss_dmc_profiles (tenant_id, id)
);
SELECT register_tenant_table('loss_monthly_balances');

-- Fila de prioridade (OPE-01): excesso sobre a meta, maior primeiro;
-- sem perda real ou sem meta, o DMC nao aparece (nao se ranqueia sem numero).
CREATE OR REPLACE VIEW loss_dmc_priority_queue AS
SELECT b.tenant_id, b.dmc_profile_id, b.period_year, b.period_month,
       b.real_losses_l_per_conn_day, b.target_l_per_conn_day,
       (b.real_losses_l_per_conn_day - b.target_l_per_conn_day) AS excess_l_per_conn_day,
       b.ili, b.confidence_grade
  FROM loss_monthly_balances b
 WHERE b.real_losses_l_per_conn_day IS NOT NULL AND b.target_l_per_conn_day IS NOT NULL
 ORDER BY excess_l_per_conn_day DESC, b.ili DESC NULLS LAST;
