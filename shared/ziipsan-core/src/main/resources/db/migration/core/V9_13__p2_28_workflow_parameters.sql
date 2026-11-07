-- Core Migration — Version: 9.13 (primeira livre após a V9_12 da P2.11 nos pacotes
-- locais; confirmar contra o HEAD na integração, como a P3.36 fez com a V9_11)
-- Description: P2.28 — definições dos parâmetros wf.* no catálogo global
-- (public.parameter_definitions).
--
-- Sem estas linhas, o console (P1.6) recusaria qualquer proposta para as chaves wf.
-- e a caixa não teria de onde resolver teto de delegação nem janela de escalonamento.
--
-- Classes e padrões (do aceite da P2.28, não inventados):
--   * wf.self_approval: classe a, SEMPRE falso — quem solicita não aprova. O padrão
--     false + o limite LOCKED + o ALLOWED_VALUES [false] travam o console; o código
--     (ApprovalRule/TenantWorkParameters) trata valor verdadeiro como catálogo
--     adulterado, nunca como licença.
--   * wf.delegation_max_days: classe d, SEM padrão de fábrica (NULL): o plano não
--     fixa o número de dias; fixar um aqui seria inventar o teto. Sem valor aceito
--     do tenant, delegar é erro explícito (MissingParameterException) até o tenant
--     definir o seu teto pela tela de parâmetros.
--   * wf.escalation_hours: classe d, SEM padrão de fábrica (NULL), pela mesma razão:
--     sem valor aceito, a rodada de escalonamento falha explicitamente em vez de
--     escalar por uma janela presumida.
-- Idempotente (ON CONFLICT DO NOTHING), no padrão da V9_10/V9_11.

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('wf.self_approval', 'BOOLEAN', 'a', 'false',
     'P2.28 (JOR-05); decisão do dono de 28/10/2026',
     NULL,
     'Quem solicita pode aprovar o próprio pedido: sempre falso (classe a, travado). A segregação solicitante != aprovador é aplicada pelo componente de aprovação da P2.28 e por CHECK em approval_tasks.'),
    ('wf.delegation_max_days', 'INT', 'd', NULL,
     'P2.28 (JOR-05)',
     NULL,
     'Teto em dias da delegação temporária da caixa de tarefas (classe d). Sem padrão de fábrica: o plano não fixa o número; o tenant define o seu teto e, sem valor aceito, nenhuma delegação nasce.'),
    ('wf.escalation_hours', 'INT', 'd', NULL,
     'P2.28 (JOR-05)',
     NULL,
     'Horas após o vencimento para o item de trabalho vencido escalar (classe d). Sem padrão de fábrica: sem valor aceito, a rodada de escalonamento falha explicitamente.')
ON CONFLICT (definition_key) DO NOTHING;

INSERT INTO public.parameter_legal_limits
    (definition_key, limit_kind, limit_value, norm_source, applies_to,
     above_max_requires_norm, above_max_requires_dual_approval,
     above_max_requires_formal_acceptance, above_max_risk_alert, notes)
VALUES
    ('wf.self_approval', 'LOCKED', 'false',
     'P2.28 (JOR-05); decisão do dono de 28/10/2026', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'Classe a: o valor não pode sair do padrão false por proposta de tenant.'),
    ('wf.self_approval', 'ALLOWED_VALUES', '[false]',
     'P2.28 (JOR-05)', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'Único valor admissível: false.'),
    ('wf.delegation_max_days', 'MIN_VALUE', '1',
     'P2.28 (JOR-05)', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'Teto de delegação precisa ser ao menos 1 dia.'),
    ('wf.escalation_hours', 'MIN_VALUE', '0',
     'P2.28 (JOR-05)', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'Janela de escalonamento não pode ser negativa; 0 escala no vencimento.')
ON CONFLICT (definition_key, limit_kind, COALESCE(applies_to, '')) DO NOTHING;

GRANT SELECT ON public.parameter_definitions TO ziipsan_app;
GRANT SELECT ON public.parameter_legal_limits TO ziipsan_app;
