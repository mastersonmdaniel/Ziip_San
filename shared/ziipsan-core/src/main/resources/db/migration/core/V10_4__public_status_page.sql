-- Core Migration — Version: 10.4
-- Description: P3.18 — pagina publica de status (ADR-0020, RNF-54, core V10_4).
--
-- Tabelas do plano v3.0 §3 (core `V10_4`, reservada a P3.18):
-- `support.status_component`, `status_incident`, `status_update` e
-- `maintenance_window`. A ligacao incidente <-> componentes e a tabela de
-- juncao `support.status_incident_component` (complemento de modelagem
-- declarado: um incidente afeta N componentes e um componente aparece em
-- N incidentes; sem ela, o aceite "status muda com a sonda/incidente"
-- nao teria onde registrar o afetado — mesmo tipo de decisao documentada
-- das migrations V10_1/V10_2).
--
-- DESENHO (o que o aceite exige do banco):
--   * O status EXIBIDO nao e gravado: nao ha coluna `status` editavel em
--     `status_component`. O que se grava e o FATO observavel — o resultado
--     da ultima sonda (`last_probe_outcome`/`last_probe_at`, escritos so
--     pelo coletor da sonda), os incidentes abertos e as janelas ativas —
--     e o status publico e DERIVADO deles a cada leitura por
--     `PublicStatusService` (platform). "Status muda sozinho com a sonda
--     fora" e, portanto, uma propriedade da derivacao, nao uma flag que
--     alguem precise lembrar de virar.
--   * Janela de manutencao com 72 h de antecedencia: a regra vive no
--     servico (`MaintenanceWindow`) E aqui, como CHECK
--     (`starts_at >= published_at + 72 h`): nem um INSERT direto publica
--     janela em cima da hora.
--   * Resumo pos-incidente publicavel (modelo da P3.11, PLT-23):
--     `status_incident.post_incident_summary` + `summary_published_at`
--     nascem e morrem juntos (CHECK de par). A revisao interna completa
--     NAO vive nesta tabela e nunca e publicada; so o bloco "Resumo
--     publicavel" do modelo da P3.11 chega aqui, e so depois de
--     `RESOLVED` (o servico recusa publicar resumo de incidente aberto).
--
-- RLS: estas tabelas NAO tem `tenant_id` e NAO habilitam RLS. A pagina de
-- status e conteudo GLOBAL da plataforma (o servico esta no ar ou nao
-- esta; nao existe "status por tenant"), como as tabelas globais de
-- `platform` (V9). A leitura publica passa SOMENTE pelo endpoint
-- `GET /api/v1/public/status`, que expoe a visao derivada e filtrada
-- (componentes visiveis, resumos so quando publicados) — nunca a tabela.
--
-- DADOS SINTETICOS APENAS: nenhuma linha semeada. Componentes, sondas e
-- incidentes reais sao cadastrados pela operacao; inventar um componente
-- aqui faria a pagina nascer afirmando um servico que ninguem mediu.

-- ---------------------------------------------------------------------------
-- support.status_component — componente exibido na pagina.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS support.status_component (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    component_key VARCHAR(100) NOT NULL
        CHECK (component_key ~ '^[a-z0-9][a-z0-9._-]{0,99}$'),
    name VARCHAR(200) NOT NULL CHECK (char_length(name) BETWEEN 1 AND 200),
    description VARCHAR(500),
    -- Chave da sonda que alimenta o componente (sonda sintetica da P0.27 /
    -- LC.7). NULL = componente sem sonda: o status deriva so de incidentes
    -- e janelas (ver PublicStatusService). Sem FK: a sonda nao e uma tabela
    -- deste banco nesta fase, e uma FK inventada nao apontaria para nada.
    probe_key VARCHAR(200),
    -- Ultimo fato da sonda. Os dois campos nascem e morrem juntos: sonda
    -- que nunca rodou e (NULL, NULL) — "nunca medido", que a derivacao
    -- mostra como UNKNOWN, nunca como OPERATIONAL inventado.
    last_probe_outcome VARCHAR(10)
        CHECK (last_probe_outcome IN ('UP', 'DOWN')),
    last_probe_at TIMESTAMPTZ,
    sort_order INT NOT NULL DEFAULT 0,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_status_component_key UNIQUE (component_key),
    CHECK ((last_probe_outcome IS NULL) = (last_probe_at IS NULL))
);

COMMENT ON TABLE support.status_component IS
    'P3.18: componente da pagina publica de status. Sem coluna de status editavel: o status publico e derivado da sonda, dos incidentes e das janelas por PublicStatusService.';

-- ---------------------------------------------------------------------------
-- support.status_incident — incidente que afeta um ou mais componentes.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS support.status_incident (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(200) NOT NULL CHECK (char_length(title) BETWEEN 1 AND 200),
    impact VARCHAR(20) NOT NULL CHECK (impact IN ('MINOR', 'MAJOR', 'CRITICAL')),
    status VARCHAR(20) NOT NULL DEFAULT 'INVESTIGATING'
        CHECK (status IN ('INVESTIGATING', 'IDENTIFIED', 'MONITORING', 'RESOLVED')),
    started_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    -- Bloco "Resumo publicavel" do modelo de revisao pos-incidente da
    -- P3.11 (PLT-23). NULL ate a publicacao; os dois campos juntos.
    post_incident_summary TEXT
        CHECK (post_incident_summary IS NULL OR char_length(post_incident_summary) >= 1),
    summary_published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (status <> 'RESOLVED' OR resolved_at IS NOT NULL),
    CHECK (resolved_at IS NULL OR resolved_at >= started_at),
    CHECK ((post_incident_summary IS NULL) = (summary_published_at IS NULL))
);

CREATE INDEX IF NOT EXISTS status_incident_open_idx
    ON support.status_incident (started_at DESC) WHERE status <> 'RESOLVED';

COMMENT ON TABLE support.status_incident IS
    'P3.18: incidente da pagina de status. post_incident_summary e o resumo publicavel da revisao pos-incidente (modelo P3.11/PLT-23); a revisao interna completa nao e publicada.';

CREATE TABLE IF NOT EXISTS support.status_incident_component (
    incident_id UUID NOT NULL REFERENCES support.status_incident (id) ON DELETE CASCADE,
    component_id UUID NOT NULL REFERENCES support.status_component (id),
    PRIMARY KEY (incident_id, component_id)
);

CREATE INDEX IF NOT EXISTS status_incident_component_component_idx
    ON support.status_incident_component (component_id);

-- ---------------------------------------------------------------------------
-- support.status_update — atualizacao publica de um incidente.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS support.status_update (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    incident_id UUID NOT NULL REFERENCES support.status_incident (id) ON DELETE CASCADE,
    body TEXT NOT NULL CHECK (char_length(body) >= 1),
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('INVESTIGATING', 'IDENTIFIED', 'MONITORING', 'RESOLVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS status_update_incident_idx
    ON support.status_update (incident_id, created_at);

COMMENT ON TABLE support.status_update IS
    'P3.18: atualizacao publica de incidente (linha do tempo exibida na pagina).';

-- ---------------------------------------------------------------------------
-- support.maintenance_window — janela de manutencao publicada.
-- A antecedencia minima de 72 h e CHECK do banco e regra do servico.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS support.maintenance_window (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    -- NULL = janela global (todos os componentes visiveis).
    component_id UUID REFERENCES support.status_component (id),
    title VARCHAR(200) NOT NULL CHECK (char_length(title) BETWEEN 1 AND 200),
    description TEXT,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (ends_at > starts_at),
    CHECK (starts_at >= published_at + INTERVAL '72 hours')
);

CREATE INDEX IF NOT EXISTS maintenance_window_starts_idx
    ON support.maintenance_window (starts_at);

COMMENT ON TABLE support.maintenance_window IS
    'P3.18: janela de manutencao da pagina de status. O CHECK de 72 h impede publicar janela com menos de 72 h de antecedencia, inclusive por INSERT direto.';

-- ---------------------------------------------------------------------------
-- GRANTS — padrao da V10_2: app escreve, central so le.
-- ---------------------------------------------------------------------------
GRANT USAGE ON SCHEMA support TO ziipsan_app, ziipsan_central;
GRANT SELECT, INSERT, UPDATE ON support.status_component TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON support.status_incident TO ziipsan_app;
GRANT SELECT, INSERT, DELETE ON support.status_incident_component TO ziipsan_app;
GRANT SELECT, INSERT ON support.status_update TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON support.maintenance_window TO ziipsan_app;
GRANT SELECT ON support.status_component TO ziipsan_central;
GRANT SELECT ON support.status_incident TO ziipsan_central;
GRANT SELECT ON support.status_incident_component TO ziipsan_central;
GRANT SELECT ON support.status_update TO ziipsan_central;
GRANT SELECT ON support.maintenance_window TO ziipsan_central;
