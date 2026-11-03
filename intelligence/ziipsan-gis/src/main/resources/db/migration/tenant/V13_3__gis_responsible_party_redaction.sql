-- GIS: nome do profissional responsavel e dado pessoal (P1-A, integracao).
-- A P1.26 criou gis_responsible_party sem regra; a propria V13_1 documenta "Nome de
-- profissional" (registro CREA/CAU/CFT) — identifica a pessoa. Ganha regra aqui, no mesmo
-- padrao da V3_2 (customers). A classificacao vai junto (ver V4_7 do fiscal: tenant
-- migrado nao re-roda a repetivel R__data_classification sem mudanca de checksum).
-- Version: 13.3.0

INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('gis_responsible_party', 'name', 'REDACT', 'nome do profissional responsável (registro CREA/CAU/CFT)')
ON CONFLICT (table_name, column_name) DO NOTHING;

INSERT INTO public.data_classification (table_name, column_name, class, reason) VALUES
    ('gis_responsible_party', 'name', 'ID', 'nome do profissional responsável (registro CREA/CAU/CFT)')
ON CONFLICT (table_name, column_name) DO NOTHING;
