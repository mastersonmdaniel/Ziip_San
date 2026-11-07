-- GL.3: teste de isolamento RLS no banco restaurado, executado COMO a role
-- da aplicacao (ziipsan_app, NOSUPERUSER e NOBYPASSRLS -- a role real, como
-- no TenantRlsIsolationTest do core). Rodar conectado como ziipsan_app
-- (o orquestrador conecta com a senha efemera do container descartavel);
-- conectar como superusuario NAO prova nada e este arquivo se recusa a
-- rodar nesse caso (guarda abaixo).
--
-- Uso (psql, pelo ensaio-restauracao-local.sh):
--   psql -U ziipsan_app -v ON_ERROR_STOP=1 \
--        -v schema_alvo='tenant_alvo' -v tenant_alvo='11111111-...' \
--        -v tenant_outro='22222222-...' \
--        -f teste-rls-restaurado.sql
--
-- PONTE DE PARAMETROS (obrigatoria): o psql NAO interpola variaveis
-- (:'...') dentro de entidades citadas -- literais, identificadores e
-- corpos dollar-quoted de blocos DO. Os tres valores de -v sao gravados
-- ANTES dos blocos, em GUCs namespacadas, por um SELECT comum (onde a
-- interpolacao acontece), e lidos dentro dos DO com current_setting().
-- Nenhum :'...' pode aparecer dentro de DO $$...$$ neste arquivo -- o
-- harness da GL.3 reprova esse padrao estaticamente. (A saida do SELECT
-- da ponte vai para /dev/null via \g: o psql deste arquivo roda dentro
-- do container descartavel, chamado pelo orquestrador.)
--
-- Contexto de tenant do projeto: set_config('app.current_tenant', <uuid>, true)
-- (o mesmo GUC que os servicos do core usam). Para CADA tabela do schema do
-- tenant-alvo com RLS habilitada e coluna tenant_id, mede tres contagens:
--   sem contexto          -> esperado 0 (sem contexto, nenhuma linha)
--   contexto do alvo      -> esperado = total da tabela (so as linhas dele)
--   contexto de OUTRO     -> esperado 0 (o contexto alheio nao enxerga nada)
-- O "total" de referencia e a contagem com o contexto do proprio tenant
-- (modelo schema-por-tenant, ADR-0005: a tabela do schema so contem linhas
-- dele; ziipsan_app nao pode desligar a RLS para medir de outro jeito).
-- Tabela vazia nos tres contextos e registrada como VAZIA (nao prova
-- isolamento, nao reprova sozinha); se TODAS forem vazias, o resumo sai
-- RLS_INCONCLUSIVO e a etapa reprova no orquestrador: ensaio sem massa
-- nao e prova.
--
-- Saida (TSV): tabela, sem_contexto, ctx_proprio, ctx_outro, veredito
-- e, ao fim, uma linha de resumo: RLS_RESUMO <TAB> OK|FALHOU|INCONCLUSIVO
\set ON_ERROR_STOP on
\pset tuples_only on
\pset format unaligned
\pset fieldsep '\t'

SELECT set_config('gl3.schema_alvo', :'schema_alvo', false),
       set_config('gl3.tenant_alvo', :'tenant_alvo', false),
       set_config('gl3.tenant_outro', :'tenant_outro', false) \g /dev/null

-- Guarda: como superusuario/postgres a RLS e contornada e o teste e teatro.
DO $$
BEGIN
    IF (SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = current_user) THEN
        RAISE EXCEPTION 'teste de RLS recusado: a sessao e superusuario ou tem BYPASSRLS (%)', current_user;
    END IF;
END $$;

BEGIN;
CREATE TEMP TABLE gl3_rls_result (
    tabela       text,
    sem_contexto bigint,
    ctx_proprio  bigint,
    ctx_outro    bigint,
    veredito     text
) ON COMMIT DROP;

DO $$
DECLARE
    v_schema text := current_setting('gl3.schema_alvo');
    v_alvo   text := current_setting('gl3.tenant_alvo');
    v_outro  text := current_setting('gl3.tenant_outro');
    r record;
    n_sem bigint;
    n_proprio bigint;
    n_outro bigint;
    v text;
BEGIN
    FOR r IN
        SELECT t.tablename
          FROM pg_tables t
          JOIN information_schema.columns c
            ON c.table_schema = t.schemaname AND c.table_name = t.tablename
           AND c.column_name = 'tenant_id'
         WHERE t.schemaname = v_schema
           AND t.rowsecurity
         ORDER BY t.tablename
    LOOP
        PERFORM set_config('app.current_tenant', '', true);
        EXECUTE format('SELECT count(*) FROM %I.%I', v_schema, r.tablename) INTO n_sem;
        PERFORM set_config('app.current_tenant', v_alvo, true);
        EXECUTE format('SELECT count(*) FROM %I.%I', v_schema, r.tablename) INTO n_proprio;
        PERFORM set_config('app.current_tenant', v_outro, true);
        EXECUTE format('SELECT count(*) FROM %I.%I', v_schema, r.tablename) INTO n_outro;
        PERFORM set_config('app.current_tenant', '', true);
        IF n_sem = 0 AND n_outro = 0 AND n_proprio > 0 THEN
            v := 'OK';
        ELSIF n_sem = 0 AND n_outro = 0 AND n_proprio = 0 THEN
            v := 'VAZIA';
        ELSE
            v := 'FALHOU';
        END IF;
        INSERT INTO gl3_rls_result VALUES (r.tablename, n_sem, n_proprio, n_outro, v);
    END LOOP;
END $$;

SELECT tabela, sem_contexto, ctx_proprio, ctx_outro, veredito
  FROM gl3_rls_result
 ORDER BY tabela;

SELECT 'RLS_RESUMO',
       CASE
           WHEN count(*) = 0 THEN 'INCONCLUSIVO'
           WHEN count(*) FILTER (WHERE veredito = 'FALHOU') > 0 THEN 'FALHOU'
           WHEN count(*) FILTER (WHERE veredito = 'OK') = 0 THEN 'INCONCLUSIVO'
           ELSE 'OK'
       END
  FROM gl3_rls_result;
COMMIT;
