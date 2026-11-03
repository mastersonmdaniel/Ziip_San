-- Fiscal: colunas com dado pessoal do emitente que a auditoria redige (P1-A, integracao).
-- A P1.8 criou fiscal_issuers sem regras; os testes de cobertura (AuditRedactionCoverageTest
-- e DataClassificationCoverageTest) recusam coluna nova que casa com o padrao sem regra ou
-- justificativa. document_kind e NOT_PERSONAL (tipo, nao valor, em PersonalDataColumns);
-- name, document_masked e document_hash sao dado pessoal e ganham regra aqui, no mesmo
-- padrao da V3_2 (customers). A classificacao vai junto: o R__data_classification deriva
-- das regras em provisionamento novo, mas tenant migrado nao re-roda a repetivel sem
-- mudanca de checksum — e o cabecalho do R__ permite classificar "na propria migration".
-- Versao: V4_7 porque a V4_6 ja e do billing na historia fundida de tenant (V4_6__bill_total).
-- Version: 4.7.0

INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('fiscal_issuers', 'name',            'REDACT', 'nome do emitente (quando CPF, identifica a pessoa)'),
    ('fiscal_issuers', 'document_masked', 'REDACT', 'máscara do CPF/CNPJ ainda identifica a pessoa'),
    ('fiscal_issuers', 'document_hash',   'OMIT',   'índice cego do documento: permite ligar registros')
ON CONFLICT (table_name, column_name) DO NOTHING;

INSERT INTO public.data_classification (table_name, column_name, class, reason) VALUES
    ('fiscal_issuers', 'name',            'ID', 'nome do emitente (quando CPF, identifica a pessoa)'),
    ('fiscal_issuers', 'document_masked', 'ID', 'máscara do CPF/CNPJ ainda identifica a pessoa'),
    ('fiscal_issuers', 'document_hash',   'ID', 'índice cego do documento: permite ligar registros')
ON CONFLICT (table_name, column_name) DO NOTHING;
