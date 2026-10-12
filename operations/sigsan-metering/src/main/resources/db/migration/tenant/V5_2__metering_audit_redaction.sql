-- Medição: colunas com dado pessoal que a auditoria redige (ver core V2 e platform V1_4)
-- Version: 5.2.0

INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('meters', 'notes', 'REDACT', 'texto livre do leiturista/técnico: pode conter dado do morador')
ON CONFLICT (table_name, column_name) DO NOTHING;
