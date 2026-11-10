-- II-5 -- definicoes dos parametros meter.renewal_* no catalogo global (core V3).
-- *** SEM NUMERO FLYWAY -- db/pendente/ *** (mesma disciplina do ficheiro do plano)
--
-- TODOS os valores sao [A DEFINIR] (pendencia P-II5-PARAM): factory_default fica NULL
-- em todas as chaves — sem valor vigente, a montagem da configuracao falha com erro
-- explicito, nunca assume peso/restricao em silencio. Pesos e restricao sao
-- parametros declarados, nunca facto inventado.
--
-- REUTILIZADO, NAO DUPLICADO: o limiar de volume por classe continua a ser o
-- meter.replace_by_volume_m3_by_class da ED-14 e o intervalo de verificacao o
-- meter.verification_interval_years da ED-14 (padrao 7, so reduz) — nenhuma das
-- duas chaves e registada outra vez aqui.

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('meter.renewal_score_weights', 'OBJECT', 'd', NULL,
     'PDCA-01, Onda II, item II-5 (benchmark B-4)', NULL,
     'Pesos do score de troca selectiva: {age, volume, classTechnology, waterQuality}, cada um em [0,1] e soma exactamente 1 (regra declarada da II-5). [A DEFINIR] — sem valor de fabrica.'),
    ('meter.renewal_age_reference_years', 'INT', 'd', NULL,
     'PDCA-01, Onda II, item II-5 (benchmark B-4)', NULL,
     'Anos de referencia do componente de idade do score (idade completa / referencia, teto 1). [A DEFINIR] — sem valor de fabrica.'),
    ('meter.renewal_technology_scores', 'OBJECT', 'd', NULL,
     'PDCA-01, Onda II, item II-5 (benchmark B-4)', NULL,
     'Pontuacao [0,1] por chave de tecnologia/classe do hidrometro (chave composta na integracao a partir de MeterModel da ED-14: classe de exatidao, R e classe legada). [A DEFINIR] — sem valor de fabrica.'),
    ('meter.renewal_annual_restriction', 'OBJECT', 'd', NULL,
     'PDCA-01, Onda II, item II-5; estimativa da II-5', NULL,
     'Restricao do plano anual selectivo: {kind: COUNT, maxReplacements} ou {kind: BUDGET, maxBudget}. [A DEFINIR] — sem valor de fabrica; sem valor vigente o plano nao fecha.')
ON CONFLICT (definition_key) DO NOTHING;
