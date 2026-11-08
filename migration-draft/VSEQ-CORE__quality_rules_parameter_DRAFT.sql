-- ============================================================================
-- P3.32 — definicao do parametro quality.rules no catalogo global (core)
-- RASCUNHO, mesma pendencia de numeracao do rascunho de tenant: entra na
-- integracao como proxima versao livre do location de core, no padrao da
-- V9_7 (registro pela frente dona do parametro, idempotente).
--
-- Classe d e da spec da P3.32 ("quality.rules (classe d, regras e limiares
-- por tenant)"). factory_default NULL deliberado: NAO ha limiar de fabrica
-- honesto para ciclos sem leitura, area por economia ou compatibilidade
-- CNAE — presumir numero seria inventar politica do tenant. Sem valor
-- aceito e vigente, a operacao falha com MissingParameterException e as
-- regras sem limiar sao registradas como nao configuradas na varredura
-- (ver QualityParameters).
-- ============================================================================

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('quality.rules', 'OBJECT', 'd', NULL,
     'P3.32 (CAD-98); plano v3.0',
     NULL,
     'Regras continuas de qualidade do cadastro comercial: por regra, enabled e severity; limiares cyclesWithoutReading, meterVerificationIntervalYears, possiblyDeceasedAgeYears e undeliveredWindowDays; minAreaPerEconomyM2 por classe regulatoria; cnaeCompatibility por divisao CNAE. Sem valor aceito, a varredura nao roda (erro explicito); regra sem o limiar de que precisa e registrada como nao configurada.')
ON CONFLICT (definition_key) DO NOTHING;

-- Grants de leitura do catalogo, como no fim do padrao V9_7 (correcao R5
-- da revisao independente: o rascunho original omitia os dois GRANT).
GRANT SELECT ON public.parameter_definitions TO ziipsan_app;
GRANT SELECT ON public.parameter_legal_limits TO ziipsan_app;
