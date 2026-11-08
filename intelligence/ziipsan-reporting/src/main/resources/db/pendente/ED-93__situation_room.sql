-- ============================================================================
-- ED-93 (PENDENTE DE NUMERACAO): sala de situacao operacional em tempo
-- quase real (OPE-22; RNF de latencia no ADR-0012; JOR-19).
-- SQL sem numero Flyway: prefixo ED-93__ em db/pendente/ (regra 4 da
-- cascata). Nenhum numero reservado da sequencia global e usado aqui
-- e nenhuma migration publicada e tocada. A faixa do reporting
-- (V15_x) e coletiva e os nominais V15_1/V15_2 estao ocupados; o numero
-- concreto so e atribuido na integracao, lido da main.
--
-- Conteudo (espelho das classes de br.com.ziipsan.reporting.situation):
--   * ops_situation_state: a TABELA DE ESTADO RESUMIDA E NATIVA — a
--     unica fonte do console. Uma linha por item visivel (ocorrencia
--     aberta, OS aberta, equipe, alarme, reservatorio, unidade fora
--     de operacao), gravada pela projecao (SituationProjector) e lida
--     pelo console (SituationConsoleService). O console NUNCA consulta
--     as tabelas primarias nem o Superset (guarda SituationSourceGuard).
--   * ops_console_indicator_bindings: ligacao DECLARADA console ->
--     indicador do registro da P3.41 (JOR-19), por tenant — espelho da
--     panel_indicator_bindings da ED-49. Nenhuma fonte lista os
--     indicadores do console, logo NENHUMA ligacao nasce pronta.
-- Licao da C3 aplicada: as duas tabelas tem UNIQUE (tenant_id, id),
-- prontas para FK composta (tenant_id, X) futura. Sem FK para
-- indicator_definitions, pelo precedente da ED-48/ED-49: o codigo e
-- referencia logica conferida pelo servico contra o registro.
-- Segunda barreira de status (correcao D3 da revisao ED-93-REV-1):
-- ck_ops_situation_status_by_kind fecha o vocabulario de
-- status_label POR item_kind aos valores que o SituationProjector
-- realmente grava, conferidos nas fontes: INCIDENT = OPEN/CONTROLLED
-- (NetworkIncident.Status da ED-92 projetado; restabelecida e
-- encerrada NAO sao ocorrencia aberta); WORK_ORDER = os 7 status
-- NAO terminais de ServiceOrderStatus da P1.31 (os terminais
-- CLOSED_* nunca entram); CREW = POSITIONED/UNPOSITIONED;
-- TELEMETRY_ALARM = OPEN/ACKNOWLEDGED (MeterAlarm.Status da ED-17
-- projetado; resolvido nao entra); RESERVOIR = as 4 faixas de
-- ReservoirLevel.band(); UNIT_OUTAGE = OUT_OF_OPERATION. Uma linha
-- gravada direto na tabela com rotulo fora do vocabulario do seu
-- kind (ex.: ocorrencia ja restabelecida/encerrada) e recusada pelo
-- banco — o filtro nao vive so no projetor.
-- NENHUMA LINHA SEMEADA: sem item, sem ligacao, sem limiar e sem teto
-- de latencia — o teto numerico do RNF e [SEM FONTE] nas fontes e e
-- configuracao da implantacao, nao dado deste SQL. Os 14 valores
-- abertos da P3.9 NAO sao tocados.
-- ============================================================================

CREATE TABLE IF NOT EXISTS ops_situation_state (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    item_kind TEXT NOT NULL CHECK (item_kind IN
        ('INCIDENT','WORK_ORDER','CREW','TELEMETRY_ALARM','RESERVOIR','UNIT_OUTAGE')),
    item_ref TEXT NOT NULL,
    title TEXT NOT NULL,
    status_label TEXT NOT NULL,
    attention BOOLEAN NOT NULL DEFAULT FALSE,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    source_at TIMESTAMPTZ NOT NULL,
    projected_at TIMESTAMPTZ NOT NULL,
    detail JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT uq_ops_situation_state_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_ops_situation_state_item UNIQUE (tenant_id, item_kind, item_ref),
    CONSTRAINT ck_ops_situation_coords_pair CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT ck_ops_situation_lat CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90)),
    CONSTRAINT ck_ops_situation_lon CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180)),
    CONSTRAINT ck_ops_situation_projection_order CHECK (projected_at >= source_at),
    CONSTRAINT ck_ops_situation_status_by_kind CHECK (
        (item_kind = 'INCIDENT' AND status_label IN ('OPEN', 'CONTROLLED'))
        OR (item_kind = 'WORK_ORDER' AND status_label IN ('DRAFT', 'OPEN', 'SCHEDULED', 'IN_EXECUTION', 'EXECUTED', 'WAITING_DEPENDENCY', 'PENDING_APPROVAL'))
        OR (item_kind = 'CREW' AND status_label IN ('POSITIONED', 'UNPOSITIONED'))
        OR (item_kind = 'TELEMETRY_ALARM' AND status_label IN ('OPEN', 'ACKNOWLEDGED'))
        OR (item_kind = 'RESERVOIR' AND status_label IN ('UNKNOWN', 'LOW', 'HIGH', 'WITHIN_DECLARED'))
        OR (item_kind = 'UNIT_OUTAGE' AND status_label IN ('OUT_OF_OPERATION'))
    )
);
SELECT register_tenant_table('ops_situation_state');

CREATE INDEX IF NOT EXISTS idx_ops_situation_state_kind
    ON ops_situation_state (tenant_id, item_kind);

CREATE TABLE IF NOT EXISTS ops_console_indicator_bindings (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    indicator_code TEXT NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    CONSTRAINT uq_ops_console_indicator_bindings_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_ops_console_indicator_bindings_key UNIQUE (tenant_id, indicator_code)
);
SELECT register_tenant_table('ops_console_indicator_bindings');
