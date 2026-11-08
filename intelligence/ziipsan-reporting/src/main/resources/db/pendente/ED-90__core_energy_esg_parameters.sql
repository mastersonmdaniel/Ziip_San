-- ============================================================================
-- ED-90 (PENDENTE DE NUMERACAO - CORE): parametros de energia e ESG
-- (OPE-08, GES-21).
--
-- POR QUE PENDENTE: parametros vivem no catalogo global do core
-- (public.parameter_definitions, mecanismo da P1.6). Este SQL entra
-- como db/pendente/ e recebe o numero na integracao (precedentes:
-- ED-59, ED-56, ED-64).
--
-- FABRICA - regra dura da tarefa (nenhum dado inventado): TODOS os
-- parametros SEM factory_default (NULL). O aceite da ED-90 nomeia as
-- quatro chaves mas NAO fixa valor em nenhuma fonte:
--  * energy.demand_alert_pct: sem valor do tenant, a ultrapassagem de
--    demanda nao e avaliada (declarado em EnergyEfficiency);
--  * energy.pf_min: sem valor do tenant, o fator de potencia nao e
--    avaliado - NENHUM minimo regulatorio foi embutido como fabrica;
--  * esg.emission_factors_version: sem versao declarada, o inventario
--    recusa (nunca "a ultima" presumida);
--  * esg.report_profile: sem perfil declarado, nada e exportado
--    (exportacao voluntaria; sem obrigacao de relatorio identificada
--    para autarquia municipal - texto do aceite).
-- Classe 'b' declarada como leitura desta entrega (configuracao do
-- tenant, precedente bench.cross_tenant_opt_in da ED-48); a integracao
-- pode reclassificar sem mudar comportamento, pois nenhum valor de
-- fabrica existe.
--
-- Os 14 valores da P3.9 NAO sao tocados por este arquivo.
-- ============================================================================

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('energy.demand_alert_pct', 'DECIMAL', 'b', NULL,
     'ED-90 (OPE-08) - tolerancia percentual do alerta de multa por ultrapassagem de demanda; o aceite nomeia a chave sem fixar valor',
     NULL,
     'Percentual sobre a demanda contratada alem do qual a demanda medida dispara o alerta de multa. Sem valor do tenant, a ultrapassagem nao e avaliada.'),
    ('energy.pf_min', 'DECIMAL', 'b', NULL,
     'ED-90 (OPE-08) - fator de potencia minimo para o alerta de multa; o aceite nomeia a chave sem fixar valor',
     NULL,
     'Fator de potencia minimo aceito sem alerta, na faixa (0, 1]. Sem valor do tenant, o fator de potencia nao e avaliado.'),
    ('esg.emission_factors_version', 'STRING', 'b', NULL,
     'ED-90 (GES-21) - versao do catalogo global de fatores de emissao usada pelo inventario; o aceite nomeia a chave sem fixar versao',
     NULL,
     'Versao do catalogo global e versionado de fatores de emissao (esg_emission_factors) que o inventario do tenant usa. Sem versao declarada, o inventario recusa.'),
    ('esg.report_profile', 'STRING', 'b', NULL,
     'ED-90 (GES-21) - perfil da exportacao voluntaria para relatorio de sustentabilidade; o aceite nomeia a chave sem fixar perfil',
     NULL,
     'Perfil declarado pelo tenant para exportar o inventario ao relatorio de sustentabilidade. Sem perfil, nada e exportado; o inventario nao depende dele.')
ON CONFLICT (definition_key) DO NOTHING;
