package br.com.ziipsan.commercial.fiscal.rules;

import br.com.ziipsan.commercial.fiscal.rules.TaxRateValidity.TaxRateRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Seleção de alíquota por vigência (P1.8; P2.4: sem sobreposição, ausente = erro). */
class TaxRateValidityTest {

    private static final List<TaxRateRow> ROWS = List.of(
            new TaxRateRow("CBS", "NATIONAL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), new BigDecimal("0.009")),
            new TaxRateRow("CBS", "NATIONAL", LocalDate.of(2027, 1, 1), null, new BigDecimal("0.020")));

    @Test
    void selectsRowCoveringCompetenceIncludingBoundaries() {
        assertEquals(new BigDecimal("0.009"),
                TaxRateValidity.rateFor(ROWS, "CBS", LocalDate.of(2026, 12, 31)));
        assertEquals(new BigDecimal("0.020"),
                TaxRateValidity.rateFor(ROWS, "CBS", LocalDate.of(2027, 1, 1)));
        assertEquals(new BigDecimal("0.020"),
                TaxRateValidity.rateFor(ROWS, "CBS", LocalDate.of(2030, 5, 20)));
    }

    @Test
    void missingRateIsExplicitError() {
        assertThrows(IllegalStateException.class,
                () -> TaxRateValidity.rateFor(ROWS, "CBS", LocalDate.of(2025, 6, 1)));
        assertThrows(IllegalStateException.class,
                () -> TaxRateValidity.rateFor(ROWS, "IBS", LocalDate.of(2026, 6, 1)));
    }

    @Test
    void overlapIsErrorNeverSilentChoice() {
        List<TaxRateRow> overlapping = List.of(
                new TaxRateRow("CBS", "NATIONAL", LocalDate.of(2026, 1, 1), null, new BigDecimal("0.009")),
                new TaxRateRow("CBS", "NATIONAL", LocalDate.of(2026, 6, 1), null, new BigDecimal("0.010")));
        assertThrows(IllegalStateException.class,
                () -> TaxRateValidity.rateFor(overlapping, "CBS", LocalDate.of(2026, 7, 1)));
    }

    @Test
    void informativeFlagFollowsTestYear() {
        assertEquals(true, TaxRateValidity.cbsIbsInformative(LocalDate.of(2026, 12, 31)));
        assertEquals(false, TaxRateValidity.cbsIbsInformative(LocalDate.of(2027, 1, 1)));
    }

    @Test
    void ibsTransitionScheduleResolvesPerYear() {
        // Valores de teste para a lógica de seleção; o plano não publica os percentuais
        // da transição do IBS de 2029 a 2032 (LC 214/2025, art. 544) e nenhum é inventado aqui.
        List<TaxRateRow> schedule = List.of(
                new TaxRateRow("IBS", "NATIONAL", LocalDate.of(2026, 1, 1), LocalDate.of(2028, 12, 31), new BigDecimal("0.001")),
                new TaxRateRow("IBS", "NATIONAL", LocalDate.of(2029, 1, 1), LocalDate.of(2029, 12, 31), new BigDecimal("0.011")),
                new TaxRateRow("IBS", "NATIONAL", LocalDate.of(2030, 1, 1), LocalDate.of(2030, 12, 31), new BigDecimal("0.012")),
                new TaxRateRow("IBS", "NATIONAL", LocalDate.of(2031, 1, 1), LocalDate.of(2031, 12, 31), new BigDecimal("0.013")),
                new TaxRateRow("IBS", "NATIONAL", LocalDate.of(2032, 1, 1), null, new BigDecimal("0.014")));
        assertEquals(new BigDecimal("0.001"), TaxRateValidity.rateFor(schedule, "IBS", LocalDate.of(2028, 12, 31)));
        assertEquals(new BigDecimal("0.011"), TaxRateValidity.rateFor(schedule, "IBS", LocalDate.of(2029, 6, 1)));
        assertEquals(new BigDecimal("0.012"), TaxRateValidity.rateFor(schedule, "IBS", LocalDate.of(2030, 6, 1)));
        assertEquals(new BigDecimal("0.013"), TaxRateValidity.rateFor(schedule, "IBS", LocalDate.of(2031, 6, 1)));
        assertEquals(new BigDecimal("0.014"), TaxRateValidity.rateFor(schedule, "IBS", LocalDate.of(2032, 1, 1)));
        assertEquals(new BigDecimal("0.014"), TaxRateValidity.rateFor(schedule, "IBS", LocalDate.of(2035, 6, 1)));
    }

    @Test
    void overlapDetectionHandlesOpenEndedIntervals() {
        assertEquals(true, TaxRateValidity.overlaps(
                LocalDate.of(2026, 1, 1), null, LocalDate.of(2026, 6, 1), null));
        assertEquals(true, TaxRateValidity.overlaps(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 31), null));
        assertEquals(false, TaxRateValidity.overlaps(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1), null));
    }
}
