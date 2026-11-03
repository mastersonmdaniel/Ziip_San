-- Core Migration — Version: 9.3.0
-- Description: P1.21 — schema `support` e conteúdo de ajuda (ADR-0020).
--
-- PLANO v3.0 §3 (core V9_3, reservada, P1): schema `support` com `article`,
-- `article_version`, `article_feedback`, `tour_completion` e `error_explanation`.
-- AJUDA-SUPORTE-SOBRE.md §3/§4: o conteúdo (artigos, tours, dicas, erros
-- explicados) vive versionado em `content/help`, editado por pull request; esta
-- migration cria só a estrutura de leitura. A CARGA do conteúdo é a migration
-- repetível `R__help_content.sql`, gerada de `content/help` pelo script
-- `scripts/help/generate-help-content.py` (o job `migrate` a reaplica sempre que
-- o conteúdo muda, sem nova imagem — "publicação pelo migrate", P1.21).
--
-- O QUE ESTA VERSAO NAO FAZ (fica para as tarefas citadas, sem inventar nada):
--   * chamados (`support.ticket`, ...): core V9_4, P1.23;
--   * `support.release_note_view` e `platform.document_acceptance`: core V9_5, P1.24;
--   * SLA e CSAT (`support.sla_policy`, `csat_response`): core V10_2, P2.15;
--   * acesso assistido e de emergência: core V10_3, P3.17;
--   * página de status: core V10_4, P3.18;
--   * API REST de ajuda (`GET /api/v1/help/...`, AJUDA §6) e as telas `/help`,
--     `/support`, `/about`: pendência declarada no relatório da frente — a busca
--     em português existe aqui no banco (`tsvector` + `unaccent`) e no bundle
--     gerado que o hook `useContextualHelp` lê; a API do servidor é o próximo
--     passo, não um atalho inventado nesta migration.
--
-- RLS E O CONTEUDO GLOBAL (decisão desta frente, registrada, sem decisão do
-- dono inventada): ADR-0020 decisão 1 e AJUDA §4 exigem `tenant_id` e RLS em
-- toda tabela do schema `support`. Artigo, versão e erro explicado são conteúdo
-- GLOBAL do produto (o mesmo para todos os tenants), não dado de um tenant:
-- por isso `tenant_id` é NULL nessas três tabelas e a policy aceita a linha
-- global (`tenant_id IS NULL`) ou a linha do tenant corrente. `article_feedback`
-- e `tour_completion` são dados DO usuário do tenant: `tenant_id` NOT NULL e a
-- policy é estrita (`tenant_id = current_tenant_id()`). Sem o `IS NULL`, o
-- conteúdo global ficaria invisível; sem o `FORCE`, o dono (ziipsan_migrator,
-- na mesma JVM que a aplicação) ignoraria a policy (precedente da V1).
--
-- COMPATIBILIDADE: schema novo, sem tabela anterior para migrar; nenhuma outra
-- migration cita `support`. A V9_1 (P1.17) e a V9_2 (P1.19) são de frentes
-- paralelas e ainda não estão neste checkout: o CoreMigrationRunner usa
-- outOfOrder(true), então a ordem de chegada não importa e a V9_3 aplica sobre
-- a V7 sem depender delas. `public.immutable_unaccent` vem do security.sql
-- (init do banco), como o índice trigram da V3_9 de tenant.
--
-- DADOS SINTETICOS APENAS: nenhum artigo, erro ou dica aqui descreve um tenant,
-- um usuário ou um documento real. Os códigos de erro semeados em
-- `R__help_content.sql` são os que já existem no código (`not-found`,
-- `validation`, `confirmation-required`, `data-conflict`); a cobertura total do
-- catálogo (`urn:ziipsan:problem:*`) é da P1.22, não desta frente.

CREATE SCHEMA IF NOT EXISTS support;

-- ---------------------------------------------------------------------------
-- support.article — artigo corrente (aponta para a versão vigente).
-- Slug estável: é ele que os arquivos de erro e o frontend referenciam.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS support.article (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    -- NULL = conteúdo global do produto; UUID = variação futura de um tenant.
    tenant_id UUID,
    slug TEXT NOT NULL UNIQUE,
    module TEXT NOT NULL,
    routes TEXT[] NOT NULL DEFAULT '{}',
    roles TEXT[] NOT NULL DEFAULT '{}',
    audience TEXT NOT NULL CHECK (audience IN ('tenant', 'internal')),
    min_version TEXT NOT NULL,
    current_version INT NOT NULL DEFAULT 1 CHECK (current_version >= 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS article_module_idx ON support.article (module);
CREATE INDEX IF NOT EXISTS article_routes_idx ON support.article USING GIN (routes);

ALTER TABLE support.article ENABLE ROW LEVEL SECURITY;
ALTER TABLE support.article FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS article_isolation ON support.article;
CREATE POLICY article_isolation ON support.article
    USING (tenant_id IS NULL OR tenant_id = current_tenant_id())
    WITH CHECK (tenant_id IS NULL OR tenant_id = current_tenant_id());

COMMENT ON TABLE support.article IS
    'P1.21: artigo de ajuda corrente. tenant_id NULL = conteúdo global do produto; a policy aceita a linha global ou a do tenant corrente.';
COMMENT ON COLUMN support.article.slug IS
    'P1.21: slug estável (= id do frontmatter em content/help/articles); referenciado por error_explanation.article_slug e pelo frontend.';

-- ---------------------------------------------------------------------------
-- support.article_version — cada versão publicada de um artigo, com o texto e
-- o vetor de busca em português (tsvector + unaccent, AJUDA §3).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS support.article_version (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    -- Espelha o tenant_id do artigo (NULL = global); sem ele a tabela ficaria
    -- sem policy própria, contra a regra do schema.
    tenant_id UUID,
    article_id UUID NOT NULL REFERENCES support.article (id) ON DELETE CASCADE,
    version INT NOT NULL CHECK (version >= 1),
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    keywords TEXT[] NOT NULL DEFAULT '{}',
    content_hash TEXT NOT NULL,
    search_vector TSVECTOR NOT NULL DEFAULT ''::tsvector,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (article_id, version)
);

CREATE INDEX IF NOT EXISTS article_version_article_idx ON support.article_version (article_id);
CREATE INDEX IF NOT EXISTS article_version_search_idx ON support.article_version USING GIN (search_vector);

ALTER TABLE support.article_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE support.article_version FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS article_version_isolation ON support.article_version;
CREATE POLICY article_version_isolation ON support.article_version
    USING (tenant_id IS NULL OR tenant_id = current_tenant_id())
    WITH CHECK (tenant_id IS NULL OR tenant_id = current_tenant_id());

-- Vetor de busca mantido por trigger: título + corpo + palavras-chave, com a
-- configuração `portuguese` e sem acento (immutable_unaccent é IMMUTABLE e
-- LEAKPROOF, do security.sql — `unaccent` puro não pode entrar em índice).
CREATE OR REPLACE FUNCTION support.article_search_vector_trigger()
RETURNS TRIGGER
LANGUAGE plpgsql AS $fn$
BEGIN
    NEW.search_vector := to_tsvector('portuguese',
        public.immutable_unaccent(COALESCE(NEW.title, '') || ' ' ||
                                  COALESCE(NEW.body, '') || ' ' ||
                                  COALESCE(array_to_string(NEW.keywords, ' '), '')));
    RETURN NEW;
END;
$fn$;

DROP TRIGGER IF EXISTS article_version_search_vector ON support.article_version;
CREATE TRIGGER article_version_search_vector
    BEFORE INSERT OR UPDATE OF title, body, keywords ON support.article_version
    FOR EACH ROW EXECUTE FUNCTION support.article_search_vector_trigger();

COMMENT ON TABLE support.article_version IS
    'P1.21: versão publicada de um artigo; search_vector em português, sem acento, mantido por trigger.';
COMMENT ON COLUMN support.article_version.content_hash IS
    'P1.21: SHA-256 do corpo normalizado; o gerador o usa para decidir se a versão muda na republicação.';

-- ---------------------------------------------------------------------------
-- support.article_feedback — "foi útil?" por usuário do tenant.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS support.article_feedback (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    article_id UUID NOT NULL REFERENCES support.article (id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    helpful BOOLEAN NOT NULL,
    comment TEXT,
    origin_route TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, user_id, article_id)
);

CREATE INDEX IF NOT EXISTS article_feedback_article_idx ON support.article_feedback (article_id);
CREATE INDEX IF NOT EXISTS article_feedback_tenant_user_idx ON support.article_feedback (tenant_id, user_id);

ALTER TABLE support.article_feedback ENABLE ROW LEVEL SECURITY;
ALTER TABLE support.article_feedback FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS article_feedback_isolation ON support.article_feedback;
CREATE POLICY article_feedback_isolation ON support.article_feedback
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

COMMENT ON TABLE support.article_feedback IS
    'P1.21: avaliação de artigo por usuário do tenant; um registro por (tenant, usuário, artigo).';

-- ---------------------------------------------------------------------------
-- support.tour_completion — tour concluído, por usuário e versão do tour.
-- (A definição do tour — passos e alvos data-tour — vive em content/help/tours
-- e vai ao frontend pelo bundle gerado; aqui fica só a conclusão, AJUDA §4.)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS support.tour_completion (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    tour_id TEXT NOT NULL,
    tour_version TEXT NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, user_id, tour_id, tour_version)
);

CREATE INDEX IF NOT EXISTS tour_completion_tenant_user_idx ON support.tour_completion (tenant_id, user_id);

ALTER TABLE support.tour_completion ENABLE ROW LEVEL SECURITY;
ALTER TABLE support.tour_completion FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tour_completion_isolation ON support.tour_completion;
CREATE POLICY tour_completion_isolation ON support.tour_completion
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

COMMENT ON TABLE support.tour_completion IS
    'P1.21: conclusão de tour por usuário do tenant; tour_id é a rota do tour em content/help/tours.';

-- ---------------------------------------------------------------------------
-- support.error_explanation — erro explicado, pelo código EXATO do problem+json
-- (ADR-0020 decisão 2: `urn:ziipsan:problem:<código>`, em inglês; textos em
-- português) ou pelo código de rejeição fiscal (`nfage:<cStat>`, `nfse:<código>`).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS support.error_explanation (
    code TEXT PRIMARY KEY,
    -- NULL = explicação global do produto (o caso atual); mesma regra de policy
    -- das tabelas de conteúdo, para uma variação futura por tenant.
    tenant_id UUID,
    title TEXT NOT NULL,
    explanation TEXT NOT NULL,
    causes TEXT[] NOT NULL DEFAULT '{}',
    actions TEXT[] NOT NULL DEFAULT '{}',
    article_slug TEXT REFERENCES support.article (slug) ON DELETE SET NULL,
    suggest_ticket BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (code LIKE 'urn:ziipsan:problem:%' OR code LIKE 'nfage:%' OR code LIKE 'nfse:%')
);

CREATE INDEX IF NOT EXISTS error_explanation_article_idx ON support.error_explanation (article_slug);

ALTER TABLE support.error_explanation ENABLE ROW LEVEL SECURITY;
ALTER TABLE support.error_explanation FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS error_explanation_isolation ON support.error_explanation;
CREATE POLICY error_explanation_isolation ON support.error_explanation
    USING (tenant_id IS NULL OR tenant_id = current_tenant_id())
    WITH CHECK (tenant_id IS NULL OR tenant_id = current_tenant_id());

COMMENT ON TABLE support.error_explanation IS
    'P1.21: erro explicado pelo código exato (problem+json ou rejeição fiscal); article_slug aponta o artigo de aprofundamento.';

-- ---------------------------------------------------------------------------
-- GRANTS — a aplicação lê o conteúdo e grava feedback/conclusão; nunca grava
-- artigo, versão ou explicação (só o job migrate, pela R__help_content.sql).
-- O USAGE no schema é obrigatório junto do SELECT (precedente da V5_1).
-- ---------------------------------------------------------------------------
GRANT USAGE ON SCHEMA support TO ziipsan_app;
GRANT SELECT ON support.article TO ziipsan_app;
GRANT SELECT ON support.article_version TO ziipsan_app;
GRANT SELECT ON support.error_explanation TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON support.article_feedback TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON support.tour_completion TO ziipsan_app;
