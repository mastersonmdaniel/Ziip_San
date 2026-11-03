-- Core Migration — Version: 9.7
-- Description: P1.6 — definicoes dos parametros so.* / sst.* da P1.31 no
-- catalogo global (public.parameter_definitions).
--
-- QUESTAO DE INTEGRACAO (P1.31 -> P1.6), RESPONDIDA AQUI: a frente P1.31
-- deixou estas chaves FORA do catalogo e registrou para verificar se a
-- P1.6 as registraria. Resposta: SIM, a P1.6 as registra, porque a tela
-- de propor/aprovar/aceitar mudanca de parametro (P1.6) recusa chave sem
-- definicao no catalogo — sem estas linhas, nenhum tenant conseguiria
-- propor um valor para os parametros da OS pela tela do console. O
-- registro segue o precedente da V9_6 (a frente do GIS registrou gis.* na
-- propria onda) e e idempotente (ON CONFLICT DO NOTHING): se a P1.31
-- decidir registrar as chaves na onda dela, as linhas sao as mesmas e
-- nada duplica. As classes, padroes e limites abaixo sao os da P1.31
-- (ServiceOrderParameters, ADR-0039 item 8, 22b), nao numeros inventados
-- aqui:
--   * so.operational_types_enabled: sem classe fixada no plano (o motor
--     nasce habilitado e o tenant pode desligar); padrao true.
--   * so.deadline_hours_by_type: mapa tipo -> horas; so reduz, e o teto
--     e o prazo da agencia declarado em cada tipo de servico — nao ha
--     teto global a registrar, e a trava vive em
--     ServiceOrderParameters.validateDeadlineOverride (P1.31).
--   * sst.required_controls_by_service_type: classe c, o tenant so amplia
--     (a remocao de controle de fabrica e recusada pelo motor, P1.31).
--   * sst.min_crew_confined_space: classe a, piso 2 (NR-33, 33.3.4.4).
--   * sst.training_validity_months: padrao da norma 12 meses (NR-33,
--     33.3.5.3/4/6), o tenant so reduz — registrado como MAX_VALUE 12,
--     porque nenhuma classe do ADR-0015 significa "so reduz" (a = travado,
--     b = padrao com aceite, c = so amplia, d = livre); a classe fica
--     NULL e o limite e a trava.
-- NAO registrados aqui (fora do escopo da P1.31, pendentes da frente
-- dona): so.appointment_window_hours e so.require_customer_acceptance_by_type
-- (ED-20), so.retroactive_close_days, so.customer_order_auto_expire e
-- so.material_charge_policy (ED-36) e so.internal_change_auto_apply
-- (unificado em service_type.internal_approval, P1.31). Registrar chave de
-- outra frente sem a norma dela seria inventar catalogo.
--
-- Versao livre verificada: V9_3 e V9_6 existem, V9 e da P1.6 (console),
-- V9_1 e da P1.31 no location de tenant (outro location, sem conflito);
-- V9_7 estava livre no location de core.

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('so.operational_types_enabled', 'BOOLEAN', NULL, 'true',
     'P1.31 (ADR-0039, item 8)',
     NULL,
     'Chave geral dos tipos operacionais de OS: desligada, nenhum tipo operacional pode ser declarado. Sem classe fixada no plano; o motor nasce habilitado e o tenant pode desligar. Pendencia: a classe deste parametro nao e fixada pela P1.31.'),
    ('so.deadline_hours_by_type', 'OBJECT', NULL, NULL,
     'P1.31 (ADR-0039, item 8)',
     NULL,
     'Prazo por tipo de servico, em horas, como mapa tipo -> horas. So reduz: um valor acima do prazo da agencia declarado no tipo e recusado pelo motor (ServiceOrderParameters.validateDeadlineOverride). Nao ha teto global a registrar.'),
    ('sst.required_controls_by_service_type', 'OBJECT', 'c', NULL,
     'P1.31 (ADR-0039, item 8); NR-33',
     NULL,
     'Controles obrigatorios de seguranca do trabalho por tipo de servico, como mapa tipo -> controles. Classe c: o tenant so amplia, nunca remove um controle de fabrica (a remocao e recusada pelo motor).'),
    ('sst.min_crew_confined_space', 'INT', 'a', '2',
     'NR-33, 33.3.4.4',
     NULL,
     'Equipe minima em espaco confinado. Classe a, piso 2: nenhum trabalho solitario, mais o vigia de entrada. Valor abaixo de 2 e recusado.'),
    ('sst.training_validity_months', 'INT', NULL, '12',
     'NR-33, 33.3.5.3/4/6',
     NULL,
     'Validade da capacitacao em meses. Padrao da norma 12 meses; o tenant so reduz (limite MAX_VALUE 12 abaixo). Nenhuma classe do ADR-0015 significa "so reduz", por isso a classe fica NULL e o limite e a trava.')
ON CONFLICT (definition_key) DO NOTHING;

INSERT INTO public.parameter_legal_limits
    (definition_key, limit_kind, limit_value, norm_source, applies_to,
     above_max_requires_norm, above_max_requires_dual_approval,
     above_max_requires_formal_acceptance, above_max_risk_alert, notes)
VALUES
    ('sst.min_crew_confined_space', 'FLOOR', '2',
     'NR-33, 33.3.4.4', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'Piso 2, classe a: equipe minima em espaco confinado nunca abaixo de 2.'),
    ('sst.training_validity_months', 'MAX_VALUE', '12',
     'NR-33, 33.3.5.3/4/6', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'Teto 12: o tenant so reduz a validade da capacitacao, nunca a amplia alem do padrao da norma.')
ON CONFLICT (definition_key, limit_kind, COALESCE(applies_to, '')) DO NOTHING;

GRANT SELECT ON public.parameter_definitions TO ziipsan_app;
GRANT SELECT ON public.parameter_legal_limits TO ziipsan_app;
