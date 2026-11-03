-- P1.9 — Outbox e worker básico (ADR-0014; plano v3.0 §3, core V8).
--
-- O QUE MUDA
--   * Tabela particionada public.outbox (uma por banco, não uma por tenant:
--     a faixa V11_x de tenant da v1 está substituída por esta tabela única,
--     como reserva o §3). Partição diária por RANGE (created_at), com dia em
--     UTC (ver abaixo); a função outbox_ensure_partition cria a partição do
--     dia sob demanda, porque a role da aplicação não tem CREATE.
--   * Colunas da ADR-0014: tenant_id, queue, type, dedup_key, payload (sem
--     dado pessoal, só identificadores), status, attempts, available_at,
--     locked_until, last_error. Mais id (= messageId do contrato da P0.45,
--     a identidade de deduplicação) e created_at/updated_at.
--   * RLS FORÇADA (mesmo padrão da V1 para audit_log/tenant_settings):
--     a aplicação só vê e só grava linhas do tenant do contexto
--     (tenant_id = current_tenant_id()); só a role ziipsan_worker lê linhas
--     de todos os tenants, e só nesta tabela (policy própria, USING true).
--   * Unicidade de deduplicação: UNIQUE (tenant_id, queue, dedup_key,
--     created_at) por partição — ou seja, garantida pelo banco dentro do
--     dia. Entre dias, a deduplicação é do publicador (PostgresMessageQueue:
--     trava consultiva transacional + checagem de existência antes do
--     INSERT), porque índice único em tabela particionada tem de incluir a
--     chave de partição.
--   * Função outbox_ensure_partition(date): SECURITY DEFINER, dona
--     ziipsan_migrator (que tem CREATE em public); a aplicação só tem
--     EXECUTE. Corrida de criação simultânea: o segundo criador recebe
--     duplicate_table e segue (a partição já existe).
--
-- NOMES: o plano e a ADR-0014 citam a role "sigsan_worker", nome anterior à
-- renomeação pós-P0.33. Aqui ela é ziipsan_worker, como ziipsan_app e
-- ziipsan_migrator. A role nasce em infra/postgres/init/sql/security.sql
-- (NOLOGIN; o processo worker recebe LOGIN + senha fora do init, no
-- provisionamento do processo — ver o relatório da frente). Esta migration
-- concede os privilégios e falha rápido se a role ainda não existir, como
-- a V1 faz com ziipsan_app: migration e security.sql viajam no mesmo commit.
--
-- TENANT_SCHEMA (item da P1.9, "remoção avaliada na reescrita da V1_1"):
-- avaliado e sem efeito aqui — não existe coluna tenant_schema no schema
-- core atual (a V1 só a menciona em comentário). Nada a remover; a
-- reescrita da V1_1 continua sendo da P0.25.
--
-- COMPATIBILIDADE: tabela nova, sem código anterior a citar. Partições do
-- dia corrente e do seguinte já nascem aqui, para o primeiro publish não
-- depender só da função. Dia em UTC, não no fuso do tenant: o limite da
-- partição é um instante absoluto (NOW()), e CURRENT_DATE da sessão
-- dependeria do TimeZone da conexão; o publicador chama a função com
-- (NOW() AT TIME ZONE 'UTC')::DATE, o mesmo instante do created_at.

CREATE TABLE IF NOT EXISTS public.outbox (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    queue TEXT NOT NULL DEFAULT 'default',
    type TEXT NOT NULL,
    dedup_key TEXT NOT NULL,
    payload JSONB NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'DEAD')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    available_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    locked_until TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (created_at, id),
    UNIQUE (tenant_id, queue, dedup_key, created_at)
) PARTITION BY RANGE (created_at);

-- Índice da tomada do worker (SKIP LOCKED): por estado e disponibilidade.
CREATE INDEX IF NOT EXISTS idx_outbox_claim
    ON public.outbox (status, available_at, created_at, id);

ALTER TABLE public.outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.outbox FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS outbox_tenant_isolation ON public.outbox;
CREATE POLICY outbox_tenant_isolation ON public.outbox
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- Só o worker lê (e atualiza) linhas de todos os tenants, e só aqui.
DROP POLICY IF EXISTS outbox_worker_all ON public.outbox;
CREATE POLICY outbox_worker_all ON public.outbox
    TO ziipsan_worker
    USING (true)
    WITH CHECK (true);

-- Mínimo privilégio: a aplicação publica e consulta a própria fila;
-- o worker toma e marca, sem publicar nem apagar.
GRANT SELECT, INSERT ON public.outbox TO ziipsan_app;
GRANT SELECT, UPDATE ON public.outbox TO ziipsan_worker;

-- Partição do dia, sob demanda e idempotente. Dona: a role de migração.
CREATE OR REPLACE FUNCTION public.outbox_ensure_partition(p_day DATE)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $fn$
DECLARE
    v_name TEXT := 'outbox_' || to_char(p_day, 'YYYYMMDD');
    v_from TIMESTAMPTZ := (p_day::TIMESTAMP AT TIME ZONE 'UTC');
    v_to TIMESTAMPTZ := ((p_day + 1)::TIMESTAMP AT TIME ZONE 'UTC');
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relname = v_name) THEN
        BEGIN
            EXECUTE format('CREATE TABLE %I PARTITION OF public.outbox'
                           || ' FOR VALUES FROM (%L) TO (%L)',
                           v_name, v_from, v_to);
        EXCEPTION WHEN duplicate_table THEN
            NULL;
        END;
    END IF;
END
$fn$;

REVOKE ALL ON FUNCTION public.outbox_ensure_partition(DATE) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.outbox_ensure_partition(DATE) TO ziipsan_app;

-- Partições do dia corrente e do seguinte (UTC), para o primeiro publish
-- não depender só da chamada da função.
SELECT public.outbox_ensure_partition((NOW() AT TIME ZONE 'UTC')::DATE);
SELECT public.outbox_ensure_partition(((NOW() AT TIME ZONE 'UTC')::DATE) + 1);

COMMENT ON TABLE public.outbox IS
    'P1.9 (ADR-0014): outbox transacional, uma por banco; partição diária (UTC). Payload sem dado pessoal.';
COMMENT ON FUNCTION public.outbox_ensure_partition(DATE) IS
    'P1.9: cria a partição diária da outbox sob demanda (SECURITY DEFINER, dona ziipsan_migrator).';
