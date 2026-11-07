-- Core Migration — P3.36: definições dos parâmetros notify.* no catálogo global
-- (public.parameter_definitions), no padrão da V9_10 (P1.18).
--
-- Sem estas linhas, o console recusaria qualquer proposta para as chaves ("chave sem
-- definição no catálogo") e a central não teria de onde ler o padrão vigente.
--   * notify.digest_default: classe d (aceite da P3.36), fábrica IMMEDIATE.
--   * notify.mandatory_categories: classe c (aceite da P3.36) = PISO da plataforma:
--     a fábrica é o piso [SECURITY, PENDING_APPROVAL, LEGAL_NOTICE]; o tenant pode
--     ACRESCENTAR categorias. A trava "nunca abaixo do piso" é semântica e vive em
--     NotificationPolicy.effectiveMandatoryCategories (o limite ALLOWED_VALUES do
--     console compara escalar e não expressaria união de conjuntos) — declarado aqui
--     para não fingir que o catálogo sozinho a garante.
-- Versão: V9_11 é a primeira livre depois da V9_10 na sequência V9_x do core
-- (V9_7 consta vago no plano e NÃO é reaproveitado). Idempotente (ON CONFLICT DO NOTHING).

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('notify.digest_default', 'TEXT', 'd', '"IMMEDIATE"',
     'P3.36 (22c; PLT-12)', NULL,
     'Ritmo padrão das notificações externas do tenant: IMMEDIATE ou DAILY (resumo diário). Classe d; a preferência do usuário em notification_preferences vence este padrão. Avisos de categoria obrigatória são sempre imediatos.'),
    ('notify.mandatory_categories', 'LIST_TEXT', 'c', '["SECURITY", "PENDING_APPROVAL", "LEGAL_NOTICE"]',
     'P3.36 (22c; PLT-12); ADR-0020', NULL,
     'Categorias de aviso que NÃO se desligam por preferência. Classe c, piso da plataforma: o valor vigente é a união do piso de fábrica com o que o tenant acrescentar; remover categoria do piso é recusado pela política da P3.36, com teste de que aviso legal não é desligável.')
ON CONFLICT (definition_key) DO NOTHING;

INSERT INTO public.parameter_legal_limits
    (definition_key, limit_kind, limit_value, norm_source, applies_to,
     above_max_requires_norm, above_max_requires_dual_approval,
     above_max_requires_formal_acceptance, above_max_risk_alert, notes)
VALUES
    ('notify.digest_default', 'ALLOWED_VALUES', '["IMMEDIATE", "DAILY"]',
     'P3.36 (22c; PLT-12)', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'Só os dois ritmos do aceite da P3.36: imediato ou resumo diário.')
ON CONFLICT (definition_key, limit_kind, COALESCE(applies_to, '')) DO NOTHING;

GRANT SELECT ON public.parameter_definitions TO ziipsan_app;
GRANT SELECT ON public.parameter_legal_limits TO ziipsan_app;
