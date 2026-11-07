-- Core Migration — Version: 10.3
-- Description: P3.17 — acesso assistido com consentimento e acesso de
-- emergencia (ADR-0020; reserva do plano v3.0 §3: core V10_3).
--
-- O QUE ESTA VERSAO FAZ:
--   * `support.assisted_consent`: o consentimento do tenant para o acesso
--     assistido — beneficiario de plataforma nomeado, escopo declarado,
--     chamado de suporte da P1.23 quando o acesso nasce dele, vigencia
--     [granted_at, expires_at) e revogacao (revoked_at/revoked_by). Sem
--     linha vigente desta tabela, o portao (AccessPolicy/AccessGuard)
--     nega o acesso assistido e audita a tentativa.
--   * `support.access_session`: a sessao de acesso, de UM dos dois modos:
--     ASSISTED vinculada ao consentimento (consent_id NOT NULL,
--     incident_id NULL) ou EMERGENCY vinculada ao incidente de seguranca
--     (incident_id NOT NULL, consent_id NULL), so para PLATFORM_OWNER
--     (CHECK + regra do servico), com o instante da reautenticacao MFA
--     da abertura gravado. Da emergencia constam ainda o prazo do aviso
--     ao tenant (notice_due_at = started_at + 24 h, calculado e provado
--     no codigo — AccessSession) e o envio (notice_sent_at). Da revogacao
--     consta must_end_by (= revoked_at + 1 min): o teto do encerramento
--     persistido depois que o tenant revoga o consentimento.
--
-- O QUE ESTA VERSAO NAO FAZ:
--   * Nao cria tabela de incidente: o registro do incidente e da P3.10
--     (modelo PersonalDataIncident, ligado a ocorrencia da P2.25), sem
--     tabela propria confirmada naquela entrega. `incident_id` e UUID
--     SEM FK de proposito — inventar a tabela aqui criaria uma segunda
--     fonte do registro. A ligacao sessao -> registro do incidente e
--     feita pelo servico (a consulta por incident_id abaixo e o registro
--     via porta IncidentSessionRegistry); promover a coluna a FK e da
--     integracao, quando a persistencia da P3.10 for confirmada.
--   * Nao cria tabela de auditoria: a trilha usa o que ja existe —
--     `public.audit_log` (tenant) e `platform.audit_event` — gravados
--     pelo AccessSessionService com o metadata/details em JSON pela
--     classe Json do core (consentId no assistido; incidentId nos dois
--     na emergencia).
--   * Nao cria tabela de aviso: o aviso ao tenant da emergencia sai pela
--     `public.outbox` da P1.9 (V8), gravada na mesma transacao da
--     abertura da sessao pelo servico.
--
-- RLS: igual a V9_4 — `tenant_id` NOT NULL e policy estrita FORCED
-- (tenant_id = current_tenant_id()). A sessao e do tenant: a Central
-- abre o contexto por tenant (set_config app.current_tenant), como no
-- TicketService; fora do contexto, a linha nao existe.
--
-- DADOS SINTETICOS APENAS: nenhuma linha semeada aqui.

-- ---------------------------------------------------------------------------
-- support.assisted_consent — consentimento do tenant para o acesso assistido.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS support.assisted_consent (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    ticket_id UUID REFERENCES support.ticket (id),
    granted_by UUID NOT NULL,
    beneficiary_id UUID NOT NULL,
    scope TEXT NOT NULL CHECK (char_length(scope) BETWEEN 1 AND 500),
    granted_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (expires_at > granted_at),
    CHECK ((revoked_at IS NULL) = (revoked_by IS NULL)),
    CHECK (revoked_at IS NULL OR revoked_at >= granted_at)
);

CREATE INDEX IF NOT EXISTS assisted_consent_tenant_idx
    ON support.assisted_consent (tenant_id, beneficiary_id, granted_at DESC);

ALTER TABLE support.assisted_consent ENABLE ROW LEVEL SECURITY;
ALTER TABLE support.assisted_consent FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS assisted_consent_isolation ON support.assisted_consent;
CREATE POLICY assisted_consent_isolation ON support.assisted_consent
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

GRANT SELECT, INSERT, UPDATE ON support.assisted_consent TO ziipsan_app;

COMMENT ON TABLE support.assisted_consent IS
    'P3.17 (ADR-0020): consentimento do tenant para acesso assistido — beneficiario nomeado, escopo, vigencia e revogacao. Sem linha vigente, o acesso e negado e auditado.';

-- ---------------------------------------------------------------------------
-- support.access_session — sessao assistida ou de emergencia.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS support.access_session (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    mode TEXT NOT NULL CHECK (mode IN ('ASSISTED', 'EMERGENCY')),
    actor_id UUID NOT NULL,
    actor_role TEXT NOT NULL CHECK (actor_role IN
        ('PLATFORM_OWNER', 'PLATFORM_OPS', 'PLATFORM_SUPPORT', 'PLATFORM_FINANCE')),
    consent_id UUID REFERENCES support.assisted_consent (id),
    incident_id UUID,
    ticket_id UUID REFERENCES support.ticket (id),
    started_at TIMESTAMPTZ NOT NULL,
    mfa_step_up_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    end_reason TEXT,
    must_end_by TIMESTAMPTZ,
    notice_due_at TIMESTAMPTZ,
    notice_sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK ((mode = 'ASSISTED' AND consent_id IS NOT NULL AND incident_id IS NULL
            AND notice_due_at IS NULL AND notice_sent_at IS NULL)
        OR (mode = 'EMERGENCY' AND incident_id IS NOT NULL AND consent_id IS NULL
            AND actor_role = 'PLATFORM_OWNER' AND mfa_step_up_at IS NOT NULL
            AND notice_due_at IS NOT NULL)),
    CHECK ((ended_at IS NULL) = (end_reason IS NULL)),
    CHECK (notice_sent_at IS NULL OR notice_due_at IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS access_session_tenant_consent_idx
    ON support.access_session (tenant_id, consent_id, started_at DESC);
CREATE INDEX IF NOT EXISTS access_session_tenant_incident_idx
    ON support.access_session (tenant_id, incident_id, started_at DESC);
CREATE INDEX IF NOT EXISTS access_session_tenant_actor_idx
    ON support.access_session (tenant_id, actor_id, started_at DESC);

ALTER TABLE support.access_session ENABLE ROW LEVEL SECURITY;
ALTER TABLE support.access_session FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS access_session_isolation ON support.access_session;
CREATE POLICY access_session_isolation ON support.access_session
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

GRANT SELECT, INSERT, UPDATE ON support.access_session TO ziipsan_app;

COMMENT ON TABLE support.access_session IS
    'P3.17 (ADR-0020): sessao de acesso de plataforma ao tenant — ASSISTED com consent_id, EMERGENCY com incident_id (sem FK: o registro do incidente e da P3.10), PLATFORM_OWNER e MFA da abertura; notice_due_at = started_at + 24 h na emergencia; must_end_by = revogacao + 1 min no assistido.';
COMMENT ON COLUMN support.access_session.incident_id IS
    'P3.17: incidente de seguranca declarado (registro da P3.10 / ocorrencia da P2.25). Sem FK nesta versao — ver o cabecalho da migration.';
