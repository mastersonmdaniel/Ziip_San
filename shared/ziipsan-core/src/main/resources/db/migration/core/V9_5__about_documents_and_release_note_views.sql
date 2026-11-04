-- Core Migration — Version: 9.5.0
-- Description: P1.24 — Sobre completo: catálogo de documentos legais, aceite
--   por usuário (platform.document_acceptance) e novidades vistas por usuário
--   (support.release_note_view). ADR-0021; AJUDA-SUPORTE-SOBRE.md §9.
--
-- PLANO v3.0 §3 (core V9_5, reservada, P1): `platform.document_acceptance` e
-- `support.release_note_view`. A terceira tabela, `platform.document`, é o
-- catálogo que a linha da P1.24 exige ("documentos com versão vigente"):
-- sem ela não haveria fonte para a "versão vigente" que o Sobre compara com
-- a "versão aceita". Ela NÃO é um segundo pipeline de documentos: o arquivo
-- do documento legal, quando publicado, passa pelo pipeline da P1.20 (tipo
-- por catálogo, classe de retenção, imutável) e o hash SHA-256 desse arquivo
-- é o `current_content_hash` daqui — a publicação futura confere a igualdade
-- dos dois hashes, em vez de guardar bytes em outro lugar.
--
-- O QUE ESTA VERSAO NAO FAZ (sem inventar nada):
--   * NENHUMA linha de documento é semeada: Termos, Política, Contrato e DPA
--     são textos legais do dono; sem texto aprovado não há versão vigente —
--     o Sobre mostra a lista vazia até a publicação, e o aceite de um código
--     sem linha no catálogo responde 404. Inventar um "v1" aqui seria
--     inventar conteúdo regulatório.
--   * Nenhum dado legal da marca (razão social, CNPJ, contato, encarregado)
--     é semeado ou exposto: o pacote branding/ziipsan/brand.json não tem o
--     bloco `legal`, e o BrandProvider já não o expõe (P0.34). Quando valores
--     reais e aprovados existirem, entram no pacote de marca — não aqui.
--   * Filtro de novidades por tenant (`affects`, ADR-0021 decisão 5): é P2.16,
--     não P1.24. A P1.24 mostra a novidade uma vez por versão por usuário;
--     a P2.16 acrescenta o filtro sem mudar estas tabelas.
--   * API REST de ajuda (`/help`, AJUDA §6) e a revisão de documentos da
--     P2.25: pendências declaradas. A P2.25 REAPROVEITA este ciclo — o
--     aceite é a "ciência" e a revisão futura acrescenta colunas a
--     `platform.document_acceptance` (ex.: reviewed_at) pela migration dela,
--     sem criar infraestrutura paralela de aceite.
--
-- CATALOGO DE CODIGOS (nunca texto livre — o equivalente em DDL ao
-- @NotBlank + catálogo da P1.20): os quatro documentos do AJUDA §9.
--   terms    = Termos de Uso
--   privacy  = Política de Privacidade
--   contract = Contrato
--   dpa      = Acordo de tratamento de dados (DPA)
--
-- IMUTABILIDADE (o portão da P1.20, aplicado ao aceite): a role da aplicação
-- (ziipsan_app) tem só SELECT no catálogo e SELECT + INSERT no aceite e nas
-- visualizações — sem UPDATE nem DELETE. Um aceite registrado nunca é
-- reescrito nem apagado pela aplicação: é evidência de auditoria (hash do
-- texto, data, IP e autenticação, como a assinatura simples da P3.34 exige
-- sobre este aceite). Re-aceitar a mesma versão é idempotente (ON CONFLICT
-- DO NOTHING no repositório) e devolve o registro original, não um novo.
--
-- RETENCAO: não há expurgo nesta frente — nenhuma linha some. A classe de
-- retenção do aceite (vigência do contrato + 5 anos, AJUDA P3) é decidida no
-- desenho do expurgo da P3, que lê estas tabelas; fixar uma classe aqui
-- seria inventar regra de retenção.
--
-- RLS: `platform.document` é catálogo GLOBAL do produto (como
-- `platform.deployment`, sem RLS, só SELECT para a aplicação). As duas
-- tabelas com dado do tenant seguem o precedente da V9_3: RLS com FORCE
-- (o migrador, dono das tabelas, também passa pela policy) e policy
-- `tenant_id = current_tenant_id()`. O repositório da aplicação fixa
-- `app.current_tenant` na transação (precedente de JdbcDocumentRepository)
-- E filtra por tenant_id em cada query — a RLS nunca é a única camada.
-- As linhas de aceite/visualização nunca são escritas pelo migrate, então
-- nenhuma policy atrapalha a migração; o catálogo é semeado pelo migrate
-- futuro da publicação, sem RLS para travar.

-- ---------------------------------------------------------------------------
-- platform.document — catálogo de documentos legais do produto.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS platform.document (
    code TEXT PRIMARY KEY
        CHECK (code IN ('terms', 'privacy', 'contract', 'dpa')),
    title TEXT NOT NULL CHECK (btrim(title) <> ''),
    current_version TEXT NOT NULL CHECK (btrim(current_version) <> ''),
    -- SHA-256 do arquivo publicado pelo pipeline da P1.20; o aceite grava o
    -- mesmo hash, para a evidência apontar o texto exato aceito.
    current_content_hash CHAR(64) NOT NULL CHECK (current_content_hash ~ '^[0-9a-f]{64}$'),
    published_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE platform.document IS
    'P1.24: catálogo global de documentos legais; current_content_hash é o SHA-256 do arquivo publicado pelo pipeline da P1.20. Sem linhas semeadas: texto legal é conteúdo do dono.';
COMMENT ON COLUMN platform.document.code IS
    'P1.24: código do catálogo (terms, privacy, contract, dpa) — nunca texto livre.';

-- ---------------------------------------------------------------------------
-- platform.document_acceptance — aceite da versão vigente, por usuário.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS platform.document_acceptance (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    document_code TEXT NOT NULL REFERENCES platform.document (code),
    document_version TEXT NOT NULL CHECK (btrim(document_version) <> ''),
    -- Hash do texto aceito (igual ao current_content_hash do catálogo no
    -- momento do aceite); é ele que a P3.34 usa como evidência da assinatura
    -- simples, junto de accepted_at, ip_address e auth_method.
    content_hash CHAR(64) NOT NULL CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ip_address INET,
    auth_method TEXT NOT NULL CHECK (btrim(auth_method) <> ''),
    UNIQUE (tenant_id, user_id, document_code, document_version)
);

CREATE INDEX IF NOT EXISTS document_acceptance_tenant_user_idx
    ON platform.document_acceptance (tenant_id, user_id);

ALTER TABLE platform.document_acceptance ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform.document_acceptance FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS document_acceptance_isolation ON platform.document_acceptance;
CREATE POLICY document_acceptance_isolation ON platform.document_acceptance
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

COMMENT ON TABLE platform.document_acceptance IS
    'P1.24 (ADR-0021): aceite de documento legal por usuário do tenant; a P2.25 reaproveita esta tabela para o ciclo de revisão e ciência, sem infraestrutura paralela.';
COMMENT ON COLUMN platform.document_acceptance.content_hash IS
    'P1.24: SHA-256 do texto aceito; evidência da assinatura simples da P3.34, com accepted_at, ip_address e auth_method.';

-- ---------------------------------------------------------------------------
-- support.release_note_view — novidade vista, uma vez por versão por usuário.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS support.release_note_view (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    -- SemVer X.Y.Z, o mesmo padrão do schema da P0.41
    -- (content/release-notes/schema.json): a versão vista é sempre uma
    -- versão publicada em content/release-notes/<version>.yaml.
    version TEXT NOT NULL CHECK (version ~ '^[0-9]+\.[0-9]+\.[0-9]+$'),
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, user_id, version)
);

CREATE INDEX IF NOT EXISTS release_note_view_tenant_user_idx
    ON support.release_note_view (tenant_id, user_id);

ALTER TABLE support.release_note_view ENABLE ROW LEVEL SECURITY;
ALTER TABLE support.release_note_view FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS release_note_view_isolation ON support.release_note_view;
CREATE POLICY release_note_view_isolation ON support.release_note_view
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

COMMENT ON TABLE support.release_note_view IS
    'P1.24 (ADR-0021 decisão 8): novidade exibida uma vez por versão por usuário, com estado no servidor. O filtro por tenant (affects) é P2.16 e não muda esta tabela.';

-- ---------------------------------------------------------------------------
-- GRANTS — a aplicação lê o catálogo e só INSERE evidência (nunca atualiza
-- nem apaga aceite ou visualização: imutabilidade do portão da P1.20).
-- O USAGE no schema platform já vem da V5_1; o USAGE no schema support vem
-- da V9_3. Sem eles, o SELECT abaixo falharia com "permission denied for
-- schema" mesmo com o GRANT na tabela (precedente da V5_1).
-- ---------------------------------------------------------------------------
GRANT SELECT ON platform.document TO ziipsan_app;
GRANT SELECT, INSERT ON platform.document_acceptance TO ziipsan_app;
GRANT SELECT, INSERT ON support.release_note_view TO ziipsan_app;
