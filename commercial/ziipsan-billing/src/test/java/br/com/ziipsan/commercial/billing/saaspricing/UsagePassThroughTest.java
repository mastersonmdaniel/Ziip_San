package br.com.ziipsan.commercial.billing.saaspricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UsagePassThroughTest {

    @Test void franchiseChargesOnlyExcessMessages() {
        var line = new UsagePassThrough.UsageLine(UsagePassThrough.UsageCategory.WHATSAPP_UTILITY,
                1000L, new BigDecimal("0.05"), 1500);
        var charge = UsagePassThrough.charge(line, SaasPricingParameters.PassThroughMode.FRANCHISE);
        assertEquals(500, charge.billableQuantity());
        assertEquals(new BigDecimal("25.00"), charge.amount());
    }

    @Test void usageWithinFranchiseChargesZero() {
        var line = new UsagePassThrough.UsageLine(UsagePassThrough.UsageCategory.WHATSAPP_MARKETING,
                1000L, new BigDecimal("0.10"), 800);
        assertEquals(new BigDecimal("0.00"),
                UsagePassThrough.charge(line, SaasPricingParameters.PassThroughMode.FRANCHISE).amount());
    }

    @Test void fullPassThroughIgnoresFranchise() {
        var line = new UsagePassThrough.UsageLine(UsagePassThrough.UsageCategory.SMS,
                1000L, new BigDecimal("0.10"), 100);
        var charge = UsagePassThrough.charge(line, SaasPricingParameters.PassThroughMode.FULL_PASS_THROUGH);
        assertEquals(100, charge.billableQuantity());
        assertEquals(new BigDecimal("10.00"), charge.amount());
    }

    @Test void llmTokensBilledPerThousand() {
        var line = new UsagePassThrough.UsageLine(UsagePassThrough.UsageCategory.LLM_TOKENS,
                100000L, new BigDecimal("2.00"), 250000);
        var charge = UsagePassThrough.charge(line, SaasPricingParameters.PassThroughMode.FRANCHISE);
        assertEquals(150000, charge.billableQuantity());
        assertEquals(new BigDecimal("300.00"), charge.amount());
    }

    @Test void whatsappCategoriesAreSeparateLines() {
        var st = UsagePassThrough.statement(UUID.randomUUID(), YearMonth.of(2026, 6), List.of(
                new UsagePassThrough.UsageLine(UsagePassThrough.UsageCategory.WHATSAPP_MARKETING, 0L, new BigDecimal("0.10"), 10),
                new UsagePassThrough.UsageLine(UsagePassThrough.UsageCategory.WHATSAPP_AUTHENTICATION, 0L, new BigDecimal("0.02"), 10)),
                SaasPricingTestSupport.params());
        assertEquals(2, st.lines().size());
        assertEquals(new BigDecimal("1.20"), st.total());
    }

    @Test void zeroUsageNeedsNoUnitCost() {
        var line = new UsagePassThrough.UsageLine(UsagePassThrough.UsageCategory.SMS, null, null, 0);
        assertEquals(new BigDecimal("0.00"),
                UsagePassThrough.charge(line, SaasPricingParameters.PassThroughMode.FRANCHISE).amount());
    }

    @Test void measuredUsageWithoutUnitCostRefused() {
        var line = new UsagePassThrough.UsageLine(UsagePassThrough.UsageCategory.SMS, 0L, null, 10);
        var ex = assertThrows(IllegalStateException.class,
                () -> UsagePassThrough.charge(line, SaasPricingParameters.PassThroughMode.FRANCHISE));
        assertTrue(ex.getMessage().contains("[A DEFINIR]"));
    }

    @Test void franchiseModeWithoutFranchiseRefused() {
        var line = new UsagePassThrough.UsageLine(UsagePassThrough.UsageCategory.SMS, null, new BigDecimal("0.10"), 10);
        var ex = assertThrows(IllegalStateException.class,
                () -> UsagePassThrough.charge(line, SaasPricingParameters.PassThroughMode.FRANCHISE));
        assertTrue(ex.getMessage().contains("[A DEFINIR]"));
    }

    @Test void missingModeRefused() {
        var noMode = SaasPricingParameters.fromValues(Map.of("saas.margin_percent", "25"));
        assertThrows(SaasPricingParameters.MissingSaasPricingParameterException.class,
                () -> UsagePassThrough.statement(UUID.randomUUID(), YearMonth.of(2026, 6), List.of(), noMode));
    }

    @Test void invalidParameterValuesRefused() {
        assertThrows(IllegalArgumentException.class, () -> SaasPricingParameters.fromValues(
                Map.of("saas.margin_percent", "abc")).marginPercent());
        assertThrows(IllegalArgumentException.class, () -> SaasPricingParameters.fromValues(
                Map.of("saas.margin_percent", "-5")).marginPercent());
        assertThrows(IllegalArgumentException.class, () -> SaasPricingParameters.fromValues(
                Map.of("saas.pass_through_mode", "OUTRO")).passThroughMode());
    }
}
