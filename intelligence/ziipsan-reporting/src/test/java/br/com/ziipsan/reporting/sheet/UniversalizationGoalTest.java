package br.com.ziipsan.reporting.sheet;

import br.com.ziipsan.core.parameters.ParameterVersionRule;
import br.com.ziipsan.reporting.indicator.IndicatorValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniversalizationGoalTest {

    private ParameterVersionRule.Validity validity() {
        return new ParameterVersionRule.Validity(LocalDate.of(2026, 1, 1), null);
    }

    private UniversalizationGoal goal(int year, UniversalizationGoal.Service service, String pct,
                                      boolean consent) {
        return new UniversalizationGoal("MUN-SINT", year, service, new BigDecimal(pct),
                UniversalizationGoal.Origin.CONTRACT, consent, validity());
    }

    @Test
    void pisoFederal2033Travado() {
        // 99% agua / 90% esgoto em 2033: no piso passa, abaixo recusa.
        goal(2033, UniversalizationGoal.Service.WATER, "99", false);
        goal(2033, UniversalizationGoal.Service.SEWAGE, "90", false);
        IndicatorValidationException e = assertThrows(IndicatorValidationException.class,
                () -> goal(2033, UniversalizationGoal.Service.WATER, "98.9", false));
        assertEquals("GOAL_BELOW_FEDERAL_FLOOR", e.code());
        assertThrows(IndicatorValidationException.class,
                () -> goal(2033, UniversalizationGoal.Service.SEWAGE, "89", false));
    }

    @Test
    void prorrogacaoSoComAnuenciaEAte2040() {
        assertThrows(IndicatorValidationException.class,
                () -> goal(2035, UniversalizationGoal.Service.WATER, "99", false));
        goal(2040, UniversalizationGoal.Service.WATER, "99", true);
        IndicatorValidationException e = assertThrows(IndicatorValidationException.class,
                () -> goal(2041, UniversalizationGoal.Service.WATER, "99", true));
        assertEquals("GOAL_YEAR_BEYOND_EXTENSION_LIMIT", e.code());
    }

    @Test
    void origemEVigenciaObrigatorias() {
        assertThrows(IndicatorValidationException.class, () -> new UniversalizationGoal(
                "M", 2030, UniversalizationGoal.Service.WATER, new BigDecimal("50"), null, false, validity()));
        assertThrows(IndicatorValidationException.class, () -> new UniversalizationGoal(
                "M", 2030, UniversalizationGoal.Service.WATER, new BigDecimal("50"),
                UniversalizationGoal.Origin.NR, false, null));
    }

    @Test
    void verificacaoTresDeCincoAnos() {
        List<UniversalizationAssessment.YearResult> cumpridos3 = List.of(
                new UniversalizationAssessment.YearResult(2029, new BigDecimal("50"), new BigDecimal("50")),
                new UniversalizationAssessment.YearResult(2030, new BigDecimal("60"), new BigDecimal("55")),
                new UniversalizationAssessment.YearResult(2031, new BigDecimal("40"), new BigDecimal("60")),
                new UniversalizationAssessment.YearResult(2032, new BigDecimal("80"), new BigDecimal("70")),
                new UniversalizationAssessment.YearResult(2033, new BigDecimal("90"), new BigDecimal("99")));
        UniversalizationAssessment.WindowVerdict v = UniversalizationAssessment.verifyFiveYearWindow(cumpridos3);
        assertEquals(3, v.yearsMet());
        assertTrue(v.fulfilled());
        List<UniversalizationAssessment.YearResult> cumpridos2 = List.of(
                new UniversalizationAssessment.YearResult(2029, new BigDecimal("50"), new BigDecimal("50")),
                new UniversalizationAssessment.YearResult(2030, new BigDecimal("60"), new BigDecimal("55")),
                new UniversalizationAssessment.YearResult(2031, new BigDecimal("40"), new BigDecimal("60")),
                new UniversalizationAssessment.YearResult(2032, new BigDecimal("60"), new BigDecimal("70")),
                new UniversalizationAssessment.YearResult(2033, new BigDecimal("90"), new BigDecimal("99")));
        assertFalse(UniversalizationAssessment.verifyFiveYearWindow(cumpridos2).fulfilled());
        assertThrows(IndicatorValidationException.class,
                () -> UniversalizationAssessment.verifyFiveYearWindow(cumpridos3.subList(0, 4)));
    }

    @Test
    void trajetoriaLigaDomiciliosPorAnoComTeto() {
        // Faltam 950 domicilios em 5 anos = 190/ano; com resto, teto: 951/5 = 191.
        assertEquals(190, UniversalizationAssessment.trajectoryPerYear(1000, 50, 5));
        assertEquals(191, UniversalizationAssessment.trajectoryPerYear(1001, 50, 5));
        assertEquals(0, UniversalizationAssessment.trajectoryPerYear(100, 150, 5));
        assertThrows(IndicatorValidationException.class,
                () -> UniversalizationAssessment.trajectoryPerYear(100, 0, 0));
    }
}
