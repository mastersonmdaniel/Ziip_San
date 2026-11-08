package br.com.ziipsan.commercial.billing.saaspricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PriceSheetAndCalculatorTest {

    @Test void bandsMustStartAtZero() {
        assertThrows(IllegalArgumentException.class, () -> new PriceSheet("X",
                LocalDate.of(2026, 1, 1), null,
                List.of(new SizeBand("M", 10, 20)), List.of(), List.of()));
    }

    @Test void bandsWithGapRefused() {
        assertThrows(IllegalArgumentException.class, () -> new PriceSheet("X",
                LocalDate.of(2026, 1, 1), null,
                List.of(new SizeBand("P", 0, 100), new SizeBand("M", 102, 200)), List.of(), List.of()));
    }

    @Test void bandsWithOverlapRefused() {
        assertThrows(IllegalArgumentException.class, () -> new PriceSheet("X",
                LocalDate.of(2026, 1, 1), null,
                List.of(new SizeBand("P", 0, 100), new SizeBand("M", 100, 200)), List.of(), List.of()));
    }

    @Test void bandForBoundaries() {
        PriceSheet s = SaasPricingTestSupport.sheet();
        assertEquals("P", s.bandFor(0).code());
        assertEquals("P", s.bandFor(1000).code());
        assertEquals("M", s.bandFor(1001).code());
        assertEquals("G", s.bandFor(5001).code());
    }

    @Test void bandForBeyondLastRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> SaasPricingTestSupport.sheet().bandFor(1000000000L));
    }

    @Test void monthlyCostBreakdown() {
        var cost = PriceCalculator.monthlyCost(SaasPricingTestSupport.sheet(), 500, 10);
        assertEquals(new BigDecimal("1000.00"), cost.fixedPerTenant());
        assertEquals(new BigDecimal("1000.00"), cost.perConnection());
        assertEquals(new BigDecimal("1000.00"), cost.sharedPool());
        assertEquals(new BigDecimal("1000.00"), cost.labor());
        assertEquals(new BigDecimal("4000.00"), cost.total());
    }

    @Test void priceAppliesMargin() {
        var priced = PriceCalculator.price(SaasPricingTestSupport.sheet(),
                SaasPricingTestSupport.params(), 500, 10);
        assertEquals("P", priced.band().code());
        assertEquals(new BigDecimal("5000.00"), priced.monthlyPrice());
    }

    @Test void missingMarginRefusedAsADefinir() {
        var noMargin = SaasPricingParameters.fromValues(Map.of("saas.pass_through_mode", "FRANCHISE"));
        var ex = assertThrows(SaasPricingParameters.MissingSaasPricingParameterException.class,
                () -> PriceCalculator.price(SaasPricingTestSupport.sheet(), noMargin, 500, 10));
        assertTrue(ex.getMessage().contains("[A DEFINIR]"));
        assertTrue(ex.getMessage().contains("saas.margin_percent"));
    }

    @Test void poolOfZeroRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> PriceCalculator.monthlyCost(SaasPricingTestSupport.sheet(), 500, 0));
    }

    @Test void laborCostIsRateTimesHours() {
        assertEquals(new BigDecimal("1000.00"),
                new ProfessionalProfile("suporte", new BigDecimal("100.00"), new BigDecimal("10")).monthlyCost());
    }

    @Test void sheetValidityWindow() {
        PriceSheet s = SaasPricingTestSupport.sheet();
        assertTrue(s.validOn(LocalDate.of(2026, 6, 1)));
        assertFalse(s.validOn(LocalDate.of(2025, 12, 31)));
    }
}
