-- Core Migration — Version: 9
-- Description: P1.6 — console da plataforma v0 (schema `platform`).
--
-- Tabelas do plano v3.0 §3 (core `V9`): `plan`, `tenant_subscription`,
-- `feature_flag`, `onboarding_step`, `tenant_lifecycle`, `audit_event`.
-- Sem impersonacao: o acesso assistido vai para o core `V10_3` (P3.17).
-- Versao livre verificada: `V9_3` e `V9_6` existem, `V9` nao; nunca reutilizar
-- versao ocupada.
--
-- Fonte unica dos dados do tenant (DAD-32): `platform.tenant_profile` e a
-- fonte de identidade do tenant a partir desta versao — razao social, nome
-- fantasia, CNPJ em CHAR(14) sem mascara, natureza juridica por dominio,
-- endereco estruturado com `municipality_ibge`, dados de emitente fiscal e
-- encarregado de dados com contato de divulgacao publica. A situacao do
-- tenant vive em `platform.tenant_lifecycle` (dominio), nao em texto livre.
-- `public.tenant_catalog` continua como cadastro de provisionamento (id,
-- schema, cluster); seus campos legados `name`, `cnpj`, `address`, `plan` e
-- `settings` NAO sao removidos aqui: `plan`/`settings` ainda sao escritos
-- pelo provisionamento (`TenantProvisioningService`) e lidos por nenhum
-- codigo de regra, mas a remocao das colunas quebraria o INSERT do
-- provisionamento e todos os tenants de teste (`ProvisionedTenant` usa
-- `.plan("PRO")`). A remocao e pendencia coordenada com a frente do
-- provisionamento (ver o relatorio da P1.6), nao desta migration: o plano
-- passa a viver em `platform.plan`/`tenant_subscription` (o contrato, que
-- vai ao core `V10_1`, le a assinatura daqui), e o `plan` do catalogo fica
-- como instantaneo legado de provisionamento, sem leitura por regra.
-- `tenant_metadata` nao existe mais (removida na P0.25): nao ha segunda
-- fonte viva para duplicar.
--
-- Backfill do perfil: so linhas do catalogo cujo CNPJ normalizado (sem
-- pontuacao, maiusculas) tem 14 alfanumericos; CNPJ fora do formato nao
-- ganha perfil automatico — o console exige o cadastro valido no
-- onboarding, em vez de inventar dado. O DV e validado na aplicacao
-- (`DocumentNumber`), nao aqui: o banco nao chama Java.
--
-- Permissoes: a role da aplicacao escreve o console (o console e runtime,
-- ao contrario de `platform.deployment`, que e so leitura). `audit_event`
-- e append-only para a aplicacao: SELECT + INSERT, sem UPDATE/DELETE.
-- Nenhum dado pessoal de tenant alem do necessario ao console (nome e
-- contato publico do encarregado, por exigencia de divulgacao da LGPD
-- art. 41) vive aqui; CNPJ vai sem mascara mas o acesso e so do console.

CREATE SCHEMA IF NOT EXISTS platform;

-- ======================================================================
-- Planos e cotas
-- ======================================================================

CREATE TABLE IF NOT EXISTS platform.plan (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    -- Cotas do plano em JSONB (chaves livres do contrato, ex.:
    -- max_users, max_consumer_units, api_client_max). Sem semente: o
    -- plano nasce pela API do console, nunca inventado aqui.
    quotas JSONB NOT NULL DEFAULT '{}',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS platform.tenant_subscription (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    plan_id UUID NOT NULL REFERENCES platform.plan (id),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CANCELLED', 'EXPIRED')),
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ends_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Um unico plano ativo por tenant.
CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_subscription_active
    ON platform.tenant_subscription (tenant_id) WHERE status = 'ACTIVE';

-- ======================================================================
-- Flags (definicao global + override por tenant)
-- ======================================================================

CREATE TABLE IF NOT EXISTS platform.feature_flag (
    flag_key VARCHAR(100) PRIMARY KEY,
    description TEXT NOT NULL,
    default_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS platform.tenant_feature_flag (
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    flag_key VARCHAR(100) NOT NULL REFERENCES platform.feature_flag (flag_key),
    enabled BOOLEAN NOT NULL,
    set_by UUID,
    set_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT tenant_feature_flag_pk PRIMARY KEY (tenant_id, flag_key)
);

-- ======================================================================
-- Fonte unica: perfil do tenant (DAD-32)
-- ======================================================================

CREATE TABLE IF NOT EXISTS platform.tenant_profile (
    tenant_id UUID PRIMARY KEY REFERENCES public.tenant_catalog (id),
    legal_name VARCHAR(200) NOT NULL,
    trade_name VARCHAR(200),
    -- CHAR(14) sem mascara; alfanumerico (CNPJ alfanumerico, IN RFB
    -- 2.229/2024) validado por `DocumentNumber` na aplicacao.
    cnpj CHAR(14) NOT NULL CHECK (cnpj ~ '^[0-9A-Z]{14}$'),
    -- Dominio de `reg.legal_nature` (PARAMETROS §3 G1): privada,
    -- autarquia, empresa publica, SEM. Muda o regime LGPD e a fonte do
    -- CadUnico (a cessao direta do CadUnico so alcanca ente de direito
    -- publico, Decreto 11.016/2022).
    legal_nature VARCHAR(20) NOT NULL
        CHECK (legal_nature IN ('PRIVATE', 'AUTARCHY', 'PUBLIC_COMPANY', 'SEM')),
    address_street VARCHAR(200),
    address_number VARCHAR(20),
    address_district VARCHAR(100),
    address_city VARCHAR(100),
    address_state CHAR(2),
    address_postal_code VARCHAR(20),
    -- Codigo IBGE do municipio com 7 digitos.
    municipality_ibge CHAR(7) CHECK (municipality_ibge ~ '^[0-9]{7}$'),
    -- Dados de emitente fiscal (ou `fiscal_issuers`, no schema do tenant,
    -- quando o modulo fiscal existir: o perfil do console e a fonte do
    -- console, nao do documento fiscal).
    state_registration VARCHAR(30),
    tax_regime VARCHAR(20)
        CHECK (tax_regime IN ('SIMPLES_NACIONAL', 'LUCRO_PRESUMIDO', 'LUCRO_REAL', 'EXEMPT')),
    -- Encarregado de dados (LGPD art. 41): nome e contato de divulgacao
    -- publica (Res. CD/ANPD 18/2024).
    dpo_name VARCHAR(200),
    dpo_public_contact VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Backfill a partir do catalogo (so CNPJ com forma valida; sem DV aqui).
INSERT INTO platform.tenant_profile (tenant_id, legal_name, cnpj)
SELECT id, name,
       upper(regexp_replace(cnpj, '[^0-9A-Za-z]', '', 'g'))
FROM public.tenant_catalog
WHERE cnpj IS NOT NULL
  AND upper(regexp_replace(cnpj, '[^0-9A-Za-z]', '', 'g')) ~ '^[0-9A-Z]{14}$'
ON CONFLICT (tenant_id) DO NOTHING;

-- ======================================================================
-- Jornada de implantacao (onboarding_step) e ciclo de vida
-- ======================================================================

CREATE TABLE IF NOT EXISTS platform.onboarding_step (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    step_key VARCHAR(100) NOT NULL,
    title VARCHAR(300) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'DONE')),
    completed_at TIMESTAMPTZ,
    completed_by UUID,
    evidence_ref VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_onboarding_step_tenant_key UNIQUE (tenant_id, step_key)
);

CREATE TABLE IF NOT EXISTS platform.tenant_lifecycle (
    tenant_id UUID PRIMARY KEY REFERENCES public.tenant_catalog (id),
    -- Dominio da situacao do tenant. SUSPENSION_NOTICE = aviso previo
    -- correndo (backoffice ainda opera); SUSPENDED = suspensao vigente
    -- (backoffice so leitura; portal, Pix, retornos e 2a via funcionam).
    -- DELETION_GRACE = carencia de 30 dias; DELETION_HELD = eliminacao
    -- suspensa por contestacao ou ordem judicial; DELETED = eliminado
    -- (terminal, com certificado).
    status VARCHAR(20) NOT NULL DEFAULT 'ONBOARDING'
        CHECK (status IN ('ONBOARDING', 'ACTIVE', 'SUSPENSION_NOTICE', 'SUSPENDED',
                          'DELETION_GRACE', 'DELETION_HELD', 'DELETED')),
    suspension_reason VARCHAR(50)
        CHECK (suspension_reason IN ('CONTRACT_DEFAULT', 'REGULATORY_ORDER', 'FRAUD_PREVENTION', 'OTHER')),
    suspension_notice_at TIMESTAMPTZ,
    suspension_effective_at TIMESTAMPTZ,
    suspension_justification TEXT,
    suspended_by UUID,
    deletion_requested_at TIMESTAMPTZ,
    deletion_requested_by UUID,
    deletion_justification TEXT,
    -- Carencia de 30 dias: deletion_requested_at + 30 dias.
    deletion_grace_until TIMESTAMPTZ,
    deletion_hold_reason VARCHAR(20)
        CHECK (deletion_hold_reason IN ('CONTESTATION', 'COURT_ORDER')),
    deletion_hold_at TIMESTAMPTZ,
    deletion_hold_reference VARCHAR(300),
    deletion_certificate_hash CHAR(64)
        CHECK (deletion_certificate_hash ~ '^[0-9a-f]{64}$'),
    deletion_certificate_issued_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (status <> 'SUSPENDED' OR suspension_effective_at IS NOT NULL),
    CHECK (status <> 'DELETION_GRACE' OR deletion_grace_until IS NOT NULL),
    CHECK (status <> 'DELETION_HELD' OR deletion_hold_reason IS NOT NULL),
    CHECK (status <> 'DELETED' OR deletion_certificate_hash IS NOT NULL),
    CHECK (status <> 'DELETED' OR deletion_certificate_issued_at IS NOT NULL)
);

-- ======================================================================
-- Exportacao do tenant (P1.6): o pedido e o hash do conteudo exportado.
-- O conteudo em si sai pela API do console (so PLATFORM_OWNER) e nao fica
-- gravado aqui: gravar uma copia do cadastro inteiro do tenant no schema
-- platform seria uma segunda fonte de dados do tenant, contra a DAD-32.
-- status READY = exportacao gerada; o hash permite conferir a integridade
-- do arquivo entregue sem guardar o arquivo no banco.
-- ======================================================================

CREATE TABLE IF NOT EXISTS platform.tenant_export (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    requested_by UUID,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    format VARCHAR(20) NOT NULL DEFAULT 'JSON'
        CHECK (format IN ('JSON')),
    status VARCHAR(20) NOT NULL DEFAULT 'READY'
        CHECK (status IN ('READY')),
    content_hash CHAR(64) NOT NULL
        CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tenant_export_tenant_created
    ON platform.tenant_export (tenant_id, created_at DESC);

-- ======================================================================
-- Auditoria do console (toda acao do console cai aqui)
-- ======================================================================

CREATE TABLE IF NOT EXISTS platform.audit_event (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID REFERENCES public.tenant_catalog (id),
    actor_id UUID,
    actor_role VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_event_tenant_created
    ON platform.audit_event (tenant_id, created_at DESC);

-- ======================================================================
-- Permissoes da role da aplicacao
-- ======================================================================

GRANT USAGE ON SCHEMA platform TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.plan TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.tenant_subscription TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.feature_flag TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.tenant_feature_flag TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.tenant_profile TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.onboarding_step TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.tenant_lifecycle TO ziipsan_app;
GRANT SELECT, INSERT ON platform.tenant_export TO ziipsan_app;
GRANT SELECT, INSERT ON platform.audit_event TO ziipsan_app;
