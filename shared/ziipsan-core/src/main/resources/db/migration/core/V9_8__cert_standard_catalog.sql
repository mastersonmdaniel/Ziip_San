-- Core Migration — Version: 9.8
-- Description: P1.32 — catalogo global de normas do modulo Certificacoes de
-- qualidade (ADR-0041; plano v3.0 §3, core V9_8, reservada, P1).
--
-- O QUE ESTA VERSAO CRIA (so estrutura; nenhuma linha de catalogo):
--   * platform.cert_standard: uma linha por norma, com a classe em quatro
--     valores (REGULATORY, VOLUNTARY, REFERENCE, EXPORT_PROFILE), a chave
--     estavel do catalogo (catalog_key), a situacao de vigencia e o selo de
--     verificacao. As colunas de situacao, selo, area, modulo, agencia, fonte
--     oficial e vigencias estruturadas (effective_from / effective_to) ja
--     nascem aqui porque a carga da P1.34 (migration repetivel R__norm_catalog)
--     escreve nestas tabelas: criar as colunas so na P1.34 exigiria ALTER
--     sobre um catalogo ja publicado, e a P1.34 nao pode trazer migration
--     versionada nova (a reserva do §3 para a carga e a repetivel R__).
--   * platform.cert_standard_edition: edicoes por norma, com vigencia. A
--     vigencia futura (effective_from no futuro) e o mecanismo pelo qual a
--     P1.34 fara a transicao VIGENCIA_FUTURA -> VIGENTE na data de efeito;
--     a transicao automatica em si e logica da P1.34, nao desta migration.
--   * platform.cert_clause: numero da clausula e mapa em palavras proprias
--     (titulo e resumo escritos pela curadoria, nunca texto da norma —
--     ADR-0041 item 11; textos pagos ficam fora do repositorio, P1.28).
--
-- O QUE ESTA VERSAO DELIBERADAMENTE NAO FAZ:
--   * Nenhuma linha semeada: o catalogo nasce VAZIO e so a carga da P1.34,
--     a partir do catalogo consolidado em docs/normas, o popula. Semear
--     aqui uma norma real seria inventar conteudo regulatorio; semear uma
--     norma sintetica no catalogo de produto contaminaria a carga da P1.34.
--     Os testes desta frente inserem normas sinteticas proprias (prefixo
--     SYN-) e as removem, sem tocar neste arquivo.
--   * Nenhum texto de norma, nenhuma edicao fixada: as edicoes em uso por
--     tenant (cert.<norma>.edition, ja no catalogo do core V3 da P0.19)
--     sao validadas contra as edicoes deste catalogo pelo servico
--     (CertificationService), nao por CHECK aqui — o catalogo vazio nao
--     pode listar edicoes que ainda nao recebeu.
--   * Nenhuma regra de RLS: o catalogo e global do produto, o mesmo para
--     todos os tenants, como public.parameter_definitions (core V3): a
--     role da aplicacao (ziipsan_app) tem so SELECT, e a escrita e do
--     migrador (a carga R__ da P1.34). Uma policy por tenant aqui
--     esconderia o catalogo de quem precisa le-lo.
--
-- COMPATIBILIDADE: schema platform ja existe (core V6_1). Nenhuma outra
-- migration cita estas tabelas; a V21_1 (tenant, P1.32) referencia o
-- catalogo por codigo/chave, sem FK entre schemas (o servico valida a
-- existencia no catalogo no momento da ativacao; um FK entre o schema do
-- tenant e o schema platform quebraria o provisionamento de tenants
-- criados antes desta versao, cujo schema seria recriado sem o catalogo
-- na mesma transacao — ver o precedente da V9_1, que tambem evita FK
-- para tabelas fora do seu schema quando a ordem nao e garantida).

CREATE TABLE IF NOT EXISTS platform.cert_standard (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    catalog_key TEXT NOT NULL CHECK (char_length(btrim(catalog_key)) > 0),
    code TEXT NOT NULL CHECK (char_length(btrim(code)) > 0),
    title TEXT NOT NULL CHECK (char_length(btrim(title)) > 0),
    summary TEXT,
    standard_class TEXT NOT NULL
        CHECK (standard_class IN ('REGULATORY', 'VOLUNTARY', 'REFERENCE', 'EXPORT_PROFILE')),
    area TEXT,
    module TEXT,
    agency TEXT,
    verification_seal TEXT
        CHECK (verification_seal IN ('OFICIAL', 'SECUNDARIA', 'NAO_CONFIRMADA')),
    status TEXT NOT NULL DEFAULT 'VIGENTE'
        CHECK (status IN ('VIGENTE', 'VIGENCIA_FUTURA', 'REVOGADA', 'SUBSTITUIDA')),
    official_source_url TEXT,
    effective_from DATE,
    effective_to DATE,
    superseded_by_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from),
    CONSTRAINT uq_cert_standard_catalog_key UNIQUE (catalog_key),
    CONSTRAINT uq_cert_standard_code UNIQUE (code),
    CONSTRAINT fk_cert_standard_superseded_by
        FOREIGN KEY (superseded_by_id) REFERENCES platform.cert_standard (id)
);

CREATE TABLE IF NOT EXISTS platform.cert_standard_edition (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    standard_id UUID NOT NULL REFERENCES platform.cert_standard (id) ON DELETE CASCADE,
    edition TEXT NOT NULL CHECK (char_length(btrim(edition)) > 0),
    effective_from DATE,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from),
    CONSTRAINT uq_cert_standard_edition UNIQUE (standard_id, edition)
);
CREATE INDEX IF NOT EXISTS idx_cert_standard_edition_standard
    ON platform.cert_standard_edition (standard_id);

CREATE TABLE IF NOT EXISTS platform.cert_clause (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    edition_id UUID NOT NULL REFERENCES platform.cert_standard_edition (id) ON DELETE CASCADE,
    clause_number TEXT NOT NULL CHECK (char_length(btrim(clause_number)) > 0),
    title TEXT NOT NULL CHECK (char_length(btrim(title)) > 0),
    summary TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cert_clause_edition_number UNIQUE (edition_id, clause_number)
);
CREATE INDEX IF NOT EXISTS idx_cert_clause_edition ON platform.cert_clause (edition_id);

GRANT SELECT ON platform.cert_standard TO ziipsan_app;
GRANT SELECT ON platform.cert_standard_edition TO ziipsan_app;
GRANT SELECT ON platform.cert_clause TO ziipsan_app;
