-- ========================================================================
-- SIGSAN - inicializacao do PostgreSQL
-- ========================================================================
--
-- REGRA CRITICA DE SEGURANCA
--
-- A role que a aplicacao usa NAO pode ser superusuario e NAO pode ter
-- BYPASSRLS. O POSTGRES_USER do container cria a role como superusuario, e
-- superusuario ignora Row Level Security completamente. Com isso a RLS das
-- policies de tenant fica inerte e qualquer tenant enxerga os dados de
-- todos os outros.
--
-- A ordem aqui importa: o CREATE ROLE do migrador precisa rodar ANTES do
-- ALTER ROLE que remove CREATEROLE da role da aplicacao.
-- ========================================================================

-- ========================================================================
-- 1. EXTENSOES
-- ========================================================================
--
-- As migrations (V1__create_core_schema.sql e a de tenant) usam
-- uuid_generate_v4() e gen_random_uuid(), alem das functions de trigger.
-- As extensões precisam existir antes das migrations, e o role de
-- migracao nao tem privilegio para crea-las.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS "btree_gin";
CREATE EXTENSION IF NOT EXISTS "btree_gist";

-- PostGIS e obrigatorio: as migrations de tenant declaram colunas GEOMETRY(POINT, 4674) e
-- GEOMETRY(LINESTRING, 4674) para enderecos e gps_track. Sem esta extensao o
-- provisionamento falha com 'type geometry does not exist'.
--
CREATE EXTENSION IF NOT EXISTS "postgis";

-- A aplicacao precisa poder usar as funcoes das extensoes.
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO sigsan_app;

-- ========================================================================
-- 2. ROLE DE MIGRACAO
-- ========================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sigsan_migrator') THEN
        CREATE ROLE sigsan_migrator LOGIN PASSWORD 'sigsan_migrator_password'
            NOSUPERUSER NOCREATEDB NOCREATEROLE;
    END IF;
END
$$;

GRANT CONNECT ON DATABASE sigsan TO sigsan_migrator;
GRANT ALL ON SCHEMA public TO sigsan_migrator;
GRANT ALL PRIVILEGES ON DATABASE sigsan TO sigsan_migrator;

-- ========================================================================
-- 3. REBAIXAR A ROLE DA APLICACAO
-- ========================================================================
--
-- NOSUPERUSER + NOBYPASSRLS sao o que faz a RLS valer. Sem os dois, a
-- policy USING (tenant_id = current_tenant_id()) e simplesmente ignorada.

ALTER ROLE sigsan_app NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE INHERIT;

-- Sem isto, um tenant pode resolver funcoes de schemas de outros tenants.
ALTER ROLE sigsan_app SET search_path = public;

-- ========================================================================
-- 4. PRIVILEGIOS DA APLICACAO
-- ========================================================================

-- A aplicacao le e escreve dados, mas nao cria schema.
GRANT CONNECT ON DATABASE sigsan TO sigsan_app;
GRANT USAGE ON SCHEMA public TO sigsan_app;

-- Operacoes nas tabelas public ja criadas pela migracao sao concedidas
-- pela propria migracao. Aqui ficam as sequencias e os defaults futuros.
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO sigsan_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sigsan_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO sigsan_app;

-- Em schemas de tenant a aplicacao precisa de USAGE para resolver nomes.
-- A separacao dos dados e feita pelas policies RLS; o privilegio de tabela
-- e concedido pela migracao do tenant.

-- ========================================================================
-- 5. FUNCAO DE CONTEXTO DE TENANT
-- ========================================================================
--
-- As policies RLS chamam current_tenant_id(), que le a GUC de sessao
-- app.current_tenant. A aplicacao seta essa GUC via SET LOCAL na transacao.
-- Fica em public porque e compartilhada por todos os schemas de tenant.

CREATE OR REPLACE FUNCTION public.current_tenant_id() RETURNS UUID AS $fn$
DECLARE
    tenant_text TEXT;
BEGIN
    tenant_text := current_setting('app.current_tenant', TRUE);
    IF tenant_text IS NULL OR tenant_text = '' THEN
        RETURN NULL;
    END IF;
    RETURN tenant_text::UUID;
EXCEPTION
    WHEN invalid_text_representation THEN
        RETURN NULL;
END;
$fn$ LANGUAGE plpgsql STABLE;

GRANT EXECUTE ON FUNCTION public.current_tenant_id() TO sigsan_app;

-- ========================================================================
-- 6. RLS NAS TABELAS COMPARTILHADAS
-- ========================================================================
--
-- public.audit_log, public.tenant_catalog e public.tenant_settings ficam fora dos schemas de
-- tenant, entao as policies por schema nao as protegem. Sem RLS aqui, qualquer requisicao
-- autenticada lia o historico de todos os tenants (incluindo snapshots JSONB de cada linha
-- alterada) e a lista de concorrentes com CNPJ, email e telefone.
--
-- FORCE e necessario porque o dono (sigsan_migrator) ignora RLS sem ele.
--
-- Alem das policies abaixo, o trigger de auditoria remove password_hash e mfa_secret antes
-- de gravar. Ver V1_1__create_tenant_schema.sql.

-- audit_log e a tabela critica: guarda o historico completo de operacoes, com snapshots JSONB
-- de cada linha alterada. Sem RLS aqui, qualquer requisicao autenticada lia o historico de
-- todos os tenants.
--
-- tenant_catalog NAO recebe RLS de proposito. E o cadastro global da plataforma, e a policy
-- por id = current_tenant_id() causa dois problemas reais:
--   1. O proprio INSERT de provisionamento passa a depender da GUC, o que torna o registro
--      do tenant circular (precisa de contexto para se cadastrar).
--   2. Listar os tenants da plataforma e uma operacao legitima de administracao, que
--      NENHUM tenant poderia fazer com a policy por id.
-- O controle de acesso a esse cadastro e problema de autorizacao de aplicacao (qual papel ve
-- a lista global), resolvido na camada de API quando existir, nao com RLS por tenant.

DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['audit_log', 'tenant_settings'] LOOP
        IF EXISTS (SELECT 1 FROM pg_class c
                   JOIN pg_namespace n ON n.oid = c.relnamespace
                   WHERE n.nspname = 'public' AND c.relname = t) THEN
            EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t);
            EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY', t);
        END IF;
    END LOOP;
END
$$;

-- ========================================================================
-- 7. VERIFICACAO
-- ========================================================================
--
-- Deve retornar exatamente uma linha com f = f.
-- Se retorna t = t, a RLS esta inerte e o isolamento de tenant nao existe.
DO $$
DECLARE
    bypass BOOLEAN;
    semRls TEXT;
BEGIN
    SELECT rolbypassrls OR rolsuper INTO bypass
    FROM pg_roles WHERE rolname = 'sigsan_app';

    IF bypass THEN
        RAISE WARNING 'FALHA CRITICA: sigsan_app tem BYPASSRLS ou SUPERUSER. RLS inerte.';
    ELSE
        RAISE NOTICE 'OK: sigsan_app sem BYPASSRLS. RLS ativa.';
    END IF;

    -- Falha alto se alguma tabela compartilhada ficou sem RLS.
    SELECT string_agg(c.relname, ', ') INTO semRls
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relname IN ('audit_log', 'tenant_catalog', 'tenant_settings')
      AND NOT c.relrowsecurity;

    IF semRls IS NOT NULL THEN
        RAISE WARNING 'FALHA CRITICA: tabela compartilhada sem RLS: %', semRls;
    ELSE
        RAISE NOTICE 'OK: tabelas compartilhadas com RLS.';
    END IF;
END
$$;
