package br.com.ziipsan.platform.portal;

import br.com.ziipsan.core.api.ApiException;

import java.util.Locale;
import java.util.Set;

/**
 * Login gov.br (ADR-0043, P3.3):
 * <ul>
 *   <li>{@code portal.idp_govbr_enabled} (classe d) so e aceito para tenant
 *       cuja natureza juridica ({@code reg.legal_nature}) seja de direito
 *       PUBLICO — tenant privado com a flag ligada e recusado;</li>
 *   <li>quando a entrada e pelo gov.br, o BFF exige o nivel minimo de
 *       {@code portal.min_assurance_by_operation} (classe b) nas operacoes
 *       sensiveis (troca de titularidade, tarifa social, alteracao de
 *       contato, negociacao de debito): prata ou ouro pela fabrica;</li>
 *   <li>sem gov.br, valem as regras de vinculo do ADR-0022 (esta politica
 *       nao interfere na entrada local).</li>
 * </ul>
 * A integracao com o provedor gov.br (solicitacao do servico, dominio
 * GOV.BR, OIDC no Keycloak) e da ED-38: aqui vive a regra, nao o provedor.
 */
public final class GovBrPolicy {

    /**
     * Dominio real de {@code reg.legal_nature} (core V9, CHECK de
     * {@code platform.tenant_profile.legal_nature}; PARAMETROS §3 G1).
     * Conferido na revisao cruzada da P3.3 contra o espelho do cadastro e
     * contra a P3.19 ({@code TicketRetentionService.LEGAL_NATURES}).
     */
    public static final Set<String> LEGAL_NATURE_DOMAIN =
            Set.of("PRIVATE", "AUTARCHY", "PUBLIC_COMPANY", "SEM");

    /**
     * Valores de {@code reg.legal_nature} de direito publico: todo o
     * dominio exceto {@code PRIVATE} (mesmo criterio da P3.19,
     * {@code PUBLIC_LEGAL_NATURES}). Valor fora do dominio NAO e publico:
     * a configuracao do gov.br e recusada (falha fechada).
     */
    public static final Set<String> PUBLIC_LAW_VALUES =
            Set.of("AUTARCHY", "PUBLIC_COMPANY", "SEM");

    public enum EntryChannel { LOCAL, GOVBR }

    private GovBrPolicy() { }

    public static boolean isPublicLaw(String legalNature) {
        return legalNature != null && PUBLIC_LAW_VALUES.contains(legalNature.trim().toUpperCase(Locale.ROOT));
    }

    /** Ligar gov.br para tenant privado (ou sem natureza declarada) e recusado. */
    public static void assertGovBrConfigurationAllowed(String legalNature, PortalParameters params) {
        if (params.idpGovbrEnabled() && !isPublicLaw(legalNature)) {
            throw new ApiException.Forbidden(
                    "Login gov.br so e permitido para tenant de direito publico (reg.legal_nature).");
        }
    }

    /** Nivel do token gov.br a partir do claim de garantia (acr/selo), sem presumir formato do provedor. */
    public static PortalParameters.AssuranceLevel levelFromClaim(String claimValue) {
        if (claimValue == null) {
            return null;
        }
        String v = claimValue.toLowerCase(Locale.ROOT);
        if (v.contains("gold") || v.contains("ouro")) {
            return PortalParameters.AssuranceLevel.GOLD;
        }
        if (v.contains("silver") || v.contains("prata")) {
            return PortalParameters.AssuranceLevel.SILVER;
        }
        if (v.contains("bronze")) {
            return PortalParameters.AssuranceLevel.BRONZE;
        }
        return null;
    }

    /**
     * Exigencia por operacao quando a entrada e gov.br. Entrada local nao
     * passa por esta trava (ADR-0022). Nivel ausente/desconhecido = recusa.
     */
    public static void requireAssurance(PortalParameters.SensitiveOperation operation, EntryChannel entry,
                                        PortalParameters.AssuranceLevel tokenLevel, PortalParameters params) {
        if (entry != EntryChannel.GOVBR) {
            return;
        }
        PortalParameters.AssuranceLevel minimum = params.minAssurance(operation);
        if (tokenLevel == null || !tokenLevel.atLeast(minimum)) {
            throw new ApiException.Forbidden(
                    "Operacao exige nivel gov.br " + minimum + " ou superior.");
        }
    }
}
