-- Parametros do tenant (P0.19): versoes com vigencia, aceite formal e perfis regulatorios.
-- Version: 1.5.0
--
-- Desenho (ADR-0002, ADR-0015, plano v3.0 P0.19):
--   * parameter_versions: um valor por chave, com vigencia [valid_from, valid_to].
--     Alteracao gera versao nova, nunca edita a anterior. Sobreposicao de vigencias da
--     mesma chave no mesmo tenant e recusada pelo banco (EXCLUDE, como tariff_versions).
--   * Quem propoe nao aprova: status APPROVED exige approved_by diferente de proposed_by,
--     approved_at preenchido e valid_from >= data da aprovacao (efeito so futuro).
--   * parameter_acceptances: o aceite formal de cada versao aprovada — quem aceitou (nome
--     e cargo), quando, hash do conteudo canonico e base normativa citada. Uma versao so
--     conta como "aceita e vigente" para a operacao quando tem aceite registrado.
--   * regulatory_profiles: perfil da agencia reguladora (base da P2.31, que carrega os
--     perfis e acrescenta colunas de origem na V1_13). Aqui, a base: agencia, ato, vigencia.
--   * holidays: sai do V6_1 (plano v3.0) e passa a viver aqui, com o ponto facultativo
--     (kind = OPTIONAL_POINT) ao lado do feriado (kind = HOLIDAY).
--
-- ORDEM COM O V6_1 (leia antes de mexer): a V1_5 roda ANTES da V6_1 (ordem Flyway).
-- Num banco virgem, esta migration cria holidays com a forma completa (inclusive as
-- CHECKs de escopo do V6_1 e a CHECK de kind); o CREATE TABLE IF NOT EXISTS do V6_1
-- vira no-op e ele so acrescenta as constraints e o indice com os nomes dele. Num banco
-- onde o V6_1 ja foi aplicado, o CREATE IF NOT EXISTS aqui vira no-op e o bloco DO
-- abaixo acrescenta a coluna kind e a CHECK de kind, sem tocar nas constraints do V6_1.
-- Por isso esta migration NAO cria constraints nem indice com os nomes do V6_1. Mas ela
-- SIM chama register_tenant_table('holidays') (correcao da integracao resto-do-P0): o
-- registro tem de vir da migration que cria a tabela — depender do V6_1 (outro modulo)
-- deixava holidays sem RLS em classpaths sem o modulo collection, e o teste
-- enablesRowLevelSecurityOnEveryTable do platform (que roda so com as migrations do
-- platform) pegou 8/9 tabelas com RLS. A chamada e idempotente (policy guardada por
-- IF NOT EXISTS), entao o V6_1 continua registrando sem conflito em qualquer ordem.
-- As tres tabelas novas seguem o padrao: register_tenant_table (RLS forcada + trilha).
--
-- NENHUM valor e semeado: perfis de agencia, feriados e valores de parametro sao dados
-- do tenant (ou oficiais, com fonte propria). Um padrao de fabrica (ex.: timezone
-- America/Sao_Paulo, multa 2%) vive no catalogo do core V3, nao como linha aqui.
--
-- Depende de V1_2 (register_tenant_table) e de btree_gist (init do banco, como V4_1).

-- =======================================================================
-- VERSOES DE PARAMETRO
-- =======================================================================

CREATE TABLE IF NOT EXISTS parameter_versions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    definition_key TEXT NOT NULL,
    -- Valor canonico em JSON (numero, texto, booleano, lista ou objeto, conforme o tipo
    -- da definicao no catalogo do core V3). Sem DEFAULT: ausencia de valor e declarada,
    -- nunca assumida; a operacao sem valor aceito e vigente falha com erro explicito.
    value JSONB NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    proposed_by UUID NOT NULL,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'PROPOSED'
        CHECK (status IN ('PROPOSED', 'APPROVED', 'REJECTED')),
    -- Hash do conteudo canonico (chave + valor + vigencia), conferido pelo aceite.
    content_hash VARCHAR(64) NOT NULL CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    -- Norma citada pelo tenant para este valor (obrigatoria fora do padrao de fabrica,
    -- exigida sempre acima do limite da classe b, ex.: multa acima de 2%).
    norm_citation VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    -- Quem propoe nao aprova; aprovacao exige data e efeito so futuro.
    CHECK (status <> 'APPROVED' OR (approved_by IS NOT NULL AND approved_by <> proposed_by)),
    CHECK (status <> 'APPROVED' OR approved_at IS NOT NULL),
    CHECK (status <> 'APPROVED' OR valid_from >= approved_at::DATE)
);
ALTER TABLE parameter_versions ADD CONSTRAINT uq_parameter_versions_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE parameter_versions ADD CONSTRAINT excl_parameter_versions_no_overlap
    EXCLUDE USING gist (tenant_id WITH =, definition_key WITH =,
                        daterange(valid_from, valid_to, '[]') WITH &&);
CREATE INDEX IF NOT EXISTS idx_parameter_versions_key_from
    ON parameter_versions(tenant_id, definition_key, valid_from);

-- =======================================================================
-- ACEITES FORMAIS
-- =======================================================================

CREATE TABLE IF NOT EXISTS parameter_acceptances (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    version_id UUID NOT NULL,
    accepted_by UUID NOT NULL,
    -- Nome e cargo de quem aceitou (ADR-0002): dado pessoal; a trilha de auditoria o
    -- redige (regra abaixo), como os demais nomes de usuario da V1_4/V1_10.
    accepter_name VARCHAR(200) NOT NULL,
    accepter_role VARCHAR(100) NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Deve ser igual ao content_hash da versao (conferido na aplicacao; CHECK entre
    -- tabelas nao existe em Postgres, e a unicidade abaixo impede aceite duplicado).
    content_hash VARCHAR(64) NOT NULL CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    norm_citation VARCHAR(300),
    -- Classe b acima do padrao (ex.: multa acima de 2%): o alerta de risco foi exibido
    -- e reconhecido por quem aceitou.
    risk_alert_acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE parameter_acceptances ADD CONSTRAINT uq_parameter_acceptances_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE parameter_acceptances ADD CONSTRAINT fk_parameter_acceptance_version
    FOREIGN KEY (tenant_id, version_id) REFERENCES parameter_versions(tenant_id, id);
-- Uma versao tem no maximo um aceite formal (o aceite e do conjunto, nao por usuario).
CREATE UNIQUE INDEX IF NOT EXISTS idx_parameter_acceptances_version
    ON parameter_acceptances(tenant_id, version_id);

INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('parameter_acceptances', 'accepter_name', 'REDACT', 'nome de quem aceitou o parametro (usuario do tenant)')
ON CONFLICT (table_name, column_name) DO NOTHING;

-- =======================================================================
-- PERFIS REGULATORIOS (base; a carga e da P2.31, colunas de origem na V1_13)
-- =======================================================================

CREATE TABLE IF NOT EXISTS regulatory_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    -- Codigo da agencia no catalogo do produto (ex.: 'ARIS'); o nome e o ato vem do
    -- catalogo da P2.31, nao de texto livre do tenant.
    agency_code VARCHAR(40) NOT NULL,
    act_number VARCHAR(100),
    act_date DATE,
    act_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUPERSEDED', 'REVOKED')),
    valid_from DATE NOT NULL,
    valid_to DATE,
    norm_source VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
ALTER TABLE regulatory_profiles ADD CONSTRAINT uq_regulatory_profiles_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE regulatory_profiles ADD CONSTRAINT excl_regulatory_profiles_no_overlap
    EXCLUDE USING gist (tenant_id WITH =, agency_code WITH =,
                        daterange(valid_from, valid_to, '[]') WITH &&);
CREATE INDEX IF NOT EXISTS idx_regulatory_profiles_agency
    ON regulatory_profiles(tenant_id, agency_code, valid_from);

-- =======================================================================
-- FERIADOS E PONTOS FACULTATIVOS (cal.holidays; sai do V6_1)
-- =======================================================================
--
-- Forma completa, igual a do V6_1 mais a coluna kind. Num banco virgem o V6_1 vira
-- no-op no CREATE e acrescenta as constraints dele (uq_holidays_tenant_id, indice
-- idx_holidays_key) e o register_tenant_table. Num banco com V6_1 aplicado, o CREATE
-- abaixo vira no-op e o bloco DO acrescenta kind + CHECK.

CREATE TABLE IF NOT EXISTS holidays (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    holiday_date DATE NOT NULL,
    scope VARCHAR(10) NOT NULL CHECK (scope IN ('NATIONAL', 'STATE', 'MUNICIPAL')),
    state_code VARCHAR(2) CHECK (state_code IS NULL OR state_code ~ '^[A-Z]{2}$'),
    municipality_ibge VARCHAR(7) CHECK (municipality_ibge IS NULL OR municipality_ibge ~ '^[0-9]{7}$'),
    description VARCHAR(150) NOT NULL,
    -- Lei, decreto ou calendario oficial que institui o feriado (ou o ponto facultativo).
    source VARCHAR(300) NOT NULL,
    -- HOLIDAY: feriado. OPTIONAL_POINT: ponto facultativo — registrado, mas nao conta
    -- como dia nao util por padrao (o tenant decide, por calendario, se conta; ver
    -- BusinessCalendar.forMunicipality com includeOptionalPoints). Nenhum feriado e semeado.
    kind VARCHAR(20) NOT NULL DEFAULT 'HOLIDAY'
        CHECK (kind IN ('HOLIDAY', 'OPTIONAL_POINT')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK ((scope = 'NATIONAL' AND state_code IS NULL AND municipality_ibge IS NULL)
        OR (scope = 'STATE' AND state_code IS NOT NULL AND municipality_ibge IS NULL)
        OR (scope = 'MUNICIPAL' AND municipality_ibge IS NOT NULL))
);

-- Banco com V6_1 ja aplicado: acrescenta kind (e a CHECK) sem recriar a tabela.
ALTER TABLE holidays ADD COLUMN IF NOT EXISTS kind VARCHAR(20) NOT NULL DEFAULT 'HOLIDAY';
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_holidays_kind_p019') THEN
        ALTER TABLE holidays ADD CONSTRAINT chk_holidays_kind_p019
            CHECK (kind IN ('HOLIDAY', 'OPTIONAL_POINT'));
    END IF;
END $$;

-- =======================================================================
-- ISOLAMENTO, AUDITORIA E COLUNAS DE ENTIDADE (padrao V1_2)
-- =======================================================================

SELECT register_tenant_table('parameter_versions');
SELECT register_tenant_table('parameter_acceptances');
SELECT register_tenant_table('regulatory_profiles');
SELECT register_tenant_table('holidays');
