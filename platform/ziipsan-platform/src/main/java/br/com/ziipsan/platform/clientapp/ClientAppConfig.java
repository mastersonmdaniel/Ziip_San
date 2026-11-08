package br.com.ziipsan.platform.clientapp;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Configuracao do app do cliente de UM tenant numa plataforma (ED-39;
 * core {@code V11_1}, {@code platform.client_app_config}; ADR-0026).
 *
 * <p>O identificador do app e gerado pelo pipeline a partir do slug do
 * tenant — {@link #appIdentifierFor(String, ClientAppPlatform)} e a UNICA
 * forma de deriva-lo, para o pipeline (TypeScript) e o servidor gerarem
 * o mesmo valor: prefixo do produto + slug. O slug e dado do cadastro do
 * tenant (P1.19), nunca inventado aqui; slug fora do padrao e recusado.
 *
 * <p>Coerencia recusada no construtor (espelho dos CHECKs da V11_1):
 * PUBLISHED exige versao publicada, build publicado e conta de loja
 * (correcao D5 da revisao: na versao original o Java aceitava
 * PUBLISHED sem build, estado que o servico nunca produz mas o record
 * permitia construir — o CHECK do SQL agora exige os tres, como o
 * Java); versao publicada, quando ha, e semver {@code x.y.z} nos dois
 * lados (idem D5: o SQL ganhou o mesmo regex); build, quando ha, e
 * positivo nos dois lados; a cor, quando ha, e hex {@code #RRGGBB}
 * (a cor gravada da marca, P1.19/P3.21); o host do Universal Link/App
 * Link e o client do Keycloak sao obrigatorios — sem eles o login por
 * PKCE nao tem retorno verificado.
 */
public record ClientAppConfig(
        UUID tenantId,
        ClientAppPlatform platform,
        String appIdentifier,
        String displayName,
        String keycloakClientId,
        String storeAccountRef,
        ClientAppStatus status,
        String publishedVersion,
        Integer publishedBuild,
        String universalLinkHost,
        String tenantBaseUrl,
        UUID iconAssetId,
        String primaryColor) {

    /** Prefixo do identificador gerado pelo pipeline (convencao desta entrega, declarada no relatorio). */
    public static final String APP_ID_PREFIX = "br.com.ziipsan.cliente.";

    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9]*(\\.[a-z0-9]+(-[a-z0-9]+)*)+");
    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");
    private static final Pattern SEMVER = Pattern.compile("\\d+\\.\\d+\\.\\d+");

    public ClientAppConfig {
        if (tenantId == null || platform == null || status == null) {
            throw new IllegalArgumentException("tenant, plataforma e estado sao obrigatorios");
        }
        if (appIdentifier == null || !IDENTIFIER.matcher(appIdentifier).matches()) {
            throw new IllegalArgumentException("identificador do app invalido: " + appIdentifier);
        }
        if (displayName == null || displayName.isBlank() || displayName.length() > 120) {
            throw new IllegalArgumentException("nome de exibicao do app obrigatorio (ate 120 caracteres)");
        }
        if (keycloakClientId == null || keycloakClientId.isBlank()) {
            throw new IllegalArgumentException("client do Keycloak do app e obrigatorio");
        }
        if (universalLinkHost == null || universalLinkHost.isBlank() || universalLinkHost.contains("://")
                || universalLinkHost.contains("/")) {
            throw new IllegalArgumentException("host do Universal Link/App Link invalido: " + universalLinkHost);
        }
        if (tenantBaseUrl == null || !tenantBaseUrl.startsWith("https://")) {
            throw new IllegalArgumentException("base do tenant deve ser https: " + tenantBaseUrl);
        }
        if (primaryColor != null && !HEX_COLOR.matcher(primaryColor).matches()) {
            throw new IllegalArgumentException("cor primaria deve ser #RRGGBB: " + primaryColor);
        }
        if (publishedVersion != null && !SEMVER.matcher(publishedVersion).matches()) {
            throw new IllegalArgumentException("versao publicada deve ser semver x.y.z: " + publishedVersion);
        }
        if (publishedBuild != null && publishedBuild <= 0) {
            throw new IllegalArgumentException("build publicado deve ser positivo: " + publishedBuild);
        }
        if (status == ClientAppStatus.PUBLISHED
                && (publishedVersion == null || publishedBuild == null
                        || storeAccountRef == null || storeAccountRef.isBlank())) {
            throw new IllegalArgumentException(
                    "app PUBLISHED exige versao publicada, build publicado e conta de loja registradas");
        }
    }

    /** O identificador que o pipeline gera para o tenant nesta plataforma. */
    public static String appIdentifierFor(String tenantSlug, ClientAppPlatform platform) {
        if (tenantSlug == null || !SLUG.matcher(tenantSlug).matches()) {
            throw new IllegalArgumentException("slug do tenant invalido para gerar o app: " + tenantSlug);
        }
        // O mesmo identificador nas duas plataformas: o bundle id do iOS e o
        // package name do Android nascem da mesma base (pratica das lojas
        // para o mesmo produto); a unicidade e por (plataforma, identificador).
        return APP_ID_PREFIX + tenantSlug.toLowerCase(Locale.ROOT);
    }

    /** O client publico que o pipeline cria no realm {@code ziipsan-portal} para este app. */
    public static String keycloakClientIdFor(String tenantSlug) {
        if (tenantSlug == null || !SLUG.matcher(tenantSlug).matches()) {
            throw new IllegalArgumentException("slug do tenant invalido para gerar o client: " + tenantSlug);
        }
        return "ziipsan-cliente-" + tenantSlug.toLowerCase(Locale.ROOT);
    }

    /** URI de retorno do login (App Link no Android, Universal Link no iOS). */
    public String redirectUri() {
        return "https://" + universalLinkHost + "/auth/callback";
    }
}
