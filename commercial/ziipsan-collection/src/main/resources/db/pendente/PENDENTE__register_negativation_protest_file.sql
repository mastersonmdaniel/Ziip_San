-- ED-30 — Registro das tabelas de tenant (CORRECAO RG LOTE 1, item G2).
--
-- ORIGEM: revisao geral 2026-11-08, frente 3 (integracao/migrations),
-- achado G2, confirmado no consolidado: o SQL de origem
-- PENDENTE__negativation_protest_file.sql cria 4 tabelas com
-- tenant_id (negative_listings, negative_listing_evaluations,
-- negative_listing_remittances, negative_listing_returns) e NENHUMA
-- chamada register_tenant_table — negativacao, dominio sensivel a
-- auditoria, fora da trilha.
--
-- CONTEUDO: SOMENTE os registros das 4 tabelas da ED-30 e a
-- classificacao/redacao das colunas de texto livre com dado pessoal
-- (padrao V4_2/V8_1/V9_1). Nenhuma tabela e criada aqui.
--
-- ORDEM OBRIGATORIA NA INTEGRACAO: numerar JUNTO E DEPOIS do
-- PENDENTE__negativation_protest_file.sql, na sequencia global da
-- P3.12 (V100+, atualizando
-- migration-policy/published-tenant-migrations.txt). Depende de as
-- tabelas ja existirem, de register_tenant_table (V1_2, core) e das
-- tabelas public.data_classification e public.audit_redaction_rule
-- (core). NUMERACAO PENDENTE: nenhum numero e inventado aqui.

-- ========================================================================
-- ISOLAMENTO E AUDITORIA (padrao register_tenant_table da core)
-- ========================================================================

SELECT register_tenant_table('negative_listings');
SELECT register_tenant_table('negative_listing_evaluations');
SELECT register_tenant_table('negative_listing_remittances');
SELECT register_tenant_table('negative_listing_returns');

-- ========================================================================
-- CLASSIFICACAO E REDACAO (P0.11) — classes ID/CT/LOC/FIN/SOC/SENS e
-- modos REDACT/OMIT, vocabulario dos precedentes (V1_6, V4_2, V9_1).
-- detail: texto montado pela avaliacao de elegibilidade sobre a
-- situacao do titular (inclui situacoes como titular falecido) —
-- texto livre com dado pessoal: REDACT + ID (precedente
-- service_order_notes.body, V9_1). channel_message: mensagem livre
-- devolvida pelo canal sobre a inscricao de uma pessoa — mesmo
-- criterio (a revisao da P2.5 §3.5 tratou texto livre externo sobre
-- cliente como candidata a REDACT como meters.notes, V5_2).
-- refusal_reasons NAO ganha regra: guarda codigos do conjunto
-- RefusalReason (nomes de enum), nao texto livre — codigos sem regra
-- e o padrao (revisao P2.5 §3.5). Valores (amount) NAO sao
-- classificados: a classe FIN existe no CHECK mas nenhuma coluna
-- foi classificada FIN ate aqui (catalogo P0.11) — inventar seria
-- fabricar dado.
-- ========================================================================

INSERT INTO public.data_classification (table_name, column_name, class, reason) VALUES
    ('negative_listing_evaluations', 'detail', 'ID', 'detalhe livre da avaliacao de elegibilidade: pode conter dado pessoal e a situacao do titular'),
    ('negative_listing_returns', 'channel_message', 'ID', 'mensagem livre do canal sobre a inscricao: pode conter dado pessoal do negativado')
ON CONFLICT (table_name, column_name) DO NOTHING;

INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('negative_listing_evaluations', 'detail', 'REDACT', 'free text: may contain personal data of the holder'),
    ('negative_listing_returns', 'channel_message', 'REDACT', 'free text: may contain personal data of the listed person')
ON CONFLICT (table_name, column_name) DO NOTHING;
