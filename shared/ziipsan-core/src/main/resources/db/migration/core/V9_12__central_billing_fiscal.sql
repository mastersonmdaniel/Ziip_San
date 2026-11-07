-- Core Migration — Version: 9.12
-- Description: P2.11 — Central: abas Faturamento e Fiscal (ADR-0018).
--
-- JUSTIFICATIVA (o plano v3.0 §3 nao reserva versao para a P2.11):
-- as abas novas publicam metricas proprias e a regra de alerta do ciclo
-- referencia `platform.metric_definition` por FK (V9_1) — sem estas
-- linhas, nem `metric_point` nem `alert_rule` aceitam as chaves novas.
-- E o estritamente necessario: 3 definicoes de metrica + 1 regra.
-- Slot da serie da Central: V9_1..V9_10 ocupadas (V9_10 = P1.18) e
-- V9_11 pertence a P3.36 (V9_11__p3_36_notify_parameters.sql); V9_12
-- verificado livre em todos os pacotes locais (correcao pos-revisao
-- da P2.11: a numeracao original V9_11 colidia com a P3.36).
-- O owner das metricas e 'platform', o padrao de todas as linhas da
-- V9_1 (correcao pos-revisao m-5).
--
-- A regra da FILA FISCAL nao nasce aqui: o limiar dela e o do RNF-08 e o
-- valor nao foi localizado nos fontes desta execucao (ver
-- RELATORIO-P2.11.md) — gravar um numero seria inventar o limiar. A
-- regra e inserida na configuracao da Central quando o limiar for
-- definido, a partir de `central.billing_fiscal.thresholds`
-- (platform.config_item), pela mesma forma desta INSERT (a aplicacao
-- monta a AlertRule com CentralBillingFiscalAlerts.fiscalQueueRule).
--
-- Isolamento (V9_1, ADR-0018) inalterado: nenhuma tabela nova, nenhum
-- GRANT novo, nenhum dado de tenant — so catalogo de metricas da
-- propria Central e a regra do ciclo (limiar 0 = qualquer item
-- obrigatorio vencido, fato do cronograma da P2.5, nao numero novo).

INSERT INTO platform.metric_definition (metric_key, unit, metric_type, description, version, owner)
VALUES
    ('billing.cycle.overdue_items', 'count', 'gauge',
     'Itens obrigatorios do cronograma do ciclo vencidos e nao realizados, por tenant (P2.11)', 1, 'platform'),
    ('fiscal.queue.pending', 'count', 'gauge',
     'Documentos fiscais aguardando desfecho (RESERVED+SIGNED+TRANSMITTED+PENDING_RECONCILIATION), por tenant (P2.11)', 1, 'platform'),
    ('fiscal.queue.oldest_pending_age_s', 'seconds', 'gauge',
     'Idade do documento fiscal aguardando desfecho ha mais tempo, por tenant (P2.11)', 1, 'platform')
ON CONFLICT (metric_key) DO NOTHING;

-- Regra do ciclo atrasado (aceite da P2.11): dispara com qualquer item
-- obrigatorio vencido (> 0). Owner/runbook exigidos pela P3.11 sao da
-- aplicacao (a V9_1 nao tem coluna runbook_url); o runbook e o RB-07.
INSERT INTO platform.alert_rule (rule_key, metric_key, comparator, threshold,
    consecutive_readings, severity, scope, owner)
VALUES ('central.billing.cycle_overdue', 'billing.cycle.overdue_items', '>', 0, 1,
        'WARNING', 'TENANT', 'platform')
ON CONFLICT (rule_key) DO NOTHING;
