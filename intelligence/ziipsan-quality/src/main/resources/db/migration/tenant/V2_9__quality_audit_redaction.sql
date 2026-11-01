-- Qualidade da água: colunas com dado pessoal que a auditoria redige (ver core V2 e platform V1_4)
-- Version: 2.9.0

INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('quality_sample', 'collector_name', 'REDACT', 'nome do coletor (empregado)')
ON CONFLICT (table_name, column_name) DO NOTHING;
