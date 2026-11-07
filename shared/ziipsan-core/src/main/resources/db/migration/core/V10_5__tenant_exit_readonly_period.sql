-- Core Migration — Version: 10.5
-- Description: P3.16 — ensaio de saida de tenant: periodo de consulta
-- somente leitura pos-contrato no nivel Setor Publico e o processo de
-- saida (exportacao, carencia, contestacao, ordem judicial, eliminacao
-- e certificado), no schema `platform` (ADR-0018, ADR-0015, ADR-0025,
-- RNF-42; usa RAS-18 da P3.19).
--
-- O QUE ESTA VERSAO FAZ (mapa do plano v3.0 SS3, core `V10_5` reservada
-- a P3.16 — unica migration desta tarefa; nenhuma outra foi inventada):
--   * `platform.tenant_readonly_period`: o periodo de consulta somente
--     leitura pos-contrato (inicio, fim), ligado ao processo de saida;
--     o banco recusa periodo com fim <= inicio ou duracao maior que
--     120 dias (o teto do aceite).
--   * `platform.tenant_readonly_user`: os usuarios do tenant
--     autorizados a consultar no periodo (o "usuarios" do mapa).
--   * `platform.tenant_exit`: o processo de saida de um tenant — fim
--     do contrato (fotografia da data), nivel (STANDARD/PUBLIC_SECTOR,
--     o mesmo vocabulario de `support.sla_policy.level` da V10_2),
--     prazo e hash da exportacao, janela da carencia, agendamento da
--     eliminacao (so PLATFORM_OWNER com MFA: `scheduled_mfa`), o
--     crypto-shredding (`key_destroyed_at`, a destruicao em si e da
--     P0.28, core V6/`tenant_key`), a expiracao de backups e de copias
--     imutaveis e o certificado de descarte.
--   * `platform.tenant_exit_contestation`: contestacao registrada na
--     carencia, com o prazo de resposta do Ziip (15 dias) e o registro
--     do alerta da Central (`alert_sent_at`).
--   * `platform.tenant_exit_court_order`: ordem judicial registrada;
--     enquanto nao levantada (`lifted_at IS NULL`), suspende a
--     execucao.
--   * `platform.tenant_exit_download`: cada download da exportacao
--     registrado (o aceite exige download e contestacao registrados).
--   * `platform.tenant_export` (V9/P1.6) passa a aceitar os formatos
--     CSV e XML alem de JSON: o CHECK original da V9 so lista 'JSON'.
--     A troca localiza o CHECK pelo catalogo (pg_constraint), sem
--     presumir o nome automatico da restricao.
--
-- DECISOES DE MODELO (documentadas para a revisao):
--   * O ciclo de vida (`platform.tenant_lifecycle`, V9) NAO ganha
--     status novo aqui: o dominio dele (DELETION_GRACE/DELETION_HELD/
--     DELETED) ja cobre carencia, suspensao e eliminado, e o periodo
--     de consulta e fato proprio, com inicio/fim/usuarios — por isso
--     tabela propria ligada ao tenant, como o mapa descreve.
--   * Sem RLS, como todas as tabelas `platform` da V9/V10_1: sao
--     tabelas do console da plataforma, acessadas pelo servico da
--     plataforma, nao por consulta de negocio do tenant.
--   * `tenant_exit.tenant_id` e UNIQUE: um processo de saida aberto
--     por tenant; reabrir saida de tenant ja eliminado nao e caminho
--     deste modelo (o certificado e terminal).
--   * NENHUMA linha e semeada: tenant sintetico do ensaio nasce pela
--     aplicacao/script do ensaio, nunca por esta migration.

-- ======================================================================
-- Processo de saida
-- ======================================================================

CREATE TABLE IF NOT EXISTS platform.tenant_exit (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL UNIQUE REFERENCES public.tenant_catalog (id),
    contract_id UUID REFERENCES platform.saas_contract (id),
    -- Fotografia do fim da vigencia (saas_contract.ends_at e DATE):
    -- todos os prazos do aceite contam desta data.
    contract_end DATE NOT NULL,
    level VARCHAR(20) NOT NULL
        CHECK (level IN ('STANDARD', 'PUBLIC_SECTOR')),
    status VARCHAR(30) NOT NULL DEFAULT 'EXPORT_PENDING'
        CHECK (status IN ('EXPORT_PENDING', 'IN_CONSULTATION', 'IN_GRACE', 'HELD',
                          'ELIMINATION_SCHEDULED', 'ELIMINATED', 'CANCELLED')),
    export_format VARCHAR(10)
        CHECK (export_format IN ('CSV', 'JSON', 'XML')),
    export_due_at TIMESTAMPTZ NOT NULL,
    export_generated_at TIMESTAMPTZ,
    export_hash CHAR(64)
        CHECK (export_hash ~ '^[0-9a-f]{64}$'),
    grace_starts_at TIMESTAMPTZ NOT NULL,
    grace_ends_at TIMESTAMPTZ NOT NULL,
    certificate_due_at TIMESTAMPTZ NOT NULL,
    scheduled_by UUID,
    scheduled_at TIMESTAMPTZ,
    scheduled_mfa BOOLEAN NOT NULL DEFAULT FALSE,
    cancelled_at TIMESTAMPTZ,
    cancelled_by UUID,
    executed_at TIMESTAMPTZ,
    -- Crypto-shredding (P0.28): instante em que a destruicao da DEK do
    -- tenant foi confirmada pelo provedor de chaves. Sem ele, o status
    -- ELIMINATED e recusado pelo CHECK abaixo.
    key_destroyed_at TIMESTAMPTZ,
    backup_expires_at TIMESTAMPTZ,
    immutable_copy_expires_at TIMESTAMPTZ,
    certificate_hash CHAR(64)
        CHECK (certificate_hash ~ '^[0-9a-f]{64}$'),
    certificate_issued_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (grace_ends_at > grace_starts_at),
    CHECK (status <> 'ELIMINATION_SCHEDULED' OR (scheduled_by IS NOT NULL AND scheduled_mfa)),
    CHECK (status <> 'ELIMINATED' OR executed_at IS NOT NULL),
    CHECK (status <> 'ELIMINATED' OR key_destroyed_at IS NOT NULL),
    CHECK (status <> 'ELIMINATED' OR certificate_hash IS NOT NULL),
    CHECK (status <> 'ELIMINATED' OR certificate_issued_at IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_tenant_exit_status_due
    ON platform.tenant_exit (status, certificate_due_at);

-- ======================================================================
-- Periodo de consulta somente leitura (nivel Setor Publico)
-- ======================================================================

CREATE TABLE IF NOT EXISTS platform.tenant_readonly_period (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    exit_id UUID NOT NULL REFERENCES platform.tenant_exit (id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'ENDED', 'CANCELLED')),
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (ends_at > starts_at),
    CHECK (ends_at - starts_at <= INTERVAL '120 days')
);

CREATE INDEX IF NOT EXISTS idx_readonly_period_tenant_status
    ON platform.tenant_readonly_period (tenant_id, status);

CREATE TABLE IF NOT EXISTS platform.tenant_readonly_user (
    period_id UUID NOT NULL REFERENCES platform.tenant_readonly_period (id),
    user_id UUID NOT NULL,
    granted_by UUID,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT tenant_readonly_user_pk PRIMARY KEY (period_id, user_id)
);

-- ======================================================================
-- Contestacao, ordem judicial e download (registros do aceite)
-- ======================================================================

CREATE TABLE IF NOT EXISTS platform.tenant_exit_contestation (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    exit_id UUID NOT NULL REFERENCES platform.tenant_exit (id),
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    registered_at TIMESTAMPTZ NOT NULL,
    subject TEXT NOT NULL,
    response_due_at TIMESTAMPTZ NOT NULL,
    responded_at TIMESTAMPTZ,
    response_ref VARCHAR(500),
    alert_sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (responded_at IS NULL OR response_ref IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_exit_contestation_open
    ON platform.tenant_exit_contestation (exit_id, response_due_at)
    WHERE responded_at IS NULL;

CREATE TABLE IF NOT EXISTS platform.tenant_exit_court_order (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    exit_id UUID NOT NULL REFERENCES platform.tenant_exit (id),
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    reference VARCHAR(300) NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL,
    lifted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (lifted_at IS NULL OR lifted_at >= registered_at)
);

CREATE INDEX IF NOT EXISTS idx_exit_court_order_active
    ON platform.tenant_exit_court_order (exit_id) WHERE lifted_at IS NULL;

CREATE TABLE IF NOT EXISTS platform.tenant_exit_download (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    exit_id UUID NOT NULL REFERENCES platform.tenant_exit (id),
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    downloaded_by UUID,
    downloaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_exit_download_exit
    ON platform.tenant_exit_download (exit_id, downloaded_at DESC);

-- ======================================================================
-- Formatos da exportacao da V9: JSON, CSV ou XML (aceite da P3.16)
-- ======================================================================

DO $$
DECLARE
    check_name TEXT;
BEGIN
    SELECT conname INTO check_name
    FROM pg_constraint
    WHERE conrelid = 'platform.tenant_export'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%format%';
    IF check_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE platform.tenant_export DROP CONSTRAINT %I', check_name);
    END IF;
END $$;

ALTER TABLE platform.tenant_export
    ADD CONSTRAINT tenant_export_format_check
    CHECK (format IN ('JSON', 'CSV', 'XML'));

-- ======================================================================
-- Permissoes da role da aplicacao (padrao V9/V10_1: console escreve;
-- audit_event continua append-only e nao e tocado aqui)
-- ======================================================================

GRANT SELECT, INSERT, UPDATE ON platform.tenant_exit TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.tenant_readonly_period TO ziipsan_app;
GRANT SELECT, INSERT ON platform.tenant_readonly_user TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.tenant_exit_contestation TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.tenant_exit_court_order TO ziipsan_app;
GRANT SELECT, INSERT ON platform.tenant_exit_download TO ziipsan_app;
GRANT SELECT ON platform.tenant_exit TO ziipsan_central;
GRANT SELECT ON platform.tenant_readonly_period TO ziipsan_central;
GRANT SELECT ON platform.tenant_exit_contestation TO ziipsan_central;
GRANT SELECT ON platform.tenant_exit_court_order TO ziipsan_central;
