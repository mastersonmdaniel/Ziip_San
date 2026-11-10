-- MM-07 — GIS por municipio: recorte tematico elemento x municipio
-- (decisao do dono n. 45; MAPA-DE-PACOTES do Bloco Multimunicipio, MM-07;
-- decisoes de coordenacao D-MM-01/02/06).
--
-- SQL PENDENTE de numeracao Flyway (D-MM-06): nenhum numero inventado.
-- Na integracao, recebe o proximo slot livre da historia tenant do modulo
-- intelligence/ziipsan-gis e e renomeado para V<numero>__*.sql.
--
-- ======================================================================
-- A LIGACAO elemento-de-rede -> municipio, estabelecida por prova de DDL
-- (ordem de preferencia probatoria do brief: (a) directa, (b) cadeia via
-- entidades ligadas, (c) espacial SO se houver limites em fonte).
-- ======================================================================
--
-- (a) DIRECTA — NAO EXISTE municipio/localidade/UC nas feicoes:
--   * gis_network_element (tabela da V2_3__create_gis_schema.sql; desde a
--     V13_2__gis_features_history_migration.sql e VIEW de compatibilidade
--     sobre gis_node/gis_arc/gis_structure/gis_connection): a UNICA coluna
--     territorial e dma_id (FK fk_gis_elem_dma -> dma_district(tenant_id,
--     id)). Sem municipality_ibge, sem locality_id, sem ligacao a UC.
--     A view de compatibilidade NEM expoe service_connection_id — por
--     isso este recorte le as tabelas de feicao (a fonte), nao a view.
--   * gis_node / gis_arc / gis_structure / gis_poi (V13_2): idem — so
--     dma_id (FKs fk_gis_node_dma / fk_gis_arc_dma / fk_gis_structure_dma
--     / fk_gis_poi_dma -> dma_district).
--   * gis_connection (V13_2): dma_id + service_connection_id (ver (b)).
--   * gis_zone (V13_2): dma_id + dma_district_id (FK fk_gis_zone_dma_
--     district) + commercial_sector_id (ver (b)).
--
-- VIA DMA — NAO E DERIVACAO (fica pendencia P-MM07-01, nao se usa):
--   elemento -> dma_district nao determina municipio: (i) o DDL do
--   dma_district (V2_2, modulo losses) NAO esta nos espelhos locais —
--   nenhuma coluna territorial do distrito e provavel a partir de fonte
--   local [SEM FONTE local]; (ii) mesmo pela via inversa, blocks.dma_id
--   (V3_1, fk_block_dma -> dma_district) e N:1 — varias quadras por DMC —
--   e NENHUM DDL vigente obriga as quadras de um DMC a estarem numa so
--   localidade/municipio. Um DMC pode abranger quadras de 2 municipios;
--   atribuir-lhe um municipio seria inventar a atribuicao. Elemento que
--   so tem DMA aparece com o estado DMA_ONLY_UNRESOLVED (excepcao
--   explicita), nunca com um IBGE adivinhado.
--
-- (c) ESPACIAL — IMPOSSIVEL a partir de fonte (fica pendencia P-MM07-03):
--   ibge_municipalities (registry V3_12__registry_r2.sql) tem SOMENTE
--   id, tenant_id, code CHAR(7), name, state, active e auditoria — SEM
--   coluna de geometria. Nenhuma tabela vigente guarda limites
--   municipais. As geometrias de gis_zone sao zonas operacionais
--   desenhadas do tenant (zone_type: SUPPLY_SYSTEM, PRESSURE_ZONE, DMA,
--   ...), nao limites do IBGE: usa-las como fronteira municipal seria
--   inventar geometria. NENHUMA juncao espacial e feita neste ficheiro.
--
-- (b) CADEIA VIA ENTIDADES LIGADAS — PROVADA para DUAS classes:
--
--   VIA 1 — RAMAL (gis_connection) -> ligacao -> UC ancora -> MM-01:
--     gis_connection.service_connection_id — coluna da V13_2 que ganhou
--       a FK fk_gis_connection_service_connection na V13_10__
--       gis_service_connection_link.sql (GIS-05, P2.23):
--       FOREIGN KEY (tenant_id, service_connection_id)
--         REFERENCES service_connections (tenant_id, id);
--     service_connections.property_unit_id NOT NULL com a FK
--       fk_service_connection_property (V3_14__service_connections.sql,
--       GIS-05, P2.23):
--       FOREIGN KEY (tenant_id, property_unit_id)
--         REFERENCES consumer_units (tenant_id, id);
--       property_unit_id e a unidade ANCORA do imovel (desenho (1) da
--       V3_14: a ligacao aponta para a ancora; service_connection_units
--       liga as unidades servidas do MESMO imovel) — a derivacao usa a
--       ancora como fonte unica, nunca uma das servidas ao acaso.
--     UC ancora -> municipio: consome-se a derivacao canonica do MM-01
--       POR NOME — a view consumer_unit_municipality (tenant_id,
--       consumer_unit_id, municipality_ibge), definida UMA vez no
--       registry (MM-01__consumer_unit_municipality.sql, D-MM-02). Este
--       ficheiro NAO reimplementa a cadeia blocks/sectors/localities.
--     service_connection_id e ANULAVEL na V13_2: ramal sem ligacao =
--       excepcao NO_SERVICE_CONNECTION.
--
--   VIA 2 — ZONA DE SETOR COMERCIAL (gis_zone, so zone_type =
--   'COMMERCIAL_SECTOR') -> setor -> localidade:
--     gis_zone.commercial_sector_id — coluna directa da V13_2 para a
--       entidade territorial commercial_sectors (registry V3_1);
--     commercial_sectors.locality_id com a FK fk_sector_locality
--       (V3_1) -> localities.municipality_ibge — o troco final da cadeia
--       D-MM-02, entrado a meio (a view do MM-01 comeca na UC; nao ha UC
--       nesta via, por isso o troco sector->locality e citado aqui a
--       partir do DDL, nao reimplementado a partir da UC).
--     RESSALVA PROBATORIA (pendencia P-MM07-02): commercial_sector_id
--       NAO tem FK — o comentario da propria V13_2 diz "o vinculo com FK
--       chega na GIS-05, junto de service_connections", mas a migration
--       da GIS-05 (V13_10) entregou a FK do ramal e a do historico e NAO
--       a da zona (conferido no ficheiro). A derivacao confia no VALOR
--       da coluna: valor que nao resolve para um setor do tenant =
--       excepcao BROKEN_CHAIN, nunca municipio inventado. A coluna so
--       tem este significado nas zonas COMMERCIAL_SECTOR (taxonomia
--       zone_type da V13_2); nas restantes zonas e ignorada.
--
-- FORA DA DERIVACAO, COM PENDENCIA NOMEADA (nunca em silencio):
--   gis_node, gis_arc, gis_structure, gis_poi nao tem NENHUMA ligacao
--   territorial provavel alem do DMA (via nao-derivacao, acima). Todas
--   as suas linhas aparecem NESTA view com municipality_ibge NULL e o
--   estado de excepcao correspondente — a classe fica fora do RECORTE
--   (nenhum IBGE lhe e atribuido) mas dentro da CONTAGEM (invariante).
--
-- ======================================================================
-- A VIEW — uma linha por feicao, das SEIS tabelas de feicao da V13_2
-- ======================================================================
-- LEFT JOINs de proposito em todas as vias: feicao sem municipio
-- derivavel CONTINUA VISIVEL, com municipality_ibge NULL e o estado em
-- derivation_status — nunca desaparece do recorte (padrao do MM-04).
-- Invariante por construcao:
--   count(view) == soma das contagens das 6 tabelas de feicao, e
--   count(DERIVED) + count(excepcoes) == count(view), por tenant.
-- derivation_status = 'DERIVED' SE E SO SE municipality_ibge NOT NULL.
--
-- Estados de excepcao (lista fechada, espelhada no servico Java):
--   NO_SERVICE_CONNECTION — ramal com service_connection_id NULL;
--   BROKEN_CHAIN          — id de ligacao presente mas sem alvo no
--                           tenant (ramal pre-FK; setor da zona
--                           inexistente — via 2 sem FK);
--   NO_UNIT_DERIVATION    — UC ancora sem derivacao no MM-01 (UC sem
--                           quadra / cadeia sem linha na view do MM-01);
--   NULL_MUNICIPALITY     — cadeia integra mas localities.
--                           municipality_ibge IS NULL (espelha o
--                           NULL_MUNICIPALITY do MunicipalityDerivation
--                           do MM-01);
--   NO_SECTOR_LINK        — zona COMMERCIAL_SECTOR com
--                           commercial_sector_id NULL;
--   DMA_ONLY_UNRESOLVED   — feicao cuja unica ligacao territorial e o
--                           DMA (pendencia P-MM07-01);
--   NO_TERRITORIAL_LINK   — feicao sem nenhuma ligacao territorial.
--
-- PADRAO DE VIEW do projeto: WITH (security_invoker = true), como a
-- view do MM-01, a de compatibilidade do GIS (V13_2) e as de reporting
-- da P3.22 (V15_1): a leitura respeita a RLS do chamador.
-- Geometrias e catalogos GIS INTOCADOS: esta view nao le nem escreve
-- nenhuma coluna de geometria e nao toca gis_feature_* / gis_catalog_*.
--
-- ADITIVO E IDEMPOTENTE: um CREATE OR REPLACE VIEW; nada mais.
-- DEPENDENCIA DE POUSO: a view referencia consumer_unit_municipality
-- (MM-01) e service_connections (V3_14, ja publicada no desenho do
-- projeto) — o MM-01 tem de pousar ANTES (ou no mesmo lote, registry
-- antes do gis), como no MM-04; sem ela, a criacao falha com erro
-- explicito, nunca com recorte errado em silencio.

CREATE OR REPLACE VIEW gis_element_municipality WITH (security_invoker = true) AS
-- ---- gis_node: sem via provavel (so DMA) ------------------------------
SELECT n.tenant_id AS tenant_id,
       'gis_node'::text AS source_table,
       n.id AS element_id,
       n.code AS element_code,
       NULL::varchar AS municipality_ibge,
       CASE WHEN n.dma_id IS NOT NULL THEN 'DMA_ONLY_UNRESOLVED'
            ELSE 'NO_TERRITORIAL_LINK' END AS derivation_status,
       NULL::text AS derivation_via,
       n.dma_id AS dma_id,
       NULL::uuid AS link_id,
       NULL::uuid AS consumer_unit_id
FROM gis_node n
UNION ALL
-- ---- gis_arc: sem via provavel (so DMA) -------------------------------
SELECT a.tenant_id,
       'gis_arc',
       a.id,
       a.code,
       NULL,
       CASE WHEN a.dma_id IS NOT NULL THEN 'DMA_ONLY_UNRESOLVED'
            ELSE 'NO_TERRITORIAL_LINK' END,
       NULL,
       a.dma_id,
       NULL,
       NULL
FROM gis_arc a
UNION ALL
-- ---- gis_connection: VIA 1 (ramal -> ligacao -> UC ancora -> MM-01) ---
SELECT cn.tenant_id,
       'gis_connection',
       cn.id,
       cn.code,
       cum.municipality_ibge,
       CASE
           WHEN cn.service_connection_id IS NULL THEN 'NO_SERVICE_CONNECTION'
           WHEN sc.id IS NULL THEN 'BROKEN_CHAIN'
           WHEN cum.consumer_unit_id IS NULL THEN 'NO_UNIT_DERIVATION'
           WHEN cum.municipality_ibge IS NULL THEN 'NULL_MUNICIPALITY'
           ELSE 'DERIVED' END,
       CASE WHEN cn.service_connection_id IS NOT NULL THEN 'SERVICE_CONNECTION' END,
       cn.dma_id,
       cn.service_connection_id,
       sc.property_unit_id
FROM gis_connection cn
LEFT JOIN service_connections sc
  ON sc.tenant_id = cn.tenant_id AND sc.id = cn.service_connection_id
LEFT JOIN consumer_unit_municipality cum
  ON cum.tenant_id = sc.tenant_id AND cum.consumer_unit_id = sc.property_unit_id
UNION ALL
-- ---- gis_structure: sem via provavel (so DMA) --------------------------
SELECT s.tenant_id,
       'gis_structure',
       s.id,
       s.code,
       NULL,
       CASE WHEN s.dma_id IS NOT NULL THEN 'DMA_ONLY_UNRESOLVED'
            ELSE 'NO_TERRITORIAL_LINK' END,
       NULL,
       s.dma_id,
       NULL,
       NULL
FROM gis_structure s
UNION ALL
-- ---- gis_zone: VIA 2 so em COMMERCIAL_SECTOR; restantes = DMA/sem ----
SELECT z.tenant_id,
       'gis_zone',
       z.id,
       z.code,
       loc.municipality_ibge,
       CASE
           WHEN z.zone_type = 'COMMERCIAL_SECTOR'
                AND z.commercial_sector_id IS NULL THEN 'NO_SECTOR_LINK'
           WHEN z.zone_type = 'COMMERCIAL_SECTOR'
                AND cs.id IS NULL THEN 'BROKEN_CHAIN'
           WHEN z.zone_type = 'COMMERCIAL_SECTOR'
                AND loc.municipality_ibge IS NULL THEN 'NULL_MUNICIPALITY'
           WHEN z.zone_type = 'COMMERCIAL_SECTOR' THEN 'DERIVED'
           WHEN z.dma_id IS NOT NULL OR z.dma_district_id IS NOT NULL
                THEN 'DMA_ONLY_UNRESOLVED'
           ELSE 'NO_TERRITORIAL_LINK' END,
       CASE WHEN z.zone_type = 'COMMERCIAL_SECTOR'
                 AND z.commercial_sector_id IS NOT NULL
            THEN 'COMMERCIAL_SECTOR' END,
       z.dma_id,
       CASE WHEN z.zone_type = 'COMMERCIAL_SECTOR' THEN z.commercial_sector_id END,
       NULL
FROM gis_zone z
LEFT JOIN commercial_sectors cs
  ON z.zone_type = 'COMMERCIAL_SECTOR'
 AND cs.tenant_id = z.tenant_id AND cs.id = z.commercial_sector_id
LEFT JOIN localities loc
  ON loc.tenant_id = cs.tenant_id AND loc.id = cs.locality_id
UNION ALL
-- ---- gis_poi: sem via provavel (so DMA) --------------------------------
SELECT p.tenant_id,
       'gis_poi',
       p.id,
       p.code,
       NULL,
       CASE WHEN p.dma_id IS NOT NULL THEN 'DMA_ONLY_UNRESOLVED'
            ELSE 'NO_TERRITORIAL_LINK' END,
       NULL,
       p.dma_id,
       NULL,
       NULL
FROM gis_poi p;

COMMENT ON VIEW gis_element_municipality IS
    'MM-07 (decisao 45, D-MM-01/02): recorte tematico GIS por municipio — uma linha por feicao das 6 tabelas da V13_2 com o municipality_ibge derivado. Vias provadas: gis_connection via service_connections.property_unit_id -> consumer_unit_municipality (MM-01); gis_zone COMMERCIAL_SECTOR via commercial_sectors -> localities (sem FK na coluna — V13_2; nao-resolucao = BROKEN_CHAIN). Sem via espacial: ibge_municipalities (V3_12) nao tem geometria. Feicao sem derivacao aparece com IBGE NULL e derivation_status de excepcao — nunca some; soma(derivados)+excepcoes == total de feicoes do tenant.';
