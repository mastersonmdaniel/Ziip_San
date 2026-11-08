package br.com.ziipsan.reporting.sheet;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

/**
 * Perfil de cobertura de uma agencia (RAS-33). A referencia normativa e a
 * nomeada no acceptance; a REGRA de solucao alternativa nasce UNDECLARED
 * ([SEM FONTE] nesta VM para o sentido) e so vira regra por declaracao
 * explicita na integracao, com a referencia registrada.
 */
public record AgencyCoverageProfile(String agencyCode, String normativeReference,
                                    AltSolutionRule altSolutionRule) {
    public AgencyCoverageProfile {
        if (agencyCode == null || agencyCode.isBlank()) {
            throw new IndicatorValidationException("AGENCY_CODE_REQUIRED", "perfil sem agencia");
        }
        if (normativeReference == null || normativeReference.isBlank()) {
            throw new IndicatorValidationException("AGENCY_NORMATIVE_REFERENCE_REQUIRED",
                    "perfil " + agencyCode + " sem a referencia normativa do acceptance (RAS-33)");
        }
        if (altSolutionRule == null) {
            altSolutionRule = AltSolutionRule.UNDECLARED;
        }
    }

    /** Perfis nomeados no acceptance (RAS-33), todos com a regra por declarar. */
    public static AgencyCoverageProfile aris() {
        return new AgencyCoverageProfile("ARIS", "ARIS RN 47/2025", AltSolutionRule.UNDECLARED);
    }

    public static AgencyCoverageProfile agir() {
        return new AgencyCoverageProfile("AGIR", "AGIR RN 024/2025", AltSolutionRule.UNDECLARED);
    }

    public static AgencyCoverageProfile aresc() {
        return new AgencyCoverageProfile("ARESC", "ARESC Res. 431/2026", AltSolutionRule.UNDECLARED);
    }
}
