-- P1.7 — Particao do audit_log (plano v3.0, linha 631; tabela §3).
--
-- DESVIO DOCUMENTADO (§3 reservava core V7, mas V7__tenant_shard_catalog.sql
-- ja existe, ocupada pelo conteudo da F21 movido na integracao do resto do P0):
-- esta entrega usa core V14. A ordem de aplicacao nao depende disso: o
-- CoreMigrationRunner usa outOfOrder(true) (precedente documentado da V3/V4).
--
-- O QUE MUDA
--   * public.audit_log vira tabela particionada por RANGE (created_at), um
--     mes civil por particao (audit_log_AAAA_MM), em UTC — instantes sao
--     gravados em UTC (PARAMETROS-DO-TENANT §1); o mes da particao e o mes
--     UTC do instante, nunca o mes local do tenant.
--   * PK (created_at, id): a chave de particao precisa estar na PK, e a
--     ordem (tempo, id) mantem a trilha ordenavel dentro da particao.
--   * id DEFAULT uuidv7(): o caminho do trigger (a maioria das linhas) ja
--     nasce v7; o caminho JPA passa a gerar v7 em AuditLog.onCreate (mesma
--     regra, sem depender do default). ATENCAO: o PostgreSQL 17 NAO tem
--     uuidv7() nativo (ele chega no PG 18); por isso esta migration define
--     public.uuidv7() na secao 0, com a mesma semantica (48 bits de millis
--     + versao 7 + variante 10 + aleatorio). No PG 18, o uuidv7() nativo de
--     pg_catalog tem precedencia sobre o public (pg_catalog e sempre
--     buscado primeiro), entao o default continua valido apos o upgrade.
--   * Sem particao DEFAULT, de proposito: linha fora de particao existente
--     falha em vez de cair num balde sem poda, o que esconderia particao
--     faltante e quebraria a poda por periodo. Quem cria particoes e a
--     funcao audit_log_ensure_partitions (papel do migrador, nunca da app:
--     a app so tem USAGE em public). A migration pre-cria o mes corrente
--     e o seguinte; os demais meses exigem a rotina de manutencao
--     (pendencia de wiring: worker/job_runs da P1.9/P2.1 — ver relatorio).
--
-- CONVERSAO COM DADOS (bancos de dev ja tem linhas sinteticas)
--   Renomeia a tabela antiga, cria a particionada, cria uma particao para
--   cada mes presente no legado, copia e apaga o legado — tudo na mesma
--   transacao do Flyway. RLS, policy e grants sao recriados depois do
--   DROP do legado, porque os indices antigos ocupam os nomes da V1 ate la.
--   A funcao de trigger (V1_3) referencia public.audit_log por nome, entao
--   continua escrevendo na tabela nova sem alteracao.
--
-- RETENCAO (G6; classe c — so amplia)
--   * public.audit_retention_policy: uma linha por tenant. retention_months
--     tem CHECK >= 6 (piso absoluto: 6 meses, Marco Civil art. 15). O piso
--     efetivo e o maior prazo de G6 que a trilha comprova, que o tenant
--     expressa via retention_months e/ou cert_retention_years
--     (cert.record_retention_years, ja definido na core V3, classe c):
--     o efetivo e GREATEST(months, cert_years * 12).
--   * Trigger de monotonicidade: qualquer UPDATE que reduza months ou
--     cert_years e recusado. Sem linha de politica, NUNCA ha expurgo
--     (padrao conservador; o PARAMETROS-DO-TENANT registra Sug. "sem" para
--     log.audit_retention_months).
--   * audit_log_purge_expired(reference): DELETE por tenant abaixo do
--     corte. O truque do contexto (set_config por tenant dentro da
--     funcao) existe porque a RLS e FORCED ate para o dono: sem contexto,
--     o DELETE nao veria linha nenhuma. Ao fim, o contexto e limpo.
--   * DROP de particao NAO e funcao desta migration, de proposito: com
--     RLS FORCED, nenhum papel sem BYPASSRLS consegue contar linhas de
--     todos os tenants dentro da funcao, entao um "drop se vazia" mentiria
--     (contaria 0 com a particao cheia). O DROP apos expurgo e passo de
--     operacao com superusuario (que ignora a RLS e conta de verdade),
--     documentado aqui e nao automatizado.
--
-- PARAMETROS (catalogo da core V3; cert.record_retention_years ja existe la)
--   * log.audit_retention_months (INT, classe c): meses de retencao da
--     trilha; piso 6 meses e piso do maior prazo de G6 que ela comprova.
--   * log.export_enabled (BOOLEAN, classe d, padrao false): exportacao
--     continua da trilha para o SIEM do cliente.
--   * log.export_format (TEXT, classe d, padrao JSONL; ALLOWED_VALUES
--     JSONL/CEF): formato da exportacao (RNF-52: sem dado pessoal em claro
--     — a exportacao leva so metadados do evento, nunca payloads nem IP).
--   A leitura do valor vigente por tenant (parameter_versions, V1_5) e
--   trabalho da tela de parametros (P1.6); os servicos desta frente
--   recebem enabled/format explicitamente ate la (pendencia registrada).

-- ======================================================================
-- 0. uuidv7() para PostgreSQL 17 (nativo so no PG 18; ver cabecalho)
-- ======================================================================

CREATE OR REPLACE FUNCTION public.uuidv7()
RETURNS UUID LANGUAGE sql SET search_path = pg_catalog, public, pg_temp AS $$
    SELECT (
        substr(v_hex, 1, 8) || '-' || substr(v_hex, 9, 4) || '-'
        || substr(v_hex, 13, 4) || '-' || substr(v_hex, 17, 4) || '-' || substr(v_hex, 21, 12)
    )::UUID
    FROM (
        SELECT lpad(to_hex((EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT), 12, '0')
               || '7' || substr(md5(random()::TEXT), 1, 3)
               || '8' || substr(md5(random()::TEXT), 1, 3)
               || substr(md5(random()::TEXT), 1, 12) AS v_hex
    ) AS s;
$$;

-- clock_timestamp() (nao now()): o default e avaliado por linha, e linhas
-- do mesmo statement precisam de millis distintos para ordenar por tempo.
REVOKE ALL ON FUNCTION public.uuidv7() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.uuidv7() TO ziipsan_migrator, ziipsan_app;

-- ======================================================================
-- 1. Gestao de particoes (so o migrador: DDL em public)
-- ======================================================================

CREATE OR REPLACE FUNCTION public.audit_log_create_partition(p_month DATE)
RETURNS TEXT LANGUAGE plpgsql SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE
    v_start DATE := date_trunc('month', p_month)::DATE;
    v_start_ts TEXT := to_char(v_start, 'YYYY-MM-DD') || ' 00:00:00+00';
    v_end_ts TEXT := to_char((v_start + INTERVAL '1 month')::DATE, 'YYYY-MM-DD') || ' 00:00:00+00';
    v_name TEXT := 'audit_log_' || to_char(v_start, 'YYYY_MM');
BEGIN
    IF to_regclass('public.' || v_name) IS NULL THEN
        EXECUTE format('CREATE TABLE public.%I PARTITION OF public.audit_log '
                       || 'FOR VALUES FROM (TIMESTAMPTZ %L) TO (TIMESTAMPTZ %L)',
                       v_name, v_start_ts, v_end_ts);
    END IF;
    RETURN v_name;
END;
$$;

CREATE OR REPLACE FUNCTION public.audit_log_ensure_partitions(p_ahead INT DEFAULT 2)
RETURNS INT LANGUAGE plpgsql SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE
    v_created INT := 0;
    v_month DATE;
    v_name TEXT;
    i INT;
BEGIN
    IF p_ahead < 0 THEN
        RAISE EXCEPTION 'audit_log: p_ahead negativo (%); use 0 ou mais', p_ahead;
    END IF;
    FOR i IN 0..p_ahead LOOP
        v_month := (date_trunc('month', now() AT TIME ZONE 'UTC') + (i || ' months')::INTERVAL)::DATE;
        v_name := 'audit_log_' || to_char(v_month, 'YYYY_MM');
        IF to_regclass('public.' || v_name) IS NULL THEN
            PERFORM public.audit_log_create_partition(v_month);
            v_created := v_created + 1;
        END IF;
    END LOOP;
    RETURN v_created;
END;
$$;

REVOKE ALL ON FUNCTION public.audit_log_create_partition(DATE) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.audit_log_create_partition(DATE) TO ziipsan_migrator;
REVOKE ALL ON FUNCTION public.audit_log_ensure_partitions(INT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.audit_log_ensure_partitions(INT) TO ziipsan_migrator;

-- ======================================================================
-- 2. Conversao da tabela (idempotente: se ja particionada, ignora)
-- ======================================================================

DO $$
DECLARE
    r RECORD;
BEGIN
    IF to_regclass('public.audit_log') IS NULL THEN
        RAISE EXCEPTION 'audit_log: tabela public.audit_log inexistente; a core V1 deve preceder a V14';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_partitioned_table WHERE partrelid = 'public.audit_log'::regclass) THEN
        RAISE NOTICE 'audit_log ja particionada; conversao ignorada';
        RETURN;
    END IF;

    ALTER TABLE public.audit_log RENAME TO audit_log_legacy;

    CREATE TABLE public.audit_log (
        id UUID NOT NULL DEFAULT uuidv7(),
        tenant_id UUID NOT NULL,
        entity_type VARCHAR(100) NOT NULL,
        entity_id UUID NOT NULL,
        action VARCHAR(20) NOT NULL,
        user_id UUID,
        correlation_id UUID,
        ip_address VARCHAR(45),
        user_agent TEXT,
        request_id VARCHAR(100),
        old_values JSONB,
        new_values JSONB,
        changed_fields JSONB,
        metadata JSONB DEFAULT '{}',
        success BOOLEAN NOT NULL DEFAULT TRUE,
        error_message TEXT,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ,
        version BIGINT,
        created_by UUID,
        updated_by UUID,
        PRIMARY KEY (created_at, id)
    ) PARTITION BY RANGE (created_at);

    -- Uma particao por mes presente no legado, para a poda continuar
    -- valendo sobre o historico migrado (e nao so sobre o que vem depois).
    FOR r IN SELECT DISTINCT (date_trunc('month', created_at AT TIME ZONE 'UTC'))::DATE AS m
             FROM public.audit_log_legacy LOOP
        PERFORM public.audit_log_create_partition(r.m);
    END LOOP;

    INSERT INTO public.audit_log
        (id, tenant_id, entity_type, entity_id, action, user_id, correlation_id,
         ip_address, user_agent, request_id, old_values, new_values, changed_fields,
         metadata, success, error_message, created_at, updated_at, version,
         created_by, updated_by)
    SELECT id, tenant_id, entity_type, entity_id, action, user_id, correlation_id,
           ip_address, user_agent, request_id, old_values, new_values, changed_fields,
           metadata, success, error_message, created_at, updated_at, version,
           created_by, updated_by
    FROM public.audit_log_legacy;

    DROP TABLE public.audit_log_legacy;
END;
$$;

-- Indices da V1, recriados sobre a tabela particionada (todos nao-unicos,
-- por isso nao precisam incluir a chave de particao; a PK ja a inclui).
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_entity ON public.audit_log (tenant_id, entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_user_time ON public.audit_log (tenant_id, user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_time ON public.audit_log (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_correlation ON public.audit_log (tenant_id, correlation_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON public.audit_log (created_at DESC);

-- RLS da V1, reaplicada (a tabela nova nasce sem policy; sem FORCE, o
-- dono ignoraria a policy — o mesmo motivo da V1).
ALTER TABLE public.audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_log FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS audit_log_isolation ON public.audit_log;
CREATE POLICY audit_log_isolation ON public.audit_log
    USING (tenant_id = public.current_tenant_id())
    WITH CHECK (tenant_id = public.current_tenant_id());

-- A aplicacao continua somente-insercao na trilha (V1): SELECT + INSERT.
GRANT SELECT, INSERT ON public.audit_log TO ziipsan_app;

COMMENT ON TABLE public.audit_log IS
    'P1.7 (core V14; §3 reservava V7, ocupada pela F21): trilha central de auditoria, particionada por mes UTC em created_at, PK (created_at, id), id UUIDv7. Sem particao DEFAULT: mes sem particao falha em vez de esconder a falta.';

-- Mes corrente + seguinte, para a escrita via trigger (NOW()) nunca
-- falhar por particao ausente logo apos a migration.
SELECT public.audit_log_ensure_partitions(1);

-- ======================================================================
-- 3. Retencao com piso (G6; classe c — so amplia)
-- ======================================================================

CREATE TABLE IF NOT EXISTS public.audit_retention_policy (
    tenant_id UUID PRIMARY KEY,
    retention_months INT NOT NULL CHECK (retention_months >= 6),
    cert_retention_years INT CHECK (cert_retention_years IS NULL OR cert_retention_years >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE public.audit_retention_policy IS
    'P1.7: retencao da trilha por tenant. Piso absoluto 6 meses (Marco Civil art. 15); efetivo = GREATEST(retention_months, cert_retention_years * 12). Sem linha: nenhum expurgo. So amplia (trigger).';

CREATE OR REPLACE FUNCTION public.audit_retention_policy_no_decrease()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = pg_catalog, public, pg_temp AS $$
BEGIN
    IF NEW.retention_months < OLD.retention_months THEN
        RAISE EXCEPTION 'audit_log: retencao so amplia (classe c): % -> % meses recusado',
            OLD.retention_months, NEW.retention_months;
    END IF;
    IF COALESCE(NEW.cert_retention_years, 0) < COALESCE(OLD.cert_retention_years, 0) THEN
        RAISE EXCEPTION 'audit_log: retencao de certificacao so amplia (classe c): % -> % anos recusado',
            OLD.cert_retention_years, NEW.cert_retention_years;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_audit_retention_policy_no_decrease ON public.audit_retention_policy;
CREATE TRIGGER trg_audit_retention_policy_no_decrease
    BEFORE UPDATE ON public.audit_retention_policy
    FOR EACH ROW EXECUTE FUNCTION public.audit_retention_policy_no_decrease();

CREATE OR REPLACE FUNCTION public.audit_log_set_retention(
    p_tenant UUID, p_months INT, p_cert_years INT DEFAULT NULL)
RETURNS VOID LANGUAGE plpgsql SET search_path = pg_catalog, public, pg_temp AS $$
BEGIN
    IF p_months < 6 THEN
        RAISE EXCEPTION 'audit_log: retencao minima de 6 meses (Marco Civil art. 15; G6); % recusado', p_months;
    END IF;
    IF p_cert_years IS NOT NULL AND p_cert_years < 0 THEN
        RAISE EXCEPTION 'audit_log: cert_retention_years negativo (%) recusado', p_cert_years;
    END IF;
    INSERT INTO public.audit_retention_policy (tenant_id, retention_months, cert_retention_years)
    VALUES (p_tenant, p_months, p_cert_years)
    ON CONFLICT (tenant_id) DO UPDATE
        SET retention_months = EXCLUDED.retention_months,
            cert_retention_years = EXCLUDED.cert_retention_years,
            updated_at = NOW();
END;
$$;

CREATE OR REPLACE FUNCTION public.audit_log_effective_retention_months(p_tenant UUID)
RETURNS INT LANGUAGE sql STABLE SET search_path = pg_catalog, public, pg_temp AS $$
    SELECT GREATEST(retention_months, COALESCE(cert_retention_years, 0) * 12)
    FROM public.audit_retention_policy WHERE tenant_id = p_tenant;
$$;

CREATE OR REPLACE FUNCTION public.audit_log_purge_expired(p_reference TIMESTAMPTZ DEFAULT now())
RETURNS BIGINT LANGUAGE plpgsql SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE
    r RECORD;
    v_cutoff TIMESTAMPTZ;
    v_n BIGINT;
    v_total BIGINT := 0;
BEGIN
    FOR r IN SELECT tenant_id,
                    GREATEST(retention_months, COALESCE(cert_retention_years, 0) * 12) AS months
             FROM public.audit_retention_policy LOOP
        v_cutoff := p_reference - make_interval(months => r.months);
        -- RLS FORCED: sem contexto, o DELETE nao veria linha nenhuma.
        -- O contexto e local a transacao e limpo ao fim da funcao.
        PERFORM set_config('app.current_tenant', r.tenant_id::TEXT, TRUE);
        DELETE FROM public.audit_log
        WHERE tenant_id = r.tenant_id AND created_at < v_cutoff;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        v_total := v_total + v_n;
    END LOOP;
    PERFORM set_config('app.current_tenant', '', TRUE);
    RETURN v_total;
END;
$$;

REVOKE ALL ON FUNCTION public.audit_log_set_retention(UUID, INT, INT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.audit_log_set_retention(UUID, INT, INT) TO ziipsan_migrator;
REVOKE ALL ON FUNCTION public.audit_log_effective_retention_months(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.audit_log_effective_retention_months(UUID) TO ziipsan_migrator;
REVOKE ALL ON FUNCTION public.audit_log_purge_expired(TIMESTAMPTZ) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.audit_log_purge_expired(TIMESTAMPTZ) TO ziipsan_migrator;

-- ======================================================================
-- 4. Parametros do catalogo (core V3; cert.record_retention_years ja existe)
-- ======================================================================

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('log.audit_retention_months', 'INT', 'c', NULL,
     'PARAMETROS-DO-TENANT §4 (G6); Marco Civil art. 15 (piso 6 meses)',
     NULL,
     'Trilha de negocio (audit_log): meses de retencao; piso 6 meses e piso do maior prazo de G6 que ela comprova; so amplia. Sem valor vigente, nao ha expurgo (conservador).'),
    ('log.export_enabled', 'BOOLEAN', 'd', 'false',
     'Plano v3.0 P1.7; RNF-52',
     NULL,
     'Exportacao continua da trilha do tenant (audit_log e personal_data_access_log) para o SIEM do cliente; desligada por padrao.'),
    ('log.export_format', 'TEXT', 'd', '"JSONL"',
     'Plano v3.0 P1.7; RNF-52',
     NULL,
     'Formato da exportacao para o SIEM: JSONL ou CEF. A exportacao leva so metadados do evento, nunca payloads nem IP (sem dado pessoal em claro).')
ON CONFLICT (definition_key) DO NOTHING;

INSERT INTO public.parameter_legal_limits
    (definition_key, limit_kind, limit_value, norm_source, applies_to,
     above_max_requires_norm, above_max_requires_dual_approval,
     above_max_requires_formal_acceptance, above_max_risk_alert, notes)
SELECT 'log.export_format', 'ALLOWED_VALUES', '["JSONL", "CEF"]', 'Plano v3.0 P1.7', NULL,
       FALSE, FALSE, FALSE, FALSE, 'Formatos aceitos na exportacao para o SIEM.'
WHERE NOT EXISTS (SELECT 1 FROM public.parameter_legal_limits
                  WHERE definition_key = 'log.export_format' AND limit_kind = 'ALLOWED_VALUES');
