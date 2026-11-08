-- ED-84 — Registro das tabelas de tenant (CORRECAO RG LOTE 1, item G2).
--
-- ORIGEM: revisao geral 2026-11-08, frente 3 (integracao/migrations),
-- achado G2, confirmado no consolidado: o SQL de origem
-- ED-84__developer_works.sql cria 9 tabelas do fluxo do empreendedor
-- e obras e NENHUMA chamada register_tenant_table.
--
-- DIVERGENCIA RELATORIO x SQL REAL (registrada): como na ED-31, nem
-- todas tem tenant_id — so 4 das 9 (developer_case, provider_work,
-- pavement_restoration, field_order_attachment). As 5 filhas seguem
-- o pai por FK (developer_supervision_visit, developer_fee_charge e
-- developer_transfer_term de developer_case; developer_incorporated_
-- asset de developer_transfer_term; provider_work_supervision_visit
-- de provider_work) e nao tem tenant_id. Vale o aviso do arquivo da
-- ED-31 deste lote: em todos os precedentes dominantes toda tabela
-- registrada tem tenant_id, e a execucao do registro sobre tabela
-- sem tenant_id so se prova na CI definitiva (o corpo da V1_2 nao
-- esta nos espelhos locais).
--
-- FORMAS ESPECIAIS, TODAS COM PRECEDENTE NOMEADO:
--  * Sem coluna id (o trigger de auditoria assume NEW.id — V8_1):
--    p_audit => false, com RLS/policy do helper (ED-17, V13_2):
--    developer_supervision_visit, developer_incorporated_asset,
--    provider_work_supervision_visit e field_order_attachment
--    (cuja PK e order_id, sem coluna id).
--  * developer_transfer_term ja tem coluna version PROPRIA (versao
--    de negocio do termo, UNIQUE (case_id, version)): entra com
--    p_entity => false para o helper nao acrescentar a coluna de
--    entidade sobre a dela — precedente das tabelas de historico da
--    V13_2 (p_entity => false, p_audit => false; aqui a auditoria
--    fica LIGADA porque a tabela tem id).
--
-- CONTEUDO: SOMENTE registros e classificacao/redacao das colunas
-- de nome/texto livre com dado pessoal. Nenhuma tabela e criada aqui.
--
-- ORDEM OBRIGATORIA NA INTEGRACAO: numerar JUNTO E DEPOIS do
-- ED-84__developer_works.sql, na sequencia global da P3.12 (V100+,
-- atualizando migration-policy/published-tenant-migrations.txt).
-- Depende de as tabelas ja existirem, de register_tenant_table
-- (V1_2, core) e das tabelas public.data_classification e
-- public.audit_redaction_rule (core). NUMERACAO PENDENTE.

-- ========================================================================
-- ISOLAMENTO E AUDITORIA (padrao register_tenant_table da core)
-- ========================================================================

SELECT register_tenant_table('developer_case');
SELECT register_tenant_table('developer_supervision_visit', p_audit => false);
SELECT register_tenant_table('developer_fee_charge');
SELECT register_tenant_table('developer_transfer_term', p_entity => false);
SELECT register_tenant_table('developer_incorporated_asset', p_audit => false);
SELECT register_tenant_table('provider_work');
SELECT register_tenant_table('provider_work_supervision_visit', p_audit => false);
SELECT register_tenant_table('pavement_restoration');
SELECT register_tenant_table('field_order_attachment', p_audit => false);

-- ========================================================================
-- CLASSIFICACAO E REDACAO (P0.11) — classes ID/CT/LOC/FIN/SOC/SENS e
-- modos REDACT/OMIT, vocabulario dos precedentes (V1_6, V4_2, V9_1).
-- Nomes de pessoa em texto: REDACT + ID (precedentes holder_name na
-- V14_1 e requester_name na V8_1). Achados de fiscalizacao em texto
-- livre: REDACT + ID (precedente service_order_notes.body, V9_1).
-- ========================================================================

INSERT INTO public.data_classification (table_name, column_name, class, reason) VALUES
    ('developer_case', 'developer_name', 'ID', 'nome do empreendedor: pode ser pessoa natural'),
    ('developer_supervision_visit', 'inspector', 'ID', 'nome do fiscal da visita'),
    ('developer_supervision_visit', 'findings', 'ID', 'achados da fiscalizacao em texto livre: pode conter dado pessoal'),
    ('developer_transfer_term', 'signed_by', 'ID', 'nome de quem assinou o termo de transferencia'),
    ('provider_work_supervision_visit', 'inspector', 'ID', 'nome do fiscal da visita'),
    ('provider_work_supervision_visit', 'findings', 'ID', 'achados da fiscalizacao em texto livre: pode conter dado pessoal')
ON CONFLICT (table_name, column_name) DO NOTHING;

INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('developer_case', 'developer_name', 'REDACT', 'nome do empreendedor: pode ser pessoa natural'),
    ('developer_supervision_visit', 'inspector', 'REDACT', 'nome do fiscal da visita'),
    ('developer_supervision_visit', 'findings', 'REDACT', 'free text: may contain personal data'),
    ('developer_transfer_term', 'signed_by', 'REDACT', 'nome de quem assinou o termo'),
    ('provider_work_supervision_visit', 'inspector', 'REDACT', 'nome do fiscal da visita'),
    ('provider_work_supervision_visit', 'findings', 'REDACT', 'free text: may contain personal data')
ON CONFLICT (table_name, column_name) DO NOTHING;
