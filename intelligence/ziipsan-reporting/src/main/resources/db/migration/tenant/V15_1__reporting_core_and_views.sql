-- V15_1 - P3.22 (Relatorios operacionais nativos: nucleo e cerca de 20 relatorios).
-- Migration RESERVADA a esta tarefa (plano v3.0, secao 3: faixa reporting,
-- "definicoes, execucoes e agendamentos de relatorio; views de relatorio por
-- dominio"). Modulo NOVO intelligence/ziipsan-reporting.
--
-- Desenho:
--   * report_definitions: catalogo declarativo. Linhas de fabrica tem
--     tenant_id NULL (visiveis a todos os tenants, como os catalogos do core);
--     registros externos (P3.31, modulo de certificacoes) e sobrescritas de
--     tenant gravam com o tenant_id preenchido. A definicao em codigo
--     (ReportRegistry) e a fonte normativa; esta tabela e o espelho
--     consultavel e o ponto de registro runtime.
--   * report_executions: uma linha por geracao (sincrona, assincrona ou
--     agendada), com a fonte de leitura usada e o statement_timeout aplicado.
--   * report_schedules: agendamento executado pelo worker (polling sobre
--     next_run_at); a entrega e SEMPRE um item em generated_files (P1.12).
--   * Views rpt_*: uma por relatorio, sobre as tabelas reais das tarefas
--     fonte (V6_2 pagamentos/retorno, V4_1/V4_3/V4_4 faturamento, V5_1
--     medicao, V3_x cadastro, V8_1 protocolo, V9_1 OS, V9_2 corte, V4_2
--     tarifa social, V4_5 fiscal). security_invoker = true para a RLS do
--     tenant valer tambem pela view (PostgreSQL 15+; a imagem do projeto
--     e PG 17).
--   * NENHUM dado de tenant e semeado: as unicas linhas inseridas sao o
--     catalogo de definicoes de relatorio (dado de produto, tenant_id NULL).
--   * CORRECAO POS-REVISAO (ver RELATORIO-P3.22): as views de isencoes
--     (RPT-024) e devolucoes (RPT-026) leem as tabelas REAIS das fontes
--     (optional_category_enrollments da V4_4; refund_requests da V6_5 da
--     P3.29) diretamente, sem bloco DO/to_regclass e sem contrato
--     inventado: no schema integrado as duas tabelas existem, e view
--     protegida que nasce vazia so escondia o erro de coluna/tabela.

CREATE TABLE IF NOT EXISTS report_definitions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID,
    code VARCHAR(60) NOT NULL,
    title VARCHAR(200) NOT NULL,
    origin VARCHAR(20) NOT NULL DEFAULT 'BUILTIN'
        CHECK (origin IN ('BUILTIN', 'CERTIFICATION', 'EXTERNAL')),
    view_name VARCHAR(80) NOT NULL,
    required_permission VARCHAR(80) NOT NULL,
    columns_spec JSONB NOT NULL,
    filters_spec JSONB NOT NULL DEFAULT '[]'::jsonb,
    default_sort VARCHAR(200),
    max_rows INT NOT NULL DEFAULT 10000 CHECK (max_rows > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_report_definitions_factory
    ON report_definitions(code) WHERE tenant_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_report_definitions_tenant
    ON report_definitions(tenant_id, code) WHERE tenant_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS report_executions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    definition_code VARCHAR(60) NOT NULL,
    requested_by UUID,
    format VARCHAR(10) NOT NULL CHECK (format IN ('PDF', 'XLSX', 'CSV')),
    params JSONB NOT NULL DEFAULT '{}'::jsonb,
    scheduled BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'RUNNING', 'READY', 'FAILED', 'DENIED')),
    data_source VARCHAR(20) NOT NULL DEFAULT 'PRIMARY'
        CHECK (data_source IN ('PRIMARY', 'READ_REPLICA')),
    statement_timeout_ms INT NOT NULL DEFAULT 120000 CHECK (statement_timeout_ms > 0),
    row_count BIGINT,
    generated_file_id UUID,
    error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ,
    -- Colunas da base das entidades (BaseEntity/TenantAwareEntity do core):
    -- version (@Version, nullable=false no mapeamento), tenant_schema,
    -- updated_at (@UpdateTimestamp) e os atores. Sem elas o SQL do
    -- repositorio quebra em runtime (correcao B6 pos-revisao).
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE report_executions ADD CONSTRAINT uq_report_executions_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_report_executions_queue
    ON report_executions(tenant_id, status, created_at);

CREATE TABLE IF NOT EXISTS report_schedules (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    definition_code VARCHAR(60) NOT NULL,
    cron_expression VARCHAR(60) NOT NULL,
    format VARCHAR(10) NOT NULL DEFAULT 'PDF' CHECK (format IN ('PDF', 'XLSX', 'CSV')),
    params JSONB NOT NULL DEFAULT '{}'::jsonb,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    next_run_at TIMESTAMPTZ NOT NULL,
    last_run_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    -- Base das entidades (correcao B6): version (@Version) e tenant_schema.
    version BIGINT NOT NULL DEFAULT 0,
    tenant_schema VARCHAR(100)
);
ALTER TABLE report_schedules ADD CONSTRAINT uq_report_schedules_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_report_schedules_due
    ON report_schedules(next_run_at) WHERE active;

SELECT register_tenant_table('report_executions', true, false);
SELECT register_tenant_table('report_schedules', true, false);
-- report_definitions NAO entra no register_tenant_table: as linhas de fabrica
-- tem tenant_id NULL (catalogo compartilhado), incompativel com a policy de
-- tenant comum; o acesso e mediado pelo nucleo (ReportRegistry), nunca por
-- consulta direta do usuario. Justificativa registrada para o FreshInstallTest.

-- =======================================================================
-- ARRECADACAO (fonte: payments e retorno da V6_2, P3.1/P3.2)
-- =======================================================================
-- RPT-001 Arrecadacao diaria (POC-58): baixado = status BAIXADO.
CREATE OR REPLACE VIEW rpt_arrecadacao_diaria WITH (security_invoker = true) AS
SELECT p.tenant_id, p.paid_on AS data, p.channel AS canal,
       COUNT(*) AS pagamentos, SUM(p.gross_amount) AS valor_bruto,
       SUM(p.net_amount) AS valor_liquido, SUM(p.withheld_amount) AS valor_retido,
       SUM(p.segregated_tax_amount) AS tributo_segregado
FROM payments p
WHERE p.status = 'BAIXADO'
GROUP BY p.tenant_id, p.paid_on, p.channel;

-- RPT-002 Pagamentos nao identificados (POC-59). payer_document e dado
-- pessoal: a coluna e marcada personal na definicao e a exportacao mascara.
-- Fontes (correcao B2 pos-revisao): o status NAO_IDENTIFICADO de payments
-- (classificacao da P3.1) UNION a fila real da P3.29
-- (unidentified_payments em NA_FILA/EM_IDENTIFICACAO, ligada ao pagamento);
-- o UNION deduplica o pagamento que esta nas duas fontes. E esta a UNICA
-- definicao de "nao identificado" dos relatorios: a RPT-026 (devolucoes)
-- nao a duplica.
CREATE OR REPLACE VIEW rpt_pagamentos_nao_identificados WITH (security_invoker = true) AS
SELECT p.tenant_id, p.id AS payment_id, p.paid_on AS data_pagamento,
       p.channel AS canal, p.gross_amount AS valor, p.payer_document AS documento_pagador,
       p.collection_month AS mes_arrecadacao, p.created_at AS recebido_em
FROM payments p
WHERE p.status = 'NAO_IDENTIFICADO'
UNION
SELECT p.tenant_id, p.id AS payment_id, p.paid_on AS data_pagamento,
       p.channel AS canal, p.gross_amount AS valor, p.payer_document AS documento_pagador,
       p.collection_month AS mes_arrecadacao, p.created_at AS recebido_em
FROM unidentified_payments up
JOIN payments p ON p.tenant_id = up.tenant_id AND p.id = up.payment_id
WHERE up.status IN ('NA_FILA', 'EM_IDENTIFICACAO');

-- RPT-003 Pagamentos em duplicidade (POC-59).
CREATE OR REPLACE VIEW rpt_pagamentos_duplicidade WITH (security_invoker = true) AS
SELECT p.tenant_id, p.id AS payment_id, p.bill_id AS conta_id, p.unit_id AS unidade_id,
       p.paid_on AS data_pagamento, p.channel AS canal, p.gross_amount AS valor,
       p.payer_document AS documento_pagador, p.created_at AS recebido_em
FROM payments p
WHERE p.status = 'DUPLICIDADE';

-- RPT-004 Pagamentos por data de remessa, pagamento, credito e banco (POC-60):
-- view-base unica; a dimensao de agrupamento e escolha de execucao do nucleo.
-- DECISAO DO COORDENADOR (correcao B3 pos-revisao): o POC-60 e "por data de
-- remessa/pagamento/credito/banco" e essas datas nascem do RETORNO - a view
-- usa INNER JOIN com o detalhe de retorno: so pagamentos COM retorno entram
-- (sem detalhe nao ha remessa/banco a agrupar). Com LEFT JOIN a soma da
-- massa era 520,75 (incluia duplicidade e nao identificado sem retorno);
-- com INNER JOIN e 345,50, o esperado calculado a mao.
-- CORRECAO R3c: NAO existe data de credito nas colunas reais do retorno
-- (return_file_details tem paid_on; payments tem collection_month, a
-- referencia de arrecadacao com dia 1 - DAD-19 da V6_2). A coluna se chama
-- mes_arrecadacao, sem rotular o dia 1 como "data de credito".
CREATE OR REPLACE VIEW rpt_pagamentos_datas_banco WITH (security_invoker = true) AS
SELECT p.tenant_id, rf.received_on AS data_remessa, p.paid_on AS data_pagamento,
       p.collection_month AS mes_arrecadacao, d.collecting_branch AS banco_agencia,
       d.capture_form AS forma_captura, d.payment_form AS forma_pagamento,
       COUNT(*) AS pagamentos, SUM(p.net_amount) AS valor_liquido
FROM payments p
JOIN return_file_details d ON d.tenant_id = p.tenant_id AND d.payment_id = p.id
JOIN return_files rf ON rf.tenant_id = d.tenant_id AND rf.id = d.return_file_id
GROUP BY p.tenant_id, rf.received_on, p.paid_on, p.collection_month,
         d.collecting_branch, d.capture_form, d.payment_form;

-- RPT-005 Situacao da importacao do retorno.
CREATE OR REPLACE VIEW rpt_retorno_importacao WITH (security_invoker = true) AS
SELECT rf.tenant_id, rf.id AS arquivo_id, rf.nsa, rf.file_name AS arquivo,
       rf.received_on AS recebido_em, rf.status AS situacao,
       COUNT(d.id) AS linhas, COALESCE(SUM(d.paid_amount), 0) AS valor_total
FROM return_files rf
LEFT JOIN return_file_details d ON d.tenant_id = rf.tenant_id AND d.return_file_id = rf.id
GROUP BY rf.tenant_id, rf.id, rf.nsa, rf.file_name, rf.received_on, rf.status;

-- =======================================================================
-- FATURAMENTO (fonte: bills/bill_items da V4_1, ciclo da V4_3)
-- =======================================================================
-- RPT-006 Faturamento sucinto por referencia.
CREATE OR REPLACE VIEW rpt_faturamento_sucinto WITH (security_invoker = true) AS
SELECT b.tenant_id, b.reference_month AS referencia,
       COUNT(*) AS contas, SUM(b.total_value) AS valor_total,
       SUM(b.water_value) AS valor_agua, SUM(b.sewer_value) AS valor_esgoto,
       SUM(b.debits_value) AS valor_debitos, SUM(b.credits_value) AS valor_creditos
FROM bills b
WHERE b.completed AND b.status NOT IN ('CANCELLED', 'CANCELLED_BY_RECTIFICATION')
GROUP BY b.tenant_id, b.reference_month;

-- RPT-007 Faturamento detalhado por categoria e faixa (bill_items).
CREATE OR REPLACE VIEW rpt_faturamento_categoria_faixa WITH (security_invoker = true) AS
SELECT i.tenant_id, b.reference_month AS referencia, c.code AS categoria_codigo,
       c.name AS categoria, i.service_kind AS servico,
       i.from_m3 AS faixa_de, i.to_m3 AS faixa_ate,
       COUNT(DISTINCT i.bill_id) AS contas, SUM(i.economies) AS economias,
       SUM(i.consumption) AS consumo_m3, SUM(i.gross_value) AS valor_bruto,
       SUM(i.discount_value) AS desconto, SUM(i.net_value) AS valor_liquido
FROM bill_items i
JOIN bills b ON b.tenant_id = i.tenant_id AND b.id = i.bill_id
JOIN categories c ON c.tenant_id = i.tenant_id AND c.id = i.category_id
WHERE b.completed AND b.status NOT IN ('CANCELLED', 'CANCELLED_BY_RECTIFICATION')
GROUP BY i.tenant_id, b.reference_month, c.code, c.name, i.service_kind, i.from_m3, i.to_m3;

-- RPT-008 Fechamento do ciclo por grupo.
CREATE OR REPLACE VIEW rpt_fechamento_ciclo_grupo WITH (security_invoker = true) AS
SELECT s.tenant_id, r.reference_month AS referencia, r.status AS referencia_situacao,
       g.code AS grupo_codigo, g.name AS grupo,
       COUNT(*) AS atividades,
       COUNT(*) FILTER (WHERE s.status = 'DONE') AS realizadas,
       -- Correcao R3d: os status reais do ciclo (V4_3) sao PENDING,
       -- COMMANDED, DONE e CANCELLED. CANCELLED e desistencia, NAO
       -- pendencia: pendentes = PENDING + COMMANDED.
       COUNT(*) FILTER (WHERE s.status IN ('PENDING', 'COMMANDED')) AS pendentes
FROM cycle_schedule_items s
JOIN billing_references r ON r.tenant_id = s.tenant_id AND r.id = s.reference_id
JOIN billing_groups g ON g.tenant_id = s.tenant_id AND g.id = s.group_id
GROUP BY s.tenant_id, r.reference_month, r.status, g.code, g.name;

-- RPT-009 Contas emitidas.
CREATE OR REPLACE VIEW rpt_contas_emitidas WITH (security_invoker = true) AS
SELECT b.tenant_id, b.reference_month AS referencia, b.issued_on AS emitida_em,
       COUNT(*) AS contas, SUM(b.total_value) AS valor_total
FROM bills b
WHERE b.completed AND b.issued_on IS NOT NULL
  AND b.status NOT IN ('CANCELLED', 'CANCELLED_BY_RECTIFICATION')
GROUP BY b.tenant_id, b.reference_month, b.issued_on;

-- RPT-010 Contas canceladas.
CREATE OR REPLACE VIEW rpt_contas_canceladas WITH (security_invoker = true) AS
SELECT b.tenant_id, b.id AS conta_id, b.unit_id AS unidade_id,
       b.reference_month AS referencia, b.status AS situacao,
       b.cancel_reason AS motivo, b.total_value AS valor, b.updated_at AS cancelada_em
FROM bills b
WHERE b.status IN ('CANCELLED', 'CANCELLED_BY_RECTIFICATION');

-- RPT-011 Contas retificadas.
CREATE OR REPLACE VIEW rpt_contas_retificadas WITH (security_invoker = true) AS
SELECT b.tenant_id, b.id AS conta_id, b.unit_id AS unidade_id,
       b.reference_month AS referencia, b.rectification_count AS retificacoes,
       b.origin_bill_id AS conta_origem_id, b.total_value AS valor, b.updated_at AS atualizada_em
FROM bills b
WHERE b.status = 'RECTIFIED' OR b.rectification_count > 0;

-- =======================================================================
-- MEDICAO (fonte: V5_1 metering, V5_6 versionamento)
-- =======================================================================
-- RPT-012 Leituras retidas: consumo com faturamento pausado (fila da P2.20).
CREATE OR REPLACE VIEW rpt_leituras_retidas WITH (security_invoker = true) AS
SELECT cr.tenant_id, cr.reference AS referencia, cr.unit_id AS unidade_id,
       cr.service_kind AS servico, cr.consumption_type AS tipo_consumo,
       cr.billed_consumption AS consumo, cr.anomaly_id AS ocorrencia_id,
       cr.created_at AS retida_desde
FROM consumption_records cr
WHERE cr.billing_paused;

-- RPT-013 Ocorrencias de leitura (catalogo reading_anomalies da V5_1).
CREATE OR REPLACE VIEW rpt_ocorrencias_leitura WITH (security_invoker = true) AS
SELECT mr.tenant_id, mr.reference AS referencia, a.code AS ocorrencia_codigo,
       a.description AS ocorrencia, mr.reading_status AS situacao_leitura,
       COUNT(*) AS leituras
FROM meter_readings mr
JOIN reading_anomalies a ON a.tenant_id = mr.tenant_id AND a.id = mr.anomaly_id
GROUP BY mr.tenant_id, mr.reference, a.code, a.description, mr.reading_status;

-- RPT-014 Histograma de consumo por faixa (POC-56), agua, versao vigente.
CREATE OR REPLACE VIEW rpt_histograma_consumo WITH (security_invoker = true) AS
SELECT cr.tenant_id, cr.reference AS referencia,
       CASE WHEN cr.billed_consumption = 0 THEN '0'
            WHEN cr.billed_consumption <= 10 THEN '1-10'
            WHEN cr.billed_consumption <= 15 THEN '11-15'
            WHEN cr.billed_consumption <= 20 THEN '16-20'
            WHEN cr.billed_consumption <= 25 THEN '21-25'
            WHEN cr.billed_consumption <= 30 THEN '26-30'
            WHEN cr.billed_consumption <= 40 THEN '31-40'
            WHEN cr.billed_consumption <= 50 THEN '41-50'
            ELSE '51+' END AS faixa_m3,
       COUNT(*) AS ligacoes, SUM(cr.billed_consumption) AS consumo_total
FROM consumption_records cr
WHERE cr.service_kind = 'WATER' AND cr.superseded_by_id IS NULL
GROUP BY cr.tenant_id, cr.reference,
       CASE WHEN cr.billed_consumption = 0 THEN '0'
            WHEN cr.billed_consumption <= 10 THEN '1-10'
            WHEN cr.billed_consumption <= 15 THEN '11-15'
            WHEN cr.billed_consumption <= 20 THEN '16-20'
            WHEN cr.billed_consumption <= 25 THEN '21-25'
            WHEN cr.billed_consumption <= 30 THEN '26-30'
            WHEN cr.billed_consumption <= 40 THEN '31-40'
            WHEN cr.billed_consumption <= 50 THEN '41-50'
            ELSE '51+' END;

-- =======================================================================
-- CADASTRO E A RECEBER (fonte: consumer_units V3_x, bills V4_1)
-- =======================================================================
-- RPT-015 Ligacoes e economias por situacao (correcao R3a pos-revisao,
-- contra a estrutura real de consumer_units, V3_5/V3_12): cada
-- consumer_unit e UMA economia; a LIGACAO e a unidade-raiz
-- (parent_unit_id IS NULL) - subunidades de condominio
-- (parent_unit_id preenchido) sao economias da ligacao-mae, nao ligacoes
-- novas. Antes as duas colunas eram COUNT(*) identicos.
CREATE OR REPLACE VIEW rpt_ligacoes_situacao WITH (security_invoker = true) AS
SELECT u.tenant_id, u.status AS situacao, u.occupancy_status AS ocupacao,
       COUNT(*) FILTER (WHERE u.parent_unit_id IS NULL) AS ligacoes,
       COUNT(*) AS economias
FROM consumer_units u
WHERE u.deleted_at IS NULL
GROUP BY u.tenant_id, u.status, u.occupancy_status;

-- RPT-016 Ligacoes e economias por categoria (via itens faturados).
CREATE OR REPLACE VIEW rpt_ligacoes_categoria WITH (security_invoker = true) AS
SELECT i.tenant_id, b.reference_month AS referencia, c.code AS categoria_codigo,
       c.name AS categoria, COUNT(DISTINCT i.bill_id) AS ligacoes,
       SUM(i.economies) AS economias
FROM bill_items i
JOIN bills b ON b.tenant_id = i.tenant_id AND b.id = i.bill_id
JOIN categories c ON c.tenant_id = i.tenant_id AND c.id = i.category_id
WHERE b.completed AND b.status NOT IN ('CANCELLED', 'CANCELLED_BY_RECTIFICATION')
GROUP BY i.tenant_id, b.reference_month, c.code, c.name;

-- RPT-017 Valores a receber por tipo de unidade (POC-57).
CREATE OR REPLACE VIEW rpt_a_receber_tipo_unidade WITH (security_invoker = true) AS
SELECT b.tenant_id, pp.name AS tipo_unidade,
       COUNT(*) AS contas_abertas, SUM(b.total_value) AS valor_a_receber,
       MIN(b.due_date) AS vencimento_mais_antigo
FROM bills b
JOIN consumer_units u ON u.tenant_id = b.tenant_id AND u.id = b.unit_id
LEFT JOIN property_profiles pp ON pp.tenant_id = u.tenant_id AND pp.id = u.property_profile_id
WHERE b.completed AND b.status NOT IN ('PAID', 'CANCELLED', 'CANCELLED_BY_RECTIFICATION')
GROUP BY b.tenant_id, pp.name;

-- RPT-018 Pendencias por usuario (POC-65): protocolos abertos e OS nao
-- encerradas, pelo usuario registrador (created_by).
CREATE OR REPLACE VIEW rpt_pendencias_usuario WITH (security_invoker = true) AS
SELECT tenant_id, usuario_id, origem, COUNT(*) AS pendencias,
       MIN(desde) AS mais_antiga_desde
FROM (
    SELECT p.tenant_id, p.created_by AS usuario_id, 'PROTOCOLO' AS origem,
           p.opened_date::timestamptz AS desde
    FROM service_protocols p
    WHERE p.status IN ('OPEN', 'IN_PROGRESS', 'ANSWER_PENDING')
    UNION ALL
    SELECT o.tenant_id, o.created_by, 'ORDEM_SERVICO',
           o.created_at
    FROM service_orders o
    WHERE o.status NOT IN ('CLOSED_EXECUTED', 'CLOSED_NOT_EXECUTED')
) pend
GROUP BY tenant_id, usuario_id, origem;

-- =======================================================================
-- CORTE (fonte: V9_2 da P3.4)
-- =======================================================================
-- RPT-019 Aptos ao corte: unidades com conta vencida e nao paga, SEM
-- protecao registrada (correcao R3b pos-revisao): quem tem motivo de nao
-- geracao/protecao em cutoff_non_generation_reasons (P3.4, V9_2) com
-- codigo de protecao/bloqueio NAO e apto ao corte - PROTECTED_CLASS,
-- SOCIAL_TARIFF_PROTECTED, BILL_UNDER_REVIEW_OR_CONTESTED ou
-- CONNECTION_NOT_ACTIVE. Motivos situacionais (NO_OVERDUE_DEBT,
-- DEBT_TOO_RECENT etc.) nao sao protecao e nao excluem.
CREATE OR REPLACE VIEW rpt_aptos_corte WITH (security_invoker = true) AS
SELECT b.tenant_id, b.unit_id AS unidade_id, COUNT(*) AS contas_vencidas,
       SUM(b.total_value) AS valor_devido, MIN(b.due_date) AS vencimento_mais_antigo
FROM bills b
WHERE b.completed AND b.due_date < CURRENT_DATE
  AND b.status NOT IN ('PAID', 'CANCELLED', 'CANCELLED_BY_RECTIFICATION')
  AND NOT EXISTS (
      SELECT 1 FROM cutoff_non_generation_reasons r
      WHERE r.tenant_id = b.tenant_id AND r.consumer_unit_id = b.unit_id
        AND r.reason_code IN ('PROTECTED_CLASS', 'SOCIAL_TARIFF_PROTECTED',
                              'BILL_UNDER_REVIEW_OR_CONTESTED', 'CONNECTION_NOT_ACTIVE'))
GROUP BY b.tenant_id, b.unit_id;

-- RPT-020 Notificacoes (avisos de corte) emitidas.
CREATE OR REPLACE VIEW rpt_notificacoes_emitidas WITH (security_invoker = true) AS
SELECT n.tenant_id, n.notice_date AS data_aviso, n.status AS situacao,
       n.delivery_proof_status AS prova_entrega,
       COUNT(*) AS avisos, SUM(n.total_amount) AS valor_total
FROM cutoff_notices n
GROUP BY n.tenant_id, n.notice_date, n.status, n.delivery_proof_status;

-- RPT-021 Motivos de nao geracao do aviso (registro da P3.4).
CREATE OR REPLACE VIEW rpt_motivos_nao_geracao_aviso WITH (security_invoker = true) AS
SELECT r.tenant_id, DATE(r.evaluated_at) AS data, r.reason_code AS motivo,
       COUNT(*) AS unidades
FROM cutoff_non_generation_reasons r
GROUP BY r.tenant_id, DATE(r.evaluated_at), r.reason_code;

-- =======================================================================
-- ATENDIMENTO (fonte: V8_1 protocolo P1.36, V9_1 OS P1.31)
-- =======================================================================
-- RPT-022 Servicos por protocolo com tempos (POC-64).
CREATE OR REPLACE VIEW rpt_servicos_protocolo WITH (security_invoker = true) AS
SELECT o.tenant_id, o.protocol_id, t.code AS servico_codigo, t.name AS servico,
       o.status AS situacao, o.created_at AS aberta_em, o.closed_at AS encerrada_em,
       CASE WHEN o.closed_at IS NOT NULL
            THEN EXTRACT(EPOCH FROM (o.closed_at - o.created_at)) / 3600.0 END AS horas_ate_encerrar
FROM service_orders o
JOIN service_types t ON t.tenant_id = o.tenant_id AND t.id = o.service_type_id
WHERE o.protocol_id IS NOT NULL;

-- RPT-027 Reclamacoes do art. 54 da NR 11: por mes, motivo classificado
-- (obrigatorio no encerramento, P1.36) e sistema (canal de origem);
-- nao atendido = encerrado depois do prazo corrente.
-- DECLARACAO (item 5/NR 11 pos-revisao): a view conta TODOS os protocolos
-- encerrados agrupados pelo close_reason_code; a identificacao de quais
-- codigos sao "reclamacao" e do catalogo de motivos do tenant
-- (service_protocol_close_reasons da V8_1) - a view nao inventa uma
-- lista de codigos de reclamacao. Mes sem encerramentos nao gera linha
-- (ausencia explicita, coberta em teste).
CREATE OR REPLACE VIEW rpt_reclamacoes_art54 WITH (security_invoker = true) AS
SELECT p.tenant_id, DATE_TRUNC('month', p.closed_at)::date AS mes,
       p.close_reason_code AS motivo, p.channel AS sistema,
       COUNT(*) AS encerrados,
       COUNT(*) FILTER (WHERE p.closed_at::date > p.current_due_date) AS nao_atendidos_no_prazo,
       ROUND(100.0 * COUNT(*) FILTER (WHERE p.closed_at::date > p.current_due_date)
             / NULLIF(COUNT(*), 0), 2) AS percentual_nao_atendido
FROM service_protocols p
WHERE p.status = 'CLOSED' AND p.closed_at IS NOT NULL
GROUP BY p.tenant_id, DATE_TRUNC('month', p.closed_at)::date, p.close_reason_code, p.channel;

-- =======================================================================
-- SOCIAL E FISCAL
-- =======================================================================
-- RPT-023 Tarifa social (V4_2).
CREATE OR REPLACE VIEW rpt_tarifa_social WITH (security_invoker = true) AS
SELECT e.tenant_id, e.status AS situacao, e.eligibility_basis AS base_elegibilidade,
       COUNT(*) AS inscricoes, SUM(e.social_economies) AS economias_sociais
FROM social_tariff_enrollments e
GROUP BY e.tenant_id, e.status, e.eligibility_basis;

-- RPT-024 Isencoes: enquadramentos em categoria opcional (V4_4,
-- optional_category_enrollments). CORRECAO B1 pos-revisao: a tabela real
-- NAO tem coluna status - tem category_code, criterion, proof, valid_from
-- e valid_to. A view le as colunas reais: isencoes por CATEGORIA, com a
-- VIGENCIA (valid_from/valid_to) como dimensao. criterion/proof NAO sao
-- expostos (dado sensivel do enquadramento, classes SOC/SENS da V4_4).
-- Sem bloco DO: no schema integrado a tabela existe; se faltar, a
-- migration deve falhar alto, nao nascer uma view vazia mentirosa.
CREATE OR REPLACE VIEW rpt_isencoes WITH (security_invoker = true) AS
SELECT e.tenant_id, e.category_code AS categoria_codigo,
       e.valid_from AS vigencia_inicio, e.valid_to AS vigencia_fim,
       COUNT(*) AS inscricoes
FROM optional_category_enrollments e
GROUP BY e.tenant_id, e.category_code, e.valid_from, e.valid_to;

-- RPT-025 NFAg-e por situacao (fiscal_documents da V4_5; estados do
-- dicionario fiscal: AUTHORIZED, REJECTED e contingencia =
-- PENDING_RECONCILIATION/TRANSMITTED sem retorno conclusivo).
CREATE OR REPLACE VIEW rpt_nfage_situacao WITH (security_invoker = true) AS
SELECT f.tenant_id, DATE(f.created_at) AS data,
       CASE WHEN f.status = 'AUTHORIZED' THEN 'AUTORIZADA'
            WHEN f.status = 'REJECTED' THEN 'REJEITADA'
            WHEN f.status IN ('PENDING_RECONCILIATION', 'TRANSMITTED') THEN 'CONTINGENCIA'
            ELSE f.status END AS situacao_agrupada,
       f.status AS situacao, COUNT(*) AS documentos
FROM fiscal_documents f
GROUP BY f.tenant_id, DATE(f.created_at),
       CASE WHEN f.status = 'AUTHORIZED' THEN 'AUTORIZADA'
            WHEN f.status = 'REJECTED' THEN 'REJEITADA'
            WHEN f.status IN ('PENDING_RECONCILIATION', 'TRANSMITTED') THEN 'CONTINGENCIA'
            ELSE f.status END, f.status;

-- RPT-026 Devolucoes (correcao B2 pos-revisao, contra a P3.29 REAL -
-- V6_5): NAO existe tabela payment_refunds. A fonte e refund_requests
-- (origin_payment_id, base_amount, refund_amount, refund_type, status,
-- created_at): motivo = refund_type (DUPLICATE_PAYMENT/UNDUE_CHARGE),
-- situacao = status da devolucao, valor_total = soma de refund_amount
-- (a devolucao PENDENTE nasce sem valor calculado na P3.29: COALESCE 0).
-- A execucao efetiva mora em refund_executions e o fato correspondente
-- em payment_events e REFUND_APPLIED (declarado pela P3.29 como fonte
-- dos relatorios); a fila de nao identificados e a RPT-002, que le
-- unidentified_payments - sem duplicar definicao aqui.
CREATE OR REPLACE VIEW rpt_devolucoes WITH (security_invoker = true) AS
SELECT r.tenant_id, DATE(r.created_at) AS data, r.refund_type AS motivo,
       r.status AS situacao, COUNT(*) AS devolucoes,
       COALESCE(SUM(r.refund_amount), 0) AS valor_total
FROM refund_requests r
GROUP BY r.tenant_id, DATE(r.created_at), r.refund_type, r.status;

-- Catalogo de definicoes de fabrica (tenant_id NULL = produto, nao tenant).
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-001', 'Arrecadação diária', 'BUILTIN', 'rpt_arrecadacao_diaria', 'reporting.rpt-001:export', '[{"name": "data", "type": "DATE", "personal": false}, {"name": "canal", "type": "TEXT", "personal": false}, {"name": "pagamentos", "type": "LONG", "personal": false}, {"name": "valor_bruto", "type": "MONEY", "personal": false}, {"name": "valor_liquido", "type": "MONEY", "personal": false}, {"name": "valor_retido", "type": "MONEY", "personal": false}, {"name": "tributo_segregado", "type": "MONEY", "personal": false}]'::jsonb, '[{"name": "data", "operator": "EQ_OR_RANGE"}]'::jsonb, 'data')
ON CONFLICT DO NOTHING; -- POC-58
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-002', 'Pagamentos não identificados', 'BUILTIN', 'rpt_pagamentos_nao_identificados', 'reporting.rpt-002:export', '[{"name": "payment_id", "type": "TEXT", "personal": false}, {"name": "data_pagamento", "type": "DATE", "personal": false}, {"name": "canal", "type": "TEXT", "personal": false}, {"name": "valor", "type": "MONEY", "personal": false}, {"name": "documento_pagador", "type": "DOCUMENT", "personal": true}, {"name": "mes_arrecadacao", "type": "DATE", "personal": false}, {"name": "recebido_em", "type": "TIMESTAMP", "personal": false}]'::jsonb, '[{"name": "data_pagamento", "operator": "EQ_OR_RANGE"}, {"name": "canal", "operator": "EQ_OR_RANGE"}]'::jsonb, 'payment_id')
ON CONFLICT DO NOTHING; -- POC-59
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-003', 'Pagamentos em duplicidade', 'BUILTIN', 'rpt_pagamentos_duplicidade', 'reporting.rpt-003:export', '[{"name": "payment_id", "type": "TEXT", "personal": false}, {"name": "conta_id", "type": "TEXT", "personal": false}, {"name": "unidade_id", "type": "TEXT", "personal": false}, {"name": "data_pagamento", "type": "DATE", "personal": false}, {"name": "canal", "type": "TEXT", "personal": false}, {"name": "valor", "type": "MONEY", "personal": false}, {"name": "documento_pagador", "type": "DOCUMENT", "personal": true}, {"name": "recebido_em", "type": "TIMESTAMP", "personal": false}]'::jsonb, '[{"name": "data_pagamento", "operator": "EQ_OR_RANGE"}, {"name": "canal", "operator": "EQ_OR_RANGE"}]'::jsonb, 'payment_id')
ON CONFLICT DO NOTHING; -- POC-59
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-004', 'Pagamentos por data de remessa, pagamento, crédito e banco', 'BUILTIN', 'rpt_pagamentos_datas_banco', 'reporting.rpt-004:export', '[{"name": "data_remessa", "type": "DATE", "personal": false}, {"name": "data_pagamento", "type": "DATE", "personal": false}, {"name": "mes_arrecadacao", "type": "DATE", "personal": false}, {"name": "banco_agencia", "type": "TEXT", "personal": false}, {"name": "forma_captura", "type": "TEXT", "personal": false}, {"name": "forma_pagamento", "type": "TEXT", "personal": false}, {"name": "pagamentos", "type": "LONG", "personal": false}, {"name": "valor_liquido", "type": "MONEY", "personal": false}]'::jsonb, '[{"name": "data_pagamento", "operator": "EQ_OR_RANGE"}]'::jsonb, 'data_remessa')
ON CONFLICT DO NOTHING; -- POC-60
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-005', 'Situação da importação do retorno', 'BUILTIN', 'rpt_retorno_importacao', 'reporting.rpt-005:export', '[{"name": "arquivo_id", "type": "TEXT", "personal": false}, {"name": "nsa", "type": "TEXT", "personal": false}, {"name": "arquivo", "type": "TEXT", "personal": false}, {"name": "recebido_em", "type": "DATE", "personal": false}, {"name": "situacao", "type": "TEXT", "personal": false}, {"name": "linhas", "type": "LONG", "personal": false}, {"name": "valor_total", "type": "MONEY", "personal": false}]'::jsonb, '[{"name": "recebido_em", "operator": "EQ_OR_RANGE"}, {"name": "situacao", "operator": "EQ_OR_RANGE"}]'::jsonb, 'arquivo_id')
ON CONFLICT DO NOTHING; -- P3.1
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-006', 'Faturamento sucinto', 'BUILTIN', 'rpt_faturamento_sucinto', 'reporting.rpt-006:export', '[{"name": "referencia", "type": "DATE", "personal": false}, {"name": "contas", "type": "LONG", "personal": false}, {"name": "valor_total", "type": "MONEY", "personal": false}, {"name": "valor_agua", "type": "MONEY", "personal": false}, {"name": "valor_esgoto", "type": "MONEY", "personal": false}, {"name": "valor_debitos", "type": "MONEY", "personal": false}, {"name": "valor_creditos", "type": "MONEY", "personal": false}]'::jsonb, '[{"name": "referencia", "operator": "EQ_OR_RANGE"}]'::jsonb, 'referencia')
ON CONFLICT DO NOTHING; -- 
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-007', 'Faturamento detalhado por categoria e faixa', 'BUILTIN', 'rpt_faturamento_categoria_faixa', 'reporting.rpt-007:export', '[{"name": "referencia", "type": "DATE", "personal": false}, {"name": "categoria_codigo", "type": "TEXT", "personal": false}, {"name": "categoria", "type": "TEXT", "personal": false}, {"name": "servico", "type": "TEXT", "personal": false}, {"name": "faixa_de", "type": "LONG", "personal": false}, {"name": "faixa_ate", "type": "LONG", "personal": false}, {"name": "contas", "type": "LONG", "personal": false}, {"name": "economias", "type": "LONG", "personal": false}, {"name": "consumo_m3", "type": "LONG", "personal": false}, {"name": "valor_bruto", "type": "MONEY", "personal": false}, {"name": "desconto", "type": "MONEY", "personal": false}, {"name": "valor_liquido", "type": "MONEY", "personal": false}]'::jsonb, '[{"name": "referencia", "operator": "EQ_OR_RANGE"}, {"name": "categoria_codigo", "operator": "EQ_OR_RANGE"}]'::jsonb, 'referencia')
ON CONFLICT DO NOTHING; -- 
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-008', 'Fechamento do ciclo por grupo', 'BUILTIN', 'rpt_fechamento_ciclo_grupo', 'reporting.rpt-008:export', '[{"name": "referencia", "type": "DATE", "personal": false}, {"name": "referencia_situacao", "type": "TEXT", "personal": false}, {"name": "grupo_codigo", "type": "TEXT", "personal": false}, {"name": "grupo", "type": "TEXT", "personal": false}, {"name": "atividades", "type": "LONG", "personal": false}, {"name": "realizadas", "type": "LONG", "personal": false}, {"name": "pendentes", "type": "LONG", "personal": false}]'::jsonb, '[{"name": "referencia", "operator": "EQ_OR_RANGE"}, {"name": "grupo_codigo", "operator": "EQ_OR_RANGE"}]'::jsonb, 'referencia')
ON CONFLICT DO NOTHING; -- 
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-009', 'Contas emitidas', 'BUILTIN', 'rpt_contas_emitidas', 'reporting.rpt-009:export', '[{"name": "referencia", "type": "DATE", "personal": false}, {"name": "emitida_em", "type": "DATE", "personal": false}, {"name": "contas", "type": "LONG", "personal": false}, {"name": "valor_total", "type": "MONEY", "personal": false}]'::jsonb, '[{"name": "referencia", "operator": "EQ_OR_RANGE"}]'::jsonb, 'referencia')
ON CONFLICT DO NOTHING; -- 
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-010', 'Contas canceladas', 'BUILTIN', 'rpt_contas_canceladas', 'reporting.rpt-010:export', '[{"name": "conta_id", "type": "TEXT", "personal": false}, {"name": "unidade_id", "type": "TEXT", "personal": false}, {"name": "referencia", "type": "DATE", "personal": false}, {"name": "situacao", "type": "TEXT", "personal": false}, {"name": "motivo", "type": "TEXT", "personal": false}, {"name": "valor", "type": "MONEY", "personal": false}, {"name": "cancelada_em", "type": "TIMESTAMP", "personal": false}]'::jsonb, '[{"name": "referencia", "operator": "EQ_OR_RANGE"}]'::jsonb, 'conta_id')
ON CONFLICT DO NOTHING; -- P2.20
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-011', 'Contas retificadas', 'BUILTIN', 'rpt_contas_retificadas', 'reporting.rpt-011:export', '[{"name": "conta_id", "type": "TEXT", "personal": false}, {"name": "unidade_id", "type": "TEXT", "personal": false}, {"name": "referencia", "type": "DATE", "personal": false}, {"name": "retificacoes", "type": "LONG", "personal": false}, {"name": "conta_origem_id", "type": "TEXT", "personal": false}, {"name": "valor", "type": "MONEY", "personal": false}, {"name": "atualizada_em", "type": "TIMESTAMP", "personal": false}]'::jsonb, '[{"name": "referencia", "operator": "EQ_OR_RANGE"}]'::jsonb, 'conta_id')
ON CONFLICT DO NOTHING; -- P2.20
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-012', 'Leituras retidas', 'BUILTIN', 'rpt_leituras_retidas', 'reporting.rpt-012:export', '[{"name": "referencia", "type": "DATE", "personal": false}, {"name": "unidade_id", "type": "TEXT", "personal": false}, {"name": "servico", "type": "TEXT", "personal": false}, {"name": "tipo_consumo", "type": "TEXT", "personal": false}, {"name": "consumo", "type": "LONG", "personal": false}, {"name": "ocorrencia_id", "type": "TEXT", "personal": false}, {"name": "retida_desde", "type": "TIMESTAMP", "personal": false}]'::jsonb, '[{"name": "referencia", "operator": "EQ_OR_RANGE"}]'::jsonb, 'referencia')
ON CONFLICT DO NOTHING; -- JOR-09
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-013', 'Ocorrências de leitura', 'BUILTIN', 'rpt_ocorrencias_leitura', 'reporting.rpt-013:export', '[{"name": "referencia", "type": "DATE", "personal": false}, {"name": "ocorrencia_codigo", "type": "TEXT", "personal": false}, {"name": "ocorrencia", "type": "TEXT", "personal": false}, {"name": "situacao_leitura", "type": "TEXT", "personal": false}, {"name": "leituras", "type": "LONG", "personal": false}]'::jsonb, '[{"name": "referencia", "operator": "EQ_OR_RANGE"}, {"name": "ocorrencia_codigo", "operator": "EQ_OR_RANGE"}]'::jsonb, 'referencia')
ON CONFLICT DO NOTHING; -- JOR-10
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-014', 'Histograma de consumo por faixa', 'BUILTIN', 'rpt_histograma_consumo', 'reporting.rpt-014:export', '[{"name": "referencia", "type": "DATE", "personal": false}, {"name": "faixa_m3", "type": "TEXT", "personal": false}, {"name": "ligacoes", "type": "LONG", "personal": false}, {"name": "consumo_total", "type": "LONG", "personal": false}]'::jsonb, '[{"name": "referencia", "operator": "EQ_OR_RANGE"}]'::jsonb, 'referencia')
ON CONFLICT DO NOTHING; -- POC-56
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-015', 'Ligações e economias por situação', 'BUILTIN', 'rpt_ligacoes_situacao', 'reporting.rpt-015:export', '[{"name": "situacao", "type": "TEXT", "personal": false}, {"name": "ocupacao", "type": "TEXT", "personal": false}, {"name": "ligacoes", "type": "LONG", "personal": false}, {"name": "economias", "type": "LONG", "personal": false}]'::jsonb, '[{"name": "situacao", "operator": "EQ_OR_RANGE"}]'::jsonb, 'situacao')
ON CONFLICT DO NOTHING; -- 
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-016', 'Ligações e economias por categoria', 'BUILTIN', 'rpt_ligacoes_categoria', 'reporting.rpt-016:export', '[{"name": "referencia", "type": "DATE", "personal": false}, {"name": "categoria_codigo", "type": "TEXT", "personal": false}, {"name": "categoria", "type": "TEXT", "personal": false}, {"name": "ligacoes", "type": "LONG", "personal": false}, {"name": "economias", "type": "LONG", "personal": false}]'::jsonb, '[{"name": "referencia", "operator": "EQ_OR_RANGE"}, {"name": "categoria_codigo", "operator": "EQ_OR_RANGE"}]'::jsonb, 'referencia')
ON CONFLICT DO NOTHING; -- 
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-017', 'Valores a receber por tipo de unidade', 'BUILTIN', 'rpt_a_receber_tipo_unidade', 'reporting.rpt-017:export', '[{"name": "tipo_unidade", "type": "TEXT", "personal": false}, {"name": "contas_abertas", "type": "LONG", "personal": false}, {"name": "valor_a_receber", "type": "MONEY", "personal": false}, {"name": "vencimento_mais_antigo", "type": "DATE", "personal": false}]'::jsonb, '[{"name": "tipo_unidade", "operator": "EQ_OR_RANGE"}]'::jsonb, 'tipo_unidade')
ON CONFLICT DO NOTHING; -- POC-57
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-018', 'Pendências por usuário', 'BUILTIN', 'rpt_pendencias_usuario', 'reporting.rpt-018:export', '[{"name": "usuario_id", "type": "TEXT", "personal": false}, {"name": "origem", "type": "TEXT", "personal": false}, {"name": "pendencias", "type": "LONG", "personal": false}, {"name": "mais_antiga_desde", "type": "TIMESTAMP", "personal": false}]'::jsonb, '[{"name": "origem", "operator": "EQ_OR_RANGE"}]'::jsonb, 'usuario_id')
ON CONFLICT DO NOTHING; -- POC-65
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-019', 'Aptos ao corte', 'BUILTIN', 'rpt_aptos_corte', 'reporting.rpt-019:export', '[{"name": "unidade_id", "type": "TEXT", "personal": false}, {"name": "contas_vencidas", "type": "LONG", "personal": false}, {"name": "valor_devido", "type": "MONEY", "personal": false}, {"name": "vencimento_mais_antigo", "type": "DATE", "personal": false}]'::jsonb, '[{"name": "unidade_id", "operator": "EQ_OR_RANGE"}]'::jsonb, 'unidade_id')
ON CONFLICT DO NOTHING; -- P3.4
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-020', 'Notificações emitidas', 'BUILTIN', 'rpt_notificacoes_emitidas', 'reporting.rpt-020:export', '[{"name": "data_aviso", "type": "DATE", "personal": false}, {"name": "situacao", "type": "TEXT", "personal": false}, {"name": "prova_entrega", "type": "TEXT", "personal": false}, {"name": "avisos", "type": "LONG", "personal": false}, {"name": "valor_total", "type": "MONEY", "personal": false}]'::jsonb, '[{"name": "data_aviso", "operator": "EQ_OR_RANGE"}]'::jsonb, 'data_aviso')
ON CONFLICT DO NOTHING; -- P3.4
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-021', 'Motivos de não geração do aviso', 'BUILTIN', 'rpt_motivos_nao_geracao_aviso', 'reporting.rpt-021:export', '[{"name": "data", "type": "DATE", "personal": false}, {"name": "motivo", "type": "TEXT", "personal": false}, {"name": "unidades", "type": "LONG", "personal": false}]'::jsonb, '[{"name": "data", "operator": "EQ_OR_RANGE"}, {"name": "motivo", "operator": "EQ_OR_RANGE"}]'::jsonb, 'data')
ON CONFLICT DO NOTHING; -- P3.4
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-022', 'Serviços por protocolo com tempos', 'BUILTIN', 'rpt_servicos_protocolo', 'reporting.rpt-022:export', '[{"name": "protocol_id", "type": "TEXT", "personal": false}, {"name": "servico_codigo", "type": "TEXT", "personal": false}, {"name": "servico", "type": "TEXT", "personal": false}, {"name": "situacao", "type": "TEXT", "personal": false}, {"name": "aberta_em", "type": "TIMESTAMP", "personal": false}, {"name": "encerrada_em", "type": "TIMESTAMP", "personal": false}, {"name": "horas_ate_encerrar", "type": "DECIMAL", "personal": false}]'::jsonb, '[{"name": "situacao", "operator": "EQ_OR_RANGE"}, {"name": "servico_codigo", "operator": "EQ_OR_RANGE"}]'::jsonb, 'protocol_id')
ON CONFLICT DO NOTHING; -- POC-64
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-023', 'Tarifa social', 'BUILTIN', 'rpt_tarifa_social', 'reporting.rpt-023:export', '[{"name": "situacao", "type": "TEXT", "personal": false}, {"name": "base_elegibilidade", "type": "TEXT", "personal": false}, {"name": "inscricoes", "type": "LONG", "personal": false}, {"name": "economias_sociais", "type": "LONG", "personal": false}]'::jsonb, '[{"name": "situacao", "operator": "EQ_OR_RANGE"}]'::jsonb, 'situacao')
ON CONFLICT DO NOTHING; -- 
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-024', 'Isenções', 'BUILTIN', 'rpt_isencoes', 'reporting.rpt-024:export', '[{"name": "categoria_codigo", "type": "TEXT", "personal": false}, {"name": "vigencia_inicio", "type": "DATE", "personal": false}, {"name": "vigencia_fim", "type": "DATE", "personal": false}, {"name": "inscricoes", "type": "LONG", "personal": false}]'::jsonb, '[{"name": "categoria_codigo", "operator": "EQ_OR_RANGE"}]'::jsonb, 'categoria_codigo')
ON CONFLICT DO NOTHING; -- 
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-025', 'NFAg-e autorizadas, rejeitadas e em contingência', 'BUILTIN', 'rpt_nfage_situacao', 'reporting.rpt-025:export', '[{"name": "data", "type": "DATE", "personal": false}, {"name": "situacao_agrupada", "type": "TEXT", "personal": false}, {"name": "situacao", "type": "TEXT", "personal": false}, {"name": "documentos", "type": "LONG", "personal": false}]'::jsonb, '[{"name": "data", "operator": "EQ_OR_RANGE"}, {"name": "situacao_agrupada", "operator": "EQ_OR_RANGE"}]'::jsonb, 'data')
ON CONFLICT DO NOTHING; -- 
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-026', 'Devoluções', 'BUILTIN', 'rpt_devolucoes', 'reporting.rpt-026:export', '[{"name": "data", "type": "DATE", "personal": false}, {"name": "motivo", "type": "TEXT", "personal": false}, {"name": "situacao", "type": "TEXT", "personal": false}, {"name": "devolucoes", "type": "LONG", "personal": false}, {"name": "valor_total", "type": "MONEY", "personal": false}]'::jsonb, '[{"name": "data", "operator": "EQ_OR_RANGE"}]'::jsonb, 'data')
ON CONFLICT DO NOTHING; -- P3.29
INSERT INTO report_definitions (tenant_id, code, title, origin, view_name, required_permission, columns_spec, filters_spec, default_sort)
VALUES (NULL, 'RPT-027', 'Reclamações — art. 54 da NR 11', 'BUILTIN', 'rpt_reclamacoes_art54', 'reporting.rpt-027:export', '[{"name": "mes", "type": "DATE", "personal": false}, {"name": "motivo", "type": "TEXT", "personal": false}, {"name": "sistema", "type": "TEXT", "personal": false}, {"name": "encerrados", "type": "LONG", "personal": false}, {"name": "nao_atendidos_no_prazo", "type": "LONG", "personal": false}, {"name": "percentual_nao_atendido", "type": "DECIMAL", "personal": false}]'::jsonb, '[{"name": "mes", "operator": "EQ_OR_RANGE"}, {"name": "motivo", "operator": "EQ_OR_RANGE"}]'::jsonb, 'mes')
ON CONFLICT DO NOTHING; -- NR11 art.54
