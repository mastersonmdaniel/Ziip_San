-- P3.23 -- Preflight da demonstracao. Rode com psql -v ON_ERROR_STOP=1 e:
--   tenant_id, demo_schema, unidades, reference_month
-- Falha cedo e alto se o ambiente nao esta no estado que a demo pressupoe.
\set ON_ERROR_STOP on
DO $$
DECLARE
    n int;
BEGIN
    -- Separacao demo x homologacao comeca no schema: as sementes da demo
    -- so podem cair no schema do tenant de demonstracao.
    IF current_schema() <> :'demo_schema' THEN
        RAISE EXCEPTION 'preflight P3.23: schema atual % difere do schema da demonstracao % -- abortado antes de semear no lugar errado',
            current_schema(), :'demo_schema';
    END IF;
    -- Tabelas do caminho (mesma lista conferida pela P2.9, DDL de tenant).
    SELECT count(*) INTO n FROM information_schema.tables
     WHERE table_schema = current_schema()
       AND table_name IN ('consumer_units','categories','tariffs','bills',
                          'meters','meter_readings','consumption_records',
                          'billing_groups','billing_references');
    IF n < 9 THEN
        RAISE EXCEPTION 'preflight P3.23: tabelas do caminho ausentes (% de 9); rode as migrations (tenant demo migrado?)', n;
    END IF;
    -- Piso do aceite, conferido ja no preflight (o gerador tambem recusa).
    IF :unidades < 10000 THEN
        RAISE EXCEPTION 'preflight P3.23: unidades % abaixo do piso de 10000 do aceite', :unidades;
    END IF;
    RAISE NOTICE 'preflight P3.23 OK: tenant %, schema %, unidades %, referencia %',
        :'tenant_id', :'demo_schema', :unidades, :'reference_month';
END $$;
