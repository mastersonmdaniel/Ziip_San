-- CAMADA-2__tariff_municipality_scope.sql
-- Arquitetura Tarifaria Unificada, Camada 2 (billing maestro):
-- escopo municipal nas 5 tabelas de tarifa (adendo de desenho do
-- municipio, D-03.1; decisao do dono, item 45).
--
-- SQL PENDENTE, SEM numero Flyway: a numeracao global e da integracao
-- (PD-TAR-08), como nos precedentes ED-25/ED-34 e no SQL da Camada 1.
-- A V4_1 publicada (V4_1__create_billing_schema.sql) NAO e tocada:
-- este ficheiro e ADITIVO NOVO sobre ela.
--
-- Conteudo: ALTER TABLE ADITIVO — uma coluna municipality_ibge
-- TEXT NULL por tabela, com CHECK de formato de 7 digitos:
--   tariffs               (V4_1:19)
--   tariff_versions       (V4_1:45)
--   tariff_category_rules (V4_1:71)
--   tariff_blocks         (V4_1:102)
--   tariff_social_bands   (V4_1:124)
-- (lista FECHADA das 5 tabelas do nivel MINIMO da analise
-- multimunicipio; nenhuma outra tabela e alterada aqui.)
--
-- Semantica (PM-01): municipality_ibge IS NULL = escopo "empresa"
-- (tenant inteiro) — e o comportamento vigente preservado. Valor
-- presente = escopo municipal: a linha vale so para as UCs cujo
-- municipio derivado (cadeia territorial, PM-05) e esse IBGE.
--
-- SEM BACKFILL: as linhas vigentes ficam NULL (= empresa), que e o
-- backfill honesto da analise (secao 5 dela: o vigente preserva-se
-- como escopo empresa). A estrategia de migracao/backfill de dados
-- (replicacao de versoes por municipio, quando decidida) e do pacote
-- residual da analise, bloco multimunicipio/integracao — NAO deste
-- SQL (fronteira registada no relatorio da Camada 2, P-C2-02).
--
-- SEM tocar RLS/particoes: o isolamento continua a ser o tenant
-- (register_tenant_table); nenhuma politica por municipio e criada
-- (fronteira D-08.1 — RLS por municipio e outro estudo, nao decidido).
-- SEM renomear nada: nenhuma tabela, coluna ou constraint vigente
-- muda de nome; os CHECKs novos tem nomes novos proprios.
--
-- IDEMPOTENCIA (correccao R2-07): as colunas ja nascem com
-- IF NOT EXISTS; as constraints, para as quais o PostgreSQL nao
-- oferece IF NOT EXISTS, vao embrulhadas em DO $$ ... EXCEPTION
-- WHEN duplicate_object — a forma com precedente no projecto
-- (ED-90/ED-72, SQLs pendentes) — pelo que reexecutar este ficheiro
-- fora do Flyway ja nao falha na segunda corrida.

ALTER TABLE tariffs
    ADD COLUMN IF NOT EXISTS municipality_ibge TEXT NULL;
DO $$ BEGIN
    ALTER TABLE tariffs
        ADD CONSTRAINT chk_tariffs_municipality_ibge_format
        CHECK (municipality_ibge IS NULL OR municipality_ibge ~ '^[0-9]{7}$');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

ALTER TABLE tariff_versions
    ADD COLUMN IF NOT EXISTS municipality_ibge TEXT NULL;
DO $$ BEGIN
    ALTER TABLE tariff_versions
        ADD CONSTRAINT chk_tariff_versions_municipality_ibge_format
        CHECK (municipality_ibge IS NULL OR municipality_ibge ~ '^[0-9]{7}$');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

ALTER TABLE tariff_category_rules
    ADD COLUMN IF NOT EXISTS municipality_ibge TEXT NULL;
DO $$ BEGIN
    ALTER TABLE tariff_category_rules
        ADD CONSTRAINT chk_tariff_category_rules_municipality_ibge_format
        CHECK (municipality_ibge IS NULL OR municipality_ibge ~ '^[0-9]{7}$');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

ALTER TABLE tariff_blocks
    ADD COLUMN IF NOT EXISTS municipality_ibge TEXT NULL;
DO $$ BEGIN
    ALTER TABLE tariff_blocks
        ADD CONSTRAINT chk_tariff_blocks_municipality_ibge_format
        CHECK (municipality_ibge IS NULL OR municipality_ibge ~ '^[0-9]{7}$');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

ALTER TABLE tariff_social_bands
    ADD COLUMN IF NOT EXISTS municipality_ibge TEXT NULL;
DO $$ BEGIN
    ALTER TABLE tariff_social_bands
        ADD CONSTRAINT chk_tariff_social_bands_municipality_ibge_format
        CHECK (municipality_ibge IS NULL OR municipality_ibge ~ '^[0-9]{7}$');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
