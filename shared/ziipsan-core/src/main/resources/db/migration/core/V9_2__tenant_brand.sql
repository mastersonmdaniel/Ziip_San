-- Core Migration — Version: 9.2
-- Description: P1.19 — marca do tenant (ADR-0016, MARCA-E-TEMAS §3).
--
-- PLANO v3.0 §3 (core V9_2, reservada, P1): `public.tenant_assets` (logo do
-- tenant, com estado da varredura) e função SECURITY DEFINER da marca
-- pré-login, restrita às chaves `branding.*` de `tenant_settings`.
--
-- O QUE ESTA VERSAO CRIA:
--   * `public.tenant_assets`: ativos de marca — LOGO ou LOGO_DARK. Bytes em
--     `bytea` (MARCA-E-TEMAS §3.2), SHA-256, tipo, dimensões e estado da
--     varredura do pipeline da P1.20 (QUARANTINED, CLEAN, INFECTED,
--     NOT_SCANNED). Só CLEAN é servido, e só pela função
--     `tenant_brand_logo` abaixo. RLS forçada por tenant, como
--     `tenant_settings`: a aplicação só lê/grava o próprio tenant, com o
--     contexto `app.current_tenant` da transação (precedente da V1).
--     SEM unicidade por (tenant, kind), de propósito (correção da retomada
--     da P1.19): um novo upload precisa coexistir com o logo vigente
--     enquanto aguarda a varredura — com UNIQUE, o segundo upload de um
--     kind que já tinha logo falhava com 23505. O logo EFETIVO é sempre o
--     apontado pela chave `branding.logo` / `branding.logo_dark` (é ele que
--     a função pré-login resolve); a ativação de um CLEAN apaga os demais
--     ativos do kind e aponta a chave, e a remoção apaga todos os do kind.
--     Um ativo em quarentena (QUARANTINED ou NOT_SCANNED) nunca é o
--     efetivo: a marca anterior segue valendo até um CLEAN a substituir.
--   * `tenant_catalog.subdomain_slug`: o rótulo público que resolve o tenant
--     pelo subdomínio (`<slug>.<base>`). A linha do plano para a V9_2 cita
--     os ativos e a função, mas sem um mapeamento host → tenant o
--     `GET /api/v1/public/tenant-brand` "pelo subdomínio" não teria de onde
--     resolver: esta coluna é esse mapeamento mínimo. NULL = tenant sem
--     subdomínio público (cai na marca do produto). UNIQUE: o mesmo slug não
--     pode apontar para dois tenants. O formato é o de um rótulo DNS
--     (minúsculas, 1–63 caracteres, sem hífen nas pontas); `app` e `www`
--     são reservados (o `app.<domínio>` mostra só a marca do produto,
--     MARCA-E-TEMAS §3.3) e recusados aqui e no serviço.
--   * `public.tenant_brand_by_slug(TEXT)`: marca pré-login. SECURITY DEFINER
--     (dona ziipsan_migrator), search_path fixo (precedente da V1_3) e
--     EXECUTE só para ziipsan_app. Sem contexto de tenant para a RLS, a
--     função resolve o tenant pelo catálogo (sem RLS) e fixa
--     `app.current_tenant` LOCAL à transação com `set_config(..., true)` —
--     o mesmo mecanismo que a aplicação usa por requisição — para que as
--     policies de `tenant_settings` e `tenant_assets` vejam só as linhas
--     daquele tenant. A GUC morre com a transação; o chamador (JdbcTemplate
--     em autocommit) não a vaza para a próxima requisição. A projeção é
--     fechada: só chaves `branding.*` com `is_sensitive = false`, só os
--     campos de exibição (nome, nome curto, cor, contato e logos), e só
--     logo com `scan_status = 'CLEAN'`. Slug desconhecido, tenant inativo
--     ou sem marca devolve NULL — o controller responde a marca do produto
--     com o mesmo status (MARCA-E-TEMAS §3.3). `branding.document_footer`
--     NÃO sai aqui: é conteúdo de documento, não de exibição pré-login.
--   * `public.tenant_brand_logo(TEXT, UUID)`: bytes do logo. Mesma
--     resolução por slug + GUC; devolve a linha só quando o ativo é do
--     tenant do slug E está CLEAN — qualquer outro caso devolve zero
--     linhas (404 no controller, sem revelar existência ou estado).
--
-- O QUE ESTA VERSAO NAO FAZ (fica para as tarefas citadas):
--   * domínio próprio do tenant (P2.27): sem CNAME, certificado ou TXT;
--     o slug aqui é só o subdomínio na base do produto;
--   * marca nos PDFs (P2.17) e no portal/e-mails (P3.21): consomem esta
--     marca, não criam outra;
--   * varredura assíncrona por worker: o upload varre na hora (pipeline da
--     P1.20); com o varredor indisponível o ativo fica QUARANTINED e a
--     marca anterior segue valendo, até um novo envio.
--
-- DADOS SINTETICOS APENAS: nenhuma linha é semeada. Os testes criam o
-- tenant, o slug, as chaves e os ativos de que precisam.

-- ======================================================================
-- tenant_catalog.subdomain_slug
-- ======================================================================

ALTER TABLE public.tenant_catalog
    ADD COLUMN IF NOT EXISTS subdomain_slug VARCHAR(63);

ALTER TABLE public.tenant_catalog
    DROP CONSTRAINT IF EXISTS chk_tenant_catalog_subdomain_slug;
ALTER TABLE public.tenant_catalog
    ADD CONSTRAINT chk_tenant_catalog_subdomain_slug
    CHECK (subdomain_slug IS NULL
        OR (subdomain_slug ~ '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$'
            AND subdomain_slug NOT IN ('app', 'www')));

-- UNIQUE ignora NULL no PostgreSQL: vários tenants sem slug são aceitos,
-- mas um slug só aponta para um tenant.
CREATE UNIQUE INDEX IF NOT EXISTS idx_tenant_catalog_subdomain_slug
    ON public.tenant_catalog (subdomain_slug);

-- ======================================================================
-- public.tenant_assets
-- ======================================================================

CREATE TABLE IF NOT EXISTS public.tenant_assets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('LOGO', 'LOGO_DARK')),
    content_type TEXT NOT NULL CHECK (content_type IN ('image/png', 'image/webp')),
    width INT NOT NULL CHECK (width > 0),
    height INT NOT NULL CHECK (height > 0),
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    sha256 CHAR(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    scan_status TEXT NOT NULL DEFAULT 'QUARANTINED'
        CHECK (scan_status IN ('QUARANTINED', 'CLEAN', 'INFECTED', 'NOT_SCANNED')),
    scanned_at TIMESTAMPTZ,
    scanner TEXT,
    threat TEXT,
    content BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE INDEX IF NOT EXISTS idx_tenant_assets_tenant_kind
    ON public.tenant_assets (tenant_id, kind);

ALTER TABLE public.tenant_assets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tenant_assets FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_assets_isolation ON public.tenant_assets;
CREATE POLICY tenant_assets_isolation ON public.tenant_assets
    USING (tenant_id = public.current_tenant_id())
    WITH CHECK (tenant_id = public.current_tenant_id());

GRANT SELECT, INSERT, UPDATE, DELETE ON public.tenant_assets TO ziipsan_app;

COMMENT ON TABLE public.tenant_assets IS
    'P1.19: ativos de marca do tenant (logo claro/escuro), com estado da varredura da P1.20. So CLEAN e servido, e so pela funcao tenant_brand_logo.';

-- ======================================================================
-- public.tenant_brand_by_slug(TEXT) -> JSONB
-- ======================================================================

CREATE OR REPLACE FUNCTION public.tenant_brand_by_slug(p_slug TEXT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $fn$
DECLARE
    v_tenant_id UUID;
    v_settings JSONB;
    v_logo_id UUID;
    v_logo_dark_id UUID;
    v_logo JSONB;
    v_logo_dark JSONB;
BEGIN
    IF p_slug IS NULL OR btrim(p_slug) = '' THEN
        RETURN NULL;
    END IF;
    SELECT id INTO v_tenant_id
      FROM public.tenant_catalog
     WHERE subdomain_slug = lower(btrim(p_slug))
       AND status = 'ACTIVE';
    IF v_tenant_id IS NULL THEN
        RETURN NULL;
    END IF;
    -- Contexto do tenant, local a transacao: sem ele, as policies FORCED
    -- de tenant_settings e tenant_assets esconderiam todas as linhas ate
    -- do dono da funcao (ziipsan_migrator nao tem BYPASSRLS, por desenho
    -- do security.sql). O filtro abaixo continua fechado: so branding.*,
    -- so nao-sensiveis, so campos de exibicao.
    PERFORM set_config('app.current_tenant', v_tenant_id::text, true);
    SELECT jsonb_object_agg(setting_key, setting_value) INTO v_settings
      FROM public.tenant_settings
     WHERE tenant_id = v_tenant_id
       AND starts_with(setting_key, 'branding.')
       AND is_sensitive = false;
    IF v_settings IS NULL THEN
        v_settings := '{}'::jsonb;
    END IF;
    -- branding.logo / branding.logo_dark guardam o id do ativo como
    -- string JSON. Qualquer outro formato e ignorado (NULL), nunca
    -- vazado e nunca usado como id.
    IF (v_settings ->> 'branding.logo') ~ '^[0-9a-fA-F-]{36}$' THEN
        v_logo_id := (v_settings ->> 'branding.logo')::uuid;
    END IF;
    IF (v_settings ->> 'branding.logo_dark') ~ '^[0-9a-fA-F-]{36}$' THEN
        v_logo_dark_id := (v_settings ->> 'branding.logo_dark')::uuid;
    END IF;
    IF v_logo_id IS NOT NULL THEN
        SELECT jsonb_build_object(
                   'id', a.id, 'contentType', a.content_type,
                   'width', a.width, 'height', a.height, 'sha256', a.sha256)
          INTO v_logo
          FROM public.tenant_assets a
         WHERE a.tenant_id = v_tenant_id
           AND a.id = v_logo_id
           AND a.scan_status = 'CLEAN';
    END IF;
    IF v_logo_dark_id IS NOT NULL THEN
        SELECT jsonb_build_object(
                   'id', a.id, 'contentType', a.content_type,
                   'width', a.width, 'height', a.height, 'sha256', a.sha256)
          INTO v_logo_dark
          FROM public.tenant_assets a
         WHERE a.tenant_id = v_tenant_id
           AND a.id = v_logo_dark_id
           AND a.scan_status = 'CLEAN';
    END IF;
    RETURN jsonb_build_object(
        'tenantId', v_tenant_id,
        'slug', lower(btrim(p_slug)),
        'displayName', CASE WHEN jsonb_typeof(v_settings -> 'branding.display_name') = 'string'
                            THEN v_settings ->> 'branding.display_name' END,
        'shortName', CASE WHEN jsonb_typeof(v_settings -> 'branding.short_name') = 'string'
                          THEN v_settings ->> 'branding.short_name' END,
        'color', CASE WHEN jsonb_typeof(v_settings -> 'branding.color') = 'string'
                      THEN v_settings ->> 'branding.color' END,
        'contact', CASE WHEN jsonb_typeof(v_settings -> 'branding.contact') = 'object'
                        THEN v_settings -> 'branding.contact' END,
        'logo', v_logo,
        'logoDark', v_logo_dark);
END;
$fn$;

REVOKE ALL ON FUNCTION public.tenant_brand_by_slug(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.tenant_brand_by_slug(TEXT) TO ziipsan_app;

COMMENT ON FUNCTION public.tenant_brand_by_slug(TEXT) IS
    'P1.19: marca pre-login do tenant pelo slug do subdominio. So chaves branding.* nao-sensiveis, so campos de exibicao, so logo CLEAN. NULL = slug desconhecido/inativo (o controller responde a marca do produto).';

-- ======================================================================
-- public.tenant_brand_logo(TEXT, UUID) -> bytes do logo CLEAN
-- ======================================================================

CREATE OR REPLACE FUNCTION public.tenant_brand_logo(p_slug TEXT, p_asset_id UUID)
RETURNS TABLE(content_type TEXT, width INT, height INT, sha256 TEXT, content BYTEA)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $fn$
DECLARE
    v_tenant_id UUID;
BEGIN
    IF p_slug IS NULL OR btrim(p_slug) = '' OR p_asset_id IS NULL THEN
        RETURN;
    END IF;
    SELECT id INTO v_tenant_id
      FROM public.tenant_catalog
     WHERE subdomain_slug = lower(btrim(p_slug))
       AND status = 'ACTIVE';
    IF v_tenant_id IS NULL THEN
        RETURN;
    END IF;
    PERFORM set_config('app.current_tenant', v_tenant_id::text, true);
    RETURN QUERY
        SELECT a.content_type, a.width, a.height, a.sha256, a.content
          FROM public.tenant_assets a
         WHERE a.tenant_id = v_tenant_id
           AND a.id = p_asset_id
           AND a.scan_status = 'CLEAN';
END;
$fn$;

REVOKE ALL ON FUNCTION public.tenant_brand_logo(TEXT, UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.tenant_brand_logo(TEXT, UUID) TO ziipsan_app;

COMMENT ON FUNCTION public.tenant_brand_logo(TEXT, UUID) IS
    'P1.19: bytes do logo do tenant, so quando o ativo e do tenant do slug e esta CLEAN. Zero linhas em qualquer outro caso (404 no controller).';
