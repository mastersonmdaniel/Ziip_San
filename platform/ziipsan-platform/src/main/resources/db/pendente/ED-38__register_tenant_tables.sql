-- ED-38 — Registro das tabelas de tenant (CORRECAO RG LOTE 1, item G2).
--
-- ORIGEM: a ED-38 nao faz parte dos 27 do achado G2 original (6
-- pacotes), mas tem o MESMO defeito, ja nomeado na propria revisao
-- (REVISAO-ED-38 §4: "Tabelas novas tem tenant_id, mas o SQL nao
-- declara RLS/classificacao — conferir na numeracao/integracao junto
-- as demais migrations pendente/"). A revisao remete expressamente
-- a conferencia para junto das demais pendentes — nao ha delegacao
-- a outra rotina (diferente da ED-67, ver RELATORIO-G2). Por isso a
-- ED-38 entra NESTE mesmo movimento: +2 tabelas.
--
-- CONTEUDO: SOMENTE os registros de portal_authentic_documents e
-- portal_anonymous_reports e a classificacao/redacao das colunas
-- com dado pessoal delas. Nenhuma tabela e criada aqui. (O item (3)
-- do SQL de origem — cadastro de chaves em public.parameter_
-- definitions, migration core — nao e tabela de tenant e nao entra
-- neste arquivo.)
--
-- ORDEM OBRIGATORIA NA INTEGRACAO: numerar JUNTO E DEPOIS do
-- ED-38__portal_selfcare.sql, na sequencia global da P3.12 (V100+,
-- atualizando migration-policy/published-tenant-migrations.txt).
-- Depende de as tabelas ja existirem, de register_tenant_table
-- (V1_2, core) e das tabelas public.data_classification e
-- public.audit_redaction_rule (core). NUMERACAO PENDENTE.

-- ========================================================================
-- ISOLAMENTO E AUDITORIA (padrao register_tenant_table da core)
-- ========================================================================

SELECT register_tenant_table('portal_authentic_documents');
SELECT register_tenant_table('portal_anonymous_reports');

-- ========================================================================
-- CLASSIFICACAO E REDACAO (P0.11) — classes ID/CT/LOC/FIN/SOC/SENS e
-- modos REDACT/OMIT, vocabulario dos precedentes (V1_6, V4_2, V9_1).
-- contact: contato do relator anonimo (e-mail ou telefone) — classe
-- CT do catalogo P0.11, modo REDACT (precedente requester_contact,
-- V8_1). photo_ref: referencia da foto do relato — fotos podem
-- mostrar pessoas: SENS + REDACT (catalogo P0.11; modo da prova
-- sensivel na V4_4). portal_authentic_documents nao ganha linha:
-- guarda numero/codigo verificador de documento emitido (metadado
-- de autenticidade), sem coluna de dado pessoal nos padroes dos
-- precedentes.
-- ========================================================================

INSERT INTO public.data_classification (table_name, column_name, class, reason) VALUES
    ('portal_anonymous_reports', 'contact', 'CT', 'contato do relator anonimo: e-mail ou telefone'),
    ('portal_anonymous_reports', 'photo_ref', 'SENS', 'foto do relato pode mostrar pessoas')
ON CONFLICT (table_name, column_name) DO NOTHING;

INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('portal_anonymous_reports', 'contact', 'REDACT', 'contato do relator anonimo: e-mail ou telefone'),
    ('portal_anonymous_reports', 'photo_ref', 'REDACT', 'foto do relato pode mostrar pessoas: conteudo nunca em claro no audit_log')
ON CONFLICT (table_name, column_name) DO NOTHING;
