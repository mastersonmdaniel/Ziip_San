-- MM-01 — Backfill descritivo dos perfis de municipio (ficheiro separado
-- do DDL, como manda o ambito do pacote; corre DEPOIS de
-- MM-01__tenant_municipality_profiles.sql).
--
-- SQL PENDENTE de numeracao Flyway (D-MM-06). Descritivo apenas (D-MM-05):
-- criar o perfil nao muda comportamento de nenhum outro modulo.
--
-- FONTES (as duas vias integras do estudo ANALISE-MULTIMUNICIPIO §5.1):
--   (a) localities distintas por tenant — a cadeia territorial do
--       registry (V3_1): todo o IBGE que o tenant ja cadastrou;
--   (b) platform.tenant_profile.municipality_ibge, quando presente — o
--       municipio do perfil-empresa mono-municipio vigente (V9).
-- Nenhum perfil e criado para IBGE que nao exista numa destas duas vias
-- nos dados do proprio tenant. IBGE NULL ou fora do formato de 7 digitos
-- nao gera perfil (a coluna de destino tem o mesmo CHECK da V9).
--
-- DETERMINISTICO: o conjunto (tenant_id, municipality_ibge) resultante
-- depende so dos dados; o id e derivado por md5 do par (mesmo truque de
-- UUID deterministico do gerador da base ficticia), e a origem, quando
-- as duas vias contem o IBGE, e MIN(derivation_origin) =
-- 'LOCALITY_CHAIN' — a cadeia e a via operacional (D-MM-02).
--
-- IDEMPOTENTE: ON CONFLICT (tenant_id, municipality_ibge) DO NOTHING —
-- a segunda execucao insere 0 linhas (provado em PG descartavel, ver o
-- RELATORIO-MM-01).

INSERT INTO platform.tenant_municipality_profiles
    (id, tenant_id, municipality_ibge, derivation_origin)
SELECT md5(src.tenant_id::text || ':' || src.municipality_ibge)::uuid AS id,
       src.tenant_id,
       src.municipality_ibge,
       MIN(src.derivation_origin) AS derivation_origin
FROM (
    SELECT l.tenant_id,
           l.municipality_ibge::char(7) AS municipality_ibge,
           'LOCALITY_CHAIN' AS derivation_origin
    FROM localities l
    WHERE l.municipality_ibge IS NOT NULL
      AND l.municipality_ibge ~ '^[0-9]{7}$'
    UNION ALL
    SELECT tp.tenant_id,
           tp.municipality_ibge,
           'TENANT_PROFILE' AS derivation_origin
    FROM platform.tenant_profile tp
    WHERE tp.municipality_ibge IS NOT NULL
) src
GROUP BY src.tenant_id, src.municipality_ibge
ON CONFLICT (tenant_id, municipality_ibge) DO NOTHING;
