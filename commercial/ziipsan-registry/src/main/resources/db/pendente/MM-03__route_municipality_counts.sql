-- MM-03 — Contagens rota x municipio (P-BF-07; decisao do dono n. 45;
-- D-MM-02/D-MM-06 do MAPA-DE-PACOTES do Bloco Multimunicipio).
--
-- SQL PENDENTE de numeracao Flyway (D-MM-06): nenhum numero inventado.
-- Na integracao, recebe o proximo slot livre da historia tenant do modulo
-- commercial/ziipsan-registry e e renomeado para V<numero>__*.sql.
--
-- O QUE E: a forma SQL da derivacao rota -> municipio do MM-03 — quantas
-- UCs de cada rota derivam para cada municipio. Consome POR NOME a
-- derivacao canonica do MM-01 (view consumer_unit_municipality); a cadeia
-- territorial NAO e repetida aqui (D-MM-02: a derivacao define-se uma vez).
--
-- COMPOSICAO DA ROTA: consumer_units.reading_route_id (FK composta por
-- tenant desde a V3_1) — e a composicao real da rota. A coluna
-- reading_routes.locality_id (V3_6) e anulavel e NAO e usada: atributo de
-- cadastro nao substitui as UCs que a rota de facto serve.
--
-- LEITURA DAS LINHAS (espelho exacto da semantica Java de
-- RouteMunicipalityScope / RouteMunicipalityResolver):
--   * uma linha (rota, IBGE, n) por municipio DERIVADO presente na rota;
--   * localidade com municipality_ibge NULL produz linha com IBGE NULL:
--     essas UCs sao INDERIVAVEIS (NULL_MUNICIPALITY do MM-01), nao um
--     "municipio nulo";
--   * UC sem linha na view do MM-01 (sem quadra / cadeia quebrada) nao
--     aparece aqui: inderivavel = total de UCs da rota (consumer_units
--     por reading_route_id) - soma das linhas derivadas (IBGE nao nulo);
--   * rota sem nenhuma UC nao aparece: e o estado EMPTY da classificacao.
-- A classificacao PURE/MIXED/EMPTY vive no validador (RoutePurityValidator)
-- e e reproduzida nas provas sobre esta view — esta view so conta.
--
-- ADITIVO E IDEMPOTENTE: CREATE OR REPLACE; nenhuma tabela e tocada,
-- nenhuma migration publicada e alterada, nenhum NOT NULL e imposto
-- (a invariante nasce como validacao com relatorio — estudo §6).

CREATE OR REPLACE VIEW route_municipality_counts WITH (security_invoker = true) AS
SELECT rr.tenant_id            AS tenant_id,
       rr.id                   AS route_id,
       cum.municipality_ibge   AS municipality_ibge,
       COUNT(*)                AS unit_count
FROM reading_routes rr
JOIN consumer_units cu
  ON cu.tenant_id = rr.tenant_id AND cu.reading_route_id = rr.id
JOIN consumer_unit_municipality cum
  ON cum.tenant_id = cu.tenant_id AND cum.consumer_unit_id = cu.id
GROUP BY rr.tenant_id, rr.id, cum.municipality_ibge;

COMMENT ON VIEW route_municipality_counts IS
    'MM-03 (P-BF-07): UCs derivadas por (rota, municipio IBGE) sobre a derivacao canonica do MM-01 (consumer_unit_municipality). IBGE NULL = UC inderivavel (localidade sem IBGE); rota ausente = sem UCs derivaveis. Classificacao PURE/MIXED/EMPTY: RoutePurityValidator.';
