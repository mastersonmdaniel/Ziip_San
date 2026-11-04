-- Core Migration — Version: 10
-- Description: P2.1 — fundacao de lote: job_runs, rotas e registro de disparos.
--
-- O QUE MUDA (plano v3.0 §3, core V10, reservada para a P2.1)
--   * public.job_runs: uma linha por execucao de lote, identificada por
--     (tenant_id, cycle_ref). A unicidade (tenant, ciclo) E a trava: o
--     segundo disparo do mesmo (tenant, ciclo) esbarra no UNIQUE e e
--     recusado, com registro em job_run_dispatches — nunca corre duas
--     vezes. O reinicio de uma execucao interrompida reusa esta linha e
--     so executa as rotas nao concluidas (ver job_run_routes).
--   * public.job_run_routes: uma linha por rota da execucao, em ordem
--     (sequence). DONE nunca e reprocessado; PENDING, FAILED e RUNNING
--     interrompida voltam na retomada.
--   * public.job_run_dispatches: registro de CADA pedido de disparo
--     (aceito ou recusado por duplicidade), para auditoria do disparo
--     duplo exigida pela P2.1.
--   * Tabelas BATCH_* do Spring Batch 6.0.5 (schema-postgresql.sql do
--     spring-batch-core, com IF NOT EXISTS acrescentado): o JobRepository
--     JDBC do Batch grava aqui a execucao de cada rota. Sem dado pessoal:
--     os parametros do job sao tenant_id, cycle_ref, route, run_id,
--     reference_date e timezone. Por serem tabelas do framework, sem
--     coluna de tenant e sem leitura por tenant na aplicacao, elas NAO
--     recebem RLS — o isolamento vive em job_runs/job_run_routes, e o
--     acesso e so pelas roles abaixo, via GRANT.
--
-- TEMPO (P0.19, PLT-14): instantes em TIMESTAMPTZ (UTC); reference_date e
-- a data de negocio do tenant (data local no fuso tenant.timezone no
-- instante do disparo) e timezone guarda o identificador IANA usado.
-- O agendamento (BatchCalendar) ajusta para o proximo dia util do
-- calendario operacional do tenant; scheduled_for aqui e o instante
-- resultante, em UTC.
--
-- RLS (mesmo padrao da V1/V8): FORCADA nas tres tabelas de lote. A
-- aplicacao so ve e so grava linhas do tenant do contexto
-- (tenant_id = current_tenant_id()); a role ziipsan_worker le linhas de
-- todos os tenants, e so nestas tabelas (policy propria, USING true).
-- Como a V8, esta migration falha rapido se as roles ainda nao
-- existirem: migration e security.sql viajam no mesmo commit.

CREATE TABLE IF NOT EXISTS public.job_runs (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    cycle_ref TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'FAILED', 'INTERRUPTED')),
    reference_date DATE NOT NULL,
    timezone TEXT NOT NULL,
    triggered_by UUID,
    scheduled_for TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_job_runs_tenant_cycle UNIQUE (tenant_id, cycle_ref)
);

CREATE INDEX IF NOT EXISTS idx_job_runs_tenant_status
    ON public.job_runs (tenant_id, status, created_at);

CREATE TABLE IF NOT EXISTS public.job_run_routes (
    id UUID NOT NULL PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES public.job_runs (id),
    tenant_id UUID NOT NULL,
    route TEXT NOT NULL,
    sequence INTEGER NOT NULL CHECK (sequence >= 0),
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_error TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_job_run_routes_run_route UNIQUE (run_id, route)
);

CREATE INDEX IF NOT EXISTS idx_job_run_routes_run_sequence
    ON public.job_run_routes (run_id, sequence);

CREATE TABLE IF NOT EXISTS public.job_run_dispatches (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    cycle_ref TEXT NOT NULL,
    outcome TEXT NOT NULL
        CHECK (outcome IN ('ACCEPTED', 'REFUSED_DUPLICATE')),
    run_id UUID REFERENCES public.job_runs (id),
    requested_by UUID,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reason TEXT
);

CREATE INDEX IF NOT EXISTS idx_job_run_dispatches_tenant_cycle
    ON public.job_run_dispatches (tenant_id, cycle_ref, requested_at);

ALTER TABLE public.job_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.job_runs FORCE ROW LEVEL SECURITY;
ALTER TABLE public.job_run_routes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.job_run_routes FORCE ROW LEVEL SECURITY;
ALTER TABLE public.job_run_dispatches ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.job_run_dispatches FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS job_runs_tenant_isolation ON public.job_runs;
CREATE POLICY job_runs_tenant_isolation ON public.job_runs
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
DROP POLICY IF EXISTS job_runs_worker_all ON public.job_runs;
CREATE POLICY job_runs_worker_all ON public.job_runs
    TO ziipsan_worker
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS job_run_routes_tenant_isolation ON public.job_run_routes;
CREATE POLICY job_run_routes_tenant_isolation ON public.job_run_routes
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
DROP POLICY IF EXISTS job_run_routes_worker_all ON public.job_run_routes;
CREATE POLICY job_run_routes_worker_all ON public.job_run_routes
    TO ziipsan_worker
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS job_run_dispatches_tenant_isolation ON public.job_run_dispatches;
CREATE POLICY job_run_dispatches_tenant_isolation ON public.job_run_dispatches
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
DROP POLICY IF EXISTS job_run_dispatches_worker_all ON public.job_run_dispatches;
CREATE POLICY job_run_dispatches_worker_all ON public.job_run_dispatches
    TO ziipsan_worker
    USING (true)
    WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE ON public.job_runs TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON public.job_run_routes TO ziipsan_app;
GRANT SELECT, INSERT ON public.job_run_dispatches TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON public.job_runs TO ziipsan_worker;
GRANT SELECT, INSERT, UPDATE ON public.job_run_routes TO ziipsan_worker;
GRANT SELECT, INSERT ON public.job_run_dispatches TO ziipsan_worker;

-- Tabelas do Spring Batch 6.0.5 (org/springframework/batch/core/schema-postgresql.sql,
-- com IF NOT EXISTS; o conteudo e o do framework, sem coluna de tenant).
CREATE TABLE IF NOT EXISTS BATCH_JOB_INSTANCE (
    JOB_INSTANCE_ID BIGINT NOT NULL PRIMARY KEY,
    VERSION BIGINT,
    JOB_NAME VARCHAR(100) NOT NULL,
    JOB_KEY VARCHAR(32) NOT NULL,
    constraint JOB_INST_UN unique (JOB_NAME, JOB_KEY)
);

CREATE TABLE IF NOT EXISTS BATCH_JOB_EXECUTION (
    JOB_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
    VERSION BIGINT,
    JOB_INSTANCE_ID BIGINT NOT NULL,
    CREATE_TIME TIMESTAMP NOT NULL,
    START_TIME TIMESTAMP DEFAULT NULL,
    END_TIME TIMESTAMP DEFAULT NULL,
    STATUS VARCHAR(10),
    EXIT_CODE VARCHAR(2500),
    EXIT_MESSAGE VARCHAR(2500),
    LAST_UPDATED TIMESTAMP,
    constraint JOB_INST_EXEC_FK foreign key (JOB_INSTANCE_ID)
        references BATCH_JOB_INSTANCE(JOB_INSTANCE_ID)
);

CREATE TABLE IF NOT EXISTS BATCH_JOB_EXECUTION_PARAMS (
    JOB_EXECUTION_ID BIGINT NOT NULL,
    PARAMETER_NAME VARCHAR(100) NOT NULL,
    PARAMETER_TYPE VARCHAR(100) NOT NULL,
    PARAMETER_VALUE VARCHAR(2500),
    IDENTIFYING CHAR(1) NOT NULL,
    constraint JOB_EXEC_PARAMS_FK foreign key (JOB_EXECUTION_ID)
        references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
);

CREATE TABLE IF NOT EXISTS BATCH_STEP_EXECUTION (
    STEP_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
    VERSION BIGINT NOT NULL,
    STEP_NAME VARCHAR(100) NOT NULL,
    JOB_EXECUTION_ID BIGINT NOT NULL,
    CREATE_TIME TIMESTAMP NOT NULL,
    START_TIME TIMESTAMP DEFAULT NULL,
    END_TIME TIMESTAMP DEFAULT NULL,
    STATUS VARCHAR(10),
    COMMIT_COUNT BIGINT,
    READ_COUNT BIGINT,
    FILTER_COUNT BIGINT,
    WRITE_COUNT BIGINT,
    READ_SKIP_COUNT BIGINT,
    WRITE_SKIP_COUNT BIGINT,
    PROCESS_SKIP_COUNT BIGINT,
    ROLLBACK_COUNT BIGINT,
    EXIT_CODE VARCHAR(2500),
    EXIT_MESSAGE VARCHAR(2500),
    LAST_UPDATED TIMESTAMP,
    constraint JOB_EXEC_STEP_FK foreign key (JOB_EXECUTION_ID)
        references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
);

CREATE TABLE IF NOT EXISTS BATCH_STEP_EXECUTION_CONTEXT (
    STEP_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
    SHORT_CONTEXT VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT TEXT,
    constraint STEP_EXEC_CTX_FK foreign key (STEP_EXECUTION_ID)
        references BATCH_STEP_EXECUTION(STEP_EXECUTION_ID)
);

CREATE TABLE IF NOT EXISTS BATCH_JOB_EXECUTION_CONTEXT (
    JOB_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
    SHORT_CONTEXT VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT TEXT,
    constraint JOB_EXEC_CTX_FK foreign key (JOB_EXECUTION_ID)
        references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
);

CREATE SEQUENCE IF NOT EXISTS BATCH_STEP_EXECUTION_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS BATCH_JOB_EXECUTION_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS BATCH_JOB_INSTANCE_SEQ MAXVALUE 9223372036854775807 NO CYCLE;

GRANT SELECT, INSERT, UPDATE, DELETE ON BATCH_JOB_INSTANCE TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON BATCH_JOB_EXECUTION TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON BATCH_JOB_EXECUTION_PARAMS TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON BATCH_STEP_EXECUTION TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON BATCH_STEP_EXECUTION_CONTEXT TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON BATCH_JOB_EXECUTION_CONTEXT TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON BATCH_JOB_INSTANCE TO ziipsan_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON BATCH_JOB_EXECUTION TO ziipsan_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON BATCH_JOB_EXECUTION_PARAMS TO ziipsan_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON BATCH_STEP_EXECUTION TO ziipsan_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON BATCH_STEP_EXECUTION_CONTEXT TO ziipsan_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON BATCH_JOB_EXECUTION_CONTEXT TO ziipsan_worker;
GRANT USAGE, SELECT, UPDATE ON BATCH_STEP_EXECUTION_SEQ TO ziipsan_app;
GRANT USAGE, SELECT, UPDATE ON BATCH_JOB_EXECUTION_SEQ TO ziipsan_app;
GRANT USAGE, SELECT, UPDATE ON BATCH_JOB_INSTANCE_SEQ TO ziipsan_app;
GRANT USAGE, SELECT, UPDATE ON BATCH_STEP_EXECUTION_SEQ TO ziipsan_worker;
GRANT USAGE, SELECT, UPDATE ON BATCH_JOB_EXECUTION_SEQ TO ziipsan_worker;
GRANT USAGE, SELECT, UPDATE ON BATCH_JOB_INSTANCE_SEQ TO ziipsan_worker;

COMMENT ON TABLE public.job_runs IS
    'P2.1: execucao de lote por (tenant, ciclo); a unicidade (tenant, ciclo) e a trava contra disparo duplo.';
COMMENT ON TABLE public.job_run_routes IS
    'P2.1: rotas de uma execucao de lote, em ordem; DONE nunca e reprocessado na retomada.';
COMMENT ON TABLE public.job_run_dispatches IS
    'P2.1: registro de cada pedido de disparo (aceito ou recusado por duplicidade).';
