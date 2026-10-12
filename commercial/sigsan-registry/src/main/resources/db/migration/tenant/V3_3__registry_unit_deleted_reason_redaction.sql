-- Cadastro: motivo da exclusao logica do imovel tambem e redigido na auditoria
-- Version: 3.3.0
--
-- A API do cadastro (E5) passou a gravar consumer_units.deleted_reason a partir do texto digitado pelo
-- operador. E texto livre, como customers.deleted_reason (redigido em V3_2): pode conter nome ou outro
-- dado do morador. O nome da coluna nao casa com o padrao do AuditRedactionCoverageTest, por isso a
-- regra entra aqui explicitamente.

INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('consumer_units', 'deleted_reason', 'REDACT', 'texto livre: pode conter dado pessoal do morador')
ON CONFLICT (table_name, column_name) DO NOTHING;
