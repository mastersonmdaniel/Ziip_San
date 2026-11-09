-- ED-43 — Chatbot e IA por modelo de linguagem de terceiro — SQL PENDENTE, SEM NUMERO Flyway.
-- Numeracao global atribuida no pouso (regra da cascata). NENHUM provedor/modelo/preco semeado:
-- provedor e modelo sao configuracao do tenant, sem default de fabrica; credencial so por referencia ao cofre.
-- CORRECAO ED-43 (m6): script IDEMPOTENTE — constraints com guarda de reexecucao (DO + pg_constraint)
-- e UNIQUE por tenant em ai_provider_registrations (um registro de provedor por tenant; duplicado
-- conflitante e recusado pelo banco, nao fica a criterio da porta).
CREATE TABLE IF NOT EXISTS ai_model_prompts (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL, model_id VARCHAR(120) NOT NULL, model_version VARCHAR(60) NOT NULL,
    prompt_id VARCHAR(120) NOT NULL, prompt_version VARCHAR(60) NOT NULL, evaluation_passed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), UNIQUE (tenant_id, model_id, model_version, prompt_id, prompt_version));
CREATE TABLE IF NOT EXISTS ai_provider_registrations (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL, in_dpa BOOLEAN NOT NULL DEFAULT FALSE, in_ripd BOOLEAN NOT NULL DEFAULT FALSE,
    foreign_provider BOOLEAN NOT NULL DEFAULT FALSE, anpd_standard_clauses BOOLEAN NOT NULL DEFAULT FALSE,
    no_training_attested BOOLEAN NOT NULL DEFAULT FALSE, processing_region VARCHAR(60),
    credential_reference VARCHAR(300), created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ai_provider_registrations_tenant UNIQUE (tenant_id));
-- Trava P3.9/RAS-20 em banco: estrangeiro sem clausula-padrao ANPD nao pode ser marcado aprovado para uso.
-- Guarda de reexecucao (m6): so adiciona a constraint se ela ainda nao existir.
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_ai_foreign_needs_clauses') THEN
        ALTER TABLE ai_provider_registrations ADD CONSTRAINT ck_ai_foreign_needs_clauses
            CHECK (NOT foreign_provider OR anpd_standard_clauses);
    END IF;
END $$;
-- Bancos onde a tabela ja existia sem a unicidade por tenant ganham a constraint com a mesma guarda.
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_ai_provider_registrations_tenant') THEN
        ALTER TABLE ai_provider_registrations ADD CONSTRAINT uq_ai_provider_registrations_tenant UNIQUE (tenant_id);
    END IF;
END $$;
CREATE TABLE IF NOT EXISTS ai_conversation_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(), tenant_id UUID NOT NULL, conversation_id UUID NOT NULL,
    feature VARCHAR(40) NOT NULL, model_id VARCHAR(120), prompt_version VARCHAR(120),
    masked_user_text TEXT, masked_reply_text TEXT, tokens_used BIGINT NOT NULL DEFAULT 0,
    outcome VARCHAR(60) NOT NULL, human_corrected BOOLEAN NOT NULL DEFAULT FALSE, occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
CREATE INDEX IF NOT EXISTS idx_ai_logs_conversation ON ai_conversation_logs (tenant_id, conversation_id, occurred_at);
-- CORRECAO R3-B (R3-M1, Rodada 3, padrao G2 / B2-6): tabelas de tenant inscritas no
-- registro de isolamento por tenant (RLS) — uma chamada por tabela, no proprio SQL pendente.
SELECT register_tenant_table('ai_model_prompts');
SELECT register_tenant_table('ai_provider_registrations');
SELECT register_tenant_table('ai_conversation_logs');
-- Parametros (definicoes destacadas para o core no pouso, padrao ED-41/ED-42):
-- ai.enabled_features (classe b, padrao desligado), ai.provider_region (c), ai.log_retention_days (c),
-- ai.human_review_required (classe a, travado p/ corte/negativacao/tarifa social), bill.explain_enabled,
-- assist.voice_enabled, assist.provider. Nenhum valor de provedor/modelo/preco neste arquivo.
-- Retencao: a selecao dos logs vencidos e de AiRetentionSweeper/AiConversationLog.retentionExpired;
-- o agendamento do expurgo segue na fiacao (P-ED43-02), como declarado no relatorio.
