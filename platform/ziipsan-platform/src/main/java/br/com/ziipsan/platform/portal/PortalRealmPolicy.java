package br.com.ziipsan.platform.portal;

import br.com.ziipsan.core.api.ApiException;

/**
 * Isolamento de realms (P3.3): o token do realm {@code ziipsan-portal} e
 * recusado nas rotas do backoffice e o token do backoffice e recusado nas
 * rotas do portal. A decisao usa SOMENTE o emissor ({@code iss}) do token:
 * o realm esta no sufixo da URL do emissor do Keycloak
 * ({@code .../realms/<realm>}). Nenhum outro claim e confiavel para isto.
 */
public final class PortalRealmPolicy {

    public static final String PORTAL_REALM = "ziipsan-portal";
    /** Client confidencial do BFF do portal (a audiencia esperada nas rotas do portal). */
    public static final String PORTAL_BFF_CLIENT = "ziipsan-portal-bff";

    private PortalRealmPolicy() { }

    public static boolean isPortalToken(String issuer) {
        return issuer != null && issuer.endsWith("/realms/" + PORTAL_REALM);
    }

    /** Rota do portal: so aceita token emitido pelo realm do portal. */
    public static void requirePortalToken(String issuer) {
        if (!isPortalToken(issuer)) {
            throw new ApiException.Forbidden("Token fora do realm do portal do consumidor.");
        }
    }

    /** Rota do backoffice: token do portal NUNCA acessa o backoffice; emissor sem realm nao e token de realm algum. */
    public static void requireBackofficeToken(String issuer) {
        if (issuer == null || issuer.isBlank() || isPortalToken(issuer) || !issuer.contains("/realms/")) {
            throw new ApiException.Forbidden("Token do portal nao acessa o backoffice.");
        }
    }
}
