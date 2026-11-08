package br.com.ziipsan.commercial.billing.saaspricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CommercialPolicyServiceTest {

    private final CommercialPolicyService service = new CommercialPolicyService();

    @Test void fullMonthlyEvaluation() {
        UUID tenant = UUID.randomUUID();
        var snap = ActiveConnectionSnapshot.sealed(tenant, YearMonth.of(2026, 6), 500);
        var eval = service.evaluate(SaasPricingTestSupport.sheet(), SaasPricingTestSupport.params(),
                snap, 10, null, List.of(new UsagePassThrough.UsageLine(
                        UsagePassThrough.UsageCategory.WHATSAPP_UTILITY, 1000L, new BigDecimal("0.05"), 1500)));
        assertEquals("P", eval.bandCode());
        assertEquals(new BigDecimal("5000.00"), eval.effectivePrice());
        assertEquals(ReferenceCeilings.CeilingStatus.WITHIN, eval.visan().status());
        assertEquals(ReferenceCeilings.CeilingStatus.WITHIN, eval.samae().status());
        assertEquals(new BigDecimal("25.00"), eval.passThrough().total());
    }

    @Test void evaluationAfterReversionAppliesDiscount() {
        UUID tenant = UUID.randomUUID();
        var snap = ActiveConnectionSnapshot.sealed(tenant, YearMonth.of(2026, 6), 500);
        var eval = service.evaluate(SaasPricingTestSupport.sheet(), SaasPricingTestSupport.params(),
                snap, 10, YearMonth.of(2026, 6), null);
        assertEquals(new BigDecimal("4500.00"), eval.effectivePrice());
        assertNull(eval.passThrough());
    }

    @Test void tamperedSnapshotBlocksEvaluation() {
        UUID tenant = UUID.randomUUID();
        var snap = ActiveConnectionSnapshot.sealed(tenant, YearMonth.of(2026, 6), 500);
        var tampered = new ActiveConnectionSnapshot(tenant, YearMonth.of(2026, 6), 5000, snap.hash());
        assertThrows(IllegalStateException.class, () -> service.evaluate(
                SaasPricingTestSupport.sheet(), SaasPricingTestSupport.params(), tampered, 10, null, null));
    }

    @Test void sheetOutOfValidityBlocksEvaluation() {
        UUID tenant = UUID.randomUUID();
        var snap = ActiveConnectionSnapshot.sealed(tenant, YearMonth.of(2025, 6), 500);
        assertThrows(IllegalArgumentException.class, () -> service.evaluate(
                SaasPricingTestSupport.sheet(), SaasPricingTestSupport.params(), snap, 10, null, null));
    }

    @Test void largerBandPricedBySameSheet() {
        UUID tenant = UUID.randomUUID();
        var snap = ActiveConnectionSnapshot.sealed(tenant, YearMonth.of(2026, 6), 2000);
        var eval = service.evaluate(SaasPricingTestSupport.sheet(), SaasPricingTestSupport.params(),
                snap, 10, null, null);
        assertEquals("M", eval.bandCode());
        // custo: 1000 fixo + 4000 por ligacao + 1000 pool + 1000 homem-hora = 7000; +25% = 8750
        assertEquals(new BigDecimal("8750.00"), eval.effectivePrice());
        assertEquals(ReferenceCeilings.CeilingStatus.ABOVE, eval.samae().status());
        assertEquals(ReferenceCeilings.CeilingStatus.WITHIN, eval.visan().status());
    }
}
