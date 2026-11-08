-- ============================================================================
-- ED-95 (PENDENTE DE NUMERACAO — CORE): parametros de perdas aparentes
-- (CAD-103, GES-25, OPE-37).
--
-- POR QUE PENDENTE: parametros vivem no catalogo global do core
-- (public.parameter_definitions, mecanismo da P1.6); nenhum numero
-- de migration foi confirmado livre nesta frente — este SQL entra
-- como db/pendente/ do core e recebe o numero na integracao
-- (precedentes: ED-96, ED-56).
--
-- FABRICA — regra dura (nenhum dado inventado): factory_default NULL
-- em TODOS.
--  * registry.property_registry_sync: o aceite nomeia a chave, mas
--    nenhuma fonte enumera o dominio de valores nem um padrao: o
--    modo e o declarado pelo tenant/integracao [A DEFINIR dominio].
--  * audit.unregistered_building_review_days: prazo da fila de
--    revisao do cruzamento; nenhuma fonte fixa numero de dias.
--  * fraud.rules: documento JSON plano de habilitacao e limiares
--    das regras (GES-25); limiar presumido destruiria a medicao do
--    ADR-0042 — sem configuracao do tenant, a avaliacao recusa.
--  * fraud.self_report_fine_waiver_enabled: CHAVE PROPOSTA por esta
--    entrega [A DEFINIR confirmacao na integracao] — o aceite pede o
--    programa de autodenuncia "como opcao do tenant" sem nomear a
--    chave; ausencia = programa nao declarado (recusa, nao presuncao).
--
-- Os 14 valores da P3.9 NAO sao tocados por este arquivo.
-- ============================================================================

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('registry.property_registry_sync', 'TEXT', 'd', NULL,
     'ED-95/CAD-103 — modo de sincronizacao do cadastro imobiliario municipal declarado pelo tenant/integracao',
     NULL,
     'Modo declarado de sincronizacao do cadastro imobiliario municipal (conector do hub, ED-62/P3.37). Sem valor de fabrica e sem dominio fechado nas fontes [A DEFINIR]: o valor declarado e registrado verbatim.'),
    ('audit.unregistered_building_review_days', 'INTEGER', 'd', NULL,
     'ED-95/OPE-37 — prazo da fila de revisao dos cruzamentos de edificacoes nao cadastradas',
     NULL,
     'Prazo em dias para revisao humana dos achados do cruzamento edificacoes x unidades (OPE-37). Sem valor de fabrica: prazo nao configurado recusa o cruzamento.'),
    ('fraud.rules', 'JSON', 'd', NULL,
     'ED-95/GES-25 — limiares por tenant das regras de suspeita (consumo e cadastro)',
     NULL,
     'Objeto JSON plano: <regra>.enabled (bool) e os limiares da regra (ex.: zeroConsumption.minConsecutiveZeroCycles, abruptDrop.minDropPercent, perEconomy.maxM3PerEconomy, perArea.maxM3PerSquareMeter, stalledMeter.minConsecutiveIdenticalReadings, undersizedMeter.minPeakToNominalPercent, meterReplacement.maxCyclesAfterReplacement, meterReplacement.minDropPercentAfterReplacement, repeatedReading.minConsecutiveRepeatedReadings). Sem valor de fabrica: regra nao habilitada nao dispara; regra habilitada sem limiar e erro explicito.'),
    ('fraud.self_report_fine_waiver_enabled', 'BOOLEAN', 'd', NULL,
     'ED-95 — programa de autodenuncia com isencao de multa como opcao do tenant [CHAVE PROPOSTA]',
     NULL,
     'O tenant optou pelo programa de autodenuncia com isencao de multa? Fato declarado (true/false). Sem declaracao, a autodenuncia e recusada.')
ON CONFLICT (definition_key) DO NOTHING;
