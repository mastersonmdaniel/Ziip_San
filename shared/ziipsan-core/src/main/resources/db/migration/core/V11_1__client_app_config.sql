-- Core Migration — Version: 11_1
-- Description: ED-39 — platform.client_app_config: configuracao do app do
-- cliente por tenant (ADR-0026).
--
-- FONTE DA VERSAO: mapa de migrations do plano v3.0 (secao de versoes
-- reservadas): core `V11_1` = configuracao do app do cliente por tenant
-- (identificador do app, contas de loja, versao publicada; ADR-0026),
-- tarefa ED-39, onda B. A core `V11` (platform.app_device) e da ED-18 e
-- NAO e tocada aqui. Esta e a UNICA migration desta entrega.
--
-- CONTEUDO: uma linha por (tenant, plataforma): o app do cliente e gerado
-- por pipeline com nome, icone e cores do tenant (marca da P1.19/P3.21),
-- publicado nas lojas na conta do PRESTADOR e ligado a base do tenant.
--   * app_identifier: identificador unico e imutavel apos a primeira
--     publicacao (package name no Android / bundle id no iOS), gerado
--     pelo pipeline a partir do slug do tenant — nunca digitado a mao;
--   * keycloak_client_id: client PUBLICO do app no realm `ziipsan-portal`,
--     criado pelo pipeline (PKCE S256 obrigatorio); sem segredo;
--   * store_account_ref: referencia (rotulo) da conta de loja do prestador
--     usada na publicacao daquela plataforma. NAO e credencial: nenhum
--     segredo, certificado ou chave vive nesta tabela;
--   * published_version / published_build: a versao efetivamente
--     publicada na loja, registrada apos a aprovacao da loja; nulos
--     enquanto o app nao foi publicado;
--   * status: DRAFT (gerado, nao submetido), IN_REVIEW (submetido,
--     aguardando a loja), PUBLISHED, SUSPENDED (retirado da venda pela
--     loja ou pelo prestador) e UNPUBLISHED (nunca publicado/encerrado);
--   * universal_link_host: host do Universal Link (iOS) / App Link
--     (Android) do retorno do login; a verificacao exige os arquivos
--     de associacao publicados nesse host (pipeline);
--   * icon_asset_id / primary_color: referencias a marca do tenant
--     (ativos da P1.19); a cor e a gravada da marca, validada no servico;
--   * tenant_base_url: base do tenant a que o app se liga (API do tenant).
--
-- SEM DADO PESSOAL: esta tabela e configuracao de publicacao; nenhum
-- nome de consumidor, documento ou contato vive aqui.
--
-- PARAMETROS: as definicoes globais que a ED-39 introduz vao no catalogo
-- public.parameter_definitions, no padrao da V9_11 (P3.36) e da V11:
--   * app.client_android_store_account (conta de organizacao do Google
--     Play do prestador; SEM padrao de fabrica: ato do dono);
--   * app.client_ios_store_account (conta do Apple Developer Program
--     (organizacao) do prestador; SEM padrao de fabrica: ato do dono).
-- Sem conta registrada na plataforma, a publicacao naquela plataforma e
-- recusada pelo servico (ClientAppConfigService) — regra PLT-33 da ED-18
-- aplicada ao app por tenant.
--
-- Permissoes: a aplicacao le e escreve a configuracao (geracao pelo
-- pipeline e registro de publicacao sao runtime). Nao ha DELETE: app
-- sai de circulacao por UNPUBLISHED/SUSPENDED, nunca por apagamento.

CREATE SCHEMA IF NOT EXISTS platform;

CREATE TABLE IF NOT EXISTS platform.client_app_config (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    platform VARCHAR(10) NOT NULL
        CHECK (platform IN ('ANDROID', 'IOS')),
    -- Identificador do app na loja (package name / bundle id), unico
    -- por plataforma entre todos os tenants: colisao publicaria o app
    -- de um tenant sobre o de outro.
    app_identifier VARCHAR(255) NOT NULL,
    -- Nome de exibicao na loja e no aparelho: o nome do tenant/prestador.
    display_name VARCHAR(120) NOT NULL,
    -- Client publico do realm ziipsan-portal criado pelo pipeline.
    keycloak_client_id VARCHAR(255) NOT NULL,
    -- Rotulo da conta de loja do prestador (sem credencial).
    store_account_ref VARCHAR(255),
    status VARCHAR(15) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'IN_REVIEW', 'PUBLISHED', 'SUSPENDED', 'UNPUBLISHED')),
    published_version VARCHAR(50)
        CHECK (published_version IS NULL OR published_version ~ '^[0-9]+[.][0-9]+[.][0-9]+$'),
    published_build INTEGER
        CHECK (published_build IS NULL OR published_build > 0),
    universal_link_host VARCHAR(255) NOT NULL,
    tenant_base_url VARCHAR(500) NOT NULL,
    icon_asset_id UUID,
    primary_color VARCHAR(7)
        CHECK (primary_color IS NULL OR primary_color ~ '^#[0-9A-Fa-f]{6}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    -- Publicado exige versao, build e conta de loja registradas
    -- (correcao D5 da revisao: o CHECK original nao exigia o build,
    -- enquanto o servico ja o exigia — agora SQL e Java exigem os tres,
    -- e versao semver/build>0 valem nos dois lados).
    CHECK (status <> 'PUBLISHED' OR (published_version IS NOT NULL AND published_build IS NOT NULL AND store_account_ref IS NOT NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_client_app_tenant_platform
    ON platform.client_app_config (tenant_id, platform);

CREATE UNIQUE INDEX IF NOT EXISTS uq_client_app_identifier_platform
    ON platform.client_app_config (platform, app_identifier);

CREATE INDEX IF NOT EXISTS idx_client_app_tenant_status
    ON platform.client_app_config (tenant_id, status);

GRANT USAGE ON SCHEMA platform TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.client_app_config TO ziipsan_app;

COMMENT ON TABLE platform.client_app_config IS
    'ED-39 (ADR-0026): configuracao do app do cliente por tenant e plataforma; sem dado pessoal, sem credencial de loja';

-- =======================================================================
-- Parametros globais da ED-39 (catalogo, padrao da V9_11/V11)
-- =======================================================================

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('app.client_android_store_account', 'TEXT', 'd', NULL,
     'ED-39 (PLT-33 da ED-18); ADR-0026', NULL,
     'Conta de organizacao do Google Play do PRESTADOR usada para publicar o app do cliente dos tenants (D-U-N-S ou verificacao governamental, regra da ED-18). Sem padrao de fabrica: o titular e registrado na implantacao, por ato do dono da conta.'),
    ('app.client_ios_store_account', 'TEXT', 'd', NULL,
     'ED-39 (PLT-33 da ED-18); ADR-0026', NULL,
     'Conta do Apple Developer Program (organizacao) do PRESTADOR usada para publicar o app do cliente dos tenants. Sem padrao de fabrica: o titular e registrado na implantacao, por ato do dono da conta.')
ON CONFLICT (definition_key) DO NOTHING;

GRANT SELECT ON public.parameter_definitions TO ziipsan_app;
