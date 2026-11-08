package br.com.ziipsan.core.effluent;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BodEfficiencyTest {

    private static final LocalDate START = LocalDate.of(2026, 3, 1);
    private static final LocalDate END = LocalDate.of(2026, 3, 31);

    @Test
    void knownRemovalEfficiency() {
        var result = BodEfficiency.assess(TestSupport.ETE_ID, START, END,
                List.of(new BodEfficiency.BodPair(new BigDecimal("200"), new BigDecimal("50"))));
        assertEquals("75.00", result.removalPercent().toPlainString());
        assertEquals("200.00", result.meanInfluent().toPlainString());
        assertEquals("50.00", result.meanEffluent().toPlainString());
        assertEquals(1, result.pairCount());
    }

    @Test
    void meansOverSeveralPairs() {
        var result = BodEfficiency.assess(TestSupport.ETE_ID, START, END, List.of(
                new BodEfficiency.BodPair(new BigDecimal("200"), new BigDecimal("60")),
                new BodEfficiency.BodPair(new BigDecimal("300"), new BigDecimal("40"))));
        assertEquals("250.00", result.meanInfluent().toPlainString());
        assertEquals("50.00", result.meanEffluent().toPlainString());
        assertEquals("80.00", result.removalPercent().toPlainString());
    }

    @Test
    void negativeRemovalIsReportedNotHidden() {
        var result = BodEfficiency.assess(TestSupport.ETE_ID, START, END,
                List.of(new BodEfficiency.BodPair(new BigDecimal("100"), new BigDecimal("120"))));
        assertEquals("-20.00", result.removalPercent().toPlainString());
    }

    @Test
    void refusesPeriodWithoutPairsAndInvalidPairs() {
        assertThrows(IllegalArgumentException.class,
                () -> BodEfficiency.assess(TestSupport.ETE_ID, START, END, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new BodEfficiency.BodPair(BigDecimal.ZERO, new BigDecimal("1")));
        assertThrows(IllegalArgumentException.class,
                () -> new BodEfficiency.BodPair(new BigDecimal("10"), new BigDecimal("-1")));
    }
}
