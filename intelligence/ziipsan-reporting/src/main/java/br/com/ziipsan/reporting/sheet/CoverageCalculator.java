package br.com.ziipsan.reporting.sheet;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Calculo de cobertura e atendimento por municipio sobre a base do IBGE
 * (aceite da ED-77): IAA (agua) e ICA (coleta de esgoto) =
 * domicilios atendidos / domicilios totais do municipio x 100, com a
 * separacao urbano/rural da mesma base. A solucao alternativa so entra no
 * numerador quando a regra DECLARADA do perfil da agencia manda contar
 * (RAS-33); perfil sem regra declarada recusa o calculo - nunca presume.
 * Massa dos testes e sintetica declarada; nenhum domicilio real aqui.
 */
public final class CoverageCalculator {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

    public record Coverage(BigDecimal iaaPercent, BigDecimal icaPercent,
                           ZoneSplit waterSplit, ZoneSplit sewageSplit) {
    }

    private CoverageCalculator() {
    }

    public static Coverage compute(IbgeHouseholdBase base, String municipalityCode,
                                   long waterServedHouseholds, long sewageCollectedHouseholds,
                                   long altSolutionHouseholds,
                                   AgencyCoverageProfile profile) {
        IbgeHouseholdBase.HouseholdEntry entry = base.require(municipalityCode);
        if (profile == null) {
            throw new IndicatorValidationException("COVERAGE_PROFILE_REQUIRED",
                    "cobertura de " + municipalityCode + " sem perfil de agencia (RAS-33)");
        }
        if (profile.altSolutionRule() == AltSolutionRule.UNDECLARED) {
            throw new IndicatorValidationException("ALT_SOLUTION_RULE_UNDECLARED",
                    "regra de solucao alternativa nao declarada para " + profile.agencyCode()
                            + " (" + profile.normativeReference() + ") [SEM FONTE]: "
                            + "cobertura recusada em vez de presumir o computo (RAS-33)");
        }
        long altCounted = profile.altSolutionRule() == AltSolutionRule.COUNTS_AS_COVERED
                ? altSolutionHouseholds : 0L;
        long waterNumerator = waterServedHouseholds + altCounted;
        if (waterNumerator > entry.totalHouseholds() || sewageCollectedHouseholds > entry.totalHouseholds()) {
            throw new IndicatorValidationException("COVERAGE_NUMERATOR_EXCEEDS_BASE",
                    "atendidos excedem os domicilios da base IBGE em " + municipalityCode);
        }
        BigDecimal iaa = percent(waterNumerator, entry.totalHouseholds());
        BigDecimal ica = percent(sewageCollectedHouseholds, entry.totalHouseholds());
        // Rateio urbano/rural: a mesma proporcao da base (declarado: a base e
        // o unico recorte zonal com fonte; recorte zonal do atendimento e
        // dado primario do tenant na integracao, nao presumido aqui alem
        // da proporcao da base).
        ZoneSplit waterSplit = split(municipalityCode, iaa, entry);
        ZoneSplit sewageSplit = split(municipalityCode, ica, entry);
        return new Coverage(iaa, ica, waterSplit, sewageSplit);
    }

    static BigDecimal percent(long numerator, long denominator) {
        if (denominator <= 0) {
            throw new IndicatorValidationException("COVERAGE_BASE_EMPTY",
                    "base IBGE sem domicilios: percentual indefinido, nunca zero forcado");
        }
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), MC);
    }

    private static ZoneSplit split(String municipalityCode, BigDecimal totalPercent,
                                   IbgeHouseholdBase.HouseholdEntry entry) {
        if (entry.totalHouseholds() == 0) {
            return new ZoneSplit(municipalityCode, totalPercent, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal urban = totalPercent.multiply(BigDecimal.valueOf(entry.urbanHouseholds()))
                .divide(BigDecimal.valueOf(entry.totalHouseholds()), MC);
        BigDecimal rural = totalPercent.subtract(urban);
        return new ZoneSplit(municipalityCode, totalPercent, urban, rural);
    }
}
