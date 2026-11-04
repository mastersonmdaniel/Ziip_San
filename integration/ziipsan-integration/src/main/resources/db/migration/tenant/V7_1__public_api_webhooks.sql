-- ============================================================================
-- P2.26 — API pública de integração e webhooks de saída (plano v3.0 §3, V7_1).
--
-- Tabelas de tenant (tenant_id, RLS forçada por register_tenant_table da
-- V1_2, trigger de auditoria): clientes OAuth 2.0 por tenant, tokens de
-- acesso opacos, baldes de limite de taxa, asserções private_key_jwt já
-- usadas (contra repetição), endpoints de webhook, entregas com reenvio e
-- alertas de desativação automática.
--
-- ORDENAÇÃO: esta migration só referencia as próprias tabelas (nenhuma FK
-- para bills, payments ou service_orders: a leitura pública é JDBC sobre as
-- tabelas dos módulos donos, sem acoplamento de migration). Por isso roda
-- igual no fresh install e no upgrade via outOfOrder do TenantMigrator.
-- Tudo é defensivo (IF NOT EXISTS / ON CONFLICT DO NOTHING).
--
-- SEGREDOS: nunca em claro. client_secret é guardado só como hash SHA-256
-- hexadecimal; segredo de webhook é guardado cifrado (ver acima). O valor
-- em claro é devolvido UMA vez, na criação ou na rotação, e nunca mais
-- pode ser lido. A rotação guarda o segredo anterior em previous_* com
-- validade de sobreposição de 24 h.
-- ============================================================================

-- ---------------------------------------------------------------- clientes
CREATE TABLE IF NOT EXISTS api_clients (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    client_id VARCHAR(80) NOT NULL,
    name VARCHAR(120) NOT NULL,
    auth_method VARCHAR(20) NOT NULL CHECK (auth_method IN ('CLIENT_SECRET', 'PRIVATE_KEY_JWT')),
    secret_hash VARCHAR(128),
    previous_secret_hash VARCHAR(128),
    previous_valid_until TIMESTAMPTZ,
    public_key_pem TEXT,
    scopes TEXT[] NOT NULL DEFAULT '{}',
    rate_limit_per_minute INT CHECK (rate_limit_per_minute IS NULL OR rate_limit_per_minute > 0),
    ip_allowlist INET[],
    environment VARCHAR(20) NOT NULL DEFAULT 'PRODUCTION' CHECK (environment IN ('PRODUCTION', 'SANDBOX')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK ((auth_method = 'CLIENT_SECRET') = (secret_hash IS NOT NULL)),
    CHECK (auth_method <> 'PRIVATE_KEY_JWT' OR public_key_pem IS NOT NULL)
);
ALTER TABLE api_clients ADD CONSTRAINT uq_api_clients_tenant_client UNIQUE (tenant_id, client_id);
-- Precedente V4_1: tabela referenciada por FK (tenant_id, id) precisa da
-- unicidade composta (a PK é só id, e o Postgres exige unique sobre as
-- colunas referenciadas — sem ela, a FK de api_access_tokens falha 42830).
ALTER TABLE api_clients ADD CONSTRAINT uq_api_clients_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_api_clients_tenant_status ON api_clients(tenant_id, status);
SELECT register_tenant_table('api_clients');

-- ------------------------------------------------------------ tokens opacos
CREATE TABLE IF NOT EXISTS api_access_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    client_id UUID NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    scopes TEXT[] NOT NULL DEFAULT '{}',
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE api_access_tokens ADD CONSTRAINT uq_api_access_tokens_tenant_hash UNIQUE (tenant_id, token_hash);
ALTER TABLE api_access_tokens ADD CONSTRAINT fk_api_access_tokens_client
    FOREIGN KEY (tenant_id, client_id) REFERENCES api_clients(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_api_access_tokens_client ON api_access_tokens(tenant_id, client_id, expires_at);
SELECT register_tenant_table('api_access_tokens');

-- ------------------------------------------------------- baldes de taxa
-- Contador de alto volume: sem colunas de entidade e sem auditoria por
-- linha (precedente das séries temporais da V1_2: p_entity=false,
-- p_audit=false). A RLS forçada continua valendo.
CREATE TABLE IF NOT EXISTS api_rate_buckets (
    tenant_id UUID NOT NULL,
    client_id UUID NOT NULL,
    window_start BIGINT NOT NULL,
    request_count INT NOT NULL DEFAULT 0 CHECK (request_count >= 0),
    PRIMARY KEY (tenant_id, client_id, window_start)
);
SELECT register_tenant_table('api_rate_buckets', false, false);

-- ------------------------------------------- asserções private_key_jwt usadas
CREATE TABLE IF NOT EXISTS api_assertion_jtis (
    tenant_id UUID NOT NULL,
    jti_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, jti_hash)
);
SELECT register_tenant_table('api_assertion_jtis', false, false);

-- ------------------------------------------------------- endpoints webhook
-- O segredo de webhook é guardado CIFRADO (FieldCipher AES-256-GCM, chave
-- do tenant, AAD webhook_endpoints.secret): assinar a entrega exige o
-- segredo em claro no envio, e hash não permite isso. O segredo de
-- cliente (api_clients), ao contrário, é só-hash: ele só é verificado,
-- nunca usado para assinar. Segredos cifrados e hashes são redigidos
-- (REDACT) no audit_log pelas regras abaixo — nunca gravados em claro.
CREATE TABLE IF NOT EXISTS webhook_endpoints (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    url VARCHAR(2048) NOT NULL,
    event_types TEXT[] NOT NULL DEFAULT '{}',
    secret_cipher TEXT NOT NULL,
    previous_secret_cipher TEXT,
    previous_valid_until TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    consecutive_failures INT NOT NULL DEFAULT 0 CHECK (consecutive_failures >= 0),
    disabled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
CREATE INDEX IF NOT EXISTS idx_webhook_endpoints_tenant_status ON webhook_endpoints(tenant_id, status);
-- Precedente V4_1 (mesmo motivo da FK de api_access_tokens): as FKs de
-- webhook_deliveries e webhook_alerts referenciam (tenant_id, id).
ALTER TABLE webhook_endpoints ADD CONSTRAINT uq_webhook_endpoints_tenant_id UNIQUE (tenant_id, id);
SELECT register_tenant_table('webhook_endpoints');

-- ------------------------------------------------------------- entregas
CREATE TABLE IF NOT EXISTS webhook_deliveries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    endpoint_id UUID NOT NULL,
    event_id UUID NOT NULL,
    event_name VARCHAR(120) NOT NULL,
    event_version INT NOT NULL DEFAULT 1 CHECK (event_version > 0),
    aggregate_key VARCHAR(200) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts INT NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ,
    last_error VARCHAR(500),
    delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
-- O event_id é único por evento, não por entrega: o mesmo evento vai para
-- cada endpoint inscrito, com o mesmo event_id (é assim que o receptor
-- deduplica). A unicidade é, portanto, (tenant, endpoint, event).
ALTER TABLE webhook_deliveries ADD CONSTRAINT uq_webhook_deliveries_tenant_endpoint_event
    UNIQUE (tenant_id, endpoint_id, event_id);
ALTER TABLE webhook_deliveries ADD CONSTRAINT fk_webhook_deliveries_endpoint
    FOREIGN KEY (tenant_id, endpoint_id) REFERENCES webhook_endpoints(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_due
    ON webhook_deliveries(tenant_id, status, next_attempt_at);
CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_aggregate
    ON webhook_deliveries(tenant_id, aggregate_key, created_at);
SELECT register_tenant_table('webhook_deliveries');

-- --------------------------------------------------------------- alertas
CREATE TABLE IF NOT EXISTS webhook_alerts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    endpoint_id UUID NOT NULL,
    reason VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE webhook_alerts ADD CONSTRAINT fk_webhook_alerts_endpoint
    FOREIGN KEY (tenant_id, endpoint_id) REFERENCES webhook_endpoints(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_webhook_alerts_tenant ON webhook_alerts(tenant_id, acknowledged, created_at);
SELECT register_tenant_table('webhook_alerts');

-- ------------------------------------------------- parâmetros do tenant (P2.26)
-- Classes do plano: api.enabled (d, desligado sem previsão no contrato),
-- api.rate_limit_per_minute (c, só reduz, teto da plataforma),
-- api.ip_allowlist, api.client_max, api.webhooks_enabled,
-- api.webhook_max_endpoints e api.webhook_event_types (d). O valor vigente
-- é o da tela de parâmetros da P1.6 (versão aprovada + aceite); sem versão
-- aceita, vale o padrão de fábrica abaixo.
INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('api.enabled', 'BOOLEAN', 'd', 'false', 'P2.26',
     NULL,
     'API pública de integração do tenant. Desligada por padrão e sem previsão no contrato: ligar exige aceite do parâmetro na tela da P1.6. Com ela desligada, o token endpoint e todas as rotas públicas devolvem 403.'),
    ('api.rate_limit_per_minute', 'INT', 'c', NULL, 'P2.26',
     NULL,
     'Limite de taxa da API pública por cliente, por minuto. Classe c: o tenant só reduz em relação ao teto da plataforma (1000/min); valor acima do teto é limitado ao teto em runtime. Sem valor aceito, vale o limite do cliente ou o teto.'),
    ('api.ip_allowlist', 'TEXT', 'd', NULL, 'P2.26',
     NULL,
     'Lista de IPs permitidos do tenant (JSON com CIDRs ou IPs), aplicada quando o cliente não tem allowlist própria. Lista vazia ou ausente significa sem restrição por IP além da do cliente.'),
    ('api.client_max', 'INT', 'd', '10', 'P2.26',
     NULL,
     'Número máximo de clientes OAuth 2.0 ativos por tenant.'),
    ('api.webhooks_enabled', 'BOOLEAN', 'd', 'false', 'P2.26',
     NULL,
     'Webhooks de saída do tenant. Desligados por padrão; ligar exige aceite do parâmetro na tela da P1.6.'),
    ('api.webhook_max_endpoints', 'INT', 'd', '5', 'P2.26',
     NULL,
     'Número máximo de endpoints de webhook ativos por tenant.'),
    ('api.webhook_event_types', 'TEXT', 'd', NULL, 'P2.26',
     NULL,
     'Tipos de evento que o tenant permite entregar por webhook (JSON com nomes do catálogo da P1.38). Ausente significa todos os tipos do catálogo de webhooks da P2.26. Tipo fora do catálogo é recusado na criação do endpoint.')
ON CONFLICT (definition_key) DO NOTHING;

-- Redação de segredos no audit_log (o trigger de auditoria da V1_2 grava
-- old/new values: sem estas regras, hash e cifra de segredo iriam para o
-- log — o hash não revela o segredo, mas a cifra não pertence ao log).
INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('api_clients', 'secret_hash', 'REDACT', 'segredo de cliente da API pública (só hash)'),
    ('api_clients', 'previous_secret_hash', 'REDACT', 'segredo anterior de cliente da API pública (só hash)'),
    ('api_access_tokens', 'token_hash', 'REDACT', 'hash do token de acesso da API pública'),
    ('api_assertion_jtis', 'jti_hash', 'REDACT', 'hash do jti de asserção private_key_jwt'),
    ('webhook_endpoints', 'secret_cipher', 'REDACT', 'segredo de webhook cifrado'),
    ('webhook_endpoints', 'previous_secret_cipher', 'REDACT', 'segredo anterior de webhook cifrado')
ON CONFLICT (table_name, column_name) DO NOTHING;

INSERT INTO public.parameter_legal_limits
    (definition_key, limit_kind, limit_value, norm_source, applies_to,
     above_max_requires_norm, above_max_requires_dual_approval,
     above_max_requires_formal_acceptance, above_max_risk_alert, notes)
VALUES
    ('api.rate_limit_per_minute', 'MAX_VALUE', '1000', 'P2.26 (teto da plataforma)',
     NULL, false, false, false, false,
     'Teto da plataforma para o limite por cliente: valor aceito acima dele é limitado ao teto em runtime (classe c: só reduz).')
ON CONFLICT (definition_key, limit_kind, COALESCE(applies_to, '')) DO NOTHING;
