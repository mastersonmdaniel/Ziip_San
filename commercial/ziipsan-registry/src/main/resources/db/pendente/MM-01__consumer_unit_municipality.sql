-- MM-01 — Derivacao canonica UC -> municipio (D-MM-02 do MAPA-DE-PACOTES
-- do Bloco Multimunicipio; decisao do dono n. 45).
--
-- SQL PENDENTE de numeracao Flyway (D-MM-06): nenhum numero inventado.
-- Na integracao, recebe o proximo slot livre da historia tenant do modulo
-- commercial/ziipsan-registry e e renomeado para V<numero>__*.sql.
--
-- A CADEIA (definida UMA vez aqui; os demais pacotes MM consomem-na por
-- nome, nunca a reimplementam):
--   consumer_units.block_id
--     -> blocks.sector_id
--       -> commercial_sectors.locality_id
--         -> localities.municipality_ibge
-- Todas as ligacoes sao FK compostas (tenant_id, id) integras desde a
-- V3_1__create_registry_schema.sql. Sao INNER JOINs de proposito: uma UC
-- sem quadra (block_id NULL, permitido pelo DDL) NAO tem municipio
-- derivavel por esta via e nao aparece na view — quem consome distingue
-- "sem derivacao" de "municipio nulo" (localidade com municipality_ibge
-- NULL aparece, com municipality_ibge NULL).
--
-- O QUE NAO E esta derivacao (adendo pos-revisao da ANALISE-MULTIMUNICIPIO,
-- correccao 1 / D-MM-02):
--   * addresses.municipality_ibge — atributo do endereco (conferencia,
--     nao derivacao operacional);
--   * consumer_units.iptu_municipality_ibge (V3_12) — municipio do IPTU,
--     excluido desta derivacao.
--
-- PRECEDENTE DE VIEW no projeto: ha precedente directo — o modulo de
-- reporting (P3.22, V15_1__reporting_core_and_views.sql) define as suas
-- consultas canonicas como CREATE OR REPLACE VIEW ... WITH
-- (security_invoker = true), e o GIS (P2.21/P2.22) idem. Esta view segue
-- o mesmo padrao. A alternativa (consulta repetida em cada consumidor)
-- foi rejeitada: a decisao D-MM-02 manda definir a derivacao uma vez.
--
-- ADITIVO E IDEMPOTENTE: CREATE OR REPLACE; nenhuma tabela e tocada,
-- nenhuma migration publicada e alterada.

CREATE OR REPLACE VIEW consumer_unit_municipality WITH (security_invoker = true) AS
SELECT cu.tenant_id        AS tenant_id,
       cu.id               AS consumer_unit_id,
       loc.municipality_ibge AS municipality_ibge
FROM consumer_units cu
JOIN blocks b
  ON b.tenant_id = cu.tenant_id AND b.id = cu.block_id
JOIN commercial_sectors cs
  ON cs.tenant_id = cu.tenant_id AND cs.id = b.sector_id
JOIN localities loc
  ON loc.tenant_id = cu.tenant_id AND loc.id = cs.locality_id;

COMMENT ON VIEW consumer_unit_municipality IS
    'MM-01 (D-MM-02): derivacao canonica UC -> municipio IBGE pela cadeia FK integra consumer_units -> blocks -> commercial_sectors -> localities. UC sem quadra nao aparece (sem derivacao).';
