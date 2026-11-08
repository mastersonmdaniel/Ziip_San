-- ED-31 — Registro das tabelas de tenant (CORRECAO RG LOTE 1, item G2).
--
-- ORIGEM: revisao geral 2026-11-08, frente 3 (integracao/migrations),
-- achado G2, confirmado no consolidado: o SQL de origem
-- PENDENTE__active_debt_core.sql cria 7 tabelas da divida ativa e
-- NENHUMA chamada register_tenant_table — a divida ativa inteira
-- fora da trilha de auditoria/classificacao.
--
-- DIVERGENCIA RELATORIO x SQL REAL (registrada): o relatorio da
-- frente 3 afirma que os outliers tem "tenant_id NOT NULL"; no SQL
-- real da ED-31 so 2 das 7 tabelas tem a coluna tenant_id
-- (active_debt_enrollments e active_debt_monthly_balances). As 5
-- filhas (items, cda_documents, installments, filing_records,
-- protest_records) seguem o pai pela FK para active_debt_enrollments
-- e nao tem tenant_id nem — salvo installments — coluna id. Em TODOS
-- os precedentes dominantes (V4_1, ED-22, ED-34, ED-35, P4.1) toda
-- tabela registrada tem tenant_id. Os registros das 5 filhas vao
-- abaixo porque o defeito G2 e a ausencia do registro, mas a
-- EXECUCAO deles sobre tabela sem tenant_id so se prova na CI
-- definitiva: o corpo de register_tenant_table (V1_2) nao esta em
-- nenhum espelho local. Se a funcao exigir tenant_id, a correcao
-- passa a ser do DDL de origem (acrescentar tenant_id as filhas) —
-- fora do escopo deste arquivo, que contem SOMENTE registros.
--
-- TABELAS SEM COLUNA id: o trigger de auditoria assume NEW.id
-- (precedente V8_1, service_protocol_sequences: sem id, todo INSERT
-- quebra com o trigger). Essas tabelas entram com p_audit => false
-- (RLS e policy do helper continuam; precedente nomeado na ED-17 e
-- na V13_2): active_debt_items, active_debt_cda_documents,
-- active_debt_filing_records, active_debt_protest_records e
-- active_debt_monthly_balances (agregado mensal congelado, PK
-- (tenant_id, month), sem id — analogo mecanico as sequencias).
--
-- CONTEUDO: SOMENTE registros. Nenhuma tabela e criada aqui.
--
-- ORDEM OBRIGATORIA NA INTEGRACAO: numerar JUNTO E DEPOIS do
-- PENDENTE__active_debt_core.sql, na sequencia global da P3.12
-- (V100+, atualizando migration-policy/published-tenant-migrations.txt).
-- Depende de as tabelas ja existirem e de register_tenant_table
-- (V1_2, core). NUMERACAO PENDENTE: nenhum numero e inventado aqui.
--
-- CLASSIFICACAO/REDACAO: nenhuma linha nova. As colunas sao UUIDs,
-- valores, datas, codigos e referencias publicas (numero de processo,
-- comarca, referencia de parecer, documento da CDA): sem coluna de
-- nome/documento/contato/local ou texto livre sobre pessoa nos
-- padroes dos precedentes (V21_1: UUIDs sem linha). Valores nao sao
-- classificados FIN: a classe existe no CHECK mas nenhuma coluna
-- foi classificada FIN ate aqui (catalogo P0.11).

SELECT register_tenant_table('active_debt_enrollments');
SELECT register_tenant_table('active_debt_items', p_audit => false);
SELECT register_tenant_table('active_debt_cda_documents', p_audit => false);
SELECT register_tenant_table('active_debt_installments');
SELECT register_tenant_table('active_debt_filing_records', p_audit => false);
SELECT register_tenant_table('active_debt_protest_records', p_audit => false);
SELECT register_tenant_table('active_debt_monthly_balances', p_audit => false);
