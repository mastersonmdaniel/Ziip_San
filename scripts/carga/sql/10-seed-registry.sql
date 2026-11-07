-- P2.9 -- Semente do REGISTRY da massa sintetica (categorias e unidades).
--
-- *** LEIA ANTES DE RODAR ***
-- A DDL da V3_1 (registry: consumer_units, categories, subcategories e a
-- ligacao unidade<->categoria/economias) NAO estava em nenhum espelho local
-- consultado, e o espelho via MCP foi interrompido por HTTP 429. Por isso
-- este arquivo NAO finge conhecer as colunas da V3_1: ele traz (1) a
-- descoberta do schema real e (2) os INSERTs minimos marcados com
-- [CONFERIR-V3_1] nos pontos em que a coluna exata depende da V3_1.
-- Conferir no PC do dono (banco de integracao/homologacao migrado):
--   \d consumer_units   \d categories   \d subcategories
-- e ajustar SOMENTE os trechos marcados. Todo o resto do pacote (tarifas,
-- medicao, ciclo) usa apenas DDL verificada (V4_1, V4_3, V5_1).
--
-- Decisoes que NAO dependem da V3_1 (verificadas noutras migrations):
--   * toda tabela de tenant tem (id UUID, tenant_id UUID) e UNIQUE
--     (tenant_id, id) -- padrao de V4_1/V5_1 e dos pais externos citados no
--     MigrationConsistencyTest (consumer_units, categories, subcategories);
--   * os ids das categorias RES/COM sao os da fixture de teste do produto
--     (FictitiousTariffs: RES = UUID(0,1), COM = UUID(0,2)) -- ids de
--     massa de teste, declarados, para o seeder de tarifas referenciar;
--   * os ids das unidades sao os deterministicos do gerar-massa.py
--     (md5 da semente), gerados aqui pela mesma formula;
--   * a massa CSV equivalente (ligacoes.csv) segue as colunas declaradas em
--     RegistryCsvSchema (P1.3): cep, street_name, address_number
--     obrigatorios da unidade -- se o caminho de importacao do produto for
--     o usado, e esse CSV que entra, nao este SQL.
--
-- Variaveis psql: tenant_id, semente, ligacoes.
\set ON_ERROR_STOP on

-- (1) DESCOBERTA -- rode isolado e confira contra os trechos marcados:
-- SELECT table_name, column_name, data_type, is_nullable
--   FROM information_schema.columns
--  WHERE table_name IN ('consumer_units','categories','subcategories')
--  ORDER BY table_name, ordinal_position;

-- (2) CATEGORIAS [CONFERIR-V3_1: nomes das colunas alem de id/tenant_id;
--     se a V3_1 exigir mais colunas NOT NULL (ex.: codigo/nome), complete-as
--     com os valores declarados 'RES'/'COM' -- sao categorias da MASSA].
INSERT INTO categories (id, tenant_id, code, name)  -- [CONFERIR-V3_1]
VALUES ('00000000-0000-0000-0000-000000000001', :'tenant_id'::uuid, 'RES', 'Residencial (massa sintetica P2.9)'),
       ('00000000-0000-0000-0000-000000000002', :'tenant_id'::uuid, 'COM', 'Comercial (massa sintetica P2.9)')
ON CONFLICT DO NOTHING;

-- (3) UNIDADES [CONFERIR-V3_1: conjunto de colunas NOT NULL da
--     consumer_units e a tabela/coluna que liga a unidade a categoria e ao
--     numero de economias -- o motor de tarifa le grupos por unidade
--     (TariffGroup: categoria + economias). Ate essa ligacao existir, o
--     faturamento real nao enxerga a massa: este e O passo manual da carga.]
--     VINCULO UNIDADE<->ROTA (correcao pos-revisao, achado 6): o despacho do
--     CycleService entrega ao lote a rota do GRUPO (billing_groups.route_code,
--     semeado no 20: ROTA-001..100 nos grupos CARGA-001..100). Para o lote
--     alcancar as unidades certas, a unidade precisa carregar a SUA rota com
--     a mesma formula do CSV (gerar-massa.py): ROTA-(((seq-1) % 100) + 1) --
--     seq 1 = ROTA-001. [CONFERIR-V3_1]: o NOME da coluna/tabela de rota da
--     unidade mora no registry (V3_1 nao verificada); o esqueleto abaixo
--     marca onde ela entra. Sem esse vinculo preenchido, o orquestrador
--     para na contagem de unidades e o disparo nao fatura a massa.
-- Esqueleto deterministico (ids identicos aos do CSV e do 30-seed-medicao):
-- INSERT INTO consumer_units (id, tenant_id, /* [CONFERIR-V3_1] demais colunas */)
-- SELECT (substr(h,1,8)||'-'||substr(h,9,4)||'-5'||substr(h,14,3)||'-'||
--         (ARRAY['8','9','a','b'])[1 + (('x'||substr(h,17,1))::bit(4)::int % 4)]||
--         substr(h,18,3)||'-'||substr(h,21,12))::uuid,
--        :'tenant_id'::uuid /* , ... */
--   FROM (SELECT md5(:'semente' || ':unit:' || g) AS h
--           FROM generate_series(1, :ligacoes) AS g) s
-- ON CONFLICT DO NOTHING;
--
-- Enquanto o passo (3) nao for confirmado contra a V3_1, o orquestrador
-- (executar-carga.sh) PARA aqui por pre-condicao: ele confere
-- count(consumer_units do tenant) >= ligacoes antes de semear a medicao.
