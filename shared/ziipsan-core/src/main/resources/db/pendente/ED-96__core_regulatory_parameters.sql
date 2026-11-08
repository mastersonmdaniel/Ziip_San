-- ============================================================================
-- ED-96 (PENDENTE DE NUMERACAO — CORE): parametros do instrumento
-- contratual regulatorio (GES-02).
--
-- POR QUE PENDENTE: parametros vivem no catalogo global do core
-- (public.parameter_definitions, mecanismo da P1.6); nenhum numero
-- de migration foi confirmado livre nesta frente — este SQL entra
-- como db/pendente/ do core e recebe o numero na integracao
-- (precedentes: ED-56, ED-64).
--
-- FABRICA — regra dura (nenhum dado inventado):
--  * reg.contract_type: SEM factory_default (NULL). E fato declarado
--    do tenant (classe d, REG): o tipo vigente nao e presumido pelo
--    produto. Ausente, a completude da ED-96 anota
--    CONTRACT_TYPE_PARAMETER_UNDECLARED.
--  * reg.risk_matrix_required: SEM factory_default (NULL). E fato
--    declarado (classe d, REG): se o instrumento exige matriz e
--    declaracao do tenant, nao presuncao. Ausente, a completude
--    anota RISK_MATRIX_REQUIREMENT_UNDECLARED e a matriz nao e
--    cobrada.
-- Os limiares do painel do Decreto 11.598/2023 NAO sao parametros:
-- sao os do aceite (margem > 0, endividamento <= 1, retorno > 0,
-- suficiencia > 1), fixos no FinancialCapacity.
--
-- Os 14 valores da P3.9 NAO sao tocados por este arquivo.
-- ============================================================================

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('reg.contract_type', 'ENUM', 'd', NULL,
     'ED-96/GES-02 — tipo do instrumento contratual regulatorio; fato declarado pelo tenant (REG)',
     NULL,
     'Tipo do instrumento sob o qual o tenant presta: PROGRAM_CONTRACT, CONCESSION, PPP, SUBDELEGATION ou DIRECT_PROVISION. Sem valor de fabrica: e fato declarado, nunca presumido.'),
    ('reg.risk_matrix_required', 'BOOLEAN', 'd', NULL,
     'ED-96/GES-02 (NR 5/ANA) — exigencia de matriz de riscos como fato declarado pelo tenant (REG)',
     NULL,
     'O instrumento exige matriz de riscos? Fato declarado (true/false). Sem declaracao, a matriz nao e cobrada na completude e a ausencia fica anotada.')
ON CONFLICT (definition_key) DO NOTHING;

INSERT INTO public.parameter_legal_limits
    (definition_key, limit_kind, limit_value, norm_source, applies_to,
     above_max_requires_norm, above_max_requires_dual_approval,
     above_max_requires_formal_acceptance, above_max_risk_alert, notes)
VALUES
    ('reg.contract_type', 'ALLOWED_VALUES',
     '["PROGRAM_CONTRACT", "CONCESSION", "PPP", "SUBDELEGATION", "DIRECT_PROVISION"]',
     'ED-96/GES-02', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'Dominio fechado do aceite da ED-96: contrato de programa, concessao, PPP, subdelegacao ou prestacao direta com agencia.')
ON CONFLICT (definition_key, limit_kind, COALESCE(applies_to, '')) DO NOTHING;
