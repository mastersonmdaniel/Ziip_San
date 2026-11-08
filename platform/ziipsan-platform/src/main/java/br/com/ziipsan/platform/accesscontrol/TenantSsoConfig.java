package br.com.ziipsan.platform.accesscontrol;

/**
 * Federação e SSO por ORGANIZAÇÃO do tenant (ED-06, ADR-0040 item 5):
 * <ul>
 *   <li>{@code sec.sso_mode}: {@code LOCAL} (só Keycloak), {@code OIDC},
 *       {@code SAML} ou {@code LDAP} (federação LDAP/AD do Keycloak);</li>
 *   <li>{@code sec.sso_enforced}: com {@code true}, o SSO é OBRIGATÓRIO —
 *       login local é recusado para os usuários da organização (exceto o
 *       acesso de emergência da P3.17, que é de PLATAFORMA, não do tenant);</li>
 *   <li>{@code sec.scim_enabled}: provisionamento SCIM sobre a API nativa do
 *       Keycloak, com escopo por organização — ver {@link ScimGuard}.</li>
 * </ul>
 * A configuração é POR ORGANIZAÇÃO (tenant): dois tenants no mesmo realm
 * podem ter provedores distintos, e a prova do aceite é exatamente essa.
 * Nenhum endpoint, entityId ou segredo de provedor vive neste record: isso é
 * configuração do Keycloak por organização, fora do código.
 */
public record TenantSsoConfig(SsoMode mode, boolean enforced, boolean scimEnabled) {

    public enum SsoMode { LOCAL, OIDC, SAML, LDAP }

    public TenantSsoConfig {
        if (mode == null) {
            throw new IllegalArgumentException("sec.sso_mode é obrigatório (LOCAL quando não há SSO)");
        }
        if (enforced && mode == SsoMode.LOCAL) {
            throw new IllegalArgumentException("sec.sso_enforced exige sec.sso_mode diferente de LOCAL");
        }
    }

    public static TenantSsoConfig localOnly() {
        return new TenantSsoConfig(SsoMode.LOCAL, false, false);
    }

    /** Login local permitido? Não quando o SSO é obrigatório e configurado. */
    public boolean localLoginAllowed() {
        return !(enforced && mode != SsoMode.LOCAL);
    }
}
