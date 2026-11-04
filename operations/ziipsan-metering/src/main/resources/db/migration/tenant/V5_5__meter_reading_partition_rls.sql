-- ============================================================================
-- P2-A (integracao) — RLS das particoes de meter_readings (plano v3.0 §3,
-- metering V5_5). Espelho da billing V4_15 para o metering.
--
-- A V5_4 particionou meter_readings e chamou register_tenant_table so no pai.
-- O Postgres NAO propaga RLS/FORCE/policy para particoes (ao contrario do
-- trigger de auditoria, que e clonado para as particoes automaticamente —
-- por isso o FreshInstallTest so acusa RLS e policy nas particoes, nunca o
-- trigger). Acesso direto a uma particao passaria sem isolamento de tenant:
-- falha real de isolamento, nao detalhe de teste. Esta migration (a) aplica
-- RLS FORCADA + policy <part>_isolation (mesma forma do
-- register_tenant_table) em TODAS as particoes existentes e (b) redefine
-- meter_readings_create_partition para que toda particao futura ja nasca
-- isolada (a funcao passa a blindar tambem a particao pre-existente, caso
-- um ensure futuro a encontre sem RLS). GRANTs nao precisam de backfill:
-- GRANT no pai particionado ja alcanca as particoes.
--
-- NO SEEDED DATA. Nenhuma linha de negocio e criada ou alterada.
-- ============================================================================

-- ------------------------------------------------------- 1. backfill das RLS
DO $$
DECLARE
    r RECORD;
    v_policy TEXT;
BEGIN
    FOR r IN
        SELECT c.relname AS part
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_inherits i ON i.inhrelid = c.oid
        JOIN pg_class p ON p.oid = i.inhparent
        JOIN pg_namespace pn ON pn.oid = p.relnamespace
        WHERE n.nspname = current_schema()
          AND pn.nspname = current_schema()
          AND p.relname = 'meter_readings'
        ORDER BY c.relname
    LOOP
        EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', current_schema(), r.part);
        EXECUTE format('ALTER TABLE %I.%I FORCE ROW LEVEL SECURITY', current_schema(), r.part);
        v_policy := r.part || '_isolation';
        IF NOT EXISTS (SELECT 1 FROM pg_policies
                       WHERE schemaname = current_schema() AND tablename = r.part AND policyname = v_policy) THEN
            EXECUTE format('CREATE POLICY %I ON %I.%I USING (tenant_id = current_tenant_id()) '
                           || 'WITH CHECK (tenant_id = current_tenant_id())',
                           v_policy, current_schema(), r.part);
        END IF;
    END LOOP;
END $$;

-- Blindagem de uma particao (idempotente): RLS FORCADA + policy _isolation.
-- Funcao nova da V5_5; o backfill acima usa o mesmo criterio inline para
-- nao depender da ordem de criacao dentro da migration. Definida ANTES de
-- meter_readings_create_partition porque o corpo plpgsql e validado na
-- criacao.
CREATE OR REPLACE FUNCTION meter_readings_secure_partition(p_schema TEXT, p_part TEXT)
RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE
    v_policy TEXT := p_part || '_isolation';
BEGIN
    EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', p_schema, p_part);
    EXECUTE format('ALTER TABLE %I.%I FORCE ROW LEVEL SECURITY', p_schema, p_part);
    IF NOT EXISTS (SELECT 1 FROM pg_policies
                   WHERE schemaname = p_schema AND tablename = p_part AND policyname = v_policy) THEN
        EXECUTE format('CREATE POLICY %I ON %I.%I USING (tenant_id = current_tenant_id()) '
                       || 'WITH CHECK (tenant_id = current_tenant_id())',
                       v_policy, p_schema, p_part);
    END IF;
END;
$$;

-- ------------------------------------------- 2. funcao futura ja nasce isolada
-- Mesma assinatura e corpo da V5_4, acrescida da blindagem (RLS FORCADA +
-- policy) apos o CREATE TABLE — e reaplicada quando a particao ja existe,
-- para o ensure futuro corrigir qualquer particao legada sem RLS.
CREATE OR REPLACE FUNCTION meter_readings_create_partition(p_month DATE)
RETURNS TEXT LANGUAGE plpgsql AS $$
DECLARE
    v_schema TEXT := current_schema();
    v_start DATE := date_trunc('month', p_month)::DATE;
    v_end DATE := (v_start + INTERVAL '1 month')::DATE;
    v_name TEXT := 'meter_readings_' || to_char(v_start, 'YYYY_MM');
BEGIN
    IF to_regclass(v_schema || '.' || v_name) IS NULL THEN
        EXECUTE format('CREATE TABLE %I.%I PARTITION OF %I.meter_readings '
                       || 'FOR VALUES FROM (%L) TO (%L)',
                       v_schema, v_name, v_schema, v_start, v_end);
    END IF;
    PERFORM meter_readings_secure_partition(v_schema, v_name);
    RETURN v_name;
END;
$$;

REVOKE ALL ON FUNCTION meter_readings_create_partition(DATE) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION meter_readings_create_partition(DATE) TO ziipsan_migrator;
REVOKE ALL ON FUNCTION meter_readings_secure_partition(TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION meter_readings_secure_partition(TEXT, TEXT) TO ziipsan_migrator;
