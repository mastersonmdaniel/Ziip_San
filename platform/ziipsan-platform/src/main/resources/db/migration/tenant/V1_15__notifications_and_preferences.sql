-- P3.36 — Central de notificações do usuário e preferências (plano v3.0 §3, tenant V1_15, módulo platform).
--
-- O QUE MUDA
--   * notifications: um aviso por (destinatário, evento, canal). Nesta tarefa nasce a linha
--     do canal IN_APP (a própria central: sino, lista, lido/não lido); as linhas EMAIL/PUSH
--     são da P3.5, que lê as preferências desta mesma migration. read_at é a leitura
--     registrada: nasce nulo e o serviço só grava a PRIMEIRA leitura (COALESCE).
--   * source_message_id: a chave de deduplicação da ingestão — o id da mensagem da outbox
--     (P1.9) que originou o aviso, com índice único parcial. A reentrega da mesma mensagem
--     NÃO duplica o aviso: a ingestão devolve o aviso já existente (correção pós-revisão).
--   * notification_preferences: registro ÚNICO de preferências por canal e por finalidade
--     (dona: P3.36, COE-19; leitores: P3.5 e ED-41). Sujeito USER (user_id) ou CONSUMER
--     (customer_id + contact_id verificado + consent_id da P1.3, CAD-84/DAD-30) — exatamente
--     um dos dois (CHECK abaixo; os dois juntos é ambiguidade recusada também no código).
--   * NENHUMA linha de categoria obrigatória nasce desligada e nenhuma preferência apaga
--     aviso: desligar canal só suprime a entrega externa daquele canal.
--
-- FKs CONDICIONAIS (padrão da V8_1, tenant, COM a guarda da integração P1-C): a guarda
-- antiga (só to_regclass) não basta — num classpath parcial a tabela referenciada pode
-- existir SEM o UNIQUE (tenant_id, id) que a FK exige, e o ALTER quebra o provisionamento
-- (42830). A guarda abaixo exige a chave única (tenant_id, id) em si, via pg_constraint,
-- exatamente como a P1-C corrigiu na V8_1.
--   * users é da V1_9 (ordena antes desta): na montagem completa a FK aplica aqui.
--   * customers é do registry V3_1, que ordena DEPOIS da V1_15 na história única do tenant:
--     numa instalação fresca o bloco pula (a tabela ainda não existe) e quem completa as
--     FKs é a repetível R__notifications_customer_fk.sql, que roda depois das versionadas
--     (precedente: R__service_protocol_order_fk.sql da P1.36). Num tenant em upgrade, onde
--     customers já existe, as FKs nascem aqui e a repetível é no-op.
--   * consent_purposes (registry, V3_12) também ordena DEPOIS: a FK de finalidade para
--     consent_purposes fica para a repetível do módulo dono — pendência declarada no
--     relatório da tarefa (o nome da tabela de consentimentos da P1.3 não foi inventado).
-- A unicidade da preferência usa COALESCE no índice único: com UNIQUE simples, linhas
-- de USER (customer_id nulo) nunca conflitariam entre si (NULL <> NULL no Postgres) e o
-- upsert do serviço não teria alvo. O índice abaixo é o alvo exato do ON CONFLICT dele.
--
-- Sem semente: categorias, finalidades e eventos são catálogos de outros donos
-- (P1.38 eventos, P1.3 finalidades); semear aqui congelaria conteúdo alheio.

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    recipient_user_id UUID,
    recipient_customer_id UUID,
    source_message_id UUID,
    event_name VARCHAR(120) NOT NULL CHECK (btrim(event_name) <> ''),
    event_version INT NOT NULL CHECK (event_version >= 1),
    category VARCHAR(30) NOT NULL CHECK (category IN
        ('SECURITY', 'PENDING_APPROVAL', 'LEGAL_NOTICE', 'BILLING', 'SERVICE_ORDER', 'ACCOUNT', 'GENERAL')),
    title_code VARCHAR(120) NOT NULL CHECK (btrim(title_code) <> ''),
    body_code VARCHAR(120) NOT NULL CHECK (btrim(body_code) <> ''),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    channel VARCHAR(10) NOT NULL CHECK (channel IN ('IN_APP', 'EMAIL', 'PUSH')),
    deliver_after TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (recipient_user_id IS NOT NULL OR recipient_customer_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_unread
    ON notifications (recipient_user_id, created_at DESC) WHERE read_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_notifications_user_list
    ON notifications (recipient_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_delivery_due
    ON notifications (channel, deliver_after) WHERE read_at IS NULL;
-- Deduplicação da ingestão: uma mensagem da outbox gera NO MÁXIMO um aviso por tenant.
-- Parcial: linhas sem origem na outbox (source_message_id nulo) não disputam a chave.
CREATE UNIQUE INDEX IF NOT EXISTS uq_notifications_source_message
    ON notifications (tenant_id, source_message_id) WHERE source_message_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS notification_preferences (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    user_id UUID,
    customer_id UUID,
    contact_id UUID,
    consent_id UUID,
    purpose_code VARCHAR(80) NOT NULL CHECK (btrim(purpose_code) <> ''),
    category VARCHAR(30) NOT NULL CHECK (category IN
        ('SECURITY', 'PENDING_APPROVAL', 'LEGAL_NOTICE', 'BILLING', 'SERVICE_ORDER', 'ACCOUNT', 'GENERAL')),
    channel VARCHAR(10) NOT NULL CHECK (channel IN ('IN_APP', 'EMAIL', 'PUSH')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    digest_mode VARCHAR(10) CHECK (digest_mode IN ('IMMEDIATE', 'DAILY')),
    silence_start TIME,
    silence_end TIME,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (user_id IS NOT NULL OR customer_id IS NOT NULL),
    CHECK (user_id IS NULL OR customer_id IS NULL),
    CHECK (customer_id IS NULL OR (contact_id IS NOT NULL AND consent_id IS NOT NULL)),
    CHECK ((silence_start IS NULL) = (silence_end IS NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_preferences_subject_purpose_channel
    ON notification_preferences (tenant_id,
        COALESCE(user_id, '00000000-0000-0000-0000-000000000000'::uuid),
        COALESCE(customer_id, '00000000-0000-0000-0000-000000000000'::uuid),
        purpose_code, channel);

-- FKs externas condicionais com a guarda da integração P1-C (UNIQUE (tenant_id, id)
-- via pg_constraint, como na V8_1 corrigida — não basta a tabela existir).
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint c
        WHERE c.conrelid = to_regclass('users')
          AND c.contype IN ('u', 'p')
          AND (SELECT array_agg(a.attname::text ORDER BY k.ord)
               FROM unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord)
               JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum)
              = ARRAY['tenant_id', 'id']
    ) THEN
        ALTER TABLE notifications ADD CONSTRAINT fk_notifications_user
            FOREIGN KEY (tenant_id, recipient_user_id) REFERENCES users (tenant_id, id);
        ALTER TABLE notification_preferences ADD CONSTRAINT fk_notification_prefs_user
            FOREIGN KEY (tenant_id, user_id) REFERENCES users (tenant_id, id);
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint c
        WHERE c.conrelid = to_regclass('customers')
          AND c.contype IN ('u', 'p')
          AND (SELECT array_agg(a.attname::text ORDER BY k.ord)
               FROM unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord)
               JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum)
              = ARRAY['tenant_id', 'id']
    ) THEN
        ALTER TABLE notifications ADD CONSTRAINT fk_notifications_customer
            FOREIGN KEY (tenant_id, recipient_customer_id) REFERENCES customers (tenant_id, id);
        ALTER TABLE notification_preferences ADD CONSTRAINT fk_notification_prefs_customer
            FOREIGN KEY (tenant_id, customer_id) REFERENCES customers (tenant_id, id);
    END IF;
END $$;

SELECT register_tenant_table('notifications');
SELECT register_tenant_table('notification_preferences');

COMMENT ON TABLE notifications IS
    'P3.36: central de avisos por usuário/consumidor; leitura registrada em read_at (primeira leitura); source_message_id deduplica a ingestão da outbox.';
COMMENT ON TABLE notification_preferences IS
    'P3.36 (COE-19): registro único de preferências por canal e finalidade; lido pela P3.5 e pela ED-41.';
