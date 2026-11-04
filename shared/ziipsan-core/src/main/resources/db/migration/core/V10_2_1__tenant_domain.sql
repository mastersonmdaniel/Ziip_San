-- Core Migration — Version: 10.2.1
-- Description: P2.27 — domínio próprio do tenant (PLT-05).
--
-- PLANO v3.0 §3 (core V10_2_1, reservada, P2): `platform.tenant_domain`:
-- domínio próprio do tenant (CNAME), verificação de posse, estado do
-- certificado, renovação e revogação; resolução do tenant pelo host
-- antes do login.
--
-- O QUE ESTA VERSAO CRIA:
--   * `platform.tenant_domain`: um domínio próprio por linha. Estados do
--     domínio: PENDING_VERIFICATION (pedido, aguardando TXT + CNAME),
--     VERIFIED (posse provada por TXT e CNAME apontado para a borda),
--     REVOKED (certificado revogado — não resolve mais, não renova) e
--     REMOVED (apontamento removido no encerramento do contrato ou pelo
--     admin; o hostname fica livre para nova reivindicação, sempre com
--     nova prova TXT — é essa exigência, e não a reserva do nome, que
--     impede a tomada de subdomínio: apontar um CNAME sozinho nunca
--     ativa um domínio de outro tenant).
--     Estado do certificado, separado do estado do domínio: NONE, PENDING,
--     ISSUED, RENEWING, REVOKED, EXPIRED. `cert_provider` registra quem
--     emitiu ('TEST' no ambiente local, com certificado de teste — a
--     automação de certificados na AWS é da LC.2 e NÃO entra aqui; a
--     interface `CertificateIssuer` do serviço é a costura para ela).
--   * Verificação de posse por registro TXT: o tenant publica
--     `_ziipsan-verify.<hostname>` com `ziipsan-verify=<token>`. O token
--     em claro só existe na resposta de criação (o admin o publica e o
--     perde); no banco fica só o SHA-256 dele — a verificação compara
--     o hash do valor lido no DNS com o hash guardado, sem jamais
--     revelar o token. O CNAME esperado (`cname_target`, a borda do
--     Ziip) é conferido antes da emissão do certificado.
--   * Unicidade parcial do hostname: o índice UNIQUE só vale para
--     linhas com status <> 'REMOVED'. Um hostname VERIFIED não pode ser
--     reivindicado por outro tenant (409 `data-conflict`, código já
--     explicado no catálogo de erros); depois de REMOVED, só uma nova
--     prova TXT o reativa, para outro tenant ou para o mesmo.
--   * `public.tenant_domain_by_host(TEXT)`: resolução pré-login do
--     tenant pelo host (domínio próprio VERIFIED). SECURITY DEFINER,
--     search_path fixo (precedente da V9_2), EXECUTE só para
--     ziipsan_app. Devolve só tenant_id + hostname + status: nunca o
--     hash do token nem dados do certificado.
--   * `public.tenant_brand_by_host(TEXT)`: a mesma projeção pré-login
--     da V9_2 (`tenant_brand_by_slug`), resolvendo o tenant pelo
--     domínio próprio em vez do slug. O controller tenta o domínio
--     próprio ANTES do slug: sem isso, um host como
--     `portal.cliente.example` cairia no slug `portal` de outro tenant.
--     O slug do catálogo segue na projeção porque as URLs de logo
--     usam o slug (`/tenant-brand/{slug}/logo/{assetId}`).
--   * Parâmetro `brand.custom_domain` (classe d, livre, padrão NULL) no
--     catálogo global: o serviço o espelha com o domínio próprio
--     VERIFIED do tenant (e o limpa na remoção, quando for igual).
--
-- RLS: `platform.tenant_domain` tem RLS ATIVADA com policy por tenant
-- (`tenant_id = public.current_tenant_id()`), como `tenant_settings` —
-- mas SEM FORCE, por um motivo provado, não por atalho: com FORCE, a
-- função SECURITY DEFINER pré-login NÃO consegue ler a tabela nem com
-- `SET LOCAL row_security = off` (o PostgreSQL recusa com "query would
-- be affected by row-level security policy" para dono sem BYPASSRLS —
-- verificado em PG 17 local durante a P2.27). Sem a leitura da função,
-- não haveria resolução do tenant pelo host antes do login, que é o
-- próprio objeto da tarefa. A propriedade que importa segue valendo e é
-- testada na IT: o papel da aplicação (ziipsan_app, sem BYPASSRLS) só
-- enxerga as linhas do tenant do contexto — zero linhas sem contexto.
-- A função é o único leitor em contexto de dono, e só devolve
-- tenant_id/hostname/status. Nenhum papel com BYPASSRLS é criado (o
-- security.sql deliberadamente não dá BYPASSRLS a ninguém).
--
-- O QUE ESTA VERSAO NAO FAZ (fica para as tarefas citadas):
--   * automação de certificados na AWS (LC.2): sem ACM, sem CloudFront
--     aqui; `CertificateIssuer` é a costura, `TestCertificateIssuer`
--     é o emissor local;
--   * remetente de e-mail com DKIM alinhado ao domínio (P3.5);
--   * chamada automática no encerramento do contrato: `releaseDomain`
--     existe no serviço e é o ponto que o ciclo de vida (P2.12–P2.14)
--     deve chamar — a borda (LC.2) deve derrubar o mapeamento quando
--     o status vira REMOVED;
--   * logo por domínio próprio sem slug: a marca pré-login por host
--     usa o slug do catálogo para as URLs de logo; tenant sem slug
--     vê a marca sem logos (mesma limitação documentada no serviço).
--
-- DADOS SINTETICOS APENAS: nenhuma linha é semeada. Os testes criam o
-- tenant, o domínio e as chaves de que precisam.

CREATE SCHEMA IF NOT EXISTS platform;

-- ======================================================================
-- platform.tenant_domain
-- ======================================================================

CREATE TABLE IF NOT EXISTS platform.tenant_domain (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    hostname TEXT NOT NULL CHECK (
        hostname = lower(hostname)
        AND char_length(hostname) <= 253
        AND hostname ~ '^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,}$'),
    status TEXT NOT NULL DEFAULT 'PENDING_VERIFICATION'
        CHECK (status IN ('PENDING_VERIFICATION', 'VERIFIED', 'REVOKED', 'REMOVED')),
    verification_token_hash CHAR(64) NOT NULL CHECK (verification_token_hash ~ '^[0-9a-f]{64}$'),
    cname_target TEXT NOT NULL,
    cname_ok BOOLEAN NOT NULL DEFAULT false,
    last_cname_check_at TIMESTAMPTZ,
    cert_status TEXT NOT NULL DEFAULT 'NONE'
        CHECK (cert_status IN ('NONE', 'PENDING', 'ISSUED', 'RENEWING', 'REVOKED', 'EXPIRED')),
    cert_provider TEXT,
    cert_issued_at TIMESTAMPTZ,
    cert_not_after TIMESTAMPTZ,
    cert_renew_after TIMESTAMPTZ,
    last_renewal_at TIMESTAMPTZ,
    renewal_failures INT NOT NULL DEFAULT 0 CHECK (renewal_failures >= 0),
    verified_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoked_reason TEXT CHECK (revoked_reason IS NULL
        OR revoked_reason IN ('CONTRACT_ENDED', 'KEY_COMPROMISE', 'ADMIN_REMOVED', 'ADMIN')),
    removed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE INDEX IF NOT EXISTS idx_tenant_domain_tenant
    ON platform.tenant_domain (tenant_id);

-- Unicidade parcial: só linhas ativas disputam o hostname. REMOVED libera
-- o nome — mas reativá-lo exige nova prova TXT (proteção contra tomada).
CREATE UNIQUE INDEX IF NOT EXISTS idx_tenant_domain_hostname_active
    ON platform.tenant_domain (hostname)
    WHERE status <> 'REMOVED';

ALTER TABLE platform.tenant_domain ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_domain_isolation ON platform.tenant_domain;
CREATE POLICY tenant_domain_isolation ON platform.tenant_domain
    USING (tenant_id = public.current_tenant_id())
    WITH CHECK (tenant_id = public.current_tenant_id());

GRANT SELECT, INSERT, UPDATE, DELETE ON platform.tenant_domain TO ziipsan_app;

COMMENT ON TABLE platform.tenant_domain IS
    'P2.27: dominio proprio do tenant (CNAME para a borda, prova TXT, certificado). RLS ativada sem FORCE: com FORCE a funcao SECURITY DEFINER pre-login nao leria a tabela (PG 17 recusa row_security=off para dono sem BYPASSRLS); o papel da aplicacao segue restrito pela policy.';

-- ======================================================================
-- public.tenant_domain_by_host(TEXT) -> tenant do domínio próprio
-- ======================================================================

CREATE OR REPLACE FUNCTION public.tenant_domain_by_host(p_host TEXT)
RETURNS TABLE(tenant_id UUID, hostname TEXT, status TEXT)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $fn$
DECLARE
    v_host TEXT;
BEGIN
    IF p_host IS NULL OR btrim(p_host) = '' THEN
        RETURN;
    END IF;
    v_host := lower(btrim(p_host));
    -- Literal IPv6 nunca é domínio próprio.
    IF v_host LIKE '[%' THEN
        RETURN;
    END IF;
    -- Porta descartada; ponto final da raiz descartado.
    v_host := split_part(v_host, ':', 1);
    v_host := rtrim(v_host, '.');
    IF v_host = '' THEN
        RETURN;
    END IF;
    -- Só domínio VERIFIED resolve; PENDING, REVOKED e REMOVED não servem
    -- conteúdo nem login (o controller cai no slug ou na marca do produto).
    -- Tenant inativo também não resolve (mesma regra da V9_2).
    RETURN QUERY
        SELECT d.tenant_id, d.hostname, d.status
          FROM platform.tenant_domain d
          JOIN public.tenant_catalog c ON c.id = d.tenant_id
         WHERE d.hostname = v_host
           AND d.status = 'VERIFIED'
           AND c.status = 'ACTIVE';
END;
$fn$;

REVOKE ALL ON FUNCTION public.tenant_domain_by_host(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.tenant_domain_by_host(TEXT) TO ziipsan_app;

COMMENT ON FUNCTION public.tenant_domain_by_host(TEXT) IS
    'P2.27: tenant do dominio proprio VERIFIED pelo host, antes do login. So tenant_id/hostname/status — nunca hash do token nem dado do certificado.';

-- ======================================================================
-- public.tenant_brand_by_host(TEXT) -> marca pré-login pelo domínio
-- ======================================================================

CREATE OR REPLACE FUNCTION public.tenant_brand_by_host(p_host TEXT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $fn$
DECLARE
    v_tenant_id UUID;
    v_hostname TEXT;
    v_settings JSONB;
    v_logo_id UUID;
    v_logo_dark_id UUID;
    v_logo JSONB;
    v_logo_dark JSONB;
BEGIN
    IF p_host IS NULL OR btrim(p_host) = '' THEN
        RETURN NULL;
    END IF;
    SELECT tenant_id, hostname INTO v_tenant_id, v_hostname
      FROM public.tenant_domain_by_host(p_host);
    IF v_tenant_id IS NULL THEN
        RETURN NULL;
    END IF;
    -- Mesmo mecanismo da V9_2: GUC local à transação para que as policies
    -- FORCED de tenant_settings e tenant_assets vejam só este tenant.
    PERFORM set_config('app.current_tenant', v_tenant_id::text, true);
    SELECT jsonb_object_agg(setting_key, setting_value) INTO v_settings
      FROM public.tenant_settings
     WHERE tenant_id = v_tenant_id
       AND starts_with(setting_key, 'branding.')
       AND is_sensitive = false;
    IF v_settings IS NULL THEN
        v_settings := '{}'::jsonb;
    END IF;
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
        'hostname', v_hostname,
        'slug', (SELECT subdomain_slug FROM public.tenant_catalog WHERE id = v_tenant_id),
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

REVOKE ALL ON FUNCTION public.tenant_brand_by_host(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.tenant_brand_by_host(TEXT) TO ziipsan_app;

COMMENT ON FUNCTION public.tenant_brand_by_host(TEXT) IS
    'P2.27: marca pre-login do tenant pelo dominio proprio VERIFIED. Mesma projecao fechada da V9_2 (so branding.* nao-sensiveis, so logo CLEAN). NULL = host sem dominio proprio VERIFIED.';

-- ======================================================================
-- Parâmetro brand.custom_domain (classe d, livre)
-- ======================================================================

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('brand.custom_domain', 'TEXT', 'd', NULL,
     'P2.27; PLT-05',
     NULL,
     'Dominio proprio do tenant (CNAME apontado para a borda do Ziip), com posse provada por registro TXT e certificado emitido. Classe d (livre): o valor efetivo e o do dominio VERIFIED em platform.tenant_domain, espelhado aqui pelo servico da P2.27; NULL = sem dominio proprio (o tenant usa o subdominio na base do produto, da P1.19).')
ON CONFLICT (definition_key) DO NOTHING;

GRANT SELECT ON public.parameter_definitions TO ziipsan_app;
