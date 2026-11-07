-- Core Migration — Version: 11
-- Description: ED-18 — platform.app_device: dispositivos do app de campo por tenant
-- (ADR-0026, ADR-0030).
--
-- FONTE DA VERSAO: mapa de migrations do plano v3.0 (secao de versoes reservadas):
-- core `V11` = platform.app_device (ED-18, onda A). O texto do aceite no JSON da
-- onda A cita "core V11_1", que e o nome ANTES da renumeracao registrada no
-- proprio mapa ("Antes core V11_1: a ED-18 e da onda A e vem antes da ED-39");
-- core `V11_1` e da ED-39 (configuracao do app do cliente) e NAO e criada aqui.
-- Esta e a UNICA migration desta entrega.
--
-- CONTEUDO (e so isto, como o mapa define): dispositivos do app de campo por
-- tenant, com estado ACTIVE, BLOCKED ou RETIRED, aprovacao, atestacao e ultimo
-- contato, sem dado pessoal alem do `sub` do usuario (ADR-0030): nenhum nome,
-- e-mail, CPF ou telefone vive nesta tabela. O identificador do aparelho
-- (device_id) e gerado no aparelho e nao identifica a pessoa.
--
-- CICLO DE VIDA (aceite da ED-18):
--   * aparelho nasce PENDING_APPROVAL (approved_at nulo): nao sincroniza;
--   * ACTIVE + aprovado: sincroniza;
--   * BLOCKED: a sincronizacao e recusada e o aparelho apaga os dados locais
--     no contato seguinte (diretiva BLOCK);
--   * RETIRED: o aparelho envia as pendencias uma ultima vez e apaga os dados
--     locais (diretiva RETIRE); depois nao sincroniza mais.
--   * revogacao pela area do tenant ou pela Central: status_changed_by_area
--     registra a origem (TENANT ou CENTRAL); a efetividade em no maximo 5
--     minutos e garantida porque o servidor confere o estado a CADA contato
--     de sincronizacao, sem cache alem dessa janela (ver FieldDeviceService).
--
-- PARAMETROS: as definicoes globais que a ED-18 introduz vao no catalogo
-- public.parameter_definitions, no padrao da V9_11 (P3.36):
--   * read.ocr_mode (desligado/sugerir/exigir — fabrica OFF, o mais seguro);
--   * read.ocr_tolerance_digits (digitos divergentes tolerados na comparacao
--     OCR x digitado — fabrica 0, nenhuma divergencia tolerada por padrao);
--   * app.store_account_owner (titular da conta de organizacao na loja;
--     SEM padrao de fabrica: e ato do dono, preenchido na implantacao).
-- Nenhum valor e inventado: os padroes acima sao as opcoes que o proprio
-- aceite enumera, com a escolha conservadora declarada.
--
-- Permissoes: a aplicacao le e escreve o registro de aparelhos (registro,
-- aprovacao, bloqueio e sincronizacao sao runtime). Nao ha DELETE: aparelho
-- sai de uso por RETIRED, nunca por apagamento da linha (trilha preservada).

CREATE SCHEMA IF NOT EXISTS platform;

CREATE TABLE IF NOT EXISTS platform.app_device (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    -- Identificador opaco gerado no aparelho (UUID do app), unico por tenant.
    device_id UUID NOT NULL,
    -- `sub` do Keycloak do leiturista que registrou o aparelho: o UNICO dado
    -- de usuario nesta tabela (ADR-0030). Sem FK: o usuario vive no Keycloak.
    user_sub VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL'
        CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'BLOCKED', 'RETIRED')),
    -- Aprovacao explicita por pessoa da area do tenant ou da Central.
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    -- Atestacao do aparelho (Play Integrity) no registro: resultado e instante.
    -- Nulo = atestacao nao apresentada; aparelho sem atestacao aprovada nao
    -- sai de PENDING_APPROVAL (ver FieldDeviceService).
    attestation_verdict VARCHAR(25)
        CHECK (attestation_verdict IN ('MEETS_DEVICE_INTEGRITY', 'MEETS_BASIC_INTEGRITY', 'FAILED', 'NOT_PRESENTED')),
    attested_at TIMESTAMPTZ,
    -- Origem da ultima mudanca de estado: TENANT (area do tenant) ou CENTRAL.
    status_changed_by_area VARCHAR(10)
        CHECK (status_changed_by_area IN ('TENANT', 'CENTRAL')),
    status_changed_by UUID,
    status_changed_at TIMESTAMPTZ,
    status_reason VARCHAR(500),
    last_contact_at TIMESTAMPTZ,
    app_version VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    -- Aparelho ACTIVE tem aprovacao registrada; os demais estados podem ou
    -- nao te-la (BLOCKED/RETIRED depois de aprovado a preservam como trilha).
    CHECK (status <> 'ACTIVE' OR approved_at IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_app_device_tenant_device
    ON platform.app_device (tenant_id, device_id);

CREATE INDEX IF NOT EXISTS idx_app_device_tenant_status
    ON platform.app_device (tenant_id, status);

GRANT USAGE ON SCHEMA platform TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.app_device TO ziipsan_app;

COMMENT ON TABLE platform.app_device IS
    'ED-18 (ADR-0030): dispositivos do app de campo por tenant; sem dado pessoal alem do sub';

-- =======================================================================
-- Parametros globais da ED-18 (catalogo, padrao da V9_11)
-- =======================================================================

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('read.ocr_mode', 'TEXT', 'd', '"OFF"',
     'ED-18 (18d-E6; OPE-34)', NULL,
     'Reconhecimento optico da leitura por foto do visor, no proprio aparelho: OFF (desligado), SUGGEST (sugerir o valor lido) ou REQUIRE (exigir conferencia). Fabrica OFF: nenhum reconhecimento roda ate o tenant ligar. Classe d.'),
    ('read.ocr_tolerance_digits', 'INT', 'd', '0',
     'ED-18 (18d-E6; OPE-34)', NULL,
     'Digitos divergentes tolerados entre o valor do OCR e o valor digitado antes de a leitura ir a fila de revisao. Fabrica 0: qualquer digito divergente gera revisao. Classe d.'),
    ('app.store_account_owner', 'TEXT', 'd', NULL,
     'ED-18 (PLT-33); ADR-0026', NULL,
     'Titular da conta de organizacao na loja (Google Play): conta de organizacao com D-U-N-S ou verificacao de orgao governamental. Sem padrao de fabrica: o titular e registrado na implantacao, por ato do dono da conta. A ED-39 aplica esta regra ao app por tenant.')
ON CONFLICT (definition_key) DO NOTHING;

INSERT INTO public.parameter_legal_limits
    (definition_key, limit_kind, limit_value, norm_source, applies_to,
     above_max_requires_norm, above_max_requires_dual_approval,
     above_max_requires_formal_acceptance, above_max_risk_alert, notes)
VALUES
    ('read.ocr_mode', 'ALLOWED_VALUES', '["OFF", "SUGGEST", "REQUIRE"]',
     'ED-18 (18d-E6; OPE-34)', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'So os tres modos do aceite da ED-18: desligado, sugerir ou exigir.'),
    ('read.ocr_tolerance_digits', 'MIN_VALUE', '0',
     'ED-18 (18d-E6; OPE-34)', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'Tolerancia negativa nao faz sentido: o piso e zero digitos.')
ON CONFLICT (definition_key, limit_kind, COALESCE(applies_to, '')) DO NOTHING;

GRANT SELECT ON public.parameter_definitions TO ziipsan_app;
GRANT SELECT ON public.parameter_legal_limits TO ziipsan_app;
