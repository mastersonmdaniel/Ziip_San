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
}
