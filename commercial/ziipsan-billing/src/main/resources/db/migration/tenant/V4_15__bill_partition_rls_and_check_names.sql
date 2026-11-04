-- ============================================================================
-- P2-A (integracao) — RLS das particoes de bills/bill_items + nomes dos CHECKs
-- da V4_6 perdidos na recriacao da V4_14 (plano v3.0 §3, billing V4_15).
--
-- 1. RLS DAS PARTICOES. A V4_14 particionou bills e bill_items e chamou
-- register_tenant_table so nos pais. O Postgres NAO propaga RLS/FORCE/policy
-- para particoes (ao contrario do trigger de auditoria, que e clonado para
-- as particoes automaticamente — por isso o FreshInstallTest so acusa RLS e
-- policy nas particoes, nunca o trigger). Acesso direto a uma particao
-- passaria sem isolamento de tenant: falha real de isolamento, nao detalhe
-- de teste. Esta migration (a) aplica RLS FORCADA + policy <part>_isolation
-- (mesma forma do register_tenant_table) em TODAS as particoes existentes e
-- (b) redefine bills_create_partition para que toda particao futura ja
-- nasca isolada (a funcao passa a blindar tambem a particao pre-existente,
-- caso um ensure futuro a encontre sem RLS). GRANTs nao precisam de
-- backfill: GRANT no pai particionado ja alcanca as particoes.
--
-- 2. NOMES DOS CHECKs DA V4_6. A V4_6 nomeou dois CHECKs de bills
-- (chk_bills_tax_treatment, chk_bills_total_with_withholding_tax) e a
-- recriacao da V4_14 os perdeu (nomes automaticos bills_tax_treatment_check
-- e bills_check1 no PG 17.11, verificados empiricamente). O nome e contrato
-- (BillingP1MigrationTest assertiona chk_bills_tax_treatment). O RENAME no
-- pai particionado propaga para as particoes (verificado no PG 17.11).
-- Os DOs com guarda existem porque RENAME CONSTRAINT nao tem IF EXISTS;
-- se um nome futuro divergir, a migration passa e o teste acusa — nunca
-- silenciosamente errado.
--
-- NO SEEDED DATA. Nenhuma linha de negocio e criada ou alterada.
-- ============================================================================

-- ---------------------------------------------------------------- 1. CHECKs
DO $$
DECLARE
    v_bills OID;
BEGIN
    SELECT c.oid INTO v_bills
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema() AND c.relname = 'bills';
    IF v_bills IS NOT NULL THEN
        IF EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = v_bills AND conname = 'bills_tax_treatment_check')
           AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = v_bills AND conname = 'chk_bills_tax_treatment') THEN
            EXECUTE 'ALTER TABLE bills RENAME CONSTRAINT bills_tax_treatment_check TO chk_bills_tax_treatment';
        END IF;
        IF EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = v_bills AND conname = 'bills_check1')
           AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = v_bills AND conname = 'chk_bills_total_with_withholding_tax') THEN
            EXECUTE 'ALTER TABLE bills RENAME CONSTRAINT bills_check1 TO chk_bills_total_with_withholding_tax';
        END IF;
    END IF;
END $$;

-- ------------------------------------------------------- 2. backfill das RLS
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
          AND p.relname IN ('bills', 'bill_items')
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
-- Funcao nova da V4_15; o backfill acima usa o mesmo criterio inline para
-- nao depender da ordem de criacao dentro da migration. Definida ANTES de
-- bills_create_partition porque o corpo plpgsql e validado na criacao.
CREATE OR REPLACE FUNCTION bills_secure_partition(p_schema TEXT, p_part TEXT)
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


-- ------------------------------------------- 3. funcao futura ja nasce isolada
-- Mesma assinatura e corpo da V4_14, acrescida da blindagem (RLS FORCADA +
-- policy) apos cada CREATE TABLE — e reaplicada quando a particao ja existe,
-- para o ensure futuro corrigir qualquer particao legada sem RLS.
CREATE OR REPLACE FUNCTION bills_create_partition(p_month DATE)
RETURNS TEXT LANGUAGE plpgsql AS $$
DECLARE
    v_schema TEXT := current_schema();
    v_start DATE := date_trunc('month', p_month)::DATE;
    v_end DATE := (v_start + INTERVAL '1 month')::DATE;
    v_name TEXT := 'bills_' || to_char(v_start, 'YYYY_MM');
    v_item_name TEXT := 'bill_items_' || to_char(v_start, 'YYYY_MM');
BEGIN
    IF to_regclass(v_schema || '.' || v_name) IS NULL THEN
        EXECUTE format('CREATE TABLE %I.%I PARTITION OF %I.bills '
                       || 'FOR VALUES FROM (%L) TO (%L)',
                       v_schema, v_name, v_schema, v_start, v_end);
    END IF;
    PERFORM bills_secure_partition(v_schema, v_name);
    IF to_regclass(v_schema || '.' || v_item_name) IS NULL THEN
        EXECUTE format('CREATE TABLE %I.%I PARTITION OF %I.bill_items '
                       || 'FOR VALUES FROM (%L) TO (%L)',
                       v_schema, v_item_name, v_schema, v_start, v_end);
    END IF;
    PERFORM bills_secure_partition(v_schema, v_item_name);
    RETURN v_name;
END;
$$;

REVOKE ALL ON FUNCTION bills_create_partition(DATE) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION bills_create_partition(DATE) TO ziipsan_migrator;
REVOKE ALL ON FUNCTION bills_secure_partition(TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION bills_secure_partition(TEXT, TEXT) TO ziipsan_migrator;
