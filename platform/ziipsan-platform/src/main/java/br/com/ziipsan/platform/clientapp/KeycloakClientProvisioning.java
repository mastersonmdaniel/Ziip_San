package br.com.ziipsan.platform.clientapp;

import java.util.List;

/**
 * Especificacao do client publico que o pipeline cria por app no realm
 * {@code ziipsan-portal} (ED-39): login pelo navegador do sistema com
 * PKCE — nunca WebView embutida, nunca segredo no aparelho.
 *
 * <p>Regras travadas por construcao e por teste:
 * <ul>
 *   <li>client PUBLICO ({@code publicClient = true}), sem segredo;</li>
 *   <li>PKCE S256 OBRIGATORIO ({@code pkce.code.challenge.method});</li>
 *   <li>fluxo SOMENTE authorization code — implicit e direct access
 *       grants desligados;</li>
 *   <li>redirect URI SOMENTE o Universal Link/App Link do app
 *       ({@link ClientAppConfig#redirectUri()}): nenhum curinga, nenhum
 *       esquema customizado (deep link nao verificado nao recebe code);</li>
 *   <li>refresh token rotativo — o armazenamento seguro (Keychain no
 *       iOS, Keystore no Android, RNF-39) e do plugin nativo do app
 *       (ver {@code cliente/src/plugins/secureToken.ts}).</li>
 * </ul>
 * A criacao efetiva no Keycloak e passo do pipeline na implantacao
 * (Admin API com credencial de implantacao, fora do app e desta classe);
 * aqui vive o CONTRATO que o pipeline materializa e o CI confere.
 */
public final class KeycloakClientProvisioning {

    public static final String REALM = "ziipsan-portal";
    public static final String CODE_CHALLENGE_METHOD = "S256";

    private KeycloakClientProvisioning() {
    }

    /** A especificacao completa do client de um app configurado. */
    public record ClientSpec(
            String realm,
            String clientId,
            boolean publicClient,
            boolean standardFlowEnabled,
            boolean implicitFlowEnabled,
            boolean directAccessGrantsEnabled,
            String pkceCodeChallengeMethod,
            List<String> redirectUris,
            List<String> webOrigins) {
        public ClientSpec {
            if (!REALM.equals(realm)) {
                throw new IllegalArgumentException("o app do cliente usa o realm " + REALM);
            }
            if (!publicClient) {
                throw new IllegalArgumentException("client do app do cliente e publico (sem segredo no aparelho)");
            }
            if (!standardFlowEnabled || implicitFlowEnabled || directAccessGrantsEnabled) {
                throw new IllegalArgumentException(
                        "client do app so aceita authorization code: implicit e direct grants desligados");
            }
            if (!CODE_CHALLENGE_METHOD.equals(pkceCodeChallengeMethod)) {
                throw new IllegalArgumentException("PKCE S256 e obrigatorio no client do app");
            }
            if (redirectUris == null || redirectUris.size() != 1
                    || redirectUris.get(0).contains("*") || !redirectUris.get(0).startsWith("https://")) {
                throw new IllegalArgumentException(
                        "redirect URI do app e somente o Universal Link/App Link https, sem curinga");
            }
            redirectUris = List.copyOf(redirectUris);
            webOrigins = webOrigins == null ? List.of() : List.copyOf(webOrigins);
        }
    }

    /** Deriva a especificacao do client a partir da configuracao do app. */
    public static ClientSpec specFor(ClientAppConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("configuracao do app e obrigatoria");
        }
        return new ClientSpec(
                REALM,
                config.keycloakClientId(),
                true,
                true,
                false,
                false,
                CODE_CHALLENGE_METHOD,
                List.of(config.redirectUri()),
                List.of());
    }
}
