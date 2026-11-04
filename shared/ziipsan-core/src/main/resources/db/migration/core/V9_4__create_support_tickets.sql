-- Core Migration — Version: 9.4.0
-- Description: P1.23 — chamados de suporte v0 e fila na Central (ADR-0020).
--
-- PLANO v3.0 §3 (core V9_4, reservada, P1): `support.ticket`, `ticket_message`,
-- `ticket_attachment`, `ticket_event` e o contador da sequência do chamado
-- `SUP-AAAA-NNNNNN` (ano + 6 dígitos, por tenant, sem duplicata em aberturas
-- paralelas: a linha do contador é travada com SELECT FOR UPDATE na mesma
-- transação que cria o chamado).
--
-- TERMO (P1.23, ver P1.36): "chamado de suporte" é o pedido do tenant AO Ziip,
-- numerado SUP-AAAA-NNNNNN. NÃO se confunde com o "protocolo de atendimento"
-- (P1.36, NR 11), que é a solicitação do consumidor AO tenant e vive em outras
-- tabelas, com outra numeração. As duas filas nunca compartilham tabela nem
-- número (ADR-0020 decisão 8).
--
-- O QUE ESTA VERSAO FAZ (só a linha P1.23):
--   * chamado numerado, com categoria, severidade S1–S5 (ADR-0025; a sugestão
--     por impacto × criticidade é do formulário, não do banco), estados
--     NEW → IN_PROGRESS → WAITING_CUSTOMER → RESOLVED → CLOSED, mais
--     CANCELLED (só a partir de NEW, pelo solicitante, antes do atendimento);
--   * conversa encadeada (ticket_message, visibilidade PUBLIC/INTERNAL — a
--     interna nunca é devolvida à API do tenant);
--   * anexos no estado do pipeline da P1.20 (QUARANTINED → CLEAN/INFECTED/
--     NOT_SCANNED), com MIME detectado pelo conteúdo, SHA-256 e chave do
--     objeto `support/<tenant>/<numero>/<anexo>`;
--   * eventos de transição, severidade, atribuição e mensagem (ticket_event).
--
-- O QUE ESTA VERSAO NAO FAZ (fica para as tarefas citadas, sem inventar nada):
--   * SLA por contrato e CSAT (`support.sla_policy`, `csat_response`): P2.15 —
--     aqui NÃO há prazo calculado, alerta de 80% nem estouro; `waiting_since`
--     registra apenas quando o chamado entrou em WAITING_CUSTOMER, para o
--     cálculo futuro;
--   * acesso assistido e de emergência: P3.17 (outras tabelas);
--   * retenção/expurgo: P3.19 — `purge_at` existe e fica NULL até o fim da
--     vigência do contrato, quando a P3.19 o fixa (vigência + 5 anos);
--   * transição automática WAITING_CUSTOMER → RESOLVED após 7 dias e
--     RESOLVED → CLOSED após 7 dias (AJUDA §5): sem job agendado nesta
--     frente; a mudança de estado é feita pela API da Central (P1.17) ou
--     pela reabertura via mensagem — o job é pendência declarada, não um
--     comportamento fingido aqui;
--   * API REST de ajuda (/help, AJUDA §6) e telas /help e /about: pendência
--     declarada — esta migration sustenta SÓ a API de chamados e a tela
--     /support.
--
-- IDS: AJUDA §4 anota "id UUIDv7", mas o PostgreSQL 17 não tem gerador de
-- UUIDv7 embutido e a V9_3 usa `uuid_generate_v4()` em todo o schema support;
-- esta migration segue a V9_3 (v4) para não introduzir um gerador caseiro.
--
-- RLS: igual à V9_3 — `tenant_id` NOT NULL e policy estrita FORCED
-- (`tenant_id = current_tenant_id()`), para a role da aplicação nunca ler
-- chamado de outro tenant; a API devolve 404 (não 403) para número de outro
-- tenant, porque a linha simplesmente não existe no contexto do solicitante.
-- A fila da Central (P1.17) NÃO lê estas tabelas direto: ela consome a API
-- de fila da plataforma, que abre o contexto por tenant (ver TicketService).
--
-- DADOS SINTETICOS APENAS: nenhuma linha semeada aqui.

-- ---------------------------------------------------------------------------
-- support.ticket_number_counter — sequência SUP-AAAA-NNNNNN por tenant e ano.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS support.ticket_number_counter (
    tenant_id UUID NOT NULL,
    year INT NOT NULL CHECK (year BETWEEN 2000 AND 2100),
    last_number INT NOT NULL DEFAULT 0 CHECK (last_number >= 0),
    PRIMARY KEY (tenant_id, year)
);

ALTER TABLE support.ticket_number_counter ENABLE ROW LEVEL SECURITY;
ALTER TABLE support.ticket_number_counter FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS ticket_number_counter_isolation ON support.ticket_number_counter;
CREATE POLICY ticket_number_counter_isolation ON support.ticket_number_counter
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

COMMENT ON TABLE support.ticket_number_counter IS
    'P1.23: último número de chamado por (tenant, ano); a linha é travada na transação de criação, sem duplicata em aberturas paralelas.';

-- ---------------------------------------------------------------------------
-- support.ticket — chamado de suporte do tenant ao Ziip.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS support.ticket (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    number TEXT NOT NULL CHECK (number ~ '^SUP-[0-9]{4}-[0-9]{6}$'),
    requester_id UUID NOT NULL,
    subject TEXT NOT NULL CHECK (char_length(subject) BETWEEN 1 AND 200),
    category TEXT NOT NULL CHECK (category IN
        ('QUESTION', 'INCIDENT', 'REQUEST', 'IMPROVEMENT', 'SECURITY', 'PRIVACY')),
    module TEXT,
    origin_route TEXT,
    app_version TEXT,
    correlation_id TEXT,
    problem_code TEXT,
    severity TEXT NOT NULL DEFAULT 'S4' CHECK (severity IN ('S1', 'S2', 'S3', 'S4', 'S5')),
    status TEXT NOT NULL DEFAULT 'NEW' CHECK (status IN
        ('NEW', 'IN_PROGRESS', 'WAITING_CUSTOMER', 'RESOLVED', 'CLOSED', 'CANCELLED')),
    level TEXT CHECK (level IN ('N1', 'N2', 'ENGINEERING')),
    assignee_id UUID,
    contains_personal_data BOOLEAN NOT NULL DEFAULT FALSE,
    reopen_count INT NOT NULL DEFAULT 0 CHECK (reopen_count >= 0),
    waiting_since TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    purge_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, number)
);

CREATE INDEX IF NOT EXISTS ticket_tenant_status_idx ON support.ticket (tenant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS ticket_tenant_requester_idx ON support.ticket (tenant_id, requester_id, created_at DESC);

ALTER TABLE support.ticket ENABLE ROW LEVEL SECURITY;
ALTER TABLE support.ticket FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS ticket_isolation ON support.ticket;
CREATE POLICY ticket_isolation ON support.ticket
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

COMMENT ON TABLE support.ticket IS
    'P1.23: chamado de suporte (SUP-AAAA-NNNNNN), distinto do protocolo de atendimento da P1.36. SLA e CSAT são P2.15; purge_at é P3.19.';
COMMENT ON COLUMN support.ticket.contains_personal_data IS
    'P1.23: marcado quando assunto ou mensagem contém CPF, CNPJ, e-mail ou telefone (ADR-0020); listas exibem o texto mascarado.';

-- ---------------------------------------------------------------------------
-- support.ticket_message — conversa encadeada; nota interna nunca vai ao tenant.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS support.ticket_message (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    ticket_id UUID NOT NULL REFERENCES support.ticket (id) ON DELETE CASCADE,
    author_type TEXT NOT NULL CHECK (author_type IN ('REQUESTER', 'AGENT', 'SYSTEM')),
    author_id UUID,
    visibility TEXT NOT NULL DEFAULT 'PUBLIC' CHECK (visibility IN ('PUBLIC', 'INTERNAL')),
    body TEXT NOT NULL CHECK (char_length(body) BETWEEN 1 AND 10000),
    contains_personal_data BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (author_type <> 'REQUESTER' OR visibility = 'PUBLIC')
);

CREATE INDEX IF NOT EXISTS ticket_message_ticket_idx ON support.ticket_message (ticket_id, created_at);

ALTER TABLE support.ticket_message ENABLE ROW LEVEL SECURITY;
ALTER TABLE support.ticket_message FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS ticket_message_isolation ON support.ticket_message;
CREATE POLICY ticket_message_isolation ON support.ticket_message
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

COMMENT ON TABLE support.ticket_message IS
    'P1.23: mensagem do chamado; INTERNAL só existe para autor AGENT/SYSTEM e nunca é devolvida à API do tenant.';

-- ---------------------------------------------------------------------------
-- support.ticket_attachment — anexo no estado do pipeline da P1.20.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS support.ticket_attachment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    ticket_id UUID NOT NULL REFERENCES support.ticket (id) ON DELETE CASCADE,
    message_id UUID REFERENCES support.ticket_message (id) ON DELETE CASCADE,
    file_name TEXT NOT NULL CHECK (char_length(file_name) BETWEEN 1 AND 255),
    detected_mime TEXT NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    sha256 TEXT NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    object_key TEXT NOT NULL,
    scan_status TEXT NOT NULL DEFAULT 'QUARANTINED' CHECK (scan_status IN
        ('QUARANTINED', 'CLEAN', 'INFECTED', 'NOT_SCANNED')),
    threat TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (scan_status <> 'INFECTED' OR threat IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS ticket_attachment_ticket_idx ON support.ticket_attachment (ticket_id);
CREATE INDEX IF NOT EXISTS ticket_attachment_message_idx ON support.ticket_attachment (message_id);

ALTER TABLE support.ticket_attachment ENABLE ROW LEVEL SECURITY;
ALTER TABLE support.ticket_attachment FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS ticket_attachment_isolation ON support.ticket_attachment;
CREATE POLICY ticket_attachment_isolation ON support.ticket_attachment
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

COMMENT ON TABLE support.ticket_attachment IS
    'P1.23: anexo do chamado, quarentena até a varredura (P1.20); download só com CLEAN. Até 5 por mensagem, 10 MB cada (AJUDA §11).';

-- ---------------------------------------------------------------------------
-- support.ticket_event — trilha de transições, severidade, atribuição e mensagem.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS support.ticket_event (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    ticket_id UUID NOT NULL REFERENCES support.ticket (id) ON DELETE CASCADE,
    event_type TEXT NOT NULL CHECK (event_type IN
        ('CREATED', 'STATUS_CHANGED', 'SEVERITY_CHANGED', 'ASSIGNED', 'MESSAGE', 'CANCELLED', 'REOPENED', 'SCAN')),
    actor_type TEXT NOT NULL CHECK (actor_type IN ('REQUESTER', 'AGENT', 'SYSTEM')),
    actor_id UUID,
    from_status TEXT,
    to_status TEXT,
    detail TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ticket_event_ticket_idx ON support.ticket_event (ticket_id, created_at);

ALTER TABLE support.ticket_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE support.ticket_event FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS ticket_event_isolation ON support.ticket_event;
CREATE POLICY ticket_event_isolation ON support.ticket_event
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

COMMENT ON TABLE support.ticket_event IS
    'P1.23: trilha do chamado; toda mutação de estado, severidade, atribuição, mensagem e varredura gera um evento.';

-- ---------------------------------------------------------------------------
-- GRANTS — a aplicação lê e grava chamados; nunca altera o contador direto
-- fora da transação de criação (o serviço trava a linha, não o SQL avulso).
-- ---------------------------------------------------------------------------
GRANT USAGE ON SCHEMA support TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON support.ticket_number_counter TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON support.ticket TO ziipsan_app;
GRANT SELECT, INSERT ON support.ticket_message TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON support.ticket_attachment TO ziipsan_app;
GRANT SELECT, INSERT ON support.ticket_event TO ziipsan_app;
