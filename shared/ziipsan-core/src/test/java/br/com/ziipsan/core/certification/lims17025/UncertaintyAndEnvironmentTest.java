package br.com.ziipsan.core.certification.lims17025;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Incerteza com fator k e regra de decisao + condicoes ambientais. */
class UncertaintyAndEnvironmentTest {

    @Test void expandedUncertaintyIsUTimesK() {
        MeasurementUncertainty u = TestSupport.uncertainty(); // u=0,5 k=2
        assertEquals(new BigDecimal("1.0"), u.expandedUncertainty());
        // k e u sao declarados: zero/negativo recusado, k ausente recusado
        assertThrows(IllegalArgumentException.class, () -> new MeasurementUncertainty(
                TestSupport.METHOD, TestSupport.PARAM, BigDecimal.ZERO, new BigDecimal("2"),
                MeasurementUncertainty.DecisionRule.GUARD_BAND, "RT-1"));
        assertThrows(NullPointerException.class, () -> new MeasurementUncertainty(
                TestSupport.METHOD, TestSupport.PARAM, new BigDecimal("0.5"), null,
                MeasurementUncertainty.DecisionRule.GUARD_BAND, "RT-1"));
        assertThrows(NullPointerException.class, () -> new MeasurementUncertainty(
                TestSupport.METHOD, TestSupport.PARAM, new BigDecimal("0.5"), new BigDecimal("2"),
                null, "RT-1"));
    }

    @Test void guardBandStatements() {
        MeasurementUncertainty u = TestSupport.uncertainty(); // U = 1,0
        BigDecimal limit = new BigDecimal("10");
        // 8,5 + 1,0 = 9,5 <= 10: conforme
        assertEquals(MeasurementUncertainty.Statement.CONFORMS,
                u.statementForMax(new BigDecimal("8.5"), limit));
        // 9,5: valor dentro, mas valor+U = 10,5 cruza o limite: indeterminado
        assertEquals(MeasurementUncertainty.Statement.INDETERMINATE,
                u.statementForMax(new BigDecimal("9.5"), limit));
        // 11,5 - 1,0 = 10,5 > 10: nao conforme
        assertEquals(MeasurementUncertainty.Statement.DOES_NOT_CONFORM,
                u.statementForMax(new BigDecimal("11.5"), limit));
        // limite minimo com banda de guarda
        assertEquals(MeasurementUncertainty.Statement.CONFORMS,
                u.statementForMin(new BigDecimal("11.5"), limit));
        assertEquals(MeasurementUncertainty.Statement.INDETERMINATE,
                u.statementForMin(new BigDecimal("10.5"), limit));
    }

    @Test void simpleAcceptanceIgnoresTheBand() {
        MeasurementUncertainty u = new MeasurementUncertainty(TestSupport.METHOD, TestSupport.PARAM,
                new BigDecimal("0.5"), new BigDecimal("2"),
                MeasurementUncertainty.DecisionRule.SIMPLE_ACCEPTANCE, "RT-1");
        assertEquals(MeasurementUncertainty.Statement.CONFORMS,
                u.statementForMax(new BigDecimal("9.5"), new BigDecimal("10")));
        assertEquals(MeasurementUncertainty.Statement.DOES_NOT_CONFORM,
                u.statementForMax(new BigDecimal("10.5"), new BigDecimal("10")));
    }

    @Test void methodValidationCoherence() {
        // LQ < LD e dossie incoerente
        assertThrows(IllegalArgumentException.class, () -> MethodValidation.validated(
                TestSupport.enabled(), TestSupport.METHOD, TestSupport.PARAM,
                new BigDecimal("0.10"), new BigDecimal("0.05"), "DOSSIE", "RT-1",
                LocalDate.of(2026, 2, 1)));
        // LD/LQ ambos ausentes sao aceitos (o dossie e a prova, nao os campos)
        MethodValidation v = MethodValidation.validated(TestSupport.enabled(), TestSupport.METHOD,
                TestSupport.PARAM, null, null, "DOSSIE", "RT-1", LocalDate.of(2026, 2, 1));
        assertNull(v.detectionLimit());
    }

    @Test void environmentalVerdicts() {
        EnvironmentalControl.Evaluation within = TestSupport.envWithin();
        assertEquals(EnvironmentalControl.Verdict.WITHIN_LIMITS, within.verdict());
        EnvironmentalControl.Evaluation out = TestSupport.envOut();
        assertEquals(EnvironmentalControl.Verdict.OUT_OF_LIMITS, out.verdict());
        assertFalse(out.violations().isEmpty());
        // sem requisito declarado: NOT_DECLARED, nunca conformidade presumida
        EnvironmentalControl.Evaluation notDeclared = EnvironmentalControl.evaluate(null,
                new EnvironmentalControl.Reading(new BigDecimal("99"), null, TestSupport.T0, "TEC-1"));
        assertEquals(EnvironmentalControl.Verdict.NOT_DECLARED, notDeclared.verdict());
        // grandeza exigida e nao medida: fora dos limites
        EnvironmentalControl.Evaluation missing = EnvironmentalControl.evaluate(
                TestSupport.envRequirement(),
                new EnvironmentalControl.Reading(null, new BigDecimal("50"), TestSupport.T0, "TEC-1"));
        assertEquals(EnvironmentalControl.Verdict.OUT_OF_LIMITS, missing.verdict());
        // requisito sem nenhuma faixa nao e requisito
        assertThrows(IllegalArgumentException.class, () -> new EnvironmentalControl.Requirement(
                null, null, null, null, "RT-1"));
    }
}
