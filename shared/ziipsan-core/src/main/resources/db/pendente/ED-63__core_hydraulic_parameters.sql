-- ============================================================================
-- ED-63 (PENDENTE DE NUMERACAO — CORE): parametros da hidraulica (GIS-12).
-- Parametros vivem no catalogo global do core (public.parameter_definitions,
-- mecanismo da P1.6; os gis.* entraram na V9_6 do core). A serie do core na
-- main nao foi conferida nesta frente a ponto de confirmar numero livre:
-- este SQL entra como db/pendente/ do core, como na ED-64.
-- NENHUM factory_default numerico: a janela de meses do consumo faturado e
-- do tenant (GIS-CADASTRO-TECNICO §11: "media de meses parametrizavel") e os
-- caminhos dos binarios sao da instalacao.
-- ============================================================================

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('gis.hydraulic.billed_months', 'INTEGER', 'c', NULL,
     'GIS-CADASTRO-TECNICO §11 (GIS-12: demanda do consumo faturado, media de meses parametrizavel)',
     NULL,
     'Meses de consumo faturado que compoem a demanda do modelo hidraulico EPANET. Sem valor do tenant, a exportacao usa o periodo explicitamente pedido na chamada.'),
    ('gis.hydraulic.epanet_bin', 'STRING', 'c', NULL,
     'ED-63/OPE-35 (biblioteca EPANET 2.2 executada como processo; ver relatorio de viabilidade)',
     NULL,
     'Caminho do binario runepanet (EPANET 2.2) no servidor. Sem ele, a simulacao falha declaradamente — nenhum resultado e produzido.'),
    ('gis.hydraulic.swmm_bin', 'STRING', 'c', NULL,
     'ED-63/OPE-35 (biblioteca SWMM 5.2 executada como processo; ver relatorio de viabilidade)',
     NULL,
     'Caminho do binario runswmm (SWMM 5.2) no servidor. Sem ele, a simulacao falha declaradamente — nenhum resultado e produzido.')
ON CONFLICT (definition_key) DO NOTHING;
