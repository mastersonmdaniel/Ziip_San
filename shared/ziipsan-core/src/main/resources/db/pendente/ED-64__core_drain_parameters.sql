-- ============================================================================
-- ED-64 (PENDENTE DE NUMERACAO — CORE): parametros da drenagem (GIS-13).
--
-- POR QUE PENDENTE: parametros vivem no catalogo global do core
-- (public.parameter_definitions, mecanismo da P1.6; os gis.* entraram
-- na V9_6 do core). A serie do core na main nao foi conferida nesta
-- frente a ponto de confirmar um numero livre, e a regra da tarefa
-- veda numero inventado: este SQL entra como db/pendente/ do core e
-- recebe o proximo numero da serie do core na integracao.
--
-- NENHUM factory_default numerico e registrado:
--  * drain.cleaning_interval_by_class — a NR 12/ANA (Res. ANA
--    245/2025, arts. 11 e 12) exige o plano de operacao e manutencao
--    com periodicidade por classe de elemento; ela NAO fixa os meses
--    neste pacote. Sem valor do tenant, nao ha OS recorrente
--    (GisDrainMaintenance devolve NOT_SCHEDULED) — nunca um intervalo
--    inventado como padrao de fabrica.
--  * drain.rain_source — a fonte e ESCOLHA do tenant (Cemaden por
--    importacao do download mensal, ou pluviometro proprio); sem
--    escolha, nenhuma estacao e criada. SEM dependencia de API do
--    Cemaden, nao confirmada.
--  * drain.gravity_min_slope — o §5.4 pune declividade abaixo da
--    minima com aviso, mas nem o §4.4 nem o §5.4 fixam o numero
--    ("nao ha NBR de projeto de microdrenagem publica", §4.4): a
--    minima e do tenant; sem ela, so os erros de sinal/sentido valem.
-- ============================================================================

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('drain.cleaning_interval_by_class', 'TABLE', 'b', NULL,
     'NR 12/ANA — Res. ANA 245/2025, arts. 11 e 12 (plano de operacao e manutencao da drenagem)',
     NULL,
     'Intervalo de limpeza em meses por classe de elemento de drenagem (INLET, STORM_PIPE, BOX_CULVERT, OPEN_CHANNEL, STORM_MANHOLE, DETENTION_BASIN). Sem valor, nao ha geracao recorrente de limpeza para a classe. A norma exige o plano com periodicidade por classe; os meses sao do tenant.'),
    ('drain.rain_source', 'ENUM', 'b', NULL,
     'GIS-CADASTRO-TECNICO §4.4/GIS-13 (aceite da ED-64)',
     NULL,
     'Fonte do monitoramento pluviometrico: CEMADEN_IMPORT (importacao do download mensal do Cemaden, com a fonte citada em cada lote) ou OWN_GAUGE (pluviometro proprio). Sem API do Cemaden, nao confirmada.'),
    ('drain.gravity_min_slope', 'DECIMAL', 'b', NULL,
     'GIS-CADASTRO-TECNICO §5.4 (aviso de declividade minima; sem valor de norma)',
     NULL,
     'Declividade minima (m/m) dos condutos de drenagem por gravidade para o aviso STORM_SLOPE_BELOW_MIN. Sem valor do tenant, apenas declividade negativa/nula e sentido contrario as cotas sao punidos.')
ON CONFLICT (definition_key) DO NOTHING;

INSERT INTO public.parameter_legal_limits
    (definition_key, limit_kind, limit_value, norm_source, applies_to,
     above_max_requires_norm, above_max_requires_dual_approval,
     above_max_requires_formal_acceptance, above_max_risk_alert, notes)
VALUES
    ('drain.rain_source', 'ALLOWED_VALUES',
     '["CEMADEN_IMPORT", "OWN_GAUGE"]',
     'GIS-CADASTRO-TECNICO §4.4/GIS-13 (aceite da ED-64)', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'Importacao de arquivo (download mensal do Cemaden) ou pluviometro proprio; nenhuma das fontes usa API do Cemaden.')
ON CONFLICT (definition_key, limit_kind, COALESCE(applies_to, '')) DO NOTHING;
