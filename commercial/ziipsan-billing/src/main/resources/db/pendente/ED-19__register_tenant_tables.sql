-- ED-19 — Registro das tabelas de tenant (CORRECAO RG LOTE 1, item G2).
--
-- ORIGEM: revisao geral 2026-11-08, frente 3 (integracao/migrations),
-- achado G2, confirmado no consolidado: o SQL de origem
-- ED-19__field_bills_and_divergences.sql cria 3 tabelas com tenant_id
-- (field_bills, field_bill_divergences, field_print_jobs) e NENHUMA
-- chamada register_tenant_table — as tabelas nascem fora da RLS
-- forcada, da policy e da trilha de auditoria que o registro liga
-- (padrao dominante do projeto; na ED-34 a mesma ausencia foi o
-- defeito D3 da revisao cruzada, corrigido no proprio SQL).
--
-- CONTEUDO: SOMENTE os registros das 3 tabelas da ED-19. Nenhuma
-- tabela e criada aqui, nenhuma coluna e alterada.
--
-- ORDEM OBRIGATORIA NA INTEGRACAO: este arquivo deve ser numerado
-- JUNTO E DEPOIS do ED-19__field_bills_and_divergences.sql, na
-- sequencia global da P3.12 (V100+, atualizando
-- migration-policy/published-tenant-migrations.txt). Depende de as
-- tabelas ja existirem e de register_tenant_table (V1_2, core).
-- NUMERACAO PENDENTE: nenhum numero e inventado aqui.
--
-- CLASSIFICACAO/REDACAO: nenhuma linha nova. As colunas das 3 tabelas
-- sao UUIDs, codigos de status com CHECK, valores, fingerprints e o
-- JSON canonico do resultado calculado (printed_result/server_result/
-- explanation) — resultado de calculo tarifario, nao coluna de dado
-- pessoal nos padroes dos precedentes (V21_1: UUIDs de ator sem
-- linha; revisao P2.5 §3.5: JSON de layout/resultado sem regra por
-- desenho). Se um dia o JSON passar a guardar dado de titular, a
-- classificacao nasce devendo na migration que o fizer (P0.11).

SELECT register_tenant_table('field_bills');
SELECT register_tenant_table('field_bill_divergences');
SELECT register_tenant_table('field_print_jobs');
