-- ED-06 — Controle de acesso de edital (ADR-0040, ADR-0043) — SQL PENDENTE.
--
-- POR QUE PENDENTE: o mapa Flyway do plano v3.0 (§3) NÃO reserva versão
-- para a ED-06 (conferido em 07/11/2026: platform/tenant vai de V1_1 a
-- V1_18 nas reservas existentes, nenhuma atribuída à ED-06). Pela regra do
-- projeto, número NÃO se inventa: este SQL entra como migration tenant do
-- módulo platform na primeira versão livre que a integração reservar,
-- renomeando o arquivo para V<versão>__ed06_access_control.sql.
--
-- O QUE MUDA (schema do tenant; RLS só do tenant, como na P3.30):
--   * composite_profiles / user_composite_profiles: perfis compostos do
--     tenant (ED-06), criados a partir dos perfis de fábrica da P1.37;
--     conjuntos em JSONB gravados pela classe Json do core;
--   * user_negative_restrictions: restrição negativa por usuário;
--   * access_delegations: delegação temporária limitada ao delegante;
--   * nist_policy_acceptances: aceite registrado do ADMIN para opção
--     contrária ao NIST SP 800-63B-4 (expiração periódica / composição);
--   * login_attempt_states: contador de tentativas por usuário, bloqueio
--     e último login (o autenticador próprio no Keycloak grava aqui via
--     serviço; a contagem por IP fica no armazenamento do autenticador,
--     fora do schema do tenant, porque IP de origem não é dado do tenant);
--   * active_sessions: espelho das sessões ativas para a consulta do
--     ADMIN (a sessão autoritativa é a do Keycloak; login único é
--     imposto na abertura, que apaga a linha anterior do usuário);
--   * recertification_campaigns / recertification_decisions: campanhas de
--     recertificação e o registro de cada decisão (evidência da ED-73).
-- Sem semente: nenhum perfil composto, restrição ou campanha nasce de
-- fábrica — são atos do administrador do tenant.

CREATE TABLE IF NOT EXISTS composite_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL CHECK (btrim(code) <> ''),
    name VARCHAR(200) NOT NULL CHECK (btrim(name) <> ''),
    factory_profiles JSONB NOT NULL DEFAULT '[]'::jsonb,
    direct_permissions JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    active BOOLEAN NOT NULL DEFAULT TRUE
);
ALTER TABLE composite_profiles ADD CONSTRAINT uq_composite_profiles_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE composite_profiles ADD CONSTRAINT uq_composite_profiles_code UNIQUE (tenant_id, code);

CREATE TABLE IF NOT EXISTS user_composite_profiles (
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    profile_id UUID NOT NULL,
    assigned_by UUID NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, user_id, profile_id),
    CONSTRAINT fk_user_composite_profiles_profile
        FOREIGN KEY (tenant_id, profile_id) REFERENCES composite_profiles (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_user_composite_profiles_user
    ON user_composite_profiles (tenant_id, user_id);

CREATE TABLE IF NOT EXISTS user_negative_restrictions (
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    denied_permissions JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, user_id)
);

CREATE TABLE IF NOT EXISTS access_delegations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    delegator_id UUID NOT NULL,
    delegate_id UUID NOT NULL,
    permissions JSONB NOT NULL DEFAULT '[]'::jsonb,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (valid_to > valid_from),
    CHECK (delegator_id <> delegate_id)
);
ALTER TABLE access_delegations ADD CONSTRAINT uq_access_delegations_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_access_delegations_delegate
    ON access_delegations (tenant_id, delegate_id, valid_to);

CREATE TABLE IF NOT EXISTS nist_policy_acceptances (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    accepted_by UUID NOT NULL,
    option_key VARCHAR(60) NOT NULL
        CHECK (option_key IN ('sec.password_max_age_days', 'sec.password_composition_required')),
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ
);
ALTER TABLE nist_policy_acceptances ADD CONSTRAINT uq_nist_acceptances_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_nist_acceptances_tenant_option
    ON nist_policy_acceptances (tenant_id, option_key, accepted_at);

CREATE TABLE IF NOT EXISTS login_attempt_states (
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    consecutive_failures INT NOT NULL DEFAULT 0 CHECK (consecutive_failures >= 0),
    locked_until TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, user_id)
);

CREATE TABLE IF NOT EXISTS active_sessions (
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    session_id VARCHAR(120) NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, user_id),
    CONSTRAINT uq_active_sessions_session UNIQUE (tenant_id, session_id)
);

CREATE TABLE IF NOT EXISTS recertification_campaigns (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL CHECK (btrim(name) <> ''),
    opens_at TIMESTAMPTZ NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    CHECK (due_at > opens_at)
);
ALTER TABLE recertification_campaigns ADD CONSTRAINT uq_recert_campaigns_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS recertification_decisions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    campaign_id UUID NOT NULL,
    subject_user_id UUID NOT NULL,
    decided_by UUID NOT NULL,
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('KEEP', 'REVOKE')),
    note VARCHAR(1000) NOT NULL CHECK (btrim(note) <> ''),
    decided_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_recert_decisions_campaign
        FOREIGN KEY (tenant_id, campaign_id) REFERENCES recertification_campaigns (tenant_id, id)
);
ALTER TABLE recertification_decisions ADD CONSTRAINT uq_recert_decisions_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_recert_decisions_subject
    ON recertification_decisions (tenant_id, campaign_id, subject_user_id);

SELECT register_tenant_table('composite_profiles');
SELECT register_tenant_table('user_composite_profiles');
SELECT register_tenant_table('user_negative_restrictions');
SELECT register_tenant_table('access_delegations');
SELECT register_tenant_table('nist_policy_acceptances');
SELECT register_tenant_table('login_attempt_states');
SELECT register_tenant_table('active_sessions');
SELECT register_tenant_table('recertification_campaigns');
SELECT register_tenant_table('recertification_decisions');

-- Classificação de dados (P0.11): ids de usuário são identificadores de
-- operador do tenant (classe ID), como nas demais tabelas do produto.
INSERT INTO public.data_classification (table_name, column_name, class, reason) VALUES
    ('composite_profiles', 'created_by', 'ID', 'identificador de quem criou o perfil composto (ED-06)'),
    ('user_composite_profiles', 'user_id', 'ID', 'identificador do usuario com perfil composto (ED-06)'),
    ('user_negative_restrictions', 'user_id', 'ID', 'identificador do usuario restrito (ED-06)'),
    ('access_delegations', 'delegator_id', 'ID', 'identificador do delegante (ED-06)'),
    ('access_delegations', 'delegate_id', 'ID', 'identificador do delegado (ED-06)'),
    ('login_attempt_states', 'user_id', 'ID', 'identificador do usuario das tentativas (ED-06)'),
    ('active_sessions', 'user_id', 'ID', 'identificador do usuario da sessao (ED-06)'),
    ('recertification_decisions', 'subject_user_id', 'ID', 'identificador do usuario recertificado (ED-06)'),
    ('recertification_decisions', 'decided_by', 'ID', 'identificador de quem decidiu a recertificacao (ED-06)')
ON CONFLICT (table_name, column_name) DO NOTHING;

COMMENT ON TABLE composite_profiles IS
    'ED-06: perfis compostos do tenant, criados pelo ADMIN a partir dos perfis de fabrica da P1.37 (ADR-0040).';
COMMENT ON TABLE user_negative_restrictions IS
    'ED-06: restricao negativa por usuario — subtrai permissoes das efetivas, inclusive de perfil de fabrica.';
COMMENT ON TABLE access_delegations IS
    'ED-06: delegacao temporaria de permissoes, limitada ao que o delegante possui e a vigencia.';
COMMENT ON TABLE nist_policy_acceptances IS
    'ED-06: aceite registrado do ADMIN do tenant para opcao contraria ao NIST SP 800-63B-4 (ADR-0040).';
COMMENT ON TABLE login_attempt_states IS
    'ED-06: tentativas de login por usuario (contador, bloqueio e ultimo login) do autenticador proprio no Keycloak.';
COMMENT ON TABLE active_sessions IS
    'ED-06: espelho da sessao ativa por usuario (login unico); a sessao autoritativa e a do Keycloak.';
COMMENT ON TABLE recertification_campaigns IS
    'ED-06: campanhas de recertificacao periodica de acessos pelo ADMIN do tenant.';
COMMENT ON TABLE recertification_decisions IS
    'ED-06: registro de cada decisao de recertificacao (manter/revogar, com justificativa) — evidencia usada pela ED-73.';
