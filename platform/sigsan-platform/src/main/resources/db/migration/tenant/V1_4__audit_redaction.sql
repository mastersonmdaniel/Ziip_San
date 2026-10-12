-- Auditoria sem dado pessoal (achado C4 do PDCA de 12/10/2026)
-- Version: 1.4.0
--
-- O trigger passa a aplicar public.audit_redact() (core V2) em old_values, new_values e
-- changed_fields. changed_fields e calculado sobre as linhas CRUAS e so depois redigido: redigir
-- antes faria um nome alterado sumir da trilha, porque "[redacted]" = "[redacted]".
--
-- Regras das tabelas desta camada (platform). Os demais modulos declaram as suas.

CREATE OR REPLACE FUNCTION audit_trigger_function() RETURNS TRIGGER AS $$
DECLARE
    old_row JSONB;
    new_row JSONB;
    changed JSONB;
    v_user UUID := NULLIF(current_setting('app.current_user', TRUE), '')::UUID;
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        old_row := to_jsonb(OLD)
            - 'password_hash' - 'mfa_secret' - 'password' - 'secret'
            - 'token' - 'refresh_token' - 'api_key' - 'private_key';
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        new_row := to_jsonb(NEW)
            - 'password_hash' - 'mfa_secret' - 'password' - 'secret'
            - 'token' - 'refresh_token' - 'api_key' - 'private_key';
    END IF;

    IF TG_OP = 'INSERT' THEN
        INSERT INTO public.audit_log (tenant_id, entity_type, entity_id, action, new_values, created_at, created_by)
        VALUES (public.current_tenant_id(), TG_TABLE_NAME, NEW.id, 'CREATE',
                public.audit_redact(TG_TABLE_NAME, new_row), NOW(), v_user);
        RETURN NEW;
    ELSIF TG_OP = 'UPDATE' THEN
        changed := (
            SELECT jsonb_object_agg(key, new_row[key])
            FROM jsonb_object_keys(new_row) AS key
            WHERE NOT (old_row[key] IS NOT DISTINCT FROM new_row[key])
        );
        INSERT INTO public.audit_log (tenant_id, entity_type, entity_id, action, old_values, new_values, changed_fields, created_at, created_by)
        VALUES (public.current_tenant_id(), TG_TABLE_NAME, NEW.id, 'UPDATE',
                public.audit_redact(TG_TABLE_NAME, old_row),
                public.audit_redact(TG_TABLE_NAME, new_row),
                public.audit_redact(TG_TABLE_NAME, changed, TRUE),
                NOW(), v_user);
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        INSERT INTO public.audit_log (tenant_id, entity_type, entity_id, action, old_values, created_at, created_by)
        VALUES (public.current_tenant_id(), TG_TABLE_NAME, OLD.id, 'DELETE',
                public.audit_redact(TG_TABLE_NAME, old_row), NOW(), v_user);
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp;

INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('users', 'username',  'REDACT', 'identificador de login, costuma ser nome ou e-mail'),
    ('users', 'email',     'REDACT', 'e-mail do usuário'),
    ('users', 'full_name', 'REDACT', 'nome do usuário'),
    ('users', 'phone',     'REDACT', 'telefone do usuário'),
    ('addresses', 'cep',         'REDACT', 'endereço de pessoa ou imóvel vinculado a pessoa'),
    ('addresses', 'street',      'REDACT', 'endereço de pessoa ou imóvel vinculado a pessoa'),
    ('addresses', 'number',      'REDACT', 'endereço de pessoa ou imóvel vinculado a pessoa'),
    ('addresses', 'complement',  'REDACT', 'endereço de pessoa ou imóvel vinculado a pessoa'),
    ('addresses', 'coordinates', 'OMIT',   'localização de residência'),
    ('documents', 'file_name',   'REDACT', 'nome de arquivo costuma conter nome ou documento da pessoa'),
    ('service_orders', 'description',         'REDACT', 'texto livre: pode conter dado pessoal'),
    ('service_orders', 'resolution',          'REDACT', 'texto livre: pode conter dado pessoal'),
    ('service_orders', 'cancellation_reason', 'REDACT', 'texto livre: pode conter dado pessoal'),
    ('service_orders', 'form_data',           'REDACT', 'formulário de campo: pode conter dado pessoal'),
    ('service_orders', 'photos',              'REDACT', 'referência a fotos de imóvel e pessoas'),
    ('service_orders', 'gps_track',           'OMIT',   'trajeto do trabalhador de campo')
ON CONFLICT (table_name, column_name) DO NOTHING;
