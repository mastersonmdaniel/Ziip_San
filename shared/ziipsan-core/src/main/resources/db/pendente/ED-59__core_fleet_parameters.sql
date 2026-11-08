-- ============================================================================
-- ED-59 (PENDENTE DE NUMERACAO — CORE): parametros da frota e das
-- equipes de campo (ADR-0028 (i); OPE-28).
--
-- POR QUE PENDENTE: parametros vivem no catalogo global do core
-- (public.parameter_definitions, mecanismo da P1.6). Nenhum numero
-- livre foi confirmado nesta frente: este SQL entra como
-- db/pendente/ do core e recebe o numero na integracao
-- (precedentes: ED-56, ED-64).
--
-- FABRICA — regra dura da tarefa (nenhum dado inventado): TODOS os
-- parametros SEM factory_default (NULL). O aceite da ED-59 nomeia as
-- chaves fleet.checklist_required e fleet.docs_alert_days mas NAO
-- fixa valor, dias, itens de checklist nem limite de desvio de
-- consumo em nenhuma fonte das dependencias:
--  * fleet.checklist_required: sem valor do tenant, o checklist nao
--    e exigido (declarado no Java); com true, os itens vem de
--    fleet.checklist_items e a exigencia sem itens falha fechada;
--  * fleet.docs_alert_days: sem valor, nao ha estado "vencendo" —
--    vencido continua detectado por comparacao de datas;
--  * fleet.checklist_items: conteudo do tenant, nunca do produto;
--  * fleet.consumption_deviation_percent: sem valor, nenhum alerta
--    de desvio de consumo e emitido.
-- A manutencao de veiculos NAO ganha parametros: reusa
-- maint.pm_generation_lead_days e maint.pm_overdue_tolerance_days
-- da ED-56 (rotina estendida, nao paralela).
--
-- Os 14 valores da P3.9 NAO sao tocados por este arquivo.
-- ============================================================================

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('fleet.checklist_required', 'BOOLEAN', 'b', NULL,
     'ED-59 (OPE-28) — exigencia de checklist pre-viagem; o aceite nomeia a chave sem fixar valor',
     NULL,
     'Quando true, a saida da equipe exige checklist pre-viagem concluido com todos os itens de fleet.checklist_items OK. Sem valor do tenant, o checklist nao e exigido.'),
    ('fleet.docs_alert_days', 'INTEGER', 'b', NULL,
     'ED-59 (OPE-28) — janela de alerta de vencimento de habilitacao/licenciamento; sem numero em fonte',
     NULL,
     'Dias de antecedencia do alerta de vencimento da habilitacao do motorista e do licenciamento do veiculo. Sem valor do tenant, nao ha estado vencendo; vencido e sempre detectado por comparacao de datas.'),
    ('fleet.checklist_items', 'TABLE', 'b', NULL,
     'ED-59 (OPE-28) — itens do checklist pre-viagem; conteudo do tenant',
     NULL,
     'Itens exigidos do checklist pre-viagem (campo items: lista de codigos). Sem itens definidos e com fleet.checklist_required=true, a saida e recusada (falha fechada declarada).'),
    ('fleet.consumption_deviation_percent', 'DECIMAL', 'b', NULL,
     'ED-59 (OPE-28) — limite de desvio do consumo medio por veiculo; sem numero em fonte',
     NULL,
     'Percentual maximo de piora do consumo do ultimo trecho frente a media anterior sem alerta. Sem valor do tenant, nenhum alerta de desvio e emitido.')
ON CONFLICT (definition_key) DO NOTHING;
