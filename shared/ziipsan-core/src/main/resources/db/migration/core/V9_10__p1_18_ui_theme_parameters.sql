-- Core Migration — Version: 9.10 (integracao P1-C: a V9_8 ja era da P1.32,
-- core V9_8__cert_standard_catalog.sql; a V9_10 e a primeira livre apos a V9_9)
-- Description: P1.18 — definicoes dos parametros ui.* / theme.* no catalogo
-- global (public.parameter_definitions).
--
-- Sem estas linhas, o console (P1.6) recusaria qualquer proposta para as
-- chaves de padrao de tema e idioma ("chave sem definicao no catalogo") e
-- o caminho "versao aceita vigente vence tenant_settings" do
-- UserPreferencesService nunca existiria na pratica: o padrao do tenant
-- so chegaria por tenant_settings. Com elas, o tenant gerencia o padrao
-- pela tela do console, com vigencia, aprovacao e aceite formal, como
-- qualquer outro parametro.
--
-- Classes e padroes (nenhum numero inventado: todos vem do plano/ADR):
--   * ui.default_locale: classe d (PLT-25 a fixa), padrao pt-BR, unico
--     idioma publicado — o limite ALLOWED_VALUES trava o console em
--     pt-BR ate que outra traducao seja publicada.
--   * theme.default_mode: classe d (MARCA-E-TEMAS §4.4 nao a trava),
--     padrao system (a fabrica e "Azul Agua no modo do sistema").
--   * theme.default_palette: classe d, padrao aqua-blue (Azul Agua).
--   * theme.allowed_palettes: classe d, SEM padrao de fabrica (NULL =
--     sem restricao: todos os padroes do registro valem) e SEM limite
--     ALLOWED_VALUES — o limite compara valor escalar contra a lista e
--     recusaria qualquer lista proposta (o valor aqui e um array, nao
--     um escalar); a trava de lista vive no UserPreferencesService.
-- Versao livre verificada: V9_7 e da P1.6, V14 ja existe; V9_8 estava
-- livre no location de core. Idempotente (ON CONFLICT DO NOTHING).

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('ui.default_locale', 'TEXT', 'd', '"pt-BR"',
     'PLT-25 (22c)',
     NULL,
     'Idioma padrao do tenant para a interface. Classe d, padrao pt-BR; pt-BR e o unico idioma publicado (PLT-25). O idioma de preferencia do usuario vive em user_preferences (P1.18, V1_7) e vence este padrao.'),
    ('theme.default_mode', 'TEXT', 'd', '"system"',
     'ADR-0017; MARCA-E-TEMAS §4.4',
     NULL,
     'Modo de tema padrao do tenant (light, dark, system). Classe d; a fabrica e o modo do sistema. A escolha do usuario em user_preferences (P1.18, V1_7) vence este padrao.'),
    ('theme.default_palette', 'TEXT', 'd', '"aqua-blue"',
     'ADR-0017; MARCA-E-TEMAS §4.4',
     NULL,
     'Padrao de cores padrao do tenant (id do registro, ex.: aqua-blue). Classe d; a fabrica e Azul Agua. A escolha do usuario em user_preferences (P1.18, V1_7) vence este padrao, se o padrao estiver na lista permitida.'),
    ('theme.allowed_palettes', 'LIST_TEXT', 'd', NULL,
     'ADR-0017; MARCA-E-TEMAS §4.4',
     NULL,
     'Padroes de cores permitidos no tenant, como lista de ids do registro. Classe d; sem valor (NULL) significa sem restricao. Um padrao fora desta lista cai no padrao do tenant sem apagar a escolha guardada do usuario (P1.18).')
ON CONFLICT (definition_key) DO NOTHING;

INSERT INTO public.parameter_legal_limits
    (definition_key, limit_kind, limit_value, norm_source, applies_to,
     above_max_requires_norm, above_max_requires_dual_approval,
     above_max_requires_formal_acceptance, above_max_risk_alert, notes)
VALUES
    ('ui.default_locale', 'ALLOWED_VALUES', '["pt-BR"]',
     'PLT-25 (22c)', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'So pt-BR e publicado; outro idioma no padrao do tenant e recusado pelo console ate a traducao existir.'),
    ('theme.default_mode', 'ALLOWED_VALUES', '["light", "dark", "system"]',
     'ADR-0017', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'Modos do ADR-0017: claro, escuro e do sistema.'),
    ('theme.default_palette', 'ALLOWED_VALUES', '["aqua-blue", "aqua-green"]',
     'ADR-0017; MARCA-E-TEMAS §4.2', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'Padroes do registro (Azul Agua, Verde Agua). Padrao novo entra aqui junto do registro (§4.7).')
ON CONFLICT (definition_key, limit_kind, COALESCE(applies_to, '')) DO NOTHING;

GRANT SELECT ON public.parameter_definitions TO ziipsan_app;
GRANT SELECT ON public.parameter_legal_limits TO ziipsan_app;
