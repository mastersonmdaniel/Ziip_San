package br.com.ziipsan.core.regulatory.export;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

class ContentAndSocialTest {
    @Test void contentPackCoversAllGes04Sections() {
        var pack = new RegulatoryContent.ContentPack(
            List.of(new RegulatoryContent.CategoryConsumption("RESIDENCIAL", "0-10", new BigDecimal("8.50"), 1000)),
            List.of(new RegulatoryContent.SubsidyCost(RegulatoryContent.SubsidyKind.FISCAL,
                    new BigDecimal("12000.00"), 120, new BigDecimal("9000.00"))),
            List.of(new RegulatoryContent.LargeUserContract("SYN-CT-1", "INDUSTRIAL", new BigDecimal("5000"))),
            List.of(new RegulatoryContent.DelinquencyRate("RESIDENCIAL", new BigDecimal("0.07"))),
            120);
        for (var s : RegulatoryContent.Section.values()) assertTrue(pack.covers(s), s.name());
    }
    @Test void subsidyKindsAreTheThreeOfLei11445Art31() {
        assertEquals(3, RegulatoryContent.SubsidyKind.values().length);
    }
    @Test void delinquencyRateOutsideRangeRefused() {
        assertThrows(IllegalArgumentException.class,
            () -> new RegulatoryContent.DelinquencyRate("X", new BigDecimal("1.5")));
    }
    @Test void itsFormulaBenefitedOverActive() {
        var its = SocialTariffIndex.of(YearMonth.of(2026, 10), 120, 1000);
        assertEquals(new BigDecimal("0.1200"), its.value());
    }
    @Test void itsZeroActiveRefusedAndBenefitedAboveActiveRefused() {
        assertThrows(IllegalArgumentException.class, () -> SocialTariffIndex.of(YearMonth.of(2026,10), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> SocialTariffIndex.of(YearMonth.of(2026,10), 1001, 1000));
    }
    @Test void anaReconciliationCountsDivergences() {
        var r = AnaReconciliation.reconcile(YearMonth.of(2026,10), "4203000", "ARIS",
            Set.of("SYN-E1","SYN-E2","SYN-E3"), Set.of("SYN-E2","SYN-E3","SYN-E4"));
        assertEquals(2, r.matched()); assertEquals(1, r.onlyLocal()); assertEquals(1, r.onlyAna());
        assertTrue(r.diverged());
    }
    @Test void anaReconciliationExactMatchDoesNotDiverge() {
        var r = AnaReconciliation.reconcile(YearMonth.of(2026,10), "4203000", "AGIR",
            Set.of("SYN-E1"), Set.of("SYN-E1"));
        assertFalse(r.diverged());
    }
}
