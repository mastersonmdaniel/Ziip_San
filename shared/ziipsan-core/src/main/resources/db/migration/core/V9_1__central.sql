-- Core Migration — Version: 9.1
-- Description: P1.17 — Central Ziip v0 (schema `platform` + `platform_stats`).
--
-- Tabelas do plano v3.0 §3 (core `V9_1`): `platform.metric_definition`,
-- `platform.metric_point` (particao diaria), `platform.metric_daily`,
-- `platform.alert_rule`, `platform.alert_event`, `platform.config_item`;
-- role `ziipsan_central` (criada no init `infra/postgres/init/sql/security.sql`,
-- como `ziipsan_worker`: NOLOGIN aqui, LOGIN + senha so no deploy que a usar);
-- funcoes `platform_stats.*` (SECURITY DEFINER, so contagens e idades).
--
-- Isolamento (CENTRAL-ZIIP §10, ADR-0018): a Central NAO le dado pessoal
-- de tenant. `ziipsan_central` recebe SELECT so nas tabelas de `platform`
-- acima + EXECUTE nas funcoes `platform_stats.*`, e NENHUM privilegio em
-- schema de tenant (o provisionamento concede schemas de tenant so a
-- `ziipsan_app` e ao migrador; aqui nao ha GRANT nenhum para tenant).
-- Cada funcao `platform_stats.*` devolve so (tenant_id, chave, contagem,
-- idade): nunca nome, CNPJ, e-mail, telefone, documento, endereco ou
-- carga de outbox (payload). O teste de fronteira prova isso pelo nome
-- das colunas de retorno e pela execucao, nao pela intencao.
--
-- Particao diaria de `metric_point`: a particao do dia corrente e a do
-- dia seguinte nascem aqui; dias futuros nascem pela funcao
-- `platform.ensure_metric_point_partition(DATE)`, chamada pelo coletor
-- antes de inserir (a funcao tambem concede INSERT/SELECT na particao
-- nova a `ziipsan_app` e SELECT a `ziipsan_central`, porque particao
-- criada depois nao herda GRANT de particao antiga).
-- Retencao (CENTRAL-ZIIP §4): 90 dias de pontos e 24 meses de
-- `metric_daily`. Na v0 a retencao de pontos e por DELETE (a role da
-- aplicacao nao tem DROP; o DROP de particao pelo migrador fica para
-- o job de retencao da P3.15) e a de `metric_daily` por DELETE do dia
-- alem de 24 meses, ambos na consolidacao diaria do coletor.
-- `metric_daily.tenant_id` e nulo para o agregado global; a unicidade
-- usa COALESCE para o sentinela zero, porque UNIQUE trata NULL como
-- distinto e aceitaria duas linhas globais por chave e dia.
-- Alerta sem duplicar: indice unico parcial em `alert_event`
-- (rule_id, tenant coalescido) enquanto `resolved_at IS NULL` — um
-- segundo INSERT do mesmo alerta aberto falha em vez de duplicar, e o
-- coletor usa ON CONFLICT DO NOTHING sobre ele.

CREATE SCHEMA IF NOT EXISTS platform_stats;

-- ======================================================================
-- Catalogo de metricas (chave fechada: domain.object.measure)
-- ======================================================================

CREATE TABLE IF NOT EXISTS platform.metric_definition (
    metric_key VARCHAR(200) PRIMARY KEY,
    unit VARCHAR(50) NOT NULL,
    metric_type VARCHAR(20) NOT NULL CHECK (metric_type IN ('gauge', 'counter')),
    description VARCHAR(500) NOT NULL,
    version INTEGER NOT NULL DEFAULT 1 CHECK (version >= 1),
    owner VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Chaves operacionais da v0. Fontes existentes no banco (nada inventado
-- de modulo futuro): catologo de tenants, outbox, jornada de implantacao,
-- ciclo de vida, alertas e o proprio coletor. Fiscal, arrecadacao, Pix e
-- MFA nao tem fonte em banco nesta fase e entram como "nao medido" no
-- Health Score, nunca como numero inventado.
INSERT INTO platform.metric_definition (metric_key, unit, metric_type, description, version, owner)
VALUES
    ('platform.tenants.total', 'count', 'gauge', 'Tenants no catalogo', 1, 'platform'),
    ('platform.tenants.active', 'count', 'gauge', 'Tenants com status ACTIVE no catalogo', 1, 'platform'),
    ('platform.outbox.pending', 'count', 'gauge', 'Mensagens PENDING na outbox, por tenant e global', 1, 'platform'),
    ('platform.outbox.dead', 'count', 'gauge', 'Mensagens DEAD na outbox, por tenant e global', 1, 'platform'),
    ('platform.outbox.oldest_pending_age_s', 'seconds', 'gauge', 'Idade da mensagem PENDING mais antiga, por tenant e global', 1, 'platform'),
    ('platform.alerts.open', 'count', 'gauge', 'Alertas abertos, por severidade (rotulo)', 1, 'platform'),
    ('platform.onboarding.pending_steps', 'count', 'gauge', 'Passos PENDING da jornada de implantacao, por tenant', 1, 'platform'),
    ('platform.collector.last_run_age_s', 'seconds', 'gauge', 'Segundos desde a ultima coleta da Central (watchdog)', 1, 'platform')
ON CONFLICT (metric_key) DO NOTHING;

-- ======================================================================
-- Pontos (particao diaria) e agregado diario
-- ======================================================================

CREATE TABLE IF NOT EXISTS platform.metric_point (
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    metric_key VARCHAR(200) NOT NULL REFERENCES platform.metric_definition (metric_key),
    tenant_id UUID,
    metric_value NUMERIC NOT NULL,
    labels JSONB NOT NULL DEFAULT '{}',
    ts TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, ts)
) PARTITION BY RANGE (ts);

CREATE INDEX IF NOT EXISTS idx_metric_point_key_ts
    ON platform.metric_point (metric_key, ts DESC, tenant_id);

-- Funcao de particao (padrao da V8 para a outbox): idempotente, dona
-- migrador, SECURITY DEFINER, e concede o acesso da particao nova.
CREATE OR REPLACE FUNCTION platform.ensure_metric_point_partition(p_day DATE)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = platform, public
AS $fn$
DECLARE
    v_name TEXT := 'metric_point_' || to_char(p_day, 'YYYYMMDD');
    v_from TIMESTAMPTZ := (p_day::TIMESTAMP AT TIME ZONE 'UTC');
    v_to TIMESTAMPTZ := ((p_day + 1)::TIMESTAMP AT TIME ZONE 'UTC');
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relname = v_name) THEN
        BEGIN
            EXECUTE format('CREATE TABLE %I PARTITION OF platform.metric_point'
                           || ' FOR VALUES FROM (%L) TO (%L)',
                           v_name, v_from, v_to);
        EXCEPTION WHEN duplicate_table THEN
            NULL;
        END;
        EXECUTE format('GRANT SELECT, INSERT ON %I TO ziipsan_app', v_name);
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ziipsan_central') THEN
            EXECUTE format('GRANT SELECT ON %I TO ziipsan_central', v_name);
        END IF;
    END IF;
END
$fn$;

REVOKE ALL ON FUNCTION platform.ensure_metric_point_partition(DATE) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION platform.ensure_metric_point_partition(DATE) TO ziipsan_app;

SELECT platform.ensure_metric_point_partition((NOW() AT TIME ZONE 'UTC')::DATE);
SELECT platform.ensure_metric_point_partition(((NOW() AT TIME ZONE 'UTC')::DATE) + 1);

CREATE TABLE IF NOT EXISTS platform.metric_daily (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    metric_key VARCHAR(200) NOT NULL REFERENCES platform.metric_definition (metric_key),
    tenant_id UUID,
    day DATE NOT NULL,
    avg_value NUMERIC NOT NULL,
    min_value NUMERIC NOT NULL,
    max_value NUMERIC NOT NULL,
    point_count INTEGER NOT NULL CHECK (point_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_metric_daily_key_day_tenant
    ON platform.metric_daily (metric_key, day,
        (COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::UUID)));

-- ======================================================================
-- Regras e eventos de alerta
-- ======================================================================

CREATE TABLE IF NOT EXISTS platform.alert_rule (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    rule_key VARCHAR(200) NOT NULL UNIQUE,
    metric_key VARCHAR(200) NOT NULL REFERENCES platform.metric_definition (metric_key),
    comparator VARCHAR(10) NOT NULL CHECK (comparator IN ('>', '>=', '<', '<=', '=')),
    threshold NUMERIC NOT NULL,
    consecutive_readings INTEGER NOT NULL DEFAULT 1 CHECK (consecutive_readings >= 1),
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    scope VARCHAR(20) NOT NULL CHECK (scope IN ('GLOBAL', 'TENANT')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    snooze_until TIMESTAMPTZ,
    owner VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS platform.alert_event (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    rule_id UUID NOT NULL REFERENCES platform.alert_rule (id),
    tenant_id UUID,
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    opened_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    acknowledged_by UUID,
    acknowledged_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    notified_channels TEXT[] NOT NULL DEFAULT '{}',
    CHECK (status <> 'RESOLVED' OR resolved_at IS NOT NULL),
    CHECK (status <> 'ACKNOWLEDGED' OR acknowledged_at IS NOT NULL)
);

-- Um alerta aberto por regra e tenant: o segundo INSERT falha (o coletor
-- usa ON CONFLICT DO NOTHING). Tenant nulo = alerta global, com o mesmo
-- sentinela de `metric_daily`.
CREATE UNIQUE INDEX IF NOT EXISTS uq_alert_event_open_rule_tenant
    ON platform.alert_event (rule_id,
        (COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::UUID)))
    WHERE resolved_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_alert_event_open
    ON platform.alert_event (status, opened_at DESC) WHERE resolved_at IS NULL;

-- Regra do watchdog do coletor (CENTRAL-ZIIP §5: alerta se a ultima
-- coleta passar de 15 min). A avaliacao e temporal (config_item, nao
-- serie): a regra existe para o evento ter regra, severidade e dono, e
-- para a aba Alertas a exibir como qualquer outra.
INSERT INTO platform.alert_rule (rule_key, metric_key, comparator, threshold,
    consecutive_readings, severity, scope, owner)
VALUES ('central.collector.stale', 'platform.collector.last_run_age_s', '>', 900, 1,
        'CRITICAL', 'GLOBAL', 'platform')
ON CONFLICT (rule_key) DO NOTHING;

-- ======================================================================
-- Itens de configuracao da Central (briefing, radar, marcadores)
-- ======================================================================

CREATE TABLE IF NOT EXISTS platform.config_item (
    item_key VARCHAR(200) PRIMARY KEY,
    item_value JSONB NOT NULL DEFAULT '{}',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Marcador do coletor: atualizado a cada coleta; o watchdog le daqui.
-- Sem valor inicial inventado: ate a primeira coleta, o watchdog trata
-- a ausencia como "nunca coletou" (stale = true), nao como zero.
INSERT INTO platform.config_item (item_key, item_value)
VALUES ('central.collector.last_run_at', '{"collected": false}'::JSONB)
ON CONFLICT (item_key) DO NOTHING;

-- ======================================================================
-- Funcoes agregadoras platform_stats.* (so contagens e idades)
-- ======================================================================
-- SECURITY DEFINER, dono migrador, search_path fixo: leem `public.outbox`
-- (RLS nao se aplica ao dono) e tabelas de `platform`, e devolvem so
-- tenant_id + contagem/idade. Nenhuma coluna pessoal aparece na saida
-- nem e referenciada no corpo, alem de `tenant_id` (o UUID do catalogo,
-- nunca nome ou documento — CENTRAL-ZIIP §8: tenant_ref e o UUID).

CREATE OR REPLACE FUNCTION platform_stats.tenant_outbox_counts()
RETURNS TABLE (tenant_id UUID, pending_count BIGINT, dead_count BIGINT,
               oldest_pending_age_s BIGINT)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, platform
AS $fn$
    SELECT o.tenant_id,
           COUNT(*) FILTER (WHERE o.status = 'PENDING'),
           COUNT(*) FILTER (WHERE o.status = 'DEAD'),
           COALESCE(EXTRACT(EPOCH FROM (NOW() - MIN(o.created_at)
               FILTER (WHERE o.status = 'PENDING')))::BIGINT, 0)
    FROM public.outbox o
    GROUP BY o.tenant_id
$fn$;

CREATE OR REPLACE FUNCTION platform_stats.tenant_counts()
RETURNS TABLE (status TEXT, tenant_count BIGINT)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, platform
AS $fn$
    SELECT c.status, COUNT(*)
    FROM public.tenant_catalog c
    GROUP BY c.status
$fn$;

CREATE OR REPLACE FUNCTION platform_stats.open_alert_counts()
RETURNS TABLE (severity TEXT, open_count BIGINT)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, platform
AS $fn$
    SELECT e.severity, COUNT(*)
    FROM platform.alert_event e
    WHERE e.resolved_at IS NULL
    GROUP BY e.severity
$fn$;

CREATE OR REPLACE FUNCTION platform_stats.audit_action_counts(p_since TIMESTAMPTZ)
RETURNS TABLE (action TEXT, event_count BIGINT)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, platform
AS $fn$
    SELECT a.action, COUNT(*)
    FROM platform.audit_event a
    WHERE a.created_at >= p_since
    GROUP BY a.action
$fn$;

CREATE OR REPLACE FUNCTION platform_stats.onboarding_counts()
RETURNS TABLE (tenant_id UUID, pending_steps BIGINT, done_steps BIGINT)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, platform
AS $fn$
    SELECT s.tenant_id,
           COUNT(*) FILTER (WHERE s.status = 'PENDING'),
           COUNT(*) FILTER (WHERE s.status = 'DONE')
    FROM platform.onboarding_step s
    GROUP BY s.tenant_id
$fn$;

REVOKE ALL ON FUNCTION platform_stats.tenant_outbox_counts() FROM PUBLIC;
REVOKE ALL ON FUNCTION platform_stats.tenant_counts() FROM PUBLIC;
REVOKE ALL ON FUNCTION platform_stats.open_alert_counts() FROM PUBLIC;
REVOKE ALL ON FUNCTION platform_stats.audit_action_counts(TIMESTAMPTZ) FROM PUBLIC;
REVOKE ALL ON FUNCTION platform_stats.onboarding_counts() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION platform_stats.tenant_outbox_counts() TO ziipsan_central, ziipsan_app;
GRANT EXECUTE ON FUNCTION platform_stats.tenant_counts() TO ziipsan_central, ziipsan_app;
GRANT EXECUTE ON FUNCTION platform_stats.open_alert_counts() TO ziipsan_central, ziipsan_app;
GRANT EXECUTE ON FUNCTION platform_stats.audit_action_counts(TIMESTAMPTZ) TO ziipsan_central, ziipsan_app;
GRANT EXECUTE ON FUNCTION platform_stats.onboarding_counts() TO ziipsan_central, ziipsan_app;

-- ======================================================================
-- Privilegios (padrao da V9: app escreve o console; central so le)
-- ======================================================================

GRANT USAGE ON SCHEMA platform TO ziipsan_app, ziipsan_central;
GRANT USAGE ON SCHEMA platform_stats TO ziipsan_app, ziipsan_central;
GRANT SELECT, INSERT, UPDATE ON platform.metric_definition TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON platform.metric_point TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON platform.metric_daily TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.alert_rule TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.alert_event TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.config_item TO ziipsan_app;
GRANT SELECT ON platform.metric_definition TO ziipsan_central;
GRANT SELECT ON platform.metric_daily TO ziipsan_central;
GRANT SELECT ON platform.alert_rule TO ziipsan_central;
GRANT SELECT ON platform.alert_event TO ziipsan_central;
GRANT SELECT ON platform.config_item TO ziipsan_central;
GRANT SELECT ON platform.metric_point TO ziipsan_central;
