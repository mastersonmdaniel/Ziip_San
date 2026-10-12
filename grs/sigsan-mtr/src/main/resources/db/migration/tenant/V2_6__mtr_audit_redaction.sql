-- MTR: colunas com dado pessoal que a auditoria redige (ver core V2 e platform V1_4)
-- Version: 2.6.0
--
-- Gerador pode ser pessoa física (cnpj_cpf com CPF, razão social = nome); o MTR guarda o CPF do
-- motorista; o transportador guarda a lista de motoristas (nome, CPF, CNH). A cifra dessas colunas
-- na própria tabela vem numa migration seguinte; aqui, só a trilha de auditoria.

INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('waste_generator', 'cnpj_cpf',               'REDACT', 'CPF quando o gerador é pessoa física'),
    ('waste_generator', 'corporate_name',         'REDACT', 'nome quando o gerador é pessoa física'),
    ('waste_generator', 'trade_name',             'REDACT', 'nome quando o gerador é pessoa física'),
    ('waste_generator', 'address',                'REDACT', 'endereço quando o gerador é pessoa física'),
    ('waste_generator', 'responsible_technician', 'REDACT', 'nome e registro do responsável técnico'),
    ('waste_transporter', 'drivers',              'OMIT',   'nome, CPF e CNH dos motoristas'),
    ('waste_mtr', 'driver_cpf',                   'REDACT', 'CPF do motorista'),
    ('waste_mtr', 'generator_signature',          'REDACT', 'assinatura: identifica o signatário'),
    ('waste_mtr', 'transporter_signature',        'REDACT', 'assinatura: identifica o signatário'),
    ('waste_mtr', 'destination_signature',        'REDACT', 'assinatura: identifica o signatário'),
    ('waste_cdf', 'technician_name',              'REDACT', 'nome do responsável técnico'),
    ('waste_cdf', 'technician_crea',              'REDACT', 'registro profissional do responsável técnico'),
    ('waste_cdf', 'pdf_document',                 'OMIT',   'documento binário com dados das partes')
ON CONFLICT (table_name, column_name) DO NOTHING;
