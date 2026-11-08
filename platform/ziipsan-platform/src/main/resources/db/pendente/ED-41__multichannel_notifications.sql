-- ED-41 — Notificações multicanal e fatura por e-mail — SQL PENDENTE, SEM NÚMERO Flyway.
-- Motivo: a numeração global é atribuída no pouso pelo coordenador (regra da
-- cascata; último número usado na main em 864ba6d: V101). Este arquivo vive em
-- db/pendente/ do módulo platform e NÃO é executado pelo Flyway até receber número.
-- NENHUM valor de provedor, preço, remetente ou conta é semeado aqui: remetente de
-- SMS, conta de WhatsApp, preços e teto são do tenant/provedor contratado.
--
-- O QUE MUDA
--   1) notifications / notification_preferences (V1_15 da P3.36): o CHECK de canal
--      passa a aceitar SMS e WHATSAPP (o enum NotificationChannel foi ampliado
--      pela ED-41). Os nomes dos CHECKs abaixo são os nomes automáticos do
--      PostgreSQL para os CHECKs inline da V1_15 — [A CONFIRMAR NA INTEGRAÇÃO]:
--      conferir os nomes reais em pg_constraint antes de rodar; se divergirem,
--      ajustar SÓ os nomes no DROP, sem mudar o conteúdo dos CHECKs novos.
--   2) msg_channel_deliveries: tentativas de SMS/WhatsApp/push com custo estimado
--      (base do teto mensal e da entregabilidade medida), por tenant.
--   3) msg_channel_delivery_events: idempotência dos eventos do provedor.
--   4) msg_usage_records: uso medido por tenant e por módulo (append-only).
--   5) Definições dos parâmetros msg.* no catálogo global (padrão da V9_11 da
--      P3.36) — esta parte pertence à sequência do CORE (public.parameter_definitions)
--      e é destacada para a migration de core correspondente na integração.

-- (1) Canais novos nos CHECKs da V1_15 ------------------------------------------------

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_channel_check;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_channel_ed41
    CHECK (channel IN ('IN_APP', 'EMAIL', 'SMS', 'WHATSAPP', 'PUSH'));

ALTER TABLE notification_preferences DROP CONSTRAINT IF EXISTS notification_preferences_channel_check;
ALTER TABLE notification_preferences ADD CONSTRAINT ck_notification_preferences_channel_ed41
    CHECK (channel IN ('IN_APP', 'EMAIL', 'SMS', 'WHATSAPP', 'PUSH'));

-- (2) Tentativas multicanal ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS msg_channel_deliveries (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    notice_id UUID NOT NULL,
    customer_id UUID,
    user_id UUID,
    contact_id UUID,
    channel VARCHAR(10) NOT NULL CHECK (channel IN ('SMS', 'WHATSAPP', 'PUSH')),
    destination VARCHAR(200) NOT NULL CHECK (btrim(destination) <> ''),
    template_code VARCHAR(120) NOT NULL CHECK (btrim(template_code) <> ''),
    template_version INT NOT NULL CHECK (template_version >= 1),
    whatsapp_category VARCHAR(20) CHECK (whatsapp_category IN ('MARKETING', 'UTILITY', 'AUTHENTICATION')),
    status VARCHAR(12) NOT NULL CHECK (status IN ('SENT', 'DELIVERED', 'FAILED', 'REJECTED')),
    provider_message_id VARCHAR(200),
    estimated_cost NUMERIC(19, 4) CHECK (estimated_cost IS NULL OR estimated_cost >= 0),
    sent_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (customer_id IS NOT NULL OR user_id IS NOT NULL),
    CHECK (channel <> 'WHATSAPP' OR whatsapp_category IS NOT NULL),
    CHECK (channel = 'PUSH' OR estimated_cost IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_msg_deliveries_notice
    ON msg_channel_deliveries (tenant_id, notice_id);
CREATE INDEX IF NOT EXISTS idx_msg_deliveries_month_cost
    ON msg_channel_deliveries (tenant_id, sent_at);
CREATE UNIQUE INDEX IF NOT EXISTS uq_msg_deliveries_provider_message
    ON msg_channel_deliveries (tenant_id, provider_message_id) WHERE provider_message_id IS NOT NULL;

-- (3) Idempotência dos eventos do provedor ----------------------------------------------

CREATE TABLE IF NOT EXISTS msg_channel_delivery_events (
    tenant_id UUID NOT NULL,
    provider_event_id VARCHAR(200) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, provider_event_id)
);

-- (4) Uso medido -------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS msg_usage_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    module_code VARCHAR(80) NOT NULL CHECK (btrim(module_code) <> ''),
    metric VARCHAR(16) NOT NULL CHECK (metric IN ('MESSAGES', 'SMS', 'AI_TOKENS', 'STORAGE', 'GIS')),
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    cost NUMERIC(19, 4) CHECK (cost IS NULL OR cost >= 0),
    reference_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_msg_usage_tenant_period
    ON msg_usage_records (tenant_id, occurred_at, module_code);

SELECT register_tenant_table('msg_channel_deliveries');
SELECT register_tenant_table('msg_channel_delivery_events');
SELECT register_tenant_table('msg_usage_records');

COMMENT ON TABLE msg_channel_deliveries IS
    'ED-41: tentativas de SMS/WhatsApp/push por tenant, com categoria de preço do WhatsApp e custo estimado (teto msg.monthly_cap e entregabilidade).';
COMMENT ON TABLE msg_usage_records IS
    'ED-41: uso medido por tenant e por módulo (mensagens, SMS, tokens de IA, armazenamento, GIS) — base da ED-10 e da ED-42, exposto ao console da P1.6.';

-- (5) Definições de parâmetros msg.* no catálogo global (CORE — destacar na integração) --
-- Padrão da V9_11 (P3.36). Classes marcadas PROPOSTA onde o aceite da ED-41 não as fixa:
-- o aceite nomeia as chaves msg.monthly_cap e msg.template_category_map, sem classe;
-- as demais chaves são NOVAS desta entrega (ver MultichannelParameters e o relatório).
-- factory_default: teto, remetentes e preços NÃO têm fábrica (ausência = não configurado,
-- e o disparo pago recusa); os dois booleanos do PDF nascem false porque a regra do
-- ADR-0028 (l) é "sem opt-in/aceite formal, não anexa" — false é a própria regra, não
-- um dado presumido.

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('msg.monthly_cap', 'DECIMAL', 'd', NULL,
     'ED-41; ADR-0019 (cotas 80%/100%)', NULL,
     'Teto de gasto mensal com mensagens pagas (SMS/WhatsApp) por tenant. Sem valor vigente, o disparo pago é recusado. Alertas na travessia de 80% e de 100%. Classe PROPOSTA (o aceite nomeia a chave, não a classe).'),
    ('msg.template_category_map', 'JSON', 'd', NULL,
     'ED-41', NULL,
     'Mapa código do modelo de WhatsApp -> categoria de preço (MARKETING, UTILITY, AUTHENTICATION). Modelo fora do mapa não é enviado. Classe PROPOSTA (o aceite nomeia a chave, não a classe).'),
    ('msg.sms_sender_id', 'TEXT', 'd', NULL,
     'ED-41 [CHAVE NOVA/proposta]; MP 1.396/2026 (remetente autenticado)', NULL,
     'Remetente autenticado de SMS do tenant. Sem valor, SMS não é enviado.'),
    ('msg.whatsapp_account_id', 'TEXT', 'd', NULL,
     'ED-41 [CHAVE NOVA/proposta]', NULL,
     'Conta de WhatsApp do tenant (identidade de envio dele). Sem valor, WhatsApp não é enviado.'),
    ('msg.bill_pdf_default', 'BOOLEAN', 'd', '"false"',
     'ED-41 [CHAVE NOVA/proposta]; ADR-0028 (l)', NULL,
     'O tenant adota PDF anexo como padrão da fatura por e-mail. Só tem efeito com msg.bill_pdf_formal_acceptance registrado e sem opt-out do morador.'),
    ('msg.bill_pdf_formal_acceptance', 'BOOLEAN', 'd', '"false"',
     'ED-41 [CHAVE NOVA/proposta]; ADR-0028 (l)', NULL,
     'Aceite formal do tenant ao padrão de PDF anexo registrado. Sem ele, msg.bill_pdf_default não anexa.')
ON CONFLICT (definition_key) DO NOTHING;
