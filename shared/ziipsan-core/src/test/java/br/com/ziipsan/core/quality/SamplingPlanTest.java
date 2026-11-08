package br.com.ziipsan.core.quality;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SamplingPlanTest {

    private SamplingPlan plan() {
        return new SamplingPlan(UUID.randomUUID(), "SIS-1",
                YearMonth.of(2026, 1), YearMonth.of(2026, 12), "FIXTURE-1",
                List.of(new SamplingPlan.PlanLine(TestSupport.POINT, "PARAM_A", 4),
                        new SamplingPlan.PlanLine(TestSupport.POINT, "PARAM_B", 2)));
    }

    @Test void plannedCountsComeFromThePlan() {
        SamplingPlan p = plan();
        assertEquals(4, p.plannedFor(TestSupport.POINT, "PARAM_A"));
        assertEquals(2, p.plannedFor(TestSupport.POINT, "PARAM_B"));
        assertEquals(0, p.plannedFor(TestSupport.POINT, "PARAM_Z"));
        assertTrue(p.covers(YearMonth.of(2026, 6)));
        assertFalse(p.covers(YearMonth.of(2027, 1)));
    }

    @Test void zeroOrNegativePlannedCountIsRefused() {
        // Quantidade de amostragem nunca vem de fabrica: linha sem quantidade positiva nao entra.
        assertThrows(IllegalArgumentException.class,
                () -> new SamplingPlan.PlanLine(TestSupport.POINT, "PARAM_A", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new SamplingPlan.PlanLine(TestSupport.POINT, "PARAM_A", -1));
    }

    @Test void duplicateLineIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new SamplingPlan(UUID.randomUUID(), "SIS-1",
                YearMonth.of(2026, 1), YearMonth.of(2026, 12), "FIXTURE-1",
                List.of(new SamplingPlan.PlanLine(TestSupport.POINT, "PARAM_A", 1),
                        new SamplingPlan.PlanLine(TestSupport.POINT, "PARAM_A", 2))));
    }

    @Test void consumerPointRequiresConsumerUnit() {
        assertThrows(IllegalArgumentException.class, () -> new SamplingPoint(UUID.randomUUID(), "PC-1",
                "SIS-1", SamplingPoint.PointKind.CONSUMER_POINT, null, true));
        assertThrows(IllegalArgumentException.class, () -> new SamplingPoint(UUID.randomUUID(), "PN-1",
                "SIS-1", SamplingPoint.PointKind.DISTRIBUTION_NETWORK, UUID.randomUUID(), true));
        assertNotNull(new SamplingPoint(UUID.randomUUID(), "PC-1", "SIS-1",
                SamplingPoint.PointKind.CONSUMER_POINT, UUID.randomUUID(), true));
    }
}
