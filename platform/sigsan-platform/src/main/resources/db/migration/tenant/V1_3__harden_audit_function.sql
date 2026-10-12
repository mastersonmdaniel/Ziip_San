-- Endurecimento da funcao de auditoria e do helper de RLS
-- Version: 1.3.0
--
-- 1. audit_trigger_function e SECURITY DEFINER (roda como sigsan_migrator, dono das tabelas) e nao
--    fixava search_path: quem controlasse o search_path da sessao poderia fazer o INSERT de
--    auditoria resolver nomes num schema seu. Agora o search_path e fixo, com pg_temp por ultimo
--    (explicito, senao o PostgreSQL o poe primeiro), e as referencias sao qualificadas.
-- 2. created_by fazia current_setting('app.current_user', TRUE)::UUID. Depois de um RESET ou de um
--    set_config com '' a GUC devolve '' (e nao NULL), e o cast falhava: toda gravacao auditada
--    quebraria assim que a conexao passasse a limpar o contexto ao voltar para o pool (E3).
-- 3. register_tenant_table passa a criar a policy com public.current_tenant_id() qualificada.
--
-- O corpo e o mesmo de V1_1 nos demais pontos. A redacao de dado pessoal vem numa versao seguinte.

CREATE OR REPLACE FUNCTION audit_trigger_function() RETURNS TRIGGER AS $$
DECLARE
    old_row JSONB;
    new_row JSONB;
    changed_fields JSONB;
    v_user UUID := NULLIF(current_setting('app.current_user', TRUE), '')::UUID;
BEGIN
    -- to_jsonb(NEW) serializa a linha inteira; as colunas de credencial saem antes de gravar.
    IF TG_OP = 'INSERT' THEN
        new_row := to_jsonb(NEW)
            - 'password_hash' - 'mfa_secret' - 'password' - 'secret'
            - 'token' - 'refresh_token' - 'api_key' - 'private_key';
        INSERT INTO public.audit_log (tenant_id, entity_type, entity_id, action, new_values, created_at, created_by)
        VALUES (public.current_tenant_id(), TG_TABLE_NAME, NEW.id, 'CREATE', new_row, NOW(), v_user);
        RETURN NEW;
    ELSIF TG_OP = 'UPDATE' THEN
        old_row := to_jsonb(OLD)
            - 'password_hash' - 'mfa_secret' - 'password' - 'secret'
            - 'token' - 'refresh_token' - 'api_key' - 'private_key';
        new_row := to_jsonb(NEW)
            - 'password_hash' - 'mfa_secret' - 'password' - 'secret'
            - 'token' - 'refresh_token' - 'api_key' - 'private_key';
        changed_fields := (
            SELECT jsonb_object_agg(key, new_row[key])
            FROM jsonb_object_keys(new_row) AS key
            WHERE NOT (old_row[key] IS NOT DISTINCT FROM new_row[key])
        );
        INSERT INTO public.audit_log (tenant_id, entity_type, entity_id, action, old_values, new_values, changed_fields, created_at, created_by)
        VALUES (public.current_tenant_id(), TG_TABLE_NAME, NEW.id, 'UPDATE', old_row, new_row, changed_fields, NOW(), v_user);
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        old_row := to_jsonb(OLD)
            - 'password_hash' - 'mfa_secret' - 'password' - 'secret'
            - 'token' - 'refresh_token' - 'api_key' - 'private_key';
        INSERT INTO public.audit_log (tenant_id, entity_type, entity_id, action, old_values, created_at, created_by)
        VALUES (public.current_tenant_id(), TG_TABLE_NAME, OLD.id, 'DELETE', old_row, NOW(), v_user);
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp;

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
            'CREATE POLICY %I ON %s USING (tenant_id = public.current_tenant_id()) '
            || 'WITH CHECK (tenant_id = public.current_tenant_id())', v_policy, p_table);
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
