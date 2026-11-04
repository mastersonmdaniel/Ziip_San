-- Core Migration — Version: 9.9
-- Description: P1.36 — definitions of the sac.* parameters the service
-- protocol consumes, in the global catalog (public.parameter_definitions).
--
-- Values, classes and factory defaults are the ones in
-- docs/arquitetura/PARAMETROS-DO-TENANT.md (20d §5, v2.15), not invented
-- here:
--   * sac.deadline_counting_rule: CALENDAR (dias corridos) or BUSINESS
--     (dias úteis, with the holidays of sac.holiday_scope). Class b.
--     Factory default CALENDAR: the most favorable counting to the user
--     (owner-confirmed 21/10/2026); each tenant may choose BUSINESS.
--   * sac.holiday_scope: NATIONAL, NATIONAL_STATE or
--     NATIONAL_STATE_MUNICIPAL. Class b. Factory default NATIONAL (only
--     in the service deadline; the cut veto considers all spheres and is
--     locked elsewhere, not here).
--   * sac.extension_policy: NONE, ONCE_JUSTIFIED or PER_ERI_RULE. Class b.
--     Factory default NONE; ombudsman at most once, for an equal period
--     (Lei 13.460, art. 16).
-- NOT registered here (other fronts own them): sac.response_days (P3),
-- sac.duplicate_handling and sac.ombudsman_* (ED-35). The protocol
-- service links duplicates and never refuses them (PARAMETROS: "recusar
-- não é opção"), but the duplicate-handling parameter itself belongs to
-- ED-35.
--
-- Free core version verified: V9_2 (P1.19), V9_5 (P1.24) and V9_8 (P1.32)
-- are reserved in plan §3; V9_9 was free in the core location.

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('sac.deadline_counting_rule', 'ENUM', 'b', '"CALENDAR"',
     'P1.36; NR 11, art. 52; PARAMETROS-DO-TENANT (20d §5, v2.15)',
     NULL,
     'Contagem do prazo do protocolo de atendimento: CALENDAR (dias corridos) ou BUSINESS (dias uteis, com os feriados de sac.holiday_scope). Padrao CALENDAR, a contagem mais favoravel ao usuario; cada tenant pode escolher BUSINESS.'),
    ('sac.holiday_scope', 'ENUM', 'b', '"NATIONAL"',
     'P1.36; PARAMETROS-DO-TENANT (20d §5, ATD-04)',
     NULL,
     'Esferas de feriado que contam no prazo do protocolo: NATIONAL (so nacionais), NATIONAL_STATE (nacionais + estaduais da UF do atendimento) ou NATIONAL_STATE_MUNICIPAL (todas as esferas). Padrao NATIONAL, so no prazo de atendimento.'),
    ('sac.extension_policy', 'ENUM', 'b', '"NONE"',
     'P1.36; Lei 13.460, art. 16; PARAMETROS-DO-TENANT (20d §5, ATD-05)',
     NULL,
     'Politica de prorrogacao do protocolo: NONE (nenhuma prorrogacao), ONCE_JUSTIFIED (uma unica prorrogacao justificada) ou PER_ERI_RULE (pela regra da agencia reguladora). Padrao NONE; ouvidoria no maximo uma vez, por igual periodo.')
ON CONFLICT (definition_key) DO NOTHING;

GRANT SELECT ON public.parameter_definitions TO ziipsan_app;
