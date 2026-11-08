package br.com.ziipsan.core.effluent;

import br.com.ziipsan.core.quality.Sample;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Automonitoramento sobre o plano e a cadeia de custodia REAL da
 * ED-50: a amostra de efluente e uma Sample da ED-50 levada ate
 * RECEIVED_BY_LAB; sem o recebimento, o registro da analise recusa.
 */
class EffluentMonitoringTest {

    private static EffluentMonitoringPlan plan() {
        return new EffluentMonitoringPlan(UUID.randomUUID(), TestSupport.TENANT, TestSupport.ETE_ID,
                YearMonth.of(2026, 1), YearMonth.of(2026, 12), "FIXTURE-1",
                List.of(new EffluentMonitoringPlan.PlanLine("ETE-01:SAIDA", "PARAM_A", 4),
                        new EffluentMonitoringPlan.PlanLine("ETE-01:SAIDA", "PARAM_B", 2)));
    }

    /** Amostra REAL da ED-50 recebida pelo laboratorio (custodia completa). */
    private static Sample receivedEd50Sample() {
        Sample s = Sample.planned(UUID.randomUUID(), TestSupport.TENANT, UUID.randomUUID(),
                Sample.SampleType.ROUTINE, "ETQ-EFL-1", UUID.randomUUID(), null);
        s.collect("COLETOR-1", TestSupport.T0);
        s.sealAndDispatch("COLETOR-1", TestSupport.T0.plusSeconds(600));
        s.receiveByLab("LAB-1", TestSupport.T0.plusSeconds(3600));
        return s;
    }

    private static EffluentMonitoring monitoringWrapping(Sample sample) {
        return new EffluentMonitoring((tenantId, sampleId) -> sampleId.equals(sample.id())
                ? new EffluentPorts.LabReceipt(sample.id(), sample.receivedByLab(), sample.labReceiverRef())
                : new EffluentPorts.LabReceipt(sampleId, false, null));
    }

    @Test
    void planLinesCarryTenantQuantitiesAndRejectZero() {
        assertEquals(4, plan().plannedFor("ETE-01:SAIDA", "PARAM_A"));
        assertEquals(0, plan().plannedFor("ETE-01:SAIDA", "PARAM_Z"));
        assertThrows(IllegalArgumentException.class,
                () -> new EffluentMonitoringPlan.PlanLine("P", "PARAM_A", 0));
    }

    @Test
    void coverageCountsCollectedPerLineWithoutPresuming() {
        var collected = List.of(
                new EffluentMonitoring.CollectedSample(UUID.randomUUID(), "ETE-01:SAIDA", "PARAM_A"),
                new EffluentMonitoring.CollectedSample(UUID.randomUUID(), "ETE-01:SAIDA", "PARAM_A"));
        var coverage = EffluentMonitoring.coverage(plan(), collected);
        assertEquals(2, coverage.get(0).collected());
        assertEquals(2, coverage.get(0).missing());
        assertFalse(coverage.get(0).fulfilled());
        assertEquals(0, coverage.get(1).collected());
        assertFalse(coverage.get(1).fulfilled());
    }

    @Test
    void analysisRegisteredOnlyAfterLabReceiptInEd50Custody() {
        Sample sample = receivedEd50Sample();
        EffluentMonitoring monitoring = monitoringWrapping(sample);
        EffluentCatalog catalog = TestSupport.catalog(TestSupport.federalMax("PARAM_A", "10", true));
        var assessment = monitoring.registerAnalysis(TestSupport.TENANT,
                new EffluentMonitoring.CollectedSample(sample.id(), "ETE-01:SAIDA", "PARAM_A"),
                new BigDecimal("8"), null, catalog, EffluentStandard.Jurisdiction.FEDERAL, null,
                LocalDate.of(2026, 3, 1));
        assertEquals(EffluentConformity.Verdict.CONFORMING, assessment.verdict());
    }

    @Test
    void analysisRefusedWhenEd50SampleNotReceivedByLab() {
        Sample plannedOnly = Sample.planned(UUID.randomUUID(), TestSupport.TENANT, UUID.randomUUID(),
                Sample.SampleType.ROUTINE, "ETQ-EFL-2", UUID.randomUUID(), null);
        EffluentMonitoring monitoring = monitoringWrapping(plannedOnly);
        EffluentCatalog catalog = TestSupport.catalog(TestSupport.federalMax("PARAM_A", "10", true));
        assertThrows(IllegalStateException.class, () -> monitoring.registerAnalysis(TestSupport.TENANT,
                new EffluentMonitoring.CollectedSample(plannedOnly.id(), "ETE-01:SAIDA", "PARAM_A"),
                new BigDecimal("8"), null, catalog, EffluentStandard.Jurisdiction.FEDERAL, null,
                LocalDate.of(2026, 3, 1)));
    }

    @Test
    void planRequiresCatalogVersionAndWastewaterUnit() {
        assertThrows(IllegalArgumentException.class, () -> new EffluentMonitoringPlan(
                UUID.randomUUID(), TestSupport.TENANT, null, YearMonth.of(2026, 1),
                YearMonth.of(2026, 2), "FIXTURE-1",
                List.of(new EffluentMonitoringPlan.PlanLine("P", "PARAM_A", 1))));
        assertTrue(plan().covers(YearMonth.of(2026, 6)));
    }
}
