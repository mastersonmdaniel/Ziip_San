-- GL.3: contagem de registros e checksum por tabela, no banco restaurado.
--
-- Uso (psql, pelo ensaio-restauracao-local.sh):
--   psql -v ON_ERROR_STOP=1 -v schema_filtro='%' -f verificacao-contagem-checksum.sql
--   psql -v ON_ERROR_STOP=1 -v schema_filtro='tenant_testemunha' -f verificacao-contagem-checksum.sql
--
-- Saida (TSV, uma linha por tabela, ordem deterministica esquema/tabela):
--   esquema <TAB> tabela <TAB> registros <TAB> sha256
--
-- O checksum e o SHA-256 do conteudo canonico da tabela: as linhas em
-- texto (t::text), ordenadas, unidas por '\n', com COALESCE para a tabela
-- vazia (hash da cadeia vazia) -- EXATAMENTE o comando da fotografia da
-- P3.33 (restaurar-tenant.sh / verificar-isolamento-tenant.sh), para o
-- checksum da GL.3 ser comparavel com o da restauracao seletiva.
-- Deterministico para o mesmo conteudo: mesma entrada, mesmo hash.
--
-- schema_filtro: '%' = banco inteiro (etapa de verificacao da restauracao
-- completa); um schema = so ele, por IGUALDADE (prova de nao-afetacao do
-- tenant-testemunha, antes/depois da restauracao seletiva). Igualdade, nao
-- LIKE: '_' em nome de schema nao pode virar coringa e casar um vizinho.
-- Tabelas de pg_catalog/information_schema ficam fora, como na P4.7.
--
-- PONTE DE PARAMETRO (obrigatoria): o psql NAO interpola variaveis
-- (:'...') dentro de entidades citadas -- literais, identificadores e
-- corpos dollar-quoted de blocos DO. Por isso o valor de -v e gravado
-- ANTES do bloco, numa GUC namespacada, por um SELECT comum (onde a
-- interpolacao acontece), e lido dentro do DO com current_setting().
-- Nenhum :'...' pode aparecer dentro de DO $$...$$ neste arquivo -- o
-- harness da GL.3 reprova esse padrao estaticamente.
\set ON_ERROR_STOP on
\pset tuples_only on
\pset format unaligned
\pset fieldsep '\t'

SELECT set_config('gl3.schema_filtro', :'schema_filtro', false) \g /dev/null

BEGIN;
DO $$
DECLARE
    v_filtro text := current_setting('gl3.schema_filtro');
    r record;
    n bigint;
    h text;
BEGIN
    CREATE TEMP TABLE IF NOT EXISTS gl3_contagem_checksum (
        esquema   text,
        tabela    text,
        registros bigint,
        sha256    text
    ) ON COMMIT DROP;
    DELETE FROM gl3_contagem_checksum;
    FOR r IN
        SELECT schemaname, tablename
          FROM pg_tables
         WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
           AND (v_filtro = '%' OR schemaname = v_filtro)
         ORDER BY schemaname, tablename
    LOOP
        EXECUTE format('SELECT count(*) FROM %I.%I', r.schemaname, r.tablename) INTO n;
        EXECUTE format(
            'SELECT encode(sha256(convert_to(coalesce(string_agg(linha, E''\n'' ORDER BY linha), ''''), ''UTF8'')), ''hex'') '
            || 'FROM (SELECT t::text AS linha FROM %I.%I t) s',
            r.schemaname, r.tablename) INTO h;
        INSERT INTO gl3_contagem_checksum VALUES (r.schemaname, r.tablename, n, h);
    END LOOP;
END $$;

SELECT esquema, tabela, registros, sha256
  FROM gl3_contagem_checksum
 ORDER BY esquema, tabela;
COMMIT;
