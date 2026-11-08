-- ED-07 — Monitoramento e alertas para o cliente (SEM NÚMERO: nenhum número
-- Flyway foi CONFIRMADO livre para a ED-07 no repositório nem reservado a ela
-- no §3 do plano v3.0 — por isso este SQL vive em db/pendente/ e só ganha
-- número na integração, no módulo platform, schema do tenant, DEPOIS da
-- V1_15 da P3.36, da qual reutiliza o padrão: register_tenant_table, índices
-- com COALESCE quando há nulos na chave e FKs condicionais com a guarda da
-- integração P1-C — ver RELATORIO-ED-07).
--
-- O QUE MUDA
--   * tenant_alert_rules: a ESCOLHA do tenant — evento do catálogo da P1.38
--     (nome + versão declarada), usuário responsável, opção de webhook.
--     Unicidade parcial: no máximo UMA regra ATIVA por
--     (tenant, evento, versão, responsável).
--   * tenant_alerts: o alerta gerado. kind CATALOG_EVENT carrega regra,
--     nome e versão do evento; kind BATCH_FAILURE NÃO carrega nome de evento
--     (a falha de lote da P2.1 não é um evento do catálogo — source_ref é o
--     id da execução de job_runs). source_ref deduplica a reentrega
--     (pelo menos uma vez da outbox da P1.9): índice único parcial por
--     (tenant, rule_id, source_ref) nas linhas de evento.
--   * tenant_alert_webhook_states: falhas consecutivas e desativação
--     automática do endpoint por (tenant, evento), da política de entrega
--     confiável da P2.26 aplicada pela ED-07. O ENDPOINT em si
--     (webhook_endpoint) e as entregas (webhook_delivery) são tabelas da
--     P2.26 — esta entrega não as duplica.
-- Sem semente: eventos são catálogo da P1.38 e responsáveis são usuários do
-- tenant; semear aqui congelaria conteúdo alheio.

CREATE TABLE IF NOT EXISTS tenant_alert_rules (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    event_name VARCHAR(120) NOT NULL CHECK (btrim(event_name) <> ''),
    event_version INT NOT NULL CHECK (event_version >= 1),
    responsible_user_id UUID NOT NULL,
    webhook_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_alert_rules_active_choice
    ON tenant_alert_rules (tenant_id, event_name, event_version, responsible_user_id)
    WHERE enabled;

CREATE INDEX IF NOT EXISTS idx_tenant_alert_rules_lookup
    ON tenant_alert_rules (tenant_id, event_name, event_version) WHERE enabled;

CREATE TABLE IF NOT EXISTS tenant_alerts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    rule_id UUID,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('CATALOG_EVENT', 'BATCH_FAILURE')),
    event_name VARCHAR(120),
    event_version INT,
    source_ref VARCHAR(120) NOT NULL CHECK (btrim(source_ref) <> ''),
    responsible_user_id UUID NOT NULL,
    title_code VARCHAR(160) NOT NULL CHECK (btrim(title_code) <> ''),
    body_code VARCHAR(160) NOT NULL CHECK (btrim(body_code) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK ((kind = 'CATALOG_EVENT' AND rule_id IS NOT NULL AND event_name IS NOT NULL
            AND event_version IS NOT NULL AND event_version >= 1)
        OR (kind = 'BATCH_FAILURE' AND event_name IS NULL AND event_version IS NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_alerts_source
    ON tenant_alerts (tenant_id, rule_id, source_ref) WHERE rule_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_alerts_batch_failure
    ON tenant_alerts (tenant_id, source_ref) WHERE kind = 'BATCH_FAILURE';
CREATE INDEX IF NOT EXISTS idx_tenant_alerts_tenant_recent
    ON tenant_alerts (tenant_id, created_at DESC);

CREATE TABLE IF NOT EXISTS tenant_alert_webhook_states (
    tenant_id UUID NOT NULL,
    event_name VARCHAR(120) NOT NULL CHECK (btrim(event_name) <> ''),
    consecutive_failures INT NOT NULL DEFAULT 0 CHECK (consecutive_failures >= 0),
    disabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, event_name)
);

SELECT register_tenant_table('tenant_alert_rules');
SELECT register_tenant_table('tenant_alerts');
SELECT register_tenant_table('tenant_alert_webhook_states');

COMMENT ON TABLE tenant_alert_rules IS
    'ED-07: escolha do tenant de evento do catálogo da P1.38 (nome+versão) com responsável e opção de webhook da P2.26.';
COMMENT ON TABLE tenant_alerts IS
    'ED-07: alertas gerados (CATALOG_EVENT pela regra; BATCH_FAILURE da P2.1 sem nome de evento); source_ref deduplica a reentrega da outbox.';
COMMENT ON TABLE tenant_alert_webhook_states IS
    'ED-07/P2.26: falhas consecutivas e desativação automática do webhook por (tenant, evento); endpoint e entregas são da P2.26.';
