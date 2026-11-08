package br.com.ziipsan.platform.clientapp;

import java.util.Objects;
import java.util.UUID;

/**
 * Regras de geracao e de publicacao da configuracao do app do cliente
 * (ED-39), sobre a core {@code V11_1}:
 *
 * <ul>
 *   <li><b>Geracao pelo pipeline:</b> {@link #draftFor} monta o rascunho
 *       com identificador e client derivados do slug (funcoes unicas de
 *       {@link ClientAppConfig}), nome/cores/icone da marca do tenant
 *       (P1.19/P3.21, recebidos prontos — esta classe nao le marca);</li>
 *   <li><b>Publicacao (PLT-33 da ED-18 aplicada por tenant):</b> submeter
 *       exige a conta de loja da plataforma registrada no parametro do
 *       prestador (sem conta, sem submissao) e a conferencia de
 *       acessibilidade SG-782 completa ({@link AccessibilityGate});</li>
 *   <li><b>Registro da publicacao:</b> so a confirmacao da loja move
 *       para PUBLISHED, com versao e build informados pela loja — esta
 *       classe nao afirma publicacao: quem chama registra o fato.</li>
 * </ul>
 * Persistencia (JDBC/JPA sobre a V11_1) e a chamada as lojas sao da
 * integracao/pipeline; aqui vivem as regras, puras e testaveis.
 */
public final class ClientAppConfigService {

    private final ClientAppParameters parameters;

    public ClientAppConfigService(ClientAppParameters parameters) {
        this.parameters = Objects.requireNonNull(parameters, "parametros sao obrigatorios");
    }

    /** Dados da marca do tenant que o pipeline aplica ao app (da P1.19/P3.21). */
    public record TenantBranding(String displayName, UUID iconAssetId, String primaryColor) {
        public TenantBranding {
            if (displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("nome da marca do tenant e obrigatorio para gerar o app");
            }
        }
    }

    /** Rascunho gerado pelo pipeline para (tenant, plataforma). */
    public ClientAppConfig draftFor(UUID tenantId, String tenantSlug, ClientAppPlatform platform,
                                    TenantBranding branding, String universalLinkHost, String tenantBaseUrl) {
        Objects.requireNonNull(branding, "marca do tenant e obrigatoria");
        return new ClientAppConfig(
                tenantId,
                platform,
                ClientAppConfig.appIdentifierFor(tenantSlug, platform),
                branding.displayName(),
                ClientAppConfig.keycloakClientIdFor(tenantSlug),
                storeAccountFor(platform).orElse(null),
                ClientAppStatus.DRAFT,
                null,
                null,
                universalLinkHost,
                tenantBaseUrl,
                branding.iconAssetId(),
                branding.primaryColor());
    }

    /**
     * Submissao a loja: exige conta da plataforma e acessibilidade SG-782.
     * Devolve a configuracao em IN_REVIEW (o fato "submetido"); a
     * aprovacao e registrada por {@link #markPublished}.
     */
    public ClientAppConfig submitForReview(ClientAppConfig config, AccessibilityGate.Checklist a11y) {
        requireStatus(config, ClientAppStatus.DRAFT);
        String account = storeAccountFor(config.platform())
                .filter(a -> !a.isBlank())
                .orElseThrow(() -> new IllegalStateException(
                        "sem conta de loja do prestador registrada para " + config.platform()
                                + " (parametro da V11_1): publicacao recusada (PLT-33)"));
        AccessibilityGate.assertReleasable(a11y);
        return copy(config, account, ClientAppStatus.IN_REVIEW, config.publishedVersion(), config.publishedBuild());
    }

    /** Registro da aprovacao da loja, com a versao/build que a loja publicou. */
    public ClientAppConfig markPublished(ClientAppConfig config, String publishedVersion, Integer publishedBuild) {
        requireStatus(config, ClientAppStatus.IN_REVIEW);
        if (publishedBuild == null || publishedBuild <= 0) {
            throw new IllegalArgumentException("build publicado deve ser positivo");
        }
        // A validacao de semver e da conta acontece no construtor do record.
        return copy(config, config.storeAccountRef(), ClientAppStatus.PUBLISHED, publishedVersion, publishedBuild);
    }

    private java.util.Optional<String> storeAccountFor(ClientAppPlatform platform) {
        return platform == ClientAppPlatform.ANDROID
                ? parameters.androidStoreAccount()
                : parameters.iosStoreAccount();
    }

    private static void requireStatus(ClientAppConfig config, ClientAppStatus expected) {
        Objects.requireNonNull(config, "configuracao e obrigatoria");
        if (config.status() != expected) {
            throw new IllegalStateException(
                    "transicao exige estado " + expected + "; atual: " + config.status());
        }
    }

    private static ClientAppConfig copy(ClientAppConfig c, String account, ClientAppStatus status,
                                        String version, Integer build) {
        return new ClientAppConfig(c.tenantId(), c.platform(), c.appIdentifier(), c.displayName(),
                c.keycloakClientId(), account, status, version, build, c.universalLinkHost(),
                c.tenantBaseUrl(), c.iconAssetId(), c.primaryColor());
    }
}
