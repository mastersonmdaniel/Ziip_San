package br.com.ziipsan.platform.portal;

import br.com.ziipsan.core.api.ApiException;

/**
 * Acessibilidade do portal (P0.36 / PLT-31 + P3.3): o criterio e WCAG 2.2
 * AA (axe sem violacao {@code serious} ou {@code critical} — a prova e do
 * frontend/CI, nao desta classe). Daqui saem as regras de negocio:
 * declaracao de acessibilidade do tenant publicada
 * ({@code portal.accessibility_statement}, classe d) e o widget VLibras
 * ({@code portal.vlibras_enabled}, classe d), que so pode ser ligado
 * DEPOIS da consulta formal aos termos do servico — sem a confirmacao da
 * consulta, ligar e recusado.
 */
public final class PortalAccessibility {

    private PortalAccessibility() { }

    /** VLibras ligado exige (a) parametro ligado e (b) consulta formal aos termos registrada. */
    public static boolean vlibrasAllowed(PortalParameters params, boolean formalTermsConsultationDone) {
        return params.vlibrasEnabled() && formalTermsConsultationDone;
    }

    public static void assertVlibrasAllowed(PortalParameters params, boolean formalTermsConsultationDone) {
        if (params.vlibrasEnabled() && !formalTermsConsultationDone) {
            throw new ApiException.Forbidden(
                    "VLibras so pode ser ligado depois da consulta formal aos termos do servico.");
        }
    }

    /** Declaracao publicada do tenant (contato + data da avaliacao validados no record). */
    public static PortalParameters.AccessibilityStatement publishedStatement(PortalParameters params) {
        return params.accessibilityStatement();
    }
}
