-- Core Migration — Version: 10.2
-- Description: P2.15 — SLA de suporte por contrato e CSAT (ADR-0020, ADR-0025, core V10_2).
--
-- O QUE ESTA VERSAO FAZ:
--   * `support.sla_policy`: politica de SLA de suporte, por tenant, referenciada
--     pelo contrato (ADR-0019). Calendario BUSINESS (horas uteis, com janela
--     configuravel por politica) ou ROUND_THE_CLOCK (24x7, sem salto de fim
--     de semana ou feriado); nivel STANDARD (prazos pela politica, sem multa)
--     ou PUBLIC_SECTOR (prazo de solucao, meta de aderencia e multa por
--     severidade, ADR-0025). NENHUMA politica e semeada: os valores de
--     edital (Videira, SAMAE) sao dados do contrato, criados pela API, e
--     provados nos testes — nao ha padrao de fabrica para eles.
--   * `support.sla_policy_target`: os cinco alvos S1–S5 de cada politica
--     (primeira resposta, solucao, aderencia, multa). Exatamente cinco por
--     politica: o servico recusa politica sem os cinco.
--   * `support.sla_severity_matrix`: sobrescrita opcional, por politica, da
--     matriz padrao impacto x criticidade -> severidade (o servico usa a
--     matriz padrao quando nao ha linha; AJUDA-SUPORTE-SOBRE §5).
--   * Prazos e pausa no chamado: `support.ticket` ganha `sla_policy_id`,
--     `first_response_due_at`, `resolution_due_at` e `first_response_at`;
--     `support.ticket_sla_pause` registra cada pausa (entrada em
--     WAITING_CUSTOMER) e retomada, para o prazo ser estendido so pelas
--     horas uteis da pausa, nunca pelo relogio parado do fim de semana.
--   * `support.csat_response`: uma avaliacao por chamado (nota 1–5), so
--     apos RESOLVED, dentro de 7 dias; nota 1 ou 2 marca `review_required`.
--   * Alertas de SLA: `support.ticket_event` ganha os tipos SLA_WARNING
--     (80% do prazo) e SLA_BREACHED (estouro); o servico os registra uma
--     vez por nivel, e a leitura (fila e relatorio) sempre recalcula o
--     nivel — o evento e trilha, nao o estado.
--   * PROMOCAO DA P2.12 (ponto de extensao): `platform.saas_contract.
--     sla_policy_key` vira FK para `support.sla_policy (tenant_id,
--     policy_key)`. Chave NULL (contrato sem SLA) continua valida; chave
--     desconhecida passa a ser recusada pelo banco e, antes dele, pelo
--     servico de contrato (validacao com erro 422, nao 500).
--
-- RLS: igual a V9_4 — `tenant_id` NOT NULL e policy estrita FORCED
-- (`tenant_id = current_tenant_id()`). As tabelas novas sao de tenant;
-- `sla_policy` NAO e catalogo global: cada tenant tem as suas politicas,
-- e o contrato referencia a politica do proprio tenant (a FK inclui o
-- tenant_id, por isso).
--
-- JANELA DE HORAS UTEIS: o plano e os ADRs fixam "horas uteis" sem
-- numerar a janela. A janela vive na politica (business_start_hour /
-- business_end_hour), com 8–18 como convencao desta frente, configuravel
-- por politica — confirmar a janela com o dono e pendencia declarada da
-- P2.15, nao um numero do edital.
--
-- DADOS SINTETICOS APENAS: nenhuma linha semeada aqui.

-- ---------------------------------------------------------------------------
-- support.sla_policy — politica de SLA por tenant.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS support.sla_policy (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    policy_key VARCHAR(100) NOT NULL CHECK (policy_key ~ '^[a-z0-9][a-z0-9_-]{0,99}$'),
    name VARCHAR(200) NOT NULL CHECK (char_length(name) BETWEEN 1 AND 200),
    calendar VARCHAR(20) NOT NULL DEFAULT 'BUSINESS'
        CHECK (calendar IN ('BUSINESS', 'ROUND_THE_CLOCK')),
    level VARCHAR(20) NOT NULL DEFAULT 'STANDARD'
        CHECK (level IN ('STANDARD', 'PUBLIC_SECTOR')),
    business_start_hour INT NOT NULL DEFAULT 8 CHECK (business_start_hour BETWEEN 0 AND 23),
    business_end_hour INT NOT NULL DEFAULT 18 CHECK (business_end_hour BETWEEN 1 AND 24),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sla_policy_tenant_key UNIQUE (tenant_id, policy_key),
    CHECK (business_end_hour > business_start_hour)
);

CREATE INDEX IF NOT EXISTS sla_policy_tenant_idx ON support.sla_policy (tenant_id, policy_key);

ALTER TABLE support.sla_policy ENABLE ROW LEVEL SECURITY;
ALTER TABLE support.sla_policy FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS sla_policy_isolation ON support.sla_policy;
CREATE POLICY sla_policy_isolation ON support.sla_policy
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

COMMENT ON TABLE support.sla_policy IS
    'P2.15: politica de SLA de suporte por tenant, referenciada pelo contrato (ADR-0019/ADR-0025). Sem semente: valores de edital sao dados do contrato.';

-- ---------------------------------------------------------------------------
-- support.sla_policy_target — os cinco alvos S1–S5 de cada politica.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS support.sla_policy_target (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    policy_id UUID NOT NULL REFERENCES support.sla_policy (id) ON DELETE CASCADE,
    severity VARCHAR(5) NOT NULL CHECK (severity IN ('S1', 'S2', 'S3', 'S4', 'S5')),
    first_response_hours INT NOT NULL CHECK (first_response_hours > 0),
    resolution_hours INT NOT NULL CHECK (resolution_hours > 0),
    adherence_pct NUMERIC(5, 2) NOT NULL CHECK (adherence_pct BETWEEN 0 AND 100),
    penalty_pct NUMERIC(5, 2) NOT NULL DEFAULT 0 CHECK (penalty_pct >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sla_policy_target_policy_severity UNIQUE (tenant_id, policy_id, severity)
);

CREATE INDEX IF NOT EXISTS sla_policy_target_policy_idx ON support.sla_policy_target (policy_id);

ALTER TABLE support.sla_policy_target ENABLE ROW LEVEL SECURITY;
ALTER TABLE support.sla_policy_target FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS sla_policy_target_isolation ON support.sla_policy_target;
CREATE POLICY sla_policy_target_isolation ON support.sla_policy_target
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

COMMENT ON TABLE support.sla_policy_target IS
    'P2.15: alvo por severidade (primeira resposta, solucao, aderencia, multa). Exatamente S1–S5 por politica.';

-- ---------------------------------------------------------------------------
-- support.sla_severity_matrix — sobrescrita opcional da matriz padrao.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS support.sla_severity_matrix (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    policy_id UUID NOT NULL REFERENCES support.sla_policy (id) ON DELETE CASCADE,
    impact VARCHAR(10) NOT NULL CHECK (impact IN ('HIGH', 'MEDIUM', 'LOW')),
    criticality VARCHAR(10) NOT NULL CHECK (criticality IN ('HIGH', 'MEDIUM', 'LOW')),
    severity VARCHAR(5) NOT NULL CHECK (severity IN ('S1', 'S2', 'S3', 'S4', 'S5')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sla_severity_matrix_cell UNIQUE (tenant_id, policy_id, impact, criticality)
);

ALTER TABLE support.sla_severity_matrix ENABLE ROW LEVEL SECURITY;
ALTER TABLE support.sla_severity_matrix FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS sla_severity_matrix_isolation ON support.sla_severity_matrix;
CREATE POLICY sla_severity_matrix_isolation ON support.sla_severity_matrix
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

COMMENT ON TABLE support.sla_severity_matrix IS
    'P2.15: sobrescrita por politica da matriz padrao impacto x criticidade -> severidade (sem linha, vale a matriz padrao do servico).';

-- ---------------------------------------------------------------------------
-- Prazos e primeira resposta no chamado (V9_4) + pausas do relogio.
-- ---------------------------------------------------------------------------
ALTER TABLE support.ticket ADD COLUMN IF NOT EXISTS sla_policy_id UUID REFERENCES support.sla_policy (id);
ALTER TABLE support.ticket ADD COLUMN IF NOT EXISTS first_response_due_at TIMESTAMPTZ;
ALTER TABLE support.ticket ADD COLUMN IF NOT EXISTS resolution_due_at TIMESTAMPTZ;
ALTER TABLE support.ticket ADD COLUMN IF NOT EXISTS first_response_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS support.ticket_sla_pause (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    ticket_id UUID NOT NULL REFERENCES support.ticket (id) ON DELETE CASCADE,
    paused_at TIMESTAMPTZ NOT NULL,
    resumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (resumed_at IS NULL OR resumed_at >= paused_at)
);

CREATE INDEX IF NOT EXISTS ticket_sla_pause_ticket_idx ON support.ticket_sla_pause (ticket_id, paused_at);

ALTER TABLE support.ticket_sla_pause ENABLE ROW LEVEL SECURITY;
ALTER TABLE support.ticket_sla_pause FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS ticket_sla_pause_isolation ON support.ticket_sla_pause;
CREATE POLICY ticket_sla_pause_isolation ON support.ticket_sla_pause
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

COMMENT ON TABLE support.ticket_sla_pause IS
    'P2.15: pausas do relogio de SLA (WAITING_CUSTOMER). A retomada estende os prazos so pelas horas uteis da pausa.';

-- ---------------------------------------------------------------------------
-- support.csat_response — uma avaliacao por chamado, nota 1–5.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS support.csat_response (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    ticket_id UUID NOT NULL REFERENCES support.ticket (id) ON DELETE CASCADE,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT CHECK (comment IS NULL OR char_length(comment) <= 1000),
    review_required BOOLEAN NOT NULL,
    responded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_csat_response_ticket UNIQUE (tenant_id, ticket_id)
);

CREATE INDEX IF NOT EXISTS csat_response_ticket_idx ON support.csat_response (ticket_id);

ALTER TABLE support.csat_response ENABLE ROW LEVEL SECURITY;
ALTER TABLE support.csat_response FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS csat_response_isolation ON support.csat_response;
CREATE POLICY csat_response_isolation ON support.csat_response
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

COMMENT ON TABLE support.csat_response IS
    'P2.15: CSAT do chamado (AJUDA §5): nota 1–5 apos RESOLVED, dentro de 7 dias; nota 1 ou 2 abre revisao.';

-- ---------------------------------------------------------------------------
-- Tipos de evento de SLA na trilha do chamado (V9_4, CHECK inline).
-- ---------------------------------------------------------------------------
ALTER TABLE support.ticket_event DROP CONSTRAINT IF EXISTS ticket_event_event_type_check;
ALTER TABLE support.ticket_event ADD CONSTRAINT ticket_event_event_type_check
    CHECK (event_type IN ('CREATED', 'STATUS_CHANGED', 'SEVERITY_CHANGED', 'ASSIGNED', 'MESSAGE',
                          'CANCELLED', 'REOPENED', 'SCAN',
                          'SLA_APPLIED', 'SLA_WARNING', 'SLA_BREACHED', 'SLA_PAUSED', 'SLA_RESUMED',
                          'FIRST_RESPONSE', 'CSAT_RECEIVED'));

-- ---------------------------------------------------------------------------
-- Promocao da P2.12: a referencia do contrato vira FK (tenant + chave).
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_saas_contract_sla_policy') THEN
        ALTER TABLE platform.saas_contract
            ADD CONSTRAINT fk_saas_contract_sla_policy
            FOREIGN KEY (tenant_id, sla_policy_key)
            REFERENCES support.sla_policy (tenant_id, policy_key);
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- GRANTS — padrao da V9_4/V10_1: app escreve, central so le.
-- ---------------------------------------------------------------------------
GRANT USAGE ON SCHEMA support TO ziipsan_app, ziipsan_central;
GRANT SELECT, INSERT, UPDATE ON support.sla_policy TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON support.sla_policy_target TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON support.sla_severity_matrix TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON support.ticket_sla_pause TO ziipsan_app;
GRANT SELECT, INSERT ON support.csat_response TO ziipsan_app;
GRANT SELECT ON support.sla_policy TO ziipsan_central;
GRANT SELECT ON support.sla_policy_target TO ziipsan_central;
GRANT SELECT ON support.sla_severity_matrix TO ziipsan_central;
GRANT SELECT ON support.ticket_sla_pause TO ziipsan_central;
GRANT SELECT ON support.csat_response TO ziipsan_central;
