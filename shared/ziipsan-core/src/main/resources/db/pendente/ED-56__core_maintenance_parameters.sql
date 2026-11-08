-- ============================================================================
-- ED-56 (PENDENTE DE NUMERACAO — CORE): parametros da manutencao de
-- ativos e do inventario (GIS-19; OPE-09, OPE-12, OPE-20, OPE-38).
--
-- POR QUE PENDENTE: parametros vivem no catalogo global do core
-- (public.parameter_definitions, mecanismo da P1.6). A serie do core
-- na main nao foi conferida nesta frente a ponto de confirmar um
-- numero livre, e a regra da tarefa veda numero inventado: este SQL
-- entra como db/pendente/ do core e recebe o numero na integracao
-- (precedente: ED-64).
--
-- FABRICA — regra dura da tarefa (nenhum dado inventado):
--  * maint.valve_exercise_years_by_class: UNICO com valor de fabrica,
--    porque o proprio aceite da ED-56 fixa a referencia: "referencia
--    da AWWA: criticas a cada ano, demais a cada cinco anos"
--    (CRITICAL=1, DEFAULT=5). O tenant pode detalhar por classe.
--  * TODOS os demais SEM factory_default (NULL): nenhuma fonte das
--    dependencias fixa dias de antecedencia/tolerancia, meses de
--    teste de hidrante, modelos de checklist, limiares de condicao
--    nem estoques de pecas. Sem valor do tenant, o comportamento e o
--    declarado no Java: lead/tolerancia zero, hidrante sem
--    vencimento por calendario, OS sem checklist, leitura sem
--    gatilho nao gera OS, peca sem politica nao e avaliada.
--  * asset.cctv_standard: [A DEFINIR] — o aceite manda confirmar
--    PACP ou WRc com o cliente e o responsavel tecnico; sem a
--    confirmacao, a importacao de inspecao e recusada.
--
-- Os 14 valores da P3.9 NAO sao tocados por este arquivo.
-- ============================================================================

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('maint.pm_generation_lead_days', 'INTEGER', 'b', NULL,
     'GIS-19/ED-56 (OPE-20) — antecedencia de geracao da OS preventiva; sem valor de norma',
     NULL,
     'Dias de antecedencia para gerar a OS preventiva antes do vencimento. Sem valor do tenant, a geracao ocorre no vencimento (lead zero declarado).'),
    ('maint.pm_overdue_tolerance_days', 'INTEGER', 'b', NULL,
     'GIS-19/ED-56 (OPE-20) — tolerancia de atraso da preventiva; sem valor de norma',
     NULL,
     'Dias de tolerancia apos o vencimento antes de a preventiva contar como atrasada (backlog vencido). Sem valor do tenant, tolerancia zero.'),
    ('maint.checklist_templates', 'TABLE', 'b', NULL,
     'GIS-19/ED-56 (OPE-20) — modelos de checklist de ronda por classe de equipamento; conteudo do tenant',
     NULL,
     'Mapa classe de equipamento -> itens do checklist de ronda. Sem modelo para a classe, a OS sai sem checklist (fato registravel), nunca com itens inventados.'),
    ('maint.valve_exercise_years_by_class', 'TABLE', 'b',
     '{"CRITICAL": 1, "DEFAULT": 5}',
     'GIS-19/ED-56 (OPE-09) — referencia da AWWA citada no aceite: criticas a cada ano, demais a cada cinco anos',
     NULL,
     'Anos entre exercicios de valvula por classe de criticidade. Fabrica = referencia AWWA do aceite (CRITICAL 1 ano, demais 5 anos); o tenant pode detalhar por classe.'),
    ('maint.hydrant_test_months', 'INTEGER', 'b', NULL,
     'GIS-19/ED-56 (OPE-09) — periodicidade do teste de hidrante; o aceite nao fixa numero',
     NULL,
     'Meses entre testes de hidrante. Sem valor do tenant, hidrante nenhum ganha vencimento por calendario.'),
    ('maint.condition_triggers', 'TABLE', 'b', NULL,
     'GIS-19/ED-56 (OPE-38) — gatilhos de manutencao por condicao; limiares do tenant',
     NULL,
     'Gatilhos por equipamento e grandeza (horimetro, corrente, vibracao, temperatura) com comparacao, limiar e unidade. Sem gatilho configurado, leitura nenhuma gera OS.'),
    ('inv.critical_spares', 'TABLE', 'b', NULL,
     'GIS-19/ED-56 (OPE-38) — politica de peca sobressalente critica por equipamento; valores do tenant',
     NULL,
     'Politica por equipamento/peca: estoque minimo e ponto de reposicao. Sem politica, a peca nao e avaliada.'),
    ('asset.cctv_standard', 'ENUM', 'b', NULL,
     'GIS-19/ED-56 (OPE-12) — padrao de inspecao por video a confirmar com o cliente e o responsavel tecnico [A DEFINIR]',
     NULL,
     'Padrao dos codigos de defeito da inspecao por video: PACP (NASSCO) ou WRC. [A DEFINIR] sem valor ate a confirmacao; sem valor, a importacao e recusada.')
ON CONFLICT (definition_key) DO NOTHING;

INSERT INTO public.parameter_legal_limits
    (definition_key, limit_kind, limit_value, norm_source, applies_to,
     above_max_requires_norm, above_max_requires_dual_approval,
     above_max_requires_formal_acceptance, above_max_risk_alert, notes)
VALUES
    ('asset.cctv_standard', 'ALLOWED_VALUES',
     '["PACP", "WRC"]',
     'GIS-19/ED-56 (OPE-12)', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'PACP importa o arquivo de transferencia da NASSCO; WRc usa a pratica WRc confirmada com o responsavel tecnico.')
ON CONFLICT (definition_key, limit_kind, COALESCE(applies_to, '')) DO NOTHING;
