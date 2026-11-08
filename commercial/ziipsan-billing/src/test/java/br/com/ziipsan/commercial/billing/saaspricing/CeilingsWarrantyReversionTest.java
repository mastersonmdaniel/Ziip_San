package br.com.ziipsan.commercial.billing.saaspricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CeilingsWarrantyReversionTest {

    @Test void ceilingsAreTheAcceptanceValues() {
        assertEquals(new BigDecimal("18866.65"), ReferenceCeilings.VISAN_MONTHLY);
        assertEquals(new BigDecimal("7636.74"), ReferenceCeilings.SAMAE_MONTHLY);
    }

    @Test void priceWithinBothCeilings() {
        assertEquals(ReferenceCeilings.CeilingStatus.WITHIN,
                ReferenceCeilings.visan(new BigDecimal("5000.00")).status());
        assertEquals(ReferenceCeilings.CeilingStatus.WITHIN,
                ReferenceCeilings.samae(new BigDecimal("5000.00")).status());
    }

    @Test void priceAboveSamaeButWithinVisan() {
        var samae = ReferenceCeilings.samae(new BigDecimal("8000.00"));
        assertEquals(ReferenceCeilings.CeilingStatus.ABOVE, samae.status());
        assertEquals(new BigDecimal("363.26"), samae.difference());
        assertEquals(ReferenceCeilings.CeilingStatus.WITHIN,
                ReferenceCeilings.visan(new BigDecimal("8000.00")).status());
    }

    @Test void priceExactlyAtCeilingIsWithin() {
        assertEquals(ReferenceCeilings.CeilingStatus.WITHIN,
                ReferenceCeilings.samae(new BigDecimal("7636.74")).status());
    }

    @Test void warrantyIsTwelveMonths() {
        assertEquals(12, WarrantyRule.WARRANTY_MONTHS);
        assertEquals(LocalDate.of(2027, 1, 15),
                WarrantyRule.warrantyEnd(LocalDate.of(2026, 1, 15)));
        assertTrue(WarrantyRule.withinWarranty(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 12, 31)));
        assertFalse(WarrantyRule.withinWarranty(LocalDate.of(2026, 1, 15), LocalDate.of(2027, 1, 15)));
    }

    @Test void defectCorrectionDeadlineIs48Hours() {
        assertEquals(48, WarrantyRule.DEFECT_CORRECTION_HOURS);
        Instant reported = Instant.parse("2026-03-01T10:00:00Z");
        assertEquals(Instant.parse("2026-03-03T10:00:00Z"), WarrantyRule.defectCorrectionDeadline(reported));
        assertTrue(WarrantyRule.correctedInTime(reported, Instant.parse("2026-03-03T10:00:00Z")));
        assertFalse(WarrantyRule.correctedInTime(reported, Instant.parse("2026-03-03T10:00:01Z")));
    }

    @Test void reversionDiscountAppliesOnlyFromReversion() {
        var params = SaasPricingParameters.fromValues(Map.of(
                "saas.reversion_discount_percent", "10", "saas.pass_through_mode", "FRANCHISE"));
        BigDecimal price = new BigDecimal("5000.00");
        assertEquals(new BigDecimal("5000.00"), ReversionDiscountRule.priceForCompetence(
                price, params, YearMonth.of(2026, 5), YearMonth.of(2026, 6)));
        assertEquals(new BigDecimal("4500.00"), ReversionDiscountRule.priceForCompetence(
                price, params, YearMonth.of(2026, 6), YearMonth.of(2026, 6)));
        assertEquals(new BigDecimal("4500.00"), ReversionDiscountRule.priceForCompetence(
                price, params, YearMonth.of(2026, 7), YearMonth.of(2026, 6)));
        assertEquals(new BigDecimal("5000.00"), ReversionDiscountRule.priceForCompetence(
                price, params, YearMonth.of(2026, 6), null));
    }

    @Test void reversionWithoutDiscountParamRefusedFromReversion() {
        var noDiscount = SaasPricingParameters.fromValues(Map.of("saas.pass_through_mode", "FRANCHISE"));
        assertThrows(SaasPricingParameters.MissingSaasPricingParameterException.class,
                () -> ReversionDiscountRule.priceForCompetence(new BigDecimal("5000.00"),
                        noDiscount, YearMonth.of(2026, 6), YearMonth.of(2026, 6)));
        // Antes da reversao o parametro nem e consultado: preco cheio sem desconto definido.
        assertEquals(new BigDecimal("5000.00"), ReversionDiscountRule.priceForCompetence(
                new BigDecimal("5000.00"), noDiscount, YearMonth.of(2026, 5), YearMonth.of(2026, 6)));
    }

    @Test void discountAbove100Refused() {
        var bad = SaasPricingParameters.fromValues(Map.of(
                "saas.reversion_discount_percent", "150", "saas.pass_through_mode", "FRANCHISE"));
        assertThrows(IllegalArgumentException.class, () -> ReversionDiscountRule.priceForCompetence(
                new BigDecimal("5000.00"), bad, YearMonth.of(2026, 6), YearMonth.of(2026, 6)));
    }
}
