-- CORRECAO R8-C (idempotencia; achado A-04 da Rodada 8, 09/11/2026):
-- as constraints e os indices deste ficheiro passam a usar o padrao
-- do bloco MM-02/MM-06 (DROP CONSTRAINT/INDEX IF EXISTS + ADD/CREATE),
-- pelo que reexecutar o ficheiro fora do Flyway ja nao falha na
-- segunda corrida. Antes: a 2a corrida falhava na linha 81
-- (uq_charge_definitions_tenant_id already exists) — provado em PG
-- descartavel no ciclo (prova-reaplicacao-ANTES.log). O conteudo
-- continua o mesmo: mesmas tabelas, mesmos CHECKs, mesmas unicidades.
-- ============================================================================
-- CAMADA 1 (PENDENTE DE NUMERACAO): registro unico e versionado de
-- cobrancas — proposta de arquitetura tarifaria, secoes 2.1 e 2.2;
-- decisao do dono, item 43 do registro de decisoes.
--
-- POR QUE PENDENTE: a faixa Flyway do billing segue a sequencia global
-- do projeto e nenhum numero e inventado nesta camada: este SQL entra
-- como db/pendente/ e recebe o numero da sequencia global na
-- integracao (precedentes: ED-25, ED-34).
--
-- NENHUM DADO DE NEGOCIO E SEMEADO: o catalogo nasce vazio (precedente
-- DAD-20 da P2.5, catalogo debit_types). Codigos, nomes, valores e
-- vigencias sao do tenant/agencia e entram pelo fluxo de cadastro.
--
-- CORRECAO DA CAMADA 1 (reconciliacao com o DESENHO-CAMADAS.md da
-- Camada 0, secao 1): charge_definitions ganha field_calculable,
-- accounting_key_ref e municipality_ibge; charge_versions ganha os canais
-- nomeados (tariff_version_id, price_item_code, external_contract_id)
-- e a coluna parameters JSONB (desenho, secao 1.3); CHECKs recusam
-- codigos/nomes em branco (C1-02, espelho da recusa do Java); trigger
-- garante versao com o mesmo modelo da definicao (C1-03).
-- A coluna tariff_version_ids (UUID[]) MANTEM-SE como esta: e a
-- pendencia de integracao P-C1-01 (array x tabela de ligacao), nao
-- resolvida nesta camada. A coluna external_source_code mantem-se
-- como codigo descritivo legado da fonte externa, com a guarda
-- anti-branco; o canal vinculante do modelo EXTERNAL_VALUE e o
-- external_contract_id.
--
-- CORRECAO DO MUNICIPIO (reconciliacao pos-item 45; adendo de desenho
-- da Camada 0, D-01/D-02 e premissas PM-01..PM-05): a coluna generica
-- de escopo da mecanica pre-item 45 e SUBSTITUIDA por
-- municipality_ibge TIPADO (NULL = escopo "empresa"; presente =
-- IBGE de exactamente 7 digitos, com CHECK de formato), e a
-- unicidade do codigo passa a ser por (tenant, escopo, codigo) via
-- indice unico por expressao sobre COALESCE(municipality_ibge, '') —
-- a forma que da ao NULL uma chave comparavel sem gastar nenhum
-- IBGE como sentinela ('' nunca e um IBGE valido).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. DEFINICAO DA COBRANCA (o QUE a cobranca e)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS charge_definitions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    charge_code VARCHAR(100) NOT NULL,
    name VARCHAR(300) NOT NULL,
    owner VARCHAR(20) NOT NULL CHECK (owner IN ('OWN', 'THIRD_PARTY')),
    revenue_nature VARCHAR(30) NOT NULL CHECK (revenue_nature IN ('TARIFF', 'COMPLEMENTARY', 'ANCILLARY', 'ASSOCIATED_PROJECT')),
    calculation_model VARCHAR(40) NOT NULL CHECK (calculation_model IN ('PROGRESSIVE_BY_CONSUMPTION', 'DERIVED_FROM_VOLUME', 'FIXED_PER_UNIT', 'CATALOG_PRICE', 'PERCENT_OF_BASE', 'EXTERNAL_VALUE')),
    third_party_provider_id UUID,
    homologation_required BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    field_calculable BOOLEAN NOT NULL DEFAULT FALSE,
    accounting_key_ref TEXT NULL,
    -- municipality_ibge: escopo municipal TIPADO (item 45 / D-01).
    -- NULL = escopo "empresa" (vale para todo o tenant — comportamento
    -- vigente preservado); presente = codigo IBGE de exactamente 7
    -- digitos do municipio (cadeia territorial: localities.
    -- municipality_ibge; mestre ibge_municipalities — o billing nunca
    -- o digita, recebe-o derivado). CHECK de formato na definicao da
    -- tabela; unicidade por escopo no indice por expressao abaixo.
    municipality_ibge TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    -- Dono x prestador: terceiro exige prestador; propria o proibe
    -- (mesma regra do dominio ChargeDefinition).
    CHECK ((owner = 'THIRD_PARTY' AND third_party_provider_id IS NOT NULL)
        OR (owner = 'OWN' AND third_party_provider_id IS NULL)),
    -- C1-02: codigos/nomes em branco recusados tambem no banco
    -- (espelho da recusa do dominio ChargeDefinition).
    CHECK (charge_code = btrim(charge_code) AND length(btrim(charge_code)) > 0),
    CHECK (name = btrim(name) AND length(btrim(name)) > 0),
    -- Escopo municipal tipado: NULL (empresa) ou IBGE de 7 digitos
    -- (espelho da validacao do dominio ChargeDefinition).
    CHECK (municipality_ibge IS NULL OR municipality_ibge ~ '^[0-9]{7}$')
);
-- UNIQUE (tenant_id, id): ancora das FKs compostas futuras (licao R3-G1).
-- R8-C: guarda de idempotencia (padrao MM-02/MM-06). A FK
-- fk_charge_versions_definition depende desta UNIQUE: larga-se
-- PRIMEIRO a FK (reposta adiante, na seccao 2), e so depois a UNIQUE —
-- sem CASCADE, para nenhum dependente cair em silencio. A guarda
-- IF EXISTS sobre a tabela e necessaria porque, na 1a corrida,
-- charge_versions ainda nao existe (nasce na seccao 2, adiante).
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_name = 'charge_versions') THEN
        ALTER TABLE charge_versions DROP CONSTRAINT IF EXISTS fk_charge_versions_definition;
    END IF;
END $$;
ALTER TABLE charge_definitions DROP CONSTRAINT IF EXISTS uq_charge_definitions_tenant_id;
ALTER TABLE charge_definitions ADD CONSTRAINT uq_charge_definitions_tenant_id UNIQUE (tenant_id, id);
-- Codigo da cobranca unico por (tenant, escopo) — item 45 / D-01.2:
-- indice unico POR EXPRESSAO sobre COALESCE(municipality_ibge, '').
-- A UNIQUE comum (tenant_id, municipality_ibge, charge_code) NAO
-- serviria: em SQL, NULL <> NULL e duas linhas de escopo "empresa"
-- com o mesmo codigo passariam. O COALESCE da ao NULL uma chave
-- comparavel sem inventar IBGE sentinela ('' nunca e IBGE valido,
-- que tem exactamente 7 digitos) e mantem a coluna nullable e honesta.
-- R8-C: guarda de idempotencia (padrao MM-02/MM-06).
DROP INDEX IF EXISTS uq_charge_definitions_tenant_scope_code;
CREATE UNIQUE INDEX uq_charge_definitions_tenant_scope_code
    ON charge_definitions (tenant_id, COALESCE(municipality_ibge, ''), charge_code);
DROP INDEX IF EXISTS idx_charge_definitions_model;
CREATE INDEX idx_charge_definitions_model ON charge_definitions(tenant_id, calculation_model, active);

-- ----------------------------------------------------------------------------
-- 2. VERSAO DA COBRANCA (valores/parametros + vigencia da versao)
-- ----------------------------------------------------------------------------
-- A coluna do numero da versao e version_number (ex-version, renomeada
-- pela cura P-CHARGEVERSION-01): "version" colide com o lock optimista
-- herdado de BaseEntity e impede o Hibernate de construir a SessionFactory.
-- A promocao desta camada para migration versionada TEM de manter
-- version_number; nenhuma base publicada tem a coluna antiga (a tabela
-- nunca foi criada por migration publicada).
CREATE TABLE IF NOT EXISTS charge_versions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    charge_definition_id UUID NOT NULL,
    version_number INT NOT NULL CHECK (version_number >= 1),
    valid_from DATE NOT NULL,
    valid_to DATE,
    calculation_model VARCHAR(40) NOT NULL CHECK (calculation_model IN ('PROGRESSIVE_BY_CONSUMPTION', 'DERIVED_FROM_VOLUME', 'FIXED_PER_UNIT', 'CATALOG_PRICE', 'PERCENT_OF_BASE', 'EXTERNAL_VALUE')),
    -- Canais nomeados da versao (INV-C1-03 na forma do desenho):
    -- exactamente um canal por modelo, com exclusividade nos CHECKs.
    tariff_version_id UUID,
    price_item_code VARCHAR(100),
    external_contract_id UUID,
    -- Coluna legada da producao (P-C1-01 subsiste como pendencia de
    -- integracao): mantida como esta, fora dos CHECKs de coerencia —
    -- o canal vinculante das versoes novas e o tariff_version_id.
    tariff_version_ids UUID[],
    percent NUMERIC(10,4) CHECK (percent IS NULL OR percent >= 0),
    fixed_amount NUMERIC(15,2) CHECK (fixed_amount IS NULL OR fixed_amount >= 0),
    unit_price NUMERIC(15,2) CHECK (unit_price IS NULL OR unit_price >= 0),
    -- Codigo descritivo legado da fonte externa (o canal vinculante
    -- e o external_contract_id); guarda anti-branco em CHECK abaixo.
    external_source_code VARCHAR(100),
    -- Serializacao jsonb dos parametros de valor (desenho, secao 1.3),
    -- espelho do ChargeParameters do dominio para a entidade JPA.
    parameters JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    -- C1-02: codigos anulaveis em branco recusados tambem no banco
    -- (espelho da recusa do dominio ChargeVersion/ChargeParameters).
    CHECK (price_item_code IS NULL OR (price_item_code = btrim(price_item_code) AND length(btrim(price_item_code)) > 0)),
    CHECK (external_source_code IS NULL OR (external_source_code = btrim(external_source_code) AND length(btrim(external_source_code)) > 0)),
    -- Coerencia modelo x canal/parametro, com exclusividade de canal
    -- (espelho do dominio ChargeVersion, INV-C1-03 na forma do desenho).
    CHECK (calculation_model <> 'PROGRESSIVE_BY_CONSUMPTION'
        OR (tariff_version_id IS NOT NULL AND price_item_code IS NULL AND external_contract_id IS NULL)),
    CHECK (calculation_model <> 'DERIVED_FROM_VOLUME'
        OR ((tariff_version_id IS NOT NULL OR percent IS NOT NULL)
            AND price_item_code IS NULL AND external_contract_id IS NULL)),
    CHECK (calculation_model <> 'FIXED_PER_UNIT'
        OR (fixed_amount IS NOT NULL AND tariff_version_id IS NULL
            AND price_item_code IS NULL AND external_contract_id IS NULL)),
    CHECK (calculation_model <> 'CATALOG_PRICE'
        OR ((price_item_code IS NOT NULL OR unit_price IS NOT NULL)
            AND tariff_version_id IS NULL AND external_contract_id IS NULL)),
    CHECK (calculation_model <> 'PERCENT_OF_BASE'
        OR (percent IS NOT NULL AND tariff_version_id IS NULL
            AND price_item_code IS NULL AND external_contract_id IS NULL)),
    CHECK (calculation_model <> 'EXTERNAL_VALUE'
        OR (external_contract_id IS NOT NULL AND tariff_version_id IS NULL AND price_item_code IS NULL))
);
-- R8-C: guardas de idempotencia (padrao MM-02/MM-06).
ALTER TABLE charge_versions DROP CONSTRAINT IF EXISTS uq_charge_versions_tenant_id;
ALTER TABLE charge_versions ADD CONSTRAINT uq_charge_versions_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE charge_versions DROP CONSTRAINT IF EXISTS uq_charge_versions_tenant_definition_version;
ALTER TABLE charge_versions ADD CONSTRAINT uq_charge_versions_tenant_definition_version UNIQUE (tenant_id, charge_definition_id, version_number);
ALTER TABLE charge_versions DROP CONSTRAINT IF EXISTS fk_charge_versions_definition;
ALTER TABLE charge_versions ADD CONSTRAINT fk_charge_versions_definition
    FOREIGN KEY (tenant_id, charge_definition_id) REFERENCES charge_definitions(tenant_id, id);
DROP INDEX IF EXISTS idx_charge_versions_vigencia;
CREATE INDEX idx_charge_versions_vigencia ON charge_versions(tenant_id, charge_definition_id, valid_from);

-- C1-03: o modelo da versao tem de ser igual ao modelo da definicao
-- tambem no banco (no Java a recusa e do ChargeCatalog). Forma com
-- precedente no projeto: funcao + trigger BEFORE INSERT OR UPDATE
-- (precedente das funcoes de preenchimento da V4_14 do billing).
CREATE OR REPLACE FUNCTION charge_versions_guard_model()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_definition_model VARCHAR(40);
BEGIN
    SELECT calculation_model INTO v_definition_model
      FROM charge_definitions
     WHERE tenant_id = NEW.tenant_id AND id = NEW.charge_definition_id;
    IF v_definition_model IS NOT NULL AND v_definition_model <> NEW.calculation_model THEN
        RAISE EXCEPTION 'modelo da versao diverge do modelo da definicao: % x %',
            NEW.calculation_model, v_definition_model;
    END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_charge_versions_model_match ON charge_versions;
CREATE TRIGGER trg_charge_versions_model_match
    BEFORE INSERT OR UPDATE ON charge_versions
    FOR EACH ROW EXECUTE FUNCTION charge_versions_guard_model();

-- Nao-sobreposicao de vigencias garantida tambem no banco (precedente
-- ED-34, exclusion constraint sobre o periodo por tenant + cobranca);
-- valid_to nulo = vigencia aberta. Requer btree_gist (extensao padrao
-- do PostgreSQL, conferir na integracao se ja habilitada).
CREATE EXTENSION IF NOT EXISTS btree_gist;
-- R8-C: guarda de idempotencia (padrao MM-02/MM-06).
ALTER TABLE charge_versions DROP CONSTRAINT IF EXISTS ex_charge_versions_no_overlap;
ALTER TABLE charge_versions ADD CONSTRAINT ex_charge_versions_no_overlap
    EXCLUDE USING gist (tenant_id WITH =, charge_definition_id WITH =,
        daterange(valid_from, COALESCE(valid_to, DATE 'infinity'), '[]') WITH &&);

-- ----------------------------------------------------------------------------
-- 3. ISOLAMENTO (padrao register_tenant_table da core, como ED-25/ED-34)
-- ----------------------------------------------------------------------------
SELECT register_tenant_table('charge_definitions');
SELECT register_tenant_table('charge_versions');
