-- ED-06 — parâmetros sec.* e portal.passkey_enabled no catálogo global
-- (core, P0.19) — SQL PENDENTE pelo mesmo motivo do arquivo tenant: o §3
-- do plano não reserva versão core para a ED-06. Na integração, entra na
-- primeira versão core livre reservada, sem mudar o conteúdo.
--
-- Classes (ADR-0015): os parâmetros de segurança do tenant são de
-- configuração do tenant — classe d (livre) quando o plano não fixa outra;
-- NENHUM limite legal é inventado aqui: os únicos pisos citados são os do
-- próprio aceite da ED-06/NIST SP 800-63B-4 (15 para fator único), que a
-- aplicação impõe em TenantSecurityPolicy, não um limite de catálogo.
--
-- DECLARAÇÕES (correção D6 da revisão — defaults/chave SEM fonte verbatim
-- no plano, pendentes de confirmação do dono; NÃO são valores do plano):
--   * param_class 'd' em todas as chaves: ESCOLHA desta tarefa (a V3 usa
--     NULL para "classe não fixada no plano"); registrar/confirmar na
--     integração;
--   * factory_default '5' de sec.lockout_attempts: DEFAULT SEM FONTE no
--     aceite/ADR (o 3 do POC-05 é valor do tenant do ensaio);
--   * chave sec.password_composition_required: CHAVE NOVA derivada nesta
--     tarefa — a opção de composição está no aceite, a chave não aparece
--     verbatim na spec/ADR (segue o padrão sec.* das demais chaves).

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('sec.password_min_length', 'INT', 'd', '15',
     'NIST SP 800-63B-4; ADR-0040',
     NULL,
     'Tamanho mínimo da senha no tenant. Padrão de fábrica 15, o mínimo do NIST para senha como fator único; com MFA, o piso do produto é 8 (TenantSecurityPolicy).'),
    ('sec.password_max_age_days', 'INT', 'd', NULL,
     'NIST SP 800-63B-4; ADR-0040',
     NULL,
     'Expiração periódica da senha, em dias. NULO (padrão) = desligada, como recomenda o NIST; ligar (exigência de edital) exige aceite registrado do ADMIN do tenant (nist_policy_acceptances).'),
    ('sec.password_history', 'INT', 'd', '0',
     'ADR-0040',
     NULL,
     'Quantidade de senhas anteriores que não podem ser repetidas na troca. 0 = sem histórico.'),
    ('sec.lockout_attempts', 'INT', 'd', '5',
     'ADR-0040',
     NULL,
     'Tentativas de login falhas consecutivas até o bloqueio da conta, contadas no servidor por usuário e por IP. O ensaio VISAN (POC-05) usa o valor 3 configurado no tenant do ensaio.'),
    ('sec.user_inactivity_days', 'INT', 'd', NULL,
     'ADR-0040',
     NULL,
     'Dias sem login até a conta ser tratada como inativa no login. NULO (padrão) = sem bloqueio por inatividade.'),
    ('sec.password_composition_required', 'BOOLEAN', 'd', 'false',
     'NIST SP 800-63B-4; ADR-0040',
     NULL,
     'Exigir regra de composição (maiúscula, minúscula, dígito e símbolo). Falso por padrão: a exigência é contrária à recomendação do NIST e só vale com aceite registrado do ADMIN do tenant.'),
    ('sec.sso_mode', 'ENUM', 'd', '"LOCAL"',
     'ADR-0040, item 5',
     NULL,
     'SSO corporativo por organização: LOCAL, OIDC, SAML ou LDAP (federação LDAP/AD no Keycloak).'),
    ('sec.sso_enforced', 'BOOLEAN', 'd', 'false',
     'ADR-0040, item 5',
     NULL,
     'Com true, o SSO é obrigatório e o login local é recusado para a organização. Exige sec.sso_mode diferente de LOCAL.'),
    ('sec.scim_enabled', 'BOOLEAN', 'd', 'false',
     'ADR-0040, item 5',
     NULL,
     'Provisionamento por SCIM sobre a API nativa do Keycloak, com escopo por organização e teto das permissões do ADMIN do tenant.'),
    ('portal.passkey_enabled', 'BOOLEAN', 'd', 'false',
     'ADR-0040; ED-06 (definição) / ED-38 (aplicação ao portal)',
     NULL,
     'Passkey como método de MFA e de entrada, definido na política nesta tarefa; quem o aplica ao portal é a ED-38.')
ON CONFLICT (definition_key) DO NOTHING;
