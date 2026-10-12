-- Tenant Schema Helper
-- Version: 1.2.0
-- Description: register_tenant_table() aplica, numa tabela de tenant, as garantias que toda
-- tabela de negocio precisa ter. As migrations dos modulos chamam esta funcao no fim de cada
-- CREATE TABLE em vez de repetir (e esquecer) cada passo.
--
-- Por que uma funcao e nao copia-e-cola: antes, cada modulo repetia ENABLE RLS e CREATE POLICY
-- a mao. Nenhum usava FORCE (o dono das tabelas, sigsan_migrator, ignora RLS sem ele), um
-- esquecia o trigger de auditoria e nenhum criava as colunas version e tenant_schema que
-- BaseEntity e TenantAwareEntity mapeiam. O build passava e o provisionamento quebrava.
--
-- Parametros:
--   p_table   tabela (regclass) com a coluna tenant_id.
--   p_entity  true (padrao) se a tabela e mapeada por entidade JPA que herda de
--             BaseEntity/TenantAwareEntity: acrescenta version e tenant_schema.
--   p_audit   true (padrao) liga o trigger de auditoria. Desligar so para series temporais
--             de alto volume e reproduziveis (ex.: resultados de simulacao), onde auditar cada
--             linha em JSONB duplicaria o armazenamento sem valor.
--
-- RLS e policy de tenant sao sempre aplicadas, independentemente dos parametros.

CREATE OR REPLACE FUNCTION register_tenant_table(
    p_table regclass,
    p_entity boolean DEFAULT true,
    p_audit boolean DEFAULT true
) RETURNS VOID AS $$
DECLARE
    v_name TEXT := (SELECT relname FROM pg_class WHERE oid = p_table);
    v_policy TEXT;
BEGIN
    IF p_entity THEN
        EXECUTE format('ALTER TABLE %s ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0', p_table);
        EXECUTE format('ALTER TABLE %s ADD COLUMN IF NOT EXISTS tenant_schema VARCHAR(100)', p_table);
    END IF;

    -- RLS por tenant. FORCE e obrigatorio: sem ele o dono da tabela ignora a policy.
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', p_table);
    EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', p_table);

    v_policy := v_name || '_isolation';
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = current_schema() AND tablename = v_name AND policyname = v_policy
    ) THEN
        EXECUTE format(
            'CREATE POLICY %I ON %s USING (tenant_id = current_tenant_id()) '
            || 'WITH CHECK (tenant_id = current_tenant_id())', v_policy, p_table);
    END IF;

    EXECUTE format('DROP TRIGGER IF EXISTS audit_trigger ON %s', p_table);
    IF p_audit THEN
        -- O trigger remove colunas sensiveis (senha, token...) antes de gravar.
        EXECUTE format(
            'CREATE TRIGGER audit_trigger AFTER INSERT OR UPDATE OR DELETE ON %s '
            || 'FOR EACH ROW EXECUTE FUNCTION audit_trigger_function()', p_table);
    END IF;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION register_tenant_table(regclass, boolean, boolean) IS
    'Aplica colunas de entidade, RLS forcada, policy de tenant e (opcional) trigger de auditoria a uma tabela de tenant';
