-- Cadastro: colunas com dado pessoal que a auditoria redige (ver core V2 e platform V1_4)
-- Version: 3.2.0

INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('customers', 'name',                'REDACT', 'nome do cliente (pessoa física ou representante)'),
    ('customers', 'trade_name',          'REDACT', 'nome fantasia, pode ser o nome da pessoa'),
    ('customers', 'birth_date',          'REDACT', 'data de nascimento'),
    ('customers', 'document_masked',     'REDACT', 'máscara do CPF/CNPJ ainda identifica a pessoa'),
    ('customers', 'document_ciphertext', 'OMIT',   'CPF/CNPJ cifrado: sem uso na trilha'),
    ('customers', 'document_hash',       'OMIT',   'índice cego do documento: permite ligar registros'),
    ('customers', 'deleted_reason',      'REDACT', 'texto livre: pode conter dado pessoal'),
    ('customer_contacts', 'value',       'REDACT', 'e-mail ou telefone do cliente'),
    ('customer_contacts', 'label',       'REDACT', 'rótulo livre do contato, pode conter nome'),
    ('unit_customers', 'end_reason',     'REDACT', 'texto livre: pode conter dado pessoal'),
    ('unit_status_history', 'reason',    'REDACT', 'texto livre: pode conter dado pessoal'),
    ('consumer_units', 'notes',          'REDACT', 'texto livre sobre o imóvel: pode conter dado do morador')
ON CONFLICT (table_name, column_name) DO NOTHING;
