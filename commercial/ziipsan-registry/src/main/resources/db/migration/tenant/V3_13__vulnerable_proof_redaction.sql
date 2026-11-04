-- V3_13 — camada C (integracao): redacao da referencia ao documento
-- comprobatorio de pessoa vulneravel.
--
-- A coluna proof_document_id (unit_vulnerable_persons, V3_12) casa com o
-- padrao de dado pessoal do PersonalDataColumns (.*document.*) e a tabela
-- tem trigger de auditoria: sem regra, o build quebra (AuditRedactionCoverageTest
-- e DataClassificationCoverageTest). A classificacao deriva daqui pela
-- repetivel R__data_classification (classe ID: e uma referencia, nao o
-- conteudo — o documento em si vive no pipeline da P1.20, com classificacao
-- propria). O motivo de saude e o consentimento da pessoa vulneravel ja sao
-- tratados pela V3_12; so a referencia ao documento ficou sem regra.
INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('unit_vulnerable_persons', 'proof_document_id', 'REDACT',
     'referência ao documento comprobatório de pessoa vulnerável (dado sensível, LGPD art. 11)')
ON CONFLICT (table_name, column_name) DO NOTHING;
