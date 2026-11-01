-- ========================================================================
-- ZIIPSAN - seguranca do PostgreSQL (roles, donos, privilegios)
-- ========================================================================
--
-- Executado por 00-roles.sh, como superusuario de bootstrap (POSTGRES_USER=postgres), no banco
-- POSTGRES_DB, uma unica vez (o entrypoint so roda o init com o diretorio de dados vazio).
-- Variaveis psql: app_pw, migrator_pw.
--
-- MODELO DE ROLES
--
--   postgres         superusuario de bootstrap. So init e operacao; NUNCA a aplicacao.
--   ziipsan_owner     NOLOGIN. Dono do banco, do schema public e de current_tenant_id().
--   ziipsan_migrator  LOGIN. DDL: cria tabelas em public (migration de core) e schemas de tenant.
--   ziipsan_app       LOGIN. A aplicacao: le e escreve dados, sem CREATE em lugar nenhum.
--
-- Nenhuma role de login e membro de ziipsan_owner.
--
-- POR QUE ASSIM (achado C1 do PDCA de 12/10/2026)
--
-- Antes, ziipsan_app era o POSTGRES_USER: criava o banco como superusuario e se rebaixava no fim do
-- init. Com isso continuava DONA do banco, de public e de current_tenant_id(), e podia redefinir a
-- funcao que as policies de RLS consultam (CREATE OR REPLACE FUNCTION, reproduzido em 12/10/2026)
-- e fazer a RLS devolver o tenant que quisesse. Alem disso o cluster ficava sem nenhum
-- superusuario, e o PostgreSQL 16+ recusa rebaixar o usuario de bootstrap.
--
-- REGRA CRITICA: ziipsan_app NAO pode ser superusuario nem ter BYPASSRLS. Superusuario ignora Row
-- Level Security; com isso as policies de tenant ficam inertes e um tenant le os dados de todos.
-- ========================================================================

\set ON_ERROR_STOP on

-- ========================================================================
-- 1. EXTENSOES (so o superusuario pode criar)
-- ========================================================================
--
-- As migrations usam uuid_generate_v4(), gen_random_uuid(), trigram e colunas GEOMETRY. As
-- migrations declaram as extensoes com IF NOT EXISTS, mas a role de migracao nao pode cria-las.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS "btree_gin";
CREATE EXTENSION IF NOT EXISTS "btree_gist";
CREATE EXTENSION IF NOT EXISTS "postgis";

-- ========================================================================
-- 2. ROLES
-- ========================================================================
--
-- As senhas chegam por variavel (00-roles.sh), nunca escritas aqui. \gexec porque variavel psql
-- nao e interpolada dentro de bloco DO.

SELECT 'CREATE ROLE ziipsan_owner NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ziipsan_owner') \gexec

SELECT format('CREATE ROLE ziipsan_migrator LOGIN PASSWORD %L', :'migrator_pw')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ziipsan_migrator') \gexec

SELECT format('CREATE ROLE ziipsan_app LOGIN PASSWORD %L', :'app_pw')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ziipsan_app') \gexec

-- Atributos impostos mesmo se a role ja existia.
ALTER ROLE ziipsan_owner NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
ALTER ROLE ziipsan_migrator LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
ALTER ROLE ziipsan_app LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;

-- Sem isto, um tenant pode resolver funcoes de schemas de outros tenants.
ALTER ROLE ziipsan_app SET search_path = public;

-- P0.9: timeouts so na role da aplicacao (NUNCA no migrador: migrations e
-- provisionamento sao longos e um timeout de app no meio cancelaria a migration).
-- Valores decididos pelo coordenador no turno 5 e registrados na ARQUITETURA-ALVO:
-- statement_timeout 5s, idle_in_transaction_session_timeout 30s (valores-alvo da ARQUITETURA-ALVO §4).
ALTER ROLE ziipsan_app SET statement_timeout = '5s';
ALTER ROLE ziipsan_app SET idle_in_transaction_session_timeout = '30s';

-- ========================================================================
-- 3. DONO DO BANCO E DO SCHEMA PUBLIC
-- ========================================================================

SELECT format('ALTER DATABASE %I OWNER TO ziipsan_owner', current_database()) \gexec
ALTER SCHEMA public OWNER TO ziipsan_owner;

-- Nada por padrao para PUBLIC: cada role recebe so o que precisa.
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', current_database()) \gexec
REVOKE ALL ON SCHEMA public FROM PUBLIC;

-- Migracao: conecta, cria schemas de tenant (CREATE no banco) e tabelas em public.
SELECT format('GRANT CONNECT, CREATE, TEMPORARY ON DATABASE %I TO ziipsan_migrator', current_database()) \gexec
GRANT USAGE, CREATE ON SCHEMA public TO ziipsan_migrator;

-- Aplicacao: conecta e resolve nomes em public. Sem CREATE e sem TEMPORARY. Os privilegios de
-- tabela sao concedidos pelas migrations (core e tenant), tabela a tabela.
SELECT format('GRANT CONNECT ON DATABASE %I TO ziipsan_app', current_database()) \gexec
GRANT USAGE ON SCHEMA public TO ziipsan_app;

-- ========================================================================
-- 4. FUNCAO DE CONTEXTO DE TENANT
-- ========================================================================
--
-- As policies RLS chamam current_tenant_id(), que le a GUC de sessao app.current_tenant. Fica em
-- public porque e compartilhada por todos os schemas de tenant. O dono e ziipsan_owner: nem a
-- aplicacao nem o migrador podem redefini-la.

-- P0.18: SQL STABLE, sem bloco EXCEPTION. A versao plpgsql anterior criava uma
-- subtransacao por invocacao (por linha, quando a RLS a avalia). O guarda regex
-- substitui o EXCEPTION: GUC ausente, vazia ou invalida devolve NULL, sem erro.
CREATE OR REPLACE FUNCTION public.current_tenant_id() RETURNS UUID
LANGUAGE sql STABLE AS $fn$
    SELECT CASE
        WHEN current_setting('app.current_tenant', TRUE)
             ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
        THEN current_setting('app.current_tenant', TRUE)::UUID
        ELSE NULL
    END
$fn$;

ALTER FUNCTION public.current_tenant_id() OWNER TO ziipsan_owner;
REVOKE ALL ON FUNCTION public.current_tenant_id() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.current_tenant_id() TO ziipsan_app, ziipsan_migrator;

-- ========================================================================
-- 5. VERIFICACAO (aborta o init se algo estiver errado)
-- ========================================================================
--
-- RAISE EXCEPTION, nao WARNING: com ON_ERROR_STOP o entrypoint aborta e o container nao sobe.
-- Antes, a verificacao so emitia WARNING e o banco subia com a RLS inerte.

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT rolname, rolsuper, rolbypassrls, rolcreaterole, rolcreatedb
             FROM pg_roles WHERE rolname IN ('ziipsan_app', 'ziipsan_migrator', 'ziipsan_owner') LOOP
        IF r.rolsuper OR r.rolbypassrls OR r.rolcreaterole OR r.rolcreatedb THEN
            RAISE EXCEPTION 'FALHA CRITICA: % tem SUPERUSER/BYPASSRLS/CREATEROLE/CREATEDB', r.rolname;
        END IF;
    END LOOP;

    IF pg_has_role('ziipsan_app', 'ziipsan_owner', 'MEMBER')
       OR pg_has_role('ziipsan_migrator', 'ziipsan_owner', 'MEMBER') THEN
        RAISE EXCEPTION 'FALHA CRITICA: role de login e membro de ziipsan_owner';
    END IF;

    IF (SELECT pg_get_userbyid(proowner) FROM pg_proc
        WHERE oid = 'public.current_tenant_id()'::regprocedure) <> 'ziipsan_owner' THEN
        RAISE EXCEPTION 'FALHA CRITICA: current_tenant_id() nao pertence a ziipsan_owner';
    END IF;

    -- P0.18: a funcao vigente tem de ser SQL STABLE (provolatile 's', linguagem sql).
    -- Uma regressao para plpgsql recriaria a subtransacao por linha da RLS.
    IF (SELECT l.lanname FROM pg_proc p JOIN pg_language l ON l.oid = p.prolang
        WHERE p.oid = 'public.current_tenant_id()'::regprocedure) <> 'sql'
       OR (SELECT provolatile FROM pg_proc
        WHERE oid = 'public.current_tenant_id()'::regprocedure) <> 's' THEN
        RAISE EXCEPTION 'FALHA CRITICA: current_tenant_id() nao e SQL STABLE';
    END IF;

    -- P0.9: timeouts da role da aplicacao conferidos no init (pg_db_role_setting
    -- guarda os ALTER ROLE ... SET em setconfig; setdatabase = 0 = todas as bases).
    IF NOT EXISTS (SELECT 1 FROM pg_db_role_setting s
        WHERE s.setrole = (SELECT oid FROM pg_authid WHERE rolname = 'ziipsan_app')
        AND s.setdatabase = 0
        AND s.setconfig @> ARRAY['statement_timeout=5s',
                                  'idle_in_transaction_session_timeout=30s']) THEN
        RAISE EXCEPTION 'FALHA CRITICA: timeouts da ziipsan_app ausentes';
    END IF;

    IF (SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = current_database()) <> 'ziipsan_owner'
       OR (SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname = 'public') <> 'ziipsan_owner' THEN
        RAISE EXCEPTION 'FALHA CRITICA: banco ou schema public nao pertencem a ziipsan_owner';
    END IF;

    IF has_schema_privilege('ziipsan_app', 'public', 'CREATE')
       OR has_database_privilege('ziipsan_app', current_database(), 'CREATE')
       OR has_database_privilege('ziipsan_app', current_database(), 'TEMPORARY') THEN
        RAISE EXCEPTION 'FALHA CRITICA: ziipsan_app pode criar objetos';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolsuper) THEN
        RAISE EXCEPTION 'FALHA CRITICA: cluster sem superusuario';
    END IF;

    RAISE NOTICE 'OK: roles, donos e privilegios conferidos';
END
$$;
