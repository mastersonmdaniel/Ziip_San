package br.com.ziipsan.reporting.sheet;

import br.com.ziipsan.core.parameters.ParameterVersionRule;
import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Meta de universalizacao versionada por municipio e ano (GES-03; aceite da
 * ED-77), com origem declarada (contrato, NR, plano municipal ou a propria
 * lei federal). A meta federal da Lei 11.445, art. 11-B, e TRAVADA COMO PISO:
 * 99% de agua e 90% de coleta e tratamento de esgoto em 31/12/2033; meta
 * abaixo do piso para 2033 e recusada. Prorrogacao ate 2040 so com anuencia
 * da agencia registrada; alem de 2040, recusa. Valores sao os do acceptance
 * (fonte: Lei 11.445, art. 11-B) - nenhum outro percentual e de fabrica.
 */
public record UniversalizationGoal(String municipalityCode, int targetYear, Service service,
                                   BigDecimal targetPercent, Origin origin,
                                   boolean agencyConsentForExtension,
                                   ParameterVersionRule.Validity validity) {

    public enum Service { WATER, SEWAGE }
    public enum Origin { CONTRACT, NR, MUNICIPAL_PLAN, FEDERAL_LAW }

    public static final BigDecimal FEDERAL_WATER_FLOOR = new BigDecimal("99");
    public static final BigDecimal FEDERAL_SEWAGE_FLOOR = new BigDecimal("90");
    public static final int FEDERAL_DEADLINE_YEAR = 2033;
    public static final int EXTENSION_LIMIT_YEAR = 2040;
    public static final LocalDate FEDERAL_DEADLINE = LocalDate.of(2033, 12, 31);

    public UniversalizationGoal {
        if (municipalityCode == null || municipalityCode.isBlank()) {
            throw new IndicatorValidationException("GOAL_MUNICIPALITY_REQUIRED",
                    "meta de universalizacao sem municipio");
        }
        if (service == null) {
            throw new IndicatorValidationException("GOAL_SERVICE_REQUIRED",
                    "meta de " + municipalityCode + " sem servico (agua/esgoto)");
        }
        if (targetPercent == null || targetPercent.signum() < 0
                || targetPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IndicatorValidationException("GOAL_TARGET_INVALID",
                    "meta percentual invalida para " + municipalityCode + ": " + targetPercent);
        }
        if (origin == null) {
            throw new IndicatorValidationException("GOAL_ORIGIN_REQUIRED",
                    "meta de " + municipalityCode + " sem origem declarada (contrato, NR, "
                            + "plano municipal ou lei federal) - classe d, REG (GES-03)");
        }
        if (validity == null) {
            throw new IndicatorValidationException("GOAL_VALIDITY_REQUIRED",
                    "meta de " + municipalityCode + " sem vigencia (classe d, REG)");
        }
        if (targetYear > EXTENSION_LIMIT_YEAR) {
            throw new IndicatorValidationException("GOAL_YEAR_BEYOND_EXTENSION_LIMIT",
                    "ano-alvo " + targetYear + " alem do limite de prorrogacao 2040 "
                            + "(Lei 11.445, art. 11-B)");
        }
        if (targetYear > FEDERAL_DEADLINE_YEAR && !agencyConsentForExtension) {
            throw new IndicatorValidationException("GOAL_EXTENSION_WITHOUT_AGENCY_CONSENT",
                    "prorrogacao para " + targetYear + " exige anuencia da agencia registrada "
                            + "(Lei 11.445, art. 11-B)");
        }
        if (targetYear == FEDERAL_DEADLINE_YEAR) {
            BigDecimal floor = service == Service.WATER ? FEDERAL_WATER_FLOOR : FEDERAL_SEWAGE_FLOOR;
            if (targetPercent.compareTo(floor) < 0) {
                throw new IndicatorValidationException("GOAL_BELOW_FEDERAL_FLOOR",
                        "meta de " + service + " para 2033 (" + targetPercent.toPlainString()
                                + "%) abaixo do piso federal travado de " + floor.toPlainString()
                                + "% (Lei 11.445, art. 11-B)");
            }
        }
    }

    public static BigDecimal federalFloor(Service service) {
        return service == Service.WATER ? FEDERAL_WATER_FLOOR : FEDERAL_SEWAGE_FLOOR;
    }
}
