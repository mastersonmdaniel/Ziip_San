-- P2.9 -- Preflight da carga. Rode com psql -v ON_ERROR_STOP=1 e as variaveis:
--   tenant_id, reference_month (AAAA-MM-01), ligacoes, faturar
-- Falha cedo e alto se o ambiente nao esta no estado que a carga pressupoe.
\set ON_ERROR_STOP on
DO $$
DECLARE
    n int;
BEGIN
    -- Tenant precisa existir no catalogo de schemas? O ZiipSan usa schema por
    -- tenant via search_path/RLS com tenant_id nas tabelas: conferimos que as
    -- tabelas do caminho existem NO SCHEMA DO TENANT (current_schema()).
    SELECT count(*) INTO n FROM information_schema.tables
     WHERE table_schema = current_schema()
       AND table_name IN ('consumer_units','categories','tariffs','bills',
                          'meters','meter_readings','consumption_records',
                          'billing_groups','billing_references');
    IF n < 9 THEN
        RAISE EXCEPTION 'preflight P2.9: tabelas do caminho de faturamento ausentes (% de 9); rode as migrations (tenant migrado?)', n;
    END IF;
    -- A referencia da carga nao pode ja ter contas: rerodar por cima de conta
    -- viva esconderia duplicidade (a idempotencia da V4_1 barra, mas o numero
    -- final deixaria de provar as 100 mil desta execucao).
    SELECT count(*) INTO n FROM bills
     WHERE tenant_id = :'tenant_id'::uuid AND reference_month = :'reference_month'::date
       AND status NOT IN ('CANCELLED','CANCELLED_BY_RECTIFICATION','PROCESSING_ERROR');
    IF n > 0 THEN
        RAISE EXCEPTION 'preflight P2.9: ja existem % contas vivas em %; use outra referencia ou limpe a carga anterior (99-limpeza.sql)', n, :'reference_month';
    END IF;
    -- NOTA (correcao pos-revisao): a checagem de CATEGORIAS nao mora mais
    -- aqui. As categorias do tenant sao criadas pelo 10-seed-registry.sql,
    -- que roda DEPOIS deste preflight no orquestrador; exigi-las aqui fazia
    -- a primeira execucao em tenant descartavel novo abortar por construcao.
    -- A checagem vive agora no passo pos-registry do executar-carga.sh.
    RAISE NOTICE 'preflight P2.9 OK: tenant %, referencia %, ligacoes %, faturar %',
        :'tenant_id', :'reference_month', :'ligacoes', :'faturar';
END $$;
