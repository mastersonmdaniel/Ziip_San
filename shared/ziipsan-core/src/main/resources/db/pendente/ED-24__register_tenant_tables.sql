-- ED-24 — Registro das tabelas de tenant (CORRECAO RG LOTE 1, item G2).
--
-- ORIGEM: revisao geral 2026-11-08, frente 3 (integracao/migrations),
-- achado G2, confirmado no consolidado: o SQL de origem
-- ED-24__fraud_inspection.sql cria 3 tabelas com tenant_id
-- (fraud_records, infraction_notices, inspection_criterion_hits) e
-- NENHUMA chamada register_tenant_table — dominio de fiscalizacao/
-- fraude, dos mais sensiveis a auditoria, fora da trilha.
--
-- CONTEUDO: SOMENTE os registros das 3 tabelas da ED-24 e a
-- classificacao/redacao das colunas com dado pessoal delas (padrao
-- V4_2/V8_1/V9_1: a classificacao e a redacao nascem junto do
-- registro). Nenhuma tabela e criada aqui.
--
-- ORDEM OBRIGATORIA NA INTEGRACAO: numerar JUNTO E DEPOIS do
-- ED-24__fraud_inspection.sql, na sequencia global da P3.12 (V100+,
-- atualizando migration-policy/published-tenant-migrations.txt).
-- Depende de as tabelas ja existirem, de register_tenant_table
-- (V1_2, core) e das tabelas public.data_classification e
-- public.audit_redaction_rule (core). NUMERACAO PENDENTE.

-- ========================================================================
-- ISOLAMENTO E AUDITORIA (padrao register_tenant_table da core)
-- ========================================================================

SELECT register_tenant_table('fraud_records');
SELECT register_tenant_table('infraction_notices');
SELECT register_tenant_table('inspection_criterion_hits');

-- ========================================================================
-- CLASSIFICACAO E REDACAO (P0.11) — vocabulario dos precedentes:
-- classes ID/CT/LOC/FIN/SOC/SENS (catalogo public.data_classification,
-- V1_6); modos REDACT/OMIT (public.audit_redaction_rule).
-- Texto livre que pode conter dado pessoal do titular: REDACT + ID
-- (precedentes service_order_notes.body na V9_1 e meters.notes na
-- V5_2). Fotos que podem mostrar pessoas: SENS + REDACT (catalogo
-- P0.11; modo REDACT da prova sensivel na V4_4 — a trilha marca a
-- mudanca, nunca o conteudo).
-- ========================================================================

INSERT INTO public.data_classification (table_name, column_name, class, reason) VALUES
    ('fraud_records', 'description', 'ID', 'descricao livre da ocorrencia de fraude: pode conter dado pessoal do titular da unidade'),
    ('fraud_records', 'photo_document_ids', 'SENS', 'fotos da fiscalizacao podem mostrar pessoas'),
    ('inspection_criterion_hits', 'evidence_summary', 'ID', 'resumo livre da evidencia: pode conter dado pessoal do titular da unidade')
ON CONFLICT (table_name, column_name) DO NOTHING;

INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('fraud_records', 'description', 'REDACT', 'free text: may contain personal data of the unit holder'),
    ('fraud_records', 'photo_document_ids', 'REDACT', 'fotos da fiscalizacao podem mostrar pessoas: conteudo nunca em claro no audit_log'),
    ('inspection_criterion_hits', 'evidence_summary', 'REDACT', 'free text: may contain personal data of the unit holder')
ON CONFLICT (table_name, column_name) DO NOTHING;
