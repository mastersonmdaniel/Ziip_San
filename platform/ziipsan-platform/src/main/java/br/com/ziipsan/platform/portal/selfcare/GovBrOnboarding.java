package br.com.ziipsan.platform.portal.selfcare;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Login gov.br OPCIONAL no portal (ED-38, ADR-0043): a habilitacao do
 * provedor que a P3.3 declarou ser desta tarefa. A REGRA de quem pode
 * ligar e de nivel por operacao continua sendo da P3.3 ({@code GovBrPolicy}
 * e {@code PortalParameters}) — aqui vivem o CHECKLIST DE ELEGIBILIDADE do
 * aceite e o contrato OIDC do realm {@code ziipsan-portal}.
 *
 * <p>Naturezas juridicas: literais EXATOS conferidos na P3.3 contra o
 * CHECK de {@code platform.tenant_profile.legal_nature} —
 * {@code PRIVATE, AUTARCHY, PUBLIC_COMPANY, SEM}; direito publico = todos
 * exceto {@code PRIVATE} (mesmo criterio da P3.19). Nenhum literal novo.
 *
 * <p>Checklist do aceite, TODOS obrigatorios antes de ligar:
 * (1) integracao solicitada pelo orgao; (2) solicitacao por servico;
 * (3) sistema hospedado em dominio gov.br pelo dominio proprio da P2.27;
 * (4) municipio aderido a Rede Nacional de Governo Digital. Item faltante
 * ou nao conferido = ligacao recusada (falha fechada).
 *
 * <p>OIDC: intermediação pelo Keycloak, authorization code + PKCE S256,
 * client confidencial do BFF (o navegador nunca recebe token, P3.3).
 * Nivel minimo do provedor: {@code portal.idp_govbr_min_level} so aceita
 * SILVER ou GOLD; BRONZE como minimo do tenant e recusado.
 *
 * <p>O login LOCAL por CPF/CNPJ/e-mail da P3.3 e mantido: a opcao gov.br
 * e um canal ADICIONAL de entrada, nunca um substituto do local.
 * {@link #localLoginAvailable(Set)} DERIVA a disponibilidade do estado
 * real dos canais de entrada configurados no realm (correcao D4 da
 * revisao cruzada: antes era uma constante {@code true}, tautologica) —
 * disponivel somente quando o canal LOCAL esta configurado, esteja o
 * canal GOVBR ligado ou desligado (teste dedicado variando o estado).
 */
public final class GovBrOnboarding {

    public static final String REALM = "ziipsan-portal";
    public static final String CODE_CHALLENGE_METHOD = "S256";

    public static final Set<String> LEGAL_NATURE_DOMAIN =
            Set.of("PRIVATE", "AUTARCHY", "PUBLIC_COMPANY", "SEM");
    public static final Set<String> PUBLIC_LAW_VALUES =
            Set.of("AUTARCHY", "PUBLIC_COMPANY", "SEM");

    public enum MinLevel { SILVER, GOLD }

    /**
     * Canais de entrada do portal — literais EXATOS do
     * {@code GovBrPolicy.EntryChannel} da P3.3 (LOCAL, GOVBR). O conjunto
     * de canais efetivamente configurados no realm e o estado real do
     * qual a disponibilidade do login local deriva (correcao D4).
     */
    public enum EntryChannel { LOCAL, GOVBR }

    /** Converte os nomes crus dos canais configurados no realm; nome fora do dominio da P3.3 e recusado. */
    public static Set<EntryChannel> parseEntryChannels(Set<String> rawNames) {
        if (rawNames == null) {
            throw new IllegalArgumentException("canais de entrada configurados sao obrigatorios");
        }
        return rawNames.stream()
                .map(n -> EntryChannel.valueOf(n.trim().toUpperCase(Locale.ROOT)))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Checklist de elegibilidade do aceite (os 4 itens, todos obrigatorios). */
    public record EligibilityChecklist(boolean integrationRequestedByAgency,
                                       boolean requestedPerService,
                                       boolean hostedOnGovBrDomain,
                                       boolean municipalityInGovDigitalNetwork) {
        public boolean complete() {
            return integrationRequestedByAgency && requestedPerService
                    && hostedOnGovBrDomain && municipalityInGovDigitalNetwork;
        }
    }

    /** Contrato OIDC do IdP gov.br no realm do portal. */
    public record OidcBrokerConfig(String realm, String responseType, String codeChallengeMethod,
                                   boolean confidentialClient, boolean pkceRequired) {
        public OidcBrokerConfig {
            if (!REALM.equals(realm)) {
                throw new IllegalArgumentException("IdP gov.br so no realm " + REALM);
            }
            if (!"code".equals(responseType) || !CODE_CHALLENGE_METHOD.equals(codeChallengeMethod)
                    || !confidentialClient || !pkceRequired) {
                throw new IllegalArgumentException(
                        "OIDC gov.br exige authorization code + PKCE S256 em client confidencial do BFF");
            }
        }

        public static OidcBrokerConfig standard() {
            return new OidcBrokerConfig(REALM, "code", CODE_CHALLENGE_METHOD, true, true);
        }
    }

    private GovBrOnboarding() { }

    public static boolean isPublicLaw(String legalNature) {
        return legalNature != null && PUBLIC_LAW_VALUES.contains(legalNature.trim().toUpperCase(Locale.ROOT));
    }

    /** Nivel minimo do tenant: so SILVER ou GOLD; qualquer outro valor (incl. BRONZE) e recusado. */
    public static MinLevel parseMinLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("portal.idp_govbr_min_level ausente com gov.br ligado: recusado");
        }
        try {
            return MinLevel.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "portal.idp_govbr_min_level so aceita SILVER ou GOLD: recusado", e);
        }
    }

    /**
     * Ligacao da opcao: exige flag ligada, tenant de direito publico,
     * checklist completo, nivel minimo valido e contrato OIDC padrao.
     * Retorna o contrato vigente; qualquer falha recusa a ligacao.
     */
    public static OidcBrokerConfig enable(UUID tenantId, String legalNature, boolean flagEnabled,
                                          EligibilityChecklist checklist, String minLevelRaw) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant e obrigatorio");
        }
        if (!flagEnabled) {
            throw new IllegalStateException("portal.idp_govbr_enabled desligado: nada a ligar");
        }
        if (!isPublicLaw(legalNature)) {
            throw new IllegalStateException(
                    "Login gov.br so e permitido para tenant de direito publico (reg.legal_nature).");
        }
        if (checklist == null || !checklist.complete()) {
            throw new IllegalStateException(
                    "Checklist de elegibilidade gov.br incompleto: integracao do orgao, por servico, "
                            + "dominio gov.br (P2.27) e municipio na Rede Nacional de Governo Digital.");
        }
        parseMinLevel(minLevelRaw);
        return OidcBrokerConfig.standard();
    }

    /**
     * Login local da P3.3 (CPF/CNPJ/e-mail), DERIVADO do estado real:
     * disponivel somente quando o canal LOCAL esta entre os canais de
     * entrada configurados no realm. Ligar ou desligar o canal GOVBR nao
     * altera o resultado; um realm configurado SEM o canal LOCAL nao tem
     * login local (correcao D4 — nada de constante).
     */
    public static boolean localLoginAvailable(Set<EntryChannel> configuredChannels) {
        return configuredChannels != null && configuredChannels.contains(EntryChannel.LOCAL);
    }

    /**
     * A opcao gov.br aparece no portal somente quando ligada, elegivel E
     * com nivel minimo configurado valido (SILVER/GOLD): com minimo
     * invalido ou ausente, {@link #enable} recusaria a ligacao, entao a
     * opcao tambem nao aparece (correcao D4). Desligada ou inelegivel,
     * some sem afetar o login local.
     */
    public static boolean govbrOptionVisible(boolean flagEnabled, String legalNature,
                                             EligibilityChecklist checklist, String minLevelRaw) {
        if (!flagEnabled || !isPublicLaw(legalNature) || checklist == null || !checklist.complete()) {
            return false;
        }
        try {
            parseMinLevel(minLevelRaw);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
