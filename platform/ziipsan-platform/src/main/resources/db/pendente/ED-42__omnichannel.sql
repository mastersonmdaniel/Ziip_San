-- ED-42 — Omnichannel híbrido — SQL PENDENTE, SEM NÚMERO Flyway.
-- Motivo: a numeração global é atribuída no pouso pelo coordenador (regra da
-- cascata; SQL sem número vai para db/pendente/ com o prefixo ED-42__).
-- Este arquivo vive em db/pendente/ do módulo platform e NÃO é executado pelo
-- Flyway até receber número. NENHUM canal, provedor, credencial, preço ou
-- prazo de atendimento é semeado aqui: canais habilitados são do tenant,
-- credenciais vivem no cofre e preços/categorias são os da ED-41.
--
-- O QUE CRIA
--   1) omni_conversations: conversa de atendimento por canal, registrada no
--      protocolo da matrícula (FK para service_protocols da P1.36, V8_1).
--   2) omni_conversation_messages: mensagens da conversa (inbound/outbound),
--      com a categoria de preço do modelo de WhatsApp da ED-41 quando houver.
--   3) omni_channel_opt_ins: linhas de opt-in por (morador, canal).
--   4) omni_webhook_events: idempotência dos eventos de webhook por canal.
--   5) omni_integrity_log: cadeia de integridade append-only por tenant.
--   6) Definição do parâmetro omni.channels_enabled no catálogo global
--      (padrão da V9_11 da P3.36) — parte de CORE destacada para a migration
--      de core correspondente na integração, como no SQL da ED-41.

-- (1) Conversas ----------------------------------------------------------------

CREATE TABLE IF NOT EXISTS omni_conversations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    protocol_id UUID NOT NULL,
    consumer_unit_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    channel VARCHAR(16) NOT NULL CHECK (channel IN
        ('WHATSAPP', 'EMAIL', 'PORTAL_CHAT', 'MESSENGER', 'INSTAGRAM', 'TELEGRAM')),
    external_thread_id VARCHAR(300) NOT NULL CHECK (btrim(external_thread_id) <> ''),
    status VARCHAR(16) NOT NULL CHECK (status IN ('OPEN', 'ASSIGNED', 'WAITING_CUSTOMER', 'CLOSED')),
    assigned_to UUID,
    priority_profile BOOLEAN NOT NULL DEFAULT FALSE,
    opened_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL)),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, protocol_id) REFERENCES service_protocols (tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_omni_conversations_protocol
    ON omni_conversations (tenant_id, protocol_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_omni_conversations_open_thread
    ON omni_conversations (tenant_id, channel, external_thread_id) WHERE status <> 'CLOSED';

-- (2) Mensagens ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS omni_conversation_messages (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    conversation_id UUID NOT NULL,
    direction VARCHAR(8) NOT NULL CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    author VARCHAR(10) NOT NULL CHECK (author IN ('CUSTOMER', 'ATTENDANT', 'SYSTEM')),
    body TEXT NOT NULL CHECK (btrim(body) <> ''),
    external_message_id VARCHAR(300),
    template_code VARCHAR(120),
    whatsapp_category VARCHAR(20) CHECK (whatsapp_category IN ('MARKETING', 'UTILITY', 'AUTHENTICATION')),
    occurred_at TIMESTAMPTZ NOT NULL,
    CHECK (template_code IS NULL OR whatsapp_category IS NOT NULL),
    CHECK (direction <> 'INBOUND' OR author = 'CUSTOMER'),
    FOREIGN KEY (tenant_id, conversation_id) REFERENCES omni_conversations (tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_omni_messages_conversation
    ON omni_conversation_messages (tenant_id, conversation_id, occurred_at);
CREATE UNIQUE INDEX IF NOT EXISTS uq_omni_messages_external
    ON omni_conversation_messages (tenant_id, external_message_id) WHERE external_message_id IS NOT NULL;

-- (3) Opt-in por canal -------------------------------------------------------------

CREATE TABLE IF NOT EXISTS omni_channel_opt_ins (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    channel VARCHAR(16) NOT NULL CHECK (channel IN
        ('WHATSAPP', 'EMAIL', 'PORTAL_CHAT', 'MESSENGER', 'INSTAGRAM', 'TELEGRAM')),
    enabled BOOLEAN NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_omni_opt_ins_customer_channel
    ON omni_channel_opt_ins (tenant_id, customer_id, channel, recorded_at);

-- (4) Idempotência de webhooks --------------------------------------------------------

CREATE TABLE IF NOT EXISTS omni_webhook_events (
    tenant_id UUID NOT NULL,
    channel VARCHAR(16) NOT NULL,
    provider_event_id VARCHAR(300) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, channel, provider_event_id)
);

-- (5) Log de integridade (cadeia SHA-256 append-only) -----------------------------------

CREATE TABLE IF NOT EXISTS omni_integrity_log (
    tenant_id UUID NOT NULL,
    sequence BIGINT NOT NULL,
    event_type VARCHAR(24) NOT NULL CHECK (event_type IN
        ('WEBHOOK_VERIFIED', 'WEBHOOK_REJECTED', 'CONVERSATION_OPENED', 'MESSAGE_RECORDED',
         'OPT_IN_CHECKED', 'ASSIGNMENT_CHANGED', 'CONVERSATION_CLOSED', 'CREDENTIAL_ACCESSED',
         'REFUSAL_RECORDED')),
    reference VARCHAR(300) NOT NULL,
    actor VARCHAR(200),
    occurred_at TIMESTAMPTZ NOT NULL,
    previous_hash VARCHAR(80) NOT NULL,
    hash VARCHAR(64) NOT NULL,
    PRIMARY KEY (tenant_id, sequence)
);

SELECT register_tenant_table('omni_conversations');
SELECT register_tenant_table('omni_conversation_messages');
SELECT register_tenant_table('omni_channel_opt_ins');
SELECT register_tenant_table('omni_webhook_events');
SELECT register_tenant_table('omni_integrity_log');

COMMENT ON TABLE omni_conversations IS
    'ED-42: conversa de atendimento omnichannel registrada no protocolo da matrícula (P1.36); canal exato aqui, ProtocolChannel do protocolo é o mapeamento declarado E-ED42-1.';
COMMENT ON TABLE omni_integrity_log IS
    'ED-42: log com integridade (cadeia SHA-256 append-only) dos eventos de webhook, conversa, opt-in, atribuição e acesso a credencial (só a referência). SG-655 a SG-661 [SEM FONTE para atribuição código-a-código].';

-- (6) Definição de parâmetro omni.* no catálogo global (CORE — destacar na integração) --
-- Chave NOVA/proposta desta entrega (ver OmnichannelParameters). Sem fábrica:
-- canal não listado não atende — nenhum canal é presumido habilitado.

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('omni.channels_enabled', 'JSON', 'd', NULL,
     'ED-42 [CHAVE NOVA/proposta]; ADR-0028 (c)', NULL,
     'Canais de atendimento omnichannel habilitados por tenant (WHATSAPP, EMAIL, PORTAL_CHAT, MESSENGER, INSTAGRAM, TELEGRAM). Sem valor vigente, nenhum canal externo atende.')
ON CONFLICT (definition_key) DO NOTHING;
