-- ED-14 -- definicoes dos parametros meter.* da hidrometria no catalogo global (core V3).
-- *** PENDENTE DE VERSAO CORE, como o SQL tenant: nenhuma subversao core esta reservada a
-- ED-14 no mapa (§3); aplicar na integracao como subversao da reserva vigente. ***
-- "So reduz" do verification_interval_years: teto travado MAX_VALUE = 7 (padrao de fabrica 7).
INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('meter.verification_interval_years', 'INT', 'd', '7',
     'Portaria Inmetro 155/2022; aceite da ED-14', NULL,
     'Intervalo de verificacao do hidrometro em anos: padrao 7, SO REDUZ (teto travado de 7 por parameter_legal_limits). A proxima verificacao e CALCULADA (ultima + intervalo), nunca digitada.'),
    ('meter.replace_by_volume_m3_by_class', 'OBJECT', 'd', NULL,
     'Aceite da ED-14', NULL,
     'Volume totalizado (m3) que dispara a troca, por classe de exatidao ("1"/"2"). Sem valor vigente, o criterio por volume falha com erro explicito — nunca limiar assumido.'),
    ('meter.sample_plan_pct', 'PERCENT', 'd', NULL,
     'Aceite da ED-14', NULL,
     'Percentual de amostragem do lote de hidrometros para ensaio em bancada (0..100).')
ON CONFLICT (definition_key) DO NOTHING;

INSERT INTO public.parameter_legal_limits
    (definition_key, limit_kind, limit_value, norm_source, notes)
VALUES
    ('meter.verification_interval_years', 'MAX_VALUE', '7',
     'Portaria Inmetro 155/2022; aceite da ED-14', 'So reduz: valor acima de 7 e recusado.'),
    ('meter.verification_interval_years', 'MIN_VALUE', '1',
     'Aceite da ED-14', 'Intervalo precisa ser ao menos 1 ano.')
ON CONFLICT DO NOTHING;
