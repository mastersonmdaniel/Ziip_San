-- MM-01 — Perfil de municipio como entidade (Bloco Multimunicipio,
-- decisao do dono n. 45; MAPA-DE-PACOTES, pacote MM-01).
--
-- SQL PENDENTE de numeracao Flyway (D-MM-06): nenhum numero inventado.
-- Na integracao, recebe o proximo slot livre da historia core do modulo
-- shared/ziipsan-core e e renomeado para V<numero>__*.sql.
--
-- ONDE VIVE: ao lado de tenant_profile, no schema `platform` da migration
-- core V9__platform_console.sql (a fonte unica de identidade do tenant,
-- DAD-32). O perfil de municipio e o desdobramento por municipio desse
-- perfil-empresa: 1 linha por (tenant, municipio IBGE).
--
-- DESENHO MINIMO (nenhuma coluna de configuracao inventada):
--   * parametros por municipio sao do pacote MM-02 (parameter_versions);
--   * tarifa por municipio e do bloco tarifario (item 43/decisao 45);
--   * esta tabela guarda SO a existencia do perfil e a origem da
--     derivacao que o criou.
--
-- HERANCA POR REFERENCIA (documentada, nao duplicada): o perfil de
-- municipio NAO copia dados de pecas que ja sao por municipio —
--   * regulatory_profiles / regulatory_profile_activations (platform
--     V1_13, P2.31): perfil regulatorio por municipality_ibge;
--   * reg_contracts (ED-96): contratos regulatorios por municipio;
--   * holidays com escopo MUNICIPAL (collection V6_1): feriados.
-- A ligacao a essas pecas e o proprio municipality_ibge (D-MM-01: a
-- chave da dimensao e o codigo IBGE, o padrao ja vigente) — por isso
-- nenhuma FK nem coluna adicional as duplica aqui.
--
-- register_tenant_table: NAO se chama — determinado pelo DDL da V9: as
-- tabelas vizinhas do schema `platform` (tenant_profile,
-- tenant_subscription, tenant_lifecycle, ...) NAO chamam
-- register_tenant_table (a funcao regista tabelas do schema do TENANT
-- para isolamento/RLS por tenant; o console da plataforma vive fora
-- dele e a fronteira de isolamento continua a ser o tenant — a decisao
-- 45 exclui expressamente municipio como fronteira de RLS). Chamar aqui
-- seria divergir das vizinhas sem fonte.
--
-- ADITIVO: tabela nova; nenhuma migration publicada e tocada.

CREATE SCHEMA IF NOT EXISTS platform;

CREATE TABLE IF NOT EXISTS platform.tenant_municipality_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    -- Codigo IBGE do municipio com 7 digitos (mesmo dominio do
    -- tenant_profile.municipality_ibge da V9 e das pecas herdadas).
    municipality_ibge CHAR(7) NOT NULL CHECK (municipality_ibge ~ '^[0-9]{7}$'),
    -- Origem da derivacao que criou o perfil (ver o backfill MM-01):
    -- LOCALITY_CHAIN = IBGE presente nas localities do tenant (cadeia
    -- territorial do registry); TENANT_PROFILE = IBGE do perfil-empresa
    -- (tenant_profile.municipality_ibge).
    derivation_origin VARCHAR(20) NOT NULL
        CHECK (derivation_origin IN ('LOCALITY_CHAIN', 'TENANT_PROFILE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Um perfil por municipio dentro do tenant.
    CONSTRAINT uq_tenant_municipality_profiles_ibge UNIQUE (tenant_id, municipality_ibge),
    -- Padrao pai de FK composta (tenant_id, id), como nas vizinhas de
    -- tenant da V3_1 e nas tabelas de tenant da V9/V1_13: quem referenciar
    -- um perfil de municipio referencia o par, nunca o id solto.
    CONSTRAINT uq_tenant_municipality_profiles_tenant_id UNIQUE (tenant_id, id)
);

COMMENT ON TABLE platform.tenant_municipality_profiles IS
    'MM-01: perfil de municipio do tenant (entidade). Um por (tenant, IBGE). Sem configuracao propria: parametros sao do MM-02, tarifa do bloco tarifario; herda por referencia (municipality_ibge) regulatory_profiles (P2.31), reg_contracts (ED-96) e feriados municipais.';
COMMENT ON COLUMN platform.tenant_municipality_profiles.derivation_origin IS
    'Origem da derivacao do perfil no backfill MM-01: LOCALITY_CHAIN (localities do tenant) ou TENANT_PROFILE (tenant_profile.municipality_ibge). Quando as duas vias contem o IBGE, regista-se LOCALITY_CHAIN (MIN lexicografico no backfill).';
