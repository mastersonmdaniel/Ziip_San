-- Perdas: colunas com dado pessoal que a auditoria redige (ver core V2 e platform V1_4)
-- Version: 2.10.0
--
-- Vazamento detectado em ramal ou imóvel: o endereço e as fotos identificam o morador.

INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('leak_detection', 'address', 'REDACT', 'endereço do imóvel onde está o vazamento'),
    ('leak_detection', 'photos',  'REDACT', 'fotos podem mostrar imóvel e pessoas'),
    ('leak_detection', 'notes',   'REDACT', 'texto livre: pode conter dado pessoal')
ON CONFLICT (table_name, column_name) DO NOTHING;
