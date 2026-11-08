-- ED-33 — Registro da tabela de tenant (CORRECAO RG LOTE 1, item G2).
--
-- ORIGEM: revisao geral 2026-11-08, frente 3 (integracao/migrations),
-- achado G2, confirmado no consolidado: o SQL de origem
-- PENDENTE__esfinge_remittance.sql cria esfinge_remittances com
-- tenant_id e NENHUMA chamada register_tenant_table.
--
-- FORA DA CONTA, POR DESENHO: esfinge_layout_versions, criada no
-- mesmo SQL de origem, NAO e registrada — e catalogo GLOBAL de
-- leiautes do TCE (PK = version, sem tenant_id), como os catalogos
-- globais que os precedentes nao registram (so se registra tabela
-- de tenant). Registrar tabela global forcaria RLS de tenant sobre
-- catalogo compartilhado: errado por desenho, nao omissao.
--
-- CONTEUDO: SOMENTE o registro de esfinge_remittances e a
-- classificacao/redacao da coluna de identificacao em texto livre
-- (padrao V8_1, requester_name). Nenhuma tabela e criada aqui.
-- O content (JSON da remessa) e o sha256 (hash do ARQUIVO da
-- remessa, nao indice cego de pessoa) nao ganham regra: o OMIT da
-- V4_2 e para indice cego/ciphertext de pessoa, que vincula
-- registros de pessoa — nao e o caso de hash de arquivo.
-- return_notes nao ganha regra: texto administrativo do ente sobre
-- o retorno (padrao das descricoes do tenant sem redacao, revisao
-- P2.5 §3.5).
--
-- ORDEM OBRIGATORIA NA INTEGRACAO: numerar JUNTO E DEPOIS do
-- PENDENTE__esfinge_remittance.sql, na sequencia global da P3.12
-- (V100+, atualizando migration-policy/published-tenant-migrations.txt).
-- Depende de a tabela ja existir, de register_tenant_table (V1_2,
-- core) e das tabelas public.data_classification e
-- public.audit_redaction_rule (core). NUMERACAO PENDENTE.

SELECT register_tenant_table('esfinge_remittances');

INSERT INTO public.data_classification (table_name, column_name, class, reason) VALUES
    ('esfinge_remittances', 'downloaded_by', 'ID', 'identificacao de quem baixou a remessa, em texto livre')
ON CONFLICT (table_name, column_name) DO NOTHING;

INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('esfinge_remittances', 'downloaded_by', 'REDACT', 'free text: may contain personal data of the user')
ON CONFLICT (table_name, column_name) DO NOTHING;
